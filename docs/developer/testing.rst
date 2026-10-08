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
       all fifteen falsifiability controls and exits non-zero if any control
       stops biting. About an hour since Phases 05 and 06 (3875 s recorded in
       ``scripts/dev-verify.sh``), Phase 07's ``paramui`` adds about
       twenty minutes and Phase 08's ``workflow`` about eight. Run it before
       signing off a phase.

   * - ``bash scripts/verify-all-gates.sh --list``
     - The fifteen controls, what each injects, and the command that proves
       it.

   * - ``bash scripts/verify-all-gates.sh --only NAME``
     - One control. Names: ``license``, ``workflows``, ``docs``,
       ``traceability``, ``sbom``, ``depscan``, ``pipeline``, ``quality``,
       ``shell``, ``tests``, ``provenance``, ``install``, ``params``,
       ``paramui``, ``workflow``. Repeatable, or comma-separated.

   * - ``bash scripts/ci/docs-build.sh``
     - The documentation gate on its own: both strict Sphinx builds. About 6 s.

   * - ``bash scripts/ci/traceability.sh``
     - The traceability report gate on its own, without Sphinx. About 1 s.

``scripts/verify-all-gates.sh`` is deliberately **not** a stage of
``scripts/build.sh``. It costs about twelve minutes where the whole build costs
under three, and a gate people are tempted to skip is a gate that rots. It
belongs in the nightly pipeline and in a phase sign-off, not in the
edit-compile loop.

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

:Checks: nine rules in
   ``cometgui-archtests/src/test/java/org/cometgui/archtests/LayeringRulesTest.java``
   -- the domain does not depend on JavaFX; the UI depends only on the domain
   and the application APIs; tool adapters do not depend on UI classes;
   provenance and hashing do not depend on the UI; the parameter parser and
   writer do not depend on JavaFX; the major layers have no dependency cycles;
   process creation is confined to the process service (``R-PROC-02``); the
   UI contains no hashing, download or archive-extraction logic; and (Phase
   07) the UI goes through the parameter model for every Comet value -- no
   line reader, ``Numbers``, value codec or ``java.util.regex``.
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
   ``cometgui.mutation.skip=false`` in each module with critical-package
   code: as of Phase 09, ``cometgui-domain``, ``cometgui-process``,
   ``cometgui-provenance``, ``cometgui-tools``, ``cometgui-install``,
   ``cometgui-params-comet``, ``cometgui-params-percolator``,
   ``cometgui-results`` and ``cometgui-workflow``.
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

``bash scripts/verify-all-gates.sh`` runs all fourteen in one command. It injects
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

   * - Hashing and provenance (Phase 04, ``provenance``)
     - ``scripts/verify-provenance-gates.sh``: a hasher digesting one byte
       less than it read; a hasher keeping every chunk; a fingerprint treating
       an absent attribute as a match; a log reader dropping a torn tail;
       ``ATOMIC_MOVE`` replaced by copy-then-delete; redaction removed from
       each of three writers.
     - Each test's own assertion, from the injections recorded in
       ``handoffs/PHASE-04-worklog.rst``; item 7 is delegated to the ``tests``
       control's mutation gate.

   * - Tool registry and installer (Phase 05, ``install``)
     - ``scripts/verify-install-gates.sh``: nineteen injections into
       production code recorded in ``handoffs/PHASE-05-worklog.rst`` (two of
       them new at unit 12, one at unit 14), five damaged manifests for the
       documentation-table generator, each in a ``git archive HEAD`` sandbox
       and each proved to have changed the compiled class.
     - Each failing assertion's own words, e.g. ``never FAILED: a user who
       cancelled has not encountered an error ==> expected: <CANCELLED> but
       was: <FAILED>``. Item 9 (macOS) is not met and only delegated.

   * - Comet parameter model (Phase 06, ``params``)
     - ``scripts/verify-param-gates.sh``: fourteen injections into
       ``cometgui-params-comet`` recorded in ``handoffs/PHASE-06-worklog.rst``
       -- the writer skipping empty values, an entry removed from the shipped
       metadata, drift comparing first tokens, a dropped neutral loss, a
       reversed count, the enzyme table's duplicate check, the writer's
       enzyme refusal, two locale-sensitive number writers, unknown parameters
       dropped by the writer and by the parser, and three tolerance-pair rule
       defects; unit 7's generator self-test, invoked; and PIT scored per
       package. Since the Comet 2026.03.0 intake (``COMET-2026-03``) also
       fifteen controls that each make one version-scoped fact version-blind
       -- overrides, drift, residue alphabets, the 2026.03.0 round trip, rule
       severities, the AScorePro and ``index_search_type`` rules, value
       migrations, and one recorded binary verdict of the validation corpus
       (data, not code). See :ref:`dev-comet-parameter-falsifiability`.
     - Each failing assertion's own words, e.g. ``min,max count ==> expected:
       <79.966331 STY 0 2,4 -1 0 0 0.0> but was: <79.966331 STY 0 4,2 -1 0 0
       0.0>``; for item 9, ``parser``, ``writer`` and ``validation`` each
       ``>= 80%`` killed and, with the validation tests removed, ``validation
       is graded BELOW the 80% threshold`` while the module-wide score, all
       ``scripts/build.sh`` grades, still passes.

   * - Comet parameter editor (Phase 07, ``paramui``)
     - ``scripts/verify-param-ui-gates.sh``: twenty-three injections into the
       views, controls and view-models of ``cometgui-ui``, at least two per
       exit gate item, four of them version-blind, each graded in the GUI
       gate test that asserts the item. See :ref:`dev-param-ui-falsifiability`.
     - Each failing assertion's own words, e.g. ``Enter on the summary entry
       moves the focus to the field ==> expected:
       <ess-peptide_mass_tolerance_lower> but was: <param-summary-entry-0>``;
       and unit 6's equivalent injection reported
       as ``HARNESS FAILURE -- the check PASSED with the defect present``.

   * - Workflow engine and Comet adapter (Phase 08, ``workflow``)
     - ``scripts/verify-workflow-gates.sh``: nineteen injections into
       ``cometgui-tools``, ``cometgui-params-comet`` and ``cometgui-workflow``
       -- at least one per exit gate item and three on the index-compatibility
       check, two of them version-blind -- most graded against the real pinned
       Comet binaries. See :ref:`dev-workflow-falsifiability`.
     - Each failing assertion's own words, or the real binary's: e.g. Comet's
       ``Error - cannot write to file ".../read-only inputs/k562_3.pep.xml"``
       when every spectrum file is put on one command line.

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
checks in 4 m 58 s. Phase 02 added a tenth, ``shell``
(``scripts/verify-shell-gates.sh``, which proves that phase's five exit gate
items fail on the defects they exist to catch), re-sized the ``tests``
controls against a tree that had outgrown them, and added seven derived-file
controls to ``quality``; on 2026-08-31 the ten controls graded 176 checks.

.. _dev-param-ui-falsifiability:

The parameter editor's harness (Phase 07, ``paramui``)
-------------------------------------------------------

``bash scripts/verify-param-ui-gates.sh`` proves that each of Phase 07's eight
exit gate items fails on a defect it exists to catch. It is registered in
``scripts/verify-all-gates.sh`` as ``paramui`` and is built from the two
harnesses before it: Phase 02's ``scripts/verify-shell-gates.sh`` (GUI gate
tests) and Phase 06's ``scripts/verify-param-gates.sh`` (the private overlay
and the bytecode proof).

* It extracts ``git archive HEAD`` into ``_build/paramui-gate-sandbox`` and
  damages only that. ``tools/`` is gitignored, so it is symlinked into the
  sandbox: the headless JavaFX tests find the font stack through the
  sandbox's own project directory, and without the link every control would
  fail for that reason instead of the injected one.
* Every module ``cometgui-app`` depends on, except ``cometgui-ui``, is built
  once from the sandbox and installed into a private overlay repository
  (``_build/paramui-gate-m2``, every other entry a symlink into
  ``_build/m2repo``). Each control then builds ``cometgui-ui`` and
  ``cometgui-app`` alone and runs only the test classes it grades;
  ``cometgui-app`` takes ``cometgui-ui`` from the reactor, and the overlay is
  checked to hold no copy of either. The shared repository's project jars are
  checked unchanged at the end.
* Each injection's anchor must match exactly once; the damaged file must
  differ from its pristine copy; after the run its compiled classes must
  differ from the clean baseline and every other file under both modules'
  ``target/classes`` must be identical to it. One file is compared with one
  line removed: ``build-identity.properties`` is Maven-filtered and carries
  the build's timestamp, which changes on every run, so its digest leaves out
  the ``cometgui.buildTimestamp`` line -- and the line must be there.
* The GUI tests are ordered and later methods start where earlier ones
  stopped, so a control runs whole test classes and then reads surefire's XML
  to require a named method red -- and, for a version-blind control, the
  method of the release the defect does not touch green.
* A final clean run of every graded class must pass with both compiled
  modules byte-identical to the baseline.
* Control ``H`` requires the harness to refuse, as a harness error or
  failure, an injection equal to its anchor, a missing anchor, an injection
  that reaches the source but not the bytecode (a real Maven run), a green run
  graded as red, a red without its diagnostic -- and unit 6's **equivalent**
  injection, run for real: Run disabled only by the workflow engine's reason
  leaves ``CrossParameterValidationUiTest`` green, because that test's
  application has no Comet installed, so the engine always has a reason
  against Run there. The harness must report that as ``HARNESS FAILURE -- the
  check PASSED with the defect present``, never as a control that bit; if the
  injection ever goes red there, control ``H`` fails and says the premise is
  stale. That is ``H6``. Since Phase 08 the engine can be ready, so ``H7``
  makes the same injection where it is not equivalent -- graded on
  ``RunReadinessUiTest``, whose application has a Comet registered and real
  files chosen: ``theParametersAloneDisableRun`` must go red, and
  ``theDecoyBlockOnScreen``, where the engine itself disables Run, must stay
  green.

``--self-test`` runs control ``H`` alone (with the baseline it needs);
``--only 1b,4v`` runs named controls plus the baseline and the final clean run.

.. list-table:: The controls (production code of ``cometgui-ui``; "recorded" names the injection in ``handoffs/PHASE-07-worklog.rst``)
   :header-rows: 1
   :widths: 6 6 42 46

   * - Control
     - Item
     - Injected defect
     - Diagnostic required (the failing assertion's own words)
   * - 1
     - 1
     - The Essentials decoy control always sets decoys-in-the-FASTA (named in
       the unit-8 brief; the work log records no item-1 injection).
     - ``EssentialsTrypticSearchUiTest``: ``#ess-decoy_search after choosing
       "Concatenated: ..." ==> expected: <Concatenated: ...> but was: <No
       internal decoys>``.
   * - 1b
     - 1
     - New: every control shows the choice, and the save writes
       ``decoy_search = 0`` -- visible only in the saved file.
     - ``the saved file differs from the checked-in
       essentials-tryptic-dda-2026.03.0.params``; and the copy the test leaves
       differs from the expected file in line 6, ``decoy_search``, alone.
   * - 1c
     - 1
     - New (unit 10): a static-modification table row writes its mass to the
       next row's parameter.
     - ``StaticModificationTableUiTest``: ``the lysine row after its mass was
       typed ==> expected: <[lysine (K), 229.162932, ...]> but was: <[lysine
       (K), 0.0000, ...]>``; and ``EssentialsTrypticSearchUiTest``: ``the
       saved file differs from the checked-in
       essentials-tryptic-dda-2026.03.0.params``.
   * - 2a
     - 2
     - New: the slot editor's *Move up* moves the slot down.
     - ``VariableModificationEditorUiTest``: ``after moving slot 2 up (2
       failures)``, ``expected: <Serialised: variable_mod01 = 79.96633 SY 0 2
       -1 0 0 0.0> but was: <Serialised: variable_mod01 = 15.9949 M 0 3 -1 0 0
       0.0>``.
   * - 2v
     - 2
     - **Version-blind** (recorded, unit 6, 6a): the slot editor offers every
       terminus code, ``^`` and ``$`` included, on every release.
     - ``Comet 2026.02.2 (2 failures)`` in
       ``theOlderReleaseOffersPeptideTerminiOnly``;
       ``theDefaultReleaseOffersProteinTermini`` must stay green.
   * - 3a
     - 3
     - Recorded (unit 7, 7b): the preset preview's *Cancel* applies every row.
     - ``PresetPreviewUiTest``: ``cancelling changes nothing; changed: ==>
       expected: <[]> but was: <[fragment_bin_offset = 0.4, ...``.
   * - 3b
     - 3
     - New: *Apply selected* applies every row.
     - ``expected: <Applied 2 changes of Low-res precursor, low-res
       fragments: ...> but was: <Applied 8 changes of ...``.
   * - 3c
     - 3
     - New (unit 10): the Essentials fragment instrument choice applies its
       preset's rows the moment it is chosen -- unit 5's behaviour, removed by
       unit 10 (``AC-PAR-08``).
     - ``FragmentInstrumentPreviewUiTest``: ``choosing changes nothing;
       changed: ==> expected: <[]> but was: <[fragment_bin_offset = 0.4,
       fragment_bin_tol = 1.0005, theoretical_fragment_ions = 1]>``.
   * - 3d
     - 3
     - New (unit 10): the fragment choice's preview is not scoped to the
       fragment parameters, so it offers the preset's precursor rows too.
     - ``expected: <[[Fragment bin width (fragment_bin_tol), 0.02, 1.0005],
       ...]> but was: <[[Precursor tolerance, upper bound
       (peptide_mass_tolerance_upper), 10, 3.0]``;
       ``PresetPreviewUiTest`` (a whole-preset preview) must stay green.
   * - 4a
     - 4
     - Recorded (unit 7, 7a): a raw apply that fails to parse resets the
       configuration.
     - ``ExpertRawEditUiTest``: ``a draft that does not parse (3 failures)``,
       ``the configured values and origins after the refused raw edit``.
   * - 4b
     - 4
     - New: a raw apply that parses is adopted without confirmation.
     - ``not before confirming ==> expected: <0> but was: <6>``.
   * - 4v
     - 4
     - **Version-blind** (recorded, unit 7 agent): the draft is parsed as the
       first offered release whatever release is selected.
     - ``Comet 2026.02.2 refuses the ^ at line 27 (2 failures)`` in
       ``theCaretDependsOnTheRelease``; ``aFailedParseChangesNothing`` (on
       2026.03.0) must stay green.
   * - 5a
     - 5
     - Recorded (unit 6): a locked output's check box left enabled. The
       session still refuses the edit, so the value stays 1.
     - ``WorkflowOutputsLockedUiTest``: ``disabled for change ==> expected:
       <true> but was: <false>``, naming ``ess-output_pepxmlfile``.
   * - 5b
     - 5
     - New: the lock's reason is never shown.
     - ``the reason is on screen ==> expected: <true> but was: <false>``.
   * - 6a
     - 6
     - Recorded (unit 6): a validation-summary entry no longer moves the focus
       to its field.
     - ``CrossParameterValidationUiTest``: ``Enter on the summary entry moves
       the focus to the field ==> expected: <ess-peptide_mass_tolerance_lower>
       but was: <param-summary-entry-0>``.
   * - 6b
     - 6
     - Recorded (unit 6, 6e): the parameters' readiness text forced to "do not
       block".
     - ``the Run section (2 failures)``; and
       ``MigrationReviewBlocksRunUiTest`` (an unresolved migration entry
       blocks Run, decision P7-3) red with ``but was: <The parameters do not
       block a run.>``.
   * - 7a
     - 7
     - Recorded (unit 6, 6d, after its rework): a parameter control's own
       accessible name removed, leaving Phase 02's generated fallback.
     - ``ParameterControlsAccessibilityUiTest``: ``TextField
       #ess-database_name under #param-essentials has only the generated
       fallback name``; the representative names, typed out; and
       ``AccessibleNameEnumerationUiTest``: ``... controls this project
       created have no name of their own``.
   * - 7b
     - 7
     - New: the validation state left out of a parameter control's accessible
       help.
     - ``#ess-database_name's accessible help does not carry its state``.
   * - 7c
     - 7
     - New (unit 10): a static-modification table row's mass field loses its
       own accessible name.
     - ``TextField #ess-add_Cterm_peptide under #param-essentials has only
       the generated fallback name``.
   * - 7v
     - 7
     - **Version-blind**, new: a field's choices are the curated definition's,
       not the selected release's.
     - ``indexSearchTypeOnTheDefaultRelease``: ``expected: <[Not set: ...,
       Peptide index (PI_DB), Fragment-ion index (FI_DB)]> but was: <[Peptide
       index (PI_DB), Fragment-ion index (FI_DB)]>``; ``theOlderRelease`` must
       stay green.
   * - 8a
     - 8
     - Recorded (unit 7, 7c): alias matching removed from the search.
     - ``ParameterSearchUiTest``: ``by alias (3 failures)``.
   * - 8b
     - 8
     - New: activating a search result no longer moves the focus to its field.
     - ``after activating the result for #adv-allowed_missed_cleavage``, ``the
       field has the focus``.
   * - 8v
     - 8
     - **Version-blind** (recorded, unit 5, 5c): a field's help is the curated
       definition's, not the release's.
     - ``ParameterSearchViewModelTest.releaseHelp``: ``expected:
       <[index_search_type]> but was: <[]>`` (the view-model grading unit 8
       shipped); and, since unit 9, the item-8 GUI test through the launched
       application: ``Comet 2026.03.0, help text "not set" (3 failures)`` and
       ``expected: <[Index type for an index built on demand
       (index_search_type) -- Matched by help text]> but was: <[]>`` in
       ``ParameterSearchUiTest.helpTextOfTheDefaultRelease``;
       ``helpTextOfTheOlderRelease`` must stay green, because the curated
       help *is* 2026.02.2's.
   * - H
     - --
     - The harness itself, as above.
     - Each a ``HARNESS ERROR`` or a recorded ``HARNESS FAILURE``.

What it does not cover: the native file dialogs (never opened headless) and the
gate-1 file's Linux path, which unit 6's sign-off records as unverified; the
``R-PARAM-06`` warning on reading a file as the selected release, tested at
view-model level only; and the unlocking of an output when its stage is
switched off, which is deferred to Phases 11 and 12 -- in this phase every
stage is enabled, so item 5's controls prove the lock and its reason only.

Measured on 2026-10-05: 1272 s (21 m 12 s) for a full run, of which the
baseline and the final clean run (all eleven graded classes, once each) take
about ten minutes together, and a control between 8 s and 68 s; 66 controls
passed, and that count was the floor ``verify-all-gates.sh`` held it to.

Unit 9 (2026-10-05) gave ``ParameterSearchUiTest`` two methods, one per
release, each searching a phrase only that release's help says -- ``not set``
in 2026.03.0's help of ``index_search_type``, ``is ignored`` in 2026.02.2's
help of ``spectral_library_ms_level`` -- and requiring it to find that
parameter on its own release and nothing on the other. Before that, unit 8
found that the GUI test's only help-text query (``placeholder``) is the same in
both releases' help, so 8v could be graded on the view-model test alone.
Control 8v now also runs ``ParameterSearchUiTest`` and grades it (four checks
added) -- in a second run on the same injection, because a failing
``cometgui-ui`` test stops the reactor before ``cometgui-app`` runs anything --
and the floor was raised to the count measured with them.

Unit 10 (2026-10-05) gave the Essentials fragment instrument choice a preview
of its own and built the static-modification table, and added four controls:
``1c`` (a table row writes its mass to the next row's parameter, graded in
``StaticModificationTableUiTest`` and in the gate-1 saved file), ``3c`` (the
instrument choice applies its rows the moment it is chosen), ``3d`` (its
preview not scoped to the fragment rows, with ``PresetPreviewUiTest`` required
to stay green) and ``7c`` (a table mass field unnamed). Controls ``3a`` and
``3b`` anchor on the same lines as before, which moved unchanged from
``PresetControl`` into ``PresetReviewPane``. The baseline and the final clean
run now cover thirteen graded classes. Measured: 84 controls passed in 1680 s
(28 m 00 s), the baseline 366 s and the clean run 353 s; the floor was raised
from 70 to 84.

Phase 08 unit 7 (2026-10-07) put the workflow engine behind the Run section and
split control ``H``'s engine injection into ``H6`` (kept, still equivalent where
no Comet is installed) and ``H7`` (new, graded where the engine is ready), as
described above; ``scripts/verify-param-ui-gates.sh`` says the same in its
header. The floor was raised from 84 to 87, the count measured with ``H7``
(``handoffs/PHASE-08-worklog.rst``, unit 7).

.. _dev-workflow-falsifiability:

The workflow engine's harness (Phase 08, ``workflow``)
-------------------------------------------------------

``bash scripts/verify-workflow-gates.sh`` proves that each of Phase 08's nine
exit gate items, and the index-compatibility check tier 1 assigned to the
phase, fails on a defect it exists to catch. It is registered in
``scripts/verify-all-gates.sh`` as ``workflow`` and takes its shape from
``paramui`` and ``install``; the differences are these.

* Three modules are damaged and graded -- ``cometgui-tools``,
  ``cometgui-params-comet`` and ``cometgui-workflow`` -- so every module
  ``cometgui-workflow`` depends on *except* those three is built once into a
  private overlay (``_build/workflow-gate-m2``), and every run builds the
  three alone. The overlay is checked to hold none of them.
* ``scratch/`` (the pinned Comet binaries and the ``D-006`` inputs) is
  symlinked into the sandbox: the real-binary gate tests stage copies from it
  and fail rather than skip without it.
* **Red is read from surefire's XML, not from Maven's exit code.** A control
  may grade a rule test in ``cometgui-params-comet`` and a real test in
  ``cometgui-workflow`` on the same injection, and a failing upstream module
  would stop the reactor first; so every run passes
  ``-Dmaven.test.failure.ignore=true``, a non-zero Maven exit is a harness
  error, and every failing testcase's message is copied, unescaped, from the
  XML into the run's log, where the expected words are matched. A real test
  whose search runs in ``@BeforeAll`` fails as a class-level testcase with an
  empty name, and is graded as that.
* Engine steps are compared as **sets**: JUnit prints a ``Set.of`` in an order
  that changes from one JVM to the next, so for the rerun-preview controls the
  harness parses ``expected: <[...]> but was: <[...]>`` and requires the
  difference to be exactly the steps the defect loses.
* Every test JVM's ``java.io.tmpdir`` is inside the sandbox (through
  ``cometgui.surefire.extraArgLine``, which none of the three modules sets), so
  a Comet a test staged, and every ``EngineFake``, runs from a path naming the
  sandbox. After each control that starts processes, nothing so named may be
  alive; anything that is is killed and the control fails.

.. list-table:: The controls ("recorded" names the unit of ``handoffs/PHASE-08-worklog.rst`` whose sign-off made the injection)
   :header-rows: 1
   :widths: 6 6 42 46

   * - Control
     - Item
     - Injected defect
     - Diagnostic required
   * - 1a
     - 1
     - Recorded (unit 4): ``-N`` dropped from the per-file command.
     - The real search's ``@BeforeAll`` in ``CometAdapterRealBinaryTest``:
       Comet's own ``Error - cannot write to file ".../read-only
       inputs/k562_3.pep.xml"``; and ``CometSearchCommandsTest``'s argv
       comparison.
   * - 1b
     - 1
     - New: every spectrum file on one Comet command line, ``-N`` kept.
     - The same Comet refusal, from a command shown to carry ``-N`` into the
       run *and* both inputs (Comet ignores ``-N`` then);
       ``oneInputPerCommand``: ``expected: <[/data/K562 3.mzML]> but was:
       <[/data/K562 3.mzML, /data/fractions/k562_4.MZXML]>``.
   * - 2
     - 2
     - New: the ``-N`` base put in ``runs/`` beside the run. The search
       succeeds; only the snapshot can see it.
     - ``RealCometRunTest#gate2...``: ``expected: <[project/runs]> but was:
       <[project/runs, project/runs/k562_3.pep.xml, ...``.
   * - 3a
     - 3
     - Recorded (unit 4): PIN feature columns compared as sets.
     - ``Expected ...CometOutputException to be thrown, but nothing was
       thrown`` in ``swappedColumns`` and ``swappedRealColumns``; the
       renamed-column tests stay green.
   * - 3b
     - 3
     - New: a header line written for every merged input.
     - ``RealCometRunTest#gate3...``: ``expected: <1> but was: <2>``; gate 1
       stays green.
   * - 4a
     - 4
     - Recorded (unit 3): "no decoys anywhere" judged only for an empty FASTA.
     - ``expected exactly one finding: ValidationReport[findings=[]]``; the
       real gate-4 test's ``the decoy configuration did not block the run``;
       the real gate-5 test stays green.
   * - 4b
     - 4
     - New: the FASTA decoy census taken but not added to the pre-run facts.
     - ``the decoy configuration did not block the run`` in both real decoy
       tests; the rule test, fed a census directly, stays green.
   * - 5
     - 5
     - New: the double-decoy block applied to ``decoy_search = 1`` only.
     - The real gate-5 test and ``doubleDecoysSeparate`` red;
       ``doubleDecoysConcatenated``, gate 4 and the engine's own validate
       step green.
   * - 6a
     - 6
     - Recorded (unit 1): the ``merge-pin -> run-percolator`` edge dropped.
     - Scenario (d)'s ``re-executed`` set short of exactly ``RUN_PERCOLATOR``
       and the five steps downstream of it alone.
   * - 6b
     - 6
     - Recorded (unit 1): ``run-percolator`` no longer declaring the
       Percolator settings.
     - Scenario (b)'s ``re-executed`` set empty where eight steps were
       expected; scenario (d) green.
   * - 6c
     - 6
     - Recorded (unit 6): the Comet-parameter fingerprint taken from the
       binary's digest.
     - After the real run, the preview of changed parameters names none of
       the five steps expected; gate 1 green.
   * - 7
     - 7
     - Recorded (unit 5): ``RunExecution.cancel`` no longer reaching running
       steps.
     - ``java.util.concurrent.TimeoutException`` at the test's own 60-second
       wait for the fake Comet's child (``CancellationTest.java:98``); then
       no process started from the sandbox alive.
   * - 8a
     - 8
     - Recorded (unit 5): every re-hash treated as equal to its record.
     - ``expected: <false> but was: <true>``; both ``RealChangedInputTest``
       methods red.
   * - 8b
     - 8
     - New: revalidation served from the hash cache.
     - ``revalidation asked the cache for nothing it could serve``; the plain
       changed-input test stays green.
   * - 9a
     - 9
     - New: the recorded ``comet.params`` SHA-256 taken from the Comet binary.
     - ``but was: <ad93b4cf...>`` (the binary's hash) in gate 9; gate 1 green.
   * - 9b
     - 9
     - New: every Comet tool record given the first invocation's argv; the
       launched commands stay right.
     - ``the argvs differ only in -N and the input ==> expected: <[2, 3]> but
       was: <[]>``; gate 1, which asserts the launched argvs, green.
   * - Iv
     - index
     - **Version-blind**, recorded (unit 3): the readable index formats judged
       ``< 4`` for every release.
     - ``expected: <INDEX_FORMAT_UNREADABLE> but was:
       <INDEX_OPTION_UNRECORDED>``; ``RealIndexTest``'s one test of both
       releases red on its 2026.03.0 half (``RunBlockedException`` not
       thrown).
   * - Iw
     - index
     - **Version-blind**, new: the first release record's formats (2026.03.0's)
       for every release.
     - The same real test red on its *other* half: ``and Comet 2026.02.2 reads
       index format v5``; its 2026.03.0 half held.
   * - Ih
     - index
     - Recorded (unit 6): an existing ``.idx`` header left out of the pre-run
       facts.
     - ``RunBlockedException`` not thrown in ``RealIndexTest``; the rule test,
       fed the header directly, green.
   * - H
     - --
     - The harness itself.
     - An unchanged file, a missing anchor, a comment-only injection (a real
       Maven run) and a selection naming a method that does not exist (a real
       run of zero tests) are each a ``HARNESS ERROR``; a green run graded as
       red, a red without its diagnostic and a process left alive from the
       sandbox are each a recorded failure.

What it does not cover: the GUI half of Run (``paramui``'s ``H7``); the
real-binary cancellation test, because with cancellation broken the real Comet
searches the whole proteome to its end, a duration no timeout of that test
bounds (item 7 is graded on the fake-Comet process-tree test, which is
bounded); and storage and unit 7b's path checks, which are not exit gate items.

Measured on 2026-10-07: 110 controls passed in 449 s (7 m 29 s) -- the
baseline, with the overlay build, 64 s; the final clean run 49 s; control 7
70 s, almost all of it the test's own 60-second bound; every other control
between 7 s and 27 s. The floor in ``verify-all-gates.sh`` is 110.

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
