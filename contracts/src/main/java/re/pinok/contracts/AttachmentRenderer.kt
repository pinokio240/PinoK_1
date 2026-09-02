package re.pinok.contracts

/**
 * #ARCH-CONTAINERS (Этап 1.4, задел под 1.5): рендер вложений силами контейнера
 * (фото/видео/аудио). Хост спрашивает canHandle по mime/типу.
 *
 * Поток (чат-пайплайн хоста, ChatDetailScreen):
 *  1. хост собирает AttachmentRenderer всех зарегистрированных контейнеров
 *     (`ContainerRegistry.find<AttachmentRenderer>()`) и спрашивает
 *     `canHandle(mime, kind)` у каждого;
 *  2. нашёлся желающий → рендер ДЕЛЕГИРУЕТСЯ контейнеру: хост мапит
 *     [rendererKey] на свой компосабл (тот же host-маппинг, что NavEntry.route
 *     → destination). Контейнер compose-типов не знает — контракты без androidx;
 *  3. не нашёлся (или rendererKey хосту неизвестен) → fallback хоста по типу
 *     вложения (graceful-деградация, ядро не зависит от контейнера).
 *
 * ФАКТ (Этап 1.5-а, первый потребитель — `:feature:photos`): фото-вложения
 * чата делегируются рендереру "photos_inline" (хост спрашивает canHandle
 * с mime-семейством image-звёздочка и kind="photo" — mime у VK-фото
 * отсутствует); fallback без контейнера — заглушка хоста
 * (PhotoAttachmentsStub: тап → PhotoViewer, сохранение в просмотрщике).
 * Видео/doc/audio-ветки на 1.5-а остались встроенными в хост и реестр
 * не спрашивают — kind="video" контейнером НЕ публикуется (false claim
 * дал бы регресс на заглушку).
 */
interface AttachmentRenderer : Capability {
    /** Может ли контейнер отрендерить это вложение. */
    fun canHandle(mimeType: String, kind: String): Boolean

    /** Стабильный ключ для host-маппинга на компосабл (как NavEntry.route). */
    val rendererKey: String

    /** Порядок среди рендереров, если претендентов несколько (меньше — раньше). */
    val order: Int
}
