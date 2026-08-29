=======================================================================
PHASE-00 work log -- Feasibility, Legal and Upstream Verification
=======================================================================

:Phase: 00
:Phase orchestrator: Phase-00 orchestrator subagent (session 2026-08-29)
:Started: 2026-08-29

Maintained by the phase orchestrator as the phase runs. One row per work unit.
A unit is not done until it carries a sign-off entry naming what was run and
what was observed -- "agent reported success" is not a sign-off.

Decomposition rationale
=======================

Phase 00 is evidence work, not code, so the units are cut along *evidence
boundaries*: each unit owns one investigation, one script family and one
output document, so that two units never write the same file and can therefore
run concurrently. Six units (1-6) have no dependencies on each other and were
launched together; three (7-9) consume artefacts that earlier units produce and
were launched only after those units were signed off.

Ephemeral inputs, per the main orchestrator's ruling on ``D-006``, live under
``/workspace/scratch/`` (added to ``.gitignore`` at the start of the phase) and
are **feasibility inputs, not the project's chosen fixture**.

Work units
==========

.. list-table::
   :header-rows: 1
   :widths: 5 30 17 48

   * - #
     - Unit and acceptance conditions
     - Gate items / rules served
     - Sign-off: what was run, what was seen, date

   * - 1
     - **Documentation build harness.** Project virtualenv at ``.venv`` with
       Sphinx; throwaway Sphinx config under ``_build/`` (Phase 01 owns the
       real ``docs/conf.py``, which must NOT be created); re-runnable
       ``scripts/feasibility/check-docs.sh`` that builds
       ``docs/feasibility/*.rst`` with ``sphinx-build -n -W``. Accept when the
       script builds a deliberately broken RST to a failure and a clean one to
       success -- exit code 0 alone is not accepted as proof.
     - Enables the RST cleanliness requirement for every deliverable
       (``ONBOARDING`` Documentation rules)
     - Signed off 2026-08-29 by the phase orchestrator. Ran
       ``bash scripts/feasibility/check-docs.sh`` myself: exit 0, "build
       succeeded.", 4 HTML pages on disk including a 12091-byte
       ``_build/docs-check/html/feasibility/index.html``. Then wrote my OWN
       broken RST (short title underline + ``:py:func:`` to a nonexistent
       target + a ``list-table`` whose ``:widths:`` has 2 entries for 3
       columns) and ran the harness on it: **exit 1**, with
       ``ERROR: "list-table" widths do not match the number of columns in
       table (3).``, ``WARNING: py:func reference target not found`` and
       ``build finished with problems, 7 warnings (with warnings treated as
       errors).`` The gate really bites. Read commit ``42feb8c`` in full (268
       lines, 2 files, nothing else swept in). ``docs/conf.py`` confirmed
       absent. The agent declined to use a ``:glob:`` toctree because an empty
       glob is itself a ``-W`` error and the alternative was
       ``suppress_warnings``, which would have weakened the gate -- correct
       call, accepted.

   * - 2
     - **Upstream fact re-verification, live, today.** Re-check every row of
       ``specification.rst``'s Verified Upstream Facts table against live
       sources on 2026-08-29, recording URL, method, date and finding per row.
       Produces ``scripts/feasibility/verify_upstream_facts.py`` (re-runnable,
       emits JSON + report) and ``docs/feasibility/upstream-facts.rst``, which
       must carry an explicit "Differences from specification.rst" section.
       Accept when every row has a fresh evidence line and no row is copied
       from the specification.
     - Gate 1; feeds gate 7 and 10
     - PENDING

   * - 3
     - **Percolator artefact enumeration and payload extraction (Linux,
       macOS).** Enumerate every Percolator release and its assets from the
       upstream API; derive *latest compatible* from that data rather than
       from the specification; extract the 3.07.1 ``.deb`` (``ar`` +
       ``data.tar.gz``) and ``.pkg`` (``xar!`` + gzip + ``070707`` cpio)
       payloads without root and in pure Python (no ``cpio``/``xar``/``7z`` on
       this host); execute the Linux binary and capture ``-X``/``-Z``; locate
       the XSD companions. Produces
       ``docs/feasibility/percolator-artefacts.rst``.
     - Gate 6, 7, 9 (``.deb`` and ``.pkg``); ``R-PERC-01``, ``R-PERC-02``,
       ``R-TOOL-02``; ``D-002``/``D-003`` evidence
     - PENDING

   * - 4
     - **Windows NSIS artefact: strongest obtainable evidence.** Decompress
       ``percolator-v3-07.exe``'s NSIS payload on Linux in pure Python, with
       nothing installed on the host, and inspect the extracted
       ``percolator.exe``. Must be scrupulous: extraction plus strings is
       *stronger inference*, NOT execution. Explicitly forbidden from writing
       any statement that Windows XML capability is verified. Produces
       ``scripts/feasibility/extract_nsis.py`` and
       ``docs/feasibility/windows-artefact.rst`` including the cost of a
       Windows runner.
     - Gate 8, 9 (NSIS)
     - PENDING

   * - 5
     - **Project-local toolchain and jpackage.** JDK and build tool under
       ``tools/<name>-<version>/``; nothing on the host, no ``apt``, no
       ``sudo``, no host pip. Prove ``jpackage`` produces a *launchable*
       Linux bundle -- launched and its output checked, not merely produced.
       Produces ``scripts/feasibility/install-toolchain.sh`` (recreates
       ``tools/`` from scratch, since ``tools/`` is gitignored) and
       ``docs/feasibility/toolchain.rst`` carrying the provenance manifest
       (URL, version, date, SHA-256, licence per tool) and the Windows/macOS
       runner requirements.
     - Gate 4; ``R-PLAT-02``, ``R-PLAT-03``
     - PENDING

   * - 6
     - **Fixture candidates and ephemeral proof input.** Costed shortlist of
       candidate fixture datasets with licences for ``D-006`` (choosing one is
       NOT in scope), plus acquisition of one small openly licensed spectrum
       file and FASTA into ``scratch/`` as an ephemeral feasibility input.
       Produces ``docs/feasibility/fixture-candidates.rst`` and
       ``scripts/feasibility/fetch_ephemeral_input.py``. Accept when the
       document states plainly that the ephemeral input is not the project's
       fixture.
     - ``D-006`` deliverable; unblocks gate 2 and 3
     - PENDING

   * - 7
     - **JavaFX startup smoke and GUI automation spike.** JavaFX startup on
       the pinned JDK/JavaFX pair, headless and (if obtainable without
       touching the host) headed; TestFX spiked against the same pair with a
       written verdict and a named fallback. Produces
       ``docs/feasibility/gui-automation-spike.rst``. An unobtainable headed
       run is recorded as unverified, not as a pass. *Depends on unit 5.*
     - Gate 5
     - PENDING

   * - 8
     - **Scientific path end to end, no GUI.** Comet 2026.02.2 -> pepXML +
       PIN -> Percolator 3.07.1 with ``-X``/``-Z`` -> PSM, peptide and weights
       plus Percolator XML -> Limelight converter -> Limelight XML, validated
       as XML and non-trivial; plus a Percolator 3.09 run proving rescoring
       works and demonstrably produces no XML. Produces
       ``scripts/feasibility/run_scientific_path.sh`` and
       ``docs/feasibility/scientific-path.rst``. *Depends on units 3, 5, 6.*
     - Gate 2, 3; ``R-PERC-01``, ``R-LL-05``
     - PENDING

   * - 9
     - **PDV CLI and converter interface capture.** Run the pinned Limelight
       converter JAR's ``--help`` and record its real argument names against
       the specification's table; download PDV 2.7.0 and prove CLI figure
       generation on the Comet pepXML plus spectrum file from unit 8.
       Produces ``docs/feasibility/pdv-converter-spike.rst``. *Depends on
       units 5 and 8.*
     - In-scope bullets "Limelight converter JAR help" and "PDV CLI figure
       generation"; ``D-005`` evidence
     - PENDING

Rejections and rework
=====================

None yet.

Deferred
========

Nothing yet.

Blockers escalated
==================

Nothing yet.
