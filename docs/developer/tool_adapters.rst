.. _dev-tool-adapters:

=============
Tool adapters
=============

.. note::

   **The process-service section below is written and current** (Phase 03).
   The per-tool adapter sections -- Comet, Percolator, the Limelight converter
   and PDV -- are still owned by phases 08, 09, 11 and 12 and are not written
   yet. Everything a tool adapter does with a subprocess, it does through the
   service described here.

.. contents:: Contents
   :depth: 2
   :local:

The process service
===================

One place where processes are created, observed, cancelled and logged. Every
tool adapter depends on it being correct under adversarial conditions, because
a scientific tool that hangs, floods stdout, spawns children or dies half-way
is the normal case rather than the exception.

It lives in ``org.cometgui.tools.process`` (Maven module ``cometgui-process``),
and it is **the only package in the product permitted to construct a**
``ProcessBuilder``. An ArchUnit rule enforces that; see
:ref:`dev-tool-adapters-archunit`.

What a caller sees
------------------

Two layers, and most callers want the second.

**The port**, ``org.cometgui.domain.ports.ProcessRunner``, implemented by
``ProcessService``. It launches an argument array in an explicit working
directory with an explicitly constructed environment, streams the two output
streams independently to a ``ProcessListener``, and returns a handle. It knows
nothing about stages, log files or the console.

**The stage layer**, ``StageRunner``, which is what a workflow stage uses:

.. code-block:: java

   StageRunner runner =
           new StageRunner(processService, clock, redactor, sink, logDirectory);

   RunningStage stage = runner.start(WorkflowStage.COMET, command);
   // ... or with a timeout, which is off unless one is given:
   RunningStage stage = runner.start(WorkflowStage.COMET, command, Duration.ofHours(6));

   stage.requestCancellation();          // safe from any thread, returns at once
   StageOutcome outcome = stage.awaitOutcome();

``RunningStage`` also offers ``stage()``, ``logFile()``, ``isAlive()`` and
``outcomeIfFinished()``. It deliberately exposes **no** ``CompletableFuture``:
a future handed out is a future a caller can complete.

``StageOutcome`` is an immutable record carrying the stage, the **redacted**
display command, the log file actually written, the exit code, the start and
end instants and the duration, the line count from each stream, whether
cancellation was requested, whether it timed out, and how many log writes
failed. **It holds no environment at all** -- the way to be sure an
environment value is never printed is not to hold one.

Argument arrays, never a command string (``R-PROC-02``)
--------------------------------------------------------

A FASTA file named ``my proteins;rm -rf.fasta`` is an ordinary filename and an
execution vulnerability the moment anything joins arguments with spaces and
hands the result to a shell. ``ToolCommand`` is the boundary at which that
becomes impossible: it validates the argument array on construction, copies it,
and offers no accessor that yields a "command line".

What it does offer is ``displayString()``, which renders the argv the way a
JSON array is written -- ``["/opt/comet", "-P", "comet.params"]`` -- with every
element quoted and with backslashes, quotes, tabs, newlines and control
characters escaped. Two properties follow, and both are the point:

* argument boundaries survive the rendering, so an argument containing a space
  is visibly one argument rather than two;
* nothing a shell reacts to can escape the quotes it is printed inside, so
  copying the text into a terminal cannot run something the application did not
  run.

The explicit working directory and environment (``R-PROC-04``)
---------------------------------------------------------------

Every process is started in ``ToolCommand.workingDirectory()``, which
``ProcessService`` checks **before** the launch so that the diagnostic names the
directory. Left to ``ProcessBuilder``, a missing directory produces
``error=2, No such file or directory`` naming the *executable*, which sends the
reader looking for the wrong problem.

**The environment is constructed, never inherited.**
``ProcessBuilder.environment()`` starts as a copy of the JVM's environment; the
service **clears it** and puts back exactly ``ToolCommand.environment()``.
A tool therefore sees no ``PATH``, no ``HOME``, no ``TMPDIR``, no ``LANG`` and,
on Windows, no ``SystemRoot`` unless the caller put it there.

That is deliberate and it is what makes a run reproducible: a search whose
result depends on which shell launched the application is a search that cannot
be repeated, and the provenance record would describe a run nobody can
reconstruct. **A caller needing an inherited variable names it in the**
``ToolCommand``, where it is recorded. It is measured, not assumed: with an
empty environment the launched process reports ``envcount 0``.

.. warning::

   This is the entry in :ref:`dev-tool-adapters-platform` most likely to need a
   product change rather than only a test. Many Windows programs will not start
   without ``SystemRoot``, and some need ``PATH`` and ``TEMP``.

Standard input is closed, not inherited
----------------------------------------

No tool in this workflow reads standard input. Redirecting it to ``INHERIT``
would let a tool that *did* read it block on the launching terminal --
invisibly, forever, inside a desktop application that has no terminal. The
default pipe is kept and closed immediately after the start, so such a tool
sees end of file at once instead of hanging.

Streaming, decoding and the line splitter
------------------------------------------

One daemon thread per stream, plus one that completes the run. Callbacks arrive
on those threads and **never** on the JavaFX application thread; a listener that
touches the user interface hops threads itself. The two streams are never
merged: a merged stream cannot tell a user which of the two a message came
from, and Comet writes progress to one and diagnostics to the other.

Output is decoded with a ``CharsetDecoder`` -- UTF-8 by default -- configured to
**replace** malformed and unmappable input rather than throw. One stray byte
from a tool must not silence the rest of a run's log.

Lines are cut by ``LineSplitter`` rather than by ``BufferedReader.readLine()``,
because two requirements need behaviour ``readLine`` cannot give:

* ``\n``, ``\r\n`` **and a lone** ``\r`` are all terminators, because Comet and
  Percolator draw progress bars with a carriage return. A ``\r`` ending one read
  and followed by ``\n`` at the start of the next produces one line break, not
  two;
* a **maximum line length** of 65,536 characters, enforced by emitting the
  accumulated characters and continuing. Without it a tool writing hundreds of
  megabytes with no newline exhausts the heap through a single
  ``StringBuilder`` -- which is precisely the failure ``R-PROC-03`` exists to
  prevent.

The final unterminated segment at end of stream is a line and is emitted; an
empty line between two terminators is a line and is emitted; no line ever
carries its terminator.

A listener that throws cannot kill a pump or lose the rest of a run's log:
``GuardedListener`` catches, counts and describes such failures instead.

Bounded in memory, complete on disk (``R-PROC-03``)
====================================================

``R-PROC-03`` has two halves and they are in two places.

**In memory**, ``org.cometgui.domain.log.BoundedMessageLog`` retains the newest
*N* messages -- ``DEFAULT_CAPACITY`` is 10,000 -- discards the oldest, and
reports how many it discarded, so a console can say "12,431 earlier lines
discarded" instead of presenting a truncated log as if it were the whole one.

**On disk**, the process service writes every line to that stage's own log file
**as it arrives**. That is what makes discarding acceptable in memory: nothing
is lost, it is simply no longer resident.

The stage log file
------------------

One file per stage, under the log directory the runner is given. The stage
identifier is validated against a safe-token pattern **before** it becomes a
file name.

::

    2026-08-31T19:04:51.250Z [cometgui] stage comet started in /runs/2026-08-31/work
    2026-08-31T19:04:51.250Z [cometgui] command ["/opt/comet", "-P", "[REDACTED]"]
    2026-08-31T19:04:51.312Z [stdout] Comet version 2024.01
    2026-08-31T19:04:51.480Z [stderr] Search 12% complete
    2026-08-31T19:06:03.007Z [cometgui] stage comet ended: exit code 0 after PT1M11.757S

* A fixed-width 24-character timestamp **from the injected clock**, always UTC,
  truncated to milliseconds -- a run's log is read later, elsewhere, beside a
  UTC provenance record.
* ``stdout`` and ``stderr`` are the same width, so tool text starts at one
  column. ``cometgui`` marks the lines the service itself writes, and **the tag
  is chosen by the code rather than taken from the text**, so a tool line can
  never impersonate one.
* The terminator is always ``\n``, never ``System.lineSeparator()``.

**Every line is flushed.** A run that was killed must leave the log of what
happened before it died, which is the run whose log matters most.

**Both pumps write through one private monitor**, so a line can never be
interleaved into the middle of another. Each stream's own lines keep their
order; the order *between* the two streams is genuinely nondeterministic and is
asserted nowhere -- asserting it would be a flaky test, which is worse than no
test.

**A re-run does not destroy the first attempt.** The first attempt is
``comet.log``, a re-run takes ``comet.1.log``, and so on to a hundred, after
which the service refuses with an ``IOException`` naming the directory rather
than overwriting. The free name is chosen by ``CREATE_NEW`` and catching
``FileAlreadyExistsException``, not by check-then-open, so two stages starting
at once cannot land on the same file. The outcome reports the file actually
written.

**The disk is the record and the console is a view.** Inside the recorder the
order is: read the clock once, redact once, count, write to disk, then append to
the console. A sink that throws cannot cost the disk a line.

Where the console lives, and why the service never holds one
-------------------------------------------------------------

The process service **never receives a** ``BoundedMessageLog``. It accepts
``RunMessageSink``, a one-method append-only interface taking a
``LogMessage``; a caller wires ``boundedMessageLog::append``.

Phase 02 left the log injected into ``CometGuiApplication`` rather than held by
the composition root, because a composition root handing out a mutable shared
object is publishing mutable state -- SpotBugs reports it as ``EI_EXPOSE_REP``
and is right. Narrowing the reference rather than moving the object settles
that:

* **nothing new is published** -- what crosses the boundary is a method
  reference, not the log, so the finding is never created;
* **the capability is one-directional** -- the process service can append, and
  cannot read the console, ``clear()`` it, or learn its capacity or discard
  count. A tool adapter that could empty the user's console is a capability
  nobody asked for;
* **thread safety is already paid for** -- ``BoundedMessageLog`` synchronises
  every method body on a private monitor, which is what makes ``log::append``
  safe from two pump threads while the JavaFX thread paints.

Cancellation and descendant termination
=======================================

``RunningStage.requestCancellation()`` and ``RunningProcess.requestCancellation()``
are requests, not guarantees, and both **return without waiting**. They are
idempotent. A caller learns that it worked from the outcome arriving.

Three rules, each of which was established by measurement rather than assumed:

**The descendants are snapshotted before anything is destroyed.**
``Process.descendants()`` is a snapshot of the tree as it is at the moment of
the call. Once the parent dies its children are reparented away from it and the
same call returns nothing, so the snapshot must be taken first or the children
become invisible.

**Descendants die first, deepest first, and the parent last.** If the parent
goes first its child is reparented to PID 1; where PID 1 is not an init that
reaps orphans, a child killed after that becomes a permanent zombie --
``/proc/<pid>`` still exists, so ``ProcessHandle.isAlive()`` stays ``true`` for
ever and ``ProcessHandle.onExit()`` never completes. Depth is computed by
walking each descendant's ``parent()`` chain, because the iteration order of
``descendants()`` is unspecified.

**Termination goes through** ``ProcessHandle.destroy()``, **never**
``Process.destroy()``. On Linux the latter closes the process's standard input,
output and error before signalling it; the pumps then fail with
``IOException: Stream closed`` and the last thing the tool said before it was
cancelled -- usually the interesting part -- is lost. Measured: 82 lines
delivered where the correct implementation delivers 1,296. Worse, the tool dies
of a broken pipe and reports exit code ``1``, so a cancelled run would be shown
to the user as a tool crash.

After a configurable grace -- five seconds by default -- anything still alive is
killed forcibly. The wait is driven by ``ProcessHandle.onExit()`` futures, never
by a sleep or a poll.

Timeouts
--------

**Off unless one is given.** ``StageRunner.start(stage, command)`` has none;
``start(stage, command, timeout)`` has one. An expiry cancels through the same
path, so descendants die and a process ignoring the polite terminate is killed,
and the outcome distinguishes a timeout from a person cancelling.

The expiry is scheduled with ``CompletableFuture.orTimeout`` on the JDK's
delayed executor -- no thread is parked and nothing polls a clock. It is
measured in **real time** rather than from the injected clock, deliberately: a
fixed clock never advances, so a clock-driven timeout would never fire.

Secret redaction (``R-SEC-03``)
================================

The rules live in ``org.cometgui.domain.secrets`` and are shared with the
provenance recorder -- the keyword list, the registry of literal values and the
pattern rules. **A rule added anywhere else re-creates the defect they were
merged to fix:** the two were briefly implemented twice in parallel and had
diverged by a keyword within hours, which would have meant a value redacted in
the console log and not in the provenance record.

``ProcessRedactor`` holds no rules. It contributes two things that are specific
to running a process:

* **Arguments are redacted before they are escaped, never after.**
  ``displayString()`` escapes quotes and backslashes, so a token containing a
  ``"`` arrives in the rendered text as ``\"`` and a literal search for the
  registered value no longer matches it -- the secret would then be printed,
  escaped, in full.
* **A run with no registered secret pays nothing per line.** Comet, Percolator
  and PDV take no credential; the Limelight upload is the only tool that will
  ever register one. With an empty registry the per-line path returns its
  argument by reference and scans nothing, so a stage emitting 500 MB does not
  pay for a feature no tool in the workflow uses.

  The price of that, stated rather than hidden: with nothing registered, a
  console line does not get the pattern rules either. The two renderings
  ``R-SEC-03`` actually names -- the display command and the captured
  environment -- get the full rule set **unconditionally**, because each is
  produced once per stage rather than once per line.

.. _dev-tool-adapters-archunit:

The rule that keeps this the only door
=======================================

``LayeringRulesTest.processCreationIsConfinedToTheProcessService`` checks
``ProcessCreationRule.CONFINED_TO_THE_PROCESS_SERVICE``: no class outside
``org.cometgui.tools.process..`` may depend on ``ProcessBuilder``, call
``Runtime.exec``, or depend on anything named
``java.lang.ProcessBuilder`` or one of its nested types.

The rule is built **once**, as a constant, and both the test that grades the
product and the test that grades deliberately illegal fixtures check that same
object. A negative control written against a *copy* of a rule proves the copy
has teeth and says nothing about the rule the build runs.

The nested-type clause exists because a ``ProcessBuilder.Redirect`` is not
assignable to a ``ProcessBuilder``, so an assignability rule alone accepted a
class holding one -- observed, not assumed. Deciding where a tool's output is
redirected is process-service work whether or not the class holding the decision
ever calls a constructor.

Testing a process service without the real tools
=================================================

``cometgui-process/src/test/resources/fakes/FakeTool.java`` is one Java program
that behaves like a badly-behaved scientific tool on demand: stdout/stderr
interleaving, chosen exit codes, files written or promised and not written,
malformed output, a partial file followed by failure, delayed output creation,
floods of arbitrary size and line length, hanging, hanging with a real child
process, hanging while ignoring a polite terminate, argv and environment echo,
Unicode, invalid UTF-8, an unterminated last line and CRLF.

It is **one program rather than a matrix of shell scripts** on purpose: a
``.sh``/``.cmd`` pair per scenario is two implementations of every fake, and the
Windows half would never be executed here. It is compiled in process by
``javax.tools`` once per JVM and launched through the JDK's own ``java`` binary,
so it needs no POSIX shell and no executable bit.

Its hanging scenarios halt themselves after 300 seconds with exit code 71.
That is not a convenience: a mutation-testing minion killed at its own timeout
strands a hanging fake, which a non-reaping PID 1 never collects. The code 71 is
one no signal produces -- a cancelled process exits 143 and a killed one 137 --
and the cancellation tests assert those numbers exactly, so a test that only
passed because the watchdog fired would fail instead.

.. _dev-tool-adapters-platform:

What has only ever run on Linux
================================

Everything above was built and measured on Linux/amd64. Process trees, signal
delivery and orphan reparenting are the most platform-divergent things in this
project, and the following have **never executed** on Windows or macOS. Each is
a specification for a later platform twin rather than a known defect.

.. list-table::
   :header-rows: 1
   :widths: 30 70

   * - Divergence point
     - What a Windows or macOS twin must prove

   * - Terminate versus kill
     - Whether ``ProcessHandle.destroy()`` and ``destroyForcibly()`` are
       distinguishable at all. If they are not, the termination grace and the
       escalation are dead code there and this page must say so.

   * - Exit codes 143 and 137
     - What a cancelled and a killed process actually report, and whether either
       can be told from an ordinary tool failure. A cancelled run that looks
       like a crash misreports the run to the user.

   * - Orphan reparenting
     - That descendants-first ordering is still correct, or is unnecessary, and
       that ``isAlive()`` goes false promptly after a kill.

   * - The descendant snapshot
     - That ``descendants()`` sees a grandchild, and that a snapshot taken
       before the parent is signalled names the whole tree.

   * - ``Process.destroy()`` closing the streams
     - Whether it does so there too. If not, using the handle is merely harmless
       rather than load-bearing, and this page must stop claiming otherwise.

   * - The cleared environment
     - That a real tool starts at all with a constructed environment, and what
       the minimum variable set is.

   * - Non-ASCII paths
     - That they work, and what the packaged application must set. On Linux this
       is proved only because the module forks its tests with a UTF-8 locale;
       the system property ``sun.jnu.encoding`` cannot be used for it, because
       the JVM resolves the value from the environment before system properties
       apply.

   * - Long paths
     - That a run directory nested as deeply as a real run nests still works.
