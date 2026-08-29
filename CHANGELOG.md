# Changelog

All notable changes to **NOBODY VPN** are documented here.

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
