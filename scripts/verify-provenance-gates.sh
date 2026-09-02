#!/usr/bin/env bash
#
# CometGUI -- prove the PHASE-04 hashing and provenance gates can fail.
#
#   bash scripts/verify-provenance-gates.sh
#
# A gate that has never been seen to fail has not been shown to work
# (CONTRIBUTING.rst, "Gate conventions").  Phase 04's exit gate is seven claims
# about hashing, caching, crash recovery, atomic finalisation and secret
# redaction.  Each is checked by a test, and a green test says nothing about
# whether that test would notice the defect it exists to catch.  This script
# injects, one at a time, the defect each item exists to catch, requires the
# narrowest command that should catch it to exit non-zero WITH THE EXPECTED
# DIAGNOSTIC, and then requires the same command to pass once the defect is
# removed.
#
# It is the sibling of scripts/verify-quality-gates.sh, verify-test-gates.sh
# and verify-shell-gates.sh, and deliberately follows verify-shell-gates.sh's
# shape rather than inventing a fourth one.
#
# IT IS ASSEMBLED FROM A RECORD, NOT INVENTED.  Every injection below was
# actually made during phase 04 and its exact failure text is in
# handoffs/PHASE-04-worklog.rst, either at the sign-off of the unit that owns
# the item or in the resumption's own sign-off entries.  That is why the
# expected diagnostics are specific: they are what was observed, not what
# seemed plausible.
#
# WHAT IT COVERS -- the seven PHASE-04 exit gate items:
#
#   0   baseline: the undamaged sandbox passes every class the controls use
#   1   item 1: a hasher that digests one byte less than it read, so every
#       published vector comes back wrong
#   2   item 2: a hasher that keeps every chunk it reads, so the 2 GB proof's
#       retained-heap bound is exceeded while the digests stay correct
#   3   item 3: a fingerprint that treats an absent attribute as a match, the
#       "Windows compatibility" shape, so a cached entry it cannot validate is
#       served anyway
#   4   item 4: a reader that drops the torn tail of a crashed log, so the
#       damage a crash leaves is not reported
#   5   item 5: ATOMIC_MOVE replaced by copy-then-delete, so a concurrent
#       reader observes truncated documents and moments with no file at all
#   6a  item 6, JSON: the JSON writer escapes a value but never redacts it
#   6b  item 6, RST: the RST writer takes a "fast path" for short values, the
#       size-conditioned leak this phase catalogued in the redactor and had
#       never tested in a writer
#   6c  item 6, event log: the log writes its payload unredacted
#   7   item 7: DELEGATED to scripts/verify-test-gates.sh, whose mutation
#       control is what proves a surviving mutation fails a build; the
#       delegation is enforced here rather than asserted in prose, and this
#       control also requires cometgui-provenance's own mutation switch to
#       still be on -- a switch that is set and inert is the failure this
#       project keeps finding
#   H   the harness itself: a control whose defect was NOT injected must be
#       reported as a HARNESS FAILURE, not as a pass
#
# WHERE IT WORKS.  Never in the working tree.  It extracts `git archive HEAD`
# into _build/provenance-gate-sandbox and damages that, so a half-injured file
# can never be committed by accident.  `git archive HEAD` is the COMMITTED
# tree: if the working tree has uncommitted changes under cometgui-*/src the
# script says so, loudly, because what it then proves is that HEAD's gates
# bite -- not the tree's.  tools/ is gitignored and therefore absent from the
# archive, so it is symlinked; without it every control would fail for the
# wrong reason.
#
# EVERY INJECTION IS ANCHORED, AND A MISSING ANCHOR IS FATAL.  Each injection
# is a literal replacement that must match EXACTLY ONCE in the file.  If the
# source moves under this script it stops with a harness error naming the
# anchor rather than injecting nothing and reporting a green run.
#
# WHAT IT SWITCHES OFF, AND WHY THAT IS NOT A WEAKENING.  Every sandbox Maven
# run passes -Dspotless.check.skip -Dcheckstyle.skip -Dspotbugs.skip
# -Djacoco.skip.  Those are Phase 01 units 2 and 3's gates, they have their own
# harnesses, and an injected defect that happens to be badly formatted would
# otherwise be rejected by the formatter first -- the control would "fail" for
# the wrong reason and prove nothing about provenance.  Nothing this script is
# testing is skipped, no test class is excluded or filtered, and every run is
# checked to have actually EXECUTED the class whose gate is under test.
#
# WHAT IT NEEDS.  A built tree: tools/ (JDK and Maven) and a populated
# _build/m2repo -- run `bash scripts/build.sh` first.  It runs Maven offline
# and needs no network.  It writes only under _build/.  Controls 0 and 2 hash a
# 2 GB temporary file, so it needs a few gigabytes of scratch space and takes
# noticeably longer than the other harnesses.
#
# EXIT STATUS
#   0  every control bit
#   1  at least one control failed -- a gate did not bite, a gate failed for
#      the wrong reason, or an injection reached the file without reaching the
#      behaviour
#   2  misuse (unknown option)
#   3  the environment is not ready (no tools/, no _build/m2repo, no git)
#   4  HARNESS ERROR: an injection did not reach the sandbox, an anchor no
#      longer exists, or a command that was supposed to run a test class ran
#      none.  The run proves nothing and must not be read as a pass.

set -Eeuo pipefail

# --------------------------------------------------------------- constants --
SCRIPT_NAME="$(basename -- "${BASH_SOURCE[0]}")"
readonly SCRIPT_NAME
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT
readonly SANDBOX="${ROOT}/_build/provenance-gate-sandbox"
readonly PRISTINE="${ROOT}/_build/provenance-gate-pristine"
readonly M2REPO="${ROOT}/_build/m2repo"
readonly LOGS="${ROOT}/_build/provenance-gate-logs"

# The files the controls damage.  Every one is in cometgui-provenance; the one
# shared rule set in cometgui-domain is deliberately NOT damaged here, because
# cometgui-process depends on it too and its own gates are Phase 03's.
readonly HASHER="cometgui-provenance/src/main/java/org/cometgui/provenance/hashing/StreamingHashService.java"
readonly FINGERPRINT="cometgui-provenance/src/main/java/org/cometgui/provenance/hashing/FileFingerprint.java"
readonly LOG_READER="cometgui-provenance/src/main/java/org/cometgui/provenance/events/ProvenanceEventLogReader.java"
readonly EVENT_LOG="cometgui-provenance/src/main/java/org/cometgui/provenance/events/ProvenanceEventLog.java"
readonly DURABILITY="cometgui-provenance/src/main/java/org/cometgui/provenance/io/FileSystemDurability.java"
readonly JSON_WRITER="cometgui-provenance/src/main/java/org/cometgui/provenance/json/JsonWriter.java"
readonly RST_WRITER="cometgui-provenance/src/main/java/org/cometgui/provenance/report/RstWriter.java"

# The test classes, by the gate item each one proves.
readonly TEST_VECTORS="StreamingHashServiceTest"
readonly TEST_HUGE="HugeFileHashingTest"
readonly TEST_CACHE="CachingHashServiceTest"
readonly TEST_FINGERPRINT="FileFingerprintTest"
readonly TEST_RECOVERY="EventLogCrashRecoveryTest"
readonly TEST_ATOMIC="AtomicDocumentWriterTest"
readonly TEST_SWEEP="SeededSecretArtefactSweepTest"
readonly TEST_REPORT="ProvenanceReportWriterTest"

# Control 7's delegation.  These strings are what makes it load-bearing: if the
# other harness loses its mutation control, this script fails rather than
# continuing to claim item 7 is covered somewhere else.
readonly TEST_GATES="scripts/verify-test-gates.sh"
readonly TEST_GATES_MUTATION="mutation"
readonly MODULE_POM="cometgui-provenance/pom.xml"
readonly MUTATION_SWITCH="<cometgui.mutation.skip>false</cometgui.mutation.skip>"

# Phase 01 units 2 and 3's gates are off in the sandbox; see the header.
readonly -a QUIET=(
    "-Dspotless.check.skip=true"
    "-Dcheckstyle.skip=true"
    "-Dspotbugs.skip=true"
    "-Djacoco.skip=true"
)

PASSED=0
FAILED=0
FAILURES=()

# ----------------------------------------------------------------- plumbing --

usage() {
    cat <<USAGE
${SCRIPT_NAME} -- prove the seven PHASE-04 exit gate items fail on the defects
they exist to catch.

Usage:
  bash scripts/${SCRIPT_NAME} [-h|--help]

It needs a built tree: the project-local toolchain in tools/ and a populated
_build/m2repo.  Run bash scripts/build.sh first.  It runs Maven offline,
damages only a git-archive sandbox under _build/, and writes only under
_build/.  Two controls hash a 2 GB temporary file.

Exit status: 0 every control bit; 1 a control failed; 2 misuse; 3 the
environment is not ready; 4 a harness error (an injection that reached
nothing).
USAGE
}

# die MESSAGE [EXIT CODE].  Only $1 is the message: $* would print the code.
die() {
    printf '\nFATAL: %s\n' "$1" >&2
    exit "${2:-1}"
}

harness_error() {
    printf '\nHARNESS ERROR: %s\n' "$1" >&2
    printf 'The run proves nothing and must not be read as a pass.\n' >&2
    exit 4
}

banner() {
    printf '\n-------------------------------------------------------------------------------\n'
    printf ' CONTROL %s\n' "$*"
    printf -- '-------------------------------------------------------------------------------\n'
}

record_pass() {
    PASSED=$((PASSED + 1))
    printf '   PASS  %s\n' "$*"
}

record_fail() {
    FAILED=$((FAILED + 1))
    FAILURES+=("$*")
    printf '   FAIL  %s\n' "$*"
}

# ------------------------------------------------------------ running tests --
#
# One command shape for every control: the narrowest thing that exercises the
# gate under test, which here is the test class that asserts it.  -am is not
# optional: cometgui-provenance depends on cometgui-domain, and without it the
# reactor resolves a jar from _build/m2repo instead of the sandbox's sources.
run_gate_tests() {
    local log="$1" classes="$2"
    local rc=0
    # The reports from the PREVIOUS control are deleted first.  Without this, a
    # run that executed nothing would be graded against the last run's reports
    # and the "the class really ran" check below would pass on stale files --
    # the vacuous pass this harness exists to refuse.
    rm -rf -- "${SANDBOX}/cometgui-provenance/target/surefire-reports"
    ( cd "${SANDBOX}" \
        && mvn -B -o -Dmaven.repo.local="${M2REPO}" "${QUIET[@]}" \
            -pl cometgui-provenance -am test \
            -Dtest="${classes}" -Dsurefire.failIfNoSpecifiedTests=false ) \
        >"${log}" 2>&1 || rc=$?
    return "${rc}"
}

gate_command() {
    printf 'mvn -o -pl cometgui-provenance -am test -Dtest=%s' "$1"
}

# THE VACUOUS PASS THIS SCRIPT COULD OFFER FOR FREE.  -Dtest with a name that
# matches nothing, plus -Dsurefire.failIfNoSpecifiedTests=false, exits 0 having
# run no test at all -- so a mistyped class name would turn every "the gate
# accepts the clean tree" control into a green line proving nothing.  Every run
# is therefore required to have EXECUTED each class it named, and a run that
# did not is a harness error rather than a control failure.
verify_classes_ran() {
    local log="$1" classes="$2" class count report
    local reports="${SANDBOX}/cometgui-provenance/target/surefire-reports"
    local -a wanted=()
    IFS=',' read -r -a wanted <<< "${classes}"
    for class in "${wanted[@]}"; do
        # THE COUNT COMES FROM THE XML REPORT, NOT FROM THE CONSOLE.  Every test
        # class in this module puts its tests in @Nested classes, and surefire
        # prints each nested class under its @DisplayName and then prints
        # "Tests run: 0 ... -- in <the outer class>".  A console-scraping check
        # therefore reads 0 for a class that ran fifty-two tests -- which is how
        # this harness failed on its own first run, correctly, as a HARNESS
        # ERROR.  <testsuite tests="N"> is the per-class total with the nested
        # classes counted in.
        report="$(ls -1 "${reports}"/TEST-*."${class}".xml 2>/dev/null | head -1 || true)"
        if [ -z "${report}" ] || [ ! -s "${report}" ]; then
            harness_error "${class} produced no surefire report under ${reports#"${ROOT}/"} for ${log#"${ROOT}/"}. The command named a class that surefire did not execute, so this control tested nothing."
        fi
        count="$(sed -n 's/.*<testsuite [^>]*tests="\([0-9][0-9]*\)".*/\1/p' "${report}" | head -1)"
        if [ -z "${count}" ]; then
            harness_error "the surefire report for ${class} carries no tests= count; it is not the shape this harness reads, and the check would pass vacuously."
        fi
        if [ "${count}" -lt 1 ]; then
            harness_error "${class} executed ${count} tests in ${log#"${ROOT}/"}. A class that runs no test cannot prove or disprove a gate."
        fi
    done
}

# TWO WAYS TO STATE AN EXPECTED DIAGNOSTIC.  A diagnostic with no number in it
# is asserted literally, because a literal cannot drift into matching something
# it was not meant to.  One that carries a measured number -- a count of torn
# documents, a heap figure, an offset -- is asserted by SHAPE, so that the
# assertion says what must be true rather than what happened to be true on one
# day in September.  The shape still has to name the thing: "the test failed
# somehow" is not an assertion.
log_has() {
    local log="$1" expected="$2" mode="$3"
    case "${mode}" in
        fixed) grep -qF -- "${expected}" "${log}" ;;
        regex) grep -qE -- "${expected}" "${log}" ;;
        *) harness_error "unknown match mode '${mode}'" ;;
    esac
}

log_first_match() {
    local log="$1" expected="$2" mode="$3"
    case "${mode}" in
        fixed) grep -F -- "${expected}" "${log}" | head -1 ;;
        regex) grep -E -- "${expected}" "${log}" | head -1 ;;
    esac
}

assert_gate_fails() { graded_failure fixed "$@"; }
assert_gate_fails_matching() { graded_failure regex "$@"; }

graded_failure() {
    local mode="$1" label="$2" expected="$3" log="$4" classes="$5"
    local rc=0
    run_gate_tests "${log}" "${classes}" || rc=$?
    if [ "${rc}" -eq 0 ]; then
        # The two ways this happens are a dead gate and an injection that
        # reached the FILE without reaching the BEHAVIOUR.  Both are failures
        # of this harness's claim and neither may be recorded as a pass.
        record_fail "${label}: HARNESS FAILURE -- the check PASSED with the defect present. Either the gate is dead or the injection never reached the running code (log: ${log#"${ROOT}/"})"
        return
    fi
    if ! log_has "${log}" "${expected}" "${mode}"; then
        record_fail "${label}: failed, but without the expected diagnostic '${expected}' (log: ${log#"${ROOT}/"})"
        printf '         first error line: %s\n' \
            "$(grep -m1 '^\[ERROR\]' "${log}" | cut -c1-140)"
        return
    fi
    record_pass "${label}: rejected, exit ${rc}"
    printf '         %s\n' "$(log_first_match "${log}" "${expected}" "${mode}" | cut -c1-160)"
}

assert_log_contains() { graded_log fixed "$@"; }
assert_log_matches() { graded_log regex "$@"; }

graded_log() {
    local mode="$1" label="$2" log="$3" expected="$4"
    if log_has "${log}" "${expected}" "${mode}"; then
        record_pass "${label}"
        printf '         %s\n' "$(log_first_match "${log}" "${expected}" "${mode}" | cut -c1-160)"
    else
        record_fail "${label}: nothing in ${log#"${ROOT}/"} matches '${expected}'"
    fi
}

# A control that only shows red proves the test is broken, not that it is
# discriminating.  This is the other half of every control.
assert_gate_passes() {
    local label="$1" log="$2" classes="$3"
    local rc=0
    run_gate_tests "${log}" "${classes}" || rc=$?
    if [ "${rc}" -ne 0 ]; then
        record_fail "${label}: the check still fails after the defect was removed (log: ${log#"${ROOT}/"})"
        printf '         %s\n' "$(grep -m3 '^\[ERROR\]' "${log}" | head -3 | cut -c1-140)"
        return
    fi
    verify_classes_ran "${log}" "${classes}"
    record_pass "${label}: exit 0, ${classes} executed"
}

# Control 7's shape: the gate being proved lives in another script or in a POM,
# so what is asserted here is that the other thing still contains it.
assert_file_contains() {
    local label="$1" file="$2" expected="$3"
    if grep -qF -- "${expected}" "${ROOT}/${file}"; then
        record_pass "${label}"
        printf '         %s: %s\n' "${file}" \
            "$(grep -nF -- "${expected}" "${ROOT}/${file}" | head -1 | cut -c1-140)"
    else
        record_fail "${label}: ${file} no longer contains '${expected}'"
    fi
}

# ------------------------------------------------- injection and its guards --

save_pristine() {
    local file="$1"
    mkdir -p -- "$(dirname -- "${PRISTINE}/${file}")"
    cp -- "${SANDBOX}/${file}" "${PRISTINE}/${file}"
}

# replace_once <label> <file> <old literal> <new literal>
# The anchor must match EXACTLY ONCE.  Zero matches means the source moved and
# the injection would test nothing; more than one means the injection is
# broader than the control describes.  Both stop the run.
replace_once() {
    local label="$1" file="$2" old="$3" new="$4"
    local rc=0
    python3 - "${SANDBOX}/${file}" "${old}" "${new}" "${label}" <<'PYTHON' || rc=$?
import sys

path, old, new, label = sys.argv[1:5]
with open(path, encoding="utf-8") as handle:
    text = handle.read()
found = text.count(old)
if found != 1:
    sys.stderr.write(
        "the anchor for %r occurs %d time(s) in %s, expected exactly once.\n"
        "  anchor: %r\n" % (label, found, path, old)
    )
    raise SystemExit(1)
with open(path, "w", encoding="utf-8") as handle:
    handle.write(text.replace(old, new))
PYTHON
    if [ "${rc}" -ne 0 ]; then
        harness_error "(${label}) the injection anchor is gone from ${file}. The source moved under this control, which would inject nothing and report green."
    fi
}

# assert_modified <label> <file> -- and it really differs from the pristine
# copy taken before the injection.  A byte-identical file is an injection that
# never happened.
assert_modified() {
    local label="$1" file="$2"
    [ -e "${SANDBOX}/${file}" ] \
        || harness_error "(${label}) ${file} is missing from the sandbox."
    [ -e "${PRISTINE}/${file}" ] \
        || harness_error "(${label}) no pristine copy of ${file} was taken, so nothing can be compared."
    if cmp -s "${SANDBOX}/${file}" "${PRISTINE}/${file}"; then
        harness_error "(${label}) ${file} is byte-identical to the pristine copy. The defect was not injected and the control would have tested nothing."
    fi
    printf '   injected %s (%s changed line(s) against the pristine copy)\n' "${file}" \
        "$(diff "${PRISTINE}/${file}" "${SANDBOX}/${file}" | grep -c '^[<>]')"
}

# restore_pristine <file> -- and prove the restoration, or the "clean" re-run
# would not be clean and its green would mean nothing.
restore_pristine() {
    local file="$1"
    [ -e "${PRISTINE}/${file}" ] \
        || harness_error "no pristine copy of ${file} to restore from."
    cp -- "${PRISTINE}/${file}" "${SANDBOX}/${file}"
    cmp -s "${SANDBOX}/${file}" "${PRISTINE}/${file}" \
        || harness_error "could not restore ${file} in the sandbox."
    # A copy preserves the modification time, and Maven's incremental compiler
    # then sees nothing newer and runs the PREVIOUS defect's classes against
    # clean sources.  That made a clean tree look broken once in this phase.
    touch -- "${SANDBOX}/${file}"
    printf '   restored %s (and touched, so the next build really recompiles it)\n' "${file}"
}

# ------------------------------------------------------------- the sandbox --
build_sandbox() {
    rm -rf -- "${SANDBOX}" "${PRISTINE}"
    mkdir -p -- "${SANDBOX}" "${PRISTINE}"

    ( cd -- "${ROOT}" && git archive HEAD ) | tar -x -C "${SANDBOX}" \
        || harness_error "git archive HEAD could not be extracted into the sandbox."

    # tools/ is gitignored and therefore not in the archive.
    ln -s -- "${ROOT}/tools" "${SANDBOX}/tools"
    [ -x "${SANDBOX}/tools/env.sh" ] || [ -f "${SANDBOX}/tools/env.sh" ] \
        || harness_error "the sandbox's tools/ symlink does not resolve. Every control would fail for the wrong reason."

    mkdir -p -- "${SANDBOX}/_build"
    ln -s -- "${M2REPO}" "${SANDBOX}/_build/m2repo"

    local copied original head
    copied="$(find "${SANDBOX}"/cometgui-*/src -name '*.java' -type f | wc -l)"
    original="$(find "${ROOT}"/cometgui-*/src -name '*.java' -type f | wc -l)"
    head="$(cd -- "${ROOT}" && git rev-parse --short HEAD)"
    echo "Sandbox: ${SANDBOX#"${ROOT}/"} (git archive ${head}, ${copied} java files)"

    if [ "${copied}" -ne "${original}" ]; then
        printf '\n  NOTE: the sandbox has %d java files and the working tree has %d.\n' \
            "${copied}" "${original}"
    fi
    local dirty
    dirty="$(cd -- "${ROOT}" && git status --porcelain -- 'cometgui-*/src' | head -20)"
    if [ -n "${dirty}" ]; then
        printf '\n  NOTE: the working tree has uncommitted changes under cometgui-*/src.\n'
        printf '        This run proves the gates of HEAD (%s), not of the working tree:\n' "${head}"
        printf '%s\n' "${dirty}" | sed 's/^/          /'
    fi
}

# ------------------------------------------------------------- the controls --

control_baseline() {
    banner "0  baseline: the undamaged sandbox passes every class the controls use"
    # Every control below is a difference from this run.  If the clean tree
    # does not pass, nothing further can be attributed to an injection.
    local log="${LOGS}/00-baseline.log"
    local classes="${TEST_VECTORS},${TEST_HUGE},${TEST_CACHE},${TEST_FINGERPRINT},${TEST_RECOVERY},${TEST_ATOMIC},${TEST_SWEEP},${TEST_REPORT}"
    assert_gate_passes "baseline: the eight gate test classes on the clean sandbox" \
        "${log}" "${classes}"
    # Gate item 2's own printout, which is the measurement rather than an
    # assertion about one.  Asserted by SHAPE: the numbers are measured.
    assert_log_matches "gate item 2: the 2 GB run reports one open and a bounded heap" \
        "${log}" \
        "huge-file: bytes=2147483648 .*opens=1 readCalls=[0-9]+ bytesDelivered=2147483648 .*heapGrowth=[0-9]+ heapLimit=[0-9]+ samples=[0-9]+"
    # The permanent negative control that ships inside the suite: every build
    # re-proves the heap bound bites while the leaky hasher's digests stay
    # exactly correct.
    assert_log_matches "gate item 2: the permanent in-suite negative control still runs" \
        "${log}" \
        "huge-file-control: keptChunks=[0-9]+ keptBytes=[0-9]+"
}

control_known_vectors() {
    banner "1  gate item 1: a hasher that digests one byte less than it read"
    # The published MD5 and SHA-256 vectors, including the zero-byte file, are
    # hand-typed literals recomputed independently with GNU coreutils.  The
    # defect is the off-by-one a maintainer writes while "fixing" the short
    # last chunk: correct for an empty file, wrong for every other.
    save_pristine "${HASHER}"
    replace_once "known vectors" "${HASHER}" \
        '                md5.update(buffer, 0, read);
                sha256.update(buffer, 0, read);' \
        '                md5.update(buffer, 0, Math.max(0, read - 1));
                sha256.update(buffer, 0, Math.max(0, read - 1));'
    assert_modified "known vectors" "${HASHER}"

    local log="${LOGS}/01-vectors-dirty.log"
    assert_gate_fails_matching "${TEST_VECTORS} rejects a hasher whose digests are wrong" \
        "expected: <[0-9a-f]{32}> but was: <[0-9a-f]{32}>" \
        "${log}" "${TEST_VECTORS}"

    restore_pristine "${HASHER}"
    assert_gate_passes "${TEST_VECTORS} accepts the hasher once the digests are right" \
        "${LOGS}/01-vectors-clean.log" "${TEST_VECTORS}"
}

control_bounded_heap() {
    banner "2  gate item 2: a hasher that keeps every chunk, so the heap bound is exceeded"
    # "A 2 GB temporary file hashes in one pass with bounded heap."  The defect
    # keeps a copy of every chunk, which leaves the DIGESTS EXACTLY CORRECT and
    # is invisible to every other test in the module.  That is the whole point
    # of the item: it is a bound on memory, not on correctness.
    save_pristine "${HASHER}"
    replace_once "bounded heap" "${HASHER}" \
        '        byte[] buffer = new byte[BUFFER_SIZE];' \
        '        byte[] buffer = new byte[BUFFER_SIZE];
        java.util.List<byte[]> keptForever = new java.util.ArrayList<>();'
    replace_once "bounded heap (retain)" "${HASHER}" \
        '                sha256.update(buffer, 0, read);' \
        '                sha256.update(buffer, 0, read);
                keptForever.add(java.util.Arrays.copyOf(buffer, read));'
    assert_modified "bounded heap" "${HASHER}"

    local log="${LOGS}/02-heap-dirty.log"
    assert_gate_fails_matching "${TEST_HUGE} rejects a hasher that retains what it read" \
        "heapGrowth|retained heap|heapLimit" \
        "${log}" "${TEST_HUGE}"

    restore_pristine "${HASHER}"
    assert_gate_passes "${TEST_HUGE} accepts the hasher once it keeps nothing" \
        "${LOGS}/02-heap-clean.log" "${TEST_HUGE}"
}

control_cache_attributes() {
    banner "3  gate item 3: a fingerprint that treats an absent attribute as a match"
    # "The hash cache returns a cached value only when every attribute
    # matches."  The defect is dressed as Windows compatibility -- where an
    # attribute is unavailable, accept anything -- and it makes a cached entry
    # the cache CANNOT VALIDATE serve a hash of content nobody read.
    save_pristine "${FINGERPRINT}"
    replace_once "cache attributes" "${FINGERPRINT}" \
        '                && Objects.equals(fileIdentity, other.fileIdentity)
                && Objects.equals(inodeChangedAt, other.inodeChangedAt);' \
        '                && (fileIdentity == null || Objects.equals(fileIdentity, other.fileIdentity))
                && (inodeChangedAt == null
                        || Objects.equals(inodeChangedAt, other.inodeChangedAt));'
    assert_modified "cache attributes" "${FINGERPRINT}"

    local log="${LOGS}/03-cache-dirty.log"
    assert_gate_fails_matching "${TEST_FINGERPRINT} rejects a fingerprint that matches on an absent attribute" \
        "expected: <false> but was: <true>" \
        "${log}" "${TEST_FINGERPRINT}"

    restore_pristine "${FINGERPRINT}"
    assert_gate_passes "${TEST_FINGERPRINT} accepts the fingerprint once every attribute must match" \
        "${LOGS}/03-cache-clean.log" "${TEST_FINGERPRINT}"
}

control_crash_recovery() {
    banner "4  gate item 4: a reader that drops the torn tail of a crashed log"
    # "A crash simulated mid-run leaves a parsable event log with usable
    # history."  A crash almost always tears the last line, and a reader that
    # silently discards it reports a clean log for a run that died -- the
    # damage disappears rather than being recorded.
    save_pristine "${LOG_READER}"
    replace_once "crash recovery" "${LOG_READER}" \
        '        if (line.size() > 0) {
            recovery.acceptTornTail(line.size(), lineStart);
        }' \
        '        // INJECTED: the torn tail is dropped instead of being reported.'
    assert_modified "crash recovery" "${LOG_READER}"

    local log="${LOGS}/04-recovery-dirty.log"
    assert_gate_fails_matching "${TEST_RECOVERY} rejects a reader that hides the tear a crash left" \
        "expected: <[0-9]+> but was: <[0-9]+>|TRUNCATED|torn" \
        "${log}" "${TEST_RECOVERY}"

    restore_pristine "${LOG_READER}"
    assert_gate_passes "${TEST_RECOVERY} accepts the reader once the tear is reported" \
        "${LOGS}/04-recovery-clean.log" "${TEST_RECOVERY}"
}

control_atomic_finalisation() {
    banner "5  gate item 5: ATOMIC_MOVE replaced by copy-then-delete"
    # "Finalisation is atomic: an interrupted finalise never leaves a truncated
    # provenance.json."  This is the item proved by OBSERVING a torn file
    # rather than by reasoning that rename is atomic, and the defect is the one
    # a maintainer writes when ATOMIC_MOVE is unsupported somewhere: fall back
    # to copy, which truncates the target and then streams into it.
    save_pristine "${DURABILITY}"
    replace_once "atomic finalisation" "${DURABILITY}" \
        '        Files.move(
                temporary,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);' \
        '        Files.copy(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        Files.delete(temporary);'
    assert_modified "atomic finalisation" "${DURABILITY}"

    local log="${LOGS}/05-atomic-dirty.log"
    assert_gate_fails_matching "${TEST_ATOMIC} rejects a finalise a concurrent reader can see through" \
        "expected: <0> but was: <[0-9]+>" \
        "${log}" "${TEST_ATOMIC}"

    restore_pristine "${DURABILITY}"
    assert_gate_passes "${TEST_ATOMIC} accepts the writer once the rename is atomic again" \
        "${LOGS}/05-atomic-clean.log" "${TEST_ATOMIC}"
}

control_json_redaction() {
    banner "6a gate item 6, JSON: a value escaped but never redacted"
    # "A seeded corpus of secrets appears nowhere in JSON, RST or logs; the
    # test greps the generated artefacts."  Redaction lives INSIDE the writer
    # precisely so that a new field cannot open a leak path; this removes it
    # from the one place it lives.
    save_pristine "${JSON_WRITER}"
    replace_once "json redaction" "${JSON_WRITER}" \
        '        escapeInto(redactor.redactText(value));' \
        '        escapeInto(value);'
    assert_modified "json redaction" "${JSON_WRITER}"

    local log="${LOGS}/06a-json-dirty.log"
    assert_gate_fails_matching "${TEST_SWEEP} finds the corpus in provenance.json on disk" \
        "corpus secret #[0-9]+ \(length [0-9]+\) survived into provenance.json" \
        "${log}" "${TEST_SWEEP}"
    # The sweep must say WHICH artefact and WHERE, and must never print the
    # secret.  Both halves are asserted, because a sweep that reported only
    # "something leaked" would be almost useless to whoever has to fix it.
    assert_log_matches "the sweep names the artefact, the offset and both searches" \
        "${log}" \
        "survived into provenance.json as US-ASCII bytes, at offset [0-9]+"

    restore_pristine "${JSON_WRITER}"
    assert_gate_passes "${TEST_SWEEP} accepts the run directory once the JSON writer redacts again" \
        "${LOGS}/06a-json-clean.log" "${TEST_SWEEP}"
}

control_rst_redaction() {
    banner "6b gate item 6, RST: a size-conditioned leak in the report writer"
    # This phase catalogued "a leak conditioned on the input's SIZE" in the
    # REDACTOR and fixed the corpus for it.  Nobody had asked whether the same
    # defect in a WRITER would be caught.  The corpus's deliberately short
    # carriers -- the shortest is twelve characters -- are what catch it, and
    # they are the thing a later agent is most likely to "tidy" into
    # realistic-looking longer examples.
    save_pristine "${RST_WRITER}"
    replace_once "rst redaction" "${RST_WRITER}" \
        '        String redacted = redactor.redactText(text);' \
        '        String redacted = text.length() < 24 ? text : redactor.redactText(text);'
    assert_modified "rst redaction" "${RST_WRITER}"

    local log="${LOGS}/06b-rst-dirty.log"
    assert_gate_fails_matching "${TEST_REPORT} finds a short secret in the report a fast path let through" \
        "corpus secret #[0-9]+ \(length 1[0-9]\) survived" \
        "${log}" "${TEST_REPORT},${TEST_SWEEP}"

    restore_pristine "${RST_WRITER}"
    assert_gate_passes "${TEST_REPORT} accepts the report once every value is redacted whatever its size" \
        "${LOGS}/06b-rst-clean.log" "${TEST_REPORT},${TEST_SWEEP}"
}

control_event_log_redaction() {
    banner "6c gate item 6, event log: a payload written unredacted"
    # The third artefact.  A run that crashes leaves the log and no manifest at
    # all, so a leak here is the one most likely to survive on a real disk.
    save_pristine "${EVENT_LOG}"
    replace_once "event log redaction" "${EVENT_LOG}" \
        'nextSequence, clock.instant(), type, redactor.redactEnvironment(payload));' \
        'nextSequence, clock.instant(), type, payload);'
    assert_modified "event log redaction" "${EVENT_LOG}"

    local log="${LOGS}/06c-events-dirty.log"
    assert_gate_fails_matching "${TEST_SWEEP} finds the corpus in the event log on disk" \
        "corpus secret #[0-9]+ \(length [0-9]+\) survived into events.log" \
        "${log}" "${TEST_SWEEP}"
    assert_log_contains "the sweep also reports that the log carries no redaction marker at all" \
        "${log}" \
        "these artefacts contain no redaction marker at all"

    restore_pristine "${EVENT_LOG}"
    assert_gate_passes "${TEST_SWEEP} accepts the run directory once the log redacts again" \
        "${LOGS}/06c-events-clean.log" "${TEST_SWEEP}"
}

control_item7_delegation() {
    banner "7  gate item 7: the mutation gate, DELEGATED and enforced rather than assumed"
    # "PIT reports no surviving mutation in the hashing and redaction packages."
    # Proving that here would mean a second mutation run of the whole module --
    # twenty minutes -- and would create two things to keep in step with one
    # gate, which is the drift these scripts exist to prevent.
    # scripts/verify-test-gates.sh already injects a covered class whose test
    # asserts nothing and requires the mutation gate to reject it.  This
    # control therefore asserts that the delegation still has something to
    # delegate TO, and fails if it does not.
    assert_file_contains "the mutation control still exists in ${TEST_GATES}" \
        "${TEST_GATES}" "${TEST_GATES_MUTATION}"
    # And the half that is this module's own: a mutation switch that is SET but
    # INERT is exactly the failure this project keeps finding, so the switch is
    # asserted here and the run that proves it is live is the phase's own
    # mutation run, recorded in handoffs/PHASE-04-worklog.rst.
    assert_file_contains "cometgui-provenance's mutation switch is still on" \
        "${MODULE_POM}" "${MUTATION_SWITCH}"
}

control_harness_self_test() {
    banner "H  the harness itself: a control whose defect was not injected must not pass"
    # Every claim above has the form "I broke X and the gate noticed".  That
    # sentence is worthless unless a run in which nothing was broken is
    # REPORTED AS A FAILURE rather than as a pass.  So the harness is pointed
    # at a clean file and required to record a failure.
    local before="${FAILED}"
    local log="${LOGS}/H-not-injected.log"
    printf '   (deliberately running a control with NO defect injected)\n'
    assert_gate_fails "a control with no defect injected" \
        "this string is not in any log" \
        "${log}" "${TEST_VECTORS}"
    if [ "${FAILED}" -eq $(( before + 1 )) ]; then
        # Undo the deliberate failure: it was the expected outcome.
        FAILED="${before}"
        unset 'FAILURES[-1]'
        record_pass "the harness reports a control whose defect was never injected as a FAILURE, not as a pass"
    else
        record_fail "HARNESS FAILURE -- a control with no defect injected did not record a failure. Every other control in this script is unreliable."
    fi
}

main() {
    case "${1:-}" in
        -h|--help) usage; exit 0 ;;
        "") ;;
        *) usage >&2; die "unknown option: $1" 2 ;;
    esac

    cd -- "${ROOT}"
    command -v git >/dev/null || die "git is not on PATH; the sandbox is a git archive." 3
    [ -f "${ROOT}/tools/env.sh" ] || die "tools/env.sh is missing; run bash scripts/build.sh first." 3
    # shellcheck disable=SC1091
    . "${ROOT}/tools/env.sh"
    command -v mvn >/dev/null || die "mvn is not on PATH after sourcing tools/env.sh." 3
    [ -d "${M2REPO}" ] \
        || die "${M2REPO#"${ROOT}/"} does not exist; run bash scripts/build.sh first. Every control runs Maven offline." 3

    mkdir -p -- "${LOGS}"
    rm -f -- "${LOGS}"/*.log

    printf '===============================================================================\n'
    printf ' %s -- every PHASE-04 exit gate item must be seen to fail\n' "${SCRIPT_NAME}"
    printf '===============================================================================\n'
    printf '  repository   %s\n' "${ROOT}"
    printf '  logs         %s\n' "${LOGS#"${ROOT}/"}"
    printf '  each control %s\n' "$(gate_command '<the class that asserts the item>')"

    local started
    started="$(date +%s)"

    build_sandbox
    control_baseline
    control_known_vectors
    control_bounded_heap
    control_cache_attributes
    control_crash_recovery
    control_atomic_finalisation
    control_json_redaction
    control_rst_redaction
    control_event_log_redaction
    control_item7_delegation
    control_harness_self_test

    local total=$(( $(date +%s) - started ))
    printf '\n===============================================================================\n'
    printf ' SUMMARY: %d control(s) passed, %d failed, in %d seconds\n' \
        "${PASSED}" "${FAILED}" "${total}"
    printf ' Logs: %s\n' "${LOGS#"${ROOT}/"}"
    printf '===============================================================================\n'
    if [ "${FAILED}" -ne 0 ]; then
        printf '\n'
        printf '  %s\n' "${FAILURES[@]}"
        die "${FAILED} provenance-gate control(s) failed. A gate that cannot be seen to fail is not a gate." 1
    fi
    printf '\n  PHASE-04 exit gate items 1 to 6 were proved here; item 7 is proved by\n'
    printf '  %s, and this run failed if that harness had lost\n' "${TEST_GATES}"
    printf '  its mutation control or if this module had lost its mutation switch.\n'
    printf '\n  Every gate rejected its defect and accepted the clean tree.\n\n'
}

main "$@"
