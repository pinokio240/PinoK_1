package re.pinok.feature.photos

import android.app.Application
import re.pinok.contracts.AppContainer
import re.pinok.contracts.AttachmentRenderer
import re.pinok.contracts.Capability
import re.pinok.contracts.NavEntry
import re.pinok.util.AppLog

/**
 * #ARCH-CONTAINERS (Этап 1.5-а): контейнер «Фото».
 *
 * Публикует 2 capability (реестр → хост):
 *  1. [PhotosNavEntry]            — кнопка «Фото» в боковой панели (route "photos" —
 *     совпадает с бывшим ядерным пунктом drawer: Screen.Photos; NavHost-destination
 *     и экран PhotosScreen остаются в хосте :app — блокер SovaApp/apiClient,
 *     переезд экрана = data-слой, см. контейнеры.план.md);
 *  2. [PhotosAttachmentRenderer]  — рендер фото-вложений чата (rendererKey
 *     "photos_inline"): хост мапит ключ на компосабл [PhotosInlineRenderer]
 *     (этот же модуль) и передаёт данные/колбэки; compose-типов контейнер
 *     в реестре не публикует (контракты без androidx).
 *
 * РЕШЕНИЯ ПО ФАКТАМ (разведка Этапа 1.5-а, см. worklog Task 12):
 *  - NavEntry публикуется: раздел «Фото» в :app существует (Screen.Photos +
 *    PhotosScreen + drawer-пункт + NavHost-destination) — до 1.5-а он был
 *    ядерным хардкодом; после — контейнерный (сняли контейнер → пункта нет).
 *  - SettingsSection НЕ публикуется: фото-вкладки в SettingsScreen нет
 *    (StickerPhotoScaleRow — строка на ядерной вкладке чата, не раздел).
 *  - PermissionNeeds НЕ публикуется: раздел работает через VK API (сеть),
 *    сохранение фото делает хостовый PhotoViewer через ImageSaver (:core:media)
 *    — отдельных dangerous-разрешений контейнеру не нужно.
 *
 * init/release — минимальные/идемпотентные: рендер и экран живут циклом
 * композиции хоста, состояние контейнеру не нужно.
 */
class PhotosContainer : AppContainer {

    override val id: String = "photos"

    override fun capabilities(): List<Capability> = listOf(
        PhotosNavEntry,
        PhotosAttachmentRenderer,
    )

    override fun init(app: Application) {
        // Минимум на 1.5-а: состояния у контейнера нет (рендер — чистая функция
        // данных хоста; экран раздела — PhotosScreen в :app). Нечего инициализировать.
        AppLog.i("PhotosContainer", "init: контейнер фото зарегистрирован (id=$id)")
    }

    override fun release() {
        // Идемпотентный no-op: ресурсов контейнер не держит.
        AppLog.i("PhotosContainer", "release: no-op (идемпотентно)")
    }
}

/**
 * Кнопка «Фото» в drawer. route = "photos" — прежний route ядерного пункта
 * (Screen.Photos); host-маппинг hostDestinationForRoute("photos") → Screen.Photos,
 * destination в NavHost хоста остаётся. order=20 — после «Звонки» (calls order=10).
 */
private object PhotosNavEntry : NavEntry {
    override val title: String = "Фото"
    override val iconKey: String = "photos"
    /** После «Звонки» (10); хост сортирует контейнерные пункты по order. */
    override val order: Int = 20
    override val route: String = "photos"
}

/**
 * Рендер фото-вложений чата (контракт [AttachmentRenderer], без androidx/compose).
 *
 * mime-таблица по ФАКТАМ хоста (разведка 1.5-а): хост-хук в ChatDetailScreen
 * спрашивает canHandle для mime-семейства image-звёздочки (image + слэш-звёздочка)
 * и kind="photo" — mime у VK-фото в messages.get отсутствует, передаётся
 * семейство. Дополнительно принимаем конкретные image-типы (задел для будущих
 * вызовов с реальным mime). ВНИМАНИЕ: литерал image-слэш-звёздочка НЕ писать
 * в block-комментариях — он открывает вложенный комментарий Kotlin (ловушка,
 * пойманная сканером скобок). kind="video" — НЕ берём: видео-ветка хоста
 * (VideoAttachmentCard, модель Video) на 1.5-а осталась в :app, хост video-хук
 * не вызывает — false claim дал бы регресс.
 */
object PhotosAttachmentRenderer : AttachmentRenderer {
    private val IMAGE_MIMES = setOf(
        "image/jpeg", "image/png", "image/webp", "image/gif", "image/heic", "image/heif",
    )

    override fun canHandle(mimeType: String, kind: String): Boolean {
        if (kind != "photo") return false
        val mime = mimeType.trim().lowercase()
        return mime == "image/*" || mime == "*/*" || mime in IMAGE_MIMES
    }

    /** Стабильный ключ для host-маппинга (ChatDetailScreen.hostRendererComposable). */
    override val rendererKey: String = "photos_inline"

    /** Первый среди рендереров (сейчас единственный). */
    override val order: Int = 10
}
