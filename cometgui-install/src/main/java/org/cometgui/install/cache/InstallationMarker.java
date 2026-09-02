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

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.provenance.json.JsonParseException;
import org.cometgui.provenance.json.JsonReader;
import org.cometgui.provenance.json.JsonValue;
import org.cometgui.provenance.json.JsonWriter;

/**
 * The completion marker: the file whose presence, and whose recorded checksums, are what {@code
 * R-TOOL-04} means by <em>installed</em>.
 *
 * <p>It is written last, after the payload has been moved into the tool cache, and it carries the
 * length and both digests of every file the manifest names -- so {@link ToolCache#verify} can
 * answer both halves of the rule from the directory alone. A marker that is merely present proves
 * the install finished; the digests prove the entry is still the one that finished.
 *
 * <h2>Which files carry a digest, and why not all of them</h2>
 *
 * <p>The marker records the paths the <em>manifest</em> names: the executable or JAR, and every
 * companion member. Those are the files whose digests the manifest pins, so comparing them at
 * install time is a check that can fail against a value this code did not compute. PDV unpacks 222
 * entries and the manifest pins a digest for one of them; hashing 115 MB on every Tool Manager
 * refresh to compare against numbers taken from the same install would cost the user seconds and
 * would only ever confirm itself. {@link #payloadEntryCount()} covers the rest cheaply: the number
 * of files the extraction placed, compared against what is in the directory now.
 *
 * <h2>What the capability list is bound to</h2>
 *
 * <p>{@code R-TOOL-07} requires probed capabilities to be re-confirmed when the recorded executable
 * checksum changes. That is what makes recording them here safe rather than a claim nobody checks:
 * the executable's SHA-256 is recorded beside them and is verified on every read, so a changed
 * executable makes the whole entry {@link InstallationState#CHECKSUM_MISMATCH} and the capabilities
 * go with it. The re-probe itself belongs to the units that own probing.
 *
 * @param schemaVersion the marker format, so a future CometGUI can refuse one it cannot read
 * @param tool which tool this directory holds
 * @param version which release of it
 * @param platform the operating system and architecture the artefact was built for
 * @param releaseTag the upstream release tag it came from
 * @param artefactUrl the pinned URL it was downloaded from ({@code D-008}: nothing is
 *     redistributed, so the URL is the provenance)
 * @param artefactSizeBytes the download's length
 * @param artefactHashes the download's MD5 and SHA-256
 * @param installedAtUtc when the install finished, in the project's canonical UTC form
 * @param executablePath the executable or JAR, relative to this directory
 * @param payloadEntryCount how many files the extraction placed in this directory, excluding the
 *     marker itself
 * @param capabilities what the probe confirmed the installed build can do, in enum order
 * @param files every file the manifest names, with its length and digests
 */
public record InstallationMarker(
        int schemaVersion,
        ToolName tool,
        ToolVersion version,
        HostPlatform platform,
        String releaseTag,
        URI artefactUrl,
        long artefactSizeBytes,
        FileHashes artefactHashes,
        String installedAtUtc,
        String executablePath,
        int payloadEntryCount,
        List<ToolCapability> capabilities,
        List<RecordedFile> files) {

    /** The marker format this version of CometGUI writes and reads. */
    public static final int SCHEMA_VERSION = 1;

    /**
     * The marker's file name inside a tool directory.
     *
     * <p>A dot-prefixed name so that a scientist listing the directory sees the tool's own files
     * first, and a {@code .json} suffix because it is one -- the same reader the artefact manifest
     * uses parses it.
     */
    public static final String FILE_NAME = ".cometgui-install.json";

    /**
     * Validates the marker and takes defensive, immutable copies of its two lists.
     *
     * @throws NullPointerException if any component is {@code null}
     * @throws IllegalArgumentException if the schema version, the artefact size or the entry count
     *     is not positive, the release tag, timestamp or executable path is blank, a capability
     *     appears twice, a file path appears twice, or the executable path is not among the
     *     recorded files -- naming the field
     */
    public InstallationMarker {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException(
                    "schemaVersion must be positive, but was: " + schemaVersion);
        }
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(platform, "platform");
        releaseTag = required(releaseTag, "releaseTag");
        Objects.requireNonNull(artefactUrl, "artefactUrl");
        if (artefactSizeBytes <= 0) {
            throw new IllegalArgumentException(
                    "artefactSizeBytes must be positive, but was: " + artefactSizeBytes);
        }
        Objects.requireNonNull(artefactHashes, "artefactHashes");
        installedAtUtc = required(installedAtUtc, "installedAtUtc");
        executablePath = required(executablePath, "executablePath");
        // Positive, not merely non-negative: an install always places at least the executable, so
        // a marker recording nothing describes a directory that cannot be the install it claims.
        if (payloadEntryCount <= 0) {
            throw new IllegalArgumentException(
                    "payloadEntryCount must be positive, but was: " + payloadEntryCount);
        }
        capabilities = checkedCapabilities(capabilities);
        files = checkedFiles(files, executablePath);
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank, but was: \"" + value + "\"");
        }
        return value;
    }

    private static List<ToolCapability> checkedCapabilities(List<ToolCapability> capabilities) {
        List<ToolCapability> copy =
                List.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
        Set<ToolCapability> seen = EnumSet.noneOf(ToolCapability.class);
        for (ToolCapability capability : copy) {
            if (!seen.add(capability)) {
                throw new IllegalArgumentException(
                        "capabilities names " + capability.id() + " more than once");
            }
        }
        return copy;
    }

    /*
     * THE EXECUTABLE MUST BE ONE OF THE RECORDED FILES.  R-SEC-02 and R-TOOL-07 both hang off the
     * executable's checksum -- it is the file that gets launched and the one whose change forces a
     * re-probe -- so a marker that recorded every companion and not the binary would satisfy every
     * other check here while leaving the only file that matters unverified.
     */
    private static List<RecordedFile> checkedFiles(
            List<RecordedFile> files, String executablePath) {
        List<RecordedFile> copy = List.copyOf(Objects.requireNonNull(files, "files"));
        Set<String> paths = new LinkedHashSet<>();
        for (RecordedFile file : copy) {
            if (!paths.add(file.path())) {
                throw new IllegalArgumentException(
                        "files records \"" + file.path() + "\" more than once");
            }
        }
        if (!paths.contains(executablePath)) {
            throw new IllegalArgumentException(
                    "files must record the executable \""
                            + executablePath
                            + "\", because its checksum is what R-SEC-02 and R-TOOL-07 both rest"
                            + " on, but records "
                            + paths);
        }
        return copy;
    }

    /**
     * What the probe confirmed, immutable and in enum order.
     *
     * @return the capabilities, possibly empty
     */
    @Override
    public List<ToolCapability> capabilities() {
        return List.copyOf(capabilities);
    }

    /**
     * Every file the marker records, immutable and in the order they were installed.
     *
     * @return the recorded files, never empty
     */
    @Override
    public List<RecordedFile> files() {
        return List.copyOf(files);
    }

    /**
     * Looks up what was recorded for one path.
     *
     * @param path the path relative to the tool's install directory
     * @return the record, or empty when the marker does not name that file
     */
    public Optional<RecordedFile> recordFor(String path) {
        Objects.requireNonNull(path, "path");
        return files.stream().filter(file -> file.path().equals(path)).findFirst();
    }

    /**
     * Whether this marker describes the tool, version and platform of a given directory.
     *
     * @param expectedTool the tool the directory's path says it holds
     * @param expectedVersion the version the directory's path says it holds
     * @param expectedPlatform the platform the directory's path says it holds
     * @return {@code true} when all three agree
     * @throws NullPointerException if any argument is {@code null}
     */
    public boolean describes(
            ToolName expectedTool, ToolVersion expectedVersion, HostPlatform expectedPlatform) {
        Objects.requireNonNull(expectedTool, "expectedTool");
        Objects.requireNonNull(expectedVersion, "expectedVersion");
        Objects.requireNonNull(expectedPlatform, "expectedPlatform");
        return tool == expectedTool
                && version.equals(expectedVersion)
                && platform.equals(expectedPlatform);
    }

    /**
     * How this marker is named in a diagnostic: the tool, the version as upstream writes it, and
     * the platform.
     *
     * @return for example {@code percolator 3.07.1 linux-x86-64}
     */
    public String describe() {
        return tool.id() + " " + version.text() + " " + platform.id();
    }

    /**
     * Renders the marker as the JSON document that is written to disk.
     *
     * <p>Through the project's one {@link JsonWriter}, with the secret rule set applied, because a
     * URL is a string a credential can hide in and this file is one a scientist may paste into a
     * bug report.
     *
     * @return the document, ending in a newline
     */
    public String toJson() {
        JsonWriter writer = JsonWriter.redactingWith(SecretRedactor.patternsOnly());
        writer.beginObject()
                .name("schemaVersion")
                .value(schemaVersion)
                .name("tool")
                .value(tool.id())
                .name("version")
                .value(version.text())
                .name("platform")
                .value(platform.id())
                .name("releaseTag")
                .value(releaseTag)
                .name("artefactUrl")
                .value(artefactUrl.toString())
                .name("artefactSizeBytes")
                .value(artefactSizeBytes)
                .name("artefactSha256")
                .value(artefactHashes.sha256())
                .name("artefactMd5")
                .value(artefactHashes.md5())
                .name("installedAtUtc")
                .value(installedAtUtc)
                .name("executablePath")
                .value(executablePath)
                .name("payloadEntryCount")
                .value(payloadEntryCount)
                .name("capabilities")
                .beginArray();
        for (ToolCapability capability : capabilities) {
            writer.value(capability.id());
        }
        writer.endArray().name("files").beginArray();
        for (RecordedFile file : files) {
            writer.beginObject()
                    .name("path")
                    .value(file.path())
                    .name("sizeBytes")
                    .value(file.sizeBytes())
                    .name("sha256")
                    .value(file.hashes().sha256())
                    .name("md5")
                    .value(file.hashes().md5())
                    .endObject();
        }
        return writer.endArray().endObject().finish();
    }

    /**
     * Reads a marker back.
     *
     * <p>Strict, like the artefact manifest's reader and for the same reason: a marker is this
     * application's own file, and a reader that guessed at a missing or misspelled field would let
     * a half-written marker report a tool as installed.
     *
     * @param document the marker's text
     * @return the marker
     * @throws MarkerFormatException if the document is not a marker this CometGUI can read, with a
     *     message naming the field
     * @throws NullPointerException if {@code document} is {@code null}
     */
    public static InstallationMarker parse(String document) {
        Objects.requireNonNull(document, "document");
        JsonValue root;
        try {
            root = JsonReader.parse(document);
        } catch (JsonParseException notJson) {
            throw new MarkerFormatException("the completion marker is not JSON", notJson);
        }
        if (!(root instanceof JsonValue.JsonObject object)) {
            throw new MarkerFormatException(
                    "a completion marker is a JSON object, and this document's root is a "
                            + root.getClass().getSimpleName());
        }
        long declared = number(object, "schemaVersion");
        if (declared != SCHEMA_VERSION) {
            throw new MarkerFormatException(
                    "this CometGUI reads completion marker schemaVersion "
                            + SCHEMA_VERSION
                            + " and the marker declares "
                            + declared);
        }
        try {
            return new InstallationMarker(
                    (int) declared,
                    ToolName.fromId(text(object, "tool")),
                    ToolVersion.parse(text(object, "version")),
                    HostPlatform.fromId(text(object, "platform")),
                    text(object, "releaseTag"),
                    URI.create(text(object, "artefactUrl")),
                    number(object, "artefactSizeBytes"),
                    new FileHashes(text(object, "artefactMd5"), text(object, "artefactSha256")),
                    text(object, "installedAtUtc"),
                    text(object, "executablePath"),
                    (int) number(object, "payloadEntryCount"),
                    capabilitiesOf(object),
                    filesOf(object));
        } catch (IllegalArgumentException rejected) {
            throw new MarkerFormatException(
                    "the completion marker is not a valid one: " + rejected.getMessage(), rejected);
        }
    }

    private static List<ToolCapability> capabilitiesOf(JsonValue.JsonObject object) {
        List<ToolCapability> capabilities = new ArrayList<>();
        for (JsonValue element : array(object, "capabilities")) {
            if (!(element instanceof JsonValue.JsonString name)) {
                throw new MarkerFormatException(
                        "every element of the completion marker's \"capabilities\" is a string, and"
                                + " one is a "
                                + element.getClass().getSimpleName());
            }
            capabilities.add(ToolCapability.fromId(name.value()));
        }
        return capabilities;
    }

    private static List<RecordedFile> filesOf(JsonValue.JsonObject object) {
        List<RecordedFile> files = new ArrayList<>();
        for (JsonValue element : array(object, "files")) {
            if (!(element instanceof JsonValue.JsonObject file)) {
                throw new MarkerFormatException(
                        "every element of the completion marker's \"files\" is an object, and one"
                                + " is a "
                                + element.getClass().getSimpleName());
            }
            files.add(
                    new RecordedFile(
                            text(file, "path"),
                            number(file, "sizeBytes"),
                            new FileHashes(text(file, "md5"), text(file, "sha256"))));
        }
        return files;
    }

    private static String text(JsonValue.JsonObject object, String name) {
        JsonValue value = member(object, name);
        if (!(value instanceof JsonValue.JsonString string)) {
            throw new MarkerFormatException(
                    "the completion marker's \""
                            + name
                            + "\" is a string, and this one is a "
                            + value.getClass().getSimpleName());
        }
        return string.value();
    }

    private static long number(JsonValue.JsonObject object, String name) {
        JsonValue value = member(object, name);
        if (!(value instanceof JsonValue.JsonNumber numeric)) {
            throw new MarkerFormatException(
                    "the completion marker's \""
                            + name
                            + "\" is a number, and this one is a "
                            + value.getClass().getSimpleName());
        }
        return numeric.value();
    }

    private static List<JsonValue> array(JsonValue.JsonObject object, String name) {
        JsonValue value = member(object, name);
        if (!(value instanceof JsonValue.JsonArray array)) {
            throw new MarkerFormatException(
                    "the completion marker's \""
                            + name
                            + "\" is an array, and this one is a "
                            + value.getClass().getSimpleName());
        }
        return array.elements();
    }

    private static JsonValue member(JsonValue.JsonObject object, String name) {
        return object.member(name)
                .orElseThrow(
                        () ->
                                new MarkerFormatException(
                                        "the completion marker has no \"" + name + "\""));
    }
}
