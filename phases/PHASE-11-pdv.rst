=========================
PHASE-11: PDV Integration
=========================

:Phase: 11
:Status: NOT STARTED
:Depends on: 05, 10
:Blocked by decisions: none -- D-005 DECIDED 2026-08-30 (enhanced, via mzTab)
:Delivers: R-PDV-01, R-PDV-02, R-PDV-03, R-PDV-04, R-PDV-05
:Proves: AC-VIS-01, AC-VIS-02, AC-VIS-03, AC-VIS-04, AC-VIS-05

Purpose
-------
Annotated spectrum viewing, driven properly. ``D-005`` was decided on
2026-08-30: **CometGUI controls PDV the way CasanovoGUI does** -- a PDV window
held open on a loopback port, told which spectrum to show when the user selects
a PSM.

The route is PDV's existing ``denovo-gui`` control mode, used **unmodified**.
That mode accepts only mzTab, and Comet plus Percolator does not produce mzTab,
so **this phase builds an mzTab exporter**. The owner was explicit that this is
a real component with its own tests, and that *"it is essential this is
accurate and true to the original results"* -- which is ``R-PDV-03``, a gate
rather than an aspiration.

Two things this route deliberately avoids, both of which earlier revisions of
the specification contemplated: there is **no PDV fork** to checksum and
maintain, and **no upstream contribution on the critical path**, so release 1
does not wait on a third party.

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
* **An mzTab exporter** from the run's Comet pepXML and Percolator results,
  sufficient for ``denovo-gui`` to display any PSM in the set (``R-PDV-02``).
  The generated file is a run artefact recorded in provenance with its
  checksum -- not a scientific deliverable and not an interchange format for
  third parties.
* **The fidelity suite (``R-PDV-03``), which is the substance of this phase.**
  Every assertion compares the mzTab against the source, never against the
  exporter's own accounting: PSM counts on both sides; sequence, charge,
  observed *m/z*, retention time, accessions, q-value and PEP transcribed
  rather than recomputed; modifications compared as parsed values including
  position and mass; a field mzTab wants and the source lacks left explicitly
  null rather than defaulted; and export failing loudly, naming the PSM, if
  anything cannot be represented faithfully.
* **The ``spectra_ref`` identity test, on a file where the two orderings
  differ.** PDV numbers spectra by 1-based file position via ``msftbx`` while
  pepXML carries the instrument scan number, and they diverge for any
  scan-range subset. A test that uses a file where they coincide proves
  nothing.
* The PDV lifecycle in the process service (``R-PDV-04``): one instance per
  result set keyed on its mzTab, an ephemeral loopback port, readiness by
  polling ``/ready`` rather than sleeping, debounced selection, and shutdown
  when the result set closes or the application exits.
* Reuse of ``Noble-Lab/CasanovoGUI``'s ``PdvLauncher`` and ``PdvController``
  (``R-PDV-05``), retaining upstream copyright notices and recording the
  derivation per ``D-001``. Supply the mzTab and the results binding; do not
  reinvent the port, readiness and selection machinery.

Out of scope
------------

* Screen-coordinate automation of PDV, in production or in tests.

Deliverables
------------

* ``org.cometgui.tools.pdv`` -- launcher, controller and the mzTab exporter.
* Visualisation UI section wired to run context, with PSM selection driving
  the running PDV window.
* ``docs/pdv.rst``, stating plainly that the mzTab is generated for PDV's
  benefit and is not an interchange artefact.

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
5. PDV starts on an ephemeral port, ``/ready`` is reached by polling, and
   selecting a known PSM changes PDV's displayed spectrum -- asserted through
   the control API rather than the screen, under a timeout.
6. **The mzTab is proved accurate and true to the source.** Every PSM in the
   results model appears exactly once and none is invented; q-value, PEP,
   charge, *m/z*, retention time and accessions match the Percolator and Comet
   output they came from; modifications match as parsed values including
   position and mass. The comparison is against the source files, not against
   the exporter's own record of what it wrote.
7. ``spectra_ref`` resolves to the spectrum that actually produced the PSM,
   demonstrated on a spectrum file where 1-based file position and instrument
   scan number **differ**.
8. A PSM that cannot be represented faithfully makes export fail loudly and
   name it. Demonstrated by injecting such a PSM, not asserted in prose.

Risks and notes
---------------

* **The exporter is the risk, not the control server.** The control server is
  proven -- CasanovoGUI drives it in production. What is unproven is that PDV's
  ``MztabImport`` accepts an mzTab CometGUI generates rather than Casanovo's.
  **Spike that first**, on a real run, before building the exporter out: if
  PDV rejects the document the whole route needs rethinking, and that is far
  cheaper to learn in a day than after the fidelity suite is written.
* **Build the baseline first anyway.** Open-in-PDV and CLI figure generation
  satisfy gate items 1-4 and are independent of the exporter, so the phase can
  bank a working integration before the harder half starts.
* **PDV's CLI is not headless** -- ``PDVCLI.PDVCLIMainClass`` extends
  ``javax.swing.JFrame`` and throws ``HeadlessException`` before parsing an
  argument; ``-Djava.awt.headless=true`` makes it worse. Figure generation
  needs a display, which is a real constraint for CI.
* **PDV exits 0 having written nothing.** Judge every invocation by its output
  files, never by exit status, and impose a timeout: ``-rt 6`` was observed
  running 600 seconds writing nothing and had to be killed.

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
