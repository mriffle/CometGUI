=====================================================
PHASE-04 handoff -- Hashing and Provenance Core
=====================================================

:Phase: 04
:Phase orchestrator: Phase-04 orchestrator subagent (session 04)
:Status: IN PROGRESS -- this document is being written as the phase closes
:Last updated: 2026-08-31

.. warning::

   **PHASE 04 IS PAUSED, NOT FINISHED, FAILED OR ABANDONED.** The owner paused
   it for cost while Phase 03 finishes alone in the same working tree. A fresh
   agent resumes it from this document. Nothing here is a claim that the phase
   is done.

   **EVERY NUMBER IN THIS DOCUMENT WAS TAKEN FROM A NOISY TREE.** Between two
   and five agents were landing code in ``cometgui-provenance`` for most of the
   phase, and Phase 03 was working in ``cometgui-process`` throughout. No
   headline figure here has been re-taken from a quiet tree, and the single
   clean end-to-end run that would produce one was deliberately NOT run,
   because Phase 03 is still changing the tree and any figure would be invalid
   by the time this phase resumes. **Re-take them all.** See
   :ref:`p04-first-thing`.

Why the numbers cannot be trusted yet
=====================================

This is not modesty, it is the phase's own central finding applied to itself.
A coverage or mutation figure taken while sibling units are mid-landing is
uninterpretable, and it can err in **either** direction. Low is visible: one
unit saw its ``json`` package read 38% because another unit's tests had not
compiled yet. High is invisible: a class whose test does not compile can be
absent from the report altogether, and an absent class does not drag an
average down -- it silently leaves the sample. That happened here, was caught,
and is the reason :ref:`p04-census` exists.

So: treat every figure below as an observation about a moving tree, useful for
knowing roughly where things stand and useless as evidence for a gate.

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
     - Strict JSON reader
     - **See below**
     - Nothing committed at the time of the pause.

   * - 10
     - ``provenance.rst`` report
     - **See below**
     - Nothing committed at the time of the pause.

   * - 11
     - Seeded-secret grep over generated artefacts
     - **NOT STARTED**
     - Gate item 6's end-to-end half. Deliberately not begun.

   * - 12
     - Documentation
     - **NOT STARTED**
     - ``docs/reference/provenance_format.rst`` and
       ``docs/developer/provenance_schema.rst`` are still the Phase 01 stubs.

   * - 13
     - Gate enablement and falsifiability
     - **Partly done**
     - The module's mutation switch is on and verified live (``4c16864``). The
       final clean run and ``verify-all-gates.sh`` were not run.

.. _p04-reverted:

Units 9 and 10 -- LANDED, NOT SIGNED OFF
-----------------------------------------

Both committed in the final minutes, so **nothing was reverted and no work was
lost**. But both are ``LANDED, NOT SIGNED OFF``, and the distinction is the
whole point of this project's structure:

* **Unit 9**, the strict JSON reader and round-trip suite, at ``9639b77``.
* **Unit 10**, the ``provenance.rst`` report, at ``9317abc``.

**I did not read either diff, did not run their gates myself, and did not
inject a defect into either.** Every other unit in this phase carries all
three. What follows is therefore each agent's own account, which is a claim to
be checked and not evidence:

Unit 10 reported 59 report tests green, its package at 100% line and 100%
branch, PIT 121 mutations with 0 survivors, and all six of its own
falsifiability defects caught -- including item (d), the structural test that
enumerates 53 record components reflectively and fails when the report omits
one. It left a fully populated sample report for the Sphinx gate at
``cometgui-provenance/target/provenance-report-sample/provenance.rst``, with
``conf.py`` and ``index.rst`` beside it so the check is one command::

    .venv/bin/sphinx-build -n -W -b html \
        cometgui-provenance/target/provenance-report-sample /tmp/rst-sample-html

**Run that. I did not.** The sample is regenerated by the test suite, and a
clean-proof copy was left in the session scratchpad, which does not survive
this session.

Unit 10 also reported the finding that decides how RST values are escaped: of
the four value shapes an inline literal cannot carry, **two fail the Sphinx
gate silently**. A lone backtick and an empty value raise errors, but a value
with a leading space *builds clean* and renders with the markup gone and the
backticks visible. So the escaping convention could not be developed by
watching the build go red, which is why it is stated in the report's own
preamble.

**Unit 10's three survivors were reported killed; unit 9's six were reported
still open, with a precise diagnosis of each -- see** :ref:`p04-survivors`.
Neither claim has been confirmed from a quiet tree.

Unit 9 also left five design questions for whoever signs it off. They are
recorded here rather than answered, because answering them without reading the
diff would be the same mistake as signing the unit off without reading it:
whether ``InvalidManifestException`` should be a top-level type rather than
nested in ``ManifestReader``; whether the reader should attach the rejecting
exception as a *cause* (it deliberately does not, because ``FileHashes``,
``RunId`` and ``DateTimeParseException`` all quote the value they rejected,
which would put a hostile document's contents into a stack trace -- the cost
being that you lose the specific message); that ``json/package-info.java``
still describes only writing; that a run spanning the timestamp format's two
extremes cannot be serialised because ``millisBetween`` overflows a ``long``;
and that locale tags must be exactly canonical, so ``"en-us"`` is rejected,
because ``Locale.forLanguageTag`` never fails and silently returns
``Locale.ROOT`` for rubbish.

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

The seven gate items, with evidence and with what is missing
============================================================

Every command below is the one that produces the evidence. Every number is
from a noisy tree.

.. list-table::
   :header-rows: 1
   :widths: 4 22 12 62

   * - #
     - Gate item
     - Status
     - Evidence, command, and what is missing

   * - 1
     - Known MD5 and SHA-256 vectors, including the zero-byte file
     - **Met**
     - Every expected digest is a hand-typed literal. I recomputed all fourteen
       myself with GNU coreutils and a Python reimplementation of the test's own
       LCG pattern generator, and all matched to the character. The reference
       table was pinned in the work log *before* the module held a hashing
       class, so no expected value can have come from the code under test.
       ``mvn -pl cometgui-provenance -am test -Dtest=StreamingHashServiceTest``

   * - 2
     - 2 GB in one pass, bounded heap, digests matching independent values
     - **Met**
     - Run by me, not reported to me: ``bytes=2147483648 opens=1
       readCalls=8193 bytesDelivered=2147483648 heapBaseline=3958160
       heapPeak=4223232 heapGrowth=265072 heapLimit=4194304 samples=83``.
       Retained heap sampled post-collection, not an allocation count. The
       digests were computed by coreutils *and* OpenSSL before this code
       existed. A **permanent negative control** ships in the suite and prints
       ``huge-file-control: keptChunks=128 keptBytes=33554432
       heapGrowth=33570352``, so every build re-proves the bound bites while
       the leaky hasher's digests stay exactly correct.
       ``mvn -pl cometgui-provenance -am test -Dtest=HugeFileHashingTest``

   * - 3
     - Cache returns a value only when every attribute matches
     - **Met on POSIX**
     - Detected rather than survived: the key carries ``unix:ctime``, which the
       kernel bumps on both the write and the mtime restoration and which user
       space cannot forge. Where that evidence is absent the cache stores
       **nothing**. **On Windows this is a different algorithm that has never
       run** -- see :ref:`p04-divergence`.
       ``mvn -pl cometgui-provenance -am test -Dtest=CachingHashServiceTest``

   * - 4
     - A crash mid-run leaves a parsable log with usable history
     - **Met on POSIX**
     - Proved by tearing real files at several byte offsets, not by argument.
       The strongest evidence is the unit's own defect (f): replacing the
       newline terminator makes one crash turn the whole log into a single torn
       line and recover **zero** events. My injection made recovery stop at the
       first defect and failed on the middle-malformed-line case,
       ``expected: <[1, 3]> but was: <[1]>``.
       ``mvn -pl cometgui-provenance -am test -Dtest=EventLogCrashRecoveryTest``

   * - 5
     - Finalisation is atomic; an interrupted finalise never truncates
     - **Met on POSIX**
     - Proved by observation. Replacing ``ATOMIC_MOVE`` with
       ``Files.copy`` + ``delete`` made a concurrent reader observe **37
       truncated documents and 23 moments where the target did not exist**.
       Interruption is proved by interrupting. **The Windows half is unproven
       and is the most important item in** :ref:`p04-divergence`.
       ``mvn -pl cometgui-provenance -am test -Dtest=AtomicDocumentWriterTest``

   * - 6
     - Seeded secrets appear nowhere in JSON, RST or logs; the test greps
     - **PARTIAL**
     - The rule set half is done and strong: one rule set in
       ``org.cometgui.domain.secrets``, applied inside the JSON writer and the
       event log rather than at call sites, with a seeded corpus whose
       **carrier length is part of its coverage**. The JSON and event-log
       sweeps exist. **What is missing is unit 11**: the end-to-end test that
       generates all three artefacts and greps the files on disk. The RST half
       cannot exist until unit 10 does.

   * - 7
     - PIT reports no surviving mutation in hashing and redaction
     - **Met for those two packages, UNVERIFIED overall**
     - The hashing package was last measured at 70 mutations, 70 killed, 0
       survived; the moved secrets package at 52 mutations, 52 killed, 0
       survived in ``cometgui-domain``. Both were clean. **But the module-wide
       figure has not been re-taken from a quiet tree**, and the last full
       reading showed nine survivors in units 9 and 10's uncommitted code
       (:ref:`p04-survivors`). Run ``mvn -pl cometgui-domain install`` first or
       PIT resolves a stale jar.

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

Open PIT survivors -- a precise work list
=========================================

The last mutation run any agent made, reported by unit 9 and read from
``mutations.xml`` rather than the console, was **776 mutations, 767 KILLED, 7
SURVIVED, 2 TIMED_OUT, 0 NO_COVERAGE**. That run was still on a moving tree.
Six survivors are in unit 9's parser and one is in unit 8's reader.

**Two of the six are equivalent mutants, and the fix is to delete the code, not
to write a test.** That is worth saying plainly, because chasing an equivalent
mutant with a test is how a suite acquires assertions that cannot fail.

.. list-table::
   :header-rows: 1
   :widths: 22 20 58

   * - Site
     - Mutator
     - Diagnosis and the fix unit 9 identified but did not apply

   * - ``JsonReader:203``, ``JsonReader:256``
     - VoidMethodCall on ``skipWhitespace``
     - **Equivalent mutants.** Whitespace is already skipped before the loop
       and after every comma, so the call at the loop top is redundant.
       **Delete the two calls.** Do not add a test.

   * - ``JsonReader:228``
     - VoidMethodCall on ``leaveContainer``
     - Needs a document where a container closes and nesting continues after
       it: ``[{"a":1},{"a":1}, ...x70]``. Without the call, depth passes 64 and
       the parse fails.

   * - ``JsonReader:252``
     - VoidMethodCall on ``leaveContainer``
     - Same, via the empty-array early return: ``[[],[], ...x70]``.

   * - ``JsonReader:264``
     - VoidMethodCall on ``leaveContainer``
     - Same, via an array's normal close: ``[[1],[1], ...x70]``.

   * - ``JsonReader:273``
     - VoidMethodCall on ``skipWhitespace``
     - The array trailing-comma fixture is ``[1, 2,]`` with no space, so the
       call is not load-bearing. ``[1, 2, ]`` -- with a space before the
       bracket -- kills it. The object equivalent is already dead because that
       fixture is multi-line.

   * - ``ProvenanceEventLogReader$Recovery:310``
     - MathMutator
     - **Real, and its fix is landed at** ``892962e`` **but UNCONFIRMED.**
       ``charAt(cut - 1)`` mutated to ``charAt(cut + 1)`` survived because the
       only surrogate fixture was a *uniform* run of astral characters, where
       cut-1 and cut+1 are two apart and therefore always the same half of a
       pair -- the mutant is observationally identical. A single astral
       character alone in ASCII tells them apart, and that fixture is now in
       the tree. **Confirming the kill is the first PIT run whoever resumes
       should make.**

The two ``TIMED_OUT`` are loop-control mutants in ``JsonWriter`` and
``ProvenanceEventLogReader`` that hang rather than fail. PIT counts a timeout
as detected, and both are genuine infinite loops, so neither is a survivor.

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

Seven shapes of one defect
==========================

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

The remedy that generalises: **verification must include an audit of the
population, and that audit has to run beside the numbers rather than once.** A
census on a quiet tree finds nothing and proves nothing; it earns its place by
running on every build.

.. _p04-first-thing:

The first thing the next agent should do
========================================

**1. Re-take every number from a quiet tree.** Nothing in this document is
evidence for a gate. In order::

    git status --short                 # must be clean; if not, stop and find out why
    . ./tools/env.sh
    mvn -B -Dmaven.repo.local=_build/m2repo -pl cometgui-domain install -DskipTests
    mvn -B -Dmaven.repo.local=_build/m2repo -pl cometgui-provenance -am verify
    mvn -B -Dmaven.repo.local=_build/m2repo -pl cometgui-provenance -am \
        org.pitest:pitest-maven:mutationCoverage
    bash <the census from the work log> cometgui-provenance

The ``install`` is not optional; see :ref:`p04-traps`. Read the census output
before reading the coverage percentage: a percentage over an incomplete
population is worse than no percentage.

**2. Sign off units 9 and 10 properly, or send them back.** They are committed
and their authors' accounts are good, but no one has read the diffs, re-run
their gates, or injected a defect into them. Signing off means all three. In
particular:

* run the Sphinx command in :ref:`p04-reverted` over unit 10's sample report --
  the RST half of gate item 6 and the whole of the report's validity rest on
  it, and **it has never been run by anyone but the agent that wrote it**;
* re-check the nine PIT survivors in :ref:`p04-survivors`, all reported killed
  and none confirmed;
* for unit 9, ask the question its brief asked: whether the round-trip suite
  alone could catch a reader that reads the wrong key. It cannot, and the
  hand-typed parse-and-assert suite is the part that carries the proof.

**3. Then, and only then, do units 11, 12 and 13**, which are not started:

* **Unit 11** is gate item 6's missing half: one end-to-end test that builds a
  manifest and an event log carrying the seeded corpus, writes JSON, RST and
  the log, and **greps the generated files on disk**. Build it on the corpus in
  ``cometgui-domain``'s ``SeededSecretCorpusTest``, and read that class's
  Javadoc first -- it records the two blind spots a sweep has.
* **Unit 12** is the documentation: ``docs/reference/provenance_format.rst``
  and ``docs/developer/provenance_schema.rst`` are still Phase 01 stubs. The
  schema they must describe is settled and pinned in ``ManifestWriterTest``'s
  hand-typed document.
* **Unit 13** is the final gate run, including ``scripts/verify-all-gates.sh``,
  which takes about twelve minutes and was deliberately not run here.

**4. Do not run two phases in one tree.** Serialise, or accept that every
number is suspect. That lesson cost this phase more than any technical problem
in it.

**5. Read ``handoffs/PHASE-04-worklog.rst``.** Every sign-off entry names the
defect that was injected and the exact failure text it produced. Those are the
proofs; this document is only the map.
