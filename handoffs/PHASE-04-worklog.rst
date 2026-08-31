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
     - pending

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
     - pending

   * - 5
     - B
     - **The input-hash cache.**
       ``org.cometgui.provenance.hashing`` -- keyed on canonical path, size,
       modification time and file identity, revalidated on every use,
       invalidated by any attribute change, bypassable. Accepts: a mutation
       that preserves size *and* mtime is still detected or rehashed, and the
       recorded hash is a hash of the content actually read.
     - ``R-PROV-02``
     - pending

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
     - pending

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

Exact read counts assume a full-length read, which POSIX does not guarantee
    Units 1's groups 6 and 8 assert the number of ``read(byte[], int, int)``
    calls exactly, which is only deterministic because a read of a regular
    file returns everything asked for on this platform. POSIX permits a short
    read. The assertions were left exact deliberately: the counts are what
    catch a second pass and a byte-at-a-time loop, and a genuine short read is
    itself worth failing on and looking at. **Phase 15 owns the version and
    platform matrix and should watch these two groups on Windows and macOS.**
    If they prove flaky there the repair is to keep the open, close, stream
    and total-byte counts exact and relax only the read-call count -- never to
    delete the group.

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

Two phase orchestrators are live in one working tree, and both run Maven. A
root ``mvn clean verify`` from one deletes ``target/`` under the other, and the
main orchestrator's own baseline build was seen to fail this way at 17:32
while this phase's opening ``scripts/build.sh`` was running. This phase has
since kept to ``-pl cometgui-provenance -am`` and has run its units strictly
one at a time rather than in the parallel waves the decomposition allows,
which costs wall-clock time and buys clean signal: a coverage gate is
module-wide, so two agents landing half-tested code in the same module would
fail each other's builds for reasons neither could diagnose.
