import os
import unittest
from unittest.mock import patch

os.environ.setdefault("AKZSA_ENV", "development")

from app import gifs


def _item(title="Cat", fh="https://media1.giphy.com/a/200.gif", small="https://media1.giphy.com/a/200_s.gif"):
    return {"title": title, "images": {"fixed_height": {"url": fh}, "fixed_height_small": {"url": small}, "original": {"url": fh}}}


class NormaliseTests(unittest.TestCase):
    def test_maps_renditions(self):
        out = gifs.normalise({"data": [_item()]})
        self.assertEqual(out, [{"url": "https://media1.giphy.com/a/200.gif", "preview_url": "https://media1.giphy.com/a/200_s.gif", "title": "Cat"}])

    def test_falls_back_to_original_and_send_url(self):
        item = {"title": " x ", "images": {"original": {"url": "https://media.giphy.com/o.gif"}}}
        out = gifs.normalise({"data": [item]})
        self.assertEqual(out[0]["url"], out[0]["preview_url"])
        self.assertEqual(out[0]["title"], "x")

    def test_rejects_foreign_or_insecure_hosts(self):
        for bad in ("http://media.giphy.com/a.gif", "https://evil.example/a.gif", "https://giphy.com.evil.io/a.gif", "javascript:alert(1)", ""):
            self.assertIsNone(gifs.safe_url(bad), bad)
        self.assertEqual(gifs.normalise({"data": [_item(fh="https://evil.example/a.gif")]}), [])

    def test_garbage_input_gives_empty_list(self):
        for junk in (None, [], {"data": "x"}, {"data": [1, None, {"images": 5}]}):
            self.assertEqual(gifs.normalise(junk), [])


class CacheTests(unittest.TestCase):
    def test_expiry_and_bound(self):
        t = [0.0]
        cache = gifs.TtlCache(ttl_s=10, max_entries=3, clock=lambda: t[0])
        cache.put("a", [{"url": "1"}])
        self.assertIsNotNone(cache.get("a"))
        t[0] = 11
        self.assertIsNone(cache.get("a"))
        for i in range(10):
            t[0] += 1
            cache.put(i, [])
        self.assertLessEqual(len(cache._items), 3)

    def test_key_is_case_insensitive(self):
        self.assertEqual(gifs.cache_key("gifs", "Cat", 12), gifs.cache_key("gifs", "cat", 12))


try:
    from fastapi.testclient import TestClient
    import app.main as main
except ModuleNotFoundError:  # fastapi not installed in this environment
    main = None


class FakeResponse:
    def __init__(self, status_code, payload): self.status_code, self._payload = status_code, payload
    def json(self):
        if isinstance(self._payload, Exception): raise self._payload
        return self._payload


class FakeAsyncClient:
    result, calls = None, []
    def __init__(self, *a, **k): pass
    async def __aenter__(self): return self
    async def __aexit__(self, *a): return False
    async def get(self, url, **kwargs):
        self.calls.append((url, kwargs))
        if isinstance(self.result, Exception): raise self.result
        return self.result


@unittest.skipIf(main is None, "fastapi not installed")
class RouteTests(unittest.TestCase):
    def setUp(self):
        FakeAsyncClient.calls = []
        main.GIF_CACHE.clear(); main.GIF_LIMITER.reset()
        self.client = TestClient(main.app)
        self.patches = [patch.object(main, "GIPHY_API_KEY", "secret-giphy"), patch.object(main, "SERVER_TOKEN", "tok"),
                        patch.object(main.httpx, "AsyncClient", FakeAsyncClient)]
        for p in self.patches: p.start()
        self.auth = {"Authorization": "Bearer tok"}

    def tearDown(self):
        for p in self.patches: p.stop()

    def test_requires_token(self):
        self.assertEqual(self.client.get("/v1/gifs/search").status_code, 401)

    def test_not_configured(self):
        with patch.object(main, "GIPHY_API_KEY", ""):
            r = self.client.get("/v1/gifs/search", headers=self.auth)
        self.assertEqual(r.status_code, 503)

    def test_trending_when_query_empty_and_key_not_leaked(self):
        FakeAsyncClient.result = FakeResponse(200, {"data": [_item()]})
        r = self.client.get("/v1/gifs/search", headers=self.auth)
        self.assertEqual(r.status_code, 200)
        self.assertNotIn("secret-giphy", r.text)
        url, kw = FakeAsyncClient.calls[0]
        self.assertTrue(url.endswith("/gifs/trending"))
        self.assertEqual(kw["params"]["rating"], "g")

    def test_search_stickers_and_cache(self):
        FakeAsyncClient.result = FakeResponse(200, {"data": [_item()]})
        for _ in range(2):
            r = self.client.get("/v1/gifs/search", params={"q": "  Happy   Birthday ", "kind": "stickers"}, headers=self.auth)
            self.assertEqual(r.json()["results"][0]["title"], "Cat")
        self.assertEqual(len(FakeAsyncClient.calls), 1)
        url, kw = FakeAsyncClient.calls[0]
        self.assertTrue(url.endswith("/stickers/search"))
        self.assertEqual(kw["params"]["q"], "Happy Birthday")

    def test_bad_kind_and_limit_rejected(self):
        self.assertEqual(self.client.get("/v1/gifs/search", params={"kind": "x"}, headers=self.auth).status_code, 422)
        self.assertEqual(self.client.get("/v1/gifs/search", params={"limit": 999}, headers=self.auth).status_code, 422)

    def test_upstream_errors(self):
        import httpx
        for result, code in ((FakeResponse(429, {}), 503), (FakeResponse(403, {}), 502), (FakeResponse(200, ValueError()), 502),
                             (httpx.ConnectTimeout("t"), 504), (httpx.ConnectError("c"), 502)):
            FakeAsyncClient.result = result
            r = self.client.get("/v1/gifs/search", params={"q": str(code) + str(type(result))}, headers=self.auth)
            self.assertEqual(r.status_code, code)
            self.assertNotIn("secret-giphy", r.text)


if __name__ == "__main__":
    unittest.main()
