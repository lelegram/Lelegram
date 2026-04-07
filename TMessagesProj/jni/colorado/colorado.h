#include <stdbool.h>

#ifdef NDEBUG
#define LOG_DISABLED
#endif

#ifndef COLORADO_PACKAGE_NAME
#define COLORADO_PACKAGE_NAME "com.fylnx.lelegram"
#endif

#ifndef COLORADO_CERT_HASH
#define COLORADO_CERT_HASH 0x693cc8c5
#endif

#ifndef COLORADO_CERT_SIZE
#define COLORADO_CERT_SIZE 0x2d7
#endif

#define PACKAGE_NAME COLORADO_PACKAGE_NAME
#define CERT_HASH COLORADO_CERT_HASH
#define CERT_SIZE COLORADO_CERT_SIZE

#ifdef __cplusplus
extern "C" {
#endif

bool check_signature();

#ifdef __cplusplus
}
#endif
