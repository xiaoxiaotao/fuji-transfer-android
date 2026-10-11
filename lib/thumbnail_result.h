#ifndef FUJI_THUMBNAIL_RESULT_H
#define FUJI_THUMBNAIL_RESULT_H
/* A consumed rejection for one object does not invalidate the PTP session. */
static int thumbnail_unavailable(int rc,int response){
 return rc==PTP_UNSUPPORTED||(rc==PTP_CHECK_CODE&&(response==0x2005||response==0x2009||response==0x200a||response==0x2019));
}
#endif
