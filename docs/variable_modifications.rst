.. _user-variable-modifications:

======================
Variable modifications
======================

A **variable modification** is a mass change a residue or terminus *may or may
not* carry -- oxidised methionine is the usual one -- so Comet scores each
peptide with and without it. Comet has **fifteen slots** for them,
``variable_mod01`` to ``variable_mod15``, and each slot is one line of eight
fields in ``comet.params``::

    variable_mod01 = 15.9949 M 0 3 -1 0 0 0.0

Nobody should have to decode that line, so CometGUI shows each slot as a
sentence and gives every field its own control:

.. code-block:: text

    Variable modification 1: +15.9949 on M; max 3 per peptide; optional
    Serialised: variable_mod01 = 15.9949 M 0 3 -1 0 0 0.0

The editor is on Essentials, in the *Variable modifications* group, and on
Advanced, in the category of the same name. Both are the same editor over the
same configuration. For the rest of the parameter editor, see
:doc:`comet_parameters`.

.. contents:: Contents
   :depth: 2
   :local:

What you see
============

At the top, **Common modification**: a list of ready-made modifications
(`The common modifications`_) and an **Add** button that puts the chosen one
into the first free slot.

Then **every slot of the selected Comet release** -- fifteen in both releases
offered today -- each with:

* its heading, the slot's sentence: ``Variable modification 2: Phospho:
  +79.966331 on STY; max 3 per peptide; optional``. A slot that matches one of
  the common modifications exactly is called by its name; a slot not in use
  reads ``unused (mass difference 0.0)``;
* **Residues and termini**: one tick box per residue letter, A to Z, and one
  per terminus code the release accepts (`Residues and termini`_);
* one box or list per remaining field (`What each field means`_);
* **Move up**, **Move down** and **Remove**;
* **Serialised:**, the exact line that will be written to ``comet.params``,
  updated as you edit;
* where the value came from and its state in words, as for every setting.

And beside the slots, the two settings that change the meaning of every slot
(`The per-peptide limit and the requirement`_), with a line saying whether the
slots agree with them.

Adding, editing, moving and removing
====================================

* **Add** puts the chosen common modification into the first unused slot. If
  all fifteen are in use, nothing is added and the editor says why.
* **Edit** any field of a slot directly. Each change is checked as you make
  it; one Comet would not accept is refused at that slot, with the reason, and
  the slot keeps its previous value.
* **Move up** and **Move down** swap a slot with its neighbour, for when the
  slot number matters to you -- to match an existing file, for example.
* **Remove** empties the slot: it goes back to the release's unused value,
  ``0.0 X 0 3 -1 0 0 0.0``.

What each field means
=====================

In the order Comet writes them. The words in *italics* are what the editor
calls each control.

1. *Mass difference* -- the mass added to the residue, in daltons; negative for
   a loss, such as ``-17.026549`` for pyroglutamate from glutamine. A mass of
   ``0.0`` means the slot is not in use.
2. *Residues* -- the residues and termini that can carry it (see
   `Residues and termini`_). ``STY`` means serine, threonine or tyrosine.
3. *Binary group* -- ``0`` for an ordinary variable modification. Any other
   number puts the slot in a "binary" group: in a peptide, either every
   residue the group names is modified or none is. Used for labelling
   experiments; leave it at ``0`` otherwise.
4. *Minimum count per peptide* and *Maximum count per peptide* -- how many
   residues of one peptide may carry this modification. Usually only the
   maximum is given (``3``); giving a minimum too writes the pair as
   ``min,max`` (``1,3``), and then a peptide must carry at least that many.
5. *Terminal distance* -- restricts the modification to residues near a
   terminus. ``-1``, the usual value, means no restriction; ``0`` means only
   the terminal residue itself; ``2`` means the terminal residue and the next
   two; ``-2`` means anywhere except the peptide's C-terminal residue.
6. *Terminus* -- which terminus that distance counts from, chosen by name:
   protein N-terminus (``0``), protein C-terminus (``1``), peptide N-terminus
   (``2``) or peptide C-terminus (``3``). It matters only when the distance is
   ``0`` or more.
7. *Required, optional or exclusive* -- chosen by name, each with Comet's own
   explanation:

   * **optional** (``0``): peptides are analysed with and without the
     modification;
   * **required** (``1``): only peptides that contain the modification are
     analysed;
   * **exclusive** (``-1``): only one of the set of exclusive modifications
     can appear in a peptide.

8. *Neutral loss* and *Second neutral loss* -- a mass the modified fragment ion
   may also lose, such as ``97.976896`` (phosphoric acid) for a
   phosphorylation; ``0.0`` means none. A second value, written as ``a,b``,
   gives two.

The sentence at the top of the slot is built from the same fields, so it always
says what the serialised line says: ``Gln->pyro-Glu: -17.026549 on Q, only at
the peptide N-terminus; max 1 per peptide; optional``.

Residues and termini
====================

The tick boxes are the residue letters A to Z and the terminus codes. Codes
can be combined with letters in one slot, as Comet allows.

``n`` and ``c`` -- *N-terminus* and *C-terminus*
    Any peptide's N- or C-terminus. Offered on both releases.

``^`` and ``$`` -- *protein N-terminus* and *protein C-terminus*
    Only the N- or C-terminus of the **protein**. **Offered on Comet 2026.03.0
    only.**

Why 2026.02.2 does not offer ``^`` and ``$``: that release accepts the two
characters in a parameter file without complaint and then **never applies
them** -- this project ran it with each and got results identical, byte for
byte, to a search with the slot unused. Offering them there would let you set
up a modification that silently does nothing. On 2026.02.2, protein-terminal
modifications are written the older way: ``n`` with a terminal distance of
``0`` from the protein N-terminus, which is how the common *Acetyl* for that
release is written. An Expert edit that puts ``^`` into a slot on 2026.02.2 is
refused at its line.

Settings Comet refuses, or silently ignores
============================================

Comet 2026.03.0 refuses some slot settings that Comet 2026.02.2 accepted
without a word. The editor checks them at the slot, before you search, as the
selected release would treat them:

* **A terminal distance below** ``-2`` is an error on 2026.03.0, which refuses
  it, and a warning on 2026.02.2, which quietly reads it as ``-1``.
* **A terminus outside** ``0``-``3`` **with a distance of** ``0`` **or more** is
  an error on both: 2026.03.0 refuses it, and 2026.02.2 accepts it and never
  applies the modification.
* **AScorePro scoring with an active slot from** ``variable_mod10`` **to**
  ``variable_mod15`` is an error on both; 2026.02.2 crashes on it.

When a 2026.02.2 configuration is migrated to 2026.03.0, a slot that breaks the
second rule cannot be carried across. It is listed in the migration review as
needing your decision, and it blocks a run until you decide; the reason is
explained in :doc:`comet_parameters`, under *The migration review*.

The common modifications
========================

Each common modification is a complete slot, ready to add. Every mass is the
monoisotopic mass of its **Unimod** record; every other field follows an
example on Comet's own ``variable_modXX`` documentation page or in Comet's own
default parameter file.

.. list-table::
   :header-rows: 1
   :widths: 30 30 40

   * - In the list as
     - Written as
     - Source of the mass
   * - Oxidation, on M
     - ``15.994915 M 0 3 -1 0 0 0.0``
     - Unimod 35, Oxidation
   * - Phospho, on S, T and Y
     - ``79.966331 STY 0 3 -1 0 0 0.0``
     - Unimod 21, Phospho
   * - Acetyl, protein N-terminus (both releases)
     - ``42.010565 n 0 1 0 0 0 0.0``
     - Unimod 1, Acetyl
   * - Acetyl, protein N-terminus (**2026.03.0 only**)
     - ``42.010565 ^ 0 1 -1 0 0 0.0``
     - Unimod 1, Acetyl
   * - Deamidation, on N and Q
     - ``0.984016 NQ 0 3 -1 0 0 0.0``
     - Unimod 7, Deamidated
   * - Gln->pyro-Glu, Q at the peptide N-terminus
     - ``-17.026549 Q 0 1 0 2 0 0.0``
     - Unimod 28, Gln->pyro-Glu

So Comet 2026.03.0 offers six and Comet 2026.02.2 five: a modification is
offered only where the release can hold it. The masses are Unimod's; the
other fields are CometGUI's choices, each based on an example in Comet's
documentation, and two are worth knowing: the Phospho entry carries **no
neutral loss** (Comet's own example adds ``97.976896``; type it into the
*Neutral loss* box if you want it), and on 2026.03.0 both forms of the
protein N-terminal Acetyl are offered. The full citations are in
:ref:`dev-comet-parameter-modification-presets`.

Comet's own default ``variable_mod01`` is ``15.9949 M 0 3 -1 0 0 0.0`` --
oxidation with a rounded mass -- so a new configuration starts with that slot
in use, called by its fields rather than by name.

The per-peptide limit and the requirement
=========================================

Two settings sit beside the slots because they change what every slot means:

*Maximum variable modifications per peptide* (``max_variable_mods_in_peptide``, default ``5``)
    The most variably modified residues one peptide may carry, counting every
    slot together. ``0`` allows none.

*Require a variable modification* (``require_variable_mod``, default off)
    When on, only peptides carrying at least one variable modification are
    analysed.

They are checked against the slots, and the result is stated beside them --
``The slots agree with the per-peptide limit and the requirement.``, or each
problem as an ``Error:`` or ``Warning:`` line. For example, requiring a
modification while no slot is in use is an error, because nothing could be
identified; a slot whose minimum count is above the per-peptide limit is
reported at both the slot and the limit. Like every error, these block a run
and appear in the validation summary.

Seeing the serialised value
===========================

The **Serialised:** line under each slot is exactly the line CometGUI will
write, and it changes as you edit. Expert shows the whole file; a slot can
also be typed there, and is then read back into the slot editor, or refused at
its line if it does not read.
