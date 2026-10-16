package re.pinok

import java.util.concurrent.atomic.AtomicBoolean

// NEWS-TIMER (2026-10-16): флаг «лента видима на экране». FeedScreen выставляет true при
// нахождении в композиции ленты, false при уходе. CommunityPostsPoller читает его и
// пропускает свои опросы, пока лента открыта — чтобы не конкурировать с лентой за
// бюджет newsfeed.get (лента и так тянет свежие новости). Thread-safe (AtomicBoolean).
object FeedVisibility {
    private val _visible = AtomicBoolean(false)
    var isVisible: Boolean
        get() = _visible.get()
        set(value) { _visible.set(value) }
}