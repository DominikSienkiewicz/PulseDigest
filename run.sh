#!/usr/bin/env bash
set -euo pipefail

if [ ! -f .env ]; then
  echo "Error: .env not found — skopiuj .env.example i uzupełnij klucze API"
  exit 1
fi

set -a
# shellcheck source=.env
source .env
set +a

echo "Starting PulseDigest..."
./gradlew bootRun
