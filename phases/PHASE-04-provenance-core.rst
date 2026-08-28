=====================================
PHASE-04: Hashing and Provenance Core
=====================================

:Phase: 04
:Status: NOT STARTED
:Depends on: 01, 03
:Blocked by decisions: none
:Delivers: R-PROV-01, R-PROV-02, R-PROV-03, R-PROV-04, R-PROV-05
:Contributes to: R-SEC-03
:Proves: AC-PRV-01, AC-PRV-05, AC-PRV-06, AC-PRV-09, AC-PRV-10 (unit level)

Purpose
-------
The provenance model, written before the stages that emit events, so that no
stage can be built without recording itself. Provenance retrofitted at the end
is provenance with holes.

In scope
--------

* Single-pass streaming MD5 + SHA-256 over a file, with correctness against
  known vectors.
* The input-hash cache keyed on path, size, mtime and file identity, with
  revalidation, invalidation and a bypass.
* The appendable provenance event model, its schema version, and atomic
  finalisation.
* File, tool, application and environment record types from the
  specification.
* Secret redaction, applied to commands, environments, logs and exports,
  driven by one rule set.
* Partial, failed and cancelled state marking.
* ``provenance.rst`` report generation from the same model as
  ``provenance.json``.

Out of scope
------------

* The Provenance UI (phase 13).
* Recording anything about real tools (phases 05, 08, 09).

Deliverables
------------

* ``org.cometgui.provenance`` with hashing, events, manifest and report
  subpackages.
* ``docs/reference/provenance_format.rst`` -- the schema, versioned.
* A property/round-trip test suite over the manifest.

Exit gate
---------

Every item is verified by the orchestrator, independently of the phase
agent's report. An item that cannot be verified has not passed.

1. Known MD5 and SHA-256 vectors pass, including the zero-byte file.
2. A 2 GB temporary file hashes in one pass with bounded heap, and both
   digests match independently computed values.
3. The hash cache returns a cached value only when every attribute matches,
   and a mutated file is always rehashed; a test mutates content while
   preserving size and asserts the recorded hash is of the content read.
4. A crash simulated mid-run leaves a parsable event log with usable
   history.
5. Finalisation is atomic: an interrupted finalise never leaves a truncated
   ``provenance.json``.
6. A seeded corpus of secrets (tokens, passwords, bearer headers,
   credential-bearing URLs) appears nowhere in JSON, RST or logs; the test
   greps the generated artefacts.
7. PIT reports no surviving mutation in the hashing and redaction packages
   that would disable verification or leak a secret.

Risks and notes
---------------

* The hash cache is a correctness risk, not a performance nicety. If in
  doubt, rehash; a wrong recorded hash is worse than a slow run.

Handoff
-------

Before finishing -- whether the phase passed, stalled or was abandoned --
write ``handoffs/PHASE-04-handoff.rst`` covering: what was built and where;
which gate items pass and the evidence for each; what is incomplete and why;
decisions encountered; surprises a later phase must know about; and the
first thing the next agent should do.
