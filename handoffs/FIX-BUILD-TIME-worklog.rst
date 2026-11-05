===============================================================
Between Phases 14 and 15 -- the gate run's wall clock, work log
===============================================================

:Brief: ``handoffs/FIX-BUILD-TIME-BRIEF.rst`` (tier 1's; not edited)
:Agent: a single fix agent, dispatched directly by tier 1
:Started: 2026-11-05, from ``783444e`` on ``main``, ``git status`` clean
:Commits: listed in `Commits`_ at the end

Kept by the fix agent as the work ran. Every number below was read off a run
made in this session. Every injection was proved to have landed by its old
anchor being gone (``grep -c`` = 0, and ``sha256sum -c`` reporting FAILED
while it was in), and was restored and checked with ``sha256sum -c``.

.. contents:: Contents
   :depth: 2
   :local:

Build economy, as followed
==========================

``scripts/build.sh`` was never run in full, and ``scripts/verify-all-gates.sh``
never without ``--only``. No ``target/`` directory of the working tree and
nothing in ``_build/m2repo`` was cleaned or written. ``--only tests`` and every
``verify-test-gates.sh --census-only`` run were started detached (``setsid
nohup ... &``) and waited on to their exit line.

Fix 1 -- the ``tests`` harness stops rebuilding the world
=========================================================

What it cost, and why
---------------------

Controls 7 and 8 each ran ``bash scripts/build.sh --offline`` inside the
sandbox to reach the evidence census in the gates stage: 46 minutes of
``mvn clean verify`` over twelve modules (``cometgui-app`` alone 24
minutes), the artefact and format stages, then PIT over the reactor -- the
census runs before PIT but the stage only dies after it -- about 140 minutes
each, 280 of the 321 that ``--only tests`` took at Phase 14.

Why not borrow the working tree's evidence
------------------------------------------

The cheapest picture of the other eleven modules would have been the working
tree's own ``target/`` directories. Measured on 2026-11-05, it would have been
dishonest: five modules' ``jacoco.xml`` (``cometgui-domain``, ``-tools``,
``-workflow``, ``-ui``, ``-app``) dated from 2026-10-24 and predated sources the
E12 repair changed on 2026-10-25 to 2026-10-30, while ``dev-verify.sh`` had
recompiled their classes since. The census run over a shadow of the working
tree with the new currency check reported exactly those five as ``STALE``
(and the domain, whose ``target/classes`` the shadow reached through a link,
first as ``NO EVIDENCE`` -- which is why the check's ``find`` follows a linked
starting point). Borrowing would also have tied the ``tests`` control to a full
build having just run in the working tree.

What changed
------------

``scripts/build.sh``

* The gates stage is split, without changing a statement, into
  ``gates_evidence_census`` (everything it reads before PIT) and
  ``stage_gates_pit`` (PIT, its grading and the stage's verdict).
  ``stage_gates`` calls the two. The census reads ``EVIDENCE_ROOT``, which is
  ``ROOT`` in every real build; ``real_classes_of`` and ``module_opts_in`` read
  it too.
* ``--gates-census-root DIR`` runs ``gates_evidence_currency`` and then
  ``gates_evidence_census`` over ``DIR`` and exits. It refuses the repository
  itself (``pwd -P`` comparison) and refuses every other option, builds
  nothing, runs no PIT, writes nothing, and ends with its own verdict line,
  never ``BUILD OK``. ``gates_evidence_currency`` exists only on this path:
  a product module with main sources and no compiled classes is ``NO
  EVIDENCE`` (the census alone would call it inert), and a module whose
  ``src/``, POM or the parent POM is newer than the evidence read for it --
  its ``jacoco.xml``, else its newest class file, or for ``cometgui-archtests``
  its import census -- is ``STALE``.

``scripts/verify-test-gates.sh``

* New control **C**, after control 6: proves every module's sources and POM in
  the sandbox are byte-identical to the working tree (a HARNESS ERROR
  otherwise), runs ``mvn clean verify`` over the whole sandbox once, counts
  the tests its surefire reports hold, requires
  ``build.sh --gates-census-root`` to accept the sandbox (the clean
  counterpart of 7 and 8, which the old harness only claimed), requires the
  census to have graded ``cometgui-domain``'s population, and requires the
  option to refuse the working tree.
* Controls 7 and 8 call the working tree's ``scripts/build.sh
  --gates-census-root`` over the sandbox (``census_run``) after their own
  ``-pl cometgui-domain clean verify``. Their diagnostics are the same
  (``MISSING  cometgui-domain: [1-9][0-9]* class\(es\) with code but no
  jacoco\.xml`` and ``ABSENT ...`` with the anchored
  ``^ +org\.cometgui\.domain\.secrets\.SecretRegistry$``); each now also
  requires the census to have failed for that reason alone (``^FATAL: 1
  test-gate evidence check\(s\) failed\.$``).
* The shortcut's own controls, each with the defect present:

  - control 7, before the domain is rebuilt: the POM is damaged and
    ``target/`` still holds control C's clean ``jacoco.xml``, which the census
    would read as ``ok``. Required: red, ``STALE    cometgui-domain:
    cometgui-domain/pom.xml is newer than``;
  - control 8, ``cometgui-results/target`` moved aside: red, ``NO EVIDENCE
    cometgui-results``, and the domain's absent class still named;
  - control 8, a ``cometgui-results`` source touched newer than its
    ``jacoco.xml``: red, ``STALE    cometgui-results: <that file> is newer
    than``, and the domain's absent class still named.

* The stale "90 s" comment is replaced; the ``build_sandbox`` comment no longer
  says the sandbox runs ``build.sh`` (``scripts/`` is still copied: the
  sandbox build's tests read it, e.g. ``ProjectXvfb`` compiles the xkbcomp
  shim from it).
* ``--census-only`` runs controls C, 7, 8 and S alone, for checking a change to
  the census. It is not the gate; the aggregate never passes it, and its floor
  would fail a run that short.
* Assertions: 37 + 12 (C: 5; 7: 2; 8: 5) = **49**; the floor in
  ``verify-all-gates.sh`` is raised from 37 to 49.

Proof that a real build is byte-for-byte unchanged
--------------------------------------------------

``tier 1``'s gate run and CI call ``scripts/build.sh`` with no options, and the
pull-request pipeline calls ``--only gates`` through ``scripts/ci/``. For both,
the only code path that changed is the gates stage's, so the original
(``git show 783444e:scripts/build.sh``) and the new script were each run as
``--only gates`` from a shadow root -- a scratch directory holding the script,
links to the twelve modules and the parent POM, and its own ``_build/`` --
with a stub ``mvn`` first on ``PATH`` (it prints its arguments and exits 0, so
PIT's grading reads the working tree's last PIT reports and nothing in the
working tree is written). Output normalised only for the shadow's path and the
seconds in the summary:

* the passing path: 361 lines each, ``cmp`` identical, both exit 0;
* the failing path (the domain's ``target/site`` hidden in both shadows): 272
  lines each, ``cmp`` identical, both ``MISSING  cometgui-domain: 83
  class(es) with code but no jacoco.xml`` and ``FATAL: 2 test-gate evidence
  check(s) failed.``, both exit 1.

``--gates-census-root`` is reached only by its own option, which a
no-option or ``--only`` run never parses; ``EVIDENCE_ROOT`` is assigned
``ROOT`` at start and changed only inside that branch.

Injections into the census
--------------------------

Both into the working tree's ``scripts/build.sh`` (the file ``census_run``
executes), each checked with ``sha256sum -c`` against the saved
``93d76539444d8a38...`` (FAILED while in, OK after the restore), each graded by
``bash scripts/verify-test-gates.sh --census-only`` run detached.

**Injection 1** -- the census no longer reports a missing ``jacoco.xml``:
``if [ "${real_count}" -eq 0 ]; then`` became ``if true; then`` (old anchor
count 0), so a module with no report is printed inert. Run: 3302 s, ``19
assertion(s) passed, 2 failed``, exit non-zero. The census printed ``inert
cometgui-domain no classes with code yet`` and ``OK: the gates stage's
evidence census passed``; the failures were exactly control 7's::

   FAIL  scripts/build.sh's evidence census rejects unmeasured coverage:
         scripts/build.sh's evidence census exited 0 with the defect present
   FAIL  and for that reason alone: one evidence check failed: nothing in
         _build/test-gate-logs/07-unmeasured-census.log matches
         '^FATAL: 1 test-gate evidence check\(s\) failed\.$'

Control 8, control C, the STALE and NO EVIDENCE controls and control S all
passed.

**Injection 2** -- the census no longer reports an absent class:
``if [ -n "${absent}" ]; then`` became ``if false; then`` (old anchor count 0),
so the population check always prints ``ok``. Run: 2713 s, ``16 assertion(s)
passed, 5 failed``, exit non-zero. The census printed ``ok cometgui-domain 79
compiled class(es), all 79 in the sample`` over a ``jacoco.xml`` without
``SecretRegistry``; the failures were control 8's three and the two "still
reported beside it" checks of the shortcut's controls::

   FAIL  scripts/build.sh's evidence census rejects the incomplete population:
         scripts/build.sh's evidence census exited 0 with the defect present
   FAIL  and it names the class that left the sample, on the census's own
         line: nothing ... matches '^ +org\.cometgui\.domain\.secrets\.SecretRegistry$'
   FAIL  and for that reason alone: one evidence check failed: nothing ...
         matches '^FATAL: 1 test-gate evidence check\(s\) failed\.$'
   FAIL  and the domain's defect is still reported beside it  (never built)
   FAIL  and the domain's defect is still reported beside it  (old evidence)

Control 7 and the rest passed. In both runs every census-root control that
does not depend on the injected check passed, so each injection was caught
by the controls meant for it and only by them.

Tests run in each sandbox build
-------------------------------

* control C's ``mvn clean verify`` over the sandbox: 10,381 tests by Maven's
  per-module summaries (96 skipped: 91 are ``cometgui-app``'s opt-in
  end-to-end classes, which every build skips), 0 failures, 54 min 29 s
  (the harness's own tally from the ``TEST-*.xml`` files, read the way
  ``build.sh``'s artefact stage reads them, was 10,339 in 651 reports);
* control 7's and control 8's ``-pl cometgui-domain clean verify``: 1,230
  tests each, 10 s and 11 s.

Both census-only runs ran control C's build the same way and reported the
same counts. The injection-2 run ran while the six-control concurrent batch
below was running beside it, and its control C was green.

A red in a control this repair did not touch, and its root cause
-----------------------------------------------------------------

The first full ``--only tests`` run after the change (started 02:30) went red
at control 6, before control C::

   sizing   cometgui-domain generates 63 mutations and kills all of them;
            K/(K+S) < 80% needs S > K/4 = 15 survivors.
   FAIL  the mutation gate rejects a suite that kills too few mutations:
         the gate exited 0 with the defect present

Control 0's baseline PIT run over the same sandbox had reported ``Generated 716
mutations Killed 715`` and the dirty run ``Generated 749 mutations Killed 715
(95%)``: the injection was sized for a module a tenth the size. ``pit_report_of``
looked for the sandbox's ``cometgui-domain`` report, found none -- controls 4
and 5 run ``clean verify`` over the domain and remove it -- and fell back to
the working tree's ``cometgui-domain/target/pit-reports/mutations.xml``. That
file, dated 2026-10-29 22:41, holds 63 mutations of two classes
(``RunAttempt``, ``RunDescriptor``): the E12 repair's targeted PIT run, which
that work log records ("PIT was run on the changed classes only"). It passed at
Phase 14 only because the working tree then held a whole-module report.

Not load, and not concurrency: nothing of mine ran beside it after 02:41, and
no mutant timed out (``TIMED_OUT 0``). Fixed at the root (``990b62c``):
control 0 keeps a copy of the pristine sandbox's report and control 6 sizes
from it; ``pit_report_of`` no longer reads the working tree at all, and a
missing report is a HARNESS ERROR. No threshold, survivor set or assertion
changed. The run was stopped with SIGTERM (the aggregate printed
``interrupted; ending every running control.``, every harness, Maven and test
process ended, exit 1) and started again.

The same fallback remains in ``jacoco_report_of``, which sizes controls 4 and
5 from the sandbox's ``jacoco.xml`` or else the working tree's. Control 4's is
always the sandbox's (control 0 has just measured the domain); control 5 sizes
from the working tree's ``cometgui-ui`` report, because no earlier control
measures the UI module and doing so costs a 17-minute build. That report is a
whole-module one today (2026-10-24), but it is not this run's measurement: it
is reported to tier 1 rather than changed here.

``--only tests``, the number the brief asked for
------------------------------------------------

``bash scripts/verify-all-gates.sh --only tests``, started detached at
03:17:05 after the control 6 fix (``990b62c``), the scripts exactly as
committed::

   04:44:21  finished tests         exit 0 in 5236s (1 of 1)
   PASS  tests: 49 assertions in 5236s
   1 control(s) passed, 0 failed, in 5237 seconds (87m17s).

and from the harness itself, ``SUMMARY: 49 assertion(s) passed, 0 failed, in
5235 seconds``. **87 minutes, against 321 at Phase 14**; the floor of 49 met.
Control 6 sized itself from ``cometgui-domain generates 716 mutations``.
Control C's whole-sandbox build took 42 min 53 s; the four shared resources
were unchanged. The previous run, red at control 6 as described above, had
run beside ``shell``, ``docs`` and ``traceability`` for its first eleven
minutes; this one ran alone (other tenants of the host aside).


Fix 2 -- the aggregate runs controls side by side
=================================================

What changed
------------

``scripts/verify-all-gates.sh``:

* ``--jobs N`` or ``COMETGUI_GATE_JOBS=N`` (the option wins; a value that is
  not a whole number of at least 1 is exit 2); otherwise ``compute_jobs``:
  ``nproc``, which honours CPU affinity; **1 at 4 cores or fewer**; above that
  ``cores / 8``, at least 1, at most 4.
* ``JOBS = 1`` is the serial loop it always was, ``run_gate`` per control, now
  written as ``gate_banner`` + ``exec_gate`` + ``grade_gate`` with the same
  statements in the same order. The header gains one line saying the run is
  serial and why.
* ``JOBS > 1``: ``run_concurrently`` starts controls in ``ALL_GATES`` order
  whenever a slot is free and none of the control's locks is held, each in a
  worker that writes ``<exit status> <seconds>`` to
  ``_build/all-gate-logs/.state/<name>.done`` only after its harness has
  ended, and prints a timestamped ``started`` / ``finished ... exit N`` /
  ``LOST`` line as each happens. When all have ended, every control is graded
  by ``grade_gate`` -- the unchanged grading -- in ``ALL_GATES`` order, so
  the summary, the per-control floors, the order and the exit code are a
  serial run's. A control with no ``.done`` is graded FAIL: "no exit status
  was recorded: the control's worker died or was killed before its harness
  ended". INT or TERM ends every running control's process tree and exits 1.
* A concurrent run fingerprints ``_build/m2repo``, ``tools/``, ``scratch/``
  and ``.venv/`` (every path, size and mtime, links followed, Python
  ``__pycache__`` pruned) before the first control and after the last; a
  change is a counted FAILURE naming the resource.

What the controls share, and what was done about each
-----------------------------------------------------

Found by reading every harness and the scripts and tests they run, and by
looking at what changed on disk across the last two gate runs (2026-10-24 and
2026-10-30), not from the sandbox names.

.. list-table::
   :header-rows: 1
   :widths: 22 48 30

   * - Shared thing
     - What the audit found
     - What was done
   * - The working tree's sources
     - Thirteen harnesses build their sandbox with ``git archive`` from
       ``HEAD`` and cannot see the working tree. ``quality`` and ``tests``
       copy ``src/``, ``config/``, POMs and (``tests``) ``scripts/``,
       ``manifests/``, ``specification.rst`` and one ``docs/developer`` page.
       No control writes any of those.
     - Nothing to serialise. Do not edit those files during a run (as before).
   * - ``docs/_build/``, ``docs/developer/traceability.rst``,
       ``docs/_generated/``, ``_build/docs-gate``
     - Every real Sphinx build of ``docs/`` deletes and rewrites
       ``docs/_build`` and, through ``conf.py``'s ``builder-inited`` hooks,
       writes the generated pages into the source tree: ``docs``
       (``docs-build.sh --self-test``) and ``pipeline`` (the release step's
       ``docs-build.sh`` and the nightly link check). ``traceability``'s
       self-test copies ``docs/`` with ``shutil.copytree`` meanwhile, which
       fails on a file that vanishes under it.
     - Lock ``docs-tree`` held by ``docs``, ``traceability``, ``pipeline``.
   * - ``_build/sbom/``
     - ``sbom.sh`` deletes and rewrites it (``sbom``, and ``pipeline``'s
       release step); ``depscan`` reads ``_build/sbom/cometgui-sbom.json``.
     - Lock ``sbom-out`` held by ``sbom``, ``depscan``, ``pipeline``.
   * - ``_build/m2repo``
     - Only read. Its newest file is from 2026-10-23, though full gate runs
       were made on 2026-10-24 and 2026-10-30; ``param``, ``paramui`` and
       ``workflow`` install into private overlays whose other entries link
       into it (offline, read only); ``pdv``, ``limelight``,
       ``provenanceui`` and ``e2e`` already digest its ``org/cometgui`` jars
       before and after.
     - Fingerprinted around every concurrent run.
   * - ``tools/``, ``scratch/``, ``.venv/``
     - Linked into most sandboxes and only read; nothing under ``tools/`` or
       ``scratch/`` changed since 2026-10-24. The pipeline's ``toolchain``,
       ``python-env`` and ``fontstack`` steps verify, and fetch only what is
       missing.
     - Fingerprinted around every concurrent run.
   * - X displays
     - ``ProjectXvfb`` (``cometgui-tools`` and ``cometgui-app`` tests, used by
       ``tests``, ``pdv``, ``limelight``, ``provenanceui``, ``e2e``) starts
       Xvfb with ``-displayfd``: Xvfb chooses a free display itself, taking
       its lock file exclusively. No harness names a display.
     - Nothing to serialise.
   * - Loopback ports
     - Every server binds port 0 (the Limelight fake, the loopback artefact
       servers, the bridge). PDV's port is reserved by binding port 0 and
       released before PDV binds it, and two tests take a "dead" port the
       same way: a window of milliseconds in which another process would have
       to be handed the same ephemeral port. No fixed port anywhere.
     - Nothing serialised; recorded here as the one residual. A collision
       would surface as a red test, never as a green one.
   * - Fixed paths under ``/tmp``
     - None. ``e2e``'s smoke install uses ``mktemp -d``; the tests use JUnit
       temporary directories. The one literal ``/tmp/comet.params`` is in the
       source of control 2's injected class, compiled and never run.
     - Nothing to serialise.
   * - The user's home
     - No harness or tool writes there: nothing project-related under
       ``~`` after the census-only runs' whole-reactor builds, which run the
       real Comet, Percolator and PDV tests.
     - Nothing to serialise.
   * - Processes
     - ``pdv``, ``limelight``, ``provenanceui`` and ``workflow`` end whatever is
       left after each Maven run, matched by their **own** sandbox's path in
       the command line or working directory; no sandbox path is a substring
       of another (``provenance-gate-sandbox`` and
       ``provenance-ui-gate-sandbox`` included). ``e2e`` ends only its own
       children.
     - Nothing to serialise.
   * - ``scripts/**/__pycache__``
     - Present; no ``.pyc`` written since 2026-10-24; most scripts set
       ``PYTHONDONTWRITEBYTECODE``.
     - Nothing to serialise.
   * - Machine load
     - GUI tests wait on the interface; ``ResultsLargeFixtureUiTest`` and
       ``DiskStoreBudgetTest`` hold wall-clock budgets (three to five times
       their measurement); PIT grading moves with load; real Comet uses every
       core. All of these run inside ``tests`` (control C's reactor build) and
       several other controls.
     - Eight cores per job and a cap of four (below), so a concurrent run
       stays under saturation. Nothing was eased.

The job-count rule and the measurement behind it
------------------------------------------------

``taskset`` limits the cores ``nproc`` reports, and each run's own log says
what it decided (the header line, and whether any ``started`` line appears --
the serial path prints none):

.. list-table::
   :header-rows: 1

   * - Run
     - Header line
     - ``started`` lines
   * - ``taskset -c 0-1``
     - ``jobs 1 -- SERIAL, one control after another (computed; 2 core(s) available)``
     - 0
   * - ``taskset -c 0-3``
     - ``jobs 1 -- SERIAL, one control after another (computed; 4 core(s) available)``
     - 0
   * - ``taskset -c 0-7``
     - ``jobs 1 -- SERIAL ... (computed; 8 core(s) available)``
     - 0
   * - ``taskset -c 0-15``
     - ``jobs 2 -- CONCURRENT ... (computed; 16 core(s) available)``
     - 2
   * - ``taskset -c 0-31`` and no ``taskset`` (64)
     - ``jobs 4 -- CONCURRENT ... (computed; 32 / 64 core(s) available)``
     - 2
   * - ``COMETGUI_GATE_JOBS=1`` on 64 cores
     - ``jobs 1 -- SERIAL ... (COMETGUI_GATE_JOBS; 64 core(s) available)``
     - 0
   * - ``--jobs 0``; ``COMETGUI_GATE_JOBS=x``
     - exit 2, "is not a whole number of at least 1 (1 is serial)"
     -

**Serial is today's behaviour.** The original script (``783444e``) and the new
one under ``taskset -c 0-3`` were run with ``--only license --only
workflows``; after masking seconds, ``diff`` shows one added line, the
``jobs`` header, and nothing else.

**Eight cores a job.** Measured with this container's cgroup CPU counter
(``/sys/fs/cgroup/cpu.stat``, 30-second samples), because the host's load
average is not a measure of our work: ``top`` showed this container's 18 tasks
using about one core while the load average stood at 50 and ``vmstat`` showed
72% of the 64 cores busy -- other tenants of the machine. Through
``cometgui-app``'s real-tool tests in control C (the heaviest stretch of the
heaviest control) the run used 1.0 to 3.9 cores per sample, with one momentary
10-second burst of 17.4. Before the other tenants' load arrived, the host's
load average over the other modules' tests was 4.3 to 7.1. Eight cores a job
is about twice the sustained figure.

**A cap of four.** The run cannot end before its longest control. Simulating
the scheduler (``ALL_GATES`` order, the two locks) over the per-control times
the harnesses themselves recorded at the Phase 14 and E12 gate runs (e2e 1356
s, install 413, limelight 1171, params 506, paramui 2251, pdv 913, percolator
830, provenance 240, provenanceui 1252, quality 141, results 690, shell 604,
workflow 512, pipeline about 530, docs 80, traceability 19, the rest under 10)
and the ``tests`` time measured below:

.. list-table:: Simulated wall clock of all 21 controls, ``tests`` at 5236 s
   :header-rows: 1

   * - Jobs
     - 1
     - 2
     - 3
     - 4
     - 5 to 8
   * - Minutes
     - 279
     - 145
     - 99
     - 87
     - 87

At four the run ends when ``tests`` ends; a fifth job has nothing left to
shorten. Over the final ``--only tests`` run, with only that control active,
the cgroup samples gave a mean of 3.1 cores (median 2.3, highest 30-second
sample 13.2) through controls 0 to 6 and a mean of 2.7 (median 2.5, highest
9.9) through control C -- consistent with the figures above.


This assumes no slowdown from running side by side. Measured: in the six-control
batch below, run beside the injection-2 census run, ``quality`` took 164 s
(141 serial), ``shell`` 676 s (604), ``provenance`` 284 s (240) -- 12 to 18%
slower, on a host other tenants were also loading.

Concurrent batches, and failures injected into them
---------------------------------------------------

**Six light controls at ``--jobs 4``** (license, workflows, docs,
traceability, sbom, depscan): exit 0 in 99 s; every count at or above its
floor (5, 23, 1, 8, 8, 16); ``depscan`` started only when ``sbom`` finished,
``traceability`` only when ``docs`` finished; all four shared resources
``unchanged``.

**The brief's batch at the computed count** (``--only docs --only
traceability --only shell --only quality --only provenance --only pipeline``,
``jobs 4 -- CONCURRENT ... (computed; 64 core(s) available)``), run beside
the injection-2 census run: traceability 8, pipeline 25, quality 42, shell
30, provenance 24 -- each at or above its floor -- and the four shared
resources unchanged. ``docs`` FAILED, for a reason of mine and not of
concurrency: this work log's title overline was one character short
(``FIX-BUILD-TIME-worklog.rst:1: WARNING: Title overline too short.``). Fixed;
``docs`` then passed (below).

**Injected into a concurrent batch** (``--jobs 4``):

* ``scripts/verify-license.sh`` forced to exit 9 (an ``exit 9`` after its
  ``set -uo pipefail``; ``sha256sum -c`` FAILED while in, OK after the
  restore): ``finished license exit 9``; graded ``FAIL  license: exited 9; a
  gate did not bite, or the harness could not run``.
* the ``docs`` harness killed with SIGKILL 40 s into its run: ``finished docs
  exit 137``; graded ``FAIL  docs: exited 137``. Summary ``2 control(s)
  passed, 2 failed``; "2 control(s) failed. A gate that cannot be seen to fail
  is not a gate."
* a worker killed with SIGKILL while its harness (``docs``) ran, so no exit
  status is ever written: ``LOST docs its worker ended without an exit
  status``; graded ``FAIL  docs: no exit status was recorded: the control's
  worker died or was killed before its harness ended, so nothing it did can be
  counted``; the run's exit code 1.

A mistake of mine during the first of these, recorded because it touched a
harness: ``pgrep -f`` with the harness's command line matched my own shell,
which it killed, leaving the license injection in place for about a minute
while nothing ran it. It was restored and checked (``scripts/verify-license.sh:
OK``) before anything else was run; the rest of that batch was finished by
exact process ids.

**A defect in the scheduler, found and fixed before the final run.** The
first version kept the workers' exit statuses in ``_build/all-gate-logs/.state``
and emptied it at start, so a second invocation started while one ran -- two
``--only`` runs side by side -- would have deleted the first one's statuses
and turned its controls ``LOST``. Each invocation now has its own ``mktemp``
directory, removed after grading. Rechecked with four light controls at
``--jobs 4`` (exit 0), then exercised by the two invocations below.

The nightly pipeline
--------------------

No workflow calls ``verify-all-gates.sh`` yet (the nightly steps are Phase 15
stubs). Called as it is documented, with no options, on a 2- or 4-core runner
it computes 1 and runs exactly the serial loop.

Final checks after the last change
----------------------------------

All after the last script change (``990b62c`` changed only
``verify-test-gates.sh``; ``build.sh`` and ``verify-all-gates.sh`` were final
from ``e1e702b``):

* ``verify-all-gates.sh --only shell,docs,traceability`` (computed ``jobs 4``,
  beside the first ``--only tests`` run): shell 30 controls in 661 s, docs 1,
  traceability 8; exit 0; shared resources unchanged;
* ``verify-all-gates.sh --only tests``: 49 assertions, exit 0 (above);
* after this log's last edit, ``scripts/ci/docs-build.sh`` and
  ``verify-all-gates.sh --only docs,traceability`` -- recorded in the report to
  tier 1, since this file cannot hold the result of building itself.


Commits
=======

* ``f1bf7ec`` -- fix 1: ``scripts/build.sh`` and
  ``scripts/verify-test-gates.sh``;
* ``e1e702b`` -- fix 2: ``scripts/verify-all-gates.sh`` (with the ``tests``
  floor raised to 49), ``CONTRIBUTING.rst``, ``docs/developer/testing.rst``;
* ``990b62c`` -- control 6 sized from the sandbox's own PIT baseline;
* the commit that adds this log.

Not pushed. ``STATUS.rst``, ``DECISIONS.rst``, ``phases/``,
``specification.rst`` and ``CLAUDE.md`` were not edited. No POM, no product
code, no threshold, floor, timeout or survivor set was lowered or eased.

Rework after tier 1's diff read
===============================

Tier 1 found that the ``--gates-census-root`` refusal compared the resolved
DIR with ``$(pwd -P)`` rather than with the repository. In practice it held,
because ``main`` changes into ROOT before that branch runs: ``cd /tmp && bash
.../scripts/build.sh --gates-census-root <repo>`` was already refused, exit 1.
But it held by accident of ordering. It now compares DIR with ROOT resolved
by ``pwd -P``, and also refuses any DIR inside the repository that is not
strictly under ``_build/``. Control C's refusal assertion now runs the refusal
both from ROOT and from a ``mktemp -d`` directory outside it, and requires
both to be refused.

``--census-only`` would rebuild the whole sandbox (control C), so this was
proved directly instead, with the working tree's ``scripts/build.sh``:

.. list-table::
   :header-rows: 1

   * - DIR, and where it was run from
     - Result
   * - ROOT, from ROOT
     - exit 1, "refuses the repository itself"
   * - ROOT, from ``/tmp``
     - exit 1, "refuses the repository itself"
   * - a symlink to ROOT, from ``/tmp``
     - exit 1, "refuses the repository itself"
   * - ``ROOT/docs``, from ``/tmp``
     - exit 1, "refuses docs: a directory inside the repository is accepted
       only under _build/"
   * - ``ROOT/_build`` itself, from ``/tmp``
     - exit 1, "refuses _build: ..."
   * - ``ROOT/_build/test-gate-sandbox``, from ``/tmp``
     - accepted; the census ran over it
   * - a directory outside the repository, from ``/tmp``
     - accepted; the census ran over it

The no-option path was compared again with ``783444e``'s ``build.sh --only
gates`` under the stub ``mvn``: identical, 361 lines, exit 0. ``--only shell``
was run after the change: 30 controls in 609 s, exit 0. ``docs-build.sh`` passed after this section was written.

The e2e red at tier 1's sign-off run (``c9d037c``)
==================================================

**What failed.** At four jobs, ``e2e`` exited 1 with 41 passed and 9 failed.
The 9 are the three dirty controls 3i, 5q and 5p, three assertions each. Each
failed at the same place: the Percolator 3.07.1 install, ``[Downloading: 0
bytes, Failed: 946,303 of 946,303 bytes]``, about 2 s in (3i: "Percolator could
not be installed, so it was not rerun"). The H-controls' ``HARNESS FAILURE``
lines are their expected output; H1 to H6 all PASSED. The window was 09:05 to
09:07:50. ``5b-dirty`` (09:07:50 to 09:09:48) installed the same Percolator and
went red only at its own step 27. The clean A and B runs (08:48 to 08:58) also
installed it.

**What the evidence says about the failing step.** ``InstallPipeline`` reports
``FAILED`` with the bytes it counted, so all 946,303 bytes arrived. One of the
steps after the transfer threw: the download's own check, the SHA-256 check,
ZIP extraction, layout check, fix-ups, probe, move or record. Which one is
not recorded anywhere. ``ManagedToolManager`` swallows the exception on the
install thread, and ``ToolRowViewModel`` says by design that "a failed install
carries no reason". Nothing reached the test's output.

**Ruled out:**

* *The upstream file.* Fetched afterwards from the pinned URL: 946,303 bytes,
  SHA-256 ``4d0e94af...``, equal to the manifest pin and to
  ``scratch/phase05/artefacts``.
* *A probe timeout.* ``PROBE_TIMEOUT`` is 120 s.
* *Analytics or network calls in the probe.* 3.07.1 accepts
  ``--no-analytics``, and the probe passes it from its first run.
* *Percolator temporary files.* The binary names them randomly
  (``%%%%-%%%%-%%%%-%converters-tmp``).
* *The probe's workspace.* It is created with ``createTempDirectory`` and only
  that directory is deleted.
* *The tool cache and the install lock.* Both live in each test's fresh home,
  under a JUnit temporary directory.
* *Disk.* 1.3 TB free.
* *Machine-wide process killing.* No Java code enumerates all processes, and
  every harness reaper is scoped to its own sandbox path or children.
* *Shared read-only resources.* Unchanged in tier 1's run and in every run
  here.

**Reproduction attempts.** All runs were in ``_build/e2e-gate-sandbox``,
checked pristine against its ``pristine-sources.sha256``. JFR recorded every
thrown exception in every JVM (``JDK_JAVA_OPTIONS``, no code change), so a
failure would have carried its reason.

* ``TierACanonicalScenarioTest`` alone: 23/23.
* Ten consecutive runs of it beside the ``tests`` sandbox's ``cometgui-app`` GUI
  tests. Those are the classes that were running at 09:05 to 09:08
  (``PercolatorCancelUiTest``, ``ResultsAccessibilityUiTest``,
  ``ResultsCountsUiTest``, ``ResultsFiltersUiTest``, ...), and they ran in the
  same order and overlapping the loop (169 tests, 0 failures): **10/10
  green**.
* Nine consecutive runs of ``TierACanonicalScenarioTest`` and
  ``TierANoXmlScenarioTest`` beside the ``provenanceui`` control
  (``--only provenanceui``, PASS 96) and the GUI tests: **9/9 green**.

That is 28 Percolator 3.07.1 installs from GitHub beside the controls that were
running at the failure, with none failing.

* **The targeted concurrent rerun**: ``verify-all-gates.sh --jobs 3 --only
  e2e,limelight,provenanceui``, the sign-off's companions, all three started
  together. ``e2e: 50 controls`` (3i red at step 5, 5q at the boundary step,
  5p at step 28, 5b at step 27, each its own), ``limelight: 130``,
  ``provenanceui: 96``, exit 0, shared resources unchanged.

**Conclusion.** No concurrency cause was found, and the failure did not
reproduce under the same concurrency. The pattern fits a transient outside
the machine better than a collision on it: one artefact fetched from GitHub
failed three times in three minutes, while the Comet artefact from the same
host installed fine in the same runs, and the next install of the same
Percolator succeeded. That is an inference, not a proof. The reason was
discarded, and proving it needs the install's exception recorded, a product
(or test-support) change this brief does not allow. **No lock, harness or
timeout was changed:** with no cause shown, a lock would be a guess. Escalated
to tier 1, with the next item.

**A separate concurrency hazard found while looking.**
``PercolatorCapabilityProbeTest.theWorkspaceIsCleanedUp`` (``cometgui-tools``)
counts every ``cometgui-percolator-probe-*`` directory in the shared
``java.io.tmpdir`` before and after its own probes. Any other JVM probing
Percolator at the same moment changes that count: the ``tests`` control's
control C, ``workflow``, ``percolator``, ``limelight``, ``provenanceui`` and
``e2e`` all can. It has not gone red yet. 21 such directories have been left
in ``/tmp`` since 07:05:47 (tier 1's ``build.sh``), which also means the
count's baseline is not empty. Fixing it is a test change: escalated, not
made.

For tier 1
==========

* The full suite at sign-off is the first time all twenty-one controls run side
  by side (four at a time on this machine). If any control goes red or
  flaky there that is green serially, the brief's rule applies: find the root
  cause or lower ``JOBS_CAP``; ``--jobs 1`` reproduces the serial run exactly.
* This host is shared: on 2026-11-05 other tenants held 30 to 50 of its 64
  cores for hours. ``nproc`` cannot see that. A concurrent run on a busy day
  competes with them as well as with itself.
* ``jacoco_report_of`` still falls back to the working tree's ``jacoco.xml``
  to size control 5 (see above). Not changed here; a decision for tier 1.
* Run ``scripts/build.sh`` before the gate suite, as always: the working
  tree's ``target/`` is from 2026-10-24 with classes recompiled since, and
  ``cometgui-domain``'s ``pit-reports`` holds the E12 repair's partial run.
  Nothing in the ``tests`` control reads either any more.
