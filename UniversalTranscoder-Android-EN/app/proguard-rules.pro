# 混淆规则文件：本工程 release 未开启 minifyEnabled，因此这里仅保留必要的保护性规则。
# 保留 AndroidX 与 Material 的类，防止将来开启混淆时反射调用被裁剪。
-keep class androidx.** { *; }
# 保留应用自身的核心与 UI 类，防止反射式创建 Fragment 时找不到类。
-keep class com.xu.universaltranscoder.** { *; }
# 保留 MediaCodec 相关系统类引用（系统类不会被混淆，此处仅作显式说明）。
-dontwarn android.media.**
