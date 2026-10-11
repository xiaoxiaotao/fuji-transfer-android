#include "FujiBridge.h"
#include <assert.h>
#include <stdio.h>
int main(void){fios_init(NULL,NULL);assert(fios_connect("invalid",0)!=0);assert(fios_connect("127.0.0.1",99)!=0);assert(fios_download(1,"unused",0)!=0);assert(fios_download(1,"unused",2147483648ULL)!=0);fios_disconnect();fios_disconnect();fios_free(NULL);puts("iOS native bridge: validation and lifecycle smoke tests passed");return 0;}
