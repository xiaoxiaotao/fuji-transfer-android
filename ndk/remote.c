/* XApp liveview mode sequence adapted from petabyt/libfuji, MIT.
 * Keep the established command session, and serialize all camera operations. */
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <libpict.h>
#include "backend.h"
#include "app.h"
#include "fuji.h"
#include "fujiptp.h"
#include "remote_frame.h"
static int mode_changed,opened,active;
static long long now_ms(void){struct timespec t;clock_gettime(CLOCK_MONOTONIC,&t);return (long long)t.tv_sec*1000+t.tv_nsec/1000000;}
static int step(struct PtpRuntime *r,int rc,const char *label){if(rc==PTP_IO_ERR)app_print("监看：%s，通信失败 rc=%d（未收到有效响应）",label,rc);else app_print("监看：%s，rc=%d，响应=0x%x",label,rc,ptp_get_return_code(r));return rc;}
JNI_FUNC(jint,cRemoteStart)(JNIEnv *env,jobject thiz){
    set_jni_env(env);struct PtpRuntime *r=ptp_get();ptp_mutex_lock(r);int rc=0;uint8_t original_default=r->response_wait_default,original_wait=r->wait_for_response;r->response_wait_default=8;r->wait_for_response=8;
    struct FujiDeviceKnowledge *f=fuji_get(r);
    if(r->io_kill_switch||active||r->connection_type==PTP_USB||f->transport!=FUJI_FEATURE_XAPP_WIRELESS_COMM||f->remote_version<0x20007){rc=PTP_UNSUPPORTED;goto done;}
    mode_changed=opened=active=0;f->console_events_valid=0;f->console_remote_active=0;
    rc=step(r,ptp_set_prop_value16(r,PTP_DPC_FUJI_CameraState,FUJI_REMOTE_ACCESS),"设置相机状态 DF00=6");if(rc)goto done;mode_changed=1;
    rc=step(r,ptp_set_prop_value16(r,PTP_DPC_FUJI_ClientState,22),"设置 XApp 取景模式 DF01=22");if(rc)goto done;
    rc=step(r,ptp_get_prop_value(r,0xdf2a),"读取取景版本 DF2A");if(rc)goto done;
    int version=ptp_parse_prop_value(r);rc=step(r,ptp_set_prop_value(r,0xdf2a,version),"协商取景版本 DF2A");if(rc)goto done;
    f->open_capture_trans_id=r->transaction;
    rc=step(r,ptp_init_open_capture(r,0,0),"启动取景 InitiateOpenCapture");if(rc)goto done;opened=1;
    rc=step(r,fuji_get_events(r),"读取取景状态");if(rc)goto done;
    rc=step(r,ptpip_connect_events(r,f->ip_address,FUJI_EVENT_IP_PORT),"连接事件端口 55741");if(rc)goto done;
    rc=step(r,ptpip_connect_video(r,f->ip_address,FUJI_LIVEVIEW_IP_PORT),"连接视频端口 55742");if(rc)goto done;
    active=1;f->console_remote_active=1;
done:r->response_wait_default=original_default;r->wait_for_response=original_wait;ptp_mutex_unlock(r);return rc;
}
struct FrameReader {struct PtpRuntime *r;long long deadline;};
static int video_read(void *arg,void *data,unsigned int size){struct FrameReader *reader=arg;if(now_ms()>reader->deadline)return -1;return ptpip_video_read(reader->r,data,size);}
JNI_FUNC(jbyteArray,cRemoteFrame)(JNIEnv *env,jobject thiz){
    set_jni_env(env);struct PtpRuntime *r=ptp_get();ptp_mutex_lock(r);jbyteArray result=NULL;
    if(active&&!r->io_kill_switch){uint8_t *jpeg=NULL;unsigned int size=0;struct FrameReader reader={r,now_ms()+5000};int rc=fuji_frame_read(video_read,&reader,&jpeg,&size);
        if(rc==0){result=(*env)->NewByteArray(env,(jsize)size);if(result)(*env)->SetByteArrayRegion(env,result,0,(jsize)size,(const jbyte *)jpeg);}else app_print("监看视频读取失败：rc=%d（超时、连接关闭或帧格式不符）",rc);free(jpeg);
    }ptp_mutex_unlock(r);return result;
}
JNI_FUNC(jint,cRemoteCapture)(JNIEnv *env,jobject thiz){
    set_jni_env(env);struct PtpRuntime *r=ptp_get();ptp_mutex_lock(r);uint8_t original_default=r->response_wait_default,original_wait=r->wait_for_response;r->response_wait_default=8;r->wait_for_response=8;
    /* Standard PTP single capture. No continuous/Bulb commands or parameter writes. */
    int rc=active&&!r->io_kill_switch?step(r,ptp_init_capture(r,0,0),"单次快门 InitiateCapture 100E"):PTP_UNSUPPORTED;
    r->response_wait_default=original_default;r->wait_for_response=original_wait;ptp_mutex_unlock(r);return rc;
}
JNI_FUNC(void,cRemoteAbort)(JNIEnv *env,jobject thiz){
    set_jni_env(env);app_print("监看恢复超过 15 秒，终止等待并释放失效连接");ptpip_remote_interrupt(ptp_get());
}
JNI_FUNC(jint,cRemoteEnd)(JNIEnv *env,jobject thiz){
    set_jni_env(env);struct PtpRuntime *r=ptp_get();ptp_mutex_lock(r);int rc=0;uint8_t original_default=r->response_wait_default,original_wait=r->wait_for_response;r->response_wait_default=1;r->wait_for_response=1;active=0;fuji_get(r)->console_remote_active=0;
    if(!r->io_kill_switch&&mode_changed){
        if(opened)rc=step(r,ptp_terminate_open_capture(r,fuji_get(r)->open_capture_trans_id),"停止取景");
        if(rc)goto close_remote;
        int state=step(r,ptp_set_prop_value16(r,PTP_DPC_FUJI_CameraState,FUJI_REMOTE_ACCESS),"恢复相机状态");if(!rc)rc=state;
        if(rc)goto close_remote;
        int mode=step(r,ptp_set_prop_value16(r,PTP_DPC_FUJI_ClientState,20),"恢复图库模式 DF01=20");if(!rc)rc=mode;
        if(!mode){int gallery=fuji_config_image_viewer(r);if(!rc)rc=gallery;}
    }
close_remote:
    ptpip_remote_close(r);opened=mode_changed=0;r->response_wait_default=original_default;r->wait_for_response=original_wait;ptp_mutex_unlock(r);return rc;
}
