# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html
# 有关在 Android Studio 中使用 CMake 的更多信息，请阅读
# 文档：https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.
# 设置构建原生库所需的最低 CMake 版本。

cmake_minimum_required(VERSION 3.6.4)

set(ANDROID_NDK on)
set(CMAKE_VERBOSE_MAKEFILE on)
set(CMAKE_BUILD_TARGET "Shared")

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/libjpeg-turbo)
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/libusb)
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/libuvc)
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/UVCCamera)