# План: единая карточка поста (PostCard) для всех разделов

> Создан: 2026-10-11. Автор: MultiTool (по заданию PinoK).

## Цель
Одна переиспользуемая карточка поста с единым контрактом, которая работает в: **ленте, стене профиля, стене сообщества, детальном экране поста, закладках**. Убирает 4 дублирующие реализации.

## Требования пользователя (все разделы — единая карточка)
- лайк / комментарий / репост колбэки
- контекстное меню (⋮)
- названия (имя автора / название группы)
- кликабельность названия группы
- аватарка (группы или пользователя поста)
- значок «отправить в закладки / удалить из закладок» на самой карточке
- **полное время и дата** (а не только относительное)

## Что есть сейчас (факты из кода)

| Экран | Карточка | Колбэки | ⋮ меню | Закладка-иконка | Автор кликабелен | Дата |
|---|---|---|---|---|---|---|
| Лента | `FeedScreen.PostCard:2187` (pr) | like/comment/repost/video/photo/post/author/bookmark/delete/pin/edit/hide/ban/reaction/subscribe/report | **всегда** (+закладка в меню) | нет иконки (в ⋮) | да | relative |
| Профиль | `WallPostCard:2213` (pub, 5 мест) | like/reaction/comment/repost/video/photo/post/pin/delete/edit/report | условно | нет | нет | relative |
| Сообщество | `CommunityPostCard:1724` (pr) | like(**ownerId/postId/isLiked**)/comment/video/photo/post/repost/actions | только isManager | нет | нет | absolute |
| Закладки | `BookmarkPostCard:726` (pr) | click/remove (+корзина) | нет | **корзина** | нет | formatDate |
| Дет.пост | inline-вёрстка `PostDetailScreen:670-854` | — | нет | нет | только группа | — |

## Проблемы для унификации
1. **Разные сигнатуры like/comment**: лента/профиль передают `(Post)->Unit`, сообщество — `(ownerId, postId, isLiked)`. Нужен единый контракт.
2. **Дубль `VideoThumbnail`** ×3 (Feed/Community/Profile) — вынести в `ui/components`.
3. **Репост** рисуется 3 способами (inline / `RepostBlock` / строка текста) — единый `RepostBlock`/`RepostCard`.
4. **Нет закладки-переключателя** в стенах/сообществах/детальном экране (только лента через ⋮).
5. **Разные хедеры** — у ленты кликабельные имя+аватар+подписка+подпись, у остальных статично.
6. **ActionIcon** дублируется ×3 (с разным long-press поведением).

## Целевая архитектура
```
ui/components/PostCard.kt            ← единая карточка
ui/components/AttachmentRenderer.kt  ← единый рендер вложений (фото/видео/аудio/репост)
ui/components/VideoThumbnail.kt      ← вынести из дублей
ui/components/RepostBlock.kt         ← вынести из ProfileScreen
ui/components/ActionIcon.kt          ← единая action-иконка (like/comment/repost/views)
```

## Единая сигнатура PostCard
```kotlin
@Composable fun PostCard(
    post: Post,
    authorName: String,
    authorPhoto: String?,
    authorIsGroup: Boolean,          // по post.fromId<0
    signerName: String? = null,
    // дата (новое требование):
    showFullDate: Boolean = false,
    // навигация
    onAuthorClick: () -> Unit = {},
    onPostClick: (Post) -> Unit = {},
    onVideoClick: (Video) -> Unit = {},
    onPhotoClick: (List<String>, Int) -> Unit = { _, _ -> },
    // интерактив
    onLikeToggle: (Post) -> Unit = {},
    onCommentClick: (Post) -> Unit = {},
    onRepostClick: (Post) -> Unit = {},
    onReaction: ((Post, ReactionEntry) -> Unit)? = null,
    // ⋮ меню
    menuItems: List<PostMenuItem> = emptyList(),  // закладка/ред/закрепить/удалить/скрыть/...
    // закладка-иконка на карточке (новое требование)
    isBookmarked: Boolean? = null,     // null = не показывать иконку
    onToggleBookmark: (() -> Unit)? = null,
    // настройки отрисовки
    textMaxLines: Int = 10,
    carouselEnabled: Boolean = true,
    showViews: Boolean = true,
)
```

## Что показывает карточка
- **Аватар** — группы или пользователя поста (автора), по `fromId`/`ownerId` (группа при `<0`).
- **Название группы** — кликабельно; имя пользователя — кликабельно; для групп бейдж `verified` (как лента).
- **Полное время и дата** — флаг `showFullDate=true` → формат «31 дек 2026, 14:05». В закладках по умолчанию.
- **Текст** — разворачивание при overflow; тап: свёрнутый→развернуть, развёрнутый→открыть пост.
- **Вложения** — `AttachmentRenderer` (фото-карусель, видео, аудио, плейлист, ссылка, опрос, документ) + репост.
- **Счётчики** — лайк/комментарий/репост/просмотры через `ActionIcon`; длинный пресс на лайк → пикер реакций.
- **⋮ меню** — динамический список пунктов (в т.ч. «В закладки/Убрать из закладок»).
- **Иконка закладки** — видимая на карточке (`isBookmarked != null`); для BookmarksScreen — «удалить из закладок» (заполненная/корзина).

## Порядок работ (этапы)
1. **Вынести дубли** (низкий риск): `VideoThumbnail`, `ActionIcon`, `RepostBlock` → `ui/components`, заменить внутренние ссылки. Проверить сборку.
2. **Создать `AttachmentRenderer`** — собрать фото/видео/аудио/плейлист/репост-рендеринг из 4 карточек.
3. **Создать единый `PostCard`** с сигнатурами из плана; включить полное время/дату и иконку-закладку.
4. **Переключить экраны** по одному: CommunityScreen → ProfileScreen → FeedScreen → PostDetailScreen → BookmarksScreen. Каждый шаг — сборка и проверка.
5. **Унифицировать API закладки**: единый `onToggleBookmark` на всех экранах через `fave.addPost/removePost` (VKApiClient:6634/6675).

## Риски и решения
- **Риск регрессии ленты** — самая нагруженная. Делать ленту последней, сравнивать поведение.
- **Разные контракты like/comment** — переход на единый `(Post)->Unit`; на точке вызова в CommunityScreen адаптировать.
- **Сборка на пользователе** — каждый этап прошу пересобрать и прислать лог.
- Попутно чинить warning `VKApiClient.kt` (уже исправлен `Condition is always true`).

## Справочные точки в коде
- `VKApiClient.faveGet` (fave.get): резолв автора поста из `response.profiles/groups` — уже сделано (коммит `c7f3a60`).
- `VKApiClient.faveAdd(type="post")` :6634, `faveRemove(type="post")` :6675, `bookmarkRemove` :6736.
- `Post.isFavorite`/`isFavoriteBool` (Models.kt:90), парсится в VKApiClient:12356, 12530, 16176.
- `fave.addPost/removePost` — API закладки поста.