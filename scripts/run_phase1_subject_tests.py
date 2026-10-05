#!/usr/bin/env python3
"""Run focused Kotlin/JUnit tests without building an APK or downloading dependencies."""
import argparse, pathlib, subprocess, tempfile
ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCES = ['core/src/main/java/com/khabir/app/domain/model/Party.kt', 'core/src/main/java/com/khabir/app/domain/model/LawyerNotification.kt', 'core/src/main/java/com/khabir/app/domain/model/CaseDocumentFields.kt', 'core/src/main/java/com/khabir/app/domain/model/UnifiedCaseSubject.kt', 'core/src/main/java/com/khabir/app/domain/model/PetitionSubjectExtraction.kt', 'core/src/main/java/com/khabir/app/domain/model/PetitionSubjectRules.kt', 'core/src/main/java/com/khabir/app/presentation/cases/PetitionIntakeParser.kt', 'core/src/main/java/com/khabir/app/presentation/cases/LawyerIntakeParser.kt', 'core/src/main/java/com/khabir/app/presentation/cases/JudgmentCaseIdentity.kt', 'core/src/main/java/com/khabir/app/presentation/cases/IntakeNarrative.kt', 'core/src/main/java/com/khabir/app/presentation/cases/DocumentReviewParser.kt', 'feature-reports/src/main/java/com/khabir/app/presentation/reports/CaseSubjectFormatter.kt', 'core/src/test/java/com/khabir/app/presentation/cases/PetitionIntakeParserTest.kt', 'core/src/test/java/com/khabir/app/presentation/cases/DocumentReviewParserTest.kt', 'core/src/test/java/com/khabir/app/presentation/cases/DocumentReviewConfidenceTest.kt', 'core/src/test/java/com/khabir/app/presentation/cases/LawyerNotificationTest.kt', 'core/src/test/java/com/khabir/app/presentation/cases/JudgmentCaseIdentityTest.kt', 'core/src/test/java/com/khabir/app/presentation/cases/IntakeNarrativeTest.kt', 'core/src/test/java/com/khabir/app/domain/model/PetitionSubjectExtractionTest.kt', 'feature-reports/src/test/java/com/khabir/app/presentation/reports/CaseSubjectFormatterTest.kt', 'core/src/main/java/com/khabir/app/domain/model/Case.kt', 'core/src/main/java/com/khabir/app/domain/model/Report.kt', 'core/src/main/java/com/khabir/app/domain/model/ExpertProfile.kt', 'core/src/main/java/com/khabir/app/domain/model/ReportTextFormat.kt', 'core/src/main/java/com/khabir/app/domain/model/ReportCalculation.kt', 'core/src/main/java/com/khabir/app/domain/model/ArabicNumerals.kt', 'core/src/main/java/com/khabir/app/domain/model/ReportTemplate.kt']
CLASSES = ['com.khabir.app.domain.model.PetitionSubjectExtractionTest', 'com.khabir.app.presentation.cases.IntakeNarrativeTest', 'com.khabir.app.presentation.cases.PetitionIntakeParserTest', 'com.khabir.app.presentation.cases.DocumentReviewParserTest', 'com.khabir.app.presentation.cases.DocumentReviewConfidenceTest', 'com.khabir.app.presentation.cases.JudgmentCaseIdentityTest', 'com.khabir.app.presentation.cases.LawyerNotificationTest', 'com.khabir.app.presentation.reports.CaseSubjectFormatterTest']
parser = argparse.ArgumentParser()
parser.add_argument('--kotlinc', required=True)
parser.add_argument('--junit', required=True)
parser.add_argument('--hamcrest', required=True)
parser.add_argument('--stdlib', required=True)
a = parser.parse_args()
with tempfile.TemporaryDirectory(prefix='expert-phase1-') as td:
    td = pathlib.Path(td)
    sources = td / 'sources.txt'
    sources.write_text('\n'.join(str(ROOT / f) for f in SOURCES) + '\n')
    jar = td / 'tests.jar'
    subprocess.run([a.kotlinc, '@' + str(sources), '-classpath', a.junit, '-d', str(jar)], check=True)
    subprocess.run(['java', '-cp', ':'.join([str(jar), a.stdlib, a.junit, a.hamcrest]), 'org.junit.runner.JUnitCore', *CLASSES], check=True)
