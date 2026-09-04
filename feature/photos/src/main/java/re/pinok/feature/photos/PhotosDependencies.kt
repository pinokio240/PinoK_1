package re.pinok.feature.photos

import androidx.compose.runtime.staticCompositionLocalOf
import re.pinok.data.model.Album
import re.pinok.data.model.PhotoItem

/**
 * #ARCH-CONTAINERS Этап 3.7-1 (2026-09-03): DI-контракт контейнера фото
 * (канон контейнеры.план.md §3.2, Этап Б). Провайдер — SovaApp (:app),
 * CompositionLocal ставится в MainActivity.setContent рядом с LocalCallsDeps.
 *
 * ТИПОВАЯ ПОЛИТИКА: :feature:photos не может видеть :app-типы — цикл
 * :app -> :feature:photos -> :app запрещён Gradle. Поэтому:
 *  - Album/PhotoItem — РЕАЛЬНЫЕ типы: пакет re.pinok.data.model целиком
 *    перенесён в :core:data (git mv, пакет сохранён; прецедент UserProfile
 *    e64edc1b/Task 22, механизм подтверждён зелёной сборкой 6923ea6b);
 *  - VKApiClient (14k+ строк, импортирует re.pinok.SovaApp — перенос
 *    невозможен) — фасад PhotosApi ниже: VKApiClient реализует его
 *    override-маркерами, ТЕЛА МЕТОДОВ НЕ ТРОГАЮТСЯ, рантайм-объект ТОТ ЖЕ
 *    (фасад — «очки», не обёртка; инвариант И4 канона §3.5);
 *  - census вызовов PhotosScreen (84/91/107/239/251/267/292/459/461):
 *    photosGetAlbums, photosGet, lastApiError, likesAdd, likesDelete —
 *    100% членов PhotosApi, ничего лишнего.
 *
 * ДЕФОЛТЫ АРГУМЕНТОВ — ТОЛЬКО ЗДЕСЬ, В ИНТЕРФЕЙСЕ (урок 197819a6/6923ea6b:
 * override объявлять дефолты НЕ МОЖЕТ). Значения = прежним дефолтам
 * VKApiClient, поэтому call-site'ы ядра через конкретный тип наследуют те
 * же дефолты и НЕ редактируются; вызовы экрана — с явными именованными
 * аргументами там, где это было и раньше.
 */
interface PhotosApi {

    /** VK photos.getAlbums — список альбомов владельца. */
    suspend fun photosGetAlbums(ownerId: Long? = null): List<Album>

    /**
     * VK photos.get — фото альбома с пагинацией (Fix #83).
     * Дефолты = прежним VKApiClient.photosGet: albumId="profile", count=50, offset=0.
     */
    suspend fun photosGet(
        ownerId: Long,
        albumId: String = "profile",
        count: Int = 50,
        offset: Int = 0,
    ): List<PhotoItem>

    /**
     * VK likes.add (Sprint 2, P1-2 #89: лайк фото type="photo").
     * Дефолты = прежним VKApiClient.likesAdd (§37.12 #326).
     */
    suspend fun likesAdd(
        type: String,
        ownerId: Long,
        itemId: Long,
        reactionId: Int? = null,
        accessKey: String? = null,
        trackCode: String? = null,
    ): Int

    /** VK likes.delete — дефолты = прежним VKApiClient.likesDelete. */
    suspend fun likesDelete(
        type: String,
        ownerId: Long,
        itemId: Long,
        accessKey: String? = null,
        trackCode: String? = null,
    ): Int

    /** Последняя ошибка VK API (текст для errorText экрана; null если нет). */
    val lastApiError: String?
}

/**
 * Зависимости контейнера фото. Состав НЕ сужается (директива пользователя,
 * канон §3.2): сегодня — только API-клиент; расширение — новыми членами.
 */
interface PhotosDependencies {
    val photosApi: PhotosApi
}

/**
 * Fail-fast провайдер (инвариант И5 канона §3.5): отсутствие провайдера
 * падает при композиции, НЕ глотается catch'ем (запрещённый паттерн Task 22).
 */
val LocalPhotosDeps = staticCompositionLocalOf<PhotosDependencies> {
    error("PhotosDependencies not provided")
}
