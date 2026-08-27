# Equalizer v6.3.5.7 (build 345) — Декомпилированный исходник

> **Источник:** `Equalizer v6.3.5.7 (345).apk` (16.1 MB)
> **Package:** `com.jazibkhan.equalizer`
> **Module name (Kotlin metadata):** `flat-equalizer-v6.3.5.7_release`
> **compileSdk:** 35 (Android 15)
> **Min SDK:** предположительно 21+ (DynamicsProcessing требует 28+)
> **Поддерживаемые плееры:** Spotify, Yandex Music, YouTube Music, VK,
> Apple Music, Deezer, AIMP, VLC, RetroMusic, Gaana, Resso, BlackPlayer и др.
> (полный список — 32 пакета в AndroidManifest.xml `<package>` тегами)

---

## Структура каталога

```
reference/equalizer/
├── README.md                      ← этот файл
├── apktool_jazibkhan_smali/       ← smali код приложения (46 файлов)
│   └── jazibkhan/equalizer/
│       ├── MyApplication.smali
│       ├── AppDatabase*.smali     ← Room DB (custom_preset, auto_apply_config)
│       ├── receivers/
│       │   ├── BootCompleteReceiver.smali   ← auto-start on boot
│       │   └── SessionReceiver.smali        ←android.media.action.OPEN_AUDIO_EFFECT_CONTROL_SESSION
│       ├── services/
│       │   ├── MainForegroundService.smali  ← ЯДРО: AudioEffect init + apply
│       │   ├── SessionChangeService.smali   ← JobService: handles session events
│       │   └── SessionChangeWorker.smali
│       ├── ui/activities/          ← Activities (Compose, обфусцированы)
│       └── views/                  ← Custom Views
│           ├── Curve.smali         ← Spectrum visualizer (Canvas)
│           ├── MidSeekBar.smali    ← Band slider (center=0, ±dB)
│           ├── ArcSeekBar.smali    ← Arc slider (bass/virtualizer)
│           └── JSwitch.smali       ← Material Switch wrapper
├── apktool_ye_smali/               ← smali AudioEffect-менеджера (24 файла)
│   └── ye/
│       ├── m0.smali                ← AudioEffectManager (singleton)
│       ├── m0$a.smali              ← BassBoost wrapper
│       ├── m0$b.smali              ← (utility)
│       ├── m0$c.smali              ← Equalizer wrapper (legacy)
│       ├── m0$d.smali              ← LoudnessEnhancer wrapper
│       ├── m0$e.smali              ← PresetReverb wrapper
│       ├── m0$f.smali              ← Virtualizer wrapper
│       ├── m0$f$a, m0$f$b          ← Virtualizer inner helpers
│       ├── a0.java ... z.java      ← DynamicsProcessing synthetic helpers
│       └── ...
├── apktool_manifest_strings/       ← Манифест + strings
│   ├── AndroidManifest.xml         ← полный манифест
│   ├── apktool.yml                 ← apktool metadata
│   └── strings.xml                 ← UI strings
└── jadx_sources/jazibkhan/equalizer/ ← Java-декомпил (R8 обфусцировал)
    └── (R.java + few views — основные классы в обфусцированных пакетах a/a0/...)
```

**APK не включён** в репозиторий (16 MB). Исходный артефакт:
`/home/z/my-project/upload/Equalizer+v6.3.5.7+(345).apk`

---

## Что было извлечено (функции и паттерны)

### 1. AudioEffect API — 6 эффектов одновременно

Приложение использует **6 различных AudioEffect** одновременно на одну
audio session:

| Эффект | Android API | Класс-обёртка | Методы |
|--------|-------------|---------------|--------|
| **Equalizer** (legacy) | `android.media.audiofx.Equalizer` | `ye/m0$c` | `setBandLevel(short, short)`, `setEnabled`, `release` |
| **BassBoost** | `android.media.audiofx.BassBoost` | `ye/m0$a` | `setStrength(short)`, `setEnabled`, `release` |
| **Virtualizer** | `android.media.audiofx.Virtualizer` | `ye/m0$f` | `setStrength(short)`, `forceVirtualizationMode`, `setEnabled`, `release` |
| **PresetReverb** | `android.media.audiofx.PresetReverb` | `ye/m0$e` | `setPreset(short)`, `setEnabled`, `release` |
| **LoudnessEnhancer** | `android.media.audiofx.LoudnessEnhancer` | `ye/m0$d` | `setTargetGain(int)`, `setEnabled`, `release` |
| **DynamicsProcessing** (modern, API 28+) | `android.media.audiofx.DynamicsProcessing` | `ye/m0` (static `i`) | `setPreEqAllChannelsTo`, `setPostEqAllChannelsTo`, `setLimiterAllChannelsTo`, `EqBand.setGain`, `EqBand.setCutoffFrequency`, `Limiter.setPostGain`, `setEnabled`, `release` |

**Двойная обработка** для совместимости: legacy Equalizer (API 9+) + modern
DynamicsProcessing (API 28+) работают параллельно. На старых устройствах
DynamicsProcessing недоступен → legacy Equalizer берёт всё на себя.

### 2. Audio session привязка

```kotlin
// Псевдокод из MainForegroundService.smali (строки 1071-1470)
val sessionId = intent.getIntExtra("android.media.extra.AUDIO_SESSION", 0)
val packageName = intent.getStringExtra("android.media.extra.PACKAGE_NAME")

equalizer = Equalizer(priority, sessionId)        // session-specific
bassBoost = BassBoost(priority, sessionId)
virtualizer = Virtualizer(priority, sessionId)
reverb = PresetReverb(priority, sessionId)
loudness = LoudnessEnhancer(sessionId)
dynamicsProcessing = DynamicsProcessing(MAX_VALUE, sessionId, config)  // static global
```

**3 режима session:**
1. **Session-specific** — эффекты привязаны к конкретной audio session
   внешнего плеера (через `OPEN_AUDIO_EFFECT_CONTROL_SESSION` broadcast).
2. **Global Mix (session 0)** — эффекты на global output mix. Работает не
   на всех устройствах, но даёт эффект для ВСЕХ источников звука.
3. **Auto-detect** — `SessionChangeService` слушает `AudioManager` и
   автоматически re-attach'ит эффекты к новой session при переключении
   плеера.

### 3. Intent-фильтры для системной интеграции

```xml
<!-- AndroidManifest.xml -->
<activity android:name=".ui.activities.MainActivity">
    <intent-filter>
        <action android:name="android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL"/>
        ...
    </intent-filter>
</activity>

<receiver android:name=".receivers.SessionReceiver">
    <!-- Ловит OPEN_AUDIO_EFFECT_CONTROL_SESSION / CLOSED_AUDIO_EFFECT_CONTROL_SESSION -->
</receiver>

<receiver android:name=".receivers.BootCompleteReceiver">
    <!-- Auto-start MainForegroundService на boot -->
</receiver>

<service android:name=".services.MainForegroundService"
         android:foregroundServiceType="specialUse">
    <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
              android:value="...AudioEffect class..."/>
</service>
```

Система Android сама вызывает `MainActivity` через
`android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL` — это стандартный
intent, который отправляют музыкальные плееры при тапе на кнопку «EQ» в
своём UI. Приложение регистрируется как обработчик → перехватывает →
открывает свой экран.

### 4. Presets — Room DB структура

`AppDatabase` (Room) хранит пользовательские пресеты в таблице
`custom_preset`:

```sql
CREATE TABLE custom_preset (
    preset_name       TEXT NOT NULL,        -- имя пресета
    vir_slider        INTEGER NOT NULL,     -- Virtualizer strength (0-1000)
    bb_slider         INTEGER NOT NULL,     -- BassBoost strength (0-1000)
    loud_slider       REAL NOT NULL,        -- LoudnessEnhancer target gain (mB)
    slider            TEXT NOT NULL,        -- JSON: [band1, band2, ...] в mB
    spinner_pos       INTEGER NOT NULL,     -- Reverb preset index (0-5)
    vir_switch        INTEGER NOT NULL,     -- Virtualizer enabled (0/1)
    bb_switch         INTEGER NOT NULL,     -- BassBoost enabled (0/1)
    loud_switch       INTEGER NOT NULL,     -- LoudnessEnhancer enabled (0/1)
    eq_switch         INTEGER NOT NULL,     -- Equalizer enabled (0/1)
    is_custom_selected INTEGER NOT NULL,    -- active preset flag
    reverb_switch     INTEGER NOT NULL,     -- Reverb enabled (0/1)
    reverb_slider     INTEGER NOT NULL,     -- Reverb level
    channel_bal_slider REAL NOT NULL DEFAULT 0,  -- L/R balance (-1.0..+1.0)
    channel_bal_switch INTEGER NOT NULL DEFAULT 0, -- balance enabled
    id                INTEGER PRIMARY KEY AUTOINCREMENT
);
```

**Auto-apply per device:**

```sql
CREATE TABLE auto_apply_config (
    audio_device_id   INTEGER NOT NULL,     -- AudioDeviceInfo.id
    custom_preset_id  TEXT NOT NULL,        -- FK to custom_preset
    PRIMARY KEY(audio_device_id)
);
```

Когда пользователь подключает bluetooth-наушники → автоматически
применяется preset, привязанный к этому устройству.

### 5. Audio device routing

`ze/b` enum: `SPEAKER`, `HEADPHONES`, `BLUETOOTH`. При смене устройства
через `AudioManager.getDevices()` → `MainForegroundService.a(state)` →
apply preset for this device.

### 6. UI Components (Custom Views)

| View | Назначение | Compose-аналог для PinoK |
|------|------------|---------------------------|
| `Curve` | Спектр-анализатор (Canvas, Path, onDraw) | `Canvas` + `Visualizer` API |
| `MidSeekBar` | Band slider с центром=0 (±dB) | `Slider` с valueRange `-1500..1500` |
| `ArcSeekBar` | Дуговой слайдер для bass/virtualizer | `Canvas` + `Modifier.pointerInput` |
| `JSwitch` | Material Switch (enable/disable effect) | `Switch` (Material3) |

### 7. Permissions

```xml
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS"/>  ← AudioEffect
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>  ← FGS для AudioEffect
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>  ← auto-start
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>  ← foreground notification
<uses-permission android:name="android.permission.WAKE_LOCK"/>  ← keep effects alive
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>  ← ads/analytics
```

---

## Что НЕ переносим из исходника

| Функция | Почему |
|---------|-------|
| **AdMob / AppLovin / Facebook ads** | Монетизация — не нужна в PinoK |
| **Singular analytics / AppMetrica** | Аналитика — не нужна |
| **In-App Billing (Premium unlock)** | PinoK бесплатный |
| **Firebase Messaging / Sessions** | Push — у нас свой (VK LongPoll) |
| **PremiumHelper SDK** (`com.zipoapps.premiumhelper`) | Троян-обёртка монетизации |
| **Backup/Restore в Google Drive** | У нас свой бэкап (account.json) |
| **Theme chooser** | У нас Material3 dynamic color |
| **Connected device list** (отдельный экран) | Auto-apply достаточно |

---

## Источник

- APK: `Equalizer v6.3.5.7 (345).apk`
- Декомпиляторы: `apktool 2.x` (smali + resources), `jadx 1.5.0` (Java)
- Полный размер декомпила: 313 MB (apktool) + 88 MB (jadx) — в репо
  включены только нужные части (2.2 MB).
