#ifndef FUJI_CONSOLE_READ_H
#define FUJI_CONSOLE_READ_H
#include "console_property.h"
struct ConsoleValue {int rc,response,writable,code,type,length;int64_t value;};
static int console_scalar(const uint8_t *data,size_t length,int is_signed,int64_t *value){
 if(!data||(length!=1&&length!=2&&length!=4))return -1;
 uint32_t v=0;for(size_t i=0;i<length;i++)v|=(uint32_t)data[i]<<(8*i);
 if(is_signed)*value=length==1?(int8_t)v:length==2?(int16_t)v:(int32_t)v;else *value=v;
 return 0;
}
static int console_rejected(int rc,int response){return rc==PTP_CHECK_CODE&&(response==0x2005||response==0x200a);}
/* Use direct value reads for XApp; descriptor support is not established:
 * X-E5 1.12 can leave 1014 unanswered, poisoning the shared gallery session. */
static struct ConsoleValue console_read_value(struct PtpRuntime *r,int code,int signed_hint){
 struct ConsoleValue out={.rc=PTP_CANCELED,.code=code};
 if(r->io_kill_switch)return out;
 out.rc=ptp_get_prop_value(r,code);out.response=(out.rc==0||out.rc==PTP_CHECK_CODE)?ptp_get_return_code(r):0;
 if(!out.rc){
  size_t n=ptp_get_payload_length(r);out.length=(int)n;
  const uint8_t *raw=ptp_get_payload(r);char hex[65]={0};
  for(size_t j=0;raw&&j<n&&j<32;j++)snprintf(hex+j*2,3,"%02x",raw[j]);
  app_print("控制台数据 0x%x：长度=%u，前32字节=%s",code,(unsigned)n,hex);
  int signed_value=signed_hint==1||(signed_hint==2&&n==4);
  if(console_scalar(ptp_get_payload(r),n,signed_value,&out.value))out.rc=PTP_UNSUPPORTED;
  else{out.writable=2;out.type=(n==1?1:n==2?3:5)+(signed_value?0:1);}
 }
 app_print("控制台直接读值 0x%x：rc=%d，响应=0x%x，数值=%lld，类型=0x%x",code,out.rc,out.response,(long long)out.value,out.type);
 return out;
}
/* Read-only probes. Descriptor rejection can still allow GetDevicePropValue.
 * Never continue probing after lost IO, and never infer writable from a value. */
static struct ConsoleValue console_read_one(struct PtpRuntime *r,int code,int signed_hint){
 struct ConsoleValue out={.rc=PTP_CANCELED,.code=code};
 if(r->io_kill_switch)return out;
 struct PtpCommand command={0};command.code=PTP_OC_GetDevicePropDesc;command.param_length=1;command.params[0]=code;
 out.rc=ptp_send(r,&command);out.response=(out.rc==0||out.rc==PTP_CHECK_CODE)?ptp_get_return_code(r):0;
 if(!out.rc){
  const uint8_t *d=ptp_get_payload(r);size_t n=ptp_get_payload_length(r);
  if(!console_property(d,n,(uint16_t)code,&out.value,&out.writable)){out.type=d[2]|((unsigned)d[3]<<8);return out;}
  out.rc=PTP_UNSUPPORTED;
 }
 app_print("控制台描述符 0x%x：rc=%d，响应=0x%x",code,out.rc,out.response);
 if(!console_rejected(out.rc,out.response)&&out.rc!=PTP_UNSUPPORTED)return out;
 if(r->io_kill_switch){out.rc=PTP_CANCELED;return out;}
 return console_read_value(r,code,signed_hint);
}
static struct ConsoleValue console_read_for_mode(struct PtpRuntime *r,int code,int signed_hint,int direct_only){
 return direct_only?console_read_value(r,code,signed_hint):console_read_one(r,code,signed_hint);
}
#endif
