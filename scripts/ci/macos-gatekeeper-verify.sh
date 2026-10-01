#!/usr/bin/env bash
#
# macos-gatekeeper-verify.sh -- PHASE-05 exit gate item 9, on a real Mac.
#
#   "On macOS, a freshly installed managed tool executes without a Gatekeeper
#    refusal."
#
# WHY THIS FILE EXISTS.  R-PLAT-04 requires the com.apple.quarantine extended
# attribute to be cleared from everything the tool cache will execute, and
# org.cometgui.install.cache.PlatformFixups is the product code that does it.
# This script is the only thing in the repository that can show, by
# execution on a Mac, whether it does -- and whether Gatekeeper then runs the
# binary, which is a separate question (gate item 9).
#
# WHAT THIS FILE HAS BEEN SEEN TO DO.  It ran on a Mac once, as
# macos-gatekeeper run 36918810975 (2026-10-01, at 49423fb, macos-latest,
# Apple silicon, Temurin 21.0.12.1).  Two findings: the Gatekeeper control did
# not bite (INCONCLUSIVE, exit 2), and PlatformFixups as it then was removed
# NOTHING -- /usr/bin/xattr -p read the attribute before and after it, because
# it used Java's UserDefinedFileAttributeView, which on macOS prefixes "user."
# to every name and so never reaches com.apple.quarantine (PlatformFixups'
# class comment cites the JDK source).  Phase 05 unit 14 changed the product to
# run /usr/bin/xattr through its process service, and added the ATTRIBUTE
# VERDICT below.  That change has NOT run on a Mac when this comment was
# written; the words verified, confirmed, proven and tested are not used of
# macOS anywhere in this repository, and this script's own verdict block says
# so on every path.
#
# THE ATTRIBUTE VERDICT IS INDEPENDENT OF THE GATEKEEPER CONTROL.  Whether
# Gatekeeper refuses a quarantined binary on a hosted runner is something the
# runner may simply not show, and when the control does not bite, everything
# about GATEKEEPER after it is uninterpretable.  The attribute is different:
# /usr/bin/xattr -p reads it directly, whatever Gatekeeper does.  So the run
# reaches TWO verdicts.  decide_attribute_verdict grades the product's fix-up
# against what xattr reads before and after it, and against what the product's
# own FixupReport claims; decide_verdict grades Gatekeeper as before.  An
# attribute failure exits 7 whatever the Gatekeeper verdict is.  That is a
# check that CAN go red on a hosted runner, which is the point of it.
#
# ============================================================================
# THE NEGATIVE CONTROL IS THE WHOLE POINT
# ============================================================================
#
# A `curl` download sets no com.apple.quarantine.  LaunchServices sets it --
# Safari, Mail, Messages, Archive Utility -- and a CI runner fetching bytes
# with a command-line tool produces an UNQUARANTINED file.  So a job that
# downloads a binary and runs it successfully proves nothing whatever: it would
# be just as green on a machine where the product's fix-up did nothing at all.
#
# This script therefore does four things IN THIS ORDER, and the order is the
# argument:
#
#   step 1  set com.apple.quarantine on the installed binary ITSELF, with
#           /usr/bin/xattr, and prove it is set by reading it back with xattr.
#           The JDK's own view is asked too and REPORTED: run 36918810975
#           showed it cannot see the attribute, and it grades nothing.
#   step 2  run the binary with the attribute STILL SET, and record exactly
#           what happens: exit status, signal, stderr.  THIS IS THE CONTROL.
#           It must bite.
#   step 3  run the product's fix-up -- PlatformFixups, through product code
#           and the product's own process service; this script never runs
#           `xattr -d` itself -- then read the attribute back with
#           `xattr -p` and grade the product's FixupReport against it.  That
#           is the ATTRIBUTE VERDICT, and it does not wait for step 2's
#           control to mean anything.
#   step 4  run the binary again, unchanged in every other respect, and
#           require Comet's own version banner and its comet.params.new.
#
# IF STEP 2 DOES NOT PRODUCE A REFUSAL, THE RESULT IS "THIS CHECK CANNOT GO
# RED", AND IT IS REPORTED AS EXACTLY THAT -- NEVER AS A PASS.  A hosted runner
# may not enforce Gatekeeper the way a clean end-user Mac does: no GUI login
# session, no Finder, different SIP and policy state, a path that is already
# trusted.  If that is what this job finds, that finding is worth more than a
# green tick, and decide_verdict() below returns INCONCLUSIVE for it.  See
# `--self-test` group B, case B2, which is the control that proves that branch
# is reachable.
#
# WHAT IS PRODUCT CODE AND WHAT IS NOT, stated plainly because the distinction
# is the evidence:
#
#   product   which platform this machine is (HostPlatform.of); which artefact
#             this machine gets (ArtefactManifest.select over
#             manifests/tools.json); whether the bytes are the pinned bytes
#             (StreamingHashService); THE QUARANTINE REMOVAL (PlatformFixups,
#             the class R-PLAT-04 names); whether Comet's own code was reached
#             (CometBanner).  All of it compiled here, from this checkout's
#             cometgui-* main sources, by scripts/ci/macos-gatekeeper/
#             MacosGatekeeperProbe.java.
#   not       the download, which is `curl` rather than the product's
#             HttpDownloader, and the install, which is a copy into
#             <cache>/bin/comet rather than a run of ArtefactInstaller's eight
#             steps.  Two reasons.  The ordering: the attribute must be present
#             and seen to bite BEFORE the fix-up runs, and a whole-pipeline
#             install runs the fix-up as its own step 5 in the same breath as
#             creating the directory.  And the honesty: the product's own
#             downloads are written by java.net.http and are NOT quarantined by
#             LaunchServices in the first place, so an end-to-end install would
#             have nothing to clear.  The install pipeline is covered by phase
#             05 unit 10's end-to-end install; what is covered HERE is the one
#             step that has never run on the platform it exists for.
#
# WHY A SHELL DRIVER, when the Windows one is Python.  That one is Python
# because `shell: bash` on a windows-latest runner is Git Bash: an MSYS
# environment that rewrites arguments that look like POSIX paths and is not
# bash.  macOS has neither problem -- it ships a real bash (3.2, so nothing
# below uses associative arrays, mapfile, or ${x,,}) and a real /usr/bin/xattr,
# which is the independent witness this check needs and which no Python module
# provides.  The logic that has to be TESTABLE -- classify_run and
# decide_verdict -- is two pure functions over observed values, exercised by
# `--self-test` against fixtures on this Linux machine.
#
# Usage:
#   bash scripts/ci/macos-gatekeeper-verify.sh                # macOS only
#   bash scripts/ci/macos-gatekeeper-verify.sh --check-only    # anywhere
#   bash scripts/ci/macos-gatekeeper-verify.sh --self-test     # anywhere
#   bash scripts/ci/macos-gatekeeper-verify.sh --help
#
# Needs network access for the real run (about 4 MB: comet.aarch64.macos.exe).
# --check-only and --self-test need none.
#
# Everything is written under _build/macos-gatekeeper/ (gitignored), and the
# transcript is printed to stdout as well, so the job log carries it whether or
# not the artifact upload runs.
#
# Exit status:
#   0  PASS: the control bit, the product's fix-up removed the attribute, and
#      the binary then executed and printed its own banner.  Also a clean
#      --check-only (which is NOT a pass: no macOS binary was executed) and a
#      clean --self-test.
#   1  NEGATIVE: the binary was refused while quarantined and the product's
#      fix-up did not fix it -- either the attribute survived the fix-up, or it
#      did not and the binary was still refused.  A real finding about
#      R-PLAT-04, meant to be loud.
#   2  INCONCLUSIVE: nothing was established.  The commonest shape, and the one
#      this script exists to be able to say: THE CONTROL DID NOT BITE -- the
#      quarantined binary ran -- so this check cannot go red on this machine.
#   3  HARNESS FAILURE: download, checksum, JDK, compilation or the attribute
#      itself failed.  Nothing was learned about Gatekeeper.
#   4  REFUSED: not a macOS host, and --check-only was not given.
#   5  MISUSE: bad arguments, or no usable JDK 17+ was found.
#   6  SELF-TEST FAILED: a control did not bite, or an undamaged case was
#      rejected.
#   7  ATTRIBUTE FAILED: independent of Gatekeeper, /usr/bin/xattr -p still
#      finds com.apple.quarantine after the product's fix-up, or the product's
#      FixupReport disagrees with what xattr observed (it claims a removal
#      xattr does not see, reports a failure xattr does not see, or does not
#      report a removal xattr does see).  Takes precedence over 1 and 2: it is
#      the one finding on this machine that does not depend on Gatekeeper.
#
# Exit 0 needs BOTH verdicts to pass; an attribute that is cleared under a
# Gatekeeper control that did not bite still exits 2.
#
# THIS IS NOT A STUB.  It deliberately does not use the project's
# unimplemented-step helper under scripts/ci/: run-pipeline-locally.sh
# classifies a step as unimplemented by grepping the script for that helper's
# file name, so even naming it here would make a real check be reported as a
# stub.  Hence the circumlocution.

set -Eeuo pipefail

SCRIPT_NAME="macos-gatekeeper-verify.sh"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

EXIT_PASS=0
EXIT_NEGATIVE=1
EXIT_INCONCLUSIVE=2
EXIT_HARNESS=3
EXIT_REFUSED=4
EXIT_MISUSE=5
EXIT_SELF_TEST_FAILED=6
EXIT_ATTRIBUTE_FAILED=7

WORK="${PROJECT_ROOT}/_build/macos-gatekeeper"
TRANSCRIPT="${WORK}/transcript.txt"
# Deliberately NOT under WORK: WORK is what the workflow uploads, and 330 kB
# of .class files would bury the transcript that is the actual evidence.
CLASSES="${PROJECT_ROOT}/_build/macos-gatekeeper-classes"
PROBE_SOURCE="scripts/ci/macos-gatekeeper/MacosGatekeeperProbe.java"
MANIFEST="manifests/tools.json"
WORKFLOW=".github/workflows/macos-gatekeeper.yml"

# The source roots the probe is compiled against: every module whose main
# sources the probe's imports reach.  Compiled with plain javac and no
# third-party jar, which is possible only because no cometgui-* module has a
# main-scope dependency outside the JDK and its siblings.
SOURCE_ROOTS="cometgui-domain/src/main/java:cometgui-install/src/main/java:cometgui-provenance/src/main/java:cometgui-process/src/main/java:cometgui-tools/src/main/java"

# The lowest Java release the product's main sources compile at.  The project
# builds at release 25 (pom.xml), but the closure this probe reaches compiles
# at 17, which is what a hosted macOS image can be expected to have.  A JDK
# below this is a harness failure, not a finding.
MINIMUM_JAVA=17

# How long the binary under test gets, in seconds.  A Gatekeeper refusal is
# immediate; `comet -p` on an idle runner is well under a second.
RUN_TIMEOUT=60

JAVA_BIN=""
JAVAC_BIN=""
TIMED_OUT=0
RUN_STATUS=0
VERDICT=""
VERDICT_WHY=""
ATTR_VERDICT=""
ATTR_VERDICT_WHY=""

# --------------------------------------------------------------- plumbing --

die() { printf '%s: %s\n' "${SCRIPT_NAME}" "$1" >&2; exit "${2:-${EXIT_MISUSE}}"; }

# Everything worth reading goes through here: stdout for the job log, and the
# transcript file for the upload.
say() {
    printf '%s\n' "$*"
    printf '%s\n' "$*" >>"${TRANSCRIPT}"
}

# Run a macOS-only tool and say plainly when it is not there, rather than
# letting the shell's own "No such file or directory" land in the transcript
# as if it were the tool's answer.
ask_tool() {
    local tool="$1"; shift
    if [ -x "${tool}" ]; then
        "${tool}" "$@" 2>&1 || true
    else
        printf 'NOT PRESENT on this machine (%s)' "${tool}"
    fi
}

rule() { say "------------------------------------------------------------------------"; }

# An assertion that NAMES THE VALUE IT OBSERVED.  A transcript that says "OK"
# is worthless; one that says what was read back is evidence.
observe() { say "    ${1} = ${2}"; }

usage() {
    sed -n '3,120p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

# ------------------------------------------------------------ the toolchain --

# Find a JDK that can compile the product sources.  On a macOS runner this is
# whatever the image ships (checked by RUNNING it, never by `command -v`); on
# this project's Linux host it is the pinned project-local JDK under tools/.
# No setup-java action: the CI contract forbids setup actions, and nothing is
# installed on the host.
usable_javac() {
    local candidate="$1"
    [ -x "${candidate}" ] || return 1
    "${candidate}" -version >/dev/null 2>&1 || return 1
    return 0
}

find_jdk() {
    # A newline-separated list rather than an array: bash 3.2 -- which is what
    # macOS ships -- errors on "${empty[@]}" under `set -u`.
    local candidates="" home=""
    [ -n "${JAVA_HOME:-}" ] && candidates="${candidates}${JAVA_HOME}
"
    # The project-local JDK, found without sourcing tools/env.sh: this script
    # must not silently inherit a toolchain it did not choose.
    for home in "${PROJECT_ROOT}"/tools/*jdk*; do
        [ -d "${home}" ] && candidates="${candidates}${home}
"
    done
    if [ -x /usr/libexec/java_home ]; then
        home="$(/usr/libexec/java_home -v "${MINIMUM_JAVA}+" 2>/dev/null || true)"
        [ -n "${home}" ] && candidates="${candidates}${home}
"
    fi
    # The GitHub macOS images export these; newest first.
    for home in "${JAVA_HOME_25_arm64:-}" "${JAVA_HOME_21_arm64:-}" \
                "${JAVA_HOME_17_arm64:-}" "${JAVA_HOME_25_X64:-}" \
                "${JAVA_HOME_21_X64:-}" "${JAVA_HOME_17_X64:-}"; do
        [ -n "${home}" ] && candidates="${candidates}${home}
"
    done

    while IFS= read -r home; do
        [ -n "${home}" ] || continue
        if usable_javac "${home}/bin/javac" && [ -x "${home}/bin/java" ]; then
            JAVAC_BIN="${home}/bin/javac"
            JAVA_BIN="${home}/bin/java"
            return 0
        fi
    done <<<"${candidates}"

    if command -v javac >/dev/null 2>&1 && command -v java >/dev/null 2>&1; then
        JAVAC_BIN="$(command -v javac)"
        JAVA_BIN="$(command -v java)"
        return 0
    fi
    return 1
}

compile_probe() {
    rm -rf -- "${CLASSES}"
    mkdir -p -- "${CLASSES}"
    say "    javac  = ${JAVAC_BIN}"
    local java_version=""
    java_version="$("${JAVA_BIN}" -version 2>&1 || true)"
    say "    java   = ${JAVA_BIN} (${java_version%%$'\n'*})"
    say "    source = ${PROBE_SOURCE}, against ${SOURCE_ROOTS//:/, }"
    if ! "${JAVAC_BIN}" --release "${MINIMUM_JAVA}" -nowarn -d "${CLASSES}" \
            -sourcepath "${SOURCE_ROOTS}" "${PROBE_SOURCE}" >>"${TRANSCRIPT}" 2>&1; then
        say "    FAILED to compile the probe against the product sources."
        tail -30 "${TRANSCRIPT}" >&2
        return 1
    fi
    # Exit code 0 proves nothing: check the output exists, and that the product
    # classes really were pulled in rather than resolved from somewhere else.
    [ -f "${CLASSES}/MacosGatekeeperProbe.class" ] \
        || { say "    FAILED: javac exited 0 and wrote no MacosGatekeeperProbe.class"; return 1; }
    [ -f "${CLASSES}/org/cometgui/install/cache/PlatformFixups.class" ] \
        || { say "    FAILED: javac exited 0 and did not compile PlatformFixups from the tree"; return 1; }
    local count
    count="$(find "${CLASSES}" -name '*.class' | wc -l | tr -d ' ')"
    observe "product classes compiled from this checkout" "${count}"
    return 0
}

# Run one probe command; its stdout lands in $1 and is echoed into the
# transcript.  Returns the probe's own exit status.
probe() {
    local out="$1"; shift
    local status=0
    "${JAVA_BIN}" -cp "${CLASSES}" MacosGatekeeperProbe "$@" >"${out}" 2>"${out}.err" || status=$?
    sed 's/^/    | /' "${out}" >>"${TRANSCRIPT}"
    sed 's/^/    | /' "${out}" || true
    if [ -s "${out}.err" ]; then
        sed 's/^/    ! /' "${out}.err" >>"${TRANSCRIPT}"
        sed 's/^/    ! /' "${out}.err" || true
    fi
    return "${status}"
}

# One value out of a probe's key=value output; empty if the key is absent.
kv() {
    local file="$1" key="$2"
    [ -f "${file}" ] || return 0
    awk -v k="probe.${key}=" 'index($0, k) == 1 { print substr($0, length(k) + 1); exit }' \
        "${file}"
}

# ------------------------------------------------------- running the binary --

# Run a command with a timeout, capturing both streams into one file.
# macOS has no /usr/bin/timeout, so this is done by hand.  Sets RUN_STATUS and
# TIMED_OUT; never fails the script, because a non-zero status IS the
# measurement here.
run_capture() {
    local out="$1" limit="$2" directory="$3"; shift 3
    local pid waited=0
    TIMED_OUT=0
    RUN_STATUS=0
    ( cd -- "${directory}" && exec "$@" ) >"${out}" 2>&1 &
    pid=$!
    while kill -0 "${pid}" 2>/dev/null; do
        if [ "${waited}" -ge "${limit}" ]; then
            kill -9 "${pid}" 2>/dev/null || true
            TIMED_OUT=1
            break
        fi
        sleep 1
        waited=$((waited + 1))
    done
    wait "${pid}" || RUN_STATUS=$?
    return 0
}

# ---------------------------------------------------------------------------
# classify_run -- the one piece of interpretation in this script, and the
# reason `--self-test` exists.  Given what was observed, say which of four
# things happened.  Pure: it prints "CLASS<TAB>why" and touches nothing.
#
#   classify_run STATUS TIMED_OUT BANNER_PRESENT OUTPUT_FILE
#
#     RAN      Comet's own version banner is in the output, so Comet's code was
#              reached.  Nothing refused it, whatever the exit status was.
#     ARCH     the kernel refused the ARCHITECTURE, not the signature: "Bad CPU
#              type in executable" is the Rosetta 2 question (D-004), not the
#              Gatekeeper one, and conflating them would put a translation
#              failure in the gate item's mouth.
#     REFUSED  killed by a signal, or exit 126, or the system said one of the
#              things it says when it refuses to run code it does not trust.
#     OTHER    anything else, including a timeout.  Never a pass, never a
#              refusal: an unexplained failure is exactly what INCONCLUSIVE is
#              for.
#
# Order matters and is asserted by the self-test: BANNER wins over everything
# (case A7 is a SIGKILL whose output carries the banner, which is a binary that
# ran and was then killed for some other reason -- not a Gatekeeper refusal),
# and ARCH wins over REFUSED (case A4 is exit 126 with "Bad CPU type", which a
# status-only rule would call a refusal).
# ---------------------------------------------------------------------------
classify_run() {
    local status="$1" timed_out="$2" banner="$3" out="$4"
    local class reason

    if [ "${banner}" = "true" ]; then
        class="RAN"
        reason="Comet's own version banner is in the output, so Comet's code ran; exit status ${status}"
    elif [ "${timed_out}" = "1" ]; then
        class="OTHER"
        reason="still running after ${RUN_TIMEOUT}s and killed by this script; no banner"
    elif [ -f "${out}" ] && grep -a -i -q -e 'bad cpu type' -e 'exec format error' \
            -e 'cannot execute binary file' "${out}"; then
        class="ARCH"
        reason="the kernel refused the architecture, not the signature (see the output); exit status ${status}"
    elif [ "${status}" -gt 128 ]; then
        class="REFUSED"
        reason="killed by signal $((status - 128)) (exit status ${status}) with no banner"
    elif [ "${status}" -eq 126 ]; then
        class="REFUSED"
        reason="exit status 126: the file exists and could not be executed"
    elif [ -f "${out}" ] && grep -a -i -q -e 'killed: 9' -e 'operation not permitted' \
            -e 'cannot be opened because' -e 'developer cannot be verified' \
            -e 'malicious software' -e 'code signature' -e 'gatekeeper' \
            -e 'quarantine' "${out}"; then
        class="REFUSED"
        reason="the system said so in the output; exit status ${status}"
    else
        class="OTHER"
        reason="exit status ${status}, no banner, and nothing in the output that names a refusal"
    fi
    # CLASS<TAB>why.  One line, because a shell function that set a global
    # would lose it: every caller reads this through a command substitution,
    # which is a subshell.
    printf '%s\t%s\n' "${class}" "${reason}"
}

# The two halves of what classify_run printed.
class_of() { printf '%s\n' "${1%%$'\t'*}"; }
why_of()   { printf '%s\n' "${1#*$'\t'}"; }

# ---------------------------------------------------------------------------
# decide_verdict -- the whole result, from the observed values, in one place so
# that `--self-test` can drive every branch without a Mac.  Sets VERDICT and
# VERDICT_WHY and RETURNS THE SCRIPT'S EXIT STATUS.
#
#   decide_verdict HOST_SOURCE HOST_PLATFORM ATTR_SET CONTROL ATTR_GONE_XATTR \
#                  ATTR_GONE_JAVA FIXUP_LISTED POST POST_CLEAN
#
# The order of the tests is the argument of the whole unit:
#
#   * a verdict about a Mac may not rest on a host this script was TOLD about;
#   * if the attribute could not be set, there was never anything to refuse,
#     and everything after it is meaningless;
#   * if the control did not bite, NOTHING IS ESTABLISHED -- that is the
#     "cannot go red" branch, and it is reported as INCONCLUSIVE, never as a
#     pass, however green everything after it looks;
#   * only then may a removal and a successful run mean anything.
# ---------------------------------------------------------------------------
decide_verdict() {
    local host_source="$1" host_platform="$2" attr_set="$3" control="$4" \
          gone_xattr="$5" gone_java="$6" fixup_listed="$7" post="$8" post_clean="$9"

    if [ "${host_source}" != "system-properties" ]; then
        VERDICT="HARNESS FAILURE -- THE HOST WAS SUPPLIED, NOT READ"
        VERDICT_WHY="the host was SUPPLIED to the product (host.source=${host_source}) rather than read from this machine. A verdict about macOS may not rest on a host this script was told about; that switch exists for the Linux self-test and for nothing else."
        return "${EXIT_HARNESS}"
    fi
    case "${host_platform}" in
        macos-*) ;;
        *)
            VERDICT="HARNESS FAILURE -- NOT A MAC"
            VERDICT_WHY="the product read this machine as ${host_platform}, which is not a Mac. Nothing below says anything about Gatekeeper."
            return "${EXIT_HARNESS}" ;;
    esac
    if [ "${attr_set}" != "yes" ]; then
        VERDICT="HARNESS FAILURE -- THE ATTRIBUTE WAS NEVER SET"
        VERDICT_WHY="com.apple.quarantine could not be set on the installed binary and read back, so there was never anything for Gatekeeper to refuse and the control below could not have bitten for the right reason."
        return "${EXIT_HARNESS}"
    fi
    if [ "${control}" = "RAN" ]; then
        VERDICT="INCONCLUSIVE -- THIS CHECK CANNOT GO RED ON THIS MACHINE"
        VERDICT_WHY="the control did not bite: the binary carrying com.apple.quarantine RAN, and printed Comet's own banner. Gatekeeper did not refuse it, so this machine cannot distinguish a working fix-up from one that does nothing, and everything after step 2 is uninterpretable. A hosted runner is not a clean end-user Mac: no GUI login session, no Finder, different SIP and policy state, and a path that may already be trusted. THIS IS NOT A PASS and gate item 9 is NOT met by it."
        return "${EXIT_INCONCLUSIVE}"
    fi
    if [ "${control}" = "ARCH" ]; then
        VERDICT="INCONCLUSIVE -- THE CONTROL FAILED ON THE ARCHITECTURE"
        VERDICT_WHY="the control failed on the ARCHITECTURE rather than on the signature, so it says nothing about Gatekeeper. That is the Rosetta 2 question (D-004), and the artefact the product selected for this machine should not have raised it."
        return "${EXIT_INCONCLUSIVE}"
    fi
    if [ "${control}" != "REFUSED" ]; then
        VERDICT="INCONCLUSIVE -- THE CONTROL NEITHER RAN NOR WAS REFUSED"
        VERDICT_WHY="the control neither ran nor was refused (${control}); the binary did not get far enough for anything after it to mean anything."
        return "${EXIT_INCONCLUSIVE}"
    fi
    if [ "${gone_xattr}" != "yes" ] || [ "${gone_java}" != "yes" ]; then
        VERDICT="NEGATIVE -- THE FIX-UP DID NOT REMOVE THE ATTRIBUTE"
        VERDICT_WHY="the control bit and the product's fix-up did NOT remove the attribute: /usr/bin/xattr says gone=${gone_xattr}, the product's own UserDefinedFileAttributeView says gone=${gone_java}. R-PLAT-04 is not satisfied on this platform by PlatformFixups as written, and a clean Mac would meet the Gatekeeper dialog the rule exists to prevent."
        return "${EXIT_NEGATIVE}"
    fi
    if [ "${fixup_listed}" != "yes" ]; then
        VERDICT="INCONCLUSIVE -- THE REMOVAL CANNOT BE ATTRIBUTED TO THE PRODUCT"
        VERDICT_WHY="the attribute is gone, and the product did not report removing it: FixupReport.quarantineCleared() did not name the installed executable. Something removed it and the product cannot be said to have done so."
        return "${EXIT_INCONCLUSIVE}"
    fi
    if [ "${post}" != "RAN" ]; then
        VERDICT="NEGATIVE -- CLEARING THE ATTRIBUTE WAS NOT ENOUGH"
        VERDICT_WHY="the attribute was removed by the product and the binary STILL did not run (${post}). Clearing com.apple.quarantine is not sufficient on this machine, which is a finding about R-PLAT-04's premise."
        return "${EXIT_NEGATIVE}"
    fi
    if [ "${post_clean}" != "yes" ]; then
        VERDICT="INCONCLUSIVE -- THE RUN AFTER THE FIX-UP WAS NOT CLEAN"
        VERDICT_WHY="Comet's banner appeared after the fix-up, so nothing refused it, but the run was not clean: it did not exit 0 and write comet.params.new. The Gatekeeper question looks answered and the run itself is not trustworthy enough to call this a pass."
        return "${EXIT_INCONCLUSIVE}"
    fi
    VERDICT="PASS"
    VERDICT_WHY="the quarantined binary was REFUSED (the control bit), the product's own PlatformFixups removed com.apple.quarantine (both witnesses agree, and FixupReport named the file), and the same binary then ran, printed Comet's version banner and wrote comet.params.new."
    return "${EXIT_PASS}"
}

# ---------------------------------------------------------------------------
# xattr_says_gone OUTPUT STATUS -- the ONE observed value the attribute
# verdict's pass rests on: does `/usr/bin/xattr -p com.apple.quarantine FILE`
# say the attribute is absent?  Pure: prints "yes" or "no".
#
#   OUTPUT  everything xattr -p printed, both streams
#   STATUS  its exit status, CAPTURED rather than discarded with `|| true`
#
# "yes" needs BOTH a non-zero exit AND a line ending in macOS's own wording,
# "No such xattr: com.apple.quarantine" (its form on macOS is
# "xattr: FILE: No such xattr: NAME"; the FILE part varies, so the match is
# anchored on the attribute name at the end of the line, tolerating only
# trailing white space).  Everything else is "no":
#   * a printed value with exit 0 is the attribute, present;
#   * EMPTY output is "not shown gone", never "gone": an xattr that failed
#     silently -- killed, missing, or redirected away -- prints exactly that,
#     and "I saw nothing" is not evidence of absence;
#   * any other error ("No such file", "Operation not permitted") says
#     nothing about the attribute;
#   * "No such xattr" for a DIFFERENT name is about that name;
#   * the right wording with exit 0 contradicts itself, and is not believed.
# Group H of --self-test drives every one of these.
# ---------------------------------------------------------------------------
xattr_says_gone() {
    local output="$1" status="$2"
    case "${status}" in
        ""|*[!0-9]*) printf 'no\n'; return 0 ;;
    esac
    if [ "${status}" -ne 0 ] && printf '%s\n' "${output}" \
            | grep -q -E ': No such xattr: com\.apple\.quarantine[[:space:]]*$'; then
        printf 'yes\n'
    else
        printf 'no\n'
    fi
}

# xattr_lists_quarantine LISTING -- step 1's decision: does the output of
# `/usr/bin/xattr FILE` (one attribute name per line) name com.apple.quarantine?
# Pure: prints "yes" or "no".  A WHOLE line must be the name, so
# com.apple.quarantine.other or a "No such file" message never counts.
xattr_lists_quarantine() {
    if printf '%s\n' "$1" | grep -q -x -F 'com.apple.quarantine'; then
        printf 'yes\n'
    else
        printf 'no\n'
    fi
}

# ---------------------------------------------------------------------------
# decide_attribute_verdict -- R-PLAT-04 alone, graded by /usr/bin/xattr, and
# INDEPENDENT of whether the Gatekeeper control bit.  Sets ATTR_VERDICT and
# ATTR_VERDICT_WHY and RETURNS AN EXIT STATUS: 0, EXIT_HARNESS or
# EXIT_ATTRIBUTE_FAILED.
#
#   decide_attribute_verdict ATTR_SET GONE_XATTR PRODUCT_CLEARED \
#                            PRODUCT_NOT_CLEARED PRODUCT_OUTCOME
#
#     ATTR_SET             xattr listed the attribute before the fix-up (yes/no)
#     GONE_XATTR           xattr -p no longer finds it after the fix-up (yes/no)
#     PRODUCT_CLEARED      FixupReport.quarantineCleared() names the file
#     PRODUCT_NOT_CLEARED  FixupReport.quarantineNotCleared() names the file
#     PRODUCT_OUTCOME      the probe's fixup.outcome, for the words only
#
# Every failure branch has its own words, and the order is arranged so that
# deleting any one branch changes the verdict a --self-test case reads (group
# E), rather than reaching the same words by another route.  The fourth branch
# is the exact shape of run 36918810975: present before, present after, and
# the product reporting nothing.
# ---------------------------------------------------------------------------
decide_attribute_verdict() {
    local attr_set="$1" gone="$2" cleared="$3" not_cleared="$4" outcome="${5:-<no answer>}"

    if [ "${attr_set}" != "yes" ]; then
        ATTR_VERDICT="ATTRIBUTE NOT GRADED -- XATTR NEVER SAW IT SET"
        ATTR_VERDICT_WHY="/usr/bin/xattr did not list com.apple.quarantine on the installed binary before the fix-up, so there was nothing for the product to remove and its fix-up cannot be graded."
        return "${EXIT_HARNESS}"
    fi
    if [ "${gone}" != "yes" ] && [ "${cleared}" = "yes" ]; then
        ATTR_VERDICT="ATTRIBUTE FAILED -- THE PRODUCT CLAIMS A REMOVAL /usr/bin/xattr DOES NOT SEE"
        ATTR_VERDICT_WHY="FixupReport.quarantineCleared() names the installed binary, and /usr/bin/xattr -p still reads com.apple.quarantine on it after the fix-up. The product's report is false. R-PLAT-04 is not delivered, whatever Gatekeeper did."
        return "${EXIT_ATTRIBUTE_FAILED}"
    fi
    if [ "${gone}" != "yes" ] && [ "${not_cleared}" = "yes" ]; then
        ATTR_VERDICT="ATTRIBUTE FAILED -- IT SURVIVED THE FIX-UP, AND THE PRODUCT SAID SO"
        ATTR_VERDICT_WHY="/usr/bin/xattr -p still reads com.apple.quarantine after the fix-up. The product reported the file as NOT cleared (fixup.outcome=${outcome}; its reason is in the probe output above), so it failed honestly -- but R-PLAT-04 is not delivered, whatever Gatekeeper did."
        return "${EXIT_ATTRIBUTE_FAILED}"
    fi
    if [ "${gone}" != "yes" ]; then
        ATTR_VERDICT="ATTRIBUTE FAILED -- IT SURVIVED THE FIX-UP, AND THE PRODUCT REPORTED NOTHING"
        ATTR_VERDICT_WHY="/usr/bin/xattr -p still reads com.apple.quarantine after the fix-up, and the product's report named the file neither cleared nor not cleared (fixup.outcome=${outcome}). That is the silent no-op macos-gatekeeper run 36918810975 found. R-PLAT-04 is not delivered, whatever Gatekeeper did."
        return "${EXIT_ATTRIBUTE_FAILED}"
    fi
    if [ "${not_cleared}" = "yes" ]; then
        ATTR_VERDICT="ATTRIBUTE FAILED -- THE PRODUCT REPORTS A FAILURE /usr/bin/xattr DOES NOT SEE"
        ATTR_VERDICT_WHY="/usr/bin/xattr -p no longer finds com.apple.quarantine, and FixupReport.quarantineNotCleared() names the installed binary anyway (fixup.outcome=${outcome}). The product's account disagrees with what xattr observed, so it cannot be trusted either way."
        return "${EXIT_ATTRIBUTE_FAILED}"
    fi
    if [ "${cleared}" != "yes" ]; then
        ATTR_VERDICT="ATTRIBUTE FAILED -- GONE, BUT THE PRODUCT DID NOT REPORT REMOVING IT"
        ATTR_VERDICT_WHY="/usr/bin/xattr saw com.apple.quarantine before the fix-up and not after it, and FixupReport.quarantineCleared() does not name the installed binary (fixup.outcome=${outcome}). Something removed it, and the product's report does not say the product did."
        return "${EXIT_ATTRIBUTE_FAILED}"
    fi
    ATTR_VERDICT="ATTRIBUTE CLEARED -- /usr/bin/xattr AND THE PRODUCT'S REPORT AGREE"
    ATTR_VERDICT_WHY="/usr/bin/xattr listed com.apple.quarantine before the product's fix-up and xattr -p does not find it after, and FixupReport.quarantineCleared() names the installed binary. This is ONE observation on ONE hosted macOS image, and it says nothing about whether Gatekeeper then runs the binary: that is the other verdict."
    return "${EXIT_PASS}"
}

# combine_exits GATEKEEPER_STATUS ATTRIBUTE_STATUS -- the script's exit status.
# An attribute failure wins: it is the one finding that does not depend on
# Gatekeeper.  Otherwise the Gatekeeper verdict decides, and only when that is
# a pass does an ungraded attribute (EXIT_HARNESS) surface.
combine_exits() {
    local gate="$1" attribute="$2"
    if [ "${attribute}" -eq "${EXIT_ATTRIBUTE_FAILED}" ]; then
        printf '%s\n' "${EXIT_ATTRIBUTE_FAILED}"
    elif [ "${gate}" -ne "${EXIT_PASS}" ]; then
        printf '%s\n' "${gate}"
    else
        printf '%s\n' "${attribute}"
    fi
}

# names_value FILE LIST VALUE -- "yes" if the probe output in FILE carries
# probe.LIST.<n>=VALUE for some index n, "no" otherwise.  Only the indexed
# entries count: probe.LIST.count and probe.LIST.<n>.reason are not entries.
names_value() {
    local file="$1" list="$2" value="$3"
    if [ -f "${file}" ] && awk -v prefix="probe.${list}." -v want="${value}" '
            index($0, prefix) == 1 {
                rest = substr($0, length(prefix) + 1)
                eq = index(rest, "=")
                if (eq > 1 && substr(rest, 1, eq - 1) ~ /^[0-9]+$/ &&
                        substr(rest, eq + 1) == want) { found = 1 }
            }
            END { exit (found ? 0 : 1) }' "${file}"; then
        printf 'yes\n'
    else
        printf 'no\n'
    fi
}

# conclude GATE_STATUS ATTRIBUTE_STATUS -- prints both verdicts, the attribute
# first, and returns the combined exit status.  The real run ends here, and so
# does --self-test case F7, which is what proves the attribute verdict reaches
# the transcript and the exit status rather than only being computed.
conclude() {
    local gate="$1" attribute="$2" status
    status="$(combine_exits "${gate}" "${attribute}")"
    attribute_block "${attribute}"
    verdict_block "${gate}"
    say "EXIT STATUS: ${status} (Gatekeeper verdict exit ${gate}, attribute verdict exit ${attribute})"
    return "${status}"
}

attribute_block() {
    local status="$1"
    say ""
    rule
    say "ATTRIBUTE VERDICT: ${ATTR_VERDICT}  (exit ${status})"
    say "  graded by /usr/bin/xattr, independent of the Gatekeeper control below"
    say ""
    printf '%s\n' "${ATTR_VERDICT_WHY}" | fold -s -w 72 | sed 's/^/  /' >>"${TRANSCRIPT}"
    printf '%s\n' "${ATTR_VERDICT_WHY}" | fold -s -w 72 | sed 's/^/  /' || true
    if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
        {
            printf '### macos-gatekeeper attribute: %s\n\n' "${ATTR_VERDICT}"
            printf '%s\n\n' "${ATTR_VERDICT_WHY}"
        } >>"${GITHUB_STEP_SUMMARY}" 2>/dev/null || true
    fi
}

# ============================================================================
# --check-only
# ============================================================================

check_only() {
    mkdir -p -- "${WORK}"
    : >"${TRANSCRIPT}"
    say "${SCRIPT_NAME} --check-only"
    say "$(date -u '+%Y-%m-%dT%H:%M:%SZ') on $(uname -s) $(uname -m)"
    rule
    say "WHAT THIS MODE IS. Everything that can be done without a Mac: the probe"
    say "compiles against the product's own sources, and the product is asked"
    say "which artefact a Mac would be given. NO macOS BINARY IS EXECUTED, no"
    say "com.apple.quarantine attribute is set by anything, and NOTHING BELOW IS"
    say "EVIDENCE ABOUT GATEKEEPER. Gate item 9 cannot be met by this mode."
    rule

    say "[1] the toolchain"
    find_jdk || die "no usable JDK ${MINIMUM_JAVA}+ found (tried JAVA_HOME, tools/, /usr/libexec/java_home, JAVA_HOME_*_arm64, PATH)" "${EXIT_MISUSE}"
    compile_probe || return "${EXIT_HARNESS}"

    say ""
    say "[2] this machine, as the PRODUCT reads it"
    probe "${WORK}/host.txt" host || true
    local platform
    platform="$(kv "${WORK}/host.txt" 'host.platform')"
    observe "product's verdict on this machine" "${platform}"

    say ""
    say "[3] the artefact a Mac would be given, chosen by ArtefactManifest.select"
    say "    (the host is SUPPLIED here -- this machine is not a Mac -- so the"
    say "     product parses 'Mac OS X'/'aarch64' rather than reading them)"
    if ! probe "${WORK}/select.txt" select --manifest "${MANIFEST}" --tool comet \
            --os-name "Mac OS X" --os-arch aarch64; then
        say "    the manifest offers an Apple silicon Mac no Comet at all"
        return "${EXIT_HARNESS}"
    fi
    observe "chosen artefact" "$(kv "${WORK}/select.txt" 'select.0.url')"
    observe "its sha256 in manifests/tools.json" "$(kv "${WORK}/select.txt" 'select.0.sha256')"
    observe "executability" "$(kv "${WORK}/select.txt" 'select.0.executability')"

    say ""
    say "[4] the Percolator rows for the same machine, for context only (D-004)"
    probe "${WORK}/select-percolator.txt" select --manifest "${MANIFEST}" --tool percolator \
        --os-name "Mac OS X" --os-arch aarch64 || true

    say ""
    say "[5] the workflow that can put this script on a Mac"
    if [ -f "${PROJECT_ROOT}/${WORKFLOW}" ]; then
        observe "${WORKFLOW}" "present"
        if grep -q "bash scripts/ci/${SCRIPT_NAME}" "${PROJECT_ROOT}/${WORKFLOW}"; then
            observe "it names this script" "yes"
        else
            observe "it names this script" "NO -- the workflow and this script have drifted"
            return "${EXIT_HARNESS}"
        fi
    else
        observe "${WORKFLOW}" "MISSING"
        return "${EXIT_HARNESS}"
    fi

    rule
    say "--check-only COMPLETE, AND IT PROVES NOTHING ABOUT GATEKEEPER."
    say "No macOS binary has been executed here. On a Mac, Comet has run once (run"
    say "36918810975), under a Gatekeeper control that did not bite."
    say "What this says is only that the probe compiles against the product's"
    say "sources and that manifests/tools.json offers an Apple silicon Mac a"
    say "native Comet. Gate item 9 stays UNMET until a macOS runner has executed"
    say "the four steps with a control that bit."
    return "${EXIT_PASS}"
}

# ============================================================================
# the real run
# ============================================================================

real_run() {
    mkdir -p -- "${WORK}"
    : >"${TRANSCRIPT}"
    local cache="${WORK}/cache"
    local rundir="${WORK}/run"
    rm -rf -- "${cache}" "${rundir}"
    mkdir -p -- "${cache}/bin" "${rundir}/control" "${rundir}/after"

    say "${SCRIPT_NAME} -- PHASE-05 exit gate item 9"
    say "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    rule
    say "[0] this machine"
    say "    uname  : $(uname -a)"
    if [ -x /usr/bin/sw_vers ]; then
        say "    sw_vers: $(/usr/bin/sw_vers -productName) $(/usr/bin/sw_vers -productVersion) ($(/usr/bin/sw_vers -buildVersion))"
    fi
    say "    arch   : $(/usr/bin/arch 2>/dev/null || uname -m)"
    say "    hw     : $(/usr/sbin/sysctl -n machdep.cpu.brand_string 2>/dev/null || echo unknown)"
    # Named rather than assumed: a machine without spctl is not a machine whose
    # Gatekeeper state is "on", it is one this script could not ask.
    say "    spctl  : $(ask_tool /usr/sbin/spctl --status)"
    say "    csrutil: $(ask_tool /usr/bin/csrutil status)"
    say "    xattr  : $(command -v xattr || echo 'MISSING')"
    say "    runner : ${RUNNER_NAME:-not a GitHub runner} ${RUNNER_ARCH:-} ${RUNNER_OS:-}"

    say ""
    say "[0b] Rosetta 2 -- REPORTED, NOT GATED (D-004)"
    say "     D-004 says the application must detect Rosetta 2 and explain its"
    say "     absence, and no machine in this project could ever say anything"
    say "     factual about it until now. The gate item does not depend on any"
    say "     of this: the artefact under test is a NATIVE aarch64 Comet."
    local rosetta_dir="absent" rosetta_run="not attempted" oahd="absent"
    if [ -d /Library/Apple/usr/libexec/oah ]; then
        rosetta_dir="present (/Library/Apple/usr/libexec/oah)"
    fi
    if pgrep oahd >/dev/null 2>&1; then oahd="running"; fi
    if [ -x /usr/bin/arch ]; then
        if /usr/bin/arch -x86_64 /usr/bin/true >/dev/null 2>&1; then
            rosetta_run="an x86-64 process started under /usr/bin/arch -x86_64"
        else
            rosetta_run="/usr/bin/arch -x86_64 /usr/bin/true FAILED -- no Rosetta 2 here"
        fi
    fi
    observe "Rosetta 2 support directory" "${rosetta_dir}"
    observe "oahd (the Rosetta daemon)" "${oahd}"
    observe "x86-64 execution" "${rosetta_run}"

    say ""
    say "[1] the toolchain, and the product code this check is about"
    find_jdk || die "no usable JDK ${MINIMUM_JAVA}+ found on this Mac (tried JAVA_HOME, /usr/libexec/java_home, JAVA_HOME_*_arm64, PATH). Nothing is installed by this script." "${EXIT_MISUSE}"
    compile_probe || return "${EXIT_HARNESS}"

    say ""
    say "[2] this machine, as the PRODUCT reads it"
    probe "${WORK}/host.txt" host || true
    local host_source host_platform
    host_source="$(kv "${WORK}/host.txt" 'host.source')"
    host_platform="$(kv "${WORK}/host.txt" 'host.platform')"
    observe "HostPlatform.of(os.name, os.arch)" "${host_platform}"
    observe "where those two strings came from" "${host_source}"

    say ""
    say "[3] the artefact THE PRODUCT selects for this machine"
    if ! probe "${WORK}/select.txt" select --manifest "${MANIFEST}" --tool comet; then
        say "    the product offers this machine no Comet it can run; nothing to test."
        VERDICT="HARNESS FAILURE"
        VERDICT_WHY="ArtefactManifest.select returned no runnable Comet for ${host_platform}."
        verdict_block "${EXIT_HARNESS}"
        return "${EXIT_HARNESS}"
    fi
    local url sha256 size install_path
    url="$(kv "${WORK}/select.txt" 'select.0.url')"
    sha256="$(kv "${WORK}/select.txt" 'select.0.sha256')"
    size="$(kv "${WORK}/select.txt" 'select.0.sizeBytes')"
    install_path="$(kv "${WORK}/select.txt" 'select.0.executablePath')"
    observe "url" "${url}"
    observe "manifest sha256" "${sha256}"
    observe "executability" "$(kv "${WORK}/select.txt" 'select.0.executability')"
    observe "installed as" "${install_path}"

    say ""
    say "[4] the Percolator rows for this machine, for context only (D-004)"
    probe "${WORK}/select-percolator.txt" select --manifest "${MANIFEST}" --tool percolator || true

    local binary="${cache}/${install_path}"
    mkdir -p -- "$(dirname -- "${binary}")"

    say ""
    say "[5] download (curl, NOT the product's downloader -- see the header)"
    if ! curl --fail --silent --show-error --location --retry 3 --max-time 300 \
            --output "${binary}" -- "${url}" >>"${TRANSCRIPT}" 2>&1; then
        say "    the download failed; nothing was learned about Gatekeeper."
        VERDICT="HARNESS FAILURE"
        VERDICT_WHY="curl could not fetch ${url}."
        verdict_block "${EXIT_HARNESS}"
        return "${EXIT_HARNESS}"
    fi
    observe "bytes on disk" "$(wc -c <"${binary}" | tr -d ' ') (manifest says ${size})"

    say ""
    say "[6] are these the pinned bytes? asked of the PRODUCT's own hasher"
    if ! probe "${WORK}/verify.txt" verify --file "${binary}" --manifest "${MANIFEST}" --tool comet; then
        say "    StreamingHashService says these are not the bytes the manifest pins."
        VERDICT="HARNESS FAILURE"
        VERDICT_WHY="the downloaded file does not match manifests/tools.json; the binary was never executed."
        verdict_block "${EXIT_HARNESS}"
        return "${EXIT_HARNESS}"
    fi
    observe "sha256 observed" "$(kv "${WORK}/verify.txt" 'verify.observed.sha256')"
    observe "md5 observed" "$(kv "${WORK}/verify.txt" 'verify.observed.md5')"
    observe "product accepted the bytes" "$(kv "${WORK}/verify.txt" 'verify.match')"

    # THE EXECUTE BIT IS SET BY HAND, HERE, ON PURPOSE.  R-PLAT-05's half of
    # PlatformFixups would set it in step 9 -- but then the CONTROL in step 8
    # would fail because the file is not executable, which is not the refusal
    # this job is about and would be a control that bit for the wrong reason.
    # The execute-bit fix-up is covered by PlatformFixupsTest; what is under
    # test here is the quarantine attribute alone.
    chmod 755 -- "${binary}"
    observe "mode set by this script before the control" "$(ls -l -- "${binary}" | awk '{print $1}')"

    say ""
    rule
    say "[7] STEP 1 OF 4 -- set com.apple.quarantine, and prove it is set"
    say "    LaunchServices sets this attribute; curl does not. So this script"
    say "    sets it, with the value shape LaunchServices writes, and reads it"
    say "    back with two independent witnesses."
    local quarantine_value="0081;$(printf '%x' "$(date +%s)");CometGUI-gate-item-9;$(uuidgen 2>/dev/null || echo 00000000-0000-0000-0000-000000000000)"
    local attr_set="no"
    if [ ! -x /usr/bin/xattr ]; then
        say "    /usr/bin/xattr is missing on this machine; it is macOS's own tool"
        say "    and this check needs it as the independent witness."
    elif /usr/bin/xattr -w com.apple.quarantine "${quarantine_value}" -- "${binary}" 2>>"${TRANSCRIPT}"; then
        local read_back names listing
        read_back="$(/usr/bin/xattr -p com.apple.quarantine -- "${binary}" 2>>"${TRANSCRIPT}" || true)"
        listing="$(/usr/bin/xattr -- "${binary}" 2>>"${TRANSCRIPT}" || true)"
        names="$(printf '%s' "${listing}" | tr '\n' ' ')"
        observe "xattr -w wrote" "${quarantine_value}"
        observe "xattr -p read back" "${read_back:-<nothing>}"
        observe "xattr lists" "${names:-<nothing>}"
        probe "${WORK}/attrs-before.txt" attrs --file "${binary}" || true
        local java_sees
        java_sees="$(kv "${WORK}/attrs-before.txt" 'attrs.quarantine.present')"
        observe "the PRODUCT's own Java view sees it" "${java_sees}"
        observe "and reads its value as" "$(kv "${WORK}/attrs-before.txt" 'attrs.quarantine.value')"
        # PRESENCE decides this, not an exact round trip of the bytes: what the
        # control needs is an attribute for Gatekeeper to refuse.  The value is
        # compared too, and reported, because a value macOS rewrote on the way
        # in would be worth knowing about -- but it does not make the step fail.
        attr_set="$(xattr_lists_quarantine "${listing}")"
        if [ "${read_back}" = "${quarantine_value}" ]; then
            observe "the value survived the round trip" "yes"
        else
            observe "the value survived the round trip" \
                "NO -- wrote \"${quarantine_value}\", read back \"${read_back}\""
        fi
        if [ "${java_sees}" != "true" ]; then
            say "    NOTE: /usr/bin/xattr can see the attribute and Java's"
            say "    UserDefinedFileAttributeView cannot. That is EXPECTED since run"
            say "    36918810975: the JDK's macOS view prefixes \"user.\" to every name"
            say "    (PlatformFixups' class comment cites the source), and PlatformFixups"
            say "    no longer uses that view. It is reported, and grades nothing."
        fi
    else
        say "    xattr -w FAILED; see the transcript above."
    fi
    observe "step 1 verdict: the attribute is present on the installed binary" "${attr_set}"

    say ""
    rule
    say "[8] STEP 2 OF 4 -- THE CONTROL. Run it while it is still quarantined."
    say "    If this does not produce a refusal, this check cannot go red on"
    say "    this machine, and the run is reported as INCONCLUSIVE."
    run_capture "${WORK}/control-run.txt" "${RUN_TIMEOUT}" "${rundir}/control" "${binary}" -p
    local control_status="${RUN_STATUS}" control_timed_out="${TIMED_OUT}"
    observe "command" "${binary} -p (cwd ${rundir}/control)"
    observe "exit status" "${control_status}"
    if [ "${control_status}" -gt 128 ]; then
        observe "killed by signal" "$((control_status - 128))"
    fi
    observe "timed out" "${control_timed_out}"
    observe "comet.params.new written" "$([ -f "${rundir}/control/comet.params.new" ] && echo yes || echo no)"
    say "    output, verbatim:"
    sed 's/^/    > /' "${WORK}/control-run.txt" | head -40 >>"${TRANSCRIPT}"
    sed 's/^/    > /' "${WORK}/control-run.txt" | head -40 || true
    probe "${WORK}/control-banner.txt" banner --file "${WORK}/control-run.txt" || true
    local control_banner control
    control_banner="$(kv "${WORK}/control-banner.txt" 'banner.present')"
    observe "Comet's own banner present (product's CometBanner)" "${control_banner}"
    local control_line
    control_line="$(classify_run "${control_status}" "${control_timed_out}" "${control_banner}" "${WORK}/control-run.txt")"
    control="$(class_of "${control_line}")"
    observe "CONTROL CLASSIFIED AS" "${control}"
    observe "because" "$(why_of "${control_line}")"
    say "    system policy log, best effort (it may be empty and that is not a failure):"
    (log show --style compact --last 3m \
        --predicate 'subsystem == "com.apple.syspolicy" OR senderImagePath CONTAINS "syspolicyd"' \
        2>/dev/null | tail -20 | sed 's/^/    ~ /') >>"${TRANSCRIPT}" 2>&1 || true

    say ""
    rule
    say "[9] STEP 3 OF 4 -- the PRODUCT's fix-up. PlatformFixups runs /usr/bin/xattr"
    say "    itself, through the product's process service; this script does not."
    probe "${WORK}/fixup.txt" fixup --dir "${cache}" --manifest "${MANIFEST}" --tool comet || true
    local fixup_host fixup_outcome cleared_count cleared_0 not_cleared_count
    local fixup_listed product_not_cleared
    fixup_host="$(kv "${WORK}/fixup.txt" 'fixup.host')"
    fixup_outcome="$(kv "${WORK}/fixup.txt" 'fixup.outcome')"
    cleared_count="$(kv "${WORK}/fixup.txt" 'fixup.quarantineCleared.count')"
    cleared_0="$(kv "${WORK}/fixup.txt" 'fixup.quarantineCleared.0')"
    not_cleared_count="$(kv "${WORK}/fixup.txt" 'fixup.quarantineNotCleared.count')"
    observe "PlatformFixups host" "${fixup_host}"
    observe "the product's fix-up outcome" "${fixup_outcome:-<no answer>}"
    observe "FixupReport.quarantineCleared().size()" "${cleared_count:-<no answer>}"
    observe "FixupReport.quarantineCleared().get(0)" "${cleared_0:-<none>}"
    observe "FixupReport.quarantineNotCleared().size()" "${not_cleared_count:-<no answer>}"
    observe "and the product's reason for the first" \
        "$(kv "${WORK}/fixup.txt" 'fixup.quarantineNotCleared.0.reason')"
    fixup_listed="$(names_value "${WORK}/fixup.txt" fixup.quarantineCleared "${install_path}")"
    product_not_cleared="$(names_value "${WORK}/fixup.txt" fixup.quarantineNotCleared "${install_path}")"
    observe "the product reports clearing the installed executable" "${fixup_listed}"
    observe "the product reports NOT clearing the installed executable" "${product_not_cleared}"

    local gone_xattr="no" gone_java="no" after_xattr after_status=0
    after_xattr="$(/usr/bin/xattr -p com.apple.quarantine -- "${binary}" 2>&1)" || after_status=$?
    observe "xattr -p after the fix-up" "${after_xattr:-<empty>}"
    observe "and its exit status" "${after_status}"
    gone_xattr="$(xattr_says_gone "${after_xattr}" "${after_status}")"
    observe "xattr says the attribute is gone" "${gone_xattr}"
    probe "${WORK}/attrs-after.txt" attrs --file "${binary}" || true
    if [ "$(kv "${WORK}/attrs-after.txt" 'attrs.quarantine.present')" = "false" ]; then
        gone_java="yes"
    fi
    observe "the product's Java view says it is gone" "${gone_java}"
    observe "all extended attributes still on the file" "$(/usr/bin/xattr -- "${binary}" | tr '\n' ' ' || true)"

    say ""
    rule
    say "[10] STEP 4 OF 4 -- run it again. Same file, same command, same cwd shape."
    run_capture "${WORK}/after-run.txt" "${RUN_TIMEOUT}" "${rundir}/after" "${binary}" -p
    local post_status="${RUN_STATUS}" post_timed_out="${TIMED_OUT}"
    observe "exit status" "${post_status}"
    if [ "${post_status}" -gt 128 ]; then
        observe "killed by signal" "$((post_status - 128))"
    fi
    observe "timed out" "${post_timed_out}"
    local params_written="no"
    if [ -f "${rundir}/after/comet.params.new" ]; then params_written="yes"; fi
    observe "comet.params.new written" "${params_written}"
    if [ "${params_written}" = "yes" ]; then
        observe "parameters declared in it" \
            "$(grep -c '^[a-z_0-9]* *=' "${rundir}/after/comet.params.new" || true)"
    fi
    say "    output, verbatim:"
    sed 's/^/    > /' "${WORK}/after-run.txt" | head -40 >>"${TRANSCRIPT}"
    sed 's/^/    > /' "${WORK}/after-run.txt" | head -40 || true
    probe "${WORK}/after-banner.txt" banner --file "${WORK}/after-run.txt" || true
    local post_banner post post_clean="no"
    post_banner="$(kv "${WORK}/after-banner.txt" 'banner.present')"
    observe "Comet's own banner present (product's CometBanner)" "${post_banner}"
    observe "the banner line" "$(kv "${WORK}/after-banner.txt" 'banner.line')"
    local post_line
    post_line="$(classify_run "${post_status}" "${post_timed_out}" "${post_banner}" "${WORK}/after-run.txt")"
    post="$(class_of "${post_line}")"
    observe "RUN AFTER THE FIX-UP CLASSIFIED AS" "${post}"
    observe "because" "$(why_of "${post_line}")"
    if [ "${post_status}" -eq 0 ] && [ "${params_written}" = "yes" ]; then post_clean="yes"; fi

    local status=0 attribute_status=0
    decide_attribute_verdict "${attr_set}" "${gone_xattr}" "${fixup_listed}" \
        "${product_not_cleared}" "${fixup_outcome}" || attribute_status=$?
    decide_verdict "${host_source}" "${host_platform}" "${attr_set}" "${control}" \
        "${gone_xattr}" "${gone_java}" "${fixup_listed}" "${post}" "${post_clean}" || status=$?
    conclude "${status}" "${attribute_status}" || return $?
    return 0
}

verdict_block() {
    local status="$1"
    say ""
    rule
    say "VERDICT: ${VERDICT}  (exit ${status})"
    say ""
    printf '%s\n' "${VERDICT_WHY}" | fold -s -w 72 | sed 's/^/  /' >>"${TRANSCRIPT}"
    printf '%s\n' "${VERDICT_WHY}" | fold -s -w 72 | sed 's/^/  /' || true
    say ""
    if [ "${status}" -eq "${EXIT_PASS}" ]; then
        say "  What this run establishes is ONE observation on ONE hosted macOS"
        say "  image. It is not a statement about every Mac, and the word this"
        say "  project permits for it is OBSERVED, not verified or proven."
    else
        say "  PHASE-05 exit gate item 9 is NOT met by this run."
    fi
    rule
    say "Transcript: ${TRANSCRIPT}"
    if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
        {
            printf '### macos-gatekeeper: %s\n\n' "${VERDICT}"
            printf '%s\n' "${VERDICT_WHY}"
        } >>"${GITHUB_STEP_SUMMARY}" 2>/dev/null || true
    fi
}

# ============================================================================
# --self-test: prove this harness can fail, on a machine with no Mac in it
# ============================================================================

SELF_TEST_PASSES=0
SELF_TEST_FAILURES=0

# expect_class EXPECTED LABEL STATUS TIMED_OUT BANNER OUTPUT_TEXT
expect_class() {
    local expected="$1" label="$2" status="$3" timed_out="$4" banner="$5" text="$6"
    local out="${WORK}/selftest-fixture.txt" line actual
    printf '%s\n' "${text}" >"${out}"
    line="$(classify_run "${status}" "${timed_out}" "${banner}" "${out}")"
    actual="$(class_of "${line}")"
    if [ "${actual}" = "${expected}" ]; then
        SELF_TEST_PASSES=$((SELF_TEST_PASSES + 1))
        printf '  ok    %-52s -> %-8s (%s)\n' "${label}" "${actual}" "$(why_of "${line}")"
    else
        SELF_TEST_FAILURES=$((SELF_TEST_FAILURES + 1))
        printf '  FAIL  %-52s -> %-8s, expected %s\n' "${label}" "${actual}" "${expected}"
    fi
}

# expect_verdict EXPECTED_EXIT EXPECTED_VERDICT LABEL <nine decide_verdict args>
#
# THE VERDICT TEXT IS PART OF THE ASSERTION, and that is not decoration: with
# the exit status alone, deleting the "cannot go red" branch left the run
# falling through to another branch with the SAME exit status, and the control
# passed over the defect.  Every branch now carries its own words and every
# case names the words it expects.
expect_verdict() {
    local expected="$1" want="$2" label="$3"; shift 3
    local actual=0
    decide_verdict "$@" || actual=$?
    if [ "${actual}" = "${expected}" ] && [ "${VERDICT}" = "${want}" ]; then
        SELF_TEST_PASSES=$((SELF_TEST_PASSES + 1))
        printf '  ok    %-52s -> exit %s  %s\n' "${label}" "${actual}" "${VERDICT}"
    else
        SELF_TEST_FAILURES=$((SELF_TEST_FAILURES + 1))
        printf '  FAIL  %-52s -> exit %s "%s", expected exit %s "%s"\n' \
            "${label}" "${actual}" "${VERDICT}" "${expected}" "${want}"
    fi
}

# expect_attribute EXPECTED_EXIT EXPECTED_VERDICT LABEL <five decide_attribute_verdict args>
# The words are part of the assertion, for the reason expect_verdict gives.
expect_attribute() {
    local expected="$1" want="$2" label="$3"; shift 3
    local actual=0
    decide_attribute_verdict "$@" || actual=$?
    if [ "${actual}" = "${expected}" ] && [ "${ATTR_VERDICT}" = "${want}" ]; then
        SELF_TEST_PASSES=$((SELF_TEST_PASSES + 1))
        printf '  ok    %-52s -> exit %s  %s\n' "${label}" "${actual}" "${ATTR_VERDICT}"
    else
        SELF_TEST_FAILURES=$((SELF_TEST_FAILURES + 1))
        printf '  FAIL  %-52s -> exit %s "%s", expected exit %s "%s"\n' \
            "${label}" "${actual}" "${ATTR_VERDICT}" "${expected}" "${want}"
    fi
}

# expect_combined EXPECTED LABEL GATE ATTRIBUTE
expect_combined() {
    local expected="$1" label="$2" actual
    actual="$(combine_exits "$3" "$4")"
    if [ "${actual}" = "${expected}" ]; then
        SELF_TEST_PASSES=$((SELF_TEST_PASSES + 1))
        printf '  ok    %-52s -> exit %s\n' "${label}" "${actual}"
    else
        SELF_TEST_FAILURES=$((SELF_TEST_FAILURES + 1))
        printf '  FAIL  %-52s -> exit %s, expected %s\n' "${label}" "${actual}" "${expected}"
    fi
}

record() {
    local ok="$1" label="$2" detail="$3"
    if [ "${ok}" = "yes" ]; then
        SELF_TEST_PASSES=$((SELF_TEST_PASSES + 1))
        printf '  ok    %-52s %s\n' "${label}" "${detail}"
    else
        SELF_TEST_FAILURES=$((SELF_TEST_FAILURES + 1))
        printf '  FAIL  %-52s %s\n' "${label}" "${detail}"
    fi
}

self_test() {
    mkdir -p -- "${WORK}"
    : >"${TRANSCRIPT}"
    printf '=== %s --self-test on %s %s ===\n\n' "${SCRIPT_NAME}" "$(uname -s)" "$(uname -m)"
    printf 'This proves the HARNESS can fail. It proves nothing about macOS: no\n'
    printf 'macOS binary is executed here, and no Gatekeeper decision is observed.\n'
    printf 'What it grades is the two functions that turn observations into a\n'
    printf 'verdict, and the product code the real run leans on.\n\n'

    # --- group A: classification of an observed run ------------------------
    printf 'A. classify_run -- what a run is read as, from status, signal and output\n'
    expect_class REFUSED "A1 SIGKILL, nothing on stderr" 137 0 false "zsh: killed     ./comet -p"
    expect_class REFUSED "A2 exit 126, the Gatekeeper wording" 126 0 false \
        "bash: ./comet: cannot be opened because the developer cannot be verified"
    expect_class REFUSED "A3 exit 1, 'Operation not permitted'" 1 0 false \
        "./comet: Operation not permitted"
    expect_class RAN "A4 exit 0 with Comet's banner" 0 0 true \
        'Comet version "2026.02 rev. 2 (6edec91)"'
    expect_class ARCH "A5 exit 126, 'Bad CPU type in executable'" 126 0 false \
        "arch: posix_spawnp: ./comet: Bad CPU type in executable"
    expect_class OTHER "A6 timed out" 0 1 false ""
    expect_class OTHER "A7 exit 1, an ordinary error" 1 0 false \
        "comet: cannot read parameter file"
    # The two precedence controls. Without them a lazier rule -- "status > 128
    # means refused" -- would pass every case above.
    expect_class RAN "A8 SIGKILL *after* the banner printed" 137 0 true \
        'Comet version "2026.02 rev. 2 (6edec91)"'
    expect_class ARCH "A9 SIGKILL with 'Bad CPU type'" 137 0 false \
        "arch: posix_spawnp: Bad CPU type in executable"
    # A10 pins the bare exit-126 rule: without it, A2 would still be classified
    # REFUSED by the wording in its output, and deleting the status rule would
    # cost nothing.  A refusal that printed nothing at all is a real shape --
    # a GUI dialog the job cannot see is exactly a refusal with a silent shell.
    expect_class REFUSED "A10 exit 126 and NOTHING printed" 126 0 false ""

    # --- group B: the verdict ----------------------------------------------
    printf '\nB. decide_verdict -- the whole result, from the observed values\n'
    expect_verdict 0 "PASS" "B1 everything as it should be" \
        system-properties macos-aarch64 yes REFUSED yes yes yes RAN yes
    expect_verdict 2 "INCONCLUSIVE -- THIS CHECK CANNOT GO RED ON THIS MACHINE" \
        "B2 THE CONTROL DID NOT BITE (cannot go red)" \
        system-properties macos-aarch64 yes RAN yes yes yes RAN yes
    # B2b: the branch must also SAY it is not a pass. A verdict that reported
    # an unbitten control in neutral words would be the defect dressed up.
    decide_verdict system-properties macos-aarch64 yes RAN yes yes yes RAN yes || true
    case "${VERDICT_WHY}" in
        *"THIS IS NOT A PASS"*)
            record yes "B2b it says so in the verdict, not just in the code" \
                "the reason carries the words THIS IS NOT A PASS" ;;
        *)
            record no "B2b it says so in the verdict, not just in the code" \
                "the reason does not say it is not a pass: ${VERDICT_WHY}" ;;
    esac
    # Every case below is arranged so that DELETING the branch it aims at
    # produces a different verdict, not the same one by another route.
    expect_verdict 1 "NEGATIVE -- THE FIX-UP DID NOT REMOVE THE ATTRIBUTE" \
        "B3 the attribute survived the product's fix-up" \
        system-properties macos-aarch64 yes REFUSED no no yes RAN yes
    expect_verdict 1 "NEGATIVE -- THE FIX-UP DID NOT REMOVE THE ATTRIBUTE" \
        "B4 xattr says gone, the product's Java view does not" \
        system-properties macos-aarch64 yes REFUSED yes no yes RAN yes
    expect_verdict 1 "NEGATIVE -- CLEARING THE ATTRIBUTE WAS NOT ENOUGH" \
        "B5 attribute gone and the binary still refused" \
        system-properties macos-aarch64 yes REFUSED yes yes yes REFUSED no
    expect_verdict 3 "HARNESS FAILURE -- THE ATTRIBUTE WAS NEVER SET" \
        "B6 the attribute could never be set" \
        system-properties macos-aarch64 no REFUSED yes yes yes RAN yes
    expect_verdict 2 "INCONCLUSIVE -- THE CONTROL FAILED ON THE ARCHITECTURE" \
        "B7 the control failed on the architecture" \
        system-properties macos-aarch64 yes ARCH yes yes yes RAN yes
    expect_verdict 2 "INCONCLUSIVE -- THE CONTROL NEITHER RAN NOR WAS REFUSED" \
        "B8 the control timed out or failed oddly" \
        system-properties macos-aarch64 yes OTHER yes yes yes RAN yes
    expect_verdict 2 "INCONCLUSIVE -- THE REMOVAL CANNOT BE ATTRIBUTED TO THE PRODUCT" \
        "B9 gone, but the product did not report clearing it" \
        system-properties macos-aarch64 yes REFUSED yes yes no RAN yes
    expect_verdict 2 "INCONCLUSIVE -- THE RUN AFTER THE FIX-UP WAS NOT CLEAN" \
        "B10 ran after the fix-up, but not cleanly" \
        system-properties macos-aarch64 yes REFUSED yes yes yes RAN no
    expect_verdict 3 "HARNESS FAILURE -- THE HOST WAS SUPPLIED, NOT READ" \
        "B11 the host was SUPPLIED, not read" \
        supplied macos-aarch64 yes REFUSED yes yes yes RAN yes
    expect_verdict 3 "HARNESS FAILURE -- NOT A MAC" \
        "B12 the host is not a Mac at all" \
        system-properties linux-x86-64 yes REFUSED yes yes yes RAN yes

    # --- group C: the product code the real run leans on --------------------
    printf '\nC. the PRODUCT code, exercised here (on Linux: the macOS branch FAILS, named; the rest is real)\n'
    if ! find_jdk; then
        printf '  FAIL  no usable JDK %s+ on this machine; group C cannot run, and a\n' "${MINIMUM_JAVA}"
        printf '        skipped control is not a passed one.\n'
        SELF_TEST_FAILURES=$((SELF_TEST_FAILURES + 1))
    else
        : >"${TRANSCRIPT}"
        if compile_probe >/dev/null 2>&1; then
            record yes "C0 the probe compiles against the product sources" \
                "$(find "${CLASSES}" -name '*.class' | wc -l | tr -d ' ') classes, PlatformFixups among them"
            self_test_product
        else
            record no "C0 the probe compiles against the product sources" "javac failed; see ${TRANSCRIPT}"
        fi
    fi

    # --- group D: this script's own refusals --------------------------------
    printf '\nD. the driver itself\n'
    local status=0
    ( bash "${BASH_SOURCE[0]}" >/dev/null 2>&1 ) || status=$?
    if [ "$(uname -s)" = "Darwin" ]; then
        record yes "D1 host refusal (skipped: this IS a Mac)" "uname -s = Darwin"
    else
        [ "${status}" -eq "${EXIT_REFUSED}" ] \
            && record yes "D1 a real run on a non-Mac is refused" "exit ${status} (${EXIT_REFUSED} = REFUSED)" \
            || record no "D1 a real run on a non-Mac is refused" "exit ${status}, expected ${EXIT_REFUSED}"
    fi
    status=0
    ( bash "${BASH_SOURCE[0]}" --nonsense >/dev/null 2>&1 ) || status=$?
    [ "${status}" -eq "${EXIT_MISUSE}" ] \
        && record yes "D2 an unknown argument is misuse" "exit ${status}" \
        || record no "D2 an unknown argument is misuse" "exit ${status}, expected ${EXIT_MISUSE}"

    self_test_attribute

    printf '\n'
    local total=$((SELF_TEST_PASSES + SELF_TEST_FAILURES))
    if [ "${SELF_TEST_FAILURES}" -ne 0 ]; then
        printf '%s: SELF-TEST FAILED -- %d of %d controls did not behave as required.\n' \
            "${SCRIPT_NAME}" "${SELF_TEST_FAILURES}" "${total}" >&2
        printf 'This harness must not be trusted until they do.\n' >&2
        return "${EXIT_SELF_TEST_FAILED}"
    fi
    # A floor, so that a control quietly deleted later is visible as a number
    # that went down rather than as a green run.
    if [ "${total}" -lt 71 ]; then
        printf '%s: SELF-TEST FAILED -- only %d controls ran; the recorded floor is 71.\n' \
            "${SCRIPT_NAME}" "${total}" >&2
        return "${EXIT_SELF_TEST_FAILED}"
    fi
    printf '%s: self-test OK -- %d/%d controls, every one seen to bite.\n' \
        "${SCRIPT_NAME}" "${SELF_TEST_PASSES}" "${total}"
    printf '%s: AND IT STILL PROVES NOTHING ABOUT GATEKEEPER, OR ABOUT xattr ON A MAC.\n' "${SCRIPT_NAME}"
    printf '%s: No macOS binary has been executed here.\n' "${SCRIPT_NAME}"
    return "${EXIT_PASS}"
}

# Groups E, F and G: the attribute verdict, independent of Gatekeeper.  Every
# case names the words it expects, and is arranged so that DELETING the branch
# it aims at produces different words (unit 13's lesson: an exit status alone
# let a deleted branch fall through to another with the same status).
self_test_attribute() {
    printf '\nE. decide_attribute_verdict -- R-PLAT-04 graded by xattr, whatever Gatekeeper did\n'
    expect_attribute 0 "ATTRIBUTE CLEARED -- /usr/bin/xattr AND THE PRODUCT'S REPORT AGREE" \
        "E1 seen before, gone after, the product names it" yes yes yes no completed
    expect_attribute 3 "ATTRIBUTE NOT GRADED -- XATTR NEVER SAW IT SET" \
        "E2 xattr never saw it set" no yes yes no completed
    expect_attribute 7 "ATTRIBUTE FAILED -- THE PRODUCT CLAIMS A REMOVAL /usr/bin/xattr DOES NOT SEE" \
        "E3 the product claims cleared, xattr still sees it" yes no yes no completed
    expect_attribute 7 "ATTRIBUTE FAILED -- IT SURVIVED THE FIX-UP, AND THE PRODUCT SAID SO" \
        "E4 still there, and the product reports not cleared" yes no no yes not-cleared
    expect_attribute 7 "ATTRIBUTE FAILED -- IT SURVIVED THE FIX-UP, AND THE PRODUCT REPORTED NOTHING" \
        "E5 RUN 36918810975 REPLAYED: still there, report silent" yes no no no completed
    expect_attribute 7 "ATTRIBUTE FAILED -- THE PRODUCT REPORTS A FAILURE /usr/bin/xattr DOES NOT SEE" \
        "E6 gone, and the product reports not cleared" yes yes no yes not-cleared
    expect_attribute 7 "ATTRIBUTE FAILED -- GONE, BUT THE PRODUCT DID NOT REPORT REMOVING IT" \
        "E7 gone, and the product did not report removing it" yes yes no no completed
    expect_attribute 7 "ATTRIBUTE FAILED -- THE PRODUCT REPORTS A FAILURE /usr/bin/xattr DOES NOT SEE" \
        "E8 gone, and the report contradicts itself" yes yes yes yes not-cleared
    # E9: the failure says it does not depend on Gatekeeper, in its own words.
    decide_attribute_verdict yes no no no completed || true
    case "${ATTR_VERDICT_WHY}" in
        *"whatever Gatekeeper did"*)
            record yes "E9 a failure says it stands whatever Gatekeeper did" \
                "the reason carries the words whatever Gatekeeper did" ;;
        *)
            record no "E9 a failure says it stands whatever Gatekeeper did" \
                "the reason does not say so: ${ATTR_VERDICT_WHY}" ;;
    esac

    printf '\nF. the exit status, and both verdicts reaching the transcript\n'
    expect_combined 7 "F1 Gatekeeper cannot go red, attribute failed" 2 7
    expect_combined 2 "F2 Gatekeeper cannot go red, attribute cleared" 2 0
    expect_combined 0 "F3 both pass" 0 0
    expect_combined 7 "F4 Gatekeeper passed, attribute failed" 0 7
    expect_combined 7 "F5 Gatekeeper negative, attribute failed" 1 7
    expect_combined 3 "F6 Gatekeeper passed, attribute ungraded" 0 3
    expect_combined 3 "F7 Gatekeeper harness failure, attribute cleared" 3 0
    # F8 -- run 36918810975's observations, through conclude(), which is where
    # the real run ends: the exit status AND the transcript lines.
    local status=0
    : >"${TRANSCRIPT}"
    decide_attribute_verdict yes no no no completed || true
    decide_verdict system-properties macos-aarch64 yes RAN no yes no RAN yes || true
    conclude "${EXIT_INCONCLUSIVE}" "${EXIT_ATTRIBUTE_FAILED}" >/dev/null 2>&1 || status=$?
    if [ "${status}" -eq "${EXIT_ATTRIBUTE_FAILED}" ] \
        && grep -qF "ATTRIBUTE VERDICT: ATTRIBUTE FAILED -- IT SURVIVED THE FIX-UP, AND THE PRODUCT REPORTED NOTHING  (exit 7)" "${TRANSCRIPT}" \
        && grep -qF "VERDICT: INCONCLUSIVE -- THIS CHECK CANNOT GO RED ON THIS MACHINE  (exit 2)" "${TRANSCRIPT}" \
        && grep -qF "EXIT STATUS: 7 (Gatekeeper verdict exit 2, attribute verdict exit 7)" "${TRANSCRIPT}"; then
        record yes "F8 run 36918810975 replayed through conclude: exit 7" \
            "both verdict lines and EXIT STATUS: 7 are in the transcript"
    else
        record no "F8 run 36918810975 replayed through conclude: exit 7" \
            "exit ${status}; transcript: $(grep -E 'VERDICT|EXIT STATUS' "${TRANSCRIPT}" | tr '\n' '|')"
    fi
    # F9 -- the outcome this unit hopes for on the runner: the attribute
    # cleared under a control that did not bite.  Still exit 2, still NOT MET.
    status=0
    : >"${TRANSCRIPT}"
    decide_attribute_verdict yes yes yes no completed || true
    decide_verdict system-properties macos-aarch64 yes RAN yes yes yes RAN yes || true
    conclude "${EXIT_INCONCLUSIVE}" "${EXIT_PASS}" >/dev/null 2>&1 || status=$?
    if [ "${status}" -eq "${EXIT_INCONCLUSIVE}" ] \
        && grep -qF "ATTRIBUTE VERDICT: ATTRIBUTE CLEARED -- /usr/bin/xattr AND THE PRODUCT'S REPORT AGREE  (exit 0)" "${TRANSCRIPT}" \
        && grep -qF "PHASE-05 exit gate item 9 is NOT met by this run." "${TRANSCRIPT}"; then
        record yes "F9 attribute cleared, control unbitten: exit 2, item 9 NOT met" \
            "a cleared attribute is not a Gatekeeper pass, and the transcript says so"
    else
        record no "F9 attribute cleared, control unbitten: exit 2, item 9 NOT met" \
            "exit ${status}; transcript: $(grep -E 'VERDICT|NOT met' "${TRANSCRIPT}" | tr '\n' '|')"
    fi

    printf '\nG. names_value -- reading the product report the verdict is graded against\n'
    local fixture="${WORK}/selftest-report.txt"
    printf '%s\n' 'probe.fixup.quarantineCleared.count=1' 'probe.fixup.quarantineCleared.0=bin/comet' \
        'probe.fixup.quarantineNotCleared.count=1' 'probe.fixup.quarantineNotCleared.0=lib/x.dylib' \
        'probe.fixup.quarantineNotCleared.0.reason=bin/comet' >"${fixture}"
    local got
    got="$(names_value "${fixture}" fixup.quarantineCleared bin/comet)"
    [ "${got}" = "yes" ] \
        && record yes "G1 an indexed entry is found" "quarantineCleared names bin/comet -> ${got}" \
        || record no "G1 an indexed entry is found" "-> ${got}"
    got="$(names_value "${fixture}" fixup.quarantineNotCleared bin/comet)"
    [ "${got}" = "no" ] \
        && record yes "G2 a .reason line is not an entry" "quarantineNotCleared does not name bin/comet -> ${got}" \
        || record no "G2 a .reason line is not an entry" "-> ${got}"
    got="$(names_value "${fixture}" fixup.quarantineCleared bin/comet.bak)"
    [ "${got}" = "no" ] \
        && record yes "G3 a different value is not a match" "bin/comet.bak -> ${got}" \
        || record no "G3 a different value is not a match" "-> ${got}"
    got="$(names_value "${WORK}/no-such-report.txt" fixup.quarantineCleared bin/comet)"
    [ "${got}" = "no" ] \
        && record yes "G4 no report at all names nothing" "missing file -> ${got}" \
        || record no "G4 no report at all names nothing" "-> ${got}"

    printf '\nH. reading xattr itself -- the observations both verdicts rest on\n'
    local f="/x/cache/bin/comet"
    expect_gone no  "H1 the value printed, exit 0: present" \
        "0081;68b6f0a0;CometGUI-gate-item-9;00000000-0000-0000-0000-000000000000" 0
    expect_gone yes "H2 'No such xattr: com.apple.quarantine', exit 1: gone" \
        "xattr: ${f}: No such xattr: com.apple.quarantine" 1
    expect_gone no  "H3 EMPTY output, exit 1: not shown gone" "" 1
    expect_gone no  "H4 EMPTY output, exit 0: not shown gone" "" 0
    expect_gone no  "H5 'No such file', exit 1: says nothing" \
        "xattr: ${f}: No such file: ${f}" 1
    expect_gone no  "H6 'No such xattr' for ANOTHER name: not this one" \
        "xattr: ${f}: No such xattr: com.apple.provenance" 1
    expect_gone no  "H7 a longer name ending the line: not this one" \
        "xattr: ${f}: No such xattr: com.apple.quarantine.other" 1
    expect_gone no  "H8 the right words with exit 0: contradiction" \
        "xattr: ${f}: No such xattr: com.apple.quarantine" 0
    expect_gone no  "H9 no exit status captured: not shown gone" \
        "xattr: ${f}: No such xattr: com.apple.quarantine" ""
    expect_listed yes "H10 the listing names it on a line of its own" \
        "com.apple.provenance
com.apple.quarantine"
    expect_listed no  "H11 a listing without it" "com.apple.provenance"
    expect_listed no  "H12 a longer name is not the name" "com.apple.quarantine.other"
    expect_listed no  "H13 an empty listing" ""
    # H14 -- STRUCTURAL, and labelled so: the real run cannot be executed off a
    # Mac, so this reads real_run's own text.  Every assignment to gone_xattr
    # and attr_set in it must be the declared default or a call to the pure
    # functions above -- an inline parse beside them (the always-"gone" arm
    # that lived here until unit 14's rework) would bypass group H entirely.
    local body stray
    body="$(awk '/^real_run\(\) \{/ { on = 1 } on { print } on && /^\}/ { exit }' "${BASH_SOURCE[0]}")"
    stray="$(printf '%s\n' "${body}" | grep -n -E '(gone_xattr|attr_set)=' \
        | grep -v -E 'local .*(gone_xattr|attr_set)="no"' \
        | grep -v -F 'gone_xattr="$(xattr_says_gone "${after_xattr}" "${after_status}")"' \
        | grep -v -F 'attr_set="$(xattr_lists_quarantine "${listing}")"' || true)"
    if [ -n "${body}" ] && [ -z "${stray}" ] \
        && [ "$(printf '%s\n' "${body}" | grep -c -F 'gone_xattr="$(xattr_says_gone "${after_xattr}" "${after_status}")"')" = "1" ] \
        && [ "$(printf '%s\n' "${body}" | grep -c -F 'attr_set="$(xattr_lists_quarantine "${listing}")"')" = "1" ]; then
        record yes "H14 STRUCTURAL: the real run decides only through H's functions" \
            "gone_xattr and attr_set are each set once, by xattr_says_gone / xattr_lists_quarantine"
    else
        record no "H14 STRUCTURAL: the real run decides only through H's functions" \
            "real_run sets them some other way: $(printf '%s' "${stray:-<a call is missing>}" | tr '\n' '|')"
    fi
}

# expect_gone EXPECTED LABEL OUTPUT STATUS -- xattr_says_gone, with the input
# words printed beside the answer so the case says what it read.
expect_gone() {
    local expected="$1" label="$2" output="$3" status="$4" actual
    actual="$(xattr_says_gone "${output}" "${status}")"
    if [ "${actual}" = "${expected}" ]; then
        SELF_TEST_PASSES=$((SELF_TEST_PASSES + 1))
        printf '  ok    %-52s -> gone=%s  [exit %s] "%s"\n' "${label}" "${actual}" "${status:-?}" "${output}"
    else
        SELF_TEST_FAILURES=$((SELF_TEST_FAILURES + 1))
        printf '  FAIL  %-52s -> gone=%s, expected gone=%s  [exit %s] "%s"\n' \
            "${label}" "${actual}" "${expected}" "${status:-?}" "${output}"
    fi
}

# expect_listed EXPECTED LABEL LISTING -- xattr_lists_quarantine, likewise.
expect_listed() {
    local expected="$1" label="$2" listing="$3" actual
    actual="$(xattr_lists_quarantine "${listing}")"
    if [ "${actual}" = "${expected}" ]; then
        SELF_TEST_PASSES=$((SELF_TEST_PASSES + 1))
        printf '  ok    %-52s -> listed=%s  "%s"\n' "${label}" "${actual}" "$(printf '%s' "${listing}" | tr '\n' '|')"
    else
        SELF_TEST_FAILURES=$((SELF_TEST_FAILURES + 1))
        printf '  FAIL  %-52s -> listed=%s, expected listed=%s  "%s"\n' \
            "${label}" "${actual}" "${expected}" "$(printf '%s' "${listing}" | tr '\n' '|')"
    fi
}

# Group C in full: the quarantine attribute, removed by the product's own code,
# on this Linux machine, with the macOS branch selected by supplying the two
# strings HostPlatform.of parses.  Every case reads the attribute back and says
# what it read.
self_test_product() {
    local sandbox="${WORK}/selftest-product"
    local file quarantine="com.apple.quarantine"

    # C1 -- a real extended attribute on this file system.  On Linux the JDK
    # stores it as user.com.apple.quarantine: it is NOT the attribute the macOS
    # branch removes, and it is used only to show the non-macOS branch (C4)
    # leaving an attribute alone.
    rm -rf -- "${sandbox}"; mkdir -p -- "${sandbox}/bin"
    file="${sandbox}/bin/comet"; printf 'not a real binary' >"${file}"; chmod 644 -- "${file}"
    if ! probe "${WORK}/st-set.txt" set-attr --file "${file}" --value "0081;selftest" >/dev/null 2>&1; then
        record no "C1 an attribute named ${quarantine} can be set here" \
            "this file system published no user-defined attribute view; nothing below can run"
        return
    fi
    record yes "C1 an attribute named ${quarantine} can be set here" \
        "read back = $(kv "${WORK}/st-set.txt" 'setattr.readBack') (on Linux: user.${quarantine})"

    # C2 -- THE macOS BRANCH, THROUGH THE REAL PROCESS SERVICE, on a machine
    # with no /usr/bin/xattr.  Its one honest outcome here is a NAMED failure:
    # the silent no-op run 36918810975 found must be impossible.  This proves
    # the product's failure path and the probe's printing of it; it proves
    # nothing about what /usr/bin/xattr does on a Mac.
    local outcome not_cleared reason cleared_count
    if [ "$(uname -s)" = "Darwin" ]; then
        record yes "C2 macOS branch with no xattr is a named failure (skipped)" \
            "this machine IS a Mac, and has /usr/bin/xattr; the real run grades it"
        record yes "C2b the attribute verdict reads it as a failure (skipped)" \
            "this machine IS a Mac"
    else
        probe "${WORK}/st-fixup.txt" fixup --dir "${sandbox}" --manifest "${MANIFEST}" --tool comet \
            --os-name "Mac OS X" --os-arch aarch64 >/dev/null 2>&1 || true
        outcome="$(kv "${WORK}/st-fixup.txt" 'fixup.outcome')"
        not_cleared="$(kv "${WORK}/st-fixup.txt" 'fixup.quarantineNotCleared.0')"
        reason="$(kv "${WORK}/st-fixup.txt" 'fixup.quarantineNotCleared.0.reason')"
        cleared_count="$(kv "${WORK}/st-fixup.txt" 'fixup.quarantineCleared.count')"
        case "${reason}" in
            "bin/comet: /usr/bin/xattr could not be started: could not start"*)
                [ "${outcome}" = "not-cleared" ] && [ "${not_cleared}" = "bin/comet" ] \
                    && [ "${cleared_count}" = "0" ] \
                    && record yes "C2 macOS branch with no xattr here is a NAMED failure" \
                        "outcome=${outcome}, not cleared [${not_cleared}], cleared ${cleared_count}" \
                    || record no "C2 macOS branch with no xattr here is a NAMED failure" \
                        "outcome=${outcome:-?}, not cleared [${not_cleared:-?}], cleared ${cleared_count:-?}" ;;
            *)
                record no "C2 macOS branch with no xattr here is a NAMED failure" \
                    "outcome=${outcome:-?}, reason: ${reason:-<none>}" ;;
        esac
        # C2b -- the PRODUCT's real report, fed to the attribute verdict as the
        # real run feeds it, with xattr's two readings SUPPLIED (yes, then
        # still present): the verdict must read the report as an honest failure.
        decide_attribute_verdict yes no \
            "$(names_value "${WORK}/st-fixup.txt" fixup.quarantineCleared bin/comet)" \
            "$(names_value "${WORK}/st-fixup.txt" fixup.quarantineNotCleared bin/comet)" \
            "${outcome}" || true
        [ "${ATTR_VERDICT}" = "ATTRIBUTE FAILED -- IT SURVIVED THE FIX-UP, AND THE PRODUCT SAID SO" ] \
            && record yes "C2b the attribute verdict reads that report as a failure" "${ATTR_VERDICT}" \
            || record no "C2b the attribute verdict reads that report as a failure" "${ATTR_VERDICT}"
    fi

    # C3 -- THE CONTROL for C4: without the fix-up the attribute is still there,
    # so C4's "still present" is not something that was never set.
    rm -rf -- "${sandbox}"; mkdir -p -- "${sandbox}/bin"
    file="${sandbox}/bin/comet"; printf 'not a real binary' >"${file}"; chmod 644 -- "${file}"
    probe "${WORK}/st-set2.txt" set-attr --file "${file}" --value "0081;selftest" >/dev/null 2>&1 || true
    probe "${WORK}/st-nofix.txt" attrs --file "${file}" >/dev/null 2>&1 || true
    present="$(kv "${WORK}/st-nofix.txt" 'attrs.quarantine.present')"
    [ "${present}" = "true" ] \
        && record yes "C3 CONTROL: with no fix-up it is still there" "present = ${present}" \
        || record no "C3 CONTROL: with no fix-up it is still there" "present = ${present}"

    # C4 -- THE OTHER CONTROL: the host gate is real. The same call, with this
    # machine's real platform, must leave the attribute alone -- which is also
    # what makes C2 a statement about the macOS branch rather than about
    # PlatformFixups running unconditionally.
    probe "${WORK}/st-linuxfix.txt" fixup --dir "${sandbox}" --manifest "${MANIFEST}" --tool comet \
        >/dev/null 2>&1 || true
    probe "${WORK}/st-after2.txt" attrs --file "${file}" >/dev/null 2>&1 || true
    local linux_cleared linux_present linux_host
    linux_host="$(kv "${WORK}/st-linuxfix.txt" 'fixup.host')"
    linux_cleared="$(kv "${WORK}/st-linuxfix.txt" 'fixup.quarantineCleared.count')"
    linux_present="$(kv "${WORK}/st-after2.txt" 'attrs.quarantine.present')"
    if [ "$(uname -s)" = "Darwin" ]; then
        record yes "C4 CONTROL: the non-macOS branch clears nothing (skipped)" \
            "this machine IS a Mac, so there is no non-macOS branch to take here"
    else
        [ "${linux_cleared}" = "0" ] && [ "${linux_present}" = "true" ] \
            && record yes "C4 CONTROL: the non-macOS branch clears nothing" \
                "host=${linux_host}, cleared ${linux_cleared}, attribute still present = ${linux_present}" \
            || record no "C4 CONTROL: the non-macOS branch clears nothing" \
                "host=${linux_host}, cleared ${linux_cleared:-?}, present = ${linux_present:-?}"
    fi

    # C5 -- the product's checksum verification says NO to the wrong bytes.
    probe "${WORK}/st-verify.txt" verify --file "${file}" --manifest "${MANIFEST}" --tool comet \
        --os-name "Mac OS X" --os-arch aarch64 >/dev/null 2>&1 \
        && record no "C5 CONTROL: the wrong bytes are rejected" "the probe ACCEPTED 17 bytes as Comet" \
        || record yes "C5 CONTROL: the wrong bytes are rejected" \
            "verify.match = $(kv "${WORK}/st-verify.txt" 'verify.match'), observed sha256 $(kv "${WORK}/st-verify.txt" 'verify.observed.sha256' | cut -c1-16)..."

    # C6 -- the product selects a NATIVE aarch64 Comet for an Apple silicon Mac.
    probe "${WORK}/st-select.txt" select --manifest "${MANIFEST}" --tool comet \
        --os-name "Mac OS X" --os-arch aarch64 >/dev/null 2>&1 || true
    local chosen_platform chosen_exec
    chosen_platform="$(kv "${WORK}/st-select.txt" 'select.0.platform')"
    chosen_exec="$(kv "${WORK}/st-select.txt" 'select.0.executability')"
    [ "${chosen_platform}" = "macos-aarch64" ] && [ "${chosen_exec}" = "NATIVE" ] \
        && record yes "C6 an Apple silicon Mac is offered a native Comet" \
            "${chosen_platform}, ${chosen_exec} -- so the gate item does not rest on Rosetta 2" \
        || record no "C6 an Apple silicon Mac is offered a native Comet" \
            "${chosen_platform:-?}, ${chosen_exec:-?}"

    # C7 -- the banner recogniser is the product's, and it says no when it must.
    printf 'zsh: killed     ./comet -p\n' >"${WORK}/st-nobanner.txt"
    probe "${WORK}/st-banner1.txt" banner --file "${WORK}/st-nobanner.txt" >/dev/null 2>&1 || true
    printf 'Comet version "2026.02 rev. 2 (6edec91)"\n' >"${WORK}/st-banner-text.txt"
    probe "${WORK}/st-banner2.txt" banner --file "${WORK}/st-banner-text.txt" >/dev/null 2>&1 || true
    [ "$(kv "${WORK}/st-banner1.txt" 'banner.present')" = "false" ] \
        && [ "$(kv "${WORK}/st-banner2.txt" 'banner.present')" = "true" ] \
        && record yes "C7 CometBanner says no to a kill and yes to a banner" \
            "refusal text -> false, banner text -> true" \
        || record no "C7 CometBanner says no to a kill and yes to a banner" \
            "refusal -> $(kv "${WORK}/st-banner1.txt" 'banner.present'), banner -> $(kv "${WORK}/st-banner2.txt" 'banner.present')"

    # C8 -- a half-supplied host is refused rather than mixed with this machine.
    probe "${WORK}/st-misuse.txt" host --os-name "Mac OS X" >/dev/null 2>&1 \
        && record no "C8 CONTROL: half a supplied host is refused" "the probe accepted it" \
        || record yes "C8 CONTROL: half a supplied host is refused" "the probe exited non-zero"

    rm -rf -- "${sandbox}"
}

# ============================================================================
# main
# ============================================================================

cd -- "${PROJECT_ROOT}"

MODE="run"
case "${1:-}" in
    "") MODE="run" ;;
    --check-only) MODE="check-only" ;;
    --self-test) MODE="self-test" ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument: $1 (try --help)" ;;
esac
[ "$#" -le 1 ] || die "one argument at most (try --help)"

[ -f "${PROJECT_ROOT}/${PROBE_SOURCE}" ] || die "no probe source at ${PROBE_SOURCE}" "${EXIT_HARNESS}"
[ -f "${PROJECT_ROOT}/${MANIFEST}" ] || die "no artefact manifest at ${MANIFEST}" "${EXIT_HARNESS}"

case "${MODE}" in
    self-test)
        status=0
        self_test || status=$?
        exit "${status}"
        ;;
    check-only)
        status=0
        check_only || status=$?
        exit "${status}"
        ;;
    run)
        if [ "$(uname -s)" != "Darwin" ]; then
            printf '%s: this is %s, not a Mac.\n' "${SCRIPT_NAME}" "$(uname -s)" >&2
            printf '%s: gate item 9 is about macOS and cannot be answered here. Run\n' "${SCRIPT_NAME}" >&2
            printf '%s: --check-only for what can be done without a Mac, or --self-test\n' "${SCRIPT_NAME}" >&2
            printf '%s: to prove this harness can fail. The real run happens on the\n' "${SCRIPT_NAME}" >&2
            printf '%s: macos-gatekeeper workflow, and nowhere else yet.\n' "${SCRIPT_NAME}" >&2
            exit "${EXIT_REFUSED}"
        fi
        status=0
        real_run || status=$?
        exit "${status}"
        ;;
esac
