#!/usr/bin/env bash
set -u
STATUS=0
adb shell settings put global window_animation_scale 0
if [ -f to.apk ]; then
  if [ -f from.apk ]; then
    echo "===== published upgrade: from.apk -> to.apk ====="
    bash .github/smoke/launch.sh com.pace.reduction from.apk to.apk || STATUS=1
  fi
  echo "===== published fresh install: to.apk ====="
  bash .github/smoke/launch.sh com.pace.reduction to.apk || STATUS=1
fi
echo "===== this commit, minified release ====="
bash .github/smoke/launch.sh com.pace.reduction head-release.apk || STATUS=1
echo "===== this commit, debug ====="
bash .github/smoke/launch.sh com.pace.reduction.debug head-debug.apk || STATUS=1
exit $STATUS
