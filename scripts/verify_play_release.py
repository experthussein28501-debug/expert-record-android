"""Strict Google Play preflight checks for the production APK/AAB pair."""
from __future__ import annotations

import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
EXPECTED_PACKAGE = "com.khabir.app.combined"
EXPECTED_LABEL = "سجل الخبير"
MIN_TARGET_SDK = 36

# Permissions intentionally used by the app or added by current Google SDKs.
ALLOWED_PERMISSIONS = {
    "android.permission.CAMERA",
    "android.permission.RECORD_AUDIO",
    "android.permission.INTERNET",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.ACCESS_FINE_LOCATION",
    "com.google.android.gms.permission.AD_ID",
    "android.permission.ACCESS_ADSERVICES_AD_ID",
    "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
    "android.permission.ACCESS_ADSERVICES_TOPICS",
}

FORBIDDEN_PERMISSIONS = {
    "android.permission.ACCESS_BACKGROUND_LOCATION",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.READ_SMS",
    "android.permission.RECEIVE_SMS",
    "android.permission.SEND_SMS",
    "android.permission.READ_CALL_LOG",
    "android.permission.WRITE_CALL_LOG",
    "android.permission.READ_CONTACTS",
    "android.permission.WRITE_CONTACTS",
    "android.permission.QUERY_ALL_PACKAGES",
}


def _android_int(value: str | None, default: int = 0) -> int:
    if value is None:
        return default
    try:
        return int(value)
    except ValueError as exc:
        raise AssertionError(f"Expected integer Android manifest value, got {value!r}") from exc


def verify_manifest(path: Path) -> None:
    root = ET.parse(path).getroot()
    package_name = root.get("package", "")
    assert package_name == EXPECTED_PACKAGE, (
        f"Unexpected production package {package_name!r}; expected {EXPECTED_PACKAGE!r}"
    )
    assert not any(token in package_name for token in (".preview", ".trial", ".test", ".debug"))

    version_code = _android_int(root.get(ANDROID + "versionCode"))
    version_name = root.get(ANDROID + "versionName", "")
    assert version_code >= 101, f"Play versionCode must be >= 101, got {version_code}"
    assert version_name == "1.0.0", f"Play versionName must be 1.0.0, got {version_name!r}"

    uses_sdk = root.find("uses-sdk")
    assert uses_sdk is not None, "Merged manifest has no uses-sdk"
    target_sdk = _android_int(uses_sdk.get(ANDROID + "targetSdkVersion"))
    assert target_sdk >= MIN_TARGET_SDK, f"targetSdk {target_sdk} is below {MIN_TARGET_SDK}"

    app = root.find("application")
    assert app is not None
    assert app.get(ANDROID + "debuggable", "false") == "false"
    assert app.get(ANDROID + "allowBackup") == "false"
    assert app.get(ANDROID + "usesCleartextTraffic") == "false"
    assert app.get(ANDROID + "label") == EXPECTED_LABEL, (
        f"Production app label must be {EXPECTED_LABEL!r}, got {app.get(ANDROID + 'label')!r}"
    )

    permissions = {
        node.get(ANDROID + "name")
        for node in root.findall("uses-permission")
        if node.get(ANDROID + "name")
    }
    forbidden = permissions & FORBIDDEN_PERMISSIONS
    assert not forbidden, f"Forbidden Play permissions present: {sorted(forbidden)}"
    unexpected = permissions - ALLOWED_PERMISSIONS
    assert not unexpected, f"Unexpected permissions require review: {sorted(unexpected)}"

    exported = []
    for tag in ("activity", "activity-alias", "service", "receiver", "provider"):
        for node in app.findall(tag):
            if node.get(ANDROID + "exported") == "true":
                exported.append((tag, node.get(ANDROID + "name", "")))
    assert exported == [("activity", "com.khabir.app.MainActivity")], (
        f"Unexpected exported components: {exported}"
    )


def verify_aab(path: Path) -> None:
    assert path.is_file() and path.stat().st_size > 0, "AAB is missing or empty"
    assert path.stat().st_size < 500 * 1024 * 1024, "AAB exceeds Play base-module size guard"
    with zipfile.ZipFile(path) as bundle:
        names = set(bundle.namelist())
        assert "base/manifest/AndroidManifest.xml" in names, "AAB missing base manifest"
        assert any(name.startswith("base/dex/classes") and name.endswith(".dex") for name in names), (
            "AAB missing base DEX"
        )
        bad = [
            name for name in names
            if name.endswith((".jks", ".keystore", ".pem")) or name.endswith("mapping.txt")
        ]
        assert not bad, f"Private signing/build material packaged in AAB: {bad}"


def main(argv: list[str]) -> None:
    if len(argv) != 3:
        raise SystemExit("usage: verify_play_release.py <merged-manifest.xml> <release.aab>")
    manifest, aab = map(Path, argv[1:])
    verify_manifest(manifest)
    verify_aab(aab)
    print("PASS: Play package, version, targetSdk, permissions, exported components and AAB structure")


if __name__ == "__main__":
    main(sys.argv)
