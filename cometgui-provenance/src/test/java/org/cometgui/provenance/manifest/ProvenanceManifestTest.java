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

package org.cometgui.provenance.manifest;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ProvenanceManifest}.
 *
 * <p>The ordering group is the one that decides whether a provenance record is reproducible. The
 * settings go in through a {@link LinkedHashMap} whose insertion order is deliberately the reverse
 * of the sorted order, and the expected key sequence is typed out here. A manifest that merely
 * copied the map would satisfy every "are the settings there" assertion and would still produce a
 * different document on every JVM whose {@link HashMap} happened to hash the keys differently.
 *
 * <p>The {@code toString} group is where {@code R-SEC-03} meets a log line. The manifest is handed
 * a settings entry whose value is a plausible credential; the assertion is that the key appears and
 * the value does not, and the whole rendered line is pinned as a literal so that a component added
 * later cannot start printing values without failing here.
 */
class ProvenanceManifestTest {

    private static final String API_KEY = "glpat-Z1x9QeR7sVbN3mK0pLtY";

    private static Map<String, String> scrambledSettings() {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("upload.api.key", API_KEY);
        settings.put("percolator.seed", "1387");
        settings.put("comet.enzyme", "trypsin");
        return settings;
    }

    private static List<ToolRecord> twoTools() {
        return List.of(
                ManifestFixtures.tool("comet", "OMP_NUM_THREADS", "8"),
                ManifestFixtures.tool("percolator", "OMP_NUM_THREADS", "8"));
    }

    private static List<FileRecord> twoFiles() {
        return List.of(
                ManifestFixtures.inputFile("spectra", "spectra.mzML"),
                ManifestFixtures.inputFile("fasta", "human.fasta"));
    }

    private static ProvenanceManifest manifest() {
        return ProvenanceManifest.current(
                ManifestFixtures.completedRun(),
                ManifestFixtures.application(),
                scrambledSettings(),
                twoTools(),
                twoFiles());
    }

    @Nested
    @DisplayName("the schema version")
    class Version {

        @Test
        @DisplayName("the factory stamps version 1")
        void theFactoryStampsVersionOne() {
            assertEquals(1, manifest().schemaVersion());
        }

        @Test
        @DisplayName("an older document's version can still be represented")
        void anOlderVersionCanBeRepresented() {
            ProvenanceManifest reopened =
                    new ProvenanceManifest(
                            1,
                            ManifestFixtures.completedRun(),
                            ManifestFixtures.application(),
                            Map.of(),
                            List.of(),
                            List.of());

            assertEquals(1, reopened.schemaVersion());
        }

        @Test
        @DisplayName("version zero and below are rejected, printing the value")
        void versionZeroAndBelowAreRejected() {
            assertAll(
                    () ->
                            assertEquals(
                                    "schemaVersion must be at least 1, but was: 0",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () -> withSchemaVersion(0))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "schemaVersion must be at least 1, but was: -3",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () -> withSchemaVersion(-3))
                                            .getMessage()));
        }

        private ProvenanceManifest withSchemaVersion(int schemaVersion) {
            return new ProvenanceManifest(
                    schemaVersion,
                    ManifestFixtures.completedRun(),
                    ManifestFixtures.application(),
                    Map.of(),
                    List.of(),
                    List.of());
        }
    }

    @Nested
    @DisplayName("what it holds")
    class Contents {

        @Test
        @DisplayName("the run, the application, the settings, the tools and the files")
        void everythingComesBack() {
            ProvenanceManifest manifest = manifest();

            assertAll(
                    () -> assertEquals("run-20260831-091500", manifest.run().runId().value()),
                    () -> assertEquals("project-alpha", manifest.run().projectId()),
                    () -> assertEquals("Frobnitz OS", manifest.application().osName()),
                    () -> assertEquals("1387", manifest.settings().get("percolator.seed")),
                    () -> assertEquals(3, manifest.settings().size()),
                    () -> assertEquals(2, manifest.tools().size()),
                    () -> assertEquals("comet", manifest.tools().get(0).name()),
                    () -> assertEquals("percolator", manifest.tools().get(1).name()),
                    () -> assertEquals(2, manifest.files().size()),
                    () -> assertEquals("spectra", manifest.files().get(0).role()));
        }

        @Test
        @DisplayName("AC-PRV-10: the Percolator seed is read back through the pinned key")
        void theSeedIsReadBackThroughThePinnedKey() {
            ProvenanceManifest manifest = manifest();

            assertAll(
                    () ->
                            assertEquals(
                                    "1387",
                                    manifest.settings()
                                            .get(ProvenanceSchema.PERCOLATOR_SEED_SETTING)),
                    () -> assertEquals("1387", manifest.settings().get("percolator.seed")),
                    () -> assertEquals("tr", manifest.application().locale().getLanguage()));
        }

        @Test
        @DisplayName("an empty manifest is legal: a run that has recorded nothing yet")
        void anEmptyManifestIsLegal() {
            ProvenanceManifest empty =
                    ProvenanceManifest.current(
                            ManifestFixtures.completedRun(),
                            ManifestFixtures.application(),
                            Map.of(),
                            List.of(),
                            List.of());

            assertAll(
                    () -> assertTrue(empty.settings().isEmpty()),
                    () -> assertTrue(empty.tools().isEmpty()),
                    () -> assertTrue(empty.files().isEmpty()));
        }
    }

    @Nested
    @DisplayName("deterministic ordering")
    class Ordering {

        @Test
        @DisplayName("the settings iterate in ascending key order, whatever order they arrived in")
        void settingsIterateInAscendingKeyOrder() {
            ProvenanceManifest manifest = manifest();

            assertAll(
                    () ->
                            assertEquals(
                                    List.of("comet.enzyme", "percolator.seed", "upload.api.key"),
                                    List.copyOf(manifest.settings().keySet())),
                    () ->
                            assertEquals(
                                    List.of("trypsin", "1387", API_KEY),
                                    List.copyOf(manifest.settings().values())),
                    () ->
                            assertEquals(
                                    List.of("upload.api.key", "percolator.seed", "comet.enzyme"),
                                    List.copyOf(scrambledSettings().keySet())));
        }

        @Test
        @DisplayName("tools and files keep the order they were recorded in")
        void toolsAndFilesKeepTheirOrder() {
            ProvenanceManifest manifest = manifest();

            assertAll(
                    () ->
                            assertEquals(
                                    List.of("comet", "percolator"),
                                    manifest.tools().stream().map(ToolRecord::name).toList()),
                    () ->
                            assertEquals(
                                    List.of("spectra", "fasta"),
                                    manifest.files().stream().map(FileRecord::role).toList()));
        }
    }

    @Nested
    @DisplayName("defensive copying")
    class Copying {

        @Test
        @DisplayName("mutating the caller's settings map afterwards does not change the manifest")
        void theSettingsMapIsCopiedIn() {
            Map<String, String> callers = scrambledSettings();
            ProvenanceManifest manifest =
                    ProvenanceManifest.current(
                            ManifestFixtures.completedRun(),
                            ManifestFixtures.application(),
                            callers,
                            List.of(),
                            List.of());

            callers.put("percolator.seed", "9999");
            callers.put("forged.setting", "forged");
            callers.remove("comet.enzyme");

            assertAll(
                    () -> assertEquals("1387", manifest.settings().get("percolator.seed")),
                    () -> assertEquals("trypsin", manifest.settings().get("comet.enzyme")),
                    () -> assertFalse(manifest.settings().containsKey("forged.setting")),
                    () -> assertEquals(3, manifest.settings().size()));
        }

        @Test
        @DisplayName("mutating the caller's tool and file lists does not change the manifest")
        void theListsAreCopiedIn() {
            List<ToolRecord> callersTools = new ArrayList<>(twoTools());
            List<FileRecord> callersFiles = new ArrayList<>(twoFiles());
            ProvenanceManifest manifest =
                    ProvenanceManifest.current(
                            ManifestFixtures.completedRun(),
                            ManifestFixtures.application(),
                            Map.of(),
                            callersTools,
                            callersFiles);

            callersTools.clear();
            callersFiles.remove(0);

            assertAll(
                    () -> assertEquals(2, manifest.tools().size()),
                    () -> assertEquals(2, manifest.files().size()),
                    () -> assertEquals("comet", manifest.tools().get(0).name()),
                    () -> assertEquals("spectra", manifest.files().get(0).role()));
        }

        @Test
        @DisplayName("the collections handed out refuse to be modified, and the manifest holds")
        void theCollectionsHandedOutRefuseToBeModified() {
            ProvenanceManifest manifest = manifest();
            Map<String, String> handedOutSettings = manifest.settings();
            List<ToolRecord> handedOutTools = manifest.tools();
            List<FileRecord> handedOutFiles = manifest.files();

            assertAll(
                    () ->
                            assertThrows(
                                    UnsupportedOperationException.class,
                                    () -> handedOutSettings.put("forged.setting", "forged")),
                    () ->
                            assertThrows(
                                    UnsupportedOperationException.class,
                                    () -> handedOutSettings.remove("percolator.seed")),
                    () ->
                            assertThrows(
                                    UnsupportedOperationException.class,
                                    () -> handedOutTools.clear()),
                    () ->
                            assertThrows(
                                    UnsupportedOperationException.class,
                                    () -> handedOutFiles.clear()),
                    () -> assertEquals(3, manifest.settings().size()),
                    () -> assertEquals(2, manifest.tools().size()),
                    () -> assertEquals(2, manifest.files().size()));
        }
    }

    @Nested
    @DisplayName("what it refuses")
    class Rejections {

        @Test
        @DisplayName("a blank settings key is rejected, printing the value")
        void aBlankSettingsKeyIsRejected() {
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> withSettings(new LinkedHashMap<>(Map.of("  ", "value"))));

            assertEquals("a settings key must not be blank, but was: \"  \"", thrown.getMessage());
        }

        @Test
        @DisplayName("a null settings key is rejected, naming the field")
        void aNullSettingsKeyIsRejected() {
            Map<String, String> withNullKey = new HashMap<>();
            withNullKey.put(null, "value");

            NullPointerException thrown =
                    assertThrows(NullPointerException.class, () -> withSettings(withNullKey));

            assertEquals("a settings key", thrown.getMessage());
        }

        @Test
        @DisplayName("a null settings value is rejected, naming the key it belongs to")
        void aNullSettingsValueIsRejected() {
            Map<String, String> withNullValue = new HashMap<>();
            withNullValue.put("percolator.seed", null);

            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class, () -> withSettings(withNullValue));

            assertEquals(
                    "the setting \"percolator.seed\" must not have a null value",
                    thrown.getMessage());
        }

        @Test
        @DisplayName("a null tool or file is rejected by index, not anonymously")
        void aNullElementIsRejectedByIndex() {
            List<ToolRecord> toolsWithNull =
                    Arrays.asList(ManifestFixtures.tool("comet", "A", "1"), null);
            List<FileRecord> filesWithNull =
                    Arrays.asList(null, ManifestFixtures.inputFile("fasta", "human.fasta"));

            assertAll(
                    () ->
                            assertEquals(
                                    "tools[1] must not be null",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () ->
                                                            ProvenanceManifest.current(
                                                                    ManifestFixtures.completedRun(),
                                                                    ManifestFixtures.application(),
                                                                    Map.of(),
                                                                    toolsWithNull,
                                                                    List.of()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "files[0] must not be null",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () ->
                                                            ProvenanceManifest.current(
                                                                    ManifestFixtures.completedRun(),
                                                                    ManifestFixtures.application(),
                                                                    Map.of(),
                                                                    List.of(),
                                                                    filesWithNull))
                                            .getMessage()));
        }

        @Test
        @DisplayName("every reference component is required, and the message names it")
        void everyReferenceComponentIsRequired() {
            assertAll(
                    () ->
                            assertEquals(
                                    "run",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            ProvenanceManifest.current(
                                                                    null,
                                                                    ManifestFixtures.application(),
                                                                    Map.of(),
                                                                    List.of(),
                                                                    List.of()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "application",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            ProvenanceManifest.current(
                                                                    ManifestFixtures.completedRun(),
                                                                    null,
                                                                    Map.of(),
                                                                    List.of(),
                                                                    List.of()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "settings",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () -> withSettings(null))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "tools",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            ProvenanceManifest.current(
                                                                    ManifestFixtures.completedRun(),
                                                                    ManifestFixtures.application(),
                                                                    Map.of(),
                                                                    null,
                                                                    List.of()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "files",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            ProvenanceManifest.current(
                                                                    ManifestFixtures.completedRun(),
                                                                    ManifestFixtures.application(),
                                                                    Map.of(),
                                                                    List.of(),
                                                                    null))
                                            .getMessage()));
        }

        private ProvenanceManifest withSettings(Map<String, String> settings) {
            return ProvenanceManifest.current(
                    ManifestFixtures.completedRun(),
                    ManifestFixtures.application(),
                    settings,
                    List.of(),
                    List.of());
        }
    }

    @Nested
    @DisplayName("what it says about itself")
    class Rendering {

        @Test
        @DisplayName("the rendered line is exactly this, and no settings value is in it")
        void theRenderedLineIsExactlyThis() {
            assertEquals(
                    "ProvenanceManifest[schemaVersion=1, runId=run-20260831-091500,"
                            + " status=completed, settingsKeys=[comet.enzyme, percolator.seed,"
                            + " upload.api.key], tools=[comet, percolator], fileCount=2]",
                    manifest().toString());
        }

        @Test
        @DisplayName("the settings keys are printed and the settings values are not")
        void theKeysArePrintedAndTheValuesAreNot() {
            String rendered = manifest().toString();

            assertAll(
                    () -> assertTrue(rendered.contains("upload.api.key")),
                    () -> assertFalse(rendered.contains(API_KEY)),
                    () -> assertFalse(rendered.contains("glpat-")),
                    () -> assertFalse(rendered.contains("trypsin")),
                    () -> assertFalse(rendered.contains("1387")));
        }
    }
}
