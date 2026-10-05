# DayDrop

Android app with a daily selection of facts, historical events, articles and quizzes. Built with Kotlin and Jetpack Compose.

## Build

Requires JDK 17 and Android SDK 35. Set the SDK path in your local `local.properties` file.

```sh
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Content is stored in `app/src/main/assets/content/`. The unit tests check its structure and the daily selection logic.

Release builds require a private keystore and the `DAYDROP_KEYSTORE`, `DAYDROP_KEYSTORE_PASSWORD`, `DAYDROP_KEY_ALIAS` and `DAYDROP_KEY_PASSWORD` environment variables. Signing material is excluded from Git.
