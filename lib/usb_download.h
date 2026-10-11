#ifndef FUJI_USB_DOWNLOAD_H
#define FUJI_USB_DOWNLOAD_H
#include <stdint.h>
#include <stdio.h>
/* Caller owns the session mutex and FILE. Use the declared size, never a short
 * chunk as EOF. A rejected first operation may fall back; never retry lost IO. */
static int fuji_usb_download_checked(struct PtpRuntime *r,int handle,FILE *file,uint32_t expected,uint32_t chunk){
 if(!file||!expected||!chunk)return PTP_RUNTIME_ERR;
 uint32_t offset=0;
 while(offset<expected){
  if(r->io_kill_switch)return PTP_CANCELED;
  uint32_t wanted=expected-offset;if(wanted>chunk)wanted=chunk;
  int rc=ptp_get_partial_object(r,handle,offset,wanted);
  if(rc){
   int response=ptp_get_return_code(r);
   /* Bound the full-object memory path. Larger files keep the chunked path. */
   if(offset==0&&rc==PTP_CHECK_CODE&&(response==0x2005||response==0x200a)&&expected<=64u*1024u*1024u&&!r->io_kill_switch){
    app_print("USB 相机拒绝分块下载，尝试完整对象下载（%u 字节）",expected);
    rc=ptp_get_object(r,handle);if(rc)return rc;
    unsigned int length=ptp_get_payload_length(r);
    if(length!=expected)return PTP_RUNTIME_ERR;
    return fwrite(ptp_get_payload(r),1,length,file)==length?0:PTP_RUNTIME_ERR;
   }
   return rc;
  }
  unsigned int length=ptp_get_payload_length(r);
  if(!length||length>wanted)return PTP_RUNTIME_ERR;
  if(fwrite(ptp_get_payload(r),1,length,file)!=length)return PTP_RUNTIME_ERR;
  offset+=length;
 }
 return 0;
}
#endif
