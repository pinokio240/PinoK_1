package re.pinok.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
 * #SETTINGS-DOMAINS (2026-10-14): отдельный полноэкранный экран «Настройки доменов VK».
 *
 * Это обёртка (Scaffold + TopAppBar) вокруг [AuthDomainsContent]. Используется как
 * самостоятельный экран. Тот же контент доступен в виде вкладки «Домены VK» внутри
 * главного экрана настроек (SettingsTab.DOMAINS → AuthDomainsContent) — настройки
 * доменов доступны ВСЕГДА, включая гостевой режим, и не являются мёртвым балластом.
 *
 * Содержит все настраиваемые VK-домены (oauth/id/login/mobile-web/api/web-api/userapi/
 * media-cdn/ok-cdn/my-cdn/queuev4/eh/vk-domain + web-client-id + force-revoke) и
 * кнопки «Сохранить» / «Восстановить по умолчанию».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthDomainsScreen(onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    Scaffold(
        // P0.46-IME-unify: паттерн A — contentWindowInsets=0 на Scaffold (отключаем
        // auto-insets), windowInsetsPadding(ime.union(navbar)) на input-контенте
        // (Box ниже оборачивает AuthDomainsContent с OutlinedTextField-полями).
        // Раньше использовался .imePadding() на Box — устаревший паттерн C.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Настройки доменов VK", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        ) {
            AuthDomainsContent(snackbar = snackbar)
        }
    }
}

/**
 * #SETTINGS-DOMAINS (2026-10-14): содержимое настроек доменов VK.
 * Используется и в полноэкранном экране (AuthDomainsScreen), и как вкладка
 * «Домены VK» в главном экране настроек (SettingsTab.DOMAINS).
 *
 * @param snackbar хост для Snackbar с уведомлением о сохранении (может быть пустым/неактивным).
 */
@Composable
fun AuthDomainsContent(snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val app = remember { SovaApp.get(context) }
    val prefs = app.prefs
    val scope = rememberCoroutineScope()
    val snapshot by prefs.data.collectAsState(initial = null)
    val snap = snapshot

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
    // #SETTINGS-DOMAINS: доп. домены.
    var webApiHost by remember(snap?.authWebApiHost) {
        mutableStateOf(snap?.authWebApiHost ?: SovaPrefs.AUTH_WEB_API_HOST_DEFAULT)
    }
    var userapiHost by remember(snap?.authUserapiHost) {
        mutableStateOf(snap?.authUserapiHost ?: SovaPrefs.AUTH_USERAPI_HOST_DEFAULT)
    }
    var mediaCdnHost by remember(snap?.authMediaCdnHost) {
        mutableStateOf(snap?.authMediaCdnHost ?: SovaPrefs.AUTH_MEDIA_CDN_HOST_DEFAULT)
    }
    var okCdnHost by remember(snap?.authOkCdnHost) {
        mutableStateOf(snap?.authOkCdnHost ?: SovaPrefs.AUTH_OK_CDN_HOST_DEFAULT)
    }
    var myCdnHost by remember(snap?.authMyCdnHost) {
        mutableStateOf(snap?.authMyCdnHost ?: SovaPrefs.AUTH_MY_CDN_HOST_DEFAULT)
    }
    var queuev4Host by remember(snap?.authQueuev4Host) {
        mutableStateOf(snap?.authQueuev4Host ?: SovaPrefs.AUTH_QUEUEV4_HOST_DEFAULT)
    }
    var ehHost by remember(snap?.authEhHost) {
        mutableStateOf(snap?.authEhHost ?: SovaPrefs.AUTH_EH_HOST_DEFAULT)
    }
    var vkDomain by remember(snap?.authVkDomain) {
        mutableStateOf(snap?.authVkDomain ?: SovaPrefs.AUTH_VK_DOMAIN_DEFAULT)
    }

    // Функция сохранения всех полей в SovaPrefs.
    val saveAll: () -> Unit = {
        scope.launch {
            prefs.setAuthOauthHost(oauthHost)
            prefs.setAuthIdHost(idHost)
            prefs.setAuthLoginHost(loginHost)
            prefs.setAuthMobileWebHost(mobileWebHost)
            prefs.setAuthApiHost(apiHost)
            prefs.setAuthWebClientId(webClientId)
            prefs.setAuthForceRevoke(forceRevoke)
            prefs.setAuthWebApiHost(webApiHost)
            prefs.setAuthUserapiHost(userapiHost)
            prefs.setAuthMediaCdnHost(mediaCdnHost)
            prefs.setAuthOkCdnHost(okCdnHost)
            prefs.setAuthMyCdnHost(myCdnHost)
            prefs.setAuthQueuev4Host(queuev4Host)
            prefs.setAuthEhHost(ehHost)
            prefs.setAuthVkDomain(vkDomain)
            snackbar.showSnackbar("Настройки доменов сохранены")
        }
    }

    // Восстановление всех полей на дефолты + сохранение.
    val restoreDefaults: () -> Unit = {
        oauthHost = SovaPrefs.AUTH_OAUTH_HOST_DEFAULT
        idHost = SovaPrefs.AUTH_ID_HOST_DEFAULT
        loginHost = SovaPrefs.AUTH_LOGIN_HOST_DEFAULT
        mobileWebHost = SovaPrefs.AUTH_MOBILE_WEB_HOST_DEFAULT
        apiHost = SovaPrefs.AUTH_API_HOST_DEFAULT
        webClientId = SovaPrefs.AUTH_WEB_CLIENT_ID_DEFAULT
        forceRevoke = false
        webApiHost = SovaPrefs.AUTH_WEB_API_HOST_DEFAULT
        userapiHost = SovaPrefs.AUTH_USERAPI_HOST_DEFAULT
        mediaCdnHost = SovaPrefs.AUTH_MEDIA_CDN_HOST_DEFAULT
        okCdnHost = SovaPrefs.AUTH_OK_CDN_HOST_DEFAULT
        myCdnHost = SovaPrefs.AUTH_MY_CDN_HOST_DEFAULT
        queuev4Host = SovaPrefs.AUTH_QUEUEV4_HOST_DEFAULT
        ehHost = SovaPrefs.AUTH_EH_HOST_DEFAULT
        vkDomain = SovaPrefs.AUTH_VK_DOMAIN_DEFAULT
        saveAll()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text("Домены VK", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Text(
            "VK мигрирует с .com на .ru. Настройки доменов влияют на то, к каким серверам " +
                "подключается приложение. Изменения применяются после нажатия «Сохранить».",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(4.dp))
        HorizontalDivider()
        Spacer(Modifier.height(4.dp))

        Text("Авторизация", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        DomainField("OAuth host", "oauth.vk.com", oauthHost, { oauthHost = it }, "Авторизация (oauth.vk.com/authorize, /access_token)")
        DomainField("VK ID host", "id.vk.com", idHost, { idHost = it }, "VK ID, exchange token refresh")
        DomainField("Login host", "login.vk.com", loginHost, { loginHost = it }, "Внутренний login endpoint (remixsid → token)")
        DomainField("Mobile web host", "m.vk.ru", mobileWebHost, { mobileWebHost = it }, "WebView для входа (m.vk.ru / m.vk.com)")
        DomainField("API host", "api.vk.com", apiHost, { apiHost = it }, "VK API gateway (api.vk.com/method/*)")
        DomainField("Web API host", "web.api.vk.ru", webApiHost, { webApiHost = it }, "Mobile-web API gateway (web.api.vk.ru)")

        Spacer(Modifier.height(4.dp))
        HorizontalDivider()
        Spacer(Modifier.height(4.dp))

        Text("Медиа и сервисы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        DomainField("UserAPI host", "userapi.com", userapiHost, { userapiHost = it }, "Аватарки/фото (sun*.userapi.com)")
        DomainField("Media CDN host", "vkuserphoto.ru", mediaCdnHost, { mediaCdnHost = it }, "Фото пользователей (vkuserphoto.ru)")
        DomainField("OK CDN host", "okcdn.ru", okCdnHost, { okCdnHost = it }, "Звонки/видео (calls/videowebrtc.okcdn.ru)")
        DomainField("My CDN host", "mycdn.me", myCdnHost, { myCdnHost = it }, "Видео/аудио CDN (mycdn.me)")
        DomainField("Основной VK домен", "vk.ru", vkDomain, { vkDomain = it }, "Базовый домен (vk.ru / vk.com)")

        Spacer(Modifier.height(4.dp))
        HorizontalDivider()
        Spacer(Modifier.height(4.dp))

        Text("Realtime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        DomainField("Queuev4 host", "queuev4.vk.ru", queuev4Host, { queuev4Host = it }, "Очередь событий/звонков (queuev4.vk.ru)")
        DomainField("EventHub host", "eh.vk.ru", ehHost, { ehHost = it }, "EventHub WebSocket (eh.vk.ru)")

        Spacer(Modifier.height(4.dp))
        HorizontalDivider()
        Spacer(Modifier.height(4.dp))

        Text("Дополнительно", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        DomainField("Web client_id", "6287487", webClientId, { webClientId = it }, "client_id vk.com desktop web", keyboardType = KeyboardType.Number)

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Всегда спрашивать разрешение", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "Если ВКЛ — VK всегда показывает экран «Разрешить доступ». Если ВЫКЛ (по умолчанию) — silent sign-in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = forceRevoke, onCheckedChange = { forceRevoke = it })
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = saveAll,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("Сохранить", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        OutlinedButton(
            onClick = restoreDefaults,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Восстановить по умолчанию")
        }

        Spacer(Modifier.height(4.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Безопасность", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "• Scheme всегда https (принудительно).\n" +
                        "• Пустые значения заменяются на defaults.\n" +
                        "• Redirect URI — всегда https://oauth.vk.com/blank.html (Fix #194).\n" +
                        "• Изменения применяются после «Сохранить».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

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