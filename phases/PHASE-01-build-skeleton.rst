================================================
PHASE-01: Repository, Build and Quality Skeleton
================================================

:Phase: 01
:Status: NOT STARTED
:Depends on: 00
:Blocked by decisions: D-008 (repository licence, for the LICENSE file only)
:Delivers: R-DOC-03, R-DOC-05
:Contributes to: R-TEST-02, R-DOC-02
:Proves: Foundations for AC-TST-02, AC-TST-03, AC-TST-04, AC-DOC-01, AC-DOC-02

Purpose
-------
Create the repository the rest of the project is built in, with every quality
gate wired up **before** there is code to hide behind them. A coverage gate
added in phase 15 measures nothing; a coverage gate added now shapes every
phase after it.

In scope
--------

* Multi-module build (Maven recommended for jpackage/JavaFX tooling
  maturity) with the module layout from the specification's package
  structure.
* Pinned JDK and JavaFX versions, resolved from ``tools/``, reproducible on
  a clean checkout.
* Formatting and static analysis, failing the build on violation.
* JUnit Jupiter, JaCoCo with the specification's thresholds (initially
  applied to the modules that exist), ArchUnit with the layering rules, PIT
  configured over the critical packages.
* A Sphinx documentation tree with ``conf.py``, ``.readthedocs.yaml``, and a
  strict build (``-n -W``) wired into the build and CI.
* The traceability report generator (``R-DOC-03``) reading a checked-in
  mapping file, failing on an unimplemented ``R-`` or an untested ``AC-``.
* CI definitions for the pull-request, nightly and release pipelines;
  nightly and release may be stubs that fail loudly rather than silently
  passing.
* Dependency vulnerability scanning and SBOM generation.
* ``handoffs/`` conventions and the phase-agent brief template.

Out of scope
------------

* Any application feature.
* Real tool downloads (phase 05 owns the registry).

Deliverables
------------

* Buildable multi-module project with all modules present but mostly empty.
* ``docs/`` tree building strictly, including
  ``developer/traceability.rst``.
* CI workflow files for the three pipelines.
* ``CONTRIBUTING.rst`` covering the commit, gate and handoff conventions.
* A ``LICENSE`` file if ``D-008`` is decided; otherwise a recorded
  placeholder and a note.

Exit gate
---------

Every item is verified by the orchestrator, independently of the phase
agent's report. An item that cannot be verified has not passed.

1. A clean checkout builds and tests green with one documented command,
   using only project-local tools.
2. The strict documentation build passes and fails when a deliberate broken
   cross-reference is introduced.
3. ArchUnit fails when a deliberate layering violation is introduced, and
   passes when it is removed.
4. The coverage gate fails when a deliberately untested class is added to a
   gated package.
5. The traceability report fails when an ``AC-`` is given no test
   reference.
6. CI runs the pull-request pipeline on a pull request and its failure
   modes are demonstrated, not assumed.

Risks and notes
---------------

* Wiring PIT and JaCoCo over JavaFX modules can be slow or awkward; scope
  them to the headless critical packages from the start rather than
  retrofitting exclusions later.

Handoff
-------

Before finishing -- whether the phase passed, stalled or was abandoned --
write ``handoffs/PHASE-01-handoff.rst`` covering: what was built and where;
which gate items pass and the evidence for each; what is incomplete and why;
decisions encountered; surprises a later phase must know about; and the
first thing the next agent should do.
