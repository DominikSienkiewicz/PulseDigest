#!/usr/bin/env bash
set -euo pipefail

if ! [[ "$SCAN_HEAD" =~ ^[0-9a-f]{40}$ && "$SCAN_BASE" =~ ^[0-9a-f]{40}$ ]] ||
  ! git cat-file -e "${SCAN_HEAD}^{commit}" 2>/dev/null; then
  echo 'Secret scan could not resolve the event commits.' >&2
  exit 1
fi

# An unseen predecessor or a rewritten branch has no reliable lower bound.
revisions=$SCAN_HEAD
if [[ "$SCAN_BASE" != 0000000000000000000000000000000000000000 ]] &&
  git cat-file -e "${SCAN_BASE}^{commit}" 2>/dev/null; then
  if [[ "$GITHUB_EVENT_NAME" == pull_request ]] || git merge-base --is-ancestor "$SCAN_BASE" "$SCAN_HEAD"; then
    revisions="$SCAN_BASE..$SCAN_HEAD"
  fi
fi

status=0
gitleaks git --config .gitleaks.toml --redact=100 --exit-code=2 \
  --log-opts="--full-history --remerge-diff $revisions" . >/dev/null 2>&1 || status=$?
case "$status" in
  0) echo 'Secret scan passed.' ;;
  2) echo 'Secret scan found a potential secret. Inspect the commit range locally.' >&2 ;;
  *) echo "Secret scan could not complete (exit code $status)." >&2 ;;
esac
exit "$status"
