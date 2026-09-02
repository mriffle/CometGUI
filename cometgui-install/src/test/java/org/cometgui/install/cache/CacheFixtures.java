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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.tools.ArtefactKind;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.MinimumHostRequirements;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.registry.ArchiveMember;
import org.cometgui.install.registry.ArtefactCompanion;
import org.cometgui.install.registry.ArtefactLicence;
import org.cometgui.install.registry.ArtefactRecord;

/**
 * Synthetic artefacts whose bytes and whose manifest records actually agree.
 *
 * <p>Every record here pins the digest of bytes this class also produces, so an install of one is
 * an install of a real artefact in every respect except its size -- and a test that corrupts a byte
 * produces a genuine mismatch rather than a contrived one.
 *
 * <p>The digests are computed here with {@link MessageDigest} rather than with the project's {@link
 * org.cometgui.provenance.hashing.StreamingHashService}, which is what the installer uses. An
 * expected value computed by the code under test cannot fail, and the two agreeing is part of what
 * these tests check.
 */
final class CacheFixtures {

    /** Where fixture artefacts pretend to come from. */
    static final URI BASE = URI.create("https://example.invalid/releases/download/rel-t/");

    /** The platform the fixture records are built for. */
    static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    /** The DOS timestamp every fixture archive entry carries, so the bytes are reproducible. */
    private static final long FIXED_ENTRY_TIME = 315532800000L;

    /** The member name inside the interruption fixture's archive. */
    static final String MEMBER_NAME = "percolator";

    /** Where that member is installed. */
    static final String INSTALLED_BINARY = "bin/percolator";

    /** The member name inside the interruption fixture's companion archive. */
    static final String COMPANION_MEMBER = "usr/share/xml/percolator/percolator_out.xsd";

    /** Where that companion member is installed. */
    static final String INSTALLED_SCHEMA = "share/xml/percolator/percolator_out.xsd";

    /** The path the shipped manifest installs the real Percolator binary at. */
    static final String INSTALLED_BINARY_OF_PERCOLATOR = "bin/percolator";

    /** The interruption fixture's companion identifier. */
    static final String COMPANION_ID = "percolator-xsd-schemas-from-deb";

    private CacheFixtures() {}

    /**
     * Writes the fixture a second JVM has to be able to rebuild from disk.
     *
     * <p>Bytes on disk rather than bytes in code, because the child process must arrive at exactly
     * the record this process has -- including its pinned digests -- and the only way to be sure of
     * that is for both to derive it from the same files.
     *
     * @param directory where to write it
     * @throws IOException if it cannot be written
     */
    static void writeSharedFixture(Path directory) throws IOException {
        java.nio.file.Files.createDirectories(directory);
        byte[] binary = bytes("#!/bin/sh\necho cometgui-installed-and-runnable\n");
        byte[] schema = bytes("<?xml version=\"1.0\"?><xs:schema/>\n");
        java.nio.file.Files.write(directory.resolve("member.bin"), binary);
        java.nio.file.Files.write(directory.resolve("companion-member.bin"), schema);
        java.nio.file.Files.write(
                directory.resolve("artefact.zip"), zip(entry(MEMBER_NAME, binary)));
        java.nio.file.Files.write(
                directory.resolve("companion.zip"), zip(entry(COMPANION_MEMBER, schema)));
    }

    /**
     * The record the shared fixture describes: a portable archive with one named member and a
     * companion archive carrying one schema, which is the shape Percolator really has.
     *
     * @param directory the fixture directory
     * @return the record
     * @throws IOException if the fixture cannot be read
     */
    static ArtefactRecord sharedRecord(Path directory) throws IOException {
        byte[] archive = java.nio.file.Files.readAllBytes(directory.resolve("artefact.zip"));
        byte[] binary = java.nio.file.Files.readAllBytes(directory.resolve("member.bin"));
        byte[] companionArchive =
                java.nio.file.Files.readAllBytes(directory.resolve("companion.zip"));
        byte[] schema = java.nio.file.Files.readAllBytes(directory.resolve("companion-member.bin"));
        return namedMember(
                ToolName.PERCOLATOR,
                "3.07.1",
                archive,
                MEMBER_NAME,
                binary,
                INSTALLED_BINARY,
                List.of(
                        zipCompanion(
                                COMPANION_ID,
                                companionArchive,
                                List.of(member(COMPANION_MEMBER, schema, INSTALLED_SCHEMA)))));
    }

    /**
     * Serves the shared fixture's bytes at the record's URLs.
     *
     * @param fetcher the fetcher to load
     * @param record the record
     * @param directory the fixture directory
     * @throws IOException if the fixture cannot be read
     */
    static void serveSharedFixture(FakeFetcher fetcher, ArtefactRecord record, Path directory)
            throws IOException {
        fetcher.serve(
                record.url(), java.nio.file.Files.readAllBytes(directory.resolve("artefact.zip")));
        fetcher.serve(
                record.companions().get(0).url(),
                java.nio.file.Files.readAllBytes(directory.resolve("companion.zip")));
    }

    /**
     * The MD5 and SHA-256 of some bytes.
     *
     * @param content the bytes
     * @return their digests
     */
    static FileHashes hashesOf(byte[] content) {
        return new FileHashes(digest("MD5", content), digest("SHA-256", content));
    }

    private static String digest(String algorithm, byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("every Java runtime provides " + algorithm, impossible);
        }
    }

    /**
     * Some bytes that are not any other fixture's bytes.
     *
     * @param text what to make them from
     * @return the bytes
     */
    static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * A zip holding the given entries, in the given order.
     *
     * @param entries entry name to content
     * @return the archive bytes
     */
    static byte[] zip(Map<String, byte[]> entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ZipEntry header = new ZipEntry(entry.getKey());
                // A fixed time, so that the same entries always produce the same bytes: a fixture
                // whose digest changed between two runs would make every pinned digest a lie.
                header.setTime(FIXED_ENTRY_TIME);
                zip.putNextEntry(header);
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        } catch (IOException impossible) {
            throw new UncheckedIOException(
                    "a zip built in memory cannot fail to write", impossible);
        }
        return out.toByteArray();
    }

    /**
     * A one-entry map, for the common case of a portable archive holding one binary.
     *
     * @param name the entry name
     * @param content its bytes
     * @return the map
     */
    static Map<String, byte[]> entry(String name, byte[] content) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(name, content);
        return entries;
    }

    static ArtefactLicence licence() {
        return new ArtefactLicence(
                "Apache-2.0",
                URI.create("https://example.invalid/LICENSE"),
                "upstream LICENSE at tag rel-t is the Apache License 2.0");
    }

    /**
     * A {@code ZIP} record that takes one named member out of an archive.
     *
     * @param tool which tool
     * @param version the version text
     * @param archive the archive bytes
     * @param memberName the member's name inside the archive
     * @param memberContent the member's bytes
     * @param installedPath where the member is installed
     * @param companions the companions, possibly none
     * @return the record
     */
    static ArtefactRecord namedMember(
            ToolName tool,
            String version,
            byte[] archive,
            String memberName,
            byte[] memberContent,
            String installedPath,
            List<ArtefactCompanion> companions) {
        return new ArtefactRecord(
                tool,
                ToolVersion.parse(version),
                "rel-t",
                LINUX,
                ArtefactKind.ZIP,
                BASE.resolve(tool.id() + "-" + version + ".zip"),
                archive.length,
                hashesOf(archive),
                Optional.of(
                        new ArchiveMember(
                                memberName,
                                memberContent.length,
                                hashesOf(memberContent),
                                installedPath)),
                Optional.empty(),
                true,
                licence(),
                companions,
                List.of(),
                List.of(),
                MinimumHostRequirements.none(),
                ToolVersion.parse("0.1.0"));
    }

    /**
     * A {@code BARE_EXECUTABLE} record: the download is the installed file.
     *
     * @param tool which tool
     * @param version the version text
     * @param content the executable's bytes
     * @param installedPath where it is installed
     * @param companions the companions, possibly none
     * @return the record
     */
    static ArtefactRecord bareExecutable(
            ToolName tool,
            String version,
            byte[] content,
            String installedPath,
            List<ArtefactCompanion> companions) {
        return new ArtefactRecord(
                tool,
                ToolVersion.parse(version),
                "rel-t",
                LINUX,
                ArtefactKind.BARE_EXECUTABLE,
                BASE.resolve(tool.id() + "-" + version + ".exe"),
                content.length,
                hashesOf(content),
                Optional.empty(),
                Optional.of(installedPath),
                true,
                licence(),
                companions,
                List.of(),
                List.of(),
                MinimumHostRequirements.none(),
                ToolVersion.parse("0.1.0"));
    }

    /**
     * A {@code JAR} record: the download is the installed file, and it is not executable.
     *
     * @param tool which tool
     * @param version the version text
     * @param content the jar's bytes
     * @param installedPath where it is installed
     * @return the record
     */
    static ArtefactRecord jar(ToolName tool, String version, byte[] content, String installedPath) {
        return new ArtefactRecord(
                tool,
                ToolVersion.parse(version),
                "rel-t",
                LINUX,
                ArtefactKind.JAR,
                BASE.resolve(tool.id() + "-" + version + ".jar"),
                content.length,
                hashesOf(content),
                Optional.empty(),
                Optional.of(installedPath),
                false,
                licence(),
                List.of(),
                List.of(),
                List.of(),
                MinimumHostRequirements.none(),
                ToolVersion.parse("0.1.0"));
    }

    /**
     * A whole-artefact {@code ZIP} record: every entry is unpacked, and the manifest pins a digest
     * for none of them.
     *
     * @param tool which tool
     * @param version the version text
     * @param archive the archive bytes
     * @param expectedPath where the executable ends up
     * @return the record
     */
    static ArtefactRecord wholeArchive(
            ToolName tool, String version, byte[] archive, String expectedPath) {
        return new ArtefactRecord(
                tool,
                ToolVersion.parse(version),
                "rel-t",
                LINUX,
                ArtefactKind.ZIP,
                BASE.resolve(tool.id() + "-" + version + "-whole.zip"),
                archive.length,
                hashesOf(archive),
                Optional.empty(),
                Optional.of(expectedPath),
                true,
                licence(),
                List.of(),
                List.of(),
                List.of(),
                MinimumHostRequirements.none(),
                ToolVersion.parse("0.1.0"));
    }

    /**
     * A {@code BARE_EXECUTABLE} companion, whose one member is the download itself.
     *
     * @param id the companion's identifier
     * @param content its bytes
     * @param installedPath where it is installed
     * @return the companion
     */
    static ArtefactCompanion bareCompanion(String id, byte[] content, String installedPath) {
        return new ArtefactCompanion(
                id,
                ArtefactKind.BARE_EXECUTABLE,
                BASE.resolve(id),
                content.length,
                hashesOf(content),
                false,
                Optional.empty(),
                "a companion library that has to sit beside the executable",
                List.of(
                        new ArchiveMember(
                                installedPath, content.length, hashesOf(content), installedPath)));
    }

    /**
     * A {@code ZIP} companion, two of whose members are installed.
     *
     * @param id the companion's identifier
     * @param archive the archive bytes
     * @param members the members to take out
     * @return the companion
     */
    static ArtefactCompanion zipCompanion(String id, byte[] archive, List<ArchiveMember> members) {
        return new ArtefactCompanion(
                id,
                ArtefactKind.ZIP,
                BASE.resolve(id + ".zip"),
                archive.length,
                hashesOf(archive),
                false,
                Optional.empty(),
                "the schemas no portable archive ships, taken from the matching package",
                members);
    }

    /**
     * A member description whose digests are the given bytes'.
     *
     * @param path the member's name inside the archive
     * @param content its bytes
     * @param installedPath where it is installed
     * @return the member
     */
    static ArchiveMember member(String path, byte[] content, String installedPath) {
        return new ArchiveMember(path, content.length, hashesOf(content), installedPath);
    }

    /**
     * A path's SHA-256, computed independently of the installer's hasher.
     *
     * @param file the file
     * @return the digest, lower-case hexadecimal
     * @throws IOException if the file cannot be read
     */
    static String sha256Of(Path file) throws IOException {
        return digest("SHA-256", java.nio.file.Files.readAllBytes(file));
    }
}
