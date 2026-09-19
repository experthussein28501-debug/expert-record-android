"""Fail CI on unsafe binary settings or missing actual app obfuscation."""
import re
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

apk, manifest, mapping = map(Path, sys.argv[1:])
a = '{http://schemas.android.com/apk/res/android}'
root = ET.parse(manifest).getroot()
app = root.find('application')
assert app is not None
assert app.get(a + 'debuggable', 'false') == 'false', 'APK permits debugging'
assert app.get(a + 'allowBackup') == 'false', 'Backup must be disabled'
assert app.get(a + 'usesCleartextTraffic') == 'false', 'Cleartext traffic enabled'
for provider in app.findall('provider'):
    assert provider.get(a + 'exported', 'false') == 'false', 'Exported provider'
rules = mapping.read_text()
assert any(old != new and old.startswith('com.khabir.') for old, new in
           re.findall(r'^(\S+) -> (\S+):$', rules, re.M)), 'No app classes obfuscated'
with zipfile.ZipFile(apk) as archive:
    bad = [n for n in archive.namelist() if n.endswith(('.jks', '.keystore', '.pem')) or n.endswith('mapping.txt')]
    assert not bad, 'Private build material packaged: ' + str(bad)
print('PASS: non-debuggable, backup off, cleartext off, providers private, app obfuscated, no signing keys/mapping bundled')
