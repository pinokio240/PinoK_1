package re.pinok.ui.screens.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.CuaMethod
import re.pinok.data.model.CuaValidationMethod
import re.pinok.data.model.CuaValidationMethods
import re.pinok.util.AppLog

/**
 * §49.6 Sprint VK-ID-1.5 — CUA (Confirm User Action) verification bottom sheet.
 *
 * Используется перед опасными действиями (resetSessions, resetAllSessions,
 * changePassword, ...) — VK требует подтверждение через SMS/Push/Email.
 *
 * Flow (MVI-стиль, состояние в [CuaSheetState]):
 *  1. На open: грузит `cua.getValidationMethods(action)`.
 *  2. Если methods пустые + canSkip → сразу [onVerified](null).
 *  3. Если methods есть → юзер выбирает канал → `cua.sendXxxCode`.
 *  4. Юзер вводит код → `cua.checkXxxCode`.
 *  5. При success → [onVerified](validationToken).
 *  6. При error → показывает сообщение, позволяет повторить.
 *
 * Lifecycle: ModalBottomSheet сам управляется parent-композицией.
 * CoroutineScope — rememberCoroutineScope (привязан к композиции sheet'а).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuaVerifySheet(
    action: String,
    title: String = "Подтверждение действия",
    onDismiss: () -> Unit,
    onVerified: (validationToken: String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()

    // Состояние sheet'а — sealed class для type-safe UI states.
    var state by remember { mutableStateOf<CuaSheetState>(CuaSheetState.Loading) }
    var codeInput by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf<String?>(null) }
    var resendDelay by remember { mutableStateOf(0) }
    var isChecking by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var selectedMethod by remember { mutableStateOf<CuaValidationMethod?>(null) }

    // Шаг 1: загрузка доступных methods при открытии sheet'а.
    LaunchedEffect(action) {
        state = CuaSheetState.Loading
        val hash = runCatching { app.tokenStorage.logoutHash() }.getOrNull()
        val methods = app.apiClient.cuaGetValidationMethods(action, hash)
        if (methods == null) {
            state = CuaSheetState.Error("Не удалось загрузить методы подтверждения. Проверьте подключение.")
            return@LaunchedEffect
        }
        if (methods.methods.isEmpty() && methods.canSkip) {
            // VK не требует verification для этого action — пропускаем.
            AppLog.i("CuaVerifySheet", "canSkip=true — verification не требуется для action=$action")
            onVerified(null)
            onDismiss()
            return@LaunchedEffect
        }
        if (methods.methods.isEmpty()) {
            state = CuaSheetState.Error("Нет доступных методов подтверждения.")
            return@LaunchedEffect
        }
        state = CuaSheetState.MethodsLoaded(methods)
        // Автовыбор первичного метода + автосенд кода.
        val primary = methods.methods.firstOrNull { it.isPrimary }
        selectedMethod = if (primary != null) primary else methods.methods.first()
    }

    // Автоматическая отправка кода при выборе метода.
    LaunchedEffect(selectedMethod) {
        val m = selectedMethod
        if (m == null) return@LaunchedEffect
        codeInput = ""
        codeError = null
        isSending = true
        val hash = runCatching { app.tokenStorage.logoutHash() }.getOrNull()
        val res = app.apiClient.cuaSendCode(m.method, hash)
        isSending = false
        if (!res.success) {
            val err = res.error
            codeError = if (err != null) err else "Не удалось отправить код"
        } else {
            resendDelay = res.retryDelaySec
        }
    }

    // Countdown resend delay.
    LaunchedEffect(resendDelay) {
        while (resendDelay > 0) {
            delay(1000)
            resendDelay -= 1
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            when (val s = state) {
                CuaSheetState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is CuaSheetState.Error -> {
                    Text(
                        s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Закрыть")
                    }
                }
                is CuaSheetState.MethodsLoaded -> {
                    // Шаг 2: выбор метода (если их несколько).
                    if (s.methods.methods.size > 1) {
                        Text(
                            "Выберите способ подтверждения:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        s.methods.methods.forEach { m ->
                            MethodRow(
                                method = m,
                                selected = selectedMethod == m,
                                onSelect = { selectedMethod = m },
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    } else {
                        // Один метод — показываем что выбрали.
                        val sm1 = selectedMethod
                        if (sm1 != null) {
                            MethodRow(
                                method = sm1,
                                selected = true,
                                onSelect = {},
                            )
                        }
                    }

                    // Шаг 3: ввод кода.
                    val sm = selectedMethod
                    if (sm != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "Код отправлен: ${sm.mask}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else if (resendDelay > 0) {
                                Text(
                                    "Повтор через ${resendDelay}с",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                IconButton(onClick = {
                                    scope.launch {
                                        val m = selectedMethod
                                        if (m == null) return@launch
                                        isSending = true
                                        val hash = runCatching { app.tokenStorage.logoutHash() }.getOrNull()
                                        val res = app.apiClient.cuaSendCode(m.method, hash)
                                        isSending = false
                                        if (res.success) resendDelay = res.retryDelaySec
                                    }
                                }) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = "Отправить код повторно")
                                }
                            }
                        }

                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = {
                                codeInput = it.filter { ch -> ch.isDigit() || ch.isLetter() }.take(8)
                                codeError = null
                            },
                            label = { Text("Код подтверждения") },
                            isError = codeError != null,
                            supportingText = run {
                                val ce = codeError
                                if (ce != null) { { Text(ce) } } else null
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                        )

                        Button(
                            onClick = {
                                if (codeInput.isBlank()) {
                                    codeError = "Введите код"
                                    return@Button
                                }
                                scope.launch {
                                    isChecking = true
                                    codeError = null
                                    val m = sm
                                    val hash = runCatching { app.tokenStorage.logoutHash() }.getOrNull()
                                    val res = app.apiClient.cuaCheckCode(codeInput, m.method, hash)
                                    isChecking = false
                                    if (res.success) {
                                        AppLog.i("CuaVerifySheet", "verification OK — closing sheet")
                                        onVerified(res.validationToken)
                                        onDismiss()
                                    } else {
                                        val attempts = res.attemptsRemaining
                                        codeError = if (attempts != null && attempts > 0) {
                                            "Неверный код. Осталось попыток: $attempts"
                                        } else {
                                            val e = res.error
                                            if (e != null) e else "Неверный код"
                                        }
                                    }
                                }
                            },
                            enabled = !isChecking && codeInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            if (isChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text("Подтвердить и продолжить")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MethodRow(
    method: CuaValidationMethod,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, onClick = onSelect)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.size(8.dp))
        val icon = when (method.method) {
            CuaMethod.SMS, CuaMethod.PHONE_BIND -> Icons.Outlined.PhoneAndroid
            CuaMethod.PUSH -> Icons.Outlined.Notifications
            CuaMethod.EMAIL -> Icons.Outlined.Email
        }
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(method.method.displayName, style = MaterialTheme.typography.bodyLarge)
            if (method.mask.isNotBlank()) {
                Text(
                    method.mask,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Type-safe состояние CUA sheet — для MVI-стиля без ViewModel. */
private sealed class CuaSheetState {
    /** Грузим доступные методы подтверждения. */
    object Loading : CuaSheetState()
    /** Методы загружены — показываем выбор + ввод кода. */
    data class MethodsLoaded(val methods: CuaValidationMethods) : CuaSheetState()
    /** Невозможно начать verification. */
    data class Error(val message: String) : CuaSheetState()
}
