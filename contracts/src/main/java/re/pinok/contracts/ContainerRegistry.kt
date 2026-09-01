package re.pinok.contracts

/**
 * Реестр контейнеров и capability'ей (Этап 1.1).
 *
 * Владелец единственного экземпляра — хост (:app). Контейнеры регистрируются
 * ДО их init() (см. SovaApp.onCreate). Хост строит UI из реестра:
 *   вкладки   = registry.find<NavEntry>().sortedBy { it.order }
 *   настройки = registry.find<SettingsSection>().sortedBy { it.order }
 * Отсутствие контейнера = отсутствие его capability = пункт не рендерится.
 *
 * Контейнер МОЖЕТ спросить реестр о чужих capability (слабая связь через
 * контракты), но НЕ должен импортировать классы чужих :feature-модулей.
 */
object ContainerRegistry {

    /** Ссылка на capability: пара (контейнер, ключ). */
    data class CapRef(val containerId: String, val capKey: String)

    private val containers = linkedMapOf<String, AppContainer>()
    private val caps = LinkedHashMap<CapRef, Capability>()

    @Synchronized
    fun register(container: AppContainer) {
        check(container.id.isNotBlank()) { "id контейнера пуст" }
        check(container.id !in containers) { "Контейнер '${container.id}' уже зарегистрирован" }
        containers[container.id] = container
        container.capabilities().forEach { c ->
            caps[CapRef(container.id, c.key)] = c
        }
    }

    /** Снятие контейнера (hot-disable на уровне реестра; UI перечитает реестр при следующем построении). */
    @Synchronized
    fun unregister(id: String) {
        containers.remove(id)
        caps.keys.removeAll { it.containerId == id }
    }

    @Synchronized
    fun containers(): List<AppContainer> = containers.values.toList()

    @Synchronized
    fun byId(id: String): AppContainer? = containers[id]

    /** Снапшот capability'ей — единая точка синхронизации для inline find().
     *  @PublishedApi: public-inline find() не может звать private-член (Kotlin: Public-API inline restriction). */
    @PublishedApi
    @Synchronized
    internal fun capsSnapshot(): List<Capability> = caps.values.toList()

    /** Все capability заданного типа (например find<NavEntry>()). */
    inline fun <reified T : Capability> find(): List<T> = capsSnapshot().filterIsInstance<T>()

    @Synchronized
    fun capabilities(): List<Capability> = caps.values.toList()
}
