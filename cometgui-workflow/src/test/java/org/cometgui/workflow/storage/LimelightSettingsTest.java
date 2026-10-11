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

package org.cometgui.workflow.storage;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.cometgui.domain.project.UnsupportedSchemaVersionException;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.tools.limelight.LimelightQValueCutoff;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** A run's Limelight settings: the format typed out, the store's two files kept apart. */
class LimelightSettingsTest {

    @TempDir private Path root;

    private static final String DEFAULT_DOCUMENT =
            "{\n"
                    + "  \"schemaVersion\": 1,\n"
                    + "  \"limelightQValueCutoff\": \"0.01\",\n"
                    + "  \"importDecoys\": false,\n"
                    + "  \"independentDecoyPrefix\": null,\n"
                    + "  \"openMod\": false\n"
                    + "}\n";

    private RunLayout run() {
        return new RunLayout(root.resolve("runs/20261011T080000Z-run-0001").toAbsolutePath());
    }

    @Test
    @DisplayName("the defaults are q 0.01 and every option off")
    void defaults() {
        assertAll(
                () ->
                        assertEquals(
                                LimelightQValueCutoff.DEFAULT,
                                LimelightSettings.DEFAULTS.qCutoff()),
                () -> assertEquals(0.01, LimelightSettings.DEFAULTS.qCutoff().value()),
                () -> assertFalse(LimelightSettings.DEFAULTS.importDecoys()),
                () ->
                        assertEquals(
                                Optional.empty(),
                                LimelightSettings.DEFAULTS.independentDecoyPrefix()),
                () -> assertFalse(LimelightSettings.DEFAULTS.openMod()));
    }

    @Test
    @DisplayName("the document is pinned, and every setting round-trips")
    void theDocument() {
        LimelightSettings changed =
                LimelightSettings.DEFAULTS
                        .withQCutoff(LimelightSettings.parseCutoff("0.05"))
                        .withImportDecoys(true)
                        .withIndependentDecoyPrefix(Optional.of("DECOY_"))
                        .withOpenMod(true);
        String rendered = LimelightSettingsJson.render(changed);
        assertAll(
                () ->
                        assertEquals(
                                DEFAULT_DOCUMENT,
                                LimelightSettingsJson.render(LimelightSettings.DEFAULTS)),
                () ->
                        assertEquals(
                                "{\n"
                                        + "  \"schemaVersion\": 1,\n"
                                        + "  \"limelightQValueCutoff\": \"0.05\",\n"
                                        + "  \"importDecoys\": true,\n"
                                        + "  \"independentDecoyPrefix\": \"DECOY_\",\n"
                                        + "  \"openMod\": true\n"
                                        + "}\n",
                                rendered),
                () -> assertEquals(changed, LimelightSettingsJson.parse(rendered, "x")),
                () ->
                        assertEquals(
                                LimelightSettings.DEFAULTS,
                                LimelightSettingsJson.parse(DEFAULT_DOCUMENT, "x")));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"0", "1", "0.05", "0.01", "1.0", "0.000001", "00.5"})
    @DisplayName("a cutoff from 0 to 1, digits and a '.', is accepted")
    void acceptedCutoffs(String text) {
        assertEquals(
                new java.math.BigDecimal(text).doubleValue(),
                LimelightSettings.parseCutoff(text).value());
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(
            strings = {
                "1.5", "-0.1", "abc", "0,01", "1e-2", ".5", "0.", "", " 0.01", "NaN", "0x1p-3"
            })
    @DisplayName("anything else is refused, naming the rule and not the text")
    void refusedCutoffs(String text) {
        IllegalArgumentException refused =
                assertThrows(
                        IllegalArgumentException.class, () -> LimelightSettings.parseCutoff(text));
        assertTrue(
                refused.getMessage()
                        .startsWith(
                                "the Limelight q-value cutoff must be a decimal number from 0"
                                        + " to 1"));
    }

    @Test
    @DisplayName(
            "a document with another version, an unknown or missing member, a bad cutoff or a"
                    + " non-boolean flag is refused")
    void refusals() {
        assertAll(
                () ->
                        assertThrows(
                                UnsupportedSchemaVersionException.class,
                                () ->
                                        LimelightSettingsJson.parse(
                                                DEFAULT_DOCUMENT.replace(
                                                        "\"schemaVersion\": 1",
                                                        "\"schemaVersion\": 2"),
                                                "x")),
                () ->
                        assertThrows(
                                InvalidDocumentException.class,
                                () ->
                                        LimelightSettingsJson.parse(
                                                DEFAULT_DOCUMENT.replace(
                                                        "\"openMod\": false",
                                                        "\"openMod\": false,"
                                                                + " \"psmQValueFilter\": \"0.01\""),
                                                "x")),
                () ->
                        assertThrows(
                                InvalidDocumentException.class,
                                () ->
                                        LimelightSettingsJson.parse(
                                                DEFAULT_DOCUMENT.replace(
                                                        ",\n  \"openMod\": false", ""),
                                                "x")),
                () ->
                        assertTrue(
                                assertThrows(
                                                InvalidDocumentException.class,
                                                () ->
                                                        LimelightSettingsJson.parse(
                                                                DEFAULT_DOCUMENT.replace(
                                                                        "\"0.01\"", "\"2\""),
                                                                "x"))
                                        .getMessage()
                                        .contains("limelightQValueCutoff")),
                () ->
                        assertTrue(
                                assertThrows(
                                                InvalidDocumentException.class,
                                                () ->
                                                        LimelightSettingsJson.parse(
                                                                DEFAULT_DOCUMENT.replace(
                                                                        "\"importDecoys\": false",
                                                                        "\"importDecoys\":"
                                                                                + " \"false\""),
                                                                "x"))
                                        .getMessage()
                                        .contains("must be true or false")),
                () ->
                        assertThrows(
                                InvalidDocumentException.class,
                                () ->
                                        LimelightSettingsJson.parse(
                                                DEFAULT_DOCUMENT.replace(
                                                        "\"independentDecoyPrefix\": null",
                                                        "\"independentDecoyPrefix\": 3"),
                                                "x")));
    }

    @Test
    @DisplayName(
            "nothing saved reads as the defaults; a save writes results/limelight-settings.json and"
                    + " reads back")
    void theStore() throws IOException {
        RunLayout run = run();
        assertEquals(LimelightSettings.DEFAULTS, LimelightSettingsStore.read(run));
        LimelightSettings saved =
                LimelightSettings.DEFAULTS.withQCutoff(LimelightSettings.parseCutoff("0.2"));

        LimelightSettingsStore.write(run, saved);

        assertAll(
                () -> assertEquals(saved, LimelightSettingsStore.read(run)),
                () ->
                        assertEquals(
                                DEFAULT_DOCUMENT.replace("\"0.01\"", "\"0.2\""),
                                Files.readString(
                                        run.root().resolve("results/limelight-settings.json"),
                                        StandardCharsets.UTF_8)),
                () ->
                        assertFalse(
                                Files.exists(run.viewStateFile()),
                                "the view state is not touched"));
    }

    @Test
    @DisplayName("a file this build cannot read is refused on read and never replaced on write")
    void anUnreadableFileIsKept() throws IOException {
        RunLayout run = run();
        Files.createDirectories(run.resultsDirectory());
        String newer = DEFAULT_DOCUMENT.replace("\"schemaVersion\": 1", "\"schemaVersion\": 9");
        Files.writeString(run.limelightSettingsFile(), newer);

        assertThrows(
                UnsupportedSchemaVersionException.class, () -> LimelightSettingsStore.read(run));
        assertThrows(
                UnsupportedSchemaVersionException.class,
                () -> LimelightSettingsStore.write(run, LimelightSettings.DEFAULTS));
        assertEquals(newer, Files.readString(run.limelightSettingsFile()));
    }
}
