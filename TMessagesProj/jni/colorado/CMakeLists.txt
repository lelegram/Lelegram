add_library(colorado STATIC
        colorado/cert.cpp
        colorado/colorado.cpp
        colorado/utils.cpp
)
target_compile_definitions(colorado PUBLIC
        COLORADO_PACKAGE_NAME="${COLORADO_PACKAGE_NAME}"
        COLORADO_CERT_HASH=${COLORADO_CERT_HASH}
        COLORADO_CERT_SIZE=${COLORADO_CERT_SIZE}
)
target_include_directories(colorado PUBLIC colorado/)
target_compile_options(colorado PUBLIC -Oz -fvisibility=hidden)
target_include_directories(colorado PUBLIC integrity)
