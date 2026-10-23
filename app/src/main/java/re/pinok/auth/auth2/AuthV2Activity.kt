package re.pinok.auth.auth2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.auth.AuthViewModel
import re.pinok.auth.exchange.AuthDomainsConfig
import re.pinok.auth.exchange.AuthState
import re.pinok.ui.components.CaptchaDialog
import re.pinok.util.AppLog

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
                CaptchaDialog()
                Surface(modifier = Modifier.fillMaxSize()) {
                    val app = SovaApp.get(this@AuthV2Activity)
                    val scope = rememberCoroutineScope()

                    // AUTH2-HINT-DIALOG: показать инструкцию перед WebView
                    val hintDisabled = app.prefsSnapshot?.authV2HintDisabled ?: false
                    var showHint by remember { mutableStateOf(!hintDisabled) }
                    var showWebView by remember { mutableStateOf(hintDisabled) }

                    if (showHint) {
                        AlertDialog(
                            onDismissRequest = { },
                            title = { Text("Авторизация 2") },
                            text = {
                                Text(
                                    "Это не вход во второй аккаунт. " +
                                    "Это запасной вход, если не сработал первый.\n\n" +
                                    "Авторизуйтесь в ВК, после успешной авторизации " +
                                    "СВЕРНИТЕ И РАЗВЕРНИТЕ приложение — " +
                                    "это нужно для «подхвата» авторизации."
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showHint = false
                                    showWebView = true
                                }) { Text("ОК") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    scope.launch {
                                        app.prefs.setAuthV2HintDisabled(true)
                                    }
                                    showHint = false
                                    showWebView = true
                                }) { Text("Больше не показывать") }
                            },
                        )
                    }

                    if (showWebView) {
                        val state = viewModel.state.collectAsState().value
                        VkLiteWebViewAuthScreen(
                            startUrl = "https://${AuthDomainsConfig.current.mobileWebHost}/login?app_id=7879029",
                            onTokenReady = { token, uid ->
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
}
