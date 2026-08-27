# VK Video Player (vk-vp-root) — Class Tree & API Reference

Исходник: сохранённая страница VK Video (`видео.zip`).

---

## 1. DOM-дерево (классы + data-testid)

```
vk-vp-root.s-0 [data-title="..."]
│   style="--timeline-slider-filled-bg: #0077FF;"
│   CSS: bg=var(--black), color=var(--white), font=12px, user-select=none, overflow=hidden
│   Modifier: .preview-track-horizontally-alinged — горизонтальная полоса превью на таймлайне
│   Modifier: .visible-outside — overflow:visible (для PiP/tooltips)
│
├── player-wrapper.s-8                    [absolute, 100% w/h]
│   │   CSS: height:100%, position:absolute, width:100%
│   │
│   ├── keyboard-controls.s-t             [tabindex="-1", role="region", aria-label="Видеоплеер"]
│   │   │   CSS: bottom:0, left:0, position:absolute, right:0, top:0
│   │   │
│   │   ├── video-wrapper.s-e             [absolute, 100% w/h, bg=black]
│   │   │   │   z-index: var(--video-container-z-index) = 0
│   │   │   │
│   │   │   └── video-container.s-e       [data-testid="video-container"]
│   │   │       │   data-is-playing="false"
│   │   │       │   style="transform: rotate(0deg) scale(1);"
│   │   │       │   CSS: transition: var(--rotate-transition-duration)=0.25s ease all
│   │   │       │   Modifier: .fast-rotate — transition:none
│   │   │       │   Modifier: .nocursor — cursor:none
│   │   │       │
│   │   │       └── <video> .player-media [crossorigin, playsinline, poster]
│   │   │           ├── <source> blob:https://vkvideo.ru/...
│   │   │           └── <track> subtitles (srclang="ru")
│   │   │
│   │   ├── <slot name="annotation">
│   │   │
│   │   ├── container.s-a                 [data-testid="unstarted-thumb", hidden when playing]
│   │   │   │   CSS: bg-position:50%, bg-repeat:no-repeat, bg-size:contain
│   │   │   │   CSS: position:absolute (all edges 0), cursor:pointer
│   │   │   │   Modifier: .bgCover — bg-size:cover
│   │   │   │   Modifier: .hidden — opacity:0, visibility:hidden
│   │   │   │
│   │   │   └── playButton.s-a            [tabindex="0", role="button", aria-label="Смотреть"]
│   │   │       │   CSS: centered flex, w/h=var(--big-play-size)=96px, margin:auto
│   │   │       │
│   │   │       ├── playButtonBackground.s-a
│   │   │       │   CSS: bg=var(--big-play-bg-color)=rgba(0,0,0,.5)
│   │   │       │   CSS: border-radius=var(--big-play-radius)=50%
│   │   │       │   CSS: hover → bg=var(--big-play-hover-bg-color)=rgba(0,0,0,.6), scale(1.15)
│   │   │       │   CSS: icon inside: w=38px, h=33px (var(--big-play-icon-width/Height))
│   │   │       │   SVG: 48x48 viewBox, play triangle path
│   │   │       │
│   │   │       └── [play-icon SVG 48x48]
│   │   │
│   │   ├── container.s-x                 [center pause icon, shown briefly on tap]
│   │   │   └── container.s-9             [animation-duration: 1000ms]
│   │   │       └── [pause-icon SVG 48x48] — two pause bars path
│   │   │
│   │   ├── root.s-y                     [data-testid="double-forward-label", hidden]
│   │   │   │   CSS: bg=var(--double-forward-bg)=rgba(0,0,0,.6), border-radius:100px
│   │   │   │   CSS: position:absolute, left:50%, top:24px, transform:translateX(-50%)
│   │   │   │   CSS: padding:6px 12px, gap:4px, font-size:13px, font-weight:600
│   │   │   │   Modifier: .visible — opacity:1, visibility:visible
│   │   │   ├── text.s-y — "2x" label
│   │   │   └── [forward SVG 16x16]
│   │   │
│   │   └── wrapper-bottom.s-18          [absolute bottom, gradient overlay]
│   │       │   CSS: bottom:0, display:flex, flex-direction:column
│   │       │   CSS: justify-content:flex-end, width:100%
│   │       │   CSS: z-index: var(--controls-container-z-index)=1
│   │       │   CSS: ::before — gradient bg (var(--controls-gradient))
│   │       │   CSS:   height=var(--controls-gradient-height)=100px
│   │       │   CSS: transition: transform var(--controls-transition-duration)=0.25s
│   │       │   CSS:   timing: cubic-bezier(0.455,0.03,0.515,0.955)
│   │       │   Modifier: .controls-hidden — transform:translateY(100%)
│   │       │   Modifier: .no-smooth — transition:none
│   │       │
│   │       ├── enriched-timeline.s-1x   [timeline + preview strip]
│   │       │   │   CSS: padding: var(--timeline-padding-vertical-top)=3px
│   │       │   │   CSS:   var(--timeline-padding-horizontal)=10px
│   │       │   │   CSS:   var(--timeline-padding-vertical-bottom)=4px
│   │       │   │   CSS: position:relative
│   │       │   │   child: wrapper-preview-track — preview strip (z-index:-1)
│   │       │   │   child: preview-track-control — close button for strip
│   │       │   │   child: preview-track-close — tooltip on close
│   │       │   │
│   │       │   └── timeline.s-b
│   │       │       ├── tooltip-wrapper.s-1k [position:relative, block]
│   │       │       │   │   CSS: bottom:100%, transform:translateX(-50%)
│   │       │       │   │   Modifier: .active — opacity:1, visibility:visible
│   │       │       │   │
│   │       │       │   └── tooltip.s-1k
│   │       │       │       ├── timeline-preview.s-1l [width=278px in example]
│   │       │       │       │   ├── preview.s-1l [bg-image, bg-size, bg-position]
│   │       │       │       │   │   CSS: height=var(--preview-track-frame-height-horizontal)=65px
│   │       │       │       │   ├── episode.s-1l [empty for single video]
│   │       │       │       │   └── time.s-1l — "4:57:07" timestamp
│   │       │       │       │
│   │       │       │       └── pointer.s-1k [CSS triangle arrow]
│   │       │       │
│   │       │       └── timeline-slider.s-1m  [role="slider", data-testid="progress_bar"]
│   │       │           │   aria-label="Ползунок временной шкалы"
│   │       │           │   CSS: height=var(--slider-height)=3px, cursor:pointer
│   │       │           │   CSS: --slider-bg: var(--timeline-slider-bg)
│   │       │           │   CSS: --slider-filled-bg: var(--timeline-slider-filled-bg)
│   │       │           │   Modifier: .slider-dragging — handle scale(1.3)
│   │       │           │   Modifier: .handle-always-visible — handle always shown
│   │       │           │   Modifier: .square — border-radius:unset
│   │       │           │   Modifier: .disabled — opacity:.4, cursor:default
│   │       │           │
│   │       │           ├── bars.s-1m [bg track, full width]
│   │       │           │   │   CSS: bg=var(--slider-bg)=hsla(0,0%,100%,.4)
│   │       │           │   │
│   │       │           │   ├── loaded.s-1m [buffered, translateX=%]
│   │       │           │   │   CSS: bg=var(--slider-loaded-bg)=hsla(0,0%,100%,.4)
│   │       │           │   │   CSS: left:-100%, transform:translateX(X%)
│   │       │           │   │   Modifier: .ads — bg=var(--slider-filled-ads)=#67a5eb
│   │       │           │   │
│   │       │           │   └── filled.s-1m [played, translateX=%]
│   │       │           │       CSS: bg=var(--slider-filled-bg), left:-100%
│   │       │           │       Modifier: .active-live — bg=var(--red)
│   │       │           │
│   │       │           └── handleWrap.s-1m [translateX=%]
│   │       │               └── handle.s-1m [circle, 12px]
│   │       │                   CSS: bg=var(--slider-handle-bg)=white
│   │       │                   CSS: opacity:0, transform:scale(0), visibility:hidden
│   │       │                   CSS: hover/.slider-dragging — opacity:1, scale(1)
│   │       │                   Modifier: .active-live — bg=var(--red)
│   │       │
│   │       └── controls.s-18              [data-testid="player_controls"]
│   │           │   CSS: display:flex, align-items:center
│   │           │   CSS: height=var(--controls-height)=40px
│   │           │   CSS: justify-content:space-between
│   │           │   CSS: padding: 0 var(--controls-desktop-padding-horizontal)=4px 4px
│   │           │   CSS: font-size:13px, white-space:nowrap
│   │           │   Modifier: .minimal-view — padding: 0 8px 4px
│   │           │   Modifier: .controls-hidden — transform:translateY(100%)
│   │           │
│   │           ├── controls-left.s-18    [flex, flex-grow:1, min-width:0, overflow:hidden]
│   │           │   │   CSS: align-content:center, display:flex
│   │           │   │   CSS: >:not(:first-child) margin-left: var(--controls-desktop-item-margin-left)=4px
│   │           │   │
│   │           │   ├── btn-container.s-1p [data-testid=btn-prev, aria-label="Предыдущее видео"]
│   │           │   │   │   child: .btn.btn-d--prev.s-27 [aria-keyshortcuts="Shift + P"]
│   │           │   │   │   SVG: prev-icon 24x24 (skip-back, scaleX(-1))
│   │           │   │   │
│   │           │   ├── btn-container.s-1p [play/pause]
│   │           │   │   │   child: .btn.s-27 [data-testid="play-btn", aria-keyshortcuts="k"]
│   │           │   │   │   SVG: play-icon / pause-icon 24x24
│   │           │   │   │
│   │           │   ├── btn-container.s-1p [data-testid=btn-next, aria-label="Следующее видео"]
│   │           │   │   │   child: .btn.btn-d--next.s-27 [aria-keyshortcuts="Shift + N"]
│   │           │   │   │   SVG: next-icon 24x24
│   │           │   │   │
│   │           │   ├── time.s-1n [data-testid="time", class="show-duration"]
│   │           │   │   │   child: .current.s-1n [data-testid="current_time"] — "00:06"
│   │           │   │   └── .duration.s-1n [data-testid="video_duration"] — "5:33:06"
│   │           │   │
│   │           │   └── btn-container.w-max.s-1p [spacer, width:100%]
│   │           │
│   │           └── controls-right.s-18   [flex, align-content:center]
│   │               │   CSS: >* margin-left: 4px
│   │               │
│   │               ├── btn-container.s-1p [autoplay toggle]
│   │               │   │   child: .btn.s-27 [data-testid="autoplay-next-toggle", role="switch"]
│   │               │   │   CSS: aria-checked="true"/"false"
│   │               │   │   child: .gutter.s-1v [toggle track]
│   │               │   │   │   CSS: w=32px, h=12px, bg=hsla(0,0%,100%,.3), border-radius:50px
│   │               │   │   └── .toggler.s-1v [toggle thumb]
│   │               │   │       CSS: w=18px, h=18px, bg=#626364 (off) / var(--toggle-color-autoplay-on)=white (on)
│   │               │   │       CSS: transition: background-color 0.2s, transform 0.2s
│   │               │   │       Modifier: .enabled — bg=white, translateX(right)
│   │               │   │
│   │               ├── volumeBar-container.s-1q [flex, align-items:center]
│   │               │   │   CSS: --slider-height:var(--slider-volume-height)=5px
│   │               │   │   CSS: --slider-radius:calc(5px/2)
│   │               │   │   CSS: --slider-handle-size:var(--slider-volume-handle-size)=13px
│   │               │   │
│   │               │   ├── tooltip-wrapper.s-23 > .btn.btn-full-opacity.s-27
│   │               │   │   [data-testid="btn-volume-horizontal", aria-keyshortcuts="m"]
│   │               │   │   SVG: volume-max-icon / volume-min-icon / volume-mute-icon 24x24
│   │               │   │
│   │               │   └── volumeBar.s-1q [horizontal slider, w=70px, margin:0 8px]
│   │               │       └── slider.s-1m [role="slider", data-testid="volume-slider"]
│   │               │           ├── bars > loaded (hidden, 0%)
│   │               │           ├── bars > filled (volume level, translateX=%)
│   │               │           └── handleWrap > handle
│   │               │
│   │               ├── btn-container.s-1p [subtitles]
│   │               │   │   child: .btn.s-27 [data-testid="subtitles-btn", aria-keyshortcuts="c"]
│   │               │   │   SVG: subtitles-icon-outline 24x24 (rectangle with lines)
│   │               │   │
│   │               ├── btn-container.s-1p [settings gear]
│   │               │   │   child: .btn.s-27 [data-testid="settings-btn", aria-expanded="false"]
│   │               │   │   child: .settings-icon-wrapper.s-1o
│   │               │   │       └── .icon.s-22
│   │               │   │           SVG: settings-icon (gear) 24x24
│   │               │   │
│   │               ├── btn-container.s-1p [PiP]
│   │               │   │   child: .btn.s-27 [data-testid="videoplayer_pip_btn"]
│   │               │   │   SVG: picture_in_picture_outline_24 (VKUI icon, 24x24)
│   │               │   │
│   │               ├── btn-container.out-of-view.s-1p [context menu ⋮, HIDDEN on desktop]
│   │               │   │   child: .btn.s-27 [data-testid="btn-context-menu"]
│   │               │   │   SVG: three-dots 24x24
│   │               │   │
│   │               ├── btn-container.s-1p [theater mode]
│   │               │   │   child: .btn.s-27 [data-testid="theater-mode-off"]
│   │               │   │   SVG: horizontal_rectangle_arrows_out_outline_24 (VKUI icon, 24x24)
│   │               │   │
│   │               └── btn-container.s-1p [fullscreen]
│   │                   │   child: .btn.s-27 [data-testid="fullscreen-btn", aria-keyshortcuts="f"]
│   │                   │   SVG: open-fullscreen-icon 24x24
│   │
├── settings-menu.s-8                  [absolute, z-index=2]
│   │   CSS: bottom: calc(var(--controls-height) + 17px), right:18px
│   │
│   └── settings-menu-container.s-p     [hidden by default]
│       │   CSS: --settings-bg: rgba(0,0,0,.72), --settings-radius: 8px
│       │   CSS: --mobile-settings-bg: #2c2d2e
│       │   CSS: --settings-item-bg-hover: hsla(0,0%,100%,.08)
│       │   CSS: --divider-border: 1px solid var(--white-alpha-24)
│       │   Modifier: .mobile — width:100%
│       │
│       └── .menu (ul, role="menu")
│           │   CSS: bg=var(--settings-bg), border-radius=var(--settings-radius)
│           │   CSS: overflow:hidden, transition: opacity 0.2s, visibility 0.2s
│           │   Modifier: .desktop — position:absolute, right:0, bottom:0
│           │   Modifier: .mobile — bg=var(--mobile-settings-bg), font-size:15px, line-height:50px
│           │
│           ├── .item (li, role="menuitem")
│           │   CSS: color:var(--white), cursor:pointer, display:flex, justify-content:flex-end
│           │   CSS: font-size:13px, padding:0 32px 0 18px (desktop) / 0 14px (mobile)
│           │   CSS: hover (desktop) → bg=var(--settings-item-bg-hover)
│           │   CSS: .data-selected=false → margin-left: calc(12px + 16px) [for check icon space]
│           │   child: .icon-wrapper (24x24, margin-right:16px)
│           │   child: .label-container (flex-grow:1, margin-right:24px)
│           │   child: .label (text), .sublabel (opacity:.5)
│           │   child: .select-icon (16x24, padding-left:12px) — check mark
│           │   Modifier: .switch-item — toggle switch row
│           │   Modifier: .header.mobile — border-bottom, justify-content:space-between
│           │   Modifier: .bottomBordered — border-bottom divider
│           │
│           └── .sub-menu (positioned, with header back-button)
│               │   CSS: padding-top: calc(8+7+18+7)px = 40px (desktop)
│               │   CSS: .header — absolute top, full width, border-bottom
│               │   CSS: .header .title — margin-left:8px
│               │   child: .close.s-p [× button, data-testid="close-settings-menu"]
│
├── thumb-timer.s-g                    [data-testid="thumb-timer", hidden by default]
│   │   CSS: bottom:0, right:0, margin:8px, z-index:2
│   │   Modifier: .live — bg=var(--red)
│   │
│   └── wrapper.s-c                    [bg=var(--black-alpha-40)=rgba(0,0,0,.4)]
│       │   CSS: border-radius:4px, padding:2px 6px, white-space:nowrap
│       │
│       ├── equalizer.s-c               [10x10px, 3 animated bars]
│       │   │   CSS: display:inline-block, position:relative
│       │   │   Modifier: .playing — animation-play-state:running
│       │   │
│       │   ├── col.s-c [first-child]   [animation: 1.5s alternate, scaleY .1→.8→.4→.6→.1]
│       │   ├── col.s-c [nth-child(2)]  [animation: scaleY 1→.3→.9→.6→1]
│       │   └── col.s-c [nth-child(3)]  [animation: scaleY .6→.9→.1→.9→.6]
│       │       CSS: width:2px, height:10px, bg=var(--btn-color), position:absolute, bottom:0
│       │
│       └── text.s-c                    [font-size:13px, font-weight:500, line-height:16px]
│           CSS: color=var(--controls-time)=white
│
├── hot-key-helpers-container.s-v      [hidden, z-index=3]
│   │   CSS: position:absolute, centered, 200x120px, pointer-events:none
│   │   CSS: animation: fadeIn 0.25s ease
│   │   Modifier: .hidden — height:0, width:0, visibility:hidden, fadeOut
│   │   Modifier: --left/right — offset 25%/75%
│   │
│   └── hot-key-helpers-text.s-v       [font-size:15px, font-weight:600]
│       CSS: max-height:40px, overflow:hidden, text-align:center
│       CSS: text-shadow: 0 0 5px rgba(0,0,0,.16)
│
├── container.s-q                      [notification area, z-index=1]
│   │   CSS: position:absolute, top:20px, centered, pointer-events:none
│   │
│   └── notification.s-q               [bg=var(--notification-bg)=#2c2d2e, border-radius=8px]
│       │   CSS: display:inline-flex, gap:10px, padding:9px 14px 10px 12px
│       │   CSS: pointer-events:auto
│       │
│       ├── description.s-q             [font-size:13px, line-height:16px, white-space:pre]
│       │   Example: "Видео загружается слишком медленно —\nпопробуйте изменить качество на «Авто»"
│       │
│       ├── btn.s-1e                    [action button]
│       │   Example: "Выбрать качество «Авто»"
│       │
│       └── close.s-16                  [× button]
│
└── overlay-container                  [fixed, 100% viewport, z-index=4, pointer-events:none]
    │
    └── overlay (per tooltip)
        └── tooltip.s-23                [bg=rgba(41,41,41,.8), border-radius:3px, padding:6px 10px]
            ├── content.s-23            [text label]
            └── tooltip-pointer.s-23    [CSS triangle, 5px border-top]
```

---

## 2. CSS Variables (vk-vp-root.s-0)

### Colors
| Variable | Value | Usage |
|----------|-------|-------|
| `--white` | `#fff` | Primary text, icons, slider fills |
| `--black` | `#000` | Background |
| `--red` | `#ff3347` | Live indicator, active live slider |
| `--blue-light` | `#70c1ff` | Info accent |
| `--black-alpha-40` | `rgba(0,0,0,.4)` | Thumb-timer bg, PiP bg |
| `--white-alpha-24` | `hsla(0,0%,100%,.24)` | Divider borders |
| `--white-alpha-72` | `hsla(0,0%,100%,.72)` | Secondary text |
| `--text-gray` | `#626d7a` | Description text |
| `--devider-gray` | `#dce1e6` | Separator color |

### Typography
| Variable | Value |
|----------|-------|
| `--font` | `-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif` |
| `--vk-vp-custom-font` | Override per embed (optional) |

### Controls
| Variable | Value | Meaning |
|----------|-------|---------|
| `--controls-height` | `40px` | Bottom bar height |
| `--controls-gradient-height` | `100px` | Gradient overlay height |
| `--controls-transition-duration` | `0.25s` | Show/hide animation |
| `--controls-transition-fn` | `cubic-bezier(0.455,0.03,0.515,0.955)` | Easing |
| `--controls-desktop-item-margin-left` | `4px` | Gap between buttons |
| `--controls-desktop-padding-horizontal` | `4px` | Side padding |
| `--controls-gradient` | `linear-gradient(180deg,transparent 0%,...,hsla(0,0%,4%,.5))` | 15-step gradient |

### Sliders
| Variable | Value |
|----------|-------|
| `--slider-bg` | `hsla(0,0%,100%,.4)` |
| `--slider-filled-bg` | `var(--white)` |
| `--slider-handle-bg` | `var(--white)` |
| `--slider-loaded-bg` | `hsla(0,0%,100%,.4)` |
| `--slider-height` | `3px` |
| `--slider-radius` | `0px` |
| `--slider-handle-size` | `12px` |
| `--slider-volume-height` | `5px` |
| `--slider-volume-radius` | `calc(5px/2)` |
| `--slider-volume-handle-size` | `13px` |

### Big Play Button
| Variable | Value |
|----------|-------|
| `--big-play-size` | `96px` |
| `--big-play-radius` | `50%` |
| `--big-play-bg-color` | `rgba(0,0,0,.5)` |
| `--big-play-hover-bg-color` | `rgba(0,0,0,.6)` |
| `--big-play-icon-width` | `38px` |
| `--big-play-icon-height` | `33px` |

### Settings Menu
| Variable | Value |
|----------|-------|
| `--settings-bg` | `rgba(0,0,0,.72)` |
| `--settings-item-bg-hover` | `hsla(0,0%,100%,.08)` |
| `--settings-radius` | `8px` |
| `--mobile-settings-bg` | `#2c2d2e` |
| `--toggle-color-separator` | `#363738` |
| `--toggle-color-off` | `#656565` |
| `--toggle-color-on` | `#71aaeb` |
| `--toggle-color-autoplay-on` | `var(--white)` |

### Timeline Preview Track
| Variable | Value |
|----------|-------|
| `--preview-track-frame-height-horizontal` | `65px` |
| `--preview-track-frame-height-vertical` | `114px` |
| `--preview-track-controls-top` | `16px` |
| `--preview-track-bottom-offset` | `10px` |
| `--preview-track-time-line-height` | `16px` |

### Z-Index Layers
| Variable | Value |
|----------|-------|
| `--video-container-z-index` | `0` |
| `--controls-container-z-index` | `1` |
| `--ads-container-z-index` | `1` |
| `--notification-z-index` | `1` |
| `--thumb-timer-z-index` | `2` |
| `--modal-z-index` | `2` |
| `--settings-menu-z-index` | `2` |
| `--context-menu-z-index` | `3` |
| `--hot-key-helpers-container-z-index` | `3` |
| `--overlay-container-z-index` | `4` |

### Notification
| Variable | Value |
|----------|-------|
| `--notification-bg` | `#2c2d2e` |
| `--notification-radius` | `8px` |

### Other
| Variable | Value |
|----------|-------|
| `--btn-color` | `var(--white)` |
| `--double-forward-bg` | `rgba(0,0,0,.6)` |
| `--rotate-transition-duration` | `0.25s` |

---

## 3. Settings Menu Structure

### 3.1 Menu Items (by weight/order)

**Settings Menu** (gear icon, data-testid="settings-btn"):

| ID | Weight | Type | Label (RU) |
|----|--------|------|------------|
| `quality-settings` | 100 | OPEN_SUB_MENU → sub-items | "Качество" (`menu_quality`) |
| `audio-language-settings` | 200 | OPEN_SUB_MENU | "Звуковая дорожка" (`menu_audio_languages`) |
| `playback-rate-settings` | 300 | OPEN_SUB_MENU → rate values | "Скорость" (`menu_playback_rate`) |
| `subtitles-settings` | 400 | OPEN_SUB_MENU | Субтитры |
| `traffic-saving-settings` | 500 | SWITCH | "Экономия данных" (`menu_traffic_saving`) |
| `download` | 600 | CLICK | |
| `report` | 700 | CLICK | "Сообщить о проблеме" (`menu_report`) |
| `copy-data` | 800 | CLICK | |
| `debug-info` | 1000 | CLICK | |

**Context Menu** (⋮ icon, data-testid="btn-context-menu"):

| ID | Weight | Label (RU) |
|----|--------|------------|
| `copy-link` | 100 | "Скопировать ссылку на видео" |
| `copy-link-timestamp` | 200 | "Скопировать ссылку на кадр" |
| `copy-embed-code` | 300 | |
| `pip` | 400 | "Свернуть в мини-проигрыватель" (`menu_pip`) |
| `video-loop` | 500 | "Включить/Отключить повтор" |
| `rotate` | 600 | "Повернуть" (`menu_video_rotate`) |
| `report` | 700 | |

### 3.2 Quality Sub-Menu Items

| Value | Label | Resolution |
|-------|-------|------------|
| `auto` | "Авто" / "Авто (1080p)" | Adaptive |
| `4320p` | "4320p" | 7680×4320 |
| `2160p` | "2160p" | 3840×2160 |
| `1440p` | "1440p" | 2560×1440 |
| `1080p` | "1080p" | |
| `720p` | "720p" | |
| `576p` | "576p" | 1024×576 |
| `480p` | "480p" | 856×480 |
| `360p` | "360p" | 640×360 |
| `240p` | "240p" | 428×240 |
| `144p` | "144p" | 256×144 |

Quality enum (JS): `INVARIANT, Q_144P, Q_240P, Q_360P, Q_480P, Q_576P, Q_720P, Q_1080P, Q_1440P, Q_2160P, Q_4320P`

Special quality items:
- **Auto quality** (`auto_quality_option`): "Авто" — adaptive bitrate
- **High quality** (`high_quality`): "Высокое качество" — with description "Будет использовано больше трафика"
- **Traffic saving** (`traffic_saving_quality`): "Экономия трафика" — "Видео грузятся быстрее, но качество ниже"

### 3.3 Playback Rate Sub-Menu

| Value | Label (RU) |
|-------|------------|
| `0.25` | "0,25x" |
| `0.5` | "0,5x" |
| `0.75` | "0,75x" |
| `1` | "Обычная" (`playback_rate_1`) |
| `1.25` | "1,25x" |
| `1.5` | "1,5x" |
| `1.75` | "1,75x" |
| `2` | "2x" |

i18n: `playback_rate: "{multiplier}x"`, `playback_rate_1: "Обычная"`

### 3.4 Menu Action Types

| Action | Description |
|--------|-------------|
| `OPEN_SUB_MENU` | Opens nested sub-menu (quality, speed, subtitles, audio) |
| `CLICK` | Triggers action (download, report, copy, debug) |
| `SWITCH` | Toggle switch (traffic saving) |

### 3.5 Menu Item Component

Each item can have:
- `icon` — 24x24 SVG icon (left)
- `label` — primary text (left-aligned via flex-grow)
- `sublabel` — secondary description (opacity:.5)
- `valueComponent` — custom render (e.g., quality check mark, toggle)
- `selected` — boolean, shows check icon (16x24, green)
- `isHiddenFor` — array of platforms ("desktop", "mobile")
- `bottomBordered` — adds divider below

---

## 4. Interactive Behaviors

### 4.1 Controls Show/Hide
- Tap video → toggle `controls-hidden` class on `wrapper-bottom`
- Transition: `translateY(100%)` with 0.25s ease
- Mouse idle 3s → auto-hide (desktop)

### 4.2 Big Play Button
- Visible when: `unstarted-thumb` not `.hidden` (video not started)
- Click → start playback, hide thumbnail
- Hover → `playButtonBackground` scale(1.15) + darker bg

### 4.3 Center Pause Feedback
- On tap to pause → show `container.s-x` (pause icon) briefly
- `animation-duration: 1000ms` → fade out

### 4.4 Double-Tap Forward/Rewind
- Double-tap left/right → seek ±10s (configurable via `doubletap_seek_step`)
- Shows `root.s-y` "2x" badge at top center briefly

### 4.5 Timeline Slider
- Hover → show `tooltip.s-1k` with preview image + timestamp
- Drag → `slider-dragging` class → handle scale(1.3)
- Touch action: none (custom gesture handling)

### 4.6 Preview Track (horizontal)
- Modifier `preview-track-horizontally-alinged` on root
- Shows horizontal filmstrip above timeline on hover/drag
- `wrapper-preview-track.s-1x` — full-width strip, z-index:-1
- `preview-track-control.s-1x` — centered close button

### 4.7 Settings Menu
- Position: `bottom: calc(40px + 17px)`, `right: 18px`
- Desktop: `position:absolute, right:0, bottom:0` within container
- Mobile: full width, bottom sheet style
- Sub-menu: slides in with header (back button + title)
- Close: click outside, press Escape, click × button

### 4.8 Volume Control
- Button: mute/unmute (3 icon states: max, min, mute)
- Slider: horizontal 70px, shown next to button
- Mobile: vertical slider (`.vertical` modifier, 50px width, rotated -90deg)

### 4.9 Autoplay Toggle
- Switch: `gutter` (32x12px track) + `toggler` (18x18px thumb)
- Off: thumb left, bg=#626364
- On: thumb right, bg=white
- Transition: 0.2s ease

### 4.10 Thumb Timer (PiP / minimized)
- Shows: equalizer animation + time text
- Position: bottom-right corner, margin:8px
- Equalizer: 3 bars, 2px wide, 10px tall, staggered animations at 1.5s
- Modifier: `.playing` → animation-play-state:running
- Modifier: `.live` → red bg, different sizing

### 4.11 Slow Loading Notification
- Triggered when video loads too slowly
- Shows: description text + action button ("Выбрать качество «Авто»")
- Position: top:20px, centered
- Bg: #2c2d2e, border-radius:8px, padding:9px 14px 10px 12px

### 4.12 Keyboard Shortcuts
| Key | Action | Label (RU) |
|-----|--------|------------|
| `k` / Space | Play/Pause | |
| `f` | Fullscreen | "На весь экран" |
| `m` | Mute/Unmute | |
| `c` | Toggle subtitles | |
| `T` | Theater mode | "Широкий экран" |
| `Shift+N` | Next video | |
| `Shift+P` | Previous video | |
| `←` / `→` | Seek ±5s | |
| `↑` / `↓` | Volume ±10% | |
| `.` / `,` | Frame step | |

---

## 5. SVG Paths (key icons)

### Play Icon (48x48 viewBox)
```
m14.4 8.76 24.02 13.85a1.6 1.6 0 0 1 0 2.78L14.4 39.24a1.6 1.6 0 0 1-2.4-1.39v-27.7a1.6 1.6 0 0 1 2.4-1.39
```

### Pause Icon (48x48 viewBox)
```
m17.4 10 1.6.2a2 2 90 01.8.8l.2 1.6v22.8l-.2 1.6a2 2 90 01-.8.8l-1.6.2h-4l-1.4-.2a2 2 90 01-.8-.8l-.2-1.6V12.6l.2-1.6a2 2 90 01.8-.8l1.6-.2h3.801zm16 0 1.6.2a2 2 90 01.8.8l.2 1.6v22.8l-.2 1.6a2 2 90 01-.8.8l-1.6.2h-4l-1.4-.2a2 2 90 01-.8-.8l-.2-1.6V12.6l.2-1.6a2 2 90 01.8-.8l1.6-.2h4z
```

### Settings (Gear) Icon (24x24 viewBox)
```
M13.25 2.83c.23.75.38 1.5 1.63 2.23 1.4.38 2.08 0 2.72-.42.66-.45.86-.32 1.12-.06l.65.64c.29.3.4.42 0 1.18-.38.7-.8 1.33-.42 2.73.72 1.25 1.47 1.47 2.22 1.62.78.16.83.39.83.76v.9c0 .42 0 .59-.83.84-.75.23-1.5.38-2.23 1.63-.38 1.4 0 2.08.42 2.72.45.66.32.86.06 1.12l-.64.65c-.3.29-.42.4-1.18 0-.7-.38-1.34-.8-2.73-.42-1.25.72-1.47 1.47-1.62 2.22-.16.78-.39.83-.76.83h-.9c-.42 0-.59 0-.84-.83-.23-.75-.38-1.5-1.63-2.23-1.4-.38-2.08 0-2.72.42-.66.45-.86.32-1.12.06l-.65-.64c-.29-.3-.4-.42 0-1.18.38-.7.8-1.34.42-2.73-.72-1.25-1.47-1.47-2.22-1.62-.78-.16-.83-.39-.83-.76v-.9c0-.42 0-.59.83-.84.75-.23 1.5-.38 2.22-1.63.38-1.4 0-2.08-.42-2.72-.44-.66-.31-.86-.05-1.12l.64-.65c.3-.29.42-.4 1.18 0 .7.38 1.33.8 2.73.42 1.25-.72 1.47-1.47 1.62-2.22.16-.78.39-.83.76-.83h.9c.42 0 .59 0 .84.83M12 7.25a4.75 4.75 0 1 0 0 9.5 4.75 4.75 0 0 0 0-9.5
```

### Skip Next/Prev Icon (24x24 viewBox)
```
M32 21.68V15a2 2 90 114 0v18a2 2 90 01-4 0v-6.66l-16.3 9.32A2.48 2.48 90 0112 33.56V14.46c0-1.9 2.06-3.08 3.7-2.12z
```
(For prev: same path with `transform: scaleX(-1)`)

### Fullscreen Icon (24x24 viewBox)
```
M10.5 19a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-4.5a1 1 0 0 1 2 0v2.07l3.36-3.36a1 1 0 0 1 1.42 0l.01.01a1 1 0 0 1 0 1.42L7.43 18H9.5a1 1 0 0 1 1 1M18 7.43l-3.36 3.36a1 1 0 0 1-1.42 0l-.01-.01a1 1 0 0 1 0-1.42L16.57 6H14.5a1 1 0 0 1 0-2H19a1 1 0 0 1 1 1v4.5a1 1 0 0 1-2 0z
```

### Subtitles Icon (48x48 viewBox)
```
M16.92 8h14.16q2.82-.02 4.66.1c1.28.1 2.4.32 3.46.86a8.8 8.8 90 013.84 3.84c.54 1.04.76 2.18.86 3.46q.12 1.84.1 4.66v6.16q.02 2.82-.1 4.66a10 10 90 01-.86 3.46 8.8 8.8 90 01-3.84 3.84 10 10 90 01-3.46.86q-1.84.12-4.66.1H16.92...
M12 29.8c0-1 .8-1.8 1.8-1.8h10.4a1.8 1.8 90 010 3.6H13.8a1.8 1.8 90 01-1.8-1.8m0-7.6c0-1 .8-1.8 1.8-1.8h3.4a1.8 1.8 90 110 3.6H13.8a1.8 1.8 90 01-1.8-1.8...
```

### Double-Forward Icon (48x48 viewBox, shown 16x16)
```
M23.75 31.2v-5.45l-11.84 6.7a1.45 1.45 180 01-2.16-1.25v-14.4a1.45 1.45 180 012.16-1.26l11.84 6.7v-5.44a1.45 1.45 180 012.16-1.26l12.7 7.19a1.45 1.45 180 010 2.52l-12.7 7.2a1.45 1.45 180 01-2.16-1.27Z
```

---

## 6. Mapping to Compose Implementation

### Architecture
```
VideoPlayerScreen (full screen, black bg)
├── AndroidView(PlayerView, useController=false)
│   └── ExoPlayer (single instance, quality switch via setMediaItem)
│
├── VKPlayerOverlay (Box over PlayerView, handles taps)
│   ├── VKBigPlayButton (center, 96dp circle, visible when !isPlaying && !hasStarted)
│   ├── VKPauseFeedback (center, 1000ms fade, shown on tap-to-pause)
│   ├── VKDoubleTapLabel (top-center, "2x", shown on double-tap seek)
│   │
│   ├── VKControlsWrapper (bottom, gradient, translateY animation)
│   │   ├── VKTimeline (Slider, 3dp height, buffered+filled bars, 12dp handle)
│   │   │   └── VKTimelineTooltip (preview image + timestamp, on hover)
│   │   │
│   │   └── VKControlsBar (Row, 40dp height, SpaceBetween)
│   │       ├── VKControlsLeft
│   │       │   ├── VKIconButton(prev)
│   │       │   ├── VKIconButton(play/pause)
│   │       │   ├── VKIconButton(next)
│   │       │   └── VKTimeDisplay(current / duration)
│   │       │
│   │       └── VKControlsRight
│   │           ├── VKAutoplayToggle (switch, 32x12dp)
│   │           ├── VKVolumeControl (button + 70dp slider)
│   │           ├── VKIconButton(subtitles)
│   │           ├── VKIconButton(settings) → opens VKSettingsMenu
│   │           ├── VKIconButton(PiP)
│   │           ├── VKIconButton(theater)
│   │           └── VKIconButton(fullscreen)
│   │
│   └── VKSettingsMenu (PopupPositioned, bottom:57dp, right:18dp)
│       ├── VKSettingsItem("Качество") → sub-menu
│       │   └── VKQualitySubItem(Auto, 2160p, 1440p, 1080p, 720p, 480p, 360p, 240p)
│       ├── VKSettingsItem("Скорость") → sub-menu
│       │   └── VKRateSubItem(0.25x, 0.5x, 0.75x, 1x, 1.25x, 1.5x, 2x)
│       ├── VKSettingsItem("Субтитры") → sub-menu
│       ├── VKSettingsSwitch("Экономия данных")
│       └── VKSettingsItem("Сообщить о проблеме")
│
├── VKThumbTimer (bottom-right, equalizer + time, for PiP mode)
├── VKSlowLoadingNotification (top-center, "Выбрать качество «Авто»")
└── VKHotKeyHelpers (center, keyboard shortcut labels, hidden)
```

### Key Implementation Notes
1. **BUG FIX**: Set `useController = false` on `PlayerView` — Media3's built-in controller was overlapping with custom controls
2. Controls auto-hide after 3s idle, show on tap
3. All control buttons use `pointer-events: auto` inside `pointer-events: none` overlay (match VK z-index layering)
4. Settings menu: use `Popup` (not `DropdownMenu`) positioned at `bottom: calc(40dp + 17dp)`, `right: 18dp`
5. Quality switch: `player.setMediaItem()` on same ExoPlayer instance, seek to saved position on STATE_READY
6. Volume slider: horizontal 70dp on landscape, vertical on portrait (optional)
7. Timeline: custom `Slider` composable with 3dp height, buffered bar, played bar, 12dp circular handle
8. Gradient: `Brush.verticalGradient` with 15 stops matching VK's CSS gradient