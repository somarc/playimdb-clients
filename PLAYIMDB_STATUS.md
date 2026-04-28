# PlayIMDB — Project Status

## What it is
A search tool that queries the IMDB suggestion API and opens results on `playimdb.com/title/{id}`. Built in two forms: a Chrome extension and an Android TV app for Amazon Firestick.

---

## Locations on disk

| Artifact | Path |
|---|---|
| Chrome extension source | `/Users/mhess/Downloads/playimdb-extension/` |
| Android TV source (Replit) | `/Users/mhess/Downloads/playimdb-tv/` |
| Built APK (ready to install) | `/Users/mhess/Downloads/playimdb-tv/app/build/outputs/apk/debug/app-debug.apk` |

---

## Android TV app — what Replit built

- Kotlin + Jetpack Compose, MVVM architecture
- `TvLazyColumn` with explicit D-pad focus traversal (`focusProperties`)
- Animated focus indicators (border 1dp → 4dp, row scale on focus)
- Voice search via `RecognizerIntent` — mic button beside the search field, result drops into search automatically
- `BackHandler` — back key clears search and returns focus to field
- Opens results in Silk browser via `ACTION_VIEW` intent

## Build config

- compileSdk / targetSdk: 34, minSdk: 21 (Fire OS 5+)
- Kotlin 1.9.22, Compose Compiler 1.5.8, AGP 8.2.2, Gradle 8.5
- `local.properties` written with: `sdk.dir=/opt/homebrew/share/android-commandlinetools`
- Known warning (non-breaking): `ImdbRepository.kt:55` — `optString("qid", null)` should be `optString("qid", "")` 

### To rebuild
```bash
cd /Users/mhess/Downloads/playimdb-tv
./gradlew assembleDebug
```

---

## Install on Firestick — next session

### Step 1 — Unlock Developer Options (if not done)
1. Settings → My Fire TV → About
2. Click your device name **7 times rapidly**
3. Developer Options appears in My Fire TV menu
4. Enable: ADB Debugging → ON, Apps from Unknown Sources → ON

### Step 2 — Serve the APK via ngrok
```bash
# Terminal 1
cd /Users/mhess/Downloads/playimdb-tv/app/build/outputs/apk/debug
python3 -m http.server 8080

# Terminal 2
ngrok http 8080
```

### Step 3 — Install via Downloader app
1. Install **Downloader** (by AFTVnews) from the Amazon Appstore on the Firestick
2. Open Downloader, enter the ngrok URL with `/app-debug.apk` appended:
   ```
   https://<your-ngrok-url>.ngrok-free.app/app-debug.apk
   ```
3. Download → Install → Launch

First launch: Settings → Applications → Manage Installed Applications → PlayIMDB → Launch  
After first launch: appears in Your Apps & Channels

---

## What's next
- [ ] Sideload APK onto Firestick
- [ ] Test voice search button on real hardware
- [ ] Fix minor Kotlin warning in `ImdbRepository.kt:55`
- [ ] Consider GitHub repo so Claude Code, Replit, and Grok can work off same source
