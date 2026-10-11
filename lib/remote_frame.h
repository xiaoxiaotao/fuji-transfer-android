#ifndef FUJI_REMOTE_FRAME_H
#define FUJI_REMOTE_FRAME_H
#include <stdint.h>
#include <stdlib.h>
#include <stddef.h>
/* Fuji TCP live view: 18-byte header, little endian total length, JPEG payload.
 * Source format: petabyt/libfuji (MIT), ptp_fuji_read_liveview_frame.
 * Unlike the upstream reader, handle fragmented headers, EOF, and untrusted lengths. */
#define FUJI_FRAME_MAX (4u*1024u*1024u)
typedef int (*fuji_frame_read_fn)(void *,void *,unsigned int);
static int fuji_frame_exact(fuji_frame_read_fn read_fn,void *arg,void *out,unsigned int size){
    unsigned int used=0;while(used<size){int n=read_fn(arg,(uint8_t *)out+used,size-used);if(n<=0||(unsigned int)n>size-used)return -1;used+=(unsigned int)n;}return 0;
}
static int fuji_frame_read(fuji_frame_read_fn read_fn,void *arg,uint8_t **jpeg,unsigned int *size){
    *jpeg=NULL;*size=0;uint8_t header[18];if(fuji_frame_exact(read_fn,arg,header,sizeof(header)))return -1;
    uint32_t total=(uint32_t)header[0]|((uint32_t)header[1]<<8)|((uint32_t)header[2]<<16)|((uint32_t)header[3]<<24);
    if(total<22||total>FUJI_FRAME_MAX)return -2;
    unsigned int length=total-18;uint8_t *data=malloc(length);if(!data)return -3;
    if(fuji_frame_exact(read_fn,arg,data,length)){free(data);return -1;}
    if(data[0]!=0xff||data[1]!=0xd8){free(data);return -2;}
    *jpeg=data;*size=length;return 0;
}
#endif
