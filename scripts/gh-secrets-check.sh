#!/usr/bin/env bash
#
# gh-secrets-check.sh — report which GitHub Actions secrets/variables this project
# needs and which are actually set on the repo.
#
# The "needed" list is derived live from .github/workflows/*.yml (every secrets.* and
# vars.* reference), so it never drifts from the workflow. GITHUB_TOKEN is excluded
# (GitHub injects it automatically).
#
# Read-only: only runs `gh secret list` / `gh variable list`. It never sets anything.
#
# Usage:   scripts/gh-secrets-check.sh [-R owner/repo]
# Exit:    0 = all required present · 1 = something missing · 2 = setup error
#
set -euo pipefail

REPO=""
while [ $# -gt 0 ]; do
  case "$1" in
    -R|--repo) REPO="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown argument: $1 (try --help)" >&2; exit 2 ;;
  esac
done

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WF_DIR="$ROOT/.github/workflows"

if [ -t 1 ]; then
  GREEN=$'\033[32m'; RED=$'\033[31m'; YELLOW=$'\033[33m'; BOLD=$'\033[1m'; DIM=$'\033[2m'; RESET=$'\033[0m'
else
  GREEN=""; RED=""; YELLOW=""; BOLD=""; DIM=""; RESET=""
fi

die() { echo "${RED}error:${RESET} $*" >&2; exit 2; }

command -v gh >/dev/null 2>&1 || die "GitHub CLI (gh) not found — install from https://cli.github.com"
gh auth status >/dev/null 2>&1 || die "gh is not authenticated — run: gh auth login"
[ -d "$WF_DIR" ] || die "no workflows directory at $WF_DIR"

REPO_FLAG=()
if [ -n "$REPO" ]; then
  REPO_FLAG=(--repo "$REPO")
  REPO_LABEL="$REPO"
else
  REPO_LABEL="$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null || echo '(from git remote)')"
fi

# Names referenced as <prefix>.NAME across all workflow files ("secrets" or "vars").
discover() {
  grep -rhoE "$1\.[A-Za-z0-9_]+" "$WF_DIR" 2>/dev/null \
    | sed "s/^$1\.//" | grep -vx 'GITHUB_TOKEN' | sort -u
}

list_names() { gh "$1" list "${REPO_FLAG[@]+"${REPO_FLAG[@]}"}" 2>/dev/null | awk 'NF{print $1}' || true; }

# Pre-fetch both stores and both required lists once, so each section can tell "missing"
# apart from "set in the wrong bucket" (the workflow reads it as the other kind → runtime miss).
HAVE_SECRET="$(list_names secret)"
HAVE_VARIABLE="$(list_names variable)"
REQ_SECRET="$(discover secrets)"
REQ_VARIABLE="$(discover vars)"
REQ_ALL="$(printf '%s\n%s\n' "$REQ_SECRET" "$REQ_VARIABLE" | grep -v '^$' | sort -u)"

GLOBAL_MISSING=0

check_kind() {
  # $1 = label · $2 = gh noun (secret|variable)
  local label="$1" noun="$2"
  local required have other other_label name present=0 total=0 missing=0
  if [ "$noun" = "secret" ]; then
    required="$REQ_SECRET"; have="$HAVE_SECRET"; other="$HAVE_VARIABLE"; other_label="variable"
  else
    required="$REQ_VARIABLE"; have="$HAVE_VARIABLE"; other="$HAVE_SECRET"; other_label="secret"
  fi

  echo "${BOLD}${label}${RESET} ${DIM}(required by workflows)${RESET}"
  if [ -z "$required" ]; then
    echo "  ${DIM}— none referenced —${RESET}"
  else
    while IFS= read -r name; do
      [ -z "$name" ] && continue
      total=$((total + 1))
      if printf '%s\n' "$have" | grep -qx "$name"; then
        echo "  ${GREEN}✓${RESET} ${name}"
        present=$((present + 1))
      elif printf '%s\n' "$other" | grep -qx "$name"; then
        echo "  ${RED}✗${RESET} ${name} ${YELLOW}(set as a ${other_label}, but the workflow reads it as a ${noun} — it won't be picked up)${RESET}"
        missing=$((missing + 1))
      else
        echo "  ${RED}✗${RESET} ${name} ${DIM}(missing)${RESET}"
        missing=$((missing + 1))
      fi
    done <<EOF
$required
EOF
  fi

  # Set in this bucket but referenced by no workflow (in either kind) — informational only.
  if [ -n "$have" ]; then
    while IFS= read -r name; do
      [ -z "$name" ] && continue
      if ! printf '%s\n' "$REQ_ALL" | grep -qx "$name"; then
        echo "  ${YELLOW}•${RESET} ${name} ${DIM}(set on repo, not used by any workflow)${RESET}"
      fi
    done <<EOF
$have
EOF
  fi

  echo "  ${DIM}${present}/${total} required ${label} set${RESET}"
  echo
  GLOBAL_MISSING=$((GLOBAL_MISSING + missing))
}

echo "${BOLD}PulseDigest — GitHub Actions secrets/variables${RESET}"
echo "Repo: ${REPO_LABEL}"
echo
check_kind "Secrets"   secret
check_kind "Variables" variable

if [ "$GLOBAL_MISSING" -gt 0 ]; then
  echo "${RED}${GLOBAL_MISSING} required item(s) missing.${RESET}"
  echo "Set them from .env with:  ${BOLD}scripts/gh-secrets-sync.sh${RESET}"
  echo "or one-by-one with:       gh secret set <NAME>"
  exit 1
fi
echo "${GREEN}All required secrets and variables are set.${RESET}"
exit 0
