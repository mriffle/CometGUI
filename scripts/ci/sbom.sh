#!/usr/bin/env bash
#
# sbom.sh -- generate the CycloneDX SBOM for the reactor and validate it.
#
# The specification requires an SBOM at release time (Supply-Chain and
# Application Security) and "SBOM generation validation" on every pull request
# (CI and Release Pipeline).  Generation is org.cyclonedx:cyclonedx-maven-plugin,
# pinned by the cyclonedx-maven-plugin.version property in pom.xml and read
# from there by this script, so the version lives in exactly one place.
#
# GENERATION IS NOT THE GATE.  The plugin exits 0 whether it wrote a document
# describing the whole reactor or one describing nothing: a wrong goal, a
# dropped test scope or a reactor that failed to resolve all produce a valid,
# well-formed, useless SBOM.  So this script:
#
#   1. removes any previous output, so a stale file cannot be mistaken for a
#      fresh one;
#   2. runs makeAggregateBom with includeTestScope=true -- JUnit and ArchUnit
#      are test-scope, and an SBOM without them omits most of what this
#      repository downloads;
#   3. checks the files it asked for actually appeared and are non-empty;
#   4. hands both documents to scripts/ci/sbom_verify.py, which cross-checks
#      them against the POMs and fails on an empty components array.
#
# Output: _build/sbom/cometgui-sbom.json and .xml (gitignored -- an SBOM is
# generated, never committed).
#
# Usage:
#   bash scripts/ci/sbom.sh
#   bash scripts/ci/sbom.sh --self-test   # the gate, then prove it can fail
#   bash scripts/ci/sbom.sh --help
#
# Needs network access the first time, to fetch the plugin into _build/m2repo.
#
# Exit status:
#   0  an SBOM was generated and it describes the project the POMs describe
#   1  the SBOM's content is wrong (see sbom_verify.py)
#   2  harness misuse or a broken environment
#   3  the generator exited 0 but wrote no, empty or unparseable output
#   4  --self-test only: a damaged SBOM was accepted

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

OUT_DIR="${PROJECT_ROOT}/_build/sbom"
BASENAME="cometgui-sbom"
JSON="${OUT_DIR}/${BASENAME}.json"
XML="${OUT_DIR}/${BASENAME}.xml"
LOG="${OUT_DIR}/generate.log"
M2REPO="${PROJECT_ROOT}/_build/m2repo"
SCHEMA_VERSION="1.6"

die() { printf 'sbom.sh: %s\n' "$1" >&2; exit "${2:-2}"; }
usage() { sed -n '3,40p' -- "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

SELF_TEST=0
case "${1:-}" in
    "")          ;;
    --self-test) SELF_TEST=1 ;;
    -h|--help)   usage; exit 0 ;;
    *)           die "unknown argument: $1 (try --help)" ;;
esac
[ "$#" -le 1 ] || die "too many arguments (try --help)"

PYTHON=""
for candidate in "${PROJECT_ROOT}/.venv/bin/python" "$(command -v python3 || true)"; do
    [ -n "${candidate}" ] && [ -x "${candidate}" ] && { PYTHON="${candidate}"; break; }
done
[ -n "${PYTHON}" ] || die "no Python 3 (tried .venv/bin/python and python3)"

# Do not litter the source tree with __pycache__ directories.
export PYTHONDONTWRITEBYTECODE=1

# The pinned toolchain, never a host JDK or a host Maven.
if ! command -v mvn >/dev/null 2>&1; then
    [ -f "${PROJECT_ROOT}/tools/env.sh" ] || die "no mvn and no tools/env.sh; run scripts/feasibility/install-toolchain.sh"
    # shellcheck disable=SC1091
    . "${PROJECT_ROOT}/tools/env.sh"
fi
command -v mvn >/dev/null 2>&1 || die "mvn is still not on PATH after sourcing tools/env.sh"

# The version pin, read from the one place it is written.
PLUGIN_VERSION="$(sed -n 's|.*<cyclonedx-maven-plugin\.version>\([^<]*\)</cyclonedx-maven-plugin\.version>.*|\1|p' \
    "${PROJECT_ROOT}/pom.xml" | head -1)"
[ -n "${PLUGIN_VERSION}" ] || die "pom.xml has no <cyclonedx-maven-plugin.version> property"
case "${PLUGIN_VERSION}" in
    *SNAPSHOT*|*LATEST*|*RELEASE*|*'['*|*']'*|*'('*|*')'*|*'${'*)
        die "cyclonedx-maven-plugin.version is '${PLUGIN_VERSION}', which is not an exact pin" ;;
esac

cd -- "${PROJECT_ROOT}"

printf '=== 1/3  generate ===\n'
printf 'sbom.sh: org.cyclonedx:cyclonedx-maven-plugin:%s (pinned in pom.xml)\n' "${PLUGIN_VERSION}"
rm -rf -- "${OUT_DIR}"
mkdir -p -- "${OUT_DIR}"

set -- \
    -B \
    -Dmaven.repo.local="${M2REPO}" \
    "org.cyclonedx:cyclonedx-maven-plugin:${PLUGIN_VERSION}:makeAggregateBom" \
    -DoutputDirectory="${OUT_DIR}" \
    -DoutputName="${BASENAME}" \
    -DoutputFormat=all \
    -DschemaVersion="${SCHEMA_VERSION}" \
    -DincludeTestScope=true
printf 'sbom.sh: + mvn %s\n\n' "$*"

status=0
mvn "$@" >"${LOG}" 2>&1 || status=$?
if [ "${status}" -ne 0 ]; then
    printf 'sbom.sh: the generator failed (exit %s). Tail of %s:\n' "${status}" "${LOG}" >&2
    tail -30 -- "${LOG}" >&2
    exit 1
fi
grep -E 'CycloneDX: (Creating|Writing)' -- "${LOG}" | sed 's/^/    /' || true

printf '\n=== 2/3  the generator claims success; check it wrote something ===\n'
for f in "${JSON}" "${XML}"; do
    [ -f "${f}" ] || die "the generator exited 0 but ${f} does not exist" 3
    [ -s "${f}" ] || die "the generator exited 0 but ${f} is empty" 3
    printf 'sbom.sh:   %s  %s bytes\n' "${f#"${PROJECT_ROOT}/"}" "$(wc -c <"${f}" | tr -d ' ')"
done

printf '\n=== 3/3  validate the CONTENT against the POMs ===\n'
"${PYTHON}" "${SCRIPT_DIR}/sbom_verify.py" --root "${PROJECT_ROOT}" --json "${JSON}" --xml "${XML}"

if [ "${SELF_TEST}" -eq 1 ]; then
    printf '\n=== 4/4  --self-test: damaged SBOMs must be rejected ===\n'
    "${PYTHON}" "${SCRIPT_DIR}/sbom_verify.py" --root "${PROJECT_ROOT}" --json "${JSON}" \
        --xml "${XML}" --self-test
fi

printf '\nsbom.sh: PASSED. SBOM at %s\n' "${JSON#"${PROJECT_ROOT}/"}"
exit 0
