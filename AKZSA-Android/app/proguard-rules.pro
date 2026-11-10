# kotlinx.serialization generates serializers; keep metadata required for release serialization.
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# Never keep verbose logging calls in release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
