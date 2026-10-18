package re.pinok.auth.auth2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import re.pinok.SovaApp
import re.pinok.auth.AuthViewModel
import re.pinok.auth.exchange.AuthDomainsConfig
import re.pinok.auth.exchange.AuthState
import re.pinok.ui.components.CaptchaDialog
import re.pinok.util.AppLog

// #AUTH2 (2026-10-17): «Авторизация 2» — альтернативный экран входа.
// Переделан ПО ОБРАЗЦУ vk-lite-auth-0.12.0-source.zip: автономный WebView-экран
// [VkLiteWebViewAuthScreen] грузит m.vk.ru/login?app_id=7879029, VK сам принимает
// номер/пароль/2FA/капчу, клиент простым polling читает web_token из localStorage
// и сохраняет его. НЕ использует VkAuthWebViewScreenV2/WebTokenAuth (тот вмешивался
// в процесс входа и давал белый экран — см. VkLiteWebViewAuthScreen KDoc).
class AuthV2Activity : ComponentActivity() {

    private val viewModel: AuthViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = SovaApp.get(this@AuthV2Activity)
                AuthViewModel(app.exchangeAuthRepository)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppLog.i("AuthV2Activity", "onCreate — флоу «Авторизация 2» (автономный WebView, как vk-lite)")
        // Если токен УЖЕ валиден (повторный вход / silent-обмен успел сохранить) —
        // показывать WebView не нужно, сразу завершаем успехом (интерфейс PinoK проявится).
        runCatching {
            if (SovaApp.get(this).tokenStorage.hasValidToken()) {
                AppLog.i("AuthV2Activity", "Токен уже валиден — завершаем сразу (RESULT_OK), без WebView")
                setResult(RESULT_OK)
                finish()
                return
            }
        }.onFailure { e ->
            AppLog.w("AuthV2Activity", "token check failed: ${e.message}")
        }
        setContent {
            MaterialTheme {
                // #CAPTCHA-V2: дублируем глобальный CaptchaDialog (мы отдельная Activity).
                CaptchaDialog()
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state = viewModel.state.collectAsState().value
                    // Стартуем на m.vk.ru/login?app_id=7879029 (собственный app_id m.vk.ru).
                    VkLiteWebViewAuthScreen(
                        startUrl = "https://${AuthDomainsConfig.current.mobileWebHost}/login?app_id=7879029",
                        onTokenReady = { token, uid ->
                            // Сохраняем токен напрямую (как vk-lite store.save). submitOAuthToken
                            // умеет сохранять access_token → repo переведёт state в Success.
                            AppLog.i("AuthV2Activity", "web_token готов (user_id=$uid) — сохраняем через submitOAuthToken")
                            viewModel.submitOAuthToken(token, uid)
                        },
                        onDismiss = {
                            viewModel.cancel()
                            setResult(RESULT_CANCELED)
                            finish()
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    // При AuthState.Success — завершаем с RESULT_OK (пробросится в MainActivity).
                    LaunchedEffect(state) {
                        if (state is AuthState.Success) {
                            setResult(RESULT_OK)
                            finish()
                        }
                    }
                }
            }
        }
    }
}