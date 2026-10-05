#!/usr/bin/env bash
# Drives an installed Pace build through first launch, onboarding and every tab, and prints any
# crash with its stack trace. Usage: launch.sh <package> <apk> [<apk to upgrade to>]
set -u
PKG="$1"; APK="$2"; UPGRADE="${3:-}"
ACTIVITY="$PKG/com.pace.reduction.MainActivity"
FAILED=0

crash_check() {
  local step="$1"
  sleep 4
  local crash
  crash=$(adb logcat -d -b crash 2>/dev/null)
  if [ -n "$crash" ] || ! adb shell pidof "$PKG" >/dev/null; then
    {
      echo "===== CRASH: $APK after step: $step ====="
      echo "$crash" | head -120
      echo "----- AndroidRuntime -----"
      adb logcat -d | grep -E "AndroidRuntime|FATAL|Exception|Caused by" | head -60
    } | tee -a smoke-report.txt
    echo "::error::CRASH in $APK after step: $step"
    FAILED=1
    adb logcat -c; adb logcat -b crash -c
    return 1
  fi
  echo "OK after step: $step" | tee -a smoke-report.txt
}

tap_text() {
  local text="$1"
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  local xy
  xy=$(python3 - "$text" <<'PY'
import re, sys, xml.etree.ElementTree as ET
want = sys.argv[1]
root = ET.parse('/tmp/ui.xml').getroot()
for node in root.iter('node'):
    if node.get('text') == want or node.get('content-desc') == want:
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)
  if [ -z "$xy" ]; then
    echo "::warning::'$text' not on screen"
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && adb shell cat /sdcard/ui.xml | grep -oE 'text="[^"]+"' | head -40
    return 1
  fi
  adb shell input tap $xy
  sleep 2
}

launch() {
  adb logcat -c; adb logcat -b crash -c
  adb shell am start -W -n "$ACTIVITY"
  crash_check "launch ($1)"
}

walk_tabs() {
  # A tab that cannot be found fails the run: a renamed label must not quietly skip a screen.
  for tab in Toolkit Coach Insights Today; do
    if tap_text "$tab"; then crash_check "open $tab tab"; else FAILED=1; fi
  done
  adb shell input swipe 540 1800 540 500 300; sleep 1
  adb shell input swipe 540 1800 540 500 300
  crash_check "scroll Today"
}

adb install -r "$APK"
launch "first install"
tap_text "Use safe defaults" && crash_check "safe defaults"
tap_text "Create my plan" && crash_check "finish onboarding"
walk_tabs
adb shell input keyevent KEYCODE_HOME; sleep 2

if [ -n "$UPGRADE" ]; then
  echo "===== upgrading to $UPGRADE ====="
  adb install -r "$UPGRADE"
  launch "after upgrade"
  walk_tabs
fi

adb uninstall "$PKG" >/dev/null 2>&1 || true
exit $FAILED
