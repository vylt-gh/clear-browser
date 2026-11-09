# Open issues

Carried forward deliberately. Ordered by how much they hurt, not by when they
were found.

## Known, cosmetic

Nothing outstanding.

## Removed

### The adaptive colour pipeline is gone
The whole thing: `ChromeColorDeriver`, `PageColorCache`, `PixelSampler`,
`PageThemeScript`, `ColorMath`, `ChromeLog`, `TopBandSampler`, the document-start
script, the web-message bridge, the per-origin cache, and the 32 unit tests that
covered it. The chrome is a plain M3 `ColorScheme` again, optionally from the
wallpaper.

It was the centrepiece of the project and it kept not paying for itself. Three
reasons, in order of how much they actually mattered:

- **It flashed.** A page's colour arrived from three sources that disagreed --
  a cache lookup, a DOM-declared `theme-color`, and a pixel sample taken
  milliseconds later. On the way to a search that meant a brief, saturated
  toolbar in a colour no page had actually chosen. Removing the slider in an
  earlier pass did not fix it, because the flash was never about the slider.
- **It was expensive in the worst place.** Deriving a scheme is a gamut fit and
  a contrast search, and the theme is recomputed whenever the screen changes.
  Memoising it was necessary to make the UI usable at all.
- **It was wrong often enough to matter, with no good remedy.** The strength
  control could only dial the feature down to "off", which is the same as not
  having it.

A browser that looks like a well-made app is not a worse browser. The page is
the content, the chrome is the app.

## UI work

### The UI was laggy
Four separate causes, all of which had to go:

- **The scroll poll ran on a fixed 16ms timer.** It now runs on the
  `Choreographer`, once per displayed frame, and drops to a timer when frames are
  running long so it stops piling work onto a frame that is already late.
- **The theme recomputed a wallpaper palette on every recomposition.**
  `dynamicLightColorScheme` parses the wallpaper, and `ClearBrowserTheme` called
  it twice (once per side of the light/dark axis) on a screen that recomposes
  constantly. Both schemes and the derived chrome are now memoised.
- **Load progress was in the main state object.** It ticks up to 100x a second
  for the whole of every load, and each tick recomposed the theme, the scrim and
  the reveal handle to move a one-pixel line. It has its own flow now.
- **Every `pushState` wrote the URL straight into state.** A single-page app can
  fire dozens of these while the user reads. Reports are now coalesced and
  skipped when the URL has not actually changed.

Also fixed while measuring: `attach` is now idempotent. It builds a WebView and
adds it to the container, and it was wired to `AndroidView`'s `update` block --
a *recomposition* callback, not an initialisation one. It happened not to fire
again in practice, but a second call would have stacked a second WebView on the
first and abandoned the old one without destroying it.

Measured with `dumpsys gfxinfo`, scrolling a long page: janky frames 23% to
1.75%, p95 20ms to 11ms. On GitHub, 34% to 26%.

### The omnibox sat under the keyboard
`imePadding`, paired with `navigationBarsPadding` because the IME inset collapses
to zero when the keyboard is down.

### The handle and the bar were two objects
They were independent, so hiding the chrome looked like one thing leaving and
another arriving, in two places, at two speeds. They are now one surface with two
sizes: collapsing stretches the bar down to the handle's diameter and slides it
into the corner, and a single animated value drives width, offset and both fades
so they cannot arrive at different times. One spring, in `Motion.kt`.

### Page headers were hidden behind the status bar
Two facts pull in opposite directions and cannot both be satisfied at once:

- A `position: fixed` header at `top: 0` sits under the status bar, and the
  status bar **consumes touches in its own region** -- verified on device, a tap
  on the covered part of a fixed header never reaches the page. Nothing the
  page does can fix this; it never gets the event.
- Insetting the page permanently hides the top of every page forever, which is
  what the user objected to: once you have scrolled, that content is right there
  and should be visible.

So the inset is applied **only while the page is at the top** and released the
moment it is not. At the top a fixed header is fully visible and tappable
(verified: a button placed at `top: 4px` is reachable); scrolled, the page is
full bleed and the content is visible under the transparent status bar (verified
by pixel sampling: page colour at y=0). The transition is animated, because a
hard jump on the first pixel of scroll reads as a glitch.

`scrollAtTop` is written at most twice per scroll rather than per frame, so this
did not reintroduce the recomposition cost from the lag work.

**Two things that did not work, recorded so they are not retried.** WebView
padding: Chromium's fixed-position layout escapes view padding, so MDN's header
still sat under the status bar and GitHub rendered a blank document because the
padding collapsed its viewport. A permanent container offset: it fixed the
header but reintroduced the filled strip the user did not want.

### The tint-strength option is gone
The slider, the setting, the preference key, and the mixing code in
`ChromeColorDeriver` are all removed. The page colour is now used outright
(clamped and contrast-checked, but not diluted back toward the theme). A control
whose range included "off" made the pipeline look broken for no benefit, and
"matches the page" is a cleaner promise than "matches the page, a bit".

## Recently landed

### Bookmarks and history
The star in the menu saves the page on screen; one row in the menu opens a
single sheet with a Bookmarks / History segmented control. Tapping a row opens
it in a new tab, and rows have their own remove target so the large tap area is
never the destructive one.

The rules live in `core/data` as pure Kotlin (`HistoryLog`, `BookmarkList`) and
are unit-tested; the stores only read and write JSON. Decisions worth recording:

- **Identity is the normalised URL.** Only `http`/`https` are stored, and the
  fragment is dropped, so `#section` links are the same page. Recording a page
  again moves it to the front (`HistoryLog`) instead of appending a duplicate.
- **History is written when a visit is finished.** URL and title arrive on
  separate callbacks, so a visit is flushed once a title is known (or when the
  load completes with none). Title-less hops are redirects and are dropped
  rather than shown as pages the user never saw.
- **History is bounded at 1000 entries**, oldest dropped first. Bookmarks are
  not capped: they are deliberate.
- **Private windows record nothing and cannot bookmark.** The engine profile is
  captured when the tab's listener is created, so a callback arriving just after
  a profile switch cannot carry a private URL into the normal history.
- **Files** are `filesDir/library/history.json` and `bookmarks.json`, written on
  one background thread using an immutable snapshot and an atomic temp+rename.
  Several visits during one write collapse into the next pass; a corrupt file is
  discarded rather than stopping the browser.

Covered by `HistoryLogTest` (8 cases) and `BookmarkListTest` (8 cases), including
redirect-style dedupe, fragment identity, invalid schemes, the cap, blank-title
fallback, and restore.


### A fresh launch opened `example.com`
Fixed, and it was two bugs wearing one coat.

The first was that `example.com` *was* the start page: a hardcoded `START_URL`
loaded whenever nothing was restored. `example.com` is a placeholder owned by
IANA whose entire purpose is to be an example in documentation, so the browser
was opening a page that exists to say nothing to a user who thought he was
opening his browser.

The second was why it kept coming back: that URL was then saved into the session
file like any other page, so the next launch dutifully restored it. Removing the
constant alone would have left every install that had ever run it pointed at
`example.com` once more, forever, which is exactly the "sometimes it still boots
to example.com" this was reported as.

Now: nothing is loaded when there is no session, and a stored session naming the
retired homepage is discarded instead of restored (`isRetiredStartPage`). One
string comparison is enough; a versioned migration for a single constant would
have been ceremony.

### The empty screen is a start page, not a blank `WebView`
There is no homepage, and this is deliberate. The engine is left with nothing
loaded and a `StartPage` overlay fills the screen instead: theme surface, the
mark at 10% as a watermark, no links. A blank `WebView` is not the same thing as
an empty screen -- it is a control with no content, and it is indistinguishable
from a page that failed to load.

The overlay covers a `WebView` that stays in the tree, because detaching it would
take the engine's view out of the hierarchy and force the whole engine to be
rebuilt to get it back. Nothing is hidden by that: at this point there is no page
to hide.

Also fixed on the way: the page background is set on the view, so the blank state
is the theme's neutral surface rather than white.

### Search engine is a setting, and Kagi is the default
Eight engines (`SearchEngine`), chosen from a dropdown in the menu, persisted by
name, and used by the address bar for anything that is not a host.

The control started as a wrapped row of chips and was replaced by a dropdown on
sight: eight chips take three rows of the sheet to say one thing, and they make a
setting that is almost never changed the loudest object on the screen. A
read-only field reading "Kagi" answers "which one is in force" at a glance, and
the label stays visible while the list is open, so the list cannot be mistaken
for pages or recent searches.

Two things worth recording:

- **The engine was hardcoded.** `normalize()` sent every query to DuckDuckGo,
  with the URL built inline. That logic is now `resolveAddress(raw, engine)` in
  `core/data`, next to the enum that supplies the template, so the resolver and
  the engines cannot drift apart, and it is covered by 11 unit tests.
- **The default is Kagi because it was asked for.** A default search engine is
  where every unqualified question the user types ends up, so the menu states
  which one is in force and the address field names it in its placeholder
  ("Search with Kagi or enter address").

### The Private badge became a glyph in the address field
The word "Private" sat at the end of the bar in a filled pill and took about a
third of its width, permanently, for a state that is simply *on*. It is now an
eye-with-a-slash, 17dp, in the accent colour, inside the address field beside the
URL -- because the question it answers is "which window is this address in".

A padlock was tried first and rejected: in an address bar a lock already means
this connection is encrypted, and this is the one indicator whose entire job is
that the user can trust what it says. A drawn hat-and-glasses was tried after
that and rejected on sight: at 16dp it reads as a cartoon face. Material's
`VisibilityOff` costs no custom artwork and means "not being seen".

### Toggle rows answered only on the switch
Found while verifying tabs on the device: the menu's rows (Private browsing,
Full screen, desktop site, …) toggled only when the 30dp switch was hit, and the
label, which is most of the row, did nothing. Each row is now a `toggleable`
node with `Role.Switch`, and the switch's own callback is null, so the row is the
single owner of the change and there is no double-toggle. Verified by tapping the
"Private browsing" text: the row turns on, tabs reset to a blank tab in the new
profile, and tapping the label again returns to normal.

### Downloads lost their extension
Fixed. The cause was upstream of where the earlier note was looking.
`URLUtil.guessFileName(url, contentDisposition, mimeType)` trusts the MIME type
when the disposition does not pin a name, and `application/octet-stream` is the
default for anything a script generates. So a server sending
`Content-Disposition: attachment; filename="report.txt"` with that generic type
produced `report.bin` at the `DownloadListener`, and no downstream MIME fixing
could recover it, because the name was already wrong.

`resolveDownloadFilename` now parses the disposition first -- including the RFC
5987 `filename*` form, which is what non-ASCII names actually arrive as -- and
only falls back to `URLUtil.guessFileName` when the header yields nothing, since
the platform helper knows the RFC 6266 edge cases better than a hand-rolled
parser would. Verified on device: `name=report.txt` from an
`application/octet-stream` response now saves as `report.txt`.

### Microphone capture never worked
Fixed, and this was silent. WebView does not send Android permission strings to
`onPermissionRequest`; it sends its own vocabulary, so
`android.webkit.resource.AUDIO_CAPTURE` is what actually arrives. The mapping
was written against `android.permission.RECORD_AUDIO`, never matched, and every
microphone request fell into the "unrecognised" branch and was denied with
nothing on screen to suggest a prompt had been expected.

Two things compounded it. The mapping was duplicated into a second `when` block
that had drifted, with no `GEOLOCATION` case and an `else` falling through to
`ACCESS_FINE_LOCATION` -- so an unsupported resource could be checked against
the location permission, and pass if the user had granted location for an
unrelated reason. And the result was granted via
`request.grant(request.resources)`, handing back the page's entire original list
rather than only what was approved.

Now: one mapping returning null for anything unsupported, and
`request.grant(approved)` for exactly the resources the user agreed to. Verified
on device -- `RECORD_AUDIO: granted=true`, and the page's error moved from
`NotAllowedError` to `NotReadableError`, which is this device having no usable
microphone rather than a refusal.

### Chrome left dead zones where it used to be
Fixed. `WebContainer` appended every distinct rect it was handed and only cleared
on null, so each measurement the chrome ever took stayed live as a touch
*denial*. The union grew on every resize -- expanding the omnibox, the menu
sheet, a navigation-inset change -- so the page silently stopped responding in
regions where nothing was drawn. A stale denial is worse than a stale allowance:
at worst a near-miss scrolls the page instead of pressing a button, whereas here
the tap was swallowed by nothing at all.

The replace-not-accumulate rule now lives in `ChromeReservations`, a plain Kotlin
class with no Android types so it can be unit tested, covered by 13 tests
including 30 resize cycles. Verified on device: a tap that registers, then
expand and collapse the chrome twice, then the same tap still registers.

### Popup WebViews had no `onRenderProcessGone`
Fixed. The child `WebView` in `onCreateWindow` now handles renderer death, and
lint is clean.

### Session restore
Done. URL + scroll offset in `filesDir/session/last.json`, written on `onStop`
(the only callback that reliably runs before a kill) and atomically via temp file
+ rename. `onSaveInstanceState` stays the fast path for fidelity; the file is the
safety net for when the Bundle is lost. The offset is re-applied on every scroll
report until it lands, because restoring an offset races page layout and would
otherwise clamp to the bottom.

### Incognito
Done. `WebViewCompat.setProfile` / `getProfile` (`MULTI_PROFILE`, API 35+), one
profile per window, so a private window cannot see or write the normal window's
state. A separate profile is the correct mechanism: "it was never stored there"
is a guarantee, where "we scrubbed it afterwards" is a promise the OS can break.

Two details that are easy to get wrong and are now enforced:

- **The profile is verified, not assumed.** `applyTo` re-reads the profile after
  setting it and logs the result. A silent failure is the worst bug this
  codebase could have: the window would say "Private" while writing cookies into
  the normal jar.
- **A private window writes nothing to disk.** `persistSession` returns early
  when the profile is private, and it does not flush cookies either, because
  `flush()` forces the in-memory jar to disk. Previously `onStop` would have
  saved the private URL, leaving a durable record of every private page — and it
  would have been silent, since nothing about a saved file is user-visible.

Private mode survives an Activity recreation (rotation, locale change) and does
not survive a real finish, decided by
`onHostDestroyed(finishing = !isChangingConfigurations)`. Snapping back to normal
on a rotation would move the user out of the private window they are still
using, and a private window that reopened on the next launch would look private
while restoring a URL the user expects to be gone.

### Handle position
Added. `ChromePosition` (left / centre / right) moves the reveal circle that
appears when the chrome auto-hides, and only that.

The bar stays full width with the address visible. That is the deliberate call:
the bar is where the URL lives, and docking it to a side to dodge a page's
floating control would mean truncating the address to `example....` to uncover
something the user was not using at that moment. The circle is passive and
overlaps the page the whole time, so moving it costs nothing. Verified on device
with a centred "contact support" bubble: with the handle moved aside, the bubble
is tappable (`BUBBLE_HIT`), and the handle still reveals the bar from its new
position.

### Tabs
Landed: a counter in the bar opens a sheet listing every open tab (title plus
host, active row filled, per-row close), with a new-tab button. The rules live in
`TabStrip`, which is plain Kotlin and is covered by 17 tests, so the selection
logic does not depend on Compose or on a `WebView` being present.

The shape of the implementation is the part worth recording:

- **One engine exists at a time.** Switching tabs detaches the active `WebView`
  from the hierarchy and pauses it, then attaches the newly selected one and
  restores it. Ten live `WebView`s would have ten renderers; a browser that
  keeps every tab warm at that price is a battery story waiting to be filed.
  Detach/reattach was verified to preserve the page and its scroll position.
- **Engines are created lazily**, on a tab's first activation. A tab restored
  from disk is a URL until it is looked at, which is also what makes restoring
  ten tabs cheap.
- **Chrome callbacks are filtered per tab.** Each tab gets a `TabListener` that
  drops callbacks from any engine that is not the active one. Without that, a
  detached page finishing a load would rewrite the address bar of the page the
  user is looking at.
- **Closing the active tab selects the tab that took its place**, else the one
  before it; closing the last tab opens a fresh blank one rather than leaving the
  window tabless. A browser that closes to nothing needs a special "no tabs"
  state everywhere, and the counter has to lie about something.
- **Blank tabs are now persisted.** The old session format skipped a session
  with no real page, which was right when there was one window and it always
  held something. With tabs, a blank tab is a position in the strip, and
  restoring the other tabs around it but not it would renumber what the user
  built.
- **Sessions store the whole strip.** `last.json` holds `tabs[]`, the `active`
  index and `savedAt`; the single-URL format is migrated on read, and the
  `onSaveInstanceState` bundle carries both the strip and each tab's `WebView`
  state, because a rotation must not quietly merge two tabs into one.

### Engine Switch to Mozilla GeckoView
In v0.2.0, the rendering engine transitioned from Android system WebView to Mozilla GeckoView with hardware WebRender compositing and built-in tracking protection. Bundled with uBlock Origin MV2 (`uBlock0@raymondhill.net`), Clear Browser eliminates ads, trackers, and malicious scripts before network calls resolve.

### Find in Page
Landed in v0.4.0: in-page search bar with forward/backward navigation, real-time match counter (`X of Y`), IME auto-focus, and auto-scrolling to active matches directly within GeckoView.

### Share Page & Copy Link
Landed in v0.6.0: Quick sharing via standard Android `ACTION_SEND` chooser and one-tap link copying directly inside the anchored popup menu.

### Chrome Visual Themes & Handle Controls
Landed in v0.7.0: A 3-way visual chrome style selector (`Clear` frosted glass, `Acrylic` translucent container, and classic `Material` from v0.1), alongside full dock coordinate fixes and a dedicated reading mode toggle to completely hide the collapsed handle.

### WebExtension Popups, AMO Store & Downloads
Landed in v0.8.0:
- WebExtension popups (uBlock Origin, Dark Reader, etc.) render dynamically in contextual bottom sheets attached to the active session.
- In-browser installation of extensions directly from `addons.mozilla.org` (AMO).
- Dedicated Downloads manager tab in the Library sheet tracking downloads in real-time.
- Hierarchical back navigation: walks backwards through history, returns to `about:blank`, and exits only when backing out from the start page.

### Canopy Pull-to-Refresh & Privacy-First Favicons
Landed in v0.9.0:
- **Canopy Pull-to-Refresh**:
  - Overscroll pull gesture damped with logarithmic-like rubber-band physics pulling the top web container downward when scrolled to the top.
  - Expanding status bar canopy linked to the sampled `topBarColor` (or theme surface on the start page), complete with a spring refresh spinner and tactile haptic trigger threshold.
  - Color continuity: preserves the previous page's top color during reloads and navigations until a new color is sampled, eliminating flashes of black.
  - Lifecycle management: single managed coroutine animates the canopy back to 0px while fading alpha, eliminating stuck spinner states.
  - Configurable toggle in the Settings bottom sheet.
- **Privacy-First Favicons (`FaviconStore` & `IcoParser`)**:
  - Direct connection to `https://${host}/favicon.ico` without third-party proxies, remote resolvers, or telemetry.
  - Pure Kotlin `IcoParser` decodes multi-entry Windows ICO containers (embedded PNGs, 1-bit, 4-bit, 8-bit, 24-bit, and 32-bit DIB bitmaps with alpha masks) without third-party image libraries.
  - Two-tier cache: in-memory `LruCache` (60 entries) and disk cache (`cacheDir/favicons/`).
  - Private tabs never write to or read from disk.
  - Tab switcher layout updated with a fixed 32dp guide slot, sharp favicons, monogram letter fallbacks, and a compass icon for blank tabs.
- **Chrome Layout & Themed Tab Button**:
  - Bottom bar icon clustering with uniform 38dp touch targets and balanced spacing between Search Pill, Tabs, Reload, and Menu.
  - Tab counter redesigned into a perfect 30dp circle with theme-adaptive styling (`Clear` frosted border, `Acrylic`, and `Material`).
- **First-Run Onboarding Tour & Interactive Live Preview (v0.10.0)**:
  - 4-step setup walkthrough (`OnboardingScreen`) featuring the official circular "C" logo (`ic_clear_logo.png`) and dedicated App Permissions setup (Notifications, Camera, Microphone, Location).
  - Interactive live dummy chrome preview demonstrating Chrome Theme and App Theme with real-time expand/minimize simulation.
  - Native Default Browser integration via Android `RoleManager.ROLE_BROWSER` (API 29+) with legacy package manager fallback.
  - Customizable minimized bar position (Left, Center, Right) and hide-on-scroll toggle.
  - Re-visitation at any time via Settings sheet "Welcome tour".
  - Dedicated App Theme controls (System, Dark, Light) independent of web content theming.
  - Borderless Material tab counter button styling.

### Verified on device
- Tabs: new tab, switch, close active / background / last, detach-and-reattach
  keeps the page, `last.json` writes `tabs[]` with the right `active` index, and
  `am force-stop` + relaunch restores the strip.
- Normal profile: `JAR=[session_id=SECRET_VALUE]`. Private profile: `JAR=[]`.
  Back to normal: the cookie is still there.
- `last.json` held the normal URL unchanged across entering private mode,
  backgrounding, and an Activity recreation.
- Rotation while private: `finishing=false private=true`, engine rebuilt back
  into `INCOGNITO`, private switch still on, 0 fatals.
- Session restore survives the lifecycle refactor: saved at scroll 1325, back at
  the same position after HOME + `force-stop` + relaunch with no intent URL.

## Known, not started

Nothing outstanding. Everything previously listed here has landed.

## Deliberately deferred

- **Passkeys / WebAuthn.** Low priority for a local-first browser without cloud accounts. Passwords are supported via Android native Autofill services (Bitwarden, 1Password, KeePassDX, etc.) and WebExtensions installed via AMO.
- **Reader / extract mode, TTS.** Reading is already Clear Browser's primary experience thanks to the uncluttered layout, auto-hiding navigation handle, and uBlock Origin ad and clutter elimination.
