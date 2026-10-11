/* Exercise the production initialization/recovery path with a scripted transport.
 * No camera writes beyond PTP initialization are reachable from these tests. */
#include <assert.h>
#include <stdarg.h>
#include <unistd.h>
static int test_usleep(useconds_t delay) { (void)delay; return 0; }
#define usleep test_usleep
#include "../lib/fuji.c"
#undef usleep

static int mode, writes, reads, closes, connects, locks, reconnect_result;
static int recovery_progress;
static void progress(int stage) { assert(stage==2); recovery_progress++; }
static unsigned offset;
static unsigned char reply[68];
enum { SUCCESS, FIRST_READ_FAILURE, ALWAYS_READ_FAILURE, REJECTED,
       MALFORMED, CANCELLED, FIRST_WRITE_FAILURE };

void app_print(char *fmt, ...) { (void)fmt; }
void ptp_verbose_log(char *fmt, ...) { (void)fmt; }
void ptp_mutex_lock(struct PtpRuntime *r) { (void)r; assert(locks++ == 0); }
void ptp_mutex_unlock(struct PtpRuntime *r) { (void)r; assert(--locks == 0); }
int ptp_write_unicode_string(char *dest, const char *name) {
    unsigned n = 0;
    do { dest[n * 2] = name[n]; dest[n * 2 + 1] = 0; } while (name[n++]);
    return (int)n * 2;
}
int ptpip_cmd_write(struct PtpRuntime *r, void *data, unsigned size) {
    (void)r; (void)data; assert(locks == 1); writes++; offset = 0;
    if (mode == FIRST_WRITE_FAILURE && writes == 1) { errno = EPIPE; return -1; }
    return (int)size;
}
int ptpip_cmd_read(struct PtpRuntime *r, void *data, unsigned size) {
    assert(locks == 1); reads++;
    if (mode == CANCELLED) { r->io_kill_switch = 1; return -1; }
    if (mode == ALWAYS_READ_FAILURE || (mode == FIRST_READ_FAILURE && writes == 1)) {
        errno = ECONNRESET; return -1;
    }
    // Fragment both header and body to exercise the real response assembly.
    unsigned n = size > 3 ? 3 : size;
    memcpy(data, reply + offset, n); offset += n;
    return (int)n;
}
int ptpip_device_close(struct PtpRuntime *r) { (void)r; assert(locks == 1); closes++; return 0; }
int ptpip_connect(struct PtpRuntime *r, const char *ip, int port, int timeout) {
    assert(locks == 1); assert(!strcmp(ip,"192.168.0.1"));
    assert(port == FUJI_CMD_IP_PORT); assert(timeout == 3);
    connects++;
    if (!reconnect_result) r->io_kill_switch = r->operation_kill_switch = 0;
    return reconnect_result;
}
int ptp_fuji_get_init_info(struct PtpRuntime *r, struct PtpFujiInitResp *resp) {
    (void)r; memset(resp, 0, sizeof(*resp)); return 0;
}
int ptp_get_return_code(struct PtpRuntime *r) { (void)r; return 0; }

static int run_case(int behavior, int connect_rc) {
    unsigned char data[512] = {0};
    struct FujiDeviceKnowledge camera = {0};
    struct PtpRuntime runtime = {0};
    struct PtpFujiInitResp response = {0};
    strcpy(camera.ip_address,"192.168.0.1");
    camera.connection_progress=progress;
    runtime.userdata = &camera; runtime.data = data;
    mode = behavior; reconnect_result = connect_rc;
    writes = reads = closes = connects = locks = 0;
    recovery_progress=0;
    memset(reply, 0, sizeof(reply));
    ptp_write_u32(reply, behavior == REJECTED ? 8 : behavior == MALFORMED ? 4 : sizeof(reply));
    ptp_write_u32(reply + 4, behavior == REJECTED ? PTPIP_INIT_FAIL : PTPIP_INIT_COMMAND_ACK);
    int rc = fuji_init_with_recovery(&runtime,"Fudge-XE5",&response);
    assert(recovery_progress==connects);
    assert(locks == 0);
    return rc;
}
int main(void) {
    assert(run_case(SUCCESS,0) == 0); assert(writes == 1 && closes == 0 && connects == 0);
    assert(run_case(FIRST_READ_FAILURE,0) == 0); assert(writes == 2 && closes == 1 && connects == 1);
    assert(run_case(FIRST_WRITE_FAILURE,0) == 0); assert(writes == 2 && closes == 1 && connects == 1);
    assert(run_case(ALWAYS_READ_FAILURE,0) == PTP_IO_ERR); assert(writes == 2 && connects == 1);
    assert(run_case(FIRST_READ_FAILURE,-1) == -1); assert(writes == 1 && connects == 1);
    assert(run_case(REJECTED,0) == PTP_RUNTIME_ERR); assert(writes == 1 && connects == 0);
    assert(run_case(MALFORMED,0) == PTP_IO_ERR); assert(writes == 1 && connects == 0);
    assert(run_case(CANCELLED,0) == PTP_IO_ERR); assert(writes == 1 && connects == 0);
    puts("PTP initialization recovery: 8 cases passed");
    return 0;
}
