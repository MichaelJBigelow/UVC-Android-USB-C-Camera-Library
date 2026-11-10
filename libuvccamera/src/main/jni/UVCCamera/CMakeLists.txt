# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html
# 有关在 Android Studio 中使用 CMake 的更多信息,请阅读
# 文档: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.
# 设置构建原生库所需的最低 CMake 版本。

cmake_minimum_required(VERSION 3.6.4)

# Declares and names the project.
# 声明并命名项目。

project(UVCCamera)

include_directories(${CMAKE_CURRENT_SOURCE_DIR}/../)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/../rapidjson/include)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DANDROID_NDK")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DLOG_NDEBUG")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DACCESS_RAW_DESCRIPTORS")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -fstrict-aliasing -fprefetch-loop-arrays")

set(SOURCES
        _onload.cpp
        utilbase.cpp
        UVCCamera.cpp
        UVCControl.cpp
        UVCPreview.cpp
        UVCButtonCallback.cpp
        UVCStatusCallback.cpp
        Parameters.cpp
        registerUVCCamera.cpp
        registerUVCControl.cpp
        )

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
# 创建并命名一个库,将其设置为 STATIC 或 SHARED,
# 并提供其源代码的相对路径。
# 您可以定义多个库,CMake 会为您构建它们。
# Gradle 会自动将共享库打包进您的 APK。

add_library( # Sets the name of the library. / 设置库的名称。
        UVCCamera

        # Sets the library as a shared library.
        # 将该库设置为共享库。
        SHARED

        # Provides a relative path to your source file(s).
        # 提供源文件的相对路径。
        ${SOURCES})

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.
# 搜索指定的预构建库并将其路径存储在一个变量中。
# 由于 CMake 默认已将系统库包含在搜索路径中,
# 您只需指定要添加的公共 NDK 库的名称。
# CMake 会在完成构建之前验证该库是否存在。

#find_library( # Sets the name of the path variable.
#        usb1.0
#
#        # Specifies the name of the NDK library that
#        # you want CMake to locate.
#        usb1.0)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.
# 指定 CMake 应链接到目标库的库。
# 您可以链接多个库,例如在此构建脚本中定义的库、
# 预构建的第三方库或系统库。

target_link_libraries( # Specifies the target library. / 指定目标库。
        UVCCamera

        # Links the target library to the log library
        # included in the NDK.
        # 将目标库链接到 NDK 中包含的日志库。
        usb1.0
        android
        log)