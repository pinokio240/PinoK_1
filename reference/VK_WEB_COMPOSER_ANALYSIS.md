# VK Web — композер сообщений и голосовые: разбор CSS/JS/DOM

Источник: сохранённая страница `Мессенджер_голосовое сообщение.html`
(3.7 МБ, 87 ассетов). CSS-файл `5a4c23f6f0e103c4.css` (содержит все
`ConvoComposer` / `VoiceRecording` / `ComposerInput` / `DropdownReforged`
правила). JS-бандлы: `b-483d721ddc25ecc0.1b4f6c9815645490.js`
(парсинг `audio_message`), `b-6f3cdbf7820e551a.4bc8cb81d1da6bb0.js`
(`messages.send`), `b-226df83bda86a954.f6794a047b370a4f.js` (API-namespace).

Захваченный стейт — **«ревью перед отправкой»**: запись уже есть (0:04),
показаны cancel / resume / play / waveform / duration / send.

---

## 1. Ветвь классов (DOM-дерево композера)

```
div.ConvoMain__composerWrapper
├── div.ConvoMain__historyUnreadWrapper                 (абсолютно позиционир., скрыт по умолч.)
│   └── div.ConvoHopNavigation.ConvoMain__historyUnread (кнопка «непрочитанные ниже»)
│       └── div.DropdownReforged.DropdownReforged--closed
│           └── div.DropdownReforged__trigger           (пустой триггер-обёртка)
└── div.ConvoMain__composer
    └── div.ConvoMain__composerContent.ConvoComposer    (фон + тень + radius 12)
        └── div.ConvoComposer__inputPanel               (flex-row, padding 0 6px)
            ├── div.ConvoComposer__remove-record        (cancel записи)
            │   └── button.ConvoComposer__button.ConvoComposer__buttonIcon--removeRecord[aria-label="Отменить"]
            │       └── i.ConvoComposer__buttonIcon > svg.cancel_outline_24
            ├── div.ComposerInput.ConvoComposer__inputWrapper.ConvoComposer__inputWrapper--hidden[role=presentation]
            │   ├── span.ComposerInput__placeholder.ConvoComposer__inputPlaceholder  "Сообщение"
            │   └── div[role=presentation]
            │       └── span.ComposerInput__input.ConvoComposer__input.ComposerInput__input--fixed
            │           [contenteditable][role=textbox][aria-multiline][aria-label="Сообщение"]
            ├── div.ConvoComposer__voice                (виден только в voice-режиме)
            │   └── div.VoiceRecording                  (CSS-grid: 'icon track duration')
            │       ├── div.VoiceRecording__buttons     (grid-area: icon)
            │       │   ├── button.ConvoComposer__button.ConvoComposer__buttonIcon--startRecording
            │       │   │   [aria-label="Продолжить запись"]
            │       │   │   └── i.ConvoComposer__buttonIcon > svg.microphone_16
            │       │   └── button.VoiceRecording__play.VoiceRecording__play--withMargin
            │       │       [aria-label="Прослушать голосовое сообщение перед отправкой"]
            │       │       └── i.Composer__buttonIcon > svg.play_16
            │       ├── div.VoiceRecording__track       (grid-area: track)
            │       │   └── svg.VoiceRecording__svg.VoiceRecording__svg--shadow[width=340][height=21][stroke-width=2]
            │       │       └── path.VoiceRecording__progress  (d="M1,10v1Z M5,10v1Z …" — вертикальные столбики)
            │       └── div.VoiceRecording__duration[style="width:4ch"]  "0:04"
            └── div.DropdownReforged.DropdownReforged--closed   (send-button обёрнут в dropdown)
                └── div.DropdownReforged__trigger
                    └── button.ConvoComposer__button.ConvoComposer__sendButton--submit
                        [aria-label="Отправить сообщение"]
                        ├── i.ConvoComposer__buttonIcon.ConvoComposer__buttonIcon--submit > svg.send_24
                        ├── div.OnboardingTooltip__target
                        ├── i.ConvoComposer__buttonIcon.ConvoComposer__buttonIcon--mic > svg.voice_outline_24
                        └── i.ConvoComposer__buttonIcon.ConvoComposer__buttonIcon--delete > svg.delete_outline_24
```

---

## 2. Классы — BEM-разбор (Block__Element--Modifier)

| Класс | BEM-роль | Тип узла | Назначение |
|---|---|---|---|
| `ConvoMain__composerWrapper` | Block__Element | div | контейнер композера, flex-center |
| `ConvoMain__historyUnreadWrapper` | Block__Element | div | обёртка «unread below» (absolute, hidden) |
| `ConvoMain__historyUnread` | Block__Element | div | кнопка перемотки к непрочитанным |
| `ConvoHopNavigation` | Block | div | хоп-навигация по сообщениям |
| `ConvoMain__composer` | Block__Element | div | padding-контейнер |
| `ConvoMain__composerContent` | Block__Element | div | визуальная карточка (фон/тень/radius) |
| `ConvoComposer` | Block | div | корень композера (flex-column) |
| `ConvoComposer__inputPanel` | Block__Element | div | строка с кнопками+инпутом (flex, padding 0 6px) |
| `ConvoComposer__remove-record` | Block__Element | div | cancel-зона (видна в voice-режиме) |
| `ConvoComposer__button` | Block__Element | button | базовая кнопка (36×36, opacity .72) |
| `ConvoComposer__buttonIcon` | Block__Element | i | обёртка svg (30×30, absolute-center) |
| `ConvoComposer__buttonIcon--removeRecord` | --Modifier | (на button) | cancel-кнопка записи |
| `ConvoComposer__buttonIcon--startRecording` | --Modifier | (на button) | resume-запись (красный кружок 24×24) |
| `ConvoComposer__buttonIcon--stopRecording` | --Modifier | (на button) | stop-запись |
| `ConvoComposer__buttonIcon--submit` | --Modifier | i | иконка send (scale 0→1 когда активна) |
| `ConvoComposer__buttonIcon--mic` | --Modifier | i | иконка mic (alt-action) |
| `ConvoComposer__buttonIcon--delete` | --Modifier | i | иконка delete (alt-action) |
| `ConvoComposer__sendButton--submit` | --Modifier | button | синяя/акцентная кнопка отправки |
| `ComposerInput` | Block | div | contenteditable-инпут (font, overflow-auto) |
| `ConvoComposer__inputWrapper` | Block__Element | div | обёртка инпута (margin 0 6px) |
| `ConvoComposer__inputWrapper--hidden` | --Modifier | div | `display:none` в voice-режиме |
| `ComposerInput__placeholder` | Block__Element | span | плейсхолдер «Сообщение» (absolute) |
| `ConvoComposer__inputPlaceholder` | Block__Element | span | доп.класс плейсхолдера (padding 12px 0) |
| `ComposerInput__input` | Block__Element | span | сам contenteditable (font inherit, break-spaces) |
| `ConvoComposer__input` | Block__Element | span | доп.класс инпута (margin 12px 0, min/max-height) |
| `ComposerInput__input--fixed` | --Modifier | span | фиксированная высота строки |
| `ConvoComposer__voice` | Block__Element | div | контейнер voice-блока (w100%, h36, flex-center) |
| `VoiceRecording` | Block | div | grid: 'icon track duration' / 32px, bg-accent, radius 20 |
| `VoiceRecording__buttons` | Block__Element | div | grid-area:icon, flex (mic + play) |
| `VoiceRecording__play` | Block__Element | button | круглая play-кнопка 24×24 (invert) |
| `VoiceRecording__play--withMargin` | --Modifier | button | margin 4px 0 4px 8px |
| `VoiceRecording__track` | Block__Element | div | grid-area:track, h21px, relative |
| `VoiceRecording__svg` | Block__Element | svg | waveform-canvas (h21, transition clip-path 1s) |
| `VoiceRecording__svg--shadow` | --Modifier | svg | absolute, top/left 0 (слой прогресса) |
| `VoiceRecording__progress` | Block__Element | path | stroke-bars прогресса (clip-path anim) |
| `VoiceRecording__path` | Block__Element | path | stroke-bars фона (opacity .4 когда active) |
| `VoiceRecording__duration` | Block__Element | div | grid-area:duration, font 12/16, "0:04" |
| `DropdownReforged` | Block | div | универсальный dropdown (flex, align-center) |
| `DropdownReforged--closed` | --Modifier | div | закрытое состояние |
| `DropdownReforged__trigger` | Block__Element | div | триггер-обёртка (flex, touch-callout none) |
| `DropdownReforged__contentWrapper` | Block__Element | div | портал-контент (opacity/transform transitions) |
| `DropdownReforged__portal--bottomSheet` | --Modifier | (portal) | mobile bottom-sheet (overlay + max-w 362) |

---

## 3. Типы узлов

| Тип | Где | Роль |
|---|---|---|
| `div` | структурные контейнеры | layout (flex/grid/absolute) |
| `button.ConvoComposer__button` | все икночные кнопки | action-trigger (36×36) |
| `span[contenteditable]` | `ComposerInput__input` | текстовый ввод (не textarea!) |
| `span` (plain) | placeholder | absolute-overlay «Сообщение» |
| `i[role=img]` | `ConvoComposer__buttonIcon` | svg-wrapper (vkuiIcon) |
| `svg` | waveform + icons | векторная графика |
| `path` | `VoiceRecording__progress` | waveform-bars (`d="M{x},10v1Z"`) |
| `div.OnboardingTooltip__target` | внутри send | tooltip-якорь |

---

## 4. CSS — ключевые правила (из `5a4c23f6f0e103c4.css`)

### Layout композера
```css
.ConvoMain__composerWrapper { position:relative; width:100%; display:flex; justify-content:center; }
.ConvoMain__composer { width:100%; max-width:var(--convoMainMaxWidth);
    padding:0 var(--convoMainComposerPaddingSide) var(--convoMainComposerPaddingBottom); }
.ConvoMain__composerContent { background-color:var(--composerBackgroundColor);
    border-radius:12px; box-shadow:var(--vkui--elevation3); }
.ConvoComposer { position:relative; display:flex; flex-direction:column; width:100%; flex:1 1 auto; }
.ConvoComposer__inputPanel { display:flex; padding:0 6px; align-items:flex-end; position:relative; }
```

### Инпут (contenteditable, НЕ textarea)
```css
.ComposerInput { position:relative; width:100%; overflow:auto;
    font:400 var(--messageFontSize)/var(--messageLineHeight) var(--font); }
.ComposerInput__input { display:block; position:relative; font:inherit; width:100%;
    flex:1 1 auto; overflow:auto; background-color:transparent; cursor:text;
    white-space:pre-wrap; white-space:break-spaces; }
.ConvoComposer__input { margin:12px 0; min-height:var(--messageLineHeight);
    max-height:calc(12 * var(--messageLineHeight)); }   /* максимум 12 строк */
.ConvoComposer__inputWrapper--hidden { display:none; }   /* скрывается в voice-режиме */
.ComposerInput__placeholder { position:absolute; left:0; top:0; font:inherit;
    color:var(--vkui--color_text_secondary); user-select:none; }
```

### Кнопки (36×36, opacity-анимация)
```css
.ConvoComposer__button { display:flex; justify-content:center; align-items:center;
    position:relative; border:0; background:none; color:var(--vkui--color_icon_medium);
    cursor:pointer; opacity:0.72; height:36px; min-width:36px; width:36px; margin:4px 0; }
.ConvoComposer__button:hover { opacity:1; }
.ConvoComposer__button:disabled { opacity:0.5; cursor:not-allowed; }
.ConvoComposer__sendButton--submit { color:var(--vkui--color_icon_accent); }
.ConvoComposer__buttonIcon { position:absolute; display:flex; justify-content:center;
    align-items:center; height:30px; top:var(--offset); left:0; right:0; bottom:var(--offset); }

/* Все alt-иконки скрыты (scale 0), активная — scale 1 */
.ConvoComposer__buttonIcon--submit, .ConvoComposer__buttonIcon--mic,
.ConvoComposer__buttonIcon--delete, .ConvoComposer__buttonIcon--loading /* … */ {
    opacity:0; transform:scale(0); transition:transform 0.2s; }
.ConvoComposer__sendButton--submit .ConvoComposer__buttonIcon--submit { opacity:1; transform:scale(1); }

/* Mic-record кнопки — красный кружок 24×24 */
.ConvoComposer__buttonIcon--startRecording, .ConvoComposer__buttonIcon--stopRecording {
    opacity:1; color:var(--vkui--color_icon_negative);
    background-color:var(--vkui--color_text_contrast);
    min-width:24px; width:24px; height:24px; border-radius:50%; padding:0;
    transition:all 0.2s ease; }
```

### VoiceRecording (grid-layout!)
```css
.VoiceRecording { --svgHeight:21px; display:grid;
    grid-template:'icon track duration' 32px / min-content auto min-content;
    align-items:center; grid-gap:10px;
    background-color:var(--vkui--vkontakte_im_toolbar_voice_msg_background);
    width:100%; border-radius:20px; padding-left:4px; padding-right:12px; }
.VoiceRecording__buttons { grid-area:icon; display:flex; }
.VoiceRecording__track { grid-area:track; height:var(--svgHeight); position:relative; }
.VoiceRecording__svg { display:block; height:var(--svgHeight); max-height:var(--svgHeight);
    max-width:100%; transition:clip-path 1s ease-out; }   /* прогресс через clip-path! */
.VoiceRecording__svg--shadow { position:absolute; top:0; left:0; }
.VoiceRecording__duration { grid-area:duration; font:12px/16px var(--font);
    color:var(--vkui--color_text_contrast); }
.VoiceRecording__play { display:flex; justify-content:center; align-items:center;
    position:relative; width:24px; height:24px; border:0; border-radius:50%;
    color:var(--vkui--vkontakte_im_toolbar_voice_msg_background);
    background-color:var(--vkui--color_text_contrast); cursor:pointer; }
.VoiceRecording__play--withMargin { margin:4px 0 4px 8px; }
.VoiceRecording__path, .VoiceRecording__progress { stroke-linejoin:round; stroke-linecap:round;
    fill:none; stroke:var(--vkui--color_text_contrast); }
.VoiceRecording--active .VoiceRecording__path { stroke:var(--vkui--color_text_contrast); opacity:0.4; }
.VoiceRecording--active .VoiceRecording__path,
.VoiceRecording--active .VoiceRecording__progress { transition:clip-path 0.25s linear; }
```

### DropdownReforged
```css
.DropdownReforged { display:flex; align-items:center; }
.DropdownReforged__trigger { display:flex; -webkit-touch-callout:none; }
.DropdownReforged__contentWrapper { opacity:1; visibility:visible;
    transform:translateY(0); pointer-events:auto; }
.DropdownReforged__contentWrapper--appear { pointer-events:none; transform:translateY(5px); opacity:0; }
.DropdownReforged__contentWrapper--appear-active { opacity:1; transform:translateY(0);
    transition:opacity 0.14s linear 0.1s, transform 0.14s linear 0.1s; }
.DropdownReforged__portal--bottomSheet::before { position:fixed; content:''; width:200vw;
    height:200vh; background-color:rgba(0,0,0,0.4); left:-100vw; top:-100vh; }
.DropdownReforged__portal--bottomSheet .DropdownReforged__contentWrapper { max-width:362px; margin:0 auto; }
```

---

## 5. JS — поток голосового сообщения (reverse-engineered)

### 5.1. Парсинг входящих `audio_message` (b-483d…js @ 153227)
```js
case "doc":
  if ("audiomsg" === e[`attach${t}_kind`]) {
    const r = e.attachments ? JSON.parse(e.attachments)[0] : {};
    n.push({ type: e[`attach${t}_type`], kind: "audio_message",
             id: e[`attach${t}`], audio_message: r.audio_message });
    break;
  }
```
→ VK присылает голосовое как `type="audio_message"` + поле `audio_message`
(НЕ как `doc` с `audio_msg`). Это два разных формата, оба надо поддерживать.

### 5.2. Полный pipeline отправки (канонический VK flow)
```
1. POST  docs.getMessagesUploadServer?type=audio_message&peer_id=…   → upload_url
2. POST  {upload_url}   multipart/form-data, field "file"=<ogg/opus>  → { file: "<token>" }
3. POST  docs.save?file=<token>&title=voice.ogg
        → { response: { type:"audio_message",
                         audio_message: { id, owner_id, access_key, duration,
                                          link_mp3, link_ogg, waveform:[…] } } }
4. POST  messages.send?peer_id=…&attachment=audio_message{owner_id}_{id}_{access_key}
        (VK также принимает attachment=doc{owner_id}_{id}_{access_key})
        → { response: { items:[{message_id}], ... } }
```

### 5.3. Запись (MediaRecorder, web)
```js
// Feature-detect (b-636f…js @ 3118):
navigator.mediaDevices.getUserMedia + RTCPeerConnection + RTCIceCandidate + RTCSessionDescription
// → MediaRecorder (audio/webm;codecs=opus), ondataavailable → chunks, onstop → Blob → upload
```

### 5.4. Waveform
`path.VoiceRecording__progress` строится из массива `waveform:[…]` (VK возвращает
~200 значений 0..1) → `d="M{x},10v{h}Z"` для каждого столбика. Прогресс
воспроизведения — `clip-path` на `VoiceRecording__svg--shadow` (transition 1s).

---

## 6. Методы (VK API)

| Метод | Назначение |
|---|---|
| `docs.getMessagesUploadServer` | `?type=audio_message&peer_id=…` → upload_url |
| (multipart upload) | POST ogg на upload_url → `{file}` |
| `docs.save` | `?file=<token>&title=…` → `audio_message{owner_id,id,access_key,duration,link_ogg,link_mp3,waveform}` |
| `messages.send` | `?peer_id=…&attachment=doc{o}_{id}_{key}&random_id=…` → message_id |
| `photos.getMessagesUploadServer` | `?peer_id=…` → upload_url (для фото в ЛС) |
| (multipart upload) | POST jpg/png, field `photo` → `{server,photo,hash}` |
| `photos.saveMessagesPhoto` | `?server=&photo=&hash=` → `[{id,owner_id,access_key}]` |
| `messages.send` | `?peer_id=…&attachment=photo{o}_{id}_{key}` |

---

## 7. Сопоставление с Android-приложением (что было сломано)

| VK Web | Android (до фикса) | Фикс |
|---|---|---|
| `docs.save` → `response.audio_message.{id,owner_id}` | `docsSave()` делал `getAsJsonObject("type")` — но `type` это **строка**, Gson бросал ClassCastException → `docsSave` **всегда** возвращал null для audio_message → `sendVoiceMessage` молча падал на шаге 3 | `docsSave()` теперь читает строку `type` и берёт вложенный объект `audio_message`/`doc`/`graffiti` |
| `VoiceRecording__buttons` (mic+play+waveform+duration) | UI есть (`VoiceMessageBubble`), но отправка не работала | pipeline починен, добавлен пошаговый лог |
| `messages.send` переживает навигацию | `rememberCoroutineScope()` отменял upload при уходе с экрана (`LeftCompositionCancellationException`) | upload теперь в `SovaApp.appScope` (SupervisorJob + IO), UI-обновления — в композиционном scope |

**Итог разборки:** голосовые НЕ отправлялись из-за бага парсинга `docs.save`
(поле `type` — строка, а не объект). Фото-прикрепление структурно верно,
но upload отменялся при навигации — теперь тоже в `appScope`.
