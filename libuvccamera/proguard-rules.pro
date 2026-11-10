# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/saki/android-sdks/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html
#
# 在此处添加项目特定的 ProGuard 规则。
# 默认情况下，此文件中的标志会追加到
# /Users/saki/android-sdks/tools/proguard/proguard-android.txt 中指定的标志之后。
# 您可以通过更改 build.gradle 中的 proguardFiles 指令来编辑包含路径及其顺序。
#
# 更多详情，请参阅
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:
# 在此处添加项目特定的 keep 选项：

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
# 如果您的项目使用带有 JS 的 WebView，请取消下面的注释，
# 并指定 JavaScript 接口类的完全限定类名：
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}
################libuvccamera##################
-keep class com.herohan.uvcapp.** { *; }
-keep class com.serenegiant.usb.** { *; }
-keepclassmembers class * implements com.serenegiant.usb.IButtonCallback {*;}
-keepclassmembers class * implements com.serenegiant.usb.IFrameCallback {*;}
-keepclassmembers class * implements com.serenegiant.usb.IStatusCallback {*;}
