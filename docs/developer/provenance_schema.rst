.. _dev-provenance-schema:

=================
Provenance schema
=================

.. note::

   **This page is written and current** (Phase 04). It describes the model, not
   the file: the on-disk format is :doc:`../reference/provenance_format`, and
   the two must be changed together. **Phase 13** owns the Provenance UI and is
   required to keep this page in sync with it -- the UI renders this model and
   nothing else.

Provenance is a primary feature of CometGUI rather than a side effect of
running one, and the model was built before any stage that emits events,
deliberately: provenance retrofitted at the end is provenance with holes. This
page is for someone changing that code. It says what the types are, why each
one is shaped the way it is, and which of those shapes are load-bearing enough
that changing them is a format change.

Everything here lives in ``org.cometgui.provenance`` (Maven module
``cometgui-provenance``), in five packages: ``manifest``, ``json``, ``events``,
``hashing`` and ``io``, plus ``report``. The rules that remove credentials are
not here: they are in ``org.cometgui.domain.secrets`` and are shared with the
process service, for the reason given under
:ref:`dev-provenance-schema-redaction`.

.. contents:: Contents
   :depth: 2
   :local:

One model, two documents
========================

``ProvenanceManifest`` is the whole provenance record for one run, as a single
immutable value. It holds no I/O and knows nothing about JSON.

That matters because the specification requires the human-readable
``provenance.rst`` report to be "generated from the same machine-readable
model, never maintained independently", and the only enforceable reading of
that is: there is one model, and both writers take it as their only argument.
``ManifestWriter`` serialises it to ``provenance.json``; ``ProvenanceReportWriter``
renders the same object to ``provenance.rst``. Neither reads a file, probes an
environment, holds a clock or asks another component a question. The two
documents cannot disagree about a run because there is nothing for them to
disagree with.

The two writers are also built the same way on purpose -- ``redactingWith`` a
rule set, a ``render`` returning the exact text, a ``writeTo`` putting it on
disk atomically, timestamps through ``CanonicalTimestamp``. A later phase
adding a field to the manifest has one shape to follow twice, and a structural
test fails until it has followed it twice.

The record types
================

.. list-table::
   :header-rows: 1
   :widths: 26 74

   * - Type
     - What it is

   * - ``ProvenanceManifest``
     - The whole record: schema version, run, application, settings, tools,
       files.

   * - ``RunRecord``
     - Which run, in which project, in what state, between which two instants.

   * - ``ApplicationRecord``
     - The application and the machine: version, build, OS, architecture, JVM,
       both locales, time zone.

   * - ``ToolRecord``
     - One tool as it existed for one run: identity, probed capabilities, and
       the execution it performed.

   * - ``ExecutionRecord``
     - One external process, from the argument array it was launched with to
       the code it exited with.

   * - ``LogRecord``
     - One archived process log: where it was written, and its two digests.

   * - ``FileRecord``
     - One file the run read or wrote, with all six facts the specification
       requires plus direction and status.

   * - ``ProvenanceStatus``, ``FileDirection``
     - The two closed vocabularies, each carrying its own **wire name**.

   * - ``ProvenanceSchema``
     - The constants of the format itself: the version, the pinned settings
       keys, the settings-key pattern.

All of them are records, all of them validate in their compact constructor, and
all of them take defensive immutable copies of every collection. The shared
validations live in one package-private ``ManifestChecks`` so that every record
rejects the same thing the same way and every message names the field and
prints the rejected value.

Four shape decisions are worth knowing before changing one.

**Tool identity and one invocation are two records.** ``ToolRecord`` holds the
facts about an installation; ``ExecutionRecord`` holds the facts about one
launch of it. A later phase that runs the same tool twice records two
executions of one identity rather than duplicating the checksums.

**The stage is a string, not a workflow type.** ``ToolRecord.stageId`` keeps
the identifier a ``StageTag`` promises is suitable for a provenance record, and
the workflow's own types stay out of this package. An interface is behaviour; a
manifest is a value that must survive being written to a file and read back
years later.

**A wire name is a field, never** ``name().toLowerCase()``. Two separate
defects are ruled out by that. *Locale*: ``"RUNNING".toLowerCase()`` is
``runnıng``, with a dotless i, under a Turkish default locale -- which is
precisely the class of defect ``R-PROV-04`` exists for. *Renaming*: a Java
constant is a name in this codebase, a wire name is a token in every document
ever written, and deriving one from the other means an ordinary rename silently
changes the on-disk format. The tests pin every wire name as a hand-typed
literal.

**The settings map is an open namespace with a closed key shape.** Later phases
record Percolator's settings, the Limelight conversion parameters and the
result-view q filters there; this phase does not own those semantics, so it
pins the *shape* of a key -- ``[a-z0-9]+(\.[a-z0-9-]+)+`` -- rather than
guessing at names. A phase that adds a key adds a constant beside
``ProvenanceSchema.PERCOLATOR_SEED_SETTING`` and asserts it against the pattern
in its own tests. A key written as a literal at two call sites is a key that
eventually differs at two call sites, and the seed vanishing silently from the
record is exactly what that would look like.

Duration is derived, never stored
---------------------------------

``RunRecord.duration()`` and ``ExecutionRecord.duration()`` subtract their two
instants on demand. Duration is a component of no record.

A stored duration is a third number that has to agree with the other two, and
the day it does not -- a clock adjustment, a copy-paste, a serialiser that
rounds one field and not another -- the record contains a contradiction with no
way to tell which half is wrong. There is nothing to keep in step if there is
nothing to keep.

``AC-PRV-05`` still requires a duration to be *recorded*, and the artefact is
the record: a duration that exists only as a method on a model the reader has
to reconstruct is not recorded in the file. So ``durationMillis`` is computed at
serialisation time by both writers and written beside the two instants -- from
the **truncated** instants, through the shared
``CanonicalTimestamp.millisBetween``, so that the number in the document is one
a reader can recompute from what the document actually shows. Since both
writers derive it the same way from the same function, the JSON and the report
cannot disagree about it either.

``ManifestReader`` **checks** ``durationMillis`` and then throws it away rather
than putting it into the model, which would create the second source of truth
the model exists without.

Timestamps in one place
-----------------------

``CanonicalTimestamp`` is the only way this project writes an instant down:
``uuuu-MM-dd'T'HH:mm:ss.SSS'Z'``, UTC, three fractional digits, ``Locale.ROOT``
and ``DecimalStyle.STANDARD`` fixed at construction.

Fixed width is a requirement rather than a preference. ``ISO_INSTANT`` renders
``...:00Z``, ``...:00.250Z`` or ``...:00.250999999Z`` depending on what the
clock did, so the *shape* of a provenance record would depend on the run; a
reader would have to accept three grammars and a hand-typed expected document
could not be written at all. The locale pinning is the same argument one field
along: ``th-TH-u-nu-thai`` and ``ar-EG-u-nu-arab`` have their own digit sets,
and a timestamp in Thai digits is a timestamp no tool will parse.

**The truncation to milliseconds is real and lossy.** An instant written by
this class and read back is truncated towards the past, so anything comparing a
parsed instant against an original must compare at millisecond precision.
``ProvenanceEvent`` truncates on the way *in* rather than on the way out, so
that an in-memory event always equals the line it produced -- otherwise the
inequality would be invisible in ordinary use and surface as an unreproducible
test on whichever machine has a microsecond-resolution clock.

Hashing: one open, one pass, one buffer
=======================================

``StreamingHashService`` computes MD5 and SHA-256 together, in one pass, over a
file of any size. ``R-PROV-01`` forbids reading a file into memory to hash it
and ``R-PROV-03`` requires both digests from a single pass, and those two rules
are the whole design:

* the file is opened **once**;
* each chunk is read into one reusable ``byte[]`` of ``BUFFER_SIZE`` bytes;
* **both** digests are updated from that chunk before the next read overwrites
  it.

Heap use is therefore constant in the size of the file -- a 2 GB spectrum file
costs the same 256 KiB as a 2 kB parameter file -- and the disk is read once
rather than twice.

The buffer is 256 KiB, and it is the smallest number with all four of the
properties wanted: it is a multiple of 64, so every ``update`` consumes whole
MD5 and SHA-256 compression blocks; a 2 GB file is 8192 reads, so syscall
overhead is noise; the chunk stays in L2 cache between the read and the two
digests, which a multi-megabyte buffer would not; and the heap cost is trivial
even when every stage hashes at once.

**Both halves of the promise are made observable, because correct digests
cannot tell them apart.** An implementation that opened the file, hashed it,
threw the result away and did it all again would return exactly the right
answer and double the I/O on precisely the files this class exists for. So
opening goes through a package-private ``FileOpener`` seam a test can count, and
``hash(InputStream)`` makes the reads countable. Neither is enough alone.

The return type is ``FileHashes``, which cannot represent a file hashed only one
way, so nothing downstream has to remember that rule. The hexadecimal rendering
goes through ``HexFormat``, whose ASCII alphabet cannot vary with the default
locale -- ``String.format`` or ``toUpperCase()`` on a Turkish host can produce a
different string for the same checksum.

The input-hash cache
====================

``CachingHashService`` decorates a ``HashService``. It exists so that a 2 GB
spectrum file is not read again on every run, and it would rather read it again
than record a digest of content nobody read.

Five attributes, and the fifth is the one that works
----------------------------------------------------

``R-PROV-02`` names four -- canonical path, size, modification time and, where
available, file identity. ``FileFingerprint`` carries all four **plus** the
POSIX inode change time (``unix:ctime``).

The four cannot see the case this phase's exit gate is about. Overwrite a file
in place with content of the same length and put the modification time back
with ``Files.setLastModifiedTime``, and the path, the size, the modification
time and the device/inode pair are all exactly what they were while the bytes
are different. That is not an exotic attack: it is what ``cp -p``, ``rsync -t``
and ``tar -x`` do every day. ``ctime`` is updated by the kernel on every write
and on every metadata change *including the timestamp restoration itself*, and
user space cannot set it. It is the only attribute in the set that is evidence
rather than a hint.

Adding it is a strengthening -- strictly more invalidation, never less.

All five attributes are read in **one** ``readAttributes`` call. A fingerprint
assembled from several ``stat`` calls could describe a file that never existed
in that state, which is exactly the sort of not-quite-true record this cache
exists to avoid.

The map is keyed on canonical path alone, because a file has one canonical path
at a time; the rest of the key is stored in the entry and compared on every
lookup. **There is no path by which a cached digest is returned without the
filesystem having been asked, on this call, what the file looks like now.**

When an entry is kept at all
----------------------------

Three conditions, all of them "when in doubt, rehash" in a different disguise.

**Tamper-evident.** The fingerprint must carry both a file identity and an
inode change time. Where either is missing the cache **stores nothing** and
every call reaches the delegate. That is a correct cache with a hit rate of
zero, which is the right trade when the alternative is a hash of content the
tools did not read.

**Unchanged across the read.** The attributes are read before the delegate
hashes and again afterwards, and the entry is stored only if the two agree.

**Settled.** A file whose last change falls in the same one-second tick as the
observation is not cached, because on a filesystem with one-second timestamps a
later write in that tick would be invisible. This is git's "racily clean" rule,
and it is here for the same reason git needs it.

The cache is bounded at 256 entries with strict least-recently-used eviction: a
map keyed on path with no bound is a leak in a long session, where the cost is
not the files the product needs but the 40 000 it browsed once. Both bypasses
``R-PROV-02`` asks for exist: ``rehash(Path)`` for one file and
``disabled(HashService)`` for a whole run, the latter keeping the type so that
wiring does not change to turn the cache off.

The lock is held only for the map operations, never while the delegate hashes.
Two threads asking for the same uncached file therefore both hash it, agree, and
one entry survives; the duplicated work is preferred to serialising every stage
behind one lock.

**The consequence on Windows is a real product behaviour, not an implementation
detail.** Neither file identity nor ``ctime`` is available there, so the cache
stores nothing at all and every run rehashes every input. For a multi-gigabyte
mzML that is a measurable cost. It is the right call for a correctness
mechanism and it is a documented decision rather than a surprise; see
:ref:`dev-provenance-schema-platform`.

The event log
=============

A manifest is one document replaced as a whole; a log is a history that grows
while the run happens and must be readable at every instant in between.
``R-PROV-05`` offers both -- "appendable events, or atomically updated state" --
and this project has both, for different jobs. **A log is deliberately not
written with the atomic writer**: write-temp-then-rename rewrites the whole file
per record, costs the length of the log per event, and would leave the run's
history in a temporary file that the crash deletes.

Three properties, each the answer to a way this goes wrong
-----------------------------------------------------------

**One record per line, terminated by** ``\n``. A crash can then damage only the
last line. A single top-level JSON array would be unreadable in full, because
the closing bracket is written last and a dead process never writes it.

**Every record is forced to the device before** ``append`` **returns.** Not
buffered, not flushed on close, not flushed when the operating system feels
like it. An event log still sitting in a buffer when the JVM dies has no
history in it at all -- and that failure is invisible in every test that does
not cut the power, because the file is byte-identical either way. There is
deliberately no ``BufferedOutputStream`` between the log and the file: a buffer
is precisely the thing that loses the tail. The force goes through an
``EventLogSync`` seam so that a test can count the calls and read the file's
length at the moment each one happened, which pins the order as well as the
fact.

**Sequence numbers are gap-free and strictly increasing.** Whole events can be
lost without leaving a mark in the bytes, so the number is what lets a reader
say that something is missing. The log assigns them; a caller cannot choose
one.

Why the payload is an open map
------------------------------

``ProvenanceEvent`` carries ``sequence``, ``timestamp``, ``type`` and a generic
``payload`` map rather than being a sealed interface with a record per event
type. The alternative reads better at a call site and fails at the point this
package must not fail: redaction would then be per-type, every new event type
would have to remember to send its own new field through the redactor, and the
first one that forgot would open a leak path the existing types had already
closed. With one generic payload there is one place where values are cleaned,
and adding an event type cannot add a field that bypasses it.

The type is still explicit -- ``ProvenanceEventType`` is the tag, pinned on the
wire -- and the payload keys have their own shape rule, which is the settings
pattern with one relaxation: a single bare segment is legal, because a payload
is scoped to one event that already carries its type.

What a torn file yields
-----------------------

``ProvenanceEventLogReader`` **does not throw on damage.** Damage is what it was
written for. An ``IOException`` means the file could not be read at all;
anything wrong with the file's *content* comes back as an ``EventLogDefect`` on
a ``RecoveredEventLog``, beside every event that survived.

Both of the obvious behaviours fail the requirement in opposite directions. A
reader that threw on the first bad byte would discard a crashed run's entire
history -- and the crashed run is the one whose history is worth having,
because the successful one has a manifest. A reader that quietly skipped what it
could not parse would hand back a plausible-looking history with an unannounced
hole in it.

Three defect kinds, and the first two are deliberately distinct:
``TORN_FINAL_LINE`` is the ordinary signature of a crash, ``MALFORMED_LINE``
means the file was edited, transferred in text mode, concatenated or hit by a
filesystem writing a block of zeroes, and ``SEQUENCE_GAP`` is the only damage
that leaves no trace in the bytes. A reader that reported the first two as "a
bad line" would leave a scientist unable to tell "the run was killed" from
"this file has been altered".

``RecoveredEventLog.intact()`` is defined as "no defects at all" rather than "no
torn tail", because a sequence gap is just as fatal to a reproduction claim as a
missing final record. ``highestSequence()`` is the largest recovered number
rather than the last, so a resumed run cannot write a second event 4 and make
the damage permanent.

Two decoding rules are load-bearing. Decoding is **strict**: replacing a
corrupt UTF-8 sequence with ``U+FFFD`` would recover a path that was never on
the disk. And a torn tail is never parsed even when it looks complete: without
the terminator there is no way to know whether the record ends there or whether
the rest of it never reached the disk.

**Reopening heals the tear.** If the file does not end with a newline, one is
written and forced before anything else -- otherwise the first new record would
be concatenated onto the torn one and the two would read back as a single
malformed line, so the old crash would have eaten a record from the new run.

**One writer per file, any number of threads**, on a private monitor. What that
does *not* make safe is stated rather than left to be discovered: two log
instances on one path -- two in a JVM, or one in each of two processes -- each
keep their own counter, so the file gets two events numbered 4 and none
numbered 5. A run needing several processes to write one log needs a lock file
around the whole cycle, which is a design decision for the phase that needs it.

Atomic finalisation
===================

``AtomicDocumentWriter`` writes a file that no reader can ever see
half-written. ``R-PROV-05`` requires that "finalisation shall be atomic
(write-temp-then-rename)", and the phase's exit gate states the consequence: an
interrupted finalise never leaves a truncated ``provenance.json``.

The sequence, and every step of it is load-bearing:

#. **A temporary file in the target's own directory.** Not ``java.io.tmpdir``:
   a rename across filesystems cannot be atomic and degrades to
   copy-then-delete, which is the half-written state this class exists to
   prevent. ``/tmp`` on ``tmpfs`` is the common case, so a writer using it would
   look correct on one machine and produce torn files on another.
#. **Written, flushed, then forced**, while the temporary file is still open.
   Data in the page cache is not on the device, and a power cut between the
   rename and the eventual writeback leaves the directory entry pointing at a
   file of zeroes. The force is ``force(true)`` -- data *and* metadata, because
   the file's length is metadata and a document whose bytes reached the disk
   under a stale length is unreadable in exactly the way ``R-PROV-05`` forbids.
#. **Then the rename**, with ``ATOMIC_MOVE`` and ``REPLACE_EXISTING``.
   ``ATOMIC_MOVE`` is a demand, not a hint: without it ``Files.move`` may fall
   back to copy-then-delete, and it is refused loudly rather than silently
   downgraded where it is not supported. A sync *after* the rename protects
   nothing, because by then the operation it was meant to make durable has
   already happened.
#. **Then the directory is forced, best effort.** Forcing the file makes the
   contents durable; forcing the directory makes the *name* durable.

**The asymmetry is the interesting part.** Opening a directory as a channel is
legal on Linux and macOS and illegal on Windows. By the time that call is made
the document is complete on the device and the rename has happened, so every
byte the caller asked to write is in place under the right name; failing there
would report a failure that did not occur and would make the class unusable on a
supported platform. So the failure is swallowed -- and because swallowing a
failure silently is how gates die, the package-private overload **returns**
whether the directory was synced, so a test asserts both outcomes instead of
trusting this paragraph. The three earlier steps have no such licence.

**A failure leaves nothing behind.** An ``IOException``, a
``RuntimeException``, an ``Error`` or a thread interruption anywhere between
opening the temporary file and completing the rename removes the temporary file
and rethrows, and the target is in exactly the state it was in before the call.
Interruption is never swallowed: a ``FileChannel`` is interruptible, so an
interrupt surfaces as ``ClosedByInterruptException`` with the thread's status
still set, and it is rethrown unchanged rather than converted into a plain
failure a caller cannot recognise as a cancellation.

Why ``Durability`` is a seam
----------------------------

The three filesystem operations whose **order** is the guarantee sit behind one
package-private interface, because every one of them is invisible in the
result. A writer that never calls ``fsync`` produces a byte-identical file,
passes every round-trip test that can be written, and loses the document on a
power cut. A writer that syncs after the rename produces the same file and
protects nothing. Correct output cannot tell any of these apart; the order of
the calls can, and only if something records them.

``moveIntoPlace`` is on the seam for the same reason. An order is a relation
between operations, and a relation can only be asserted where both operands are
observed -- leaving ``Files.move`` to be called directly would make the one fact
that matters most, that the data sync came before the rename, unobservable.
``[syncFile, move, syncDirectory]`` is a list a test compares against a literal.

The seam is not a configuration point. Nothing outside the package may
substitute a different notion of durability for the files a provenance record is
made of.

.. _dev-provenance-schema-redaction:

Redaction lives inside the writers
==================================

``R-SEC-03`` requires that no secret reaches a provenance record or an export,
and the phase's exit gate states it as a property that can be greped for: a
seeded corpus of secrets appears nowhere in the JSON, the RST or the log.

**The rules are not in this module.** They are ``SecretRedactor`` and
``SecretRegistry`` in ``org.cometgui.domain.secrets``, shared with the process
service. Phases 03 and 04 briefly built two rule sets in parallel and the two
keyword lists had diverged within hours, which would have meant a value redacted
in the console log and not in the provenance record -- precisely the silent,
security-relevant drift ``R-SEC-03`` exists to prevent. **A rule added anywhere
else re-creates that defect.** See :ref:`dev-tool-adapters`.

**Redaction is applied inside the writers, not at the call sites.** A redactor
is a constructor argument of ``JsonWriter``, ``RstWriter``, ``ManifestWriter``,
``ProvenanceReportWriter`` and ``ProvenanceEventLog``, and there is no overload
without one. Every string value that goes into a document goes through
``JsonWriter.value(String)`` or its RST twin, and each of those calls
``redactText``.

That is the whole point of the arrangement. A design in which each caller
remembers to redact is a design in which one caller eventually does not, and the
caller that forgets is the field somebody adds next year. **A new field on the
manifest therefore cannot open a new leak path**, which is a property of the
structure rather than of anyone's diligence.

Two things the text rules cannot see are redacted positionally, by the writer
that knows about them, before the value reaches the generic path:

* the **argument array**, where ``--password`` makes the *next* element a
  credential; and
* the **process environment**, where a variable named ``GITHUB_TOKEN`` holds one
  whatever its value looks like.

Redaction is idempotent, so a value that passes through the text rules again on
its way into the document loses nothing by it -- and idempotence is not a
nicety: a value may be redacted on its way into the event log and again on its
way into the manifest, and a rule that mangled its own output would produce
``[[REDACTED]]`` in one artefact and ``[REDACTED]`` in the other for the same
field.

Three further rules a change here must not break:

* **Names are never redacted.** A name is part of the schema, chosen by this
  repository rather than supplied by a run, and a reader meeting
  ``"[REDACTED]": 1`` could do nothing sensible with it.
* **Only long** ``--`` **flags redact the next argument.** ``AC-PRV-03``
  requires the exact argument array to be recorded, and a silently altered array
  is a defect of the same family as a leak. Single-letter options are ordinary
  options for real scientific tools -- Comet's own ``-P`` names the parameter
  file -- so a positional rule over them would blank a file path about as often
  as a password. **The correct fix for a leak found there is to register the
  value**, not to add the flag.
* **Over-redaction is not a safe failure.** It corrupts a record ``R-PROV-01``
  requires to be complete. A path, a digest, a version number and a Comet
  parameter line must come through byte-identical.

The marker is ``[REDACTED]`` and the brackets matter: the same rule set feeds
``provenance.rst``, and in reStructuredText a doubled asterisk opens strong
emphasis, so ``***REDACTED***`` would break a document built with
``sphinx-build -n -W``.

Reading a manifest back
=======================

``ManifestReader`` is the inverse of ``ManifestWriter`` and deliberately **not**
its mirror image. A reader written by inverting the writer line by line would
agree with the writer about every mistake the writer makes; this one is written
against the format, and its tests parse hand-typed documents and assert
hand-typed values with no writer involved. A round trip through the two proves
they agree, which is a weaker statement than either being right.

The same argument runs one level down: this project writes its own JSON reader
and writer rather than taking a dependency, because ``provenance.json`` is the
artefact the product exists to produce, so what counts as a valid one has to be
a property of this repository rather than of a library's default leniency. A
permissive parser is the danger: it accepts a corrupted record and hands back an
object graph that looks like a run that never happened.

Three rules of the reader are worth stating here because they are easy to
weaken by accident. The **schema version is resolved before anything else**, so
a document this build cannot interpret is refused for that reason rather than
for whatever else is wrong with it. **Nothing re-implements a validation**: a
bad digest, a negative size, a relative path, a blank role, an end before a
start, a settings key that is not dotted lower-case are each rejected by the
record that owns the rule, at the moment the reader tries to construct it. And
**no value from the document ever reaches a message** -- a rejection names the
member path and the rule, member names being literals in this repository, and
an underlying rejection is deliberately not attached as a cause, because those
messages quote the value they rejected and a document read from disk may
contain anything.

.. _dev-provenance-schema-platform:

Platform divergence
===================

Everything in this module was built and measured on Linux/amd64. What follows
is the honest state of that, in two tiers, and the distinction between them is
the point: collapsing them would overstate the residue as much as omitting them
would understate it.

The grading rule they establish, which applies beyond this module: *"we could
not run this code on that platform"* is a **testing gap** and does not cap a
grade; *"there is different code on that platform and it has never run"* is
**unverified behaviour** and does.

Tier A -- the divergent branch is executed here, by a faithful stand-in
------------------------------------------------------------------------

Not unexecuted code, and therefore not residue.

**The hash cache's attribute source.** ``FileFingerprint.of`` asks whether the
filesystem publishes the ``unix`` view; without it ``fileKey`` and ``ctime`` are
null, ``tamperEvident()`` is false, and the cache stores nothing. That branch
runs here through a **zip filesystem**, and the stand-in was measured rather
than assumed: the default filesystem publishes ``[owner, dos, basic, posix,
user, unix]`` and a non-null ``fileKey``, and the zip filesystem publishes
``[zip, basic]`` and a null one. So the Windows-*shaped* algorithm is exercised
and mutation-tested. What remains unverified is that Windows is the case it
stands for.

**Directory fsync**, exercised by substituting a ``Durability`` that throws. The
Windows question is sharper than "does it work": opening a directory channel and
**silently succeeding without forcing anything** has identical observable
behaviour to failing, and the opposite durability guarantee.

Tier B -- never executed in any form
-------------------------------------

This is the residue.

**ATOMIC_MOVE under contention, and this is the one that matters.** The
atomic-finalisation gate item is proved by a concurrent reader observing only
whole documents, and that proof is POSIX-only. On Windows a rename over a file
another process holds open can fail with ``AccessDeniedException`` -- and the
Provenance UI, a virus scanner and a sync client are all exactly such
processes. **If Windows cannot replace an open file the repair is a retry policy
or a different finalisation strategy: a design change, not a test.** It is
routed into Phase 13 so that the viewer is not built first and the problem
discovered second.

**Absolute-path validation.** ``Path.isAbsolute()`` is false for ``/var/...``
on Windows, so records valid here are rejected there. That is the actual reason
behind the twenty tests disabled on Windows in this module, and the repair is a
second pinned document with Windows paths -- never a relaxation of the rule.

**Path.toRealPath() as the cache key**, which on Windows also folds case and
8.3 short names.

What was deliberately kept off the list, which is as much a judgement as what
went on it: line endings, number formatting, digests, redaction, and JSON and
RST generation all run identical code everywhere, with the locale-sensitive
paths pinned under Turkish, German and Thai-digit locales. Those are testing
gaps at worst, and admitting them here would dilute the list.

Changing the format
===================

The on-disk consequences of a change to any of this are in
:doc:`../reference/provenance_format`, and its
:ref:`version policy <ref-provenance-format-version>` is the contract. In
summary: a member removed, renamed, re-typed or given a new meaning bumps
``ProvenanceSchema.VERSION``; adding an optional member an older reader can
ignore does not; nothing else escapes a bump.

Two mechanical consequences for anyone making such a change:

* **The reference page and the writer are compared by a test.**
  ``ProvenanceFormatDocumentationTest`` renders a fully populated manifest,
  parses it back and asserts the set of member names against a hand-typed list,
  with the count asserted separately so that an empty walk cannot pass. When it
  fails, the reference page is stale and both must be updated.
* **The wire names and the pinned document are hand-typed literals**, on
  purpose, so that a rename in Java cannot silently take the on-disk format with
  it. Regenerating an expected document from the code under test would remove
  the only thing those tests prove.
