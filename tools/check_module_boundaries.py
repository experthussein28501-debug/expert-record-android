"""Fail CI when a feature takes a dependency on another feature."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
FEATURES = ("auth", "cases", "notifications", "reports")
SHARED_PARSERS = {"PetitionIntakeParser", "DocumentReviewParser", "IntakeNarrative"}

class ModuleBoundaries(unittest.TestCase):
    def test_features_only_depend_on_core(self):
        for feature in FEATURES:
            script = (ROOT / f"feature-{feature}/build.gradle.kts").read_text()
            self.assertEqual([":core"], re.findall(r'project\("([^"]+)"\)', script), feature)

    def test_no_sibling_ui_imports(self):
        for feature in FEATURES:
            for source in (ROOT / f"feature-{feature}/src/main").rglob("*.kt"):
                for package, symbol in re.findall(r"import com\.khabir\.app\.presentation\.([a-z]+)\.([\w]+)", source.read_text()):
                    if package in FEATURES and package != feature:
                        self.assertTrue(package == "cases" and symbol in SHARED_PARSERS, str(source))

    def test_notification_app_excludes_other_feature_modules(self):
        script = (ROOT / "notification-app/build.gradle.kts").read_text()
        self.assertEqual([":core", ":feature-notifications"], re.findall(r'project\("([^"]+)"\)', script))

    def test_report_app_excludes_other_feature_modules(self):
        script = (ROOT / "report-app/build.gradle.kts").read_text()
        self.assertEqual([":core", ":feature-reports"], re.findall(r'project\("([^"]+)"\)', script))

    def test_core_does_not_depend_on_features(self):
        self.assertNotIn('project(":feature-', (ROOT / "core/build.gradle.kts").read_text())
        for source in (ROOT / "core/src/main").rglob("*.kt"):
            self.assertNotRegex(source.read_text(), r"import com\.khabir\.app\.presentation\.(auth|reports|notifications)\.")

if __name__ == "__main__":
    unittest.main(verbosity=2)
