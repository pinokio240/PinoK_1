package re.pinok.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import re.pinok.data.model.Post
import re.pinok.data.model.Video
import re.pinok.ui.screens.feed.ReactionEntry
import re.pinok.ui.screens.feed.ReactionPicker
import re.pinok.util.linkifyVkText
import re.pinok.util.openUrlExternal
import re.pinok.util.toAbsoluteTime
import re.pinok.util.toRelativeTime

/**
 * POSTCARD-REFACTOR-PLAN (этап 3a): ЕДИНАЯ карточка поста для всех поверхностей —
 * лента (FeedScreen), сообщество (CommunityScreen), профиль (ProfileScreen),
 * открытый пост (PostDetailScreen), закладки (BookmarksScreen).
 *
 * Эталон — приватная [FeedScreen.PostCard] (самая богатая реализация): кликабельный
 * аватар + имя с verified-бейджем, подпись автора, кнопка подписки, динамическое
 * [⋮]-меню ([PostMenuItem]), метки «Реклама»/«Рекомендуемое»/закреп/копирайт/donut,
 * текст с linkify и разворачиванием, вложения ([PostAttachmentRenderer]), репост
 * ([RepostBlock]), action-bar со счётчиками ([ActionIcon]).
 *
 * Новое по требованиям пользователя (на этом этапе — зелёный контракт):
 *  - [showFullDate] — полное время и дата ([toAbsoluteTime]) вместо относительного
 *    ([toRelativeTime]); футер-время (как в эталоне FeedScreen).
 *  - [isBookmarked]/[onToggleBookmark] — ВИДИМАЯ иконка-закладка прямо на карточке
 *    (переключатель), а не только внутри [⋮]-меню. [isBookmarked] == null — иконку
 *    не показывать.
 *  - кликабельное название (группы и автора) через [onAuthorClick].
 *  - аватарка группы или пользователя поста ([authorPhoto]/[authorName]/[authorIsGroup]).
 *  - лайк/комментарий/репост колбэки ([onLikeToggle]/[onCommentClick]/[onRepostClick])
 *    + пикер реакций ([onReaction]) по long-press на лайке.
 *  - динамическое [⋮]-меню через [menuItems] (закладка/редактировать/закрепить/
 *    скрыть/бан/пожаловаться/удалить — наполняет вызывающий экран).
 *
 * NULL-ПОЛИТИКА: поля из :core:data опциональны по схеме Gson — проверки только
 * «if (x != null)» (БЕЗ ?./?:/!! при работе с объектами чужого модуля); любые
 * nullable-значения захватываются в локальные val ДО использования
 * (#ARCH-CONTAINERS 3.7-1, smart-cast чужого модуля невозможен).
 *
 * Вложения и репост делегируются общим компонентам [PostAttachmentRenderer] и
 * [RepostBlock]; счётчики — общему [ActionIcon] (long-press лайка → [ReactionPicker]).
 */
@Composable
fun PostCard(
    post: Post,
    authorName: String,
    authorPhoto: String?,
    authorIsGroup: Boolean,               // post.fromId < 0
    signerName: String? = null,
    // ══ новые требования пользователя ══
    showFullDate: Boolean = false,        // true → toAbsoluteTime(), false → toRelativeTime()
    isBookmarked: Boolean? = null,        // null = не показывать иконку-закладку
    onToggleBookmark: (() -> Unit)? = null,
    // подтверждённость автора (для verified-бейджа у групп; по умолчанию отключено)
    verified: Boolean = false,
    // ══ навигация ══
    onAuthorClick: () -> Unit = {},
    onPostClick: (Post) -> Unit = {},
    onVideoClick: (Video) -> Unit = {},
    onPhotoClick: (List<String>, Int) -> Unit = { _, _ -> },
    // ══ интерактив ══
    onLikeToggle: (Post) -> Unit = {},
    onCommentClick: (Post) -> Unit = {},
    onRepostClick: (Post) -> Unit = {},
    onReaction: ((Post, ReactionEntry) -> Unit)? = null,
    // ══ пост-детерминированное состояние (для Profile/WallPostCard) ══
    // Оптимистичное состояние лайка экрана (key-умозаключение уже в вызывающем).
    // null — использовать серверные post.likes. (isLiked, count)
    likesOverride: Pair<Boolean, Int>? = null,
    // Оптимистичная «моя реакция» на пост (reaction_id; 0/absent = нет).
    // null — использовать серверные post.reactions.user_reacted.
    reactionOverride: Int? = null,
    // ══ ⋮ меню ══
    menuItems: List<PostMenuItem> = emptyList(),
    showPinnedBadge: Boolean = true,
    showAdBadge: Boolean = true,
    showSubscribeButton: Boolean = false,
    subscribed: Boolean? = null,
    onSubscribe: (Post) -> Unit = {},
    // ══ настройки отрисовки ══
    textMaxLines: Int = 6,
    carouselEnabled: Boolean = true,
    showViews: Boolean = true,
    myUserId: Long = 0L,
    // Группа-источник РЕПОСТА (copy_history[0] из группы, ownerId<0).
    // Пробрасываются в RepostBlock для заголовка репоста (аватар/имя/клик в группу).
    // Резолв имени/фото/клика делает вызывающий экран (у PostCard нет карты групп).
    repostGroupName: String? = null,
    repostGroupPhoto: String? = null,
    onRepostGroupClick: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    // ——— ВРЕМЯ (футер, как в эталоне FeedScreen; формат — по showFullDate) ———
    val timeStr = if (showFullDate) post.date.toAbsoluteTime() else post.date.toRelativeTime()

    // ——— СЧЁТЧИКИ из модели (оптимистичные состояния — зона вызывающего экрана) ———
    val likeObj = post.likes
    // #PROFILE-OPTIMISTIC: likesOverride (isLiked, count) — переопределяет
    // серверные post.likes (как в WallPostCard ProfileScreen). NULL-ЯВНО.
    val lo = likesOverride
    val isLiked = if (lo != null) lo.first else (likeObj != null && likeObj.userLikes == 1)
    val likeCount = if (lo != null) lo.second else (if (likeObj != null) likeObj.count else 0)
    // #PROFILE-OPTIMISTIC: reactionOverride — «моя реакция» (для предвыделения
    // в пикере). null → серверные post.reactions.user_reacted (если >0).
    val ro = reactionOverride
    val myReactionId = if (ro != null) ro else post.reactions?.userReacted?.takeIf { it > 0 } ?: 0
    val commentObj = post.comments
    val commentCount = if (commentObj != null) commentObj.count else 0
    val repostObj = post.reposts
    val repostCount = if (repostObj != null) repostObj.count else 0
    val viewsObj = post.views
    val viewsCount = if (viewsObj != null) viewsObj.count else 0
    val photoAttachments = post.attachments
    val copyHistory = post.copyHistory
    val copyright = post.copyright
    val donut = post.donut
    val donutPlaceholder = if (post.isDonut) donut?.placeholder else null

    var showMenu by remember { mutableStateOf(false) }
    var showReactions by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ─── МЕТКИ «Реклама» / «Рекомендуемое» ───
            if (post.isAd && showAdBadge) {
                Text(
                    text = "Реклама",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
                )
            }
            val postType = post.postType
            if (postType != null && postType in listOf("suggest", "suggested")) {
                Text(
                    text = "Рекомендуемое",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = if (post.isAd && showAdBadge) 0.dp else 8.dp,
                    ),
                )
            }
            // ─── HEADER: аватар + имя (+verified/подпись) + подписка + закладка + ⋮ ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // кликабельный аватар 36dp (группа или пользователь поста)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onAuthorClick() },
                ) {
                    val photo = authorPhoto
                    if (photo != null) {
                        AsyncImage(
                            model = photo,
                            contentDescription = authorName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        // fallback-буква, если нет фото
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = authorName.take(1).uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                // имя автора (кликабельно, primary + underline) + verified + подпись
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = authorName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable { onAuthorClick() },
                        )
                        // verified-бейдж (для подтверждённых групп)
                        if (authorIsGroup && verified) {
                            Icon(
                                Icons.Outlined.Verified,
                                contentDescription = "Верифицировано",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    val signer = signerName
                    if (signer != null) {
                        Text(
                            text = signer,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // кнопка подписки (чип-стиль, как в эталоне FeedScreen)
                if (showSubscribeButton && post.fromId != 0L && post.fromId != myUserId) {
                    val sub = subscribed
                    Text(
                        text = if (sub == true) "Отписаться" else "Подписаться",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (sub == true) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (sub == true) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            )
                            .clickable { onSubscribe(post) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                // иконка-закладка прямо на карточке (новое требование)
                val bookmarked = isBookmarked
                if (bookmarked != null) {
                    IconButton(onClick = { onToggleBookmark?.invoke() }) {
                        Icon(
                            imageVector = if (bookmarked) Icons.Outlined.Bookmark
                            else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (bookmarked) "Убрать из закладок" else "В закладки",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // ⋮ контекстное меню
                if (menuItems.isNotEmpty()) {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                Icons.Outlined.MoreHoriz,
                                contentDescription = "Действия",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            menuItems.forEach { item ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = item.label,
                                            // #DARK-THEME-FIX: обычные пункты меню красить в
                                            // onSurface (читаемо в тёмной теме), а не в
                                            // Color.Unspecified (мог выродиться в нечитаемый).
                                            color = if (item.destructive) Color(0xFFE53935)
                                            else MaterialTheme.colorScheme.onSurface,
                                        )
                                    },
                                    leadingIcon = {
                                        val icon = item.icon
                                        if (icon != null) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = if (item.destructive) Color(0xFFE53935)
                                                else MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    },
                                    onClick = {
                                        showMenu = false
                                        item.onClick()
                                    },
                                )
                            }
                        }
                    }
                }
            }
            // индикатор закреплённого поста
            if (post.isPinnedBool && showPinnedBadge) {
                Text(
                    text = "Закреплённый пост",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp),
                )
            }
            // копирайт-источник
            val copyrightName = copyright?.name
            if (copyrightName != null) {
                Text(
                    text = copyrightName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // donut-заглушка
            if (donutPlaceholder != null) {
                Text(
                    text = donutPlaceholder,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // ─── ТЕКСТ с linkify и разворачиванием ───
            if (post.text.isNotBlank()) {
                var expanded by remember { mutableStateOf(false) }
                val textLines = post.text.lines().size
                val needsExpand = textLines > textMaxLines
                val linkColor = MaterialTheme.colorScheme.primary
                val fullText = linkifyVkText(
                    text = post.text,
                    linkColor = linkColor,
                    onUrlClick = { url -> openUrlExternal(ctx, url) },
                )
                val collapsedText = if (needsExpand) {
                    linkifyVkText(
                        text = post.text.lines().take(textMaxLines).joinToString("\n"),
                        linkColor = linkColor,
                        onUrlClick = { url -> openUrlExternal(ctx, url) },
                    )
                } else fullText
                Text(
                    text = if (needsExpand && !expanded) collapsedText else fullText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPostClick(post) }
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                )
                if (needsExpand && !expanded) {
                    Text(
                        text = "Показать ещё",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
                            .clickable { expanded = true },
                    )
                }
            }
            // ─── ВЛОЖЕНИЯ (фото/видео/аудио/плейлисты/ссылки/опрос/документ) ───
            val attachments = photoAttachments
            if (attachments != null) {
                PostAttachmentRenderer(
                    attachments = attachments,
                    onPhotoClick = onPhotoClick,
                    onVideoClick = onVideoClick,
                    onPostClick = onPostClick,
                    carouselEnabled = carouselEnabled,
                    showVideoTitle = true,
                    titleExpandable = false,
                    renderLinkPollDoc = true,
                    consumeClicks = false,
                    clipThumbnailEnabled = false,
                    post = post,
                )
            }
            // ─── РЕПОСТ (copy_history[0]) ───
            if (!copyHistory.isNullOrEmpty()) {
                val repost = copyHistory.first()
                RepostBlock(
                    repost = repost,
                    onPhotoClick = onPhotoClick,
                    onVideoClick = onVideoClick,
                    onPostClick = onPostClick,
                    carouselEnabled = carouselEnabled,
                    groupName = repostGroupName,
                    groupPhoto = repostGroupPhoto,
                    onGroupClick = onRepostGroupClick,
                )
            }
            // ─── ACTION BAR: лайк / комментарий / репост / время (+ views) ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box {
                    ActionIcon(
                        icon = if (isLiked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        count = likeCount,
                        tint = if (isLiked) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { onLikeToggle(post) },
                        onLongClick = { if (onReaction != null) showReactions = true },
                    )
                    if (showReactions) {
                        Box(
                            modifier = Modifier
                                .offset(y = (-48).dp)
                                .zIndex(10f),
                        ) {
                            ReactionPicker(
                                onDismiss = { showReactions = false },
                                onSelect = { reaction ->
                                    onReaction?.invoke(post, reaction)
                                },
                                // #PROFILE-OPTIMISTIC: предвыделение «моей» реакции
                                // (reaction_id из reactionOverride или post.reactions).
                                selectedReactionId = myReactionId,
                            )
                        }
                    }
                }
                ActionIcon(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    count = commentCount,
                    onClick = { onCommentClick(post) },
                )
                ActionIcon(
                    icon = Icons.Outlined.Repeat,
                    count = repostCount,
                    onClick = { onRepostClick(post) },
                )
                // просмотры (view count) — опционально (showViews)
                if (showViews) {
                    ActionIcon(
                        icon = Icons.Outlined.Visibility,
                        count = viewsCount,
                        onClick = null,
                    )
                }
                // время в футере (формат — full/relative по showFullDate)
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * POSTCARD-REFACTOR-PLAN (этап 3a): пункт динамического [⋮]-меню карточки поста.
 *
 * Вызывающий экран сам решает, какие пункты разрешены (по правам поста) и наполняет
 * [PostCard.menuItems]: закладка, редактировать, закрепить/открепить, скрыть из ленты,
 * не показывать от автора (бан), пожаловаться, удалить. Продуктовые гейты (только на
 * чужих записях и т.п.) — тоже зона вызывающего экрана.
 *
 * @param label       текст пункта (например, «В закладки», «Удалить пост»).
 * @param icon        опциональная leading-иконка (переключатель в [⋮]-меню эталона).
 * @param onClick     действие при выборе пункта (вызывающий экран закрывает меню сам).
 * @param destructive признак деструктивного действия — текст/иконка красным (как
 *                    «Удалить пост» в эталоне FeedScreen).
 */
data class PostMenuItem(
    val label: String,
    val icon: ImageVector? = null,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
)