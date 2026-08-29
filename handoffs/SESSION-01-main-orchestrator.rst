=================================================
Main orchestrator session handoff -- session 01
=================================================

:Session: 01 -- main orchestrator (tier 1)
:Dates: 2026-08-29
:Ended: after Phase 00 sign-off, before Phase 01 was dispatched
:Reason for stopping: the owner asked this session to stop at that point
:Phases signed off: 00 (``PARTIAL``)

This is a *session* handoff, not a phase handoff. Phase records live in
``handoffs/PHASE-00-worklog.rst`` and ``handoffs/PHASE-00-handoff.rst``. The
authoritative project state is ``STATUS.rst``; if this file and ``STATUS.rst``
ever disagree, ``STATUS.rst`` wins.

What this session did
=====================

#. Read in: ``ONBOARDING.rst``, ``STATUS.rst``, ``DECISIONS.rst``,
   ``phases/index.rst``, ``phases/PHASE-00-feasibility.rst``, and the two
   specification sections Phase 00 names.
#. Corrected two stale lines it owned before starting: ``STATUS.rst`` still
   described Phase 00's chief output as a costed ``D-002`` recommendation, and
   ``DECISIONS.rst`` was dated 2026-08-28 while carrying 2026-08-29 decisions.
#. Spawned **one** phase orchestrator for Phase 00, which decomposed the phase
   into ten work units and spawned a fresh agent for each. The three-tier model
   held: no tier did the tier below's job.
#. **Re-ran all ten gate items itself** and signed the phase off ``PARTIAL``.
#. Recorded the outcome in ``STATUS.rst``, amended ``specification.rst`` to
   revision 5, updated seven ``DECISIONS.rst`` entries with evidence and costed
   options, and updated ``phases/index.rst``.

What sign-off actually caught
=============================

Worth recording, because it is the argument for keeping sign-off expensive.

* **A fact changed mid-phase.** ``Noble-Lab/CasanovoGUI`` published GPL-3.0 at
  ``2026-08-29T01:56:35Z``. The Phase 00 work unit checked shortly before that
  commit and correctly recorded ``license = null``. The sign-off re-check about
  an hour later found the licence. Both were right when made; only re-running
  the check surfaced it. ``D-001`` changed from a permission question into a
  copyleft question as a result.
* **The re-run confirmed rather than contradicted the phase's headline
  finding.** The ``noxml`` result was reproduced independently: the ``noxml``
  binary given ``-X`` wrote a document differing from the XML-capable build's
  in two lines, both inside ``<command_line>``.
* **The phase's own honesty held up.** A grep of every committed document for
  *verified*, *confirmed*, *proven* or *tested* near a Windows XML claim
  returned nothing. The phase did not overclaim the item it could not test.

State of the tree
=================

* Committed: ``docs/feasibility/`` (10 documents, builds clean under
  ``sphinx-build -n -W``), ``scripts/feasibility/`` (re-runnable),
  ``handoffs/PHASE-00-*.rst``, and the four documents this tier owns.
* Not committed, by design: ``tools/``, ``.venv/``, ``_build/``, ``scratch/``
  are gitignored. ``tools/`` holds Liberica JDK 25.0.4.1+1, Maven 3.9.16,
  OpenJFX Monocle 21.0.2 and an X11/font stack; rebuild it with
  ``scripts/feasibility/install-toolchain.sh``. Its provenance is recorded in
  the committed ``docs/feasibility/toolchain.rst``.
* No product code exists. That is correct: Phase 00 writes none.
* There is still no git remote (``D-008``).

What the next session should do
===============================

**First action:** put the two owner questions in ``STATUS.rst``'s *Next
action* section to the owner -- ``D-001`` with ``D-008`` together, and
``D-002`` option C. Neither blocks starting Phase 01, but ``D-002`` option C
is much cheaper answered before Phase 05 than after.

**Then:** dispatch Phase 01 (``phases/PHASE-01-build-skeleton.rst``) to a
fresh phase orchestrator subagent. Phase 01's only dependency is met and its
toolchain is installed. It is blocked on ``D-008`` only for the ``LICENSE``
file itself -- the Maven layout, the Sphinx tree and the quality gates all
proceed without it, provided the ``LICENSE`` file is placed last and is never
invented.

Traps this session verified, which will bite later phases
==========================================================

Each was reproduced by the main orchestrator, not merely reported.

* ``jpackage`` **strips the runtime's** ``bin/java``. The app image has no
  ``bin`` directory at all. CometGUI must launch the Limelight converter JAR
  and PDV, so Phase 01 and Phase 16 need a plan for this.
* **Percolator's own XSD rejects Percolator's own output** --
  ``percolator_out.xsd`` fixes ``majorVersion`` at ``2``, the binary writes
  ``3``. ``R-TOOL-02`` installs those XSDs.
* **A help-text capability probe cannot work.** The XML and ``noxml`` twins
  print identical help. ``scripts/feasibility/probe_xml_capability.py`` is
  currently wrong for exactly this reason and must not be copied into the
  product.
* **The Limelight converter's exit status is unusable** -- it exits 0 with no
  arguments and on unrecognised options. Judge it by its output file.
* **PDV's CLI is not headless** and exits 0 having written nothing on indexed
  mzML. MGF is the working route; ``-rt 6`` hangs.
* **The pinned JDK ships no Monocle and this host has no fonts**, so any
  ``Scene`` containing a control dies on ``fontFactory is null`` until both are
  supplied. This will hit CI too.
* **Downloaded mzML arrived CRLF-corrupted and Comet exited 249.** Verify
  checksums and fetch in binary mode.

Owner decisions taken during this session
=========================================

After Phase 00 was signed off, the owner answered the licensing question.

* **``D-001`` DECIDED:** CometGUI derives from CasanovoGUI and is released
  under **GPL-3.0**. ``R-SEC-01``'s prohibition on copying CasanovoGUI source
  is lifted, replaced by an obligation to retain notices and record the
  derivation.
* **``D-008`` part decided:** the licence is GPL-3.0, which unblocks Phase 01's
  ``LICENSE`` file. **Where CometGUI is published, and whether tool binaries
  are redistributed, remain open. There is still no git remote and none may be
  created.**
* **PDV is to be treated as GPL-3.0** by owner direction, resolving its
  upstream ``LICENSE``/``pom.xml`` contradiction conservatively. Phase 16 must
  still obtain the real answer from upstream; the assumption is directionally
  safe because it constrains the project more than the Apache reading would.

Recorded in ``DECISIONS.rst``, ``STATUS.rst``, ``phases/index.rst`` and
specification revision 6. The one owner question left with a deadline is
``D-002`` option C, which should be answered before Phase 05 starts.
