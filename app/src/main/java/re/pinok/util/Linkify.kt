package re.pinok.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration

// ════════════════════════════════════════════════════════════════════════════
// Fix #204: Утилита парсинга VK inline-ссылок + обычных URL в кликабельный текст.
//
// VK использует inline-токен [#alias|<display>|<url>] для ссылок внутри текста
// постов/сообщений (например в ленте: "[#alias|t.me/club_arduino|https://t.me/...]"
// должен отображаться как кликабельный "t.me/club_arduino" со спрятанной ссылкой).
// Также поддерживается 2-частный формат [#<display>|<url>] (без "alias|").
//
// Параллельно ловим обычные http(s)://… и www.… URL (как делал старый linkifyMessageText
// в ChatDetailScreen). Замыкающая пунктуация (.,)!?;:'") отрезается от URL, но
// остаётся в тексте.
//
// Применяется в: FeedScreen (лента), PostDetailScreen (просмотр поста),
// ChatDetailScreen (сообщения) — единое поведение (вариант C по выбору юзера).
// ════════════════════════════════════════════════════════════════════════════

/**
 * Regex для VK inline-ссылки `[#alias|display|url]` или `[#display|url]`.
 *
 * Группы:
 *  1) display — отображаемый текст (то, что видит юзер)
 *  2) url     — реальная ссылка (то, что откроется)
 *
 * `alias|` — опциональный маркер (VK использует его для inline-ссылок).
 * Без него токен всё ещё валиден (`[#текст|url]`).
 *
 * Не допускаем `]` и `|` внутри display, и `]` внутри url — иначе жадный
 * match съест соседние токены в одной строке.
 */
private val VK_INLINE_LINK_REGEX = Regex(
    "\\[#(?:alias\\|)?([^\\]|]+)\\|([^\\]]+)\\]",
)

/**
 * Regex для обычных URL: `http(s)://…` и `www.…`.
 * Без запятых/точек с запятой/скобок в составе (отрезаются как пунктуация).
 * Дефис, точка, подчёркивание, проценты, параметры запроса — включены.
 */
private val PLAIN_URL_REGEX = Regex(
    "(?:https?://|www\\.)[A-Za-z0-9._~:/?#@!\\$&'()*+=%\\-]+",
    RegexOption.IGNORE_CASE,
)

/**
 * Один match (либо #alias-токен, либо обычный URL) с уже вычисленным
 * display-текстом и финальным url.
 */
private data class LinkMatch(
    val start: Int,
    val end: Int,        // эксклюзивный (как range.last+1), без замыкающей пунктуации
    val display: String,  // что показать юзеру
    val url: String,      // куда вести
)

/**
 * Превращает текст с VK inline-ссылками и обычными URL в [AnnotatedString]
 * с кликабельными [LinkAnnotation.Clickable]. Клик вызывает [onUrlClick].
 *
 * @param linkColor цвет ссылок (на исходящем bubble — onPrimary, на входящем — primary)
 * @param onUrlClick обработчик клика (внутренний/внешний браузер по настройке)
 */
fun linkifyVkText(
    text: String,
    linkColor: Color,
    onUrlClick: (String) -> Unit,
): AnnotatedString = buildAnnotatedString {
    val matches = mutableListOf<LinkMatch>()

    // 1. VK inline-ссылки [#alias|display|url]
    for (m in VK_INLINE_LINK_REGEX.findAll(text)) {
        val display = m.groupValues[1]
        val url = m.groupValues[2]
        if (display.isBlank() || url.isBlank()) continue
        matches += LinkMatch(
            start = m.range.first,
            end = m.range.last + 1,
            display = display,
            url = url,
        )
    }

    // 2. Обычные URL (http(s)://, www.) — с отрезанием замыкающей пунктуации
    for (m in PLAIN_URL_REGEX.findAll(text)) {
        var url = m.value
        var end = m.range.last + 1
        while (url.isNotEmpty() && url.last() in ".,)!?;:'\"") {
            url = url.dropLast(1)
            end--
        }
        if (url.isBlank()) continue
        val display = text.substring(m.range.first, end)
        val finalUrl = if (url.startsWith("www.", ignoreCase = true)) "https://$url" else url
        matches += LinkMatch(
            start = m.range.first,
            end = end,
            display = display,
            url = finalUrl,
        )
    }

    // 3. Сортируем по позиции и отбрасываем перекрытия (inline-токен может содержать
    //    URL внутри display — тогда优先итет у токена, т.к. он добавлен первым и start меньше
    //    при равенстве start). Простой approach: отсортировать по start, затем skip'ать
    //    match'и чей start < последний добавленный end.
    matches.sortBy { it.start }
    val accepted = mutableListOf<LinkMatch>()
    var lastEnd = 0
    for (m in matches) {
        if (m.start >= lastEnd) {
            accepted += m
            lastEnd = m.end
        }
    }

    // 4. Сборка AnnotatedString: обычный текст между ссылками + addLink + append display.
    var cursor = 0
    for ((idx, m) in accepted.withIndex()) {
        if (m.start > cursor) append(text.substring(cursor, m.start))
        val tag = "vklink_$idx"
        addLink(
            LinkAnnotation.Clickable(
                tag = tag,
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                    ),
                ),
                linkInteractionListener = LinkInteractionListener { onUrlClick(m.url) },
            ),
            start = m.start,
            end = m.end,
        )
        append(m.display)
        cursor = m.end
    }
    if (cursor < text.length) append(text.substring(cursor))
}

// ─── Board (обсуждения): упоминания [id|Имя] — B-FIX-7 / AUDIT #26 ───────────

/**
 * Regex для VK mention-формата в комментариях обсуждений:
 *  - `[id123|Иван]`      → пользователь (id положительный)
 *  - `[club456|Имя группы]` / `[public789|Имя]` → сообщество (id отрицательный)
 *  - `[123|Имя]`         → голый id (VK иногда отдаёт без префикса)
 *
 * Группа 1 = префикс (`id`/`club`/`public` или пусто), группа 2 = числовой id,
 * группа 3 = отображаемое имя. `|` и `]` не допускаются внутри имени (иначе жадный
 * match съест соседние токены).
 *
 * ВНИМАНИЕ: НЕ путать с inline-ссылками `[#alias|display|url]` (см.
 * [VK_INLINE_LINK_REGEX]) — это другой формат, у него `#`-префикс и 3 части.
 * Mention'ы указывают на юзеров/сообщества, а не на URL — поэтому обрабатываются
 * отдельным regex'ом и отдельным callback'ом [onUserClick].
 */
private val VK_MENTION_REGEX = Regex(
    "\\[(id|club|public)?(\\d+)\\|([^\\]]+)\\]",
)

/**
 * B-FIX-7 (AUDIT #26): Превращает текст комментария с VK mention'ами
 * `[id123|Иван]` / `[club456|Имя]` в [AnnotatedString] с кликабельными span'ами.
 *
 * Каждое упоминание рендерится именем (group 3) цветом [mentionColor] (accent),
 * как [LinkAnnotation.Clickable] с [LinkInteractionListener] → [onUserClick]:
 *  - `id`/без префикса → положительный id (пользователь)
 *  - `club`/`public`   → отрицательный id (сообщество, как VK API from_id<0)
 *
 * Остальной текст остаётся обычным. Клик по упоминанию вызывает [onUserClick](id).
 * Используется [LinkAnnotation.Clickable] (НЕ deprecated [ClickableText]) —
 * как в [linkifyVkText], рендерится обычным `Text`.
 *
 * @param text         сырой текст комментария (возможно уже обрезанный showmoretext)
 * @param mentionColor цвет упоминаний (BoardAccent #447bba по снапшоту VK web)
 * @param onUserClick  callback клика по упоминанию (id>0 — юзер, id<0 — сообщество)
 * @return AnnotatedString с кликабельными mention-ссылками.
 */
fun buildBoardCommentText(
    text: String,
    mentionColor: Color,
    onUserClick: (Long) -> Unit,
): AnnotatedString = buildAnnotatedString {
    var lastEnd = 0
    for (match in VK_MENTION_REGEX.findAll(text)) {
        val prefix = match.groupValues[1]
        val rawId = match.groupValues[2].toLongOrNull() ?: 0L
        val name = match.groupValues[3]
        if (name.isBlank() || rawId == 0L) continue
        // club/public → отрицательный id (сообщество), иначе — пользователь.
        val entityId = if (prefix == "club" || prefix == "public") -rawId else rawId
        if (match.range.first > lastEnd) {
            append(text.substring(lastEnd, match.range.first))
        }
        val nameStart = length
        addLink(
            LinkAnnotation.Clickable(
                tag = "user_$entityId",
                styles = TextLinkStyles(
                    style = SpanStyle(color = mentionColor),
                ),
                linkInteractionListener = LinkInteractionListener { onUserClick(entityId) },
            ),
            start = nameStart,
            end = nameStart + name.length,
        )
        append(name)
        lastEnd = match.range.last + 1
    }
    if (lastEnd < text.length) append(text.substring(lastEnd))
}

/**
 * Fix #204: Открывает URL внешним браузером (ACTION_VIEW) с нормализацией.
 *
 * Переиспользуется лентой (FeedScreen), просмотром поста (PostDetailScreen),
 * репостами (RepostBlock), link-вложениями (PostAttachmentRenderer),
 * обсуждениями (BoardTopicScreen) и чатом (ChatDetailScreen).
 *
 * Логика нормализации взята из Fix #51-A (LinkCard): добавляем https:// если
 * нет scheme, валидируем scheme+host, проверяем resolveActivity.
 *
 * INTERNAL-LINK-ROUTING (запрос пользователя: «все ссылки VK.RU должны
 * переходить внутри приложения»): если host URL принадлежит VK-семейству
 * (vk.com / m.vk.com / vk.ru / m.vk.ru / vkvideo.ru / vk.me), ссылка
 * открывается ВНУТРИ PinoK через [openVkUrlInternal] (ACTION_VIEW intent,
 * направленный явно на MainActivity → handleDeepLinkIntent →
 * VkUrlDeepLinker.deepLinkFromUrl → нативная навигация OpenPost/OpenPhoto/
 * OpenVideo/OpenUser/OpenCommunity/InternalBrowser). Не-VK ссылки — внешний
 * браузер (как раньше).
 */
fun openUrlExternal(ctx: Context, rawUrl: String) {
    val url = rawUrl.trim()
    if (url.isEmpty()) {
        Toast.makeText(ctx, "Ссылка недоступна", Toast.LENGTH_SHORT).show()
        return
    }
    val normalized = if (url.contains("://")) url else "https://$url"
    val uri = try {
        Uri.parse(normalized)
    } catch (e: Exception) {
        AppLog.w("Linkify", "invalid url=$url", e)
        Toast.makeText(ctx, "Некорректная ссылка", Toast.LENGTH_SHORT).show()
        return
    }
    if (uri.scheme == null || uri.host == null) {
        AppLog.w("Linkify", "no scheme/host in url=$normalized")
        Toast.makeText(ctx, "Некорректная ссылка", Toast.LENGTH_SHORT).show()
        return
    }

    // INTERNAL-LINK-ROUTING: VK-ссылки открываем ВНУТРИ приложения.
    if (isVkUrl(uri)) {
        openVkUrlInternal(ctx, normalized)
        return
    }

    // Не-VK ссылка → внешний браузер (как раньше).
    val intent = Intent(Intent.ACTION_VIEW, uri)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (intent.resolveActivity(ctx.packageManager) == null) {
        AppLog.w("Linkify", "no app to handle url=$normalized")
        Toast.makeText(ctx, "Нет приложения для открытия ссылки", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        ctx.startActivity(intent)
        AppLog.i("Linkify", "opened url=$normalized")
    } catch (e: Exception) {
        AppLog.w("Linkify", "failed to open url=$normalized", e)
        Toast.makeText(ctx, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
    }
}

/**
 * INTERNAL-LINK-ROUTING: host-множество VK-доменов.
 *
 * Синхронизировано с intent-filter MainActivity в AndroidManifest.xml
 * (vk.com / m.vk.com / vk.ru / m.vk.ru) + добавлены vkvideo.ru и vk.me
 * (manifest intent-filter их не ловит, но openVkUrlInternal использует
 * setClass → MainActivity примет ACTION_VIEW с любым VK host).
 */
private val VK_DOMAINS = setOf(
    "vk.com",
    "m.vk.com",
    "vk.ru",
    "m.vk.ru",
    "vkvideo.ru",
    "vk.me",
)

/**
 * INTERNAL-LINK-ROUTING: проверка, является ли host URL VK-доменом.
 *
 * Сравнение по ТОЧНОМУ host (с нормализацией: lowercase + срез «www.»),
 * чтобы не срабатывал на look-alike домены типа «notvk.ru»,
 * «vk.com.evil.example» или query-параметре «?ref=vk.com».
 */
private fun isVkUrl(uri: Uri): Boolean {
    val host = uri.host?.lowercase()?.removePrefix("www.") ?: return false
    return host in VK_DOMAINS
}

/**
 * INTERNAL-LINK-ROUTING: открыть VK-ссылку ВНУТРИ приложения.
 *
 * Строит ACTION_VIEW intent с URI=url, направленный ЯВНО на MainActivity
 * через [Intent.setClass] — это обходит системный chooser (пользователю НЕ
 * предлагают выбор между PinoK / VK / браузером). MainActivity.handleDeepLinkIntent
 * перехватит ACTION_VIEW → [re.pinok.realtime.VkUrlDeepLinker.deepLinkFromUrl]
 * → навигация к нативному экрану (OpenPost/OpenPhoto/OpenVideo/OpenUser/
 * OpenCommunity/InternalBrowser).
 *
 * Флаги:
 *  - FLAG_ACTIVITY_NEW_TASK — обязательно при startActivity из не-Activity
 *    контекста (Application / Service / Compose `ctx` может быть Application).
 *  - FLAG_ACTIVITY_CLEAR_TOP — если MainActivity уже в стеке, очищаем верхушку
 *    и доставляем новый intent через onNewIntent → handleDeepLinkIntent.
 *
 * Fallback: если MainActivity почему-то не смогла принять intent (например,
 * process crash / security exception) — повторяем как обычный ACTION_VIEW
 * без setClass (системный chooser / внешний браузер).
 */
private fun openVkUrlInternal(ctx: Context, url: String) {
    AppLog.i("Linkify", "INTERNAL-LINK-ROUTING: opening VK url internally: $url")
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        setClass(ctx, re.pinok.ui.MainActivity::class.java)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    try {
        ctx.startActivity(intent)
        AppLog.i("Linkify", "INTERNAL-LINK-ROUTING: routed to MainActivity: $url")
    } catch (e: Exception) {
        AppLog.w(
            "Linkify",
            "INTERNAL-LINK-ROUTING: failed to open internally, fallback to external: $url",
            e,
        )
        // Fallback: внешний браузер если внутренний не сработал.
        val extIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(extIntent)
        } catch (e2: Exception) {
            AppLog.w(
                "Linkify",
                "INTERNAL-LINK-ROUTING: external fallback also failed: $url",
                e2,
            )
            Toast.makeText(ctx, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
        }
    }
}

