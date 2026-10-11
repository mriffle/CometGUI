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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.project.ProjectId;
import org.cometgui.domain.run.ArchivedFile;
import org.cometgui.domain.run.DatabaseDelivery;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RecordedInput;
import org.cometgui.domain.run.RunDerivation;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallProgressListener;
import org.cometgui.domain.tools.NoManagedBuild;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.events.ProvenanceEventLogReader;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.provenance.manifest.ApplicationRecord;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ManifestWriter;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.provenance.manifest.RunRecord;
import org.cometgui.tools.api.JavaRuntime;
import org.cometgui.tools.limelight.LimelightConverter;
import org.cometgui.workflow.steps.RunResultFiles;

/**
 * A finished run laid out on disk the way the workflow leaves one -- its {@code comet.params}, its
 * Comet pepXML, its Percolator XML whose PSM ids carry Comet's absolute {@code -N} base, its FASTA
 * and a {@code provenance.json} recording each output's checksums -- plus the converter behind the
 * Tool Manager and the process seam.
 *
 * <p>The converter's JAR is the real pinned v2.8.1 artefact, copied from {@code
 * scratch/phase05/artefacts} and held to its SHA-256, because the conversion reads the schema it
 * validates against out of that JAR. A scripted converter that "writes a valid file" copies the
 * real known-good Limelight XML Phase 00 converted ({@code scratch/scientific-path/limelight}),
 * held to its SHA-256, with the PSM q-value filter set to the {@code -q} it was given -- as the
 * real converter does. A missing or changed file FAILS the test that needs it; nothing skips.
 */
final class LimelightFixtures {

    /** The converter release the manifest pins. */
    static final String VERSION = "2.8.1";

    /** The pinned JAR, relative to the repository root. */
    static final String JAR = "scratch/phase05/artefacts/v2.8.1__cometPercolator2LimelightXML.jar";

    /** Its SHA-256, as {@code manifests/tools.json} pins it. */
    static final String JAR_SHA256 =
            "843573396ce0654a0ac81582b378c496923e49dde71f40d750d890947774ece1";

    /** Phase 00's real conversion of the K562 fixture, relative to the repository root. */
    static final String KNOWN_GOOD = "scratch/scientific-path/limelight/limelight.xml";

    /** Its SHA-256. */
    static final String KNOWN_GOOD_SHA256 =
            "25c218a8f5658ea19273703517040c823530b1c5890681b0e231f001408c0a11";

    /** The PSM q-value annotation as the known-good file carries it, at the default cutoff. */
    static final String PSM_Q_ANNOTATION =
            "<filterable_psm_annotation_type name=\"q-value\" description=\"Q-value\""
                    + " filter_direction=\"below\" default_filter_value=\"0.01\"/>";

    static final String RUN_ID = "run-0001";

    static final Instant CREATED = Instant.parse("2026-10-11T08:00:00Z");

    static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-11T09:00:00Z"), ZoneOffset.UTC);

    private LimelightFixtures() {}

    /** The repository root: the nearest directory up from here holding {@code manifests}. */
    static Path repositoryRoot() {
        Path here = Path.of("").toAbsolutePath();
        for (Path at = here; at != null; at = at.getParent()) {
            if (Files.isDirectory(at.resolve("manifests"))) {
                return at;
            }
        }
        throw new AssertionError("no manifests directory above " + here);
    }

    /**
     * A file under the repository, held to its pinned SHA-256.
     *
     * @throws AssertionError naming the file and how to refill it, if it is missing or changed
     */
    static Path pinned(String relative, String sha256) throws IOException {
        Path file = repositoryRoot().resolve(relative);
        if (!Files.isRegularFile(file)) {
            throw new AssertionError(
                    file
                            + " is missing. It is gitignored scratch: refill it as Phase 05"
                            + " (artefacts) and Phase 00 (scientific-path) describe.");
        }
        String actual = sha256(file);
        if (!sha256.equals(actual)) {
            throw new AssertionError(file + " has SHA-256 " + actual + ", pinned " + sha256);
        }
        return file;
    }

    /** The pinned converter JAR, copied into a directory of its own (its working directory). */
    static Path converterJar(Path directory) throws IOException {
        Path source = pinned(JAR, JAR_SHA256);
        Files.createDirectories(directory);
        Path jar = directory.resolve("cometPercolator2LimelightXML.jar").toAbsolutePath();
        Files.copy(source, jar, StandardCopyOption.REPLACE_EXISTING);
        return jar;
    }

    static String sha256(Path file) throws IOException {
        return digest(file, "SHA-256");
    }

    static String md5(Path file) throws IOException {
        return digest(file, "MD5");
    }

    /** A digest computed here, with the JDK, sharing nothing with the product's hasher. */
    static String digest(Path file, String algorithm) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[1 << 16];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }

    /** One PSM of a constructed pout XML. */
    record Psm(String base, int scan, boolean decoy) {}

    /**
     * A Percolator 3.07.1-shaped pout XML: every PSM in {@code psms}, one peptide referencing all
     * of them, ids {@code <idPrefix><base>_<scan>_2_1}.
     */
    static String pout(String idPrefix, List<Psm> psms, boolean withDecoyAttribute) {
        StringBuilder xml =
                new StringBuilder(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                                + "<percolator_output"
                                + " xmlns=\"http://per-colator.com/percolator_out/15\""
                                + " xmlns:p=\"http://per-colator.com/percolator_out/15\""
                                + " p:majorVersion=\"3\" p:minorVersion=\"07\">\n"
                                + "  <process_info><command_line>percolator -X x</command_line>"
                                + "</process_info>\n  <psms>\n");
        for (Psm psm : psms) {
            xml.append("    <psm p:psm_id=\"")
                    .append(idPrefix)
                    .append(psm.base())
                    .append('_')
                    .append(psm.scan())
                    .append("_2_1\"")
                    .append(withDecoyAttribute ? " p:decoy=\"" + psm.decoy() + "\"" : "")
                    .append("><svm_score>1.0</svm_score><q_value>0.001</q_value></psm>\n");
        }
        xml.append(
                "  </psms>\n  <peptides>\n    <peptide p:peptide_id=\"PEPTIDEK\">" + "<psm_ids>");
        for (Psm psm : psms) {
            xml.append("<psm_id>")
                    .append(idPrefix)
                    .append(psm.base())
                    .append('_')
                    .append(psm.scan())
                    .append("_2_1</psm_id>");
        }
        xml.append("</psm_ids></peptide>\n  </peptides>\n</percolator_output>\n");
        return xml.toString();
    }

    /** The three target PSMs of every scripted run, over two spectrum files. */
    static final List<Psm> TARGETS =
            List.of(
                    new Psm("k562_3", 101, false),
                    new Psm("k562_3", 102, false),
                    new Psm("k562_4", 201, false));

    /** A finished run, and what it recorded. */
    record FinishedRun(
            Path project, RunLayout layout, RunIdentity identity, Path fasta, Path spectra) {

        Path pout() {
            return RunResultFiles.percolatorXml(layout);
        }

        Path pepXml(String base) {
            return layout.pepXmlFile(base);
        }

        /** The events of the run's log, oldest first; empty without a log. */
        List<ProvenanceEvent> events() throws IOException {
            if (!Files.exists(layout.eventLogFile())) {
                return List.of();
            }
            return ProvenanceEventLogReader.recover(
                            layout.eventLogFile(), SecretRedactor.patternsOnly())
                    .events();
        }

        List<ProvenanceEvent> events(ProvenanceEventType type) throws IOException {
            return events().stream().filter(event -> event.type() == type).toList();
        }
    }

    /**
     * A finished run whose search ran over two spectrum files and whose Percolator wrote XML.
     *
     * @param root an empty directory
     * @param psms the PSMs its Percolator XML holds
     * @param withDecoyAttribute whether Percolator ran with {@code -Z} (every PSM says p:decoy)
     */
    static FinishedRun finishedRun(Path root, List<Psm> psms, boolean withDecoyAttribute)
            throws IOException {
        Path project = root.resolve("project").toAbsolutePath();
        RunLayout layout =
                new RunLayout(
                        project.resolve("runs")
                                .resolve(RunLayout.directoryName(CREATED, new RunId(RUN_ID))));
        for (Path directory : layout.directories()) {
            Files.createDirectories(directory);
        }
        Path inputs = Files.createDirectories(root.resolve("inputs").toAbsolutePath());
        Path spectra3 = Files.writeString(inputs.resolve("k562_3.mzML"), "<mzML/>");
        Path spectra4 = Files.writeString(inputs.resolve("k562_4.mzML"), "<mzML/>");
        Path fasta = Files.writeString(inputs.resolve("db.fasta"), ">sp|P1|A\nPEPTIDEK\n");
        Path params =
                Files.writeString(
                        layout.cometParamsFile(), "# comet_version 2026.03.0\ndecoy_search = 1\n");
        Files.writeString(
                layout.pepXmlFile("k562_3"), "<msms_pipeline_analysis>3</msms_pipeline_analysis>");
        Files.writeString(
                layout.pepXmlFile("k562_4"), "<msms_pipeline_analysis>4</msms_pipeline_analysis>");
        Path pout = RunResultFiles.percolatorXml(layout);
        Files.createDirectories(RunResultFiles.percolatorOutputDirectory(layout));
        Files.writeString(
                pout,
                pout(layout.cometOutputDirectory().toString() + "/", psms, withDecoyAttribute),
                StandardCharsets.UTF_8);
        RunIdentity identity =
                new RunIdentity(
                        new RunId(RUN_ID),
                        new ProjectId("cometgui-default"),
                        CREATED,
                        "2026.03.0",
                        RunIdentity.spectraOf(List.of(recorded(spectra3), recorded(spectra4))),
                        recorded(fasta),
                        new ArchivedFile(
                                RunLayout.cometParamsRelativePath(),
                                Files.size(params),
                                hashes(params)),
                        IndexMode.NONE,
                        DatabaseDelivery.PARAMETER_FILE);
        writeManifest(
                layout,
                List.of(
                        output(RunResultFiles.PEP_XML_ROLE, layout.pepXmlFile("k562_3")),
                        output(RunResultFiles.PEP_XML_ROLE, layout.pepXmlFile("k562_4")),
                        output(RunResultFiles.POUT_XML_ROLE, pout)));
        return new FinishedRun(project, layout, identity, fasta, spectra3);
    }

    /**
     * A Percolator rerun of {@code source}: its own directory and Percolator XML, the source's
     * search, and {@code derivedFrom} naming the source.
     */
    static FinishedRun derivedRun(FinishedRun source, List<Psm> psms) throws IOException {
        RunId id = new RunId("run-0002");
        Instant created = CREATED.plusSeconds(3600);
        RunLayout layout =
                new RunLayout(
                        source.layout()
                                .root()
                                .resolveSibling(RunLayout.directoryName(created, id)));
        for (Path directory : layout.directories()) {
            Files.createDirectories(directory);
        }
        Files.copy(source.layout().cometParamsFile(), layout.cometParamsFile());
        Path pout = RunResultFiles.percolatorXml(layout);
        Files.createDirectories(RunResultFiles.percolatorOutputDirectory(layout));
        Files.writeString(
                pout,
                pout(source.layout().cometOutputDirectory().toString() + "/", psms, false),
                StandardCharsets.UTF_8);
        RunIdentity base = source.identity();
        RunIdentity identity =
                new RunIdentity(
                        id,
                        base.projectId(),
                        created,
                        base.cometRelease(),
                        base.spectra(),
                        base.fasta(),
                        base.parameters(),
                        base.indexMode(),
                        base.databaseDelivery(),
                        Optional.of(
                                new RunDerivation(
                                        base.runId(),
                                        base.created(),
                                        new ArchivedFile(
                                                RunLayout.provenanceJsonRelativePath(),
                                                1,
                                                hashes(source.layout().provenanceJsonFile())),
                                        new ArchivedFile(
                                                RunLayout.mergedPinRelativePath(),
                                                1,
                                                new FileHashes("0".repeat(32), "0".repeat(64))))));
        writeManifest(layout, List.of(output(RunResultFiles.POUT_XML_ROLE, pout)));
        return new FinishedRun(
                source.project(), layout, identity, source.fasta(), source.spectra());
    }

    static RecordedInput recorded(Path file) throws IOException {
        return new RecordedInput(file, Files.size(file), CREATED, hashes(file));
    }

    static FileHashes hashes(Path file) throws IOException {
        return new FileHashes(md5(file), sha256(file));
    }

    static FileRecord output(String role, Path file) throws IOException {
        return new FileRecord(
                FileDirection.OUTPUT,
                role,
                file,
                Files.size(file),
                CREATED,
                hashes(file),
                ProvenanceStatus.COMPLETED);
    }

    static void writeManifest(RunLayout layout, List<FileRecord> files) throws IOException {
        ManifestWriter.redactingWith(SecretRedactor.patternsOnly())
                .writeTo(
                        layout.provenanceJsonFile(),
                        ProvenanceManifest.current(
                                new RunRecord(
                                        new RunId(
                                                String.valueOf(layout.root().getFileName())
                                                        .substring(17)),
                                        "cometgui-default",
                                        ProvenanceStatus.COMPLETED,
                                        CREATED,
                                        Optional.of(CREATED.plusSeconds(60))),
                                ApplicationRecord.capture("0.1.0-SNAPSHOT", "test"),
                                Map.of(),
                                List.of(),
                                files));
    }

    /** A Tool Manager offering one converter build, as it is given. */
    static final class Converters implements ToolManager {

        private volatile List<ToolOffer> offers;

        private final InstallHandle handle = () -> {};

        private final List<String> installs = Collections.synchronizedList(new ArrayList<>());

        List<String> installs() {
            return List.copyOf(installs);
        }

        Converters(ToolOffer... offers) {
            this.offers = List.of(offers);
        }

        void offer(ToolOffer... now) {
            offers = List.of(now);
        }

        @Override
        public List<ToolOffer> offers() {
            return offers;
        }

        @Override
        public List<NoManagedBuild> noManagedBuild() {
            return List.of();
        }

        @Override
        public InstallHandle install(
                ToolName tool, ToolVersion version, InstallProgressListener listener) {
            installs.add(tool.id() + " " + version.text());
            return handle;
        }

        InstallHandle handle() {
            return handle;
        }

        @Override
        public ToolOffer registerLocalBinary(ToolName tool, Path executable) {
            throw new UnsupportedOperationException();
        }
    }

    static ToolOffer installed(String version, Path jar) {
        return new ToolOffer(
                ToolName.LIMELIGHT_CONVERTER,
                ToolVersion.parse(version),
                ToolOrigin.MANAGED,
                ToolInstallState.INSTALLED,
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.of(jar),
                OptionalLong.of(2_762_075L));
    }

    static ToolOffer converter(String version, ToolInstallState state) {
        boolean fetchable = state != ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM;
        return new ToolOffer(
                ToolName.LIMELIGHT_CONVERTER,
                ToolVersion.parse(version),
                ToolOrigin.MANAGED,
                state,
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                fetchable ? OptionalLong.of(2_762_075L) : OptionalLong.empty());
    }

    /** What the scripted converter does with a conversion. */
    enum Writes {
        /** The known-good Limelight XML, with -q's value as its PSM q-value filter; exit 0. */
        VALID,
        /** Nothing at all; exit 0. */
        NOTHING,
        /** A file of zero bytes; exit 0. */
        EMPTY,
        /** Nothing; exit 1. */
        EXITS_ONE,
        /** The process cannot be started. */
        CANNOT_START,
        /** Writes nothing and never exits until it is asked to stop; then exits 143. */
        HANGS
    }

    /**
     * The converter behind the process seam: the recording runner every refusal is proved against.
     * It answers {@code --version} with the version it is told to report, and records every start
     * -- with how many conversion events the run's log held at that moment, and the PSM ids of the
     * Percolator XML it was given.
     */
    static final class ScriptedConverter implements ProcessRunner {

        private final Writes writes;
        private final String reports;
        private final RunLayout run;
        private final List<ToolCommand> starts = Collections.synchronizedList(new ArrayList<>());
        private final List<Integer> eventsAtStart = Collections.synchronizedList(new ArrayList<>());
        private final List<String> givenPout = Collections.synchronizedList(new ArrayList<>());

        List<ToolCommand> starts() {
            return List.copyOf(starts);
        }

        List<Integer> eventsAtStart() {
            return List.copyOf(eventsAtStart);
        }

        List<String> givenPout() {
            return List.copyOf(givenPout);
        }

        ScriptedConverter(Writes writes, String reports, RunLayout run) {
            this.writes = writes;
            this.reports = reports;
            this.run = run;
        }

        List<ToolCommand> conversions() {
            return starts.stream()
                    .filter(command -> !command.argv().contains("--version"))
                    .toList();
        }

        @Override
        public RunningProcess start(ToolCommand command, ProcessListener listener)
                throws IOException {
            starts.add(command);
            List<String> argv = command.argv();
            if (argv.contains("--version")) {
                listener.onStandardOutput("cometPercolator2LimelightXML.jar v" + reports);
                listener.onExit(0);
                return exited(0);
            }
            eventsAtStart.add(
                    Files.exists(run.eventLogFile())
                            ? ProvenanceEventLogReader.recover(
                                            run.eventLogFile(), SecretRedactor.patternsOnly())
                                    .events()
                                    .size()
                            : 0);
            Path given = Path.of(after(argv, "-p"));
            givenPout.add(Files.readString(given, StandardCharsets.UTF_8));
            if (writes == Writes.CANNOT_START) {
                throw new IOException("the stand-in for the converter cannot be started");
            }
            Path out = Path.of(after(argv, "-o"));
            listener.onStandardError("Error printing runtime information.");
            listener.onStandardOutput("Reading comet params into memory... Done.");
            if (writes == Writes.HANGS) {
                return new RunningProcess() {
                    private boolean alive = true;

                    @Override
                    public synchronized boolean isAlive() {
                        return alive;
                    }

                    @Override
                    public int waitForExit() {
                        return 143;
                    }

                    @Override
                    public synchronized void requestCancellation() {
                        if (alive) {
                            alive = false;
                            listener.onExit(143);
                        }
                    }
                };
            }
            int exit = 0;
            switch (writes) {
                case VALID -> {
                    String good =
                            Files.readString(
                                    pinned(KNOWN_GOOD, KNOWN_GOOD_SHA256), StandardCharsets.UTF_8);
                    if (!good.contains(PSM_Q_ANNOTATION)) {
                        throw new AssertionError("the known-good file's q annotation moved");
                    }
                    Files.writeString(
                            out,
                            good.replace(
                                    PSM_Q_ANNOTATION,
                                    PSM_Q_ANNOTATION.replace(
                                            "default_filter_value=\"0.01\"",
                                            "default_filter_value=\"" + after(argv, "-q") + "\"")),
                            StandardCharsets.UTF_8);
                    listener.onStandardOutput("Validating Limelight XML... Done.");
                }
                case EMPTY -> Files.createFile(out);
                case EXITS_ONE -> exit = 1;
                default -> {
                    // NOTHING: writes nothing and exits 0, as the real converter does on a bad -q
                }
            }
            listener.onExit(exit);
            return exited(exit);
        }

        static String after(List<String> argv, String option) {
            int at = argv.indexOf(option);
            if (at < 0 || at + 1 >= argv.size()) {
                throw new AssertionError(option + " is not in " + argv);
            }
            return argv.get(at + 1);
        }

        private static RunningProcess exited(int code) {
            return new RunningProcess() {
                @Override
                public boolean isAlive() {
                    return false;
                }

                @Override
                public int waitForExit() {
                    return code;
                }

                @Override
                public void requestCancellation() {
                    // already exited
                }
            };
        }
    }

    /** The runs said to be executing, changeable by a test. */
    static final Set<RunId> EXECUTING = new CopyOnWriteArraySet<>();

    /** The conversions over a scripted converter and a Tool Manager. */
    static LimelightConversions conversions(ToolManager tools, ProcessRunner processes)
            throws IOException {
        return conversions(tools, processes, LimelightConverter.DEFAULT_TIMEOUT, message -> {});
    }

    /** The same, with a timeout and a console of the test's own. */
    static LimelightConversions conversions(
            ToolManager tools,
            ProcessRunner processes,
            java.time.Duration timeout,
            org.cometgui.tools.process.RunMessageSink sink)
            throws IOException {
        return new LimelightConversions(
                new LimelightProvisioning(tools),
                processes,
                new StreamingHashService(),
                SecretRedactor.patternsOnly(),
                sink,
                CLOCK,
                LINUX,
                JavaRuntime.ofThisApplication(),
                timeout,
                () -> Set.copyOf(EXECUTING));
    }

    /** One event's argument array, rebuilt from its numbered keys. */
    static List<String> argv(ProvenanceEvent event) {
        Map<String, String> payload = event.payload();
        int count = Integer.parseInt(payload.get(LimelightConversions.ARGV_COUNT));
        List<String> argv = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            argv.add(payload.get(LimelightConversions.ARGV_PREFIX + index));
        }
        return argv;
    }

    /** One event's recorded inputs: path to "role md5 sha256". */
    static Map<String, String> inputs(ProvenanceEvent event) {
        Map<String, String> payload = event.payload();
        int count = Integer.parseInt(payload.get(LimelightConversions.INPUT_COUNT));
        Map<String, String> inputs = new LinkedHashMap<>();
        for (int n = 1; n <= count; n++) {
            String key = LimelightConversions.INPUT_PREFIX + n + ".";
            inputs.put(
                    payload.get(key + "path"),
                    payload.get(key + "role")
                            + " "
                            + payload.get(key + "md5")
                            + " "
                            + payload.get(key + "sha256"));
        }
        return inputs;
    }
}
