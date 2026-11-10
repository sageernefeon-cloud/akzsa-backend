"""Sarvam AI speech-to-text helpers (pure Python, no network).

Indian-language dictation (Hindi, Hinglish, Kannada, Tamil, ...) is sent to Sarvam's Saaras model; English stays on
Groq Whisper unless AKZSA_ASR_PROVIDER says otherwise. The Sarvam key (SARVAM_API_KEY) exists only on the server.
Docs: https://docs.sarvam.ai/api-reference-docs/speech-to-text/transcribe  (POST https://api.sarvam.ai/speech-to-text,
header api-subscription-key, multipart form, sync limit 30 seconds of audio).
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional

SARVAM_STT_URL = "https://api.sarvam.ai/speech-to-text"
DEFAULT_MODEL = "saaras:v4"
SYNC_LIMIT_SECONDS = 30

# AKZSA language code -> Sarvam BCP-47 code. "hinglish" is Hindi speech written in Roman letters.
SARVAM_CODES: Dict[str, str] = {
    "hi": "hi-IN", "hinglish": "hi-IN", "bn": "bn-IN", "kn": "kn-IN", "ml": "ml-IN", "mr": "mr-IN", "od": "od-IN",
    "pa": "pa-IN", "ta": "ta-IN", "te": "te-IN", "gu": "gu-IN", "as": "as-IN", "ur": "ur-IN", "ne": "ne-IN",
    "kok": "kok-IN", "ks": "ks-IN", "sd": "sd-IN", "sa": "sa-IN", "sat": "sat-IN", "mni": "mni-IN",
    "brx": "brx-IN", "mai": "mai-IN", "doi": "doi-IN", "en": "en-IN",
}

_ALIASES = {"hinglish-latn": "hinglish", "hi-latn": "hinglish", "auto": "hinglish", "kn-in-kannada": "kn", "or": "od", "or-in": "od"}

# Languages Whisper (the Groq fallback) understands as a language hint; for anything else the hint is left out.
WHISPER_LANGUAGES = {"en", "hi", "bn", "kn", "ml", "mr", "pa", "ta", "te", "gu", "as", "ur", "ne", "sa"}


def normalise_language(raw: str) -> Optional[str]:
    """'ta-IN' / 'ta_in' / 'Hinglish' -> 'ta' / 'ta' / 'hinglish'. None when the language is not supported."""
    value = (raw or "en").strip().lower().replace("_", "-")
    value = _ALIASES.get(value, value)
    if value in SARVAM_CODES:
        return value
    base = value.split("-", 1)[0]
    if base == "en":
        return "en"               # en-US, en-GB, en-IN ...
    if base in SARVAM_CODES and (value.endswith("-in") or base == value):
        return base
    return None


def provider_order(language: str, sarvam_ready: bool, groq_ready: bool, preference: str = "auto") -> List[str]:
    """Which speech providers to try, in order (the second one is the fallback).

    auto (default): Sarvam first for Indian languages and Hinglish, Groq first for English.
    sarvam: Sarvam first for everything. groq: Groq only (Sarvam switched off)."""
    pref = (preference or "auto").strip().lower()
    if pref not in {"auto", "sarvam", "groq"}:
        pref = "auto"
    if pref == "groq":
        return ["groq"] if groq_ready else []
    order = ["sarvam", "groq"] if (pref == "sarvam" or language != "en") else ["groq", "sarvam"]
    ready = {"sarvam": sarvam_ready, "groq": groq_ready}
    return [name for name in order if ready[name]]


def form_fields(language: str, model: str = DEFAULT_MODEL, hinglish_mode: str = "translit", keyterms: Optional[List[str]] = None) -> Dict[str, str]:
    """Multipart form fields (besides the file). Hinglish asks Saaras for Roman-letter output (mode=translit)."""
    fields: Dict[str, str] = {"model": model, "language_code": SARVAM_CODES.get(language, "unknown")}
    if language == "hinglish":
        fields["mode"] = hinglish_mode if hinglish_mode in {"translit", "codemix", "transcribe", "verbatim"} else "translit"
    else:
        fields["mode"] = "transcribe"
    terms = [t.strip()[:64] for t in (keyterms or []) if t and t.strip()][:50]
    if terms and model.startswith("saaras:v4"):
        import json
        fields["keyterms"] = json.dumps(terms, ensure_ascii=False)
    return fields


def parse_transcript(payload: Any) -> str:
    text = payload.get("transcript") if isinstance(payload, dict) else None
    return text.strip() if isinstance(text, str) else ""


def extract_keyterms(vocabulary: str) -> List[str]:
    """The app's personal vocabulary (comma/newline separated words) -> Sarvam keyterms (names, brands, places)."""
    out: List[str] = []
    for part in (vocabulary or "").replace("\n", ",").split(","):
        part = part.strip()
        if 2 <= len(part) <= 64 and part.lower() not in {t.lower() for t in out}:
            out.append(part)
    return out[:50]
