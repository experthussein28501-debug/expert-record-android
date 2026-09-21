#!/usr/bin/env bash
set -euo pipefail

review_dir="release-output/integration-review-0.9.0"
mkdir -p "$review_dir"

# Build the exact optimized APK on the same runner/emulator job and install it immediately.
./gradlew --no-daemon -PKHABIR_TEST_BUILD_TYPE=trial :app:assembleCombinedTrial --stacktrace
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

adb shell uiautomator dump /sdcard/khabir-ui.xml >/dev/null
adb pull /sdcard/khabir-ui.xml /tmp/khabir-ui.xml >/dev/null

python3 - <<'PY' > /tmp/tap.txt
import re, xml.etree.ElementTree as ET
root=ET.parse("/tmp/khabir-ui.xml").getroot()
labels=("فتح النسخة التجريبية","تخطي","تخطي والدخول للتجربة")
for label in labels:
    for n in root.iter("node"):
        if n.attrib.get("text","") == label:
            m=re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.attrib.get("bounds",""))
            if m:
                x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); raise SystemExit
raise SystemExit("No supported trial entry button found")
PY
read -r x y < /tmp/tap.txt
adb shell input tap "$x" "$y"
sleep 3

adb shell uiautomator dump /sdcard/khabir-home.xml
adb pull /sdcard/khabir-home.xml "$review_dir/home.xml"
grep -q 'القضايا' "$review_dir/home.xml"
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

# Regression: opening the report camera must not close/crash the application.
adb shell pm grant "$pkg" android.permission.CAMERA || true
python3 - <<'PY' > /tmp/tap-new-report.txt
import re, xml.etree.ElementTree as ET
root=ET.parse('release-output/integration-review-0.9.0/reports.xml').getroot()
for n in root.iter('node'):
    if n.attrib.get('text','') == 'دعوى جديدة':
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds',''))
        if m:
            x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2, (y1+y2)//2); break
PY
read -r irx iry < /tmp/tap-new-report.txt
adb shell input tap "$irx" "$iry"
sleep 3
adb shell uiautomator dump /sdcard/khabir-report-editor.xml
adb pull /sdcard/khabir-report-editor.xml "$review_dir/report-editor.xml"
grep -q 'تقرير الخبرة' "$review_dir/report-editor.xml"

camera_found=0
for attempt in 1 2 3 4 5 6 7 8; do
  adb shell uiautomator dump /sdcard/khabir-report-camera-target.xml >/dev/null
  adb pull /sdcard/khabir-report-camera-target.xml /tmp/khabir-report-camera-target.xml >/dev/null
  python3 - <<'PY' > /tmp/tap-report-camera.txt
import re, xml.etree.ElementTree as ET
root=ET.parse('/tmp/khabir-report-camera-target.xml').getroot()
for n in root.iter('node'):
    if n.attrib.get('content-desc','') == 'تصوير الموضوع':
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds',''))
        if m:
            x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2, (y1+y2)//2); break
PY
  if [ -s /tmp/tap-report-camera.txt ]; then
    read -r crx cry < /tmp/tap-report-camera.txt
    adb shell input tap "$crx" "$cry"
    camera_found=1
    break
  fi
  # Stay inside the report ScrollView bounds on the emulator (roughly y=672..1542).
  adb shell input swipe 540 1450 540 750 350
  sleep 0.6
done
test "$camera_found" -eq 1
sleep 3
adb shell uiautomator dump /sdcard/khabir-report-camera.xml
adb pull /sdcard/khabir-report-camera.xml "$review_dir/report-camera.xml"
grep -q 'فتح Google Lens' "$review_dir/report-camera.xml"
adb shell pidof "$pkg" > /tmp/khabir-pid-before-camera-close
test -s /tmp/khabir-pid-before-camera-close

python3 - <<'PY' > /tmp/tap-camera-close.txt
import re, xml.etree.ElementTree as ET
root=ET.parse('release-output/integration-review-0.9.0/report-camera.xml').getroot()
for n in root.iter('node'):
    if n.attrib.get('content-desc','') == 'إغلاق الكاميرا':
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds',''))
        if m:
            x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2, (y1+y2)//2); break
PY
read -r ccx ccy < /tmp/tap-camera-close.txt
adb shell input tap "$ccx" "$ccy"
sleep 2
adb shell uiautomator dump /sdcard/khabir-report-after-camera.xml
adb pull /sdcard/khabir-report-after-camera.xml "$review_dir/report-after-camera.xml"
grep -q 'تقرير الخبرة' "$review_dir/report-after-camera.xml"
adb shell pidof "$pkg" > /tmp/khabir-pid-after-camera-close
test -s /tmp/khabir-pid-after-camera-close
adb exec-out screencap -p > "$review_dir/report-after-camera.png"

# Return from editor to reports hub, then to home.
adb shell input keyevent KEYCODE_BACK
sleep 2
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell uiautomator dump /sdcard/khabir-home2.xml
adb pull /sdcard/khabir-home2.xml /tmp/khabir-home2.xml

notification_found=0
for attempt in 1 2 3 4 5; do
  adb shell uiautomator dump /sdcard/khabir-home2.xml >/dev/null
  adb pull /sdcard/khabir-home2.xml /tmp/khabir-home2.xml >/dev/null
  python3 - <<'PY' > /tmp/tap-notifications.txt
import re, xml.etree.ElementTree as ET
root=ET.parse("/tmp/khabir-home2.xml").getroot()
for n in root.iter("node"):
    if n.attrib.get("text","") == "الإخطارات":
        m=re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.attrib.get("bounds",""))
        if m:
            x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); break
PY
  if [ -s /tmp/tap-notifications.txt ]; then
    read -r nx ny < /tmp/tap-notifications.txt
    adb shell input tap "$nx" "$ny"
    notification_found=1
    break
  fi
  adb shell input swipe 540 1550 540 500 300
  sleep 0.6
done
test "$notification_found" -eq 1
sleep 2
adb shell uiautomator dump /sdcard/khabir-notifications.xml
adb pull /sdcard/khabir-notifications.xml "$review_dir/notifications.xml"
grep -q 'الإخطارات وسركي الإخطارات' "$review_dir/notifications.xml"

# Finally run the bundled Arabic OCR instrumented test.
./gradlew --no-daemon -PKHABIR_TEST_BUILD_TYPE=trial :app:connectedCombinedTrialAndroidTest --stacktrace

cmp "$trial_apk" "$review_dir/verified-optimized.apk"
"$(dirname "$AAPT")/apksigner" verify --verbose "$review_dir/verified-optimized.apk" > "$review_dir/signature-verification.txt"
echo "Integrated optimized app UI + Arabic OCR verification passed."

