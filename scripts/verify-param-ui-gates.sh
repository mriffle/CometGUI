#!/usr/bin/env bash
#
# CometGUI -- prove the PHASE-07 Comet parameter editor gates can fail.
#
#   bash scripts/verify-param-ui-gates.sh                  every control
#   bash scripts/verify-param-ui-gates.sh --self-test      control H only
#   bash scripts/verify-param-ui-gates.sh --only 1,4v,H    named controls only
#
# A gate that has never been seen to fail has not been shown to work
# (CONTRIBUTING.rst, "Gate conventions").  Phase 07's exit gate is eight claims
# about the parameter editor of a running JavaFX application, each checked by a
# headless GUI test in cometgui-app (decision P7-10).  A green GUI test says
# nothing about whether it would notice the defect it exists to catch, and this
# phase has already met three that would not: unit 6's accessibility test
# accepted Phase 02's generated fallback names, unit 7's Expert test started
# from the defaults a reset reproduces, and unit 6's "Run disabled only by the
# engine reason" was equivalent until Phase 08 (it still is in the item-6 test,
# whose application has no Comet installed; control H shows both halves).  So
# this script injects, one at
# a time, a defect each item exists to catch -- into PRODUCTION code of
# cometgui-ui, the views, controls and view-models the tests drive -- requires
# the gate test class that asserts the item to fail WITH THAT DEFECT'S OWN
# DIAGNOSTIC (the failing assertion's own words, never a sentence every failure
# shares), proves the injection reached the compiled classes, and requires the
# clean sandbox to pass again on the baseline's own bytecode.
#
# It is the sibling of scripts/verify-shell-gates.sh (Phase 02's GUI gates) and
# scripts/verify-param-gates.sh (Phase 06's parameter model), and takes its
# shape from them rather than inventing a third: a `git archive HEAD` sandbox,
# the tools/ symlink the headless GUI tests need, upstream modules built once
# into a private overlay repository, anchors that must match exactly once, a
# pristine copy per damaged file, graded failures, bytecode digests, one
# batched clean re-run, and a control H on itself.
#
# IT IS ASSEMBLED FROM A RECORD.  Every control marked [recorded] names the
# unit of handoffs/PHASE-07-worklog.rst whose sign-off (orchestrator) or report
# (agent) made that injection and saw it red; where the record gives the
# failure text, the control requires the same words.  [NEW] marks this
# script's own.
#
# WHAT IT COVERS (PHASE-07 exit gate items; see phases/PHASE-07-comet-param-ui.rst)
#
#   0   baseline: the private overlay is built from the sandbox, the undamaged
#       sandbox passes every graded test class in one run, and the compiled
#       cometgui-ui and cometgui-app are digested
#   1   item 1 [named in the unit 8 brief; the work log records no injection
#       for item 1]: the Essentials decoy control always chooses
#       FASTA_CONTAINS_DECOYS, so the choice the test makes does not
#       hold -- red at the test's own choose() step, which names the control
#       (EssentialsTrypticSearchUiTest)
#   1b  item 1 [NEW]: every control shows the choice and the SAVE writes
#       decoy_search = 0 -- visible only to the byte comparison of the saved
#       file with the checked-in expected file, which must go red, and the
#       copy the test leaves must differ from the expected file in that one
#       line alone
#   1c  item 1 [NEW, unit 10]: a row of the static-modification table writes
#       its mass to the NEXT row's parameter, so the lysine TMT lands on
#       glutamic acid -- red in the static-modification table's GUI test at
#       the lysine row, and in the gate-1 search's saved-file comparison
#       (StaticModificationTableUiTest, EssentialsTrypticSearchUiTest)
#   2a  item 2 [NEW]: the slot editor's "Move up" moves the slot DOWN, so the
#       serialised tuple after the reorder step is wrong
#       (VariableModificationEditorUiTest)
#   2v  item 2, VERSION-BLIND [recorded, unit 6 injection 6a]: the slot editor
#       offers TerminalCode.values() -- ^ and $ -- on every release; the
#       2026.03.0 test must STAY GREEN, the 2026.02.2 one goes red
#   3a  item 3 [recorded, unit 7 injection 7b]: the preset preview's Cancel
#       applies every row (PresetPreviewUiTest)
#   3b  item 3 [NEW]: "Apply selected" applies every row, so the subset is not
#       exactly the subset
#   3c  item 3 [NEW, unit 10]: the Essentials fragment instrument choice
#       applies its preset's rows the moment it is chosen, before the
#       scientist has seen them (AC-PAR-08: "a diff before changing
#       anything") -- unit 5's behaviour, which unit 9 found and unit 10
#       removed (FragmentInstrumentPreviewUiTest)
#   3d  item 3 [NEW, unit 10]: the fragment choice's preview is not scoped to
#       the fragment parameters, so it offers -- and Apply all would apply --
#       the preset's precursor rows too; the whole-preset preview, which the
#       defect does not touch, must STAY GREEN (PresetPreviewUiTest)
#   4a  item 4 [recorded, unit 7 injection 7a, after the rework]: a raw apply
#       that fails to parse resets the configuration to the release's starting
#       set (ExpertRawEditUiTest)
#   4b  item 4 [NEW]: a raw apply that parses is adopted at once, without the
#       confirmation step
#   4v  item 4, VERSION-BLIND [recorded, unit 7 agent's report]: the Expert
#       draft is parsed as the FIRST offered release whatever release is
#       selected, so 2026.02.2 accepts the ^ residue; the 2026.03.0 test must
#       STAY GREEN
#   5a  item 5 [recorded, unit 6]: a locked output's check box is left enabled
#       in the view.  Unit 6 found that the session refuses the edit anyway, so
#       the value stays at 1 -- the red is the test's own "disabled for change"
#       (WorkflowOutputsLockedUiTest)
#   5b  item 5 [NEW]: the lock's reason is never shown on screen
#   6a  item 6 [recorded, unit 6 brief]: activating a validation-summary entry
#       no longer moves the focus to its field (CrossParameterValidationUiTest)
#   6b  item 6 [recorded, unit 6 injection 6e]: the parameters' readiness text
#       forced to "do not block"; graded on CrossParameterValidationUiTest AND
#       on MigrationReviewBlocksRunUiTest, whose unresolved migration entry
#       (decision P7-3) must be named as blocking in the same text
#   7a  item 7 [recorded, unit 6 injection 6d, after the rework]: a parameter
#       control's own accessible name removed (FieldControl's input), so only
#       Phase 02's generated fallback name is left -- which the tests must call
#       "only the generated fallback name", not a name
#       (ParameterControlsAccessibilityUiTest, AccessibleNameEnumerationUiTest)
#   7b  item 7 [NEW]: the validation state left out of a parameter control's
#       accessible help, so a screen reader is not told what the screen shows
#   7c  item 7 [NEW, unit 10]: a static-modification table row's mass field
#       loses its own accessible name, so only Phase 02's generated fallback
#       is left -- the table's cells are parameter controls like any other
#       (ParameterControlsAccessibilityUiTest)
#   7v  item 7, VERSION-BLIND [NEW]: a field's choices are the CURATED
#       definition's, not the selected release's, so 2026.03.0's
#       index_search_type loses its -1 "Not set" choice; the 2026.02.2 test of
#       the same combo must STAY GREEN
#   8a  item 8 [recorded, unit 7 injection 7c]: alias matching removed from the
#       parameter search -- no alias matches at all (ParameterSearchUiTest)
#   8b  item 8 [NEW]: activating a search result no longer moves the focus to
#       its field
#   8v  item 8, VERSION-BLIND [recorded, unit 5 injection 5c]: a field's help is
#       the curated definition's instead of the selected release's, so search by
#       help text searches the wrong release's words.  Graded on the
#       VIEW-MODEL test ParameterSearchViewModelTest#releaseHelp in
#       cometgui-ui (unit 8's grading, kept) AND, since unit 9, on the GUI
#       test ParameterSearchUiTest through the launched application: its
#       2026.03.0 method (a query only 2026.03.0's help says) goes red, and
#       its 2026.02.2 method -- the release whose help the curated text is --
#       must STAY GREEN
#   H   the harness itself: a replacement equal to its anchor, an anchor that
#       matches nothing, an injection that reaches the source but not the
#       bytecode, a green run graded as a red, and a red without the expected
#       diagnostic must each be reported as a HARNESS ERROR or FAILURE, never as
#       a pass; and unit 6's injection -- Run disabled only by the engine's
#       reasons -- made for real twice.  H6: graded on the item-6 test, whose
#       application has no Comet installed, so the engine always has a reason
#       there and the injection is EQUIVALENT: it must be reported as "HARNESS
#       FAILURE -- the check PASSED with the defect present", never as a control
#       that bit.  H7 [NEW, phase 08 unit 7]: graded on RunReadinessUiTest,
#       whose engine is READY (a registered Comet, real files, the pre-run check
#       answering), so the same injection must go RED where a parameter error
#       alone disables Run -- and the decoy-block method, where the engine
#       itself disables Run, must STAY GREEN.  Phase 08 made the injection a
#       real control there; H6 stays to prove the harness still refuses to
#       count an equivalent one.
#
# WHAT IT DOES NOT COVER, said plainly:
#
#   * Native file dialogs (never opened headless) and the gate-1 file's Linux
#     path: unit 6's sign-off records both as unverified, and nothing here
#     changes that.
#   * The R-PARAM-06 warning on "read as the selected release" is tested at
#     view-model level only (unit 7's named gap); no control here touches it.
#   * Stage-dependent unlocking of a workflow output (deferred to Phases 11 and
#     12): in this phase every dependent stage is enabled, so item 5's controls
#     prove the lock and its reason, not the unlocking.
#
# WHERE IT WORKS.  Never in the working tree.  It extracts `git archive HEAD`
# into _build/paramui-gate-sandbox and damages that.  `git archive HEAD` is the
# COMMITTED tree: uncommitted changes under cometgui-*/src, scripts/ or a POM
# are reported loudly, because the run then proves HEAD's gates and not the
# tree's.  tools/ is gitignored and absent from the archive, and the headless
# JavaFX tests resolve the project-local font stack through the SANDBOX's own
# maven.multiModuleProjectDirectory, so tools/ is symlinked (Phase 02 handoff,
# Surprise 7): without it every control would fail with "the project-local font
# stack is missing", which is correct behaviour and a false positive for every
# gate here.  Nothing here writes to tools/.
#
# TWO MODULES, BUILT AGAINST THEIR OWN UPSTREAM.  Every injection is in
# cometgui-ui; every graded test is in cometgui-app (decision P7-10), except
# control 8v's view-model test in cometgui-ui.  Their upstream modules (read
# from the sandbox's POMs: everything cometgui-app depends on, transitively,
# except cometgui-ui) are built ONCE, from the sandbox, and installed into a
# PRIVATE overlay repository, _build/paramui-gate-m2, whose every other entry
# is a symlink into _build/m2repo.  Every Maven run then builds cometgui-ui and
# cometgui-app alone (`-pl cometgui-ui,cometgui-app`, no -am) against HEAD's
# upstream jars -- not the shared repository's older ones, which this script
# never writes -- so a control costs two module compiles and its own test
# classes.  cometgui-app takes cometgui-ui from the reactor, so the damaged
# classes are the ones the launched application runs; the overlay holds no
# cometgui-ui jar that could stand in for them, and that is checked.
#
# EVERY INJECTION IS PROVED TO HAVE LANDED, IN THE SOURCE AND IN THE BYTECODE.
#   * the anchor must match EXACTLY ONCE, or the run stops as a harness error;
#   * the sandbox file must differ from its pristine copy;
#   * after the dirty run, the compiled form of the damaged file -- Foo.class
#     and every Foo$*.class -- must DIFFER from the clean baseline; an edit that
#     reached the source but not the bytecode tested the clean code, and is a
#     harness error;
#   * and every OTHER file under both modules' target/classes must be
#     byte-identical to the baseline, so the red is attributable to this one
#     injection and to nothing a previous control left behind.
#
# ONE FILE IS COMPARED WITH ONE LINE REMOVED, AND ONLY ONE.  cometgui-app's
# build-identity.properties is Maven-filtered (Phase 07 unit 6) and carries
# ${maven.build.timestamp}, which Maven rewrites on EVERY run.  Its digest is
# taken with the one line `cometgui.buildTimestamp=...` removed -- and the line
# is required to be there, so that the normalisation cannot quietly hide
# anything else.  Every other file is compared byte for byte.
#
# AND EVERY RESTORATION IS PROVED IN THE BYTECODE.  A restored file is copied
# back, compared and TOUCHED (a copy keeping the snapshot's mtime could leave
# Maven running the injected class), and the next run's digest comparison --
# ending with the final clean run's -- proves the compiled modules are the
# baseline's again.
#
# WHY THE CLEAN RE-RUN IS BATCHED.  Each dirty run proves every class but the
# injected one is byte-identical to the baseline; ONE final clean run, over
# every test class the dirty runs used, must pass with every class executed AND
# leave both compiled modules byte-identical to the baseline.  Identical
# bytecode under identical tests is the baseline's own green, proved rather
# than assumed -- and it costs one run instead of eighteen.
#
# WHOLE CLASSES, AND THEN THE METHODS.  The GUI tests are ordered
# (@TestMethodOrder) and later methods start from the state earlier ones left,
# so a control selects WHOLE test classes (none has @Nested tests; a bare class
# selector naming one that has is refused), and then reads surefire's XML to
# require a named method red -- and, for the version-blind controls, the
# method of the release the defect does not touch GREEN.
#
# WHAT IT SWITCHES OFF, AND WHY THAT IS NOT A WEAKENING.  Every sandbox Maven
# run passes -Dspotless.check.skip -Dcheckstyle.skip -Dspotbugs.skip
# -Djacoco.skip.  Those are Phase 01's gates with their own harnesses; an
# injection like `if (false)` would otherwise be stopped by Checkstyle before a
# test ran, and the control would "fail" for the wrong reason.  (-Djacoco.skip
# also skips cometgui-ui's view-model coverage rule, which is not what any
# control here grades.)  Nothing this script tests is skipped, no test is
# excluded, and every run is checked to have EXECUTED what it named.
#
# WHAT IT NEEDS.  A built tree: tools/ (JDK, Maven, Monocle, the font stack)
# and a populated _build/m2repo.  Offline; writes only under _build/.
#
# WHAT IT COSTS.  Measured and printed per control; the total is on the SUMMARY
# line.  The GUI test classes dominate: each control runs only the classes it
# grades, but those launch the application, and the baseline and the final
# clean run each run all thirteen graded classes once.
#
# EXIT STATUS
#   0  every control bit
#   1  at least one control failed -- a gate did not bite, a gate failed for
#      the wrong reason, a test that had to stay green went red, or the clean
#      tree did not pass again
#   2  misuse (unknown option, unknown control)
#   3  the environment is not ready (no tools/, no _build/m2repo, no font stack)
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
readonly SANDBOX="${ROOT}/_build/paramui-gate-sandbox"
readonly PRISTINE="${ROOT}/_build/paramui-gate-pristine"
readonly OVERLAY="${ROOT}/_build/paramui-gate-m2"
readonly M2REPO="${ROOT}/_build/m2repo"
readonly LOGS="${ROOT}/_build/paramui-gate-logs"
readonly FONTSTACK="tools/fontstack-bookworm-20260829/root"

# The two modules every control rebuilds.  The injections are in UI; the GUI
# gate tests are in APP.
readonly UI="cometgui-ui"
readonly APP="cometgui-app"
readonly -a MODULES=("${UI}" "${APP}")

# The one compiled file Maven rewrites on every run (see the header), and the
# one line of it that changes.
readonly IDENTITY="${APP}/org/cometgui/app/config/build-identity.properties"
readonly IDENTITY_LINE='^cometgui\.buildTimestamp='

# The production files the controls damage.
readonly J="${UI}/src/main/java/org/cometgui/ui"
readonly DECOY_CONTROL="${J}/controls/params/DecoySourceControl.java"
readonly VARMOD_EDITOR="${J}/controls/params/VariableModEditor.java"
# The preset preview's actions moved from PresetControl into PresetReviewPane
# in unit 10, unchanged, when the fragment instrument choice was given a
# preview of its own; controls 3a and 3b anchor on the same lines there.
readonly PRESET_CONTROL="${J}/controls/params/PresetReviewPane.java"
readonly PRESET_PREVIEW="${J}/viewmodel/params/PresetPreview.java"
readonly ESSENTIALS_VIEW="${J}/view/params/EssentialsView.java"
readonly STATIC_MOD_TABLE="${J}/controls/params/StaticModTable.java"
readonly EXPERT_VM="${J}/viewmodel/params/ExpertViewModel.java"
readonly FIELD_CONTROL="${J}/controls/params/FieldControl.java"
readonly SUMMARY_PANE="${J}/controls/params/ValidationSummaryPane.java"
readonly RUN_CONTROL="${J}/controls/params/RunControl.java"
readonly FIELD_VM="${J}/viewmodel/params/FieldViewModel.java"
readonly SEARCH_VM="${J}/viewmodel/params/ParameterSearchViewModel.java"
readonly EDITOR_VIEW="${J}/view/params/CometParametersView.java"
readonly FILES_VM="${J}/viewmodel/params/ParameterFilesViewModel.java"

# The graded test classes, by the gate item each proves.
readonly T1="EssentialsTrypticSearchUiTest"
readonly T2="VariableModificationEditorUiTest"
readonly T3="PresetPreviewUiTest"
readonly T3F="FragmentInstrumentPreviewUiTest"
readonly TSM="StaticModificationTableUiTest"
readonly T4="ExpertRawEditUiTest"
readonly T5="WorkflowOutputsLockedUiTest"
readonly T6="CrossParameterValidationUiTest"
readonly T6M="MigrationReviewBlocksRunUiTest"
# Phase 08 unit 7: Run readiness with the workflow engine's half READY, so the
# parameters' half can be seen alone (control H7).
readonly T6R="RunReadinessUiTest"
readonly T7="ParameterControlsAccessibilityUiTest"
readonly T7N="AccessibleNameEnumerationUiTest"
readonly T8="ParameterSearchUiTest"
# Control 8v's view-model test in cometgui-ui.  The class has @Nested classes,
# so it is named by method (a bare class selector would drop them silently).
readonly T8V="ParameterSearchViewModelTest#releaseHelp"

# Units 2 and 3 of Phase 01 have their own harnesses; see the header.
readonly -a QUIET=(
    "-Dspotless.check.skip=true"
    "-Dcheckstyle.skip=true"
    "-Dspotbugs.skip=true"
    "-Djacoco.skip=true"
)

# Every control id, in the order they run.
readonly -a ALL_CONTROLS=(1 1b 1c 2a 2v 3a 3b 3c 3d 4a 4b 4v 5a 5b 6a 6b 7a 7b 7c 7v 8a 8b 8v H)

PASSED=0
FAILED=0
FAILURES=()
declare -a TIMINGS=()
# Every selector a dirty run used, for the final clean run.
declare -a USED_SELECTORS=()
# Every file a control damaged and restored, for the report.
declare -a RESTORED=()

# ----------------------------------------------------------------- plumbing --

usage() {
    cat <<USAGE
${SCRIPT_NAME} -- prove the eight PHASE-07 exit gate items fail on the
defects they exist to catch.

Usage:
  bash scripts/${SCRIPT_NAME}               every control
  bash scripts/${SCRIPT_NAME} --self-test   control H only: the harness must
                                            refuse to report a pass for an
                                            injection that did not land, and
                                            for an equivalent injection
  bash scripts/${SCRIPT_NAME} --only IDS    the named controls (comma-separated,
                                            from: ${ALL_CONTROLS[*]}) plus the
                                            baseline and the final clean run
  bash scripts/${SCRIPT_NAME} -h|--help

It needs a built tree: tools/ (JDK, Maven, Monocle, the font stack) and a
populated _build/m2repo.  It runs Maven offline, damages only a git-archive
sandbox under _build/, and writes only under _build/.

Exit status: 0 every control bit; 1 a control failed; 2 misuse; 3 the
environment is not ready; 4 a harness error (an injection that reached
nothing, or a restoration that did not reach the bytecode).
USAGE
}

# die MESSAGE [EXIT CODE].  Only $1 is the message.
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

# While control H provokes a failure on purpose, it is printed as REFUSED so
# that nobody reading the log mistakes the expected outcome for a real one,
# and any pass the provoked grading prints on the way is NOT counted: the
# SUMMARY count is what verify-all-gates.sh holds to a floor.
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

# ---------------------------------------------------------- upstream modules --
#
# upstream_modules -- every cometgui module cometgui-app depends on,
# transitively and at any scope, read from the sandbox's own POMs, except
# cometgui-ui, which every control rebuilds.  A module that itself depends on
# cometgui-ui would carry an undamaged copy of it into the overlay; there is
# none today, and if one appears this is a harness error rather than a quiet
# hole in every control.
upstream_modules() {
    python3 - "${SANDBOX}" "${APP}" "${UI}" <<'PYTHON'
import re
import sys
from pathlib import Path

root, app, ui = Path(sys.argv[1]), sys.argv[2], sys.argv[3]
deps = {}
for pom in root.glob("cometgui-*/pom.xml"):
    module = pom.parent.name
    text = re.sub(r"<parent>.*?</parent>", "", pom.read_text(encoding="utf-8"), flags=re.S)
    deps[module] = sorted(set(re.findall(r"<artifactId>(cometgui-[a-z-]+)</artifactId>", text)) - {module})
for wanted in (app, ui):
    if wanted not in deps:
        sys.stderr.write("no module %r in the sandbox\n" % wanted)
        raise SystemExit(1)
if ui not in deps[app]:
    sys.stderr.write("%s does not depend on %s; the controls would test nothing\n" % (app, ui))
    raise SystemExit(1)
seen, todo = set(), list(deps[app])
while todo:
    module = todo.pop()
    if module in seen:
        continue
    seen.add(module)
    todo.extend(deps.get(module, []))
seen.discard(ui)
for module in sorted(seen):
    if ui in deps.get(module, []):
        sys.stderr.write("%s depends on %s, so the overlay would carry an undamaged copy\n" % (module, ui))
        raise SystemExit(1)
print(",".join(sorted(seen)))
PYTHON
}

# build_overlay -- the private repository: every top-level entry of
# _build/m2repo symlinked, except org/cometgui, which is a real directory that
# receives HEAD's upstream jars built from this sandbox.  The shared
# repository's org/cometgui is digested before and after, and must not change.
COMETGUI_REPO_DIGEST=""
cometgui_repo_digest() {
    ( cd -- "${M2REPO}/org/cometgui" && find . -type f -name '*.jar' -print0 | sort -z \
        | xargs -0 -r sha256sum | sha256sum | cut -d' ' -f1 )
}

build_overlay() {
    rm -rf -- "${OVERLAY}"
    mkdir -p -- "${OVERLAY}/org/cometgui"
    local entry
    for entry in "${M2REPO}"/*; do
        [ "$(basename -- "${entry}")" = "org" ] && continue
        ln -s -- "${entry}" "${OVERLAY}/"
    done
    for entry in "${M2REPO}"/org/*; do
        [ "$(basename -- "${entry}")" = "cometgui" ] && continue
        ln -s -- "${entry}" "${OVERLAY}/org/"
    done
    COMETGUI_REPO_DIGEST="$(cometgui_repo_digest)"

    local upstream log="${LOGS}/upstream-install.log" rc=0 marker
    upstream="$(upstream_modules)" \
        || harness_error "the upstream modules of ${APP} cannot be read from the sandbox's POMs, or one of them depends on ${UI}."
    [ -n "${upstream}" ] || harness_error "${APP} has no upstream module at all; the POM read is wrong."
    marker="${LOGS}/upstream-install.marker"
    touch -- "${marker}"
    printf '   upstream of %s and %s: %s\n' "${UI}" "${APP}" "${upstream}"
    printf '   mvn -o -pl %s -am install -DskipTests (into %s)\n' "${upstream}" "$(rel "${OVERLAY}")"
    ( cd -- "${SANDBOX}" \
        && mvn -B -o -Dmaven.repo.local="${OVERLAY}" "${QUIET[@]}" \
            -pl "${upstream}" -am install -DskipTests ) >"${log}" 2>&1 || rc=$?
    printf '\n=== MVN EXIT STATUS: %d ===\n' "${rc}" >>"${log}"
    [ "${rc}" -eq 0 ] \
        || harness_error "the sandbox's upstream modules did not build and install into the overlay (exit ${rc}, log: $(rel "${log}"))."
    # Proof the overlay holds THIS sandbox's jars: one per upstream module,
    # written after the marker, inside the overlay's own real directory -- and
    # no jar of the two modules the controls rebuild.
    local module jar
    for module in ${upstream//,/ }; do
        jar="${OVERLAY}/org/cometgui/${module}/0.1.0-SNAPSHOT/${module}-0.1.0-SNAPSHOT.jar"
        [ -s "${jar}" ] && [ "${jar}" -nt "${marker}" ] && [ ! -L "${jar}" ] \
            || harness_error "the overlay has no freshly built ${module} jar at $(rel "${jar}"); ${UI} would be built against an older one."
    done
    for module in "${MODULES[@]}"; do
        [ ! -e "${OVERLAY}/org/cometgui/${module}" ] \
            || harness_error "the overlay holds ${module}, so a run could take an undamaged ${module} from it instead of the reactor."
    done
    [ ! -L "${OVERLAY}/org/cometgui" ] \
        || harness_error "the overlay's org/cometgui is a symlink into the shared repository."
    record_pass "upstream ${upstream} built from the sandbox and installed into the private overlay only"
}

# ------------------------------------------------------- bytecode evidence --
#
# class_tree -- "sha256  <module>/<path>" for every file under both modules'
# target/classes: compiled classes AND the resources copied there, sorted.  The
# one Maven-filtered identity file is digested without its timestamp line (see
# the header), and that line must be there.
class_tree() {
    local module dir
    for module in "${MODULES[@]}"; do
        dir="${SANDBOX}/${module}/target/classes"
        [ -d "${dir}" ] || continue
        ( cd -- "${dir}" && find . -type f -print0 | sort -z | xargs -0 -r sha256sum ) \
            | sed "s#  \./#  ${module}/#"
    done | while read -r digest path; do
        if [ "${path}" = "${IDENTITY}" ]; then
            grep -qE -- "${IDENTITY_LINE}" "${SANDBOX}/${APP}/target/classes/${IDENTITY#"${APP}/"}" \
                || { printf 'NO-TIMESTAMP-LINE  %s\n' "${path}"; continue; }
            digest="$(grep -vE -- "${IDENTITY_LINE}" "${SANDBOX}/${APP}/target/classes/${IDENTITY#"${APP}/"}" \
                | sha256sum | cut -d' ' -f1)"
        fi
        printf '%s  %s\n' "${digest}" "${path}"
    done
}

readonly BASELINE_TREE="${LOGS}/baseline-classes.sha256"

record_baseline_tree() {
    class_tree >"${BASELINE_TREE}"
    local module count
    for module in "${MODULES[@]}"; do
        count="$(grep -c "  ${module}/.*\.class\$" "${BASELINE_TREE}" || true)"
        [ "${count}" -gt 0 ] \
            || harness_error "the baseline run compiled no class in ${module}, so no clean bytecode can be compared against."
    done
    grep -q "  ${IDENTITY}\$" "${BASELINE_TREE}" \
        || harness_error "the baseline's ${APP}/target/classes holds no build-identity.properties; the one normalised file is not where this script says it is."
    if grep -q '^NO-TIMESTAMP-LINE' "${BASELINE_TREE}"; then
        harness_error "build-identity.properties has no cometgui.buildTimestamp line, so this script's one normalisation is stale. Re-establish it; do not delete the check."
    fi
    record_pass "baseline: both compiled modules are digested ($(grep -c "  ${UI}/.*\.class\$" "${BASELINE_TREE}") ${UI} classes, $(grep -c "  ${APP}/.*\.class\$" "${BASELINE_TREE}") ${APP} classes, $(grep -vc '\.class$' "${BASELINE_TREE}") resources)"
}

# compiled_prefix SOURCE -- what the compiled form of one main source is called
# in class_tree: <module>/org/.../Foo (Foo.class, Foo$*.class).
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

# compare_tree LOG [INJECTED SOURCE] -- the compiled modules after a run,
# against the baseline.  With an injected source: its compiled form MUST
# differ, and everything else MUST be identical.  Without one: everything must
# be identical.  Anything else is a harness error.
compare_tree() {
    local log="$1" injected="${2:-}"
    [ -s "${BASELINE_TREE}" ] || harness_error "no baseline digest of the compiled modules was recorded."
    local now prefix=""
    now="$(class_tree)"
    if printf '%s\n' "${now}" | grep -q '^NO-TIMESTAMP-LINE'; then
        harness_error "$(rel "${log}"): build-identity.properties lost its timestamp line during the run."
    fi
    [ -n "${injected}" ] && prefix="$(compiled_prefix "${injected}")"

    local -A before=() after=() all=()
    local digest path
    while read -r digest path; do
        before["${path}"]="${digest}"
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
        harness_error "$(rel "${log}"): ${#stray[@]} file(s) under target/classes differ from the clean baseline although no control damaged them: ${stray[*]:0:5}. A previous restoration did not reach the bytecode, or the build is not reproducible, so this run's result cannot be attributed."
    fi
    if [ -n "${prefix}" ]; then
        [ "${count}" -gt 0 ] \
            || harness_error "$(rel "${log}"): target/classes has no compiled form of ${injected} at all."
        [ "${changed}" -gt 0 ] \
            || harness_error "$(rel "${log}"): the compiled form of ${injected} is BYTE-IDENTICAL to the clean baseline after the dirty run. The injection reached the source and not the bytecode, so the run tested the clean code and its result is not evidence of anything."
        printf '   bytecode: %d of %d compiled file(s) of the damaged source changed; %d other file(s) identical to the baseline\n' \
            "${changed}" "${count}" "${others}"
    else
        printf '   bytecode: all %d compiled file(s) identical to the clean baseline\n' "${others}"
    fi
}

# ------------------------------------------------------------ running tests --

# run_mvn LOG SELECTORS -- the one test command shape in this script: the two
# modules alone, against the overlay.  The exit status is written INTO the log.
run_mvn() {
    local log="$1" selectors="$2" rc=0 module
    for module in "${MODULES[@]}"; do
        rm -rf -- "${SANDBOX}/${module}/target/surefire-reports"
    done
    ( cd -- "${SANDBOX}" \
        && mvn -B -o -Dmaven.repo.local="${OVERLAY}" "${QUIET[@]}" \
            -pl "${UI},${APP}" test \
            -Dtest="${selectors}" -Dsurefire.failIfNoSpecifiedTests=false ) \
        >"${log}" 2>&1 || rc=$?
    printf '\n=== MVN EXIT STATUS: %d ===\n' "${rc}" >>"${log}"
    return "${rc}"
}

gate_command() {
    printf 'mvn -o -pl %s,%s test -Dtest=%s' "${UI}" "${APP}" "$1"
}

# The outer class of a selector: Foo and Foo#bar are both Foo.
selector_class() {
    local selector="${1%%#*}"
    printf '%s' "${selector%%\$*}"
}

# test_source_of CLASS -- the one test source of that name in either module.
test_source_of() {
    local class="$1" found
    found="$(cd -- "${SANDBOX}" && find "${UI}/src/test/java" "${APP}/src/test/java" \
        -name "${class}.java" -type f | head -2)"
    [ -n "${found}" ] \
        || harness_error "no test class ${class}.java exists in the sandbox. A control naming a test that does not exist tests nothing."
    [ "$(printf '%s\n' "${found}" | wc -l)" -eq 1 ] \
        || harness_error "the test class name ${class} is ambiguous in the sandbox: ${found}"
    printf '%s' "${found}"
}

report_of() {
    local class="$1"
    find "${SANDBOX}/${UI}/target/surefire-reports" "${SANDBOX}/${APP}/target/surefire-reports" \
        -maxdepth 1 -name "TEST-*.${class}.xml" 2>/dev/null | head -1 || true
}

# testcase_outcome CLASS METHOD -- passed, failed, or absent, from the XML
# report of the class.  A parameterised method passes only if every one of its
# invocations does.
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
        if name == method or name.startswith(method + "(") or name.startswith(method + "{"):
            failed = case.find("failure") is not None or case.find("error") is not None
            if failed:
                outcome = "failed"
            elif outcome == "absent":
                outcome = "passed"
print(outcome)
PYTHON
}

# verify_classes_ran LOG SELECTORS -- every class a selector names produced a
# report with at least one test, a bare class selector names no class with
# @Nested tests, and every method named ran.  Otherwise a harness error: the
# vacuous pass -Dsurefire.failIfNoSpecifiedTests=false offers for free.
verify_classes_ran() {
    local log="$1" selectors="$2"
    local -a wanted=() methods=()
    local selector class report count source method outcome
    IFS=',' read -r -a wanted <<<"${selectors}"
    for selector in "${wanted[@]}"; do
        class="$(selector_class "${selector}")"
        source="$(test_source_of "${class}")"
        report="$(report_of "${class}")"
        if [ -z "${report}" ] || [ ! -s "${report}" ]; then
            harness_error "${selector} produced no surefire report for $(rel "${log}"). The command named a test that surefire did not execute, so this run tested nothing."
        fi
        count="$(sed -n 's/.*<testsuite [^>]*tests="\([0-9][0-9]*\)".*/\1/p' "${report}" | head -1)"
        [ -n "${count}" ] \
            || harness_error "the surefire report for ${class} carries no tests= count; this harness cannot read it and would pass vacuously."
        [ "${count}" -ge 1 ] \
            || harness_error "${selector} executed ${count} tests in $(rel "${log}"). A selection that runs no test cannot prove or disprove a gate."
        if [ "${selector}" = "${selector%%#*}" ]; then
            if grep -q '@Nested' "${SANDBOX}/${source}"; then
                harness_error "the selector ${selector} names a class with @Nested tests, and surefire drops those from a class selector in a list without a word. Name the method."
            fi
            continue
        fi
        IFS='+' read -r -a methods <<<"${selector#*#}"
        for method in "${methods[@]}"; do
            outcome="$(testcase_outcome "${class}" "${method}")"
            [ "${outcome}" != "absent" ] \
                || harness_error "${class}#${method} did not run in $(rel "${log}"); the selector ${selector} selects less than it names."
        done
    done
}

# assert_testcase LABEL WANT CLASS METHOD -- one method's outcome, required.
assert_testcase() {
    local label="$1" want="$2" class="$3" method="$4" got
    got="$(testcase_outcome "${class}" "${method}")"
    [ "${got}" != "absent" ] \
        || harness_error "(${label}) ${class}.${method} did not run, so its outcome cannot be read."
    if [ "${got}" = "${want}" ]; then
        record_pass "${label}: ${class}.${method} ${got}"
    else
        record_fail "${label}: ${class}.${method} ${got}, and this control requires it ${want}"
    fi
}

# TWO WAYS TO STATE AN EXPECTED DIAGNOSTIC: a literal where the text carries
# nothing that varies, a regular expression where it does -- and then the
# expression must still name the thing.
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
            "$(grep -m1 -E '^\[ERROR\] +[A-Za-z]' "${log}" | cut -c1-200 || true)"
        return
    fi
    record_pass "${label}: rejected, exit ${rc}"
    printf '         %s\n' "$(log_first_match "${log}" "${expected}" "${mode}" | sed 's/^ *//' | cut -c1-240)"
}

assert_log_contains() { graded_log fixed "$@"; }
assert_log_matches() { graded_log regex "$@"; }

graded_log() {
    local mode="$1" label="$2" log="$3" expected="$4"
    if log_has "${log}" "${expected}" "${mode}"; then
        record_pass "${label}"
        printf '         %s\n' "$(log_first_match "${log}" "${expected}" "${mode}" | sed 's/^ *//' | cut -c1-240)"
    else
        record_fail "${label}: nothing in $(rel "${log}") matches '${expected}'"
    fi
}

# dirty_run LABEL SOURCE SELECTORS LOG -- runs the narrow check on the damaged
# sandbox and proves what it ran.  Leaves the exit status in DIRTY_RC.
DIRTY_RC=0
dirty_run() {
    local label="$1" source="$2" selectors="$3" log="$4"
    DIRTY_RC=0
    run_mvn "${log}" "${selectors}" || DIRTY_RC=$?
    if grep -qE 'COMPILATION ERROR|Compilation failure' "${log}"; then
        harness_error "(${label}) the damaged sandbox does not compile -- see $(rel "${log}"). A red that never ran a test is not a result."
    fi
    if grep -qE 'NoClassDefFoundError|ClassNotFoundException: org\.cometgui' "${log}"; then
        harness_error "(${label}) $(rel "${log}") carries NoClassDefFoundError/ClassNotFoundException for project code: the red is the harness's, not the gate's."
    fi
    if grep -qF 'the project-local font stack is missing' "${log}"; then
        harness_error "(${label}) $(rel "${log}") says the project-local font stack is missing: every GUI test fails for that reason, not the injected one."
    fi
    compare_tree "${log}" "${source}"
    verify_classes_ran "${log}" "${selectors}"
    USED_SELECTORS+=("${selectors}")
}

# ------------------------------------------------- injection and its guards --

save_pristine() {
    local file="$1"
    mkdir -p -- "$(dirname -- "${PRISTINE}/${file}")"
    cp -p -- "${SANDBOX}/${file}" "${PRISTINE}/${file}"
}

# replace_once LABEL FILE OLD NEW -- the anchor must match EXACTLY ONCE.
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
with open(path, "w", encoding="utf-8") as handle:
    handle.write(text.replace(old, new))
PYTHON
    if [ "${rc}" -ne 0 ]; then
        harness_error "(${label}) the injection anchor is gone from ${file}. The source moved under this control, which would inject nothing and report green."
    fi
}

# assert_modified LABEL FILE -- and it really differs from the pristine copy.
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

# restore_pristine FILE -- copied back, compared, and TOUCHED.
restore_pristine() {
    local file="$1"
    [ -e "${PRISTINE}/${file}" ] || harness_error "no pristine copy of ${file} to restore from."
    cp -- "${PRISTINE}/${file}" "${SANDBOX}/${file}"
    cmp -s "${SANDBOX}/${file}" "${PRISTINE}/${file}" || harness_error "could not restore ${file} in the sandbox."
    touch -- "${SANDBOX}/${file}"
    RESTORED+=("${file}")
    printf '   restored %s (byte-identical to the pristine copy, and touched)\n' "${file}"
}

# inject_and_run LABEL SOURCE SELECTORS MODE EXPECTED OLD NEW -- the common
# shape: damage one production file once, run the classes that grade it,
# require the red with its own words.  The caller then makes any further
# assertions on DIRTY_LOG and restores the file.
DIRTY_LOG=""
inject_and_run() {
    local label="$1" source="$2" selectors="$3" mode="$4" expected="$5" old="$6" new="$7"
    save_pristine "${source}"
    replace_once "${label}" "${source}" "${old}" "${new}"
    assert_modified "${label}" "${source}"
    DIRTY_LOG="${LOGS}/${CONTROL_ID}-dirty.log"
    printf '   %s\n' "$(gate_command "${selectors}")"
    dirty_run "${label}" "${source}" "${selectors}" "${DIRTY_LOG}"
    grade_red "${mode}" "${label}" "${DIRTY_RC}" "${DIRTY_LOG}" "${expected}"
}

# ------------------------------------------------------------- the sandbox --
build_sandbox() {
    rm -rf -- "${SANDBOX}" "${PRISTINE}"
    mkdir -p -- "${SANDBOX}" "${PRISTINE}"
    ( cd -- "${ROOT}" && git archive HEAD ) | tar -x -C "${SANDBOX}" \
        || harness_error "git archive HEAD could not be extracted into the sandbox."
    # tools/ is gitignored, so it is NOT in the archive, and the headless
    # JavaFX tests resolve the font stack through the SANDBOX's own
    # maven.multiModuleProjectDirectory.  Without this link every control fails
    # with "the project-local font stack is missing" -- correct behaviour, and
    # a false positive for every gate here.
    ln -s -- "${ROOT}/tools" "${SANDBOX}/tools"
    [ -f "${SANDBOX}/tools/env.sh" ] && [ -d "${SANDBOX}/${FONTSTACK}" ] \
        || harness_error "the sandbox's tools/ symlink does not resolve to the toolchain and the font stack. Every GUI control would fail for the wrong reason."
    local head dirty
    head="$(cd -- "${ROOT}" && git rev-parse --short HEAD)"
    echo "Sandbox: $(rel "${SANDBOX}") (git archive ${head}, $(find "${SANDBOX}/${UI}/src/main" -name '*.java' -type f | wc -l) main java files in ${UI})"
    dirty="$(cd -- "${ROOT}" && git status --porcelain -- 'cometgui-*/src' scripts pom.xml 'cometgui-*/pom.xml' | head -20)"
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
readonly SEL_1="${T1}"
readonly SEL_1C="${T1},${TSM}"
readonly SEL_2="${T2}"
readonly SEL_3="${T3}"
readonly SEL_3C="${T3F}"
readonly SEL_3D="${T3F},${T3}"
readonly SEL_4="${T4}"
readonly SEL_5="${T5}"
readonly SEL_6A="${T6}"
readonly SEL_6B="${T6},${T6M}"
readonly SEL_7A="${T7},${T7N}"
readonly SEL_7="${T7}"
readonly SEL_8="${T8}"
readonly SEL_8V="${T8V}"
# Control 8v's second, GUI run.  A run whose cometgui-ui tests fail stops the
# reactor before cometgui-app, so the view-model test and the GUI test cannot
# share one run: 8v makes two, on the same injection.
readonly SEL_8VG="${T8}"
# Control H: H3 needs a cheap run (its bytecode check refuses it whatever the
# tests say); H6 must run the class unit 6's equivalent injection was graded on;
# H7 the class where, since phase 08, the same injection is not equivalent.
readonly SEL_H="${T8V},${T6},${T6R}"

control_selectors() {
    case "$1" in
        1|1b) printf '%s' "${SEL_1}" ;;
        1c) printf '%s' "${SEL_1C}" ;;
        2a|2v) printf '%s' "${SEL_2}" ;;
        3a|3b) printf '%s' "${SEL_3}" ;;
        3c) printf '%s' "${SEL_3C}" ;;
        3d) printf '%s' "${SEL_3D}" ;;
        4a|4b|4v) printf '%s' "${SEL_4}" ;;
        5a|5b) printf '%s' "${SEL_5}" ;;
        6a) printf '%s' "${SEL_6A}" ;;
        6b) printf '%s' "${SEL_6B}" ;;
        7a) printf '%s' "${SEL_7A}" ;;
        7b|7c|7v) printf '%s' "${SEL_7}" ;;
        8a|8b) printf '%s' "${SEL_8}" ;;
        8v) printf '%s,%s' "${SEL_8V}" "${SEL_8VG}" ;;
        H) printf '%s' "${SEL_H}" ;;
        *) die "no control '$1'. Controls: ${ALL_CONTROLS[*]}" 2 ;;
    esac
}

BASELINE_SELECTORS=""
plan_baseline() {
    local id
    local -a selectors=()
    for id in "$@"; do
        selectors+=("$(control_selectors "${id}")")
    done
    BASELINE_SELECTORS="$(printf '%s\n' "${selectors[@]}" | tr ',' '\n' | awk 'NF && !seen[$0]++' | paste -sd, -)"
}

# --------------------------------------------------------------- control 0 --

control_baseline() {
    begin_control "0" "baseline: the overlay is built and the undamaged sandbox passes every graded class"
    build_overlay
    local log="${LOGS}/0-baseline.log" rc=0
    printf '   %s\n' "$(gate_command "${BASELINE_SELECTORS}")"
    run_mvn "${log}" "${BASELINE_SELECTORS}" || rc=$?
    if [ "${rc}" -ne 0 ]; then
        record_fail "baseline: the undamaged sandbox does NOT pass the graded classes (exit ${rc}, log: $(rel "${log}")). Nothing below could be attributed to an injection."
        printf '         %s\n' "$(grep -m3 -E '^\[ERROR\] +[A-Za-z]' "${log}" | cut -c1-200 || true)"
        end_control
        return 1
    fi
    verify_classes_ran "${log}" "${BASELINE_SELECTORS}"
    record_pass "baseline: exit 0, every graded class executed ($(printf '%s\n' "${BASELINE_SELECTORS}" | tr ',' '\n' | grep -c .) selectors)"
    record_baseline_tree
    end_control
}

# ------------------------------------------------------------- the controls --

control_1() {
    begin_control "1" "item 1 [unit 8 brief]: the Essentials decoy control always chooses decoys-in-the-FASTA"
    # Gate 1: "configures a complete tryptic DDA search using Essentials only".
    # The test chooses Comet's concatenated internal decoys; with the control
    # setting FASTA_CONTAINS_DECOYS whatever is chosen, the choice does not
    # hold, and the test's own choose() step says so before anything is saved:
    # it names the control, what was chosen and what the control shows.
    inject_and_run "decoy choice ignored" "${DECOY_CONTROL}" "${SEL_1}" fixed \
        '#ess-decoy_search after choosing "Concatenated: targets and decoys compete, one result per spectrum" ==> expected: <Concatenated: targets and decoys compete, one result per spectrum> but was: <No internal decoys>' \
        'session.setDecoySource(after.source());' \
        'session.setDecoySource(DecoySource.FASTA_CONTAINS_DECOYS);'
    assert_testcase "the Essentials search is the method that failed" failed \
        "${T1}" essentialsConfiguresATrypticSearch
    restore_pristine "${DECOY_CONTROL}"
    end_control
}

# The copy EssentialsTrypticSearchUiTest writes of the file it saved when the
# file is not the expected one, and the expected file itself.
readonly GATE1_ACTUAL="${APP}/target/gate-1-actual-comet.params"
readonly GATE1_EXPECTED="${APP}/src/test/resources/org/cometgui/app/gui/essentials-tryptic-dda-2026.03.0.params"

# assert_saved_file_differs_only LABEL EXPECTED-LINE-PREFIX ACTUAL-LINE-PREFIX
# -- the saved file the test copied differs from the checked-in expected file
# in exactly one line, and that line is the one the control damaged.  This is
# the harness reading the gate's own evidence, not re-implementing the gate.
assert_saved_file_differs_only() {
    local label="$1" want_old="$2" want_new="$3" out rc=0
    [ -f "${SANDBOX}/${GATE1_ACTUAL}" ] \
        || harness_error "(${label}) the test wrote no copy of the saved file at ${GATE1_ACTUAL}; the comparison it failed on cannot be read."
    out="$(python3 - "${SANDBOX}/${GATE1_EXPECTED}" "${SANDBOX}/${GATE1_ACTUAL}" "${want_old}" "${want_new}" <<'PYTHON'
import sys

expected_path, actual_path, want_old, want_new = sys.argv[1:5]
with open(expected_path, encoding="utf-8") as handle:
    expected = handle.read().split("\n")
with open(actual_path, encoding="utf-8") as handle:
    actual = handle.read().split("\n")
if len(expected) != len(actual):
    print("the saved file has %d lines and the expected file %d" % (len(actual), len(expected)))
    raise SystemExit(1)
differing = [(i + 1, e, a) for i, (e, a) in enumerate(zip(expected, actual)) if e != a]
if len(differing) != 1:
    print("%d lines differ, not one: %r" % (len(differing), differing[:3]))
    raise SystemExit(1)
number, old, new = differing[0]
if not old.startswith(want_old) or not new.startswith(want_new):
    print("line %d differs as %r -> %r, not as %r -> %r" % (number, old, new, want_old, want_new))
    raise SystemExit(1)
print("line %d only: %r -> %r" % (number, old.split("#")[0].strip(), new.split("#")[0].strip()))
PYTHON
)" || rc=$?
    if [ "${rc}" -eq 0 ]; then
        record_pass "${label}"
        printf '         %s\n' "${out}"
    else
        record_fail "${label}: ${out}"
    fi
}

control_1b() {
    begin_control "1b" "item 1 [NEW]: the screen shows the choice, the SAVED FILE does not"
    # The other half of gate 1, and the one its wording names: "the generated
    # parameter file matches an expected canonical file exactly".  Control 1's
    # defect is visible on screen; this one is not.  Every control shows what
    # was chosen, and the save writes the configuration with decoy_search
    # quietly made 0 -- a search Percolator could not rescore.  Only the byte
    # comparison of the saved file can see it.
    rm -f -- "${SANDBOX}/${GATE1_ACTUAL}"
    inject_and_run "the saved file loses the decoy choice" "${FILES_VM}" "${SEL_1}" fixed \
        'the saved file differs from the checked-in essentials-tryptic-dda-2026.03.0.params' \
        '        CometParameters model = session.model();' \
        '        CometParameters model =
                session.model()
                        .withDecoySource(
                                org.cometgui.params.comet.model.DecoySource.FASTA_CONTAINS_DECOYS,
                                org.cometgui.params.comet.model.ValueOrigin.USER);'
    assert_saved_file_differs_only \
        "and the saved file differs from the expected one in the decoy_search line alone" \
        'decoy_search = 1 ' 'decoy_search = 0 '
    restore_pristine "${FILES_VM}"
    end_control
}

control_1c() {
    begin_control "1c" "item 1 [NEW, unit 10]: a static-modification row writes its mass to the next row's parameter"
    # Gate 1's search sets TMT on lysine through the static-modification table
    # (unit 10).  With each row writing to its neighbour's parameter, the table
    # test's lysine row no longer holds what was typed -- named by its own
    # message -- and the gate-1 search saves add_E_glutamic_acid and
    # add_Cterm_protein instead of add_K_lysine and add_Nterm_peptide.
    inject_and_run "row writes the next row's mass" "${STATIC_MOD_TABLE}" "${SEL_1C}" fixed \
        'the lysine row after its mass was typed ==> expected: <[lysine (K), 229.162932, Static modification: lysine (K), Changed from default 0.0000 -- Set by you, No problems.]> but was: <[lysine (K), 0.0000, Static modification: lysine (K), Default -- Comet 2026.03.0 default, No problems.]>' \
        '            table.setMass(table.row(field.name()).orElseThrow(), mass.getText());' \
        '            List<StaticModRow> all = table.rows();
            table.setMass(
                    all.get(all.indexOf(table.row(field.name()).orElseThrow()) + 1),
                    mass.getText());'
    assert_testcase "the table walk is the method that failed" failed "${TSM}" theTable
    assert_log_contains "and the gate-1 search's saved file is not the expected one" \
        "${DIRTY_LOG}" 'the saved file differs from the checked-in essentials-tryptic-dda-2026.03.0.params'
    assert_testcase "the Essentials search is a method that failed" failed \
        "${T1}" essentialsConfiguresATrypticSearch
    restore_pristine "${STATIC_MOD_TABLE}"
    end_control
}

control_2a() {
    begin_control "2a" "item 2 [NEW]: the slot editor's Move up moves the slot down"
    # Gate 2: "adds, edits, reorders and removes a variable modification and
    # asserts the serialised tuple after each step".  The add and edit steps
    # stay right; the reorder step must be the one that fails, by its own name.
    inject_and_run "move up moves down" "${VARMOD_EDITOR}" "${SEL_2}" fixed \
        'after moving slot 2 up' \
        'up.setOnAction(event -> report("Moved " + name + " up.", mods.moveUp(name)));' \
        'up.setOnAction(event -> report("Moved " + name + " up.", mods.moveDown(name)));'
    assert_log_contains "and the slot that should have moved into slot 1 is named by its serialised tuple" \
        "${DIRTY_LOG}" 'expected: <Serialised: variable_mod01 = 79.96633 SY 0 2 -1 0 0 0.0> but was: <Serialised: variable_mod01 = 15.9949 M 0 3 -1 0 0 0.0>'
    assert_testcase "the add/edit/reorder/remove walk is the method that failed" failed \
        "${T2}" addEditReorderRemove
    restore_pristine "${VARMOD_EDITOR}"
    end_control
}

control_2v() {
    begin_control "2v" "item 2, VERSION-BLIND [recorded, unit 6 injection 6a]: every release offers ^ and \$"
    # Unit 6's sign-off, injection 6a: "VariableModEditor offers
    # TerminalCode.values() instead of the release's codes -- red:
    # VariableModificationEditorUiTest.theOlderReleaseOffersPeptideTerminiOnly:
    # Comet 2026.02.2 (2 failures)".  The default release really does offer all
    # four, so its test must stay green: the red is the version, nothing else.
    inject_and_run "terminus codes of every release" "${VARMOD_EDITOR}" "${SEL_2}" fixed \
        'Comet 2026.02.2 (2 failures)' \
        'for (TerminalCode code : mods.terminusCodes()) {' \
        'for (TerminalCode code : TerminalCode.values()) {'
    assert_testcase "2026.02.2's terminus offer is the method that failed" failed \
        "${T2}" theOlderReleaseOffersPeptideTerminiOnly
    assert_testcase "2026.03.0, whose offer the defect does not change, stays green" passed \
        "${T2}" theDefaultReleaseOffersProteinTermini
    restore_pristine "${VARMOD_EDITOR}"
    end_control
}

control_3a() {
    begin_control "3a" "item 3 [recorded, unit 7 injection 7b]: the preset preview's Cancel applies every row"
    # Unit 7's sign-off, injection 7b: "PresetControl's Cancel calls
    # presets.applyAll() -- red: PresetPreviewUiTest.previewCancelAndApplyASubset:
    # cancelling changes nothing".
    inject_and_run "cancel applies all" "${PRESET_CONTROL}" "${SEL_3}" fixed \
        'cancelling changes nothing; changed:' \
        '                    presets.cancel();' \
        '                    presets.applyAll();'
    assert_log_contains "and the declarations the cancel changed are listed" \
        "${DIRTY_LOG}" 'fragment_bin_tol = 1.0005'
    restore_pristine "${PRESET_CONTROL}"
    end_control
}

control_3b() {
    begin_control "3b" "item 3 [NEW]: Apply selected applies every row"
    # "applying a subset applies exactly that subset".  Two of eight rows are
    # ticked; with all eight applied, the test's typed-out status -- the two
    # changes, by name, with their values -- is met by a status of eight.
    inject_and_run "apply selected applies all" "${PRESET_CONTROL}" "${SEL_3}" regex \
        'expected: <Applied 2 changes of Low-res precursor, low-res fragments: Precursor tolerance, upper bound \(peptide_mass_tolerance_upper\) 10 -> 3\.0; Fragment bin width \(fragment_bin_tol\) 0\.02 -> 1\.0005\.> but was: <Applied 8 changes of Low-res precursor, low-res fragments: ' \
        'applySelected.setOnAction(event -> report(previewedName(), presets.applySelected()));' \
        'applySelected.setOnAction(event -> report(previewedName(), presets.applyAll()));'
    assert_testcase "the preview/cancel/subset walk is the method that failed" failed \
        "${T3}" previewCancelAndApplyASubset
    restore_pristine "${PRESET_CONTROL}"
    end_control
}

control_3c() {
    begin_control "3c" "item 3 [NEW, unit 10]: the fragment instrument choice applies its rows the moment it is chosen"
    # AC-PAR-08: "Preset application shows a diff before changing anything".
    # Unit 5 made the Essentials fragment choice apply its preset's rows at
    # once; unit 9 found it and unit 10 routed it through a preview.  Put
    # back, the configuration changes on the choice itself, and the test's
    # canonical-text comparison names the three declarations.
    inject_and_run "fragment choice applied at once" "${ESSENTIALS_VIEW}" "${SEL_3C}" fixed \
        'choosing changes nothing; changed: ==> expected: <[]> but was: <[fragment_bin_offset = 0.4, fragment_bin_tol = 1.0005, theoretical_fragment_ions = 1]>' \
        '                                preview.previewing(fragmentPresets.previewFragment(after));' \
        '                                preview.previewing(fragmentPresets.previewFragment(after));
                                fragmentPresets.applyAll();'
    assert_testcase "the instrument-choice walk is the method that failed" failed \
        "${T3F}" instrumentChoiceIsPreviewed
    restore_pristine "${ESSENTIALS_VIEW}"
    end_control
}

control_3d() {
    begin_control "3d" "item 3 [NEW, unit 10]: the fragment preview is not scoped to the fragment parameters"
    # The instrument choice previews the fragment rows of a preset that also
    # sets the precursor window.  Unscoped, the preview offers the precursor
    # rows as well, and Apply all would change them.  A whole-preset preview is
    # unscoped anyway, so the gate-3 preset test must stay green: the red is the
    # scope, nothing else.
    inject_and_run "fragment preview unscoped" "${PRESET_PREVIEW}" "${SEL_3D}" fixed \
        'expected: <[[Fragment bin width (fragment_bin_tol), 0.02, 1.0005], [Fragment bin offset (fragment_bin_offset), 0.1, 0.4], [Flanking-bin scoring (theoretical_fragment_ions), 0, 1]]> but was: <[[Precursor tolerance, upper bound (peptide_mass_tolerance_upper), 10, 3.0]' \
        '        return scope.map(names -> names.contains(parameter)).orElse(true);' \
        '        return scope.isPresent() || true;'
    assert_testcase "the instrument-choice walk is the method that failed" failed \
        "${T3F}" instrumentChoiceIsPreviewed
    assert_testcase "the whole-preset preview, which the defect does not touch, stays green" passed \
        "${T3}" previewCancelAndApplyASubset
    restore_pristine "${PRESET_PREVIEW}"
    end_control
}

control_4a() {
    begin_control "4a" "item 4 [recorded, unit 7 injection 7a]: a failed raw apply resets the configuration"
    # Unit 7's sign-off, injection 7a, green before the rework and red after it:
    # "ExpertRawEditUiTest.aFailedParseChangesNothing: a draft that does not
    # parse (3 failures)".  The configuration is moved away from the defaults
    # first, so a reset is visible.
    inject_and_run "failed apply resets" "${EXPERT_VM}" "${SEL_4}" fixed \
        'a draft that does not parse (3 failures)' \
        '            applyErrors.set(parsed.errors());' \
        '            applyErrors.set(parsed.errors());
            session.newConfiguration(session.release());'
    assert_log_contains "and the configured values and origins are named as changed" \
        "${DIRTY_LOG}" 'the configured values and origins after the refused raw edit'
    assert_testcase "the failed-parse walk is a method that failed" failed \
        "${T4}" aFailedParseChangesNothing
    restore_pristine "${EXPERT_VM}"
    end_control
}

control_4b() {
    begin_control "4b" "item 4 [NEW]: a raw apply that parses is adopted without confirmation"
    # R-PARAM-08: a raw edit is applied only on explicit confirmation.  The
    # typed field must still read 0 between Apply and Confirm.
    inject_and_run "apply adopts without confirmation" "${EXPERT_VM}" "${SEL_4}" fixed \
        'not before confirming ==> expected: <0> but was: <6>' \
        '                                enforced)));
        return EditOutcome.applied();' \
        '                                enforced)));
        session.adopt(proposed, Adoption.RAW_APPLIED);
        return EditOutcome.applied();'
    assert_testcase "the failed-parse walk, which holds the confirmation step, failed" failed \
        "${T4}" aFailedParseChangesNothing
    restore_pristine "${EXPERT_VM}"
    end_control
}

control_4v() {
    begin_control "4v" "item 4, VERSION-BLIND [recorded, unit 7 agent]: the draft is parsed as the first offered release"
    # Unit 7's agent's own version-blind injection, recorded red in its report:
    # "Expert parsing every draft as the first offered release".  On Comet
    # 2026.02.2 the ^ residue must be refused at line 27; parsed as 2026.03.0 it
    # is not.  The 2026.03.0 walk is untouched by the defect and must stay
    # green.
    inject_and_run "draft parsed as the first offered release" "${EXPERT_VM}" "${SEL_4}" fixed \
        'Comet 2026.02.2 refuses the ^ at line 27' \
        'return new CometParamsParser(session.metadata(), session.release()).parse(draft.get());' \
        'return new CometParamsParser(session.metadata(), session.offeredReleases().get(0)).parse(draft.get());'
    assert_testcase "the release-dependent ^ walk is the method that failed" failed \
        "${T4}" theCaretDependsOnTheRelease
    assert_testcase "the 2026.03.0 failed-parse walk, which the defect does not touch, stays green" passed \
        "${T4}" aFailedParseChangesNothing
    restore_pristine "${EXPERT_VM}"
    end_control
}

control_5a() {
    begin_control "5a" "item 5 [recorded, unit 6]: a locked output's check box left enabled in the view"
    # "Disabling a workflow-required output is impossible while the dependent
    # stage is enabled".  Unit 6 found the session refuses the edit anyway, so
    # the saved value stays 1; what the view must still show is a control that
    # cannot be changed, and the test says so in its own words.
    inject_and_run "lock not applied in the view" "${FIELD_CONTROL}" "${SEL_5}" fixed \
        'disabled for change ==> expected: <true> but was: <false>' \
        '            input.setDisable(locked);' \
        '            input.setDisable(false);'
    assert_log_matches "and the failure names the locked output" \
        "${DIRTY_LOG}" '(ess|adv)-output_(pepxmlfile|percolatorfile), (before any attempt|after a click on it)'
    restore_pristine "${FIELD_CONTROL}"
    end_control
}

control_5b() {
    begin_control "5b" "item 5 [NEW]: the lock's reason is never shown"
    # "... and the reason is shown".  The reason text is still set; it is
    # simply never visible.
    inject_and_run "lock reason hidden" "${FIELD_CONTROL}" "${SEL_5}" fixed \
        'the reason is on screen ==> expected: <true> but was: <false>' \
        '            lock.setVisible(locked);' \
        '            lock.setVisible(false);'
    restore_pristine "${FIELD_CONTROL}"
    end_control
}

control_6a() {
    begin_control "6a" "item 6 [recorded, unit 6]: a summary entry no longer moves the focus to its field"
    # "... appears in the summary and is reachable by keyboard".  The entry is
    # still there and still reached by Tab; Enter on it does nothing.
    inject_and_run "summary entry inert" "${SUMMARY_PANE}" "${SEL_6A}" fixed \
        'Enter on the summary entry moves the focus to the field ==> expected: <ess-peptide_mass_tolerance_lower> but was: <param-summary-entry-0>' \
        'button.setOnAction(event -> focusField.accept(name));' \
        'button.setOnAction(event -> { });'
    restore_pristine "${SUMMARY_PANE}"
    end_control
}

control_6b() {
    begin_control "6b" "item 6 [recorded, unit 6 injection 6e]: the parameters' readiness forced to 'do not block'"
    # Unit 6's sign-off, injection 6e (which replaced the equivalent 6b, see
    # control H6): "the parameters' readiness text forced to 'do not block' --
    # red: CrossParameterValidationUiTest 4/4".  The same text is how an
    # unresolved migration entry blocks Run (decision P7-3), so the migration
    # test must name it too.
    inject_and_run "readiness text forced" "${RUN_CONTROL}" "${SEL_6B}" fixed \
        'the Run section (2 failures)' \
        '                readiness.parametersBlockRun()
                        ? "The parameters block a run:' \
        '                false
                        ? "The parameters block a run:'
    assert_testcase "the reversed-window walk failed" failed "${T6}" aReversedPrecursorWindow
    assert_testcase "the migrated NEEDS_ATTENTION entry no longer blocks Run in words" failed \
        "${T6M}" aMigratedEntryNeedingAttentionBlocksRun
    assert_log_contains "and the migration test names the text it was given instead" \
        "${DIRTY_LOG}" 'but was: <The parameters do not block a run.>'
    restore_pristine "${RUN_CONTROL}"
    end_control
}

control_7a() {
    begin_control "7a" "item 7 [recorded, unit 6 injection 6d]: a parameter control's own accessible name removed"
    # Unit 6's rework: removing named(input, ...) from FieldControl left the
    # test green because Phase 02's fallback names every unnamed control.  After
    # fc68afa it is red in both classes; a generated name is not a name.
    inject_and_run "parameter control unnamed" "${FIELD_CONTROL}" "${SEL_7A}" regex \
        'TextField #ess-database_name under #param-essentials has only the generated fallback name' \
        '        input.setId(UiIds.parameterControl(surface, name));
        named(input, field.displayName());' \
        '        input.setId(UiIds.parameterControl(surface, name));'
    assert_log_contains "the representative names name the field by its display name, typed out" \
        "${DIRTY_LOG}" '#adv-allowed_missed_cleavage is named "text field within adv-category-digestion_enzymes", not "Allowed missed cleavages"'
    assert_log_matches "and Phase 02's enumeration refuses the generated name too" \
        "${DIRTY_LOG}" '[0-9]+ controls this project created have no name of their own: TextField with id #ess-database_name'
    restore_pristine "${FIELD_CONTROL}"
    end_control
}

control_7b() {
    begin_control "7b" "item 7 [NEW]: the validation state left out of a parameter control's accessible help"
    # "... and validation state is conveyed in text": on screen in the state
    # label, and to a screen reader in the control's help.  The label stays;
    # the help loses its Validation line.
    inject_and_run "state not in accessible help" "${FIELD_CONTROL}" "${SEL_7}" fixed \
        "#ess-database_name's accessible help does not carry its state" \
        '        lines.add("Validation: " + field.stateText());' \
        '        // injected defect: the validation state is not in the accessible help'
    assert_testcase "the 2026.03.0 walk failed" failed "${T7}" theDefaultRelease
    assert_testcase "the finding-in-text check failed" failed "${T7}" aFindingIsStatedInText
    restore_pristine "${FIELD_CONTROL}"
    end_control
}

control_7c() {
    begin_control "7c" "item 7 [NEW, unit 10]: a static-modification row's mass field loses its own accessible name"
    # Unit 10 moved the add_* parameters out of FieldControl into the table;
    # gate 7 still walks every one of them by its parameter identifier.  The
    # table's mass field unnamed leaves Phase 02's generated fallback, which is
    # not a name.
    inject_and_run "static-mod mass field unnamed" "${STATIC_MOD_TABLE}" "${SEL_7}" fixed \
        'TextField #ess-add_Cterm_peptide under #param-essentials has only the generated fallback name' \
        '            named(mass, field.displayName());' \
        '            // injected defect: the mass field is not named'
    assert_testcase "the 2026.03.0 walk failed" failed "${T7}" theDefaultRelease
    restore_pristine "${STATIC_MOD_TABLE}"
    end_control
}

control_7v() {
    begin_control "7v" "item 7, VERSION-BLIND [NEW]: a field's choices are the curated definition's, not the release's"
    # Decision P7-2: every field is the selected release's.  index_search_type's
    # -1 "Not set" choice is a 2026.03.0 override of the curated definition; with
    # the curated choices, 2026.03.0 loses it.  2026.02.2's choices ARE the
    # curated ones, so its check of the same combo must stay green.
    inject_and_run "curated choices for every release" "${FIELD_VM}" "${SEL_7}" fixed \
        'expected: <[Not set: an index built on demand is a fragment-ion index (FI_DB), and Comet never warns, Peptide index (PI_DB), Fragment-ion index (FI_DB)]> but was: <[Peptide index (PI_DB), Fragment-ion index (FI_DB)]>' \
        '        for (var choice : definition.choices()) {' \
        '        for (var choice : session.metadata().parameter(definition.name()).orElseThrow().choices()) {'
    assert_testcase "2026.03.0's index_search_type offer is the method that failed" failed \
        "${T7}" indexSearchTypeOnTheDefaultRelease
    assert_testcase "2026.02.2, whose choices the defect does not change, stays green" passed \
        "${T7}" theOlderRelease
    restore_pristine "${FIELD_VM}"
    end_control
}

control_8a() {
    begin_control "8a" "item 8 [recorded, unit 7 injection 7c]: alias matching removed from the search"
    # Unit 7's sign-off, injection 7c: "red: ParameterSearchUiTest.findsByEachAttribute:
    # by alias (3 failures)".  The form matters: no alias is matched at all.
    # (A first form here kept the matched aliases and dropped only the ALIAS
    # attribute; SearchHit's own invariant then threw on the FX thread, a red
    # for a reason other than the defect, which this harness refused.)
    inject_and_run "alias matching removed" "${SEARCH_VM}" "${SEL_8}" fixed \
        'by alias (3 failures)' \
        '                field.definition().aliases().stream().filter(a -> contains(a, wanted)).toList();' \
        '                List.<String>of();'
    assert_log_contains "and the alias query finds nothing where num_enzyme_termini was expected" \
        "${DIRTY_LOG}" 'expected: <[Enzymatic termini (num_enzyme_termini) -- Matched by alias "semi-tryptic"]> but was: <[]>'
    restore_pristine "${SEARCH_VM}"
    end_control
}

control_8b() {
    begin_control "8b" "item 8 [NEW]: activating a search result no longer moves the focus to its field"
    # The result is found and listed; Enter on it opens Advanced but leaves the
    # focus where it was, so the field is not reached.
    inject_and_run "result does not focus its field" "${EDITOR_VIEW}" "${SEL_8}" fixed \
        'the field has the focus ==> expected: <adv-allowed_missed_cleavage> but was:' \
        '        if (advanced.focus(hit.name())' \
        '        if (false && advanced.focus(hit.name())'
    assert_log_contains "and the failure names the result that was activated" \
        "${DIRTY_LOG}" 'after activating the result for #adv-allowed_missed_cleavage'
    restore_pristine "${EDITOR_VIEW}"
    end_control
}

control_8v() {
    begin_control "8v" "item 8, VERSION-BLIND [recorded, unit 5 injection 5c]: help is the curated text, not the release's"
    # Unit 5's sign-off, injection 5c: "FieldViewModel.shortHelp returns the
    # curated help instead of the release's -- red:
    # ParameterSearchViewModelTest.releaseHelp expected: <[index_search_type]>
    # but was: <[]>".  Graded on that VIEW-MODEL test, as unit 8 shipped it,
    # AND on the item-8 GUI test: unit 9 gave ParameterSearchUiTest one method
    # per release whose query only that release's help says ("not set" in
    # 2026.03.0's help of index_search_type, "is ignored" in 2026.02.2's help
    # of spectral_library_ms_level).  The curated help IS 2026.02.2's, so the
    # defect blinds the 2026.03.0 method and leaves the 2026.02.2 one green.
    inject_and_run "curated help for every release" "${FIELD_VM}" "${SEL_8V}" fixed \
        'expected: <[index_search_type]> but was: <[]>' \
        '        return definition.shortHelp();' \
        '        return session.metadata().parameter(definition.name()).orElseThrow().shortHelp();'
    assert_testcase "the release-help search is the method that failed" failed \
        ParameterSearchViewModelTest releaseHelp
    # The same injection, the GUI test: a second run, because a failing
    # cometgui-ui test stops the reactor before cometgui-app runs anything.
    DIRTY_LOG="${LOGS}/${CONTROL_ID}-gui-dirty.log"
    printf '   %s\n' "$(gate_command "${SEL_8VG}")"
    dirty_run "curated help for every release, through the launched application" \
        "${FIELD_VM}" "${SEL_8VG}" "${DIRTY_LOG}"
    grade_red fixed "the launched application's search misses 2026.03.0's help text" \
        "${DIRTY_RC}" "${DIRTY_LOG}" 'Comet 2026.03.0, help text "not set" (3 failures)'
    assert_log_contains "naming the parameter whose 2026.03.0 help says it" \
        "${DIRTY_LOG}" 'expected: <[Index type for an index built on demand (index_search_type) -- Matched by help text]> but was: <[]>'
    assert_testcase "the GUI test of 2026.03.0's help is the GUI method that failed" failed \
        "${T8}" helpTextOfTheDefaultRelease
    assert_testcase "2026.02.2, whose help the curated text is, stays green" passed \
        "${T8}" helpTextOfTheOlderRelease
    restore_pristine "${FIELD_VM}"
    end_control
}

# ---------------------------------------------------------------- control H --

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

# expect_recorded_failure LABEL MUST-SAY COMMAND... -- the command must record
# exactly one failure, and that failure must say MUST-SAY.
expect_recorded_failure() {
    local label="$1" must="$2"
    shift 2
    local before="${FAILED}"
    DELIBERATE=1
    "$@"
    DELIBERATE=0
    if [ "${FAILED}" -eq $(( before + 1 )) ] && printf '%s' "${FAILURES[-1]}" | grep -qF -- "${must}"; then
        FAILED="${before}"
        unset 'FAILURES[-1]'
        record_pass "${label}"
    else
        record_fail "${label}: HARNESS FAILURE -- it did not record exactly one failure saying '${must}'. Every other control in this script is unreliable."
    fi
}

readonly H_ANCHOR='        run.setDisable(!readiness.runEnabled());'

h_same_text() {
    replace_once "H1" "${RUN_CONTROL}" "${H_ANCHOR}" "${H_ANCHOR}"
    assert_modified "H1" "${RUN_CONTROL}"
}

h_no_anchor() {
    replace_once "H2" "${RUN_CONTROL}" 'this text is in no source file' 'nor is this'
}

h_source_not_bytecode() {
    replace_once "H3" "${RUN_CONTROL}" "${H_ANCHOR}" \
        "${H_ANCHOR} // H3: the source changed and the bytecode cannot"
    assert_modified "H3" "${RUN_CONTROL}"
    dirty_run "H3" "${RUN_CONTROL}" "${T8V}" "${LOGS}/H3-comment-only.log"
}

control_H() {
    begin_control "H" "the harness itself: an injection that did not land, or changed nothing, is never a pass"
    save_pristine "${RUN_CONTROL}"

    expect_harness_error "H1 an injection whose replacement equals its anchor is refused" \
        "byte-identical to the pristine copy" h_same_text
    expect_harness_error "H2 an anchor that matches nothing is refused" \
        "the injection anchor is gone" h_no_anchor
    cmp -s "${SANDBOX}/${RUN_CONTROL}" "${PRISTINE}/${RUN_CONTROL}" \
        || harness_error "control H's refused injections changed ${RUN_CONTROL} after all."

    # A comment on the anchor's own line moves no line number, so javac writes
    # the same class: assert_modified accepts it -- the source did change --
    # and the bytecode check must refuse it.  A real Maven run.
    expect_harness_error "H3 an injection that changed the source but not the bytecode is refused" \
        "BYTE-IDENTICAL to the clean baseline after the dirty run" h_source_not_bytecode
    restore_pristine "${RUN_CONTROL}"

    # H3's run tested the clean code; graded as a red, the grader must record a
    # failure rather than a pass.
    local log="${LOGS}/H3-comment-only.log" rc
    [ -s "${log}" ] || harness_error "H3 left no log, so H4 has nothing to grade."
    rc="$(sed -n 's/^=== MVN EXIT STATUS: \([0-9]*\) ===$/\1/p' "${log}" | tail -1)"
    [ "${rc}" = "0" ] \
        || harness_error "H3's run of the clean code did not pass (exit ${rc:-unknown}); H4 needs a green run to grade."
    expect_recorded_failure "H4 a control run with NO defect injected is recorded as a failure, not a pass" \
        "HARNESS FAILURE -- the check PASSED with the defect present" \
        grade_red fixed "H4 (deliberately graded with no defect injected)" "${rc}" "${log}" \
        'expected: <[index_search_type]> but was: <[]>'

    log="${LOGS}/H5-wrong-reason.log"
    printf '[ERROR] Tests run: 1, Failures: 1\n[ERROR]   SomeOtherTest.somethingElse:1 expected: <1> but was: <2>\n' >"${log}"
    expect_recorded_failure "H5 a red WITHOUT the expected diagnostic is recorded as a failure, not a pass" \
        "failed, but without the expected diagnostic" \
        grade_red fixed "H5 (deliberately graded against the wrong reason)" 1 "${log}" \
        'expected: <[index_search_type]> but was: <[]>'

    # H6, the equivalent injection, for real.  Unit 6's injection 6b -- Run
    # disabled only by the workflow engine's reason -- left
    # CrossParameterValidationUiTest green, and rightly: until Phase 08 the
    # engine's reason was always present.  Since Phase 08 unit 7 the engine's
    # half is real, but that test's application has no Comet installed (its
    # Tool Manager reads an application data directory holding none), so the
    # engine still always has a reason THERE and the injected Run control
    # behaves exactly like the real one.  It is NOT a control of item 6 there
    # (6b above is), and a harness that counted it as one would be lying.  It
    # reaches the bytecode, it runs, it is green -- and the grading must call
    # that a HARNESS FAILURE, which is what this sub-control requires.
    local -r engine_only='        run.setDisable(!readiness.engineReasons().isEmpty());'
    save_pristine "${RUN_CONTROL}"
    replace_once "H6" "${RUN_CONTROL}" "${H_ANCHOR}" "${engine_only}"
    assert_modified "H6" "${RUN_CONTROL}"
    log="${LOGS}/H6-equivalent.log"
    printf '   %s\n' "$(gate_command "${T6}")"
    dirty_run "H6" "${RUN_CONTROL}" "${T6}" "${log}"
    expect_recorded_failure "H6 an EQUIVALENT injection (Run disabled only by the engine's reasons, in a test whose engine always has one) is reported as a HARNESS FAILURE, not as a control that bit" \
        "HARNESS FAILURE -- the check PASSED with the defect present" \
        grade_red fixed "H6 (the equivalent injection, graded as item 6)" "${DIRTY_RC}" "${log}" \
        'Run is disabled ==> expected: <true> but was: <false>'
    if [ "${DIRTY_RC}" -ne 0 ]; then
        record_fail "H6's premise is stale: the equivalent injection went RED in ${T6} (exit ${DIRTY_RC}). Either that test's application now has a Comet installed -- then the injection is a real control there too, as H7 is -- or something else broke; see $(rel "${log}")"
    fi
    restore_pristine "${RUN_CONTROL}"

    # H7 [NEW, phase 08 unit 7]: the SAME injection where it is not equivalent.
    # RunReadinessUiTest's application has a Comet registered with its Tool
    # Manager and real files chosen, so the pre-run check answers "the
    # workflow engine can run this search"; a reversed precursor window is
    # then the ONLY reason against Run.  With Run disabled only by the
    # engine's reasons, Run is enabled over a parameter error -- the defect
    # Phase 07's gate-6 note said could not be seen before Phase 08.  The
    # decoy-block method, where the ENGINE disables Run, must stay green: the
    # injection changes nothing there, and a red there would mean the test is
    # failing for another reason.
    save_pristine "${RUN_CONTROL}"
    replace_once "H7" "${RUN_CONTROL}" "${H_ANCHOR}" "${engine_only}"
    assert_modified "H7" "${RUN_CONTROL}"
    log="${LOGS}/H7-engine-ready.log"
    printf '   %s\n' "$(gate_command "${T6R}")"
    dirty_run "H7" "${RUN_CONTROL}" "${T6R}" "${log}"
    grade_red fixed "H7 Run disabled only by the engine's reasons, where the engine is ready (item 6 with the engine's half real)" \
        "${DIRTY_RC}" "${log}" \
        'Run is disabled by the parameters alone ==> expected: <true> but was: <false>'
    assert_testcase "H7 the parameters-alone method is the one that failed" failed \
        "${T6R}" theParametersAloneDisableRun
    assert_testcase "H7 the decoy-block method, where the engine itself disables Run, stays green" passed \
        "${T6R}" theDecoyBlockOnScreen
    restore_pristine "${RUN_CONTROL}"

    USED_SELECTORS+=("${SEL_H}")
    end_control
}

# ---------------------------------------------------------------- the main --

run_control() {
    case "$1" in
        1) control_1 ;; 1b) control_1b ;; 1c) control_1c ;;
        2a) control_2a ;; 2v) control_2v ;;
        3a) control_3a ;; 3b) control_3b ;; 3c) control_3c ;; 3d) control_3d ;;
        4a) control_4a ;; 4b) control_4b ;; 4v) control_4v ;;
        5a) control_5a ;; 5b) control_5b ;;
        6a) control_6a ;; 6b) control_6b ;;
        7a) control_7a ;; 7b) control_7b ;; 7c) control_7c ;; 7v) control_7v ;;
        8a) control_8a ;; 8b) control_8b ;; 8v) control_8v ;;
        H) control_H ;;
        *) die "no control '$1'. Controls: ${ALL_CONTROLS[*]}" 2 ;;
    esac
}

final_clean_run() {
    begin_control "C" "every restoration: the clean sandbox passes again, on the baseline's own bytecode"
    local log="${LOGS}/C-clean.log" rc=0 selectors
    selectors="$(printf '%s\n' "${USED_SELECTORS[@]}" | tr ',' '\n' | awk 'NF && !seen[$0]++' | paste -sd, -)"
    printf '   %s\n' "$(gate_command "${selectors}")"
    run_mvn "${log}" "${selectors}" || rc=$?
    if [ "${rc}" -ne 0 ]; then
        record_fail "the clean sandbox does not pass again after the restorations (exit ${rc}, log: $(rel "${log}"))"
        printf '         %s\n' "$(grep -m3 -E '^\[ERROR\] +[A-Za-z]' "${log}" | cut -c1-200 || true)"
    else
        verify_classes_ran "${log}" "${selectors}"
        record_pass "the clean sandbox passes again: exit 0, every test class the dirty runs used executed ($(printf '%s\n' "${selectors}" | tr ',' '\n' | grep -c .) selectors)"
    fi
    compare_tree "${log}"
    local file
    for file in $(printf '%s\n' "${RESTORED[@]}" | sort -u); do
        record_pass "restored compiled form of ${file##*/} is byte-identical to the clean baseline"
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
    [ -d "${M2REPO}/org/cometgui" ] || die "$(rel "${M2REPO}") is not populated; run bash scripts/build.sh first." 3
    bash "${ROOT}/scripts/fetch-fontstack.sh" --verify >/dev/null \
        || die "the font stack is missing; run bash scripts/fetch-fontstack.sh first. Without it every GUI control fails for the wrong reason." 3

    local -a selected=()
    if [ "${self_test_only}" -eq 1 ]; then
        selected=(H)
    elif [ -n "${only}" ]; then
        IFS=',' read -r -a selected <<<"${only}"
    else
        selected=("${ALL_CONTROLS[@]}")
    fi
    local id
    for id in "${selected[@]}"; do
        control_selectors "${id}" >/dev/null
    done

    mkdir -p -- "${LOGS}"
    rm -f -- "${LOGS}"/*.log "${LOGS}"/*.sha256 "${LOGS}"/*.marker

    printf '===============================================================================\n'
    printf ' %s -- every PHASE-07 gate item must be seen to fail\n' "${SCRIPT_NAME}"
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
        for id in "${selected[@]}"; do
            run_control "${id}"
        done
        final_clean_run
    fi
    [ "$(cometgui_repo_digest)" = "${COMETGUI_REPO_DIGEST}" ] \
        || harness_error "the shared repository's org/cometgui jars changed during this run; this script must never write them."

    local total=$(( $(date +%s) - started ))
    printf '\n===============================================================================\n'
    printf ' per-control wall clock:\n'
    printf '   %s\n' "${TIMINGS[@]}"
    printf ' SUMMARY: %d control(s) passed, %d failed, in %d seconds (%dm%02ds)\n' \
        "${PASSED}" "${FAILED}" "${total}" "$((total / 60))" "$((total % 60))"
    printf ' Logs: %s\n' "$(rel "${LOGS}")"
    printf '===============================================================================\n'
    if [ "${FAILED}" -ne 0 ]; then
        printf '\n'
        printf '  %s\n' "${FAILURES[@]}"
        die "${FAILED} param-ui-gate control(s) failed. A gate that cannot be seen to fail is not a gate." 1
    fi
    if [ "${self_test_only}" -eq 1 ]; then
        printf '\n  self-test OK -- the harness reports an unchanged file, a missing anchor, an\n'
        printf '  injection that reached the source but not the bytecode, a run with no\n'
        printf '  defect, a red for the wrong reason and an EQUIVALENT injection as a HARNESS\n'
        printf '  ERROR or FAILURE, not as a pass.\n\n'
        return 0
    fi
    if [ -n "${only}" ]; then
        printf '\n  The selected controls bit (--only %s). This is NOT a full run.\n\n' "${only}"
        return 0
    fi
    printf '\n  PHASE-07 exit gate items 1 to 8 were proved here, each by a production defect\n'
    printf '  in cometgui-ui in a git-archive sandbox, proved in the bytecode and graded on\n'
    printf '  the failing assertion'"'"'s own words in the gate test that asserts the item.\n'
    printf '  Four controls were version-blind (2v, 4v, 7v and 8v), each requiring the\n'
    printf '  release the defect does not touch to stay green where its test can show it;\n'
    printf '  8v is graded on the item-8 GUI test on both releases and on its view-model\n'
    printf '  test.\n'
    printf '  The harness reports an injection that reached the source but not the\n'
    printf '  bytecode as a HARNESS ERROR, not as a pass, and an equivalent injection as\n'
    printf '  a HARNESS FAILURE.\n'
    printf '\n  Every gate rejected its defect and accepted the clean tree.\n\n'
}

main "$@"
