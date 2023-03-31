
cmake_minimum_required(VERSION 3.18.1)

project("thx")

link_directories(../jniLibs/arm64-v8a)
include_directories(include)

set(CMAKE_C_FLAGS " -s -Ofast -Wall")

add_library(
        thx
        SHARED
        android.c
        thx.c
)

target_link_libraries(
        thx
        log
        ssl
        crypto
)
