=================================================
PHASE-16: Documentation and Release Qualification
=================================================

:Phase: 16
:Status: IN PROGRESS (dispatched 2026-11-12)
:Depends on: 15
:Blocked by decisions: none open. D-009 must be settled before release.
   D-001, D-004, D-006, D-008, D-018 and D-019 are DECIDED
:Delivers: R-DOC-01, R-DOC-02, R-DOC-06
:Contributes to: R-DOC-03, R-DOC-04, R-DOC-05, R-SEC-01
:Proves: AC-DOC-01, AC-DOC-02, AC-REL-02, AC-REL-03, AC-UX-01..06, AC-TST-07

.. important::

   **The packaged launcher must start the JVM in a UTF-8 locale.** Without an
   exported ``LANG``/``LC_ALL``, ``sun.jnu.encoding`` is ``ANSI_X3.4-1968`` and
   ``Path.of`` throws ``InvalidPathException`` on any non-ASCII path, before a
   line of CometGUI runs. **Do not "fix" this with
   ``-Dsun.jnu.encoding=UTF-8``**: it is resolved from the process environment
   before system properties are applied, so the flag is accepted without error
   and does nothing -- measured, not assumed. It must be the environment, which
   ``jpackage --java-options`` cannot express. Carry this together with the
   ``--enable-native-access=ALL-UNNAMED`` java-option Phase 02 left for this
   phase. See ``STATUS.rst``, *Risks currently live*, item 1.

Purpose
-------
Everything that stands between a working application and a release someone else
can install, trust and cite -- including the human activities no agent can
discharge.

In scope
--------

* The complete user documentation tree, including the platform-support and
  troubleshooting pages that state plainly where the Limelight path works
  out of the box.
* The complete developer documentation tree, including traceability.
* The generated parameter reference, built in CI.
* Read the Docs configuration, building successfully.
* Release packaging for every tier-1 platform, with checksums, SBOM, and
  signing or notarisation where infrastructure permits.
* Clean-machine acceptance on each tier-1 platform.
* Licence audit of every bundled and transitive component, including any
  project-built tool binaries.
* Scheduling and recording the six human UX-validation activities and the
  defects they produce.
* **The CI fixture** (added by the owner, 2026-11-12): the K562 spectra
  fetched into the CI jobs that wait for them, and a FASTA trimmed to suit
  those spectra in place of the whole human proteome. See
  :ref:`phase16-ci-fixture`.
* The two decisions the owner took on 2026-11-12 and Phase 15 left
  unimplemented: ``D-016`` option B and ``D-017`` option A
  (``DECISIONS.rst``).

.. _phase16-ci-fixture:

The CI fixture
--------------

Two owner decisions of 2026-11-12 (``DECISIONS.rst``, ``D-006``).

**The spectra** are the two K562 mzML files of the local fixture, unchanged
and unfiltered: *"yes, use the K562 pair as the CI fixture."*

**The FASTA** is not the whole proteome. The owner: *"We shouldn't use a
FASTA for the whole proteome, it's unnecessarily large. Add to phase 16 a
step to get the FASTA and suitably filter it for our data, so that our CI
will still run successfully, but not with an overly large FASTA."*

The step, in order:

#. **Measure what the change touches before making it.** The reviewed
   goldens are keyed by the fixture's digest, so a different FASTA
   invalidates every one of them; the performance baseline and any test that
   pins a count from the present fixture move too. List them first.
#. **Get the source FASTA** -- the UniProt human reference proteome the local
   fixture already uses -- by checksum, recording the UniProt release.
#. **Search the K562 pair against it** and take the proteins behind the
   confident identifications.
#. **Keep those proteins and a background of others.** A FASTA holding only
   the identified proteins is over-filtered: Comet's decoys are made from the
   FASTA, so almost every spectrum would match a true target, Percolator
   would have too few decoys and poor matches to train on, and a result set
   in which everything passes 1% proves nothing about the filters. The
   background is chosen by a recorded, deterministic rule and sized by
   measurement, not by guess.
#. **Accept the trimmed FASTA only on a search of it.** The K562 pair is run
   against it through the product's own workflow, for every Comet and
   Percolator pair the version matrix offers. Percolator must train and
   write its results; confident PSMs and peptides at 1% must clear a floor;
   decoy PSMs must be present; PSMs must fall on both sides of the 1% line;
   the Limelight conversion must still validate. The phase proposes the
   floors from what it measures and tier 1 confirms them. **The check is
   shown red on a deliberately over-filtered FASTA** -- identified proteins
   only -- before it is trusted.
#. **Make it reproducible.** The selection is a committed script and a
   committed list of accessions, with the UniProt release it was made from.
#. **Use one fixture, not two.** Local testing and CI share the trimmed
   FASTA, so there is one set of goldens. They are regenerated once, each
   with its review record (``R-TEST-07``); a golden is never regenerated to
   make a red run green.
#. **Wire the fetch into the CI jobs** that name ``D-006`` today, under every
   condition ``D-006`` already sets for the spectra: fetched by checksum,
   never committed, the CRLF defence applied on the runner.

**The trimmed FASTA is committed to the repository** (``D-018``, option A,
decided 2026-11-12), with UniProt's attribution (CC BY 4.0) and the release
it was made from, and it is entered in the licence audit. **The two K562
mzML files are not committed**: the owner allows small test files in the
repository only where no licence is violated, and the terms these files are
redistributed under have never been established (``D-006``, amendment to
part 1). They stay fetched by checksum.

.. _phase16-raw-fixture:

The Thermo RAW fixture
----------------------

Added 2026-11-12, by the owner's plan in ``D-006``: a search made before
this phase found Thermo RAW files under 10 MB that give real
identifications. The owner chose ``ph050213_18.raw`` from PRIDE project
``PXD002914`` (``D-019``, option A); its checksum, the fallback file and
what was measured are recorded there, and the search report is
``docs/feasibility/small-thermo-raw-files.rst``.

What the search measured is **not** what this phase must show. Comet reads
RAW only on Windows, so each candidate was converted to mzML on Linux and
that was searched. Nothing yet shows that Comet on Windows opens any of
these files. The steps, in order:

#. **Try it on a Windows runner before building anything.** Comet for
   Windows, with its Thermo companions, reads the chosen RAW file directly
   and the search is carried through Percolator. The counts are compared
   with those in ``D-019``. Known risks to look at: empty MS1 scans, MS2
   scans that carry no precursor charge, and an old RAW format version in
   one candidate set.
#. **If it reads**, the Windows RAW smoke test replaces its stub: the file
   is fetched by checksum on the runner, searched through the product
   against the FASTA of its organism, and held to a reviewed golden taken
   from a runner (``R-TEST-07``). These are low-resolution ion-trap spectra,
   so the search needs its own parameter set, recorded with the fixture; the
   seven values the search used are in ``D-019``.
#. **If it does not read**, the next candidate in ``D-019`` is tried. If
   none reads, the RAW job is taken out of the nightly and ``AC-TST-11`` is
   recorded as not met and outstanding, as the owner directed. It is never
   made to exit 0 having tested nothing.

**The RAW file is fetched, not committed.** Its archive states its licence
as *EBI terms of use*, which is not a grant this project can read as
permission to redistribute; the owner's condition for keeping a test file in
the repository is therefore not met (``D-006``, amendment to part 1). The
FASTA for its organism is UniProt's (CC BY 4.0) and is committed with
attribution, whole if it is already small.

**No RAW converter enters the project.** The search used ThermoRawFileParser
in a scratch directory to judge candidates. It ships Thermo's RawFileReader
licence, which forbids combining the reader with GPL software, and CometGUI
is GPL-3.0. The product and its tests reach RAW only through Comet.

Out of scope
------------

* Shipping while any AC- criterion is unmet.

Deliverables
------------

* Complete ``docs/`` tree and a published Read the Docs build.
* Release artefacts for each tier-1 platform with checksums and SBOM.
* ``docs/developer/releasing.rst`` and a release checklist.
* Recorded UX session findings, triaged as defects.
* The trimmed FASTA's selection script and accession list, its acceptance
  check, and the goldens regenerated for it with their review records.

Exit gate
---------

The phase orchestrator verifies every item, and the main orchestrator then
re-runs them to sign the phase off. Neither accepts a report in place of
running the check. An item that cannot be verified has not passed.

1. The strict documentation build and the link check both pass, and Read
   the Docs builds the same tree.
2. The traceability report shows every ``R-`` implemented by a phase and
   every ``AC-`` either tested or carrying a recorded human sign-off.
3. A clean machine per tier-1 platform installs only CometGUI and completes
   the Definition of Done workflow.
4. Release artefacts carry published checksums and an SBOM, and the licence
   audit is complete and recorded.
5. ``D-001`` is resolved and recorded before any public redistribution.
6. All six UX-validation activities are complete, with findings triaged.
7. No release-critical test is quarantined and every release gate in the
   pipeline passes.
8. The CI jobs that waited on ``D-006`` search the K562 pair against the
   trimmed FASTA on a runner and pass against reviewed goldens; the trimmed
   FASTA's acceptance check passes and has been seen to fail on an
   over-filtered FASTA; no job fetches the whole proteome
   (:ref:`phase16-ci-fixture`).
9. The Windows RAW smoke test searches the RAW fixture on a Windows runner,
   through the product, and passes against a reviewed golden
   (:ref:`phase16-raw-fixture`). If no candidate can be read and the job is
   shelved as the owner directed, this item is **not met** and is named as
   residue; it is not reworded to pass.

Risks and notes
---------------

* The human-gated criteria are the long pole. Start scheduling them during
  phase 07, not here.

Handoff
-------

The **phase orchestrator** owns both records for this phase.

``handoffs/PHASE-16-worklog.rst`` is written as the phase runs: the work units,
their acceptance conditions, which agent did each, and the sign-off entry for
each -- what was run and what was observed.

``handoffs/PHASE-16-handoff.rst`` is written before finishing, whether the phase
passed, stalled or was abandoned: what was built and where; which gate items
pass and the evidence for each; what is incomplete and why; decisions
encountered; surprises a later phase must know about; and the first thing the
next agent should do.
