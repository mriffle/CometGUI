=====================================================
PHASE-04 handoff -- Hashing and Provenance Core
=====================================================

:Phase: 04
:Phase orchestrator: session 04 (units 1-10), then session 05's resumption
   orchestrator (units 9-13 and the sign-offs)
:Outcome: **PARTIAL** -- all seven gate items evidenced on POSIX; the residue
   is named at :ref:`p04-divergence` and is Windows behaviour that has never
   run anywhere
:Last updated: 2026-09-02

.. note::

   **THE PHASE IS FINISHED AND EVERY NUMBER BELOW WAS RE-TAKEN FROM A QUIET
   TREE.** It was paused part-built on 2026-08-31 with units 1-8 signed off, 9
   and 10 landed but unsigned, and 11-13 not started. It resumed on 2026-09-01
   with no other phase live in the working tree, which is what makes the
   figures mean anything. The paused document's warning that "every number in
   this document was taken from a noisy tree" has been discharged: they were
   all re-taken, and where a re-taken number differs from the paused one, the
   difference is recorded rather than overwritten.

Where the numbers come from
===========================

One clean end-to-end run at ``9ddb3e3``, with ``git status --short`` empty and
no other agent in the tree, in the order :ref:`p04-first-thing` prescribes:
``mvn -pl cometgui-domain install`` first, because PIT resolves from
``_build/m2repo`` and not from the reactor, then one ``verify``, then one
mutation run, then the class-population census over both modules.

.. list-table::
   :header-rows: 1
   :widths: 30 35 35

   * - Measurement
     - ``cometgui-provenance``
     - ``cometgui-domain``

   * - Tests
     - 669 run, 0 failures, 0 errors, 2 skipped
     - 359 run, 0 failures, 0 errors, 0 skipped

   * - JaCoCo line / branch
     - **100.00%** (1827/1827) / **100.00%** (623/623)
     - **100.00%** (432/432) / **100.00%** (188/188)

   * - PIT
     - **774 mutations, 771 KILLED, 0 SURVIVED, 3 TIMED_OUT, 0 NO_COVERAGE**
     - **204 mutations, 204 KILLED, 0 SURVIVED**

   * - Class-population census
     - 37 compiled, **37 in jacoco.xml**, 30 with PIT mutations
     - 25 compiled, **25 in jacoco.xml**, 15 with PIT mutations

   * - Static analysis
     - ``BugInstance size is 0``, ``0 Checkstyle violations``
     - the same

**Read the census before the percentages, and it is clean.** The seven
``cometgui-provenance`` classes with no PIT mutations were judged rather than
skimmed: an enum of constants, four interfaces, an exception with constructors
only, a two-component record whose validation delegates, and a constants
holder. Not one is a class whose tests failed to compile -- which is the
condition that made the census necessary. ``ManifestReader``, the class that
was compiled, carried 79 ``NO_COVERAGE`` mutations and was **absent from the
coverage sample** on the day the census was written, is back in the population.

**The aggregate falsifiability suite**, ``bash scripts/verify-all-gates.sh``:
**11 controls passed, 0 failed, in 1917 seconds (31m57s)**, up from 10 controls
in 1702 seconds at the start of the session. The eleventh is this phase's own
new harness (:ref:`p04-harness`), graded at 24 controls. No floor was lowered
and no control was removed.

The paused document's central warning, kept because it is still true
--------------------------------------------------------------------

A coverage or mutation figure taken while sibling units are mid-landing is
uninterpretable, and it can err in **either** direction. Low is visible: one
unit saw its ``json`` package read 38% because another unit's tests had not
compiled yet. High is invisible: a class whose test does not compile can be
absent from the report altogether, and an absent class does not drag an average
down -- it silently leaves the sample. That happened here, was caught, and is
the reason :ref:`p04-census` exists. **The remedy is not to be careful; it is
to run the census beside the numbers on every build.**

.. contents:: Contents
   :depth: 2
   :local:

.. _p04-divergence:

Platform divergence: where this phase's code takes a different path
===================================================================

**This is not the skip list, and the difference decides the grade.** A test
that could not run on a platform is a *testing gap*: the same code runs
everywhere and we simply could not execute it. A branch that *behaves
differently* on a platform where it has never run is *unverified behaviour*.
Phase 02 passed with the first; this phase has the second, and the honest
outcome is ``PARTIAL`` with the residue named precisely rather than ``PASSED``
with a caveat in prose.

The list below was produced by searching production code deliberately for
platform-conditional constructs, not by reading back the ``@DisabledOnOs``
annotations. It is in two tiers, because they are not equally unverified.

Tier A -- the divergent branch IS executed here, by a faithful stand-in
-----------------------------------------------------------------------

These are not unexecuted code. Each has a substitute on this platform that
takes the same branch, so the logic is exercised and mutation-tested. What is
unverified is that **Windows really is the case the substitute stands for**.

.. list-table::
   :header-rows: 1
   :widths: 20 40 40

   * - Divergence
     - How it is exercised here
     - What a Windows twin must prove

   * - **The hash cache's attribute source.**
       ``FileFingerprint.of`` asks the file system whether it publishes the
       ``unix`` view. With it, the key carries ``fileKey`` and ``unix:ctime``.
       Without it, both are ``null``, ``tamperEvident()`` is false, and the
       cache **stores nothing at all**. That is a different algorithm on
       Windows, not the same algorithm untested. (Gate item 3.)
     - Through a **zip file system**, which is a faithful stand-in: measured on
       this host, ``zipfs`` publishes ``[zip, basic]`` with no ``unix`` view and
       returns ``fileKey() == null``, while the default file system publishes
       ``[owner, dos, basic, posix, user, unix]``. ``FileFingerprintTest`` and
       ``CachingHashServiceTest`` both drive real files inside a real zip.
     - That a Windows default file system likewise omits the ``unix`` view and
       returns a null ``fileKey``, so it takes the basic branch; that the cache
       is then genuinely inert rather than serving an entry it cannot validate;
       and that a full run therefore rehashes every input every time, which is
       a **performance** characteristic Phase 15 should measure on a
       multi-gigabyte spectrum file rather than assume is tolerable.

   * - **Directory fsync after the rename.**
       ``FileSystemDurability.syncDirectory`` opens the containing directory as
       a channel and forces it. Opening a directory as a channel is not
       possible on Windows, so ``syncDirectoryIfPossible`` catches the
       ``IOException`` and returns ``false``: the write still succeeds, because
       the data is already renamed into place, but the *rename* is not made
       durable against a power loss. (Gate items 4 and 5.)
     - By substituting a ``Durability`` whose ``syncDirectory`` throws, and
       asserting the target is still present and correct. Two tests are
       ``@EnabledOnOs({LINUX, MAC})`` because they need a real directory
       channel.
     - That opening a directory channel really does fail on Windows rather than
       succeeding and silently doing nothing -- those two have identical
       observable behaviour in the success case and opposite durability
       guarantees -- and what, if anything, Windows offers instead.

Tier B -- divergent behaviour with no execution anywhere
--------------------------------------------------------

These have never run in any form. They are the residue that caps the grade.

.. list-table::
   :header-rows: 1
   :widths: 24 76

   * - Divergence
     - What a Windows twin must prove

   * - **``ATOMIC_MOVE`` under contention.** ``FileSystemDurability.moveIntoPlace``
       demands ``ATOMIC_MOVE`` with ``REPLACE_EXISTING``. On POSIX this replaces
       an open file happily, and gate item 5 is proved here by a concurrent
       reader observing only whole documents. On Windows a rename over a file
       another process holds open can fail with ``AccessDeniedException`` --
       and the Provenance UI (Phase 13), a virus scanner or a synchronisation
       client are all exactly such processes.
     - **This is the most important one in the list.** That finalising
       ``provenance.json`` while a reader holds it open either succeeds or
       fails cleanly, and in particular that it can never leave the target
       truncated or absent. Gate item 5's promise -- "an interrupted finalise
       never leaves a truncated ``provenance.json``" -- is proved on POSIX and
       is **unproven on Windows**. If Windows cannot replace an open file, the
       product needs a retry or a different finalisation strategy there, which
       is a design change, not a test.

   * - **Absolute-path validation.** ``ManifestChecks.requireAbsolute`` and
       ``ToolCommand``'s working directory both use ``Path.isAbsolute()``.
       ``/var/cometgui/runs/...`` is absolute here and **not** absolute on
       Windows, so records that are valid on this platform are rejected there.
       This is why twenty tests carry ``@DisabledOnOs(WINDOWS)``.
     - That a real Windows run builds ``C:\...`` paths that the records accept,
       and that the pinned JSON and RST documents have Windows twins with
       Windows paths. The repair is a second pinned document, never a
       relaxation of the absolute-path rule.

   * - **``Path.toRealPath()`` as the cache key.** ``CachingHashService``
       canonicalises through ``toRealPath``. On Windows that also resolves
       case-insensitivity and short 8.3 names, so two spellings of one file
       canonicalise together -- which is correct, and has never executed.
     - That two spellings of one path share a cache entry and that a rename
       which differs only in case invalidates it.

   * - **The JVM cannot represent a non-ASCII path** when ``sun.jnu.encoding``
       is not UTF-8. Not Windows-specific, and covered in full at
       :ref:`p04-encoding`, including the finding that the obvious
       ``-Dsun.jnu.encoding=UTF-8`` remedy is inert.
     - That the packaged launcher starts the JVM in a UTF-8 locale on every
       platform, and that an accented data directory works end to end.

What is NOT on this list, deliberately
---------------------------------------

Line endings, number formatting, digest computation, secret redaction, JSON
and RST generation and the event-log line format all run **identical code on
every platform** -- ``\n`` is written explicitly, numbers go through
``Long.toString``, hex through ``HexFormat``, and every locale-sensitive path
is pinned by a test under Turkish, German and Thai-digit locales. Those are
testing gaps at worst, not divergences, and they do not belong in this
document.

.. _p04-unit-state:

Unit state, one line each
=========================

Thirteen units were planned; the decomposition and every sign-off entry with
its evidence is in ``handoffs/PHASE-04-worklog.rst``. "Signed off" means the
phase orchestrator read the diff, ran the gate itself, **and injected a defect
the unit had not tried, watched it fail, and reverted it** -- never that an
agent reported success.

.. list-table::
   :header-rows: 1
   :widths: 5 26 16 53

   * - #
     - Unit
     - State
     - What exists

   * - 1
     - Streaming single-pass hasher
     - **Signed off**
     - ``185fbc1``. Rejected once first: it proved "one pass" through a
       package-private seam the production path could bypass. Reworked to make
       the *open* countable.

   * - 2
     - Atomic, durable file writing
     - **Signed off**
     - ``a6f0b48``. ``org.cometgui.provenance.io``.

   * - 3
     - Secret rule set
     - **Signed off, then MOVED**
     - ``2b4575d``, then relocated to ``cometgui-domain`` at ``b0e7122`` and
       the old copy deleted at ``464378e``. See :ref:`p04-where-secrets`.

   * - 4
     - Manifest record types
     - **Signed off**
     - ``fea6bd6``. ``org.cometgui.provenance.manifest``.

   * - 5
     - Input-hash cache
     - **Signed off**
     - ``7e269dc``. Keys on a fifth attribute beyond ``R-PROV-02``'s four.

   * - 6
     - 2 GB bounded-heap proof
     - **Signed off**
     - ``3119189``, ``6cbcad4``. Ships a permanent negative control.

   * - 7
     - Canonical JSON writer
     - **Signed off**
     - ``b1afb68``, reworked at ``e10274c`` to record ``durationMillis``.

   * - 8
     - Appendable event log
     - **Signed off**
     - ``3e552b4``, reworked at ``8f00cae`` and ``4c33b79``.

   * - 9
     - Strict JSON reader and round-trip suite
     - **Signed off**
     - ``9639b77``, signed off at ``90443ed``. Landed unsigned at the pause;
       the resumption read the diff, re-ran the gate, injected two defects,
       answered the unit's five design questions, closed the six PIT survivors
       and repaired one defect the unit had not reported. See
       :ref:`p04-resumption-work`.

   * - 10
     - ``provenance.rst`` report
     - **Signed off**
     - ``9317abc``, signed off at ``670b886``. Same shape: diff read, gate
       re-run, two defects injected, and the Sphinx gate over the generated
       sample run **both ways** for the first time.

   * - 11
     - Seeded-secret grep over generated artefacts
     - **Signed off**
     - ``df7fbac``, signed off at ``d8d2f0d``.
       ``SeededSecretArtefactSweepTest`` writes all three artefacts into one
       run directory, walks it, and greps every file on disk twice -- as UTF-8
       text and as raw bytes. Gate item 6 is now whole.

   * - 12
     - Documentation
     - **Signed off**
     - ``31be8c1``, signed off at ``aefe40d``.
       ``docs/reference/provenance_format.rst`` (the versioned schema),
       ``docs/developer/provenance_schema.rst`` (the model), a mechanical drift
       check between the page and the writer, and five ``AC-PRV`` evidence
       entries.

   * - 13
     - Gate enablement and falsifiability
     - **Done**
     - ``9ddb3e3``. The module's mutation switch is on and proved live;
       ``scripts/verify-provenance-gates.sh`` is new and registered as the
       eleventh control of ``verify-all-gates.sh``; the final clean run and the
       aggregate suite were both made.

.. _p04-resumption-work:

Units 9 and 10, and what signing them off actually found
---------------------------------------------------------

At the pause both were ``LANDED, NOT SIGNED OFF``: committed green by their
authors in the final minutes, with **no diff read, no gate re-run and no defect
injected into either**. The resumption did all three for each, and the two
things it found are worth more than the sign-offs.

**The round-trip suite's blind spot, proved rather than argued.** The paused
handoff claimed that a round trip cannot catch a reader that reads the wrong
key, and that only the two hand-typed suites can. That is a claim about a
suite, so it was tested on the suite: ``"formatLocale"`` was renamed to
``"format_locale"`` in **both** ``ManifestWriter`` and ``ManifestReader`` -- a
writer and a reader agreeing on a wrong name, which is the only shape a round
trip is blind to. ``ManifestRoundTripTest`` stayed **entirely green**, 14 tests
including all two hundred generated manifests and the byte-stability check,
while every ``provenance.json`` the build could produce carried a member name
no other reader would understand. ``ManifestWriterTest`` and
``ManifestReaderTest`` together failed 21 and errored 15. **Preserve that
separation.** A future agent "simplifying" the reader tests to build their
fixtures with the writer would delete the only thing standing between this
project and a symmetric, unanimous, wrong provenance record.

**A defect unit 9 did not report, found at sign-off and repaired.** The
reader's contract is that a hostile document is refused with a located
``InvalidManifestException`` and never anything else -- which is why nesting is
bounded rather than left to overflow the stack. The arithmetic twin of that was
open. The timestamp format's year field is backed by a ``LocalDate`` and reaches
year +-999 999 999, about two billion years, while ``Duration.toMillis()``
overflows a ``long`` at about 292 million. Measured on this JVM::

    parse OK   -0999999999-01-01T00:00:00.000Z
    parse OK   +0999999999-12-31T23:59:59.999Z
    millisBetween(...) THREW java.lang.ArithmeticException: long overflow

A document at both extremes parsed, satisfied every model invariant, reached
``requireRecordedDuration`` and threw ``java.lang.ArithmeticException`` **out of
``ManifestReader.parse``**, past every caller that catches
``InvalidManifestException`` and nothing else. Unit 9 knew the *writer* half and
recorded it as "a run spanning the format's two extremes cannot be serialised";
the *reader* half is the one a corrupt file reaches. Repaired, with its own
negative control.

**The Sphinx gate over unit 10's sample report, run both ways at last.** The
sample is the fully populated fixture -- an empty settings value, one with a
leading space, one with a line feed, one with backticks and an asterisk, a path
holding a quotation mark and a backslash, accented text and an emoji. Clean it
builds; and because exit 0 proves nothing, the **HTML was read**: no
``problematic`` or ``system-message`` spans, **zero** literal double-backticks
anywhere in the output, 108 rendered inline literals, and each of the four
values an inline literal cannot carry present in its escaped form. With an
underline one character short injected, the same gate failed with eight
``Title underline too short`` warnings under ``-W`` and exit 1. Two of those
four value shapes **fail silently in Sphinx**, so a green build is not by
itself evidence that a value was rendered as a literal -- which is why the HTML
is inspected and not only the exit code.

.. _p04-crosstalk:

One cross-agent false alarm, worth understanding
------------------------------------------------

Unit 10 reported, correctly and without touching it, that
``ManifestWriter:322`` read
``json.name("durationMillis").value(execution.duration().toSeconds())`` -- a
field named milliseconds carrying seconds, which would have put the JSON and
the RST three orders of magnitude apart on the same value.

It was **not** a real defect. It was unit 7's falsifiability defect (h),
present in the working tree for the minutes it took that agent to inject it,
observe the failure and revert it. I confirmed the committed code reads
``CanonicalTimestamp.millisBetween(...)``, that the pinned document carries
``1950500``, and that ``git log -S "duration().toSeconds()"`` finds **no commit
that ever contained it**.

The hazard generalises, and a future multi-agent phase should expect it: **in a
shared working tree, one agent reading another's file can observe a
deliberately injected defect and report it as real.** Unit 10 did exactly the
right thing -- it reported rather than assumed, and it did not touch a file
outside its unit. Had it "fixed" what it saw, it would have silently reverted
another agent's revert.

.. _p04-gate-items:

The seven gate items, with the command that produces the evidence
=================================================================

Every command below was run by the phase orchestrator on the quiet tree at
``9ddb3e3``, and every figure is from that run. Each item also has a control in
``scripts/verify-provenance-gates.sh`` that injects the defect the item exists
to catch and requires the named command to reject it -- so each row is backed by
a green run *and* by a red one.

.. list-table::
   :header-rows: 1
   :widths: 4 20 10 66

   * - #
     - Gate item
     - Result
     - The command, the evidence, and the control that proves it can fail

   * - 1
     - Known MD5 and SHA-256 vectors, including the zero-byte file
     - **MET**
     - ``mvn -pl cometgui-provenance -am test -Dtest=StreamingHashServiceTest``
       -- 52 tests, 0 failures. Every expected digest is a hand-typed literal
       and all fourteen published values were **recomputed independently with
       GNU coreutils** at the resumption, including the three that live inside
       a ``@CsvSource`` string where a grep for a quoted hex constant misses
       them; every one matched to the character. The reference table was pinned
       in the work log *before* the module held a hashing class, so no expected
       value can have come from the code under test.
       **Control 1** digests one byte less than was read: ``MD5 ==> expected:
       <900150983cd24fb0d6963f7d28e17f72> but was:
       <187ef4436122d1cc2f40dc2b92f0eba0>``.

   * - 2
     - 2 GB in one pass, bounded heap, digests matching independent values
     - **MET**
     - ``mvn -pl cometgui-provenance -am test -Dtest=HugeFileHashingTest`` --
       the suite prints its own measurement: ``huge-file: bytes=2147483648
       writeMillis=1192 hashMillis=14189 totalMillis=15381 opens=1
       readCalls=8193 bytesDelivered=2147483648 heapBaseline=4680816
       heapPeak=4945888 heapGrowth=265072 heapLimit=4194304 samples=81``.
       Retained heap sampled post-collection, not an allocation count: growth
       of one buffer, 15.8x under a bound of 16x ``BUFFER_SIZE``. Both digests
       were computed by coreutils **and** OpenSSL before this code existed. A
       **permanent negative control** ships in the suite and prints
       ``huge-file-control: keptChunks=128 keptBytes=33554432
       heapGrowth=33833192 heapLimit=4194304``, so every build re-proves the
       bound bites while the leaky hasher's digests stay exactly correct.
       **Control 2** makes the real hasher keep every chunk, which leaves the
       digests correct and is invisible to every other test in the module.

   * - 3
     - Cache returns a value only when every attribute matches
     - **MET on POSIX**
     - ``mvn -pl cometgui-provenance -am test -Dtest=CachingHashServiceTest``
       (35 tests) and ``-Dtest=FileFingerprintTest``. Detected rather than
       survived: the key carries a fifth attribute beyond ``R-PROV-02``'s four,
       the inode change time ``unix:ctime``, which the kernel bumps on both the
       write and the mtime restoration and which user space cannot forge. Where
       that evidence is absent the cache stores **nothing**. **On Windows this
       is a different algorithm that has never run** -- :ref:`p04-divergence`.
       **Control 3** treats an absent attribute as a match, dressed as Windows
       compatibility: ``absent vs present identity ==> expected: <false> but
       was: <true>``.

   * - 4
     - A crash mid-run leaves a parsable log with usable history
     - **MET on POSIX**
     - ``mvn -pl cometgui-provenance -am test -Dtest=EventLogCrashRecoveryTest``
       -- proved by tearing real files at several byte offsets, not by
       argument. The unit's own strongest evidence is defect (f): replacing the
       newline terminator makes one crash turn the whole log into a single torn
       line and recover **zero** events. **Control 4** drops the torn tail
       instead of reporting it, so a crashed run reads back as a clean one.

   * - 5
     - Finalisation is atomic; an interrupted finalise never truncates
     - **MET on POSIX**
     - ``mvn -pl cometgui-provenance -am test -Dtest=AtomicDocumentWriterTest``
       -- proved by observation. **Control 5** replaces ``ATOMIC_MOVE`` with
       ``Files.copy`` + ``delete`` and a concurrent reader then observes
       truncated documents and moments at which the target does not exist at
       all. Interruption is proved by interrupting. **The Windows half is
       unproven and is the most important item in** :ref:`p04-divergence`.

   * - 6
     - Seeded secrets appear nowhere in JSON, RST or logs; the test greps
     - **MET**
     - ``mvn -pl cometgui-provenance -am test
       -Dtest=SeededSecretArtefactSweepTest`` (8 tests) plus
       ``ProvenanceReportWriterTest``, ``EventLogSecrecyTest`` and
       ``cometgui-domain``'s ``SeededSecretCorpusTest``. **This is the item the
       pause left ``PARTIAL`` and unit 11 completed.** One test now builds a
       manifest and a seventeen-event stream carrying all thirteen corpus
       entries, writes ``provenance.json``, ``provenance.rst`` and an event log
       into one run directory, **walks that directory** and reads every regular
       file back off disk, searching each as UTF-8 text and as raw US-ASCII
       bytes. The RST half is closed by ``sphinx-build -n -W`` over the
       generated sample, run both ways.
       **Controls 6a, 6b and 6c** remove redaction from each writer in turn.
       Each failure names the artefact, the corpus index, the length and the
       offset, and prints no secret: ``corpus secret #1 (length 39) survived
       into provenance.json as US-ASCII bytes, at offset 1595``.

   * - 7
     - PIT reports no surviving mutation in hashing and redaction
     - **MET, and stronger than the item asks**
     - ``mvn -pl cometgui-provenance -am test-compile
       org.pitest:pitest-maven:mutationCoverage``, read from ``mutations.xml``
       rather than from the console: **774 mutations, 771 KILLED, 0 SURVIVED,
       3 TIMED_OUT, 0 NO_COVERAGE** across the whole module, not only the two
       packages the item names -- ``hashing`` 70, ``io`` 20, ``events`` 189,
       ``json`` 221, ``manifest`` 153, ``report`` 121. ``cometgui-domain``,
       which holds the redaction rule set, is **204 mutations, 204 KILLED**
       with ``secrets`` contributing 52. The three timeouts are genuine
       infinite loops and PIT counts a timeout as detected; each was read and
       confirmed to be one. **Control 7** asserts that
       ``scripts/verify-test-gates.sh`` still holds the mutation control the
       item delegates to, and that this module still carries
       ``<cometgui.mutation.skip>false</cometgui.mutation.skip>`` -- a switch
       that is set and inert is the failure this project keeps finding.

**Why the outcome is ``PARTIAL`` and not ``PASSED``.** Every item above is met
on the platform the gate runs on. Items 3, 4 and 5 carry a Windows half that is
**different code that has never executed anywhere**, which
``STATUS.rst``'s grading rule calls unverified behaviour rather than a testing
gap. The residue is enumerated at :ref:`p04-divergence` tier B and the largest
of it -- ``ATOMIC_MOVE`` over a file another process holds open -- is already
routed to Phase 13.

.. _p04-where-secrets:

Where the shared secret rule set lives now
==========================================

**``org.cometgui.provenance.redaction`` NO LONGER EXISTS.** Do not recreate it.

The rule set is ``org.cometgui.domain.secrets`` in ``cometgui-domain``:
``SecretRedactor``, ``SecretRegistry``, ``SecretTooShortException``. Both
``cometgui-provenance`` and ``cometgui-process`` depend on ``cometgui-domain``
and not on each other, so it is the only module that can hold shared code
between the two.

Why it moved, because the reason generalises. Phase 03 independently built a
second rule set -- ``SecretNames``, ``SecretValues``, ``ProcessRedactor`` in
``cometgui-process`` -- and within hours the two keyword lists had diverged, so
a value named ``...signature...`` was redacted in the process log and not in
provenance. That is exactly the silent drift ``R-SEC-03`` exists to prevent,
and it falsified this phase's own "driven by ONE rule set" wording. The main
orchestrator ruled the move; this phase executed it because its tests were the
deeper pair.

The keyword list is the **considered union**, fourteen entries. ``signature``
came from Phase 03 and was a genuine gap: ``REQUEST_SIGNATURE`` matched nothing
before. ``limelightkey`` was added as a forward-looking, product-specific entry
with its limitation written into the code -- ``LIMELIGHT_API_KEY`` was already
covered by ``apikey``, a plain ``LIMELIGHT_KEY`` was covered by nothing, and
**it does not make Phase 12 safe on its own**, because no name rule can see a
token passed positionally or embedded in a URL. Phase 12 must register the
credential *value* with a ``SecretRegistry``.

Phase 03 keeps ``ProcessRedactor``, which holds two process-specific insights
that must survive: redacting each argv element **before**
``ToolCommand.displayString()`` escapes it, since escaping turns a quote inside
a token into ``\\"`` and a literal post-escape search then misses it; and
returning the argument by reference when the registry is empty, so a 500 MB log
flood costs nothing.

.. _p04-encoding-sites:

The two ``Assumptions.abort`` encoding sites, and why they stay
===============================================================

Two test helpers abort rather than fail when ``Path.of`` throws
``InvalidPathException``, naming ``sun.jnu.encoding`` and the
``LANG=C.UTF-8`` remedy in the message. **Leave them exactly as they are.**
Both the phase orchestrator and the main orchestrator agreed on this.

They are the honest form. This JVM reports ``sun.jnu.encoding =
ANSI_X3.4-1968`` because nothing sets ``LANG``, so a non-ASCII path is not
representable *at all* -- ``Path.of("/data/protéomique/x.mzML")`` throws before
any CometGUI code runs, and the same call under ``LANG=C.UTF-8`` returns the
path unharmed. A skip carrying that diagnostic is worth more than a green
obtained by deleting the test.

**The obvious remedy is inert, and this is the part to carry forward.** Passing
``-Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8`` does nothing:
``sun.jnu.encoding`` is resolved from the process environment *before* system
properties are applied, so the flag is accepted without error and ignored.
Measured::

    java -Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8 ...
      file.encoding    = UTF-8
      sun.jnu.encoding = ANSI_X3.4-1968      <- ignored
      THREW: InvalidPathException: /data/prot?omique/x.mzML

Only the environment works (``env LC_ALL=C.UTF-8``). The requirement for
Phases 14 and 16 is therefore **"the launcher must start the JVM in a UTF-8
locale"**, which ``jpackage --java-options`` cannot express. A handoff naming a
fix that does not work is worse than one naming only the problem.

.. _p04-survivors:

The PIT survivors: all closed, and two of them were not what they looked like
=============================================================================

At the pause the last mutation run showed **776 mutations, 767 KILLED, 7
SURVIVED, 2 TIMED_OUT** on a moving tree, all diagnosed and **none confirmed**.
The resumption confirmed every one. The module is now **774 mutations, 771
KILLED, 0 SURVIVED, 3 TIMED_OUT**.

.. list-table::
   :header-rows: 1
   :widths: 34 14 52

   * - Site, as the pause listed it
     - Outcome
     - What actually closed it

   * - ``ProvenanceEventLogReader$Recovery:310``, MathMutator on
       ``charAt(cut - 1)``
     - **KILLED**
     - The one genuine survivor. Its fixture landed at ``892962e`` and was
       unconfirmed; the resumption's first mutation run confirms it. A single
       astral character alone in ASCII is what tells ``cut-1`` and ``cut+1``
       apart -- in a *uniform* run of astral characters they are always the
       same half of a pair, and the mutant is observationally identical.

   * - ``JsonReader:203`` and ``:256``, VoidMethodCall on ``skipWhitespace``
     - **Equivalent, and the code was deleted**
     - The pause called these equivalent mutants whose fix is to delete code.
       Checked rather than accepted, and it holds: whitespace is already
       skipped on the way into each loop and after every comma, so the call at
       the loop top is reachable only in a state where it has nothing to do.
       Both are gone, replaced by a comment stating the invariant they were
       pretending to defend. **A call that cannot change behaviour is the code
       form of a check that cannot fail.**

   * - ``JsonReader:228``, ``:252``, ``:264``, VoidMethodCall on
       ``leaveContainer``
     - **KILLED, and they were never equivalent**
     - These are three of the four routes out of a container, and the existing
       nesting test drove only the fourth -- the empty-object early return. The
       reason no test noticed is that depth is checked only on the way *in*, so
       it takes more than ``MAX_DEPTH`` siblings of the right shape to bite.
       **The consequence is a real product defect, not a mutation-score
       artefact**: ``provenance.json`` holds one object per file and one per
       tool in flat arrays, so a reader that counted opens without counting
       closes would refuse to parse any run with more than 64 inputs.

   * - ``JsonReader:273``, VoidMethodCall on ``skipWhitespace``
     - **KILLED**
     - Shape 5, an input set too narrow. The trailing-comma fixture was
       ``[1, 2,]`` with no space, so the call did nothing in it. ``[1, 2, ]``
       and ``[1, 2,\n]`` are now asserted beside it: without the skip the
       reader reports "a value was expected here" for a document whose actual
       fault is a trailing comma.

**The three timeouts, read rather than counted.** ``JsonWriter:445`` and
``ProvenanceEventLogReader:169`` are negated loop conditionals; the third,
``EventLineFormat$Cursor:440``, is a MathMutator on the ``index++`` that
consumes a closing quote, which makes ``readString`` loop on one character for
ever. All three are genuine infinite loops, so PIT counting a timeout as
detected is right here rather than merely conventional. One mutation moved
between ``KILLED`` and ``TIMED_OUT`` across two runs with the total detected
unchanged: a mutant that hangs rather than fails is detected either way, and a
mutation slow enough to sit near the timeout can report as either.

.. _p04-roundtrip:

Why the round-trip suite is not the proof, in unit 9's words
============================================================

The phase brief warned that "a round-trip test that serialises and
deserialises with the same code proves symmetry, not correctness". Unit 9
tested that claim rather than repeating it, and its answer is the clearest
statement of the trap this project has produced.

It injected a reader that reads ``"formatLocale"`` where it must read
``"locale"``. **Both suites failed** -- hand-typed with
``expected: <en_US> but was: <de_DE>``, and the round trip with eleven
failures.

But, in its own words: the round trip caught it **only because the writer is
correct and signed off**. Had the writer's key been changed to the same wrong
name, writer and reader would agree, every round trip and all 200 generated
manifests would be green, and **every ``provenance.json`` on disk would be
wrong**. ``ManifestRoundTripTest`` never mentions a key name, so no key name
can be wrong in a way it detects once both sides agree.

What catches a mutually-agreeing pair is the two hand-typed documents:
``ManifestWriterTest`` pins the bytes, ``ManifestReaderTest`` pins the values
read from those bytes, and both were typed from the format rather than
captured from the code. That is why the reader's tests use no writer at all.
**Preserve that separation.** A future agent "simplifying" the reader tests to
generate their fixtures with the writer would remove the only thing standing
between this project and a symmetric, unanimous, wrong provenance record.

.. _p04-traps:

Traps that cost hours and are not discoverable
==============================================

Every one of these was paid for once already in this phase.

**PIT resolves from the local repository, not the reactor.** After the secret
rule set moved modules, ``_build/m2repo`` still held the pre-move
``cometgui-domain`` jar, and a mutation run failed with
``ClassNotFoundException: org.cometgui.domain.secrets.SecretRegistry`` and 83
test failures that looked exactly like another unit's bug. **Run
``mvn -pl cometgui-domain install`` before any PIT run**, always, and
especially after any cross-module change. Ten seconds to avoid, an hour to
diagnose.

**A ``NO_COVERAGE`` reading can be a classloading failure wearing a costume.**
Unit 8 saw five ``NO_COVERAGE`` mutations that were nothing of the sort. The
installed ``cometgui-domain`` jar in ``_build/m2repo`` predated the secrets
package move, so under PIT every test importing ``SecretRegistry`` failed to
load, and the lines only those tests reach read as uncovered. **A
``NO_COVERAGE`` reading caused by a classloading failure is indistinguishable
from one caused by missing tests.** The diagnostic is one command::

    jar tf _build/m2repo/org/cometgui/cometgui-domain/*/cometgui-domain-*.jar \
        | grep secrets/

Zero entries means the jar is stale. After ``mvn -pl cometgui-domain install``
the five vanished and one real survivor remained. This will recur every time
``cometgui-domain`` changes.

**A revert can preserve mtime and quietly not rebuild.** Unit 9's revert
script used ``shutil.copy2``, which copies the modification time with the
file, so Maven's incremental compiler saw nothing newer and the suite ran
against the *previous* defect's classes -- reporting failures against clean
sources. This is the injection-clobber hazard's mirror image: it can make a
clean tree look broken as easily as a broken tree look clean. ``touch`` the
reverted file, and always re-run after a revert rather than trusting it.

**Scope ``spotless:apply``.** A bare ``mvn spotless:apply`` reformats every
file in the module, including files another agent is mid-edit in, and the
diff then looks like you touched their work. Use
``-DspotlessFiles=".*/provenance/<yourpackage>/.*[.]java"``.

**Commit by exact file path, not by directory.** A directory pathspec sweeps a
sibling's in-flight files into your commit. Every unit here was told to name
its files.

**Sibling agents share one scratchpad root.** One agent overwrote another's
``inject.py`` mid-run, so two injections silently changed nothing and the suite
went green *with the defect supposedly present*. Use a private subdirectory,
and **verify every injection landed** -- assert the replacement matched exactly
once, then grep the marker back out of the target file -- before believing any
test result. Restore with ``sha256sum -c``, never by eye.

**The strict docs gate fails on a title underline one character short.**
``sphinx-build -n -W`` treats it as an error. This bit twice, in this phase's
own hand-written documents. Run ``bash scripts/ci/docs-build.sh`` **and gate
the commit on it** -- running the check and committing on the next line is how
both slipped through.

**``mvn -pl <module>`` always needs ``-am``**, or you compile against stale
snapshots in ``_build/m2repo`` and the error names your own code.

**Two Maven runs in one tree collide.** ``scripts/build.sh`` line 217 runs
``mvn clean verify`` at the repository root *in the working tree*, so two
concurrent runs delete each other's ``target/``. While another phase is live,
serialise every Maven invocation behind a lock::

    flock /path/to/p04-maven.lock -c 'cd <repo> && . ./tools/env.sh && mvn ...'

.. _p04-census:

The class-population census
===========================

**Why it exists.** A coverage rule reports "All coverage checks have been met"
over whatever is in its sample. A class whose test does not compile can be
absent from the report entirely, and an absent class does not drag an average
down -- it silently leaves the sample. That produces a **real measurement over
an incomplete population**: a number that is both correct and meaningless, and
the only variety that re-running the gate cannot catch, because re-running it
reproduces the same clean figure.

It is not hypothetical. On its first run, against a tree where five units were
landing at once, it found::

    module: cometgui-provenance
      compiled classes (excluding package-info and inner): 37
      classes in jacoco.xml:                               36
      distinct classes with PIT mutations:                 30
      COMPILED BUT ABSENT FROM JACOCO:
        org.cometgui.provenance.manifest.ManifestReader

``ManifestReader`` was compiled, carried 79 ``NO_COVERAGE`` mutations, and was
absent from the coverage sample the rule passed over.

The script is in ``handoffs/PHASE-04-worklog.rst`` verbatim, with its
invocation. **Design note for whoever implements it properly:** the JaCoCo list
is a hard failure, but the second list -- compiled classes with no PIT
mutations -- must stay a prompt for a human rather than an assertion, because
an interface, a constant enum or a branchless record legitimately yields none.

Tier 1 has taken this as infrastructure to be closed after Phases 03 and 04
land and before Phase 05 is dispatched, in two parts: the census in
``scripts/build.sh``'s gates stage beside the module-level check it completes,
**and a control in ``scripts/verify-test-gates.sh`` that injects a class
excluded from coverage and requires the census to fail naming it.** The second
half is not optional. A rule that has never been seen to go red is not yet a
rule, and that is the mistake this project was founded on.

.. _p04-shapes:

Nine shapes of one defect
=========================

This project has one recurring failure, and this phase catalogued four new
forms of it. They are collected here because the next agent will meet an
eighth, and the pattern is more useful than any single instance.

The failure is **a check that cannot fail**. What varies is *which part*
stopped working.

#. **The assertion evaluated nothing.** Phase 01's ArchUnit rule reported 8/8
   while its import came back empty.
#. **The expected value came from the code under test.** Phase 02's GUI tests
   computed their expected identifier by calling the same production helper the
   production code called, so renaming an identifier left the build green.
#. **The property was proved on a seam production could bypass.** Unit 1 here
   asserted "one pass" through a package-private ``hash(InputStream)``; I
   replaced ``hash(Path)``'s delegation with one that read the file **twice**
   and all 39 tests stayed green.
#. **The injection stopped working, not the assertion.** Unit 5's ``inject.py``
   was overwritten by another agent in the shared scratchpad, so two defects
   silently changed nothing and the suite went green *with the defect
   supposedly present*. "I injected X and the tests still passed" is
   indistinguishable from "the gate is weak" unless the edit is verified to
   have landed.
#. **The defect could not be injected at all.** Unit 7 injected "render
   timestamps in the default zone" -- the defect its gate exists to catch --
   and all 79 tests passed, because the formatter is a ``static final`` field
   initialised before any test can call ``setDefault``, on a host whose zone is
   already UTC. The repair asserts ``ZoneOffset.UTC`` reflectively, and
   deliberately not ``ZoneId.of("UTC")``, because the two are unequal and only
   one of them bites.
#. **The sweep was blind to a whole class of leak.** A ``contains(secret)``
   sweep is defeated by a partial rewrite *and* by a leak conditioned on input
   SIZE. My ``if (text.length() < 32) return text;`` leaked ``password:
   swordfish-42`` in clear while ``SeededSecretCorpusTest`` passed 8/8, because
   every carrier in the corpus was long. Carrier length is now part of the
   corpus's coverage, pinned by its own assertion.
#. **A real measurement over an incomplete population.** The worst, because it
   is the only one that survives a re-run of the gate. ``ManifestReader`` was
   compiled, carried 79 ``NO_COVERAGE`` mutations, and was *absent from
   jacoco.xml*; "All coverage checks have been met" was true of the sample and
   meaningless about the code. See :ref:`p04-census`.

#. **An injection that reached the source but not the compiled class.** Phase
   03's, and the reason every injection in this phase's harness is proved to
   have changed the ``.class`` and not only the ``.java``.
#. **A check whose subject silently left the sample.** The ninth, found by this
   phase's own harness on its first run: a "did the class actually run" guard
   that scraped the console read ``Tests run: 0`` for a class that had just run
   fifty-two tests, because this module puts its tests in ``@Nested`` classes
   and surefire prints the outer class as zero. It stopped the run rather than
   passing -- which is the only reason it is a story about a guard working
   rather than another entry in this list.

The remedy that generalises: **verification must include an audit of the
population, and that audit has to run beside the numbers rather than once.** A
census on a quiet tree finds nothing and proves nothing; it earns its place by
running on every build. Expect a tenth.

.. _p04-harness:

The falsifiability harness this phase now ships
===============================================

``scripts/verify-provenance-gates.sh``, registered as the ``provenance``
control of ``scripts/verify-all-gates.sh`` with a floor of **24 controls**.
Phases 01 and 02 each ship one; Phase 03 escalated the absence of one as debt
before Phase 08 depends on its service. Phase 04 now has one, and it was
**assembled from ``handoffs/PHASE-04-worklog.rst`` rather than invented** --
every injection in it was actually made during the phase and its exact failure
text is in that record.

It damages a ``git archive HEAD`` sandbox under ``_build/`` and never the
working tree; every injection must match its anchor **exactly once** and be
proved to differ from a pristine copy before any test result is believed; every
restore is verified and then ``touch``\ ed, because a copy preserves the
modification time and Maven then runs the *previous* defect's classes against
clean sources.

**It found two defects in itself before it accepted anything, and both are
recorded in the script rather than quietly fixed.**

* Its "did the class actually run" guard scraped the console for ``Tests run:
  N ... -- in <class>``. Every test class in this module puts its tests in
  ``@Nested`` classes, and surefire prints each nested class under its
  ``@DisplayName`` and then prints ``Tests run: 0`` for the **outer** class --
  so the guard read 0 for a class that had just run 52 tests and stopped the run
  with a harness error. That is the guard working, on its first run, on its own
  author. The count now comes from ``<testsuite tests="N">`` in the surefire
  XML, and the previous control's reports are deleted before each run so that a
  run which executed nothing cannot be graded against stale files.
* Its gate item 2 assertion was written from the printout **quoted in this
  handoff**, which is abbreviated: the real line carries three timing fields
  between ``bytes=`` and ``opens=``. The harness rejected its own first
  expectation before it accepted anything.

Cost: **236 seconds**, which takes ``verify-all-gates.sh`` from 1702 to 1917
seconds. The script's stated cost was corrected from "about seven minutes" to
"about half an hour", which it already was before this phase added to it.

.. _p04-carried-answers:

Two questions carried in from elsewhere, answered
=================================================

**Phase 03's escalation 4: the fixed sleep in ``CachingHashServiceTest``.**
Answered by justifying it in the code, and the justification is now a paragraph
of ``awaitSettled``'s documentation. It is **not a fixed sleep**: the deadline
is read from the file's own later of ``mtime`` and ``unix:ctime``, truncated to
its second plus one, and the sleep is whatever remains of that and is skipped
when the deadline has already passed -- which it usually has. **There is no
event to wait for**: the thing awaited is not a change to the file but the
system clock advancing past the second the file was last changed in, and the
kernel publishes no notification for that, so a ``WatchService`` has nothing to
subscribe to and a poll would be a busy-wait for the same deadline. The
alternative is a substituted clock, which group 4 already uses for the boundary
cases and which group 1 deliberately does not, because a property proved only
through a seam is a property the production path is free not to have -- the
rejection this phase's unit 1 earned. The one chosen number is a 20 ms margin
past the second boundary, and it is named as such. If a mechanical scan flags
the method, that paragraph is the answer.

**The seeded corpus and GitHub's push protection.** Tier 1's push of ``main``
was rejected because ``glpat-Z1x9QeR7sVbN3mK0pLtY`` -- a hand-typed fake shaped
like a GitLab access token -- matches the ``glpat-`` prefix its scanner keys on.
Two corrections to the record first, both checked rather than assumed. The
string is in **five locations, all of them in ``cometgui-provenance``**;
``git log -S`` over all history shows it entering in exactly two source commits
(``5b10a58`` and ``3e552b4``) and there is **no occurrence in
``cometgui-domain``, ever**. And ``STATUS.rst`` now contains the literal string
too, so tier 1's own record is a sixth copy and the owner's allowlisting has to
cover it.

**The convention question, which is this module's to answer.** Whoever chose
``AKIAIOSFODNN7EXAMPLE`` chose AWS's published documentation example, which
scanners allowlist by design. **That is the right convention and the corpus
should adopt it wherever a provider-shaped prefix is needed.** The property the
corpus actually needs is *distinctiveness against an accidental substring
match* -- that a sweep for it cannot be satisfied by text that happens to occur
in a path, a digest or a version string -- and a provider's own published
example has that property exactly as well as a hand-typed fake does, because it
is equally arbitrary and equally long. Authenticity is not a property the corpus
needs at all: nothing here authenticates against a provider, and
``SeededSecretCorpusTest``'s own reasoning is about *shape* and *length*, never
about whether a token would be accepted by anyone. So adopting published
examples costs the corpus nothing it needs, and buys the absence of a class of
obstruction that will otherwise recur at every fork, mirror and re-push.

Two caveats that keep this from being a rule to apply blindly. **A published
example does not exist for every provider** -- Limelight has none, and
``ll_live_9f8e7d6c5b4a39281706`` is invented because it must be; for those, an
invented value is still correct, and choosing one that fails the provider's own
checksum where a checksum exists is what
``ghp_S3cr3tT0k3nExampleValue0123456789ab`` does by luck and should do by
intent. And **the fixtures must not be changed now**: the string is already in
unpushed-then-pushed history, editing the working tree cannot remove it, and
rewriting history would destroy the commit hashes that every sign-off entry in
both work logs cites as its evidence. The convention is for the **next** fixture
anyone writes, and for Phase 12, which will hold a real Limelight credential and
where the same scanner is a protection rather than an obstacle and must not be
routinely bypassed.

.. _p04-escalations:

Escalated to the main orchestrator
==================================

#. **The grade.** ``PARTIAL``, for the reason in :ref:`p04-gate-items`: every
   item is met on POSIX and items 3, 4 and 5 carry Windows code that has never
   executed anywhere. If tier 1 judges that "verify per platform" is Phase 15's
   obligation rather than this phase's, every item is a clean ``PASS`` and so is
   the phase. That is a call above me.

#. **``scripts/verify-test-gates.sh`` needs one line, and its own comment
   predicts it.** The sandbox that script builds carries the POMs, ``config/``,
   ``scripts/``, ``specification.rst`` and each module's ``src/`` -- and its
   comment says "Phase 04's provenance report and Phase 15's traceability work
   are the next two that will want a document here; add them the same way, with
   the reason". Unit 12's drift check between ``docs/reference/
   provenance_format.rst`` and ``ManifestWriter`` would be **mechanical on both
   sides** if the test could read that page, and today it cannot: a test that
   opened ``docs/`` would fail inside that sandbox and take a currently-green
   control down with it. The page is therefore stood in for by a hand-typed set
   inside the test, with the page named in the class documentation.
   **The cost of each option.** Adding ``cp "${ROOT}/docs/reference/
   provenance_format.rst" "${SANDBOX}/..."`` is one line and lets a follow-up
   make the check fully mechanical; leaving it is a page that can drift from
   its own hand-typed copy while the writer stays in step with both. I did not
   make the change because that script is on my escalate-before-editing list
   and tier 1 is implementing the census in it immediately after this phase
   lands. **A cross-directory ``.. include::`` is not an alternative**: I tested
   it, and ``scripts/ci/docs-build.sh --self-test`` copies only ``docs/`` into
   a throwaway tree, so an include reaching outside it would break the
   documentation gate's own self-test.

#. **The class-population census is still fifteen lines run by hand.** Tier 1
   owns putting it into ``scripts/build.sh`` with a control in
   ``verify-test-gates.sh``. It was run by hand at every measurement in this
   resumption and found nothing, which is what a clean tree should look like --
   and is exactly why it has to run on every build rather than when someone
   remembers. The script is in the work log verbatim.

#. **``ExecutionRecord.status`` documents three values and enforces none.** Its
   documentation says ``COMPLETED``, ``FAILED`` or ``CANCELLED``; the
   constructor only null-checks and ``ManifestReader`` accepts all five wire
   names there. Narrowing the type is a behaviour change and belongs to whoever
   owns the stage semantics -- Phase 08. Documented as it is rather than as it
   claims to be.

#. **The specification names "a safely rendered command for display" and the
   format has no member for it.** ``argv`` is recorded and the display string
   is a pure function of it. The reference page states the divergence. If tier 1
   thinks the file must carry it, that is a schema-version 2 change rather than
   a documentation fix.

#. **The event log's file name is pinned by no constant.**
   ``ManifestWriter.FILE_NAME`` and ``ProvenanceReportWriter.FILE_NAME`` exist;
   the log is opened on a caller-supplied path and ``events.log`` appears only
   in tests. **Phase 13 needs a discoverable log in a run directory** and must
   pin a constant rather than guess a name.

.. _p04-first-thing:

What is incomplete, and the first thing the next agent should do
================================================================

**What is incomplete.** Only the platform residue. Every unit is signed off,
every gate item is evidenced, and nothing in this phase is deferred except:

* the Windows behaviour of gate items 3, 4 and 5, enumerated at
  :ref:`p04-divergence` tier B. ``ATOMIC_MOVE`` over a file another process
  holds open is routed to Phase 13; absolute-path validation and
  ``toRealPath`` case folding are Phase 15's platform matrix;
* the ``sun.jnu.encoding`` defect, routed to Phases 14 and 16, with the finding
  that the obvious ``-Dsun.jnu.encoding=UTF-8`` remedy is **inert** -- see
  :ref:`p04-encoding-sites`. **Do not delete the two ``Assumptions.abort``
  sites**; both orchestrators agreed they are the honest form;
* the six items escalated at :ref:`p04-escalations`, none of which blocks a
  later phase.

**The first thing the next agent should do, by who they are.**

*If you are grading this phase:* re-run the seven commands in
:ref:`p04-gate-items` and then ``bash scripts/verify-provenance-gates.sh``,
which injects a defect into each item and requires the gate to reject it. Do
not reuse its injections as your own -- pick different ones. The census is
fifteen lines in the work log and takes a second; run it before you read any
percentage.

*If you are Phase 13, the Provenance UI:* read
``docs/reference/provenance_format.rst`` first -- it is written so that a
parser can be built from it without opening the Java -- and then
``docs/developer/provenance_schema.rst``. Then settle ``ATOMIC_MOVE`` under
contention **before** building a viewer that holds ``provenance.json`` open,
because if Windows cannot replace an open file the repair is a retry policy or
a different finalisation strategy: a design change, not a test. And pin a
constant for the event log's file name; there is not one yet.

*If you are Phase 08, the workflow engine:* every stage that runs a tool must
add a ``ToolRecord`` with its ``stageId``, and every file it touches a
``FileRecord``. The model exists so that no stage can be built without
recording itself -- that is the whole reason this phase came before the stages
-- and ``ProvenanceEventLog`` is what a crashed run leaves behind. Settings keys
are gated by *shape*, ``[a-z0-9]+(\.[a-z0-9-]+)+``, and each phase pins its own
constant beside ``percolator.seed``.

*If you are Phase 12, Limelight upload:* ``limelightkey`` in the shared keyword
list **does not make you safe on its own**. No name rule can see a token passed
positionally or embedded in a URL. Register the credential *value* with a
``SecretRegistry``, and read :ref:`p04-carried-answers` on the fixture
convention before you write a test fixture that looks like a real key.

*Whoever you are:* **read ``handoffs/PHASE-04-worklog.rst``.** Every sign-off
entry names the defect that was injected and the exact failure text it
produced. Those are the proofs; this document is only the map.
