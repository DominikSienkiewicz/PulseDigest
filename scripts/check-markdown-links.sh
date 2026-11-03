#!/usr/bin/env bash
# A relative Markdown link must point at something the repository actually ships.
#
# README is the router into /docs/, so a link that leads nowhere is a public defect, and no Java
# test can see it. The check existed before this script, inline in .github/workflows/digest.yml,
# and had two flaws that cost one red build each:
#
#   - it ran only in CI, so a dead link was always discovered one push too late;
#   - it resolved targets against the filesystem, so anything present locally but ignored by git
#     passed on the author's machine and failed in the checkout. `docs/concepts/` is local by
#     design (.gitignore), and on 2026-10-19 `docs/the-build.md` linked straight to it — green
#     here, dead there.
#
# So this resolves against the files git tracks rather than against the disk, and runs locally.
# A target that exists here but is untracked is reported dead, because that is what a checkout
# sees. The disk is not the deliverable.
#
#   scripts/check-markdown-links.sh
#
# Exit 0 clean, 1 with findings, 2 on a usage or repository error.
#
# Scope, deliberately narrow: relative targets of inline links in tracked `*.md` files. External
# URLs would need the network and would go red on someone else's outage; a bare `#fragment` is not
# a path. Reference-style definitions, link titles and percent-encoding are not parsed — the inline
# form is the only one this repository uses, and parsing shapes nobody writes buys false positives.
set -euo pipefail

usage() {
    echo "usage: $(basename "$0")" >&2
    exit 2
}

[ "$#" -eq 0 ] || usage

git rev-parse --git-dir >/dev/null 2>&1 || {
    echo "not a git repository" >&2
    exit 2
}

cd "$(git rev-parse --show-toplevel)"

NL=$'\n'
# One newline-delimited snapshot of what the repository ships, so the lookups below are string
# matches rather than a process per link. core.quotePath=false because git octal-escapes
# non-ASCII paths by default, and this is a project that writes Polish: an escaped "ż" would
# never match the link that spells it, and the gate would go red at an innocent file.
TRACKED="${NL}$(git -c core.quotePath=false ls-files)${NL}"

is_tracked_file() {
    case "$TRACKED" in
        *"${NL}${1}${NL}"*) return 0 ;;
    esac
    return 1
}

is_tracked_directory() {
    case "$TRACKED" in
        *"${NL}${1}/"*) return 0 ;;
    esac
    return 1
}

# Lexical resolution, on purpose: consulting the disk is the exact mistake this script corrects.
# "docs/adr/../configuration.md" is "docs/configuration.md" whether or not anything is there.
# Exit 1 means the link climbs out of the repository root, which no checkout can satisfy.
resolve() {
    printf '%s\n' "$1" | awk -F/ '
        {
            depth = 0
            for (i = 1; i <= NF; i++) {
                if ($i == "" || $i == ".") continue
                if ($i == "..") {
                    if (depth == 0) exit 1
                    depth--
                    continue
                }
                part[++depth] = $i
            }
            out = ""
            for (i = 1; i <= depth; i++) out = out (i > 1 ? "/" : "") part[i]
            print out
        }'
}

findings=$(mktemp)
trap 'rm -f "$findings"' EXIT

while IFS= read -r file; do
    # Tracked but deleted in the working tree: nothing to read, and the deletion is the author's
    # business, not this gate's.
    [ -f "$file" ] || continue
    dir=$(dirname "$file")

    while IFS= read -r target; do
        case "$target" in http*|mailto:*|'') continue ;; esac

        if [ "$dir" = "." ]; then
            candidate="$target"
        else
            candidate="$dir/$target"
        fi

        if ! resolved=$(resolve "$candidate"); then
            echo "DEAD $file -> $target (climbs out of the repository root)" >>"$findings"
            continue
        fi

        # The repository root itself, from a link like "(./)".
        [ -n "$resolved" ] || continue

        if is_tracked_file "$resolved" || is_tracked_directory "$resolved"; then
            continue
        fi

        if [ -e "$resolved" ]; then
            echo "DEAD $file -> $target (present here but untracked: a checkout will not have it)" >>"$findings"
        else
            echo "DEAD $file -> $target" >>"$findings"
        fi
    done < <(grep -oE '\]\([^)#][^)]*\)' "$file" | sed 's/](\(.*\))/\1/' | sed 's/#.*//')
done < <(git -c core.quotePath=false ls-files '*.md')

if [ -s "$findings" ]; then
    cat "$findings"
    cat >&2 <<'EOF'

A relative link points at something no checkout contains. Either the target moved and the link did
not, or the target is untracked — `docs/concepts/` is local by design, so it can be named in prose
but never linked. `git add` the target, fix the path, or drop the link.
EOF
    exit 1
fi

echo "markdown links OK ($(git -c core.quotePath=false ls-files '*.md' | wc -l | tr -d ' ') files)"
