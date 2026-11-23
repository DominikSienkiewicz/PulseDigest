#!/usr/bin/env bash
# Exercise commit ranges against the real scanner with one synthetic, repository-specific rule.
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
scan="$root/scripts/scan-secrets.sh"
workflow="$root/.github/workflows/digest.yml"
fail() { echo "FAIL: $*" >&2; exit 1; }
if grep -Fq 'uses: gitleaks/gitleaks-action@' "$workflow"; then fail 'Secret scan still needs the organization license'; fi
grep -Fq 'sha256sum -c -' "$workflow" || fail 'missing CLI checksum check'
grep -Fq 'run: bash scripts/scan-secrets.sh' "$workflow" || fail 'the workflow does not run the tested scanner'
[[ -f "$scan" ]] || fail 'missing Git history scanner'

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
cd "$tmp"
git init -q -b main
git config core.hooksPath /dev/null
git config user.name Test
git config user.email test@example.invalid
cat > .gitleaks.toml <<'CONFIG'
title = "Synthetic acceptance rule"
[[rules]]
id = "synthetic-probe"
description = "Acceptance probe"
regex = '''SYNTHETIC[_]PROBE_[0-9]{8}'''
CONFIG
git add -- .gitleaks.toml && git commit -qm 'base'
base=$(git rev-parse HEAD)
marker=SYNTHETIC_PROBE_12345678
printf '%s\n' "$marker" > example
git add -- example && git commit -qm 'add example'
leak=$(git rev-parse HEAD)
git rm -q example && git commit -qm 'remove example'
removed=$(git rev-parse HEAD)
git commit --allow-empty -qm 'clean tip'
clean=$(git rev-parse HEAD)

check_scan() {
  local expected=$1 event=$2 lower=$3 upper=$4 status=0
  GITHUB_EVENT_NAME="$event" SCAN_BASE="$lower" SCAN_HEAD="$upper" bash "$scan" >"$tmp/output" 2>&1 || status=$?
  [[ "$status" == "$expected" ]] || fail "$event returned $status (expected $expected)"
  if grep -Fq -e "$marker" -e synthetic-probe -e example "$tmp/output"; then fail 'a finding reached the log'; fi
}

check_scan 2 pull_request "$base" "$removed"
check_scan 2 push "$base" "$removed"
check_scan 0 push "$removed" "$clean"
check_scan 2 push 0000000000000000000000000000000000000000 "$removed"
check_scan 2 push ffffffffffffffffffffffffffffffffffffffff "$removed"
git switch -q -c replaced "$leak"
git commit --allow-empty -qm 'replacement'
check_scan 2 push "$clean" "$(git rev-parse HEAD)"
check_scan 1 push "$base" invalid
echo 'Secret scan covers event ranges and replaced history without logging findings.'
