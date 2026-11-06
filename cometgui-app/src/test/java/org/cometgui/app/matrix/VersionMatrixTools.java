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

package org.cometgui.app.matrix;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.cometgui.app.matrix.MatrixSupport.Row;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.tools.ArtefactKind;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.ToolAdvisory;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.install.registry.ArtefactManifestReader;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.params.comet.schema.CometParameterSchema;
import org.cometgui.params.comet.schema.CometParameterSchemaProvider;
import org.cometgui.params.comet.schema.CometToolIdentity;
import org.cometgui.params.comet.schema.DiscoveryMode;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.schema.PercolatorSetting;
import org.cometgui.results.filtering.PeptideQValueFilter;
import org.cometgui.results.filtering.PsmQValueFilter;
import org.cometgui.results.parser.LearnedWeights;
import org.cometgui.results.parser.ResultRow;
import org.cometgui.results.parser.ResultTable;
import org.cometgui.results.parser.ResultTableReader;
import org.cometgui.results.parser.WeightsReader;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.percolator.PercolatorArtefact;
import org.cometgui.tools.percolator.PercolatorCommand;
import org.cometgui.tools.percolator.PercolatorCommands;
import org.cometgui.tools.percolator.PercolatorOption;
import org.cometgui.tools.percolator.PercolatorRequest;
import org.cometgui.tools.percolator.PoutDocument;
import org.cometgui.tools.percolator.SyntheticPin;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * The parts of the version matrix that need no spectra (gate item 1; specification, <em>Version
 * matrix tests</em>): every build the product's Tool Manager offers on this host installed from its
 * pinned upstream URL into an empty cache by the product's own installer and three-stage probe, and
 * then exercised.
 *
 * <ul>
 *   <li><strong>Comet</strong>, each release: {@code -p} and {@code -q} written byte for byte as
 *       the committed fixtures under {@code
 *       cometgui-params-comet/src/test/resources/fixtures/comet/}, and the product's schema
 *       provider reading the release's schema from the binary.
 *   <li><strong>Percolator</strong>, each release: over CometGUI's own synthetic PIN ({@link
 *       SyntheticPin}, no fixture data), through the product's command builder, PSM, peptide, decoy
 *       and weights outputs parsed by the product's readers and counted with the product's default
 *       filters; pout XML requested and read only where the probe observed {@code XML_OUTPUT}; and
 *       the builder shown to leave {@code -X} out when no stage needs it and when the build was not
 *       observed to accept it.
 *   <li><strong>PDV and the Limelight converter</strong>: installed and identified. Their
 *       functional runs are the {@code pdv} and {@code limelight} harnesses', not repeated here.
 *   <li>Optionally, a <strong>registered local Percolator 3.09</strong> ({@value
 *       MatrixSupport#LOCAL_309}), through the Tool Manager's own registration -- recorded as a
 *       local binary, never as a managed offer ({@code D-003}).
 * </ul>
 *
 * <p>Expected values are not here: each row records what it observed and the matrix compares the
 * golden values with the reviewed goldens ({@code R-TEST-07}). What is asserted here is what must
 * hold whatever the version: exit statuses, argument arrays, outputs present, counts agreeing with
 * this test's own line counts, and the installed bytes being the pinned bytes.
 */
@EnabledIfSystemProperty(
        named = MatrixSupport.ENABLE,
        matches = "true",
        disabledReason = MatrixSupport.DISABLED)
class VersionMatrixTools {

    /** What a Percolator invocation may take. A bound, never a delay. */
    private static final Duration PERCOLATOR_BOUND = Duration.ofMinutes(5);

    /** The cut-offs at which the synthetic outputs' target PSMs are also counted. */
    private static final List<String> CUTOFFS = List.of("0.01", "0.1", "0.5", "1");

    @TempDir private static Path temporary;

    private static ProcessService processes;

    private static ToolManager manager;

    @BeforeAll
    static void theProductsToolManager() throws IOException {
        MatrixSupport.openGuardForUpstream();
        processes = new ProcessService(Clock.systemUTC());
        manager = MatrixSupport.toolManager(temporary.resolve("cache"), processes);
    }

    @AfterAll
    static void restore() {
        MatrixSupport.restoreGuard();
    }

    @Test
    @DisplayName(
            "every build offered on this host installs from upstream, is probed, and does what its"
                    + " version can do")
    void everyOfferedBuild() throws IOException, InterruptedException {
        String host = MatrixSupport.host().id();
        List<ToolOffer> offered =
                manager.offers().stream()
                        .filter(offer -> offer.origin() == ToolOrigin.MANAGED)
                        .filter(
                                offer ->
                                        offer.state()
                                                != ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM)
                        .toList();
        assertFalse(offered.isEmpty(), "the Tool Manager offers nothing on " + host);
        List<String> failed = new ArrayList<>();
        for (ToolOffer offer : offered) {
            String id = "offer:" + offer.tool().id() + ":" + offer.version().text() + ":" + host;
            Row row = Row.start(id);
            row.observe("stateBeforeInstall", offer.state().name());
            ToolOffer[] installed = new ToolOffer[1];
            boolean ok =
                    row.check(
                            "installed through the Tool Manager from the pinned upstream URL into"
                                    + " an empty cache, every stage of the probe passed",
                            () -> installed[0] = installAndRecord(row, offer));
            if (ok) {
                Path work =
                        Files.createDirectories(temporary.resolve(MatrixSupport.Row.fileName(id)));
                switch (offer.tool()) {
                    case COMET -> comet(row, installed[0], work);
                    case PERCOLATOR ->
                            percolator(
                                    row,
                                    installed[0],
                                    work,
                                    "percolator-" + offer.version().text());
                    case PDV, LIMELIGHT_CONVERTER -> javaTool(row, installed[0]);
                }
            }
            try {
                row.finish();
            } catch (AssertionError red) {
                failed.add(red.getMessage());
            }
        }
        localPercolator(host, failed);
        assertEquals(List.of(), failed, "rows that failed");
    }

    /** Installs one offer and records the installed bytes, the probe's verdict and the record. */
    private static ToolOffer installAndRecord(Row row, ToolOffer offer)
            throws IOException, InterruptedException {
        ToolOffer installed = MatrixSupport.install(manager, offer.tool(), offer.version());
        Path executable = installed.installedPath().orElseThrow();
        ArtefactRecord record = record(installed);
        String actual = MatrixSupport.sha256(executable);
        row.observe("url", record.url().toString());
        row.observe("kind", record.kind().name());
        row.observe("installedSha256", actual);
        row.observe("capabilities", MatrixSupport.capabilityIds(installed));
        row.observe("advisories", installed.advisories().stream().map(ToolAdvisory::id).toList());
        row.observe(
                "unconfirmedClaims",
                installed.unconfirmedClaims().stream()
                        .map(claim -> claim.capability().id())
                        .sorted()
                        .toList());
        row.observe("artefactIdentity", installed.artefactIdentity().orElse(""));
        if (record.member().isPresent()) {
            String pinned = record.member().get().hashes().sha256();
            row.observe("pinnedSha256", pinned);
            assertEquals(pinned, actual, "the installed executable is the pinned archive member");
        } else if (record.kind() == ArtefactKind.ZIP) {
            /* A whole-archive extraction (PDV): the download's SHA-256 is the pin, checked by the
             * installer's verification step; the executable inside it has no digest of its own. */
            String expected = record.expectedExecutablePath().orElseThrow();
            row.observe("pinnedSha256", record.hashes().sha256() + " (the archive)");
            assertTrue(
                    executable.toString().replace('\\', '/').endsWith("/" + expected),
                    executable + " is the archive's " + expected);
        } else {
            String pinned = record.hashes().sha256();
            row.observe("pinnedSha256", pinned);
            assertEquals(pinned, actual, "the installed executable is the pinned download");
        }
        for (DeclaredCapability capability : installed.capabilities()) {
            assertEquals(
                    CapabilityEvidence.OBSERVED_BY_EXECUTION,
                    capability.evidence(),
                    capability.capability().id() + " of an installed build is the probe's verdict");
        }
        return installed;
    }

    /** The shipped manifest's record of an offer on this host. */
    private static ArtefactRecord record(ToolOffer offer) throws IOException {
        List<ArtefactRecord> matching =
                ArtefactManifestReader.readFromClasspath()
                        .select(MatrixSupport.host(), offer.tool(), offer.version())
                        .stream()
                        .map(selection -> selection.artefact())
                        .toList();
        assertEquals(1, matching.size(), () -> "manifest records for " + offer + ": " + matching);
        return matching.get(0);
    }

    // ------------------------------------------------------------------------------------------
    // Comet
    // ------------------------------------------------------------------------------------------

    private static void comet(Row row, ToolOffer installed, Path work) {
        Path executable = installed.installedPath().orElseThrow();
        String release = installed.version().text();
        for (String argument : List.of("-p", "-q")) {
            row.check(
                    "comet " + argument + " writes the committed fixture byte for byte",
                    () -> {
                        Path directory = Files.createDirectories(work.resolve("dump" + argument));
                        ToolRunOutcome outcome =
                                runner(Duration.ofMinutes(1))
                                        .run(
                                                new ToolCommand(
                                                        List.of(executable.toString(), argument),
                                                        directory,
                                                        Map.of()));
                        assertTrue(outcome.exitedZero(), outcome::joinedOutput);
                        Path written = directory.resolve(CometParameterSchemaProvider.WRITTEN_FILE);
                        assertTrue(Files.isRegularFile(written), "no " + written);
                        Path fixture = cometFixture(release, argument);
                        assertTrue(
                                Files.isRegularFile(fixture),
                                "there is no committed fixture " + fixture + " for this release");
                        row.observe(
                                "fixture" + argument,
                                MatrixSupport.root().relativize(fixture).toString());
                        row.observe("sha256" + argument, MatrixSupport.sha256(written));
                        assertArrayEquals(
                                Files.readAllBytes(fixture),
                                Files.readAllBytes(written),
                                "comet " + argument + " against " + fixture);
                    });
        }
        row.check(
                "the product's schema provider reads the release's schema from the binary",
                () -> {
                    Set<ToolCapability> probed = EnumSet.noneOf(ToolCapability.class);
                    installed.capabilities().forEach(declared -> probed.add(declared.capability()));
                    Path workspaces = Files.createDirectories(work.resolve("schema"));
                    CometParameterSchema schema =
                            new CometParameterSchemaProvider(
                                            processes,
                                            MetadataLoader.loadBundled(),
                                            Duration.ofMinutes(1),
                                            workspaces)
                                    .schemaFor(
                                            new CometToolIdentity(
                                                    executable, installed.version(), probed));
                    List<String> names = schema.discovered().names();
                    row.observe("schemaMode", schema.mode().name());
                    row.observe("schemaMarker", schema.discovered().marker().text());
                    row.observe("schemaParameterCount", names.size());
                    row.observe("schemaParameters", names.stream().sorted().toList());
                    assertEquals(
                            probed.contains(ToolCapability.COMPLETE_PARAMS_QUERY)
                                    ? DiscoveryMode.COMPLETE
                                    : DiscoveryMode.PARTIAL_DISCOVERY,
                            schema.mode(),
                            "the mode follows the probed COMPLETE_PARAMS_QUERY, never the version");
                    assertTrue(schema.drift().isClean(), schema.drift()::describe);
                    assertFalse(names.isEmpty(), "the release declared no parameter");
                });
    }

    private static Path cometFixture(String release, String argument) {
        return MatrixSupport.root()
                .resolve("cometgui-params-comet/src/test/resources/fixtures/comet")
                .resolve(release)
                .resolve(MatrixSupport.host().id())
                .resolve("-p".equals(argument) ? "comet-p.params" : "comet-q.params");
    }

    // ------------------------------------------------------------------------------------------
    // Percolator
    // ------------------------------------------------------------------------------------------

    /**
     * The Percolator checks over the synthetic PIN, for a managed or a registered build.
     *
     * @param row the row
     * @param installed the offer, installed or registered
     * @param work an empty directory of the row's own
     * @param goldenKey the directory under {@code goldens/} its reviewed expectations live in
     */
    private static void percolator(Row row, ToolOffer installed, Path work, String goldenKey) {
        Path executable = installed.installedPath().orElseThrow();
        Set<ToolCapability> probed = EnumSet.noneOf(ToolCapability.class);
        installed.capabilities().forEach(declared -> probed.add(declared.capability()));
        row.observe("goldenKey", goldenKey);
        row.observe("xmlCapable", probed.contains(ToolCapability.XML_OUTPUT));
        Map<String, Object> metrics = new LinkedHashMap<>();
        row.observe("metrics", metrics);
        row.check(
                "over CometGUI's synthetic PIN, with an enabled stage needing XML: -X exactly"
                        + " when the probe observed XML_OUTPUT; every requested output written and"
                        + " parsed",
                () -> {
                    Path pin = SyntheticPin.writeForCapabilityProbe(Files.createDirectories(work));
                    row.observe("fixtureDigest", MatrixSupport.sha256(pin));
                    Path out = Files.createDirectories(work.resolve("out-xml"));
                    PercolatorCommand command =
                            PercolatorCommands.build(request(executable, pin, out, probed, true));
                    List<String> argv = command.command().argv();
                    row.observe(
                            "argv",
                            argv.subList(1, argv.size()).stream()
                                    .map(
                                            argument ->
                                                    argument.startsWith(work.toString())
                                                            ? "<work>"
                                                                    + argument.substring(
                                                                            work.toString()
                                                                                    .length())
                                                            : argument)
                                    .toList());
                    boolean xml = probed.contains(ToolCapability.XML_OUTPUT);
                    assertEquals(
                            xml,
                            argv.contains(PercolatorOption.XML_OUTPUT.spelling()),
                            "-X requested exactly when XML_OUTPUT was observed: " + argv);
                    assertEquals(xml, command.writesXml(), "the command's own account of -X");
                    if (!xml) {
                        assertTrue(
                                command.omission(PercolatorOption.XML_OUTPUT).isPresent(),
                                "a build without XML_OUTPUT says why -X was left out");
                    }
                    ToolRunOutcome outcome = runner(PERCOLATOR_BOUND).run(command.command());
                    assertTrue(outcome.exitedZero(), outcome::joinedOutput);
                    for (Map.Entry<PercolatorArtefact, Path> artefact :
                            command.artefacts().entrySet()) {
                        assertTrue(
                                Files.isRegularFile(artefact.getValue())
                                        && Files.size(artefact.getValue()) > 0,
                                "requested and not written: " + artefact);
                    }
                    tables(command, metrics);
                    if (xml) {
                        PoutDocument pout =
                                PoutDocument.read(
                                        command.artefacts().get(PercolatorArtefact.POUT_XML));
                        assertEquals(PoutDocument.NAMESPACE, pout.namespace());
                        metrics.put("pout.psms", pout.psmCount());
                        metrics.put("pout.decoyPsms", pout.decoyPsmCount());
                        metrics.put("pout.peptides", pout.peptideCount());
                    }
                });
        row.check(
                "no enabled stage needs XML: no -X, whatever the build can do",
                () -> {
                    PercolatorCommand command =
                            PercolatorCommands.build(
                                    request(
                                            executable,
                                            work.resolve("probe.pin"),
                                            work.resolve("out-plain"),
                                            probed,
                                            false));
                    assertFalse(
                            command.command()
                                    .argv()
                                    .contains(PercolatorOption.XML_OUTPUT.spelling()),
                            command.command().argv()::toString);
                    assertFalse(command.writesXml());
                });
        row.check(
                "the same build without XML_OUTPUT in its probed set: -X left out, and said so",
                () -> {
                    Set<ToolCapability> withoutXml = EnumSet.copyOf(probed);
                    withoutXml.remove(ToolCapability.XML_OUTPUT);
                    withoutXml.remove(ToolCapability.XML_DECOY_OUTPUT);
                    PercolatorCommand command =
                            PercolatorCommands.build(
                                    request(
                                            executable,
                                            work.resolve("probe.pin"),
                                            work.resolve("out-noxml"),
                                            withoutXml,
                                            true));
                    assertFalse(
                            command.command()
                                    .argv()
                                    .contains(PercolatorOption.XML_OUTPUT.spelling()),
                            command.command().argv()::toString);
                    assertTrue(command.omission(PercolatorOption.XML_OUTPUT).isPresent());
                });
    }

    /** The product's request, with the default settings mapped as the workflow maps them. */
    private static PercolatorRequest request(
            Path executable, Path pin, Path out, Set<ToolCapability> probed, boolean xmlNeeded) {
        PercolatorSettings settings = PercolatorSettings.defaults();
        Map<PercolatorOption, String> values = new EnumMap<>(PercolatorOption.class);
        for (PercolatorSetting setting : PercolatorSetting.values()) {
            PercolatorOption option =
                    switch (setting) {
                        case TEST_FDR -> PercolatorOption.TEST_FDR;
                        case TRAIN_FDR -> PercolatorOption.TRAIN_FDR;
                        case RANDOM_SEED -> PercolatorOption.SEED;
                        case MAXIMUM_ITERATIONS -> PercolatorOption.MAX_ITERATIONS;
                        case THREAD_COUNT -> PercolatorOption.NUM_THREADS;
                    };
            values.put(option, settings.valueText(setting));
        }
        return new PercolatorRequest(executable, pin, out, probed, xmlNeeded, values);
    }

    /** Parses every table and the weights, with counts this test makes itself beside them. */
    private static void tables(PercolatorCommand command, Map<String, Object> metrics)
            throws Exception {
        for (PercolatorArtefact artefact :
                List.of(
                        PercolatorArtefact.TARGET_PSMS,
                        PercolatorArtefact.TARGET_PEPTIDES,
                        PercolatorArtefact.DECOY_PSMS,
                        PercolatorArtefact.DECOY_PEPTIDES)) {
            Path file = command.artefacts().get(artefact);
            if (file == null) {
                continue;
            }
            String name = artefact.fileName();
            ResultTable table = ResultTableReader.readAll(file);
            long lines = Files.readAllLines(file, StandardCharsets.UTF_8).size() - 1L;
            assertEquals(lines, table.rows().size(), name + ": parsed rows against lines - 1");
            assertEquals(lines, table.counts().rows(), name + ": counted rows");
            boolean peptides =
                    artefact == PercolatorArtefact.TARGET_PEPTIDES
                            || artefact == PercolatorArtefact.DECOY_PEPTIDES;
            long passing =
                    table.rows().stream()
                            .filter(
                                    row ->
                                            peptides
                                                    ? PeptideQValueFilter.DEFAULT.passes(row)
                                                    : PsmQValueFilter.DEFAULT.passes(row))
                            .count();
            assertEquals(
                    independentAtOrBelow(table, "0.01"),
                    passing,
                    name + ": the product's default filter against this test's own count");
            metrics.put(name + ".rows", table.rows().size());
            metrics.put(name + ".knownQValues", table.counts().knownQValues());
            metrics.put(name + ".passingDefaultCutoff", passing);
            for (String cutoff : CUTOFFS) {
                metrics.put(name + ".qAtOrBelow." + cutoff, independentAtOrBelow(table, cutoff));
            }
            metrics.put(name + ".sha256", MatrixSupport.sha256(file));
        }
        Path weightsFile = command.artefacts().get(PercolatorArtefact.WEIGHTS);
        if (weightsFile != null) {
            LearnedWeights weights = WeightsReader.read(weightsFile);
            metrics.put("weights.splits", weights.splitCount());
            metrics.put("weights.features", weights.featureNames());
            metrics.put("weights.sha256", MatrixSupport.sha256(weightsFile));
        }
    }

    /**
     * Rows whose q-value text is at or below a cut-off, read as a decimal here, not by the product.
     */
    static long independentAtOrBelow(ResultTable table, String cutoff) {
        BigDecimal limit = new BigDecimal(cutoff);
        long count = 0;
        for (ResultRow row : table.rows()) {
            try {
                if (new BigDecimal(row.qValue().text().trim()).compareTo(limit) <= 0) {
                    count++;
                }
            } catch (NumberFormatException unknown) {
                // an unknown q-value passes no cut-off
            }
        }
        return count;
    }

    // ------------------------------------------------------------------------------------------
    // PDV and the converter
    // ------------------------------------------------------------------------------------------

    private static void javaTool(Row row, ToolOffer installed) {
        row.observe(
                "functionalRuns",
                installed.tool() == ToolName.PDV
                        ? "bash scripts/verify-pdv-gates.sh (PHASE-11 harness: real PDV figures,"
                                + " control API, mzTab)"
                        : "bash scripts/verify-limelight-gates.sh (PHASE-12 harness: real"
                                + " conversions, schema validation)");
        row.check(
                "identified by the probe's identity stage, with no capability to observe",
                () -> {
                    assertTrue(
                            installed.artefactIdentity().isPresent()
                                    || installed.installedPath().isPresent(),
                            installed::toString);
                    assertEquals(List.of(), MatrixSupport.capabilityIds(installed));
                    assertTrue(Files.isReadable(installed.installedPath().orElseThrow()));
                });
    }

    // ------------------------------------------------------------------------------------------
    // A registered local Percolator 3.09, not a managed offer
    // ------------------------------------------------------------------------------------------

    private static void localPercolator(String host, List<String> failed) throws IOException {
        String wrapper = System.getProperty(MatrixSupport.LOCAL_309, "");
        if (wrapper.isBlank()) {
            return;
        }
        Row row = Row.start("local:percolator:3.09:" + host);
        ToolOffer[] registered = new ToolOffer[1];
        boolean ok =
                row.check(
                        "registered through the Tool Manager as a local binary and probed",
                        () -> {
                            ToolOffer offer =
                                    manager.registerLocalBinary(
                                            ToolName.PERCOLATOR, Path.of(wrapper).toAbsolutePath());
                            assertEquals(ToolOrigin.LOCAL, offer.origin());
                            assertEquals("3.09", offer.version().text().substring(0, 4));
                            row.observe("version", offer.version().text());
                            row.observe("capabilities", MatrixSupport.capabilityIds(offer));
                            registered[0] = offer;
                        });
        if (ok) {
            percolator(
                    row,
                    registered[0],
                    Files.createDirectories(temporary.resolve("local-3.09")),
                    "percolator-3.09-local");
        }
        try {
            row.finish();
        } catch (AssertionError red) {
            failed.add(red.getMessage());
        }
    }

    private static ToolRunner runner(Duration bound) {
        return new ToolRunner(processes, bound);
    }
}
