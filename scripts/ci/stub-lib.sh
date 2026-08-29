#!/usr/bin/env bash
#
# stub-lib.sh -- the one implementation of "this CI step is not built yet".
#
# Phase 01 installs the nightly and release pipelines before the work they
# drive exists. The phase document permits that ("nightly and release may be
# stubs that fail loudly rather than silently passing") and this file is what
# makes "loudly" mean something specific:
#
#   * a stub NEVER exits 0.  A step that exits 0 having done nothing is exactly
#     the failure mode this project's gate conventions exist to prevent;
#   * a stub names the phase that owns the real implementation, so a reader of
#     a red pipeline knows whether it is broken or merely unbuilt;
#   * every stub uses this one file, so `scripts/ci/check-workflows.py` can
#     tell a stub from a real step by looking for the reference to it, and
#     `scripts/ci/run-pipeline-locally.sh` can require exactly one of the two
#     outcomes from every step it runs.
#
# Not usable directly: it is exec'd by a `scripts/ci/<name>.sh` wrapper.
#
#   exec bash "${SCRIPT_DIR}/stub-lib.sh" \
#       --script  nightly-determinism.sh \
#       --phase   PHASE-12 \
#       --owns    "determinism comparisons across repeated runs" \
#       --spec    "specification.rst, Nightly pipeline"
#
# Exit status:
#   70  always -- "not implemented; the named phase owns it".  Chosen so it can
#       never be confused with 0 (pass), 1 (a real gate failed) or 2 (misuse).
#   2   this file was called wrongly.

set -Eeuo pipefail

readonly STUB_EXIT=70

script=""; phase=""; owns=""; spec=""
while [ "$#" -gt 0 ]; do
    case "$1" in
        --script) script="${2:?--script needs a value}"; shift 2 ;;
        --phase)  phase="${2:?--phase needs a value}";  shift 2 ;;
        --owns)   owns="${2:?--owns needs a value}";    shift 2 ;;
        --spec)   spec="${2:?--spec needs a value}";    shift 2 ;;
        *) printf 'stub-lib.sh: unknown argument: %s\n' "$1" >&2; exit 2 ;;
    esac
done

for v in script phase owns spec; do
    [ -n "${!v}" ] || { printf 'stub-lib.sh: --%s is required\n' "${v}" >&2; exit 2; }
done

case "${phase}" in
    PHASE-[0-9][0-9]) ;;
    *) printf 'stub-lib.sh: --phase must look like PHASE-nn, got %s\n' "${phase}" >&2; exit 2 ;;
esac

printf '\n'
printf '===============================================================================\n'
printf ' NOT IMPLEMENTED -- %s\n' "${script}"
printf '===============================================================================\n'
printf ' This CI step is a deliberate stub installed by PHASE-01.\n'
printf '\n'
printf '   step        %s\n' "${script}"
printf '   owned by    %s\n' "${phase}"
printf '   must do     %s\n' "${owns}"
printf '   required by %s\n' "${spec}"
printf '\n'
printf ' It exits %d rather than 0 on purpose. A pipeline step that exits 0 having\n' "${STUB_EXIT}"
printf ' done nothing is indistinguishable from one that passed, and this project\n'
printf ' does not have that failure mode. %s replaces this file with the\n' "${phase}"
printf ' real check; nobody weakens it to make a pipeline green.\n'
printf '===============================================================================\n'
printf '\n'

exit "${STUB_EXIT}"
