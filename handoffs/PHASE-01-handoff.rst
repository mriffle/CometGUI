==============================================================
PHASE-01 handoff -- Repository, Build and Quality Skeleton
==============================================================

:Phase: 01
:Agent finished: 2026-08-29
:Outcome: **PARTIAL**
:Phase orchestrator: Phase-01 orchestrator subagent (session 02)
:Records: ``handoffs/PHASE-01-worklog.rst`` (8 units, each with a sign-off
   entry naming what I ran and what I saw)

In one line: **every exit gate item passes except the "on a pull request" half
of item 6, which needs a git remote that ``D-008`` withholds** -- so the three
CI workflow files are written and every one of their steps is proved by running
it on this machine, and the half that GitHub would have to execute is recorded
unmet rather than claimed.

Read :ref:`p01-surprises` before starting Phase 02. Two entries there will
otherwise cost a later phase a day each.

What was built
==============

A twelve-module Maven build, five quality gates that fail the build, a strict
documentation tree, a traceability report that is a documentation build
failure when it is incomplete, three CI pipelines, an SBOM, a dependency
scanner, and one command that runs all of it.

.. list-table::
   :header-rows: 1
   :widths: 34 66

   * - Where
     - What it is

   * - ``pom.xml``, ``cometgui-*/``
     - ``org.cometgui:cometgui-parent:0.1.0-SNAPSHOT``; eleven product modules
       laid out in the specification's package structure plus
       ``cometgui-archtests``. 53 ``package-info.java`` files make the layout
       real in git; inter-module dependencies declare only the edges the
       layering permits. JavaFX comes from the Liberica JDK -- there is no
       ``org.openjfx`` dependency anywhere.

   * - ``org.cometgui.domain.build.BuildIdentity``
     - The one class with real branching. It exists so the coverage and
       mutation gates measure something; later phases should replace rather
       than extend it. Its 37 tests assert values and messages.

   * - ``scripts/build.sh``
     - **The one documented command.** Eleven stages: toolchain, fontstack,
       python, Maven ``clean verify``, artefacts, format evidence, gates,
       integration, docs, supply chain, workflows. It bootstraps ``tools/``
       and ``.venv`` when absent and verifies its own outputs rather than
       trusting Maven's exit code.

   * - ``.mvn/maven.config``, ``.mvn/jvm.config``
     - Force ``maven.repo.local=_build/m2repo`` for *any* ``mvn`` run from the
       root, and supply the ``jdk.compiler`` exports google-java-format needs.

   * - ``config/``, quality plugins
     - Spotless 3.10.1 (google-java-format 1.36.1, AOSP), Checkstyle 14.0.0
       over a project-owned rule set, SpotBugs 4.10.4 at ``effort=Max``. All
       bound so ``mvn verify`` fails on a violation.

   * - JaCoCo, ArchUnit, PIT
     - JaCoCo 0.8.15 with the specification's thresholds; ArchUnit 1.5.0
       encoding eight rules from the *Architecture tests* section; PIT 1.30.0
       over the critical packages at ``R-TEST-02``'s 80%.

   * - ``docs/``, ``.readthedocs.yaml``
     - The specification's recommended tree in full, as honest stubs naming
       their owning phase; Phase 00's evidence integrated rather than
       excluded; ``scripts/ci/docs-build.sh`` runs ``R-DOC-05``'s exact
       command plus a second strict build over the project documents outside
       ``docs/``.

   * - ``scripts/traceability/``, ``docs/traceability-map.toml``
     - ``R-DOC-03``. Reads the specification for which identifiers exist, the
       phase documents for ``R-`` ownership, ``STATUS.rst``'s phase board for
       whether a "planned" entry is still legitimate, and a checked-in map for
       ``AC-`` evidence. Generated into the Sphinx build by a
       ``builder-inited`` hook, so an incomplete map fails the documentation
       build.

   * - ``.github/workflows/``, ``scripts/ci/``
     - Three pipelines whose every step is a ``scripts/ci/*.sh`` a person can
       run. CycloneDX SBOM with content validation; an OSV dependency scanner;
       a checker proving the workflows and the scripts cannot drift.

   * - ``LICENSE``, ``CONTRIBUTING.rst``, ``handoffs/BRIEF-TEMPLATE.rst``
     - The full unmodified GPL-3.0 text (``D-001``), the contributor
       conventions, and the tier-3 dispatch template.

   * - ``scripts/verify-all-gates.sh``
     - One command that proves every gate above still fails on the defect it
       exists to catch. **9 controls, 123 graded checks, about five minutes.**

Gate items
==========

Every command below was run by me on the final tree, not read from an agent's
report. Run them from ``/workspace``.

.. list-table::
   :header-rows: 1
   :widths: 6 12 82

   * - Item
     - Result
     - Command run, and what it printed

   * - 1
     - PASS
     - ``git clone /workspace /tmp/final-clone`` -- a tree with no ``tools/``
       and no ``.venv`` -- then ``bash scripts/build.sh``: ``11/11 stages OK
       in 133 seconds. BUILD OK``, ``6 report file(s): tests=54 failures=0
       errors=0 skipped=0``. It bootstrapped the JDK and Maven into its own
       ``tools/``, created the virtualenv from ``requirements-dev.txt`` and
       fetched 698 artefacts into its own ``_build/m2repo``. ``ls -la ~/.m2``
       says *no such directory* before and after -- the host repository has
       never been created by anything in this phase.

   * - 2
     - PASS
     - ``bash scripts/ci/docs-build.sh`` -- ``build 1 OK -- 51 HTML page(s)``
       from the literal ``sphinx-build -n -W -b html docs docs/_build/html``,
       ``build 2 OK -- 34 HTML page(s)`` over the 31 project documents outside
       ``docs/``. Falsified by me independently of the unit: appending
       ``:ref:`this-label-does-not-exist-anywhere``` to a copy of
       ``installation.rst`` gives exit 1 with ``WARNING: undefined label`` and
       ``warnings treated as errors``; removing it gives exit 0. An orphan
       document also fails, with ``document isn't included in any toctree``.

   * - 3
     - PASS
     - Two violations, both injected by me into a ``git archive HEAD``
       sandbox. A ``javafx.scene.control.Label`` reference in
       ``org.cometgui.domain``: ``Architecture Violation ... Rule 'no classes
       that reside in a package 'org.cometgui.domain..' should depend on ...
       'javafx..'' was violated (2 times)``, naming the method and line. A
       ``new ProcessBuilder("/bin/true").start()`` in the domain: the
       ``R-PROC-02`` rule fires the same way. Removing each returns the suite
       to green. ``bash scripts/verify-all-gates.sh --only tests`` re-proves
       both, plus a truncated import and a wrong root package.

   * - 4
     - PASS
     - An untested but genuinely branchy class added to
       ``org.cometgui.domain.build`` gives ``Rule violated for bundle
       cometgui-domain: lines covered ratio is 0.79, but expected minimum is
       0.90`` **and** the branch rule at 0.75 against 0.85; deleting it gives
       exit 0. The mutation gate bites at the same standard: the same kind of
       injection takes PIT from ``Generated 22 mutations Killed 22 (100%)`` to
       ``Killed 22 (71%)`` and fails with ``Mutation score of 71 is below
       threshold of 80``.

   * - 5
     - PASS
     - In a clean extraction: deleting the ``AC-DOC-02`` entry from the map
       gives exit 1 and ``[MAP-MISSING-ID] AC-DOC-02: defined in
       specification.rst but absent from traceability-map.toml``; pointing an
       entry at a file that does not exist gives ``[CHECK-MISSING]``; claiming
       a human sign-off the specification does not mark gives
       ``[AC-NO-EVIDENCE]``. The same failure is a **documentation build**
       failure, which is what ``R-DOC-03`` actually requires:
       ``sphinx-build`` exits 2 with ``ExtensionError: traceability: the
       traceability report is not complete``.

   * - 6
     - **PARTIAL**
     - **The "its failure modes are demonstrated" half passes; the "CI runs
       the pull-request pipeline on a pull request" half is unmet.** ``bash
       scripts/ci/run-pipeline-locally.sh`` exits 0 over ``42 step(s) across 3
       workflow(s); 37 executed on this machine; 0 unexpected``, and prints
       ``Still unmet: 'on a pull request'. No remote exists (D-008)`` itself.
       Nightly and release steps belonging to later phases exit **70** naming
       the owning phase rather than passing silently. ``git remote -v`` prints
       nothing and no remote was created. GitHub has never executed these
       files.

**Falsifiability, aggregated.** ``bash scripts/verify-all-gates.sh`` exits 0 in
**4m58s**: 9 controls, 123 graded checks, 0 failed, and it reports which gate
item each control serves. I attacked it: with ``verify-test-gates.sh`` made
mode 644, and again with ``sbom.sh`` deleted, it exits **3 before running
anything** -- ``REFUSING TO CONTINUE. Running the rest would report a green
summary for a set of gates that were never proved to bite``.

.. _p01-incomplete:

What is incomplete and why
==========================

#. **The pull-request pipeline has never run on a pull request.** There is no
   git remote; ``D-008``'s publication half is open; creating one is the
   owner's decision. Everything else about the pipeline is proved locally.
   Closing this needs one decision and no engineering.

#. **Read the Docs has never built the tree.** ``.readthedocs.yaml`` is
   written, is valid YAML, and uses only documented v2 keys, but the service
   builds from a remote. ``AC-DOC-01``'s second clause stays open; Phase 16
   owns it. Residual unknown: whether python 3.11 is pre-built for the
   ``ubuntu-24.04`` image.

#. **Windows and macOS runners are named but have never executed.** No
   non-Linux machine exists here. The matrix entries carry comments saying so,
   and the local runner records them as ``NOT RUN``. Same blocker as item 6.

#. **Coverage and mutation gates are inert on nine of twelve modules**, which
   is correct -- they hold only ``package-info.java`` today -- but a later
   phase must not mistake an inert rule for a passing one. The build prints
   ``inert  <module>  no classes with code yet`` per module so this cannot be
   missed, and two drift guards fail the build if a module grows gated code
   with its gate switched off.

#. **No real-tool integration test exists.** ``scripts/ci/integration-tests.sh``
   exits 0 today and asserts that there are genuinely no ``*IT.java`` files;
   it exits 1 the moment one appears. That was the unit's judgement call and I
   accepted it: a permanently red pipeline from here to Phase 08 teaches
   people to ignore red.

#. **The copyright holder line is a placeholder.** Every Java file says
   ``Copyright (C) 2026 The CometGUI authors.`` No legal entity is named
   anywhere in the project, and naming one is adjacent to ``D-001``/``D-008``.
   No agent should substitute a name.

Decisions encountered
=====================

No ``D-`` item was answered by this phase. Escalations and judgement calls:

``D-008`` (open, publication half)
    The only thing standing between Phase 01 and a full pass. It was escalated
    by the main orchestrator to the owner **before** this phase started; the
    owner directed the phase to run and record item 6 unmet. Nothing further
    was needed during the phase.

``D-001`` (decided) -- implemented, with one part deliberately left open
    ``LICENSE`` is the full GPL-3.0 text, verified three ways. Every Java file
    carries a short GPL header from a single template. **Phase 02 must extend
    that configuration for CasanovoGUI-derived files -- a second Spotless file
    set and a second Checkstyle execution with their own header -- and must
    never delete, suppress or exclude the existing check to make a derived
    file pass.** The obligation is written into ``pom.xml``,
    ``config/checkstyle/checkstyle.xml`` and ``CONTRIBUTING.rst``.

Judgement calls that were mine
    ``docs/feasibility/`` is **integrated** into the strict build rather than
    excluded, because the alternative was taking Phase 00's evidence out of
    the only strict build the project runs.
    ``scripts/feasibility/check-docs.sh`` is **kept, not retired**: it shares
    no configuration with ``docs/conf.py``, so it remains an independent
    cross-check if the real configuration ever develops a fault that hides
    warnings -- but it is no longer the gate.
    Dependency scanning uses **OSV, not OWASP dependency-check**, because the
    latter needs an NVD API key the project does not have.
    The per-file licence **header policy** was mine to settle and is recorded
    in ``CONTRIBUTING.rst``.

For the main orchestrator to route
    ``ONBOARDING.rst`` still calls ``specification.rst`` "revision 2" at lines
    43 and 87; it is revision 7. Every phase document still says ``:Status:
    NOT STARTED``, including Phase 00 (``PARTIAL``) and Phase 01. And
    ``phases/PHASE-05-tool-registry.rst`` refers to ``docs/user/tool_manager.rst``
    where the specification's tree says ``docs/tool_manager.rst``; the
    specification's path was used. None of these are mine to edit.

.. _p01-surprises:

Surprises
=========

**1. Spotless cannot check the licence header on ``package-info.java``, and
this repository has 53 of them.** ``spotless-maven-plugin`` applies
``LicenseHeaderStep.unsupportedJvmFilesFilter()`` unconditionally, which
excludes ``package-info.java``, ``package-info.groovy`` and ``module-info.java``
**by name**; no delimiter or configuration works around it. Relying on Spotless
alone would have left ``D-001``'s header obligation unmet on 87% of the tree.
Checkstyle's ``Header`` module over the same header file closes it. I
reproduced this myself: a header-less ``package-info.java`` gives
``spotless:check`` **exit 0** and ``checkstyle:check`` exit 1 with ``Missing a
header - not enough lines in file``. **Consequence for every later phase:**
``mvn spotless:apply`` will not add a header to a new ``package-info.java`` --
copy it from a sibling by hand.

**2. ``.gitignore``'s ``tools/`` pattern silently ate source files.**
Unanchored, it also matched ``org/cometgui/tools/`` inside three modules, and
``git add`` skipped eight files without saying so. It is now ``/tools/``. Any
project with a source package that collides with a gitignored directory name
has this bug and will not be told about it.

**3. A gate that is never run stops working without anyone noticing -- and it
happened inside this phase, to me.** My own integration commit ``f71ceb4``
pointed ``AC-TST-02..04`` at ``scripts/verify-test-gates.sh``; the traceability
self-test copied ``scripts/ci/`` and ``scripts/traceability/`` into its sandbox
but not ``scripts/``, so from that commit onward ``traceability.sh --self-test``
exited 4 with ``HARNESS FAILURE``. Nothing in ``build.sh`` or the pull-request
pipeline ran ``--self-test``, so **gate item 5's falsifiability quietly stopped
being demonstrable and every other check stayed green.** Unit 8 found it; I
confirmed it against the archived commit. The harness behaved correctly by
refusing to grade. This is exactly why ``scripts/verify-all-gates.sh`` exists
and why it belongs in the nightly pipeline.

**4. The vacuous pass is the real enemy, and every tool here offers one.**
JaCoCo exits 0 on a module with no execution data (``Skipping JaCoCo execution
due to missing execution data file``) and never evaluates the rule; ArchUnit
passes every rule if the import is empty; PIT can be told not to care about
zero mutations; SpotBugs can analyse nothing; ``spotless:check`` skips files it
does not support; a dependency scanner with no network reports no
vulnerabilities. Each is defended, and each defence is itself tested: a class
census with a floor of 50, a coverage-agent presence check, ``failWhenNoMutations``
left on, analysed-class counts, and a **canary** -- the dependency scanner
sends a known-vulnerable coordinate with every batch and fails if the endpoint
does not find it. I proved that one by writing a fake OSV endpoint that
answered "no vulnerabilities" to everything: the scanner exited 6 with
``CANARY CONTROL FAILED -- THE SCAN RESULT CANNOT BE TRUSTED``.

**5. ``--release 25`` resolves the JDK-bundled JavaFX modules with no
``--add-modules``.** Contrary to what the Phase 00 spike suggested, the spike
needed ``source``/``target`` only because it also passed ``--add-exports`` at
internal ``com.sun.*`` packages, which ``--release`` forbids. Product modules
compile against ``javafx.scene.control`` under ``release=25`` unchanged. A
module that later needs ``--add-exports`` must override the property locally.

**6. A JavaFX layering violation in the domain does not even compile** in this
build, so the ArchUnit rule is a second line of defence rather than the first.
That is worth knowing before someone concludes the rule is untested: it is
tested, but you have to make the violation compilable to see it fire.

**7. ``.mvn/jvm.config`` cannot contain comments.** Maven splits the file on
whitespace and passes every token to the JVM, so a ``#`` line gives ``Error:
Could not find or load main class #``. The explanation has to live in the POM.

**8. The headless JavaFX recipe is Linux/amd64 only and knows it.** The
``LD_LIBRARY_PATH`` names ``x86_64-linux-gnu``. The test asserts measured text
width rather than "did not throw", and when the font stack is missing it fails
**loudly** with the command that fixes it. The phase that adds non-Linux
runners owns splitting this into OS-activated profiles.

**9. ``bash scripts/build.sh --only format`` cannot be run standalone.**
Checkstyle's default cache makes a second run in the same ``target/`` write an
empty report, and the evidence check then fails with ``CHECKSTYLE CHECKED
NOTHING``. That is the evidence check working, not a false alarm.

First thing the next agent should do
====================================

**Run ``bash scripts/build.sh``, then read ``docs/developer/testing.rst``.**
The first tells you the skeleton is intact on your machine in about ninety
seconds; the second is the map of every gate you are about to be judged by,
what threshold it enforces, where that number comes from in the specification,
and the exact defect that proves it still bites. Phase 02 starts in
``cometgui-ui``, where the headless JavaFX recipe is already proven and
waiting -- and where ``D-001``'s derivation-notice obligation lands the moment
the first CasanovoGUI-derived file arrives.
