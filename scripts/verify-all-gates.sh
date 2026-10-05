#!/usr/bin/env bash
#
# CometGUI -- run every falsifiability control the project has, in one command.
#
#   bash scripts/verify-all-gates.sh
#
# A gate that has never been seen to fail has not been shown to work
# (CONTRIBUTING.rst, "Gate conventions").  Phase 01 installs eleven quality
# gates and each of them ships with its own demonstration of failure; Phase 02
# adds the application-shell gates, which are five claims about a running
# JavaFX application and ship with their own harness in the same shape.  This
# script is the aggregate: it runs every one of those demonstrations, maps each
# to the exit gate item it serves -- naming the phase, because two phases'
# items are now in play and both are numbered from one -- and exits non-zero if
# any control fails to bite.
#
# IT DELEGATES.  It injects no defect of its own and re-implements no gate.
# Every control below is somebody else's harness -- scripts/verify-*.sh and the
# --self-test modes of scripts/ci/*.sh -- called at its documented entry point.
# Duplicating an injection here would create a second thing to keep in step
# with the gate, which is the drift these scripts exist to prevent.
#
# EXIT CODE 0 PROVES NOTHING, AND THAT APPLIES TO THIS SCRIPT TOO.  A sub-
# harness that exits 0 having run nothing is exactly the failure the project
# warns about, so for every control this script requires three things:
#
#   1. the sub-harness exists and is executable -- checked for EVERY selected
#      control BEFORE any of them runs, and fatal if not.  An aggregator that
#      quietly skips a missing harness is worse than no aggregator, because it
#      converts an absent gate into a green line;
#   2. it exits 0 and its output carries the marker that means "the defect was
#      injected and caught", not merely "the program ended";
#   3. it reports at least as many controls as the floor recorded here.  The
#      floors were measured on 2026-08-29 by running each harness.  A harness
#      may grow -- more controls is fine and the number is printed -- but a run
#      that grades fewer controls than it used to has had controls removed,
#      skipped or silently short-circuited, and that is a FAILURE, not a pass.
#
# COST.  About twelve minutes on the 2026-08-30 development machine, almost all
# of it Maven: the coverage/architecture/mutation harness rebuilds a damaged
# copy of the reactor nine times and runs PIT, and the shell harness rebuilds
# and re-runs a damaged copy seventeen times under the headless JavaFX
# platform.  It is far too slow to be a stage of scripts/build.sh and is
# deliberately NOT wired into it.  Run it before signing off a phase, after
# touching anything under config/, pom.xml or scripts/, and from the nightly
# pipeline.
#
# NETWORK.  The dependency-scan control needs https://api.osv.dev.  That is
# deliberate and is not an offline mode waiting to be added: an offline
# dependency scan is not a dependency scan.  Everything else runs offline once
# tools/, .venv/ and _build/m2repo exist.
#
# THE WORKING TREE IS NEVER TOUCHED.  Every sub-harness damages a copy under
# _build/.  This script writes only _build/all-gate-logs/.
#
# Exit status:
#   0  every control ran and bit
#   1  at least one control did not bite, or reported fewer controls than its
#      recorded floor
#   2  misuse (unknown option, unknown gate name)
#   3  a sub-harness is missing or not executable -- a control would have been
#      skipped, and a skipped control is never a pass

set -Eeuo pipefail

# --------------------------------------------------------------- constants --
readonly SCRIPT_NAME="$(basename -- "${BASH_SOURCE[0]}")"
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT
readonly LOGS="${ROOT}/_build/all-gate-logs"

# The controls, in the order they run: cheap first, so a broken tree is
# reported in seconds rather than after the Maven harnesses.  The gate-item
# mapping is in gate_spec below, not in this order.
readonly -a ALL_GATES=(
    license
    workflows
    docs
    traceability
    sbom
    depscan
    pipeline
    quality
    shell
    tests
    provenance
    install
    params
    paramui
)

PASSED=0
FAILED=0
declare -a FAILURES=()
declare -a ROWS=()
declare -a COVERED=()

# ------------------------------------------------------------- the controls --
#
# gate_spec NAME populates, for one control:
#   GATE_PHASE   the phase whose exit gate those items belong to.  Two phases
#                now install gates and both number their items from one, so the
#                phase is carried explicitly rather than inferred -- renumbering
#                one phase's items to make them unique would be a silent lie
#                about which document a reader should check
#   GATE_ITEMS   the exit gate item(s) it serves, within GATE_PHASE, for the
#                summary
#   GATE_DEFECT  what is deliberately broken, in one line
#   GATE_SCRIPT  the sub-harness, relative to the repository root
#   GATE_ARGS    its arguments
#   GATE_PROOF   literal strings that MUST appear in the output; their absence
#                means the harness ended without doing its job
#   GATE_FLOOR   the number of controls it reported when last recorded.  It is
#                RAISED whenever a harness grows: a floor left behind lets a
#                later removal go unnoticed, which is the whole point of having
#                one.  (quality: 20 on 2026-08-29, 42 on 2026-08-30 when phase
#                02 unit 4 added the seven derived-file controls.  tests: 32 on
#                2026-08-29, 33 on 2026-08-30 when phase 02 unit 11 re-sized the
#                coverage and mutation injections against the grown tree and
#                added control 6's proof that the coverage gate accepts the
#                defect the mutation gate rejects.  shell: 30 on 2026-08-30,
#                when phase 02 unit 10 first shipped it.  workflows: 9 on
#                2026-08-29, 23 on 2026-10-01, measured by phase 05 unit 12 --
#                it had grown to 19 and then 23 without the floor following.
#                install: 83 on 2026-10-01, phase 05 unit 12's first shipping
#                count; 88 on 2026-10-01, when phase 05 unit 14 added control
#                19, the macOS quarantine re-check; 95 on 2026-10-04, when
#                COMET-2026-03 unit 2 added controls 20-22.  params: 68 on
#                2026-10-02, phase 06 unit 8's first shipping count; 109 on
#                2026-10-04, when COMET-2026-03 unit 6 added the fifteen
#                version-blind controls v3a-v6b, control 8's two per-release
#                checks and control H's H8-H10.  paramui: 66 on 2026-10-05,
#                phase 07 unit 8's first shipping count; 70 on 2026-10-05,
#                when phase 07 unit 9 made control 8v also grade the item-8
#                GUI test on both releases (four checks).)
#   GATE_UNIT    what that number counts, for the summary line
#
# gate_count NAME LOG echoes the number of controls the harness reported, or
# nothing if it reported none in the expected form.

gate_spec() {
    GATE_PHASE="01"
    GATE_ITEMS=""; GATE_DEFECT=""; GATE_SCRIPT=""; GATE_ARGS=()
    GATE_PROOF=(); GATE_FLOOR=0; GATE_UNIT="control(s)"
    case "$1" in
        license)
            GATE_ITEMS="D-001"
            GATE_DEFECT="five damaged copies of LICENSE -- truncated, altered title, CRLF-expanded, a wrong git blob, and absent"
            GATE_SCRIPT="scripts/verify-license.sh"
            GATE_ARGS=(--self-test)
            GATE_PROOF=("SELF-TEST PASSED" "real LICENSE accepted")
            GATE_FLOOR=5
            GATE_UNIT="damaged copies rejected"
            ;;
        workflows)
            GATE_ITEMS="6"
            GATE_DEFECT="twenty-three damaged copies of .github/ -- a renamed step script, a dropped required step, continue-on-error, a trailing || true, a git push added to release.yml, an unknown workflow naming a missing script, an action outside a workflow allowlist, an action pinned to a tag, and the if: always() dropped from the transcript upload; and, for the Windows and the macOS Gatekeeper workflows, the transcript upload deleted, its if: always() dropped, the job moved to ubuntu-latest and the verification step pointed elsewhere"
            GATE_SCRIPT="scripts/ci/check-workflows.sh"
            GATE_ARGS=(--self-test)
            GATE_PROOF=("self-test OK" "the undamaged one accepted")
            GATE_FLOOR=23
            GATE_UNIT="damaged copies rejected"
            ;;
        docs)
            GATE_ITEMS="2"
            GATE_DEFECT="a broken :ref: cross-reference appended to a copy of docs/index.rst"
            GATE_SCRIPT="scripts/ci/docs-build.sh"
            GATE_ARGS=(--self-test)
            GATE_PROOF=(
                "self-test OK -- fails on the broken cross-reference, passes without it."
                "docs-build.sh: PASSED."
            )
            GATE_FLOOR=1
            GATE_UNIT="injected cross-reference"
            ;;
        traceability)
            GATE_ITEMS="5"
            GATE_DEFECT="eight defects in copies of the map and the phase documents -- among them an AC- given no test reference, which must also fail the strict Sphinx build"
            GATE_SCRIPT="scripts/ci/traceability.sh"
            GATE_ARGS=(--self-test)
            GATE_PROOF=(
                "selftest: OK"
                "self-test OK -- the gate fails on every injected defect"
                "traceability.sh: PASSED."
            )
            GATE_FLOOR=8
            GATE_UNIT="injected defects caught"
            ;;
        sbom)
            GATE_ITEMS="6"
            GATE_DEFECT="eight damaged SBOMs -- empty components array, no components key, SPDX in a CycloneDX field, JUnit dropped, reactor modules only, a mangled purl, a missing file, zero bytes"
            GATE_SCRIPT="scripts/ci/sbom.sh"
            GATE_ARGS=(--self-test)
            GATE_PROOF=("damaged SBOM(s) rejected, the real one accepted." "sbom.sh: PASSED.")
            GATE_FLOOR=8
            GATE_UNIT="damaged SBOMs rejected"
            ;;
        depscan)
            GATE_ITEMS="6"
            GATE_DEFECT="a known-vulnerable fixture, three kinds of unreachable endpoint, an endpoint that answers 200 with an all-clear lie, five kinds of bad allowlist, and an empty SBOM"
            GATE_SCRIPT="scripts/ci/dependency-scan.sh"
            GATE_ARGS=(--self-test)
            GATE_PROOF=("dependency-scan.sh: self-test OK" "control-real-scan")
            GATE_FLOOR=16
            GATE_UNIT="cases"
            ;;
        pipeline)
            GATE_ITEMS="6"
            GATE_DEFECT="none injected: every nightly and release step whose work belongs to a later phase is a stub, and each MUST exit 70 rather than 0 -- the silent pass this phase was told not to ship"
            GATE_SCRIPT="scripts/ci/run-pipeline-locally.sh"
            GATE_ARGS=(nightly release)
            GATE_PROOF=("0 unexpected." "OK -- every executed step behaved as its classification requires.")
            GATE_FLOOR=24
            GATE_UNIT="steps executed and classified"
            ;;
        quality)
            GATE_ITEMS="1, 6"
            GATE_DEFECT="misformatted source, an MIT header on a GPL-3.0 file, a package-info.java with no header at all, string comparison by reference, a brace-less conditional, a guaranteed null dereference; and for the derived-file attribution machinery (D-001 obligation 2, R-SEC-01): a derived file with the ordinary header and no upstream attribution, a derived file with no per-file derivation record, an unearned derivation claimed by a non-derived file, a badly formatted derived file proving google-java-format still applies to that file set, a derived file that neither file set matches and that a GREEN build therefore checks with nothing, and a module dropped from checkstyle-derived.xml"
            GATE_SCRIPT="scripts/verify-quality-gates.sh"
            GATE_ARGS=()
            GATE_PROOF=("Every gate rejected its defect and accepted the clean tree.")
            GATE_FLOOR=42
            GATE_UNIT="controls"
            ;;
        shell)
            GATE_PHASE="02"
            GATE_ITEMS="1,2,4,5"
            GATE_DEFECT="a shell whose every navigation entry selects the same section, arrow keys that move two sections at a time so a section cannot be reached by keyboard alone, section panes with no stable identifier, a content area that shows every pane at once while every identifier stays in place, an accessible name removed where it is assigned once -- and, as a control on the harness itself, the same name removed at the site showStage() re-assigns, which the harness must report as a HARNESS FAILURE rather than as a gate that did not bite; then the console's rendered-document window removed, and the eviction removed from BoundedMessageLog.append so the retained heap grows past its documented cap"
            GATE_SCRIPT="scripts/verify-shell-gates.sh"
            GATE_ARGS=()
            GATE_PROOF=(
                "Every gate rejected its defect and accepted the clean tree."
                "PHASE-02 exit gate items 1, 2, 4 and 5 were proved here"
                "the harness reports an injection that reached the file but not the behaviour as a HARNESS FAILURE, not as a pass"
            )
            GATE_FLOOR=30
            GATE_UNIT="controls"
            ;;
        tests)
            GATE_ITEMS="3, 4"
            GATE_DEFECT="a JavaFX import in the domain, a ProcessBuilder outside the process service, a truncated ArchUnit import, an untested class in a gated package sized so the ratio really falls below 0.90, an untested view-model sized against the package rule, a covered class whose test asserts nothing so its mutations survive, and a module whose coverage was never measured"
            GATE_SCRIPT="scripts/verify-test-gates.sh"
            GATE_ARGS=()
            GATE_PROOF=("Every gate rejected its defect and accepted the clean tree.")
            GATE_FLOOR=37
            GATE_UNIT="assertions"
            ;;
        provenance)
            GATE_PHASE="04"
            GATE_ITEMS="1,2,3,4,5,6"
            GATE_DEFECT="a hasher that digests one byte less than it read so every published vector comes back wrong; a hasher that keeps every chunk, leaving the digests EXACTLY CORRECT while the 2 GB proof's retained-heap bound is exceeded; a fingerprint that treats an absent attribute as a match, dressed as Windows compatibility, so the cache serves an entry it cannot validate; a log reader that drops the torn tail a crash leaves, so the damage is not reported; ATOMIC_MOVE replaced by copy-then-delete, which a concurrent reader sees straight through; and redaction removed from each of the three writers in turn -- the JSON value path, a size-conditioned fast path in the RST writer, and the event log's payload -- each caught by a grep of the bytes on disk that names the artefact, the corpus index and the offset without printing the secret"
            GATE_SCRIPT="scripts/verify-provenance-gates.sh"
            GATE_ARGS=()
            GATE_PROOF=(
                "Every gate rejected its defect and accepted the clean tree."
                "PHASE-04 exit gate items 1 to 6 were proved here"
            )
            GATE_FLOOR=24
            GATE_UNIT="controls"
            ;;
        install)
            GATE_PHASE="05"
            GATE_ITEMS="1,2,3,4,5,6,7,8"
            GATE_DEFECT="from the injections recorded in handoffs/PHASE-05-worklog.rst, each into production code in a git-archive sandbox and each proved to have reached the bytecode: a mid-transfer cancel reported as FAILED, resumed progress counted from the resume point, a progress report delivered off the JavaFX thread, the real Tool Manager's Install control disabled, the step-2 re-hash disabled, a corrupted artefact let out of the transfer step so only the [DOWNLOADING, FAILED] phase sequence sees it, a drive letter never seen, the xar DOCTYPE guard deleted, a lost file counted from the marker, a build that no longer starts offered, an unreachable binary's refusal naming no alternatives, alternatives keyed on the version, the offer order inverted, the download size quoting the artefact alone, Locale.ROOT removed from the probe's PIN, a blank note accepted for UNVERIFIED evidence; NEW at unit 12, a Thermo companion gate that ignores a missing DLL (item 6) and a local Percolator floor of 3.04 (item 7); NEW at unit 14, R-PLAT-04's macOS quarantine step believing an xattr -d that exited 0 and removed nothing, graded on Linux against a scripted xattr (not evidence about macOS); five damaged manifests the documentation-table generator must reject with each one's own diagnostic, one through the Sphinx hook, and a generator that writes nothing; NEW at COMET-2026-03 unit 2, the identity stage made version-blind (the 2026.03.0 binary installed under a 2026.02.2 pin) and the default Comet no longer the newest release, in select() and in the Tool Manager's own release order; and, as a control on the harness itself, an injection that reached the source but not the bytecode, which must be reported as a HARNESS ERROR. Item 9 (macOS) is NOT MET and is only delegated"
            GATE_SCRIPT="scripts/verify-install-gates.sh"
            GATE_ARGS=()
            GATE_PROOF=(
                "Every gate rejected its defect and accepted the clean tree."
                "PHASE-05 exit gate items 1, 2, 3, 4, 5 and 8 were proved here"
                "Item 9 is NOT MET"
                "bytecode as a HARNESS ERROR, not as a pass"
            )
            GATE_FLOOR=95
            GATE_UNIT="controls"
            ;;
        params)
            GATE_PHASE="06"
            GATE_ITEMS="1,2,3,4,5,6,7,8,9"
            GATE_DEFECT="from the injections recorded in handoffs/PHASE-06-worklog.rst, each into production code (or, for item 2, the shipped metadata) of cometgui-params-comet in a git-archive sandbox and each proved to have reached the compiled module: the writer skipping every empty-valued parameter; the scan_range entry removed from the metadata; the drift default comparison reduced to its first token; the second neutral loss dropped; a min,max count written max,min; the enzyme table's duplicate-number invariant disabled; the writer's refusal of an absent enzyme number disabled; the one number writer, and separately the writer's decimal path, made locale-sensitive (Locale.ROOT required to stay green); the writer dropping the unknown section; the parser reporting and then dropping unknown parameters; the tolerance pair routed through the generic ordering rule; an asymmetric window made an error; the reversed-pair error unable to fire; unit 7's generator self-test invoked (27 damaged inputs when it shipped, 55 since COMET-2026-03 unit 6; 4 defects through the real Sphinx hook); PIT scored per package for parser, writer and validation and for the module, each >= 80%, every non-killed mutant listed, and the validation package graded BELOW with its tests removed; and, as controls on the harness itself, an injection that reached the source but not the bytecode and a PIT report with an empty package, each reported as a HARNESS ERROR or FAILURE. Item 9's per-survivor judgement is listed, not automated. NEW at COMET-2026-03 unit 6, each making one version-scoped fact of the Comet 2026.03.0 intake version-blind (its exit gate items 3-6), with the untouched release required to stay green where it can: the first version record's overrides applied to every release, and no override applied; scan_range's range ending at 2026.02.2 in the shipped metadata, named UNMODELLED for 2026.03.0 only; every release's tuple codec given 2026.03.0's residue alphabet, and 2024.01.0's; the writer writing the curated inline comment, so the pinned 2026.03.0 canonical round trip goes red; every model judged with 2026.02.2's rule severities; validation reading the newest release's alphabet; AScorePro's -1 suppressing the slot error, its merge of identical slots disabled, and its protein-terminus rewrite applied in a release without ^; index_search_type.ignored_without_idx warning in every release; one recorded binary verdict of the validation corpus made wrong (data, not code); value migrations applied whatever release they are from, and rule-keyed migrations never applied; control 8's generator floor raised to 55 with its per-release checks graded; and H8-H10 on the new controls' own plumbing"
            GATE_SCRIPT="scripts/verify-param-gates.sh"
            GATE_ARGS=()
            GATE_PROOF=(
                "Every gate rejected its defect and accepted the clean tree."
                "PHASE-06 exit gate items 1 to 8 were proved here"
                "Item 9: PIT scored parser, writer and validation each >= 80% and the module"
                "bytecode as a HARNESS ERROR, not as a pass"
                "COMET-2026-03 exit gate items 3 to 6 were made version-blind here"
            )
            GATE_FLOOR=109
            GATE_UNIT="controls"
            ;;
        paramui)
            GATE_PHASE="07"
            GATE_ITEMS="1,2,3,4,5,6,7,8"
            GATE_DEFECT="from the injections recorded in handoffs/PHASE-07-worklog.rst (and new ones, each marked so), each into production code of cometgui-ui in a git-archive sandbox, each proved to have reached the compiled classes and graded on the failing assertion's own words in the GUI gate test that asserts the item: the Essentials decoy control always choosing decoys-in-the-FASTA, and the save quietly writing decoy_search = 0 while the screen shows the choice (the saved file must differ from the checked-in expected file in that one line); NEW at unit 10, a static-modification table row writing its mass to the next row's parameter (the table test's lysine row and the gate-1 saved file); the slot editor's Move up moving down; Cancel applying every preset row, and Apply selected applying every row; NEW at unit 10, the Essentials fragment instrument choice applying its preset's rows the moment it is chosen (AC-PAR-08), and its preview not scoped to the fragment rows (the whole-preset test required to stay green); a failed raw Expert apply resetting the configuration, and a raw apply adopted without confirmation; a locked output's check box left enabled, and its reason never shown; a summary entry that no longer moves the focus, and the parameters' readiness forced to 'do not block' (with the migrated NEEDS_ATTENTION entry's test); a parameter control's own accessible name removed so only the generated fallback is left, the same for a static-modification table row's mass field (NEW at unit 10), and the validation state left out of its accessible help; alias matching removed, and a search result that no longer focuses its field; VERSION-BLIND, each with the untouched release required to stay green where its test can show it: the slot editor offering ^ and $ on every release, the Expert draft parsed as the first offered release, a field's choices taken from the curated definition, and a field's help taken from the curated definition (graded on its view-model test and on the item-8 GUI test, whose 2026.02.2 method must stay green); and, as controls on the harness itself, an unchanged file, a missing anchor, an injection that reached the source but not the bytecode, a green run graded as red, a red without its diagnostic, and unit 6's EQUIVALENT injection (Run disabled only by the engine's reason), which must be reported as a HARNESS FAILURE"
            GATE_SCRIPT="scripts/verify-param-ui-gates.sh"
            GATE_ARGS=()
            GATE_PROOF=(
                "Every gate rejected its defect and accepted the clean tree."
                "PHASE-07 exit gate items 1 to 8 were proved here"
                "Four controls were version-blind (2v, 4v, 7v and 8v)"
                "bytecode as a HARNESS ERROR, not as a pass"
            )
            GATE_FLOOR=70
            GATE_UNIT="controls"
            ;;
        *)
            return 1
            ;;
    esac
    return 0
}

gate_count() {
    local name="$1" log="$2"
    case "${name}" in
        license)
            sed -n 's/.*SELF-TEST PASSED -- \([0-9][0-9]*\) negative controls rejected.*/\1/p' -- "${log}" | head -1 ;;
        workflows)
            sed -n 's/.*self-test OK -- \([0-9][0-9]*\) damaged copies rejected.*/\1/p' -- "${log}" | head -1 ;;
        docs)
            grep -cF -- 'self-test OK -- fails on the broken cross-reference' "${log}" || true ;;
        traceability)
            sed -n 's/^selftest: OK -- \([0-9][0-9]*\) case(s).*/\1/p' -- "${log}" | head -1 ;;
        sbom)
            sed -n 's/.*self-test OK -- \([0-9][0-9]*\) damaged SBOM(s) rejected.*/\1/p' -- "${log}" | head -1 ;;
        depscan)
            sed -n 's/.*self-test OK -- \([0-9][0-9]*\)\/[0-9][0-9]* cases.*/\1/p' -- "${log}" | head -1 ;;
        pipeline)
            sed -n 's/.*workflow(s); \([0-9][0-9]*\) executed on this machine; 0 unexpected.*/\1/p' -- "${log}" | head -1 ;;
        quality)
            sed -n 's/.*SUMMARY: \([0-9][0-9]*\) control(s) passed, 0 failed.*/\1/p' -- "${log}" | head -1 ;;
        shell)
            sed -n 's/.*SUMMARY: \([0-9][0-9]*\) control(s) passed, 0 failed.*/\1/p' -- "${log}" | head -1 ;;
        tests)
            sed -n 's/.*SUMMARY: \([0-9][0-9]*\) assertion(s) passed, 0 failed.*/\1/p' -- "${log}" | head -1 ;;
        provenance)
            sed -n 's/.*SUMMARY: \([0-9][0-9]*\) control(s) passed, 0 failed.*/\1/p' -- "${log}" | head -1 ;;
        install)
            sed -n 's/.*SUMMARY: \([0-9][0-9]*\) control(s) passed, 0 failed.*/\1/p' -- "${log}" | head -1 ;;
        params)
            sed -n 's/.*SUMMARY: \([0-9][0-9]*\) control(s) passed, 0 failed.*/\1/p' -- "${log}" | head -1 ;;
        paramui)
            sed -n 's/.*SUMMARY: \([0-9][0-9]*\) control(s) passed, 0 failed.*/\1/p' -- "${log}" | head -1 ;;
    esac
}

# ----------------------------------------------------------------- plumbing --
usage() {
    cat <<USAGE
${SCRIPT_NAME} -- run every falsifiability control the project has and prove
that every PHASE-01, PHASE-02, PHASE-04, PHASE-05, PHASE-06 and PHASE-07 gate
still fails on the defect it exists to catch.

Usage:
  bash scripts/${SCRIPT_NAME}                 run every control
  bash scripts/${SCRIPT_NAME} --only NAME     run one control (repeatable, or
                                              comma-separated)
  bash scripts/${SCRIPT_NAME} --list          what the controls are, what each
                                              injects, and the command that
                                              proves it
  bash scripts/${SCRIPT_NAME} --help

WHAT IT DOES.  It runs each gate's own harness -- scripts/verify-*.sh and the
--self-test modes of scripts/ci/*.sh -- and requires each to exit 0, to print
the marker that means the injected defect was caught, and to grade at least as
many controls as it did when this script was written.  It injects nothing
itself.  A sub-harness that is missing or not executable is a fatal error
before anything runs (exit 3): a skipped control must never be counted as a
pass.

WHAT IT COSTS.  About half an hour, almost all of it Maven.  Elapsed time is
printed per control.  This is why it is not a stage of scripts/build.sh.

WHEN TO RUN IT.
  * Before signing off a phase -- it is the evidence that the phase's gates
    still bite, which "the build is green" is not.
  * After changing anything under config/, pom.xml, scripts/ or
    .github/workflows/.
  * In the nightly pipeline, where seven minutes is free.

WHAT IT NEEDS.  A built tree (run bash scripts/build.sh first) for tools/,
.venv/ and _build/m2repo; the project-local font stack; and network access to
https://api.osv.dev for the dependency-scan control.

WHAT IT DOES NOT COVER.  Exit gate item 1 -- "a clean checkout builds and tests
green with one documented command" -- is bash scripts/build.sh, and is not
repeated here; the quality control proves only the half of item 1 that consists
of gates failing the build.  The "on a pull request" half of exit gate item 6
needs GitHub to run the pipeline ON A PULL REQUEST, and no pull request has
ever been opened.  The remote has existed since D-008 was decided on
2026-08-30 and main is pushed; one scheduled nightly has run and failed on a
Phase-15 stub by design.  No pull-request workflow has run.  The pipeline
control proves every step on this machine instead, and says so.  PHASE-05 exit
gate item 9 -- a managed tool executing on macOS without a Gatekeeper refusal
-- is NOT MET: a hosted macOS runner did not refuse a quarantined binary
(run 36918810975), so it cannot show acceptance either, and the install control
only requires the words that say so to still be there.
USAGE
}

# die MESSAGE [EXIT CODE].  Only $1 is the message: $* would print the exit
# code as part of it.
die() {
    printf '\n%s: %s\n' "${SCRIPT_NAME}" "$1" >&2
    exit "${2:-2}"
}

banner() {
    printf '\n-------------------------------------------------------------------------------\n'
    printf ' %s\n' "$*"
    printf -- '-------------------------------------------------------------------------------\n'
}

list_gates() {
    local name
    printf '\n%s -- %d falsifiability control(s)\n\n' "${SCRIPT_NAME}" "${#ALL_GATES[@]}"
    printf '  %-13s %-9s %s\n' "NAME" "GATE ITEM" "COMMAND"
    printf '  %-13s %-9s %s\n' "----" "---------" "-------"
    for name in "${ALL_GATES[@]}"; do
        gate_spec "${name}"
        printf '  %-13s %-9s bash %s%s\n' \
            "${name}" "${GATE_PHASE}:${GATE_ITEMS}" "${GATE_SCRIPT}" \
            "$([ "${#GATE_ARGS[@]}" -gt 0 ] && printf ' %s' "${GATE_ARGS[*]}")"
        printf '  %-13s %-9s injects: %s\n\n' "" "" "${GATE_DEFECT}"
    done
    printf '  The ITEM column is phase-qualified: 01:n is an item of PHASE-01, 02:n of\n'
    printf '  PHASE-02, 04:n of PHASE-04, 05:n of PHASE-05, 06:n of PHASE-06, 07:n of\n'
    printf '  PHASE-07.\n'
    printf '  Every phase numbers its items\n'
    printf '  from one, so the phase is always named rather than inferred.\n'
    printf '  PHASE-01 items: 1 one documented build command; 2 strict documentation\n'
    printf '  build; 3 ArchUnit layering; 4 coverage; 5 traceability; 6 CI pipelines.\n'
    printf '  PHASE-02 items: 1 every section reachable by mouse and by keyboard alone;\n'
    printf '  2 a headless GUI test navigating all sections by stable identifier;\n'
    printf '  3 ArchUnit proving the domain has no JavaFX dependency; 4 an accessible\n'
    printf '  name on every control; 5 a bounded console under a flood test.\n'
    printf '  PHASE-04 items: 1 known MD5 and SHA-256 vectors including the zero-byte\n'
    printf '  file; 2 a 2 GB file hashed in one pass with bounded heap; 3 a hash cache\n'
    printf '  that returns a value only when every attribute matches; 4 a crash leaving a\n'
    printf '  parsable event log with usable history; 5 atomic finalisation; 6 a seeded\n'
    printf '  secret corpus appearing nowhere in JSON, RST or logs; 7 no surviving\n'
    printf '  mutation in hashing and redaction, which the tests control above proves.\n'
    printf '  PHASE-05 items: 1 four tools installed through the Tool Manager UI; 2 a\n'
    printf '  corrupted download rejected and never executed; 3 archive traversal,\n'
    printf '  absolute-path, symlink and bomb attacks rejected; 4 an interrupted install\n'
    printf '  never reports itself installed; 5 the R-PLAT-03 diagnostic and no offer of\n'
    printf '  a tool that cannot load; 6 no THERMO_RAW_WINDOWS without the Thermo DLLs;\n'
    printf '  7 a local Percolator below 3.05 rejected; 8 no offer absent from the\n'
    printf '  manifest; 9 macOS without a Gatekeeper refusal -- NOT MET, delegated only.\n'
    printf '  PHASE-06 items: 1 a byte-stable double round trip of the real -q output;\n'
    printf '  2 all 118 parameters modelled and drift failing on a removed entry; 3 every\n'
    printf '  variable-modification tuple form in all fifteen slots; 4 the enzyme table,\n'
    printf '  a custom enzyme, and no absent enzyme number written; 5 byte-identical\n'
    printf '  output under a comma-decimal locale; 6 an unknown parameter kept and\n'
    printf '  reported; 7 the tolerance pair by its own rule; 8 the generated reference\n'
    printf '  strict and complete; 9 PIT >= 80%% over parser, writer and validation.\n'
    printf '  PHASE-07 items: 1 an Essentials-only tryptic DDA search saved as the\n'
    printf '  expected canonical file; 2 a variable modification added, edited,\n'
    printf '  reordered and removed; 3 a preset diff, an exact subset, a cancel that\n'
    printf '  changes nothing; 4 a raw Expert edit that fails to parse changing\n'
    printf '  nothing; 5 a workflow-required output that cannot be disabled, with its\n'
    printf '  reason; 6 a cross-parameter error blocking Run, at the field, in the\n'
    printf '  summary, by keyboard; 7 every parameter control named, validation in\n'
    printf '  text; 8 search by name, display name, help text and alias.\n'
    printf '  D-001 is the GPL-3.0 licence obligation, a phase deliverable rather than a\n'
    printf '  numbered gate item.  See phases/PHASE-01-build-skeleton.rst,\n'
    printf '  phases/PHASE-02-app-shell.rst, phases/PHASE-04-provenance-core.rst,\n'
    printf '  phases/PHASE-05-tool-registry.rst, phases/PHASE-06-comet-param-model.rst and\n'
    printf '  phases/PHASE-07-comet-param-ui.rst.\n\n'
}

# preflight SELECTED...  -- every sub-harness must be there and executable
# before any of them runs.  This is the single most important property of this
# script, so it is checked for all of them and reported in full rather than
# failing at the first one.
preflight() {
    local name missing=0 path
    printf '\n=== Preflight: every sub-harness must exist and be executable ===\n'
    printf 'A missing harness is a FAILURE, never a skipped control.\n\n'
    for name in "$@"; do
        gate_spec "${name}"
        path="${ROOT}/${GATE_SCRIPT}"
        if [ ! -f "${path}" ]; then
            printf '  MISSING        %-13s %s\n' "${name}" "${GATE_SCRIPT}"
            missing=$((missing + 1))
        elif [ ! -x "${path}" ]; then
            printf '  NOT EXECUTABLE %-13s %s (mode %s)\n' \
                "${name}" "${GATE_SCRIPT}" "$(stat -c %a -- "${path}")"
            missing=$((missing + 1))
        elif [ ! -s "${path}" ]; then
            printf '  EMPTY          %-13s %s\n' "${name}" "${GATE_SCRIPT}"
            missing=$((missing + 1))
        else
            printf '  ok             %-13s %s\n' "${name}" "${GATE_SCRIPT}"
        fi
    done
    if [ "${missing}" -ne 0 ]; then
        printf '\n'
        printf '%s: %d sub-harness(es) cannot be run.\n' "${SCRIPT_NAME}" "${missing}" >&2
        printf '%s: REFUSING TO CONTINUE. Running the rest would report a green\n' "${SCRIPT_NAME}" >&2
        printf '%s: summary for a set of gates that were never proved to bite,\n' "${SCRIPT_NAME}" >&2
        printf '%s: which is the one thing this script exists to prevent.\n' "${SCRIPT_NAME}" >&2
        exit 3
    fi
    printf '\n  %d/%d sub-harness(es) present and executable.\n' "$#" "$#"
}

# run_gate NAME -- run one control and grade it.
run_gate() {
    local name="$1"
    gate_spec "${name}"

    local log="${LOGS}/${name}.log"
    local rel="${log#"${ROOT}/"}"
    local cmd="bash ${GATE_SCRIPT}"
    [ "${#GATE_ARGS[@]}" -gt 0 ] && cmd="${cmd} ${GATE_ARGS[*]}"

    banner "CONTROL ${name}  --  PHASE-${GATE_PHASE} exit gate item ${GATE_ITEMS}"
    printf '  injects  %s\n' "${GATE_DEFECT}"
    printf '  proved by %s\n' "${cmd}"
    printf '  log       %s\n\n' "${rel}"

    local started rc=0
    started="$(date +%s)"
    if [ "${#GATE_ARGS[@]}" -gt 0 ]; then
        bash "${ROOT}/${GATE_SCRIPT}" "${GATE_ARGS[@]}" >"${log}" 2>&1 || rc=$?
    else
        bash "${ROOT}/${GATE_SCRIPT}" >"${log}" 2>&1 || rc=$?
    fi
    local elapsed=$(( $(date +%s) - started ))

    # --- grade it -----------------------------------------------------------
    local verdict="PASS" why="" count=""

    if [ ! -s "${log}" ]; then
        verdict="FAIL"
        why="the harness wrote no output at all (exit ${rc}); it cannot have run a control"
    elif [ "${rc}" -ne 0 ]; then
        verdict="FAIL"
        why="exited ${rc}; a gate did not bite, or the harness could not run. See ${rel}"
    else
        local marker
        for marker in "${GATE_PROOF[@]}"; do
            if ! grep -qF -- "${marker}" "${log}"; then
                verdict="FAIL"
                why="exited 0 but never printed '${marker}' -- it ended without proving anything. See ${rel}"
                break
            fi
        done
    fi

    if [ "${verdict}" = "PASS" ]; then
        count="$(gate_count "${name}" "${log}" || true)"
        if [ -z "${count}" ]; then
            verdict="FAIL"
            why="exited 0 with its marker but reported no control count; this script cannot tell how much it graded. See ${rel}"
        elif [ "${count}" -lt "${GATE_FLOOR}" ]; then
            verdict="FAIL"
            why="graded ${count} ${GATE_UNIT}, fewer than the recorded floor of ${GATE_FLOOR}; controls have been removed or skipped. See ${rel}"
        fi
    fi

    # --- record it ----------------------------------------------------------
    if [ "${verdict}" = "PASS" ]; then
        PASSED=$((PASSED + 1))
        printf '  PASS  %s: %s %s in %ds\n' "${name}" "${count}" "${GATE_UNIT}" "${elapsed}"
        [ "${count}" -gt "${GATE_FLOOR}" ] \
            && printf '        (the floor recorded here is %d; the harness has grown)\n' "${GATE_FLOOR}"
        COVERED+=("${GATE_PHASE} ${GATE_ITEMS}")
    else
        FAILED=$((FAILED + 1))
        FAILURES+=("${name}: ${why}")
        printf '  FAIL  %s: %s\n' "${name}" "${why}"
        printf '        last lines of %s:\n' "${rel}"
        tail -5 -- "${log}" 2>/dev/null | sed 's/^/          /' || true
        count="${count:-0}"
    fi

    ROWS+=("$(printf '  %-4s  %-13s %-9s %-6s %5ds  %s' \
        "${verdict}" "${name}" "${GATE_PHASE}:${GATE_ITEMS}" "${count:-?}" \
        "${elapsed}" "${cmd}")")
    ROWS+=("$(printf '        injected: %s' "${GATE_DEFECT}")")
}

# -------------------------------------------------------------------- main --
main() {
    local -a selected=()
    local only=""

    while [ "$#" -gt 0 ]; do
        case "$1" in
            -h|--help) usage; exit 0 ;;
            --list)    list_gates; exit 0 ;;
            --only)
                [ "$#" -ge 2 ] || die "--only needs a gate name (try --list)"
                only="${only}${only:+,}$2"
                shift 2
                ;;
            *) usage >&2; die "unknown option: $1" ;;
        esac
    done

    if [ -n "${only}" ]; then
        local want found
        local -a wanted=()
        IFS=',' read -r -a wanted <<< "${only}"
        for want in "${wanted[@]}"; do
            found=0
            for name in "${ALL_GATES[@]}"; do
                [ "${name}" = "${want}" ] && found=1
            done
            [ "${found}" -eq 1 ] || die "--only: no such gate '${want}'. Names: ${ALL_GATES[*]}"
        done
        # Run in ALL_GATES order, once each, whatever order they were given in.
        for name in "${ALL_GATES[@]}"; do
            for want in "${wanted[@]}"; do
                if [ "${name}" = "${want}" ]; then
                    selected+=("${name}")
                    break
                fi
            done
        done
    else
        selected=("${ALL_GATES[@]}")
    fi

    cd -- "${ROOT}"
    mkdir -p -- "${LOGS}"

    printf '===============================================================================\n'
    printf ' %s -- every PHASE-01, PHASE-02, PHASE-04, PHASE-05, PHASE-06 and PHASE-07 gate must be seen to fail\n' "${SCRIPT_NAME}"
    printf '===============================================================================\n'
    printf '  repository   %s\n' "${ROOT}"
    printf '  controls     %d of %d\n' "${#selected[@]}" "${#ALL_GATES[@]}"
    printf '  logs         %s\n' "${LOGS#"${ROOT}/"}"
    printf '  expect this to take several minutes; see --help\n'

    preflight "${selected[@]}"

    local started
    started="$(date +%s)"
    local name
    for name in "${selected[@]}"; do
        run_gate "${name}"
    done
    local total=$(( $(date +%s) - started ))

    # --- the summary --------------------------------------------------------
    printf '\n===============================================================================\n'
    printf ' SUMMARY\n'
    printf '===============================================================================\n'
    printf '  %-4s  %-13s %-9s %-6s %6s  %s\n' \
        "" "GATE" "ITEM" "GRADED" "TIME" "COMMAND THAT PROVES IT"
    printf '%s\n' "${ROWS[@]}"

    # Which exit gate items this run covered, per phase, deduplicated and
    # sorted.  Per phase and not pooled: every phase numbers its items from
    # one, so a pooled list would silently credit one phase with another's
    # coverage -- which is exactly the kind of quiet lie these controls exist
    # to prevent.
    #
    # GATE_ITEMS is split on commas and only ^[0-9]+$ survives, so an entry
    # written as a RANGE -- "1-6" -- is dropped and the phase prints "none".
    # State the items individually.  A phase added here without being added to
    # the loop below prints nothing at all, which is why the loop is a list
    # rather than a wildcard: a missing phase is visible as a missing line.
    local phase items
    printf '\n'
    for phase in 01 02 04 05 06 07; do
        items="$(printf '%s\n' "${COVERED[@]}" \
            | sed -n "s/^${phase} //p" | tr ',' '\n' | tr -d ' ' \
            | grep -E '^[0-9]+$' | sort -un | paste -sd, - || true)"
        printf '  PHASE-%s exit gate items covered by the controls that passed: %s\n' \
            "${phase}" "${items:-none}"
    done
    printf '  PHASE-01 item 1 is covered only in part: these controls prove that the\n'
    printf '  gates which FAIL the build still bite. That a clean checkout BUILDS green\n'
    printf '  is bash scripts/build.sh, and is not repeated here.\n'
    printf '  The "on a pull request" half of PHASE-01 item 6 needs GitHub to run the\n'
    printf '  pipeline ON A PULL REQUEST. The remote has existed since D-008 was decided\n'
    printf '  on 2026-08-30 and main is pushed, but no pull request has been opened, so\n'
    printf '  that trigger has never fired; the pipeline control proves every step on\n'
    printf '  this machine instead.\n'
    printf '  PHASE-02 item 3 -- an ArchUnit test proving the domain module has no JavaFX\n'
    printf '  dependency -- is the same rule PHASE-01 item 3 installs, so it is proved by\n'
    printf '  the tests control rather than injected twice; the shell control fails if\n'
    printf '  that harness ever loses it.\n'
    printf '  PHASE-05 item 9 -- a managed tool executing on macOS without a Gatekeeper\n'
    printf '  refusal -- is NOT MET and is not listed above: a hosted macOS runner does\n'
    printf '  not refuse a quarantined binary, so it cannot show acceptance. The install\n'
    printf '  control only delegates it.\n'
    printf '  PHASE-06 item 9 is covered in its numeric half only: the params control\n'
    printf '  requires parser, writer and validation each >= 80%% and lists every\n'
    printf '  mutant PIT did not kill. Whether one suppresses a validation error or\n'
    printf '  drops a parameter is a judgement read from that list, not automated.\n'

    printf '\n  %d control(s) passed, %d failed, in %d seconds (%dm%02ds).\n' \
        "${PASSED}" "${FAILED}" "${total}" "$((total / 60))" "$((total % 60))"

    if [ "${FAILED}" -ne 0 ]; then
        printf '\n'
        printf '  %s\n' "${FAILURES[@]}"
        die "${FAILED} control(s) failed. A gate that cannot be seen to fail is not a gate." 1
    fi
    printf '\n  Every gate was seen to reject its defect and accept the clean tree.\n\n'
}

main "$@"
