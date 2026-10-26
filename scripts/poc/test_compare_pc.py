import json
from pathlib import Path
import tempfile
import unittest

import compare_pc


class ComparePcTest(unittest.TestCase):
    def test_expanded_suite_requires_one_hundred_unique_cases_and_known_version(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory) / "run-055-expanded"
            folder.mkdir()
            for name, count, version in [("complete", 100, 1), ("partial", 99, 1), ("unknown", 100, 2)]:
                records = [{"type": "run_start", "stage": "quality-pc", "model": name,
                            "suite": "expanded", "expandedVersion": version}]
                records += [{"type": "infer", "case": f"x{i:03}"} for i in range(1, count + 1)]
                records += [{"type": "quality_summary", "summary": {"total": count, "allMet": True}},
                            {"type": "run_end", "reason": "done"}]
                (folder / f"{name}.jsonl").write_text("\n".join(map(json.dumps, records)), encoding="utf-8")
            rows = {r["model"]: r for r in compare_pc.collect(Path(directory))}
            self.assertTrue(rows["complete"]["approved"])
            self.assertFalse(rows["partial"]["approved"])
            self.assertFalse(rows["unknown"]["approved"])

    def test_private_suite_requires_all_declared_sensitive_cases(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory) / "run-001-private"
            folder.mkdir()
            for name, count, sensitive in [("complete", 2, True), ("partial", 1, True), ("mixed", 2, False)]:
                records = [{"type": "run_start", "stage": "quality-pc", "model": name, "suite": "private",
                            "cases": {"fictitious": 0, "private": 2, "selected": 2}}]
                records += [{"type": "infer", "case": str(i), "sensitive": sensitive} for i in range(count)]
                records += [{"type": "quality_summary", "summary": {"total": count, "allMet": True}},
                            {"type": "run_end", "reason": "done"}]
                (folder / f"{name}.jsonl").write_text("\n".join(map(json.dumps, records)), encoding="utf-8")
            rows = {r["model"]: r for r in compare_pc.collect(Path(directory))}
            self.assertTrue(rows["complete"]["approved"])
            self.assertFalse(rows["partial"]["approved"])
            self.assertFalse(rows["mixed"]["approved"])

    def test_partial_diagnostic_cannot_qualify_even_when_all_metrics_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory) / "run-001-test"
            folder.mkdir()
            def save(name, count, done):
                records = [{"type": "run_start", "stage": "quality-pc", "model": name, "suite": "dev"}]
                records += [{"type": "infer", "case": str(i)} for i in range(count)]
                records += [{"type": "quality_summary", "summary": {"total": count, "allMet": True}}]
                if done:
                    records += [{"type": "run_end", "reason": "done"}]
                (folder / f"{name}.jsonl").write_text("\n".join(map(json.dumps, records)), encoding="utf-8")
            save("partial", 1, True)
            save("unfinished", 37, False)
            save("complete", 37, True)
            rows = {r["model"]: r for r in compare_pc.collect(Path(directory))}
            self.assertFalse(rows["partial"]["approved"])
            self.assertFalse(rows["unfinished"]["approved"])
            self.assertTrue(rows["complete"]["approved"])


    def test_brands_suite_requires_twenty_seven_unique_cases_and_known_version(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory) / "run-058-brands"
            folder.mkdir()
            for name, count, version in [("complete", 27, 1), ("partial", 26, 1), ("unknown", 27, 2)]:
                records = [{"type": "run_start", "stage": "quality-pc", "model": name,
                            "suite": "brands", "brandVersion": version}]
                records += [{"type": "infer", "case": f"b{i:03}"} for i in range(1, count + 1)]
                records += [{"type": "quality_summary", "summary": {"total": count, "allMet": True}},
                            {"type": "run_end", "reason": "done"}]
                (folder / f"{name}.jsonl").write_text("\n".join(map(json.dumps, records)), encoding="utf-8")
            rows = {r["model"]: r for r in compare_pc.collect(Path(directory))}
            self.assertTrue(rows["complete"]["approved"])
            self.assertFalse(rows["partial"]["approved"])
            self.assertFalse(rows["unknown"]["approved"])


if __name__ == "__main__":
    unittest.main()
