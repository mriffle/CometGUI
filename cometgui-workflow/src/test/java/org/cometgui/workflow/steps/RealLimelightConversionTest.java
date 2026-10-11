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

package org.cometgui.workflow.steps;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallProgressListener;
import org.cometgui.domain.tools.NoManagedBuild;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.DownstreamStage;
import org.cometgui.params.percolator.resolution.PercolatorResolver;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.events.ProvenanceEventLogReader;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.tools.api.JavaRuntime;
import org.cometgui.tools.limelight.LimelightConverter;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunRequest;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepStateListener;
import org.cometgui.workflow.limelight.LimelightConversions;
import org.cometgui.workflow.limelight.LimelightConverted;
import org.cometgui.workflow.limelight.LimelightProvisioning;
import org.cometgui.workflow.limelight.LimelightReadiness;
import org.cometgui.workflow.storage.LimelightSettings;
import org.cometgui.workflow.storage.LimelightSettingsStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.SAXException;

/**
 * Phase 12 gate item 1 and {@code AC-LL-02}, and gate item 5's model half, against a REAL CometGUI
 * run: Comet 2026.03.0 on the two K562 mzML and the proteome's first 1000 records ({@code
 * decoy_search = 1}), then Percolator 3.07.1 with Limelight conversion enabled, through the real
 * engine and the real process service -- the inputs of {@code RealPercolatorRunTest} -- then the
 * REAL pinned converter v2.8.1 through {@link LimelightConversions}, twice.
 *
 * <p>Everything asserted is computed here independently: the Limelight XML is validated by this
 * test against the {@code limelight-xml.xsd} it reads out of the pinned JAR itself; its {@code psm}
 * elements are counted with this test's own StAX reader against the target PSM rows of the run's
 * own {@code psms.tsv}, counted here; and its MD5 and SHA-256 are this test's own digests.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet and Percolator binaries have ever been executed in"
                        + " this project")
class RealLimelightConversionTest {

    private static final String CONVERTER_JAR =
            "scratch/phase05/artefacts/v2.8.1__cometPercolator2LimelightXML.jar";

    private static final String CONVERTER_SHA256 =
            "843573396ce0654a0ac81582b378c496923e49dde71f40d750d890947774ece1";

    @TempDir private static Path scratch;

    private static RealProject project;

    private static PreparedRun prepared;

    private static RunResult result;

    private static RunIdentity identity;

    private static Path jar;

    private static LimelightConversions conversions;

    private static LimelightReadiness readiness;

    private static LimelightConverted first;

    private static LimelightConverted second;

    private static byte[] viewStateBefore;

    @BeforeAll
    static void searchRescoreAndConvert()
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Path root = scratch.toRealPath();
        Path comet = RealComet.stageComet(RealComet.NEWER, root.resolve("bin/comet"));
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        List<Path> spectra = RealComet.spectra(inputs);
        Path fasta = RealComet.subset(inputs.resolve("subset.fasta"));
        Path percolator = RealPercolator.stage3071(root.resolve("bin/percolator-3.07.1"));
        Set<ToolCapability> probed = RealPercolator.probe("3.07.1", percolator);
        ToolOffer offer =
                RealPercolator.offer(
                        "3.07.1",
                        ToolOrigin.MANAGED,
                        percolator,
                        probed,
                        RealPercolator.ADVISORIES_3071);
        project = RealProject.create(root.resolve("project"));
        Set<DownstreamStage> limelight = EnumSet.of(DownstreamStage.LIMELIGHT_CONVERSION);
        PercolatorChoice choice =
                new PercolatorChoice(
                        new PercolatorSelection(offer, percolator, RealComet.sha256(percolator)),
                        PercolatorSettings.defaults(),
                        limelight,
                        PercolatorResolver.resolve(List.of(offer), limelight));
        prepared =
                project.prepare(
                        new SearchRequest(
                                        RealComet.model(
                                                RealComet.NEWER,
                                                fasta,
                                                DecoySource.COMET_INTERNAL_CONCATENATED,
                                                4),
                                        spectra,
                                        RealComet.selection(RealComet.NEWER, comet),
                                        IndexMode.NONE)
                                .withPercolator(choice));
        result =
                project.engine()
                        .start(
                                new RunRequest(
                                        project.store(),
                                        project.lock(),
                                        prepared.layout(),
                                        prepared.plan(),
                                        prepared.inputs(),
                                        Set.of(),
                                        prepared.actions(),
                                        RealProject.application(),
                                        prepared.settings()),
                                StepStateListener.NONE)
                        .await(RealProject.RUN_BOUND)
                        .orElseThrow();
        identity = project.store().read(prepared.layout()).identity();

        jar =
                Files.createDirectories(root.resolve("cache/limelight-converter/2.8.1"))
                        .resolve("cometPercolator2LimelightXML.jar");
        Path pinned = RealComet.repositoryRoot().resolve(CONVERTER_JAR);
        assertTrue(Files.isRegularFile(pinned), pinned + " is missing (gitignored scratch)");
        assertEquals(CONVERTER_SHA256, RealComet.sha256(pinned), "the pinned converter JAR");
        Files.copy(pinned, jar, StandardCopyOption.REPLACE_EXISTING);
        conversions =
                new LimelightConversions(
                        new LimelightProvisioning(new InstalledConverter(jar)),
                        project.runner(),
                        project.hashes(),
                        SecretRedactor.patternsOnly(),
                        project.sink(),
                        Clock.systemUTC(),
                        RealPercolator.HOST,
                        JavaRuntime.ofThisApplication(),
                        LimelightConverter.DEFAULT_TIMEOUT,
                        Set::of);
        RunLayout layout = prepared.layout();
        viewStateBefore =
                Files.exists(layout.viewStateFile())
                        ? Files.readAllBytes(layout.viewStateFile())
                        : null;
        readiness =
                conversions.check(
                        layout, identity, LimelightSettingsStore.read(layout), Optional.empty());
        first =
                conversions.convert(
                        layout, identity, LimelightSettingsStore.read(layout), Optional.empty());
        conversions.saveSettings(
                layout,
                LimelightSettingsStore.read(layout)
                        .withQCutoff(LimelightSettings.parseCutoff("0.05")));
        second =
                conversions.convert(
                        layout, identity, LimelightSettingsStore.read(layout), Optional.empty());
    }

    @AfterAll
    static void cleanUp() throws IOException {
        if (project != null) {
            project.close();
        }
    }

    /** The Tool Manager's answer for an installed, verified converter at one JAR. */
    private record InstalledConverter(Path jar) implements ToolManager {

        @Override
        public List<ToolOffer> offers() {
            return List.of(
                    new ToolOffer(
                            ToolName.LIMELIGHT_CONVERTER,
                            ToolVersion.parse("2.8.1"),
                            ToolOrigin.MANAGED,
                            ToolInstallState.INSTALLED,
                            List.of(),
                            List.of(),
                            Optional.empty(),
                            Optional.of(jar),
                            OptionalLong.of(2_762_075L)));
        }

        @Override
        public List<NoManagedBuild> noManagedBuild() {
            return List.of();
        }

        @Override
        public InstallHandle install(
                ToolName tool, ToolVersion version, InstallProgressListener listener) {
            throw new UnsupportedOperationException("already installed");
        }

        @Override
        public ToolOffer registerLocalBinary(ToolName tool, Path executable) {
            throw new UnsupportedOperationException();
        }
    }

    private static List<ProvenanceEvent> events(ProvenanceEventType type) throws IOException {
        return ProvenanceEventLogReader.recover(
                        prepared.layout().eventLogFile(), SecretRedactor.patternsOnly())
                .events()
                .stream()
                .filter(event -> event.type() == type)
                .toList();
    }

    private static List<String> argv(ProvenanceEvent event) {
        Map<String, String> payload = event.payload();
        int count = Integer.parseInt(payload.get(LimelightConversions.ARGV_COUNT));
        List<String> argv = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            argv.add(payload.get(LimelightConversions.ARGV_PREFIX + index));
        }
        return argv;
    }

    private static boolean adjacent(List<String> argv, String option, String value) {
        for (int index = 0; index + 1 < argv.size(); index++) {
            if (argv.get(index).equals(option) && argv.get(index + 1).equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static String digest(Path file, String algorithm) throws IOException {
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

    /** The target PSM rows of the run's own psms.tsv, counted here: lines after the header. */
    private static long targetPsmRows() throws IOException {
        List<String> lines =
                Files.readAllLines(
                        prepared.layout().root().resolve("outputs/percolator/psms.tsv"),
                        StandardCharsets.UTF_8);
        return lines.stream().skip(1).filter(line -> !line.isBlank()).count();
    }

    /** What this test reads from a Limelight XML with its own StAX reader. */
    private record Read(long psms, List<String> psmQFilters) {}

    private static Read read(Path file) throws IOException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        long psms = 0;
        List<String> filters = new ArrayList<>();
        try (InputStream in = Files.newInputStream(file)) {
            XMLStreamReader reader = factory.createXMLStreamReader(in);
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();
                    if ("psm".equals(name)) {
                        psms++;
                    } else if ("filterable_psm_annotation_type".equals(name)
                            && "q-value".equals(reader.getAttributeValue(null, "name"))) {
                        filters.add(reader.getAttributeValue(null, "default_filter_value"));
                    }
                }
            }
            reader.close();
        } catch (XMLStreamException malformed) {
            throw new IOException(malformed);
        }
        return new Read(psms, filters);
    }

    /** Valid against the schema this test reads out of the pinned JAR itself. */
    private static void validate(Path file) throws IOException, SAXException {
        SchemaFactory schemas = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        schemas.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        schemas.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("limelight-xml.xsd");
            assertTrue(entry != null, "the converter JAR carries limelight-xml.xsd");
            try (InputStream xsd = zip.getInputStream(entry)) {
                Validator validator = schemas.newSchema(new StreamSource(xsd)).newValidator();
                validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                validator.validate(new StreamSource(file.toFile()));
            }
        }
    }

    @Test
    @DisplayName(
            "gate 1, AC-LL-02: the real run's real conversion is a valid Limelight XML holding"
                    + " every target PSM of the run's own table, checksummed into provenance with"
                    + " this test's own MD5 and SHA-256, -q 0.01 and -d <run>/outputs/comet")
    void gate1ARealConversion() throws IOException, SAXException {
        assertEquals(
                AttemptOutcome.SUCCEEDED, result.outcome(), () -> result.failures().toString());
        assertTrue(readiness.ready(), () -> readiness.refusals().toString());
        RunLayout layout = prepared.layout();
        Path file = layout.root().resolve("outputs/limelight/limelight-1.xml");
        validate(file);
        Read read = read(file);
        long targets = targetPsmRows();
        ProvenanceEvent converted = events(ProvenanceEventType.LIMELIGHT_CONVERTED).get(0);
        Map<String, String> payload = converted.payload();
        List<String> argv = argv(converted);
        String pout = Files.readString(layout.root().resolve("outputs/percolator/pout.xml"));
        assertAll(
                () -> assertEquals(file, first.file()),
                () -> assertTrue(targets > 0, "the run found target PSMs"),
                () -> assertEquals(targets, read.psms(), "every target PSM of psms.tsv"),
                () ->
                        assertTrue(
                                pout.contains("p:psm_id=\"" + layout.cometOutputDirectory() + "/"),
                                "the run's own PSM ids carry Comet's absolute -N base"),
                () -> assertEquals(List.of("0.01"), read.psmQFilters()),
                () -> assertEquals(digest(file, "SHA-256"), payload.get("output.sha256")),
                () -> assertEquals(digest(file, "MD5"), payload.get("output.md5")),
                () -> assertEquals(digest(file, "SHA-256"), first.hashes().sha256()),
                () -> assertEquals(Long.toString(Files.size(file)), payload.get("output.size")),
                () -> assertEquals(Long.toString(targets), payload.get("output.psms")),
                () -> assertTrue(adjacent(argv, "-q", "0.01"), argv::toString),
                () ->
                        assertTrue(
                                adjacent(
                                        argv,
                                        "-d",
                                        layout.root().resolve("outputs/comet").toString()),
                                argv::toString),
                () -> assertEquals("2.8.1", payload.get("tool.version")),
                () -> assertEquals(CONVERTER_SHA256, payload.get("converter.jar.sha256")),
                () -> assertEquals(digest(jar, "MD5"), payload.get("converter.jar.md5")),
                () ->
                        assertEquals(
                                digest(layout.cometParamsFile(), "SHA-256"),
                                payload.get("input.1.sha256")),
                () -> assertEquals("comet-params", payload.get("input.1.role")),
                () -> assertEquals("percolator-xml", payload.get("input.2.role")),
                () ->
                        assertEquals(
                                digest(
                                        layout.root().resolve("outputs/percolator/pout.xml"),
                                        "SHA-256"),
                                payload.get("input.2.sha256")),
                () -> assertEquals("percolator-xml-for-converter", payload.get("input.3.role")),
                () ->
                        assertFalse(
                                Files.getPosixFilePermissions(file)
                                        .contains(
                                                java.nio.file.attribute.PosixFilePermission
                                                        .OWNER_WRITE),
                                "the kept file is read-only"),
                () ->
                        assertEquals(
                                1,
                                events(ProvenanceEventType.LIMELIGHT_CONVERTING).stream()
                                        .filter(
                                                event ->
                                                        event.payload()
                                                                .get("output.path")
                                                                .equals(file.toString()))
                                        .count()),
                () ->
                        assertTrue(
                                Files.readString(
                                                layout.root()
                                                        .resolve("logs/limelight-converter.log"))
                                        .contains("Validating Limelight XML"),
                                "the converter's own output is in its log"));
    }

    @Test
    @DisplayName(
            "gate 5: a changed Limelight cutoff reaches the next real conversion's -q and its"
                    + " file's PSM q-value filter, filters no PSM, and leaves the display filters"
                    + " alone")
    void gate5TheCutoffReachesTheRealFile() throws IOException, SAXException {
        RunLayout layout = prepared.layout();
        Path file = layout.root().resolve("outputs/limelight/limelight-2.xml");
        validate(file);
        Read read = read(file);
        ProvenanceEvent converted = events(ProvenanceEventType.LIMELIGHT_CONVERTED).get(1);
        List<String> argv = argv(converted);
        assertAll(
                () -> assertEquals(file, second.file()),
                () -> assertEquals(List.of("0.05"), read.psmQFilters()),
                () ->
                        assertEquals(
                                List.of("0.01"),
                                read(layout.root().resolve("outputs/limelight/limelight-1.xml"))
                                        .psmQFilters(),
                                "the first file is kept as it was"),
                () -> assertEquals(targetPsmRows(), read.psms(), "the cutoff filters nothing"),
                () -> assertTrue(adjacent(argv, "-q", "0.05"), argv::toString),
                () -> assertEquals("0.05", converted.payload().get("limelight.q-cutoff")),
                () ->
                        assertEquals(
                                digest(file, "SHA-256"), converted.payload().get("output.sha256")),
                () ->
                        assertEquals(
                                viewStateBefore == null,
                                !Files.exists(layout.viewStateFile()),
                                "the display filters' file is as it was"),
                () ->
                        assertTrue(
                                viewStateBefore == null
                                        || java.util.Arrays.equals(
                                                viewStateBefore,
                                                Files.readAllBytes(layout.viewStateFile()))),
                () ->
                        assertEquals(
                                "0.05", LimelightSettingsStore.read(layout).qCutoff().argument()));
    }
}
