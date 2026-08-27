package re.pinok.auth

/**
 * P2-8 #AUTH-AUDIT (восстановление): JS-инъекции для VK ID React controlled inputs.
 *
 * Эти JS были удалены вместе с мёртвым V1 `VkAuthWebViewScreen` в коммите cca2f4ecc,
 * но V2 их НЕ инжектил — получился только 1 слой фикса (SovaInputConnection) вместо 3.
 * Симптом: зеркальный ввод в VK ID форме пароля (Pluton240 → 042notulP).
 *
 * Восстановлены в [VkAuthWebViewScreenV2] через `evaluateJavascript` в
 * onPageStarted + onPageFinished (для надёжности — React может ре-рендерить
 * input после navigation).
 *
 * Тройной слой фикса (см. FixedInputWebView.kt KDoc):
 *   Слой 1: FixedInputWebView + SovaInputConnection (перехват setComposingText)
 *   Слой 2: VK_2FA_CURSOR_FIX_JS (этот файл) — setTimeout forceEnd после input
 *   Слой 3: нативный 2FA overlay (не реализован в V2, можно добавить если слои 1+2 не помогут)
 */

/**
 * #75 Слой 2: JS cursor-fix для React controlled inputs на id.vk.com / id.vk.ru.
 *
 * Проблема: React controlled input после onChange ре-рендерит input,
 * устанавливая `input.value` и вызывая `setSelectionRange(0, 0)` —
 * курсор всегда сбрасывается в начало → каждый новый символ печатается
 * в начало → ТЕКСТ ЗЕРКАЛИТСЯ (Pluton240 → 042notulP).
 *
 * Решение: Перехватываем `input` event в CAPTURE phase (раньше React) +
 * `setTimeout` с тремя задержками (0ms, 16ms, 50ms) — setTimeout
 * выполняется ПОСЛЕ React-обработчика (который синхронный), поэтому
 * наш `setSelectionRange(len, len)` побеждает.
 *
 * Также обрабатываем `focusin` (восстановление курсора при потере/возврате фокуса)
 * и `keyup` (дополнительная страховка).
 *
 * Инжектится на ЛЮБОМ домене (безопасно — для не-React input это no-op,
 * setSelectionRange(len, len) на уже корректной позиции ничего не меняет).
 */
internal const val VK_2FA_CURSOR_FIX_JS = """
(function() {
    if (window.__sovaCursorFix) return;
    window.__sovaCursorFix = true;
    function forceEnd(el) {
        try {
            var len = (el.value || '').length;
            if (len > 0 && el.selectionStart !== len) {
                el.setSelectionRange(len, len);
            }
        } catch(e) {}
    }
    function onInput(e) {
        var el = e.target;
        if (!el || (el.tagName !== 'INPUT' && el.tagName !== 'TEXTAREA')) return;
        setTimeout(function(){ forceEnd(el); }, 0);
        setTimeout(function(){ forceEnd(el); }, 16);
        setTimeout(function(){ forceEnd(el); }, 50);
    }
    function onFocus(e) {
        var el = e.target;
        if (!el || (el.tagName !== 'INPUT' && el.tagName !== 'TEXTAREA')) return;
        setTimeout(function(){ forceEnd(el); }, 0);
        setTimeout(function(){ forceEnd(el); }, 100);
    }
    document.addEventListener('input', onInput, true);
    document.addEventListener('focusin', onFocus, true);
    document.addEventListener('keyup', function(e) {
        if (e.target && (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA')) {
            setTimeout(function(){ forceEnd(e.target); }, 0);
        }
    }, true);
})();
"""

/**
 * #74: JS-инъекция для скрытия рекламы на странице ВК.
 *
 * Блокирует CSS-классы рекламы в m.vk.ru ленте (после успешного входа,
 * если пользователь попадает на feed). Безопасно — ad block не ломает
 * функциональность VK, только скрывает рекламные блоки.
 *
 * JS оборачен в IIFE + проверка `window.__sovaAdBlock` чтобы не
 * дублировать при повторных вызовах (onPageStarted + onPageFinished).
 */
internal const val VK_INPUT_HARDENING_JS = """
(function() {
    if (window.__sovaAdBlock) return;
    window.__sovaAdBlock = true;
    try {
        var adStyle = document.getElementById('sova-ad-block');
        if (!adStyle) {
            adStyle = document.createElement('style');
            adStyle.id = 'sova-ad-block';
            adStyle.textContent = [
                '[data-ad], .ad_card, .adcard, .ads_ad_block, .ads_ad_banner,',
                '.ads_banner, .ads_frame, .ad_banner, .ad_banner_top,',
                '.reklama, .promo, .promo_block, .feed_recom_promo,',
                '.ai_promo, .apps_feedAiPromo, .ai_recom_block,',
                '.WallCard--ads, .wall_card_ads, .ads_add_row,',
                '.feed_right_add, .wall_add_row, .WallCard--ad,',
                '[class*="ad-"], [class*="ads-"], [class*="AdCard"],',
                '[class*="PromoBlock"], [class*="ReklamaBlock"],',
                '[data-testid="ad"], [data-testid="ads"]',
                '{ display: none !important; visibility: hidden !important; }'
            ].join(' ');
            (document.head || document.documentElement).appendChild(adStyle);
        }
    } catch(e) {}
})();
"""
