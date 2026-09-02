=====================================================
PHASE-04 work log -- Hashing and Provenance Core
=====================================================

:Phase: 04
:Phase orchestrator: Phase-04 orchestrator subagent (session 04)
:Started: 2026-08-31

Maintained by the phase orchestrator as the phase runs. A unit is not done
until it carries a sign-off entry naming what was run and what was observed --
"agent reported success" is not a sign-off.

.. contents:: Contents
   :depth: 1
   :local:

Baseline observed before any work started
=========================================

``git status`` clean at ``e97d863``. ``bash scripts/build.sh`` printed
``11/11 stages OK in 182 seconds. Maven local repository: _build/m2repo`` and
``BUILD OK``. The checkout is at
``/mnt/10TBdrive/home/mriffle/work/comet-gui``; ``/workspace`` no longer
exists, so every path written by an earlier phase that says ``/workspace`` is
stale text, not a second tree.

``cometgui-provenance`` at that commit holds five ``package-info.java`` files
and nothing else: ``org.cometgui.provenance`` and its ``hashing``, ``events``,
``manifest`` and ``report`` subpackages. ``org.cometgui.domain.ports``
holds the two ports this phase implements -- ``HashService`` (one method,
``FileHashes hash(Path)``) and the ``FileHashes`` record, which validates both
digests and cannot represent a file hashed only one way.

Engineering decisions taken by the orchestrator before decomposing
==================================================================

These are engineering choices, not ``D-`` items. Later phases inherit them.

No JSON library; a hand-written canonical writer and a strict reader
    ``cometgui-provenance`` depends on ``cometgui-domain`` and nothing else,
    and the project has no JSON dependency anywhere. Adding one is possible --
    the network is up and Gson happens to be in ``_build/m2repo`` as a
    SpotBugs plugin dependency -- but it is rejected here for three reasons.
    The provenance record is the artefact the whole product exists to produce,
    so its byte-level form should be *chosen*, not inherited from a library's
    defaults; a canonical writer with a fixed field order and sorted maps is
    what makes a schema documentable and a hand-typed expected literal
    possible; and a library's serialiser would sit outside PIT's reach while
    the gate demands mutation coverage of exactly this code. The reader is
    written to accept only the shape this project emits and to reject
    everything else, so it stays small.

Redaction is applied at the serialiser, not at the call sites
    The gate greps generated artefacts for a seeded secret corpus. A design in
    which each caller remembers to redact is a design in which one caller
    eventually does not. Every string that reaches JSON, RST or the event log
    passes through one ``SecretRedactor`` instance built from one
    ``SecretRules`` rule set, invoked inside the writers themselves. Adding a
    new field to the manifest therefore cannot open a new leak path.

``ToolCommand`` already models the redactable path and is not re-litigated
    ``org.cometgui.domain.ports.ToolCommand`` deliberately has no shell-command
    accessor, prints environment *names* only from ``toString()``, and exposes
    values through ``environment()`` as "the deliberate, redactable path the
    provenance recorder uses". This phase is that recorder: it reads
    ``environment()`` and redacts, and it renders the argument array with
    ``displayString()`` after redacting the arguments.

Determinism is a schema property
    Field order is fixed and declared; maps are sorted by key; timestamps are
    ISO-8601 in UTC at a fixed precision; numbers are integers or plain
    decimals with no locale involvement. Without this, no expected literal can
    be hand-typed and ``R-PROV-04``'s locale requirement would be a comment
    rather than a property.

.. _p04-reference-digests:

Independently computed reference digests
========================================

Computed by the orchestrator before any code existed, so that no expected value
in this phase can have come from the code under test. Every value below was
produced twice, by two implementations that share no code with each other or
with CometGUI: GNU coreutils ``md5sum``/``sha256sum`` and OpenSSL
``openssl dgst``. The short vectors additionally match the published RFC 1321
and NIST vectors.

.. list-table:: Short vectors
   :header-rows: 1
   :widths: 22 39 39

   * - Content
     - MD5
     - SHA-256
   * - zero bytes
     - ``d41d8cd98f00b204e9800998ecf8427e``
     - ``e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855``
   * - ``a``
     - ``0cc175b9c0f1b6a831c399e269772661``
     - ``ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb``
   * - ``abc``
     - ``900150983cd24fb0d6963f7d28e17f72``
     - ``ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad``
   * - ``message digest``
     - ``f96b697d7cb7938d525a2f31aaf161d0``
     - ``f7846f55cf23e14eebeab5b4e1550cad5b509e3348fbc4efa3a1413d393cb650``
   * - ``abcdefghijklmnopqrstuvwxyz``
     - ``c3fcd3d76192e4007dfb496cca67e13b``
     - ``71c480df93d6ae2f1efad1447c66c9525e316218cf51fc8d9ed832f2daf18b73``
   * - ``The quick brown fox jumps over the lazy dog``
     - ``9e107d9d372bb6826bd81d3542a419d6``
     - ``d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592``
   * - one million ``a``
     - ``7707d6ae4e027c70eea2a935c2296f21``
     - ``cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0``

The 2 GB corpus for gate item 2 is defined here rather than by the code that
hashes it. It is a 65521-byte block, ``block[j] = (j * 251 + 17) mod 256``,
repeated until the file is exactly 2 147 483 648 bytes long. 65521 is prime and
therefore shares no factor with any power-of-two read buffer, so a defect that
mis-handles a chunk boundary changes the content it digests rather than
landing on an identical repeat; the file length is not a multiple of the block,
so the last block is partial. The reference values, from both tools, are

``MD5 = 222ed00f986369a06082191a1300d095``

``SHA-256 = afba2dcb851c0337d7f364e52c88ac7590c5e5b29c6a5c1739cfda4b59ad3be3``

and the generator that produced them is nine lines of Python that never touch
this repository.

.. _p04-measured-before:

What the gates measured before this phase, so that "green" can be checked
=========================================================================

A coverage or mutation report is only evidence if the code in question was in
the sample. Phase 01 shipped an ArchUnit rule that reported 8/8 while
evaluating nothing, and the same shape is available here for free: a gate whose
package list does not reach a new subpackage measures nothing and passes. These
are the numbers as they stood at ``effeded``, before ``cometgui-provenance``
held a single class with code, so that the after-numbers can be compared rather
than believed.

.. list-table::
   :header-rows: 1
   :widths: 34 33 33

   * - Measurement
     - ``cometgui-provenance``
     - ``cometgui-domain`` (for scale)
   * - ``target/site/jacoco/jacoco.xml``
     - **absent** -- ``scripts/build.sh`` prints ``inert  cometgui-provenance
       no classes with code yet``
     - present
   * - JaCoCo ``<class name=`` entries
     - 0
     - 22
   * - JaCoCo report ``CLASS`` counter
     - none
     - ``missed="0" covered="12"``
   * - JaCoCo report ``LINE`` counter
     - none
     - ``missed="0" covered="301"``
   * - JaCoCo report ``BRANCH`` counter
     - none
     - ``missed="0" covered="144"``
   * - ``target/pit-reports/mutations.xml``
     - **absent**; the POM has no ``cometgui.mutation.skip`` override, so the
       parent's ``true`` applies and PIT does not run here at all
     - present, 152 mutations, 152 killed
   * - PIT mutations by package
     - none
     - ``build`` 22, ``log`` 23, ``platform`` 65, ``ports`` 37, ``run`` 5

Two consequences follow, and both are checked at the gate rather than assumed.
The module's mutation switch must be turned on by this phase, or PIT will
produce no report for it and ``scripts/build.sh`` will say so. And every
subpackage this phase creates must appear in the per-package mutation counts
above's equivalent for ``cometgui-provenance``: PIT's ``targetClasses`` entry is
``org.cometgui.provenance.*``, whose glob is expected to reach subpackages, but
that expectation is verified by counting mutations per package in the produced
report, not by reading the POM.

Work units
==========

Thirteen units in four waves. Waves are parallel only where the units cannot
touch the same files; the file list for each unit is fixed by the brief.

.. list-table::
   :header-rows: 1
   :widths: 5 12 33 16 34

   * - #
     - Wave
     - Unit and acceptance conditions
     - Rules served
     - Sign-off: what was run, what was seen, date

   * - 1
     - A
     - **Streaming single-pass hasher.**
       ``org.cometgui.provenance.hashing`` -- a ``HashService`` that reads a
       file once, in bounded chunks, updating both digests from the same
       buffer. Accepts: hand-typed known vectors including the zero-byte file;
       a chunk-boundary proof that the single pass and two separate passes
       agree; failure behaviour for a missing file and a directory.
     - ``R-PROV-01``, ``R-PROV-03``
     - **SIGNED OFF 2026-08-31** at ``185fbc1``, after one rejection
       (:ref:`p04-unit1-rejection`). I recomputed all fourteen expected
       digests myself from the test's own fixtures using GNU coreutils and a
       Python reimplementation of its LCG pattern -- the seven published
       vectors, the four buffer-straddling lengths, the 256 ascending byte
       values and the NUL/0xFF fixture -- and every one matched to the
       character. I re-injected my two-pass defect in its stronger form, two
       opens *through* the new seam: group 8 failed 7 of 13 with
       ``open() calls ==> expected: <1> but was: <2>``,
       ``streams handed to the hasher ==> expected: <1> but was: <2>``,
       ``bytes read from the file, summed over every stream opened ==>
       expected: <1> but was: <2>`` and
       ``close() calls ==> expected: <1> but was: <2>`` -- while **every
       digest assertion still passed**, which is the finding restated as
       evidence. Reverted, then ``mvn -pl cometgui-provenance -am verify``
       plus PIT: ``Tests run: 52, Failures: 0, Errors: 0``,
       ``All coverage checks have been met``, ``BugInstance size is 0``,
       ``0 Checkstyle violations``, ``BUILD SUCCESS``. Measured counts moved
       from **absent** to: JaCoCo line 23/23, branch 4/4, 1 class in
       ``org/cometgui/provenance/hashing``; PIT 8 mutations, 8 killed, 0
       survived, 0 no-coverage, all 8 in
       ``org.cometgui.provenance.hashing.StreamingHashService`` -- which is
       also the proof that PIT's ``org.cometgui.provenance.*`` glob does
       reach a subpackage.

   * - 2
     - A
     - **Durable, atomic file writing.**
       ``org.cometgui.provenance.io`` -- write-temp, fsync, ``ATOMIC_MOVE``,
       with the temp file removed on any failure. Accepts: a fault-injected
       write leaves the target absent or unchanged and never truncated; a
       concurrent reader never observes a partial file; an interrupted write
       is proved by interrupting, not by asserting that rename is atomic.
     - ``R-PROV-05``
     - **SIGNED OFF 2026-08-31** at ``a6f0b48``. I ran
       ``mvn -pl cometgui-provenance -am verify org.pitest:...:mutationCoverage``
       myself: ``Tests run: 87, Failures: 0, Errors: 0``,
       ``All coverage checks have been met``, ``BugInstance size is 0``,
       ``0 Checkstyle violations``, ``BUILD SUCCESS``. Then I injected a defect
       the agent had not tried, and the one gate item 5 is actually about:
       ``Files.move(tmp, target, ATOMIC_MOVE, REPLACE_EXISTING)`` replaced by
       ``Files.copy(tmp, target, REPLACE_EXISTING); Files.delete(tmp);``, which
       truncates the target and then streams into it. The concurrent-reader
       test failed with ``the reader saw a document of 65536 bytes, which is
       neither of the two written ==> expected: <0> but was: <37>`` and ``the
       target vanished mid-rename ==> expected: <0> but was: <23>``. **That is
       gate item 5 proved by observing a torn file, not by reasoning that
       rename is atomic.** Reverted and re-ran green. PIT, with **no**
       ``-D`` override on the command line, produced 28 mutations, 28 killed,
       0 survived across all three classes, which also closes the carried
       question of whether the POM switch was live or merely set.

   * - 3
     - A
     - **The secret rule set.**
       ``org.cometgui.provenance.redaction`` -- one ``SecretRules`` plus one
       ``SecretRedactor`` covering free text, argument arrays and
       environments. Accepts: a seeded corpus of tokens, passwords, bearer
       headers and credential-bearing URLs is redacted in all three shapes;
       the marker is a fixed literal; redaction is idempotent; non-secret
       content survives unchanged.
     - ``R-SEC-03``
     - **SIGNED OFF 2026-08-31** at ``2b4575d``, after two rework rounds. I ran
       the module gate myself: ``Tests run: 309, Failures: 0, Errors: 0``,
       ``All coverage checks have been met``, ``BugInstance size is 0``,
       ``0 Checkstyle violations``, PIT ``130 mutations, 130 killed, 0
       survived`` with redaction contributing 52 and no survivor anywhere.
       Two injections of my own. (1) A plausible optimisation,
       ``if (text.length() < 32) return text;`` as the first statement of
       ``redactText``: six failures including ``expected: <password:
       [REDACTED]> but was: <password: swordfish-42>`` -- **but
       ``SeededSecretCorpusTest`` passed, 8 tests, 0 failures**, which is the
       finding described at :ref:`p04-sweep-blindness` and is now fixed.
       (2) A classic off-by-one, ``index < argv.size() - 1``: ten failures
       across four classes including the corpus test, e.g. ``expected: <[-k,
       [REDACTED]]> but was: <[-k]>``. Reverted; the restored file's SHA-256
       ``010251f5...`` matches the value the unit reported independently.

   * - 4
     - A
     - **Manifest record types.**
       ``org.cometgui.provenance.manifest`` -- file, tool, application and
       environment records, the schema version, and the run/file status
       marking (``complete``, ``partial``, ``failed``, ``cancelled``). Pure
       value types, no I/O. Accepts: every record validates its own
       invariants; a file record cannot exist without both digests; the JVM
       locale and time zone have declared homes.
     - ``R-PROV-01``, ``R-PROV-04``, ``R-PROV-05``
     - **SIGNED OFF 2026-08-31** at ``fea6bd6``, after one rework round that
       added the stage id, the ``FORMAT`` locale and the settings-key shape
       rule (:ref:`p04-schema-decisions`). Same module gate run as unit 3
       above; ``manifest`` contributes 50 of the 130 mutations with no
       survivors. My own injection went at the newest code, capturing the
       format locale from ``Locale.Category.DISPLAY`` instead of ``FORMAT``:
       two failures, ``expected: <en_US> but was: <tr_TR>`` and -- the one
       that matters -- ``expected: <.> but was: <,>``. That second assertion
       is on the actual decimal separator the recorded locale produces, so the
       test proves the ``R-PARAM-11`` consequence the requirement exists for
       rather than proving that a getter returns a field. Reverted and
       re-verified clean.

   * - 5
     - B
     - **The input-hash cache.**
       ``org.cometgui.provenance.hashing`` -- keyed on canonical path, size,
       modification time and file identity, revalidated on every use,
       invalidated by any attribute change, bypassable. Accepts: a mutation
       that preserves size *and* mtime is still detected or rehashed, and the
       recorded hash is a hash of the content actually read.
     - ``R-PROV-02``
     - **SIGNED OFF 2026-08-31** at ``7e269dc``. The gate item is met by
       DETECTION, not by luck: the cache keys on a fifth attribute beyond the
       four ``R-PROV-02`` names, the POSIX inode change time ``unix:ctime``,
       which the kernel bumps on both the write and the timestamp restoration
       and which user space cannot set. Where identity or ctime is absent
       (Windows, or a file inside a zip -- tested for real through
       ``FileSystems.newFileSystem``) nothing is cached at all, which is
       "when in doubt, rehash" implemented literally rather than quoted. The
       settling rule behind it is grounded in measurement, not assumption:
       two back-to-back writes to one file left an *identical* ctime in 184
       of 200 trials on this host, because timestamps are only as fine as the
       kernel tick. My own injection went at a shape the unit's twelve did not
       cover, dressed as Windows compatibility --
       ``fileIdentity == null || Objects.equals(...)`` on both new attributes,
       so a cached entry with a null attribute matches anything. Two failures:
       ``absent vs present identity ==> expected: <false> but was: <true>``
       and the same for ctime. Restored and verified with ``sha256sum -c``
       rather than by eye. Full module gate, run by me: ``Tests run: 554,
       Failures: 0, Errors: 0``, 100% line and 100% branch, ``BugInstance size
       is 0``, PIT ``hashing`` 70 mutations, 70 killed, 0 survived.

   * - 6
     - B
     - **The 2 GB bounded-heap proof.** Test-only, in the hashing package. A
       2 GB temporary file is hashed in one pass; both digests are compared
       against values computed by ``md5sum`` and ``sha256sum`` and hand-typed
       into the test; retained heap is asserted, not allocation count.
     - ``R-PROV-01``, ``R-PROV-03``
     - **SIGNED OFF 2026-08-31** at ``3119189``/``6cbcad4``. I ran the test
       myself and read its own printout: ``bytes=2147483648 opens=1
       readCalls=8193 bytesDelivered=2147483648 heapBaseline=3958160
       heapPeak=4223232 heapGrowth=265072 heapLimit=4194304 samples=83`` in
       14.1 s, with the class at 29.6 s. **Retained heap, not an allocation
       count**, sampled post-collection from a watchdog: growth of one buffer,
       15.8x under a bound of 16x ``BUFFER_SIZE``. Both digests are the values
       I computed with coreutils and OpenSSL before this module had a hashing
       class. The unit shipped a **permanent negative control** rather than
       only reverting its defect, and I ratified that: the same run prints
       ``huge-file-control: keptChunks=128 keptBytes=33554432
       heapGrowth=33570352 heapLimit=4194304``, so every build re-proves that
       the bound bites at 32 MiB *while the leaky hasher's digests are exactly
       correct*. My own injection went at the anti-vacuity guard, which is the
       defect class this project keeps shipping: a watchdog that returns
       before taking a single sample. Both tests failed with ``the
       retained-heap watchdog took only 1 samples, fewer than the 8 a measured
       run needs; its peak means nothing`` -- so a heap bound with no
       observations behind it cannot pass. Restored, ``sha256sum -c`` OK,
       marker count 0, and no 2 GB corpus anywhere on disk afterwards.

   * - 7
     - B
     - **Canonical JSON writer and manifest serialisation.**
       ``org.cometgui.provenance.manifest`` -- deterministic JSON with a fixed
       field order, sorted maps and redaction applied inside the writer.
       Accepts: the whole document is pinned as a hand-typed literal; the
       finalise path uses unit 2's atomic writer.
     - ``R-PROV-05``, ``R-SEC-03``
     - **SIGNED OFF 2026-08-31** at ``b1afb68``. The whole document is pinned as
       a hand-typed text block, and I validated that literal with an
       implementation that is not ours: extracted it and ran
       ``python3 -m json.tool``, which parsed it, with ``schemaVersion`` first
       and equal to 1 and the top-level key order
       ``[schemaVersion, run, application, settings, tools, files]``. My own
       injection went after a defect class the unit's own six did not name:
       ``out.append(Long.toString(value))`` replaced by
       ``String.format("%d", value)``, which is locale-sensitive only under a
       locale whose numbering system is not ASCII. It failed --
       ``expected: <1234567890123> but was: <?????????????>`` -- because the
       unit had already thought of it and tests under
       ``th-TH-u-nu-thai`` as well as Turkish and German. A German or Turkish
       locale alone would NOT have caught it, since neither changes the digits
       of a grouping-free integer. Restored and verified with ``sha256sum -c``.
       **Reworked at ``e10274c``** on my instruction, because the unit had left
       the derived duration out of the document and this phase claims to prove
       ``AC-PRV-05`` -- "start, end, duration and exit code are recorded for
       every process" -- and the file *is* the record. The unit's objection was
       sound (a third number that can contradict the two it comes from) and it
       is answered by deriving at write time rather than storing:
       ``durationMillis`` is emitted next to ``end`` and is a component of no
       record type. I checked the result independently, recomputing all three
       values with python ``datetime`` from the documents' own rendered
       timestamps: run 2039750, comet 1950500, percolator 91125, all agreeing,
       and the running run carrying ``null`` for both ``end`` and
       ``durationMillis``. One subtlety the unit found and unit 9 was told:
       the value derives from the millisecond-TRUNCATED instants, because the
       pinned run starts at ``09:14:00.250999999Z`` and renders ``.250Z`` --
       raw subtraction gives 2039749, and only 2039750 is checkable against
       the document a reader can see.

   * - 8
     - B
     - **Appendable event log.**
       ``org.cometgui.provenance.events`` -- typed events, an append-only
       writer that flushes each record, and a reader that recovers every
       complete event from a torn file. Accepts: a simulated crash mid-run
       leaves a parsable log whose recovered events are asserted by content.
     - ``R-PROV-05``, ``AC-PRV-06``
     - **SIGNED OFF 2026-08-31** at ``8f00cae``/``4c33b79``, after two rework
       rounds. Gate item 4 is proved by tearing a real file, not by argument.
       The unit's own best evidence is defect (f): replacing the newline
       terminator with a comma makes one crash turn the whole log into a single
       torn line and recover **zero** events -- which proves the line-oriented
       design rather than asserting it. My first injection made recovery stop
       at the first defect instead of continuing past it, dressed as "once the
       log is damaged the rest cannot be trusted": two failures, the telling one
       being ``expected: <[1, 3]> but was: <[1]>`` -- a malformed line in the
       MIDDLE with good events after it, which is exactly the "usable history"
       the gate item is about. My second went at the rework's own reasoning,
       swapping clean-then-bound for bound-then-clean with a plausible
       efficiency excuse; it failed with ``a fragment of the seeded token
       survived the cut, so the detail was truncated before it was redacted``,
       a message written for the reasoning rather than the outcome. Restored,
       ``sha256sum -c`` OK, marker count 0.

   * - 9
     - C
     - **Strict JSON reader and the manifest round-trip suite.** Accepts:
       parsing is pinned against hand-typed JSON, not against output the
       writer produced; malformed documents are rejected with a located
       message; a property suite over generated manifests round-trips.
     - ``R-PROV-05``
     - **LANDED, NOT SIGNED OFF** at ``9639b77``. Committed green by its
       author minutes before the phase paused. I did not read the diff, run
       its gate, or inject a defect into it.

   * - 10
     - C
     - **The RST report.**
       ``org.cometgui.provenance.report`` -- ``provenance.rst`` generated from
       the same model as the JSON. Accepts: expected RST pinned as a hand-typed
       literal; every fact in the report is traceable to a manifest field;
       redaction applied through the same rule set.
     - ``R-PROV-05``, ``R-SEC-03``
     - **LANDED, NOT SIGNED OFF** at ``9317abc``. Same caveat as unit 9, and
       its sample report for the Sphinx gate has never been checked by anyone
       but its author.

   * - 11
     - D
     - **The seeded-secret grep proof.** An end-to-end test that builds a
       manifest and an event log carrying the whole seeded corpus, writes
       JSON, RST and the log, then greps the generated files on disk for every
       seeded secret. Accepts: zero occurrences, and the test names the file
       and offset when it finds one.
     - ``R-SEC-03``, ``AC-PRV-09``
     - **NOT STARTED.** Gate item 6's end-to-end half.

   * - 12
     - D
     - **Documentation.** ``docs/reference/provenance_format.rst`` (the
       versioned schema) and ``docs/developer/provenance_schema.rst`` (the
       model behind it). Accepts: ``sphinx-build -n -W`` clean; every field in
       the document exists in the code and every field in the code appears in
       the document, checked by a test rather than by reading.
     - ``R-PROV-05``, ``R-DOC-03``
     - **NOT STARTED.** Both pages are still Phase 01 stubs.

   * - 13
     - D
     - **Gate enablement and falsifiability.** The module's mutation switch,
       the coverage gate, PIT survivor triage, and the injected-defect
       evidence for all seven gate items. Orchestrator work, not delegated.
     - ``R-TEST-02``
     - **PARTLY DONE.** The module's mutation switch is on and verified live
       (``4c16864``). The final clean run and ``verify-all-gates.sh`` were
       deliberately not run: the tree is not quiet.

.. _p04-schema-decisions:

Schema decisions taken at unit 4, which later phases inherit
============================================================

Unit 4 raised six design questions rather than answering them alone. All six
are recorded here because a schema decision is cheap before unit 7 pins the
JSON and costs a schema-version bump afterwards.

Optional artefact identity, and an inverse wire-name lookup
    Both accepted as submitted. A *local* binary the user pointed at has no
    upstream artefact, and a required field would put a ``"local"`` sentinel --
    a lie -- into the record; the ``managed`` flag already carries that fact.
    ``fromWireName`` lives beside the wire names so that unit 9's reader cannot
    grow a second, drifting table.

A stage identifier goes in **now**
    ``ToolRecord`` gains ``Optional<String> stageId``. The phase document's own
    purpose statement decides this: the provenance model is "written before the
    stages that emit events, so that no stage can be built without recording
    itself. Provenance retrofitted at the end is provenance with holes." It
    holds the ``StageTag.id()`` string rather than the domain type, so the
    manifest package stays free of the workflow's types, and it is absent for
    an invocation outside any stage such as a capability probe.

Settings keys are validated in shape, not invented in name
    Unit 4 was right that only ``percolator.seed`` being pinned invites exactly
    the drift ``AC-PRV-10`` describes. But this phase does not own the
    semantics of the q-filter or Limelight settings, and a wrongly pinned key
    is worse than an unpinned one. So the *shape* is gated instead: every key
    must match ``[a-z0-9]+(\.[a-z0-9-]+)+``, rejected by name otherwise, and
    ``ProvenanceSchema`` documents that each phase pins its own constant
    beside the seed. Later phases get a rule they cannot drift from without
    failing a test.

Both locales are recorded, not one
    The sharpest question in the phase so far. ``R-PROV-04`` says to record
    "the JVM default locale", and gives its reason: "precisely because locale
    can affect serialisation (``R-PARAM-11``)". But the category that actually
    governs number formatting is ``Locale.Category.FORMAT``, so a record
    carrying only ``Locale.getDefault()`` records the thing the requirement
    *names* while missing the thing it is *for*. ``ApplicationRecord``
    therefore carries both, and the test sets them apart with
    ``Locale.setDefault(Locale.Category.FORMAT, ...)`` alone -- an assertion
    that is impossible to pass with a single field. They agree today because
    nothing in the product separates them; the record does not assume it.

An out-copy defect is gated by SpotBugs, not by JUnit -- and that is honest
    Unit 4 injected the defect I asked for, returning the field instead of a
    copy from ``ToolRecord.capabilities()``, and reported that **the whole
    suite passed**. It is right: the field is already an unmodifiable sorted
    set, so returning it is behaviourally indistinguishable. What caught it was
    ``mvn verify``'s SpotBugs step, ``EI_EXPOSE_REP ... may expose internal
    representation``. No JUnit guard was added, because ``List.copyOf`` on an
    already-immutable list returns the same instance and ``assertNotSame``
    would fail on *correct* code. The gate is real; it is simply a static
    analyser rather than a test, and that is worth knowing before someone
    "strengthens" it.

.. _p04-sweep-blindness:

The finding that a "no secret survives" sweep is the weak point, not the rules
==============================================================================

Three defects in unit 3 mattered, and **all three were in the shape of an
assertion rather than in the production rules**. Gate item 6 rests on a sweep
that greps generated artefacts for a corpus of seeded secrets, so this is the
single most load-bearing lesson of the phase and unit 11 inherits it.

A sweep proves the ABSENCE OF A STRING, not the PRESENCE OF REDACTION. It is
only as strong as the shapes *and the sizes* of the inputs it is given, and it
is blind to at least three things unless they are deliberately covered. The
third -- occurrence count -- was found by tier 1 at sign-off; see
:ref:`p04-third-blind-spot`.

**Blind spot 1: a partial rewrite.** ``contains(secret)`` is defeated by one
changed character. The unit found this itself, by injection: with a registered
PEM key, an earlier pattern rule rewrote the ``-----BEGIN`` line, the literal
that the registry was matching on no longer occurred, and **62 characters of
key material survived** with the registry loaded and the sweep green. The
repair was to run the literal pass *first as well as last*, and to hold the
key's three lines as separate corpus entries so a rule that eats only the
header still fails.

**Blind spot 2: a leak conditioned on the input's size.** I injected
``if (text.length() < 32) return text;`` at the top of ``redactText`` -- the
sort of optimisation a maintainer writes in good faith. Six assertions failed
in ``SecretRedactorTest``, including ``password: swordfish-42`` in clear. But
``SeededSecretCorpusTest`` **passed, 8 tests, 0 failures**, because every
carrier in the corpus was comfortably over 32 characters. The corpus now
carries one deliberately short carrier per rule family, the shortest 12
characters and the longest 23, and -- the part that keeps it fixed --
``everyShortCarrierIsActuallyShort`` asserts the length property directly, so
that someone later "tidying" the examples into realistic longer ones fails
instead of silently reopening the hole. Against the repaired corpus my defect
fails three ways, naming the secret and the carrier.

**A boundary of the name rule, recorded rather than papered over.** I suggested
``pw=swordfish-42`` as the short assignment carrier. It is not covered by the
pattern rules at all: ``pw`` and ``pass`` are not name keywords, ``password``
and ``passwd`` do not contain them as substrings, and ``pwd`` is deliberately
excluded because ``PWD`` is the shell's working directory. The unit used
``auth=`` for the pattern-covered case and kept ``pw=`` as a registry-only
carrier, which turns a surprise into a documented boundary. I declined to add
``pw`` as a keyword: name matching is by substring, so ``pw`` would match
``PWD`` and redact the working directory out of every provenance record.

.. _p04-injection-clobber:

An injection that silently stops injecting reports a PASS
=========================================================

Unit 5 found this and it is the most dangerous mechanical hazard of the phase.
Sibling subagents share one scratchpad directory. The unit wrote its injection
script there as ``inject.py``; an agent in another module wrote its own
``inject.py`` to the same path mid-run; two of unit 5's injections then ran the
wrong script, **changed nothing, and the suite went green with the defect
supposedly present**.

This is the project's signature failure in a new costume. Phase 01's ArchUnit
rule evaluated nothing; Phase 02's identifiers were compared against
themselves; here it is the *defect* rather than the *assertion* that stopped
working. The sentence "I injected X and the tests still passed" is exactly the
sentence that means "no defect was present", and it is indistinguishable from
"the gate is weak" unless the edit is confirmed to have landed. The unit caught
it only because two defects that had previously FAILED suddenly passed, which
is the one pattern that cannot be explained away.

**The rule this phase now works to, and which the next agent should inherit.**
An injection is evidence only if the edit is verified before the test result is
believed. Every injection in this phase asserts ``count(old) == 1`` before
writing, prints a marker, and has the marker grepped back out of the target
file before anything is run; every restore is checked with ``sha256sum -c``
rather than by eye. Per-agent subdirectories are not optional in a shared
scratchpad.

.. _p04-key-namespaces:

Two key namespaces, and why only one of them needs a namespace
==============================================================

The schema has two places where a later phase invents key names: the flat
``settings`` map, and an event's payload. Unit 4 raised the first, unit 8 the
second, and the phase gates the SHAPE of both rather than inventing names it
does not own the semantics of. But the two rules differ, and unit 8's argument
for the difference is better than the one I offered, so it is recorded in its
terms rather than mine.

I had said only that ``status`` is "genuinely global" while ``percolator.seed``
is "genuinely namespaced" -- an observation, not a rule. The rule is this. **A
settings key needs two segments because the settings map is one flat dictionary
shared by every phase in a run** -- Percolator's seed, the Limelight conversion
parameters and the result view's q filters all live in the same map -- so a
bare ``seed`` is ambiguous about which tool owns it and the first segment is
what disambiguates it. **A payload is not that.** It is scoped to a single
event that already carries its ``ProvenanceEventType``, so ``stage`` inside a
``stage.started`` event cannot collide with anything and ``stage.name`` would
only restate the type in the key.

So a payload key may be a single bare lower-case segment as well as the dotted
form, while a settings key may not. Everything that was actually doing the work
is kept in both: lower-case ASCII, digits, dots and hyphens, no underscore, no
camel case, no empty segment, no leading or trailing dot. ``runId``, ``run_id``,
``RUN.ID``, ``run.``, ``.id``, ``run..id`` and ``""`` are rejected in both.

The payload rule is *composed from* the settings constant rather than copied,
and a test asserts the composition still contains it -- so a later
copy-and-edit fails rather than merely looking untidy. Unit 8 proved that
assertion rather than trusting it, by drifting the copy a single character
(``[a-z0-9-]`` to ``[a-z0-9_-]``, quietly allowing underscores) and watching
``the payload rule no longer contains the settings rule ==> expected: <true>
but was: <false>``.

.. _p04-encoding:

Two environment findings a later phase must not lose
====================================================

**A scientist with an accent in their directory name cannot run this product,
and it has nothing to do with provenance.** Unit 7 hit it and I confirmed it
myself rather than relaying it. This JVM reports::

    file.encoding   = UTF-8
    native.encoding = ANSI_X3.4-1968
    sun.jnu.encoding = ANSI_X3.4-1968

because the environment sets no ``LANG``. ``sun.jnu.encoding`` is what the JDK
uses to turn a Java string into a filesystem path, so::

    Path.of("/data/protéomique/x.mzML")
    -> java.nio.file.InvalidPathException: Malformed input or input contains
       unmappable characters: /data/prot?omique/x.mzML

and under ``LANG=C.UTF-8`` the same call returns the path unharmed. This is
*before* any CometGUI code runs; no amount of correct handling downstream can
recover it.

.. warning::

   **The obvious remedy does not work, and this correction matters more than
   the finding.** This work log first said Phases 14 and 16 should pass
   ``-Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8`` in the ``jpackage``
   java-options. The main orchestrator tested that claim rather than accepting
   it, and I then reproduced it myself::

       java -Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8 ...
         file.encoding    = UTF-8
         sun.jnu.encoding = ANSI_X3.4-1968      <- the flag was ignored
         THREW: java.nio.file.InvalidPathException: Malformed input or input
                contains unmappable characters: /data/prot?omique/x.mzML

   ``sun.jnu.encoding`` is resolved by the JVM from the process environment
   *before* system properties are applied, so the flag is **accepted without
   error and does nothing**. ``-Dfile.encoding=UTF-8`` appeared to work only
   because it was already UTF-8, which makes the pair look half-effective while
   being entirely inert for the failure it was supposed to fix.

   That is this project's signature defect in a new place: **a remedy that
   cannot work, adopted because nothing goes red when you apply it.** Someone
   would have put those options into Phase 16's java-options, watched a clean
   build, and shipped a product that still cannot open an accented path.

   What actually works is the **environment, set before the JVM starts** --
   verified here::

       env LC_ALL=C.UTF-8 java ...   -> sun.jnu.encoding = UTF-8, path OK
       env LANG=C.UTF-8 java ...     -> same

   So the requirement for Phases 14 and 16 is **"the launcher must start the
   JVM in a UTF-8 locale"**, which is a launcher and packaging problem that
   ``jpackage --java-options`` cannot express. A CI runner that does not export
   a UTF-8 locale will also silently skip coverage a user's machine needs.

Unit 7 handled it honestly rather than hiding it: the non-ASCII
text and the emoji surrogate pair moved into tool *warnings*, so the
byte-level UTF-8 assertion over the whole document stays unconditional, and
the one path-specific test aborts with a diagnostic naming the encoding rather
than pretending to pass.

**PIT resolves from the local repository, not the reactor.** After the
redaction move, ``_build/m2repo`` still held a pre-move ``cometgui-domain``
jar, so ``mvn -pl cometgui-provenance -am org.pitest:...:mutationCoverage``
failed with ``ClassNotFoundException: org.cometgui.domain.secrets.SecretRegistry``
and reported 83 test failures that looked like another unit's bug. They were
not. **Run ``mvn -pl cometgui-domain install`` after any cross-module move
before running PIT.** This one costs an hour to diagnose and ten seconds to
avoid, and it will bite the main orchestrator re-running this phase's gate.

.. _p04-sample-census:

A class can leave the coverage sample without leaving a mark
============================================================

The main orchestrator raised this while five units were landing at once, and
it is the sharpest measurement point of the phase. A package-level coverage or
mutation figure taken while sibling units are mid-landing is not merely noisy,
it is **uninterpretable, and it can err in either direction**. Low is visible:
unit 7 saw ``json`` at 38% and correctly told me to read per-class numbers
instead. **High is invisible**: a class whose test does not compile can be
absent from the report altogether, and an absent class does not drag an
average down -- it silently leaves the sample. "All coverage checks have been
met" then means "met by whatever was left in it".

That is the same family as every other finding this session, and it is the one
version a handoff cannot expose, because the number it produces looks perfect.

**So it is now checked rather than hoped for.** A census compares the classes
actually compiled into ``target/classes`` against the classes present in
``jacoco.xml`` and in ``mutations.xml``. Run against the tree mid-landing it
found a live instance immediately::

    compiled classes (excluding package-info and inner): 37
    classes in jacoco.xml:                               36
    COMPILED BUT ABSENT FROM JACOCO:
      org.cometgui.provenance.manifest.ManifestReader

``ManifestReader`` had been compiled and was carrying 79 ``NO_COVERAGE``
mutations, because a unit had excluded its test to get a green suite while
another unit was mid-flight. The coverage rule passed over a sample that had
quietly lost its worst member.

``scripts/build.sh`` already fails a MODULE that compiles classes and produces
no ``jacoco.xml`` at all. It does not check the per-CLASS case, which is the
one that bites when a single test is excluded. That gap is reported upward
rather than patched here: ``scripts/`` is shared and not this phase's to edit.
Tier 1 has taken it as infrastructure, to be closed after Phases 03 and 04 land
and before Phase 05 is dispatched, in two parts: the census itself in
``scripts/build.sh``'s gates stage beside the module-level check it completes,
and a control in ``scripts/verify-test-gates.sh`` that injects a class excluded
from coverage and requires the census to fail naming it. The second half is not
optional -- a rule that has never been seen to go red is not yet a rule, which
is the mistake this project was founded on.

The script is handed over verbatim rather than described, because it has
actually caught something. Invoked as
``bash sample-census.sh cometgui-provenance``::

    #!/usr/bin/env bash
    # Did every compiled class actually reach the coverage and mutation samples?
    # A class whose tests did not compile can vanish from a report entirely, and
    # an absent class does not drag an average down -- it silently leaves the
    # sample.
    set -uo pipefail
    M="${1:-cometgui-provenance}"
    cd /mnt/10TBdrive/home/mriffle/work/comet-gui || exit 1
    CLASSES=$(cd "$M/target/classes" 2>/dev/null && find . -name '*.class' -type f \
      ! -name 'package-info.class' ! -name '*$*' \
      | sed 's|^\./||; s|\.class$||; s|/|.|g' | sort)
    JACOCO=$(grep -o '<class name="[^"]*"' "$M/target/site/jacoco/jacoco.xml" 2>/dev/null \
      | sed 's/.*name="//; s/"//; s|/|.|g' | grep -v '\$' | sort -u)
    PITC=$(grep -o '<mutatedClass>[^<]*</mutatedClass>' \
      "$M/target/pit-reports/mutations.xml" 2>/dev/null \
      | sed 's|</\?mutatedClass>||g' | sed 's/\$.*//' | sort -u)
    echo "module: $M"
    echo "  compiled classes (excluding package-info and inner): $(echo "$CLASSES" | grep -c .)"
    echo "  classes in jacoco.xml:                               $(echo "$JACOCO" | grep -c .)"
    echo "  distinct classes with PIT mutations:                 $(echo "$PITC" | grep -c .)"
    MISSING_J=$(comm -23 <(echo "$CLASSES") <(echo "$JACOCO"))
    MISSING_P=$(comm -23 <(echo "$CLASSES") <(echo "$PITC"))
    if [ -n "$MISSING_J" ]; then
        echo "  COMPILED BUT ABSENT FROM JACOCO:"; echo "$MISSING_J" | sed 's/^/    /'
    else
        echo "  ok: every compiled class is in the coverage sample"
    fi
    if [ -n "$MISSING_P" ]; then
        echo "  compiled but no PIT mutations (may be legitimate: constants,"
        echo "  interfaces, records with no branches):"
        echo "$MISSING_P" | sed 's/^/    /'
    fi

and the output that caught the live instance, on a tree where five units were
landing at once::

    module: cometgui-provenance
      compiled classes (excluding package-info and inner): 37
      classes in jacoco.xml:                               36
      distinct classes with PIT mutations:                 30
      COMPILED BUT ABSENT FROM JACOCO:
        org.cometgui.provenance.manifest.ManifestReader

The second list, classes with no PIT mutations, is a prompt rather than a
failure: an interface, an enum of constants or a record with no branches
legitimately yields none, so it is printed for a human to judge instead of
being asserted on.

**Every headline figure this phase reports is taken from one clean end-to-end
run with no agents in flight, a module free of modified or untracked sources,
``mvn -pl cometgui-domain install`` first, then one ``verify`` and one mutation
run** -- never assembled from per-class numbers gathered across different runs.
A composite figure that cannot be reproduced in one command is not evidence.

.. _p04-skip-audit:

Audit: is any test excluded, disabled or scoped out to keep a suite green?
==========================================================================

Asked directly by tier 1, because a sentence in one of my escalations -- "a
unit had excluded its test to get a green suite" -- describes a hard-rule
violation if it persisted. It did not. Answered here from a fresh audit of the
tree rather than from memory.

**No test is excluded, disabled or skipped to make anything pass.**

* **No surefire exclusion anywhere.** The only ``<excludes>`` in ``pom.xml`` are
  Spotless and Checkstyle *file-set* excludes for ``**/derived/**``, which are
  Phase 02's two-licence-header split and are covered by a second execution;
  the coverage block carries an explicit comment, "NO EXCLUSIONS. There is no
  ``<excludes>`` here and adding one to make a ... pass" is refused. No pom in
  the repository names ``ManifestReader``, and both
  ``ManifestReader.java`` and ``ManifestReaderTest.java`` are present and
  compiled. The ``ManifestReader`` absence the census caught was transient
  in-flight scoping on one agent's command line, and it is gone.
* **No unconditional ``@Disabled`` in either module.** Zero occurrences.
* **Conditional skips exist and are disclosures, not exclusions.** Two
  ``@EnabledOnOs({LINUX, MAC})`` on the directory-fsync tests, whose Windows
  half is genuinely unverifiable here and is recorded as unverified; and
  twenty ``@DisabledOnOs(WINDOWS)`` on tests that build POSIX absolute paths,
  each carrying a ``disabledReason``. Phase 15 owns the platform matrix and the
  repair is a Windows twin, never a relaxation.
* **Two ``Assumptions.abort`` sites, both the same encoding case**
  (:ref:`p04-encoding`). Each sits in a helper that aborts *only* when
  ``Path.of`` actually throws ``InvalidPathException``, and names
  ``sun.jnu.encoding`` and the ``LANG=C.UTF-8`` remedy in its message. Under a
  UTF-8 locale the helper returns a path and the tests run normally. This is
  the honest form: the suite reports a skip with a diagnostic rather than a
  green it has not earned.

Rejections and rework
=====================

.. _p04-unit1-rejection:

Unit 1, first submission (``96fe2ce``) -- REJECTED 2026-08-31
-------------------------------------------------------------

**The single-pass property was proved on a seam the production path is free
not to use.** This is the third appearance in this project of the defect the
phase brief calls acute, after Phase 01's ArchUnit rule that evaluated nothing
and Phase 02's identifiers that were compared against themselves.

The submitted work was good in every other respect. All 39 expected digests
were hand-typed literals, and I recomputed every one of them independently --
the seven short vectors, the four chunk-boundary lengths from the test's own
LCG pattern reproduced in Python, the 256 ascending byte values and the
``00 ff 00 ff 80 7f 01 fe 00 00 ff ff`` fixture -- with GNU coreutils, and all
fourteen boundary values matched to the character. The agent had proved seven
of its own defects failed correctly.

What it did not have was any test of ``hash(Path)``. The class reads
``hash(Path) -> Files.newInputStream -> hash(InputStream)``, and every
single-pass assertion was made by handing a recording stream to the
package-private ``hash(InputStream)``. So I replaced the delegation in
``hash(Path)`` with a version that opens and reads the whole file **twice**,
discarding the first result::

    FileHashes discardedFirstPass = hash(Files.newInputStream(path));
    if (discardedFirstPass == null) {
        throw new IOException("unreachable");
    }
    return hash(Files.newInputStream(path));

That is precisely what ``R-PROV-01`` and ``R-PROV-03`` forbid -- it doubles the
I/O on the multi-gigabyte spectrum files the requirement was written for -- and
it returns entirely correct digests. Observed::

    Tests run: 39, Failures: 0, Errors: 0, Skipped: 0
    BUILD SUCCESS

including all nine of the group-6 read-count assertions. A gate item that
cannot see a doubling of the I/O it exists to bound is not yet a gate item.

Sent back with the requirement that opening be made observable -- a
package-private ``FileOpener`` seam, with tests asserting through
``hash(Path)`` that the opener is called exactly once, that the bytes
delivered equal the file length exactly once over, and that the stream is
closed once -- and that the rework re-apply the two-pass defect above and show
it failing.

Deferred
========

**The phase was PAUSED by the owner for cost, not finished and not failed.**
Units 1 to 8 are signed off; 9 and 10 are landed but not signed off; 11, 12 and
13 were not started. ``handoffs/PHASE-04-handoff.rst`` is written for a fresh
agent with no context.

Explicitly not done, each with its reason:

* **Unit 11**, the seeded-secret grep over generated artefacts, gate item 6's
  end-to-end half. The rule set, the corpus and the per-writer sweeps all
  exist; what is missing is the one test that writes JSON, RST and a log and
  greps the files on disk.
* **Unit 12**, the two documentation pages, still Phase 01 stubs. The schema
  they must describe is settled and pinned as a hand-typed document in
  ``ManifestWriterTest``.
* **Unit 13's final run.** ``scripts/verify-all-gates.sh`` and the single clean
  end-to-end measurement were deliberately not run: Phase 03 is still changing
  the tree, so any figure would be invalid before this phase resumes.
* **Sign-off of units 9 and 10** -- diffs unread, gates not re-run, no defect
  injected into either.
* **Confirmation that the one real PIT survivor is dead.** Its fix is landed at
  ``892962e`` and unconfirmed.
* **The Sphinx check over unit 10's generated report**, the RST half of gate
  item 6, run so far only by the agent that wrote it.

Open items the orchestrator is carrying
=======================================

Neither is a blocker; both are written down so that they cannot be forgotten
between units.

The mutation switch is live -- CLOSED 2026-08-31
    Confirmed at unit 2's sign-off: ``mvn -pl cometgui-provenance -am verify
    org.pitest:pitest-maven:mutationCoverage`` with **no**
    ``-Dcometgui.mutation.skip`` on the command line produced
    ``cometgui-provenance/target/pit-reports/mutations.xml`` with 28 mutations.
    The POM switch is doing the work, not the override. Original text follows.

The mutation switch is set but has not yet been seen to *run*
    ``cometgui-provenance/pom.xml`` now carries
    ``<cometgui.mutation.skip>false</cometgui.mutation.skip>`` (``4c16864``).
    The POM parses and ``mvn validate`` is clean, and PIT has been observed
    producing 8 mutations for this module -- but only with
    ``-Dcometgui.mutation.skip=false`` forced on the command line, which is
    not the path ``scripts/build.sh`` takes. Confirm at the next sign-off that
    a plain ``org.pitest:pitest-maven:mutationCoverage`` with no override
    produces ``cometgui-provenance/target/pit-reports/mutations.xml``. A
    switch that is set and inert is exactly the failure this project keeps
    finding.

Exact read counts are a PLATFORM ASSUMPTION, stated here deliberately
    Units 1's groups 6 and 8 assert the number of ``read(byte[], int, int)``
    calls exactly, which is only deterministic because a read of a regular
    file returns everything asked for on this platform. POSIX permits a short
    read. The assertions were left exact deliberately: the counts are what
    catch a second pass and a byte-at-a-time loop, and a genuine short read is
    itself worth failing on and looking at.

    **The decision, stated so that a future CI failure reads as a documented
    expectation rather than a mystery.** The read-CALL count is an assertion
    about this platform as much as about this code, and it is kept exact
    anyway. **Phase 15 owns the version and platform matrix and should watch
    these two groups on Windows and macOS.** If they prove flaky there, the
    repair is to relax the read-call count ALONE. The count of BYTES summed
    over every stream opened must stay exact in every case: that is the
    assertion which actually catches a second pass over the file, and it is
    the one that failed when the two-pass defect was injected. Relaxing it
    would return the suite to the state that was rejected at unit 1.

Two findings from unit 2 that later phases must not lose
--------------------------------------------------------

**A same-filesystem temporary file is not optional, and this machine hides
that.** ``java.io.tmpdir`` is ``/tmp`` on an overlay filesystem while the
checkout is on ``/dev/sda1`` -- but JUnit's ``@TempDir`` also lives under
``/tmp``, so a writer that wrongly used the system temp directory would still
complete an ``ATOMIC_MOVE`` in every test on this host and never tear a file.
The defect was caught only by the explicit assertion that the temporary file is
a sibling of the target. **That test is not decoration and must not be
deleted**; on a machine where the project directory and ``/tmp`` differ, the
same defect is silent data corruption.

**The Windows half of the directory-sync asymmetry is UNVERIFIED.** Opening a
directory as a channel fails on Windows, and the writer deliberately tolerates
that because by then the data is already renamed into place. Two tests are
``@EnabledOnOs({OS.LINUX, OS.MAC})``, and the Windows behaviour is proved only
by substituting a failing ``Durability``, never on a real Windows host. This is
the same residue Phase 00 recorded: no Windows or macOS binary has ever been
executed in this project. It does not cap this phase -- gate item 5 is proved
on the platform the gate runs on -- but Phase 15 owns the platform matrix and
should close it.

Blockers escalated
==================

None so far. Phase 03's process service has not been needed: nothing in this
phase launches a process, and the fake-tool corpus in
``src/test/resources/fakes/`` has not been read or referenced.

An environment observation for tier 1, not a blocker
----------------------------------------------------

.. note::

   **This section originally misattributed the cause, and the correction is
   worth more than the observation.** It said the main orchestrator's own
   baseline build had been destroyed by a concurrent ``target/`` deletion. That
   was wrong, and tier 1 checked it rather than accepting it. The three
   ``scripts/verify-*-gates.sh`` harnesses each carry "WHERE IT WORKS. Never in
   the working tree" and each builds a *copy* under ``_build/``, so none of them
   runs Maven in the working tree and none can remove a ``target/`` there. The
   copying mechanism differs between them and the distinction is worth keeping
   straight: ``verify-shell-gates.sh`` extracts ``git archive HEAD``, so it
   tests the **committed** tree, while ``verify-quality-gates.sh`` and
   ``verify-test-gates.sh`` ``cp`` the POMs, ``config/`` and each module's
   ``src/``, so they test the **working** tree including uncommitted edits. A
   phase with dirty files gets different coverage from those two harnesses than
   from the third. The baseline ran
   17:27:37 to 17:39:34 with zero FAIL lines and its 10/10 stands. The failing
   log this phase observed at 17:32 was Phase 03's opening
   ``scripts/build.sh``, not the baseline; sibling subagents share one
   scratchpad directory, which is how the two runs came to look like one.
   Corrected here because a durability record that keeps a false attribution is
   worse than one that never made the claim.

The real mechanism is narrower and it is a dispatch mistake rather than a gate
hazard: ``scripts/build.sh`` line 217 runs ``mvn ... clean verify`` **at the
repository root, in the working tree**, and both this phase and Phase 03 were
instructed to run it before starting. Two of those at once is the ``target/``
deletion. Tier 1 has since told Phase 03 not to run ``build.sh`` again while
this phase is live.

``_build/m2repo`` remains a genuine concurrent-write hazard regardless, because
both phases and the gate harnesses share it even when the modules differ. This
phase therefore keeps every Maven invocation behind a ``flock`` on
``p04-maven.lock`` and restricts itself to ``-pl cometgui-provenance -am``.

.. _p04-resumption:

Resumption, 2026-09-01: a second phase orchestrator, on a quiet tree
====================================================================

The phase was paused on 2026-08-31 with units 1-8 signed off, 9 and 10 landed
but unsigned, and 11-13 not started. This section is written by the phase
orchestrator that resumed it. Everything above this line is the first
orchestrator's record and is not edited.

**The tree really is quiet this time.** No other phase orchestrator is live, no
other agent is landing code in either module, and tier 1 is not running Maven or
``docs-build.sh`` while this phase works. Every number below therefore means
what it says, which is the entire reason the owner paused the phase.

.. _p04-remeasured:

Step 1 -- every headline number re-taken from the quiet tree
-------------------------------------------------------------

Run at ``HEAD`` = ``9abfe1b`` with ``git status --short`` empty, in the order
:ref:`p04-first-thing` prescribes. ``mvn -pl cometgui-domain install`` first,
because PIT resolves from ``_build/m2repo`` and not from the reactor.

.. list-table::
   :header-rows: 1
   :widths: 34 66

   * - Command
     - What it produced

   * - ``mvn -B -pl cometgui-domain -am install -DskipTests``
     - ``BUILD SUCCESS``. Run first so that no PIT reading below is a
       classloading failure in costume.

   * - ``mvn -B -pl cometgui-provenance -am verify``
     - ``Tests run: 657, Failures: 0, Errors: 0, Skipped: 2`` for
       ``cometgui-provenance`` and ``Tests run: 359, Failures: 0, Errors: 0,
       Skipped: 0`` for ``cometgui-domain``; ``All coverage checks have been
       met``, ``BugInstance size is 0``, ``0 Checkstyle violations`` in both
       modules; ``BUILD SUCCESS`` in 91 seconds.

   * - ``mvn -B -pl cometgui-provenance -am test-compile
       org.pitest:pitest-maven:mutationCoverage``
     - ``cometgui-provenance``: **776 mutations, 767 KILLED, 6 SURVIVED, 3
       TIMED_OUT, 0 NO_COVERAGE**, read from ``mutations.xml`` and not from the
       console. ``cometgui-domain``: **204 mutations, 204 KILLED, 0 SURVIVED**.

   * - JaCoCo, read from ``jacoco.xml``
     - ``cometgui-provenance`` **100.00% line (1826/1826) and 100.00% branch
       (623/623)**, every one of its six packages at 100/100.
       ``cometgui-domain`` **100.00% line (432/432) and 100.00% branch
       (188/188)**.

**The census first, the percentages second.** Run verbatim from
:ref:`p04-sample-census` over both modules::

    module: cometgui-provenance
      compiled classes (excluding package-info and inner): 37
      classes in jacoco.xml:                               37
      distinct classes with PIT mutations:                 30
      ok: every compiled class is in the coverage sample
      compiled but no PIT mutations (may be legitimate: constants,
      interfaces, records with no branches):
        org.cometgui.provenance.events.EventLogDefectKind
        org.cometgui.provenance.events.EventLogSync
        org.cometgui.provenance.events.MalformedEventLineException
        org.cometgui.provenance.io.ContentWriter
        org.cometgui.provenance.io.Durability
        org.cometgui.provenance.manifest.LogRecord
        org.cometgui.provenance.manifest.ProvenanceSchema

    module: cometgui-domain
      compiled classes (excluding package-info and inner): 25
      classes in jacoco.xml:                               25
      distinct classes with PIT mutations:                 15
      ok: every compiled class is in the coverage sample
      compiled but no PIT mutations (may be legitimate: constants,
      interfaces, records with no branches):
        org.cometgui.domain.platform.GlibcVersionSource
        org.cometgui.domain.ports.DownloadProgressListener
        org.cometgui.domain.ports.Downloader
        org.cometgui.domain.ports.FileSystemAccess
        org.cometgui.domain.ports.HashService
        org.cometgui.domain.ports.ProcessListener
        org.cometgui.domain.ports.ProcessRunner
        org.cometgui.domain.ports.RunIdSource
        org.cometgui.domain.ports.RunningProcess
        org.cometgui.domain.run.StageTag

**``ManifestReader`` is back in the sample**, which is the finding that made the
census exist. 37 compiled, 37 in ``jacoco.xml``: nothing left the population.

**The second list was judged rather than skimmed**, because that is what the
census hands to a human. Every entry is legitimately mutation-free and the kind
of each was checked in the source rather than inferred from its name:
``EventLogDefectKind`` is an enum of constants; ``EventLogSync``,
``ContentWriter``, ``Durability``, ``StageTag``, ``GlibcVersionSource`` and the
seven ``org.cometgui.domain.ports`` entries are interfaces;
``MalformedEventLineException`` is an exception with constructors only;
``LogRecord`` is a two-component record whose validation delegates to
``ManifestChecks.requireAbsolute`` and ``Objects.requireNonNull`` and carries no
branch of its own; ``ProvenanceSchema`` is a constants holder. None is a class
whose tests failed to compile.

.. _p04-survivors-confirmed:

Step 2 -- the nine open PIT survivors, confirmed one at a time
---------------------------------------------------------------

:ref:`p04-survivors` listed seven survivors and two timeouts from a moving
tree, all reported killed or diagnosed and **none confirmed**. Confirmed here
against ``mutations.xml`` from the quiet run.

.. list-table::
   :header-rows: 1
   :widths: 40 18 42

   * - Site, as the paused phase listed it
     - Now
     - Evidence

   * - ``ProvenanceEventLogReader$Recovery:310`` MathMutator
       (``charAt(cut - 1)`` to ``charAt(cut + 1)``)
     - **KILLED**
     - Gone from the survivor list. The fixture landed at ``892962e`` --
       ``truncationInspectsTheCharacterBeforeTheCut``, one astral character
       alone in a run of ASCII -- and this is the run that confirms it. The
       phase's one genuine survivor is dead.

   * - ``JsonReader:203``, ``:256`` VoidMethodCall on ``skipWhitespace``
     - SURVIVED, **equivalent confirmed**
     - See :ref:`p04-equivalent-check`.

   * - ``JsonReader:228``, ``:252``, ``:264`` VoidMethodCall on
       ``leaveContainer``
     - SURVIVED, **real and killable**
     - See :ref:`p04-equivalent-check`; these are not equivalent and the
       property they guard is a real one.

   * - ``JsonReader:273`` VoidMethodCall on ``skipWhitespace``
     - SURVIVED, **real and killable**
     - The array trailing-comma fixture has no space before the bracket, so the
       call is not load-bearing in it. An input-set-too-narrow gap, shape 5.

   * - The two ``TIMED_OUT`` loop-control mutants
     - **three now**
     - ``JsonWriter:445`` (NegateConditionals in ``indent``),
       ``ProvenanceEventLogReader:169`` (NegateConditionals in ``recover``) and
       a third this run: ``EventLineFormat$Cursor:440``, MathMutator on the
       ``index++`` that consumes a closing quote, which makes ``readString``
       loop on one character for ever. All three are genuine infinite loops, so
       PIT counting a timeout as detected is right here rather than merely
       conventional. One mutation moved between KILLED and TIMED_OUT between
       the two runs and the total detected is unchanged; a mutant that hangs
       rather than fails is detected either way.

.. _p04-equivalent-check:

The six JsonReader survivors, checked rather than accepted
-----------------------------------------------------------

:ref:`p04-survivors` argued that two of the six were **equivalent mutants**
whose fix is to delete code rather than to write a test, and tier 1 asked for
that argument to be checked rather than accepted. It is right for two of them
and wrong for the other four, and the difference is worth stating because
"equivalent mutant" is the most convenient thing a survivor can be called.

**The two that really are equivalent -- and they were deleted.**
``JsonReader:203`` and ``:256`` are ``skipWhitespace()`` at the top of the
member loop and the element loop. Whitespace is already skipped on the way into
each loop (before the empty-container check) and again after every comma, so
the call at the top of the loop is reachable only in a state where it has
nothing to do. Reading the code establishes it; the mutation report proves no
test can distinguish it. Both calls are now gone, replaced by a comment stating
the invariant they were pretending to defend. **A call that cannot change
behaviour is the code form of a check that cannot fail**, and this project does
not keep those.

**The four that are not equivalent, and what they actually guard.**
``JsonReader:228``, ``:252``, ``:264`` are ``leaveContainer()`` on three of the
four routes out of a container -- an object with members, an empty array, an
array with elements -- and the existing nesting test
``doesNotAccumulateAcrossSiblings`` drives only the fourth, the *empty object*.
So depth was decremented on one route and untested on three, and the reason no
test noticed is that depth is only checked on the way **in**: you need more than
``MAX_DEPTH`` siblings of the right shape before it bites. **The consequence is
not theoretical.** ``provenance.json`` holds one object per file and one per
tool in flat arrays, so a reader that counted opens without counting closes
would refuse to parse any run with more than 64 inputs -- a real product defect
that a real run reaches. ``closesAContainerByEveryRouteOutOfIt`` now drives all
four routes with 70 siblings each.

``JsonReader:273`` is the whitespace skip after an array's comma, and it is
shape 5 of the catalogue -- an input set too narrow to see the defect. The
trailing-comma fixture was ``[1, 2,]``, with no space before the bracket, so the
call did nothing in it. ``[1, 2, ]`` and ``[1, 2,\n]`` are now asserted beside
it: without the skip the reader reports "a value was expected here" for a
document whose actual fault is a trailing comma, which sends whoever is
repairing a corrupt record to the wrong rule.

.. _p04-unit9-signoff:

Unit 9 -- SIGNED OFF 2026-09-01
--------------------------------

**What I read.** The whole of ``9639b77``: ``JsonReader`` (589 lines),
``JsonValue``, ``JsonParseException``, ``ManifestReader`` (909 lines) and all
four test files. Not a summary of them.

**What I ran.** ``mvn -B -pl cometgui-provenance -am verify`` and the mutation
run, both from the quiet tree; the numbers are in :ref:`p04-remeasured`.

**Injection A -- the question tier 1 asked, answered by experiment rather than
by argument.** The handoff claims that the round-trip suite cannot catch a
reader that reads the wrong key, and that only the two hand-typed suites can.
That is a claim about a suite, so I tested it on the suite. I renamed the member
``"formatLocale"`` to ``"format_locale"`` in **both** ``ManifestWriter`` and
``ManifestReader`` -- a writer and a reader agreeing on a wrong name, which is
the only shape a round trip is blind to -- asserting the anchor occurred exactly
once in each file first and grepping the marker back out of both afterwards.
Observed:

* ``ManifestRoundTripTest``: **Tests run: 14, Failures: 0, Errors: 0** --
  entirely green, including all two hundred generated manifests and the
  byte-stability check, **with every ``provenance.json`` the build can produce
  carrying a member name no other reader would understand**;
* ``ManifestWriterTest`` plus ``ManifestReaderTest``: **Tests run: 71,
  Failures: 21, Errors: 15**, the first of them
  ``expected: <the provenance manifest is not valid:
  "tools[0].execution.durationMillis" is 1950, ...> but was: <the provenance
  manifest is not valid: "application" has no member "format_locale">``;
* ``ManifestWriterTest`` alone: **Tests run: 24, Failures: 7**.

The claim holds exactly as stated. **The hand-typed pair is the proof and the
round trip is the supplement**, and a future agent "simplifying" the reader
tests to build their fixtures with the writer would delete the only thing
standing between this project and a symmetric, unanimous, wrong record.
Reverted with ``git checkout --``, ``touch``, and a marker count of 0 in both
files.

**Injection B -- leniency, which is how a strict reader dies.** In
``ManifestReader.member``, an absent member returned ``JsonNull.NULL`` instead
of throwing: the plausible "be liberal in what you accept" change, and the one
that collapses the absent-versus-null distinction the whole schema rests on.
Three failures, and the third is the one worth having:
``ManifestReaderTest.refusesAnAbsentMember expected: <... "run" has no member
"projectId"> but was: <... "run.projectId" must be a string">``,
``refusesANonNumericOrAbsentVersion``, and
``refused:321 Expected ... InvalidManifestException to be thrown, but nothing
was thrown`` -- a document with no ``end`` key at all silently read as a run
still in progress. Reverted; marker count 0; suite green again.

**A defect I found that the unit did not report, and repaired.** The reader's
own contract is that a hostile document is refused with a located
``InvalidManifestException`` and never anything else -- it is why nesting is
bounded rather than left to overflow the stack. The arithmetic twin of that was
open. The timestamp format's year field is backed by a ``LocalDate`` and reaches
year +-999 999 999, about two billion years, while ``Duration.toMillis()``
overflows a ``long`` at about 292 million. Measured on this JVM::

    parse OK   -0999999999-01-01T00:00:00.000Z
    parse OK   +0999999999-12-31T23:59:59.999Z
    millisBetween(...) THREW java.lang.ArithmeticException: long overflow

A document at both extremes parses, satisfies every model invariant, reaches
``requireRecordedDuration`` and threw ``java.lang.ArithmeticException`` **out of
``ManifestReader.parse``** -- past every caller that catches
``InvalidManifestException`` and nothing else. Unit 9 knew the *writer* half of
this and recorded it as "a run spanning the format's two extremes cannot be
serialised"; the reader half is the one an attacker or a corrupt file reaches,
and it was not covered. Repaired by refusing it as an invalid manifest, and the
repair carries its own negative control: with the guard removed the new test
fails with ``Unexpected exception type thrown, expected:
<...InvalidManifestException> but was: <java.lang.ArithmeticException>`` and
``Caused by: java.lang.ArithmeticException: long overflow``. Guard restored,
suite green.

**Unit 9's five design questions, answered with the diff in front of me.**

``InvalidManifestException`` nested or top-level
    **Nested, unchanged.** One rejection type for all three ways a document can
    be unreadable is right, and the name a caller writes,
    ``ManifestReader.InvalidManifestException``, says where the rule that
    rejected it lives. Phase 13's viewer is its first real caller; if it wants a
    shorter name it can import the nested type. Moving it now would be churn in
    signed-off code for no behaviour.

Should the reader attach the rejecting exception as a cause
    **No, unchanged, and the reasoning is stronger than the unit stated it.**
    ``FileHashes``, ``RunId``, ``ZoneId.of`` and ``DateTimeParseException`` all
    quote the value they rejected, so a cause chain would carry a hostile
    document's contents into every log that prints a stack trace. The cost is
    real -- you lose the specific message -- and it is paid deliberately: the
    class of the rejection *is* named, and the member path tells the reader
    which line of the file to open. ``JsonParseException`` is attached, because
    that one type promises its own message quotes nothing.

``json/package-info.java`` describes only writing
    **Fixed.** It now documents the reader, the grammar it refuses and why, the
    depth bound, and the rule that a rejection names a rule and a position and
    never a character of the document. A package document that describes half
    its package is how the next agent learns the wrong contract.

A run spanning the timestamp format's two extremes cannot be serialised
    **Kept as a stated property of the format, now enforced on both sides.**
    ``ManifestRoundTripTest`` already round-trips each extreme separately and
    says why it cannot do both in one manifest. The reader now refuses such a
    document instead of throwing; see above. No format change: an interval of
    292 million years is not a proteomics run, and widening ``durationMillis``
    to something a ``long`` cannot hold would cost every reader in the world.

Locale tags must be exactly canonical, so ``"en-us"`` is rejected
    **Kept, unchanged, and it is the right call.**
    ``Locale.forLanguageTag`` never fails -- handed rubbish it returns
    ``Locale.ROOT`` -- so accepting a non-canonical tag means the field that
    exists to explain a locale-dependent difference is the field that quietly
    loses its value. Re-rendering the tag and requiring a match is the only
    check available that can fail. ``R-PROV-04`` records what the JVM had, and
    what the JVM had is what ``toLanguageTag()`` writes.

.. _p04-unit10-signoff:

Unit 10 -- SIGNED OFF 2026-09-01
---------------------------------

**What I read.** The whole of ``9317abc``: ``ProvenanceReportWriter`` (493
lines), ``RstWriter`` (702 lines), the package document and both test files.

**What I ran.** The module gate from the quiet tree, and then the check the
paused phase named as its outstanding obligation.

**The Sphinx gate over the generated sample, run at last -- and seen to fail
first.** The paused handoff recorded that unit 10's sample report "has never
been through Sphinx by anyone but the agent that wrote it". A check nobody has
watched fail is not yet a check, so it was run both ways.

The sample is regenerated by ``ProvenanceReportWriterTest`` into
``cometgui-provenance/target/provenance-report-sample/``, and it is the fully
populated fixture rather than a tidy one: it carries an empty settings value, a
value with a leading space, one with a line feed, one with backticks and an
asterisk, a path holding a quotation mark and a backslash, accented text and an
emoji. On the clean tree::

    .venv/bin/sphinx-build -n -W -b html \
        cometgui-provenance/target/provenance-report-sample /tmp/.../rst-sample-html
    build succeeded.        exit 0        provenance.rst = 5085 bytes

**Exit 0 proves nothing, so the HTML was read rather than the exit code.** Zero
``problematic`` or ``system-message`` spans; **zero** occurrences of a literal
``\`\``` left anywhere in the output, which is what a value the markup failed to
process would leave behind; 108 rendered inline literals; the emoji and the
accented path present as themselves. The four values an inline literal cannot
carry each came out in the escaped form the report's own preamble describes --
``""``, ``" indented"``, ``"first line\nsecond line"`` and
``"wrote \u0060weights\u0060 to 5 * 3 files"``. That last pair matters because
two of the four **fail silently**: a leading space builds clean and renders the
backticks as text, so a green Sphinx run is not by itself evidence that a value
was rendered as a literal.

**Injection B -- the underline, through Sphinx rather than through JUnit.**
``ruleFor`` was changed to emit a rule one character shorter than its heading:
the defect the class documentation says the generated underline exists to
prevent, and the one whose whole point is that ``-W`` turns it into a build
failure for the entire project. The suite failed 11 of 33, **and the regenerated
sample then failed the documentation gate itself**::

    provenance.rst:1:   WARNING: Title overline too short.
    provenance.rst:17:  WARNING: Title underline too short.
    ... eight in all ...
    SPHINX EXIT=1

Reverted; marker count 0; 5085 bytes again; ``build succeeded`` and exit 0. That
is the RST half of gate item 6 seen to reject its defect and accept the clean
document, which is the standard this project holds a gate to and which this one
had not yet met.

**Injection A -- a size-conditioned leak, in the writer this time.** The phase
catalogued shape 6 -- a leak conditioned on the input's SIZE -- in the
*redactor* and fixed the corpus for it. Nobody had asked whether the same defect
in a *writer* would be caught. So ``RstWriter.value`` got the same plausible
fast path, ``text.length() < 24 ? text : redactor.redactText(text)``. Observed::

    ProvenanceReportWriterTest.doesNotSurviveAnywhere
      expected: <[]> but was: <[corpus secret #7 (length 12) survived into the
      rendered report, corpus secret #7 (length 12) survived into
      provenance.rst, corpus secret #9 (length 20) survived into the rendered
      report, corpus secret #9 (length 20) survived into provenance.rst]>
    RstWriterTest.runsOverEveryValue  6 failures, the first
      "a value survived redaction ==> expected: <false> but was: <true>"

Both artefacts were named, the rendered string **and the file on disk**, and the
message named the corpus index and the length without printing the secret. The
short carriers are what caught it: every one of them is under 24 characters, and
a corpus of realistic-looking long examples would have passed. Reverted; marker
count 0; 59 report tests green.

**What I checked in the diff beyond the injections.**

* ``ProvenanceReportWriter`` reads a ``ProvenanceManifest`` and nothing else --
  no file, no clock, no environment -- so ``R-PROV-05``'s "generated from the
  same machine-readable model, never maintained independently" is a property of
  the class rather than a promise about it.
* ``Structure.theWalkReachesEveryRecordType`` is a real anti-vacuity guard: the
  reflective walk over 53 components would otherwise pass by comparing two empty
  sets, and it is pinned with the count and three named components.
* The duration is derived through the shared ``CanonicalTimestamp.millisBetween``
  in both writers, so the JSON and the RST cannot disagree by the millisecond
  the truncation costs.
* Redaction is in the writer, not at the call sites, and the environment **key**
  is redacted as well as its value -- an environment name is run data, not
  schema, and ``RstWriterTest`` pins ``:D:\n   * ``[REDACTED]`` =
  ``[REDACTED]``\n``.
* Nothing outside ``org.cometgui.provenance.report`` was touched by ``9317abc``.

**One thing the commit message got right and is worth keeping.** ``9317abc``
recorded honestly that the module-wide ``verify`` was RED at the time, on a
SpotBugs ``RV_ABSOLUTE_VALUE_OF_HASHCODE`` belonging to another unit, and did
not touch it. That was repaired at ``892962e`` and the module is green now.
Reporting a red build you did not cause is the behaviour this structure needs.

.. _p04-noweakening:

Audit at the resumption: was any gate weakened while the phase was paused?
--------------------------------------------------------------------------

Asked and answered mechanically rather than from memory, because a sign-off that
does not check this is not a sign-off.

* ``git diff --stat 119672f..HEAD -- pom.xml cometgui-provenance/pom.xml
  cometgui-domain/pom.xml config/`` is **empty**. No threshold, no exclusion and
  no rule set changed between the pause and the resumption.
* The coverage limits in ``pom.xml`` are still ``LINE COVEREDRATIO 0.90`` and
  ``BRANCH COVEREDRATIO 0.85`` on the core modules and ``LINE 0.80`` on
  view-model logic; ``<mutationThreshold>80</mutationThreshold>`` is unchanged;
  ``targetClasses`` still carries ``org.cometgui.provenance.*``.
* There is **no** surefire ``<excludes>`` anywhere. The only ``<excludes>`` in
  any pom are the Spotless and Checkstyle file-set splits for ``**/derived/**``,
  each of which is covered by a second execution -- Phase 02's two-licence-header
  arrangement, not an exemption.
* **Zero** unconditional ``@Disabled`` in either module.
* Exactly **two** ``Assumptions.abort`` sites, both the ``sun.jnu.encoding``
  case, in ``ManifestRoundTripTest`` and ``ManifestWriterTest``. They stay; see
  :ref:`p04-encoding`.
* The twenty ``@DisabledOnOs(WINDOWS)`` and two ``@EnabledOnOs({LINUX, MAC})``
  annotations are unchanged and each still carries its ``disabledReason``.

The independently computed digests were re-derived as well, since gate item 1
rests on them. All seven short vectors were recomputed here with GNU coreutils
and compared against the literals in ``StreamingHashServiceTest`` -- including
the three that live inside a ``@CsvSource`` string rather than in a Java string
literal, which a naive grep for a quoted hex constant misses. Every one matches
to the character.

.. _p04-unit11-signoff:

Unit 11 -- SIGNED OFF 2026-09-01
---------------------------------

**What it is.** ``SeededSecretArtefactSweepTest`` in ``org.cometgui.provenance``
-- the root package, because it spans ``manifest``, ``report`` and ``events``
and belongs to none of them. Landed at ``df7fbac``, 978 lines, eight tests. It
builds one manifest and a seventeen-event stream that between them carry all
thirteen corpus entries in every carrier shape gate item 6 names, writes
``provenance.json``, ``provenance.rst`` and ``events.log`` into one run
directory, **walks that directory** and reads every regular file back off disk,
searching each twice -- as UTF-8 text and as raw US-ASCII bytes.

**What I ran.** ``mvn -B -pl cometgui-provenance -am verify`` myself:
``Tests run: 667, Failures: 0, Errors: 0, Skipped: 2``, the new class at
``Tests run: 8, Failures: 0``, ``All coverage checks have been met``,
``BugInstance size is 0``, ``0 Checkstyle violations``, ``BUILD SUCCESS``. The
two skips are the pre-existing Windows-disabled tests in ``ManifestRoundTripTest``
and ``ManifestWriterTest``; this class skips nothing on Linux.

**Injection A -- the JSON half of gate item 6, which the unit had not tried.**
The unit's own production injection was redaction dropped from the *event log*.
So mine went at the *other* artefact: ``JsonWriter.value``'s
``escapeInto(redactor.redactText(value))`` became ``escapeInto(value)`` --
escaped but never redacted. The compiled class was confirmed to have changed,
``5dd38bf618e1870f8a3d76fb81f495bc`` to ``030ab2bc4eb414e3e1cdd29b8ab73069``,
because an injection that reaches the source and not the ``.class`` is the
eighth catalogued shape. Observed::

    expected: <[]> but was: <[corpus secret #1 (length 39) survived into
    provenance.json as UTF-8 text, at offset 1595, ... #2 (95) @619,
    #4 (14) @1150, #5 (28) @3257, #6 (28) @3328, #7 (12) @1237,
    #8 (25) @1067, #9 (20) @331, #10 (66) @781, #11 (64) @849,
    #12 (64) @915 ...]>

    thePatternRulesAloneLeakExactlyTheRegistryOnlyCarriers
    expected: <{events.log=[0, 5, 6, 7, 8], provenance.json=[0, 5, 6, 7, 8],
                provenance.rst=[0, 5, 6, 7, 8]}>
    but was:  <{events.log=[0, 5, 6, 7, 8],
                provenance.json=[0, 1, 2, 4, 5, 6, 7, 8, 9, 10, 11, 12],
                provenance.rst=[0, 5, 6, 7, 8]}>

Three things in that output are worth more than the failure itself. **The two
searches agree on every offset**, which cross-checks the decode the class says
it is cross-checking. **Only ``provenance.json`` moved**: the RST and the log
stayed exactly at their pinned leak sets, so the sweep discriminates between
artefacts rather than reporting "something, somewhere". And **secrets #0 and #3
did not leak even with redaction gone from the JSON value path**, because
``ManifestWriter`` clears the environment by variable *name* and the argument
after ``--password`` *positionally*, before ``JsonWriter`` ever sees them --
which is the layered design working, visible only because a defect removed one
layer.

**Injection B -- is the walk load-bearing, or decoration?** The unit's
distinctive claim over the three narrower sweeps is that it enumerates the run
directory instead of naming three files, and ``containsAll`` plus
``size() >= 3`` is a weak way to assert that. So I put a fourth file in the run
directory that nothing names -- ``stray-upload.log``, holding ``token
tok_live_abcdef0123456789`` in clear, the shape of an artefact a later phase
adds without telling anyone. **Three separate assertions fired**::

    notOneSecretReachesAnyFileInTheRunDirectory
      <[corpus secret #8 (length 25) survived into stray-upload.log as UTF-8
        text, at offset 6, ... as US-ASCII bytes, at offset 6]>
    everyArtefactCarriesTheRedactionMarker
      "these artefacts contain no redaction marker at all, so nothing was
       redacted in them and 'nothing leaked' means nothing"
      expected: <[]> but was: <[stray-upload.log]>
    thePatternRulesAloneLeakExactlyTheRegistryOnlyCarriers
      ... but was: <{..., stray-upload.log=[8]}>

The enumeration is real. Both injections reverted, marker counts 0, and the
full ``verify`` green again.

**What the unit reported honestly and I confirmed.**

* Its guard-(a) demonstration removed a carrier *shape* -- two occurrences --
  rather than a single line, because no corpus entry has exactly one carrier by
  design. It said so rather than claiming a cleaner result than it had.
* It did not run PIT and said so. Mutation coverage of this class is measured
  in the final gate run recorded below, not by the unit.
* It found, and reported rather than edited, that
  ``ProvenanceReportWriterTest.manifestCarryingTheCorpus()`` writes a PEM block
  with no ``-----END-----`` delimiter, so the ``SecretRedactor`` PEM *pattern*
  cannot match it there and only the registry clears it in that fixture.
  **Checked, and it is closed by this unit rather than open**: unit 11's own
  fixture uses a complete block, and its ``patternsOnly()`` test pins corpus
  indices 10-12 as *absent* from every artefact's leak set -- which is the PEM
  pattern rule being exercised through the RST and JSON paths. No change made to
  unit 10's fixture; changing a signed-off test to prove something another test
  already proves would be churn.
* A run directory after all three writers contains exactly ``events.log``,
  ``provenance.json`` and ``provenance.rst``. ``AtomicDocumentWriter`` leaves no
  temporary file behind, measured rather than assumed.

.. _p04-unit12-signoff:

Unit 12 -- SIGNED OFF 2026-09-01
---------------------------------

**What it is.** ``31be8c1``: ``docs/reference/provenance_format.rst`` (1163
lines, the versioned on-disk schema), ``docs/developer/provenance_schema.rst``
(642 lines, the model behind it), ``ProvenanceFormatDocumentationTest`` (401
lines, the drift check) and five ``AC-PRV`` evidence entries in
``docs/traceability-map.toml``.

**What I ran.** ``mvn -B -pl cometgui-provenance -am verify``: ``Tests run:
669, Failures: 0, Errors: 0, Skipped: 2``, the new class at ``Tests run: 2``,
``All coverage checks have been met``, ``BugInstance size is 0``,
``0 Checkstyle violations``. ``bash scripts/ci/docs-build.sh``: ``build 1 OK --
51 HTML page(s) ... (49 from source documents)``, ``build 2 OK -- 46 HTML
page(s) ... (43 from source documents)``, ``docs-build.sh: PASSED.``.
``bash scripts/ci/traceability.sh``: ``PASSED``, ``99 R- rules, 80 AC-
criteria, all mapped and verified``, ``0 automated, 11 partial, 61 planned, 8
human sign-off``. I read the rendered rows rather than the summary line: all
five ``AC-PRV`` criteria now read **partial**, each naming its test *and*
keeping the ``planned`` entry with a note saying what Phase 04 does **not**
prove -- for ``AC-PRV-10``, "that the value written is the seed Percolator
actually ran with is phase 09's". That is the honest shape.

**Injection A -- the direction the unit did not try.** The unit proved the
drift check fires when a member is **added** to the writer. The other direction
is the dangerous one: a member **removed**, which leaves the reference page
promising a field no ``provenance.json`` carries, and a reader writing a parser
from the page against a document that has no such member. I deleted
``json.name("architecture").value(application.architecture());`` from
``ManifestWriter``. Both halves fired::

    expected: <[application, application.architecture, application.buildIdentifier, ...]>
    but was:  <[application, application.buildIdentifier, ...]>

    the walk must find the documented number of members; an empty walk equals
    an empty expected set and proves nothing ==> expected: <57> but was: <56>

**Injection B -- is the exclusion guard vacuous?** The check skips the two
open-ended maps, ``settings`` and each ``execution.environment``, because their
keys are run data rather than schema. An exclusion that excluded nothing would
look exactly like a correct one, so the class asserts the fixture really
carries entries in both. I emptied the fixture's settings map and the guard
fired with the message it was written for::

    the fixture must carry a settings entry for the exclusion to exclude
    ==> expected: <true> but was: <false>

Both reverted, marker counts 0, the class green again.

**One thing the unit reported as a defect that is not one, corrected here.** It
reported that ``FileHashes`` accepts ``[0-9a-fA-F]+`` while every writer emits
lower case, and called it a defect it had not fixed. Read in full, the record's
compact constructor ends ``return digest.toLowerCase(Locale.ROOT)``: it
**canonicalises**, so the model never carries an upper-case digest and two
records built from differently-cased spellings of one digest are equal. The
lenience is on the way in only, and it is deliberate. The reference page's own
wording is already right -- "a writer emits lower case; a reader that compares
digest strings should normalise case rather than assume it, because the model's
own check accepts either" -- which is advice to someone comparing raw document
text, and correct. No change made. Recorded because a handoff that carries a
false defect is worse than one that carries none.

**Three findings from the unit that are real and are carried forward.**

* **The specification's "safely rendered command for display" has no member in
  the format.** ``argv`` is recorded and the display string is a pure function
  of it (``ToolCommand.displayString()``). The page states the divergence in
  its *Where each specification fact is recorded* table rather than hiding it.
  It sits oddly beside ``durationMillis``, which **is** written on the ground
  that "the artefact is the record" -- and the two are reconcilable only
  because a duration cannot be recomputed from a *truncated* document without
  the writer's own rule, while a display string can be recomputed from ``argv``
  exactly. If tier 1 disagrees, it is a schema-version 2 change, not a
  documentation fix.
* **``ExecutionRecord.status`` documents three values and enforces none.** Its
  Javadoc says ``COMPLETED``, ``FAILED`` or ``CANCELLED``; the constructor only
  null-checks and ``ManifestReader`` accepts all five wire names there. The
  page now states what is written, what is documented and what is actually
  accepted, which is the honest three-way answer. Narrowing the type is a
  behaviour change and belongs to whoever owns the stage semantics.
* **The event log's file name is pinned by no constant.**
  ``ManifestWriter.FILE_NAME`` and ``ProvenanceReportWriter.FILE_NAME`` exist;
  the log is opened on a caller-supplied path and ``events.log`` appears only
  in tests. **Phase 13 needs a discoverable log in a run directory** and must
  pin a constant rather than guess a name.

.. _p04-unit13-signoff:

Unit 13 -- gate enablement, the final measurement, and a falsifiability harness
-------------------------------------------------------------------------------

Orchestrator work, not delegated.

**The falsifiability harness that Phase 03 escalated as debt.**
``scripts/verify-provenance-gates.sh`` (``9ddb3e3``) is Phase 04's answer to the
gap Phase 03 named: Phases 01 and 02 each ship a harness proving their gate
items fail on the defects those items exist to catch, and a third phase ending
without one would have doubled the debt. It was **assembled from this work log
rather than invented** -- every injection in it was actually made during the
phase and its exact failure text is recorded above -- and it is registered in
``scripts/verify-all-gates.sh`` as the ``provenance`` control with a floor of
24.

**Twenty-four controls, 236 seconds.** The full list is in the script's header.
The five that carry the phase's own findings are worth naming here: a hasher
that keeps every chunk it reads, which leaves the **digests exactly correct**
and is invisible to every test except the 2 GB retained-heap bound; a
fingerprint that treats an absent attribute as a match, dressed as Windows
compatibility; ``ATOMIC_MOVE`` replaced by copy-then-delete, which a concurrent
reader sees straight through; the size-conditioned leak in the RST writer that
only the corpus's short carriers catch; and a control on the harness itself that
runs with **no** defect injected and requires the run to be reported as a
HARNESS FAILURE rather than a pass.

**The harness found two defects in itself before it accepted anything, and both
are recorded in the script rather than quietly fixed.**

* Its "did the class actually run" guard scraped the console for
  ``Tests run: N ... -- in <class>``. Every test class in this module puts its
  tests in ``@Nested`` classes, and surefire prints each nested class under its
  ``@DisplayName`` and then prints ``Tests run: 0`` for the *outer* class -- so
  the guard read **0 for a class that had just run 52 tests** and stopped the
  run with a harness error. That is the guard working, on its first run, on its
  own author. The count now comes from ``<testsuite tests="N">`` in the surefire
  XML, and the previous control's reports are deleted before each run so that a
  run which executed nothing cannot be graded against stale files.
* Its gate item 2 assertion was written from the printout quoted in
  ``handoffs/PHASE-04-handoff.rst``, which is abbreviated: the real line is
  ``huge-file: bytes=2147483648 writeMillis=... hashMillis=... totalMillis=...
  opens=1 readCalls=8193 bytesDelivered=2147483648 ...`` and carries three
  timing fields the quotation omits. The harness rejected its own first
  expectation before it accepted anything, which is the behaviour a harness
  exists to have.

**Item 7 is delegated and the delegation is enforced rather than asserted.**
Proving the mutation gate here would mean a second twenty-minute mutation run
and two things to keep in step with one gate.
``scripts/verify-test-gates.sh`` already injects a covered class whose test
asserts nothing and requires the mutation gate to reject it, so control 7
asserts that harness still contains its mutation control **and** that
``cometgui-provenance/pom.xml`` still carries
``<cometgui.mutation.skip>false</cometgui.mutation.skip>``. A switch that is set
and inert is the failure this project keeps finding, and it is now asserted on
every aggregate run rather than checked once.

.. _p04-third-blind-spot:

Rework after tier 1's sign-off injection: the corpus's third blind spot
-----------------------------------------------------------------------

Tier 1 injected one character into the shared rule set --
``KNOWN_TOKEN_SHAPES.matcher(cleaned).replaceAll(...)`` became
``replaceFirst(...)`` -- and **everything stayed green**: 83 domain tests and 22
provenance secrecy tests, including this phase's own
``SeededSecretArtefactSweepTest``, all passing with a secret-leaking change live
in ``cometgui-domain``. **No carrier in the corpus held its secret more than
once**, so nothing in the suite could tell the two apart. That is catalogued
shape 5, an input set too narrow, on a dimension the corpus had already reasoned
carefully about twice.

**A sharper finding underneath it, measured before anything was written.** The
obvious repair -- add a two-occurrence carrier to the corpus -- does **not**
work, and would have shipped a test that cannot fail. ``redactText`` runs the
literal registry pass **first**, and ``SecretRegistry.redactIn`` uses
``String.replace``, which clears **every** occurrence. ``loaded()`` registers
the whole corpus, so under it the literal pass repairs a broken pattern rule
before that rule is ever reached. Measured on the compiled class with the defect
live::

    --- patternsOnly(): the registry cannot mask a pattern defect ---
    token shape x2         survives=true   [REDACTED] AKIAIOSFODNN7EXAMPLE
    --- loaded(): the registry's String.replace clears every occurrence ---
    token shape x2         survives=false  [REDACTED] [REDACTED]

So the repeated carriers are swept with ``SecretRedactor.patternsOnly()``, and
the reason is written into the class documentation beside the carriers rather
than left for the next person to rediscover. **This also means the existing
``shortCarriersComeOutAsWritten`` assertions prove less than they appear to**:
run through ``loaded()``, ``auth=swordfish-42`` is cleared by the registry
before the assignment rule sees it. They are not wrong -- they pin the composed
behaviour a caller gets -- but ``patternsAloneCoverWhatTheyClaim`` is the test
that holds the pattern rules to account, and it is where the new carriers
matter.

**What was added**, to
``cometgui-domain/src/test/java/org/cometgui/domain/secrets/SeededSecretCorpusTest.java``
and nothing else:

* a third entry in the class documentation's blind-spot list, naming occurrence
  count beside partial rewrite and input size, in the same voice, and carrying
  the masking measurement above;
* ``repeatedCarriers()`` -- one carrier per rule family with its secret in it
  twice, **built by duplicating the corresponding short carrier** rather than
  written out again, so the short carriers stay short by construction and a
  later edit to one cannot leave its twin testing a different rule;
* ``repeatedCarriersComeOutAsWritten`` (pattern families, ``patternsOnly()``),
  ``repeatedRegistryOnlyCarriersComeOutAsWritten`` (bare value, ``pw=``, argv,
  ``loaded()``), and ``everyRepeatedCarrierIsItsShortCarrierTwice``, which pins
  the duplication relationship so blind spots (2) and (3) cannot pull against
  each other;
* the repeated carriers, a two-block PEM carrier, a four-element argv and a
  two-name environment wired into ``redactEveryCarrier``, so they reach both the
  absence sweep and ``patternsAloneCoverWhatTheyClaim``.

**Every expected string is hand-typed from the rule** -- each occurrence of the
secret becomes the marker, everything else survives -- and not captured from the
redactor. The probe above was an independent cross-check of values already
derived, not their source.

**The deliverable: the defect re-injected, and the exact text.** Anchor asserted
to occur exactly once, marker grepped back out, and the **compiled class
confirmed changed** (``f3210458a6d76b92c55629238ba82bca`` to
``b5051066b25adafa9093c319b407cb3d``) before any result was believed::

    SeededSecretCorpusTest.patternsAloneCoverWhatTheyClaim:600
      a pattern rule stopped covering its carrier: [corpus secret #9 survived
      the short token shape twice carrier with the pattern rules alone]
      ==> expected: <true> but was: <false>

    SeededSecretCorpusTest.repeatedCarriersComeOutAsWritten:424
      expected: <[REDACTED] [REDACTED]> but was: <[REDACTED] AKIAIOSFODNN7EXAMPLE>

**And each family's carrier was proved load-bearing on its own rule**, one
injection at a time so that attribution could not be borrowed. Every one names
**its own** carrier and no other:

.. list-table::
   :header-rows: 1
   :widths: 42 58

   * - Injection
     - The carrier that caught it

   * - ``KNOWN_TOKEN_SHAPES`` to ``replaceFirst``
     - ``corpus secret #9 survived the short token shape twice carrier``

   * - ``PEM_PRIVATE_KEY`` to ``replaceFirst``
     - ``corpus secret #10`` and ``#11 survived the pem block twice carrier``

   * - ``CREDENTIAL_URL`` to ``replaceFirst``
     - ``corpus secret #7 survived the short credential URL twice carrier``

   * - ``BEARER_TOKEN`` to ``replaceFirst``
     - ``corpus secret #7 survived the short bearer twice carrier``

   * - the assignment scan stopped after its first match
     - ``corpus secret #7 survived the short assignment twice carrier``

That fifth one is worth noting: ``redactSecretAssignments`` is an explicit scan
rather than a ``replaceAll``, so ``replaceFirst`` cannot be written there -- the
equivalent defect is the loop stopping early, and the carrier catches that too.

**This is a change to ``cometgui-domain``, which Phase 03 also depends on**, so
it is recorded here as the shared-module rule requires. It is **test-only**: no
production file was modified, ``replaceAll`` is correct and stays, and Phase
03's sign-off is untouched. The one production edit made during this work was
each temporary injection, every one reverted with ``git checkout --`` and a
``touch``, with the marker count confirmed back to 0.

**Re-measured after the rework**, on the quiet tree:

* ``cometgui-domain`` **362 tests** (was 359; the three new ones), 0 failures;
  ``cometgui-provenance`` **669 tests**, 0 failures, 2 skipped -- unchanged.
* JaCoCo **100.00% line and 100.00% branch** in both modules, unchanged.
* PIT ``cometgui-domain`` **204 mutations, 204 KILLED**, ``secrets``
  contributing 52; ``cometgui-provenance`` **774 mutations, 771 KILLED, 0
  SURVIVED, 3 TIMED_OUT**. Unchanged.
* The census is still whole: **37 compiled / 37 in jacoco.xml** for
  ``cometgui-provenance`` and **25 / 25** for ``cometgui-domain``.
