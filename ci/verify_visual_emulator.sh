#!/usr/bin/env bash
set -euo pipefail

review_dir="release-output/visual-emulator-0.9.9"
mkdir -p "$review_dir"

./gradlew --no-daemon -PKHABIR_TEST_BUILD_TYPE=trial :app:assembleCombinedTrial --stacktrace
apk="app/build/outputs/apk/combined/trial/app-combined-trial.apk"
test -s "$apk"

AAPT="$ANDROID_HOME/build-tools/36.0.0/aapt"
if [ ! -x "$AAPT" ]; then
  AAPT="$(find "$ANDROID_HOME/build-tools" -type f -name aapt | sort -V | tail -n 1)"
fi
test -x "$AAPT"
pkg=$("$AAPT" dump badging "$apk" | sed -n "s/package: name='\([^']*\)'.*/\1/p")
test -n "$pkg"

adb install -r "$apk"
adb shell am force-stop "$pkg"
adb shell am start -W -n "$pkg/com.khabir.app.MainActivity"
sleep 2

dump_ui() {
  local name="$1"
  adb shell uiautomator dump "/sdcard/khabir-$name.xml" >/dev/null
  adb pull "/sdcard/khabir-$name.xml" "$review_dir/$name.xml" >/dev/null
  adb exec-out screencap -p > "$review_dir/$name.png"
  test -s "$review_dir/$name.png"
}

tap_text() {
  local xml="$1"
  local needle="$2"
  python3 - "$xml" "$needle" <<'PY'
import re, sys, xml.etree.ElementTree as ET
path, needle = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()
for n in root.iter("node"):
    text = n.attrib.get("text", "")
    desc = n.attrib.get("content-desc", "")
    if needle == text or needle in text or needle == desc or needle in desc:
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.attrib.get("bounds",""))
        if m:
            x1,y1,x2,y2 = map(int,m.groups())
            print((x1+x2)//2, (y1+y2)//2)
            raise SystemExit(0)
raise SystemExit("Missing tappable text: " + needle)
PY
}

assert_texts_and_bounds() {
  local xml="$1"; shift
  python3 - "$xml" "$@" <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET
path, *needles = sys.argv[1:]
size = subprocess.check_output(["adb","shell","wm","size"], text=True)
m = re.search(r"(\d+)x(\d+)", size)
if not m:
    raise SystemExit("Could not read emulator screen size")
width, height = map(int, m.groups())
root = ET.parse(path).getroot()
nodes = list(root.iter("node"))
for needle in needles:
    matches = [n for n in nodes if needle in n.attrib.get("text","") or needle in n.attrib.get("content-desc","")]
    if not matches:
        raise SystemExit(f"Missing expected UI text: {needle}")
    for n in matches:
        b = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.attrib.get("bounds",""))
        if not b:
            continue
        x1,y1,x2,y2 = map(int,b.groups())
        if x1 < 0 or y1 < 0 or x2 > width or y2 > height or x2 <= x1 or y2 <= y1:
            raise SystemExit(f"Out-of-bounds UI for {needle}: {(x1,y1,x2,y2)} vs {(width,height)}")
PY
}

# Login / entry gate.
dump_ui "login"
if grep -q 'تخطي والدخول للتجربة' "$review_dir/login.xml"; then
  read -r x y < <(tap_text "$review_dir/login.xml" "تخطي والدخول للتجربة")
  adb shell input tap "$x" "$y"
  sleep 2
fi

dump_ui "home"
assert_texts_and_bounds "$review_dir/home.xml" "القضايا" "الإخطارات" "التقارير" "محاضر الأعمال" "الأجندة"

open_and_capture() {
  local home_label="$1"
  local name="$2"
  shift 2
  dump_ui "home-before-$name"
  read -r x y < <(tap_text "$review_dir/home-before-$name.xml" "$home_label")
  adb shell input tap "$x" "$y"
  sleep 2
  dump_ui "$name"
  assert_texts_and_bounds "$review_dir/$name.xml" "$@"
  adb shell input keyevent KEYCODE_BACK
  sleep 1
}

open_and_capture "القضايا" "cases" "بيانات القضايا" "قضية جديدة"
open_and_capture "التقارير" "reports" "التقارير" "تقرير جديد أو من دعوى مسجلة"
open_and_capture "الإخطارات" "notifications" "الإخطارات وسركي الإخطارات"
open_and_capture "محاضر الأعمال" "work-minutes" "محاضر الأعمال" "مجموعة جديدة أو من دعوى مسجلة"
open_and_capture "الأجندة" "agenda" "الأجندة"

# Re-check home after all back navigation.
dump_ui "home-final"
assert_texts_and_bounds "$review_dir/home-final.xml" "القضايا" "الإخطارات" "التقارير" "محاضر الأعمال" "الأجندة"

# Detect blank or suspiciously tiny screenshots.
python3 - "$review_dir" <<'PY'
import os, sys, struct, zlib
folder = sys.argv[1]
pngs = [os.path.join(folder,f) for f in os.listdir(folder) if f.endswith(".png")]
if len(pngs) < 7:
    raise SystemExit(f"Expected at least 7 screenshots, got {len(pngs)}")
for p in pngs:
    if os.path.getsize(p) < 5000:
        raise SystemExit(f"Screenshot unexpectedly small: {p}")
print("Visual emulator screenshots:", len(pngs))
PY

echo "Emulator visual navigation and bounds checks passed."
