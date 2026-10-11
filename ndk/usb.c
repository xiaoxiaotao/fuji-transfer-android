// NDK backend for camlib
// Copyright 2024 (c) Daniel C
#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <sys/ioctl.h>
#include <linux/usbdevice_fs.h>
#include <string.h>
#include <libpict.h>
#include <fcntl.h>
#include <signal.h>
#include "app.h"
#include "android.h"

struct PtpCommPriv {
	jobject obj;
	jobject dev;
	int fd;
	int endpoint_in;
	int endpoint_out;
};

// TODO: Rename to ptp_comm_init
static struct PtpCommPriv *init_comm(struct PtpRuntime *r) {
	if (r->comm_priv == NULL) {
		r->comm_priv = calloc(1, sizeof(struct PtpCommPriv));
	}

	return r->comm_priv;
}

void ptp_comm_deinit(struct PtpRuntime *r) {
	// TODO:
}

static jobject get_usb_man(JNIEnv *env, jobject ctx) {
	jclass ClassContext = (*env)->FindClass(env, "android/content/Context");
	jfieldID lid_USB_SERVICE = (*env)->GetStaticFieldID(env, ClassContext, "USB_SERVICE", "Ljava/lang/String;");
	jobject USB_SERVICE = (*env)->GetStaticObjectField(env, ClassContext, lid_USB_SERVICE);

	jmethodID get_sys_service_m = (*env)->GetMethodID(env, (*env)->FindClass(env, "android/content/Context"), "getSystemService", "(Ljava/lang/String;)Ljava/lang/Object;");
	return (*env)->CallObjectMethod(env, ctx, get_sys_service_m, USB_SERVICE);
}

static jobject get_ptp_interface(JNIEnv *env, jobject dev) {
	jclass ClassUsbInterface = (*env)->FindClass(env, "android/hardware/usb/UsbInterface");
	jmethodID get_dev_class_m = (*env)->GetMethodID(env, ClassUsbInterface, "getInterfaceClass", "()I");
	jclass ClassUsbDevice = (*env)->FindClass(env, "android/hardware/usb/UsbDevice");
	jmethodID MethodgetInterfaceCount = (*env)->GetMethodID(env, ClassUsbDevice, "getInterfaceCount", "()I");
	jmethodID MethodgetInterface = (*env)->GetMethodID(env, ClassUsbDevice, "getInterface", "(I)Landroid/hardware/usb/UsbInterface;");
	int ifaceCount = (*env)->CallIntMethod(env, dev, MethodgetInterfaceCount);
	for (int i = 0; i < ifaceCount; i++) {
		jobject interf = (*env)->CallObjectMethod(env, dev, MethodgetInterface, i);
		int interf_class = (*env)->CallIntMethod(env, interf, get_dev_class_m);
		if (interf_class == 6) {
			return interf;
		}
	}
	return NULL;
}

struct PtpDeviceEntry *ptpusb_device_list(struct PtpRuntime *r) {
    JNIEnv *env=get_jni_env();jobject ctx=get_jni_ctx();
    (*env)->PushLocalFrame(env,100);
    jobject man=get_usb_man(env,ctx);
    jclass man_c=(*env)->FindClass(env,"android/hardware/usb/UsbManager");
    jobject map=(*env)->CallObjectMethod(env,man,(*env)->GetMethodID(env,man_c,"getDeviceList","()Ljava/util/HashMap;"));
    jclass map_c=(*env)->FindClass(env,"java/util/HashMap");
    jobject values=(*env)->CallObjectMethod(env,map,(*env)->GetMethodID(env,map_c,"values","()Ljava/util/Collection;"));
    jclass coll_c=(*env)->FindClass(env,"java/util/Collection");
    jobject iter=(*env)->CallObjectMethod(env,values,(*env)->GetMethodID(env,coll_c,"iterator","()Ljava/util/Iterator;"));
    jclass iter_c=(*env)->FindClass(env,"java/util/Iterator");
    jmethodID has_next=(*env)->GetMethodID(env,iter_c,"hasNext","()Z"),next=(*env)->GetMethodID(env,iter_c,"next","()Ljava/lang/Object;");
    jclass dev_c=(*env)->FindClass(env,"android/hardware/usb/UsbDevice"),iface_c=(*env)->FindClass(env,"android/hardware/usb/UsbInterface"),ep_c=(*env)->FindClass(env,"android/hardware/usb/UsbEndpoint");
    jmethodID vid_m=(*env)->GetMethodID(env,dev_c,"getVendorId","()I"),pid_m=(*env)->GetMethodID(env,dev_c,"getProductId","()I");
    jmethodID count_m=(*env)->GetMethodID(env,iface_c,"getEndpointCount","()I"),ep_m=(*env)->GetMethodID(env,iface_c,"getEndpoint","(I)Landroid/hardware/usb/UsbEndpoint;");
    jmethodID type_m=(*env)->GetMethodID(env,ep_c,"getType","()I"),addr_m=(*env)->GetMethodID(env,ep_c,"getAddress","()I");
    struct PtpDeviceEntry *head=NULL,*tail=NULL;int total=0,valid=0;
    while((*env)->CallBooleanMethod(env,iter,has_next)){
        (*env)->PushLocalFrame(env,16);
        jobject dev=(*env)->CallObjectMethod(env,iter,next);int vid=(*env)->CallIntMethod(env,dev,vid_m),pid=(*env)->CallIntMethod(env,dev,pid_m);total++;
        jobject iface=get_ptp_interface(env,dev);
        app_print("USB 设备：VID=0x%04x，PID=0x%04x，PTP 接口=%s",vid,pid,iface?"有":"无");
        if(iface){
            int in=0,out=0,count=(*env)->CallIntMethod(env,iface,count_m);
            for(int i=0;i<count;i++){jobject ep=(*env)->CallObjectMethod(env,iface,ep_m,i);int type=(*env)->CallIntMethod(env,ep,type_m),addr=(*env)->CallIntMethod(env,ep,addr_m);if(type==2){if(addr&0x80)in=addr;else out=addr;}(*env)->DeleteLocalRef(env,ep);}
            if(in&&out){
                struct PtpDeviceEntry *entry=calloc(1,sizeof(*entry));
                if(!entry){(*env)->PopLocalFrame(env,NULL);break;}
                entry->vendor_id=vid;entry->product_id=pid;entry->id=-1;entry->endpoint_in=in;entry->endpoint_out=out;
                entry->device_handle_ptr=(void *)(*env)->NewGlobalRef(env,dev);
                if(tail){tail->next=entry;entry->prev=tail;}else head=entry;tail=entry;valid++;
            }else app_print("USB 接口缺少双向批量端点，请确认相机为 USB 读卡器模式");
        }
        (*env)->PopLocalFrame(env,NULL);
    }
    app_print("USB 检测：系统设备=%d，PTP 设备=%d",total,valid);
    if(!total)app_print("手机系统尚未识别 USB 设备：请检查数据线、OTG/USB 主机模式与相机供电/通信设置");
    (*env)->PopLocalFrame(env,NULL);return head;
}

static int get_usb_permission(JNIEnv *env, jobject ctx, jobject man, jobject device) {
	(*env)->PushLocalFrame(env, 20);

	jclass man_c = (*env)->FindClass(env, "android/hardware/usb/UsbManager");
	jmethodID has_perm_m = (*env)->GetMethodID(env, man_c, "hasPermission", "(Landroid/hardware/usb/UsbDevice;)Z");
	if ((*env)->CallBooleanMethod(env, man, has_perm_m, device)) { (*env)->PopLocalFrame(env, NULL); return 0; }

	jstring pkg = jni_get_package_name(env, ctx);
	jstring perm = jni_concat_strings2(env, pkg, (*env)->NewStringUTF(env, ".USB_PERMISSION"));

	jclass intent_c = (*env)->FindClass(env, "android/content/Intent");
	jmethodID intent_init = (*env)->GetMethodID(env, intent_c, "<init>", "(Ljava/lang/String;)V");
	jobject permission_intent = (*env)->NewObject(env, intent_c, intent_init, perm);
	(*env)->CallObjectMethod(env, permission_intent, (*env)->GetMethodID(env, intent_c, "setPackage", "(Ljava/lang/String;)Landroid/content/Intent;"), pkg);

	jclass pending_intent_class = (*env)->FindClass(env, "android/app/PendingIntent");
	jmethodID pending_intent_get_broadcast = (*env)->GetStaticMethodID(env, pending_intent_class, "getBroadcast", "(Landroid/content/Context;ILandroid/content/Intent;I)Landroid/app/PendingIntent;");

	const jint FLAG_IMMUTABLE = 0x04000000;
	jobject pending_intent = (*env)->CallStaticObjectMethod(env, pending_intent_class, pending_intent_get_broadcast, ctx, 0, permission_intent, FLAG_IMMUTABLE);

	if (pending_intent == NULL) {
		(*env)->PopLocalFrame(env, NULL);
		return PTP_NO_PERM;
	}

	jmethodID req_perm_m = (*env)->GetMethodID(env, man_c, "requestPermission", "(Landroid/hardware/usb/UsbDevice;Landroid/app/PendingIntent;)V");
	(*env)->CallVoidMethod(env, man, req_perm_m, device, pending_intent);

	app_print("请允许系统弹窗中的 USB 相机访问权限（最多等待 30 秒）");
	for (int i = 0; i < 60; i++) {
		if ((*env)->CallBooleanMethod(env, man, has_perm_m, device)) {
			(*env)->PopLocalFrame(env, NULL);
			return 0;
		}
		usleep(500 * 1000);
	}

	(*env)->PopLocalFrame(env, NULL);
	return PTP_NO_PERM;
}

int ptp_device_open(struct PtpRuntime *r, struct PtpDeviceEntry *entry) {
	JNIEnv *env = get_jni_env();
	jobject ctx = get_jni_ctx();
	(*env)->PushLocalFrame(env, 20);

	jobject man = get_usb_man(env, ctx);
	jclass man_c = (*env)->FindClass(env, "android/hardware/usb/UsbManager");

	int permission = get_usb_permission(env, ctx, man, (jobject)entry->device_handle_ptr);
	if (permission) { app_print("USB 访问权限未获得，请允许后重试"); (*env)->PopLocalFrame(env, NULL); return permission; }

	jmethodID open_dev_m = (*env)->GetMethodID(env, man_c, "openDevice", "(Landroid/hardware/usb/UsbDevice;)Landroid/hardware/usb/UsbDeviceConnection;");
	jobject connection = (*env)->CallObjectMethod(env, man, open_dev_m, (jobject)entry->device_handle_ptr);
	if (connection == NULL) {
		(*env)->PopLocalFrame(env, NULL);
		app_print("USB openDevice 失败：设备可能已断开或被其他导入应用占用");
		return PTP_OPEN_FAIL;
	}

	jclass connection_c = (*env)->FindClass(env, "android/hardware/usb/UsbDeviceConnection");
	jobject iface = get_ptp_interface(env, (jobject)entry->device_handle_ptr);
	jmethodID claim = (*env)->GetMethodID(env, connection_c, "claimInterface", "(Landroid/hardware/usb/UsbInterface;Z)Z");
	if (!iface || !(*env)->CallBooleanMethod(env, connection, claim, iface, JNI_TRUE)) {
		(*env)->CallVoidMethod(env, connection, (*env)->GetMethodID(env, connection_c, "close", "()V"));
		app_print("无法占用 USB PTP 接口，请关闭其他相机导入应用后重试");
		(*env)->PopLocalFrame(env, NULL); return PTP_OPEN_FAIL;
	}
	app_print("USB PTP 接口已占用，IN=0x%x，OUT=0x%x", entry->endpoint_in, entry->endpoint_out);
	struct PtpCommPriv *priv = init_comm(r);
	priv->obj = (*env)->NewGlobalRef(env, connection);
	priv->dev = (*env)->NewGlobalRef(env, (jobject)entry->device_handle_ptr);

	jmethodID get_desc_m = (*env)->GetMethodID(env, (*env)->FindClass(env, "android/hardware/usb/UsbDeviceConnection"), "getFileDescriptor", "()I");
	priv->fd = (*env)->CallIntMethod(env, connection, get_desc_m);

	r->io_kill_switch = 0;
	r->operation_kill_switch = 0;

	//jmethodID get_max_packet_size_m = (*env)->GetMethodID(env, usb_endpoint_c, "getMaxPacketSize", "()I" );
	jclass iface_c=(*env)->FindClass(env,"android/hardware/usb/UsbInterface"),ep_c=(*env)->FindClass(env,"android/hardware/usb/UsbEndpoint");
	jmethodID count_m=(*env)->GetMethodID(env,iface_c,"getEndpointCount","()I"),ep_m=(*env)->GetMethodID(env,iface_c,"getEndpoint","(I)Landroid/hardware/usb/UsbEndpoint;");
	r->max_packet_size = 512;
	for(int i=0;i<(*env)->CallIntMethod(env,iface,count_m);i++){jobject ep=(*env)->CallObjectMethod(env,iface,ep_m,i);if((*env)->CallIntMethod(env,ep,(*env)->GetMethodID(env,ep_c,"getAddress","()I"))==entry->endpoint_in)r->max_packet_size=(*env)->CallIntMethod(env,ep,(*env)->GetMethodID(env,ep_c,"getMaxPacketSize","()I"));(*env)->DeleteLocalRef(env,ep);}

	priv->endpoint_in = (int)entry->endpoint_in;
	priv->endpoint_out = (int)entry->endpoint_out;

	(*env)->PopLocalFrame(env, NULL);
	return 0;
}

void ptpusb_free_device_list_entry(void *ptr) {
	JNIEnv *env = get_jni_env();
	// TODO: release interface on dev object
	(*env)->DeleteGlobalRef(env, (jobject)ptr);
}

int ptp_device_connect(struct PtpRuntime *r) {
	// This can be a portable function in camlib/src/lib.c
	return -1;
}

int ptp_cmd_write(struct PtpRuntime *r, void *to, unsigned int length) {
	if (r->io_kill_switch) return -1;
	struct PtpCommPriv *priv = init_comm(r);
	struct usbdevfs_bulktransfer ctrl;
	ctrl.ep = priv->endpoint_out;
	ctrl.len = length;
	ctrl.data = to;
	ctrl.timeout = PTP_TIMEOUT;
	int rc = ioctl(priv->fd, USBDEVFS_BULK, &ctrl);
	return rc;
}

int ptp_cmd_read(struct PtpRuntime *r, void *to, unsigned int length) {
	if (r->io_kill_switch) return -1;
	struct PtpCommPriv *priv = init_comm(r);
	struct usbdevfs_bulktransfer ctrl;
	ctrl.ep = priv->endpoint_in;
	ctrl.len = length;
	ctrl.data = to;
	ctrl.timeout = PTP_TIMEOUT;
	int rc = ioctl(priv->fd, USBDEVFS_BULK, &ctrl);
	if (rc > 0) ptp_report_read_progress((unsigned int)rc);
	return rc;
}

int ptpusb_get_status(struct PtpRuntime *r) {
	if (r->io_kill_switch) return -100;
	struct PtpCommPriv *priv = init_comm(r);
	struct usbdevfs_ctrltransfer ctrl;
	ctrl.bRequestType = 0x80;
	ctrl.bRequest = 0;
	ctrl.wValue = 0;
	ctrl.wIndex = 0;
	char buffer[2];
	ctrl.data = buffer;
	ctrl.wLength = sizeof(buffer);
	ctrl.timeout = PTP_TIMEOUT;
	int rc = ioctl(priv->fd, USBDEVFS_CONTROL, &ctrl);
	if (rc < 0) return -1;
	return 0;
}

int ptp_device_close(struct PtpRuntime *r) {
	JNIEnv *env = get_jni_env();
	struct PtpCommPriv *priv = r->comm_priv;
	if (!priv) return 0;
	if (!priv->obj) { free(priv); r->comm_priv=NULL; return 0; }
	jclass class = (*env)->GetObjectClass(env, priv->obj);

	jobject interf = get_ptp_interface(env, priv->dev);

	jclass conn_c = (*env)->FindClass(env, "android/hardware/usb/UsbDeviceConnection");
	jmethodID release_interf_m = (*env)->GetMethodID(env, conn_c, "releaseInterface", "(Landroid/hardware/usb/UsbInterface;)Z");
	(*env)->CallBooleanMethod(env, priv->obj, release_interf_m, interf);

	jmethodID close = (*env)->GetMethodID(env, class, "close", "()V");
	(*env)->CallVoidMethod(env, priv->obj, close);
	(*env)->DeleteGlobalRef(env, priv->obj);
	if(priv->dev)(*env)->DeleteGlobalRef(env, priv->dev);
	(*env)->DeleteLocalRef(env, interf);
	(*env)->DeleteLocalRef(env, class);
	(*env)->DeleteLocalRef(env, conn_c);
	free(priv);r->comm_priv=NULL;
	return 0;
}

int ptp_device_reset(struct PtpRuntime *r) {
	return -1;
}

int ptp_read_int(struct PtpRuntime *r, void *to, unsigned int length) {
	return -1;
}
