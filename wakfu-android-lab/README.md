# WAKFU Android Compatibility Probe 0.1

**Not a playable WAKFU port.** This lightweight, unaffiliated research utility executes a real OpenGL ES 2 smoke test on an ARM64 Android device, inventories its RAM/CPU/GPU, and scans a user-selected *legally obtained* WAKFU Linux directory or small ZIP for client JARs and native components. It never runs Ankama binaries, modifies accounts, bypasses login, or contacts game servers.

## Hardware target
MediaTek Helio G99, Mali-G57 MC2, 6 GiB RAM, Android.

## Known blocker
WAKFU Linux currently bundles JOGL 2.6.x, native JOGL/OpenGL, X11 and desktop runtime components. Android's ART does not execute desktop Java JARs as native Android apps. Linux x86_64 .so binaries cannot be loaded in ARM64 Android without suitable binary translation and ABI bridging. The graphics smoke-test FPS is NOT indicative of WAKFU gameplay.

## Build
GitHub Actions (on this dedicated experiment branch), or Gradle 8.9 + JDK 17 + Android SDK 35: `gradle :app:assembleDebug`.

## Usage
1. Install debug APK (no extra permissions needed).
2. Run OpenGL ES test; choose **Share compatibility report**.
3. Optionally inspect a WAKFU Linux directory using Android's folder picker (recommended), or a ZIP under 512MB. Archives are not uploaded.
4. Report any rendering failures and the diagnostic output.

## Next engineering gate
Obtain lawful game binaries and Ankama's permission for authorized online compatibility testing. Evaluate viable ARM64-JRE + desktop JOGL/X11 compatibility strategy. Do not claim this utility boots WAKFU.
