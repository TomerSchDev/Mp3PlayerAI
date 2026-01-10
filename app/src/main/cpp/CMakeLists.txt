cmake_minimum_required(VERSION 3.22.1)
project(mp3playerai_llm)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3")

# Path to llama.cpp sources
set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)

# llama.cpp builds differ by version; this "source add" approach is stable:
add_library(llama STATIC
        ${LLAMA_DIR}/ggml/src/ggml.c
        ${LLAMA_DIR}/ggml/src/ggml-alloc.c
        ${LLAMA_DIR}/ggml/src/ggml-backend.c
        ${LLAMA_DIR}/ggml/src/ggml-quants.c
        ${LLAMA_DIR}/ggml/src/ggml-threading.c
        ${LLAMA_DIR}/src/llama.cpp
        ${LLAMA_DIR}/src/llama-model.cpp
        ${LLAMA_DIR}/src/llama-context.cpp
        ${LLAMA_DIR}/src/llama-sampling.cpp
        ${LLAMA_DIR}/src/llama-vocab.cpp
)

target_include_directories(llama PUBLIC
        ${LLAMA_DIR}
        ${LLAMA_DIR}/include
        ${LLAMA_DIR}/ggml/include
        ${LLAMA_DIR}/ggml/src
        ${LLAMA_DIR}/src
)

add_library(llm_jni SHARED
        llm_jni.cpp
)

target_link_libraries(llm_jni
        llama
        log
)
