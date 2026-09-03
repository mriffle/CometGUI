====================================================================
PHASE-05 handoff -- Tool Registry and Installer (to a successor)
====================================================================

:Phase: 05
:Written: 2026-09-03
:Outcome: **INCOMPLETE. Units 1-7 of 13 signed off. Stopped after unit 7 by
   owner instruction, not because anything is wrong.**
:Written by: the second Phase-05 phase orchestrator (session 06), who took over
   mid-phase at unit 6 and will not be available to answer questions
:Supersedes: the revision written on 2026-09-02 at unit 5, which is in git
   history at ``be5dd72``. That document said "units 1-5 signed off, 6-12 not
   started"; treat any copy of it you find as stale.
:Records: ``handoffs/PHASE-05-worklog.rst`` -- **this document is the map; the
   work log is the proof.** Every figure here is backed there by the command
   that produced it.

.. contents:: Contents
   :depth: 2
   :local:

Start here
==========

Do these five things before you plan anything.

#. ``cd "$(git rev-parse --show-toplevel)"`` and ``. ./tools/env.sh``. **The
   checkout is not at** ``/workspace``; documents older than 2026-08-31 say it
   is and are stale text, not a second tree.
#. Read :ref:`p05h-unsigned`. A unit 8 was dispatched and cancelled mid-flight;
   its work exists under a git tag, unverified, and it found a live defect on
   its way out that unit 10 will otherwise walk into.
#. Read ``handoffs/PHASE-05-worklog.rst`` in full. It is long. It holds every
   injection with its exact failure text, which is what makes seven sign-offs
   checkable rather than assertions from a stranger.
#. Run ``bash scripts/build.sh`` on a quiet tree. See :ref:`p05h-figures` for
   which recorded numbers you may trust without re-taking and which you may not.
#. Read :ref:`p05h-nowhere`. It is the part that dies with me.

The one-paragraph version
=========================

This phase makes "the scientist installs nothing by hand" true: a manifest of
pinned upstream artefacts, a downloader, checksum verification, safe
extraction, an atomic install, a three-stage probe, tool adapters, and a Tool
Manager UI. **Everything up to and including the probes and the tool adapters
is built, gated and proven against real upstream artefacts. The Tool Manager
runtime, the UI and the end-to-end install are not.** The phase's expected
grade is ``PARTIAL``, because gate item 9 needs macOS.

.. _p05h-unsigned:

Unit 8 was dispatched, stopped and reverted -- and it found a live defect
==========================================================================

The owner's instruction to stop after unit 7 arrived while a unit 8 agent was
working. **It had already committed when my stop order reached it.** It reverted
its own commit with ``git reset --hard 0630d6d`` and preserved the work under a
tag rather than letting the reflog expire::

    phase05-unit8-cancelled  ->  be2edfc  (24 files, ~5000 insertions, UNVERIFIED)

**Nobody has verified any of it.** I did not run its build, read its diff or
inject anything, because doing so *is* unit 8's sign-off, which is precisely the
work I was told not to start. Treat that tag as a **reading reference, not a
starting point you can trust**: it changed ``cometgui-domain``'s ``ToolOffer``,
and ``cometgui-domain`` is mutation-critical with a hand-typed survivor set
pinned in ``scripts/verify-test-gates.sh``. If you build on it and control 0
fails, that is the control working as designed -- kill the new mutant with a
test or argue equivalence in the production code, and **never add an entry to
that list to make a build pass**. Delete the tag with ``git tag -d
phase05-unit8-cancelled`` if you would rather it did not exist.

**The tree is at ``0630d6d``, which I built and gated myself**, so you start
from a state that is known green rather than from unjudged work. That is the
outcome I wanted and I did not have to force it: the agent proposed the revert
and the tag itself.

.. _p05h-cancel-defect:

The live defect unit 8 found before it stopped, which is the phase's shape again
----------------------------------------------------------------------------------

**Cancelling an install mid-download reports ``FAILED``, not ``CANCELLED``.**
Reproduced three times against the real PDV artefact, cancelled at 4 MB over
loopback: ``expected: <CANCELLED> but was: <FAILED>``.

``InstallPipeline`` hands the same ``DownloadCancellation`` down into the
transfer; ``HttpDownloader`` honours it between chunks and raises
``DownloadCancelledException``; **nothing translates that into
``InstallCancelledException``**, so it leaves ``ArtefactInstaller.install``
through the ``catch (IOException)`` arm and the listener is told the install
failed. ``InstallHandle.cancel()``'s own Javadoc forbids exactly this: *"the
listener then sees a report carrying ``InstallPhase.CANCELLED``, never
``InstallPhase.FAILED`` -- a user who cancelled has not encountered an error."*

**Why it survived unit 5's sign-off**, which is the part worth carrying:
``FakeFetcher`` ignores cancellation, so cancellation was graded **only at step
boundaries** and never *inside* a transfer. That is this phase's signature hole
for the fourth time -- a rule graded at one point on an axis it does not depend
on. Unit 5's interruption proof enumerates all eight steps and is sound; the
axis it never varied is *where within a step* the cancellation lands.

**It is not a defect this phase introduced today** -- it has been there since
unit 5 -- **but it lands squarely on the 99 MB PDV transfer that
``phases/PHASE-05-tool-registry.rst`` singles out for deliberate cancellation
testing**, which is unit 10's gate item 1 work. The reported fix is about ten
lines in ``InstallPipeline.runNextStep``. I have **not** verified the diagnosis
myself; it is reported here as the agent gave it, with its evidence, and the
first thing to do with it is reproduce it.

.. _p05h-tree:

The tree you are inheriting
============================

.. list-table::
   :header-rows: 1
   :widths: 34 66

   * - Thing
     - State

   * - ``git status --short``
     - empty

   * - ``HEAD``
     - ``0630d6d``, **one commit ahead of ``origin/main``**, and it is the last
       commit I verified myself. Tier 1 holds the push.

   * - ``bash scripts/build.sh`` at ``0630d6d``
     - ``11/11 stages OK in 1331 seconds. BUILD OK``,
       ``tests=3532 failures=0 errors=0 skipped=3``

   * - ``bash scripts/verify-test-gates.sh`` at ``0630d6d``
     - ``37 assertion(s) passed, 0 failed, in 2947 seconds``, exit 0

   * - Modules with code from this phase
     - ``cometgui-domain`` (``org.cometgui.domain.tools``),
       ``cometgui-install`` (registry, download, verify, archive, cache, probe),
       ``cometgui-tools`` (api, comet, percolator, pdv, limelight)

   * - Mutation-gated modules
     - ``cometgui-tools``'s switch was flipped on by unit 7 and **must never go
       back off**. 14 critical package prefixes.

The three skips are two of Phase 04's and one opt-in network test declining
without its flag, with the reason printed. **This phase disabled nothing**:
every ``@Disabled`` in the tree is a ``@DisabledOnOs(WINDOWS)`` with a reason,
and there are no unconditional ones.

.. _p05h-units:

Units 1 to 7, and the defect injected into each
================================================

Units 1-5 were signed off by my predecessor; units 6 and 7 by me. **Every unit
was sent back at least once except unit 5.**

.. list-table::
   :header-rows: 1
   :widths: 4 16 10 70

   * - #
     - Unit
     - State
     - The injected defect, and the text it produced

   * - 1
     - Domain tool vocabulary
     - **SIGNED OFF** ``42033ad``
     - Blank-note rejection skipped when evidence is ``UNVERIFIED``. **Survived
       108 tests.** After rework: ``every evidence value must reject a blank
       note ... expected: <[OBSERVED_BY_EXECUTION,
       INFERRED_FROM_ARTEFACT_BYTES, UNVERIFIED]> but was:
       <[OBSERVED_BY_EXECUTION, INFERRED_FROM_ARTEFACT_BYTES]>``

   * - 2
     - Manifest and strict reader
     - **SIGNED OFF** ``eac6d5e``
     - Three. Keeping the translated row: ``pdv is a JAR, and a row marked
       TRANSLATED_ROSETTA_2 would tell a scientist that a Java program runs
       under Rosetta 2 ==> expected: <NATIVE> but was: <TRANSLATED_ROSETTA_2>``

   * - 3
     - Downloader and checksum decision
     - **SIGNED OFF** ``12d871e``
     - Progress on a **resumed** transfer reported from the resume point.
       **Survived 338 tests.** Now ``expected: <1500000> but was: <16229>``.

   * - 4
     - Safe extraction
     - **SIGNED OFF** ``00e6494``
     - ``hasDriveLetter`` returning false: bit, 8 failures. Then each of five
       XXE guards deleted in turn; all five fail.

   * - 5
     - Atomic install, marker, lock
     - **SIGNED OFF** ``0ff3d72``
     - Marker's own ``payloadEntryCount`` trusted instead of counting: bit,
       ``expected: <CONTENT_COUNT_MISMATCH> but was: <INSTALLED>``

   * - 6
     - Loadability and identity probes, ``R-PLAT-03``
     - **SIGNED OFF** ``c599c7e``, two rounds
     - An unreachable binary treated as **offered** rather than refused.
       **Survived 944 tests.** ``R-TOOL-06``'s "a tool that fails loadability
       shall never be offered", switched off for one way of failing. Second,
       unannounced injection (alternatives emptied) **bit**, 3 failures,
       proving the audit was real.

   * - 7
     - Tool adapters, functional capability probe, local binaries
     - **SIGNED OFF** ``9e38f3b``, one round
     - ``Locale.ROOT`` removed from the synthetic PIN. **Survived 205 tests
       including seven that run the real binary.** Proven harmful, not argued:
       under ``de_DE`` the feature columns become ``1,0540``, which Percolator
       refuses, so the probe reports "cannot write XML" -- a false negative on
       the one capability ``R-PERC-02`` requires be established functionally.

   * - 8
     - Tool Manager runtime
     - **CANCELLED, reverted**
     - Stopped by owner instruction; its commit reverted and preserved under the
       tag ``phase05-unit8-cancelled``, unverified. See :ref:`p05h-unsigned`. It
       found a live cancellation defect on its way out
       (:ref:`p05h-cancel-defect`).

.. _p05h-nowhere:

What I know that is written nowhere else
=========================================

Where a useful injection comes from, confirmed over two more units
-------------------------------------------------------------------

My predecessor found that **every injection it chose from inside its own
acceptance conditions bit immediately, and all three that survived came from
outside them.** I inherited that and it held for units 6 and 7 without
exception.

In unit 6 I tried five candidates drawn from the acceptance list -- the
manifest-versus-banner version check, the alternatives filter, the timeout
cancellation, the "unknown C library leaves the C++ refusal standing" case --
and **every one was already graded, several better than the list asked.** Only
when I stopped shopping the list and asked *"what silent behaviour does this
code have that no condition names?"* did I find the hole. In unit 7 the same
question found the locale.

**Both of my surviving injections were the same shape**, and it is worth
naming: a ``throws`` clause nothing exercised, and an ambient JVM default
nothing varied. Neither is behaviour anyone wrote a condition about, because
both look like infrastructure rather than logic.

The defect class this phase paid for three times
--------------------------------------------------

**A rule keyed on the wrong attribute of the right idea.** All three read
correctly at the call site:

* the offered-set rule graded over *what the probe answers* and not over
  *whether it could answer* (unit 6);
* the alternatives exclusion keyed on the *version* when it meant the *row*
  (unit 6 again -- and I found that one by **reading**, not by injecting);
* ``ToolRunOutcome`` joined with ``System.lineSeparator()`` while its test
  computed the expected value the same way, so the choice was unobservable
  (unit 7, found by its own agent).

**The rule that resolves it: the download URL is how this product asks "is this
the same build?"** One version can be two rows -- Comet 2026.02.2 ships two
macOS builds and on Apple silicon **both are offered** -- and one platform
carries several versions, so keying on the platform deletes every alternative
there is. **The counter-example matters as much**: ``StagedToolProbe``'s
identity stage rightly compares *versions*, because "is this the release the
manifest pinned?" is a question about releases; the row is pinned by its
SHA-256 at ``InstallStep.VERIFY_SHA256`` (step 2), four steps before ``PROBE``
(step 6). **Unit 8 inherits ``select``, ``ManifestAlternatives`` and
``ProbeGatedOffers`` together and is where "which row is this?" is asked most.**

Ambient defaults are the other place nothing looks
----------------------------------------------------

Unit 7 shipped two: the PIN's locale and the line separator. The convention for
grading them already exists in ``cometgui-provenance`` (five test classes) and
now in ``cometgui-tools``. Vary the default; do not trust that it is pinned
because the code says ``Locale.ROOT``. **A protection that cannot be observed to
matter is indistinguishable from one that is absent** -- unit 4's five XXE
guards, arriving twice more.

And the damage is wider than it looks: under a Thai-digit locale **every**
numeric column follows the locale, not only the decimals -- the row index, the
label, the scan number and the ``%05d`` accession as well as the ``%.4f``
features.

Read why a build is RED with the same suspicion as why it is green
--------------------------------------------------------------------

Twice in unit 7 my own injection harness produced a red I could have mistaken
for a result: once Checkstyle's 100-character line limit, because my marker
comment was too long; once Spotless, because removing both ``Locale.ROOT``
arguments left the import unused. **Neither red was the defect.** Had I read
either as a result I would have sent an agent back for something it had not
done.

This project has spent months on greens that mean nothing. The mirror case is
rarer and worse in one specific way: **a false red costs another tier's time and
credibility rather than your own**, and suspecting an agent is cheaper than
suspecting yourself, so it is the half that gets skipped.

*The same family:* ``git diff --stat A..B`` over a range that spans other
people's commits will show you their files as though the agent touched them. Use
``git show --stat <commit>`` when the question is "what did this agent do". I
nearly sent unit 7 back for touching ``handoffs/`` that were my own and tier 1's.

Exit codes, in the fourth and fifth shapes
--------------------------------------------

Three agents were caught by ``-Dtest`` expressions selecting nothing. Then I ran
``bash scripts/verify-all-gates.sh > log 2>&1; echo "EXIT=$?"`` in the
background and **the harness reported "completed (exit code 0)" while the suite
had exited 1** -- the wrapper's status, not the command's. Unit 7's agent then
hit the fifth shape: ``nohup ... &`` reported a build as completed which had
actually been killed mid-stage, leaving an orphan whose stale log line later
surfaced as a false ``BUILD FAILED``.

**Use** ``{ cmd; echo "EXIT STATUS: $?"; } > log`` -- a redirect, not a pipe,
because a pipeline reports its last stage. The status is then *inside* the
evidence, which is the only form immune to all five.

A liveness check must exclude the dead as well as the checker
---------------------------------------------------------------

``STATUS.rst`` records that a ``pgrep -f`` check matching its own command line
is both the impatient and the patient form of the same mistake. There is a
second cause that excluding the checker does not fix: this host carried **705
zombie processes, 345 of them named ``java``**, from shells that died in earlier
sessions. Any name-based "is anything running" check matches hundreds of
processes that exited hours ago. Match on a live attribute -- I use
``ps -eo stat,comm | awk '$1 !~ /Z/ && $2=="java"'`` -- and remember that **the
completion notification is the only signal that means finished.**

.. _p05h-figures:

Which figures to trust, one by one
===================================

My predecessor's discipline, continued, because it is what made this handover
cheap for me.

.. list-table::
   :header-rows: 1
   :widths: 32 12 56

   * - Figure
     - Trust
     - Why

   * - ``build.sh`` 11/11 in 1331s, ``tests=3532 failures=0 errors=0 skipped=3``
     - **Yes**
     - Mine, on ``0630d6d``, quiet tree, ``git status`` empty, exit status
       captured inside the log.

   * - ``verify-test-gates.sh`` 37 assertions, 0 failed, 2947s
     - **Yes**
     - Mine, on ``0630d6d``, in full. Control 0 reports the survivor set
       *exactly* the hand-typed entry; control 2 still bites now that
       ``org.cometgui.tools.comet`` holds real classes; control 8 rejects with
       the census's own diagnostic.

   * - ``cometgui-domain`` 100.0/100.0, 49/49 census, 369/370
     - **Yes**
     - Same run, and the single survivor is ``ToolVersion:214:Conditionals
       BoundaryMutator``, unmoved across every commit of units 6 and 7.

   * - ``cometgui-tools`` 98.5/97.9, 18/18 census, 220/222
     - **Yes**
     - Same run. Both non-kills argued in the production source.

   * - ``cometgui-install`` 100.0 line / 99.6 branch, 79/79, 1295/1311
     - **Yes**
     - Same run. None of the non-kills is in ``probe``.

   * - The 54 manifest checksums, and the artefact mirror
     - **Yes**
     - My predecessor re-derived the 54. I independently re-derived **32
       artefact and companion rows** against the shipped manifest on 2026-09-02:
       0 mismatches, 0 missing.

   * - ``verify-all-gates.sh`` -- 11 controls, 0 failed
     - **NO -- re-take it**
     - I re-took it at ``be5dd72`` and it was **RED**: 10 passed, 1 failed,
       1504s. Both faults were repaired by tier 1 at ``90d87fa`` and I verified
       the repair by diff, but **the aggregate suite has not been run green
       end to end since**. That is the single most important number you should
       re-take, and it is ~50 minutes.

   * - Anything about unit 8 / the ``phase05-unit8-cancelled`` tag
     - **NO**
     - Never built, never reviewed, never injected into.

**One delta I could not explain and did not tidy away.** On two of unit 6's
commits the agent's reactor-level mutation count and mine differed by exactly
one -- it reported 1289/1305 and 1291/1307, my runs and the build's own lines
said 1288 and 1290 -- always in the safe direction, while the **package-level**
figure was identical in every run. The likely cause is a mutant on the
``KILLED``/``TIMED_OUT`` boundary, where load decides the side and ``build.sh``
counts only ``KILLED``. **Nobody has demonstrated that.** Do not read a defect
into a one-mutant difference between two runs, and do not read this as an
explanation either.

.. _p05h-remaining:

The six remaining units, and why they are shaped this way
==========================================================

The numbering is not the original decomposition. **Inherit the reasoning, not
just the list** -- my predecessor's one real failure was recording conclusions
without the arguments behind them, and it cost me most of a day rediscovering
them.

**What changed, and why:**

* **Old unit 8 (local binary registration) was absorbed into unit 7.** It is the
  same adapter code with a different source of binary; splitting it bought a
  second agent's context and nothing else. Done, and it worked.
* **Unit 8's number was reused for the ``ToolManager`` runtime.** When I took
  over, ``org.cometgui.domain.tools.ToolManager`` -- the port the UI is allowed
  to see, and the only one it has -- **had no implementation and no unit
  producing one**. The original plan folded it into the word *"wiring"* in the
  UI unit. It is not wiring: it is manifest ``select``, the host-requirement
  filter, ``ToolCache.verify``, ``InstallPipeline`` on a background thread
  behind an ``InstallHandle``, failure mapped to ``ToolInstallState`` and
  ``LoaderDiagnostic``, and ``registerLocalBinary``. **It is what gate items 1,
  2, 5, 7 and 8 actually run through**, and burying it inside the least testable
  unit in the phase was the plan's one structural error.
* **It runs before the UI**, which is the earlier plan's own intent applied one
  unit sooner: exercising the port from JUnit reveals a wrong port shape without
  a UI existing at all. It has already earned that -- see the ``ToolOffer`` gap
  below, which was visible from reading the port and was confirmed by the
  cancelled unit.
* **Gate item 1's wording is literal** -- *"driven through the Tool Manager UI,
  not from a test helper"* -- so unit 10 still has to drive the real UI. Unit 8
  does not discharge it.
* **Unit 13 is the macOS attempt**, approved by tier 1 as a **late** unit so it
  cannot displace units other gate items depend on. Its number is higher than
  its position on purpose: renumbering 11 and 12 would invalidate references
  already committed in the work log, and a silently renumbered unit is the drift
  this project keeps paying for.

**Order: 8, 9, 10, 13, 11, 12. Serially.** No positive argument exists that any
two cannot collide -- they share ``cometgui-domain``, the Maven working tree,
``_build/m2repo``, the docs gate and the git index -- so none is offered.

.. list-table::
   :header-rows: 1
   :widths: 5 95

   * - #
     - Unit

   * - 8
     - **The Tool Manager runtime behind the domain port**, in
       ``cometgui-install``, wired in ``ApplicationServices``. Exercised end to
       end from JUnit with **no UI**. Note ``cometgui-tools`` cannot see
       ``cometgui-install``, so ``CapabilityProber`` and ``JavaArtefactIdentity``
       are adapted where both are visible, which is ``cometgui-app``; unit 7
       proved that route in ``StagedJavaToolProbeTest``. **Fix the ``ToolOffer``
       download-size gap here** (tier 1's direction), and **fix or route the
       cancellation defect** in :ref:`p05h-cancel-defect`.

   * - 9
     - **Tool Manager UI section and wiring.** The hardest constraint in the
       phase: ``LayeringRulesTest`` forbids ``org.cometgui.ui..`` from touching
       ``org.cometgui.install..``, ``org.cometgui.tools..``, ``java.net``,
       ``java.security``, ``java.util.zip`` and ``java.util.jar``, and
       ``cometgui-ui``'s POM declares neither installer module. Everything the
       UI renders must be in ``org.cometgui.domain.tools``. Render
       ``ToolVersion.text()``, never the cache directory name. ``SectionId.TOOL_MANAGER``
       already exists; content arrives through ``SectionPane.addContent(Node)``.
       **``SectionArrivals.noteFor`` throws for a section with no note**, so
       filling the section means changing ``ShellView`` too, and
       ``ViewModelIndependenceTest`` pins the exact file list of
       ``org.cometgui.ui.viewmodel``.

   * - 10
     - **The end-to-end install driven through the UI** (gate items 1 and 2),
       plus the deliberate PDV cancel-and-restart. **Cancellation deletes the
       partial**, so a cancelled 99 MB download restarts from zero; resume
       survives a *failure*, not a cancellation. Headless JavaFX under Monocle
       via ``cometgui-ui/src/test/.../testing/FxToolkit``.

   * - 13
     - **The macOS Gatekeeper attempt** (gate item 9). ``release.yml`` already
       declares ``macos-latest``, so runners are reachable, and
       ``windows-percolator-verify.sh`` is the proven template. **The negative
       control is mandatory**: a ``curl`` download sets no
       ``com.apple.quarantine``, so the job must set the attribute itself and
       show that leaving it set produces a refusal. **If the control does not
       bite, the result is "this check cannot go red" and must be reported as
       such, never as a pass.** Tier 1 pushes and dispatches.

   * - 11
     - **Documentation**: ``docs/developer/tool_registry.rst``,
       ``docs/tool_manager.rst``, and **only the artefact table** in
       ``docs/platform_support.rst``. Checked in the stubs themselves:
       ``docs/developer/tool_adapters.rst``'s per-tool sections belong to phases
       08, 09, 11 and 12, and ``docs/developer/version_capabilities.rst`` to
       Phase 09. **There is no ``docs/user/`` directory.** Generate the platform
       matrix from ``manifests/tools.json`` so it cannot diverge.

   * - 12
     - **``scripts/verify-install-gates.sh``**, assembled from the injections in
       the work log rather than invented, in ``verify-provenance-gates.sh``'s
       shape. **Tier 1 has approved the additive registration** in
       ``verify-all-gates.sh``: register the control, never lower a floor,
       raising your own is expected. Must be last.

.. _p05h-open:

Three findings that are not mine to close
==========================================

#. **``ToolOffer`` carries no download size.** Its own Javadoc says *"everything
   a scientist is shown about a tool is expressible here, or it is not shown"* --
   and as the port stands **the Tool Manager cannot tell a user that PDV is a
   103 407 417-byte download**, on the one transfer the phase document singles
   out for cancellation testing. Tier 1 has ruled this is fixed in unit 8, not
   deferred. ``ArtefactRecord.sizeBytes`` already exists.
#. **The URL-is-the-key rule, and unit 8 inherits all three classes it governs.**
   ``select``, ``ManifestAlternatives`` and ``ProbeGatedOffers`` together, and
   unit 8 is where *"which row is this?"* is asked most often. See
   :ref:`p05h-nowhere` for the rule and its counter-example.
#. **Six test classes mutate global JVM state** through ``Locale.setDefault`` --
   five in ``cometgui-provenance``, one in ``cometgui-tools``. Safe today: no
   parallel configuration in any POM, no ``junit-platform.properties``, and
   JUnit 5 defaults to sequential. **The moment anyone enables parallel
   execution they all need ``@ResourceLock(Resources.LOCALE)`` or they go
   racy.** Escalated so it lands where that decision is made rather than being
   discovered as a flake.

And one smaller one: **``CLAUDE.md`` line 9 still reads "the requirements
(revision 10)"** while ``specification.rst`` is at **revision 11** (``a4aeb80``).
That is tier 1's file and the coding harness's entry point.

.. _p05h-gates:

The nine exit gate items, as they stand
========================================

.. list-table::
   :header-rows: 1
   :widths: 5 14 81

   * - #
     - State
     - Evidence, or what is missing

   * - 1
     - **NOT MET**
     - Needs units 8, 9, 10. The download half works through product code, and
       **PDV and the converter are no longer blocked** -- unit 7 supplied their
       identities, so all four tools can be probed.

   * - 2
     - **PARTIAL**
     - Proved at the installer over all four artefact kinds; the UI half is
       unit 10.

   * - 3
     - **MET**
     - 12 attacks x 4 multi-entry kinds; a **real** upstream artefact containing
       ``../my_build/percolator-noxml/src/percolator`` installs safely and is
       rejected whole; bomb ceilings bite on ratio, size and entry count
       separately; a bytecode scan proves only ``ExtractionGuard`` can place a
       file.

   * - 4
     - **MET**
     - Interruption in a real second JVM via ``Runtime.halt`` after each of the
       eight steps; two JVMs serialise with one observed to wait, and a control
       shows overlap without the lock. **But see**
       :ref:`p05h-cancel-defect` -- cancellation *within* a step was never
       graded.

   * - 5
     - **MET at the probe; the offered-set half needs unit 8**
     - Unit 6 executes the real 3.09 payload and produces both ``R-PLAT-03``
       layers with the whole message pinned; a build that fails loadability is
       absent from the offered set, asserted on the set.

   * - 6
     - **MET**
     - Unit 7: a Comet install without the three Thermo DLLs does not advertise
       ``THERMO_RAW_WINDOWS`` and one with them does, graded both ways.

   * - 7
     - **MET at the adapter; needs unit 8 to reach the port**
     - A 3.04 binary is rejected naming both versions; the real 3.07.1
       registers with the manifest's own digests and its probed capabilities;
       unreadable, not-Percolator and too-old are three distinct refusals.

   * - 8
     - **PARTIAL**
     - Manifest and selection done and tested against the shipped file; the UI
       half is unit 9.

   * - 9
     - **NOT MET, and it is why the phase is PARTIAL**
     - No macOS binary has ever been executed anywhere in this project. Unit 13
       is the approved attempt; until it runs with a **biting negative
       control**, this stays unmet and must not be reported otherwise.

**``R-SEC-06`` is vacuously satisfied and is not delivered work.** It governs
*project-built* tool binaries under ``D-002``, and option C means the project
builds none. Say that plainly rather than letting it read as work done.

.. _p05h-decisions:

Decisions you should not re-litigate
=====================================

* **The UI may not see the installer.** Structural, enforced by ArchUnit.
* **One JSON reader, one hasher, one process launcher, one redactor, one
  artefact-mirror helper.** Never write a second of any.
* **For a ``ZIP`` the manifest names the member and the archive's own path never
  places a file** -- forced by the real traversing artefact -- **and the
  traversal guard is still exercised against that same artefact**, so the design
  did not become the reason the guard is untested.
* **Windows takes the XSD pair from the Linux ``.deb``** (tier 1, 2026-09-02),
  because the two files are byte-identical across platforms and versions.
* **``AtomicMoveNotSupportedException`` is re-thrown, never handled.**
* **``NSIS_PAYLOAD`` is not implemented and must not be** (``D-002`` option C).
* **``scripts/ci/nightly-manifest-verify.sh`` stays a stub that exits non-zero.**
  It is Phase 15's; making it exit 0 would be a gate weakening.
* **``cometgui-tools``'s mutation switch is ON** and switching it back off is a
  rejection.

.. _p05h-first:

The first thing to do
======================

#. Decide about the ``phase05-unit8-cancelled`` tag (:ref:`p05h-unsigned`). My
   recommendation is to read it, not build on it.
#. **Reproduce the cancellation defect** in :ref:`p05h-cancel-defect` before
   planning unit 8, because it changes what unit 8 must do and it is unit 10's
   gate item otherwise.
#. ``bash scripts/build.sh`` on a quiet tree, then **``bash
   scripts/verify-all-gates.sh``** -- the one figure in this document that
   describes a tree nobody has confirmed green end to end.
#. Then read the work log. This document is what I concluded; that one is what
   happened.
