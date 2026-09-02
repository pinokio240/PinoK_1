package re.pinok.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import re.pinok.util.AppLog
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Network connectivity observer — reactive + snapshot.
 *
 * Раньше был только snapshot-метод `isOnline()`. Теперь:
 *  - [isOnlineFlow] — StateFlow, эмитит true/false при каждом изменении сети.
 *  - [isOnline()] — по-прежнему snapshot для быстрой проверки.
 *  - Подписывается на ConnectivityManager.NetworkCallback — моментально
 *    узнаёт о смене WiFi↔Mobile, потере связи, восстановлении.
 *  - LongPollClient слушает этот flow и прерывает poll при потере сети,
 *    немедленно переподключается при восстановлении.
 *  - При потере сети — вызывает `httpClient.connectionPool.evictAll()`
 *    чтобы очистить застоявшиеся TCP-соединения на мёртвом интерфейсе.
 */
class NetworkObserver(private val context: Context) {

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkOnline())
    val isOnlineFlow: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val registered = AtomicBoolean(false)
    private var currentNetwork: Network? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    /** Fix #171: callback для registerDefaultNetworkCallback (смена default route). */
    private var defaultCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Fix #175: Timestamp последней смены default network (mobile↔Wi-Fi).
     *
     * VK API иногда возвращает error 5 (token invalid) / 1117 (token expired)
     * сразу после смены IP-адреса клиента (Wi-Fi → Mobile = другой подсеть/AS).
     * Это VK-серверная security-фича: «подозрительная активность — инвалидируем
     * токен». Но через 5-15 секунд после switch'а токен снова начинает работать.
     *
     * [VKApiClient] читает это поле в обработчике error 5/1117: если switch был
     * недавно (< 30 сек) — НЕ чистит access_token и НЕ триггерит AuthActivity,
     * а делает задержку + retry. Только если ошибка persist'ит после grace
     * периода — реальная инвалидация.
     */
    @Volatile
    var lastDefaultNetworkSwitchTs: Long = 0L
        private set

    /**
     * Fix #250: флаг первого onAvailable от registerDefaultNetworkCallback.
     *
     * Согласно Android docs, первый onAvailable срабатывает СРАЗУ при
     * регистрации callback'а с текущей default network — это НЕ switch,
     * это инициализация. Но раньше мы обновляли lastDefaultNetworkSwitchTs
     * даже для первого onAvailable → grace period ложно активировался при
     * холодном старте приложения → 31 секунда ложных retry'ев при error 1117
     * (когда токен реально нерабочий, а не "потерпите 5с после switch").
     *
     * Теперь первый onAvailable просто помечает флаг false и НЕ обновляет
     * timestamp. Только последующие onAvailable (реальная смена default route)
     * запускают grace period.
     */
    @Volatile
    private var firstDefaultAvailable: Boolean = true

    /**
     * Fix #180: последний известный IP-адрес default network.
     *
     * `onLinkPropertiesChanged` срабатывает при ЛЮБОМ изменении link properties
     * — включая DHCP renewal, IPv6 prefix change, DNS change. VK может
     * инвалилировать token при ЛЮБОМ изменении IP (не только при switch
     * интерфейса). Это поле позволяет сравнить старый и новый IP и точно
     * определить, был ли IP change.
     *
     * Пустая строка = первый запуск / нет IP. Формат: "1.2.3.4, 2001:db8::1"
     * (join всех linkAddresses).
     */
    @Volatile
    private var lastKnownIp: String = ""

    /** Fix #175: True если последняя смена default network была в течение [windowMs]. */
    fun isRecentlySwitched(windowMs: Long = 30_000L): Boolean {
        val ts = lastDefaultNetworkSwitchTs
        if (ts == 0L) return false
        return System.currentTimeMillis() - ts < windowMs
    }

    /** Список callback'ов для очистки stale connections при ПОЛНОЙ потере сети. */
    private val onLostListeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    /**
     * Fix #171: Список callback'ов для уведомления о СМЕНЕ default network
     * (mobile→Wi-Fi или Wi-Fi→mobile), когда сеть осталась онлайн.
     *
     * Это отдельный сигнал от [onLostListeners]: onLost срабатывает при потере
     * ЛЮБОЙ сети (даже если остался второй интерфейс), а default-network-changed
     * — только когда система переключила default route на другой интерфейс.
     *
     * Подписчики (SovaApp, PlayerConnection) используют это чтобы мягко
     * перезапустить HLS-стрим на новом интерфейсе БЕЗ cancelAll() (которое
     * убивало ExoPlayer при switch).
     */
    private val onDefaultNetworkChangedListeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    /** Зарегистрировать listener для очистки соединений при ПОЛНОЙ потере сети. */
    fun addOnNetworkLostListener(listener: () -> Unit) {
        onLostListeners.add(listener)
    }

    /**
     * Fix #233 (P1-6): Удалить listener потери сети.
     * Без этого listener'ы накапливаются при многократных start()/stop()
     * (LongPollClient.start() регистрирует лямбду каждый раз) и держат
     * ссылку на мёртвый LongPollClient через захват httpClient.
     */
    fun removeOnNetworkLostListener(listener: () -> Unit) {
        onLostListeners.remove(listener)
    }

    /**
     * Fix #171: Зарегистрировать listener для уведомления о смене default
     * network (switch WiFi↔Mobile без потери связи).
     */
    fun addOnDefaultNetworkChangedListener(listener: () -> Unit) {
        onDefaultNetworkChangedListeners.add(listener)
    }

    /**
     * Fix #233 (P1-6): Удалить listener смены default network.
     */
    fun removeOnDefaultNetworkChangedListener(listener: () -> Unit) {
        onDefaultNetworkChangedListeners.remove(listener)
    }

    /**
     * Активировать реактивный мониторинг. Безопасно вызывать несколько раз.
     * Должен вызываться один раз при старте приложения.
     */
    fun register() {
        if (!registered.compareAndSet(false, true)) return
        val connectivityManager = cm ?: return
        AppLog.i("NetworkObserver", "registering NetworkCallback")

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                AppLog.i("NetworkObserver", "onAvailable: $network")
                currentNetwork = network
                _isOnline.value = true
            }

            override fun onLost(network: Network) {
                AppLog.w("NetworkObserver", "onLost: $network")
                currentNetwork = null
                val stillOnline = checkOnline()
                _isOnline.value = stillOnline
                // Fix #171: вызываем onLostListeners ТОЛЬКО при полной потере сети.
                // Раньше onLost срабатывал на потерю ЛЮБОЙ сети — даже если остался
                // второй интерфейс (switch mobile→Wi-Fi). Это вызывало
                // httpClient.dispatcher.cancelAll() который убивал HLS-стрим ExoPlayer
                // (он использует тот же httpClient через OkHttpDataSource).
                // При switch — default-network-changed listener сделает мягкий reset.
                if (!stillOnline) {
                    AppLog.i("NetworkObserver", "onLost: fully offline — evicting pool + cancelAll")
                    onLostListeners.forEach { listener ->
                        try { listener() } catch (_: Exception) {}
                    }
                } else {
                    AppLog.i("NetworkObserver", "onLost: network switched (still online) — skip cancelAll, default-network-changed listener will handle")
                }
            }

            override fun onUnavailable() {
                AppLog.w("NetworkObserver", "onUnavailable")
                _isOnline.value = false
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                if (hasInternet != _isOnline.value) {
                    AppLog.i("NetworkObserver", "capabilities changed, online=$hasInternet")
                    _isOnline.value = hasInternet
                }
            }
        }.also { callback = it })

        // Fix #171: отдельный callback для default network — эмитит ТОЛЬКО смену
        // default route (mobile→Wi-Fi или Wi-Fi→mobile), без ложных срабатываний
        // на потерю не-default сети. Доступен с API 24 (minSdk=24 ✓).
        //
        // Согласно Android docs: onAvailable = система выбрала новый default
        // network; onLost = нет default network вообще (полная потеря связи).
        // Это точно различает «switch» (есть onAvailable без предшествующего
        // onLost) и «полная потеря» (onLost без последующего onAvailable).
        try {
            connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    // Fix #250: первый onAvailable при регистрации callback'а —
                    // это НЕ switch, это инициализация (Android docs: "called
                    // immediately with the current default network"). Раньше мы
                    // обновляли lastDefaultNetworkSwitchTs даже для первого
                    // onAvailable → grace period ложно активировался при холодном
                    // старте → 31 секунда ложных retry'ев при error 1117.
                    if (firstDefaultAvailable) {
                        firstDefaultAvailable = false
                        AppLog.i("NetworkObserver", "DEFAULT onAvailable: $network (initial — NOT a switch, grace period NOT armed)")
                        // НЕ обновляем lastDefaultNetworkSwitchTs.
                        // lastKnownIp обновится в onLinkPropertiesChanged следом.
                        return
                    }
                    AppLog.i("NetworkObserver", "DEFAULT onAvailable: $network (default route switched)")
                    // Fix #175: записываем timestamp switch'а для grace period
                    // в VKApiClient (см. error 5/1117 handler).
                    lastDefaultNetworkSwitchTs = System.currentTimeMillis()
                    // Fix #180: сбрасываем lastKnownIp — onLinkPropertiesChanged
                    // сработает следом и обновит. Это нужно чтобы первый
                    // onLinkPropertiesChanged после switch не выглядел как "IP не
                    // изменился" (если случайнно совпадёт со старым).
                    lastKnownIp = ""
                    // Смена default network — уведомляем подписчиков для мягкого
                    // reset'а (evictAll без cancelAll) + reprepare плеера.
                    onDefaultNetworkChangedListeners.forEach { listener ->
                        try { listener() } catch (_: Exception) {}
                    }
                }

                /**
                 * Fix #180: точная детекция IP/DNS change БЕЗ switch интерфейса.
                 *
                 * `onLinkPropertiesChanged` срабатывает при DHCP renewal, IPv6
                 * prefix change, DNS change, captive portal resolution — все эти
                 * случаи могут инвалидировать VK token (security feature: IP change
                 * = suspicious activity), но НЕ триггерят `onAvailable` (т.к.
                 * default route не сменился, остался тот же интерфейс).
                 *
                 * Сравниваем новый IP-набор с [lastKnownIp]. Если отличается —
                 * обновляем [lastDefaultNetworkSwitchTs] (запускаем grace period
                 * для VKApiClient) + уведомляем onDefaultNetworkChangedListeners
                 * (мягкий reset OkHttp pool + reprepare плеера).
                 */
                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                    val newIp = linkProperties.linkAddresses.joinToString { it.address.hostAddress ?: "" }
                    // #GRACE-FALSE-POSITIVE (Fix #177): ПРОВЕРКА "первый запуск"
                    // ДОЛЖНА БЫТЬ ПЕРВОЙ. Раньше условие `newIp != lastKnownIp`
                    // было истинно при lastKnownIp="" (холодный старт) → grace period
                    // ложно активировался → VKApiClient НЕ вызывал notifyTokenInvalidated
                    // 30 сек после старта → пользователь висел без авторизации.
                    // Теперь: если lastKnownIp пустой — это init, просто запоминаем.
                    if (lastKnownIp.isBlank() && newIp.isNotBlank()) {
                        // Первый запуск / первое получение IP — просто запоминаем,
                        // без уведомления (это не switch, это init).
                        lastKnownIp = newIp
                        AppLog.i("NetworkObserver", "DEFAULT onLinkPropertiesChanged: initial IP assigned '$newIp' (NOT a switch, grace period NOT armed)")
                        return
                    }
                    if (newIp != lastKnownIp && newIp.isNotBlank()) {
                        AppLog.i("NetworkObserver", "DEFAULT onLinkPropertiesChanged: IP changed '$lastKnownIp' → '$newIp' (DHCP renewal / IPv6 prefix / DNS)")
                        lastKnownIp = newIp
                        // IP change = эквивалент switch для VK security — обновляем
                        // timestamp grace period'а чтобы VKApiClient НЕ чистил token
                        // при последующем error 5/1117.
                        lastDefaultNetworkSwitchTs = System.currentTimeMillis()
                        // Мягкий reset (evictAll без cancelAll) + reprepare плеера —
                        // OkHttp connection pool может держать stale connections
                        // на старом DNS resolution.
                        onDefaultNetworkChangedListeners.forEach { listener ->
                            try { listener() } catch (_: Exception) {}
                        }
                    }
                }

                /**
                 * Fix #179: Doze/sleep mode recovery.
                 *
                 * `onBlockedStatusChanged` срабатывает когда:
                 *   - приложение уходит в background → blocked=true
                 *   - Android переходит в Doze → blocked=true
                 *   - приложение возвращается в foreground → blocked=false
                 *   - Android выходит из Doze → blocked=false
                 *
                 * Во время Doze сокеты могли "застыть" (TCP keep-alive не отправлялся,
                 * сервер закрыл соединение). При выходе из Doze (blocked=false) нужно
                 * мягко reset'нуть OkHttp pool + reprepare плеер — аналогично switch.
                 *
                 * ВНИМАНИЕ #DOZE-NO-GRACE (2026-08-02): РАНЬШЕ здесь обновлялся
                 * lastDefaultNetworkSwitchTs → запускался grace period 30с в
                 * VKApiClient. Но выход из Doze НЕ меняет IP-адрес (маршрут тот же,
                 * просто сокеты "застыли"). VK НЕ возвращает 5/1130 просто из-за
                 * выхода из Doze — только при реальной смене сети.
                 *
                 * Ложный grace period маскировал реальную проблему: при каждом
                 * возврате из фона VKApiClient получал err=5 (если токен реально
                 * истёк к тому моменту) → grace period делал delay 5с + retry →
                 * второй err=5 → clearAccessToken + notifyTokenInvalidated →
                 * AuthActivity. Пользователь жаловался: "переключение ви-фи на
                 * мобильную сеть требует регистрацию".
                 *
                 * Фикс: НЕ обновляем lastDefaultNetworkSwitchTs здесь. evictAll +
                 * reprepare плеера остаются (сокеты могли стать stale). Grace period
                 * срабатывает ТОЛЬКО при реальном onAvailable (смена default route)
                 * или onLinkPropertiesChanged с IP change.
                 *
                 * Доступен с API 29 (Android 10). minSdk=24, поэтому guarded.
                 * Баг Android 9: onBlockedStatusChanged не вызывается при регистрации
                 * callback — нужно предположить unblocked по умолчанию (см. Google
                 * Issue Tracker #226640805).
                 */
                override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                    AppLog.i("NetworkObserver", "DEFAULT onBlockedStatusChanged: blocked=$blocked (Doze/sleep)")
                    if (!blocked) {
                        // Вышли из Doze — мягкий reset как при switch.
                        // evictAll закроет stale keep-alive, reprepare плеер
                        // перестроит HLS MediaSource.
                        // #DOZE-NO-GRACE: НЕ обновляем lastDefaultNetworkSwitchTs —
                        // выход из Doze НЕ меняет IP, VK не вернёт 5/1130 только
                        // из-за возврата из фона. Grace period срабатывает только
                        // при реальном onAvailable (смена default route).
                        onDefaultNetworkChangedListeners.forEach { listener ->
                            try { listener() } catch (_: Exception) {}
                        }
                    }
                }
            }.also { defaultCallback = it })
        } catch (e: Exception) {
            AppLog.e("NetworkObserver", "registerDefaultNetworkCallback failed", e)
        }
    }

    /** Снятие регистрации. Вызывать при остановке приложения. */
    fun unregister() {
        if (!registered.compareAndSet(true, false)) return
        val connectivityManager = cm ?: return
        val cb = callback ?: return
        try {
            connectivityManager.unregisterNetworkCallback(cb)
        } catch (_: Exception) {}
        callback = null
        // Fix #171: снимаем и default network callback.
        defaultCallback?.let { dcb ->
            try { connectivityManager.unregisterNetworkCallback(dcb) } catch (_: Exception) {}
        }
        defaultCallback = null
        // Fix #250: сбрасываем флаг первого onAvailable — при следующем register()
        // первый onAvailable снова должен считаться инициализацией, не switch'ем.
        firstDefaultAvailable = true
    }

    fun isOnline(): Boolean = _isOnline.value

    fun isOffline(): Boolean = !_isOnline.value

    /** Connection type string for display (e.g. in settings). */
    fun connectionType(): String {
        val connectivityManager = cm ?: return "none"
        val nw = connectivityManager.activeNetwork ?: return "none"
        val caps = connectivityManager.getNetworkCapabilities(nw) ?: return "none"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "other"
        }
    }

    private fun checkOnline(): Boolean {
        val connectivityManager = cm ?: return false
        val nw = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(nw) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}