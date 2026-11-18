cmake_minimum_required(VERSION 3.22.1)
project(soplau_llama LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Only the llama/ggml core is needed on device. Examples, tests, server, curl and
# common utilities are switched off to keep the build small and fast.
# Static llama/ggml objects get linked into our shared library, so they must be position-independent.
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

# Pinned release: the newest master is mid-way through an API migration.
set(SOPLAU_LLAMA_TAG "b6000" CACHE STRING "llama.cpp release tag")

include(FetchContent)
FetchContent_Declare(
    llama
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG        ${SOPLAU_LLAMA_TAG}
    GIT_SHALLOW    TRUE
)
FetchContent_MakeAvailable(llama)

add_library(soplau_llama SHARED soplau_llama.cpp)
target_link_libraries(soplau_llama PRIVATE llama)

if(ANDROID)
    find_library(android_log_lib log)
    target_link_libraries(soplau_llama PRIVATE ${android_log_lib})
endif()
