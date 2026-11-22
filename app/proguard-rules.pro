# kotlinx.serialization: keep generated serializers for saved games and match logs.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.ludosovereign.** { *** Companion; }
-keepclasseswithmembers class com.ludosovereign.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.ludosovereign.**$$serializer { *; }
-dontwarn kotlinx.serialization.**
