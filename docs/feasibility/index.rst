=======================================
Phase 00 feasibility evidence
=======================================

:Phase: 00 -- Feasibility, Legal and Upstream Verification
:Status: in progress
:Last updated: 2026-08-29

This directory holds the evidence Phase 00 produces: re-verified upstream
facts, per-platform Percolator artefact findings, toolchain and ``jpackage``
results, the GUI automation verdict, the scripted scientific path, and the
fixture-candidate shortlist. It is evidence, not product documentation --
Phase 01 owns the real Sphinx tree at ``docs/conf.py``, which Phase 00 must not
create.

Every document here must build clean under ``sphinx-build -n -W`` (nitpicky,
warnings treated as errors), per the documentation rule in ``ONBOARDING.rst``.

.. toctree::
   :maxdepth: 1

.. Deliberately empty for now. The build harness auto-discovers every ``.rst``
   in this directory and puts it in a generated toctree, so a document does not
   need to be listed here to be checked. A ``:glob:`` entry was the obvious
   alternative but a glob that matches nothing raises
   ``toctree glob pattern '*' didn't match any documents``, which ``-W`` turns
   into an error while this directory holds only ``index.rst``. Phase 01, which
   owns the real tree, can switch this to a glob once the set is populated.

Checking these documents
========================

``scripts/feasibility/check-docs.sh`` is a throwaway harness that regenerates a
disposable Sphinx source tree under ``_build/docs-check/`` on every run --
never stale, never committed -- and builds it with ``sphinx-build -n -W``::

   bash scripts/feasibility/check-docs.sh                     # all of docs/feasibility/
   bash scripts/feasibility/check-docs.sh path/to/one.rst     # just one file

It discovers documents rather than listing them, so a document added by a later
work unit is checked without editing the script. It exits non-zero when the
build produces any warning, and -- because exit code 0 proves nothing -- it also
verifies that the expected HTML pages exist and are non-empty before reporting
success. HTML lands in ``_build/docs-check/html/``; the sphinx output is kept at
``_build/docs-check/sphinx-build.log``. Both are gitignored.

The harness needs no network access once the virtualenv exists.

Documentation toolchain provenance
==================================

The phase orchestrator folds this into the phase's toolchain manifest; recorded
here so the fact is not lost.

.. list-table::
   :header-rows: 1
   :widths: 22 78

   * - Field
     - Value
   * - Package
     - ``Sphinx``
   * - Version
     - 9.0.4
   * - Source
     - PyPI (``https://pypi.org/project/Sphinx/``), installed with
       ``/workspace/.venv/bin/pip install sphinx``
   * - Install date
     - 2026-08-29
   * - Location
     - ``/workspace/.venv`` -- a project virtualenv created with
       ``python3 -m venv /workspace/.venv`` on the host's Python 3.11.2.
       Gitignored; nothing was installed on the host, and no ``apt``, ``sudo``
       or host-level ``pip`` was used.
   * - Resolved dependencies
     - ``alabaster`` 1.0.0, ``babel`` 2.18.0, ``certifi`` 2026.7.22,
       ``charset-normalizer`` 3.5.1, ``docutils`` 0.22.4, ``idna`` 3.19,
       ``imagesize`` 2.0.1, ``Jinja2`` 3.1.6, ``MarkupSafe`` 3.0.3,
       ``packaging`` 26.3, ``Pygments`` 2.21.0, ``requests`` 2.34.2,
       ``roman-numerals`` 4.1.0, ``snowballstemmer`` 3.1.1,
       ``sphinxcontrib-applehelp`` 2.0.0, ``sphinxcontrib-devhelp`` 2.0.0,
       ``sphinxcontrib-htmlhelp`` 2.1.0, ``sphinxcontrib-jsmath`` 1.0.1,
       ``sphinxcontrib-qthelp`` 2.0.0, ``sphinxcontrib-serializinghtml`` 2.0.0,
       ``urllib3`` 2.7.0
   * - Licence
     - Sphinx is BSD-2-Clause. Not re-verified against the distributed
       artefact by this work unit; the toolchain unit's provenance manifest
       carries licence verification for tools the product ships. Sphinx is a
       build-time documentation tool and is not redistributed with CometGUI.

Recreating the virtualenv from scratch (needs network, once)::

   python3 -m venv /workspace/.venv
   /workspace/.venv/bin/pip install sphinx
