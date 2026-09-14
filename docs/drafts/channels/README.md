# Черновики раздела «Каналы» (этап 1.6 #ARCH-CONTAINERS)

Присланы тестером 14.09 (kotling.zip), в сборку НЕ входят — референс для волны каналов.

- `ChannelsRepository.kt` — data-слой через веб-API VK (web.api.vk.ru/method/):
  channels.get / channels.getById / channels.getHistory / channels.getPinnedMessages.
  Содержит точные схемы ответов (ChannelInfo: unread_count, read_up_to_cmid,
  notification_settings, is_member, is_don, last_message; ChannelPost: cmid,
  текст, counters.reactions/comments/views, pinned).
- `ChannelsContainer.kt` — черновик контейнера по шаблону CallsContainer:
  4 capability (NavEntry drawer, SettingsSection, Permissions, ChannelOpener);
  документирует набор методов VK web для каналов (messages.getConversationsById /
  getItems / getDiff / getConfig / getHistory).

Статус ключевого факта: VK web читает контент каналов как СООБЩЕНИЯ (cmid),
а НЕ wall-посты → наш фолбэк Fix #394 (messages-история) каноничен, wall.get — шаткий.

ПРИМЕЧАНИЕ (CODING_STYLE): в черновиках использованы ?./?: — при реальной
реализации по NULL-EXPLICIT заменить на явные проверки.
Токен: черновик ждёт «web-token» через tokenProvider — совместимость нашего
токена с channels.* проверяет пробник #CHANNELS-PROBE (см. VKApiClient).
