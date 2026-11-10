# AKZSA -> Gboard-level typing: status against the 14-area roadmap

"Same features" means the same everyday behaviour and layout conventions. Gboard's logo, branding, proprietary
models and dictionaries are not copied; AKZSA keeps its own Midnight look, One Mic and AI Workspace.

**Nothing below has been compiled or run** (the authoring environment had no Android SDK or network). Every item is
"code written + unit tests written". First step on your side: `./gradlew testDebugUnitTest assembleDebug`.

## Implemented
| Area | Item | Where |
|---|---|---|
| Toolbar (P0) | Aligned, responsive, scrollable toolbar | `ToolbarItemView`, `ToolbarLayoutPolicy`, `TOOLBAR_ALIGNMENT_REPORT.md` |
| 1 | Optional 0-9 number row; number hints on Q-P (long-press types the digit); double-space full stop | `KeyboardRenderer`, `KeyView.hintText`, `TypingRules` |
| 2 | Offline completion, next-word, spelling candidates, emoji suggestion, clipboard-piece suggestion (link/email/phone/date) | `PredictionEngine`, `Suggestions`, `EmojiLogic`, `ClipboardLogic.extract` |
| 2 | Conservative auto-correction with backspace undo; personal dictionary respected | `AutoCorrect`, `TypingController` |
| 3 | Space-bar swipe moves cursor; backspace swipe-left deletes words | `SwipeGestures`, `KeyboardRenderer` |
| 4 | Select word / sentence, delete word, undo, redo (Ctrl+Z key events) | `WordEdit`, `TypingController`, `CursorRow.buildEditRow` |
| 5 | Emoji search (type on the keyboard), recents, favourites (long-press), skin tone, size, tabs | `EmojiLogic`, `EmojiStore`, `EmojiPanel` |
| 7 | Clipboard: optional on-device history, pin (long-press), delete one, clear, 24h expiry, search logic, smart extraction; passwords/OTP/PIN/card numbers never kept; incognito | `ClipboardLogic`, `ClipboardHistory`, `ClipboardRow` |
| 8 | Native layouts for Hindi, Marathi, Bengali, Punjabi, Gujarati, Tamil, Telugu, Kannada, Malayalam, Urdu, Arabic | `NativeLayouts`, `ToolPanels.LANGUAGE_CHOICES` |
| 10 | Keyboard height slider (85-120%), one-handed left/right, key sound + volume, number row/hints toggles | `AppSettings`, `AkzsaApp`, `KeyboardRenderer` |
| 11 | Date and time insertion chips in Snippets | `ToolPanels.snippets` |
| 13 | Incognito mode (no clipboard history, no recent emoji); learning off in password fields | `AppSettings.incognito` |

## Known caveats of what was added
- Indic layouts are InScript-style (Devanagari key set mapped into each script by Unicode offset, only assigned letters kept). They type native script directly; they are not phonetic yet. Letter placement should be reviewed with native speakers.
- Selecting Tamil, Telugu, Bengali, etc. also sets the voice language code (e.g. `ta-IN`). Check that the backend speech route accepts those codes before relying on voice in those languages.
- Arabic and Urdu keys are not mirrored right-to-left; text direction is handled by the app being typed into.
- Undo and redo only work in editors that implement Ctrl+Z / Ctrl+Shift+Z.
- Prediction/autocorrect are English only and use a few-hundred-word list; a real language model needs a licensed corpus.

## Added in the final pass
| Area | Item | Where |
|---|---|---|
| 1 | Second symbols page (`=<` key) and `!` `?` on the first symbols page | `KeyboardRenderer` |
| 3 | Glide typing (swipe over letters; best word + space; auto-space before it; setting to turn off) | `GlideEngine`, `KeyboardRenderer.installKeyTouch`, `TypingController.commitGlide` |
| 4 | Select paragraph, extend selection left/right (Shift+arrow), delete sentence, smart paste | `WordEdit`, `SmartPaste`, `CursorRow.buildEditRow2` |
| 6 | GIF trending / recent / favourites (long-press) and stickers via the same provider | `GifPanel`, `GifStore` |
| 7 | Clipboard search, category filter (All/Pinned/Links/Emails/Phones), edit (moves the clip into the field) | `ClipboardRow`, `ClipboardLogic` |
| 9 | Voice commands: delete word/sentence, select all/word/sentence, new line/paragraph, undo/redo, "<name> emoji" (whole utterance only, on-device, setting to turn off) | `VoiceCommands`, `DictationController` |
| 10 | Accent colours (blue, green, rose, amber), toolbar order editor | `AccentThemes`, `ToolbarOrder`, `AkzsaApp` |
| 11 | Email templates, phone formatting, Search web and Translate (hand-off to the browser on tap, with the selected text) | `ToolPanels.snippets` |

Caveats: glide needs real-device tuning (start distance 22dp, letter hit-testing by screen position); translate and web search are not inline previews and send the selected text to the browser only when tapped; stickers need the same GIPHY key as GIFs.

## Not done (cannot be done honestly without extra assets, services or a device)
| Area | Remaining and why |
|---|---|
| 2 | Real n-gram model and per-language prediction (needs a licensed corpus); grammar suggestions beyond the existing AI Grammar Fix |
| 8 | Phonetic typing/transliteration for the 10 new languages and language detection (needs per-language rule tables); downloadable language packs (needs hosting) |
| 9 | Offline speech recognition and noise handling (needs an on-device model) |
| 10 | Floating keyboard, custom backgrounds, key borders, vibration strength (strength needs the VIBRATE permission, left out on purpose) |
| 11 | In-keyboard translation preview and web-result preview (needs a translation/search backend contract) |
| 12 | Handwriting (needs a recogniser such as ML Kit Digital Ink; not available here) |
| 13/14 | TalkBack audit, retention-hours UI, and the device/latency/memory test programme: these need a real device and cannot be simulated |
