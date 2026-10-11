#ifndef FUJI_IOS_BRIDGE_H
#define FUJI_IOS_BRIDGE_H
#include <stdint.h>
#include <stddef.h>
/* All operations must run on one serial worker. Buffers belong to the caller. */
typedef void (*fios_log_callback)(const char *);
typedef void (*fios_progress_callback)(uint64_t, uint64_t);
void fios_init(fios_log_callback log, fios_progress_callback progress);
int fios_connect(const char *ipv4, int protocol); /* 0 auto, 1 classic, 2 XApp */
void fios_disconnect(void);
int fios_ping(void);
int fios_handles(uint32_t **handles, size_t *count);
int fios_info(uint32_t handle, char **json);
int fios_thumbnail(uint32_t handle, uint8_t **bytes, size_t *length);
int fios_download(uint32_t handle, const char *path, uint64_t expected_size);
void fios_free(void *buffer);
#endif
