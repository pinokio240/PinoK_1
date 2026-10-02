package re.pinok.realtime

import re.pinok.api.VKApiClient

/**
 * §42.3 #NOTIFY-LEAK2 — единый источник истины для классификации
 * «уведомление от сообщества».
 *
 * Раньше showBatch (fromCommunity) и SnNotifyFilter (sn_groups) использовали
 * РАЗНЫЕ предикаты, из-за чего фильтр и гейт notifyMode рассинхронивались:
 *   - showBatch считал сообществом: parentOwnerId < 0, либо owner==0 + первый
 *     feedback — сообщество, либо new_posts/group_invites;
 *   - sn_groups в SnNotifyFilter считал сообществом ТОЛЬКО parentOwnerId < 0.
 *
 * Итог [.isCommunityNotif] — единое правило для обоих мест.
 *
 * ## Классификация «сообщество»
 *
 *   - parentOwnerId < 0 — VK-канон: группы отрицательные;
 *   - parentOwnerId == 0, но первый feedback profile — сообщество (id < 0);
 *   - тип — новостной «просто групповой» (new_posts / group_invites), у которых
 *     action.entity.owner_id часто отсутствует (owner==0);
 *   - type входит в [COMMUNITY_BROADCAST_TYPES] при owner==0 и пустом feedbackIds
 *     (трансляции сообществ без актора-юзера: market/clip/story/podcast/video/photo).
 *     Это дефолт безопасности: безопаснее НЕ показывать такое как «от юзера».
 */
internal fun VKApiClient.NotificationItem.isCommunityNotif(): Boolean {
    return parentOwnerId < 0
        || (parentOwnerId == 0L && feedbackIds.firstOrNull()?.let { it < 0 } == true)
        || type == "new_posts" || type == "group_invites"
        || (parentOwnerId == 0L && feedbackIds.isEmpty() && type in COMMUNITY_BROADCAST_TYPES)
}

/**
 * Типы «широковещательных» трансляций сообществ, которые при owner==0 и пустом
 * feedbackIds приходят БЕЗ актора-юзера (см. [#NOTIFY-LEAK2]).
 */
internal val COMMUNITY_BROADCAST_TYPES: Set<String> =
    setOf("market", "clip", "story", "podcast", "video", "photo")