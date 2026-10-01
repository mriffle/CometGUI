.. _dev-comet-parameter-schema:

======================
Comet parameter schema
======================

.. note::

   **Status: in progress, Phase 06.** Only the real-binary fixtures and the
   tests that prove them have landed (unit 1). The schema, parser, writer,
   validation, presets and migration are still to come, as is the generator
   behind :doc:`../reference/comet_parameters_generated` (``R-DOC-04``). Until
   they land, the sections below other than *Fixtures* describe intent, not the
   product.

What this page will cover
=========================

How the typed, versioned parameter schema is built from Comet's own ``-q``
output plus curated metadata, how it is parsed, written, validated and
migrated, and how the drift test catches a schema that no longer matches the
binary.

.. _dev-comet-parameter-fixtures:

Fixtures
========

Every test of the parameter model reads the parameter files Comet itself
writes, never a file typed to match what a parser expects (``R-TEST-01``).

What the files are
------------------

::

    cometgui-params-comet/src/test/resources/fixtures/comet/
        <version>/<os>-<arch>/comet-q.params
        <version>/<os>-<arch>/comet-p.params
        <version>/<os>-<arch>/SHA256SUMS

``<version>``, ``<os>`` and ``<arch>`` are spelled exactly as
``manifests/tools.json`` spells them. ``comet-q.params`` is the unmodified
``comet.params.new`` written by ``comet -q`` (the complete file, 118
parameters for 2026.02.2); ``comet-p.params`` is the one written by ``comet
-p`` (the default file, 96). ``SHA256SUMS`` is ``sha256sum`` output for both.
A ``.gitattributes`` in ``fixtures/comet/`` sets ``-text`` so that Git never
converts their line endings on checkout -- ``core.autocrlf=true`` is the
default on a Windows runner and would otherwise change the bytes.

Tests locate fixtures through
``org.cometgui.params.comet.fixtures.CometFixtures`` (test sources) by
version, platform and mode rather than by path.

Today there is one set: **Comet 2026.02.2, linux/x86-64**.

.. list-table::
   :header-rows: 1
   :widths: 22 12 66

   * - File
     - Bytes
     - SHA-256
   * - ``comet-q.params``
     - 11 844
     - ``d15048709f485c09a840dcb2a384dc301b4ba4da4566c2ea053e9c17eae58f51``
   * - ``comet-p.params``
     - 10 214
     - ``b56c967959ae04e2e782411858b2f5be06863e572cf1bfdfeccc7a4b28fbb19c``

The manifest also names Comet 2026.02.2 for linux/aarch64, macos/x86-64,
macos/aarch64 and windows/x86-64. **Their output has never been captured,
because those binaries have never been executed here**; only linux/x86-64
runs on this project's host. The fixture tests print each such row as ``NOT
CAPTURED`` rather than treating it as covered.

How they were produced
----------------------

Captured on 2026-10-01 from the pinned Linux binary, in a private scratch
directory under ``_build/``, from the repository root::

    $ mkdir -p _build/p06-u1/bin _build/p06-u1/q _build/p06-u1/p
    $ cp scratch/phase05/artefacts/v2026.02.2__comet.linux.exe _build/p06-u1/bin/comet
    $ chmod 700 _build/p06-u1/bin/comet
    $ sha256sum _build/p06-u1/bin/comet
    af515b6ed5a17efafff7277a6a9c73cee97e26d38f3c9b2a8da16adaa44e6d9e  _build/p06-u1/bin/comet
    $ B=$(realpath _build/p06-u1/bin/comet)
    $ (cd _build/p06-u1/q && "$B" -q)     # exit 0, prints the banner and
    $ (cd _build/p06-u1/p && "$B" -p)     # "Created:  comet.params.new"

The SHA-256 equals the ``sha256`` of the ``comet``/``linux``/``x86-64`` row of
``manifests/tools.json`` (URL
``https://github.com/UWPR/Comet/releases/download/v2026.02.2/comet.linux.exe``).
Each run used its own **empty** working directory, because Comet writes
``comet.params.new`` into the working directory under either option. The two
files were then copied, unmodified, to ``comet-q.params`` and
``comet-p.params``, and ``SHA256SUMS`` was written with ``sha256sum
comet-p.params comet-q.params``. The ``-q`` output is byte-identical to Phase
00's capture of 2026-08-29.

The first line of either file is ``# comet_version 2026.02 rev. 2
(6edec91)`` -- Comet's own spelling, not the manifest's ``2026.02.2``.

What the build re-proves on every run
-------------------------------------

All in ``cometgui-params-comet``'s test sources, package
``org.cometgui.params.comet.fixtures``:

* ``CometFixtureRealBinaryTest`` (Linux only): for every Comet version in the
  manifest with a linux/x86-64 row, stages the binary from the gitignored
  mirror ``scratch/phase05/artefacts/``, checks its SHA-256 against the
  manifest (read with ``JsonReader``), runs ``-q`` and ``-p`` through
  ``ProcessService`` in empty directories, and requires the written file to
  equal the fixture **byte for byte**. A missing mirror binary, a checksum
  mismatch, a non-zero exit or a missing ``comet.params.new`` each *fail* with
  a diagnostic; none of them skips. So a fixture edited by hand fails the
  build even if its ``SHA256SUMS`` is regenerated to match.
* ``FixtureMatrixTest`` (every platform): every distinct Comet ``version`` in
  ``manifests/tools.json`` has a ``linux-x86-64`` directory with both files
  and a ``SHA256SUMS`` that lists both and verifies. A manifest version with no
  fixtures fails the build.
* ``ParameterDumpFactsTest`` (every platform): the ``R-PARAM-01`` facts --
  ``-q`` declares 118 parameters, ``-p`` 96, ``-p`` declares nothing ``-q``
  does not, and the difference is exactly the 22 names the specification
  lists, typed into the test from the specification rather than derived from
  the fixtures; the ``-q`` file starts with the ``# comet_version`` marker and
  ends with the ``[COMET_ENZYME_INFO]`` table. Declarations are counted with a
  line rule (a name at column one, optional blanks, ``=``), not a parser.

Adding a new Comet version
--------------------------

#. Add its rows to ``manifests/tools.json`` (the installer's work, not this
   page's). From that commit on, ``FixtureMatrixTest`` fails until step 4 is
   done -- by design.
#. Put the linux/x86-64 binary in the mirror as
   ``scratch/phase05/artefacts/<releaseTag>__<file name from its URL>`` and
   check ``sha256sum`` against the manifest row.
#. Run it with ``-q`` and with ``-p``, each in its own empty directory, exactly
   as above.
#. Copy each ``comet.params.new`` unmodified to
   ``fixtures/comet/<version>/linux-x86-64/comet-q.params`` and
   ``comet-p.params``, and write ``SHA256SUMS`` there with ``sha256sum
   comet-p.params comet-q.params``. Confirm with ``git diff --stat`` and
   ``git check-attr -a`` that Git sees them as ``text: unset`` and stores them
   unchanged.
#. Run ``mvn -B -o -pl cometgui-params-comet -am verify``. The real-binary
   test must pass for the new version. ``ParameterDumpFactsTest`` asserts the
   2026.02.2 facts only; a new version's counts are a new fact, established by
   running it and checked against the specification, not copied from the
   file.

Do not add fixtures for a platform whose binary was not executed. Its row
stays reported as never captured.
