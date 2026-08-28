# CometGUI: Comet + Percolator Desktop Workflow

# Implementation Specification

:Status: Implementation-ready design specification
:Date: 2026-08-28
:Target application: Cross-platform Java desktop application
:Primary source base: Noble-Lab CasanovoGUI
:Primary search engine: Comet
:Primary post-processor: Percolator
:Visualization: PDV
:Downstream integration: Limelight
:Documentation format: reStructuredText / Sphinx / Read the Docs

## Executive Summary

CometGUI shall be a cross-platform desktop application for configuring and
running a complete Comet -> Percolator proteomics workflow without requiring
the user to install Java, Comet, Percolator, PDV, the Limelight converter, or
any other command-line dependency manually.

The application shall be derived from the CasanovoGUI code base and shall
preserve the strongest parts of that application: a modern JavaFX desktop
shell, bundled Java runtime, managed tool installation, live process output,
PDV integration patterns, Limelight upload patterns, and cross-platform
packaging. The Casanovo-specific workflow layer shall be replaced by a general
scientific workflow/tool-adapter architecture suitable for Comet and
Percolator.

The primary difficulty in this project is not invoking Comet. It is creating a
safe, understandable, version-aware graphical editor for Comet's large and
interdependent parameter space. The application shall therefore *not* expose
`comet.params` as a flat collection of text boxes. It shall maintain a typed,
versioned Comet parameter model and present parameters using progressive
disclosure, domain-oriented groups, dedicated controls for structured values,
version-matched help, cross-parameter validation, presets, search, reset/diff
operations, and an Expert raw-parameter view that round-trips without silently
dropping unknown settings.

As of the date of this specification, the current Comet release is
2026.02.2. The current Percolator release is 3.09, but Percolator 3.09 removed
XML/XSD I/O. The required Comet/Percolator-to-Limelight converter consumes
Percolator XML, so the default *full-workflow-compatible* Percolator version
shall be 3.08.0, the latest release immediately preceding removal of XML I/O.
Percolator 3.09 and future versions shall nevertheless be selectable and fully
usable for search rescoring and result viewing when their capabilities permit.
The UI shall explain that Limelight conversion is unavailable for a selected
Percolator that cannot emit XML and shall offer an explicit rerun of the
Percolator stage with a compatible version, without rerunning Comet.

The product shall support user-selectable Percolator versions starting with
3.05. Support shall be capability-driven rather than based only on semantic
version numbers. Managed builds or upstream release binaries shall be tested
against a compatibility matrix. A user may also register a local Percolator
binary >= 3.05; the application shall probe its version and capabilities before
allowing it to run.

Percolator result filtering in the GUI shall default to:

* PSM q-value <= 0.01.
* Peptide q-value <= 0.01.

These are *result-view/export filters*. They shall be independent from
Percolator's `--trainFDR` and `--testFDR` learning/evaluation options,
which shall also default to their conventional 0.01 values but shall be shown
under Advanced Percolator settings. Changing a GUI result filter shall never
rerun Percolator or mutate the original Percolator result files.

Percolator learned model coefficients shall be captured and displayed in a
"Learned feature weights" view. The UI may use "parameter importances" as a
navigation label to match user expectations, but explanatory text shall make
clear that these are learned normalized SVM feature weights, not causal
importance values. The view shall show each cross-validation split, summary
statistics, ranking by mean absolute coefficient, coefficient sign, and
cross-split consistency.

Every workflow run shall produce a provenance record. At minimum, provenance
shall contain every tool name/version, exact executable checksum, exact command
arguments, timestamps, exit status, all generated parameter files, and MD5
checksums for every input and output file. SHA-256 shall be recorded alongside
MD5 and shall be used for download/integrity verification because MD5 alone is
not an adequate security checksum. Provenance shall be viewable in the GUI and
exportable in a machine-readable JSON representation and a human-readable RST
report.

Testing is a first-class deliverable. Unit testing alone is insufficient. The
project shall include a GUI-driven end-to-end harness that starts from a clean
temporary user environment, drives the same controls that a user drives,
generates real Comet parameters from those controls, obtains real tool
binaries, executes real Comet and Percolator processes on real spectra and
FASTA fixtures, verifies real output, verifies q-value filtering and learned
weights, exercises PDV and Limelight conversion, independently recomputes
provenance hashes, closes/reopens the project, and exercises meaningful
failure cases. The actual packaged application shall be tested on supported
operating systems before release.

## Normative Language

The words **shall**, **must**, and **required** describe mandatory behavior.
The word **should** describes behavior expected unless there is a documented
technical reason to deviate. The word **may** describes optional behavior.

## Project Goals

The application shall satisfy the following top-level goals.

1. Make a high-quality Comet + Percolator workflow accessible without manual
   command-line tool installation.
2. Preserve the scientific flexibility of Comet rather than hiding its useful
   parameters.
3. Make configuration safer than editing `comet.params` manually.
4. Support reproducible reruns with exact tool and parameter provenance.
5. Allow Percolator versions >= 3.05 to be selected and managed explicitly.
6. Make version-dependent capabilities visible instead of failing late.
7. Provide first-class PSM and peptide result exploration with independent
   q-value filtering.
8. Make Percolator learned SVM weights inspectable.
9. Provide PDV spectrum visualization.
10. Convert compatible Comet + Percolator results to Limelight XML and upload
    them to Limelight.
11. Provide comprehensive, automated scientific and GUI testing.
12. Package the application so a user can install CometGUI and run it on a
    clean supported system without manually installing its scientific tools.
13. Document all user and developer-facing behavior in RST for Sphinx and Read
    the Docs.

## Non-Goals

The first production release is not required to:

* Reimplement Comet scoring.
* Reimplement Percolator machine learning or FDR estimation.
* Invent a new PSM visualization engine when PDV already supports Comet
  pepXML.
* Modify Percolator output q-values in place.
* Pretend that newer Percolator versions support XML when they do not.
* Silently translate incompatible Percolator output into a made-up XML format.
* Reproduce all possible raw command-line flags as equal-priority controls on
  the primary screen.
* Guarantee byte-identical floating point scores across every operating system
  and every tool version when upstream tools do not guarantee this.
* Automate an external GUI such as PDV with screen-coordinate clicking in
  production code.

## Research-Derived Constraints and Release Assumptions

These assumptions are dated and shall be represented in version/capability
metadata rather than scattered hard-coded conditionals.

CasanovoGUI base

```

The existing CasanovoGUI application provides an appropriate JavaFX desktop
foundation, cross-platform packaging, a bundled Java runtime, managed
first-run installation of scientific software, live process output, PDV launch
support, and Limelight-related integration patterns.

However, the CasanovoGUI repository, as inspected for this specification, does
not expose a license file in its repository root and its Maven POM does not
state a project license. **Before code is copied into or redistributed as a
new product, ownership/licensing permission for the CasanovoGUI source must be
made explicit.** This is a release gate, not a documentation nicety.

The base currently targets Java 23+ and JavaFX 25.x and uses AtlantaFX. The
new project should initially preserve that stack so that the work focuses on
the workflow and parameter editor rather than an unnecessary GUI-framework
migration.

Comet
~~~

The default verified Comet version for the initial implementation shall be
2026.02.2. Tool metadata shall not assume that this remains latest forever.
New Comet releases shall enter the managed registry only after automated
compatibility and regression tests pass.

Comet configuration is versioned. The parameter schema used by the GUI shall
therefore be tied to the selected Comet version. The application shall use the
selected binary itself, where possible, as one source of truth for supported
parameter names and generated defaults, and shall supplement that with
curated metadata for types, value ranges, descriptions, relationships, and UI
presentation.

On current Comet releases, ``comet -p`` produces a normal parameter file and
``comet -q`` can expose a more complete parameter set. Schema drift tests shall
compare GUI metadata with parameters emitted by supported binaries.

Percolator
~~~~~~~~

Percolator 3.09 is the newest release at the date of this specification and
removed XML/XSD I/O. Percolator 3.08.0 supports XML output and shall be the
initial default for the complete Comet -> Percolator -> Limelight workflow.

The application shall support Percolator 3.05 and newer. Older point releases
may contain known behavioral defects. Version metadata shall support advisory
messages and CI shall test representative versions. For example, older 3.06
behavior around peptide protein IDs is a reason to test version-specific
outputs rather than treating all 3.x releases as interchangeable.

Limelight converter
```

The required `limelight-import-comet-percolator` converter consumes:

* the Comet parameter file;
* Comet pepXML output;
* Percolator XML output;
* optionally the FASTA file;
* one optional converter q-value override.

Because the converter has one q-value override, the GUI shall not pretend that
its independent PSM and peptide display filters map one-to-one onto Limelight
conversion. Limelight conversion shall have its own explicitly labeled
"Limelight q-value cutoff", default 0.01.

PDV

```

PDV supports Comet pepXML with MGF, mzML, and mzXML spectrum files and therefore
shall be the supported annotated-spectrum viewer. The current PDV 2.6.0 release
shall be the initial managed version.

PDV's current external control server is documented for its ``denovo-gui``
mode used by CasanovoGUI. A corresponding database-search control mode is not
documented. CometGUI shall therefore distinguish two integration levels:

* baseline: managed PDV installation plus reliable opening/batch visualization
  of Comet pepXML and source spectra using documented PDV database-search
  support;
* enhanced: exact row-to-spectrum selection from CometGUI through a generalized
  PDV database-search launch/control mode, preferably contributed upstream to
  PDV.

The product shall not rely on brittle screen-coordinate automation of PDV.

UX Design Methodology
---------------------

The Comet parameter editor shall be designed using explicit human-computer
interaction methods rather than by mechanically exposing the underlying text
file.

User classes
```

At minimum, design and usability work shall consider these user classes.

Routine proteomics user
Wants a correct search with familiar parameters, common modifications,
tryptic digestion, instrument-appropriate mass tolerances, and clear
results. This user should not need to understand every Comet internal
option.

Advanced search-method developer
Intentionally changes less common fragmentation, indexing, enzyme,
modification, spectral-processing, decoy, and output settings. This user
requires access to the complete parameter space and exact serialization.

Workflow administrator / reproducibility reviewer
Primarily cares about versions, tool installation, provenance, exact
commands, checksums, logs, compatibility, and the ability to reproduce a
prior run.

Primary user tasks

```

The UI shall be optimized around actual tasks, including:

* choose spectra and a sequence database;
* start from an instrument/search preset;
* define precursor and fragment tolerances;
* define digestion;
* define static and variable modifications;
* configure decoy behavior;
* review advanced Comet settings when needed;
* choose a Percolator version;
* run the complete workflow;
* diagnose failure from a specific stage;
* filter PSMs and peptides by q-value;
* inspect learned Percolator feature weights;
* inspect a PSM in PDV;
* convert/upload a result to Limelight;
* inspect/export exact provenance;
* reopen a historical run and know exactly what happened.

Design principles
~~~~~~~~~~~~~~~

The implementation shall apply the following principles.

Progressive disclosure
    Common, high-impact parameters are shown first. Advanced and Expert
    settings remain available without overwhelming the default workflow.

Recognition rather than recall
    Users see units, valid ranges, descriptions, current defaults, preset
    origin, and common choices. They do not need to memorize Comet's numeric
    enum encodings.

Error prevention
    Invalid combinations are blocked before a run where possible. The UI
    explains why a value is invalid and points to the responsible field.

User control and reversibility
    Every parameter category and individual parameter can be reset. Applying a
    preset shows a diff before changing the configuration. Raw results are not
    destructively changed by result filters.

Visibility of system state
    Tool downloads, validation, hashing, Comet, Percolator, conversion, upload,
    and finalization each have explicit states and progress/status indicators.

Consistency
    Similar parameter types use similar controls. Units and serialized values
    are represented consistently across categories.

Version transparency
    When behavior is unavailable because of a selected tool version, the UI
    says so at configuration time rather than allowing a late cryptic process
    failure.

Inline help
    Each parameter has concise help and a link/action to open version-matched
    documentation. Help shall describe the scientific meaning, not merely
    restate the parameter name.

Accessibility
    Every interactive control requires an accessible label/name; validation
    errors must be conveyed in text, not by color alone; keyboard navigation
    and visible focus shall be tested; custom JavaFX controls shall expose
    appropriate accessibility attributes.

UX validation activities
```

Before release, the parameter UI shall undergo:

1. Domain/task analysis with at least one experienced Comet user.
2. A heuristic evaluation against standard usability heuristics.
3. A cognitive walkthrough of the primary workflow.
4. At least one usability test with a routine proteomics user who has not
   implemented the GUI.
5. A usability test with an advanced Comet user using imported/custom
   parameters.
6. Keyboard-only and accessibility review.

Issues found in these sessions shall be tracked like software defects.

## Information Architecture

The primary application window should use a stable left navigation or top-level
workflow navigation rather than proliferating modal dialogs.

Recommended primary sections are:

Run
Inputs, workflow summary, selected tool versions, high-level parameter
summary, validation, and Run/Cancel controls.

Comet Parameters
Typed parameter editor with Essentials, Advanced, and Expert modes.

Percolator
Version selection, result-filter defaults, advanced learning options, and
version capability/advisory information.

Results
Run summary, PSM table, peptide table, learned feature weights, and export.

Visualization
PDV status, selected spectrum/PSM context, and Open in PDV actions.

Limelight
Converter compatibility, converter parameters, generated Limelight XML,
upload configuration, and upload log/status.

Provenance
Tool versions/checksums, file hashes, exact commands, parameter files,
run timeline, environment, warnings, and export.

Console
A persistent or collapsible live console that can filter messages by
workflow stage.

Tool Manager and application Settings may be secondary navigation/dialogs.

The primary Run screen should present the workflow as a stage stepper:

`Inputs -> Validate -> Comet -> Percolator -> Results`

Optional downstream stages should be visibly attached:

`Results -> PDV`

`Results -> Limelight XML -> Limelight Upload`

## Software Architecture

Architectural style

```

The project shall use a layered ports/adapters architecture with a UI-facing
MVVM or Presenter boundary. JavaFX controllers must not contain scientific
process logic, file hashing logic, download logic, or output parsing logic.

The workflow engine and domain logic shall be usable from tests without
launching JavaFX. The JavaFX layer shall translate user actions into domain
commands and observe state.

Recommended package structure
```

::

```
org.cometgui.app
    bootstrap/
    config/

org.cometgui.domain
    project/
    run/
    tools/
    params/
    results/
    provenance/

org.cometgui.workflow
    engine/
    steps/
    state/

org.cometgui.tools
    api/
    comet/
    percolator/
    pdv/
    limelight/
    process/

org.cometgui.install
    registry/
    download/
    verify/
    archive/
    probe/

org.cometgui.params.comet
    schema/
    parser/
    writer/
    validation/
    presets/
    migration/

org.cometgui.params.percolator
    schema/
    validation/

org.cometgui.results
    parser/
    filtering/
    export/

org.cometgui.provenance
    hashing/
    manifest/
    events/
    report/

org.cometgui.ui
    view/
    viewmodel/
    controls/
    dialogs/
```

Key interfaces

```

The exact Java names may change, but the following responsibilities shall
exist.

.. code-block:: java

    public interface ToolAdapter {
        ToolIdentity identify(Path executable) throws ToolException;
        Set<ToolCapability> probeCapabilities(Path executable) throws ToolException;
        ToolCommand buildCommand(ToolExecutionRequest request);
        ToolExecutionResult validateOutputs(ToolExecutionContext context);
    }

.. code-block:: java

    public interface ProcessRunner {
        RunningProcess start(ToolCommand command, ProcessListener listener)
            throws IOException;
    }

.. code-block:: java

    public interface HashService {
        FileHashes hash(Path path) throws IOException;
    }

.. code-block:: java

    public interface ProvenanceRecorder {
        void record(ProvenanceEvent event);
        ProvenanceManifest finalizeManifest(RunOutcome outcome);
    }

.. code-block:: java

    public interface CometParameterSchemaProvider {
        CometParameterSchema schemaFor(CometToolIdentity comet);
    }

Dependencies such as the clock, environment reader, process runner, downloader,
filesystem abstraction where useful, random run-ID source, and hash service
shall be injectable. This is necessary for deterministic tests.

Workflow state model
```

Each workflow step shall use explicit states:

* `NOT_STARTED`
* `VALIDATING`
* `READY`
* `RUNNING`
* `SUCCEEDED`
* `FAILED`
* `CANCEL_REQUESTED`
* `CANCELLED`
* `SKIPPED`

The overall run state shall be derived from step states. State changes shall be
observable by the UI and written to provenance.

Process execution

```

Processes shall be started using argument arrays, never by constructing a
single shell command string. This avoids shell quoting problems and injection.

The process service shall:

* stream stdout and stderr independently;
* timestamp emitted lines/events;
* never block the JavaFX application thread;
* preserve full logs to disk;
* expose exit code and duration;
* support cancellation;
* attempt to terminate descendant processes when cancelling;
* time out only where a stage-specific timeout is explicitly configured;
* redact secrets before provenance/log display where applicable.

Tool Installation and Version Management
----------------------------------------

Zero-manual-install requirement
```

A supported user workflow must begin from a clean machine on which only the
CometGUI installer has been installed/extracted. The application shall bundle
its own Java runtime in native release packages, following the CasanovoGUI
packaging approach.

The application shall install scientific tools into an application-private
cache, for example:

::

```
~/.comet-gui/
    tools/
        comet/
            2026.02.2/
                windows-x64/
                linux-x64/
                macos-arm64/
        percolator/
            3.08.0/
            3.09.0/
        pdv/
            2.6.0/
        limelight-converter/
            <version>/
```

Exact platform names may differ. Tool installs shall not require root/admin
privileges after the application itself is installed.

Managed artifact manifest

```

Tool locations shall come from a versioned, signed or release-bundled manifest,
not ad hoc URL construction spread throughout the code.

Each managed artifact record shall contain at least:

* tool name;
* upstream version;
* release/tag/commit when known;
* operating system;
* architecture;
* download URL;
* archive type;
* expected executable/JAR path;
* expected SHA-256;
* expected MD5, when available or computed during release preparation;
* license metadata;
* required companion files;
* known capabilities;
* known advisories;
* minimum compatible CometGUI version.

SHA-256 verification is mandatory before an executable is launched. MD5 shall
also be computed and recorded for provenance but shall not be the security
trust mechanism.

Installation shall be atomic:

1. Download to a temporary file.
2. Verify expected SHA-256.
3. Extract into a temporary directory with path-traversal and unsafe-symlink
   protections.
4. Verify expected executable/companion layout.
5. Probe version/capabilities.
6. Rename/move atomically into the final cache directory.
7. Record installation metadata.

Interrupted installations shall be safely discarded or resumed without leaving
a tool that appears installed but is incomplete.

Percolator version support
```

The Tool Manager shall show all supported verified Percolator versions >= 3.05
that are available for the user's platform.

The user shall have three installation modes:

Managed verified version
Downloaded and verified by CometGUI from the curated artifact manifest.

Registered local binary
The user selects a local executable. CometGUI probes it, verifies it is
Percolator >= 3.05, computes checksums, and records it as unmanaged/local.

Developer/custom artifact
Optional expert mode. A custom URL or local archive may be registered only
if an expected SHA-256 is supplied. This mode shall be clearly marked
unsupported/unverified unless the compatibility suite has been run.

If official upstream release assets do not exist for every requested
Percolator version/platform pair, the product must choose one of these explicit
strategies:

* publish reproducibly built companion binaries from project CI after license
  review; or
* mark that version/platform combination unavailable as a managed install while
  still permitting a local binary.

The UI must not promise a downloadable build that does not exist.

Capability probing

```

Version strings alone shall not determine all behavior. Each registered tool
shall have a capability set. Example Percolator capabilities include:

* ``XML_OUTPUT``
* ``PSM_TSV_OUTPUT``
* ``PEPTIDE_TSV_OUTPUT``
* ``DECOY_OUTPUT``
* ``WEIGHTS_OUTPUT``
* ``THREAD_OPTION``

Example Comet capabilities include:

* ``PEPXML_OUTPUT``
* ``PIN_OUTPUT``
* ``COMPLETE_PARAMS_QUERY``
* ``THERMO_RAW_WINDOWS``
* ``INDEX_SEARCH``
* ``FRAGMENT_ION_INDEX``
* ``REAL_TIME_SEARCH_OPTIONS``

A capability may be established from verified registry metadata and confirmed
by ``--help``/``--version``/small smoke test. Unknown local binaries should be
probed conservatively.

Percolator compatibility behavior
```

Expected initial behavior is:

.. list-table:: Percolator workflow compatibility
:header-rows: 1
:widths: 18 18 20 44

* * Version family
  * Standard rescoring
  * Percolator XML
  * Limelight behavior
* * 3.05
  * Supported/tested
  * Expected; verify in CI
  * Enabled only after capability probe/test
* * 3.06.x
  * Supported/tested
  * Expected; verify in CI
  * Enabled only after capability probe/test; show advisories for affected releases
* * 3.07.x
  * Supported/tested
  * Expected; verify in CI
  * Enabled after capability probe/test
* * 3.08.0
  * Supported; recommended default
  * Yes
  * Enabled; default complete-workflow version
* * 3.09
  * Supported
  * No
  * Disabled for this run; offer explicit 3.08 rerun
* * Future
  * Capability-dependent
  * Capability-dependent
  * Capability-dependent

Existing projects shall pin exact tool versions. Application updates shall
never silently change the scientific tools used when rerunning a historical
run.

## Comet Parameter Model and Editor

Core rule

```

The Comet parameter editor shall be schema-driven and typed. The UI shall not
be the source of truth. The parameter model shall be serializable to canonical
``comet.params`` text, parsable from existing parameter files, diffable, and
version-aware.

Parameter definition model
```

A parameter definition should contain fields equivalent to:

.. code-block:: java

```
public record ParameterDefinition<T>(
    String name,
    String displayName,
    ParameterCategory category,
    ParameterValueType valueType,
    T defaultValue,
    Optional<T> minValue,
    Optional<T> maxValue,
    List<Choice<T>> choices,
    VisibilityLevel visibility,
    String shortHelp,
    String detailedHelpRef,
    VersionRange supportedVersions,
    SerializationRule serialization,
    List<ValidatorId> validators
) {}
```

A parameter value in a project/run shall also remember its origin:

* Comet default;
* application preset;
* user changed;
* imported from file;
* workflow-enforced.

The UI shall be able to show this origin.

Schema discovery and drift detection

```

For supported Comet binaries that expose complete parameter generation/query,
the build and application shall use that output to verify supported names and
defaults.

Curated schema metadata is still required because the binary output alone does
not provide enough information for a high-quality GUI: scientific descriptions,
value semantics, relationships, enum labels, recommended groupings, and
structured-control behavior must be modeled explicitly.

CI shall contain a schema-drift test:

1. Install each Comet version supported by the release matrix.
2. Ask the binary for its complete parameter set where supported.
3. Parse parameter names and generated defaults.
4. Compare them with the checked-in schema metadata.
5. Fail if a supported binary introduces a parameter with no metadata unless
   it is explicitly allow-listed as hidden/internal.
6. Fail if the GUI claims a parameter supported when the verified binary no
   longer recognizes it.

Imported unknown parameters shall never be silently discarded. They shall be
preserved in Expert mode with a warning and round-tripped on write unless the
user explicitly removes them.

Parameter editor levels
~~~~~~~~~~~~~~~~~~~~~

Essentials
^^^^^^^^^^

Essentials shall contain the common workflow-defining controls most users need:

* spectrum inputs;
* FASTA database;
* search/acquisition preset;
* precursor mass tolerance lower/upper and units;
* precursor tolerance type and isotope handling;
* fragment mass tolerance;
* search enzyme;
* number of enzymatic termini;
* allowed missed cleavages;
* static modifications;
* variable modifications;
* target/decoy strategy and decoy prefix/behavior where applicable;
* thread count / automatic CPU selection;
* a concise output summary showing that pepXML and PIN are required.

Advanced
^^^^^^^^

Advanced mode shall group parameters by scientific concept rather than file
order. At minimum categories shall include:

* Database and PEFF.
* CPU and execution.
* Precursor mass and isotope handling.
* Digestion/enzyme.
* Fragment ion scoring.
* Fragment-ion and peptide-index search options.
* Spectrum/scan/charge filters.
* Spectral preprocessing.
* Search ranges and peptide constraints.
* Output options.
* MS1/real-time-search options when supported.
* Static modifications.
* Variable modifications.
* Miscellaneous/version-specific options.

Expert
^^^^^^

Expert mode shall provide:

* canonical raw ``comet.params`` text;
* syntax highlighting;
* line-level validation diagnostics where possible;
* diff versus selected preset/default;
* diff versus last saved/run configuration;
* unknown imported parameters;
* a round-trip action from raw text into the typed model;
* explicit confirmation before a raw edit changes the typed configuration.

The raw editor must not become a second unsynchronized source of truth. Apply
must parse and validate into the typed model.

Global parameter search
~~~~~~~~~~~~~~~~~~~~~

The parameter editor shall include a search field matching:

* parameter name;
* display name;
* help text;
* category;
* common aliases such as "precursor tolerance", "missed cleavage", or
  "oxidation" where appropriate.

Additional filters should include:

* Modified only.
* Errors only.
* Warnings only.
* Expert parameters.
* Unsupported/imported parameters.

Typed control requirements
~~~~~~~~~~~~~~~~~~~~~~~~

Boolean parameters
    Use check boxes/toggles. The UI may show the serialized 0/1 value in help
    but shall not require users to type it.

Enums
    Use descriptive combo boxes/radio groups. The serialized integer or token
    shall be displayed in advanced help.

Numeric parameters
    Use validated numeric fields/spinners appropriate to range and precision.
    Scientific notation shall be supported when Comet supports it.

Ranges
    Use paired controls with one semantic label and validate lower <= upper.

Mass tolerances
    Use compound controls containing value(s), unit, and tolerance semantics.

File parameters
    Use a path field plus chooser; show existence/readability checks and avoid
    truncating the full path in accessible text/tooltips.

Ion families
    Use named checkboxes for A/B/C/X/Y/Z-related ion series and neutral-loss
    behavior instead of asking users to edit numeric flags.

Static modifications
    Use a residue/terminus-oriented table or grid with modification mass, name
    if user-supplied, and reset/default state.

Enzyme definitions
    Use an enzyme selector for known entries and a dedicated editor for custom
    enzyme definitions. The selected enzyme number and the enzyme table must be
    serialized consistently.

Variable modification editor
~~~~~~~~~~~~~~~~~~~~~~~~~~

Variable modifications are sufficiently structured that they require a
purpose-built editor. A free-form text field is not acceptable as the primary
control.

The editor shall present rows for Comet's variable modification slots and
model the fields represented by current Comet variable-mod syntax, including:

* mass delta;
* residue(s) or terminus selector token;
* binary/group behavior;
* minimum/maximum or maximum modification count semantics;
* distance from a terminus;
* terminus type;
* required/exclusive semantics;
* optional neutral loss value(s).

The UI should display a human-readable summary such as:

``Oxidation: +15.994915 on M; max 3 per peptide; optional``

rather than forcing users to mentally decode the serialized tuple.

Required features:

* Add modification.
* Remove modification.
* Reorder/assign slot where order matters.
* Common modification presets.
* Residue multi-select.
* N-/C-terminal choices.
* Explicit max/min count controls.
* Required/exclusive behavior with explanation.
* Neutral-loss editor.
* Validation of illegal residue/terminus/count combinations.
* "Show serialized value" for expert inspection.
* Round-trip tests for every supported tuple form.

Presets
~~~~~

Presets shall be versioned configuration deltas, not opaque replacement files.

Initial presets should include Comet's conventional instrument-resolution
patterns such as low-low, high-low, and high-high as appropriate to the
selected version, plus a minimal set of clearly named project presets if the
scientific team defines them.

Applying a preset shall show a reviewable diff:

::

    Parameter                    Current        Preset
    ----------------------------------------------------
    fragment_bin_tol             0.02           1.0005
    mass_type_fragment           monoisotopic   monoisotopic
    ...

The user can apply all or selected changes. User-defined presets shall store
which Comet version/schema they were created against. Applying them to a
newer/older version shall run a compatibility check.

Workflow-enforced Comet outputs
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The CometGUI workflow requires Comet artifacts consumed by downstream stages.
The application shall therefore force the corresponding Comet output settings
needed to create:

* pepXML for PDV and Limelight conversion;
* PIN/Percolator input for Percolator.

These fields shall appear in the output section as locked/on with text such as
"Required by CometGUI workflow". The exact option names shall come from the
selected version's schema.

Users may enable additional Comet outputs, but they cannot disable artifacts
required by an enabled downstream stage.

Comet validation
~~~~~~~~~~~~~~

Validation shall occur both per-field and across fields. Examples include:

* database exists and is readable;
* spectra exist and use a supported format;
* Thermo RAW is permitted only on supported platform/tool combinations;
* precursor tolerance values and units are valid;
* numeric ranges have lower <= upper;
* peptide-length ranges are valid;
* selected enzyme exists in the serialized enzyme table;
* variable modification tuples are internally valid;
* modification counts are consistent with selected version limits;
* output pepXML and PIN are enabled for the workflow;
* selected index/search options are compatible;
* decoy configuration is sufficient for Percolator;
* output paths are writable;
* an imported unknown parameter is explicitly surfaced;
* parameters unavailable in the selected Comet version are blocked rather than
  ignored.

Errors shall be attached to the responsible field/category, summarized at the
top of the editor, and accessible by keyboard.

Canonical serialization
~~~~~~~~~~~~~~~~~~~~~

The Comet parameter writer shall be deterministic for a given model:

* stable ordering;
* stable numeric formatting;
* stable newline convention within a platform-independent canonical artifact;
* explicit generated header containing CometGUI version and target Comet
  version;
* no hidden mutation at process-launch time.

The exact file written to disk shall be the exact file recorded in provenance
and passed to Comet.

Percolator Configuration
------------------------

Standard Percolator UI
~~~~~~~~~~~~~~~~~~~~

The standard Percolator screen shall expose:

* Percolator version selector.
* Capability/status badge.
* PSM result q-value filter: default 0.01.
* Peptide result q-value filter: default 0.01.
* A concise explanation that these filters change display/export only.
* An Advanced settings disclosure.

Advanced Percolator settings
~~~~~~~~~~~~~~~~~~~~~~~~~~

Advanced settings shall expose at minimum, where supported by the selected
version:

* ``testFDR``; default 0.01.
* ``trainFDR``; default 0.01.
* random seed.
* maximum iterations.
* thread count.
* train subset options where relevant.
* search-input/target-decoy behavior where relevant.
* protein decoy prefix behavior where relevant.
* additional supported options explicitly chosen for the product schema.

The GUI shall not equate ``testFDR`` with the PSM result display filter.
Descriptions shall distinguish them.

Percolator outputs
~~~~~~~~~~~~~~~~

For a normal run, the adapter shall request and preserve as available:

* target PSM TSV;
* target peptide TSV;
* optional decoy PSM TSV;
* optional decoy peptide TSV;
* learned weights file;
* XML output when the selected version supports XML and an enabled downstream
  stage needs it;
* stdout and stderr logs.

Raw outputs shall be immutable after successful completion. Derived filtered
exports shall be new files.

Input validation before Percolator
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Before invoking Percolator, CometGUI shall validate the PIN sufficiently to
catch common workflow mistakes:

* file exists and is nonempty;
* required header fields exist;
* both target and decoy labels are present when the configured mode requires
  them;
* feature columns contain parsable numeric values where required;
* protein/decoy prefix behavior is not obviously inconsistent with the selected
  configuration.

This validation should produce a useful GUI error before Percolator emits a
more obscure message.

Q-value result filters
~~~~~~~~~~~~~~~~~~~~

The Results model shall keep original q-values read from Percolator. A filter
is a view predicate:

.. code-block:: text

    PSM visible     := psm.q_value <= psm_filter
    Peptide visible := peptide.q_value <= peptide_filter

Required behavior:

* Defaults are 0.01 and 0.01 independently.
* Valid range is [0, 1].
* Values are stored in the project/run view state and provenance when used to
  generate an export.
* Changing a value updates counts/table contents without rerunning tools.
* Filter boundary behavior is inclusive (<=).
* The UI shows total records and records passing the current filter.
* Exported filtered files identify the cutoff in their provenance/metadata.
* Raw Percolator files remain unchanged.

Learned feature weights / parameter importance view
```

The application shall always request a Percolator weights artifact when the
selected version supports it. If a stable weights file is unavailable for a
specific version, a version-specific parser may fall back to the documented
stdout block, but the weights file is preferred.

The UI shall call this view **Learned feature weights (Percolator SVM)**. A
navigation shortcut may say **Parameter importances**, but the description must
state that the values are coefficients learned after Percolator's feature
normalization/cross-validation and should not be interpreted as causal
importance.

For every feature show:

* feature name;
* split 1 weight;
* split 2 weight;
* split 3 weight, when three splits are present;
* mean signed weight;
* mean absolute weight;
* standard deviation;
* sign consistency indicator;
* rank by mean absolute weight.

The table shall be sortable. A bar chart may show mean absolute magnitude with
signed direction available in text/table/tooltips. The table remains the
accessibility and export source of truth.

The weights artifact itself shall be checksummed and listed in provenance.

## Workflow Engine

Canonical workflow DAG

```

A normal run shall use these steps:

1. Validate project inputs/configuration.
2. Resolve/install/probe Comet.
3. Resolve/install/probe Percolator.
4. Serialize canonical Comet parameter file.
5. Hash immutable inputs.
6. Run Comet.
7. Validate Comet pepXML and PIN outputs.
8. Run Percolator.
9. Validate/parse Percolator outputs and learned weights.
10. Finalize core result indexes and summaries.
11. Hash outputs and finalize core provenance.
12. Optional: launch/use PDV.
13. Optional: run Limelight converter.
14. Optional: upload to Limelight.
15. Append downstream provenance events and refresh report.

Hashing may execute concurrently with tool installation when safe, but a tool
must never begin reading an input that is being mutated by CometGUI. The input
fingerprint recorded for a run must correspond to the file contents actually
used.

Stage reruns
~~~~~~~~~~

The workflow shall understand stage dependencies.

Examples:

* Changing only PSM/peptide display q filters requires no scientific rerun.
* Changing Percolator parameters requires rerunning Percolator and downstream
  Limelight conversion, but not Comet.
* Choosing Percolator 3.08 for Limelight after running 3.09 requires rerunning
  Percolator from the preserved PIN, then conversion; Comet is reused.
* Changing Comet parameters invalidates Comet, Percolator, and downstream
  stages.
* Changing only Limelight q cutoff invalidates only conversion/upload.

The UI shall preview which stages will rerun before execution.

Cancellation and recovery
```

Cancelling shall terminate the active stage and its process descendants where
possible, mark outputs as partial, and preserve logs/provenance.

A failed or cancelled run shall be reopenable. The user shall be able to retry
from the failed stage when prerequisites are still valid. CometGUI shall not
silently reuse a prerequisite whose checksum no longer matches the manifest.

## Project and Run Storage

Project model

```

A project contains mutable user intent and one or more immutable run records.

Recommended top-level structure:

::

    MyProject/
        project.json
        presets/
        runs/
            20260828T231500Z-<id>/
                run.json
                parameters/
                    comet.params
                    percolator-settings.json
                outputs/
                    comet/
                    percolator/
                    limelight/
                logs/
                    comet.stdout.log
                    comet.stderr.log
                    percolator.stdout.log
                    percolator.stderr.log
                    limelight-converter.log
                    limelight-upload.log
                provenance/
                    provenance.json
                    provenance.rst

Large input spectra/FASTA should not be copied into the project by default.
The project shall store canonical path, size, timestamps, MD5, SHA-256, and
other identity data. A future "portable project" function may copy/hard-link
inputs explicitly.

Run immutability
```

Once a run starts, its serialized Comet and Percolator scientific parameters
shall be immutable. Editing the project creates a new prospective configuration
or a new run/retry record. This prevents the GUI from showing a parameter state
that differs from what was actually executed.

## Results Model and UI

Result indexing

```

Percolator outputs shall be parsed into a queryable local result model. For
moderate files, in-memory models are acceptable; for very large result sets,
the implementation should support a disk-backed indexed representation such as
SQLite so the GUI does not require loading millions of rows into heap memory.

The parser layer shall be independent of the UI and version-aware.

PSM table
~~~~~~~

Columns should include when available:

* spectrum/native identifier;
* source file;
* scan/index;
* precursor charge;
* peptide/peptidoform;
* protein IDs;
* Percolator score;
* q-value;
* PEP;
* useful Comet score/features carried through the output;
* decoy/target state where appropriate.

Peptide table
~~~~~~~~~~~

Columns should include when available:

* peptide/peptidoform;
* proteins;
* Percolator score;
* q-value;
* PEP;
* number of supporting PSMs if derivable without changing scientific meaning.

Result tables shall support sorting, text filtering, column visibility,
copy/export, and stable selection.

PDV Integration
---------------

Managed installation
```

PDV shall be managed by the same Tool Registry as Comet and Percolator. The
initial verified version shall be PDV 2.6.0. Its JAR checksum and reported
version shall appear in provenance whenever PDV is launched from a run.

Baseline integration

```

The baseline production integration shall use documented PDV functionality for
Comet pepXML plus MGF/mzML/mzXML.

The Comet pepXML produced by the search shall be preserved even though the
primary result tables come from Percolator. This provides the native search
identification data PDV understands.

The Results UI shall provide:

* Open run in PDV.
* Open selected source/spectrum context in PDV when a robust documented mapping
  is available.
* A clear error if the source spectrum format cannot be visualized by the
  selected PDV version.

Enhanced exact-selection integration
```

A desirable enhancement is a generalized database-search equivalent of PDV's
existing de novo external-control mode. The preferred approach is an upstream
PDV contribution, conceptually similar to:

.. code-block:: text

```
java -jar PDV.jar db-gui \
    --result search.pep.xml \
    --result-format pepxml \
    --spectrum run.mzML \
    --port <ephemeral-port> \
    --hide-psm-table
```

and a loopback-only selection API based on a stable spectrum/native identifier.

If this does not exist upstream when implementation begins, the team shall
choose one explicit path:

1. ship baseline Open-in-PDV plus PDV CLI batch figure generation; or
2. maintain a minimal, clearly versioned PDV fork containing only the required
   database-search launcher/control extension.

A fork must be checksum/version tracked and should be upstreamed as quickly as
possible.

PDV testing

```

Automated tests shall not rely only on whether a PDV window appears. At least
one test shall invoke PDV's deterministic command-line figure generation on a
known Comet pepXML + spectrum and assert that a valid nonempty output figure is
produced. If the enhanced control server exists, E2E tests shall additionally
probe its health endpoint and select a known PSM.

Limelight Conversion and Upload
-------------------------------

Converter management
```

The `limelight-import-comet-percolator` JAR shall be installed and versioned
through the Tool Registry. The project shall pin a tested converter version for
each application release rather than silently using an untested latest JAR.

Conversion prerequisites

```

The Limelight tab shall validate:

* canonical Comet parameter file exists;
* Comet pepXML exists;
* Percolator XML exists;
* selected converter is installed/verified;
* FASTA path is available when needed;
* output directory is writable.

If the selected Percolator lacks ``XML_OUTPUT``, conversion controls shall be
disabled with an explanatory message and an action:

``Rerun Percolator with 3.08.0 for Limelight``

This action shall preserve the original Percolator run and create a distinct
Percolator-stage execution/provenance record. It shall reuse the Comet PIN and
shall not rerun Comet unless the PIN fails its checksum/prerequisite validation.

Converter UI
~~~~~~~~~~

The standard converter UI shall expose:

* Limelight q-value cutoff, default 0.01;
* output Limelight XML path;
* whether to import decoys;
* optional independent decoy prefix;
* open-mod mode;
* resolved FASTA;
* resolved pepXML directory.

Advanced values that can be inferred reliably should be shown read-only by
default and editable only when necessary.

The converter's one q-value option is separate from the GUI's independent PSM
and peptide result filters.

Conversion validation
~~~~~~~~~~~~~~~~~~~

A successful process exit is necessary but not sufficient. CometGUI shall also
validate that the Limelight XML:

* exists;
* is nonempty;
* is readable;
* has expected top-level XML structure;
* passes any readily available converter/schema validation that can be run
  locally.

The output shall be checksummed and recorded in provenance.

Upload
~~~~

The upload UI should reuse/refactor the CasanovoGUI Limelight upload approach.
It shall provide server/project selection as required by the Limelight API,
show live upload/import logs, and retain the final server-side identifier/URL
metadata when available.

Credentials/tokens/passwords shall never be written to provenance or ordinary
logs. Prefer OS credential/keychain storage where practical. At minimum,
secrets must be held separately from project files and redacted from command
display, process environment capture, and exported reports.

Provenance and Reproducibility
------------------------------

Provenance is a primary feature, not an afterthought.

Hash requirements
~~~~~~~~~~~~~~~

For every regular input and output file used/created by a run, record:

* canonical path at time of run;
* role/type;
* byte size;
* modification timestamp;
* MD5;
* SHA-256.

MD5 is mandatory because it is an explicit product requirement. SHA-256 is
also mandatory for robust integrity/security use.

Files shall be hashed by streaming chunks, not by reading the whole file into
memory. Output files shall be hashed only after the producing process has
closed/finalized them. Partial files from failed/cancelled stages may be hashed
but must be marked ``partial``.

Tool provenance
~~~~~~~~~~~~~

For each tool invocation record:

* logical tool name;
* reported version;
* release/tag/commit when known;
* executable/JAR path;
* executable/JAR MD5 and SHA-256;
* upstream/managed artifact identity;
* managed versus local status;
* capabilities;
* exact argument array;
* safely rendered command for display;
* working directory;
* environment variables added/overridden;
* start timestamp;
* end timestamp;
* duration;
* exit code;
* stdout log path/checksums;
* stderr log path/checksums;
* cancellation/failure state;
* warnings/advisories active for that version.

Application provenance
~~~~~~~~~~~~~~~~~~~~

Record:

* CometGUI version;
* build identifier/git commit;
* operating system/version;
* architecture;
* JVM/runtime version;
* locale/time zone where relevant;
* project/run IDs;
* generated Comet parameter file hash and full archived copy;
* Percolator scientific settings;
* result-view q filters when used for a derived export;
* Limelight conversion parameters;
* PDV launch/version when used.

Do not record secrets.

Provenance event model
~~~~~~~~~~~~~~~~~~~~

Provenance should be written incrementally as appendable events or atomically
updated state so a crash still leaves useful history. The final
``provenance.json`` shall have a schema version.

The human-readable ``provenance.rst`` report shall be generated from the same
machine-readable model, not maintained independently.

Provenance UI
~~~~~~~~~~~

The Provenance tab shall contain:

Summary
    Run ID, status, start/end, tool versions, input count, output count.

Tools
    Name, version, managed/local, binary path, MD5, SHA-256, capability badge.

Inputs/outputs
    File role, path, size, MD5, SHA-256, status.

Parameters
    Exact Comet file, Percolator settings, preset/origin information, and diffs
    where useful.

Timeline
    Ordered workflow stages with command, time, duration, exit status.

Logs
    Links/actions to open archived logs.

Warnings
    Version advisories, compatibility workarounds, partial-file notices.

Actions shall include Copy MD5, Copy SHA-256, Copy command, Open file location,
Export provenance JSON, and Export provenance RST.

Supply-Chain and Application Security
-------------------------------------

At minimum:

* Use HTTPS for managed downloads.
* Verify SHA-256 before executing downloaded artifacts.
* Prefer signed upstream releases or a signed CometGUI artifact manifest.
* Guard ZIP/TAR extraction against ``../`` traversal, absolute paths, and unsafe
  symlinks.
* Never execute a tool directly from an unverified temporary download.
* Record provenance for the exact artifact executed.
* Keep tool caches user-writable but application-scoped.
* Do not put credentials in command-line arguments when a safer API exists.
* Redact known secret environment variables.
* Generate an SBOM for CometGUI dependencies at release time.
* Run dependency vulnerability scanning in CI.
* Pin build-plugin versions.
* Sign native installers/packages where project infrastructure permits.
* Publish release checksums.
* Audit licenses for CasanovoGUI source, Comet, Percolator, PDV, converter,
  Java runtime, and bundled/transitive components before redistribution.

Testing Strategy
----------------

Testing philosophy
~~~~~~~~~~~~~~~~

The test suite must be able to catch real defects. Tests that only assert that
a method returns non-null, a window opens, or an exception does not occur are
insufficient for scientific and provenance-critical code.

The suite shall distinguish:

Fast unit tests
    Run on every local/CI build and do not require network or native scientific
    tools.

Component/integration tests
    Exercise filesystem/process/parser/install boundaries with controlled
    fixtures and fake processes where useful.

Real-tool integration tests
    Execute real pinned Comet/Percolator/converter/PDV binaries on small real
    fixtures.

GUI tests
    Drive JavaFX controls and verify UI state/validation.

Packaged end-to-end tests
    Start the actual packaged application in a clean environment and drive a
    complete real workflow.

Nightly/scientific regression tests
    Use larger real data, broader version matrices, determinism and performance
    checks.

Release acceptance tests
    Test the exact installer/package artifacts that will be published.

JUnit and Java test practices
```

Use JUnit Jupiter (JUnit 5) for Java tests. Apply Java testing best practices:

* Arrange/Act/Assert or similarly clear test structure.
* One scientifically meaningful behavior per test when practical.
* Descriptive test names stating condition and expected outcome.
* Parameterized tests for boundaries/version matrices.
* Dynamic tests where a schema defines a large set of invariant checks.
* Temporary directories for filesystem tests.
* No dependence on developer home-directory state.
* Fixed seeds for randomized tests; print seed on failure.
* Explicit timeouts for processes and deadlock-prone code.
* Avoid arbitrary `sleep` in asynchronous/GUI tests; wait on observable
  state/conditions.
* Clean up processes/resources in `finally`/test extensions.
* Make failures retain logs and diagnostic artifacts.
* Keep scientific E2E tests serial/resource-locked when parallelism would cause
  contention; parallelize pure unit tests where safe.

Unit tests: Comet parameter system

```

At minimum, meaningful tests shall cover:

* parse every supported scalar type;
* serialize every supported scalar type;
* canonical parse -> write -> parse equivalence;
* imported comments/unknown parameter preservation policy;
* malformed lines;
* duplicate parameter handling policy;
* enum mappings;
* numeric lower/upper boundaries;
* mass tolerance units;
* enzyme table parsing/serialization;
* custom enzyme serialization;
* static modification controls;
* every variable-modification tuple field;
* variable modification min/max counts;
* terminal variable modifications;
* required/exclusive variable modifications;
* one and two neutral-loss values;
* invalid variable-mod combinations;
* range validation;
* output requirements enforced by workflow;
* decoy cross-validation;
* version-introduced/version-removed parameters;
* presets and preset diffs;
* resetting one field/category/all fields;
* deterministic serialization;
* schema migration behavior;
* raw Expert mode round-trip;
* unknown imported parameters not silently lost.

Tests shall use expected serialized ``comet.params`` snippets/files and shall
fail on semantically meaningful changes.

Unit tests: Percolator
~~~~~~~~~~~~~~~~~~~

At minimum:

* version parsing for 3.05 through current/future-looking strings;
* capability mapping;
* command argument construction by version;
* PSM TSV parsing;
* peptide TSV parsing;
* q-value parsing and missing/NaN handling policy;
* inclusive filter boundary at exactly 0.01;
* filter values 0 and 1;
* invalid filter values below 0 or above 1;
* PSM filter independent of peptide filter;
* train/test FDR settings independent of result filters;
* weights file parsing;
* three-split weight summary statistics;
* sign consistency;
* ranking by mean absolute weight;
* malformed weights file;
* missing weights file behavior;
* 3.09 XML capability absent;
* 3.08 XML capability present in verified registry;
* version advisory rendering/model behavior.

Unit tests: provenance
~~~~~~~~~~~~~~~~~~~~

At minimum:

* known MD5 test vectors;
* known SHA-256 test vectors;
* streaming large-ish temporary files;
* zero-byte files;
* file changed during hash detection/handling policy;
* deterministic manifest serialization where required;
* command argument preservation;
* environment override capture;
* secret redaction;
* no token/password leakage in JSON/RST;
* partial output state;
* failed process state;
* cancelled process state;
* finalization is atomic;
* reopen/parse manifest;
* schema-version migration;
* all mandatory files represented.

Unit tests: installation/security
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

At minimum:

* correct artifact selection by OS/architecture/version;
* checksum match;
* checksum mismatch blocks execution;
* truncated archive;
* missing expected executable;
* interrupted install recovery;
* existing valid cache hit;
* existing corrupt cache rejected;
* ZIP/TAR path traversal attempt rejected;
* absolute archive path rejected;
* unsafe symlink extraction rejected;
* local binary version probe;
* local binary < 3.05 rejected for Percolator;
* local binary checksum recorded;
* capability probe failure produces actionable state.

Architecture tests
~~~~~~~~~~~~~~~~

Use ArchUnit or equivalent to enforce rules such as:

* ``ui`` may depend on domain/application APIs, but domain must not depend on
  JavaFX.
* tool adapters must not depend on UI classes.
* provenance/hashing must not depend on UI.
* parameter parser/writer must not depend on JavaFX.
* no cyclic dependencies between major modules/layers.
* process creation is centralized through the process service rather than
  scattered ``new ProcessBuilder`` calls.

Mutation testing
~~~~~~~~~~~~~~

Use PIT or an equivalent Java mutation-testing system on critical pure logic.
Mutation testing is especially important for code where superficial line
coverage is easy to achieve but assertions may be weak.

Prioritize:

* Comet parameter parsers/writers;
* parameter validators;
* Percolator q-value filters;
* command builders;
* version/capability rules;
* checksum/provenance code;
* secret redaction;
* stage invalidation rules.

A recommended initial gate is >= 80% mutation score in these critical
packages, with no known surviving mutation that can disable checksum
verification, invert a q-value comparison, drop a required output, suppress a
validation error, or leak a secret.

Coverage
~~~~~~

Use JaCoCo. Coverage is a diagnostic/gate, not the goal by itself.

Recommended initial gates:

* core domain/parameter/provenance logic: >= 90% line and >= 85% branch;
* UI-independent view-model/presenter logic: >= 80% line;
* adapters: coverage supplemented by real integration tests rather than chasing
  artificial line counts;
* JavaFX rendering glue: no unrealistic numeric target, but primary user flows
  must be GUI-tested.

Any lower threshold must be documented with the untested risk.

Component/integration tests with fake executables
```

Create tiny test executables/scripts that behave like scientific tools so the
workflow engine can deterministically test:

* stdout/stderr interleaving;
* exit 0 with outputs;
* exit nonzero;
* child process creation;
* hanging process and cancellation;
* huge stdout/stderr volume;
* missing output despite exit 0;
* malformed output;
* partial file followed by failure;
* delayed output creation;
* paths containing spaces and Unicode.

These fakes complement, but never replace, real-tool tests.

Real-tool integration tests

```

The test repository shall include or fetch a small license-compatible real
proteomics fixture:

* small FASTA with target and decoy behavior suitable for Comet/Percolator;
* small mzML and/or MGF containing enough real spectra to produce both target
  and decoy PIN rows;
* known, stable Comet parameter preset;
* expected invariants and version-pinned golden data.

Real-tool tests shall:

1. Generate ``comet.params`` using the production parameter writer.
2. Run the pinned real Comet binary.
3. Verify pepXML and PIN existence/parseability.
4. Verify target and decoy rows required by the configured Percolator mode.
5. Run real Percolator.
6. Verify PSM/peptide output and learned weights.
7. Verify XML for XML-capable versions.
8. Run the real Limelight converter for the 3.08 workflow.
9. Verify a valid nonempty Limelight XML result.
10. Run PDV CLI generation for at least one selected spectrum and verify a
    nonempty annotated figure.

JavaFX GUI automation
~~~~~~~~~~~~~~~~~~~

TestFX is a candidate because it provides a JavaFX robot/JUnit integration, but
its compatibility with the selected JDK/JavaFX versions shall be proven in an
early technical spike rather than assumed. If TestFX cannot reliably operate
with JavaFX 25/JDK 23+ in CI, the project shall retain the same test semantics
behind a small ``FxUiDriver`` abstraction and use a compatible JavaFX robot or
native-accessibility automation mechanism.

Controls required by automated tests shall have stable semantic identifiers
(``fx:id`` or a dedicated stable test/accessibility ID). Tests shall not locate
important controls by pixel coordinates or brittle CSS ancestry.

GUI tests shall cover at least:

* navigation to all primary sections;
* file chooser abstraction/test injection;
* Comet Essentials fields;
* Advanced category expansion;
* parameter search;
* reset field/category;
* preset preview/diff/apply;
* variable modification add/edit/remove;
* inline validation and error summary;
* Expert raw editor apply/reject;
* Percolator version selection/capability message;
* default q filters both equal 0.01;
* independent q filter editing;
* run button enabled/disabled rules;
* run progress/state stepper;
* cancellation;
* results tables;
* learned weights table/chart data;
* Limelight disabled for no-XML Percolator;
* Limelight rerun-with-3.08 action;
* Provenance tab and checksum copying;
* keyboard focus traversal for critical workflow;
* accessible names for primary controls.

Packaged GUI end-to-end harness
```

This is a mandatory release feature.

The harness shall test the *packaged application* with a fresh temporary home
and tool cache. It must not call `WorkflowEngine.run()` directly and call
that an E2E test.

Preferred interaction strategy:

* JavaFX robot for in-process packaged-test builds where feasible; and/or
* an external UI automation driver against the packaged executable;
* optionally a test-only loopback control bridge that triggers the *same UI
  actions/commands* as real controls and exposes observable UI state.

A test bridge, if used, must be disabled or omitted in production builds and
must not bypass parameter serialization, validation, or workflow orchestration.
It exists to make UI interaction deterministic, not to create a hidden backend
execution API that avoids the GUI.

Canonical E2E scenario
^^^^^^^^^^^^^^^^^^^^^^

The principal E2E test shall perform the following sequence.

1. Create a brand-new temporary user home/application data directory.
2. Ensure no Comet, Percolator, PDV, or converter is installed in that cache.
3. Launch the exact packaged CometGUI artifact.
4. Verify the application reaches a ready state.
5. Create/open a test project through the UI.
6. Choose the real spectrum fixture through the same control used by users.
7. Choose the real FASTA fixture.
8. Select Comet 2026.02.2.
9. Select Percolator 3.08.0.
10. Change at least one precursor/fragment parameter from its preset value.
11. Add/edit at least one variable modification using the structured GUI.
12. Verify the GUI parameter summary reflects these changes.
13. Confirm default PSM q filter = 0.01.
14. Confirm default peptide q filter = 0.01.
15. Click Run using the GUI.
16. Verify the GUI downloads required tools automatically.
17. Verify download/checksum states become successful.
18. Wait on observable workflow state, never a fixed sleep.
19. Verify generated `comet.params` exactly contains the GUI-selected values.
20. Verify workflow-required pepXML/PIN outputs were forced on.
21. Verify Comet exits successfully and pepXML/PIN parse.
22. Verify Percolator exits successfully and PSM, peptide, weights, and XML
    artifacts parse.
23. Independently calculate the number of PSMs with q <= 0.01 from the raw
    Percolator file and compare with the GUI count.
24. Independently calculate the number of peptides with q <= 0.01 and compare
    with the GUI count.
25. Change PSM filter to 0.005 through the UI and verify only the PSM count
    changes according to independent calculation.
26. Change peptide filter to 0.02 and verify the peptide count independently.
27. Open Learned feature weights and compare displayed values/summary ranking
    with the actual weights artifact.
28. Invoke PDV integration. At minimum, run the supported PDV visualization or
    CLI figure path on a known PSM and verify successful output.
29. Run the real Limelight converter through the UI.
30. Validate the resulting Limelight XML.
31. Exercise Limelight upload against a controlled local fake endpoint or an
    official test/sandbox endpoint; do not upload CI data to a production
    server.
32. Open the Provenance view.
33. Independently recompute MD5 and SHA-256 for every declared input/output and
    compare with the manifest.
34. Verify tool binary/JAR checksums in provenance match files on disk.
35. Verify exact Comet and Percolator versions and argv are present.
36. Verify secrets are absent.
37. Close CometGUI.
38. Relaunch the packaged application and reopen the project.
39. Verify results, selected run, tool versions, q-filter state, and provenance
    remain coherent.

This test should be designed to fail if a GUI control stops affecting the
parameter file, a downstream stage is bypassed, filtering uses `<` instead
of `<=`, an output is omitted from provenance, or tool installation is no
longer automatic.

Second E2E scenario: newest Percolator without XML
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

A second packaged E2E test shall:

1. Select Percolator 3.09.
2. Run Comet + Percolator successfully.
3. Verify standard PSM/peptide/weights result viewing works.
4. Verify the Limelight tab explains XML incompatibility before conversion is
   attempted.
5. Verify the one-click compatible rerun selects/installs 3.08.0 only after the
   test/user invokes that explicit action.
6. Verify Comet is not rerun.
7. Verify the new 3.08 Percolator execution produces XML.
8. Verify Limelight conversion then succeeds.
9. Verify provenance contains both Percolator executions with distinct
   versions/checksums/commands.

Failure-path GUI E2E scenarios
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Separate E2E/integration tests shall exercise:

* download checksum mismatch;
* network unavailable on first install;
* network unavailable after tools are cached;
* Comet exits nonzero;
* Comet exits zero but required PIN missing;
* PIN contains no decoys;
* Percolator exits nonzero;
* Percolator output malformed;
* converter exits nonzero;
* converter returns zero but XML missing/empty;
* user cancels Comet;
* user cancels Percolator;
* source input is deleted after selection;
* input checksum changes before a rerun;
* output/project directory becomes unwritable;
* insufficient disk space where it can be simulated safely;
* paths containing spaces;
* paths containing Unicode;
* long paths on supported platforms;
* corrupted cached tool;
* interrupted tool install;
* stale/partial run reopened.

Scientific regression fixtures and test oracles

```

Do not use one oracle type for all tests.

Exact invariants
    Format, required columns, process exit, version, command args, generated
    params, provenance hashes, filter math, known selected fixture IDs.

Version-pinned golden expectations
    Expected selected PSM IDs, stable counts/ranges, known parameter file text,
    known weights parser output for a specific pinned tool pair.

Tolerant scientific metrics
    For larger real datasets, compare PSM counts/rank agreement/performance
    within justified tolerance instead of requiring byte-identical float output
    across platforms.

Goldens shall be keyed by the scientific tool pair, for example:

::

    src/test/resources/goldens/
        comet-2026.02.2_percolator-3.08.0/
        comet-2026.02.2_percolator-3.09.0/

Updating a golden shall require a reviewed explanation of why the scientific
change is expected.

Percolator version matrix tests
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

CI/nightly testing shall include representative versions:

* 3.05.x.
* 3.06.1 or later 3.06.x, plus a targeted advisory/regression check for any
  specifically supported affected 3.06 release.
* 3.07.1.
* 3.08.0.
* 3.09.
* newest verified future version after registry update.

For each version, test available PSM/peptide/weights outputs. For XML-capable
versions, test XML. For 3.09+, verify that the app does not request a removed
XML option and does not enable Limelight conversion from nonexistent XML.

Comet regression strategy
~~~~~~~~~~~~~~~~~~~~~~~

The Comet project itself uses unit, regression, real-data, determinism, and
performance testing patterns. CometGUI should adopt the same philosophy at its
integration boundary.

Nightly tests should include:

* current default Comet;
* at least one prior supported Comet version if the product offers it;
* direct FASTA search;
* relevant index-search mode if exposed in the GUI;
* repeated run/determinism checks on pinned fixture;
* Windows Thermo RAW smoke test where infrastructure permits;
* mzML or mzXML cross-platform baseline;
* a larger real dataset for result-count/rank/performance drift.

Performance and resource tests
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Performance tests shall measure separately:

* application startup;
* parsing a large Comet params file;
* rendering/filtering large PSM/peptide result sets;
* hashing multi-GB files;
* Comet execution time (informational/tool-dependent);
* Percolator execution time;
* peak GUI heap usage for large result tables;
* cancellation latency;
* project reopen time.

Performance regression thresholds should be based on stable dedicated/nightly
runners, not noisy pull-request shared runners.

Flakiness policy
~~~~~~~~~~~~~~

A flaky test is a defect. Do not hide instability with unconditional retries.
A temporarily quarantined flaky test must have:

* an issue/owner;
* captured diagnostics;
* a stated reason;
* a removal/fix plan.

Release-critical scientific E2E tests may not remain quarantined at release.

CI and Release Pipeline
-----------------------

Pull-request pipeline
~~~~~~~~~~~~~~~~~~~

At minimum:

1. Compile on supported JDK.
2. Formatting/style check.
3. Static analysis.
4. JUnit fast tests.
5. JaCoCo coverage gate.
6. ArchUnit architecture tests.
7. PIT mutation tests for critical packages, either every PR or on a required
   scheduled/merge gate depending on runtime.
8. Small real-tool integration tests on Linux.
9. RST/Sphinx docs build with warnings as errors.
10. Dependency/security scan.
11. SBOM generation validation.

Nightly pipeline
~~~~~~~~~~~~~~

Add:

* broader Comet/Percolator version matrix;
* larger real dataset;
* determinism comparisons;
* performance metrics;
* headless and/or native GUI test suite;
* Windows RAW-specific search test;
* documentation link checker;
* managed tool URL/checksum availability verification.

Release pipeline
~~~~~~~~~~~~~~

For Windows x64, macOS arm64, and Linux x64 packages at minimum:

1. Build native packaged application with bundled runtime.
2. Produce installer/archive.
3. Compute release checksums.
4. Run clean-home packaged smoke test.
5. Run canonical packaged GUI E2E on that exact artifact.
6. Run 3.08 full Limelight-compatible E2E.
7. Run 3.09 no-XML compatibility E2E.
8. Verify tool download manifest.
9. Verify RST docs release build.
10. Generate/publish SBOM.
11. Sign/notarize where infrastructure requires/permits.
12. Publish only if all release gates pass.

Documentation
-------------

All project documentation shall be authored in reStructuredText and built with
Sphinx for Read the Docs. Avoid a second Markdown documentation system. If a
hosting platform absolutely requires a small README file, prefer ``README.rst``
where supported and keep substantive documentation under ``docs/``.

Recommended documentation tree
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

::

    README.rst
    .readthedocs.yaml
    docs/
        conf.py
        index.rst
        installation.rst
        getting_started.rst
        workflow.rst
        comet_parameters.rst
        comet_parameter_presets.rst
        variable_modifications.rst
        percolator.rst
        results.rst
        learned_feature_weights.rst
        pdv.rst
        limelight.rst
        provenance.rst
        tool_manager.rst
        troubleshooting.rst
        faq.rst
        citations.rst
        release_notes.rst

        developer/
            index.rst
            architecture.rst
            workflow_engine.rst
            comet_parameter_schema.rst
            tool_adapters.rst
            tool_registry.rst
            version_capabilities.rst
            results_model.rst
            provenance_schema.rst
            security.rst
            testing.rst
            e2e_harness.rst
            releasing.rst

        reference/
            comet_parameters_generated.rst
            percolator_options.rst
            project_format.rst
            provenance_format.rst
            command_examples.rst

Parameter reference generation
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The Comet parameter schema shall be able to generate an RST reference page so
user documentation and GUI metadata cannot silently diverge.

For every parameter, generated docs should include:

* Comet parameter name;
* GUI display name;
* category;
* type;
* default for selected/versioned schema;
* allowed values/range;
* scientific description;
* serialization form;
* version availability;
* related parameters;
* preset effects where useful.

Documentation CI
~~~~~~~~~~~~~~

CI shall run Sphinx in strict mode, conceptually:

.. code-block:: text

    sphinx-build -n -W -b html docs docs/_build/html

A scheduled/release job shall also run link checking. Broken internal
cross-references are build failures.

Implementation Sequence
-----------------------

Phase 0: legal and technical feasibility gates
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Before broad implementation:

* clarify/license permission for derivative use of CasanovoGUI source;
* verify redistribution/download rules for every managed external tool;
* prove JavaFX 25/JDK target packaging on all supported OS targets;
* run a TestFX/alternative JavaFX automation compatibility spike;
* prove Comet 2026.02.2 -> PIN/pepXML -> Percolator 3.08 XML/TSV/weights ->
  Limelight converter manually from a scripted prototype;
* prove Percolator 3.09 path without XML;
* prove PDV CLI/database-search visualization on a current Comet pepXML;
* decide whether the enhanced PDV database control mode will be contributed
  upstream or deferred to baseline integration.

Phase 1: fork/refactor CasanovoGUI shell
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

* establish CometGUI identity/package names;
* preserve modern JavaFX/AtlantaFX styling and packaging;
* replace Casanovo-specific workflow classes with generic tool/process APIs;
* refactor reusable installer, runner, PDV, and Limelight patterns;
* create project/run state model;
* add unit/architecture test infrastructure before feature expansion.

Phase 2: secure Tool Registry
~~~~~~~~~~~~~~~~~~~~~~~~~~~

* artifact manifest;
* downloader;
* SHA-256 verification;
* safe archive extraction;
* atomic installs;
* version/capability probes;
* Comet managed install;
* Percolator 3.08 and 3.09 managed installs;
* PDV and Limelight converter managed installs;
* local Percolator registration;
* provenance records for tool artifacts.

Phase 3: Comet parameter system
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

* typed schema;
* parser/writer;
* schema drift tests;
* Essentials UI;
* Advanced categories;
* variable modification editor;
* enzyme/static mod editors;
* presets/diff/reset/search;
* Expert raw view;
* version validation;
* RST reference generation.

This phase should be treated as the central product-design effort, not a small
form-building task.

Phase 4: core Comet -> Percolator workflow
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

* workflow engine/state machine;
* Comet adapter;
* required pepXML/PIN outputs;
* Percolator adapter and version capabilities;
* result filters;
* weights capture;
* result parsers;
* cancellation/retry;
* core provenance.

Phase 5: results and visualization
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

* scalable PSM/peptide models;
* filter/sort/export;
* learned weight table/chart;
* PDV managed install/open;
* PDV CLI test integration;
* optional enhanced PDV row-control extension.

Phase 6: Limelight and complete provenance
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

* converter adapter;
* single Limelight q cutoff UI;
* Percolator 3.09 incompatibility handling;
* explicit rerun with 3.08;
* upload UI;
* credential redaction/storage;
* provenance UI and RST/JSON export.

Phase 7: hardening and comprehensive automation
```

* complete unit tests;
* mutation testing;
* architecture tests;
* GUI robot suite;
* packaged E2E harness;
* real fixtures;
* cross-version matrix;
* negative/chaos tests;
* Windows RAW tests;
* performance/regression jobs;
* supply-chain/security tests.

Phase 8: documentation and release qualification

```

* complete RST user docs;
* complete RST developer/testing docs;
* generated parameter reference;
* Sphinx warning-free build;
* Read the Docs configuration;
* release packages;
* clean-machine acceptance;
* SBOM/checksum/signing/licensing review.

Acceptance Criteria
-------------------

A release is not complete unless all applicable criteria below are met.

Installation and tool management
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

* [ ] On each supported OS, a user can install/extract CometGUI without
      installing Java separately.
* [ ] A first real run installs required scientific tools automatically.
* [ ] Downloaded executables/JARs are SHA-256 verified before execution.
* [ ] Managed tool MD5 and SHA-256 are shown in provenance.
* [ ] Corrupt/checksum-mismatched tools are never executed.
* [ ] Percolator 3.05+ local binaries can be registered and probed.
* [ ] Existing runs pin exact scientific tool versions.

Comet parameter UI
~~~~~~~~~~~~~~~~

* [ ] Comet 2026.02.2 is fully represented by the supported parameter schema,
      subject only to explicitly documented internal/hidden exclusions.
* [ ] Schema drift CI detects unmodeled supported parameters.
* [ ] Essentials mode can configure a normal tryptic DDA search without raw
      parameter editing.
* [ ] Advanced mode exposes all supported user-relevant parameters.
* [ ] Variable modifications use a structured editor.
* [ ] Imported unknown parameters are not silently dropped.
* [ ] Expert raw mode round-trips through the typed model.
* [ ] Preset application shows a diff.
* [ ] Required pepXML/PIN outputs cannot be disabled accidentally.
* [ ] Invalid cross-parameter configurations block Run with actionable errors.

Percolator/results
~~~~~~~~~~~~~~~~

* [ ] Default PSM result q cutoff is 0.01.
* [ ] Default peptide result q cutoff is 0.01.
* [ ] These filters are independent.
* [ ] These filters do not rerun Percolator.
* [ ] ``trainFDR`` and ``testFDR`` are represented separately.
* [ ] 3.08.0 can produce PSM, peptide, weights, and XML in the verified workflow.
* [ ] 3.09 can run normal rescoring without the GUI attempting removed XML I/O.
* [ ] Learned Percolator weights are viewable and exportable.
* [ ] Weight summary values match the underlying artifact.

PDV/Limelight
~~~~~~~~~~~

* [ ] Current verified PDV installs automatically.
* [ ] A known Comet pepXML + spectrum can be visualized.
* [ ] PDV CLI automated test produces a valid annotated spectrum artifact.
* [ ] Limelight converter installs automatically.
* [ ] Limelight conversion succeeds for the 3.08 workflow fixture.
* [ ] Limelight controls are disabled/explained for 3.09 without XML.
* [ ] Explicit 3.08 rerun reuses Comet output and enables conversion.
* [ ] Limelight q cutoff is separately configurable and defaults to 0.01.
* [ ] Credentials are not stored in provenance/logs.

Provenance
~~~~~~~~

* [ ] Every input and output file has MD5 and SHA-256 where the file exists.
* [ ] Exact tool versions and tool artifact hashes are recorded.
* [ ] Exact command argument arrays are recorded.
* [ ] Exact generated Comet parameter file is archived and hashed.
* [ ] Start/end/duration/exit code are recorded for each process.
* [ ] Failed/cancelled runs retain useful provenance.
* [ ] Provenance is viewable in the GUI.
* [ ] Provenance exports to JSON and RST.
* [ ] Independent E2E hash recomputation matches the manifest.
* [ ] Secret redaction tests pass.

Testing/release
~~~~~~~~~~~~~

* [ ] Meaningful JUnit tests cover critical domain logic.
* [ ] Core coverage gates pass.
* [ ] Critical-package mutation score gate passes.
* [ ] Architecture rules pass.
* [ ] JavaFX GUI automation passes.
* [ ] Canonical packaged 3.08 E2E passes on supported release platforms.
* [ ] Packaged 3.09 no-XML E2E passes.
* [ ] Failure-path E2E/integration suite passes.
* [ ] Nightly real-data regression suite is healthy.
* [ ] Windows Thermo RAW smoke test is healthy when that feature is released.
* [ ] Sphinx docs build with warnings as errors.
* [ ] Read the Docs configuration builds successfully.
* [ ] SBOM/security/dependency checks pass.
* [ ] CasanovoGUI derivative-source licensing issue is resolved before public
      redistribution.

Key Risks and Required Decisions
--------------------------------

CasanovoGUI source licensing
~~~~~~~~~~~~~~~~~~~~~~~~~~

This is the highest-priority nontechnical gate. The source is public, but a
public GitHub repository is not automatically permission to redistribute a
derivative. Add an explicit license to the source base or obtain/document
permission before release.

Percolator historical binary availability
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

"Support every Percolator version >= 3.05" is feasible at the adapter/schema
level, but managed one-click installation also depends on a usable binary for
every version/platform. The release process must either publish verified
project-built artifacts where legally permitted or describe unsupported
managed combinations and allow a local binary.

Percolator XML removal
~~~~~~~~~~~~~~~~~~~~

This must remain a capability, not a hack. Do not freeze the entire application
on 3.08 merely for Limelight. Let users use newer Percolator versions and make
the Limelight compatibility boundary explicit.

PDV database-search remote control
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Current PDV has the needed file-format support but its documented external
control path is de-novo-specific. If precise click-a-Percolator-row -> update
PDV-in-place behavior is required for version 1.0, budget an upstream PDV
feature or small maintained fork. Do not hide this dependency behind fragile
GUI automation.

Comet parameter completeness
~~~~~~~~~~~~~~~~~~~~~~~~~~

Comet evolves. A hand-written static form will rot. The combination of
versioned metadata + binary-derived supported parameter names + schema drift CI
is required to keep the GUI credible.

Scientific golden tests
~~~~~~~~~~~~~~~~~~~~~

Exact output can change legitimately with tool versions. Goldens must be keyed
by tool versions and reviewed when changed. Workflow correctness, provenance,
filter math, parameter generation, and format invariants can be tested more
strictly than all floating-point scores.

Large result scalability
~~~~~~~~~~~~~~~~~~~~~~

Do not assume result tables fit in memory. Establish a performance fixture early
and switch to disk-backed indexing before UI architecture becomes coupled to
``ObservableList`` containing every PSM.

Definition of Done
------------------

The project is done when a scientist on a clean supported computer can install
only CometGUI, choose real spectra and FASTA, configure a scientifically valid
Comet search through a comprehensible parameter interface, select a supported
Percolator version, execute the real workflow, inspect 1% PSM and peptide
results, change those filters independently, inspect learned Percolator feature
weights, inspect spectra in PDV, produce/upload compatible Limelight XML, and
inspect a provenance record containing exact versions, commands, and MD5 plus
SHA-256 hashes for all inputs and outputs -- and when automated tests drive the
packaged GUI through the same workflow and independently prove that those
claims are true.
```
