=====================================================================
Between Phases 14 and 15 -- a tier-1 brief: the gate run's wall clock
=====================================================================

:Tier: a single fix agent, dispatched directly by tier 1 (no phase
   orchestrator)
:Dispatched by: Main orchestrator, session 11
:Date: 2026-11-05
:Starts from: the E12 repair signed off at ``0f07f63`` (``STATUS.rst``,
   ``status-fix-p14-p15``). No ``D-`` item is open.
:Runs alongside: **nothing.**
:Owner's instruction (2026-11-05): do the first two build-time fixes now,
   before Phase 15 -- *"Those changes are fine for this system, but remember
   on GitHub CI we only have 2 or 4 cores"*. Phase 15 itself is **not**
   resumed.

.. note::

   Written and owned by tier 1. Do not edit it. Record your work in
   ``handoffs/FIX-BUILD-TIME-worklog.rst``, which is yours. You do not edit
   ``STATUS.rst``, ``DECISIONS.rst``, ``phases/``, ``specification.rst`` or
   ``CLAUDE.md``.

.. _fixbt-builds:

Build economy -- the owner's standing rule, read before anything else
=====================================================================

The owner has said it three times (2026-09-17, 2026-09-18, 2026-10-01):
*running the whole build/test suite takes a very long time and slows
development; do not do it as routine.* At Phase 14's exit gate,
``scripts/build.sh`` took 142 minutes, ``verify-all-gates.sh --only tests``
321, and the other twenty controls 190 -- **10 h 52 min**. This brief exists
to cut that.

#. **You never run** ``scripts/build.sh`` **in full, and never run**
   ``scripts/verify-all-gates.sh`` **with no** ``--only``. Tier 1 runs the
   whole gate suite once, at this repair's sign-off. The working tree's
   ``target/`` directories and ``_build/m2repo`` are from the last full build
   and are what the harnesses read; do not ``clean`` the working tree.
#. **The inner loop is** ``bash scripts/dev-verify.sh``. It is never a gate.
   Its ``--mutation`` flag is broken; do not use it.
#. ``--only tests`` **is the one long harness you must run** (it is what fix 1
   changes). Run it detached (``setsid nohup ... &``) and wait for its exit
   line with a loop. **Never end your turn while your own check is still
   running**; nothing will wake you, and two orchestrators have stalled for
   exactly this reason.
#. Pass this section **verbatim** to any agent you start. (You should not
   need one: this is one agent's work.)

What is slow, as tier 1 measured it
===================================

Read ``scripts/verify-all-gates.sh``, ``scripts/verify-test-gates.sh`` and
the ``stage_gates`` function of ``scripts/build.sh`` first.

* ``--only tests`` (37 assertions, 321 minutes) runs **the whole**
  ``scripts/build.sh --offline`` **twice** inside its sandbox, in
  ``assert_build_script_fails``, for controls 7 (unmeasured coverage) and 8
  (a class that left the coverage sample). Each run is a full ``clean
  verify`` of all twelve modules with every test, the GUI tests and the real
  Comet and Percolator runs included -- about two hours each -- in order to
  reach the evidence census in ``stage_gates``. The comment above control 7
  still says this costs "90 s"; that was Phase 01.
* ``verify-all-gates.sh`` runs its twenty-one controls **one after
  another** on a 64-core machine. Each already has its own sandbox under
  ``_build/<name>-gate-sandbox``.

The two fixes
=============

Fix 1 -- the ``tests`` harness stops rebuilding the world to reach one check
------------------------------------------------------------------------------

Controls 7 and 8 must prove the **same thing they prove today**: that the
evidence census in ``scripts/build.sh`` -- the real code, the one tier 1's
gate run executes, not a copy of it -- rejects (7) a module with no
``jacoco.xml`` and (8) a compiled class missing from ``jacoco.xml``, with the
same diagnostics they grade today (``MISSING  cometgui-domain: ...`` and the
anchored ``^ +org\.cometgui\.domain\.secrets\.SecretRegistry$`` line).

What changes is how much work runs to get there. The defect is in one
module, ``cometgui-domain``, and the control has already run that module's
``clean verify`` itself (``assert_passes ... -pl cometgui-domain clean
verify``). Find the least work that puts the census in front of the
defective module with a complete, honest picture of the other eleven, and
reach it through ``scripts/build.sh`` itself (for example through its
existing ``--only`` mechanism, or a narrowly scoped option you add to it).
Whatever you add:

* **must not be a way to skip the census in a real build.** Tier 1's gate
  run and CI call ``scripts/build.sh`` with no options; their behaviour must
  be byte-for-byte what it is today. Say in the work log how you proved it.
* must not make the census read stale evidence and pass. Add a control that
  shows your shortcut cannot turn a real defect green -- e.g. the defect
  present and the other modules' evidence missing or old must still be red,
  not silently accepted.
* must keep every other assertion of the harness, and its floor of 37
  assertions; raising it for your new controls is welcome.

Fix the stale "90 s" comment while you are there.

Fix 2 -- ``verify-all-gates.sh`` runs controls side by side, sized by cores
-----------------------------------------------------------------------------

Add a job count to ``scripts/verify-all-gates.sh`` and run the controls
concurrently up to it.

* **The default must be safe on GitHub's runners, which have 2 or 4 cores
  (owner, 2026-11-05).** Follow the precedent of ``cometgui.pit.threads``
  (``STATUS.rst``, the Phase 05 build-economy section): compute it from the
  cores this process may use (``nproc`` honours CPU affinity), and **at 4
  cores or fewer it is 1 -- serial, exactly today's behaviour.** Cap it
  where measurement says more stops helping; say what you measured. Allow an
  explicit override (``--jobs N`` or an environment variable), with ``1``
  meaning serial.
* **Each control's result and log stay its own.** The summary, the
  per-control floors, the exit code and the order the summary prints in are
  unchanged; a control that fails while others run is still reported as
  failing, with its log, and the run's exit code is still non-zero. A
  control that dies, hangs or is killed must not read as passing.
* **Find what the controls actually share before running any two at once**
  -- this is the hard part, and "they have different sandboxes" is not the
  argument (``ONBOARDING.rst``, :ref:`onboarding-what-agents-actually-share`).
  At least: ``_build/m2repo``, the ``tools/`` cache, X displays (five
  harnesses start Xvfb), loopback ports (the Limelight fake, PDV's port),
  fixed paths under ``/tmp``, ``scratch/``, and anything a harness writes
  in the working tree. Record each shared thing and what you did about it in
  the work log. Where two controls cannot be made safe together, serialise
  that pair rather than guess.
* **Load is a known hazard here.** ``build.sh`` grades the mutation gate on
  ``KILLED`` alone, so a few boundary mutants in ``cometgui-process`` and
  ``cometgui-install`` move with machine load; GUI tests wait on the
  interface. If running concurrently turns a control red or flaky, that is a
  finding: fix the root cause or lower the concurrency, and **never** raise a
  timeout, add a sleep, relax an assertion or touch the survivor set to make
  it pass.
* The nightly pipeline's call of ``verify-all-gates.sh`` keeps working
  unchanged on a 2- or 4-core runner.

Prove it
========

* **Fix 1:** run ``bash scripts/verify-all-gates.sh --only tests``
  (detached, waited on) and report its assertion count and wall clock against
  321 minutes. Inject into the census itself in ``scripts/build.sh`` --
  first so it no longer reports a missing ``jacoco.xml``, then so it no
  longer reports an absent class -- and show controls 7 and 8 go red for
  each, restored and verified with ``sha256sum -c``. Count the tests that ran
  in each sandbox build.
* **Fix 2:** with ``taskset -c 0-3`` and ``taskset -c 0-1``, show the
  computed job count is 1 and the run is serial (from its own log, not its
  exit code). Run a short concurrent batch of the cheaper controls (e.g.
  ``--only docs --only traceability --only shell --only quality --only
  provenance --only pipeline``) at the computed count and show every count
  matches its floor. Inject a failure into one control of a concurrent batch
  (a harness forced to exit non-zero, and one killed mid-run) and show the
  summary and exit code report it. Do **not** run all twenty concurrently
  yourself; tier 1 does that at sign-off.
* ``scripts/ci/docs-build.sh`` and ``--only shell`` (it checks the shell
  scripts) after your last change; ``--only docs`` and ``--only
  traceability`` if any ``.rst`` changed.

Standing rules
==============

* **Sign-off means you saw it go red for a reason you chose.** Prove an
  injection landed (the old anchor is gone, not just a marker found),
  restore and verify with ``sha256sum -c``.
* Never weaken a gate, checksum, validation rule, threshold or floor; never
  add to the pinned survivor set. No POM changes are approved by this brief.
* No product code changes are expected. If you find you need one, stop and
  escalate.
* **On a red check, fix the root cause and continue** (owner, 2026-10-01);
  record it and report it.
* Commit with an explicit pathspec, never ``git add -A``. **Do not push.**
* All documentation is reStructuredText and passes
  ``scripts/ci/docs-build.sh``. Update ``CONTRIBUTING.rst`` or the build
  documentation wherever they describe how the gate suite runs.
* Escalate upward only. Never contact the owner or answer a ``D-`` item.

Report back
===========

Report to tier 1 when finished or genuinely blocked: commits; what each fix
changed; what the controls share and what you did about each; the job-count
rule and the measurement behind its cap; your injections with their failure
text; the ``--only tests`` count and wall clock; anything escalated. Short
and factual.
