# AKZSA Universal AI – One Mic v3

This ZIP contains the Android Studio project and FastAPI backend source for the AKZSA one-microphone upgrade.

## Main behavior

Use the single **Talk** microphone for normal dictation, asking AKZSA to write something, or asking it to edit text. The app sends the transcript to the backend in automatic intent mode. You do not need to switch between TYPE, WRITE, and CMD.

The **FILE** control remains a separate explicit control for browsing/searching the private on-device file index. It is not a dictation/write/command mode.

## Project folders

- `AKZSA-Android/` — Android Studio project and keyboard source.
- `server/` — FastAPI backend deployed to Render.
- `docs/` — architecture, privacy, testing, and release guidance.
- `UNIVERSAL_AI_UPGRADE_STATUS.md` — exact implemented/not-implemented scope and verification status.

## Backend setup

Deploy `server/` as the Render service root. Configure:

- `GROQ_API_KEY` — your provider API key.
- `AKZSA_SERVER_TOKEN` — a long random token; enter the same token in AKZSA settings.
- `SARVAM_API_KEY` — Sarvam AI key; Hindi, Hinglish and other Indian-language dictation uses Sarvam Saaras (Groq stays as backup and for English).
- `GIPHY_API_KEY` — free key from developers.giphy.com; powers GIF/sticker search via `GET /v1/gifs/search` (the app never holds this key).

Keep the backend URL on HTTPS. Never put your provider API key in Android source code or the APK.

## Android build

1. Open the `AKZSA-Android/` folder in Android Studio.
2. Allow Gradle sync to download the project's required Gradle/Android dependencies.
3. Run the unit tests.
4. Build and install a debug APK.
5. Test in messaging, email, browser, and notes apps before using it for important text.

## Important limits

This is source code, not a prebuilt APK. The backend test suite passed in the packaging environment, but Android compilation and physical-device behavior were not verified there. GIF/sticker provider integration and a full general-purpose editing-plan executor are not included in this version. See `UNIVERSAL_AI_UPGRADE_STATUS.md` for details.
