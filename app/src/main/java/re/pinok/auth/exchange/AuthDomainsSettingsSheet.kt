package re.pinok.auth.exchange

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.local.SovaPrefs

/**
 * Fix #189: Шестерёнка с настройками VK доменов — доступна ДО авторизации.
 *
 * Контекст: VK мигрирует с .com на .ru (2025-2026). Раньше домены были
 * зашиты хардкодом — если VK полностью переключался на .ru, пользователю
 * приходилось ждать обновления приложения. Теперь он сам может переключить
 * домены на экране входа.
 *
 * UI:
 *   - IconButton (Icons.Outlined.Settings) в правом верхнем углу LandingScreen.
 *   - Тап → ModalBottomSheet с полями:
 *     • OAuth host (oauth.vk.com / oauth.vk.ru)
 *     • VK ID host (id.vk.com / id.vk.ru)
 *     • Login host (login.vk.com / login.vk.ru)
 *     • Mobile web host (m.vk.ru / m.vk.com)
 *     • API host (api.vk.com / api.vk.ru)
 *     • Web client_id (6287487)
 *     • Force revoke (toggle)
 *   - Кнопка «Сбросить к значениям по умолчанию».
 *
 * Запись: SovaPrefs.setAuth*Host() → DataStore → Flow → SovaApp.collect →
 * AuthDomainsConfig.snapshot обновляется за ~10-50мс.
 *
 * Чтение: AuthDomainsConfig.current — синхронно, O(1).
 */
@Composable
fun AuthDomainsSettingsIcon() {
    val context = LocalContext.current
    var showSheet by remember { mutableStateOf(false) }

    IconButton(onClick = { showSheet = true }) {
        Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = "Настройки доменов VK",
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }

    if (showSheet) {
        AuthDomainsSettingsSheet(onDismiss = { showSheet = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthDomainsSettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = remember { SovaApp.get(context) }
    val prefs = app.prefs
    val scope = rememberCoroutineScope()
    val snapshot by prefs.data.collectAsState(initial = null)
    val snap = snapshot
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Локальные state для smooth UX — пользователь печатает, мы не пишем
    // в DataStore на каждый символ (иначе лагает). Пишем только при
    // onValueChange finished или при dismiss.
    var oauthHost by remember(snap?.authOauthHost) {
        mutableStateOf(snap?.authOauthHost ?: SovaPrefs.AUTH_OAUTH_HOST_DEFAULT)
    }
    var idHost by remember(snap?.authIdHost) {
        mutableStateOf(snap?.authIdHost ?: SovaPrefs.AUTH_ID_HOST_DEFAULT)
    }
    var loginHost by remember(snap?.authLoginHost) {
        mutableStateOf(snap?.authLoginHost ?: SovaPrefs.AUTH_LOGIN_HOST_DEFAULT)
    }
    var mobileWebHost by remember(snap?.authMobileWebHost) {
        mutableStateOf(snap?.authMobileWebHost ?: SovaPrefs.AUTH_MOBILE_WEB_HOST_DEFAULT)
    }
    var apiHost by remember(snap?.authApiHost) {
        mutableStateOf(snap?.authApiHost ?: SovaPrefs.AUTH_API_HOST_DEFAULT)
    }
    var webClientId by remember(snap?.authWebClientId) {
        mutableStateOf(snap?.authWebClientId ?: SovaPrefs.AUTH_WEB_CLIENT_ID_DEFAULT)
    }
    var forceRevoke by remember(snap?.authForceRevoke) {
        mutableStateOf(snap?.authForceRevoke ?: false)
    }

    // Сохраняем ВСЕ поля при dismiss (на случай если пользователь забыл нажать «Сохранить»).
    // На самом деле мы сохраняем сразу при изменении (onValueChange), но для
    // OutlinedTextField это вызывается на каждый символ. Поэтому пишем в
    // DataStore только при dismiss — это надёжнее.

    if (snap == null) {
        // Ждём загрузки prefs — показываем пустой sheet (быстро, ~50мс).
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Загрузка настроек…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    ModalBottomSheet(
        onDismissRequest = {
            // Сохраняем все поля при закрытии sheet.
            scope.launch {
                prefs.setAuthOauthHost(oauthHost)
                prefs.setAuthIdHost(idHost)
                prefs.setAuthLoginHost(loginHost)
                prefs.setAuthMobileWebHost(mobileWebHost)
                prefs.setAuthApiHost(apiHost)
                prefs.setAuthWebClientId(webClientId)
                prefs.setAuthForceRevoke(forceRevoke)
            }
            onDismiss()
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Заголовок
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "Домены VK",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Text(
                text = "VK мигрирует с .com на .ru. Если вход не работает — " +
                    "попробуйте переключить домены на .ru. Настройки применяются " +
                    "немедленно, без перезапуска.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))

            // Поля доменов
            DomainField(
                label = "OAuth host",
                hint = "oauth.vk.com",
                value = oauthHost,
                onChange = { oauthHost = it },
                description = "Авторизация (oauth.vk.com/authorize, /access_token)",
            )
            DomainField(
                label = "VK ID host",
                hint = "id.vk.com",
                value = idHost,
                onChange = { idHost = it },
                description = "VK ID, exchange token refresh",
            )
            DomainField(
                label = "Login host",
                hint = "login.vk.com",
                value = loginHost,
                onChange = { loginHost = it },
                description = "Внутренний login endpoint (remixsid → token)",
            )
            DomainField(
                label = "Mobile web host",
                hint = "m.vk.ru",
                value = mobileWebHost,
                onChange = { mobileWebHost = it },
                description = "WebView для входа (m.vk.ru / m.vk.com)",
            )
            DomainField(
                label = "API host",
                hint = "api.vk.com",
                value = apiHost,
                onChange = { apiHost = it },
                description = "VK API gateway (api.vk.com/method/*). Применяется к API-вызовам (VKEndpoints).",
            )

            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))

            // Дополнительные параметры
            Text(
                text = "Дополнительно",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            DomainField(
                label = "Web client_id",
                hint = "6287487",
                value = webClientId,
                onChange = { webClientId = it },
                description = "client_id vk.com desktop web (6287487 по умолчанию)",
                keyboardType = KeyboardType.Number,
            )

            // Force revoke toggle
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Всегда спрашивать разрешение",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "Если ВКЛ — VK всегда показывает экран «Разрешить доступ». " +
                                "Если ВЫКЛ (по умолчанию) — silent sign-in: при активной " +
                                "сессии VK сразу редиректит без запроса.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = forceRevoke,
                        onCheckedChange = { forceRevoke = it },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Кнопка сброса
            OutlinedButton(
                onClick = {
                    oauthHost = SovaPrefs.AUTH_OAUTH_HOST_DEFAULT
                    idHost = SovaPrefs.AUTH_ID_HOST_DEFAULT
                    loginHost = SovaPrefs.AUTH_LOGIN_HOST_DEFAULT
                    mobileWebHost = SovaPrefs.AUTH_MOBILE_WEB_HOST_DEFAULT
                    apiHost = SovaPrefs.AUTH_API_HOST_DEFAULT
                    webClientId = SovaPrefs.AUTH_WEB_CLIENT_ID_DEFAULT
                    forceRevoke = false
                    scope.launch {
                        prefs.setAuthOauthHost(oauthHost)
                        prefs.setAuthIdHost(idHost)
                        prefs.setAuthLoginHost(loginHost)
                        prefs.setAuthMobileWebHost(mobileWebHost)
                        prefs.setAuthApiHost(apiHost)
                        prefs.setAuthWebClientId(webClientId)
                        prefs.setAuthForceRevoke(forceRevoke)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Restore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Сбросить к значениям по умолчанию")
            }

            Spacer(Modifier.height(4.dp))

            // Подсказка про .ru миграцию
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Public,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "Быстрое переключение на .ru",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Если VK полностью перешёл на .ru домены — нажмите, " +
                            "чтобы переключить все 5 доменов на .ru варианты:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            oauthHost = "oauth.vk.ru"
                            idHost = "id.vk.ru"
                            loginHost = "login.vk.ru"
                            mobileWebHost = "m.vk.ru"
                            apiHost = "api.vk.ru"
                            scope.launch {
                                prefs.setAuthOauthHost(oauthHost)
                                prefs.setAuthIdHost(idHost)
                                prefs.setAuthLoginHost(loginHost)
                                prefs.setAuthMobileWebHost(mobileWebHost)
                                prefs.setAuthApiHost(apiHost)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                    ) {
                        Text("Переключить все на .ru")
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Информационный блок
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VerifiedUser,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Безопасность",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "• Scheme всегда https (принудительно).\n" +
                            "• Пустые значения заменяются на defaults.\n" +
                            "• Redirect URI — всегда https://oauth.vk.com/blank.html\n" +
                            "  (Fix #194: заглушка /blank.html существует только на .com,\n" +
                            "   на .ru отдаёт 405; не зависит от oauthHost).\n" +
                            "• Изменения применяются немедленно к следующему auth flow.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Текстовое поле для домена с label, hint и описанием.
 */
@Composable
private fun DomainField(
    label: String,
    hint: String,
    value: String,
    onChange: (String) -> Unit,
    description: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            placeholder = { Text(hint) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )
    }
}
