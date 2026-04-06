cmake_minimum_required(VERSION 3.22)
project(micplugin_native VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ─── FetchContent for CLAP headers ─────────────────────────────────────────────
include(FetchContent)

FetchContent_Declare(
    clap
    GIT_REPOSITORY https://github.com/free-audio/clap.git
    GIT_TAG        1.2.1
    GIT_SHALLOW    TRUE
    GIT_PROGRESS   FALSE
)
FetchContent_MakeAvailable(clap)

FetchContent_Declare(
    lv2_headers
    GIT_REPOSITORY https://github.com/lv2/lv2.git
    GIT_TAG        v1.18.10
    GIT_SHALLOW    TRUE
    GIT_PROGRESS   FALSE
)
FetchContent_MakeAvailable(lv2_headers)

# ─── Oboe via prefab ──────────────────────────────────────────────────────────
find_package(oboe REQUIRED CONFIG)

# ─── Native library ───────────────────────────────────────────────────────────
add_library(micplugin_native SHARED
    AudioEngine.cpp
    PluginHost.cpp
    ClapHost.cpp
    Lv2Host.cpp
    Vst3Bridge.cpp
)

target_include_directories(micplugin_native PRIVATE
    ${clap_SOURCE_DIR}/include
    ${lv2_headers_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_compile_options(micplugin_native PRIVATE
    -O2
    -ffast-math
    -DANDROID
    -Wall
    -Wno-unused-parameter
)

target_link_libraries(micplugin_native
    oboe::oboe
    log
    android
    dl        # dlopen for plugin loading
    OpenSLES  # OpenSL ES fallback
)
