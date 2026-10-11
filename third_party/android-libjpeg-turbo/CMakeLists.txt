cmake_minimum_required(VERSION 3.10)

project(libjpeg-turbo)

set(SOURCE_PATH ${CMAKE_CURRENT_SOURCE_DIR}/libjpeg-turbo-2.0.1)

set(SOURCES
    ${SOURCE_PATH}/jcapimin.c
    ${SOURCE_PATH}/jcapistd.c
    ${SOURCE_PATH}/jccoefct.c
    ${SOURCE_PATH}/jccolor.c
    ${SOURCE_PATH}/jcdctmgr.c
    ${SOURCE_PATH}/jchuff.c
    ${SOURCE_PATH}/jcinit.c
    ${SOURCE_PATH}/jcmainct.c
    ${SOURCE_PATH}/jcmarker.c
    ${SOURCE_PATH}/jcmaster.c
    ${SOURCE_PATH}/jcomapi.c
    ${SOURCE_PATH}/jcparam.c
    ${SOURCE_PATH}/jcphuff.c
    ${SOURCE_PATH}/jcprepct.c
    ${SOURCE_PATH}/jcsample.c
    ${SOURCE_PATH}/jctrans.c
    ${SOURCE_PATH}/jdapimin.c
    ${SOURCE_PATH}/jdapistd.c
    ${SOURCE_PATH}/jdatadst.c
    ${SOURCE_PATH}/jdatasrc.c
    ${SOURCE_PATH}/jdcoefct.c
    ${SOURCE_PATH}/jdcolor.c
    ${SOURCE_PATH}/jddctmgr.c
    ${SOURCE_PATH}/jdhuff.c
    ${SOURCE_PATH}/jdinput.c
    ${SOURCE_PATH}/jdmainct.c
    ${SOURCE_PATH}/jdmarker.c
    ${SOURCE_PATH}/jdmaster.c
    ${SOURCE_PATH}/jdmerge.c
    ${SOURCE_PATH}/jdphuff.c
    ${SOURCE_PATH}/jdpostct.c
    ${SOURCE_PATH}/jdsample.c
    ${SOURCE_PATH}/jdtrans.c
    ${SOURCE_PATH}/jerror.c
    ${SOURCE_PATH}/jfdctflt.c
    ${SOURCE_PATH}/jfdctfst.c
    ${SOURCE_PATH}/jfdctint.c
    ${SOURCE_PATH}/jidctflt.c
    ${SOURCE_PATH}/jidctfst.c
    ${SOURCE_PATH}/jidctint.c
    ${SOURCE_PATH}/jidctred.c
    ${SOURCE_PATH}/jquant1.c
    ${SOURCE_PATH}/jquant2.c
    ${SOURCE_PATH}/jutils.c
    ${SOURCE_PATH}/jmemmgr.c
    ${SOURCE_PATH}/jmemnobs.c
    ${SOURCE_PATH}/jaricom.c
    ${SOURCE_PATH}/jcarith.c
    ${SOURCE_PATH}/jdarith.c
    ${SOURCE_PATH}/turbojpeg.c
    ${SOURCE_PATH}/transupp.c
    ${SOURCE_PATH}/jdatadst-tj.c
    ${SOURCE_PATH}/jdatasrc-tj.c
)

set(ASM_INCLUDE_FLAGS "-i${SOURCE_PATH}/simd/nasm -i${SOURCE_PATH}/simd/x86_64/")

if(${CMAKE_ANDROID_ARCH_ABI} STREQUAL "x86_64" OR ${CMAKE_SYSTEM_PROCESSOR} MATCHES "x86_64")
    enable_language(C ASM_NASM)
    add_library(libjpeg-turbo-asm
        ${SOURCE_PATH}/simd/x86_64/jfdctflt-sse.asm
        ${SOURCE_PATH}/simd/x86_64/jfdctfst-sse2.asm
        ${SOURCE_PATH}/simd/x86_64/jfdctint-avx2.asm
        ${SOURCE_PATH}/simd/x86_64/jfdctint-sse2.asm
        ${SOURCE_PATH}/simd/x86_64/jidctflt-sse2.asm
        ${SOURCE_PATH}/simd/x86_64/jidctfst-sse2.asm
        ${SOURCE_PATH}/simd/x86_64/jidctint-avx2.asm
        ${SOURCE_PATH}/simd/x86_64/jidctint-sse2.asm
        ${SOURCE_PATH}/simd/x86_64/jidctred-sse2.asm
        ${SOURCE_PATH}/simd/x86_64/jccolor-sse2.asm
        ${SOURCE_PATH}/simd/x86_64/jccolor-avx2.asm
        ${SOURCE_PATH}/simd/x86_64/jcgray-avx2.asm
        ${SOURCE_PATH}/simd/x86_64/jcgray-sse2.asm
        ${SOURCE_PATH}/simd/x86_64/jcsample-avx2.asm
        ${SOURCE_PATH}/simd/x86_64/jcsample-sse2.asm
        ${SOURCE_PATH}/simd/x86_64/jdcolor-avx2.asm
        ${SOURCE_PATH}/simd/x86_64/jdcolor-sse2.asm
        ${SOURCE_PATH}/simd/x86_64/jdmerge-avx2.asm
        ${SOURCE_PATH}/simd/x86_64/jdmerge-sse2.asm
        ${SOURCE_PATH}/simd/x86_64/jdsample-avx2.asm
        ${SOURCE_PATH}/simd/x86_64/jdsample-sse2.asm
        ${SOURCE_PATH}/simd/x86_64/jquantf-sse2.asm
        ${SOURCE_PATH}/simd/x86_64/jquanti-avx2.asm
        ${SOURCE_PATH}/simd/x86_64/jquanti-sse2.asm
        ${SOURCE_PATH}/simd/x86_64/jsimdcpu.asm
        ${SOURCE_PATH}/simd/x86_64/jchuff-sse2.asm
        ${SOURCE_PATH}/simd/x86_64/jcphuff-sse2.asm
    )
    set(CMAKE_ASM_NASM_FLAGS "-DELF ${ASM_INCLUDE_FLAGS} -D__x86_64__")
    list(APPEND SOURCES
        ${SOURCE_PATH}/simd/x86_64/jsimd.c
    )
    set(ARCH_FLAGS "-DSIZEOF_SIZE_T=8")
    set_target_properties(libjpeg-turbo-asm PROPERTIES LINKER_LANGUAGE ASM_NASM)
elseif(${CMAKE_ANDROID_ARCH_ABI} STREQUAL "x86")
    enable_language(C ASM_NASM)
    add_library(libjpeg-turbo-asm
        ${SOURCE_PATH}/simd/i386/jsimdcpu.asm
        ${SOURCE_PATH}/simd/i386/jccolor-avx2.asm
        ${SOURCE_PATH}/simd/i386/jccolor-mmx.asm
        ${SOURCE_PATH}/simd/i386/jccolor-sse2.asm
        ${SOURCE_PATH}/simd/i386/jcgray-avx2.asm
        ${SOURCE_PATH}/simd/i386/jcgray-mmx.asm
        ${SOURCE_PATH}/simd/i386/jcgray-sse2.asm
        ${SOURCE_PATH}/simd/i386/jchuff-sse2.asm
        ${SOURCE_PATH}/simd/i386/jcphuff-sse2.asm
        ${SOURCE_PATH}/simd/i386/jcsample-avx2.asm
        ${SOURCE_PATH}/simd/i386/jcsample-mmx.asm
        ${SOURCE_PATH}/simd/i386/jcsample-sse2.asm
        ${SOURCE_PATH}/simd/i386/jdcolor-avx2.asm
        ${SOURCE_PATH}/simd/i386/jdcolor-mmx.asm
        ${SOURCE_PATH}/simd/i386/jdcolor-sse2.asm
        ${SOURCE_PATH}/simd/i386/jdmerge-avx2.asm
        ${SOURCE_PATH}/simd/i386/jdmerge-mmx.asm
        ${SOURCE_PATH}/simd/i386/jdmerge-sse2.asm
        ${SOURCE_PATH}/simd/i386/jdsample-avx2.asm
        ${SOURCE_PATH}/simd/i386/jdsample-mmx.asm
        ${SOURCE_PATH}/simd/i386/jdsample-sse2.asm
        ${SOURCE_PATH}/simd/i386/jfdctflt-3dn.asm
        ${SOURCE_PATH}/simd/i386/jfdctflt-sse.asm
        ${SOURCE_PATH}/simd/i386/jfdctfst-mmx.asm
        ${SOURCE_PATH}/simd/i386/jfdctfst-sse2.asm
        ${SOURCE_PATH}/simd/i386/jfdctint-avx2.asm
        ${SOURCE_PATH}/simd/i386/jfdctint-mmx.asm
        ${SOURCE_PATH}/simd/i386/jfdctint-sse2.asm
        ${SOURCE_PATH}/simd/i386/jidctflt-3dn.asm
        ${SOURCE_PATH}/simd/i386/jidctflt-sse.asm
        ${SOURCE_PATH}/simd/i386/jidctflt-sse2.asm
        ${SOURCE_PATH}/simd/i386/jidctfst-mmx.asm
        ${SOURCE_PATH}/simd/i386/jidctfst-sse2.asm
        ${SOURCE_PATH}/simd/i386/jidctint-avx2.asm
        ${SOURCE_PATH}/simd/i386/jidctint-mmx.asm
        ${SOURCE_PATH}/simd/i386/jidctint-sse2.asm
        ${SOURCE_PATH}/simd/i386/jidctred-mmx.asm
        ${SOURCE_PATH}/simd/i386/jidctred-sse2.asm
        ${SOURCE_PATH}/simd/i386/jquant-3dn.asm
        ${SOURCE_PATH}/simd/i386/jquant-mmx.asm
        ${SOURCE_PATH}/simd/i386/jquant-sse.asm
        ${SOURCE_PATH}/simd/i386/jquantf-sse2.asm
        ${SOURCE_PATH}/simd/i386/jquanti-avx2.asm
        ${SOURCE_PATH}/simd/i386/jquanti-sse2.asm
    )
    list(APPEND SOURCES
        ${SOURCE_PATH}/simd/i386/jsimd.c
    )
    set(CMAKE_ASM_NASM_FLAGS "-DELF -DSIZEOF_SIZE_T=4 ${ASM_INCLUDE_FLAGS} -DPIC")
    set(ARCH_FLAGS "-DSIZEOF_SIZE_T=4")
    set_target_properties(libjpeg-turbo-asm PROPERTIES LINKER_LANGUAGE ASM_NASM)
elseif(${CMAKE_ANDROID_ARCH_ABI} STREQUAL "armeabi-v7a")
    enable_language(C ASM)
    add_library(libjpeg-turbo-asm
        ${SOURCE_PATH}/simd/arm/jsimd_neon.S
    )
    list(APPEND SOURCES
        ${SOURCE_PATH}/simd/arm/jsimd.c
    )
    set(CMAKE_ASM_NASM_FLAGS "-DSIZEOF_SIZE_T=4 ${ASM_INCLUDE_FLAGS} -D__ARM_HAVE_NEON")
    set(ARCH_FLAGS "-DSIZEOF_SIZE_T=4")
    set_target_properties(libjpeg-turbo-asm PROPERTIES LINKER_LANGUAGE ASM)
elseif(${CMAKE_ANDROID_ARCH_ABI} STREQUAL "arm64-v8a" OR ${CMAKE_SYSTEM_PROCESSOR} MATCHES "aarch64")
    enable_language(C ASM)
    add_library(libjpeg-turbo-asm
        ${SOURCE_PATH}/simd/arm64/jsimd_neon.S
    )
    list(APPEND SOURCES
        ${SOURCE_PATH}/simd/arm64/jsimd.c
    )
    set(CMAKE_ASM_NASM_FLAGS "-DSIZEOF_SIZE_T=8 ${ASM_INCLUDE_FLAGS}")
    set(ARCH_FLAGS "-DSIZEOF_SIZE_T=8")
    set_target_properties(libjpeg-turbo-asm PROPERTIES LINKER_LANGUAGE ASM)
endif()

#message(FATAL_ERROR ${SOURCES})
add_library(libjpeg-turbo STATIC ${SOURCES})
target_link_libraries(libjpeg-turbo
    libjpeg-turbo-asm
)

target_include_directories(libjpeg-turbo
    PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include
    PUBLIC ${SOURCE_PATH}
    PRIVATE ${SOURCE_PATH}/simd
    PRIVATE ${SOURCE_PATH}/simd/nasm
    PRIVATE ${SOURCE_PATH}/simd/loongson
    PRIVATE ${SOURCE_PATH}/simd/mips
    PRIVATE ${SOURCE_PATH}/simd/powerpc
)

target_compile_options(libjpeg-turbo PRIVATE
    "-DBUILD=\"20181112\""
    "-DPACKAGE_NAME=\"libjpeg-turbo\""
    -DVERSION="2.0.1"
    "-DLIBJPEG_TURBO_VERSION=\"2.0.1\""
    -DJPEG_LIB_VERSION=62
    -DC_ARITH_CODING_SUPPORTED=1
    -DD_ARITH_CODING_SUPPORTED=1
    -DBITS_IN_JSAMPLE=8
    -DHAVE_DLFCN_H=1
    -DHAVE_INTTYPES_H=1
    -DHAVE_LOCALE_H=1
    -DHAVE_MEMCPY=1
    -DHAVE_MEMORY_H=1
    -DHAVE_MEMSET=1
    -DHAVE_STDDEF_H=1
    -DHAVE_STDINT_H=1
    -DHAVE_STDLIB_H=1
    -DHAVE_STRINGS_H=1
    -DHAVE_STRING_H=1
    -DHAVE_SYS_STAT_H=1
    -DHAVE_SYS_TYPES_H=1
    -DHAVE_UNISTD_H=1
    -DHAVE_UNSIGNED_CHAR=1
    -DHAVE_UNSIGNED_SHORT=1
#    "-DINLINE=inline __attribute__((always_inline))"
    -DINLINE=inline
    -DMEM_SRCDST_SUPPORTED=1
    -DNEED_SYS_TYPES_H=1
    -DSTDC_HEADERS=1
    -DWITH_SIMD=1
    ${ARCH_FLAGS}
)
