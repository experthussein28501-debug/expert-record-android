#!/usr/bin/env bash
set -euo pipefail

review_dir="release-output/integration-review-0.9.0"
mkdir -p "$review_dir"

# Build the exact optimized APK on the same runner/emulator job and install it immediately.
gradle -PKHABIR_TEST_BUILD_TYPE=trial :app:assembleCombinedTrial --stacktrace
trial_apk="app/build/outputs/apk/combined/trial/app-combined-trial.apk"
test -s "$trial_apk"

AAPT="$ANDROID_HOME/build-tools/36.0.0/aapt"
if [ ! -x "$AAPT" ]; then
  AAPT="$(find "$ANDROID_HOME/build-tools" -type f -name aapt | sort -V | tail -n 1)"
fi
test -x "$AAPT"

pkg=$("$AAPT" dump badging "$trial_apk" | sed -n "s/package: name='\([^']*\)'.*/\1/p")
test -n "$pkg"
echo "Testing package: $pkg"

adb install -r "$trial_apk"
adb shell am force-stop "$pkg"
adb shell am start -W -n "$pkg/com.khabir.app.MainActivity"
sleep 2

adb shell uiautomator dump /sdcard/khabir-ui.xml
adb pull /sdcard/khabir-ui.xml /tmp/khabir-ui.xml
grep -q 'تخطي والدخول للتجربة' /tmp/khabir-ui.xml

python3 - <<'PY' > /tmp/tap.txt
import re, xml.etree.ElementTree as ET
root=ET.parse('/tmp/khabir-ui.xml').getroot()
for n in root.iter('node'):
    if 'تخطي والدخول للتجربة' in n.attrib.get('text',''):
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib['bounds'])
        if m:
            x1,y1,x2,y2=map(int,m.groups())
            print((x1+x2)//2, (y1+y2)//2)
            break
PY
read -r x y < /tmp/tap.txt
adb shell input tap "$x" "$y"
sleep 2

adb shell uiautomator dump /sdcard/khabir-home.xml
adb pull /sdcard/khabir-home.xml "$review_dir/home.xml"
grep -q 'القضايا' "$review_dir/home.xml"
grep -q 'الإخطارات' "$review_dir/home.xml"
grep -q 'التقارير' "$review_dir/home.xml"
adb exec-out screencap -p > "$review_dir/app-home.png"

# Preserve the exact optimized APK that passed the UI checks before any other Gradle task.
cp "$trial_apk" "$review_dir/verified-optimized.apk"
test -s "$review_dir/verified-optimized.apk"

python3 - <<'PY' > /tmp/tap-report.txt
import re, xml.etree.ElementTree as ET
root=ET.parse('release-output/integration-review-0.9.0/home.xml').getroot()
for n in root.iter('node'):
    if n.attrib.get('text','') == 'التقارير':
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib['bounds'])
        if m:
            x1,y1,x2,y2=map(int,m.groups())
            print((x1+x2)//2, (y1+y2)//2)
            break
PY
read -r rx ry < /tmp/tap-report.txt
adb shell input tap "$rx" "$ry"
sleep 2
adb shell uiautomator dump /sdcard/khabir-reports.xml
adb pull /sdcard/khabir-reports.xml "$review_dir/reports.xml"
grep -q 'تقرير جديد أو من دعوى مسجلة' "$review_dir/reports.xml"

adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell uiautomator dump /sdcard/khabir-home2.xml
adb pull /sdcard/khabir-home2.xml /tmp/khabir-home2.xml

python3 - <<'PY' > /tmp/tap-notifications.txt
import re, xml.etree.ElementTree as ET
root=ET.parse('/tmp/khabir-home2.xml').getroot()
for n in root.iter('node'):
    if n.attrib.get('text','') == 'الإخطارات':
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib['bounds'])
        if m:
            x1,y1,x2,y2=map(int,m.groups())
            print((x1+x2)//2, (y1+y2)//2)
            break
PY
read -r nx ny < /tmp/tap-notifications.txt
adb shell input tap "$nx" "$ny"
sleep 2
adb shell uiautomator dump /sdcard/khabir-notifications.xml
adb pull /sdcard/khabir-notifications.xml "$review_dir/notifications.xml"
grep -q 'الإخطارات وسركي الإخطارات' "$review_dir/notifications.xml"

# Finally run the bundled Arabic OCR instrumented test.
gradle -PKHABIR_TEST_BUILD_TYPE=trial :app:connectedCombinedTrialAndroidTest --stacktrace

cmp "$trial_apk" "$review_dir/verified-optimized.apk"
"$(dirname "$AAPT")/apksigner" verify --verbose "$review_dir/verified-optimized.apk" > "$review_dir/signature-verification.txt"
echo "Integrated optimized app UI + Arabic OCR verification passed."

