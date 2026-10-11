#include <libpict.h>
#include "backend.h"
#include "app.h"
#include "fuji.h"
#include "console_read.h"
JNI_FUNC(jlongArray,cReadCameraParameters)(JNIEnv *env,jobject thiz){
 set_jni_env(env);struct PtpRuntime *r=ptp_get();
 const int codes[]={0x5007,0x500d,0x500f,0x5005,0x5010,0x500e,0x500a,0x5001};
 const int alternate[]={0xd218,0xd219,0,0,0,0,0,0};
 jlong values[56];for(int i=0;i<8;i++){for(int j=0;j<7;j++)values[i*7+j]=0;values[i*7]=PTP_CANCELED;values[i*7+4]=codes[i];}
 ptp_mutex_lock(r);uint8_t wait=r->wait_for_response,def=r->response_wait_default;r->wait_for_response=r->response_wait_default=2;
 int direct_only=r->connection_type!=PTP_USB&&fuji_get(r)->transport==FUJI_FEATURE_XAPP_WIRELESS_COMM;
 app_print("控制台读取方式：%s",direct_only?"XApp 直接读值（不请求描述符）":"标准属性描述符");
 app_print("开始读取控制台：连接类型=%d，富士传输模式=%d（只读，不切换相机模式）",r->connection_type,fuji_get(r)->transport);
 for(int i=0;i<8&&!r->io_kill_switch;i++){
  struct ConsoleValue out=console_read_for_mode(r,codes[i],i==4?1:i==2?2:0,direct_only);
  if(alternate[i]&&(console_rejected(out.rc,out.response)||out.rc==PTP_UNSUPPORTED)&&!r->io_kill_switch){
   app_print("标准属性未开放或格式未适配，尝试富士属性 0x%x",alternate[i]);
   out=console_read_for_mode(r,alternate[i],i==1,direct_only);
  }
  values[i*7]=out.rc;values[i*7+1]=out.response;values[i*7+2]=out.value;values[i*7+3]=out.writable;values[i*7+4]=out.code;values[i*7+5]=out.type;values[i*7+6]=out.length;
  app_print("控制台结果 0x%x：rc=%d，响应=0x%x，数值=%lld，访问标志=%d，类型=0x%x",out.code,out.rc,out.response,(long long)out.value,out.writable,out.type);
  if(out.rc&&out.rc!=PTP_CHECK_CODE&&out.rc!=PTP_UNSUPPORTED)break;
 }
 r->wait_for_response=wait;r->response_wait_default=def;ptp_mutex_unlock(r);
 jlongArray result=(*env)->NewLongArray(env,56);if(result)(*env)->SetLongArrayRegion(env,result,0,56,values);return result;
}
