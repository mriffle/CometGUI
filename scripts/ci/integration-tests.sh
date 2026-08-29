#!/usr/bin/env bash
#
# integration-tests.sh -- the specification's "small real-tool integration
# tests on Linux" item of the pull-request pipeline.
#
# THERE ARE NONE YET, AND THIS SCRIPT SAYS SO RATHER THAN PRETENDING.
#
# Real-tool integration tests execute real pinned Comet, Percolator, converter
# and PDV binaries on small real fixtures.  None of that exists at PHASE-01:
# the tool registry and installer are PHASE-05, the workflow engine is
# PHASE-08, and which fixture data may be used is D-006, still open.  A step
# that quietly exits 0 today would be indistinguishable from one that will
# quietly exit 0 after those tests exist but stop being run, so this script
# does not merely print a note -- it asserts the fact it is reporting:
#
#   * it looks for integration-test sources (*IT.java, *ITCase.java,
#     Integration*Test.java) under every module's test source root;
#   * if it finds NONE, it says so loudly, names the owning phases, and exits
#     0 -- there is genuinely nothing to run, and failing the pull-request
#     pipeline of every phase from here to PHASE-08 would only teach people to
#     ignore it;
#   * if it finds ANY, it exits non-zero, because integration tests now exist
#     and this script is a placeholder that would not run them.  That is the
#     falsifiable half: the day a later phase adds the first one, this step
#     fails and forces someone to wire maven-failsafe-plugin (pinned in
#     pom.xml, with no execution bound yet) and rewrite this script.
#
# Usage:
#   bash scripts/ci/integration-tests.sh
#   bash scripts/ci/integration-tests.sh --self-test   # prove it can fail
#
# --self-test copies a module's test tree under _build/, plants one
# `SomethingIT.java` in the copy, and requires the detection to fire.  The
# working tree is never touched.
#
# Exit status:
#   0  no integration-test sources exist yet -- reported, not assumed
#   1  integration-test sources exist but nothing here runs them
#   2  harness misuse or a broken environment
#   4  --self-test only: the detection did not fire on a planted test

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

die() { printf 'integration-tests.sh: %s\n' "$1" >&2; exit "${2:-2}"; }
usage() { sed -n '3,45p' -- "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

SELF_TEST=0
case "${1:-}" in
    "")          ;;
    --self-test) SELF_TEST=1 ;;
    -h|--help)   usage; exit 0 ;;
    *)           die "unknown argument: $1 (try --help)" ;;
esac
[ "$#" -le 1 ] || die "too many arguments (try --help)"

# find_integration_sources ROOT -- prints every path that looks like an
# integration test under ROOT's module test source roots.
#
# The search is anchored at <root>/cometgui-*/src/test/java rather than run
# over the whole tree.  _build/ holds throwaway copies of this repository made
# by the falsifiability harnesses, and a whole-tree search would count their
# files as if they were product sources.
find_integration_sources() {
    local root="$1"
    local -a src_roots=()
    mapfile -t src_roots < <(find "${root}" -maxdepth 4 -type d \
        -path "${root}/cometgui-*/src/test/java" | sort)
    [ "${#src_roots[@]}" -gt 0 ] || return 0
    find "${src_roots[@]}" -type f \
         \( -name '*IT.java' -o -name '*ITCase.java' -o -name 'Integration*Test.java' \) \
         -print 2>/dev/null | sort
}

printf '=== Real-tool integration tests (Linux) ===\n'

# The module test source roots that exist right now, so the search is over a
# real denominator rather than an empty tree that would trivially "pass".
roots=$(find "${PROJECT_ROOT}" -maxdepth 4 -type d -path "${PROJECT_ROOT}/cometgui-*/src/test/java" | sort | wc -l)
printf 'integration-tests.sh: %s module test source root(s) searched\n' "${roots}"
[ "${roots}" -gt 0 ] || die "no module test source roots found at all; refusing to report an answer over an empty tree" 2

declare -a FOUND=()
mapfile -t FOUND < <(find_integration_sources "${PROJECT_ROOT}")

if [ "${#FOUND[@]}" -gt 0 ]; then
    printf '\nintegration-tests.sh: FAILED -- %d integration-test source(s) exist:\n' "${#FOUND[@]}" >&2
    printf '    %s\n' "${FOUND[@]}" >&2
    cat >&2 <<'MSG'

This script was written when there were none, and it does not run them.  Wire
maven-failsafe-plugin (its version is already pinned in pom.xml; no execution
is bound) and replace this script with one that runs the suite and verifies
its report, exactly as scripts/build.sh verifies the Surefire reports.
MSG
    exit 1
fi

cat <<'MSG'

  NO REAL-TOOL INTEGRATION TESTS EXIST YET.

  The specification's pull-request pipeline requires "small real-tool
  integration tests on Linux".  They need things PHASE-01 does not have:

    PHASE-05  the tool registry and installer -- there is no pinned Comet,
              Percolator, converter or PDV binary to execute
    PHASE-08  the workflow engine that would drive one
    D-006     whose spectra and FASTA may be used as a fixture (OPEN)

  This step therefore reports honestly that it ran nothing, and asserts that
  fact: it searched every module test source root and found zero files named
  *IT.java, *ITCase.java or Integration*Test.java.  The moment one appears,
  this step fails until someone wires failsafe and rewrites it.

MSG

if [ "${SELF_TEST}" -eq 1 ]; then
    NEG="${PROJECT_ROOT}/_build/integration-selftest"
    printf '=== Self-test: the detection must fire on a planted integration test ===\n'
    printf 'integration-tests.sh: the working tree is not touched; everything happens under %s\n' "${NEG}"
    rm -rf -- "${NEG}"
    mkdir -p -- "${NEG}/cometgui-domain/src/test/java/org/cometgui/domain"
    cat > "${NEG}/cometgui-domain/src/test/java/org/cometgui/domain/CometRealToolIT.java" <<'JAVA'
// Planted by scripts/ci/integration-tests.sh --self-test. Never compiled.
class CometRealToolIT {}
JAVA
    printf 'integration-tests.sh: planted %s\n' "${NEG}/cometgui-domain/src/test/java/org/cometgui/domain/CometRealToolIT.java"
    hits=$(find_integration_sources "${NEG}" | wc -l)
    if [ "${hits}" -ne 1 ]; then
        printf 'integration-tests.sh: SELF-TEST FAILED -- the detection found %s file(s), expected 1.\n' "${hits}" >&2
        printf 'integration-tests.sh: this step cannot tell that integration tests have appeared.\n' >&2
        exit 4
    fi
    printf 'integration-tests.sh: detection fired (1 file found), and finds 0 once the plant is removed:\n'
    rm -rf -- "${NEG}"
    printf '    %s\n' "$(find_integration_sources "${PROJECT_ROOT}" | wc -l) file(s) in the real tree"
    printf 'integration-tests.sh: self-test OK.\n\n'
fi

printf 'integration-tests.sh: PASSED (nothing to run, and that is asserted rather than assumed).\n'
exit 0
