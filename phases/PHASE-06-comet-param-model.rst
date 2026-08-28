===============================
PHASE-06: Comet Parameter Model
===============================

:Phase: 06
:Status: NOT STARTED
:Depends on: 01, 05
:Blocked by decisions: none
:Delivers: R-PARAM-01..12, R-DOC-04, R-TEST-01
:Proves: AC-PAR-01, AC-PAR-02, AC-PAR-06, AC-PAR-11 (headless parts)

Purpose
-------
The typed, versioned parameter model: the product's scientific core, and the
part most likely to be built plausibly and wrongly. It is headless on purpose
-- the editor UI is a separate phase -- so that parsing, serialisation and
validation are provable without a display.

In scope
--------

* Schema discovery from ``comet -q`` with the ``-p`` fallback and
  ``PARTIAL_DISCOVERY`` marking.
* Curated metadata: display names, categories, help, enum labels,
  relationships, visibility levels, version ranges.
* Parser handling every structural kind in the specification, plus comment
  structure, the ``# comet_version`` marker, empty values, duplicates and
  malformed lines.
* The ``[COMET_ENZYME_INFO]`` table as a first-class model, with custom
  enzymes and second-enzyme consistency.
* All fifteen variable-modification slots as typed tuples with version-
  driven field layout.
* Canonical deterministic writer using ``Locale.ROOT``.
* Value origin tracking (default, preset, user, imported, workflow-
  enforced).
* Per-field and cross-field validators, including the signed tolerance pair
  rule and the decoy rules the workflow needs.
* Presets as versioned deltas, with diff computation.
* Schema migration between Comet versions.
* The schema-drift CI test.
* Generation of ``reference/comet_parameters_generated.rst``.

Out of scope
------------

* Any JavaFX control (phase 07).
* Running Comet (phase 08).

Deliverables
------------

* ``org.cometgui.params.comet`` with schema, parser, writer, validation,
  presets and migration.
* Checked-in fixtures: the real ``comet -p`` and ``comet -q`` output for
  every Comet version in the matrix.
* Generated parameter reference page, built by documentation CI.
* ``docs/developer/comet_parameter_schema.rst``.

Exit gate
---------

Every item is verified by the orchestrator, independently of the phase
agent's report. An item that cannot be verified has not passed.

1. Parsing then writing the real ``comet -q`` output for 2026.02.2 produces
   byte-stable canonical output, and a second round trip is identical.
2. All 118 parameters of 2026.02.2 are either modelled with metadata or
   explicitly allow-listed as internal, and the drift test fails when an
   entry is removed from the metadata.
3. Every variable-modification tuple form round-trips, across all fifteen
   slots, including terminal, required/exclusive and both neutral-loss
   forms.
4. The enzyme table round-trips, a custom enzyme survives, and the writer
   refuses to emit an enzyme number absent from the table.
5. The writer produces byte-identical output under a comma-decimal locale.
6. An unknown imported parameter survives a parse/write cycle and is
   reported, not dropped.
7. The signed precursor tolerance pair is validated by its own rule and not
   by the generic ordering rule.
8. The generated RST reference builds strictly and covers every modelled
   parameter.
9. PIT reports >= 80% mutation score across parser, writer and validators,
   with no surviving mutation that suppresses a validation error or drops a
   parameter.

Risks and notes
---------------

* Building the schema from ``comet -p`` instead of ``-q`` silently loses ten
  variable-modification slots and eleven other parameters. This is the
  specific failure this phase exists to prevent.

Handoff
-------

Before finishing -- whether the phase passed, stalled or was abandoned --
write ``handoffs/PHASE-06-handoff.rst`` covering: what was built and where;
which gate items pass and the evidence for each; what is incomplete and why;
decisions encountered; surprises a later phase must know about; and the
first thing the next agent should do.
