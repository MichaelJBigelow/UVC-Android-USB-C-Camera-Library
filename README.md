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

The library is compiled for Java 17, so apps that use it must build with JDK 17 or newer. Its only dependencies are `androidx.annotation` and the Kotlin standard library (which `androidx.annotation` already brings in), so it adds no other AndroidX libraries to the apps that use it. Kotlin apps need Kotlin 2.3 or newer to read the library's Kotlin 2.4 metadata; Java apps need nothing extra.

Building this repository requires JDK 17 or newer. It is tested with JDK 25, the version bundled with Android Studio. Gradle must run on a full JDK, not a JRE, because it compiles the version catalog's accessors; from the command line, point `JAVA_HOME` at a JDK (for example Android Studio's bundled one).

Plugin and library versions are declared in one place, the Gradle version catalog [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

The library's camera API (`com.herohan.uvcapp`) and its helpers (`com.serenegiant.utils`) are written in Kotlin, and keep the same API for Java callers: getters, setters, static constants and static methods have the names they had in Java. The low-level USB, JNI and OpenGL code it builds on (`com.serenegiant.usb` and `com.serenegiant.opengl`) is still Java, as is the bundled libjpeg-turbo. The two sample apps, `app` (a full USB camera app) and `demo` (one screen per library feature), are written in Kotlin with a Jetpack Compose UI in Material 3, using the wallpaper's colors (dynamic color) on Android 12+; the camera preview is a `TextureView` or `SurfaceView` hosted in Compose with `AndroidView`.

The code the two sample apps share, the Compose dialogs and theme and `CameraSession` (which ties a camera to an activity's lifecycle), lives in the `sample-common` module. It is not part of the published library.

Tests
-----

Unit tests run on the JVM:
```bash
./gradlew testDebugUnitTest
```

Instrumented tests run on a device or emulator with Android 10 or newer, and need no USB camera:
```bash
./gradlew connectedDebugAndroidTest
```

They cover the library (capture settings, device filters, saving pictures, and `CameraHelper` before a camera is selected), the sample apps' dialogs, every demo screen, and the app's start screen. Opening a camera, previewing and recording need a real UVC camera, so only these tests use one: the demo's `CommonTest`, a recording soak test of about 30 minutes, runs only when a UVC camera is attached and is skipped otherwise, while the app's `MainActivityTest` is skipped when one is attached.
