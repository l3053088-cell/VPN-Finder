# VPN-Finder Android launcher app

This project builds a small Android launcher app for the user's Termux installation.

## What it does
- App label: VPN-Finder
- Includes regular Android launcher and Android TV / Leanback launcher entry points.
- Requests `com.termux.permission.RUN_COMMAND`.
- Calls Termux `RunCommandService` to execute:
  `python "$HOME/vpn-finder/vpn_finder.py"`
- Does not bundle or modify the Python script.

## Build
Push this folder to a GitHub repository. GitHub Actions builds a debug APK on every push or when manually started.

In GitHub: Actions -> Build VPN-Finder APK -> latest run -> Artifacts -> VPN-Finder-debug-apk.

Install `app-debug.apk` on the Android device. The debug APK is intended for personal installation/testing.

## First launch
1. Open VPN-Finder.
2. Grant the permission to run commands in Termux when Android asks.
3. If the permission prompt does not appear, open Settings -> Apps -> VPN-Finder -> Permissions / Additional permissions and grant "Run commands in Termux environment".
4. Tap "ЗАПУСТИТЬ ПОИСК".

Termux must already be installed, initialized, and have `allow-external-apps = true`.
