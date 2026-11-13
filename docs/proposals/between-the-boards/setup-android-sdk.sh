#!/usr/bin/env bash
# Installs the Android SDK this project builds with and points Gradle at it.
#
#   bash docs/proposals/between-the-boards/setup-android-sdk.sh      (from the repository root)
#
# It downloads from dl.google.com, so that host has to be reachable. Safe to run again: it skips
# what is already there. These are the same commands as the setup script of the "Game-Suite"
# cloud environment, for a session whose container was built without it.
set -euo pipefail

SDK="${ANDROID_HOME:-/opt/android-sdk}"

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "$SDK/cmdline-tools"
  curl -fsSL -o /tmp/cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -q -o /tmp/cmdline-tools.zip -d "$SDK/cmdline-tools"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm /tmp/cmdline-tools.zip
fi

# "yes" is cut off when sdkmanager has read enough, which is not a failure.
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-35" "build-tools;34.0.0" > /dev/null

echo "sdk.dir=$SDK" > local.properties
echo "Android SDK ready at $SDK. Try: ./gradlew assemblePreview"
