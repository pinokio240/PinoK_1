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
# #PERF-R8 (Task 74): снят широкий -keep class androidx.compose.** { *; }.
# Плагин kotlin-compose 2.4.0 генерирует всю рантайм-метаинформацию
# (Composer-индексы, групповые ключи, диспатч) прямо в классах, поэтому
# внешний -keep не нужен и мешает R8 удалять/обфусцировать неиспользуемый
# Compose-код. Оставлен только -dontwarn для опциональных модулей.
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
# #PERF-R8 (Task 75): снят широкий -keep class androidx.media3.** { *; }.
# media3 1.8.0 поставляется с consumer-rules.pro (сам объявляет нужные keep
# для рефлексии и сервисов), дублирующий широкий keep только мешал R8
# удалять неиспользуемые части ExoPlayer/UI/session. -dontwarn оставлен.
-dontwarn androidx.media3.**

# --- App models (Gson reflection) ---
# #PERF-R8 (Task 75): снят широкий -keep class re.pinok.data.model.** { *; }.
# Gson десериализует поля рефлексией. В Models.kt/UserProfile.kt все поля
# покрыты @SerializedName (общее правило Gson выше), но в VkAccountModels/
# CallModels/VideoQuality аннотаций нет — там имя поля берётся из JSON,
# поэтому его НЕЛЬЗЯ обфусцировать. Сохраняем имена полей, но разрешаем R8
# удалять/обфусцировать имена классов и неиспользуемые методы.
-keepclassmembers class re.pinok.data.model.** {
    <fields>;
}

# --- BuildConfig ---
-keep class re.pinok.BuildConfig { *; }

# --- Navigation Screen sealed class ---
# R8 не должен удалять/обфусцировать sealed class объекты, используемые в Compose Navigation.
-keep class re.pinok.ui.navigation.Screen { *; }
-keepclassmembers class re.pinok.ui.navigation.Screen { *; }
# --- Room / WorkManager ---
# #FIX-ROOM (Task 75): R8 удалял no-arg конструктор у androidx.work.impl.WorkDatabase_Impl,
# а Room/WorkManager инстанцируют *Database_Impl рефлексией -> краш при старте:
#   NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init> []
# (Падал androidx.startup.InitializationProvider -> WorkManagerInitializer).
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keepclassmembers class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
