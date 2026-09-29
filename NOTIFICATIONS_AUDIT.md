# Аудит вкладки «Уведомления» (NotificationsTab)

Дата: 2026-09-29. Файл: app/src/main/java/re/pinok/ui/screens/settings/SettingsScreen.kt, NotificationsTab (6307-7430).

## 1. Цель
Проверка каждого UI-элемента: вызов, ответ, смена свойства при вкл/выкл, читатель префа.

## 2. Элементы и потребители

| # | Элемент | Строка | Setter | Читатель |
|---|---------|--------|--------|----------|
| 1 | Отключить звук | 6563 | setMsgMute | ChatDetailScreen/ChatInfoScreen |
| 2 | Мастер push | 6678 | setPushEnabled | NotificationsPoller:48, SovaApp:302 |
| 3 | Лайки | 6687 | setPushLikes | NotificationsPoller:243, SnNotifyFilter:57 |
| 4 | Комментарии | 6696 | setPushComments | NotificationsPoller:244 |
| 5 | Ответы | 6705 | setPushReplies | NotificationsPoller:245 |
| 6 | Подписки | 6714 | setPushFollows | NotificationsPoller:246 |
| 7 | Упоминания | 6723 | setPushMentions | NotificationsPoller:247 |
| 8 | Репосты | 6732 | setPushReposts | NotificationsPoller:248 |
| 9 | Записи на стене | 6741 | setPushWall | NotificationsPoller:249 |
| 10 | Подарки | 6750 | setPushGifts | NotificationsPoller:250 |
| 11 | Прочее | 6759 | setPushOther | NotificationsPoller:251 |
| 12 | Интервал fallback | 6786 | setPushPollingIntervalSec | SovaApp:2248, NotificationsPoller:33 |
| 13 | От сообществ | 6816 | setPushFromCommunities | SnNotifyFilter:58, VkNotificationsNotifier:308 |
| 14 | От пользователей | 6825 | setPushFromUsers | SnNotifyFilter:58 |
| 15 | Группировка | 6939 | setPushGroupingMode | SovaApp:991, VkNotificationsNotifier:406 |
| 16 | Порог N | 6966 | setPushGroupThreshold | VkNotificationsNotifier |
| 17 | Режим превью | 7006 | setPushPreviewMode | VkNotificationsNotifier:43 |
| 18 | Длина превью | 7027 | setPushPreviewLength | VkNotificationsNotifier |
| 19 | Аватар | 7042 | setPushShowAvatar | VkNotificationsNotifier:55 |
| 20 | BigPicture | 7051 | setPushShowBigPicture | VkNotificationsNotifier:60 |
| 21 | Режим уведомлений | 7097 | setNotifyMode | SovaApp:1365, VkNotificationsNotifier:376 |
| 22 | Звук сообществ | 7154 | setNotifyCommunitiesSound | SovaApp:1526, MessageNotifier:128 |
| 23 | Вибро сообществ | 7163 | setNotifyCommunitiesVibration | SovaApp:1530 |
| 24 | Авто-скрытие | 7193 | setPushAutoDismissMs | VkNotificationsNotifier:67 |
| 25 | Звук | 7208 | setPushSoundEnabled | VkNotificationsNotifier:784 |
| 26 | Вибрация | 7217 | setPushVibrationEnabled | VkNotificationsNotifier:789 |
| 27 | Кнопки действий | 7226 | setPushActionButtons | VkNotificationsNotifier:71 |
| 28 | Кнопка ответа | 7237 | setPushReplyButton | VkNotificationsNotifier |
| 29 | Quiet hours вкл | 7249 | setPushQuietHoursEnabled | VkNotificationsNotifier |
| 30 | Quiet hours старт | 7278 | setPushQuietHoursStart | VkNotificationsNotifier |
| 31 | Quiet hours конец | 7293 | setPushQuietHoursEnd | VkNotificationsNotifier |

## 3. Серверные (BFF)
- toggleNotify()->settingsGeneralToggleNotify; optimistic+revert+Toast (6345-6372).
- Загрузка settingsGeneralGetNotifySettings("notify") при старте (6388-6438).
- Без устройства не проверяемо.

## 4. Не беспокоить (silent)
- accountGetSilentModeStatus/Start/Stop (6379,6498,6519) — реальные API.

## 5. Дубли (двойной set)
- setNotifyMode: 7097+7104.
- setVideoSeekStepSec: 2929+2934.
- setVideoPreferredQuality: 3906+3913.

## 6. План проверки на устройстве
Для каждого элемента §2: вкл->лог потребителя->ответ; выкл->смена поведения; перезапуск->persist; серверные->success/revert+Toast.

## 7. Итог
- 31 клиентский элемент привязаны к prefs, читатели найдены.
- Дублей пунктов меню нет. Заглушек UI нет.
