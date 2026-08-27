// File: media/AudioRouteLogger.kt
package re.pinok.media

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import re.pinok.SovaApp
import re.pinok.util.AppLog

/**
 * Логирование активного audio output route + кодека Bluetooth.
 *
 * **Назначение.** Раньше при жалобе «звук стал приглушённым после подключения
 * BT» в логе было только `Audio devices added: [BT_A2DP]` — без кодека,
 * без имени устройства, без sample rate. Это делало диагностику невозможной:
 * непонятно, кодек виноват (SBC vs LDAC), reattach, или сам AudioEffect.
 *
 * Теперь при смене route пишется полная картина:
 * ```
 * AudioRouteLogger: active output → BT_A2DP "Sony WH-1000XM4" codec=LDAC sr=96000 ch=2
 * AudioRouteLogger: BassBoost/Loudness могут давать артефакты на LDAC > 990kbps
 * ```
 *
 * **Кодек.** На API 33+ (Android 13) `BluetoothA2dp.getCodecStatus()` отдаёт
 * тип кодека (SBC/AAC/aptX/aptX_HD/LDAC). Метод помечен `@SystemApi` и
 * отсутствует в публичном SDK stub — прямой вызов не компилируется ни при
 * каком `compileSdk`. Читаем через рефлексию: компилируется везде, работает
 * в runtime на Android 13+. На старых API пишем `codec=unknown(API<33)`.
 *
 * **BluetoothA2dp** требует `BLUETOOTH_CONNECT` permission на API 31+.
 * Без него `getProfileProxy` кидает SecurityException — мы это ловим и
 * пишем `codec=perm_denied`. Сам `AudioDeviceCallback` НЕ требует этого
 * permission — он работает и без него, просто без детальной BT-инфо.
 *
 * **SCO detection** (см. [isScoRoute]): звонковая гарнитура (HFP) —
 * моно 8kHz/16kHz, узкая полоса. Virtualizer/Reverb на SCO бессмысленны
 * и вредны (фазовые артефакты в узкополосном моно). Вызывается из
 * [AudioEffectsEngine] для auto-disable этих эффектов на SCO.
 */
object AudioRouteLogger {

    private const val TAG = "AudioRouteLogger"

    /**
     * Логирует активный output route + кодек. Вызывается из
     * [re.pinok.service.PlayerService] при срабатывании AudioDeviceCallback.
     *
     * @param changedToType тип устройства, на которое перешли (из
     *   AudioDeviceInfo.type). Используется только для лога «changed to».
     */
    fun logActiveRoute(changedToType: Int = -1) {
        try {
            val ctx = SovaApp.get()
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am == null) {
                AppLog.w(TAG, "logActiveRoute: AudioManager null")
                return
            }
            val sinks = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            // Берём sink с наивысшим приоритетом (external > built-in),
            // не просто firstOrNull — иначе на Hotwav Cyber 15 первым в
            // списке идёт EARPIECE и лог врёт про «active output → EARPIECE»
            // при подключённых BT-наушниках.
            val active = sinks.maxByOrNull { outputPriority(it) }
                ?.takeIf { outputPriority(it) > 0 }
                ?: sinks.firstOrNull()
            if (active == null) {
                AppLog.i(TAG, "active output → (none) — sinks empty")
                return
            }
            val typeName = deviceTypeName(active.type)
            val changedLabel = if (changedToType >= 0) " [changed to ${deviceTypeName(changedToType)}]" else ""
            // Для BT пытаемся прочитать кодек + имя устройства.
            val codecInfo = if (active.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                readBtCodecInfo(ctx)
            } else {
                ""
            }
            val sampleRate = try { active.sampleRates.firstOrNull() ?: 0 } catch (_: Exception) { 0 }
            val channels = try { active.channelMasks.size } catch (_: Exception) { 0 }
            AppLog.i(TAG, "active output → $typeName$changedLabel " +
                "sr=$sampleRate ch=$channels$codecInfo")
        } catch (e: Exception) {
            AppLog.w(TAG, "logActiveRoute failed: ${e.message}")
        }
    }

    /**
     * True если текущий активный output — Bluetooth SCO (звонковая гарнитура).
     * SCO = 8kHz/16kHz моно, узкая полоса, для голоса. Virtualizer/Reverb
     * на SCO дают фазовые артефакты и эхо — их надо отключать.
     *
     * A2DP — это **стерео** музыкальный профиль (SBC/AAC/aptX/LDAC),
     * на нём эффекты работают нормально.
     *
     * **Важно:** `getDevices(GET_DEVICES_OUTPUTS)` возвращает ВСЕ доступные
     * выходы, а не активный. У большинства BT-гарнитур HFP/SCO присутствует
     * всегда, пока наушники подключены (для входящих звонков) — даже когда
     * музыка идёт через A2DP. Поэтому простая проверка `sinks.any { SCO }`
     * возвращает true на любых BT-наушниках и ложно триггерит suspendForSco.
     *
     * Правильная логика: SCO активен только если
     *  - A2DP отсутствует (гарнитура в режиме звонка, не музыки), ИЛИ
     *  - система явно переключила вывод на SCO (во время звонка через
     *    BT-гарнитуру).
     *
     * Fix «приглушённого звука по BT»: раньше Virtualizer выключался на
     * любых BT-наушниках, потому что SCO всегда присутствовал в списке.
     *
     * Fix deprecation: `AudioManager.isBluetoothScoOn` помечен deprecated
     * в API 33 (без прямой замены в public API). Современный путь —
     * `AudioManager.getCommunicationDevice()` (API 31+, `S`): возвращает
     * активный communication device, установленный через `setCommunicationDevice`.
     * Если его тип `TYPE_BLUETOOTH_SCO` → SCO активен как voice-call route.
     * На API < 31 fallback на deprecated `isBluetoothScoOn` под @Suppress.
     */
    @Suppress("DEPRECATION")  // isBluetoothScoOn deprecated в API 33 без замены; на API 31+ используем communicationDevice
    fun isScoRoute(): Boolean = try {
        val ctx = SovaApp.get()
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val sinks = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val hasSco = sinks.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        val hasA2dp = sinks.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        // SCO как активный output = только если A2DP нет (гарнитура в режиме
        // звонка) либо система явно переключила вывод на SCO (Telecom-звонок).
        //
        // API 31+: getCommunicationDevice() — замена для isBluetoothScoOn.
        //   Возвращает AudioDeviceInfo активного communication device
        //   (устанавливается Telecom/VoIP apps через setCommunicationDevice).
        //   Если type == TYPE_BLUETOOTH_SCO → голосовой звонок идёт через BT.
        // API < 31: isBluetoothScoOn (нет замены в public API).
        //
        // @Suppress("DEPRECATION") на уровне функции — паттерн как в
        // AudioEffectsEngine.kt для Virtualizer (deprecated в API 33 без
        // замены). На API 31+ мы фактически НЕ вызываем isBluetoothScoOn
        // (используем communicationDevice), но компилятор видит ссылку в
        // else-ветке и эмитит warning без suppress.
        val scoActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.communicationDevice?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        } else {
            am.isBluetoothScoOn
        }
        (hasSco && !hasA2dp) || scoActive
    } catch (e: Exception) {
        AppLog.w(TAG, "isScoRoute failed: ${e.message}")
        false
    }

    // ─── Internals ──────────────────────────────────────────────

    /**
     * Эвристика приоритета: external devices (BT/wired/USB) приоритетнее
     * built-in (speaker/earpiece). AudioFlinger показывает несколько sink'ов
     * одновременно (earpiece + speaker + BT_A2DP) — берём external первым,
     * built-in только как fallback.
     *
     * Возвращает приоритет: 0 = не sink (пропустить), 1 = built-in, 2 = external.
     */
    private fun outputPriority(info: AudioDeviceInfo): Int = when (info.type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET -> 2
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> 1
        else -> 0
    }

    /**
     * Читает кодек BT A2DP + имя устройства через BluetoothA2dp profile proxy.
     * Требует BLUETOOTH_CONNECT на API 31+.
     *
     * Возвращает строку вида ` "Sony WH-1000XM4" codec=LDAC` или
     * пустую строку если не удалось прочитать.
     */
    @SuppressLint("MissingPermission")
    private fun readBtCodecInfo(ctx: Context): String {
        // BluetoothAdapter.getDefaultAdapter() deprecated в API 31 —
        // замена: BluetoothManager.getAdapter() через BLUETOOTH_SERVICE.
        val ba = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter ?: return ""
        var proxy: BluetoothA2dp? = null
        return try {
            // Получаем синхронно (этот вызов блокирует на ~50мс, но мы
            // уже на main handler'е PlayerService — приемлемо для лога).
            val ready = java.util.concurrent.CountDownLatch(1)
            var result: String = ""
            ba.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, p: android.bluetooth.BluetoothProfile) {
                    if (profile != BluetoothProfile.A2DP) { ready.countDown(); return }
                    @Suppress("UNCHECKED_CAST")
                    proxy = p as? BluetoothA2dp
                    result = try {
                        formatCodecInfo(proxy)
                    } catch (e: SecurityException) {
                        " codec=perm_denied(BLUETOOTH_CONNECT)"
                    } catch (e: Exception) {
                        " codec=read_err(${e.message})"
                    }
                    ready.countDown()
                }
                override fun onServiceDisconnected(profile: Int) {
                    ready.countDown()
                }
            }, BluetoothProfile.A2DP)
            // Ждём максимум 300мс — если BT-сервис не ответил, пишем unknown.
            ready.await(300, java.util.concurrent.TimeUnit.MILLISECONDS)
            result
        } catch (e: SecurityException) {
            " codec=perm_denied"
        } catch (e: Exception) {
            " codec=err(${e.message})"
        } finally {
            // Не закрываем proxy через close() — он shared, закроется с BT.
            // Просто отпускаем ссылку.
        }
    }

    @SuppressLint("MissingPermission")
    private fun formatCodecInfo(proxy: BluetoothA2dp?): String {
        if (proxy == null) return ""
        // Имя активного устройства + кодек.
        val devices = try { proxy.connectedDevices } catch (e: SecurityException) { return " codec=perm_denied" }
        val dev = devices.firstOrNull { proxy.isA2dpPlaying(it) } ?: devices.firstOrNull() ?: return ""
        val name = try { dev.name ?: "?" } catch (e: SecurityException) { "?" }
        val codecStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // API 33+: getCodecStatus() / getCodecConfig() / getCodecType() —
            // все @SystemApi, в публичном SDK stub их нет. Только рефлексия.
            readCodecTypeViaReflection(proxy, dev)
        } else {
            "unknown(API<33)"
        }
        return " \"$name\" codec=$codecStr"
    }

    /**
     * Читает тип кодека через рефлексию. Цепочка:
     * `BluetoothA2dp.getCodecStatus(device)` → `BluetoothCodecStatus.getCodecConfig()`
     * → `BluetoothCodecConfig.getCodecType()` (int).
     *
     * Все три метода — `@SystemApi(client = MODULE_LIBRARIES)`, их нет в
     * публичном SDK stub, прямой вызов не компилируется. Рефлексия работает
     * в runtime на Android 13+ без дополнительных разрешений сверх
     * `BLUETOOTH_CONNECT`.
     */
    @SuppressLint("MissingPermission")
    private fun readCodecTypeViaReflection(proxy: BluetoothA2dp, dev: BluetoothDevice): String {
        return try {
            val getStatus = proxy.javaClass
                .getMethod("getCodecStatus", BluetoothDevice::class.java)
            val status = getStatus.invoke(proxy, dev) ?: return "null-status"
            val getConfig = status.javaClass.getMethod("getCodecConfig")
            val config = getConfig.invoke(status) ?: return "null-config"
            val getType = config.javaClass.getMethod("getCodecType")
            // getCodecType() возвращает int (с @CodecType IntDef) на всех API 33+.
            val codecType = (getType.invoke(config) as Number).toInt()
            codecTypeName(codecType)
        } catch (e: NoSuchMethodException) {
            "no-method(SystemApi)"
        } catch (e: SecurityException) {
            "perm_denied"
        } catch (e: ClassCastException) {
            "codec-type-unexpected(${e.message})"
        } catch (e: Exception) {
            "err(${e.javaClass.simpleName})"
        }
    }

    /**
     * Имя типа кодека для лога. На API 33+ `BluetoothCodecConfig.getCodecType()`
     * возвращает int с `@CodecType` IntDef: 0=SBC, 1=AAC, 2=aptX, 3=aptX_HD,
     * 4=LDAC, 5=aptX_TWS, 6=aptX_Adaptive, 7=LC3.
     */
    private fun codecTypeName(codecType: Int?): String = when (codecType) {
        0 -> "SBC"
        1 -> "AAC"
        2 -> "aptX"
        3 -> "aptX_HD"
        4 -> "LDAC"
        5 -> "aptX_TWS"
        6 -> "aptX_Adaptive"
        7 -> "LC3"
        null -> "null"
        else -> "type=$codecType"
    }

    /** Читаемое имя типа audio device (совпадает с PlayerService.deviceTypeName). */
    private fun deviceTypeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "SPEAKER"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "EARPIECE"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BT_A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BT_SCO"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
        else -> "type=$type"
    }
}
