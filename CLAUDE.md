# wireless-debug-tile

A Quick Settings tile that toggles Android's Wireless debugging. Developed against a
Galaxy S25 (SM-S931W) on Android 16 / One UI 8.5. Nothing in it is Samsung specific.

## Why it exists

Stock Android has a built-in Wireless debugging developer tile from Android 12 on. One UI
strips the developer tiles, verified by enumerating every tile service on the device:

```
adb shell pm query-services -a android.service.quicksettings.action.QS_TILE
```

39 services, no wireless debugging tile. Check this before assuming a device needs the app.

## The mechanism

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
app/src/main/kotlin/com/clearcmos/wirelessdebugtile/
  WirelessDebugging.kt            read, write, observe the setting; permission check
  TileAppearance.kt               pure state mapping, the only unit-tested logic
  WirelessDebuggingTileService.kt the Quick Settings tile
  ToggleActivity.kt               invisible flip-and-toast, target of the shortcut
  MainActivity.kt                 setup and status, also the tile's long-press target
app/src/main/res/xml/shortcuts.xml  static launcher shortcut to ToggleActivity
```

`ToggleActivity` extends `android.app.Activity`, not `AppCompatActivity`, so it can use a
plain translucent platform theme and never inflate a view.

## Build

The nix devShell pins JDK 17, Gradle, and an SDK with platforms 35 and 36 plus
build-tools 35.0.0. `buildToolsVersion` is pinned in `app/build.gradle.kts` so the
read-only nix store SDK is never asked to fetch another revision, and `GRADLE_OPTS` in
`flake.nix` points aapt2 at the SDK copy for the same reason.

```
nix develop --command gradle assembleDebug
nix develop --command gradle testDebugUnitTest
nix develop --command gradle ktlintCheck lintDebug
nix develop --command gradle ktlintFormat
```

Lint runs with `warningsAsErrors = true`. Version-drift checks are disabled because they
fail on every upstream release; dependency and SDK bumps are deliberate changes here.

## Testing on a device

Reinstalling clears the grant, so a full cycle is:

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.clearcmos.wirelessdebugtile android.permission.WRITE_SECURE_SETTINGS
adb shell cmd statusbar add-tile com.clearcmos.wirelessdebugtile/com.clearcmos.wirelessdebugtile.WirelessDebuggingTileService
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
