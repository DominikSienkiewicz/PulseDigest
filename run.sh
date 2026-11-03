#!/usr/bin/env bash
set -euo pipefail

if [ ! -f .env ]; then
  echo "Error: .env not found — skopiuj .env.example i uzupełnij klucze API"
  exit 1
fi

set -a
# .env holds the credentials and is gitignored, so there is never a copy for shellcheck to read:
# `source=.env` resolved to nothing and left SC1091 for a human to wave through. /dev/null says
# "do not follow this" on purpose, which is the honest version of the same thing.
# shellcheck source=/dev/null
source .env
set +a

echo "Starting PulseDigest..."
./gradlew bootRun
