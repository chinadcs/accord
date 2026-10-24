"""Behavioral checks for instruction generation, independent of any Android app.

Run from the repository root:
    python -m unittest discover -s .codex -p "test_*.py"
"""

from contextlib import redirect_stdout
import importlib.util
from io import StringIO
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location("sync_agents", Path(__file__).with_name("sync-agents.py"))
SYNC = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SYNC)


class SyncAgentsTests(unittest.TestCase):
    def setUp(self):
        # The tests operate on a separate tree. No fixture ever rewrites the actual
        # agent catalog or depends on an application/environment that does not exist.
        temporary = tempfile.TemporaryDirectory(prefix="accord-sync-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        root_patch = patch.object(SYNC, "ROOT", self.root)
        root_patch.start()
        self.addCleanup(root_patch.stop)
        self.skill = self.root / ".claude/skills/sample/SKILL.md"
        self.agent = self.root / ".claude/agents/example.md"
        self.skill.parent.mkdir(parents=True)
        self.agent.parent.mkdir(parents=True)
        self.skill.write_text(
            "---\nname: sample\ndescription: Sample skill\n---\n\nBody\n", encoding="utf-8"
        )
        self.source = (
            "---\nname: example\ndescription: Sample role\nmodel: opus\n"
            "skills: [sample]\n---\n\nBody\n"
        )
        self.agent.write_text(self.source, encoding="utf-8")
        self.adapter = self.root / ".codex/agents/example.toml"
        self.entry = self.root / ".agents/skills/sample/SKILL.md"

    def run_sync(self, *arguments):
        with patch("sys.argv", ["sync-agents.py", *arguments]), redirect_stdout(StringIO()):
            return SYNC.main()

    def test_check_reports_missing_output_without_creating_directories(self):
        self.assertEqual(self.run_sync("--check"), 1)
        self.assertFalse((self.root / ".codex").exists())
        self.assertFalse((self.root / ".agents").exists())

    def test_generation_points_to_shared_sources_and_repeated_runs_do_not_rewrite(self):
        self.assertEqual(self.run_sync(), 0)
        self.assertIn(".claude/agents/example.md", self.adapter.read_text(encoding="utf-8"))
        self.assertIn(".claude/skills/sample/SKILL.md", self.entry.read_text(encoding="utf-8"))
        self.assertEqual(self.run_sync("--check"), 0)
        before = {p: p.stat().st_mtime_ns for p in (self.adapter, self.entry)}
        self.assertEqual(self.run_sync(), 0)
        self.assertEqual(before, {p: p.stat().st_mtime_ns for p in before})

    def test_check_detects_drift_without_repairing_it_then_generation_repairs_it(self):
        self.run_sync()
        original = self.adapter.read_text(encoding="utf-8")
        self.adapter.write_text(original + "# drift\n", encoding="utf-8")
        self.assertEqual(self.run_sync("--check"), 1)
        self.assertEqual(self.adapter.read_text(encoding="utf-8"), original + "# drift\n")
        self.assertEqual(self.run_sync(), 0)
        self.assertEqual(self.adapter.read_text(encoding="utf-8"), original)

    def test_manual_file_is_preserved_and_preflight_prevents_partial_writes(self):
        self.run_sync()
        self.adapter.write_text('name = "manual"\n', encoding="utf-8")
        before = self.entry.read_bytes()
        self.skill.write_text(
            self.skill.read_text(encoding="utf-8").replace("Sample skill", "New description"),
            encoding="utf-8",
        )
        with self.assertRaisesRegex(ValueError, "manual file"):
            self.run_sync()
        self.assertEqual(self.adapter.read_text(encoding="utf-8"), 'name = "manual"\n')
        self.assertEqual(self.entry.read_bytes(), before)

    def test_obsolete_generated_file_requires_review_and_is_not_deleted(self):
        self.run_sync()
        obsolete = self.adapter.with_name("obsolete.toml")
        original = self.adapter.read_bytes()
        obsolete.write_bytes(original)
        with self.assertRaisesRegex(ValueError, "Obsolete generated file"):
            self.run_sync()
        self.assertEqual(obsolete.read_bytes(), original)

    def test_missing_skill_is_rejected_before_any_output_is_created(self):
        self.agent.write_text(self.source.replace("[sample]", "[missing]"), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "missing project skills"):
            self.run_sync()
        self.assertFalse(self.adapter.exists())
        self.assertFalse(self.entry.exists())

    def test_mismatched_source_name_is_rejected(self):
        self.agent.write_text(self.source.replace("name: example", "name: mismatch"), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "mismatched name"):
            self.run_sync()
        self.assertFalse(self.adapter.exists())

    def test_missing_frontmatter_is_rejected(self):
        self.skill.write_text("No metadata\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "Missing YAML frontmatter"):
            self.run_sync()
        self.assertFalse(self.adapter.exists())


if __name__ == "__main__":
    unittest.main()
