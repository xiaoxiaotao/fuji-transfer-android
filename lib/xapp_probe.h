#ifndef FUJI_XAPP_PROBE_H
#define FUJI_XAPP_PROBE_H
/* These two reads probe a fixed extension object, not the real D621 index.
 * InvalidObjectHandle is tolerated only here; IO and other errors remain fatal. */
static int xapp_optional_object_probe(struct PtpRuntime *r,struct PtpCommand *cmd){
 int rc=ptp_send(r,cmd);
 if(rc==PTP_CHECK_CODE&&ptp_get_return_code(r)==0x2009&&(cmd->code==0x9054||cmd->code==0x9055)&&cmd->param_length==1&&cmd->params[0]==0x10000001){
  app_print("XApp 固定编号探测 0x%x 返回对象无效（0x2009），继续读取真实照片索引",cmd->code);return 0;
 }
 if(rc)app_print("XApp 图库操作 0x%x 失败：rc=%d，响应=0x%x",cmd->code,rc,ptp_get_return_code(r));
 return rc;
}
#endif
