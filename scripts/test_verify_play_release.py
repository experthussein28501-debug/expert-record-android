import tempfile
import unittest
import zipfile
from pathlib import Path

from verify_play_release import verify_aab, verify_manifest


ANDROID = "http://schemas.android.com/apk/res/android"


def manifest_xml(package="com.khabir.app.combined", version_code="101", version_name="1.0.0",
                 target="36", label="سجل الخبير", extra_permission=""):
    extra = f'<uses-permission android:name="{extra_permission}" />' if extra_permission else ""
    return f'''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="{ANDROID}" package="{package}"
    android:versionCode="{version_code}" android:versionName="{version_name}">
    <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="{target}" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.CAMERA" />
    {extra}
    <application android:debuggable="false" android:allowBackup="false"
        android:usesCleartextTraffic="false" android:label="{label}">
        <activity android:name="com.khabir.app.MainActivity" android:exported="true" />
        <provider android:name="androidx.core.content.FileProvider" android:exported="false" />
    </application>
</manifest>'''


class VerifyPlayReleaseTest(unittest.TestCase):
    def write_manifest(self, directory, text):
        path = Path(directory) / "manifest.xml"
        path.write_text(text, encoding="utf-8")
        return path

    def write_aab(self, directory, include_secret=False):
        path = Path(directory) / "app.aab"
        with zipfile.ZipFile(path, "w") as bundle:
            bundle.writestr("base/manifest/AndroidManifest.xml", b"binary")
            bundle.writestr("base/dex/classes.dex", b"dex")
            if include_secret:
                bundle.writestr("base/root/upload-key.jks", b"secret")
        return path

    def test_store_ready_manifest_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            verify_manifest(self.write_manifest(tmp, manifest_xml()))

    def test_preview_package_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(AssertionError):
                verify_manifest(self.write_manifest(tmp, manifest_xml(package="com.khabir.app.combined.preview")))

    def test_old_target_sdk_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(AssertionError):
                verify_manifest(self.write_manifest(tmp, manifest_xml(target="35")))

    def test_background_location_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(AssertionError):
                verify_manifest(self.write_manifest(
                    tmp, manifest_xml(extra_permission="android.permission.ACCESS_BACKGROUND_LOCATION")
                ))

    def test_unreviewed_permission_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(AssertionError):
                verify_manifest(self.write_manifest(
                    tmp, manifest_xml(extra_permission="android.permission.READ_CALENDAR")
                ))


    def test_reviewed_sdk_permissions_are_allowed(self):
        with tempfile.TemporaryDirectory() as tmp:
            text = manifest_xml(extra_permission="android.permission.FOREGROUND_SERVICE")
            text = text.replace(
                '<uses-permission android:name="android.permission.CAMERA" />',
                '<uses-permission android:name="android.permission.CAMERA" />'
                '<uses-permission android:name="android.permission.WAKE_LOCK" />'
                '<uses-permission android:name="com.google.android.providers.gsf.permission.READ_GSERVICES" />'
                '<uses-permission android:name="com.khabir.app.combined.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" />'
            )
            verify_manifest(self.write_manifest(tmp, text))

    def test_play_aab_structure_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            verify_aab(self.write_aab(tmp))

    def test_signing_material_must_not_be_packaged(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(AssertionError):
                verify_aab(self.write_aab(tmp, include_secret=True))


if __name__ == "__main__":
    unittest.main()
