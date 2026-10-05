.. _user-comet-parameters:

================
Comet parameters
================

Comet is configured by a ``comet.params`` file of about 120 settings. The
**Comet Parameters** section is where you set them, without editing that file
by hand: every setting has its own control, with its name in words, its help,
its current value and where that value came from. You can still see -- and, if
you want to, edit -- the file itself.

The short version:

* Start on **Essentials**. It holds the settings a normal search needs, in the
  order you would set them up, and nothing else.
* Every problem is stated **in words, at the setting**, and listed at the top
  of the editor. Anything listed as an *Error* stops a run until it is fixed.
* Saving writes a **new** parameter file. CometGUI never overwrites one.

.. note::

   **This page describes the editor as it is built today.** No search can be
   started yet: the part of CometGUI that runs Comet and Percolator is still to
   come, and the Run section says so. What *is* finished is everything that
   decides whether your parameters would block a run.
   :ref:`user-comet-parameters-limits` lists what the editor does not do yet.

   Variable modifications have their own page, :doc:`variable_modifications`,
   and so do the instrument presets, :doc:`comet_parameter_presets`. Every
   parameter, with its help and its default for each Comet release, is listed
   in the generated reference, :doc:`reference/comet_parameters_generated`.

.. contents:: Contents
   :depth: 2
   :local:

What the editor looks like
==========================

From top to bottom:

#. **Comet release** -- which Comet the configuration is for (see
   `Choosing the Comet release`_), with a line of text saying what is selected
   and whether anything was migrated.
#. **Level** -- *Essentials*, *Advanced* or *Expert*; exactly one is shown.
   Beside it, **Save parameter file...** and **Start again from defaults...**.
#. **Import parameter file...**, and, when a file needs it, the question of how
   to read it (see `Importing a parameter file`_).
#. **The validation summary**: ``Validation: No errors or warnings.``, or the
   counts and one entry per problem (see `Problems, and what stops a run`_).
#. **The migration review**, only while a migrated configuration is being
   reviewed (see `The migration review`_).
#. **Find a parameter**: the search box and its five filters (see
   `Finding a parameter`_).
#. The level you chose.

Every setting, wherever it is shown, says the same four things in text:

* its **name in words, with Comet's own name** -- for example *Allowed missed
  cleavages* (``allowed_missed_cleavage``);
* its **value**, in a control that suits it: a tick box for on/off settings, a
  list of described choices for coded ones (you choose *Semi-specific:
  C-terminus specific, N-terminus unspecific* and never have to remember that
  it is ``num_enzyme_termini = 9``), two boxes for a range,
  a path box with **Choose...** for a file;
* **where the value came from** -- ``Value from: Comet 2026.03.0 default``,
  ``Set by you``, ``Set by a preset``, ``Imported from a parameter file`` or
  ``Required by CometGUI workflow``;
* its **state** -- ``No problems.``, or lines beginning ``Error:`` or
  ``Warning:``.

Its tooltip, which a screen reader also reads, adds the help text, the list of
choices with the value each writes into ``comet.params``, the release's
default, and a link to Comet's own page for that parameter.

A typed value takes effect when you press Enter or leave the box. If Comet
would not accept it, it is **not applied**: the setting keeps its previous
value and says so -- ``Error: not applied, the configuration still holds
"2".`` followed by the reason -- and that refusal blocks a run until you type
something valid or reset the setting.

Choosing the Comet release
==========================

Each Comet release has its own parameters, defaults and rules, so the
configuration always belongs to one release. Two are offered:

* **Comet 2026.03.0**, the default, listed as ``Comet 2026.03.0 (default)``;
* **Comet 2026.02.2**, still supported.

They are the releases CometGUI can install for you, describe parameter by
parameter, and start from Comet's own defaults for. Choosing one changes, for
every setting, the choices offered, the default, the help text and the rules
it is checked against. One visible example: the variable-modification editor
offers the protein-terminus codes ``^`` and ``$`` on 2026.03.0 only (see
:doc:`variable_modifications`).

**Switching release migrates the configuration you have**; it does not throw
it away. Each value is carried across, rewritten where the newer release spells
it differently, and the line beside the selector says what happened, for
example::

    Comet 2026.02.2 is selected. Migrated: Comet 2026.03.0 -> 2026.02.2:
    118 parameters, 1 changed, 117 unchanged; 0 need your decision.

The changes are then listed in the migration review.

The migration review
--------------------

A migration is shown as a report you can read before you search: one row per
parameter that changed, saying what it was, what it is now and why. Most rows
need nothing from you (``No decision needed.``). A row marked **Needs your
decision** is one whose old value the new release cannot hold; CometGUI has put
the new release's default in its place, and that row **blocks a run** --
``Blocks the run until you decide.`` -- until you either

* press **Accept this value** to keep what the configuration now holds, or
* press **Go to the field** and set the value yourself.

Until then it is also an *Error* in the validation summary, at that setting,
and you cannot switch release again.

**Why this blocks rather than warns.** The new release's default is not always
harmless. Comet 2026.02.2 accepted a variable modification restricted to a
terminus it did not recognise and then quietly never applied it; Comet
2026.03.0 refuses such a setting. Migrating one, CometGUI puts 2026.03.0's
default for that slot in its place -- and the default for the first slot,
``variable_mod01``, is ``15.9949 M 0 3 -1 0 0 0.0``: **oxidised methionine,
switched on**. Without the review, a search you set up with that slot doing
nothing would run with a methionine oxidation you never chose. So CometGUI
stops and asks.

Essentials
==========

Essentials is a short, ordered list of tasks, not a filtered view of every
setting. Each group has a heading and one sentence saying what it is for:

**Inputs**
    The spectrum files to search (**Add spectrum files...**) and the protein
    database (``database_name``). Each file shows whether it was found and can
    be read. Spectrum files are inputs to a run, not parameters: they are
    never written into ``comet.params``.

**Search preset**
    A starting point for your instrument: low-, high- or mixed-resolution.
    You see every change it would make before anything changes; see
    :doc:`comet_parameter_presets`.

**Precursor**
    The precursor mass window (lower and upper bound), its units, whether it
    applies to the singly charged mass or to the precursor m/z, and which
    isotope peaks are considered, with the whole setting repeated in one
    sentence -- for example ``Precursor setting: -10 to 10 ppm; applied to:
    Precursor m/z; isotope offsets: ...``. The lower bound is normally
    negative.

**Fragment ions**
    The fragment tolerance as Comet's bin width and offset. **Instrument
    setting** offers them in instrument terms -- low-resolution (ion trap) or
    high-resolution fragments -- with the values of Comet's own example files,
    and shows which one the current values match. Choosing one **changes
    nothing yet**: it opens a preview of what it would change -- one row per
    fragment parameter, with your value and the setting's, each with a tick
    box -- and only **Apply all** or **Apply selected** changes the
    configuration; **Cancel** leaves it exactly as it was. The preview works as
    a preset's does (see :doc:`comet_parameter_presets`).

**Digestion**
    The enzyme, the second enzyme and the sample enzyme, chosen by name; how
    many peptide termini must be enzymatic; how many missed cleavages are
    allowed.

**Static modifications**
    A table with one row per residue and terminus, the four termini first:
    the residue or terminus in words (``lysine (K)``, ``peptide
    N-terminus``), the mass added -- carbamidomethyl cysteine is Comet's
    default, ``57.021464`` on cysteine --, the modification's name, whether
    the mass is the default and where it came from (for example ``Changed from
    default 0.0000 -- Set by you``), a **Reset** back to the default, and
    whether the row has a problem, in words. A mass that is not a number is
    refused at its row, which says so and that the configuration still holds
    the old value. **Comet has no name for a static modification** -- only a
    mass per residue or terminus -- so the name column shows the setting's own
    name, such as ``Static modification: lysine (K)``. The four user-definable
    residues B, J, X and Z are in the same table on Advanced.

**Variable modifications**
    The slot editor, with the per-peptide limit and the "require a
    modification" switch beside it. See :doc:`variable_modifications`.

**Decoys**
    Where the decoys come from -- already in your FASTA, or made by Comet and
    reported together with or separately from the targets -- as one choice in
    words, and the prefix that marks a decoy protein. See :doc:`decoys`.

**Execution**
    The number of search threads; 0 lets Comet use one per processor core.

**Outputs**
    The two result files the workflow needs, shown on and locked (see
    `Outputs the workflow requires`_).

That is enough to configure a normal tryptic DDA search without opening
Advanced or Expert; a test configures exactly such a search this way and
compares the saved file, byte for byte, with one checked by hand.

Advanced
========

Advanced shows **every** parameter of the selected release -- 118 on each --
grouped by what it does rather than where it appears in the file, in fourteen
categories:

#. Database and PEFF
#. CPU and execution
#. Precursor mass and isotope handling
#. Digestion and enzymes -- including the second enzyme, and the **enzyme
   table** (Comet's ``[COMET_ENZYME_INFO]``), where you add a custom enzyme --
   its number, its name, which side of a residue it cuts on, the residues it
   cuts at and those that prevent a cut -- or remove one. Every enzyme choice
   offers exactly the rows of this table, and a configuration naming an
   enzyme the table does not define is an error.
#. Fragment-ion scoring -- including the ion series, one tick box each
#. Fragment-ion and peptide-index search options
#. Spectrum, scan and charge filters
#. Spectral pre-processing
#. Search ranges and peptide constraints
#. Output options
#. MS1 and real-time-search options
#. Static modifications -- the same residue/terminus table as Essentials,
   with the four user-definable residues B, J, X and Z after the others
#. Variable modifications -- the same slot editor as Essentials
#. Miscellaneous and version-specific options -- which holds no parameter in
   either release offered today

Each category opens and closes with its own button, which says how many
parameters it holds. Settings few searches need are marked in their help as
*an expert parameter*; they are still shown, not hidden.

Expert
======

Expert shows the parameter file itself.

**Canonical comet.params (read-only)**
    Exactly the text a saved file holds for the current configuration.

**Draft**
    A copy you can edit. Editing the draft changes nothing by itself. Below
    it, the draft is shown line by line with its syntax colours *and*, beside
    each line, what kind of line it is and any problem with it in words, so
    nothing depends on colour. The problems are also listed as entries you
    can reach with the keyboard; activating one moves the cursor to its line.

**Apply draft...**
    Reads the draft as the selected release and checks it.

    * If it reads, you are shown every change it would make -- ``Applying
      changes 1 value:`` followed by each parameter, old value and new -- and
      **nothing changes until you press** *Confirm: change the
      configuration*. *Do not apply* leaves everything as it was.
    * If it does not read, **nothing changes at all**: every setting keeps
      its value and where it came from, and the offending lines are listed
      with their numbers and text, for example ``Line 27: variable_mod02 =
      42.010565 ^ 0 1 -1 0 0 0.0`` when ``^`` is typed on Comet 2026.02.2.

    *Discard draft* makes the draft the canonical text again.

**Differences**
    The configuration compared with the release's defaults, or with the
    defaults plus one of the presets, and separately with the file you last
    saved.

**Unknown parameters**
    Parameters an imported file contained that the selected release does not
    know. They are kept, shown here with a warning, written back into every
    file you save, and removed only if you press **Remove** beside one.

Finding a parameter
===================

Type in **Find a parameter** and every parameter whose name, name in words,
help text, category or one of its common aliases contains what you typed is
listed -- ``allowed_missed``, ``Enzymatic termini``, ``semi-tryptic`` and
``precursor tolerance`` all work. Matching ignores case and takes what you
typed as one phrase. Each result says *why* it was found::

    Enzymatic termini (num_enzyme_termini) -- Matched by alias "semi-tryptic"

The help text searched is the selected release's: Comet 2026.03.0 describes
some parameters differently from 2026.02.2, and a search finds what the release
you are configuring says.

Five filters narrow the list, and every ticked one must hold:

* **Modified only** -- the value differs from the release's default. (The
  Percolator input output is always listed: the workflow switches it on, which
  is a real change from Comet's default.)
* **Errors only** and **Warnings only** -- the parameter has a problem of that
  kind.
* **Expert parameters** -- the ones few searches need.
* **Unsupported/imported parameters** -- unknown parameters from an imported
  file.

With a filter ticked and no text, every parameter the filters admit is listed.
Activating a result -- Tab to it and press Enter, or click it -- opens the
parameter's category on Advanced and puts the cursor in its field.

Resetting
=========

Three sizes, each back to **the selected release's own default**:

* **Reset**, beside a setting -- that setting only.
* **Reset category...**, on an Advanced category -- every setting in it, after
  you confirm (for example ``Reset 13 parameters to the Comet 2026.03.0
  defaults``).
* **Start again from defaults...** -- the whole configuration, after you
  confirm.

The two outputs the workflow requires stay on through every reset.

Problems, and what stops a run
==============================

CometGUI checks each setting on its own and in combination with others -- a
precursor window whose lower bound is above its upper bound, a modification
required while no slot holds one, an enzyme number missing from the enzyme
table. Every problem is shown three ways:

#. **At the setting**, in words: ``Error: peptide_mass_tolerance_lower = 10 and
   peptide_mass_tolerance_upper = -10: the lower bound is above the upper
   bound, ...``. A problem that involves two settings is shown at both.
#. **In the summary** at the top of the editor, with the counts --
   ``Validation: 1 error and 0 warnings.`` -- and one entry per problem naming
   the setting and its category.
#. **For the keyboard**: each summary entry can be reached with Tab, and
   pressing Enter on it opens the level and category holding the setting and
   moves the cursor there.

No problem is shown by colour alone, and a screen reader hears the same words.

**What blocks a run.** An *Error*, an edit that was not applied, and a
migration entry that needs your decision each block a run, and the Run section
lists every one of them under ``The parameters block a run:``. A *Warning*
does not block a run. (For example, a precursor window that does not include
zero is a warning, not an error: it is unusual but can be deliberate.) A
configuration that would block a run also cannot be saved.

Outputs the workflow requires
=============================

The workflow needs two of Comet's output files:

* the **pepXML** file (``output_pepxmlfile``), which PDV, the spectrum viewer,
  and the Limelight export both read;
* the **Percolator input**, a ``.pin`` file (``output_percolatorfile``), which
  Percolator rescoring reads. Comet's own default for this one is off, so this
  is a real change CometGUI makes for you, and the setting says so.

Both are shown **on and locked**, with the reason as visible text, for example
``Required by CometGUI workflow: Percolator rescoring reads the .pin file``.
They cannot be switched off with the mouse, the keyboard, a reset, a preset or
an edit in Expert, and their origin reads ``Required by CometGUI workflow``.
You may switch on any of Comet's other outputs.

Saving a parameter file
=======================

**Save parameter file...** asks where to save, then writes the configuration
there and reports the file's size and its SHA-256 and MD5 checksums.

* **It never overwrites.** If a file of that name already exists, nothing is
  written and you are asked to choose another name.
* **It is refused while anything would block a run**, with every reason listed.
  A saved file is always one Comet can be given.
* **The saved file is the canonical form** -- exactly what Expert shows as the
  canonical text: a first line naming the Comet release, a second naming the
  CometGUI version that wrote it, every parameter in a fixed order with Comet's
  own short comment, numbers written the same way on every computer whatever
  its language settings, and any unknown parameters you kept. Your own comments
  from an imported file are not carried over, except those belonging to unknown
  parameters.

Importing a parameter file
==========================

**Import parameter file...** reads an existing ``comet.params`` into the
editor; what was imported is marked ``Imported from a parameter file``. A file
that cannot be read changes nothing, and every problem is listed with its line.

The first line of a Comet parameter file names the Comet release that wrote
it. If that is not the release selected in the editor, CometGUI does not guess:
it names both releases and waits for you to choose --

* **Migrate it to the selected release**, with the reviewable report above;
* **Switch to Comet 2026.02.2** (the file's own release), when that release
  is one CometGUI offers;
* **Read it as the selected release**, as it is -- the mismatch then stays
  listed as a warning naming both releases;
* **Import nothing**.

Until you choose, the configuration you had is untouched.

.. _user-comet-parameters-limits:

What the editor does not do yet
===============================

Stated plainly, so that you do not have to find them by trying.

**No search can be started.** The Run control is disabled and says why: the
part of CometGUI that runs Comet and Percolator comes in a later phase. What
the Run section shows today is whether *your parameters* would block a run.

**There is no comparison with a previous run** in Expert, because there are no
runs yet; the comparison with the last saved file is there.

**The outputs are always locked.** The pepXML and PIN outputs would be
unlocked if the stage needing them were switched off, but no stage can be
switched off yet, so both are locked on in every configuration.

**Presets are applied from Essentials only**, and there are no presets of your
own yet; see :doc:`comet_parameter_presets`.

**The spectrum and database files are only checked to exist and be readable.**
Checking their formats, and counting the decoys already in the FASTA, belong to
the workflow and arrive with it.

**The file choosers themselves are not exercised by the automated tests**,
which answer the "choose a file" question directly; everything after the
choice is.
