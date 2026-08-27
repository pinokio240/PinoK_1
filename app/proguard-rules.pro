# =====================================================================
# SOVA_2_0 ProGuard rules
# =====================================================================

# --- Kotlin ---
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# --- Coroutines ---
-keepclassmembernames class kotlinx.** { volatile <fields>; }
-keepclassmembernames class kotlin.coroutines.** { volatile <fields>; }

# --- Compose ---
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# --- OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Gson ---
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory { *; }
-keep class * implements com.google.gson.JsonSerializer { *; }
-keep class * implements com.google.gson.JsonDeserializer { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- Coil ---
-dontwarn coil.**

# --- Media3 ---
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# --- App models (Gson reflection) ---
-keep class re.pinok.data.model.** { *; }
-keepclassmembers class re.pinok.data.model.** { *; }

# --- BuildConfig ---
-keep class re.pinok.BuildConfig { *; }

# --- Navigation Screen sealed class ---
# R8 не должен удалять/обfuscate sealed class объекты, используемые в Compose Navigation.
-keep class re.pinok.ui.navigation.Screen { *; }
-keepclassmembers class re.pinok.ui.navigation.Screen { *; }
