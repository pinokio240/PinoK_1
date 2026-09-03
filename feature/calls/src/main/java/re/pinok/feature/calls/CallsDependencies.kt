package re.pinok.feature.calls

import androidx.compose.runtime.staticCompositionLocalOf
import re.pinok.api.VKApiClient
import re.pinok.data.local.SovaPrefs
import re.pinok.realtime.CallSignalingClient
import re.pinok.realtime.Queuev4Client
import re.pinok.realtime.LongPollClient
import re.pinok.auth.exchange.ExchangeAuthRepository
import okhttp3.OkHttpClient
import kotlinx.coroutines.flow.StateFlow

interface CallsDependencies {
    val apiClient: VKApiClient
    val prefs: SovaPrefs
    val httpClient: OkHttpClient
    val exchangeAuthRepository: ExchangeAuthRepository
    val queuev4Client: Queuev4Client
    val longPollClient: LongPollClient
    val callsSessionKey: String
    val callsSessionUid: Long

    suspend fun ensureCallsSessionKey(force: Boolean = false): String?
    suspend fun getCallConversationParams(conversationId: String): Pair<String, re.pinok.media.ConversationParamsDecoder.Params>
    fun getOkUid(): Long
    fun getVkUid(): Long
    fun getAnonymUid(): Long
}

val LocalCallsDeps = staticCompositionLocalOf<CallsDependencies> {
    error("CallsDependencies not provided")
}