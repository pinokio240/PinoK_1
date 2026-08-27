package re.pinok.ui.screens.calls

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import re.pinok.SovaApp
import re.pinok.data.model.CallDirection
import re.pinok.data.model.CallMediaType
import re.pinok.data.model.CallPhase
import re.pinok.data.model.CallParticipant
import re.pinok.data.model.VkCall
import re.pinok.media.WebRtcEngine
import re.pinok.util.AppLog
import org.webrtc.SessionDescription

/**
 * #CALLS: экран активного звонка — входящий, исходящий, разговор.
 *
 * Состояния:
 *  - RINGING: анимация звонка + кнопки «Принять»/«Отклонить»
 *  - CONNECTING: спиннер + «Соединение…»
 *  - ACTIVE: разговор — кнопки mute/speaker/end
 *  - ENDED/FAILED: результат + кнопка «Закрыть»
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(
    peerId: Long,
    title: String,
    photo: String?,
    incoming: Boolean,
    onNavigateBack: () -> Unit,
) {
    val app = SovaApp.get()
    val context = LocalContext.current
    val direction = if (incoming) CallDirection.INCOMING else CallDirection.OUTGOING
    var phase by remember { mutableStateOf(if (incoming) CallPhase.RINGING else CallPhase.CONNECTING) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }
    var callDuration by remember { mutableStateOf(0L) }

    val peer = CallParticipant(peerId = peerId, name = title, photo100 = photo)
    val call = VkCall(
        callId = "",
        peer = peer,
        direction = direction,
        mediaType = CallMediaType.AUDIO,
        phase = phase,
        isMuted = isMuted,
        isSpeakerOn = isSpeakerOn,
    )

    val engine = remember {
        WebRtcEngine(
            context = context,
            onCallPhaseChanged = { phase = it },
            onLocalSdpReady = { /* TODO: send via Queuev4Client */ },
            onIceCandidateReady = { /* TODO: send via Queuev4Client */ },
        )
    }

    LaunchedEffect(Unit) {
        engine.initialize()
        if (!incoming) {
            AppLog.i("CallScreen", "Starting call to peerId=$peerId")
            try {
                val callId = app.apiClient.messagesStartCall(peerId)
                AppLog.i("CallScreen", "messagesStartCall returned: $callId")
                if (callId == null) {
                    val err = app.apiClient.lastApiError
                    val errCode = app.apiClient.lastApiErrorCode
                    AppLog.e("CallScreen", "startCall failed: err=$err code=$errCode")
                    android.widget.Toast.makeText(
                        context,
                        "Звонки не поддерживаются для web-токена (err=$errCode)",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                    phase = CallPhase.FAILED
                } else {
                    AppLog.i("CallScreen", "Call started: callId=$callId")
                    // #CALLS: запускаем queuev4-клиент для сигналинга звонка.
                    // queuev4.start() сам вызовет queue.subscribe с SAT-токеном.
                    app.queuev4Client.start()
                }
            } catch (e: Exception) {
                AppLog.e("CallScreen", "startCall exception", e)
                android.widget.Toast.makeText(
                    context,
                    "Ошибка: ${e.message}",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
                phase = CallPhase.FAILED
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { engine.release() }
    }

    val startTime = remember { System.currentTimeMillis() }
    LaunchedEffect(phase) {
        if (phase == CallPhase.ACTIVE) {
            kotlinx.coroutines.delay(1000)
            callDuration = (System.currentTimeMillis() - startTime) / 1000
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E),
                    titleContentColor = Color.White,
                ),
            )
        },
        containerColor = Color(0xFF1A1A2E),
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(32.dp),
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (photo != null) {
                        AsyncImage(
                            model = photo,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    } else {
                        Text(title.take(1).uppercase(), fontSize = 40.sp, color = Color.White)
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Phase text
                Text(
                    text = when (phase) {
                        CallPhase.RINGING -> if (incoming) "Входящий звонок…" else "Звоним…"
                        CallPhase.CONNECTING -> "Соединение…"
                        CallPhase.ACTIVE -> formatDuration(callDuration)
                        CallPhase.ENDED -> "Звонок завершён"
                        CallPhase.FAILED -> "Ошибка соединения"
                        else -> ""
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(8.dp))

                if (phase == CallPhase.CONNECTING) {
                    CircularProgressIndicator(color = Color.White)
                }

                Spacer(Modifier.height(48.dp))

                // Controls
                when (phase) {
                    CallPhase.RINGING -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Decline
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                IconButton(
                                    onClick = {
                                        phase = CallPhase.ENDED
                                        onNavigateBack()
                                    },
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red),
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                                ) {
                                    Icon(Icons.Default.CallEnd, contentDescription = "Отклонить", modifier = Modifier.size(32.dp))
                                }
                                Text("Отклонить", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                            }
                            // Accept
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                IconButton(
                                    onClick = {
                                        engine.acceptCall(call)
                                        phase = CallPhase.CONNECTING
                                    },
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4CAF50)),
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = "Принять", modifier = Modifier.size(32.dp))
                                }
                                Text("Принять", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                            }
                        }
                    }
                    CallPhase.ACTIVE, CallPhase.CONNECTING -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CallControlButton(
                                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                label = if (isMuted) "Вкл. микрофон" else "Микрофон",
                                color = if (isMuted) Color(0xFF555555) else Color(0xFF333333),
                                onClick = { isMuted = !isMuted; engine.setMuted(isMuted) },
                            )
                            CallControlButton(
                                icon = Icons.Default.CallEnd,
                                label = "Завершить",
                                color = Color.Red,
                                onClick = {
                                    engine.endCall()
                                    phase = CallPhase.ENDED
                                    onNavigateBack()
                                },
                            )
                            CallControlButton(
                                icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                label = if (isSpeakerOn) "Динамик" else "Динамик",
                                color = if (isSpeakerOn) Color(0xFF4CAF50) else Color(0xFF333333),
                                onClick = { isSpeakerOn = !isSpeakerOn; engine.setSpeakerOn(isSpeakerOn) },
                            )
                        }
                    }
                    CallPhase.ENDED, CallPhase.FAILED -> {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF333333)),
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "Закрыть", tint = Color.White)
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(color),
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
        }
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "${m}:${s.toString().padStart(2, '0')}"
}