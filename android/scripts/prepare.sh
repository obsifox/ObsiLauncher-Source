#!/usr/bin/env bash
# Fetch the pinned ZalithLauncher2 sources and apply the ObsiLauncher overlay + patches.
# Usage: android/scripts/prepare.sh            (works locally and in CI)
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"   # .../android
# shellcheck disable=SC1091
source "$HERE/upstream.version"

WORK="${OBSI_ANDROID_WORK:-$HERE/.work}"
SRC="$WORK/upstream"

mkdir -p "$SRC"
if [ ! -d "$SRC/.git" ]; then
  git -C "$SRC" init -q
  git -C "$SRC" remote add origin "$UPSTREAM_REPO"
fi

echo "[prepare] fetching $UPSTREAM_REPO @ $UPSTREAM_TAG ($UPSTREAM_COMMIT)"
git -C "$SRC" fetch -q --depth 1 origin "$UPSTREAM_COMMIT"
git -C "$SRC" checkout -q --force FETCH_HEAD
git -C "$SRC" clean -fdxq

python3 "$HERE/scripts/patch.py" "$SRC"
echo "[prepare] ready: $SRC"
