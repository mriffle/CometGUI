.. _dev-tool-registry:

=============
Tool registry
=============

CometGUI installs every scientific tool it needs by itself. This page is how:
the manifest that describes an artefact, the path from a pinned URL to a
running binary, and the probe that decides whether the binary on disk is usable
on this machine.

It is a design document, not a tour of the classes. Where a decision looks
arbitrary, the constraint that forced it is recorded beside it -- because the
alternative is that a later reader removes the oddity and re-creates the
problem.

Scope. **Phase 05 -- Tool Registry and Installer**
(``phases/PHASE-05-tool-registry.rst``) delivers ``R-TOOL-01``..``R-TOOL-09``,
``R-PLAT-02``..``R-PLAT-05``, ``R-SEC-02``, ``R-SEC-05`` and ``R-SEC-06``. What
each *tool adapter* does with a subprocess is :doc:`tool_adapters`; how a
Percolator version's capability drives command construction downstream is
:doc:`version_capabilities`. What a scientist sees is :doc:`/tool_manager`.

.. contents:: Contents
   :depth: 2
   :local:

The manifest
============

One file, ``manifests/tools.json``, at the repository root. It is the
authoritative copy and it is shipped into ``cometgui-install``'s jar by a
``<resource>`` element pointing at the repository's ``manifests`` directory, so
there is exactly one file rather than a committed original and a copy under
``src/main/resources`` that someone has to remember to update. The reader loads
it from the classpath at ``/tools.json``, never from a relative path that would
work only when the working directory is the repository root.

Adding a tool, a version or a platform is a manifest change plus a probe
result. It is never a code change (``R-PERC-12``).

Every field, and why a missing one is a rejection
-------------------------------------------------

``R-TOOL-01`` and ``R-TOOL-03`` name the fields; the reader is strict, and a
record missing any of them is **rejected with a message naming the field**. A
blank cell in a user interface reads as a fact, so a partially populated record
is worse than no record.

.. list-table::
   :header-rows: 1
   :widths: 28 72

   * - Field
     - What it carries

   * - ``tool``, ``version``, ``releaseTag``
     - The tool identifier (``comet``, ``percolator``, ``pdv``,
       ``limelight-converter``), the version as **upstream spells it**, and the
       upstream release tag the artefact came from.

   * - ``os``, ``arch``
     - The platform, as the pair whose identifier is ``os-arch`` --
       ``linux-x86-64``, ``macos-aarch64``, ``windows-x86-64`` and so on. Every
       record carries both, which is why a platform-independent artefact is
       several records; see :ref:`dev-tool-registry-one-download`.

   * - ``kind``
     - The artefact kind, from which the extractor is chosen. Never inferred
       from the URL suffix. See :ref:`dev-tool-registry-kinds`.

   * - ``url``, ``sizeBytes``, ``sha256``, ``md5``
     - The pinned download. ``https`` only. ``R-SEC-02`` makes SHA-256 the
       integrity authority; MD5 is recorded for provenance and is never the
       trust mechanism.

   * - ``member``, ``memberSizeBytes``, ``memberSha256``, ``memberMd5``
     - For a ``ZIP``: the archive member to take, and its own digests. See
       :ref:`dev-tool-registry-named-member`.

   * - ``expectedExecutablePath`` / ``installedPath``, ``executable``
     - Where the file lands inside the installed tool directory, and whether it
       gets an executable bit. Both JARs are marked non-executable, which is
       why "the installed path is executable" is false for half the managed
       tools.

   * - ``licence``
     - ``spdx``, the upstream licence URL, and a note recording what the
       upstream licence file actually says. Metadata for
       :doc:`/citations`; the licence audit itself is a human sign-off.

   * - ``companions``
     - Second downloads installed atomically with the binary (``R-TOOL-02``),
       each with its own kind, URL, digests, the members taken out of it and
       where each lands. See :ref:`dev-tool-registry-companions`.

   * - ``capabilities``
     - What the build is expected to be able to do -- each claim carrying the
       **evidence** behind it and a note naming who established it. See
       :ref:`dev-tool-registry-evidence`.

   * - ``advisories``
     - The caveats ``R-PERC-11`` requires to be shown at selection time and
       recorded in provenance, each with a stable identifier and a sentence.

   * - ``minimumHostRequirements``
     - ``glibc``, ``glibcxx``, ``macos`` and ``requiredHostLibraries``, so the
       application can explain in advance that an artefact will not run here
       instead of discovering it at probe time (``R-TOOL-03``).

   * - ``minimumCometGuiVersion``
     - The oldest CometGUI that understands this record.

.. _dev-tool-registry-evidence:

Capability is a field with an evidence value, never a bare claim
----------------------------------------------------------------

Every capability in the manifest says how it was established:

``observed-by-execution``
    This project ran that binary on that platform and watched it do this.

``inferred-from-artefact-bytes``
    The claim comes from markers found inside the artefact -- the pout-XML
    writer's own strings, for instance -- and not from running it.

``unverified``
    The claim comes from upstream's release material alone.

The words *verified*, *confirmed*, *proven* and *tested* do not appear in the
manifest at all, and a test in ``cometgui-install`` enforces that mechanically
over the file's text. The rule is deliberately blunt: banning the words
outright is checkable, while banning them "only beside an unverified row"
requires a judgement nobody will make at three in the morning. ``unverified``
is an evidence value and does not match, because the word boundary falls before
``un``, not before ``verified``.

The same rule is applied to what reaches these pages: the generator that
renders the tables below refuses to write a fragment containing any of those
four words.

None of this outranks the probe. ``R-TOOL-07``'s probe runs the installed
binary on the host and its answer is what the application acts on. The evidence
field exists so the Tool Manager can say what is known *before* the probe runs,
and so that a fabricated row is visible as one.

.. _dev-tool-registry-one-download:

Selection, and one row per download
-----------------------------------

``select`` answers "what is on offer on this host" for a tool, a version and a
host platform. Two rules in it are worth knowing before reading the code.

**A platform-independent artefact is still one record per platform.** The
specification requires an operating system and an architecture in every record,
so PDV's zip and the Limelight converter's JAR are five records each naming one
file. ``select`` collapses them; the raw record list does not. Anything that
means "what is on offer here" goes through ``select``.

**The key is the download URL, not the version.** On Apple silicon both the
``macos-aarch64`` and the ``macos-x86-64`` rows are runnable, so keying the
one-row-per-download rule on the version would have shown PDV's 103 MB download
twice, the second marked as running under Rosetta 2 -- a false statement about
a Java program. Comet is the opposite case: its two macOS builds are two
different files with two different digests, so they are two genuine offers,
with the native one ordered before the translated one (``D-004``).

That distinction bit twice in this phase, in both directions, and it is the
rule to carry: **the download URL is how this product asks "is this the same
build?"**. The identity probe still compares *versions*, because the question
that stage asks is "is this binary the release the manifest pinned", which a
banner answers as a version; the two macOS Comet rows genuinely are both
2026.02.2, and the SHA-256 checked four steps earlier is what tells those two
files apart.

.. _dev-tool-registry-kinds:

Artefact kinds
==============

The extractor is selected from the ``kind`` field, never inferred from the URL
suffix alone (``R-TOOL-01``).

.. list-table::
   :header-rows: 1
   :widths: 22 78

   * - Kind
     - Used for, and how it is unpacked

   * - ``BARE_EXECUTABLE``
     - Comet on every platform, and Percolator 3.09 on Windows -- whose release
       publishes the executable itself rather than a portable archive. The
       bytes are the file.

   * - ``ZIP``
     - Percolator's portable ``noxml`` archives, and PDV. A single named member
       is taken; see :ref:`dev-tool-registry-named-member`.

   * - ``TAR_GZ``
     - Supported, multi-entry, carrying the full guard set.

   * - ``JAR``
     - PDV's and the Limelight converter's Java artefacts. Installed as a file;
       identity comes from the JAR manifest or a banner, not from an exec.

   * - ``DEB_PAYLOAD``
     - ``ar`` plus ``data.tar.*``. Used **only** to fetch the Percolator XSD
       companions. The installer never runs a package manager.

   * - ``PKG_PAYLOAD``
     - ``xar!`` plus gzip plus ``070707`` cpio. The same, on macOS.

``NSIS_PAYLOAD`` is **not implemented** and must not be reinstated without a
new owner decision. ``D-002`` option C took the Percolator binary from the
portable zip on every tier-1 platform, which deleted the need for it;
``DEB_PAYLOAD`` and ``PKG_PAYLOAD`` survive only for the XSD companions.
Installers are never executed.

.. _dev-tool-registry-named-member:

Why a ``ZIP`` record names its member, and the archive's own path never places a file
--------------------------------------------------------------------------------------

Forced by a real upstream artefact, not by taste.

``rel-3-06-05/percolator-noxml-osx-portable.zip`` holds exactly one member, and
it is named::

    ../my_build/percolator-noxml/src/percolator

A correct ``R-SEC-05`` traversal guard rejects that archive. So under a naive
design Percolator 3.06.5 would be uninstallable on macOS -- and the obvious
repair, taking the basename, is exactly the weakening this project forbids: it
would turn the guard into a sanitiser and leave every other archive relying on
it. The 3.07.1 and 3.09 macOS zips make the same point more mildly, their one
member being
``Users/runner/work/percolator/percolator/build/percolator-noxml/src/percolator``.

The design resolves it without touching the guard. **For a ``ZIP`` the manifest
names the member and the destination, and no output path is ever derived from
an archive entry name.** That is stronger than sanitising the name, not weaker:
for a single-member portable archive, no attacker-controlled string reaches the
file system at all.

And both halves hold at once. Multi-entry kinds -- ``TAR_GZ``,
``DEB_PAYLOAD``, ``PKG_PAYLOAD`` -- are extracted wholesale and carry the full
``R-SEC-05`` guard set, and **the guard that rejects that traversal entry is
still exercised, against that same real upstream artefact**: the 3.06.5 macOS
zip is installed in named-member mode *and* rejected whole, with the entry
named and the destination's parent directory walked afterwards to show nothing
escaped. A design that made the guard unreachable would have been the defect
this one avoids.

Download, check, extract, install, probe
========================================

The path from a pinned URL to a tool the application will run.

The download
------------

``org.cometgui.install.download`` speaks ``java.net.http``. Four facts about
the real upstream host shape it, each measured against that host rather than
taken from what HTTP permits.

**A release download is a redirect to a signed URL that expires.** A
``github.com`` release URL answers ``302`` with ``content-length: 0`` and
redirects to an asset host with a signature good for about an hour. Two
consequences: the client **must follow redirects** -- Java's ``HttpClient``
defaults to ``Redirect.NEVER``, and a downloader that forgets this writes a
zero-byte file and says nothing -- and a resume attempted later must re-request
the original URL to get a fresh signature rather than reusing a stored redirect
target.

**Range requests work.** The asset host answers ``accept-ranges: bytes``, and a
ranged request returns ``206`` with a ``content-range`` and an ``ETag``. Resume
is genuinely implementable.

**``If-Range`` is ignored, and this is the trap.** Sent a deliberately stale
validator, the server still answers ``206`` with the partial range rather than
``200`` with the whole body -- observed twice, through the redirect and again
directly against the signed URL, with the response even carrying the real
ETag. So the standard mechanism for "tell me if the file changed under my
partial download" does not work here: if upstream re-tags an asset between the
first attempt and the resume, a client trusting ``If-Range`` splices bytes from
two different files and no HTTP status reveals it.

The rules that follow:

#. Record the total length and the ``ETag`` seen on the first attempt and
   discard the partial file if either differs on the resume. Cheap, but
   **advisory only** -- nothing obliges a server to keep an ETag stable.
#. **The mandatory SHA-256 check is the sole integrity authority**
   (``R-SEC-02``), and it is what actually catches a spliced download.
#. **A resumed download that fails its checksum discards the partial file and
   restarts from zero**, and never resumes again. Resuming a second time
   splices the same corruption back in and fails identically, which reads as an
   upstream fault when it is the client's own.

Cancellation is not failure and not resume: **a cancelled transfer deletes its
partial file**, so a cancelled 103 MB download restarts from zero. Resume
survives a *failure*, not a cancellation.

A vanished or re-tagged upstream artefact is reported as an **availability**
failure naming the URL and the expected checksum -- never as a corrupt download
and never as a probe failure. ``D-008`` downloads tools rather than
redistributing them, so the project holds no copy to fall back on, and telling
the two apart is what lets a user act.

.. _dev-tool-registry-loopback:

Why the HTTPS rule has a loopback-literal carve-out
----------------------------------------------------

The downloader accepts ``https`` anywhere, plus plain ``http`` to a
**127.0.0.0/8 or ``::1`` literal only**. That is a deliberate narrowing of
"HTTPS only", and the reasoning is recorded because a later reader will
otherwise close it or widen it.

*What it buys.* The routine test suite serves real artefact bytes from a
loopback server, so the download, checksum, extraction, install and probe path
is exercised end to end against the real bytes through the real product code.

*Why it is narrow enough to be safe.* The property ``R-SEC-02`` protects is
integrity against an intermediary, and a connection to a loopback **literal**
has no intermediary because it has no path off the machine. The carve-out is
literal-only and anchored -- matched whole, so ``127.0.0.1.example.com`` cannot
slip through -- and host **names** are refused, including ``localhost``,
because a name resolves at connect time and a name-based check therefore
answers a different question from the connection that follows. Credentials in
the URL are refused over both schemes, loopback included.

*Why it cannot reach product data.* Every manifest record is required to be
``https``, and an assertion over the shipped manifest holds that. The
loopback rule lives one layer lower, in the request, and no manifest record can
carry a loopback URL.

*Why the alternatives are worse.* A strict production rule plus a permissive
test-only seam would run every transfer test through a seam production does not
use -- a property proved through the wrong door. An HTTPS loopback server needs
either a new dependency or a committed private key, and a committed private key
is strictly worse than a plain-text connection to an address that never leaves
the machine.

Extraction, and the guards that apply to every kind
----------------------------------------------------

``R-SEC-05`` is one implementation covering every artefact kind, so that a kind
added later cannot arrive without the guard set: path traversal, absolute
paths, unsafe symlinks and decompression bombs, the last of which bites
separately on ratio, on absolute size and on entry count. A truncated archive
is refused. Payload entries carrying a ``./`` prefix inside a ``.deb`` tar or a
``.pkg`` cpio are normalised -- by normalising, not by trimming whatever the
archive happens to start with.

Two structural rules are worth knowing before changing this package.

**Only the extraction guard touches the file system.** A structural check
parses each compiled class's constant pool and requires that no other class in
the package can create, write, link, copy or delete a file, with anti-vacuity
checks so that a scan which found nothing fails rather than passes.

**The one parser that reads hostile input is hardened, and the hardening is
observable.** A ``.pkg``'s xar table of contents is XML that arrived over the
network inside a downloaded file. The parser forces five settings -- secure
processing on, ``DOCTYPE`` refused, XInclude off, entity references not
expanded, namespace awareness off -- and it forces them onto a factory the
caller supplies, rather than inspecting a fresh factory whose defaults would
make two of the five unobservable and therefore deletable with the suite
green. It
also builds from ``newDefaultInstance`` rather than ``newInstance``, because
``newInstance`` consults a system property and the class path, so a dependency
could otherwise change which parser reads that document while the hardening was
written for a different one.

The eight steps, the marker and the lock
-----------------------------------------

An install is the specification's eight steps, driven from an enumeration, so
that a step added without an action **stops the installer** rather than being
skipped:

#. ``DOWNLOAD_TO_TEMPORARY_FILE``
#. ``VERIFY_SHA256`` -- and it re-hashes the file rather than trusting the
   digest the download reported, because a source that lies otherwise passes
#. ``EXTRACT_WITH_GUARDS``
#. ``VERIFY_EXPECTED_LAYOUT``
#. ``APPLY_PLATFORM_FIXUPS`` -- executable bits; macOS quarantine removal
#. ``PROBE``
#. ``MOVE_ATOMICALLY_INTO_CACHE``
#. ``RECORD_INSTALLATION_METADATA``

The cache is one root, so that staging and the installed tree are on the same
file system and step 7 is a rename rather than an interruptible copy::

    <root>/tools/<tool>/<version>/<platform>/     an installed tool
    <root>/cache/downloads/<key>/                 artefacts being fetched
    <root>/cache/staging/<id>/                    an install being built
    <root>/cache/locks/<key>.lock                 the R-TOOL-05 lock files

The version directory is the **normalised** version, because two spellings of
one version (``3.09`` and ``3.09.0``) must not become two directories; the
marker and every string a person reads keep upstream's spelling. Percolator
3.07.1 installs under ``percolator/3.7.1/`` and calls itself 3.07.1 everywhere
a user sees it.

**The marker is written last**, after the atomic move, in
``.cometgui-install.json``. It carries the length and both digests of every
file the manifest names, plus the payload entry count, and the cache re-hashes
what the marker records every time it is asked whether a tool is installed --
because a cached answer is a second place the truth can live. A marker whose
recorded digest no longer matches the file on disk makes the entry
*not installed*, not installed-with-a-warning.

An install halted after each of the eight steps in turn -- in a second JVM, by
``Runtime.halt``, so there is no unwinding and no ``finally`` -- leaves
``NOT_PRESENT`` after steps 1 to 6, ``NO_MARKER`` after step 7, and
``INSTALLED`` only after step 8.

**The lock is two locks** (``R-TOOL-05``), because a ``FileLock`` is held by
the *process*: a second attempt to lock the same region inside one JVM throws
rather than waiting. So a JVM-wide monitor keyed by the lock file's absolute
path comes first, then the file lock. The lock file is created and never
deleted, because deleting it on release races: a second process can be blocked
on the very file being unlinked, would then hold a lock on an inode nothing
else can reach, and both would proceed.

A lock that has never been observed to block has not been shown to work. This
project has already paid for the other kind -- a lock file sitting in the build
directory that nothing took, while the authoritative record told readers builds
were serialised. So the two-JVM check is paired with a control in which the
lock is left out and the same harness observes the overlap.

The three-stage probe
=====================

``R-TOOL-06``: three ordered stages, each with distinct failure states, and a
failure at one stage says **nothing** about the stages after it.

``LOADABILITY``
    Does the binary start at all? The dynamic loader, the architecture, the
    executable bit and, on macOS, Gatekeeper.

``IDENTITY``
    What does it say it is? A version parsed from the binary's own banner, and
    compared with the version the manifest pinned.

``CAPABILITY``
    What can it actually do? Functional, never textual.

A stage is reached only when every earlier stage passed, and a tool that fails
loadability is **never offered for selection**. Conflating the stages is the
specific defect this phase exists to avoid: reporting a missing Visual C++
runtime as "not XML-capable" tells a Windows user to change Percolator version
when what they need is a runtime.

Two consequences of that ordering are written into the code rather than left to
a reader. An ambiguous failure kind takes the **earliest** stage it could
belong to, because the safe direction is "we did not establish that it starts"
and never "we established that it cannot do this". And a capability probe that
could not run must never become an **empty capability set**, because an empty
set is positive evidence of absence under ``R-TOOL-08``.

The ``R-PLAT-03`` diagnostic
-----------------------------

A loader failure produces a diagnostic naming the required version, the host's
version and the alternatives available. The classifier is written against text
observed on this host rather than invented: a missing shared object
(``error while loading shared libraries: libboost_filesystem.so.1.83.0``, exit
127) and, with that object stubbed out, the symbol-version failure beneath it
(``version 'GLIBCXX_3.4.32' not found``, ``version 'GLIBC_2.38' not found``).
The **GLIBCXX** line is reported before the GLIBC one, which is why the
advance check reads a C++ runtime floor as well as a glibc floor; a check that
knew only about glibc would predict "runnable" for a binary that fails on
``libstdc++``.

**Alternatives are excluded by download URL, not by version.** Excluding by
version is right until one version is two rows: if Comet's native Apple silicon
build fails to load, the x86-64 build of the *same version* is the alternative
``R-PLAT-03`` requires to be named, and a version-keyed filter would have told
that user "Alternatives: none known" while a managed build that ``D-004`` says
runs there sat in the manifest. Excluding by platform fails in the other
direction, deleting every alternative there is.

**An unreachable binary is one refusal, not a collapsed list.** A binary the
probe cannot start at all produces its own refusal carrying its own diagnostic,
and every other candidate is still decided. "Offered" is not among the
available answers, and neither is "the whole offer list failed".

.. _dev-tool-registry-functional-probe:

Why capability is probed functionally rather than from ``--help``
------------------------------------------------------------------

Because ``--help`` discriminates nothing, measured rather than assumed.

The portable ``noxml`` Percolator binary and the ``.deb``'s binary print
**byte-identical help text, 17 928 characters each**, both listing
``--xmloutput`` and ``--decoy-xml-output``, both exiting 0. A text probe reads
the same string from a build that writes pout XML and from one that does not.
Note also that the help text arrives on **stderr**, so a probe reading stdout
alone sees an empty string -- which is its own way to get a false negative.

So ``XML_OUTPUT`` is established by running the binary over a synthetic PIN and
inspecting the file it writes: the root element, the
``http://per-colator.com/percolator_out/15`` namespace, and the exact
``<psm>`` count.

**"The output file exists" is not a probe condition.** On an under-sized
fixture the binary exits 1 with *"median decoy score <= score at 1% FDR"* and
the output file **exists and is zero bytes**. Reading existence as success
would report the capability that is absent.

**The fixture is 64 target and 64 decoy rows**, and the size is measured.
Sweeping 50 seeds through the real 3.07.1 binary aborted **10 times out of 50**
at 8+8 and **0 out of 50** at 64+64. An under-sized fixture therefore makes the
probe *unreliable* -- roughly one draw in five misreports a capable binary --
rather than reliably wrong, which is worse: an intermittent negative cannot be
told from a real one. Any negative control at 8+8 must pin a seed measured to
abort, and the seed in the code is one of those ten. It is not a tidy-up
candidate.

The PIN generator formats every numeric column under ``Locale.ROOT``. That is
load-bearing rather than tidy: under a comma-decimal locale the feature columns
come back with commas and Percolator cannot parse the row, and under a
Thai-digit locale **every** numeric column comes back in Thai digits -- the row
index, the label, the scan number and the accession as well as the three
feature columns. The result would be a malformed PIN, which Percolator refuses,
which the probe reads as *"this build cannot write XML"*: a false negative on
the one capability the functional probe exists to establish, reintroduced
through a different door.

Identity, where the binary cannot print a version
--------------------------------------------------

**PDV cannot print a version on any host.** Its command-line entry point
extends ``JFrame`` and builds the frame before reading its first argument, so
``-h``, ``-v``, ``-V`` and ``--version`` all exit 1 with a
``HeadlessException``, and running headless is what *causes* it. Identity is
therefore read from the JAR manifest's ``Implementation-Version`` -- out of
bytes whose SHA-256 was checked four steps earlier -- and the limit is recorded
rather than dressed up as a launch. The Limelight converter does print a banner
and exit 0, so it is identified by launching it.

.. _dev-tool-registry-companions:

Companions
==========

Comet's Thermo RAW support
--------------------------

``CometWrapper.dll``, ``ThermoFisher.CommonCore.Data.dll`` and
``ThermoFisher.CommonCore.RawFileReader.dll`` are companions of the Windows
Comet record, and an install missing them **does not advertise**
``THERMO_RAW_WINDOWS`` (``R-TOOL-02``).

The Windows Visual C++ runtime
------------------------------

The Windows Percolator portable zip is the bare executable and carries none of
the runtime it imports. ``MSVCP140.dll``, ``VCRUNTIME140.dll``,
``VCRUNTIME140_1.dll`` and ``VCOMP140.DLL`` are declared as required host
libraries, and their absence is reported as an ``R-PLAT-03`` **loader** failure
naming the DLL -- never as "not XML-capable". ``bcrypt.dll`` is deliberately
not in that list: it is a system DLL, not a redistributable.

The XSD pair, and why Windows takes it from the Linux ``.deb``
---------------------------------------------------------------

**No portable archive ships an XSD.** Every portable zip upstream publishes,
3.06.5 through 3.09 and on all three platforms, holds exactly one member: the
bare executable. ``R-TOOL-02`` requires ``percolator_out.xsd`` and
``percolator_in.xsd`` beside the binary, so they are a second, small download
from the matching ``noxml`` package -- the ``.deb`` on Linux, the ``.pkg`` on
macOS, as the specification prescribes.

The specification is **silent on Windows**, because ``D-002`` option C deleted
NSIS payload extraction and the NSIS installer is the only Windows artefact
that carries the schemas. The survey found that the pair is **byte-identical
across platforms and across both versions that ship it**: ``percolator_out.xsd``
``21204c89...`` and ``percolator_in.xsd`` ``fa50a550...`` come out identical
from the 3.07.1 ``.deb``, the 3.07.1 ``.pkg``, the 3.06.5 ``.deb`` and the
3.06.5 ``.pkg``.

So **Windows fetches the two schemas from the Linux ``noxml`` ``.deb``**. That
was escalated rather than decided by an agent, and answered on 2026-09-02 after
the byte-identity was re-derived independently. The reasoning, recorded here
because taking a Debian package's payload onto a Windows machine looks like a
mistake to a later reader and an undocumented oddity becomes somebody's
cleanup:

* the file Windows would have got from the NSIS installer **is** the file it
  gets from the ``.deb`` -- same bytes, same digest;
* it fills a silence in the specification rather than contradicting its text;
* it redistributes nothing, reverses no owner decision and executes no
  installer; and
* the alternatives were worse: installing without the schemas leaves
  ``R-TOOL-02`` unmet on one platform, and reinstating NSIS extraction reverses
  an owner decision no agent may reverse.

Why the shipped ``percolator_out.xsd`` cannot validate the binary's own output
------------------------------------------------------------------------------

The schema declares ``majorVersion`` as ``use="required" fixed="2"``. The 3.07.1
binary writes ``3``. So the schema as shipped **cannot validate that binary's
own output unmodified**, and a later phase that wires it up as a validation gate
will find every document rejected on an attribute that has nothing to do with
the document's correctness.

The XSDs are therefore a **provenance and validation asset, not a runtime
prerequisite and not, as shipped, a working validation gate**. Phase 00
established by execution that the binary writes pout XML with no XSD present at
all, which is why an install missing them is a provenance gap rather than a
broken tool. The registry records that distinction on the companion record
itself rather than leaving it implicit.

.. _dev-tool-registry-generated-tables:

The tables on this page are generated
=====================================

``docs/platform_support.rst``'s artefact matrix and the provenance table below
are restatements of ``manifests/tools.json``. Typed into a page, they would be a
second copy of the manifest with nothing keeping it in step -- and this project
has twice paid for a second copy of something that then diverged.

So they are **generated during the documentation build**, exactly as the
traceability report (``R-DOC-03``) is. ``scripts/toolmatrix.py`` reads the
manifest; ``docs/conf.py`` calls it from a ``builder-inited`` handler, before
Sphinx reads the source tree; the pages pull the result in with
``.. include::``. Two consequences, and they are the point:

* **there is no committed copy to drift.** The fragments live under
  ``docs/_generated/`` and are gitignored, so the only way to change what these
  tables say is to change the manifest or the generator; and
* **a manifest the generator refuses fails the documentation build.** The
  generator validates every record -- required fields, platform identifiers it
  knows, ``https`` URLs, digest shapes, a positive size, an evidence value on
  every capability claim and a note behind it -- and rejects a bad record with a
  message naming the record and the field. It also refuses to write a fragment
  containing the words *verified*, *confirmed*, *proven* or *tested*.

Running it alone is how its reaction to its input is demonstrated::

    python3 scripts/toolmatrix.py --check
    python3 scripts/toolmatrix.py --manifest <an edited copy> --out-dir <a scratch dir>

A platform added to the manifest that the generator does not know is a build
failure rather than a row silently missing from the page, which is the failure
mode a generated table exists to prevent.

.. _dev-tool-registry-provenance:

Artefact provenance
===================

Every artefact and companion the manifest pins, with the URL it comes from, its
size, the SHA-256 the installer requires before the bytes are unpacked or run,
and the licence the record carries. Nothing is redistributed: ``D-008`` fetches
each of these from upstream at install time, so no release artefact of this
project contains one.

.. include:: /_generated/tool-artefact-provenance.rsti

What has been observed, and what has not
========================================

The honest limits of everything above, in one place.

**The routine test suite proves the path, not upstream's availability.** It
serves the real artefact bytes over real HTTP from a loopback mirror, through
the real downloader, the real checksum check, the real extractor, the real
atomic move and the real probe, and it runs the real binaries. What it does not
prove is that upstream is still reachable. A separate opt-in run fetches every
manifest artefact from its real URL -- 115 982 855 bytes from five URLs across
three repositories, about 11 seconds -- and a nightly manifest check belongs to
Phase 15.

**Linux x86-64 is where this project has watched binaries run.** Comet's and
Percolator's capability sets are ``observed-by-execution`` there. The four
managed tools install from an empty cache and probe, driven through the Tool
Manager interface. Install times measured on the development machine: Comet 275
ms, Percolator 1412 ms, PDV 2200 ms, the converter 653 ms.

**macOS is unverified.** Phase 05's exit gate item 9 -- a freshly installed
managed tool executing on macOS without a Gatekeeper refusal -- is **not met**.
The job that could meet it has run once, as macos-gatekeeper run 36918810975 on
a hosted ``macos-latest`` runner, and it settled one question and left one
open. Java's extended-attribute view cannot see macOS's
``com.apple.quarantine`` at all: the JDK's macOS view prefixes ``user.`` to
every attribute name, so the quarantine removal as it then was removed nothing.
Phase 05 unit 14 moved the removal to ``/usr/bin/xattr``, run through the
product's process service, and the job now grades the attribute with
``xattr -p`` on its own; that change has not yet run on a Mac. The question
left open is Gatekeeper's: on that runner a quarantined binary ran anyway, so
the job could not go red there, and whether an unsigned upstream binary is
refused for signature reasons independent of quarantine is still unknown.

**Windows has one execution, on one hosted image.** A ``windows-latest`` runner
executed Percolator 3.07.1's portable ``noxml`` binary once, which is why that
one manifest row reads ``observed-by-execution`` and names the run. One run on
one hosted runner is not a clean end-user machine, and no other Windows row
claims more than an inference.

**Percolator 3.09 publishes nothing for Linux**, and it is shown as unavailable
rather than hidden. Its ``.deb`` needs ``GLIBC_2.38`` **and**
``libboost_filesystem.so.1.83.0``, which it does not ship, and there is no
portable archive. Absent is the honest entry.

**``R-SEC-06`` is vacuously satisfied in this phase.** ``D-002`` option C means
the project builds no tool binaries, so the rule has no subject here. It is
recorded plainly rather than written up as delivered work.
