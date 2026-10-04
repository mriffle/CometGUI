=======================================================================
Comet 2026.03.0 intake brief -- tier 1 to the work-package orchestrator
=======================================================================

:Work package: Comet 2026.03.0 intake (amends Phases 05 and 06; runs before
   Phase 07)
:Tier: 2 -- orchestrator (one fresh agent, one work package)
:Dispatched by: Main orchestrator, session 10
:Date: 2026-10-04
:Authority: ``D-010`` (decided by the owner 2026-10-04) and
   ``specification.rst`` revision 12
:Depends on: Phases 05 (PARTIAL) and 06 (PASSED), both signed off. No ``D-``
   item is open.
:Runs alongside: **nothing.** You are the only orchestrator live in this tree.

.. note::

   Written and owned by tier 1. Do not edit it. What you produce goes in
   ``handoffs/COMET-2026-03-worklog.rst`` and
   ``handoffs/COMET-2026-03-handoff.rst``, which are yours. You do not edit
   ``STATUS.rst``, ``DECISIONS.rst``, ``phases/``, ``CLAUDE.md`` or
   ``specification.rst``. Specification text you need changed is **proposed**
   to tier 1 in your report, with the evidence; tier 1 amends by revision.

.. _c2603-builds:

Build economy -- the owner's standing rule, read before anything else
=====================================================================

The owner has said it three times (2026-09-17, 2026-09-18, 2026-10-01):
*running the whole build/test suite takes a very long time and slows
development; do not do it as routine.* ``scripts/build.sh`` is about 23
minutes and ``scripts/verify-all-gates.sh`` about 80 more.

#. **Neither you nor any unit agent runs** ``scripts/build.sh`` **or**
   ``scripts/verify-all-gates.sh`` **in full.** No opening baseline: tier 1 ran
   both at Phase 06's exit gate on the tree you start from (``8ffe626``, plus
   test- and docs-only repairs since). Tier 1 runs them once more, at **this**
   work package's exit gate.
#. **The inner loop is** ``bash scripts/dev-verify.sh`` -- it maps the diff to
   the modules it can break (``--dry-run`` shows the selection). It is never a
   gate.
#. **Its** ``--mutation`` **flag is broken** and must not be used: pitest-maven
   1.30.0 removed built-in incremental history. For mutation evidence at unit
   sign-off, run PIT on the **one** module, restricted to the classes the unit
   changed, e.g. ``mvn -pl <module> -am test-compile
   org.pitest:pitest-maven:mutationCoverage -DtargetClasses=<fqcn,...>``, with
   the project's Maven settings (copy how ``scripts/dev-verify.sh`` invokes
   Maven). Never add a PIT plugin or change the POM's PIT configuration.
#. **Unit sign-off** is: read the diff, run the affected module's tests,
   targeted PIT as above, your own production-code injections, and
   ``scripts/ci/docs-build.sh`` if ``.rst`` changed. Nothing wider -- **but not
   narrower either**: run ``bash scripts/verify-all-gates.sh --only NAME`` for
   every gate whose harness reads what the unit changed. This package touches
   ``manifests/tools.json`` (read by ``docs/conf.py``, the ``install`` and
   ``traceability`` harnesses) and the generated parameter reference (read by
   ``docs``, ``traceability`` and ``params``). Phase 05 went red at its exit
   gate because nobody ran ``--only traceability`` after a manifest-reading
   change; do not repeat it.
#. Pass this section **verbatim** to every unit agent. They do not see this
   brief otherwise.

Read first, in this order
=========================

Read them properly, not by grep.

#. ``ONBOARDING.rst`` -- all of it, especially *Roles*, *Sign-off* and *What
   parallel agents actually share*.
#. ``CONTRIBUTING.rst``.
#. ``DECISIONS.rst`` ``D-010`` -- what the owner decided and how tier 1 reads
   it.
#. ``specification.rst`` revision 12 -- the *Comet* section (default version
   policy), ``R-PARAM-01``..``R-PARAM-12``, ``R-TEST-01``, ``AC-PAR-01``,
   ``02``, ``06``, ``11``, and the tool-registry section.
#. ``phases/PHASE-06-comet-param-model.rst`` and
   ``handoffs/PHASE-06-handoff.rst`` -- the model you are extending, and how
   its fixtures were captured.
#. ``handoffs/PHASE-05-handoff.rst`` -- the manifest, installer, probe and
   ``scripts/verify-install-gates.sh``.
#. The upstream release: ``https://github.com/UWPR/Comet/releases/tag/v2026.03.0``
   and ``https://uwpr.github.io/Comet/parameters/parameters_202603/``.
   **These are leads, not facts.** Every behaviour you build on is confirmed
   by running the real binary.

What this work package is, plainly
==================================

Comet 2026.03.0 was released on 2026-10-01. The owner has made it the default
verified Comet version; 2026.02.2 stays supported as an older version whose
files import and migrate. The specification's own rule applies: *a new Comet
release enters the managed registry only after automated compatibility and
regression tests pass.* This package earns that entry, and leaves Phase 07 a
model it can build the editor on.

What the release notes say changed that touches this project -- each is a
claim to **verify by execution**, then model:

* ``variable_modNN``'s residue field accepts ``^`` (protein N-terminus) and
  ``$`` (protein C-terminus).
* The fifth/sixth fields: a distance below -2, or a terminus outside 0-3 with a
  distance rule, is now **rejected with an error**.
* AScorePro enabled together with ``variable_mod10``..``15`` is now an error.
* ``index_search_type`` now only picks which index type to auto-build;
  ``comet -p`` no longer writes it and ``comet -q`` writes ``-1``. A 2026.02.2
  ``-p`` file carries ``index_search_type = 1``, which now warns.
* ``add_U_selenocysteine`` is now actually read; the spectral-library MS level
  parameter's name is fixed; an undefined ``search_enzyme_number``,
  ``search_enzyme2_number`` or ``sample_enzyme_number`` is now an error.
* ``.idx`` format v5: earlier indexes are rejected.
* Two new Windows assets (``CometWrapperCore.dll``, ``Ijwhost.dll``) for a .NET
  8 real-time wrapper. CometGUI does not use the wrapper; decide from the
  installer's existing companion-file rules whether they are required for
  ``comet.win64.exe`` and record why.

The trap, this time: **a version-blind rule.** If ``^`` becomes legal, it must
be legal *for 2026.03.0* and still rejected for 2026.02.2; if a position field
becomes an error, 2026.02.2 files that used it must migrate with a visible
change, not silently. Every validation and every schema fact is keyed on the
selected version. A test that only ever runs against one version cannot see
this defect.

Exit gate
=========

You verify every item; tier 1 then re-runs them. An item that cannot be
verified has not passed.

#. **Registry.** ``manifests/tools.json`` carries Comet 2026.03.0 for every
   platform the manifest already covers, each with URL, size and SHA-256
   computed from bytes **you downloaded**, not copied from anywhere. 2026.03.0
   is the default; 2026.02.2 remains. The product's real installer installs
   2026.03.0 on Linux x86-64 from upstream, verifies it, and the probe reads
   ``2026.03.0`` from the binary's banner. ``scripts/verify-install-gates.sh``
   passes at a floor no lower than today's, and a corrupted 2026.03.0 hash is
   refused.
#. **Fixtures.** Real ``comet -q`` and ``comet -p`` output for 2026.03.0,
   captured from the binary, pinned by SHA-256, with the capture recipe
   recorded so a successor can reproduce it. The 2026.02.2 and 2024.01.0
   fixtures are unchanged.
#. **Schema.** Every parameter 2026.03.0's ``-q`` emits is modelled with
   metadata or allow-listed as internal; the drift test runs per version in
   the matrix, and goes red when an entry is removed *and* when a parameter is
   modelled for a version whose binary does not emit it. Report the real
   ``-q``/``-p`` counts for 2026.03.0 and what ``-p`` misses, for tier 1 to
   put in the specification.
#. **Round trip.** Gate items 1, 3, 4, 5 and 6 of
   ``phases/PHASE-06-comet-param-model.rst`` hold for 2026.03.0 as they do for
   2026.02.2, including ``^`` and ``$`` across all fifteen slots.
#. **Version-scoped validation, agreed with the binary.** For each rule change
   above, a corpus of parameter files that the real 2026.03.0 binary accepts
   or rejects (by running it -- record the command and its output) and the
   validator agrees on every case. The same ``^``/``$`` and position-field
   cases are judged by 2026.02.2's rules and give 2026.02.2's answer. Where
   the binary cannot be run to settle a case, say so; do not infer.
#. **Migration.** 2026.02.2 and 2024.01.0 fixtures migrate to 2026.03.0 with
   a reviewable diff, and the ``index_search_type = 1`` line is handled
   visibly. A migrated file is accepted by the real 2026.03.0 binary.
#. **Reference.** The generated parameter reference covers 2026.03.0 and
   builds strictly; ``--only docs --only traceability --only params`` pass.
#. **Mutation.** Gate item 9 of Phase 06 holds over the changed classes, with
   no surviving mutant that makes a version-scoped rule version-blind.

Standing rules
==============

* **Serial is the default** (owner, 2026-08-31). Parallel units need a
  recorded argument that collision is *impossible*. None will exist here.
* **Sign-off means you saw it go red for a reason you chose.** Inject into
  **production** code, prove the edit landed (anchor matches once; marker
  grepped back), restore and verify with ``sha256sum -c``. Use a private
  scratchpad subdirectory. At least one injection per unit makes a rule
  version-blind.
* **Fixtures are real output, never hand-written.**
* **Audit the population, not only the number**: every class in
  ``target/classes`` appears in the coverage and mutation reports.
* Locale: number formatting uses ``Locale.ROOT``.
* Never weaken a gate, checksum, validation rule or threshold; never add to
  the pinned survivor set in ``scripts/verify-test-gates.sh``. Raise floors,
  never lower one. Additive controls are welcome.
* Network access to GitHub for the upstream release is approved. Nothing is
  installed on the host; binaries go where Phase 05's tooling puts them.
* Commit with an explicit pathspec, never ``git add -A``. **Do not push.**
* All documentation is reStructuredText and passes
  ``scripts/ci/docs-build.sh``.
* **On a red check in this work, fix the root cause and continue** (owner,
  2026-10-01); record it and report it. Escalate only what you cannot fix
  without weakening something or answering a ``D-`` item.

Also yours, because it is in the path
=====================================

* ``scripts/verify-test-gates.sh``'s precondition checks
  ``scratch/phase05/artefacts`` but not ``scratch/phase06/artefacts``, which
  the sandbox build now needs (Phase 06 residue). If your fixtures add a third
  location, the precondition covers it too.
* ``phases/PHASE-06-comet-param-model.rst`` says "eleven other parameters";
  do **not** edit it -- report it; tier 1 owns phase documents.

Not yours
=========

Any JavaFX control (Phase 07); running a search or building an ``.idx``
(Phase 08); the "selected index is compatible" validation, which no phase owns
(report what 2026.03.0's v5 format changes about it, build nothing); the
mutation-scoring question in ``scripts/build.sh`` (the owner's); repairing
``dev-verify.sh --mutation`` (tier 1); macOS and Windows execution (record the
macOS/Windows rows as unverified unless a workflow already runs them, and say
which workflow files name a Comet version so tier 1 can update and run them).

Report back
===========

When the work ends -- finished, stalled or stopped -- write
``handoffs/COMET-2026-03-handoff.rst`` for a successor who cannot ask you
questions, and report to tier 1: commits, each gate item with its evidence
(met, partial with residue named, or unverifiable), the injections you used,
proposed specification text with its evidence, and anything escalated. Short
and factual; every claim is re-run, not read.
