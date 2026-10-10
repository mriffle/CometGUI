.. _user-tool-manager:

============
Tool Manager
============

CometGUI installs the scientific tools it runs -- Comet, Percolator, PDV and
the Limelight converter -- so that you do not have to. The Tool Manager is
where you see which builds this machine can have, install one, watch it being
installed, and read what the installer found out about it afterwards.

Nothing here asks you to choose a download, find a checksum or unpack an
archive. What it does ask you to read is the **evidence** beside each claim,
because a build this project has watched run and a build nobody has ever
started are both offered here, and the difference matters.

.. note::

   **This page describes what the Tool Manager does today.** It is complete
   about its own limits: :ref:`tool-manager-limits` lists the things it cannot
   do yet, rather than leaving you to find them. What each tool is *for* is in
   the pages for :doc:`percolator`, :doc:`pdv` and :doc:`limelight`; which
   builds exist for your platform is in :doc:`platform_support`.

.. contents:: Contents
   :depth: 2
   :local:

What you see
============

One line at the top says what the list holds -- ``6 tool builds on this
host.`` -- and then one row per build.

A row is a whole build, not a tool: a tool can appear more than once, because a
tool can have more than one version, and a binary you registered yourself is a
row of its own after the releases CometGUI can install.

Each row carries:

**The tool and the version**
    ``percolator 3.07.1`` -- the version as upstream spells it, always. (The
    directory it installs into uses a normalised form, so you may see
    ``percolator/3.7.1/`` on disk. Same release.)

**Where it came from and what state it is in**
    ``CometGUI-managed: not installed``. See :ref:`tool-manager-row-states`.

**What the build can do, and how that is known**
    ``Capabilities: XML_OUTPUT (observed-by-execution), XML_DECOY_OUTPUT
    (observed-by-execution)``, or ``Capabilities: none declared``. See
    :ref:`tool-manager-capabilities`.

**Advisories**
    The caveats that come with this version, each on its own line, or
    ``Advisories: none``. They are printed word for word as the registry holds
    them, because re-wording a caveat is the application saying something the
    registry did not.

**How large the download is**
    ``Download: 2,798,963 bytes``. See :ref:`tool-manager-download-size`.

**Why a build will not run here**, where that applies
    The loader diagnostic, printed exactly as produced, naming what the build
    requires, what this machine has, and what else you could use instead.

**Where it is installed**, once it is
    ``Installed at: ...``.

And two actions: **Install**, available for a managed build that is not
installed or whose last attempt did not succeed, and **Cancel**, available
while an install of that row is running.

.. _tool-manager-row-states:

The four kinds of row
=====================

.. list-table::
   :header-rows: 1
   :widths: 24 30 46

   * - Kind of row
     - What the row says
     - What you can do

   * - **Installed**
     - ``CometGUI-managed: installed``, with the path it installed to.
     - Nothing needs doing. The capabilities shown are the ones the installer
       probed on this machine.

   * - **Available**
     - ``CometGUI-managed: not installed``, or ``CometGUI-managed: the last
       install attempt did not succeed``, or ``CometGUI-managed: installing``
       while one is running.
     - **Install** fetches it. A failed attempt can be started again.

   * - **Unavailable on this platform**
     - ``CometGUI-managed: not published for this platform`` where upstream
       publishes nothing for this operating system and architecture, or
       ``CometGUI-managed: cannot run on this host`` where it is published but
       this machine does not meet what it requires.
     - Nothing. The row is shown rather than hidden, with its reason, so that
       "where did Percolator 3.09 go?" has an answer. There is no Install
       action on it.

       The two differ in what else the row can say. *Cannot run on this host*
       comes from a real artefact record, so it carries that build's
       capabilities, its advisories and the diagnostic naming what is missing.
       *Not published for this platform* has no artefact record behind it here
       at all, so the row reads ``Capabilities: none declared``,
       ``Advisories: none`` and ``Download: nothing to fetch on this host`` --
       an absence of information, not a set of findings.

   * - **Your own binary**
     - ``Your own binary: installed``, for a Percolator or a Comet you
       registered yourself.
     - Nothing to download; it was never fetched. Note the limit in
       :ref:`tool-manager-limits`.

A build that is not published here, and a build that cannot run here, are both
**shown and not hidden**. The rule is that the application must not *promise* a
one-click install that cannot work -- not that it should pretend a release does
not exist.

.. _tool-manager-no-managed-build:

When CometGUI has no build of a tool for your computer
------------------------------------------------------

Sometimes the developers of a tool publish no build of it at all for your kind
of computer. Then CometGUI has nothing to download, and the Tool Manager says
so in one sentence above the list, rather than leaving you to work it out from
a column of rows reading ``not published for this platform``.

**On an Intel Mac this is Comet.** The Comet developers publish macOS builds
for Apple silicon only -- both macOS files they publish are Apple-silicon
programs, whatever the file names suggest -- and an Apple-silicon program
cannot run on an Intel Mac. So on an Intel Mac the Tool Manager reads:

    No CometGUI-managed Comet exists for an Intel Mac (macos-x86-64): the Comet
    developers publish no build of it for this kind of computer, so there is
    nothing for CometGUI to download and install. You can register a Comet you
    have built or obtained yourself: CometGUI runs it to read its version and
    check what it can do, and then lists it below as your own binary.

Below it, **Register your own Comet...** opens a file chooser. CometGUI runs
the file you choose to read its Comet version, checksums it, and asks it for
its parameter files to see what it can do; if any of that fails you are told
which, in a sentence of its own, and nothing is added. A Comet registered this
way appears in the list as ``Your own binary: installed`` and a search whose
parameters are for that Comet release uses it. CometGUI did not download it,
so it carries an advisory saying so, and it is never assumed to read Thermo RAW
files.

Every Comet release is still listed, as ``not published for this platform``,
with no Install action: CometGUI does not offer an install that cannot work.
On an Apple silicon Mac, on Linux and on Windows none of this appears -- Comet
is installed from the Tool Manager as usual.

The same sentence appears for any tool, on any computer, for which CometGUI's
list of published builds has none -- for example Percolator on Linux on a
64-bit ARM processor, a platform release 1 does not support. Where CometGUI
cannot register a binary of that tool, the sentence says so and there is no
register action.

Your own Percolator
-------------------

Where no managed XML-capable Percolator exists for your platform, the
documented remedy is to point CometGUI at a Percolator you already have. A
binary registered that way must be **Percolator 3.05 or newer** -- an older one
is refused with a message naming the version found and the minimum required,
and something that is not Percolator at all is refused differently from
something that is merely too old.

What you then get is an ordinary row reading ``Your own binary: installed``,
with the capabilities CometGUI probed **by running that file here**, its MD5
and SHA-256 recorded for the provenance of any run that uses it, and one
advisory saying what it cannot do for you:

    This Percolator was registered from a file on this machine. CometGUI did
    not download it and cannot check it against a pinned checksum, so its
    provenance is whatever you know about where it came from. Its capabilities
    below were probed by running it here.

There is no download figure on the row, because there was no download.

.. _tool-manager-capabilities:

Capabilities, and the evidence behind them
==========================================

Every capability is shown with how it was established. There are three values
and they mean different things.

``observed-by-execution``
    This project ran **that build on that platform** and watched it do this.
    For Percolator's XML output that means: the binary was run over a synthetic
    input of 64 target and 64 decoy rows, and the file it wrote was opened and
    inspected -- the root element, the namespace and the exact number of PSM
    entries.

``inferred-from-artefact-bytes``
    The claim comes from markers found inside the downloaded artefact, not from
    running it.

``unverified``
    The claim comes from upstream's release material alone.

Two things follow from this that are worth knowing.

**Before you install, the evidence is about the artefact; after you install, it
is about your machine.** The installer probes every build it installs and the
probe's answer is what the application acts on. A capability that the registry
claims and the probe does not find is not offered downstream.

**An empty capability list is an answer, not a gap.** ``Capabilities: none
declared`` on PDV and on the Limelight converter is correct: neither has a
capability of the kind this list describes. It does not mean the probe failed.

The words *verified*, *confirmed*, *proven* and *tested* are not used anywhere
in the registry of a build nobody has run -- so if a row does not say
``observed-by-execution``, nobody in this project has started that binary.

.. _tool-manager-download-size:

The download size is the whole transfer
=======================================

``Download: 2,798,963 bytes`` on Percolator 3.07.1 for Linux is **the number
of bytes that will move**, not the size of one file and not the size of what
ends up on disk.

That build fetches two things: the 946,303-byte portable archive holding the
binary, and the 1,852,660-byte package the two XSD schema files are taken out
of. A figure naming only the first would be three times too small for the thing
it describes, in a number you would act on before starting a transfer on a slow
connection.

PDV has no companion download, so its figure is the artefact itself:
103,407,417 bytes. It is by far the largest of the four and the one worth
starting when you have the bandwidth.

Installing, watching and cancelling
===================================

Pressing **Install** starts the work on a background thread; the interface
stays responsive and the row reports where it has got to:

``Downloading: 20,004,761 of 103,407,417 bytes``

then ``Verifying the download``, ``Extracting``, ``Installing``, ``Probing``
and finally ``Done`` -- or ``Cancelled``, or ``Failed``.

**Cancel** stops the transfer and deletes the partial file. This is the
deliberate design rather than an omission: a cancelled download does not leave
bytes behind that a later attempt would resume from and trust. The next attempt
starts from zero, which has been measured on the 103 MB PDV download -- two
requests, neither of them asking for a byte range.

If a download arrives corrupt, it is rejected and **the tool is never run**.
The checksum check happens before anything is unpacked, and a failed check ends
the install without launching a process.

.. _tool-manager-diagnostics:

When a build will not run here
==============================

A build that cannot start on this machine produces a diagnostic, and the row
shows it exactly as it was produced. It names three things: what the build
requires, what this machine has, and what else is available -- for example
``Alternatives: percolator 3.06.5 linux-x86-64``, or ``Alternatives: none
known -- registering a local binary is the documented remedy``.

A build that fails to *start* is never reported as lacking a capability. On
Windows, a missing Visual C++ runtime is reported as a missing
``MSVCP140.dll``, not as "this build cannot write XML" -- because the first
tells you to install a runtime and the second would send you to change
Percolator version for no reason.

.. _tool-manager-limits:

What this section cannot do yet
===============================

Stated plainly, because finding these out by trying is worse.

**A registered local binary lasts the session.** A Percolator or Comet you
register yourself is checksummed, probed and shown as ``Your own binary``, and
it is forgotten when the application exits. Persisting it needs a settings
store that no phase has built yet.

**This screen registers a binary only for a tool it has no build of.** The
register action appears only in the sentence described in
:ref:`tool-manager-no-managed-build` -- on an Intel Mac, for Comet. A
Percolator of your own is registered from the Percolator section --
**Register a local Percolator binary...**, with a file chooser
(:doc:`percolator`) -- and appears here as ``Your own binary: installed``.

**There is no Refresh.** Rows are read when the section is built and again
around an install. A tool installed or removed outside CometGUI while it is
running is not noticed until the application is restarted.

**A failed install does not say why.** ``the last install attempt did not
succeed`` is all a row reports for a checksum, extraction or probe failure; only
a *loader* failure carries a diagnostic. The row says what it knows rather than
inventing a reason for a download it did not watch.

**Advisories hang off an artefact record, so a release with none here shows
none.** Percolator 3.09 publishes nothing for Linux. The row is still there,
reading ``not published for this platform`` -- but with no artefact record
behind it on this machine it carries no advisories, so the advisory saying that
3.09 cannot emit the XML the Limelight path needs is not shown on Linux. The
Percolator section names a newer version it passed over, with the capability
it lacks, only for a version that can run here -- a 3.09 you registered
yourself, for example (:doc:`percolator`).

**The long notes behind each capability are not shown.** Each capability in the
registry carries a sentence recording who established it and what they saw.
Those are in the developer documentation, at :ref:`dev-tool-registry-provenance`
and :ref:`dev-tool-registry-evidence`, not in the row.

**Reading the list is not free.** With four tools installed, building the
offered list starts three processes -- two binaries and a Java runtime -- and
took 353 ms on the development machine, because every installed build is asked
whether it still starts. On a slower machine that is a visible pause when the
section is opened.

**macOS is unverified.** No macOS binary has ever been executed anywhere in
this project, on any machine, so on macOS everything on this page describes what
the application is built to do rather than what has been seen to happen. On
Windows, one Percolator build has been executed once, on a hosted CI runner --
not on a clean end-user machine. Everything described here as measured was
measured on Linux x86-64.
