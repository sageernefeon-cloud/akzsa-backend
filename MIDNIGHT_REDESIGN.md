# AKZSA 3.0 – Midnight redesign: implementation and test report

AKZSA · One Mic AI Keyboard · *Type. Speak. Create. More.*

## IMPORTANT: build status
**This source was NOT compiled and NOT run on a phone.** The environment it was written in has no Android SDK, no Gradle
access and no Kotlin compiler. Static checks were done (balanced braces in every Kotlin file, no stale references), and the
backend tests were run (76 pass). Build it before trusting it, and send any compiler errors back.

## How to get an APK (no Android Studio needed)
1. Create a GitHub repository and upload the *contents* of this zip.
2. Open **Actions → Build APK → Run workflow**. It runs the unit tests and builds the debug APK.
3. Download **AKZSA-debug-apk** from the run's Artifacts. Or, locally: `cd AKZSA-Android && ./gradlew assembleDebug`.

## What changed
| Area | Change |
|---|---|
| Colours | New "AKZSA Midnight" palette, light and dark exactly as specified (`design/GraphiteSignal.kt`, mirrored in `res/values/colors.xml`). Old lime/ivory/midnight-ivory themes removed. Theme choice: System / Light / Dark; colours ease between themes in the app. |
| Branding | AKZSA "A" mark with teal voice dot: launcher icon (adaptive + monochrome), splash, app bar, onboarding. Tagline everywhere. No Groq, no backend URL, no provider name on any screen. |
| Keyboard layout | Input strip (`+` cursor/selection controls, live text you are typing, Send), 7-icon toolbar (scrolls sideways if narrow), suggestion chips, QWERTY, bottom row, mic row. |
| Mic + space | Row: `hide | space~~ | MIC | ~~space | switch keyboard`. Both waveform zones insert one space on tap; only the round mic starts voice. The main space bar also stays (labelled AKZSA). |
| Grammar Fix | The ✦ button opens the AI Workspace on Grammar and immediately prepares a fix. A Before / After card with **Cancel · Regenerate · Apply** replaces the keys; nothing changes until Apply. |
| AI Emoji Adder | Separate "AI Emoji" tab in the workspace (not the normal emoji picker). One suggestion at a time + **Regenerate** (multiple simultaneous suggestions need a backend change). |
| Other AI | Reply (Auto/Polite/Short/Formal/Friendly), Rewrite, Translate (English/Hindi/Hinglish/Kannada), Notes (Email/To-do/Notes/Summary) are tabs in the same workspace. Existing backend contract unchanged. |
| Voice | While listening/processing the keys are replaced by a voice panel: waveform, status, language, "Tap to stop". Waveform follows real sound level with the on-device recogniser; in cloud recording mode it only animates gently (no level data). Errors (permission, network, service) show in the status line in the error colour. |
| Productivity | Clipboard, Emoji, GIF panels kept. Snippets and Languages are new panels. Only one panel is open at a time. |
| Settings | Reorganised: Voice, Typing preferences, AI personalisation, Privacy and security, Theme, Advanced (service address/token, collapsed). |
| Diagnostics | Keyboard core (really enabled?), AI services, Voice service, microphone permission, Network (device connectivity), Last error. All computed, none hard-coded. |
| Version | 3.0.0 (versionCode 13). `.github/workflows/build-apk.yml` added. |

## Implementation map (new / heavily changed files)
`keyboard/KeyboardRenderer.kt` (layout), `KeyView.kt` + `KeyIcons.kt` (drawn icons, key styles, press animation),
`ToolbarController.kt`, `ToolPanels.kt` (AI Workspace, Languages, Snippets, voice), `PanelUi.kt`, `AiWorkspace.kt`,
`StripText.kt`, `Suggestions.kt`, `KeyboardState.kt`, `KeyboardLayoutPolicy.kt`, `PlanController.kt` (Regenerate),
`AkzsaInputMethodService.kt` (strip refresh on cursor change), `res/layout/keyboard_frame.xml`, `ui/AkzsaApp.kt`.

## Tests
Written/updated (run by the GitHub workflow): palette values + contrast, colour/key-style rules, layout policy, strip text,
suggestions, AI tools; backend contract test for the Grammar "fix" style. Instrumented tests added for the two space zones and
the sparkle button (need a device/emulator).

| Verified here | Could NOT be verified here |
|---|---|
| Backend tests: 76 pass. Kotlin brace balance. No secrets in the source (only fake fixtures in a test). | Gradle build, unit tests, lint, APK. Everything on a phone: typing, both spaces, mic, Grammar Fix, panels, themes, small/large screens, app switching. |

## Known deviations and gaps (please read)
- **Light secondary text #747C89 is about 3.9:1** on the light backgrounds: fine for hints/captions, below the 4.5:1 AA level for small body text. The test allows 3:1 for it. The specified teal mic colour is about 2.9:1 on white keys, so the mic has a 2 dp ring and a large glyph.
- Reference screenshot is followed for structure; icons are drawn in code, so shapes differ slightly from the picture.
- Suggestion chips are real but limited: your personal dictionary while typing a word, snippets and the last copied text between words. There is no general word-prediction engine.
- Not implemented (no existing setting/backend): automatic capitalisation toggle, punctuation-behaviour setting, writing-tone chips (use the Writing style text), several simultaneous emoji suggestions, snippet editing inside the keyboard (editing is in the app), GIF recent/favourites, transcript preview while listening. They are not faked.
- Languages: Hindi switches the Devanagari typing layer; Kannada and Hinglish change voice language only (no Kannada typing layer).
- GIF search now goes through the server (`GIPHY_API_KEY` is a server env var, not an app build setting); GIF, AI and cloud voice need a running AKZSA server and an access token (see `server/.env.example`, README).
- The panel/Settings wording still mentions "GIPHY" on the GIF panel as required by GIPHY's terms.

## Security
No live keys, tokens or signing files are in this zip (`keystore.properties`, `server/.env` absent). **If you ever shared the earlier zips or chats that contained real keys, rotate them.** The default service address is still compiled into `SettingsRepository.kt` (not shown in any screen); change it for your own deployment.
