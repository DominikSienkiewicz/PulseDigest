#!/usr/bin/env bash
#
# gh-secrets-sync.sh — push GitHub Actions secrets/variables from a local .env file.
#
# Only keys the workflows actually reference are pushed: a key referenced as secrets.*
# becomes a `gh secret set`, one referenced as vars.* becomes a `gh variable set`.
# Other .env keys are ignored. The required list is derived live from
# .github/workflows/*.yml, so it stays in sync with the workflow.
#
# Values are piped to gh over stdin, never passed as arguments (so they never appear
# in `ps`/shell history). By default the plan is printed and confirmed before applying.
#
# Usage:   scripts/gh-secrets-sync.sh [-f .env] [-R owner/repo] [--dry-run] [-y]
#   -f, --file     env file to read (default: <repo>/.env)
#   -R, --repo     target repo owner/name (default: gh auto-detect from git remote)
#       --dry-run  show what would be set, change nothing
#   -y, --yes      apply without the confirmation prompt
#
# Exit: 0 = applied/clean · 1 = one or more set calls failed · 2 = setup error
#
set -euo pipefail

ENV_FILE=""
REPO=""
DRY_RUN=0
ASSUME_YES=0
while [ $# -gt 0 ]; do
  case "$1" in
    -f|--file) ENV_FILE="${2:-}"; shift 2 ;;
    -R|--repo) REPO="${2:-}"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    -y|--yes) ASSUME_YES=1; shift ;;
    -h|--help) sed -n '2,21p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
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

command -v gh >/dev/null 2>&1 || die "GitHub CLI (gh) not found — install from https://cli.github.com"
gh auth status >/dev/null 2>&1 || die "gh is not authenticated — run: gh auth login"
[ -d "$WF_DIR" ] || die "no workflows directory at $WF_DIR"
[ -f "$ENV_FILE" ] || die "env file not found: $ENV_FILE (copy .env.example to .env first)"

REPO_FLAG=()
if [ -n "$REPO" ]; then
  REPO_FLAG=(--repo "$REPO")
  REPO_LABEL="$REPO"
else
  REPO_LABEL="$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null || echo '(from git remote)')"
fi

discover() {
  grep -rhoE "$1\.[A-Za-z0-9_]+" "$WF_DIR" 2>/dev/null \
    | sed "s/^$1\.//" | grep -vx 'GITHUB_TOKEN' | sort -u
}

# Read a single key's value from the env file (last assignment wins). Returns the raw
# value after the first '=', minus one layer of surrounding quotes and a trailing CR.
# Inline comments are NOT stripped (values are taken verbatim) to avoid corrupting URLs.
get_env_value() {
  local key="$1" file="$2" line val
  line="$(grep -E "^[[:space:]]*(export[[:space:]]+)?${key}=" "$file" | tail -n1 || true)"
  [ -z "$line" ] && return 1
  line="${line#"${line%%[![:space:]]*}"}"   # strip leading whitespace
  line="${line#export }"
  val="${line#*=}"
  val="${val%$'\r'}"                          # strip trailing CR (CRLF files)
  case "$val" in
    \"*\") val="${val#\"}"; val="${val%\"}" ;;
    \'*\') val="${val#\'}"; val="${val%\'}" ;;
  esac
  printf '%s' "$val"
}

# Build the plan into parallel indexed arrays (bash 3.2 has no associative arrays).
PLAN_NOUN=(); PLAN_NAME=(); PLAN_VALUE=(); PLAN_MASK=()
N=0
SKIPPED=""

stage_kind() {
  # $1 = gh noun (secret|variable) · $2 = workflow prefix (secrets|vars) · $3 = mask(1/0)
  local noun="$1" ref="$2" mask="$3" required name value
  required="$(discover "$ref")"
  [ -z "$required" ] && return 0
  while IFS= read -r name; do
    [ -z "$name" ] && continue
    if value="$(get_env_value "$name" "$ENV_FILE")" && [ -n "$value" ]; then
      PLAN_NOUN[N]="$noun"; PLAN_NAME[N]="$name"; PLAN_VALUE[N]="$value"; PLAN_MASK[N]="$mask"
      N=$((N + 1))
    else
      SKIPPED="${SKIPPED}  ${YELLOW}skip${RESET} ${name} ${DIM}(absent or empty in $(basename "$ENV_FILE"))${RESET}
"
    fi
  done <<EOF
$required
EOF
}

stage_kind secret   secrets 1
stage_kind variable vars    0

# .env keys not referenced by any workflow — reported, never pushed.
required_all="$(printf '%s\n%s\n' "$(discover secrets)" "$(discover vars)" | sort -u)"
env_keys="$(grep -vE '^[[:space:]]*#|^[[:space:]]*$' "$ENV_FILE" \
  | sed -E 's/^[[:space:]]*(export[[:space:]]+)?//; s/=.*//; s/[[:space:]]//g' | sort -u)"
ignored="$(comm -23 <(printf '%s\n' "$env_keys") <(printf '%s\n' "$required_all") | grep -v '^$' || true)"

echo "${BOLD}PulseDigest — push secrets/variables from $(basename "$ENV_FILE")${RESET}"
echo "Repo: ${REPO_LABEL}   Source: ${ENV_FILE}"
[ "$DRY_RUN" = "1" ] && echo "${YELLOW}(dry-run — nothing will be changed)${RESET}"
echo
echo "${BOLD}Plan${RESET}"
if [ "$N" -eq 0 ]; then
  echo "  ${DIM}— nothing to push —${RESET}"
else
  i=0
  while [ "$i" -lt "$N" ]; do
    if [ "${PLAN_MASK[i]}" = "1" ]; then
      shown="•••• ${DIM}(${#PLAN_VALUE[i]} chars)${RESET}"
    else
      shown="${PLAN_VALUE[i]}"
    fi
    echo "  ${GREEN}set${RESET} ${PLAN_NOUN[i]} ${BOLD}${PLAN_NAME[i]}${RESET} = ${shown}"
    i=$((i + 1))
  done
fi
[ -n "$SKIPPED" ] && printf '%s' "$SKIPPED"
[ -n "$ignored" ] && echo "  ${DIM}ignored (not used by any workflow): $(printf '%s' "$ignored" | tr '\n' ' ')${RESET}"
echo

if [ "$DRY_RUN" = "1" ] || [ "$N" -eq 0 ]; then
  exit 0
fi

if [ "$ASSUME_YES" != "1" ]; then
  printf 'Apply %s change(s) to %s? [y/N] ' "$N" "$REPO_LABEL"
  read -r reply
  case "$reply" in
    y|Y|yes|YES) ;;
    *) echo "Aborted."; exit 0 ;;
  esac
fi

FAIL=0
i=0
while [ "$i" -lt "$N" ]; do
  if printf '%s' "${PLAN_VALUE[i]}" | gh "${PLAN_NOUN[i]}" set "${PLAN_NAME[i]}" "${REPO_FLAG[@]+"${REPO_FLAG[@]}"}" >/dev/null 2>&1; then
    echo "  ${GREEN}✓${RESET} ${PLAN_NOUN[i]} ${PLAN_NAME[i]}"
  else
    echo "  ${RED}✗ FAILED${RESET} ${PLAN_NOUN[i]} ${PLAN_NAME[i]}"
    FAIL=$((FAIL + 1))
  fi
  i=$((i + 1))
done
echo
if [ "$FAIL" -gt 0 ]; then
  echo "${RED}${FAIL} of ${N} failed.${RESET}"
  exit 1
fi
echo "${GREEN}Done — ${N} item(s) set on ${REPO_LABEL}.${RESET}"
exit 0
