==========================================================================
PHASE-16 handoff -- Documentation and Release Qualification (SECOND PAUSE)
==========================================================================

:Phase: 16
:Written: 2026-11-14 by the second Phase-16 orchestrator (tier 2). It
   replaces the first orchestrator's pause record of 2026-11-12, whose
   substance on units 1 and 2 is carried below.
:Outcome: **PAUSED by the owner after unit 5a**, not finished. The owner,
   relayed by tier 1 on 2026-11-13: *"It looks like 5a is currently being
   run. When 5a finishes, I want a pause."* Units 1, 2, 3, 3b, 4 and 4c are
   accepted. **Unit 5a is PARTIAL**: half of it landed and is signed off;
   the other half was not dispatched, because it turns on a finding that is
   tier 1's to rule on. Units 5b to 5e and 6 to 16 have not started. **No
   exit-gate item is met.**
:Records: ``handoffs/PHASE-16-worklog.rst`` -- **this page is the map; the
   work log is the proof.** Each sign-off there names the commands run, the
   counts seen and the orchestrator's own injections. Its section *Current
   state -- a checkpoint kept true* is what a fresh session reads first.
:Commits: the first orchestrator's ``c15acef`` .. ``0659194``; the second's
   ``91efc6d`` .. the commit adding this page; tier 1's own among them. Nobody
   in this phase ran ``scripts/build.sh`` or the full
   ``scripts/verify-all-gates.sh``.

.. contents:: Contents
   :depth: 2
   :local:

In one paragraph
================

Comet for Windows reads the project's Thermo RAW fixture on a GitHub
runner. A FASTA trimmed to suit the K562 spectra is committed, reproducible
byte for byte, with an acceptance check tier 1 confirmed; the owner accepted
Percolator's warning on it (``D-020``, option A). All of Phase 15's residue
is fixed, each item with a test seen red on the old code, and four defects
that fixing it uncovered are fixed too: PSM identifiers no longer carry the
run's absolute path; the Provenance run selector no longer reads every run
it passes; a press on Install is no longer lost when another install ends;
the determinism grader's tie key now includes the scan it claimed to. The
move of the tests onto the committed FASTA has begun: the tools and
parameter modules' real-binary tests search a cut of it. The workflow and
application tests still search the whole proteome's first 1,000 records,
and moving them has met an open product defect (the Limelight converter
refuses a five-result run that holds a duplicated peptide). Nothing else in
the phase has started.

Units, and the evidence
=======================

Every figure below is from a run the orchestrator of the time made itself;
the commands and the injections are in the work log under each unit's name.

.. list-table::
   :header-rows: 1
   :widths: 6 12 82

   * - Unit
     - Verdict
     - What, and what was seen
   * - 1
     - Accepted
     - **The RAW trial.** ``scripts/spikes/windows-raw-trial/``; the WH8102
       proteome under ``fixtures/synechococcus-wh8102/``. Run
       ``43150059739`` on a Windows runner: both Comet releases read
       ``ph050213_18.raw`` directly (17,655 PIN rows; 873 / 761 and 894 /
       767 PSMs / peptides at q < 0.01). Two pin injections red.
   * - 2
     - Accepted
     - **The trimmed FASTA.** ``fixtures/human-k562-subset/`` (3,698
       records, 2,539,164 bytes, SHA-256 ``bd252b62...1b34``),
       ``scripts/fixtures/``. Rebuilt byte for byte from its source; the
       acceptance check green (60 criteria, four pairs, two
       configurations) and red on the identified proteins alone; tier 1
       confirmed the floors by its own run.
   * - 3
     - Accepted
     - **Residue A.** The merged PIN's rows are counted after writing
       (``PinMergeRowCountException``); the Results run chooser opens a run
       when a choice is committed; the retained-heap leak traced to
       discarded column headers and fixed; ``KeyboardOnlyNavigationUiTest``
       isolated. Injections into ``PinMerger`` and ``ResultsPane`` red;
       reactor ``verify`` green; 7 controls green; matrix GREEN. **Its
       cell factory broke** ``e2e`` **and I accepted it without running
       that control**; found by unit 4's agent, repaired in unit 4.
   * - 3b
     - Accepted
     - **PSM identifiers.** Comet is run in ``<run>/outputs/comet`` with
       ``-N<base>``; no identifier of a new run holds a path; runs made
       before still open, map, show in PDV and convert. The determinism
       tolerance lost its seven "run's own directory" fields; **the
       grader's tie key was repaired** (P16-10). Injections into
       ``RunLayout``, ``CometSearchCommands`` and ``determinism.py`` red;
       reactor green; 13 of 14 controls green and ``e2e`` 48 of 50, then 50
       of 50 on a rerun (see 4c); matrix and determinism GREEN against the
       unchanged goldens.
   * - 4
     - Accepted
     - **Residue B**, two rounds. The staged probe's refusal carries the
       exit status; a failed install's reason survives a restart;
       ``CometCapabilityProbe`` owns no workspace in ``/tmp``;
       ``SecretToolCredentialStore`` cancels on interrupt;
       ``ZipArchiveReader`` refuses lengths past the directory; the Windows
       manifest row for Comet 2026.02.2 lists its runtime libraries, held to
       the binary's import table by a test; fifteen GUI test classes no
       longer read the machine's application-data folder; the Provenance
       selector fixed over a shared ``CommittedChoice``. Eight injections
       red; reactor green; 14 controls green; matrix GREEN.
   * - 4c
     - Accepted
     - **A lost Install press.** One ``e2e`` run in five was red in
       ``NetworkUnavailableUiTest``. Cause in the product: a row disabling
       the action that holds the focus made JavaFX move the focus and
       scroll the list in the next frame. Fixed in ``ToolManagerPane``;
       reproduced 4 of 5 at a 4 Hz pulse on the old code, 0 of 5 after.
       Injection red; reactor green; 14 controls green.
   * - 5a
     - **PARTIAL**
     - **The seams, first half.** See the next section.

Unit 5a -- what landed, what did not, and why
=============================================

**Landed** (``e88e440``, ``6a2f7fc``, ``1984599``, ``581a659``): the
real-binary tests of ``cometgui-tools`` (7 classes) and
``cometgui-params-comet`` (6 classes), and one typed value in
``cometgui-domain``, search **a cut of the committed FASTA**: its first 500
records marked ``identified`` in ``accessions.tsv`` (392,463 bytes, SHA-256
``c12a0261...088e``; I recomputed the cut and its digest independently and
they agree). The six committed index headers, both ``SHA256SUMS`` and every
pinned count were taken again from both Comet releases (the table is in the
work log and in ``docs/developer/test_fixtures.rst``,
``dev-test-fixtures-moved``); the 42 validation verdicts were run again and
none changed; no bound was changed. ``verify-test-gates.sh``'s sandbox
carries ``fixtures/``; ``verify-param-gates.sh`` checks for the file. The
moved classes got no slower (tools 65.9 to 61.3 s, params-comet 15.3 to
12.2 s, the agent's figures).

**What the orchestrator saw of what landed** (the work log, *Unit 5a*):
three injections into production code the moved tests exercise, each red
(``PinReader`` counting decoys twice, ``CometIndexHeaderReader`` one peptide
too many, ``FastaDecoyRule`` one decoy entry too many); the reactor
``verify`` green in 52 min 36 s, 0 failures; sixteen controls, 15 green at
their floors (``tests`` 68, ``params`` 109, ``e2e`` 50 among them) and
``hardening`` red on its control C because I ran the ``verify`` beside it --
rerun alone, 35 of 35. Not shown by anyone: the ``tests`` control red
without the ``fixtures/`` copy in its sandbox.

**Not landed:** everything behind ``RealComet.subset`` (16 workflow classes
and the workflow's ``IndexHeaders``) and ``RealSearch.subset`` (10
application classes), with the three harness lists that follow them
(``percolator``, ``provenanceui``, ``workflow``). They still search the
whole proteome's first 1,000 records and still pass. The agent's unapplied
edits for them are in ``scratch/p16/u5a/patches/``.

**Why it stopped -- two findings, both for tier 1.**

#. **The cut is over-filtered in kind.** No "first N records" of the
   committed file met the tests' bounds, their time and a steady Percolator
   together (first 1,000: Percolator 22 s, twice; first 1,000 background
   records: Percolator exits 1; first 250: a bound of more than 3,000 PSMs
   missed). The first 500 identified records do. Choosing the cut was this
   tier's to do, by measured time, and I accept the agent's choice for the
   two modules it moved: those tests check that the adapters read what the
   tools wrote, not that a search is statistically sound. **Tier 1 should
   know** that a cut of identified proteins alone is what the owner's
   trimming step warned against for the fixture as a whole; here it is a
   cut for adapter tests, and the fixture itself is unchanged.
#. **The Limelight converter refuses a five-result run of any FASTA that
   holds** ``P38159`` **or** ``Q96E39`` (records 1,313 and 2,548 of the
   committed file): at scan 11483 of K562_3 Comet reports one peptide
   twice and converter v2.8.1 stops. On the cut, the workflow's
   ``RealLimelightConversionTest``, ``RealLimelightCompatibleRerunTest`` and
   ``RealIdentifiersTest.theEarlierRunIsStillConverted`` are refused; the
   application's Limelight tests search the same input. They pass today
   only because the proteome's first 1,000 records hold neither protein.
   **This is Phase 14's open finding E5 reached by the tests themselves**,
   and it is why the second half was not dispatched: the ways through are
   to search with one result per spectrum in the tests that convert (which
   changes what they exercise), to cut around the two proteins (which hides
   the defect), or to settle E5 in the product (a product decision). None
   is a unit agent's or this tier's to choose.

Gate items
==========

.. list-table::
   :header-rows: 1
   :widths: 6 16 78

   * - Item
     - Result
     - State at this pause
   * - 1
     - Not started
     - Units 12 and 13. The strict build passes on the tree as it is.
   * - 2
     - Not started
     - Unit 16. The six "planned, phase 15" entries stand.
   * - 3
     - Not started
     - Units 8 to 10.
   * - 4
     - Not started
     - Units 8, 9 and 14. Two data files are committed with their
       attribution and wait for the licence audit.
   * - 5
     - Not assessed
     - ``D-001`` is decided; the audit that records it is unit 14.
   * - 6
     - Not started
     - Unit 15. Nothing prepared; no finding exists. Two items are noted
       for its keyboard checklist (unit 4c).
   * - 7
     - Not started
     - Units 9 and 16. One intermittent red in a release-critical test was
       found and fixed at its cause (unit 4c); the quarantine register is
       still empty.
   * - 8
     - **Partial**
     - The trimmed FASTA exists; its acceptance check passes, was seen red
       on an over-filtered FASTA, and its floors are confirmed. Two of five
       modules' real-binary tests read a cut of it. **Not done:** the
       workflow and application tests (5a's second half), the cancel tests
       (5b), Phase 00's outputs (5c), the goldens and the single fixture
       (5d), the proof that nothing reads the whole proteome (5e), the CI
       jobs (7), which are still stubs that exit 70.
   * - 9
     - **Partial**
     - Step 1 only: Comet on a Windows runner reads the file (unit 1). The
       smoke test through the product is unit 11.

Pushed branches and runner runs
===============================

One branch and one run, both the first orchestrator's. **The second
orchestrator pushed nothing**, and no runner run is in flight.

* ``phase16/raw-trial-1`` at ``25abc52`` (``6dae9f5`` plus one commit adding
  ``.github/workflows/phase16-raw-trial.yml``). It stays on ``origin``.
* Run ``43150059739``, workflow ``phase16-raw-trial``, job
  ``130956861567``: success, 2026-11-12 03:43 to 03:44 UTC.

``main`` is tier 1's to push.

State on disk outside git
=========================

All under ignored directories.

Needed by a resume:

* ``scratch/fixture/`` -- the two K562 mzML files and the whole proteome.
  The workflow and application tests still read the proteome's first 1,000
  records from it; ``fixture-fasta.sh verify --source`` rebuilds from it.
* ``scratch/phase05/artefacts``, ``scratch/phase06/artefacts``,
  ``scratch/percolator/3.09``, ``scratch/scientific-path``,
  ``scratch/limelight-upload-spike/artefacts`` -- unchanged.
* ``_build/m2repo`` -- Maven's repository; every check ran offline.
* ``scratch/p16/orch/unit-standing.txt`` and ``scratch/p16/orch/briefs/``
  (``u3``, ``u3b``, ``u4``, ``u4r``, ``u4c``, ``u5a``, ``u5b``, ``u6``) and
  ``_build/p16-orch/briefs/u5c.txt`` -- what each unit agent was or would be
  given. Their substance is in the work log (*How the remaining units are
  briefed*), so their loss costs wording, not content.
* ``scratch/p16/u5a/patches/`` -- the 5a agent's unapplied workflow and
  application seam edits; ``scratch/p16/u5a/measure/`` -- the scripts its
  pinned numbers came from; ``_build/p16u5a/report-draft.txt`` -- its
  report.
* ``scratch/p16/u3/item-B-investigation.txt`` -- the record behind unit 3b.

Evidence, not needed: ``_build/p16-orch/u3``, ``u3b``, ``u4``, ``u4c``,
``u5a`` (my runs and injections, among them the red ``e2e`` run of
2026-11-13, ``u3b/e2e-red.log``); ``_build/p16u3`` .. ``_build/p16u5a`` (the
agents' logs); ``scratch/p16/u*`` (their working files);
``scratch/p16/tier1/`` is tier 1's.

**Nothing is left uncommitted in the tree, and no injection is in place**
(``git status`` empty; no ``INJECTION-IN-PROGRESS.txt`` under ``_build/``).

Decisions encountered
=====================

No ``D-`` item was answered by this tier. ``D-020`` was decided by the
owner (option A) while unit 3 ran, and the order went back to P16-3's.
Design decisions P16-1 to P16-10 are in the work log. **Tier 1 should look
at:**

* **P16-8** -- unit 5 split into 5a to 5e within the four rulings.
* **P16-9** -- the determinism tolerance's seven "run's own directory"
  fields removed with the identifier change.
* **P16-10** -- the determinism grader's tie key repaired (``git show
  0b18c18 -- scripts/matrix/determinism.py``). Stricter than before; it
  touches a check tier 1 signed off in Phase 15.
* **Unit 3b's reading of** ``R-CMT-03`` **and** ``R-PROC-04``: Comet now
  runs in ``outputs/comet`` inside the run with a bare ``-N``; the rules
  hold as written and no amendment was asked for.

Escalated to tier 1
===================

#. **Unit 5a's finding 2** (above): how the tests that convert for
   Limelight are to stand on the committed FASTA, given E5. The second half
   of 5a waits on it.
#. **Unit 5a's finding 1**: the cut of identified records, accepted by this
   tier for the adapter tests; say if it should not stand.
#. **A keyboard focus with no visible ring** rests on a Tool Manager row's
   state label after an install ends (unit 4c's fix). Whether that is
   acceptable is a person's judgement (``AC-UX``); it is listed for unit 15.
#. **Documents this tier may not edit:** ``STATUS.rst``'s Phase 15 residue
   list still names its items as open, and its line that
   ``KeyboardOnlyNavigationUiTest`` fails beside an installed Comet was a
   prediction (the test passed there; it was unisolated). The specification
   has no ``R-INS`` rule although documents cite one.
#. **Not in any residue list:** the Limelight XML's ``conversion_program
   arguments`` attribute carries the run's absolute paths, whatever PSM
   identifiers hold.
#. **A documentation duty from** ``D-020`` **not yet discharged:**
   ``docs/developer/test_fixtures.rst`` still says the warning is put to
   tier 1. It is placed in unit 5d (that page), 5e (``testing.rst``) and 12
   (the user pages); none has run.
#. **Unverified here:** unit 4's replace-on-write of a failed install's
   reason on Windows and macOS (for units 9 and 10).

Surprises
=========

* **A unit was accepted with** ``e2e`` **red.** The end-to-end classes are
  opt-in, so the reactor ``verify`` does not run them, and the harness
  script names none of the interface's files. From unit 4 on, a change to
  the interface, the engine or what a run writes is signed off with ``e2e``
  and every section harness.
* **Fixing the residue found four more defects**, each in code the residue
  list did not name (above, *In one paragraph*).
* **A grader could not see what it compared.** While identifiers began with
  a path the determinism grader's tie key held no scan.
* **A rare red was a real product defect.** One run in five; reproduced only
  by slowing the frame rate.
* **The committed FASTA makes the tests meet E5.** The old input avoided it
  by chance.
* **Percolator with one result per spectrum is slower and unsteady** on any
  input that identifies anything (2.5 to 13 s on the cut; the agent's
  figures). It will bear on the Limelight tests whatever is ruled.
* **A reactor** ``verify`` **beside the** ``hardening`` **control fails
  it**: its control C sees ``cometgui-*/target`` change. My doing, once;
  the standing text for unit agents now forbids it.
* **Two documentation builds at once break each other** (a ``ThemeError``
  from a file removed under the other): tier 1's and mine collided once on
  2026-11-13; mine was run again and passed.
* **A foreground command is moved to the background after 180 seconds**;
  an orchestrator waits in shorter calls.

First thing the next agent should do
====================================

**Tier 1:** record the pause; rule on 5a's two findings (the second
decides how 5a is finished); read P16-10. Nothing here needs a full build.

**The resuming phase orchestrator:** read the brief, then the work log from
*Current state* down. Confirm ``git status`` is clean, no
``INJECTION-IN-PROGRESS.txt`` exists under ``_build/``, and
``scratch/fixture`` is present. Then, **once tier 1 has ruled on finding
2**, dispatch one fresh agent for the rest of 5a (the workflow and
application seams, from ``scratch/p16/u5a/patches/`` as a starting point,
not as proof), sign it off with the reactor ``verify``, ``e2e`` and every
section harness, and go on in the work log's order: 5b, 5c, 5d, 5e, 6, 7,
then 8 to 16.
