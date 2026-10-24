# 🐾 Lelegram
[![Crowdin](https://badges.crowdin.net/e/a094217ac83905ae1625526d59bba8dc/localized.svg)](https://crowdin.com/project/lelegram)  
Lelegram is a third-party Telegram client with not many but useful modifications.

Based on Telegram for Android 13.0.3 (7183).

- Telegram channel: https://t.me/lelegram_updates
- Feedback: https://github.com/Lelegram/Lelegram/issues

## API, Protocol documentation

Telegram API manuals: https://core.telegram.org/api

MTProto protocol manuals: https://core.telegram.org/mtproto

## Compilation Guide

Use Java 21, Android SDK 36, build tools 36.1.0, NDK 27.3.13750724, and CMake 4.0.2. The project uses Gradle 9.3.1 and Android Gradle Plugin 9.1.0.

1. Download the Lelegram source code ( `git clone https://github.com/Lelegram/Lelegram.git` )
1. Initialize the pinned dependencies with `git submodule update --init --recursive`.
1. Fill out storeFile, storePassword, keyAlias, keyPassword in local.properties to access your release.keystore
1. Go to https://console.firebase.google.com/, create two android apps with application IDs com.fylnx.lelegram and com.fylnx.lelegram.beta, turn on firebase messaging and download `google-services.json`, which should be copied into `TMessagesProj` folder.
1. Open the project in the Studio (note that it should be opened, NOT imported).
1. Provide Telegram API constants through Gradle properties or environment variables: `LELE_APP_ID` and `LELE_APP_HASH`. GitHub Actions reads the same names from repository secrets.
1. You are ready to compile Lelegram.

The pinned Media3 Gradle scripts require `android.builtInKotlin=false` and `android.newDsl=false`; keep these repository settings when building with AGP 9.1.

## Localization

Lelegram is forked from Telegram, thus most locales follows the translations of Telegram for Android, checkout https://translations.telegram.org/en/android/.

As for the Lelegram specialized strings, we use Crowdin to translate Lelegram. Join project at https://crowdin.com/project/lelegram. Help us bring Lelegram to the world!

App builds package official, Lelegram, and accessibility translations from `values*/strings*.xml` into binary localization assets.

Application labels and their alias targets must remain Android string resources. `TelegramStringsTask.PLATFORM_APP_NAME_RESOURCES` keeps them available to system Settings and launchers.
