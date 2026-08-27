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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import re.pinok.SovaApp
import re.pinok.ui.theme.SOVATheme
import java.security.MessageDigest

/**
 * Locker activity — PIN + biometric.
 *
 * Replaces the original SOVA V RE `LockedActivity` (which used ProgressIconView + custom
 * NoTouchRadioButton). SOVA_2.0 ships a pure Compose implementation.
 *
 * The PIN is stored as a SHA-256 hash in [SovaApp.prefs] — never plaintext.
 */
class LockerActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SOVATheme {
                LockerScreen(onUnlocked = { finish() })
            }
        }
    }

    companion object {
        fun launch(context: Context) {
            val i = Intent(context, LockerActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(i)
        }

        /** SHA-256 hash of a PIN string — stored in prefs, never compared plaintext. */
        fun hashPin(pin: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val raw = md.digest(("sova2-salt:$pin").toByteArray(Charsets.UTF_8))
            return raw.joinToString("") { "%02x".format(it) }
        }

        /** Vibrate the device briefly (used on wrong PIN). */
        fun vibrate(context: Context) {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(80)
            }
        }
    }
}

@Composable
private fun LockerScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { SovaApp.get(context).prefs }
    val snapshot by prefs.data.collectAsState(initial = null)
    val snap = snapshot
    val storedHash = snap?.lockerPinHash.orEmpty()
    // #29 (закрытие хвостов): биометрия показывается только когда
    // lockerBiometric=true. Раньше кнопка была видна всегда.
    val biometricEnabled = snap?.lockerBiometric == true
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

            error?.let {
                Text(
                    it,
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
