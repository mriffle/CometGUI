==============
Project Status
==============

:Project: CometGUI -- Comet + Percolator desktop workflow
:Updated: 2026-08-28
:Updated by: Planning session (specification review and phase organisation)
:Current phase: 00 -- not started
:Overall: Planning complete. No product code exists.

This file is the **only** authoritative record of where the project is. Update
it at every gate, every decision and every milestone. If it disagrees with
anything else, fix it here first.

Where we are
============

The project has a reviewed specification, a verified set of upstream facts, a
phase plan and an open decision list. Nothing has been implemented. The next
action is to run **Phase 00**.

What exists
-----------

.. list-table::
   :header-rows: 1
   :widths: 30 14 56

   * - Artefact
     - State
     - Notes
   * - ``specification.rst``
     - Revision 2
     - Reviewed, corrected against live upstream sources, converted to valid
       RST; passes ``sphinx-build -n -W``.
   * - ``ONBOARDING.rst``
     - Complete
     - Read-first document for any orchestrating agent.
   * - ``phases/`` (00-16)
     - Complete
     - Scope, deliverables and exit gate per phase. None started.
   * - ``DECISIONS.rst``
     - 8 open
     - ``D-001``..``D-008``; none decided.
   * - ``handoffs/``
     - Empty
     - One file per phase, written by the phase agent.
   * - Product code
     - None
     - No build, no modules, no toolchain installed yet.

Phase board
===========

.. list-table::
   :header-rows: 1
   :widths: 8 40 16 36

   * - Phase
     - Title
     - Status
     - Gate evidence
   * - 00
     - Feasibility, legal and upstream verification
     - NOT STARTED
     - --
   * - 01
     - Repository, build and quality skeleton
     - NOT STARTED
     - --
   * - 02
     - Application shell and navigation
     - NOT STARTED
     - --
   * - 03
     - Process service
     - NOT STARTED
     - --
   * - 04
     - Hashing and provenance core
     - NOT STARTED
     - --
   * - 05
     - Tool registry and installer
     - NOT STARTED
     - --
   * - 06
     - Comet parameter model
     - NOT STARTED
     - --
   * - 07
     - Comet parameter editor UI
     - NOT STARTED
     - --
   * - 08
     - Workflow engine and Comet adapter
     - NOT STARTED
     - --
   * - 09
     - Percolator adapter and version capabilities
     - NOT STARTED
     - --
   * - 10
     - Results model and UI
     - NOT STARTED
     - --
   * - 11
     - PDV integration
     - NOT STARTED
     - --
   * - 12
     - Limelight conversion and upload
     - NOT STARTED
     - --
   * - 13
     - Provenance UI and reports
     - NOT STARTED
     - --
   * - 14
     - GUI automation and packaged end-to-end harness
     - NOT STARTED
     - --
   * - 15
     - Version matrix, performance and hardening
     - NOT STARTED
     - --
   * - 16
     - Documentation and release qualification
     - NOT STARTED
     - --

Status values are ``NOT STARTED``, ``IN PROGRESS``, ``PARTIAL`` (with the
residue named), ``BLOCKED`` (with the decision or dependency named), or
``PASSED`` (with the date and the evidence the orchestrator verified).

Open decisions
==============

All eight are open. ``D-001`` and ``D-002`` are the two that shape the product
rather than merely configure it.

.. list-table::
   :header-rows: 1
   :widths: 12 46 42

   * - ID
     - Question
     - Blocks
   * - ``D-001``
     - May CasanovoGUI source be reused? (No licence published.)
     - Derivation in 02; redistribution in 16
   * - ``D-002``
     - How is an XML-capable Percolator obtained per platform?
     - 05, 09, 12; the Limelight promise
   * - ``D-003``
     - Which Percolator version/platform pairs are managed?
     - 05, 09, 15
   * - ``D-004``
     - macOS architecture policy for the Percolator stage
     - 05, 16
   * - ``D-005``
     - PDV baseline only, or enhanced control mode?
     - 11
   * - ``D-006``
     - Which fixture data, under which licence?
     - 00, 14, 16
   * - ``D-007``
     - Which Limelight endpoint do tests target?
     - 12, 14
   * - ``D-008``
     - CometGUI's own licence and distribution
     - 01 (LICENSE file), 16

Risks currently live
====================

#. **The latest compatible Percolator has no published binary anywhere.**
   Verified 2026-08-29. The product's policy is to run the latest compatible
   version; with Limelight enabled that resolves to **3.08.1**
   (``rel-3-08-01``), the newest release that can emit the XML the converter
   requires -- and it is a tag with no GitHub release. The newest *published*
   XML-capable artefact is 3.08.0's Linux ``.deb``, which needs glibc 2.38 and
   carries a PEP-greater-than-1.0 defect that 3.08.1 fixes. Every portable
   upstream archive is ``noxml``, XML is an opt-in compile flag, and Bioconda's
   builds are XML-free and skip macOS. Using the latest compatible version
   therefore means **building Percolator from source with
   ``-DXML_SUPPORT=ON``** (``D-002``, recommendation: option 1). Everything
   downstream -- the manifest, the release matrix, the UI's promises, the test
   matrix -- depends on that decision.
#. **CasanovoGUI has no licence.** Verified 2026-08-28. The architecture is
   implementable independently, so this blocks derivation, not the project;
   but it must be resolved before public redistribution.
#. **Upstream drift.** PDV moved 2.6.0 -> 2.7.0 between the first and second
   drafts of the specification on the same day. Phase 00 re-verifies
   everything; phase 15 adds the CI job that catches it thereafter.
#. **No toolchain on this machine.** No JDK, Maven, Gradle or Docker. Phase 00
   installs them project-locally under ``tools/``; nothing goes on the host.

Next action
===========

Run **Phase 00** (``phases/PHASE-00-feasibility.rst``). It writes no product
code. Its most valuable output is a costed recommendation for ``D-002``,
because that decision determines the manifest, the release pipeline, the test
matrix and what the user interface is allowed to promise.

Change log
==========

.. list-table::
   :header-rows: 1
   :widths: 14 20 66

   * - Date
     - Phase
     - Entry
   * - 2026-08-28
     - --
     - Specification received (revision 1) and committed as the baseline.
   * - 2026-08-28
     - --
     - Deep review completed. Upstream facts verified against live sources;
       Percolator artefact finding recorded; specification revised to revision
       2 and converted to valid RST.
   * - 2026-08-28
     - --
     - Phases 00-16 defined with exit gates; ``ONBOARDING.rst``,
       ``STATUS.rst`` and ``DECISIONS.rst`` created. Project not yet started.
   * - 2026-08-29
     - --
     - Owner directed that the product use the **latest compatible**
       Percolator rather than a pinned 3.08. Verified that this resolves to
       3.08.1 (``rel-3-08-01``), which has no published binary on any platform;
       that XML is an opt-in compile flag removed in 3.09; that the Limelight
       converter has no non-XML path; and that Bioconda cannot supply an
       XML-capable build. Specification revision 3 replaces pinned defaults
       with computed resolution (``R-PERC-02``, ``R-PERC-10``) and records the
       converter's verified interface (``R-LL-05``). ``D-002``'s recommendation
       changes to building from source.
