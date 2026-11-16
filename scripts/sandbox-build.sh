#!/usr/bin/env bash
# Runs inside the sbx cloud sandbox. Assumes the project is unpacked at ~/t8mini.
set -euxo pipefail

export DEBIAN_FRONTEND=noninteractive
export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:/opt/gradle/bin:/usr/local/bin:$PATH"

if ! command -v java >/dev/null; then
  sudo apt-get update -qq
  sudo apt-get install -y -qq \
    openjdk-21-jdk-headless ca-certificates ca-certificates-java \
    curl xz-utils unzip zip git >/dev/null
  # Debian leaves the Java truststore empty until this runs, which breaks sdkmanager TLS.
  sudo update-ca-certificates -f >/dev/null
  sudo dpkg-reconfigure ca-certificates-java >/dev/null 2>&1
fi

if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  sudo mkdir -p "$ANDROID_HOME/cmdline-tools" "$ANDROID_HOME/licenses"
  curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -o /tmp/cmdline.zip
  sudo unzip -qo /tmp/cmdline.zip -d "$ANDROID_HOME/cmdline-tools"
  sudo rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  sudo mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  printf '24333f8a63b6825ea9c5514f83c2829b004d1fee' | sudo tee "$ANDROID_HOME/licenses/android-sdk-license" >/dev/null
  printf 'd56f5187479451eabf01fb78af6dfcb131a6481e' | sudo tee -a "$ANDROID_HOME/licenses/android-sdk-license" >/dev/null
  printf '504667f4c0de7af1a06de9f4b1727b84351f2910' | sudo tee "$ANDROID_HOME/licenses/android-sdk-preview-license" >/dev/null
  sudo chown -R "$(id -u):$(id -g)" "$ANDROID_HOME"
  sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0" >/dev/null
fi
yes | sdkmanager --licenses >/dev/null 2>&1 || true

if [ ! -x /opt/gradle/bin/gradle ]; then
  curl -fsSL https://services.gradle.org/distributions/gradle-8.14.3-bin.zip -o /tmp/gradle.zip
  sudo unzip -qo /tmp/gradle.zip -d /opt
  sudo rm -rf /opt/gradle
  sudo mv /opt/gradle-8.14.3 /opt/gradle
fi

VARIANT="${VARIANT:-debug}"
TASK="assembleDebug"
[ "$VARIANT" = "release" ] && TASK="assembleRelease"

cd ~/t8mini
gradle --no-daemon --stacktrace :protocol:test ":app:$TASK"

ls -lh "app/build/outputs/apk/$VARIANT/app-$VARIANT.apk"
