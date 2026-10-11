#ifndef FUJI_CONSOLE_EVENTS_H
#define FUJI_CONSOLE_EVENTS_H
#include <stdint.h>
#include <stddef.h>
static const uint16_t console_codes[8]={0x5007,0x500d,0x500f,0x5005,0x5010,0x500e,0x500a,0x5001};
/* D212: uint16 count, then count records of uint16 code + uint32 value.
 * Validate the entire batch before updating any value. */
static int console_events_update(const uint8_t *data,size_t size,uint32_t values[8],unsigned *valid){
 if(!data||size<2)return -1;
 unsigned count=data[0]|((unsigned)data[1]<<8);
 if(count>(size-2)/6)return -1;
 for(unsigned i=0;i<count;i++){
  const uint8_t *p=data+2+i*6;unsigned code=p[0]|((unsigned)p[1]<<8);
  for(unsigned j=0;j<8;j++)if(code==console_codes[j]){
   values[j]=(uint32_t)p[2]|((uint32_t)p[3]<<8)|((uint32_t)p[4]<<16)|((uint32_t)p[5]<<24);*valid|=1u<<j;
  }
 }
 return 0;
}
#endif
