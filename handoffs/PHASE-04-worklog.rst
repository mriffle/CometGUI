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
     - pending

   * - 2
     - A
     - **Durable, atomic file writing.**
       ``org.cometgui.provenance.io`` -- write-temp, fsync, ``ATOMIC_MOVE``,
       with the temp file removed on any failure. Accepts: a fault-injected
       write leaves the target absent or unchanged and never truncated; a
       concurrent reader never observes a partial file; an interrupted write
       is proved by interrupting, not by asserting that rename is atomic.
     - ``R-PROV-05``
     - pending

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

pending

Deferred
========

pending

Blockers escalated
==================

pending
