#!/usr/bin/env bash
#
# docs-build.sh -- the CometGUI documentation gate.
#
# All project documentation is reStructuredText and must build clean under
# `sphinx-build -n -W` (nitpicky, warnings treated as errors). That splits into
# two builds, because the project's documents live in two places:
#
#   Build 1 -- the published tree. Exactly
#       sphinx-build -n -W -b html docs docs/_build/html
#   because R-DOC-05 fixes that command line. Output is what Read the Docs
#   publishes.
#
#   Build 2 -- the project documents that are NOT part of the published tree:
#   README.rst, ONBOARDING.rst, STATUS.rst, DECISIONS.rst, specification.rst,
#   CONTRIBUTING.rst and everything under phases/ and handoffs/. The hard
#   documentation rule applies to them, but they must not appear in the user-
#   facing toctree, so they are built in a throwaway tree regenerated under
#   _build/docs-gate/ on every run -- never stale, never committed. Documents
#   are discovered, never listed, so a document added later is covered without
#   editing this script, and a document that does not exist yet (another agent
#   may be writing it right now) is not an error.
#
# Exit code 0 proves nothing: sphinx-build can exit 0 having written nothing
# useful, so both builds verify that the expected HTML exists and is non-empty.
#
# Usage:
#   scripts/ci/docs-build.sh              # the gate: both builds
#   scripts/ci/docs-build.sh --self-test  # the gate, then prove it can fail
#   scripts/ci/docs-build.sh --help
#
# --self-test additionally demonstrates PHASE-01 exit gate item 2, directly
# after build 1: it copies the published tree to _build/docs-gate/negative/,
# injects a broken cross-reference
# there, shows the build failing on it, removes the injection and shows the same
# build passing. The working tree is never touched. (Phase 01 unit 8 owns the
# aggregate falsifiability harness; this is deliberately self-contained.)
#
# Exit status:
#   0  every build passed and produced the HTML it should have
#   1  sphinx-build failed -- under -W a warning is an error
#   2  harness misuse or a broken environment (no virtualenv, no documents, ...)
#   3  sphinx-build exited 0 but produced no or empty HTML
#   4  --self-test only: the injected broken cross-reference did NOT fail the
#      build, i.e. the gate is not falsifiable and cannot be trusted
#
# Needs no network access once .venv exists.

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

VENV="${PROJECT_ROOT}/.venv"
SPHINX_BUILD="${VENV}/bin/sphinx-build"          # project-local, never a host binary

GATE_ROOT="${PROJECT_ROOT}/_build/docs-gate"
PUBLISHED_LOG="${GATE_ROOT}/published-build.log"
PROJECT_SRC="${GATE_ROOT}/project-src"
PROJECT_HTML="${GATE_ROOT}/project-html"
PROJECT_DOCTREES="${GATE_ROOT}/project-doctrees"
PROJECT_LOG="${GATE_ROOT}/project-build.log"
NEG_ROOT="${GATE_ROOT}/negative"

SELF_TEST=0

die() { printf 'docs-build.sh: %s\n' "$1" >&2; exit "${2:-2}"; }

usage() {
    sed -n '3,45p' -- "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --self-test) SELF_TEST=1; shift ;;
        -h|--help)   usage; exit 0 ;;
        *)           die "unknown argument: $1 (try --help)" ;;
    esac
done

[ -x "${SPHINX_BUILD}" ] || die "no sphinx-build at ${SPHINX_BUILD}.
Create the project virtualenv first (nothing may be installed on the host):
    python3 -m venv ${VENV}
    ${VENV}/bin/pip install -r ${PROJECT_ROOT}/docs/requirements.txt"

mkdir -p -- "${GATE_ROOT}"

# --- helpers ----------------------------------------------------------------

# run_sphinx LOGFILE ARGS...  -- runs sphinx-build, tees to LOGFILE, returns
# sphinx-build's own exit status (not tee's -- hence PIPESTATUS).
#
# Every call site must put this in a condition context (`if ! run_sphinx ...`
# or `run_sphinx ... || status=$?`), which is what keeps `set -e` from killing
# the script on an expected failure. Do not add `set +e` here: it would leak
# back into the caller.
run_sphinx() {
    local log="$1"; shift
    local status
    "${SPHINX_BUILD}" "$@" 2>&1 | tee -- "${log}"
    status="${PIPESTATUS[0]}"
    return "${status}"
}

# verify_html HTML_DIR REL_DOC...  -- each REL_DOC is a docname (no extension);
# fails if the corresponding .html is missing or empty.
verify_html() {
    local html_dir="$1"; shift
    local missing=0 doc page
    for doc in "$@"; do
        page="${html_dir}/${doc}.html"
        if [ ! -s "${page}" ]; then
            printf 'docs-build.sh: expected HTML missing or empty: %s\n' "${page}" >&2
            missing=1
        fi
    done
    return "${missing}"
}

# ---------------------------------------------------------------------------
# Build 1 -- the published documentation tree (R-DOC-05's exact command line)
# ---------------------------------------------------------------------------

[ -f "${PROJECT_ROOT}/docs/conf.py" ] || die "no ${PROJECT_ROOT}/docs/conf.py"

# Discovered, not listed: every .rst under docs/ except the build output.
declare -a PUBLISHED_DOCS=()
while IFS= read -r -d '' f; do
    rel="${f#"${PROJECT_ROOT}"/docs/}"
    PUBLISHED_DOCS+=("${rel%.rst}")
done < <(find "${PROJECT_ROOT}/docs" -type f -name '*.rst' -not -path "${PROJECT_ROOT}/docs/_build/*" -print0 | sort -z)

[ "${#PUBLISHED_DOCS[@]}" -gt 0 ] || die "no .rst files under docs/; refusing to report success over an empty tree"

printf '=== Build 1: published documentation tree ===\n'
printf 'docs-build.sh: %s source document(s) under docs/\n' "${#PUBLISHED_DOCS[@]}"

# Always a full build. Two reasons, both about the gate meaning something:
# an incremental build only re-reads and re-writes documents Sphinx thinks are
# stale, so a cross-reference that a *change elsewhere* has just invalidated can
# go unreported; and leftover HTML from an earlier run would satisfy the
# "the HTML exists" check below even if this run wrote nothing. R-DOC-05 fixes
# the command line, so the cache is cleared before it rather than by adding -E.
rm -rf -- "${PROJECT_ROOT}/docs/_build"

printf 'docs-build.sh: cleared docs/_build (full build, never incremental)\n'
printf 'docs-build.sh: running (from %s):\n' "${PROJECT_ROOT}"
printf '    %s -n -W -b html docs docs/_build/html\n\n' "${SPHINX_BUILD}"

cd -- "${PROJECT_ROOT}"
if ! run_sphinx "${PUBLISHED_LOG}" -n -W -b html docs docs/_build/html; then
    printf '\ndocs-build.sh: FAILED -- the published documentation build did not pass.\n' >&2
    printf 'docs-build.sh: under -W a warning is an error; full output at %s\n' "${PUBLISHED_LOG}" >&2
    exit 1
fi

if ! verify_html "${PROJECT_ROOT}/docs/_build/html" "${PUBLISHED_DOCS[@]}"; then
    printf 'docs-build.sh: FAILED -- sphinx-build exited 0 but the published HTML is missing.\n' >&2
    exit 3
fi

PUBLISHED_PAGES="$(find "${PROJECT_ROOT}/docs/_build/html" -type f -name '*.html' | wc -l | tr -d ' ')"
printf '\ndocs-build.sh: build 1 OK -- %s HTML page(s) in docs/_build/html (%s from source documents)\n' \
    "${PUBLISHED_PAGES}" "${#PUBLISHED_DOCS[@]}"

# ---------------------------------------------------------------------------
# --self-test -- prove the gate can fail (PHASE-01 exit gate item 2)
# ---------------------------------------------------------------------------

if [ "${SELF_TEST}" -eq 1 ]; then
    NEG_LABEL='docs-build-self-test-label-that-does-not-exist'
    NEG_SRC="${NEG_ROOT}/src"
    NEG_HTML="${NEG_ROOT}/html"
    NEG_DOCTREES="${NEG_ROOT}/doctrees"
    NEG_LOG_BROKEN="${NEG_ROOT}/broken.log"
    NEG_LOG_CLEAN="${NEG_ROOT}/clean.log"

    printf '\n=== Self-test: a deliberate broken cross-reference must fail the build ===\n'
    printf 'docs-build.sh: the working tree is not touched; everything happens under %s\n' "${NEG_ROOT}"

    rm -rf -- "${NEG_ROOT}"
    mkdir -p -- "${NEG_SRC}"
    # Copy the published tree, minus its build output.
    (cd -- "${PROJECT_ROOT}/docs" && find . -type f -not -path './_build/*' -print0 \
        | tar --null --files-from=- -cf -) | (cd -- "${NEG_SRC}" && tar -xf -)
    [ -f "${NEG_SRC}/conf.py" ] || die "self-test: copy of docs/ has no conf.py"

    printf '\n--- injecting into %s ---\n' "${NEG_SRC}/index.rst"
    {
        printf '\n'
        printf 'Deliberate defect injected by ``scripts/ci/docs-build.sh --self-test``:\n'
        printf ':ref:`%s`\n' "${NEG_LABEL}"
    } >> "${NEG_SRC}/index.rst"
    tail -n 3 -- "${NEG_SRC}/index.rst"
    printf -- '---\n\n'

    neg_status=0
    run_sphinx "${NEG_LOG_BROKEN}" -n -W -b html -d "${NEG_DOCTREES}" "${NEG_SRC}" "${NEG_HTML}" \
        || neg_status=$?

    if [ "${neg_status}" -eq 0 ]; then
        printf '\ndocs-build.sh: SELF-TEST FAILED -- the broken cross-reference did not fail the build.\n' >&2
        printf 'docs-build.sh: the documentation gate is not falsifiable and must not be trusted.\n' >&2
        exit 4
    fi
    if ! grep -q "undefined label: '${NEG_LABEL}'" -- "${NEG_LOG_BROKEN}"; then
        printf '\ndocs-build.sh: SELF-TEST FAILED -- the build failed, but not on the injected\n' >&2
        printf 'docs-build.sh: cross-reference. Something else is broken; see %s\n' "${NEG_LOG_BROKEN}" >&2
        exit 4
    fi
    printf '\ndocs-build.sh: build failed as required (exit %s). The error:\n' "${neg_status}"
    grep -n "undefined label: '${NEG_LABEL}'" -- "${NEG_LOG_BROKEN}" | sed 's/^/    /'

    # Remove the injection and rebuild the same tree.
    printf '\n--- removing the injection and rebuilding the same tree ---\n'
    cp -- "${PROJECT_ROOT}/docs/index.rst" "${NEG_SRC}/index.rst"
    rm -rf -- "${NEG_DOCTREES}" "${NEG_HTML}"

    if ! run_sphinx "${NEG_LOG_CLEAN}" -n -W -b html -d "${NEG_DOCTREES}" "${NEG_SRC}" "${NEG_HTML}"; then
        printf '\ndocs-build.sh: SELF-TEST FAILED -- the tree does not build clean once the\n' >&2
        printf 'docs-build.sh: injected defect is removed; see %s\n' "${NEG_LOG_CLEAN}" >&2
        exit 1
    fi
    if [ ! -s "${NEG_HTML}/index.html" ]; then
        printf '\ndocs-build.sh: SELF-TEST FAILED -- clean rebuild exited 0 but wrote no index.html.\n' >&2
        exit 3
    fi
    printf '\ndocs-build.sh: self-test OK -- fails on the broken cross-reference, passes without it.\n'
fi

# ---------------------------------------------------------------------------
# Build 2 -- project documents outside the published tree
# ---------------------------------------------------------------------------

# Discovery. Root-level .rst files (depth 1 only), plus everything under
# phases/ and handoffs/. Nothing here is hard-coded: a new root document, a new
# phase or a new handoff is picked up on the next run, and a document that does
# not exist yet simply is not found.
declare -a PROJECT_RELS=()
while IFS= read -r -d '' f; do
    PROJECT_RELS+=("${f#"${PROJECT_ROOT}"/}")
done < <(
    {
        find "${PROJECT_ROOT}" -maxdepth 1 -type f -name '*.rst' -print0
        for d in phases handoffs; do
            [ -d "${PROJECT_ROOT}/${d}" ] && find "${PROJECT_ROOT}/${d}" -type f -name '*.rst' -print0
        done
        true
    } | sort -z
)

[ "${#PROJECT_RELS[@]}" -gt 0 ] || die "no project .rst files found outside docs/; refusing to report success over an empty tree"

# Reading order for the generated master document. This orders the toctree; it
# never decides membership -- every discovered document is included.
sort_key() {
    case "$1" in
        README.rst)        printf '10' ;;
        ONBOARDING.rst)    printf '11' ;;
        STATUS.rst)        printf '12' ;;
        DECISIONS.rst)     printf '13' ;;
        specification.rst) printf '14' ;;
        CONTRIBUTING.rst)  printf '15' ;;
        phases/index.rst)  printf '20' ;;
        phases/*)          printf '21' ;;
        handoffs/*)        printf '30' ;;
        *)                 printf '19' ;;
    esac
}

declare -a ORDERED=()
while IFS= read -r line; do
    ORDERED+=("${line#*$'\t'}")
done < <(
    for rel in "${PROJECT_RELS[@]}"; do
        printf '%s\t%s\n' "$(sort_key "${rel}")" "${rel}"
    done | LC_ALL=C sort -k1,1 -k2,2
)

rm -rf -- "${PROJECT_SRC}" "${PROJECT_HTML}" "${PROJECT_DOCTREES}"
mkdir -p -- "${PROJECT_SRC}/project"

for rel in "${ORDERED[@]}"; do
    dest="${PROJECT_SRC}/project/${rel}"
    mkdir -p -- "$(dirname -- "${dest}")"
    cp -- "${PROJECT_ROOT}/${rel}" "${dest}"
done

cat > "${PROJECT_SRC}/conf.py" <<'CONF_EOF'
# Generated by scripts/ci/docs-build.sh. Throwaway: rewritten on every run and
# never committed. It exists only so that the project documents that are not
# part of the published tree still get a strict -n -W build. The real
# configuration is docs/conf.py.
project = "CometGUI project documents"
author = "The CometGUI project"
extensions = []
root_doc = "index"
exclude_patterns = []
templates_path = []
html_static_path = []
html_theme = "alabaster"
# No suppress_warnings, no nitpick_ignore: -n -W must bite.
CONF_EOF

{
    printf '%s\n' \
        '=========================================' \
        'CometGUI project documents -- build check' \
        '========================================='
    printf '\n'
    printf 'Generated by ``scripts/ci/docs-build.sh`` -- throwaway. These documents are\n'
    printf 'project documentation and must build clean under ``-n -W``, but they are not\n'
    printf 'part of the published tree in ``docs/``.\n\n'
    printf '.. toctree::\n   :maxdepth: 1\n\n'
    for rel in "${ORDERED[@]}"; do
        printf '   project/%s\n' "${rel%.rst}"
    done
} > "${PROJECT_SRC}/index.rst"

printf '\n=== Build 2: project documents outside the published tree ===\n'
printf 'docs-build.sh: %s document(s) discovered:\n' "${#ORDERED[@]}"
printf '    %s\n' "${ORDERED[@]}"
printf 'docs-build.sh: throwaway source tree %s\n' "${PROJECT_SRC}"
printf 'docs-build.sh: running:\n'
printf '    %s -n -W -b html %s %s\n\n' "${SPHINX_BUILD}" "${PROJECT_SRC}" "${PROJECT_HTML}"

if ! run_sphinx "${PROJECT_LOG}" -n -W -b html -d "${PROJECT_DOCTREES}" "${PROJECT_SRC}" "${PROJECT_HTML}"; then
    printf '\ndocs-build.sh: FAILED -- a project document outside docs/ did not build clean.\n' >&2
    printf 'docs-build.sh: under -W a warning is an error; full output at %s\n' "${PROJECT_LOG}" >&2
    exit 1
fi

declare -a PROJECT_DOCNAMES=("index")
for rel in "${ORDERED[@]}"; do
    PROJECT_DOCNAMES+=("project/${rel%.rst}")
done

if ! verify_html "${PROJECT_HTML}" "${PROJECT_DOCNAMES[@]}"; then
    printf 'docs-build.sh: FAILED -- sphinx-build exited 0 but the project-document HTML is missing.\n' >&2
    exit 3
fi

PROJECT_PAGES="$(find "${PROJECT_HTML}" -type f -name '*.html' | wc -l | tr -d ' ')"
printf '\ndocs-build.sh: build 2 OK -- %s HTML page(s) in %s (%s from source documents)\n' \
    "${PROJECT_PAGES}" "${PROJECT_HTML}" "${#ORDERED[@]}"

printf '\ndocs-build.sh: PASSED.\n'
printf 'docs-build.sh: published HTML  %s\n' "${PROJECT_ROOT}/docs/_build/html/index.html"
printf 'docs-build.sh: project HTML    %s\n' "${PROJECT_HTML}/index.html"
printf 'docs-build.sh: logs            %s, %s\n' "${PUBLISHED_LOG}" "${PROJECT_LOG}"
exit 0
