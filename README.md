# 🐾 Lelegram
[![Crowdin](https://badges.crowdin.net/e/a094217ac83905ae1625526d59bba8dc/localized.svg)](https://crowdin.com/project/lelegram)  
Lelegram is a third-party Telegram client with not many but useful modifications.

- Telegram channel: https://t.me/lelegram_updates
- Feedback: https://github.com/Lelegram/Lelegram/issues

## API, Protocol documentation

Telegram API manuals: https://core.telegram.org/api

MTProto protocol manuals: https://core.telegram.org/mtproto

## Compilation Guide

1. Download the Lelegram source code ( `git clone https://github.com/Lelegram/Lelegram.git` )
1. Fill out storeFile, storePassword, keyAlias, keyPassword in local.properties to access your release.keystore
1. Go to https://console.firebase.google.com/, create two android apps with application IDs com.fylnx.lelegram and com.fylnx.lelegram.beta, turn on firebase messaging and download `google-services.json`, which should be copied into `TMessagesProj` folder.
1. Open the project in the Studio (note that it should be opened, NOT imported).
1. Fill out values in `TMessagesProj/src/main/java/com/fylnx/lelegram/Extra.java` – there’s a link for each of the variables showing where and which data to obtain.
1. You are ready to compile Lelegram.

## Localization

Lelegram is forked from Telegram, thus most locales follows the translations of Telegram for Android, checkout https://translations.telegram.org/en/android/.

As for the Lelegram specialized strings, we use Crowdin to translate Lelegram. Join project at https://crowdin.com/project/lelegram. Help us bring Lelegram to the world!
