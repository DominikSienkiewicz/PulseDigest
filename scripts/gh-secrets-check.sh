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
# Auth: if the env file (default <repo>/.env) defines GH_TOKEN, the script uses it for every
# gh call — so it works regardless of your shell's gh auth, and overrides a too-narrow
# GH_TOKEN/GITHUB_TOKEN exported globally. Blank/absent → falls back to your `gh auth login`.
#
# Usage:   scripts/gh-secrets-check.sh [-R owner/repo] [-f .env]
# Exit:    0 = all required present · 1 = something missing · 2 = setup error
#
set -euo pipefail

REPO=""
ENV_FILE=""
while [ $# -gt 0 ]; do
  case "$1" in
    -R|--repo) REPO="${2:-}"; shift 2 ;;
    -f|--file) ENV_FILE="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown argument: $1 (try --help)" >&2; exit 2 ;;
  esac
done

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WF_DIR="$ROOT/.github/workflows"
[ -n "$ENV_FILE" ] || ENV_FILE="$ROOT/.env"

if [ -t 1 ]; then
  GREEN=$'\033[32m'; RED=$'\033[31m'; YELLOW=$'\033[33m'; BOLD=$'\033[1m'; DIM=$'\033[2m'; RESET=$'\033[0m'
else
  GREEN=""; RED=""; YELLOW=""; BOLD=""; DIM=""; RESET=""
fi

die() { echo "${RED}error:${RESET} $*" >&2; exit 2; }

# Read a single key's value from the env file (last assignment wins): everything after the
# first '=', minus one layer of surrounding quotes and a trailing CR. Inline comments are kept.
get_env_value() {
  local key="$1" file="$2" line val
  line="$(grep -E "^[[:space:]]*(export[[:space:]]+)?${key}=" "$file" | tail -n1 || true)"
  [ -z "$line" ] && return 1
  line="${line#"${line%%[![:space:]]*}"}"
  line="${line#export }"
  val="${line#*=}"
  val="${val%$'\r'}"
  case "$val" in
    \"*\") val="${val#\"}"; val="${val%\"}" ;;
    \'*\') val="${val#\'}"; val="${val%\'}" ;;
  esac
  printf '%s' "$val"
}

# Prefer a GH_TOKEN from the env file for every gh call (overrides a too-narrow token already
# exported in the shell). Exported only into this process + its children, never your shell.
if [ -f "$ENV_FILE" ]; then
  _ENV_GH_TOKEN="$(get_env_value GH_TOKEN "$ENV_FILE" 2>/dev/null || true)"
  [ -n "${_ENV_GH_TOKEN:-}" ] && export GH_TOKEN="$_ENV_GH_TOKEN"
fi

command -v gh >/dev/null 2>&1 || die "GitHub CLI (gh) not found — install from https://cli.github.com"
gh auth status >/dev/null 2>&1 || die "gh is not authenticated — run: gh auth login (or set GH_TOKEN in $ENV_FILE)"
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

# Exact-line membership test, pure bash (no pipe, so no pipefail/SIGPIPE fragility in the hot loop).
# $1 = newline-separated haystack · $2 = needle (names are [A-Z0-9_], safe as a case pattern).
contains_line() {
  case $'\n'"$1"$'\n' in
    *$'\n'"$2"$'\n'*) return 0 ;;
    *) return 1 ;;
  esac
}

# List names for a store, distinguishing a real gh failure (no admin access, repo not
# found, …) from a genuinely empty store. A failure aborts loudly instead of silently
# reporting everything as "missing" — otherwise a permissions error looks identical to
# "nothing is set". Result is left in FETCH_OUT.
FETCH_OUT=""
fetch_names() {
  local noun="$1" raw rc
  raw="$(gh "$noun" list "${REPO_FLAG[@]+"${REPO_FLAG[@]}"}" 2>&1)" && rc=0 || rc=$?
  if [ "$rc" -ne 0 ]; then
    echo "${RED}error:${RESET} could not list ${noun}s on ${REPO_LABEL} — gh said:" >&2
    printf '  %s\n' "$raw" >&2
    if [ -n "${GH_TOKEN:-}${GITHUB_TOKEN:-}" ]; then
      echo "  ${YELLOW}GH_TOKEN/GITHUB_TOKEN is set${RESET} — it overrides your 'gh auth login' token." >&2
      echo "  ${DIM}A fine-grained PAT without 'Secrets: Read' causes exactly this 403. Either:${RESET}" >&2
      echo "    ${DIM}• run with your keyring token:${RESET}  GH_TOKEN= GITHUB_TOKEN= ${0##*/}" >&2
      echo "    ${DIM}• or grant that PAT 'Secrets: Read' (Variables too, for vars).${RESET}" >&2
    else
      echo "  ${DIM}Listing ${noun}s needs repo admin / 'Secrets: Read'. Wrong repo? Target another with -R owner/repo.${RESET}" >&2
    fi
    exit 2
  fi
  FETCH_OUT="$(printf '%s\n' "$raw" | awk 'NF{print $1}')"
}

# Pre-fetch both stores and both required lists once, so each section can tell "missing"
# apart from "set in the wrong bucket" (the workflow reads it as the other kind → runtime miss).
fetch_names secret;   HAVE_SECRET="$FETCH_OUT"
fetch_names variable; HAVE_VARIABLE="$FETCH_OUT"
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
      if contains_line "$have" "$name"; then
        echo "  ${GREEN}✓${RESET} ${name}"
        present=$((present + 1))
      elif contains_line "$other" "$name"; then
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
      if ! contains_line "$REQ_ALL" "$name"; then
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

TOTAL_REQUIRED="$(printf '%s\n' "$REQ_ALL" | grep -c .)"
if [ "$GLOBAL_MISSING" -gt 0 ]; then
  echo "${RED}${GLOBAL_MISSING} required item(s) missing.${RESET}"
  if [ "$GLOBAL_MISSING" -eq "$TOTAL_REQUIRED" ]; then
    echo "${YELLOW}Nothing is set on ${REPO_LABEL}.${RESET} Secrets/variables are per-repo —"
    echo "  is this the repo your digest workflow actually runs on? If it lives on another"
    echo "  repo (e.g. an older one), point the script there:"
    echo "    ${BOLD}scripts/gh-secrets-check.sh -R owner/other-repo${RESET}"
  fi
  echo "Set them from .env with:  ${BOLD}scripts/gh-secrets-sync.sh${RESET}"
  echo "or one-by-one with:       gh secret set <NAME>   /   gh variable set <NAME>"
  exit 1
fi
echo "${GREEN}All required secrets and variables are set.${RESET}"
exit 0
