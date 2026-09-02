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

package org.cometgui.install.registry;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.tools.ArtefactKind;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.MinimumHostRequirements;
import org.cometgui.domain.tools.ToolAdvisory;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.provenance.json.JsonParseException;
import org.cometgui.provenance.json.JsonReader;
import org.cometgui.provenance.json.JsonValue;

/**
 * Reads {@code tools.json} and refuses everything that is not one.
 *
 * <p>Written in the shape of {@link org.cometgui.provenance.manifest.ManifestReader} and using the
 * same {@link JsonReader}, because a second JSON parser in this repository would be a second
 * opinion about what a valid document is. What differs is the policy on members this build does not
 * know, and the difference is deliberate:
 *
 * <ul>
 *   <li>a <em>provenance</em> record is written by some build of CometGUI and read by another, so
 *       an older reader has to be able to ignore a field a newer writer added;
 *   <li>this manifest is <strong>shipped inside the same jar as this reader</strong>. The two are
 *       one release. A member this build does not know is therefore not forward compatibility, it
 *       is a typo -- {@code sha526} for {@code sha256}, {@code memberPath} for {@code member} --
 *       and a typo that is ignored is a field that silently stopped being read. So an unknown
 *       member is a rejection here, and it names the member.
 * </ul>
 *
 * <h2>Every rejection names the field and the record</h2>
 *
 * <p>A manifest is a data file; a rejection that says only "invalid manifest" makes a one-character
 * mistake a debugging session. Each message therefore names the member path -- {@code
 * artefacts[5].companions[0].members[1].sha256} -- and, as soon as the record's identity has been
 * read, the record: {@code in the record for percolator 3.07.1 windows-x86-64}. The identity is
 * read first, before anything else in a record, precisely so that it is available to every later
 * message.
 *
 * <h2>What a message never contains</h2>
 *
 * <p>No value out of the document reaches a message. That is {@link JsonParseException}'s rule
 * carried into the semantic layer, and it has one visible consequence: when something outside this
 * class rejects a value -- a record constructor, {@link FileHashes}, {@link ToolVersion#parse} --
 * that exception is not attached as a cause and its message is not repeated, because those messages
 * quote what they rejected. This class states the rule that was broken in its own words instead.
 * Two things are exempt because they are this product's own vocabulary rather than anything the
 * document supplies: the list of accepted identifiers for an enumerated field, and a record's
 * identity once its tool, version and platform have been resolved to constants.
 *
 * <p>Member <em>names</em> are the one thing quoted back, because "has an unknown member" with no
 * name is not a diagnosis. A name that is not a plain identifier is replaced rather than printed.
 *
 * <h2>Not instantiable, and stateless</h2>
 *
 * <p>Reading a manifest is one operation over a document.
 */
public final class ArtefactManifestReader {

    /**
     * Where the manifest sits on the classpath.
     *
     * <p>Absolute, so it is resolved against the classpath root rather than against this class's
     * package. {@code cometgui-install}'s POM ships {@code manifests/tools.json} from the
     * repository root into the jar, so this is the same file that is authoritative in the source
     * tree rather than a copy kept in step by hand.
     */
    public static final String RESOURCE_NAME = "/tools.json";

    /** The member path of the document root, which every other path grows from. */
    private static final String ROOT = "";

    /** A member name that can be quoted back into a message without quoting the document. */
    private static final Pattern PLAIN_NAME = Pattern.compile("[A-Za-z0-9_]{1,64}");

    private static final Set<String> ROOT_MEMBERS = Set.of("schemaVersion", "artefacts");

    private static final Set<String> RECORD_MEMBERS =
            Set.of(
                    "tool",
                    "version",
                    "releaseTag",
                    "os",
                    "arch",
                    "kind",
                    "url",
                    "sizeBytes",
                    "sha256",
                    "md5",
                    "member",
                    "memberSizeBytes",
                    "memberSha256",
                    "memberMd5",
                    "installedPath",
                    "expectedExecutablePath",
                    "executable",
                    "licence",
                    "companions",
                    "capabilities",
                    "advisories",
                    "minimumHostRequirements",
                    "minimumCometGuiVersion");

    /** The five members that together declare named-member extraction. */
    private static final Set<String> MEMBER_MODE_MEMBERS =
            Set.of("member", "memberSizeBytes", "memberSha256", "memberMd5", "installedPath");

    private static final Set<String> LICENCE_MEMBERS = Set.of("spdx", "url", "note");

    private static final Set<String> COMPANION_MEMBERS =
            Set.of(
                    "id",
                    "kind",
                    "url",
                    "sizeBytes",
                    "sha256",
                    "md5",
                    "runtimePrerequisite",
                    "gatesCapability",
                    "note",
                    "members");

    private static final Set<String> COMPANION_MEMBER_MEMBERS =
            Set.of("path", "sizeBytes", "sha256", "md5", "installedPath");

    private static final Set<String> CAPABILITY_MEMBERS = Set.of("capability", "evidence", "note");

    private static final Set<String> ADVISORY_MEMBERS = Set.of("id", "text");

    private static final Set<String> REQUIREMENT_MEMBERS =
            Set.of("glibc", "glibcxx", "macos", "requiredHostLibraries");

    private ArtefactManifestReader() {
        throw new AssertionError(
                "ArtefactManifestReader is a utility class and is never instantiated");
    }

    /**
     * Reads the manifest this build ships, from the classpath.
     *
     * <p>This is how the product reads it. Reading it from a relative path would work only when the
     * working directory happened to be the repository root, which is true in a developer's shell
     * and false in every installed copy.
     *
     * @return the manifest
     * @throws IOException if the resource cannot be read, or does not hold UTF-8 text
     * @throws InvalidArtefactManifestException if the resource is missing from the jar, or is not a
     *     manifest this build reads
     */
    public static ArtefactManifest readFromClasspath() throws IOException {
        try (InputStream stream = ArtefactManifestReader.class.getResourceAsStream(RESOURCE_NAME)) {
            return fromResource(stream);
        }
    }

    /**
     * Reads the manifest from what a classpath lookup returned.
     *
     * <p>Split out of {@link #readFromClasspath()} for one reason: {@link
     * Class#getResourceAsStream} answers {@code null} when the resource is absent, and that branch
     * is the one that fires when the jar is built wrong. It cannot be reached through the public
     * method without replacing this class's own class loader, so it is reachable here instead -- an
     * untestable branch is a branch nobody has seen work.
     *
     * @param stream the resource's bytes, or {@code null} if the classpath has no such resource
     * @return the manifest
     * @throws IOException if the stream cannot be read, or does not hold UTF-8 text
     * @throws InvalidArtefactManifestException if the resource is absent, or is not a manifest
     */
    static ArtefactManifest fromResource(InputStream stream) throws IOException {
        if (stream == null) {
            throw new InvalidArtefactManifestException(
                    "the tool artefact manifest is missing from the classpath at \""
                            + RESOURCE_NAME
                            + "\"; this build cannot offer any managed tool, and the cause is a"
                            + " packaging fault rather than anything the user did");
        }
        return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    }

    /**
     * Reads a manifest from an exact path.
     *
     * <p>UTF-8, and strictly: {@link Files#readString(Path, java.nio.charset.Charset)} reports a
     * malformed byte sequence rather than replacing it, so a file that is not UTF-8 fails here
     * instead of arriving as a document with the wrong characters in it.
     *
     * @param file the document to read
     * @return the manifest the file describes
     * @throws IOException if the file cannot be read, or does not hold UTF-8 text
     * @throws NullPointerException if {@code file} is {@code null}
     * @throws InvalidArtefactManifestException if the file is not a manifest this build reads
     */
    public static ArtefactManifest readFrom(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    /**
     * Reads a manifest from the text of a {@code tools.json} document.
     *
     * @param document the whole document
     * @return the manifest it describes
     * @throws NullPointerException if {@code document} is {@code null}
     * @throws InvalidArtefactManifestException if the text is not well-formed JSON, if the schema
     *     version is not one this build reads, or if the document is not a manifest this build's
     *     model accepts
     */
    public static ArtefactManifest parse(String document) {
        Objects.requireNonNull(document, "document");
        JsonValue root;
        try {
            root = JsonReader.parse(document);
        } catch (JsonParseException malformed) {
            throw new InvalidArtefactManifestException(
                    "the tool artefact manifest is not well-formed JSON: " + malformed.getMessage(),
                    malformed);
        }
        return read(object(root, ROOT, null));
    }

    private static ArtefactManifest read(JsonValue.JsonObject root) {
        requireOnlyKnownMembers(root, ROOT, null, ROOT_MEMBERS);
        long declared = integer(member(root, ROOT, null, "schemaVersion"), "schemaVersion", null);
        requireReadableVersion(declared);

        List<JsonValue> elements = array(member(root, ROOT, null, "artefacts"), "artefacts", null);
        List<ArtefactRecord> records = new ArrayList<>(elements.size());
        for (int index = 0; index < elements.size(); index++) {
            String path = "artefacts[" + index + "]";
            records.add(readRecord(object(elements.get(index), path, null), path));
        }
        return checked(
                "artefacts",
                null,
                "must hold artefacts this build's model accepts",
                () -> new ArtefactManifest((int) declared, records));
    }

    /*
     * The version decides everything, so it is decided before any other member is looked at: a
     * document this build cannot interpret is refused for that reason and not for whatever else
     * happens to be wrong with it.  A higher version is refused rather than half-read, because a
     * newer writer may have changed what a field MEANS rather than only added one.
     */
    private static void requireReadableVersion(long declared) {
        if (declared != ArtefactManifest.SCHEMA_VERSION) {
            throw new InvalidArtefactManifestException(
                    "the tool artefact manifest declares schema version "
                            + declared
                            + ", and this build of CometGUI reads version "
                            + ArtefactManifest.SCHEMA_VERSION
                            + "; a newer manifest may have changed what a field means rather than"
                            + " only added one, and an older one must be migrated explicitly, so"
                            + " neither is half-understood here");
        }
    }

    private static ArtefactRecord readRecord(JsonValue.JsonObject json, String path) {
        // Identity first, so that every rejection below can name the record it happened in --
        // including the unknown-member check, which looks at the whole object before any of it is
        // read.  A record whose own identity is unreadable is named by its position instead.
        String record = describe(json, path);

        requireOnlyKnownMembers(json, path, record, RECORD_MEMBERS);
        ToolName tool = toolName(text(json, path, record, "tool"), child(path, "tool"), record);
        ToolVersion version =
                toolVersion(text(json, path, record, "version"), child(path, "version"), record);
        HostPlatform platform =
                new HostPlatform(
                        operatingSystem(text(json, path, record, "os"), child(path, "os"), record),
                        architecture(
                                text(json, path, record, "arch"), child(path, "arch"), record));

        String releaseTag = text(json, path, record, "releaseTag");
        ArtefactKind kind =
                artefactKind(text(json, path, record, "kind"), child(path, "kind"), record);
        URI url = url(json, path, record, "url");
        long sizeBytes =
                integer(member(json, path, record, "sizeBytes"), child(path, "sizeBytes"), record);
        FileHashes hashes = hashes(json, path, record, "sha256", "md5");
        Optional<ArchiveMember> archiveMember = readExtractionMode(json, path, record);
        Optional<String> expectedExecutablePath =
                archiveMember.isPresent()
                        ? Optional.empty()
                        : Optional.of(text(json, path, record, "expectedExecutablePath"));
        boolean executable =
                flag(member(json, path, record, "executable"), child(path, "executable"), record);
        ArtefactLicence licence = readLicence(json, path, record);
        List<ArtefactCompanion> companions = readCompanions(json, path, record, tool);
        List<DeclaredCapability> capabilities = readCapabilities(json, path, record, tool);
        List<ToolAdvisory> advisories = readAdvisories(json, path, record);
        MinimumHostRequirements requirements = readRequirements(json, path, record);
        ToolVersion minimumCometGuiVersion =
                toolVersion(
                        text(json, path, record, "minimumCometGuiVersion"),
                        child(path, "minimumCometGuiVersion"),
                        record);

        return checked(
                path,
                record,
                "was rejected by the artefact model; open the manifest at that record",
                () ->
                        new ArtefactRecord(
                                tool,
                                version,
                                releaseTag,
                                platform,
                                kind,
                                url,
                                sizeBytes,
                                hashes,
                                archiveMember,
                                expectedExecutablePath,
                                executable,
                                licence,
                                companions,
                                capabilities,
                                advisories,
                                requirements,
                                minimumCometGuiVersion));
    }

    /*
     * How this record is named in every message about it.  The identity is read here first, before
     * anything else, and the reads are deliberately allowed to fail: a record whose tool, version
     * or platform is itself unreadable is named by its position in the document instead, and the
     * strict read below then rejects it and says which of those members was wrong.  Nothing is
     * swallowed -- every member read here is read again, strictly, a few lines later.
     */
    private static String describe(JsonValue.JsonObject json, String path) {
        try {
            ToolName tool = toolName(text(json, path, null, "tool"), child(path, "tool"), null);
            ToolVersion version =
                    toolVersion(text(json, path, null, "version"), child(path, "version"), null);
            HostPlatform platform =
                    new HostPlatform(
                            operatingSystem(text(json, path, null, "os"), child(path, "os"), null),
                            architecture(
                                    text(json, path, null, "arch"), child(path, "arch"), null));
            return "the record for " + tool.id() + " " + version.text() + " " + platform.id();
        } catch (InvalidArtefactManifestException notIdentifiableYet) {
            return "the record at " + path;
        }
    }

    /*
     * A record declares exactly one extraction mode.  The named-member mode is declared by five
     * members that stand or fall together; the whole-artefact mode by one.  Which mode a record is
     * in is decided by whether ANY of the five is present, so that a record with four of them is a
     * named-member record missing a field -- named -- rather than a whole-artefact record with
     * four strays.
     */
    private static Optional<ArchiveMember> readExtractionMode(
            JsonValue.JsonObject json, String path, String record) {
        boolean namedMember =
                MEMBER_MODE_MEMBERS.stream().anyMatch(name -> json.member(name).isPresent());
        boolean wholeArtefact = json.member("expectedExecutablePath").isPresent();
        if (namedMember && wholeArtefact) {
            throw invalid(
                    path,
                    record,
                    "declares both extraction modes: \"member\" and its four companion members"
                            + " name a single member to take out of the archive, and"
                            + " \"expectedExecutablePath\" unpacks the whole artefact, so a record"
                            + " carrying both has two answers to one question");
        }
        if (!namedMember && !wholeArtefact) {
            throw invalid(
                    path,
                    record,
                    "declares neither extraction mode: give \"member\", \"memberSizeBytes\","
                            + " \"memberSha256\", \"memberMd5\" and \"installedPath\" to name a"
                            + " single member and where it is installed, or"
                            + " \"expectedExecutablePath\" to unpack the whole artefact and say"
                            + " where the executable ends up");
        }
        if (!namedMember) {
            return Optional.empty();
        }
        String name = text(json, path, record, "member");
        long memberSize =
                integer(
                        member(json, path, record, "memberSizeBytes"),
                        child(path, "memberSizeBytes"),
                        record);
        FileHashes memberHashes = hashes(json, path, record, "memberSha256", "memberMd5");
        String installedPath = text(json, path, record, "installedPath");
        return Optional.of(
                checked(
                        child(path, "member"),
                        record,
                        "must name a member, a positive size, two digests and a relative install"
                                + " path inside the install directory",
                        () -> new ArchiveMember(name, memberSize, memberHashes, installedPath)));
    }

    private static ArtefactLicence readLicence(
            JsonValue.JsonObject json, String path, String record) {
        String licencePath = child(path, "licence");
        JsonValue.JsonObject licence =
                object(member(json, path, record, "licence"), licencePath, record);
        requireOnlyKnownMembers(licence, licencePath, record, LICENCE_MEMBERS);
        String spdx = text(licence, licencePath, record, "spdx");
        URI url = url(licence, licencePath, record, "url");
        String note = text(licence, licencePath, record, "note");
        return checked(
                licencePath,
                record,
                "must carry a non-blank SPDX identifier, an https URL and a non-blank note",
                () -> new ArtefactLicence(spdx, url, note));
    }

    private static List<ArtefactCompanion> readCompanions(
            JsonValue.JsonObject json, String path, String record, ToolName tool) {
        String companionsPath = child(path, "companions");
        List<JsonValue> elements =
                array(member(json, path, record, "companions"), companionsPath, record);
        List<ArtefactCompanion> companions = new ArrayList<>(elements.size());
        for (int index = 0; index < elements.size(); index++) {
            String companionPath = companionsPath + "[" + index + "]";
            companions.add(
                    readCompanion(
                            object(elements.get(index), companionPath, record),
                            companionPath,
                            record,
                            tool));
        }
        return companions;
    }

    private static ArtefactCompanion readCompanion(
            JsonValue.JsonObject json, String path, String record, ToolName tool) {
        requireOnlyKnownMembers(json, path, record, COMPANION_MEMBERS);
        String id = text(json, path, record, "id");
        ArtefactKind kind =
                artefactKind(text(json, path, record, "kind"), child(path, "kind"), record);
        URI url = url(json, path, record, "url");
        long sizeBytes =
                integer(member(json, path, record, "sizeBytes"), child(path, "sizeBytes"), record);
        FileHashes hashes = hashes(json, path, record, "sha256", "md5");
        boolean runtimePrerequisite =
                flag(
                        member(json, path, record, "runtimePrerequisite"),
                        child(path, "runtimePrerequisite"),
                        record);
        Optional<ToolCapability> gates =
                optionalText(json, path, record, "gatesCapability")
                        .map(
                                value ->
                                        toolCapability(
                                                value, child(path, "gatesCapability"), record));
        gates.ifPresent(
                capability ->
                        requireBelongsTo(capability, tool, child(path, "gatesCapability"), record));
        String note = text(json, path, record, "note");

        String membersPath = child(path, "members");
        List<JsonValue> elements =
                array(member(json, path, record, "members"), membersPath, record);
        List<ArchiveMember> members = new ArrayList<>(elements.size());
        for (int index = 0; index < elements.size(); index++) {
            String memberPath = membersPath + "[" + index + "]";
            members.add(
                    readCompanionMember(
                            object(elements.get(index), memberPath, record), memberPath, record));
        }
        return checked(
                path,
                record,
                "was rejected by the companion model; a BARE_EXECUTABLE companion in particular"
                        + " must describe exactly the file it downloads",
                () ->
                        new ArtefactCompanion(
                                id,
                                kind,
                                url,
                                sizeBytes,
                                hashes,
                                runtimePrerequisite,
                                gates,
                                note,
                                members));
    }

    private static ArchiveMember readCompanionMember(
            JsonValue.JsonObject json, String path, String record) {
        requireOnlyKnownMembers(json, path, record, COMPANION_MEMBER_MEMBERS);
        String name = text(json, path, record, "path");
        long sizeBytes =
                integer(member(json, path, record, "sizeBytes"), child(path, "sizeBytes"), record);
        FileHashes hashes = hashes(json, path, record, "sha256", "md5");
        String installedPath = text(json, path, record, "installedPath");
        return checked(
                path,
                record,
                "must name a member, a positive size, two digests and a relative install path"
                        + " inside the install directory",
                () -> new ArchiveMember(name, sizeBytes, hashes, installedPath));
    }

    private static List<DeclaredCapability> readCapabilities(
            JsonValue.JsonObject json, String path, String record, ToolName tool) {
        String capabilitiesPath = child(path, "capabilities");
        List<JsonValue> elements =
                array(member(json, path, record, "capabilities"), capabilitiesPath, record);
        List<DeclaredCapability> capabilities = new ArrayList<>(elements.size());
        for (int index = 0; index < elements.size(); index++) {
            String capabilityPath = capabilitiesPath + "[" + index + "]";
            JsonValue.JsonObject entry = object(elements.get(index), capabilityPath, record);
            requireOnlyKnownMembers(entry, capabilityPath, record, CAPABILITY_MEMBERS);
            ToolCapability capability =
                    toolCapability(
                            text(entry, capabilityPath, record, "capability"),
                            child(capabilityPath, "capability"),
                            record);
            requireBelongsTo(capability, tool, child(capabilityPath, "capability"), record);
            CapabilityEvidence evidence =
                    capabilityEvidence(
                            text(entry, capabilityPath, record, "evidence"),
                            child(capabilityPath, "evidence"),
                            record);
            String note = text(entry, capabilityPath, record, "note");
            capabilities.add(
                    checked(
                            capabilityPath,
                            record,
                            "must carry a capability, its evidence and a non-blank note saying"
                                    + " where that evidence came from",
                            () -> new DeclaredCapability(capability, evidence, note)));
        }
        return capabilities;
    }

    private static List<ToolAdvisory> readAdvisories(
            JsonValue.JsonObject json, String path, String record) {
        String advisoriesPath = child(path, "advisories");
        List<JsonValue> elements =
                array(member(json, path, record, "advisories"), advisoriesPath, record);
        List<ToolAdvisory> advisories = new ArrayList<>(elements.size());
        for (int index = 0; index < elements.size(); index++) {
            String advisoryPath = advisoriesPath + "[" + index + "]";
            JsonValue.JsonObject entry = object(elements.get(index), advisoryPath, record);
            requireOnlyKnownMembers(entry, advisoryPath, record, ADVISORY_MEMBERS);
            String id = text(entry, advisoryPath, record, "id");
            String advisoryText = text(entry, advisoryPath, record, "text");
            advisories.add(
                    checked(
                            advisoryPath,
                            record,
                            "must carry a lower-case identifier of words joined by single dots or"
                                    + " hyphens and a non-blank sentence",
                            () -> new ToolAdvisory(id, advisoryText)));
        }
        return advisories;
    }

    private static MinimumHostRequirements readRequirements(
            JsonValue.JsonObject json, String path, String record) {
        String requirementsPath = child(path, "minimumHostRequirements");
        JsonValue.JsonObject requirements =
                object(
                        member(json, path, record, "minimumHostRequirements"),
                        requirementsPath,
                        record);
        requireOnlyKnownMembers(requirements, requirementsPath, record, REQUIREMENT_MEMBERS);
        Optional<GlibcVersion> glibc =
                optionalText(requirements, requirementsPath, record, "glibc")
                        .map(
                                value ->
                                        checked(
                                                child(requirementsPath, "glibc"),
                                                record,
                                                "must be a glibc version such as 2.34",
                                                () -> GlibcVersion.parse(value)));
        /*
         * The C++ runtime floor is read the same way and is deliberately a SEPARATE member rather
         * than something derived from the glibc one.  A GNU/Linux binary records GLIBC_* and
         * GLIBCXX_* symbol versions independently, and in the loader failure this project executed
         * the GLIBCXX line is reported FIRST -- so a manifest that carried only the glibc floor
         * would let an advance check answer "runnable" for a build that fails on libstdc++.
         */
        Optional<GlibcVersion> glibcxx =
                optionalText(requirements, requirementsPath, record, "glibcxx")
                        .map(
                                value ->
                                        checked(
                                                child(requirementsPath, "glibcxx"),
                                                record,
                                                "must be the numbers of a GLIBCXX symbol version,"
                                                        + " such as 3.4.29, written without the"
                                                        + " GLIBCXX_ prefix",
                                                () -> GlibcVersion.parse(value)));
        Optional<String> macos = optionalText(requirements, requirementsPath, record, "macos");
        String librariesPath = child(requirementsPath, "requiredHostLibraries");
        List<JsonValue> elements =
                array(
                        member(requirements, requirementsPath, record, "requiredHostLibraries"),
                        librariesPath,
                        record);
        List<String> libraries = new ArrayList<>(elements.size());
        for (int index = 0; index < elements.size(); index++) {
            libraries.add(string(elements.get(index), librariesPath + "[" + index + "]", record));
        }
        return checked(
                requirementsPath,
                record,
                "must carry an optional glibc version, an optional GLIBCXX version, an optional"
                        + " macOS version and a list of required host libraries with none blank"
                        + " and none named twice",
                () -> new MinimumHostRequirements(glibc, glibcxx, macos, libraries));
    }

    /*
     * A capability is a fact about one tool.  THERMO_RAW_WINDOWS said of Percolator would render
     * in the Tool Manager as something that build can do and would be recorded in provenance as
     * something the run relied on, so it is refused here rather than carried.  The same check
     * covers a companion's gated capability, which is the same mistake one level down.
     */
    private static void requireBelongsTo(
            ToolCapability capability, ToolName tool, String path, String record) {
        if (!capability.belongsTo(tool)) {
            throw invalid(
                    path,
                    record,
                    "is "
                            + capability.id()
                            + ", which is a capability of "
                            + capability.tool().id()
                            + " and cannot be declared for "
                            + tool.id());
        }
    }

    // ------------------------------------------------------------ typed field readers --

    private static ToolName toolName(String id, String path, String record) {
        try {
            return ToolName.fromId(id);
        } catch (IllegalArgumentException unknown) {
            throw invalid(path, record, "must be one of " + idsOf(ToolName.values(), ToolName::id));
        }
    }

    private static HostOperatingSystem operatingSystem(String id, String path, String record) {
        try {
            return HostOperatingSystem.fromId(id);
        } catch (IllegalArgumentException unknown) {
            throw invalid(
                    path,
                    record,
                    "must be one of "
                            + idsOf(HostOperatingSystem.values(), HostOperatingSystem::id));
        }
    }

    private static HostArchitecture architecture(String id, String path, String record) {
        try {
            return HostArchitecture.fromId(id);
        } catch (IllegalArgumentException unknown) {
            throw invalid(
                    path,
                    record,
                    "must be one of " + idsOf(HostArchitecture.values(), HostArchitecture::id));
        }
    }

    private static ArtefactKind artefactKind(String id, String path, String record) {
        try {
            return ArtefactKind.fromId(id);
        } catch (IllegalArgumentException unknown) {
            throw invalid(
                    path,
                    record,
                    "must be one of " + idsOf(ArtefactKind.values(), ArtefactKind::id));
        }
    }

    private static ToolCapability toolCapability(String id, String path, String record) {
        try {
            return ToolCapability.fromId(id);
        } catch (IllegalArgumentException unknown) {
            throw invalid(
                    path,
                    record,
                    "must be one of " + idsOf(ToolCapability.values(), ToolCapability::id));
        }
    }

    private static CapabilityEvidence capabilityEvidence(String id, String path, String record) {
        try {
            return CapabilityEvidence.fromId(id);
        } catch (IllegalArgumentException unknown) {
            throw invalid(
                    path,
                    record,
                    "must be one of " + idsOf(CapabilityEvidence.values(), CapabilityEvidence::id));
        }
    }

    private static ToolVersion toolVersion(String text, String path, String record) {
        try {
            return ToolVersion.parse(text);
        } catch (IllegalArgumentException notAVersion) {
            throw invalid(
                    path,
                    record,
                    "must be two to four numeric components, such as 3.09, 3.07.1 or 2026.02.2");
        }
    }

    private static <T> String idsOf(T[] values, java.util.function.Function<T, String> id) {
        return Arrays.stream(values).map(id).toList().toString();
    }

    private static URI url(
            JsonValue.JsonObject owner, String ownerPath, String record, String name) {
        String path = child(ownerPath, name);
        String value = string(member(owner, ownerPath, record, name), path, record);
        URI url;
        try {
            url = new URI(value);
        } catch (URISyntaxException notAUrl) {
            throw invalid(path, record, "must be a URL");
        }
        return checked(
                path,
                record,
                "must be an absolute https URL with a host and no credentials",
                () -> ArtefactValues.downloadUrl(url, name));
    }

    /*
     * The two digests are checked one at a time before FileHashes is asked for the pair, so that a
     * message can say WHICH of the two is malformed; FileHashes rejects the pair as a whole and
     * quotes the value it refused, which this class does not repeat.  The lengths come from that
     * type's own constants, so there is one statement of how long a digest is.
     */
    private static FileHashes hashes(
            JsonValue.JsonObject owner,
            String ownerPath,
            String record,
            String sha256Name,
            String md5Name) {
        String sha256 =
                digest(
                        text(owner, ownerPath, record, sha256Name),
                        FileHashes.SHA256_LENGTH,
                        child(ownerPath, sha256Name),
                        record);
        String md5 =
                digest(
                        text(owner, ownerPath, record, md5Name),
                        FileHashes.MD5_LENGTH,
                        child(ownerPath, md5Name),
                        record);
        return checked(
                child(ownerPath, sha256Name),
                record,
                "and its sibling digest must be accepted by the project's one checksum type",
                () -> new FileHashes(md5, sha256));
    }

    private static String digest(String value, int length, String path, String record) {
        try {
            return ArtefactValues.digest(value, length, path);
        } catch (IllegalArgumentException malformed) {
            throw invalid(path, record, "must be " + length + " hexadecimal characters");
        }
    }

    // ------------------------------------------------------------------ JSON plumbing --

    private static void requireOnlyKnownMembers(
            JsonValue.JsonObject owner, String path, String record, Set<String> known) {
        Set<String> unknown = new LinkedHashSet<>();
        for (String name : owner.members().keySet()) {
            if (!known.contains(name)) {
                unknown.add(PLAIN_NAME.matcher(name).matches() ? name : "<not an identifier>");
            }
        }
        if (!unknown.isEmpty()) {
            throw invalid(
                    path,
                    record,
                    "has member(s) this build does not know: "
                            + unknown
                            + ". The manifest ships in the same jar as this reader, so an unknown"
                            + " member is a misspelling and not a newer format; a misspelling that"
                            + " were ignored would be a field that silently stopped being read");
        }
    }

    private static JsonValue member(
            JsonValue.JsonObject owner, String ownerPath, String record, String name) {
        return owner.member(name)
                .orElseThrow(() -> invalid(child(ownerPath, name), record, "is missing"));
    }

    private static JsonValue.JsonObject object(JsonValue value, String path, String record) {
        if (value instanceof JsonValue.JsonObject asObject) {
            return asObject;
        }
        throw invalid(path, record, "must be a JSON object");
    }

    private static List<JsonValue> array(JsonValue value, String path, String record) {
        if (value instanceof JsonValue.JsonArray asArray) {
            return asArray.elements();
        }
        throw invalid(path, record, "must be a JSON array");
    }

    private static String string(JsonValue value, String path, String record) {
        if (value instanceof JsonValue.JsonString asString) {
            return asString.value();
        }
        throw invalid(path, record, "must be a string");
    }

    private static long integer(JsonValue value, String path, String record) {
        if (value instanceof JsonValue.JsonNumber asNumber) {
            return asNumber.value();
        }
        throw invalid(path, record, "must be a whole number");
    }

    private static boolean flag(JsonValue value, String path, String record) {
        if (value instanceof JsonValue.JsonBoolean asBoolean) {
            return asBoolean.value();
        }
        throw invalid(path, record, "must be true or false");
    }

    private static String text(
            JsonValue.JsonObject owner, String ownerPath, String record, String name) {
        return string(member(owner, ownerPath, record, name), child(ownerPath, name), record);
    }

    private static Optional<String> optionalText(
            JsonValue.JsonObject owner, String ownerPath, String record, String name) {
        JsonValue value = member(owner, ownerPath, record, name);
        if (value instanceof JsonValue.JsonNull) {
            return Optional.empty();
        }
        return Optional.of(string(value, child(ownerPath, name), record));
    }

    /**
     * Builds part of the model, turning any rejection into one that quotes no value.
     *
     * <p>The supplier holds nothing but a constructor call over values that were already read.
     * Reading a member inside it would put this {@code catch} in the way of a rejection that is
     * already precise about which member was wrong.
     *
     * @param <T> the type being built
     * @param path the member path the values came from
     * @param record the record they belong to, or {@code null} at the document root
     * @param rule what that member has to satisfy, in this class's own words
     * @param construction the constructor call to attempt
     * @return whatever it built
     */
    private static <T> T checked(
            String path, String record, String rule, Supplier<T> construction) {
        try {
            return construction.get();
        } catch (RuntimeException rejected) {
            throw invalid(path, record, rule);
        }
    }

    private static String child(String ownerPath, String name) {
        return ownerPath.isEmpty() ? name : ownerPath + "." + name;
    }

    private static InvalidArtefactManifestException invalid(
            String path, String record, String problem) {
        String subject = path.isEmpty() ? "the document" : "\"" + path + "\"";
        String where = record == null ? "" : ", in " + record;
        return new InvalidArtefactManifestException(
                "the tool artefact manifest is not valid: " + subject + " " + problem + where);
    }

    /**
     * Thrown when a document is not a tool artefact manifest this build can read.
     *
     * <p>One type for every way that can be true -- the text is not JSON, the schema version is not
     * this one, a member is missing, unknown or of the wrong shape, or the content is not something
     * the model accepts -- because a caller's response to all of them is the same: this build can
     * offer no managed tool, and here is which member of which record to look at.
     *
     * <p>See the enclosing class for the rule every message obeys: the member path, the record and
     * the rule, and never a value out of the document.
     */
    public static final class InvalidArtefactManifestException extends RuntimeException {

        @Serial private static final long serialVersionUID = 1L;

        /**
         * Creates the exception.
         *
         * @param message which member of which record broke which rule
         */
        InvalidArtefactManifestException(String message) {
            super(message);
        }

        /**
         * Creates the exception with the parse failure that caused it.
         *
         * <p>A {@link JsonParseException} is the one cause it is safe to attach, because that type
         * promises its own message quotes no document content.
         *
         * @param message which member of which record broke which rule
         * @param cause the parse failure, whose message is a rule and a position
         */
        InvalidArtefactManifestException(String message, JsonParseException cause) {
            super(message, cause);
        }
    }
}
