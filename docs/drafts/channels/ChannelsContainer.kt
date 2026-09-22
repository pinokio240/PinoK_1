// =====================================================================
// PinoK_1 - CHERNOVIK kontenera "Kanali" (etap 1.6 #ARCH-CONTAINERS)
// =====================================================================
// Istochniki:
//  - CallsContainer.kt (etap 1.3) - shablon kontenera
//  - kanaly.snapshoty.razbor.md (Task ID 55) - patterny VK web
//  - plan.volna-18 (kanaly: schetchiki i set)
//  - live CDP-damp 2026-09-14 (/im/channels/-230861657)
//
// PRAVILA (obyazatelny):
//  - :feature:* zavisit TOLKO ot :contracts + :core:* - nikogda ot :app
//  - Kontener publikuet capability v ContainerRegistry; host stroit UI iz reestra
//  - Net kontenera -> net capability -> punkt UI ne renderitsya, yadro zhivo
//  - NULL-EXPLICIT (CODING_STYLE.md): !!, ?., ?: zapreshcheny; yavnye proverki
//  - Tyazhelye resursy (VKApiClient/set) NE derzhim v kontenere
// =====================================================================

package re.pinok.feature.channels

import android.app.Application
import re.pinok.contracts.AppContainer
import re.pinok.contracts.Capability
import re.pinok.contracts.NavEntry
import re.pinok.contracts.PermissionNeeds
import re.pinok.contracts.SettingsSection
import re.pinok.util.AppLog

// ---------------------------------------------------------------------
// Capability: otkryt kanal po peer_id. Realizaciya navigacii - v :app.
// ---------------------------------------------------------------------
interface ChannelOpener {
    fun openChannel(peerId: Long): Boolean
    companion object {
        const val KEY: String = "ChannelOpener"
    }
}

// ---------------------------------------------------------------------
// Kontener "Kanali". Publikuet 4 capability:
//   1. ChannelsNavEntry       - knopka "Kanali" v drawer (route = "channels_list")
//   2. ChannelsSettingsSection - vkladka nastroyek (route = "settings_channels")
//   3. ChannelsPermissions    - POST_NOTIFICATIONS (podpiska na novye posty)
//   4. ChannelsOpenerImpl     - otkrytie kanala iz yadra (lenta, profil, chat)
//
// VK API (live-damp 2026-09-14):
//   messages.getConversationsById - metadata kanala
//   messages.getItems             - posty kanala (paginaciya start_from)
//   messages.getDiff              - delta-obnovlenie lenty kanalov
//   messages.getConfig            - konfig kanalov
//   messages.getHistory           - istoriya konkretnogo kanala
//
// Hranilishche (ref. VK web localStorage namespace "reforged-storage-db-v1-<uid>-*"):
//   channels-drafts, hidden-pinned-messages, search-channel-posts-requests
// ---------------------------------------------------------------------
class ChannelsContainer(
    private val openChannelHook: (peerId: Long) -> Boolean,
) : AppContainer {

    override val id: String = "channels"

    override fun capabilities(): List<Capability> = listOf(
        ChannelsNavEntry,
        ChannelsSettingsSection,
        ChannelsPermissions,
        ChannelsOpenerImpl(openChannelHook),
    )

    override fun init(app: Application) {
        AppLog.i("ChannelsContainer", "init: kontener kanalov zaregistrirovan (id=$id)")
    }

    override fun release() {
        AppLog.i("ChannelsContainer", "release: no-op (idempotentno)")
    }
}
