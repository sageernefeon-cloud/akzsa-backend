import os
import sys
import unittest
from pathlib import Path

os.environ.setdefault("AKZSA_ENV", "development")
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from fastapi.testclient import TestClient
import app.main as main


class FileSearchRemovedTests(unittest.TestCase):
    """AI File Manager was removed: the backend must not expose or advertise file search."""

    def setUp(self):
        main.SERVER_TOKEN = ""
        self.client = TestClient(main.app)

    def test_file_search_endpoint_is_gone(self):
        response = self.client.post("/v1/files/search", json={"query": "invoice", "files": []})
        self.assertIn(response.status_code, (404, 405))

    def test_health_no_longer_mentions_file_search(self):
        body = self.client.get("/health").json()
        self.assertNotIn("file_search_mode", body)
        self.assertNotIn("file_index_uploaded", body)

    def test_find_file_is_not_an_allowed_action_kind(self):
        kinds = main.CommandAction.model_fields["kind"].annotation.__args__
        self.assertNotIn("find_file", kinds)
        self.assertIn("correct_last_dictation", kinds)


if __name__ == "__main__":
    unittest.main()
