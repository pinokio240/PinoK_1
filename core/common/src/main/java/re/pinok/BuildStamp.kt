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
    const val STAMP: String = "calls-2026.09.02-4"
}
