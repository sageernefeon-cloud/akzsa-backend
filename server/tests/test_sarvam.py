import os
import unittest
from unittest.mock import patch

os.environ.setdefault("AKZSA_ENV", "development")

from app import sarvam


class LanguageTests(unittest.TestCase):
    def test_aliases_and_codes(self):
        for raw, want in (("hi", "hi"), ("hi-IN", "hi"), ("Hinglish", "hinglish"), ("hi-latn", "hinglish"), ("auto", "hinglish"),
                          ("ta_in", "ta"), ("kn-IN", "kn"), ("te", "te"), ("bn-IN", "bn"), ("en-US", "en"), ("or", "od"), ("", "en")):
            self.assertEqual(sarvam.normalise_language(raw), want, raw)

    def test_unsupported(self):
        for raw in ("fr", "ar-SA", "ar", "xx", "ta-US"):
            self.assertIsNone(sarvam.normalise_language(raw), raw)


class ProviderOrderTests(unittest.TestCase):
    def test_auto(self):
        self.assertEqual(sarvam.provider_order("hi", True, True), ["sarvam", "groq"])
        self.assertEqual(sarvam.provider_order("hinglish", True, False), ["sarvam"])
        self.assertEqual(sarvam.provider_order("en", True, True), ["groq", "sarvam"])
        self.assertEqual(sarvam.provider_order("ta", False, True), ["groq"])
        self.assertEqual(sarvam.provider_order("ta", False, False), [])

    def test_forced(self):
        self.assertEqual(sarvam.provider_order("en", True, True, "sarvam"), ["sarvam", "groq"])
        self.assertEqual(sarvam.provider_order("hi", True, True, "groq"), ["groq"])
        self.assertEqual(sarvam.provider_order("hi", True, False, "groq"), [])
        self.assertEqual(sarvam.provider_order("hi", True, True, "nonsense"), ["sarvam", "groq"])


class FormTests(unittest.TestCase):
    def test_hindi(self):
        f = sarvam.form_fields("hi")
        self.assertEqual((f["language_code"], f["mode"], f["model"]), ("hi-IN", "transcribe", sarvam.DEFAULT_MODEL))

    def test_hinglish_is_roman(self):
        f = sarvam.form_fields("hinglish")
        self.assertEqual((f["language_code"], f["mode"]), ("hi-IN", "translit"))
        self.assertEqual(sarvam.form_fields("hinglish", hinglish_mode="codemix")["mode"], "codemix")
        self.assertEqual(sarvam.form_fields("hinglish", hinglish_mode="bogus")["mode"], "translit")

    def test_keyterms_only_for_v4_and_capped(self):
        terms = ["Akzsa", "Nefeon"] + ["t%d" % i for i in range(80)]
        self.assertIn("keyterms", sarvam.form_fields("hi", "saaras:v4", keyterms=terms))
        self.assertNotIn("keyterms", sarvam.form_fields("hi", "saaras:v3", keyterms=terms))
        import json
        self.assertEqual(len(json.loads(sarvam.form_fields("hi", keyterms=terms)["keyterms"])), 50)

    def test_extract_keyterms(self):
        self.assertEqual(sarvam.extract_keyterms("Akzsa, Nefeon\nakzsa, x, Genjini"), ["Akzsa", "Nefeon", "Genjini"])

    def test_parse(self):
        self.assertEqual(sarvam.parse_transcript({"transcript": "  mera naam  "}), "mera naam")
        for junk in (None, [], {}, {"transcript": 5}):
            self.assertEqual(sarvam.parse_transcript(junk), "")


try:
    from fastapi.testclient import TestClient
    import app.main as main
except ModuleNotFoundError:
    main = None


class FakeResponse:
    def __init__(self, status_code, payload): self.status_code, self._payload = status_code, payload
    def json(self): return self._payload


class FakeAsyncClient:
    responses, calls = [], []
    def __init__(self, *a, **k): pass
    async def __aenter__(self): return self
    async def __aexit__(self, *a): return False
    async def post(self, url, **kwargs):
        self.calls.append((url, kwargs)); return self.responses.pop(0)


@unittest.skipIf(main is None, "fastapi not installed")
class RouteTests(unittest.TestCase):
    def setUp(self):
        FakeAsyncClient.calls = []
        self.client = TestClient(main.app)
        self.patches = [patch.object(main, "SERVER_TOKEN", ""), patch.object(main.httpx, "AsyncClient", FakeAsyncClient)]
        for p in self.patches: p.start()

    def tearDown(self):
        for p in self.patches: p.stop()

    def post(self, language, polish="false"):
        return self.client.post("/v1/voice/transcribe", content=b"a" * 2048,
                                headers={"Content-Type": "audio/mp4", "X-AKZSA-Language": language, "X-AKZSA-Polish": polish})

    def test_hindi_goes_to_sarvam_with_key_header(self):
        FakeAsyncClient.responses = [FakeResponse(200, {"transcript": "नमस्ते आप कैसे हैं", "language_code": "hi-IN"})]
        with patch.object(main, "SARVAM_API_KEY", "sv-key"), patch.object(main, "GROQ_API_KEY", "gq-key"):
            r = self.post("hi-IN")
        self.assertEqual(r.status_code, 200, r.text)
        url, kw = FakeAsyncClient.calls[0]
        self.assertEqual(url, sarvam.SARVAM_STT_URL)
        self.assertEqual(kw["headers"], {"api-subscription-key": "sv-key"})
        self.assertEqual(kw["data"]["language_code"], "hi-IN")
        self.assertEqual(r.json()["model"], main.SARVAM_STT_MODEL)
        self.assertNotIn("sv-key", r.text)

    def test_hinglish_uses_translit(self):
        FakeAsyncClient.responses = [FakeResponse(200, {"transcript": "mera phone number hai"})]
        with patch.object(main, "SARVAM_API_KEY", "sv-key"), patch.object(main, "GROQ_API_KEY", ""):
            r = self.post("hinglish")
        self.assertEqual(r.status_code, 200, r.text)
        self.assertEqual(FakeAsyncClient.calls[0][1]["data"]["mode"], "translit")
        self.assertEqual(r.json()["text"], "mera phone number hai")

    def test_english_stays_on_groq(self):
        FakeAsyncClient.responses = [FakeResponse(200, {"text": "see you at five"})]
        with patch.object(main, "SARVAM_API_KEY", "sv-key"), patch.object(main, "GROQ_API_KEY", "gq-key"):
            r = self.post("en")
        self.assertEqual(r.status_code, 200, r.text)
        self.assertEqual(FakeAsyncClient.calls[0][0], main.GROQ_AUDIO_URL)

    def test_sarvam_failure_falls_back_to_groq(self):
        FakeAsyncClient.responses = [FakeResponse(400, {"error": {}}), FakeResponse(200, {"text": "mera naam rahul hai"})]
        with patch.object(main, "SARVAM_API_KEY", "sv-key"), patch.object(main, "GROQ_API_KEY", "gq-key"):
            r = self.post("hinglish")
        self.assertEqual(r.status_code, 200, r.text)
        self.assertEqual([c[0] for c in FakeAsyncClient.calls], [sarvam.SARVAM_STT_URL, main.GROQ_AUDIO_URL])

    def test_sarvam_failure_without_groq_is_reported(self):
        FakeAsyncClient.responses = [FakeResponse(429, {})]
        with patch.object(main, "SARVAM_API_KEY", "sv-key"), patch.object(main, "GROQ_API_KEY", ""):
            r = self.post("ta")
        self.assertEqual(r.status_code, 429)

    def test_groq_only_still_handles_hindi_and_skips_unsupported_hint(self):
        FakeAsyncClient.responses = [FakeResponse(200, {"text": "namaste"}), FakeResponse(200, {"text": "வணக்கம்"})]
        with patch.object(main, "GROQ_API_KEY", "gq-key"):
            self.assertEqual(self.post("hi").status_code, 200)
            self.assertEqual(FakeAsyncClient.calls[0][1]["data"]["language"], "hi")
            self.assertEqual(self.post("ta").status_code, 200)
            self.assertEqual(FakeAsyncClient.calls[1][1]["data"]["language"], "ta")

    def test_unsupported_language_and_no_keys(self):
        with patch.object(main, "SARVAM_API_KEY", "sv-key"):
            self.assertEqual(self.post("fr").status_code, 422)
        self.assertEqual(self.post("hi").status_code, 503)

    def test_sarvam_only_skips_cleanup(self):
        FakeAsyncClient.responses = [FakeResponse(200, {"transcript": "kal milte hain"})]
        with patch.object(main, "SARVAM_API_KEY", "sv-key"), patch.object(main, "GROQ_API_KEY", ""):
            r = self.post("hinglish", polish="true")
        self.assertEqual(r.status_code, 200, r.text)
        self.assertFalse(r.json()["polish_applied"])
        self.assertEqual(len(FakeAsyncClient.calls), 1)


if __name__ == "__main__":
    unittest.main()
