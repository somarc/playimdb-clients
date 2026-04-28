# playimdb-clients

Two client apps that search IMDB titles and open them on **playimdb.com**.

| App | Folder | Platform |
|---|---|---|
| Chrome extension | [`chrome-extension/`](./chrome-extension) | Desktop browser (Manifest v3 popup) |
| Android TV app   | [`playimdb-tv/`](./playimdb-tv)         | Amazon Firestick / Android TV (sideload) |

Both query the IMDB suggestion API (`v2.sg.media-imdb.com/suggestion/...`) with a 350 ms debounce and open the chosen title at `https://playimdb.com/title/<id>`.

---

## Chrome extension

### Install (developer mode)

1. Open `chrome://extensions`
2. Toggle **Developer mode** on (top right)
3. Click **Load unpacked** and pick the [`chrome-extension/`](./chrome-extension) folder

The PlayIMDB icon appears in your toolbar. Pin it for quick access.

### Files

- `manifest.json` — Manifest v3 config
- `popup.html` / `popup.css` / `popup.js` — extension popup UI
- `icons/` — 16, 48, 128 px PNG icons (plus the source SVGs)

No build step. After editing any file, click the reload icon for the extension on `chrome://extensions`.

### Package for distribution

If you want a single `.zip` to share or store:

```bash
cd chrome-extension
zip -r ../playimdb-extension.zip . -x "*.svg"
```

SVGs are excluded because the Chrome Web Store only needs the compiled PNGs.

---

## Android TV app (Firestick)

Native Kotlin + Jetpack Compose, MVVM architecture. No Google Play Services dependency.

### Splash screen

A 6-second branded intro plays on launch before the search screen fades in.

<video src="loading.mp4" autoplay loop muted playsinline width="720"></video>

### Features
- `TvLazyColumn` with explicit D-pad focus traversal (`focusProperties`) for remote-control navigation
- Animated focus indicators — border grows from 1 dp to 4 dp and the focused row scales up
- Voice search via `RecognizerIntent` — mic button beside the search field; recognized text drops into the search field automatically
- `BackHandler` — back key clears the search and returns focus to the input field
- Results open in the Silk browser via an `ACTION_VIEW` intent

### Build config

| Setting | Value |
|---|---|
| compileSdk / targetSdk | 34 |
| minSdk | 21 (Fire OS 5+) |
| Kotlin | 1.9.22 |
| Compose Compiler | 1.5.8 |
| AGP | 8.2.2 |
| Gradle | 8.5 |

### Prerequisites

- **Android Studio** (recommended — bundles JDK 17 and the Android SDK), or the standalone command-line SDK tools
- **adb** — included with the Android SDK. Add the `platform-tools` directory to your `PATH` to call `adb` from anywhere:
  - Android Studio install: `~/Library/Android/sdk/platform-tools/` (macOS)
  - Homebrew CLI tools install: `/opt/homebrew/share/android-commandlinetools/platform-tools/`
- One or more **Firesticks** with developer/ADB access enabled (see below)
- The Firestick(s) on the same network as your computer

#### Enable developer options on a Firestick

1. **Settings → My Fire TV → About** — click your device name **7 times** to reveal Developer Options (newer Fire OS hides it by default)
2. **Settings → My Fire TV → Developer Options → ADB debugging → ON**
3. **Settings → My Fire TV → Developer Options → Apps from Unknown Sources → ON**

### Build the debug APK

First time only — point Gradle at your Android SDK:

```bash
cd playimdb-tv

# Android Studio install (macOS)
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties

# Homebrew CLI tools install (macOS)
echo "sdk.dir=/opt/homebrew/share/android-commandlinetools" > local.properties
```

Adjust the path for Windows or Linux as needed.

Then build:

```bash
./gradlew assembleDebug
```

Output:

```
app/build/outputs/apk/debug/app-debug.apk
```

The first build takes a few minutes (Gradle downloads Compose, AndroidX, OkHttp, Coil, etc.). Subsequent builds are seconds.

---

### Install via ADB (local network)

#### Single Firestick

Replace `192.168.0.109` with your Firestick's IP address (find it at **Settings → My Fire TV → About → Network**):

```bash
adb connect 192.168.0.109:5555
```

The first time you connect, an authorization prompt appears on the Firestick TV screen — select **Always allow from this computer**, then proceed:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`-r` reinstalls over an existing copy.

#### Multiple Firesticks

Connect to each device once — the first connection to each Firestick will show an authorization prompt on the TV screen; select **Always allow from this computer** before continuing:

```bash
adb connect 192.168.0.109:5555
adb connect 192.168.0.235:5555
adb connect 192.168.0.204:5555
```

Verify they're all attached:

```bash
adb devices
```

You should see something like:

```
List of devices attached
192.168.0.109:5555      device
192.168.0.235:5555      device
192.168.0.204:5555      device
```

Then install on each by IP using `-s <serial>`:

```bash
adb -s 192.168.0.109:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 192.168.0.235:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 192.168.0.204:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

#### Uninstall

```bash
adb -s 192.168.0.109:5555 uninstall com.playimdb.tv
```

---

### Troubleshooting

| Symptom | Fix |
|---|---|
| `SDK location not found` | Create `playimdb-tv/local.properties` with `sdk.dir=...` pointing at your Android SDK |
| `unable to connect to <ip>:5555` | Confirm the Firestick is on the same network and ADB debugging is enabled. On first connection, a prompt to authorize the computer appears on the Firestick — accept it |
| `device unauthorized` | An RSA key prompt is waiting on the Firestick — click **Always allow from this computer** |
| Voice button does nothing | Fire OS's speech recognizer may not be initialized. Make sure a region is set (**Settings → My Fire TV → About → Region**) and reboot the Firestick; the recognizer initializes on the first boot after region is configured |
| Kotlin warning at `ImdbRepository.kt:55` — `optString("qid", null)` | Non-breaking; replace with `optString("qid", "")` to silence it |

### Key files

- `app/src/main/java/com/playimdb/tv/MainActivity.kt` — activity entry, voice recognizer, browser intent
- `app/src/main/java/com/playimdb/tv/SearchViewModel.kt` — debounced search state
- `app/src/main/java/com/playimdb/tv/ImdbRepository.kt` — IMDB suggestion API client
- `app/src/main/java/com/playimdb/tv/ui/SearchScreen.kt` — Compose UI (search field, mic button, result list)
- `app/src/main/AndroidManifest.xml` — declares `LEANBACK_LAUNCHER` so the app shows on the Fire TV home screen

---

Personal-use project. Not affiliated with IMDB or playimdb.com.
