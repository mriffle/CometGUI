===========================================
PHASE-08: Workflow Engine and Comet Adapter
===========================================

:Phase: 08
:Status: NOT STARTED
:Depends on: 03, 04, 05, 06
:Blocked by decisions: none
:Delivers: R-CMT-01..08, R-DEC-01..04, R-RUN-01..06
:Proves: AC-WF-01..05, AC-PRV-03, AC-PRV-04

Purpose
-------
Make a real search happen, correctly, with its outputs contained and its
provenance complete. This phase decides the shape of every run the product will
ever record, including the multi-file model that the specification adds in
revision 2.

In scope
--------

* Workflow engine: declared DAG, step states, derived run state, observable
  transitions, provenance events.
* Stage dependency and invalidation computed from a declared graph plus per-
  stage input fingerprints, with the rerun preview.
* Comet adapter: one invocation per spectrum file with ``-N`` into the run
  directory, ``-P`` for the canonical parameter file, ``-D`` only when
  overriding, recorded per-file argument arrays.
* Bounded concurrency across per-file invocations.
* Output containment, proven with a read-only input directory.
* pepXML and PIN validation per file; PIN merge with header and feature-
  column checks.
* Index modes (``-i``, ``-j``) as a cached, keyed, provenance-recorded step,
  reading an existing ``.idx`` file's self-description.
* The target/decoy model, FASTA decoy detection, and the blocking rules.
* Project and run storage, schema versions, run immutability, project
  locking.
* Cancellation, retry from a failed stage, and prerequisite checksum
  revalidation.

Out of scope
------------

* Percolator (phase 09).
* Results parsing beyond what validation needs (phase 10).

Deliverables
------------

* ``org.cometgui.workflow`` and ``org.cometgui.tools.comet``.
* ``org.cometgui.domain.project`` and ``run`` storage with versioned
  schemas.
* ``docs/developer/workflow_engine.rst``,
  ``docs/reference/project_format.rst``, ``docs/decoys.rst``.

Exit gate
---------

Every item is verified by the orchestrator, independently of the phase
agent's report. An item that cannot be verified has not passed.

1. A real Comet run over **two** spectrum fixtures produces two pepXML and
   two PIN files inside the run directory, with distinct ``-N`` base names,
   while the input directory is mounted read-only.
2. Nothing is written outside the run directory during a run; a test
   snapshots the input tree before and after.
3. The merged PIN has exactly one header and the summed data-row count of
   its inputs; a feature-column mismatch fails the stage with a message
   naming the files.
4. A FASTA with no decoys and ``decoy_search = 0`` blocks the run before
   Comet starts, with a message naming the decoy configuration.
5. A FASTA already containing decoys combined with ``decoy_search != 0`` is
   blocked.
6. The rerun preview names exactly the stages that re-execute for each
   scenario in the specification's stage-rerun list.
7. Cancelling a running Comet stage terminates it and its descendants and
   leaves parsable logs and provenance.
8. A run whose input file changed since the last run refuses to reuse the
   prerequisite and says which file changed.
9. Provenance contains a distinct argument array per spectrum file, and the
   archived ``comet.params`` hash matches the file that was executed.

Risks and notes
---------------

* ``-N`` accepts only one input file. An implementation that passes several
  files at once will appear to work and will write into the user's data
  directory.

Handoff
-------

Before finishing -- whether the phase passed, stalled or was abandoned --
write ``handoffs/PHASE-08-handoff.rst`` covering: what was built and where;
which gate items pass and the evidence for each; what is incomplete and why;
decisions encountered; surprises a later phase must know about; and the
first thing the next agent should do.
