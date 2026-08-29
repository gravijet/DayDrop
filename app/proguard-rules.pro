# kotlinx.serialization keeps its serializers in synthetic members that R8
# cannot see through, so the generated code needs to survive shrinking.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.gravijet.daydrop.**$$serializer { *; }
-keepclassmembers class com.gravijet.daydrop.** {
    *** Companion;
}
-keepclasseswithmembers class com.gravijet.daydrop.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Enum values are looked up by name when a saved drop is read back.
-keepclassmembers enum com.gravijet.daydrop.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
