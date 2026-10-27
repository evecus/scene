# kotlinx.serialization 官方 R8 规则（serialization 1.7+）
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class jm.scene.**$$serializer { *; }
-keepclassmembers class jm.scene.** {
    *** Companion;
}
-keepclasseswithmembers class jm.scene.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# libsu（自带 consumer rules，这里兜底防止 shell 命令类被裁）
-keep class com.topjohnwu.superuser.** { *; }

# 保留崩溃堆栈可读性
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
