#ifndef FUDGE_WIFI_PROTOCOL_H
#define FUDGE_WIFI_PROTOCOL_H
/* A capability hint, with a manual protocol override in the connection UI. */
static inline int fuji_wifi_has_xapp(int remote, int extended) {
    return remote >= 0x20007 && extended > 0;
}
#endif
