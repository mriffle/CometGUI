#!/usr/bin/env bash
#
# CometGUI -- prove the PHASE-05 tool-registry and installer gates can fail.
#
#   bash scripts/verify-install-gates.sh                 every control
#   bash scripts/verify-install-gates.sh --self-test     control H only
#   bash scripts/verify-install-gates.sh --only 2,G,H    named controls only
#
# A gate that has never been seen to fail has not been shown to work
# (CONTRIBUTING.rst, "Gate conventions").  Phase 05's exit gate is nine claims
# about downloading, verifying, extracting, installing, probing and offering
# tool binaries.  Each is checked by a test, and a green test says nothing about
# whether that test would notice the defect it exists to catch.  This script
# injects, one at a time, a defect each item exists to catch, requires the
# narrowest command that should catch it to exit non-zero WITH THE EXPECTED
# DIAGNOSTIC -- the failing assertion's own words, never a sentence every
# failure shares -- and requires the clean tree to pass again afterwards.
#
# It is the sibling of scripts/verify-provenance-gates.sh and follows its shape:
# a `git archive HEAD` sandbox, anchors that must match exactly once, a
# pristine copy per damaged file, graded failures, and a control H on itself.
#
# IT IS ASSEMBLED FROM A RECORD, NOT INVENTED.  Every injection marked
# [recorded] below was really made during phase 05, and the failure it produced
# is in handoffs/PHASE-05-worklog.rst at the unit named.  Two controls are
# marked [NEW at unit 12, not from the record]: exit gate items 6 and 7 had no
# recorded injection at all, so a test had never been seen to fail for either.
# They are held to exactly the same standard as the recorded ones and are
# labelled so that nobody mistakes them for history.
#
# WHAT IT COVERS (PHASE-05 exit gate items; see phases/PHASE-05-tool-registry.rst)
#
#   0   baseline: the undamaged sandbox passes every test selector the controls
#       use, in one run, and the clean bytecode of every module is digested
#   1   [recorded, unit 1; the tenth shape] DeclaredCapability's blank-note
#       rule given an added conjunct, `&& evidence != UNVERIFIED`.  R-TOOL-08:
#       no numbered gate item -- it is the rule that keeps every macOS and
#       Windows capability row honest, invisible to coverage and to mutation
#   2   item 1 [recorded, unit 8]: the DownloadCancelledException translation
#       removed, so a mid-transfer cancel reports FAILED.  The step-boundary
#       test in the same class is required to STAY GREEN: that is the
#       measurement that the boundary axis alone could not see this
#   3   item 1 [recorded, unit 3]: progress on a RESUMED transfer reported from
#       the resume point rather than the absolute position
#   4   item 1 [recorded, unit 9]: the uiThread.execute hop removed, so a report
#       reaches a live scene on a cometgui-install-N thread.  JavaFX throws
#       nothing for this; the test observes the THREAD
#   5   item 1 [recorded, unit 10]: the real Tool Manager's Install control
#       disconnected with install.setDisable(true), caught through the real
#       window in cometgui-app -- immediately, not after the 600 s a no-op
#       handler costs (ShownToolManager.TERMINAL_TIMEOUT_SECONDS)
#   6   item 2 [recorded, unit 10]: the installer's step-2 re-hash disabled
#       with `if (false)`, so a source that lies about its bytes gets through
#   7   item 2 [unit 10's carried-forward instruction]: the transfer step's
#       verdict ignored, so a corrupted artefact leaves step 1 and is caught
#       only by step 2 -- graded by the phase-sequence assertion
#       [DOWNLOADING, FAILED] that unit 10 added, and by nothing else.  The
#       assertion that no process was launched is required to STAY GREEN
#   8   item 3 [recorded, unit 4]: hasDriveLetter returning false
#   9   item 3 [recorded, unit 4]: the DOCTYPE guard deleted from the xar
#       table-of-contents parser, the strongest of the five XXE deletions
#   10  item 4 [recorded, unit 5]: the cache trusting the marker's own
#       payloadEntryCount instead of counting the directory
#   11  item 5 [recorded, unit 8]: ManagedToolManager.refusalFor answering
#       Optional.empty(), so a build that no longer starts is offered
#   12  item 5 [recorded, unit 6]: an unreachable binary's refusal naming no
#       alternatives -- R-PLAT-03's "available alternatives" dropped
#   13  item 5 [recorded, unit 6]: ManifestAlternatives keyed on the VERSION
#       rather than the row, so a sibling build of one release goes unnamed
#   14  item 6 [NEW at unit 12, not from the record]: a companion gate that
#       does not shut when a companion file is missing, so a Comet install
#       without the Thermo DLLs advertises THERMO_RAW_WINDOWS
#   15  item 7 [NEW at unit 12, not from the record]: the local-Percolator
#       floor moved from 3.05 to 3.04, so a 3.04 binary is registered
#   16  item 8 [recorded, unit 2]: the offer order's second key inverted, so a
#       translated row is offered before a native one and a JAR is described
#       as running under Rosetta 2
#   17  R-TOOL-01 [recorded, unit 8]: the download size quoting the artefact
#       alone rather than artefact plus companions.  No numbered item: it is
#       the figure the Tool Manager offers a scientist before a transfer
#   18  R-PERC-02 [recorded, unit 7]: Locale.ROOT removed from the synthetic
#       PIN, so the functional probe's fixture follows the host locale.
#       Supports item 1's "probes each successfully"
#   19  R-PLAT-04 [NEW at unit 14, not from the record]: PlatformFixups's
#       macOS branch with its re-check removed, so a `/usr/bin/xattr -d` that
#       exits 0 and changes nothing is believed and the file reported cleared --
#       the silent no-op macos-gatekeeper run 36918810975 found, in the one
#       shape a process can take it.  Graded on Linux through the scripted
#       ProcessRunner production calls; NOT evidence of what xattr does on a
#       Mac, which only the macos-gatekeeper workflow can show
#   20  R-TOOL-06 [NEW at COMET-2026-03 unit 2, not from the record]: the
#       identity stage made VERSION-BLIND -- StagedToolProbe's comparison of
#       the banner's version with the record's replaced by `false` -- so the
#       real Comet 2026.03.0 binary installs under a record pinning 2026.02.2.
#       The SHA-256 matches (they are the pinned bytes), so the banner is the
#       only thing that can tell the releases apart
#   21  D-010 [NEW at COMET-2026-03 unit 2, not from the record]: the offer
#       order's FIRST key inverted, so select() offers the oldest release
#       first and the default Comet stops being the newest the manifest names
#       -- graded host by host against the shipped manifest
#   22  D-010 [NEW at COMET-2026-03 unit 2, not from the record]: the Tool
#       Manager's own release order (ManagedToolManager.releasesOf) inverted,
#       so the port offers 2026.02.2 as the first -- the default -- Comet
#   G   R-DOC-06 / R-PERC-12 [recorded, unit 11]: scripts/toolmatrix.py, the
#       documentation-table generator.  The clean manifest renders and the
#       output names the manifest's own digest; the five recorded manifest
#       injections -- a missing sha256, an unknown platform, a plain http URL,
#       an unknown evidence value, and a claim word in a companion id -- are
#       each rejected WITH THAT INJECTION'S OWN DIAGNOSTIC, never merely with
#       the outer ExtensionError sentence, which is identical for every
#       rejection; one of them also through the real Sphinx builder-inited
#       hook (scripts/ci/docs-build.sh in the sandbox); and unit 11's own
#       injection -- generate() returning early when yesterday's fragments
#       exist -- fails the documentation build
#   M   item 9: NOT MET, and DELEGATED, never claimed.  No macOS binary has
#       ever been executed in this project.  This control requires
#       scripts/ci/macos-gatekeeper-verify.sh to still carry the words that
#       say so, and its own --self-test to pass.  It is not coverage of item 9
#   H   the harness itself: an injection whose replacement equals its anchor,
#       an anchor that matches nothing, a manifest edit that changes nothing,
#       an injection that reaches the source but not the bytecode, a green
#       run graded as a red, and a red without the expected diagnostic must
#       each be reported as a HARNESS ERROR or FAILURE -- never as a pass
#
# WHAT IT DOES NOT COVER, said plainly rather than left to be discovered:
#
#   * Item 9 is not met; see control M.
#   * Control 19 grades R-PLAT-04's macOS branch against a scripted
#     /usr/bin/xattr.  Whether the real xattr on a real Mac lists and deletes
#     com.apple.quarantine the way the script stands in for it is graded only
#     by scripts/ci/macos-gatekeeper-verify.sh's ATTRIBUTE VERDICT, on a
#     macOS runner, and by nothing here.
#   * Item 3 names four attacks.  Two have recorded injections (absolute path
#     via the drive letter, and the xar XXE guard).  Traversal, symlink and the
#     decompression bomb each have their own tests and NO recorded injection
#     in the work log, so they are residue here, not coverage.
#   * Recorded injections deliberately not encoded, each landing on an
#     assertion a control above already grades: unit 2's version-keyed and
#     keep-last one-row-per-download rules (both fail
#     aPlatformIndependentDownloadIsOfferedOnce, which control 16 asserts);
#     the other four of unit 4's five XXE deletions (same test class as 9);
#     unit 1's InstallProgress negative-count injection (a domain value check
#     with no gate item).  Unit 8's survived `offerFor` conjunct is not a
#     control because unit 8's orchestrator could not show it is a defect.
#   * UNIT 10'S ArtefactVerifier NEUTERING IS NOT A CONTROL, BY INSTRUCTION
#     AND BY MEASUREMENT.  Measured at unit 12 in this sandbox: with
#     `actual.sha256().equals(expected.sha256())` replaced by `true` (and the
#     compiled ArtefactVerifier.class seen to change), ToolManagerInstallUiTest
#     #aCorruptedArtefactIsRejectedAndNothingIsExecuted -- the test carrying
#     the [DOWNLOADING, FAILED] assertion -- ManagedToolManagerInstallTest
#     #aCorruptedArtefactIsRejectedAndNothingIsExecuted and all 28 of
#     CacheEdgeCasesTest PASS.  The verdict becomes MATCHED and
#     VerificationResult's constructor independently re-states the rule, so
#     the install still fails inside step 1 and the phases are still
#     [DOWNLOADING, FAILED].  So the phase-sequence assertion does not grade
#     that injection either; it grades a different one.  Control 7 removes
#     the ONE thing between a REJECTED verdict and step 2 --
#     VerifiedDownloader's acceptance test -- and that is a defect only the
#     phase sequence can see: nothing is executed either way, and of the
#     test's nine assertions exactly one fails.
#
# WHERE IT WORKS.  Never in the working tree.  It extracts `git archive HEAD`
# into _build/install-gate-sandbox and damages that.  `git archive HEAD` is the
# COMMITTED tree: uncommitted changes under cometgui-*/src, manifests/ or
# scripts/ are reported loudly, because the run then proves HEAD's gates and
# not the tree's.  tools/, .venv/ and scratch/ are gitignored and absent from
# the archive, so they are symlinked: tools/ for the JDK, Maven and the font
# stack the headless JavaFX tests need; .venv/ for Sphinx; scratch/ for the
# real upstream artefact mirror this phase's install and extraction suites
# read -- and FAIL, rather than skip, without.  Nothing here writes to them.
#
# EVERY INJECTION IS PROVED TO HAVE LANDED, IN THE SOURCE AND IN THE BYTECODE.
#   * the anchor must match EXACTLY ONCE, or the run stops as a harness error;
#   * the sandbox file must differ from its pristine copy;
#   * after the dirty run, the compiled class set of the damaged file
#     (Foo.class and every Foo$*.class) must DIFFER from the clean baseline --
#     an edit that reached the source but not the bytecode is the eighth shape
#     in the work log, and a run graded on unchanged bytecode proves nothing;
#   * and every OTHER class in every module that run compiled must be
#     byte-identical to the baseline, so the red is attributable to this one
#     injection and to nothing a previous control left behind.
#
# AND EVERY RESTORATION IS PROVED IN THE BYTECODE, NOT ONLY IN THE SOURCE.
# Unit 9 found a red that was not a result: `cp` keeps the snapshot's mtime,
# Maven's incremental compiler kept the injected class, and a source that was
# byte-identical to the snapshot ran injected bytecode.  So a restored file is
# copied back, compared, and TOUCHED; and the bytecode is checked as below.
#
# WHY THE CLEAN RE-RUN IS BATCHED, AND WHY THAT IS NOT A WEAKENING.  The
# sibling harness re-runs the narrow check after every control.  Here each
# module's bytecode is digested once, after the baseline run passed every
# selector; each dirty run then proves every class but the injected one is
# still byte-identical to that baseline (so the previous control's restoration
# is proved in the bytecode by the very next run in that module); and ONE
# final clean run, over the same closed module set and the same selectors as
# the baseline, must pass with every named class executed AND leave every
# module's class tree byte-identical to the baseline.  Identical bytecode
# under identical tests is the baseline's own green, proved rather than
# assumed, at the cost of one Maven invocation instead of eighteen.
#
# EACH CONTROL BUILDS ONE CLOSED MODULE SET.  Every Maven command is
# `mvn -o -pl <module> -am test -Dtest=<Class or Class#method>`.  `-am` is not
# optional -- a single-module run does not rebuild a sibling and surfaces as
# NoClassDefFoundError in code nobody touched -- and `-am` alone is a closed
# set; `-amd` is not (handoffs/SESSION-08-main-orchestrator.rst).  Never the
# reactor, never scripts/build.sh, never PIT.  Controls 5 and 7 run in
# cometgui-app because gate item 1 is literally "driven through the Tool
# Manager UI", and that window is assembled there.
#
# A CLASS SELECTOR IN A LIST WITH A Class#method SELECTOR DROPS @Nested TESTS,
# SILENTLY.  Measured while writing this script (surefire 3.5.6): alone,
# `-Dtest=SyntheticPinTest` runs 37 tests including the seven locale cases in
# its @Nested class; in a list beside any Class#method entry it runs 16 and
# none of them, and HttpDownloaderTest, all of whose tests are @Nested, runs
# nothing.  The baseline and the final clean run here are such lists.  So
# every run is checked against surefire's XML: a bare class selector is
# refused for a class with a @Nested class, and every method a selector names
# must appear in the report -- or it is a harness error.
#
# WHAT IT SWITCHES OFF, AND WHY THAT IS NOT A WEAKENING.  Every sandbox Maven
# run passes -Dspotless.check.skip -Dcheckstyle.skip -Dspotbugs.skip
# -Djacoco.skip.  Those are Phase 01's gates and have their own harnesses, and
# an injection that leaves an unused import or a long line would otherwise be
# rejected by the formatter first -- a red for the wrong reason, which this
# phase's record carries three times (Checkstyle's line length once, an
# orphaned import twice).  Nothing this script tests is skipped, no test is
# excluded, and every run is checked to have EXECUTED every class it named.
#
# WHAT IT NEEDS.  A built tree: tools/, .venv/, a populated _build/m2repo, and
# the gitignored artefact mirror at scratch/phase05/artefacts.  It runs Maven
# offline, needs no network, and writes only under _build/.
#
# WHAT IT COSTS.  Measured and printed per control; the total is on the
# SUMMARY line.  Every dirty run names the narrowest selector that catches its
# defect (Class#method where that narrows it), so the cost is dominated by
# Maven's reactor walk, not by tests.
#
# EXIT STATUS
#   0  every control bit
#   1  at least one control failed -- a gate did not bite, a gate failed for
#      the wrong reason, or the clean tree did not pass again
#   2  misuse (unknown option, unknown control)
#   3  the environment is not ready (no tools/, .venv/, _build/m2repo, mirror)
#   4  HARNESS ERROR: an anchor is gone, an injection did not reach the source
#      or the bytecode, a restoration did not reach the bytecode, a dirty run
#      did not compile, or a command ran none of the tests it named.  The run
#      proves nothing and must not be read as a pass.

set -Eeuo pipefail

# --------------------------------------------------------------- constants --
SCRIPT_NAME="$(basename -- "${BASH_SOURCE[0]}")"
readonly SCRIPT_NAME
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT
readonly SANDBOX="${ROOT}/_build/install-gate-sandbox"
readonly PRISTINE="${ROOT}/_build/install-gate-pristine"
readonly M2REPO="${ROOT}/_build/m2repo"
readonly LOGS="${ROOT}/_build/install-gate-logs"
readonly MIRROR="scratch/phase05/artefacts"

# The modules a control can run in.
readonly MOD_DOMAIN="cometgui-domain"
readonly MOD_INSTALL="cometgui-install"
readonly MOD_TOOLS="cometgui-tools"
readonly MOD_UI="cometgui-ui"
readonly MOD_APP="cometgui-app"

# The production files the controls damage.
readonly J="src/main/java/org/cometgui"
readonly DECLARED_CAPABILITY="${MOD_DOMAIN}/${J}/domain/tools/DeclaredCapability.java"
readonly PIPELINE="${MOD_INSTALL}/${J}/install/cache/InstallPipeline.java"
readonly DOWNLOADER="${MOD_INSTALL}/${J}/install/download/HttpDownloader.java"
readonly VIEW_MODEL="${MOD_UI}/${J}/ui/viewmodel/ToolManagerViewModel.java"
readonly PANE="${MOD_UI}/${J}/ui/controls/ToolManagerPane.java"
readonly VERIFIED_DOWNLOADER="${MOD_INSTALL}/${J}/install/verify/VerifiedDownloader.java"
readonly GUARD="${MOD_INSTALL}/${J}/install/archive/ExtractionGuard.java"
readonly PKG_READER="${MOD_INSTALL}/${J}/install/archive/PkgPayloadReader.java"
readonly TOOL_CACHE="${MOD_INSTALL}/${J}/install/cache/ToolCache.java"
readonly MANAGER="${MOD_INSTALL}/${J}/install/manager/ManagedToolManager.java"
readonly GATED_OFFERS="${MOD_INSTALL}/${J}/install/probe/ProbeGatedOffers.java"
readonly ALTERNATIVES="${MOD_INSTALL}/${J}/install/probe/ManifestAlternatives.java"
readonly COMPANION_GATE="${MOD_TOOLS}/${J}/tools/api/CompanionGate.java"
readonly LOCAL_PERCOLATOR="${MOD_TOOLS}/${J}/tools/percolator/LocalPercolatorRegistration.java"
readonly MANIFEST_CLASS="${MOD_INSTALL}/${J}/install/registry/ArtefactManifest.java"
readonly STAGED_PROBE="${MOD_INSTALL}/${J}/install/probe/StagedToolProbe.java"
readonly PLATFORM_FIXUPS="${MOD_INSTALL}/${J}/install/cache/PlatformFixups.java"
readonly SYNTHETIC_PIN="${MOD_TOOLS}/${J}/tools/percolator/SyntheticPin.java"

# The generator and the file it validates (control G).
readonly TOOLS_JSON="manifests/tools.json"
readonly TOOLMATRIX="scripts/toolmatrix.py"
readonly DOCS_BUILD="scripts/ci/docs-build.sh"

# Control M's delegation.  These strings are what make it load-bearing: if the
# words that say gate item 9 is not met are ever removed, this script fails.
readonly GATEKEEPER="scripts/ci/macos-gatekeeper-verify.sh"
readonly GATEKEEPER_NOT_A_PASS="THIS IS NOT A PASS and gate item 9 is NOT met by it"
readonly GATEKEEPER_CANNOT_GO_RED="THIS CHECK CANNOT GO RED ON THIS MACHINE"

# Phase 01's gates are off in the sandbox; see the header.
readonly -a QUIET=(
    "-Dspotless.check.skip=true"
    "-Dcheckstyle.skip=true"
    "-Dspotbugs.skip=true"
    "-Djacoco.skip=true"
)

# Every control id, in the order they run.  Cheap and Maven-free first.
readonly -a ALL_CONTROLS=(G M 1 2 3 6 8 9 10 19 11 12 13 16 17 20 21 22 14 15 18 4 5 7 H)

PASSED=0
FAILED=0
FAILURES=()
# The committed manifest's digest, taken when the sandbox is built.
MANIFEST_DIGEST=""
declare -a TIMINGS=()
# Every run module and selector a control used, for the final clean run.
declare -a USED_MODULES=()
declare -a USED_SELECTORS=()
# Every file a control damaged and restored, for the final bytecode proof.
declare -a RESTORED=()

# ----------------------------------------------------------------- plumbing --

usage() {
    cat <<USAGE
${SCRIPT_NAME} -- prove the PHASE-05 exit gate items fail on the defects they
exist to catch.

Usage:
  bash scripts/${SCRIPT_NAME}               every control
  bash scripts/${SCRIPT_NAME} --self-test   control H only: the harness must
                                            refuse to report a pass for an
                                            injection that did not land
  bash scripts/${SCRIPT_NAME} --only IDS    the named controls (comma-separated,
                                            from: ${ALL_CONTROLS[*]}) plus the
                                            baseline and the final clean run
  bash scripts/${SCRIPT_NAME} -h|--help

It needs a built tree: tools/, .venv/, a populated _build/m2repo and the
gitignored artefact mirror at ${MIRROR}.  It runs Maven offline, damages only
a git-archive sandbox under _build/, and writes only under _build/.

Exit status: 0 every control bit; 1 a control failed; 2 misuse; 3 the
environment is not ready; 4 a harness error (an injection that reached
nothing, or a restoration that did not reach the bytecode).
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
    TIMINGS+=("$(printf '%-4s %5ds' "${CONTROL_ID}" "${elapsed}")")
    printf '   (control %s took %ds)\n' "${CONTROL_ID}" "${elapsed}"
}

record_pass() {
    PASSED=$((PASSED + 1))
    printf '   PASS  %s\n' "$*"
}

# While control H provokes a failure on purpose, it is printed as REFUSED so
# that nobody reading the log mistakes the expected outcome for a real one.
DELIBERATE=0
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

# -------------------------------------------------- the closed module sets --
#
# module_closure MODULE -- MODULE and every cometgui module it depends on,
# transitively, read from the sandbox's own POMs.  This is the set `-pl MODULE
# -am` builds, and so the set whose bytecode a run is allowed to change.
module_closure() {
    python3 - "${SANDBOX}" "$1" <<'PYTHON'
import re
import sys
from pathlib import Path

root, wanted = Path(sys.argv[1]), sys.argv[2].split(",")
deps = {}
for pom in root.glob("cometgui-*/pom.xml"):
    module = pom.parent.name
    text = re.sub(r"<parent>.*?</parent>", "", pom.read_text(encoding="utf-8"), flags=re.S)
    found = set(re.findall(r"<artifactId>(cometgui-[a-z-]+)</artifactId>", text))
    deps[module] = sorted(found - {module})
for module in wanted:
    if module not in deps:
        sys.stderr.write("no module %r in the sandbox\n" % module)
        raise SystemExit(1)
seen, todo = set(), list(wanted)
while todo:
    module = todo.pop()
    if module in seen:
        continue
    seen.add(module)
    todo.extend(deps.get(module, []))
print(" ".join(sorted(seen)))
PYTHON
}

# ------------------------------------------------------- bytecode evidence --
#
# class_tree MODULE -- "sha256  path" for every compiled class, sorted.
class_tree() {
    local dir="${SANDBOX}/$1/target/classes"
    [ -d "${dir}" ] || return 0
    ( cd -- "${dir}" && find . -type f -name '*.class' -print0 | sort -z \
        | xargs -0 -r sha256sum )
}

baseline_tree_file() {
    printf '%s/baseline-classes-%s.sha256' "${LOGS}" "$1"
}

# class_prefix SOURCE -- ./org/.../Foo, the stem of every class javac writes
# for one source file: Foo.class and every Foo$*.class.
class_prefix() {
    local source="$1"
    local within="${source#*/src/main/java/}"
    printf './%s' "${within%.java}"
}

# is_class_of PREFIX PATH -- whether PATH is PREFIX.class or PREFIX$*.class.
is_class_of() {
    local prefix="$1" path="$2"
    [ "${path}" = "${prefix}.class" ] && return 0
    case "${path}" in
        "${prefix}\$"*.class) return 0 ;;
    esac
    return 1
}

record_baseline_trees() {
    local module tree count total=0
    for module in "$@"; do
        tree="$(baseline_tree_file "${module}")"
        class_tree "${module}" >"${tree}"
        count="$(wc -l <"${tree}")"
        [ "${count}" -gt 0 ] \
            || harness_error "the baseline run compiled no class in ${module}, so no clean bytecode can be compared against."
        total=$((total + count))
    done
    record_pass "baseline: the clean bytecode of $# module(s) is digested (${total} classes)"
}

# compare_trees LOG MODULE [INJECTED SOURCE] -- the bytecode of MODULE after a
# run, against its baseline.  With an injected source: that file's class set
# MUST differ, and every other class MUST be identical.  Without one: every
# class must be identical.  Prints a one-line summary on success; any other
# outcome is a harness error, because the run it describes graded the wrong
# bytecode.
compare_trees() {
    local log="$1" module="$2" injected="${3:-}"
    local baseline now prefix=""
    baseline="$(baseline_tree_file "${module}")"
    [ -s "${baseline}" ] \
        || harness_error "no baseline bytecode was recorded for ${module}."
    now="$(class_tree "${module}")"
    [ -n "${injected}" ] && prefix="$(class_prefix "${injected}")"

    local -A before=() after=()
    local digest path
    while read -r digest path; do
        before["${path}"]="${digest}"
    done <"${baseline}"
    while read -r digest path; do
        [ -n "${path}" ] && after["${path}"]="${digest}"
    done <<<"${now}"

    local changed_injected=0 injected_count=0 others=0
    local -a stray=()
    local -A all=()
    for path in "${!before[@]}"; do all["${path}"]=1; done
    for path in "${!after[@]}"; do all["${path}"]=1; done
    for path in "${!all[@]}"; do
        if [ -n "${prefix}" ] && is_class_of "${prefix}" "${path}"; then
            injected_count=$((injected_count + 1))
            if [ "${before[${path}]:-absent}" != "${after[${path}]:-absent}" ]; then
                changed_injected=$((changed_injected + 1))
            fi
            continue
        fi
        if [ "${before[${path}]:-absent}" != "${after[${path}]:-absent}" ]; then
            stray+=("${path#./}")
        else
            others=$((others + 1))
        fi
    done

    if [ "${#stray[@]}" -gt 0 ]; then
        harness_error "$(rel "${log}"): ${#stray[@]} class(es) in ${module} differ from the clean baseline although no control damaged them: ${stray[*]:0:5}. The run graded bytecode that is not the baseline's plus one injection -- a previous restoration did not reach the bytecode, or the build is not reproducible -- so its result cannot be attributed."
    fi
    if [ -n "${prefix}" ]; then
        [ "${injected_count}" -gt 0 ] \
            || harness_error "$(rel "${log}"): ${module} has no compiled class for ${injected} at all."
        if [ "${changed_injected}" -eq 0 ]; then
            harness_error "$(rel "${log}"): the compiled class of ${injected} is BYTE-IDENTICAL to the clean baseline after the dirty run. The injection reached the source and not the bytecode -- the eighth shape -- so the run tested the clean class and its result is not evidence of anything."
        fi
        printf '   bytecode: %s -- %d of %d class file(s) for the damaged source changed; %d other class(es) identical to the baseline\n' \
            "${module}" "${changed_injected}" "${injected_count}" "${others}"
    else
        printf '   bytecode: %s -- all %d class(es) identical to the clean baseline\n' \
            "${module}" "${others}"
    fi
}

# ------------------------------------------------------------ running tests --

# run_mvn LOG MODULE SELECTORS -- the one Maven command shape in this script.
# The exit status is written INTO the log as well as returned, because a
# wrapper's exit code has lied in this phase before.
run_mvn() {
    local log="$1" module="$2" selectors="$3"
    local rc=0 m closure
    closure="$(module_closure "${module}")" \
        || harness_error "the module set for ${module} cannot be read from the sandbox's POMs."
    for m in ${closure}; do
        rm -rf -- "${SANDBOX}/${m}/target/surefire-reports"
    done
    ( cd -- "${SANDBOX}" \
        && mvn -B -o -Dmaven.repo.local="${M2REPO}" "${QUIET[@]}" \
            -pl "${module}" -am test \
            -Dtest="${selectors}" -Dsurefire.failIfNoSpecifiedTests=false ) \
        >"${log}" 2>&1 || rc=$?
    printf '\n=== MVN EXIT STATUS: %d ===\n' "${rc}" >>"${log}"
    return "${rc}"
}

# Where a selector's test class lives, found from the sandbox's test sources so
# that a renamed or moved class is a harness error rather than a silent skip.
module_of_test() {
    local class="$1" found
    found="$(cd -- "${SANDBOX}" && find cometgui-*/src/test/java -name "${class}.java" -type f | head -2)"
    [ -n "${found}" ] \
        || harness_error "no test class ${class}.java exists in the sandbox. A control naming a test that does not exist tests nothing."
    [ "$(printf '%s\n' "${found}" | wc -l)" -eq 1 ] \
        || harness_error "the test class name ${class} is ambiguous in the sandbox: ${found}"
    printf '%s' "${found%%/*}"
}

# The outer class of a selector: Foo, Foo#bar and Foo#bar+baz are all Foo.
selector_class() {
    local selector="${1%%#*}"
    printf '%s' "${selector%%\$*}"
}

# THE VACUOUS PASS THIS SCRIPT COULD OFFER FOR FREE.  -Dtest with a name that
# matches nothing, plus -Dsurefire.failIfNoSpecifiedTests=false, exits 0 having
# run no test at all.  So every run, red or green, is required to have
# EXECUTED every class it named, read from surefire's XML report and not from
# the console (the console prints "Tests run: 0" for an outer class whose
# tests are all in @Nested classes).
verify_classes_ran() {
    local log="$1" selectors="$2"
    local -a wanted=() methods=()
    local selector class module report count source method outcome
    IFS=',' read -r -a wanted <<<"${selectors}"
    for selector in "${wanted[@]}"; do
        class="$(selector_class "${selector}")"
        module="$(module_of_test "${class}")"
        report="$(find "${SANDBOX}/${module}/target/surefire-reports" -maxdepth 1 \
            -name "TEST-*.${class}.xml" 2>/dev/null | head -1 || true)"
        if [ -z "${report}" ] || [ ! -s "${report}" ]; then
            harness_error "${selector} produced no surefire report in ${module} for $(rel "${log}"). The command named a test that surefire did not execute, so this run tested nothing."
        fi
        count="$(sed -n 's/.*<testsuite [^>]*tests="\([0-9][0-9]*\)".*/\1/p' "${report}" | head -1)"
        [ -n "${count}" ] \
            || harness_error "the surefire report for ${class} carries no tests= count; this harness cannot read it and would pass vacuously."
        [ "${count}" -ge 1 ] \
            || harness_error "${selector} executed ${count} tests in $(rel "${log}"). A selection that runs no test cannot prove or disprove a gate."
        if [ "${selector}" = "${selector%%#*}" ]; then
            # A CLASS SELECTOR BESIDE A Class#method SELECTOR SILENTLY DROPS
            # @Nested TESTS (see the header: SyntheticPinTest ran 16 tests and
            # none of its nested locale tests).  So a bare class selector is
            # allowed only for a class with no @Nested class; anything else
            # names its methods.
            source="$(cd -- "${SANDBOX}" && find "${module}/src/test/java" -name "${class}.java" -type f | head -1)"
            if grep -q '@Nested' "${SANDBOX}/${source}"; then
                harness_error "the selector ${selector} names a class with @Nested tests, and surefire drops those from a class selector without a word. Name the nested class and its methods (Outer\$Inner#method)."
            fi
            continue
        fi
        # Every method a selector names must have run, or the selection is not
        # what the control says it is.
        IFS='+' read -r -a methods <<<"${selector#*#}"
        for method in "${methods[@]}"; do
            outcome="$(testcase_outcome "${module}" "${class}" "${method}")"
            [ "${outcome}" != "absent" ] \
                || harness_error "${class}#${method} did not run in $(rel "${log}"); the selector ${selector} selects less than it names."
        done
    done
}

# testcase_outcome MODULE CLASS METHOD -- passed, failed, or absent, read from
# the XML report: the test either ran and passed, ran and failed, or did not
# run.  Used where the MEASUREMENT is that one test stays green while another
# in the same class goes red.
testcase_outcome() {
    local module="$1" class="$2" method="$3"
    python3 - "${SANDBOX}/${module}/target/surefire-reports" "${class}" "${method}" <<'PYTHON'
import glob
import sys
import xml.etree.ElementTree as ET

reports, cls, method = sys.argv[1:4]
outcome = "absent"
for path in glob.glob("%s/TEST-*.%s.xml" % (reports, cls)):
    for case in ET.parse(path).getroot().iter("testcase"):
        name = case.get("name", "")
        if name == method or name.startswith(method + "(") or name.startswith(method + "{"):
            failed = case.find("failure") is not None or case.find("error") is not None
            if failed:
                outcome = "failed"
            elif outcome == "absent":
                outcome = "passed"
print(outcome)
PYTHON
}

assert_testcase() {
    local label="$1" want="$2" module="$3" class="$4" method="$5"
    local got
    got="$(testcase_outcome "${module}" "${class}" "${method}")"
    if [ "${got}" = "absent" ]; then
        harness_error "(${label}) ${class}.${method} did not run, so its outcome cannot be read. The selector does not select it."
    fi
    if [ "${got}" = "${want}" ]; then
        record_pass "${label}: ${class}.${method} ${got}"
    else
        record_fail "${label}: ${class}.${method} ${got}, and this control requires it ${want}"
    fi
}

# TWO WAYS TO STATE AN EXPECTED DIAGNOSTIC.  A literal where the text carries
# nothing that varies; a regular expression where it carries a measured value
# -- a thread number, a port, a temporary path -- and then the expression must
# still name the thing.  "The test failed somehow" is never an assertion.
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

# grade_red MODE LABEL RC LOG EXPECTED -- the grading half of a dirty run,
# separate from running it so that control H can grade a run it knows is
# wrong and require the grader to refuse it.
grade_red() {
    local mode="$1" label="$2" rc="$3" log="$4" expected="$5"
    if [ "${rc}" -eq 0 ]; then
        record_fail "${label}: HARNESS FAILURE -- the check PASSED with the defect present. Either the gate is dead or the injection never reached the running code (log: $(rel "${log}"))"
        return
    fi
    if ! log_has "${log}" "${expected}" "${mode}"; then
        record_fail "${label}: failed, but without the expected diagnostic '${expected}' (log: $(rel "${log}"))"
        printf '         first error line: %s\n' \
            "$(grep -m1 -E '^\[ERROR\] +[A-Za-z]' "${log}" | cut -c1-160 || true)"
        return
    fi
    record_pass "${label}: rejected, exit ${rc}"
    printf '         %s\n' "$(log_first_match "${log}" "${expected}" "${mode}" | sed 's/^ *//' | cut -c1-220)"
}

assert_log_contains() { graded_log fixed "$@"; }
assert_log_matches() { graded_log regex "$@"; }

graded_log() {
    local mode="$1" label="$2" log="$3" expected="$4"
    if log_has "${log}" "${expected}" "${mode}"; then
        record_pass "${label}"
        printf '         %s\n' "$(log_first_match "${log}" "${expected}" "${mode}" | sed 's/^ *//' | cut -c1-220)"
    else
        record_fail "${label}: nothing in $(rel "${log}") matches '${expected}'"
    fi
}

assert_log_lacks() {
    local label="$1" log="$2" unexpected="$3"
    if grep -qF -- "${unexpected}" "${log}"; then
        record_fail "${label}: $(rel "${log}") contains '${unexpected}'"
    else
        record_pass "${label}"
    fi
}

# dirty_run LABEL SOURCE RUN_MODULE SELECTORS LOG -- runs the narrow check on
# the damaged sandbox and proves what it ran: that the module set compiled,
# that the damaged source's bytecode CHANGED, that nothing else's did, and that
# every named test class executed.  Leaves the exit status in DIRTY_RC.  Any
# failure of those proofs is a harness error: the run's colour would not be a
# result.
DIRTY_RC=0
dirty_run() {
    local label="$1" source="$2" module="$3" selectors="$4" log="$5"
    DIRTY_RC=0
    run_mvn "${log}" "${module}" "${selectors}" || DIRTY_RC=$?
    if grep -qE 'COMPILATION ERROR|Compilation failure' "${log}"; then
        harness_error "(${label}) the damaged sandbox does not compile -- see $(rel "${log}"). A red that never ran a test is not a result."
    fi
    if grep -qE 'NoClassDefFoundError|ClassNotFoundException: org\.cometgui' "${log}"; then
        harness_error "(${label}) $(rel "${log}") carries NoClassDefFoundError/ClassNotFoundException for project code: the reactor did not build a sibling, and the red is the harness's, not the gate's."
    fi
    local m closure
    closure="$(module_closure "${module}")" \
        || harness_error "the module set for ${module} cannot be read from the sandbox's POMs."
    for m in ${closure}; do
        if [ "${m}" = "${source%%/*}" ]; then
            compare_trees "${log}" "${m}" "${source}"
        else
            compare_trees "${log}" "${m}"
        fi
    done
    verify_classes_ran "${log}" "${selectors}"
    note_used "${module}" "${selectors}"
}

note_used() {
    USED_MODULES+=("$1")
    USED_SELECTORS+=("$2")
}

# ------------------------------------------------- injection and its guards --

save_pristine() {
    local file="$1"
    mkdir -p -- "$(dirname -- "${PRISTINE}/${file}")"
    cp -p -- "${SANDBOX}/${file}" "${PRISTINE}/${file}"
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
        "$(diff "${PRISTINE}/${file}" "${SANDBOX}/${file}" | grep -c '^[<>]' || true)"
}

# restore_pristine <file> -- copied back, compared, and TOUCHED: a copy that
# kept the snapshot's mtime would look older than the injected class, and
# Maven's incremental compiler would keep running the injected bytecode
# (handoffs/PHASE-05-worklog.rst, "A fourth shape of a red that is not a
# result").  The bytecode half of the proof is compare_trees, on the next run.
restore_pristine() {
    local file="$1"
    [ -e "${PRISTINE}/${file}" ] \
        || harness_error "no pristine copy of ${file} to restore from."
    cp -- "${PRISTINE}/${file}" "${SANDBOX}/${file}"
    cmp -s "${SANDBOX}/${file}" "${PRISTINE}/${file}" \
        || harness_error "could not restore ${file} in the sandbox."
    touch -- "${SANDBOX}/${file}"
    RESTORED+=("${file}")
    printf '   restored %s (byte-identical to the pristine copy, and touched)\n' "${file}"
}

# --------------------------------------------------- one recorded injection --
#
# java_control_inject LABEL SOURCE RUN_MODULE SELECTORS MODE EXPECTED OLD NEW
# The common shape: damage one production file once, run the narrowest
# selector, require the red with its own words.  The caller then makes any
# further assertions on DIRTY_LOG and restores the file.
DIRTY_LOG=""
java_control_inject() {
    local label="$1" source="$2" module="$3" selectors="$4" mode="$5" expected="$6"
    local old="$7" new="$8"
    save_pristine "${source}"
    replace_once "${label}" "${source}" "${old}" "${new}"
    assert_modified "${label}" "${source}"
    DIRTY_LOG="${LOGS}/${CONTROL_ID}-dirty.log"
    printf '   %s\n' "$(gate_command "${module}" "${selectors}")"
    dirty_run "${label}" "${source}" "${module}" "${selectors}" "${DIRTY_LOG}"
    grade_red "${mode}" "${label}" "${DIRTY_RC}" "${DIRTY_LOG}" "${expected}"
}

gate_command() {
    printf 'mvn -o -pl %s -am test -Dtest=%s' "$1" "$2"
}

# ------------------------------------------------------------- the sandbox --
build_sandbox() {
    rm -rf -- "${SANDBOX}" "${PRISTINE}"
    mkdir -p -- "${SANDBOX}" "${PRISTINE}"

    ( cd -- "${ROOT}" && git archive HEAD ) | tar -x -C "${SANDBOX}" \
        || harness_error "git archive HEAD could not be extracted into the sandbox."

    # tools/ is gitignored and therefore not in the archive.  The headless
    # JavaFX tests resolve the project-local font stack through the SANDBOX's
    # own maven.multiModuleProjectDirectory, so without this link controls 4,
    # 5 and 7 fail with "the project-local font stack is missing" -- correct
    # behaviour, and a red for the wrong reason.
    ln -s -- "${ROOT}/tools" "${SANDBOX}/tools"
    [ -f "${SANDBOX}/tools/env.sh" ] \
        || harness_error "the sandbox's tools/ symlink does not resolve. Every control would fail for the wrong reason."
    compgen -G "${SANDBOX}/tools/fontstack-*" >/dev/null \
        || harness_error "the sandbox's tools/ has no font stack. The headless JavaFX controls would fail for the wrong reason."
    # .venv/ for the Sphinx half of control G.
    ln -s -- "${ROOT}/.venv" "${SANDBOX}/.venv"
    # scratch/ for the real upstream artefact mirror, read-only by convention.
    ln -s -- "${ROOT}/scratch" "${SANDBOX}/scratch"
    [ -d "${SANDBOX}/${MIRROR}" ] \
        || harness_error "the sandbox's scratch/ symlink does not resolve to ${MIRROR}."

    mkdir -p -- "${SANDBOX}/_build"
    ln -s -- "${M2REPO}" "${SANDBOX}/_build/m2repo"

    MANIFEST_DIGEST="$(sha256_of "${SANDBOX}/${TOOLS_JSON}")"
    local head
    head="$(cd -- "${ROOT}" && git rev-parse --short HEAD)"
    echo "Sandbox: $(rel "${SANDBOX}") (git archive ${head}, $(find "${SANDBOX}"/cometgui-*/src -name '*.java' -type f | wc -l) java files)"
    local dirty
    dirty="$(cd -- "${ROOT}" && git status --porcelain -- 'cometgui-*/src' manifests scripts/toolmatrix.py docs/conf.py | head -20)"
    if [ -n "${dirty}" ]; then
        printf '\n  NOTE: the working tree has uncommitted changes this harness reads.\n'
        printf '        This run proves the gates of HEAD (%s), not of the working tree:\n' "${head}"
        printf '%s\n' "${dirty}" | sed 's/^/          /'
    fi
}

# ------------------------------------------------------------- the selectors --
#
# One place for every selector, so the baseline and the final clean run select
# exactly what the dirty runs select.
readonly SEL_1="DeclaredCapabilityTest#theEvidenceAxisIsCoveredWhole"
readonly SEL_2="ManagedToolManagerCancellationTest#cancellingInsideTheMainTransferReportsCancelled+cancellingAtAStepBoundaryReportsCancelled,CancelledInstallTest#cancellingInsideTheMainTransfer+cancellingAtAStepBoundary"
readonly SEL_3="HttpDownloaderTest\$Progress#onAResumedTransferCountsTheWholeArtefact"
readonly SEL_4="ProgressReachesTheInterfaceThreadTest"
readonly SEL_5="ToolManagerInstallUiTest#theFourManagedToolsInstallFromAnEmptyCache"
readonly SEL_6="CacheEdgeCasesTest#aSourceThatKeepsTheLengthAndChangesTheBytesIsRefused"
readonly SEL_7="ToolManagerInstallUiTest#aCorruptedArtefactIsRejectedAndNothingIsExecuted"
readonly SEL_8="ExtractionGuardTest#refusedPaths"
readonly SEL_9="XarTableOfContentsHardeningTest"
readonly SEL_10="ToolCacheTest#aLostFileIsCaughtByTheEntryCount"
readonly SEL_11="ManagedToolManagerInstallTest#anInstalledBuildThatNoLongerStartsIsNotOffered+anInstalledBuildThatNoLongerStartsCannotBeInstalledAgain"
readonly SEL_12="ProbeGatedOffersTest#anUnreachableBinaryIsRefusedAndDoesNotBlankTheList+theReasonIsReadFromTheWholeCauseChain+aRealStagedEntryThatVanished"
readonly SEL_13="ManifestAlternativesTest#aSiblingRowOfTheSameVersionIsAnAlternative+theSiblingRelationHoldsBothWays"
readonly SEL_14="CometCapabilityProbeTest#withoutOneThermoLibrary+withNoThermoLibraries+withTheThermoLibraries"
readonly SEL_15="LocalPercolatorRegistrationTest#tooOld+exactlyTheMinimum"
readonly SEL_16="ShippedManifestTest#aPlatformIndependentDownloadIsOfferedOnce,ManifestAlternativesTest#theNativeBuildOfAReleaseIsOfferedFirst,ArtefactManifestTest#selectionIsOrdered"
readonly SEL_17="ManagedToolManagerInstallTest#anInstallRunsEndToEndThroughThePort,ManagedToolManagerOffersTest#theRowsAScientistSeesOnLinux"
readonly SEL_18="SyntheticPinTest\$LocaleIndependence#theFixturesDoNotFollowTheDefaultLocale+bothFormatCallSitesHold"
readonly SEL_19="PlatformFixupsTest#aDeletionThatChangesNothingIsReportedAsNotCleared+onMacOsEveryQuarantinedFileIsClearedThroughXattr+onOtherHostsTheAttributeIsNotTouched"
readonly SEL_20="CometReleaseInstallTest#aBinaryThatIsNotThePinnedReleaseIsRefusedByItsBanner+everyLinuxCometReleaseInstallsAndIdentifiesItself"
readonly SEL_21="ShippedManifestTest#theDefaultCometIsTheNewestReleaseOnEveryHost"
readonly SEL_22="ManagedToolManagerOffersTest#theDefaultCometIsTheNewestReleaseTheManifestNames+theRowsAScientistSeesOnLinux"

# control_target ID -- "RUN_MODULE SELECTORS" for a Java control, empty for
# the others.  The baseline covers exactly the selected controls' targets, so
# --only and --self-test do not pay for controls they do not run.
control_target() {
    case "$1" in
        1|H) printf '%s %s' "${MOD_DOMAIN}" "${SEL_1}" ;;
        2) printf '%s %s' "${MOD_INSTALL}" "${SEL_2}" ;;
        3) printf '%s %s' "${MOD_INSTALL}" "${SEL_3}" ;;
        4) printf '%s %s' "${MOD_UI}" "${SEL_4}" ;;
        5) printf '%s %s' "${MOD_APP}" "${SEL_5}" ;;
        6) printf '%s %s' "${MOD_INSTALL}" "${SEL_6}" ;;
        7) printf '%s %s' "${MOD_APP}" "${SEL_7}" ;;
        8) printf '%s %s' "${MOD_INSTALL}" "${SEL_8}" ;;
        9) printf '%s %s' "${MOD_INSTALL}" "${SEL_9}" ;;
        10) printf '%s %s' "${MOD_INSTALL}" "${SEL_10}" ;;
        11) printf '%s %s' "${MOD_INSTALL}" "${SEL_11}" ;;
        12) printf '%s %s' "${MOD_INSTALL}" "${SEL_12}" ;;
        13) printf '%s %s' "${MOD_INSTALL}" "${SEL_13}" ;;
        14) printf '%s %s' "${MOD_TOOLS}" "${SEL_14}" ;;
        15) printf '%s %s' "${MOD_TOOLS}" "${SEL_15}" ;;
        16) printf '%s %s' "${MOD_INSTALL}" "${SEL_16}" ;;
        17) printf '%s %s' "${MOD_INSTALL}" "${SEL_17}" ;;
        18) printf '%s %s' "${MOD_TOOLS}" "${SEL_18}" ;;
        19) printf '%s %s' "${MOD_INSTALL}" "${SEL_19}" ;;
        20) printf '%s %s' "${MOD_INSTALL}" "${SEL_20}" ;;
        21) printf '%s %s' "${MOD_INSTALL}" "${SEL_21}" ;;
        22) printf '%s %s' "${MOD_INSTALL}" "${SEL_22}" ;;
        G|M) ;;
        *) die "no control '$1'. Controls: ${ALL_CONTROLS[*]}" 2 ;;
    esac
}

BASELINE_MODULES=""
BASELINE_SELECTORS=""

plan_baseline() {
    local id target
    local -a modules=() selectors=()
    for id in "$@"; do
        target="$(control_target "${id}")"
        [ -n "${target}" ] || continue
        modules+=("${target%% *}")
        selectors+=("${target#* }")
    done
    BASELINE_MODULES="$(printf '%s\n' "${modules[@]}" | awk 'NF && !seen[$0]++' | paste -sd, -)"
    BASELINE_SELECTORS="$(printf '%s\n' "${selectors[@]}" | tr ',' '\n' | awk 'NF && !seen[$0]++' | paste -sd, -)"
}

# --------------------------------------------------------------- control 0 --

control_baseline() {
    begin_control "0" "baseline: the undamaged sandbox passes every selector the controls use"
    # Every control below is a difference from this run.  If the clean tree
    # does not pass, nothing further can be attributed to an injection.  One
    # Maven invocation over the union of the controls' closed module sets.
    local log="${LOGS}/0-baseline.log" rc=0
    if [ -z "${BASELINE_MODULES}" ]; then
        printf '   (no Java control selected; no Maven baseline is needed)\n'
        end_control
        return 0
    fi
    printf '   %s\n' "$(gate_command "${BASELINE_MODULES}" "${BASELINE_SELECTORS}")"
    run_mvn "${log}" "${BASELINE_MODULES}" "${BASELINE_SELECTORS}" || rc=$?
    if [ "${rc}" -ne 0 ]; then
        record_fail "baseline: the undamaged sandbox does NOT pass the controls' selectors (exit ${rc}, log: $(rel "${log}")). Nothing below could be attributed to an injection."
        printf '         %s\n' "$(grep -m3 -E '^\[ERROR\] +[A-Za-z]' "${log}" | cut -c1-160 || true)"
        end_control
        return 1
    fi
    verify_classes_ran "${log}" "${BASELINE_SELECTORS}"
    record_pass "baseline: exit 0, every selector executed ($(printf '%s\n' "${BASELINE_SELECTORS}" | tr ',' '\n' | grep -c .) selectors)"
    local closure
    closure="$(module_closure "${BASELINE_MODULES}")" \
        || harness_error "the baseline module set cannot be read from the sandbox's POMs."
    # shellcheck disable=SC2086
    record_baseline_trees ${closure}
    end_control
}

# ------------------------------------------------------------- the controls --

control_1() {
    begin_control "1" "R-TOOL-08 [recorded, unit 1; the tenth shape]: an added conjunct in DeclaredCapability"
    # The injection unit 1's sign-off carried to unit 12 by name
    # (handoffs/PHASE-05-worklog.rst, p05-u1-injection and p05-tenth-shape):
    # the plausible-sounding "an unverified capability has no provenance to
    # state, so let its note be empty".  It sat under 100% line and branch
    # coverage and a 99.7% mutation score, because PIT mutates the expression
    # that is there and never adds a conjunct.  UNVERIFIED is the evidence of
    # every macOS and Windows capability row, and the note is the only field
    # saying why.
    java_control_inject "blank note accepted for UNVERIFIED" "${DECLARED_CAPABILITY}" \
        "${MOD_DOMAIN}" "${SEL_1}" fixed \
        'every evidence value must reject a blank note; a value missing here is a capability that can be claimed with no provenance ==> expected: <[OBSERVED_BY_EXECUTION, INFERRED_FROM_ARTEFACT_BYTES, UNVERIFIED]> but was: <[OBSERVED_BY_EXECUTION, INFERRED_FROM_ARTEFACT_BYTES]>' \
        '        if (note.isBlank()) {' \
        '        if (note.isBlank() && evidence != CapabilityEvidence.UNVERIFIED) {'
    restore_pristine "${DECLARED_CAPABILITY}"
    end_control
}

control_2() {
    begin_control "2" "item 1 [recorded, unit 8]: a mid-transfer cancellation reported as FAILED"
    # LIVE IN SIGNED-OFF CODE until unit 8 (p05-u8-cancel-defect,
    # p05-cancel-reproduced): "Cancelling an install mid-download reports
    # FAILED, not CANCELLED".  DownloadCancelledException is an IOException,
    # so without the translation it leaves ArtefactInstaller.install through
    # the catch (IOException) arm and a user who pressed Cancel is told the
    # install FAILED.  The red asserted is the PORT's -- the phase a scientist
    # is shown -- and the pipeline's exception-type assertion is asserted too.
    # Unit 5 graded cancellation at all eight STEP BOUNDARIES; the axis it
    # never varied was where WITHIN a step it lands.  So both step-boundary
    # tests are selected as well, and each is required to stay GREEN: that is
    # the measurement, not decoration.
    java_control_inject "cancellation translation removed" "${PIPELINE}" \
        "${MOD_INSTALL}" "${SEL_2}" fixed \
        'never FAILED: a user who cancelled has not encountered an error ==> expected: <CANCELLED> but was: <FAILED>' \
        '            throw new InstallCancelledException(record.describe(), step, cancelledMidTransfer);' \
        '            throw cancelledMidTransfer;'
    assert_log_contains "and the pipeline throws the transfer's own exception, not the one the contract names" \
        "${DIRTY_LOG}" \
        'Unexpected exception type thrown, expected: <org.cometgui.install.cache.InstallCancelledException> but was: <org.cometgui.install.download.DownloadCancelledException>'
    assert_testcase "the port's mid-transfer cancellation is caught" failed \
        "${MOD_INSTALL}" ManagedToolManagerCancellationTest cancellingInsideTheMainTransferReportsCancelled
    assert_testcase "the port's step-boundary test stays green: the boundary axis alone cannot see this" passed \
        "${MOD_INSTALL}" ManagedToolManagerCancellationTest cancellingAtAStepBoundaryReportsCancelled
    assert_testcase "and so does the pipeline's" passed \
        "${MOD_INSTALL}" CancelledInstallTest cancellingAtAStepBoundary
    restore_pristine "${PIPELINE}"
    end_control
}

control_3() {
    begin_control "3" "item 1 [recorded, unit 3]: progress on a resumed transfer counted from the resume point"
    # Unit 3's sign-off injection (p05-u3-signoff), which 338 tests passed
    # before the rework: the listener is told the offset, then jumps BACKWARDS
    # to the first chunk of the remainder -- a progress bar running in reverse
    # on the 99 MB PDV download, the one transfer big enough to be resumed.
    # The test is the one the rework added on the resumed axis.
    java_control_inject "resumed progress counted from the resume point" "${DOWNLOADER}" \
        "${MOD_INSTALL}" "${SEL_3}" regex \
        'and nothing below it is ever reported: a count of this attempt rather than of the artefact would show up here as a number near the first chunk size ==> expected: <1500000> but was: <[0-9]+>' \
        '                listener.onProgress(offset + transferred, declaredTotal);' \
        '                listener.onProgress(transferred, declaredTotal);'
    assert_log_contains "and the progress is not monotone across the resume boundary" \
        "${DIRTY_LOG}" 'monotone ACROSS the resume boundary, which is the join a fresh download never has'
    assert_log_contains "and the last report is the remainder, not the artefact" \
        "${DIRTY_LOG}" 'expected: <2000000> but was: <500000>'
    restore_pristine "${DOWNLOADER}"
    end_control
}

control_4() {
    begin_control "4" "item 1 [recorded, unit 9]: a progress report delivered to a live scene off the interface thread"
    # Unit 9's second rework (p05-u9-hop).  With the hop removed the whole tree
    # stayed green, because every test passed Runnable::run.  JavaFX RAISES NO
    # EXCEPTION here -- the orchestrator's own first probe was a "did not
    # throw" test and passed -- so the test observes the THREAD the change
    # arrives on, and its message names that thread.
    java_control_inject "interface-thread hop removed" "${VIEW_MODEL}" \
        "${MOD_UI}" "${SEL_4}" regex \
        "a progress report raised on a background install thread must reach the row's progress property on the JavaFX application thread, but it arrived on \"cometgui-install-[0-9]+\"" \
        '                        progress -> uiThread.execute(() -> onProgress(row, progress)));' \
        '                        progress -> onProgress(row, progress));'
    assert_log_matches "and a terminal report reaches the pane's row list off the interface thread too" \
        "${DIRTY_LOG}" \
        "must reach the pane's row list on the JavaFX application thread, but it arrived on \"cometgui-install-[0-9]+\""
    restore_pristine "${VIEW_MODEL}"
    end_control
}

control_5() {
    begin_control "5" "item 1 [recorded, unit 10]: the real Tool Manager's Install control disconnected"
    # Unit 10's carried-forward instruction: a disconnected control costs ten
    # minutes to detect with a no-op handler, because
    # ShownToolManager.TERMINAL_TIMEOUT_SECONDS is 600 to accommodate the
    # opt-in upstream run.  install.setDisable(true) is the same defect --
    # pressing Install does nothing -- and the gate-1 test catches it before
    # the first press, by reading each row's Install control in the real
    # window assembled in cometgui-app.  The row named is the first one the
    # test presses: the default Comet, 2026.03.0 since COMET-2026-03 unit 2
    # (D-010), where it was 2026.02.2 -- the same assertion on the same
    # control, reached one row earlier in a list that grew.
    java_control_inject "Install control disabled" "${PANE}" \
        "${MOD_APP}" "${SEL_5}" fixed \
        "comet-2026_03_0-1's Install control is disabled before anything is installed ==> expected: <true> but was: <false>" \
        '            install.setDisable(!row.canInstall());' \
        '            install.setDisable(true);'
    assert_log_lacks "and it was caught at once, by the window's own state, not by the 600 s terminal timeout" \
        "${DIRTY_LOG}" 'showed no terminal install phase within'
    restore_pristine "${PANE}"
    end_control
}

control_6() {
    begin_control "6" "item 2 [recorded, unit 10]: the installer's step-2 re-hash disabled"
    # Unit 10's orchestrator's injection (p05-u10-signoff), at the boundary
    # whose comment says "in a correct product this cannot fire, which is the
    # point": is that defence-in-depth check ever actually reached?  It is --
    # unit 5 wrote the test of a source that hands over bytes it says are
    # verified and are not.
    java_control_inject "step-2 re-hash disabled" "${PIPELINE}" \
        "${MOD_INSTALL}" "${SEL_6}" regex \
        'the probe was called for comet 2026\.02\.2 linux-x86-64 at .*, and this test exists to prove it is not: a corrupted artefact must be rejected before anything can execute it \(R-SEC-02, gate item 2\)' \
        '        if (!actual.sha256().equals(pinned.sha256())) {' \
        '        if (false) {'
    assert_log_matches "and the refusal the test requires never came" \
        "${DIRTY_LOG}" 'CacheEdgeCasesTest\.aSourceThatKeepsTheLengthAndChangesTheBytesIsRefused:[0-9]+ Unexpected exception type thrown, expected: <org\.cometgui\.install\.cache\.InstallRejectedException> but was: <java\.lang\.AssertionError>'
    restore_pristine "${PIPELINE}"
    end_control
}

control_7() {
    begin_control "7" "item 2 [unit 10's instruction]: a corrupted artefact let out of the transfer step"
    # Unit 10 neutered ArtefactVerifier's SHA-256 comparison and three tests
    # stayed green -- a flipped byte in a zip also fails to extract, and
    # VerificationResult's constructor independently re-states the rule -- so
    # it added an assertion on WHERE the artefact was rejected: the phases a
    # scientist's row shows are exactly [DOWNLOADING, FAILED].  The work log
    # says unit 12 must not build a control from that neutering expecting a
    # red; this harness's header says why it cannot produce one.  This control
    # removes the one thing between a rejected verdict and step 2 --
    # VerifiedDownloader's acceptance test -- so the corrupted file leaves the
    # transfer step and is caught only by step 2's re-hash.  Nothing is
    # executed either way, so the ONLY assertion that can see the defect is
    # the phase sequence, and its red is the one required.
    java_control_inject "the transfer step's verdict ignored" "${VERIFIED_DOWNLOADER}" \
        "${MOD_APP}" "${SEL_7}" regex \
        'R-SEC-02 is verify before you execute, so WHERE the artefact was rejected is the claim.*expected: <\[DOWNLOADING, FAILED\]> but was: <\[DOWNLOADING, VERIFYING, FAILED\]>' \
        '        if (firstVerdict.accepted()) {' \
        '        if (true) {'
    assert_log_lacks "and nothing was executed: the no-process assertion stays green" \
        "${DIRTY_LOG}" 'R-SEC-02: nothing was executed'
    assert_log_matches "and of the test's nine assertions exactly one failed: only the phase sequence saw this" \
        "${DIRTY_LOG}" 'ToolManagerInstallUiTest\.aCorruptedArtefactIsRejectedAndNothingIsExecuted:[0-9]+ Multiple Failures \(1 failure\)'
    restore_pristine "${VERIFIED_DOWNLOADER}"
    end_control
}

control_8() {
    begin_control "8" "item 3 [recorded, unit 4]: an absolute Windows path accepted as an install path"
    # Unit 4's orchestrator's injection (p05-u4-signoff): it bit because the
    # refused-path table carries "C:/x" with a FORWARD slash, the one
    # drive-letter form no other check catches.
    java_control_inject "drive letter never seen" "${GUARD}" \
        "${MOD_INSTALL}" "${SEL_8}" fixed \
        'accepted as an archive name: "C:/x"' \
        "        return path.length() >= 2 && Character.isLetter(path.charAt(0)) && path.charAt(1) == ':';" \
        '        return false;'
    assert_log_contains "and as a manifest install path, the guard's other caller" \
        "${DIRTY_LOG}" 'accepted as a manifest install path: "C:/x"'
    restore_pristine "${GUARD}"
    end_control
}

control_9() {
    begin_control "9" "item 3 [recorded, unit 4]: the DOCTYPE guard deleted from the xar table-of-contents parser"
    # Unit 4's five XXE guards (p05-xxe) could each be deleted with the suite
    # green until the rework; the orchestrator then deleted each in turn and
    # DISALLOW_DOCTYPE failed four tests, the most of the five.  The table of
    # contents is XML that arrived over the network inside a downloaded .pkg.
    java_control_inject "DOCTYPE guard deleted" "${PKG_READER}" \
        "${MOD_INSTALL}" "${SEL_9}" fixed \
        'a DOCTYPE declaration must be refused outright: it is where an external entity would be declared' \
        '        factory.setFeature(DISALLOW_DOCTYPE, true);' \
        '        // INJECTED by verify-install-gates.sh: the DOCTYPE guard is gone.'
    assert_log_contains "and an external entity is no longer refused at the declaration" \
        "${DIRTY_LOG}" 'XarTableOfContentsHardeningTest.anExternalEntityIsRefused'
    restore_pristine "${PKG_READER}"
    end_control
}

control_10() {
    begin_control "10" "item 4 [recorded, unit 5]: an install that lost a file still reports itself installed"
    # Unit 5's orchestrator's injection (p05-u5-signoff): the marker's
    # payloadEntryCount is a recorded value, and a cache that trusts it
    # compares the record with itself.
    java_control_inject "entry count read from the marker" "${TOOL_CACHE}" \
        "${MOD_INSTALL}" "${SEL_10}" regex \
        'ToolCacheTest\.aLostFileIsCaughtByTheEntryCount:[0-9]+ expected: <CONTENT_COUNT_MISMATCH> but was: <INSTALLED>' \
        '        int present = countPayloadEntries(directory);' \
        '        int present = marker.payloadEntryCount();'
    restore_pristine "${TOOL_CACHE}"
    end_control
}

control_19() {
    begin_control "19" "R-PLAT-04 [NEW at unit 14, not from the record]: a deletion believed, not re-checked"
    # NOT FROM THE RECORD.  The defect macos-gatekeeper run 36918810975 found
    # was a fix-up that changed nothing and reported nothing.  Unit 14 moved the
    # removal to /usr/bin/xattr and made the step LIST AGAIN after deleting, so
    # a deletion that exits 0 and leaves the attribute is a named failure.
    # This removes that re-check.  The scripted xattr standing in for the real
    # one is the ProcessRunner port production calls; what the real xattr does
    # on a Mac is not graded here (see the header).  The Linux branch's test is
    # selected too and is required to STAY GREEN: the injection is in the macOS
    # branch alone.
    java_control_inject "the deletion is believed, not re-checked" "${PLATFORM_FIXUPS}" \
        "${MOD_INSTALL}" "${SEL_19}" regex \
        'PlatformFixupsTest\.aDeletionThatChangesNothingIsReportedAsNotCleared:[0-9]+ Expected org\.cometgui\.install\.cache\.QuarantineNotClearedException to be thrown, but nothing was thrown\.' \
        '            if (quarantined(absolute, workingDirectory)) {' \
        '            if (false) {'
    assert_log_contains "and the faithful run no longer lists the file again after deleting from it" \
        "${DIRTY_LOG}" 'listed, deleted, and listed AGAIN: a deletion is re-checked, never assumed'
    assert_testcase "the silent no-op is believed" failed \
        "${MOD_INSTALL}" PlatformFixupsTest aDeletionThatChangesNothingIsReportedAsNotCleared
    assert_testcase "the Linux branch's test stays green: the injection is in the macOS branch" passed \
        "${MOD_INSTALL}" PlatformFixupsTest onOtherHostsTheAttributeIsNotTouched
    restore_pristine "${PLATFORM_FIXUPS}"
    end_control
}

control_11() {
    begin_control "11" "item 5 [recorded, unit 8]: a build that no longer starts, offered anyway"
    # Unit 8's orchestrator's injection that bit (p05-u8-injections): "a build
    # we installed once is one we know starts".  R-TOOL-06's last sentence,
    # graded at the offered set and at the install entry point.
    java_control_inject "refusalFor answers no refusal" "${MANAGER}" \
        "${MOD_INSTALL}" "${SEL_11}" fixed \
        'R-TOOL-06: a tool that fails loadability is never offered ==> expected: <[]> but was: <[ToolOffer[tool=PERCOLATOR, version=3.7.1' \
        '        return loadability.refusalFor(record);' \
        '        return Optional.empty();'
    assert_log_matches "and the install entry point accepts it again, so the rule is not only about rendering" \
        "${DIRTY_LOG}" 'ManagedToolManagerInstallTest\.anInstalledBuildThatNoLongerStartsCannotBeInstalledAgain:[0-9]+ Expected java\.lang\.IllegalArgumentException to be thrown, but nothing was thrown'
    restore_pristine "${MANAGER}"
    end_control
}

control_12() {
    begin_control "12" "item 5 [recorded, unit 6]: an unreachable binary's refusal naming no alternatives"
    # Unit 6's orchestrator's second, unannounced injection (p05-u6-rework):
    # "a binary we could not reach tells us nothing about what to try
    # instead".  R-PLAT-03 requires the available alternatives to be named,
    # and the identical call in advanceRefusal is left untouched -- so the
    # anchor is the call INSIDE unreachableRefusal, with its own indentation.
    java_control_inject "unreachable refusal names no alternatives" "${GATED_OFFERS}" \
        "${MOD_INSTALL}" "${SEL_12}" regex \
        'R-PLAT-03 requires the alternatives to be named, and they cannot be named from a path that discarded the offered set ==> expected: <.*Alternatives: percolator 3\.06\.5 linux-x86-64\.> but was: <.*Alternatives: none known' \
        '                        alternatives.apply(record));
        return classifier' \
        '                        List.of());
        return classifier'
    restore_pristine "${GATED_OFFERS}"
    end_control
}

control_13() {
    begin_control "13" "item 5 [recorded, unit 6]: the R-PLAT-03 alternatives keyed on the version, not the row"
    # Found by reading at unit 6 (p05-u6-alternatives-defect) and re-injected
    # by the orchestrator at acceptance: Comet 2026.02.2 is TWO macOS rows, and
    # excluding the failing build by version takes its sibling with it.  Since
    # COMET-2026-03 unit 2 the manifest also names 2026.03.0, which the
    # defect leaves standing, so the two tests compare the alternatives of
    # the failing build's OWN release -- where the defect still reads
    # "but was: <[]>" -- and both graded texts are unchanged.
    java_control_inject "alternatives keyed on the version" "${ALTERNATIVES}" \
        "${MOD_INSTALL}" "${SEL_13}" fixed \
        'R-PLAT-03 requires it to be named. Excluding the failing build by VERSION rather than by row takes the sibling with it and tells the scientist there is nothing else ==> expected: <[comet 2026.02.2 macos-x86-64]> but was: <[]>' \
        '        return candidate.url().equals(record.url());' \
        '        return candidate.version().equals(record.version());'
    assert_log_contains "and the relation holds both ways round" \
        "${DIRTY_LOG}" 'the rule is about which ROW failed, not which of the two is preferred ==> expected: <[comet 2026.02.2 macos-aarch64]> but was: <[]>'
    restore_pristine "${ALTERNATIVES}"
    end_control
}

control_14() {
    begin_control "14" "item 6 [NEW at unit 12, not from the record]: Thermo DLLs missing, THERMO_RAW_WINDOWS advertised"
    # NO INJECTION FOR GATE ITEM 6 IS RECORDED in handoffs/PHASE-05-worklog.rst,
    # so before unit 12 no test for it had been seen to fail.  The defect: a
    # companion gate whose missing-file check does not shut it, so a Comet
    # install with only some (or none) of the three Thermo DLLs advertises
    # THERMO_RAW_WINDOWS.  The test with all three present must stay GREEN,
    # which is what shows the rule is a rule and not a gate that is always
    # shut.
    java_control_inject "companion gate ignores a missing file" "${COMPANION_GATE}" \
        "${MOD_TOOLS}" "${SEL_14}" fixed \
        'R-TOOL-02: an install missing them shall not advertise it ==> expected: <true> but was: <false>' \
        '            if (!Files.isRegularFile(directory.resolve(name))) {
                return false;
            }' \
        '            if (!Files.isRegularFile(directory.resolve(name))) {
                continue;
            }'
    assert_testcase "with none of the DLLs it is advertised too" failed \
        "${MOD_TOOLS}" CometCapabilityProbeTest withNoThermoLibraries
    assert_testcase "and with all three present it is still advertised, so the gate is not merely shut" passed \
        "${MOD_TOOLS}" CometCapabilityProbeTest withTheThermoLibraries
    restore_pristine "${COMPANION_GATE}"
    end_control
}

control_15() {
    begin_control "15" "item 7 [NEW at unit 12, not from the record]: a local Percolator 3.04 registered"
    # NO INJECTION FOR GATE ITEM 7 IS RECORDED either.  The defect: the floor
    # the local-binary check compares against is 3.04 rather than the
    # specification's 3.05, so the binary the gate item names is registered.
    # The test asserts the refusal by type and then its WHOLE message,
    # hand-typed; the red is that the refusal never came.  "Exactly 3.05
    # registers" must stay green.
    java_control_inject "the floor moved to 3.04" "${LOCAL_PERCOLATOR}" \
        "${MOD_TOOLS}" "${SEL_15}" regex \
        'LocalPercolatorRegistrationTest\.tooOld:[0-9]+ Unexpected exception type thrown, expected: <org\.cometgui\.domain\.tools\.ToolRegistrationException> but was: <java\.lang\.AssertionError>' \
        '        if (version.isAtLeast(MINIMUM_VERSION)) {' \
        '        if (version.isAtLeast(ToolVersion.parse("3.04"))) {'
    # The AssertionError is the scripted runner's: the 3.04 binary got PAST
    # the floor and the registration went on to probe its capabilities.
    assert_log_contains "and the 3.04 binary went past the floor into the capability probe" \
        "${DIRTY_LOG}" 'the probe started 2 process(es) and only 1 answer(s) were scripted'
    assert_testcase "and exactly 3.05 still registers, so the red is the floor and not the parser" passed \
        "${MOD_TOOLS}" LocalPercolatorRegistrationTest exactlyTheMinimum
    restore_pristine "${LOCAL_PERCOLATOR}"
    end_control
}

control_16() {
    begin_control "16" "item 8 [recorded, unit 2]: a translated row offered ahead of a native one"
    # Unit 2's orchestrator's injection A (p05-u2-signoff): the second key of
    # OFFER_ORDER inverted.  After the rework it failed three tests where it
    # had failed one, including the shipped manifest's own JAR, which is
    # offered once and as TRANSLATED_ROSETTA_2 -- a false statement about a
    # Java program.  Unit 2's injections B and C land on that same assertion
    # and are not separate controls.
    java_control_inject "offer order inverted" "${MANIFEST_CLASS}" \
        "${MOD_INSTALL}" "${SEL_16}" fixed \
        'pdv is a JAR, and a row marked TRANSLATED_ROSETTA_2 would tell a scientist that a Java program runs under Rosetta 2 ==> expected: <NATIVE> but was: <TRANSLATED_ROSETTA_2>' \
        '                    .thenComparing(ArtefactSelection::isTranslated);' \
        '                    .thenComparing(ArtefactSelection::isTranslated, Comparator.reverseOrder());'
    # D-011 (2026-10-10): the shipped manifest no longer has a release with a
    # native and a translated build on one host -- the 2026.02.2 "x86-64" macOS
    # Comet row was arm64 and was removed -- so D-004's sentence is graded on
    # ManifestAlternativesTest's synthetic sibling row instead, with the same
    # failure text.
    assert_log_contains "and D-004's sentence, on a synthetic sibling row" \
        "${DIRTY_LOG}" 'the native build must be first, not merely present ==> expected: <NATIVE> but was: <TRANSLATED_ROSETTA_2>'
    assert_testcase "and the fixture ordering test" failed \
        "${MOD_INSTALL}" ArtefactManifestTest selectionIsOrdered
    restore_pristine "${MANIFEST_CLASS}"
    end_control
}

control_17() {
    begin_control "17" "R-TOOL-01 [recorded, unit 8]: a download size that misstates the transfer"
    # Unit 8 was sent back for this (p05-u8-signoff) and the orchestrator put
    # the artefact-only value back to see the new assertions fail.  Percolator
    # 3.07.1 fetches 946 303 bytes AND a 1 852 660-byte .deb.
    java_control_inject "download size is the artefact alone" "${MANAGER}" \
        "${MOD_INSTALL}" "${SEL_17}" fixed \
        'both of them served above ==> expected: <OptionalLong[2798963]> but was: <OptionalLong[946303]>' \
        '        long bytes = record.sizeBytes();
        for (ArtefactCompanion companion : record.companions()) {
            bytes += companion.sizeBytes();
        }
        return bytes;' \
        '        return record.sizeBytes();'
    assert_log_contains "and the hand-typed rows a scientist sees on Linux" \
        "${DIRTY_LOG}" 'percolator 3.07.1 NOT_INSTALLED 946303'
    restore_pristine "${MANAGER}"
    end_control
}

control_20() {
    begin_control "20" "R-TOOL-06 [NEW at COMET-2026-03 unit 2]: the identity stage made version-blind"
    # Not from the record: the intake's own injection.  The real 2026.03.0
    # binary is installed under a record identical to its own row except that
    # it pins 2026.02.2.  The SHA-256 matches -- these are the pinned bytes --
    # so the comparison of the banner's version with the record's is the only
    # thing standing between them, and with it gone the wrong release is
    # installed and recorded under 2026.02.2.
    java_control_inject "identity comparison ignored" "${STAGED_PROBE}" \
        "${MOD_INSTALL}" "${SEL_20}" fixed \
        'the 2026.03.0 binary must be refused under a record pinning 2026.02.2: its SHA-256 matches, so the identity stage reading the banner is the only thing that can tell the two releases apart ==> Expected org.cometgui.install.cache.InstallRejectedException to be thrown, but nothing was thrown.' \
        '        if (!identified.equals(record.version())) {' \
        '        if (false) {'
    assert_testcase "and every genuine release still installs and identifies itself: only the mislabelled one is affected" \
        passed "${MOD_INSTALL}" CometReleaseInstallTest everyLinuxCometReleaseInstallsAndIdentifiesItself
    restore_pristine "${STAGED_PROBE}"
    end_control
}

control_21() {
    begin_control "21" "D-010 [NEW at COMET-2026-03 unit 2]: select() no longer offers the newest release first"
    # Not from the record.  The default Comet is nothing but the first row
    # select() offers; there is no field naming it.  Reversing the first key
    # of OFFER_ORDER makes the oldest release the default on every host.
    java_control_inject "offer order oldest first" "${MANIFEST_CLASS}" \
        "${MOD_INSTALL}" "${SEL_21}" fixed \
        "D-010's default, host by host ==> expected: <[linux-x86-64 2026.03.0, linux-aarch64 2026.03.0, macos-aarch64 2026.03.0, windows-x86-64 2026.03.0]> but was: <[linux-x86-64 2026.02.2, linux-aarch64 2026.02.2, macos-aarch64 2026.02.2, windows-x86-64 2026.02.2]>" \
        '                            Comparator.reverseOrder())
                    .thenComparing(ArtefactSelection::isTranslated);' \
        '                            Comparator.naturalOrder())
                    .thenComparing(ArtefactSelection::isTranslated);'
    assert_log_contains "and the derivation from the rows says the same, on its own" \
        "${DIRTY_LOG}" 'linux-x86-64: the first Comet offered must be the newest one runnable there'
    restore_pristine "${MANIFEST_CLASS}"
    end_control
}

control_22() {
    begin_control "22" "D-010 [NEW at COMET-2026-03 unit 2]: the Tool Manager lists a tool's releases oldest first"
    # Not from the record.  The port orders releases itself, over the whole
    # manifest, so that a release with no build on this host is still shown
    # as unavailable; select()'s order does not reach it.  Inverting that sort
    # makes 2026.02.2 the first -- the default -- Comet a scientist is offered.
    java_control_inject "release order oldest first" "${MANAGER}" \
        "${MOD_INSTALL}" "${SEL_22}" fixed \
        'D-010: the default -- the first Comet offered -- is 2026.03.0 ==> expected: <2026.03.0> but was: <2026.02.2>' \
        '        newestFirst.sort(Comparator.reverseOrder());' \
        '        newestFirst.sort(Comparator.naturalOrder());'
    assert_log_contains "and the hand-typed rows a scientist sees on Linux go red with it" \
        "${DIRTY_LOG}" 'expected: <[comet 2026.03.0 NOT_INSTALLED 7077008, comet 2026.02.2 NOT_INSTALLED 7014400,'
    restore_pristine "${MANAGER}"
    end_control
}

control_18() {
    begin_control "18" "R-PERC-02 [recorded, unit 7]: the functional probe's fixture following the host locale"
    # Unit 7's orchestrator's injection (p05-u7-locale): Locale.ROOT removed
    # from both format calls.  205 tests passed, seven of them running the
    # real 3.07.1 binary over the generated PIN; under de_DE the PIN has
    # commas Percolator cannot parse, which reads as "cannot write XML" -- a
    # false negative on the capability the probe exists to establish.  The
    # rework's tests are in a @Nested class, which a class selector silently
    # drops, so they are named.
    save_pristine "${SYNTHETIC_PIN}"
    replace_once "Locale.ROOT removed (accession)" "${SYNTHETIC_PIN}" \
        '                            Locale.ROOT, target ? "sp|P%05d|TEST" : "decoy_sp|P%05d|TEST", row);' \
        '                            target ? "sp|P%05d|TEST" : "decoy_sp|P%05d|TEST", row);'
    replace_once "Locale.ROOT removed (feature columns)" "${SYNTHETIC_PIN}" \
        '                                    Locale.ROOT,
                                    "psm%d' \
        '                                    "psm%d'
    assert_modified "Locale.ROOT removed" "${SYNTHETIC_PIN}"
    DIRTY_LOG="${LOGS}/${CONTROL_ID}-dirty.log"
    printf '   %s\n' "$(gate_command "${MOD_TOOLS}" "${SEL_18}")"
    dirty_run "Locale.ROOT removed" "${SYNTHETIC_PIN}" "${MOD_TOOLS}" "${SEL_18}" "${DIRTY_LOG}"
    grade_red fixed "Locale.ROOT removed" "${DIRTY_RC}" "${DIRTY_LOG}" \
        'the 64 plus 64 fixture the real binary was run over changed under de-DE'
    assert_log_contains "and the negative control's fixture too" \
        "${DIRTY_LOG}" "the negative control's fixture changed under de-DE"
    assert_log_contains "and under a non-ASCII-digit locale, every numeric column" \
        "${DIRTY_LOG}" 'changed under th-TH-u-nu-thai'
    restore_pristine "${SYNTHETIC_PIN}"
    end_control
}

# ---------------------------------------------------------------- control G --
#
# THE GENERATOR.  scripts/toolmatrix.py validates manifests/tools.json and
# renders the platform matrix and the provenance table; docs/conf.py calls its
# generate() from a builder-inited hook, and the command-line form without
# --check calls the same generate().  Every injection here goes into the
# SANDBOX's manifest -- never the real one -- and is restored and proved
# restored (byte-identical, by SHA-256) before anything Maven reads it.

readonly GEN_OUT="_build/install-gate-toolmatrix"

# manifest_edit LABEL OP PATH [JSON VALUE] -- one edit to the sandbox
# manifest, written back in the file's own format (indent 2, which reproduces
# it byte for byte), so the diff is the edit and nothing else.  The path must
# exist and the value must change, or it is a harness error: an edit that
# changed nothing would test nothing.
manifest_edit() {
    local label="$1" op="$2" path="$3" value="${4:-}"
    local rc=0
    python3 - "${SANDBOX}/${TOOLS_JSON}" "${op}" "${path}" "${value}" <<'PYTHON' || rc=$?
import json
import re
import sys

file, op, path, value = sys.argv[1:5]
with open(file, encoding="utf-8") as handle:
    original = handle.read()
manifest = json.loads(original)
if json.dumps(manifest, indent=2, ensure_ascii=False) + "\n" != original:
    sys.stderr.write("the manifest is not in the format this edit writes back\n")
    raise SystemExit(1)
parts = re.findall(r"[^.\[\]]+|\[\d+\]", path)
node = manifest
for part in parts[:-1]:
    node = node[int(part[1:-1])] if part.startswith("[") else node[part]
last = parts[-1]
if op == "delete":
    if last not in node:
        sys.stderr.write("%s is not in the manifest\n" % path)
        raise SystemExit(1)
    del node[last]
elif op == "set":
    if last not in node:
        sys.stderr.write("%s is not in the manifest\n" % path)
        raise SystemExit(1)
    new = json.loads(value)
    if node[last] == new:
        sys.stderr.write("%s is already %r; the edit changes nothing\n" % (path, new))
        raise SystemExit(1)
    node[last] = new
else:
    sys.stderr.write("unknown operation %r\n" % op)
    raise SystemExit(1)
with open(file, "w", encoding="utf-8") as handle:
    handle.write(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n")
PYTHON
    if [ "${rc}" -ne 0 ]; then
        harness_error "(${label}) the manifest edit '${op} ${path}' did not land. A control whose defect was not injected tests nothing."
    fi
}

sha256_of() {
    sha256sum -- "$1" | cut -d' ' -f1
}

# generator_cli LOG -- the command-line form of the generator over the
# sandbox, which calls the same generate() the documentation build calls.
generator_cli() {
    local log="$1" rc=0
    rm -rf -- "${SANDBOX:?}/${GEN_OUT}"
    python3 "${SANDBOX}/${TOOLMATRIX}" --root "${SANDBOX}" --out-dir "${SANDBOX}/${GEN_OUT}" \
        >"${log}" 2>&1 || rc=$?
    printf '\n=== TOOLMATRIX EXIT STATUS: %d ===\n' "${rc}" >>"${log}"
    return "${rc}"
}

# docs_build LOG -- the project's own documentation gate, run IN the sandbox,
# so the builder-inited hook in the sandbox's docs/conf.py is what fires.
docs_build() {
    local log="$1" rc=0
    ( cd -- "${SANDBOX}" && bash "${SANDBOX}/${DOCS_BUILD}" ) >"${log}" 2>&1 || rc=$?
    printf '\n=== DOCS-BUILD EXIT STATUS: %d ===\n' "${rc}" >>"${log}"
    return "${rc}"
}

# generator_rejects ID LABEL OP PATH VALUE EXPECTED-INNER-DIAGNOSTIC
generator_rejects() {
    local id="$1" label="$2" op="$3" path="$4" value="$5" expected="$6"
    local log="${LOGS}/G${id}-cli.log" rc=0
    save_pristine "${TOOLS_JSON}"
    manifest_edit "${label}" "${op}" "${path}" "${value}"
    assert_modified "${label}" "${TOOLS_JSON}"
    generator_cli "${log}" || rc=$?
    grade_red fixed "G${id} ${label}" "${rc}" "${log}" "${expected}"
    if [ -n "$(find "${SANDBOX}/${GEN_OUT}" -type f 2>/dev/null | head -1)" ]; then
        record_fail "G${id} ${label}: the generator rejected the manifest AND wrote a fragment"
    fi
    restore_manifest
}

restore_manifest() {
    cp -- "${PRISTINE}/${TOOLS_JSON}" "${SANDBOX}/${TOOLS_JSON}"
    touch -- "${SANDBOX}/${TOOLS_JSON}"
    [ "$(sha256_of "${SANDBOX}/${TOOLS_JSON}")" = "${MANIFEST_DIGEST}" ] \
        || harness_error "the sandbox manifest is not the committed one after restoring it; every control that reads it would grade a damaged manifest."
}

control_G() {
    begin_control "G" "R-DOC-06/R-PERC-12 [recorded, unit 11]: the documentation-table generator"
    [ "$(cd -- "${ROOT}" && git rev-parse HEAD:"${TOOLS_JSON}")" \
        = "$(git hash-object -- "${SANDBOX}/${TOOLS_JSON}")" ] \
        || harness_error "the sandbox manifest is not HEAD's ${TOOLS_JSON}."

    # G0: the clean manifest renders, both fragments are written, and the
    # output names THIS manifest's digest -- unit 11's non-vacuity check.
    local log="${LOGS}/G0-cli-clean.log" rc=0
    generator_cli "${log}" || rc=$?
    if [ "${rc}" -ne 0 ]; then
        record_fail "G0 the clean manifest is rejected by the generator (exit ${rc}, log: $(rel "${log}"))"
    else
        assert_log_matches "G0 the clean manifest renders and the output names its own digest" \
            "${log}" "toolmatrix: from .*tools\.json -- [0-9]+ artefact record\(s\), .*sha256 ${MANIFEST_DIGEST}"
        local written
        written="$(find "${SANDBOX}/${GEN_OUT}" -type f -size +0 | wc -l)"
        if [ "${written}" -eq 2 ]; then
            record_pass "G0 and both fragments are written, non-empty"
        else
            record_fail "G0 the generator wrote ${written} non-empty fragment(s), not 2"
        fi
    fi

    # The five recorded injections (commit 9a33bb4's message), each asserting
    # the generator's INNER diagnostic -- the ToolMatrixError text specific to
    # that defect.  artefacts[0] is Comet 2026.02.2 linux-x86-64; the first
    # record carrying companions is found below rather than assumed by index.
    generator_rejects 1 "a missing sha256" delete "artefacts[0].sha256" "" \
        'artefacts[0] (comet 2026.02.2 linux-x86-64) is missing the required field "sha256"'
    generator_rejects 2 "an unknown platform" set "artefacts[0].os" '"plan9"' \
        'names the platform "plan9-x86-64", which this generator does not know'
    generator_rejects 3 "a plain http URL" set "artefacts[0].url" \
        '"http://github.com/UWPR/Comet/releases/download/v2026.02.2/comet.linux.exe"' \
        "has the download URL 'http://github.com/UWPR/Comet/releases/download/v2026.02.2/comet.linux.exe'. D-008 downloads every managed tool by pinned https URL"
    generator_rejects 4 "an unknown evidence value" set "artefacts[0].capabilities[0].evidence" '"rumoured"' \
        "capabilities[0] declares PEPXML_OUTPUT on the evidence 'rumoured'"
    local companion_path
    companion_path="$(python3 - "${SANDBOX}/${TOOLS_JSON}" <<'PYTHON'
import json
import sys

manifest = json.load(open(sys.argv[1], encoding="utf-8"))
for index, artefact in enumerate(manifest["artefacts"]):
    if artefact["companions"]:
        print("artefacts[%d].companions[0].id" % index)
        break
PYTHON
)"
    [ -n "${companion_path}" ] || harness_error "no artefact in the manifest carries a companion."
    generator_rejects 5 "a claim word in a companion id" set "${companion_path}" '"thermo-libraries-verified"' \
        'uses the word "verified"'

    # G6: through the REAL hook.  The project's documentation gate, run in the
    # sandbox on the clean manifest, passes, and its log carries the hook's
    # own line with this manifest's digest -- so a green docs build is proved
    # to have run the generator.
    log="${LOGS}/G6-docs-clean.log"
    rc=0
    docs_build "${log}" || rc=$?
    if [ "${rc}" -ne 0 ]; then
        record_fail "G6 the sandbox documentation build fails on the clean manifest (exit ${rc}, log: $(rel "${log}"))"
    else
        assert_log_contains "G6 the sandbox documentation build passes on the clean manifest" \
            "${log}" 'docs-build.sh: PASSED'
        assert_log_matches "G6 and the hook ran the generator over this manifest" \
            "${log}" "\[toolmatrix\] wrote .*manifest sha256 ${MANIFEST_DIGEST}"
    fi

    # G7: one rejection through the hook -- the claim word, which is checked
    # on the RENDERED text and so proves the hook runs the renderer too.  The
    # outer ExtensionError sentence is asserted only as the evidence that the
    # hook carried it; the grade is the inner diagnostic.
    log="${LOGS}/G7-docs-claim-word.log"
    rc=0
    save_pristine "${TOOLS_JSON}"
    manifest_edit "claim word, through the hook" set "${companion_path}" '"thermo-libraries-verified"'
    assert_modified "claim word, through the hook" "${TOOLS_JSON}"
    docs_build "${log}" || rc=$?
    grade_red fixed "G7 the documentation build fails with the generator's own diagnostic" \
        "${rc}" "${log}" 'uses the word "verified"'
    assert_log_contains "G7 and it came through the builder-inited hook" \
        "${log}" 'toolmatrix: the tool artefact manifest was rejected, so the documentation build fails'
    restore_manifest

    # G8: unit 11's orchestrator's injection, into the generator itself:
    # generate() returns early when the output directory already exists -- "the
    # fragments are already there from the last build".  G6 left yesterday's
    # fragments in the sandbox, which is the situation the defect needs.
    log="${LOGS}/G8-docs-early-return.log"
    rc=0
    [ -n "$(find "${SANDBOX}/docs/_generated" -type f 2>/dev/null | head -1)" ] \
        || harness_error "(G8) no fragments from a previous build are in the sandbox, so an early return on 'already there' would not be exercised."
    save_pristine "${TOOLMATRIX}"
    replace_once "generate() returns early" "${TOOLMATRIX}" \
        '    source = Path(manifest_file) if manifest_file else manifest_path(root)' \
        '    if Path(out_dir).is_dir():
        return {}
    source = Path(manifest_file) if manifest_file else manifest_path(root)'
    assert_modified "generate() returns early" "${TOOLMATRIX}"
    docs_build "${log}" || rc=$?
    grade_red regex "G8 a generator that writes nothing fails the documentation build" \
        "${rc}" "${log}" "_generate_tool_matrix .*builder-inited.*exception: 'written'"
    restore_pristine "${TOOLMATRIX}"
    end_control
}

# ---------------------------------------------------------------- control M --

control_M() {
    begin_control "M" "item 9: NOT MET -- DELEGATED and enforced, never claimed"
    # Gate item 9 is "on macOS, a freshly installed managed tool executes
    # without a Gatekeeper refusal".  No macOS binary has ever been executed
    # anywhere in this project, and NOTHING HERE COVERS ITEM 9.  This control
    # is a delegation: it requires unit 13's driver to still say, in the words
    # its own self-test pins, that a control which did not bite is NOT A PASS,
    # and requires that self-test to pass.  It fails if those words go.
    local file
    for file in "${GATEKEEPER_NOT_A_PASS}" "${GATEKEEPER_CANNOT_GO_RED}"; do
        if grep -qF -- "${file}" "${SANDBOX}/${GATEKEEPER}"; then
            record_pass "M ${GATEKEEPER} still says: ${file}"
        else
            record_fail "M ${GATEKEEPER} no longer contains '${file}'"
        fi
    done
    local log="${LOGS}/M-gatekeeper-self-test.log" rc=0
    ( cd -- "${SANDBOX}" && bash "${SANDBOX}/${GATEKEEPER}" --self-test ) >"${log}" 2>&1 || rc=$?
    if [ "${rc}" -ne 0 ]; then
        record_fail "M ${GATEKEEPER} --self-test failed (exit ${rc}, log: $(rel "${log}"))"
    else
        assert_log_matches "M its --self-test passes, every case seen to bite" \
            "${log}" 'self-test OK -- [0-9]+/[0-9]+ controls, every one seen to bite'
        assert_log_contains "M and still says it proves nothing about Gatekeeper" \
            "${log}" 'AND IT STILL PROVES NOTHING ABOUT GATEKEEPER'
    fi
    printf '   NOTE  gate item 9 is NOT MET and this control does not make it met.\n'
    end_control
}

# ---------------------------------------------------------------- control H --

# expect_harness_error LABEL WHAT -- runs WHAT in a subshell, which must stop
# with a HARNESS ERROR (exit 4) naming why.  Run in a subshell so that the
# refusal it provokes does not end this run.
expect_harness_error() {
    local label="$1" why="$2"
    shift 2
    local out rc=0
    out="$( ( "$@" ) 2>&1 )" || rc=$?
    if [ "${rc}" -eq 4 ] && printf '%s' "${out}" | grep -q 'HARNESS ERROR' \
        && printf '%s' "${out}" | grep -qF -- "${why}"; then
        record_pass "${label}"
        printf '         %s\n' "$(printf '%s\n' "${out}" | grep 'HARNESS ERROR' | head -1 | cut -c1-200)"
    else
        record_fail "${label}: the harness did NOT refuse (exit ${rc}); it must stop with a HARNESS ERROR naming '${why}'. Output: $(printf '%s' "${out}" | tail -3 | tr '\n' ' ' | cut -c1-300)"
    fi
}

# expect_recorded_failure LABEL WHAT -- WHAT must record exactly one failure,
# which is then taken back: it was the expected outcome.
expect_recorded_failure() {
    local label="$1"
    shift
    local before="${FAILED}"
    DELIBERATE=1
    "$@"
    DELIBERATE=0
    if [ "${FAILED}" -eq $(( before + 1 )) ]; then
        FAILED="${before}"
        unset 'FAILURES[-1]'
        record_pass "${label}"
    else
        record_fail "${label}: HARNESS FAILURE -- it did not record a failure. Every other control in this script is unreliable."
    fi
}

h_same_text() {
    replace_once "H1" "${DECLARED_CAPABILITY}" '        if (note.isBlank()) {' '        if (note.isBlank()) {'
    assert_modified "H1" "${DECLARED_CAPABILITY}"
}

h_no_anchor() {
    replace_once "H2" "${DECLARED_CAPABILITY}" 'this text is in no source file' 'nor is this'
}

h_manifest_no_change() {
    save_pristine "${TOOLS_JSON}"
    manifest_edit "H3" set "artefacts[0].tool" '"comet"'
}

h_source_not_bytecode() {
    replace_once "H4" "${DECLARED_CAPABILITY}" \
        '        if (note.isBlank()) {' \
        '        if (note.isBlank()) { // H4: the source changed and the bytecode cannot'
    assert_modified "H4" "${DECLARED_CAPABILITY}"
    dirty_run "H4" "${DECLARED_CAPABILITY}" "${MOD_DOMAIN}" "${SEL_1}" "${LOGS}/H4-comment-only.log"
}

control_H() {
    begin_control "H" "the harness itself: an injection that did not land must never be a pass"
    save_pristine "${DECLARED_CAPABILITY}"

    # (i) a replacement equal to its anchor leaves the file unchanged.
    expect_harness_error "H1 an injection whose replacement equals its anchor is refused" \
        "byte-identical to the pristine copy" h_same_text
    # an anchor that is not in the file.
    expect_harness_error "H2 an anchor that matches nothing is refused" \
        "the injection anchor is gone" h_no_anchor
    cmp -s "${SANDBOX}/${DECLARED_CAPABILITY}" "${PRISTINE}/${DECLARED_CAPABILITY}" \
        || harness_error "control H's refused injections changed ${DECLARED_CAPABILITY} after all."
    # a manifest edit that changes nothing.
    expect_harness_error "H3 a manifest edit that changes nothing is refused" \
        "did not land" h_manifest_no_change
    cmp -s "${SANDBOX}/${TOOLS_JSON}" "${PRISTINE}/${TOOLS_JSON}" \
        || harness_error "control H's refused manifest edit changed ${TOOLS_JSON} after all."

    # (ii) an injection that reaches the source and not the bytecode: a
    # comment on the anchor's own line moves no line number, so javac writes
    # the same class.  assert_modified accepts it -- the source did change --
    # and the bytecode check must refuse it.  This is a real Maven run.
    expect_harness_error "H4 an injection that changed the source but not the bytecode is refused" \
        "BYTE-IDENTICAL to the clean baseline after the dirty run" h_source_not_bytecode

    # (iii) a control run with no defect in it.  H4's run WAS such a run -- the
    # code it tested is the clean code -- so its log is graded as a red here,
    # and the grader must record a failure rather than a pass.
    local log="${LOGS}/H4-comment-only.log" rc
    [ -s "${log}" ] || harness_error "H4 left no log, so (iii) has nothing to grade."
    rc="$(sed -n 's/^=== MVN EXIT STATUS: \([0-9]*\) ===$/\1/p' "${log}" | tail -1)"
    [ "${rc}" = "0" ] \
        || harness_error "H4's run of the clean code did not pass (exit ${rc:-unknown}); (iii) needs a green run to grade."
    expect_recorded_failure "H5 a control run with NO defect injected is recorded as a failure, not a pass" \
        grade_red fixed "H5 (deliberately graded with no defect injected)" "${rc}" "${log}" \
        'every evidence value must reject a blank note'

    # A red for the wrong reason: a failing run whose log lacks the expected
    # diagnostic.  The log is written here, so this grades the grader alone.
    log="${LOGS}/H6-wrong-reason.log"
    printf '[ERROR] Tests run: 1, Failures: 1\n[ERROR]   SomeOtherTest.somethingElse:1 expected: <1> but was: <2>\n' >"${log}"
    expect_recorded_failure "H6 a red WITHOUT the expected diagnostic is recorded as a failure, not a pass" \
        grade_red fixed "H6 (deliberately graded against the wrong reason)" 1 "${log}" \
        'every evidence value must reject a blank note'

    restore_pristine "${DECLARED_CAPABILITY}"
    restore_manifest
    # The comment-only class was compiled in H4: the final clean run must
    # prove the domain module's bytecode is back to the baseline's.
    note_used "${MOD_DOMAIN}" "${SEL_1}"
    end_control
}

# ---------------------------------------------------------------- the main --

run_control() {
    case "$1" in
        1) control_1 ;; 2) control_2 ;; 3) control_3 ;; 4) control_4 ;;
        5) control_5 ;; 6) control_6 ;; 7) control_7 ;; 8) control_8 ;;
        9) control_9 ;; 10) control_10 ;; 11) control_11 ;; 12) control_12 ;;
        13) control_13 ;; 14) control_14 ;; 15) control_15 ;; 16) control_16 ;;
        17) control_17 ;; 18) control_18 ;; 19) control_19 ;;
        20) control_20 ;; 21) control_21 ;; 22) control_22 ;;
        G) control_G ;; M) control_M ;; H) control_H ;;
        *) die "no control '$1'. Controls: ${ALL_CONTROLS[*]}" 2 ;;
    esac
}

final_clean_run() {
    begin_control "C" "every restoration: the clean sandbox passes again, on the baseline's own bytecode"
    local log="${LOGS}/C-clean.log" rc=0
    local modules selectors m s
    modules="$(printf '%s\n' "${USED_MODULES[@]}" | sort -u | paste -sd, -)"
    selectors="$(printf '%s\n' "${USED_SELECTORS[@]}" | tr ',' '\n' | awk '!seen[$0]++' | paste -sd, -)"
    printf '   %s\n' "$(gate_command "${modules}" "${selectors}")"
    run_mvn "${log}" "${modules}" "${selectors}" || rc=$?
    if [ "${rc}" -ne 0 ]; then
        record_fail "the clean sandbox does not pass again after the restorations (exit ${rc}, log: $(rel "${log}"))"
        printf '         %s\n' "$(grep -m3 -E '^\[ERROR\] +[A-Za-z]' "${log}" | cut -c1-160 || true)"
    else
        verify_classes_ran "${log}" "${selectors}"
        record_pass "the clean sandbox passes again: exit 0, every selector the dirty runs used executed"
    fi
    # The bytecode half of every restoration.  Every module the run compiled
    # must be byte-identical to the baseline, which proves every restored file
    # reached the bytecode -- the stale-class trap is refused here.
    local -A closure=()
    for m in $(printf '%s\n' "${USED_MODULES[@]}" | sort -u); do
        s="$(module_closure "${m}")" \
            || harness_error "the module set for ${m} cannot be read from the sandbox's POMs."
        for s in ${s}; do closure["${s}"]=1; done
    done
    for m in $(printf '%s\n' "${!closure[@]}" | sort); do
        compare_trees "${log}" "${m}"
    done
    local file
    for file in $(printf '%s\n' "${RESTORED[@]}" | grep '\.java$' | sort -u); do
        record_pass "restored bytecode of ${file##*/} is byte-identical to the clean baseline"
    done
    end_control
}

main() {
    local self_test_only=0 only=""
    while [ "$#" -gt 0 ]; do
        case "$1" in
            -h|--help) usage; exit 0 ;;
            --self-test) self_test_only=1; shift ;;
            --only)
                [ "$#" -ge 2 ] || die "--only needs control ids" 2
                only="${only}${only:+,}$2"
                shift 2
                ;;
            *) usage >&2; die "unknown option: $1" 2 ;;
        esac
    done

    cd -- "${ROOT}"
    command -v git >/dev/null || die "git is not on PATH; the sandbox is a git archive." 3
    command -v python3 >/dev/null || die "python3 is not on PATH." 3
    [ -f "${ROOT}/tools/env.sh" ] || die "tools/env.sh is missing; run bash scripts/build.sh first." 3
    # shellcheck disable=SC1091
    . "${ROOT}/tools/env.sh"
    command -v mvn >/dev/null || die "mvn is not on PATH after sourcing tools/env.sh." 3
    [ -d "${M2REPO}" ] || die "$(rel "${M2REPO}") does not exist; run bash scripts/build.sh first." 3
    [ -x "${ROOT}/.venv/bin/sphinx-build" ] || die ".venv/bin/sphinx-build is missing; control G needs the project's Sphinx." 3
    [ -d "${ROOT}/${MIRROR}" ] \
        || die "${MIRROR} does not exist. This phase's install, cancellation and extraction suites read the REAL upstream artefact bytes from there and FAIL rather than skip without them. Refill it from the URLs in manifests/tools.json, verifying each SHA-256." 3

    local -a selected=()
    if [ "${self_test_only}" -eq 1 ]; then
        selected=(H)
    elif [ -n "${only}" ]; then
        IFS=',' read -r -a selected <<<"${only}"
    else
        selected=("${ALL_CONTROLS[@]}")
    fi

    mkdir -p -- "${LOGS}"
    rm -f -- "${LOGS}"/*.log "${LOGS}"/*.sha256

    printf '===============================================================================\n'
    printf ' %s -- every PHASE-05 gate this phase recorded must be seen to fail\n' "${SCRIPT_NAME}"
    printf '===============================================================================\n'
    printf '  repository   %s\n' "${ROOT}"
    printf '  logs         %s\n' "$(rel "${LOGS}")"
    printf '  controls     %s\n' "${selected[*]}"

    local started
    started="$(date +%s)"
    plan_baseline "${selected[@]}"
    build_sandbox
    control_baseline || true
    if [ "${FAILED}" -eq 0 ]; then
        local id
        for id in "${selected[@]}"; do
            run_control "${id}"
        done
        if [ "${#USED_MODULES[@]}" -gt 0 ]; then
            final_clean_run
        fi
    fi

    local total=$(( $(date +%s) - started ))
    printf '\n===============================================================================\n'
    printf ' per-control wall clock:\n'
    printf '   %s\n' "${TIMINGS[@]}"
    printf ' SUMMARY: %d control(s) passed, %d failed, in %d seconds\n' \
        "${PASSED}" "${FAILED}" "${total}"
    printf ' Logs: %s\n' "$(rel "${LOGS}")"
    printf '===============================================================================\n'
    if [ "${FAILED}" -ne 0 ]; then
        printf '\n'
        printf '  %s\n' "${FAILURES[@]}"
        die "${FAILED} install-gate control(s) failed. A gate that cannot be seen to fail is not a gate." 1
    fi
    if [ "${self_test_only}" -eq 1 ]; then
        printf '\n  self-test OK -- the harness reports an injection that reached the source but\n'
        printf '  not the bytecode, an unchanged file, a missing anchor, a run with no defect\n'
        printf '  and a red for the wrong reason as a HARNESS ERROR or FAILURE, not as a pass.\n\n'
        return 0
    fi
    if [ -n "${only}" ]; then
        printf '\n  The selected controls bit (--only %s). This is NOT a full run.\n\n' "${only}"
        return 0
    fi
    printf '\n  PHASE-05 exit gate items 1, 2, 3, 4, 5 and 8 were proved here from the\n'
    printf '  injections recorded in handoffs/PHASE-05-worklog.rst, and items 6 and 7 by\n'
    printf '  controls NEW at unit 12 that the record did not have.\n'
    printf '  R-PLAT-04: control 19 (NEW at unit 14) grades the macOS quarantine branch on\n'
    printf '  Linux, against a scripted xattr. What xattr does on a Mac is NOT graded here.\n'
    printf '  Item 9 is NOT MET: no macOS binary has ever been executed in this project.\n'
    printf '  Control M is a delegation to %s and covers nothing.\n' "${GATEKEEPER}"
    printf '  The harness reports an injection that reached the source but not the\n'
    printf '  bytecode as a HARNESS ERROR, not as a pass.\n'
    printf '\n  Every gate rejected its defect and accepted the clean tree.\n\n'
}

main "$@"
