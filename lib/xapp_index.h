#ifndef FUDGE_XAPP_INDEX_H
#define FUDGE_XAPP_INDEX_H
#include <stdint.h>
#include <stddef.h>
static uint32_t xapp_u32(const uint8_t *p) {
    return (uint32_t)p[0] | ((uint32_t)p[1]<<8) | ((uint32_t)p[2]<<16) | ((uint32_t)p[3]<<24);
}
/* Accept only an exact PTP UINT32 array with the previously reported count. */
static int xapp_parse_index(const uint8_t *data, size_t length, int count, int *out) {
    if (!data || !out || count <= 0 || count > 30000 || length != 4u + 4u*(size_t)count || xapp_u32(data) != (uint32_t)count) return 0;
    for (int i=0;i<count;i++) {
        uint32_t handle=xapp_u32(data+4+4*i);
        if (!handle || handle==UINT32_MAX) return 0;
        out[i]=(int)handle;
    }
    return 1;
}
#endif
