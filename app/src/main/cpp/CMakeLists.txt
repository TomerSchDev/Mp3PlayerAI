cmake_minimum_required(VERSION 3.22)
project(llama_android_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_compile_options(-O3 -DNDEBUG)

# Adjust to where you put llama.cpp
# Example: <project_root>/llama.cpp
set(LLAMA_CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../llama.cpp)

if(NOT EXISTS ${LLAMA_CPP_DIR}/CMakeLists.txt)
    message(FATAL_ERROR "llama.cpp not found at: ${LLAMA_CPP_DIR}. Clone it into your project root.")
endif()

# Build llama.cpp as a subproject
add_subdirectory(${LLAMA_CPP_DIR} ${CMAKE_BINARY_DIR}/llama-build)

add_library(llama-android SHARED
        llama_android.cpp
)

target_include_directories(llama-android PRIVATE
        ${LLAMA_CPP_DIR}
        ${LLAMA_CPP_DIR}/include
)

# llama.cpp targets can vary slightly by revision; 'llama' is common.
target_link_libraries(llama-android
        llama
        android
        log
)
