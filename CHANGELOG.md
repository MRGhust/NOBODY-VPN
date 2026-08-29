# Changelog

All notable changes to **NOBODY VPN** are documented here.

## v0.6.1-beta — 2026-08-29

### 🐞 Fixed
- **VPN failed to start** — `core init failed: ... xray.xudp.basekey: invalid value (BaseKey must be
  32 bytes)`. The XUDP base key passed to the core must be exactly 32 bytes (base64url-encoded);
  the app now derives a stable key from the device `ANDROID_ID` (same approach as v2rayNG),
  fixing `start error: core init failed` for every connection attempt.
- **Language switching reverted to English** — the in-app locale is now owned by
  `AppCompatDelegate` and persisted via `AppLocalesMetadataHolderService` (`autoStoreLocales`),
  so choosing **فارسی** sticks immediately and survives app restarts / process death. The old
  DataStore sync effect that could bounce the app back to English was removed.
- **Could not return to HOME from the bottom menu** — tab navigation is now deterministic
  (always collapses to the start destination first), and **selecting a server now returns you
  to Home automatically**, matching standard VPN app UX.

### ⚡ Size (about −54%)
- Enabled R8 `minifyEnabled` + `shrinkResources` — the unminified dex (~56 MB, mostly
  `material-icons-extended`) shrinks to ~4 MB.
- Limited packaged locales to `en` / `fa` (strips ~40 library languages).

| Build | v0.6.0-beta | v0.6.1-beta |
|-------|------------:|------------:|
| arm64-v8a | 99 MB | **45 MB** |
| universal | 204 MB | **151 MB** |

### 🧾 Technical
- `versionCode` 3, `versionName` 0.6.1-beta

## v0.6.0-beta — 2026-08-29

> Re-baselined the version line to a proper beta series for the open-source release.

### 🐞 Fixed
- **VPN permission flow** — the system VPN consent dialog is now requested *before* connecting.
  This resolves the `VPN permission was not granted` error on first launch / first connect.
- **Quick Settings tile** — when VPN consent is still missing, the tile now opens the app and
  triggers the consent dialog instead of failing silently.
- **Language switching** — changing the app language (فارسی / English / System) is now applied
  instantly without needing to restart the app.
- Subscription refresh result now shows a properly localized message instead of raw symbols.
- Generic `OK` / `Cancel` dialogs now use localized strings.

### ✨ UI
- **Redesigned Home screen** — cleaner minimal layout with a status pill header.
- **New animated power button** — gradient status ring (glow while connected, rotating arc while
  connecting), press-scale spring animation and haptic feedback.
- Clipboard import is now a first-class action in the Servers toolbar menu, plus a paste button
  directly inside the manual add dialog.

### 🧾 Technical
- `versionCode` 2, `versionName` 0.6.0-beta
- Optional per-ABI build flag: `./gradlew assembleRelease -Pabi=arm64-v8a`
- Release signing is skipped automatically when no keystore is present (CI friendly)

## v1.0.0 — initial private build

- First feature-complete internal build (Xray core 26.x, TUN mode, subscriptions, QR import,
  split tunneling, routing/DNS settings, live speed, tile, boot connect, logs).
