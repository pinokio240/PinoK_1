# Реверс-инжиниринг: как VK web отображает «Каналы»

> Файлы: `C:\Users\Pinokio240\Desktop\Ссылки\каналы\`
> Снимок сделан с **m.vk.ru (мобильный VK / Мессенджер)**, обе HTML — один и тот же канал `-230861657` («Мир Дунхуа Аниме»), открытый через `m.vk.ru/mail/channels/-230861657?entrypoint=channel&tab=allChannels`.
> ВАЖНО: это НЕ классический vk.com-канал-группа со стеной (`wall.get`/`groups.getById`), а НОВАЯ модель «каналов» в Мессенджере с отдельным API `channels.*`. Это кардинально меняет структуру данных и вёрстку.

---

## Ключевой вывод (сначала главное)

VK web в этой фиче показывает «канал» как **подписку-на-трансляцию внутри Мессенджера** (модуль `im`, экран `im`). Пост в канале — это **сообщение, отправленное самим каналом** (автор = id канала, отрицательное число). Поэтому:

- НЕТ классической стены: не вызываются `wall.get / groups.getById / groups.getMembers`.
- Вместо них — `channels.getById`, `channels.getHistory`, `channels.getPinnedMessages`, `channels.getChannelMessagesCounters`, `channels.markAsRead`.
- Лента постов — это виртуальный список сообщений (`PostsList` + `VirtualScrollItem`), сгруппированный по дням (`PostsList__dateGroup`, `DateSeparator`).
- Каждый пост дублирует шапку канала (аватар + название), потому что это «сообщения» канала в диалоге.

---

## А. Пост в канале (карточка канала / лента)

### 1–2. Шапка поста — ЕСТЬ, в КАЖДОМ посте (не закреплена сверху)

Каждый пост в `ChannelPostWrapper` (data-testid `vkme_channel_post`) содержит:

```
ChannelPostWrapper
└─ ChannelPostHighlightWrapper (ChannelPostSecondaryHighlightWrapper)
   └─ ChannelPostSecondary (--withText)
      ├─ ChannelPostAvatar            ← АВАТАР канала (36px, BasicAvatar--size-36, кликабельный, role=button, aria-label="Мир Дунхуа Аниме")
      ├─ ChannelPostHeader
      │  ├─ ChannelPostHeader__titleWrapper  ← НАЗВАНИЕ «Мир Дунхуа Аниме» (кликабельно, role=button)
      │  └─ ChannelPostSecondary__meta
      │     └─ ChannelPostMeta
      │        ├─ ChannelPostMeta__views     ← 👁 ПРОСМОТРЫ поста: "14,2K" (icon view_12)
      │        └─ ChannelPostMeta__time      ← ВРЕМЯ: видимое "17:17" + screen-reader "8 октября в 17:17"
      ├─ ChannelPostSecondary__content (ChannelPostContentWrapper)
      │  ├─ PostText (PostText__refactored)  ← ТЕКСТ поста (br=перенос строк, ссылки .MessageText__link)
      │  └─ ChannelPostMediaAttaches         ← МЕДИА
      └─ ChannelPostSecondary__footer
         ├─ Ряд ReactionChip (емодзи-реакции с счётчиком)
         └─ Кнопка комментариев (comment_outline_16, "1 комментарий"/"17 комментариев")
└─ ChannelPostWrapper__actions (DropdownReforged)
   └─ MessageActionsButtonContainer (vkme_messages_actions)
      ├─ ⋯ more_horizontal_16 (vkme_messages_actions_button_more "Действия с сообщением")
      └─ ⟳ share_outline_16  (vkme_messages_actions_button_forward "Переслать")
```

**Аватар** (`ChannelPostAvatar`): кликабельный, 36px, ведёт на канал/профиль.
**Название** (`ChannelPostHeader__title`): кликабельное, ведёт в канал.

### 3. Кликабельность
- Название — да (role=button). Аватар — да (role=button). Оба ведут на «Мир Дунхуа Аниме».

### 4. Время поста
- **В шапке** (`ChannelPostMeta__time`): видимый формат — только время `17:17` для сегодняшних; относительное `три часа назад`, `вчера`, `6 часов назад` для старых; полная абсолютная дата (`8 октября в 17:17`) прячется в `vkuiVisuallyHidden` (для скринридера/доступности).
- Отдельно у **видео-вложения** своя строка `AttachVideos__views`: `3,4K просмотров · 3 дня назад` (относительная дата).

### 5. Футер поста
- **Реакции-емодзи** (`ReactionChip`): например `👍 4`, `🔥 1`, `❤️ 1`, `⚡ 7` — с счётчиком `AnimatedCounter`; клик по чипу = поставить реакцию.
- **Комментарии**: кнопка-ссылка с иконкой `comment_outline_16` и текстом: `1 комментарий / 17 комментариев / Комментировать`.
- **Просмотры**: НЕ в футере, а в шапке (`ChannelPostMeta__views`). В футере поста отдельного текста «N просмотров» нет — он привязан к видео (`AttachVideos__views`).

### 6. Закреплённый пост
- Один закреплённый пост показывается **отдельным баннером сверху**: `BannerPanel` → `MultiplePins` (data-testid `vkme_channel_pinned_banner`) → `MultiplePinsItem` с `data-cmid="1650"` (id закреплённого сообщения).
- Заголовок баннера: `ConvoPinnedMessage__authorLink` = **«Закреплённый пост»** (`h3.MultiplePinsItem__info`).
- Текст: превью закреплённого поста (`MultiplePinsItem__text`).
- Пагинация для нескольких пинов: `MultiplePins__pagination`.
- В самой ленте закреплённый пост НЕ дублируется (только баннер сверху).
- Пин возвращается отдельным запросом `channels.getPinnedMessages` (в нём `is_pinned=true`).

### 7. Донат-пост / подписной пост (VK Donut)
Есть ДВА варианта, оба подтверждены снипшетом:

**а) Блок-превью подписки** (если есть эксклюзивные посты): в профиле-сайдбаре канала
- `ChannelProfileVKDonutPostsPreview` : иконка пончика (SVG), title `vkuiPlaceholder__title` = «1 эксклюзивный пост в канале уже ждёт», кнопка `vkuiButton__modePrimary` = **«Открыть за 299 ₽/мес»** → `https://m.vk.ru/app7132546#/payment?source=donut_post_channel&owner_id=-225794656`.

**б) Сам донат-пост (заблокированный paywall)**: поле `cm_payload.donut` у сообщения:
- `is_donut: true`
- `placeholder`: текст «Поддержите сообщество через VK Донат…» + кнопка **«Поддержать автора»** (`action.open_url` → app7132546).
- `paywall`: `hint_text` + `snippet` { `title:"Откройте этот и другие эксклюзивные посты автора"`, `button:{title:"Поддержать за 299 ₽/мес"}`, `photos[]` (превью видео) }.

Мета канала: `donut_settings` (см. ниже) — есть ли у канала подписка, цена (`subscription_button.title`).

---

## Б. Список каналов (лента нескольких каналов)

В снипшетах единичного многоканального списка НЕТ: обе HTML-страницы — один и тот же канал `-230861657`. Однако `Каналы.html` показывает **именно ленту постов одного канала** с элементами списка:

- Каждый пост — это `PostsList__item VirtualScrollItem` с `data-itemkey` = cmid.
- Посты сгруппированы по дням: `PostsList__dateGroup` → липкий разделитель `StickyDateSeparator`/`DateSeparator` («7 октября», «8 октября»).
- В ленте НЕТ отдельного «элемента подписки» (аватар+подписчики+⋯) для самого канала — шапка канала одна, закреплена сверху (`ChannelHeader`), а каждый пост несёт свой мини-шапку. Это похоже на диалог с каналом.

Если нужен именно СПИСОК нескольких каналов (как в меню/вкладке «Каналы»), его в снапшоте нет — нужен отдельный снимок. В m.vk.ru список каналов — это обычные строки диалогов (аватар + название + счётчик непрочитанного).

---

## В. Карточка канала (метаданные)

Есть два места:

### 1. Шапка канала (закреплённая вверху ленты) — `ChannelHeader`
```
ChannelHeader (data-testid vkme_channel_*)
├─ ✕ cancel_24 (закрыть)
├─ ChannelHeader__link/ChannelHeader__avatar  ← аватар 32px (BasicAvatar--size-32), кликабельный
├─ ChannelHeader__info
│  ├─ ChannelTitle__title        ← «Мир Дунхуа Аниме» (h3)
│  ├─ ChannelTitle__icon (muted_16)  ← значок «без звука» (уведомления выкл.)
│  └─ ChannelHeader__infoSubtitle ← «76,8K подписчиков»
├─ 🔍 search_outline_24 (поиск по каналу)
└─ ⋯ more_horizontal_24 (Ещё / DropdownReforged)
```

### 2. Профиль-сайдбар канала (правая панель / карточка) — `ChannelProfile` (в «канал_карточка канала.html»)
```
ChannelProfile (data-testid vkme_channel_*)
├─ AvatarCard
│  ├─ AvatarCard__avatar  (BasicAvatar--size-80, 80px)
│  └─ AvatarCard__about
│     ├─ ChannelProfile__name («Мир Дунхуа Аниме», split на firstName/lastNameWord)
│     └─ ChannelProfile__descriptionParagraph (data-testid vkme_channel_subscribers) «76,8K подписчиков»
├─ ChannelProfileMenu__actions
│  ├─ [Сообщество]  users_3_outline_20  (data-testid vkme_channel_open_group)  ← ссылка на родительское сообщество
│  ├─ [Написать]    message_outline_20  (data-testid vkme_channel_open_convo)
│  └─ [Включить/Отключить уведомления] notification_slash_outline_20 (data-testid vkme_channel_unmute_notifications)
└─ ChannelProfileVKDonutPostsPreview  ← плашка донат-подписки (см. А.7а)
```
НЕТ отдельной «кнопки Подписаться» в этом сайдбаре, потому что пользователь уже подписан (is_member=true). Подписка/отписка = смена `user_data.is_member` и кнопки уведомлений.

---

## Ключевые данные из HAR (`channels.*` API)

### `channels.getById` — метаданные канала
POST `https://web.api.vk.ru/method/channels.getById?v=5.289&client_id=7879029`
Параметры: `channel_ids=-230861657&extended=1&fields=<длинный список, см. ниже>`

Поля объекта `response.items[0].channel` (что реально приходит):
| Поле | Значение | Смысл |
|---|---|---|
| `channel_id` | -230861657 | id канала (отрицательный) |
| `sort_id.major_id/minor_id` | 0 / 1791723785 | сортировка |
| `user_data.is_member` | true | подписан ли я |
| `user_data.is_don` | false | есть ли у меня донат-подписка |
| `user_data.is_owner` | 0 | владелец |
| `user_data.admin_level` | 0 | права администратора |
| `user_data.read_state.read_up_to_cmid` / `unread_count` | 1970 / 11 | прочитано/непрочитано |
| `user_data.can_post` / `can_post_donut` | false | могу ли постить я |
| `user_data.notification_settings.is_enabled` | false | включены ли уведомления |
| `channel_type` | "community_channel" | тип канала |
| `parent_id` | **-225794656** | родительское сообщество (группа) |
| `members_count` | 76765 | число подписчиков канала |
| `can_comment` | true | можно комментировать |
| `photo_base` | <url> | аватар канала |
| `title` | "Мир Дунхуа Аниме" | название |
| `donut_settings.is_enabled` | true | есть ли VK Donut-подписка |
| `donut_settings.subscription_button.action.url` | https://m.vk.ru/app7132546#/payment?source=donut_post_channel&owner_id=-225794656 | ссылка оплаты |
| `donut_settings.subscription_button.title` | "Открыть за 299 ₽/мес" | текст кнопки |
| `last_message` | {...} | последний пост-сообщение (см. структуру поста) |

Родительское сообщество (`groups[]` = id 225794656, т.е. группа `-225794656`): полное описание, `members_count` (143854), `screen_name=donghuaworld`, `photo_50/100/200/base`, `action_button` («Открыть сайт» → max.ru), `menu.items[]` (приложения поддержки, видеоплейлисты), `contacts` (email), `wall:2`, `site`.

### `channels.getHistory` — лента постов
POST `.../channels.getHistory?v=5.289`
Параметры: `channel_id=-230861657&start_cmid=1970&count=20&offset=-11&extended=1&fields=<тот же список>`
Ответ: `items[]` — посты (сообщения), `groups[]`, `profiles[]`.

Каждый пост (`item`):
```
{
  channel_id: -230861657,   // id канала
  cmid: 1981,               // id сообщения/поста
  author_id: -230861657,    // автор = канал (отрицательный)
  time: 1791723785,         // UNIX
  channel_type: "community_channel",
  parent_id: -225794656,
  cm_payload: {
    post_type: "post",      // тип
    counters: {
      message_id: 1981,
      reactions: { count, items: [ {id, count} ] },   // 👍🔥❤️⚡ по id
      comments:  { can_post, can_view, count, groups_can_post },  // настройки комментариев
      views: { count }                               // число просмотров поста
    },
    can_edit, marked_as_ads, track_code, owner_id,
    // опционально:
    donut: {...},           // paywall/placeholder для донат-поста (см. А.7б)
  },
  text: "...",              // текст поста (может быть пустым)
  attachments: [ { type: "video|photo|doc|...", ... } ], // вложения
  is_pinned: false,         // закреплён ли
  guid, mute_notifications
}
```

### `channels.getPinnedMessages` — закреплённые
POST `.../channels.getPinnedMessages?v=5.289`
Параметры: `channel_id=-230861657&extended=1&fields=...`
Ответ: `items[]`, где `is_pinned=true`. Тот же формат, что и history.

### `channels.markAsRead` — отметка прочитанного
POST `.../channels.markAsRead?v=5.289`
Параметры: `channel_id=-230861657&last_read_cmid=<N>` (тот самый cmid из read_state).

### `channels.getChannelMessagesCounters`, `channels.getOwnersForCreate`
- Счётчики непрочитанного по каналам.
- `getOwnersForCreate` — список сообществ, от имени которых можно создавать каналы (для авторов). Не используется при чтении.

### Поля `fields`, запрашиваемые у пользователей/групп (все копируют профиль сообщества):
`id, first_name, ..., photo_50, photo_100, photo_200, screen_name, city, online_info, verified, followers_count, contacts, image_status, photo_base, name, type, members_count, member_status, is_closed, can_message, deactivated, activity, is_messages_blocked, can_send_notify, can_post_donut, site, reposts_disabled, description, action_button, menu, role, unread_count, wall, can_manage, age_limits, warning_notification, ...`

---

## Чем пост в канале отличается от обычного поста группы/профиля (в этой модели)

Так как это Мессенджер, пост в канале — это СИМВОЛ сообщения, а не wall-пост:

| Аспект | Обычный пост группы (vk.com wall) | Пост в канале (m.vk.ru channels) |
|---|---|---|
| Источник данных | `wall.get` → items[].text/attachments | `channels.getHistory` → items[] |
| Автор | любой участник | всегда сам канал (`author_id = channel_id`) |
| Шапка поста | автор+группа/репост | всегда аватар+название канала (36px) + мета (просмотры, время) |
| Лайк | кнопка Like | **емодзи-реакции** (ReactionChip с счётчиком) |
| Комментарии | action-кнопка | кнопка с текстом «N комментариев» |
| Репост | «Поделиться» в actions | кнопка «Переслать» (share_outline_16) |
| Просмотры | как правило в футере | в шапке (`ChannelPostMeta__views`) + у видео отдельно |
| Закреп | Пины внутри ленты | **баннер сверху** (MultiplePins) |
| Группировка | нет | по дням (`DateSeparator`) |
| Донат-пост | плашка доната в теле | `cm_payload.donut.paywall.snippet` + профильный блок |
| Кнопка ⋯ (more) | меню поста | `more_horizontal_16` «Действия с сообщением» |
| Скриншот | — | есть |

Сожаление: этой модели совершенно НЕТ схемы `groups.getById`/стены, значит для переноса в PinoK это отдельный тип данных «чатовый канал», а не «групповой пост».

---

## Прямые рекомендации для PinoK (парсинг снапшота → приложение)

Исходя из данных, если переносить «как на вебе»:

1. **Модель данных**: завести отдельную сущность `Channel` (не WallPost). Минимальный набор полей канала:
   - `channel_id` (отрицательный), `title`, `photo_base`, `members_count`, `parent_id` (группа), `channel_type`, `user_data{is_member, is_don, unread_count}`, `donut_settings{is_enabled, subscription_button{title, action.url}}`, `last_message`.
   - Родительское сообщество: `groups[]` (name, photo, description, screen_name).

2. **Модель поста**: `ChannelPost` с полями:
   - `cmid`, `author_id` (всегда канал), `time` (unix), `is_pinned`, `text`, `attachments[]` (video/photo/doc по `type`).
   - `counters.views.count` → просмотры; `counters.reactions.items[{id,count}]` → реакции; `counters.comments.count` → число комментариев.
   - `cm_payload.donut` → признак эксклюзивного/донат-поста (показывать paywall/placeholder вместо контента).

3. **Шапка поста в списке**: рисуется в каждом посте: аватар 36px (`ChannelPostAvatar`) + название канала + `views` + время. Не зависеть от «одной шапки на весь список».

4. **Время**: показывать относительное для старых постов (`N часов/дней назад`, `вчера`), абсолютное — при нажатии/в аргументе (`8 октября в 17:17`). Для сегодняшних — только HH:MM.

5. **Футер**: ряд реакций-емодзи с количеством (по топ-4 из `reactions.items`, сортируя по count) + кнопка комментариев с числом.

6. **Закреплённый пост**: при наличии `getPinnedMessages` показывать баннер сверху «Закреплённый пост» (заголовок + превью текста); `is_pinned=true` в history учитывать.

7. **Донат/подписка**: если `donut_settings.is_enabled` и не `is_don` — показывать блок-превью подписки (весь профиль), а эксклюзивные посты — через paywall (`donut.paywall.snippet`) вместо текста/медиа, если пользователь не подписан.

8. **Открытие поста/медиа**: видео вложение хранит `video` с `files.mp4_*` превью и ссылку `z=video-{owner}_{vid}/{hash}`; для плеера можно использовать `cm_payload.counters/comments`.

9. **Аватар/название кликабельны** → переход на канал/сообщество (`parent_id`), т.е. навигация по `-parent_id` как по группе.

10. Показывать подписчиков канала как `members_count` с форматированием K/M («76,8K подписчиков»).

---

*Примечание: весь анализ сделан по снапшотам. Значения времени/счётчиков соответствуют дате снимка (2026-10-11). Классы VKUI/«vkit-*» обфусцированы и вряд ли стабильны между релизами — для рендера в PinoK опираться на data-testid (`vkme_channel_post`, `vkme_channel_pinned_banner`, `vkme_channel_subscribers`) и на поля JSON-ответов, а не на CSS-классы.*