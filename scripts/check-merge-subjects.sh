#!/usr/bin/env bash
# C5 — a merge commit must not repeat the subject of the branch it merges.
#
# The convention was written down in CLAUDE.md on 2026-10-19 with the evidence, and writing it down
# is exactly as effective as the author's memory on the day of the merge. `git log --oneline` had
# four consecutive pairs of identical subjects, each one change counted twice, because a local merge
# takes the branch's subject by default and nothing objected. So this objects.
#
#   scripts/check-merge-subjects.sh                 check from the baseline below to HEAD
#   scripts/check-merge-subjects.sh <range>         check an explicit range, e.g. origin/main..HEAD
#
# Exit 0 clean, 1 with findings, 2 on a usage or repository error.
#
# Why a baseline and not all of history: the duplicates that prompted the convention are already
# committed, and rewriting published history to satisfy a style check would be a far worse trade than
# the noise it removes. The convention starts here and the baseline says where "here" is.
set -euo pipefail

# The newest commit that predates the convention. Everything after it is held to it.
BASELINE="9e5ba4f"

usage() {
    echo "usage: $(basename "$0") [<git range>]" >&2
    exit 2
}

[ "$#" -le 1 ] || usage
case "${1:-}" in -h|--help) usage ;; esac

git rev-parse --git-dir >/dev/null 2>&1 || {
    echo "not a git repository" >&2
    exit 2
}

if [ "$#" -eq 1 ]; then
    RANGE="$1"
elif git cat-file -e "${BASELINE}^{commit}" 2>/dev/null; then
    RANGE="${BASELINE}..HEAD"
else
    # A shallow clone (CI's default) may not contain the baseline at all. Checking nothing silently
    # would be the failure mode this script exists to prevent, so say so and pass: a missing baseline
    # is a clone problem, not a history problem.
    echo "note: baseline ${BASELINE} is not in this clone (shallow?); nothing to check"
    exit 0
fi

findings=0
while read -r sha parents; do
    # Two or more parents is a merge; one is an ordinary commit and cannot duplicate anything.
    set -- $parents
    [ "$#" -ge 2 ] || continue
    merge_subject=$(git log -1 --format=%s "$sha")
    for parent in "$@"; do
        parent_subject=$(git log -1 --format=%s "$parent")
        if [ "$merge_subject" = "$parent_subject" ]; then
            short_merge=$(git rev-parse --short "$sha")
            short_parent=$(git rev-parse --short "$parent")
            echo "DUPLICATE ${short_merge} merge repeats ${short_parent}: ${merge_subject}"
            findings=$((findings + 1))
            break
        fi
    done
done < <(git log --format='%H %P' "$RANGE")

if [ "$findings" -gt 0 ]; then
    cat >&2 <<'EOF'

A merge commit carries the same subject as one of its parents, so one change occupies two lines of
`git log --oneline` and the second says nothing the first did not. Pick one (CLAUDE.md § Merge
subjects):
  - squash merge, so the branch becomes one commit;
  - give the merge its own subject, e.g. "merge: feedback loop (#24)";
  - fast-forward, when the branch is a single commit and there is nothing to duplicate.
EOF
    exit 1
fi

echo "merge subjects OK (${RANGE})"
