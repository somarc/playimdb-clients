# playimdb-clients

Two small clients that search IMDB titles and open them on **playimdb.com**.

| App | Folder | Platform |
|---|---|---|
| Chrome extension | [`extension/`](./extension) | Desktop browser, Manifest V3 popup |
| Android TV app | [`android-tv/`](./android-tv) | Amazon Fire TV / Android TV sideload |

Both clients query the IMDB suggestion API (`v2.sg.media-imdb.com/suggestion/...`) with a 350 ms debounce and open the selected title at `https://playimdb.com/title/<id>`.

## Media

Source splash videos live in [`media/`](./media). The Android app currently packages only `media/loading2.mp4` as `android-tv/app/src/main/res/raw/loading2.mp4` to keep the APK smaller.

## Chrome Extension

### Install

1. Open `chrome://extensions`
2. Toggle **Developer mode** on.
3. Click **Load unpacked** and pick [`extension/`](./extension).

The PlayIMDB icon appears in the browser toolbar. Pin it for quick access.

### Features

- Debounced IMDB title search.
- Cancels stale in-flight searches while typing.
- Arrow up/down selects results; Enter opens the selected result.
- Clicking a result opens `playimdb.com` in a new tab.

### Package

```bash
cd extension
zip -r ../playimdb-extension.zip .
```

## Android TV App

Native Kotlin + Jetpack Compose. No Google Play Services dependency.

### Splash Screen

A branded intro video plays on launch before the search screen appears. The packaged splash resource is:

```text
android-tv/app/src/main/res/raw/loading2.mp4
```

The splash exits when playback ends, with a 20-second fallback timeout.

### Features

- TV-focused Compose UI.
- `TvLazyColumn` result list with D-pad focus traversal.
- Live Charts mode for Top Movies, Top TV, Popular Movies, and Popular TV.
- Animated focus indicators for remote-control navigation.
- Search input supports the platform keyboard, including its mic button when available.
- Back clears the search and returns focus to the input field.
- Results open in the browser via an `ACTION_VIEW` intent.
- Network searches are cancellable so stale requests do not keep running after new input.

Charts are fetched directly from IMDb on the device and cached locally for 24 hours. The repo does not publish IMDb-derived chart JSON.

### Build Config

| Setting | Value |
|---|---|
| compileSdk / targetSdk | 34 |
| minSdk | 21 |
| Compose Compiler | 1.5.8 |
| Gradle | 8.5 |

### Build

First time only, make sure `android-tv/local.properties` points at your Android SDK:

```bash
cd android-tv
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

Build the debug APK:

```bash
./gradlew assembleDebug
```

Output:

```text
android-tv/app/build/outputs/apk/debug/app-debug.apk
```

Run lint and build together:

```bash
./gradlew lintDebug assembleDebug
```

### Install Via ADB

Replace `192.168.0.109` with the Fire TV / Android TV device IP address.

```bash
adb connect 192.168.0.109:5555
adb -s 192.168.0.109:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

Uninstall:

```bash
adb -s 192.168.0.109:5555 uninstall com.playimdb.tv
```

### Troubleshooting

| Symptom | Fix |
|---|---|
| `SDK location not found` | Create `android-tv/local.properties` with `sdk.dir=...` pointing at your Android SDK. |
| `unable to connect to <ip>:5555` | Confirm the TV device is on the same network and ADB debugging is enabled. |
| `device unauthorized` | Accept the RSA authorization prompt on the TV screen. |
| Search fails repeatedly | Check network connectivity from the TV device. |

### Key Files

- [`android-tv/app/src/main/java/com/playimdb/tv/MainActivity.kt`](./android-tv/app/src/main/java/com/playimdb/tv/MainActivity.kt) - activity entry and browser intent.
- [`android-tv/app/src/main/java/com/playimdb/tv/SearchViewModel.kt`](./android-tv/app/src/main/java/com/playimdb/tv/SearchViewModel.kt) - debounced search state.
- [`android-tv/app/src/main/java/com/playimdb/tv/ImdbRepository.kt`](./android-tv/app/src/main/java/com/playimdb/tv/ImdbRepository.kt) - IMDB suggestion API client.
- [`android-tv/app/src/main/java/com/playimdb/tv/ui/SearchScreen.kt`](./android-tv/app/src/main/java/com/playimdb/tv/ui/SearchScreen.kt) - Compose search UI.
- [`android-tv/app/src/main/java/com/playimdb/tv/ui/SplashScreen.kt`](./android-tv/app/src/main/java/com/playimdb/tv/ui/SplashScreen.kt) - splash video playback.
- [`android-tv/app/src/main/AndroidManifest.xml`](./android-tv/app/src/main/AndroidManifest.xml) - launcher and app declarations.

Personal-use project. Not affiliated with IMDB or playimdb.com.
