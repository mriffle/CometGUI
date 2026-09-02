.. _windows-ci-verification:

===============================================================
The CI harness that will run the Windows checklist
===============================================================

:Phase: PHASE-00 residue -- gate item 8, first branch; and PHASE-01 gate item 6
:Work units: U3 (the driver), U4 (the workflow), U5 (this page)
:Date: 2026-08-30
:Harness: ``.github/workflows/windows-percolator.yml`` calls
   ``scripts/ci/windows-percolator-verify.sh``, which execs
   ``scripts/ci/windows_percolator_verify.py``
:Status: a harness, not a result -- see :ref:`windows-ci-status`

.. contents:: Contents
   :depth: 2
   :local:

.. warning::

   **No Windows binary in this project has been executed, and this page does
   not change that.** It describes a harness which has itself never been
   started: GitHub has run no workflow in this repository at all. Nothing here
   is a statement about how ``percolator.exe`` behaves on Windows. Every such
   statement in this repository is still the static evidence in
   :ref:`windows-artefact`, under that document's opening warning, and
   ``xml_capability`` in the tool manifest stays ``unverified-on-windows``
   until a run says otherwise -- see :ref:`windows-artefact-manifest`.

What this is and why it exists
==============================

:ref:`windows-artefact` establishes what can be read out of the bytes of
``percolator-v3-07.exe`` on a Linux host, and is explicit that this stops well
short of the question the project needs answered. Its recommended way out is a
seven-step checklist -- "Recommended: the checklist for a Windows machine" --
addressed to a person with fifteen minutes and a Windows machine, ending:
*"Return the transcript. It replaces this document's central caveat with a
fact, either way."*

Nobody has run it. This harness is that checklist automated, so that a runner
executes it on every pull request instead of a person executing it once. The
checklist itself is not restated here; it is the source of record and lives in
that document. What follows is what the automation does with it, where the
evidence lands, and -- at more length than anything else on this page -- why
its central step is arranged so that a binary which never started cannot be
mistaken for a binary which passed.

The harness has three parts and one output:

.. list-table::
   :header-rows: 1
   :widths: 42 58

   * - File
     - What it is
   * - ``.github/workflows/windows-percolator.yml``
     - The only thing in the repository that can put the checklist on a
       Windows machine. Three steps, no matrix.
   * - ``scripts/ci/windows-percolator-verify.sh``
     - A thin wrapper. It finds a Python interpreter and execs the driver;
       there is no other logic in it.
   * - ``scripts/ci/windows_percolator_verify.py``
     - The whole of the logic: the seven steps, a section 8, the transcript,
       the verdict block and a self-test.
   * - ``_build/windows-verify/transcript.txt``
     - The transcript. Also printed to stdout, and uploaded as an artifact.

What runs, and where
====================

The workflow
------------

.. list-table::
   :header-rows: 1
   :widths: 26 74

   * - Property
     - Value
   * - Name
     - ``windows-percolator``
   * - Triggers
     - ``pull_request`` against ``main``, and ``workflow_dispatch``
   * - Runner
     - ``windows-latest``; no matrix, because the one platform this project
       has never been able to observe is the whole point
   * - Permissions
     - ``contents: read``
   * - Shell
     - ``bash`` by default, which on that image is Git Bash
   * - Job timeout
     - 30 minutes; the driver additionally imposes its own per-process
       timeouts, so a hung binary is recorded as a timeout rather than left
       for the runner to kill

``pull_request`` is the trigger that matters. Opening the branch that carries
the workflow as a pull request against ``main`` is what runs it the first time,
and the same pull request exercises ``pull-request.yml``, which has also never
run. ``workflow_dispatch`` is declared as well, but GitHub offers the "Run
workflow" button only for a workflow file already on the default branch, so
until this merges the pull request is the only way to start it.

The three steps are:

#. **Check out the repository**, with ``actions/checkout`` pinned to
   ``fbc6f3992d24b796d5a048ff273f7fcc4a7b6c09`` -- the commit the ``v5`` tag
   resolved to on 2026-08-29, and the same commit the other three workflows
   pin.
#. **Run the checklist**: ``bash scripts/ci/windows-percolator-verify.sh``.
   One ``run:`` line, one script, which a person can run on their own machine.
   There is no ``setup-python`` step: this project's CI contract forbids setup
   actions, and the ``windows-latest`` image ships an interpreter.
#. **Upload the transcript**, with ``actions/upload-artifact`` pinned to
   ``043fb46d1a93c77aae656e7c1c64a875d1fc6a0a`` -- ``v7.0.1`` as it resolved on
   2026-08-30. Artifact name ``windows-percolator-transcript``, path
   ``_build/windows-verify/``, ``if-no-files-found: error``.

Both actions are pinned to a full 40-character commit SHA because a tag is
mutable and an action runs arbitrary code inside the job.
``scripts/ci/check-workflows.py`` enforces the pinning, and permits
``actions/upload-artifact`` in this workflow and in no other.

The upload carries ``if: always()``, the only ``if:`` in any of this
repository's workflows. Without it, a failing run would be the one whose
transcript never left the runner -- which is precisely the
evidence a negative result needs. The driver also prints the transcript to
stdout, so the job log carries it whether the upload step runs or not.

The wrapper, and why the logic is not in it
-------------------------------------------

The entry point has to be a single ``bash scripts/...`` invocation, because
that is what this project's CI contract requires and what
``check-workflows.py`` enforces. But ``shell: bash`` on a ``windows-latest``
runner is Git Bash, an MSYS environment that rewrites arguments looking like
POSIX paths, with its own quoting rules and its own idea of what a path is. No
logic may depend on Git Bash behaving like bash, so the wrapper does exactly
two things: it ``cd``\ s to the project root and hands the driver a *relative*
script path (MSYS leaves those alone, where it would rewrite one beginning with
``/``), and it finds a Python interpreter.

It finds that interpreter by **running** each candidate rather than locating it
with ``command -v``: on a Windows image ``python`` may be a Microsoft Store
app-execution alias that answers ``command -v`` and then does nothing. The
candidates are ``python3``, ``python`` and, last, the ``py -3`` launcher; each
must satisfy ``sys.version_info >= (3, 8)``. If none does, the wrapper exits 5
and says so. Everything after that is Python, which launches every process with
an argument array and a timeout -- never a shell string.

The driver imports nothing outside the standard library, needs Python 3.8 or
newer, installs nothing, and writes nothing outside ``_build/windows-verify/``.

The transcript
--------------

Everything the run observes goes into ``_build/windows-verify/transcript.txt``
and, simultaneously, to stdout. It opens with a host block -- UTC timestamp,
``platform.platform()``, system, release, version, machine, node, ``os.name``,
``sys.platform``, the Python version and executable, the working directory, and
whichever of ``CI``, ``GITHUB_ACTIONS``, ``GITHUB_WORKFLOW``, ``GITHUB_RUN_ID``,
``GITHUB_SHA``, ``RUNNER_OS``, ``RUNNER_ARCH``, ``RUNNER_NAME``, ``ImageOS`` and
``ImageVersion`` are set -- so a reader can tell which machine and which runner
image produced it rather than inferring it.

Captured process output is escaped to ASCII and CRLF-normalised, and nothing
else about it is altered. That is deliberate: Percolator prints a non-ASCII
author name in its banner, a Windows console in CI uses whatever code page the
image chose, and a transcript that died half way through recording a result
with a ``UnicodeEncodeError`` would be worse than no transcript.

How the seven steps map to the checklist
========================================

The steps are the checklist's, in the checklist's order. Three of them assert a
pinned SHA-256; the rest assert what was observed in captured output. **A
checksum mismatch is a hard stop.** It is never retried, never warned about and
never continued past: the pinned values are what tie a Windows run to the same
bytes the Linux-side evidence in :ref:`windows-artefact` was gathered from, and
a run against different bytes would describe an experiment nobody performed. A
mismatch ends the run as a HARNESS FAILURE, and the transcript names both the
expected and the observed value.

.. list-table:: The seven steps and the identity each one pins
   :header-rows: 1
   :widths: 8 52 40

   * - Step
     - What it does
     - Pinned SHA-256 it asserts

   * - 1
     - Downloads ``percolator-v3-07.exe`` from the ``rel-3-07-01`` release,
       retrying only genuine transport errors.
     - ``a9860e02a7e78b9bc069438e6564eb20e90bb46244aa628d567e4b69fe1ea348``,
       and 1 818 841 bytes.

   * - 2
     - Extracts the NSIS payload with
       ``scripts/feasibility/extract_nsis.py``. The installer is **not** run,
       and must never be: its manifest requests ``requireAdministrator``, so
       ``/S`` does not avoid the elevation prompt.
     - ``bin/percolator.exe``
       ``044f3957e2f05a38d13d8c77136f24435827d8563850b8808b5ad52e6aa4691e``
       at 804 864 bytes, 22 distinct payload files, ``percolator_in.xsd``
       ``fc3c95e02950af3c44ae0c830c3ecf8005a543358eb7311f94c12dab4a216b87``
       and ``percolator_out.xsd``
       ``c4c664ea673817ded4616958b0682f401f940f40212246473e75835f3597bc1b``.

   * - 3
     - Captures the whole of ``percolator.exe --help``.
     - None. It asserts instead that the version banner is present, that the
       exit code is 0 as the Linux build of the same release gives, and that
       the output contains ``--xmloutput``.

   * - 4
     - Generates the 400-PSM synthetic PIN with
       ``scripts/feasibility/make_synthetic_pin.py``, then runs
       ``percolator.exe -X pout.xml test.pin`` and records the exit code,
       whether the file exists, its size, its first ten lines and its
       ``<psm`` count.
     - The PIN:
       ``6643ede48534fcd28c90a1d4e53781e47ba39b0523e9f907ea8e1a63b15af61e``.
       The reference figures it is compared against -- 143 729 bytes, 200
       ``<psm`` elements -- were measured on Linux with this exact input.

   * - 5
     - Runs ``percolator.exe --xml-in test.pin`` from the extracted
       ``INSTDIR`` and records whether ``Compiler flag XML_SUPPORT was off``
       appears. The discriminating test; see
       :ref:`windows-ci-falsifiability`.
     - None. It asserts markers in the captured output, which is the whole
       difficulty.

   * - 6
     - Records whether the account is an administrator
       (``IsUserAnAdmin()`` through ``ctypes``, plus ``whoami`` and
       ``whoami /groups``), that the installer was not executed, and every
       process the earlier steps launched with its cwd and its outcome.
     - None.

   * - 7
     - Writes the transcript, and records its path and length in it.
     - None.

The PIN generator is shared on purpose. It used to be a Python heredoc inside
``scripts/feasibility/windows-artefact.sh`` and was factored out so that both
platforms call one generator: a cross-platform comparison whose two sides are
fed different files establishes nothing, and two copies of a generator are two
things that drift. It writes with an explicit newline rather than the platform
default, so Windows text-mode translation cannot silently change the bytes, and
the run asserts the pinned SHA-256 of the file *on disk* before Percolator is
given it.

Section 8, and why it is not part of the checklist
==================================================

The checklist was written on 2026-08-29 and describes the NSIS installer's
payload. On that same day the owner took ``D-002`` **option C**: the product
obtains Percolator from the portable ``noxml`` archive on every tier-1
platform, and the NSIS payload extractor the installer was going to contain is
not built. The checklist therefore exercises an artefact the product no longer
ships.

The checklist is nevertheless implemented exactly as written, because it is the
gate. Section 8 is additional: while a Windows machine is available, it also
downloads ``percolator-noxml-windows-portable.zip`` (SHA-256
``1510c2cfc8ce05822ac46e53954c7e6e5fa42305789fa94aad2f73657a0f94a2``, 329 022
bytes), extracts its single ``percolator.exe``
(``b9d9bbe82bc4a68d367a8cb00a0a22892b0b1cb516510fd0459d1df6805f059f``, 707 072
bytes) with the standard library's ``zipfile`` -- no installer, no elevation --
and runs the same three probes against it: ``--help``, ``-X`` on the same PIN,
and ``--xml-in``.

Two properties of section 8 matter:

* **It is reported separately and does not gate the job**, with one exception.
  If the portable binary *runs* -- banner present, no launch error, no timeout
  -- and yet writes no output file, an empty one, or one with no ``<psm``
  elements, the job fails loudly. That combination would contradict the premise
  ``D-002`` option C rests on, and a harness that swallowed it would be worse
  than useless.
* **Nothing is copied next to that binary.** The MSVC runtime DLLs from the
  NSIS payload are deliberately not placed beside it, because the open question
  about this artefact is whether the ZIP alone suffices. A missing-DLL failure
  there is information, not a harness fault, and the transcript says so: on
  Windows it usually appears as exit ``3221225781`` (``0xC0000135``,
  ``STATUS_DLL_NOT_FOUND``).

Section 8 also carries the positive control that the next section is about.

.. _windows-ci-falsifiability:

Falsifiability: why the discriminating test needs a positive control
====================================================================

This is the most important section on this page, and the one to read before
changing step 5.

Step 5 tests for an **absence**. An ``XML_SUPPORT=ON`` build must not print
``Compiler flag XML_SUPPORT was off``; the ``noxml`` twin prints it by name.
The trouble is that the absence has many other causes, and every one of them
looks identical to success in a naive check:

* the binary was never launched, because the path was wrong;
* it launched and died immediately on a missing DLL;
* it launched and hung, and was killed by a timeout;
* it ran, but its output was not captured -- a redirection or encoding
  problem, not a Percolator problem;
* it ran and stopped before ever reaching the pin-XML input path where the
  diagnostic is emitted.

In all five cases the string is absent. A check that reported "absent, so this
is the XML build" would be a check that returns the same answer whether or not
the program exists. That is not a test; it is a formality dressed as one, and a
job engineered around it would go green forever.

So the driver refuses to let an absence mean anything until the *same captured
output* carries positive proof that the binary ran and got as far as the code
in question. Two markers are required:

.. list-table::
   :header-rows: 1
   :widths: 40 60

   * - Marker required in the same output
     - What its presence shows

   * - ``Percolator version``
     - The binary started and produced its own banner. Nothing was launched
       and lost.

   * - ``Reading pin-xml input from datafile``
     - It reached the pin-XML input path -- the code path where a ``noxml``
       build emits the diagnostic. Without this, the program may simply have
       stopped earlier.

The order of the driver's tests is itself part of the design. The diagnostic is
looked for first, because its *presence* shows the binary ran and refused, and
that is a NEGATIVE whatever else is missing. Then a launch error, then a
timeout, then the banner, then the pin-XML marker. Only when all of those have
been cleared is the absence allowed to count as an ``OK``. Anything else is
INCONCLUSIVE, and INCONCLUSIVE never becomes PASS.

That still leaves one hole, and section 8 fills it. Suppose the detector itself
is broken -- the output is captured in a way that mangles the string, or the
console code page rewrites it, or a future edit breaks the comparison. The
markers above would still be present and the diagnostic still absent, and the
step would report ``OK`` for the wrong reason. So section 8 runs the *same*
probe against the ``noxml`` build, where the diagnostic **must** appear. That
is the positive control:

* Control shows the diagnostic: the detector can see that string on this host,
  and step 5's absence result stands on its own markers plus a working
  detector.
* Control runs and does **not** show it: the detector cannot be shown to work
  here, so step 5's absence establishes nothing. The driver **downgrades** a
  step 5 ``OK`` to INCONCLUSIVE and records why, rather than reporting a
  result it has just discovered it cannot claim.
* Control could not be run at all (launch failure, timeout, no banner): no
  downgrade, but the transcript notes that step 5's absence rests on its own
  positive markers alone, without a control.

The downgrade is applied to the step 5 finding *object*, after section 8 has
run, and the final assertion is written only then -- so what appears in the
verdict block is the post-downgrade answer, not a snapshot of the optimistic
one. The harness's ``--self-test`` includes that exact case.

.. _windows-ci-verdicts:

The four verdicts and their exit codes
======================================

.. list-table:: Verdicts
   :header-rows: 1
   :widths: 18 8 34 40

   * - Verdict
     - Exit
     - What the driver means by it
     - What it would mean for the project

   * - PASS
     - 0
     - Every checklist assertion held, on Windows, each naming the value it
       observed.
     - The transcript the checklist asked for exists, and says yes. What
       follows from it for gate item 8 is the phase orchestrator's and the
       owner's to decide; a transcript is evidence, not a gate decision.

   * - NEGATIVE
     - 1
     - The binary ran and the evidence contradicts an inference this project
       currently relies on.
     - **A finding to escalate, not a job to fix.** The right response is to
       record it and revisit the inference -- the marker reading in
       :ref:`windows-artefact-control`, or the premise behind ``D-002``
       option C if section 8 is the source. Adjusting the harness to make
       this go away would destroy the only thing it is for.

   * - INCONCLUSIVE
     - 2
     - The binary did not run far enough for the test to mean anything.
     - Nothing is established either way. The environment or the harness has
       to change before another run means more than this one did. It is not
       a negative result about Percolator and must not be recorded as one.

   * - HARNESS FAILURE
     - 3
     - Download, checksum, extraction, Python or the PIN generator failed.
     - **Nothing was learned about the binary.** Ours to fix -- with the one
       exception that a checksum mismatch means the upstream bytes are not
       the bytes this project recorded, which is a finding in its own right.

Four further statuses are not verdicts about the binary:

.. list-table::
   :header-rows: 1
   :widths: 24 8 68

   * - Status
     - Exit
     - Meaning

   * - CHECK-ONLY
     - 0
     - The platform-independent steps ran and no Windows binary was executed.
       It exits 0, and it is **not** a pass; the transcript says so in terms.

   * - REFUSED
     - 4
     - Not a Windows host, and ``--check-only`` was not given. The driver
       names ``os.name``, ``sys.platform`` and the platform string and stops.

   * - MISUSE
     - 5
     - Bad arguments, or no usable Python 3.8+ was found.

   * - SELF-TEST FAILED
     - 6
     - A damaged case was accepted, or a control case was rejected. The
       harness cannot be relied on to report a negative.

Exit code 0 proves nothing on its own, which is why every assertion in the
transcript names the value it observed next to the value expected. No step is
recorded as having held because a command returned 0.

.. _windows-ci-proves:

What a pass would establish, and what it still would not
========================================================

Would be established
--------------------

On the machine named in the transcript's host block, and on the date in it:

* That the payload binary whose SHA-256 is ``044f3957...`` starts and prints
  its own version banner.
* That its ``--help`` reaches the code which prints ``--xmloutput``, and exits
  0. This is a different claim from a ``strings`` hit, which
  :ref:`windows-artefact-control` shows discriminates nothing.
* That ``-X`` on the pinned 400-PSM input writes a Percolator XML file, with
  its size and ``<psm`` count recorded beside the Linux twin's 143 729 bytes
  and 200 elements.
* That ``--xml-in`` reached the pin-XML input path and did not print
  ``Compiler flag XML_SUPPORT was off``, with section 8's control showing the
  detector could see that string on that host.
* That the payload was obtained without running the installer and without an
  elevation prompt, from a non-interactive session that could not have
  answered one.

Would not be established
------------------------

Following the model of :ref:`windows-artefact-proves`, at length, because this
list is the point:

* **The standard-user path.** A hosted GitHub runner is an administrator; step
  6 records that fact rather than glossing it. The account the product's users
  will have is not the account that ran, and the non-administrator case is the
  one the product actually needs.
* **Consumer Windows.** ``windows-latest`` is a Windows Server image, not
  Windows 10 or Windows 11 as an end user has it. Which server image, and
  which build, is whatever the alias points at on the day; the transcript's
  host block records it so that the run is attributable to a specific image
  rather than to "Windows".
* **Any architecture but x86-64.** Nothing about Windows on ARM.
* **Whether the portable ZIP needs a Visual C++ redistributable on a clean
  machine.** The runner image ships Visual Studio and its redistributables, so
  a successful launch there is compatible with both answers, and a project
  already carrying "the Windows portable zip needs a Visual C++ runtime it does
  not carry" as a known cost of ``D-002`` option C gains nothing from it. The
  transcript states this limitation itself.
* **Anything about macOS.** No macOS artefact is touched by this harness, and
  the macOS binaries have not been executed either.
* **Anything about other Percolator versions.** This pins one artefact from
  ``rel-3-07-01``. It says nothing about 3.06, 3.08 or 3.09, whose XML handling
  differs.
* **Whether the output validates or is usable downstream.** The job runs no XSD
  validator and no JVM, so it does not address whether the ``-X`` output passes
  ``percolator_out.xsd`` or whether the Limelight converter accepts it. Those
  are the open questions in E2 of :ref:`windows-artefact-escalation`.
* **Durability.** One run, one image, one day. Runner images are updated, and a
  result from one is not a property of Windows.
* **The manifest.** A run does not license new wording on its own;
  ``xml_capability`` stays ``unverified-on-windows`` until one says otherwise,
  under the rules in :ref:`windows-artefact-manifest`, which this page does not
  restate or relax.

Running the harness yourself
============================

Three invocations, all from the project root::

    bash scripts/ci/windows-percolator-verify.sh --check-only
    bash scripts/ci/windows-percolator-verify.sh --self-test
    bash scripts/ci/windows-percolator-verify.sh

``--check-only`` runs every platform-independent step on any operating system:
the two downloads and their checksums, the NSIS extraction and its payload
checksums, the XSD checksums, and PIN generation against its pinned SHA-256. It
skips ``--help``, the ``-X`` half of step 4, ``--xml-in``, and section 8's
execution half. The verdict is CHECK-ONLY and the exit status is 0, and **that
is not a pass** -- no Windows binary was executed and nothing about Windows is
inferred. The transcript says exactly that. It needs about 2.1 MB of network.

``--self-test`` damages the harness on purpose and requires the right verdict
to come out of each damaged case: a wrong expected checksum, a corrupted
download, an empty capture, a binary that never launched, a timeout, a crash,
``-X`` writing nothing, the real ``noxml`` diagnostic, the step 5 downgrade,
and the exit-code mapping. It needs no network and no Windows machine, and
writes only into a sandbox under ``_build/windows-verify/``. On this Linux host
on 2026-08-30 it reported ``34 case(s), 0 failed`` and exited 0.

The bare invocation on a non-Windows host **refuses**: it prints ``REFUSED --
this is not a Windows host``, names ``os.name``, ``sys.platform`` and the full
platform string, points at ``--check-only``, and exits 4. It writes no
transcript, because it declines before there is anything to record. It does not
pretend to have run.

.. _windows-ci-status:

Status on 2026-08-30
====================

.. list-table::
   :header-rows: 1
   :widths: 40 60

   * - Question
     - Answer on 2026-08-30

   * - Does the workflow exist?
     - Yes, on the branch ``windows-percolator-verification``. It is not on
       ``main``.
   * - Has it been pushed?
     - No. The session that wrote it has no push credential of any kind: no
       ``gh``, no token, no SSH key, no credential helper.
   * - Has GitHub ever run it?
     - No. GitHub has executed **no** workflow in this repository at all: the
       Actions API's ``/actions/runs`` reported ``total_count = 0`` when the
       remote was queried anonymously on 2026-08-30, while
       ``/actions/workflows`` listed ``nightly``, ``pull-request`` and
       ``release`` as active. Actions is enabled; nothing has ever started.
   * - Is there a remote?
     - Yes, since ``D-008`` was decided on 2026-08-30.
   * - Has the Windows binary been executed?
     - No. That has not changed, and nothing on this page changes it.

So this page describes a harness, not a result. What it takes to turn it into a
result is a push and a pull request, after which the transcript either replaces
:ref:`windows-artefact`'s central caveat with a fact or fails loudly and says
why. Until then the second branch of gate item 8 is still where the project
stands, exactly as :ref:`windows-artefact-proves` records, and this page is not
evidence towards the first.
