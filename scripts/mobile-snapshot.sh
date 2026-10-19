#!/usr/bin/env bash
# N17 — renders the recorded digest at phone width and leaves a PNG for the regression test.
#
# Why a pinned container and not the Chrome on this machine: a screenshot diff is only a test if the
# renderer is the same on both sides of it. Local Chrome on macOS and whatever CI has installed
# disagree about font hinting and subpixel rendering on every single glyph, so a baseline recorded on
# one would fail on the other for reasons that have nothing to do with the layout. One pinned image,
# the same everywhere, is what makes the comparison mean something.
#
#   scripts/mobile-snapshot.sh            render to build/mobile/ for the test to compare
#   scripts/mobile-snapshot.sh --update   also replace the committed baseline
#
# The comparison itself lives in MobileRenderingRegressionTest, which reads the PNG this writes.
set -euo pipefail

CHROME_IMAGE="zenika/alpine-chrome:124"
WIDTH=375
# Tall enough for the whole document, not just the fold: at 375px the recorded edition renders ~7000px tall in the pinned container,
# and the first version of this script captured 2400 — so the tables, which are the most fragile part
# of the layout and the reason this test exists, were not in the image at all. The test asserts the
# capture actually reached the bottom, so this number failing to keep up is a loud failure and not a
# silently narrower guard.
HEIGHT=9000
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE="docs/sample-digest.html"
OUT_DIR="$ROOT/build/mobile"
OUT="$OUT_DIR/sample-digest-mobile.png"
BASELINE="$ROOT/docs/images/sample-digest-mobile.png"
RENDER_LOG="$OUT_DIR/renderer.log"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required: the snapshot must come from the pinned renderer, not a local browser." >&2
  exit 2
fi

mkdir -p "$OUT_DIR"

# Why the container is told which user to be.
#
# The image declares `USER chrome` (uid 1000), while the directory above belongs to whoever ran this
# script — uid 1001 on a GitHub runner, and 0755, so uid 1000 cannot create a file in it. A renderer
# that cannot write its `--screenshot` target exits 2 having written nothing, which is exactly how
# this gate failed on 2026-10-19 after passing on every macOS machine it was developed on: Docker
# Desktop grants a bind mount write access whatever the ownership underneath says, so the mismatch
# stays invisible until the build runs on Linux. Running the container as the invoking user removes
# the mismatch where it starts; widening the directory's permissions would only hide it again.
#
# Taking the uid away from `chrome` also takes away the home directory that uid owned, and a uid with
# no passwd entry in the image has no home to fall back on, so the profile is pointed somewhere
# world-writable. It has to be an explicit flag rather than `$HOME` alone, because Chromium derives
# the profile path once at startup and a failure there is a crash, not a warning.
status=0
docker run --rm \
  --user "$(id -u):$(id -g)" \
  -e HOME=/tmp \
  -v "$ROOT:/work" -w /work \
  --entrypoint chromium-browser \
  "$CHROME_IMAGE" \
  --headless --no-sandbox --disable-gpu --hide-scrollbars \
  --user-data-dir=/tmp/chromium-profile \
  --window-size="${WIDTH},${HEIGHT}" \
  --screenshot=/work/build/mobile/sample-digest-mobile.png \
  "file:///work/${SOURCE}" >"$RENDER_LOG" 2>&1 || status=$?

# Chromium warns about Bluetooth and sandbox threads on a perfectly good run, so its output is kept
# out of the way — but only until it is worth reading. Discarding it unconditionally, as the first
# version of this script did, is what turned the failure above into a bare "exit code 2" with not one
# line of explanation in the CI log; the renderer knew exactly what was wrong and was told to shut up.
if [ "$status" -ne 0 ]; then
  echo "Renderer failed (exit $status). Its own output follows:" >&2
  cat "$RENDER_LOG" >&2
  exit "$status"
fi

[ -f "$OUT" ] || {
  echo "Renderer reported success but produced no screenshot. Its output follows:" >&2
  cat "$RENDER_LOG" >&2
  exit 1
}
echo "Rendered ${WIDTH}px snapshot -> ${OUT#"$ROOT"/}"

if [ "${1:-}" = "--update" ]; then
  mkdir -p "$(dirname "$BASELINE")"
  cp "$OUT" "$BASELINE"
  echo "Baseline updated -> ${BASELINE#"$ROOT"/}"
  echo "Review the image before committing it: it is the thing the test will trust from now on."
fi
