# Handover: AKZSA 1.8.0 for the APK building team

## State in one paragraph
Source for the keyboard (Kotlin) and the FastAPI backend. The AI File Manager (File AI), its 45 MB MobileCLIP model,
OCR/PDF/ML Kit/ONNX libraries, storage permissions and the server file-search endpoint have been **removed**. Backend
tests pass (58). Pure-logic code for Snippets and Backtrack was compiled and checked with the Kotlin compiler. The Android
app as a whole has NOT been built with Gradle or run on a device: budget time for a first Gradle run and a few small compile fixes.

## Do these in order
1. **Compile and test:** `cd AKZSA-Android && ./gradlew :app:testDebugUnitTest :app:assembleDebug` (Windows: `scripts/build.ps1`).
   Java 17+ and Android SDK 36. Most likely places for a compile fix: `GifPanel.kt`, `GifThumbnails.kt`, `TypingController.kt`,
   `KeyboardRenderer.kt`, `AkzsaApp.kt` (all were edited in 1.8.0 without a compiler for Android classes).
2. **GIPHY key (server only):** free key at developers.giphy.com. Set `GIPHY_API_KEY` on the server (Render env / `server/.env`).
   The app no longer holds any GIPHY key; it calls `/v1/gifs/search` with the same bearer token as the AI features.
3. **Backend:** deploy `server/` to Render (`server/render.yaml`). Set `GROQ_API_KEY` and `AKZSA_SERVER_TOKEN`; the same token
   goes into the app settings. Check `/health`. **Rotate any key that was ever inside a zip.**
4. **Signing:** copy `keystore.properties.example` to `keystore.properties`, create a keystore, then `scripts/build.sh release-aab`.
5. **Device test on 3+ phones with a SIGNED, minified build:** follow `docs/DEVICE_TEST_PLAN.md`.
6. Bump `versionCode`, upload to internal testing, complete `docs/PLAY_STORE_CHECKLIST.md`.

## 1.8.0 device checks
* **GIF panel:** open GIF, thumbnails appear and ANIMATE (Android 9+; Android 8 shows the first frame). Type a word with the
  keyboard keys, press Enter, results change. Tap a GIF in WhatsApp (sends) and in an app that rejects GIFs (link copied).
* **Snippets:** Settings > Snippets, add `my signature = Best regards,\nJordan`, say "my signature": text appears instantly, no network call.
* **Backtrack:** dictate "Meet at 5pm", then say "actually make that 6pm": the old phrase is replaced, trailing space kept.
  Type a letter first, then try again: it must refuse and change nothing.
* New Line works in WhatsApp, Instagram, Telegram; Send/Go/Search key is accent coloured; no duplicate Enter.
* Cursor row and Clipboard row work in 3 apps; nothing is captured from password fields.

## Known open items
See `docs/KNOWN_LIMITATIONS.md`. Phase 4 is not in this tree.
