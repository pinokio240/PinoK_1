# EQUALIZER_INTEGRATION_PLAN.md — План внедрения Equalizer в PinoK

> **Источник:** декомпиляция `Equalizer v6.3.5.7` (см. `reference/equalizer/`)
> **Цель:** перенести продвинутые audio-эффекты в PinoK, расширив текущий
> `EqualizerHelper` (только legacy `Equalizer`) до полного набора:
> BassBoost + Virtualizer + LoudnessEnhancer + PresetReverb + DynamicsProcessing
> + spectrum visualizer + custom presets (Room) + auto-apply per device.

---

## Текущее состояние PinoK

### Что уже есть

| Компонент | Файл | Что делает |
|-----------|------|------------|
| `EqualizerHelper` | `media/EqualizerHelper.kt` | Legacy `Equalizer` только (5-10 band, ±dB) |
| `EqualizerPreset` | `data/model/EqualizerPreset.kt` | Data class с preset'ами (Normal/Pop/Rock/Jazz/Classical/Bass/Treble) |
| Audio session ID | `PlayerService` (ExoPlayer) | `AudioManager.generateAudioSessionId()` once, fixed для всех треков |
| UI: EQ button + BottomSheet | `AudioPlayerScreen.kt:551, 674-690` | Кнопка + bottom sheet с ползунками bands |
| Persistence | `SharedPreferences("equalizer")` | enabled, preset name, bands CSV |

### Чего нет (gap analysis)

| Возможность | Статус |
|-------------|--------|
| **BassBoost** | ❌ |
| **Virtualizer** | ❌ |
| **LoudnessEnhancer** | ❌ |
| **PresetReverb** | ❌ |
| **DynamicsProcessing** (API 28+) | ❌ |
| **Spectrum visualizer** (Canvas) | ❌ |
| **Custom presets (Room DB)** | ❌ (только фиксированный список) |
| **Auto-apply per audio device** | ❌ |
| **Global Mix режим (session 0)** | ❌ |
| **Foreground service** (effects alive вне app) | ❌ |
| **Per-band on/off** | ❌ (только master switch) |
| **L/R channel balance** | ❌ |

---

## План внедрения — 5 этапов

### Этап 1: AudioEffectsEngine — единый движок (1-2 дня)

**Цель:** заменить `EqualizerHelper` на новый `AudioEffectsEngine`, который
поддерживает все 6 эффектов.

#### 1.1. Новый класс `AudioEffectsEngine` (`media/AudioEffectsEngine.kt`)

```kotlin
package re.pinok.media

import android.media.audiofx.*
import re.pinok.util.AppLog

/**
 * Полный движок audio-эффектов для PinoK.
 * Поддерживает 6 эффектов одновременно на одну audio session:
 *   - Equalizer (legacy, API 9+)
 *   - BassBoost (API 9+)
 *   - Virtualizer (API 9+)
 *   - PresetReverb (API 9+)
 *   - LoudnessEnhancer (API 19+)
 *   - DynamicsProcessing (API 28+, advanced: pre-EQ + post-EQ + limiter)
 *
 * Паттерн из Equalizer v6.3.5.7: legacy Equalizer + DynamicsProcessing
 * работают ПАРАЛЛЕЛЬНО для совместимости. На API <28 DP недоступен →
 * legacy берёт всё на себя.
 *
 * Thread-safety: все методы synchronized. attach/release идемпотентны.
 */
class AudioEffectsEngine(
    private val sessionId: Int,  // ExoPlayer audio session, fixed в PlayerService
) {
    @Volatile private var equalizer: Equalizer? = null
    @Volatile private var bassBoost: BassBoost? = null
    @Volatile private var virtualizer: Virtualizer? = null
    @Volatile private var reverb: PresetReverb? = null
    @Volatile private var loudness: LoudnessEnhancer? = null
    @Volatile private var dynamicsProcessing: DynamicsProcessing? = null  // API 28+

    @Volatile private var attached = false

    fun attach() {
        if (attached) return
        try {
            equalizer = Equalizer(0, sessionId).also { it.enabled = false }
            bassBoost = BassBoost(0, sessionId).also { it.enabled = false }
            virtualizer = Virtualizer(0, sessionId).also { it.enabled = false }
            reverb = PresetReverb(0, sessionId).also { it.enabled = false }
            if (Build.VERSION.SDK_INT >= 19) {
                loudness = LoudnessEnhancer(sessionId).also { it.enabled = false }
            }
            if (Build.VERSION.SDK_INT >= 28) {
                val config = DynamicsProcessing.Config.Builder(
                    0, 1, true, 1, false, 0, true, 5, true
                ).build()
                dynamicsProcessing = DynamicsProcessing(Int.MAX_VALUE, sessionId, config)
                    .also { it.enabled = false }
            }
            attached = true
        } catch (e: Exception) {
            AppLog.e("AudioEffectsEngine", "attach failed: ${e.message}")
            release()
        }
    }

    fun release() {
        listOf(equalizer, bassBoost, virtualizer, reverb, loudness, dynamicsProcessing)
            .forEach { runCatching { it?.release() } }
        equalizer = null; bassBoost = null; virtualizer = null
        reverb = null; loudness = null; dynamicsProcessing = null
        attached = false
    }

    // ─── Equalizer (legacy) ─────────────────────────────────────
    fun setEqEnabled(on: Boolean) { equalizer?.enabled = on }
    fun setBandLevel(band: Short, level: Short) {
        equalizer?.setBandLevel(band, level)
    }
    fun getNumberOfBands(): Short = equalizer?.numberOfBands ?: 0
    fun getCenterFreq(band: Short): Int = equalizer?.getCenterFreq(band) ?: 0
    fun getBandLevelRange(): ShortArray = equalizer?.bandLevelRange ?: shortArrayOf(-1500, 1500)

    // ─── BassBoost ──────────────────────────────────────────────
    fun setBassBoostEnabled(on: Boolean) { bassBoost?.enabled = on }
    fun setBassBoostStrength(strength: Short) {  // 0..1000
        bassBoost?.setStrength(strength)
    }

    // ─── Virtualizer ────────────────────────────────────────────
    fun setVirtualizerEnabled(on: Boolean) { virtualizer?.enabled = on }
    fun setVirtualizerStrength(strength: Short) {  // 0..1000
        virtualizer?.setStrength(strength)
    }

    // ─── PresetReverb (6 presets: 0=None,1=LargeRoom,2=MediumRoom,
    //     3=SmallRoom,4=LargeHall,5=MediumHall,6=Plate) ──────────
    fun setReverbEnabled(on: Boolean) { reverb?.enabled = on }
    fun setReverbPreset(preset: Short) { reverb?.setPreset(preset) }

    // ─── LoudnessEnhancer (API 19+) ─────────────────────────────
    fun setLoudnessEnabled(on: Boolean) { loudness?.enabled = on }
    fun setLoudnessTargetGain(gainmB: Int) {  // millibels, 0..1500 (≈ +15 dB)
        loudness?.setTargetGain(gainmB)
    }

    // ─── DynamicsProcessing (API 28+, advanced) ─────────────────
    fun setDynamicsProcessingEnabled(on: Boolean) {
        if (Build.VERSION.SDK_INT >= 28) dynamicsProcessing?.enabled = on
    }
    // Pre-EQ, Post-EQ, Limiter — тонкая настройка для power users
    fun setPreEqBandGain(bandIndex: Int, gainDb: Float) {
        if (Build.VERSION.SDK_INT < 28) return
        val dp = dynamicsProcessing ?: return
        val eq = dp.getPreEqByChannelIndex(0)
        val band = eq.getBand(bandIndex)
        band.gain = gainDb
        dp.setPreEqAllChannelsTo(eq)
    }
    // ... аналогично PostEq, Limiter

    // ─── Visualizer (для spectrum) ──────────────────────────────
    // Отдельный API: android.media.audiofx.Visualizer
    fun createVisualizer(): Visualizer? { /* см. Этап 3 */ }
}
```

#### 1.2. Миграция `EqualizerHelper` → `AudioEffectsEngine`

- `PlayerService.onCreate`: создать `engine = AudioEffectsEngine(sessionId)`
- `Player.Listener.onAudioSessionIdChanged`: НЕ пересоздаём (fixed session)
- `EqualizerHelper` → **deprecated** facade, делегирует в engine (обратная
  совместимость с UI)
- UI постепенно переводится на новый API

#### 1.3. Persistence — расширить SharedPreferences → Room (Этап 4)

Временно — расширить `SharedPreferences("equalizer")`:
- `eq_bass_strength` (int 0-1000)
- `eq_virtualizer_strength` (int 0-1000)
- `eq_loudness_gain` (int mB)
- `eq_reverb_preset` (int 0-6)
- `eq_bass_enabled`, `eq_virtualizer_enabled`, `eq_loudness_enabled`, `eq_reverb_enabled`
- `eq_dp_enabled` (DynamicsProcessing master switch)

---

### Этап 2: Расширенный UI (1-2 дня)

**Цель:** заменить текущий EQ BottomSheet на полноэкранный экран
`EqualizerScreen` с вкладками.

#### 2.1. Полноэкранный `EqualizerScreen` (`ui/screens/music/EqualizerScreen.kt`)

Layout (Compose):
```
┌──────────────────────────────────────────┐
│ ← Эквалайзер              [⚙ настройки]   │  ← TopAppBar
├──────────────────────────────────────────┤
│ [Пресеты] [Полосы] [Bass/Virt] [Reverb] [Loud]  ← TabRow (5 вкладок)
├──────────────────────────────────────────┤
│                                          │
│          Контент вкладки                 │  ← AnimatedContent
│                                          │
├──────────────────────────────────────────┤
│ [▶ Превью]  Master Switch: [Вкл/Выкл]   │  ← BottomBar
└──────────────────────────────────────────┘
```

#### 2.2. Вкладки

| Вкладка | Контент |
|---------|---------|
| **Пресеты** | Список встроенных + custom. Tap → apply. Long-press → edit/delete (для custom). FAB → «Сохранить как preset» |
| **Полосы EQ** | 5-10 вертикальных `MidSeekBar` (Canvas), center=0, ±15 dB. Per-band on/off switch (DynamicsProcessing). Band labels: 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz |
| **Bass/Virt** | 2 `ArcSeekBar` (дуговые ползунки 0-1000). Switch для каждого |
| **Reverb** | 6 пресетов (None/LargeRoom/MediumRoom/SmallRoom/LargeHall/MediumHall) — radio buttons + Switch |
| **Loudness** | `Slider` 0-15 dB (target gain). Switch |

#### 2.3. Spectrum Visualizer сверху (Этап 3)

В Header'е — `Canvas` с real-time спектром, который рисуется через
`Visualizer.OnDataCaptureListener`. Визуально даёт обратную связь —
«полосы двигаются» при изменении EQ.

---

### Этап 3: Spectrum Visualizer (1 день)

**Цель:** real-time FFT-визуализатор как в Equalizer v6.3.5.7 (`Curve.smali`).

#### 3.1. `SpectrumVisualizer` Composable (`ui/components/SpectrumVisualizer.kt`)

```kotlin
@Composable
fun SpectrumVisualizer(
    sessionId: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val bars = remember { mutableStateListOf<Float>() }  // 32 bars, 0..1

    DisposableEffect(sessionId) {
        val visualizer = Visualizer(sessionId)
        visualizer.captureSize = Visualizer.getCaptureSizeRange()[1]
        visualizer.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
            override fun onWaveFormData(visualizer: Visualizer?, waveform: ByteArray?) {}
            override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?) {
                fft?.let { computeBars(it, bars) }  // 32 bars из 256-byte FFT
            }
        }, Visualizer.getMaxCaptureRate() / 2, true, true)
        visualizer.enabled = true
        onDispose { visualizer.release() }
    }

    Canvas(modifier) {
        // Рисуем 32 вертикальные полосы с плавной интерполяцией
        // (Path + cubicTo как в Curve.smali)
        val barWidth = size.width / bars.size
        bars.forEachIndexed { i, v ->
            val h = size.height * v
            drawRoundRect(
                color = color.copy(alpha = 0.3f + 0.7f * v),
                topLeft = Offset(i * barWidth, size.height - h),
                size = Size(barWidth * 0.8f, h),
                cornerRadius = CornerRadius(4f, 4f),
            )
        }
    }
}
```

---

### Этап 4: Custom Presets (Room DB) (1-2 дня)

**Цель:** пользователь может сохранить свои пресеты и переиспользовать.

#### 4.1. Schema (`prisma/schema.prisma` — НЕ, используем Room для Android)

PinoK использует **Room** (как и Equalizer v6.3.5.7), не Prisma.

```kotlin
// data/local/AudioPresetDao.kt
@Entity(tableName = "audio_preset")
data class AudioPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val eqEnabled: Boolean,
    val bands: String,                    // JSON: [short, short, ...] mB per band
    val bassEnabled: Boolean,
    val bassStrength: Int,                // 0-1000
    val virtualizerEnabled: Boolean,
    val virtualizerStrength: Int,         // 0-1000
    val loudnessEnabled: Boolean,
    val loudnessGain: Int,                // mB
    val reverbEnabled: Boolean,
    val reverbPreset: Int,                // 0-6
    val dpEnabled: Boolean,               // DynamicsProcessing master
    val channelBalance: Float,            // -1.0..+1.0 (L/R)
    val createdAt: Long,
    val isBuiltIn: Boolean = false,
)

@Dao
interface AudioPresetDao {
    @Query("SELECT * FROM audio_preset ORDER BY isBuiltIn DESC, name ASC")
    fun observeAll(): Flow<List<AudioPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preset: AudioPresetEntity): Long

    @Delete
    suspend fun delete(preset: AudioPresetEntity)

    @Query("SELECT * FROM audio_preset WHERE id = :id")
    suspend fun getById(id: Long): AudioPresetEntity?
}

// auto-apply per device
@Entity(tableName = "audio_preset_device")
data class AudioPresetDeviceEntity(
    @PrimaryKey val audioDeviceId: Int,   // AudioDeviceInfo.id
    val presetId: Long,
)
```

#### 4.2. Сидируем встроенные пресеты (как в Equalizer v6.3.5.7)

```kotlin
val BUILT_IN_PRESETS = listOf(
    AudioPresetEntity(name="Normal", eqEnabled=true, bands="[0,0,0,0,0]",
        bassEnabled=false, bassStrength=0, virtualizerEnabled=false,
        virtualizerStrength=0, loudnessEnabled=false, loudnessGain=0,
        reverbEnabled=false, reverbPreset=0, dpEnabled=false,
        channelBalance=0f, isBuiltIn=true),
    AudioPresetEntity(name="Pop", bands="[−1,−1,0,2,4]", ...),
    AudioPresetEntity(name="Rock", bands="[4,2,0,−1,−2]", ...),
    AudioPresetEntity(name="Jazz", bands="[3,2,−1,2,3]", ...),
    AudioPresetEntity(name="Classical", bands="[4,3,0,2,3]", ...),
    AudioPresetEntity(name="Bass Boost", bands="[6,4,0,0,0]",
        bassEnabled=true, bassStrength=600, ...),
    AudioPresetEntity(name="Treble Boost", bands="[0,0,0,4,6]", ...),
    AudioPresetEntity(name="Vocal", bands="[−2,0,4,4,0]", ...),
)
```

#### 4.3. UI: «Сохранить как preset»

FAB в `EqualizerScreen` вкладки «Пресеты» → диалог ввода имени →
сохранение текущего состояния engine как новый `AudioPresetEntity`.

---

### Этап 5: Auto-apply per device + Foreground Service (2 дня, опционально)

**Цель:** эффекты работают вне приложения и авто-переключаются при
подключении bluetooth/wired наушников.

#### 5.1. `AudioDeviceObserver` (`media/AudioDeviceObserver.kt`)

```kotlin
class AudioDeviceObserver(private val context: Context) {
    private val am = context.getSystemService(AudioManager::class.java)

    fun observe(onDeviceChanged: (AudioDeviceInfo?) -> Unit) {
        // registerAudioDeviceCallback — listens для wired/bt/speaker change
        am.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                val active = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .firstOrNull { it.isSink }
                onDeviceChanged(active)
            }
            override fun onAudioDevicesRemoved(removed: Array<AudioDeviceInfo>) {
                onDeviceChanged(am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .firstOrNull { it.isSink })
            }
        }, null)
    }
}
```

При смене устройства → DAO `audio_preset_device.findByDeviceId(id)` →
`engine.applyPreset(preset)`.

#### 5.2. Foreground Service `AudioFxService` (`media/AudioFxService.kt`)

**Важно:** PinoK уже имеет `PlayerService` (foreground для музыки). Эффекты
привязаны к session этого же service — отдельный FGS **не нужен**, если
эффекты работают только во время воспроизведения в PinoK.

**Global Mix режим (опционально)** — если хотим эффекты на внешние плееры
(Spotify, Yandex Music и т.п.):
- Создать `AudioFxService` (foregroundServiceType=specialUse) с
  `intent-filter` на `OPEN_AUDIO_EFFECT_CONTROL_SESSION`
- Регистрировать `SessionReceiver` для прослушивания внешних session
- Привязывать effects к session внешнего плеера

**Решение:** для PinoK Global Mix **не нужен** — PinoK сам музыкальный
плеер, его effects работают только на собственную audio session. Это
упрощает архитектуру и не требует FGS-permission.

---

## Этапы и приоритеты

| Этап | Срок | Приоритет | Зависимости |
|------|------|-----------|-------------|
| **1. AudioEffectsEngine** | 1-2 дня | P0 (must) | — |
| **2. Расширенный UI** | 1-2 дня | P0 (must) | Этап 1 |
| **3. Spectrum Visualizer** | 1 день | P1 (nice) | Этап 1 |
| **4. Custom Presets (Room)** | 1-2 дня | P1 (nice) | Этап 1 |
| **5. Auto-apply + FGS** | 2 дня | P2 (optional) | Этап 4 |

**MVP = Этапы 1+2** (4 дня) → пользователь получает BassBoost +
Virtualizer + LoudnessEnhancer + Reverb + расширенный UI.

---

## Риски и митигация

| Риск | Митигация |
|------|-----------|
| **AudioEffect init crash** на некоторых устройствах (Xiaomi/Huawei custom ROMs) | try-catch вокруг каждого `new Equalizer(...)`; `attach()` не падает если один эффект не создался |
| **DynamicsProcessing недоступен** на API <28 | Проверка `Build.VERSION.SDK_INT >= 28`; legacy Equalizer берёт всё на себя |
| **Virtualizer не работает на speaker** (только headphones) | Документировать; `forceVirtualizationMode(VIRTUALIZATION_MODE_AUTO)` |
| **PresetReverb ломает звук** на некоторых устройствах | Default off; switch в UI с предупреждением «может не работать на всех устройствах» |
| **Equalizer + DynamicsProcessing конфликт** (оба меняют частоты) | Если DP enabled → EQ legacy выключаем (они дублируют); UI показывает либо одно, либо другое |
| **Performance** — Visualizer + 6 AudioEffect на каждый трек | Visualizer только при открытом UI; effects создаются once для fixed sessionId (PinoK уже так делает) |
| **Battery** — LoudnessEnhancer + BassBoost непрерывно | Master switch в UI; при выключенном audio эффекты release'ятся |

---

## Что НЕ переносим (см. также `reference/equalizer/README.md`)

- **AdMob / AppLovin / Facebook ads** — монетизация
- **Singular / AppMetrica analytics** — аналитика
- **In-App Billing** — PinoK бесплатный
- **PremiumHelper SDK** — троян-обёртка монетизации
- **Firebase Messaging** — у нас свой push (VK LongPoll)
- **Global Mix режим** — PinoK сам плеер, не нужен
- **Theme chooser** — Material3 dynamic color уже есть

---

## Файлы (план)

### Новые файлы

| Файл | Этап |
|------|------|
| `media/AudioEffectsEngine.kt` | 1 |
| `media/AudioDeviceObserver.kt` | 5 |
| `data/local/AudioPresetDao.kt` | 4 |
| `data/local/AudioPresetDatabase.kt` | 4 |
| `data/model/AudioPreset.kt` (расширить существующий) | 4 |
| `ui/screens/music/EqualizerScreen.kt` | 2 |
| `ui/components/SpectrumVisualizer.kt` | 3 |
| `ui/components/MidBandSlider.kt` (Compose MidSeekBar analog) | 2 |
| `ui/components/ArcSlider.kt` (Compose ArcSeekBar analog) | 2 |

### Изменяемые файлы

| Файл | Изменение |
|------|-----------|
| `media/EqualizerHelper.kt` | Deprecated, делегирует в `AudioEffectsEngine` |
| `media/PlayerService.kt` | Создаёт `AudioEffectsEngine` в onCreate, attach к session |
| `ui/screens/music/AudioPlayerScreen.kt` | Кнопка EQ → навигация на `EqualizerScreen` (вместо BottomSheet) |
| `data/model/EqualizerPreset.kt` | Расширить полями: bassStrength, virtualizerStrength, loudness, reverb, bands[] |
| `AndroidManifest.xml` | Добавить `MODIFY_AUDIO_SETTINGS` (уже есть?) |

---

## Acceptance criteria (Этапы 1+2 — MVP)

1. ✅ В `AudioPlayerScreen` кнопка EQ открывает полноэкранный экран
2. ✅ На экране 5 вкладок: Пресеты / Полосы / Bass+Virt / Reverb / Loudness
3. ✅ BassBoost: slider 0-1000, switch — звук меняется в реальном времени
4. ✅ Virtualizer: slider 0-1000, switch — звук меняется
5. ✅ LoudnessEnhancer: slider 0-15 dB, switch — звук меняется
6. ✅ PresetReverb: 6 пресетов + switch — звук меняется
7. ✅ Equalizer: 5-10 полос ±15 dB, master switch
8. ✅ Все настройки сохраняются и восстанавливаются при перезапуске
9. ✅ При смене трека настройки НЕ сбрасываются (fixed sessionId)
10. ✅ На API 19+ работает LoudnessEnhancer; на API 28+ — DynamicsProcessing;
    на старых — legacy Equalizer

---

## Источник

- Декомпил: `reference/equalizer/` (apktool smali + jadx Java)
- Манифест: `reference/equalizer/apktool_manifest_strings/AndroidManifest.xml`
- AudioEffect manager: `reference/equalizer/apktool_ye_smali/ye/m0*.smali`
- Service: `reference/equalizer/apktool_jazibkhan_smali/jazibkhan/equalizer/services/MainForegroundService.smali`
- Структура БД: извлечена из SQL `CREATE TABLE` в `AppDatabase*.smali`
