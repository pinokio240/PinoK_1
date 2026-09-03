package re.pinok

/**
 * #CALLS-BUILD-STAMP (2026-09-01): явная метка версии звонковой логики в логе.
 *
 * ЗАЧЕМ: в логе 12:31–12:42 на одном устройстве в одном пакете (re.pinok.debug)
 * исполнялись ДВА разных кода — процесс 12728 (новая сборка 106d0281) и процесс
 * 13022 (старый APK: hangup {reason:"hungup",conversationId} lowercase с
 * CallSignalingClient.kt:266 — таких строк нет НИ В ОДНОМ коммите). Старый APK
 * отправлял hangup в формате, который сервер отвергает ("Invalid message
 * format") → официальный клиент НЕ получал сброс — «hangup не доходил».
 * VERSION_NAME/versionCode у сборок совпадали, отличить было нечем.
 *
 * ПРАВИЛО: bump-ить STAMP в КАЖДОМ коммите, меняющем звонковую цепочку
 * (формат — «calls-<дата>-<порядковый номер дня>»). Строка печатается:
 *  - в SovaApp.onCreate (старт процесса — видно сразу после открытия приложения);
 *  - в CallScreen CALL START (маркер начала каждого звонка).
 * Если в присланном логе звонка НЕТ этой метки или она устаревшая — тест
 * выполнен НЕ той сборкой, разбор проводить бессмысленно.
 */
object BuildStamp {
    // -10 (03.09) = Task 22: устранены 55 ошибок лога 2026-09-03 (все в
    // :feature:calls): (1) UserProfile git mv из :app Models.kt -> :core:data
    // (пакет re.pinok.data.model сохранён; gson в :core:data) — фасады
    // CallsApi/пользователи UserFull.user видят класс без цикла :feature->:app,
    // каскад 14 ошибок CallScreen (destructure/firstName/photo100) уходит;
    // (2) чтение LocalCallsDeps.current — @Composable-вызов — вынесено из try/
    // LaunchedEffect/DisposableEffect на compose-уровень в 7 файлах секций
    // (K2: "Try catch is not supported around composable function invocations");
    // отсутствие провайдера теперь fail-fast при композиции (замысел
    // staticCompositionLocalOf), а не глотается catch'ем;
    // (3) CallsHistoryScreen: унаследованные app.apiClient -> deps.apiClient;
    // messagesGetInboundCalls добавлен в фасад CallsApi (пропуск census Task 20)
    // + override в VKApiClient; (4) ensureCallsSessionKey() x2 -> force = false
    // (прежний дефолт SovaApp; в интерфейсе аргумент без дефолта); (5)
    // CallsWebViewScreen: RemixsidCapturer (:app, пакет-кластер auth.exchange —
    // перенос невозможен, урок Task 21) недостижим из :feature — в фасад CallsAuth
    // добавлен buildVkCookieHeader(), ExchangeAuthRepository делегирует
    // (рантайм тот же), класс доведён до ": CallsAuth" (override-маркеры Task 21
    // остались в рабочем дереве без супертипа — завершены здесь). Поведение
    // звонков не менялось; состав членов CallsDependencies НЕ сужен.
    // -9 (03.09) = Task 21: ExchangeAuthRepository возвращён в :app (пакет
    // auth.exchange — кластер 14 файлов, same-package ссылки не требуют импорта;
    // перенос одного файла = 330 unresolved), в CallsDependencies член заменён
    // фасадом CallsAuth (userId()/remixsid()). Поведение звонков не менялось.
    // -8 (03.09) = #ARCH-DATA Task 20: DI-контракт CallsDependencies (6742de6c)
    // сделан собираемым: SovaPrefs -> :core:data (новый модуль), ExchangeAuthRepository
    // -> :core:network, PermissionManager -> :feature:calls (пакеты сохранены);
    // VKApiClient/Queuev4Client/LongPollClient — фасады CallsApi/CallsQueue/CallsLongPoll
    // (перенос VKApiClient невозможен: 14k строк, импорт re.pinok.SovaApp); SovaApp
    // реализует CallsDependencies, провайдер в MainActivity.setContent. Состав членов
    // 6742de6c НЕ сужен; поведение звонков не менялось (те же объекты в рантайме).
    // -7 (02.09) = хост-хук CallStarter приведён к контракту: requestOutgoingCall
    // возвращает Boolean (pending-механизм всегда принимает — true; краш хука
    // ловит CallsStarterImpl — false). Ошибка «Return type mismatch: expected
    // 'Boolean', actual 'Unit'» (SovaApp.kt:654) — первая компиляция :app.
    // Поведение звонков не менялось. Штамп отличит эту сборку от так и не
    // собравшейся -6.
    // -6 (02.09) = фикс компиляции :feature:calls (WebRtcEngine.setIceServers): прямой
    // вызов turn.username/turn.credential на nullable-ресивере IceServer? (4 ошибки
    // «Only safe (?.) or non-null asserted (!!.) calls are allowed») заменён явной
    // null-проверкой с ранним выходом (#NULL-EXPLICIT). Поведение звонков НЕ менялось
    // — ветка guard'а формально недостижима. Ошибки существовали с initial commit,
    // но раньше не проявлялись: сборка падала на резолве зависимостей ДО компиляции
    // Kotlin (TOML-опечатка androidx-annotation, hotfix a5a26926). Штамп бамплен,
    // чтобы лог пользователя отличал исправленную сборку от так и не собравшейся -5.
    // -5 (02.09) = #ARCH-CONTAINERS Этап 1.4: хост переведён на реестр контейнеров.
    // Правлена звонковая UI-цепочка: кнопки звонка (лента/друзья/чат/история)
    // стартуют ТОЛЬКО через CallStarter (реестр; null → кнопки не рендерятся),
    // исходящая навигация доносит title/photo через OutgoingCallMeta, CallScreen
    // сам подтягивает профиль для исходящих без мета (usersGetByIds). Поведение
    // с зарегистрированным контейнером — прежнее (ровно один пункт/вкладка/кнопки).
    // -4 (02.09) = #ARCH-CONTAINERS Этап 1.3: звонковое ядро переехало в контейнер
    // :feature:calls (WebRtcEngine, VideoTextureRenderer, CallModels, CallsContainer).
    // Поведение звонков НЕ менялось (чистый перенос + регистрация контейнера в
    // реестре, UI-хардкод хоста не тронут) — штамп бамплен, чтобы звонковый лог
    // пользователя однозначно отличал сборку с контейнером от предыдущей (stamp -3).
    // -3 (02.09) = #CALLS-SERVER-REJOIN + #CALLS-ACCEPT-PCRESTART + #CALLS-ANSWER-CYCLE
    // (разбор лога ciber.txt 12:45–12:49, stamp -2): (1) topology→SERVER — bounce со СТАРЫМ
    // token давал conversation-not-found ×2 → ZOMBIE; теперь полный ре-join: свежие params
    // (getCallConversationParams → новый token) + stop/start сигналинга + повторный accept;
    // (2) accepted-call при «answer есть, ICE нет» — PC-RESTART (recreateAndReoffer) вместо
    // IceRestart на старом PC (эталон: IceRestart не делает никто); (3) дедуп answer/offer
    // по o=-строке SDP вместо булева флага — ответ на НОВЫЙ offer больше не теряется
    // (рассинхрон ufrag/pwd звонка №2), дубли того же цикла по-прежнему отсекаются;
    // (4) ZOMBIE не срабатывает в окне ре-join'а (12с), watchdog 7с→10с.
    const val STAMP: String = "calls-2026.09.03-11"
}
