# =====================================================
# proguard-rules.pro - ProGuard 混淆规则文件
# =====================================================
#
# 作用：配置代码混淆规则，控制哪些代码应该被保留、哪些可以被移除或混淆
#
# 使用场景：
# 1. 正式发布 APK 时（minifyEnabled true）
# 2. 保护知识产权，增加反编译难度
# 3. 减小 APK 体积（移除未使用的代码）
# 4. 优化字节码，提高运行效率
#
# 注意：debug 构建时通常不启用混淆（minifyEnabled false）
#       便于调试和定位问题
# =====================================================

# Add project specific ProGuard rules here.
# 在这里添加项目特定的 ProGuard 规则

# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
# 您可以在 build.gradle 中使用 proguardFiles 设置来控制应用的配置文件集合

# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html
# 更多详细信息，请参阅 Android 官方文档

# =====================================================
# WebView JavaScript 接口保留规则（已注释）
# =====================================================
# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
# 如果您的项目使用带 JavaScript 的 WebView，请取消以下注释，
# 并指定 JavaScript 接口类的完整限定类名：

#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}
#
# 说明：
# -keepclassmembers：保留指定类的所有成员（方法和字段）
# 作用：防止 WebView 的 JavaScript 接口方法被混淆
# 原因：JS 调用 Java 方法时通过方法名匹配，混淆后会找不到对应方法
#
# 示例：
# -keepclassmembers class com.example.MyJavaScriptInterface {
#    public *;
# }

# =====================================================
# 调试信息保留规则（已注释）
# =====================================================
# Uncomment this to preserve the line number information for
# debugging stack traces.
# 取消注释以保留行号信息，用于调试堆栈跟踪
#-keepattributes SourceFile,LineNumberTable
#
# 说明：
# -keepattributes：保留指定的字节码属性
# SourceFile：保留源文件名（堆栈中显示哪个文件）
# LineNumberTable：保留行号信息（堆栈中显示哪一行）
#
# 作用：崩溃日志中可以显示具体的代码行号，便于定位问题
# 建议：正式发布时建议保留，方便分析线上崩溃

# =====================================================
# 源文件重命名规则（已注释）
# =====================================================
# If you keep the line number information, uncomment this to
# hide the original source file name.
# 如果您保留了行号信息，取消注释此项可以隐藏原始源文件名
#-renamesourcefileattribute SourceFile
#
# 说明：
# -renamesourcefileattribute：将所有源文件名重命名为指定字符串
# 作用：增加反编译难度，隐藏真实的源文件名
#
# 示例：
# 原始堆栈：at com.example.MyClass.myMethod(MyClass.java:10)
# 重命名后：at com.example.MyClass.myMethod(SourceFile:10)

# =====================================================
# 常用 ProGuard 规则参考（仅供参考，未在文件中）
# =====================================================

# 1. 保留所有 Activity 子类（防止找不到入口）
# -keep public class * extends android.app.Activity

# 2. 保留自定义 View
# -keep public class * extends android.view.View

# 3. 保留 Service 子类
# -keep public class * extends android.app.Service

# 4. 保留 BroadcastReceiver 子类
# -keep public class * extends android.content.BroadcastReceiver

# 5. 保留 ContentProvider 子类
# -keep public class * extends android.content.ContentProvider

# 6. 保留所有使用 @Keep 注解的类和方法
# -keep @androidx.annotation.Keep class *
# -keepclassmembers class * {
#     @androidx.annotation.Keep *;
# }

# 7. 保留 Parcelable 实现类（序列化需要）
# -keep class * implements android.os.Parcelable {
#   public static final android.os.Parcelable$Creator *;
# }

# 8. 保留 R 资源类中的静态字段（资源引用）
# -keepclassmembers class **.R$* {
#     public static <fields>;
# }

# 9. 保留枚举（混淆可能导致 values() 方法失效）
# -keepclassmembers enum * {
#     public static **[] values();
#     public static ** valueOf(java.lang.String);
# }

# 10. 保留本地方法（JNI 调用）
# -keepclasseswithmembernames class * {
#     native <methods>;
# }

# 11. 保留混淆后仍然需要反射调用的类
# -keep class com.example.SomeClass { *; }