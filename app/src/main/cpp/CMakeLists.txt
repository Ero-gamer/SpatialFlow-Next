cmake_minimum_required(VERSION 3.22.1)
project(spatialflow_audio LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 1. Prune unused Oboe software resamplers and flowgraph converters (~300 KB size saving)
# Since Bit-Perfect mode strictly forbids software rate conversion, we disable conversion
set(OBOE_DISABLE_CONVERSION ON CACHE BOOL "Exclude data conversion source files and features" FORCE)

# 2. Add Oboe as a subproject
add_subdirectory(oboe ./oboe-bin)

# 3. SpatialFlow Lean Audio Engine & JNI Bridge
add_library(spatialflow_audio SHARED
    OboeStreamEngine.cpp
    SpatialFlowOboeJni.cpp
)

target_include_directories(spatialflow_audio PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/oboe/include
)

# 4. Aggressive size and performance optimization flags
target_compile_options(spatialflow_audio PRIVATE
    -O3
    -flto
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
    -fno-exceptions
    -fno-rtti
)

target_link_options(spatialflow_audio PRIVATE
    -Wl,--gc-sections
    -Wl,-s
)

# 5. Link Oboe, Android native audio/hardware, and logging
target_link_libraries(spatialflow_audio PRIVATE
    oboe
    android
    log
)
