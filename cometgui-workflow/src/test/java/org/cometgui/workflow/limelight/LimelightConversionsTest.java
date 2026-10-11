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

import static org.cometgui.workflow.limelight.LimelightFixtures.TARGETS;
import static org.cometgui.workflow.limelight.LimelightFixtures.VERSION;
import static org.cometgui.workflow.limelight.LimelightFixtures.argv;
import static org.cometgui.workflow.limelight.LimelightFixtures.inputs;
import static org.cometgui.workflow.limelight.LimelightFixtures.md5;
import static org.cometgui.workflow.limelight.LimelightFixtures.sha256;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.results.filtering.DisplayFilters;
import org.cometgui.results.filtering.PeptideQValueFilter;
import org.cometgui.results.filtering.PsmQValueFilter;
import org.cometgui.tools.limelight.LimelightConversionException;
import org.cometgui.tools.limelight.LimelightConversionRequest;
import org.cometgui.tools.limelight.LimelightConverter;
import org.cometgui.tools.limelight.LimelightQValueCutoff;
import org.cometgui.workflow.limelight.LimelightFixtures.Converters;
import org.cometgui.workflow.limelight.LimelightFixtures.FinishedRun;
import org.cometgui.workflow.limelight.LimelightFixtures.Psm;
import org.cometgui.workflow.limelight.LimelightFixtures.ScriptedConverter;
import org.cometgui.workflow.limelight.LimelightFixtures.Writes;
import org.cometgui.workflow.steps.RunResultFiles;
import org.cometgui.workflow.storage.LimelightSettings;
import org.cometgui.workflow.storage.LimelightSettingsStore;
import org.cometgui.workflow.storage.ViewStateStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * A finished run converted through {@link LimelightConversions} with the converter behind a
 * scripted, recording process runner: phase 12 gate items 2, 5 and 8 (model halves), every
 * prerequisite refused by name before anything starts, and the record each conversion leaves.
 *
 * <p>Every expected checksum is computed here with the JDK's own digests; every expected path and
 * argument is typed out or built from the run's layout; every count of launches is the recording
 * runner's.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "the conversion's paths and read-only check are POSIX; Linux is where this runs")
class LimelightConversionsTest {

    @TempDir private Path root;

    @AfterEach
    void nothingExecuting() {
        LimelightFixtures.EXECUTING.clear();
    }

    /** One scripted world: a finished run, the converter installed, a recording runner. */
    private record World(
            FinishedRun run,
            Path jar,
            Converters tools,
            ScriptedConverter converter,
            LimelightConversions conversions) {

        LimelightReadiness check(LimelightSettings settings) throws IOException {
            return conversions.check(run.layout(), run.identity(), settings, Optional.empty());
        }

        LimelightConverted convert(LimelightSettings settings) throws IOException {
            return conversions.convert(run.layout(), run.identity(), settings, Optional.empty());
        }

        LimelightConversionRefusedException refused(LimelightSettings settings) {
            return assertThrows(LimelightConversionRefusedException.class, () -> convert(settings));
        }

        Path output(int n) {
            return run.layout().limelightOutputDirectory().resolve("limelight-" + n + ".xml");
        }
    }

    private World world(Writes writes, String reports, List<Psm> psms, boolean decoyAttribute)
            throws IOException {
        FinishedRun run = LimelightFixtures.finishedRun(root, psms, decoyAttribute);
        Path jar = LimelightFixtures.converterJar(root.resolve("cache/limelight-converter"));
        Converters tools = new Converters(LimelightFixtures.installed(VERSION, jar));
        ScriptedConverter converter = new ScriptedConverter(writes, reports, run.layout());
        return new World(
                run, jar, tools, converter, LimelightFixtures.conversions(tools, converter));
    }

    private World world() throws IOException {
        return world(Writes.VALID, VERSION, TARGETS, false);
    }

    /** Nothing ran, nothing was recorded and nothing was written under outputs/limelight. */
    private static void nothingStarted(World world) throws IOException {
        assertAll(
                () -> assertEquals(List.of(), world.converter().starts(), "no process was started"),
                () -> assertEquals(List.of(), world.run().events(), "nothing was recorded"),
                () ->
                        assertFalse(
                                Files.exists(world.run().layout().limelightOutputDirectory()),
                                "nothing was written under outputs/limelight"));
    }

    private static LimelightRefusal only(
            LimelightConversionRefusedException refused, LimelightPrerequisite prerequisite) {
        assertEquals(
                List.of(prerequisite),
                refused.refusals().stream().map(LimelightRefusal::prerequisite).toList(),
                refused::getMessage);
        return refused.refusals().get(0);
    }

    /** Every regular file under a directory, by relative path, with its SHA-256. */
    private static Map<String, String> snapshot(Path directory) throws IOException {
        Map<String, String> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(directory)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                files.put(directory.relativize(file).toString(), sha256(file));
            }
        }
        return files;
    }

    private static List<String> changed(Map<String, String> before, Map<String, String> after) {
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, String> file : after.entrySet()) {
            if (!file.getValue().equals(before.get(file.getKey()))) {
                changed.add(file.getKey());
            }
        }
        for (String name : before.keySet()) {
            if (!after.containsKey(name)) {
                changed.add(name + " (removed)");
            }
        }
        return changed;
    }

    /** The PSM q-value filter the file carries, read here with a pattern of its own. */
    private static String psmQFilter(Path limelightXml) throws IOException {
        Matcher matcher =
                Pattern.compile(
                                "<filterable_psm_annotation_type name=\"q-value\"[^>]*"
                                        + "default_filter_value=\"([^\"]*)\"")
                        .matcher(Files.readString(limelightXml, StandardCharsets.UTF_8));
        List<String> found = new ArrayList<>();
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        assertEquals(1, found.size(), "one PSM q-value annotation: " + found);
        return found.get(0);
    }

    private static int count(Path file, String text) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        int count = 0;
        for (int at = content.indexOf(text); at >= 0; at = content.indexOf(text, at + 1)) {
            count++;
        }
        return count;
    }

    // ------------------------------------------------------------------------------ the answer --

    @Test
    @DisplayName(
            "a finished run that meets every prerequisite is ready; the check starts nothing and"
                    + " writes nothing, and resolves the converter's inputs read-only")
    void aFinishedRunIsReady() throws IOException {
        World world = world();
        LimelightReadiness readiness = world.check(LimelightSettings.DEFAULTS);
        RunLayout layout = world.run().layout();
        assertAll(
                () -> assertTrue(readiness.ready(), () -> readiness.refusals().toString()),
                () -> assertEquals(world.run().identity().runId(), readiness.runId()),
                () -> assertEquals(layout.cometParamsFile(), readiness.cometParams()),
                () ->
                        assertEquals(
                                layout.root().resolve("outputs/percolator/pout.xml"),
                                readiness.percolatorXml()),
                () ->
                        assertEquals(
                                layout.root().resolve("outputs/comet"),
                                readiness.pepXmlDirectory()),
                () -> assertEquals(world.run().fasta(), readiness.fasta()),
                () ->
                        assertEquals(
                                Optional.of(
                                        layout.root().resolve("outputs/limelight/limelight-1.xml")),
                                readiness.output()),
                () ->
                        assertEquals(
                                Optional.of(
                                        new LimelightConverterInstallation(
                                                org.cometgui.domain.tools.ToolVersion.parse(
                                                        VERSION),
                                                world.jar())),
                                readiness.converter()),
                () ->
                        assertEquals(
                                Optional.empty(), readiness.refusal(LimelightPrerequisite.FASTA)),
                () -> assertEquals(LimelightSettings.DEFAULTS, readiness.settings()));
        nothingStarted(world);
        assertFalse(Files.exists(layout.resultsDirectory()), "the check wrote nothing");
    }

    // ----------------------------------------------------------------- the conversion, gate 1 --

    @Test
    @DisplayName(
            "a conversion records limelight.converting before the converter starts, keeps a valid"
                    + " file read-only, and records limelight.converted with checksums equal to"
                    + " this test's own")
    void aConversionIsRecordedAndKept() throws IOException {
        World world = world();
        RunLayout layout = world.run().layout();
        Path file = world.output(1);

        LimelightConverted converted = world.convert(LimelightSettings.DEFAULTS);

        Path copy = layout.root().resolve("results/limelight/limelight-1.xml.pout.xml");
        List<String> expectedArgv =
                List.of(
                        JavaRuntimeOfThisTest.LAUNCHER,
                        "-jar",
                        world.jar().toString(),
                        "-c",
                        layout.cometParamsFile().toString(),
                        "-p",
                        copy.toString(),
                        "-d",
                        layout.root().resolve("outputs/comet").toString(),
                        "-f",
                        world.run().fasta().toString(),
                        "-o",
                        LimelightConverter.stagingPath(file).toString(),
                        "-q",
                        "0.01");
        List<ProvenanceEvent> events = world.run().events();
        assertEquals(
                List.of(
                        ProvenanceEventType.LIMELIGHT_CONVERTING,
                        ProvenanceEventType.LIMELIGHT_CONVERTED),
                events.stream().map(ProvenanceEvent::type).toList());
        ProvenanceEvent started = events.get(0);
        ProvenanceEvent done = events.get(1);
        Map<String, String> payload = done.payload();
        Path log = layout.root().resolve("logs/limelight-converter.log");
        String given = world.converter().givenPout().get(0);
        assertAll(
                () -> assertEquals(file, converted.file()),
                () -> assertTrue(Files.isRegularFile(file)),
                () -> assertEquals(sha256(file), converted.hashes().sha256()),
                () -> assertEquals(md5(file), converted.hashes().md5()),
                () -> assertEquals(3897, count(file, "<psm "), "the file is the known-good copy"),
                () -> assertEquals("0.01", psmQFilter(file)),
                () ->
                        assertEquals(
                                Set.of(
                                        PosixFilePermission.OWNER_READ,
                                        PosixFilePermission.GROUP_READ,
                                        PosixFilePermission.OTHERS_READ),
                                Files.getPosixFilePermissions(file),
                                "the kept file is read-only"),
                () -> assertFalse(Files.exists(LimelightConverter.stagingPath(file))),
                () -> assertEquals(2, world.converter().starts().size(), "--version, then convert"),
                () ->
                        assertEquals(
                                List.of("--version"),
                                world.converter().starts().get(0).argv().subList(3, 4),
                                "the version is asked first"),
                () ->
                        assertEquals(
                                List.of(1),
                                world.converter().eventsAtStart(),
                                "limelight.converting was in the log when the converter started"),
                () -> assertEquals(expectedArgv, world.converter().conversions().get(0).argv()),
                () -> assertEquals(expectedArgv, argv(done)),
                () -> assertEquals(expectedArgv, argv(started)),
                () -> assertEquals(expectedArgv, converted.argv()),
                () ->
                        assertTrue(
                                given.contains("p:psm_id=\"k562_3_101_2_1\"")
                                        && given.contains("<psm_id>k562_4_201_2_1</psm_id>")
                                        && !given.contains(
                                                layout.cometOutputDirectory().toString()),
                                "the converter is given the PSM ids without the directory: "
                                        + given),
                () -> assertFalse(Files.exists(copy), "the copy is deleted afterwards"),
                () -> assertEquals("run-0001", payload.get("run.id")),
                () -> assertEquals("limelight-converter", payload.get("tool")),
                () -> assertEquals(VERSION, payload.get("tool.version")),
                () -> assertEquals(world.jar().toString(), payload.get("converter.jar.path")),
                () ->
                        assertEquals(
                                LimelightFixtures.JAR_SHA256, payload.get("converter.jar.sha256")),
                () -> assertEquals(md5(world.jar()), payload.get("converter.jar.md5")),
                () -> assertEquals("0.01", payload.get("limelight.q-cutoff")),
                () -> assertEquals("false", payload.get("limelight.import-decoys")),
                () -> assertEquals("false", payload.get("limelight.open-mod")),
                () -> assertFalse(payload.containsKey("limelight.independent-decoy-prefix")),
                () -> assertEquals(file.toString(), payload.get("output.path")),
                () -> assertEquals(sha256(file), payload.get("output.sha256")),
                () -> assertEquals(md5(file), payload.get("output.md5")),
                () -> assertEquals(Long.toString(Files.size(file)), payload.get("output.size")),
                () -> assertEquals("3897", payload.get("output.psms")),
                () -> assertEquals("0.01", payload.get("output.q-cutoff")),
                () -> assertEquals("0", payload.get("process.exit-code")),
                () -> assertEquals(log.toString(), payload.get("log.path")),
                () -> assertEquals(sha256(log), payload.get("log.sha256")),
                () -> assertEquals(md5(log), payload.get("log.md5")),
                () ->
                        assertTrue(
                                Files.readString(log, StandardCharsets.UTF_8)
                                        .contains("Error printing runtime information."),
                                "the converter's output is in its log"),
                () -> assertEquals(Optional.of(log), converted.log()),
                () -> assertEquals("LANG=C.UTF-8", payload.get("process.environment")),
                () ->
                        assertTrue(
                                Files.readString(log, StandardCharsets.UTF_8)
                                        .contains("Validating Limelight XML... Done."),
                                "standard output reaches the log too"),
                () ->
                        assertTrue(
                                payload.entrySet().containsAll(started.payload().entrySet()),
                                "limelight.converted repeats limelight.converting"),
                () ->
                        assertFalse(
                                started.payload().containsKey("output.sha256"),
                                "nothing about the output is claimed before it exists"));
        Map<String, String> expectedInputs = new LinkedHashMap<>();
        expectedInputs.put(
                layout.cometParamsFile().toString(),
                "comet-params "
                        + md5(layout.cometParamsFile())
                        + " "
                        + sha256(layout.cometParamsFile()));
        Path pout = world.run().pout();
        expectedInputs.put(pout.toString(), "percolator-xml " + md5(pout) + " " + sha256(pout));
        Path givenFile = root.resolve("given.xml");
        Files.writeString(givenFile, given, StandardCharsets.UTF_8);
        expectedInputs.put(
                copy.toString(),
                "percolator-xml-for-converter " + md5(givenFile) + " " + sha256(givenFile));
        for (String base : List.of("k562_3", "k562_4")) {
            Path pepXml = world.run().pepXml(base);
            expectedInputs.put(pepXml.toString(), "pepxml " + md5(pepXml) + " " + sha256(pepXml));
        }
        Path fasta = world.run().fasta();
        expectedInputs.put(fasta.toString(), "fasta " + md5(fasta) + " " + sha256(fasta));
        assertEquals(expectedInputs, inputs(done));
        assertEquals(expectedInputs, inputs(started));
    }

    @Test
    @DisplayName(
            "a second conversion writes limelight-2.xml and its own log, and leaves the first file"
                    + " as it was")
    void aSecondConversionIsANewFile() throws IOException {
        World world = world();
        world.convert(LimelightSettings.DEFAULTS);
        String first = sha256(world.output(1));

        LimelightConverted second = world.convert(LimelightSettings.DEFAULTS);

        Path logs = world.run().layout().logsDirectory();
        assertAll(
                () -> assertEquals(world.output(2), second.file()),
                () -> assertEquals(first, sha256(world.output(1))),
                () ->
                        assertEquals(
                                Optional.of(logs.resolve("limelight-converter.1.log")),
                                second.log()),
                () -> assertTrue(Files.isRegularFile(logs.resolve("limelight-converter.log"))),
                () ->
                        assertEquals(
                                2,
                                world.run()
                                        .events(ProvenanceEventType.LIMELIGHT_CONVERTED)
                                        .size()));
    }

    @Test
    @DisplayName("a named output is written where it is named, and only if nothing is there")
    void aNamedOutput() throws IOException {
        World world = world();
        Path named = Files.createDirectories(root.resolve("elsewhere")).resolve("mine.xml");

        LimelightConverted converted =
                world.conversions()
                        .convert(
                                world.run().layout(),
                                world.run().identity(),
                                LimelightSettings.DEFAULTS,
                                Optional.of(named));

        assertEquals(named, converted.file());
        assertTrue(Files.isRegularFile(named));
        assertFalse(Files.exists(world.run().layout().limelightOutputDirectory()));
    }

    // ------------------------------------------------------------------------------- gate 2 --

    @ParameterizedTest(name = "[{index}] the converter {0}")
    @EnumSource(
            value = Writes.class,
            names = {"NOTHING", "EMPTY", "EXITS_ONE"})
    @DisplayName(
            "gate 2: exit 0 with no file, exit 0 with an empty file, and exit 1 each fail the"
                    + " conversion, record limelight.unconverted with no output checksum, and leave"
                    + " nothing at the final path")
    void gate2AFailedConversionKeepsNothing(Writes writes) throws IOException {
        World world = world(writes, VERSION, TARGETS, false);
        Path file = world.output(1);

        LimelightConversionException failed =
                assertThrows(
                        LimelightConversionException.class,
                        () -> world.convert(LimelightSettings.DEFAULTS));

        LimelightConversionException.Failure expected =
                switch (writes) {
                    case NOTHING -> LimelightConversionException.Failure.NO_OUTPUT;
                    case EMPTY -> LimelightConversionException.Failure.EMPTY_OUTPUT;
                    default -> LimelightConversionException.Failure.EXITED_NON_ZERO;
                };
        String wire =
                switch (writes) {
                    case NOTHING -> "no-output";
                    case EMPTY -> "empty-output";
                    default -> "exited-non-zero";
                };
        List<ProvenanceEvent> events = world.run().events();
        assertEquals(
                List.of(
                        ProvenanceEventType.LIMELIGHT_CONVERTING,
                        ProvenanceEventType.LIMELIGHT_CONVERSION_FAILED),
                events.stream().map(ProvenanceEvent::type).toList());
        Map<String, String> payload = events.get(1).payload();
        assertAll(
                () -> assertEquals(expected, failed.failure()),
                () -> assertEquals(1, world.converter().conversions().size()),
                () -> assertFalse(Files.exists(file), "nothing at the final path"),
                () -> assertFalse(Files.exists(LimelightConverter.stagingPath(file))),
                () -> assertEquals(wire, payload.get("limelight.failure")),
                () -> assertEquals(failed.getMessage(), payload.get("message")),
                () ->
                        assertTrue(
                                failed.getMessage()
                                                .contains(
                                                        "Reading comet params into memory... Done.")
                                        && failed.getMessage()
                                                .contains("Error printing runtime information."),
                                "both streams reach the judge: " + failed.getMessage()),
                () -> assertEquals(file.toString(), payload.get("output.path")),
                () -> assertFalse(payload.containsKey("output.sha256")),
                () -> assertFalse(payload.containsKey("output.md5")),
                () ->
                        assertEquals(
                                world.run()
                                        .layout()
                                        .logsDirectory()
                                        .resolve("limelight-converter.log")
                                        .toString(),
                                payload.get("log.path")),
                () ->
                        assertEquals(
                                List.of(),
                                world.run().events(ProvenanceEventType.LIMELIGHT_CONVERTED)),
                () ->
                        assertFalse(
                                Files.exists(
                                        world.run()
                                                .layout()
                                                .limelightWorkDirectory()
                                                .resolve("limelight-1.xml.pout.xml"))));
    }

    @Test
    @DisplayName(
            "a converter that cannot be started at all is recorded as could-not-run, after its"
                    + " limelight.converting")
    void aConverterThatCannotStart() throws IOException {
        World world = world(Writes.CANNOT_START, VERSION, TARGETS, false);

        IOException failed =
                assertThrows(IOException.class, () -> world.convert(LimelightSettings.DEFAULTS));

        List<ProvenanceEvent> events = world.run().events();
        assertAll(
                () -> assertFalse(failed instanceof LimelightConversionRefusedException),
                () ->
                        assertEquals(
                                List.of(
                                        ProvenanceEventType.LIMELIGHT_CONVERTING,
                                        ProvenanceEventType.LIMELIGHT_CONVERSION_FAILED),
                                events.stream().map(ProvenanceEvent::type).toList()),
                () ->
                        assertEquals(
                                "could-not-run", events.get(1).payload().get("limelight.failure")),
                () -> assertFalse(Files.exists(world.output(1))));
    }

    // ------------------------------------------------------------------- gate 5, model half --

    @Test
    @DisplayName(
            "gate 5: the Limelight cutoff defaults to 0.01, with nothing saved, and reaches -q and"
                    + " the file")
    void gate5TheDefault() throws IOException {
        World world = world();
        LimelightSettings saved = LimelightSettingsStore.read(world.run().layout());

        world.convert(saved);

        assertAll(
                () -> assertEquals(new LimelightQValueCutoff(0.01), saved.qCutoff()),
                () -> assertEquals(LimelightSettings.DEFAULTS, saved),
                () -> assertFalse(Files.exists(world.run().layout().limelightSettingsFile())),
                () ->
                        assertEquals(
                                "0.01",
                                ScriptedConverter.after(
                                        world.converter().conversions().get(0).argv(), "-q")),
                () -> assertEquals("0.01", psmQFilter(world.output(1))));
    }

    @Test
    @DisplayName(
            "gate 5: changing the Limelight cutoff writes only its own file, changes the next"
                    + " conversion's -q and the file's PSM q-value filter, and no display filter")
    void gate5ChangingTheLimelightCutoff() throws IOException {
        World world = world();
        RunLayout layout = world.run().layout();
        ViewStateStore.write(
                layout,
                new DisplayFilters(
                        PsmQValueFilter.parse("0.02"), PeptideQValueFilter.parse("0.03")));
        world.convert(LimelightSettings.DEFAULTS);
        Map<String, String> before = snapshot(layout.root());

        world.conversions()
                .saveSettings(
                        layout,
                        LimelightSettings.DEFAULTS.withQCutoff(
                                LimelightSettings.parseCutoff("0.05")));

        Map<String, String> after = snapshot(layout.root());
        assertEquals(List.of("results/limelight-settings.json"), changed(before, after));
        assertEquals(
                new DisplayFilters(
                        PsmQValueFilter.parse("0.02"), PeptideQValueFilter.parse("0.03")),
                ViewStateStore.read(layout).filters(),
                "the display filters are what they were");

        LimelightConverted second = world.convert(LimelightSettingsStore.read(layout));

        ProvenanceEvent done = world.run().events(ProvenanceEventType.LIMELIGHT_CONVERTED).get(1);
        assertAll(
                () -> assertEquals(world.output(2), second.file()),
                () ->
                        assertEquals(
                                "0.05",
                                ScriptedConverter.after(
                                        world.converter().conversions().get(1).argv(), "-q")),
                () -> assertEquals("0.05", psmQFilter(world.output(2))),
                () -> assertEquals("0.01", psmQFilter(world.output(1)), "the first file is kept"),
                () -> assertEquals("0.05", done.payload().get("limelight.q-cutoff")),
                () -> assertEquals("0.05", done.payload().get("output.q-cutoff")),
                () ->
                        assertEquals(
                                new DisplayFilters(
                                        PsmQValueFilter.parse("0.02"),
                                        PeptideQValueFilter.parse("0.03")),
                                ViewStateStore.read(layout).filters()));
    }

    @Test
    @DisplayName(
            "gate 5: changing the display filters writes only the view state and changes no"
                    + " Limelight cutoff")
    void gate5ChangingTheDisplayFilters() throws IOException {
        World world = world();
        RunLayout layout = world.run().layout();
        world.conversions()
                .saveSettings(
                        layout,
                        LimelightSettings.DEFAULTS.withQCutoff(
                                LimelightSettings.parseCutoff("0.02")));
        Map<String, String> before = snapshot(layout.root());

        ViewStateStore.write(
                layout,
                new DisplayFilters(
                        PsmQValueFilter.parse("0.05"), PeptideQValueFilter.parse("0.05")));

        assertEquals(List.of("results/view-state.json"), changed(before, snapshot(layout.root())));
        LimelightSettings now = LimelightSettingsStore.read(layout);
        assertEquals(LimelightSettings.parseCutoff("0.02"), now.qCutoff());
        world.convert(now);
        assertEquals(
                "0.02",
                ScriptedConverter.after(world.converter().conversions().get(0).argv(), "-q"));
    }

    // ------------------------------------------------------------------- gate 8, model half --

    @Test
    @DisplayName(
            "gate 8: against a run whose Percolator wrote no decoy PSMs, decoy import is shown"
                    + " unavailable with its reason, refused when saved, refused by the check, and"
                    + " refused before any process when converting")
    void gate8DecoyImportAgainstANoDecoyRun() throws IOException {
        World world = world();
        RunLayout layout = world.run().layout();
        LimelightSettings decoys = LimelightSettings.DEFAULTS.withImportDecoys(true);
        String expected =
                "Limelight decoy import is not available for this run: its Percolator output "
                        + world.run().pout()
                        + " holds no decoy PSMs, because Percolator was not run with -Z (decoy"
                        + " XML output). Asking the converter to import decoys from it would"
                        + " silently import none. Comet's internal decoys cannot be imported"
                        + " either: the converter looks every protein up in the FASTA, and"
                        + " Comet's generated decoys are not FASTA entries.";

        LimelightReadiness readiness = world.check(LimelightSettings.DEFAULTS);
        LimelightConversionRefusedException saved =
                assertThrows(
                        LimelightConversionRefusedException.class,
                        () -> world.conversions().saveSettings(layout, decoys));
        LimelightReadiness asked = world.check(decoys);
        LimelightConversionRefusedException converted = world.refused(decoys);

        assertAll(
                () -> assertTrue(readiness.ready()),
                () -> assertEquals(Optional.of(expected), readiness.decoyImportUnavailable()),
                () ->
                        assertEquals(
                                Optional.of(expected),
                                world.conversions().decoyImportUnavailable(layout)),
                () ->
                        assertEquals(
                                expected,
                                only(saved, LimelightPrerequisite.DECOY_IMPORT).message()),
                () -> assertFalse(Files.exists(layout.limelightSettingsFile()), "nothing saved"),
                () ->
                        assertEquals(
                                Optional.of(expected),
                                asked.refusal(LimelightPrerequisite.DECOY_IMPORT)
                                        .map(LimelightRefusal::message)),
                () ->
                        assertEquals(
                                expected,
                                only(converted, LimelightPrerequisite.DECOY_IMPORT).message()));
        nothingStarted(world);
    }

    @Test
    @DisplayName(
            "gate 8: a run whose Percolator wrote decoy PSMs offers decoy import, saves it, and"
                    + " refuses a conversion without it")
    void gate8ARunWithDecoys() throws IOException {
        List<Psm> withDecoys =
                List.of(
                        new Psm("k562_3", 101, false),
                        new Psm("k562_3", 102, true),
                        new Psm("k562_4", 201, false));
        World world = world(Writes.VALID, VERSION, withDecoys, true);
        RunLayout layout = world.run().layout();

        LimelightReadiness readiness = world.check(LimelightSettings.DEFAULTS);
        world.conversions().saveSettings(layout, LimelightSettings.DEFAULTS.withImportDecoys(true));

        assertAll(
                () -> assertEquals(Optional.empty(), readiness.decoyImportUnavailable()),
                () ->
                        assertEquals(
                                Optional.empty(),
                                world.conversions().decoyImportUnavailable(layout)),
                () ->
                        assertTrue(
                                readiness
                                        .refusal(LimelightPrerequisite.DECOY_IMPORT)
                                        .orElseThrow()
                                        .message()
                                        .contains("holds 1 decoy PSMs"),
                                readiness::toString),
                () -> assertTrue(LimelightSettingsStore.read(layout).importDecoys()),
                () -> assertTrue(world.check(LimelightSettingsStore.read(layout)).ready()));
        assertEquals(List.of(), world.converter().starts());
    }

    // -------------------------------------------------------- every prerequisite, by name --

    @Test
    @DisplayName("an executing run is refused, and nothing else of it is read")
    void anExecutingRun() throws IOException {
        World world = world();
        LimelightFixtures.EXECUTING.add(world.run().identity().runId());

        LimelightRefusal refusal =
                only(world.refused(LimelightSettings.DEFAULTS), LimelightPrerequisite.RUN_ENDED);

        assertEquals(
                "run run-0001 is executing: its results are not final and the workflow engine"
                        + " holds its event log. Convert it when the run has ended.",
                refusal.message());
        nothingStarted(world);
    }

    @Test
    @DisplayName("comet.params missing, or changed since the run recorded it, is refused by name")
    void cometParams() throws IOException {
        World world = world();
        Path params = world.run().layout().cometParamsFile();
        String recorded = sha256(params);
        Files.writeString(params, "decoy_search = 2\n", java.nio.file.StandardOpenOption.APPEND);
        String now = sha256(params);

        LimelightRefusal changed =
                only(world.refused(LimelightSettings.DEFAULTS), LimelightPrerequisite.COMET_PARAMS);
        Files.delete(params);
        LimelightRefusal missing =
                only(world.refused(LimelightSettings.DEFAULTS), LimelightPrerequisite.COMET_PARAMS);

        assertAll(
                () ->
                        assertEquals(
                                "the canonical Comet parameter file "
                                        + params
                                        + " of run run-0001 has changed since it was recorded:"
                                        + " its SHA-256 is now "
                                        + now
                                        + ", but run run-0001 recorded "
                                        + recorded
                                        + ". It is not converted from (R-RUN-02); rerun the stage"
                                        + " that produced it, or restore the file.",
                                changed.message()),
                () -> assertEquals(Optional.of(params), changed.file()),
                () ->
                        assertEquals(
                                "the canonical Comet parameter file "
                                        + params
                                        + " of run run-0001 does not exist or cannot be read; run"
                                        + " run-0001 recorded SHA-256 "
                                        + recorded,
                                missing.message()));
        nothingStarted(world);
    }

    @Test
    @DisplayName(
            "a pepXML missing, changed, or not among the run's recorded outputs is refused by name")
    void pepXml() throws IOException {
        World world = world();
        Path pepXml = world.run().pepXml("k562_4");
        Files.writeString(pepXml, "<changed/>");

        LimelightRefusal changed =
                only(world.refused(LimelightSettings.DEFAULTS), LimelightPrerequisite.COMET_PEPXML);
        Files.delete(pepXml);
        LimelightRefusal missing =
                only(world.refused(LimelightSettings.DEFAULTS), LimelightPrerequisite.COMET_PEPXML);

        assertAll(
                () -> assertEquals(Optional.of(pepXml), changed.file()),
                () ->
                        assertTrue(
                                changed.message().contains("has changed since it was recorded"),
                                changed::message),
                () -> assertEquals(Optional.of(pepXml), missing.file()),
                () -> assertTrue(missing.message().contains("does not exist"), missing::message));
        nothingStarted(world);
    }

    @Test
    @DisplayName(
            "a PSM id naming a pepXML the search did not record, or a file outside the pepXML"
                    + " directory, is refused by name")
    void psmIdsMustNameTheRunsOwnPepXml() throws IOException {
        World stranger =
                world(
                        Writes.VALID,
                        VERSION,
                        List.of(new Psm("k562_3", 1, false), new Psm("k562_9", 2, false)),
                        false);
        LimelightRefusal notRecorded =
                only(
                        stranger.refused(LimelightSettings.DEFAULTS),
                        LimelightPrerequisite.COMET_PEPXML);
        Path k5629 = stranger.run().layout().cometOutputDirectory().resolve("k562_9.pep.xml");
        assertAll(
                () -> assertEquals(Optional.of(k5629), notRecorded.file()),
                () ->
                        assertEquals(
                                "the Limelight converter would read "
                                        + k5629
                                        + ", derived from the Percolator PSM ids, and it is not one"
                                        + " of run run-0001's recorded Comet outputs"
                                        + " (k562_3.pep.xml, k562_4.pep.xml)",
                                notRecorded.message()));
        nothingStarted(stranger);
    }

    @Test
    @DisplayName("a PSM id under another directory is refused naming the id")
    void psmIdOutsideTheDirectory() throws IOException {
        World world = world();
        Path pout = world.run().pout();
        Files.writeString(pout, LimelightFixtures.pout("/elsewhere/comet/", TARGETS, false));
        LimelightFixtures.writeManifest(
                world.run().layout(),
                List.of(
                        LimelightFixtures.output(
                                RunResultFiles.PEP_XML_ROLE, world.run().pepXml("k562_3")),
                        LimelightFixtures.output(
                                RunResultFiles.PEP_XML_ROLE, world.run().pepXml("k562_4")),
                        LimelightFixtures.output(RunResultFiles.POUT_XML_ROLE, pout)));

        LimelightRefusal refusal =
                only(world.refused(LimelightSettings.DEFAULTS), LimelightPrerequisite.COMET_PEPXML);

        assertEquals(
                "the Percolator PSM id \"/elsewhere/comet/k562_3_101_2_1\" in "
                        + pout
                        + " names a Comet output that is not directly in "
                        + world.run().layout().cometOutputDirectory()
                        + ", so it is not one of this run's pepXML files",
                refusal.message());
        nothingStarted(world);
    }

    @Test
    @DisplayName(
            "a Percolator that wrote no XML, an XML changed since it was recorded, and a"
                    + " missing XML"
                    + " are each refused by name")
    void percolatorXml() throws IOException {
        World world = world();
        RunLayout layout = world.run().layout();
        Path pout = world.run().pout();
        LimelightFixtures.writeManifest(
                layout,
                List.of(
                        LimelightFixtures.output(
                                RunResultFiles.PEP_XML_ROLE, world.run().pepXml("k562_3")),
                        LimelightFixtures.output(
                                RunResultFiles.PEP_XML_ROLE, world.run().pepXml("k562_4"))));
        LimelightRefusal noXml =
                only(
                        world.refused(LimelightSettings.DEFAULTS),
                        LimelightPrerequisite.PERCOLATOR_XML);

        LimelightFixtures.writeManifest(
                layout,
                List.of(
                        LimelightFixtures.output(
                                RunResultFiles.PEP_XML_ROLE, world.run().pepXml("k562_3")),
                        LimelightFixtures.output(
                                RunResultFiles.PEP_XML_ROLE, world.run().pepXml("k562_4")),
                        LimelightFixtures.output(RunResultFiles.POUT_XML_ROLE, pout)));
        Files.writeString(
                pout,
                LimelightFixtures.pout(
                        layout.cometOutputDirectory() + "/", TARGETS.subList(0, 2), false));
        LimelightRefusal changed =
                only(
                        world.refused(LimelightSettings.DEFAULTS),
                        LimelightPrerequisite.PERCOLATOR_XML);
        Files.delete(pout);
        LimelightRefusal missing =
                only(
                        world.refused(LimelightSettings.DEFAULTS),
                        LimelightPrerequisite.PERCOLATOR_XML);

        assertAll(
                () ->
                        assertEquals(
                                "run run-0001's Percolator wrote no XML: its provenance records no"
                                        + " Percolator XML output "
                                        + pout
                                        + " (Percolator ran without XML output, or with a build"
                                        + " that cannot write it), and the Limelight converter"
                                        + " reads only Percolator XML",
                                noXml.message()),
                () ->
                        assertTrue(
                                changed.message().contains("has changed since it was recorded"),
                                changed::message),
                () -> assertEquals(Optional.of(pout), changed.file()),
                () -> assertTrue(missing.message().contains("does not exist"), missing::message));
        nothingStarted(world);
    }

    @Test
    @DisplayName(
            "a converter the Tool Manager does not offer as installed and verified is refused with"
                    + " its explanation, and nothing is installed by a check")
    void aConverterNotInstalled() throws IOException {
        World world = world();
        world.tools().offer(LimelightFixtures.converter(VERSION, ToolInstallState.NOT_INSTALLED));

        LimelightRefusal refusal =
                only(world.refused(LimelightSettings.DEFAULTS), LimelightPrerequisite.CONVERTER);

        assertAll(
                () ->
                        assertEquals(
                                "The Limelight converter 2.8.1 is not installed. It is"
                                        + " installed the"
                                        + " first time a run is converted (a download of 2,762,075"
                                        + " bytes), which no search waits for.",
                                refusal.message()),
                () ->
                        assertEquals(
                                Optional.empty(),
                                world.check(LimelightSettings.DEFAULTS).converter()),
                () -> assertEquals(List.of(), world.tools().installs()));
        nothingStarted(world);
    }

    @Test
    @DisplayName(
            "an installed converter that reports another version is not used: only --version was"
                    + " started, nothing was recorded")
    void aConverterThatIsNotWhatItWasOffered() throws IOException {
        World world = world(Writes.VALID, "2.8.0", TARGETS, false);

        LimelightRefusal refusal =
                only(world.refused(LimelightSettings.DEFAULTS), LimelightPrerequisite.CONVERTER);

        assertAll(
                () ->
                        assertEquals(
                                "the Limelight converter at "
                                        + world.jar()
                                        + " reports version 2.8.0, but the Tool Manager offered it"
                                        + " as 2.8.1; it is not used",
                                refusal.message()),
                () -> assertEquals(1, world.converter().starts().size()),
                () -> assertTrue(world.converter().starts().get(0).argv().contains("--version")),
                () -> assertEquals(List.of(), world.converter().conversions()),
                () -> assertEquals(List.of(), world.run().events()),
                () -> assertFalse(Files.exists(world.output(1))));
    }

    @Test
    @DisplayName(
            "the FASTA missing, or changed since the run recorded it, is refused naming the file")
    void fasta() throws IOException {
        World world = world();
        Path fasta = world.run().fasta();
        Files.writeString(fasta, ">sp|P2|B\nKKKK\n", java.nio.file.StandardOpenOption.APPEND);

        LimelightRefusal changed =
                only(world.refused(LimelightSettings.DEFAULTS), LimelightPrerequisite.FASTA);
        Files.delete(fasta);
        LimelightRefusal missing =
                only(world.refused(LimelightSettings.DEFAULTS), LimelightPrerequisite.FASTA);

        assertAll(
                () -> assertEquals(Optional.of(fasta), changed.file()),
                () ->
                        assertTrue(
                                changed.message()
                                        .startsWith(
                                                "the FASTA "
                                                        + fasta
                                                        + " of run run-0001 has changed since it"
                                                        + " was recorded: its SHA-256 is now "),
                                changed::message),
                () -> assertEquals(Optional.of(fasta), missing.file()),
                () -> assertTrue(missing.message().contains("does not exist"), missing::message));
        nothingStarted(world);
    }

    @Test
    @DisplayName(
            "an output that exists, whose staging file exists, that is relative, or whose directory"
                    + " is missing is refused; a conversion never overwrites")
    void output() throws IOException {
        World world = world();
        Path existing = Files.writeString(root.resolve("taken.xml"), "mine");
        Path staged = root.resolve("staged.xml");
        Files.writeString(LimelightConverter.stagingPath(staged), "mine");
        List<Optional<Path>> outputs =
                List.of(
                        Optional.of(existing),
                        Optional.of(staged),
                        Optional.of(Path.of("relative.xml")),
                        Optional.of(root.resolve("absent/out.xml")));
        for (Optional<Path> output : outputs) {
            LimelightConversionRefusedException refused =
                    assertThrows(
                            LimelightConversionRefusedException.class,
                            () ->
                                    world.conversions()
                                            .convert(
                                                    world.run().layout(),
                                                    world.run().identity(),
                                                    LimelightSettings.DEFAULTS,
                                                    output));
            only(refused, LimelightPrerequisite.OUTPUT);
        }
        assertEquals("mine", Files.readString(existing));
        nothingStarted(world);
    }

    @Test
    @DisplayName("an outputs/limelight that cannot be written to is refused")
    void anUnwritableOutputDirectory() throws IOException {
        World world = world();
        Path directory = Files.createDirectories(world.run().layout().limelightOutputDirectory());
        Set<PosixFilePermission> was = Files.getPosixFilePermissions(directory);
        Files.setPosixFilePermissions(
                directory,
                Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
        try {
            LimelightRefusal refusal =
                    only(world.refused(LimelightSettings.DEFAULTS), LimelightPrerequisite.OUTPUT);
            assertEquals(
                    "the Limelight output directory " + directory + " cannot be written to",
                    refusal.message());
        } finally {
            Files.setPosixFilePermissions(directory, was);
        }
        assertEquals(List.of(), world.converter().starts());
    }

    @Test
    @DisplayName(
            "an independent decoy prefix the converter cannot take is refused by the"
                    + " request's rule")
    void aBadPrefix() throws IOException {
        World world = world();
        LimelightRefusal refusal =
                only(
                        world.refused(
                                LimelightSettings.DEFAULTS.withIndependentDecoyPrefix(
                                        Optional.of("DE COY"))),
                        LimelightPrerequisite.OPTIONS);
        assertTrue(
                refusal.message().contains("cannot be blank or hold whitespace"), refusal::message);
        nothingStarted(world);
    }

    @Test
    @DisplayName("every unmet prerequisite is named at once, in order")
    void everyRefusalAtOnce() throws IOException {
        World world = world();
        Files.delete(world.run().layout().cometParamsFile());
        Files.delete(world.run().fasta());
        world.tools().offer(LimelightFixtures.converter(VERSION, ToolInstallState.FAILED));

        LimelightConversionRefusedException refused = world.refused(LimelightSettings.DEFAULTS);

        assertEquals(
                List.of(
                        LimelightPrerequisite.COMET_PARAMS,
                        LimelightPrerequisite.CONVERTER,
                        LimelightPrerequisite.FASTA),
                refused.refusals().stream().map(LimelightRefusal::prerequisite).toList());
        assertEquals(
                Optional.empty(),
                world.check(LimelightSettings.DEFAULTS).refusal(LimelightPrerequisite.OUTPUT));
        assertTrue(
                refused.getMessage()
                        .startsWith(
                                "the Limelight conversion was refused before anything was"
                                        + " started -- COMET_PARAMS: "),
                refused::getMessage);
        nothingStarted(world);
    }

    @Test
    @DisplayName(
            "a Percolator rerun is converted from the search of the run it was derived from: -d is"
                    + " that run's outputs/comet, the file and the record are the rerun's")
    void aDerivedRun() throws IOException {
        World world = world();
        FinishedRun derived = LimelightFixtures.derivedRun(world.run(), TARGETS);
        LimelightConversions conversions = world.conversions();

        LimelightConverted converted =
                conversions.convert(
                        derived.layout(),
                        derived.identity(),
                        LimelightSettings.DEFAULTS,
                        Optional.empty());

        List<String> argv = world.converter().conversions().get(0).argv();
        assertAll(
                () ->
                        assertEquals(
                                world.run().layout().cometOutputDirectory().toString(),
                                ScriptedConverter.after(argv, "-d")),
                () ->
                        assertEquals(
                                derived.layout()
                                        .limelightOutputDirectory()
                                        .resolve("limelight-1.xml"),
                                converted.file()),
                () ->
                        assertEquals(
                                1, derived.events(ProvenanceEventType.LIMELIGHT_CONVERTED).size()),
                () -> assertEquals(List.of(), world.run().events()));
    }

    @Test
    @DisplayName("the decoy-import explanation is the conversion request's own sentence")
    void theExplanationIsTheRequests() throws IOException {
        World world = world();
        assertEquals(
                LimelightConversionRequest.decoyImportUnavailable(
                        org.cometgui.tools.limelight.PercolatorXmlPsms.read(world.run().pout())),
                world.check(LimelightSettings.DEFAULTS).decoyImportUnavailable());
    }

    @Test
    @DisplayName(
            "a run without a provenance record is refused for its Percolator XML and its pepXML,"
                    + " both naming the record")
    void noProvenanceRecord() throws IOException {
        World world = world();
        Path record = world.run().layout().provenanceJsonFile();
        Files.delete(record);

        LimelightConversionRefusedException refused = world.refused(LimelightSettings.DEFAULTS);

        assertEquals(
                List.of(LimelightPrerequisite.PERCOLATOR_XML, LimelightPrerequisite.COMET_PEPXML),
                refused.refusals().stream().map(LimelightRefusal::prerequisite).toList());
        for (LimelightRefusal refusal : refused.refusals()) {
            assertEquals(Optional.of(record), refusal.file());
        }
        nothingStarted(world);
    }

    @Test
    @DisplayName("a search whose provenance records no pepXML is refused")
    void noRecordedPepXml() throws IOException {
        World world = world();
        LimelightFixtures.writeManifest(
                world.run().layout(),
                List.of(
                        LimelightFixtures.output(
                                RunResultFiles.POUT_XML_ROLE, world.run().pout())));

        LimelightRefusal refusal =
                only(world.refused(LimelightSettings.DEFAULTS), LimelightPrerequisite.COMET_PEPXML);

        assertEquals(
                "run run-0001's provenance records no complete Comet pepXML output in "
                        + world.run().layout().cometOutputDirectory()
                        + ", so there is no search for the converter to read",
                refusal.message());
        nothingStarted(world);
    }

    @Test
    @DisplayName(
            "when outputs/limelight does not exist yet, an outputs/ that cannot be written to is"
                    + " refused, naming both")
    void anUnwritableOutputsDirectory() throws IOException {
        World world = world();
        Path outputs = world.run().layout().outputsDirectory();
        Set<PosixFilePermission> was = Files.getPosixFilePermissions(outputs);
        Files.setPosixFilePermissions(
                outputs, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
        try {
            LimelightRefusal refusal =
                    only(world.refused(LimelightSettings.DEFAULTS), LimelightPrerequisite.OUTPUT);
            assertEquals(
                    "the Limelight output directory "
                            + world.run().layout().limelightOutputDirectory()
                            + " cannot be written to ("
                            + outputs
                            + " cannot be written to)",
                    refusal.message());
        } finally {
            Files.setPosixFilePermissions(outputs, was);
        }
        assertEquals(List.of(), world.converter().starts());
    }

    @Test
    @DisplayName(
            "decoy import is explained unavailable for a run with no Percolator XML and for one"
                    + " whose XML cannot be read")
    void decoyImportWithoutAReadableXml() throws IOException {
        World world = world();
        RunLayout layout = world.run().layout();
        Path pout = world.run().pout();
        Files.writeString(pout, "not xml");
        Optional<String> unreadable = world.conversions().decoyImportUnavailable(layout);
        Files.delete(pout);
        Optional<String> missing = world.conversions().decoyImportUnavailable(layout);

        assertAll(
                () ->
                        assertTrue(
                                unreadable
                                        .orElseThrow()
                                        .startsWith(
                                                "Limelight decoy import is not available for this"
                                                        + " run: its Percolator XML cannot be"
                                                        + " read: "),
                                unreadable::toString),
                () ->
                        assertEquals(
                                Optional.of(
                                        "Limelight decoy import is not available for this run: it"
                                                + " has no Percolator XML "
                                                + pout
                                                + "."),
                                missing));
    }

    @Test
    @DisplayName(
            "a converter JAR that carries no schema is refused before the conversion starts:"
                    + " only --version ran, nothing was recorded")
    void aConverterWithoutItsSchema() throws IOException {
        FinishedRun run = LimelightFixtures.finishedRun(root, TARGETS, false);
        Path jar =
                Files.createDirectories(root.resolve("cache/empty"))
                        .resolve("cometPercolator2LimelightXML.jar");
        try (java.util.zip.ZipOutputStream zip =
                new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("META-INF/MANIFEST.MF"));
            zip.write("Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        ScriptedConverter converter = new ScriptedConverter(Writes.VALID, VERSION, run.layout());
        LimelightConversions conversions =
                LimelightFixtures.conversions(
                        new Converters(LimelightFixtures.installed(VERSION, jar)), converter);

        LimelightConversionRefusedException refused =
                assertThrows(
                        LimelightConversionRefusedException.class,
                        () ->
                                conversions.convert(
                                        run.layout(),
                                        run.identity(),
                                        LimelightSettings.DEFAULTS,
                                        Optional.empty()));

        assertAll(
                () -> only(refused, LimelightPrerequisite.CONVERTER),
                () -> assertEquals(1, converter.starts().size(), "--version only"),
                () -> assertEquals(List.of(), converter.conversions()),
                () -> assertEquals(List.of(), run.events()));
    }

    @Test
    @DisplayName(
            "an independent decoy prefix reaches the argument array and the record, attached with"
                    + " '='")
    void aPrefixIsPassedAndRecorded() throws IOException {
        World world = world();

        world.convert(LimelightSettings.DEFAULTS.withIndependentDecoyPrefix(Optional.of("DECOY_")));

        ProvenanceEvent done = world.run().events(ProvenanceEventType.LIMELIGHT_CONVERTED).get(0);
        assertAll(
                () -> assertTrue(argv(done).contains("--independent-decoy-prefix=DECOY_")),
                () ->
                        assertTrue(
                                world.converter()
                                        .conversions()
                                        .get(0)
                                        .argv()
                                        .contains("--independent-decoy-prefix=DECOY_")),
                () ->
                        assertEquals(
                                "DECOY_",
                                done.payload().get("limelight.independent-decoy-prefix")));
    }

    @Test
    @DisplayName(
            "a converter that runs past its timeout is stopped through the stage, recorded as"
                    + " timed-out, and keeps nothing; its log lines reach the console under the"
                    + " conversion's stage")
    void aConversionThatTimesOut() throws IOException {
        FinishedRun run = LimelightFixtures.finishedRun(root, TARGETS, false);
        Path jar = LimelightFixtures.converterJar(root.resolve("cache/limelight-converter"));
        ScriptedConverter converter = new ScriptedConverter(Writes.HANGS, VERSION, run.layout());
        List<org.cometgui.domain.log.LogMessage> console =
                java.util.Collections.synchronizedList(new ArrayList<>());
        LimelightConversions conversions =
                LimelightFixtures.conversions(
                        new Converters(LimelightFixtures.installed(VERSION, jar)),
                        converter,
                        java.time.Duration.ofSeconds(1),
                        console::add);

        LimelightConversionException failed =
                assertThrows(
                        LimelightConversionException.class,
                        () ->
                                conversions.convert(
                                        run.layout(),
                                        run.identity(),
                                        LimelightSettings.DEFAULTS,
                                        Optional.empty()));

        List<ProvenanceEvent> events = run.events();
        assertAll(
                () ->
                        assertEquals(
                                LimelightConversionException.Failure.TIMED_OUT, failed.failure()),
                () ->
                        assertEquals(
                                ProvenanceEventType.LIMELIGHT_CONVERSION_FAILED,
                                events.get(1).type()),
                () -> assertEquals("timed-out", events.get(1).payload().get("limelight.failure")),
                () -> assertTrue(events.get(1).payload().containsKey("log.sha256")),
                () ->
                        assertFalse(
                                Files.exists(
                                        run.layout()
                                                .limelightOutputDirectory()
                                                .resolve("limelight-1.xml"))),
                () ->
                        assertTrue(
                                console.stream().anyMatch(LimelightConversionsTest::underTheStage),
                                console::toString));
    }

    @Test
    @DisplayName(
            "a Percolator rerun whose source run's pepXML changed is refused naming the source run"
                    + " and the file")
    void aDerivedRunWhoseSearchChanged() throws IOException {
        World world = world();
        FinishedRun derived = LimelightFixtures.derivedRun(world.run(), TARGETS);
        Path pepXml = world.run().pepXml("k562_3");
        Files.writeString(pepXml, "<changed/>");

        LimelightConversionRefusedException refused =
                assertThrows(
                        LimelightConversionRefusedException.class,
                        () ->
                                world.conversions()
                                        .convert(
                                                derived.layout(),
                                                derived.identity(),
                                                LimelightSettings.DEFAULTS,
                                                Optional.empty()));

        LimelightRefusal refusal = only(refused, LimelightPrerequisite.COMET_PEPXML);
        assertTrue(
                refusal.message()
                        .startsWith("the Comet pepXML " + pepXml + " of run run-0001 has changed"),
                refusal::message);
        assertEquals(List.of(), world.converter().starts());
    }

    private static boolean underTheStage(org.cometgui.domain.log.LogMessage message) {
        return message.stage()
                .map(
                        stage ->
                                "limelight-converter".equals(stage.id())
                                        && "Limelight conversion".equals(stage.displayName()))
                .orElse(false);
    }

    /** The java launcher this test JVM runs on, which the conversion uses. */
    private static final class JavaRuntimeOfThisTest {

        static final String LAUNCHER = launcher();

        private static String launcher() {
            try {
                return org.cometgui.tools.api.JavaRuntime.ofThisApplication().launcher().toString();
            } catch (IOException none) {
                throw new IllegalStateException(none);
            }
        }
    }
}
