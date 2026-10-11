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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
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
import org.cometgui.domain.run.RunDerivation;
import org.cometgui.domain.run.RunDescriptor;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallProgressListener;
import org.cometgui.domain.tools.NoManagedBuild;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.CompatibilityAction;
import org.cometgui.params.percolator.resolution.DownstreamStage;
import org.cometgui.params.percolator.resolution.PercolatorResolver;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.tools.api.JavaRuntime;
import org.cometgui.tools.limelight.LimelightConverter;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepStateListener;
import org.cometgui.workflow.limelight.LimelightCompatibility;
import org.cometgui.workflow.limelight.LimelightConversionRefusedException;
import org.cometgui.workflow.limelight.LimelightConversions;
import org.cometgui.workflow.limelight.LimelightConverted;
import org.cometgui.workflow.limelight.LimelightPrerequisite;
import org.cometgui.workflow.limelight.LimelightProvisioning;
import org.cometgui.workflow.limelight.LimelightRefusal;
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
 * Phase 12 exit gate item 4 and {@code AC-LL-04}, and gate item 3's model half ({@code AC-LL-03}),
 * against REAL runs through the real engine, the real process service and the real converter: the
 * specification's <em>Second E2E scenario</em> below the interface.
 *
 * <p>A real Comet 2026.03.0 search of the two K562 mzML and the proteome's first 1000 records,
 * rescored by Percolator 3.09 -- the registered local binary {@code scratch/percolator/3.09}, the
 * only way 3.09 runs on Linux -- with Limelight conversion <em>enabled</em>. 3.09 cannot write pout
 * XML, so: the run's compatibility, read from its own record, says it is not convertible and why,
 * and offers the compatible rerun labelled with the version Phase 09's resolution returns here
 * (3.07.1, managed); a conversion of it is refused before any process starts; the rerun is invoked
 * through Phase 09's one path ({@link PercolatorRerun}, via {@link
 * PercolatorRerun#compatibleChoice}); the derived run's Percolator is 3.07.1 and wrote pout XML;
 * and its conversion through {@link LimelightConversions} -- the REAL pinned converter v2.8.1 -- is
 * a valid Limelight XML holding every target PSM of the derived run's own table.
 *
 * <p>Everything is judged here: the Limelight XML against the {@code limelight-xml.xsd} read out of
 * the pinned JAR by this test, its {@code psm} elements counted by this test's own StAX reader, the
 * target rows of {@code psms.tsv} counted here, every SHA-256 computed here, Comet and converter
 * launches counted on the one process runner, and both Percolator executions read back from the two
 * {@code provenance.json} files.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet, Percolator and converter runs have ever been executed"
                        + " in this project")
class RealLimelightCompatibleRerunTest {

    private static final String CONVERTER_JAR =
            "scratch/phase05/artefacts/v2.8.1__cometPercolator2LimelightXML.jar";

    private static final String CONVERTER_SHA256 =
            "843573396ce0654a0ac81582b378c496923e49dde71f40d750d890947774ece1";

    private static final Set<DownstreamStage> LIMELIGHT =
            EnumSet.of(DownstreamStage.LIMELIGHT_CONVERSION);

    @TempDir private static Path scratch;

    private static Path comet;

    private static Path percolator3071;

    private static Path percolator309;

    private static ToolOffer offer3071;

    private static ToolOffer offer309;

    private static Path jar;

    private static RealProject project;

    private static PreparedRun original;

    private static RunResult originalResult;

    private static RunIdentity originalIdentity;

    private static byte[] originalProvenanceBefore;

    private static LimelightConversions conversions;

    private static LimelightCompatibility before;

    private static LimelightConversionRefusedException refused;

    private static int launchesBeforeRefusal;

    private static int launchesAfterRefusal;

    private static DerivedRun derived;

    private static RunResult derivedResult;

    private static RunIdentity derivedIdentity;

    private static LimelightCompatibility after;

    private static LimelightConverted converted;

    private static int cometBefore;

    private static int cometAfter;

    @BeforeAll
    static void runThreeNineRerunAndConvert()
            throws IOException,
                    InterruptedException,
                    RunBlockedException,
                    ReuseRefusedException,
                    RerunRefusedException {
        Path root = scratch.toRealPath();
        comet = RealComet.stageComet(RealComet.NEWER, root.resolve("bin/comet"));
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        List<Path> spectra = RealComet.spectra(inputs);
        Path fasta = RealComet.subset(inputs.resolve("subset.fasta"));
        percolator3071 = RealPercolator.stage3071(root.resolve("bin/percolator-3.07.1"));
        percolator309 = RealPercolator.wrapper309();
        offer3071 =
                RealPercolator.offer(
                        "3.07.1",
                        ToolOrigin.MANAGED,
                        percolator3071,
                        RealPercolator.probe("3.07.1", percolator3071),
                        RealPercolator.ADVISORIES_3071);
        offer309 =
                RealPercolator.offer(
                        "3.09",
                        ToolOrigin.LOCAL,
                        percolator309,
                        RealPercolator.probe("3.09", percolator309),
                        List.of());
        project = RealProject.create(root.resolve("project"));

        // The scientist chose 3.09 and switched Limelight on: resolution would have chosen 3.07.1.
        PercolatorChoice threeNine =
                new PercolatorChoice(
                        new PercolatorSelection(
                                offer309, percolator309, RealComet.sha256(percolator309)),
                        PercolatorSettings.defaults(),
                        LIMELIGHT,
                        PercolatorResolver.resolve(List.of(offer309, offer3071), LIMELIGHT));
        original =
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
                                .withPercolator(threeNine));
        originalResult = project.run(original);
        originalIdentity = project.store().read(original.layout()).identity();
        originalProvenanceBefore = Files.readAllBytes(original.layout().provenanceJsonFile());

        jar =
                Files.createDirectories(root.resolve("cache/limelight-converter/2.8.1"))
                        .resolve("cometPercolator2LimelightXML.jar");
        Path pinned = RealComet.repositoryRoot().resolve(CONVERTER_JAR);
        assertTrue(Files.isRegularFile(pinned), pinned + " is missing (gitignored scratch)");
        assertEquals(CONVERTER_SHA256, RealComet.sha256(pinned), "the pinned converter JAR");
        Files.copy(pinned, jar, StandardCopyOption.REPLACE_EXISTING);
        conversions =
                new LimelightConversions(
                        new LimelightProvisioning(new Offered(jar, offer309, offer3071)),
                        project.runner(),
                        project.hashes(),
                        SecretRedactor.patternsOnly(),
                        project.sink(),
                        Clock.systemUTC(),
                        RealPercolator.HOST,
                        JavaRuntime.ofThisApplication(),
                        LimelightConverter.DEFAULT_TIMEOUT,
                        Set::of);

        cometBefore = project.runner().launchesOf(comet).size();
        before = conversions.compatibility(original.layout(), originalIdentity);
        launchesBeforeRefusal = project.runner().launches().size();
        refused =
                assertThrows(
                        LimelightConversionRefusedException.class,
                        () ->
                                conversions.convert(
                                        original.layout(),
                                        originalIdentity,
                                        LimelightSettingsStore.read(original.layout()),
                                        Optional.empty()));
        launchesAfterRefusal = project.runner().launches().size();

        // The offered action, invoked through Phase 09's one rerun path.
        CompatibilityAction action = before.action().orElseThrow();
        PercolatorRerun rerun = new PercolatorRerun(project.hashes());
        PercolatorChoice compatible = rerun.compatibleChoice(action, PercolatorSettings.defaults());
        derived =
                rerun.prepare(
                        project.store(),
                        project.lock(),
                        original.layout(),
                        compatible,
                        RealProject.application());
        derivedResult =
                rerun.start(project.engine(), derived, StepStateListener.NONE)
                        .await(RealProject.RUN_BOUND)
                        .orElseThrow();
        derivedIdentity = project.store().read(derived.layout()).identity();

        after = conversions.compatibility(derived.layout(), derivedIdentity);
        converted =
                conversions.convert(
                        derived.layout(),
                        derivedIdentity,
                        LimelightSettingsStore.read(derived.layout()),
                        Optional.empty());
        cometAfter = project.runner().launchesOf(comet).size();
    }

    @AfterAll
    static void cleanUp() throws IOException {
        if (project != null) {
            project.close();
        }
    }

    /** The Tool Manager here now: the installed converter, 3.09 registered, 3.07.1 installed. */
    private record Offered(Path jar, ToolOffer threeNine, ToolOffer threeSeven)
            implements ToolManager {

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
                            OptionalLong.of(2_762_075L)),
                    threeSeven,
                    threeNine);
        }

        @Override
        public List<NoManagedBuild> noManagedBuild() {
            return List.of();
        }

        @Override
        public InstallHandle install(
                ToolName tool, ToolVersion version, InstallProgressListener listener) {
            throw new UnsupportedOperationException("everything here is installed");
        }

        @Override
        public ToolOffer registerLocalBinary(ToolName tool, Path executable) {
            throw new UnsupportedOperationException();
        }
    }

    private static ToolRecord percolatorTool(ProvenanceManifest manifest) {
        List<ToolRecord> tools = RunEvidence.tools(manifest, CometWorkflow.PERCOLATOR_TOOL_NAME);
        assertEquals(1, tools.size(), "one Percolator execution per run");
        return tools.get(0);
    }

    /** Phase 09's recorded reason for leaving -X out, found in the settings by this test. */
    private static String recordedXmlOmission(Map<String, String> settings) {
        for (Map.Entry<String, String> setting : settings.entrySet()) {
            if (setting.getKey().startsWith("percolator.not-emitted.")
                    && setting.getKey().endsWith(".option")
                    && setting.getValue().equals("-X")) {
                String key = setting.getKey();
                return settings.get(
                        key.substring(0, key.length() - ".option".length()) + ".reason");
            }
        }
        throw new AssertionError("the 3.09 run records no -X omission: " + settings);
    }

    /** The target PSM rows of a run's own psms.tsv, counted here: the lines after the header. */
    private static long targetPsmRows(RunLayout layout) throws IOException {
        return Files.readAllLines(
                        layout.root().resolve("outputs/percolator/psms.tsv"),
                        StandardCharsets.UTF_8)
                .stream()
                .skip(1)
                .filter(line -> !line.isBlank())
                .count();
    }

    private static long psmElements(Path file) throws IOException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        long psms = 0;
        try (InputStream in = Files.newInputStream(file)) {
            XMLStreamReader reader = factory.createXMLStreamReader(in);
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT
                        && "psm".equals(reader.getLocalName())) {
                    psms++;
                }
            }
            reader.close();
        } catch (XMLStreamException malformed) {
            throw new IOException(malformed);
        }
        return psms;
    }

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

    private static long converterLaunches() {
        return project.runner().launches().stream()
                .filter(launch -> launch.command().argv().contains(jar.toString()))
                .count();
    }

    @Test
    @DisplayName(
            "gate 3 (model), AC-LL-03: the 3.09 run is not convertible, in Phase 09's recorded"
                    + " words, and the offered action is the rerun with the version resolution"
                    + " returns here, 3.07.1")
    void gate3TheExplanationAndTheOfferedAction() throws IOException {
        assertEquals(
                AttemptOutcome.SUCCEEDED,
                originalResult.outcome(),
                () -> originalResult.failures().toString());
        Map<String, String> settings = RunEvidence.manifest(original.layout()).settings();
        String omission = recordedXmlOmission(settings);
        String expected =
                "run run-0001's Percolator 3.09 (registered local binary) did not write the"
                        + " Percolator XML the Limelight converter requires: "
                        + omission;
        CompatibilityAction action = before.action().orElseThrow();
        assertAll(
                () -> assertTrue(omission.contains("XML_OUTPUT"), omission),
                () -> assertFalse(before.convertible()),
                () -> assertEquals(Optional.of(expected), before.explanation()),
                () ->
                        assertEquals(
                                Optional.of("3.09 (registered local binary)"), before.percolator()),
                () -> assertTrue(action.offersRerun()),
                () -> assertFalse(action.installFirst()),
                () -> assertEquals(offer3071, action.build().orElseThrow()),
                () -> assertEquals("Rerun Percolator with 3.07.1 for Limelight", action.label()),
                () ->
                        assertFalse(
                                Files.exists(
                                        original.layout()
                                                .root()
                                                .resolve("outputs/percolator/pout.xml"))));
        System.out.printf(
                Locale.ROOT,
                "RealLimelightCompatibleRerunTest gate 3%n  explanation: %s%n  action: %s%n  %s%n",
                expected,
                action.label(),
                action.explanation());
    }

    @Test
    @DisplayName(
            "gate 3: a conversion of the 3.09 run is refused before any launch, with the"
                    + " compatibility's explanation; no converter process started")
    void aConversionOfTheOriginalIsRefusedFirst() {
        List<LimelightRefusal> refusals = refused.refusals();
        assertAll(
                () ->
                        assertEquals(
                                List.of(LimelightPrerequisite.PERCOLATOR_XML),
                                refusals.stream().map(LimelightRefusal::prerequisite).toList(),
                                refused::getMessage),
                () -> assertEquals(before.explanation().orElseThrow(), refusals.get(0).message()),
                () ->
                        assertEquals(
                                launchesBeforeRefusal,
                                launchesAfterRefusal,
                                "no process of any kind started for the refused conversion"),
                () ->
                        assertFalse(
                                Files.exists(original.layout().limelightOutputDirectory()),
                                "nothing written under the original's outputs/limelight"));
    }

    @Test
    @DisplayName(
            "gate 4, AC-LL-04: the rerun's Percolator is 3.07.1 and wrote pout XML; the derived run"
                    + " is convertible and its real conversion is a valid Limelight XML holding"
                    + " every target PSM of its own table; Comet launched zero more times")
    void gate4TheRerunConverts() throws IOException, SAXException {
        assertEquals(
                AttemptOutcome.SUCCEEDED,
                derivedResult.outcome(),
                () -> derivedResult.failures().toString());
        RunLayout layout = derived.layout();
        Path pout = layout.root().resolve("outputs/percolator/pout.xml");
        Path file = layout.root().resolve("outputs/limelight/limelight-1.xml");
        validate(file);
        long targets = targetPsmRows(layout);
        long psms = psmElements(file);
        ToolRecord rescored = percolatorTool(RunEvidence.manifest(layout));
        assertAll(
                () -> assertEquals("3.07.1", rescored.version()),
                () -> assertTrue(Files.size(pout) > 0, "the rerun wrote pout XML"),
                () -> assertTrue(after.convertible(), () -> after.explanation().toString()),
                () -> assertEquals(Optional.empty(), after.action()),
                () -> assertEquals(Optional.of("3.07.1"), after.percolator()),
                () -> assertEquals(file, converted.file()),
                () -> assertTrue(targets > 0, "the derived run found target PSMs"),
                () -> assertEquals(targets, psms, "every target PSM of the derived psms.tsv"),
                () -> assertEquals(RealComet.sha256(file), converted.hashes().sha256()),
                () -> assertEquals(2, cometBefore, "the original's two Comet invocations"),
                () ->
                        assertEquals(
                                cometBefore,
                                cometAfter,
                                "Comet launched during the rerun or the conversion"),
                () ->
                        assertEquals(
                                2,
                                converterLaunches(),
                                "the converter started twice, both for the derived run:"
                                        + " --version, then the conversion"),
                () ->
                        assertEquals(
                                original.layout().cometOutputDirectory(),
                                LimelightConversions.searchedRun(layout, derivedIdentity)
                                        .cometOutputDirectory(),
                                "the pepXML the conversion read are the source run's"));
        System.out.printf(
                Locale.ROOT,
                "RealLimelightCompatibleRerunTest gate 4: %s, %d psm = %d target rows, Comet"
                        + " launches %d -> %d%n",
                file,
                psms,
                targets,
                cometBefore,
                cometAfter);
    }

    @Test
    @DisplayName(
            "gate 4: provenance shows both Percolator executions -- distinct version, binary"
                    + " SHA-256 and argv -- the original's provenance.json byte for byte unchanged,"
                    + " the derived run naming it in derivedFrom, the merged PIN its recorded"
                    + " SHA-256")
    void gate4BothExecutionsInProvenance() throws IOException {
        RunLayout originalLayout = original.layout();
        RunLayout layout = derived.layout();
        byte[] originalNow = Files.readAllBytes(originalLayout.provenanceJsonFile());
        ProvenanceManifest first = RunEvidence.manifest(originalLayout);
        ProvenanceManifest second = RunEvidence.manifest(layout);
        ToolRecord threeNine = percolatorTool(first);
        ToolRecord threeSeven = percolatorTool(second);
        List<String> firstArgv = threeNine.execution().command().argv();
        List<String> secondArgv = threeSeven.execution().command().argv();
        String firstText = Files.readString(originalLayout.provenanceJsonFile());
        String secondText = Files.readString(layout.provenanceJsonFile());
        String recordedPin = null;
        for (FileRecord file : first.files()) {
            if (file.direction() == FileDirection.OUTPUT && "merged-pin".equals(file.role())) {
                recordedPin = file.hashes().sha256();
            }
        }
        String pin = recordedPin;
        RunDescriptor descriptor = project.store().read(layout);
        RunDerivation from = descriptor.identity().derivedFrom().orElseThrow();
        assertAll(
                () -> assertArrayEquals(originalProvenanceBefore, originalNow),
                () -> assertEquals("3.09", threeNine.version()),
                () -> assertEquals("3.07.1", threeSeven.version()),
                () -> assertNotEquals(threeNine.version(), threeSeven.version()),
                () -> assertEquals(RealPercolator.SHA256_WRAPPER_309, threeNine.hashes().sha256()),
                () -> assertEquals(RealPercolator.SHA256_3071, threeSeven.hashes().sha256()),
                () -> assertEquals(RealComet.sha256(percolator309), threeNine.hashes().sha256()),
                () -> assertEquals(RealComet.sha256(percolator3071), threeSeven.hashes().sha256()),
                () -> assertNotEquals(threeNine.hashes().sha256(), threeSeven.hashes().sha256()),
                () -> assertNotEquals(firstArgv, secondArgv),
                () -> assertEquals(percolator309.toString(), firstArgv.get(0)),
                () -> assertEquals(percolator3071.toString(), secondArgv.get(0)),
                () -> assertFalse(firstArgv.contains("-X"), firstArgv::toString),
                () ->
                        assertEquals(
                                layout.root().resolve("outputs/percolator/pout.xml").toString(),
                                secondArgv.get(secondArgv.indexOf("-X") + 1)),
                () -> assertTrue(firstText.contains(threeNine.hashes().sha256())),
                () -> assertTrue(secondText.contains(threeSeven.hashes().sha256())),
                () -> assertFalse(firstText.contains(RealPercolator.SHA256_3071)),
                () -> assertEquals(2, descriptor.schemaVersion()),
                () -> assertEquals("run-0001", from.runId().value()),
                () ->
                        assertEquals(
                                RealComet.sha256(originalLayout.provenanceJsonFile()),
                                from.provenance().hashes().sha256()),
                () -> assertEquals(RealComet.sha256(originalLayout.mergedPinFile()), pin),
                () -> assertEquals(pin, RealComet.sha256(layout.mergedPinFile())),
                () -> assertEquals(pin, from.mergedPin().hashes().sha256()),
                () -> assertEquals(List.of(), RunEvidence.tools(second, CometWorkflow.TOOL_NAME)),
                () ->
                        assertEquals(
                                "run-0001", second.settings().get(RerunProvenance.SOURCE_RUN_ID)),
                () ->
                        assertEquals(
                                "3.09",
                                second.settings().get(RerunProvenance.SOURCE_PERCOLATOR_VERSION)));
        System.out.printf(
                Locale.ROOT,
                "RealLimelightCompatibleRerunTest provenance%n  first  %s %s %s%n  second %s %s"
                        + " %s%n  merged PIN %s%n",
                threeNine.version(),
                threeNine.hashes().sha256(),
                firstArgv,
                threeSeven.version(),
                threeSeven.hashes().sha256(),
                secondArgv,
                pin);
    }
}
