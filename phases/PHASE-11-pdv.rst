=========================
PHASE-11: PDV Integration
=========================

:Phase: 11
:Status: NOT STARTED
:Depends on: 05, 10
:Blocked by decisions: D-005
:Delivers: R-PDV-01
:Proves: AC-VIS-01, AC-VIS-02, AC-VIS-03

Purpose
-------
Annotated spectrum viewing, at the integration level the owner chose in
``D-005``: baseline open-in-PDV, or baseline plus an upstream-contributed
database-search control mode.

In scope
--------

* Managed PDV 2.7.0 install, deferred to first use, cancellable, with its
  ~99 MB download not blocking a search.
* Open run in PDV using the preserved Comet pepXML and the source spectra.
* Open the selected PSM's spectrum context where a documented mapping
  exists.
* A clear error when the spectrum format cannot be visualised by the
  selected PDV version.
* PDV CLI figure generation used as the automated test path.
* PDV launch and version recorded in provenance.
* If ``D-005`` selects enhanced: the launcher and loopback selection API,
  plus a health probe and a select-a-known-PSM test; a fork, if used, is
  version- and checksum-tracked.

Out of scope
------------

* Screen-coordinate automation of PDV, in production or in tests.

Deliverables
------------

* ``org.cometgui.tools.pdv``.
* Visualisation UI section wired to run context.
* ``docs/pdv.rst``.

Exit gate
---------

The phase orchestrator verifies every item, and the main orchestrator then
re-runs them to sign the phase off. Neither accepts a report in place of
running the check. An item that cannot be verified has not passed.

1. PDV installs on demand from an empty cache, and the search workflow is
   usable while it downloads.
2. A PDV CLI invocation on a real Comet pepXML plus its spectrum file
   produces a non-empty, valid annotated figure; the test asserts on the
   file's content, not merely its existence.
3. An unsupported spectrum format produces the specified error rather than
   a launched-and-broken PDV.
4. PDV's JAR checksum and reported version appear in the run's provenance
   whenever it is launched.
5. If enhanced mode was chosen: the health endpoint responds and selecting
   a known PSM changes PDV's displayed spectrum, asserted through the API
   rather than the screen.

Risks and notes
---------------

* Enhanced mode depends on upstream work that may not exist. Ship baseline
  first so the phase can pass without it.

Handoff
-------

The **phase orchestrator** owns both records for this phase.

``handoffs/PHASE-11-worklog.rst`` is written as the phase runs: the work units,
their acceptance conditions, which agent did each, and the sign-off entry for
each -- what was run and what was observed.

``handoffs/PHASE-11-handoff.rst`` is written before finishing, whether the phase
passed, stalled or was abandoned: what was built and where; which gate items
pass and the evidence for each; what is incomplete and why; decisions
encountered; surprises a later phase must know about; and the first thing the
next agent should do.
