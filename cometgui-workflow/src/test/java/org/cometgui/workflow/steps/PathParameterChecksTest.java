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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.cometgui.domain.project.ProjectId;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.parser.ReleaseDefaults;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.cometgui.workflow.storage.ProjectStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit 7b: every set parameter whose metadata names the validator {@code path} must name a readable
 * file before Comet starts, word for word, over made-up files. No Comet process is involved; {@link
 * RealPathParameterTest} holds the same check against the real binary.
 */
class PathParameterChecksTest {

    /** The bundled metadata's path parameters other than {@code database_name}, typed out. */
    private static final List<String> PATH_PARAMETERS =
            List.of(
                    "peff_obo",
                    "compoundmods_file",
                    "spectral_library_name",
                    "protein_modslist_file");

    /** {@code comet -q}'s placeholder for the spectral library in both bundled releases. */
    static final String PLACEHOLDER = "/some/path/speclib.file";

    @TempDir private Path directory;

    private FakeSearch fake;

    private ProjectLayout project;

    private PreRunChecks checks;

    @BeforeEach
    void setUp() throws IOException {
        fake = FakeSearch.create(directory);
        project = new ProjectLayout(fake.project());
        new ProjectStore(Clock.systemUTC()).create(project, new ProjectId("paths"));
        checks = FakeSearch.checks(FakeSearch.hashes(), false);
    }

    private CometParameters model() {
        return fake.model(DecoySource.COMET_INTERNAL_CONCATENATED);
    }

    private List<String> problems(CometParameters model) throws IOException {
        return checks.check(project, model, fake.spectra(), fake.selection(), IndexMode.NONE)
                .report()
                .problems();
    }

    private List<String> problems(String name, String value) throws IOException {
        return problems(model().withText(name, value, ValueOrigin.USER));
    }

    @Test
    @DisplayName(
            "the release default spectral_library_name, -q's placeholder, blocks the run with the"
                    + " parameter, its value and what to do")
    void thePlaceholderBlocks() throws IOException {
        CometParameters defaulted = model().resetToDefault("spectral_library_name");
        assertEquals(PLACEHOLDER, defaulted.text("spectral_library_name"));
        PreRunReport report =
                checks.check(project, defaulted, fake.spectra(), fake.selection(), IndexMode.NONE)
                        .report();
        assertEquals(
                List.of(
                        "spectral_library_name (Spectral library file) = /some/path/speclib.file"
                                + " does not exist or cannot be read; clear it to search without"
                                + " one, or choose the file"),
                report.problems());
        assertTrue(report.blocked());
        assertFalse(report.validation().hasErrors(), "the form of the placeholder is legal");
    }

    @Test
    @DisplayName(
            "each path parameter: missing, a directory, unreadable, relative and NUL are refused")
    void eachPathParameterIsChecked() throws IOException {
        Path missing = fake.root().resolve("inputs/none.file");
        Path folder = Files.createDirectories(fake.root().resolve("inputs/folder"));
        Path locked = Files.writeString(fake.root().resolve("inputs/locked.file"), "x\n");
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"));
        assertFalse(
                Files.isReadable(locked),
                "a file without read permission is still readable -- running as root? -- so the"
                        + " unreadable case cannot be proved");
        String withNul = fake.root().resolve("a") + "\0b";
        for (String name : PATH_PARAMETERS) {
            String label = model().definition(name).displayName();
            for (Path unusable : List.of(missing, folder, locked)) {
                assertEquals(
                        List.of(
                                name
                                        + " ("
                                        + label
                                        + ") = "
                                        + unusable
                                        + " does not exist or cannot be read; clear it to search"
                                        + " without one, or choose the file"),
                        problems(name, unusable.toString()),
                        name + " = " + unusable);
            }
            assertEquals(
                    List.of(
                            name
                                    + " ("
                                    + label
                                    + ") = inputs/x.file must name the file by its absolute path;"
                                    + " Comet runs in the run directory, where a relative path"
                                    + " would name another file"),
                    problems(name, "inputs/x.file"),
                    name + " relative");
            assertEquals(
                    List.of(
                            name
                                    + " ("
                                    + label
                                    + ") = "
                                    + withNul
                                    + " is not a path: "
                                    + invalidPathMessage(withNul)),
                    problems(name, withNul),
                    name + " with a NUL");
        }
        assertEquals(
                "Spectral library file", model().definition("spectral_library_name").displayName());
    }

    @Test
    @DisplayName("each path parameter: empty, or a readable file, passes")
    void emptyOrReadableFilePasses() throws IOException {
        Path readable = Files.writeString(fake.root().resolve("inputs/some.file"), "x\n");
        Path link = Files.createSymbolicLink(fake.root().resolve("inputs/link.file"), readable);
        for (String name : PATH_PARAMETERS) {
            assertEquals(List.of(), problems(name, ""), name + " empty");
            assertEquals(List.of(), problems(name, readable.toString()), name + " readable");
            assertEquals(List.of(), problems(name, link.toString()), name + " a link to a file");
        }
    }

    @Test
    @DisplayName("every unusable path parameter is reported, in the model's order, after the rest")
    void allReportedInModelOrder() throws IOException {
        CometParameters model = model();
        for (String name : PATH_PARAMETERS) {
            model = model.withText(name, "/no/" + name, ValueOrigin.USER);
        }
        List<String> reported = new ArrayList<>();
        for (String problem : problems(model)) {
            reported.add(problem.substring(0, problem.indexOf(' ')));
        }
        assertEquals(PATH_PARAMETERS, reported);
    }

    @Test
    @DisplayName("database_name keeps its own check and is not reported twice")
    void databaseNotReportedTwice() throws IOException {
        Path missing = fake.root().resolve("inputs/none.fasta");
        assertEquals(
                List.of(
                        "the database "
                                + missing
                                + " (database_name) does not exist or cannot be read"),
                problems("database_name", missing.toString()));
    }

    @Test
    @DisplayName("the set comes from each release's metadata: both bundled releases, same four")
    void theSetIsTheBundledMetadatas() {
        for (ToolVersion release : ReleaseDefaults.bundledReleases()) {
            CometParameters model =
                    ReleaseDefaults.load(MetadataLoader.loadBundled(), release)
                            .withText("database_name", "/no/db.fasta", ValueOrigin.USER);
            for (String name : PATH_PARAMETERS) {
                model = model.withText(name, "/no/" + name, ValueOrigin.USER);
            }
            List<String> problems = new ArrayList<>();
            PreRunChecks.checkPathParameters(model, problems);
            List<String> reported = new ArrayList<>();
            for (String problem : problems) {
                reported.add(problem.substring(0, problem.indexOf(' ')));
            }
            assertEquals(PATH_PARAMETERS, reported, release.text());
        }
    }

    @Test
    @DisplayName(
            "a parameter given the path validator in a test metadata is checked, and one stripped"
                    + " of it is not: the set is the metadata's, not a list in the workflow")
    void theSetFollowsTheMetadata() throws IOException {
        String json = bundledJson();
        json = setValidators(json, "pinfile_protein_delimiter", "\"validators\": \\[\\]", "path");
        json =
                setValidators(
                        json,
                        "spectral_library_name",
                        "\"validators\": \\[\\s*\"path\"\\s*\\]",
                        "");
        CuratedMetadata edited = MetadataLoader.load(json);
        CometParameters model =
                ReleaseDefaults.load(edited, ToolVersion.parse(RealComet.NEWER))
                        .withText("pinfile_protein_delimiter", "/no/delimiter", ValueOrigin.USER)
                        .withText("spectral_library_name", PLACEHOLDER, ValueOrigin.USER);
        List<String> problems = new ArrayList<>();
        PreRunChecks.checkPathParameters(model, problems);
        assertEquals(
                List.of(
                        "pinfile_protein_delimiter ("
                                + model.definition("pinfile_protein_delimiter").displayName()
                                + ") = /no/delimiter does not exist or cannot be read; clear it to"
                                + " search without one, or choose the file"),
                problems);

        // The same values under the bundled metadata: the opposite verdicts.
        CometParameters bundled =
                ReleaseDefaults.load(
                                MetadataLoader.loadBundled(), ToolVersion.parse(RealComet.NEWER))
                        .withText("pinfile_protein_delimiter", "/no/delimiter", ValueOrigin.USER)
                        .withText("spectral_library_name", PLACEHOLDER, ValueOrigin.USER);
        List<String> bundledProblems = new ArrayList<>();
        PreRunChecks.checkPathParameters(bundled, bundledProblems);
        assertEquals(
                List.of(
                        "spectral_library_name (Spectral library file) = /some/path/speclib.file"
                                + " does not exist or cannot be read; clear it to search without"
                                + " one, or choose the file"),
                bundledProblems);
    }

    private static String invalidPathMessage(String text) {
        try {
            Path.of(text);
        } catch (java.nio.file.InvalidPathException refused) {
            return refused.getMessage();
        }
        throw new AssertionError(text + " was accepted as a path");
    }

    private static String bundledJson() throws IOException {
        try (InputStream in = MetadataLoader.class.getResourceAsStream(MetadataLoader.RESOURCE)) {
            assertNotNull(in, MetadataLoader.RESOURCE);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Replaces the first validators list matching a pattern after a parameter's name. */
    private static String setValidators(String json, String name, String pattern, String id) {
        int at = json.indexOf("\"name\": \"" + name + "\"");
        assertTrue(at >= 0, name + " in the bundled metadata");
        Matcher matcher = Pattern.compile(pattern).matcher(json);
        assertTrue(matcher.find(at), name + "'s validators in the bundled metadata");
        String replacement =
                id.isEmpty() ? "\"validators\": []" : "\"validators\": [\"" + id + "\"]";
        return json.substring(0, matcher.start()) + replacement + json.substring(matcher.end());
    }
}
