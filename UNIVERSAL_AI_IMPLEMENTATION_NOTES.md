# AKZSA Universal AI – implementation notes

## What this source update changes

- Removes the visible TYPE / WRITE / CMD mode selector from the keyboard toolbar.
- Leaves one microphone entry point. Speech is transcribed, then sent to `/v1/commands/interpret` with `mode="auto"` so the AI chooses dictation, writing, or a supported edit.
- Routes cloud-recorded speech through the same classifier instead of inserting cloud transcription directly into the field.
- Disables phrase-by-phrase streaming in the universal microphone flow because streaming commits words before the AI can classify whether the utterance is a command.
- Does not run the legacy exact-phrase command parser for normal auto requests, except the tiny `new paragraph` shortcut. Other utterances reach the AI classifier.
- Updates the server prompt to recognize natural paraphrases and to prefer ordinary dictation over a destructive action when uncertain.
- Keeps the existing FILE button and on-device file picker/index. File search remains local to the phone.

## How to use the updated keyboard

1. Deploy the `server/` folder as the Render service root, as in the existing project setup.
2. Configure `GROQ_API_KEY` and `AKZSA_SERVER_TOKEN` in the Render environment. Keep the same token in AKZSA settings.
3. Build/install the Android app from `AKZSA-Android/`.
4. Focus a normal text field and tap the single Talk microphone. Say a normal sentence to dictate, ask for new content to write, or describe an edit in ordinary language.
5. Use FILE when you want to browse/select files manually. The AI file-search intent can also open the local file search panel.

## Safety and known boundaries

- The backend only proposes an action. Android validates it and applies it through the active editor connection.
- Destructive or generated changes can show the existing confirmation preview. The target app must expose a usable Android text connection; some editors hide their text.
- Editor context sharing remains governed by the existing privacy setting. If it is off, the AI cannot see surrounding messages and must not invent reply context.
- Emoji characters can be inserted as text. This source update does **not** add a GIF/sticker search provider or guarantee rich GIF/sticker insertion. That requires choosing/configuring a provider (API key, terms, privacy policy) and implementing provider search plus Android `commitContent` for editors that support image MIME types, with a share-sheet fallback for others.
- No keyboard can guarantee perfect intent classification. The system prompt defaults to dictation when uncertain, and destructive operations require confirmation.
- This is not a verified release build. Backend tests were run; Android compilation and real-device testing still need to be done in Android Studio because this environment could not download the Gradle distribution.

## Verification performed

- `python -m pytest server/tests -q`: **59 passed**.
- Android Gradle compile: **not verified**; Gradle 8.14.4 could not be downloaded in this environment because `services.gradle.org` did not resolve.
- APK/device behavior: **not verified**. Test with WhatsApp, Gmail, Chrome, and Notes before using for important text.
