# Architecture

```
Android app (single process)
 ├─ keyboard/  AkzsaInputMethodService (thin lifecycle) → KeyboardHub → KeyboardRenderer (keyboard_frame.xml + KeyView),
 │             ToolbarController, TypingController, PlanController, DictationController
 │             ── policies: SensitiveFieldPolicy, KeyboardLayoutPolicy,
 │             DictationFormatter, HinglishTransliterator, DevanagariLayout, KeyLabels, KeyboardInsets
 ├─ network/   BackendClient (OkHttp)  →  server/app/main.py (FastAPI) → Groq (Whisper + LLM)
 ├─ ui/        Compose app: onboarding, home, settings, privacy, diagnostics (Graphite Signal theme)
 └─ design/    GraphiteSignal tokens
```
**Data flow rules.** Snippets are matched on the phone. Only a transcript/selected text (commands) or an audio clip (Cloud Dictation) is sent, only after a user action, never from protected fields.
**Why the IME package was not renamed:** `com.nefeon.akzsa.keyboard.AkzsaInputMethodService` is the component name Android stores when a user enables the keyboard; renaming would silently disable it for existing users.

## ADR-1 Backtrack records the exact inserted text
The field must still END WITH the text AKZSA last inserted (capitals and trailing space included) before a correction is applied; otherwise nothing changes.
## ADR-2 GIF previews without an image library
Animated previews use ImageDecoder (API 28+) on small GIPHY renditions, cached in memory; no third-party image loader.
## ADR-3 AI File Manager removed (1.8.0)
It needed broad storage access, a 45 MB bundled model and heavy native libraries. Removing it shrinks the APK and the permission footprint.
## ADR-5 Hinglish = Hindi recogniser + local romanisation
English recognisers mangle Hindi words; Hindi recognisers return Devanagari. Romanising locally gives Hinglish text with no extra network call.
## ADR-6 Keyboard sizing from measurements
`KeyboardLayoutPolicy` takes width, height, font scale and user height preference; no device/brand checks.
