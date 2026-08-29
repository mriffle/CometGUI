===========================================================
PHASE-01 work log -- Repository, Build and Quality Skeleton
===========================================================

:Phase: 01
:Phase orchestrator: Phase-01 orchestrator subagent (session 02)
:Started: 2026-08-29

Maintained by the phase orchestrator as the phase runs. A unit is not done
until it carries a sign-off entry naming what was run and what was observed --
"agent reported success" is not a sign-off.

.. contents:: Contents
   :depth: 1
   :local:

Decomposition
=============

Eight work units. The ordering below is a dependency order, not a preference:
units in the same wave touch disjoint files and are run in parallel; units in
later waves are started only after every unit they depend on is signed off.

.. list-table:: Waves and file ownership
   :header-rows: 1
   :widths: 8 12 40 40

   * - Wave
     - Units
     - Runs in parallel because
     - Files owned

   * - A
     - 1, 4, 5
     - Disjoint file sets: build tree, documentation tree, legal/process
       documents.
     - U1 ``pom.xml``, ``cometgui-*/``, ``scripts/build.sh``, ``.mvn/``,
       ``.gitignore``; U4 ``docs/``, ``.readthedocs.yaml``,
       ``scripts/ci/docs-build.sh``; U5 ``LICENSE``, ``CONTRIBUTING.rst``,
       ``handoffs/BRIEF-TEMPLATE.rst``.

   * - B
     - 2, then 3
     - Both edit the parent ``pom.xml``; serialised. Unit 6 runs alongside
       them (different files).
     - U2 ``config/`` quality configuration and the quality plugin block;
       U3 the testing plugin block, ``cometgui-archtests/``, module test
       sources.

   * - B
     - 6
     - Touches only the traceability generator, its map and the ``conf.py``
       hook; no overlap with 2 or 3.
     - ``scripts/traceability/``, ``docs/traceability-map.toml``,
       ``docs/conf.py`` (hook only), ``docs/developer/traceability.rst``.

   * - C
     - 7
     - Needs a working build, a working docs build and a working
       traceability report to wrap in CI.
     - ``.github/workflows/``, ``scripts/ci/`` (except ``docs-build.sh``),
       SBOM and dependency-scan plugin/config.

   * - D
     - 8
     - Needs every gate to exist before it can prove each one fails.
     - ``scripts/ci/negative-controls.sh``, ``docs/developer/testing.rst``.

Work units
==========

.. list-table::
   :header-rows: 1
   :widths: 4 30 14 52

   * - #
     - Unit and acceptance conditions
     - Rules served
     - Sign-off: what was run, what was seen, date

   * - 1
     - **Maven multi-module skeleton and the one documented command.**
       Parent ``pom.xml`` plus the eleven product modules and
       ``cometgui-archtests``, package directories matching the
       specification's package structure, one small genuinely-tested class per
       gated module, ``.mvn/maven.config`` forcing
       ``maven.repo.local=_build/m2repo``, and ``scripts/build.sh`` which
       bootstraps ``tools/`` and ``.venv`` and then runs the whole local gate.
       Every plugin version is chosen by *running* it on JDK 25, not by
       assumption. Acceptance: ``bash scripts/build.sh`` green from a clean
       ``_build/``; no write to ``~/.m2``; ``mvn -v`` reports the pinned Maven
       and JDK.
     - Gate 1; foundation for all others
     - *pending -- not started*

   * - 2
     - **Formatting and static analysis that fail the build.** Spotless
       (format + licence header + import order), Checkstyle and SpotBugs
       wired into ``mvn verify`` with configuration under ``config/``.
       Acceptance: each of the three fails the build on a deliberate
       violation and passes when it is removed, demonstrated by the agent and
       re-run by the orchestrator.
     - Gate 1, gate 6 (PR pipeline steps)
     - *pending -- not started*

   * - 3
     - **Test, coverage, architecture and mutation gates.** JUnit Jupiter;
       JaCoCo with the specification's thresholds on the gated modules;
       ArchUnit rules from the specification's *Architecture tests* section in
       ``cometgui-archtests``; PIT over the critical packages with the
       ``R-TEST-02`` 80% threshold; and the headless JavaFX test recipe
       (injected Monocle plus the project-local font stack) proven inside the
       real build. Acceptance: ``mvn verify`` runs all of them; each fails on
       its deliberate defect.
     - ``R-TEST-02``; gates 3, 4; ``AC-TST-02``, ``AC-TST-03``, ``AC-TST-04``
     - *pending -- not started*

   * - 4
     - **The real Sphinx tree.** ``docs/conf.py``, the page set from the
       specification's recommended documentation tree as honest stubs,
       ``.readthedocs.yaml``, the integration decision for
       ``docs/feasibility/``, and ``scripts/ci/docs-build.sh`` running both
       ``sphinx-build -n -W -b html docs docs/_build/html`` and a strict check
       over the repository-root, ``phases/`` and ``handoffs/`` documents.
       Acceptance: both builds clean; a deliberate broken cross-reference
       fails the build.
     - ``R-DOC-05``; gate 2; ``AC-DOC-01``
     - *pending -- not started*

   * - 5
     - **Legal and process documents.** ``LICENSE`` -- the full unmodified
       GPL-3.0 text, fetched and verified against CasanovoGUI's own blob
       (35 149 bytes, git blob sha ``f288702d``) per ``D-001``;
       ``CONTRIBUTING.rst`` covering the commit, gate and handoff conventions;
       ``handoffs/BRIEF-TEMPLATE.rst``, the phase-agent brief template.
       Acceptance: the licence text is byte-identical to the canonical GPL-3.0
       and the check is scripted, not asserted.
     - ``D-001`` obligation 1; phase deliverables
     - **ACCEPTED 2026-08-29**, commit ``d182895``. I did not read the agent's
       claim about the licence text -- I fetched both sources myself.
       ``curl https://www.gnu.org/licenses/gpl-3.0.txt`` and ``curl
       https://raw.githubusercontent.com/Noble-Lab/CasanovoGUI/main/LICENSE``
       are byte-identical to the committed ``LICENSE`` (``cmp`` silent both
       times), sha256 ``3972dc97...``, 35 149 bytes, 674 lines, and ``git
       hash-object LICENSE`` prints
       ``f288702d2fa16d3cdf0035b15a9fcbc552cd88e7`` -- the exact blob sha
       ``DECISIONS.rst`` records for CasanovoGUI. ``bash
       scripts/verify-license.sh`` printed 17 PASS lines and exit 0; I then
       built four damaged copies under ``_build/orch-check/`` myself and it
       exited 1 on each with the right diagnosis (truncated: "TRUNCATED by
       30196 bytes"; altered title: "the text has been ALTERED" plus the blob
       mismatch; CRLF: "GROWN by 674 bytes"; missing: "LICENSE is a D-001
       obligation"). ``check-docs.sh`` builds ``CONTRIBUTING.rst``,
       ``handoffs/BRIEF-TEMPLATE.rst`` and ``README.rst`` clean under
       ``-n -W``. I read ``CONTRIBUTING.rst`` (393 lines) against
       ``ONBOARDING.rst`` and found no contradiction; it points rather than
       restates, and states the no-remote rule with the ``D-008`` reason. Diff
       is five files, all owned by the unit. **Follow-up commit ``6d12561``
       re-verified**: it adds a ``--self-test`` mode that damages copies under
       ``_build/`` and requires each to be rejected; I ran ``bash
       scripts/verify-license.sh --self-test`` myself and saw ``SELF-TEST
       PASSED -- 5 negative controls rejected, real LICENSE accepted``, exit
       0. The agent also reported catching a real defect in its own harness
       through a sabotage control (the self-test depended on the executable
       bit and every control silently returned 126), which is the harness
       being falsifiable rather than merely green.

   * - 6
     - **Traceability report generator.** Reads the phase documents for
       ``R-`` ownership and a checked-in map for ``AC-`` evidence; expands the
       ``nn..nn`` range notation the phase documents use; verifies that every
       named test actually exists in the tree; fails on an ``R-`` with no
       owning phase, an ``R-`` owned twice, an ``AC-`` with no test and no
       human-sign-off mark, and a named test that does not exist. Generates
       ``docs/developer/traceability.rst`` during the Sphinx build so an
       incomplete map is a documentation build failure. Stdlib Python only.
       Acceptance: the four failure modes are demonstrated; the generated page
       builds under ``-n -W``.
     - ``R-DOC-03``, ``R-DOC-02``; gate 5; ``AC-DOC-02``
     - *pending -- not started*

   * - 7
     - **CI definitions, SBOM and dependency scanning.** Three workflow files
       (pull-request, nightly, release) whose every step is a
       ``scripts/ci/*.sh`` script that can be run locally; CycloneDX SBOM
       generation with content validation; an OSV-based dependency
       vulnerability scan that fails loudly when the network is unavailable
       rather than passing silently; and a checker proving the workflow files
       and the step scripts cannot drift apart. Nightly and release steps that
       belong to later phases exit non-zero saying so. Acceptance: every PR
       step runs locally and passes; the nightly and release stubs fail
       loudly; the SBOM lists real components; the scanner reports a known CVE
       on a deliberately vulnerable fixture.
     - ``AC-REL-01``; gate 6 (the half that can be met without a remote)
     - *pending -- not started*

   * - 8
     - **The falsifiability harness.** One re-runnable script that, for each
       quality gate, injects the defect that gate exists to catch into a
       sandbox copy of the tree, runs the narrowest command that should catch
       it, and requires both a non-zero exit and the expected diagnostic;
       then shows the same command passing with the defect removed. Covers
       gate items 2, 3, 4 and 5 and the pull-request pipeline's failure modes
       for gate item 6. Acceptance: every control passes, and the harness
       itself is falsifiable -- a control whose defect is not injected must be
       reported as a harness failure, not a pass.
     - Gates 2, 3, 4, 5, 6
     - *pending -- not started*

Decisions taken by the phase orchestrator
=========================================

Judgement calls that were mine to make, recorded here so a later phase can see
they were deliberate. None of them answers a ``D-`` item.

#. **``docs/feasibility/`` is integrated into the real Sphinx tree**, not
   excluded from it. Under ``-n -W`` a document in the source tree that is in
   no toctree is a build failure, so the alternative was to exclude Phase 00's
   evidence from the only strict build the project runs. It is reachable from
   the developer index as an evidence appendix.

#. **``scripts/feasibility/check-docs.sh`` is kept, not retired.** It builds a
   throwaway tree that shares no configuration with ``docs/conf.py``, so it
   stays useful as an independent cross-check if the real configuration ever
   develops a fault that hides warnings. It is no longer the gate; the gate is
   ``scripts/ci/docs-build.sh``.

#. **The strict documentation gate covers the whole repository, not only
   ``docs/``.** ``ONBOARDING.rst``, ``STATUS.rst``, ``DECISIONS.rst``,
   ``specification.rst``, ``README.rst``, ``CONTRIBUTING.rst``, ``phases/``
   and ``handoffs/`` are project documentation and the hard rule applies to
   them, but they are not part of the published user documentation and must
   not appear in its toctree. They get a second strict build over a generated
   throwaway tree.

#. **Dependency scanning uses OSV rather than OWASP dependency-check.**
   dependency-check needs an NVD API key to build its database in reasonable
   time and the project has no credentials; without one it is hours per run.
   ``api.osv.dev`` needs no key, answers Maven coordinates, and lets the
   scanner be proved falsifiable against a known-vulnerable fixture.

#. **Skeleton classes exist so the gates are not vacuous.** A coverage gate
   over zero classes measures nothing and a mutation gate over zero classes
   cannot run. Each gated module therefore carries one small class with real
   branching and real tests. They are scaffolding, they are marked as such,
   and later phases should replace rather than extend them.

Rejections and rework
=====================

Units sent back, why, and what changed. Nothing here yet; entries are added
when they happen, never in advance.

Deferred
========

.. list-table::
   :header-rows: 1
   :widths: 30 70

   * - Item
     - Reason and where it goes

   * - The pull-request pipeline running **on an actual pull request**
     - There is no git remote and ``D-008`` withholds one; creating one is an
       owner decision. Every step of that pipeline is proven locally instead.
       Gate item 6 is therefore half met and the phase signs off ``PARTIAL``.
       Closing it needs the publication half of ``D-008``.

   * - Read the Docs actually building the tree
     - The plan is to write ``.readthedocs.yaml`` and check it locally as far
       as is possible without the service; no Read the Docs project exists and
       creating one is part of the same open decision. ``AC-DOC-01``'s Read
       the Docs half belongs to Phase 16.

   * - Coverage thresholds for adapter, workflow, install and UI modules
     - The specification gates core domain, parameter and provenance logic
       numerically and says adapters are covered by real integration tests
       rather than artificial line counts. The intent is that those rules are
       written and inert until the modules have classes; the phase that fills a
       module owns turning its threshold on.

   * - Windows and macOS CI runners
     - Same blocker as gate item 6. The workflow files name the runners and
       the matrix so that adding a remote turns them on rather than requiring
       them to be written.

Blockers escalated
==================

.. list-table::
   :header-rows: 1
   :widths: 20 80

   * - When
     - What, and the outcome

   * - Before the phase started
     - Gate item 6 cannot be met without a git remote. Escalated by the main
       orchestrator to the owner on 2026-08-29 **before** this phase was
       dispatched; the owner directed that the phase run anyway, prove every
       pipeline step locally, and record the "on a pull request" half unmet.
       No further escalation was needed during the phase.
