# AKZSA Universal AI One Mic — implementation handoff

This package is based on the original uploaded source archive. It retains the existing Android IME, FastAPI backend, local File AI index, voice recognition, settings, and tests.

## Changes made in this pass

- Added a separate quick-actions row to the keyboard: Tools, New line, Enter/Send, and Search.
- Added independent Grammar Fix and AI Emoji actions under Tools.
- Grammar Fix / AI Emoji read the focused editor through Android's `ExtractedText` API only when **Share editor context** is enabled. They request a full-field rewrite from the existing authenticated backend and present a confirmation preview before replacing text.
- Added a New line handler that inserts only `\n` in multiline text fields. It does not call `performEditorAction`; Enter/Send remains a separate handler using the host editor's IME action policy.
- Added separate GIF and Search entry points. These currently open GIPHY and web search in the browser; they are not yet an embedded AKZSA GIF picker or internal search-results panel.

## Requirements that remain incomplete

The uploaded approved PRD describes more than simple buttons. The following parts are **not represented as complete** in this source:
- Embedded GIPHY search, previews, recents/favourites, and `commitContent` rich-content insertion.
- AKZSA-owned internal search panel and result insertion (the current Search shortcut opens web search externally).
- Full device-level acceptance testing for all eight buttons and all host editors.
- Android compilation and APK packaging could not be verified in this environment because Gradle 8.14.4 could not be downloaded (`services.gradle.org` DNS unavailable).

Voice dictation and language cycling already existed in the supplied source and were retained. The keyboard's existing Enter/Send logic was retained and is now separately exposed in the quick-actions row. The existing FILE button continues to open the private on-device file search.

## Verification actually performed

- Backend tests: `python -m pytest server/tests -q` — **59 passed**.
- Python syntax: `python -m compileall -q server/app server/tests` — passed.
- Android XML parsing — all XML files parsed successfully.
- ZIP integrity — checked after packaging.
- Android Gradle tests/build — attempted but blocked before Kotlin compilation because this environment could not resolve `services.gradle.org`.
- Physical Android device tests — not performed.

## Before using/releasing

1. Open `AKZSA-Android/` in Android Studio with network access and an installed Android SDK.
2. Run `testDebugUnitTest` and `assembleDebug`; fix any Android compile/test issue reported there.
3. Test on-device in a multiline note, a single-line chat composer, Gmail compose, and a protected/password field.
4. Configure the backend URL and raw bearer token in AKZSA Settings. Keep **Share editor context** off unless the user intentionally wants focused text sent to the AI backend.
5. Do not treat this package as a release-ready APK until the missing embedded GIF/internal Search work and device acceptance tests are completed.
