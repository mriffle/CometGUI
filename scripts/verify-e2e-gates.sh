#!/usr/bin/env bash
#
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
#
# verify-e2e-gates.sh -- prove the seven PHASE-14 exit gate items, by running
# the end-to-end tier and by injecting the defects gate item 5 names into
# production code (Phase 14 unit 9; design decision P14-14).
#
#   bash scripts/verify-e2e-gates.sh                every control
#   bash scripts/verify-e2e-gates.sh --self-test    control H only (and the
#                                                   clean Tier A run it needs)
#   bash scripts/verify-e2e-gates.sh --only IDS     named controls (comma-
#                                                   separated), plus the clean
#                                                   Tier A run every one needs
#   bash scripts/verify-e2e-gates.sh --preflight    what the host lacks, and stop
#   bash scripts/verify-e2e-gates.sh -h|--help
#
# A gate that has never been seen to fail has not been shown to work
# (CONTRIBUTING.rst, "Gate conventions").  Phase 14's exit gate is seven
# claims, phases/PHASE-14-e2e.rst, quoted here so the grading below can be
# read against them:
#
#   1. The canonical scenario passes in Tier A, end to end, from an empty tool
#      cache through provenance verification and application restart.
#   2. The canonical scenario passes in Tier B against the packaged artefact on
#      the reference platform.
#   3. The no-XML scenario passes in both tiers, including the assertion that
#      Comet was not rerun.
#   4. Every failure-path scenario in the specification has a passing test.
#   5. Deliberately inverting a q-value comparison, dropping a provenance
#      entry, or bypassing a downstream stage each cause the canonical test to
#      fail -- demonstrated, not assumed.
#   6. No test uses a fixed sleep; all waits are on observable state.
#   7. The bridge-absence check fails when the bridge is deliberately included
#      in a build.
#
# WHAT EACH CONTROL DOES
#
#   A   items 1 and 3 (Tier A): TierACanonicalScenarioTest and
#       TierANoXmlScenarioTest, once, in one Maven run under LC_ALL=C.UTF-8,
#       which this script exports itself.  Each class must have run EVERY one of
#       its @Test methods -- counted from the surefire XML report this run
#       wrote, against the @Test methods in its source, none skipped, none
#       failed -- and the report must record the forked JVM's sun.jnu.encoding
#       as UTF-8.  The no-XML class's cometNotRerun step (gate item 3's "Comet
#       was not rerun") is named and must have passed.  This run's compiled
#       modules are the bytecode baseline every injection is compared with.
#   L   the class's own locale assertion: TierACanonicalScenarioTest launched
#       with EVERY locale variable removed from the environment must fail before
#       any step runs, naming the encoding (sun.jnu.encoding=ANSI_X3.4-1968 on a
#       POSIX locale) -- so the green run in A is green BECAUSE of the locale,
#       not whatever it was.
#   P   item 2's artefact: scripts/package/build-app-image.sh, run in the
#       sandbox, builds the Linux app-image and its tar.gz; the archive's
#       .sha256 must verify, before Tier B and again after every control.
#   B   items 2 and 3 (Tier B) and the launched-process locale:
#       TierBCanonicalScenarioTest, TierBNoXmlScenarioTest (cometNotRerun named)
#       and TierBLocaleControlTest, once, in one Maven run, against the archive
#       P built -- each test class installs it fresh, read-only, outside the
#       repository (scripts/package/install-archive.sh) and launches it.  Every
#       @Test method run and passed, as in A.  TierBLocaleControlTest is the
#       control that the harness fails loudly on a non-UTF-8 launched process.
#   4   item 4: every test scripts/e2e/failure-paths.toml maps with run =
#       "ordinary" is run here once (one Maven run), its surefire reports kept
#       beside those of A and B; then scripts/e2e/check-failure-paths.sh
#       --reports <exactly those reports> --scope all must PASS, grading every
#       mapped id (the e2e ids from A and B); and its --self-test must PASS.
#   6   item 6: scripts/e2e/check-no-fixed-sleep.sh on the sandbox's compiled
#       tree, and its --self-test.
#   7   item 7: scripts/ci/release-no-test-bridge.sh on the clean archive P
#       built (PASSED, with the amount it inspected), and its --self-test (every
#       bridge-included variant rejected, an empty image refused).
#   3i  item 3 [recorded, unit 4 sign-off]: PercolatorRerun.copy touches the
#       source run's file after copying it -- TierANoXmlScenarioTest must go red
#       at cometNotRerun on the DISK assertion ("the first run's Comet trees,
#       unchanged ... mtime"), with the step before it (compatibleRerun) green.
#   5q  item 5, inverted q-value comparison [recorded, unit 7 sign-off, Tier B]:
#       QValueFilter.classify's <= made < -- TierACanonicalScenarioTest red at
#       the scenario's boundary step (inclusiveCutoff: "PSMs at q <= ..."), and
#       step 21 at the specification's own 0.01 cutoff, which no q-value in the
#       run equals, still green: the boundary step is what catches it.
#   5p  item 5, dropped provenance entry [recorded, unit 3 sign-off]:
#       ManifestWriter.render leaving the searched FASTA out of provenance.json
#       -- red at step 28 (provenance: "...UP000005640_9606.fasta: not declared
#       as an input"), the upload before it green.
#   5b  item 5, a bypassed downstream stage the product's own guards do not
#       catch [recorded, unit 3 agent's injection]: LimelightUploadClient
#       skipping the protocol's final submit and reporting the upload's own
#       answer as the submit's, so the screen still says "Submitted to
#       Limelight" -- red at step 27 (upload: "the final submit queued the
#       import"), the conversion before it green.
#       Each injection: the anchor must match EXACTLY ONCE and be gone after;
#       the file must differ from its pristine copy; the compiled form of the
#       damaged class must DIFFER from the clean baseline after the run and every
#       other compiled file must be IDENTICAL to it; the red must be in the named
#       testcase WITH THE EXPECTED FAILURE'S OWN WORDS -- a red for another
#       reason is a failure of this harness, never a pass; then the file is
#       restored, and sha256sum -c against the pristine digests proves it.
#   H   the harness itself, each reported as a HARNESS FAILURE and never as a
#       control that bit: an injection whose anchor is missing; an injection
#       that reaches the source but not the bytecode (a real Maven run); a
#       green run graded as a red; a red without its expected words; and a run
#       in which zero tests ran -- a REAL Maven run of an end-to-end class
#       without -Dcometgui.e2e=true, which surefire reports as tests="2"
#       skipped="2" and Maven as BUILD SUCCESS: the shape a harness that counts
#       reported tests would call green.
#   C   every restoration reached the bytecode: the sandbox recompiled
#       (test-compile, no test run) is identical to the baseline; every damaged
#       source passes sha256sum -c against its pristine digest; the archive
#       still verifies; the shared repository's org/cometgui jars unchanged.
#
# LEAN, ON PURPOSE (P14-14).  Each scenario class runs ONCE per harness run in
# its clean form.  The exceptions are the injections, which each need a run
# with the defect in it: gate item 5's three rerun the canonical Tier A test
# (the brief's one stated exception), gate item 3's one reruns the no-XML Tier A
# test (the brief requires that injection too), and control L reruns the
# canonical class only as far as its locale assertion, before any step.  There
# is NO final clean rerun of the scenarios: C proves the restorations in the
# bytecode instead, which is what a rerun would have been evidence of.
#
# WHERE IT WORKS.  Never in the working tree.  It extracts `git archive HEAD`
# into _build/e2e-gate-sandbox and works there; uncommitted changes are reported
# loudly and are NOT what is graded.  scratch/ (the D-006 LOCAL fixture -- both
# K562 mzML files and the UniProt proteome -- the Percolator 3.09 the no-XML
# scenario registers, and the Phase 05 artefacts the ordinary failure-path
# tests mirror) and tools/ (the JDK, Maven, the X11 and font closures) are
# gitignored and are symlinked in from the working tree; so is _build/m2repo,
# read offline and never written (digested before and after).  It refuses to
# start when any of them is missing, naming it.  It never runs inside a sandbox
# that lacks them: a scenario that cannot find its fixture fails for the wrong
# reason, and a skipped scenario is never a pass.
#
# NETWORK -- REQUIRED.  The end-to-end scenarios install Comet, Percolator, PDV
# and the Limelight converter the way a user does: from the URLs the shipped
# manifests/tools.json pins, on github.com, whose release downloads redirect to
# *.githubusercontent.com.  Tier A's JVM may reach exactly those hosts and
# loopback; Tier B's packaged application downloads them itself.  The preflight
# fetches the headers of the four pinned Linux artefacts and refuses to start
# if any does not answer 200.  No Limelight server is ever contacted: every
# upload goes to the D-007 fake on loopback.
#
# THE LOCALE.  This script exports LC_ALL=C.UTF-8 and LANG=C.UTF-8 itself, for
# every Maven run except control L's, which removes every locale variable on
# purpose.  It does not inherit the host's (this project's build host exports
# none at all).
#
# WHAT IT SWITCHES OFF, AND WHY THAT IS NOT A WEAKENING.  Every sandbox Maven
# run passes -Dspotless.check.skip -Dcheckstyle.skip -Dspotbugs.skip
# -Djacoco.skip: Phase 01's gates, which have their own harnesses; an
# injection would otherwise be stopped by a formatter before a test ran.
# Nothing this script grades is skipped, no test is excluded, and every run is
# checked to have EXECUTED what it named.
#
# PROCESSES.  Real Comet, Percolator, PDV, the converter, Xvfb and the packaged
# CometGUI run under the tests, and Tier A/B put them under /tmp, outside the
# sandbox.  So every Maven run is started under a child-subreaper
# (prctl(PR_SET_CHILD_SUBREAPER)): whatever it leaves behind is re-parented to
# the reaper, waited for up to 20 s, then ended (TERM, then KILL) and counted;
# a process that survives KILL is a HARNESS FAILURE.
#
# WHAT IT COSTS.  Measured and printed per control; the total is on the
# SUMMARY line.  See docs/developer/testing.rst once unit 10 records it.
#
# WHAT IT DOES NOT COVER, said plainly:
#   * macOS and Windows: the packaging configuration for them has never been
#     executed (Phase 14 unit 5); only the Linux app-image is built and tested.
#   * That PDV DRAWS in Tier B: on this host the packaged application cannot
#     give PDV the X11 libraries (E6); Tier B proves PDV is installed and
#     launched from the installed runtime and fails only for that reason.
#   * CI: no CI job runs this script.  It needs the D-006 LOCAL fixture, and
#     D-006's CI half is OPEN; nothing may present the local fixture as the CI
#     fixture.  See scripts/ci/release-e2e-*.sh.
#
# EXIT STATUS
#   0  every control bit
#   1  at least one control failed: a gate did not bite, bit for the wrong
#      reason, a sibling that had to stay green went red, or a clean run failed
#   2  misuse (unknown option, unknown control)
#   3  the environment is not ready (fixture, X11 closure, network, toolchain)
#   4  HARNESS FAILURE: an anchor is gone, an injection reached nothing, a run
#      did not build, a command ran none of the tests it named or ran zero, or a
#      process could not be ended.  The run proves nothing and is not a pass.

set -Eeuo pipefail

# --------------------------------------------------------------- constants --
SCRIPT_NAME="$(basename -- "${BASH_SOURCE[0]}")"
readonly SCRIPT_NAME
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT
readonly SANDBOX="${ROOT}/_build/e2e-gate-sandbox"
readonly PRISTINE="${ROOT}/_build/e2e-gate-pristine"
readonly M2REPO="${ROOT}/_build/m2repo"
readonly LOGS="${ROOT}/_build/e2e-gate-logs"
readonly REPORTS="${LOGS}/reports"

# The gitignored inputs the graded tests read; they fail rather than skip
# without them.
readonly -a SCRATCH_NEEDED=(
    "scratch/fixture/20100614_Velos1_TaGe_SA_K562_3.mzML"
    "scratch/fixture/20100614_Velos1_TaGe_SA_K562_4.mzML"
    "scratch/fixture/UP000005640_9606.fasta"
    "scratch/percolator/3.09/run-percolator-3.09.sh"
    "scratch/phase05/artefacts/v2026.03.0__comet.linux.exe"
    "scratch/phase05/artefacts/rel-3-07-01__percolator-noxml-ubuntu-portable.zip"
    "scratch/phase05/artefacts/v2.8.1__cometPercolator2LimelightXML.jar"
    "scratch/scientific-path"
)
readonly X11_ROOT="tools/x11-bookworm-20260829/root"
readonly FONTSTACK="tools/fontstack-bookworm-20260829/root"
readonly -a TOOLS_NEEDED=(
    "${X11_ROOT}/usr/bin/Xvfb"
    "${X11_ROOT}/usr/bin/xkbcomp"
    "${X11_ROOT}/usr/share/X11/xkb"
    "${FONTSTACK}/etc/fonts/fonts.conf"
)
readonly SHIM="scripts/feasibility/gui-spike/xkbcomp-path-shim.c"
# The four pinned Linux artefacts the scenarios download (manifests/tools.json).
readonly -a NETWORK_NEEDED=(
    "https://github.com/UWPR/Comet/releases/download/v2026.03.0/comet.linux.exe"
    "https://github.com/percolator/percolator/releases/download/rel-3-07-01/percolator-noxml-ubuntu-portable.zip"
    "https://github.com/wenbostar/PDV/releases/download/v2.7.0/PDV-2.7.0.zip"
    "https://github.com/yeastrc/limelight-import-comet-percolator/releases/download/v2.8.1/cometPercolator2LimelightXML.jar"
)
# The one normalised compiled file: cometgui-app's Maven-filtered build
# identity carries a timestamp.
readonly IDENTITY="cometgui-app/org/cometgui/app/config/build-identity.properties"
readonly IDENTITY_LINE='^cometgui\.buildTimestamp='

readonly APP="cometgui-app"
readonly ARCHIVE_DIR="_build/package/linux"

# The production files the controls damage.
readonly QVALUE_FILTER="cometgui-results/src/main/java/org/cometgui/results/filtering/QValueFilter.java"
readonly MANIFEST_WRITER="cometgui-provenance/src/main/java/org/cometgui/provenance/manifest/ManifestWriter.java"
readonly UPLOAD_CLIENT="cometgui-tools/src/main/java/org/cometgui/tools/limelight/LimelightUploadClient.java"
readonly PERCOLATOR_RERUN="cometgui-workflow/src/main/java/org/cometgui/workflow/steps/PercolatorRerun.java"

# The graded test classes.
readonly T_A_CANON="TierACanonicalScenarioTest"
readonly T_A_NOXML="TierANoXmlScenarioTest"
readonly T_B_CANON="TierBCanonicalScenarioTest"
readonly T_B_NOXML="TierBNoXmlScenarioTest"
readonly T_B_LOCALE="TierBLocaleControlTest"
readonly T_CANARY="EndToEndCanaryTest"
readonly T_H_CHEAP="QValueFilterStatusTest"

readonly -a QUIET=(
    "-Dspotless.check.skip=true"
    "-Dcheckstyle.skip=true"
    "-Dspotbugs.skip=true"
    "-Djacoco.skip=true"
)

# Every control id, in the order they run.  A runs first whatever is selected.
readonly -a ALL_CONTROLS=(L P B 4 6 7 3i 5q 5p 5b H)

PASSED=0
FAILED=0
FAILURES=()
declare -a TIMINGS=()
declare -a RESTORED=()
LEFT_BEHIND=0

# ----------------------------------------------------------------- plumbing --

usage() {
    cat <<USAGE
${SCRIPT_NAME} -- prove the seven PHASE-14 exit gate items: run the end-to-end
tier once (Tier A and Tier B, both scenarios, the failure-path suite), inject
gate item 5's three defects and item 3's one into PRODUCTION code, and check the
harness on itself.

Usage:
  bash scripts/${SCRIPT_NAME}               every control
  bash scripts/${SCRIPT_NAME} --self-test   control H only (with the clean
                                            Tier A run it compares against)
  bash scripts/${SCRIPT_NAME} --only IDS    the named controls, comma-separated,
                                            from: ${ALL_CONTROLS[*]}
                                            (the clean Tier A run, A, always
                                            runs; 4 and 7 also run P and B, or
                                            P, which they read)
  bash scripts/${SCRIPT_NAME} --preflight   check the host and stop
  bash scripts/${SCRIPT_NAME} -h|--help

NEEDS NETWORK.  The scenarios install Comet, Percolator, PDV and the Limelight
converter from the URLs manifests/tools.json pins -- github.com and the
*.githubusercontent.com hosts its release downloads redirect to.  The preflight
fetches the headers of those four artefacts and refuses to start without them.
No Limelight server is contacted: uploads go to the D-007 fake on loopback.

NEEDS THE WORKING TREE'S GITIGNORED DATA, symlinked into a git-archive sandbox:
scratch/fixture (the D-006 LOCAL fixture: both K562 mzML files and the UniProt
proteome), scratch/percolator/3.09, scratch/phase05/artefacts,
scratch/scientific-path; tools/ (JDK, Maven, the X11 closure with Xvfb and the
font stack); _build/m2repo; the host's gcc, curl and /usr/bin/timeout.  Missing
any of them is exit 3 naming it -- never a skipped control.

Writes only under _build/ and /tmp (the tests' own temporary homes and
installs).  Not a stage of scripts/build.sh; run through
scripts/verify-all-gates.sh (as e2e) at a phase's exit gate, or alone.  No CI
job runs it: it needs the LOCAL fixture, and D-006's CI half is open.

Exit status: 0 every control bit; 1 a control failed; 2 misuse; 3 the
environment is not ready; 4 a HARNESS FAILURE (an injection that reached
nothing, a run that ran nothing or zero tests, a process it could not end).
USAGE
}

die() {
    printf '\nFATAL: %s\n' "$1" >&2
    exit "${2:-1}"
}

harness_error() {
    printf '\nHARNESS FAILURE: %s\n' "$1" >&2
    printf 'The run proves nothing and must not be read as a pass.\n' >&2
    exit 4
}

CONTROL_ID=""
CONTROL_STARTED=0

begin_control() {
    CONTROL_ID="$1"
    shift
    CONTROL_STARTED="$(date +%s)"
    printf '\n-------------------------------------------------------------------------------\n'
    printf ' CONTROL %s  %s\n' "${CONTROL_ID}" "$*"
    printf -- '-------------------------------------------------------------------------------\n'
}

end_control() {
    local elapsed=$(( $(date +%s) - CONTROL_STARTED ))
    TIMINGS+=("$(printf '%-3s %5ds  (%dm%02ds)' "${CONTROL_ID}" "${elapsed}" "$((elapsed / 60))" "$((elapsed % 60))")")
    printf '   (control %s took %ds)\n' "${CONTROL_ID}" "${elapsed}"
}

# While control H provokes a failure on purpose, any pass the provoked grading
# prints is NOT counted: the SUMMARY count is what verify-all-gates.sh holds
# to a floor.
DELIBERATE=0
record_pass() {
    if [ "${DELIBERATE}" -eq 1 ]; then
        printf '   (inside control H, not counted: %s)\n' "$*"
        return
    fi
    PASSED=$((PASSED + 1))
    printf '   PASS  %s\n' "$*"
}

record_fail() {
    FAILED=$((FAILED + 1))
    FAILURES+=("$*")
    if [ "${DELIBERATE}" -eq 1 ]; then
        printf '   (refused, as control H requires: %s)\n' "$*"
    else
        printf '   FAIL  %s\n' "$*"
    fi
}

rel() {
    printf '%s' "${1#"${ROOT}/"}"
}

# ------------------------------------------------------------- module sets --
#
# reactor_of TOP -- TOP and every cometgui module it depends on, read from the
# sandbox's own POMs: exactly what `mvn -pl TOP -am` builds.
declare -A REACTORS=()
reactor_of() {
    local top="$1"
    if [ -z "${REACTORS[${top}]:-}" ]; then
        REACTORS[${top}]="$(python3 - "${SANDBOX}" "${top}" <<'PYTHON'
import re
import sys
from pathlib import Path

root, top = Path(sys.argv[1]), sys.argv[2]
deps = {}
for pom in root.glob("cometgui-*/pom.xml"):
    module = pom.parent.name
    text = re.sub(r"<parent>.*?</parent>", "", pom.read_text(encoding="utf-8"), flags=re.S)
    deps[module] = sorted(set(re.findall(r"<artifactId>(cometgui-[a-z-]+)</artifactId>", text)) - {module})
if top not in deps:
    sys.stderr.write("no module %r in the sandbox\n" % top)
    raise SystemExit(1)
seen, todo = set(), [top]
while todo:
    module = todo.pop()
    if module in seen:
        continue
    seen.add(module)
    todo.extend(deps.get(module, []))
missing = sorted(seen - set(deps))
if missing:
    sys.stderr.write("%s depends on modules the sandbox does not hold: %s\n" % (top, missing))
    raise SystemExit(1)
print(",".join(sorted(seen)))
PYTHON
)" || harness_error "the module set of ${top} cannot be read from the sandbox's POMs."
    fi
    printf '%s' "${REACTORS[${top}]}"
}

COMETGUI_REPO_DIGEST=""
cometgui_repo_digest() {
    if [ -d "${M2REPO}/org/cometgui" ]; then
        ( cd -- "${M2REPO}/org/cometgui" && find . -type f -name '*.jar' -print0 | sort -z \
            | xargs -0 -r sha256sum | sha256sum | cut -d' ' -f1 )
    else
        printf 'absent'
    fi
}

# ------------------------------------------------------- bytecode evidence --
#
# class_tree MODULES -- "sha256  <module>/<path>" for every file under the named
# modules' target/classes; build-identity.properties without its timestamp
# line, which must be there (NO-TIMESTAMP-LINE otherwise).
class_tree() {
    local modules="$1" module dir digest path file
    for module in ${modules//,/ }; do
        dir="${SANDBOX}/${module}/target/classes"
        [ -d "${dir}" ] || continue
        ( cd -- "${dir}" && find . -type f -print0 | sort -z | xargs -0 -r sha256sum ) \
            | sed "s#  \./#  ${module}/#"
    done | while read -r digest path; do
        if [ "${path}" = "${IDENTITY}" ]; then
            file="${SANDBOX}/cometgui-app/target/classes/${IDENTITY#cometgui-app/}"
            grep -qE -- "${IDENTITY_LINE}" "${file}" \
                || { printf 'NO-TIMESTAMP-LINE  %s\n' "${path}"; continue; }
            digest="$(grep -vE -- "${IDENTITY_LINE}" "${file}" | sha256sum | cut -d' ' -f1)"
        fi
        printf '%s  %s\n' "${digest}" "${path}"
    done
}

readonly BASELINE_TREE="${LOGS}/baseline-classes.sha256"
BASELINE_MODULES=""

record_baseline_tree() {
    local modules="$1" module count summary=""
    BASELINE_MODULES="${modules}"
    class_tree "${modules}" >"${BASELINE_TREE}"
    for module in ${modules//,/ }; do
        count="$(grep -c "  ${module}/.*\.class\$" "${BASELINE_TREE}" || true)"
        [ "${count}" -gt 0 ] \
            || harness_error "the clean run compiled no class in ${module}, so no clean bytecode can be compared against."
        summary="${summary}${summary:+, }${count} ${module#cometgui-}"
    done
    grep -q "  ${IDENTITY}\$" "${BASELINE_TREE}" \
        || harness_error "the clean run's cometgui-app/target/classes holds no build-identity.properties; the one normalised file is not where this script says it is."
    if grep -q '^NO-TIMESTAMP-LINE' "${BASELINE_TREE}"; then
        harness_error "build-identity.properties has no cometgui.buildTimestamp line, so this script's one normalisation is stale. Re-establish it; do not delete the check."
    fi
    record_pass "baseline bytecode: every compiled module digested (${summary} classes; $(grep -vc '\.class$' "${BASELINE_TREE}") resources)"
}

compiled_prefix() {
    local source="$1"
    case "${source}" in
        */src/main/java/*.java)
            local module="${source%%/*}" within="${source#*/src/main/java/}"
            printf '%s/%s' "${module}" "${within%.java}"
            ;;
        *) harness_error "no compiled form is known for ${source}" ;;
    esac
}

is_compiled_form_of() {
    local prefix="$1" path="$2"
    [ "${path}" = "${prefix}.class" ] && return 0
    case "${path}" in
        "${prefix}\$"*.class) return 0 ;;
    esac
    return 1
}

# compare_tree LOG MODULES [INJECTED SOURCE] -- with an injected source, its
# compiled form MUST differ from the baseline and everything else MUST be
# identical; without one, everything must be identical.  Anything else is a
# harness failure.
compare_tree() {
    local log="$1" modules="$2" injected="${3:-}"
    [ -s "${BASELINE_TREE}" ] || harness_error "no baseline digest of the compiled modules was recorded."
    local now prefix="" module
    now="$(class_tree "${modules}")"
    if grep -q '^NO-TIMESTAMP-LINE' <<<"${now}"; then
        harness_error "$(rel "${log}"): build-identity.properties lost its timestamp line during the run."
    fi
    [ -n "${injected}" ] && prefix="$(compiled_prefix "${injected}")"
    if [ -n "${prefix}" ]; then
        case ",${modules}," in
            *",${prefix%%/*},"*) ;;
            *) harness_error "$(rel "${log}"): ${injected} is not in the run's module set ${modules}, so the run cannot have compiled it." ;;
        esac
    fi

    local -A before=() after=() all=()
    local digest path
    while read -r digest path; do
        module="${path%%/*}"
        case ",${modules}," in
            *",${module},"*) before["${path}"]="${digest}" ;;
        esac
    done <"${BASELINE_TREE}"
    while read -r digest path; do
        [ -n "${path}" ] && after["${path}"]="${digest}"
    done <<<"${now}"
    for path in "${!before[@]}"; do all["${path}"]=1; done
    for path in "${!after[@]}"; do all["${path}"]=1; done

    local changed=0 count=0 others=0
    local -a stray=()
    for path in "${!all[@]}"; do
        if [ -n "${prefix}" ] && is_compiled_form_of "${prefix}" "${path}"; then
            count=$((count + 1))
            [ "${before[${path}]:-absent}" != "${after[${path}]:-absent}" ] && changed=$((changed + 1))
            continue
        fi
        if [ "${before[${path}]:-absent}" != "${after[${path}]:-absent}" ]; then
            stray+=("${path}")
        else
            others=$((others + 1))
        fi
    done

    if [ "${#stray[@]}" -gt 0 ]; then
        harness_error "$(rel "${log}"): ${#stray[@]} file(s) under target/classes differ from the clean baseline although no control damaged them: ${stray[*]:0:5}. A restoration did not reach the bytecode, or the build is not reproducible, so this run's result cannot be attributed."
    fi
    if [ -n "${prefix}" ]; then
        [ "${count}" -gt 0 ] \
            || harness_error "$(rel "${log}"): target/classes has no compiled form of ${injected} at all."
        [ "${changed}" -gt 0 ] \
            || harness_error "$(rel "${log}"): the compiled form of ${injected} is BYTE-IDENTICAL to the clean baseline after the dirty run. The injection reached the source and not the bytecode, so the run tested the clean code and its result is not evidence of anything."
        printf '   bytecode: %d of %d compiled file(s) of the damaged source changed; %d other file(s) identical to the baseline\n' \
            "${changed}" "${count}" "${others}"
    else
        printf '   bytecode: all %d compiled file(s) of %s identical to the clean baseline\n' "${others}" "${modules}"
    fi
}

# --------------------------------------------------------- running Maven --
#
# The reaper: runs a command as a child subreaper, so that every process it
# leaves behind -- Xvfb, a tool, the packaged application, wherever on disk it
# runs -- is re-parented here and can be found, waited for and ended.
readonly REAPER="${LOGS}/reaper.py"
write_reaper() {
    cat >"${REAPER}" <<'PYTHON'
"""Run a command as a child subreaper; end and count whatever it leaves behind."""
import ctypes
import os
import signal
import subprocess
import sys
import time

PR_SET_CHILD_SUBREAPER = 36
libc = ctypes.CDLL(None, use_errno=True)
if libc.prctl(PR_SET_CHILD_SUBREAPER, 1, 0, 0, 0) != 0:
    print("=== REAPER: prctl(PR_SET_CHILD_SUBREAPER) failed, errno %d ===" % ctypes.get_errno())
    sys.exit(97)

me = str(os.getpid())


def reap_zombies():
    while True:
        try:
            pid, _ = os.waitpid(-1, os.WNOHANG)
        except ChildProcessError:
            return
        if pid == 0:
            return


def children():
    found = []
    for pid in os.listdir("/proc"):
        if not pid.isdigit():
            continue
        try:
            with open("/proc/%s/stat" % pid, encoding="utf-8", errors="replace") as handle:
                rest = handle.read().rsplit(")", 1)[1].split()
            with open("/proc/%s/cmdline" % pid, "rb") as handle:
                command = handle.read().replace(b"\0", b" ").decode("utf-8", "replace")
        except (OSError, IndexError):
            continue
        if rest[1] == me and rest[0] != "Z":
            found.append((int(pid), command[:200]))
    return found


command = subprocess.Popen(sys.argv[1:])
status = command.wait()
sys.stdout.flush()
reap_zombies()
left = children()
waited = 0
while left and waited < 20:
    time.sleep(1)
    waited += 1
    reap_zombies()
    left = children()
ended = []
for _ in range(5):
    if not left:
        break
    ended.extend(left)
    for pid, _ in left:
        try:
            os.kill(pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
    time.sleep(3)
    reap_zombies()
    for pid, _ in children():
        try:
            os.kill(pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
    time.sleep(1)
    reap_zombies()
    left = children()
print("\n=== LEFT BEHIND: %d ===" % len({pid for pid, _ in ended}))
for pid, text in ended:
    print("left behind: %d %s" % (pid, text))
print("=== SURVIVED KILL: %d ===" % len(left))
for pid, text in left:
    print("survived: %d %s" % (pid, text))
sys.exit(status if status >= 0 else 128 - status)
PYTHON
}

# run_mvn LOG TOP SELECTORS -- the one test command shape: TOP and its upstream
# modules in one reactor, nothing installed, test failures not stopping it, so
# a non-zero exit means the build itself failed.  MVN_ENV (an array) prefixes
# the command (env ...), MVN_EXTRA (an array) adds Maven arguments, and
# MVN_GOAL is the goal (test).  The surefire reports of every module of the
# reactor are deleted first, so every report read afterwards is this run's, and
# are then copied to REPORTS/RUN_LABEL.  The exit status, both test counts and
# every failing testcase's message are written INTO the log.
declare -a MVN_ENV=()
declare -a MVN_EXTRA=()
MVN_GOAL="test"
RUN_LABEL=""
RUN_MODULES=""
RUN_STARTED=0
run_mvn() {
    local log="$1" top="$2" selectors="$3" rc=0 module
    RUN_MODULES="$(reactor_of "${top}")"
    for module in ${RUN_MODULES//,/ }; do
        rm -rf -- "${SANDBOX}/${module}/target/surefire-reports"
    done
    local -a selection=()
    [ -n "${selectors}" ] && selection=("-Dtest=${selectors}" "-Dsurefire.failIfNoSpecifiedTests=false")
    RUN_STARTED="$(date +%s)"
    ( cd -- "${SANDBOX}" \
        && "${MVN_ENV[@]+"${MVN_ENV[@]}"}" python3 "${REAPER}" \
            mvn -B -o -Dmaven.repo.local="${M2REPO}" "${QUIET[@]}" \
            -pl "${top}" -am "${MVN_GOAL}" \
            -Dmaven.test.failure.ignore=true \
            "${selection[@]+"${selection[@]}"}" "${MVN_EXTRA[@]+"${MVN_EXTRA[@]}"}" ) \
        >"${log}" 2>&1 || rc=$?
    printf '\n=== MVN EXIT STATUS: %d ===\n' "${rc}" >>"${log}"
    local left survived
    left="$(sed -n 's/^=== LEFT BEHIND: \([0-9][0-9]*\) ===$/\1/p' "${log}" | tail -1)"
    survived="$(sed -n 's/^=== SURVIVED KILL: \([0-9][0-9]*\) ===$/\1/p' "${log}" | tail -1)"
    [ -n "${left}" ] && [ -n "${survived}" ] \
        || harness_error "$(rel "${log}"): the reaper did not report what the run left behind; this script cannot tell whether a process of this run is still alive."
    [ "${survived}" -eq 0 ] \
        || harness_error "$(rel "${log}"): ${survived} process(es) the run started survived KILL: $(grep '^survived: ' "${log}" | head -3 | tr '\n' ' ')"
    if [ "${left}" -gt 0 ]; then
        LEFT_BEHIND=$((LEFT_BEHIND + left))
        printf '   processes: %d left running by the run were ended: %s\n' "${left}" \
            "$(grep '^left behind: ' "${log}" | head -3 | cut -c14-90 | tr '\n' ';')"
    fi
    if [ -n "${RUN_LABEL}" ]; then
        rm -rf -- "${REPORTS:?}/${RUN_LABEL}"
        for module in ${RUN_MODULES//,/ }; do
            if [ -d "${SANDBOX}/${module}/target/surefire-reports" ]; then
                mkdir -p -- "${REPORTS}/${RUN_LABEL}/${module}"
                cp -p -- "${SANDBOX}/${module}/target/surefire-reports"/TEST-*.xml \
                    "${REPORTS}/${RUN_LABEL}/${module}/" 2>/dev/null || true
            fi
        done
    fi
    append_failures "${log}" "${RUN_MODULES}"
    append_console_counts "${log}"
    if [ "${MVN_GOAL}" = "test" ]; then
        printf '   %s test(s) RAN by the XML reports (%s reported, %s skipped), %s by Maven'"'"'s module summaries\n' \
            "$(ran_count "${log}")" "$(executed_count "${log}")" "$(skipped_count "${log}")" "$(console_ran "${log}")"
    fi
    return "${rc}"
}

mvn_shape() {
    printf 'mvn -o -pl %s -am %s -Dmaven.test.failure.ignore=true -Dtest=%s %s' "$1" "${MVN_GOAL}" "$2" \
        "${MVN_EXTRA[*]+"${MVN_EXTRA[*]}"}"
}

# append_failures LOG MODULES -- testcases reported, skipped and run in the
# fresh reports, then every failed or errored testcase with its message and the
# first lines of its trace, unescaped, under a marker the grading reads.
append_failures() {
    local log="$1" modules="$2"
    # shellcheck disable=SC2086
    python3 - "${SANDBOX}" "${RUN_STARTED}" ${modules//,/ } >>"${log}" <<'PYTHON'
import glob
import os
import sys
import xml.etree.ElementTree as ET

root, started, modules = sys.argv[1], int(sys.argv[2]), sys.argv[3:]
executed = skipped = 0
stale = []
failed = []
for module in modules:
    for path in sorted(glob.glob("%s/%s/target/surefire-reports/TEST-*.xml" % (root, module))):
        if os.stat(path).st_mtime < started - 1:
            stale.append(path)
        suite = ET.parse(path).getroot()
        for case in suite.iter("testcase"):
            executed += 1
            if case.find("skipped") is not None:
                skipped += 1
            for kind in ("failure", "error"):
                node = case.find(kind)
                if node is not None:
                    failed.append((suite.get("name"), case.get("name"), kind, node))
print("\n=== %d TESTCASE(S) REPORTED ===" % executed)
print("=== %d TESTCASE(S) SKIPPED ===" % skipped)
print("=== %d TESTCASE(S) RAN ===" % (executed - skipped))
print("=== %d STALE REPORT(S) ===" % len(stale))
for path in stale:
    print("stale report: %s" % path)
print("=== FAILED TESTCASES FROM SUREFIRE XML ===")
for suite, name, kind, node in failed:
    print("FAILED %s#%s [%s] %s" % (suite, name, kind, node.get("type", "")))
    message = node.get("message") or ""
    for line in message.splitlines()[:60]:
        print("  message: %s" % line)
    for line in (node.text or "").splitlines()[:25]:
        print("  trace: %s" % line)
print("=== %d FAILED TESTCASE(S) ===" % len(failed))
PYTHON
}

# append_console_counts LOG -- the second count, from Maven's per-module
# summary lines ("Tests run: N, Failures: F, Errors: E, Skipped: S" with no
# "-- in <class>" suffix): run = N - S.
append_console_counts() {
    local log="$1"
    python3 - "${log}" >>"${log}" <<'PYTHON'
import re
import sys

summary = re.compile(
    r"^\[(?:INFO|WARNING|ERROR)\] Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)$")
ran = bad = modules = 0
with open(sys.argv[1], encoding="utf-8", errors="replace") as handle:
    for line in handle:
        match = summary.match(line.rstrip("\n"))
        if match:
            modules += 1
            ran += int(match.group(1)) - int(match.group(4))
            bad += int(match.group(2)) + int(match.group(3))
print("=== CONSOLE: %d TEST(S) RAN, %d FAILED OR IN ERROR, IN %d MODULE SUMMARY LINE(S) ===" % (ran, bad, modules))
PYTHON
}

failed_count() { sed -n 's/^=== \([0-9][0-9]*\) FAILED TESTCASE(S) ===$/\1/p' "$1" | tail -1; }
executed_count() { sed -n 's/^=== \([0-9][0-9]*\) TESTCASE(S) REPORTED ===$/\1/p' "$1" | tail -1; }
skipped_count() { sed -n 's/^=== \([0-9][0-9]*\) TESTCASE(S) SKIPPED ===$/\1/p' "$1" | tail -1; }
ran_count() { sed -n 's/^=== \([0-9][0-9]*\) TESTCASE(S) RAN ===$/\1/p' "$1" | tail -1; }
stale_count() { sed -n 's/^=== \([0-9][0-9]*\) STALE REPORT(S) ===$/\1/p' "$1" | tail -1; }
console_ran() { sed -n 's/^=== CONSOLE: \([0-9][0-9]*\) TEST(S) RAN, .*/\1/p' "$1" | tail -1; }
console_failed() { sed -n 's/^=== CONSOLE: [0-9][0-9]* TEST(S) RAN, \([0-9][0-9]*\) FAILED.*/\1/p' "$1" | tail -1; }

# require_counts LABEL LOG -- both counts present and at least one, and no
# stale report.  A run in which zero tests RAN -- every one reported skipped
# counts as zero -- is never a pass and never a red: it is this harness not
# knowing what it ran.
require_counts() {
    local label="$1" log="$2" xml console stale
    xml="$(ran_count "${log}")"
    console="$(console_ran "${log}")"
    stale="$(stale_count "${log}")"
    [ -n "${xml}" ] && [ -n "${console}" ] && [ -n "${stale}" ] \
        || harness_error "(${label}) $(rel "${log}") carries no test count from the XML (${xml:-none}) or from Maven's module summaries (${console:-none})."
    [ "${stale}" -eq 0 ] \
        || harness_error "(${label}) $(rel "${log}") read ${stale} surefire report(s) older than the run; reports were not cleared, so what ran cannot be told from what had run before."
    [ "${xml}" -ge 1 ] && [ "${console}" -ge 1 ] \
        || harness_error "(${label}) $(rel "${log}") RAN ${xml} test(s) by the XML reports ($(executed_count "${log}") reported, $(skipped_count "${log}") skipped) and ${console} by Maven's module summaries. A run in which zero tests ran cannot prove or disprove a gate, so this run tested nothing."
    [ "${xml}" -ge "${console}" ] \
        || harness_error "(${label}) $(rel "${log}"): surefire's XML reports hold ${xml} run testcase(s) and Maven counted ${console}, so a report was overwritten (two @Nested classes of one test class in one selection) or two tests share a name. Split the selection (see batches_of)."
}

# batches_of SELECTORS -- the selection split into as few runs as keep every
# report whole: no run names two different classes (an outer class and one of
# its @Nested classes, or two of them) of one test class.
batches_of() {
    python3 - "$1" <<'PYTHON'
import sys

batches = []
for selector in (part for part in sys.argv[1].split(",") if part):
    named = selector.split("#")[0]
    outer = named.split("$")[0]
    for classes, items in batches:
        if classes.get(outer, named) == named:
            classes[outer] = named
            items.append(selector)
            break
    else:
        batches.append(({outer: named}, [selector]))
for _, items in batches:
    print(",".join(items))
PYTHON
}

red_count() {
    local log="$1" xml console
    xml="$(failed_count "${log}")"
    console="$(console_failed "${log}")"
    [ -n "${xml}" ] || { printf ''; return; }
    if [ -n "${console}" ] && [ "${console}" -gt "${xml}" ]; then
        printf '%s' "${console}"
    else
        printf '%s' "${xml}"
    fi
}

selector_class() {
    local selector="${1%%#*}"
    printf '%s' "${selector%%\$*}"
}

# test_source_of CLASS MODULES -- the one test source of that simple name in
# the run's modules (surefire matches -Dtest by simple name).
test_source_of() {
    local class="$1" modules="$2" found module
    local -a dirs=()
    for module in ${modules//,/ }; do
        [ -d "${SANDBOX}/${module}/src/test/java" ] && dirs+=("${module}/src/test/java")
    done
    found="$(cd -- "${SANDBOX}" && find "${dirs[@]}" -name "${class}.java" -type f | head -2)"
    [ -n "${found}" ] \
        || harness_error "no test class ${class}.java exists in the sandbox's ${modules}. A control naming a test that does not exist tests nothing."
    [ "$(printf '%s\n' "${found}" | wc -l)" -eq 1 ] \
        || harness_error "the test class name ${class} is ambiguous in the sandbox: ${found}"
    printf '%s' "${found}"
}

report_of() {
    local class="$1" module
    for module in ${RUN_MODULES//,/ }; do
        find "${SANDBOX}/${module}/target/surefire-reports" -maxdepth 1 \
            -name "TEST-*.${class}.xml" 2>/dev/null || true
    done | head -1
}

# testcase_outcome CLASS METHOD -- passed, failed, skipped or absent, from the
# class's XML report.  A parameterised method passes only if every invocation
# does; a skipped step is NOT a pass.
testcase_outcome() {
    local class="$1" method="$2" report
    report="$(report_of "${class}")"
    python3 - "${report}" "${method}" <<'PYTHON'
import sys
import xml.etree.ElementTree as ET

report, method = sys.argv[1:3]
outcome = "absent"
if report:
    for case in ET.parse(report).getroot().iter("testcase"):
        name = case.get("name", "")
        if name == method or (method and (name.startswith(method + "(")
                or name.startswith(method + "[") or name.startswith(method + "{"))):
            if case.find("failure") is not None or case.find("error") is not None:
                outcome = "failed"
            elif case.find("skipped") is not None:
                if outcome in ("absent", "passed"):
                    outcome = "skipped"
            elif outcome == "absent":
                outcome = "passed"
print(outcome)
PYTHON
}

# class_tally CLASS -- "reported skipped failed passed" for one class's report.
class_tally() {
    local report
    report="$(report_of "$1")"
    [ -n "${report}" ] || { printf 'none'; return; }
    python3 - "${report}" <<'PYTHON'
import sys
import xml.etree.ElementTree as ET

reported = skipped = failed = 0
for case in ET.parse(sys.argv[1]).getroot().iter("testcase"):
    reported += 1
    if case.find("failure") is not None or case.find("error") is not None:
        failed += 1
    elif case.find("skipped") is not None:
        skipped += 1
print(reported, skipped, failed, reported - skipped - failed)
PYTHON
}

# verify_classes_ran LOG SELECTORS -- every class a selector names produced a
# fresh report in which at least one test RAN, a bare class selector names no
# class with @Nested tests, and every method named ran (not skipped).
verify_classes_ran() {
    local log="$1" selectors="$2"
    local -a wanted=() methods=()
    local selector class report source method outcome tally
    IFS=',' read -r -a wanted <<<"${selectors}"
    for selector in "${wanted[@]}"; do
        class="$(selector_class "${selector}")"
        source="$(test_source_of "${class}" "${RUN_MODULES}")"
        report="$(report_of "${class}")"
        if [ -z "${report}" ] || [ ! -s "${report}" ]; then
            harness_error "${selector} produced no surefire report for $(rel "${log}"). The command named a test that surefire did not execute, so this run tested nothing."
        fi
        tally="$(class_tally "${class}")"
        read -r -a counts <<<"${tally}"
        [ "${counts[0]}" -ge 1 ] && [ $(( counts[0] - counts[1] )) -ge 1 ] \
            || harness_error "${selector}: ${counts[0]} test(s) reported and ${counts[1]} of them skipped in $(rel "${log}"). Zero tests ran, which cannot prove or disprove a gate, so this run tested nothing."
        if [ "${selector}" = "${selector%%#*}" ]; then
            if grep -q '@Nested' "${SANDBOX}/${source}"; then
                harness_error "the selector ${selector} names a class with @Nested tests, which surefire drops from a class selector in a list without a word. Name the method (Outer\$Inner#method)."
            fi
            continue
        fi
        IFS='+' read -r -a methods <<<"${selector#*#}"
        for method in "${methods[@]}"; do
            outcome="$(testcase_outcome "${class}" "${method}")"
            case "${outcome}" in
                absent) harness_error "${class}#${method} did not run in $(rel "${log}"); the selector ${selector} selects less than it names, so this run tested nothing it named." ;;
                skipped) harness_error "${class}#${method} was SKIPPED in $(rel "${log}"): zero runs of a test the selection names, which is never a pass." ;;
            esac
        done
    done
}

# require_every_test_ran LABEL CLASS -- every @Test method in the class's
# source is in its fresh report, none skipped, none failed.
require_every_test_ran() {
    local label="$1" class="$2" source declared tally
    source="$(test_source_of "${class}" "${RUN_MODULES}")"
    declared="$(grep -cE '^[[:space:]]*@Test[[:space:]]*$' "${SANDBOX}/${source}" || true)"
    [ "${declared}" -ge 1 ] || harness_error "(${label}) ${source} declares no @Test method that this script can count."
    if grep -qE '@(Nested|ParameterizedTest|RepeatedTest|TestFactory)' "${SANDBOX}/${source}"; then
        harness_error "(${label}) ${source} has nested, parameterised or dynamic tests; counting its @Test lines would not be the number of tests that must run."
    fi
    tally="$(class_tally "${class}")"
    [ "${tally}" != "none" ] || harness_error "(${label}) no surefire report for ${class}."
    read -r -a counts <<<"${tally}"
    if [ "${counts[0]}" -eq "${declared}" ] && [ "${counts[1]}" -eq 0 ] \
        && [ "${counts[2]}" -eq 0 ] && [ "${counts[3]}" -eq "${declared}" ]; then
        record_pass "${label}: ${class} -- ${counts[3]} of its ${declared} @Test methods RAN and passed (counted from this run's surefire XML: ${counts[0]} reported, ${counts[1]} skipped, ${counts[2]} failed)"
    else
        record_fail "${label}: ${class} -- of its ${declared} @Test methods the report holds ${counts[0]}, ${counts[1]} skipped, ${counts[2]} failed, ${counts[3]} passed; every one must run and pass"
        grep -A3 "^FAILED .*${class}#" "${CURRENT_LOG}" | head -12 | cut -c1-240 | sed 's/^/         /' || true
    fi
}
CURRENT_LOG=""

# require_report_property LABEL CLASS NAME VALUE -- the forked JVM's system
# property as surefire recorded it in the class's report.
require_report_property() {
    local label="$1" class="$2" name="$3" value="$4" report got
    report="$(report_of "${class}")"
    got="$(python3 - "${report}" "${name}" <<'PYTHON'
import sys
import xml.etree.ElementTree as ET

for prop in ET.parse(sys.argv[1]).getroot().iter("property"):
    if prop.get("name") == sys.argv[2]:
        print(prop.get("value"))
        break
else:
    print("(not recorded)")
PYTHON
)"
    if [ "${got}" = "${value}" ]; then
        record_pass "${label}: ${class}'s forked JVM ran with ${name}=${got} (from its report)"
    else
        record_fail "${label}: ${class}'s forked JVM ran with ${name}=${got}, not ${value}"
    fi
}

assert_testcase() {
    local label="$1" want="$2" class="$3" method="$4" got
    got="$(testcase_outcome "${class}" "${method}")"
    [ "${got}" != "absent" ] \
        || harness_error "(${label}) ${class}.${method} is not in the report, so its outcome cannot be read."
    if [ "${got}" = "${want}" ]; then
        record_pass "${label}: ${class}.${method} ${got}"
    else
        record_fail "${label}: ${class}.${method} ${got}, and this control requires it ${want}"
    fi
}

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

# grade_red MODE LABEL FAILED-COUNT LOG EXPECTED -- separate from running, so
# control H can grade a run it knows is wrong and require the refusal.  The
# expected words are matched only in the failure section the XML wrote, never
# in what the build printed.
grade_red() {
    local mode="$1" label="$2" failed="$3" log="$4" expected="$5"
    if [ "${failed}" -eq 0 ]; then
        record_fail "${label}: HARNESS FAILURE -- the check PASSED with the defect present. Either the gate is dead or the injection never reached the running code (log: $(rel "${log}"))"
        return
    fi
    local section="${log}.failures"
    sed -n '/^=== FAILED TESTCASES FROM SUREFIRE XML ===$/,/^=== [0-9][0-9]* FAILED TESTCASE(S) ===$/p' \
        "${log}" >"${section}"
    if ! log_has "${section}" "${expected}" "${mode}"; then
        record_fail "${label}: HARNESS FAILURE -- red, but without the expected failure's own words '${expected}'; a red for another reason is not this gate biting (log: $(rel "${log}"))"
        printf '         first failure: %s\n' \
            "$(grep -m1 -A1 '^FAILED ' "${log}" | tr '\n' ' ' | cut -c1-240 || true)"
        return
    fi
    record_pass "${label}: rejected, ${failed} testcase(s) red"
    printf '         %s\n' "$(log_first_match "${section}" "${expected}" "${mode}" | sed 's/^ *//' | cut -c1-240)"
}

# dirty_run LABEL SOURCE TOP SELECTORS LOG -- runs the selection on the damaged
# sandbox and proves what it ran; leaves the red count in DIRTY_FAILED.
DIRTY_FAILED=0
dirty_run() {
    local label="$1" source="$2" top="$3" selectors="$4" log="$5" rc=0
    DIRTY_FAILED=0
    CURRENT_LOG="${log}"
    run_mvn "${log}" "${top}" "${selectors}" || rc=$?
    if grep -qE 'COMPILATION ERROR|Compilation failure' "${log}"; then
        harness_error "(${label}) the damaged sandbox does not compile -- see $(rel "${log}"). A red that never ran a test is not a result."
    fi
    [ "${rc}" -eq 0 ] \
        || harness_error "(${label}) Maven exited ${rc} although test failures do not stop it here: the build itself failed -- see $(rel "${log}")."
    if grep -qE 'NoClassDefFoundError|ClassNotFoundException: org\.cometgui' "${log}"; then
        harness_error "(${label}) $(rel "${log}") carries NoClassDefFoundError/ClassNotFoundException for project code: the red is the harness's, not the gate's."
    fi
    compare_tree "${log}" "${RUN_MODULES}" "${source}"
    require_counts "${label}" "${log}"
    verify_classes_ran "${log}" "${selectors}"
    DIRTY_FAILED="$(red_count "${log}")"
    [ -n "${DIRTY_FAILED}" ] || harness_error "(${label}) $(rel "${log}") carries no failed-testcase count."
}

# clean_run LABEL TOP SELECTORS LOG -- an undamaged run: exit 0, both counts,
# every named class ran.  Its red count is left in CLEAN_FAILED for the caller
# to grade per class.
CLEAN_FAILED=0
clean_run() {
    local label="$1" top="$2" selectors="$3" log="$4" rc=0
    CURRENT_LOG="${log}"
    printf '   %s\n' "$(mvn_shape "${top}" "${selectors}")"
    run_mvn "${log}" "${top}" "${selectors}" || rc=$?
    [ "${rc}" -eq 0 ] \
        || harness_error "(${label}) Maven exited ${rc} although test failures do not stop it here: the build itself failed -- see $(rel "${log}") ($(grep -m2 -E 'ERROR\]|BUILD FAILURE' "${log}" | tr '\n' ' ' | cut -c1-200))."
    require_counts "${label}" "${log}"
    verify_classes_ran "${log}" "${selectors}"
    CLEAN_FAILED="$(red_count "${log}")"
}

# ------------------------------------------------- injection and its guards --

readonly PRISTINE_SUMS="${LOGS}/pristine-sources.sha256"

save_pristine() {
    local file="$1"
    mkdir -p -- "$(dirname -- "${PRISTINE}/${file}")"
    cp -p -- "${SANDBOX}/${file}" "${PRISTINE}/${file}"
    ( cd -- "${SANDBOX}" && sha256sum -- "${file}" ) >>"${PRISTINE_SUMS}"
}

# replace_once LABEL FILE OLD NEW -- the anchor must match EXACTLY ONCE before;
# afterwards the replacement must be there and the anchor gone (unless the
# replacement itself contains it).
replace_once() {
    local label="$1" file="$2" old="$3" new="$4" rc=0
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
text = text.replace(old, new)
if new not in text or (old not in new and old in text):
    sys.stderr.write("the replacement for %r did not land in %s\n" % (label, path))
    raise SystemExit(1)
with open(path, "w", encoding="utf-8") as handle:
    handle.write(text)
PYTHON
    if [ "${rc}" -ne 0 ]; then
        harness_error "(${label}) the injection anchor is gone from ${file}. The source moved under this control, which would inject nothing and report green."
    fi
}

assert_modified() {
    local label="$1" file="$2"
    [ -e "${SANDBOX}/${file}" ] || harness_error "(${label}) ${file} is missing from the sandbox."
    [ -e "${PRISTINE}/${file}" ] || harness_error "(${label}) no pristine copy of ${file} was taken, so nothing can be compared."
    if cmp -s "${SANDBOX}/${file}" "${PRISTINE}/${file}"; then
        harness_error "(${label}) ${file} is byte-identical to the pristine copy. The defect was not injected and the control would have tested nothing."
    fi
    printf '   injected %s (%s changed line(s) against the pristine copy)\n' "${file}" \
        "$(diff "${PRISTINE}/${file}" "${SANDBOX}/${file}" | grep -c '^[<>]' || true)"
}

# restore_pristine FILE -- copied back, then PROVED back: sha256sum -c of the
# digest taken before the injection.
restore_pristine() {
    local file="$1" check
    [ -e "${PRISTINE}/${file}" ] || harness_error "no pristine copy of ${file} to restore from."
    cp -- "${PRISTINE}/${file}" "${SANDBOX}/${file}"
    touch -- "${SANDBOX}/${file}"
    check="$( cd -- "${SANDBOX}" && grep -F "  ${file}" "${PRISTINE_SUMS}" | tail -1 | sha256sum -c - 2>&1 )" \
        || harness_error "could not restore ${file} in the sandbox: sha256sum -c says ${check}"
    RESTORED+=("${file}")
    printf '   restored %s -- sha256sum -c: %s\n' "${file}" "${check}"
}

# inject_and_run LABEL SOURCE SELECTORS MODE EXPECTED OLD NEW [OLD NEW]...
DIRTY_LOG=""
inject_and_run() {
    local label="$1" source="$2" selectors="$3" mode="$4" expected="$5"
    shift 5
    [ "$#" -ge 2 ] && [ $(( $# % 2 )) -eq 0 ] \
        || harness_error "(${label}) an injection needs anchor and replacement pairs."
    save_pristine "${source}"
    local anchors=0
    while [ "$#" -gt 0 ]; do
        anchors=$((anchors + 1))
        replace_once "${label} [anchor ${anchors}]" "${source}" "$1" "$2"
        shift 2
    done
    assert_modified "${label}" "${source}"
    DIRTY_LOG="${LOGS}/${CONTROL_ID}-dirty.log"
    RUN_LABEL=""
    printf '   %s\n' "$(mvn_shape "${APP}" "${selectors}")"
    dirty_run "${label}" "${source}" "${APP}" "${selectors}" "${DIRTY_LOG}"
    grade_red "${mode}" "${label}" "${DIRTY_FAILED}" "${DIRTY_LOG}" "${expected}"
}

# ------------------------------------------------------------- the sandbox --
build_sandbox() {
    rm -rf -- "${SANDBOX}" "${PRISTINE}"
    mkdir -p -- "${SANDBOX}" "${PRISTINE}"
    ( cd -- "${ROOT}" && git archive HEAD ) | tar -x -C "${SANDBOX}" \
        || harness_error "git archive HEAD could not be extracted into the sandbox."
    # Gitignored, and read through the repository root the tests find above
    # their working directory (the directory holding manifests/), which is the
    # sandbox: the fixture and artefacts, the toolchain and closures, and the
    # offline Maven repository.
    ln -s -- "${ROOT}/scratch" "${SANDBOX}/scratch"
    ln -s -- "${ROOT}/tools" "${SANDBOX}/tools"
    mkdir -p -- "${SANDBOX}/_build"
    ln -s -- "${M2REPO}" "${SANDBOX}/_build/m2repo"
    local needed
    for needed in "${SCRATCH_NEEDED[@]}" "${TOOLS_NEEDED[@]}" "${SHIM}" "_build/m2repo/org" "manifests/tools.json"; do
        [ -e "${SANDBOX}/${needed}" ] \
            || harness_error "the sandbox does not resolve ${needed}. Every control grading a test that reads it would fail for the wrong reason."
    done
    local head dirty
    head="$(cd -- "${ROOT}" && git rev-parse --short HEAD)"
    record_pass "sandbox: git archive ${head} in $(rel "${SANDBOX}"), with scratch/, tools/ and _build/m2repo linked from the working tree and every input the scenarios read resolved"
    dirty="$(cd -- "${ROOT}" && git status --porcelain -- 'cometgui-*/src' scripts packaging manifests pom.xml 'cometgui-*/pom.xml' | head -20)"
    if [ -n "${dirty}" ]; then
        printf '\n  NOTE: the working tree has uncommitted changes this harness reads.\n'
        printf '        This run proves the gates of HEAD (%s), not of the working tree:\n' "${head}"
        printf '%s\n' "${dirty}" | sed 's/^/          /'
    fi
}

# ------------------------------------------------------------- the controls --

TIER_A_DONE=0
# A: the clean Tier A run, both scenarios, once.  Always runs: its bytecode is
# the baseline, and every injection reruns one of its classes.
control_A() {
    begin_control "A" "items 1 and 3, Tier A: both scenarios, once, under LC_ALL=C.UTF-8, every @Test method run"
    COMETGUI_REPO_DIGEST="$(cometgui_repo_digest)"
    local log="${LOGS}/A-tier-a.log"
    RUN_LABEL="A"
    MVN_EXTRA=("-Dcometgui.e2e=true")
    clean_run "Tier A" "${APP}" "${T_A_CANON},${T_A_NOXML}" "${log}"
    record_baseline_tree "${RUN_MODULES}"
    require_every_test_ran "item 1, the canonical scenario in Tier A" "${T_A_CANON}"
    require_report_property "item 1, the runner's locale" "${T_A_CANON}" "sun.jnu.encoding" "UTF-8"
    require_every_test_ran "item 3, the no-XML scenario in Tier A" "${T_A_NOXML}"
    assert_testcase "item 3, Tier A: \"Comet was not rerun\" (step 5, from disk)" passed "${T_A_NOXML}" cometNotRerun
    require_report_property "item 3, the runner's locale" "${T_A_NOXML}" "sun.jnu.encoding" "UTF-8"
    if [ "${FAILED}" -ne 0 ]; then
        printf '   the clean Tier A run is not green; nothing below could be attributed to an injection.\n'
    fi
    TIER_A_DONE=1
    end_control
}

# L: the class's own locale assertion bites -- no locale at all.
control_L() {
    begin_control "L" "item 1's locale: the canonical Tier A class launched with no locale fails first, naming the encoding"
    local log="${LOGS}/L-no-locale.log" rc=0 name
    local -a strip=(env)
    while IFS='=' read -r name _; do
        case "${name}" in
            LANG|LANGUAGE|LC_*) strip+=("-u" "${name}") ;;
        esac
    done < <(env)
    MVN_ENV=("${strip[@]}")
    MVN_EXTRA=("-Dcometgui.e2e=true")
    RUN_LABEL=""
    CURRENT_LOG="${log}"
    printf '   env %s %s\n' "$(printf '%s ' "${strip[@]:1}")" "$(mvn_shape "${APP}" "${T_A_CANON}")"
    run_mvn "${log}" "${APP}" "${T_A_CANON}" || rc=$?
    MVN_ENV=()
    [ "${rc}" -eq 0 ] || harness_error "(L) Maven exited ${rc}: the build itself failed -- see $(rel "${log}")."
    compare_tree "${log}" "${RUN_MODULES}"
    local red
    red="$(red_count "${log}")"
    [ -n "${red}" ] || harness_error "(L) $(rel "${log}") carries no failed-testcase count."
    grade_red regex "the canonical Tier A class with no locale" "${red}" "${log}" \
        'needs a UTF-8 runner, and this test JVM \(pid [0-9]+\) has sun\.jnu\.encoding=[^,]+, not UTF-8'
    require_report_property "  ... and the forked JVM really ran without one" "${T_A_CANON}" \
        "sun.jnu.encoding" "ANSI_X3.4-1968"
    local tally
    tally="$(class_tally "${T_A_CANON}")"
    read -r -a counts <<<"${tally}"
    if [ "${tally}" != "none" ] && [ "${counts[3]}" -eq 0 ] \
        && [ "$(testcase_outcome "${T_A_CANON}" freshHome)" = "absent" ]; then
        record_pass "  ... and not one step ran: the class failed before its first step (${counts[0]} testcase(s), ${counts[3]} passed)"
    else
        record_fail "  ... a step of ${T_A_CANON} ran without a UTF-8 locale (${tally}); the assertion is not the first thing the class does"
    fi
    end_control
}

ARCHIVE=""
# P: the packaged artefact, built in the sandbox from HEAD.
control_P() {
    begin_control "P" "item 2's artefact: build-app-image.sh in the sandbox, the archive verified against its SHA-256"
    local log="${LOGS}/P-build-app-image.log" rc=0
    ( cd -- "${SANDBOX}" && python3 "${REAPER}" bash scripts/package/build-app-image.sh ) >"${log}" 2>&1 || rc=$?
    [ "${rc}" -eq 0 ] || harness_error "(P) scripts/package/build-app-image.sh exited ${rc}: $(tail -3 "${log}" | tr '\n' ' ' | cut -c1-240)"
    local -a archives=()
    shopt -s nullglob
    archives=("${SANDBOX}/${ARCHIVE_DIR}"/CometGUI-*-linux-x86_64.tar.gz)
    shopt -u nullglob
    [ "${#archives[@]}" -eq 1 ] || harness_error "(P) ${ARCHIVE_DIR} in the sandbox holds ${#archives[@]} archive(s), not one."
    ARCHIVE="${archives[0]}"
    local check
    if check="$(cd -- "$(dirname -- "${ARCHIVE}")" && sha256sum -c -- "$(basename -- "${ARCHIVE}").sha256" 2>&1)"; then
        record_pass "the archive $(rel "${ARCHIVE}") ($(stat -c %s -- "${ARCHIVE}") bytes) was built by this run and verifies: ${check}"
    else
        record_fail "the archive does not verify against its .sha256: ${check}"
    fi
    # Listed to a file first: grep -q stops reading early, and tar's SIGPIPE
    # would then fail the pipeline under pipefail whatever the archive holds.
    local listing="${LOGS}/P-archive-listing.txt"
    tar -tzf "${ARCHIVE}" >"${listing}" || harness_error "(P) the archive cannot be listed."
    if grep -qx 'CometGUI/bin/CometGUI' "${listing}" \
        && grep -qx 'CometGUI/lib/runtime/bin/java' "${listing}"; then
        record_pass "  ... and lists the launcher CometGUI/bin/CometGUI and the bundled CometGUI/lib/runtime/bin/java"
    else
        record_fail "the archive lists no CometGUI/bin/CometGUI or no CometGUI/lib/runtime/bin/java"
    fi
    end_control
}

archive_unchanged() {
    local label="$1" check
    [ -n "${ARCHIVE}" ] || return 0
    if check="$(cd -- "$(dirname -- "${ARCHIVE}")" && sha256sum -c -- "$(basename -- "${ARCHIVE}").sha256" 2>&1)"; then
        record_pass "${label}: the archive Tier B and the release check read is still the one P built (${check})"
    else
        record_fail "${label}: the archive no longer verifies: ${check}"
    fi
}

# B: Tier B, both scenarios and the locale control, once.
control_B() {
    begin_control "B" "items 2 and 3, Tier B: the installed archive, both scenarios and the launched-process locale control, once"
    local log="${LOGS}/B-tier-b.log"
    RUN_LABEL="B"
    MVN_EXTRA=("-Dcometgui.e2e=true")
    clean_run "Tier B" "${APP}" "${T_B_CANON},${T_B_NOXML},${T_B_LOCALE}" "${log}"
    compare_tree "${log}" "${RUN_MODULES}"
    require_every_test_ran "item 2, the canonical scenario in Tier B" "${T_B_CANON}"
    require_every_test_ran "item 3, the no-XML scenario in Tier B" "${T_B_NOXML}"
    assert_testcase "item 3, Tier B: \"Comet was not rerun\" (step 5, from disk)" passed "${T_B_NOXML}" cometNotRerun
    require_every_test_ran "the launched-process locale control" "${T_B_LOCALE}"
    assert_testcase "  ... the harness's assertion fails loudly on the packaged process started with no locale" passed \
        "${T_B_LOCALE}" theLocaleAssertionFailsLoudly
    archive_unchanged "after Tier B"
    end_control
}

# The ordinary failure-path tests, from the sandbox's own map: Class#m1+m2 per
# class -- except a class whose tests run in a declared order
# (@TestMethodOrder), whose later tests build on the earlier ones and are
# therefore selected whole, as scripts/build.sh runs them.
ordinary_selectors() {
    python3 - "${SANDBOX}" <<'PYTHON'
import glob
import sys
import tomllib
from collections import OrderedDict

root = sys.argv[1]
with open(root + "/scripts/e2e/failure-paths.toml", "rb") as handle:
    scenarios = tomllib.load(handle)["scenario"]
methods = OrderedDict()
for scenario in scenarios:
    for test in scenario.get("test", []):
        if test.get("run") != "ordinary":
            continue
        qualified, method = test["id"].split("#", 1)
        simple = qualified.rsplit(".", 1)[1]
        methods.setdefault(simple, [])
        if method not in methods[simple]:
            methods[simple].append(method)
selectors = []
for cls, names in methods.items():
    outer = cls.split("$")[0]
    sources = glob.glob(root + "/cometgui-*/src/test/java/**/%s.java" % outer, recursive=True)
    ordered = len(sources) == 1 and "@TestMethodOrder" in open(sources[0], encoding="utf-8").read()
    if ordered and "$" not in cls:
        selectors.append(cls)
    else:
        selectors.append("%s#%s" % (cls, "+".join(names)))
print(",".join(selectors))
PYTHON
}

distinct_mapped_ids() {
    python3 - "${SANDBOX}/scripts/e2e/failure-paths.toml" <<'PYTHON'
import sys
import tomllib

with open(sys.argv[1], "rb") as handle:
    scenarios = tomllib.load(handle)["scenario"]
print(len({t["id"] for s in scenarios for t in s.get("test", [])}))
PYTHON
}

# 4: the failure-path suite, then the map's checker over exactly these reports.
control_4() {
    begin_control "4" "item 4: every mapped failure-path test run fresh here, then check-failure-paths.sh over exactly those reports"
    local selectors batch runs=0
    selectors="$(ordinary_selectors)"
    [ -n "${selectors}" ] || harness_error "(4) the map names no ordinary test."
    local -a batches=()
    mapfile -t batches < <(batches_of "${selectors}")
    MVN_EXTRA=()
    for batch in "${batches[@]}"; do
        runs=$((runs + 1))
        RUN_LABEL="F${runs}"
        clean_run "the failure-path suite (${runs})" "${APP}" "${batch}" "${LOGS}/4-failure-paths-${runs}.log"
        compare_tree "${LOGS}/4-failure-paths-${runs}.log" "${RUN_MODULES}"
        if [ "${CLEAN_FAILED:-x}" = "0" ]; then
            record_pass "the failure-path suite, run ${runs}: $(ran_count "${CURRENT_LOG}") test(s) ran, 0 failed ($(printf '%s\n' "${batch}" | tr ',' '\n' | grep -c .) classes)"
        else
            record_fail "the failure-path suite, run ${runs}: ${CLEAN_FAILED:-no} failed testcase(s) (log: $(rel "${CURRENT_LOG}"))"
            grep -m5 '^FAILED ' "${CURRENT_LOG}" | cut -c1-200 | sed 's/^/         /' || true
        fi
    done
    local -a dirs=()
    local dir
    for dir in "${REPORTS}"/A "${REPORTS}"/B "${REPORTS}"/F*; do
        [ -d "${dir}" ] && dirs+=("${dir}")
    done
    [ -d "${REPORTS}/A" ] && [ -d "${REPORTS}/B" ] \
        || harness_error "(4) the Tier A and Tier B reports of this run are not both kept, so the e2e ids cannot be graded from this run."
    local log="${LOGS}/4-check-failure-paths.log" rc=0 expected graded
    ( cd -- "${SANDBOX}" && bash scripts/e2e/check-failure-paths.sh --reports "${dirs[@]}" --scope all ) >"${log}" 2>&1 || rc=$?
    expected="$(distinct_mapped_ids)"
    graded="$(sed -n 's/^(c) scope all: \([0-9][0-9]*\) test id(s) graded.*/\1/p' "${log}" | tail -1)"
    if [ "${rc}" -eq 0 ] && grep -qx 'check-failure-paths: PASSED' "${log}" \
        && ! grep -q 'FAIL:' "${log}" && [ "${graded:-0}" -eq "${expected}" ]; then
        record_pass "check-failure-paths.sh --reports <this run's ${#dirs[@]} report sets> --scope all: PASSED, every one of the ${expected} mapped test ids graded -- $(grep '^(a)\|^(c)' "${log}" | tr '\n' ' ' | cut -c1-200)"
    else
        record_fail "check-failure-paths.sh over this run's reports: exit ${rc}, ${graded:-no} of ${expected} ids graded (log: $(rel "${log}")): $(grep -m4 'FAIL:' "${log}" | tr '\n' ' ' | cut -c1-300)"
    fi
    log="${LOGS}/4-check-failure-paths-self-test.log"
    rc=0
    ( cd -- "${SANDBOX}" && bash scripts/e2e/check-failure-paths.sh --self-test ) >"${log}" 2>&1 || rc=$?
    if [ "${rc}" -eq 0 ] && grep -qx 'check-failure-paths --self-test: PASSED' "${log}"; then
        record_pass "check-failure-paths.sh --self-test: PASSED -- $(grep -m1 '^self-test: ' "${log}")"
    else
        record_fail "check-failure-paths.sh --self-test: exit ${rc} (log: $(rel "${log}"))"
    fi
    end_control
}

# 6: no fixed sleep.
control_6() {
    begin_control "6" "item 6: check-no-fixed-sleep.sh on the sandbox's compiled tree, and its --self-test"
    local log="${LOGS}/6-no-fixed-sleep.log" rc=0
    ( cd -- "${SANDBOX}" && bash scripts/e2e/check-no-fixed-sleep.sh ) >"${log}" 2>&1 || rc=$?
    if [ "${rc}" -eq 0 ] && grep -q '^check-no-fixed-sleep: PASSED' "${log}"; then
        record_pass "$(grep -m1 '^check-no-fixed-sleep: PASSED' "${log}" | cut -c1-220)"
    else
        record_fail "check-no-fixed-sleep.sh: exit ${rc} (log: $(rel "${log}")): $(tail -3 "${log}" | tr '\n' ' ' | cut -c1-240)"
    fi
    log="${LOGS}/6-no-fixed-sleep-self-test.log"
    rc=0
    ( cd -- "${SANDBOX}" && bash scripts/e2e/check-no-fixed-sleep.sh --self-test ) >"${log}" 2>&1 || rc=$?
    if [ "${rc}" -eq 0 ] && grep -q '^check-no-fixed-sleep --self-test: PASSED' "${log}"; then
        record_pass "$(grep -m1 '^check-no-fixed-sleep --self-test: PASSED' "${log}" | cut -c1-220)"
    else
        record_fail "check-no-fixed-sleep.sh --self-test: exit ${rc} (log: $(rel "${log}"))"
    fi
    end_control
}

# 7: no bridge in the release artefact.
control_7() {
    begin_control "7" "item 7: release-no-test-bridge.sh on the clean archive, and its --self-test (bridge-included builds)"
    [ -n "${ARCHIVE}" ] || harness_error "(7) no archive was built by control P."
    local log="${LOGS}/7-no-test-bridge.log" rc=0
    ( cd -- "${SANDBOX}" && bash scripts/ci/release-no-test-bridge.sh "${ARCHIVE}" ) >"${log}" 2>&1 || rc=$?
    if [ "${rc}" -eq 0 ] && grep -q '^release-no-test-bridge: PASSED' "${log}" \
        && grep -qE '^  [1-9][0-9]* files; [1-9][0-9]* JAR\(s\).* [1-9][0-9]* class entries.* 1 launcher configuration\(s\).* [1-9][0-9]* runtime modules holding [1-9][0-9]* classes' "${log}"; then
        record_pass "the clean archive: $(grep -m1 -E '^  [1-9][0-9]* files;' "${log}" | sed 's/^ *//') -- PASSED"
    else
        record_fail "release-no-test-bridge.sh on the clean archive: exit ${rc} (log: $(rel "${log}")): $(tail -3 "${log}" | tr '\n' ' ' | cut -c1-240)"
    fi
    log="${LOGS}/7-no-test-bridge-self-test.log"
    rc=0
    ( cd -- "${SANDBOX}" && python3 "${REAPER}" bash scripts/ci/release-no-test-bridge.sh --self-test ) >"${log}" 2>&1 || rc=$?
    if [ "${rc}" -eq 0 ] && grep -q 'release-no-test-bridge.sh --self-test: PASSED' "${log}" \
        && ! grep -q '^  MISSED' "${log}"; then
        record_pass "$(grep -m1 'release-no-test-bridge.sh --self-test: PASSED' "${log}" | cut -c1-240)"
    else
        record_fail "release-no-test-bridge.sh --self-test: exit ${rc} (log: $(rel "${log}"))"
    fi
    compare_tree "${log}" "$(reactor_of "${APP}")"
    archive_unchanged "after the release check"
    end_control
}

# ---- gate item 3's injection -------------------------------------------------

control_3i() {
    begin_control "3i" "item 3 [recorded, unit 4]: the compatible rerun touching the source run's Comet outputs"
    MVN_EXTRA=("-Dcometgui.e2e=true")
    inject_and_run "the rerun touches what it copied" "${PERCOLATOR_RERUN}" "${T_A_NOXML}" regex \
        "message: .*the first run's Comet trees, unchanged ==> expected: <\\[\\]> but was: <\\[.*mtime" \
        '        Files.copy(from, to);' \
        '        Files.copy(from, to);
        Files.setLastModifiedTime(
                from,
                java.nio.file.attribute.FileTime.fromMillis(
                        Files.getLastModifiedTime(from).toMillis() + 2000));'
    assert_testcase "the disk says Comet's outputs changed" failed "${T_A_NOXML}" cometNotRerun
    assert_testcase "the compatible rerun itself (the step before) stays green" passed "${T_A_NOXML}" compatibleRerun
    restore_pristine "${PERCOLATOR_RERUN}"
    end_control
}

# ---- gate item 5's three injections -----------------------------------------

control_5q() {
    begin_control "5q" "item 5 [recorded, unit 7]: the q-value comparison inverted (<= made <)"
    MVN_EXTRA=("-Dcometgui.e2e=true")
    inject_and_run "an exclusive q-value cutoff" "${QVALUE_FILTER}" "${T_A_CANON}" regex \
        'message: .*PSMs at q <= [0-9.]+ ==> expected: <\[' \
        '        return value <= comparable ? Visibility.PASSES : Visibility.FAILS;' \
        '        return value < comparable ? Visibility.PASSES : Visibility.FAILS;'
    assert_testcase "the scenario's boundary step" failed "${T_A_CANON}" inclusiveCutoff
    assert_testcase "step 21 at the specification's 0.01, which no q-value equals, stays green" passed \
        "${T_A_CANON}" countsAtOnePercent
    restore_pristine "${QVALUE_FILTER}"
    end_control
}

control_5p() {
    begin_control "5p" "item 5 [recorded, unit 3]: a provenance entry dropped (the searched FASTA left out of provenance.json)"
    MVN_EXTRA=("-Dcometgui.e2e=true")
    inject_and_run "the database not in the record" "${MANIFEST_WRITER}" "${T_A_CANON}" regex \
        'message: .*UP000005640_9606\.fasta: not declared as an input' \
        '        for (FileRecord file : manifest.files()) {
            writeFile(json, file);
        }' \
        '        for (FileRecord file : manifest.files()) {
            if (!file.path().toString().endsWith(".fasta")) {
                writeFile(json, file);
            }
        }'
    assert_testcase "step 28, provenance against the disk" failed "${T_A_CANON}" provenance
    assert_testcase "step 27, the upload before it, stays green" passed "${T_A_CANON}" upload
    restore_pristine "${MANIFEST_WRITER}"
    end_control
}

control_5b() {
    begin_control "5b" "item 5 [recorded, unit 3]: a downstream stage bypassed -- the upload's final submit skipped, the screen still says submitted"
    MVN_EXTRA=("-Dcometgui.e2e=true")
    inject_and_run "the final submit never sent" "${UPLOAD_CLIENT}" "${T_A_CANON}" regex \
        'message: .*the final submit queued the import ==> expected: <true> but was: <false>' \
        '            SubmitImportWire.Answer submitted =
                    upload.call(
                            LimelightUploadStep.FINAL_SUBMIT,
                            SubmitImportWire.finalSubmit(
                                    project, uploadKey, request.fileName(), secret));' \
        '            SubmitImportWire.Answer submitted = sent;'
    assert_testcase "step 27, the upload to the D-007 fake" failed "${T_A_CANON}" upload
    assert_testcase "step 26, the conversion before it, stays green" passed "${T_A_CANON}" limelightConversion
    restore_pristine "${UPLOAD_CLIENT}"
    end_control
}

# ---------------------------------------------------------------- control H --

expect_harness_error() {
    local label="$1" why="$2"
    shift 2
    local out rc=0
    out="$( ( "$@" ) 2>&1 )" || rc=$?
    if [ "${rc}" -eq 4 ] && grep -q 'HARNESS FAILURE' <<<"${out}" \
        && grep -qF -- "${why}" <<<"${out}"; then
        record_pass "${label}"
        printf '         %s\n' "$(printf '%s\n' "${out}" | grep 'HARNESS FAILURE' | head -1 | cut -c1-220)"
    else
        record_fail "${label}: the harness did NOT refuse (exit ${rc}); it must stop with a HARNESS FAILURE naming '${why}'. Output: $(printf '%s' "${out}" | tail -3 | tr '\n' ' ' | cut -c1-300)"
    fi
}

expect_recorded_failure() {
    local label="$1" must="$2"
    shift 2
    local before="${FAILED}"
    DELIBERATE=1
    "$@"
    DELIBERATE=0
    if [ "${FAILED}" -eq $(( before + 1 )) ] && grep -qF -- "${must}" <<<"${FAILURES[-1]}"; then
        FAILED="${before}"
        unset 'FAILURES[-1]'
        record_pass "${label}"
    else
        record_fail "${label}: HARNESS FAILURE -- it did not record exactly one failure saying '${must}'. Every other control in this script is unreliable."
    fi
}

readonly H_FILE="${QVALUE_FILTER}"
readonly H_ANCHOR='        return value <= comparable ? Visibility.PASSES : Visibility.FAILS;'

h_no_anchor() {
    replace_once "H1" "${H_FILE}" 'this text is in no source file' 'nor is this'
}

# A comment on the anchor's own line moves no line number, so javac writes the
# same class: the source changed, the bytecode cannot have.  A real Maven run.
h_source_not_bytecode() {
    replace_once "H2" "${H_FILE}" "${H_ANCHOR}" \
        "${H_ANCHOR} // H2: the source changed and the bytecode cannot"
    assert_modified "H2" "${H_FILE}"
    MVN_EXTRA=()
    RUN_LABEL=""
    dirty_run "H2" "${H_FILE}" "cometgui-results" "${T_H_CHEAP}" "${LOGS}/H2-comment-only.log"
}

# H5: a REAL run of an end-to-end class without the opt-in: surefire reports
# every test, skipped, and Maven says BUILD SUCCESS.  Zero tests ran.
h_zero_ran() {
    local log="${LOGS}/H5-zero-ran.log" rc=0
    MVN_EXTRA=()
    RUN_LABEL=""
    run_mvn "${log}" "${APP}" "${T_CANARY}" || rc=$?
    [ "${rc}" -eq 0 ] || harness_error "H5's Maven run itself failed (exit ${rc}); it must build and skip what it names."
    grep -q 'BUILD SUCCESS' "${log}" || harness_error "H5's Maven run did not say BUILD SUCCESS, so it is not the shape it exists to show."
    require_counts "H5" "${log}"
}

# H6: the same, by a log whose reports count zero.
h_zero_count() {
    local log="${LOGS}/H6-zero-count.log"
    printf '[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0\n\n=== 0 TESTCASE(S) REPORTED ===\n=== 0 TESTCASE(S) SKIPPED ===\n=== 0 TESTCASE(S) RAN ===\n=== 0 STALE REPORT(S) ===\n' >"${log}"
    append_console_counts "${log}"
    require_counts "H6" "${log}"
}

control_H() {
    begin_control "H" "the harness itself: an injection that did not land, or a run that ran nothing, is never a pass"
    save_pristine "${H_FILE}"

    expect_harness_error "H1 an injection whose anchor matches nothing is a HARNESS FAILURE" \
        "the injection anchor is gone" h_no_anchor
    cmp -s "${SANDBOX}/${H_FILE}" "${PRISTINE}/${H_FILE}" \
        || harness_error "control H's refused injection changed ${H_FILE} after all."

    expect_harness_error "H2 an injection that changed the source but not the bytecode is a HARNESS FAILURE" \
        "BYTE-IDENTICAL to the clean baseline after the dirty run" h_source_not_bytecode
    restore_pristine "${H_FILE}"

    # H2's run tested the clean code and passed: graded as a red, the grader
    # must record a harness failure rather than a control that bit.
    local log="${LOGS}/H2-comment-only.log" failed
    [ -s "${log}" ] || harness_error "H2 left no log, so H3 has nothing to grade."
    failed="$(red_count "${log}")"
    [ "${failed}" = "0" ] \
        || harness_error "H2's run of the clean code did not pass (${failed:-unknown} failed); H3 needs a green run to grade."
    [ "$(ran_count "${log}")" -ge 1 ] || harness_error "H2's run ran no test; H3 needs a green run that ran something."
    expect_recorded_failure "H3 a green run graded as a red is a HARNESS FAILURE, not a pass" \
        "HARNESS FAILURE -- the check PASSED with the defect present" \
        grade_red regex "H3 (deliberately graded with no defect injected)" "${failed}" "${log}" \
        'PSMs at q <= '

    log="${LOGS}/H4-wrong-reason.log"
    printf '=== FAILED TESTCASES FROM SUREFIRE XML ===\nFAILED org.cometgui.app.e2e.tiera.TierACanonicalScenarioTest#inclusiveCutoff [failure]\n  message: the merged PIN ==> expected: <6657> but was: <6656>\n=== 1 FAILED TESTCASE(S) ===\n' >"${log}"
    expect_recorded_failure "H4 a red WITHOUT the expected failure's own words is a HARNESS FAILURE, not a pass" \
        "HARNESS FAILURE -- red, but without the expected failure's own words" \
        grade_red regex "H4 (deliberately graded against the wrong reason)" 1 "${log}" \
        'PSMs at q <= [0-9.]+ ==> expected: <\['

    expect_harness_error "H5 a real run of an end-to-end class without -Dcometgui.e2e=true -- every test reported SKIPPED, Maven BUILD SUCCESS -- is a HARNESS FAILURE: zero tests ran" \
        "A run in which zero tests ran cannot prove or disprove a gate" h_zero_ran
    expect_harness_error "H6 a run whose reports and Maven's summaries count zero tests is a HARNESS FAILURE" \
        "A run in which zero tests ran cannot prove or disprove a gate" h_zero_count
    end_control
}

# ---------------------------------------------------------------- control C --

control_C() {
    begin_control "C" "every restoration reached the bytecode; the archive and the shared repository unchanged"
    local log="${LOGS}/C-recompile.log" rc=0
    MVN_GOAL="test-compile"
    MVN_EXTRA=()
    RUN_LABEL=""
    printf '   mvn -o -pl %s -am test-compile\n' "${APP}"
    run_mvn "${log}" "${APP}" "" || rc=$?
    MVN_GOAL="test"
    [ "${rc}" -eq 0 ] || harness_error "(C) the restored sandbox does not compile: see $(rel "${log}")."
    compare_tree "${log}" "${BASELINE_MODULES}"
    record_pass "the restored sandbox, recompiled, is byte-identical to the clean baseline in every module of ${APP}'s reactor"
    if [ -s "${PRISTINE_SUMS}" ]; then
        local check
        if check="$(cd -- "${SANDBOX}" && sort -u -k2 "${PRISTINE_SUMS}" | sha256sum -c - 2>&1)"; then
            record_pass "every damaged source is its pristine self again -- sha256sum -c: $(printf '%s' "${check}" | tr '\n' ' ' | cut -c1-260)"
        else
            record_fail "a damaged source was not restored: ${check}"
        fi
    fi
    archive_unchanged "at the end"
    if [ "$(cometgui_repo_digest)" = "${COMETGUI_REPO_DIGEST}" ]; then
        record_pass "the shared repository's org/cometgui jars are unchanged: nothing was installed"
    else
        harness_error "the shared repository's org/cometgui jars changed during this run; this script must never write them."
    fi
    end_control
}

# ---------------------------------------------------------------- preflight --

preflight() {
    local -a missing=()
    local needed url code
    command -v git >/dev/null || missing+=("git on PATH (the sandbox is a git archive)")
    command -v python3 >/dev/null || missing+=("python3 on PATH")
    python3 -c 'import tomllib' 2>/dev/null || missing+=("python3 with tomllib (3.11 or newer): the failure-path map is TOML")
    [ -f "${ROOT}/tools/env.sh" ] || missing+=("tools/env.sh (run bash scripts/build.sh first)")
    [ -d "${M2REPO}/org" ] || missing+=("$(rel "${M2REPO}") populated (run bash scripts/build.sh first)")
    for needed in "${SCRATCH_NEEDED[@]}"; do
        [ -e "${ROOT}/${needed}" ] || missing+=("${needed} -- the D-006 LOCAL fixture or a tool artefact the scenarios read (gitignored; see docs/developer/testing.rst)")
    done
    for needed in "${TOOLS_NEEDED[@]}"; do
        [ -e "${ROOT}/${needed}" ] || missing+=("${needed} -- the project-local X11 closure and font stack (bash scripts/feasibility/javafx-smoke.sh, then bash scripts/feasibility/javafx-headed-xvfb.sh)")
    done
    [ -f "${ROOT}/${SHIM}" ] || missing+=("${SHIM} (Xvfb's xkbcomp path shim source)")
    command -v gcc >/dev/null || missing+=("the host's gcc on PATH (the test support compiles the xkbcomp shim with it)")
    [ -x /usr/bin/timeout ] || missing+=("/usr/bin/timeout (Xvfb runs under it in the test support)")
    command -v curl >/dev/null || missing+=("curl on PATH (the network preflight)")
    if [ "$(id -u)" -eq 0 ]; then
        missing+=("a non-root user: the Tier B install is made read-only, and permission bits refuse root nothing")
    fi
    if command -v curl >/dev/null; then
        for url in "${NETWORK_NEEDED[@]}"; do
            code="$(curl -sS -o /dev/null -I -L --max-time 30 -w '%{http_code}' -- "${url}" 2>/dev/null || true)"
            [ "${code}" = "200" ] \
                || missing+=("network: ${url} answered '${code:-nothing}', not 200 -- the scenarios download the tools from the pinned GitHub release URLs")
        done
    fi
    if [ "${#missing[@]}" -gt 0 ]; then
        printf '\n%s: the environment is not ready; missing:\n' "${SCRIPT_NAME}" >&2
        printf '  - %s\n' "${missing[@]}" >&2
        die "refusing to start: the controls that read these would fail for the wrong reason, and a skipped control is never a pass." 3
    fi
    # shellcheck disable=SC1091
    . "${ROOT}/tools/env.sh"
    command -v mvn >/dev/null || die "mvn is not on PATH after sourcing tools/env.sh." 3
    bash "${ROOT}/scripts/fetch-fontstack.sh" --verify >/dev/null \
        || die "the font stack is missing or damaged; run bash scripts/fetch-fontstack.sh first. Without it every GUI control fails for the wrong reason." 3
    printf '%s: preflight -- the fixture, the X11 closure, the toolchain and the four pinned artefacts on GitHub are all there\n' "${SCRIPT_NAME}"
}

# -------------------------------------------------------------------- main --

run_control() {
    case "$1" in
        L) control_L ;; P) control_P ;; B) control_B ;;
        4) control_4 ;; 6) control_6 ;; 7) control_7 ;;
        3i) control_3i ;; 5q) control_5q ;; 5p) control_5p ;; 5b) control_5b ;;
        H) control_H ;;
        *) die "no control '$1'. Controls: ${ALL_CONTROLS[*]}" 2 ;;
    esac
}

main() {
    local self_test_only=0 preflight_only=0 only=""
    while [ "$#" -gt 0 ]; do
        case "$1" in
            -h|--help) usage; exit 0 ;;
            --self-test) self_test_only=1; shift ;;
            --preflight) preflight_only=1; shift ;;
            --only)
                [ "$#" -ge 2 ] || die "--only needs control ids" 2
                only="${only}${only:+,}$2"
                shift 2
                ;;
            *) usage >&2; die "unknown option: $1" 2 ;;
        esac
    done

    cd -- "${ROOT}"
    local -a wanted=() selected=()
    if [ "${self_test_only}" -eq 1 ]; then
        wanted=(H)
    elif [ -n "${only}" ]; then
        IFS=',' read -r -a wanted <<<"${only}"
    else
        wanted=("${ALL_CONTROLS[@]}")
    fi
    local id known
    for id in "${wanted[@]}"; do
        known=0
        for known_id in "${ALL_CONTROLS[@]}"; do [ "${known_id}" = "${id}" ] && known=1; done
        [ "${known}" -eq 1 ] || die "no control '${id}'. Controls: ${ALL_CONTROLS[*]} (A always runs)" 2
    done
    # Prerequisites: 4 grades the reports of A and B, and B and 7 read P's
    # archive.  Run in ALL_CONTROLS order, once each.
    local -A want=()
    for id in "${wanted[@]}"; do want["${id}"]=1; done
    [ -n "${want[4]:-}" ] && want[B]=1
    [ -n "${want[B]:-}" ] && want[P]=1
    [ -n "${want[7]:-}" ] && want[P]=1
    for id in "${ALL_CONTROLS[@]}"; do
        [ -n "${want[${id}]:-}" ] && selected+=("${id}")
    done

    preflight
    [ "${preflight_only}" -eq 0 ] || exit 0

    # The harness's own locale, for every Maven run but control L's.
    export LC_ALL=C.UTF-8
    export LANG=C.UTF-8
    export PYTHONDONTWRITEBYTECODE=1
    # Every Maven run is started by a Python reaper.  Under a C or POSIX locale
    # Python "coerces" it (PEP 538) by setting LC_CTYPE=C.UTF-8 in its OWN
    # environment, which every child inherits -- so control L's run "with no
    # locale" would quietly have one, and pass.  Control L found exactly that.
    export PYTHONCOERCECLOCALE=0

    mkdir -p -- "${LOGS}"
    rm -rf -- "${REPORTS}"
    rm -f -- "${LOGS}"/*.log "${LOGS}"/*.failures "${LOGS}"/*.sha256 "${LOGS}"/*.txt "${PRISTINE_SUMS}"
    write_reaper

    printf '===============================================================================\n'
    printf ' %s -- every PHASE-14 gate item must be seen to hold, and item 5 to fail\n' "${SCRIPT_NAME}"
    printf '===============================================================================\n'
    printf '  repository   %s\n' "${ROOT}"
    printf '  logs         %s\n' "$(rel "${LOGS}")"
    printf '  controls     A %s C\n' "${selected[*]}"
    printf '  locale       LC_ALL=%s LANG=%s (exported by this script)\n' "${LC_ALL}" "${LANG}"

    local started
    started="$(date +%s)"
    begin_control "0" "the sandbox"
    build_sandbox
    end_control
    # P before A when it is wanted: build-app-image.sh's package goal compiles
    # the same classes, and A's compiled tree is then the baseline every later
    # run, P's archive aside, is compared with.
    [ -n "${want[P]:-}" ] && control_P
    control_A
    if [ "${FAILED}" -eq 0 ]; then
        for id in "${selected[@]}"; do
            [ "${id}" = "P" ] && continue
            run_control "${id}"
        done
        control_C
    fi
    [ -z "${COMETGUI_REPO_DIGEST}" ] || [ "$(cometgui_repo_digest)" = "${COMETGUI_REPO_DIGEST}" ] \
        || harness_error "the shared repository's org/cometgui jars changed during this run; this script must never write them."

    local total=$(( $(date +%s) - started ))
    printf '\n===============================================================================\n'
    printf ' per-control wall clock (the cost of this harness):\n'
    printf '   %s\n' "${TIMINGS[@]}"
    printf ' processes left running by a run and ended by this script: %d\n' "${LEFT_BEHIND}"
    printf ' SUMMARY: %d control(s) passed, %d failed, in %d seconds (%dm%02ds)\n' \
        "${PASSED}" "${FAILED}" "${total}" "$((total / 60))" "$((total % 60))"
    printf ' Logs: %s\n' "$(rel "${LOGS}")"
    printf '===============================================================================\n'
    if [ "${FAILED}" -ne 0 ]; then
        printf '\n'
        printf '  %s\n' "${FAILURES[@]}"
        die "${FAILED} end-to-end gate control(s) failed. A gate that cannot be seen to fail is not a gate." 1
    fi
    if [ "${self_test_only}" -eq 1 ]; then
        printf '\n  self-test OK -- the harness reports a missing anchor, an injection that\n'
        printf '  reached the source but not the bytecode, a green graded as red, a red\n'
        printf '  without its words, and a run in which zero tests ran as a HARNESS FAILURE\n'
        printf '  -- never as a pass.\n\n'
        return 0
    fi
    if [ -n "${only}" ]; then
        printf '\n  The selected controls bit (--only %s). This is NOT a full run.\n\n' "${only}"
        return 0
    fi
    printf '\n  PHASE-14 exit gate items 1 to 7 were proved here: both scenarios in Tier A\n'
    printf '  and in Tier B against the packaged archive built and installed by this run,\n'
    printf '  every @Test method counted as run from this run'"'"'s surefire XML, under a\n'
    printf '  UTF-8 locale this script exported and the class'"'"'s own assertion of it; the\n'
    printf '  failure-path map graded over this run'"'"'s fresh reports; and gate item 5'"'"'s\n'
    printf '  three defects -- an inverted q-value comparison, a dropped provenance entry\n'
    printf '  and a bypassed upload -- and item 3'"'"'s touched Comet outputs, each injected\n'
    printf '  into production code, proved in the bytecode and graded on the failing\n'
    printf '  assertion'"'"'s own words in the named step.\n'
    printf '  The harness reports an injection that reached the source but not the\n'
    printf '  bytecode, and a run in which zero tests ran, as a HARNESS FAILURE, not as a\n'
    printf '  pass.\n'
    printf '\n  Every gate rejected its defect and accepted the clean tree.\n\n'
}

main "$@"
