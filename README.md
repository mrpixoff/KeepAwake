<div align="center">
  <img src="docs/icon.png" width="96" height="96" alt="KeepAwake icon">
  <h1>KeepAwake</h1>
  <p>Minimalist Android utility in Nothing OS style that keeps your screen awake with a Quick Settings tile and timer.</p>
</div>

<p align="center">
  <img src="docs/screenshots/main-light.png" width="240" alt="KeepAwake light theme">
  &nbsp;
  <img src="docs/screenshots/main-dark.png" width="240" alt="KeepAwake dark theme">
</p>

> **Public beta — v0.9.0-beta.** Core functionality is tested (120 unit tests), but behavior can still vary across OEM firmware. Feedback and issue reports are welcome.

## Why KeepAwake?

Sometimes Android's display timeout gets in the way — reading recipes, following docs, navigating, debugging, or keeping a dashboard visible. KeepAwake gives you a fast, native-feeling switch instead of digging through system settings every time.

- **One tap from anywhere.** Toggle screen holding from the main app or from the Quick Settings tile in the notification shade.
- **Resilient on aggressive skins.** The app writes `SCREEN_OFF_TIMEOUT = Int.MAX_VALUE` and also holds a `SCREEN_BRIGHT_WAKE_LOCK` as a safety net for shells like Realme UI / ColorOS, One UI, MIUI and other OEM builds that may reset unusual timeout values.
- **Timer or infinite mode.** Keep the screen on indefinitely, or auto-stop after 15, 30 or 60 minutes.
- **Clean Nothing OS look.** Monochrome light/dark interface, monospace typography, clear `STANDBY` / `REC` states.
- **Safe cleanup.** The original screen timeout is restored on stop, timer expiry, screen off, low battery, process restart and reboot.

## Permissions

| Permission | Why it is needed |
|---|---|
| `WRITE_SETTINGS` | Allows KeepAwake to change the system screen timeout (`SCREEN_OFF_TIMEOUT`) while the mode is active. This is the core mechanism that keeps the display awake. |
| `POST_NOTIFICATIONS` | Required on Android 13+ to show the foreground-service notification with the current status and a quick “Turn off” action. |

The app also uses `WAKE_LOCK` and foreground-service permissions internally as part of the keep-awake/safety-net behavior. It does not request internet access and has no analytics or ads.

## Installation

1. Open the [Releases](../../releases) page and download `KeepAwake-v0.9.0-beta.apk`.
2. On your Android phone, allow installing apps from your browser/file manager if prompted.
3. Install the APK (Android 8.0 / API 24 or newer required).
4. Launch KeepAwake and grant **Modify system settings** (`WRITE_SETTINGS`) when asked.
5. On Android 13+, allow notifications so the foreground service can show its status/control notification.
6. Optional: add the **KeepAwake** tile to Quick Settings for one-tap access from any app.

> Note: This is a public beta release signed with a developer key. Play Protect may show a generic warning for sideloaded APKs.

## Building from Source

Requires JDK 17 and Android SDK 35.

```bash
git clone https://github.com/mrpixoff/KeepAwake.git
cd KeepAwake
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## License

MIT
