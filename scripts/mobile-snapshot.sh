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

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required: the snapshot must come from the pinned renderer, not a local browser." >&2
  exit 2
fi

mkdir -p "$OUT_DIR"
docker run --rm \
  -v "$ROOT:/work" -w /work \
  --entrypoint chromium-browser \
  "$CHROME_IMAGE" \
  --headless --no-sandbox --disable-gpu --hide-scrollbars \
  --window-size="${WIDTH},${HEIGHT}" \
  --screenshot=/work/build/mobile/sample-digest-mobile.png \
  "file:///work/${SOURCE}" >/dev/null 2>&1

[ -f "$OUT" ] || { echo "Renderer produced no screenshot." >&2; exit 1; }
echo "Rendered ${WIDTH}px snapshot -> ${OUT#"$ROOT"/}"

if [ "${1:-}" = "--update" ]; then
  mkdir -p "$(dirname "$BASELINE")"
  cp "$OUT" "$BASELINE"
  echo "Baseline updated -> ${BASELINE#"$ROOT"/}"
  echo "Review the image before committing it: it is the thing the test will trust from now on."
fi
