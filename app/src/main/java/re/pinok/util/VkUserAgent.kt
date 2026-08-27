package re.pinok.util

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import java.util.Locale

/**
 * Генератор User-Agent в формате официального VK Android-клиента.
 *
 * Извлечено из декомпилята SOVA RE (`defpackage/C7754aaaaa.java:83`):
 * ```java
 * String.format("VKAndroidApp/%s-%d (Android %s; SDK %d; %s; %s %s; %s; %dx%d)",
 *     packageInfo.versionName, Integer.valueOf(packageInfo.versionCode),
 *     Build.VERSION.RELEASE, Integer.valueOf(Build.VERSION.SDK_INT),
 *     Build.CPU_ABI, Build.MANUFACTURER, Build.MODEL,
 *     Locale.getDefault().getLanguage(),
 *     displayMetrics.heightPixels, displayMetrics.widthPixels);
 * ```
 *
 * VK API отбрасывает не-официальные клиенты (error 15 "Access denied"
 * на messages.* / audio.*) именно по User-Agent. Статический хардкод
 * `VKAndroidApp/8.178-12345 (Android 14; SDK 34; arm64-v8a; Android-Studio; ru; smartphone)`
 * некорректен: `Android-Studio` вместо manufacturer, `smartphone` вместо WxH.
 */
object VkUserAgent {

    private const val FALLBACK_UA =
        "VKAndroidApp/8.38-16786 (Android 10.0.0; SDK 29; armeabi-v7a; ONEPLUS A5010; ru; 2160x1080)"

    @Volatile
    private var cached: String? = null

    /**
     * Возвращает User-Agent. Кешируется после первого вызова
     * (Build.* и PackageInfo не меняются в runtime).
     */
    @Synchronized
    fun get(app: Application): String {
        cached?.let { return it }
        val ua = build(app)
        cached = ua
        return ua
    }

    private fun build(app: Application): String {
        return try {
            val pkg = app.packageManager.getPackageInfo(app.packageName, 0)
            val dm = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val metrics = app.getSystemService(android.view.WindowManager::class.java)
                    .currentWindowMetrics
                android.util.DisplayMetrics().also {
                    it.widthPixels = metrics.bounds.width()
                    it.heightPixels = metrics.bounds.height()
                    it.density = app.resources.displayMetrics.density
                    it.densityDpi = (it.density * 160).toInt()
                }
            } else {
                @Suppress("DEPRECATION")
                val wm = app.getSystemService("window") as WindowManager
                @Suppress("DEPRECATION")
                val dm = android.util.DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(dm)
                dm
            }

            val abi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Build.SUPPORTED_ABIS.firstOrNull() ?: @Suppress("DEPRECATION") Build.CPU_ABI
            } else {
                @Suppress("DEPRECATION")
                Build.CPU_ABI
            }

            String.format(
                Locale.US,
                "VKAndroidApp/%s-%d (Android %s; SDK %d; %s; %s %s; %s; %dx%d)",
                pkg.versionName,
                @Suppress("DEPRECATION") pkg.versionCode,
                Build.VERSION.RELEASE,
                Build.VERSION.SDK_INT,
                abi,
                Build.MANUFACTURER,
                Build.MODEL,
                Locale.getDefault().language,
                dm.heightPixels,
                dm.widthPixels,
            )
        } catch (e: PackageManager.NameNotFoundException) {
            AppLog.w(TAG, "PackageInfo not found, using fallback UA", e)
            FALLBACK_UA
        } catch (e: Exception) {
            AppLog.w(TAG, "UA generation failed, using fallback", e)
            FALLBACK_UA
        }
    }

    private const val TAG = "VkUserAgent"
}
