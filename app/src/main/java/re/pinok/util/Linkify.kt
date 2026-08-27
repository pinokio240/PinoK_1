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

/**
 * Fix #204: Открывает URL внешним браузером (ACTION_VIEW) с нормализацией.
 *
 * Переиспользуется лентой (FeedScreen) и просмотром поста (PostDetailScreen),
 * где нет настройки внутреннего браузера. ChatDetailScreen использует свой
 * [onUrlClick] с поддержкой openLinksInInternalBrowser.
 *
 * Логика нормализации взята из Fix #51-A (LinkCard): добавляем https:// если
 * нет scheme, валидируем scheme+host, проверяем resolveActivity.
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

