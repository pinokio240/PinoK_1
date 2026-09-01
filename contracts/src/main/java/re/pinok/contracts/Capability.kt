package re.pinok.contracts

import android.app.Application

/**
 * #ARCH-CONTAINERS (Этап 1.1): контрактный слой модульной архитектуры.
 *
 * Контейнер — самостоятельный раздел приложения (звонки, фото, аудио),
 * оформленный как Gradle-модуль :feature:<id>. Он публикует в реестр набор
 * Capability — единиц функциональности, из которых хост собирает UI
 * (навигацию, настройки, обработчики вложений). Контейнера нет — capability
 * нет — пункт UI не рендерится, ядро приложения продолжает работать
 * (graceful деградация).
 *
 * ПРАВИЛА (несоблюдение убивает «удаляемость»):
 *  1. :feature:* зависят ТОЛЬКО от :contracts и :core:* — никогда друг от друга.
 *  2. Контейнеры не держат ссылок на хост-классы (:app) — только через контракты.
 *  3. Своё состояние контейнер хранит изолированно: свои таблицы/префы/подкаталоги.
 *
 * Этот файл — только интерфейсы, БЕЗ androidx/compose: контракты должны
 * собираться быстрее всего остального и не тянуть UI-стек в consumers.
 */
interface AppContainer {
    /** Уникальный идентификатор: "calls", "photos", "audio". */
    val id: String

    /** Capability, публикуемые контейнером в реестр. Вызывается один раз при register(). */
    fun capabilities(): List<Capability>

    /** Инициализация на старте процесса (хост даёт Application). Падение init одного контейнера не должно валить хост — хост изолирует через runCatching. */
    fun init(app: Application)

    /** Освобождение ресурсов (потоки, слушатели). Вызывается хостом редко; должен быть идемпотентным. */
    fun release()
}

/**
 * Единица функциональности в реестре. Ключ по умолчанию — простое имя класса:
 * один контейнер публикует одну capability каждого типа, иначе переопредели key.
 */
interface Capability {
    val key: String
        get() = this::class.java.simpleName
}

/**
 * Пункт навигации (вкладка/раздел главного экрана).
 * iconKey — строковый ключ, иконку мапит ХОСТ (контракты без compose):
 * "messages", "music", "video", "profile", ...
 */
interface NavEntry : Capability {
    val title: String
    val iconKey: String
    /** Порядок сортировки вкладок; меньше — левее. */
    val order: Int
    /** Маршрут внутри NavHost хоста, который контейнер сам регистрирует при init. */
    val route: String
}

/** Собственный раздел настроек контейнера (хост агрегирует их в один экран). */
interface SettingsSection : Capability {
    val title: String
    val order: Int
    val route: String
}

/** Права, которые контейнер запрашивает при включении функциональности. */
interface PermissionNeeds : Capability {
    val permissions: List<String>
}
