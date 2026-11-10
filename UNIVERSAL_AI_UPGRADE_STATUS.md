# AKZSA Universal AI – One Mic v3 source status

## Included in this source package

- One microphone entry point for ordinary dictation, AI writing, and AI editing; the keyboard does not ask the user to choose TYPE / WRITE / CMD.
- The keyboard sends speech to the backend with `mode="auto"` and includes selected/surrounding editor context only when the user's context-sharing setting permits it.
- Backend AI prompt classifies dictation, writing, and supported edits by intent rather than requiring one exact command phrase.
- Generated text, emoji characters, email drafts, and replies use the existing AI text-generation action.
- Existing local FILE search/picker is retained as a separate explicit file-browsing control; file contents/index are not uploaded to the backend.
- Existing delete-all, delete-last-words, delete-last-characters, new-paragraph, generated-text insertion, selected-text rewrite/correction/translation, and file-search actions remain.
- **New in v3:** `delete_selected_text` is supported. If the user selects text and says “remove this”, “delete this”, or a natural equivalent, the AI can request deletion of that exact selection. The Android client checks that the selection has not changed and always requires confirmation before removing it.
- Protected/password fields remain blocked from voice/AI operations.

## Not included / not claimed complete

- GIF provider search, GIF result UI, download/cache, and Android rich-content insertion are not implemented.
- Sticker provider search/insertion is not implemented.
- A general plan-v1 executor for arbitrary ranges, quoted-text replacement, cursor movement, and broad sentence/paragraph operations is not implemented. The app still uses its allowlisted action model.
- A persistent 20-action undo history is not implemented. Android editor undo behavior is not uniform; the existing app does not claim universal undo.
- Full multilingual intent phrase-corpus coverage and real-device validation are not complete.

## Verification performed for v3

- Backend tests: `python -m pytest server/tests -q` — **59 passed**.
- Android unit tests/build: **attempted, not completed**. `./gradlew testDebugUnitTest assembleDebug --no-daemon` could not download Gradle 8.14.4 because this environment could not resolve `services.gradle.org`.
- Physical-device tests: **not run**.

## Important deployment note

This is source code, not an APK. Before release, open `AKZSA-Android/` in Android Studio, allow Gradle to sync online, run the Android unit tests, build/install a debug APK, and test dictation, generation, selected-text deletion, editing, and file search in several target apps. Deploy the `server/` directory as the Render service root and configure `GROQ_API_KEY` and `AKZSA_SERVER_TOKEN` there. Do not publish a production APK until Android build and device checks pass.
