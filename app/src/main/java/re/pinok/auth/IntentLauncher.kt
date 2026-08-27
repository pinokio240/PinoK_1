package re.pinok.auth

import android.content.Intent
import android.net.Uri
import re.pinok.SovaApp
import re.pinok.util.AppLog

/**
 * P2-10 #AUTH-AUDIT: единая точка запуска intent:// / custom schemes / market://
 * из WebView (shouldOverrideUrlLoading).
 *
 * Раньше tryLaunchIntentUrl / tryLaunchCustomScheme / tryLaunchMarketUrl были
 * private функциями в AuthActivity.kt (V1) и дублировались как private в
 * VkAuthWebViewScreenV2.kt. Этот object — единственная реализация.
 *
 * **launchIntentUrl** — полноценный парсинг intent:// URI через
 * [Intent.parseUri] с флагом [Intent.URI_INTENT_SCHEME]. Это правильнее
 * чем простой `Intent(ACTION_VIEW, Uri.parse(url))`, потому что intent://
 * URI содержит package name, fallback URL и доп. флаги:
 * ```
 * intent://qr.vk.ru/ca?q=AUTH_HASH#Intent;
 *   scheme=https;package=com.vkontakte.android;
 *   S.browser_fallback_url=market://details?id=com.vkontakte.android;end
 * ```
 *
 * Если целевое приложение установлено — запускается оно. Если НЕ установлено —
 * пытаемся открыть `S.browser_fallback_url` (обычно market:// ссылка).
 *
 * **launchCustomScheme** — для `vkontakte://`, `vk://`, `vklink://` схем.
 * Запускает intent напрямую, без fallback (custom schemes не имеют fallback).
 *
 * **launchMarketUrl** — для `market://` ссылок. Преобразует в https://
 * Play Store URL как fallback если Play Store не установлен.
 *
 * @return `true` если intent запущен успешно (или fallback открыт),
 *         `false` если не удалось. В случае `false` WebView продолжит
 *         загружать URL (что покажет ошибку — это нормально).
 */
object IntentLauncher {

    private const val TAG = "IntentLauncher"

    /**
     * Парсит intent:// URL и запускает целевое приложение.
     *
     * Поддерживает:
     *   - `intent://...#Intent;scheme=X;package=Y;end`
     *   - `S.browser_fallback_url=...` — fallback если package не установлен
     *   - обычные http/https/custom-scheme URLs (как fallback)
     */
    fun launchIntentUrl(url: String): Boolean {
        return try {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            val ctx = SovaApp.get()

            // Пробуем запустить целевое приложение напрямую.
            // resolveActivity проверит, установлено ли приложение — иначе
            // startActivity упадёт с ActivityNotFoundException.
            if (intent.resolveActivity(ctx.packageManager) != null) {
                ctx.startActivity(intent)
                AppLog.i(TAG, "launchIntentUrl: запущен package=${intent.`package`} url=$url")
                true
            } else {
                // Целевое приложение НЕ установлено — пробуем fallback URL.
                val fallback = intent.getStringExtra("browser_fallback_url")
                if (fallback != null) {
                    AppLog.i(TAG, "launchIntentUrl: package не установлен, fallback → $fallback")
                    launchMarketUrl(fallback) || launchActionView(fallback)
                } else {
                    AppLog.w(TAG, "launchIntentUrl: package не установлен и fallback отсутствует — url=$url")
                    false
                }
            }
        } catch (e: Exception) {
            // Если URL не формат intent:// — пробуем как обычный ACTION_VIEW.
            AppLog.w(TAG, "launchIntentUrl: parseUri failed (${e.message}), пробуем ACTION_VIEW → $url")
            launchActionView(url)
        }
    }

    /**
     * Запускает custom scheme URL (`vkontakte://`, `vk://`, `vklink://`).
     *
     * Custom schemes НЕ имеют fallback (в отличие от intent://) — если
     * приложение не установлено, возвращаем false.
     */
    fun launchCustomScheme(url: String, scheme: String): Boolean {
        AppLog.i(TAG, "launchCustomScheme: scheme=$scheme url=$url")
        return launchActionView(url)
    }

    /**
     * Запускает market:// URL. Если Play Store не установлен — fallback на
     * https://play.google.com/store/apps/details?id=...
     */
    fun launchMarketUrl(url: String): Boolean {
        return try {
            val ctx = SovaApp.get()
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (marketIntent.resolveActivity(ctx.packageManager) != null) {
                ctx.startActivity(marketIntent)
                AppLog.i(TAG, "launchMarketUrl: Play Store запущен → $url")
                true
            } else {
                // Play Store не установлен — fallback на https://play.google.com.
                // Извлекаем package id из market://details?id=X
                val packageId = Uri.parse(url).getQueryParameter("id")
                if (packageId != null) {
                    val httpsUrl = "https://play.google.com/store/apps/details?id=$packageId"
                    AppLog.i(TAG, "launchMarketUrl: Play Store не установлен, fallback → $httpsUrl")
                    launchActionView(httpsUrl)
                } else {
                    AppLog.w(TAG, "launchMarketUrl: Play Store не установлен и package id отсутствует — url=$url")
                    false
                }
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "launchMarketUrl failed: ${e.message}")
            false
        }
    }

    /**
     * Простой запуск ACTION_VIEW для произвольного URL. Используется как
     * fallback для http/https/custom schemes.
     */
    fun launchActionView(url: String): Boolean {
        return try {
            val ctx = SovaApp.get()
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(ctx.packageManager) != null) {
                ctx.startActivity(intent)
                AppLog.i(TAG, "launchActionView: запущен → $url")
                true
            } else {
                AppLog.w(TAG, "launchActionView: no activity handles url=$url")
                false
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "launchActionView failed: ${e.message}")
            false
        }
    }
}
