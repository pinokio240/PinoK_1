package re.pinok.feature.audio

import android.app.Application
import re.pinok.contracts.AppContainer
import re.pinok.contracts.AttachmentRenderer
import re.pinok.contracts.Capability
import re.pinok.contracts.NavEntry
import re.pinok.contracts.PermissionNeeds
import re.pinok.contracts.SettingsSection
import re.pinok.util.AppLog

/**
 * #ARCH-CONTAINERS (Этап 1.5-б): контейнер «Аудио» (плеер/эквалайзер —
 * вводные: музыка.md, EQUALIZER_INTEGRATION_PLAN.md).
 *
 * Публикует 4 capability (реестр → хост):
 *  1. [AudioNavEntry]           — кнопка «Эквалайзер» в боковой панели (route
 *     "equalizer" — СОВПАДАЕТ с бывшим ядерным пунктом drawer: Screen.Equalizer;
 *     NavHost-destination и экран EqualizerScreen остаются в хосте :app —
 *     блокер EqualizerFeatureFlags/CustomPresetStore/AudioEffectsEngine, см.
 *     контейнеры.план.md, Этап 1.5-б);
 *  2. [AudioSettingsSection]    — вкладка настроек «Эквалайзер» (route
 *     "settings_audio" — новый стабильный маршрут по образцу "settings_calls";
 *     контент рендерит ХОСТ: компосабл EqualizerTab остаётся в :app —
 *     EqualizerFeatureFlags читает SovaApp.get() и EqualizerHelper, которые
 *     фиче-модулю недоступны);
 *  3. [AudioAttachmentRenderer] — рендер аудио-вложений чата (rendererKey
 *     "audio_inline"): хост мапит ключ на компосабл [AudioInlineRenderer]
 *     (этот же модуль) и передаёт данные/колбэки; ВОЙС-вложения НЕ публикуются
 *     (VoiceMessageBubble тянет data.model.Attachment.Doc + VoicePlaybackController
 *     :app — ветка voice осталась host-inline, реестр для неё не спрашивается);
 *  4. [AudioPermissions]        — MODIFY_AUDIO_SETTINGS (эквалайзер; normal-
 *     permission, декларативно). RECORD_AUDIO НЕ публикуем по фактам: запись
 *     войсов — функция чата хоста (ChatDetailScreen + VoiceRecorder из
 *     :core:media), BLUETOOTH_CONNECT — маршрутизация PlayerService (:app);
 *     оба запрашиваются хостом независимо от контейнера.
 *
 * Раздел «Музыка» Dock (Screen.Music) — собственность ЯДРА (правило владения
 * UI): NavEntry на него НЕ публикуется. Ядерная вкладка настроек «Музыка»
 * (MusicTab: качество API-запросов/MP3, авто-загрузка) тоже осталась в :app —
 * её тумблеры потребляются заблокированным data-слоем (apiClient/
 * PlayerConnection/TrackDownloadManager), перенос ownership вкладки без домена
 * = осколок (решение разведки 1.5-б, см. worklog Task 13).
 *
 * init/release — минимальные/идемпотентные: рендер и экраны живут циклом
 * композиции хоста, состояние контейнеру не нужно (эквалайзер-движки —
 * синглтоны :app с собственным циклом).
 */
class AudioContainer : AppContainer {

    override val id: String = "audio"

    override fun capabilities(): List<Capability> = listOf(
        AudioNavEntry,
        AudioSettingsSection,
        AudioAttachmentRenderer,
        AudioPermissions,
    )

    override fun init(app: Application) {
        // Минимум на 1.5-б: состояния у контейнера нет (рендер — чистая функция
        // данных хоста; экран EqualizerScreen и вкладка настроек — в :app).
        // Нечего инициализировать.
        AppLog.i("AudioContainer", "init: контейнер аудио зарегистрирован (id=$id)")
    }

    override fun release() {
        // Идемпотентный no-op: ресурсов контейнер не держит (эффекты
        // эквалайзера принадлежат PlayerService/AudioEffectsEngine в :app).
        AppLog.i("AudioContainer", "release: no-op (идемпотентно)")
    }
}

/**
 * Кнопка «Эквалайзер» в drawer. route = "equalizer" — прежний route ядерного
 * пункта (Screen.Equalizer); host-маппинг hostDestinationForRoute("equalizer")
 * → Screen.Equalizer, destination в NavHost хоста остаётся (открывается и из
 * плеера — onOpenFullEqualizer — независимо от контейнера).
 * order=30 — после «Звонки»(10) и «Фото»(20).
 */
private object AudioNavEntry : NavEntry {
    override val title: String = "Эквалайзер"
    override val iconKey: String = "equalizer"
    /** После «Фото» (20); хост сортирует контейнерные пункты по order. */
    override val order: Int = 30
    override val route: String = "equalizer"
}

/**
 * Вкладка настроек «Эквалайзер» (бывшая ядерная SettingsTab.EQUALIZER — тумблеры
 * видимости эффектов). route — новый стабильный ключ (см. KDoc контейнера).
 * order=80 — контейнерные секции рендерятся после ядерных вкладок; до «Звонки»(90).
 */
private object AudioSettingsSection : SettingsSection {
    override val title: String = "Эквалайзер"
    override val order: Int = 80
    override val route: String = "settings_audio"
}

/**
 * Рендер аудио-вложений чата (контракт [AttachmentRenderer], без androidx/compose).
 *
 * mime-таблица по ФАКТАМ хоста (разведка 1.5-б): хост-хук в ChatDetailScreen
 * спрашивает canHandle для mime-семейства audio-звёздочка (audio + слэш-звёздочка)
 * и kind="audio" — mime у VK-аудио-вложений messages.get отсутствует, передаётся
 * семейство. Дополнительно принимаем конкретные audio-типы (задел для будущих
 * вызовов с реальным mime). ВНИМАНИЕ: литерал audio-слэш-звёздочка НЕ писать
 * в block-комментариях — он открывает вложенный комментарий Kotlin (ловушка,
 * пойманная сканером скобок на 1.5-а). kind="voice"/"video" — НЕ берём:
 * ветки VoiceMessageBubble/VideoAttachmentCard остались в :app, хост для них
 * реестр не спрашивает — false claim дал бы регресс на заглушку.
 */
object AudioAttachmentRenderer : AttachmentRenderer {
    private val AUDIO_MIMES = setOf(
        "audio/mpeg", "audio/mp3", "audio/ogg", "audio/aac",
        "audio/mp4", "audio/flac", "audio/wav", "audio/x-wav",
    )

    override fun canHandle(mimeType: String, kind: String): Boolean {
        if (kind != "audio") return false
        val mime = mimeType.trim().lowercase()
        return mime == "audio/*" || mime == "*/*" || mime in AUDIO_MIMES
    }

    /** Стабильный ключ для host-маппинга (ChatDetailScreen.hostAudioRendererComposable). */
    override val rendererKey: String = "audio_inline"

    /** Первый среди рендереров своего типа (photos — order 10 другого kind). */
    override val order: Int = 10
}

/**
 * Права аудио-домена: эквалайзеру нужен MODIFY_AUDIO_SETTINGS (normal —
 * декларируется в манифесте хоста, runtime-запроса нет; см. комментарий
 * манифеста: без него EqualizerHelper.setEnabled молча fails на ряде OEM).
 * Опасные разрешения аудио-домена хост запрашивает сам: RECORD_AUDIO —
 * запись войсов в чате (ChatDetailScreen), BLUETOOTH_CONNECT — BT-маршрутизация
 * PlayerService. Публикуем как декларативную метадату (потребители — Этап 2+).
 */
private object AudioPermissions : PermissionNeeds {
    override val permissions: List<String> = listOf(
        android.Manifest.permission.MODIFY_AUDIO_SETTINGS,
    )
}
