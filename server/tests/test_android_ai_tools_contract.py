"""Guards against drift between the Android AI row (AiTools.kt) and the backend (app/transform.py).

If someone renames a style on one side only, the keyboard would send a value the server rejects with 422.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "server"))

from app import transform  # noqa: E402

KOTLIN = ROOT / "AKZSA-Android/app/src/main/java/com/nefeon/akzsa/keyboard/AiTools.kt"


def _blocks(source: str):
    """name -> text of `private val <name> = listOf( ... )`."""
    found = {}
    for match in re.finditer(r"private val (\w+) = listOf\((.*?)\n    \)", source, re.S):
        found[match.group(1)] = match.group(2)
    return found


def test_wire_names_match_server_tasks():
    source = KOTLIN.read_text(encoding="utf-8")
    wires = set(re.findall(r'\w+\("(\w+)", "[A-Za-z]+"\)', source))
    assert wires == set(transform.TASKS)


def test_every_android_style_is_accepted_by_the_server():
    blocks = _blocks(KOTLIN.read_text(encoding="utf-8"))
    task_for = {"rewrite": "rewrite", "reply": "reply", "structure": "structure"}
    for block_name, task in task_for.items():
        styles = re.findall(r'style = "(\w+)"', blocks[block_name])
        assert styles, block_name
        for style in styles:
            assert transform.resolve_style(task, style) == style


def test_every_android_language_is_accepted_by_the_server():
    blocks = _blocks(KOTLIN.read_text(encoding="utf-8"))
    languages = re.findall(r'language = "([A-Za-z ]+)"', blocks["translate"])
    assert len(languages) >= 3
    for language in languages:
        assert transform.resolve_language("translate", language) == language


def test_grammar_fix_style_is_accepted_by_the_server():
    """The sparkle button (Grammar Fix) runs a rewrite with the 'fix' style; both sides must agree."""
    workspace = KOTLIN.with_name("AiWorkspace.kt").read_text(encoding="utf-8")
    styles = re.findall(r'GRAMMAR_OPTION = AiOption\("Fix", style = "(\w+)"\)', workspace)
    assert styles == ["fix"]
    assert transform.resolve_style("rewrite", styles[0]) == "fix"
