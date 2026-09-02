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

package org.cometgui.install.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The completion marker: what it holds, what it refuses, and what it reads back as. */
class InstallationMarkerTest {

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static final FileHashes ARTEFACT =
            new FileHashes(
                    "9c86de1c45d2d93dae1ab43216b5864c",
                    "4d0e94af851884ff8ab6a2223e73cf28ba3ced28f6af863d4a76d541009b9dd1");

    private static final FileHashes BINARY =
            new FileHashes(
                    "0b77b68fd859639d7421f1c5e006ade5",
                    "1ba38acf09520cc89d5ed907ed0382c4d23876a7e20ec3e91cbbaa2ed431237c");

    private static InstallationMarker marker() {
        return new InstallationMarker(
                InstallationMarker.SCHEMA_VERSION,
                ToolName.PERCOLATOR,
                ToolVersion.parse("3.07.1"),
                LINUX,
                "rel-3-07-01",
                URI.create("https://example.invalid/percolator-noxml-ubuntu-portable.zip"),
                946303,
                ARTEFACT,
                "2026-09-02T11:22:33.444Z",
                "bin/percolator",
                2,
                List.of(ToolCapability.XML_OUTPUT, ToolCapability.XML_DECOY_OUTPUT),
                List.of(
                        new RecordedFile("bin/percolator", 2538632, BINARY),
                        new RecordedFile("share/xml/percolator_out.xsd", 10388, ARTEFACT)));
    }

    @Test
    @DisplayName("a marker survives a round trip through its own JSON, field for field")
    void aMarkerSurvivesARoundTrip() {
        InstallationMarker original = marker();

        InstallationMarker read = InstallationMarker.parse(original.toJson());

        assertEquals(original, read);
        assertEquals("3.07.1", read.version().text(), "including the spelling upstream used");
        assertEquals(
                List.of(ToolCapability.XML_OUTPUT, ToolCapability.XML_DECOY_OUTPUT),
                read.capabilities());
        assertEquals(
                "1ba38acf09520cc89d5ed907ed0382c4d23876a7e20ec3e91cbbaa2ed431237c",
                read.recordFor("bin/percolator").orElseThrow().hashes().sha256());
        assertTrue(read.recordFor("nothing/like/it").isEmpty());
    }

    @Test
    @DisplayName("the document is the one a scientist would read, and ends in a newline")
    void theDocumentIsReadable() {
        String json = marker().toJson();

        assertTrue(json.startsWith("{\n  \"schemaVersion\": 1,"), json);
        assertTrue(json.contains("\"tool\": \"percolator\""), json);
        assertTrue(json.contains("\"version\": \"3.07.1\""), json);
        assertTrue(json.contains("\"platform\": \"linux-x86-64\""), json);
        assertTrue(json.contains("\"executablePath\": \"bin/percolator\""), json);
        assertTrue(json.contains("\"payloadEntryCount\": 2"), json);
        assertTrue(json.endsWith("}\n"), "a POSIX text file ends in a newline");
    }

    @Test
    @DisplayName("a marker knows which directory it belongs in")
    void aMarkerKnowsWhichDirectoryItBelongsIn() {
        InstallationMarker marker = marker();

        assertTrue(marker.describes(ToolName.PERCOLATOR, ToolVersion.parse("3.07.1"), LINUX));
        assertTrue(
                marker.describes(ToolName.PERCOLATOR, ToolVersion.parse("3.7.1"), LINUX),
                "3.07.1 and 3.7.1 are one version, and one directory");
        assertFalse(marker.describes(ToolName.COMET, ToolVersion.parse("3.07.1"), LINUX));
        assertFalse(marker.describes(ToolName.PERCOLATOR, ToolVersion.parse("3.09"), LINUX));
        assertFalse(
                marker.describes(
                        ToolName.PERCOLATOR,
                        ToolVersion.parse("3.07.1"),
                        new HostPlatform(HostOperatingSystem.MACOS, HostArchitecture.X86_64)));
        assertEquals("percolator 3.07.1 linux-x86-64", marker.describe());
    }

    @Test
    @DisplayName("a marker that does not record the executable is refused")
    void aMarkerMustRecordTheExecutable() {
        IllegalArgumentException refused =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new InstallationMarker(
                                        1,
                                        ToolName.PERCOLATOR,
                                        ToolVersion.parse("3.07.1"),
                                        LINUX,
                                        "rel-t",
                                        URI.create("https://example.invalid/a.zip"),
                                        1,
                                        ARTEFACT,
                                        "2026-09-02T11:22:33.444Z",
                                        "bin/percolator",
                                        1,
                                        List.of(),
                                        List.of(new RecordedFile("share/other", 1, ARTEFACT))));

        assertTrue(
                refused.getMessage().contains("bin/percolator")
                        && refused.getMessage().contains("R-SEC-02")
                        && refused.getMessage().contains("share/other"),
                () ->
                        "the refusal names the executable it wanted and what was recorded instead: "
                                + refused.getMessage());
    }

    @ParameterizedTest
    @CsvSource({"0, schemaVersion must be positive", "-1, schemaVersion must be positive"})
    @DisplayName("a marker with no usable schema version is refused, naming the field")
    void aBadSchemaVersionIsRefused(int schemaVersion, String expected) {
        IllegalArgumentException refused =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new InstallationMarker(
                                        schemaVersion,
                                        ToolName.PERCOLATOR,
                                        ToolVersion.parse("3.07.1"),
                                        LINUX,
                                        "rel-t",
                                        URI.create("https://example.invalid/a.zip"),
                                        1,
                                        ARTEFACT,
                                        "2026-09-02T11:22:33.444Z",
                                        "bin/percolator",
                                        1,
                                        List.of(),
                                        List.of(new RecordedFile("bin/percolator", 1, ARTEFACT))));
        assertTrue(refused.getMessage().startsWith(expected), refused::getMessage);
    }

    @Test
    @DisplayName("a capability recorded twice, a file twice and a negative count are all refused")
    void duplicatesAndNegativeCountsAreRefused() {
        assertTrue(
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        withCapabilities(
                                                List.of(
                                                        ToolCapability.XML_OUTPUT,
                                                        ToolCapability.XML_OUTPUT)))
                        .getMessage()
                        .contains("XML_OUTPUT"),
                "the refusal names the capability");
        assertTrue(
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        withFiles(
                                                List.of(
                                                        new RecordedFile(
                                                                "bin/percolator", 1, ARTEFACT),
                                                        new RecordedFile(
                                                                "bin/percolator", 2, ARTEFACT))))
                        .getMessage()
                        .contains("bin/percolator"),
                "the refusal names the path");
        assertTrue(
                assertThrows(IllegalArgumentException.class, () -> withEntryCount(-1))
                        .getMessage()
                        .contains("payloadEntryCount"),
                "the refusal names the field");
    }

    @Test
    @DisplayName("a blank release tag, timestamp or executable path is refused, naming the field")
    void blankFieldsAreRefused() {
        assertTrue(
                assertThrows(IllegalArgumentException.class, () -> withReleaseTag("  "))
                        .getMessage()
                        .contains("releaseTag"));
        assertTrue(
                assertThrows(IllegalArgumentException.class, () -> withTimestamp(""))
                        .getMessage()
                        .contains("installedAtUtc"));
        assertTrue(
                assertThrows(IllegalArgumentException.class, () -> withExecutablePath(" "))
                        .getMessage()
                        .contains("executablePath"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "schemaVersion",
                "tool",
                "version",
                "platform",
                "releaseTag",
                "artefactUrl",
                "artefactSizeBytes",
                "artefactSha256",
                "artefactMd5",
                "installedAtUtc",
                "executablePath",
                "payloadEntryCount",
                "capabilities",
                "files"
            })
    @DisplayName("a marker missing any field is refused, and the message names that field")
    void everyMissingFieldIsRefusedByName(String field) {
        String document = withoutMember(marker().toJson(), field);

        MarkerFormatException refused =
                assertThrows(MarkerFormatException.class, () -> InstallationMarker.parse(document));

        assertTrue(
                refused.getMessage().contains("\"" + field + "\""),
                () -> "removing " + field + " produced: " + refused.getMessage());
    }

    @Test
    @DisplayName("a marker of another schema version is refused rather than guessed at")
    void anotherSchemaVersionIsRefused() {
        String document = marker().toJson().replace("\"schemaVersion\": 1", "\"schemaVersion\": 2");

        MarkerFormatException refused =
                assertThrows(MarkerFormatException.class, () -> InstallationMarker.parse(document));

        assertEquals(
                "this CometGUI reads completion marker schemaVersion 1 and the marker declares 2",
                refused.getMessage());
    }

    @Test
    @DisplayName("a field of the wrong JSON type is refused, naming the field and what was there")
    void aFieldOfTheWrongTypeIsRefused() {
        assertTrue(
                assertThrows(
                                MarkerFormatException.class,
                                () ->
                                        InstallationMarker.parse(
                                                marker().toJson()
                                                        .replace(
                                                                "\"tool\": \"percolator\"",
                                                                "\"tool\": 7")))
                        .getMessage()
                        .contains("JsonNumber"));
        assertTrue(
                assertThrows(
                                MarkerFormatException.class,
                                () ->
                                        InstallationMarker.parse(
                                                marker().toJson()
                                                        .replace(
                                                                "\"payloadEntryCount\": 2",
                                                                "\"payloadEntryCount\": \"2\"")))
                        .getMessage()
                        .contains("JsonString"));
        assertTrue(
                assertThrows(
                                MarkerFormatException.class,
                                () -> InstallationMarker.parse(withCapabilitiesNotAnArray()))
                        .getMessage()
                        .contains("\"capabilities\""));
    }

    @Test
    @DisplayName("a document that is not JSON, or not an object, is refused as a marker")
    void aDocumentThatIsNotAMarkerIsRefused() {
        assertTrue(
                assertThrows(
                                MarkerFormatException.class,
                                () -> InstallationMarker.parse("{not json"))
                        .getMessage()
                        .contains("not JSON"));
        assertTrue(
                assertThrows(MarkerFormatException.class, () -> InstallationMarker.parse("[1, 2]"))
                        .getMessage()
                        .contains("JsonArray"));
    }

    @Test
    @DisplayName("an unknown tool, capability or platform is refused rather than guessed at")
    void unknownIdentifiersAreRefused() {
        assertTrue(
                assertThrows(
                                MarkerFormatException.class,
                                () ->
                                        InstallationMarker.parse(
                                                marker().toJson()
                                                        .replace(
                                                                "\"percolator\"",
                                                                "\"Percolator\"")))
                        .getMessage()
                        .contains("Percolator"),
                "an exact match, no case folding");
        assertTrue(
                assertThrows(
                                MarkerFormatException.class,
                                () ->
                                        InstallationMarker.parse(
                                                marker().toJson()
                                                        .replace(
                                                                "\"XML_OUTPUT\"",
                                                                "\"teleportation\"")))
                        .getMessage()
                        .contains("teleportation"));
        assertTrue(
                assertThrows(
                                MarkerFormatException.class,
                                () ->
                                        InstallationMarker.parse(
                                                marker().toJson()
                                                        .replace(
                                                                "\"linux-x86-64\"",
                                                                "\"plan9-vax\"")))
                        .getMessage()
                        .contains("plan9"),
                "the refusal quotes the value it could not resolve");
    }

    @Test
    @DisplayName("a marker whose lists hold the wrong shape is refused")
    void listsOfTheWrongShapeAreRefused() {
        assertTrue(
                assertThrows(
                                MarkerFormatException.class,
                                () ->
                                        InstallationMarker.parse(
                                                marker().toJson()
                                                        .replace("\"XML_OUTPUT\"", "12345")))
                        .getMessage()
                        .contains("JsonNumber"));
        assertTrue(
                assertThrows(
                                MarkerFormatException.class,
                                () ->
                                        InstallationMarker.parse(
                                                marker().toJson()
                                                        .replace(
                                                                "\"files\": [\n    {",
                                                                "\"files\": [\n    \"x\",\n    {")))
                        .getMessage()
                        .contains("JsonString"));
    }

    @Test
    @DisplayName("the marker rejects nulls, naming the component")
    void nullsAreRejected() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new InstallationMarker(
                                1,
                                Nulls.of(ToolName.class),
                                ToolVersion.parse("3.07.1"),
                                LINUX,
                                "rel-t",
                                URI.create("https://example.invalid/a.zip"),
                                1,
                                ARTEFACT,
                                "2026-09-02T11:22:33.444Z",
                                "bin/percolator",
                                1,
                                List.of(),
                                List.of(new RecordedFile("bin/percolator", 1, ARTEFACT))));
        assertThrows(
                NullPointerException.class, () -> InstallationMarker.parse(Nulls.of(String.class)));
        assertThrows(
                NullPointerException.class,
                () ->
                        marker().describes(
                                        Nulls.of(ToolName.class), ToolVersion.parse("1.0"), LINUX));
        assertThrows(NullPointerException.class, () -> marker().recordFor(Nulls.of(String.class)));
    }

    private static String withCapabilitiesNotAnArray() {
        return marker().toJson()
                .replace(
                        "\"capabilities\": [\n    \"XML_OUTPUT\",\n    \"XML_DECOY_OUTPUT\"\n  ]",
                        "\"capabilities\": \"XML_OUTPUT\"");
    }

    private static String withoutMember(String document, String field) {
        String[] lines = document.split("\n", -1);
        StringBuilder kept = new StringBuilder(document.length());
        int depth = 0;
        boolean dropping = false;
        for (String line : lines) {
            if (dropping) {
                depth += count(line, '[') + count(line, '{') - count(line, ']') - count(line, '}');
                if (depth <= 0) {
                    dropping = false;
                }
                continue;
            }
            if (line.trim().startsWith("\"" + field + "\":")) {
                depth = count(line, '[') + count(line, '{') - count(line, ']') - count(line, '}');
                dropping = depth > 0;
                continue;
            }
            kept.append(line).append('\n');
        }
        return kept.toString().replace(",\n}", "\n}").replaceFirst(",\\s*\\n\\}", "\n}");
    }

    private static int count(String text, char character) {
        int total = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == character) {
                total++;
            }
        }
        return total;
    }

    private static InstallationMarker withCapabilities(List<ToolCapability> capabilities) {
        return replace(capabilities, null, null, null, null, 2);
    }

    private static InstallationMarker withFiles(List<RecordedFile> files) {
        return replace(null, files, null, null, null, 2);
    }

    private static InstallationMarker withEntryCount(int entryCount) {
        return replace(null, null, null, null, null, entryCount);
    }

    private static InstallationMarker withReleaseTag(String releaseTag) {
        return replace(null, null, releaseTag, null, null, 2);
    }

    private static InstallationMarker withTimestamp(String timestamp) {
        return replace(null, null, null, timestamp, null, 2);
    }

    private static InstallationMarker withExecutablePath(String executablePath) {
        return replace(null, null, null, null, executablePath, 2);
    }

    private static InstallationMarker replace(
            List<ToolCapability> capabilities,
            List<RecordedFile> files,
            String releaseTag,
            String timestamp,
            String executablePath,
            int entryCount) {
        InstallationMarker base = marker();
        return new InstallationMarker(
                base.schemaVersion(),
                base.tool(),
                base.version(),
                base.platform(),
                releaseTag == null ? base.releaseTag() : releaseTag,
                base.artefactUrl(),
                base.artefactSizeBytes(),
                base.artefactHashes(),
                timestamp == null ? base.installedAtUtc() : timestamp,
                executablePath == null ? base.executablePath() : executablePath,
                entryCount,
                capabilities == null ? base.capabilities() : capabilities,
                files == null ? base.files() : files);
    }
}
