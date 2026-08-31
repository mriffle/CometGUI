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
     - pending

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

   * - 8
     - B
     - **Appendable event log.**
       ``org.cometgui.provenance.events`` -- typed events, an append-only
       writer that flushes each record, and a reader that recovers every
       complete event from a torn file. Accepts: a simulated crash mid-run
       leaves a parsable log whose recovered events are asserted by content.
     - ``R-PROV-05``, ``AC-PRV-06``
     - pending

   * - 9
     - C
     - **Strict JSON reader and the manifest round-trip suite.** Accepts:
       parsing is pinned against hand-typed JSON, not against output the
       writer produced; malformed documents are rejected with a located
       message; a property suite over generated manifests round-trips.
     - ``R-PROV-05``
     - pending

   * - 10
     - C
     - **The RST report.**
       ``org.cometgui.provenance.report`` -- ``provenance.rst`` generated from
       the same model as the JSON. Accepts: expected RST pinned as a hand-typed
       literal; every fact in the report is traceable to a manifest field;
       redaction applied through the same rule set.
     - ``R-PROV-05``, ``R-SEC-03``
     - pending

   * - 11
     - D
     - **The seeded-secret grep proof.** An end-to-end test that builds a
       manifest and an event log carrying the whole seeded corpus, writes
       JSON, RST and the log, then greps the generated files on disk for every
       seeded secret. Accepts: zero occurrences, and the test names the file
       and offset when it finds one.
     - ``R-SEC-03``, ``AC-PRV-09``
     - pending

   * - 12
     - D
     - **Documentation.** ``docs/reference/provenance_format.rst`` (the
       versioned schema) and ``docs/developer/provenance_schema.rst`` (the
       model behind it). Accepts: ``sphinx-build -n -W`` clean; every field in
       the document exists in the code and every field in the code appears in
       the document, checked by a test rather than by reading.
     - ``R-PROV-05``, ``R-DOC-03``
     - pending

   * - 13
     - D
     - **Gate enablement and falsifiability.** The module's mutation switch,
       the coverage gate, PIT survivor triage, and the injected-defect
       evidence for all seven gate items. Orchestrator work, not delegated.
     - ``R-TEST-02``
     - pending

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
is blind to at least two things unless they are deliberately covered.

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

pending

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
