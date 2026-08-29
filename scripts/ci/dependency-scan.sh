#!/usr/bin/env bash
#
# dependency-scan.sh -- the CometGUI dependency vulnerability gate.
#
# The specification requires "dependency and security scanning" on every pull
# request (CI and Release Pipeline) and "run dependency vulnerability scanning
# in CI" (Supply-Chain and Application Security).  The scan itself is
# scripts/ci/dependency-scan.py, which reads the CycloneDX SBOM produced by
# scripts/ci/sbom.sh and queries OSV.  This wrapper exists so that every CI
# step is a uniform `scripts/ci/*.sh` invocation, and so that the falsifiability
# harness lives next to the gate it falsifies.
#
# Usage:
#   bash scripts/ci/dependency-scan.sh
#   bash scripts/ci/dependency-scan.sh --self-test   # the gate, then prove it can fail
#   bash scripts/ci/dependency-scan.sh --help
#
# --self-test is the important half.  A vulnerability scanner is the classic
# tool that exits 0 while doing nothing, so this harness requires the scanner to
# be SEEN failing in every way it is supposed to fail before its clean answer is
# believed: on a known-vulnerable component, on an unreachable endpoint, behind
# a dead proxy, against an endpoint that answers 200 with an all-clean lie, one
# that answers with HTML, one that answers the wrong number of results, on an
# allowlist entry with no reason, on a placeholder reason, on a typo'd field, on
# a stale entry that matches nothing, and on an SBOM with no components.  The
# working tree is never touched: fixtures and damaged allowlists live under
# _build/dependency-scan/selftest/.
#
# Needs network access to https://api.osv.dev.  That is deliberate: this gate
# has no offline mode, because an offline dependency scan is not a dependency
# scan.  If OSV is unreachable the step fails (exit 4) and says so.
#
# Exit status: dependency-scan.py's, unchanged --
#   0 clean   1 vulnerable   2 misuse   3 bad SBOM   4 SCAN DID NOT RUN
#   5 invalid allowlist      6 canary control failed
#   7 --self-test only: an expected failure did not happen

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
SCAN="${SCRIPT_DIR}/dependency-scan.py"
SBOM="${PROJECT_ROOT}/_build/sbom/cometgui-sbom.json"
FIXTURE="${SCRIPT_DIR}/security/fixtures/known-vulnerable.bom.json"
WORK="${PROJECT_ROOT}/_build/dependency-scan"
SELF="${WORK}/selftest"

die() { printf 'dependency-scan.sh: %s\n' "$1" >&2; exit "${2:-2}"; }
usage() { sed -n '3,38p' -- "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

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

if [ ! -f "${SBOM}" ]; then
    die "no SBOM at ${SBOM}. Run scripts/ci/sbom.sh first -- the scan reads the SBOM,
it does not re-derive the dependency graph, so that the document that is published
and the document that is scanned are the same document." 3
fi

mkdir -p -- "${WORK}"

if [ "${SELF_TEST}" -eq 0 ]; then
    exec "${PYTHON}" "${SCAN}" --root "${PROJECT_ROOT}" --sbom "${SBOM}" \
        --json-out "${WORK}/report.json"
fi

# ---------------------------------------------------------------------------
# --self-test
# ---------------------------------------------------------------------------
rm -rf -- "${SELF}"
mkdir -p -- "${SELF}"

FAILURES=0
CASE=0

# expect CODE  LABEL  GREP-OR-EMPTY  -- command...
expect() {
    local want="$1" label="$2" needle="$3"; shift 3
    [ "$1" = "--" ] && shift
    CASE=$((CASE + 1))
    local log="${SELF}/case-$(printf '%02d' "${CASE}")-${label}.log"
    local got=0
    "$@" >"${log}" 2>&1 || got=$?
    local verdict="ok  "
    if [ "${got}" -ne "${want}" ]; then
        verdict="FAIL"
        FAILURES=$((FAILURES + 1))
    elif [ -n "${needle}" ] && ! grep -qF -- "${needle}" "${log}"; then
        verdict="FAIL"
        FAILURES=$((FAILURES + 1))
        printf '        expected output to contain: %s\n' "${needle}"
    fi
    printf '  %s %-34s exit %-2s (expected %s)  %s\n' \
        "${verdict}" "${label}" "${got}" "${want}" "${log#"${PROJECT_ROOT}/"}"
    if [ "${verdict}" = "FAIL" ]; then
        sed -n '1,20p' -- "${log}" | sed 's/^/        /'
    fi
}

write_allowlist() {
    local path="$1"; shift
    printf '%s\n' "$*" > "${path}"
}

free_port() {
    "${PYTHON}" - <<'PY'
import socket
s = socket.socket()
s.bind(("127.0.0.1", 0))
print(s.getsockname()[1])
s.close()
PY
}

start_stub() {
    local mode="$1" port="$2" log="$3"
    "${PYTHON}" "${SCAN}" --serve-stub "${port}" --stub-mode "${mode}" >"${log}" 2>&1 &
    STUB_PID=$!
    local i
    for i in $(seq 1 100); do
        grep -q listening -- "${log}" 2>/dev/null && return 0
        "${PYTHON}" -c "import sys,time; time.sleep(0.05)"
    done
    kill "${STUB_PID}" 2>/dev/null || true
    die "the stub OSV endpoint did not start; see ${log}"
}

stop_stub() {
    [ -n "${STUB_PID:-}" ] || return 0
    kill "${STUB_PID}" 2>/dev/null || true
    wait "${STUB_PID}" 2>/dev/null || true
    STUB_PID=""
}
trap stop_stub EXIT

printf '=== dependency-scan --self-test ===\n'
printf 'Everything happens under %s; the working tree is not touched.\n\n' "${SELF#"${PROJECT_ROOT}/"}"

printf -- '-- 1. it finds a vulnerability when there is one\n'
expect 1 "log4j-fixture" "CVE-2021-44228" -- \
    "${PYTHON}" "${SCAN}" --root "${PROJECT_ROOT}" --sbom "${FIXTURE}" \
        --json-out "${SELF}/fixture-report.json"

printf -- '\n-- 2. it never reports "clean" when it could not ask\n'
expect 4 "endpoint-refused" "THE DEPENDENCY SCAN DID NOT RUN" -- \
    "${PYTHON}" "${SCAN}" --root "${PROJECT_ROOT}" --sbom "${SBOM}" \
        --endpoint "http://127.0.0.1:9" --timeout 5 --json-out "${SELF}/unused.json"

expect 4 "endpoint-dns-failure" "THE DEPENDENCY SCAN DID NOT RUN" -- \
    "${PYTHON}" "${SCAN}" --root "${PROJECT_ROOT}" --sbom "${SBOM}" \
        --endpoint "https://osv.invalid.cometgui.test" --timeout 10 --json-out "${SELF}/unused.json"

env_proxy_scan() {
    https_proxy="http://127.0.0.1:9" http_proxy="http://127.0.0.1:9" \
        "${PYTHON}" "${SCAN}" --root "${PROJECT_ROOT}" --sbom "${SBOM}" \
            --timeout 5 --json-out "${SELF}/unused.json"
}
expect 4 "dead-proxy-env" "THE DEPENDENCY SCAN DID NOT RUN" -- env_proxy_scan

printf -- '\n-- 3. it does not believe an endpoint that answers but cannot find Log4Shell\n'
PORT="$(free_port)"
start_stub empty "${PORT}" "${SELF}/stub-empty.log"
expect 6 "lying-endpoint-all-clean" "CANARY CONTROL FAILED" -- \
    "${PYTHON}" "${SCAN}" --root "${PROJECT_ROOT}" --sbom "${SBOM}" \
        --endpoint "http://127.0.0.1:${PORT}" --timeout 10 --json-out "${SELF}/unused.json"
stop_stub

PORT="$(free_port)"
start_stub garbage "${PORT}" "${SELF}/stub-garbage.log"
expect 4 "endpoint-answers-html" "not JSON" -- \
    "${PYTHON}" "${SCAN}" --root "${PROJECT_ROOT}" --sbom "${SBOM}" \
        --endpoint "http://127.0.0.1:${PORT}" --timeout 10 --json-out "${SELF}/unused.json"
stop_stub

PORT="$(free_port)"
start_stub short "${PORT}" "${SELF}/stub-short.log"
expect 4 "endpoint-answers-wrong-count" "cannot be matched to the questions" -- \
    "${PYTHON}" "${SCAN}" --root "${PROJECT_ROOT}" --sbom "${SBOM}" \
        --endpoint "http://127.0.0.1:${PORT}" --timeout 10 --json-out "${SELF}/unused.json"
stop_stub

printf -- '\n-- 4. the allowlist cannot be used to hide a finding without saying why\n'
write_allowlist "${SELF}/allow-no-reason.json" \
  '{"schema":1,"entries":[{"id":"GHSA-jfh8-c2jp-5v3q","package":"org.apache.logging.log4j:log4j-core","date":"2026-08-29"}]}'
expect 5 "allowlist-no-reason" 'no "reason"' -- \
    "${PYTHON}" "${SCAN}" --root "${PROJECT_ROOT}" --sbom "${FIXTURE}" \
        --allowlist "${SELF}/allow-no-reason.json" --json-out "${SELF}/unused.json"

write_allowlist "${SELF}/allow-placeholder.json" \
  '{"schema":1,"entries":[{"id":"GHSA-jfh8-c2jp-5v3q","package":"org.apache.logging.log4j:log4j-core","reason":"n/a","date":"2026-08-29"}]}'
expect 5 "allowlist-placeholder-reason" "placeholder" -- \
    "${PYTHON}" "${SCAN}" --root "${PROJECT_ROOT}" --sbom "${FIXTURE}" \
        --allowlist "${SELF}/allow-placeholder.json" --json-out "${SELF}/unused.json"

write_allowlist "${SELF}/allow-short-reason.json" \
  '{"schema":1,"entries":[{"id":"GHSA-jfh8-c2jp-5v3q","package":"org.apache.logging.log4j:log4j-core","reason":"fine","date":"2026-08-29"}]}'
expect 5 "allowlist-unactionable-reason" "characters" -- \
    "${PYTHON}" "${SCAN}" --root "${PROJECT_ROOT}" --sbom "${FIXTURE}" \
        --allowlist "${SELF}/allow-short-reason.json" --json-out "${SELF}/unused.json"

write_allowlist "${SELF}/allow-typo.json" \
  '{"schema":1,"entries":[{"id":"GHSA-jfh8-c2jp-5v3q","package":"org.apache.logging.log4j:log4j-core","resaon":"a typo in the field name must not silently disable the entry","date":"2026-08-29"}]}'
expect 5 "allowlist-typo-field" "unknown field" -- \
    "${PYTHON}" "${SCAN}" --root "${PROJECT_ROOT}" --sbom "${FIXTURE}" \
        --allowlist "${SELF}/allow-typo.json" --json-out "${SELF}/unused.json"

write_allowlist "${SELF}/allow-stale.json" \
  '{"schema":1,"entries":[{"id":"GHSA-0000-0000-0000","package":"org.slf4j:slf4j-api","reason":"a stale acceptance that matches no finding is a place for a real one to hide","date":"2026-08-29"}]}'
expect 5 "allowlist-matches-nothing" "MATCHES NOTHING" -- \
    "${PYTHON}" "${SCAN}" --root "${PROJECT_ROOT}" --sbom "${SBOM}" \
        --allowlist "${SELF}/allow-stale.json" --json-out "${SELF}/unused.json"

write_allowlist "${SELF}/allow-expired.json" \
  '{"schema":1,"entries":[{"id":"CVE-2021-44228","package":"org.apache.logging.log4j:log4j-core","reason":"accepted once with a review date that has now passed, so it must stop being accepted","date":"2021-12-10","review_by":"2022-01-10"}]}'
expect 1 "allowlist-entry-expired" "EXPIRED" -- \
    "${PYTHON}" "${SCAN}" --root "${PROJECT_ROOT}" --sbom "${FIXTURE}" \
        --allowlist "${SELF}/allow-expired.json" --json-out "${SELF}/unused.json"

printf -- '\n-- 5. a well-formed acceptance really is applied (and only to what it names)\n'
write_allowlist "${SELF}/allow-valid.json" \
  '{"schema":1,"entries":[{"id":"CVE-2021-44228","package":"org.apache.logging.log4j:log4j-core","reason":"fixture only: proves that a complete allowlist entry with a real reason is honoured","date":"2026-08-29"}]}'
expect 1 "allowlist-valid-entry-applied" "ACCEPTED" -- \
    "${PYTHON}" "${SCAN}" --root "${PROJECT_ROOT}" --sbom "${FIXTURE}" \
        --allowlist "${SELF}/allow-valid.json" --json-out "${SELF}/accepted-report.json"
if [ -f "${SELF}/accepted-report.json" ]; then
    "${PYTHON}" - "${SELF}/accepted-report.json" <<'PY'
import json, sys
doc = json.load(open(sys.argv[1]))
accepted = {a["id"] for a in doc["accepted"]}
unresolved = {u["id"] for u in doc["unresolved"]}
print(f"        accepted={sorted(accepted)}  still unresolved={len(unresolved)}")
assert accepted == {"GHSA-jfh8-c2jp-5v3q"}, accepted
assert "GHSA-jfh8-c2jp-5v3q" not in unresolved
assert len(unresolved) >= 1, "accepting one advisory must not accept the others"
print("        ok   the accepted advisory left the failure list; the rest did not")
PY
fi

printf -- '\n-- 6. an SBOM with nothing in it is not a clean scan\n'
printf '%s\n' '{"bomFormat":"CycloneDX","specVersion":"1.6","components":[]}' > "${SELF}/empty.bom.json"
expect 3 "empty-sbom" "EMPTY" -- \
    "${PYTHON}" "${SCAN}" --root "${PROJECT_ROOT}" --sbom "${SELF}/empty.bom.json" \
        --json-out "${SELF}/unused.json"

printf -- '\n-- 7. control: the real SBOM against the real endpoint must still pass\n'
expect 0 "control-real-scan" "canary control OK" -- \
    "${PYTHON}" "${SCAN}" --root "${PROJECT_ROOT}" --sbom "${SBOM}" \
        --json-out "${SELF}/control-report.json"

printf '\n'
if [ "${FAILURES}" -ne 0 ]; then
    printf 'dependency-scan.sh: SELF-TEST FAILED -- %d of %d case(s) did not behave as required.\n' \
        "${FAILURES}" "${CASE}" >&2
    printf 'dependency-scan.sh: this gate has not been shown to work and must not be trusted.\n' >&2
    exit 7
fi
printf 'dependency-scan.sh: self-test OK -- %d/%d cases.  The scanner has been seen to fail\n' "${CASE}" "${CASE}"
printf 'dependency-scan.sh: on a real vulnerability, on three kinds of unreachable endpoint,\n'
printf 'dependency-scan.sh: on an endpoint that lies, and on five kinds of bad allowlist --\n'
printf 'dependency-scan.sh: and to pass only on the real SBOM against the real OSV API.\n'
exit 0
