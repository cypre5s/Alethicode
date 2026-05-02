import tempfile
import unittest
from pathlib import Path

from tools.ai_tutor.kt_baseline.runner import (
    load_interactions,
    run_baselines,
    split_by_course_time,
    validate_no_leakage,
)


FIXTURE_PATH = Path(__file__).resolve().parents[1] / "fixtures" / "sample_interactions.jsonl"


class KtBaselineTest(unittest.TestCase):

    def test_split_by_course_time_should_prevent_temporal_leakage(self):
        interactions = load_interactions(FIXTURE_PATH)

        splits = split_by_course_time(interactions, train_ratio=0.6, val_ratio=0.2)
        validation = validate_no_leakage(splits)

        self.assertTrue(validation["passed"])
        self.assertEqual(validation["violations"], [])
        self.assertGreater(len(splits["train"]), 0)
        self.assertGreater(len(splits["val"]), 0)
        self.assertGreater(len(splits["test"]), 0)

    def test_run_baselines_should_emit_leaderboard_and_report(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            output_dir = Path(tmp_dir)

            result = run_baselines(
                FIXTURE_PATH,
                output_dir,
                seed=7,
                epochs=1,
                train_ratio=0.6,
                val_ratio=0.2,
            )

            self.assertEqual(set(result["leaderboard"].keys()), {"BKT-lite", "DKT", "AKT"})
            self.assertTrue((output_dir / "leaderboard.json").is_file())
            self.assertTrue((output_dir / "report.md").is_file())
            self.assertTrue((output_dir / "split_summary.json").is_file())


if __name__ == "__main__":
    unittest.main()
