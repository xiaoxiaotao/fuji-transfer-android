#ifndef FUJI_CONSOLE_PROPERTY_H
#define FUJI_CONSOLE_PROPERTY_H
#include <stdint.h>
#include <stddef.h>
/* Read only the bounded scalar part of a PTP DevicePropDesc, without the
 * existing parser's unbounded enumeration allocation or panic on Form=None. */
static int console_property(const uint8_t *data,size_t length,uint16_t code,int64_t *value,int *writable){
 if(!data||length<6||((uint16_t)data[0]|((uint16_t)data[1]<<8))!=code)return -1;
 unsigned type=data[2]|((unsigned)data[3]<<8);unsigned width=type<=2?1:type<=4?2:type<=6?4:0;
 if(!type||!width||length<6+2*width||data[4]>1)return -1;
 uint32_t raw=0;for(unsigned i=0;i<width;i++)raw|=(uint32_t)data[5+width+i]<<(8*i);
 if(type==1)*value=(int8_t)raw;else if(type==3)*value=(int16_t)raw;else if(type==5)*value=(int32_t)raw;else *value=raw;
 *writable=data[4]==1;return 0;
}
#endif
