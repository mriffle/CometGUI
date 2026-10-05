.. _user-comet-parameter-presets:

=================
Parameter presets
=================

A **preset** is a named set of changes for a common situation -- here, the
resolution of your instrument. It is not a whole parameter file: it sets only
the parameters it is about and leaves everything else as you have it. And it
never changes anything without showing you first: you see every change it
would make, and you choose all of them, some of them, or none.

Presets are on Essentials, in the *Search preset* group. For the rest of the
parameter editor, see :doc:`comet_parameters`.

.. contents:: Contents
   :depth: 2
   :local:

The built-in presets
====================

Comet's documentation publishes an example parameter file for each of the
three conventional instrument-resolution patterns. CometGUI's three presets are
exactly those files' values, for the eight parameters on which the three files
differ from one another -- so applying any one of them sets every
instrument-resolution parameter, and nothing else.

.. list-table::
   :header-rows: 1
   :widths: 31 23 23 23

   * - Parameter
     - **Low-res precursor, low-res fragments** (``low-low``)
     - **High-res precursor, low-res fragments** (``high-low``)
     - **High-res precursor, high-res fragments** (``high-high``)
   * - Precursor tolerance, upper bound (``peptide_mass_tolerance_upper``)
     - ``3.0``
     - ``20.0``
     - ``20.0``
   * - Precursor tolerance, lower bound (``peptide_mass_tolerance_lower``)
     - ``-3.0``
     - ``-20.0``
     - ``-20.0``
   * - Precursor tolerance units (``peptide_mass_units``)
     - amu (``0``)
     - ppm (``2``)
     - ppm (``2``)
   * - Precursor tolerance applies to (``precursor_tolerance_type``)
     - singly protonated peptide mass, MH+ (``0``)
     - precursor m/z (``1``)
     - precursor m/z (``1``)
   * - Precursor isotope offsets (``isotope_error``)
     - off (``0``)
     - 0, +1, +2 (``2``)
     - 0, +1, +2 (``2``)
   * - Fragment bin width (``fragment_bin_tol``)
     - ``1.0005``
     - ``1.0005``
     - ``0.02``
   * - Fragment bin offset (``fragment_bin_offset``)
     - ``0.4``
     - ``0.4``
     - ``0.0``
   * - Flanking-bin scoring (``theoretical_fragment_ions``)
     - ``1``
     - ``1``
     - ``0``

In words:

* **Low-res precursor, low-res fragments** -- low-resolution MS1 and ion-trap
  MS/MS: a 3 amu window on the singly charged mass, no isotope offsets, and
  roughly 1 Da fragment bins.
* **High-res precursor, low-res fragments** -- high-resolution MS1 with
  ion-trap MS/MS: a 20 ppm window with isotope offsets 0, +1 and +2, and
  roughly 1 Da fragment bins.
* **High-res precursor, high-res fragments** -- high-resolution MS1 and MS/MS:
  the same 20 ppm window, and 0.02 Da fragment bins. These are also Comet's
  own defaults, so on a new configuration this preset changes nothing.

Every value is traced to the line of Comet's example file that gives it, and
the fragment values also to the recommendations on Comet's own parameter
pages; the citations, and two things found in Comet's documentation along the
way, are in :ref:`dev-comet-parameter-builtin-presets`.

The *Instrument setting* choice in the *Fragment ions* group of Essentials
uses the same values for the fragment parameters alone: choosing low- or
high-resolution fragments there sets ``fragment_bin_tol``,
``fragment_bin_offset`` and ``theoretical_fragment_ions`` from the matching
preset at once, without a preview, and they then read ``Value from: Set by a
preset``.

Previewing and applying a preset
================================

#. Choose a preset in **Search/acquisition preset** and press **Preview
   changes...**. Nothing has changed yet, and the editor says so:
   ``Previewing Low-res precursor, low-res fragments: 8 changes. Nothing has
   changed yet.``
#. The preview lists one row per parameter the preset would change, with the
   value you have now and the preset's value, under the heading *Parameter --
   current value -- preset value*. A parameter that already holds the preset's
   value is not listed.
#. Every row has a tick box, ticked to start with. Then:

   * **Apply all** applies every row;
   * **Apply selected** applies exactly the ticked rows, and nothing else;
   * **Cancel** closes the preview and changes nothing -- not a value, and not
     where any value came from.

After applying, the editor lists what changed, for example ``Applied 2 changes
of Low-res precursor, low-res fragments: Precursor tolerance, upper bound
(peptide_mass_tolerance_upper) 10 -> 3.0; Fragment bin width
(fragment_bin_tol) 0.02 -> 1.0005.``, and each applied setting reads ``Value
from: Set by a preset``.

A preview belongs to the configuration it was made for: if the configuration
changes while it is open, applying it is refused and you are asked to preview
again. A row for an output the workflow requires could never be applied, so it
cannot be ticked.

Presets and Comet releases
==========================

Each preset records the Comet release it was made for. The built-in ones were
made for Comet 2026.02.2, whose example files they quote, and the preview says
so whenever your configuration is for another release::

    Low-res precursor, low-res fragments: Made for Comet 2026.02.2; this
    configuration is for Comet 2026.03.0.

Before anything is shown, the preset is checked against the release you are
configuring, and the result is stated under **Compatibility**:

* ``Compatibility: no problems; every change can be applied to this
  release.`` -- the case for all three built-in presets on both releases
  offered today;
* a change the release spells differently is rewritten for it, and listed as
  ``Converted -- ...`` with the reason;
* a change the release cannot take -- a parameter it does not have, or a value
  it cannot hold -- is listed as a problem in words (``Not in this release --
  ...`` or ``This release cannot hold the value -- ...``) and is never offered
  as a row, so it can never be applied.

Presets you do not get (yet)
============================

**No project presets.** The specification suggests a small set of clearly
named project presets besides the instrument ones. None is shipped: every
candidate considered either repeated something the workflow already enforces,
such as the required outputs, or would have set scientific values that Comet's
own documentation does not give. The reasoning is recorded in
:ref:`dev-comet-parameter-builtin-presets`.

**No presets of your own.** Saving your own preset, and applying it later, is
not offered yet. A saved parameter file, imported again, is the way to reuse a
configuration today; see :doc:`comet_parameters`.

**Only from Essentials.** The preset choice and its preview are on the
Essentials level. Expert can *compare* the configuration with the defaults plus
any one preset, but does not apply presets.
