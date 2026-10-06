# VaultXRP Wallet for Android

Local-first, non-custodial XRPL wallet. Keys are generated and stored only on this device. Recovery phrases are never transmitted.

Package: `com.vaultxrplwallet.app`  
Display name: VaultXRP  
Version: 1.2.0

## Requirements

- JDK 17 (`JAVA_HOME`)
- Android SDK 35 (`ANDROID_HOME` or `local.properties`)

This repo expects:

```
JAVA_HOME=$HOME/tools/jdk-17
ANDROID_HOME=$HOME/Library/Android/sdk
```

## Build

```sh
export JAVA_HOME="$HOME/tools/jdk-17"
cd android
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:bundleRelease
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`  
Release APK: `app/build/outputs/apk/release/app-release.apk`  
Release AAB: `app/build/outputs/bundle/release/app-release.aab`

## Emulator

```sh
export ANDROID_HOME="$HOME/Library/Android/sdk"
"$ANDROID_HOME/emulator/emulator" -avd vaultxrp_api35 &
"$ANDROID_HOME/platform-tools/adb" install -r app/build/outputs/apk/debug/app-debug.apk
```

## Security

- Seeds stay in app-private storage, encrypted with an Android Keystore AES-256-GCM key.
- Unlock, sign, reveal, export, and delete require device authentication.
- No analytics SDK. No wallet-registration API. No admin panel.
- Encrypted backups use AES-256-GCM and PBKDF2-HMAC-SHA256 (310,000 iterations).
