// File: util/PermissionManager.kt
package re.pinok.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
// Audit #40: удалены неиспользуемые импорты ComponentActivity и SovaApp.
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/**
 * Централизованный менеджер разрешений приложения.
 *
 * Проблема: приложение использует foreground-сервисы (MusicDownload, VideoDownload, Player),
 * но на Android 13+ (API 33) permission POST_NOTIFICATIONS НИКОГДА не запрашивался.
 * Без него уведомления сервисов не показываются — пользователь не видит прогресс загрузки,
 * не видит плеер в шторке. Также отсутствовали гранулярные медиа-разрешения (API 33+)
 * и MANAGE_EXTERNAL_STORAGE в манифесте (API 30+).
 *
 * Решение:
 *  - AndroidManifest.xml: добавлены READ_MEDIA_AUDIO/VIDEO/IMAGES, MANAGE_EXTERNAL_STORAGE
 *  - PermissionManager: единственная точка запроса ВСЕХ нужных runtime-разрешений
 *  - Вызывается из MainActivity при запуске (после авторизации)
 *
 * Разрешения, которые запрашиваются:
 *  1. POST_NOTIFICATIONS (API 33+) — критическое! Без него не работают foreground-сервисы
 *  2. READ_MEDIA_AUDIO (API 33+) — доступ к аудиофайлам для офлайн-менеджера
 *  3. READ_MEDIA_VIDEO (API 33+) — доступ к видеофайлам для офлайн-менеджера
 *  4. READ_MEDIA_IMAGES (API 33+) — доступ к фото (прикрепления, просмотр)
 *  5. RECORD_AUDIO (все API) — голосовые сообщения (запрос из ChatDetailScreen оставлен
 *     как fallback, но здесь тоже проверяется для единой логики)
 *
 * MANAGE_EXTERNAL_STORAGE (API 30+) — НЕ запрашивается стандартным диалогом.
 * Android запрещает это — только через Settings intent. Запрос реализован
 * в SettingsScreen (кастомный путь кэша).
 */
object PermissionManager {

    /**
     * Собрать список runtime-разрешений, которые нужно запросить
     * для текущей версии Android.
     */
    fun getRequiredPermissions(): List<String> {
        val perms = mutableListOf<String>()

        // RECORD_AUDIO — все API (голосовые сообщения)
        perms.add(Manifest.permission.RECORD_AUDIO)

        // #CALLS-REAL-CAMERA (2026-10-15): CAMERA — для реального захвата видео в звонках
        // (иначе SecurityException "cannot open camera without camera permission" при
        // videoTxEnabled). Запрашиваем при первом входе по умолчанию, как и микрофон.
        perms.add(Manifest.permission.CAMERA)

        // BLUETOOTH_CONNECT — API 31+ (dangerous).
        // Нужен для: диагностики BT-кодека (AudioRouteLogger), BT-маршрутизации
        // звонков (CallsContainer) и reattach эквалайзера при переключении BT
        // (PlayerService). Объявлен в манифесте, но без runtime-запроса он никогда
        // не выдаётся → кодек всегда "perm_denied", а звонки не могут выбрать
        // BT-гарнитуру. Запрашиваем пакетно на старте (не блокирует вход).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // API 33+: POST_NOTIFICATIONS — критическое для foreground-сервисов
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
            // API 33+: гранулярные медиа-разрешения (замена READ_EXTERNAL_STORAGE)
            perms.add(Manifest.permission.READ_MEDIA_AUDIO)
            perms.add(Manifest.permission.READ_MEDIA_VIDEO)
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // API 23-32: классическое READ_EXTERNAL_STORAGE
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        return perms
    }

    /**
     * Фильтрует список, оставляя только те разрешения, которые ЕЩЁ не выданы.
     */
    fun getNotGrantedPermissions(context: Context): List<String> {
        return getRequiredPermissions().filter { perm ->
            ContextCompat.checkSelfPermission(context, perm) !=
                PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Проверить, выдано ли конкретное разрешение.
     */
    fun isGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Проверить, выдано ли POST_NOTIFICATIONS (или не требуется на этом API).
     */
    fun hasNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return isGranted(context, Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * Проверить, выдано ли RECORD_AUDIO.
     */
    fun hasRecordAudio(context: Context): Boolean {
        return isGranted(context, Manifest.permission.RECORD_AUDIO)
    }

    /**
     * #CALLS-REAL-CAMERA: проверить, выдано ли CAMERA (реальный захват камеры).
     */
    fun hasCamera(context: Context): Boolean {
        return isGranted(context, Manifest.permission.CAMERA)
    }

    /**
     * #ALL-FILES (2026-10-15): есть ли «Все файлы» (MANAGE_EXTERNAL_STORAGE, API 30+).
     * Это специальное разрешение (не через system-диалог, а через системные настройки).
     * На Android 11+ обычного «диалога записи всех файлов» нет — только Settings-экран.
     */
    fun hasAllFilesAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        return Environment.isExternalStorageManager()
    }

    /**
     * #ALL-FILES: Intent в системные настройки для ручного включения «Все файлы»
     * (MANAGE_EXTERNAL_STORAGE). Запускается activity-контекстом. Вернёт null, если
     * экран недоступен (старый API) — тогда управление файлами не требует разрешения.
     */
    fun allFilesAccessSettingsIntent(context: Context): android.content.Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                android.net.Uri.parse("package:${context.packageName}")
            )
            // Если intent без package не находится — общий экран управления доступом.
            val pm = context.packageManager
            val resolveInfo = pm.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            if (resolveInfo != null) intent else
                android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        } catch (e: Throwable) {
            AppLog.w("PermissionManager", "allFilesAccessSettingsIntent: ${e.message}")
            null
        }
    }

    /**
     * Проверить, есть ли доступ к медиафайлам (audio/video/images).
     */
    fun hasMediaAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return isGranted(context, Manifest.permission.READ_MEDIA_AUDIO) &&
                isGranted(context, Manifest.permission.READ_MEDIA_VIDEO) &&
                isGranted(context, Manifest.permission.READ_MEDIA_IMAGES)
        }
        return isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

/**
 * Composable-хук для автоматического запроса всех недостающих разрешений.
 *
 * Используется в MainActivity. При первом запуске (или после обновления)
 * показывает системный диалог для POST_NOTIFICATIONS и гранулярных медиа-разрешений.
 *
 * Не блокирует UI — пользователь может отклонить, приложение продолжит работу
 * (но уведомления не будут показываться).
 */
@Composable
fun RequestAllPermissionsEffect() {
    val context = androidx.compose.ui.platform.LocalContext.current

    // rememberLauncherForActivityResult для множественного запроса разрешений
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val results = grants.map { (perm, granted) ->
            val name = perm.substringAfterLast(".")
            "$name=${if (granted) "GRANTED" else "DENIED"}"
        }.joinToString(", ")
        AppLog.i("PermissionManager", "Permission results: $results")

        // Если POST_NOTIFICATIONS отклонён — логируем предупреждение
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (grants[Manifest.permission.POST_NOTIFICATIONS] == false) {
                AppLog.w("PermissionManager", "POST_NOTIFICATIONS denied — foreground service notifications will not show on Android 13+")
            }
        }
    }

    // Считаем, сколько раз мы уже пытались запросить разрешения.
    // Audit #40: используем remember (не rememberSaveable, как говорил старый комментарий) —
    // флаг не переживает пересоздание Activity, но этого достаточно чтобы не спамить
    // запросом при каждом recomposition в рамках одной сессии.
    var requestAttempted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (requestAttempted) return@LaunchedEffect
        requestAttempted = true

        val needed = PermissionManager.getNotGrantedPermissions(context)
        if (needed.isNotEmpty()) {
            AppLog.i("PermissionManager", "Requesting ${needed.size} permissions: ${needed.map { it.substringAfterLast(".") }}")
            launcher.launch(needed.toTypedArray())
        } else {
            AppLog.i("PermissionManager", "All permissions already granted")
        }

        // #ALL-FILES (2026-10-15): MANAGE_EXTERNAL_STORAGE («Все файлы») — специальное
        // разрешение, его НЕТ в обычном системном диалоге: включается только через
        // системные Настройки → «Все файлы». Если доступа ещё нет — открываем экран
        // разрешения для этого приложения (не блокирует вход; пользователь решит сам).
        // Откладываем на ~1.5с, чтобы системный диалог обычных прав успел закрыться
        // и не наслаиваться на экран Настроек (два системных окна одновременно — глюк).
        if (!PermissionManager.hasAllFilesAccess(context)) {
            kotlinx.coroutines.delay(1500)
            val intent = PermissionManager.allFilesAccessSettingsIntent(context)
            if (intent != null) {
                // Для запуска системного экрана нужен Activity-контекст.
                val activity = context as? android.app.Activity
                if (activity != null) {
                    AppLog.i("PermissionManager", "#ALL-FILES: открываю системные настройки «Все файлы»")
                    activity.startActivity(intent)
                } else {
                    AppLog.w("PermissionManager", "#ALL-FILES: контекст не Activity — системный экран не открыт (root/вне Activity)")
                }
            } else {
                AppLog.i("PermissionManager", "#ALL-FILES: экран недоступен (старый API) — доступ не требуется")
            }
        } else {
            AppLog.i("PermissionManager", "#ALL-FILES: «Все файлы» уже предоставлены")
        }
    }
}