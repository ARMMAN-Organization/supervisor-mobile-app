#!/bin/bash
# Run Supervisor on the MOBILE (A142 phone)
set -e
SERIAL=000583458001183
ADB=/Users/nd-bharath/Library/Android/sdk/platform-tools/adb
cd "$(dirname "$0")"

# Local per-developer override (untracked, see run-mobile.local.sh.example)
if [ -f "run-mobile.local.sh" ]; then
  source run-mobile.local.sh
fi

export ANDROID_SERIAL=$SERIAL
./gradlew installDebug
"$ADB" -s "$SERIAL" shell am start -n org.armman.supervisor/.MainActivity
echo "✅ Supervisor launched on MOBILE (A142)"
