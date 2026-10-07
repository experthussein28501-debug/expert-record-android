#!/usr/bin/env python3
"""Prove the added regressions fail on the delivered 0.9.17 sources, then restore fixes."""
import pathlib
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
BASELINE = "d07162fa34f6a9f994048c28b811d4f74ca80c00"
PATHS = [
    "core/src/main/java/com/khabir/app/presentation/settings/ExpertProfileViewModel.kt",
    "core/src/main/java/com/khabir/app/domain/model/ReportCalculation.kt",
]
EXPECTED = {
    "selectingAlreadySelectedProviderDoesNotDeleteSavedKey",
    "editingThenClearingReplacementDraftPreservesSavedKey",
    "switchingProviderPreservesSavedPairAndClearsUnvalidatedDraft",
    "legacyNumericLabelDoesNotShiftAmountsOrLoseNotes",
}

subprocess.run(["git", "fetch", "--no-tags", "--depth=1", "origin", BASELINE], cwd=ROOT, check=True)
fixed = {p: (ROOT / p).read_bytes() for p in PATHS}
try:
    for p in PATHS:
        (ROOT / p).write_bytes(subprocess.check_output(["git", "show", f"{BASELINE}:{p}"], cwd=ROOT))
    result = subprocess.run([
        "./gradlew", "--no-daemon", "-PKHABIR_SKIP_SCREENSHOT_TESTS=true",
        ":core:testDebugUnitTest", "--rerun-tasks",
        "--tests", "com.khabir.app.presentation.settings.ExpertProfileKeyDraftTest",
        "--tests", "com.khabir.app.domain.model.ReportCalculationCodecTest",
    ], cwd=ROOT)
    evidence = ROOT / "review-regression-evidence"
    evidence.mkdir(exist_ok=True)
    failed = set()
    for report in (ROOT / "core/build/test-results/testDebugUnitTest").glob("TEST-*.xml"):
        if any(name in report.name for name in ("ExpertProfileKeyDraftTest", "ReportCalculationCodecTest")):
            shutil.copy2(report, evidence / report.name)
            tree = ET.parse(report)
            for case in tree.findall(".//testcase"):
                if case.find("failure") is not None or case.find("error") is not None:
                    failed.add(case.attrib["name"])
    if result.returncode == 0 or failed != EXPECTED:
        raise SystemExit(f"Unexpected baseline result: exit={result.returncode}, failures={sorted(failed)}")
    message = f"Baseline {BASELINE}: all four expected defects reproduced; fixes restored for full validation.\n"
    (evidence / "baseline.txt").write_text(message)
    print(message)
finally:
    for p, content in fixed.items():
        (ROOT / p).write_bytes(content)
