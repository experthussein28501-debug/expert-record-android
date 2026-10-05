import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


class SecurityGateTest(unittest.TestCase):
    def check_gate(self, debug='false', backup='false', cleartext='false', exported='false', mapping=True, secret=False, test_host=False):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk, manifest, rules = (root / name for name in ('test.apk', 'manifest.xml', 'mapping.txt'))
            with zipfile.ZipFile(apk, 'w') as archive:
                archive.writestr('classes.dex', b'test fixture')
                if secret:
                    archive.writestr('assets/signing.keystore', b'fixture')
            manifest.write_text(
                '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
                f'<application android:debuggable="{debug}" android:allowBackup="{backup}" '
                f'android:usesCleartextTraffic="{cleartext}"><provider android:exported="{exported}"/>'
                + ('<activity android:name="com.khabir.app.presentation.AgendaTestActivity" android:exported="false"/>' if test_host else '')
                + '</application></manifest>')
            rules.write_text('com.khabir.agenda.Editor -> a.b:\n' if mapping else 'com.khabir.agenda.Editor -> com.khabir.agenda.Editor:\n')
            result = subprocess.run([sys.executable, str(Path(__file__).with_name('verify_apk_security.py')),
                                     str(apk), str(manifest), str(rules)], capture_output=True)
            return result.returncode == 0

    def test_secure_fixture_passes(self):
        self.assertTrue(self.check_gate())

    def test_each_insecure_property_fails(self):
        for field in ('debug', 'backup', 'cleartext', 'exported'):
            with self.subTest(field=field):
                self.assertFalse(self.check_gate(**{field: 'true'}))

    def test_no_obfuscation_fails(self):
        self.assertFalse(self.check_gate(mapping=False))

    def test_bundled_signing_material_fails(self):
        self.assertFalse(self.check_gate(secret=True))

    def test_instrumentation_host_cannot_ship_in_the_distributable(self):
        self.assertFalse(self.check_gate(test_host=True))
