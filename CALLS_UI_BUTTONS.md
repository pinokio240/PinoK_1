# КНОПКИ ОКОН ЗВОНКОВ VK — из веб-снапшотов (папка «Новая папка»)

> Источник: HTML-снапшоты реальных звонков:
> - «Сообщения и вызовы_ входящие.html» — окно ВХОДЯЩЕГО звонка
> - «Сообщения и вызовы.html» / «Звонки…» — окно АКТИВНОГО звонка
> - «сообщения1.html» — свёрнутый виджет звонка
> - «Звонки и встречи.html» — превью/присоединение
>
> UI-фреймворк: VKUI (vkuiButton/vkuiIcon/vkuiAvatar/vkuiSimpleCell).
> Свойства кнопок: vkuiButton__sizeS, modeTertiary, appearancePositive/Negative.

---

## 1. ВХОДЯЩИЙ ЗВОНОК (панель IncomingCall)

Контейнер: `<div id="incoming_call" class="IncomingCall__panel--HBPEd" data-testid="mvk_calls_incoming_call">`

Структура:
```
IncomingCall__panel
├── IncomingCall__incomingCall (card, vkuiGroup__modeCard)
│   ├── IncomingCall__caller (vkuiSimpleCell)
│   │   ├── mvk_calls_incoming_call_caller_avatar   (vkuiAvatar)
│   │   └── mvk_calls_incoming_call_caller_name     (имя звонящего)
│   └── IncomingCall__buttonGroup (vkuiButtonGroup, modeHorizontal, gapS, stretched)
│       ├── [Отклонить]  data-testid="mvk_calls_incoming_call_btn_decline"
│       │   - иконка: vkuiIcon--cancel_24 (24px)
│       │   - класс: vkuiButton__sizeS, modeTertiary, appearanceNegative
│       │   - текст: «Отклонить»
│       └── [Принять]    data-testid="mvk_calls_incoming_call_btn_accept"
│           - иконка: vkuiIcon--phone_24 (24px)
│           - класс: vkuiButton__sizeS, modeTertiary, appearancePositive
│           - текст: «Принять»
```

| Кнопка | testid | Иконка | Размер | Цвет | Действие |
|---|---|---|---|---|---|
| Отклонить | mvk_calls_incoming_call_btn_decline | cancel_24 | 24 | appearanceNegative (красный) | hangup (отклонить) |
| Принять | mvk_calls_incoming_call_btn_accept | phone_24 | 24 | appearancePositive (зелёный) | accept-call |

---

## 2. АКТИВНЫЙ ЗВОНОК (общий: исходящий после принятия / входящий после приёма)

Контейнер: `data-testid="mvk_calls_call"`

Структура:
```
mvk_calls_call
├── header:
│   ├── mvk_calls_call_header_collapse     (свернуть)
│   ├── mvk_calls_call_header_participants (участники)
│   ├── mvk_calls_call_header_chat         (чат)
│   └── mvk_calls_call_header_settings     (настройки)
├── participants:
│   ├── mvk_calls_call_common_avatar / mvk_calls_call_common_video
│   ├── mvk_calls_call_participant_avatar
│   ├── mvk_calls_call_participant_local
│   └── mvk_calls_call_participant_name
└── footer (кнопки 28px, vkuiButton__sizeS modeTertiary):
    ├── mvk_calls_call_footer_mic_pressed   microphone_alt_28      «Включить микрофон»
    ├── mvk_calls_call_footer_camera        videocam_slash_alt_28  «Включить камеру»
    ├── mvk_calls_call_footer_link          chain_outline_28       «Ссылка»
    ├── mvk_calls_call_footer_hand          hand_28                «Поднять руку»
    └── mvk_calls_call_footer_exit          cancel_alt_outline_28  «Завершить»
```

| Кнопка | testid | Иконка | aria-label | Действие |
|---|---|---|---|---|
| Микрофон | mvk_calls_call_footer_mic_pressed | microphone_alt_28 | Включить микрофон | toggleLocalAudio / change-media-settings |
| Камера | mvk_calls_call_footer_camera | videocam_slash_alt_28 | Включить камеру | toggleLocalVideo |
| Ссылка | mvk_calls_call_footer_link | chain_outline_28 | Ссылка | скопировать join-ссылку |
| Рука | mvk_calls_call_footer_hand | hand_28 | Поднять руку | putHandsDown / requestPromotion |
| Завершить | mvk_calls_call_footer_exit | cancel_alt_outline_28 | Завершить | hangup |
| Свернуть | mvk_calls_call_header_collapse | — | — | свернуть в виджет |
| Участники | mvk_calls_call_header_participants | — | — | список участников |
| Чат | mvk_calls_call_header_chat | — | — | открыть чат |
| Настройки | mvk_calls_call_header_settings | — | — | настройки звонка |

Примечание: footer-кнопки без текста (только иконка + aria-label), size 28px.

---

## 3. СВЁРНУТЫЙ ВИДЖЕТ ЗВОНКА (активный, в фоне)

Источник: «сообщения1.html», «Звонки и встречи.html».

| Кнопка | testid | Действие |
|---|---|---|
| Микрофон | calls_call_widget_button_mic | toggleLocalAudio |
| Восстановить | calls_call_widget_button_restore | развернуть окно |
| Демонстрация экрана | calls_call_widget_button_screen_share | screen share |
| Настройки | calls_call_widget_button_settings | настройки |
| Камера | calls_call_widget_button_video | toggleLocalVideo |
| Фото (участник) | calls_call_photo_visible | фото участника |

---

## 4. ПРЕВЬЮ / ПРИСОЕДИНЕНИЕ (join)

Источник: «Звонки и встречи.html», «_ Лента новостей.html».

| Кнопка | testid | Действие |
|---|---|---|
| Камера | calls_preview_enable_cam_button | включить камеру |
| Микрофон | calls_preview_enable_mic_button | включить микрофон |
| Присоединиться | calls_preview_join_button | joinConversationByLink |
| Профиль | calls_preview_profile_button | открыть профиль |
| (anonym-варианты) | calls_preview_join_button_anonym, calls_preview_card_root_anonym, calls_preview_enable_cam_button, calls_preview_enable_mic_button | анонимный вход |

---

## 5. ИСХОДЯЩИЙ ЗВОНОК (дозвон)

Отдельного снапшота «исходящий дозвон» в папке нет — исходящий использует **тот же mvk_calls_call** 
(активный звонок) в состоянии дозвона: участник, «Завершить» (cancel_alt_outline_28), микрофон.
Инициируется кнопкой в чате: `mvk_calls_call` / `friends_call_button` (в списке друзей).

---

## 6. СООТВЕТСТВИЕ КНОПКА → КОМАНДА СИГНАЛИНГА

| Кнопка UI | Команда signaling (из calls SDK) |
|---|---|
| Принять | accept-call {mediaSettings} |
| Отклонить | hangup {reason:"declined"} |
| Завершить | hangup {reason:"hungup"} |
| Микрофон | change-media-settings {mediaSettings.isAudioEnabled} |
| Камера | change-media-settings {mediaSettings.isVideoEnabled} |
| Рука | put-hands-down / request-promotion |
