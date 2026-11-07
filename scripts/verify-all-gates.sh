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
# dependency scan is not a dependency scan.  The pipeline control runs the
# real tool manifest verification (R-TEST-08, since Phase 15 unit 5), which
# needs github.com and api.github.com for the same reason; its self-test, the
# manifest control, is loopback only.  The e2e control needs the tools'
# release hosts too.  Everything else runs offline once tools/, .venv/ and
# _build/m2repo exist.
#
# THE WORKING TREE IS NEVER TOUCHED.  Every sub-harness damages a copy under
# _build/.  This script writes only _build/all-gate-logs/.  The pipeline
# control, whose nightly matrix, determinism and performance steps and release
# packaging run Maven, has run every step in its own `git archive HEAD`
# checkout, _build/ci-pipeline-sandbox, since Phase 15 unit 10: before that it
# rewrote the working tree's cometgui-*/target under any build beside it.
#
# SIDE BY SIDE, SIZED BY CORES (2026-11-05).  The controls can run
# concurrently, up to a job count:
#
#   * computed from the cores this process may use (nproc, which honours CPU
#     affinity): 1 -- SERIAL, exactly the behaviour before this existed -- at 4
#     cores or fewer, which is every GitHub-hosted runner; above that one job
#     per CORES_PER_JOB cores, capped at JOBS_CAP (see compute_jobs for the
#     measurements behind both numbers);
#   * or set explicitly, with --jobs N or COMETGUI_GATE_JOBS=N (the option
#     wins).  1 means serial.
#
# Each control keeps its own log, and the grading, the summary, the floors,
# the exit code and the ORDER the controls print in are those of a serial run:
# controls are graded in ALL_GATES order once all have ended, whatever order
# they finished in.  A control whose harness exits non-zero, is killed, or
# whose worker dies before recording an exit status is a FAILURE.
#
# What the controls SHARE was audited before any two were allowed to run
# together (handoffs/FIX-BUILD-TIME-worklog.rst has the audit).  Two groups
# write the same working-tree files and never run at once -- see gate_locks.
# Everything else they share (_build/m2repo, tools/, scratch/, .venv/) they
# only read, and a concurrent run proves it: each is fingerprinted before the
# first control starts and after the last ends, and a change FAILS the run.
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
    manifest
    flakiness
    pipeline
    hardening
    quality
    shell
    tests
    provenance
    install
    params
    paramui
    workflow
    percolator
    results
    pdv
    limelight
    provenanceui
    e2e
)

# The job-count rule; see compute_jobs.
readonly CORES_PER_JOB=8
readonly JOBS_CAP=4

# Read-only resources every concurrent control shares; see fingerprint.
readonly -a SHARED_READ_ONLY=(_build/m2repo tools scratch .venv)

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
#                defect the mutation gate rejects; 37 by Phase 14; 49 on
#                2026-11-05, when FIX-BUILD-TIME replaced the two whole
#                sandbox builds of controls 7 and 8 with one (control C) and
#                the census alone, adding twelve assertions on the census's
#                picture and currency; 68 on 2026-11-05, when Phase 15 unit 3
#                added control M, nineteen assertions on the per-package
#                mutation grading (D-014, D-015).  shell: 30 on 2026-08-30,
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
#                GUI test on both releases (four checks); 87 on 2026-10-07,
#                when phase 08 unit 7 added H7, the engine-only injection
#                graded where the engine is ready (three checks).  workflow:
#                110 on 2026-10-07, phase 08 unit 9's first shipping count.
#                percolator: 94 on 2026-10-08, phase 09 unit 9's first
#                shipping count; 109 on 2026-10-08, when phase 10 unit D-013
#                added controls A1-A3 (--no-analytics).  results: 100 on
#                2026-10-09, phase 10 unit 11's first shipping count.
#                pdv: 107 on 2026-10-11, phase 11 unit 9's first shipping
#                count.  limelight: 130 on 2026-10-12, phase 12 unit 9's
#                first shipping count.  provenanceui: 96 on 2026-10-14,
#                phase 13 unit 7's first shipping count.  e2e: 48 on
#                2026-10-23, phase 14 unit 9's first shipping count; 50 the
#                same day, when the orchestrator added the smoke and its
#                self-test to control P.  flakiness: 39 on 2026-11-07,
#                phase 15 unit 9's first shipping count; 40 the same day,
#                when the orchestrator's sign-off added the same-line retry
#                case.  hardening: 35 on 2026-11-07, phase 15 unit 10's first
#                shipping count.  pipeline: 24 when first floored; 31 on
#                2026-11-07, raised by phase 15 unit 10 to the count every
#                run since unit 8 has graded.)
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
            GATE_DEFECT="eight defects in copies of the map and the phase documents -- among them an AC- given no test reference, which must also fail the strict Sphinx build; and, since Phase 15 unit 6, R-TEST-07's review rule over copies of the goldens: a golden changed with no new review record, its bytes changed by one newline, a placeholder and an empty explanation, a future date, a missing REVIEW.json, a stale record, an unjustified tolerance, a golden under another tool pair's key and a stray file, each red, the committed goldens green; and, since Phase 15 unit 7, gate item 3's determinism tolerance (determinism.py --self-test): a removed field, an undocumented field, a changed rule, an undocumented artefact, a documented artefact never compared and a different N, each red where the document and the data disagree, and the comparison red on an unnamed attribute, a changed byte, tied rows reordered with no thread count varied, a changed q-value, a control that changed nothing, a variation that never reached the tool, a file no artefact names and a planned run never made; and, since Phase 15 unit 8, gate item 4's performance thresholds (performance.py --self-test): each threshold exceeded red naming the measurement, the threshold and the observed value, a failed check and a missing record red, an unknown or unmatched runner profile and a refused fixture never green, the data refused for a threshold looser than its ceiling, never shown red, without a rationale, missing, on an informational measurement or below its own baseline, and the document check red on a threshold changed in the data alone"
            GATE_SCRIPT="scripts/ci/traceability.sh"
            GATE_ARGS=(--self-test)
            GATE_PROOF=(
                "selftest: OK"
                "self-test OK -- the gate fails on every injected defect"
                "goldens: self-test OK -- "
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
        manifest)
            GATE_PHASE="15"
            GATE_ITEMS="5"
            GATE_DEFECT="R-TEST-08's manifest verification against a loopback server serving synthetic artefacts, releases listings and a synthetic manifest (no upstream host, manifests/tools.json untouched): one byte altered, the size changed, a pinned SHA-256 differing in its last character alone and an MD5 alone, each red (a) naming the record; HTTP 404, 410 and a redirect to nothing, each red (b) in D-008's availability words and requested once; an unknown newer release red (c) naming its tag, while known tags out of date order, a newer draft and a newer prerelease stay green; a persistent connection reset and a persistent 503 (exactly 3 attempts), a rate-limited releases API (not retried) and every host refusing, each COULD NOT VERIFY and never green, and one reset followed by the asset green with the retry printed; a pinned member wrong in a ZIP, a traversal-named ZIP member, a .deb and a .pkg, and a member absent, each red; every red at once, each named; the not-adopted list honoured, and refused with a placeholder reason, a future date or a stale entry; a non-https record refused; and, as controls on the grader, a case with its defect not injected and a red with another defect's words, each reported as a HARNESS FAILURE"
            GATE_SCRIPT="scripts/ci/nightly-manifest-verify.sh"
            GATE_ARGS=(--self-test)
            GATE_PROOF=(
                "manifest-verify: self-test OK --"
                "H1 a case whose defect was not injected: reported as a HARNESS FAILURE (expected exit 1, got 0)"
            )
            GATE_FLOOR=30
            GATE_UNIT="cases"
            ;;
        flakiness)
            GATE_PHASE="15"
            GATE_ITEMS="6"
            GATE_DEFECT="the specification's flakiness policy over copies of the tree (scripts/ci/flakiness_check.py --self-test; the working tree is only read): surefire's rerun-failing-tests count in a POM's surefire configuration, as a POM property and in .mvn/maven.config, the ignore-test-failures switch in a module POM, a JUnit retry annotation and an extension that intercepts a test's exception, each red RETRY; a harness re-running Maven until it passes, a loop broken by a passing run and a Python loop on Maven's exit status, each red HARNESS-RETRY; an unregistered @Disabled on a method and on a class, a platform condition that turns a test off on Linux, an unrecognised opt-in property and a quarantine tag, a surefire exclusion and excluded groups on a command line, an unrecognised assumption, and an opt-in no harness sets any more, each red with its own words; an assumption that skips on timing red; a register entry with no owner, no fix plan, no issue, a placeholder reason, uncaptured diagnostics, a future date, one that quarantines nothing and one whose class carries only a recognised condition, each red REGISTER; a failure-path test, a Tier A scenario and a test a gate harness grades, each quarantined with a complete entry and each red RELEASE-CRITICAL; a failure-path id another nested class's method shares, red AMBIGUOUS-ID; a missing register and a tree with no test sources refused; the clean tree and a fully registered ordinary quarantine green; and, as controls on the grader, a case with its defect not injected, a red with another defect's words and an injection that changed nothing, each reported as a HARNESS FAILURE"
            GATE_SCRIPT="scripts/ci/flakiness-check.sh"
            GATE_ARGS=(--self-test)
            GATE_PROOF=(
                "flakiness-check: self-test OK --"
                "H1 a case whose defect was not injected: reported as a HARNESS FAILURE"
            )
            GATE_FLOOR=40
            GATE_UNIT="cases"
            ;;
        pipeline)
            GATE_ITEMS="6"
            GATE_DEFECT="none injected: every nightly and release step whose work belongs to a later phase is a stub, and each MUST exit 70 rather than 0 -- the silent pass this phase was told not to ship; every real step MUST exit 0, among them, since Phase 15 unit 5, the real tool manifest verification, which downloads every pinned artefact (about 170 MB, twice: nightly and release) and reads the GitHub releases listings, so it needs the network and is red whenever upstream has changed, and, since Phase 15 unit 7, the determinism suite's synthetic half (every offered Percolator installed from upstream and run twelve times over the synthetic PIN), and, since Phase 15 unit 8, the performance suite's spectra-free half in its quick mode (COMETGUI_PERFORMANCE_QUICK=1: every measurement, fewer repeats, graded against this host's runner profile; 374 s measured as a step of this control); since Phase 15 unit 10 every step runs in a git archive of HEAD, _build/ci-pipeline-sandbox, the checkout a runner would make, never in the working tree"
            GATE_SCRIPT="scripts/ci/run-pipeline-locally.sh"
            GATE_ARGS=(nightly release)
            GATE_PROOF=(
                "0 unexpected."
                "OK -- every executed step behaved as its classification requires."
                "runs from its root, never from the working tree."
            )
            GATE_FLOOR=31
            GATE_UNIT="steps executed and classified"
            ;;
        hardening)
            GATE_PHASE="15"
            GATE_ITEMS="1,2,3,4,6"
            GATE_DEFECT="in a git-archive sandbox, each proved to reach the compiled class or the script it targets before grading: PercolatorCommands requesting -X from a build without XML_OUTPUT (the version matrix's tools part, real binaries from upstream) and matrix.py's manifest reader skipping a row; PercolatorSettings giving the seed from the clock (the determinism suite's synthetic half) and the determinism data's N made 4 (check-doc); a synthetic PIT report with one package below 80% inside a module above it (build.sh --gates-pit-root; the real-PIT proof is the tests control's M); StreamingHashService digesting every chunk twice more and ProcessTree sending no polite stop (run-performance.sh --only, each beside the other's clean measurement), and a runner with no profile graded NO BASELINE; a release-critical test entered in the quarantine register; and a census of every workflow step -- a PHASE-15 stub that waits for neither D-006 nor a runner made red twice; each with its clean counterpart, and a control H on the harness itself. Item 5 is the manifest control's"
            GATE_SCRIPT="scripts/verify-hardening-gates.sh"
            GATE_ARGS=()
            GATE_PROOF=(
                "Every gate rejected its defect and accepted the clean tree."
                "PHASE-15 exit gate items 1, 2, 3, 4 and 6 were proved here"
                "an injection that reached the source but not the bytecode as a HARNESS FAILURE"
            )
            GATE_FLOOR=35
            GATE_UNIT="controls"
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
            GATE_DEFECT="a JavaFX import in the domain, a ProcessBuilder outside the process service, a truncated ArchUnit import, an untested class in a gated package sized so the ratio really falls below 0.90, an untested view-model sized against the package rule from the sandbox's own report, a covered class whose test asserts nothing so its mutations survive, a module whose coverage was never measured and a class excluded from the coverage report -- each graded by scripts/build.sh's own evidence census over a sandbox built once (--gates-census-root), which must accept the clean sandbox, refuse the working tree, and report old or missing evidence STALE or NO EVIDENCE beside the defect rather than read it; and, graded by scripts/build.sh's own mutation grading over real PIT runs (--gates-pit-root, D-014 and D-015), a package at 0% inside a module PIT's own threshold passes, a package whose only non-KILLED mutant is a real TIMED_OUT (which must pass, the mutant named), a targetClasses entry that matches nothing, a critical package excluded from PIT, and a report older than the POM -- the clean module accepted and the working tree refused"
            GATE_SCRIPT="scripts/verify-test-gates.sh"
            GATE_ARGS=()
            GATE_PROOF=("Every gate rejected its defect and accepted the clean tree.")
            GATE_FLOOR=68
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
            GATE_FLOOR=87
            GATE_UNIT="controls"
            ;;
        workflow)
            GATE_PHASE="08"
            GATE_ITEMS="1,2,3,4,5,6,7,8,9"
            GATE_DEFECT="from the injections recorded in handoffs/PHASE-08-worklog.rst (and new ones, each marked so), each into production code of cometgui-tools, cometgui-params-comet or cometgui-workflow in a git-archive sandbox, each proved to have reached the compiled classes and graded on the failing assertion's own words in the named testcase, most against the real pinned Comet binaries: -N dropped from the per-file command, and (NEW) every spectrum file on one Comet command line -- each refused by the file system in the real search from the read-only input directory; (NEW) the -N base put beside the run in runs/, caught by the before/after snapshot; PIN feature columns compared as sets (a renamed column required to stay refused), and (NEW) a header written per input in the real merged PIN; 'no decoys anywhere' judged only for an empty FASTA, (NEW) the FASTA decoy census not consulted before launch, and (NEW) the double-decoy block applied to decoy_search = 1 only (decoy_search = 2 red, 1 green); the merge-pin -> run-percolator edge dropped, run-percolator not declaring the Percolator settings, and the Comet-parameter fingerprint taken from the binary's digest so the preview after a real run names nothing; cancellation no longer delivered to running steps, bounded by the test's own wait and followed by a check that no process started from the sandbox is alive; every re-hash treated as equal (the real changed-input test red), and (NEW) revalidation served from the hash cache (red only where the cache is stale); (NEW) the recorded comet.params hash taken from the binary, and (NEW) one argv recorded for both invocations while the launched argvs stay right; the index check VERSION-BLIND twice -- formats judged < 4 for every release, and (NEW) the first release's formats for every release -- each red in the one real test of both releases on opposite halves; an existing .idx header left out of the pre-run facts; and, as controls on the harness itself, an unchanged file, a missing anchor, an injection that reached the source but not the bytecode, a selection that ran zero tests, a green run graded as red, a red without its diagnostic, and a process left alive"
            GATE_SCRIPT="scripts/verify-workflow-gates.sh"
            GATE_ARGS=()
            GATE_PROOF=(
                "Every gate rejected its defect and accepted the clean tree."
                "PHASE-08 exit gate items 1 to 9 and the index-compatibility check were proved"
                "Two index controls were version-blind (Iv and Iw)"
                "bytecode as a HARNESS ERROR, not as a pass, and a selection that ran zero"
            )
            GATE_FLOOR=110
            GATE_UNIT="controls"
            ;;
        percolator)
            GATE_PHASE="09"
            GATE_ITEMS="1,2,3,4,5,6,7,8,9"
            GATE_DEFECT="from the injections recorded in handoffs/PHASE-09-worklog.rst (and new ones, each marked so), each into production code of cometgui-params-percolator, cometgui-results, cometgui-tools, cometgui-workflow or cometgui-ui in a git-archive sandbox, each built with its upstream modules in one reactor (-pl TOP -am, nothing installed), proved to have reached the compiled classes and graded on the failing assertion's own words in the named testcase, items 1, 2, 4, 5, 7 and 9 against the real Percolator 3.07.1 and 3.09 on a merged PIN the real Comet wrote: the probe's target/decoy judgement of a table disabled, so a capability is granted without its observable; (NEW) the table reader dropping the first data row (the real tables and the real run's independently counted rows), and (NEW) run-percolator no longer checking that an artefact exists (an empty one still refused); -X requested whenever the build is capable (red in the recorded argv of the real 3.07.1 run with Limelight off), and (NEW) whenever a stage needs it, capable or not (the real 3.09 handed -X and failing); VERSION-BLIND three times, each with the real 3.07.1/3.09 pair required to stay green -- Limelight available iff a candidate is older than 3.09, (NEW) the default chosen by that rule (the future 3.10 and the inferred macOS-like 3.07.1 red), and (NEW) the Percolator section saying Limelight can run whenever a build older than 3.09 is known (the view-model's and the GUI's inferred-claim tests red); (NEW) the skip reason no longer naming the missing capability, red in the resolver, the view-model, the GUI and the real run's provenance.json; the pre-launch PIN check bypassed, so the real zero-decoy PIN reaches the real Percolator; (NEW) the derived run no longer recording run-comet among the Comet results it reuses, and the rerun's 'every Comet result must be reused' and 'nothing changed' refusals disabled; (NEW) the effective seed dropped from provenance.json of every run that did not succeed (the real failed runs red, gate 7's successful runs green); (NEW) at most three weight splits read (the two- and four-split files red, the real three-split files green), and the same-features-per-split check disabled; raw outputs never made read-only, and (NEW) parsing writing back to the raw file it parsed; (NEW, phase 10, D-013) the command builder no longer emitting --no-analytics (red in the argv read back from provenance.json of every real run), the probe no longer passing it on its later runs, and the probe never granting NO_ANALYTICS_OPTION; and, as controls on the harness itself, an unchanged file, a missing anchor, an injection that reached the source but not the bytecode, a selection that ran zero tests, a green run graded as red and a red without its diagnostic"
            GATE_SCRIPT="scripts/verify-percolator-gates.sh"
            GATE_ARGS=()
            GATE_PROOF=(
                "Every gate rejected its defect and accepted the clean tree."
                "PHASE-09 exit gate items 1 to 9 and the capability probe were proved here"
                "Three controls were version-blind (3v, 3w and 3u)"
                "bytecode as a HARNESS ERROR, not as a pass, and a selection that ran zero"
            )
            GATE_FLOOR=109
            GATE_UNIT="controls"
            ;;
        results)
            GATE_PHASE="10"
            GATE_ITEMS="1,2,3,4,5,6,7,8"
            GATE_DEFECT="from the injections recorded in handoffs/PHASE-10-worklog.rst (and new ones, each marked so), each into production code of cometgui-results, cometgui-workflow, cometgui-ui or cometgui-app in a git-archive sandbox, each built with its upstream modules in one reactor (-pl TOP -am, nothing installed), proved to have reached the compiled classes and graded on the failing assertion's own words in the named testcase: the one q-value predicate made exclusive (both stores and the predicate's tests red at exactly the cutoff, the unknown category green); (NEW) a refused filter text still changing the filter, (NEW) the peptide filter's edit also setting the PSM filter, and (NEW) the range check widened to [0, 10]; a listener on the one display-filter state launching /bin/true through the application's one process runner (the headless GUI gate-2 test); the disk store's count loop skipping the last row (the in-memory store green), (NEW) the in-memory tally counting failing rows as passing (the disk store green), and the Passing count on screen bound to the failing count (the headless GUI counts test); the export opening the raw table for writing (a read-only raw output under outputs/ refuses it, a writable table elsewhere green), and (NEW) finalise-results writing the store's index under outputs/; the sidecar's cutoff taken from the default filter, and its rowsWritten one too many; (NEW) the store factory never switching to disk (the -Xmx64m child dies of OutOfMemoryError on the 1 000 000-row fixture, the readAll negative control unchanged), the results table's items accumulating (the large-fixture GUI test), and the table view-model asking for every row as one page; the n - 1 standard deviation, the mean absolute weight summing signed values, (NEW) ties no longer sharing a rank, (NEW) the split count fixed at three (the two- and four-split files red, the real three-split files green), and (NEW) a zero weight counted as positive; unknown-q-value rows written into the passing export, and (NEW) an empty q-value classed as failing rather than unknown; and, as controls on the harness itself, an unchanged file, a missing anchor, an injection that reached the source but not the bytecode, a selection that ran zero tests, a green run graded as red and a red without its diagnostic"
            GATE_SCRIPT="scripts/verify-results-gates.sh"
            GATE_ARGS=()
            GATE_PROOF=(
                "Every gate rejected its defect and accepted the clean tree."
                "PHASE-10 exit gate items 1 to 8 were proved here"
                "bytecode as a HARNESS ERROR, not as a pass, and a selection that ran zero"
            )
            GATE_FLOOR=100
            GATE_UNIT="controls"
            ;;
        pdv)
            GATE_PHASE="11"
            GATE_ITEMS="1,2,3,4,5,6,7,8"
            GATE_DEFECT="from the injections recorded in handoffs/PHASE-11-worklog.rst (and new ones, each marked so), each into production code of cometgui-tools, cometgui-workflow, cometgui-ui or cometgui-app in a git-archive sandbox, each built with its upstream modules in one reactor (-pl TOP -am, nothing installed), proved to have reached the compiled classes and graded on the failing assertion's own words in the named testcase, with every process left under the sandbox ended after each run: Draw drawing without installing PDV (the view-model's install-then-draw), and (NEW) the install's start holding the interface thread (the first-use GUI test, real Comet, Percolator and PDV from an empty cache through a held transfer, finds the interface frozen); the PNG judge's blank-figure floor removed, (NEW) PDV's exit 0 with a requested figure missing no longer NO_FIGURE, and (NEW) -k p for -k s, both red against the real PDV 2.7.0; (NEW) the format check bypassed before launch, and (NEW) mzXML marked supported; the launcher's other-program refusal removed, (NEW) PDV started before its launch is recorded, (NEW) the JAR's MD5 recorded as its SHA-256, and (NEW) the manifest-version check removed; the denovo-gui command given port + 1, (NEW) the controller sending the next row's reference (200 from PDV, red only in the in-JVM observer of the real PDV), (NEW) a window's shutdown no longer ending PDV's process, another Results run no longer closing the window, and (NEW) the application's stop() no longer closing PDV's windows (the live-window GUI test); score and q-value swapped, (NEW) PEP fed the q-value, accessions cut to the first protein, (NEW) a static modification's delta rounded, and (NEW) the decoy flag constant, each graded on the fidelity suite against the real K562 sources; (NEW) spectra_ref by scan number, by 1-based file position and to a shared spectrum's first row, each with the exporter's own read-back of its references also switched off so that the fidelity suite is what catches it; the C-terminal refusal disabled, (NEW) the PSM's name dropped from the refusal, and (NEW) a refused export leaving its staging directory; and, as controls on the harness itself, an unchanged file, a missing anchor, an injection that reached the source but not the bytecode, a test method renamed so its selection ran zero tests, a green run graded as red and a red without its diagnostic"
            GATE_SCRIPT="scripts/verify-pdv-gates.sh"
            GATE_ARGS=()
            GATE_PROOF=(
                "Every gate rejected its defect and accepted the clean tree."
                "PHASE-11 exit gate items 1 to 8 were proved here"
                "bytecode as a HARNESS ERROR, not as a pass, and a selection that ran zero"
            )
            GATE_FLOOR=107
            GATE_UNIT="controls"
            ;;
        limelight)
            GATE_PHASE="12"
            GATE_ITEMS="1,2,3,4,5,6,7,8"
            GATE_DEFECT="from the injections recorded in handoffs/PHASE-12-worklog.rst (and new ones, each marked so), each into production code of cometgui-domain, cometgui-params-percolator, cometgui-tools, cometgui-workflow, cometgui-ui or cometgui-app in a git-archive sandbox, each built with its upstream modules in one reactor (-pl TOP -am, nothing installed), proved to have reached the compiled classes and graded on the failing assertion's own words in the named testcase, with every process left under the sandbox ended after each run: the output judge's root check reduced to 'has a name', (NEW) its schema validation skipped (Phase 00's two schema-invalid corruptions accepted), and (NEW) the conversion's output.sha256 recorded as the MD5 (red against the real Comet, Percolator 3.07.1 and converter run); (NEW) the converter adapter trusting exit 0 with a missing or empty file, (NEW) the judge's existence check removed (red against the real converter exiting 0 on an unknown option), and (NEW) its emptiness check removed; CompatibilityAction.forStage offering the rerun whenever any build is selected (R-LL-02's branch), the action's label from the literal 3.07.1 (red only for the hypothetical 3.08.2 and the unreleased 3.10, the real Linux set green), and the conversion controls enabled for an incompatible run (the pane and the headless GUI against the real 3.09 run); the conversion of a run that is not ready no longer refused (the real 3.09 run's), (NEW) the compatible rerun's choice enabling no downstream stage (the headless GUI's real rerun), and (NEW) the derived run no longer recording the Percolator its source ran (the real rerun's provenance); (NEW) the q cutoff's default 0.05, (NEW) -q not emitted (red against the real converter only where the cutoff is not its own default 0.01), and (NEW) saving the cutoff also writing the display filters; (NEW) the workflow's upload confirmation reusable, the port's single-send check always passing, a changed server no longer withdrawing the destination, (NEW) Upload sending as soon as the destination is shown (the view-model and the headless GUI against the fake), (NEW) the upload client given no proxy so the test guard's sinkhole is bypassed (graded ONLY against this machine's own non-loopback address, nothing leaving the machine), and plain http accepted to any host; (NEW) the upload no longer adding the key to the redactor it writes with, (NEW) the key written unredacted to the upload log (the leak test's walk of every file), (NEW) a credential admitted without being registered with the redactor, and (NEW) the key field not emptied (the pane and the headless GUI's gate-7 test); saveSettings' decoy refusal unreachable, decoy import enabled whenever the settings are (view-model and pane), (NEW) a pout XML without decoys judged able to import them (the request and the workflow), and the 'decoys present but not imported' refusal unreachable; and, as controls on the harness itself, an unchanged file, a missing anchor, an injection that reached the source but not the bytecode, a test method renamed so its selection ran zero tests, a green run graded as red and a red without its diagnostic"
            GATE_SCRIPT="scripts/verify-limelight-gates.sh"
            GATE_ARGS=()
            GATE_PROOF=(
                "Every gate rejected its defect and accepted the clean tree."
                "PHASE-12 exit gate items 1 to 8 were proved here"
                "bytecode as a HARNESS ERROR, not as a pass, and a selection that ran zero"
            )
            GATE_FLOOR=130
            GATE_UNIT="controls"
            ;;
        provenanceui)
            GATE_PHASE="13"
            GATE_ITEMS="1,2,3,4,5,6"
            GATE_DEFECT="from the injections recorded in handoffs/PHASE-13-worklog.rst (and new ones, each marked so), each into production code of cometgui-provenance, cometgui-workflow, cometgui-ui or cometgui-app in a git-archive sandbox, each built with its upstream modules in one reactor (-pl TOP -am, nothing installed), proved to have reached the compiled classes and graded on the failing assertion's own words in the named testcase, with every process left under the sandbox ended after each run, items 1 to 6 against the real Comet, Percolator and converter run whose digests the gate test computes itself: the database recorded as an input by neither hash-inputs nor run-comet, and (NEW) the product's one hasher wrong in the MD5 alone (red only because the gate's digests are its own); (NEW) Comet's tool record without its probed capabilities, (NEW) Percolator's without its artefact identity, and (NEW) Comet's without its release tag; (NEW) one Comet execution of a two-file run dropped from the record; (NEW) a failed or cancelled stage's outputs recorded as completed (the real failed and cancelled runs), and the read model warning only of FAILED files; the RST printing a log's MD5 under SHA-256 (the export still byte-identical to the run's own report), and (NEW) Export JSON copying the sealed file instead of rendering the loaded model; the JSON writer skipping redaction over 200 characters, (NEW) the RST writer redacting nothing, a read-model warning not redacted, and the app's read model built over the pattern rules instead of the session's redactor; P13-1's AccessDeniedException no longer retried, one rename attempt fewer, and ManifestReader keeping a FileChannel open; P13-7's verification comparing the SHA-256 only; (NEW) P13-8's record opened on the interface thread (the view-model and the headless GUI's thread test); (NEW) Copy SHA-256 copying the MD5 (the view-model and the headless GUI's clipboard); and, as controls on the harness itself, an unchanged file, a missing anchor, an injection that reached the source but not the bytecode, a test method renamed so its selection ran zero tests, a green run graded as red, a red without its diagnostic, a run counted at zero tests, and an XML report holding fewer testcases than Maven ran"
            GATE_SCRIPT="scripts/verify-provenance-ui-gates.sh"
            GATE_ARGS=()
            GATE_PROOF=(
                "Every gate rejected its defect and accepted the clean tree."
                "PHASE-13 exit gate items 1 to 6 were proved here"
                "bytecode as a HARNESS ERROR, not as a pass, and a selection that ran zero"
            )
            GATE_FLOOR=96
            GATE_UNIT="controls"
            ;;
        e2e)
            GATE_PHASE="14"
            GATE_ITEMS="1,2,3,4,5,6,7"
            GATE_DEFECT="from the record in handoffs/PHASE-14-worklog.rst: the end-to-end tier run ONCE in a git-archive sandbox under LC_ALL=C.UTF-8, which the harness exports itself -- Tier A's canonical and no-XML scenarios and Tier B's against the archive build-app-image.sh builds there and each class installs fresh, every @Test method counted as RUN from that run's surefire XML (none skipped), Comet-not-rerun named in both tiers, the class's own locale assertion shown failing with no locale, and Tier B's launched-process locale control; every failure-path test the map names run fresh and check-failure-paths.sh graded over exactly those reports (and its --self-test); check-no-fixed-sleep.sh and release-no-test-bridge.sh on the clean archive, each with its --self-test; and, each into PRODUCTION code, proved in the bytecode and graded on the failing assertion's own words in the named step, with an earlier step required green: the compatible rerun touching the first run's Comet outputs (item 3, red on the disk assertion), and gate item 5's three -- QValueFilter's <= made < (red at the boundary step), the searched FASTA dropped from provenance.json (red at step 28) and the Limelight upload's final submit skipped while the screen says submitted (red at step 27); and, as controls on the harness itself, a missing anchor, an injection that reached the source but not the bytecode, a green graded as red, a red without its words, and a real run of an end-to-end class without its opt-in in which every test was skipped, each a HARNESS FAILURE. Needs network to the tools' GitHub release hosts and the D-006 local fixture; no CI job runs it"
            GATE_SCRIPT="scripts/verify-e2e-gates.sh"
            GATE_ARGS=()
            GATE_PROOF=(
                "Every gate rejected its defect and accepted the clean tree."
                "PHASE-14 exit gate items 1 to 7 were proved here"
                "bytecode, and a run in which zero tests ran, as a HARNESS FAILURE"
            )
            GATE_FLOOR=50
            GATE_UNIT="controls"
            ;;
        *)
            return 1
            ;;
    esac
    return 0
}

# gate_locks NAME -- the working-tree outputs a control WRITES and another
# control writes or reads, as lock names.  Two controls that hold a common lock
# never run at the same time.  Found by audit, not assumed from the sandboxes:
#
#   docs-tree  every real Sphinx build of docs/ -- docs-build.sh, which the docs
#              control and the pipeline's release step run, and the pipeline's
#              link check -- deletes and rewrites docs/_build/ and writes the
#              generated pages docs/developer/traceability.rst and
#              docs/_generated/ into the source tree; the traceability control's
#              self-test copies docs/ while that happens.  The pipeline and docs
#              also share _build/docs-gate.
#   sbom-out   sbom.sh deletes and rewrites _build/sbom/, which the sbom
#              control and the pipeline's release step run, and the depscan
#              control reads _build/sbom/cometgui-sbom.json.
#
# The pipeline control holds both although, since Phase 15 unit 10, it runs
# every step in its own checkout (_build/ci-pipeline-sandbox) and writes
# neither in the working tree -- nor cometgui-*/target, nor anything but
# _build/ci-transcript/, which no other control reads (measured by that unit:
# docs/_build, docs/_generated, the generated traceability page, _build/sbom,
# _build/package and every module's target/ byte-for-byte unchanged by a run).
# The locks are kept because dropping them would buy no time -- the tests
# control is the long pole -- and one measured run is not an audit.
#
# The hardening control (Phase 15 unit 10) holds none: it writes only
# _build/hardening-gate-* and the tests' own temporary directories, and reads
# _build/m2repo offline (its control C digests it, the working tree's git
# status and its cometgui-*/target before and after).
gate_locks() {
    case "$1" in
        docs|traceability) echo "docs-tree" ;;
        sbom|depscan)      echo "sbom-out" ;;
        pipeline)          echo "docs-tree sbom-out" ;;
        *)                 echo "" ;;
    esac
}

# compute_jobs -- the default job count, from the cores this process may use.
#
#   <= 4 cores          1, serial.  Every GitHub-hosted runner the project
#                       uses has 2 or 4 (owner, 2026-11-05), and serial is the
#                       behaviour every floor here was measured under.
#   above 4             cores / CORES_PER_JOB, at least 1, at most JOBS_CAP.
#
# CORES_PER_JOB = 8.  One heavy control is a Maven reactor running real Comet,
# Percolator, PDV and headless GUI tests.  Measured on the 64-core development
# machine on 2026-11-05 from this container's own cgroup CPU counter (not the
# load average, which other tenants of that machine drove to 50 that day), the
# tests control's whole-reactor build used 1 to 4 cores in 30-second samples
# through cometgui-app's real-tool tests, with momentary bursts to about 17
# (handoffs/FIX-BUILD-TIME-worklog.rst).  Eight cores a job is twice the
# sustained figure, which matters because some controls grade time and
# patience -- GUI tests wait on the interface, two suites hold wall-clock
# budgets, and PIT's grading moves with load -- and none of those may be eased
# to make a concurrent run pass.  So a 16-core machine runs 2, a 32-core one 4.
#
# JOBS_CAP = 4.  The run cannot end before its longest control does, the tests
# control; with the per-control times recorded at the Phase 14 and E12 gate
# runs and the tests control's own after this change, four jobs already finish
# every other control inside that one's run, so a fifth slot could only add
# load (the worklog has the arithmetic).
compute_jobs() {
    local cores jobs
    cores="$(nproc 2>/dev/null || echo 1)"
    case "${cores}" in ''|*[!0-9]*) cores=1 ;; esac
    if [ "${cores}" -le 4 ]; then
        jobs=1
    else
        jobs=$(( cores / CORES_PER_JOB ))
        [ "${jobs}" -lt 1 ] && jobs=1
        [ "${jobs}" -gt "${JOBS_CAP}" ] && jobs="${JOBS_CAP}"
    fi
    CORES="${cores}"
    JOBS="${jobs}"
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
        manifest)
            sed -n 's/.*manifest-verify: self-test OK -- \([0-9][0-9]*\)\/[0-9][0-9]* cases.*/\1/p' -- "${log}" | head -1 ;;
        flakiness)
            sed -n 's/.*flakiness-check: self-test OK -- \([0-9][0-9]*\)\/[0-9][0-9]* cases.*/\1/p' -- "${log}" | head -1 ;;
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
        workflow)
            sed -n 's/.*SUMMARY: \([0-9][0-9]*\) control(s) passed, 0 failed.*/\1/p' -- "${log}" | head -1 ;;
        percolator)
            sed -n 's/.*SUMMARY: \([0-9][0-9]*\) control(s) passed, 0 failed.*/\1/p' -- "${log}" | head -1 ;;
        results)
            sed -n 's/.*SUMMARY: \([0-9][0-9]*\) control(s) passed, 0 failed.*/\1/p' -- "${log}" | head -1 ;;
        pdv)
            sed -n 's/.*SUMMARY: \([0-9][0-9]*\) control(s) passed, 0 failed.*/\1/p' -- "${log}" | head -1 ;;
        limelight)
            sed -n 's/.*SUMMARY: \([0-9][0-9]*\) control(s) passed, 0 failed.*/\1/p' -- "${log}" | head -1 ;;
        provenanceui)
            sed -n 's/.*SUMMARY: \([0-9][0-9]*\) control(s) passed, 0 failed.*/\1/p' -- "${log}" | head -1 ;;
        e2e)
            sed -n 's/.*SUMMARY: \([0-9][0-9]*\) control(s) passed, 0 failed.*/\1/p' -- "${log}" | head -1 ;;
        hardening)
            sed -n 's/^SUMMARY: \([0-9][0-9]*\) control(s) passed, 0 failed$/\1/p' -- "${log}" | head -1 ;;
    esac
}

# ----------------------------------------------------------------- plumbing --
usage() {
    cat <<USAGE
${SCRIPT_NAME} -- run every falsifiability control the project has and prove
that every PHASE-01, PHASE-02, PHASE-04, PHASE-05, PHASE-06, PHASE-07,
PHASE-08, PHASE-09, PHASE-10, PHASE-11, PHASE-12, PHASE-13, PHASE-14 and
PHASE-15 gate still fails on the defect it exists to catch.

Usage:
  bash scripts/${SCRIPT_NAME}                 run every control
  bash scripts/${SCRIPT_NAME} --only NAME     run one control (repeatable, or
                                              comma-separated)
  bash scripts/${SCRIPT_NAME} --jobs N        run up to N controls at once (1 is
                                              serial); COMETGUI_GATE_JOBS=N does
                                              the same.  Default: 1 at 4 cores or
                                              fewer, else one per ${CORES_PER_JOB} cores,
                                              at most ${JOBS_CAP}
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
.venv/ and _build/m2repo; the project-local font stack; network access to
https://api.osv.dev for the dependency-scan control; network access to
github.com (and where it redirects release downloads) and api.github.com for
the pipeline control, whose nightly and release runs include the real tool
manifest verification; network access to github.com and where it redirects for
the hardening control, whose version matrix installs every offered Linux build
from its pinned URL; and, for the e2e control,
the D-006 local fixture under scratch/ and network access to github.com and
*.githubusercontent.com, where the tools' pinned release URLs are served.

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
    printf '  PHASE-07, 08:n of PHASE-08, 09:n of PHASE-09, 10:n of PHASE-10, 11:n of\n'
    printf '  PHASE-11, 12:n of PHASE-12, 13:n of PHASE-13, 14:n of PHASE-14, 15:n of\n'
    printf '  PHASE-15.\n'
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
    printf '  PHASE-08 items: 1 a real two-file Comet run with distinct -N bases from a\n'
    printf '  read-only input directory; 2 nothing written outside the run directory;\n'
    printf '  3 one merged PIN header, the summed rows, a feature-column mismatch naming\n'
    printf '  the files; 4 no decoys and decoy_search = 0 blocked before Comet; 5 decoys\n'
    printf '  in the FASTA and decoy_search != 0 blocked; 6 the rerun preview per\n'
    printf '  scenario; 7 cancellation killing Comet and its descendants, records\n'
    printf '  parsable; 8 a changed input refusing reuse, naming the file; 9 one argv per\n'
    printf '  spectrum file and the archived comet.params hash of the executed file.  The\n'
    printf '  same control also proves the index-compatibility check tier 1 assigned.\n'
    printf '  PHASE-09 items: 1 a real Percolator run on the merged PIN whose PSM,\n'
    printf '  peptide and weights artefacts parse; 2 XML produced with an XML-capable\n'
    printf '  build and no XML option for 3.09, asserted on the recorded argument array;\n'
    printf '  3 the default resolved from observed capability, re-evaluated on toggling,\n'
    printf '  a non-XML default and Limelight unavailable without an XML-capable build;\n'
    printf '  4 a skipped newer version named with its missing capability in the UI and\n'
    printf '  in provenance; 5 a zero-decoy PIN failing before Percolator is launched;\n'
    printf '  6 the compatible-version rerun, Comet untouched; 7 the effective seed in\n'
    printf '  provenance for every run; 8 the weights split count read from the\n'
    printf '  artefact; 9 raw outputs byte-identical across parsing and filtering.\n'
    printf '  PHASE-10 items: 1 both default filters 0.01, independent, inclusive at\n'
    printf '  exactly 0.01, rejecting values outside [0, 1]; 2 a filter change launching\n'
    printf '  no process; 3 displayed counts equal to independent counts at 0, 0.005,\n'
    printf '  0.01 and 1; 4 raw Percolator files byte-identical after filtering and\n'
    printf '  export; 5 an export carrying the run ID, the cutoff and the before/after\n'
    printf '  row counts; 6 the large fixture within its time and heap budget, the UI\n'
    printf '  bound to a paged model; 7 weights values, ranking and sign consistency\n'
    printf '  against an independent computation; 8 unknown q-values counted and shown\n'
    printf '  as their own category, identically in UI and export.\n'
    printf '  PHASE-11 items: 1 PDV installed on demand from an empty cache, the search\n'
    printf '  workflow usable while it downloads; 2 a real PDV CLI figure from a real\n'
    printf '  Comet pepXML, judged by its content; 3 an unsupported spectrum format\n'
    printf '  giving the specified error before PDV is launched; 4 the JAR checksum and\n'
    printf '  version in the run'"'"'s provenance whenever PDV is launched; 5 an ephemeral\n'
    printf '  port, /ready polled, a selection changing the displayed spectrum through\n'
    printf '  the control API under a timeout; 6 the mzTab accurate and true to the\n'
    printf '  source files; 7 spectra_ref resolving to the producing spectrum where file\n'
    printf '  position and scan number differ; 8 an unrepresentable PSM failing the\n'
    printf '  export loudly, by name.\n'
    printf '  PHASE-12 items: 1 a real conversion structurally validated and\n'
    printf '  checksummed into provenance; 2 a converter exit 0 with a missing or empty\n'
    printf '  XML failing the stage; 3 without an XML-capable Percolator, the controls\n'
    printf '  disabled and explained before any attempt, the action what the platform\n'
    printf '  can do; 4 the compatible rerun converting without rerunning Comet, both\n'
    printf '  Percolator executions in provenance; 5 the Limelight q cutoff 0.01 and\n'
    printf '  independent of the display filters; 6 upload only on an explicit action,\n'
    printf '  the destination first, the fake in tests; 7 a seeded credential in no\n'
    printf '  log, provenance record or export; 8 decoy import against a run without\n'
    printf '  decoys prevented at configuration time.\n'
    printf '  PHASE-13 items: 1 every input and output of a completed real run with both\n'
    printf '  digests, recomputed independently from disk; 2 every tool'"'"'s version, path\n'
    printf '  and both digests matching the files in the tool cache; 3 every process'"'"'s\n'
    printf '  argument array, one per spectrum file, present and displayable; 4 a failed\n'
    printf '  and a cancelled run opening with partial outputs marked; 5 JSON and RST\n'
    printf '  from one model, the RST'"'"'s facts asserted against the JSON; 6 no secret in\n'
    printf '  either export.\n'
    printf '  PHASE-14 items: 1 the canonical scenario in Tier A from an empty tool cache\n'
    printf '  through provenance and restart; 2 the canonical scenario in Tier B against the\n'
    printf '  packaged artefact; 3 the no-XML scenario in both tiers, Comet not rerun; 4\n'
    printf '  every specified failure path with a passing test; 5 an inverted q-value\n'
    printf '  comparison, a dropped provenance entry and a bypassed downstream stage each\n'
    printf '  failing the canonical test; 6 no fixed sleep; 7 the bridge-absence check\n'
    printf '  failing on a build that includes the bridge.\n'
    printf '  PHASE-15 items: 1 every offered version/platform combination with a passing\n'
    printf '  matrix run, every other one recorded unavailable; 2 the mutation gate at\n'
    printf '  >= 80%% in each critical package (hardening grades it cheaply, the tests\n'
    printf '  control over real PIT runs); 3 determinism repeats within the written\n'
    printf '  tolerance; 4 performance thresholds on a recorded runner profile; 5 the\n'
    printf '  manifest verification job demonstrably failing when a checksum is altered\n'
    printf '  (the manifest control alone); 6 no release-critical test quarantined (the\n'
    printf '  flakiness policy, enforced).\n'
    printf '  D-001 is the GPL-3.0 licence obligation, a phase deliverable rather than a\n'
    printf '  numbered gate item.  See phases/PHASE-01-build-skeleton.rst,\n'
    printf '  phases/PHASE-02-app-shell.rst, phases/PHASE-04-provenance-core.rst,\n'
    printf '  phases/PHASE-05-tool-registry.rst, phases/PHASE-06-comet-param-model.rst,\n'
    printf '  phases/PHASE-07-comet-param-ui.rst, phases/PHASE-08-workflow-comet.rst,\n'
    printf '  phases/PHASE-09-percolator.rst, phases/PHASE-10-results.rst,\n'
    printf '  phases/PHASE-11-pdv.rst, phases/PHASE-12-limelight.rst,\n'
    printf '  phases/PHASE-13-provenance-ui.rst, phases/PHASE-14-e2e.rst and\n'
    printf '  phases/PHASE-15-hardening.rst.\n\n'
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

# gate_banner NAME -- the control's heading, before its result.
gate_banner() {
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
}

# exec_gate NAME -- run one control's harness, its output to its own log.
# Sets RUN_RC and RUN_ELAPSED.
exec_gate() {
    local name="$1"
    gate_spec "${name}"
    local log="${LOGS}/${name}.log" started
    RUN_RC=0
    started="$(date +%s)"
    if [ "${#GATE_ARGS[@]}" -gt 0 ]; then
        bash "${ROOT}/${GATE_SCRIPT}" "${GATE_ARGS[@]}" >"${log}" 2>&1 || RUN_RC=$?
    else
        bash "${ROOT}/${GATE_SCRIPT}" >"${log}" 2>&1 || RUN_RC=$?
    fi
    RUN_ELAPSED=$(( $(date +%s) - started ))
}

# run_gate NAME -- run one control and grade it (the serial path).
run_gate() {
    local name="$1"
    gate_banner "${name}"
    exec_gate "${name}"
    grade_gate "${name}" "${RUN_RC}" "${RUN_ELAPSED}" ""
}

# grade_gate NAME RC ELAPSED WHY-NOT-RUN -- grade one control from its log and
# exit status, and record it.  WHY-NOT-RUN, when not empty, is the reason no
# exit status exists (a worker that died); the control then FAILS.
grade_gate() {
    local name="$1" rc="$2" elapsed="$3" lost="$4"
    gate_spec "${name}"

    local log="${LOGS}/${name}.log"
    local rel="${log#"${ROOT}/"}"
    local cmd="bash ${GATE_SCRIPT}"
    [ "${#GATE_ARGS[@]}" -gt 0 ] && cmd="${cmd} ${GATE_ARGS[*]}"

    # --- grade it -----------------------------------------------------------
    local verdict="PASS" why="" count=""

    if [ -n "${lost}" ]; then
        verdict="FAIL"
        why="${lost}. See ${rel}"
    elif [ ! -s "${log}" ]; then
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

# fingerprint DIR -- one digest of every path, size and mtime under DIR,
# following links (tools/ and scratch/ are reached through them in sandboxes).
# Python's bytecode caches are left out: CPython writes them on a module's
# first import, by write-then-rename, so concurrent writers are safe by design
# and a fresh cache file is not a control writing what another reads.
fingerprint() {
    ( cd -- "${ROOT}" && find -L "$1" \( -name __pycache__ -prune \) -o -printf '%P %s %T@\n' 2>/dev/null \
        | LC_ALL=C sort | sha256sum | cut -d' ' -f1 )
}

# kill_tree PID -- end a worker and everything under it, children first.
kill_tree() {
    local pid="$1" child
    for child in $(ps -o pid= --ppid "${pid}" 2>/dev/null); do
        kill_tree "${child}"
    done
    kill -TERM "${pid}" 2>/dev/null || true
}

declare -A WORKER_PID=()
declare -A WORKER_DONE=()

on_interrupt() {
    local name
    printf '\n%s: interrupted; ending every running control.\n' "${SCRIPT_NAME}" >&2
    for name in "${!WORKER_PID[@]}"; do
        [ -n "${WORKER_DONE[${name}]:-}" ] || kill_tree "${WORKER_PID[${name}]}"
    done
    exit 1
}

# run_concurrently SELECTED... -- run the controls up to JOBS at a time,
# never two that share a lock, then grade every one of them in the order
# given.  Each worker records "<exit status> <seconds>" in STATE/<name>.done
# only once its harness has ended; a control with no such file is graded as
# a failure, never as a pass.
run_concurrently() {
    # One state directory per invocation, so that a second invocation running
    # other controls at the same time cannot remove this one's exit statuses.
    local state
    state="$(mktemp -d "${LOGS}/.state.XXXXXX")"
    trap on_interrupt INT TERM

    local -a pending=("$@")
    local -A held=()
    local total="$#" finished=0 running=0 name lock free
    local -a still=()

    while [ "${#pending[@]}" -gt 0 ] || [ "${running}" -gt 0 ]; do
        # Start whatever may start, in the order given.
        still=()
        for name in "${pending[@]}"; do
            free=1
            for lock in $(gate_locks "${name}"); do
                [ -z "${held[${lock}]:-}" ] || free=0
            done
            if [ "${running}" -lt "${JOBS}" ] && [ "${free}" -eq 1 ]; then
                for lock in $(gate_locks "${name}"); do held[${lock}]="${name}"; done
                (
                    trap - INT TERM
                    exec_gate "${name}"
                    printf '%s %s\n' "${RUN_RC}" "${RUN_ELAPSED}" >"${state}/${name}.tmp"
                    mv -- "${state}/${name}.tmp" "${state}/${name}.done"
                ) &
                WORKER_PID[${name}]=$!
                running=$((running + 1))
                printf '  %s  started  %-13s (%d running)\n' "$(date +%T)" "${name}" "${running}"
            else
                still+=("${name}")
            fi
        done
        pending=("${still[@]}")
        [ "${running}" -gt 0 ] || continue

        # Wait for one to end, then collect every one that has.
        wait -n 2>/dev/null || true
        for name in "${!WORKER_PID[@]}"; do
            [ -z "${WORKER_DONE[${name}]:-}" ] || continue
            kill -0 "${WORKER_PID[${name}]}" 2>/dev/null && continue
            wait "${WORKER_PID[${name}]}" 2>/dev/null || true
            WORKER_DONE[${name}]=1
            running=$((running - 1))
            finished=$((finished + 1))
            for lock in $(gate_locks "${name}"); do unset "held[${lock}]"; done
            if [ -f "${state}/${name}.done" ]; then
                printf '  %s  finished %-13s exit %s in %ss (%d of %d)\n' "$(date +%T)" \
                    "${name}" "$(cut -d' ' -f1 "${state}/${name}.done")" \
                    "$(cut -d' ' -f2 "${state}/${name}.done")" "${finished}" "${total}"
            else
                printf '  %s  LOST     %-13s its worker ended without an exit status (%d of %d)\n' \
                    "$(date +%T)" "${name}" "${finished}" "${total}"
            fi
        done
    done
    trap - INT TERM

    # Grade, in the order given -- the order a serial run prints.
    local rc elapsed
    for name in "$@"; do
        gate_banner "${name}"
        if [ -f "${state}/${name}.done" ]; then
            read -r rc elapsed < "${state}/${name}.done"
            grade_gate "${name}" "${rc}" "${elapsed}" ""
        else
            grade_gate "${name}" 1 0 \
                "no exit status was recorded: the control's worker died or was killed before its harness ended, so nothing it did can be counted"
        fi
    done
    rm -rf -- "${state}"
}

# -------------------------------------------------------------------- main --
main() {
    local -a selected=()
    local only="" jobs_from="computed"
    local jobs_option="${COMETGUI_GATE_JOBS:-}"
    [ -n "${jobs_option}" ] && jobs_from="COMETGUI_GATE_JOBS"

    while [ "$#" -gt 0 ]; do
        case "$1" in
            -h|--help) usage; exit 0 ;;
            --list)    list_gates; exit 0 ;;
            --only)
                [ "$#" -ge 2 ] || die "--only needs a gate name (try --list)"
                only="${only}${only:+,}$2"
                shift 2
                ;;
            --jobs)
                [ "$#" -ge 2 ] || die "--jobs needs a number (1 is serial)"
                jobs_option="$2"
                jobs_from="--jobs"
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

    compute_jobs
    if [ -n "${jobs_option}" ]; then
        case "${jobs_option}" in
            ''|*[!0-9]*|0*) die "${jobs_from}: '${jobs_option}' is not a whole number of at least 1 (1 is serial)" ;;
        esac
        JOBS="${jobs_option}"
    fi

    cd -- "${ROOT}"
    mkdir -p -- "${LOGS}"

    printf '===============================================================================\n'
    printf ' %s -- every PHASE-01, PHASE-02, PHASE-04, PHASE-05, PHASE-06, PHASE-07, PHASE-08, PHASE-09, PHASE-10, PHASE-11, PHASE-12, PHASE-13, PHASE-14 and PHASE-15 gate must be seen to fail\n' "${SCRIPT_NAME}"
    printf '===============================================================================\n'
    printf '  repository   %s\n' "${ROOT}"
    printf '  controls     %d of %d\n' "${#selected[@]}" "${#ALL_GATES[@]}"
    printf '  logs         %s\n' "${LOGS#"${ROOT}/"}"
    if [ "${JOBS}" -eq 1 ]; then
        printf '  jobs         1 -- SERIAL, one control after another (%s; %s core(s) available)\n' \
            "${jobs_from}" "${CORES}"
    else
        printf '  jobs         %d -- CONCURRENT, up to %d controls at once (%s; %s core(s) available)\n' \
            "${JOBS}" "${JOBS}" "${jobs_from}" "${CORES}"
    fi
    printf '  expect this to take several minutes; see --help\n'

    preflight "${selected[@]}"

    local started
    started="$(date +%s)"
    local name
    if [ "${JOBS}" -eq 1 ]; then
        for name in "${selected[@]}"; do
            run_gate "${name}"
        done
    else
        local resource
        local -A before=()
        for resource in "${SHARED_READ_ONLY[@]}"; do
            before[${resource}]="$(fingerprint "${resource}")"
        done
        printf '\n=== %d control(s), up to %d at once; graded below in the serial order ===\n' \
            "${#selected[@]}" "${JOBS}"
        printf '  shared and only read, fingerprinted now and again at the end: %s\n\n' \
            "${SHARED_READ_ONLY[*]}"
        run_concurrently "${selected[@]}"
        banner "SHARED RESOURCES  --  every concurrent control must only have read them"
        for resource in "${SHARED_READ_ONLY[@]}"; do
            if [ "$(fingerprint "${resource}")" = "${before[${resource}]}" ]; then
                printf '  ok       %s unchanged\n' "${resource}"
            else
                FAILED=$((FAILED + 1))
                FAILURES+=("shared resources: ${resource} CHANGED during a concurrent run. A control wrote something every other control reads, so no result of this run can be trusted; find the writer, and run with --jobs 1 until it is fixed")
                printf '  CHANGED  %s -- a control wrote to it while others read it\n' "${resource}"
            fi
        done
    fi
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
    for phase in 01 02 04 05 06 07 08 09 10 11 12 13 14 15; do
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
