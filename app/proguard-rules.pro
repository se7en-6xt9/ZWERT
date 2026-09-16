# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Strip verbose/debug logs in release builds to avoid leaking sensitive user data or tokens
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# Keep Room database entities & Moshi serialization classes
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-keep class * implements java.io.Serializable { *; }
-keepclassmembers class * implements java.io.Serializable { *; }
