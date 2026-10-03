cmake_minimum_required(VERSION 3.22.1)

project("vflat_native_engine")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Enable OpenMP for parallel integral image computation
find_package(OpenMP)
if(OpenMP_CXX_FOUND)
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${OpenMP_C_FLAGS}")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${OpenMP_CXX_FLAGS}")
endif()

# Silicon-Level Extreme Aggressive Machine Code Generation (-O3, LTO, Vectorize, Fast-Math)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -flto -fvectorize -ffast-math -DNDEBUG")

# Locate OpenCV via Android Gradle Prefab (org.opencv:opencv:4.10.0)
find_package(OpenCV REQUIRED CONFIG)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
)

add_library(
    cv_engine
    SHARED
    cv_engine.cpp
    native-lib.cpp
)

target_link_libraries(
    cv_engine
    OpenCV::opencv_java4
    android
    jnigraphics
    log
)

target_link_options(cv_engine PRIVATE "-Wl,-z,max-page-size=16384")

if(OpenMP_CXX_FOUND)
    target_link_libraries(cv_engine OpenMP::OpenMP_CXX)
    target_link_options(cv_engine PRIVATE "-static-openmp")
endif()
