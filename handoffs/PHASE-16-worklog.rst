=====================================================================
PHASE-16 work log -- Documentation and Release Qualification
=====================================================================

:Phase: 16
:Phase orchestrator: tier-2 phase orchestrator, dispatched by tier 1 at
   ``d303e21`` (brief ``handoffs/PHASE-16-BRIEF.rst``)
:Started: 2026-11-12
:Paused: 2026-11-12, by the owner, after unit 2 (*"I want to pause phase 16
   after it finishes its current unit's work. I will continue it later."*,
   relayed by tier 1). Units 1 and 2 are accepted; units 3 to 16 have not
   started. ``handoffs/PHASE-16-handoff.rst`` is the pause handoff.
:Resumed: 2026-11-12, by the owner (*"Resume phase 16"*), with a second
   phase orchestrator dispatched by tier 1 at ``b76704a``. From unit 3 on
   this log is the second orchestrator's; see *Resume* below.

Maintained by the phase orchestrator as the phase runs. One row per work unit.
A unit is not done until it carries a sign-off entry naming what was run and
what was observed -- "agent reported success" is not a sign-off.

.. contents:: Contents
   :depth: 1
   :local:

Starting state
==============

``git status`` clean at ``d303e21`` on ``main``, equal to ``origin/main``; no
other phase live; no Maven, Java or gate process running (``ps``). Host: 64
cores, load average 0.2. ``scratch/fixture`` holds both K562 mzML files and
the whole UniProt human proteome (13,731,881 bytes); ``scratch/phase05/artefacts``
holds every pinned artefact of the manifest, the Windows Comet executables and
their Thermo companions among them; ``scratch/raw-scout`` holds the RAW search
agent's working files. **No baseline build was taken**, by the owner's
build-economy rule: tier 1 ran both at ``9c35e46``.

Facts established before design
-------------------------------

* **No workflow has a** ``push`` **trigger.** ``pull-request.yml`` runs on
  ``pull_request``, ``merge_group`` and ``workflow_dispatch``; ``nightly.yml``
  on its schedule and ``workflow_dispatch``; ``release.yml`` on
  ``workflow_dispatch`` alone. The brief allows this phase a branch push and
  API reads, not a pull request and not a dispatch, so a pushed branch runs
  nothing as the workflows stand (decision P16-1).
* **Pushing and reading work.** ``git push --dry-run origin
  HEAD:refs/heads/phase16/probe`` was accepted (nothing was pushed); the
  Actions API answers with the credential git uses, job logs included (read:
  the nightly of 2026-11-11 at ``5efdc3b``, eleven jobs, seven red on their
  ``D-006`` and ``D-007`` stubs, four green).
* **The stubs that name** ``D-006``: ``nightly-large-dataset.sh``,
  ``nightly-determinism-fixture.sh``, ``nightly-performance-fixture.sh``,
  ``nightly-gui-tests.sh``, ``nightly-windows-raw.sh``,
  ``release-e2e-canonical.sh``, ``release-e2e-limelight.sh``,
  ``release-e2e-noxml.sh``; ``nightly-limelight-sandbox.sh`` names it for its
  input. **Stubs that wait on this phase's packaging**: ``release-package.sh``,
  ``release-checksums.sh``, ``release-smoke.sh``, ``release-sign-notarise.sh``,
  ``release-publish.sh``.
* **The user documentation is mostly headings.** ``faq``, ``workflow``,
  ``getting_started``, ``installation``, ``troubleshooting``,
  ``release_notes``, ``reference/command_examples`` and
  ``developer/releasing`` are between 19 and 27 lines each.
* **Nothing has ever built or run on a macOS or Windows runner** beyond the
  two single-purpose jobs of Phase 05 (Percolator 3.07.1 on Windows, the
  Gatekeeper check). ``scripts/ci/toolchain.sh`` and the ``jpackage`` argument
  files for both platforms have never executed there.

Resume -- the second orchestrator (2026-11-12)
==============================================

``git status`` clean at ``b76704a`` on ``main``, equal to ``origin/main``; no
Maven, Java or gate process in this container. **The host is shared and
busy:** load average 44 on 64 cores at the start, none of it from this tree,
so a timing-graded red is reproduced before it is believed or dismissed.
``scratch/fixture`` holds both K562 mzML files and the whole proteome.
``scratch/p16/orch/build-economy.txt`` was made again from the brief (the
old copy differed by one trailing blank line).

**What I read.** In full: the brief with its resume section,
``ONBOARDING.rst``, ``CONTRIBUTING.rst``, ``phases/PHASE-16-release.rst``,
the pause handoff, this log, ``handoffs/PHASE-15-handoff.rst``,
``DECISIONS.rst`` ``D-016`` to ``D-020``, ``STATUS.rst``'s header and
``status-p15-signed``. The longer documents (``docs/developer/testing.rst``,
``docs/developer/test_fixtures.rst``, the specification's rules, ``D-006``,
the Phase 14 handoff) are read section by section as each unit needs them,
and each unit agent is sent to the sections it needs.

**What binds the order now** (the brief's resume section): ``D-020`` is OPEN.
Units 5 (every part), 7 and the fixture-dependent part of 10 do not start
until tier 1 relays the owner's answer. The order is 3, 4, 6, then 8 and 9,
11, and 12 to 16 as far as each goes without the fixture. Unit 5 is split
before any of it is dispatched; the split is recorded here first.

**How a unit agent is briefed.** Each gets
``scratch/p16/orch/unit-standing.txt`` -- tier 1's *Build economy* section
verbatim between two rules, and the standing rules -- and its own brief,
``scratch/p16/orch/briefs/u<N>.txt``. Both are kept on disk for tier 1.

Current state -- a checkpoint kept true (last written 2026-11-13 23:55)
=======================================================================

Tier 1 relayed two things on 2026-11-13: **the owner pauses the phase when
unit 5a is finished** (*"When 5a finishes, I want a pause."*), and the
server may be restarted before then, ending every agent without warning.
This section is what a fresh session needs; it is rewritten whenever it
stops being true.

**Accepted:** units 1, 2, 3, 3b, 4, 4c. Each has its sign-off below.

**In progress: unit 5a** (the seams and the first-1,000-records tests onto
the committed FASTA), one agent, dispatched at ``aa83b65``, brief
``scratch/p16/orch/briefs/u5a.txt``. **Not accepted.** At 23:47 its agent
had made four commits and had not reported:

* ``e88e440`` -- ``cometgui-tools``' real-binary tests search a cut of the
  committed FASTA; index headers and counts taken again;
* ``6a2f7fc`` -- the validation corpus searches a cut of it; the 42
  verdicts run again on both Comet releases;
* ``1984599`` -- the ``tests`` gate's sandbox carries ``fixtures/``;
* ``581a659`` -- the record in ``docs/developer/test_fixtures.rst``.

It was then running its own gate run (``--only
params,tests,workflow,percolator,provenanceui,pdv,limelight,results,shell,paramui,e2e,hardening,docs,...``;
log ``_build/p16u5a/gates.log``; its reactor log is
``_build/p16u5a/reactor.log`` with ``reactor.exit``). ``tests`` alone
takes about 90 minutes. **None of my own sign-off checks for 5a has run.**
Whether the workflow and app seams (``RealComet``, ``RealSearch``) are
moved is not yet known to me: read the agent's commits against the 5a row
of *The split of unit 5* before believing the unit complete.

**If you are a fresh session:** check ``git status`` and
``_build/p16-orch/INJECTION-IN-PROGRESS.txt`` and
``_build/p16u*/INJECTION-IN-PROGRESS.txt`` first (an injection left in a
tracked file is restored from ``git`` before anything else). Then establish
what 5a's agent left: its commits, anything uncommitted, its logs. Finish
5a with one fresh agent if it is short, sign it off (reactor ``verify``;
your own injections; every ``--only`` control whose harness reads what it
changed, ``e2e`` and the section harnesses among them), and **stop: the
owner's pause holds after 5a.** Write the pause handoff. Start nothing
else until tier 1 relays a resume.

**Not started:** 5b, 5c, 5d, 5e, 6 to 16.

**Pushed by this phase:** one branch, ``phase16/raw-trial-1``, by the first
orchestrator. I have pushed nothing. No runner run is in flight.

How the remaining units are briefed (what the briefs hold beyond the rows)
--------------------------------------------------------------------------

Every unit agent is given ``scratch/p16/orch/unit-standing.txt`` and one
brief. Those files are on disk, not in git; if they are gone, this is their
substance.

*The standing text:* tier 1's *Build economy* section verbatim; one agent,
one unit, no sub-agents; scratch under ``scratch/p16/u<N>/``, logs under
``_build/p16u<N>/``, nothing written under ``scratch/`` while a gate run is
live; long commands detached with an exit file and never a turn ended while
one runs; never ``-rf`` on a reactor; the end-to-end classes are opt-in, so
a change to the interface, the engine or what a run writes is checked with
``--only e2e`` and every section harness whatever the scripts name; a fix
ships with a test seen red on the old production code, restored with
``sha256sum -c``; no golden, pinned digest or count moves unless the brief
names it; nothing weakened, skipped, retried or quarantined; no push;
explicit pathspecs and the two trailer lines; a clean tree at the report.
**To add at the next dispatch (tier 1, 2026-11-13):** before altering a
tracked file for an injection an agent writes
``_build/p16u<N>/INJECTION-IN-PROGRESS.txt`` (file, SHA-256 of the original,
one line on the change) and deletes it once the file is restored and
verified; and it commits at every milestone so a restart costs minutes.

*Lessons that go into every brief from here:* harness controls anchor on
exact production lines, so a refactor that strands one is repaired in the
agent's own code where possible (unit 4c) and never by lowering a floor; a
timing-graded red is reproduced before it is believed or dismissed; the
host's load is stated with every timing.

*5b (the cancel tests):* drafted, ``scratch/p16/orch/briefs/u5b.txt``. Beyond
the split's row: the window is measured from the moment the test's trigger
is observable to Comet's own exit when not cancelled, five runs or more per
candidate, unconfined and under ``taskset -c 0-1``; each test must fail,
not pass, if Comet had already finished when the cancel arrived; each moved
test green ten times running and five times on two cores; ``proteome()``
itself goes when its last user does; the ``hardening`` control that injects
"no polite stop" must still go red.

*5c (Phase 00's outputs):* drafted, ``_build/p16-orch/briefs/u5c.txt``.
Beyond the row: the old outputs are renamed once to
``scratch/scientific-path.whole-proteome/`` and kept until the unit is
accepted; the script changes only as far as its input requires and each
change is listed; one table of every count with the product reader's figure
and an independent count; the tests that do not run without the directory
are found by running the modules with it moved aside; ``--only tests`` is
not run again here unless a value it quotes moved.

*5d (the fixture, goldens, baseline):* not drafted. Beyond the row:
``docs/developer/test_fixtures.rst`` still says ``D-020`` is put to tier 1
and is corrected here (the owner accepted the warning, option A); the
``gui-heap-peak`` threshold's rationale still cites the creep unit 3 fixed
and is retaken with the baseline; the review record of each new golden
explains every difference from the old one.

*5e (the words and the proof):* not drafted. Beyond the row:
``testing.rst`` states the ``D-020`` warning plainly; the dated 2026-11-06
digests in its prose are of the earlier compared form (unit 3b's note).

*6 (D-016 B, D-017 A):* drafted, ``scratch/p16/orch/briefs/u6.txt``. Beyond
the row: the class list (every command builder and every stage-invalidation
class under ``org.cometgui.workflow``, with near misses and why) is checked
against ``R-TEST-02`` and not narrowed to make the cost come out; the PIT
wall clock is measured before any POM is committed, on this host and as a
2- or 4-core runner would see it, and above about fifteen minutes nothing
of ``D-016`` is committed; every undetected mutant in the new packages is
audited by name into ``testing.rst``; ``/tmp/cometgui-*-probe-*`` is counted
before and after the PIT run; a step that throws an ``Error`` was seen once
to leave the engine waiting until ``await`` timed out (unit 3b) and is
looked at here; the one ``--only tests`` run, detached.

*7 to 16:* as their rows state, with these additions found so far -- unit 9
examines the two bare ``ATOMIC_MOVE`` writes (``ToolCache.writeMarker``,
``LimelightConverter``) and unit 4's replace-on-write on the Windows and
macOS runners; unit 12 corrects ``PreRunChecks``' "Comet runs in the run
directory", states the ``D-020`` warning for users, and what bounds a
run's paths now that ``-N`` is bare; unit 13 rewords ``CONTRIBUTING.rst``
(the single-page check, *Before you report a unit done*); unit 15's
keyboard checklist names the focus that rests on a row's state label with
no ring after an install (unit 4c) and audits controls disabled while
focused; unit 16 fixes ``dev-verify.sh`` (a changed gate script mapped to
``verify-shell-gates.sh``; its closing text under ``--verify``) with a test
of the mapping, and ``testing.rst``'s "Limelight enabled".

*For tier 1, so far:* ``STATUS.rst``'s Phase 15 residue line on
``KeyboardOnlyNavigationUiTest`` was a prediction, not what happens; the
Limelight XML's ``conversion_program arguments`` carries absolute paths; the
specification has no ``R-INS`` rule though documents cite one; the
determinism grader's tie key was repaired (P16-10, ``0b18c18``).

Design decisions (orchestrator's, recorded before dispatch)
===========================================================

P16-1 -- how a pushed branch runs a workflow
   A branch ``phase16/<purpose>-<n>`` is made by the orchestrator in a
   ``git worktree`` under ``scratch/p16-push/``, never in the working tree
   the units build in. Its tip is a commit of ``main``'s line plus **one**
   commit, never merged, that adds a ``push`` trigger naming that branch to
   the workflow under test (and, for a trial, the trial's own workflow file).
   The trigger commit's whole diff is recorded here beside each run. ``main``'s
   triggers do not change, so nothing this phase pushes makes ``main`` start
   running workflows on every push. Each branch stays on ``origin``; none is
   deleted or force-pushed. Evidence from such a run is evidence about the
   commit under the trigger commit, and is recorded with both hashes.
P16-2 -- the RAW trial is bare tools, and comes first
   Unit 1 runs Comet for Windows and Percolator directly on a Windows runner,
   with no CometGUI code, because the question is whether Comet opens the
   file at all. Its script is committed under ``scripts/spikes/``; the
   workflow that calls it exists only on the pushed branch. The product-level
   smoke test (unit 11) is built only if the trial reads the file.
P16-3 -- the order
   The RAW trial first (the brief). Then the trimmed FASTA, which touches no
   product code. Then Phase 15's residue, **before** the goldens are
   regenerated, so they are regenerated once over the product as it will
   ship. Then the single fixture and its goldens; then ``D-016`` and
   ``D-017`` with the one ``--only tests`` run, which then also covers the
   residue's changes to gated code. Then CI wiring, packaging and the
   runners, which test the product as it then stands. Documentation, the
   licence audit, the UX preparation and traceability come last because they
   describe what the earlier units produced.
P16-4 -- the fixture's floors
   Proposed by unit 2 from its own measurements, recorded here at its
   sign-off, confirmed by tier 1 (the brief). A floor is a count the check
   must reach; it is never set from a run that was already failing.
P16-5 -- steps with no infrastructure
   Signing, notarisation and publishing have none, and the brief forbids
   creating a release. No such step may exit 0 having done nothing. What
   each does instead is decided at unit 9 and recorded there for tier 1.
P16-6 -- serial, and one scratch directory per agent
   Units run one at a time. Each agent works under ``scratch/p16/u<N>/`` and
   the orchestrator under ``scratch/p16/orch/``. A remote runner job in
   flight is not an agent in the tree; the orchestrator may wait on one while
   the next unit runs, and records it.

P16-7 -- where committed fixture data lives
   Under ``fixtures/<name>/`` at the repository root: the FASTA, a plain-text
   attribution file beside it naming UniProt, CC BY 4.0, the proteome
   identifier and the release, and nothing else. Spectrum files are never
   there. Each is also entered in ``docs/citations.rst`` and the licence
   audit. A fetch script assembles a working fixture directory (fetched
   spectra plus a copy of the committed FASTA) outside ``fixtures/``.
   *Amended before unit 2 was dispatched:* a trimmed FASTA also has the list
   of accessions it was made from beside it, because the phase document
   requires that list to be committed and it is what pins the file's digest.

``D-020`` decided, and the order (2026-11-12)
---------------------------------------------

Tier 1's message, received while unit 3 ran: the owner decided ``D-020``,
option A -- *"re: D-020: yes, it is acceptable."* I checked the record
before acting on it: tier 1's commit ``f369f6e`` changes only
``DECISIONS.rst``, ``STATUS.rst``, the phase document and
``phases/index.rst``, and ``D-020`` reads DECIDED. No ``D-`` item is open.

* The order is P16-3's again: 3, 4, 3b, 5a to 5e, 6, 7, then 8 to 16. The
  detour in the brief's resume section applied only while ``D-020`` was
  open.
* The committed FASTA and its floors stand. The acceptance check prints
  Percolator's warning wherever Percolator gives it and does not grade it.
* **A documentation duty comes with the decision:** the pages that describe
  the fixture say plainly that its one-result-per-spectrum runs carry the
  warning, why, and that the owner accepted it under ``D-020``.
  ``docs/developer/test_fixtures.rst`` (which still says the question is put
  to tier 1) is corrected in **unit 5d**, the unit that next touches it;
  ``docs/developer/testing.rst`` in **unit 5e**; the user-facing statement
  in **unit 12**.

P16-9 -- PSM identifiers are their own unit, and the determinism tolerance gets stricter
   Unit 3's item B was stopped at its hard stop and is dispatched as **unit
   3b**, after unit 4 and before 5a, so that the goldens are regenerated
   once over the product as it will ship (P16-3's reason). The fix removes
   the run directory from every identifier, so the seven tolerated fields
   "the run's own directory" in ``scripts/matrix/determinism-data.json``
   stop being true. They are **removed**, with the same rows of the table in
   ``testing.rst``: the tolerance gets stricter, which is the allowed
   direction. Nothing is added to it. No golden moves (identifier digests
   are taken without the directory); if one would, the unit stops.

The split of unit 5 (recorded 2026-11-12, before any part is dispatched)
------------------------------------------------------------------------

Decision **P16-8**. Unit 5 is five units, 5a to 5e, each with its own fresh
agent and its own sign-off, serial. (Drafted while ``D-020`` was open; the
owner decided it before any part was dispatched.) The split follows the inventory in
``docs/developer/test_fixtures.rst`` (*What the change of FASTA touches*) and
is bounded by the four rulings in the brief's resume section. Each part
measures the test time it adds or removes, module by module, against the
same tests at its starting commit; the build must not grow.

.. list-table::
   :header-rows: 1
   :widths: 6 50 44

   * - Part
     - What it moves
     - Acceptance, beyond the standing rules
   * - 5a
     - **The seams, and the tests that search the proteome's first 1,000
       records.** ``RealSearch``, ``RealCometSearch``, ``RealComet``,
       ``CometIndexRealBinaryTest``, ``CometAdapterRealBinaryTest``,
       ``LimelightFixtures``' FASTA, the validation corpus
       (``corpus.json``, ``ValidationCorpusRealBinaryTest``,
       ``MigratedFileRealBinaryTest``), the committed index headers under
       ``fixtures/comet-index`` with ``IndexHeaders`` and
       ``IndexDescriptions``, and every test behind ``subset()``. The gate
       harnesses that name the three fixture files for these tests
       (``param``, ``percolator``, ``provenanceui``, ``workflow``).
     - The input is the committed FASTA, read from ``fixtures/``; whether a
       test searches all 3,698 records or a cut is decided by measured test
       time and recorded. Index headers and every pinned count are taken
       again **from the real binaries** (both Comet releases), never typed
       to match; the 42 validation verdicts are run again. Nothing behind
       ``subset()`` reads ``UP000005640_9606.fasta`` afterwards.
   * - 5b
     - **The tests that cancel a search.** ``RealCancellationTest``,
       ``RealCancelUiTest``, ``ProvenanceSectionUiTest``,
       ``RealProvenanceGateTest``'s cancelled run, and the ``cancel-comet``
       performance measurement.
     - A search that lasts long enough from the same spectra and the
       committed FASTA, **by search settings**; never a sleep, a stand-in
       binary or the whole proteome. The window is measured at 2 cores
       (``taskset -c 0-1``) and at this host's count, several times, and
       recorded; **if no settings hold a window on two cores, the part stops
       and the timings go to tier 1.** Each test is shown red with
       cancellation broken in production code. The ``cancel-comet``
       threshold is taken again by the recorded procedure
       (``testing.rst``), not edited.
   * - 5c
     - **Phase 00's search outputs.** ``scripts/feasibility/run_scientific_path.sh``
       run once against the committed FASTA; the digests and counts in
       ``K562Run``, both ``LimelightFixtures``, ``PdvMzTabsFidelityTest``,
       ``RealK562``/``RealK562Test``, ``ResultsFixtures``, ``K562Outputs``,
       ``ProjectLimelightUploadPortTest``, ``PROVENANCE.txt`` and
       ``WEIGHTS-SUMMARY.txt`` taken from the new files; the gate harnesses
       ``pdv``, ``limelight``, ``results`` and ``tests`` where they quote
       them.
     - Recorded as a change of input. Every pinned count is checked against
       the product's own readers **and** an independent count, as the
       present ones were. **The tests that do not run on a runner because
       these outputs are not there are listed by count and by name**, in
       ``test_fixtures.rst``; nothing hides that. The old outputs are kept
       aside until the part is accepted, then removed, so nothing can still
       be reading them.
   * - 5d
     - **One fixture, its fetch, and what is graded against it.** One fetch
       script (spectra by checksum from the pinned commit, CRLF defence,
       the committed FASTA copied beside them); the Tier A and Tier B
       applications and ``verify-e2e-gates.sh``; the four pair goldens
       regenerated once, each with its review record; ``matrix-data.json``'s
       advisories checked; the performance baseline and the
       ``percolator-fixture`` threshold by the recorded procedure; the
       matrix and determinism suites' refusal messages.
     - ``fixture-fasta.sh describe`` and the acceptance check (with its
       self-test) green on the directory the fetch script makes. Matrix,
       determinism and the end-to-end tiers green on it. A golden is
       regenerated because its input changed and for no other reason; each
       difference from the old golden is explained in its review record.
       Percolator's warning is printed wherever Percolator gives it,
       whatever ``D-020``'s answer.
   * - 5e
     - **The words, and the proof that nothing is left.** ``testing.rst``,
       the seven developer pages and ``docs/decoys.rst`` that quote the old
       fixture; ``traceability-map.toml``'s commands; and a committed check
       that no test, script, gate or job reads the whole proteome, a file
       cut from it or an output of a search against it.
     - The check is red on a planted reference and green on the tree, and
       its one allowed exception is ``fixture-fasta.sh verify --source``.
       ``scratch/fixture/UP000005640_9606.fasta`` is moved aside and the
       affected modules' ``verify`` and the affected ``--only`` gates still
       pass.

**Order:** 5a, 5b, 5c, 5d, 5e. 5a first because every later part reads
through its seams; 5d after 5c so that the goldens are regenerated once, over
tests that already stand on the committed FASTA; 5e last because it proves
the other four.

Work units
==========

.. list-table::
   :header-rows: 1
   :widths: 5 45 20 30

   * - #
     - Unit and acceptance conditions
     - Rules and gate items
     - Sign-off
   * - 1
     - **RAW trial on a Windows runner.** Comet for Windows with its Thermo
       companions reads ``ph050213_18.raw`` directly (fetched by checksum from
       PRIDE), against the WH8102 proteome with ``D-019``'s seven values;
       Percolator 3.07.1 trains; PSMs and peptides at q < 0.01 printed and
       compared with 906 / 787. Fallback ``ph280213_36.raw`` only if the
       first cannot be read. No ThermoRawFileParser. A failed read is red.
     - Gate 9 step 1; ``AC-TST-11``; ``D-019``
     - **Accepted 2026-11-12.** Both Comet releases read the file on a
       Windows runner; see *Unit 1* below.
   * - 2
     - **The trimmed FASTA.** The list of what the change touches; the source
       FASTA by checksum with its UniProt release; a committed selection
       script and accession list; identified proteins plus a background
       chosen by a recorded deterministic rule and sized by measurement; the
       FASTA committed with UniProt's attribution; an acceptance check
       through the product's workflow for every offered Comet/Percolator
       pair, with proposed floors, **red on an identified-proteins-only
       FASTA**.
     - Gate 8; *The CI fixture* steps 1-6; ``D-006``, ``D-018``
     - **Accepted 2026-11-12** after one fix round; see *Unit 2* below. The
       floors are a proposal for tier 1.
   * - 3
     - **Residue A, results and workflow.** The merged-PIN row count checked
       by the product; PSM identifiers without the absolute run path; the
       Results run chooser not opening every run it passes; the retained
       heap per opened run traced and fixed or recorded with its cause;
       ``KeyboardOnlyNavigationUiTest`` isolated from an installed Comet.
       Each with a test that proves the value and is red on the old code.
     - Brief obligation 12; ``status-p15-signed``
     - **Accepted 2026-11-12** at ``8446134``: four of five items; the PSM
       identifiers are carried to unit 3b. See *Unit 3* below.
   * - 3b
     - **PSM identifiers without the absolute run path** (unit 3's item B,
       P16-9). No path in a PIN's SpecId, a Percolator PSMId or an export,
       for runs made from now on; runs made before still open and work;
       every consumer proved on real binaries; the determinism tolerance's
       seven "run's own directory" fields removed; the matrix green against
       the unchanged goldens.
     - Brief obligation 12; ``R-CMT-03``, ``R-CMT-04``, ``R-PROC-04``
     - **Accepted 2026-11-13** at ``0b18c18``. See *Unit 3b* below.
   * - 4c
     - **An Install that is pressed and never starts.** One ``e2e`` run in
       five was red in ``NetworkUnavailableUiTest.theNetworkBack`` (found at
       unit 3b's sign-off). Reproduced on purpose; the mechanism named;
       fixed at its cause -- the product if a user's press can be lost, the
       test's wait if not; never a retry, a longer bound or a quarantine;
       the class green 30 times running and 15 times on two cores.
     - Gate 7; the flakiness policy; ``R-TOOL-04``
     - **Accepted 2026-11-13** at ``60970a1``. See *Unit 4c* below.
   * - 4
     - **Residue B, install and tools.** The probe's message for a non-zero
       exit carries the status; a failed install's reason survives a restart;
       ``CometCapabilityProbe`` leaves no workspace behind;
       ``SecretToolCredentialStore`` cancels ``secret-tool`` when interrupted;
       ``ZipArchiveReader`` reports an extra-field length past the directory
       as its checked failure. Each with a test red on the old code. **Added
       at the resume:** the manifest's Windows row for Comet 2026.02.2
       (``requiredHostLibraries`` from the binary's import table, with a
       test). **Carried from unit 3:** the two GUI tests that still launch
       through ``main``; the Provenance section's run selector; the
       chooser's untested abandon cases.
     - Brief obligation 12; ``status-p15-signed``
     - **Accepted 2026-11-13** at ``a502515``, after a second round. See
       *Unit 4* below.
   * - 5
     - **One fixture.** Local testing and CI read the K562 pair with the
       committed trimmed FASTA; one fetch script (checksum, CRLF defence);
       every golden regenerated once with its review record; the performance
       baseline and every test that pinned a count from the old fixture
       moved; the matrix, determinism and end-to-end tiers green on it.
     - Gate 8; step 7; ``R-TEST-07``
     - **Split into 5a to 5e** (P16-8, *The split of unit 5*). 5a
       dispatched 2026-11-13 at the commit that records unit 4c
   * - 6
     - ``D-016`` **option B and** ``D-017`` **option A.** The added PIT wall
       clock measured before adoption (stop above about fifteen minutes);
       command-builder and stage-invalidation classes targeted and graded per
       package at 80%; workflow and results held to 90/85, the raised gate
       shown red; ``--only tests`` once.
     - ``R-TEST-02``; ``AC-TST-02``, ``AC-TST-03``
     - not started (paused after unit 2)
   * - 7
     - **The fixture in CI.** The nightly jobs that named ``D-006`` fetch the
       spectra by checksum on the runner and run against the reviewed
       goldens; serial at four cores or fewer; no job fetches the whole
       proteome; stale ``D-006`` wording corrected. Run on a hosted runner
       from a pushed branch.
     - Gate 8; step 8; ``AC-TST-10``
     - not started (paused after unit 2)
   * - 8
     - **The launcher and the release scripts.** The packaged launcher starts
       the JVM in a UTF-8 locale on every platform's launcher and with
       ``--enable-native-access``; proved on Linux with a non-ASCII path
       through the installed launcher; ``release-package.sh``,
       ``release-checksums.sh`` and ``release-smoke.sh`` real; the SBOM
       beside the artefacts.
     - Gate 3, 4; ``AC-REL-01``; the phase document's warning
     - not started (paused after unit 2)
   * - 9
     - **The release workflow on three runners.** Toolchain, packaging,
       checksums, SBOM and the smoke test on Linux, Windows and macOS
       runners; signing and publishing as P16-5 decides. Pushed and read by
       the orchestrator.
     - Gate 3, 4, 7; ``AC-INS-01``
     - not started (paused after unit 2)
   * - 10
     - **Packaged end-to-end on each runner.** The three release end-to-end
       steps real, on the exact artefact, with the fixture fetched; Linux
       first, then Windows and macOS as far as each goes, the remainder named.
     - Gate 3; ``AC-TST-07``, ``AC-TST-08``
     - not started (paused after unit 2)
   * - 11
     - **The Windows RAW smoke test through the product**, with a reviewed
       golden taken from a runner and the organism's FASTA committed; or the
       job taken out of the nightly and ``AC-TST-11`` recorded not met.
     - Gate 9; ``AC-TST-11``
     - not started (paused after unit 2)
   * - 12
     - **The user documentation tree**, complete, with platform support and
       troubleshooting stating per platform which Percolator versions are
       managed, which write XML, and where Limelight works out of the box.
     - ``R-DOC-06``; gate 1
     - not started (paused after unit 2)
   * - 13
     - **The developer tree and the release documents.** ``releasing.rst``
       and the release checklist; the generated parameter reference built in
       CI; the Read the Docs configuration built as it says; the link check.
     - ``R-DOC-04``, ``R-DOC-05``; ``AC-DOC-01``; gate 1
     - not started (paused after unit 2)
   * - 14
     - **The licence audit**, every bundled and transitive component, managed
       tool and data file with source, licence and attribution; the source
       offer GPL-3.0 requires; the sign-off line empty.
     - ``AC-REL-03``; gate 4, 5; ``R-SEC-01``
     - not started (paused after unit 2)
   * - 15
     - **The six UX activities prepared**: protocol, task script, checklist,
       recording template and triage route for each; an agent's own heuristic
       and keyboard review, labelled as that, with its defects filed.
     - ``AC-UX-01``..``06``; gate 6
     - not started (paused after unit 2)
   * - 16
     - **Traceability and the phase's gate harness.** The six "planned,
       phase 15" entries and Phase 14's two re-homed or recorded unmet; a
       lean control for this phase's gate registered additively;
       ``testing.rst`` brought up to date.
     - ``R-DOC-01``..``03``; ``AC-DOC-02``; gate 2, 7
     - not started (paused after unit 2)

Sign-offs
=========

Written as each unit lands.

Unit 1 -- the RAW trial (accepted 2026-11-12)
---------------------------------------------

Agent commits ``5980502`` (the WH8102 proteome under
``fixtures/synechococcus-wh8102/`` with ``ATTRIBUTION.txt``, a ``fixtures/**
-text`` rule in ``.gitattributes``, a UniProt entry in ``docs/citations.rst``)
and ``6dae9f5`` (``scripts/spikes/windows-raw-trial/``: ``trial.py``,
``run.sh``, ``workflow.yml`` as a template outside ``.github/workflows/``).

**What I ran and saw.**

* The diff, both commits; ``trial.py``'s verdict function read in full: READ
  is decided from a PIN of at least 1,000 rows with targets and decoys in a
  directory created empty, never from an exit code.
* The FASTA: ``sha256sum`` ``b4c8aaf4...1750d``, 1,080,427 bytes, 2,513
  records, no CR byte, ``cmp`` equal to the scout's copy, ``git check-attr``
  ``text: unset``.
* ``run.sh --check-only`` in my own directory: exit 0, 11 downloads, every
  size and SHA-256 equal to its pin (both Comet releases with their three
  Thermo companions, the Percolator 3.07.1 archive, both RAW files).
* ``scripts/ci/check-workflows.sh``: PASSED, five workflows, unchanged.
  ``scripts/ci/docs-build.sh``: PASSED (58 and 95 pages).
* **Injection A**, into ``manifests/tools.json``: the pinned SHA-256 of Comet
  2026.03.0's ``comet.win64.exe`` changed in its first hex digit (anchor gone,
  ``sha256sum -c`` FAILED). ``--check-only``: exit 3, ``PIN MISMATCH for Comet
  2026.03.0: comet.win64.exe -- HARD STOP``. **Injection B**, into
  ``trial.py``: the RAW file's pin changed in its last digit. Exit 3, ``PIN
  MISMATCH for RAW file (primary, D-019): ph050213_18.raw``. Both restored,
  ``sha256sum -c`` OK, tree clean.

**The trial itself**, run ``43150059739`` (see *Runner runs relied on*):
green in 59 s on ``Windows-2025Server-10.0.26100``.

.. list-table::
   :header-rows: 1
   :widths: 16 12 16 14 14 28

   * - Comet
     - Read
     - PIN rows (target / decoy)
     - PSMs, q < 0.01
     - Peptides, q < 0.01
     - Against the Linux mzML search with the same release
   * - 2026.02.2
     - READ
     - 17,655 (9,674 / 7,981)
     - 873
     - 761
     - 906 / 787 (``D-019``): -33 / -26
   * - 2026.03.0
     - READ
     - 17,655 (9,674 / 7,981)
     - 894
     - 767
     - 920 / 797 (the agent's measurement): -26 / -30

Comet exited 0 and printed nothing about an empty scan or a charge. It loaded
3,531 spectra against 3,624 from the converted mzML: reading the RAW file
itself it searched 239 scans as 1+ and 1,646 as 2+ and 3+ (162 and 1,731 from
the mzML), so the two paths assign charge differently and the counts differ by
about 3%. The fallback file was not needed and was not searched. Five
assemblies ``ThermoFisher.CommonCore.RawFileReader.dll`` references are
neither beside Comet nor in the GAC (``OpenMcdf``, ``OpenMcdf.Extensions``,
``System.IO.FileSystem.AccessControl``, ``System.Security.AccessControl``,
``System.Security.Principal.Windows``); the read succeeded without them.

**Consequence.** Gate item 9's first step is done: Comet on Windows opens
``ph050213_18.raw``. Unit 11 builds the product-level test, and its golden is
taken from a runner, not from ``D-019``'s figures.

**Reported by the agent, for tier 1** (documents neither of us may edit):
``D-019``'s 906 / 787 is Comet 2026.02.2's figure and 2026.03.0 gives 920 /
797 from the same mzML; the search report says every scan was searched as 2+
and 3+ where 162 were searched as 1+; the manifest's Windows row for Comet
2026.02.2 has an empty ``requiredHostLibraries`` although its binary imports
the same four Visual C++ runtime libraries as 2026.03.0's; ``CONTRIBUTING.rst``
still asks a unit agent for a green ``scripts/build.sh``; ``ONBOARDING.rst``
still says specification revision 10 and ``D-001``..``D-008``.

Unit 2 -- the trimmed FASTA (accepted 2026-11-12, after one fix round)
----------------------------------------------------------------------

Agent commits ``d2b57e2`` (the FASTA, ``accessions.tsv``, ``ATTRIBUTION.txt``
under ``fixtures/human-k562-subset/``; ``scripts/fixtures/fixture_fasta.py``
and ``fixture-fasta.sh``), ``d8d985a`` (the acceptance check:
``scripts/fixtures/check-fixture.sh``, ``fixture_acceptance.py``,
``fixture-acceptance.json``; the opt-in ``FixtureAcceptancePairs`` and a
``runOnce`` overload in ``VersionMatrixPairs``, both test code),
``41411c4`` and ``18b7a26`` (``docs/developer/test_fixtures.rst``, the UniProt
entry in ``docs/citations.rst``), and ``13f7b07`` (the fix round). No product
code changed. Nothing reads the new FASTA yet: switching over is unit 5.

**The file.** ``fixtures/human-k562-subset/uniprot-human-2026_02-k562-subset.fasta``:
3,698 records (698 identified, 3,000 background), 2,539,164 bytes, SHA-256
``bd252b62568d88881e0d13bcdfaa6b9db96666af0ba08fc51863fcd7bdec1b34``. Source:
``UP000005640_9606.fasta``, 20,652 records, 13,731,881 bytes, SHA-256
``2329a517bec9bd7269f9ce3b9252d8b959ae98bc41405a945c2d6134b284d5a0``, UniProt
release 2026_02 (the agent matched the bytes to EMBL-EBI's QfO copy of that
release; I did not repeat the 2.36 GB download). *Identified*: named by
Percolator for a target PSM or a target peptide with q <= 0.01 in any of eight
runs against the whole proteome (four pairs, five results and one result per
spectrum). *Background*: the other records ranked by the SHA-256 of the
accession, the first 3,000. The fixture digest with the K562 pair is
``1d530d5bc575bb240eb25c0b1d03622f28295cc8173904467264602908194f26``.

**What I ran and saw.**

* The diff. The ``VersionMatrixPairs`` change is a ``runOnce`` overload taking
  extra Comet parameters; the old signature calls it with none.
* ``fixture-fasta.sh describe``: OK, 3,698 records, the digest above.
  ``fixture-fasta.sh verify --source scratch/fixture/UP000005640_9606.fasta``:
  OK, byte for byte. ``sha256sum`` of both files as stated; no CR byte;
  ``git check-attr`` ``text: unset``.
* ``check-fixture.sh DIR --offline --require-committed`` on my own copy of the
  K562 pair and the committed FASTA: exit 0 in 131 s, ``SUMMARY ok 60 RED 0``,
  ``VERDICT GREEN``; the counts equal the agent's (for Comet 2026.03.0 with
  Percolator 3.07.1, five results: 1,103 PSMs and 654 peptides at 1%, 2,744
  decoy PSMs, 2,784 PSMs above 1%).
* ``check-fixture.sh --self-test DIR --offline``: exit 0. The identified
  proteins alone (698 records, 563,574 bytes), searched by 8 of 8 runs, **RED
  on** ``psms-above-1pct [matrix]`` for every pair (2,605, 2,585, 2,600 and
  2,580 against a floor of 2,691) and on no other criterion. The background
  alone: red on ``psms-at-1pct`` and ``peptides-at-1pct`` (0) in both
  configurations.
* The version matrix over the present fixture and goldens
  (``COMETGUI_MATRIX_FIXTURE=scratch/fixture``): RUN-PASSED 11, RUN-FAILED 0,
  UNAVAILABLE 14, UNVERIFIED 36, GREEN, as at the Phase 15 sign-off -- the
  ``runOnce`` change altered nothing.
* **Injection A**, into ``PinMerger`` (production, ``cometgui-tools``): the
  first row of every file after the first dropped (old anchor gone,
  ``sha256sum -c`` FAILED). The acceptance check: exit 1, ``ok 52 RED 8``,
  ``VERDICT RED -- percolator-wrote-results [matrix]; [top1]``, "the merged
  PIN holds (6630, 3886, 2744) rows/targets/decoys and the per-file PINs
  (6631, 3887, 2744)". Restored, ``sha256sum -c`` OK.
* **Injection B**, into the committed FASTA: one record (``P62318``) removed.
  ``fixture-fasta.sh describe``: FAILED, "2538931 bytes, and the list records
  2539164". **But** ``check-fixture.sh --require-committed`` **on that altered
  file said "the committed fixture FASTA", GREEN, exit 0** -- see *Rejections
  and rework*. After the fix round, the same injection: REFUSED in 0 s, exit
  3, "does not match its pin: its SHA-256 is 1940fe82..., and
  fixtures/human-k562-subset/accessions.tsv pins bd252b62...". With the
  checkout restored and the altered file as the candidate: REFUSED, exit 3,
  "is not the committed fixture FASTA". Restored, ``sha256sum -c`` OK.
* ``check-fixture.sh --self-test-grading``: OK, 32 graded cases, 4 refusals, 8
  cases of the committed FASTA's identity.
* ``mvn -o -B -pl cometgui-app -am verify -DskipTests`` at ``13f7b07``: BUILD
  SUCCESS, twelve modules (Spotless, Checkstyle, SpotBugs over test code
  included). The agent's full ``-pl cometgui-app -am verify`` after its last
  Java change: BUILD SUCCESS in 46 min, the app module 576 tests, 0 failures,
  91 skipped (its log, ``_build/p16u2/app-verify2.log``; I read the result
  and did not repeat the 46 minutes, the ordinary test classes being
  unchanged).
* ``scripts/ci/flakiness-check.sh``: OK, register empty.
  ``scripts/verify-all-gates.sh --only hardening,docs,traceability,flakiness``
  at ``13f7b07``: 4 passed, 0 failed, 609 s.

**The floors, proposed (P16-4); tier 1 confirms.** In
``scripts/fixtures/fixture-acceptance.json``. Each is 95% of the smallest of
the four pairs' counts against the whole proteome, except the two small counts
of the one-result run, which are two thirds. Counts are in the order Comet
2026.02.2 with Percolator 3.06.5 and 3.07.1, then Comet 2026.03.0 with the
same two.

.. list-table::
   :header-rows: 1
   :widths: 9 19 8 22 22 20

   * - Run
     - Criterion
     - Floor
     - Whole proteome
     - Committed file
     - Identified only
   * - five results
     - PSMs at 1%
     - 957
     - 1008, 1036, 1013, 1053
     - 1099, 1101, 1092, 1103
     - 1149, 1129, 1144, 1124
   * - five results
     - peptides at 1%
     - 562
     - 599, 602, 592, 598
     - 648, 653, 646, 654
     - 668, 672, 670, 658
   * - five results
     - decoy PSMs
     - 2644
     - 2784
     - 2744
     - 2812
   * - five results
     - **PSMs above 1%**
     - **2691**
     - 2878, 2850, 2873, 2833
     - 2788, 2786, 2795, 2784
     - **2580, 2600, 2585, 2605**
   * - one result
     - PSMs at 1%
     - 998
     - 1051, 1059, 1065, 1075
     - 1087, 1090, 1092, 1087
     - 1119, 1124, 1119, 1124
   * - one result
     - peptides at 1%
     - 589
     - 621, 620, 623, 623
     - 640, 637, 644, 640
     - 653, 660, 654, 661
   * - one result
     - decoy PSMs
     - 80
     - 120
     - 97
     - 90
   * - one result
     - PSMs above 1%
     - 93
     - 164, 156, 150, 140
     - 149, 146, 144, 149
     - 118, 113, 118, 113

Only "PSMs above 1%" with five results catches over-filtering; the committed
file clears it by 93 PSMs and the identified proteins alone miss it by 86 to
111. The check passes from a background of 1,000 records (1.2 MB); 3,000 was
chosen for margin. All of it is measured on Linux x86-64 only.

Unit 3 -- Residue A: results and workflow (accepted 2026-11-12; item B carried to unit 3b)
------------------------------------------------------------------------------------------

One fresh agent, one round. Agent commits ``02fb2b9`` (A), ``5973c9e`` and
``8446134`` (C), ``bac5531`` (E), ``585c86c``, ``01624db`` and ``f5bd984``
(D), ``326e6e5`` (the workflow harness's control 3b). Tier 1's ``f369f6e``
(``D-020`` decided) landed between them and touches only tier 1's files.

**What was built** (the diff, read in full for production code and the
harness; 16 files, +1741 -45).

* **A, the merged PIN's rows** (``R-CMT-06``). ``PinMerger`` reads back the
  file it wrote through ``PinReader``, counts its data rows and compares
  them with the rows read; a difference is the new checked
  ``PinMergeRowCountException`` stating both counts, no merged file is left,
  and ``merge-pin`` fails in those words (``SearchSteps.MergePin``). The
  record's total is the written file's count. A test seam,
  ``PinMerger.Output``, lets a test put a lossy stream before the disk.
* **C, the Results run chooser.** ``ResultsPane``: an arrow on the closed
  chooser opens its list and changes nothing; arrows in the list open no
  run; Enter, Space or a click on a run opens that one; Escape puts the
  chooser back on the open run.
* **D, the retained heap: a leak, traced and fixed.** Each discarded column
  header stayed reachable from the pane's long-lived heading button through
  the old ``LabelSkin``'s listener on the button's layout bounds (the
  agent's heap-dump trace: 1,260 of 1,280 live headers on that one path
  after 150 opens). ``ResultsPane.headingMoved`` disposes the skin of the
  label a heading leaves.
* **E, the test defect.** ``KeyboardOnlyNavigationUiTest`` starts the
  application over its own empty data directory
  (``ParameterEditorApp.launchOver``) and asserts nothing is listed as
  installed and nothing was created there.
* ``scripts/verify-workflow-gates.sh`` control 3b (a header written for
  every input): the product's own read-back now refuses that file, so the
  control is graded on the merge's words and on gates 1 and 3 both red. The
  same defect is injected; the control count is unchanged.

**Not built: item B (PSM identifiers).** The agent investigated it, measured
a fix with both Comet releases (working directory ``<run>/outputs/comet``
and ``-N<base>``; the PIN is byte-identical but for the directory), and
stopped at its brief's hard stop: seven tolerated fields of
``scripts/matrix/determinism-data.json`` ("the run's own directory") would
stop being true. No golden and no pinned count moves. Its record is
``scratch/p16/u3/item-B-investigation.txt``. Carried to **unit 3b**
(decision P16-9).

**What I ran and saw**, at ``8446134``.

* ``bash scripts/dev-verify.sh --since 91efc6d --verify`` (the whole
  reactor, which is what four changed modules close over): BUILD SUCCESS in
  63 min; tests run / skipped per module -- domain 1238 / 0, provenance 709
  / 2, process 286 / 0, results 1312 / 0, tools 1140 / 1, install 1071 / 2,
  params-comet 2123 / 0, params-percolator 68 / 0, workflow 1039 / 0, ui 843
  / 0, app 584 / 91 (the opt-in suites), archtests 40 / 0; 0 failures, 0
  errors. The five new or changed classes ran: ``PinMergerTest`` 17,
  ``RealMergeRowLossTest`` 1, ``KeyboardOnlyNavigationUiTest`` 4,
  ``ResultsRunChooserUiTest`` 7, ``ResultsHeapOverOpenedRunsUiTest`` 1.
* **Injection A**, into ``PinMerger`` (production): the comparison
  ``written != read`` made ``written > read``, so a lost row is tolerated
  (anchor gone, ``sha256sum -c`` FAILED). ``PinMergerTest``: 17 run, **2
  red** (``theOnlyRowLost``, ``aRowLostOnTheWayToTheDisk``; the doubled-row
  case stays green, as it should). ``RealMergeRowLossTest``: 1 run, **1
  red**, "expected: <... was not written completely: 6472 data rows were
  read ...> but was: <a merge of 6472 input rows cannot hold 6471>" -- the
  record's own constructor still refuses the sum, a second guard the agent
  did not mention. Restored, ``sha256sum -c`` OK.
* **Injection C**, into ``ResultsPane``: the value listener's
  ``if (!run.isShowing())`` made unconditional, so every arrow in the open
  list opens a run. ``ResultsRunChooserUiTest``: 7 run, **6 red**, e.g.
  "two runs up from run-a ==> expected: <run-c> but was: <run-d>".
  Restored, OK.
* **Injection D**, into ``ResultsPane``: ``discarded.setSkin(null)``
  deleted. ``ResultsHeapOverOpenedRunsUiTest``: 1 run, **1 red**, "live
  column headers 173 then 776; live heap 163499624 then 175174000 bytes,
  over 120 runs opened". Restored, OK.
* **E** is a change to a test, so there is no production injection. I read
  the diff and the agent's four logs (``_build/p16u3/e-*.log``): with a
  Comet installed in a redirected copy of the folder, the old test passed 4
  of 4 twice; the new assertion on the old launch was red ("expected: <[]>
  but was: <[CometGUI-managed: installed]>"); the new test passed 4 of 4.
  **So the failure Phase 15 recorded does not occur**, and what was fixed is
  the isolation. I did not repeat the redirected install.
* ``bash scripts/verify-all-gates.sh --only
  workflow,results,shell,docs,traceability,flakiness,quality`` (four at a
  time): **7 passed, 0 failed, 1023 s** -- workflow 110, results 100, shell
  30, quality 42, flakiness 44, traceability 8, docs 1, each at its floor;
  ``_build/m2repo``, ``tools``, ``scratch`` and ``.venv`` unchanged. These
  are the three harnesses that name a file the unit changed, and the four
  that read tests, scripts and pages in general.
* The version matrix over the present fixture
  (``COMETGUI_MATRIX_FIXTURE=scratch/fixture bash scripts/matrix/run-matrix.sh
  --offline``): RUN-PASSED 11, RUN-FAILED 0, UNAVAILABLE 14, UNVERIFIED 36,
  GREEN against the committed goldens -- the merge's read-back changed no
  result.
* Not repeated: the agent's PIT run over ``org.cometgui.tools.comet``
  (38 of 38 killed, its log ``_build/p16u3/pit.log``); unit 6's ``--only
  tests`` grades that package again. ``SearchSteps`` and ``ResultsPane`` are
  outside PIT's targets.

**Cost.** About 45 s more in the reactor ``verify`` (the agent's figure; in
my run the four new classes took 6.9, 13.9, 15.8 and 13.1 s). No gate's
count or floor changed.

**Carried, and where.**

* Item B: unit 3b.
* ``SectionNavigationUiTest`` and ``AccessibleNameEnumerationUiTest`` still
  launch through ``main`` and read the machine's application-data folder;
  the Provenance section's own run selector was not examined; "a click
  elsewhere or focus leaving the chooser abandons the walk" is claimed by
  ``ResultsPane``'s comment and tested by nothing. All three: unit 4, items
  G and H.
* The ``gui-heap-peak`` threshold's rationale in
  ``scripts/perf/performance-data.json`` still cites the creep. It is pinned
  data; it is retaken with the baseline in unit 5d.
* ``scripts/dev-verify.sh`` told this unit that its change to
  ``verify-workflow-gates.sh`` maps to ``verify-shell-gates.sh`` -- the
  wrong-gate mapping tier 1 listed. Unit 16 touches the script; fixed there
  with a test of the mapping.
* For tier 1: ``STATUS.rst``'s residue line "``KeyboardOnlyNavigationUiTest``
  fails on a machine with Comet in the application-data folder" was a
  prediction and is not what happens (the test passed there; it was
  unisolated). The Limelight XML's ``conversion_program arguments``
  attribute carries the run's absolute paths whatever is done about PSM
  identifiers; nothing in the residue list covers it.

Unit 4 -- Residue B: install and tools (accepted 2026-11-13, after a second round)
-----------------------------------------------------------------------------------

Two fresh agents. The first (commits ``6d72280`` .. ``9995ceb``, eleven)
finished items A to G and the second half of H and ran short of context; the
second (``ba86345`` .. ``a502515``, five) finished H, repaired the ``e2e``
control that unit 3 had broken, and closed two loose ends. Briefs:
``scratch/p16/orch/briefs/u4.txt`` and ``u4r.txt``.

**What was built** (production diff read in full, both rounds).

* **A.** ``StagedToolProbe`` ends every refusal made after the binary ran
  with its exit status ("It exited with status N."), so a failed install's
  recorded reason carries it. The two capability probes already said it; a
  test now pins that with a redacted token.
* **B.** ``ToolCache`` keeps a failed install's redacted reason at
  ``<root>/cache/failures/<key>.txt``; ``ManagedToolManager`` writes it on
  failure, removes it when the next attempt starts, and reads it back
  (redacting again) when it has no attempt of its own. Installed outranks
  it. Round 2: the write is ``ATOMIC_MOVE, REPLACE_EXISTING``, as the
  product's other atomic writes are.
* **C.** ``CometCapabilityProbe`` takes its workspace parent; every tools
  test passes its own and requires it empty afterwards.
* **D.** ``SecretToolCredentialStore``: an interrupted wait cancels
  ``secret-tool`` through the process service, restores the interrupt and
  throws its checked failure.
* **E.** ``ZipArchiveReader`` refuses a name length or an extra-field length
  that runs past the central directory, each as ``MALFORMED_ARCHIVE``.
* **F.** ``manifests/tools.json``: the Windows row of Comet 2026.02.2 lists
  the four Visual C++ runtime libraries its import table names and carries
  the 2026.03.0 row's advisory. ``WindowsCometImportTableTest`` holds every
  Windows Comet row to its mirrored binary's import table and fails, not
  skips, without the binary.
* **G.** ``SectionNavigationUiTest``, ``AccessibleNameEnumerationUiTest``,
  the twelve classes that used ``ParameterEditorApp.launch`` and
  ``ConsoleFloodUiTest`` no longer read the machine's application-data
  folder. Still through ``main``, because they test it:
  ``CometGuiApplicationStartupTest`` and the opt-in
  ``PerformanceStartupChild``. Round 2 removed the now uncalled
  ``RunningApplication.launchedByMain``.
* **H.** The Provenance section's run selector did read every run passed
  (the new ``ProvenanceRunSelectorUiTest``: 9 of 10 red on the old pane).
  Unit 3's logic moved to ``org.cometgui.ui.controls.CommittedChoice`` and
  both panes are built over it. ``ResultsRunChooserUiTest`` gained the two
  abandon cases and a case holding the driver to what the cells show.
* **The** ``e2e`` **repair.** ``ResultsPane`` no longer has a cell factory:
  the mouse press on a run is seen by a filter on the pop-up's list. The
  driver, the scenario and the harness are untouched.

**What I ran and saw**, at ``a502515``.

* ``bash scripts/dev-verify.sh --since 4907634 --verify``: BUILD SUCCESS in
  47 min 37 s; domain 1238, provenance 709, process 286, results 1312, tools
  1143, install 1090, params-comet 2123, params-percolator 68, workflow
  1039, ui 843, app 601 (91 opt-in skipped), archtests 40; 0 failures, 0
  errors.
* **My injections**, each into production code, anchor gone and
  ``sha256sum -c`` FAILED while in, restored and OK:

  .. list-table::
     :header-rows: 1
     :widths: 8 46 46

     * - Item
       - Injection
       - Red
     * - A
       - ``StagedToolProbe.exitedWith`` returns the empty string
       - ``StagedToolProbeTest`` 14 run, 4 failed
     * - B
       - ``ManagedToolManager`` never forgets an earlier failure when a new
         attempt starts
       - ``FailedInstallReasonAfterRestartTest`` 7 run, 2 failed (a later
         attempt that is cancelled; one that succeeds)
     * - C
       - ``CometCapabilityProbe`` does not delete its workspace
       - ``CometCapabilityProbeTest`` 20 run, 16 failed
     * - D
       - ``SecretToolCredentialStore`` does not restore the interrupt
       - ``SecretToolCredentialStoreTest`` 15 run, 2 failed
     * - E
       - ``ZipArchiveReader``'s extra-field bound off by one; its name
         bound off by 65,536
       - ``ArchiveEdgeCasesTest`` 38 run, 1 failed, each time
     * - F
       - one library deleted from the 2026.02.2 row of ``tools.json``
       - ``WindowsCometImportTableTest`` 1 run, 1 failed;
         ``ShippedManifestTest`` 20 run, 1 failed
     * - H
       - ``CommittedChoice`` does not put the chooser back when its list
         closes without a choice
       - ``ResultsRunChooserUiTest`` 10 run, 8 failed;
         ``ProvenanceRunSelectorUiTest`` 10 run, 9 failed

  My first injection for B did not compile and proved nothing; it was made
  again. G is a change to tests; I read it and did not inject.
* ``bash scripts/verify-all-gates.sh --only
  e2e,results,shell,provenanceui,pdv,limelight,install,manifest,hardening,paramui,flakiness,quality,traceability,docs``
  (four at a time): **14 passed, 0 failed, 3000 s** -- e2e 50, results 100,
  shell 30, provenanceui 96, pdv 107, limelight 130, install 95, manifest
  30, hardening 35, paramui 87, flakiness 44, quality 42, traceability 8,
  docs 1, each at its floor; the four shared directories unchanged.
* The version matrix over the present fixture, after the manifest's change:
  RUN-PASSED 11, RUN-FAILED 0, UNAVAILABLE 14, UNVERIFIED 36, GREEN.
* Not repeated: the agents' PIT runs (``install.archive`` 98.5%,
  ``install.cache`` 99.6%, ``install.manager`` 100%, ``install.probe``
  100%, ``tools.comet`` 99.5%, ``tools.credentials`` 100%; ``ToolCache`` 49
  of 49 after round 2). Unit 6's ``--only tests`` grades every package again.

**Cost.** 27 tests and about 5 s (round 1); ``ProvenanceRunSelectorUiTest``,
about 13 s and one forked JVM (round 2). No gate's count or floor changed.

**Unverified.** Item B's replace-on-write on Windows and macOS: Linux
replaces the target of a rename with or without ``REPLACE_EXISTING``, so the
new test is not red on the old line here. It goes to the runner units (9,
10).

**Carried, and where.**

* Two other bare ``ATOMIC_MOVE`` onto a file (``ToolCache.writeMarker``,
  ``LimelightConverter`` about line 172) were not examined: unit 9, with the
  Windows runner, where the difference can be seen.
* Workflow and app tests still let both probes make their workspaces in
  ``/tmp`` (they are removed unless the JVM is killed). Unit 6 counts
  ``/tmp/cometgui-*-probe-*`` before and after its PIT run over workflow
  classes and fixes it there if any is left.
* ``dev-verify.sh``'s closing text says Spotless, Checkstyle and SpotBugs
  were not run even with ``--verify``, where they are: unit 16, with the
  wrong-gate mapping.
* For tier 1: ``STATUS.rst``'s Phase 15 residue list still names these items
  as open; ``handoffs/PHASE-14-worklog.rst`` line 82 names the removed
  ``launchedByMain`` (history, left); the specification has no ``R-INS``
  rule although documents cite one.

Unit 3b -- PSM identifiers without the absolute run path (accepted 2026-11-13)
------------------------------------------------------------------------------

One fresh agent, one round. Commits ``631e0fe`` (the readers), ``68b74d7``
(the command, its tests, the harness's controls 1a, 1b and 2, the stand-in
Comets) and ``0b18c18`` (the tolerance, the grader, the pages); 49 files,
+1817 -307. Brief ``scratch/p16/orch/briefs/u3b.txt``.

**What was built.** ``CometSearchCommands`` gives Comet ``-N<base>`` and
runs it in ``<run>/outputs/comet``; ``R-CMT-03``, ``R-CMT-04`` and
``R-PROC-04`` hold as written (``-N`` still names a path inside the run
directory, the working directory is explicit and inside it), so no
amendment is asked for. Every identifier of a new run -- a PIN's SpecId,
Percolator's PSMId and ``psm_id``, an exported table -- is
``<base>_<scan>_<charge>_<rank>``. ``RunLayout.psmIdBases`` gives the readers
both the bare base and the absolute one, so **a run made before the change
still opens, maps to its source files, shows in PDV and converts**;
``PoutForConverter``'s copy is still made for such a run and not for a new
one.

**The determinism tolerance** (P16-9). The seven "run's own directory"
fields are gone from ``scripts/matrix/determinism-data.json`` and from the
table in ``testing.rst``; nothing was added to either.

**A grader defect came out with it, and the agent repaired the grader**
(decision **P16-10**, mine, on reading the diff). With bare identifiers the
suite went red on one tied pair of peptides in ``pout.xml``.
``determinism.py``'s ``pout_key`` takes a scan from an element's first
identifier to decide whether two rows are a tie; while every identifier
began with a path it never found one, so a tie was judged on scores and
mass alone, and once it could find one it took, for a ``<peptide>``, the
scan of whichever PSM came first in the text. The repair takes a peptide's
scan from the PSM among its own that carries its score and mass. That is
**stricter** than what was graded before -- two rows of different scans are
no longer a tie -- and it is the rule the page already states. Five new
self-test cases; with no scan in the key, two of them come back "only tie
order" where they must be DIFFERS. I accept it: it is a repair to a check
that could not see what it claimed to compare, in the allowed direction,
and nothing was tolerated to make a run green. **Tier 1 should read this
one** (``git show 0b18c18 -- scripts/matrix/determinism.py``).

**What I ran and saw**, at ``0b18c18``.

* ``bash scripts/dev-verify.sh --since b31a49b --verify``: BUILD SUCCESS in
  47 min 39 s; domain 1239, provenance 709, process 286, results 1312, tools
  1151, install 1090, params-comet 2123, params-percolator 68, workflow
  1051, ui 843, app 601 (91 opt-in skipped), archtests 40; 0 failures, 0
  errors.
* **Injection I**, into ``RunLayout.psmIdBases`` (production): only the bare
  base returned, so an earlier run's identifiers are not recognised (anchor
  gone, ``sha256sum -c`` FAILED). Red in four modules: ``RunLayoutTest`` 1
  of 19, ``PdvMzTabsTest`` 9 of 12, ``PsmFiguresTest`` 8 of 11,
  ``PdvMzTabsFidelityTest`` 1 of 2, ``RealIdentifiersTest`` 1 of 7,
  ``ProjectResultsPortTest`` 3 of 10, ``ResultsAccessibilityUiTest`` 1 of 4.
  Restored, OK.
* **Injection II**, into ``CometSearchCommands``: Comet run in the run's
  root again with the bare ``-N``, so its files land in the wrong place.
  ``CometSearchCommandsTest`` 3 of 12, ``RealCometRunTest`` 7 of 7,
  ``RealIdentifiersTest`` 7 of 7 red. Restored, OK.
* **Injection G**, into ``scripts/matrix/determinism.py``: the tie key's
  scan made empty, which is what the grader effectively had before.
  ``determinism.py --self-test``: exit 7, 2 of 28 FAILED -- "two peptides
  ... of different scans, swapped ... -> DIFFERS -- only tie order" and the
  same for two PSMs. Restored, OK; 28 of 28 again.
* ``bash scripts/verify-all-gates.sh --only
  workflow,percolator,results,pdv,limelight,provenanceui,e2e,shell,paramui,hardening,traceability,docs,flakiness,quality``:
  **13 passed, 1 FAILED, 3138 s** -- workflow 110, percolator 109, results
  100, pdv 107, limelight 130, provenanceui 96, shell 30, paramui 87,
  hardening 35, quality 42, flakiness 44, traceability 8, docs 1, each at
  its floor; **e2e 48 of 50**. The red is not this unit's code: in control
  4's failure-path suite ``NetworkUnavailableUiTest.theNetworkBack`` waited
  two minutes for Percolator's row to say installed and it said "not
  installed" throughout, as if the Install control was pressed and no
  attempt began; Comet's install, one line earlier in the same test, had
  worked. The test's network is a loopback server. **Rerun by me,**
  ``--only e2e``: **50 of 50 in 1348 s.** The same control was 50 of 50 at
  ``a502515`` (the agent's run and mine) and at ``0b18c18`` in the agent's
  run. One red in five runs: an intermittent failure in a release-critical
  test, which the flakiness policy calls a defect. It is not waved through
  and not blamed on the unit: **unit 4c** (below) reproduces and fixes it
  before 5a starts. The red run's logs are kept in
  ``_build/p16-orch/u3b/e2e-red.log`` and ``e2e-gate-logs-red/``.
* The version matrix over the present fixture: RUN-PASSED 11, RUN-FAILED 0,
  UNAVAILABLE 14, UNVERIFIED 36, GREEN against the unchanged goldens. The
  determinism suite (``--part determinism``): exit 0, GREEN, "every repeat
  and variation agrees within the documented tolerance; every control
  differs".
* Not repeated: the agent's PIT figures (``domain.run`` 77 of 77,
  ``results.parser`` 43 of 43, ``tools.comet`` 45 of 45, ``tools.pdv`` 256
  of 264, no survivor); unit 6's ``--only tests`` grades them again.

**Cost.** ``RealIdentifiersTest`` about 32 s; ``PdvMzTabsFidelityTest``
about 3 s more.

**Carried, and where.**

* The Limelight XML's ``conversion_program arguments`` still holds absolute
  paths: for tier 1, as recorded under unit 3.
* ``PreRunChecks`` still says "Comet runs in the run directory" (it runs in
  ``outputs/comet`` inside it): unit 12, with the user pages.
* The agent saw, once, from its own test helper, a step that throws an
  ``Error`` leave the engine waiting until ``await`` timed out (318 s). Not
  investigated. Unit 6 looks at it, since it adds mutation testing over the
  engine's neighbours and a mutant that throws an ``Error`` would hang the
  same way.
* Comet's 511-byte limit no longer bounds a run's output base, only
  ``-P``: the Phase 14 unit 8a finding is partly out of date; unit 12's
  troubleshooting page states what is true now.

Unit 4c -- an Install that is pressed and never starts (accepted 2026-11-13)
-----------------------------------------------------------------------------

One fresh agent, one round. Commits ``e3a9246`` (the fix, its test, the user
page), ``4d0d629`` (the flakiness record in ``testing.rst``) and ``60970a1``
(the fix restructured so the ``install`` harness's anchor line is byte for
byte as before -- the agent's first form stranded control 5's anchor, the
harness said so with exit 4, and the agent changed its own code, not the
harness). Brief ``scratch/p16/orch/briefs/u4c.txt``.

**The cause is in the product.** When an install ends, the row disables the
action that holds the keyboard focus (Cancel). At the next pulse JavaFX moves
the focus to the next control in traversal order -- another build's Install
-- and the rows' scroll pane scrolls it into view, so every row moves. A
press aimed in that one frame lands on a label. The test pressed
Percolator's Install just after Comet's row said "installed"; a user who has
scrolled to a second build while the first downloads loses the press the
same way. The agent's evidence: 0 red in 60 runs of the class at 60 Hz, 4 in
240 of an instrumented copy, and **4 in 5 of the unchanged class with the
pulse slowed to 4 Hz**, each with the gate's own two messages; a trace
showing text, aim, the focus move, the button 466 px lower, a press on a
label and no action. Rows are not rebuilt, the view model refuses nothing,
and unit 4's failure record is not involved.

**The fix.** ``ToolManagerPane``: before a row disables an action that owns
the focus, the focus is moved inside the row (to the row's other action if
it can be taken, otherwise to the state label), which scrolls nothing. No
test, driver, harness, bound or gate was changed.

**What I ran and saw**, at ``60970a1``.

* The diff (57 lines of production code).
* **My injection**, into ``ToolManagerPane``: the call ``handTheFocusOn()``
  deleted (anchor gone, ``sha256sum -c`` FAILED).
  ``ToolManagerRowFocusTest``: 6 run, **5 red**. Restored, OK.
* ``bash scripts/dev-verify.sh --since cb593b6 --verify``: BUILD SUCCESS in
  50 min 18 s; domain 1239, provenance 709, process 286, results 1312, tools
  1151, install 1090, params-comet 2123, params-percolator 68, workflow
  1051, ui 849, app 601 (91 opt-in skipped), archtests 40; 0 failures.
* ``bash scripts/verify-all-gates.sh --only
  e2e,install,shell,paramui,results,pdv,limelight,provenanceui,workflow,percolator,flakiness,quality,traceability,docs``,
  beside that verify: **14 passed, 0 failed, 3044 s** -- e2e 50, install 95,
  shell 30, paramui 87, results 100, pdv 107, limelight 130, provenanceui
  96, workflow 110, percolator 109, flakiness 44, quality 42, traceability
  8, docs 1; the four shared directories unchanged.
* Not repeated: the agent's loops after the fix (the class 30 times running
  unconfined and 15 times on two cores at ``60970a1``, 0 red; 5 of 5 green
  at 4 Hz, where the old code was 4 of 5 red). ``e2e`` has now been 50 of 50
  in my last two runs.

**What this does not show.** The gate's own red cannot be replayed; the
identification rests on the identical message, the traces and the forced
runs. If ``NetworkUnavailableUiTest`` goes red again in these words, the
cause was not, or not only, this.

**Cost.** ``ToolManagerRowFocusTest``, 6 tests, about 2.3 s.

**Carried, and where.**

* After an install ends the keyboard focus is on that row's state label,
  which shows no focus ring; before, it jumped to another build's Install.
  Whether a focus with no visible indicator is acceptable is a UX finding
  for a person: it goes into unit 15's keyboard checklist as a named item,
  and to tier 1.
* ``NoManagedBuildView``'s Register button (``D-011``) is disabled while it
  holds the focus -- the same shape, not fixed; other controls disabled
  while focused inside a scroll pane were not audited. Unit 15's heuristic
  and keyboard review audits them and files what it finds.
* A row that gains its installed path still moves the rows below it by 18
  px in one frame; a press in that frame could still miss. Recorded, not
  fixed.

The outage (2026-11-13)
-----------------------

Between about 09:50 and 17:58 this orchestrator was stopped by an
authentication failure on the host, relayed by tier 1 on resuming it. Unit
3b's agent had already finished and reported (its last commit, ``0b18c18``,
is its finished state: verified above, not assumed). What the outage
interrupted was my own gate run for unit 3b, which ran on detached and
ended at 10:16 with the result above. On resuming I checked: the tree
clean at ``0b18c18``; no Maven, gate or tool process alive; every file I
had injected into equal to ``HEAD`` (``git status`` empty). Nothing was
lost.

Runner runs relied on
=====================

Every pushed branch and every run, with its URL, commits and conclusion.

.. list-table::
   :header-rows: 1
   :widths: 22 22 56

   * - Branch
     - Commits
     - Run and conclusion
   * - ``phase16/raw-trial-1``
     - ``6dae9f5`` plus trigger commit ``25abc52`` (adds
       ``.github/workflows/phase16-raw-trial.yml``, a copy of
       ``scripts/spikes/windows-raw-trial/workflow.yml``, 63 lines, nothing
       else)
     - https://github.com/mriffle/CometGUI/actions/runs/43150059739 --
       ``phase16-raw-trial``, job ``130956861567``, **success**, 2026-11-12
       03:43-03:44 UTC. The ``RAW-TRIAL|`` lines of its log are the result.

Rejections and rework
=====================

Units sent back, why, and what changed.

**Unit 2, sent back once (2026-11-12).** Two defects found at sign-off.

#. ``check-fixture.sh --require-committed`` **could not go red on an altered
   committed file.** It compared the candidate with whatever bytes the
   checkout held, so a working-tree FASTA with a record removed, given as the
   candidate, was reported as "the committed fixture FASTA" and passed GREEN
   with exit 0. On a runner that is the case the flag exists for. Fixed in
   ``13f7b07``: the reference is the digest the committed accession list pins
   (``subset-sha256``), read by ``fixture_fasta.py``'s own reader; a checkout
   whose file differs from its pin, or a candidate that differs from it, is
   REFUSED with exit 3 before anything is searched; eight self-test cases over
   injured copies. Seen red by me, as recorded above.
#. ``check-fixture.sh --help`` described the self-test by the phase
   document's prediction (red on the decoy count and on training) and not by
   what it requires (red on ``psms-above-1pct``). Corrected in ``13f7b07``.

**Unit 3, a defect found after acceptance (2026-11-13).** Unit 3's item C
gave the Results run chooser a cell factory, and the end-to-end driver
refuses to say what a combo box shows once it has one, so
``TierACanonicalScenarioTest#relaunch`` failed and the ``e2e`` control was
red from ``5973c9e``. **I accepted unit 3 without running** ``e2e``: I chose
the controls by searching the harness scripts for the changed files' names,
and ``verify-e2e-gates.sh`` names none of them -- it drives the section
through the scenario classes, which are opt-in and so not in the reactor
``verify`` either. Unit 4's first agent found it. Repaired in unit 4's
second round (``ba86345``) at its cause: the chooser needs no cell factory.
``e2e`` is 50 of 50 again in my run at ``a502515``. **From here on a unit
that changes anything under** ``cometgui-ui`` **or the application's
sections is signed off with** ``e2e`` **and every section harness, whatever
the scripts name.**

**Unit 4, sent back once (2026-11-13)** -- to a fresh agent, the first
having run short of context with item H half done. The second round also
carried the ``e2e`` repair above. Nothing of the first round was rejected.

Deferred
========

Anything explicitly not done in this phase, with the reason and where it goes.

Nothing is deferred out of the phase so far. (At the pause, units 3 to 16
had not started; the owner resumed the phase the same day.)

Blockers escalated
==================

What was escalated to the main orchestrator, when, and the outcome.

Escalated with the pause handoff, 2026-11-12. None is answered here.

#. **The floors** in the table under *Unit 2*: a proposal, for tier 1 to
   confirm (the brief).
#. **Percolator's warning that its negative samples are too few.** With one
   result per spectrum -- the configuration that is converted to Limelight
   XML -- Percolator prints it at 105 decoy PSMs or fewer. The whole proteome
   gives 120; every subset measured, up to 8,698 of 20,652 records, gives 90
   to 105. Percolator still trains and every run converts. No trimmed FASTA
   measured clears the warning, so the check reports it and does not grade
   it. Whether that is acceptable follows from the owner's decision to trim
   and is not an agent's to accept.
#. **What over-filtering does, measured, is not what the phase document and**
   ``D-006`` **predict.** With the identified proteins alone Percolator still
   trains and decoys do not disappear (2,812 decoy rows against 2,784); more
   PSMs pass 1% and fewer are rejected. The check is red on it for that
   reason, by name.
#. ``D-018`` says a pinned checksum of the proteome "can no longer be fetched
   at all" after a UniProt release; the 2026_02 bytes are still served by
   EMBL-EBI's QfO directory and its release archive (the agent's finding).
#. ``docs/developer/testing.rst`` calls the matrix pair runs "Limelight
   enabled"; they request XML and do not convert. With five results per
   spectrum the converter refuses the run (Phase 14's E5, still open), which
   is why the acceptance check converts a one-result run.
#. **Unit 5 will be larger than its row suggests** (the agent's inventory,
   ``docs/developer/test_fixtures.rst``, *What the change of FASTA touches*:
   129 files). Most real-binary tests search the proteome's first 1,000
   records, a different file with its own digest and committed index headers;
   four tests and one harness control need the whole proteome because they
   need a search long enough to cancel (Comet takes 2.6 s a file against the
   subset); and many tests read Phase 00's outputs under
   ``scratch/scientific-path`` by digest, which a runner does not have and
   which cannot be committed.
#. From unit 1, documents neither tier 2 nor tier 3 may edit: listed at the
   end of *Unit 1* above.
#. ``CONTRIBUTING.rst`` offers ``scripts/feasibility/check-docs.sh FILE.rst``
   as the single-file check, and it refuses any file outside
   ``docs/feasibility``; ``scripts/dev-verify.sh`` points a changed shell
   script at ``verify-shell-gates.sh``, which is the application-shell gate
   (both the unit 2 agent's findings, not checked by me).
