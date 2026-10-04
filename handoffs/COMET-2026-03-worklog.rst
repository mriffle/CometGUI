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

Not yet dispatched.

.. _c2603-u3-signoff:

Unit 3
------

Not yet dispatched.

.. _c2603-u4-signoff:

Unit 4
------

Not yet dispatched.

.. _c2603-u5-signoff:

Unit 5
------

Not yet dispatched.

.. _c2603-u6-signoff:

Unit 6
------

Not yet dispatched.

Rejections and rework
=====================

None yet.

Deferred
========

None yet.

Blockers escalated
==================

None yet.
