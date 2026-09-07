package re.pinok.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.api.VkMultiAccount
import re.pinok.util.AppLog
import re.pinok.util.openUrlExternal

// ═══════════════════════════════════════════════════════════
// IMP-VKID: Экран «Аккаунт VK ID» (native-first по снапшоту
// VK ID-кабинета, vkid.снапшоты.парсинг.полный.md §3/§4).
//
//   - «Аккаунты»:      account.getMulti (VKA accountGetMulti — метод
//                      подтверждён парсингом account.bundle, §2.1/§3.4);
//                      текущий помечен по is_logged_in, а если VK поле
//                      не отдал — по совпадению id с
//                      ExchangeAuthRepository.userId().
//   - «Безопасность»:  ссылочные ячейки id.vk.com/ru/manage/* через
//                      Linkify.openUrlExternal (паттерн «Статей»
//                      ProfileScreen П-6b).
//   - «VK Pay»:        одна ячейка-ссылка (контур мини-аппа, §2.3:
//                      *_go_to_miniapp, платёжных endpoint'ов 0).
//
// ЧЕСТНЫЕ ОТКЛОНЕНИЯ (no-stub, §3/§4):
//   1. Переключение аккаунтов НЕ реализовано: для второго аккаунта
//      нужен свой AuthResult (access_token + exchange_token +
//      session-куки) через auth_by_exchange_token, wire которого в
//      снапшоте не снят; ExchangeTokenStorage — плоский набор ключей
//      под ОДНУ сессию (второго слота нет). Строки аккаунтов
//      НЕКЛИКАБЕЛЬНЫ, переключение не имитируется.
//   2. «Устройства и активность» (список сессий) — только ссылка:
//      wire списка сессий в снапшоте не снят (клиентский рендер, §3.2).
//   3. Двухфакторная аутентификация — без статуса: account.getInfo в
//      бандле отсутствует (§2.1 фиксирует только account.get), поле
//      2FA-статуса в ответе API не подтверждено (§3.2 — только
//      UI-флаг «2fa») → честная ссылка без индикатора.
//   4. «Вход через VK ID (сервисы и сайты)» — только ссылка:
//      apps.getUsersConnected/secure.* в бандле НЕ подтверждены
//      (§3.3), список сервисов рисовать не на чем.
//   5. Смена пароля — только ссылка (changePasswordStart/–
//      /create-password — отдельный VK ID-флоу верификации, §3.2).
//   6. Анкета не дублируется — строка «Редактировать профиль» ведёт в
//      существующий EditProfileScreen (П-3, account.get/saveProfileInfo).
//   7. VK Pay — честная подпись «откроется в браузере»: платёжный
//      контур живёт в отдельном мини-аппе (VKWebAppOpenPayForm +
//      go_to_miniapp, §2.3/§4 п.5), deeplink/внешний браузер —
//      рекомендация §4 п.5.
//   НЕ рисуются вовсе (no-stub, §4 п.6): passkey, телеметрия, FAQ,
//   «удаление аккаунта» (wire отсутствует, §3.1).
// ═══════════════════════════════════════════════════════════

/** Управление VK ID: https://id.vk.com/ru/manage (2FA-контур кабинета). */
private const val URL_MANAGE = "https://id.vk.com/ru/manage"

/** Безопасность VK ID (пароль, устройства и активность). */
private const val URL_MANAGE_SECURITY = "https://id.vk.com/ru/manage/security"

/** «Вход через VK ID» — подключённые сервисы и сайты. */
private const val URL_MANAGE_SERVICES = "https://id.vk.com/ru/manage/services"

/** VK Pay: карты и платежи (мини-апп, открывается в браузере — §4 п.5). */
private const val URL_VKPAY = "https://id.vk.com/vkpay"

/**
 * Экран «Аккаунт VK ID»: мультипрофили (account.getMulti, справочно) +
 * ссылочные ячейки разделов кабинета VK ID (Безопасность, VK Pay).
 * Pull-to-refresh и error-стейт — паттерн BlacklistScreen §PROFILE-P4.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VkIdAccountScreen(
    onBack: () -> Unit,
    // IMP-VKID: анкета уже живёт в EditProfileScreen (П-3) — шапка экрана
    // даёт дешёвую строку-вход вместо дублирования формы.
    onOpenEditProfile: () -> Unit = {},
) {
    val app = SovaApp.get()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var accounts by remember { mutableStateOf<List<VkMultiAccount>>(emptyList()) }

    // Текущий пользователь ядра (UserProfileScreen-паттерн:
    // exchangeAuthRepository.userId()) — для метки, если VK не отдал
    // is_logged_in (гвард аккаунта = one-session storage ядра).
    val currentUserId = remember { app.exchangeAuthRepository.userId() }

    fun loadAccounts() {
        scope.launch {
            loading = true
            try {
                accounts = app.apiClient.accountGetMulti()
            } catch (e: Exception) {
                AppLog.e("VkIdAccountScreen", "loadAccounts error", e)
                accounts = emptyList()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadAccounts() }

    // Честный error-стейт (паттерн FollowersSubscriptionsScreen/BlacklistScreen):
    // accountGetMulti глотает ошибки в пустой список — если список пуст,
    // а lastApiError не пуст, показываем ошибку, а не «секцию без аккаунтов».
    val loadError = if (accounts.isEmpty() && app.apiClient.lastApiError != null) {
        app.apiClient.lastApiError
    } else {
        null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Аккаунт VK ID") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    scope.launch {
                        refreshing = true
                        try {
                            val fresh = app.apiClient.accountGetMulti()
                            accounts = fresh
                            if (fresh.isEmpty() && app.apiClient.lastApiError != null) {
                                Toast.makeText(
                                    context,
                                    "Не удалось обновить: ${app.apiClient.lastApiError}",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        } catch (e: Exception) {
                            AppLog.e("VkIdAccountScreen", "refresh error", e)
                            Toast.makeText(
                                context,
                                "Не удалось обновить: ${e.message ?: "нет ответа сервера"}",
                                Toast.LENGTH_SHORT,
                            ).show()
                        } finally {
                            refreshing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    // Шапка: анкета в существующем редакторе профиля (не дублируем).
                    item(key = "edit_profile") {
                        LinkCell(
                            icon = Icons.Filled.Edit,
                            title = "Редактировать профиль",
                            subtitle = "Имя, ник, город — нативный редактор анкеты",
                            onClick = onOpenEditProfile,
                        )
                    }

                    val error = loadError
                    if (error != null) {
                        item(key = "load_error") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    "Не удалось загрузить аккаунты: $error",
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { loadAccounts() },
                                    modifier = Modifier.heightIn(min = 44.dp),
                                ) {
                                    Text("Повторить")
                                }
                            }
                        }
                    } else if (accounts.isNotEmpty()) {
                        item(key = "accounts_header") {
                            SectionHeader(title = "Аккаунты (${accounts.size})")
                        }
                        items(accounts, key = { "account_${it.id}" }) { account ->
                            MultiAccountRow(
                                account = account,
                                isCurrent = account.isLoggedIn == true ||
                                    (account.isLoggedIn == null && account.id == currentUserId),
                            )
                        }
                        // Честное отклонение №1 — видимая подпись, а не молчание.
                        item(key = "accounts_note") {
                            Text(
                                "Переключение аккаунтов недоступно: оно требует " +
                                    "токен-exchange флоу VK ID, в клиенте хранится " +
                                    "одна сессия.",
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    item(key = "security_header") {
                        SectionHeader(title = "Безопасность")
                    }
                    // Отклонения №2/№5: список сессий и смена пароля — wire не
                    // снят, ячейки честно ведут в соответствующий раздел кабинета.
                    item(key = "cell_password") {
                        LinkCell(
                            icon = Icons.Outlined.Lock,
                            title = "Пароль",
                            subtitle = "Смена пароля — в кабинете VK ID",
                            url = URL_MANAGE_SECURITY,
                        )
                    }
                    item(key = "cell_devices") {
                        LinkCell(
                            icon = Icons.Filled.DevicesOther,
                            title = "Устройства и активность",
                            subtitle = "Список сессий недоступен нативно — откроется раздел кабинета",
                            url = URL_MANAGE_SECURITY,
                        )
                    }
                    item(key = "cell_services") {
                        LinkCell(
                            icon = Icons.Outlined.Apps,
                            title = "Вход через VK ID (сервисы и сайты)",
                            subtitle = "Список подключённых сервисов недоступен нативно — откроется раздел кабинета",
                            url = URL_MANAGE_SERVICES,
                        )
                    }
                    // Отклонение №3: статус 2FA не показываем (поле ответа не
                    // подтверждено снапшотом) — только ссылка.
                    item(key = "cell_2fa") {
                        LinkCell(
                            icon = Icons.Outlined.Shield,
                            title = "Двухфакторная аутентификация",
                            subtitle = "Статус и настройка — в кабинете VK ID",
                            url = URL_MANAGE,
                        )
                    }

                    item(key = "vkpay_header") {
                        SectionHeader(title = "VK Pay")
                    }
                    // Отклонение №7: платёжных endpoint'ов в бандле нет
                    // (go_to_miniapp) — «откроется в браузере» прямо в подписи.
                    item(key = "cell_vkpay") {
                        LinkCell(
                            icon = Icons.Outlined.CreditCard,
                            title = "Карты и платежи",
                            subtitle = "Откроется в браузере (мини-апп VK Pay)",
                            url = URL_VKPAY,
                        )
                    }
                }
            }
        }
    }
}

/** Заголовок секции (стиль HiddenSectionHeader IMP-FEED-2). */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Строка мультипрофиля: аватар (AsyncImage + фоллбэк — первая буква,
 * HiddenSourceRow-паттерн) + имя + метка «Текущий».
 *
 * НЕКЛИКАБЕЛЬНА намеренно (честное отклонение №1): переключение аккаунтов
 * требует токен-exchange флоу (wire не снят) и второго слота сессии
 * (ExchangeTokenStorage односессионный) — переключение не имитируется.
 */
@Composable
private fun MultiAccountRow(account: VkMultiAccount, isCurrent: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val photo = account.photo100
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = account.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "Текущий",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * Ссылочная ячейка раздела кабинета VK ID: иконка + заголовок + подпись +
 * шеврон, тап → внешний браузер (Linkify.openUrlExternal, паттерн «Статей»
 * ProfileScreen П-6b). Ячейки кабинета — веб-контент id.vk.com, честное
 * поведение — системный браузер.
 */
@Composable
private fun LinkCell(
    icon: ImageVector,
    title: String,
    subtitle: String,
    url: String,
) {
    // LocalContext.current — только в композиции (в onClick-лямбде нельзя).
    val context = LocalContext.current
    LinkCell(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = { openUrlExternal(ctx = context, rawUrl = url) },
    )
}

/** Тело LinkCell с готовым onClick (используется и для внутренних переходов). */
@Composable
private fun LinkCell(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp).defaultMinSize(minHeight = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
