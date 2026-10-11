#include "FujiBridge.h"
#include "libpict.h"
#include "fuji.h"
#include "app.h"
#include "object.h"
#include "thumbnail_result.h"
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>
#include <limits.h>
#include <arpa/inet.h>
#include <sys/socket.h>
static struct PtpRuntime runtime;
static int initialized;
static fios_log_callback logger;
static fios_progress_callback progress;
static uint64_t received,total;
static void emit(char *fmt,va_list args){char text[2048];vsnprintf(text,sizeof text,fmt,args);if(logger)logger(text);}
void app_print(char *fmt,...){va_list a;va_start(a,fmt);emit(fmt,a);va_end(a);}
void plat_dbg(char *fmt,...){va_list a;va_start(a,fmt);emit(fmt,a);va_end(a);}
void ptp_verbose_log(char *fmt,...){va_list a;va_start(a,fmt);emit(fmt,a);va_end(a);}
void ptp_error_log(char *fmt,...){va_list a;va_start(a,fmt);emit(fmt,a);va_end(a);}
void ptp_panic(char *fmt,...){va_list a;va_start(a,fmt);emit(fmt,a);va_end(a);abort();}
struct PtpRuntime *ptp_get(void){return &runtime;}
char *app_get_client_name(void){return strdup("Fudge-XE5");}
void app_send_cam_name(const char *name){app_print("相机：%s",name);}
int app_get_os_network_handle(struct NetworkHandle *h){memset(h,0,sizeof *h);h->ignore=1;return 0;}
int app_get_wifi_network_handle(struct NetworkHandle *h){return app_get_os_network_handle(h);}
int app_bind_socket_to_network(int fd,struct NetworkHandle *h){return 0;}
int ptpip_set_extra_socket_settings(struct PtpRuntime *r,int fd){
#ifdef SO_NOSIGPIPE
 int on=1;return setsockopt(fd,SOL_SOCKET,SO_NOSIGPIPE,&on,sizeof on);
#else
 return 0;
#endif
}
void app_set_progress_bar(int active,int size){total=active?(uint64_t)size:0;received=0;}
void ptp_report_read_progress(unsigned int size){if(total){received+=size;if(progress)progress(received>total?total:received,total);}}
void fios_init(fios_log_callback log,fios_progress_callback callback){logger=log;progress=callback;if(!initialized){ptp_init(&runtime);fuji_reset_ptp(&runtime);initialized=1;}}
void ptp_comm_deinit(struct PtpRuntime *r){}
struct PtpDeviceEntry *ptpusb_device_list(struct PtpRuntime *r){return NULL;}
int ptp_device_open(struct PtpRuntime *r,struct PtpDeviceEntry *e){return PTP_UNSUPPORTED;}
void ptpusb_free_device_list_entry(void *p){free(p);}
void fios_free(void *buffer){free(buffer);}
void fios_disconnect(void){if(initialized)ptp_report_error(&runtime,"用户断开",0);}
int fios_connect(const char *ip,int protocol){
 struct in_addr addr;if(!initialized||!ip||inet_pton(AF_INET,ip,&addr)!=1||protocol<0||protocol>2)return PTP_RUNTIME_ERR;
 if(!runtime.io_kill_switch)return PTP_RUNTIME_ERR;
 if(runtime.comm_priv){ptpip_device_close(&runtime);free(runtime.comm_priv);runtime.comm_priv=NULL;}
 if(runtime.oc){ptp_free_object_service(runtime.oc);runtime.oc=NULL;}
 fuji_reset_ptp(&runtime);struct FujiDeviceKnowledge *f=fuji_get(&runtime);
 snprintf(f->ip_address,sizeof f->ip_address,"%s",ip);f->net.ignore=1;
 f->transport=protocol==2?FUJI_FEATURE_XAPP_WIRELESS_COMM:FUJI_FEATURE_WIRELESS_COMM;f->auto_protocol=protocol==0;
 int rc=ptpip_connect(&runtime,ip,FUJI_CMD_IP_PORT,3);
 if(!rc)rc=fuji_setup(&runtime);
 if(!rc)rc=fuji_config_image_viewer(&runtime);
 if(rc){ptp_report_error(&runtime,"连接失败",rc);if(runtime.comm_priv)ptpip_device_close(&runtime);runtime.io_kill_switch=1;}
 return rc;
}
int fios_ping(void){return initialized&&!runtime.io_kill_switch?fuji_get_events(&runtime):PTP_IO_ERR;}
int fios_handles(uint32_t **handles,size_t *count){
 *handles=NULL;*count=0;struct PtpArray *list=NULL;int rc=ptp_fuji_get_object_handles(&runtime,&list);if(rc)return rc;
 if(!list||list->length<0||list->length>100000){free(list);return PTP_RUNTIME_ERR;}
 if(runtime.oc){ptp_free_object_service(runtime.oc);runtime.oc=NULL;}
 runtime.oc=ptp_create_object_service(list->data,list->length,NULL,NULL);
 *count=list->length;if(*count){*handles=malloc(*count*sizeof(uint32_t));if(!*handles){free(list);return PTP_RUNTIME_ERR;}memcpy(*handles,list->data,*count*sizeof(uint32_t));}free(list);return 0;
}
int fios_info(uint32_t handle,char **json){
 *json=NULL;struct PtpObjectInfo oi;int rc=fuji_begin_download_get_object_info(&runtime,(int)handle,&oi);if(rc)return rc;
 char buffer[2048];ptp_object_info_json(&oi,buffer,sizeof buffer);*json=strdup(buffer);return *json?0:PTP_RUNTIME_ERR;
}
int fios_thumbnail(uint32_t handle,uint8_t **bytes,size_t *length){
 *bytes=NULL;*length=0;unsigned int offset=0,size=0;int rc=fuji_get_thumb(&runtime,(int)handle,&offset,&size);
 if(thumbnail_unavailable(rc,rc==PTP_CHECK_CODE?ptp_get_return_code(&runtime):0))return 0;if(rc)return rc;
 unsigned int payload=ptp_get_payload_length(&runtime);if(offset>payload||size>payload-offset)return PTP_RUNTIME_ERR;
 if(size){*bytes=malloc(size);if(!*bytes)return PTP_RUNTIME_ERR;memcpy(*bytes,(uint8_t *)ptp_get_payload(&runtime)+offset,size);*length=size;}return 0;
}
static int write_chunk(void *file,void *bytes,int size,int offset){return size>=0&&fwrite(bytes,1,(size_t)size,file)==(size_t)size?0:-1;}
int fios_download(uint32_t handle,const char *path,uint64_t expected){
 if(!path||!expected||expected>INT_MAX)return PTP_RUNTIME_ERR;
 struct PtpObjectInfo oi;int rc=fuji_begin_download_get_object_info(&runtime,(int)handle,&oi);if(rc)return rc;if(oi.compressed_size!=expected)return PTP_RUNTIME_ERR;
 FILE *file=fopen(path,"wb");if(!file)return PTP_RUNTIME_ERR;
 app_set_progress_bar(1,(int)expected);rc=fuji_download_file(&runtime,(int)handle,(int)expected,write_chunk,file);app_set_progress_bar(0,0);
 if(!rc&&(ferror(file)||ftell(file)!=(long)expected))rc=PTP_RUNTIME_ERR;
 if(fclose(file)&&!rc)rc=PTP_RUNTIME_ERR;if(rc)remove(path);return rc;
}
