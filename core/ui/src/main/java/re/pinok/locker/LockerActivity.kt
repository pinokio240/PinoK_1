package re.pinok.locker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.security.MessageDigest

/**
 * Locker activity — PIN + biometric.
 *
 * Replaces the original SOVA V RE `LockedActivity` (which used ProgressIconView + custom
 * NoTouchRadioButton). SOVA_2.0 ships a pure Compose implementation.
 *
 * Fix #PIN-CORE #LOCKER-TO-CORE-UI: активити переехал из :app (re/pinok/locker) в :core:ui
 * (пакет сохранён — манифест `.locker.LockerActivity` и все FQCN-вызовы не меняются).
 * Модуль не зависит от :app, поэтому данные приходят через Intent-extras:
 *  - [EXTRA_PIN_HASH]  — SHA-256 хэш PIN (см. [hashPin]), хранится в SovaPrefs (:core:data);
 *  - [EXTRA_BIOMETRIC] — включена ли биометрия (lockerBiometric).
 * launch() кладёт оба extras — читать prefs внутри локера больше не нужно.
 *
 * WHY extras, а не общий источник: единственный способ открыть локер — companion [launch]
 * (MainActivity 4 места: boot / после auth / onResume cached / cold-fallback), поэтому
 * старое значение хэша на экране невозможно: PIN меняется только через SettingsScreen,
 * который достижим только из разблокированного приложения (локер к тому моменту finish()).
 */
class LockerActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Fix #PIN-CORE #LOCKER-TO-CORE-UI: раньше LockerScreen читал SovaApp.prefs
        // (app-зависимость). Теперь хэш и флаг биометрии кладутся в Intent при
        // запуске — core-модуль остаётся чистым от :app/:core:data.
        val storedHash = intent.getStringExtra(EXTRA_PIN_HASH).orEmpty()
        val biometricEnabled = intent.getBooleanExtra(EXTRA_BIOMETRIC, false)
        setContent {
            // Fix #PIN-CORE #LOCKER-TO-CORE-UI: приватная самодостаточная тема
            // (см. LockerTheme) вместо re.pinok.ui.theme.SOVATheme (:app).
            LockerTheme {
                LockerScreen(
                    storedHash = storedHash,
                    biometricEnabled = biometricEnabled,
                    onUnlocked = {
                        // Fix #380 #LOCKER-RELOCK-LOOP: фиксируем успешную разблокировку
                        // ДО finish() — resume-чек MainActivity в grace-окне 5с не
                        // перезапустит локер (иначе непрозрачный локер кладёт MainActivity
                        // в onStop → isBackgrounded=true → после finish() onResume видел
                        // «возврат из фона» и запускал локер заново — бесконечный цикл
                        // «ввёл верный PIN — снова просит PIN»). Касается и PIN-пада,
                        // и биометрии (обе ветки идут через onUnlocked).
                        LockerActivity.markUnlocked()
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        // Fix #PIN-CORE #LOCKER-TO-CORE-UI: квалификаторы extras — не столкнуться
        // с чужими ключами в Intent (могут быть серийные extras от системы).
        private const val EXTRA_PIN_HASH = "re.pinok.locker.EXTRA_PIN_HASH"
        private const val EXTRA_BIOMETRIC = "re.pinok.locker.EXTRA_BIOMETRIC"

        /**
         * Запуск локера. pinHash/biometricEnabled берутся вызывающим (MainActivity)
         * из актуального SovaPrefs-snapshot'а — Snapshot-поля lockerPinHash/lockerBiometric.
         */
        fun launch(context: Context, pinHash: String, biometricEnabled: Boolean) {
            val i = Intent(context, LockerActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_PIN_HASH, pinHash)
                putExtra(EXTRA_BIOMETRIC, biometricEnabled)
            }
            context.startActivity(i)
        }

        /** SHA-256 hash of a PIN string — stored in prefs, never compared plaintext. */
        fun hashPin(pin: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val raw = md.digest(("sova2-salt:$pin").toByteArray(Charsets.UTF_8))
            return raw.joinToString("") { "%02x".format(it) }
        }

        /**
         * Fix #380 #LOCKER-RELOCK-LOOP: timestamp последней успешной
         * разблокировки (markUnlocked, ставится перед finish()).
         *
         * MainActivity в onResume обязан пропускать перезапуск локера, пока
         * действует grace-окно [UNLOCK_GRACE_MS]: непрозрачная тема локера
         * кладёт MainActivity в onStop (isBackgrounded=true), и без grace
         * успешный ввод PIN приводил к немедленному повторному запуску
         * LockerActivity из resume-чека «Блокировки при возврате из фона» —
         * бесконечный цикл запроса PIN.
         */
        @Volatile
        var lastUnlockAtMs: Long = 0L
            private set

        /** Окно после разблокировки, в котором resume-чек не перезапускает локер. */
        private const val UNLOCK_GRACE_MS = 5_000L

        /** Зафиксировать успешную разблокировку (вызывается из LockerScreen перед finish()). */
        fun markUnlocked() {
            lastUnlockAtMs = System.currentTimeMillis()
        }

        /**
         * Одноразовость grace: консюмится первым же resume-чеком, который
         * пропустил перезапуск по grace — следующий РЕАЛЬНЫЙ уход в фон и
         * возврат снова блокируется (флаг не «живёт вечно» после разблокировки).
         */
        fun consumeUnlockGrace() {
            lastUnlockAtMs = 0L
        }

        /** true, пока действует grace-окно после успешной разблокировки. */
        fun unlockGraceActive(): Boolean {
            val at = lastUnlockAtMs
            return at != 0L && System.currentTimeMillis() - at < UNLOCK_GRACE_MS
        }

        /**
         * Vibrate the device briefly (used on wrong PIN).
         *
         * Fix #PIN-CORE #LOCKER-TO-CORE-UI: логика Fix #380 сохранена 1:1,
         * null-обработка переписана явно (if + захват val) по правилу NULL-ЯВНО —
         * поведение идентично прежнему (as? + ?. в исходнике).
         */
        fun vibrate(context: Context) {
            val vibrator: Vibrator? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                if (manager != null) manager.defaultVibrator else null
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null) return
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(80)
            }
        }
    }
}

/**
 * Fix #PIN-CORE #LOCKER-TO-CORE-UI: самодостаточная тема локера (:core:ui не может
 * импортировать re.pinok.ui.theme.SOVATheme из :app). Статические палитры скопированы
 * из SOVATheme (без dynamic-color/monet/accent/fontScale — локер их роли не читает),
 * тёмная и светлая ветки — ровно те значения, что раньше применялись к локеру по
 * isSystemInDarkTheme(). Используемые локером роли: background/onBackground (PIN dots),
 * surfaceVariant (клавиши пада), error (сообщение об ошибке).
 */
@Composable
private fun LockerTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            background = Color(0xFF121212),       // VK desktop: page background
            onBackground = Color.White,
            surface = Color(0xFF1E1E1E),          // VK desktop: card/surface
            onSurface = Color.White,
            surfaceVariant = Color(0xFF2A2A2A),   // VK: slightly lighter surface
            onSurfaceVariant = Color(0xFF999999), // VK: secondary text
            error = Color(0xFFCF6679),
        )
    } else {
        lightColorScheme(
            background = Color.White,
            onBackground = Color(0xFF000000),     // SovaColors.Black
            surface = Color.White,
            onSurface = Color(0xFF000000),
            surfaceVariant = Color(0xFFF5F5F5),
            onSurfaceVariant = Color(0xFF333333),
            error = Color(0xFFB00020),
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

@Composable
private fun LockerScreen(
    // Fix #PIN-CORE #LOCKER-TO-CORE-UI: данные из Intent-extras вместо SovaApp.prefs.
    storedHash: String,
    biometricEnabled: Boolean,
    onUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    // #29 (закрытие хвостов): биометрия показывается только когда
    // lockerBiometric=true. Раньше кнопка была видна всегда.
    val biometricAvailable = remember(biometricEnabled) {
        if (!biometricEnabled) false
        else BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    var pinInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun tryPin() {
        if (pinInput.length == 4) {
            val hash = LockerActivity.hashPin(pinInput)
            if (hash == storedHash) {
                onUnlocked()
            } else {
                error = "Неверный PIN"
                LockerActivity.vibrate(context)
                pinInput = ""
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Введите PIN",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(24.dp))

            // PIN dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(4) { i ->
                    val filled = i < pinInput.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (filled) MaterialTheme.colorScheme.onBackground else Color.Transparent)
                            .border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // NULL-ЯВНО: вместо error?.let — явная проверка и захват val
            // (тот же паттерн, что в PinSetupDialog в SettingsScreen).
            val errorMessage = error
            if (errorMessage != null) {
                Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
            }

            Spacer(Modifier.height(24.dp))

            // Number pad — клавиша "bio" показывается только когда
            // lockerBiometric=true AND устройство имеет зарегистрированный биометрик.
            val keys = if (biometricAvailable) {
                listOf("1","2","3","4","5","6","7","8","9","bio","0","del")
            } else {
                listOf("1","2","3","4","5","6","7","8","9","","0","del")
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                keys.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        row.forEach { k ->
                            if (k.isEmpty()) {
                                // Empty placeholder для сохранения layout'а 4x3
                                Box(modifier = Modifier.size(72.dp))
                            } else {
                                KeyButton(
                                    key = k,
                                    onClick = {
                                        when (k) {
                                            "del" -> {
                                                if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1)
                                            }
                                            "bio" -> {
                                                showBiometric(context as androidx.fragment.app.FragmentActivity) { onUnlocked() }
                                            }
                                            else -> {
                                                if (pinInput.length < 4) {
                                                    pinInput += k
                                                    tryPin()
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyButton(key: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when (key) {
            "del" -> Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Удалить")
            "bio" -> Icon(Icons.Default.Fingerprint, contentDescription = "Биометрия")
            else  -> Text(key, fontSize = 24.sp, fontWeight = FontWeight.Normal)
        }
    }
}

private fun showBiometric(activity: androidx.fragment.app.FragmentActivity, onSuccess: () -> Unit) {
    val canAuth = BiometricManager.from(activity)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) return

    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("PinoK")
        .setSubtitle("Разблокируйте приложение")
        .setNegativeButtonText("Отмена")
        .build()
    prompt.authenticate(info)
}
