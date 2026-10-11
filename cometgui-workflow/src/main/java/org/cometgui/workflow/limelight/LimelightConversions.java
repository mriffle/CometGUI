/*
 * CometGUI -- Comet to Percolator proteomics search workflow with provenance.
 * Copyright (C) 2026 The CometGUI authors.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License, version 3, as published
 * by the Free Software Foundation. It is distributed WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for details.
 *
 * The full licence is the LICENSE file at the root of this repository. If it
 * is missing, see <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.cometgui.workflow.limelight;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.percolator.resolution.CompatibilityAction;
import org.cometgui.params.percolator.resolution.DownstreamStage;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ManifestReader;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.tools.api.JavaRuntime;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.limelight.LimelightConversion;
import org.cometgui.tools.limelight.LimelightConversionException;
import org.cometgui.tools.limelight.LimelightConversionRequest;
import org.cometgui.tools.limelight.LimelightConverter;
import org.cometgui.tools.limelight.LimelightConverterIdentity;
import org.cometgui.tools.limelight.PepXmlSet;
import org.cometgui.tools.limelight.PercolatorXmlPsms;
import org.cometgui.tools.percolator.PercolatorOption;
import org.cometgui.tools.process.RunMessageSink;
import org.cometgui.workflow.steps.PercolatorProvenance;
import org.cometgui.workflow.steps.RunResultFiles;
import org.cometgui.workflow.storage.LimelightSettings;
import org.cometgui.workflow.storage.LimelightSettingsStore;

/**
 * A finished run converted to Limelight XML: whether it can be, and doing it -- the one path
 * through which a Limelight conversion is started (phase 12, design decisions P12-1, P12-3, P12-5
 * to P12-8).
 *
 * <h2>The one question: {@link #check}</h2>
 *
 * <p>"Can this run convert now, and if not, why?" is answered by {@link #check}, which reads and
 * hashes, and starts nothing. Every prerequisite of the specification's <em>Conversion
 * prerequisites</em> is checked and every one that is not met is named ({@link
 * LimelightPrerequisite}): the run is not executing; its canonical {@code comet.params} exists and
 * hashes to the SHA-256 the run recorded; its Percolator wrote XML, recorded complete in its {@code
 * provenance.json} and unchanged since; the Comet pepXML its search recorded exist in that search's
 * {@code outputs/comet/} unchanged, and are every pepXML the converter will derive from the PSM
 * ids; the converter is installed and verified through the Tool Manager; the run's FASTA is where
 * the run recorded it and unchanged ({@code R-RUN-02}: the refusal says which file changed); the
 * output is a new file in a writable directory; and the decoy choice agrees with the run's own
 * Percolator output (P12-7). {@link #convert} asks the same question first and starts nothing
 * unless every answer is yes.
 *
 * <h2>The conversion: {@link #convert}</h2>
 *
 * <ol>
 *   <li>The Percolator XML is given to the converter as it is, or -- when its PSM ids carry Comet's
 *       absolute {@code -N} base, as every CometGUI run's do -- as a verified copy with the pepXML
 *       directory removed from them (proposed design decision P12-18, {@code PoutForConverter}).
 *   <li>Every pepXML the converter will read is proved the run's own ({@link PepXmlSet}, P12-8).
 *   <li>The installed converter is started with {@code --version} through the process service and
 *       must report the version the Tool Manager offered it as; its JAR is hashed with the one
 *       hasher.
 *   <li>The conversion request is built ({@link LimelightConversionRequest}, whose rules refuse a
 *       decoy choice the Percolator XML contradicts), and the converter runs through {@link
 *       LimelightConverter} on the bundled Java runtime, over the process service, as a logged
 *       stage ({@code logs/limelight-converter.log}). A {@value #CONVERTING} event is written
 *       before the process starts.
 *   <li>The output is judged by its content, never by the exit status (P12-3). A file that passed
 *       is hashed (MD5 and SHA-256, one pass), recorded in a {@value #CONVERTED} event and made
 *       read-only; a conversion that failed leaves nothing at the output path and records a {@value
 *       #FAILED} event with the named failure and its reason.
 * </ol>
 *
 * <h2>Where</h2>
 *
 * <p>Unless the scientist names a file, each conversion writes a new file {@code
 * <run>/outputs/limelight/limelight-<n>.xml}, {@code n} one more than any used before; a named file
 * must be absolute, must not exist, and must be in a writable directory. Nothing is ever
 * overwritten.
 *
 * <h2>The record</h2>
 *
 * <p>Three event types in the run's {@code events.log}, each carrying {@value #RUN_ID}; {@value
 * #TOOL} ({@code limelight-converter}) and {@value #VERSION} (as the converter reports it); {@value
 * #JAR_PATH}, {@value #JAR_MD5}, {@value #JAR_SHA256}; {@value #JAVA}; {@value #ARGV_COUNT} and
 * {@value #ARGV_PREFIX}{@code 0..n-1} after {@link SecretRedactor#redactArgv}; {@value
 * #WORKING_DIRECTORY} and {@value #ENVIRONMENT}; the settings {@value #Q_CUTOFF}, {@value
 * #IMPORT_DECOYS}, {@value #DECOY_PREFIX} (when one is set) and {@value #OPEN_MOD}; {@value
 * #INPUT_COUNT} and, for each file the converter reads, {@code input.<n>.role}, {@code .path},
 * {@code .md5} and {@code .sha256}, the roles {@value #ROLE_COMET_PARAMS}, {@value
 * #ROLE_PERCOLATOR_XML}, {@value #ROLE_PERCOLATOR_XML_FOR_CONVERTER} (the copy, when there is one),
 * {@value #ROLE_PEPXML} for each pepXML and {@value #ROLE_FASTA}; and {@value #OUTPUT_PATH}. A
 * {@value #CONVERTED} event adds {@value #OUTPUT_SIZE}, {@value #OUTPUT_MD5}, {@value
 * #OUTPUT_SHA256}, {@value #OUTPUT_PSMS}, {@value #OUTPUT_REPORTED_PEPTIDES}, {@value
 * #OUTPUT_Q_CUTOFF} (read back from the file), {@value #EXIT_CODE} and the log's {@value
 * #LOG_PATH}, {@value #LOG_MD5}, {@value #LOG_SHA256}; a {@value #FAILED} event adds {@value
 * #FAILURE}, {@value #MESSAGE} and the log's three keys when there is a log. Every value also
 * passes through the event log's own redaction.
 *
 * <h2>A run that is executing</h2>
 *
 * <p>Refused, as Phase 10 refuses an export and Phase 11 a PDV launch: the engine holds the run's
 * event log while it executes, and its results are not final.
 */
public final class LimelightConversions {

    /** The wire name of the event written before the converter starts. */
    public static final String CONVERTING = "limelight.converting";

    /** The wire name of the event written for a conversion that kept its output. */
    public static final String CONVERTED = "limelight.converted";

    /** The wire name of the event written for a conversion that kept nothing. */
    public static final String FAILED = "limelight.unconverted";

    /** The run. */
    public static final String RUN_ID = ProvenanceEvent.RUN_ID_KEY;

    /** The tool: {@code limelight-converter}. */
    public static final String TOOL = ProvenanceEvent.TOOL_KEY;

    /** The version the converter reported when it was started with {@code --version}. */
    public static final String VERSION = ProvenanceEvent.TOOL_VERSION_KEY;

    /** The installed JAR's absolute path. */
    public static final String JAR_PATH = "converter.jar.path";

    /** The installed JAR's MD5. */
    public static final String JAR_MD5 = "converter.jar.md5";

    /** The installed JAR's SHA-256. */
    public static final String JAR_SHA256 = "converter.jar.sha256";

    /** The Java launcher the JAR ran on. */
    public static final String JAVA = "converter.java";

    /** How many elements the argument array has. */
    public static final String ARGV_COUNT = "argv.count";

    /** Each element of the argument array is under this prefix and its 0-based index. */
    public static final String ARGV_PREFIX = "argv.";

    /** The process's working directory. */
    public static final String WORKING_DIRECTORY = "process.working-directory";

    /** The environment the command sets. */
    public static final String ENVIRONMENT = "process.environment";

    /** The Limelight q-value cutoff, as given to {@code -q}. */
    public static final String Q_CUTOFF = "limelight.q-cutoff";

    /** Whether decoys were imported. */
    public static final String IMPORT_DECOYS = "limelight.import-decoys";

    /** The independent-decoy prefix, present only when one was set. */
    public static final String DECOY_PREFIX = "limelight.independent-decoy-prefix";

    /** Whether open-modification mode was on. */
    public static final String OPEN_MOD = "limelight.open-mod";

    /** How many input files are recorded. */
    public static final String INPUT_COUNT = "input.count";

    /** An input's role, path and checksums are under {@code input.<n>.}, {@code n} from 1. */
    public static final String INPUT_PREFIX = "input.";

    /** An input's role: the run's canonical {@code comet.params}. */
    public static final String ROLE_COMET_PARAMS = "comet-params";

    /** An input's role: the run's Percolator XML. */
    public static final String ROLE_PERCOLATOR_XML = "percolator-xml";

    /** An input's role: the copy of the Percolator XML the converter was given (P12-18). */
    public static final String ROLE_PERCOLATOR_XML_FOR_CONVERTER = "percolator-xml-for-converter";

    /** An input's role: one Comet pepXML. */
    public static final String ROLE_PEPXML = "pepxml";

    /** An input's role: the FASTA. */
    public static final String ROLE_FASTA = "fasta";

    /** The Limelight XML's final path. */
    public static final String OUTPUT_PATH = "output.path";

    /** Its size in bytes. */
    public static final String OUTPUT_SIZE = "output.size";

    /** Its MD5. */
    public static final String OUTPUT_MD5 = "output.md5";

    /** Its SHA-256. */
    public static final String OUTPUT_SHA256 = "output.sha256";

    /** How many {@code psm} elements it holds. */
    public static final String OUTPUT_PSMS = "output.psms";

    /** How many {@code reported_peptide} elements it holds. */
    public static final String OUTPUT_REPORTED_PEPTIDES = "output.reported-peptides";

    /** The PSM q-value filter the file carries, as written. */
    public static final String OUTPUT_Q_CUTOFF = "output.q-cutoff";

    /** The converter's exit status. */
    public static final String EXIT_CODE = "process.exit-code";

    /** The converter log's path. */
    public static final String LOG_PATH = "log.path";

    /** The converter log's MD5. */
    public static final String LOG_MD5 = "log.md5";

    /** The converter log's SHA-256. */
    public static final String LOG_SHA256 = "log.sha256";

    /** Why a conversion kept nothing, as a lower-case hyphenated name. */
    public static final String FAILURE = "limelight.failure";

    /** The failure's whole message. */
    public static final String MESSAGE = ProvenanceEvent.MESSAGE_KEY;

    /** {@value #FAILURE} when the converter's process could not be run at all. */
    public static final String COULD_NOT_RUN = "could-not-run";

    /** What a default output's name starts with. */
    public static final String OUTPUT_PREFIX = "limelight-";

    /** What a default output's name ends with. */
    public static final String OUTPUT_SUFFIX = ".xml";

    /** The stage a run's Percolator XML is for. */
    private static final DownstreamStage LIMELIGHT = DownstreamStage.LIMELIGHT_CONVERSION;

    /** {@code percolator.origin} of a registered local binary. */
    private static final String LOCAL = "local";

    /** A {@code percolator.not-emitted.<nn>} entry's option key ends with this. */
    private static final String OPTION_SUFFIX = ".option";

    /** ... and its reason key with this. */
    private static final String REASON_SUFFIX = ".reason";

    /** How long the converter is given to report its version. */
    public static final Duration IDENTITY_TIMEOUT = Duration.ofMinutes(2);

    /** A default output's name, or its staging file's: the number is group 1. */
    private static final Pattern NUMBERED =
            Pattern.compile(
                    Pattern.quote(OUTPUT_PREFIX)
                            + "([1-9][0-9]{0,8})"
                            + Pattern.quote(OUTPUT_SUFFIX)
                            + "(?:"
                            + Pattern.quote(LimelightConverter.STAGING_SUFFIX)
                            + ")?");

    private final LimelightProvisioning provisioning;
    private final ProcessRunner processes;
    private final HashService hashes;
    private final SecretRedactor redactor;
    private final RunMessageSink sink;
    private final Clock clock;
    private final HostPlatform host;
    private final JavaRuntime java;
    private final Duration timeout;
    private final Supplier<Set<RunId>> executing;

    /**
     * The conversions, over the application's one Tool Manager, process service, hasher and secret
     * rule set.
     *
     * @param provisioning the converter through the Tool Manager
     * @param processes the process service, the only thing that starts the converter
     * @param hashes the one hasher
     * @param redactor the one secret rule set
     * @param sink where the converter's log lines are shown as they arrive
     * @param clock dates every event and log line
     * @param host this computer
     * @param java the bundled Java runtime the JAR runs on
     * @param timeout how long one conversion is given ({@link LimelightConverter#DEFAULT_TIMEOUT})
     * @param executing the runs the session's engine is executing now
     */
    public LimelightConversions(
            LimelightProvisioning provisioning,
            ProcessRunner processes,
            HashService hashes,
            SecretRedactor redactor,
            RunMessageSink sink,
            Clock clock,
            HostPlatform host,
            JavaRuntime java,
            Duration timeout,
            Supplier<Set<RunId>> executing) {
        this.provisioning = Objects.requireNonNull(provisioning, "provisioning");
        this.processes = Objects.requireNonNull(processes, "processes");
        this.hashes = Objects.requireNonNull(hashes, "hashes");
        this.redactor = Objects.requireNonNull(redactor, "redactor");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.host = Objects.requireNonNull(host, "host");
        this.java = Objects.requireNonNull(java, "java");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.executing = Objects.requireNonNull(executing, "executing");
    }

    /**
     * The run whose {@code outputs/comet/} holds a run's pepXML: the run itself, or for a
     * Percolator rerun the run it was derived from, beside it in the project's {@code runs/}.
     *
     * @param run the run
     * @param identity its recorded identity
     * @return the run that searched
     */
    public static RunLayout searchedRun(RunLayout run, RunIdentity identity) {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(identity, "identity");
        return identity.derivedFrom()
                .map(source -> new RunLayout(run.root().resolveSibling(source.directoryName())))
                .orElse(run);
    }

    /**
     * Why decoy import cannot be offered for a run, read from its own Percolator XML (P12-7): what
     * the Limelight section shows beside a disabled control, before anyone asks.
     *
     * @param run the run
     * @return the explanation, or empty when its Percolator XML holds decoy PSMs
     * @throws NullPointerException if {@code run} is {@code null}
     */
    public Optional<String> decoyImportUnavailable(RunLayout run) {
        Objects.requireNonNull(run, "run");
        Path pout = RunResultFiles.percolatorXml(run);
        if (!Files.isRegularFile(pout)) {
            return Optional.of(
                    "Limelight decoy import is not available for this run: it has no Percolator"
                            + " XML "
                            + pout
                            + ".");
        }
        try {
            return LimelightConversionRequest.decoyImportUnavailable(PercolatorXmlPsms.read(pout));
        } catch (IOException unreadable) {
            return Optional.of(
                    "Limelight decoy import is not available for this run: its Percolator XML"
                            + " cannot be read: "
                            + unreadable.getMessage());
        }
    }

    /**
     * Saves a run's Limelight settings, refusing a decoy import the run cannot honour (P12-7: at
     * configuration time, not at conversion time). Writes {@code results/limelight-settings.json}
     * and nothing else.
     *
     * @param run the run
     * @param settings the settings
     * @throws LimelightConversionRefusedException if decoy import is asked for and the run's
     *     Percolator XML holds no decoy PSMs; nothing is written
     * @throws IOException if the settings cannot be written, or the run's saved settings cannot be
     *     read
     */
    public void saveSettings(RunLayout run, LimelightSettings settings) throws IOException {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(settings, "settings");
        if (settings.importDecoys()) {
            Optional<String> unavailable = decoyImportUnavailable(run);
            if (unavailable.isPresent()) {
                throw new LimelightConversionRefusedException(
                        List.of(
                                LimelightRefusal.of(
                                        LimelightPrerequisite.DECOY_IMPORT,
                                        RunResultFiles.percolatorXml(run),
                                        unavailable.get())));
            }
        }
        LimelightSettingsStore.write(run, settings);
    }

    /**
     * Whether a run's own Percolator wrote the XML the Limelight converter reads -- read from what
     * the run recorded, never from the Percolator selected now -- and, when it did not, the action
     * offered (P12-9, {@code R-LL-01}, {@code R-LL-02}). Reads and hashes the run's Percolator XML;
     * starts nothing and writes nothing. The same answer {@link #check} gives as {@link
     * LimelightReadiness#compatibility()}, without the other prerequisites.
     *
     * @param run the run
     * @param identity its recorded identity
     * @return the answer
     * @throws IOException if the Percolator XML cannot be hashed for a reason that is not a refusal
     */
    public LimelightCompatibility compatibility(RunLayout run, RunIdentity identity)
            throws IOException {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(identity, "identity");
        String name = "run " + identity.runId().value();
        if (executing.get().contains(identity.runId())) {
            return stillExecuting(identity, name);
        }
        List<LimelightRefusal> problems = new ArrayList<>();
        Optional<ProvenanceManifest> manifest =
                manifest(run, name, problems, LimelightPrerequisite.PERCOLATOR_XML);
        return percolatorXml(identity, name, manifest, problems, RunResultFiles.percolatorXml(run))
                .compatibility();
    }

    /**
     * Whether a run can be converted now with these settings, and if not every reason why. Reads
     * and hashes; starts nothing and writes nothing.
     *
     * @param run the run
     * @param identity its recorded identity
     * @param settings the settings to convert with
     * @param output the file to write, or empty for a new file in {@code outputs/limelight/}
     * @return the answer
     * @throws IOException if a file the checks need cannot be read for a reason that is not a
     *     refusal
     */
    public LimelightReadiness check(
            RunLayout run, RunIdentity identity, LimelightSettings settings, Optional<Path> output)
            throws IOException {
        return assess(run, identity, settings, output).readiness();
    }

    /**
     * Converts a run to Limelight XML with these settings.
     *
     * @param run the run
     * @param identity its recorded identity
     * @param settings the settings to convert with
     * @param output the file to write, or empty for a new file in {@code outputs/limelight/}
     * @return the conversion, its file valid, read-only and recorded
     * @throws LimelightConversionRefusedException if a prerequisite is not met; no converter
     *     process was started, except {@code --version} when the refusal is that the converter
     *     reported another version, and nothing was written to the output path
     * @throws LimelightConversionException if the converter ran and its output was not kept: the
     *     failure is recorded in a {@value #FAILED} event and nothing is at the output path
     * @throws IOException if a file cannot be read, hashed or written, or the event log cannot be
     *     written
     */
    public synchronized LimelightConverted convert(
            RunLayout run, RunIdentity identity, LimelightSettings settings, Optional<Path> output)
            throws IOException {
        Assessment assessed = assess(run, identity, settings, output);
        LimelightReadiness readiness = assessed.readiness();
        if (!readiness.ready()) {
            throw new LimelightConversionRefusedException(readiness.refusals());
        }
        PercolatorXmlPsms psms = assessed.psms().orElseThrow();
        Path target = readiness.output().orElseThrow();
        Optional<Path> copy = Optional.empty();
        PercolatorXmlPsms given = psms;
        if (PoutForConverter.needsCopy(psms)) {
            Path work = Files.createDirectories(run.limelightWorkDirectory());
            Path file = work.resolve(target.getFileName() + ".pout.xml");
            Files.deleteIfExists(file);
            copy = Optional.of(file);
            try {
                given = PoutForConverter.write(psms, readiness.pepXmlDirectory(), file);
            } catch (IOException | IllegalArgumentException notCopied) {
                throw refused(
                        LimelightPrerequisite.PERCOLATOR_XML, psms.file(), notCopied.getMessage());
            }
        }
        try {
            return run(run, identity, settings, assessed, given, copy, target);
        } finally {
            if (copy.isPresent()) {
                Files.deleteIfExists(copy.get());
            }
        }
    }

    private LimelightConverted run(
            RunLayout run,
            RunIdentity identity,
            LimelightSettings settings,
            Assessment assessed,
            PercolatorXmlPsms given,
            Optional<Path> copy,
            Path target)
            throws IOException {
        LimelightReadiness readiness = assessed.readiness();
        LimelightConverterInstallation converter = readiness.converter().orElseThrow();
        PepXmlSet pepXmls;
        try {
            pepXmls =
                    PepXmlSet.verify(
                            given, readiness.pepXmlDirectory(), assessed.recordedPepXml(), hashes);
        } catch (LimelightConversionException notTheRunsOwn) {
            throw refused(
                    LimelightPrerequisite.COMET_PEPXML,
                    notTheRunsOwn.file().orElse(readiness.pepXmlDirectory()),
                    notTheRunsOwn.getMessage());
        }
        ToolVersion reported = identify(converter);
        FileHashes jarHashes = hashes.hash(converter.jar());
        LimelightConversionRequest request;
        try {
            request =
                    new LimelightConversionRequest(
                            readiness.cometParams(),
                            given,
                            pepXmls,
                            readiness.fasta(),
                            target,
                            settings.qCutoff(),
                            settings.importDecoys(),
                            settings.independentDecoyPrefix(),
                            settings.openMod(),
                            false);
        } catch (IllegalArgumentException notAccepted) {
            throw refused(LimelightPrerequisite.OPTIONS, notAccepted.getMessage());
        }
        Files.createDirectories(Objects.requireNonNull(target.getParent(), "output directory"));
        Map<String, String> recorded = new LinkedHashMap<>();
        recorded.put(RUN_ID, identity.runId().value());
        recorded.put(TOOL, ToolName.LIMELIGHT_CONVERTER.id());
        recorded.put(VERSION, reported.text());
        recorded.put(JAR_PATH, converter.jar().toString());
        recorded.put(JAR_MD5, jarHashes.md5());
        recorded.put(JAR_SHA256, jarHashes.sha256());
        recorded.put(JAVA, java.launcher().toString());
        recorded.put(Q_CUTOFF, settings.qCutoff().argument());
        recorded.put(IMPORT_DECOYS, Boolean.toString(settings.importDecoys()));
        settings.independentDecoyPrefix().ifPresent(prefix -> recorded.put(DECOY_PREFIX, prefix));
        recorded.put(OPEN_MOD, Boolean.toString(settings.openMod()));
        List<Input> inputs = new ArrayList<>();
        inputs.add(new Input(ROLE_COMET_PARAMS, readiness.cometParams(), assessed.cometParams()));
        inputs.add(
                new Input(
                        ROLE_PERCOLATOR_XML, readiness.percolatorXml(), assessed.percolatorXml()));
        if (copy.isPresent()) {
            inputs.add(
                    new Input(
                            ROLE_PERCOLATOR_XML_FOR_CONVERTER,
                            copy.get(),
                            hashes.hash(copy.get())));
        }
        for (Map.Entry<String, FileHashes> pepXml : pepXmls.files().entrySet()) {
            inputs.add(
                    new Input(
                            ROLE_PEPXML,
                            readiness.pepXmlDirectory().resolve(pepXml.getKey()),
                            pepXml.getValue()));
        }
        inputs.add(new Input(ROLE_FASTA, readiness.fasta(), assessed.fasta()));
        recorded.put(INPUT_COUNT, Integer.toString(inputs.size()));
        for (int index = 0; index < inputs.size(); index++) {
            Input input = inputs.get(index);
            String key = INPUT_PREFIX + (index + 1) + ".";
            recorded.put(key + "role", input.role());
            recorded.put(key + "path", input.path().toString());
            recorded.put(key + "md5", input.hashes().md5());
            recorded.put(key + "sha256", input.hashes().sha256());
        }
        recorded.put(OUTPUT_PATH, target.toString());
        ConverterLaunch launch =
                new ConverterLaunch(
                        processes, clock, redactor, sink, run, converter.jar(), recorded);
        LimelightConversion conversion;
        try {
            conversion =
                    new LimelightConverter(launch, timeout, java, converter.jar()).convert(request);
        } catch (IOException failed) {
            throw failed(launch, failed, target);
        }
        Path file = conversion.output().file();
        FileHashes outputHashes = hashes.hash(file);
        Optional<Path> log = launch.log();
        Map<String, String> done = new LinkedHashMap<>(launch.payload());
        done.put(OUTPUT_SIZE, Long.toString(Files.size(file)));
        done.put(OUTPUT_MD5, outputHashes.md5());
        done.put(OUTPUT_SHA256, outputHashes.sha256());
        done.put(OUTPUT_PSMS, Integer.toString(conversion.output().psmCount()));
        done.put(
                OUTPUT_REPORTED_PEPTIDES,
                Integer.toString(conversion.output().reportedPeptideCount()));
        done.put(OUTPUT_Q_CUTOFF, conversion.output().psmQValueDefault().orElseThrow());
        done.put(EXIT_CODE, Integer.toString(conversion.outcome().exitCode().orElseThrow()));
        putLog(done, log);
        try {
            launch.append(ProvenanceEventType.LIMELIGHT_CONVERTED, done);
        } catch (IOException notRecorded) {
            Files.deleteIfExists(file);
            throw notRecorded;
        }
        RunResultFiles.makeReadOnly(file);
        return new LimelightConverted(conversion.output(), outputHashes, launch.argv(), log);
    }

    /** The failure of a conversion that was asked for: recorded if it started, then rethrown. */
    private IOException failed(ConverterLaunch launch, IOException failed, Path target)
            throws IOException {
        if (!launch.recorded()) {
            if (failed instanceof LimelightConversionException named) {
                LimelightPrerequisite which =
                        named.failure() == LimelightConversionException.Failure.NO_SCHEMA
                                ? LimelightPrerequisite.CONVERTER
                                : LimelightPrerequisite.OUTPUT;
                return refused(which, named.file().orElse(target), named.getMessage());
            }
            return failed;
        }
        Map<String, String> record = new LinkedHashMap<>(launch.payload());
        record.put(
                FAILURE,
                failed instanceof LimelightConversionException named
                        ? named.failure().name().toLowerCase(Locale.ROOT).replace('_', '-')
                        : COULD_NOT_RUN);
        record.put(MESSAGE, String.valueOf(failed.getMessage()));
        putLog(record, launch.log());
        launch.append(ProvenanceEventType.LIMELIGHT_CONVERSION_FAILED, record);
        return failed;
    }

    private void putLog(Map<String, String> record, Optional<Path> log) throws IOException {
        if (log.isPresent() && Files.isRegularFile(log.get())) {
            FileHashes logHashes = hashes.hash(log.get());
            record.put(LOG_PATH, log.get().toString());
            record.put(LOG_MD5, logHashes.md5());
            record.put(LOG_SHA256, logHashes.sha256());
        }
    }

    /** The installed converter's own report of its version, which must be the offered one. */
    private ToolVersion identify(LimelightConverterInstallation converter)
            throws LimelightConversionRefusedException {
        ToolVersion reported;
        try {
            reported =
                    new LimelightConverterIdentity(
                                    new ToolRunner(processes, IDENTITY_TIMEOUT), java)
                            .identify(ToolName.LIMELIGHT_CONVERTER, host, converter.jar());
        } catch (IOException notIdentified) {
            throw refused(
                    LimelightPrerequisite.CONVERTER,
                    converter.jar(),
                    "the Limelight converter at "
                            + converter.jar()
                            + " could not be identified, so it is not used: "
                            + notIdentified.getMessage());
        }
        if (!reported.equals(converter.version())) {
            throw refused(
                    LimelightPrerequisite.CONVERTER,
                    converter.jar(),
                    "the Limelight converter at "
                            + converter.jar()
                            + " reports version "
                            + reported.text()
                            + ", but the Tool Manager offered it as "
                            + converter.version().text()
                            + "; it is not used");
        }
        return reported;
    }

    // ------------------------------------------------------------------------- the assessment --

    private static String executingMessage(String name) {
        return name
                + " is executing: its results are not final and the workflow engine holds its event"
                + " log. Convert it when the run has ended.";
    }

    private static LimelightCompatibility stillExecuting(RunIdentity identity, String name) {
        return new LimelightCompatibility(
                identity.runId(),
                Optional.empty(),
                Optional.of(executingMessage(name)),
                Optional.empty());
    }

    private Assessment assess(
            RunLayout run, RunIdentity identity, LimelightSettings settings, Optional<Path> output)
            throws IOException {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(output, "output");
        RunLayout searched = searchedRun(run, identity);
        Path params = run.cometParamsFile();
        Path pout = RunResultFiles.percolatorXml(run);
        Path pepXmlDirectory = searched.cometOutputDirectory();
        Path fasta = identity.fasta().path();
        String name = "run " + identity.runId().value();
        List<LimelightRefusal> refusals = new ArrayList<>();
        if (executing.get().contains(identity.runId())) {
            refusals.add(
                    LimelightRefusal.of(LimelightPrerequisite.RUN_ENDED, executingMessage(name)));
            return new Assessment(
                    new LimelightReadiness(
                            identity.runId(),
                            refusals,
                            settings,
                            params,
                            pout,
                            pepXmlDirectory,
                            fasta,
                            Optional.empty(),
                            Optional.of(
                                    "Whether decoys can be imported is decided when the run has"
                                            + " ended."),
                            Optional.empty(),
                            stillExecuting(identity, name)),
                    Optional.empty(),
                    Map.of(),
                    null,
                    null,
                    null);
        }
        FileHashes paramsHashes =
                recorded(
                        refusals,
                        LimelightPrerequisite.COMET_PARAMS,
                        name,
                        "canonical Comet parameter file",
                        params,
                        identity.parameters().hashes().sha256());

        List<LimelightRefusal> manifestProblems = new ArrayList<>();
        Optional<ProvenanceManifest> manifest =
                manifest(run, name, manifestProblems, LimelightPrerequisite.PERCOLATOR_XML);
        refusals.addAll(manifestProblems);
        PoutCheck poutCheck = percolatorXml(identity, name, manifest, manifestProblems, pout);
        poutCheck.refusal().ifPresent(refusals::add);
        FileHashes poutHashes = poutCheck.hashes();
        Optional<PercolatorXmlPsms> psms = Optional.empty();
        if (poutHashes != null) {
            psms = readPercolatorXml(refusals, pout);
        }

        String searchedName =
                identity.derivedFrom().map(source -> "run " + source.runId().value()).orElse(name);
        Optional<ProvenanceManifest> searchManifest =
                identity.isDerived()
                        ? manifest(
                                searched,
                                searchedName,
                                refusals,
                                LimelightPrerequisite.COMET_PEPXML)
                        : manifest;
        if (!identity.isDerived() && manifest.isEmpty()) {
            refusals.add(
                    LimelightRefusal.of(
                            LimelightPrerequisite.COMET_PEPXML,
                            run.provenanceJsonFile(),
                            name
                                    + "'s Comet pepXML cannot be checked against what it recorded,"
                                    + " because its provenance record cannot be read"));
        }
        Map<String, String> recordedPepXml = new TreeMap<>();
        if (searchManifest.isPresent()) {
            recordedPepXml = pepXmls(refusals, searchedName, searchManifest.get(), pepXmlDirectory);
            if (psms.isPresent() && !recordedPepXml.isEmpty()) {
                derivedNames(refusals, psms.get(), pepXmlDirectory, recordedPepXml, searchedName);
            }
        }

        LimelightConverterStatus status = provisioning.status();
        if (status.installation().isEmpty()) {
            refusals.add(
                    LimelightRefusal.of(LimelightPrerequisite.CONVERTER, status.explanation()));
        }

        FileHashes fastaHashes =
                recorded(
                        refusals,
                        LimelightPrerequisite.FASTA,
                        name,
                        "FASTA",
                        fasta,
                        identity.fasta().hashes().sha256());

        Optional<Path> target = output(refusals, run, output);

        Optional<String> decoys =
                psms.isPresent()
                        ? LimelightConversionRequest.decoyImportUnavailable(psms.get())
                        : Optional.of(
                                "Whether decoys can be imported cannot be decided: the run's"
                                        + " Percolator XML could not be read.");
        if (psms.isPresent()) {
            decoyChoice(refusals, settings, psms.get(), decoys);
            prefix(refusals, settings, psms.get(), pepXmlDirectory, params, fasta, target);
        }
        return new Assessment(
                new LimelightReadiness(
                        identity.runId(),
                        refusals,
                        settings,
                        params,
                        pout,
                        pepXmlDirectory,
                        fasta,
                        target,
                        decoys,
                        status.installation(),
                        poutCheck.compatibility()),
                psms,
                recordedPepXml,
                paramsHashes,
                poutHashes,
                fastaHashes);
    }

    /** A file the run recorded: present and unchanged, or a refusal saying which changed. */
    private FileHashes recorded(
            List<LimelightRefusal> refusals,
            LimelightPrerequisite prerequisite,
            String name,
            String what,
            Path file,
            String recordedSha256)
            throws IOException {
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            refusals.add(
                    LimelightRefusal.of(
                            prerequisite,
                            file,
                            "the "
                                    + what
                                    + " "
                                    + file
                                    + " of "
                                    + name
                                    + " does not exist or cannot be read; "
                                    + name
                                    + " recorded SHA-256 "
                                    + recordedSha256));
            return null;
        }
        FileHashes now = hashes.hash(file);
        if (!now.sha256().equalsIgnoreCase(recordedSha256)) {
            refusals.add(
                    LimelightRefusal.of(
                            prerequisite,
                            file,
                            "the "
                                    + what
                                    + " "
                                    + file
                                    + " of "
                                    + name
                                    + " has changed since it was recorded: its SHA-256 is now "
                                    + now.sha256()
                                    + ", but "
                                    + name
                                    + " recorded "
                                    + recordedSha256
                                    + ". It is not converted from (R-RUN-02); rerun the stage"
                                    + " that produced it, or restore the file."));
            return null;
        }
        return now;
    }

    private static Optional<ProvenanceManifest> manifest(
            RunLayout layout,
            String name,
            List<LimelightRefusal> refusals,
            LimelightPrerequisite prerequisite) {
        Path file = layout.provenanceJsonFile();
        if (!Files.isRegularFile(file)) {
            refusals.add(
                    LimelightRefusal.of(
                            prerequisite,
                            file,
                            name
                                    + " has no provenance record "
                                    + file
                                    + ", so what it produced cannot be checked and nothing of it"
                                    + " is converted"));
            return Optional.empty();
        }
        try {
            return Optional.of(ManifestReader.readFrom(file));
        } catch (IOException | RuntimeException unreadable) {
            refusals.add(
                    LimelightRefusal.of(
                            prerequisite,
                            file,
                            name
                                    + "'s provenance record "
                                    + file
                                    + " cannot be read, so nothing of the run is converted: "
                                    + unreadable.getMessage()));
            return Optional.empty();
        }
    }

    /**
     * The run's Percolator XML, read from what the run recorded (P12-9): its hashes when it is
     * there, recorded complete and unchanged; otherwise the refusal, whose words are the
     * compatibility's explanation, and the action offered.
     */
    private PoutCheck percolatorXml(
            RunIdentity identity,
            String name,
            Optional<ProvenanceManifest> manifest,
            List<LimelightRefusal> manifestProblems,
            Path pout)
            throws IOException {
        RunId id = identity.runId();
        if (manifest.isEmpty()) {
            return new PoutCheck(
                    new LimelightCompatibility(
                            id,
                            Optional.empty(),
                            Optional.of(manifestProblems.get(0).message()),
                            Optional.empty()),
                    null,
                    Optional.empty());
        }
        Map<String, String> settings = manifest.get().settings();
        Optional<String> percolator = recordedPercolator(settings);
        FileRecord record = null;
        for (FileRecord file : manifest.get().files()) {
            if (file.direction() == FileDirection.OUTPUT
                    && RunResultFiles.POUT_XML_ROLE.equals(file.role())
                    && file.path().equals(pout)) {
                record = file;
            }
        }
        List<LimelightRefusal> found = new ArrayList<>();
        FileHashes poutHashes = null;
        if (record == null) {
            found.add(
                    LimelightRefusal.of(
                            LimelightPrerequisite.PERCOLATOR_XML,
                            pout,
                            noXml(name, settings, percolator, pout)));
        } else if (record.status() != ProvenanceStatus.COMPLETED) {
            found.add(
                    LimelightRefusal.of(
                            LimelightPrerequisite.PERCOLATOR_XML,
                            pout,
                            "the Percolator XML "
                                    + pout
                                    + " of "
                                    + name
                                    + " is recorded "
                                    + record.status().wireName()
                                    + ", not complete, so it is not converted"));
        } else {
            poutHashes =
                    recorded(
                            found,
                            LimelightPrerequisite.PERCOLATOR_XML,
                            name,
                            "Percolator XML",
                            pout,
                            record.hashes().sha256());
        }
        if (found.isEmpty()) {
            return new PoutCheck(
                    new LimelightCompatibility(id, percolator, Optional.empty(), Optional.empty()),
                    poutHashes,
                    Optional.empty());
        }
        LimelightRefusal refusal = found.get(0);
        return new PoutCheck(
                new LimelightCompatibility(
                        id,
                        percolator,
                        Optional.of(refusal.message()),
                        Optional.of(
                                CompatibilityAction.forStage(
                                        provisioning.percolatorOffers(), LIMELIGHT))),
                null,
                Optional.of(refusal));
    }

    /** The run's recorded Percolator, named as resolution names a build, if it records one. */
    private static Optional<String> recordedPercolator(Map<String, String> settings) {
        String version = settings.get(PercolatorProvenance.VERSION);
        if (version == null) {
            return Optional.empty();
        }
        return Optional.of(
                CompatibilityAction.buildName(
                        version,
                        LOCAL.equals(settings.get(PercolatorProvenance.ORIGIN))
                                ? ToolOrigin.LOCAL
                                : ToolOrigin.MANAGED));
    }

    /**
     * Why a run records no Percolator XML, from its recorded Percolator execution: Phase 09's own
     * sentence for {@code -X} when the command left it out, otherwise the recorded capabilities and
     * what the run recorded about pout XML.
     */
    private static String noXml(
            String name, Map<String, String> settings, Optional<String> percolator, Path pout) {
        if (percolator.isEmpty()) {
            return name
                    + "'s provenance records no Percolator execution, so it has no Percolator XML "
                    + pout
                    + ", which the Limelight converter requires";
        }
        String head =
                name
                        + "'s Percolator "
                        + percolator.get()
                        + " did not write the Percolator XML the Limelight converter requires: ";
        Set<String> recorded =
                Set.of(settings.getOrDefault(PercolatorProvenance.CAPABILITIES, "").split(" "));
        List<String> needed = new ArrayList<>();
        boolean capable = true;
        for (ToolCapability capability : LIMELIGHT.requiredCapabilities()) {
            needed.add(capability.id());
            capable &= recorded.contains(capability.id());
        }
        String capabilities = String.join(" and ", needed);
        if (!capable) {
            return head
                    + notEmittedXml(settings)
                            .orElse(
                                    "its recorded capabilities do not include "
                                            + capabilities
                                            + ", which "
                                            + LIMELIGHT.label()
                                            + " needs ("
                                            + LIMELIGHT.why()
                                            + ")");
        }
        return head
                + "it was observed to have "
                + capabilities
                + ", but the run records no Percolator XML output "
                + pout
                + ", and its record of the pout XML reads: "
                + settings.getOrDefault(PercolatorProvenance.POUT_XML, "nothing");
    }

    /** Phase 09's recorded reason for leaving {@code -X} out of the command, if it did. */
    private static Optional<String> notEmittedXml(Map<String, String> settings) {
        String option = PercolatorOption.XML_OUTPUT.spelling();
        for (Map.Entry<String, String> setting : settings.entrySet()) {
            String key = setting.getKey();
            if (key.startsWith(PercolatorProvenance.NOT_EMITTED_PREFIX)
                    && key.endsWith(OPTION_SUFFIX)
                    && option.equals(setting.getValue())) {
                return Optional.ofNullable(
                        settings.get(
                                key.substring(0, key.length() - OPTION_SUFFIX.length())
                                        + REASON_SUFFIX));
            }
        }
        return Optional.empty();
    }

    private static Optional<PercolatorXmlPsms> readPercolatorXml(
            List<LimelightRefusal> refusals, Path pout) {
        PercolatorXmlPsms psms;
        try {
            psms = PercolatorXmlPsms.read(pout);
        } catch (IOException unreadable) {
            refusals.add(
                    LimelightRefusal.of(
                            LimelightPrerequisite.PERCOLATOR_XML,
                            pout,
                            "the Percolator XML "
                                    + pout
                                    + " cannot be read: "
                                    + unreadable.getMessage()));
            return Optional.empty();
        }
        if (!psms.isPercolatorXml()) {
            refusals.add(
                    LimelightRefusal.of(
                            LimelightPrerequisite.PERCOLATOR_XML,
                            pout,
                            pout
                                    + " is not Percolator XML, so the Limelight converter"
                                    + " cannot read it"));
            return Optional.empty();
        }
        if (psms.psmIds().isEmpty()) {
            refusals.add(
                    LimelightRefusal.of(
                            LimelightPrerequisite.PERCOLATOR_XML,
                            pout,
                            "the Percolator XML "
                                    + pout
                                    + " holds no PSM, so there is nothing to convert"));
            return Optional.empty();
        }
        return Optional.of(psms);
    }

    /** The searched run's recorded pepXML, each present and unchanged: file name to SHA-256. */
    private Map<String, String> pepXmls(
            List<LimelightRefusal> refusals,
            String name,
            ProvenanceManifest manifest,
            Path directory)
            throws IOException {
        Map<String, String> recorded = new TreeMap<>();
        for (FileRecord file : manifest.files()) {
            if (file.direction() == FileDirection.OUTPUT
                    && RunResultFiles.PEP_XML_ROLE.equals(file.role())
                    && file.status() == ProvenanceStatus.COMPLETED
                    && directory.equals(file.path().getParent())
                    && file.path().getFileName() != null) {
                recorded.put(String.valueOf(file.path().getFileName()), file.hashes().sha256());
            }
        }
        if (recorded.isEmpty()) {
            refusals.add(
                    LimelightRefusal.of(
                            LimelightPrerequisite.COMET_PEPXML,
                            directory,
                            name
                                    + "'s provenance records no complete Comet pepXML output in "
                                    + directory
                                    + ", so there is no search for the converter to read"));
            return recorded;
        }
        for (Map.Entry<String, String> pepXml : recorded.entrySet()) {
            recorded(
                    refusals,
                    LimelightPrerequisite.COMET_PEPXML,
                    name,
                    "Comet pepXML",
                    directory.resolve(pepXml.getKey()),
                    pepXml.getValue());
        }
        return recorded;
    }

    /** Every pepXML the converter will derive from the PSM ids must be one the search recorded. */
    private static void derivedNames(
            List<LimelightRefusal> refusals,
            PercolatorXmlPsms psms,
            Path directory,
            Map<String, String> recorded,
            String name) {
        List<String> ids;
        try {
            ids = PoutForConverter.converterIds(psms, directory);
        } catch (IllegalArgumentException outside) {
            refusals.add(
                    LimelightRefusal.of(
                            LimelightPrerequisite.COMET_PEPXML, psms.file(), outside.getMessage()));
            return;
        }
        Set<String> names = new TreeSet<>();
        for (String id : ids) {
            try {
                names.add(PepXmlSet.expectedFileName(id));
            } catch (IllegalArgumentException underivable) {
                refusals.add(
                        LimelightRefusal.of(
                                LimelightPrerequisite.COMET_PEPXML,
                                psms.file(),
                                "in " + psms.file() + ": " + underivable.getMessage()));
                return;
            }
        }
        for (String file : names) {
            if (!recorded.containsKey(file)) {
                refusals.add(
                        LimelightRefusal.of(
                                LimelightPrerequisite.COMET_PEPXML,
                                directory.resolve(file),
                                "the Limelight converter would read "
                                        + directory.resolve(file)
                                        + ", derived from the Percolator PSM ids, and it is not"
                                        + " one of "
                                        + name
                                        + "'s recorded Comet outputs ("
                                        + String.join(", ", recorded.keySet())
                                        + ")"));
            }
        }
    }

    /** The output: a usable new file, or a refusal. */
    private static Optional<Path> output(
            List<LimelightRefusal> refusals, RunLayout run, Optional<Path> requested)
            throws IOException {
        if (requested.isPresent()) {
            Path file = requested.get();
            if (!file.isAbsolute() || file.getFileName() == null) {
                refusals.add(
                        LimelightRefusal.of(
                                LimelightPrerequisite.OUTPUT,
                                file,
                                "the Limelight XML file must be named by an absolute path, but was "
                                        + file));
                return Optional.empty();
            }
            Path staging = LimelightConverter.stagingPath(file);
            for (Path taken : List.of(file, staging)) {
                if (Files.exists(taken, LinkOption.NOFOLLOW_LINKS)) {
                    refusals.add(
                            LimelightRefusal.of(
                                    LimelightPrerequisite.OUTPUT,
                                    taken,
                                    "something is already at "
                                            + taken
                                            + ", and a Limelight conversion never overwrites a"
                                            + " file"));
                    return Optional.empty();
                }
            }
            Path directory = file.getParent();
            if (directory == null || !Files.isDirectory(directory)) {
                refusals.add(
                        LimelightRefusal.of(
                                LimelightPrerequisite.OUTPUT,
                                file,
                                "the directory of the Limelight XML file "
                                        + file
                                        + " does not exist"));
                return Optional.empty();
            }
            if (!Files.isWritable(directory)) {
                refusals.add(
                        LimelightRefusal.of(
                                LimelightPrerequisite.OUTPUT,
                                directory,
                                "the directory " + directory + " cannot be written to"));
                return Optional.empty();
            }
            return Optional.of(file);
        }
        Path directory = run.limelightOutputDirectory();
        Path writable = directory;
        while (writable != null && !Files.exists(writable, LinkOption.NOFOLLOW_LINKS)) {
            writable = writable.getParent();
        }
        if (writable == null || !Files.isDirectory(writable) || !Files.isWritable(writable)) {
            refusals.add(
                    LimelightRefusal.of(
                            LimelightPrerequisite.OUTPUT,
                            directory,
                            "the Limelight output directory "
                                    + directory
                                    + " cannot be written to"
                                    + (writable == null || writable.equals(directory)
                                            ? ""
                                            : " (" + writable + " cannot be written to)")));
            return Optional.empty();
        }
        return Optional.of(directory.resolve(OUTPUT_PREFIX + next(directory) + OUTPUT_SUFFIX));
    }

    /** One more than any number a default output or its staging file in the directory has. */
    private static int next(Path directory) throws IOException {
        int highest = 0;
        if (Files.isDirectory(directory)) {
            try (Stream<Path> listed = Files.list(directory)) {
                for (Path path : listed.toList()) {
                    Matcher numbered = NUMBERED.matcher(String.valueOf(path.getFileName()));
                    if (numbered.matches()) {
                        highest = Math.max(highest, Integer.parseInt(numbered.group(1)));
                    }
                }
            }
        }
        return highest + 1;
    }

    /** The decoy choice against the run's own Percolator output (P12-7). */
    private static void decoyChoice(
            List<LimelightRefusal> refusals,
            LimelightSettings settings,
            PercolatorXmlPsms psms,
            Optional<String> unavailable) {
        if (settings.importDecoys() && unavailable.isPresent()) {
            refusals.add(
                    LimelightRefusal.of(
                            LimelightPrerequisite.DECOY_IMPORT, psms.file(), unavailable.get()));
        } else if (!settings.importDecoys() && psms.decoyPsmCount() > 0) {
            refusals.add(
                    LimelightRefusal.of(
                            LimelightPrerequisite.DECOY_IMPORT,
                            psms.file(),
                            "the Percolator XML "
                                    + psms.file()
                                    + " holds "
                                    + psms.decoyPsmCount()
                                    + " decoy PSMs, and the converter fails on such a file unless"
                                    + " decoy import is selected"));
        }
    }

    /**
     * The independent-decoy prefix, by the conversion request's own rule: a request is made with
     * every other value consistent, and only the prefix left to refuse.
     */
    private static void prefix(
            List<LimelightRefusal> refusals,
            LimelightSettings settings,
            PercolatorXmlPsms psms,
            Path pepXmlDirectory,
            Path params,
            Path fasta,
            Optional<Path> target) {
        if (settings.independentDecoyPrefix().isEmpty()) {
            return;
        }
        PepXmlSet placeholder =
                new PepXmlSet(
                        psms.file(),
                        pepXmlDirectory,
                        Map.of(
                                "placeholder" + PepXmlSet.EXTENSION,
                                new FileHashes("0".repeat(32), "0".repeat(64))));
        try {
            new LimelightConversionRequest(
                    params,
                    psms,
                    placeholder,
                    fasta,
                    target.orElse(pepXmlDirectory.resolve("placeholder.xml")),
                    settings.qCutoff(),
                    psms.decoyPsmCount() > 0,
                    settings.independentDecoyPrefix(),
                    settings.openMod(),
                    false);
        } catch (IllegalArgumentException refused) {
            refusals.add(LimelightRefusal.of(LimelightPrerequisite.OPTIONS, refused.getMessage()));
        }
    }

    private static LimelightConversionRefusedException refused(
            LimelightPrerequisite prerequisite, Path file, String message) {
        return new LimelightConversionRefusedException(
                List.of(LimelightRefusal.of(prerequisite, file, message)));
    }

    private static LimelightConversionRefusedException refused(
            LimelightPrerequisite prerequisite, String message) {
        return new LimelightConversionRefusedException(
                List.of(LimelightRefusal.of(prerequisite, message)));
    }

    /**
     * What the run's Percolator XML came to: the compatibility, its hashes when it can be read
     * ({@code null} otherwise), and the refusal when it cannot.
     */
    private record PoutCheck(
            LimelightCompatibility compatibility,
            FileHashes hashes,
            Optional<LimelightRefusal> refusal) {}

    /** One file the converter reads, and what it is. */
    private record Input(String role, Path path, FileHashes hashes) {}

    /**
     * The answer, and what {@link #convert} needs of the reading behind it; the hashes are {@code
     * null} where the file was refused.
     */
    private record Assessment(
            LimelightReadiness readiness,
            Optional<PercolatorXmlPsms> psms,
            Map<String, String> recordedPepXml,
            FileHashes cometParams,
            FileHashes percolatorXml,
            FileHashes fasta) {}
}
