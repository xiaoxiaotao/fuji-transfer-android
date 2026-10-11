// Implements Fujifilm nonstandard PTP/IP implementation
// This is all portable code
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <errno.h>
#include <libpict.h>
#include <sys/stat.h>
#include "app.h"
#include "fuji.h"
#include "fujiptp.h"
#include "exif.h"
#include "object.h"
#include "xapp_index.h"
#include "wifi_protocol.h"
#include "xapp_probe.h"

static int fuji_init_with_recovery(struct PtpRuntime *r, char *name, struct PtpFujiInitResp *resp);

struct FujiDeviceKnowledge *fuji_get(struct PtpRuntime *r) {
	return (struct FujiDeviceKnowledge *)r->userdata;
}

struct NetworkHandle *ptp_get_network_info(struct PtpRuntime *r) {
	return &fuji_get(r)->net;
}

int fuji_reset_ptp(struct PtpRuntime *r) {
    if (r->userdata) free(fuji_get(r)->xapp_handles);
	ptp_reset(r);
	if (r->userdata == NULL)
		r->userdata = malloc(sizeof(struct FujiDeviceKnowledge));
	memset(r->userdata, 0, sizeof(struct FujiDeviceKnowledge));
	r->connection_type = PTP_IP_USB;
	r->response_wait_default = 3; // Fuji cams are slow!
	return 0;
}

void ptp_report_error(struct PtpRuntime *r, const char *reason, int code) {
	if (r->io_kill_switch) return;
	ptp_mutex_lock(r);
	if (r->io_kill_switch) {
		ptp_mutex_unlock(r);
		return;
	}

	// Safely disconnect if intentional
	if (code == 0) {
		ptp_verbose_log("Closing session\n");
		int close_rc = ptp_close_session(r);
		app_print("CloseSession result: %d", close_rc);
	}

	r->operation_kill_switch = 1;

	ptp_verbose_log("Goodbye\n");

	if (r->connection_type == PTP_IP_USB) {
		// Send Fuji's 'goodbye' packet - we don't care if this fails or not
		uint8_t goodbye_packet[] = {0x8, 0x0, 0x0, 0x0, 0xff, 0xff, 0xff, 0xff};
		int goodbye_rc = ptpip_cmd_write(r, goodbye_packet, 8);
		app_print("Fuji goodbye write result: %d", goodbye_rc);

		ptpip_device_close(r);
	} else if (r->connection_type == PTP_USB) {
		ptp_device_close(r);
	}

	r->io_kill_switch = 1;

	ptp_mutex_unlock(r);

	if (reason == NULL) {
		if (code == PTP_IO_ERR) {
			app_print("连接已断开：通信错误");
		} else {
			app_print("连接已断开：运行错误");
		}
	} else {
		app_print("连接已断开：%s", reason);
	}
}

// New function to connect from struct instead of passing in IP/port/etc
int fuji_connect_from_discoverinfo(struct PtpRuntime *r, struct DiscoverInfo *info) {
	fuji_reset_ptp(r);
    snprintf(fuji_get(r)->info_model, sizeof(fuji_get(r)->info_model), "%.*s", (int)sizeof(info->camera_model), info->camera_model);
    snprintf(fuji_get(r)->info_name, sizeof(fuji_get(r)->info_name), "%.*s", (int)sizeof(info->camera_name), info->camera_name);
	strncpy(fuji_get(r)->ip_address, info->camera_ip, 64);
	strncpy(fuji_get(r)->autosave_client_name, info->client_name, 64);
	fuji_get(r)->transport = info->transport;
	memcpy(&fuji_get(r)->net, &info->h, sizeof(struct NetworkHandle));

	int rc = ptpip_connect(r, info->camera_ip, info->camera_port, 5);

	// If camera ignored the TCP connect, try again
	if (rc) {
		rc = ptpip_connect(r, info->camera_ip, info->camera_port, 5);
	}

	if (rc) {
		plat_dbg("Error connecting to %s:%d\n", info->camera_ip, info->camera_port);
		return rc;
	}

	return 0;
}

int fuji_connection_entry(struct PtpRuntime *r) {
	plat_dbg("transport: %d", fuji_get(r)->transport);
	if (fuji_get(r)->transport == FUJI_FEATURE_WIRELESS_TETHER || r->connection_type == PTP_USB) {
		return fujitether_setup(r);
	} else {
		int rc = fuji_setup(r);

		// TODO: Handle this less weird
		if (!rc && fuji_get(r)->camera_state == FUJI_MULTIPLE_TRANSFER) {
			rc = fuji_download_classic(r);
			if (rc) {
				app_print("照片下载出错");
				return rc;
			}
			app_print("请在文件管理器或相册中查看。");
			ptp_report_error(r, "Disconnected", 0);
		}

		return rc;
	}

	return 0;
}

// Assumes cmd socket is valid
int fuji_setup(struct PtpRuntime *r) {
	struct FujiDeviceKnowledge *fuji = fuji_get(r);
	if(fuji->connection_progress)fuji->connection_progress(0);

	app_print(fuji->auto_protocol ? "正在建立相机协议连接…" : fuji->transport == FUJI_FEATURE_XAPP_WIRELESS_COMM ? "正在建立 XApp 协议连接…" : "请在相机上按 OK 确认连接。");

	char *device_name = fuji->transport == FUJI_FEATURE_XAPP_WIRELESS_COMM ? strdup("Fudge-XE5") : app_get_client_name();

	struct PtpFujiInitResp resp;
	int xapp = fuji->transport == FUJI_FEATURE_XAPP_WIRELESS_COMM;
	int rc = xapp ? fuji_init_with_recovery(r, device_name, &resp) : ptpip_fuji_init_req(r, device_name, &resp);
	if (!xapp && rc == PTP_RUNTIME_ERR) {
		rc = ptpip_fuji_init_req(r, device_name, &resp);
	} else if (!xapp && rc) {
		usleep(1000); // One last chance...
		rc = ptpip_fuji_init_req(r, device_name, &resp);
	}

	free(device_name);
	if (rc) {
		app_print("连接初始化失败");
		return rc;
	}
	app_print("连接已初始化。");

	snprintf(fuji->info_name, sizeof(fuji->info_name), "%.*s", (int)sizeof(resp.cam_name), resp.cam_name);
	app_send_cam_name(resp.cam_name);

	if (fuji->transport == FUJI_FEATURE_WIRELESS_COMM || fuji->transport == FUJI_FEATURE_XAPP_WIRELESS_COMM) {
		app_print("相机正在处理…");
		usleep(50000); // Fuji cameras require at least 50ms delay after init
	}

	app_print("正在打开 PTP 会话…");
    if(fuji->connection_progress)fuji->connection_progress(1);
    if (fuji->auto_protocol || fuji->transport == FUJI_FEATURE_XAPP_WIRELESS_COMM) {
        // Match current libfuji: Fujifilm starts OpenSession at transaction 1.
        r->session++;
        r->transaction = 1;
        struct PtpCommand open = {0};
        open.code = PTP_OC_OpenSession;
        open.params[0] = r->session;
        open.param_length = 1;
        app_print("XApp OpenSession：事务编号=1，会话编号=%d", r->session);
        rc = ptp_send(r, &open);
    } else {
        rc = ptp_open_session(r);
    }
	if (rc) { char diagnostic[120]; snprintf(diagnostic, sizeof(diagnostic), "OpenSession 失败：rc=%d，响应=0x%x", rc, ptp_get_return_code(r)); app_print(diagnostic); }
	if (rc == PTP_CHECK_CODE) {
		if (ptp_get_return_code(r) != PTP_RC_SessionAlreadyOpened) {
			return rc;
		}
	} else if (rc) {
		app_print("无法建立相机会话。");
		return rc;
	}

    if (fuji->auto_protocol) {
        app_print("正在识别相机连接方式…");
        int remote = -1, extended = -1;
        rc = ptp_get_prop_value(r, PTP_DPC_FUJI_RemoteVersion);
        if (!rc) remote = ptp_parse_prop_value(r);
        else if (rc != PTP_CHECK_CODE || (ptp_get_return_code(r) != PTP_RC_DevicePropNotSupported && ptp_get_return_code(r) != PTP_RC_OperationNotSupported)) return rc;
        if (remote >= 0x20007) {
            rc = ptp_get_prop_value(r, 0xdf28);
            if (!rc) extended = ptp_parse_prop_value(r);
            else if (rc != PTP_CHECK_CODE || (ptp_get_return_code(r) != PTP_RC_DevicePropNotSupported && ptp_get_return_code(r) != PTP_RC_OperationNotSupported)) return rc;
        }
        fuji->transport = fuji_wifi_has_xapp(remote, extended) ? FUJI_FEATURE_XAPP_WIRELESS_COMM : FUJI_FEATURE_WIRELESS_COMM;
        app_print("自动识别：远程版本=0x%x，扩展版本=0x%x，选择%s", remote, extended, fuji->transport == FUJI_FEATURE_XAPP_WIRELESS_COMM ? "XApp 图库" : "旧款无线通信");
    }
    if (fuji->transport == FUJI_FEATURE_WIRELESS_TETHER) return 0;

    if (fuji->transport != FUJI_FEATURE_XAPP_WIRELESS_COMM) app_print("请在相机上按 OK 允许访问。");
	rc = fuji->transport == FUJI_FEATURE_XAPP_WIRELESS_COMM ? 0 : fuji_wait_for_access(r);
	if (rc) {
		app_print("无法获得相机访问权限。");
		return rc;
	}

	// Remote cams don't need to wait for access, so waiting for the 'OK'
	// is done somewhere else
	if (fuji->camera_state != FUJI_REMOTE_ACCESS) {
		app_print("相机已接受连接。");
	}

	// NOTE: cFujiConfigInitMode *must* be called before fuji_config_version, or anything else.
	// If not, it will break up the connection and destroy packets for any file operation.
	rc = fuji_config_init_mode(r);
	if (rc) {
		ptp_verbose_log("fuji_config_init_mode: %d\n", rc);
		app_print("无法设置相机模式");
		return rc;
	}

	if (fuji->camera_state == FUJI_MULTIPLE_TRANSFER) {
		return 0;
	}

	app_print("正在初始化照片查看器");
	rc = fuji->transport == FUJI_FEATURE_XAPP_WIRELESS_COMM ? 0 : fuji_config_version(r);
	if (rc) {
		app_print("无法检查协议版本。");
		return rc;
	}

	if (fuji->transport == FUJI_FEATURE_AUTOSAVE) {
		rc = fuji_begin_file_download(r);
		if (rc) return rc;
	}

	rc = fuji->transport == FUJI_FEATURE_XAPP_WIRELESS_COMM ? 0 : fuji_config_device_info_routine(r);
	if (rc) return rc;

	// Setup remote mode
	if (fuji->remote_version != -1 && fuji->camera_state == FUJI_REMOTE_ACCESS && fuji->transport != FUJI_FEATURE_XAPP_WIRELESS_COMM) {
		rc = fuji_setup_remote_mode(r);
		if (rc) return rc;
	}

	return 0;
}

int fuji_setup_remote_mode(struct PtpRuntime *r) {
	int rc = fuji_remote_mode_open_sockets(r);
	if (rc) {
		app_print("无法启动遥控模式");
		return rc;
	} else {
		app_print("已进入遥控模式。");
	}

	rc = fuji_get_events(r);
	if (rc) return rc;

	const char *ip = fuji_get(r)->ip_address;

	rc = ptpip_connect_events(r, ip, FUJI_EVENT_IP_PORT);
	if (rc) return rc;

	rc = ptpip_connect_video(r, ip, FUJI_LIVEVIEW_IP_PORT);
	if (rc) return rc;

	rc = fuji_remote_mode_end(r);
	if (rc) {
		app_print("遥控初始化失败。");
		return rc;
	} else {
		app_print("遥控初始化完成。");
	}

	rc = fuji_get_events(r);
	if (rc) return rc;

	return 0;
}


static long get_ms(void);

static int ptpip_fuji_init_req_(struct PtpRuntime *r, char *device_name, struct PtpFujiInitResp *resp, int *transport_failed) {
    if (transport_failed) *transport_failed = 0;
	struct FujiInitPacket *p = (struct FujiInitPacket *)r->data;
	memset(p, 0, sizeof(struct FujiInitPacket));
	ptp_write_u32(&p->length, 0x52);
	ptp_write_u32(&p->type, PTPIP_INIT_COMMAND_REQ);
	ptp_write_u32(&p->version, FUJI_PROTOCOL_VERSION);

	ptp_write_u32(&p->guid1, 0x5d48a5ad);
	ptp_write_u32(&p->guid2, 0xb7fb287);
	ptp_write_u32(&p->guid3, 0xd0ded5d3);
	ptp_write_u32(&p->guid4, 0x0);

	ptp_write_unicode_string(p->device_name, device_name);

	int written = ptpip_cmd_write(r, r->data, (int)p->length);
	if (written != (int)p->length) {
        if (transport_failed) *transport_failed = 1;
        app_print("PTP 初始化写入失败：bytes=%d，errno=%d", written, errno);
        return PTP_IO_ERR;
    }

    // TCP may return a partial header/body or zero bytes on socket timeout.
    memset(r->data, 0, 256);
    unsigned int received = 0, required = 4;
    long deadline = get_ms() + 15000000L;
    while (received < required) {
        int x = ptpip_cmd_read(r, r->data + received, required - received);
        if (x < 0 || r->io_kill_switch || get_ms() >= deadline) {
            if (transport_failed) *transport_failed = 1;
            app_print("PTP 握手响应读取失败：read=%d，errno=%d，received=%u/%u，cancelled=%d", x, errno, received, required, r->io_kill_switch);
            return PTP_IO_ERR;
        }
        if (x == 0) { usleep(10000); continue; }
        received += x;
        if (received == 4 && required == 4) {
            required = p->length;
            if (required < 8 || required > 256) {
                app_print("PTP 握手响应长度无效。"); return PTP_IO_ERR;
            }
        }
    }
    char handshake_info[100];
    snprintf(handshake_info, sizeof(handshake_info), "PTP 握手响应：长度=%u，类型=%u", required, p->type);
    app_print(handshake_info);
    if (p->type != PTPIP_INIT_FAIL && (p->type != PTPIP_INIT_COMMAND_ACK || required < 26)) {
        app_print("PTP 握手响应类型或长度不符合预期。"); return PTP_IO_ERR;
    }

	if (p->type == PTPIP_INIT_FAIL) {
		app_print("相机明确拒绝 PTP 初始化连接（INIT_FAIL）。");
		return PTP_RUNTIME_ERR;
	}

	ptp_fuji_get_init_info(r, resp);
	ptp_verbose_log("Connected to %s\n", resp->cam_name);

	if (ptp_get_return_code(r) != 0) {
		return PTP_IO_ERR;
	}

	return 0;
}
int ptpip_fuji_init_req(struct PtpRuntime *r, char *device_name, struct PtpFujiInitResp *resp) {
	ptp_mutex_lock(r);
	int rc = ptpip_fuji_init_req_(r, device_name, resp, NULL);
	ptp_mutex_unlock(r);
	return rc;
}

// Recover only transport failure before OpenSession, never a camera rejection
// or a gallery/download operation. Serialize recovery with manual teardown.
static int fuji_init_with_recovery(struct PtpRuntime *r, char *name, struct PtpFujiInitResp *resp) {
    ptp_mutex_lock(r);
    int transport_failed = 0;
    int rc = ptpip_fuji_init_req_(r, name, resp, &transport_failed);
    if (rc == PTP_IO_ERR && transport_failed && !r->io_kill_switch && !r->operation_kill_switch) {
        app_print("PTP 初始化传输失败，关闭旧 TCP 连接后恢复一次…");
        if(fuji_get(r)->connection_progress)fuji_get(r)->connection_progress(2);
        ptpip_device_close(r);
        usleep(1000000);
        rc = ptpip_connect(r, fuji_get(r)->ip_address, FUJI_CMD_IP_PORT, 3);
        if (!rc) rc = ptpip_fuji_init_req_(r, name, resp, NULL);
        app_print("PTP 初始化恢复结果：rc=%d", rc);
    }
    ptp_mutex_unlock(r);
    return rc;
}

int fuji_d228(void) {
	// Thing that PC AutoSave does
	//	char buffer[64];
	//	int s = 0;
	//	s += ptp_write_u8(buffer + s, 6);
	//	s += ptp_write_u32(buffer + s, 0x0020);
	//	s += ptp_write_u32(buffer + s, 0x0030);
	//	s += ptp_write_u32(buffer + s, 0x002f);
	//	s += ptp_write_u32(buffer + s, 0x0036);
	//	s += ptp_write_u32(buffer + s, 0x0030);
	//	s += ptp_write_u32(buffer + s, 0x0000);
	//
	//	ptp_set_prop_value_data(r, 0xd228, buffer, s);
	return 0;
}

struct MyAddInfo {
	struct PtpRuntime *r;
	int handle;
	uint8_t *buffer;
	int size;
};

static uint8_t *my_add(void *arg, uint8_t *buffer, int new_len, int old_len) {
	// Needs to be a new buffer
	ptp_verbose_log("downloading more exif %d %d\n", new_len, old_len);
	struct MyAddInfo *i = (struct MyAddInfo *)arg;
	i->buffer = realloc(i->buffer, new_len);
	if (i->buffer == NULL) abort();

	int rc = ptp_get_partial_object(i->r, i->handle, old_len, new_len - old_len);
	if (rc) return NULL;

	memcpy(i->buffer + old_len, ptp_get_payload(i->r), ptp_get_payload_length(i->r));
	return i->buffer;
}

int ptp_get_partial_exif(struct PtpRuntime *r, int handle, unsigned int *offset, unsigned int *length) {
	ptp_mutex_lock(r);

	int rc = fuji_get_events(r);
	if (rc) return rc;

	int max_size = 0x4000;
	rc = ptp_get_partial_object(r, handle, 0, max_size);
	if (rc == PTP_CHECK_CODE) {
		return PTP_RUNTIME_ERR;
	}
	if (rc) {
		ptp_mutex_unlock(r);
		return rc;
	}

	struct MyAddInfo temp = {
		.r = r,
		.handle = handle,
		.buffer = malloc(max_size),
		.size = max_size,
	};

	memcpy(temp.buffer, ptp_get_payload(r), ptp_get_payload_length(r));

	struct ExifC c = {0};
	c.length = ptp_get_payload_length(r);
	c.buf = temp.buffer;
	c.arg = &temp;
	c.get_more = my_add;

	rc = exif_start_raw(&c);
	ptp_verbose_log("Exif: %d\n", rc);

	if (c.thumb_of == 0 || c.thumb_size == 0) {
		rc = PTP_RUNTIME_ERR;
		goto end;
	}

	*offset = c.thumb_of;
	*length = c.thumb_size;
	ptp_verbose_log("Exif thumb offset: %u size: %u\n", c.thumb_of, c.thumb_size);

	// Given transfer speed/camera speed 5 event calls is generally enough for the camera
	// to not stop responding to object-related PTP commands
	rc = fuji_get_events(r);
	if (rc) goto end;
	rc = fuji_get_events(r);
	if (rc) goto end;
	rc = fuji_get_events(r);
	if (rc) goto end;
	rc = fuji_get_events(r);
	if (rc) goto end;

	end:;
	ptp_mutex_unlock(r);
	return rc;
}

int fuji_get_thumb(struct PtpRuntime *r, int handle, unsigned int *offset, unsigned int *length) {
	(*offset) = 0;
	(*length) = 0;
	if (fuji_get(r)->transport == FUJI_FEATURE_AUTOSAVE) {
		return ptp_get_partial_exif(r, handle, offset, length);
	} else {
		// Patch for newer cameras. ptp_get_thumbnail blocks forever unless this is called.
		if (fuji_get(r)->transport == FUJI_FEATURE_XAPP_WIRELESS_COMM || fuji_get(r)->remote_version > 0x20006) {
			struct PtpObjectInfo oi;
			if (handle <= 5) app_print("缩略图 #%d：先读取照片信息", handle);
			int rc;
            for (int attempt = 0; ; attempt++) {
                rc = ptp_get_object_info(r, (int)handle, &oi);
                if (rc != PTP_CHECK_CODE || ptp_get_return_code(r) != 0x2019 || attempt >= 4) break;
                if (attempt == 0) app_print("照片 #%d：相机忙，等待后重试（最多 5 次）", handle);
                usleep(250000 * (attempt + 1));
            }
			if (rc) app_print("照片信息 #%d 失败：rc=%d，响应=0x%x", handle, rc, ptp_get_return_code(r));
			if (rc == PTP_CHECK_CODE) return 0;
			if (rc) return rc;

			// Give it to the object service so it can be shown in UI
			ptp_object_service_set(r, r->oc, handle, &oi);
		}

		if (handle <= 5) app_print("缩略图 #%d：请求 GetThumb", handle);
		int rc;
        for (int attempt = 0; ; attempt++) {
            rc = ptp_get_thumbnail(r, (int)handle);
            if (rc != PTP_CHECK_CODE || ptp_get_return_code(r) != 0x2019 || attempt >= 4) break;
            if (attempt == 0) app_print("缩略图 #%d：相机忙，等待后重试", handle);
            usleep(250000 * (attempt + 1));
        }
		if (rc) app_print("缩略图 #%d 失败：rc=%d，响应=0x%x", handle, rc, ptp_get_return_code(r));
		if (rc == PTP_CHECK_CODE) {
			ptp_verbose_log("Thumbnail get failed: %x\n", ptp_get_return_code(r));
			return 0;
		} else if (rc) {
			return rc;
		} else if (ptp_get_payload_length(r) < 100) {
			return 0; // Weird situation, thumbnail is too small.
		} else {
			(*offset) = 0x0;
			(*length) = ptp_get_payload_length(r);
			if (handle <= 5) app_print("缩略图 #%d：收到 %u 字节", handle, *length);
		}
		return 0;
	}
}

int fuji_begin_file_download(struct PtpRuntime *r) {
	ptp_verbose_log("Beginning file download\n");
	int rc = fuji_get_events(r);
	if (rc) return rc;

	// Seems to take a while in some cases.
	r->wait_for_response = 3;
	rc = ptp_set_prop_value16(r, PTP_DPC_FUJI_EnableCorrectFileSize, 1);
	return rc;
}

int fuji_end_file_download(struct PtpRuntime *r) {
	int rc = ptp_set_prop_value16(r, PTP_DPC_FUJI_EnableCorrectFileSize, 0);
	return rc;
}

int fuji_begin_download_get_object_info(struct PtpRuntime *r, int handle, struct PtpObjectInfo *oi) {
	int rc;
	if (fuji_get(r)->transport == FUJI_FEATURE_XAPP_WIRELESS_COMM) {
        rc = ptp_set_prop_value16(r, 0xd226, 2);
        if (rc) return rc;
    } else if (r->connection_type == PTP_IP_USB) {
		rc = fuji_begin_file_download(r);
		if (rc) return rc;
	}

	rc = ptp_get_object_info(r, handle, oi);
	return rc;
}

int fuji_get_device_info(struct PtpRuntime *r) {
	struct PtpCommand cmd;
	cmd.code = PTP_OC_FUJI_GetDeviceInfo;
	cmd.param_length = 0;
	// TODO: Parsing should be done here
	return ptp_send(r, &cmd);
}

static int fuji_tether_download(struct PtpRuntime *r) {
	struct PtpArray *a;
	int rc = ptp_get_object_handles(r, -1, 0x0, 0x0, &a);
	if (rc) return rc;

	for (int i = 0; i < (int)a->length; i++) {
		// oi.filename will always be DSCF0001.JPG
		struct PtpObjectInfo oi;
		rc = ptp_get_object_info(r, a->data[i], &oi);
		if (rc) return rc;

		app_downloading_file(&oi);

		char buffer[256];
		app_get_tether_file_path(buffer);
		FILE *f = fopen(buffer, "wb");
		if (f == NULL) return PTP_RUNTIME_ERR;
		app_print("正在下载 %s", buffer);
		ptp_download_object(r, (int)a->data[i], f, 0x100000);
		fclose(f);

		app_downloaded_file(&oi, buffer);

		if (fuji_get(r)->transport == FUJI_FEATURE_WIRELESS_TETHER) {
			ptp_delete_object(r, (int)a->data[i]);
		}

		app_print("下载完成。");
	}

	free(a);
	return 0;
}

int fuji_get_events(struct PtpRuntime *r) {
	struct FujiDeviceKnowledge *fuji = fuji_get(r);
	ptp_mutex_lock(r);
	int rc = ptp_get_prop_value(r, PTP_DPC_FUJI_EventsList);
	if (rc == PTP_CHECK_CODE) {
		ptp_mutex_unlock(r);
		return 0;
	}
	if (rc) {
		ptp_mutex_unlock(r);
		return rc;
	}

	struct PtpFujiEvents *ev = (struct PtpFujiEvents *)(ptp_get_payload(r));

	uint16_t length, code;
	uint32_t value;

	ptp_read_u16(&ev->length, &length);

	ptp_verbose_log("Found %d events\n", ev->length);
	for (int i = 0; i < ev->length; i++) {
		ptp_read_u16(&ev->events[i].code, &code);
		ptp_read_u32(&ev->events[i].value, &value);
		ptp_verbose_log("%X changed to %X\n", code, value);
	}

	for (int i = 0; i < ev->length; i++) {
		ptp_read_u16(&ev->events[i].code, &code);
		ptp_read_u32(&ev->events[i].value, &value);
		switch (ev->events[i].code) {
		case PTP_DPC_FUJI_SelectedImgsMode:
			fuji->selected_imgs_mode = (int)ev->events[i].value;
			break;
		case PTP_DPC_FUJI_ObjectCount:
			fuji->num_objects = (int)ev->events[i].value;
			break;
		case PTP_DPC_FUJI_CameraState:
			fuji->camera_state = (int)ev->events[i].value;
			break;
		case PTP_DPC_FUJI_FreeSDRAMImages:
			if (fuji->transport == FUJI_FEATURE_WIRELESS_TETHER)
				fuji_tether_download(r);
			break;
		}
	}

	ptp_mutex_unlock(r);

	return 0;
}

// Call this immediately after session init
int fuji_wait_for_access(struct PtpRuntime *r) {
	struct FujiDeviceKnowledge *fuji = fuji_get(r);
	// We *need* these properties on camera init - otherwise, produce an error
	fuji->camera_state = FUJI_WAIT_FOR_ACCESS;
	fuji->num_objects = -1;
	fuji->selected_imgs_mode = -1;

	while (1) {
		// After opening session, immediately get events
		int rc = fuji_get_events(r);
		if (rc) return rc;

		// Wait until camera state is unlocked
		if (fuji->camera_state != FUJI_WAIT_FOR_ACCESS) {
			if (fuji->selected_imgs_mode != -1) {
				// Multiple mode doesn't send num_objects
				return 0;
			} else {
				if (fuji->num_objects == -1) {
					ptp_verbose_log("Failed to get num_objects from first event\n");
					return PTP_RUNTIME_ERR;
				}
			}
			return 0;
		}

		PTP_SLEEP(100);
	}
}

// Handles critical init sequence. This is after initializing the socket, and opening session.
// Called right after obtaining access to the device.
int fuji_config_init_mode(struct PtpRuntime *r) {
	struct FujiDeviceKnowledge *fuji = fuji_get(r);

	int rc = ptp_get_prop_value(r, PTP_DPC_FUJI_GetObjectVersion);
	if (rc) return rc;
	fuji->get_object_version = ptp_parse_prop_value(r);
	ptp_verbose_log("GetObjectVersion: 0x%X\n", fuji->get_object_version);

	rc = ptp_get_prop_value(r, PTP_DPC_FUJI_RemoteGetObjectVersion);
	if (rc) return rc;
	fuji->remote_image_view_version = ptp_parse_prop_value(r);
	ptp_verbose_log("RemoteGetObjectVersion: 0x%X\n", fuji->remote_image_view_version);

	rc = ptp_get_prop_value(r, PTP_DPC_FUJI_ImageGetVersion);
	if (rc) return rc;
	fuji->image_get_version = ptp_parse_prop_value(r);
	ptp_verbose_log("ImageGetVersion: 0x%X\n", fuji->image_get_version);

	rc = ptp_get_prop_value(r, PTP_DPC_FUJI_RemoteVersion);
	if (rc) return rc;
	fuji->remote_version = ptp_parse_prop_value(r);
	ptp_verbose_log("RemoteVersion: 0x%X\n", fuji->remote_version);

	ptp_verbose_log("CameraState is %d\n", fuji->camera_state);

	// Determine preferred mode from state and version info
	int mode = 0;
	if (fuji->transport == FUJI_FEATURE_XAPP_WIRELESS_COMM) {
        mode = 20; // XApp gallery mode, observed in libfuji
    } else if (fuji->remote_version != -1 || fuji->camera_state == FUJI_REMOTE_ACCESS) {
		mode = FUJI_REMOTE_MODE;
	} else {
		if (fuji->camera_state == FUJI_MULTIPLE_TRANSFER) {
			mode = FUJI_VIEW_MULTIPLE;
		} else if (fuji->camera_state == FUJI_FULL_ACCESS) {
			mode = FUJI_VIEW_ALL_IMGS;
		} else if (fuji->camera_state == FUJI_PC_AUTO_SAVE) {
			mode = FUJI_OLD_REMOTE;
		} else {
			mode = FUJI_VIEW_ALL_IMGS;
		}
	}

	ptp_verbose_log("Setting mode to %d\n", mode);

	// On newer cams, setting client state causes cam to have a dialog (Yes/No accept connection)
	// We have to wait for a response in this case
	r->wait_for_response = 255;

	rc = ptp_set_prop_value16(r, PTP_DPC_FUJI_ClientState, mode);
	if (rc) return rc;

	rc = fuji_get_events(r);
	if (rc) return rc;

	return 0;
}


int fuji_config_version_(struct PtpRuntime *r) {
	struct FujiDeviceKnowledge *fuji = fuji_get(r);
	int rc = 0;
	if (fuji->camera_state == FUJI_PC_AUTO_SAVE) {
		rc = ptp_get_prop_value(r, PTP_DPC_FUJI_AutoSaveVersion);
		if (rc) return rc;
		int code = ptp_parse_prop_value(r);
		rc = ptp_set_prop_value(r, PTP_DPC_FUJI_AutoSaveVersion, code);
		if (rc) return rc;
	} else if (fuji->remote_version == -1) {
		rc = ptp_get_prop_value(r, PTP_DPC_FUJI_GetObjectVersion);
		if (rc) return rc;

		int version = ptp_parse_prop_value(r);

		//fuji->image_view_version = version;

		// The property must be set again (to it's own value) to tell the camera
		// that the current version is supported - Fuji's app does this, so we assume it's necessary
		rc = ptp_set_prop_value(r, PTP_DPC_FUJI_GetObjectVersion, version);
		if (rc) return rc;
	} else {
		// Some cams set from 2000a to 2000b
		// Others set 20006 to 2000c (?)
		// X-T20 has 20004
		// Setting to the highest (supported?) value (2000c) seems to be what Fuji does
		rc = ptp_set_prop_value(r, PTP_DPC_FUJI_RemoteVersion, FUJI_CAM_CONNECT_REMOTE_VER);
		if (rc) return rc;

		// Don't understand this object yet - has some kind of important data
		struct PtpObjectInfo oi;
		rc = ptp_get_object_info(r, 0xfffffff1, &oi);
		if (rc == PTP_CHECK_CODE) {
			ptp_verbose_log("Didn't get valid info for 0xfffffff1\n");
		} else if (rc) {
			return rc;
		} else {
			char buffer[512];
			ptp_object_info_json(&oi, buffer, sizeof(buffer));
			ptp_verbose_log("0xfffffff1: %s\n", buffer);
		}
	}

	return 0;
}
int fuji_config_version(struct PtpRuntime *r) {
	ptp_mutex_lock(r);
	int rc = fuji_config_version_(r);
	ptp_mutex_unlock(r);
	return rc;
}

int fuji_config_device_info_routine(struct PtpRuntime *r) {
	struct FujiDeviceKnowledge *fuji = fuji_get(r);
	if (fuji->remote_version != -1 && fuji->camera_state != FUJI_PC_AUTO_SAVE) {
		int rc = fuji_get_device_info(r);
		if (rc) return rc;

		fuji_register_device_info(r, ptp_get_payload(r));

		// TODO: Parse device info
		// Only useful for later on when we want to do property setting/getting
	}

	return 0;
}

// Tell camera to open event/video sockets
int fuji_remote_mode_open_sockets(struct PtpRuntime *r) {
	struct FujiDeviceKnowledge *fuji = fuji_get(r);
	if (fuji->remote_version == -1) return 0;

	// Begin camera remote - (per spec, OpenCapture is much more broad than 'take picture')
	// This tells the camera to open the remote mode sockets (video/event)
	fuji->open_capture_trans_id = r->transaction;
	int rc = ptp_init_open_capture(r, 0, 0);
	if (rc) return rc;

	return 0;
}

// 'End' remote mode (or more like finish setup)
int fuji_remote_mode_end(struct PtpRuntime *r) {
	struct FujiDeviceKnowledge *fuji = fuji_get(r);
	if (fuji->remote_version == -1) return 0;

	// Right after remote mode is entered, camera gives off a bunch of properties
	int rc = fuji_get_events(r);
	if (rc) return rc;

	rc = ptp_terminate_open_capture(r, fuji->open_capture_trans_id);
	if (rc) return rc;

	return 0;
}

// Adapted from petabyt/libfuji fuji_xapp_config_gallery (MIT).
static int fuji_xapp_gallery(struct PtpRuntime *r) {
    app_print("XApp 图库：读取版本 DF28");
    int rc = ptp_get_prop_value(r, 0xdf28);
    if (rc) { app_print("XApp 图库操作失败：rc=%d，响应=0x%x", rc, ptp_get_return_code(r)); return rc; }
    int version = ptp_parse_prop_value(r);
    app_print("XApp 图库：写入版本 DF28");
    rc = ptp_set_prop_value(r, 0xdf28, version);
    if (rc) { app_print("XApp 图库操作失败：rc=%d，响应=0x%x", rc, ptp_get_return_code(r)); return rc; }
    app_print("XApp 图库：配置 D226");
    rc = ptp_set_prop_value16(r, 0xd226, 0);
    if (rc) { app_print("XApp 图库操作失败：rc=%d，响应=0x%x", rc, ptp_get_return_code(r)); return rc; }
    app_print("XApp 图库：配置 D227");
    rc = ptp_set_prop_value16(r, 0xd227, 0);
    if (rc) { app_print("XApp 图库操作失败：rc=%d，响应=0x%x", rc, ptp_get_return_code(r)); return rc; }
    struct PtpCommand cmd = {0};
    app_print("XApp 图库：请求 9054");
    cmd.code = 0x9054; cmd.param_length = 1; cmd.params[0] = 0x10000001;
    rc = xapp_optional_object_probe(r, &cmd); if (rc) return rc;
    app_print("XApp 图库：请求 9055");
    cmd.code = 0x9055;
    rc = xapp_optional_object_probe(r, &cmd); if (rc) return rc;
    app_print("XApp 图库：请求 9050");
    cmd.code = 0x9050; cmd.param_length = 0;
    rc = ptp_send(r, &cmd); if (rc) { app_print("XApp 图库操作失败：rc=%d，响应=0x%x", rc, ptp_get_return_code(r)); return rc; }
    app_print("XApp 图库：读取 D22B");
    rc = ptp_get_prop_value(r, 0xd22b); if (rc) { app_print("XApp 图库操作失败：rc=%d，响应=0x%x", rc, ptp_get_return_code(r)); return rc; }
    app_print("XApp 图库：请求照片索引 9053");
    cmd.code = 0x9053; cmd.param_length = 2; cmd.params[0] = 0; cmd.params[1] = 30000;
    rc = ptp_send(r, &cmd); if (rc) { app_print("XApp 图库操作失败：rc=%d，响应=0x%x", rc, ptp_get_return_code(r)); return rc; }
    app_print("XApp 图库：读取照片数量 D620");
    rc = ptp_get_prop_value(r, 0xd620); if (rc) { app_print("XApp 图库操作失败：rc=%d，响应=0x%x", rc, ptp_get_return_code(r)); return rc; }
    int count = ptp_parse_prop_value(r);
    if (count < 0 || count > 30000) return PTP_RUNTIME_ERR;
    fuji_get(r)->num_objects = count;
    app_print("XApp 图库：照片数量=%d，读取索引 D621", count);
    rc = ptp_get_prop_value(r, 0xd621);
    if (rc) app_print("照片索引读取失败：rc=%d，响应=0x%x", rc, ptp_get_return_code(r));
    if (!rc) {
        int size=ptp_get_payload_length(r);
        const uint8_t *data=ptp_get_payload(r);
        app_print("D621 索引：字节数=%d，声明数量=%u", size, size>=4?xapp_u32(data):0);
        int *handles=count>0?malloc(sizeof(int)*count):NULL;
        free(fuji_get(r)->xapp_handles); fuji_get(r)->xapp_handles=NULL;
        if (handles && xapp_parse_index(data, size<0?0:(size_t)size, count, handles)) {
            fuji_get(r)->xapp_handles=handles;
            app_print("采用 D621 实际编号：首项=0x%x，末项=0x%x", handles[0], handles[count-1]);
        } else {
            free(handles);
            app_print("D621 不符合 UINT32 编号数组格式，保留 libfuji 的顺序编号。");
        }
        app_print("相机状态=%d，远程协议=0x%x，对象协议=0x%x", fuji_get(r)->camera_state, fuji_get(r)->remote_version, fuji_get(r)->get_object_version);
        app_print("照片索引就绪，等待相机准备缩略图…"); usleep(1000000);
    }
    return rc;
}

int fuji_config_image_viewer(struct PtpRuntime *r) {
	struct FujiDeviceKnowledge *fuji = fuji_get(r);
    if (fuji->transport == FUJI_FEATURE_XAPP_WIRELESS_COMM) return fuji_xapp_gallery(r);
	if (r->connection_type == PTP_USB) return 0;
	if (fuji->transport == FUJI_FEATURE_WIRELESS_TETHER) return 0;
	plat_dbg("remote_image_view_version: %X", fuji->remote_image_view_version);
	if (fuji->remote_image_view_version != -1) {
		int rc = fuji_get_events(r);
		if (rc) return rc;

		// Tell the camera that we actually want that mode
		rc = ptp_set_prop_value16(r, PTP_DPC_FUJI_CameraState, FUJI_REMOTE_ACCESS);
		if (rc) return rc;

		rc = fuji_get_events(r);
		if (rc) return rc;

		// Will confirm CameraState is set
		// This will also update a bunch of other properties
		rc = fuji_get_events(r);
		if (rc) return rc;

		// Check SD card slot, not really useful for now
		//rc = ptp_get_prop_value(r, PTP_DPC_FUJI_StorageID);
		//if (rc) return rc;
		//ptp_verbose_log("Storage ID: %d\n", ptp_parse_prop_value(r));

		// Now we finally enter the remote image viewer
		rc = ptp_set_prop_value16(r, PTP_DPC_FUJI_ClientState, FUJI_MODE_REMOTE_IMG_VIEW);
		if (rc) return rc;

		rc = fuji_get_events(r);
		if (rc) return rc;

		rc = ptp_get_prop_value(r, PTP_DPC_FUJI_RemoteGetObjectVersion);
		fuji->remote_image_view_version = ptp_parse_prop_value(r);
		if (rc) return rc;

		// Set the prop higher - X-S10 and X-H1 want 4
		rc = ptp_set_prop_value(r, PTP_DPC_FUJI_RemoteGetObjectVersion, 5);
		if (rc) return rc;

		// The props we set should show up here
		rc = fuji_get_events(r);
		if (rc) return rc;
	}

	return 0;
}

static inline int do_download(int mask, int format) {
	if (mask & PTP_SELET_JPEG && format == PTP_OF_JPEG) return 1;
	if (mask & PTP_SELET_MOV && format == PTP_OF_MOV) return 1;
	if (mask & PTP_SELET_RAW && format == PTP_OF_RAW) return 1;
	return 0;
}

int fuji_import_objects(struct PtpRuntime *r, int *object_ids, int length, int mask) {
	int rc;
	for (int i = 0; i < length; i++) {
		struct PtpObjectInfo *oi = ptp_object_service_get(r, r->oc, object_ids[i]);

		struct PtpObjectInfo temp_oi;
		if (oi == NULL) {
			rc = ptp_get_object_info(r, object_ids[i], &temp_oi);
			if (rc) return rc;
			oi = &temp_oi;
		}

		if (!do_download(mask, oi->obj_format)) continue;

		app_downloading_file(oi);

		char path[256];
		app_get_file_path(path, oi->filename);

		struct stat buffer;
		if (stat(path, &buffer) == 0) {
			ptp_verbose_log("File already exists");
			continue;
		}

		FILE *f = fopen(path, "wb");
		if (f == NULL) {
			ptp_verbose_log("fopen(%s) failed", path);
			return PTP_RUNTIME_ERR;
		}

		rc = ptp_download_object(r, object_ids[i], f, 0x100000);
		fclose(f);
		if (rc) {
			app_print("保存 %s 失败：%s", oi->filename, ptp_perror(rc));
			return rc;
		}

		if (app_check_thread_cancel()) {
			return 0;
		}

		app_downloaded_file(oi, path);
	}

	return 0;
}

static long get_ms(void) {
	struct timespec ts;
	clock_gettime(CLOCK_MONOTONIC, &ts);
	return (long)(ts.tv_sec * 1000000L + ts.tv_nsec / 1000L);
}

int fuji_download_file(struct PtpRuntime *r, int handle, int file_size, int (handle_add)(void *, void *, int, int), void *arg) {
	int rc = 0;

	ptp_verbose_log("Going to download object #%d\n", handle);

	rc = fuji_get(r)->transport == FUJI_FEATURE_XAPP_WIRELESS_COMM ? 0 : ptp_get_prop_value(r, PTP_DPC_FUJI_CompressionCutOff);
	if (rc) return rc;

	ptp_mutex_lock(r);

	long then = get_ms();

	// Makes sure to set the compression prop back to 0 after finished
	// (extra data won't go through for some reason)
	int read = 0;
	while (1) {
		if (app_check_thread_cancel()) {
			rc = PTP_CANCELED;
			goto end;
		}

		long then_c = get_ms();

		int cur = file_size - read;
		if (cur > FUJI_MAX_PARTIAL_OBJECT) cur = FUJI_MAX_PARTIAL_OBJECT;
		rc = ptp_get_partial_object(r, handle, read, cur);
		if (rc == PTP_CHECK_CODE) {
			goto end;
		} else if (rc) {
			plat_dbg("Download fail %d", rc);
			ptp_mutex_unlock(r);
			return rc;
		}

		size_t payload_size = ptp_get_payload_length(r);

		app_report_download_speed(get_ms() - then_c, payload_size);

		if (payload_size == 0) {
			rc = PTP_RUNTIME_ERR;
			goto end;
		}

        if (payload_size > (size_t)(file_size - read)) { rc = PTP_RUNTIME_ERR; goto end; }
        if (handle_add(arg, ptp_get_payload(r), (int)payload_size, read)) { rc = PTP_RUNTIME_ERR; goto end; }

		read += (int)payload_size;

		if (read >= file_size) {
			long now = get_ms();
			plat_dbg("Took %ld seconds", (now - then) / 1000 / 1000);
			rc = 0;
			goto end;
		}
	}

    end:;
    if (fuji_get(r)->transport == FUJI_FEATURE_XAPP_WIRELESS_COMM) {
        int cleanup_rc = ptp_set_prop_value16(r, 0xd226, 2);
        if (!rc) rc = cleanup_rc;
    }
    if (fuji_get(r)->transport == FUJI_FEATURE_WIRELESS_COMM) {
		rc = fuji_get_events(r);
		if (rc) {
			ptp_mutex_unlock(r);
			return rc;
		}
		rc = fuji_end_file_download(r);
	}
	ptp_mutex_unlock(r);
	return rc;
}

// Functionality of FUJI_MULTIPLE_TRANSFER
int fuji_download_classic(struct PtpRuntime *r) {
	while (1) {
		// This determines whether the connection is terminated or not
		struct PtpObjectInfo oi;
		int rc = ptp_get_object_info(r, 1, &oi);
		if (rc) return rc;

		app_downloading_file(&oi);

		char path[256];
		app_get_file_path(path, oi.filename);
		FILE *f = fopen(path, "wb");
		if (f == NULL) return PTP_RUNTIME_ERR;

		// Not sure if 0x100000 is required or not, but we'll do what Fuji is doing.
		rc = ptp_download_object(r, 1, f, 0x100000);
		fclose(f);
		if (rc) {
			app_print("保存 %s 失败：%s", oi.filename, ptp_perror(rc));
			return rc;
		}

		app_downloaded_file(&oi, path);

		// Fuji's filesystem will swap out object ID 1 with the next image. If there
		// are no more images, the camera shuts down the connection and turns off.

		// In other words, the camera is in superposition - it's on and off at the same time.
		// We don't know until we observe it:
		rc = fuji_get_events(r);
		if (rc) return 0;
	}
}

int ptp_fuji_get_object_handles(struct PtpRuntime *r, struct PtpArray **a) {
	struct FujiDeviceKnowledge *fuji = fuji_get(r);
	if (r->connection_type == PTP_USB) {
		return ptp_get_object_handles(r, -1, 0x0, 0x0, a);
	} else {
		// By this point num_objects should be known
		if (fuji->num_objects == 0 || fuji->num_objects == -1) {
			return PTP_RUNTIME_ERR;
		}

		// (Object handles 0x0 is invalid, as per spec)
		struct PtpArray *list = malloc(sizeof(int) * fuji->num_objects + sizeof(struct PtpArray));
		list->length = fuji->num_objects;
		for (int i = 0; i < fuji->num_objects; i++) {
			list->data[i] = fuji->xapp_handles ? fuji->xapp_handles[i] : i + 1;
		}
		(*a) = list;
	}
	return 0;
}
