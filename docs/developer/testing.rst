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

   **This page describes the state on 2026-11-07, after Phase 15's work
   units** (*Version Matrix, Performance and Hardening*,
   ``phases/PHASE-15-hardening.rst``; the phase's own sign-off is tier 1's and
   is recorded in ``STATUS.rst``, not here). Phase 01 wrote the page and
   installed the gates before there was code to hide behind them; Phases 02-14
   added a harness each; Phase 15 added the tool manifest verification, the
   version matrix, determinism, performance, the flakiness policy, the
   per-package mutation grading and its own harness. Only Linux x86-64 has run
   any of it. What remains open is collected in `What is not tested yet`_.

   Every number on this page carries its date and where it came from: a run
   recorded in a phase work log's sign-off, ``STATUS.rst``, or a command run
   for this page. A number dated 2026-08-29 is Phase 01's measurement of a
   much smaller tree, kept as the record of what was first seen; the build
   prints the current figure.

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
       ``.venv`` if needed, then runs all eleven stages: toolchain, font
       stack, Python, ``mvn clean verify`` (compile, every module's tests,
       package), artefact verification, formatting and static-analysis
       evidence, coverage/architecture/mutation gates, integration tests, the
       strict documentation build, SBOM and dependency scan, and the CI
       workflow and flakiness checks. ``11/11`` in 9353 s (156 min) at
       ``c9d037c``, tier 1's run of 2026-11-05 (``STATUS.rst``). PIT over the
       reactor alone took 105 min at 16 threads in Phase 15 unit 3's
       whole-reactor run. It was 87 s over Phase 01's tree.

   * - ``bash scripts/dev-verify.sh``
     - **The inner loop, never a gate.** Maps the change to the modules it can
       break and runs only their tests (``--dry-run`` shows the selection,
       ``--verify`` adds SpotBugs and JaCoCo, ``--mutation`` adds PIT over the
       changed classes and prints the per-package grading of that run).

   * - ``bash scripts/build.sh --only gates``
     - Just the JaCoCo, ArchUnit and PIT stage, against the classes an earlier
       build already produced -- PIT over the reactor included, so it is not
       quick. This is also exactly what the pull-request pipeline runs,
       through ``scripts/ci/test-gates.sh``.

   * - ``bash scripts/verify-all-gates.sh``
     - **Prove every gate still fails on the defect it exists to catch.** Runs
       all twenty-four falsifiability controls and exits non-zero if any
       control stops biting. The twenty-one before Phase 15 took 91 minutes
       four at a time on the 64-core development host (tier 1, ``c9d037c``,
       2026-11-05; ``tests`` 5454 s of it); Phase 15's three were measured
       alone -- ``manifest`` about 2 s, ``flakiness`` about 40 s,
       ``hardening`` 603 s -- and all twenty-four have not yet run together.
       Serial, at four cores or fewer, it is many hours. Run once per phase,
       at the exit gate, not as routine.

   * - ``bash scripts/verify-all-gates.sh --jobs N``
     - Up to ``N`` controls at once; ``1`` is serial, and so is the default at
       four cores or fewer. ``COMETGUI_GATE_JOBS=N`` does the same.

   * - ``bash scripts/verify-all-gates.sh --list``
     - The twenty-four controls, what each injects, and the command that
       proves it.

   * - ``bash scripts/verify-all-gates.sh --only NAME``
     - One control. Names: ``license``, ``workflows``, ``docs``,
       ``traceability``, ``sbom``, ``depscan``, ``pipeline``, ``quality``,
       ``shell``, ``tests``, ``provenance``, ``install``, ``params``,
       ``paramui``, ``workflow``, ``percolator``, ``results``, ``pdv``,
       ``limelight``, ``provenanceui``, ``e2e``, and since Phase 15
       ``manifest``, ``flakiness`` and ``hardening`` (``--list`` has them
       all). Repeatable, or comma-separated.

   * - ``bash scripts/verify-hardening-gates.sh``
     - Phase 15's own harness (the ``hardening`` control): the version matrix,
       the per-package mutation grading, the determinism suite and its written
       tolerance, two performance thresholds and the no-baseline verdict, the
       quarantine register, and a census of every Phase-15-owned pipeline step,
       each shown red on its defect in a ``git archive HEAD`` sandbox. About
       ten minutes; needs network, no spectra. ``--worktree`` grades the
       working tree's tracked files instead of ``HEAD``. See
       :ref:`dev-hardening-falsifiability`.

   * - ``bash scripts/ci/docs-build.sh``
     - The documentation gate on its own: both strict Sphinx builds. About 6 s.

   * - ``bash scripts/ci/traceability.sh``
     - The traceability report gate on its own, without Sphinx. About 1 s.
       Since Phase 15 unit 6 it also enforces ``R-TEST-07``'s review rule over
       the goldens (``scripts/matrix/check_goldens.py``).

   * - ``bash scripts/ci/flakiness-check.sh``
     - The flakiness policy (PHASE-15 gate item 6): red on any unconditional
       retry, any test disabled without an entry in
       ``scripts/ci/quarantine.toml``, and any entry naming a release-critical
       test. About 2 s; a step of the pull-request pipeline and of
       ``build.sh``'s ``workflows`` stage. ``--self-test`` (40 cases, about 40
       s) is the ``flakiness`` gate control. See :ref:`dev-testing-flakiness`.

   * - ``COMETGUI_MATRIX_FIXTURE=scratch/fixture bash scripts/matrix/run-matrix.sh``
     - The version matrix (PHASE-15 gate item 1): every offered build installed
       from upstream and exercised, every pair run over the named fixture, every
       other combination recorded by name. Opt-in; about two minutes. See
       :ref:`dev-testing-version-matrix`.

   * - ``bash scripts/matrix/run-matrix.sh --part determinism``
     - Determinism (PHASE-15 gate item 3), with the fixture as above; opt-in.
       See :ref:`dev-testing-determinism`.

   * - ``bash scripts/perf/run-performance.sh``
     - Performance (PHASE-15 gate item 4), graded against a runner profile;
       opt-in. See :ref:`dev-testing-performance`.

   * - ``bash scripts/ci/nightly-manifest-verify.sh``
     - Every pinned tool artefact checked against upstream (``R-TEST-08``);
       about 22 s and 170 MB. See :ref:`dev-manifest-verify`.

``scripts/verify-all-gates.sh`` is deliberately **not** a stage of
``scripts/build.sh``. Together they take about four hours on this host
(``STATUS.rst``), and by the owner's standing build-economy rule both run once
per phase, at the exit gate, by tier 1 -- not in the edit-compile loop, where
``scripts/dev-verify.sh`` and one control at a time (``--only NAME``) are the
tools.

Testing philosophy
==================

``specification.rst``, *Testing Strategy*, distinguishes seven kinds of test
and says plainly that unit testing alone is insufficient. Every kind now
exists on Linux x86-64; what is missing is other platforms and the CI half of
everything that needs spectra.

.. list-table::
   :header-rows: 1
   :widths: 22 30 48

   * - Kind
     - What the specification asks for
     - State on 2026-11-07

   * - Fast unit tests
     - Every local and CI build; no network, no native tools.
     - **Running**, in ``mvn verify``: 10 419 tests in twelve modules at
       ``a3f2ff5`` (every module's count in Phase 15 unit 1's sign-off), with
       the headless GUI suite below among them. Phase 01 had 54.

   * - Component / integration
     - Filesystem, process, parser and install boundaries, with controlled
       fixtures and fake executables.
     - **Running**, in the same ``mvn verify``: the process service and
       workflow engine against fake executables (Phases 03 and 08; the
       stand-in tools are also what the GUI tests run), the installer
       against a loopback artefact server (Phase 05).

   * - Real-tool integration
     - Real pinned Comet, Percolator, converter and PDV binaries on small
       real fixtures.
     - **Running on Linux.** Ordinary surefire classes named for it
       (``Real...``, 45 classes on 2026-11-07 by the flakiness check's count),
       in ``mvn verify``; and the version matrix over every Linux build
       (:ref:`dev-testing-version-matrix`). ``scripts/ci/integration-tests.sh``
       still guards only the ``*IT`` naming, which nothing uses.

   * - GUI tests
     - Drive JavaFX controls, verify UI state and validation.
     - **Running**, headless on Linux: the ``*UiTest`` classes in
       ``cometgui-app`` and the JavaFX tests of ``cometgui-ui``
       (`The headless JavaFX recipe`_),
       and Phase 14's Tier A end-to-end tier over them
       (:ref:`dev-e2e-harness`, opt-in).

   * - Packaged end-to-end
     - Start the packaged application in a clean environment and drive a real
       workflow.
     - **Linux only, opt-in, not in CI.** Phase 14's Tier B runs both
       end-to-end scenarios against the installed Linux archive; macOS and
       Windows packaging has never been executed (:ref:`dev-e2e-gaps`).

   * - Nightly / scientific regression
     - Larger real data, version matrices, determinism, performance.
     - **Real where no spectra are needed; waiting on** ``D-006`` **where they
       are.** Real: the tool manifest (:ref:`dev-manifest-verify`), the
       matrix's tools part, determinism's synthetic half and the performance
       suite's no-spectra part -- the last red by design on any runner
       without a profile, every GitHub runner among them
       (:ref:`dev-testing-performance`).
       Stubs that exit 70: the large dataset, the fixture halves of
       determinism and performance, the GUI tests, the Windows RAW test and
       the Limelight sandbox (``D-007``); see `What is not tested yet`_.
       Phase 15's work is not pushed yet, so GitHub's scheduled nightly
       still runs the stubs of ``fabbbcf``.

   * - Release acceptance
     - The exact installer and package artefacts that will be published.
     - **Partly real.** The no-test-bridge check (:ref:`dev-e2e-no-bridge`),
       the tool manifest, the documentation build and the SBOM are real
       release steps; packaging, checksums, the smoke test, the three
       end-to-end steps, signing and publishing are stubs waiting for
       Phase 16's release artefact and, for the end-to-end steps, ``D-006``'s
       CI fixture.

Two rules from ``CONTRIBUTING.rst`` apply to every test anyone adds:

* **A test that asserts "did not throw" is not a test.** Prove the value: the
  parsed field, the written file, the computed checksum, the rejected input,
  the exact error message.
* **Numeric targets come from the specification, not from taste.** Every
  threshold below cites the clause it comes from. A lower threshold must be
  documented with the untested risk, in the commit that lowers it.

The gates that exist today
==========================

The eleven build gates from Spotless to the CI definitions are wired into
``mvn verify`` or ``scripts/build.sh``; the five sections after them are
Phase 15's, run from their own scripts, the nightly and the gate harnesses
(the flakiness check is also a step of ``build.sh``'s ``workflows`` stage).
Where a section quotes a count from 2026-08-29 it is Phase 01's, over a tree a
fraction of today's size. Every
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

:Threshold: **>= 80% in every critical package, each graded on its own**
   (``D-015``), with a ``TIMED_OUT`` mutant counted as detected (``D-014``).
   Score = (``KILLED`` + ``TIMED_OUT``) / every mutation of the package;
   ``SURVIVED``, ``NO_COVERAGE`` and every other status count against it.
:Where the number comes from: ``R-TEST-02`` -- ">= 80% mutation score in those
   packages, with **no** surviving mutation that can disable checksum
   verification, invert a q-value comparison, drop a required output, suppress
   a validation error, pass an unsupported option to a tool, or leak a secret";
   the two owner decisions of 2026-11-05 (``DECISIONS.rst``) say how it is
   counted and over what.
:What a critical package is: the **Java package** of each class PIT mutates
   (inner classes folded into their outer class), for every class under a
   ``<targetClasses>`` entry of the parent ``pom.xml``. An entry such as
   ``org.cometgui.params.comet.*`` covers several Java packages (parser,
   writer, validation ...), and each is graded separately -- grading per entry
   or per module is what let the validation package sit at 37.5% inside a
   module at 88.7%.
:Target packages: the ``<targetClasses>`` list in ``pom.xml`` -- the domain,
   both parameter modules, provenance, results filtering, parsing and export,
   tools, install registry/verify/probe/download/archive/cache/manager, and
   workflow state -- 47 Java packages graded at Phase 15 unit 3's sign-off,
   all at or above 80%, the lowest ``tools.pdv.derived`` at 89.7%. A later
   phase adds its packages; it does not narrow the list. **Not in it:**
   ``org.cometgui.workflow.{engine,steps,storage}``, where the command
   builders and stage invalidation ``R-TEST-02`` names live. PIT over
   ``cometgui-workflow`` with them ran more than 2 h 45 min without finishing
   (against 20 min for ``workflow.state`` alone), so Phase 15 did not add them
   and escalated the cost to tier 1 (``handoffs/PHASE-15-worklog.rst``,
   *Blockers escalated*).
:Configured in: ``pom.xml`` (``pitest-maven`` 1.30.0 with
   ``pitest-junit5-plugin`` 1.2.3), the ``mutation`` profile, and
   ``cometgui.mutation.skip=false`` in each module with critical-package
   code: ``cometgui-domain``, ``cometgui-process``, ``cometgui-provenance``,
   ``cometgui-tools``, ``cometgui-install``, ``cometgui-params-comet``,
   ``cometgui-params-percolator``, ``cometgui-results`` and
   ``cometgui-workflow``.
:Runs with: ``bash scripts/build.sh`` (or ``--only gates``), which runs PIT over
   the reactor and then grades the reports with ``gates_mutation_grading``.

What the gates stage prints and fails on, in order:

* every module that switches the gate on has a ``mutations.xml`` with real
  mutations in it (``MISSING``, ``VACUOUS``), and no module without the switch
  has one (``UNEXPECTED``);
* one line per critical package, sorted: ``PASS`` or ``BELOW``, detected/all,
  the score, and the KILLED / TIMED_OUT / SURVIVED / NO_COVERAGE / other
  counts. A ``BELOW`` package is a counted failure, and its undetected mutants
  are listed under it (status, class, method, line, mutator);
* **every** ``TIMED_OUT`` mutant by name -- class, method, line, mutator -- so
  a hang that is really a broken test stays visible even though it counts as
  detected;
* **no mutations is a failure**, at two levels: a ``<targetClasses>`` entry
  that yields no mutation anywhere (``EMPTY``), and a Java package under one
  that compiles classes with code but carries no mutation (``NO MUTATIONS``,
  naming the package, its module and its classes);
* each module's total, marked **not graded**: it is printed for reading, and
  grades nothing;
* a module that compiles critical-package code with its switch off
  (``OFF``).

PIT's own ``mutationThreshold=80`` stays in the POM. It is module-wide, so it
cannot fail a build in which every package is at or above 80% (the module's
weighted average then is too), and it is not what the build is graded on.

PIT is not part of a plain ``mvn verify`` because it re-runs the suite once per
mutation. It is not optional either: the build script re-derives from the
compiled classes which modules ought to have the gate switched on, and fails a
module that has critical-package code with its switch off.

**Grading without running PIT.** ``bash scripts/build.sh --gates-pit-root DIR
[--gates-pit-modules M[,M]]`` runs only that grading, the same function, over
the reports a sandbox already holds. It refuses the repository itself, any
directory inside it outside ``_build/``, and every other option, reports a
``mutations.xml`` older than its module's sources or POM or the parent POM as
``STALE``, and ends with its own verdict line, never ``BUILD OK``.
``--gates-pit-modules`` narrows it to the modules a sandbox ran PIT in; a real
build grades every module. The ``tests`` control grades its sandbox this way.

**In the inner loop.** ``bash scripts/dev-verify.sh --mutation`` runs PIT over
the production classes the change touched (and the class a changed test is
named after), with ``-am``, writing its report under ``_build/dev-verify/``,
and prints this grading of that run -- over those classes only, which is not a
package's score and not the gate. It used PIT's incremental history until
Phase 15; pitest 1.30.0 refuses history unless a history plugin is installed,
and none is.

Forbidden survivors: the audit
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

:Date: 2026-11-06 (Phase 15, unit 4)
:Run audited: unit 3's whole-reactor PIT run of 2026-11-06 at ``d70206a``
   (16 threads): 76 undetected mutants in the 47 critical packages, 45
   ``SURVIVED`` and 31 ``NO_COVERAGE``.

The score is half of ``R-TEST-02``. The other half is that **no** surviving
mutation may be one that can, in the shipped product:

#. disable checksum verification;
#. invert a q-value comparison;
#. drop a required output;
#. suppress a validation error;
#. pass an unsupported option to a tool;
#. leak a secret.

**How a survivor is judged.** Read the mutated line in its method and ask what
the mutant does to the product, not to the test suite. A survivor is either
*equivalent* -- no input can tell it from the original, and the reason says
which invariant makes that so -- or it changes behaviour, and then the reason
says what changes (only a message's words; only a resource released on an
error path; ...) and why that is none of the six. "Probably harmless" is not a
reason. A survivor in one of the six classes is removed by a test that kills
it, or by a production fix where it exposes a defect -- never by an exclusion,
and the same rule is preferred for the rest wherever a test is reasonable.

**Result.** Two of the 76 were in a forbidden class, and both are killed:
``SecretToolCredentialStore.save`` not zeroing its copy of the secret once
``secret-tool`` had it (*leak a secret*), and ``ZipArchiveReader``'s walk of a
Zip64 extra field running past the field's end (*suppress a validation
error*: a malformed archive unpacked instead of refused). One more survivor
exposed a **defect**, now fixed: a Zip64 size field whose header claimed eight
bytes the extra field did not hold was read from the bytes after it -- the
entry's comment -- and the archive was unpacked. No survivor touched a q-value
comparison or a command builder. The other candidates -- the truncation and
step checks before the SHA-256, the export's row copy, the mzTab exporter's
self-checks -- are equivalent or still fail the export (table below).

Seventeen other survivors were killed by new tests: ``secret-tool``'s timeout,
standard-error cap and interrupt; the completion thread's interrupts; the
standard-input flush; the process-tree depth cap; the sort-file codec's empty
text list; the export copier's one read per buffer; PDV's interrupted shutdown
and figure-owner tie; Limelight's interrupted upload. **57 remain**, none in a
forbidden class: 28 equivalent, the rest a message's words, a resource
released on an error path, an interrupt restored on a thread nothing reads, or
a race no test can drive. Package names are under ``org.cometgui``; mutators
are PIT's, shortened (*boundary* is ``ConditionalsBoundaryMutator``, *void
call* ``VoidMethodCallMutator``, and so on).

.. list-table:: Undetected after unit 4 (lines as in the tree after it)
   :header-rows: 1
   :widths: 34 6 10 8 42

   * - Class and method
     - Line
     - Mutator
     - Status
     - Reason
   * - ``domain.tools.ToolVersion.compareTo``
     - 214
     - boundary
     - survived
     - Equivalent: the extra iteration compares ``componentAt(width)``, 0 against 0, for any two versions.
   * - ``install.archive.ExtractionGuard.checkedRelativePath``
     - 181
     - boundary
     - survived
     - Equivalent: a backslash at index 0 was already refused as an absolute name above, so ``>= 0`` and ``> 0`` agree.
   * - ``install.archive.ExtractionGuard.parentDirectoryOf``
     - 603
     - boundary
     - survived
     - Equivalent: a checked relative path never starts with ``/``, so ``lastSlash`` is never 0 (and ``resolve("")`` would be the destination anyway).
   * - ``install.archive.ZipArchiveReader.read``
     - 373
     - boundary
     - survived
     - Equivalent: a file channel never answers a non-empty buffer with 0, so ``< 0`` and ``<= 0`` agree.
   * - ``install.archive.ZipArchiveReader.zip64UncompressedSize``
     - 340
     - boundary
     - survived
     - Equivalent since the fix: a field header ending the extra field leaves no room for an eight-byte size, so ``<=`` and ``<`` both reach the refusal. Before the fix this survivor was the defect.
   * - ``install.download.HttpDownloader.copy``
     - 507
     - boundary
     - survived
     - Equivalent: ``declaredTotal >= 0`` vs ``> 0`` differ only at a declared length of 0, where ``size < 0`` is false either way. The SHA-256 check follows regardless.
   * - ``install.download.HttpDownloader.nextChunk``
     - 579
     - boundary
     - survived
     - Equivalent, as the line above: at a declared length of 0, ``onDisk < 0`` is false either way.
   * - ``install.cache.InstallPipeline.<init>``
     - 254
     - void call
     - survived
     - Equivalent in the shipped code: all eight steps, ``VERIFY_SHA256`` among them, are given actions on the lines above, so the guard cannot fire; its refusal is tested directly.
   * - ``install.archive.PkgPayloadReader.elementsOf``
     - 245
     - boundary
     - survived
     - Equivalent: ``NodeList.item(length)`` is null, not an element, so one walk too far adds nothing.
   * - ``install.archive.PkgPayloadReader.read``
     - 399
     - boundary
     - survived
     - Equivalent: as ``ZipArchiveReader.read``.
   * - ``install.archive.BoundedEntryStream.read``
     - 90
     - boundary
     - survived
     - Equivalent: subtracting a zero-byte read is a no-op.
   * - ``params.comet.validation.VariableModRules.checkSlot``
     - 204
     - boundary
     - survived
     - Equivalent: reached only for a requirement code other than 0 and 1, where ``> 0`` and ``>= 0`` agree; the finding is raised either way, this chooses its words.
   * - ``params.comet.parser.ParamsLineReader.declaration``
     - 137
     - boundary
     - survived
     - Equivalent: ``hash`` is never 0 here, a line starting with ``#`` being a comment already.
   * - ``params.comet.parser.ParamsLineReader.declaration``
     - 137
     - boundary
     - survived
     - Equivalent: one character cannot be both ``#`` and ``=``, so ``hash`` never equals ``equals``.
   * - ``tools.process.StartedProcess.joinUninterruptibly``
     - 281
     - void call
     - survived
     - Equivalent in effect: without ``join()`` the ``while (pump.isAlive())`` loop spins until the pump ends; same order, CPU only.
   * - ``results.filtering.store.DiskResultStore.close``
     - 365
     - void call
     - survived
     - Equivalent: ``closed`` is set first and every method calls ``requireOpen``; clearing the map only drops references.
   * - ``results.filtering.store.DiskResultStore.open``
     - 182
     - void call
     - no coverage
     - Error path: the raw table's channel failing to open just after its index did; the mapped index is then not released (a leak), nothing else.
   * - ``results.filtering.store.DiskResultStore.positionOf``
     - 333
     - boundary
     - survived
     - Equivalent: a sort file is checked to be a permutation when opened (``NOT_A_PERMUTATION``), so the row is found before ``position == rows``.
   * - ``results.filtering.store.DiskResultStore.readRow``
     - 434
     - math
     - no coverage
     - Message only (a row number in a refusal), and reached only if the raw table shrank after the same call checked it unchanged.
   * - ``results.filtering.store.SortFile.build``
     - 341
     - primitive return
     - survived
     - Equivalent in the product: ``DiskResultStore`` ignores the spill count; tests read it through the chunk-size overload.
   * - ``results.filtering.store.SortFile.build``
     - 407
     - void call
     - survived
     - Equivalent: the chunk is not read again after the last spill.
   * - ``results.filtering.store.SortFile.open``
     - 256
     - void call
     - no coverage
     - Error path: releases the mapped arena only when mapping or checking a local sort file throws; a leak, nothing else.
   * - ``results.filtering.store.SortFile.writeDescending``
     - 493
     - boundary
     - survived
     - Equivalent: position 0 has no predecessor, so its tie bit is never set and ``start >= 0`` stops where ``start > 0`` does.
   * - ``results.filtering.store.DiskIndex.open``
     - 161
     - void call
     - no coverage
     - Error path: as ``SortFile.open``.
   * - ``results.parser.ResultTableReader.closeQuietly``
     - 331
     - void call
     - no coverage
     - Diagnostic only: the suppressed exception of a refusal, when closing the reader also fails.
   * - ``results.filtering.store.CheckedFiles$Writer.<init>``
     - 108
     - negate
     - no coverage
     - Error path: closing the temporary file's channel when positioning it fails, which needs a negative header size; every caller passes a constant. A leak only.
   * - ``results.filtering.store.CheckedFiles$Writer.<init>``
     - 109
     - void call
     - no coverage
     - As the line above.
   * - ``results.export.ForwardCopier.fill``
     - 81
     - boundary
     - survived
     - Equivalent: the loop above it exits only when ``read != 0``.
   * - ``results.export.ResultExporter$TableCopy.writeTo``
     - 528
     - boundary
     - survived
     - Equivalent, so no output is dropped: ``pending`` is -1 or a row's offset, and a row starts after the header line (a table needs one), never at 0.
   * - ``tools.pdv.PdvMzTabExporter$Export.changed``
     - 1193
     - null return
     - no coverage
     - The export still fails (``NullPointerException`` instead of ``SOURCE_CHANGED``); needs the table to change mid-export. Nothing is accepted.
   * - ``tools.pdv.PdvMzTabExporter$Export.incomplete``
     - 1177
     - null return
     - no coverage
     - The export's check of its own mzTab, reached only by a writer defect; the mutant still fails it (``NullPointerException``).
   * - ``tools.pdv.PdvMzTabExporter$Export.indexRows``
     - 390
     - math
     - no coverage
     - Message only, in a self-check whose two counts come from one reader.
   * - ``tools.pdv.PdvMzTabExporter$Export.psmIdOf``
     - 1235
     - empty return
     - no coverage
     - Message only: how a refusal names a row whose table can no longer be read.
   * - ``tools.pdv.PdvMzTabExporter$Export.readBack``
     - 1117
     - math
     - no coverage
     - Message only (a line number), in the self-check above.
   * - ``tools.pdv.PdvMzTabExporter$Export.readBack``
     - 1130
     - math
     - no coverage
     - As the line above.
   * - ``tools.comet.PepXmlSearchHitReader$Walk.refused``
     - 728
     - boundary
     - survived
     - Equivalent: a StAX line number is -1 or at least 1; message only.
   * - ``tools.pdv.PngFigure.inflate``
     - 282
     - void call
     - survived
     - Equivalent: ``Inflater.end`` frees native memory only.
   * - ``tools.pdv.PngFigure.paeth``
     - 360
     - boundary
     - survived
     - Equivalent: ``pa == pb`` with ``pa <= pc`` implies ``a == b``, so either branch returns the same value.
   * - ``tools.pdv.derived.PdvController$Window.exited``
     - 708
     - void call
     - survived
     - Timer hygiene: after the window closes, a poll returns at its first check and a debounce finds nothing pending; no state changes.
   * - ``tools.pdv.derived.PdvController$Window.fail``
     - 678
     - void call
     - survived
     - As the line above, after a failure.
   * - ``tools.pdv.derived.PdvController$Window.poll``
     - 594
     - negate
     - no coverage
     - Defensive path: needs the controller's own JDK HTTP client to throw an unchecked exception. PDV is a viewer: none of the six.
   * - ``tools.pdv.derived.PdvController$Window.poll``
     - 591
     - void call
     - no coverage
     - Restores the interrupt on a poller thread being shut down; nothing reads it.
   * - ``tools.pdv.derived.PdvController$Window.poll``
     - 595
     - void call
     - no coverage
     - As ``poll`` line 594.
   * - ``tools.pdv.derived.PdvController$Window.poll``
     - 596
     - void call
     - no coverage
     - As ``poll`` line 594.
   * - ``tools.pdv.derived.PdvController$Window.schedule``
     - 747
     - void call
     - no coverage
     - Reached only if the controller closes between readiness and scheduling (a race); a selection's refusal words.
   * - ``tools.pdv.derived.PdvController$Window.send``
     - 766
     - negate
     - no coverage
     - Unreachable: every way out of ``READY`` abandons the pending selection first; message only.
   * - ``tools.pdv.derived.PdvController$Window.send``
     - 788
     - void call
     - no coverage
     - Restores the interrupt on the selection thread when ``shutdownNow`` interrupts a ``/select``; nothing reads it.
   * - ``tools.pdv.derived.PdvController$Window.stillStarting``
     - 641
     - false return
     - survived
     - Words at most: the attempt was replaced first, so the one extra ``/ready`` the poll then sends to the abandoned port is discarded by ``starting()``, re-checked under the lock -- except that its refusal overwrites ``lastReadyAnswer`` until the new attempt's next poll does.
   * - ``tools.pdv.derived.PdvController$Window.stillStarting``
     - 655
     - false return
     - survived
     - Equivalent in state: the window is already ``FAILED`` and its message written, so the extra ``/ready`` is discarded.
   * - ``tools.pdv.PdvSpectrumSupport.notMzMl``
     - 257
     - void call
     - survived
     - Equivalent: ``XMLStreamReader.close`` frees parser state; the byte array behind it is not a resource.
   * - ``tools.pdv.PdvMzTabExporter$Export$Rows.writeAll``
     - 966
     - math
     - no coverage
     - The export still fails (an index error instead of ``SOURCE_CHANGED``); needs the table to change mid-export.
   * - ``tools.limelight.LimelightUploadClient.describe``
     - 419
     - negate
     - survived
     - Message only: an ``IOException`` with a blank, non-null message; such messages never hold the key.
   * - ``tools.pdv.PdvMzTabRefs.columns``
     - 120
     - boundary
     - survived
     - Equivalent: the header's field 0 is ``PSH``, so a wanted column is never at index 0.
   * - ``tools.pdv.derived.PdvController.close``
     - 407
     - void call
     - survived
     - Resource hygiene: the HTTP client's own threads after the controller closed; no product state.
   * - ``tools.limelight.LimelightUploadClient$Counting$1.onError``
     - 362
     - void call
     - no coverage
     - Needs the file to fail mid-read during an upload; the client would then fail on its timeout instead of at once. Nothing is reported submitted.
   * - ``tools.comet.PepXmlSearchHitReader$Source.open``
     - 234
     - void call
     - no coverage
     - Diagnostic only: the suppressed exception of a refusal.
   * - ``tools.pdv.PdvMzTabExporter$Slots.nativeIdOf``
     - 1310
     - empty return
     - no coverage
     - Message only: a fallback name for a slot not in the map its slots come from.

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
``sphinx-build`` can exit 0 having written nothing. On 2026-11-07 (run for
this page): build 1, 55 source documents, 57 HTML pages; build 2, 89
documents, 92 pages (Phase 01: 49 and 31).

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

On 2026-11-07 (``bash scripts/ci/traceability.sh``, after Phase 15 unit 11's
map entries): ``100 R- rules, 80 AC- criteria, all mapped and verified``;
``52 automated, 10 partial, 10 planned, 8 human sign-off``, the evidence
naming 172 JUnit tests and 13 check files; 48 generator unit tests pass
(Phase 01's first run: 94 rules, 78 criteria, 0 automated). "Partial" means a
check exists and a ``planned`` entry names what is still missing; the
generator refuses a ``planned`` entry for a phase ``STATUS.rst`` records as
PASSED. The map has only an ``AC-`` half: an ``R-`` rule's owner is the phase
whose ``:Delivers:`` names it. The generated page is :doc:`traceability`; it
is produced during the documentation build so it cannot silently diverge from the code. Fix the
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

On 2026-11-07 (``scripts/ci/sbom.sh`` and ``scripts/ci/dependency-scan.sh``,
run for this page): 31 components -- 12 reactor modules, 5 declared
third-party dependencies, 14 resolved transitive ones -- JSON and XML agreeing
on all 31 purls; 20 coordinates scanned, none with an unaccepted
vulnerability, the allowlist empty, and the canary answered with 8 advisories
(Phase 01's first run: 26 components, 15 coordinates). The canary is the
interesting part -- see `Traps`_.

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

**What has run on GitHub** (the repository's Actions listing, read
2026-11-07). The pull-request pipeline has run once, on pull request #1 at
``686f9bc`` on 2026-09-02: success, all 15 steps green (``STATUS.rst``, *Phase
01 item 6*); every step it has gained since -- the flakiness check among them
-- has run only on this machine. The nightly has run on its schedule 68 times,
last on 2026-11-06 at ``fabbbcf``, red as the owner decided while its stubs
stand; Phase 15's commits are not pushed, so none of its real nightly jobs has
run there. The release workflow has never run.

**Where the local run runs.** On a runner, ``actions/checkout`` makes a fresh
checkout and every step builds in it. ``run-pipeline-locally.sh`` makes the
same checkout -- ``git archive HEAD`` into ``_build/ci-pipeline-sandbox``,
afresh each time -- reads the steps from *its* workflow files and runs each
step's own command from its root, with ``tools/``, ``scratch/``, ``.venv/`` and
``_build/m2repo`` linked in read-only. Until Phase 15 unit 10 it ran them in the
working tree, where the nightly matrix, determinism and performance steps
rewrote ``cometgui-*/target`` under any Maven build beside them, and where the
release no-test-bridge step inspected whatever archive an earlier build had
left in ``_build/package`` (on 2026-11-07, one from 2026-10-23) instead of
building one from ``HEAD``. The step scripts did not change: each finds its root
from its own location, so on a runner it builds in the checkout and here in
the sandbox. Uncommitted changes are not exercised, and the transcript says so.
Measured on 2026-11-07 (``--only pipeline`` at ``0c42108``): 31 steps in 1701
s, the link check 1110 s of it; the sandbox's first Maven build now lands in
the version-matrix step (110 s, against 38 s in the warm working tree) and the
no-test-bridge step builds the archive it inspects (54 s). A digest of every
file under the working tree's ``cometgui-*/target`` (10 008 files) was
byte-for-byte the same before and after, and a listing of every other
working-tree file (path, size, time) differed only in a file edited by hand
during the run.

.. _dev-manifest-verify:

Tool manifest verification -- ``R-TEST-08``
-------------------------------------------

:Checks: every artefact and companion record of ``manifests/tools.json``
   against upstream, and every tool repository's newest release against the
   tags the manifest records.
:Fails: exit 1 when upstream changed, exit 3 when it could not verify -- both
   red; exit 0 only when everything matched.
:Configured in: ``scripts/ci/manifest_verify.py`` (standard library only),
   ``scripts/ci/manifest/not-adopted.json``; the self-test is
   ``scripts/ci/manifest_verify_selftest.py``.
:Runs with: ``bash scripts/ci/nightly-manifest-verify.sh`` -- the nightly's
   ``manifest-verify`` job and a release step; ``--self-test`` is the
   ``manifest`` control of ``scripts/verify-all-gates.sh``.

CometGUI redistributes no tool (``D-008``): the user's own copy downloads each
one from upstream by pinned URL, size, SHA-256 and MD5. If upstream deletes a
release asset, re-tags a release or rebuilds an asset in place, every *new*
installation breaks and the project has no copy to fall back on. This job is
what notices before a user does. It exists because exactly that happened to
the PDV 2.6.0 pin while the specification was being written, and nothing
noticed.

**What it does.** Each distinct URL is downloaded once (the five PDV rows share
one, for instance), streamed into a temporary directory outside the repository,
hashed, and deleted; nothing downloaded is executed or extracted to disk. Its
size, SHA-256 and MD5 are compared in full with every record that pins it.
Where a record pins an archive member -- ``member`` and its digests on a
Percolator ZIP record, ``members`` on a companion -- the member is read out of
the archive in memory, by the reader the record's ``kind`` names (``ZIP``,
``DEB_PAYLOAD``: ``ar`` then ``data.tar.*``, ``PKG_PAYLOAD``: ``xar`` then the
gzip ``070707`` cpio), and compared too. Then, for each GitHub repository the
download URLs name, ``https://api.github.com/repos/<owner>/<repo>/releases`` is
read; drafts and prereleases are ignored, the rest are ordered by publication
date, and the newest must carry a tag the manifest records. On 2026-11-06 it
checked 38 records and 25 members over 28 downloads (170,152,439 bytes) and four
listings in 22 seconds, and every one matched.

**Reading a red.** Each verdict is its own block, naming the record (tool,
version, operating system, architecture, and the companion or member where
there is one), the URL, and expected against actual:

``RED (a) CHECKSUM OR SIZE CHANGED``
   The bytes at the pinned URL are not the bytes the manifest pins. Each of
   size, SHA-256 and MD5 is printed expected-against-actual, with
   ``<-- CHANGED`` on those that differ. Upstream rebuilt or replaced the
   asset under the same name. Do **not** update the digest to match: the
   product would then install a binary nobody has probed. Fetch it, find out
   what changed, and re-pin it through the same review a new version gets.
``RED (b) GONE UPSTREAM -- D-008 upstream availability failure``
   HTTP 404 or 410, or a redirect to nothing. Worded as the product's own
   ``ArtefactUnavailableException`` is: an availability failure, not a corrupt
   download -- the asset was removed or re-tagged, and every new install of
   that record fails until the manifest changes.
``RED (c) NEW UPSTREAM RELEASE``
   A repository's newest published release carries a tag the manifest does not
   record. It names the tag, its date, the tools and the recorded tags. Adopt
   it (a manifest change with its probes and evidence), or record it in
   ``scripts/ci/manifest/not-adopted.json``.
``COULD NOT VERIFY``
   No verdict could be reached for that check: a persistent transport failure,
   a rate-limited API, an archive the reader cannot open. This is never green.
   If there is also a red verdict, the exit status is 1 and both are printed.

**What is retried, and why that hides nothing.** Only transport failures, at
most three attempts, each from byte zero and each printed with its cause: a
refused, reset or aborted connection, a body shorter than its declared length,
a socket timeout, a DNS failure, HTTP 500/502/503/504. None of those is a fact
about the artefact. A 404, a 410, a redirect to nothing, a size or digest
mismatch, a missing member and a rate limit are **never** retried: each is a
verdict, or a fact a second request cannot change, and asking twice for a
verdict is how a flaky upstream gets averaged into a green one. The self-test
counts the requests to prove it.

**The not-adopted list.** ``scripts/ci/manifest/not-adopted.json`` records a
newer upstream release the project has decided not to adopt, one entry per
release: ``repository``, ``tag``, a ``reason`` of at least 40 characters a
reader can act on, and the ISO ``date`` it was decided. **Empty is the correct
state**, and it was empty when this job was written, because every
repository's newest release was already recorded. An entry is refused (exit 2)
if its reason is a placeholder, its date is in the future, the manifest
already records its tag, or it no longer matches an unrecorded newer release:
a stale entry is a place for the next release to hide. It is a reviewed change
to a committed file, never a flag.

**The self-test**, ``bash scripts/ci/nightly-manifest-verify.sh --self-test``,
touches no upstream host and never reads ``manifests/tools.json``. It builds
its own small artefacts -- a bare executable, ZIPs (one with a member named
``../my_build/src/percolator``, as the real 3.06.5 macOS archive has), a
``.deb``, a ``.pkg`` -- a synthetic manifest pinning them and synthetic releases
listings, serves them from a loopback HTTP server that redirects downloads as
GitHub does, and runs the real verifier against it, once per case, with one
defect injected each time. Each case is graded on its exit status, on the
exact lines it must print, and on the number of requests the server saw. The
cases: a clean world (verified, the shared URL fetched once); one byte
altered, the size changed, a pinned SHA-256 differing only in its last
character and an MD5 alone (each red (a) naming the record); 404, 410 and a
redirect with no ``Location`` (each red (b), in the availability words,
requested once); an unknown newer tag (red (c) naming it), and known tags out
of date order, a newer draft and a newer prerelease (each green); a connection
reset and a 503 that persist (could not verify after exactly three attempts),
one reset then the asset (green, the retry printed), a rate-limited API (could
not verify, requested once) and every host refusing (could not verify); a
wrong member pin in a ZIP, a traversal-named ZIP member, a ``.deb`` and a
``.pkg``, and a pinned member absent (each red (a)); every kind of red at once
(each named); the not-adopted list honoured, and refused with a placeholder
reason, a future date or a stale entry; a manifest with a non-https URL
refused; and the wrapper refusing an unknown option. Two more grade the
grader: the byte-altered case run **without** its defect, and run with a 404
instead, must each be reported as a harness failure -- otherwise a grader that
passes everything would look exactly like a verifier that catches everything.
30 cases, about two seconds.

**Why the self-test is its own control.** Every other ``scripts/ci/*.sh``
gate's self-test -- the SBOM, the dependency scan, the workflow checker -- is
a control of ``scripts/verify-all-gates.sh`` in its own right, graded on its
markers and a floor, and this one follows that pattern (``manifest``, PHASE-15
item 5, floor 30). The ``pipeline`` control was the other candidate, but it
grades the workflows' step classification, and it now runs the *real* job --
twice, nightly and release, about 170 MB each -- so it needs the network and
goes red whenever upstream changes. The self-test is loopback-only, takes
seconds, writes nothing but its own temporary directory, so it holds no
``gate_locks`` lock, and stays a deterministic proof that the job **can** go
red even on a day when upstream is down.

**What it does not cover.** The job reads the first 100 entries of each
releases listing, which GitHub orders newest first; a repository that
published more than 100 releases since the newest recorded one would have
older new tags left unnamed, though the newest would still be red. It holds
no credential (no workflow may), so it shares GitHub's limit of 60
unauthenticated API requests an hour per address with whatever else runs on
the runner's address; it uses four, and a refusal is "could not verify", not
green. The release workflow's Windows and macOS entries run it too, and have
never run.

.. _dev-testing-version-matrix:

Version matrix -- gate item 1 and ``R-TEST-07``
-----------------------------------------------

:Checks: every version/platform combination ``manifests/tools.json`` and the
   product name: each build offered on this host installed and exercised, every
   other combination recorded by name with its reason (PHASE-15 exit gate item
   1; specification, *Version matrix tests*).
:Fails: exit 1 when a combination offered here failed a check, differed from its
   reviewed golden or has no verdict, or when the derivation finds an
   unexplained gap, a stale reason, an unrecorded version difference, or a
   disagreement between the two readings of the manifest; exit 3 when the
   fixture part was requested and no fixture was named. Exit 0 only when
   everything this invocation is responsible for ``RUN-PASSED``.
:Configured in: ``scripts/matrix/matrix.py`` (the grading, standard library
   only), ``scripts/matrix/matrix-data.json`` (platforms, recorded reasons,
   advisories), the goldens under ``cometgui-app/src/test/resources/goldens/``,
   and three opt-in JUnit classes in ``cometgui-app``,
   ``org.cometgui.app.matrix.VersionMatrix{Census,Tools,Pairs}``.
:Runs with: ``bash scripts/matrix/run-matrix.sh`` -- the one entry point; the
   nightly's ``version-matrix`` job (``scripts/ci/nightly-version-matrix.sh``,
   ``--part tools``) and ``large-dataset`` job
   (``scripts/ci/nightly-large-dataset.sh``, ``--part pairs``, waiting on
   ``D-006``).

**How to run it.** ::

    COMETGUI_MATRIX_FIXTURE=scratch/fixture bash scripts/matrix/run-matrix.sh   # everything
    bash scripts/matrix/run-matrix.sh --part tools     # no spectra: what the nightly runs
    bash scripts/matrix/run-matrix.sh --self-test      # the grading, no Maven, no network

Add ``--offline`` where ``_build/m2repo`` already holds the build's
dependencies. The report is ``_build/version-matrix/matrix-report.txt`` (and
``.json``): one row per combination with its tools and versions, platform,
verdict, evidence and reason, then every version difference observed and the
advisory that explains it. On this machine the whole matrix takes about two
minutes (an empty-cache install of every Linux build -- about 120 MB from
GitHub, PDV being 103 MB of it -- the Comet and Percolator checks, and four
pair runs of about 15 s each); ``--part tools`` takes about one.

**The named fixture input.** The pair runs need spectra and take them as one
named input and nothing else: ``COMETGUI_MATRIX_FIXTURE`` (or ``--fixture``), a
directory whose ``*.mzML`` files are searched against its one ``*.fasta``.
There is no default. Without it the pair part is *refused*: its rows are
``UNVERIFIED`` with the words "REFUSED ... nothing was searched; this is not a
pass", and the exit status is 3. ``D-006``'s CI fixture is open, so on a runner
the ``large-dataset`` job exits 70 through ``stub-lib.sh`` naming ``D-006`` and
what it would need. Locally, ``scratch/fixture`` is the ``D-006`` local fixture;
it is never committed and never presented as the CI fixture. The fixture's
digest -- the SHA-256 of the sorted lines ``<sha256>  <name>`` of the files read
-- is computed twice, by the JUnit class and by ``matrix.py``, and must agree.
An mzML that arrived with CR bytes is searched as an LF copy (``D-006``'s
transit corruption); both digests are recorded.

**What runs, for each combination offered on this host.**

* *Install and probe*, the real code path: the product's Tool Manager
  (``ToolManagerWiring.toolManager``) over an empty cache, the real
  ``HttpDownloader`` against each record's pinned upstream URL, the real
  eight-step installer and three-stage probe. The installed executable must be
  the pinned bytes and every capability the probe's observation.
* *Comet*, each release: ``-p`` and ``-q`` write the committed fixtures under
  ``cometgui-params-comet/src/test/resources/fixtures/comet/`` byte for byte,
  and the product's ``CometParameterSchemaProvider`` reads the release's schema
  (mode from the probed ``COMPLETE_PARAMS_QUERY``, no drift).
* *Percolator*, each release, over CometGUI's own synthetic PIN: through the
  product's ``PercolatorCommands``, ``-X`` exactly when the probe observed
  ``XML_OUTPUT``; every requested output parsed by the product's readers and
  counted with its default filters beside the test's own counts; and the
  builder shown to leave ``-X`` out when no stage needs XML and when the build's
  probed set lacks ``XML_OUTPUT``.
* *PDV and the Limelight converter*: installed and identified. Their functional
  runs are the ``pdv`` and ``limelight`` harnesses', not repeated.
* *Every (Comet, Percolator) pair* over the fixture: the whole workflow through
  the product's run port, Limelight enabled. Exact invariants are asserted
  (success; one Comet invocation per spectrum file and one Percolator, each the
  installed executable at its digest, exit 0; ``-X`` exactly when probed; every
  output present; every recorded digest equal to the test's own; the merged
  PIN holding exactly the per-file PIN rows); the golden values are compared
  with the reviewed goldens.
* *A registered local Percolator 3.09* where ``scratch/percolator/3.09`` exists:
  recorded as "local binary, not a managed offer" (Linux has no managed 3.09,
  ``D-003``).

**What each verdict means.**

``RUN-PASSED``
   Exercised on this host; every check passed and every golden value equals
   the reviewed golden.
``RUN-FAILED``
   Exercised and a check failed or a golden differed -- or the combination is
   offered here and no row was recorded for it at all.
``UNAVAILABLE``
   The product does not offer it; ``matrix-data.json`` records the reason and
   the decision (``D-011`` for an Intel Mac's Comet, ``D-003`` for Linux 3.09,
   the platform matrix for Linux aarch64's Percolator and for Windows on ARM).
   A gap with no recorded reason, or a reason that covers no gap, is red.
``UNVERIFIED``
   Offered, not exercised here: a platform no machine here can run (the reason
   says what that platform's CI runner would run), a part not requested, the
   fixture part refused, or the Windows Thermo RAW smoke test (``AC-TST-11``,
   "when that feature ships"). Unverified is not passed.

**Derived, never typed.** The rows come from ``manifests/tools.json`` read by
``matrix.py`` as plain JSON *and* from the census the product writes
(``VersionMatrixCensus``: every record its own reader returned, and what
``ArtefactManifest.select`` offers on each platform it names). The two readings
must agree record for record, every record must be offered somewhere, the
host's offers must be what the Tool Manager lists, and every row the product
writes must be one the matrix derived. A manifest row the matrix ignored is
red, naming it.

**Goldens and the review rule (``R-TEST-07``).** Goldens live under
``cometgui-app/src/test/resources/goldens/<key>/`` -- ``<key>`` is the tool
pair, ``comet-<v>_percolator-<v>``, or the single tool for the synthetic-PIN
runs, ``percolator-<v>`` (``-local`` for a registered local binary) -- one file
per fixture digest and platform, ``fixture-<digest>.<platform>.json``. A golden
holds derived results only: counts, the selected-PSM count at the default
cut-off, decoy counts, the weights split count, and SHA-256 digests of the
sorted selected identifiers (PSM identifiers without the run directory Comet
prefixes them with) -- never spectrum or FASTA content. A ``D-006`` CI fixture
has its own digest and therefore adds goldens beside the local fixture's.
Every value is compared exactly: two complete runs gave identical values for
every key, Comet writes byte-identical PINs run to run at 4 threads and
Percolator byte-identical tables with ``--seed 1``, so nothing the values derive
from varies on one platform. That equality is per platform, which is why the
platform is part of the key; a tolerance, if one is ever needed, must carry a
written ``why``.

``R-TEST-07``'s rule is enforced by ``scripts/matrix/check_goldens.py``, which
``scripts/ci/traceability.sh`` runs on every pull request: each golden's
current SHA-256 must appear in the ``REVIEW.json`` beside it, with a date that
is not in the future, a reviewer and a written explanation of why the
scientific change is expected that is not a placeholder. A golden whose
content changes without a new record is red, however small the change; old
records stay as history. ``check_goldens.py --self-test`` (part of the
``traceability`` control) proves each of those red on a copy and the
committed tree green. To change a golden: run the matrix, inspect the
candidates ``python3 scripts/matrix/matrix.py write-goldens --report
_build/version-matrix --out _build/golden-candidates`` writes, copy the ones
whose change you can explain, and add the record.

**Version differences are advisories.** Every difference the matrix observes
between two versions -- Comet defaults, Percolator capabilities, Percolator
outputs over the synthetic PIN, pair results -- must be matched by an advisory
in ``matrix-data.json``, which is also listed in
:ref:`dev-version-matrix-advisories`; an unmatched difference is red. Nothing
is special-cased: no product or test logic branches on a version number.

**Shown red** (2026-11-06, each into production code or the grading, each
restored and ``sha256sum -c`` OK after): ``PercolatorCommands`` requesting
``-X`` whatever the build's capabilities -- both managed Percolator rows red at
"the same build without XML_OUTPUT ... -X left out" and the local 3.09 row red
at "-X requested exactly when XML_OUTPUT was observed"; ``PinMerger`` dropping
the second file's first row -- every pair red at "merged PIN rows against the
per-file PINs ==> expected: <6670> but was: <6669>" (the product's own run
still succeeded: the merge record counts what it read, not what it wrote);
``matrix.py``'s manifest reader skipping one Windows row -- red at "only in the
product [('percolator', '3.06.5', 'windows-x86-64')]".
``bash scripts/matrix/run-matrix.sh --self-test`` grades fifteen synthetic
cases, among them each of the derivation's reds.

**What it does not cover.** Only Linux x86-64 has been run. macOS and Windows
rows are ``UNVERIFIED`` until a runner of that platform runs the same entry
point; the Comet ``-p``/``-q`` fixtures exist only for ``linux-x86-64``, so a
first run elsewhere would fail on "no committed fixture" until captured there.
The pair runs have never run on a CI runner (``D-006``).

.. _dev-testing-determinism:

Determinism -- gate item 3
--------------------------

:Checks: that the same pinned run -- the same inputs, the same parameters, the
   same seed -- repeated N = 3 times through the product, each time into a fresh
   run directory, gives the same results artefact by artefact; that varying what
   should change nothing (Comet's and Percolator's thread counts) changes only
   what the tolerance below names; and that the comparison can see a change at
   all (a control with another seed must change Percolator's results). PHASE-15
   exit gate item 3; specification, *Version matrix tests* ("repeated-run
   determinism on a pinned fixture").
:Fails: exit 1 when any artefact of a repeat or a variation differs from the
   first repeat beyond what the tolerance below names -- the words name the
   part, the tool pair or build, the configuration, the artefact, the file and
   the line, row and column, attribute or field -- when the control changed
   nothing, when a varied setting never reached the tool, when a run wrote a
   file no artefact names, when a planned run is missing, or when this
   section's table and the data disagree; exit 3 when the fixture half was
   requested and no fixture was named. Exit 0 only when everything agrees.
:Configured in: ``scripts/matrix/determinism-data.json`` (the runs and the
   per-artefact tolerance), ``scripts/matrix/determinism.py`` (the comparison,
   standard library only), and two opt-in JUnit classes in ``cometgui-app``,
   ``org.cometgui.app.matrix.Determinism{Synthetic,Pairs}``, which share the
   version matrix's support and its run-port helper.
:Runs with: ``bash scripts/matrix/run-matrix.sh --part determinism`` (both
   halves) or ``--part determinism-synthetic`` (no spectra); the nightly's
   ``determinism`` job (``scripts/ci/nightly-determinism.sh``, the synthetic
   half, real) and ``determinism-fixture`` job
   (``scripts/ci/nightly-determinism-fixture.sh``, waiting on ``D-006``).

**How to run it.** ::

    COMETGUI_MATRIX_FIXTURE=scratch/fixture bash scripts/matrix/run-matrix.sh --part determinism
    bash scripts/matrix/run-matrix.sh --part determinism-synthetic   # no spectra: what the nightly runs
    python3 scripts/matrix/determinism.py --self-test                # the comparison, no Maven
    python3 scripts/matrix/determinism.py check-doc                  # this section against the data

The report is ``_build/determinism/determinism-report.txt`` (and ``.json``):
every run against the first repeat, artefact by artefact, with its verdict --
``identical``, ``within tolerance`` (naming the only fields that differed),
``differs, as the control requires``, or ``DIFFERS`` with where. Every run's
whole directory stays under ``_build/determinism/runs/`` (about 300 MB for the
fixture half) so a difference can be read. The fixture is the version
matrix's one named input, ``COMETGUI_MATRIX_FIXTURE``; without it the fixture
half is refused (exit 3, never green) and the synthetic half still runs.

**What runs.** Both halves install every build from its pinned upstream URL into
an empty cache through the product's Tool Manager, as the version matrix does.

* *Synthetic* (no spectra): every Percolator offered here (3.07.1 and 3.06.5 on
  Linux x86-64) over CometGUI's own committed synthetic PIN, each run in a fresh
  directory with a fresh copy of the PIN, the command built by the product's
  ``PercolatorCommands`` from ``PercolatorSettings`` as the workflow maps them,
  ``-X`` where the probe observed ``XML_OUTPUT``.
* *Fixture*: the whole Comet -> Percolator run through the product's run port
  (``RunWiring.port``, ``WorkflowRunPort``, ``CometWorkflow``), each run in a
  *fresh project* so that it is a new run in a new run directory and never the
  session's retry of its last one; Limelight enabled, so a stage needs XML.

.. list-table:: The runs (``configurations`` in the data)
   :header-rows: 1
   :widths: 20 12 10 10 10 38

   * - Configuration
     - Halves
     - Comet threads
     - Percolator seed
     - Percolator threads
     - Role
   * - ``repeat-1``, ``repeat-2``, ``repeat-3``
     - both; every offered build and pair
     - 4
     - 1
     - 3
     - the pinned run, N = 3 times; ``repeat-1`` is what everything is compared with
   * - ``comet-threads-1``
     - fixture, default pair
     - 1
     - 1
     - 3
     - a variation that must change no result: every protein searched by one thread
   * - ``comet-threads-0``
     - fixture, default pair
     - 0
     - 1
     - 3
     - the same: Comet's own default, one thread per core (64 here)
   * - ``percolator-threads-1``
     - both
     - 4
     - 1
     - 1
     - the same: the folds trained one after another
   * - ``percolator-threads-8``
     - synthetic
     - (none)
     - 1
     - 8
     - the same: more threads than folds
   * - ``control-seed-2``
     - both
     - 4
     - 2
     - 3
     - the control: another seed draws other folds, so Percolator's results must
       change and Comet's must not

The default pair is Comet 2026.03.0 with Percolator 3.07.1, the product's own
choice for a Limelight-enabled run. The variations run for the default pair
alone; the repeats for every pair (four on Linux x86-64). A variation must show
its setting reached the tool (``carries``: the varied value appears in
``comet.params`` and the pepXML, in ``percolator-settings.json``, the argument
array and provenance), or it is red: an unvaried variation proves nothing.

What Comet and Percolator vary, read from their source
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Read at the pinned tags, Comet ``v2026.03.0`` (and ``v2026.02.2``) from
``github.com/UWPR/Comet`` and Percolator ``rel-3-07-01`` (and ``rel-3-06-05``)
from ``github.com/percolator/percolator``. Paths are within each tag's tree.

*Comet threads.* In a direct FASTA search each protein is one job of the
thread pool (``CometSearch/CometSearch.cpp:1214-1218``), so one spectrum's
candidates are scored by several threads in whatever order they finish. Nothing
the output shows depends on that order: a candidate's xcorr is summed by one
thread in a fixed loop and rounded to three decimals before any shared state is
touched (``:5222``); the per-query histogram the E-value is fitted to takes
integer increments under the query's own lock (``:5224``, ``:5251``); a
candidate that merely ties the last stored score no longer evicts it by arrival
order -- the tie is broken by peptide string (``:5643-5697``, the comment there
records the earlier arrival-order defect); the stored candidates are sorted by
xcorr, then peptide, then modification sites (``CometPostAnalysis.cpp:400``,
``:1200-1229``); the E-value is fitted per query (``:1567-1640``), padding a
small histogram from a fixed table (``CometDecoys.h:25``); and the queries are
sorted by scan, then charge, before any writer runs
(``search/Pipeline.cpp:198``, ``search/SearchUtils.h:57-62``). No random number
is drawn anywhere in the search (``rand``, ``srand``, ``shuffle``, ``mt19937``
and ``<random>`` occur only in comments), and internal decoys are reversals,
not shuffles (``CometSearch.cpp:3923``). The numbers are printed at fixed
precision (``CometWritePepXML.cpp:832-849``, ``CometWritePercolator.cpp:166-237``).
So the PIN and the pepXML must be identical whatever the thread count, apart
from what Comet writes about the run itself: the date (``CometWritePepXML.cpp:68-69``,
``:144``), the paths it resolves (``:148``, ``:150``, ``:190``), every
parameter as configured -- ``num_threads`` among them, so a thread variation
shows there (``:300-304``) -- and its standard output's dates and timings
(``search/Pipeline.cpp:91``, ``:248``, ``:280``). The PIN's ``SpecId`` is built
from the ``-N`` base (``CometWritePercolator.cpp:121-125``), which the product
passes as the absolute output base inside the run directory.

*Percolator's randomness.* One process-wide Park-Miller generator
(``PseudoRandom.cpp:20-27``), seeded by ``--seed``, default 1
(``Caller.cpp:229-232``, ``:530-531``). It assigns PSMs to the three
cross-validation folds after a deterministic sort (``Scores.cpp:396-455``:
``:412`` sorts by a hash of file and scan, ``:428`` draws the fold); the other
draws are switched off at the product's settings (subset training, the RESET
algorithm) or are mix-max bootstrap draws from the same generator in a fixed
order. The SVM starts from zero weights (``CrossValidation.cpp:333-335``). The
product always passes the seed, so the same seed draws the same folds; another
seed draws others -- which is what the control shows. Rel-3-06-05 has the same
generator and default but sorts by scan, mass and charge before drawing
(``Scores.cpp:384``), so its folds, and therefore its results, differ from
3.07.1's for the same seed (the version matrix's advisory).

*Percolator's threads.* ``--num-threads`` sets OpenMP's team size
(``Caller.cpp:310-312``, ``:946-947``) for three loops over folds and
(cpos, cneg) pairs (``CrossValidation.cpp:297``, ``:371``, ``:411``); each
iteration writes its own slot, no reduction or shared accumulator exists, the
generator is not called inside them, the SVM uses Percolator's own
single-threaded BLAS, and the folds are merged in a fixed order
(``Scores.cpp:225-238``). Every value Percolator prints is therefore the same
at any thread count -- and measured, it is: every score, q-value, PEP and
weight. What is *not* fixed is the order of rows Percolator's final sort cannot
separate: ``std::sort`` -- not stable -- on (score, scan, expMass, label)
(``Scores.cpp:44-46``, ``:241-242``). PSMs of one spectrum whose features are
identical (positional isomers of an oxidised methionine, ranks of one scan)
score exactly alike and tie on all four keys. Measured: at ``--num-threads 1``
against 3, Percolator 3.07.1 moved such tied rows at 84 row positions of the
four tables (30, 24, 24 and 6 of 3886, 2973, 2784 and 2356 rows) and the
matching ``<psm>`` and ``<peptide>`` elements of the pout XML -- each table the
same set of rows, every value identical; at one thread count, repeats never
did. Why the input order of that unstable sort follows the
thread count was not traced to a line; the tolerance does not depend on it, as
it allows only the order *within* a block of rows tied on what the table shows
of those keys.

*Percolator's output.* Tables at the stream's default six significant digits
(``ResultHolder.cpp:42-52``), weights at fixed precision with a static header
(``CrossValidation.cpp:464-500``), pout XML with no timestamp but its own
command line (``XMLInterface.cpp:440``, ``Caller.cpp:98-105``); the standard
error carries the start time and timings (``Caller.cpp:77-78``, ``:732-733``,
``:1008``).

*No numeric tolerance.* None of the above lets accumulation order reach a
printed number: Comet rounds each candidate's score before it is shared and
adds integers to the histogram; Percolator's parallel loops do not reduce into
shared floating point. So no artefact is compared with a numeric tolerance --
every number must be equal, as text. (The specification's *tolerant scientific
metrics* are for larger datasets *across platforms*; determinism is one
platform, one build.)

*What the product varies.* Every run is a new run directory named from the
clock (``ClockRunIdSource``), and that path is written into the content: Comet
writes it into the PIN's ``SpecId`` and the pepXML, Percolator copies it into
its tables and pout XML. Provenance records instants, durations, file
modification times, and the digests and sizes of files whose content carries
that path. The engine runs ready steps concurrently, so the event log's order
is the scheduler's.

The tolerance
~~~~~~~~~~~~~

Each artefact is compared with the same artefact of ``repeat-1`` by one rule:
``bytes`` (byte-identical); ``text``, ``tsv``, ``pout``, ``json`` (identical,
line for line, row for row or field for field, after removing exactly the
fields named); ``jsonl-unordered`` (the same, as a set of events); ``record``
(the values the run's record holds, exactly). A field marked *only when ...* is
removed only for a run whose configuration varies that setting. Every file a
run writes must belong to an artefact, or the run is red. ``determinism.py
check-doc`` requires this table and the data to name the same artefacts, with
the same rule and the same fields -- one more or one fewer on either side is
red -- and this section to state the data's N.

.. determinism-tolerance-table-start

.. list-table:: The tolerance, per artefact (machine-readable: ``scripts/matrix/determinism-data.json``)
   :header-rows: 1
   :widths: 16 12 10 62

   * - Artefact
     - Written by
     - Compared
     - What may differ, why, and where the source says so
   * - ``fixture:comet-params``
       (``parameters/comet.params``)
     - CometGUI
     - ``text``
     - the product's parameter writer is deterministic: the same model writes the same bytes.

       *num_threads*, only when Comet's thread count is varied: the varied Comet setting itself (source: CometGUI CometParameters num_threads).
   * - ``fixture:percolator-settings``
       (``parameters/percolator-settings.json``)
     - CometGUI
     - ``text``
     - the product's archived Percolator settings: the same settings write the same bytes.

       *thread-count*, only when Percolator's thread count is varied: the varied Percolator setting itself (source: CometGUI PercolatorSettingsFile).

       *random-seed*, only when the seed is varied (the control): the control's varied seed (source: CometGUI PercolatorSettingsFile).
   * - ``fixture:comet-pepxml``
       (``outputs/comet/*.pep.xml``)
     - Comet
     - ``text``
     - each query is scored and its candidates ranked by one deterministic rule whatever thread scores which protein: a candidate's xcorr is summed by one thread and rounded to 3 decimals, the per-query histogram takes integer adds under the query's lock, a tie for the last stored place is broken by peptide string, candidates are sorted by xcorr then peptide then modification sites, queries by scan then charge before anything is written, the E-value is fitted per query, and no random number is drawn (source: Comet v2026.03.0 CometSearch/CometSearch.cpp:1214-1218, :5222, :5224-5284, :5643-5697; CometPostAnalysis.cpp:400, :1200-1229, :1567-1640; search/Pipeline.cpp:198; search/SearchUtils.h:57-62; CometDecoys.h:25).

       *msms_pipeline_analysis date*: Comet writes the local time of the search (source: Comet v2026.03.0 CometSearch/CometWritePepXML.cpp:68-69, :144).

       *summary_xml and search_summary base_name: the run's own directory*: the run's own directory: every run is a new run in a new directory (the product never reuses one), and the path is written into the content (source: Comet v2026.03.0 CometSearch/CometWritePepXML.cpp:148, :190 (the -N base, resolved)).

       *parameter num_threads*, only when Comet's thread count is varied: Comet writes every parameter as configured, num_threads among them (source: Comet v2026.03.0 CometSearch/CometWritePepXML.cpp:300-304; Comet.cpp:283-287).
   * - ``fixture:comet-pin``
       (``outputs/comet/*.pin``)
     - Comet
     - ``tsv``
     - as comet-pepxml: the same queries in the same order with the same scores; the PIN carries no date, version or parameter.

       *SpecId: the run's own directory*: the run's own directory: every run is a new run in a new directory (the product never reuses one), and the path is written into the content; Comet builds SpecId from the -N base (source: Comet v2026.03.0 CometSearch/CometWritePercolator.cpp:121-125; Comet.cpp:140-146).
   * - ``fixture:merged-pin``
       (``inputs/pin/merged.pin``)
     - CometGUI (PinMerger)
     - ``tsv``
     - every row of every per-file PIN in input order, byte for byte (PinMerger).

       *SpecId: the run's own directory*: the run's own directory: every run is a new run in a new directory (the product never reuses one), and the path is written into the content; Comet builds SpecId from the -N base (source: Comet v2026.03.0 CometSearch/CometWritePercolator.cpp:121-125; Comet.cpp:140-146).
   * - ``fixture:percolator-psms``
       (``outputs/percolator/psms.tsv``)
     - Percolator
     - ``tsv``
     - one seeded generator (seed 1 unless --seed) draws the cross-validation folds before training, nothing random is drawn inside the parallel training loops, the folds are merged in a fixed order and the values are printed at the stream's fixed default precision (source: Percolator rel-3-07-01 src/PseudoRandom.cpp:20-27; Caller.cpp:229-232, :530-531; Scores.cpp:396-455; CrossValidation.cpp:297, :371, :411; Scores.cpp:225-238).

       *PSMId: the run's own directory*: the run's own directory: every run is a new run in a new directory (the product never reuses one), and the path is written into the content; Percolator copies Comet's SpecId (source: Percolator rel-3-07-01 src/ResultHolder.cpp:42-52).

       *order of rows tied on Percolator's sort keys*, only when Percolator's thread count is varied: Percolator sorts its results with std::sort, which is not stable, on (score, scan, expMass, label); PSMs of one spectrum with identical features (positional isomers of an oxidised methionine, ranks of one scan) score exactly alike and stay unordered. Measured: at --num-threads 1 against 3 such rows change places, their values identical; repeats at one thread count never do. Rows are compared as a set only within a block of consecutive rows equal in score, q-value, PEP and scan number (source: Percolator rel-3-07-01 src/Scores.cpp:44-46 (operator> keys), :241-242 (postMergeStep: sort, greater<ScoreHolder>); ISO C++ [alg.sort]: std::sort is not stable).
   * - ``fixture:percolator-peptides``
       (``outputs/percolator/peptides.tsv``)
     - Percolator
     - ``tsv``
     - one seeded generator (seed 1 unless --seed) draws the cross-validation folds before training, nothing random is drawn inside the parallel training loops, the folds are merged in a fixed order and the values are printed at the stream's fixed default precision (source: Percolator rel-3-07-01 src/PseudoRandom.cpp:20-27; Caller.cpp:229-232, :530-531; Scores.cpp:396-455; CrossValidation.cpp:297, :371, :411; Scores.cpp:225-238).

       *PSMId: the run's own directory*: the run's own directory: every run is a new run in a new directory (the product never reuses one), and the path is written into the content; Percolator copies Comet's SpecId (source: Percolator rel-3-07-01 src/ResultHolder.cpp:42-52).

       *order of rows tied on Percolator's sort keys*, only when Percolator's thread count is varied: Percolator sorts its results with std::sort, which is not stable, on (score, scan, expMass, label); PSMs of one spectrum with identical features (positional isomers of an oxidised methionine, ranks of one scan) score exactly alike and stay unordered. Measured: at --num-threads 1 against 3 such rows change places, their values identical; repeats at one thread count never do. Rows are compared as a set only within a block of consecutive rows equal in score, q-value, PEP and scan number (source: Percolator rel-3-07-01 src/Scores.cpp:44-46 (operator> keys), :241-242 (postMergeStep: sort, greater<ScoreHolder>); ISO C++ [alg.sort]: std::sort is not stable).
   * - ``fixture:percolator-decoy-psms``
       (``outputs/percolator/decoy-psms.tsv``)
     - Percolator
     - ``tsv``
     - one seeded generator (seed 1 unless --seed) draws the cross-validation folds before training, nothing random is drawn inside the parallel training loops, the folds are merged in a fixed order and the values are printed at the stream's fixed default precision (source: Percolator rel-3-07-01 src/PseudoRandom.cpp:20-27; Caller.cpp:229-232, :530-531; Scores.cpp:396-455; CrossValidation.cpp:297, :371, :411; Scores.cpp:225-238).

       *PSMId: the run's own directory*: the run's own directory: every run is a new run in a new directory (the product never reuses one), and the path is written into the content; Percolator copies Comet's SpecId (source: Percolator rel-3-07-01 src/ResultHolder.cpp:42-52).

       *order of rows tied on Percolator's sort keys*, only when Percolator's thread count is varied: Percolator sorts its results with std::sort, which is not stable, on (score, scan, expMass, label); PSMs of one spectrum with identical features (positional isomers of an oxidised methionine, ranks of one scan) score exactly alike and stay unordered. Measured: at --num-threads 1 against 3 such rows change places, their values identical; repeats at one thread count never do. Rows are compared as a set only within a block of consecutive rows equal in score, q-value, PEP and scan number (source: Percolator rel-3-07-01 src/Scores.cpp:44-46 (operator> keys), :241-242 (postMergeStep: sort, greater<ScoreHolder>); ISO C++ [alg.sort]: std::sort is not stable).
   * - ``fixture:percolator-decoy-peptides``
       (``outputs/percolator/decoy-peptides.tsv``)
     - Percolator
     - ``tsv``
     - one seeded generator (seed 1 unless --seed) draws the cross-validation folds before training, nothing random is drawn inside the parallel training loops, the folds are merged in a fixed order and the values are printed at the stream's fixed default precision (source: Percolator rel-3-07-01 src/PseudoRandom.cpp:20-27; Caller.cpp:229-232, :530-531; Scores.cpp:396-455; CrossValidation.cpp:297, :371, :411; Scores.cpp:225-238).

       *PSMId: the run's own directory*: the run's own directory: every run is a new run in a new directory (the product never reuses one), and the path is written into the content; Percolator copies Comet's SpecId (source: Percolator rel-3-07-01 src/ResultHolder.cpp:42-52).

       *order of rows tied on Percolator's sort keys*, only when Percolator's thread count is varied: Percolator sorts its results with std::sort, which is not stable, on (score, scan, expMass, label); PSMs of one spectrum with identical features (positional isomers of an oxidised methionine, ranks of one scan) score exactly alike and stay unordered. Measured: at --num-threads 1 against 3 such rows change places, their values identical; repeats at one thread count never do. Rows are compared as a set only within a block of consecutive rows equal in score, q-value, PEP and scan number (source: Percolator rel-3-07-01 src/Scores.cpp:44-46 (operator> keys), :241-242 (postMergeStep: sort, greater<ScoreHolder>); ISO C++ [alg.sort]: std::sort is not stable).
   * - ``fixture:percolator-weights``
       (``outputs/percolator/weights.txt``)
     - Percolator
     - ``bytes``
     - the learned weights of each fold, written at fixed precision with a static header: no path, no time (source: Percolator rel-3-07-01 src/CrossValidation.cpp:464-500).

       Nothing may differ.
   * - ``fixture:percolator-pout``
       (``outputs/percolator/pout.xml``)
     - Percolator
     - ``pout``
     - as the tables; pout XML carries no timestamp.

       *process_info command_line*: Percolator echoes its argument array, which names the run's own directory; the argument array itself is compared in provenance-json (source: Percolator rel-3-07-01 src/XMLInterface.cpp:440; Caller.cpp:98-105).

       *psm_id: the run's own directory*: the run's own directory: every run is a new run in a new directory (the product never reuses one), and the path is written into the content; Percolator copies Comet's SpecId (source: Percolator rel-3-07-01 src/Scores.cpp:62-101).

       *order of <psm> and <peptide> elements tied on Percolator's sort keys*, only when Percolator's thread count is varied: Percolator sorts its results with std::sort, which is not stable, on (score, scan, expMass, label); PSMs of one spectrum with identical features (positional isomers of an oxidised methionine, ranks of one scan) score exactly alike and stay unordered. Measured: at --num-threads 1 against 3 such rows change places, their values identical; repeats at one thread count never do. Elements are compared as a set only within a block of consecutive rows equal in svm_score, q_value, pep, exp_mass and scan number; a peptide's psm_ids, listed in that same order, are compared as a set (source: Percolator rel-3-07-01 src/Scores.cpp:44-46 (operator> keys), :241-242 (postMergeStep: sort, greater<ScoreHolder>); ISO C++ [alg.sort]: std::sort is not stable).
   * - ``fixture:comet-log``
       (``logs/comet-*.log``)
     - Comet (captured by CometGUI)
     - ``text``
     - Comet's standard output, captured line by line by the product.

       *log line time*: the product stamps every captured line with the instant it was read (source: CometGUI StageLogFormat (cometgui-process)).

       *the run's own directory*: the run's own directory: every run is a new run in a new directory (the product never reuses one), and the path is written into the content (source: CometGUI RunLayout (runs/<run id>/); Comet -N base Comet v2026.03.0 Comet.cpp:140-146).

       *Search start / Search end / Run stats*: Comet prints the wall-clock date, elapsed time, rate and memory (source: Comet v2026.03.0 CometSearch/search/Pipeline.cpp:91, :248, :280).

       *stage duration*: the product records how long the process ran (source: CometGUI StageLogFormat).
   * - ``fixture:percolator-log``
       (``logs/percolator.log``)
     - Percolator (captured by CometGUI)
     - ``text``
     - Percolator's standard error, captured line by line by the product.

       *log line time*: the product stamps every captured line with the instant it was read (source: CometGUI StageLogFormat (cometgui-process)).

       *the run's own directory*: the run's own directory: every run is a new run in a new directory (the product never reuses one), and the path is written into the content (source: CometGUI RunLayout (runs/<run id>/); Comet -N base Comet v2026.03.0 Comet.cpp:140-146).

       *Started*: Percolator prints the wall-clock start time (source: Percolator rel-3-07-01 src/Caller.cpp:78).

       *cpu and wall-clock seconds*: Percolator prints how long reading and processing took (source: Percolator rel-3-07-01 src/Caller.cpp:732-733, :1008).

       *stage duration*: the product records how long the process ran (source: CometGUI StageLogFormat).

       *--num-threads value*, only when Percolator's thread count is varied: the varied Percolator setting itself, echoed wherever the command is recorded (source: CometGUI PercolatorRun.values / PercolatorCommands (the option the product passes); Percolator rel-3-07-01 src/Caller.cpp:310-312).

       *--seed value*, only when the seed is varied (the control): the control's varied seed, echoed wherever the command is recorded (source: Percolator rel-3-07-01 src/Caller.cpp:229-232, 530-531).
   * - ``fixture:provenance-json``
       (``provenance/provenance.json``)
     - CometGUI
     - ``json``
     - the provenance record: every tool's name, version, path and digests, every argument array, every input's digests, every output's size and digests, the settings.

       *run identifier, times and durations*: the run identifier is taken from the clock, and every start, end and duration is a measurement (source: CometGUI ClockRunIdSource, ManifestWriter).

       *digests of the run's own files*: a digest of a file whose content carries the run's own directory differs with it; each digest of a file under the run directory is replaced by the name of the file it is the digest of (the files themselves are compared above). Digests of the inputs and of the tools, outside the run directory, are compared as recorded (source: CometGUI provenance FileRecord, ToolRecord; this suite's digest_labels).

       *sizes of the run's own files*: a file whose content carries the run's own directory is longer or shorter with that path; the file itself is compared above, so its recorded size is dropped. Sizes of the inputs, outside the run directory, are compared as recorded (source: CometGUI provenance FileRecord (sizeBytes)).

       *the run's own directory*: the run's own directory: every run is a new run in a new directory (the product never reuses one), and the path is written into the content (source: CometGUI RunLayout).

       *--num-threads value*, only when Percolator's thread count is varied: the varied Percolator setting itself, echoed wherever the command is recorded (source: CometGUI PercolatorRun.values / PercolatorCommands (the option the product passes); Percolator rel-3-07-01 src/Caller.cpp:310-312).

       *percolator.thread-count*, only when Percolator's thread count is varied: the varied setting, recorded (source: CometGUI PercolatorProvenance).

       *--seed value*, only when the seed is varied (the control): the control's varied seed, echoed wherever the command is recorded (source: Percolator rel-3-07-01 src/Caller.cpp:229-232, 530-531).

       *percolator seed settings*, only when the seed is varied (the control): the control's seed, recorded (source: CometGUI PercolatorProvenance, EffectiveSeed).
   * - ``fixture:provenance-rst``
       (``provenance/provenance.rst``)
     - CometGUI
     - ``text``
     - the provenance record rendered for people: the same content as provenance-json, read with each list-table row on one line.

       *digests of the run's own files*: a digest of a file whose content carries the run's own directory differs with it; each digest of a file under the run directory is replaced by the name of the file it is the digest of (the files themselves are compared above). Digests of the inputs and of the tools, outside the run directory, are compared as recorded (source: CometGUI provenance FileRecord, ToolRecord; this suite's digest_labels).

       *the run's own directory*: the run's own directory: every run is a new run in a new directory (the product never reuses one), and the path is written into the content (source: CometGUI RunLayout).

       *run identifier, instants and durations*: the run identifier is taken from the clock, and every start, end and duration is a measurement (source: CometGUI ProvenanceRstWriter).

       *sizes of the run's own files*: a file whose content carries the run's own directory is longer or shorter with that path; the file itself is compared above, so its recorded size is dropped. Sizes of the inputs, outside the run directory, are compared as recorded (source: CometGUI provenance FileRecord (sizeBytes)).

       *--num-threads value*, only when Percolator's thread count is varied: the varied Percolator setting itself, echoed wherever the command is recorded (source: CometGUI PercolatorRun.values / PercolatorCommands (the option the product passes); Percolator rel-3-07-01 src/Caller.cpp:310-312).

       *percolator.thread-count*, only when Percolator's thread count is varied: the varied setting, recorded (source: CometGUI PercolatorProvenance).

       *--seed value*, only when the seed is varied (the control): the control's varied seed, echoed wherever the command is recorded (source: Percolator rel-3-07-01 src/Caller.cpp:229-232, 530-531).

       *percolator seed settings*, only when the seed is varied (the control): the control's seed, recorded (source: CometGUI PercolatorProvenance, EffectiveSeed).
   * - ``fixture:events-log``
       (``provenance/events.log``)
     - CometGUI
     - ``jsonl-unordered``
     - the provenance event log; compared as a set of events because the engine runs ready steps concurrently (resolve-comet, resolve-percolator, serialise-comet-params and hash-inputs start together), so their interleaving is the scheduler's.

       *seq, time and run identifier*: each event's sequence number and instant follow the scheduler and the clock, and the run identifier is taken from the clock (source: CometGUI EventLog, ClockRunIdSource).

       *digests of the run's own files*: a digest of a file whose content carries the run's own directory differs with it; each digest of a file under the run directory is replaced by the name of the file it is the digest of (the files themselves are compared above). Digests of the inputs and of the tools, outside the run directory, are compared as recorded (source: CometGUI provenance FileRecord, ToolRecord; this suite's digest_labels).

       *the run's own directory*: the run's own directory: every run is a new run in a new directory (the product never reuses one), and the path is written into the content (source: CometGUI RunLayout).

       *--num-threads value*, only when Percolator's thread count is varied: the varied Percolator setting itself, echoed wherever the command is recorded (source: CometGUI PercolatorRun.values / PercolatorCommands (the option the product passes); Percolator rel-3-07-01 src/Caller.cpp:310-312).

       *--seed value*, only when the seed is varied (the control): the control's varied seed, echoed wherever the command is recorded (source: Percolator rel-3-07-01 src/Caller.cpp:229-232, 530-531).
   * - ``fixture:run-json``
       (``run.json``)
     - CometGUI
     - ``json``
     - the run's own record of its inputs, parameters and attempts.

       *run identifier and times*: from the clock (source: CometGUI RunJson, ClockRunIdSource).

       *step fingerprints*: a digest over a step's input digests, among them outputs whose bytes carry the run's own directory (source: CometGUI RecordedFingerprint).

       *digests of the run's own files*: a digest of a file whose content carries the run's own directory differs with it; each digest of a file under the run directory is replaced by the name of the file it is the digest of (the files themselves are compared above). Digests of the inputs and of the tools, outside the run directory, are compared as recorded (source: CometGUI provenance FileRecord, ToolRecord; this suite's digest_labels).

       *the run's own directory*: the run's own directory: every run is a new run in a new directory (the product never reuses one), and the path is written into the content (source: CometGUI RunLayout).
   * - ``fixture:product-results``
       (``the run's record``)
     - CometGUI
     - ``record``
     - what the product derives from the tables: each table's parsed row count, the rows its default filters select at q <= 0.01 and their identifiers (PSM identifiers without the run's directory), and the weights as its reader reads them.

       *order of the selected identifiers among tied rows*, only when Percolator's thread count is varied: the product lists the rows its default filter selects in the table's order, so it inherits the order of tied rows above; the identifiers themselves and their count must be equal (source: Percolator rel-3-07-01 src/Scores.cpp:241-242; DeterminismSupport.derived (the product's PsmQValueFilter, PeptideQValueFilter in table order)).
   * - ``synthetic:synthetic-pin``
       (``probe.pin``)
     - CometGUI (SyntheticPin)
     - ``bytes``
     - the committed synthetic PIN is generated from a fixed seed (SyntheticPin.PROBE_SEED).

       Nothing may differ.
   * - ``synthetic:synthetic-psms``
       (``out/psms.tsv``)
     - Percolator
     - ``tsv``
     - as percolator-psms; the synthetic identifiers carry no path.

       *order of rows tied on Percolator's sort keys*, only when Percolator's thread count is varied: Percolator sorts its results with std::sort, which is not stable, on (score, scan, expMass, label); PSMs of one spectrum with identical features (positional isomers of an oxidised methionine, ranks of one scan) score exactly alike and stay unordered. Measured: at --num-threads 1 against 3 such rows change places, their values identical; repeats at one thread count never do. Rows are compared as a set only within a block of consecutive rows equal in score, q-value, PEP and scan number (source: Percolator rel-3-07-01 src/Scores.cpp:44-46 (operator> keys), :241-242 (postMergeStep: sort, greater<ScoreHolder>); ISO C++ [alg.sort]: std::sort is not stable).
   * - ``synthetic:synthetic-peptides``
       (``out/peptides.tsv``)
     - Percolator
     - ``tsv``
     - as percolator-psms; the synthetic identifiers carry no path.

       *order of rows tied on Percolator's sort keys*, only when Percolator's thread count is varied: Percolator sorts its results with std::sort, which is not stable, on (score, scan, expMass, label); PSMs of one spectrum with identical features (positional isomers of an oxidised methionine, ranks of one scan) score exactly alike and stay unordered. Measured: at --num-threads 1 against 3 such rows change places, their values identical; repeats at one thread count never do. Rows are compared as a set only within a block of consecutive rows equal in score, q-value, PEP and scan number (source: Percolator rel-3-07-01 src/Scores.cpp:44-46 (operator> keys), :241-242 (postMergeStep: sort, greater<ScoreHolder>); ISO C++ [alg.sort]: std::sort is not stable).
   * - ``synthetic:synthetic-decoy-psms``
       (``out/decoy-psms.tsv``)
     - Percolator
     - ``tsv``
     - as percolator-psms; the synthetic identifiers carry no path.

       *order of rows tied on Percolator's sort keys*, only when Percolator's thread count is varied: Percolator sorts its results with std::sort, which is not stable, on (score, scan, expMass, label); PSMs of one spectrum with identical features (positional isomers of an oxidised methionine, ranks of one scan) score exactly alike and stay unordered. Measured: at --num-threads 1 against 3 such rows change places, their values identical; repeats at one thread count never do. Rows are compared as a set only within a block of consecutive rows equal in score, q-value, PEP and scan number (source: Percolator rel-3-07-01 src/Scores.cpp:44-46 (operator> keys), :241-242 (postMergeStep: sort, greater<ScoreHolder>); ISO C++ [alg.sort]: std::sort is not stable).
   * - ``synthetic:synthetic-decoy-peptides``
       (``out/decoy-peptides.tsv``)
     - Percolator
     - ``tsv``
     - as percolator-psms; the synthetic identifiers carry no path.

       *order of rows tied on Percolator's sort keys*, only when Percolator's thread count is varied: Percolator sorts its results with std::sort, which is not stable, on (score, scan, expMass, label); PSMs of one spectrum with identical features (positional isomers of an oxidised methionine, ranks of one scan) score exactly alike and stay unordered. Measured: at --num-threads 1 against 3 such rows change places, their values identical; repeats at one thread count never do. Rows are compared as a set only within a block of consecutive rows equal in score, q-value, PEP and scan number (source: Percolator rel-3-07-01 src/Scores.cpp:44-46 (operator> keys), :241-242 (postMergeStep: sort, greater<ScoreHolder>); ISO C++ [alg.sort]: std::sort is not stable).
   * - ``synthetic:synthetic-weights``
       (``out/weights.txt``)
     - Percolator
     - ``bytes``
     - as percolator-weights.

       Nothing may differ.
   * - ``synthetic:synthetic-pout``
       (``out/pout.xml``)
     - Percolator
     - ``pout``
     - as percolator-pout.

       *process_info command_line*: Percolator echoes its argument array, which names the run's own directory; the argument array itself is compared in provenance-json (source: Percolator rel-3-07-01 src/XMLInterface.cpp:440; Caller.cpp:98-105).

       *order of <psm> and <peptide> elements tied on Percolator's sort keys*, only when Percolator's thread count is varied: Percolator sorts its results with std::sort, which is not stable, on (score, scan, expMass, label); PSMs of one spectrum with identical features (positional isomers of an oxidised methionine, ranks of one scan) score exactly alike and stay unordered. Measured: at --num-threads 1 against 3 such rows change places, their values identical; repeats at one thread count never do. Elements are compared as a set only within a block of consecutive rows equal in svm_score, q_value, pep, exp_mass and scan number; a peptide's psm_ids, listed in that same order, are compared as a set (source: Percolator rel-3-07-01 src/Scores.cpp:44-46 (operator> keys), :241-242 (postMergeStep: sort, greater<ScoreHolder>); ISO C++ [alg.sort]: std::sort is not stable).
   * - ``synthetic:synthetic-stdout``
       (``percolator.stdout``)
     - Percolator
     - ``bytes``
     - Percolator writes its results to files, so its standard output is empty.

       Nothing may differ.
   * - ``synthetic:synthetic-stderr``
       (``percolator.stderr``)
     - Percolator
     - ``text``
     - Percolator's standard error.

       *the run's own directory*: the run's own directory: every run is a new run in a new directory (the product never reuses one), and the path is written into the content (source: this suite's run directory).

       *Started*: Percolator prints the wall-clock start time (source: Percolator rel-3-07-01 src/Caller.cpp:78).

       *cpu and wall-clock seconds*: Percolator prints how long reading and processing took (source: Percolator rel-3-07-01 src/Caller.cpp:732-733, :1008).

       *--num-threads value*, only when Percolator's thread count is varied: the varied Percolator setting itself, echoed wherever the command is recorded (source: CometGUI PercolatorRun.values / PercolatorCommands (the option the product passes); Percolator rel-3-07-01 src/Caller.cpp:310-312).

       *--seed value*, only when the seed is varied (the control): the control's varied seed, echoed wherever the command is recorded (source: Percolator rel-3-07-01 src/Caller.cpp:229-232, 530-531).
   * - ``synthetic:synthetic-command``
       (``the run's record``)
     - CometGUI (PercolatorCommands)
     - ``record``
     - the argument array the product's command builder made (the run's directory written <work>) and the effective seed the product records.

       *--num-threads value*, only when Percolator's thread count is varied: the varied Percolator setting itself, echoed wherever the command is recorded (source: CometGUI PercolatorRun.values / PercolatorCommands (the option the product passes); Percolator rel-3-07-01 src/Caller.cpp:310-312).

       *--seed value and the effective seed*, only when the seed is varied (the control): the control's seed (source: CometGUI EffectiveSeed).
   * - ``synthetic:synthetic-product-results``
       (``the run's record``)
     - CometGUI
     - ``record``
     - as product-results.

       *order of the selected identifiers among tied rows*, only when Percolator's thread count is varied: the product lists the rows its default filter selects in the table's order, so it inherits the order of tied rows above; the identifiers themselves and their count must be equal (source: Percolator rel-3-07-01 src/Scores.cpp:241-242; DeterminismSupport.derived (the product's PsmQValueFilter, PeptideQValueFilter in table order)).

.. determinism-tolerance-table-end


**Evidence** (2026-11-06, Linux x86-64, 64 cores, on the tree of this unit).
``COMETGUI_MATRIX_FIXTURE=scratch/fixture bash scripts/matrix/run-matrix.sh
--part determinism --offline``: **GREEN** in 360 s (Maven 349 s; the fixture
runs 293 s of it). N = 3. 28 runs: 12 synthetic (Percolator 3.07.1 and 3.06.5,
six configurations each) and 16 over the fixture (the three repeats of each of
the four pairs, and the four variations of the default pair); 22 comparisons
with ``repeat-1``, 323 artefact verdicts. Every repeat: 188 of 188 artefacts
``identical`` after the base fields alone -- no repeat needed a ``whenVaried``
field, and none a numeric tolerance. Variations: 77 ``identical``, 21 ``within
tolerance`` (Comet's thread count: only ``num_threads`` in ``comet.params`` and
the pepXML; Percolator's: only the thread count wherever it is recorded, and,
at 1 thread against 3, the order of tied rows above). Controls: 23 artefacts
``differs, as the control requires`` (every Percolator table, the weights, the
pout XML, Percolator's standard error, the event log's table counts and the
product's selected identifiers), 12 ``identical`` (every Comet artefact and
log, the merged PIN, ``run.json``, the synthetic PIN, Percolator's empty
standard output, and the synthetic runs' derived results, where nothing passes
1% either way) and 5 ``within tolerance`` (the provenance record, its rendering,
the archived settings and the argument array: only the seed where recorded).
The synthetic half alone (``--part determinism-synthetic``, what the nightly
runs) took 56 s, and 29 s as a step of the ``pipeline`` control (warm build):
that is what this unit adds to that control. Without the fixture, ``--part
determinism`` ran the synthetic half green and exited 3, ``REFUSED``.

The digests behind "identical" (SHA-256, first 16 hex digits; for a compared
form, of the lines left after the named fields are removed), the same in
``repeat-1``, ``repeat-2`` and ``repeat-3`` of the default pair:
``weights.txt`` ``a1c7db225fe1b555`` (the file itself; provenance records the
same), ``merged.pin`` ``1d215d23fae05f7d``, the two PINs ``ebd046c51ecbaf4c``
and ``ab6f7a42aaef8b3b``, the two pepXML ``1d5ed4c095e2081a`` and
``25a7fae13b53846f``, ``psms.tsv`` ``437b3ad4b5591678``, ``pout.xml``
``dd487f9e2911b4ef``; over the synthetic PIN with 3.07.1, ``weights.txt``
``5370f2375d3ea1f1`` and ``psms.tsv`` ``6e782dd7bbba9f16``. Every run's
digests are in ``determinism-report.json``.

**Shown red** (2026-11-06; each injected into production code, the anchor gone
and ``sha256sum -c`` FAILED while in, restored and ``sha256sum -c`` OK after):

* ``PinMerger`` writing each file's rows in ``HashSet`` order (the rows carry
  the run's directory, so the order differs run to run): RED, 43 problems,
  first ``fixture comet-2026.02.2_percolator-3.06.5 repeat-2 (repeat): artefact
  merged-pin DIFFERS -- inputs/pin/merged.pin: line 2, row
  '<run>/outputs/comet/20100614_Velos1_TaGe_SA_K562_3_11788_4_3', column
  SpecId``; Percolator's tables and pout XML followed, its tied rows now read in
  another order -- the input order is what decides them.
* ``PercolatorSettings.valueText`` giving the seed from the clock instead of the
  setting: synthetic half RED, 60 problems, first ``synthetic percolator-3.06.5
  repeat-2 (repeat): artefact synthetic-psms DIFFERS -- out/psms.tsv: line 2,
  row 'psm84', column PSMId``, and ``synthetic-command ... field argv``.
* ``WorkflowRunPort.start`` handing the engine the spectrum files in reverse
  order on every second run: RED, 51 problems, first ``fixture
  comet-2026.02.2_percolator-3.06.5 repeat-2 (repeat): artefact merged-pin
  DIFFERS -- inputs/pin/merged.pin: line 2, row
  '<run>/outputs/comet/20100614_Velos1_TaGe_SA_K562_4_9939_2_1', column
  SpecId``, with the Comet logs, provenance, its rendering, the event log and
  ``run.json`` red beside it on every pair (``repeat-3``, an odd run, agreed).

``determinism.py --self-test`` (in the ``traceability`` control) proves the
document check red on a field, an artefact, a rule or N on one side only, and
the comparison red on each of its own defects.

**What it does not cover.** Linux x86-64 only; macOS and Windows would run the
same entry point on their runners. One fixture (the ``D-006`` local one: two
K562 mzML files, the UniProt human proteome) and one host. The ``RESET``
algorithm, subset training, index search and the fragment-ion index are not
exercised (the product does not offer them); Percolator 3.09, a local binary on
Linux, is not part of the suite. The fixture half has never run on a CI runner
(``D-006``).

.. _dev-testing-performance:

Performance -- gate item 4
--------------------------

:Checks: the specification's nine measurements (*Performance and resource
   tests*), each through the product's own code path, each repeated to a median
   and a range, and every one but Comet's execution time graded against a
   threshold of the runner's *profile*; ``AC-RES-10`` ("Result tables remain
   responsive on the large performance fixture within the documented heap
   budget"). PHASE-15 exit gate item 4.
:Fails: exit 1 when a median is beyond its threshold -- the line names the
   measurement, the threshold, the observed median and the profile -- when a
   check of the measured work fails (a count, a digest, the window's title, a
   tree not gone, an attempt not ``CANCELLED``), or when a planned measurement
   wrote no record; exit 4, ``NO BASELINE FOR THIS RUNNER PROFILE``, on a host
   no recorded profile matches -- every measurement still runs and is reported,
   and none is graded; exit 3 when the fixture part was requested and no
   fixture was named. Exit 0 only when every planned measurement passed (or is
   informational) on a recorded profile.
:Configured in: ``scripts/perf/performance-data.json`` (the measurements, the
   sizes of what is generated, and per profile the thresholds, their rationale,
   the injection that showed each red, and the baseline), ``scripts/perf/performance.py``
   (the plan, the grading, the host and its load; standard library only), and
   seven opt-in JUnit classes in ``cometgui-app``:
   ``org.cometgui.app.performance.Performance{Startup,Parameters,Hashing}``,
   ``org.cometgui.app.gui.Performance{Results,Projects}`` and
   ``org.cometgui.app.matrix.Performance{Percolator,Fixture}``, named so that no
   plain ``mvn verify`` or ``scripts/build.sh`` selects them, and disabled
   without ``-Dcometgui.perf=true`` (P15-5).
:Runs with: ``bash scripts/perf/run-performance.sh`` -- the one entry point; the
   nightly's ``performance`` job (``scripts/ci/nightly-performance.sh``,
   ``--part no-spectra``) and ``performance-fixture`` job
   (``scripts/ci/nightly-performance-fixture.sh``, waiting on ``D-006``).

**How to run it.** ::

    COMETGUI_MATRIX_FIXTURE=scratch/fixture bash scripts/perf/run-performance.sh   # all nine
    bash scripts/perf/run-performance.sh --part no-spectra        # what the nightly runs
    bash scripts/perf/run-performance.sh --part no-spectra --quick   # fewer repeats, same thresholds
    bash scripts/perf/run-performance.sh --only hash-throughput   # one measurement
    bash scripts/perf/run-performance.sh --profile NAME           # assert a profile (must match the host)
    bash scripts/perf/run-performance.sh --self-test              # the grading, no Maven
    bash scripts/perf/run-performance.sh --check-doc              # this section against the data

The report is ``_build/performance/performance-report.txt`` (and ``.json``):
the host (CPU model, CPUs visible, memory, kernel, frequency governor, JDK), the
load while the suite ran (the host's load average before, after and sampled
every ten seconds, and this container's CPU use from cgroup ``cpu.stat``), the
profile and why it was chosen, and per measurement its verdict, N, median,
range, threshold and the words of any red. Every sample and every check is in
``_build/performance/measurements/<id>.json``. ``--work DIR`` puts the
generated files elsewhere (about 3.5 GB at most; emptied at the end).

**What is measured, and through what.** Every sample is the product's own code
doing the work a scientist waits on; every record carries checks that the work
was the real work.

``startup-first-window`` (a)
   A fresh JVM runs the application's own ``main`` (``CometGuiLauncher``),
   headless with the module's Monocle recipe read from ``cometgui-app/pom.xml``
   and the project font stack; timed from just before the process service
   starts it to the line it prints *from the JavaFX application thread* once the
   shell's stage is showing. JVM start, class loading, the composition root and
   the first layout are inside the number. Check: titled ``CometGUI``, the shell
   as its root, 1280 by 800.
``params-import`` (b)
   The Comet Parameters section's import (``ParameterFilesViewModel.importFile``):
   read, parsed by the release's parser, adopted into a fresh session and
   validated. The file is generated: Comet 2026.03.0's complete ``comet -q``
   output with all fifteen variable modifications set, the enzyme table
   extended to 2000 rows and 20 000 unknown parameters, each with a comment
   (1.6 MB, 24 195 lines). Checks: ``IMPORTED``; every unknown parameter, every
   enzyme row and fifteen modifications held by the model.
``results-*`` (c) and ``gui-heap-peak`` (g)
   Phase 10's large performance fixture (1 000 000 target PSMs and 400 000
   target peptides, synthetic, ``scripts/fixtures/large-results-fixture.py``;
   the local copy if its digests hold, else generated into the work directory)
   copied into a fresh constructed run per repeat, so every open is cold, and
   driven through the real composition root by the robot, timed from the first
   event to the answer on screen as ``ResultsLargeFixtureUiTest`` times its
   budgets: the run selected (``results-open``: both tables hashed, indexed and
   mapped); four PSM filter changes; a numeric and a text sort, each first use;
   four page moves; the peptide table shown; four peptide filter changes. Every
   count shown is checked against the generator's manifest, every page position
   against the arithmetic, and the table never holds more than one page. The
   heap: the used heap after a forced collection, before the first run is
   opened and after every action; a repeat's sample is the most it stood above
   that baseline, which the documented budget (64 MB) bounds.
``hash-throughput`` (d)
   ``RunWiring.hasher()`` -- the engine's one hasher, a cache over the
   single-pass ``StreamingHashService`` -- over a 2 GiB file of seeded
   pseudo-random bytes generated in the work directory, a fresh hasher per
   sample so the cache never answers. Check: every sample's MD5 and SHA-256
   equal the JDK's, computed while the file was written. The file has just been
   written, so it is read from the page cache: what is measured is the hasher,
   not the disk.
``comet-fixture`` (e) and ``percolator-fixture`` (f)
   The whole search through the run port (``RunWiring.port``, as the Run
   section starts it) over the named fixture, in a fresh project per sample,
   with the product's default parameters and settings; the samples are the
   engine's ``run-comet`` and ``run-percolator`` steps, as its transitions date
   them. Comet's is informational, by the specification. Check: every search
   succeeded.
``percolator-synthetic`` (f)
   Percolator 3.07.1, installed from its pinned URL by the Tool Manager, over
   CometGUI's own synthetic PIN, with the command ``PercolatorCommands`` builds
   from the default settings, through ``ToolRunner``. Checks: exit 0; the
   product's readers parse 64 target and 64 decoy PSMs and three splits.
``cancel-fake-tool`` and ``cancel-comet`` (h)
   From the run's Cancel (``RunHandle.cancel``; the run port's
   ``ActiveRun.cancel``, what the Run section's button calls) to every process of
   the snapshotted tree gone. The fake tool is the stand-in Percolator of the
   GUI tests, which starts ``/bin/sleep`` and waits for it; the real one is
   Comet over the fixture, once its process has run for a second. Checks: the
   tree gone, the attempt ``CANCELLED``.
``project-reopen`` (i)
   A project of 50 sealed runs, each made by the engine with the stand-in
   tools and a 64 MiB generated spectrum file, reopened as a relaunched
   application does: a new ``ProjectSession``, the Results and Provenance
   sections' ports, and timed: the lock taken, both run lists read, and the
   most recent run opened in both. Checks: 50 listed in each, every record
   sealed, the record opened without a problem.

**Runner profiles, and what a verdict means.** A threshold measured on one
machine says nothing about another, so every threshold belongs to a *runner
profile*: a host fingerprint (CPU model, CPUs visible, memory within 2%, OS and
architecture) with its thresholds, the rationale for each, the injection that
showed each red, and the baseline it was derived from. ``performance.py``
detects the profile from the host; ``--profile NAME`` asserts one, which must
also match the host.

``PASS``
   The median is within the profile's threshold and every check held.
``RED``
   A median beyond its threshold (named with the threshold and the observed
   value), a failed check, or a planned measurement with no record.
``INFO``
   Comet's execution time: the specification makes it informational. Its
   checks are still graded.
``NO-BASELINE``
   Measured and reported on a host no profile matches. The run ends ``NO
   BASELINE FOR THIS RUNNER PROFILE``, exit 4 -- every GitHub-hosted runner
   today. It is never green.
``REFUSED``
   A fixture measurement with no fixture named (exit 3, never green).

**The fixture.** Comet's and Percolator's times over spectra and Cancel during
a real Comet search take the version matrix's one named input,
``COMETGUI_MATRIX_FIXTURE``; there is no default, and without it they are
``REFUSED``. Everything else generates its own data at run time and deletes it,
so the ``no-spectra`` part needs nothing ``D-006`` governs.

**How a threshold is set.** From a full run on the profile's host
(``performance.py propose --report DIR --name NAME`` applies the rule and
prints a candidate profile for review): for a time, at least twice the median
*and* one and a half times the slowest sample; for a throughput, at most 0.6 of
the median and 0.8 of the slowest. Then two limits, both checked by the data's
own validation: never looser than the level at which a scientist would see a
regression (each measurement's ``ceiling`` -- the documented interface budgets
of ``ResultsLargeFixtureUiTest`` for the result tables, the 64 MB heap budget of
``AC-RES-10``, the process service's 5 s termination grace for cancellation),
and never tighter than the noise this host showed under load: the ``no-spectra``
part was run with 32 of the 64 cores kept busy by a stand-in co-tenant
(load average 37, this container's CPUs busy 33.9 on average), and where its
slowest sample came near the rule's figure the threshold was raised to clear it
by a quarter (``results-open``, ``results-psm-sort-text``,
``percolator-synthetic``). Sub-second measurements whose rule figure is a few
milliseconds (``cancel-fake-tool``, ``project-reopen``) carry an absolute floor
instead, stated in their rationale. And every threshold was **shown red**: a
production-code injection pushed the measurement over it and the suite named the
measurement, the threshold and the observed value. A threshold no injection can
push over is a defect; the data refuses a threshold with no recorded red, no
rationale, a value looser than its ceiling, or a baseline median beyond it.

.. performance-baseline-table-start

.. list-table:: Runner profile ``shared-xeon-6226r-64`` -- baseline 2026-11-06 20:48:17 UTC at ``ba49bc2`` (machine-readable: ``scripts/perf/performance-data.json``)
   :header-rows: 1
   :widths: 5 18 4 9 14 28 22

   * - Spec
     - Measurement
     - N
     - Median
     - Range
     - Threshold, and why
     - Shown red by
   * - (a)
     - ``startup-first-window``: application startup to the first usable window
     - 7
     - 3496 ms
     - 3335 ms to 3649 ms
     - at most 7000 ms: twice the median (3496 ms) and 1.9 times the slowest sample (3649 ms); under the 10 s at which a start reads as hung. A 2x slower start is a regression a scientist notices on every launch.
     - CometGuiApplication.start parks the JavaFX thread 5 s before the theme is applied (observed 8485 ms, N=3)
   * - (b)
     - ``params-import``: importing, parsing and validating a large Comet parameter file
     - 30
     - 184 ms
     - 165 ms to 382 ms
     - at most 600 ms: about three times the median (184 ms) and 1.6 times the slowest sample, the first, cold one (382 ms); a third of the 2 s at which the editor visibly stalls.
     - CometParamsParser keeps an unknown parameter only after scanning every one kept so far for its name (quadratic in 20 000 unknowns) (observed 6236 ms, N=10)
   * - (c)
     - ``results-open``: opening a run of 1 000 000 PSMs and 400 000 peptides (cold)
     - 5
     - 2981 ms
     - 2943 ms to 3583 ms
     - at most 8000 ms: 2.7 times the median (2981 ms) and 2.2 times the slowest (3583 ms), and 1.3 times the slowest open with 32 of the 64 cores kept busy by another tenant (6283 ms); about half the documented 15 s open budget, so a doubled cold open is red long before a scientist would call the table unresponsive.
     - DiskIndex.build parks 3 s before indexing a table (two tables per open) (observed 9347 ms, N=2)
   * - (c)
     - ``results-psm-filter``: a PSM q-value filter change on 1 000 000 PSMs
     - 20
     - 918 ms
     - 689 ms to 1046 ms
     - at most 2000 ms: twice the median (918 ms; most of it the robot typing the cutoff) and 1.9 times the slowest (1046 ms); half the documented 4 s filter budget.
     - DiskResultStore.query parks 1.5 s before every query (observed 2323 ms, N=8)
   * - (c)
     - ``results-psm-sort-numeric``: sorting 219 777 passing PSMs by score, first use
     - 5
     - 991 ms
     - 952 ms to 1324 ms
     - at most 2000 ms: twice the median (991 ms) and 1.5 times the slowest, the first (1324 ms); a third of the documented 6 s numeric sort budget.
     - DiskResultStore parks 6 s before building a sort file (observed 7150 ms, N=2)
   * - (c)
     - ``results-psm-sort-text``: sorting 219 777 passing PSMs by peptide, first use
     - 5
     - 3228 ms
     - 3165 ms to 3441 ms
     - at most 7500 ms: 2.3 times the median (3228 ms) and 2.2 times the slowest (3441 ms), and 1.4 times the slowest with 32 cores kept busy by another tenant (5477 ms); half the documented 15 s text sort budget.
     - DiskResultStore parks 6 s before building a sort file (observed 9337 ms, N=2)
   * - (c)
     - ``results-psm-page``: a page move in the PSM table
     - 20
     - 459 ms
     - 382 ms to 641 ms
     - at most 1000 ms: twice the median (459 ms) and 1.6 times the slowest (641 ms); 40% of the documented 2.5 s page budget.
     - DiskResultStore.query parks 1.5 s before every query (observed 1979 ms, N=8)
   * - (c)
     - ``results-peptide-show``: showing the 400 000-row peptide table
     - 5
     - 865 ms
     - 769 ms to 917 ms
     - at most 1800 ms: twice the median (865 ms) and twice the slowest (917 ms); under half the 4 s a query may take.
     - DiskResultStore.query parks 1.5 s before every query (observed 2107 ms, N=2)
   * - (c)
     - ``results-peptide-filter``: a peptide q-value filter change on 400 000 peptides
     - 20
     - 940 ms
     - 678 ms to 1134 ms
     - at most 2000 ms: twice the median (940 ms) and 1.8 times the slowest (1134 ms); half the documented 4 s filter budget.
     - DiskResultStore.query parks 1.5 s before every query (observed 2301 ms, N=8)
   * - (d)
     - ``hash-throughput``: hashing a 2 GiB generated file, MD5 and SHA-256 in one pass
     - 5
     - 258 MiB/s
     - 251 MiB/s to 258 MiB/s
     - at least 150 MiB/s: at least 0.58 of the median (258 MiB/s) and 0.6 of the slowest sample (251 MiB/s): a hasher that read the file twice, or digested it twice, halves the throughput and is red; above the 100 MiB/s below which a 4 GB spectrum takes over 40 s.
     - StreamingHashService.hash(Path) opens and digests the file twice (correct digests, two reads) (observed 127 MiB/s, N=3)
   * - (e)
     - ``comet-fixture``: Comet's execution time over the named fixture (the run-comet step)
     - 5
     - 4.00 s
     - 3.96 s to 4.12 s
     - informational: informational: the specification gives Comet's execution time no threshold; it is Comet's, not CometGUI's
     - --
   * - (f)
     - ``percolator-fixture``: Percolator's execution time over the named fixture's merged PIN (the run-percolator step)
     - 5
     - 2.14 s
     - 2.13 s to 2.21 s
     - at most 4.50 s: twice the median (2.14 s) and twice the slowest (2.21 s); well under the 30 s a scientist would notice as a slow Percolator step.
     - PercolatorSteps.RunPercolator parks 3 s after Percolator exits (observed 5.17 s, N=2)
   * - (f)
     - ``percolator-synthetic``: Percolator's execution time over CometGUI's synthetic PIN
     - 15
     - 532 ms
     - 509 ms to 579 ms
     - at most 1500 ms: 2.8 times the median (532 ms) and 2.6 times the slowest (579 ms), and 1.4 times the slowest with 32 cores kept busy by another tenant (1090 ms; Percolator runs three threads); under a third of the 5 s ceiling, so a fixed delay of a second added to every tool run is red.
     - ToolRunner.run parks 1.2 s after the tool exits (observed 1769 ms, N=5)
   * - (g)
     - ``gui-heap-peak``: peak retained GUI heap above the baseline while the large tables are used
     - 5
     - 4.93 MiB
     - 3.63 MiB to 6.26 MiB
     - at most 16.0 MiB: about three times the median (4.93 MiB) and 1.7 times the slowest sample of two full runs (9.30 MiB; the peak creeps by about 0.6 MiB per run opened, see the findings); a quarter of the documented 64 MB budget, so a store that keeps rows on the heap is red whatever the noise of a collection.
     - ResultStores.IN_MEMORY_ROW_LIMIT raised to 10 000 000, so the large tables are held on the heap (observed 909 MiB, N=2)
   * - (h)
     - ``cancel-fake-tool``: Cancel to the process tree gone: the stand-in Percolator and its child
     - 15
     - 7.78 ms
     - 7.21 ms to 19.0 ms
     - at most 250 ms: a floor rather than a multiple: the median (7.78 ms) is a signal and two exits, and on this shared host a scheduler delay of tens of milliseconds is noise, not the product; 250 ms is 13 times the slowest sample (19.0 ms) and a quarter of the 1 s a Cancel may take.
     - StartedProcess.requestCancellation waits up to 3 s for the tool to exit by itself before signalling it (observed 3009 ms, N=5)
   * - (h)
     - ``cancel-comet``: Cancel to the process tree gone: a real Comet search over the named fixture
     - 5
     - 94.1 ms
     - 91.6 ms to 100 ms
     - at most 500 ms: five times the median (94.1 ms) and the slowest (100 ms): Comet ends on the first signal; a tenth of the 5 s termination grace, so a cancellation that waited for the grace, or for Comet, is red.
     - StartedProcess.requestCancellation waits up to 3 s for the tool to exit by itself before signalling it (observed 1596 ms, N=2 (Comet finished its search inside the wait))
   * - (i)
     - ``project-reopen``: reopening a project of 50 sealed runs: lock, both run lists, the newest run opened
     - 15
     - 59.0 ms
     - 45.4 ms to 118 ms
     - at most 400 ms: a floor over the noise of 50 run directories read from a shared disk: about seven times the median (59.0 ms) and 3.4 times the slowest (118 ms); a fifth of the 2 s at which empty sections are visible, so a reopen that re-reads every run's inputs is red.
     - ProvenanceReader.list re-hashes every run's recorded spectrum file (50 x 64 MiB) (observed 12682 ms, N=5)

.. performance-baseline-table-end

**The baseline** (2026-11-06, at ``ba49bc2`` with this unit's suite in the
working tree): ``COMETGUI_MATRIX_FIXTURE=scratch/fixture bash
scripts/perf/run-performance.sh``, full mode, every measurement, 384 s (Maven
383 s; the classes: startup 27 s, parameters 7 s, hashing 50 s, the fixture 58 s,
Percolator 16 s, projects 60 s, results 155 s). Host: Intel Xeon Gold 6226R at
2.90 GHz, 64 CPUs visible, 376.5 GiB, Linux 6.8.0-147 x86-64, frequency
governor ``schedutil``, OpenJDK 25.0.4.1 (Liberica, project-local), the
project's Debian 12 font stack, headless Monocle. Load while it ran: the host's
1-minute load average 4.1 before, 4.98 after, 6.72 on average and 14.36 at most
(sampled every ten seconds); this container's CPUs busy (cgroup ``cpu.stat``)
3.86 on average, most of it the suite. The data holds the same, and
``performance-report.json`` of any run holds its own.

.. warning::

   **This shared host is not the dedicated runner, and gate item 4's "on the
   dedicated runner" half is UNVERIFIABLE here.** The profile above
   (``shared-xeon-6226r-64``, ``"dedicated": false``) is the development host
   this project is built on: a container on a 64-core machine whose other
   tenants come and go, with a ``schedutil`` frequency governor. Its PASS is
   evidence that the suite measures the product's real paths, grades them and
   goes red on a regression -- not the dedicated-runner verdict. No dedicated
   runner exists. What one would need:

   * **fixed hardware** -- one machine model, never a pool -- recorded as its
     profile's fingerprint; a change of hardware is a new profile;
   * **no co-tenants**: nothing else scheduled on it while the suite runs (no
     other CI job, no shared container host), and the suite run serially;
   * **a pinned CPU frequency**: the ``performance`` governor (or a fixed
     frequency), turbo and deep C-states off, so that a sample does not depend
     on the thermal state of the last one;
   * **the same software stack** as the profile records: the project-local JDK
     and font stack (``tools/env.sh``), the same kernel line, and a local disk
     with room for the 3.5 GB work directory;
   * **the named fixture** for the fixture part, which waits on ``D-006``'s CI
     half like every spectra-needing job;
   * **its own stored baseline**: a profile in ``performance-data.json``, made
     from at least three full runs on that runner on different days (``propose``
     on each; the thresholds from the slowest), reviewed like a golden, each
     threshold shown red there with the injections recorded here, and kept by
     ``check-doc``;
   * **how its thresholds are derived**: exactly as above -- the rule, the
     ceilings, the noise of a loaded run -- and on a dedicated machine the noise
     term should shrink, so its thresholds can be tighter than this host's.

**CI.** The nightly's ``performance`` job runs ``nightly-performance.sh``
(``--part no-spectra``, full repeats) on ``ubuntu-latest``. **It is red there,
by design:** a GitHub-hosted runner (2 or 4 shared cores) matches no profile,
so after measuring and reporting everything the step exits 4, ``NO BASELINE FOR
THIS RUNNER PROFILE``. It turns green when a dedicated runner and its profile
exist. The ``performance-fixture`` job (``nightly-performance-fixture.sh``) is a
stub naming ``D-006`` and the dedicated runner, exit 70, until both exist. The
``pipeline`` control runs the nightly step on this host with
``COMETGUI_PERFORMANCE_QUICK=1`` (set by ``run-pipeline-locally.sh``): every
``no-spectra`` measurement, two to ten repeats instead of five to thirty, the
same sizes and thresholds, graded against this host's profile. That is what
this unit adds to that control: 374 s measured as its step on 2026-11-06 (the
control 1674 s in all), and 238 s run alone under the 32-core load above, Maven's
build included in both. Most of the difference is making the 50-run project
for ``project-reopen`` -- 213 s in the control's run against 49 s in the
baseline -- which writes and syncs thousands of small files and is at the mercy
of the shared disk; in that same run ``cancel-fake-tool`` read 42 ms and
``project-reopen`` 110 ms against the baseline's 8 ms and 58 ms, still well
inside their floors. That is the noise the floors are for.

**Shown red** (2026-11-06; each injected into production code, the anchor gone
and ``sha256sum -c`` FAILED while in, restored and ``sha256sum -c`` OK after;
``--quick --only`` the measurements named; the measurements not named by an
injection stayed green where they were run beside it): every row of the table
above. Of note: the sort-file injection left ``results-open`` and
``results-psm-filter`` green, and the index injection left the filter and page
moves green, so each threshold answers to its own work; ``ResultStores``'
in-memory limit raised to 10 000 000 put the 1 000 000-row table on the heap
(909 MiB retained) while every *time* stayed within its threshold -- only the
heap measurement sees that regression; and the first ``ToolRunner`` injection
(a 0.8 s wait placed *before* awaiting the exit) did not go red, because it ran
concurrently with Percolator -- it was moved after the exit, and the threshold
was later raised and the injection re-made at 1.2 s.

**Findings from building it.**

* Choosing a run in the Results section's run selector **with the keyboard
  opens every run it passes**: each arrow key selects, and each selection opens
  that run's stores (hash, index, map). The first version of this suite timed
  "opens" of 0.85 s that were warm (already indexed on the way past) and one of
  15 s that was five opens; the run is now selected directly, as a click does.
  A scientist arrowing through a project of large runs pays a cold open for
  every run passed. Reported, not changed here (no product change in this unit).
* The peak retained heap **creeps by about 0.6 MiB per run opened** in one
  session (6.6, 7.6, 8.2, 8.7, 9.3 MiB over five runs in one baseline); small
  against the 64 MB budget, but it does not return to the baseline when a run is
  closed. Not traced.
* ``cancel-comet``: Comet 2026.03.0 ends on the first signal (about 90 ms from
  Cancel to gone); the cancellation injection (wait up to 3 s for the tool to
  finish first) observed 1.6 s because the fixture's search finished inside the
  wait.

**What it does not cover.** The dedicated runner (above): no threshold here has
been measured on one. Linux x86-64 only. The fixture part over one fixture (the
``D-006`` local one) and one pair (Comet 2026.03.0, Percolator 3.07.1, the
product's choice for a Limelight-enabled run); it has never run on a CI runner.
The startup is headless (Monocle, software rendering); a desktop with a GPU and
a window manager is not measured. The results tables are the synthetic large
fixture; a real search of that size has not been run. Hashing is measured from
the page cache, not from a cold disk.


.. _dev-testing-flakiness:

Flakiness policy -- gate item 6
-------------------------------

``specification.rst``, *Flakiness policy*: "A flaky test is a defect. Do not
hide instability with unconditional retries. A temporarily quarantined flaky
test must have an issue and owner, captured diagnostics, a stated reason and a
fix plan. Release-critical scientific end-to-end tests may not remain
quarantined at release." PHASE-15 exit gate item 6: "No release-critical test
is quarantined." This section is the policy in words;
``scripts/ci/flakiness-check.sh`` (over ``scripts/ci/flakiness_check.py``,
standard library only) holds the tree to it.

What counts as flaky
~~~~~~~~~~~~~~~~~~~~

A test is flaky when its outcome on unchanged code depends on something the
test does not control: timing, load, scheduling, garbage collection, the
order of other tests, a random value, the machine's state. That includes a
falsifiability control that bites on one run and not the next (``pdv`` 5e,
below), and a test that is green for the wrong reason only under some timing
-- the project's signature defect. One red run with no change is enough to call
it flaky; "passed on re-run" is evidence of the defect, not of its absence.

What may be done about one
~~~~~~~~~~~~~~~~~~~~~~~~~~

#. **Fix it at its cause**, which is the only fix. Reproduce it on purpose
   first -- load the host, or widen the window with a test-only latch or
   delay -- so that the red is seen to come from the suspected cause; then make
   the test wait on the observable state it needs, or fix the product if the
   product is what is wrong. The assertion is not edited. A bound (a timeout,
   a heap budget) moves only with measured evidence that the bound itself was
   wrong, and the evidence is written down beside it.
#. **Quarantine it, temporarily**, only when it cannot be fixed yet, and never
   when it fails because of the product. A quarantine is an entry in
   ``scripts/ci/quarantine.toml`` with all seven fields -- ``test``,
   ``issue`` (an issue URL or a ``handoffs/`` file), ``owner``,
   ``diagnostics`` (the captured run, committed under
   ``scripts/ci/quarantine/``), ``reason``, ``fix_plan`` and ``since`` (a
   date, never in the future) -- together with the ``@Disabled`` it explains.
   The register is **empty**, which is the correct state.

A release-critical test may not be quarantined at all.

What is release-critical
~~~~~~~~~~~~~~~~~~~~~~~~

Computed by the check from the tree on every run, never a list kept by hand
(``bash scripts/ci/flakiness-check.sh --list-release-critical``; 279 classes
on 2026-11-07). A test class is release-critical when any of these names it:

* the failure-path map, ``scripts/e2e/failure-paths.toml`` (``AC-TST-09``) --
  40 classes;
* the end-to-end tiers: every class under ``app/e2e/tiera/`` and
  ``app/e2e/tierb/``, and every class meta-annotated ``@EndToEnd``
  (``AC-TST-06``..``08``) -- 8;
* a real binary: every test class whose name says it runs a real tool
  (``Real...``, ``...RealBinary...``, ``...RealPdv...``, ``...RealTest``) --
  45;
* a gate harness: every test class ``scripts/verify-*-gates.sh`` or the
  ``scripts/e2e/``, ``scripts/matrix/`` and ``scripts/perf/`` scripts name --
  234;
* the traceability map: every class ``docs/traceability-map.toml`` gives as
  test evidence for a requirement or criterion (the project is finished only
  when every ``AC-`` passes its named test) -- 105 (101 before Phase 15 unit
  11 named four more; the total stays 279, all four already counted).

A register entry naming such a class, or a method or nested class of one, is
red ``[RELEASE-CRITICAL]``.

What is forbidden
~~~~~~~~~~~~~~~~~

Each is red with its own words:

* **Unconditional retries and ignored failures** (``[RETRY]``):
  surefire's or failsafe's ``rerunFailingTestsCount`` (element, property or
  ``-D``), ``testFailureIgnore`` / ``maven.test.failure.ignore`` in any POM,
  ``.mvn/`` file, script or workflow; JUnit retry mechanisms --
  ``@RetryingTest``, ``@RepeatedIfExceptionsTest``, ``@Flaky``, the
  junit-pioneer or rerunner-jupiter dependency, and any implementation of
  ``TestExecutionExceptionHandler`` or ``InvocationInterceptor``.
  ``@RepeatedTest`` is not a retry -- every repetition must pass -- and is
  allowed.
* **Loop-until-green in a harness** (``[HARNESS-RETRY]``): ``until`` or
  ``while !`` on a test runner, a runner followed by ``&& break`` or
  ``|| continue`` inside a loop, ``if <runner>; then ... break``, and a Python
  loop around a runner subprocess that breaks or returns on exit status 0.
  A *network transport* retry is not a test retry and is allowed where it is:
  ``manifest_verify.py`` retries a transfer (never a verdict), at most three
  times, each printed, and ``curl --retry`` fetches an artefact.
* **Disabling without a register entry** (``[UNREGISTERED]``): ``@Disabled``,
  ``@DisabledIf``/``@EnabledIf``, a system-property or environment condition
  on a property the policy does not recognise, a ``@Tag`` it does not
  recognise (``@Tag("quarantine")``), a new ``ExecutionCondition``, a
  surefire or failsafe exclusion (``excludes``, ``excludesFile``, ``groups``,
  ``excludedGroups``, ``skip``, ``skipTests``, ``test``) in a POM, and
  ``-Dtest=!...``, ``-DexcludedGroups=``, ``-Dgroups=`` in a script.
* **A platform condition that turns a test off on Linux** (``[PLATFORM]``),
  the platform every pipeline runs on: quarantine by another name.
* **An opt-in nothing runs** (``[OPT-IN]``): a recognised opt-in property whose
  runner no longer sets it.
* **An assumption that skips on timing** (``[TIMING-ASSUMPTION]``): a condition
  that reads a ``Duration``, the clock, an elapsed time or a timeout. A test
  that skips itself when it is slow hides exactly what this policy exists to
  expose.

The recognised legitimate conditions
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Listed in the check by name, each with why it is not a quarantine; anything
else needs a register entry. Counted on 2026-11-07:

* **Platform scoping** -- ``@EnabledOnOs`` (116) that includes ``LINUX`` and
  ``@DisabledOnOs`` (25) that excludes it: the test exercises a POSIX or
  Linux fact (permission bits, ``/proc``, the Linux binaries) and runs on every
  pipeline.
* **The documented opt-in properties** (16 ``@EnabledIfSystemProperty`` and
  the one ``ExecutionCondition``), each with what runs it, which the check
  verifies sets ``-D<property>=``: ``cometgui.e2e`` (``EndToEndExtension``;
  ``verify-e2e-gates.sh``, ``release-e2e-canonical.sh``), ``cometgui.matrix``
  (``matrix.py``, ``determinism.py``), ``cometgui.perf`` (``performance.py``),
  ``cometgui.limelight.sandbox.url`` (``nightly-limelight-sandbox.sh``) and
  ``cometgui.install.upstream``, which no script sets: its three tests
  (``UpstreamInstallUiTest``, ``CometReleaseInstallTest``,
  ``UpstreamArtefactTest``) download from the tools' real release URLs and are
  run by hand as ``docs/developer/limelight_integration.rst`` says. The nightly
  manifest verification (``R-TEST-08``) checks the same artefacts
  automatically; that these three run on no schedule is stated here rather
  than hidden.
* **The tag** ``e2e`` (``EndToEndExtension.TAG``), which selects; nothing in
  the tree excludes a group.
* **Eight assumptions**, by file and condition: the four Tier A/B step chains
  (``failedStep == null`` -- a step after one that failed, which already fails
  the class), a JVM whose file-name encoding cannot spell a non-ASCII name
  (three; surefire sets ``C.UTF-8``), and a user who can write a read-only
  directory (root).
* ``-Dmaven.test.failure.ignore=true`` **in a falsifiability harness** (20
  uses, ten harnesses): only in a ``scripts/verify-*-gates.sh`` that defines
  both ``assert_testcase`` and ``verify_classes_ran``. Its dirty runs are meant
  to fail; the flag lets Maven finish so each named test's outcome is read
  from the surefire XML after every named test is proved to have run. Nothing
  is ignored.

The check, and where it runs
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

``bash scripts/ci/flakiness-check.sh`` scans every test source (965 files),
POM (14), script (119) and workflow in about 2 s: exit 0 green, 1 red with
every problem printed, 2 refused (no register, or nothing to scan -- never
green). It also refuses a failure-path map id ``Outer$Nested#method`` whose
method name another class of the same outer declares (``[AMBIGUOUS-ID]``; see
the nested-report audit below). It is a step of the pull-request pipeline
(``check-workflows.py`` requires it) and of ``build.sh``'s ``workflows``
stage.

``--self-test`` copies the tree's sources, scripts and POMs into a temporary
directory and injects each forbidden shape into its own copy: 33 defects, each
red with its own kind and words; the clean copy and a fully registered
quarantine of an ordinary test green; a missing register and a tree with no
test sources refused; and three controls on the grader (a case whose defect
was not injected, a red graded against another case's words, an injection that
wrote nothing), each a harness failure; and the exit status of the command
itself -- 40 cases, about 40 s (the fortieth, a rerun on the same line as a
grading harness's permitted ``maven.test.failure.ignore``, was added at the
unit 9 sign-off, when that shape passed the check). It is the ``flakiness``
control of ``verify-all-gates.sh`` (PHASE-15 item 6, floor 40), which only reads the working tree. Because its first case is the clean tree, the control is also red
whenever the tree itself breaks the policy.

How to quarantine, if it must be done
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

#. Capture the failing run's surefire report and console into
   ``scripts/ci/quarantine/<Test>-<date>.log`` and commit it.
#. Open an issue, or write the defect into the phase work log.
#. Add ``@Disabled("<one line: why, and the register>")`` and the
   ``[[quarantine]]`` entry with all seven fields, in one commit.
#. ``bash scripts/ci/flakiness-check.sh`` must be green. If it says
   ``[RELEASE-CRITICAL]``, the test cannot be quarantined: fix it.

The entry is removed in the commit that fixes the test; an entry that covers
nothing needing it -- no ``@Disabled``, unrecognised condition or exclusion --
is red as stale.

Known instances, and their state
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Every test called flaky, intermittent or timing-dependent in ``STATUS.rst``
and ``handoffs/`` up to 2026-11-07:

.. list-table::
   :header-rows: 1
   :widths: 30 16 40 14

   * - Test
     - Seen
     - Cause
     - State
   * - ``ProcessTree`` cancellation order (Phase 03)
     - Phase 03 unit 2b
     - A property true only because a race is always won: an injected
       snapshot-after-destroy passed every test.
     - Fixed (Phase 03): the order is asserted as a fact.
   * - ``ProgressReachesTheInterfaceThreadTest`` (Phase 05)
     - Phases 06, 12-13 gates
     - The test listened on an unmodifiable view it did not keep; JavaFX
       observes the source weakly, so a collection dropped the listener (12/30
       red with a 1 MB young generation).
     - Fixed (FIX-P12-P13).
   * - Phase 10's large-fixture GUI test
     - Phase 10 unit 8
     - Pressing Last disabled the focused button and focus fell into the
       table.
     - Fixed (Phase 10 unit 9).
   * - A wait in step 9 (Phase 14)
     - Phase 14 unit 9
     - Recorded only as "a flaky wait in step 9 fixed" in the unit 9
       sign-off (``handoffs/PHASE-14-worklog.rst``); no cause is written down.
     - Fixed (Phase 14 unit 9).
   * - ``RealAwkwardPathRunTest.longPaths``
     - FIX-P14-P15
     - The path length depended on JUnit's random ``@TempDir`` name (3998
       bytes against 4000).
     - Fixed (``4b17931``).
   * - ``pdv`` controls 5b and 5e
     - Phases 11, 13, 15 unit 4
     - Graded by timing; see :ref:`dev-pdv-timing-controls`.
     - Fixed (Phase 15 unit 9a).
   * - ``ResultsCountsUiTest.theSyntheticRun``
     - Phase 15 unit 6
     - TestFX read the scene graph from the test thread.
     - Fixed (unit 9), below.
   * - ``VariableModificationEditorUiTest`` (``paramui`` 2v)
     - Phases 08, 12, 14
     - The same: ``bounds are not visible in Scene``.
     - Fixed (unit 9), below.
   * - ``ConsoleFloodUiTest.theRetainedHeapStaysWithinItsBound``
     - Phase 15 unit 8
     - The measurement counted dead space the collector left in place.
     - Fixed (unit 9), below.
   * - ``KeyboardOnlyNavigationUiTest``
     - Phase 08
     - Not flaky: deterministic, it fails on a machine with Comet installed
       in the application-data folder. The GUI tests' data folder is
       ``$XDG_DATA_HOME/cometgui`` inside the font stack, which nothing
       creates, so no pipeline meets it.
     - Open, in ``STATUS.rst`` (an isolation defect, tier 1's to route).

None is quarantined.

The TestFX driver read the scene from the test thread (unit 9)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Two flaky GUI tests, one cause. ``ResultsCountsUiTest.theSyntheticRun`` erred
once (``IndexOutOfBoundsException: Index 0 out of bounds for length 0`` in
``Parent.lookupAll``, from ``FxRobot.clickOn``); ``VariableModificationEditorUiTest``
failed now and then with TestFX's ``bounds are not visible in Scene``.
``TestFxUiDriver.clickOn`` called ``robot.clickOn("#id")``, and TestFX resolves
the selector -- walking every ``Parent``'s child list -- and reads the node's
bounds **on the calling thread, the test's**, while the application thread
may be changing the scene: a table filled by a background query, a slot list
rebuilt. The first is a child list emptied between the walk's size check and
its ``get``; the second a node read in the middle of being moved.

*Reproduced on purpose* by ``SceneChurnClickUiTest`` (cometgui-app), which
keeps each window open all the time instead of for milliseconds: the
application thread removes and re-adds the last child of a 200-child list
without pause while a hundred clicks are made on another button; and, in a
second case, moves a button out of its window and back within each of its
tasks, so that a reader on that thread never sees it out of place. With the
old driver: ``IndexOutOfBoundsException: Index 200 out of bounds for length
200`` from the same frames as the original, 4 runs of 4; and ``bounds are not
visible in Scene`` (2 of 3; the third the same torn read caught one check
earlier as ``no nodes were visible``).

*Fixed* in the driver, the test's own code: the node is revealed and its
target point computed in one task on the application thread
(``AbstractFxUiDriver.visibleCentreOf``: the centre of the part its window
shows, refusing a hidden node or one wholly outside its window, as TestFX
did), and TestFX is given only the point. No assertion changed. Evidence:
``SceneChurnClickUiTest`` red on the old driver 4 runs of 4 (its second case 3
of 3) and green on the new one 3 of 3 alone; on ``325472d``, ``mvn -o -B -pl
cometgui-app -am verify`` BUILD
SUCCESS in 2811 s (``cometgui-app`` 576 tests, 91 opt-in skipped, 0 failures;
SpotBugs 0 in every module); the four classes of this unit together
(``SceneChurnClickUiTest``, ``ResultsCountsUiTest``,
``VariableModificationEditorUiTest``, ``ConsoleFloodUiTest``, 16 tests) five
runs alone (load average 5 to 8), 16 of 16 each in about 147 s, and three
beside 96 busy loops (load average about 100), 16 of 16 each in about 260 s;
and every harness that grades a test clicking through this driver,
``verify-all-gates.sh --only`` (four at once, 3111 s): ``paramui`` 87,
``results`` 100, ``install`` 95, ``percolator`` 109, ``pdv`` 107,
``limelight`` 130, ``provenanceui`` 96, ``e2e`` 50 and ``shell`` 30, all PASS.

The flood test measured the collector, not the console (unit 8's residue)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

``ConsoleFloodUiTest.theRetainedHeapStaysWithinItsBound`` read 22,440,392
bytes (21 MB) against its 20 MB bound once in a reactor build, and about 8 MB
alone. It measured ``MemoryMXBean.getHeapMemoryUsage().getUsed()`` after two
requested collections. Each test class runs in its own JVM here
(``reuseForks=false``), so nothing from an earlier class is retained, and the
data folder holds no tool or project, so nothing loads in the background
(checked: the tool list had been read before the first reading in every run).
Two hypotheses were tested and one refuted:

* *Fresh allocation buffers counted as used* -- refuted: the heap pools'
  usage at the end of the collection equalled ``getUsed()`` to the byte, even
  with another thread allocating in between.
* *Dead space counted as used* -- confirmed. G1's full collection leaves the
  dead objects of a region that is almost all live where they lie
  (``MarkSweepDeadRatio``, 5%), still counted as used. Measured against the
  JVM's own class histogram: 7,363,552 to 9,211,104 bytes of the heap at each
  reading were dead, so the used growth read 6,610,488 to 9,798,008 bytes over
  runs whose **live** growth was 6,874,064 to 7,054,408 bytes every time
  (alone, beside 96 busy loops at a load average near 100, with and without
  JaCoCo's agent). Widened on purpose with ``-XX:MarkSweepDeadRatio=90``, the
  old measurement read 31,437,456 bytes and failed the test while the live
  growth was 7,024,144 bytes.

*Fixed* in the measurement: the test now sums the live objects of
``GC.class_histogram`` (the ``DiagnosticCommand`` bean's
``gcClassHistogram``: a full collection, then every live object by class),
leaving out the collector's ``jdk.internal.vm.Filler`` objects. **The 20 MB
bound is unchanged.** It still separates a bounded console from an unbounded
one: with the eviction removed from ``BoundedMessageLog.append`` the live
growth is 35,799,880 bytes (34 MB), red, and ``shell`` control 5b's regular
expression still matches. With ``MarkSweepDeadRatio=90`` the fixed test is
green (2 of 2). Evidence: the fixed test green 3 runs of 3 alone, 2 of 2 with ``MarkSweepDeadRatio=90``,
and in the eight repeats of the driver's evidence above (five alone, three at a
load average near 100); ``shell`` 30 PASS, control 5b red with its own words.

Surefire reports of ``@Nested`` classes: the audit (unit 9)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Surefire 3.5.6 writes one ``TEST-<outer>.xml`` per outer class, with each
nested class's cases inside it and their ``classname`` set to the nested
class's display name. Measured: a whole-class run keeps every nested class's
cases in that file, in a module that reuses forks and in one that does not;
a selection naming two nested classes of one outer
(``-Dtest='Outer$A#m,Outer$B#n'``) keeps both in ``cometgui-params-percolator``
(forks reused), but in ``cometgui-ui`` or ``cometgui-app``
(``reuseForks=false``) each nested class is its own fork and the second
report **overwrites** the first (``SectionIdTest``: one case left of two). A
bare class selector in a list (``-Dtest=A,B``) runs its nested tests in both
kinds of module.

What was checked:

* **Every selection** in the 64 tracked scripts -- 1,739 selectors, from every
  quoted string and assignment, gate harness variables expanded -- for two
  classes of one outer in a ``reuseForks=false`` module, and for a method name
  selected in two classes of one outer: **none**. The provenanceui harness,
  which once had such selections, splits them per nested class and refuses a
  run whose XML holds fewer cases than Maven counted.
* **Every grader that reads a surefire report.** The ten falsifiability
  harnesses with ``assert_testcase`` refuse an absent testcase as a harness
  error, and ``verify_classes_ran`` requires every named method to have run,
  so an overwritten report is loud, never a verdict. ``verify-provenance-gates.sh``
  requires each class's ``tests=`` count to be at least 1 from whole-class
  runs; ``verify-test-gates.sh`` and ``build.sh`` read whole-build reports;
  ``nightly-limelight-sandbox.sh`` reads one class with no nested classes.
  ``scripts/matrix/`` and ``scripts/perf/`` grade the files their classes
  write, not surefire's reports.
* **Method names shared between nested classes** of one outer: 22 in the
  tree, none selected twice by any harness. ``failure_paths.py`` grades the
  failure-path map's two nested ids
  (``HttpDownloaderTest$Failures#aRefusedConnectionIsANetworkFailure``,
  ``AwkwardPathTest$ThroughTheService#theWorkingDirectory``) by method name
  in whole-module reports; both names are unique in their outer classes today,
  and the flakiness check now keeps them so (``[AMBIGUOUS-ID]``).

No grader reads a result that may have been overwritten; nothing needed
repair.


Falsifiability
==============

*A gate that has never been seen to fail has not been shown to work.* Every
gate above ships with a harness that injects the defect the gate exists to
catch, requires the narrowest command that should catch it to exit non-zero
**with the expected diagnostic**, and then requires the same command to pass
once the defect is removed. Every harness damages a copy under ``_build/``;
the working tree is never touched.

``bash scripts/verify-all-gates.sh`` runs all twenty-four in one command. It injects
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
     - A covered class whose test asserts nothing (control 6); and, graded by
       ``scripts/build.sh --gates-pit-root`` over real PIT runs of
       ``cometgui-domain`` (control M): a new package at 0% inside a module
       that stays above 80%; a package whose only non-``KILLED`` mutant is a
       real ``TIMED_OUT`` (a loop its mutant never leaves); a critical
       package excluded from PIT in the module's POM; a ``<targetClasses>``
       entry that matches nothing; and the parent POM changed after the
       report was written.
     - Control 6: ``Mutation score of N is below threshold of 80``, with
       ``Mutations with no coverage 0``, while the coverage gate accepts the
       same defect. Control M: ``BELOW    org.cometgui.domain.weakpackagecontrol
       0/N 0.0%`` and exactly one failure, while PIT's own module threshold
       passes and the module line reads above 80% and ``not graded``; the
       timed-out package ``PASS`` at 100% although ``KILLED`` alone is 75%,
       with ``TIMED_OUT org.cometgui.domain.timedoutcontrol.StepCounter.stepsTo
       line N MathMutator``; ``NO MUTATIONS org.cometgui.domain.secrets
       (cometgui-domain)``; ``EMPTY
       org.cometgui.domain.nosuchcontrolpackage.*``; ``STALE ... pom.xml is
       newer than``; the restored module accepted, every package at or above
       80%; and the working tree refused.

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
     - Twenty-three damaged copies of ``.github/``: among them a renamed step
       script, a dropped required step, ``continue-on-error``, a trailing ``||
       true``, a ``git push`` added to ``release.yml``, and an action pinned to
       a tag.
     - e.g. ``names scripts/ci/traceability.sh, which does not exist``.

   * - CI stubs
     - None injected by the ``pipeline`` control: every nightly and release
       step whose work is not done *is* a stub, and each must exit 70 rather
       than 0. Since Phase 15 the ``hardening`` control's census also requires
       every PHASE-15 stub to say it waits for ``D-006`` or a runner, and
       shows that red twice (:ref:`dev-hardening-falsifiability`).
     - A stub that exited 0 fails ``run-pipeline-locally.sh``, which classifies
       each step before running it -- in its own ``git archive HEAD`` checkout,
       never the working tree, since Phase 15 unit 10.

   * - Tool manifest verification (Phase 15, ``manifest``)
     - ``bash scripts/ci/nightly-manifest-verify.sh --self-test``: 28 cases
       against a loopback server and a synthetic manifest -- bytes, size and
       pins altered, 404/410/a redirect to nothing, an unknown newer release,
       persistent transport failures, wrong member pins in a ZIP, ``.deb`` and
       ``.pkg`` -- and two controls on its own grader
       (:ref:`dev-manifest-verify`).
     - The exact lines, e.g. ``RED (a) CHECKSUM OR SIZE CHANGED: comet 1.0.0
       linux x86-64``; ``RED (b) GONE UPSTREAM -- D-008 upstream availability
       failure``; ``RED (c) NEW UPSTREAM RELEASE: wenbostar/PDV published
       v2.1.0``; and the server's request count where "never retried" is the
       claim.

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

   * - Percolator adapter and version capabilities (Phase 09, ``percolator``)
     - ``scripts/verify-percolator-gates.sh``: twenty-one injections into
       ``cometgui-params-percolator``, ``cometgui-results``,
       ``cometgui-tools``, ``cometgui-workflow`` and ``cometgui-ui`` -- at
       least one per exit gate item and one on the capability probe, three of
       them version-blind, three for ``--no-analytics`` (``D-013``, Phase 10)
       -- items 1, 2, 4, 5, 7 and 9 graded against the real Percolator 3.07.1
       and 3.09. See :ref:`dev-percolator-falsifiability`.
     - Each failing assertion's own words, or the real binary's run: e.g. the
       real 3.09 handed ``-X`` -- ``RUN_PERCOLATOR=invocation percolator
       exited with code 1 ... ==> expected: <SUCCEEDED> but was: <FAILED>``.

   * - Results model and UI (Phase 10, ``results``)
     - ``scripts/verify-results-gates.sh``: twenty-two injections into
       ``cometgui-results``, ``cometgui-workflow``, ``cometgui-ui`` and
       ``cometgui-app`` -- at least one per exit gate item, items 2, 3 and 6
       also through the headless GUI, items 3, 6, 7 and 8 against the large
       fixture or the real K562 Percolator outputs. See
       :ref:`dev-results-falsifiability`.
     - Each failing assertion's own words, e.g. the store factory never
       switching to disk -- ``the child failed: [OUT_OF_MEMORY Java heap
       space] ==> expected: <0> but was: <3>``.

   * - PDV integration and the mzTab exporter (Phase 11, ``pdv``)
     - ``scripts/verify-pdv-gates.sh``: twenty-seven injections into
       ``cometgui-tools``, ``cometgui-workflow``, ``cometgui-ui`` and
       ``cometgui-app`` -- at least two per exit gate item; items 2 and 5
       also against the real PDV 2.7.0, items 1 and 5 through the headless
       GUI, items 6 to 8 against the real K562 sources. See
       :ref:`dev-pdv-falsifiability`.
     - Each failing assertion's own words, e.g. the controller sending the
       next row's reference -- ``PDV drew nothing on row 0 within PT1M;
       seen: [PDVOBS displayed row=1 ...`` from the in-JVM observer of the
       real PDV, which answered ``200 selected``.

   * - Limelight conversion and upload (Phase 12, ``limelight``)
     - ``scripts/verify-limelight-gates.sh``: twenty-nine injections into
       ``cometgui-domain``, ``cometgui-params-percolator``,
       ``cometgui-tools``, ``cometgui-workflow``, ``cometgui-ui`` and
       ``cometgui-app`` -- at least two per exit gate item; items 1, 2, 4 and
       5 also against the real converter, Comet and Percolator, items 3, 4, 6
       and 7 through the headless GUI. See :ref:`dev-limelight-falsifiability`.
     - Each failing assertion's own words, e.g. the label built from the
       literal 3.07.1 -- ``expected: <Rerun Percolator with 3.08.2 for
       Limelight> but was: <Rerun Percolator with 3.07.1 for Limelight>``,
       while the real Linux set stays green.

   * - Provenance UI and reports (Phase 13, ``provenanceui``)
     - ``scripts/verify-provenance-ui-gates.sh``: twenty injections into
       ``cometgui-provenance``, ``cometgui-workflow``, ``cometgui-ui`` and
       ``cometgui-app`` -- at least one per exit gate item, items 1 to 6
       against the real Comet, Percolator and converter run whose digests the
       gate test computes itself, and the design obligations P13-1, P13-7 and
       P13-8, P13-8 also through the headless GUI. See
       :ref:`dev-provenance-ui-falsifiability`.
     - Each failing assertion's own words, e.g. the product's hasher wrong in
       the MD5 alone -- ``.../parameters/comet.params: MD5 recomputed from
       disk ==> expected: <...> but was: <...>``, which a gate comparing the
       record with the product's own digests could not see.

   * - End-to-end tier (Phase 14, ``e2e``)
     - ``scripts/verify-e2e-gates.sh``: both tiers' scenarios run once in a
       ``git archive HEAD`` sandbox, the failure-path map graded over that
       run's reports, the no-fixed-sleep and no-test-bridge checks with their
       self-tests, and four injections into production code -- the compatible
       rerun touching Comet's outputs, ``<=`` made ``<`` in the q-value
       filter, the FASTA dropped from provenance, the Limelight final submit
       skipped -- plus a control H on itself. 48 graded checks in 1084 s at
       unit 9's sign-off; needs network to GitHub and the ``D-006`` local
       fixture. See :ref:`dev-e2e-gate-harness`.
     - Each failing assertion's own words in the named step, e.g. ``the first
       run's Comet trees, unchanged ... mtime`` for the rerun, and ``...
       UP000005640_9606.fasta: not declared as an input`` at step 28.

   * - Version matrix, mutation grading, determinism, performance and the
       quarantine register (Phase 15, ``hardening``)
     - ``scripts/verify-hardening-gates.sh``: in a ``git archive HEAD``
       sandbox, ``-X`` requested from a build without ``XML_OUTPUT`` and the
       matrix's manifest reader skipping a row (item 1); a synthetic PIT
       report with one package below 80% inside a module above it (item 2,
       cheaply; the real-PIT proof is ``tests``' control M); the seed taken
       from the clock and a field removed in the data but not the document
       (item 3); hashing made three times slower and cancellation without its
       polite stop, and a runner with no profile (item 4); a release-critical
       test in the quarantine register (item 6); and a census of every
       workflow step in which a PHASE-15 stub must wait on ``D-006`` or a
       runner. Item 5 is the ``manifest`` control's. See
       :ref:`dev-hardening-falsifiability`.
     - Each verdict's own words, e.g. ``RUN-FAILED
       offer:percolator:3.07.1:linux-x86-64 the same build without XML_OUTPUT
       in its probed set: -X left out, and said so`` with the Comet rows still
       ``RUN-PASSED``; ``RED cancel-fake-tool -- ... median 5034 ms over N=5 is
       over its threshold 250 ms`` with ``hash-throughput`` ``PASS`` beside it.

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
and that is a failure.

It may run them side by side. The default job count is computed from the
cores the process may use (``nproc``, which honours CPU affinity): **1, serial,
at four cores or fewer**, and above that one job per eight cores, capped at
four. Each control still writes its own log, and the controls are graded and
printed in the serial order once all have ended, so the summary, the floors
and the exit code read exactly as a serial run's; a control whose harness exits
non-zero, is killed, or whose worker dies before recording an exit status is a
failure. Controls that write the same working-tree files hold a common lock
(``gate_locks``: ``docs``, ``traceability`` and ``pipeline`` share
``docs/_build`` and the generated pages; ``sbom``, ``depscan`` and
``pipeline`` share ``_build/sbom``) and never overlap -- ``pipeline`` keeps
both locks although since Phase 15 unit 10 it runs every step in its own
checkout and writes neither, because dropping them would buy no time -- and a
concurrent run
fingerprints the four resources every control only reads -- ``_build/m2repo``,
``tools/``, ``scratch/`` and ``.venv/`` -- before and after, and fails if one
changed. The audit behind both is in ``handoffs/FIX-BUILD-TIME-worklog.rst``.

The ``tests`` control proves the evidence census in ``scripts/build.sh``
without running the whole build to reach it: it builds its sandbox once
(control C), rebuilds only ``cometgui-domain`` for each defect, and runs
``bash scripts/build.sh --gates-census-root <sandbox>``, which executes the
census function the gates stage executes and nothing else. That option refuses
the repository itself and every other option, reports a module that was never
built as ``NO EVIDENCE`` and evidence older than its sources as ``STALE``, and
is never a build. Its control M grades the mutation gate the same way, over
real PIT runs of ``cometgui-domain`` in the sandbox, with ``bash
scripts/build.sh --gates-pit-root <sandbox> --gates-pit-modules
cometgui-domain``. Control 5 runs after control C, so that it sizes its
injection from the sandbox's own ``cometgui-ui`` ``jacoco.xml``, never the
working tree's; a missing report is a harness error.

On 2026-08-29 the nine controls graded 123 individual
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

.. _dev-percolator-falsifiability:

The Percolator harness (Phase 09, ``percolator``)
-------------------------------------------------

``bash scripts/verify-percolator-gates.sh`` proves that each of Phase 09's
nine exit gate items, and the capability probe, fails on a defect it exists to
catch. It is registered in ``scripts/verify-all-gates.sh`` as ``percolator``
and takes its shape from ``workflow``; the differences are these.

* **One closed module set per control, nothing installed.** Each control
  names the top module of the narrowest set holding its damaged file and its
  graded tests, and every run is ``mvn -o -pl TOP -am test``: the damaged
  module and everything between it and the tests are compiled from the
  sandbox in one reactor. There is no overlay repository; ``_build/m2repo`` is
  only read, and its ``org/cometgui`` jars are digested before and after. The
  bytecode comparison covers the modules the run built.
* ``scratch/`` (the pinned Comet and Percolator binaries, the 3.09 wrapper and
  its Boost libraries, the ``D-006`` inputs) and ``tools/`` (the font stack of
  the headless GUI tests) are symlinked into the sandbox; the script refuses
  to start, naming the file, when a fixture is missing.
* ``cometgui-app``'s ``build-identity.properties`` carries a build timestamp
  Maven rewrites on every run; it is digested without that one line, which is
  required to be there, as ``paramui`` does.
* The test JVMs' temporary directory is not moved (``cometgui-ui`` and
  ``cometgui-app`` set ``cometgui.surefire.extraArgLine`` themselves), so
  there is no live-process check; every real run here is synchronous and
  bounded.

.. list-table:: The controls ("recorded" names the unit of ``handoffs/PHASE-09-worklog.rst`` whose sign-off made the injection)
   :header-rows: 1
   :widths: 6 6 42 46

   * - Control
     - Item
     - Injected defect
     - Diagnostic required
   * - P
     - probe
     - Recorded (unit 1): the target/decoy judgement of a probe table
       disabled -- a capability granted without its observable.
     - ``a decoy among the targets ==> expected: <[...]>`` (``PSM_TSV_OUTPUT``
       granted); ``damagedDecoyTables`` red; a fully capable build green.
   * - A1
     - ``D-013``
     - New (Phase 10): the command builder no longer emits
       ``--no-analytics``.
     - The argv read back from ``provenance.json`` of every real run:
       ``--no-analytics once in the recorded argv [...] ==> expected: <1> but
       was: <0>``; the builder's two ``D-013`` tests red; the real 3.09 run's
       XML test green.
   * - A2
     - ``D-013``
     - New (Phase 10): the probe observes ``--no-analytics`` but no longer
       passes it on its later runs.
     - ``every launch of the probe carries --no-analytics exactly once ==>
       expected: <[]> but was: <[...``; the typed-out argument arrays red; the
       probed set and a build refusing the switch green.
   * - A3
     - ``D-013``
     - New (Phase 10): the probe never grants ``NO_ANALYTICS_OPTION``.
     - The fully capable build's set: ``expected: <[... NO_ANALYTICS_OPTION
       ...]> but was: <[...]>``; a build refusing the switch green.
   * - 1a
     - 1
     - New: the table reader drops the first data row.
     - The real run's ``tables.percolator-psms.rows ... expected: <3285> but
       was: <3284>``; the real 3.07.1/3.06.5/3.09 tables against awk's counts
       red; the real weights green.
   * - 1b
     - 1
     - New: ``run-percolator`` no longer checks that an artefact exists (an
       empty one is still refused).
     - ``aMissingArtefactFails``: the step fails later, naming a bare path,
       ``.../weights.txt ==> expected: <true> but was: <false>``;
       ``anEmptyArtefactFails`` green.
   * - 2a
     - 2
     - Recorded (unit 3): ``-X`` whenever the build is capable, needed or not.
     - The **recorded** argv of the real 3.07.1 run with Limelight off holds
       ``-X, .../outputs/percolator/pout.xml``; both real 3.09 runs green.
   * - 2b
     - 2
     - New: ``-X`` whenever a stage needs it, capable or not.
     - The real 3.09 chosen with Limelight on is handed ``-X`` and fails:
       ``invocation percolator exited with code 1``; 3.09 without Limelight
       green.
   * - 3v
     - 3
     - **Version-blind**, recorded (unit 2): a stage available iff a
       candidate is older than 3.09.
     - ``ResolutionChangeTest.switchedOnButUnavailable``'s notice and
       ``macosInferred`` red; the real-pair tests ``limelightOn`` and
       ``only309`` green.
   * - 3w
     - 3
     - **Version-blind**, new: the default chosen by "Limelight needs a
       version below 3.09".
     - ``macosInferred``: ``expected: <3.09> but was: <3.07.1>``;
       ``futureWithXmlWins`` red (3.10 skipped for no missing capability);
       ``limelightOn`` and ``limelightOff`` green.
   * - 3u
     - 3
     - **Version-blind**, new: the Percolator section says Limelight can run
       whenever a build older than 3.09 is known.
     - The view-model's and the GUI's inferred-claim tests:
       ``... > but was: <Limelight conversion can run: ...``; the view-model's
       real-pair tests green.
   * - 4
     - 4
     - New: the skip reason no longer names the missing capability.
     - Red in the resolver, the view-model, the GUI and the real run's
       ``provenance.json``: ``... because 3.09 (registered local binary)
       lacks a capability.``
   * - 5
     - 5
     - Recorded (unit 5): the pre-launch PIN check bypassed.
     - The real zero-decoy PIN: ``expected: <Percolator was not started: the
       PIN file .../merged.pin holds 198 target rows and no decoy row ...> but
       was: <invocation percolator exited with code 1 ...``.
   * - 6a
     - 6
     - New: the derived run's plan no longer provides ``run-comet``.
     - The plan test, and the real rerun's record: ``expected:
       <serialise-comet-params run-comet validate-comet-outputs merge-pin> but
       was: <serialise-comet-params validate-comet-outputs merge-pin>``.
   * - 6b
     - 6
     - Recorded (unit 6): "every Comet result step must be reused" disabled.
     - ``cometResultNotRecorded``: ``RerunRefusedException`` not thrown; the
       rerun and the changed-merged-PIN refusal green.
   * - 6c
     - 6
     - Recorded (unit 6): the "nothing changed" refusal disabled.
     - ``nothingChanged``: ``RerunRefusedException`` not thrown; the rerun
       green.
   * - 7
     - 7
     - New: the effective seed dropped from ``provenance.json`` of every run
       that did not succeed.
     - The real failed runs: ``expected: <1> but was: <null>``; gate 7's own
       test of the four successful real runs **green** -- only a failed run
       can show it.
   * - 8a
     - 8
     - New: at most three weight splits read.
     - The constructed four-split file: ``expected: <4> but was: <3>``, the
       two-split file red; the real three-split files green.
   * - 8b
     - 8
     - Recorded (unit 4): the same-features-in-every-split check disabled.
     - ``splitMismatch``: ``PercolatorOutputException`` not thrown.
   * - 9a
     - 9
     - Recorded (unit 5): raw outputs never made read-only.
     - ``decoy-peptides.tsv is writable`` over the four real runs.
   * - 9b
     - 9
     - New: parsing writes back to the raw file it parsed.
     - ``.../weights.txt changed ==> expected: <5370...> but was: <...>``.
   * - H
     - --
     - The harness itself.
     - An unchanged file, a missing anchor, a comment-only injection (a real
       Maven run) and a selection naming a method that does not exist (a real
       run of zero tests) are each a ``HARNESS ERROR``; a green run graded as
       red and a red without its diagnostic are each a recorded failure.

**Version-blind, plainly.** On the real Linux pair (3.07.1 with
``XML_OUTPUT``, 3.09 without) a rule keyed on "version below 3.09" and a rule
keyed on observed ``XML_OUTPUT`` agree, so no test over the real binaries can
tell them apart. Controls 3v, 3w and 3u *require* the real-pair tests to stay
green under the version rule; only the future-version (3.10) and
inferred-claim tests catch them.

What it does not cover: Windows and macOS; export (Phase 10); skipping the
source merged PIN's re-hash in ``RerunSource`` alone, which the re-hash of the
copy masks with the same sentence by design; the probe's version-blind
injection (unit 1) and ``PercolatorRealBinaryTest``, left to unit 1's
sign-off to save a 36 s real-binary class in three runs. One observation from
building it: ``RealPercolatorRunTest.gate4TheSkippedVersionIsRecorded``
checks the reason only for *containing* ``XML_OUTPUT``, which the stage's own
explanation clause also contains; control 4 therefore removes the whole
clause. A defect dropping only "lacks ``XML_OUTPUT``" would still be caught
by the resolver's, the view-model's and the GUI's exact-text tests, but not
by that provenance test.

Measured on 2026-10-08: 94 controls passed in 633 s (10 m 33 s) -- the
baseline 104 s, the final clean run 88 s; the six controls graded on
``RealPercolatorRunTest`` (four real Comet searches and Percolator runs in its
``@BeforeAll``) 43-44 s each, control 4 51 s, 6a 30 s, 3u 18 s, every other
control between 5 s and 14 s. The floor in ``verify-all-gates.sh`` was 94.
Phase 10's unit ``D-013`` added A1-A3: measured the same day, 109 controls
passed in 697 s (11 m 37 s) -- A1 43 s (it is graded on
``RealPercolatorRunTest``), A2 and A3 6 s each -- and the floor is 109.

.. _dev-results-falsifiability:

The results harness (Phase 10, ``results``)
-------------------------------------------

``bash scripts/verify-results-gates.sh`` proves that each of Phase 10's eight
exit gate items fails on a defect it exists to catch. It is registered in
``scripts/verify-all-gates.sh`` as ``results`` and takes its shape from
``percolator`` -- a ``git archive HEAD`` sandbox, anchors that must match
exactly once, bytecode digests proving each injection reached the compiled
class, red read from surefire's XML by testcase and message, siblings
required to stay green, one closed module set per control (``mvn -o -pl TOP
-am test``, nothing installed) and one batched clean re-run. The differences:

* The module sets nest (``cometgui-results`` within ``cometgui-workflow``
  within ``cometgui-ui`` within ``cometgui-app``), and the script checks that
  they still do. The baseline and the clean re-run use the largest set any
  selected control needs, so ``--only`` over ``cometgui-results`` controls
  compiles three modules, not eleven.
* ``scratch/`` is symlinked in for ``scratch/phase10/large`` (the
  1 000 000-row fixture; ``python3 scripts/fixtures/large-results-fixture.py``
  makes it) and the real K562 outputs under ``scratch/scientific-path``; the
  script refuses to start, naming the file, when one is missing.
* ``Set.of``'s iteration order is salted per JVM, so where a failing
  assertion prints a set (the stores' passing and unknown sets), the harness
  matches the number of elements expected and shown, never their order.
* A parameterised method's invocation is graded by its surefire name where
  only some invocations may go red: control 7d requires
  ``checkedInFiles(String, String, int, List)[1]`` and ``[2]`` (two and four
  splits) red and ``[4]`` (the real three-split 3.07.1 file) green.

.. list-table:: The controls ("recorded" names the unit of ``handoffs/PHASE-10-worklog.rst`` whose sign-off made the injection, or whose agent did, as that sign-off lists)
   :header-rows: 1
   :widths: 6 6 42 46

   * - Control
     - Item
     - Injected defect
     - Diagnostic required
   * - 1a
     - 1
     - Recorded (unit 2): the one q-value predicate made exclusive,
       ``q < cutoff``.
     - ``QValueFilterTest`` ``atDefault`` and ``zero``: ``expected: <PASSES>
       but was: <FAILS>``; both stores' ``inclusiveAtTheCutoff``: six
       q-values expected to pass 0.01, five shown; the unknown category green.
   * - 1b
     - 1
     - New: a refused PSM filter text resets the PSM filter to the default.
     - ``DisplayFiltersViewModelTest.refused``: ``expected:
       <DisplayFilters[psm=PsmQValueFilter(q <= 0.05), ...]> but was:
       <DisplayFilters[psm=PsmQValueFilter(q <= 0.01), ...]>`` and a change
       event for refused text; ``bothEndsAndIndependence`` green.
   * - 1c
     - 1
     - New: the peptide filter's edit also sets the PSM filter.
     - ``bothEndsAndIndependence``: ``expected:
       <DisplayFilters[psm=PsmQValueFilter(q <= 0), peptide=...(q <= 1)]> but
       was: <DisplayFilters[psm=PsmQValueFilter(q <= 1), ...]>``; ``refused``
       and ``defaults`` green.
   * - 1d
     - 1
     - New: the range check widened to [0, 10].
     - ``QValueFilterTest.outOfRange``: ``Expected
       java.lang.IllegalArgumentException to be thrown, but nothing was
       thrown.``; the view-model's ``refused`` red; ``notANumber`` green.
   * - 2
     - 2
     - Recorded (unit 8): a listener on the one display-filter state, in the
       composition root, launches ``/bin/true`` through the application's one
       process runner.
     - *GUI*, ``ResultsNoProcessUiTest``: ``the one process runner was called
       while the filters changed ==> expected: <[]> but was: <[[/bin/true],
       ...``.
   * - 3a
     - 3
     - Recorded (unit 3): the disk store's count loop skips the last row.
     - ``counts() ==> expected: <FilterCounts[total=23, passing=2,
       failing=14, unknownQValue=7]> but was: <FilterCounts[total=22, ...,
       failing=13, ...]>``; the in-memory store green.
   * - 3b
     - 3
     - New: the in-memory tally counts a failing row as passing.
     - ``... but was: <FilterCounts[total=23, passing=16, failing=0,
       ...]>``; the disk store, which counts its index itself, green.
   * - 3c
     - 3
     - Recorded (unit 9): the Passing count on screen bound to the failing
       count.
     - *GUI*, ``ResultsCountsUiTest.theSyntheticRun``: ``counts shown that
       differ from the independent counts ==> expected: <[]> but was:
       <[Target PSMs at 0: shown [64, 64, 64, 0], expected [64, 0, 64, 0]``.
   * - 4a
     - 4
     - Recorded (unit 6): the export opens the raw table ``READ`` and
       ``WRITE``.
     - ``aSourceInsideTheRunIsRelativeAndOnlyRead``: ``AccessDeniedException:
       .../outputs/percolator/psms.tsv`` (the raw output is read-only);
       ``theNameIsPinned``, over a writable table, green.
   * - 4b
     - 4
     - New: ``finalise-results`` writes the store's index under ``outputs/``
       instead of ``results/index/``.
     - ``aTableAboveTheLimitIsIndexedUnderResults`` errors with
       ``NoSuchFileException`` naming the run's ``results`` directory;
       ``aTableAtTheLimitStaysInMemory`` green.
   * - 5a
     - 5
     - Recorded (units 6 and 9): the sidecar's cutoff from the default filter.
     - ``constructed/psms-shuffled.tsv at 0, PASSING: the cutoff applied ==>
       expected: <0> but was: <0.01>``; the unknown export green.
   * - 5b
     - 5
     - Recorded (unit 6): the sidecar's ``rowsWritten`` one too many.
     - ``... at 0, PASSING ==> expected: <2> but was: <3>``, and on the real
       3.07.1 table ``expected: <0> but was: <1>``.
   * - 6a
     - 6
     - New: the store factory never switches to the disk store.
     - ``DiskStoreBudgetTest.largeFixtureWithinBudget``: ``the child failed:
       [OUT_OF_MEMORY Java heap space] ==> expected: <0> but was: <3>``; the
       ``readAll`` negative control unchanged.
   * - 6b
     - 6
     - Recorded (unit 8): the results table's items accumulate (``addAll``
       for ``setAll``).
     - *GUI*, ``ResultsLargeFixtureUiTest``: ``at 0: the results table holds
       400 items, more than one page of 200``.
   * - 6c
     - 6
     - Recorded (unit 7): the table view-model asks the store for every row,
       up to its maximum, as one page.
     - ``ResultTableViewModelTest$Page.paging``: ``... status: The table
       could not be read: a page holds at most 200 rows, not 950 ==>
       expected: <1> but was: <0>``; ``pageBound`` green.
   * - 7a
     - 7
     - Recorded (unit 9): the sample standard deviation, *n* - 1.
     - The real K562 weights: ``lnrSp sd ==> expected: <0.07918894423395786>
       but was: <0.09698625332145443>``; the checked-in files red.
   * - 7b
     - 7
     - Recorded (unit 4): the mean absolute weight sums signed values.
     - ``lnrSp mean |w| ==> expected: <0.3363333333333333> but was:
       <-0.3363333333333334>`` and ``lnrSp rank ==> expected: <1> but was:
       <21>``.
   * - 7c
     - 7
     - New: equal mean absolute weights no longer share a rank (ties broken
       by file order).
     - The real K562 weights: ``Charge1 rank ==> expected: <18> but was:
       <19>`` (four all-zero features share rank 18); the constructed ties
       red; the sign consistency green.
   * - 7d
     - 7
     - New: the summary divides by three splits whatever the file holds.
     - ``lnrSp mean ==> expected: <0.1275> but was: <0.085>`` (two splits),
       ``feat_a mean ==> expected: <0.3125> but was: <0.4166666666666667>``
       (four); the real three-split files green.
   * - 7e
     - 7
     - New: a zero weight counted as positive in the sign consistency.
     - ``deltLCn sign ==> expected: <ALL_ZERO> but was: <ALL_POSITIVE>`` (real
       K562), ``mixedWithZero`` red.
   * - 8a
     - 8
     - Recorded (unit 6): unknown-q-value rows written into the passing
       export.
     - ``constructed/psms-shuffled.tsv at 0, PASSING: the export is not the
       raw header and selected lines ==> array lengths differ, expected: <255>
       but was: <857>``; the unknown export green.
   * - 8b
     - 8
     - New: an empty q-value classed as failing rather than unknown (in the
       one predicate, so store and export alike).
     - ``the hand count of unknown rows at 0 ==> expected: <8> but was:
       <7>``; both stores' unknown category: eight spellings expected, seven
       shown; a known q-value at the cutoff green.
   * - H
     - --
     - The harness itself.
     - An unchanged file, a missing anchor, a comment-only injection (a real
       Maven run) and a selection naming a method that does not exist (a real
       run of zero tests) are each a ``HARNESS ERROR``; a green run graded as
       red and a red without its diagnostic are each a recorded failure.

What it does not cover: Windows and macOS; the GUI export, weights and K562
counts tests (``ResultsExportUiTest``, ``ResultsWeightsUiTest``,
``ResultsCountsUiTest``'s K562 methods) and the real run
(``RealRunUiTest``) -- each seen red at unit 9's sign-off, left out here to
save 35-75 s classes in three runs each, so items 4, 5, 7 and 8 are graded on
the unit tests beneath them; and the budgets' time limits, graded only as far
as control 6a's child running out of heap. Two findings from building it:
``ResultTableViewModelTest$Page.paging`` failed control 6c with a bare
``expected: <1> but was: <0>``, so its page-number assertion now carries the
table's status (a message only; no expected value changed); and the real K562
weights *do* have ties -- four all-zero features share rank 18 -- so 7c is red
on the real files too.

Measured on 2026-10-09: 100 controls passed in 567 s (9 m 27 s) -- the
baseline 139 s and the final clean run 126 s (each compiles and runs the
``cometgui-app`` reactor's graded selection), the ``cometgui-results``
controls 6-10 s each, 8a 26 s (the export gate test over 22 tables), the
``cometgui-ui`` and ``cometgui-workflow`` controls 12-16 s, the GUI controls
2, 6b and 3c 26 s, 24 s and 47 s, and control H 11 s. ``--self-test`` and
``--only`` over ``cometgui-results`` controls need only that module set. The
floor in ``verify-all-gates.sh`` is 100.

.. _dev-pdv-falsifiability:

The PDV harness (Phase 11, ``pdv``)
-----------------------------------

``bash scripts/verify-pdv-gates.sh`` proves that each of Phase 11's eight
exit gate items fails on a defect it exists to catch. It is registered in
``scripts/verify-all-gates.sh`` as ``pdv`` and takes its shape from
``results`` -- a ``git archive HEAD`` sandbox, anchors that must match exactly
once, bytecode digests proving each injection reached the compiled class, red
read from surefire's XML by testcase and message, siblings required to stay
green, nested module sets (``cometgui-tools`` within ``cometgui-workflow``
within ``cometgui-ui`` within ``cometgui-app``), ``mvn -o -pl TOP -am test``
with nothing installed, and one batched clean re-run. The differences:

* **A preflight refuses to start**, naming what is missing, without the PDV,
  Comet and Percolator artefacts under ``scratch/phase05/artefacts/``, the
  K562 mzML, FASTA, pepXML and Percolator outputs, the project-local X11 and
  font stacks under ``tools/``, the xkbcomp shim source, the host's ``gcc``
  and ``/usr/bin/timeout`` -- or while a process from an earlier run is still
  alive in the sandbox.
* **Processes.** After every Maven run the script waits up to 20 s for every
  process whose command line or working directory lies in the sandbox (real
  PDV, Xvfb, their ``timeout`` wrappers) to end, then ends what is left and
  says so; one that survives ``KILL`` is a harness error. Between controls,
  anything left in the build directories since the baseline (a test's
  temporary directory) is removed.
* **Several anchors in one file.** Control 7's injections also switch off the
  exporter's own read-back of its references, which would otherwise refuse
  every wrong reference itself: the point is that the *fidelity suite*
  catches it.
* **Tests counted.** Every run prints the number of testcases its fresh
  reports hold, besides the per-selector check that each named class and
  method ran.

.. list-table:: The controls ("recorded" names the unit of ``handoffs/PHASE-11-worklog.rst`` whose sign-off made the injection)
   :header-rows: 1
   :widths: 6 6 42 46

   * - Control
     - Item
     - Injected defect
     - Diagnostic required
   * - 1a
     - 1
     - Recorded (unit 3, K3): Draw draws without installing PDV.
     - ``VisualisationViewModelTest.installThenDraw``: ``expected: <Installing
       PDV 2.7.0: starting.> but was: <PDV 2.7.0 is not installed. ...``, and
       no install asked of the Tool Manager; ``drawAtOnce`` green.
   * - 1b
     - 1
     - New: starting the install holds the JavaFX thread for 40 s.
     - *GUI*, ``PdvFirstUseUiTest`` (real Comet, Percolator and PDV from an
       empty cache): ``pressing Draw did not return within PT30S: the
       interface is frozen``.
   * - 2a
     - 2
     - Recorded (unit 2, J1): the PNG judge's blank-figure floor removed.
     - ``PngFigureTest.floor``: ``Expected ...InvalidFigureException to be
       thrown, but nothing was thrown``; ``everyFilter`` green.
   * - 2b
     - 2
     - New: PDV exiting 0 with a requested figure missing is no longer
       ``NO_FIGURE``.
     - *Real PDV*, ``PdvFiguresRealTest.anAbsentSpectrumIsANamedFailure``:
       ``expected: <NO_FIGURE> but was: <PDV_REPORTED_ERRORS>``; the real
       figure of a known PSM green.
   * - 2c
     - 2
     - New: ``-k p`` for ``-k s`` (the ids read as peptide sequences).
     - *Real PDV*, ``aKnownPsmIsDrawn``: ``PDV exited 0 and wrote no figure
       for "controllerType=0 controllerNumber=1 scan=11367"``.
   * - 3a
     - 3
     - New: the format check bypassed before launch.
     - ``PdvFiguresTest.formatGateBeforeLaunch``: ``expected:
       <...UnsupportedSpectrumFileException> but was:
       <java.lang.IllegalArgumentException>``; ``drawn`` green.
   * - 3b
     - 3
     - New: mzXML marked supported by PDV 2.7.0.
     - ``PdvSpectrumSupportTest.supported``: ``expected: <[MZML, MGF]> but
       was: <[MZML, MZXML, MGF]>`` (the expected ``Set.of`` in either order);
       ``accepted`` green.
   * - 4a
     - 4
     - Recorded (unit 3, K2): the launcher's other-program refusal removed.
     - ``PdvLaunchesTest.anotherProgramIsRefused``: ``Expected
       java.io.IOException to be thrown, but nothing was thrown.``
   * - 4b
     - 4
     - New: PDV started before its launch is recorded.
     - ``theRecordPrecedesTheStart``: ``recorded first ==> expected: <[1]>
       but was: <[0]>``.
   * - 4c
     - 4
     - New: the JAR's MD5 recorded as its SHA-256.
     - ``theRecordPrecedesTheStart``: the independent 64-digit SHA-256
       expected, a 32-digit MD5 found.
   * - 4d
     - 4
     - New: the manifest-version check removed.
     - ``aVersionMismatchIsRefused``: nothing thrown.
   * - 5a
     - 5
     - Recorded (unit 7, Q2): the ``denovo-gui`` command given ``port + 1``.
     - ``DenovoGuiCommandTest.theProductCommand``: ``--port, 41547`` expected,
       ``--port, 41548`` given.
   * - 5b
     - 5
     - New: the controller sends the *next* row's reference.
     - *Real PDV*, ``PdvControllerRealPdvTest``: ``PDV drew nothing on row 0
       within PT1M; seen: [PDVOBS displayed row=1 ...`` -- PDV answered ``200
       selected`` for the wrong row, so only the observer can see it; the
       unknown reference still 404.
   * - 5c
     - 5
     - New: a window's shutdown no longer ends PDV's process.
     - ``PdvControllerTest.shutdownEndsTheProcess``: ``its process had not
       ended 10.0 s after it was told to stop. ==> expected: <CLOSED> but was:
       <FAILED>``.
   * - 5d
     - 5
     - Recorded (unit 7, Q1): another Results run no longer closes the window.
     - ``VisualisationWindowTest``: ``expected: <PDV's window for run run-1
       was closed: ...> but was: <PDV is ready on loopback port ...``.
   * - 5e
     - 5
     - New: the application's ``stop()`` no longer closes PDV's windows.
     - *GUI*, ``PdvLiveWindowUiTest``: ``PDV was still running when the
       application's exit closed the Results section's run: stop() did not
       end PDV itself ==> expected: <false> but was: <true>`` (see
       :ref:`dev-pdv-timing-controls`).
   * - 6a
     - 6
     - Recorded (unit 6, N1): score and q-value columns swapped.
     - ``K562FidelityTest.scores``: ``search_engine_score[1] is "0.00112905",
       the source's score is "2.37114"``; ``charge`` green.
   * - 6b
     - 6
     - New: the PEP column fed the q-value.
     - ``search_engine_score[3] is "0.00112905", the source's
       posterior_error_prob is "9.27998e-07"``.
   * - 6c
     - 6
     - Recorded (unit 5, M2, graded there on the exporter's own tests): the
       accession cut to the first protein.
     - ``accession as Percolator's proteinIds: 6670 compared, 402 failed``.
   * - 6d
     - 6
     - New: a static modification's delta rounded to two places.
     - ``residue 4 (C) lacks the fixed 57.021464:
       EEEC[+57.02]HFYAGGQVYPGEASR``; modifications against the pepXML red.
   * - 6e
     - 6
     - New: the decoy flag written ``0`` for every row.
     - ``counts differ: the sources hold 7 target and 2 decoy PSMs, the mzTab
       9 and 0``; on K562, ``2773 joined source PSMs have no mzTab row``.
   * - 7a
     - 7
     - New: ``spectra_ref`` by instrument scan number, the exporter's
       read-back also off.
     - ``spectra_ref ms_run[1]:index=11367 is beyond ms_run-1.mgf's 3638
       entries``; ``positionAndScanDiffer`` green.
   * - 7b
     - 7
     - New: ``spectra_ref`` by the spectrum's 1-based file position,
       read-back off.
     - ``ms_run[1]:index=181 (entry 181) states CHARGE=3+, the PSM's
       assumed_charge is 2``.
   * - 7c
     - 7
     - New: ``spectra_ref`` to the first row of a shared spectrum, read-back
       off.
     - ``spectra_ref ms_run[1]:index=7 is also target PSM ..._11460_3_1's, and
       PDV selects only the first row of a reference``.
   * - 8a
     - 8
     - Recorded (unit 5, M1): the C-terminal refusal disabled.
     - ``UnrepresentableTest.cTerminalModification``: ``the message names the
       reason UNREPRESENTABLE_MODIFICATION`` (the export still failed, for
       the wrong reason); the exporter's own refusal red.
   * - 8b
     - 8
     - New: the PSM's name dropped from the refusal.
     - ``the message names the PSM ..._3_11367_2_1: The mzTab for PDV was not
       written: a PSM ...``.
   * - 8c
     - 8
     - New: a refused export leaves its staging directory behind.
     - ``nothing at all is left beside it ==> expected: <[]> but was:
       <[.mztab-staging-...]>``.
   * - H
     - --
     - The harness itself.
     - An unchanged file, a missing anchor, a comment-only injection (a real
       Maven run) and a test method renamed in the sandbox so that its
       selection runs zero tests (a real run) are each a ``HARNESS ERROR``; a
       green run graded as red and a red without its diagnostic are each a
       recorded failure.

What it does not cover: Windows and macOS; two GUI and real-PDV grades that
unit sign-offs saw red, left out because they cost a test's full time bound
-- K3 in ``PdvFirstUseUiTest`` (the six-minute ``PDV_BOUND``; reproduced once
while building this script, 401 s) and Q2 in ``PdvControllerRealPdvTest``
(the three-minute readiness bound); item 1's GUI test is graded by 1b and
the real PDV's control path by 5b instead. The bind-race relaunch and a
foreign ``/ready`` stay proved against the fake only.

Measured on 2026-10-11 through ``bash scripts/verify-all-gates.sh --only
pdv``: 107 controls passed in 820 s (13 m 40 s) -- the baseline 133 s and the
final clean run 119 s (each compiles the ``cometgui-app`` reactor and runs
every graded selection: 47 tests, among them both GUI tests and the real-PDV
observer), the ``cometgui-tools``, ``cometgui-workflow`` and ``cometgui-ui``
controls 8-15 s each, 5c 39 s (the fake's ten-second shutdown deadline), 5b
77 s (the observer's one-minute draw bound), the GUI controls 1b 111 s and
5e 56 s, and control H 15 s. No process was left for the script to end. The
floor in ``verify-all-gates.sh`` is 107.

.. _dev-pdv-timing-controls:

Controls 5b and 5e graded by timing (fixed in Phase 15, unit 9a)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Handled under the specification's *Flakiness policy*: each was a defect in
its test, found and fixed at its cause -- no retry, no quarantine, and no
diagnostic widened (5e's now names one outcome where it allowed two). Both were
seen on ``eec0003`` (``handoffs/PHASE-15-worklog.rst``, unit 4's sign-off).

* **5e passed with its defect present** (four harnesses at once). The exit
  ends PDV twice over: ``stop()``'s ``closeAllWindows()``, which waits for
  each process, and then ``results.close()``, whose emptied run makes
  ``VisualisationViewModel.runChanged`` close the window *again*,
  asynchronously on the Run section's threads, unwaited -- ``R-PDV-04`` ends a
  window when its result set closes as well as on exit. With the first
  removed, the second still sent PDV ``SIGTERM`` 20-45 ms into the exit (a
  stack trace on ``requestCancellation`` names it), and PDV was gone before
  ``assertGone`` looked whenever it looked late: holding the assertion back
  until PDV ended made the old test pass with the defect. The test now reads
  PDV's process at the moment the Results section's run chooser is emptied --
  inside ``stop()``, before the second path starts -- so PDV can have ended by
  then only through ``stop()`` itself.
* **5b failed without its diagnostic.** PDV draws its first row by itself
  when its import ends, on a thread of its own, and ``/ready`` can answer 200
  before it has (54 ms before, in a quiet run). That drawing's report could
  arrive after the first selection's ``forgetDisplayed()``: as a stray
  ``row=0`` line at the head of ``seen`` (the run that failed), or -- row 0
  being the first PSM selected, and the report able to carry row 0's own
  peaks -- satisfying that selection with the defect present, moving the red
  to row 1. Delaying that one report by 2 s reproduced the second outcome
  twice in two runs. ``PdvControllerRealPdvTest`` now waits for the opening
  report before it selects anything, and ``PdvDisplayObserver`` refuses with
  an error, rather than attaching, if PDV had drawn before it listened. The
  same delay then gave 5b's own words, and the clean test stayed green.
* The 10 s shutdown bound in that run's output is control 5c's *expected*
  diagnostic (5c passed), not part of 5b; nothing was holding PDV.

Repeat evidence on ``69e1b45``, ``bash scripts/verify-pdv-gates.sh --only
5b,5e`` (each run the baseline, 5b, 5e and the clean re-run, 10 grades): five
runs alone, 10 of 10 each, 301-305 s; three runs beside 96 busy loops on the
64-core host (load average about 100), 10 of 10 each, 794-862 s. Every run
showed 5b's and 5e's own words, and the clean re-run green.

.. _dev-limelight-falsifiability:

The Limelight harness (Phase 12, ``limelight``)
-----------------------------------------------

``bash scripts/verify-limelight-gates.sh`` proves that each of Phase 12's
eight exit gate items fails on a defect it exists to catch. It is registered
in ``scripts/verify-all-gates.sh`` as ``limelight`` and takes its shape from
``pdv`` (:ref:`dev-pdv-falsifiability`): a ``git archive HEAD`` sandbox,
anchors that must match exactly once, bytecode digests proving each injection
reached the compiled class, red read from surefire's XML by testcase and
message, siblings required to stay green, ``mvn -o -pl TOP -am test`` with
nothing installed (so the coverage checks, bound to ``verify``, never run and
no ``cometgui.coverage.*.skip`` is passed), processes left under the sandbox
ended after every run, and one batched clean re-run. The differences:

* **The module sets are not one chain.** ``cometgui-domain`` and
  ``cometgui-params-percolator`` are siblings below ``cometgui-workflow``, so
  the baseline and the clean run use the first of domain, params-percolator,
  tools, workflow, ui, app whose set *holds* every selected control's set --
  computed from the sandbox's own POMs and checked, not assumed.
* **A preflight refuses to start**, naming what is missing, without the
  converter JAR, Comet 2026.03.0, the Percolator 3.07.1 zip and ``.deb``
  under ``scratch/phase05/artefacts/``, ``scratch/percolator/3.09``, the K562
  fixture and Phase 00's outputs and corruptions under
  ``scratch/scientific-path/``, the project-local X11 and font stacks, the
  xkbcomp shim source, ``gcc`` and ``/usr/bin/timeout``.
  ``scratch/limelight-upload-spike/`` is not needed: no selected test runs the
  real importer.
* **No host but this one.** Control 6e gives the upload client no proxy,
  which bypasses the test guard's loopback sinkhole -- the defect that would
  let a test reach a real server. It runs **only**
  ``LoopbackGuardTest.theMachinesOwnAddressIsCaught``, which aims at this
  machine's own non-loopback address and port 9; the guard tests aimed at
  TEST-NET and at a ``.invalid`` name are never run under it.
* **A red in ``@BeforeAll``.** ``RealLimelightCompatibleRerunTest`` does its
  whole real run in ``@BeforeAll``; control 4a's defect fails it there, so
  surefire reports the class's own testcase (no method name), and the control
  grades that line.

.. list-table:: The controls ("recorded" names the unit of ``handoffs/PHASE-12-worklog.rst`` whose sign-off made the injection)
   :header-rows: 1
   :widths: 6 6 42 46

   * - Control
     - Item
     - Injected defect
     - Diagnostic required
   * - 1a
     - 1
     - Recorded (unit 2, K1): the judge's root check reduced to "the element
       has a name".
     - ``LimelightXmlJudgeTest.wrongRoot``: ``expected: <WRONG_ROOT> but was:
       <SCHEMA_INVALID>``; the real output still accepted.
   * - 1b
     - 1
     - New: the judge's schema validation skipped.
     - ``phase00BogusElement`` and ``phase00MissingAttribute``: ``Expected
       ...LimelightConversionException to be thrown, but nothing was
       thrown``; the truncated file still refused.
   * - 1c
     - 1
     - New: ``output.sha256`` of ``limelight.converted`` recorded as the MD5.
     - *Real*, ``RealLimelightConversionTest.gate1ARealConversion``: a
       64-digit SHA-256 expected, a 32-digit MD5 found.
   * - 2a
     - 2
     - New: the adapter trusting exit 0 when the staging file is missing or
       empty (judge bypassed, move skipped).
     - ``LimelightConverterTest.exitZeroWithNoFile`` and
       ``exitZeroWithAnEmptyFile``: nothing thrown; a malformed file and a
       non-zero exit still fail.
   * - 2b
     - 2
     - New: the judge's existence check removed.
     - *Real converter*, ``anUnknownOptionExitsZero``: ``expected:
       <...LimelightConversionException> but was:
       <java.nio.file.NoSuchFileException>``; a real conversion still kept.
   * - 2c
     - 2
     - New: the judge's emptiness check removed.
     - ``absentEmptyUnreadable``: ``expected: <EMPTY_OUTPUT> but was:
       <NOT_WELL_FORMED>``.
   * - 3a
     - 3
     - Recorded (unit 4, N1): ``CompatibilityAction.forStage`` offering the
       rerun whenever resolution selects any build.
     - ``CompatibilityActionTest$NoRerun``: ``expected:
       <REGISTER_LOCAL_BINARY> but was: <RERUN_PERCOLATOR>``; ``linuxSet``
       green.
   * - 3b
     - 3
     - Recorded (unit 4, N2): the label from the literal ``3.07.1``.
     - ``whateverResolutionReturns`` (3.08.2) and ``aFutureBuild`` (3.10) red;
       ``linuxSet`` green -- 3.07.1 is what resolution returns today.
   * - 3c
     - 3
     - Recorded (unit 6, Q1): the conversion controls enabled for any run with
       a readiness answer.
     - ``LimelightPaneTest.incompatible``; *GUI*,
       ``LimelightRerunUiTest.gate3`` against the real 3.09 run: ``the section
       before anything is attempted (4 failures)``.
   * - 4a
     - 4
     - Recorded (unit 4, N3): a run that is not ready no longer refused.
     - *Real*, ``RealLimelightCompatibleRerunTest`` (``@BeforeAll``):
       ``expected: <...LimelightConversionRefusedException> but was:
       <java.util.NoSuchElementException>``.
   * - 4b
     - 4
     - New: the compatible rerun's choice enabling no downstream stage.
     - *GUI*, ``LimelightRerunUiTest.gate4``: no derived run appears -- ``the
       runs: [<one run>] ==> expected: <true> but was: <false>``.
   * - 4c
     - 4
     - New: the derived run no longer recording the Percolator its source
       ran.
     - *Real*, ``gate4BothExecutionsInProvenance``: ``expected: <3.09> but
       was: <none>``; ``gate4TheRerunConverts`` green.
   * - 5a
     - 5
     - New: the cutoff's default 0.05.
     - ``LimelightConversionsTest.gate5TheDefault``: ``expected:
       <LimelightQValueCutoff[value=0.01]> but was: <...[value=0.05]>``.
   * - 5b
     - 5
     - New: ``-q`` not emitted.
     - *Real converter*, ``theCutoffReachesTheFile``: ``carries the
       Percolator PSM q-value filter 0.01, not the cutoff 0.05 the conversion
       asked for``; the real 0.01 conversion cannot see it and stays green.
   * - 5c
     - 5
     - New: saving the cutoff also writes the display filters' view state.
     - ``gate5ChangingTheLimelightCutoff``: ``expected:
       <[results/limelight-settings.json]> but was:
       <[results/limelight-settings.json, results/view-state.json]>``.
   * - 6a
     - 6
     - New: the workflow's upload confirmation reusable.
     - ``LimelightUploadsTest.aConfirmationIsUsedOnce``: nothing thrown.
   * - 6b
     - 6
     - Recorded (unit 7, R2): the port's single-send check always passing.
     - ``ProjectLimelightUploadPortTest.prepareThenSend``: nothing thrown.
   * - 6c
     - 6
     - Recorded (unit 7, R1): a changed server no longer withdrawing the
       destination.
     - ``aChangeWithdrawsTheDestination``: ``expected: <The destination shown
       was withdrawn because ...``.
   * - 6d
     - 6
     - New: Upload sending as soon as the destination is shown.
     - ``LimelightUploadViewModelTest.uploadSendsNothing``; *GUI*,
       ``LimelightUploadUiTest.gate6`` against the fake: ``limelight.uploading``
       recorded before Send.
   * - 6e
     - 6
     - New: the upload client given ``NO_PROXY``, bypassing the guard.
     - ``LoopbackGuardTest.theMachinesOwnAddressIsCaught`` only:
       ``expected: <[CONNECT <own address>:9 HTTP/1.1]> but was: <[]>``.
   * - 6f
     - 6
     - Recorded (unit 5, P1): plain http accepted to any host.
     - ``LimelightServerTest.plainHttpOnlyToLoopback``: nothing thrown.
   * - 7a
     - 7
     - New: the upload no longer adds the key to the redactor it writes with.
     - ``theServersWordsAreRedacted``: ``expected: <Optional[no such key:
       [REDACTED]]> but was: <Optional[no such key: LLworkflow-seeded-...``.
   * - 7b
     - 7
     - New: the key written to the upload log, unredacted (two anchors: a
       line carrying it, and the log's own redaction off).
     - ``LimelightUploadLeakTest`` walk: ``the seeded credential was written
       to a file ==> expected: <{}> but was: <{.../limelight-upload.log=utf-8``.
   * - 7c
     - 7
     - New: ``SessionSecrets.admit`` no longer registering the credential.
     - ``SessionSecretsTest.admittingRegisters``: ``expected: <1> but was:
       <0>``.
   * - 7d
     - 7
     - New: the key field not emptied once its key is taken.
     - ``LimelightUploadPaneTest.theKeyField``; *GUI*,
       ``LimelightUploadUiTest.gate7``: ``the window and the console (2
       failures)``, the seeded ``CGSEED-`` key read back from the window.
   * - 8a
     - 8
     - Recorded (unit 3, L3): ``saveSettings``' decoy refusal unreachable.
     - ``gate8DecoyImportAgainstANoDecoyRun``: nothing thrown;
       ``gate8ARunWithDecoys`` green.
   * - 8b
     - 8
     - Recorded (unit 6, Q2): decoy import enabled whenever the settings are.
     - ``LimelightViewModelTest.aConvertibleRun``: ``gate 8: refused at
       configuration time ==> expected: <false> but was: <true>``; the pane's
       ``convertible`` red.
   * - 8c
     - 8
     - New: a pout XML without decoy PSMs judged able to import them.
     - ``LimelightConversionRequestTest``: ``expected:
       <java.lang.IllegalArgumentException> but was:
       <...LimelightConversionException>``; the workflow's gate 8 red.
   * - 8d
     - 8
     - Recorded (unit 2, K3): "decoys present but not imported" unreachable.
     - ``decoysWithoutImportAreRefused``: nothing thrown.
   * - H
     - --
     - The harness itself.
     - An unchanged file, a missing anchor, a comment-only injection (a real
       Maven run) and a test method renamed so its selection runs zero tests
       are each a ``HARNESS ERROR``; a green run graded as red and a red
       without its diagnostic are each a recorded failure.

What it does not cover: Windows and macOS; a real Limelight server (never
contacted); item 1's GUI test (item 1 is graded on the judge and the real
workflow conversion); Q2's GUI grade (8b is graded on the view-model and the
pane); R1 in the GUI, which unit 7 saw stay green (the GUI test changes the
project, not the server). The two defensive layers the work log records as
untestable from outside (L2, P2) are not controls; 7b grades P2's layer only
together with a line that carries the key.

Measured on 2026-10-12 through ``bash scripts/verify-all-gates.sh --only
limelight`` at ``35413e5``: 130 controls passed in 1129 s (18 m 49 s) -- the
baseline 239 s and the final clean run 221 s (each compiles the
``cometgui-app`` reactor and runs every graded selection: 71 tests, among them
the real conversion, the real compatible rerun and three headless GUI
classes), the ``cometgui-domain``, ``cometgui-params-percolator``,
``cometgui-tools`` and ``cometgui-workflow`` controls 5-33 s each, the
``cometgui-ui`` and ``cometgui-app`` unit controls 19-27 s, the GUI controls
3c 44 s, 4b 49 s, 6d 66 s and 7d 65 s, and control H 18 s. No process was left
for the script to end. The floor in ``verify-all-gates.sh`` is 130.

.. _dev-provenance-gate-test:

Phase 13's gate test on real runs (``RealProvenanceGateTest``)
---------------------------------------------------------------

``cometgui-app``'s ``org.cometgui.app.config.RealProvenanceGateTest`` proves
Phase 13's six exit gate items on real runs, **against the files on disk and
never against the record itself** (design decisions P13-7 and P13-10 in
``handoffs/PHASE-13-worklog.rst``). It lives in ``cometgui-app`` because item
2's "match the files in the tool cache" needs a real cache with real
completion markers, which only ``cometgui-install`` makes and
``cometgui-workflow`` cannot reach (P13-10).

**The world** (``GateWorld``, built once for the class): Comet 2026.03.0,
Percolator 3.07.1 and the Limelight converter 2.8.1 installed by the product's
own installer -- ``ToolManagerWiring.toolManager`` over an **empty** cache,
the real artefacts fetched from the mirror through a loopback server, with the
real probe and the real completion markers; a completed Comet and Percolator
run of the two K562 files against the 1000-record subset through the
product's own run port (``RunWiring.port``); its Limelight conversion; a
refused and a submitted upload to the loopback fake; and both exports
rendered through the read model from one loaded record.

.. list-table::
   :header-rows: 1
   :widths: 8 30 62

   * - Item
     - Tests (nested class)
     - What is proved
   * - 1
     - ``ListedFiles``: ``everyListedFileMatchesTheDisk``,
       ``everyListedLogMatchesTheDisk``, ``everyOutputOnDiskIsListed``
     - Every one of the 28 file records has an MD5 and a SHA-256 equal to the
       test's own digests and size of the file on disk, against a hand-typed
       census of the records; the 6 archived logs likewise; every regular file
       under ``outputs/`` at the seal, enumerated from disk, is listed, and the
       one written after it (the conversion) is named by its
       ``limelight.converted`` event with its SHA-256.
   * - 2
     - ``Tools``: ``comet``, ``percolator``, ``converter``, ``threeInstalls``
     - Each tool record's version, path (inside the cache), MD5, SHA-256,
       artefact identity, probed capabilities and release tag equal both the
       test's own digest of the executable and the completion marker the test
       found by walking the cache -- and a hand-typed expectation. The
       converter, recorded in ``events.log`` after the seal, the same way.
       The walk finds exactly the three installs.
   * - 3
     - ``Commands``: ``onePerSpectrumFile``, ``percolatorReadsTheMergedPin``,
       ``displayedIsRecorded``
     - One Comet argument array per spectrum file, executable first, each with
       exactly its own input, its own ``-N`` base and ``-P`` naming the
       archived ``comet.params``; Percolator's names the merged PIN once; the
       read model's displayed command, split by the test's own tokenizer, is
       the recorded array element by element, in the Tools part and in the
       timeline.
   * - 4
     - ``FailedAndCancelled``: ``failedRun``, ``cancelledRun``
     - A real run Comet aborts on a damaged spectrum file (exit 134) and a real
       run cancelled while Comet searches the whole proteome each open through
       the read model, sealed, with the status in the record and the summary,
       the stage's status and exit in the timeline, and exactly the outputs of
       the stages that did not complete marked partial -- in the record, in
       the view and in the warnings -- with the test's own digests of each.
   * - 5
     - ``Exports``: ``exportsAreTheRunsOwn``, ``everyFactIsInTheReport``
     - Both exports equal the run's own ``provenance.json`` and
       ``provenance.rst`` byte for byte; every fact of the exported JSON --
       summary, application, every setting in order, every tool and execution
       field including start, end, duration and exit code, every log, every
       file row -- is in the exported RST where the report puts it.
   * - 6
     - ``Secrets``: ``noSeededSecretAnywhere``, ``theEnvironmentIsTheProductsOwn``,
       ``theSearchFindsEveryPlantedForm``
     - Five secrets, each entered by its own path -- the Limelight submit key
       in a refused and a submitted upload, a password in a Limelight server
       address, a URL password and an ``api_key=`` value in an applied preset's
       name, and a URL password in a preset name over 256 characters (a long
       carrier, its length asserted) -- are in no form in any file of the
       project or either export, every file enumerated from disk by Phase
       12's ``SecretSweep``; a control plants every form of each into a copy
       of the record and requires the sweep to find it.

**Its independence rules**, each the answer to a way a gate test can end up
testing the product against itself:

* **Its own digests.** Every digest it compares with the record is
  ``OwnDigests`` (``org.cometgui.app.testing``): a streaming loop over
  ``java.security.MessageDigest``, never the product's ``StreamingHashService``
  or ``CachingHashService``, and never the product's *Verify against disk*.
* **Its own readers.** The record is read with ``IndependentJson``, an RFC 8259
  reader sharing no code with the product's JSON reader or ``ManifestReader``;
  the report with ``IndependentRst``, written from the layout
  ``ProvenanceReportWriter`` documents and sharing no code with it. A reader and
  a writer that agree on a wrong member name agree with each other.
* **The cache, not the record, says where a tool is.** Each tool is matched to
  the completion marker the test finds by walking the tool cache, read with the
  same independent JSON reader -- never located from the record's path alone.
* **Hand-typed expectations** beside every comparison: the file census, the
  outputs at the seal, versions, tags, artefacts and capabilities.

The injections it was seen red under at unit 4's sign-off (the database
recorded as an input by no step; the RST printing a log's MD5 under
"SHA-256"; redaction skipped for values over 200 characters) are in the work
log.

**What it does not prove, and why.** A run killed before it sealed its record
needs an engine that dies mid-run, which neither the product nor the test
support can make honestly; the reading of such a directory is
``ProvenanceStatesTest`` (``cometgui-workflow``) over a constructed one. The
only environment variable the product gives a tool is ``LANG=C.UTF-8``
(asserted), so no secret can be seeded there. A secret typed as a Comet
parameter value is written verbatim into the archived ``comet.params``, which
``AC-PRV-04`` requires to be the exact executed file, so the seeds enter the
settings through preset names instead.

Linux only (``@EnabledOnOs(LINUX)``): 17 tests, about 26 s. Files it reads
outside its module: ``scratch/fixture/`` (both K562 mzML files and the UniProt
proteome) and, under ``scratch/phase05/artefacts/``,
``v2026.03.0__comet.linux.exe``,
``rel-3-07-01__percolator-noxml-ubuntu-portable.zip``,
``rel-3-07-01__percolator-noxml-v3-07-linux-amd64.deb`` and
``v2.8.1__cometPercolator2LimelightXML.jar``; and ``manifests/tools.json`` on
the class path.

The same section through the running application is
``org.cometgui.app.gui.ProvenanceSectionUiTest``: a real run through the Run
section, then its record on screen -- tool digests equal ``OwnDigests``, Copy
MD5, Copy SHA-256 and Copy command read back from the clipboard as exact
values, both exports byte-identical to the run's documents, an existing target
refused and Replace offered, every file verifying, Open file location
recorded as exactly ``xdg-open <folder>`` by a recording runner -- and a
cancelled and a failed real run opened from the list, their partial outputs
shown as ``PARTIAL``; and no port call on the JavaFX thread. See
:doc:`provenance_ui`.

.. _dev-provenance-ui-falsifiability:

The provenance UI harness (Phase 13, ``provenanceui``)
------------------------------------------------------

``bash scripts/verify-provenance-ui-gates.sh`` proves that each of Phase 13's
six exit gate items, and the phase's design obligations P13-1 (the bounded
rename retry and a viewer that holds no descriptor), P13-7 (the product's
*Verify against disk* comparing both digests) and P13-8 (nothing slow on the
JavaFX thread), fails on a defect it exists to catch. It is registered in
``scripts/verify-all-gates.sh`` as ``provenanceui`` and is **not** a stage of
``scripts/build.sh``. It takes its shape and its plumbing from ``limelight``
(:ref:`dev-limelight-falsifiability`): a ``git archive HEAD`` sandbox, anchors
that must match exactly once, bytecode digests proving each injection reached
the compiled class, red read from surefire's XML by testcase and message,
siblings required to stay green, ``mvn -o -pl TOP -am test`` with nothing
installed, processes left under the sandbox ended after every run, a clean
re-run of every selection the dirty runs used, and a control H on itself. The
differences:

* **Two test counts, which must agree.** Surefire runs each ``@Nested`` class
  a selection names as a test set of its own, and writes each set's report
  under the *outer* class's name, so a selection naming two nested classes of
  ``RealProvenanceGateTest`` keeps only the last one's testcases in the XML;
  two tests of one name fold the same way (unit 1's finding). Every run
  therefore also sums Maven's module summary lines (``Tests run: N, ...``
  without ``-- in``), and a run whose XML holds fewer testcases than Maven ran
  is a ``HARNESS ERROR``, as is either count at zero. A red is a failed
  testcase by either count.
* **Batched baseline and clean run.** The baseline and the final clean run
  split the union of the selections into as few Maven runs as keep every
  report whole -- no run names two classes of one test class -- and compare
  every run's bytecode with the first's. Each nested class of
  ``RealProvenanceGateTest`` rebuilds its shared world (three installs, a real
  run, a conversion, two uploads), about 20 s, so the two cost about four
  minutes each.
* **The expected words are read only from the failure section** the XML
  wrote into the log, never from what the build printed, so a test that only
  prints the words cannot pass a control.

.. list-table:: The controls ("recorded" names the unit of ``handoffs/PHASE-13-worklog.rst`` whose sign-off made the injection)
   :header-rows: 1
   :widths: 6 8 40 46

   * - Control
     - Item
     - Injected defect
     - Diagnostic required
   * - 1a
     - 1
     - Recorded (unit 4, M3): the database declared by neither
       ``hash-inputs`` nor ``run-comet``.
     - *Real*, ``everyListedFileMatchesTheDisk``: ``the files the record
       lists ==> expected: <[... input fasta subset.fasta, ...``; the logs
       green.
   * - 1b
     - 1
     - New: the product's one hasher digesting a byte short, for the MD5
       alone.
     - *Real*, ``everyListedFileMatchesTheDisk`` and
       ``everyListedLogMatchesTheDisk``: ``MD5 recomputed from disk ==>
       expected: <...> but was: <...>``, and no SHA-256 red. The record agrees
       with the product; only a gate with digests of its own (P13-7) sees it.
   * - 2a
     - 2
     - New: Comet's tool record without its probed capabilities.
     - *Real*, ``Tools.comet``: ``comet: the capabilities its install probed
       ==> expected: <[COMPLETE_PARAMS_QUERY, PEPXML_OUTPUT, PIN_OUTPUT]> but
       was: <[]>``; ``percolator`` green.
   * - 2b
     - 2
     - New: Percolator's tool record without its artefact identity.
     - *Real*, ``Tools.percolator``: ``the artefact its install came from ==>
       expected: <https://.../percolator-noxml-ubuntu-portable.zip sha256:...>
       but was: <null>``; ``comet`` green.
   * - 2c
     - 2
     - New: Comet's tool record without its release tag.
     - *Real*, ``Tools.comet``: ``expected: <v2026.03.0> but was: <null>``.
   * - 3a
     - 3
     - New: the last of a step's several invocations not recorded.
     - *Real*, ``onePerSpectrumFile``: ``two spectrum files, two Comet
       invocations ==> expected: <2> but was: <1>``; Percolator's argument
       array green.
   * - 4a
     - 4
     - New: the engine recording a failed or cancelled stage's outputs as
       completed.
     - *Real*, ``failedRun`` and ``cancelledRun``: ``partial in the record:
       [k562_3 cancelled] ==> expected: <[output pepxml k562_3.pep.xml, output
       pin k562_3.pin]> but was: <[]>``.
   * - 4b
     - 4
     - Recorded (unit 3, K4): the read model warning only of ``FAILED``
       files.
     - ``ProvenanceStatesTest.failedRun``: ``expected:
       <Warning[source=PARTIAL_FILE, ...k562_2.pin ... is recorded as
       partial]>``; ``cancelledRun`` and the census red.
   * - 5a
     - 5
     - Recorded (unit 4, M4): the RST printing a log's MD5 under "SHA-256".
     - *Real*, ``everyFactIsInTheReport``: ``Tools/Tool 1, comet ==>
       expected: <{...``; ``exportsAreTheRunsOwn`` stays green, as it must --
       one writer made both.
   * - 5b
     - 5
     - New: Export JSON copying the sealed file instead of rendering the
       loaded model.
     - ``ProvenanceExportTest.renderedNotCopied``: ``NoSuchFileException``;
       ``oneObject``: ``expected: <2> but was: <0>``; ``byteIdentical`` green.
   * - 6a
     - 6
     - Recorded (unit 4, M1): the JSON writer skipping redaction over 200
       characters.
     - *Real*, ``noSeededSecretAnywhere``: ``...provenance.json holds the
       secret as [...], seeded as the password of a URL in a long (> 256
       characters) ...``.
   * - 6b
     - 6
     - New: the RST writer redacting nothing.
     - *Real*, ``noSeededSecretAnywhere``: ``.../provenance/provenance.rst
       holds the secret``, and no JSON among the hits.
   * - 6c
     - 6
     - Recorded (unit 3, K3): a read-model warning's message not redacted.
     - ``SeededSecretTest.absentEverywhere``: ``expected: <[]> but was:
       <[failed reading Zq7Kx-limelight-91xW, ...``.
   * - 6d
     - 6
     - Recorded (unit 5, N3): the app's read model built over
       ``SecretRedactor::patternsOnly``.
     - ``ProvenanceSessionRedactionTest``: ``once admitted, nowhere (5
       failures)``.
   * - d1
     - P13-1
     - Recorded (unit 1, O2): ``AccessDeniedException`` no longer retried.
     - ``AtomicDocumentWriterRetryTest``: ``expected: <10> but was: <1>``; a
       bare ``FileSystemException`` still retried.
   * - d2
     - P13-1
     - Recorded (unit 1, O1): one rename attempt fewer.
     - ``successOnTheLastAttempt``: ``expected: <refusal #10> but was:
       <refusal #9>``.
   * - d3
     - P13-1
     - Recorded (unit 1, O5): ``ManifestReader.readFrom`` keeping a
       ``FileChannel``.
     - ``ReadersHoldNoDescriptorTest$Manifest``: ``expected: <0> but was:
       <1>``; the event-log reader green.
   * - d4
     - P13-7
     - Recorded (unit 3, K1): *Verify against disk* comparing the SHA-256
       only.
     - ``eitherDigestAloneDiffers``: a record whose MD5 alone is wrong
       verified ``MATCHES``.
   * - d5
     - P13-8
     - New: the view-model opening a record on the interface thread.
     - ``ProvenanceViewModelTest``: the port called outside the background
       executor; *GUI*, ``noPortCallOnTheFxThread``: ``a port call on the
       JavaFX application thread stalls the window``.
   * - d6
     - unit 5
     - New: Copy SHA-256 of a tool copying its MD5.
     - ``ProvenanceViewModelTest$Copies.exactValues``; *GUI*,
       ``aCompletedRun``: ``Copy SHA-256 of Comet ==> expected: <64 hex> but
       was: <32 hex>``.
   * - H
     - --
     - The harness itself.
     - An unchanged file, a missing anchor, a comment-only injection (a real
       Maven run), a test method renamed so its selection runs zero tests, a
       run counted at zero tests and an XML report holding fewer testcases
       than Maven ran are each a ``HARNESS ERROR``; a green run graded as red
       and a red without its diagnostic are each a recorded failure.

What it does not cover: Windows and macOS (P13-1's retry is exercised through
its seam only, P13-6's macOS and Windows argv are not graded); a run killed
before it seals, on a real engine; the parameter-origin settings (unit 2) and
the discrepancy warnings (unit 3b), which are not exit gate items; unit 5's
Replace offer and ``xdg-open`` status (N1, N2). Their injections are in the
work log.

Measured on 2026-10-14 by ``bash scripts/verify-provenance-ui-gates.sh`` at
``44c4617``: 96 controls passed in 1241 s (20 m 41 s) -- the baseline 251 s and
the final clean run 236 s (each six Maven runs over the ``cometgui-app``
reactor, 64 tests, every nested class of ``RealProvenanceGateTest`` rebuilding
its world and the headless GUI's three real runs), the thirteen
``cometgui-app`` controls 33-69 s each, the ``cometgui-provenance`` and
``cometgui-workflow`` controls 7-17 s, and control H 14 s. No process was left
for the script to end. The floor in ``verify-all-gates.sh`` is 96.

.. _dev-hardening-falsifiability:

The hardening harness (Phase 15, ``hardening``)
-----------------------------------------------

``bash scripts/verify-hardening-gates.sh`` proves that Phase 15's exit gate
items fail on the defects they exist to catch. It is registered in
``scripts/verify-all-gates.sh`` as ``hardening`` (PHASE-15 items 1, 2, 3, 4 and
6) and is **not** a stage of ``scripts/build.sh``. It is deliberately lean: no
spectra, seven Maven runs, each ``-pl cometgui-app -am test`` restricted to the
opt-in classes a suite names -- the version matrix's tools part, the
determinism suite's synthetic half, two performance measurements in quick mode.

It works in two copies of ``git archive HEAD`` (``--worktree``: the tracked
files as they are on disk, said loudly): ``_build/hardening-gate-sandbox``,
where Maven runs, with ``tools/``, ``scratch/``, ``.venv/`` and
``_build/m2repo`` linked in read-only; and ``_build/hardening-gate-tree``, a
plain copy for the script-only controls. Each Maven injection's anchor must
match exactly once and be gone after; after the run and before grading, the
damaged class's compiled form must differ from the clean baseline and every
other compiled class be identical to it; the red must carry its own words;
then the file is restored and ``sha256sum -c`` proves it, and the next clean
run's bytecode proves the restoration reached the classes. A script or data
injection is proved by its digest and its anchor.

.. list-table:: The controls
   :header-rows: 1
   :widths: 8 46 46

   * - Control
     - Defect, or what is run
     - Graded on
   * - ``6a``, ``6b``
     - Item 6. The real ``flakiness_check.py`` over the plain copy; then
       (new) a complete seven-field register entry naming
       ``TierACanonicalScenarioTest``.
     - ``flakiness-check: OK ... no release-critical test quarantined`` with
       ``0 entries``; then ``RED [RELEASE-CRITICAL] scripts/ci/quarantine.toml:
       org.cometgui.app.e2e.tiera.TierACanonicalScenarioTest is
       release-critical (end-to-end, failure-path map, gate harness,
       traceability)``.
   * - ``K``, ``Ka``, ``Kb``
     - The census (new): every ``run:`` step of every workflow, read through
       ``check-workflows.py --list-steps`` and classified exactly as
       ``run-pipeline-locally.sh`` classifies it; every stub run in an empty
       environment; every PHASE-15 stub required to wait on ``D-006`` or a
       runner. Then ``--waits-for`` removed from ``nightly-large-dataset.sh``,
       and ``nightly-determinism.sh`` made a PHASE-15 stub.
     - ``census: OK`` -- on 2026-11-07, 36 step scripts in five workflows, 22
       real and 14 stubs, four of them PHASE-15's (the large dataset, the
       determinism and performance fixture halves, the Windows RAW test); then
       ``census: RED -- scripts/ci/nightly-large-dataset.sh is a PHASE-15
       stub ... that does not say it waits for D-006 or a runner``, and the
       same for ``nightly-determinism.sh``.
   * - ``1a``, ``1c``, ``1b``
     - Item 1. ``run-matrix.sh --part tools --offline``, clean (the bytecode
       baseline); ``matrix.py``'s manifest reader skipping Percolator 3.06.5
       for Windows, graded by ``matrix.py grade`` over 1a's report;
       ``PercolatorCommands`` requesting ``-X`` from a build without
       ``XML_OUTPUT`` (both recorded, unit 6).
     - ``VERDICT GREEN`` with every managed Linux row ``RUN-PASSED``; ``only in
       the product [('percolator', '3.06.5', 'windows-x86-64')]``; both managed
       Percolator rows ``RUN-FAILED`` at ``the same build without XML_OUTPUT in
       its probed set: -X left out, and said so``, Comet ``RUN-PASSED``.
   * - ``3a``, ``3b``, ``3c``
     - Item 3. ``run-matrix.sh --part determinism-synthetic``, clean;
       ``PercolatorSettings.valueText`` giving the seed from the clock
       (recorded, unit 7); ``determinism.py check-doc`` clean, then (new) the
       data removing a pepXML field under a name the document does not state.
     - ``VERDICT GREEN``; ``repeat-2 (repeat): artefact synthetic-psms DIFFERS
       -- out/psms.tsv``; ``fields removed differ -- only in the document
       ['msms_pipeline_analysis date'], only in the data
       ['msms_pipeline_analysis date and time']``.
   * - ``2``
     - Item 2, cheaply (new): ``build.sh --gates-pit-root`` over a shadow of
       the sandbox holding a *synthetic* ``cometgui-domain`` report written by
       the harness -- every package mutated and killed; then the smallest
       package's mutations all ``SURVIVED`` with the module still above 80%.
       The real-PIT proof is the ``tests`` control's M, not repeated.
     - ``OK: the gates stage's mutation grading passed``; then ``BELOW`` and
       ``... is under the R-TEST-02 threshold of 80%`` naming the package, the
       module line ``not graded``.
   * - ``4a``, ``4b``, ``4c``
     - Item 4. ``run-performance.sh --part no-spectra --quick --only
       hash-throughput,cancel-fake-tool``: ``StreamingHashService`` digesting
       every chunk twice more; ``ProcessTree.destroyAll`` sending no polite
       stop (both recorded, unit 8); clean; and the clean records graded with
       ``--profile github-ubuntu-latest``.
     - ``RED hash-throughput -- ... is under its threshold`` with
       ``cancel-fake-tool`` ``PASS`` in the same run, and the reverse; both
       ``PASS``; ``NO BASELINE FOR THIS RUNNER PROFILE``, exit 4.
   * - ``H``
     - The harness on itself: an anchor that is missing, a replacement equal
       to its anchor, a green graded as red, a red graded on another defect's
       words, and 1b's injection compared with the baseline with no build
       after it.
     - Each reported as a ``HARNESS FAILURE``, never as a control that bit.
   * - ``C``
     - Every restoration, and what is shared.
     - Every damaged file passes ``sha256sum -c``; the repository's
       ``org/cometgui`` jars, the working tree's ``git status`` and every file
       under its ``cometgui-*/target`` unchanged.

Item 5 is delegated to the ``manifest`` control (a checksum altered, ``RED
(a)``, against a loopback server) and item 6's check of the check to the
``flakiness`` control; neither is repeated.

**The harness caught itself once.** Its first draft made 3c's defect the data's
``repeats`` set to 4; ``determinism.py`` refuses that data before ``check-doc``
reads it (exit 2, "the synthetic part repeats its baseline 3 time(s), fewer
than repeats = 4"), and the harness reported a ``HARNESS FAILURE -- red with
exit 2, not the 1 its verdict means`` rather than a pass. The field rename
replaced it.

What it does not cover: spectra (the fixture halves of the matrix, determinism
and performance are their own suites' and wait for ``D-006``'s CI fixture);
macOS and Windows; the dedicated performance runner; the fourteen other
performance measurements, whose reds unit 8 recorded one by one.

Measured on 2026-11-07: ``bash scripts/verify-all-gates.sh --only hardening`` at
``0c42108``, run while the ``pipeline`` control ran beside it in another
invocation (64 cores, load 2-7): **35 controls in 603 s** (10 m 3 s), every
shared resource unchanged; the first run, ``--worktree`` on the same tree, took
619 s -- 1a 123 s (the sandbox's first build), 1b 61 s, 3a 56 s, 3b 54 s, 4a
116 s, 4b 113 s, 4c 86 s, everything else a few seconds. The floor in
``verify-all-gates.sh`` is 35.

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
macOS (still true on 2026-11-07); running the GUI and packaged tiers there is
Phase 16's (``AC-TST-07``).

What is not tested yet
======================

Stated plainly, because a documentation page that implies more coverage than
exists is worse than no page. The state on 2026-11-07; each row's detail is in
the section it points to, and "owner" is whoever the record names -- where none
is named, the row says so.

.. list-table::
   :header-rows: 1
   :widths: 30 18 52

   * - Not tested
     - Owner
     - Why not, and what waits on it

   * - macOS and Windows, anywhere
     - Phase 16 (``AC-TST-07``); a runner of each platform
     - This project has one Linux x86-64 host. Every macOS and Windows
       combination the product offers is ``UNVERIFIED`` in the version matrix
       (:doc:`../platform_support`), the
       Comet ``-p``/``-q`` fixtures exist only for ``linux-x86-64``, and no
       suite, GUI test or packaged tier has run on either. The only
       executions there are two single hosted-runner jobs: Percolator 3.07.1
       on Windows, and the macOS Gatekeeper check, which left Gatekeeper's
       question open (:doc:`tool_registry`, *What has been observed, and what
       has not*).

   * - The dedicated performance runner
     - none recorded; needs a machine
     - None exists, so PHASE-15 gate item 4's "on the dedicated runner" half
       is unverifiable; the one profile is this shared host. The nightly
       ``performance`` job is red by design on any runner without a profile,
       every GitHub runner among them (exit 4), and
       ``nightly-performance-fixture.sh`` is a stub. What such a runner
       needs: :ref:`dev-testing-performance`.

   * - Anything over spectra in CI: ``D-006``'s CI fixture is **open**
     - the owner (``D-006``)
     - No CI job may use the local fixture. Waiting on it, each a stub that
       exits 70 naming it: ``nightly-large-dataset.sh`` (the matrix's pair
       runs), ``nightly-determinism-fixture.sh``,
       ``nightly-performance-fixture.sh`` (and the dedicated runner),
       ``nightly-gui-tests.sh`` (and a display), ``nightly-windows-raw.sh``
       (and a Windows runner), and ``release-e2e-canonical.sh``,
       ``release-e2e-limelight.sh`` and ``release-e2e-noxml.sh`` (and Phase
       16's release artefact). Locally, with the ``D-006`` local fixture,
       everything here but the Windows RAW test has run on Linux.

   * - Thermo RAW
     - none recorded; needs a Windows runner and a RAW fixture (``D-006``)
     - Comet reads RAW only on Windows. The matrix records the Windows smoke
       test ``UNVERIFIED`` (``AC-TST-11``, "when that feature ships") and
       ``nightly-windows-raw.sh`` is a stub.

   * - Mutation testing of ``org.cometgui.workflow.{engine,steps,storage}``
     - tier 1 (escalated by Phase 15)
     - The command builders and stage invalidation ``R-TEST-02`` names are not
       graded: PIT over them ran more than 2 h 45 min unfinished, against a
       15-minute allowance (`Mutation -- PIT`_). ``workflow.state`` is graded.

   * - Coverage rules beyond the five modules that have one
     - not decided by any phase
     - The 90/85 rule is on in ``cometgui-domain``, ``-provenance``,
       ``-params-comet`` and ``-params-percolator``, the view-model rule in
       ``cometgui-ui``. The specification gives adapters no number; whether
       ``cometgui-results`` (q-value filtering) and ``cometgui-workflow``
       count as core domain logic was left by ``cometgui-workflow/pom.xml``
       to "the phase that adds the engine" and is recorded nowhere since.

   * - Release acceptance on real installer artefacts
     - Phase 16
     - Packaging, checksums, the smoke test, signing and publishing are stubs;
       Phase 14 packages and tests a Linux archive locally only
       (:ref:`dev-e2e-ci`).

   * - The pull-request pipeline as it stands
     - whoever opens the next pull request
     - It ran once, on 2026-09-02 (`CI definitions`_); every step since has run
       only here.

Product gaps the tests found -- each reported upward, none fixed yet, and no
test changed to hide one:

* **The merged PIN's row count is not checked by the product.** Its merge
  record counts rows read, not written, so a dropped row still gives a
  successful run; only the version matrix's own count saw it (Phase 15 unit 6,
  escalated; :ref:`dev-testing-version-matrix`).
* **PSM identifiers carry the absolute run path** Comet builds from ``-N``;
  the goldens strip it and determinism normalises it (unit 6, a product
  question).
* ``KeyboardOnlyNavigationUiTest`` **fails on a machine with Comet installed in
  the application-data folder** -- deterministic, an isolation defect; open in
  ``STATUS.rst``, tier 1's to route (:ref:`dev-testing-flakiness`).
* **The Results run chooser opens every run it passes** on keyboard navigation,
  each a cold open of a large run (unit 8, reported;
  :ref:`dev-testing-performance`).
* **The retained GUI heap creeps** by about 0.6 MiB per run opened in one
  session and does not return when a run closes (unit 8, not traced).
