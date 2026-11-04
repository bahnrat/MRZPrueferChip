
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**
-keep class androidx.graphics.** { *; }
-dontwarn androidx.graphics.**
-keepattributes *Annotation*,Signature,InnerClasses,ExclosingMethod

-keep class java.security.** { *; }
-keep class javax.crypto.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

-assumenosideeffects class android.util.Log {
public static boolean isLoggable(java.lang.String, int);
public static int v(...);
public static int d(...);
public static int i(...);
public static int w(...);
public static int e(...);
}


