#!/usr/bin/env bash
#
# verify-license.sh -- prove that LICENSE is still the full, unmodified GPL-3.0.
#
# `D-001` (DECIDED 2026-08-29) releases CometGUI under GPL-3.0 and obliges the
# repository root to carry the *full, unmodified* GNU General Public License
# version 3. A licence file is the kind of file nobody reads again after it
# lands, so a truncation, a stray editor reformat or a well-meant "trim the
# boilerplate" commit would go unnoticed. This script makes that a detectable
# event: it is re-runnable, needs no network, and checks the file three ways --
# by size, by digest, and by structure.
#
# Usage:
#   scripts/verify-license.sh              # check the repository's LICENSE
#   scripts/verify-license.sh FILE         # check some other copy (negative tests)
#   scripts/verify-license.sh --self-test  # prove this script fails when it should
#
# The second form exists so the script's own failure can be demonstrated
# against a deliberately damaged copy under _build/ -- never against the real
# file in the working tree. The third form automates exactly that: a gate that
# has never been seen to fail has not been shown to work, so this one carries
# its own negative controls.
#
# Exit status:
#   0  every check passed
#   1  at least one check failed -- the file is missing, truncated or altered
#   2  harness misuse (too many arguments, no sha256 tool)
#   3  --self-test only: a negative control did not behave as required, so this
#      script's failure detection is itself broken
#
# Provenance of the pinned values. On 2026-08-29 the identical 35 149-byte text
# was retrieved from two independent sources and compared byte for byte:
#   * https://www.gnu.org/licenses/gpl-3.0.txt                     (FSF)
#   * https://raw.githubusercontent.com/Noble-Lab/CasanovoGUI/main/LICENSE
# The GitHub blob sha below is the one `DECISIONS.rst` records for
# CasanovoGUI's LICENSE, so a passing run also proves CometGUI ships the same
# licence text as the upstream work it derives from.

set -uo pipefail

EXPECTED_SHA256="3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986"
EXPECTED_GIT_BLOB="f288702d2fa16d3cdf0035b15a9fcbc552cd88e7"
EXPECTED_BYTES=35149
EXPECTED_LINES=674

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

if [ "$#" -gt 1 ]; then
    printf 'verify-license.sh: usage: verify-license.sh [FILE | --self-test]\n' >&2
    exit 2
fi

# --- self-test ---------------------------------------------------------------
# Damages copies of the licence under _build/ (gitignored, never the real file)
# and requires this script to reject each one, with the right diagnostic and a
# non-zero exit. A control whose damage did not actually take is reported as a
# harness failure, not a pass.

self_test() {
    local real="${PROJECT_ROOT}/LICENSE"
    local dir="${PROJECT_ROOT}/_build/license-self-test"
    local rc=0

    if [ ! -f "${real}" ]; then
        printf 'self-test: cannot run: %s does not exist\n' "${real}" >&2
        return 3
    fi
    rm -rf -- "${dir}"
    mkdir -p -- "${dir}"

    # control <name> <damaged-file> <expected diagnostic substring>
    control() {
        local name="$1" file="$2" want="$3" out status
        printf '\n=== negative control: %s ===\n' "${name}"
        if [ -e "${file}" ] && cmp -s -- "${real}" "${file}"; then
            printf 'self-test: HARNESS FAILURE -- %s is byte-identical to the real LICENSE;\n' "${file}" >&2
            printf 'self-test: the damage was not injected, so this control proves nothing.\n' >&2
            rc=3
            return
        fi
        out="$(bash -- "${BASH_SOURCE[0]}" "${file}" 2>&1)"
        status=$?
        printf '%s\n' "${out}"
        if [ "${status}" -eq 0 ]; then
            printf 'self-test: FAILED -- %s was ACCEPTED (exit 0); the check does not bite.\n' "${name}" >&2
            rc=3
        elif ! printf '%s' "${out}" | grep -q -F -- "${want}"; then
            printf 'self-test: FAILED -- %s exited %s but never said: %s\n' "${name}" "${status}" "${want}" >&2
            rc=3
        else
            printf 'self-test: OK -- %s rejected (exit %s) with the expected diagnostic.\n' "${name}" "${status}"
        fi
    }

    head -n 200 -- "${real}" > "${dir}/LICENSE-truncated"
    control "truncated to 200 lines" "${dir}/LICENSE-truncated" "the file is TRUNCATED"

    sed '''s/^  16\. Limitation of Liability\.$/  16. Limitation of liability./''' -- "${real}" > "${dir}/LICENSE-altered"
    control "one character altered, same size" "${dir}/LICENSE-altered" "the text has been ALTERED"

    sed '''s/$/\r/''' -- "${real}" > "${dir}/LICENSE-crlf"
    control "converted to CRLF" "${dir}/LICENSE-crlf" "CRLF line endings"

    : > "${dir}/LICENSE-empty"
    control "empty file" "${dir}/LICENSE-empty" "file is empty"

    control "missing file" "${dir}/LICENSE-absent" "no such file"

    # Positive control last: the real file must still pass, or the negative
    # controls above prove only that the script rejects everything.
    printf '\n=== positive control: the repository LICENSE ===\n'
    if bash -- "${BASH_SOURCE[0]}" "${real}" > "${dir}/positive.log" 2>&1; then
        printf 'self-test: OK -- %s accepted (exit 0).\n' "${real}"
    else
        printf 'self-test: FAILED -- the real LICENSE was REJECTED. Output:\n' >&2
        cat -- "${dir}/positive.log" >&2
        rc=3
    fi

    printf '\n'
    if [ "${rc}" -ne 0 ]; then
        printf 'verify-license.sh: SELF-TEST FAILED -- this script cannot be trusted as a gate.\n' >&2
        return "${rc}"
    fi
    printf 'verify-license.sh: SELF-TEST PASSED -- 5 negative controls rejected, real LICENSE accepted.\n'
    printf 'verify-license.sh: damaged copies kept under %s\n' "${dir}"
    return 0
}

if [ "${1:-}" = "--self-test" ]; then
    self_test
    exit $?
fi

TARGET="${1:-${PROJECT_ROOT}/LICENSE}"

failures=0

pass() { printf 'verify-license.sh: PASS  %s\n' "$1"; }
fail() {
    printf 'verify-license.sh: FAIL  %s\n' "$1" >&2
    failures=$((failures + 1))
}

printf 'verify-license.sh: checking %s\n' "${TARGET}"

# --- 1. the file exists at all ----------------------------------------------

if [ ! -f "${TARGET}" ]; then
    printf 'verify-license.sh: FAIL  no such file: %s\n' "${TARGET}" >&2
    printf 'verify-license.sh: LICENSE is a D-001 obligation; the repository must not ship without it.\n' >&2
    printf 'verify-license.sh: FAILED -- 1 check failed.\n' >&2
    exit 1
fi
if [ ! -s "${TARGET}" ]; then
    printf 'verify-license.sh: FAIL  file is empty: %s\n' "${TARGET}" >&2
    printf 'verify-license.sh: FAILED -- 1 check failed.\n' >&2
    exit 1
fi
pass "file exists and is not empty"

# --- 2. size and shape -------------------------------------------------------
# Checked before the digest because a size mismatch says *how* the file is
# wrong (truncated, or grown) where a digest only says "different".

actual_bytes="$(wc -c < "${TARGET}" | tr -d ' ')"
if [ "${actual_bytes}" = "${EXPECTED_BYTES}" ]; then
    pass "byte count is ${EXPECTED_BYTES}"
else
    if [ "${actual_bytes}" -lt "${EXPECTED_BYTES}" ]; then
        fail "byte count is ${actual_bytes}, expected ${EXPECTED_BYTES} -- the file is TRUNCATED by $((EXPECTED_BYTES - actual_bytes)) bytes"
    else
        fail "byte count is ${actual_bytes}, expected ${EXPECTED_BYTES} -- the file has GROWN by $((actual_bytes - EXPECTED_BYTES)) bytes"
    fi
fi

actual_lines="$(wc -l < "${TARGET}" | tr -d ' ')"
if [ "${actual_lines}" = "${EXPECTED_LINES}" ]; then
    pass "line count is ${EXPECTED_LINES}"
else
    fail "line count is ${actual_lines}, expected ${EXPECTED_LINES}"
fi

# --- 3. exact content --------------------------------------------------------

if command -v sha256sum >/dev/null 2>&1; then
    actual_sha256="$(sha256sum -- "${TARGET}" | cut -d' ' -f1)"
elif command -v shasum >/dev/null 2>&1; then
    actual_sha256="$(shasum -a 256 -- "${TARGET}" | cut -d' ' -f1)"
else
    printf 'verify-license.sh: no sha256sum or shasum available; cannot verify the digest.\n' >&2
    printf 'verify-license.sh: refusing to report success without it.\n' >&2
    exit 2
fi
if [ "${actual_sha256}" = "${EXPECTED_SHA256}" ]; then
    pass "SHA-256 is ${EXPECTED_SHA256}"
else
    fail "SHA-256 is ${actual_sha256}, expected ${EXPECTED_SHA256} -- the text has been ALTERED"
fi

# Same file, second independent digest, and the one D-001 records for
# CasanovoGUI's blob. Skipped rather than failed when git is unavailable.
if command -v git >/dev/null 2>&1; then
    actual_blob="$(git hash-object -- "${TARGET}")"
    if [ "${actual_blob}" = "${EXPECTED_GIT_BLOB}" ]; then
        pass "git blob sha is ${EXPECTED_GIT_BLOB} (matches CasanovoGUI's LICENSE per D-001)"
    else
        fail "git blob sha is ${actual_blob}, expected ${EXPECTED_GIT_BLOB}"
    fi
else
    printf 'verify-license.sh: SKIP  git blob sha (no git on PATH)\n'
fi

# --- 4. structure ------------------------------------------------------------
# A digest catches everything, but only says "different". These checks say
# *what* is missing, which is what a person repairing the file needs. They also
# keep the script meaningful if the pinned digest ever has to be re-pinned.

check_line() {  # check_line <line-number> <exact-expected-text> <description>
    local n="$1" want="$2" desc="$3" got
    got="$(sed -n "${n}p" -- "${TARGET}")"
    if [ "${got}" = "${want}" ]; then
        pass "line ${n} is the ${desc}"
    else
        fail "line ${n} should be the ${desc} but reads: ${got:-<missing>}"
    fi
}

check_line 1 '                    GNU GENERAL PUBLIC LICENSE' 'GPL title'
check_line 2 '                       Version 3, 29 June 2007' 'version line'

check_landmark() {  # check_landmark <extended-regex> <description>
    local re="$1" desc="$2" n
    n="$(grep -c -E -- "${re}" "${TARGET}")"
    if [ "${n}" = "1" ]; then
        pass "${desc} present exactly once"
    else
        fail "${desc}: found ${n} occurrence(s), expected exactly 1"
    fi
}

check_landmark '^ +Preamble$'                                  'Preamble heading'
check_landmark '^ +TERMS AND CONDITIONS$'                      'TERMS AND CONDITIONS heading'
check_landmark '^ +END OF TERMS AND CONDITIONS$'               'END OF TERMS AND CONDITIONS heading'
check_landmark '^ +How to Apply These Terms to Your New Programs$' 'How to Apply These Terms heading'
check_landmark '^  15\. Disclaimer of Warranty\.$'             'section 15 (Disclaimer of Warranty)'
check_landmark '^  16\. Limitation of Liability\.$'            'section 16 (Limitation of Liability)'

# All eighteen numbered sections, 0 through 17, each exactly once.
missing_sections=""
for n in $(seq 0 17); do
    count="$(grep -c -E "^  ${n}\. " "${TARGET}")"
    [ "${count}" = "1" ] || missing_sections="${missing_sections} ${n}(x${count})"
done
if [ -z "${missing_sections}" ]; then
    pass "all 18 numbered sections 0-17 present exactly once"
else
    fail "numbered sections wrong or missing:${missing_sections} -- the licence is INCOMPLETE"
fi

# The last line of the licence, so a tail-end truncation cannot pass.
last_line="$(tail -n 1 -- "${TARGET}")"
if [ "${last_line}" = '<https://www.gnu.org/licenses/why-not-lgpl.html>.' ]; then
    pass "final line is the canonical closing reference"
else
    fail "final line is '${last_line}', expected '<https://www.gnu.org/licenses/why-not-lgpl.html>.' -- the file ends early"
fi

# Plain ASCII, LF endings: a CRLF conversion or a smart-quote edit is a change
# to a licence text and must be reported as one.
if grep -q -- $'\r' "${TARGET}"; then
    fail "carriage returns present -- the file has been converted to CRLF line endings"
else
    pass "no carriage returns (LF line endings)"
fi
if LC_ALL=C grep -q -P '[^\x00-\x7F]' "${TARGET}" 2>/dev/null; then
    fail "non-ASCII bytes present -- the text has been re-encoded or re-typeset"
else
    pass "pure ASCII"
fi

# --- verdict -----------------------------------------------------------------

printf '\n'
if [ "${failures}" -ne 0 ]; then
    printf 'verify-license.sh: FAILED -- %s check(s) failed on %s\n' "${failures}" "${TARGET}" >&2
    printf 'verify-license.sh: this file is NOT the full unmodified GPL-3.0 text that D-001 requires.\n' >&2
    printf 'verify-license.sh: restore it from https://www.gnu.org/licenses/gpl-3.0.txt and re-run.\n' >&2
    exit 1
fi
printf 'verify-license.sh: OK -- %s is the full, unmodified GPL-3.0 text (D-001 obligation 1).\n' "${TARGET}"
exit 0
