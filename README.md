[![Maven Central](https://img.shields.io/maven-central/v/com.herohan/UVCAndroid.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.herohan%22%20AND%20a:%22UVCAndroid%22)

UVCAndroid
=========

Library and sample to access UVC camera on non-rooted Android device

[中文文档： UVCAndroid，安卓UVC相机通用开发库](https://blog.csdn.net/hanshiying007/article/details/124118486)

How do I use it?
---

### Setup

##### Dependencies
```groovy
repositories {
  mavenCentral()
}

dependencies {
    implementation 'com.herohan:UVCAndroid:1.0.13'
}
```
R8 / ProGuard
-------------

If you are using R8 the shrinking and obfuscation rules are included automatically.

ProGuard users must manually add the below options.
```groovy
-keep class com.herohan.uvcapp.** { *; }
-keep class com.serenegiant.usb.** { *; }
-keepclassmembers class * implements com.serenegiant.usb.IButtonCallback {*;}
-keepclassmembers class * implements com.serenegiant.usb.IFrameCallback {*;}
-keepclassmembers class * implements com.serenegiant.usb.IStatusCallback {*;}
```

Requirements
--------------
Android 10 (API level 29)+

The library is compiled for Java 17, so apps that use it must build with JDK 17 or newer. It depends on `androidx.core` 1.19.1, which requires apps that use the library to compile against Android API 37 (`compileSdk = 37`) with Android Gradle Plugin 9.1 or newer.

Building this repository requires JDK 17 or newer. It is tested with JDK 25, the version bundled with Android Studio. Gradle must run on a full JDK, not a JRE, because it compiles the version catalog's accessors; from the command line, point `JAVA_HOME` at a JDK (for example Android Studio's bundled one).

Plugin and library versions are declared in one place, the Gradle version catalog [`gradle/libs.versions.toml`](gradle/libs.versions.toml).
