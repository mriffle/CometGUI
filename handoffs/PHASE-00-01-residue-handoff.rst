=====================================================================
PHASE-00/01 residue handoff -- the branch the owner has to push
=====================================================================

:Scope: PHASE-00 exit gate item 8 (first branch) and PHASE-01 exit gate item 6
:Phase orchestrator: residue orchestrator subagent (session 02)
:Date: 2026-08-30
:Work log: ``handoffs/PHASE-00-01-residue-worklog.rst``
:Branch: ``windows-percolator-verification`` (local only -- **not pushed**)

.. contents:: Contents
   :depth: 2
   :local:

The one-paragraph version
=========================

Two gate items were held open by a missing git remote. The remote exists now,
so both are ordinary work again -- but *this session cannot close either one*,
because closing them requires GitHub to execute something, and this session has
no credential that can push. What it produced instead is a branch that makes
closure a matter of the owner running four commands: a Windows verification
job that executes the seven-step checklist in
``docs/feasibility/windows-artefact.rst`` against ``percolator-v3-07.exe`` on a
``windows-latest`` runner, carried by a pull request whose existence is itself
what PHASE-01 gate item 6 has been waiting for.

**Nothing in this branch claims the Windows binary has been executed.** It has
not. The manifest wording rules in ``windows-artefact.rst`` are untouched:
``xml_capability`` stays ``unverified-on-windows``, the basis keeps the
sentence "The binary was not executed on Windows.", and no document says
*verified*, *confirmed*, *proven* or *tested* of the Windows binary.

What could not be done here, and how that was checked
=====================================================

Not asserted from the brief -- checked:

.. list-table::
   :header-rows: 1
   :widths: 38 62

   * - Check
     - Result

   * - ``command -v gh``
     - not installed

   * - ``GH_TOKEN`` / ``GITHUB_TOKEN``
     - both unset

   * - ``git config --get credential.helper``
     - none

   * - ``~/.ssh``
     - does not exist

   * - ``GIT_TERMINAL_PROMPT=0 git push --dry-run origin ...``
     - ``fatal: could not read Username for 'https://github.com': terminal
       prompts disabled``

The remote itself was queried anonymously and is real:
``mriffle/CometGUI``, public, default branch ``main`` at ``9115b1c``.
``/actions/workflows`` lists ``nightly``, ``pull-request`` and ``release``, all
``active`` -- so **GitHub Actions is already enabled on the repository** -- and
``/actions/runs`` reports ``total_count = 0``. GitHub has therefore still never
executed anything in this project, which is exactly what PHASE-01 gate item 6
says, confirmed from the live remote rather than from the local repository.

The two escalations the brief asked about
=========================================

.. _residue-e1:

E1 -- "``-X`` present" does not discriminate. Agreed, and it is worse than that
-------------------------------------------------------------------------------

Gate item 8's first branch reads: the Windows artefact "is confirmed on a
Windows runner -- payload obtained without admin rights, ``-X`` present".
``windows-artefact.rst``'s escalation E1 says "``-X`` present" is satisfied by
the ``noxml`` build too, so it discriminates nothing. **That is right**, and
Phase 00's own executed Linux control is the proof: both builds advertise
``-X``/``--xmloutput`` in ``--help``, and both actually wrote a Percolator XML
file with 200 ``<psm>`` elements -- 143 729 bytes from the XML build, 143 733
from the ``noxml`` one, differing only in ``<command_line>``. This
orchestrator re-ran that control today; the numbers reproduce exactly.

Two different claims are being run together in that wording:

#. **"This binary can write the Percolator XML the Limelight converter
   consumes."** True of *both* builds. ``-X`` proves it, and it is the property
   the product actually needs.
#. **"This binary is an ``XML_SUPPORT=ON`` build."** What ``XML_SUPPORT`` gates
   is the pin-XML *input* path. The discriminating test is ``--xml-in``, which
   the ``noxml`` build refuses by name (``ERROR: Compiler flag XML_SUPPORT was
   off``), or the Xerces linkage.

"``-X`` present" tests neither: it is satisfied by an artefact that fails claim
2, and it is weaker than an actual run for claim 1.

**And the wording is stale in a second way that E1 could not have known.**
E1 was written on 2026-08-29. Later that day the owner took ``D-002`` **option
C**: Percolator's binary now comes from the portable ``noxml`` ZIP on every
tier-1 platform, and the NSIS payload extractor the product was going to build
is not built (``phases/index.rst``, "Phase 00's residue"). So gate item 8's
first branch now asks for confirmation of an artefact **the product does not
ship**. Closing it on its own terms is still worth doing -- it is the last
piece of Phase 00's evidence and the checklist is written -- but it is no
longer the most useful thing a Windows runner could do.

*This is a gate wording problem and gates are not a phase orchestrator's to
change.* Recommendation for tier 1, to weigh rather than adopt: amend gate item
8's first branch to name the artefact and the test, along the lines of --
*executed on a Windows runner: the payload is obtained without administrative
rights; the binary starts; ``-X`` on a real PIN writes a Percolator XML file
whose ``<psm>`` count matches the Linux control; and, where an
``XML_SUPPORT=ON`` claim is made, ``--xml-in`` does not print "Compiler flag
XML_SUPPORT was off"* -- and to say which artefact, now that ``D-002`` option C
has changed the answer.

Rather than wait for that, the job on this branch does both: it executes the
seven-step checklist against ``percolator-v3-07.exe`` exactly as written, so
the gate can be closed on its own terms, **and** it runs the same tests against
the ``noxml`` portable ZIP's ``percolator.exe`` -- the binary the product will
actually ship -- as a clearly separated section that is reported but does not
redefine the gate.

.. _residue-e2:

E2 -- the executed evidence bearing on ``D-002``
------------------------------------------------

E2 recommended keeping ``D-002`` as decided and raising a narrow question
later. **It has been overtaken: the owner went further than E2 recommended**
and took option C on 2026-08-29. As an escalation E2 is closed.

What E2 listed as *not established* is a different matter, and most of it is
still not established:

* *Whether a ``noxml`` build's ``-X`` output validates against
  ``percolator_out.xsd``.* Partly answered, and the answer makes the question
  moot: ``DECISIONS.rst`` records that ``percolator_out.xsd`` rejects
  Percolator's own output regardless of build -- the schema fixes
  ``majorVersion`` at ``2`` and the binary writes ``3``. The schema therefore
  cannot be the discriminator for either build.
* *Whether the Limelight converter accepts it.* **Still open, and now cheap.**
  E2 said this needed a JVM; there has been one under ``tools/`` since Phase
  01. Nobody has run it. It is a Phase 05/09 input and it is the question that
  actually matters, because ``D-002`` option C rests on the answer being yes.
  Recommending it to tier 1 as a small, self-contained piece of work.
* *Whether the Windows and macOS ``noxml`` builds behave as the Linux one
  does.* Windows is addressed by section 8 of the job on this branch, once a
  runner executes it. **macOS remains entirely untested and no work here
  changes that.**
* *Whether 3.09 behaves differently again.* Unchanged.

Traps and findings worth carrying forward
=========================================

.. list-table::
   :header-rows: 1
   :widths: 34 66

   * - Finding
     - Why it matters

   * - **The extractor was not Windows-safe**
     - ``docs/feasibility/windows-artefact.rst`` said
       ``scripts/feasibility/extract_nsis.py`` "runs on Windows unchanged".
       The audit found a Windows-only path traversal past its own containment
       check: Win32 strips trailing dots and spaces from every path component,
       so a member named ``$INSTDIR\.. \.. \evil.txt`` passed the ``".."``
       filter and would have been written outside the output directory -- and
       ``percolator.exe`` with a trailing space would have silently replaced
       ``percolator.exe``, the file whose SHA-256 the checklist pins. Fixed
       and proved falsifiable. See the U2 sign-off in the work log.

   * - **A checked-out shell script is CRLF on a Windows runner**
     - The runner image installs Git for Windows with ``/VERYSILENT`` and no
       ``CRLFOption`` override (``images/windows/scripts/build/Install-Git.ps1``
       sets only ``safe.directory``), so the installer default --
       ``core.autocrlf=true`` -- applies. Every ``scripts/ci/*.sh`` in this
       project starts with ``set -Eeuo pipefail``, and a CRLF copy of that line
       fails immediately: ``set: pipefail: invalid option name``, exit 2.
       Demonstrated here on a deliberately CRLF file. A ``.gitattributes``
       pinning ``eol=lf`` removes the dependency on that setting either way.
       **The existing nightly Windows job has the same latent failure**, and
       nobody would have found out until it first ran.

   * - **A SHA-256 check can hash the wrong thing**
     - The PIN generator's ``--expect-sha256`` originally hashed the string it
       meant to write rather than the file it wrote, so on Windows it would
       have reported the intended PIN while a CRLF-translated one sat on disk.
       Caught at sign-off by damaging the writer and watching the check pass.

   * - **A comment can turn a real check into a "stub"**
     - ``scripts/ci/run-pipeline-locally.sh`` decides whether a step is an
       unimplemented stub -- one that MUST exit 70 -- by grepping the script
       file for the literal string ``stub-lib.sh``. The new verification
       wrapper's header comment originally *named* that file in order to say
       "this is not a stub", which would have made a real check be classified
       as a stub by the very mechanism it was disclaiming. The literal is
       absent from both new files and the comment explains the circumlocution.
       Anything written later that mentions that filename in prose hits the
       same trap.

What is on the branch
=====================

Eleven files. The branch is ``windows-percolator-verification``, based on
``main`` at ``82609f0``, and it was built in a separate ``git worktree`` so
that a second phase orchestrator working on ``main`` in ``/workspace`` was
never disturbed: ``/workspace``'s working tree was clean throughout and no
commit of this work landed on ``main`` except the two records in ``handoffs/``.

.. list-table::
   :header-rows: 1
   :widths: 42 58

   * - File
     - What it is

   * - ``.github/workflows/windows-percolator.yml``
     - New. ``pull_request`` to ``main`` plus ``workflow_dispatch``, on
       ``windows-latest``, three steps: checkout, the checklist, and the
       transcript upload. Both actions pinned to full commit SHAs. The upload
       carries ``if: always()`` so that a *failing* run is not the one run
       whose evidence never leaves the runner.

   * - ``scripts/ci/windows-percolator-verify.sh``
     - New. A thin wrapper: it finds a Python by RUNNING each candidate
       (``python3``, ``python``, ``py -3``) rather than trusting
       ``command -v``, because a Microsoft Store alias answers ``command -v``
       and then does nothing.

   * - ``scripts/ci/windows_percolator_verify.py``
     - New, and the substance: the seven checklist steps, a section 8, the
       transcript, the verdict block and a 34-case self-test.

   * - ``scripts/feasibility/make_synthetic_pin.py``
     - New. The 400-PSM PIN generator, factored out of a heredoc inside
       ``windows-artefact.sh`` so that both platforms call one generator.
       Byte-identical to what the heredoc produced.

   * - ``scripts/feasibility/windows-artefact.sh``
     - Call site only: it now calls that generator and asserts its SHA-256.

   * - ``scripts/feasibility/extract_nsis.py``
     - Made genuinely Windows-safe, with a ``--self-test`` that drives its
       path safety net under both POSIX and Win32 semantics.

   * - ``scripts/ci/check-workflows.py``
     - Extended so that a fourth workflow file cannot escape the gate: it now
       DISCOVERS every file in ``.github/workflows/`` instead of iterating
       three names, and the relaxations the new file needs are narrow
       allowlists with their own self-test cases.

   * - ``scripts/ci/run-pipeline-locally.sh``
     - Covers the fourth workflow, and its summary no longer says a remote
       does not exist.

   * - ``.gitattributes``
     - New. ``*.sh`` and ``*.py`` pinned to LF. See the traps table.

   * - ``docs/feasibility/windows-ci-verification.rst``
     - New. What the harness is, what a pass would and would not establish,
       and its status: a harness, not a result.

   * - ``docs/feasibility/windows-artefact.rst``
     - Three corrections, each a fact that changed: the extractor's
       portability claim (twice) and the cost table's "blocked by ``D-008``"
       cell. **Its warning, its "Not established" list, its gate item 8 status
       and its manifest wording are untouched.**

What cannot be known until a runner executes it
===============================================

Everything below is unknown today and this session cannot make it known. It is
listed so that nobody reads the branch as evidence.

**About the Windows binary** -- the whole point of the job:

* whether ``percolator.exe`` starts at all on Windows Server;
* its real ``--help`` text and exit code (the harness pins the expectation to
  the Linux value, 0, and reports a difference rather than hiding it);
* whether ``-X`` writes a Percolator XML file there, and with how many
  ``<psm>`` elements;
* whether ``--xml-in`` prints ``Compiler flag XML_SUPPORT was off`` -- the
  discriminating test, and the one that decides whether the project's
  inference about this artefact holds;
* whether the MSVC runtime DLLs shipped in the payload are found by the
  loader from the directory the harness extracts them into. The symptom of a
  failure would be exit ``0xC0000135`` and an INCONCLUSIVE verdict, not a
  false pass;
* whether the portable ``noxml`` ZIP's binary -- the one the product ships
  after ``D-002`` option C -- runs on the runner at all.

**About the harness itself**, which has only ever run on Linux:

* whether Git Bash finds a Python (the wrapper *runs* each of ``python3``,
  ``python`` and ``py -3`` rather than trusting ``command -v``, because a
  Microsoft Store alias answers ``command -v`` and then does nothing);
* whether MSYS leaves a relative script path and the flags unrewritten. The
  wrapper changes directory to the project root and passes a relative path precisely so
  there is no absolute POSIX path for MSYS to rewrite; that reasoning is
  argued, not observed;
* whether the ``.gitattributes`` does what it is there for -- a checkout with
  LF endings under ``core.autocrlf=true``;
* Windows exit-code formatting against a real ``0xC0000005``.

**About the pull-request pipeline**, which has also never run anywhere but
here: whether a hosted ``ubuntu-latest`` runner can complete it inside its
90-minute timeout, with its own JDK, Maven and font stack downloaded from
scratch and PIT mutation testing on four cores. Every step passes on this
machine; nothing more than that is known.

**Never answerable by this job, whatever it returns:** behaviour for a
standard, non-administrator user (a hosted runner is an administrator);
consumer Windows 10 and 11; Windows on ARM; a clean machine without Visual
Studio's redistributables; and anything at all about macOS.

What the owner has to do
========================

Nothing below can be done from this session: there is no credential here. All
of it is plain shell, to be typed on the machine that holds ``/workspace``,
where the branch lives.

**Push ``main`` first, then the branch.** The order matters. The branch is
based on ``main`` as it stood at ``82609f0``. If the branch is pushed while
``origin/main`` is still at ``9115b1c``, GitHub computes the pull request
against that older commit and the diff will carry a handful of unrelated
commits as well. Push ``main`` first and the pull request contains only this
work.

.. code-block:: text

    cd /workspace
    git status
    git push origin main
    git push origin windows-percolator-verification

Then open the pull request in the browser:

.. code-block:: text

    https://github.com/mriffle/CometGUI/compare/main...windows-percolator-verification?expand=1

and press "Create pull request". Opening it starts both pipelines: the
existing ``pull-request`` workflow (which is what PHASE-01 gate item 6 has been
waiting for) and the new ``windows-percolator`` job.

Watch them at:

.. code-block:: text

    https://github.com/mriffle/CometGUI/actions

**The trap that will cost an hour if it is not expected.** A GitHub Personal
Access Token cannot create or update anything under ``.github/workflows/``
unless it carries the **workflow** scope. This branch's whole point is a file
in that directory, so the second push is exactly where it bites. The failure
looks like this, and it names no account, no token and no fix:

.. code-block:: text

    ! [remote rejected] windows-percolator-verification -> windows-percolator-verification
      (refusing to allow a Personal Access Token to create or update workflow
      `.github/workflows/windows-percolator.yml` without `workflow` scope)

The remedy is on the token, not in the repository: a **classic** PAT needs the
``workflow`` checkbox; a **fine-grained** PAT needs *Repository permissions ->
Workflows: Read and write*. Then push again -- nothing here needs changing.
The first push, ``git push origin main``, is not affected: none of the
unpushed ``main`` commits touches ``.github/``, which was checked.

**GitHub Actions appears to be enabled already.** The Actions API lists all
three existing workflows as ``active``, which a repository with Actions
disabled does not do. If a run does not start anyway, it is *Settings ->
Actions -> General -> Allow all actions and reusable workflows*.

**Reading the result.** The Windows job uploads its transcript as an artifact
called by the workflow's ``name``, and also prints the whole of it into the job
log, so the evidence survives even if the upload does not happen. The verdict
block at the end is the thing to read. It says one of:

.. list-table::
   :header-rows: 1
   :widths: 22 12 66

   * - Verdict
     - Exit
     - What it means

   * - ``PASS``
     - 0
     - Every checklist assertion held on a Windows runner, each naming the
       value it observed. This is what closes PHASE-00 gate item 8's first
       branch.

   * - ``NEGATIVE``
     - 1
     - The binary ran and the evidence contradicts something this project
       currently infers. **This is a real finding, not a bug in the job.** It
       goes to tier 1 and probably to ``D-002``.

   * - ``INCONCLUSIVE``
     - 2
     - The binary did not run far enough for the test to mean anything.
       Nothing is established either way.

   * - ``HARNESS FAILURE``
     - 3
     - A download, a checksum, the extraction or Python failed. Nothing was
       learned about the binary.

A red Windows job is therefore not automatically a defect to fix: read which
of the four it is first.

What was verified here, and with what output
============================================

Every line below was run by the phase orchestrator itself, on the branch, not
read from a report.

.. list-table::
   :header-rows: 1
   :widths: 46 54

   * - Command
     - What it printed

   * - ``bash scripts/build.sh``
     - ``11/11 stages OK in 89 seconds. BUILD OK`` -- the baseline, intact.

   * - ``bash scripts/ci/check-workflows.sh``
     - ``PASSED``, with ``4 workflow file(s) discovered`` and the new file's
       required content named.

   * - ``bash scripts/ci/check-workflows.sh --self-test``
     - ``self-test OK -- 19 damaged copies rejected, the undamaged one
       accepted`` (nine before this work).

   * - Ten injected defects of the orchestrator's own, in a sandbox copy
     - All ten rejected, each with a message naming the cause, including an
       action outside a workflow's allowlist and the ``if: always()`` removed
       from the transcript upload.

   * - ``bash scripts/ci/run-pipeline-locally.sh``
     - ``45 step(s) across 4 workflow(s); 37 executed on this machine; 0
       unexpected``, exit 0. The executed count did not move: every step of
       the new workflow is ``NOT RUN`` here, honestly recorded.

   * - ``bash scripts/ci/windows-percolator-verify.sh``
     - ``REFUSED -- this is not a Windows host``, exit 4.

   * - ``bash scripts/ci/windows-percolator-verify.sh --self-test``
     - ``34 case(s), 0 failed. Every damaged case was rejected and every
       control accepted.``

   * - ``bash scripts/ci/windows-percolator-verify.sh --check-only``, from a
       deleted work directory
     - exit 0, every pinned identity observed rather than assumed, a
       20 954-byte transcript, and a verdict block saying ``CHECK-ONLY ... NO
       WINDOWS BINARY WAS EXECUTED. This is not a pass.``

   * - The whole Windows path forced on Linux (``os.name = "nt"`` after
       import)
     - ``verdict: INCONCLUSIVE``, ``exit code: 2``, every launch reported as
       ``could not be launched at all``. It cannot go green while doing
       nothing.

   * - ``python3 scripts/feasibility/extract_nsis.py --self-test``
     - ``27 checks``, exit 0; reverting the single changed line makes ``10 of
       27`` fail, including a Windows-only escape from the output directory.

   * - A fresh extraction of ``percolator-v3-07.exe``, diffed against the tree
       Phase 00 produced with the original extractor
     - No differences at all; 22 payload files; ``044f3957...``,
       ``fc3c95e0...``, ``c4c664ea...`` unchanged.

   * - ``bash scripts/feasibility/windows-artefact.sh`` end to end
     - Reproduces the recorded Linux control exactly: 143 729 and 143 733
       bytes, 200 ``<psm>`` elements each, the ``XML_SUPPORT was off``
       diagnostic on the ``noxml`` build only.

   * - ``bash scripts/ci/docs-build.sh``
     - ``PASSED``, both strict builds, with the new page among them.

Where the branch is
===================

The work was done in a **separate git worktree** so that the phase
orchestrator running Phase 02 on ``main`` in ``/workspace`` was never
disturbed. The branch itself lives in ``/workspace``'s repository and is
visible there::

    git branch -v
    git worktree list

If the scratch worktree has been cleaned up, ``git worktree prune`` tidies the
metadata and the branch is unaffected. To re-create a place to inspect it::

    git worktree add /some/path windows-percolator-verification

Residue for tier 1
==================

Five things this orchestrator could not do, none of which blocks the push:

#. **Gate item 8's wording.** See :ref:`residue-e1`. Its test does not
   discriminate, and after ``D-002`` option C it names an artefact the product
   no longer ships. A gate is tier 1's to amend.
#. **The open half of E2.** Whether the Limelight converter accepts a
   ``noxml`` build's ``-X`` output. ``D-002`` option C rests on the answer
   being yes; a JVM has existed under ``tools/`` since Phase 01; nobody has
   run it. Small, self-contained, and a Phase 05/09 input.
#. **``phases/index.rst``** says PHASE-01 proved "42 steps, 3 workflows".
   There are 45 across 4 now.
#. **``STATUS.rst`` and ``DECISIONS.rst``** each speak of "the three workflow
   files".
#. **``docs/developer/testing.rst``** repeats "Nine damaged copies of
   ``.github/``", which is nineteen now. That file belongs to the Phase 02
   orchestrator's paths and was deliberately not touched.
#. **``CONTRIBUTING.rst``** still refers in places to "the open publication
   half of ``D-008``", which the owner closed on 2026-08-30.

None of them is a phase orchestrator's file to edit.

