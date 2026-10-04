#!/usr/bin/env bash
#
# CometGUI -- prove the PHASE-06 Comet parameter model gates can fail.
#
#   bash scripts/verify-param-gates.sh                   every control
#   bash scripts/verify-param-gates.sh --self-test       control H only
#   bash scripts/verify-param-gates.sh --only 2a,7c,H    named controls only
#
# A gate that has never been seen to fail has not been shown to work
# (CONTRIBUTING.rst, "Gate conventions").  Phase 06's exit gate is nine claims
# about parsing, writing and validating comet.params.  Each is checked by tests,
# and a green test says nothing about whether it would notice the defect it
# exists to catch.  This script injects, one at a time, a defect each item
# exists to catch -- into PRODUCTION code, or for item 2 into the production
# metadata the module ships -- requires the narrowest test selector that should
# catch it to fail WITH THAT DEFECT'S OWN DIAGNOSTIC (the failing assertion's
# own words, never a sentence every failure shares), proves the injection
# reached the compiled module, and requires the clean sandbox to pass again.
# Item 9 is graded by running PIT the way scripts/build.sh scores it, per
# package, and its negative control must bite.
#
# It is the sibling of scripts/verify-install-gates.sh (Phase 05) and follows
# its shape: a `git archive HEAD` sandbox, anchors that must match exactly
# once, a pristine copy per damaged file, graded failures, bytecode digests,
# one batched clean re-run, and a control H on itself.
#
# IT IS ASSEMBLED FROM A RECORD, NOT INVENTED.  Every control marked [recorded]
# names the unit of handoffs/PHASE-06-worklog.rst (or the unit-8 brief's
# summary of a unit agent's report) where that injection was really made.  Where
# the record gives the exact failure text, the control requires it; where the
# record names the injection but not its form (the unit AGENTS' injections,
# whose reports reached the work log only as a summary), the form here is this
# script's and is said to be.  At Phase 06 nothing here was [NEW]: every gate
# item had at least one recorded injection.  The COMET-2026-03 controls (v3a
# to v6b) are partly recorded from that package's work log and partly NEW,
# each marked so.
#
# WHAT IT COVERS (PHASE-06 exit gate items; see phases/PHASE-06-comet-param-model.rst)
#
#   0   baseline: the undamaged sandbox passes every selector the controls use,
#       in one run, and the clean compiled module (classes AND resources) is
#       digested
#   1   item 1 [recorded, unit 4, orchestrator]: the writer silently skips every
#       parameter whose value text is empty, so the byte-stable text and the
#       second parse both change
#   2a  item 2 [recorded, unit 2]: the scan_range entry removed from the
#       sandbox's copy of the shipped metadata JSON; the drift test against the
#       real -q fixture must name it UNMODELLED
#   2b  item 2 [recorded, unit 2, orchestrator]: the drift default comparison
#       reduced to the first token -- the thin one, graded by
#       defaultDiffersInAPartialDump and by nothing else
#   3a  item 3 [recorded, unit 3, agent]: only the first neutral loss written
#   3b  item 3 [recorded, unit 3, orchestrator]: a min,max count written max,min
#   4a  item 4 [recorded, unit 3, agent]: the enzyme table's duplicate-number
#       invariant disabled, so a custom enzyme may reuse a number
#   4b  item 4 [recorded, unit 4, agent]: the writer's refusal of an enzyme
#       number absent from the table disabled
#   5a  item 5 [recorded, unit 3, agent]: Numbers.text, the one number writer,
#       made locale-sensitive; the ROOT-locale text is required to STAY GREEN
#   5b  item 5 [recorded, unit 4, agent]: the writer's decimal path made
#       locale-sensitive -- unit 4's "offset 1076, 20,0"; ROOT stays green
#   6a  item 6 [recorded, unit 4, agent]: the writer drops the unknown section
#   6b  item 6 [recorded, unit 4, agent]: the parser drops unknown parameters
#       (and still warns about them)
#   7a  item 7 [recorded, unit 5, agent]: the tolerance pair routed through the
#       generic ordering rule -- red through that rule's own PRODUCT GUARD
#       (it refuses a value that is not a two-value range), 14 errors, as the
#       record says; graded on the guard's sentence
#   7b  item 7 [recorded, unit 5, agent]: an asymmetric window made an error
#   7c  item 7 [recorded, unit 5, orchestrator]: the reversed-pair error can
#       never fire (compareTo > 0 became > 1)
#   8   item 8 [recorded, unit 7]: scripts/cometparams_selftest.py, INVOKED in
#       the sandbox, not duplicated -- 55 damaged generator inputs (27 when
#       unit 7 shipped it; COMET-2026-03 units 1, 3 and 4 added the overrides,
#       the residue alphabets and the rule severities) and 4 defects through
#       the real Sphinx builder-inited hook, each graded there on its own
#       diagnostic; graded here on its OK line, its floors, the removed entry
#       it names, the hook's count equal to the metadata's, and (COMET-2026-03
#       unit 6) its two per-release controls: index_search_type's and
#       variable_mod01's entries state each release's own facts
#
#   COMET-2026-03 (the Comet 2026.03.0 intake, handoffs/COMET-2026-03-worklog.rst):
#   its exit gate items 3 to 6 are VERSION-SCOPED facts, kept as data in each
#   release's version record (decision C-2).  The defect each family exists to
#   catch is a fact applied to the WRONG RELEASE, so every control below makes
#   one thing VERSION-BLIND, and where it can, requires the release the defect
#   does not touch to STAY GREEN -- the red is the version, nothing else.
#   [recorded, uN orch.] is that unit's sign-off injection in the package's
#   work log, with its failure text where the log gives it; [NEW, u6] is this
#   script's own.
#
#   v3a item 3 [recorded, u1 orch.]: CuratedMetadata applies the FIRST version
#       record's overrides (2026.03.0's) to every release: -1 becomes a
#       2026.02.2 choice
#   v3b item 3 [NEW, u6]: no release's override is applied: 2026.03.0's drift
#       sees index_search_type's -1 as drift; 2026.02.2's counts stay green
#   v3c item 3 [NEW, u6]: scan_range's range in the shipped metadata ends at
#       2026.02.2, so the drift test of 2026.03.0 alone must name it
#       UNMODELLED; 2026.02.2's counts stay green
#   v4a item 4 [NEW, u6, after u3 orch. 1]: the tuple codec gives every
#       release 2026.03.0's residue alphabet: 2026.02.2 accepts ^
#   v4b item 4 [NEW, u6]: the reverse -- every release gets 2024.01.0's
#       alphabet: 2026.03.0 refuses ^ and its fifteen-slot round trip goes red
#   v4c item 4 [NEW, u6]: the writer writes the CURATED inline comment, not
#       the release's own: the 2026.03.0 byte-stable canonical round trip (gate
#       item 1 for 2026.03.0) goes red; 2026.02.2's stays green
#   v5a item 5 [recorded, u4 orch. 1]: severities bound to the second version
#       record (2026.02.2's) for every model
#   v5b item 5 [NEW, u6]: validation reads the newest release's residue
#       alphabet: ^ in a 2026.02.2 model is no longer an error
#   v5c item 5 [recorded, u4 orch. 2]: AScorePro's "localise all" (-1)
#       suppresses the slot-10 error (ascore == 0 became <= 0)
#   v5d item 5 [NEW, u6]: AScorePro's merge of identical slots disabled: a
#       slot Comet merges away is reported
#   v5e item 5 [NEW, u6]: AScorePro's protein-terminus rewrite applied in a
#       release whose alphabet has no ^ (2026.02.2 merges as 2026.03.0 does)
#   v5f item 5 [NEW, u6]: index_search_type.ignored_without_idx warns in every
#       release; 2026.03.0's warning stays green
#   v5g item 5 [NEW, u6, DATA -- not production code]: one recorded BINARY
#       verdict of the validation corpus made wrong (ist-1's 2026.03.0 warning
#       line removed): the real-binary corpus test must disagree with it
#   v6a item 6 [recorded, u5 orch. A]: value migrations applied whatever
#       release they are FROM
#   v6b item 6 [recorded, u5 orch. B]: migration hands conversion no source
#       findings, so rule-keyed value migrations never apply
#
#   9   item 9 [recorded, units 2 and 6]: PIT over cometgui-params-comet in the
#       sandbox, scored as scripts/build.sh scores it (status='KILLED' over all
#       mutations in mutations.xml; TIMED_OUT is not killed), PER PACKAGE for
#       parser, writer and validation and for the module, each >= 80 %, with
#       EVERY non-killed mutant in those three packages listed by class, line,
#       mutator and status.  Its negative control removes every validation test
#       class from the sandbox (source and compiled class), runs PIT again over
#       the whole module, and requires the validation package to be graded
#       BELOW -- with proof that no removed test killed anything
#   H   the harness itself: a replacement equal to its anchor, an anchor that
#       matches nothing, a metadata removal that removes nothing, an injection
#       that reaches the source but not the bytecode, a green run graded as a
#       red, a red without the expected diagnostic, and a PIT report in which a
#       graded package has no mutation at all must each be reported as a
#       HARNESS ERROR or FAILURE -- never as a pass; and, for the COMET-2026-03
#       controls' own plumbing (unit 6), a metadata range edit that edits
#       nothing, a corpus verdict edit that edits nothing, and a test resource
#       damaged in the source but not on the test class path
#
# WHAT IT DOES NOT COVER, said plainly rather than left to be discovered:
#
#   * Item 9's second half -- "no surviving mutation that suppresses a
#     validation error or drops a parameter" -- is a JUDGEMENT about each
#     survivor, not a number, and this script does not make it.  It lists every
#     non-killed mutant in parser, writer and validation so that the list
#     cannot be missed, and carries NO allow-list of accepted survivors: a new
#     survivor does not fail this script unless it takes a package below 80 %.
#     handoffs/PHASE-06-worklog.rst records the argument for each survivor
#     (ParamsLineReader:137 x2 and VariableModRules:178 -- :204 since the
#     COMET-2026-03 intake -- equivalent), and
#     whoever signs the gate off re-reads the list printed here against it.
#   * Control 9 runs PIT once per arm (clean, then validation tests removed).
#     It is NOT scripts/verify-test-gates.sh's pinned-survivor control, which
#     is a different module and is untouched.
#   * Unit 7's equivalent injection (the generator's empty-value form) is not a
#     control: it changes no byte of output.  Unit 6's two injections that went
#     red through product invariants (MigrationEntry, CometParameters) serve no
#     numbered gate item and are not encoded.  Unit 4's parser-origin injection
#     and unit 6's preset-origin injection likewise serve no numbered item.
#   * Every recorded injection of the unit AGENTS (marked "agent") is recorded
#     only as a one-line summary; its exact form here is this script's.
#
# WHERE IT WORKS.  Never in the working tree.  It extracts `git archive HEAD`
# into _build/param-gate-sandbox and damages that.  `git archive HEAD` is the
# COMMITTED tree: uncommitted changes under cometgui-*/src, scripts/ or docs/
# are reported loudly, because the run then proves HEAD's gates and not the
# tree's.  tools/, .venv/ and scratch/ are gitignored and absent from the
# archive, so they are symlinked: tools/ for the JDK and Maven, .venv/ for the
# generator self-test's Sphinx and the module's generator agreement test,
# scratch/ for the real Comet binaries (2026.03.0, 2026.02.2 and 2024.01.0)
# and the validation corpus's D-006 spectra and proteome (scratch/fixture) the
# module's real-binary tests run -- and FAIL, rather than skip, without.
# Nothing here writes to them.
#
# ONE MODULE, BUILT AGAINST ITS OWN UPSTREAM.  Every injection is in
# cometgui-params-comet.  Its upstream modules (read from its POM: domain,
# provenance, and process at test scope) are built ONCE, from the sandbox, and
# installed into a PRIVATE overlay repository, _build/param-gate-m2, whose
# every other entry is a symlink into _build/m2repo.  Every Maven run then
# builds the one module alone (`-pl cometgui-params-comet`, no -am) against
# HEAD's upstream jars -- not the shared repository's older ones, which this
# script never writes -- so a control costs one module compile and one narrow
# test selection, and PIT scores HEAD's code against HEAD's dependencies.
# Nothing is damaged upstream, so the -am reason in the sibling (a sibling not
# rebuilt) cannot arise; the overlay's jars are proved to be this sandbox's.
#
# EVERY INJECTION IS PROVED TO HAVE LANDED, IN THE SOURCE AND IN THE BYTECODE.
#   * the anchor must match EXACTLY ONCE, or the run stops as a harness error;
#   * the sandbox file must differ from its pristine copy;
#   * after the dirty run, the compiled form of the damaged file -- Foo.class
#     and every Foo$*.class, or the resource's copy under target/classes --
#     must DIFFER from the clean baseline; an edit that reached the source but
#     not the bytecode tested the clean code, and is a harness error;
#   * and every OTHER file under target/classes must be byte-identical to the
#     baseline, so the red is attributable to this one injection and to
#     nothing a previous control left behind.
#
# AND EVERY RESTORATION IS PROVED IN THE BYTECODE, NOT ONLY IN THE SOURCE.  A
# restored file is copied back, compared and TOUCHED (a copy keeping the
# snapshot's mtime can leave Maven running the injected class), and the next
# run's digest comparison -- ending with the final clean run's -- proves the
# compiled module is the baseline's again.
#
# WHY THE CLEAN RE-RUN IS BATCHED.  As in the sibling: each dirty run proves
# every class but the injected one is byte-identical to the baseline; ONE
# final clean run, over every selector the dirty runs used, must pass with
# every named test executed AND leave the compiled module byte-identical to the
# baseline.  Identical bytecode under identical tests is the baseline's own
# green, proved rather than assumed.
#
# A CLASS SELECTOR IN A LIST WITH A Class#method SELECTOR DROPS @Nested TESTS,
# SILENTLY (measured in Phase 05, surefire 3.5.6).  So every run is checked
# against surefire's XML: a bare class selector is refused for a class with a
# @Nested class, and every method a selector names must appear in the report.
#
# WHAT IT SWITCHES OFF, AND WHY THAT IS NOT A WEAKENING.  Every sandbox Maven
# run passes -Dspotless.check.skip -Dcheckstyle.skip -Dspotbugs.skip
# -Djacoco.skip.  Those are Phase 01's gates with their own harnesses; an
# injection like `if (false)` would otherwise be stopped by Checkstyle before a
# test ran (Phase 06 unit 1's record).  Nothing this script tests is skipped,
# no test is excluded, and every run is checked to have EXECUTED what it named.
# PIT runs with the POM's own configuration (targetClasses, mutators, the 80
# threshold); only its thread count is scaled as scripts/build.sh scales it.
#
# WHAT IT NEEDS.  A built tree: tools/, .venv/, a populated _build/m2repo, and
# the gitignored mirrors scratch/phase05/artefacts (Comet 2026.03.0 and
# 2026.02.2), scratch/phase06/artefacts (Comet 2024.01.0) and the D-006 inputs
# under scratch/fixture.  Offline; writes only under _build/.
#
# WHAT IT COSTS.  Measured and printed per control; the total is on the
# SUMMARY line.  The two PIT runs dominate.
#
# EXIT STATUS
#   0  every control bit
#   1  at least one control failed -- a gate did not bite, a gate failed for
#      the wrong reason, a package fell below 80 %, or the clean tree did not
#      pass again
#   2  misuse (unknown option, unknown control)
#   3  the environment is not ready (no tools/, .venv/, _build/m2repo, mirrors)
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
readonly SANDBOX="${ROOT}/_build/param-gate-sandbox"
readonly PRISTINE="${ROOT}/_build/param-gate-pristine"
readonly OVERLAY="${ROOT}/_build/param-gate-m2"
readonly M2REPO="${ROOT}/_build/m2repo"
readonly LOGS="${ROOT}/_build/param-gate-logs"
readonly MIRROR_2026="scratch/phase05/artefacts/v2026.02.2__comet.linux.exe"
readonly MIRROR_2024="scratch/phase06/artefacts/v2024.01.0__comet.linux.exe"
readonly MIRROR_2026_03="scratch/phase05/artefacts/v2026.03.0__comet.linux.exe"
# The validation corpus's real-binary test searches these (D-006, gitignored).
readonly CORPUS_SPECTRA="scratch/fixture/20100614_Velos1_TaGe_SA_K562_3.mzML"
readonly CORPUS_PROTEOME="scratch/fixture/UP000005640_9606.fasta"

# The one module every control damages and runs.
readonly MODULE="cometgui-params-comet"

# The production files the controls damage.
readonly J="${MODULE}/src/main/java/org/cometgui/params/comet"
readonly WRITER="${J}/writer/CanonicalParamsWriter.java"
readonly DRIFT="${J}/schema/SchemaDrift.java"
readonly TUPLE_CODEC="${J}/value/VariableModCodec.java"
readonly ENZYME_TABLE="${J}/value/EnzymeTable.java"
readonly NUMBERS="${J}/value/Numbers.java"
readonly VALUE_CODEC="${J}/model/ParameterValueCodec.java"
readonly PARSER="${J}/parser/CometParamsParser.java"
readonly VALIDATOR="${J}/validation/CometValidator.java"
readonly RULE="${J}/validation/Rule.java"
readonly PAIR_RULE="${J}/validation/TolerancePairRule.java"
readonly METADATA="${MODULE}/src/main/resources/org/cometgui/params/comet/schema/comet-parameters.json"
# COMET-2026-03 unit 6: the version-scoped classes, and one test resource.
readonly CURATED="${J}/schema/CuratedMetadata.java"
readonly VERSION_RECORD="${J}/schema/CometVersionRecord.java"
readonly FINDINGS="${J}/validation/Findings.java"
readonly SEVERITIES="${J}/validation/VersionSeverities.java"
readonly VARMOD_RULES="${J}/validation/VariableModRules.java"
readonly ASCORE_RULE="${J}/validation/AScoreProRule.java"
readonly MIGRATION="${J}/migration/SchemaMigration.java"
readonly CORPUS="${MODULE}/src/test/resources/fixtures/comet-validation/corpus.json"

# Control 8's harness, unit 7's: invoked, never duplicated.  Its floors are the
# counts it printed when last recorded; fewer means cases were lost.  27 when
# unit 7 shipped it; 55 on 2026-10-04 (COMET-2026-03 unit 6), measured after
# COMET-2026-03 units 1, 3 and 4 added 13 override, 8 alphabet and 7
# severity cases.
readonly PARAMS_SELFTEST="scripts/cometparams_selftest.py"
readonly SELFTEST_GENERATOR_FLOOR=55
readonly SELFTEST_HOOK_FLOOR=4

# Control 9: the packages item 9 names, and the threshold, R-TEST-02's.
readonly PIT_PREFIX="org.cometgui.params.comet"
readonly -a PIT_PACKAGES=(parser writer validation)
readonly PIT_THRESHOLD_X10=800
readonly VALIDATION_TESTS="${MODULE}/src/test/java/org/cometgui/params/comet/validation"

# Phase 01's gates are off in the sandbox; see the header.
readonly -a QUIET=(
    "-Dspotless.check.skip=true"
    "-Dcheckstyle.skip=true"
    "-Dspotbugs.skip=true"
    "-Djacoco.skip=true"
)

# Every control id, in the order they run.  Maven-free first, PIT last.
readonly -a ALL_CONTROLS=(8 1 2a 2b 3a 3b 4a 4b 5a 5b 6a 6b 7a 7b 7c
    v3a v3b v3c v4a v4b v4c v5a v5b v5c v5d v5e v5f v5g v6a v6b 9 H)

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
${SCRIPT_NAME} -- prove the PHASE-06 exit gate items fail on the defects they
exist to catch, and grade item 9 (PIT) per package.

Usage:
  bash scripts/${SCRIPT_NAME}               every control
  bash scripts/${SCRIPT_NAME} --self-test   control H only: the harness must
                                            refuse to report a pass for an
                                            injection that did not land
  bash scripts/${SCRIPT_NAME} --only IDS    the named controls (comma-separated,
                                            from: ${ALL_CONTROLS[*]}) plus the
                                            baseline and the final clean run
  bash scripts/${SCRIPT_NAME} -h|--help

It needs a built tree: tools/, .venv/, a populated _build/m2repo, the
gitignored Comet mirrors ${MIRROR_2026},
${MIRROR_2026_03} and
${MIRROR_2024}, and the D-006 inputs under scratch/fixture.
It runs Maven offline, damages only a git-archive sandbox under _build/, and
writes only under _build/.

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

sha256_of() {
    sha256sum -- "$1" | cut -d' ' -f1
}

# ---------------------------------------------------------- upstream modules --
#
# upstream_modules -- every cometgui module MODULE depends on, transitively and
# at any scope, read from the sandbox's own POMs.
upstream_modules() {
    python3 - "${SANDBOX}" "${MODULE}" <<'PYTHON'
import re
import sys
from pathlib import Path

root, wanted = Path(sys.argv[1]), sys.argv[2]
deps = {}
for pom in root.glob("cometgui-*/pom.xml"):
    module = pom.parent.name
    text = re.sub(r"<parent>.*?</parent>", "", pom.read_text(encoding="utf-8"), flags=re.S)
    deps[module] = sorted(set(re.findall(r"<artifactId>(cometgui-[a-z-]+)</artifactId>", text)) - {module})
if wanted not in deps:
    sys.stderr.write("no module %r in the sandbox\n" % wanted)
    raise SystemExit(1)
seen, todo = set(), list(deps[wanted])
while todo:
    module = todo.pop()
    if module in seen:
        continue
    seen.add(module)
    todo.extend(deps.get(module, []))
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
        || harness_error "the upstream modules of ${MODULE} cannot be read from the sandbox's POMs."
    [ -n "${upstream}" ] || harness_error "${MODULE} has no upstream module at all; the POM read is wrong."
    marker="${LOGS}/upstream-install.marker"
    touch -- "${marker}"
    printf '   upstream of %s: %s\n' "${MODULE}" "${upstream}"
    printf '   mvn -o -pl %s -am install -DskipTests (into %s)\n' "${upstream}" "$(rel "${OVERLAY}")"
    ( cd -- "${SANDBOX}" \
        && mvn -B -o -Dmaven.repo.local="${OVERLAY}" "${QUIET[@]}" \
            -pl "${upstream}" -am install -DskipTests ) >"${log}" 2>&1 || rc=$?
    printf '\n=== MVN EXIT STATUS: %d ===\n' "${rc}" >>"${log}"
    [ "${rc}" -eq 0 ] \
        || harness_error "the sandbox's upstream modules did not build and install into the overlay (exit ${rc}, log: $(rel "${log}"))."
    # Proof the overlay holds THIS sandbox's jars: one per upstream module,
    # written after the marker, inside the overlay's own real directory.
    local module jar
    for module in ${upstream//,/ }; do
        jar="${OVERLAY}/org/cometgui/${module}/0.1.0-SNAPSHOT/${module}-0.1.0-SNAPSHOT.jar"
        [ -s "${jar}" ] && [ "${jar}" -nt "${marker}" ] && [ ! -L "${jar}" ] \
            || harness_error "the overlay has no freshly built ${module} jar at $(rel "${jar}"); ${MODULE} would be built against an older one."
    done
    [ ! -L "${OVERLAY}/org/cometgui" ] \
        || harness_error "the overlay's org/cometgui is a symlink into the shared repository."
    record_pass "upstream ${upstream} built from the sandbox and installed into the private overlay only"
}

# ------------------------------------------------------- bytecode evidence --
#
# class_tree -- "sha256  path" for every file under the module's
# target/classes: compiled classes AND the resources copied there, sorted.
class_tree() {
    local dir="${SANDBOX}/${MODULE}/target/classes"
    [ -d "${dir}" ] || return 0
    ( cd -- "${dir}" && find . -type f -print0 | sort -z | xargs -0 -r sha256sum )
}

readonly BASELINE_TREE="${LOGS}/baseline-classes.sha256"

record_baseline_tree() {
    class_tree >"${BASELINE_TREE}"
    local classes resources
    classes="$(grep -c '\.class$' "${BASELINE_TREE}" || true)"
    resources="$(grep -vc '\.class$' "${BASELINE_TREE}" || true)"
    [ "${classes}" -gt 0 ] \
        || harness_error "the baseline run compiled no class in ${MODULE}, so no clean bytecode can be compared against."
    grep -q '/comet-parameters\.json$' "${BASELINE_TREE}" \
        || harness_error "the baseline's target/classes holds no comet-parameters.json; control 2a's proof would have nothing to compare."
    record_pass "baseline: the clean compiled module is digested (${classes} classes, ${resources} resources)"
}

# compiled_prefix SOURCE -- what the compiled form of one source is called
# under target/classes: ./org/.../Foo for a Java file (Foo.class, Foo$*.class),
# or ./<path> exactly for a resource.
compiled_prefix() {
    local source="$1"
    case "${source}" in
        */src/main/java/*.java)
            local within="${source#*/src/main/java/}"
            printf 'java ./%s' "${within%.java}"
            ;;
        */src/main/resources/*)
            printf 'resource ./%s' "${source#*/src/main/resources/}"
            ;;
        *) harness_error "no compiled form is known for ${source}" ;;
    esac
}

is_compiled_form_of() {
    local kind="$1" prefix="$2" path="$3"
    if [ "${kind}" = "resource" ]; then
        [ "${path}" = "${prefix}" ]
        return
    fi
    [ "${path}" = "${prefix}.class" ] && return 0
    case "${path}" in
        "${prefix}\$"*.class) return 0 ;;
    esac
    return 1
}

# compare_tree LOG [INJECTED SOURCE] -- the compiled module after a run,
# against the baseline.  With an injected source: its compiled form MUST
# differ, and everything else MUST be identical.  Without one: everything must
# be identical.  Anything else is a harness error.
compare_tree() {
    local log="$1" injected="${2:-}"
    [ -s "${BASELINE_TREE}" ] || harness_error "no baseline digest of the compiled module was recorded."
    local now kind="" prefix="" form
    now="$(class_tree)"
    if [ -n "${injected}" ]; then
        form="$(compiled_prefix "${injected}")"
        kind="${form%% *}"
        prefix="${form#* }"
    fi

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
        if [ -n "${prefix}" ] && is_compiled_form_of "${kind}" "${prefix}" "${path}"; then
            count=$((count + 1))
            [ "${before[${path}]:-absent}" != "${after[${path}]:-absent}" ] && changed=$((changed + 1))
            continue
        fi
        if [ "${before[${path}]:-absent}" != "${after[${path}]:-absent}" ]; then
            stray+=("${path#./}")
        else
            others=$((others + 1))
        fi
    done

    if [ "${#stray[@]}" -gt 0 ]; then
        harness_error "$(rel "${log}"): ${#stray[@]} file(s) under ${MODULE}/target/classes differ from the clean baseline although no control damaged them: ${stray[*]:0:5}. A previous restoration did not reach the bytecode, or the build is not reproducible, so this run's result cannot be attributed."
    fi
    if [ -n "${prefix}" ]; then
        [ "${count}" -gt 0 ] \
            || harness_error "$(rel "${log}"): ${MODULE}/target/classes has no compiled form of ${injected} at all."
        [ "${changed}" -gt 0 ] \
            || harness_error "$(rel "${log}"): the compiled form of ${injected} is BYTE-IDENTICAL to the clean baseline after the dirty run. The injection reached the source and not the bytecode, so the run tested the clean code and its result is not evidence of anything."
        printf '   bytecode: %d of %d compiled file(s) of the damaged source changed; %d other file(s) identical to the baseline\n' \
            "${changed}" "${count}" "${others}"
    else
        printf '   bytecode: all %d compiled file(s) identical to the clean baseline\n' "${others}"
    fi
}

# ------------------------------------------------------------ running tests --

# run_mvn LOG SELECTORS -- the one test command shape in this script: the
# module alone, against the overlay.  The exit status is written INTO the log.
run_mvn() {
    local log="$1" selectors="$2" rc=0
    rm -rf -- "${SANDBOX}/${MODULE}/target/surefire-reports"
    ( cd -- "${SANDBOX}" \
        && mvn -B -o -Dmaven.repo.local="${OVERLAY}" "${QUIET[@]}" \
            -pl "${MODULE}" test \
            -Dtest="${selectors}" -Dsurefire.failIfNoSpecifiedTests=false ) \
        >"${log}" 2>&1 || rc=$?
    printf '\n=== MVN EXIT STATUS: %d ===\n' "${rc}" >>"${log}"
    return "${rc}"
}

gate_command() {
    printf 'mvn -o -pl %s test -Dtest=%s' "${MODULE}" "$1"
}

# The outer class of a selector: Foo, Foo#bar, Foo$Inner#bar are all Foo.
selector_class() {
    local selector="${1%%#*}"
    printf '%s' "${selector%%\$*}"
}

test_source_of() {
    local class="$1" found
    found="$(cd -- "${SANDBOX}" && find "${MODULE}/src/test/java" -name "${class}.java" -type f | head -2)"
    [ -n "${found}" ] \
        || harness_error "no test class ${class}.java exists in the sandbox. A control naming a test that does not exist tests nothing."
    [ "$(printf '%s\n' "${found}" | wc -l)" -eq 1 ] \
        || harness_error "the test class name ${class} is ambiguous in the sandbox: ${found}"
    printf '%s' "${found}"
}

# testcase_outcome CLASS METHOD -- passed, failed, or absent, from the XML
# report of the outer class (surefire writes @Nested and dynamic tests there).
testcase_outcome() {
    local class="$1" method="$2"
    python3 - "${SANDBOX}/${MODULE}/target/surefire-reports" "${class}" "${method}" <<'PYTHON'
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
        report="$(find "${SANDBOX}/${MODULE}/target/surefire-reports" -maxdepth 1 \
            -name "TEST-*.${class}.xml" 2>/dev/null | head -1 || true)"
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
                harness_error "the selector ${selector} names a class with @Nested tests, and surefire drops those from a class selector in a list without a word. Name the nested class and its methods (Outer\$Inner#method)."
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
    case "${source}" in
        */src/test/resources/*)
            # A damaged TEST resource (control v5g's data): no production class
            # may change, and the test class path must hold the damaged bytes.
            compare_tree "${log}"
            assert_test_resource_reached "${label}" "${source}"
            ;;
        *) compare_tree "${log}" "${source}" ;;
    esac
    verify_classes_ran "${log}" "${selectors}"
    USED_SELECTORS+=("${selectors}")
}

# assert_test_resource_reached LABEL SOURCE -- a test resource is not compiled,
# so the bytecode proof cannot see it.  Its copy on the test class path
# (target/test-classes) is what the tests read: it must DIFFER from the
# pristine copy and EQUAL the damaged source.  Anything else is a harness
# error -- the run read something other than the defect.
assert_test_resource_reached() {
    local label="$1" source="$2"
    local within="${source#*/src/test/resources/}"
    local copy="${SANDBOX}/${MODULE}/target/test-classes/${within}"
    [ -f "${copy}" ] \
        || harness_error "(${label}) the test class path holds no copy of ${source} at $(rel "${copy}"), so nothing shows the tests read it."
    [ -e "${PRISTINE}/${source}" ] \
        || harness_error "(${label}) no pristine copy of ${source} was taken, so nothing can be compared."
    if cmp -s "${copy}" "${PRISTINE}/${source}"; then
        harness_error "(${label}) the test class path's copy of ${source} is BYTE-IDENTICAL to the pristine copy. The damage reached the source and not what the tests read, so the run tested the clean data and its result is not evidence of anything."
    fi
    cmp -s "${copy}" "${SANDBOX}/${source}" \
        || harness_error "(${label}) the test class path's copy of ${source} is neither the pristine nor the damaged file; the run's input cannot be attributed."
    printf '   test class path: %s holds the damaged bytes (sha256 %s); every compiled class identical to the baseline\n' \
        "${within}" "$(sha256_of "${copy}" | cut -c1-12)"
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

# remove_parameter LABEL NAME -- deletes one parameter's object from the
# sandbox's metadata JSON, as text, so that the diff is that object and nothing
# else.  The object must occur exactly once, and the result must still be JSON
# with exactly one parameter fewer and none of that name -- or it is a harness
# error, because an edit that removed nothing (or broke the file) tests
# something other than the control says.
remove_parameter() {
    local label="$1" name="$2" rc=0
    python3 - "${SANDBOX}/${METADATA}" "${name}" <<'PYTHON' || rc=$?
import json
import sys

path, name = sys.argv[1:3]
with open(path, encoding="utf-8") as handle:
    text = handle.read()
before = json.loads(text)
opening = '    {\n      "name": "%s",\n' % name
if text.count(opening) != 1:
    sys.stderr.write("the parameter %r opens %d time(s), expected once\n" % (name, text.count(opening)))
    raise SystemExit(1)
start = text.index(opening)
end = text.index("\n    }", start) + len("\n    }")
if text.startswith(",\n", end):
    # Not the last object: remove it, its comma and its line end.
    edited = text[:start] + text[end + 2:]
else:
    # The last object: remove the comma and line end that precede it.
    edited = text[:text.rindex(",", 0, start)] + text[end:]
after = json.loads(edited)
names_before = [p["name"] for p in before["parameters"]]
names_after = [p["name"] for p in after["parameters"]]
if len(names_after) != len(names_before) - 1 or name in names_after:
    sys.stderr.write("the edit did not remove exactly %r\n" % name)
    raise SystemExit(1)
if [n for n in names_before if n != name] != names_after:
    sys.stderr.write("the edit changed more than %r\n" % name)
    raise SystemExit(1)
with open(path, "w", encoding="utf-8") as handle:
    handle.write(edited)
PYTHON
    if [ "${rc}" -ne 0 ]; then
        harness_error "(${label}) removing ${name} from the sandbox metadata did not land. A control whose defect was not injected tests nothing."
    fi
}

# set_parameter_through LABEL NAME RELEASE -- ends one parameter's version range
# at RELEASE in the sandbox's metadata JSON, as text, so that the diff is that
# one field.  The parameter must open exactly once, its span must hold
# "through": null exactly once, and the result must still be JSON in which
# that field, and nothing else, changed -- or it is a harness error.
set_parameter_through() {
    local label="$1" name="$2" release="$3" rc=0
    python3 - "${SANDBOX}/${METADATA}" "${name}" "${release}" <<'PYTHON' || rc=$?
import json
import sys

path, name, release = sys.argv[1:4]
with open(path, encoding="utf-8") as handle:
    text = handle.read()
before = json.loads(text)
opening = '    {\n      "name": "%s",\n' % name
if text.count(opening) != 1:
    sys.stderr.write("the parameter %r opens %d time(s), expected once\n" % (name, text.count(opening)))
    raise SystemExit(1)
start = text.index(opening)
end = text.index("\n    }", start) + len("\n    }")
span = text[start:end]
old = '"through": null'
if span.count(old) != 1:
    sys.stderr.write("%r's object holds %r %d time(s), expected once\n" % (name, old, span.count(old)))
    raise SystemExit(1)
edited = text[:start] + span.replace(old, '"through": "%s"' % release) + text[end:]
after = json.loads(edited)
names = [p["name"] for p in before["parameters"]]
index = names.index(name)
if after["parameters"][index]["versions"]["through"] != release:
    sys.stderr.write("the edit did not set %r's range to end at %s\n" % (name, release))
    raise SystemExit(1)
after["parameters"][index]["versions"]["through"] = None
if after != before:
    sys.stderr.write("the edit changed more than %r's range\n" % name)
    raise SystemExit(1)
with open(path, "w", encoding="utf-8") as handle:
    handle.write(edited)
PYTHON
    if [ "${rc}" -ne 0 ]; then
        harness_error "(${label}) ending ${name}'s range at ${release} in the sandbox metadata did not land. A control whose defect was not injected tests nothing."
    fi
}

# clear_corpus_lines LABEL CASE RELEASE -- in the sandbox's validation corpus,
# empties the Warning and Error lines recorded for one release's run of one
# case: a recorded BINARY verdict made wrong, and nothing else.  The file is
# rewritten in the one form it is kept in (2-space JSON); the original must
# already be in that form, the case and verdict must exist once, and the lines
# must not already be empty -- or it is a harness error.
clear_corpus_lines() {
    local label="$1" kase="$2" release="$3" rc=0
    python3 - "${SANDBOX}/${CORPUS}" "${kase}" "${release}" <<'PYTHON' || rc=$?
import json
import sys

path, kase, release = sys.argv[1:4]
with open(path, encoding="utf-8") as handle:
    text = handle.read()
data = json.loads(text)
if json.dumps(data, indent=2, ensure_ascii=False) + "\n" != text:
    sys.stderr.write("the corpus is not in the form this edit rewrites; the diff would not be one field\n")
    raise SystemExit(1)
cases = [c for c in data["cases"] if c["id"] == kase]
if len(cases) != 1:
    sys.stderr.write("the case %r occurs %d time(s), expected once\n" % (kase, len(cases)))
    raise SystemExit(1)
verdicts = [v for v in cases[0]["verdicts"] if v["version"] == release]
if len(verdicts) != 1:
    sys.stderr.write("%r has %d verdict(s) for %s, expected one\n" % (kase, len(verdicts), release))
    raise SystemExit(1)
if not verdicts[0]["lines"]:
    sys.stderr.write("%r's %s verdict records no line; emptying it would change nothing\n" % (kase, release))
    raise SystemExit(1)
verdicts[0]["lines"] = []
with open(path, "w", encoding="utf-8") as handle:
    handle.write(json.dumps(data, indent=2, ensure_ascii=False) + "\n")
PYTHON
    if [ "${rc}" -ne 0 ]; then
        harness_error "(${label}) emptying ${kase}'s recorded ${release} lines in the sandbox corpus did not land. A control whose defect was not injected tests nothing."
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

# java_control_inject LABEL SOURCE SELECTORS MODE EXPECTED OLD NEW -- the
# common shape: damage one production file once, run the narrowest selector,
# require the red with its own words.  The caller then makes any further
# assertions on DIRTY_LOG and restores the file.
DIRTY_LOG=""
java_control_inject() {
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
    ln -s -- "${ROOT}/tools" "${SANDBOX}/tools"
    [ -f "${SANDBOX}/tools/env.sh" ] \
        || harness_error "the sandbox's tools/ symlink does not resolve. Every control would fail for the wrong reason."
    ln -s -- "${ROOT}/.venv" "${SANDBOX}/.venv"
    ln -s -- "${ROOT}/scratch" "${SANDBOX}/scratch"
    [ -f "${SANDBOX}/${MIRROR_2026}" ] && [ -f "${SANDBOX}/${MIRROR_2024}" ] \
        && [ -f "${SANDBOX}/${MIRROR_2026_03}" ] && [ -f "${SANDBOX}/${CORPUS_SPECTRA}" ] \
        || harness_error "the sandbox's scratch/ symlink does not resolve to the three Comet mirrors and the corpus inputs."
    local head dirty
    head="$(cd -- "${ROOT}" && git rev-parse --short HEAD)"
    echo "Sandbox: $(rel "${SANDBOX}") (git archive ${head}, $(find "${SANDBOX}/${MODULE}/src" -name '*.java' -type f | wc -l) java files in ${MODULE})"
    dirty="$(cd -- "${ROOT}" && git status --porcelain -- 'cometgui-*/src' scripts docs pom.xml 'cometgui-*/pom.xml' | head -20)"
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
readonly SEL_1="CanonicalWriterTest\$DoubleRoundTrip#textUnchanged+secondRoundTripIsIdentical+secondParseIsTheSameModel+valuesAsComet"
readonly SEL_2A="SchemaDriftFixtureTest#theDriftTest+theCountsFor202602"
readonly SEL_2B="SchemaDriftTest#defaultDiffersInAPartialDump"
readonly SEL_3="VariableModRoundTripTest#everyFormInEverySlot"
readonly SEL_4A="EnzymeTableCodecTest\$Custom#mayNotReuseANumber,CometParamsParserTest\$Errors#duplicateEnzymeNumber"
readonly SEL_4B="CanonicalWriterTest\$Enzymes#refusesAnAbsentNumber+refusesARemovedRow"
readonly SEL_5A="LocaleIndependenceTest#commaDecimalLocalesWriteTheSameText,CommaLocaleWriterTest#byteIdenticalUnderCommaLocales,CanonicalWriterTest\$DoubleRoundTrip#textUnchanged"
readonly SEL_5B="CommaLocaleWriterTest#byteIdenticalUnderCommaLocales,CanonicalWriterTest\$DoubleRoundTrip#textUnchanged"
readonly SEL_6A="CanonicalWriterTest\$Unknown#survive+reported"
readonly SEL_6B="CanonicalWriterTest\$Unknown#survive+reported,CometParamsParserTest\$Unknown#keptAndReported"
readonly SEL_7A="TolerancePairRuleTest"
readonly SEL_7B="TolerancePairRuleTest#asymmetric"
readonly SEL_7C="TolerancePairRuleTest#boundaries+reversed"
readonly SEL_H="${SEL_1}"
# COMET-2026-03.  A test that must STAY GREEN (the release the defect does not
# touch) is selected beside the one that must go red.
readonly SEL_V3A="Comet202603CurationTest#minusOneIsNotA202602Choice+indexSearchTypeIsVersionScoped"
readonly SEL_V3B="Comet202603CurationTest#indexSearchTypeIsVersionScoped,SchemaDriftFixtureTest#theCountsFor202603+theCountsFor202602"
readonly SEL_V3C="SchemaDriftFixtureTest#theDriftTest+theCountsFor202603+theCountsFor202602"
readonly SEL_V4A="VariableModCodecAlphabetTest#versionScoped,ReleaseWriterGateTest#proteinTerminusSlots"
readonly SEL_V4B="VariableModCodecAlphabetTest#versionScoped,VariableModRoundTripTest#everyFormInEverySlotOfComet202603"
readonly SEL_V4C="ReleaseWriterGateTest#gateItem1"
readonly SEL_V5A="VersionScopedRulesTest\$Distance#severityByRelease,VersionScopedRulesTest\$IndexSearchType#warnsForTheNewerRelease,ValidationCorpusTest#theValidatorAgrees"
readonly SEL_V5B="ResidueAlphabetRuleTest#proteinNTerminus"
readonly SEL_V5C="AScoreProRuleTest#slotsAboveNine"
readonly SEL_V5D="AScoreProRuleTest#mergedSlots"
readonly SEL_V5E="AScoreProRuleTest#proteinTerminusRewrite"
readonly SEL_V5F="VersionScopedRulesTest\$IndexSearchType#silentForTheOlderRelease+warnsForTheNewerRelease"
readonly SEL_V5G="ValidationCorpusRealBinaryTest#replay"
readonly SEL_V6A="MigrationTo202603Test\$Keyed#sameRelease+otherTarget+backToOlder"
readonly SEL_V6B="MigrationTo202603Test\$Edits#distanceBelowMinusTwo+terminusOutsideZeroToThree"

control_selectors() {
    case "$1" in
        1) printf '%s' "${SEL_1}" ;;
        2a) printf '%s' "${SEL_2A}" ;;
        2b) printf '%s' "${SEL_2B}" ;;
        3a|3b) printf '%s' "${SEL_3}" ;;
        4a) printf '%s' "${SEL_4A}" ;;
        4b) printf '%s' "${SEL_4B}" ;;
        5a) printf '%s' "${SEL_5A}" ;;
        5b) printf '%s' "${SEL_5B}" ;;
        6a) printf '%s' "${SEL_6A}" ;;
        6b) printf '%s' "${SEL_6B}" ;;
        7a) printf '%s' "${SEL_7A}" ;;
        7b) printf '%s' "${SEL_7B}" ;;
        7c) printf '%s' "${SEL_7C}" ;;
        H) printf '%s' "${SEL_H}" ;;
        v3a) printf '%s' "${SEL_V3A}" ;;
        v3b) printf '%s' "${SEL_V3B}" ;;
        v3c) printf '%s' "${SEL_V3C}" ;;
        v4a) printf '%s' "${SEL_V4A}" ;;
        v4b) printf '%s' "${SEL_V4B}" ;;
        v4c) printf '%s' "${SEL_V4C}" ;;
        v5a) printf '%s' "${SEL_V5A}" ;;
        v5b) printf '%s' "${SEL_V5B}" ;;
        v5c) printf '%s' "${SEL_V5C}" ;;
        v5d) printf '%s' "${SEL_V5D}" ;;
        v5e) printf '%s' "${SEL_V5E}" ;;
        v5f) printf '%s' "${SEL_V5F}" ;;
        v5g) printf '%s' "${SEL_V5G}" ;;
        v6a) printf '%s' "${SEL_V6A}" ;;
        v6b) printf '%s' "${SEL_V6B}" ;;
        8|9) ;;
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
    # Control 9 needs a compiled clean module to digest even when it is the
    # only control selected; any selector compiles it.
    BASELINE_SELECTORS="$(printf '%s\n' "${selectors[@]}" | tr ',' '\n' | awk 'NF && !seen[$0]++' | paste -sd, -)"
    [ -n "${BASELINE_SELECTORS}" ] || BASELINE_SELECTORS="${SEL_2B}"
}

# --------------------------------------------------------------- control 0 --

control_baseline() {
    begin_control "0" "baseline: the overlay is built and the undamaged sandbox passes every selector"
    build_overlay
    local log="${LOGS}/0-baseline.log" rc=0
    printf '   %s\n' "$(gate_command "${BASELINE_SELECTORS}")"
    run_mvn "${log}" "${BASELINE_SELECTORS}" || rc=$?
    if [ "${rc}" -ne 0 ]; then
        record_fail "baseline: the undamaged sandbox does NOT pass the controls' selectors (exit ${rc}, log: $(rel "${log}")). Nothing below could be attributed to an injection."
        printf '         %s\n' "$(grep -m3 -E '^\[ERROR\] +[A-Za-z]' "${log}" | cut -c1-200 || true)"
        end_control
        return 1
    fi
    verify_classes_ran "${log}" "${BASELINE_SELECTORS}"
    record_pass "baseline: exit 0, every selector executed ($(printf '%s\n' "${BASELINE_SELECTORS}" | tr ',' '\n' | grep -c .) selectors)"
    record_baseline_tree
    end_control
}

# ------------------------------------------------------------- the controls --

control_1() {
    begin_control "1" "item 1 [recorded, unit 4, orchestrator]: the writer skips every empty-valued parameter"
    # Unit 4's sign-off, injection 1: "the writer silently skips every
    # parameter whose value text is empty -- peff_obo, mass_offsets...
    # dropped".  The canonical text's SHA-256 is pinned by textUnchanged since
    # unit 4's rework, and the second parse must give the same model.
    java_control_inject "empty-valued parameters skipped" "${WRITER}" "${SEL_1}" fixed \
        'expected: <f381afe1d48749d49a0bb5e97a0375be7e691f3c6bfa2f740e96589b4502d62b> but was: <185bce304644fbb7d07d1a166b0a9b094b0325ffa199e3ee7c96a2de4266f38a>' \
        '            String value = model.codec().format(entry.definition(), entry.value());' \
        '            String value = model.codec().format(entry.definition(), entry.value());
            if (value.isEmpty()) {
                continue;
            }'
    assert_testcase "and the second parse is not the first model" failed \
        CanonicalWriterTest secondParseIsTheSameModel
    assert_log_matches "and peff_obo is among the declarations no longer written" \
        "${DIRTY_LOG}" 'valuesAsComet expected: <\[database_name, decoy_search, index_search_type, num_threads, peff_format, peff_obo, '
    restore_pristine "${WRITER}"
    end_control
}

control_2a() {
    begin_control "2a" "item 2 [recorded, unit 2]: an entry removed from the shipped metadata"
    # Unit 2's agent removed an entry from a copy of the metadata JSON and saw
    # the drift test name it UNMODELLED (the unit-8 brief's summary).  Here the
    # copy is the sandbox's own resource, so the module that ships it is the
    # module that is tested, and its compiled copy under target/classes must
    # change.  scan_range is the entry the record names.
    save_pristine "${METADATA}"
    remove_parameter "scan_range removed" "scan_range"
    assert_modified "scan_range removed" "${METADATA}"
    DIRTY_LOG="${LOGS}/${CONTROL_ID}-dirty.log"
    printf '   %s\n' "$(gate_command "${SEL_2A}")"
    dirty_run "scan_range removed" "${METADATA}" "${SEL_2A}" "${DIRTY_LOG}"
    grade_red regex "scan_range removed" "${DIRTY_RC}" "${DIRTY_LOG}" \
        'UNMODELLED: Comet 2026\.02\.2 declares scan_range \(line [0-9]+, default "0 0"\), which has no metadata'
    assert_log_contains "and the counts say 117 modelled of 118 declared" \
        "${DIRTY_LOG}" 'expected: <[118, 118, 0]> but was: <[118, 117, 0]>'
    restore_pristine "${METADATA}"
    end_control
}

control_2b() {
    begin_control "2b" "item 2 [recorded, unit 2, orchestrator]: the drift default comparison reduced to the first token"
    # Unit 2's sign-off, injection 1, second form (the first, `index < 1`, went
    # red for the wrong reason and was not counted).  The work log calls this
    # THIN: one constructed test is all that guards a later-token default.
    java_control_inject "drift compares the first token only" "${DRIFT}" "${SEL_2B}" fixed \
        'PARTIAL_DISCOVERY: declared 10, modelled 9, allow-listed 1, findings 0 ==> expected: <1> but was: <0>' \
        '        for (int index = 0; index < left.length; index++) {' \
        '        for (int index = 0; index < Math.min(1, left.length); index++) {'
    restore_pristine "${DRIFT}"
    end_control
}

control_3a() {
    begin_control "3a" "item 3 [recorded, unit 3, agent]: only the first neutral loss written"
    java_control_inject "second neutral loss dropped" "${TUPLE_CODEC}" "${SEL_3}" fixed \
        'two neutral losses ==> expected: <79.966331 STY 0 3 -1 0 0 97.976896,79.966331> but was: <79.966331 STY 0 3 -1 0 0 97.976896>' \
        '                yield String.join(",", losses.stream().map(Numbers::text).toList());' \
        '                yield Numbers.text(losses.get(0));'
    assert_log_contains "and the everything-at-once form in the same slots" \
        "${DIRTY_LOG}" 'everything at once: group, min,max, terminal, required, two losses ==> expected: <79.966331 nSTc 3 1,2 5 2 1 97.976896,79.966331> but was: <79.966331 nSTc 3 1,2 5 2 1 97.976896>'
    restore_pristine "${TUPLE_CODEC}"
    end_control
}

control_3b() {
    begin_control "3b" "item 3 [recorded, unit 3, orchestrator]: a min,max count written max,min"
    # Unit 3's sign-off, injection 1: 30 round-trip failures, two forms in
    # each of the fifteen slots.
    java_control_inject "min,max written max,min" "${TUPLE_CODEC}" "${SEL_3}" fixed \
        'min,max count ==> expected: <79.966331 STY 0 2,4 -1 0 0 0.0> but was: <79.966331 STY 0 4,2 -1 0 0 0.0>' \
        '                yield value.minimumCount().getAsInt() + "," + maximum;' \
        '                yield maximum + "," + value.minimumCount().getAsInt();'
    assert_log_contains "and in all fifteen slots: two forms each, 30 failures" \
        "${DIRTY_LOG}" 'Tests run: 255, Failures: 30, Errors: 0'
    restore_pristine "${TUPLE_CODEC}"
    end_control
}

control_4a() {
    begin_control "4a" "item 4 [recorded, unit 3, agent]: the enzyme table's duplicate-number invariant disabled"
    # The table refuses two rows with one number; Comet would silently use the
    # later one.  The parser has a check of its own, with its own message, so
    # its test is selected too and must STAY GREEN: this control removes the
    # TABLE's invariant, the one a custom enzyme added in the model meets.
    java_control_inject "duplicate enzyme numbers accepted" "${ENZYME_TABLE}" "${SEL_4A}" fixed \
        'EnzymeTableCodecTest.mayNotReuseANumber Unexpected exception type thrown, expected: <java.lang.IllegalArgumentException> but was: <org.opentest4j.AssertionFailedError>' \
        '            for (int earlier = 0; earlier < index; earlier++) {' \
        '            for (int earlier = 0; earlier < 0; earlier++) {'
    # The refusal never came, and the inner assertion shows why: the table
    # now holds Lys_C as number 3 AND the custom MyLysC as number 3.
    assert_log_matches "and the table holds two rows numbered 3" \
        "${DIRTY_LOG}" 'EnzymeDefinition\[number=3, name=Lys_C, .*EnzymeDefinition\[number=3, name=MyLysC, '
    assert_testcase "the parser's own duplicate check stays green: the injection is the table's" passed \
        CometParamsParserTest duplicateEnzymeNumber
    restore_pristine "${ENZYME_TABLE}"
    end_control
}

control_4b() {
    begin_control "4b" "item 4 [recorded, unit 4, agent]: the writer emits an enzyme number absent from its table"
    java_control_inject "writer refusal disabled" "${WRITER}" "${SEL_4B}" fixed \
        'CanonicalWriterTest.refusesAnAbsentNumber search_enzyme_number ==> Expected org.cometgui.params.comet.writer.ParamsWriteException to be thrown, but nothing was thrown.' \
        '            if (!table.contains(number)) {' \
        '            if (false) {'
    assert_testcase "and removing the row a parameter names is no longer refused" failed \
        CanonicalWriterTest refusesARemovedRow
    restore_pristine "${WRITER}"
    end_control
}

control_5a() {
    begin_control "5a" "item 5 [recorded, unit 3, agent]: the one number writer made locale-sensitive"
    # String.format with the scale BigDecimal carries writes exactly
    # toPlainString's digits under Locale.ROOT and a decimal comma under de-DE:
    # the plausible defect.  So the ROOT text test is selected and must STAY
    # GREEN -- the red is the locale, nothing else.
    java_control_inject "Numbers.text follows the default locale" "${NUMBERS}" "${SEL_5A}" regex \
        'model 0 written under de_DE: the bytes differ first at offset [0-9]+' \
        '        return value.toPlainString();' \
        '        return String.format("%." + Math.max(0, value.scale()) + "f", value);'
    assert_log_matches "and the value package's own locale test" \
        "${DIRTY_LOG}" 'LocaleIndependenceTest\.commaDecimalLocalesWriteTheSameText'
    assert_testcase "under Locale.ROOT the canonical text is unchanged: the red is the locale" passed \
        CanonicalWriterTest textUnchanged
    restore_pristine "${NUMBERS}"
    end_control
}

control_5b() {
    begin_control "5b" "item 5 [recorded, unit 4, agent]: the writer's decimal path made locale-sensitive"
    # Unit 4's agent's form was a NumberFormat, seen to differ first at offset
    # 1076 writing 20,0 (the brief).  Same defect at the model codec, the
    # writer's own call site; ROOT must stay green.
    java_control_inject "the decimal value path follows the default locale" "${VALUE_CODEC}" "${SEL_5B}" fixed \
        'model 0 written under de_DE: the bytes differ first at offset 1076' \
        '            case ParameterValue.Decimal decimal -> Numbers.text(decimal.value());' \
        '            case ParameterValue.Decimal decimal ->
                    String.format("%." + Math.max(0, decimal.value().scale()) + "f", decimal.value());'
    assert_log_contains "and what it wrote there is 20,0" \
        "${DIRTY_LOG}" 'got "tide_mass_tolerance_upper = 20,0    # upper bound of the pre"'
    assert_testcase "under Locale.ROOT the canonical text is unchanged: the red is the locale" passed \
        CanonicalWriterTest textUnchanged
    restore_pristine "${VALUE_CODEC}"
    end_control
}

control_6a() {
    begin_control "6a" "item 6 [recorded, unit 4, agent]: the writer drops the unknown parameters"
    java_control_inject "unknown section never written" "${WRITER}" "${SEL_6A}" fixed \
        'expected: <[ms1_mass_range, precursor_NL_ions]> but was: <[]>' \
        '        if (!unknowns.isEmpty()) {' \
        '        if (false) {'
    assert_testcase "and the value, comment and comment line do not survive" failed \
        CanonicalWriterTest survive
    restore_pristine "${WRITER}"
    end_control
}

control_6b() {
    begin_control "6b" "item 6 [recorded, unit 4, agent]: the parser drops unknown parameters, still warning"
    # The parser still issues its UNKNOWN_PARAMETER warning -- the plausible
    # half-defect: reported, then dropped.  The first parse's report is right;
    # the model holds nothing, so nothing is written back.
    java_control_inject "unknowns reported and dropped" "${PARSER}" "${SEL_6B}" fixed \
        'expected: <[ms1_mass_range, precursor_NL_ions]> but was: <[]>' \
        '            unknowns.add(unknown);' \
        '            // INJECTED by verify-param-gates.sh: the unknown is reported and dropped.'
    assert_testcase "and the parser's own test sees the model hold none" failed \
        CometParamsParserTest keptAndReported
    restore_pristine "${PARSER}"
    end_control
}

control_7a() {
    begin_control "7a" "item 7 [recorded, unit 5, agent]: the tolerance pair graded by the generic ordering rule"
    # Unit 5's agent's injection (a): "routes it there and 14 tests fail".
    # They fail as ERRORS, through a PRODUCT GUARD, not a test assertion: the
    # generic rule refuses a value that is not a two-value range, naming the
    # pair member and its kind.  Recorded as such, as unit 6's invariant reds
    # were; the grade is that guard's own sentence, inside the test that
    # exists to say the generic rule never grades the pair.
    java_control_inject "pair routed through the generic rule" "${VALIDATOR}" "${SEL_7A}" regex \
        'TolerancePairRuleTest\.lambda\$genericRuleNeverFires\$[0-9]+:[0-9]+->window:[0-9]+ . IllegalState peptide_mass_tolerance_upper names the ordered_range validator but is of kind TOLERANCE_PAIR_MEMBER, not a two-value range' \
        '        rules.put(ValidatorId.SIGNED_TOLERANCE_PAIR, TolerancePairRule::check);' \
        '        rules.put(ValidatorId.SIGNED_TOLERANCE_PAIR, OrderedRangeRule::check);'
    assert_log_contains "and every test that validates a window errs: 14 of the class's 15" \
        "${DIRTY_LOG}" 'Tests run: 15, Failures: 0, Errors: 14, Skipped: 0'
    assert_testcase "and a reversed window is no longer the pair rule's error" failed \
        TolerancePairRuleTest reversed
    assert_testcase "while the metadata test, which reads no window, stays green" passed \
        TolerancePairRuleTest metadata
    restore_pristine "${VALIDATOR}"
    end_control
}

control_7b() {
    begin_control "7b" "item 7 [recorded, unit 5, agent]: a deliberate asymmetric window made an error"
    java_control_inject "asymmetric is an error" "${RULE}" "${SEL_7B}" regex \
        'TolerancePairRuleTest\.asymmetric:[0-9]+ expected: <WARNING> but was: <ERROR>' \
        '            "signed_tolerance_pair.asymmetric",
            Severity.WARNING,' \
        '            "signed_tolerance_pair.asymmetric",
            Severity.ERROR,'
    restore_pristine "${RULE}"
    end_control
}

control_7c() {
    begin_control "7c" "item 7 [recorded, unit 5, orchestrator]: the reversed-pair error can never fire"
    # Unit 5's sign-off, injection 1: 20.0001 / 20 is then graded same-signed.
    java_control_inject "reversed never fires" "${PAIR_RULE}" "${SEL_7C}" regex \
        'TolerancePairRuleTest\.boundaries:[0-9]+->assertPair:[0-9]+ Finding\[rule=PAIR_SAME_SIGNED, .*peptide_mass_tolerance_lower = 20\.0001 and peptide_mass_tolerance_upper = 20' \
        '        if (pair.lower().compareTo(pair.upper()) > 0) {' \
        '        if (pair.lower().compareTo(pair.upper()) > 1) {'
    assert_testcase "and 20 / -20 is no longer reversed" failed TolerancePairRuleTest reversed
    restore_pristine "${PAIR_RULE}"
    end_control
}

# ------------------------------------------- COMET-2026-03: version-blind --
#
# Each control below makes one version-scoped fact VERSION-BLIND.  The record
# is handoffs/COMET-2026-03-worklog.rst; see the header for which are recorded
# and which are this script's own.

control_v3a() {
    begin_control "v3a" "COMET-2026-03 item 3 [recorded, u1 orch.]: every release given the first record's overrides"
    # Unit 1's sign-off, injection 1: "CuratedMetadata.forVersion reads the
    # first version record -- 2026.03.0 -- whatever version is asked",
    # "-1 became legal for 2026.02.2".
    java_control_inject "the first record's overrides for every release" "${CURATED}" "${SEL_V3A}" regex \
        'Comet202603CurationTest\.minusOneIsNotA202602Choice:[0-9]+ \[\] ==> expected: <1> but was: <0>' \
        '        return version(version)
                .flatMap(record -> record.override(definition.name()))' \
        '        return versions.stream()
                .findFirst()
                .flatMap(record -> record.override(definition.name()))'
    assert_log_matches "and 2026.02.2's index_search_type default became 2026.03.0's" \
        "${DIRTY_LOG}" 'Comet202603CurationTest\.indexSearchTypeIsVersionScoped:[0-9]+ .*expected: <1> but was: <-1>'
    restore_pristine "${CURATED}"
    end_control
}

control_v3b() {
    begin_control "v3b" "COMET-2026-03 item 3 [NEW, u6]: no release's override is applied"
    # The release's own default (-1) is what makes 2026.03.0's -q drift-clean:
    # without its override the curated 1 is drift there.  2026.02.2 has no
    # override, so its counts must stay green.
    java_control_inject "overrides never applied" "${CURATED}" "${SEL_V3B}" regex \
        'Comet202603CurationTest\.indexSearchTypeIsVersionScoped:[0-9]+ .*expected: <-1> but was: <1>' \
        '                .map(override -> override.applyTo(definition))' \
        '                .map(override -> definition)'
    assert_testcase "and 2026.03.0's drift test is no longer clean" failed \
        SchemaDriftFixtureTest theCountsFor202603
    assert_testcase "while 2026.02.2, which has no override, stays drift-clean: the red is the version" passed \
        SchemaDriftFixtureTest theCountsFor202602
    restore_pristine "${CURATED}"
    end_control
}

control_v3c() {
    begin_control "v3c" "COMET-2026-03 item 3 [NEW, u6]: scan_range no longer claimed for 2026.03.0 in the shipped metadata"
    # The drift test runs per release.  An entry missing for one release only
    # must be named for that release, and the other must stay clean: a drift
    # test that ran for 2026.02.2 alone would pass this.
    save_pristine "${METADATA}"
    set_parameter_through "scan_range ends at 2026.02.2" "scan_range" "2026.02.2"
    assert_modified "scan_range ends at 2026.02.2" "${METADATA}"
    DIRTY_LOG="${LOGS}/${CONTROL_ID}-dirty.log"
    printf '   %s\n' "$(gate_command "${SEL_V3C}")"
    dirty_run "scan_range ends at 2026.02.2" "${METADATA}" "${SEL_V3C}" "${DIRTY_LOG}"
    grade_red regex "scan_range ends at 2026.02.2" "${DIRTY_RC}" "${DIRTY_LOG}" \
        'UNMODELLED: Comet 2026\.03\.0 declares scan_range \(line [0-9]+, default "0 0"\), which has no metadata'
    assert_log_matches "and the 2026.03.0 counts say 117 modelled of 118 declared" \
        "${DIRTY_LOG}" 'SchemaDriftFixtureTest\.theCountsFor202603:[0-9]+ Comet 2026\.03\.0 COMPLETE: declared 118, modelled 117, allow-listed 0, findings 1$'
    assert_testcase "while 2026.02.2's counts stay green: the red is the version" passed \
        SchemaDriftFixtureTest theCountsFor202602
    restore_pristine "${METADATA}"
    end_control
}

control_v4a() {
    begin_control "v4a" "COMET-2026-03 item 4 [NEW, u6]: every release's codec given 2026.03.0's residue alphabet"
    # Unit 3's sign-off injection 1 gave every release the newest LAYOUT; this
    # is that defect narrowed to the alphabet, the one fact it was about.
    java_control_inject "the newest alphabet for every release" "${TUPLE_CODEC}" "${SEL_V4A}" regex \
        'VariableModCodecAlphabetTest\.versionScoped:[0-9]+ Expected org\.cometgui\.params\.comet\.value\.ValueSyntaxException to be thrown, but nothing was thrown' \
        '        return new VariableModCodec("Comet " + version.text(), record.variableModTuple(), slots);' \
        '        VariableModLayout tuple = record.variableModTuple();
        return new VariableModCodec(
                "Comet " + version.text(),
                new VariableModLayout(
                        tuple.fields(),
                        tuple.source(),
                        metadata.versions().get(0).variableModTuple().residueAlphabet()),
                slots);'
    assert_testcase "and the writer gate no longer refuses ^ and \$ for 2026.02.2" failed \
        ReleaseWriterGateTest proteinTerminusSlots
    restore_pristine "${TUPLE_CODEC}"
    end_control
}

control_v4b() {
    begin_control "v4b" "COMET-2026-03 item 4 [NEW, u6]: every release's codec given 2024.01.0's residue alphabet"
    java_control_inject "the oldest alphabet for every release" "${TUPLE_CODEC}" "${SEL_V4B}" regex \
        'holds .\^., which Comet 2026\.03\.0 does not accept in a residue token' \
        '        return new VariableModCodec("Comet " + version.text(), record.variableModTuple(), slots);' \
        '        VariableModLayout tuple = record.variableModTuple();
        return new VariableModCodec(
                "Comet " + version.text(),
                new VariableModLayout(
                        tuple.fields(),
                        tuple.source(),
                        metadata.versions()
                                .get(metadata.versions().size() - 1)
                                .variableModTuple()
                                .residueAlphabet()),
                slots);'
    assert_testcase "and the 2026.03.0 alphabet test fails too" failed \
        VariableModCodecAlphabetTest versionScoped
    restore_pristine "${TUPLE_CODEC}"
    end_control
}

control_v4c() {
    begin_control "v4c" "COMET-2026-03 item 4 [NEW, u6]: the writer writes the curated inline comment, not the release's"
    # Gate item 1 for 2026.03.0: its canonical text's SHA-256 is pinned.  The
    # release's inline comments (decoy_search, index_search_type) are its
    # override; the curated ones are 2026.02.2's, so 2026.02.2 stays green.
    java_control_inject "inline comment read from the curated definition" "${WRITER}" "${SEL_V4C}" regex \
        'Comet 2026\.03\.0 ==> expected: <c600c64f473ec46baaa760c7b1f55c78faa196dd96c36748da06c8ea9d5fcf2e> but was: <[0-9a-f]{64}>' \
        '            line(out, declaration(entry.name(), value, entry.definition().inlineComment()));' \
        '            line(
                    out,
                    declaration(
                            entry.name(),
                            value,
                            model.metadata()
                                    .parameter(entry.name())
                                    .orElseThrow()
                                    .inlineComment()));'
    assert_log_contains "and only one of the two releases fails" \
        "${DIRTY_LOG}" 'Tests run: 2, Failures: 1, Errors: 0, Skipped: 0'
    restore_pristine "${WRITER}"
    end_control
}

control_v5a() {
    begin_control "v5a" "COMET-2026-03 item 5 [recorded, u4 orch. 1]: every model judged with 2026.02.2's severities"
    # Unit 4's sign-off, injection 1: "Findings binds every model to the
    # second version record, 2026.02.2", with the corpus text below.
    java_control_inject "severities of the second record for every model" "${FINDINGS}" "${SEL_V5A}" fixed \
        'ist-1, Comet 2026.03.0: ValidationReport[findings=[]] ==> expected: <[WARNING index_search_type.ignored_without_idx]> but was: <[]>' \
        '        this.severities =
                VersionSeverities.of(model.metadata().version(model.version()).orElseThrow());' \
        '        this.severities = VersionSeverities.of(model.metadata().versions().get(1));'
    assert_testcase "and below -2 is no longer an error for 2026.03.0" failed \
        VersionScopedRulesTest severityByRelease
    assert_testcase "and 2026.03.0's index_search_type warning is gone" failed \
        VersionScopedRulesTest warnsForTheNewerRelease
    restore_pristine "${FINDINGS}"
    end_control
}

control_v5b() {
    begin_control "v5b" "COMET-2026-03 item 5 [NEW, u6]: validation reads the newest release's residue alphabet"
    # The codec refuses ^ for 2026.02.2 when a file is read or written; a model
    # built in code (Phase 07's editor) holds it until then, and validation must
    # say so at the field (unit 3's condition on unit 4).
    java_control_inject "the newest alphabet in validation" "${VARMOD_RULES}" "${SEL_V5B}" regex \
        'ResidueAlphabetRuleTest\.proteinNTerminus:[0-9]+ .*expected: <1> but was: <0>' \
        '        return model.metadata()
                .version(model.version())
                .orElseThrow()
                .variableModTuple()
                .residueAlphabet();' \
        '        return model.metadata().versions().get(0).variableModTuple().residueAlphabet();'
    restore_pristine "${VARMOD_RULES}"
    end_control
}

control_v5c() {
    begin_control "v5c" "COMET-2026-03 item 5 [recorded, u4 orch. 2]: AScorePro's 'localise all' suppresses the slot error"
    java_control_inject "ascore == 0 became ascore <= 0" "${ASCORE_RULE}" "${SEL_V5C}" regex \
        'AScoreProRuleTest\.slotsAboveNine.*print_ascorepro_score, -1\]: \[\] ==> expected: <1> but was: <0>' \
        '        if (ascore == 0) {' \
        '        if (ascore <= 0) {'
    restore_pristine "${ASCORE_RULE}"
    end_control
}

control_v5d() {
    begin_control "v5d" "COMET-2026-03 item 5 [NEW, u6]: AScorePro applied to slots Comet merges away"
    java_control_inject "no slot is ever merged" "${ASCORE_RULE}" "${SEL_V5D}" regex \
        'AScoreProRuleTest\.mergedSlots.* expected: <\[\]> but was: <\[' \
        '            boolean merged = key.requirement() != -1 && !seen.add(key);' \
        '            boolean merged = false;'
    restore_pristine "${ASCORE_RULE}"
    end_control
}

control_v5e() {
    begin_control "v5e" "COMET-2026-03 item 5 [NEW, u6]: AScorePro's protein-terminus rewrite in a release without ^"
    # Comet 2026.03.0 rewrites n at distance 0 from the protein N-terminus to ^
    # before it merges; 2026.02.2 has no ^ and does not.  The rule reads that
    # from the release's alphabet: without the check, 2026.02.2 merges too.
    java_control_inject "the rewrite whatever the alphabet" "${ASCORE_RULE}" "${SEL_V5E}" regex \
        'AScoreProRuleTest\.proteinTerminusRewrite:[0-9]+->flagged:[0-9]+ 2026\.2\.2 \[variable_mod01, 42\.010565 n 0 1 0 0 0 0\.0, variable_mod10, 42\.010565 n 0 1 -1 0 0 0\.0\]: \[\] ==> expected: <1> but was: <0>' \
        '        if (!alphabet.accepts(to)) {
            return false;
        }' \
        '        if (false) {
            return false;
        }'
    restore_pristine "${ASCORE_RULE}"
    end_control
}

control_v5f() {
    begin_control "v5f" "COMET-2026-03 item 5 [NEW, u6]: index_search_type.ignored_without_idx in every release"
    java_control_inject "the index rule warns whatever the release" "${SEVERITIES}" "${SEL_V5F}" regex \
        'VersionScopedRulesTest\.silentForTheOlderRelease expected: <\[\]> but was: <\[Finding\[rule=INDEX_SEARCH_TYPE_IGNORED, severity=WARNING, parameters=\[index_search_type, database_name\]' \
        '        return rule.fixedSeverity().or(() -> scoped.get(rule));' \
        '        if (rule == Rule.INDEX_SEARCH_TYPE_IGNORED) {
            return Optional.of(Severity.WARNING);
        }
        return rule.fixedSeverity().or(() -> scoped.get(rule));'
    assert_testcase "while 2026.03.0's own warning stays green: the red is the version" passed \
        VersionScopedRulesTest warnsForTheNewerRelease
    restore_pristine "${SEVERITIES}"
    end_control
}

control_v5g() {
    begin_control "v5g" "COMET-2026-03 item 5 [NEW, u6, DATA]: one recorded binary verdict of the corpus made wrong"
    # NOT PRODUCTION CODE.  The validation corpus records what each real binary
    # did with each case; ValidationCorpusRealBinaryTest replays every case on
    # both binaries and runs no production class of this module (unit 4's
    # rework).  Here the recorded 2026.03.0 warning of ist-1 is removed, as if
    # whoever recorded it had misread the binary: the real binary must
    # contradict the record.  Proved on the test class path, not in bytecode.
    save_pristine "${CORPUS}"
    clear_corpus_lines "ist-1's 2026.03.0 warning unrecorded" "ist-1" "2026.03.0"
    assert_modified "ist-1's 2026.03.0 warning unrecorded" "${CORPUS}"
    DIRTY_LOG="${LOGS}/${CONTROL_ID}-dirty.log"
    printf '   %s\n' "$(gate_command "${SEL_V5G}")"
    dirty_run "ist-1's 2026.03.0 warning unrecorded" "${CORPUS}" "${SEL_V5G}" "${DIRTY_LOG}"
    grade_red regex "ist-1's 2026.03.0 warning unrecorded" "${DIRTY_RC}" "${DIRTY_LOG}" \
        'ist-1, Comet 2026\.03\.0: Warning and Error lines ==> expected: <\[\]> but was: <\[Warning - index_search_type = 1 is ignored: "[^"]*" is not an \.idx file'
    assert_log_contains "and that case alone: one of the corpus's 42 cases fails" \
        "${DIRTY_LOG}" 'Tests run: 42, Failures: 1, Errors: 0, Skipped: 0'
    restore_pristine "${CORPUS}"
    end_control
}

control_v6a() {
    begin_control "v6a" "COMET-2026-03 item 6 [recorded, u5 orch. A]: value migrations applied whatever release they are from"
    java_control_inject "valueMigrationsFrom ignores from" "${VERSION_RECORD}" "${SEL_V6A}" fixed \
        'MigrationTo202603Test.sameRelease expected: <118> but was: <116>' \
        '        return valueMigrations.stream().filter(m -> m.from().equals(from)).toList();' \
        '        return valueMigrations.stream().toList();'
    restore_pristine "${VERSION_RECORD}"
    end_control
}

control_v6b() {
    begin_control "v6b" "COMET-2026-03 item 6 [recorded, u5 orch. B]: rule-keyed value migrations never applied"
    java_control_inject "conversion handed no source findings" "${MIGRATION}" "${SEL_V6B}" fixed \
        'MigrationTo202603Test.distanceBelowMinusTwo 2026.02.2 ==> expected: <CONVERTED> but was: <CARRIED>' \
        '                        conversion.convert(
                                name, source.text(name), sourceRules.getOrDefault(name, Set.of()));' \
        '                        conversion.convert(name, source.text(name), Set.of());'
    assert_log_matches "and a terminus outside 0-3 is carried instead of needing attention" \
        "${DIRTY_LOG}" 'terminusOutsideZeroToThree.*expected: <NEEDS_ATTENTION> but was: <CARRIED>'
    restore_pristine "${MIGRATION}"
    end_control
}

# ---------------------------------------------------------------- control 8 --

control_8() {
    begin_control "8" "item 8 [recorded, unit 7]: the generated reference's own self-test, invoked in the sandbox"
    # NOT DUPLICATED.  scripts/cometparams_selftest.py damages copies of the
    # generator's inputs and of the generator, and drives the real Sphinx
    # builder-inited hook in a project copy; it grades each case on its own
    # diagnostic and reports an injection that did not land as a HARNESS
    # FAILURE.  This control runs it from the sandbox, over the sandbox, and
    # grades it on more than its exit status.
    local log="${LOGS}/8-cometparams-selftest.log" rc=0 modelled
    rm -rf -- "${SANDBOX}/_build/cometparams-selftest"
    printf '   python3 %s --root <sandbox> --work <sandbox>/_build/cometparams-selftest\n' "${PARAMS_SELFTEST}"
    ( cd -- "${SANDBOX}" && "${ROOT}/.venv/bin/python" "${SANDBOX}/${PARAMS_SELFTEST}" \
        --root "${SANDBOX}" --work "${SANDBOX}/_build/cometparams-selftest" \
        --sphinx "${ROOT}/.venv/bin/sphinx-build" ) >"${log}" 2>&1 || rc=$?
    printf '\n=== SELFTEST EXIT STATUS: %d ===\n' "${rc}" >>"${log}"
    if [ "${rc}" -ne 0 ]; then
        record_fail "8 ${PARAMS_SELFTEST} failed (exit ${rc}, log: $(rel "${log}"))"
        printf '         %s\n' "$(grep -m2 -E 'HARNESS|FAIL|Error' "${log}" | cut -c1-200 || true)"
        end_control
        return
    fi
    local generator hook
    generator="$(sed -n 's/^cometparams-selftest: OK -- \([0-9][0-9]*\) damaged input(s) rejected by the generator and \([0-9][0-9]*\) defect(s) failing the documentation build.*/\1/p' "${log}")"
    hook="$(sed -n 's/^cometparams-selftest: OK -- \([0-9][0-9]*\) damaged input(s) rejected by the generator and \([0-9][0-9]*\) defect(s) failing the documentation build.*/\2/p' "${log}")"
    if [ -z "${generator}" ] || [ -z "${hook}" ]; then
        record_fail "8 the self-test exited 0 without its OK line; it cannot be shown to have graded anything (log: $(rel "${log}"))"
    elif [ "${generator}" -lt "${SELFTEST_GENERATOR_FLOOR}" ] || [ "${hook}" -lt "${SELFTEST_HOOK_FLOOR}" ]; then
        record_fail "8 the self-test graded ${generator} generator case(s) and ${hook} hook defect(s), fewer than unit 7's ${SELFTEST_GENERATOR_FLOOR} and ${SELFTEST_HOOK_FLOOR}: cases were lost"
    else
        record_pass "8 the self-test passes: ${generator} damaged inputs rejected by the generator and ${hook} defects failing the real documentation build, each on its own diagnostic (floors ${SELFTEST_GENERATOR_FLOOR} and ${SELFTEST_HOOK_FLOOR})"
    fi
    # Gate item 8's second half, "covers every modelled parameter": the
    # metadata's own count, read here independently, must be what the
    # generator rendered and what the hook counted in the built HTML.
    modelled="$(python3 -c 'import json,sys; print(len(json.load(open(sys.argv[1], encoding="utf-8"))["parameters"]))' "${SANDBOX}/${METADATA}")"
    assert_log_matches "8 the generator renders one entry per modelled parameter (${modelled})" \
        "${log}" "renders: ${modelled} parameter entries for ${modelled} modelled parameters"
    assert_log_matches "8 through the real hook: ${modelled} entries logged and ${modelled} sections in the strict HTML build" \
        "${log}" "hook-clean +builds clean; hook logged ${modelled} entries, .*; ${modelled} parameter sections in the HTML"
    assert_log_contains "8 an entry removed from the metadata is refused by the generator, naming it" \
        "${log}" 'declares "num_threads", which the metadata'
    assert_log_matches "8 and fails the documentation build through the hook" \
        "${log}" 'hook-entry-removed +build failed \(exit [1-9][0-9]*\): .*comet-parameters\.json: comet -q of Comet 2026\.02\.2'
    assert_log_matches "8 and a generator that drops an entry after its own check fails on the hook's independent count" \
        "${log}" "hook-entry-dropped-after-check +build failed .*holds $((modelled - 1)) parameter entries, but the metadata models ${modelled} parameters"
    # COMET-2026-03 unit 6: the self-test's per-release controls, which a
    # generator that ignored the version records' overrides or alphabets fails.
    assert_log_matches "8 per release: index_search_type's entry carries each release's own default, choices and comment" \
        "${log}" '^ +control +rendered for 2026\.02\.2, 2026\.03\.0: index_search_type carries each release.s own default, choices and comment$'
    assert_log_matches "8 per release: variable_mod01's entry states ^ and \$ for 2026.03.0 only" \
        "${log}" '^ +control +variable_mod01 states each release.s residue alphabet: \^ and \$ for 2026\.03\.0 only$'
    end_control
}

# ---------------------------------------------------------------- control 9 --
#
# PIT, scored as scripts/build.sh scores it, per package.

pit_threads() {
    local cores threads
    cores="$(nproc 2>/dev/null || echo 4)"
    threads=$(( cores / 2 ))
    [ "${threads}" -gt 16 ] && threads=16
    [ "${threads}" -lt 4 ] && threads=4
    printf '%s' "${threads}"
}

PIT_RC=0
run_pit() {
    local log="$1" threads
    threads="$(pit_threads)"
    PIT_RC=0
    rm -rf -- "${SANDBOX}/${MODULE}/target/pit-reports"
    printf '   mvn -o -pl %s -Dcometgui.pit.threads=%s test-compile org.pitest:pitest-maven:mutationCoverage\n' "${MODULE}" "${threads}"
    ( cd -- "${SANDBOX}" \
        && mvn -B -o -Dmaven.repo.local="${OVERLAY}" "${QUIET[@]}" \
            -Dcometgui.pit.threads="${threads}" \
            -pl "${MODULE}" test-compile org.pitest:pitest-maven:mutationCoverage ) \
        >"${log}" 2>&1 || PIT_RC=$?
    printf '\n=== MVN EXIT STATUS: %d ===\n' "${PIT_RC}" >>"${log}"
    if grep -qE 'COMPILATION ERROR|Compilation failure' "${log}"; then
        harness_error "the sandbox did not compile for PIT -- see $(rel "${log}")."
    fi
    [ -s "${SANDBOX}/${MODULE}/target/pit-reports/mutations.xml" ] \
        || harness_error "PIT wrote no mutations.xml (exit ${PIT_RC}, log: $(rel "${log}")); nothing can be scored."
}

# score_pit XML -- prints, for each graded package and the module, a line
#   GRADE <name> <killed> <total> <score x10> OK|BELOW|VACUOUS
# and then every non-killed mutant in the graded packages.  The arithmetic is
# scripts/build.sh's: status='KILLED' over every <mutation> element, integer
# per mille.
score_pit() {
    python3 - "$1" "${PIT_PREFIX}" "${PIT_THRESHOLD_X10}" "${PIT_PACKAGES[@]}" <<'PYTHON'
import sys
import xml.etree.ElementTree as ET

path, prefix, threshold = sys.argv[1], sys.argv[2], int(sys.argv[3])
packages = sys.argv[4:]
mutations = list(ET.parse(path).getroot().iter("mutation"))


def package_of(mutation):
    cls = mutation.findtext("mutatedClass", "")
    return cls.rsplit(".", 1)[0] if "." in cls else ""


def grade(name, chosen):
    total = len(chosen)
    killed = sum(1 for m in chosen if m.get("status") == "KILLED")
    if total == 0:
        print("GRADE %s 0 0 0 VACUOUS" % name)
        return
    x10 = killed * 1000 // total
    print("GRADE %s %d %d %d %s" % (name, killed, total, x10, "OK" if x10 >= threshold else "BELOW"))


for package in packages:
    grade(package, [m for m in mutations if package_of(m) == prefix + "." + package])
grade("module", mutations)

others = sorted({package_of(m) for m in mutations} - {prefix + "." + p for p in packages})
for other in others:
    chosen = [m for m in mutations if package_of(m) == other]
    killed = sum(1 for m in chosen if m.get("status") == "KILLED")
    print("INFO %s %d/%d (not graded per package here; inside the module's score)" % (other, killed, len(chosen)))

for package in packages:
    for m in mutations:
        if package_of(m) != prefix + "." + package or m.get("status") == "KILLED":
            continue
        print("NONKILLED %-9s %-11s %s:%s %s -- %s" % (
            package,
            m.get("status"),
            m.findtext("mutatedClass"),
            m.findtext("lineNumber"),
            m.findtext("mutator", "").rsplit(".", 1)[-1],
            m.findtext("description", "")))
PYTHON
}

# grade_pit LABEL XML WANT-VALIDATION -- records one verdict per graded line.
# WANT-VALIDATION is OK for the clean arm and BELOW for the negative arm.
grade_pit() {
    local label="$1" xml="$2" want_validation="$3"
    local out line name killed total x10 verdict pct
    out="$(score_pit "${xml}")" || harness_error "the PIT report $(rel "${xml}") could not be read."
    if [ "${want_validation}" = "BELOW" ]; then
        # The negative arm's list is the removed tests' work, not a finding.
        printf '   %s non-killed mutant(s) in parser, writer and validation with the validation tests removed (listed in the clean arm only)\n' \
            "$(printf '%s\n' "${out}" | grep -c '^NONKILLED' || true)"
    else
        printf '   every mutant PIT did not kill in %s -- read each against handoffs/PHASE-06-worklog.rst\n' "${PIT_PACKAGES[*]}"
        printf '   (TIMED_OUT is not counted as killed, as scripts/build.sh does not count it):\n'
        printf '%s\n' "${out}" | grep '^NONKILLED' | sed 's/^NONKILLED /     not killed: /' || printf '     none\n'
    fi
    printf '%s\n' "${out}" | grep '^INFO' | sed 's/^INFO /   info: /' || true
    while read -r line; do
        set -- ${line}
        name="$2" killed="$3" total="$4" x10="$5" verdict="$6"
        pct="$((x10 / 10)).$((x10 % 10))%"
        if [ "${verdict}" = "VACUOUS" ]; then
            record_fail "${label}: ${name} has NO mutation in $(rel "${xml}"); a score over nothing is not a score"
            continue
        fi
        if [ "${name}" = "validation" ] && [ "${want_validation}" = "BELOW" ]; then
            if [ "${verdict}" = "BELOW" ]; then
                record_pass "${label}: validation is graded BELOW the 80% threshold: ${killed}/${total} = ${pct}"
            else
                record_fail "${label}: validation scored ${killed}/${total} = ${pct} with its tests removed, and the per-package grade did not fall below 80%"
            fi
            continue
        fi
        if [ "${want_validation}" = "BELOW" ]; then
            printf '   %-10s %d/%d = %s (%s)\n' "${name}" "${killed}" "${total}" "${pct}" "${verdict}"
            continue
        fi
        if [ "${verdict}" = "OK" ]; then
            record_pass "${label}: ${name} ${killed}/${total} mutations killed = ${pct} >= 80%"
        else
            record_fail "${label}: ${name} ${killed}/${total} mutations killed = ${pct}, BELOW the R-TEST-02 threshold of 80%"
        fi
    done < <(printf '%s\n' "${out}" | grep '^GRADE')
    PIT_GRADES="$(printf '%s\n' "${out}" | grep '^GRADE')"
}

PIT_GRADES=""
control_9() {
    begin_control "9" "item 9 [recorded, units 2 and 6]: PIT per package -- parser, writer, validation and the module"
    local log="${LOGS}/9-pit-clean.log" xml="${SANDBOX}/${MODULE}/target/pit-reports/mutations.xml"
    run_pit "${log}"
    if [ "${PIT_RC}" -ne 0 ]; then
        record_fail "9 PIT on the clean sandbox exited ${PIT_RC} (log: $(rel "${log}")); its own 80% module threshold or its run failed"
    else
        record_pass "9 PIT on the clean sandbox exits 0 with the POM's own configuration"
    fi
    # PIT compiled the module: it must have scored the baseline's bytecode.
    compare_tree "${log}"
    grade_pit "9" "${xml}" OK
    cp -- "${xml}" "${LOGS}/9-mutations-clean.xml"

    # The negative arm: every validation test class removed, source AND
    # compiled class, so PIT cannot run what was taken away.
    printf '\n   negative arm: every *Test.java under %s removed\n' "$(rel "${SANDBOX}/${VALIDATION_TESTS}")"
    local -a removed=()
    local file
    while IFS= read -r file; do
        removed+=("${file#"${SANDBOX}/"}")
    done < <(find "${SANDBOX}/${VALIDATION_TESTS}" -maxdepth 1 -name '*Test.java' -type f | sort)
    [ "${#removed[@]}" -gt 0 ] \
        || harness_error "no validation test class was found to remove; the negative arm would remove nothing."
    for file in "${removed[@]}"; do
        save_pristine "${file}"
        rm -f -- "${SANDBOX}/${file}"
    done
    local compiled="${SANDBOX}/${MODULE}/target/test-classes/org/cometgui/params/comet/validation"
    for file in "${removed[@]}"; do
        file="$(basename -- "${file}" .java)"
        rm -f -- "${compiled}/${file}.class" "${compiled}/${file}\$"*.class
    done
    printf '   removed %d test class(es): %s\n' "${#removed[@]}" \
        "$(printf '%s\n' "${removed[@]}" | xargs -n1 basename | sed 's/\.java$//' | paste -sd' ' -)"
    log="${LOGS}/9-pit-negative.log"
    run_pit "${log}"
    printf '   PIT exit status with the validation tests removed: %d\n' "${PIT_RC}"
    # Proof the removal reached what PIT ran: no compiled class of a removed
    # test exists, and no mutant names a removed test as its killer.
    for file in "${removed[@]}"; do
        file="$(basename -- "${file}" .java)"
        [ ! -e "${compiled}/${file}.class" ] \
            || harness_error "the removed test ${file} was compiled again; the negative arm did not remove it."
    done
    local killers
    killers="$(grep -oE 'org\.cometgui\.params\.comet\.validation\.[A-Za-z]+Test' "${xml}" | sort -u | paste -sd' ' - || true)"
    [ -z "${killers}" ] \
        || harness_error "mutations in the negative arm were killed by removed tests (${killers}); the removal did not reach PIT."
    record_pass "9 negative arm: ${#removed[@]} validation test classes removed, none compiled, none named as a killing test"
    compare_tree "${log}"
    grade_pit "9 negative" "${xml}" BELOW
    cp -- "${xml}" "${LOGS}/9-mutations-negative.xml"
    local module_line
    module_line="$(printf '%s\n' "${PIT_GRADES}" | grep '^GRADE module ' || true)"
    printf '   (the module-wide score -- the only number scripts/build.sh grades -- in the negative arm: %s)\n' \
        "$(printf '%s' "${module_line}" | awk '{printf "%d/%d = %d.%d%%, %s", $3, $4, $5/10, $5%10, $6}')"
    for file in "${removed[@]}"; do
        restore_pristine "${file}"
    done
    # The restoration of TEST code is proved by running it: every restored
    # class, selected by class alone (no Class#method in this list, so no
    # @Nested test is dropped), must compile, run at least one test and pass,
    # and the main bytecode must still be the baseline's.
    local selectors
    selectors="$(printf '%s\n' "${removed[@]}" | xargs -n1 basename | sed 's/\.java$//' | paste -sd, -)"
    log="${LOGS}/9-restored-tests.log"
    printf '   %s\n' "$(gate_command "${selectors}")"
    local rc=0 class report count
    run_mvn "${log}" "${selectors}" || rc=$?
    if [ "${rc}" -ne 0 ]; then
        record_fail "9 the restored validation tests do not pass again (exit ${rc}, log: $(rel "${log}"))"
    else
        for class in ${selectors//,/ }; do
            report="${SANDBOX}/${MODULE}/target/surefire-reports/TEST-${PIT_PREFIX}.validation.${class}.xml"
            count="$(sed -n 's/.*<testsuite [^>]*tests="\([0-9][0-9]*\)".*/\1/p' "${report}" 2>/dev/null | head -1)"
            [ -n "${count}" ] && [ "${count}" -ge 1 ] \
                || harness_error "the restored test ${class} did not run (log: $(rel "${log}")); the negative arm's restoration is not proved."
        done
        record_pass "9 every restored validation test class compiles, runs and passes again (${#removed[@]} classes)"
    fi
    compare_tree "${log}"
    # The final clean run executes the pair-rule tests again too.
    USED_SELECTORS+=("${SEL_7A}")
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
        record_fail "${label}: HARNESS FAILURE -- it did not record exactly one failure. Every other control in this script is unreliable."
    fi
}

readonly H_ANCHOR='            String value = model.codec().format(entry.definition(), entry.value());'

h_same_text() {
    replace_once "H1" "${WRITER}" "${H_ANCHOR}" "${H_ANCHOR}"
    assert_modified "H1" "${WRITER}"
}

h_no_anchor() {
    replace_once "H2" "${WRITER}" 'this text is in no source file' 'nor is this'
}

h_metadata_no_change() {
    save_pristine "${METADATA}"
    remove_parameter "H3" "no_such_parameter"
}

h_source_not_bytecode() {
    replace_once "H4" "${WRITER}" "${H_ANCHOR}" \
        "${H_ANCHOR} // H4: the source changed and the bytecode cannot"
    assert_modified "H4" "${WRITER}"
    dirty_run "H4" "${WRITER}" "${SEL_H}" "${LOGS}/H4-comment-only.log"
}

h_vacuous_pit_report() {
    local xml="${LOGS}/H7-mutations.xml"
    cat >"${xml}" <<'XML'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<mutations>
<mutation detected='true' status='KILLED' numberOfTestsRun='1'><sourceFile>A.java</sourceFile><mutatedClass>org.cometgui.params.comet.parser.A</mutatedClass><mutatedMethod>a</mutatedMethod><methodDescription>()V</methodDescription><lineNumber>1</lineNumber><mutator>org.pitest.mutationtest.engine.gregor.mutators.VoidMethodCallMutator</mutator><description>constructed for control H</description></mutation>
<mutation detected='true' status='KILLED' numberOfTestsRun='1'><sourceFile>B.java</sourceFile><mutatedClass>org.cometgui.params.comet.validation.B</mutatedClass><mutatedMethod>b</mutatedMethod><methodDescription>()V</methodDescription><lineNumber>1</lineNumber><mutator>org.pitest.mutationtest.engine.gregor.mutators.VoidMethodCallMutator</mutator><description>constructed for control H</description></mutation>
</mutations>
XML
    grade_pit "H7 (deliberately graded: a report with no writer mutation)" "${xml}" OK
}

# The sandbox's corpus damaged, and NOT copied to the test class path: what a
# run that skipped process-test-resources would leave.  Runs in a subshell
# (expect_harness_error), so the edit is undone by H's restoration.
h_test_resource_not_copied() {
    clear_corpus_lines "H10" "ist-1" "2026.03.0"
    assert_modified "H10" "${CORPUS}"
    assert_test_resource_reached "H10" "${CORPUS}"
}

control_H() {
    begin_control "H" "the harness itself: an injection that did not land must never be a pass"
    save_pristine "${WRITER}"

    expect_harness_error "H1 an injection whose replacement equals its anchor is refused" \
        "byte-identical to the pristine copy" h_same_text
    expect_harness_error "H2 an anchor that matches nothing is refused" \
        "the injection anchor is gone" h_no_anchor
    cmp -s "${SANDBOX}/${WRITER}" "${PRISTINE}/${WRITER}" \
        || harness_error "control H's refused injections changed ${WRITER} after all."
    expect_harness_error "H3 a metadata removal that removes nothing is refused" \
        "did not land" h_metadata_no_change
    cmp -s "${SANDBOX}/${METADATA}" "${PRISTINE}/${METADATA}" \
        || harness_error "control H's refused metadata edit changed ${METADATA} after all."

    # A comment on the anchor's own line moves no line number, so javac writes
    # the same class: assert_modified accepts it -- the source did change --
    # and the bytecode check must refuse it.  A real Maven run.
    expect_harness_error "H4 an injection that changed the source but not the bytecode is refused" \
        "BYTE-IDENTICAL to the clean baseline after the dirty run" h_source_not_bytecode

    # H4's run tested the clean code; graded as a red, the grader must record a
    # failure rather than a pass.
    local log="${LOGS}/H4-comment-only.log" rc
    [ -s "${log}" ] || harness_error "H4 left no log, so H5 has nothing to grade."
    rc="$(sed -n 's/^=== MVN EXIT STATUS: \([0-9]*\) ===$/\1/p' "${log}" | tail -1)"
    [ "${rc}" = "0" ] \
        || harness_error "H4's run of the clean code did not pass (exit ${rc:-unknown}); H5 needs a green run to grade."
    expect_recorded_failure "H5 a control run with NO defect injected is recorded as a failure, not a pass" \
        grade_red fixed "H5 (deliberately graded with no defect injected)" "${rc}" "${log}" \
        'expected: <f381afe1d48749d49a0bb5e97a0375be7e691f3c6bfa2f740e96589b4502d62b> but was:'

    log="${LOGS}/H6-wrong-reason.log"
    printf '[ERROR] Tests run: 1, Failures: 1\n[ERROR]   SomeOtherTest.somethingElse:1 expected: <1> but was: <2>\n' >"${log}"
    expect_recorded_failure "H6 a red WITHOUT the expected diagnostic is recorded as a failure, not a pass" \
        grade_red fixed "H6 (deliberately graded against the wrong reason)" 1 "${log}" \
        'expected: <f381afe1d48749d49a0bb5e97a0375be7e691f3c6bfa2f740e96589b4502d62b> but was:'

    # The PIT grader: a package with no mutation at all is not a 100% score.
    expect_recorded_failure "H7 a PIT report in which a graded package has no mutation is a failure, not a score" \
        h_vacuous_pit_report

    # COMET-2026-03 unit 6: the new controls' own plumbing.  Neither edit may
    # write anything, and a damaged test resource that never reached the test
    # class path must stop the run -- no Maven run is needed to show it: the
    # baseline left the clean copy there.
    expect_harness_error "H8 a metadata range edit naming a parameter that does not exist is refused" \
        "did not land" set_parameter_through "H8" "no_such_parameter" "2026.02.2"
    cmp -s "${SANDBOX}/${METADATA}" "${PRISTINE}/${METADATA}" \
        || harness_error "control H8's refused metadata edit changed ${METADATA} after all."
    save_pristine "${CORPUS}"
    expect_harness_error "H9 a corpus verdict edit that would change nothing is refused" \
        "did not land" clear_corpus_lines "H9" "ist-1" "2026.02.2"
    cmp -s "${SANDBOX}/${CORPUS}" "${PRISTINE}/${CORPUS}" \
        || harness_error "control H9's refused corpus edit changed ${CORPUS} after all."
    expect_harness_error "H10 a test resource damaged in the source but not on the test class path is refused" \
        "BYTE-IDENTICAL to the pristine copy" h_test_resource_not_copied

    restore_pristine "${WRITER}"
    restore_pristine "${METADATA}"
    restore_pristine "${CORPUS}"
    USED_SELECTORS+=("${SEL_H}")
    end_control
}

# ---------------------------------------------------------------- the main --

run_control() {
    case "$1" in
        1) control_1 ;; 2a) control_2a ;; 2b) control_2b ;;
        3a) control_3a ;; 3b) control_3b ;; 4a) control_4a ;; 4b) control_4b ;;
        5a) control_5a ;; 5b) control_5b ;; 6a) control_6a ;; 6b) control_6b ;;
        7a) control_7a ;; 7b) control_7b ;; 7c) control_7c ;;
        8) control_8 ;; 9) control_9 ;; H) control_H ;;
        v3a) control_v3a ;; v3b) control_v3b ;; v3c) control_v3c ;;
        v4a) control_v4a ;; v4b) control_v4b ;; v4c) control_v4c ;;
        v5a) control_v5a ;; v5b) control_v5b ;; v5c) control_v5c ;; v5d) control_v5d ;;
        v5e) control_v5e ;; v5f) control_v5f ;; v5g) control_v5g ;;
        v6a) control_v6a ;; v6b) control_v6b ;;
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
        record_pass "the clean sandbox passes again: exit 0, every selector the dirty runs used executed"
    fi
    compare_tree "${log}"
    local file
    for file in $(printf '%s\n' "${RESTORED[@]}" | grep '/src/main/' | sort -u); do
        record_pass "restored compiled form of ${file##*/} is byte-identical to the clean baseline"
    done
    # A restored TEST resource is not in the bytecode proof: its copy on the
    # test class path must be the pristine file again, or the clean run above
    # read the damage.
    local copy
    for file in $(printf '%s\n' "${RESTORED[@]}" | grep '/src/test/resources/' | sort -u); do
        copy="${SANDBOX}/${MODULE}/target/test-classes/${file#*/src/test/resources/}"
        cmp -s "${copy}" "${PRISTINE}/${file}" \
            || harness_error "the test class path's copy of ${file} is not the pristine file after the final clean run; the restoration did not reach what the tests read."
        record_pass "restored test-class-path copy of ${file##*/} is byte-identical to the pristine file"
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
    [ -x "${ROOT}/.venv/bin/sphinx-build" ] || die ".venv/bin/sphinx-build is missing; control 8 needs the project's Sphinx." 3
    local mirror
    for mirror in "${MIRROR_2026}" "${MIRROR_2024}" "${MIRROR_2026_03}"; do
        [ -f "${ROOT}/${mirror}" ] \
            || die "${mirror} does not exist. ${MODULE}'s real-binary tests run it and FAIL rather than skip without it, so PIT (control 9) would score a failing suite. The mirror is gitignored; refill it from the release URL and verify its SHA-256 (manifests/tools.json for 2026.03.0 and 2026.02.2; handoffs/PHASE-06-worklog.rst, unit 6, for 2024.01.0)." 3
    done
    local input
    for input in "${CORPUS_SPECTRA}" "${CORPUS_PROTEOME}"; do
        [ -f "${ROOT}/${input}" ] \
            || die "${input} does not exist. ${MODULE}'s validation-corpus test searches it with the real Comet binaries and FAILS rather than skips without it, so control v5g and PIT (control 9) could not run. The inputs are gitignored (D-006); refill them with python3 scripts/feasibility/fetch_ephemeral_input.py, which fetches by checksum." 3
    done

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
    rm -f -- "${LOGS}"/*.log "${LOGS}"/*.sha256 "${LOGS}"/*.xml "${LOGS}"/*.marker

    printf '===============================================================================\n'
    printf ' %s -- every PHASE-06 gate item must be seen to fail\n' "${SCRIPT_NAME}"
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
    printf ' SUMMARY: %d control(s) passed, %d failed, in %d seconds\n' \
        "${PASSED}" "${FAILED}" "${total}"
    printf ' Logs: %s\n' "$(rel "${LOGS}")"
    printf '===============================================================================\n'
    if [ "${FAILED}" -ne 0 ]; then
        printf '\n'
        printf '  %s\n' "${FAILURES[@]}"
        die "${FAILED} param-gate control(s) failed. A gate that cannot be seen to fail is not a gate." 1
    fi
    if [ "${self_test_only}" -eq 1 ]; then
        printf '\n  self-test OK -- the harness reports an injection that reached the source but\n'
        printf '  not the bytecode, an unchanged file, a missing anchor, a metadata edit that\n'
        printf '  removed nothing, a run with no defect, a red for the wrong reason, a PIT\n'
        printf '  report with an empty package, a metadata range edit and a corpus verdict\n'
        printf '  edit that edit nothing, and a test resource damaged in the source but not on\n'
        printf '  the test class path as a HARNESS ERROR or FAILURE, not as a pass.\n\n'
        return 0
    fi
    if [ -n "${only}" ]; then
        printf '\n  The selected controls bit (--only %s). This is NOT a full run.\n\n' "${only}"
        return 0
    fi
    printf '\n  PHASE-06 exit gate items 1 to 8 were proved here from the injections\n'
    printf '  recorded in handoffs/PHASE-06-worklog.rst, each into production code or the\n'
    printf '  shipped metadata in a git-archive sandbox and each proved in the bytecode.\n'
    printf '  Item 9: PIT scored parser, writer and validation each >= 80%% and the module\n'
    printf '  >= 80%%, and the per-package grade fell below 80%% with the validation tests\n'
    printf '  removed. Every non-killed mutant in those packages is listed above. Whether any\n'
    printf '  of them suppresses a validation error or drops a parameter is a JUDGEMENT this\n'
    printf '  script does not make: read the list against handoffs/PHASE-06-worklog.rst.\n'
    printf '  COMET-2026-03 exit gate items 3 to 6 were made version-blind here, one\n'
    printf '  family at a time -- overrides and drift, the residue alphabet and the\n'
    printf '  2026.03.0 round trip, rule severities and the AScorePro and index rules,\n'
    printf '  value migrations -- each in production code or the shipped metadata, plus\n'
    printf '  one recorded binary verdict of the validation corpus (data, not code).\n'
    printf '  The harness reports an injection that reached the source but not the\n'
    printf '  bytecode as a HARNESS ERROR, not as a pass.\n'
    printf '\n  Every gate rejected its defect and accepted the clean tree.\n\n'
}

main "$@"
