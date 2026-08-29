=======================================================================
PHASE-00 work log -- Feasibility, Legal and Upstream Verification
=======================================================================

:Phase: 00
:Phase orchestrator: Phase-00 orchestrator subagent (session 2026-08-29)
:Started: 2026-08-29

Maintained by the phase orchestrator as the phase runs. One row per work unit.
A unit is not done until it carries a sign-off entry naming what was run and
what was observed -- "agent reported success" is not a sign-off.

Decomposition rationale
=======================

Phase 00 is evidence work, not code, so the units are cut along *evidence
boundaries*: each unit owns one investigation, one script family and one
output document, so that two units never write the same file and can therefore
run concurrently. Six units (1-6) have no dependencies on each other and were
launched together; three (7-9) consume artefacts that earlier units produce and
were launched only after those units were signed off.

Ephemeral inputs, per the main orchestrator's ruling on ``D-006``, live under
``/workspace/scratch/`` (added to ``.gitignore`` at the start of the phase) and
are **feasibility inputs, not the project's chosen fixture**.

Work units
==========

.. list-table::
   :header-rows: 1
   :widths: 5 30 17 48

   * - #
     - Unit and acceptance conditions
     - Gate items / rules served
     - Sign-off: what was run, what was seen, date

   * - 1
     - **Documentation build harness.** Project virtualenv at ``.venv`` with
       Sphinx; throwaway Sphinx config under ``_build/`` (Phase 01 owns the
       real ``docs/conf.py``, which must NOT be created); re-runnable
       ``scripts/feasibility/check-docs.sh`` that builds
       ``docs/feasibility/*.rst`` with ``sphinx-build -n -W``. Accept when the
       script builds a deliberately broken RST to a failure and a clean one to
       success -- exit code 0 alone is not accepted as proof.
     - Enables the RST cleanliness requirement for every deliverable
       (``ONBOARDING`` Documentation rules)
     - Signed off 2026-08-29 by the phase orchestrator. Ran
       ``bash scripts/feasibility/check-docs.sh`` myself: exit 0, "build
       succeeded.", 4 HTML pages on disk including a 12091-byte
       ``_build/docs-check/html/feasibility/index.html``. Then wrote my OWN
       broken RST (short title underline + ``:py:func:`` to a nonexistent
       target + a ``list-table`` whose ``:widths:`` has 2 entries for 3
       columns) and ran the harness on it: **exit 1**, with
       ``ERROR: "list-table" widths do not match the number of columns in
       table (3).``, ``WARNING: py:func reference target not found`` and
       ``build finished with problems, 7 warnings (with warnings treated as
       errors).`` The gate really bites. Read commit ``42feb8c`` in full (268
       lines, 2 files, nothing else swept in). ``docs/conf.py`` confirmed
       absent. The agent declined to use a ``:glob:`` toctree because an empty
       glob is itself a ``-W`` error and the alternative was
       ``suppress_warnings``, which would have weakened the gate -- correct
       call, accepted.

   * - 2
     - **Upstream fact re-verification, live, today.** Re-check every row of
       ``specification.rst``'s Verified Upstream Facts table against live
       sources on 2026-08-29, recording URL, method, date and finding per row.
       Produces ``scripts/feasibility/verify_upstream_facts.py`` (re-runnable,
       emits JSON + report) and ``docs/feasibility/upstream-facts.rst``, which
       must carry an explicit "Differences from specification.rst" section.
       Accept when every row has a fresh evidence line and no row is copied
       from the specification.
     - Gate 1; feeds gate 7 and 10
     - Signed off 2026-08-29 by the phase orchestrator. Read commit
       ``cc2ac06`` (1615 lines, 2 files, nothing else swept in). Re-ran
       ``python3 scripts/feasibility/verify_upstream_facts.py``: 22 facts, 18
       CONFIRMED / 0 CHANGED / 4 UNVERIFIED (the four artefact rows delegated
       to units 3 and 4). Then checked five rows against live sources MYSELF,
       not through the agent's script: PDV ``releases.atom`` returns v2.7.0 /
       2026-08-14T19:55:27Z; ``rel-3-09``'s ``CMakeLists.txt`` fetched raw is
       3541 bytes with **0** occurrences of ``XML_SUPPORT`` and **0** of
       ``xerces``, while ``rel-3-08``'s carries ``option(XML_SUPPORT ... OFF)``
       at line 23; the CasanovoGUI repository API still returns
       ``license = None``, pushed 2026-08-21. I also executed
       ``comet.linux.exe`` myself: banner ``Comet version "2026.02 rev. 2
       (6edec91)"``, ``readelf -d`` says "There is no dynamic section in this
       file", and ``-p`` / ``-q`` emit **96** and **118** parameters with
       exactly the 22 extra names the specification lists and none removed.
       ``check-docs.sh`` on the document exits 0. Three specification
       differences escalated, none patched by the agent -- correct.
       **I found one thing the agent did not:** ``releases.atom`` lists
       ``rel-3-08-01``, but ``GET /releases/tags/rel-3-08-01`` returns
       **HTTP 404**, so the tag-with-no-release claim holds AND the atom
       fallback must never be used alone to enumerate releases.

   * - 3
     - **Percolator artefact enumeration and payload extraction (Linux,
       macOS).** Enumerate every Percolator release and its assets from the
       upstream API; derive *latest compatible* from that data rather than
       from the specification; extract the 3.07.1 ``.deb`` (``ar`` +
       ``data.tar.gz``) and ``.pkg`` (``xar!`` + gzip + ``070707`` cpio)
       payloads without root and in pure Python (no ``cpio``/``xar``/``7z`` on
       this host); execute the Linux binary and capture ``-X``/``-Z``; locate
       the XSD companions. Produces
       ``docs/feasibility/percolator-artefacts.rst``.
     - Gate 6, 7, 9 (``.deb`` and ``.pkg``); ``R-PERC-01``, ``R-PERC-02``,
       ``R-TOOL-02``; ``D-002``/``D-003`` evidence
     - Signed off 2026-08-29 by the phase orchestrator. Read commit
       ``a677e09``. **Executed the extracted binaries myself**, not through the
       agent's scripts: ``scratch/percolator/3.07.1-linux-x86_64/usr/bin/
       percolator -h`` prints ``Percolator version 3.07.1, Build Date Jun 20
       2024 13:21:20`` and its help carries ``-X <filename> / --xmloutput
       <filename>  Path to xml-output (pout) file.`` and ``-Z /
       --decoy-xml-output ... Only available if -X is set.`` Both XSDs are in
       the payload (``percolator_out.xsd`` 10388 B, ``percolator_in.xsd``
       15457 B). Ran the 3.08.0 binary myself: it fails at the loader with
       ``version `GLIBCXX_3.4.32' not found`` and ``version `GLIBC_2.38' not
       found`` -- the specification's claim, reproduced. Ran the 3.09 wrapper:
       ``Percolator version 3.09.0, Build Date May 21 2026``, and grepping its
       help for ``xmloutput|decoy-xml`` returns **0** matches. Parsed the macOS
       Mach-O header myself: magic ``0xfeedfacf`` (MH_MAGIC_64), cputype
       ``0x1000007`` = x86-64 only, with both XSDs beside it. Re-ran
       ``enumerate_percolator_releases.py``: latest compatible computes to
       **3.7.1 (rel-3-07-01)** under both the strict and the optimistic
       variant, from 28 releases -- confirms the specification rather than
       assuming it. ``check-docs.sh`` clean. D-003 presented as five costed
       options and not answered -- correct.

   * - 4
     - **Windows NSIS artefact: strongest obtainable evidence.** Decompress
       ``percolator-v3-07.exe``'s NSIS payload on Linux in pure Python, with
       nothing installed on the host, and inspect the extracted
       ``percolator.exe``. Must be scrupulous: extraction plus strings is
       *stronger inference*, NOT execution. Explicitly forbidden from writing
       any statement that Windows XML capability is verified. Produces
       ``scripts/feasibility/extract_nsis.py`` and
       ``docs/feasibility/windows-artefact.rst`` including the cost of a
       Windows runner.
     - Gate 8, 9 (NSIS)
     - Signed off 2026-08-29 by the phase orchestrator. Read commit
       ``12be584``. Deleted the extraction output and **re-ran
       ``scripts/feasibility/windows-artefact.sh`` myself** from clean: exit 0,
       all sections reproduced. **The extractor is proved by an independent
       route, which I re-checked myself**: the ``percolator.exe`` the pure-
       Python NSIS extractor pulls out of the ``noxml`` installer is
       byte-identical to the one Python's ``zipfile`` pulls out of
       ``percolator-noxml-windows-portable.zip`` -- both sha256
       ``b9d9bbe82bc4a68d367a8cb00a0a22892b0b1cb516510fd0459d1df6805f059f``,
       707072 bytes. Ran ``pe_info.py`` on the XML build: PE32+, machine
       ``0x8664``, console subsystem, imports ``xerces-c_3_1.dll``; both XSDs
       are in the payload. **Overclaim audit: I grepped the document for
       verified/confirmed/proven/tested near any XML-capability claim and found
       none** -- the manifest wording it prescribes is
       ``xml_capability: unverified-on-windows`` and the sentence "The binary
       was not executed on Windows" appears four times. Gate item 8 therefore
       takes its SECOND branch, correctly. **The unit also produced the phase's
       most consequential finding** (see "Blockers escalated"): the ``noxml``
       twin is not distinguishable by the ``-X`` help text, which is what gate
       item 8's own suggested test relies on.

   * - 5
     - **Project-local toolchain and jpackage.** JDK and build tool under
       ``tools/<name>-<version>/``; nothing on the host, no ``apt``, no
       ``sudo``, no host pip. Prove ``jpackage`` produces a *launchable*
       Linux bundle -- launched and its output checked, not merely produced.
       Produces ``scripts/feasibility/install-toolchain.sh`` (recreates
       ``tools/`` from scratch, since ``tools/`` is gitignored) and
       ``docs/feasibility/toolchain.rst`` carrying the provenance manifest
       (URL, version, date, SHA-256, licence per tool) and the Windows/macOS
       runner requirements.
     - Gate 4; ``R-PLAT-02``, ``R-PLAT-03``
     - Signed off 2026-08-29 by the phase orchestrator. Read commit
       ``5b4482e`` (1470 lines, 5 files). **Moved the installed toolchain
       aside and re-ran the installer cold**
       (``COMETGUI_TOOLCHAIN_NO_CACHE=1 bash scripts/feasibility/
       install-toolchain.sh``): re-downloaded 388 MB + 9 MB, verified both
       SHA-256s against the pins, installed, and self-verified
       ``java.version = 25.0.4.1``, ``jpackage --version = 25.0.4.1``, all
       seven JavaFX modules, ``Apache Maven 3.9.16`` -- 13 s. **I then tested
       the checksum gate myself** by copying the script with Maven's pin
       replaced by ``deadbeef...``: it printed ``FATAL: maven: SHA-256
       MISMATCH``, exited 1, did **not** create ``tools/apache-maven-3.9.16``,
       and deleted the bad archive. Ran ``jpackage-proof.sh`` from a deleted
       ``dest/`` and then **launched the produced bundle myself** with
       ``env -i PATH=/usr/bin:/bin`` (no ``java`` on that PATH, verified):
       ``java.version = 25.0.4.1``, ``java.vendor = BellSoft``, ``java.home =
       .../ToolchainProbe/lib/runtime``, ``self contained = true``,
       ``PROBE RESULT = PASS``, exit 0. ``deb`` fails for want of ``fakeroot``
       and ``rpm`` is not offered at all -- recorded as a host constraint, not
       worked around. Gate item 4 PASSES for ``app-image`` on Linux.

   * - 6
     - **Fixture candidates and ephemeral proof input.** Costed shortlist of
       candidate fixture datasets with licences for ``D-006`` (choosing one is
       NOT in scope), plus acquisition of one small openly licensed spectrum
       file and FASTA into ``scratch/`` as an ephemeral feasibility input.
       Produces ``docs/feasibility/fixture-candidates.rst`` and
       ``scripts/feasibility/fetch_ephemeral_input.py``. Accept when the
       document states plainly that the ephemeral input is not the project's
       fixture.
     - ``D-006`` deliverable; unblocks gate 2 and 3
     - Signed off 2026-08-29 by the phase orchestrator. Read commits
       ``f5def32`` and ``b06c1d3``. **Deleted ``scratch/fixture`` entirely and
       re-ran ``python3 scripts/feasibility/fetch_ephemeral_input.py``
       myself**: 2.6 s, all four files re-downloaded, every SHA-256 and byte
       size re-verified, and the script's own content checks re-run. **I then
       parsed the mzML myself** rather than trusting its counts: 728 and 610
       ``ms level value="2"`` spectra against declared ``spectrumList``
       counts of 801 and 671, and 20652 ``>`` records in the FASTA -- matching
       the agent's figures exactly. Six candidates with per-file sizes,
       parsed MS2 counts and licence URLs; the strongest licence finding is
       that PRIDE exposes a machine-readable per-project ``license`` field, so
       "a PRIDE dataset" is not a licence status. **D-006 is not answered**:
       the document says so in three places and the ephemeral input is labelled
       a feasibility input throughout. Nothing under ``scratch/`` is tracked by
       git -- checked with ``git ls-files``.

   * - 7
     - **JavaFX startup smoke and GUI automation spike.** JavaFX startup on
       the pinned JDK/JavaFX pair, headless and (if obtainable without
       touching the host) headed; TestFX spiked against the same pair with a
       written verdict and a named fallback. Produces
       ``docs/feasibility/gui-automation-spike.rst``. An unobtainable headed
       run is recorded as unverified, not as a pass. *Depends on unit 5.*
     - Gate 5
     - Signed off 2026-08-29 by the phase orchestrator. Read commit
       ``610d026``. **Ran ``bash scripts/feasibility/javafx-smoke.sh``
       myself**: all 7 stages PASS. The evidence is real, not a
       did-not-throw: from inside ``Application.start()`` the harness reports
       FX thread ``JavaFX Application Thread``, toolkit ``QuantumToolkit``,
       ``javafx.runtime.version = 25.0.4+1``, and -- the check that actually
       proves rendering happened -- ``Scene.snapshot()`` pixel (2,2) =
       ``ff204080``, matching the CSS background exactly. **Both negative
       controls fired under my run**: stage 5 broke the expected FX version
       and the harness exited 1; stage 7 broke the TestFX assertion and
       Maven reported ``AssertionFailedError: expected:
       <=NOT-WHAT-THE-HANDLER-PRODUCES> but was: <=COMET>``, ``BUILD
       FAILURE``. **I also ran ``javafx-headed-xvfb.sh`` myself**: exit 0,
       all 6 stages, 139 Debian packages verified and extracted
       project-locally, Xvfb on ``:99``, JavaFX started on
       ``GtkApplication``, 14 checks passed, TestFX green headed too. So both
       headless AND headed are verified, not just headless. Verdict for gate
       item 5: **TestFX 4.0.18 works**, and the named fallback (JUnit 5 +
       ``Platform.runLater`` + ``javafx.scene.robot.Robot``) is proved
       alongside it rather than merely proposed.

   * - 8
     - **Scientific path end to end, no GUI.** Comet 2026.02.2 -> pepXML +
       PIN -> Percolator 3.07.1 with ``-X``/``-Z`` -> PSM, peptide and weights
       plus Percolator XML -> Limelight converter -> Limelight XML, validated
       as XML and non-trivial; plus a Percolator 3.09 run proving rescoring
       works and demonstrably produces no XML. Produces
       ``scripts/feasibility/run_scientific_path.sh`` and
       ``docs/feasibility/scientific-path.rst``. *Depends on units 3, 5, 6.*
     - Gate 2, 3; ``R-PERC-01``, ``R-LL-05``
     - Signed off 2026-08-29 by the phase orchestrator. Read commit
       ``68ec1b6``. **Ran ``bash scripts/feasibility/run_scientific_path.sh``
       end to end myself**: exit 0, every stage asserted. My run reproduced
       6670 PIN rows (3897 target / 2773 decoy), 3.07.1 giving 1026 target
       PSMs and 603 peptides at q<0.01 with an 11-row weights file, and 3.09
       giving the identical 1026/603 with **0 XML files written** and 0 help
       matches for ``xmloutput|decoy-xml-output``; handed ``-X``,
       ``--xmloutput`` or ``-Z`` anyway, 3.09 exits 1 with "is invalid".
       **I then validated the Limelight XML MYSELF**, with my own
       ``javax.xml.validation`` program and the ``limelight-xml.xsd`` I
       extracted from the converter JAR by hand: 12797113 bytes, 3897
       ``<psm``, 2985 ``<reported_peptide``, 2747 ``<matched_protein``,
       ``errors=0 fatals=0``. **And I proved my own validator can fail**: a
       truncated copy gave ``FATAL: XML document structures must start and
       end within the same entity``, and a copy with one element renamed gave
       ``cvc-complex-type.2.4.a: Invalid content was found starting with
       element 'bogus_element_orchestrator'``. Gate items 2 and 3 PASS.
       Nothing from the run was committed -- only the script, the document
       and ``comet.params``.

   * - 9
     - **PDV CLI and converter interface capture.** Run the pinned Limelight
       converter JAR's ``--help`` and record its real argument names against
       the specification's table; download PDV 2.7.0 and prove CLI figure
       generation on the Comet pepXML plus spectrum file from unit 8.
       Produces ``docs/feasibility/pdv-converter-spike.rst``. *Depends on
       units 5 and 8.*
     - In-scope bullets "Limelight converter JAR help" and "PDV CLI figure
       generation"; ``D-005`` evidence
     - PENDING

   * - 10
     - **Does the ``noxml`` build emit Percolator XML? A per-release,
       per-platform sweep.** Created mid-phase, after the orchestrator
       personally verified that the 3.07.1 Linux ``noxml`` binary emits
       ``percolator_out`` XML under ``-X`` byte-identically to the
       XML-capable build. Establishes which releases and platforms this holds
       for, whether the XML validates against the XSD, whether ``-Z`` works on
       a ``noxml`` build, and what it does to ``D-002``/``D-003``/``D-004``.
       Produces ``docs/feasibility/noxml-capability.rst``.
     - Gate 7, 10; ``R-PERC-02`` capability probe design
     - Signed off 2026-08-29 by the phase orchestrator. Read commits
       ``7d2c214`` and ``c569cf9``. This unit exists because I personally
       verified the trigger first: the 3.07.1 Linux ``noxml`` binary
       (2182688 B, ``Build Date Jun 20 2024 13:20:18``) run as ``-X`` on unit
       4's PIN exits 0 and writes 143848 bytes of
       ``percolator_out/15`` XML with 200 ``<psm>`` and 200 ``<peptide>``,
       and ``diff`` against the XML-capable build's output with
       ``<command_line>`` masked reports **no difference**. The sweep then
       covered 31 binaries from 29 artefacts across rel-3-05..rel-3-09. Two
       independent units reached the converter-acceptance answer separately
       (unit 8 through the real Comet pepXML, unit 10 through the sweep) and
       agree. I re-ran the full ``check-docs.sh``: 12 HTML pages, clean.
       ``D-002``/``D-003``/``D-004`` presented as costed options and **not
       answered** -- correct. The finding is escalated below.


Rejections and rework
=====================

None yet.

Deferred
========

Nothing yet.

Blockers escalated
==================

Nothing yet.
