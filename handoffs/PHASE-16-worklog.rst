=====================================================================
PHASE-16 work log -- Documentation and Release Qualification
=====================================================================

:Phase: 16
:Phase orchestrator: tier-2 phase orchestrator, dispatched by tier 1 at
   ``d303e21`` (brief ``handoffs/PHASE-16-BRIEF.rst``)
:Started: 2026-11-12

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
     - pending
   * - 3
     - **Residue A, results and workflow.** The merged-PIN row count checked
       by the product; PSM identifiers without the absolute run path; the
       Results run chooser not opening every run it passes; the retained
       heap per opened run traced and fixed or recorded with its cause;
       ``KeyboardOnlyNavigationUiTest`` isolated from an installed Comet.
       Each with a test that proves the value and is red on the old code.
     - Brief obligation 12; ``status-p15-signed``
     - pending
   * - 4
     - **Residue B, install and tools.** The probe's message for a non-zero
       exit carries the status; a failed install's reason survives a restart;
       ``CometCapabilityProbe`` leaves no workspace behind;
       ``SecretToolCredentialStore`` cancels ``secret-tool`` when interrupted;
       ``ZipArchiveReader`` reports an extra-field length past the directory
       as its checked failure. Each with a test red on the old code.
     - Brief obligation 12; ``status-p15-signed``
     - pending
   * - 5
     - **One fixture.** Local testing and CI read the K562 pair with the
       committed trimmed FASTA; one fetch script (checksum, CRLF defence);
       every golden regenerated once with its review record; the performance
       baseline and every test that pinned a count from the old fixture
       moved; the matrix, determinism and end-to-end tiers green on it.
     - Gate 8; step 7; ``R-TEST-07``
     - pending
   * - 6
     - ``D-016`` **option B and** ``D-017`` **option A.** The added PIT wall
       clock measured before adoption (stop above about fifteen minutes);
       command-builder and stage-invalidation classes targeted and graded per
       package at 80%; workflow and results held to 90/85, the raised gate
       shown red; ``--only tests`` once.
     - ``R-TEST-02``; ``AC-TST-02``, ``AC-TST-03``
     - pending
   * - 7
     - **The fixture in CI.** The nightly jobs that named ``D-006`` fetch the
       spectra by checksum on the runner and run against the reviewed
       goldens; serial at four cores or fewer; no job fetches the whole
       proteome; stale ``D-006`` wording corrected. Run on a hosted runner
       from a pushed branch.
     - Gate 8; step 8; ``AC-TST-10``
     - pending
   * - 8
     - **The launcher and the release scripts.** The packaged launcher starts
       the JVM in a UTF-8 locale on every platform's launcher and with
       ``--enable-native-access``; proved on Linux with a non-ASCII path
       through the installed launcher; ``release-package.sh``,
       ``release-checksums.sh`` and ``release-smoke.sh`` real; the SBOM
       beside the artefacts.
     - Gate 3, 4; ``AC-REL-01``; the phase document's warning
     - pending
   * - 9
     - **The release workflow on three runners.** Toolchain, packaging,
       checksums, SBOM and the smoke test on Linux, Windows and macOS
       runners; signing and publishing as P16-5 decides. Pushed and read by
       the orchestrator.
     - Gate 3, 4, 7; ``AC-INS-01``
     - pending
   * - 10
     - **Packaged end-to-end on each runner.** The three release end-to-end
       steps real, on the exact artefact, with the fixture fetched; Linux
       first, then Windows and macOS as far as each goes, the remainder named.
     - Gate 3; ``AC-TST-07``, ``AC-TST-08``
     - pending
   * - 11
     - **The Windows RAW smoke test through the product**, with a reviewed
       golden taken from a runner and the organism's FASTA committed; or the
       job taken out of the nightly and ``AC-TST-11`` recorded not met.
     - Gate 9; ``AC-TST-11``
     - pending
   * - 12
     - **The user documentation tree**, complete, with platform support and
       troubleshooting stating per platform which Percolator versions are
       managed, which write XML, and where Limelight works out of the box.
     - ``R-DOC-06``; gate 1
     - pending
   * - 13
     - **The developer tree and the release documents.** ``releasing.rst``
       and the release checklist; the generated parameter reference built in
       CI; the Read the Docs configuration built as it says; the link check.
     - ``R-DOC-04``, ``R-DOC-05``; ``AC-DOC-01``; gate 1
     - pending
   * - 14
     - **The licence audit**, every bundled and transitive component, managed
       tool and data file with source, licence and attribution; the source
       offer GPL-3.0 requires; the sign-off line empty.
     - ``AC-REL-03``; gate 4, 5; ``R-SEC-01``
     - pending
   * - 15
     - **The six UX activities prepared**: protocol, task script, checklist,
       recording template and triage route for each; an agent's own heuristic
       and keyboard review, labelled as that, with its defects filed.
     - ``AC-UX-01``..``06``; gate 6
     - pending
   * - 16
     - **Traceability and the phase's gate harness.** The six "planned,
       phase 15" entries and Phase 14's two re-homed or recorded unmet; a
       lean control for this phase's gate registered additively;
       ``testing.rst`` brought up to date.
     - ``R-DOC-01``..``03``; ``AC-DOC-02``; gate 2, 7
     - pending

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

Deferred
========

Anything explicitly not done in this phase, with the reason and where it goes.

Blockers escalated
==================

What was escalated to the main orchestrator, when, and the outcome.
