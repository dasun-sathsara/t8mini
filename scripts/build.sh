#!/usr/bin/env bash
# Build the T8 Mini app on a Docker cloud sandbox and install it on the Pixel.
# Usage: ./scripts/build.sh [sandbox-name] [output-apk]
#   KEEP=1     leave the sandbox running (default here, sandboxes are cheap to keep warm)
#   RELEASE=1  build the release variant instead of debug
set -euo pipefail

NAME="${1:-t8-apk-build}"
HERE="$(cd "$(dirname "$0")/.." && pwd)"
VARIANT="debug"
[ "${RELEASE:-0}" = "1" ] && VARIANT="release"
OUT="${2:-$HOME/Home/data/t8mini-$VARIANT.apk}"
REMOTE_APK="/home/agent/t8mini/app/build/outputs/apk/$VARIANT/app-$VARIANT.apk"

command -v sbx >/dev/null || { echo "sbx is missing: brew install docker/tap/sbx && sbx login"; exit 1; }

remote() { sbx --cloud exec "$NAME" -- bash -lc "$*"; }

if ! sbx --cloud ls | awk '{print $1}' | grep -qx "shell/$NAME"; then
  echo "==> Creating $NAME (XL, amd64)"
  sbx --cloud create shell --name "$NAME" \
    --cpus 16 --memory 32g --ttl 4h --on-timeout delete \
    --platform linux/amd64
fi

echo "==> Starting $NAME"
for _ in $(seq 1 60); do
  sbx --cloud exec "$NAME" -- true >/dev/null 2>&1 && break
  sleep 5
done
remote 'true' >/dev/null || { echo "sandbox did not start"; exit 1; }

echo "==> Uploading project"
TARBALL="$(mktemp -t t8mini).tgz"
tar czf "$TARBALL" -C "$HERE" \
  --exclude build --exclude .gradle --exclude .git --exclude local.properties .
sbx --cloud cp "$TARBALL" "$NAME:/home/agent/t8mini.tgz" >/dev/null
sbx --cloud cp "$HERE/scripts/sandbox-build.sh" "$NAME:/home/agent/t8-build.sh" >/dev/null
remote 'rm -rf ~/t8mini && mkdir -p ~/t8mini && tar xzf ~/t8mini.tgz -C ~/t8mini'

echo "==> Building $VARIANT (detached; a long exec stream can drop)"
remote 'chmod +x ~/t8-build.sh; VARIANT='$VARIANT' setsid nohup bash ~/t8-build.sh > ~/build.log 2>&1 < /dev/null & echo started' || true

while true; do
  sleep 20
  remote 'grep -qE "BUILD SUCCESSFUL|BUILD FAILED|FAILURE:|error:" ~/build.log 2>/dev/null' && break
  if [ "$SECONDS" -gt 3600 ]; then
    echo
    echo "==> Timed out after an hour, last lines:"
    remote 'tail -60 ~/build.log'
    exit 1
  fi
  printf .
done
echo

if ! remote 'grep -q "BUILD SUCCESSFUL" ~/build.log'; then
  echo "==> Build failed, last lines:"
  remote 'tail -80 ~/build.log'
  exit 1
fi

echo "==> Copying APK to $OUT"
sbx --cloud cp "$NAME:$REMOTE_APK" "$OUT" >/dev/null
shasum -a 256 "$OUT"

serial="$(adb devices | awk '$2=="device"{print $1; exit}')"
if [ -n "$serial" ]; then
  echo "==> Installing on $serial"
  adb -s "$serial" install -r "$OUT" | tail -1
else
  echo "==> No USB device attached; skipping install"
fi

if [ "${KEEP:-1}" != "1" ]; then
  echo "==> Stopping $NAME"
  sbx --cloud stop "$NAME" >/dev/null
fi
