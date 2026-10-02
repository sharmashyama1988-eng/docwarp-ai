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

# Locate OpenCV Android SDK
# If OPENCV_DIR is passed as a CMake argument or environment variable, use it.
# Otherwise check standard locations or fallback to built-in headers.
if(DEFINED OpenCV_DIR)
    find_package(OpenCV REQUIRED)
    message(STATUS "OpenCV found from OpenCV_DIR: ${OpenCV_DIR}")
elseif(DEFINED ENV{OpenCV_DIR})
    find_package(OpenCV REQUIRED PATHS $ENV{OpenCV_DIR})
    message(STATUS "OpenCV found from ENV OpenCV_DIR: $ENV{OpenCV_DIR}")
else()
    # Try default search path in parent sdk folder if cloned with project
    find_package(OpenCV QUIET PATHS
        ${CMAKE_CURRENT_SOURCE_DIR}/../../../../opencv-android-sdk/sdk/native/jni
        ${CMAKE_CURRENT_SOURCE_DIR}/../../../../OpenCV-android-sdk/sdk/native/jni
    )
endif()

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${OpenCV_INCLUDE_DIRS}
)

add_library(
    cv_engine
    SHARED
    cv_engine.cpp
    native-lib.cpp
)

target_link_libraries(
    cv_engine
    ${OpenCV_LIBS}
    android
    jnigraphics
    log
)

if(OpenMP_CXX_FOUND)
    target_link_libraries(cv_engine OpenMP::OpenMP_CXX)
endif()
