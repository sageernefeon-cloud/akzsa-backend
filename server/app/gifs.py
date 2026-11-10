"""GIF search helpers (pure Python, no network): response normalisation and a small TTL cache.

The GIPHY API key lives only on the server (GIPHY_API_KEY). The Android app calls /v1/gifs/search and gets back a
compact, already-validated list, so the key is never inside the APK and the app never talks to GIPHY directly.
"""
from __future__ import annotations

import time
from typing import Any, Callable, Dict, List, Optional, Tuple
from urllib.parse import urlparse

MAX_QUERY_CHARS = 60
DEFAULT_LIMIT = 12
MAX_LIMIT = 25
CACHE_TTL_S = 120.0
CACHE_MAX_ENTRIES = 300
_MAX_URL_CHARS = 600


def safe_url(value: Any) -> Optional[str]:
    """Only https URLs on giphy.com hosts are passed on, so a bad upstream reply cannot point the app elsewhere."""
    if not isinstance(value, str) or not value or len(value) > _MAX_URL_CHARS:
        return None
    try:
        parsed = urlparse(value)
        host = (parsed.hostname or "").lower()
    except ValueError:
        return None
    if parsed.scheme != "https" or not (host == "giphy.com" or host.endswith(".giphy.com")):
        return None
    return value


def _rendition_url(images: Dict[str, Any], name: str) -> Optional[str]:
    item = images.get(name)
    return safe_url(item.get("url")) if isinstance(item, dict) else None


def normalise(payload: Any) -> List[Dict[str, str]]:
    """GIPHY JSON -> [{url, preview_url, title}]. url = ~200px-tall GIF (chat friendly), preview_url = small animated one."""
    data = payload.get("data") if isinstance(payload, dict) else None
    if not isinstance(data, list):
        return []
    out: List[Dict[str, str]] = []
    for item in data:
        if not isinstance(item, dict) or not isinstance(item.get("images"), dict):
            continue
        images = item["images"]
        send = _rendition_url(images, "fixed_height") or _rendition_url(images, "original")
        if not send:
            continue
        preview = (
            _rendition_url(images, "fixed_height_small")
            or _rendition_url(images, "fixed_height_small_still")
            or send
        )
        title = item.get("title")
        out.append({"url": send, "preview_url": preview, "title": title.strip()[:80] if isinstance(title, str) else ""})
    return out


def cache_key(kind: str, query: str, limit: int) -> Tuple[str, str, int]:
    return (kind, query.lower(), limit)


class TtlCache:
    """Tiny in-memory cache (per process) so popular searches and 'trending' do not use up the GIPHY quota."""

    def __init__(self, ttl_s: float = CACHE_TTL_S, max_entries: int = CACHE_MAX_ENTRIES, clock: Callable[[], float] = time.monotonic):
        self.ttl_s, self.max_entries, self._clock = ttl_s, max_entries, clock
        self._items: Dict[Any, Tuple[float, List[Dict[str, str]]]] = {}

    def get(self, key: Any) -> Optional[List[Dict[str, str]]]:
        hit = self._items.get(key)
        if hit is None:
            return None
        if hit[0] <= self._clock():
            del self._items[key]
            return None
        return hit[1]

    def put(self, key: Any, value: List[Dict[str, str]]) -> None:
        now = self._clock()
        if len(self._items) >= self.max_entries:
            for k in [k for k, (exp, _) in self._items.items() if exp <= now]:
                del self._items[k]
            while len(self._items) >= self.max_entries:
                del self._items[min(self._items, key=lambda k: self._items[k][0])]
        self._items[key] = (now + self.ttl_s, value)

    def clear(self) -> None:
        self._items.clear()
