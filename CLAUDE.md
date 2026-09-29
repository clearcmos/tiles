# tiles

Custom Quick Settings tiles in one app (`com.clearcmos.tiles`). Developed against a
Galaxy S25 (SM-S931W) on Android 16 / One UI 8.5. This repo was `wireless-debug-tile`
until the second tile arrived; its history carries over.

Each tile lives in its own package with its own `TileService`. `MainActivity` is the
shared setup screen and the long-press target of every tile. Add a tile as a new package,
a `<service>` entry in the manifest, and a section in `activity_main.xml`.

The one shared hazard: every reinstall clears the `WRITE_SECURE_SETTINGS` grant, and the
Wireless debug tile is the on-device way back when adb is lost. Re-run `pm grant` in the
same command as every install, whichever tile you are working on.

## Wireless debug: why it exists

Stock Android has a built-in Wireless debugging developer tile from Android 12 on. One UI
strips the developer tiles, verified by enumerating every tile service on the device:

```
adb shell pm query-services -a android.service.quicksettings.action.QS_TILE
```

39 services, no wireless debugging tile. Check this before assuming a device needs the app.

## Wireless debug: the mechanism

- Android 11 moved network adb to the `adb_wifi_enabled` global setting. The older
  `adb_enabled` global is USB debugging and is not touched here. The target device runs
  with `adb_enabled=0` while wireless debugging works, which confirms the split.
- Writing a global setting needs `WRITE_SECURE_SETTINGS`, declared by the platform as
  `signature|privileged|development|installer|role`. The `development` flag is why
  `pm grant` works for a normal app. Confirm on any device with:

```
adb shell dumpsys package permission android.permission.WRITE_SECURE_SETTINGS
```

- `AdbService` observes the setting and starts or stops the transport, so writing the int
  is the entire implementation.

## Layout

```
app/src/main/kotlin/com/clearcmos/tiles/
  MainActivity.kt                 setup and status for every tile, long-press target
  wirelessdebug/
    WirelessDebugging.kt            read, write, observe the setting; permission check
    TileAppearance.kt               pure state mapping, unit tested
    WirelessDebuggingTileService.kt the tile
    ToggleActivity.kt               invisible flip-and-toast, target of the shortcut
  calls/
    CallsStatus.kt                  reply parsing, tap target, appearance; pure, unit tested
    Calls.kt                        prefs, single-thread request runner, change listeners
    CallsTileService.kt             the tile
    SshClient.kt                    jsch client adapted from kata's
app/src/main/res/xml/shortcuts.xml  static launcher shortcut to ToggleActivity
```

`ToggleActivity` extends `android.app.Activity`, not `AppCompatActivity`, so it can use a
plain translucent platform theme and never inflate a view.

## Headset calls: the mechanism

- The "Calls" switch in a headset's Bluetooth details is the HFP connection policy,
  visible as `HEADSET=0` (off) or `100` (on) per device in `adb shell dumpsys bluetooth_manager`.
  Setting it to allowed makes `HeadsetService` connect HFP; forbidden drops it about a
  second later.
- `BluetoothHeadset.setConnectionPolicy` is `@SystemApi` behind `BLUETOOTH_PRIVILEGED`,
  declared `signature|privileged` with no `development` flag, so `pm grant` refuses it.
  Shizuku would work but was not chosen. The adb shell UID holds it, and the workstation
  already runs a helper dex at that UID for Bluetooth handoff.
- So the tile runs `calls on|off|status` over SSH on the workstation, which answers with
  one `<mac> calls=on|off hfp=<state> <name>` line per headset. The headset list lives on
  the workstation. The app's key must be pinned there with `restrict,command=` to the
  script that dispatches those verbs.
- LAN only by construction: wireless debugging is scoped to one access point, so the
  workstation cannot reach the phone's adb away from home. A VPN to the workstation does
  not change that.
- ECDSA, not ed25519: jsch's ed25519 lives in its Java 15+ multi-release classes, which
  Android ignores.
- A tap sets Calls off only when every headset reports on; any other state, including
  never synced, sets on.
- While a request is in flight the tile is `STATE_UNAVAILABLE`, which also stops SystemUI
  delivering a second tap. Failures show as inactive "Unreachable" so a tap retries.
  An unconfigured tile is inactive, not unavailable, so a tap reaches `onClick` and opens
  the app.
- The last reply is persisted in SharedPreferences. `onStartListening` refreshes when the
  reply is over two minutes old, so the shade does not SSH every time it opens.
- Verified end to end on 2026-09-29: app toggle off, then Quick Settings tile tap on, with
  `dumpsys bluetooth_manager` and the Settings switch agreeing each time.

## Build

The nix devShell pins JDK 17, Gradle, and an SDK with platforms 35 and 36 plus
build-tools 35.0.0. `buildToolsVersion` is pinned in `app/build.gradle.kts` so the
read-only nix store SDK is never asked to fetch another revision, and `GRADLE_OPTS` in
`flake.nix` points aapt2 at the SDK copy for the same reason.

```
nix develop --command gradle assembleDebug
nix develop --command gradle testDebugUnitTest
nix develop --command gradle jacocoDebugCoverageVerification
nix develop --command gradle ktlintCheck lintDebug
nix develop --command gradle ktlintFormat
```

Lint runs with `warningsAsErrors = true`. Version-drift checks are disabled because they
fail on every upstream release; dependency and SDK bumps are deliberate changes here.

## Testing on a device

Reinstalling clears the grant, so a full cycle is:

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.clearcmos.tiles android.permission.WRITE_SECURE_SETTINGS
adb shell cmd statusbar add-tile com.clearcmos.tiles/com.clearcmos.tiles.wirelessdebug.WirelessDebuggingTileService
adb shell cmd statusbar add-tile com.clearcmos.tiles/com.clearcmos.tiles.calls.CallsTileService
```

### The lockout hazard

Testing the off direction over wireless adb kills the connection running the test, and
there is no scripted way back. A detached recovery does not work:

```
adb shell "setsid sh -c 'sleep 8; for i in 1 2 3; do settings put global adb_wifi_enabled 1; sleep 12; done' >/dev/null 2>&1 &"
```

This was tried and failed. `setsid` escapes adbd's process group, but the process still
died before its first sleep elapsed; a probe file it was meant to write was never
created. Assume anything spawned over adb dies with the connection.

The only recovery is on the device: tap the tile, or use the Settings toggle. Install and
place the tile *before* testing the off direction. Toggling also clears any pinned 5555
port and assigns a new random one, so reconnecting needs a port scan.

## One UI Quick Settings placement

- `cmd statusbar add-tile` prepends to `secure sysui_qs_tiles`.
  `StatusBarManager.requestAddTileService` appends.
- Either way the tile first renders at the **bottom** of the panel, below the media player
  and the large Smart View / Modes / Nearby devices tiles. The panel scrolls; a screenshot
  of the unscrolled panel does not show it, which reads as the tile failing to register.
- SystemUI owns `sysui_qs_tiles` and rewrites it from memory within seconds, so reordering
  with `settings put` does not stick. Repositioning is a manual drag in the QS editor.
- One UI keeps the rendered layout in `secure grid_quick_panel_specs` with per-tile size
  and position, and the catalog of installable tiles in `secure quick_panel_tile_specs`.

## Design notes

- The tile uses listening mode, not `ACTIVE_TILE`, and registers a `ContentObserver` on
  the setting between `onStartListening` and `onStopListening`. The setting is mutable
  from the Settings app and from a host over adb, so the tile follows it rather than
  caching it.
- A missing grant maps to `STATE_UNAVAILABLE` with a "Needs grant" subtitle instead of the
  tile vanishing or silently doing nothing. Tapping it opens `MainActivity`, which shows
  the grant command.
- The home-screen surface is a static launcher shortcut, not an `AppWidgetProvider`. A
  shortcut drags onto a home screen for the same one-tap result without `RemoteViews`
  layouts or update broadcasts.
- `minSdk = 34` so `startActivityAndCollapse(PendingIntent)` and
  `StatusBarManager.requestAddTileService` are both available without version guards. The
  pre-34 `startActivityAndCollapse(Intent)` overload is deprecated and would fail
  warnings-as-errors lint.
- Tile labels are capped at 18 characters by the platform, hence "Wireless debug".
- `targetSdk` 35 and up draws edge to edge, so `MainActivity` applies system bar insets as
  padding on top of the layout's own.

## Engineering baseline

Tier 2 (public remote, LICENSE). CI in `.github/workflows/ci.yml` runs unit tests, the
coverage gate, ktlint, and lint through the nix devShell, with a `ci-ok` aggregate job.
Dependabot covers GitHub Actions and Gradle monthly; version bumps stay deliberate reviewed
changes.

- Coverage: JaCoCo over the debug unit tests, gated in `app/build.gradle.kts` at 15% line
  coverage against a measured 16.3% (2026-09-29). Ratchet the minimum up when tests
  are added; never down.
- Test exemptions: the tile services, `MainActivity`, `ToggleActivity`, and the I/O bodies of
  `Calls.kt` and `SshClient.kt` need Android framework classes or a live SSH host and have no
  unit tests. `WirelessDebugging.kt` is a thin `Settings.Global` wrapper. Their decision logic
  is extracted and tested: `CallsReply.outcome` and `isStale`, and `describeConnectFailure`.
- Changelog: none kept. Git history and this file's decision log serve the purpose.

## Decision log

- 2026-09-29: merged `wireless-debug-tile` into this multi-tile app. Every reinstall clears
  `WRITE_SECURE_SETTINGS`, and the Wireless debug tile is the on-device way back when adb is
  lost, so `pm grant` runs in the same command as every install.
- 2026-09-29: a detached `setsid` recovery loop over adb was tried for the wireless-debug
  off direction and died with the connection before its first sleep elapsed. The only
  recovery is on the device.
- 2026-09-29: SSH keys are ECDSA because jsch's ed25519 lives in Java 15+ multi-release
  classes that Android ignores.
- 2026-09-29: added CI, dependabot, and the JaCoCo gate under the eng-baseline audit.
