.. _dev-workflow-engine:

===============
Workflow engine
===============

This page describes the workflow engine and the Comet steps **as built** in
Phase 08: the declared step graph, how a run decides what to execute, how it
records what it did, how it is cancelled and retried, and how the Comet search
is invoked, checked and merged. ``specification.rst`` (*Workflow Engine*,
*Comet Invocation and the Multi-File Run Model*, *Target/decoy strategy*) is
the authority on what is required; where the build differs, this page says so.

.. note::

   **State after Phase 08 -- Workflow Engine and Comet Adapter**, written on
   2026-10-07 against the tree as committed. A Phase 08 run ends with the
   merged PIN file and its provenance: Percolator and every later step are
   declared in the graph but not implemented (Phases 09 to 12). Every measured
   number below was produced by a real run of the pinned Comet binaries on
   Linux/x86-64, recorded in the work-unit sign-offs of
   ``handoffs/PHASE-08-worklog.rst`` or asserted by the test named beside it.
   Nothing here has run on Windows or macOS; see `What has never run`_.

   **Amended after Phase 09** (2026-10-08): a run may now carry a Percolator
   half, which plans and implements ``resolve-percolator``,
   ``run-percolator`` and ``parse-percolator`` (`The Percolator steps`_), and
   a derived run reruns Percolator alone
   (`Derived runs: the compatible-version Percolator rerun`_). How a build's
   capabilities are established and which build is selected is
   :doc:`version_capabilities`. ``finalise-results`` and every later step
   remain unimplemented (Phases 10 to 12).

The on-disk formats this page refers to have their own references:
:doc:`../reference/project_format` (the project, ``run.json``, the lock and the
index cache) and :doc:`../reference/provenance_format` (``provenance.json`` and
the event log). The decoy checks are explained for users on :doc:`../decoys`
and for developers in :ref:`dev-comet-parameter-prerun-facts`.

.. contents:: Contents
   :depth: 2
   :local:

Where the code is
=================

.. list-table::
   :header-rows: 1
   :widths: 34 66

   * - Package (module)
     - What it holds

   * - ``org.cometgui.workflow.state`` (``cometgui-workflow``)
     - Pure data and derivation, no file, process or thread: the seventeen
       ``EngineStep`` constants, the declared ``StepGraph``, ``Plan``, the
       per-step ``Fingerprints``, the ``RerunPreview``, ``RunState`` and the
       ``StageProjection`` onto the stepper. A PIT target in the POM.

   * - ``org.cometgui.workflow.engine`` (``cometgui-workflow``)
     - The executor: ``WorkflowEngine``, one ``RunExecution`` per attempt,
       ``StepContext`` (the only way a step reaches the process service),
       ``ConcurrencyBound``, ``ReuseValidator``/``ReuseCheck`` (prerequisite
       revalidation) and ``ProvenanceLedger``. Tool-agnostic: a step is a
       ``StepAction``.

   * - ``org.cometgui.workflow.steps`` (``cometgui-workflow``)
     - The Comet run: ``CometWorkflow`` (check, prepare, start, preview),
       ``PreRunChecks``, the preparation and search step actions,
       ``RunDeclarations``, ``RunInputs``, ``IndexCacheKey`` and
       ``IndexCacheEntry``; the Percolator steps; and the derived run
       (``PercolatorRerun``, ``DerivedRun``; see
       `Derived runs: the compatible-version Percolator rerun`_).

   * - ``org.cometgui.workflow.storage`` (``cometgui-workflow``)
     - ``project.json``, ``run.json`` and ``project.lock``; see
       :doc:`../reference/project_format`.

   * - ``org.cometgui.tools.comet`` (``cometgui-tools``)
     - The Comet adapter, pure of the engine: ``CometSearchCommands``,
       ``CometIndexCommand``, ``CometPepXmlValidator``, ``CometPinValidator``,
       ``PinReader``/``PinHeader``, ``PinMerger``, and the pre-run readers
       ``FastaDecoyScanner`` and ``CometIndexHeaderReader``.

   * - ``org.cometgui.domain.run``, ``.project``, ``.params``
       (``cometgui-domain``)
     - The pure models: the run layout and ``-N`` base names, the run and
       project descriptors and schema-version policy, and the pre-run facts
       (``FastaDecoyCensus``, ``CometIndexDescription``, ``PreRunFacts``).

   * - ``org.cometgui.params.comet.validation`` (``cometgui-params-comet``)
     - The decoy blocks and the index-compatibility rules, as rules of the one
       validator (``CometValidator.validate(model, PreRunFacts)``).

The engine, the steps and storage are **not** in the POM's PIT
``targetClasses``; every Phase 08 unit sign-off ran PIT on them by
``-DtargetClasses`` instead, and adding them was escalated rather than changed
(work log, P8-2).

The declared graph
==================

``R-RUN-01`` requires stage invalidation to be "computed from a declared
dependency graph and an input fingerprint per stage, not from ad hoc
conditionals". ``StepGraph.canonical()`` is that declaration. It holds all
seventeen steps of the specification's *Canonical workflow DAG* -- Percolator
and the optional steps included, so that invalidation is computed over the
real graph even though Phase 08 implements only part of it (decision P8-9).

The steps
---------

.. list-table::
   :header-rows: 1
   :widths: 5 27 15 13 8 32

   * - #
     - Identifier
     - Stepper stage
     - Kind
     - Phase
     - Inputs it declares

   * - 1
     - ``validate-configuration``
     - Validate
     - PREPARATION
     - 08
     - spectrum-files, fasta, comet-parameters, comet-index-mode
   * - 2
     - ``resolve-comet``
     - Comet
     - PREPARATION
     - 08
     - comet-tool
   * - 3
     - ``resolve-percolator``
     - Percolator
     - PREPARATION
     - 09
     - percolator-tool
   * - 4
     - ``serialise-comet-params``
     - Comet
     - RESULT
     - 08
     - comet-parameters
   * - 5
     - ``hash-inputs``
     - Inputs
     - PREPARATION
     - 08
     - spectrum-files, fasta
   * - 6
     - ``build-comet-index``
     - Comet
     - RESULT
     - 08
     - fasta, comet-parameters, comet-tool, comet-index-mode
   * - 7
     - ``run-comet``
     - Comet
     - RESULT
     - 08
     - spectrum-files, fasta, comet-parameters, comet-index-mode, comet-tool
   * - 8
     - ``validate-comet-outputs``
     - Comet
     - RESULT
     - 08
     - (none)
   * - 9
     - ``merge-pin``
     - Comet
     - RESULT
     - 08
     - (none)
   * - 10
     - ``run-percolator``
     - Percolator
     - RESULT
     - 09
     - percolator-settings, percolator-tool
   * - 11
     - ``parse-percolator``
     - Percolator
     - RESULT
     - 09
     - (none)
   * - 12
     - ``finalise-results``
     - Results
     - RESULT
     - 10
     - (none)
   * - 13
     - ``finalise-provenance``
     - Results
     - RESULT
     - 08
     - (none)
   * - 14
     - ``launch-pdv`` (optional)
     - PDV
     - RESULT
     - 11
     - pdv-tool
   * - 15
     - ``convert-limelight`` (optional)
     - Limelight XML
     - RESULT
     - 12
     - limelight-q-cutoff, limelight-converter-options,
       limelight-converter-tool
   * - 16
     - ``upload-limelight`` (optional)
     - Limelight upload
     - RESULT
     - 12
     - limelight-upload-target
   * - 17
     - ``append-downstream-provenance``
     - Results
     - RESULT
     - 11
     - (none)

The identifiers are written into provenance and logs; ``InputKind`` adds two
kinds, ``psm-display-filter`` and ``peptide-display-filter``, that **no step
declares**. A caller can hand the engine the whole configuration, and a
display-filter change still cannot reach any fingerprint; a test pins that no
step declares either.

**The engine's steps are not the stepper's stages.** The scientist watches
eight stages (``WorkflowStage``, Phase 02); each engine step is drawn under
exactly one of them, as the table shows, and neither model is derived from the
other. ``StageProjection`` combines the states of a stage's planned steps into
one; a stage with **no** planned step gets no state at all, so in a run
without a Percolator half the Percolator stage is drawn as not planned --
never as succeeded or skipped.

The edges
---------

Twenty-six edges, each declared once with its reason in ``StepGraph``'s
Javadoc. Twenty are *required*: planning the downstream step plans the
upstream one. Six are *if planned*: they order and invalidate when both ends
are in a plan, but do not pull the upstream step in.

* ``validate-configuration`` before ``resolve-comet``, ``resolve-percolator``,
  ``serialise-comet-params`` and ``hash-inputs`` -- nothing is probed,
  archived or hashed for a configuration the validator blocks.
* ``serialise-comet-params``, ``resolve-comet`` and ``hash-inputs`` before
  ``build-comet-index`` and before ``run-comet``.
* ``build-comet-index`` before ``run-comet`` (if planned).
* ``run-comet`` -> ``validate-comet-outputs`` -> ``merge-pin``.
* ``merge-pin`` and ``resolve-percolator`` before ``run-percolator``;
  ``run-percolator`` -> ``parse-percolator`` -> ``finalise-results``.
* ``merge-pin`` before ``finalise-provenance``; ``finalise-results`` before
  ``finalise-provenance`` (if planned).
* ``finalise-results`` -> ``launch-pdv``; ``parse-percolator`` ->
  ``convert-limelight`` -> ``upload-limelight``.
* ``finalise-provenance`` before ``append-downstream-provenance``, and
  ``launch-pdv``, ``convert-limelight`` and ``upload-limelight`` before it (all
  three if planned).

There is deliberately no edge between ``hash-inputs`` and either
``resolve-*`` step, so hashing runs while a tool is resolved, as the
specification allows. ``StepGraph.declare`` refuses a self edge, an edge naming
an undeclared step, a repeated edge and a cycle, each naming the steps; the
canonical graph goes through the same checks.

Two kinds of step, and why
--------------------------

Without a distinction the specification's *Stage reruns* paragraph cannot be
met. Step 1 reads the Comet parameters *and* everything else; if its
fingerprint fed Comet's, a Percolator change would change Comet's fingerprint,
which the specification forbids. And resolving Percolator must happen before
Percolator re-runs even when its identity did not change, because the run needs
the binary's location now. So (``StepKind``):

* a **RESULT** step's outputs are reusable while its fingerprint matches the
  recorded one; an edge out of it is a *data* edge, and when it re-executes,
  every RESULT step downstream re-executes too;
* a **PREPARATION** step is never reused. It executes exactly when a step that
  depends on it executes, and its executing never by itself makes anything
  downstream re-execute; an edge out of it is an *ordering* edge. Whatever it
  establishes that a later result depends on (a tool identity, a file's hash)
  is declared as that later step's own input.

Plans: what a run executes
--------------------------

A run executes a ``Plan``: the steps wanted, closed under required edges.
``CometWorkflow.planFor`` wants ``finalise-provenance``, plus
``build-comet-index`` when an index mode is selected, which gives::

    validate-configuration, resolve-comet, serialise-comet-params, hash-inputs,
    [build-comet-index,] run-comet, validate-comet-outputs, merge-pin,
    finalise-provenance

``resolve-percolator`` is not in it: nothing wanted requires it. The run state
is derived over the planned steps only (``RunState.deriveFrom(Plan, ...)``),
and a step outside the plan is reported as not planned. A planned step with no
action is refused by name when the run starts.

Since Phase 09, ``CometWorkflow.planFor(mode, percolator)`` with
``percolator`` set also wants ``parse-percolator``, which pulls in
``run-percolator`` and through it ``merge-pin`` and ``resolve-percolator``.
``finalise-results`` (Phase 10) is still not wanted, and its edge into
``finalise-provenance`` is an *if planned* one, so it is not pulled in. A
request's Percolator half is ``SearchRequest.withPercolator``; a request
without one plans exactly the Phase 08 steps above.

Fingerprints and the rerun preview
==================================

The fingerprint
---------------

Each step's fingerprint (``Fingerprints``, decision P8-10) is the SHA-256 of
this ASCII text, every line ending in ``\n``::

    cometgui-step-fingerprint 1
    step <step id>
    input <input kind id> <input digest>          one per declared input, in order
    upstream <step id> <upstream fingerprint>     one per planned RESULT upstream step

Every element is a fixed identifier or a lower-case hex digest, so the text
holds no user data and no locale-dependent formatting, and can be recomputed by
hand with ``printf ... | sha256sum``. What goes in: the step's identity,
exactly the inputs it declares (for Comet, the canonical parameter file's
bytes, the spectra and FASTA hashes, the index mode and the tool's version and
binary SHA-256), and the fingerprints of its planned RESULT upstream steps.
PREPARATION upstream steps are absent by design. A step one of whose declared
inputs has no value is refused rather than fingerprinted, naming the missing
inputs. The first line versions the encoding: changing it changes every
fingerprint, so a recorded run reruns rather than being matched against a
fingerprint computed another way.

Only steps that **succeeded** have their fingerprint recorded, in
``run.json``'s ``succeededSteps`` (:doc:`../reference/project_format`); a
preview compares against the attempts' records merged, the latest winning.

The preview
-----------

``RerunPreview`` is computed from the plan, the current inputs and the recorded
fingerprints only, plus an optional set of steps the caller forces. For each
planned RESULT step, in plan order, the reasons it re-executes are: *forced*;
*not recorded* (nothing to reuse) or else one *input changed* per declared
input whose digest differs; one *upstream re-executes* per planned RESULT step
it reads from that re-executes; and, if none of those applies, *fingerprint
changed*. No reason means REUSE. Each PREPARATION step then gets one
*prerequisite of* per directly downstream step that executes: PREPARE if it has
any, NOT NEEDED if not. If nothing a result depends on changed, nothing
executes at all -- not even validation.

The specification's five scenarios
----------------------------------

``org.cometgui.workflow.state.RerunPreviewTest`` (nested class
``StageReruns``) holds each scenario's expected sets hand-typed, over the full
graph with every optional step planned and a recorded run of the same plan:

.. list-table::
   :header-rows: 1
   :widths: 22 42 18 18

   * - Change
     - Re-executes
     - Runs as a prerequisite
     - Reused

   * - (a) only the PSM and peptide display filters
     - nothing
     - nothing
     - all twelve RESULT steps

   * - (b) Percolator parameters
     - ``run-percolator``, ``parse-percolator``, ``finalise-results``,
       ``finalise-provenance``, ``launch-pdv``, ``convert-limelight``,
       ``upload-limelight``, ``append-downstream-provenance``
     - ``validate-configuration``, ``resolve-percolator``
     - ``serialise-comet-params``, ``run-comet``,
       ``validate-comet-outputs``, ``merge-pin``

   * - (c) an XML-capable Percolator chosen for Limelight (the earlier plan
       had no Limelight steps)
     - ``run-percolator``, ``parse-percolator``, ``finalise-results``,
       ``finalise-provenance``, ``convert-limelight``, ``upload-limelight``,
       ``append-downstream-provenance``
     - ``validate-configuration``, ``resolve-percolator``
     - ``serialise-comet-params``, ``run-comet``,
       ``validate-comet-outputs``, ``merge-pin`` -- the preserved merged PIN

   * - (d) Comet parameters
     - all twelve RESULT steps (and ``build-comet-index`` when an index mode
       is planned)
     - ``validate-configuration``, ``resolve-comet``, ``resolve-percolator``,
       ``hash-inputs``
     - nothing

   * - (e) only the Limelight q cutoff
     - ``convert-limelight``, ``upload-limelight``,
       ``append-downstream-provenance``
     - nothing
     - the other nine RESULT steps

Where the sets go beyond the specification's words, the test's Javadoc says why:
results, PDV and core provenance re-execute after a Percolator change because
they are downstream of Percolator in the declared graph; step 17 re-executes
after a q-cutoff change because the new conversion's provenance has to be
appended; and preparation steps are listed separately, never among the
re-executed results. An equal cutoff written differently (``0.010`` for
``0.01``) re-executes nothing.

Against a real run, ``RealCometRunTest.gate6RerunPreviewAfterARealRun`` checks
the Phase 08 plan: unchanged configuration -- nothing executes and the five
RESULT steps (``serialise-comet-params``, ``run-comet``,
``validate-comet-outputs``, ``merge-pin``, ``finalise-provenance``) are reused;
changed ``fragment_bin_tol`` -- those five re-execute with
``validate-configuration``, ``resolve-comet`` and ``hash-inputs`` as
prerequisites; a changed spectrum file -- Comet and downstream re-execute and
the parameter file is reused.

Running an attempt
==================

Step states and transitions
---------------------------

Every state change happens in one place (``RunExecution.transition``) under one
lock, so the order of listener callbacks and event-log records is the order the
changes happened. The engine allows exactly these moves
(``StateWireNames``)::

    NOT_STARTED      -> VALIDATING | READY | SKIPPED | CANCELLED
    VALIDATING       -> READY | FAILED | CANCEL_REQUESTED
    READY            -> RUNNING | CANCEL_REQUESTED
    RUNNING          -> SUCCEEDED | FAILED | CANCEL_REQUESTED
    CANCEL_REQUESTED -> CANCELLED | SUCCEEDED

``VALIDATING`` is used only by a step that validates -- in Phase 08,
``validate-configuration``. ``SKIPPED`` is a step the preview reuses or does
not need. ``CANCEL_REQUESTED -> SUCCEEDED`` is a step that finished before the
cancellation reached it; ``NOT_STARTED -> CANCELLED`` happens only in that race,
to the steps that would have started next, so a cancelled run always derives
``CANCELLED``. The wire names (``not-started``, ``validating``, ``ready``,
``running``, ``succeeded``, ``failed``, ``cancel-requested``, ``cancelled``,
``skipped``) are literals, never ``name().toLowerCase()``.

The run's state is never stored. It is derived from the step states by
``RunState``'s one precedence -- cancel requested, running, not started,
failed, cancelled, still running, succeeded -- in which a failed *optional*
step does not fail the run and a skipped step counts as satisfied.

An attempt, from start to finish
--------------------------------

``WorkflowEngine.start`` runs ``checkReuse`` first, so it cannot be skipped:

#. **Reuse check.** The rerun preview is computed against the fingerprints
   ``run.json`` records, and every file of every step it would reuse is
   re-hashed (`Retry, new runs and prerequisite revalidation`_). A changed
   file refuses the start with ``ReuseRefusedException``.
#. **The attempt is recorded.** A new attempt is added to ``run.json`` and
   ``run.started`` is appended to ``provenance/events.log`` with the run id,
   the attempt number and the plan. Steps the preview reuses or does not need
   move to ``SKIPPED``.
#. **Steps run as their upstream steps finish.** A step is dispatched when
   every planned upstream step has succeeded or been skipped, alongside every
   other ready step. Each step that succeeds has its fingerprint recorded in
   ``run.json``. A step that fails stops new steps from starting; steps
   already running finish.
#. **Finalisation**, whether the attempt succeeded, failed or was cancelled:
   ``provenance.json`` is written atomically by the redacting manifest writer,
   ``provenance.rst`` is rendered from the same model, ``run.finished`` carries
   the terminal status, and the attempt's outcome is recorded in ``run.json``.

What is written per step and per invocation
-------------------------------------------

To the event log (:ref:`ref-provenance-format-event-types`):

* ``stage.started`` when a step enters ``VALIDATING``, ``READY``, ``RUNNING``
  or ``CANCEL_REQUESTED``, and ``stage.finished`` when it reaches a terminal
  state, each with the step id, the state's wire name and the attempt. A
  step's details ride on its ``stage.finished`` -- for ``merge-pin`` the merge
  record, for ``validate-comet-outputs`` each file's spectrum-query, target and
  decoy counts, for ``build-comet-index`` whether the index was built or
  reused and its SHA-256;
* ``tool.invoked`` once per tool invocation, after it has exited, with its
  stage identifier (``comet-01``, ``comet-02`` ..., ``comet-index``), tool,
  version, status and exit code;
* ``file.hashed`` once per declared file as its step ends, with both digests,
  direction, role and status.

To ``provenance.json`` (:doc:`../reference/provenance_format`), assembled by
``ProvenanceLedger`` in plan order and, within a step, in the order of its
invocation list and declarations -- never in completion order:

* **one tool record per invocation** (decision P8-12), whose ``stageId`` is the
  invocation's and whose execution record carries **its own argument array**,
  start, end, exit code, status and log. Both log members name the one
  stream-tagged log the process service wrote for that invocation (see
  `The run directory`_);
* **one file record per declared file**, with role (``spectrum``, ``fasta``,
  ``comet-index``, ``comet-params``, ``pepxml``, ``decoy-pepxml``, ``pin``,
  ``merged-pin``) and both digests. An output of a step that did not finish is
  recorded ``partial``. A file one step writes and a later step reads is listed
  once as an output and once as an input;
* **settings** under pinned keys: ``comet.release``, ``comet.index-mode``,
  ``comet.database-delivery``, ``comet.params-sha256``,
  ``comet.index-cache-key`` (with an index mode), and the engine's own
  ``workflow.attempt`` and ``workflow.plan``.

Bounded concurrency
-------------------

``StepContext.invokeAll`` runs a step's invocations at most
``ConcurrencyBound.of(invocations, cores, threadsPerInvocation, cap)`` at a
time: ``max(1, min(invocations, cores / threadsPerInvocation, cap))``, where a
``threadsPerInvocation`` of zero or less (Comet's ``num_threads = 0``, "all
cores") means one at a time. ``run-comet`` passes the parameters'
``num_threads``; the application sets the cap to 4
(``RunWiring.INVOCATION_CAP``). The bound changes only how many invocations
overlap -- never a command, an output name or the provenance order
(``R-CMT-05``). The first invocation that does not complete fails the call:
those still running are cancelled, those not started are never started, and
every log already written is kept. ``InvocationConcurrencyTest`` observes the
bound rather than assuming it (five invocations with a bound of two overlap
exactly two, and are recorded in list order).

Cancellation
------------

``RunHandle.cancel`` moves every running step to ``CANCEL_REQUESTED`` and calls
``requestCancellation`` on its context, which reaches each running invocation
through ``RunningStage.requestCancellation`` -- the process service's
descendant-first termination (:doc:`tool_adapters`, *Cancellation and
descendant termination*). The engine has no other way to stop a process
(decision P8-13). Afterwards the stage logs and the event log parse,
``provenance.json`` is finalised with status ``cancelled``, and the outputs
present are recorded ``partial``.

It is proved twice. ``org.cometgui.workflow.engine.CancellationTest`` uses a
fake tool that starts a child and asserts both pids dead, neither by the fake's
own watchdog. ``RealCancellationTest`` cancels the real Comet 2026.03.0
mid-search -- the whole proteome at ``num_threads = 1``, about 31 s for one
K562 file, caught after Comet's ``Load spectra: 728`` line -- and asserts
Comet and every descendant dead by pid, ``run-comet`` ``CANCELLED``, the
stage log's last line ``[cometgui] stage comet-01 ended``, the last event
``run.finished`` with status ``cancelled``, and ``k562_3.pep.xml`` and
``k562_3.pin`` recorded ``partial``.

Retry, new runs and prerequisite revalidation
---------------------------------------------

**A retry is another attempt of the same run**: ``PreparedRun.request()`` again,
fingerprinted from the inputs the run *recorded*. Steps whose fingerprints
still match are reused -- but only after ``ReuseValidator`` has looked up every
file each such step declares in the latest ``provenance.json``, required it
recorded complete and still present, and **re-hashed** it with
``CachingHashService.rehash``, which reads the bytes every time and never
serves a cached digest (decision P8-14, ``R-RUN-02``). Every invocation of a
reused step must be recorded completed. A mismatch refuses reuse with a message
naming the file, its role, the step and both SHA-256 values, and
``ReuseCheck.offered()`` is the plan with the producing step forced, so it and
everything downstream run again.

**A run's inputs are fixed when it is created** (``R-RUN-06``). Taking that
offer after an *input* changed still fails, in ``hash-inputs``, with a message
saying to start a new run; a run never searches bytes other than those its
``run.json`` records. ``RealChangedInputTest`` proves both halves for a changed
spectrum file and a changed FASTA, with nothing launched by either refusal.

Within one application session the interface decides between the two
(``WorkflowRunPort``): when the spectra, database, parameter file, index mode
and Comet digest exactly as the last run recorded them, Run retries that run;
otherwise it prepares a new one. **A Phase 08 run cannot be retried across an
application restart**: the prepared run is held in memory.

.. _dev-workflow-engine-derived-runs:

Derived runs: the compatible-version Percolator rerun
-----------------------------------------------------

Phase 09 (design decision P9-11) adds the one kind of new run that is not a
fresh search: ``PercolatorRerun`` reruns Percolator -- another build, other
settings or other downstream stages -- from the merged PIN an earlier run
preserved, without running Comet (the specification's *Stage reruns*). It is a
new run, because a different Percolator is a different configuration
(``R-RUN-06``), and it stays inside the one graph, run store, engine, ledger and
launcher:

* **The plan.** ``PercolatorRerun.planFor(mode)`` is ``Plan.covering(wanted,
  provided)``: it wants ``parse-percolator`` and ``finalise-provenance``, and
  the Comet result steps of the source's search (``serialise-comet-params``,
  [``build-comet-index``,] ``run-comet``, ``validate-comet-outputs``,
  ``merge-pin``) are *provided*: the closure stops at them, so the plan is
  ``validate-configuration``, ``resolve-percolator``, ``run-percolator``,
  ``parse-percolator``, ``finalise-provenance``. A provided step is never
  planned, gets no state and has no action, so no Comet can be launched.
* **The check, before anything is created** (``preview`` and ``prepare``
  alike). The source is read, never written: its ``run.json`` (it must have
  ended; a derived run cannot be rerun again), its ``provenance.json``, the one
  Comet executable its tool records name, and its merged PIN and
  ``comet.params``, each re-hashed with ``CachingHashService.rehash`` and held to
  the SHA-256 the source recorded; a mismatch refuses the rerun naming the file,
  its role and both digests (``R-RUN-02``). The selected Percolator gets the same
  check a search's Percolator half gets. Then the declared graph decides: over
  the search-with-Percolator plan, against the source's recorded fingerprints,
  every provided step must be one the source's own rerun preview would
  **reuse** -- a source whose ``merge-pin`` never succeeded is refused there --
  and ``run-percolator`` must **not** be: a source that already ran this exact
  Percolator has nothing to rerun.
* **The run.** The run directory is reserved; the source's ``comet.params`` and
  merged PIN are copied in (a copy, not a hard link, so the source stays
  byte-identical) and each copy re-hashed against the source's record; anything
  that fails after the reservation removes the directory again.
  ``run.json`` is schema version 2, with ``derivedFrom``
  (:doc:`../reference/project_format`). Percolator's half is prepared by the
  same code as a search's (``CometWorkflow.preparePercolator``), and the three
  Percolator steps are the same ``PercolatorSteps`` -- ``PercolatorRun`` holds
  the run directory, the hasher and the decoy configuration, not a Comet run.
  The decoy configuration is read from the copied ``comet.params`` by the one
  parameter parser.
* **Provenance.** No Comet tool record and no ``comet.*`` setting: Comet is
  recorded by reference, in the ``rerun.*`` settings
  (:ref:`ref-provenance-format-rerun-settings`).
* **The preview** the interface shows before starting is
  ``PercolatorRerunPreview``: the new run's own ``RerunPreview`` (the Percolator
  steps execute; ``validate-configuration`` and ``resolve-percolator`` are
  prepared) and ``reusedFromSource()``, the provided steps, shown as "not
  executed -- its result is reused from run ...".

``finalise-provenance`` in a derived run re-hashes the two copies once more; like
a search's (Phase 09 unit 5's residue), it is ordered only by planned steps it
reads from, so it runs alongside Percolator until Phase 10 plans
``finalise-results``. ``RealPercolatorRerunTest`` proves the scientist's case
on real binaries: Comet 2026.03.0 and Percolator 3.09 with Limelight disabled,
then the rerun with 3.07.1 and Limelight -- Comet launched zero times, a second
execution record with another version, checksum and argument array (``-X`` and
``pout.xml`` only in the second), the merged PIN equal, and the source run's
whole tree (every path, size, SHA-256, time and mode) identical afterwards.

The Comet run
=============

``CometWorkflow`` is the entry point the interface and every real-run test
use. ``check`` is Run readiness and writes nothing. ``prepare`` runs the same
check and, if anything blocks, throws ``RunBlockedException`` -- **no run
directory is created and nothing is launched**; otherwise it reserves the run
directory, writes ``parameters/comet.params`` once with
``CanonicalParamsWriter.writeOnce`` (``R-PARAM-12``,
:ref:`dev-comet-parameter-write-once`), hashes the inputs and records the
run's identity in ``run.json``. ``start`` starts an attempt; ``preview``
computes the rerun preview of a changed configuration against a recorded run.

The pre-run check
-----------------

``PreRunChecks`` runs in ``check``, in ``prepare`` and again in the run's own
``validate-configuration`` step, so no attempt -- a retry hours later included
-- reaches Comet without passing it:

#. The selected Comet is the release the parameters are for, exists, is
   executable and still has the SHA-256 it was selected at.
#. There is at least one spectrum file; each exists, is readable and has a
   spectrum extension Comet reads here (``.raw`` only on Windows); their
   ``-N`` base names can be derived.
#. ``database_name`` is an absolute path to a readable file. A FASTA is scanned
   for decoys with the model's own ``decoy_prefix`` (``R-DEC-02``). An
   existing ``.idx`` has its header read, and the FASTA its ``InputDB:``
   names is scanned; an index whose FASTA cannot be found is refused.
#. With an index mode, a complete cache entry's index header is read, so the
   validator judges the index the search would reuse.
#. Every other parameter whose metadata carries validator ``path`` -- in the
   bundled metadata ``peff_obo``, ``compoundmods_file``,
   ``spectral_library_name`` and ``protein_modslist_file`` -- is empty or an
   absolute path to a readable file (unit 7b). The set is read from the
   metadata (``PathParameterChecksTest.theSetFollowsTheMetadata``).
#. The project's ``runs/`` directory exists and is writable.
#. The one validator judges the model with every fact gathered. **The decoy
   blocks and the index-compatibility check are its rules**, so there is no
   second rule here: ``decoy.none_anywhere`` (``decoy_search = 0`` and no decoy
   record), ``decoy.double_decoys`` (``decoy_search`` 1 or 2 and any decoy
   record), and the index rules of :ref:`dev-comet-parameter-index-formats`
   (2026.03.0 reads only format 5; an index whose recorded options contradict
   the search is refused).

Any problem, or any error in the validator's report, blocks the run; warnings
do not. The exact refusal texts are on :doc:`../decoys`; for example
``RealDecoyBlockTest`` asserts, with no launch and no run directory::

    the run cannot start:
    - [decoy.none_anywhere] decoy_search = 0 (no internal decoys) and <fasta>
      holds no entry whose accession begins with DECOY_ (0 of 1000 records): ...

**The release default placeholder.** Comet 2026.03.0's ``-q`` output sets
``spectral_library_name = /some/path/speclib.file``; measured, that makes
Comet exit 1 (``Error (5) - cannot read spectral library file``). The check
refuses it before Comet starts::

    the run cannot start:
    - spectral_library_name (Spectral library file) = /some/path/speclib.file
      does not exist or cannot be read; clear it to search without one, or
      choose the file

A new configuration no longer starts from that default: by ``D-012`` it
starts with the field empty, CometGUI's recorded starting value
(:ref:`dev-comet-parameter-starting-values`), and empty means no
spectral-library search in both releases; a file without the line, imported
or migrated, is read the same way, as Comet reads it. The check now fires
only when a file or the scientist names a library.

The search, one invocation per file
-----------------------------------

``run-comet`` builds one command per spectrum file (``CometSearchCommands``,
decision P8-4) and runs them through ``invokeAll``::

    comet -P<run>/parameters/comet.params [-D<database>] -N<run>/outputs/comet/<base> <input>

* ``-P`` is always the archived file ``writeOnce`` wrote and hashed; the
  builder takes no parameter-file path at all.
* ``-D`` appears only when the database must override ``database_name`` --
  in Phase 08, only to search a cached index -- and ``run.json``'s
  ``databaseDelivery`` records which mechanism was used (``R-CMT-04``).
* ``-N`` always names ``outputs/comet/<base>`` inside the run (``R-CMT-03``).
  Comet ignores ``-N`` **silently** when given two inputs and writes beside
  each input instead -- re-measured on 2026.03.0 in unit 4 -- so no public
  method of the builder can put two inputs on one command line
  (``CometSearchCommandsTest.twoInputsAreInexpressible``).
* Two commands of one run differ in exactly the ``-N`` base and the input.
  The working directory is the run directory; the environment is exactly
  ``LANG=C.UTF-8``. A run directory holding a control character is refused,
  because Comet writes the ``-N`` path verbatim into every PIN row's ``SpecId``
  (Phase 09 must know: ``SpecId`` carries the absolute path).

Outputs left by an earlier attempt are removed before the search, so a stale
file cannot pass for a fresh one. Each invocation's stage identifier is
``comet-<nn>``, ``nn`` the file's 1-based position.

Output validation and the PIN merge
-----------------------------------

``validate-comet-outputs`` checks, per file: the pepXML is well formed, has the
expected root and names its own input and ``-N`` base (with
``decoy_search = 2``, the separate ``<base>.decoy.pep.xml`` too); the PIN
parses through the one PIN parser and holds both target and decoy rows
(``R-DEC-04``). A PIN with no decoy row fails the step naming the decoy
configuration::

    the PIN file <pin> holds 1807 target rows and no decoy row (Label -1), so
    Percolator would have no negative examples; the decoy configuration was
    decoy_search = 1 (Comet's internal decoys, concatenated), decoy_prefix = "DECOY_"

``merge-pin`` merges the per-file PINs into ``inputs/pin/merged.pin``
(``PinMerger``, ``R-CMT-06``): exactly one header, every data row in input
order byte for byte, the feature columns required equal **in order** -- a
renamed, swapped, missing or extra column fails the stage naming both files --
and the merge recorded (each input and its row count, the total, the output and
its SHA-256) in the step's ``stage.finished`` event.

``finalise-provenance`` re-hashes the archived ``comet.params`` after every
search and requires the digest ``writeOnce`` returned and the run recorded
(``AC-PRV-04``); a parameter file changed during the run fails the run
(``RealStepTest.finaliseRefusesAParameterFileChangedDuringTheRun``).
``serialise-comet-params`` makes the same check before Comet reads the file,
because ``writeOnce``'s plain ``CREATE_NEW`` write is not atomic.

Measured, the two-file run
--------------------------

``RealCometRunTest`` runs Comet 2026.03.0 once through the engine on the two
K562 mzML files (from a directory proven read-only) and the UniProt proteome's
first 1000 records, ``decoy_search = 1``, ``num_threads = 4``:

.. list-table::
   :header-rows: 1
   :widths: 22 26 26 26

   * - File
     - Spectrum queries
     - Target / decoy PIN rows
     - PIN data rows
   * - ``k562_3``
     - 728
     - 1807 / 1747
     - 3554
   * - ``k562_4``
     - 607
     - 1478 / 1440
     - 2918
   * - ``merged.pin``
     - --
     - --
     - 3554 + 2918 = 6472, under one header

The run directory then holds exactly ``k562_3.pep.xml``, ``k562_3.pin``,
``k562_4.pep.xml`` and ``k562_4.pin`` under ``outputs/comet/``,
``comet-01.log`` and ``comet-02.log`` under ``logs/``, and one
``comet.params``; the input directory still lists only its three files.

The index cache
---------------

With an index mode (``-i`` fragment-ion, ``-j`` peptide), building the index is
its own step, cached in the **project** under ``index-cache/<key>/`` (decision
P8-8, ``R-CMT-07``). Measured before design: ``comet -i`` writes
``<database>.idx`` *beside the database*, which fails for a read-only input
directory. So the build runs Comet with ``-D`` naming a **symbolic link** to the
FASTA inside the cache entry, and the index lands there; the search then passes
``-D<entry>/<fasta>.idx``.

* **The key** (``IndexCacheKey``) is a SHA-256 over the FASTA's SHA-256 and file
  name, the mode, the Comet release, every option the ``.idx`` header records
  and, conservatively, the ``fragindex_*`` options and ``equal_I_and_L``, with
  the canonical enzyme table.
* **An entry is complete or absent, never half.** Comet writes the header
  before the body, so a cancelled build leaves a header that reads as valid.
  An entry counts as complete only when its ``index.complete`` marker exists,
  written atomically after the build exited zero and its header was judged.
  A build first removes an index and marker an unfinished build left.
* **Reuse is re-hashed** against the SHA-256 the marker records, and the reused
  index's header is judged by the validator's index rules before the search
  reads it; a changed cached index is refused naming both digests.

The entry's layout is in :doc:`../reference/project_format`. The index cache is
the one designed exception to "everything a run writes is inside its run
directory": it belongs to the project, not to the user's data. **The
interface offers no index mode yet**; every search it starts is ``IndexMode.NONE``,
and the cache is exercised by ``RealIndexTest`` and ``RealIndexCacheTest``.

The run directory
=================

The layout (specification, *Project model*; decision P8-3) is in
:doc:`../reference/project_format`, ``runs/<UTC yyyyMMdd'T'HHmmss'Z'>-<id>/``
holding ``run.json``, ``parameters/comet.params``, ``inputs/pin/merged.pin``,
``outputs/comet/<base>.{pep.xml,pin}``, ``logs/``, and
``provenance/{provenance.json,provenance.rst,events.log}``.

**One divergence from the specification.** The specification shows
``logs/comet.<spectrum-basename>.{stdout,stderr}.log``. The process service
writes **one** timestamped, stream-tagged log per invocation (Phase 03's
design, which keeps the interleaving two files would lose), named after a stage
identifier that must match ``[A-Za-z0-9_-]{1,64}``. So a Comet invocation's log
is ``logs/comet-<nn>.log`` (a retried invocation ``comet-<nn>.1.log`` ...), and
``run.json`` and provenance map each ``nn`` to its spectrum file and base name.
This is escalated as a proposed specification amendment, not built around.

**Containment** (``R-CMT-08``). ``RealCometRunTest`` snapshots the whole
scratch tree before and after the run: the input tree is identical, and the one
path that changed outside the run directory is ``project/runs`` itself, whose
modification time moved when the run directory was added. The read-only input
directory is proved read-only first: a test creates a file in it and requires
``AccessDeniedException``; if the write succeeds (running as root, a file
system ignoring modes) the test fails, naming why (decision P8-5).

The Percolator steps
====================

Phase 09 implements the three Percolator steps in ``PercolatorSteps``
(``org.cometgui.workflow.steps``), over a ``PercolatorRun``: the run
directory, the one hasher, the run's one decoy configuration, the
``PercolatorChoice`` (selection, settings, enabled downstream stages and the
resolution behind them), and the command, **built once when the run is
prepared** from the selection's probed capabilities -- so the files the steps
declare, the files Percolator is asked to write and the options provenance
says were not passed are one decision, made before anything runs. Which build
is selected and what it is passed is :doc:`version_capabilities`.

* **Prepare.** ``parameters/percolator-settings.json`` is written once and
  hashed (:doc:`../reference/project_format`), and every ``percolator.*``
  provenance setting is fixed then (``PercolatorProvenance``;
  :ref:`ref-provenance-format-percolator-settings`), so a run that fails --
  even before Percolator is launched -- still records its effective seed.
* ``resolve-percolator`` (PREPARATION) re-hashes the selected executable and
  refuses one whose SHA-256 differs from the one it was selected at, naming
  both.
* ``run-percolator`` (RESULT; inputs ``percolator-settings`` and
  ``percolator-tool``) re-hashes the settings file and refuses a changed one;
  then ``PercolatorPinCheck`` over the merged PIN with the run's decoy
  configuration -- a PIN without decoy rows fails the step and **no process is
  launched** (``R-DEC-04``); then exactly one ``StepContext.invoke`` with stage
  identifier ``percolator`` (log ``logs/percolator.log``). After exit 0 every
  requested artefact must exist and hold bytes, no unrequested file may be in
  ``outputs/percolator/`` (so an unrequested ``.xml`` fails the run), and each
  artefact is made read-only (``R-PERC-07``).
* ``parse-percolator`` (RESULT) reads -- never writes -- the four tables
  through the one ``ResultTableReader``, the weights through ``WeightsReader``
  (split count from the file, ``R-PERC-09``) and the pout XML through
  ``PoutDocument`` when it was written, and records the counts as details of
  its ``stage.finished`` event.

Known limits, recorded rather than hidden: ``finalise-provenance`` is ordered
only after the planned steps it reads from, so until Phase 10 plans
``finalise-results`` it can run alongside the Percolator steps (harmless today:
``provenance.json`` is written when the attempt ends; pinned by a test); and
``run.json`` does not record the Percolator half, so -- as for Comet -- a run
with Percolator cannot be retried across an application restart.

In the application
==================

The Run section reaches the engine through ``RunEnginePort``, a view-model port
in ``cometgui-ui`` that the composition root implements as ``WorkflowRunPort``
(decision P8-16). The interface layer never sees the process service, the Tool
Manager or the engine's collaborators; ``RunViewModel`` calls the port only from
a background executor and applies every answer on the JavaFX thread, dropping
an answer to an older configuration so Run is never enabled on a stale check.

* **Which Comet**: the first Comet of the parameters' release that the Tool
  Manager reports installed with a path -- a managed install before a
  registered local binary -- hashed when it is selected.
* **Run readiness** has two halves, the parameters' (Phase 07) and the
  engine's: no Comet of the release installed, the pre-run check's problems
  and the validator's errors over the file-system facts (the decoy blocks and
  the index refusal among them), a check still running, a run in progress, or
  nothing to run. Run is enabled only when neither half has a reason.
* **The project**: one per session, ``projects/default`` under the application
  data directory (``ProjectSession``), created and locked the first time a
  check finds a Comet to run -- not when the window opens -- and held until the
  application stops.
* **The rerun preview** is shown before Run as text, for a retry ("nothing the
  steps read has changed, so Run retries run ...") or a new run ("...
  changed, so Run starts a new run ... and every step executes in it"), with
  each step's decision and reasons.
* **Which Percolator** (Phase 09): the Percolator section's request
  (``PercolatorViewModel``), which ``WorkflowRunPort`` turns into
  ``SearchRequest.withPercolator``. Its problems -- no build read yet, none
  can run, the default not installed, a setting the model refused -- are
  reasons in the engine's half of Run readiness. The compatible-version rerun
  is a separate action in that section (``PercolatorRerunViewModel``, behind
  ``PercolatorRerunPort``), over the session's last run.

Which tests prove the exit gate
===============================

.. list-table::
   :header-rows: 1
   :widths: 8 46 46

   * - Gate
     - What
     - Test (``org.cometgui.workflow.steps`` unless named)

   * - 1
     - Two spectrum files, two pepXML and two PIN files in the run, distinct
       ``-N`` bases, input directory read-only
     - ``RealCometRunTest.gate1TwoFilesFromAReadOnlyDirectory``
   * - 2
     - Nothing written outside the run directory
     - ``RealCometRunTest.gate2NothingWrittenOutsideTheRunDirectory``
   * - 3
     - One header, summed rows; a column mismatch names the files
     - ``RealCometRunTest.gate3MergedPinHasOneHeaderAndTheSummedRows``,
       ``RealMergeMismatchTest.gate3FeatureColumnMismatchFailsTheStage``
   * - 4, 5
     - Both decoy blocks before Comet starts
     - ``RealDecoyBlockTest`` (three methods)
   * - 6
     - The rerun preview per scenario
     - ``org.cometgui.workflow.state.RerunPreviewTest``,
       ``RealCometRunTest.gate6RerunPreviewAfterARealRun``
   * - 7
     - Cancellation kills the tree, leaves parsable records
     - ``org.cometgui.workflow.engine.CancellationTest``,
       ``RealCancellationTest.gate7CancelTheRealCometMidSearch``
   * - 8
     - A changed input refuses reuse, naming the file
     - ``org.cometgui.workflow.engine.RetryRevalidationTest``,
       ``RealChangedInputTest``
   * - 9
     - One argument array per file; the archived ``comet.params`` hash is the
       executed file's
     - ``RealCometRunTest.gate9ArgvPerFileAndArchivedParamsHash``

The same behaviour through the interface is driven by ``RealRunUiTest``,
``RealCancelUiTest`` and ``RunReadinessUiTest`` (``org.cometgui.app.gui``).
The acceptance criteria's entries are in ``docs/traceability-map.toml``.

The table above is Phase 08's exit gate. **Phase 09's** gate items for the
Percolator steps are proved by these tests in ``org.cometgui.workflow.steps``
(all real binaries, Linux only); the full table, with the unit and interface
tests beside them, is in :doc:`version_capabilities`:

.. list-table::
   :header-rows: 1
   :widths: 8 46 46

   * - Gate
     - What
     - Test

   * - 1, 2
     - A real run writes and parses PSM, peptide, weights and (3.07.1 with
       Limelight) pout XML; 3.09 gets no XML option in the recorded argv
     - ``RealPercolatorRunTest.gate1And2WithAnXmlCapableBuild``,
       ``gate2ThreeNineWithoutLimelight``,
       ``gate2ThreeNineChosenWithLimelight``
   * - 4
     - The skipped newer version and its missing capability in provenance
     - ``RealPercolatorRunTest.gate4TheSkippedVersionIsRecorded``
   * - 5
     - The real zero-decoy PIN fails ``run-percolator`` with no launch
     - ``RealPercolatorRunTest.gate5TheRealZeroDecoyPin``
   * - 6
     - The compatible-version rerun: another execution record, Comet not
       launched, the source run byte-identical
     - ``RealPercolatorRerunTest.gate6TheSecondExecutionRecord``,
       ``gate9TheOriginalIsUntouched``
   * - 7
     - The effective seed in every run's provenance, failed ones included
     - ``RealPercolatorRunTest.gate7TheSeedOfEveryRun``,
       ``gate5TheRealZeroDecoyPin``
   * - 9
     - Raw outputs read-only and byte-identical through parsing and
       provenance finalisation
     - ``RealPercolatorRunTest.gate9RawOutputsAreUnchangedAndReadOnly``

What has never run
==================

Every real-binary test is ``@EnabledOnOs(LINUX)``: only the linux/x86-64 Comet
binaries have ever been executed in this project. Not run on Windows or macOS,
and so unverified there:

* the Comet search and index commands themselves, and the environment of
  exactly ``LANG=C.UTF-8`` (Windows programs commonly need ``SystemRoot``,
  which a platform twin must establish);
* ``.raw`` input, which the pre-run check accepts only on Windows;
* the index cache's symbolic link to the FASTA, which has only been created
  on Linux;
* cancellation of a real Comet and its descendants (the process service's own
  platform caveats are in :ref:`dev-tool-adapters-platform`);
* the project lock's byte-range lock, designed around Windows' mandatory
  locks (:doc:`../reference/project_format`) but never taken there;
* the default project location on Windows and macOS, which is computed from
  the platform's application-data convention but never created there;
* any Percolator (Phase 09): only the Linux 3.07.1 and 3.06.5 portable
  binaries and 3.09 as a registered local binary (the ``.rpm``'s executable
  with Boost 1.66 libraries) have run; the DOS read-only attribute that
  ``PercolatorSteps.makeReadOnly`` sets where there are no POSIX permissions
  has never been set (:doc:`version_capabilities`).

Known limits, recorded rather than hidden:

* A Phase 08 run cannot be retried across an application restart, and
  neither can a run with Percolator (``run.json`` does not record the
  Percolator half).
* Comet 2026.02.2's fragment-ion (format 4) index built with
  ``decoy_search = 1`` searches to **zero decoy rows**; ``R-DEC-04`` catches
  it after the search (``RealStepTest.anOlderFragmentIndexSearchWithoutDecoysIsRefused``),
  and a pre-run rule was recommended, not built.
