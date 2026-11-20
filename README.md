# VPN-Finder — Android / Android TV

Native Android application for collecting public VPN configuration URIs and checking TCP endpoint reachability. It does not require Termux or Python.

## Device compatibility

- Android 6.0+ (minSdk 23); intended for Android 13.
- ARM64 and other Android ABIs use the same Java APK; no native CPU-specific binary is bundled.
- Android TV / Leanback launcher entry is included.
- Requires Internet access.

## Features

1. Fetches public configuration feeds hosted on GitHub.
2. Extracts VLESS, VMess, Trojan, Shadowsocks, SSR, Hysteria, Hysteria2, TUIC and related URI schemes.
3. Decodes Base64 subscription responses where possible and removes duplicate URIs.
4. TCP-checks endpoints for supported URI formats and reports connection latency.
5. Exports found links to `Download/VPN-Finder/vpn_links_found.txt`; if TCP checks have results, exports those to `vpn_links_tcp_checked.txt`.

**Important:** TCP reachability is not a protocol handshake and does not prove that a VPN tunnel works. This Android-native first release implements collection and TCP screening. Xray-based deep protocol verification from the separate Linux script is not included in this build.

## Build

GitHub Actions builds a debug APK on pushes and manual dispatches:

1. Open **Actions**.
2. Select **Build VPN-Finder APK**.
3. Open the latest run on branch `android-native-app`.
4. Download artifact **VPN-Finder-debug-apk** and extract `app-debug.apk`.
5. Install the APK on the Android device.

The APK is a standard Android application and does not need Termux, Python, root, or changes to the system image.

## Privacy and safety

The app fetches public text feeds over HTTPS. Public VPN configurations are supplied by third parties and may be untrusted, unstable, or malicious. Do not use them for banking, private accounts, or sensitive traffic. The app only collects and TCP-checks links; it does not connect the device to a VPN.
