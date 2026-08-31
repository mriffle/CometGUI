=====================================================
PHASE-04 handoff -- Hashing and Provenance Core
=====================================================

:Phase: 04
:Phase orchestrator: Phase-04 orchestrator subagent (session 04)
:Status: IN PROGRESS -- this document is being written as the phase closes
:Last updated: 2026-08-31

.. warning::

   **This handoff is incomplete.** It is started early and deliberately,
   because the platform-divergence analysis below is the part that decides the
   phase's grade and it must not depend on one agent surviving to the end. The
   gate-item evidence, the final measured numbers and the "first thing the next
   agent should do" section are written when the phase closes.
   ``handoffs/PHASE-04-worklog.rst`` is the running record until then.

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
