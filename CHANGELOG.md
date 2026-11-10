# Changelog

## 3.0.2 - Sarvam AI speech-to-text for Indian languages (NOT YET COMPILED ON ANDROID)
- **Server:** `/v1/voice/transcribe` now sends Hindi, Hinglish and other Indian languages (Bengali, Kannada, Malayalam, Marathi, Odia,
  Punjabi, Tamil, Telugu, Gujarati, Assamese, Urdu, Nepali and more) to Sarvam `speech-to-text` (model `saaras:v4`, `SARVAM_API_KEY`).
  Hinglish asks for Roman-letter output (`mode=translit`). English stays on Groq Whisper. If Sarvam fails (busy, key problem, clip over
  its 30-second limit) the route falls back to Groq when `GROQ_API_KEY` is set. `AKZSA_ASR_PROVIDER=auto|sarvam|groq` overrides routing.
  The personal vocabulary is sent as Sarvam key terms. New `server/app/sarvam.py`, tests in `server/tests/test_sarvam.py`.
  AI cleanup (Groq) is skipped when only Sarvam is configured. `/health` shows `sarvam_configured`.
- **Android:** `cloudVoiceLanguageCode` now passes Tamil, Telugu, Bengali, Marathi, Gujarati, Malayalam, Punjabi and Urdu through to the
  server instead of mapping them to English. Cloud dictation must be switched on in settings for the keyboard to use the server.

## 3.0.1 - GIPHY key moved to the server (NOT YET COMPILED ON ANDROID)
- **Server:** new `GET /v1/gifs/search?q=&kind=gifs|stickers&limit=` (bearer token + rate limit, rating forced to G, 120 s result cache,
  only https giphy.com URLs returned). Reads `GIPHY_API_KEY` from the environment; `/health` reports `gifs_configured`. httpx request
  logging is silenced so the key can never appear in logs. New `server/app/gifs.py`, tests in `server/tests/test_gifs.py`.
- **Android:** `GifPanel` no longer calls GIPHY; it uses `BackendClient.searchGifs` through a `fetchGifs` callback supplied by
  `KeyboardRenderer`. The `GIPHY_API_KEY` BuildConfig field and the `giphyApiKey` keystore setting are removed, so the APK holds no key.
- **Action needed:** add `GIPHY_API_KEY` in Render (Environment) and redeploy; rotate any GIPHY key that was ever built into an APK.

## 3.0.0 - AKZSA Midnight redesign (NOT YET COMPILED ON ANDROID; see MIDNIGHT_REDESIGN.md)
- New light/dark palette, AKZSA logo mark, launcher icon and splash; no provider/backend names in the UI.
- Keyboard rebuilt: input strip, 7-icon toolbar, suggestion chips, mic row with a space zone on each side.
- Sparkle = Grammar Fix with Before / After / Regenerate; AI Emoji Adder is a separate tab; Languages and Snippets panels.
- Voice session panel with waveform; settings regrouped; diagnostics show real status.


## 1.8.0
- **GIF panel:** thumbnails are now real, ANIMATED previews (2-row grid, Android 9+; first frame on Android 8). `GifThumbnails.kt`.
- **AI File Manager removed:** FILE key, panel, indexer, search, OCR/PDF/photo model, 45 MB `assets/clip`, storage + foreground-service
  permissions, `files/` package, play/full flavors, ML Kit / ONNX / PDFBox / WorkManager / DocumentFile / ExifInterface dependencies,
  server `/v1/files/search` and the `find_file` action. FileProvider now exposes only the GIF cache.
- **Snippets:** added the missing Settings field (the feature had no way to enter snippets). Inserted verbatim, no cleanup applied.
- **Backtrack fix:** the app recorded the raw transcript, so the "field still ends with what I said" check could never match
  (capitals/trailing space differ). It now records the exact inserted text and keeps spacing/capital on the replacement.
- Tests added: SnippetDictionary, BacktrackText, GifThumbnails, ActionValidator (Backtrack, find_file rejected), server `test_no_file_search`.

## 1.8.0 - Phase 4 (NOT YET COMPILED ON ANDROID; no Kotlin compiler was available)

### P4-1 Service split
`AkzsaInputMethodService.kt` 2130 -> ~120 lines (lifecycle only). New files in `keyboard/`:
`KeyboardHub` (wiring), `KeyboardState`, `KeyboardRenderer`, `ToolbarController`, `FileAiPanelController`,
plus `TypingController`, `PlanController`, `DictationController` (needed to get the service under 400 lines).
Code was moved, not rewritten; `voice/`, Hinglish files and `server/` are untouched.
- Fixed a compile error found in the old service: `BundledClipTagger` (package `files`) was used without an import.
- `startStreamingDictation()` was never called in the old service either; kept as-is (documented in code).

### P4-2 KeyView
`KeyView.kt`: TextView subclass drawing fill/border/radius/optional shadow in `onDraw()` with one Paint; pressed
fill animates over 80 ms (ValueAnimator). Colour rules are in `KeyColors` (unit-tested). Renderer uses it for all keys.

### P4-3 XML frame
`res/layout/keyboard_frame.xml` (root, status, toolbar slot, panel slot, preview, key rows) is inflated once;
rows/keys/panels are still built in Kotlin.

### Audit fixes
- `SecureTokenStore`: a transient Keystore error no longer deletes the saved token (only permanently invalid keys
  do); the decrypted token is cached in memory; `readTokenAsync()` reads off the main thread and is used by the
  keyboard controllers.
- `KeyboardLog`: the keyboard controllers no longer swallow exceptions silently (class name only is logged, never text).
- `FieldEditMath` extracted from `PlanController` and unit-tested.
- Not done (needs a compiler/device): separate indexing process, narrower FileProvider paths, key-level
  incremental re-render, pinned server lock file.

### P4-4 / P4-5 (already present in the zip, verified)
ThermalGuard 42.0/40.0 with the call in `runIndex()`; MidnightIvory palette + settings option.
Known gap: MidnightIvory `borderStrong` is below 3:1 on its surfaces (Light/Dark pass).

## 1.7.0 (versionCode 11) - Phases 1-3 merged and completed (NOT YET COMPILED ON ANDROID)

STATUS: merged and completed by static reading. Backend: 59 tests pass, `compileall` clean. Android: every Kotlin
file was parsed with kotlinc 2.3.21 and shows no syntax errors, but nothing has been built with Gradle or run on a
phone. Run `scripts/build.sh test` first and fix whatever the compiler reports.

### Phase 1 - keyboard, file manager, bundled photo model, OCR
- Quick row: duplicate Enter removed; New Line works in WhatsApp/Instagram/Telegram (no multi-line flag check);
  Send/Go/Search/Done/Next keys use the accent colour; Enter key is wider.
- File AI panel: first-open crash (`root.post`), results rendered on the main thread, empty-index message re-renders,
  results area 160 dp. `BuildConfig.ALL_FILES_ACCESS` guarded.
- Low battery no longer blocks indexing: the battery constraint is removed from `ClipImageIndexWorker` and from the
  one-time/first scan. The periodic 6-hour refresh still waits for charging. The 107 MB model download keeps its
  battery-not-low rule on purpose.
- MobileCLIP-S0 bundled in `assets/clip` (image encoder + 112 label vectors). `BundledClipTagger` tags photos offline
  from the first launch and merges the labels into `imageLabels`. The download pipeline is unchanged.
- `ClipCategoryManager.ALL_LABELS`: 500+ labels (Indian + global food, places, clothing, documents, sports,
  festivals, music, hard cases), de-duplicated. They are scored once the full model has been downloaded, or offline
  from install after you run `scripts/generate_label_embeddings.py` (see docs/HANDOVER_APK_TEAM.md).
- OCR: 4096 px bitmaps; weak first results (< 120 characters) are retried on a contrast-stretched, 3x3-sharpened copy
  and then on a 180-degree rotated copy (both used to run only for files above the size cap). Labels keep up to 40
  words and drop filler words such as "of".

### Phase 2 (as found in the merged tree)
- Emoji panel (`EmojiPanel.kt`), MORE row, AI Emoji key.

### Phase 3 - cursor, clipboard, GIF
- Cursor row (select all, cut, copy, paste, arrows).
- Clipboard row now shows a real history (last 10 copies, in memory only, never written to disk). Copies made while a
  protected field is focused, or marked sensitive by the source app, are not kept. A Clear chip wipes the history.
- GIF panel (GIPHY): the search field is typed with the keyboard's own keys (Enter or the magnifier searches); tapping
  a GIF downloads it (6 MB cap) and sends it with the keyboard-image API. Apps that do not accept GIFs get the link
  copied to the clipboard instead. Needs `GIPHY_API_KEY` at build time.

### Not done
- Phase 4 (prompt not received). Model SHA-256 pinning (needs Hugging Face access). Gradle build, unit tests and
  device tests.

## 1.6.1 (versionCode 10) - first real compile
- **Fixed the only Kotlin error the first Gradle build reported:** `ClipTokenizer.kt` used `Locale.ROOT` without `import java.util.Locale` (code inherited from the 1.4.x line). A scan of every other Kotlin file found no further missing imports.
- Everything else in the first build (resources, manifest, dependency resolution, dexing steps before Kotlin) completed on a real Windows machine (JDK 21, Gradle 8.14.4, Kotlin 2.3.21), so the Gradle wrapper checksum and plugin versions are confirmed to work.
- `AKZSA-Build.ps1`: shows clean error text, auto-repairs this kind of missing `Locale` import in an unpacked copy, and names the APK after its version.
- Still not confirmed: the APK itself, the unit tests, and everything on a phone.

## 1.6.0 (versionCode 9) - voice upgrade: live dictation, noise handling, Hinglish dictionary (NOT YET COMPILED)

### Live ("streaming") cloud dictation
- Plain cloud dictation now works phrase by phrase: the microphone is read continuously, every pause ends a phrase, the phrase is sent and inserted (in order) while you keep talking. Text appears about a second after each pause. It is near-live, not word-by-word.
- Safety: falls back to the old record-then-upload path if the microphone cannot be opened or no token is saved; command/writing/File AI modes keep their old paths; a failed phrase is retried once; auto-stop after 25 s without speech and after 10 minutes; Settings > "Live cloud dictation" turns it off.
- New classes: `voice/StreamingDictationSession`, `voice/VoiceCapture`, `voice/AudioDsp` (+ `SpeechEndpointer`), `BackendClient.transcribeSegment`.

### Noise handling (cloud dictation)
- 16 kHz mono capture from the VOICE_RECOGNITION source, the phone's NoiseSuppressor when available, a 100 Hz high-pass filter (wind, handling noise, hum), quiet-speech boost (up to +18 dB, never attenuates).
- Speech detection follows the room: threshold = 3x the quietest level of the last 3 s, so a fan or AC already running is not speech. Bursts shorter than 0.3 s (door, cough, click) are dropped, so Whisper is never asked to transcribe noise-only audio. 300 ms pre-roll keeps word beginnings.
- Settings > "Noise reduction" turns the filter/suppressor/boost off.

### Hinglish
- New built-in `HinglishDictionary` (about 590 entries): English words that Hindi engines write in Devanagari go back to English ("इनवॉइस" -> "invoice", "मीटिंग" -> "meeting", "व्हाट्सएप" -> "WhatsApp") and the most common Hindi words get their usual typed spelling ("नहीं" -> "nahi"). Lookup ignores nukta and candrabindu/anusvara differences. Everything else still uses the rule-based converter.
- Server: the Hinglish prompt now contains a Roman-letter example sentence (Whisper copies the script/style of its prompt). `AKZSA_HINGLISH_LANGUAGE=hi` is an opt-in A/B switch (default: auto-detect, as before).
- Session context: the end of what was already dictated is sent with each phrase as the end of the Whisper prompt, so spelling, script and style stay consistent between phrases.

### Accuracy / dictionary
- Personal dictionary (Settings) is now a real dictionary: a plain term (`NEFEON`) is a recognition hint and its spelling/capitals are enforced; `nefion = NEFEON` (also `=>` and `->`) corrects a word that is often heard wrongly. Whole-word, case-insensitive, local. Only correct spellings are sent to the server. Limit raised from 1,200 to 4,000 characters.
- Spoken punctuation (English, Hindi, Hinglish): "comma", "full stop", "question mark", "exclamation mark", "colon", "semicolon", "new line", "new paragraph", "open/close bracket". Settings > "Spoken punctuation".
- `DictationFormatter` keeps intentional newlines. `HinglishTransliteratorTest` now expects "invoice" (it previously documented the limitation as "invois").

### Server
- WAV bodies are forwarded with the right file name/type; `X-AKZSA-Context-B64` header; verbatim repeats of the session context are rejected like prompt echoes; prompt logic moved to `app/voice_prompt.py` (pure Python, 12 tests); default voice limits raised 10 -> 40 per install and 30 -> 120 per IP per minute (live dictation sends one request per phrase).

## 1.5.2 (versionCode 8) - merge with Production v1 device fixes + faster OCR / photo AI (NOT YET COMPILED)

### Merged from the Production v1 zip (fixes that were reported on a real phone)
- **Open file jumped to the Photos/Gallery home screen.** New `FileOpener`: images/videos/audio open through their MediaStore URI, documents through the FileProvider; a moved/deleted file is reported instead of launching an app that cannot show it; a chooser appears when no default app exists. Used by `FileAiActivity` and the keyboard.
- **Indexing missed documents / indexed junk.** New `IndexPolicy`: Documents/Download are visited before Pictures/Screenshots/WhatsApp, then other folders, then DCIM/video/music, then Android. Hidden folders and files (`.thumbnails`, `.Trash`, `.trashed-*`, `.nomedia`, `*.tmp`, `*.crdownload`...) are skipped in the phone scan, the folder-picker scan and the single-file update path, and are also hidden at search time (text search and photo search) for rows older builds had already indexed. Photo AI no longer embeds such images.
- **Wrong file types.** New `MimeResolver`: videos, audio, zip, apk, office files get proper types instead of `application/octet-stream`.
- **Scans that would not stop.** Extraction, image analysis, OCR and PDF OCR no longer swallow coroutine cancellation.
- **Build flavors.** The `full` flavor no longer adds `.full` to the application id, so `assembleFullDebug` installs over earlier AKZSA debug builds (keyboard stays enabled, all-files permission and index are kept). `scripts/build.*` build `full` by default; release scripts still build `play`.
- Added `IndexPolicyAndMimeTest` (from the Production zip).

### Faster, without removing features
- **Photo AI no longer waits for the OCR scan.** It starts immediately (newest photos first) and rests longer between photos only while OCR is still running. `ThermalGuard` still pauses it when the phone is warm.
- Rest between photos 250 ms -> 30 ms (120 ms while OCR runs). CLIP threads: fixed 2 -> half of the CPU cores, between 2 and 4.
- **Devanagari OCR pass skipped on long, clean English pages** (>= 250 characters, >= 180 letters, no Indic characters, almost no stray symbols). Short, noisy or mixed-script pages still get both passes. Trade-off: a bilingual page with a long clean English part may lose its Hindi text; set `LATIN_ONLY_MIN_CHARS` to `Int.MAX_VALUE` in `LocalFileIndexer` to always run both passes again.

### Deliberately NOT taken from the Production zip
- Gemma / MediaPipe semantic stack (`LocalSemanticSearch`, `SemanticEmbeddingIndexWorker`, `SemanticModelDownloadWorker`, `domain/search/*`) - already removed in 1.5.1 (needs about 6 GB RAM in the keyboard process, never measured).
- Kotlin plugin 2.4.21 (kept 2.3.21 to match AGP 8.13's R8), the Gemma/MediaPipe ProGuard rules (kept the ML Kit/PDFBox rules), and the 25,000-file cap (kept 50,000 / 200,000 visited).

### Not changed (still open)
- OCR image size (3,000 px), 5 embeddings per photo, 30-page PDF OCR cap, one file at a time. Measure on a real phone before tuning these.

## 1.5.1 (versionCode 7) - v1 + v2 merge, file/photo pipeline rebuilt (NOT YET COMPILED)

### Fixed - file and photo pipeline (taken from v2, adapted to v1)
- **Search loaded every document's full OCR text into memory on each query** (`allFiles()`), including from inside the keyboard process. Search now uses bounded SQLite FTS candidates (`searchCandidates`) and loads only the matching rows (`findByIds`).
- **Index capped at 5,000 files / 10,000 characters per file / 6 PDF pages**; now 50,000 files / 200,000 characters / 30 PDF pages.
- **Rotated phone photos were OCR'd sideways** (EXIF orientation ignored): orientation is now applied; OCR bitmap width raised from 1,350-1,600 px to 3,000 px (small print on receipts/invoices was unreadable).
- **Office files**: all worksheets and slides are read (v1 read only `sheet1` and slides 1-5) via `OfficeTextExtractor`.
- **Photo search** now embeds the full frame plus four quadrant tiles per photo (v1 centre-cropped and lost subjects near the edges), with `ClipRanking` for scoring.
- **Hinglish / Hindi / Kannada / Tamil / Telugu / Bengali / Urdu queries** map to English image labels and concepts (`MultilingualQueryNormalizer`).
- GSTIN / invoice number / amount are extracted once at index time instead of on every query (v1 still falls back to the text for old records).
- Photo index and file index are fused with rank-based fusion (RRF) instead of mixed score scales.

### Fixed - photo model download
- **Download restarted from zero after every dropped connection**: now resumes with HTTP Range.
- **Model could never download on mobile data**: Wi-Fi remains the default, a new button allows an explicit mobile-data download.
- Model URLs now point at an immutable Hugging Face revision instead of `main`.

### Kept from v1
- Play / full flavors, ALL_FILES_ACCESS gate, consent-first startup, model integrity verifier (trust-on-first-use), identifier exact-match scoring, backend hardening, Hindi layout, dictation formatter.

### Removed
- Gemma / EmbeddingGemma semantic stack (MediaPipe `tasks-retrieval`, LiteRT-LM): needed about 6 GB RAM inside the keyboard process, was never resolved or measured, and `domain/search/*` fusion code that only served it.

### Build / project
- Kotlin plugins pinned to 2.3.21 (2.4.x needs a newer R8 than AGP 8.13.0 bundles).
- Added `androidx.exifinterface`, Gradle wrapper SHA-256, ML Kit / PDFBox ProGuard rules.
- Added GitHub Actions CI and a non-root Dockerfile.

## 1.5.0 (versionCode 6) - production hardening

### Fixed - voice text
- **Dictation glued onto previous text** ("Hellohow are you"): new `DictationFormatter` adds the leading space, avoids double spaces, keeps punctuation attached, never adds spaces in e-mail/URL/number fields.
- **First word never capitalised** in sentence-case fields: now follows the field's caps mode (`getCursorCapsMode`).
- **Speech lost on "no match" / timeout**: the last partial hypothesis is now inserted instead of discarded.
- **Recogniser stopped at the first short pause**: silence-length hints added (best effort; providers may ignore).
- **Cloud English could insert the prompt text itself** (Whisper echoes `prompt` on quiet audio): English prompt is now glossary-only; an echo guard and subtitle-boilerplate guard return 422 instead of inserting garbage.
- Hindi/Kannada offline "language not supported" errors now explain what to do; rate-limit error added.

### Fixed - Hindi / Hinglish
- Hinglish on-device used `en-IN` (garbles Hindi words). Now Hindi engine + local Devanagari->Roman transliteration (`HinglishTransliterator`); cloud results containing Devanagari are romanised too.
- **New Hindi (Devanagari) typing layout** (consonants, matras, vowels, conjunct keys, halant, danda) with an `अ/A` toggle.

### Fixed - keyboard
- No way to leave the keyboard: added a next-keyboard / picker key.
- No auto-capitalisation, no caps lock (double-tap Shift): added.
- Fixed 23-43 dp rows and 26-31 dp buttons: now 44-56 dp key rows, 48 dp toolbar/actions via `KeyboardLayoutPolicy` (width/height/font-scale/profile based, never device model); width bounded on tablets/landscape; insets handled.
- Files mode stacked a big panel above the full keyboard and **wiped results on any re-render**: results now persist, the results surface replaces the letter keys, with `Edit query` and `Back to typing`.
- Password check missed OTP/PIN/CVV fields and incognito editors: new `SensitiveFieldPolicy` (voice, cloud, AI and file search all off).
- Screen-reader labels for symbol keys; text sizes no longer 9.5-10.5 sp.

### Fixed - search / privacy / build
- `max(lexical, semantic)` score merging replaced by weighted Reciprocal Rank Fusion; at most one embedding model active (`EmbeddingProviderPolicy`).
- Score capped at 100 made strong matches tie (wrong invoice ranked first) - cap raised; exact-identifier boost added.
- Invented "Match score: N/100" removed from the UI.
- MobileCLIP model had **no integrity check** (size only): now hash-recorded, verified before load, atomic promote; corrupt files are deleted and re-downloaded. Vector fingerprints include a model tag.
- A 107 MB model download was queued on **every app launch**; now only after the user opts in to File AI.
- File AI permission screen no longer auto-opens on first launch.
- `catch (Exception)` swallowed coroutine cancellation in the client and service: fixed.
- Shipped `gradlew` was a custom bootstrap pinned to Gradle 8.13 while properties said 8.14.4, and `gradle-wrapper.jar` was missing: replaced with the official generated wrapper.
- Removed 5 duplicate/patching scripts and the duplicate backend `main.py`.
- Backend: constant-time token check, per-install + per-IP rate limits, provider concurrency cap, request IDs, security headers, 413 body cap, provider 429/401 mapping, no traceback logging, docs off in production.

### Added
- Graphite Signal design tokens (light/dark, WCAG-tested), new adaptive + monochrome icon, edge-to-edge, predictive-back opt-in, backup/extraction rules, `play`/`full` flavors, env-based release signing, R8 keep rules, regression corpus, 134 tests.

## v3 review fixes (31 October 2026)

- Fixed a backend response-model mismatch: `delete_selected_text` was offered by the AI JSON schema and Android validator but omitted from the Pydantic `CommandAction` allowlist. The backend now accepts and returns this action correctly.
- Added regression coverage for both the action model and the authenticated `/v1/commands/interpret` endpoint response.
- Fixed an un-awaited coroutine warning in the provider concurrency timeout test by closing the simulated pending acquire coroutine.
- Updated an outdated Android comment that referred to the removed TYPE mode.
- Re-ran backend tests: 59 passed with no warnings; Python syntax and Android XML well-formedness checks passed.
- Android Gradle compilation was attempted but could not start because this environment cannot resolve `services.gradle.org` to download the configured Gradle 8.14.4 distribution.

## Phase 3
- Cursor row, clipboard row, inline GIPHY GIF panel (set GIPHY_API_KEY env or giphyApiKey in keystore.properties).

## Unreleased (batches 1-2)
- Keyboard feel: touch-down typing for character keys, slide-off cancel, no dead zones, Shift without full rebuild, key preview and long-press accents.
- AI row: Reply, Rewrite, Translate, Notes (email / to-do / notes / summary) with optional writing-style persona. Backend: POST /v1/ai/transform.

## Toolbar alignment and responsive fix
- New `ToolbarItemView`: every toolbar button shares one structure (28dp icon box, 24dp glyph, 4dp gap, one label line on a shared baseline); the whole cell is the touch target.
- New `ToolbarLayoutPolicy`: replaces the fixed 54dp minimum (7 x 54 = 378dp) with a width calculation from the real available width; all seven share the row when they fit, otherwise the row scrolls with a half-cut last item and an edge fade. Original order and all seven actions kept.
- `KeyIcons.opticalOffset`: glyphs are centred by their visual bounds (the document icon was half a grid unit off).
- Toolbar height 44/40 -> 52/48dp so icon + gap + label fit and the 48dp touch height is met.
- Scroll position survives re-renders, so tapping Lang or Settings at the far end does not jump the row back.
- Tests: `ToolbarLayoutPolicyTest`.

## Typing upgrade (Gboard-parity, milestone 1)
- `PredictionEngine` + `Suggestions(smart=true)`: offline completion, next-word and spelling candidates.
- `AutoCorrect`: curated, conservative corrections with backspace undo; double-space full stop (`TypingRules`).
- New settings: smartSuggestions, autoCorrect, doubleSpacePeriod, showNumberRow (+ toggles in the app).
- Optional number row. Tests: PredictionEngineTest, AutoCorrectTest. Status doc: docs/GBOARD_PARITY_STATUS.md.

## Gestures and editing (Gboard-parity, milestone 2)
- Space-bar swipe moves the cursor; backspace swipe left deletes words (`SwipeGestures`).
- Select word / sentence, delete word, undo, redo row (`WordEdit`, `CursorRow.buildEditRow`). Tests: SwipeGesturesTest.

## Milestone 3: languages, emoji, clipboard, personalisation
- `NativeLayouts`: 11 native-script layouts; language chips for all.
- Emoji panel: search, recents, favourites, skin tone, size.
- Clipboard: optional persistence, pin, delete, expiry, privacy filter, smart extraction; incognito.
- Settings: height slider, one-handed, key sound/volume, number hints, remember clipboard, incognito.
- Snippets: Date and Time chips. Tests: NativeLayoutsTest, EmojiAndClipboardTest.

## Final pass
- Glide typing, symbols page 2, paragraph/sentence/selection editing, smart paste.
- GIF recents/favourites/stickers, clipboard search/filter/edit.
- Voice editing commands, accent colours, toolbar order, templates, phone format, web search and translate hand-off.
- Tests: FinalFeaturesTest.
