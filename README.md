# wireless-debug-tile

A Quick Settings tile that turns Android's Wireless debugging on and off in one tap.

## Check whether you need this first

Stock Android has shipped a Wireless debugging Quick Settings tile since Android 12.
Look in Settings > Developer options > Quick settings developer tiles. If it is there,
use it and ignore this app.

Some OEM skins remove those developer tiles. Samsung's One UI is the case this was
written for: on a Galaxy S25 running One UI 8.5, enumerating every tile service on the
device returns 39 results and none of them is a wireless debugging tile.

```
adb shell pm query-services -a android.service.quicksettings.action.QS_TILE
```

If that comes back without a wireless debugging entry, this app fills the gap.

## Alternatives

[WADBS](https://github.com/Smooth-E/wireless-adb-switch) covers the same ground with more
features, including several home-screen widgets and enable-on-boot, and it is on F-Droid.
It is the better choice if you want those, or if you already run Shizuku or root.

This app is smaller and takes a different access route: one `adb` command, granted once,
and no root, no Shizuku, and no companion process.

## What it does

- A Quick Settings tile showing the current state, which flips on tap.
- A launcher shortcut that can be dragged onto a home screen as a one-tap button.
- A setup screen with the current state, the permission status, and the grant command.

It writes one setting, `adb_wifi_enabled`. It has no network access, stores nothing, and
requests no other permission.

## Requirements

- Android 14 or later.
- Developer options enabled.
- One adb session, once, to grant the permission.

## Install

```
nix develop --command gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.clearcmos.wirelessdebugtile android.permission.WRITE_SECURE_SETTINGS
```

Then add the tile, either with the app's "Add Quick Settings tile" button or over adb:

```
adb shell cmd statusbar add-tile com.clearcmos.wirelessdebugtile/com.clearcmos.wirelessdebugtile.WirelessDebuggingTileService
```

The tile is added at the end of the panel, so scroll down to find it. Drag it somewhere
useful with the pencil icon in Quick Settings.

Reinstalling clears the grant. Re-run the `pm grant` command after every upgrade.

## How it works

Android 11 moved network adb behind the `adb_wifi_enabled` global setting. Writing a
global setting needs `WRITE_SECURE_SETTINGS`, which the platform declares as
`signature|privileged|development|installer|role`. The `development` flag is what lets
`adb shell pm grant` give it to an ordinary app. After that,
`Settings.Global.putInt(resolver, "adb_wifi_enabled", 1)` is the whole implementation:
the framework's `AdbService` watches the setting and starts or stops the transport.

Turning wireless debugging off drops any live adb connection, including the one that
granted the permission. The tile is therefore also the way back on, which is why a
missing grant shows as an unavailable tile rather than a hidden one.

## Development

The build runs in a nix devShell pinning the JDK, Gradle, and the Android SDK.

```
nix develop
gradle assembleDebug
gradle testDebugUnitTest
gradle ktlintCheck lintDebug
gradle ktlintFormat
```

## Licence

MIT. See [LICENSE](LICENSE).
