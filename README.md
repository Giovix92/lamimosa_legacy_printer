# lamimosa_legacy_printer

Minimal companion app for the Jelly Bean 4.1.1 (API 16) POS device: shows the
order list from the same backend as `lamimosa_android`/`lamimosa_website`,
and prints a receipt straight to the Epson fiscal printer over the LAN.
Everything else the modern app does (order taking, push notifications, admin
panel) stays out of scope on purpose — this device only needs to print.

## Why a separate project instead of lowering minSdk on the main app

`lamimosa_android` uses Jetpack Compose, Hilt, Room, WorkManager,
Navigation-Compose — all of which floor at API 21+ regardless of what
`minSdk` is set to. This app avoids all of that: plain Android Views, no
AndroidX, no third-party libraries at all (`HttpURLConnection` + `org.json`,
both in the framework since API 1). Verified end-to-end: `minSdk = 16`
builds and packages cleanly with this dependency set (see build log from
2026-08-07 — `assembleDebug` succeeds, no manifest-merger conflicts).

## Architecture

```
JB device app ──HTTPS──> Netlify orders API (www.lamimosapasticceria.com/api/order)
      │
      └──HTTP (LAN only)──> Epson fiscal printer  http://<ip>:<port>/cgi-bin/fpmate.cgi
```

- No local server on the JB device, no calls in the other direction. The
  iPad web-app and this app both talk to Netlify independently; they never
  talk to each other. This sidesteps browser mixed-content restrictions
  entirely (see chat history if this decision needs revisiting).
- Printing protocol/XML format is ported near-verbatim from
  `lamimosa_android`'s `EpsonFiscalXmlBuilder.kt` / `EpsonFiscalSoapDriver.kt`
  so both apps produce identical receipts on the same printer.
- "Print" is manual/tap-based (`MainActivity`'s `ListView`), not automatic —
  by design, see the decision made when this was scaffolded.

## First-run configuration

`Config.kt` hardcodes the orders API base URL and the shared `STAFF_PASSWORD`
("operai", per `order.mjs`'s `checkAuth()`) — intentional, this app only
ever runs on one premises-bound device. Update that file if the password
rotates.

Printer IP/port/endpoint/operator/line-width are configured on-device via the
Settings screen (stored in SharedPreferences) since those are hardware-
specific and easier to punch in on first boot than to hardcode.

## Building & installing

```sh
./gradlew assembleDebug
adb connect 192.168.1.75:8022   # or however this POS's adb is reached
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Order detail / edit / status / archive / delete

`MainActivity`'s list is tap-through into `OrderDetailActivity` (full detail
view, mirrors the `.order-card-detail` block in js/admin.js) with buttons for
Stampa, "Segna come `<next status>`" (same `STATUS_CYCLE` as the web admin —
see `model/OrderStatus.kt`), Modifica, Archivia/Ripristina, Elimina.

`OrderEditActivity` is a **full-parity** edit form matching the web admin's
edit modal field-for-field (~25 fields: sede/occasione/categoria/tipologiaTorta
dropdowns, gusti multi-select capped at 2, topping, panna, scritta, candeline,
sesso/età, date/time pickers, etc.). It fetches `/api/config` on first open
(`net/OrderConfig.kt`) and ports the exact filtering logic from
`js/order-config.js` — `gustiPerTipologia`/`toppingPerTipologia`/
`gustiDisabilitatiPerTipologia` — including the `gustiLiberi` (free-text-only,
e.g. Tiramisù/Sacher/Babbà) and `gustiDisabilitati` (field hidden entirely)
special cases per tipologia. Status/archivedFrom are deliberately **not** in
this form, same split as the web admin (those live on OrderDetailActivity's
own buttons, not the edit modal).

All mutations go through `net/OrderMutationClient.kt` (PATCH/DELETE against
`/api/order`, same Bearer auth as the orders GET). On any successful mutation
the screen returns `RESULT_OK` and `MainActivity` reloads the list from the
server rather than trying to patch its local cache — simplest-correct over
clever.

## PATCH/DELETE don't work through HttpURLConnection - confirmed, worked around

Real-hardware testing surfaced two more Android HTTP-stack limitations, both
in `java.net.HttpURLConnection` itself, unrelated to TLS:

- `setRequestMethod("PATCH")` throws `ProtocolException("Invalid HTTP
  method: PATCH")` - PATCH simply isn't in Android's whitelist.
- `DELETE` *is* whitelisted, but the platform treats it as a body-less
  method: setting `doOutput=true` and writing throws
  `ProtocolException("DELETE does not support writing")`. `order.mjs`'s
  DELETE handler requires a JSON body (`{"id": ...}`), so this wasn't
  optional to work around.

Fixed by `net/RawHttpClient.kt`: instead of fighting `HttpURLConnection`
with version-fragile reflection hacks, it opens the TLS socket directly
(through the same Conscrypt engine + pinned trust store as
`TlsSocketFactory`) and writes the HTTP/1.1 request by hand - method line,
headers, body - then parses the response per RFC 7230 (Content-Length or
chunked, doesn't just block-read until the socket closes, since Netlify's
edge may not honor a `Connection: close` hint). Only `OrderMutationClient`
(PATCH/DELETE) uses this; GET (`OrdersApiClient`) and POST
(`PrinterClient`) work fine through the normal APIs and are untouched.

## Errors now surface as dialogs, not toasts

Every failure path (`net/*Client` failures, config-load failure, etc.) now
goes through `util/Dialogs.kt`'s `Activity.showError()` - a real
`AlertDialog` that stays until dismissed, instead of a `Toast` that could
disappear in ~2s before anyone read it. Success feedback is still a
(harmless-to-miss) `Toast`.

## Visual pass

No custom logo - reuses `lamimosa_android`'s app icon as-is (turns out
that's still Android Studio's unmodified default green-robot placeholder,
never a real "La Mimosa" mark; copied verbatim rather than inventing a new
one). The actual ask was making the *app* less plain:

- `values/colors.xml` pulls the same brand palette as
  `lamimosa_android/.../colors.xml` (`MimosaPink #D81B60` / `MimosaCream`).
- `values/styles.xml` - API 16 is Holo-only (no `colorPrimary`/Material
  theming), so this hand-themes `Theme.Holo.Light`: cream window
  background, a pink `ActionBar` with white title text, and a global
  `AppButton` style (solid pink, rounded, pressed/disabled states) applied
  to every `<Button>` via the theme's `android:buttonStyle` - free uplift,
  no per-layout XML changes needed for most buttons.
- Two style variants for hierarchy: `AppButton.Outline` (pink-stroke,
  cream fill - Modifica/Archivia/Impostazioni, so they don't visually
  compete with the screen's primary action) and `AppButton.Danger`
  (red-stroke - Elimina only).
- `item_order.xml` list rows are now card-styled (`drawable/bg_card.xml` -
  white, rounded, subtle border) instead of flat text rows.

## Release build

`assembleRelease` now does what it should: `minifyEnabled true` +
`shrinkResources true` (was `false`/no shrink before) - **~2.7 MB → ~880
KB**. Signed with the auto-generated debug keystore
(`signingConfig signingConfigs.debug`, not a real release key) since this
app is sideloaded on one premises-bound device and never distributed -
proper release-key management would be ceremony without benefit here.
R8 needed two `-dontwarn` rules for Conscrypt's legacy OEM-adapter classes
(`com.android.org.conscrypt.SSLParametersImpl` and the pre-KitKat
Harmony equivalent) - they exist on-device on the old Android versions
that need them but aren't on the compile classpath, so R8 fails the build
over the unresolved reference unless told to ignore it (see
`app/proguard-rules.pro`).

## Follow-up fixes (2026-08-08)

- **Real launcher icon.** The icon copied from `lamimosa_android` turned out
  to be Android Studio's unmodified default green-robot placeholder (that
  app never had a real one either), so there was nothing to actually
  inherit. Replaced with a generated icon (`gen_icon.py`-style, brand pink
  gradient rounded square + a white printer/receipt glyph) in
  `mipmap-*/ic_launcher.png` + `ic_launcher_round.png` (PNG, not the old
  `.webp` copies - had to delete those, Android doesn't allow two files
  resolving to the same resource name).
- **Button spacing.** `android:layout_margin*` set only inside a `<style>`
  turned out unreliable when that style is applied through the theme's
  default `android:buttonStyle` (as opposed to an explicit `style=""` on
  the tag) - the two default-styled pink buttons in `OrderDetailActivity`
  rendered flush against each other. Fixed by setting
  `android:layout_marginBottom` explicitly on every button in the affected
  layouts, not relying on the style for spacing.
- **"Segna come  Stampato" double-space.** Not actually a double space in
  the string - `🖨️`/`🗄️` are base emoji + a U+FE0F variation selector,
  which API 16's text renderer doesn't understand and shows as a second
  visible glyph/gap. Fixed by dropping the variation selector from every
  emoji that had one (`model/OrderStatus.kt`, `OrderDetailActivity`,
  `activity_order_detail.xml`) - same visual glyph, no more phantom gap.
- **Order creation.** `OrderEditActivity` now doubles as "Nuovo Ordine"
  (mirrors admin.js exactly: same form, `id` empty ⇒ create mode). Defaults
  match the web admin's create-mode prefill (categoria=torta, scritta/
  candeline=No, sesso=neutro, eventDate=today). `OrderMutationClient.create()`
  POSTs (plain HttpURLConnection - POST is a normal method, no
  RawHttpClient needed) with two backend quirks mirrored: order.mjs's POST
  handler reads the event date from `data`, not `eventDate` like PATCH does
  (renamed just before sending), and `silent: true` (mirrors admin.js not
  pinging its own push subscribers for a staff-entered order). New "➕
  Nuovo Ordine" button on `MainActivity`.
- **Printer test function.** `SettingsActivity` now has "🖨 Test Stampa",
  using the already-ported `EpsonFiscalXmlBuilder.buildTestPage()` (mirrors
  the main app's `PrinterJavaScriptInterface.testPrint()`). Tests whatever
  IP/port/endpoint/operator is currently typed in the fields, not
  necessarily what's saved yet - lets staff verify a change before
  committing to it.

## Emoji don't render on Jelly Bean - removed app-wide

"Nuovo"/"Stampato" status text showed no icon on real hardware. Root cause:
most pictograph emoji (🖨 🗄 🗑 📅 🕒 🟡 etc., Unicode's "Miscellaneous
Symbols and Pictographs" block) were added to Unicode in 2010-2014 -
Jelly Bean's (2012) bundled font doesn't have glyphs for most of them, so
they render as nothing/tofu. This isn't a per-glyph guessing game worth
re-litigating one emoji at a time on real hardware round-trips - **removed
pictograph emoji from the whole app**, in favour of:

- **Status color** is now a real drawn dot (`drawable/dot_status.xml`,
  recolored per-status via `GradientDrawable.setColor()` in code - see
  `OrderStatus.colorInt()`/`colorHex()`), not a colored emoji glyph. Works
  regardless of font/emoji support since it's drawn, not text. Note the
  `.mutate()` call before `setColor()` in `MainActivity` - drawables
  inflated from the same XML resource share a `ConstantState` by default,
  so skipping it would recolor every row in the list to whichever status
  was set last.
- `OrderDetailActivity`'s "Stato" line uses the same colors via HTML
  `<font color="...">` instead of an emoji prefix.
- Buttons (Stampa/Modifica/Elimina/Archivia/Nuovo Ordine/Test Stampa/date-
  time pickers) dropped their icon entirely - plain Italian text, relying
  on the button-style color-coding (primary/outline/danger, see the
  "Visual pass" section above) for at-a-glance meaning instead.

## Background order-check service (starts on boot)

Polls for new orders every 5 minutes, independent of the app being open,
and posts a notification when one shows up. No WorkManager/JobScheduler -
both are API 21+, off-limits at minSdk 16 - so this is built on the only
cross-API-level primitive that actually works here: `AlarmManager`.

- `service/AlarmScheduler.kt` - schedules a repeating (inexact, for
  battery) `AlarmManager` alarm targeting `AlarmReceiver`.
- `service/BootReceiver.kt` - listens for `BOOT_COMPLETED` and re-schedules
  the alarm, since alarms don't survive a reboot on their own. This is the
  actual "starts on boot" part.
- `App.kt` (custom `Application`) also calls `AlarmScheduler.schedule()` on
  every process start - covers the gap between "just installed, never
  rebooted yet" and the first reboot, and self-heals if the alarm was ever
  cleared (force-stop).
- `service/AlarmReceiver.kt` - `BroadcastReceiver.onReceive()` can't block
  on network I/O, so it just grabs a short (30s, auto-releasing) partial
  wake lock and hands off to `OrderCheckService`.
- `service/OrderCheckService.kt` (`IntentService` - runs its work off the
  main thread automatically) - does one `GET /api/order`, diffs the ID set
  against `service/OrderCheckPrefs.kt`'s previously-known set
  (SharedPreferences, survives process death), fires a notification for
  anything new. First-run seeds the known set silently instead of
  notifying about every pre-existing order the moment this feature ships.
- `service/NotificationHelper.kt` - tapping the notification opens
  `MainActivity`. Uses `NotificationChannel` only behind an SDK-version
  guard (API 26+); on API 16 itself, `Notification.Builder.build()` (added
  in API 16 exactly - no older fallback needed for this app's own floor).

**Confirmed on real hardware:** boot handling itself works (app opened
instantly after reboot - the process was already warm, meaning
`BOOT_COMPLETED` → `AlarmScheduler` fired correctly). No notification on
a first test, though, for two reasons that stack:

1. `AlarmScheduler`'s first fire is a full `POLL_INTERVAL_MS` (5 minutes)
   *after* scheduling, not immediately.
2. `OrderCheckService`'s first-ever check silently seeds its baseline
   instead of notifying (by design - otherwise every pre-existing order
   would fire a notification the moment this feature ships).

A test order created right after boot/reinstall can sail straight into
that silent seed pass with nothing to show for it. Fixed by having
`App.onCreate()` also kick an immediate `OrderCheckService` run (not just
schedule the periodic alarm) - seeding now happens within seconds of
process start instead of up to 5 minutes later. Also added a manual
"Controlla nuovi ordini ora" button in Settings
(`SettingsActivity.btnCheckOrdersNow`) that starts the same service
on-demand, for testing without waiting on the timer at all.

Also needs `RECEIVE_BOOT_COMPLETED` and `WAKE_LOCK` permissions (added to
the manifest) beyond what the app already had.

## Known gaps / next steps

- Print status is **not** written back to the orders API (order.mjs's
  `status` field is untouched by printing itself — but IS touched by the
  status-cycle/archive buttons now, see above). If auto-marking "stampato"
  on print is wanted, that's a small addition to `OrderDetailActivity.printOrder()`.
- Not yet tested against a real Epson fiscal printer — the orders-API leg
  (list, detail, edit, status, archive, delete) has been confirmed working
  on real JB hardware (see TLS notes below); the printer leg (fpMate XML →
  `/cgi-bin/fpmate.cgi`) is still unverified end-to-end.
- The edit form's gusti multi-select uses a simple `AlertDialog` checklist
  (checks past the 2-item cap are silently trimmed to the first 2 on save,
  with a toast) rather than the web's live-disable-past-2 UX — functionally
  equivalent, less polished.
- The background order-check service's boot-handling is confirmed working
  on real hardware (see above); the immediate-check-on-launch fix and the
  manual "check now" button are not yet re-tested after that fix. Also
  still open: whether this particular JB build/OEM skin kills backgrounded
  processes aggressively between the (up to) 5-minute periodic checks
  (some OEM skins do, pre-dating today's more standardized battery-
  optimization exemptions) - only matters for orders that arrive between
  checks while the app hasn't been opened in a while, worth watching for
  over a longer unattended stretch, not just a quick reboot test.

## TLS on real hardware (confirmed, not just theoretical)

Plain protocol re-enabling (`sslSocket.setEnabledProtocols(["TLSv1.1","TLSv1.2"])`
on the stock `SSLSocketFactory`) was **not enough** — real device testing hit:

```
javax.net.ssl.SSLProtocolException: SSL Handshake aborted [...]
SSL23_GET_SERVER_HELLO: unsupported protocol
```

API 16's built-in OpenSSL can't complete a TLS 1.2 handshake with Netlify's
edge at all (cipher suites/extensions too old), independent of which
protocol versions are requested. Fixed by bundling **Conscrypt**
(`org.conscrypt:conscrypt-android:2.5.2`) and installing it as the top
security provider in `TlsSocketFactory.install()` — it ships its own
BoringSSL, fully decoupled from the device's system TLS stack. Confirmed:
Conscrypt's AAR has no minSdk-floor conflict with 16 (`assembleDebug` still
succeeds). `ndk.abiFilters = ["armeabi-v7a"]` was added to `defaultConfig`
to stop the native `.so` from being packaged for arm64/x86/x86_64 as well
(dropped the APK from ~4.7 MB to ~1.7 MB) — safe since JB-era POS hardware
is 32-bit ARM.

### Second real-hardware issue: trust anchor not found

After Conscrypt fixed the handshake, the next real-device failure was:

```
java.security.cert.CertPathValidatorException: Trust anchor for
certification path not found
```

Confirmed OS-level, not app-specific — Chrome on the same device fails
identically against the same site. Cause: Netlify serves a Let's Encrypt
cert (`issuer=Let's Encrypt, CN=YE2` as of 2026-08, chaining to **ISRG Root
X1**), and Jelly Bean's factory CA store predates Let's Encrypt entirely —
there is no code path to update it on an unrooted JB device, so every
Let's Encrypt-issued site (not just this one) fails trust validation
system-wide, in every app including the browser.

Fixed by shipping ISRG Root X1's certificate directly in the app
(`res/raw/isrg_root_x1.pem`, self-signed, valid until 2035 — fetched from
`https://letsencrypt.org/certs/isrgrootx1.pem`, SHA-256 fingerprint
`96:BC:EC:06:26:49:76:F3:74:60:77:9A:CF:28:C5:A7:CF:E8:A3:C0:AA:E1:1A:8F:FC:EE:05:C0:BD:DF:08:C6`)
and building a custom `TrustManager` from it in `TlsSocketFactory.install()`,
instead of relying on the device's system trust store. This only affects
this app's own HTTPS calls — it does not and cannot fix Chrome or the rest
of the OS.

**If Let's Encrypt ever rotates away from ISRG Root X1** (not expected
before 2035, but intermediates like the current "YE2" already rotate
independently and are handled fine since the server sends its own
intermediate at handshake time — only the root is pinned here), this file
needs updating.
