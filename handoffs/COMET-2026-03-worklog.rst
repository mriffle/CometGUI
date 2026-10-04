=====================================================
COMET-2026-03 work log -- Comet 2026.03.0 intake
=====================================================

:Work package: Comet 2026.03.0 intake (``handoffs/COMET-2026-03-BRIEF.rst``)
:Orchestrator: tier-2 work-package orchestrator, dispatched by tier 1
   session 10 at ``d19b232``
:Started: 2026-10-04

Maintained by the work-package orchestrator as the package runs. A unit is not
done until it carries a sign-off entry naming what was run and what was
observed -- "agent reported success" is not a sign-off.

.. contents:: Contents
   :depth: 1
   :local:

Starting state
==============

``git status`` clean at ``d19b232`` on ``main``. **No baseline build was
taken**, by the owner's build-economy rule (brief, *Build economy*).

Facts re-established by the orchestrator, before decomposing
-------------------------------------------------------------

Run by me on 2026-10-04 in a private scratchpad directory (not in the tree).
These are my own independent capture, against which each unit's work is
checked; they are not a substitute for the unit's own capture.

* **Downloads.** All ten binary assets of ``v2026.03.0`` fetched with ``curl
  -sSfL`` from ``https://github.com/UWPR/Comet/releases/download/v2026.03.0/``.
  Every size and SHA-256 equals the ``digest`` the GitHub releases API
  publishes for the asset, e.g. ``comet.linux.exe`` 7 077 008 B,
  ``ad93b4cf60c2ed7afc2f41b8a3a05567c3938d999f17deb7b3676bc1fa91e7ed``;
  ``CometWrapperCore.dll`` 4 501 504 B; ``Ijwhost.dll`` 129 800 B. The two
  ``ThermoFisher.CommonCore.*.dll`` are byte-identical in size to 2026.02.2's
  (406 016 and 650 752 B); ``CometWrapper.dll`` changed (4 411 392 ->
  4 494 336 B).
* **Linkage.** ``readelf -d comet.linux.exe``: "There is no dynamic section"
  -- statically linked, as 2026.02.2.
* **Banner.** ``comet -q`` and ``comet -p`` each exit 0 and print ``Comet
  version "2026.03 rev. 0 (fa08489)"`` and ``Created:  comet.params.new``.
* **Counts.** Lines matching ``^[A-Za-z0-9_]* *=``: ``-q`` **118**
  (12 551 B), ``-p`` **95** (10 256 B). The ``-q`` name set is **identical** to
  2026.02.2's. ``-p`` omits 23 names: 2026.02.2's 22 plus
  ``index_search_type``, which the release notes say ``-p`` no longer writes.
* **Default and comment changes, ``-q`` 2026.02.2 -> 2026.03.0** (whole-file
  ``diff``): the marker line; ``decoy_search``'s inline comment; the
  ``index_search_type`` block comment, its inline comment, and its default
  **``1`` -> ``-1``**; five new comment lines above the variable modifications
  documenting ``^``, ``$``, ``-2`` and the terminus codes. No other line.
* **Rule probes**, ``comet -P<file>`` on a copy of each version's own ``-q``
  file with one line changed. With ``missing.mzML`` as input (parameter load
  only): 2026.03.0 rejects ``search_enzyme_number = 99`` with ``Error -
  search_enzyme_number 99 is missing definition in params file.``; 2026.02.2
  reaches the input. No other probed case is decided at parameter load. With a
  real search (``D-006`` local fixture ``..._K562_3.mzML``, CRLF-repaired copy;
  UniProt human FASTA; ``spectral_library_name`` emptied, because ``-q``'s
  default ``/some/path/speclib.file`` makes Comet stop with ``Error (5) -
  cannot read spectral library file``; ``scan_range = 11188 11192``):

  - ``variable_mod01 = 15.9949 M 0 3 -3 0 0 0.0`` and ``... 2 4 0 0.0``:
    2026.03.0 stops, rc 1, ``Error - variable_mod01 (M): invalid
    term_distance/which_term "-3 0"; term_distance must be -2, -1 or >= 0, and
    which_term 0-3 ...``; 2026.02.2 completes, rc 0.
  - ``^`` and ``$`` in the residue field: both versions complete, rc 0, with
    no message. **2026.02.2 does not refuse them**; it is silent.
  - ``index_search_type = 1`` with a FASTA: 2026.03.0 warns ``Warning -
    index_search_type = 1 is ignored: "<fasta>" is not an .idx file ...``;
    ``-1`` is silent on both.
  - ``variable_mod10`` active with ``print_ascorepro_score`` ``1`` or ``-1``:
    **both versions complete, rc 0, no message** -- *but see the correction
    below: this probe was wrong.*

  - 2026.03.0 no longer warns ``invalid parameter found:
    spectral_library_ms_level`` (2026.02.2 does).

* ``-q``'s default ``print_ascorepro_score`` is ``1`` in both versions.

**Correction, same day, by me.** My slot-10 probe used
``15.9949 M 0 3 -1 0 0 0.0``, identical to slot 1, and Comet **merges** a slot
identical to a lower one before any check (``CometSearchManager.cpp``
L1483-1512 at ``v2026.03.0``, commit ``fa08489``), so slot 10 was no longer
active. The check itself is L1577-1603. Re-probed with
``variable_mod10 = 79.966331 STY 0 3 -1 0 0 0.0``: with
``print_ascorepro_score = 1`` 2026.03.0 stops, rc 1, ``Error -
print_ascorepro_score is enabled but variable_mod10 is active; AScorePro
localization is only supported for variable_mod01 through variable_mod09.
...``; with ``0`` it completes; 2026.02.2 completes in both. The same STY slot
in ``variable_mod09`` with ``-1`` completes. The release note is
**confirmed**, with the merge rule as a condition the validator must model.

Coupling that fixes the order of units
---------------------------------------

Adding a 2026.03.0 Comet row to ``manifests/tools.json`` makes, at once,
``FixtureMatrixTest`` fail (no fixture), ``SchemaDriftFixtureTest`` fail (no
version record) and the documentation build fail (``scripts/cometparams.py``
refuses an installed version with no record). And the version record cannot
be drift-clean today: ``index_search_type``'s 2026.03.0 default ``-1`` is not
one of its curated choices, and choices are not versioned. So the
**schema comes first and the registry second**.

Engineering decisions
=====================

C-1. Serial, one fresh agent per unit
    Owner's standing rule. Every unit below writes the module or the
    metadata the next one reads; no argument for parallelism exists.

C-2. Version-scoped facts are DATA in the metadata, keyed by version record
    Phase 06 already keys defaults and the tuple layout on the version record.
    Every 2026.03.0 difference -- choices, inline comments, help, the residue
    alphabet, rule severities -- is added the same way, never as an
    ``if (version ...)`` in a codec or a rule. A rule that needs a version fact
    reads it from the version the model carries.

C-3. The mirror is Phase 05's
    Binaries go to ``scratch/phase05/artefacts/<releaseTag>__<asset>`` as every
    existing real-binary test expects.

C-4. Unit sign-off method
    Per ``ONBOARDING.rst`` *Sign-off* and the brief: read the whole diff;
    ``mvn -B -o -pl <module> -am verify``; targeted PIT on the changed classes
    (``mvn -B -o -pl <module> -am test-compile
    org.pitest:pitest-maven:mutationCoverage -DtargetClasses=...``), reading
    ``mutations.xml``; at least two production-code injections of my own, one
    making a rule version-blind, each proved landed (anchor once, marker
    grepped, class hash changed) and restored with ``sha256sum -c``;
    ``scripts/ci/docs-build.sh`` if ``.rst`` changed; ``bash
    scripts/verify-all-gates.sh --only NAME`` for every harness that reads what
    changed. Never ``scripts/build.sh`` or the full ``verify-all-gates.sh``.

Work units
==========

.. list-table::
   :header-rows: 1
   :widths: 5 50 15 30

   * - #
     - Unit and acceptance conditions
     - Gate items
     - Sign-off

   * - 1
     - **Fixtures and the version-scoped schema.** Mirror
       ``v2026.03.0__comet.linux.exe``; capture real ``-q``/``-p`` output into
       ``fixtures/comet/2026.03.0/linux-x86-64/`` with ``SHA256SUMS`` and the
       recipe; 2026.02.2 and 2024.01.0 fixtures byte-unchanged. A 2026.03.0
       version record (marker ``2026.03 rev. 0 (fa08489)``, tuple layout,
       default overrides). Per-version overrides extended beyond defaults
       (choices, inline comment, help, help URL) as data, with
       ``MetadataLoader`` and ``cometparams.py`` refusals. 2026.03.0 drift
       clean: 118 declared / 118 modelled / 0 allow-listed; ``-p`` 95 as
       ``PARTIAL_DISCOVERY``. The drift test runs for every version that has
       fixtures as well as every manifest version, and goes red on an entry
       removed and on a parameter claimed for a version whose ``-q`` lacks it.
       ``verify-test-gates.sh``'s precondition covers
       ``scratch/phase06/artefacts``.
     - 2, 3 (half), brief residue
     - :ref:`c2603-u1-signoff`

   * - 2
     - **Registry.** ``manifests/tools.json`` rows for 2026.03.0 on all five
       platforms, URL/size/SHA-256 (and MD5) from bytes downloaded; Windows
       companions decided from the installer's companion rules, with
       ``CometWrapperCore.dll``/``Ijwhost.dll`` recorded either way and why.
       2026.03.0 the default (newest) and 2026.02.2 still offered. The real
       installer installs 2026.03.0 on Linux x86-64 from upstream, verifies
       it, and the probe reads ``2026.03.0`` from the banner; a corrupted
       2026.03.0 hash is refused. Real-binary fixture test now proves the
       2026.03.0 fixture bytes. ``verify-install-gates.sh`` passes at a floor
       >= 88. Tool tables and the parameter reference document 2026.03.0.
     - 1, 7 (half)
     - :ref:`c2603-u2-signoff`

   * - 3
     - **Values and round trip for 2026.03.0.** ``^`` and ``$`` legal in the
       residue field for 2026.03.0 only (alphabet from the version record),
       refused for 2026.02.2 and 2024.01.0. Phase 06 gate items 1, 3, 4, 5, 6
       hold for 2026.03.0: byte-stable double round trip of its real ``-q``;
       every tuple form, plus ``^``/``$`` forms, in all fifteen slots; enzyme
       table; comma-decimal locale; unknown parameter preserved. The real
       2026.03.0 binary reads the canonical file.
     - 4
     - :ref:`c2603-u3-signoff`

   * - 4
     - **Version-scoped validation, agreed with the binary.** For each rule
       change (``^``/``$``, distance below -2, terminus outside 0-3 with a
       distance, AScorePro with slots 10-15, ``index_search_type``, undefined
       enzyme numbers, ``add_U_selenocysteine``, the spectral-library MS level
       name), a corpus of parameter files judged by both real binaries, each
       command and output recorded; the validator, given each version, agrees
       on every case; cases the binary cannot settle are named, not inferred.
       A real-binary test replays the corpus.
     - 5
     - :ref:`c2603-u4-signoff`

   * - 5
     - **Migration to 2026.03.0.** The 2026.02.2 and 2024.01.0 fixtures migrate
       to 2026.03.0 with a reviewable diff; ``index_search_type = 1`` is
       reported, not silently carried or dropped; position-field values
       2026.03.0 rejects migrate visibly. A migrated file is accepted by the
       real 2026.03.0 binary.
     - 6
     - :ref:`c2603-u5-signoff`

   * - 6
     - **Falsifiability and mutation.** ``scripts/verify-param-gates.sh``
       gains additive controls for this package's items, including at least
       one version-blind injection per version-scoped rule family; floor
       raised to the measured count. PIT over the classes changed by units
       1-5, every survivor read; none makes a version-scoped rule
       version-blind. Developer page complete.
     - 8, falsifiability of 3-6
     - :ref:`c2603-u6-signoff`

Sign-off entries
================

.. _c2603-u1-signoff:

Unit 1
------

**ACCEPTED 2026-10-04 at ``f375c87``, no rework.** One fresh agent; commits
``72f0ed5`` (fixtures, test-gate precondition), ``65e97ce`` (overrides, the
2026.03.0 record, tests, generator), ``88ed18b`` (developer page), ``f375c87``
(self-test control exit code). 27 paths, all inside its brief; no edit to
``manifests/tools.json``.

What I ran and saw:

* **Read the whole production diff**: ``ParameterOverride`` (new record: name,
  source, optional default / choices / shortHelp / helpUrl, and an inline
  comment with its own "replaced" flag so a release can have *no* comment);
  ``CometVersionRecord.defaults`` -> ``overrides`` with ``defaults()`` kept as
  a view; ``CuratedMetadata.forVersion`` applies the version's override;
  ``MetadataLoader`` reads ``overrides`` with every replaced field held to the
  curated field's rule, required to differ from it, and the version's
  resulting default required to be one of its resulting choices. No
  ``if (version ...)`` anywhere (C-2). The 2026.03.0 record overrides
  ``index_search_type`` (default ``-1``, choices ``-1``/``0``/``1``),
  ``decoy_search`` (comment), ``spectral_library_ms_level`` and
  ``add_U_selenocysteine`` (comment, help, reference), each with an
  ``https://`` source at the tag. ``scripts/verify-test-gates.sh``'s new
  precondition is additive.
* **Fixtures against my own independent capture** (*Starting state*): ``git
  show HEAD:<fixture> | cmp -`` identical for both ``comet-q.params`` and
  ``comet-p.params``; ``sha256sum -c SHA256SUMS`` OK for 2026.03.0 and
  2026.02.2; ``git diff --stat`` empty over the 2026.02.2 and 2024.01.0
  fixtures; ``git check-attr``: ``text: unset``. Mirror
  ``scratch/phase05/artefacts/v2026.03.0__comet.linux.exe`` sha256
  ``ad93b4cf...91e7ed``.
* ``mvn -B -o -pl cometgui-params-comet -am verify`` -> ``BUILD SUCCESS``,
  2m53s; ``Tests run: 1032, Failures: 0, Errors: 0, Skipped: 0`` (979 before).
* **PIT**, ``test-compile org.pitest:pitest-maven:mutationCoverage
  -DfailWhenNoMutations=false -DtargetClasses=`` the four changed classes:
  **192/192 killed** -- ``ParameterOverride`` 17, ``CometVersionRecord`` 7,
  ``CuratedMetadata`` 24, ``MetadataLoader`` 144; none surviving.
  (``-DfailWhenNoMutations=false`` is needed because ``-am`` runs PIT in the
  upstream modules, which have none of the target classes.)
* **Injection 1, version-blind** (``CuratedMetadata.forVersion`` reads the
  *first* version record -- 2026.03.0 -- whatever version is asked). My first
  form was stopped by **Spotless** before compiling and the class hash did not
  change, so it gave no verdict; re-injected in formatter-clean layout: class
  ``2b848f36`` -> ``0bdfc99e``; **28 failures** in 10 classes, among them
  ``Comet202603CurationTest.minusOneIsNotA202602Choice:246 expected: <1> but
  was: <0>`` (``-1`` became legal for 2026.02.2) and
  ``indexSearchTypeIsVersionScoped:142 expected: <1> but was: <-1>``.
  Restored, ``sha256sum -c`` OK.
* **Injection 2** (``MetadataLoader`` no longer detects a parameter overridden
  twice): class ``a64ee876`` -> ``ccbd2368``; 2 failures --
  ``VersionDefaultsLoaderTest.aNameOverriddenTwiceIsRejected ... Expected
  ...InvalidMetadataException to be thrown, but nothing was thrown.`` and the
  same in ``VersionOverridesLoaderTest``. Restored, ``sha256sum -c`` OK;
  ``git status`` clean.
* ``scripts/ci/docs-build.sh`` PASSED (count line ``118 parameter entries =
  118 modelled parameters ... for Comet 2026.02.2`` -- the manifest still
  names only 2026.02.2, as briefed); ``--self-test`` PASSED.
* ``bash scripts/verify-all-gates.sh --only docs --only traceability --only
  params`` -> ``3 control(s) passed, 0 failed, in 308 seconds``; params ``68
  controls`` (floor 68, unchanged).
* **Precondition**: with ``scratch/phase06/artefacts`` renamed away,
  ``verify-test-gates.sh`` stops at once with ``FATAL: scratch/phase06/artefacts
  does not exist. ...``; renamed back, binary present.
* **Spot-check of an execution claim**: 2026.03.0 with ``index_search_type =
  99`` prints ``Warning - index_search_type = 99 is not -1, 0 or 1; using the
  default (-1, not set).`` -- as the agent reported.
* **Not run, deliberately: ``--only tests``**, although
  ``verify-test-gates.sh`` changed. It is ~50 minutes and builds every module
  in a sandbox, and units 2-5 change what it builds; it is run **once**, on a
  quiet tree, after the last unit (see the handoff). The changed line is a
  precondition and was exercised directly above.

Residue carried forward: the real-binary fixture test is manifest-driven, so
it does not yet re-prove the 2026.03.0 fixture bytes -- unit 2 makes it.
The generator self-test now has 40 cases while ``verify-param-gates.sh``'s
recorded count is 27 -- unit 6 raises it.

.. _c2603-u2-signoff:

Unit 2
------

**ACCEPTED 2026-10-04 at ``667c080``, no rework, two departures from its
brief accepted on evidence (below).** One fresh agent; commits ``bc10bb9``
(rows and tests), ``149bc5c`` (registry docs, harness controls 20-22),
``667c080`` (install floor 88 -> 95). The agent stopped once mid-unit with its
work uncommitted ("waiting for the verify run"; nothing was running); tier 1
flagged it and I resumed the same agent with an instruction to run every check
in the foreground. 17 paths, all in scope. **No production class changed**;
data, tests, docs and harness only.

What I ran and saw:

* **Read the diff.** ``manifests/tools.json``: insertions only (0 removed
  lines), four 2026.03.0 rows at indices 5-8 so every ``artefacts[0..4]``
  reference in tests and harnesses still names the same record. Existing
  tests: every removed assertion is re-expressed, none dropped -- counts that
  grew are re-typed (``6`` -> ``7`` offers, ``23`` -> ``27`` artefacts), and
  ``CometRealBinaryTest`` became per-release with hand-typed counts (``-p`` 96
  for 2026.02.2, 95 for 2026.03.0; ``-q`` 118 for both). Harness: control 5's
  expected text names ``comet-2026_03_0-1`` (the gate-1 UI test now presses the
  default Comet, the same assertion); controls 13 and 16 unchanged in text;
  controls 20-22 new.
* **Rows against my own download** (*Starting state*): size, SHA-256 and MD5
  of all four rows and the three Windows companions equal my independently
  downloaded bytes.
* **Departure 1 -- no macos-x86-64 row.** Verified myself: the first eight
  bytes of ``comet.macos.exe`` **and** ``comet.aarch64.macos.exe`` are ``cf fa
  ed fe 0c 00 00 01`` -- Mach-O 64, CPU type ``0x0100000C``, **ARM64** -- in
  both 2026.03.0 and 2026.02.2; the two 2026.03.0 files differ in 115 bytes.
  Upstream publishes no x86-64 macOS Comet, so writing that row would record a
  false fact. Gate item 1's "every platform the manifest already covers" is
  therefore met for four of five and **cannot** be met for the fifth;
  escalated, with the related finding that the existing **2026.02.2
  macos-x86-64 row describes an arm64 binary**.
* **Departure 2** -- the Windows 2026.03.0 row declares the four Visual C++
  runtime DLLs its import table names, as the Windows Percolator rows do. The
  2026.02.2 Windows row does not, although its binary imports the same:
  escalated, not changed.
* **Companions** -- ``CometWrapper.dll`` and the two ThermoFisher DLLs kept as
  required (R-TOOL-02 names them); ``CometWrapperCore.dll`` and
  ``Ijwhost.dll`` not companions (import table, .NET references,
  ``Comet.vcxproj``; recorded in ``tool_registry.rst``). The agent's finding
  that ``comet.win64.exe`` does not reference ``CometWrapper.dll`` either is
  escalated as a specification question.
* ``mvn -B -o -pl cometgui-install,cometgui-tools,cometgui-params-comet,cometgui-app
  -am verify`` -> ``BUILD SUCCESS``, 6m18s. Surefire: install 1024 (0 failed,
  2 skipped), tools 232 (0, 0), params-comet 1031 (0, 0), app 120 (0, 1). The
  three skips are the opt-in ``-Dcometgui.install.upstream=true`` tests.
* **The real upstream install, run myself**: ``mvn -B -o -pl cometgui-install
  -am test -Dcometgui.install.upstream=true
  -Dtest=CometReleaseInstallTest,UpstreamArtefactTest`` -> rc 0, nothing
  skipped; ``theDefaultCometInstallsFromItsRealUpstreamUrl`` (1.05 s) uses the
  real ``HttpDownloader``, ``ArtefactVerifier``, ``ToolCache`` and a probe over
  the real ``ProcessService``, and asserts SHA-256 ``ad93b4cf...``, 7 077 008
  bytes, identity ``2026.03.0`` and a verifying cache entry.
  ``aCorruptedPinIsRefusedBeforeAnythingRuns`` passed in the same run.
* **Injection 1, version-blind** (``ManagedToolManager.rowsOf`` keeps every
  row whatever its version): class ``3acb52f0`` -> ``b7abb3e0``; ``Tests run:
  1024, Failures: 13`` -- e.g. ``ManagedToolManagerOffersTest
  .aReleaseWithNoArtefactHereIsShownAsUnavailable ... one row expected for
  percolator 3.09: [] ==> expected: <1> but was: <0>``. Restored,
  ``sha256sum -c`` OK.
* **Injection 2** (``VersionBanner``'s Comet pattern reads one digit of the
  release number): class ``6e079c12`` -> ``67da14ff``; 4 failures, 1 error --
  ``VersionBannerTest.cometQuotedBanner:80 expected: <2026.02.2> but was:
  <2026.0.2>`` and ``CometReleaseInstallTest
  .aBinaryThatIsNotThePinnedReleaseIsRefusedByItsBanner``. Restored,
  ``sha256sum -c`` OK; ``git status`` clean.
* **PIT**: no production class changed, so there is nothing to mutate; the
  agent ran ``StagedToolProbe`` 16/16 and ``ArtefactManifest`` 21/21 as extra
  evidence of the new tests' reach. Not re-run.
* ``scripts/ci/docs-build.sh`` PASSED; count line ``118 parameter entries =
  118 modelled parameters, 0 internal, for Comet 2026.02.2, 2026.03.0``.
* ``verify-all-gates.sh --only docs --only traceability`` -> docs 1,
  traceability 8, PASS; ``--only install`` -> ``95 controls in 260s`` (floor
  95); ``--only params`` -> ``68 controls in 255s``.

Findings for later units and for tier 1: ``.idx`` format v5 -- 2026.03.0
rejects an index whose first line does not begin ``Comet index database v5``
(``CometPeptideIndex.cpp`` at ``fa08489``), so a future "selected index is
compatible" check can compare line 1 with the selected version's format. Only
``.github/workflows/macos-gatekeeper.yml`` names a Comet version (a comment,
line 38); its driver selects through ``ArtefactManifest.select`` and will now
pick 2026.03.0 aarch64.

.. _c2603-u3-signoff:

Unit 3
------

**ACCEPTED 2026-10-04 at ``6aadcf1``, no rework.** One fresh agent; commits
``6159488`` (alphabet, codec, round trips, generator) and ``6aadcf1``
(developer page). 25 paths, all in scope.

What I ran and saw:

* **Read the production diff.** Each release's ``variableModTuple`` carries a
  ``residueAlphabet`` (``characters``, ``source``): 2026.03.0 ``A-Z n c ^ $``
  (``CometSearchManager.cpp`` L1538-1563 at ``v2026.03.0``); 2026.02.2 and
  2024.01.0 ``A-Z n c``. ``MetadataLoader`` validates it; ``VariableModCodec``
  is built per release (``forVersion``) and refuses a character outside that
  release's alphabet **on read and on write**, naming the release.
  ``TerminalCode`` gives ``^``/``$`` their words. ``VariableModification`` now
  admits the union of every release's characters -- the per-release law moved
  to the codec. Accepted, with a condition placed on unit 4: a model built in
  code (Phase 07's editor) can hold a ``^`` slot for 2026.02.2 until it is
  written, so validation must also report it at the field.
* ``mvn -B -o -pl cometgui-params-comet -am verify`` -> ``BUILD SUCCESS``;
  ``Tests run: 1731, Failures: 0, Errors: 0, Skipped: 0``;
  ``VariableModRoundTripTest`` alone 917 test cases.
* **PIT** over ``ResidueAlphabet``, ``TerminalCode``, ``VariableModLayout``,
  ``MetadataLoader``, ``VariableModCodec*``, ``VariableModification``:
  **314/314 killed**.
* **Injection 1, version-blind** (``VariableModCodec.forVersion`` takes the
  first -- newest -- release's layout for every version): class ``e3e84a02``
  -> ``35c1b6ab``; ``Tests run: 1731, Failures: 277, Errors: 3``, e.g.
  ``VersionConversionTest.notConvertible:83 expected: <NOT_CONVERTIBLE> but
  was: <SAME>`` and ``RealMigrationTest.aPairedLossNeedsAttention expected:
  <NEEDS_ATTENTION> but was: <CARRIED>``. Restored, ``sha256sum -c`` OK.
* **Injection 2** (the read path checks ``"A"`` instead of the token, so a
  2026.02.2 file with ``^`` would parse): the change is in the inner class
  ``VariableModCodec$Reading``, so the outer class's hash only returned from
  injection 1's build to pristine; 272 failures, 1 error, e.g.
  ``VariableModRoundTripTest ... acetylation of the protein N-terminus, ^ ==>
  Expected ...ValueSyntaxException to be thrown, but nothing was thrown.``
  Restored, ``sha256sum -c`` OK; ``git status`` clean.
* **Execution claim checked independently**: my own 2026.02.2 searches over
  scans 11188-11300 with and without ``variable_mod02 = 42.010565 ^ 0 1 -1 0 0
  0.0`` gave identical ``.txt`` results (517 lines, same SHA-256 of the body)
  -- ``^`` is a silent no-op in 2026.02.2, which is why it stays refused there.
* ``scripts/ci/docs-build.sh`` PASSED. ``--only docs --only traceability``
  -> 2 passed in 56 s; ``--only params`` -> ``68 controls in 271s``.

Residue: ``variable_modNN``'s curated help still says "n and c for termini"
for every release, and the 2026.03.0 inline comment of ``output_txtfile``
still mentions 2026.02.2 -- both carried to unit 4 as metadata fixes in its
scope; harness controls for the 2026.03.0 round trip are unit 6's.

.. _c2603-u4-signoff:

Unit 4
------

**ACCEPTED 2026-10-04 at ``5a25dc1``, after one rework round** (see
*Rejections and rework*). One fresh agent; commits ``c1b89f7`` (rules,
corpus, tests), ``f08ff50`` (generator), ``543fb08`` (developer page),
``5a25dc1`` (rework). 35 paths, all in scope; no spectra or FASTA committed.

What I ran and saw:

* **Read the production diff.** Each version record carries a required
  ``ruleSeverities`` list (rule id, ``ERROR``/``WARNING``/``OFF``, https
  source); ``VersionSeverities`` binds them to the model's release and refuses
  an unknown rule, a rule whose severity is fixed, and a version-scoped rule a
  release does not state -- no default, no ``if (version ...)`` (C-2). Two
  rules are version-scoped: ``variable_mod_tuple.distance_undocumented``
  (2026.03.0 error; 2026.02.2 and 2024.01.0 warning) and the new
  ``index_search_type.ignored_without_idx`` (2026.03.0 warning; older off).
  New fixed-severity rules read release facts: ``variable_mod_tuple
  .residue_not_in_release`` (the alphabet -- meets the condition unit 3's
  sign-off set) and ``variable_mods.ascorepro_slot_unsupported`` (Comet's
  merge of identical slots modelled from ``CometSearchManager.cpp``
  L1475-1512).
* **The one 2026.02.2 catalogue change, reproduced myself**: AScorePro with an
  active slot 10-15 is an **error for 2026.02.2 too**. My own run --
  2026.02.2, full human proteome, scans 11000-12500, ``print_ascorepro_score =
  -1``, only ``variable_mod10 = 15.9949 M 0 3 -1 0 0 0.0`` active -- ended
  ``Segmentation fault (core dumped)``, rc **139**, after ``Post analysis:``;
  the same modification in ``variable_mod02`` completed, rc 0.
* ``mvn -B -o -pl cometgui-params-comet -am verify`` -> ``BUILD SUCCESS``,
  ``Tests run: 1862, Failures: 0, Errors: 0, Skipped: 0``;
  ``ValidationCorpusRealBinaryTest`` 43 and ``ValidationCorpusTest`` 45, none
  skipped.
* **PIT** (my targeted run, real-binary test excluded) over the eleven changed
  classes: **351/352**; the survivor is ``VariableModRules:204`` (Phase 06's
  documented equivalent at what was line 178: the ``> 0`` boundary differs only
  at code 0, which never reaches that branch; message wording only).
* **Injection 1, version-blind** (``Findings`` binds every model to the
  second version record, 2026.02.2): my first form was stopped by Spotless --
  no verdict; re-injected formatter-clean: class ``ab02407c`` -> ``a7f24efb``;
  ``Tests run: 1862, Failures: 12`` -- among them ``ValidationCorpus...
  ist-1, Comet 2026.03.0: ValidationReport[findings=[]] ==> expected: <[WARNING
  index_search_type.ignored_without_idx]> but was: <[]>`` and
  ``dist-minus3, Comet 2026.03.0 ... severity=WARNING``. Restored,
  ``sha256sum -c`` OK.
* **Injection 2** (``AScoreProRule``: ``ascore == 0`` -> ``ascore <= 0``, so
  "localise all" suppresses the error): class ``9b4a2bd2`` -> ``14046953``; 6
  failures -- ``AScoreProRuleTest.slotsAboveNine ... print_ascorepro_score, -1]:
  [] ==> expected: <1> but was: <0>`` for both releases, and the corpus case
  ``ascore-slot10-all, Comet 2026.03.0``. Restored, ``sha256sum -c`` OK.
* **First ``--only params`` run, at ``543fb08``: FAIL** -- ``67 control(s)
  passed, 1 failed``; control 9 ``validation 191/252 mutations killed = 75.7%,
  BELOW the R-TEST-02 threshold of 80%``, about sixty validation mutants
  ``TIMED_OUT``. The agent had reported this harness green. Sent back.
* **After rework, ``--only params`` at ``5a25dc1``: PASS**, ``68 controls in
  294s``; control 9: parser 96/101, writer 30/30, **validation 251/252 =
  99.6 %**, module 1263/1270; only ``VariableModRules:204`` not killed in
  validation; the negative arm still grades validation BELOW (93/252). Module
  verify re-run: 1862, 0 failed, 0 skipped.
* ``scripts/ci/docs-build.sh`` PASSED; ``--only docs --only traceability`` ->
  2 passed in 57 s. The ``verify-test-gates.sh`` precondition now also requires
  ``scratch/fixture`` (additive).

What the corpus establishes (42 cases, both binaries, recorded in
``fixtures/comet-validation/corpus.json`` and the developer page): only the
undefined-enzyme errors fire at parameter load in 2026.03.0; every other
2026.03.0 check fires when a search starts. Cases the binaries cannot settle
here: an existing ``.idx`` whose type disagrees (Phase 08); Comet's count cap
of 5 when building an index, before merging (not modelled -- in that edge case
the validator could report an AScorePro error the binary would not); the
``-i``/``-j`` flags; Windows, macOS and real-time search.

.. _c2603-u5-signoff:

Unit 5
------

**ACCEPTED 2026-10-04 at ``876dc91``, no rework; two judgement calls carried
as residue.** One fresh agent; commits ``7f9e1b1`` (code, data, tests,
migrated files), ``92f3ea6`` (PIT follow-up), ``876dc91`` (developer page). 24
paths, all in scope; no spectra or FASTA committed.

What I ran and saw:

* **Read the diff and the data.** Each version record carries a required
  ``valueMigrations`` list, filed under the **target** release and naming the
  ``from`` release; an entry matches one value of a parameter or a validation
  rule the **source** model's own validation reports there, and has one action
  (``CONVERT`` with ``becomes``/``field``, ``NEEDS_ATTENTION`` or
  ``NOTICE``), a reason and an https source. Into 2026.03.0 from 2026.02.2:
  ``index_search_type`` ``1`` -> ``-1``; ``0`` carried with a notice; a
  distance below -2 -> ``-1``; a terminus outside 0-3 with a distance ->
  NEEDS_ATTENTION. From 2024.01.0: the same two tuple entries. Into 2026.02.2
  from 2026.03.0: ``-1`` -> ``1``. Two new report outcomes, ``CONVERTED`` and
  ``NOTED``. No ``if`` on a version or a parameter name (C-2).
* ``mvn -B -o -pl cometgui-params-comet -am verify`` -> ``BUILD SUCCESS``,
  ``Tests run: 1917, Failures: 0, Errors: 0, Skipped: 0``
  (``MigrationTo202603Test`` 27, ``MigratedFileRealBinaryTest`` 6,
  ``ValueMigrationsLoaderTest`` 15, ``ValueMigrationConversionTest`` 7).
* **The real binary, run myself** on the four committed migrated files
  (``fixtures/comet-migrated/2026.03.0/from-{2026.02.2,2024.01.0}-{q,p}.params``,
  each holding ``index_search_type = -1``) with only the database, scan range
  and an emptied ``spectral_library_name`` changed: 2026.03.0 rc **0** on all
  four, **no Warning or Error line**.
* **PIT** over the six changed classes and their inner types, **no test
  excluded**: **332/332 killed**.
* **Injection A, version-blind** (``CometVersionRecord.valueMigrationsFrom``
  ignores ``from``): class ``df4e1de5`` -> ``fd6a2f4f``; 6 failures, e.g.
  ``MigrationTo202603Test.sameRelease expected: <118> but was: <116>`` and a
  2024.01.0 distance migration explained with 2026.02.2's reason. Restored,
  ``sha256sum -c`` OK.
* **Injection B** (``SchemaMigration`` hands conversion no source findings, so
  rule-keyed entries never apply): class ``4998091f`` -> ``b827eaf5``; 3
  failures -- ``distanceBelowMinusTwo 2026.02.2 ==> expected: <CONVERTED> but
  was: <CARRIED>`` and ``terminusOutsideZeroToThree expected: <NEEDS_ATTENTION>
  but was: <CARRIED>``. Restored, ``sha256sum -c`` OK; ``git status`` clean.
* ``scripts/ci/docs-build.sh`` PASSED; ``--only docs --only traceability`` ->
  2 passed in 58 s; ``--only params`` -> PASS, 68 controls in 317 s; control 9:
  parser 96/101, writer 30/30, validation 251/252, module 1360/1367, migration
  84/84 with nothing timed out.

The four real migrations: 2026.02.2 ``-q`` and ``-p`` -> CONVERTED 1
(``index_search_type``), CARRIED 117; 2024.01.0 ``-q`` and ``-p`` -> ADDED 9,
CARRIED 109. The migrated 2026.02.2 ``-q`` writes byte for byte what 2026.03.0's
own ``-q`` writes canonically.

Residue, for tier 1 (judgement calls, not defects in the gate):

* **Distance below -2 is converted to -1 although one edge case differs.** In
  2026.02.2 and 2024.01.0 the exclusive-modification check switches on only
  when some active slot has a distance of exactly -1; with two exclusive slots
  at -3 the agent measured 7265 result lines against 7263 at -1. No 2026.03.0
  value reproduces the old behaviour; the conversion's reason states the
  exception. Flagging instead would replace the slot with its default.
* **NEEDS_ATTENTION puts the slot's default into the migrated set** (Phase 06
  semantics). For ``variable_mod01`` that default is an active methionine
  oxidation, so a migrated set that is run without review searches a
  modification the user never had. The report says so; Phase 07/08 should
  make an unresolved NEEDS_ATTENTION block a run.
* The preset compatibility check applies only value-matched entries; a preset
  carrying a distance of -3 is caught later by validation.
* Upstream: 2024.01.0 with every variable-modification slot unused stops with
  ``Error in StorePeptides. stored twice``, exit 1 (outside scope).

.. _c2603-u6-signoff:

Unit 6
------

Not yet dispatched.

Rejections and rework
=====================

* **Unit 4, round 1 rejected (2026-10-04).** ``--only params`` failed control
  9 at ``543fb08``: validation 191/252 = 75.7 %. Cause:
  ``ValidationCorpusRealBinaryTest``'s ``@BeforeAll`` ran all 86 real Comet
  searches and its cases then parsed and validated, so PIT mapped every
  validator mutant to it and re-ran the searches per mutant; those mutants
  ended ``TIMED_OUT``, which the gate (rightly) does not count as killed. The
  agent's own PIT excluded the test, and its single harness pass was
  load-dependent -- it had reported the harness green. Fixed at the root, with
  no change to the PIT configuration, the harness or the threshold: the
  real-binary test now checks only binary == recorded verdict, building each
  file by text edits and running no production class of the module; the fast
  ``ValidationCorpusTest`` checks validator == the same recorded verdict.
  Re-signed above.

Deferred
========

None yet.

Blockers escalated
==================

None yet.
