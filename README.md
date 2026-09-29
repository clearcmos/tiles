# tiles

Custom Android Quick Settings tiles, in one app. Developed against a Galaxy S25 on
Android 16 / One UI 8.5.

This app replaces `wireless-debug-tile`, which held the first tile on its own.

| Tile | What a tap does | Needs |
| --- | --- | --- |
| Wireless debug | Turns Wireless debugging on or off | `WRITE_SECURE_SETTINGS`, granted once over adb |
| Headset calls | Turns the Bluetooth "Calls" switch on or off on a fixed set of headsets | A workstation on the same network that reaches the phone over wireless adb |

The app also has a setup screen showing each tile's state, what it needs, and a button to
add the tile.

## Wireless debug

### Check whether you need it

Stock Android has shipped a Wireless debugging Quick Settings tile since Android 12. Look
in Settings > Developer options > Quick settings developer tiles. If it is there, use it.

One UI removes those developer tiles. On the S25, enumerating every tile service returns
none for wireless debugging:

```
adb shell pm query-services -a android.service.quicksettings.action.QS_TILE
```

[WADBS](https://github.com/Smooth-E/wireless-adb-switch) covers the same ground with more
features and is on F-Droid.

### How it works

Android 11 moved network adb behind the `adb_wifi_enabled` global setting. Writing a
global setting needs `WRITE_SECURE_SETTINGS`, which the platform declares as
`signature|privileged|development|installer|role`. The `development` flag lets
`adb shell pm grant` give it to an ordinary app. After that,
`Settings.Global.putInt(resolver, "adb_wifi_enabled", 1)` is the whole implementation.

Turning wireless debugging off drops any live adb connection, including the one that
granted the permission, so the tile is also the way back on.

A launcher shortcut for this tile can be dragged onto a home screen as a one-tap button.

## Headset calls

Each paired headset's Bluetooth details page has a "Calls" switch. It is the HFP
(hands-free profile) connection policy, and changing it needs
`BluetoothHeadset.setConnectionPolicy`, a system API behind `BLUETOOTH_PRIVILEGED`. That
permission is `signature|privileged`, so no installable app can hold it and `pm grant`
refuses it. The adb shell user does hold it.

So the tile does not do the work itself. It runs one command over SSH on a workstation:
`calls on`, `calls off`, or `calls status`. The workstation runs a small helper on the
phone at adb shell privilege, over wireless adb, and prints one line per headset:

```
AA:BB:CC:00:00:01 calls=on hfp=connected Soundcore Life Q30
```

The tile shows On when every headset has Calls on, Off when none do, and Mixed otherwise.
A tap turns Calls off only from On, and on from any other state. The headset list lives on
the workstation, not in the app. The workstation side is not in this repo.

Limits:

- It works only on the home network. Wireless debugging is tied to one access point, so
  the workstation cannot reach the phone's adb from anywhere else. A VPN does not help.
- Allowing Calls on a headset that is not connected makes Android try to connect it,
  and the phone briefly shows "Can't connect" for one that is off or in its case. The
  Settings switch behaves the same way.
- The tile shows the last reply. It asks again when the shade opens and the last reply is
  more than two minutes old, so a change made in Settings shows up late.

### Setup

1. Open the app, enter the workstation host and login name, and tap Save.
2. The app generates an ECDSA key on first launch. Add the public key to
   `~/.ssh/authorized_keys` on the workstation, pinned with `restrict,command="..."` to
   the script that answers the three commands. It is also readable over adb:
   `adb shell cat /sdcard/Android/data/com.clearcmos.tiles/files/id_ecdsa.pub`
3. Tap Refresh. The first connection records the workstation's host key and refuses a
   different one later.

The private key stays in the app's private storage and is excluded from backup.

## Install

```
nix develop --command gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.clearcmos.tiles android.permission.WRITE_SECURE_SETTINGS
adb shell cmd statusbar add-tile com.clearcmos.tiles/com.clearcmos.tiles.wirelessdebug.WirelessDebuggingTileService
adb shell cmd statusbar add-tile com.clearcmos.tiles/com.clearcmos.tiles.calls.CallsTileService
```

Reinstalling clears the grant. Re-run the `pm grant` command after every upgrade. New tiles
are added at the end of the panel, so scroll down to find them.

## Requirements

- Android 14 or later.
- Developer options enabled, and one adb session to grant the permission.

## Development

The build runs in a nix devShell pinning the JDK, Gradle, and the Android SDK.

```
nix develop
gradle assembleDebug
gradle testDebugUnitTest
gradle jacocoDebugCoverageVerification
gradle ktlintCheck lintDebug
gradle ktlintFormat
```

## Licence

MIT. See [LICENSE](LICENSE).
