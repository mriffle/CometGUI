.. _dev-testing:

=======
Testing
=======

CometGUI's test suite has to do something stronger than "not throw". It has to
prove the application's scientific and provenance claims: that a checksum was
verified, that a q-value comparison points the right way, that an argument
array reached the process unchanged, that a secret never left the machine. A
suite that cannot do that is decoration, and this page is how a new contributor
finds out what the project already has, what it does not have yet, and -- for
every gate -- the defect that proves the gate actually bites.

.. note::

   **This page describes the state after Phase 01.** Phase 01 installed the
   gates before there was code to hide behind them, so almost everything below
   is a gate over a nearly empty tree. **Phase 15** (*Version Matrix,
   Performance and Hardening*, ``phases/PHASE-15-hardening.rst``) completes it
   with the real-tool, GUI, packaged and nightly suites. Where this page says
   something does not exist yet it names the phase that owns it, and
   `What is not tested yet`_ collects them.

   Every number, threshold, command and diagnostic on this page was produced by
   running the thing on 2026-08-29, not copied from another document.

.. contents:: Contents
   :depth: 2
   :local:

Commands
========

.. list-table::
   :header-rows: 1
   :widths: 46 54

   * - Command
     - What it does

   * - ``bash scripts/build.sh``
     - **The one documented build command.** Bootstraps ``tools/`` and
       ``.venv`` if needed, then runs all eleven stages: compile, unit tests,
       package, artefact verification, formatting and static-analysis
       evidence, coverage/architecture/mutation gates, integration tests,
       the strict documentation build, SBOM and dependency scan, and the CI
       workflow check. 87 s and ``11/11 stages OK`` on the development
       machine.

   * - ``bash scripts/build.sh --only gates``
     - Just the JaCoCo, ArchUnit and PIT stage, against the classes an earlier
       build already produced. 8 s. This is also exactly what the
       pull-request pipeline runs, through ``scripts/ci/test-gates.sh``.

   * - ``bash scripts/verify-all-gates.sh``
     - **Prove every gate still fails on the defect it exists to catch.** Runs
       all nine falsifiability harnesses and exits non-zero if any control
       stops biting. About five minutes. Run it before signing off a phase.

   * - ``bash scripts/verify-all-gates.sh --list``
     - The nine controls, what each injects, and the command that proves it.

   * - ``bash scripts/verify-all-gates.sh --only NAME``
     - One control. Names: ``license``, ``workflows``, ``docs``,
       ``traceability``, ``sbom``, ``depscan``, ``pipeline``, ``quality``,
       ``tests``. Repeatable, or comma-separated.

   * - ``bash scripts/ci/docs-build.sh``
     - The documentation gate on its own: both strict Sphinx builds. About 6 s.

   * - ``bash scripts/ci/traceability.sh``
     - The traceability report gate on its own, without Sphinx. About 1 s.

``scripts/verify-all-gates.sh`` is deliberately **not** a stage of
``scripts/build.sh``. It costs five minutes where the whole build costs ninety
seconds, and a gate people are tempted to skip is a gate that rots. It belongs
in the nightly pipeline and in a phase sign-off, not in the edit-compile loop.

Testing philosophy
==================

``specification.rst``, *Testing Strategy*, distinguishes seven kinds of test
and says plainly that unit testing alone is insufficient. Phase 01 has reached
the first of them and has built the scaffolding for two more.

.. list-table::
   :header-rows: 1
   :widths: 22 34 44

   * - Kind
     - What the specification asks for
     - State after Phase 01

   * - Fast unit tests
     - Every local and CI build; no network, no native tools.
     - **Running.** 54 tests over four modules, in ``mvn verify``.

   * - Component / integration
     - Filesystem, process, parser and install boundaries, with controlled
       fixtures and fake executables.
     - **Not written.** Needs the process service (Phase 03) and the fake
       executables the specification describes.

   * - Real-tool integration
     - Real pinned Comet, Percolator, converter and PDV binaries on small
       real fixtures.
     - **Not written**, and asserted to be absent rather than assumed:
       ``scripts/ci/integration-tests.sh`` searches every module test source
       root for ``*IT.java``, ``*ITCase.java`` and ``Integration*Test.java``
       and fails the moment one appears without failsafe wired up. Needs
       Phase 05 (tool registry), Phase 08 (workflow engine) and ``D-006``
       (whose data may be a fixture -- open).

   * - GUI tests
     - Drive JavaFX controls, verify UI state and validation.
     - **Recipe proven, suite not written.** Two headless JavaFX tests run in
       ``cometgui-ui``; see `The headless JavaFX recipe`_. Phase 14 owns the
       suite.

   * - Packaged end-to-end
     - Start the packaged application in a clean environment and drive a real
       workflow.
     - **Not written.** Phase 14.

   * - Nightly / scientific regression
     - Larger real data, version matrices, determinism, performance.
     - **Pipeline exists, work does not.** Every nightly step is a stub that
       exits 70 naming its owning phase. A green nightly today would mean
       nothing; a red one names exactly what is missing.

   * - Release acceptance
     - The exact installer and package artefacts that will be published.
     - **Not written.** Phases 14 and 16.

Two rules from ``CONTRIBUTING.rst`` apply to every test anyone adds:

* **A test that asserts "did not throw" is not a test.** Prove the value: the
  parsed field, the written file, the computed checksum, the rejected input,
  the exact error message.
* **Numeric targets come from the specification, not from taste.** Every
  threshold below cites the clause it comes from. A lower threshold must be
  documented with the untested risk, in the commit that lowers it.

The gates that exist today
==========================

Eleven gates, all wired into ``mvn verify`` or ``scripts/build.sh``. Every
version is pinned exactly in ``pom.xml``, and the two static analysers are
pinned *separately from their Maven plugins* (Checkstyle 14.0.0, SpotBugs
4.10.4) so a plugin's bundled default cannot drift underneath the gate.

Formatting and licence header -- Spotless
-----------------------------------------

:Checks: google-java-format 1.36.1, import order, unused imports, trailing
   whitespace, final newline, and the GPL-3.0 header.
:Threshold: none -- any deviation fails.
:Configured in: ``pom.xml`` (``spotless-maven-plugin`` 3.10.1), header text in
   ``config/license/java-header.txt``.
:Bound to: ``validate``, so it fails before anything is compiled.
:Runs with: ``bash scripts/build.sh``; ``mvn spotless:apply`` fixes what it can.

The header file is the single authoritative copy, and it is the one ``D-001``
obliges the project to carry. **Spotless cannot enforce it everywhere** -- see
`Traps`_.

Project style -- Checkstyle
---------------------------

:Checks: 55 modules in ``config/checkstyle/checkstyle.xml``, including
   ``Header`` (against the same header file), ``StringLiteralEquality``,
   ``NeedBraces``, ``FallThrough``, ``EqualsHashCode``, ``VisibilityModifier``,
   the naming rules, and Javadoc on public types and methods.
:Threshold: ``violationSeverity=error``, ``failOnViolation=true`` -- zero
   violations.
:Configured in: ``config/checkstyle/checkstyle.xml``, plugin block in
   ``pom.xml``.
:Bound to: ``validate``, with ``includeTestSourceDirectory=true``.

Checkstyle exists alongside Spotless rather than instead of it because neither
is sufficient alone: Spotless will not add braces or turn ``==`` into
``.equals``, and Checkstyle will not reformat. They were seen to catch
different defects.

Bug patterns -- SpotBugs
------------------------

:Checks: bytecode, at ``effort=Max`` and ``threshold=Low`` -- the most
   sensitive setting it has, chosen deliberately while the tree was almost
   empty so no later phase inherits a defect the analyser was never tuned to
   see.
:Threshold: zero findings.
:Configured in: ``pom.xml``; exclusions in ``config/spotbugs/exclude.xml``,
   which carries the policy and, as of 2026-08-29, exactly one narrow
   exclusion (three null-parameter patterns, in ``*Test.java`` only, because a
   test proving a method rejects ``null`` has to pass it ``null``).
:Bound to: ``verify``.

An exclusion is the last resort. A bare ``<Bug pattern=".."/>`` with no class
or method match silences a pattern across the whole product and is a weakening
of a gate.

Evidence that the three analysers ran
-------------------------------------

Exit code 0 proves nothing, and all three of these exit 0 when skipped. The
``format`` stage of ``scripts/build.sh`` therefore reads
``target/spotless-index``, ``target/checkstyle-result.xml`` and
``target/spotbugsXml.xml`` and compares what each tool says it inspected
against the ``.java`` files on disk, per module. On 2026-08-29 that was
``Spotless 63 file(s), Checkstyle 63 file(s), SpotBugs 66 class(es)`` over
twelve modules, and it fails with ``CHECKSTYLE CHECKED NOTHING`` if a tool
reports an empty file set.

Coverage -- JaCoCo
------------------

:Threshold: **>= 90% line and >= 85% branch** (``BUNDLE``) on core domain,
   parameter and provenance logic; **>= 80% line** (``PACKAGE``, matching
   ``org.cometgui.ui.viewmodel*``) on view-model and presenter logic.
:Where the numbers come from: ``specification.rst``, *Testing Strategy /
   Coverage* -- "core domain, parameter and provenance logic >= 90% line and
   >= 85% branch; UI-independent view-model and presenter logic >= 80% line;
   adapters covered by real integration tests rather than artificial line
   counts; JavaFX rendering glue has no numeric target".
:Configured in: ``pom.xml`` (``jacoco-maven-plugin`` 0.8.15), two ``check``
   executions, ``haltOnFailure=true``, **no** ``<excludes>``.
:Switched on per module: ``cometgui.coverage.core.skip=false`` in
   ``cometgui-domain``, ``cometgui-provenance``, ``cometgui-params-comet`` and
   ``cometgui-params-percolator``; ``cometgui.coverage.viewmodel.skip=false``
   in ``cometgui-ui``.
:Runs with: ``bash scripts/build.sh --only gates``.

Adapter, install, workflow and app modules have no numeric rule, because the
specification gives them none. That is a deliberate absence, not an oversight,
and the phase that fills a module owns turning its threshold on.

Measured on 2026-08-29: ``cometgui-domain line 100.0% (35/35) branch 100.0%
(24/24)``, ``cometgui-ui line 100.0% (1/1)``. Every other gated module is
reported ``inert`` -- see `Traps`_ for why an inert rule is not a passing rule.

Architecture -- ArchUnit
------------------------

:Checks: eight rules in
   ``cometgui-archtests/src/test/java/org/cometgui/archtests/LayeringRulesTest.java``
   -- the domain does not depend on JavaFX; the UI depends only on the domain
   and the application APIs; tool adapters do not depend on UI classes;
   provenance and hashing do not depend on the UI; the parameter parser and
   writer do not depend on JavaFX; the major layers have no dependency cycles;
   process creation is confined to the process service (``R-PROC-02``); and
   the UI contains no hashing, download or archive-extraction logic.
:Where the rules come from: ``specification.rst``, *Architecture tests*.
:Threshold: zero violations.
:Configured in: ``cometgui-archtests`` (ArchUnit 1.5.0),
   ``src/test/resources/archunit.properties``.

The rules are only as good as the class import they run against, so
``ClassImportCensusTest`` asserts the import itself: a floor of
``MINIMUM_IMPORTED_CLASSES = 50``, at least one class from every product
module, and a rule that matches no class fails rather than passing quietly. On
2026-08-29: ``55 classes imported from org.cometgui``, ``8 architecture rule(s)
checked, 0 failures``.

Mutation -- PIT
---------------

:Threshold: **>= 80% mutation score** over the critical packages
   (``mutationThreshold=80``).
:Where the number comes from: ``R-TEST-02`` -- ">= 80% mutation score in those
   packages, with **no** surviving mutation that can disable checksum
   verification, invert a q-value comparison, drop a required output, suppress
   a validation error, pass an unsupported option to a tool, or leak a secret".
:Target packages: eleven prefixes listed in ``pom.xml`` -- the domain, both
   parameter modules, provenance, results filtering and parsing, tools,
   install registry/verify/probe, and workflow state. A later phase adds its
   packages; it does not narrow the list.
:Configured in: ``pom.xml`` (``pitest-maven`` 1.30.0 with
   ``pitest-junit5-plugin`` 1.2.3), the ``mutation`` profile, and
   ``cometgui.mutation.skip=false`` in ``cometgui-domain``.
:Runs with: ``bash scripts/build.sh --only gates``, which invokes the goal
   directly and then fails if the report has no real mutations in it.

PIT is not part of a plain ``mvn verify`` because it re-runs the suite once per
mutation. It is not optional either: the build script re-derives from the
compiled classes which modules ought to have the gate switched on, and fails a
module that has critical-package code with its switch off. On 2026-08-29:
``cometgui-domain 22/22 mutations killed = 100.0%``.

Documentation -- strict Sphinx
------------------------------

:Checks: two builds. Build 1 is literally
   ``sphinx-build -n -W -b html docs docs/_build/html``, because ``R-DOC-05``
   fixes that command line. Build 2 covers the project documents that must not
   appear in the published toctree -- ``README.rst``, ``ONBOARDING.rst``,
   ``STATUS.rst``, ``DECISIONS.rst``, ``specification.rst``,
   ``CONTRIBUTING.rst`` and everything under ``phases/`` and ``handoffs/`` --
   in a throwaway tree regenerated under ``_build/docs-gate/`` on every run.
:Threshold: zero warnings. ``-n`` is nitpicky and ``-W`` makes warnings
   errors, so a broken internal cross-reference is a build failure
   (``R-DOC-05``). ``docs/conf.py`` sets ``nitpicky = True`` and has no
   ``suppress_warnings`` and no ``nitpick_ignore``.
:Runs with: ``bash scripts/ci/docs-build.sh``.

Both builds verify their output rather than trusting the exit code:
``sphinx-build`` can exit 0 having written nothing. On 2026-08-29: build 1,
49 source documents, 51 HTML pages; build 2, 31 documents, 34 pages.

Documents are **discovered, never listed**, so a document added later is
covered without editing the script.

Traceability -- ``R-DOC-03``
----------------------------

:Checks: every ``R-`` rule has exactly one implementing phase, and every
   ``AC-`` criterion names at least one automated test or is explicitly marked
   as needing human sign-off; that a named test class actually exists under
   ``cometgui-*/src/test/java``; and that a named automated check file exists.
:Threshold: zero unmapped identifiers, in either direction -- an identifier the
   specification defines and the map omits fails, and so does one the map
   invents.
:Configured in: ``docs/traceability-map.toml`` and ``scripts/traceability/``
   (stdlib Python only).
:Runs with: ``bash scripts/ci/traceability.sh``, and again inside the Sphinx
   build through the ``builder-inited`` hook in ``docs/conf.py``, which is what
   makes an incomplete map a *documentation build* failure rather than merely a
   script failure.

On 2026-08-29: ``94 R- rules, 78 AC- criteria, all mapped and verified``;
``0 automated, 5 partial, 65 planned, 8 human sign-off``; 48 generator unit
tests pass. The generated page is :doc:`traceability`; it is produced during
the documentation build so it cannot silently diverge from the code. Fix the
generator or its input, never the generated page.

Supply chain -- SBOM and dependency scan
----------------------------------------

:Checks: a CycloneDX 1.6 SBOM for the whole reactor including test scope, then
   every Maven coordinate in it queried against ``https://api.osv.dev``.
:Threshold: the SBOM must describe the project the POMs describe (an empty
   ``components`` array is a failure, not a clean bill of health); zero
   unaccepted vulnerabilities; every allowlist entry must carry a real reason
   and a date.
:Configured in: ``pom.xml`` (``cyclonedx-maven-plugin`` 2.9.3),
   ``scripts/ci/sbom_verify.py``, ``scripts/ci/dependency-scan.py``,
   ``scripts/ci/security/allowlist.json``.
:Runs with: ``bash scripts/build.sh`` (``supplychain`` stage).

On 2026-08-29: 26 components, JSON and XML agreeing on all 26 purls; 15
coordinates scanned plus one canary. The canary is the interesting part -- see
`Traps`_.

CI definitions
--------------

:Checks: every step in ``.github/workflows/`` names a ``scripts/ci/*.sh`` that
   exists and is executable; every pipeline step the specification requires has
   a step; every ``scripts/build.sh`` stage is covered by the pull-request
   pipeline; no stub in the pull-request pipeline; and ``release.yml`` has no
   secret, no push and read-only permissions.
:Runs with: ``bash scripts/ci/check-workflows.sh``, and
   ``bash scripts/ci/run-pipeline-locally.sh`` executes the steps themselves,
   reading them out of the workflow files rather than a copy.

**The pipelines have never run on GitHub.** There is no git remote and creating
one is ``D-008``, still open. Phase 01 exit gate item 6 is therefore half met:
every step is proved on this machine, and the "on a pull request" half is
recorded as unmet rather than pretended.

Falsifiability
==============

*A gate that has never been seen to fail has not been shown to work.* Every
gate above ships with a harness that injects the defect the gate exists to
catch, requires the narrowest command that should catch it to exit non-zero
**with the expected diagnostic**, and then requires the same command to pass
once the defect is removed. Every harness damages a copy under ``_build/``;
the working tree is never touched.

``bash scripts/verify-all-gates.sh`` runs all nine in one command. It injects
nothing itself -- it delegates -- and it fails if a sub-harness is missing or
not executable rather than skipping it, because a skipped control counted as a
pass is worse than no aggregator at all.

.. list-table:: The defect that proves each gate, and the diagnostic it produces
   :header-rows: 1
   :widths: 12 44 44

   * - Gate
     - Injected defect
     - Diagnostic required before the control passes

   * - Spotless
     - A class with 2-space indent, imports out of order and a 100-column
       overrun; and separately a class carrying an MIT header on a GPL-3.0
       file.
     - ``spotless:check`` exits non-zero naming ``NegativeControl.java``.

   * - Checkstyle
     - A ``package-info.java`` with no licence header; ``name == "comet"``; a
       brace-less ``if``.
     - ``Missing a header``; ``StringLiteralEquality``; ``NeedBraces``. The
       first is the one Spotless cannot see, and the control asserts both
       halves: Spotless passes, Checkstyle fails.

   * - SpotBugs
     - A method that dereferences a variable which is always ``null``, in a
       file that is clean for Spotless and Checkstyle, so nothing else can
       stop the build.
     - ``NP_ALWAYS_NULL`` -- *High: Null pointer dereference of nothing in
       ...lengthOfNothing()*.

   * - ArchUnit
     - A ``javafx.scene.control.Label`` reference in ``org.cometgui.domain``;
       ``new ProcessBuilder(...)`` outside the process service; and a
       deliberately truncated class import.
     - ``Architecture Violation [Priority: MEDIUM] - Rule 'no classes that
       reside in a package 'org.cometgui.domain..' should depend on classes
       that reside in any package ['javafx..' ...]' was violated``, naming the
       method and line; the same for ``no classes that reside outside of
       package 'org.cometgui.tools.process..' should depend on classes that
       are assignable to java.lang.ProcessBuilder``; and, for the truncated
       import, the census failing rather than every rule passing vacuously.

   * - JaCoCo
     - An untested but genuinely branchy class added to a gated package; and
       an untested class in ``org.cometgui.ui.viewmodel``.
     - ``Rule violated for bundle cometgui-domain: lines covered ratio is
       0.74, but expected minimum is 0.90``, with ``branches covered ratio is
       0.70, but expected minimum is 0.85``; and, for the view-model,
       ``Rule violated for package org.cometgui.ui.viewmodel: lines covered
       ratio is 0.00, but expected minimum is 0.80``.

   * - PIT
     - A test suite weakened until mutations survive.
     - ``Mutation score of 27 is below threshold of 80``, from a run that
       reported ``Generated 22 mutations Killed 6 (27%)`` -- while the
       weakened suite itself still passed, so only the mutation gate saw it.

   * - Coverage measurement
     - A module with classes but no execution data at all -- the vacuous pass
       JaCoCo offers for free, where the rule is never evaluated and
       ``mvn verify`` still exits 0.
     - ``scripts/build.sh`` fails the module rather than reporting it green.

   * - Sphinx
     - A ``:ref:`` to a label that does not exist, appended to a copy of
       ``docs/index.rst``.
     - ``WARNING: undefined label: 'docs-build-self-test-label-that-does-not-exist'``
       and ``warnings treated as errors``.

   * - Traceability
     - Eight defects, among them an ``AC-`` whose evidence list is emptied.
     - ``[AC-NO-EVIDENCE] AC-INS-01: no test reference and no human-sign-off
       mark`` -- required both from the script *and* from the strict Sphinx
       build, because ``R-DOC-03`` makes it a documentation build failure.

   * - SBOM
     - Eight damaged documents: empty ``components`` array, no ``components``
       key, ``bomFormat: SPDX``, JUnit dropped, reactor modules only, a
       mangled purl, a missing file, zero bytes.
     - e.g. ``the components array is EMPTY. The generator exited 0 and
       produced a valid document ...``; ``no components array at all``;
       ``bomFormat is 'SPDX', expected 'CycloneDX'``.

   * - Dependency scan
     - A known-vulnerable fixture; three kinds of unreachable endpoint; an
       endpoint answering HTTP 200 with an all-clear lie; five kinds of bad
       allowlist; an empty SBOM.
     - ``CVE-2021-44228`` found on the fixture; ``THE DEPENDENCY SCAN DID NOT
       RUN`` when it could not ask; ``CANARY CONTROL FAILED`` when the
       endpoint lies.

   * - CI definitions
     - Nine damaged copies of ``.github/``: a renamed step script, a dropped
       required step, ``continue-on-error``, a trailing ``|| true``, a
       ``git push`` added to ``release.yml``.
     - e.g. ``names scripts/ci/traceability.sh, which does not exist``.

   * - CI stubs
     - None injected. Every nightly and release step whose work belongs to a
       later phase *is* a stub, and each must exit 70 rather than 0.
     - A stub that exited 0 fails ``run-pipeline-locally.sh``, which classifies
       each step before running it.

   * - ``LICENSE``
     - Five damaged copies: truncated, altered title, CRLF-expanded, a wrong
       git blob sha, and absent.
     - e.g. ``byte count is 10119, expected 35149 -- the file is TRUNCATED by
       25030 bytes``; ``the text has been ALTERED``.

**The harnesses are themselves falsifiable.** Each proves the defect really
reached the sandbox before grading the control -- the file exists and differs
from the pristine state -- and reports a control whose defect was *not*
injected as a **harness failure**, never as a pass:

.. code-block:: text

   FATAL: HARNESS ERROR (deliberately un-injected): .../NegativeControl.java
   was not created in the sandbox. The control would have tested nothing.

``verify-all-gates.sh`` applies the same rule to its children. For each control
it requires the sub-harness to exist and be executable *before any control
runs*, to print the marker that means the defect was caught, and to grade at
least as many controls as the floor recorded in the script. A harness that
exits 0 with a lower count than it used to has had controls removed or skipped,
and that is a failure. On 2026-08-29 the nine controls graded 123 individual
checks in 4 m 58 s.

Traps
=====

Every one of these was found by a gate failing on it, and every one would
otherwise be rediscovered by a later phase.

**Spotless silently skips** ``package-info.java``.
   ``spotless-maven-plugin``'s ``licenseHeader`` step excludes
   ``package-info.java`` by name and **cannot be configured out of it** --
   observed directly: a ``package-info.java`` with no header at all gives
   ``spotless:check`` **exit 0** and ``checkstyle:check`` exit 1. This
   repository has 53 of them
   out of 63 Java files, so Spotless alone would have left ``D-001``'s header
   obligation unmet on most of the tree -- and ``spotless:check`` reports
   nothing wrong. Checkstyle's ``Header`` module, over the same header file,
   is what closes it. The practical consequence: ``mvn spotless:apply`` will
   **not** add a header to a new ``package-info.java``. Copy one from a sibling
   by hand, or the build fails with ``Missing a header - not enough lines in
   file``.

**JaCoCo passes a module with no execution data.**
   A rule it cannot evaluate is a rule it cannot violate. A module whose only
   classes are ``package-info`` carries no ``LINE`` or ``BRANCH`` counter at
   all, so the check goal passes it silently; so does a module whose tests
   never ran. An inert rule is not a passing rule. ``scripts/build.sh`` prints
   the measured counters per module -- ``ok`` with real numbers, or ``inert``
   with the reason -- and fails a module that grows gated code with its switch
   still off.

   The same class of fault bit once for real, and it is worth knowing: the
   view-model rule was first written with a slash-separated package pattern,
   on the reasonable assumption that JaCoCo report element names are VM names.
   They are dotted. The rule matched nothing and passed happily over an
   uncovered class. What caught it was the deliberately-failing view-model
   control -- which is the whole argument for making an inert rule fail on
   purpose before believing it.

**ArchUnit passes vacuously on an empty import.**
   Every rule is a statement about the classes ArchUnit was given. Given none,
   every rule holds. A misconfigured ``@AnalyzeClasses`` package, a module that
   failed to compile, or a jar that was not on the test classpath all produce a
   green suite that checked nothing. ``ClassImportCensusTest`` is the defence:
   a floor of 50 imported classes, at least one class per product module, and
   an explicit test that a rule matching no class fails.

**PIT and** ``failWhenNoMutations``.
   Turning it off is the classic vacuous pass -- PIT finds nothing to mutate,
   exits 0, and the 80% gate is never evaluated. It is left at its default
   (``true``) on purpose. The way a module with no critical code yet stays out
   is the per-module ``cometgui.mutation.skip`` switch, and ``build.sh``
   re-derives from the compiled classes which modules should have it on, so the
   switch cannot be used to hide code.

**Headless JavaFX needs an injected Monocle** *and* **a real font stack.**
   See `The headless JavaFX recipe`_ -- it is two separate traps, and each of
   them fails in a way that looks like something else.

**The dependency scanner's canary.**
   A vulnerability scanner is the classic tool that exits 0 while doing
   nothing: an unreachable endpoint, a dead proxy, or an endpoint that answers
   200 with "no vulnerabilities" all look exactly like a clean project. So
   ``dependency-scan.py`` sends a **canary coordinate** -- a version of
   ``log4j-core`` that is known to be vulnerable -- alongside the real ones,
   and requires the endpoint to find it. If the endpoint reports the canary
   clean, the scan exits 6 with ``CANARY CONTROL FAILED -- THE SCAN RESULT
   CANNOT BE TRUSTED``. A clean answer is only believed when the same query
   round-trip has just been seen to find something. There is no offline mode,
   and that is deliberate: an offline dependency scan is not a dependency scan.

**The Checkstyle evidence file is only valid immediately after the build.**
   ``maven-checkstyle-plugin`` keeps an incremental cache at
   ``target/checkstyle-cachefile`` by default. The first run over a clean
   ``target/`` writes a full ``checkstyle-result.xml``; a **second** run over
   an unchanged tree processes zero files and writes an empty report --
   measured as 1161 bytes then 83 bytes for ``cometgui-domain`` with
   ``mvn -B -q -pl cometgui-domain validate`` run twice. ``scripts/build.sh``
   is unaffected because its ``build`` stage is ``clean verify`` and its
   ``format`` stage reads the report immediately afterwards. But
   ``bash scripts/build.sh --only format`` on a tree whose last Maven
   invocation was anything else fails with ``CHECKSTYLE CHECKED NOTHING``.
   That is the evidence check working, not a false alarm -- run the full
   ``bash scripts/build.sh``.

The headless JavaFX recipe
==========================

The GUI tests run headless on Linux with no display, and the recipe is two
independent pieces. Both live in ``cometgui-ui/pom.xml``; each fails in a way
that does not obviously name its cause.

**An injected Monocle.** The Liberica Full JDK's ``javafx.graphics`` contains
no Monocle at all -- only ``com.sun.glass.ui.gtk`` and ``.delegate`` -- so
setting ``-Dglass.platform=Monocle`` alone fails trying to load ``libglass.so``.
``org.testfx:openjfx-monocle`` 21.0.2 supplies a headless Glass platform; it is
fetched into ``target/monocle/`` by ``maven-dependency-plugin`` and injected
with ``--patch-module javafx.graphics=...`` plus the ``--add-exports`` and
``--add-opens`` that let the platform factory be instantiated reflectively.
Then ``-Dglass.platform=Monocle -Dmonocle.platform=Headless -Dprism.order=sw``.

**A real font stack.** A ``Scene`` containing any ``Control`` initialises CSS on
its first ``Node``, which calls ``Font.getDefault()``. With no fonts that call
fails with ``fontFactory is null`` and every GUI test dies before its first
assertion. This host has no libfreetype, no fontconfig and no font files, and
nothing may be installed on it, so the stack is fetched from the Debian 12
archive into ``tools/fontstack-bookworm-20260829/`` by
``bash scripts/fetch-fontstack.sh``, every file pinned by SHA-256 and
re-verified on every run. Surefire then exports ``LD_LIBRARY_PATH``,
``FONTCONFIG_PATH`` and ``XDG_DATA_HOME`` at it.

The tests assert **measured text**, not that nothing threw:
``HeadlessSceneTest`` lays out a scene of real controls and requires the label
to report a non-zero width, and a long string to lay out wider than a short
one. A zero width would mean the font subsystem loaded nothing -- which is
exactly the failure this recipe exists to prevent, and exactly what a
"did not throw" assertion would have let through.

This is the **Linux** recipe. Nothing here has been executed on Windows or
macOS; Phase 00 established that and Phase 14 owns proving it elsewhere.

What is not tested yet
======================

Stated plainly, because a documentation page that implies more coverage than
exists is worse than no page.

.. list-table::
   :header-rows: 1
   :widths: 40 18 42

   * - Not tested
     - Owning phase
     - Why not yet

   * - Anything the application actually does
     - 02--13
     - There is no application yet. Each gated module carries one small class
       with real branching and real tests, so the gates are not vacuous. They
       are scaffolding and are marked as such; later phases replace rather
       than extend them.

   * - Component tests with fake executables
     - 03, 08
     - Needs the process service.

   * - Real-tool integration tests
     - 05, 08, and ``D-006``
     - No pinned Comet, Percolator, converter or PDV binary exists yet, and
       whose spectra and FASTA may be used as a fixture is an open owner
       decision.

   * - The GUI suite, and packaged end-to-end tests
     - 14
     - The recipe is proven; the controls to drive do not exist.

   * - Nightly regression, determinism, performance, version matrix
     - 15
     - Every nightly step is a stub that exits 70 naming its phase.

   * - Release acceptance on real installer artefacts
     - 14, 16
     - Nothing is packaged yet.

   * - Coverage gates on adapter, workflow, install and app modules
     - the phase that fills each module
     - The specification gives those modules no numeric target and says
       adapters are covered by real integration tests instead. The rules are
       written and inert; turning one on is the job of the phase that adds the
       code.

   * - Windows and macOS, anywhere
     - 05, 09, 14, 15
     - This environment has one Linux machine and no remote. The workflow
       files name the runners and the matrix so a later phase turns them on
       rather than discovering they were never written.

   * - CI on an actual pull request
     - blocked on ``D-008``
     - There is no git remote and creating one is an owner decision. Every
       pipeline step is proved locally instead, and Phase 01 records gate
       item 6 as half met.
