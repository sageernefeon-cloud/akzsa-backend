# Known limitations (honest list)

* **No APK/AAB, no device testing, no Gradle compile** of the whole app was possible while preparing this release.
* **Voice quality is mostly the speech engine's**, not AKZSA's. On-device recognition needs the language installed in the phone's speech service; Hindi/Kannada offline may be unavailable on some phones. Cloud Dictation (Groq Whisper) needs the server + token.
* **Hinglish romanisation is a heuristic**, not a dictionary. Medial schwa deletion is deliberately conservative ("kamala" rather than "kamla"). A table fixes ~35 very common words. English words spoken inside Hindi may be spelled phonetically ("invois").
* **Silence-length hints are best effort**; Android recognisers may ignore them and still stop at a pause. Tap Talk again to continue.
* **The echo/boilerplate guard can drop real speech** in one narrow case: a short (<48 KB) clip whose entire content is multi-word subtitle boilerplate such as "thanks for watching".
* **Hindi layout** is a simple varga-ordered layout, not InScript or phonetic. No Kannada typing layout yet (Kannada voice works).
* No word suggestions, auto-correct, swipe typing, or long-press accents (typing aid is deliberately minimal).
* In-memory backend rate limits reset on restart and are per instance. Behind a proxy the peer IP is the proxy unless `--proxy-headers`/trusted forwarded IPs are configured.
* The client token is a **shared secret inside the APK**: it deters casual abuse only. Use Play Integrity + per-install quotas for real protection.

## Voice (added in 1.6.0)
* Live dictation is phrase-level (text after each pause), not word-by-word, and cloud only. The on-device engine already streams its own partial results.
* Noise handling is signal processing, not an AI denoiser: steady noise, hum, wind and short bursts are handled; background conversation (babble) is not. An RNNoise/ML denoiser would be the next step.
* Spoken punctuation converts those phrases everywhere (see the switch). The Hinglish dictionary has about 590 entries; unknown words use rules. Forcing Hindi decoding for Hinglish (`AKZSA_HINGLISH_LANGUAGE=hi`) is untested: compare on real speech before making it the default.
* Phrase upload uses 16 kHz WAV (about 32 KB per second of speech): fine on Wi-Fi/4G, noticeable on very slow data.

## Added in 1.7.0
* **Clipboard history is memory-only.** It disappears when Android stops the keyboard process and only records copies made while the keyboard is installed and running. It cannot see copies made before it started.
* **GIF previews animate only on Android 9+** (API 28); Android 8 shows the first frame. Previews are skipped if one file is over 1 MB.
* **GIF sending depends on the target app.** WhatsApp, Telegram, Gboard-compatible editors and most chat apps accept keyboard GIFs; others get the link copied instead. GIF search needs internet, the AKZSA server and its bearer token (the GIPHY key is stored on the server as `GIPHY_API_KEY`).
* **Cursor row** has no long-press repeat and no shift-select.

## Added in 1.8.0
* **AI File Manager removed.** No file search, indexing, OCR, photo search or storage permissions. Nothing to maintain, nothing uploaded.
* **Snippets work only for a whole spoken phrase** and only outside live cloud dictation (live mode inserts text phrase by phrase, before any check).
* **Backtrack works only on the AI-command path**, and only if the field still ends with exactly what AKZSA last inserted. Any edit in between makes it refuse (by design). It is not available in live cloud dictation.
* Snippets and Backtrack have unit tests but have NOT been tested with real speech on a device.
