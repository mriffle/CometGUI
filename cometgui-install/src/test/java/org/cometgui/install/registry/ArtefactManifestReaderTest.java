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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.cometgui.install.registry.ArtefactManifestReader.InvalidArtefactManifestException;
import org.cometgui.install.registry.ManifestDocuments.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link ArtefactManifestReader}: every rejection, and every one of them graded over the
 * axes the rule does not depend on.
 *
 * <p><strong>Why the grading, spelled out once here.</strong> Phase 05's unit 1 was sent back
 * because a rejection was asserted at one point of an axis it did not depend on: the blank-note
 * rule was exercised over five blank strings and one evidence value, and an injected extra conjunct
 * -- {@code && evidence != UNVERIFIED} -- switched the rule off for the value every Windows and
 * macOS manifest row carries while 108 tests passed. That defect sat under 100% line coverage, 100%
 * branch coverage and a 99.7% mutation score, and none of the three could have found it: PIT
 * mutates the expression that is there and never adds a conjunct.
 *
 * <p>So each rejection below is asserted over {@link #SHAPES} -- four tools, five platforms, four
 * artefact kinds and both extraction modes -- rather than over whichever record happened to be
 * typed first. A rule that were quietly restricted to, say, {@code percolator} records would fail
 * here rather than pass everywhere.
 */
class ArtefactManifestReaderTest {

    /**
     * One record shape: a tool, a version, a platform, a kind and an extraction mode.
     *
     * @param tool the tool identifier
     * @param version the version text
     * @param releaseTag the upstream release tag
     * @param os the operating-system identifier
     * @param arch the architecture identifier
     * @param kind the artefact-kind identifier
     * @param namedMember whether the record names a single archive member
     */
    private record Shape(
            String tool,
            String version,
            String releaseTag,
            String os,
            String arch,
            String kind,
            boolean namedMember) {

        Json record() {
            return namedMember
                    ? ManifestDocuments.namedMember(tool, version, releaseTag, os, arch, kind)
                    : ManifestDocuments.wholeArtefact(tool, version, releaseTag, os, arch, kind);
        }

        /** The identity the reader prints, typed here rather than taken from the reader. */
        String label() {
            return tool + " " + version + " " + os + "-" + arch;
        }
    }

    private static final List<Shape> SHAPES =
            List.of(
                    new Shape(
                            "percolator", "3.07.1", "rel-3-07-01", "linux", "x86-64", "ZIP", true),
                    new Shape(
                            "percolator", "3.06.5", "rel-3-06-05", "macos", "x86-64", "ZIP", true),
                    new Shape("comet", "2026.02.2", "v2026.02.2", "windows", "x86-64", "ZIP", true),
                    new Shape("pdv", "2.7.0", "v2.7.0", "macos", "aarch64", "ZIP", true),
                    new Shape(
                            "comet",
                            "2026.02.2",
                            "v2026.02.2",
                            "linux",
                            "aarch64",
                            "BARE_EXECUTABLE",
                            false),
                    new Shape("pdv", "2.7.0", "v2.7.0", "linux", "x86-64", "ZIP", false),
                    new Shape(
                            "limelight-converter",
                            "2.8.1",
                            "v2.8.1",
                            "windows",
                            "x86-64",
                            "JAR",
                            false),
                    new Shape("percolator", "3.09", "rel-3-09", "macos", "aarch64", "ZIP", false));

    private static final List<Shape> NAMED_MEMBER_SHAPES =
            SHAPES.stream().filter(Shape::namedMember).toList();

    /** Members every record carries, whatever its tool, platform, kind or extraction mode. */
    private static final List<String> ALWAYS_REQUIRED =
            List.of(
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
                    "executable",
                    "licence",
                    "companions",
                    "capabilities",
                    "advisories",
                    "minimumHostRequirements",
                    "minimumCometGuiVersion");

    /** The message a document declaring an unreadable schema version begins with. */
    private static String unreadableVersion(long declared) {
        return "the tool artefact manifest declares schema version "
                + declared
                + ", and this build of CometGUI reads version 1";
    }

    /** The message an unknown capability identifier begins with. */
    private static final String UNKNOWN_CAPABILITY_PREFIX =
            "the tool artefact manifest is not valid:"
                    + " \"artefacts[0].capabilities[0].capability\" must be one of [XML_OUTPUT,";

    /** A document with an unknown member at the root. */
    private static final String UNKNOWN_ROOT_MEMBER =
            "{\"schemaVersion\": 1, \"artefacts\": [], \"signed\": false}";

    private static String rejectionOf(String document) {
        return assertThrows(
                        InvalidArtefactManifestException.class,
                        () -> ArtefactManifestReader.parse(document))
                .getMessage();
    }

    private static String expected(String path, String problem, Shape shape) {
        return "the tool artefact manifest is not valid: \""
                + path
                + "\" "
                + problem
                + ", in the record for "
                + shape.label();
    }

    /** The form a rejection takes while the record's own identity is still unreadable. */
    private static String expectedAt(String path, String problem, String recordPath) {
        return "the tool artefact manifest is not valid: \""
                + path
                + "\" "
                + problem
                + ", in the record at "
                + recordPath;
    }

    /** The four members that make a record nameable; break one and the record has no name yet. */
    private static final List<String> IDENTITY_MEMBERS = List.of("tool", "version", "os", "arch");

    // ------------------------------------------------------------------ it reads one --

    @Test
    @DisplayName("a valid document parses into the record it describes")
    void aValidDocumentParses() {
        ArtefactManifest manifest =
                ArtefactManifestReader.parse(
                        ManifestDocuments.document(
                                ManifestDocuments.namedMember(
                                                "percolator",
                                                "3.07.1",
                                                "rel-3-07-01",
                                                "linux",
                                                "x86-64",
                                                "ZIP")
                                        .render()));

        ArtefactRecord record = manifest.artefacts().get(0);
        assertAll(
                () -> assertEquals(1, manifest.schemaVersion()),
                () -> assertEquals(1, manifest.artefacts().size()),
                () -> assertEquals("percolator 3.07.1 linux-x86-64", record.describe()),
                () -> assertEquals("rel-3-07-01", record.releaseTag()),
                () -> assertEquals("bin/percolator", record.executablePath()),
                () -> assertTrue(record.isSingleMemberExtraction()),
                () -> assertEquals("percolator", record.member().orElseThrow().path()),
                () ->
                        assertEquals(
                                ManifestDocuments.MEMBER_SHA256,
                                record.member().orElseThrow().hashes().sha256()),
                () -> assertEquals(ManifestDocuments.ARTEFACT_MD5, record.hashes().md5()));
    }

    // --------------------------------------------------------------- missing members --

    @Test
    @DisplayName("a missing required member is rejected by name, for every tool and platform")
    void aMissingRequiredMemberIsRejected() {
        List<Executable> assertions = new ArrayList<>();
        for (Shape shape : SHAPES) {
            for (String name : ALWAYS_REQUIRED) {
                String document = ManifestDocuments.document(shape.record().without(name).render());
                String expectedMessage =
                        IDENTITY_MEMBERS.contains(name)
                                ? expectedAt("artefacts[0]." + name, "is missing", "artefacts[0]")
                                : expected("artefacts[0]." + name, "is missing", shape);
                assertions.add(
                        () ->
                                assertEquals(
                                        expectedMessage,
                                        rejectionOf(document),
                                        "member " + name + " of " + shape.label()));
            }
        }
        assertAll(assertions);
    }

    @Test
    @DisplayName("a named-member record missing one of its five member fields is rejected by name")
    void aMissingMemberModeMemberIsRejected() {
        List<String> members =
                List.of("member", "memberSizeBytes", "memberSha256", "memberMd5", "installedPath");
        List<Executable> assertions = new ArrayList<>();
        for (Shape shape : NAMED_MEMBER_SHAPES) {
            for (String name : members) {
                String document = ManifestDocuments.document(shape.record().without(name).render());
                assertions.add(
                        () ->
                                assertEquals(
                                        expected("artefacts[0]." + name, "is missing", shape),
                                        rejectionOf(document),
                                        "member " + name + " of " + shape.label()));
            }
        }
        assertAll(assertions);
    }

    @Test
    @DisplayName("a missing member of a nested object is rejected at its own path")
    void aMissingNestedMemberIsRejected() {
        Shape shape = SHAPES.get(0);
        String licenceMissingNote =
                shape.record()
                        .raw(
                                "licence",
                                new Json()
                                        .str("spdx", "Apache-2.0")
                                        .str("url", "https://example.org/LICENSE")
                                        .render())
                        .render();
        String capabilityMissingEvidence =
                shape.record()
                        .raw(
                                "capabilities",
                                ManifestDocuments.array(
                                        ManifestDocuments.capability(
                                                        "XML_OUTPUT", "observed-by-execution")
                                                .without("evidence")
                                                .render()))
                        .render();
        String requirementsMissingGlibc =
                shape.record()
                        .raw(
                                "minimumHostRequirements",
                                new Json()
                                        .raw("macos", "null")
                                        .raw("requiredHostLibraries", "[]")
                                        .render())
                        .render();

        assertAll(
                () ->
                        assertEquals(
                                expected("artefacts[0].licence.note", "is missing", shape),
                                rejectionOf(ManifestDocuments.document(licenceMissingNote))),
                () ->
                        assertEquals(
                                expected(
                                        "artefacts[0].capabilities[0].evidence",
                                        "is missing",
                                        shape),
                                rejectionOf(ManifestDocuments.document(capabilityMissingEvidence))),
                () ->
                        assertEquals(
                                expected(
                                        "artefacts[0].minimumHostRequirements.glibc",
                                        "is missing",
                                        shape),
                                rejectionOf(ManifestDocuments.document(requirementsMissingGlibc))));
    }

    @Test
    @DisplayName("a document with no schemaVersion and one with no artefacts are both rejected")
    void theRootMembersAreRequired() {
        assertAll(
                () ->
                        assertEquals(
                                "the tool artefact manifest is not valid: \"schemaVersion\" is"
                                        + " missing",
                                rejectionOf("{\"artefacts\": []}")),
                () ->
                        assertEquals(
                                "the tool artefact manifest is not valid: \"artefacts\" is missing",
                                rejectionOf("{\"schemaVersion\": 1}")));
    }

    // --------------------------------------------------------------- unknown members --

    @Test
    @DisplayName("an unknown member is rejected and named, for every tool and platform")
    void anUnknownMemberIsRejected() {
        List<Executable> assertions = new ArrayList<>();
        for (Shape shape : SHAPES) {
            String document =
                    ManifestDocuments.document(shape.record().renamed("sha256", "sha526").render());
            assertions.add(
                    () ->
                            assertTrue(
                                    rejectionOf(document)
                                            .startsWith(
                                                    "the tool artefact manifest is not valid:"
                                                            + " \"artefacts[0]\" has member(s) this"
                                                            + " build does not know: [sha526]."),
                                    shape.label() + ": " + rejectionOf(document)));
            assertions.add(
                    () ->
                            assertTrue(
                                    rejectionOf(document)
                                            .endsWith("in the record for " + shape.label()),
                                    "the identity is read before the unknown-member check exactly"
                                            + " so that this message can name the record: "
                                            + rejectionOf(document)));
        }
        assertAll(assertions);
    }

    @Test
    @DisplayName("an unknown member is rejected at every level of the document")
    void anUnknownMemberIsRejectedAtEveryLevel() {
        Shape shape = SHAPES.get(0);
        String unknownInLicence =
                shape.record()
                        .raw(
                                "licence",
                                new Json()
                                        .str("spdx", "Apache-2.0")
                                        .str("url", "https://example.org/LICENSE")
                                        .str("note", "a note")
                                        .str("spdxVersion", "3")
                                        .render())
                        .render();
        String unknownInCapability =
                shape.record()
                        .raw(
                                "capabilities",
                                ManifestDocuments.array(
                                        ManifestDocuments.capability(
                                                        "XML_OUTPUT", "observed-by-execution")
                                                .str("proof", "none")
                                                .render()))
                        .render();
        String unknownInCompanion =
                shape.record()
                        .raw(
                                "companions",
                                ManifestDocuments.array(
                                        ManifestDocuments.payloadCompanion("x", "DEB_PAYLOAD")
                                                .str("optional", "yes")
                                                .render()))
                        .render();

        assertAll(
                () ->
                        assertTrue(
                                rejectionOf(ManifestDocuments.document(unknownInLicence))
                                        .contains(
                                                "\"artefacts[0].licence\" has member(s) this build"
                                                        + " does not know: [spdxVersion]")),
                () ->
                        assertTrue(
                                rejectionOf(ManifestDocuments.document(unknownInCapability))
                                        .contains(
                                                "\"artefacts[0].capabilities[0]\" has member(s)"
                                                        + " this build does not know: [proof]")),
                () ->
                        assertTrue(
                                rejectionOf(ManifestDocuments.document(unknownInCompanion))
                                        .contains(
                                                "\"artefacts[0].companions[0]\" has member(s) this"
                                                        + " build does not know: [optional]")),
                () ->
                        assertTrue(
                                rejectionOf(UNKNOWN_ROOT_MEMBER)
                                        .contains(
                                                "the document has member(s) this build does not"
                                                        + " know: [signed]")));
    }

    @Test
    @DisplayName(
            "an unknown member is rejected inside an advisory, a companion member and the"
                    + " host requirements too")
    void anUnknownMemberIsRejectedInEveryNestedList() {
        Shape shape = SHAPES.get(0);
        String unknownInAdvisory =
                shape.record()
                        .raw(
                                "advisories",
                                ManifestDocuments.array(
                                        new Json()
                                                .str("id", "percolator.a")
                                                .str("text", "a caveat")
                                                .str("severity", "high")
                                                .render()))
                        .render();
        String unknownInCompanionMember =
                shape.record()
                        .raw(
                                "companions",
                                ManifestDocuments.array(
                                        ManifestDocuments.payloadCompanion("x", "DEB_PAYLOAD")
                                                .raw(
                                                        "members",
                                                        ManifestDocuments.array(
                                                                ManifestDocuments.companionMember(
                                                                                "a", "share/a")
                                                                        .str("mode", "0444")
                                                                        .render()))
                                                .render()))
                        .render();
        String unknownInRequirements =
                shape.record()
                        .raw(
                                "minimumHostRequirements",
                                new Json()
                                        .raw("glibc", "null")
                                        .raw("macos", "null")
                                        .raw("requiredHostLibraries", "[]")
                                        .str("glibcxx", "3.4.29")
                                        .render())
                        .render();

        assertAll(
                () ->
                        assertTrue(
                                rejectionOf(ManifestDocuments.document(unknownInAdvisory))
                                        .contains(
                                                "\"artefacts[0].advisories[0]\" has member(s) this"
                                                        + " build does not know: [severity]"),
                                rejectionOf(ManifestDocuments.document(unknownInAdvisory))),
                () ->
                        assertTrue(
                                rejectionOf(ManifestDocuments.document(unknownInCompanionMember))
                                        .contains(
                                                "\"artefacts[0].companions[0].members[0]\" has"
                                                        + " member(s) this build does not know:"
                                                        + " [mode]"),
                                rejectionOf(ManifestDocuments.document(unknownInCompanionMember))),
                () ->
                        assertTrue(
                                rejectionOf(ManifestDocuments.document(unknownInRequirements))
                                        .contains(
                                                "\"artefacts[0].minimumHostRequirements\" has"
                                                        + " member(s) this build does not know:"
                                                        + " [glibcxx]"),
                                rejectionOf(ManifestDocuments.document(unknownInRequirements))));
    }

    @Test
    @DisplayName("a member name that is not an identifier is reported without being printed")
    void aHostileMemberNameIsNotPrinted() {
        String document =
                ManifestDocuments.document(
                        SHAPES.get(0).record().str("pass word: hunter2", "x").render());

        String message = rejectionOf(document);
        assertAll(
                () -> assertTrue(message.contains("[<not an identifier>]"), message),
                () -> assertTrue(!message.contains("hunter2"), message));
    }

    // ------------------------------------------------------------- malformed digests --

    @Test
    @DisplayName("a malformed SHA-256 is rejected at its own field, for every tool and platform")
    void aMalformedSha256IsRejected() {
        List<String> malformed =
                List.of(
                        "",
                        "4d0e94af",
                        ManifestDocuments.ARTEFACT_SHA256 + "0",
                        ManifestDocuments.ARTEFACT_SHA256.replace('4', 'z'));
        List<Executable> assertions = new ArrayList<>();
        for (Shape shape : SHAPES) {
            for (String value : malformed) {
                String document =
                        ManifestDocuments.document(shape.record().str("sha256", value).render());
                assertions.add(
                        () ->
                                assertEquals(
                                        expected(
                                                "artefacts[0].sha256",
                                                "must be 64 hexadecimal characters",
                                                shape),
                                        rejectionOf(document),
                                        shape.label()
                                                + " with sha256 of length "
                                                + value.length()));
            }
        }
        assertAll(assertions);
    }

    @Test
    @DisplayName("a malformed MD5 is rejected at its own field, for every tool and platform")
    void aMalformedMd5IsRejected() {
        List<String> malformed =
                List.of(
                        "",
                        "9c86de1c",
                        ManifestDocuments.ARTEFACT_MD5 + "0",
                        ManifestDocuments.ARTEFACT_MD5.replace('9', 'q'));
        List<Executable> assertions = new ArrayList<>();
        for (Shape shape : SHAPES) {
            for (String value : malformed) {
                String document =
                        ManifestDocuments.document(shape.record().str("md5", value).render());
                assertions.add(
                        () ->
                                assertEquals(
                                        expected(
                                                "artefacts[0].md5",
                                                "must be 32 hexadecimal characters",
                                                shape),
                                        rejectionOf(document),
                                        shape.label() + " with md5 of length " + value.length()));
            }
        }
        assertAll(assertions);
    }

    @Test
    @DisplayName("a malformed digest is rejected wherever a digest appears, not only on a record")
    void aMalformedDigestIsRejectedEverywhereOneAppears() {
        Shape named = NAMED_MEMBER_SHAPES.get(0);
        String badMemberSha = named.record().str("memberSha256", "nope").render();
        String badMemberMd5 = named.record().str("memberMd5", "nope").render();
        String badCompanionSha =
                SHAPES.get(0)
                        .record()
                        .raw(
                                "companions",
                                ManifestDocuments.array(
                                        ManifestDocuments.payloadCompanion("x", "DEB_PAYLOAD")
                                                .str("sha256", "nope")
                                                .render()))
                        .render();
        String badCompanionMemberMd5 =
                SHAPES.get(0)
                        .record()
                        .raw(
                                "companions",
                                ManifestDocuments.array(
                                        ManifestDocuments.payloadCompanion("x", "DEB_PAYLOAD")
                                                .raw(
                                                        "members",
                                                        ManifestDocuments.array(
                                                                ManifestDocuments.companionMember(
                                                                                "a", "b")
                                                                        .str("md5", "nope")
                                                                        .render()))
                                                .render()))
                        .render();

        assertAll(
                () ->
                        assertEquals(
                                expected(
                                        "artefacts[0].memberSha256",
                                        "must be 64 hexadecimal characters",
                                        named),
                                rejectionOf(ManifestDocuments.document(badMemberSha))),
                () ->
                        assertEquals(
                                expected(
                                        "artefacts[0].memberMd5",
                                        "must be 32 hexadecimal characters",
                                        named),
                                rejectionOf(ManifestDocuments.document(badMemberMd5))),
                () ->
                        assertEquals(
                                expected(
                                        "artefacts[0].companions[0].sha256",
                                        "must be 64 hexadecimal characters",
                                        SHAPES.get(0)),
                                rejectionOf(ManifestDocuments.document(badCompanionSha))),
                () ->
                        assertEquals(
                                expected(
                                        "artefacts[0].companions[0].members[0].md5",
                                        "must be 32 hexadecimal characters",
                                        SHAPES.get(0)),
                                rejectionOf(ManifestDocuments.document(badCompanionMemberMd5))));
    }

    // -------------------------------------------------------- unknown enumerated ids --

    @Test
    @DisplayName("an unknown tool identifier is rejected, whatever the platform and kind")
    void anUnknownToolIsRejected() {
        List<Executable> assertions = new ArrayList<>();
        for (Shape shape : SHAPES) {
            for (String bad : List.of("Percolator", "percolator ", "comet2", "")) {
                String document =
                        ManifestDocuments.document(shape.record().str("tool", bad).render());
                assertions.add(
                        () ->
                                assertEquals(
                                        expectedAt(
                                                "artefacts[0].tool",
                                                "must be one of [comet, percolator, pdv,"
                                                        + " limelight-converter]",
                                                "artefacts[0]"),
                                        rejectionOf(document),
                                        shape.label() + " with tool \"" + bad + "\""));
            }
        }
        assertAll(assertions);
    }

    @Test
    @DisplayName("an unknown platform identifier is rejected, whatever the tool and kind")
    void anUnknownPlatformIsRejected() {
        List<Executable> assertions = new ArrayList<>();
        for (Shape shape : SHAPES) {
            String badOs = ManifestDocuments.document(shape.record().str("os", "osx").render());
            String badArch =
                    ManifestDocuments.document(shape.record().str("arch", "amd64").render());
            assertions.add(
                    () ->
                            assertEquals(
                                    expectedAt(
                                            "artefacts[0].os",
                                            "must be one of [linux, macos, windows]",
                                            "artefacts[0]"),
                                    rejectionOf(badOs),
                                    shape.label()));
            assertions.add(
                    () ->
                            assertEquals(
                                    expectedAt(
                                            "artefacts[0].arch",
                                            "must be one of [x86-64, aarch64]",
                                            "artefacts[0]"),
                                    rejectionOf(badArch),
                                    shape.label()
                                            + ": amd64 is what os.arch says and is deliberately not"
                                            + " a manifest spelling"));
        }
        assertAll(assertions);
    }

    @Test
    @DisplayName("an unknown artefact kind is rejected, and NSIS_PAYLOAD is one of them")
    void anUnknownKindIsRejected() {
        List<Executable> assertions = new ArrayList<>();
        for (Shape shape : SHAPES) {
            for (String bad : List.of("NSIS_PAYLOAD", "zip", "TARBALL", "RPM_PAYLOAD")) {
                String document =
                        ManifestDocuments.document(shape.record().str("kind", bad).render());
                assertions.add(
                        () ->
                                assertEquals(
                                        expected(
                                                "artefacts[0].kind",
                                                "must be one of [BARE_EXECUTABLE, ZIP, TAR_GZ,"
                                                        + " JAR, DEB_PAYLOAD, PKG_PAYLOAD]",
                                                shape),
                                        rejectionOf(document),
                                        shape.label()
                                                + " with kind "
                                                + bad
                                                + ": D-002 option C deleted NSIS payload"
                                                + " extraction, and only a new owner decision can"
                                                + " reinstate it"));
            }
        }
        assertAll(assertions);
    }

    /** The accepted evidence identifiers, as the reader lists them. */
    private static final String EVIDENCE_IDS =
            "must be one of [observed-by-execution, inferred-from-artefact-bytes, unverified]";

    /**
     * A capability that really belongs to a tool, or {@code null} where the tool has none.
     *
     * <p>PDV and the Limelight converter have no declarable capability at all -- they are JARs,
     * identified by version -- so a test that varies evidence cannot be graded over them, and
     * pretending otherwise would be asserting the wrong rejection.
     *
     * @param tool the tool identifier
     * @return a capability identifier that belongs to it, or {@code null}
     */
    private static String capabilityOf(String tool) {
        return switch (tool) {
            case "percolator" -> "XML_OUTPUT";
            case "comet" -> "PIN_OUTPUT";
            default -> null;
        };
    }

    @Test
    @DisplayName("an unknown capability identifier is rejected, whatever the tool and platform")
    void anUnknownCapabilityIsRejected() {
        List<Executable> assertions = new ArrayList<>();
        for (Shape shape : SHAPES) {
            String badCapability =
                    ManifestDocuments.document(
                            shape.record()
                                    .raw(
                                            "capabilities",
                                            ManifestDocuments.array(
                                                    ManifestDocuments.capability(
                                                                    "XML_INPUT",
                                                                    "observed-by-execution")
                                                            .render()))
                                    .render());
            assertions.add(
                    () ->
                            assertTrue(
                                    rejectionOf(badCapability)
                                            .startsWith(UNKNOWN_CAPABILITY_PREFIX),
                                    shape.label() + ": " + rejectionOf(badCapability)));
        }
        assertAll(assertions);
    }

    @Test
    @DisplayName("an unknown evidence identifier is rejected, and \"verified\" is one of them")
    void anUnknownEvidenceIsRejected() {
        List<Executable> assertions = new ArrayList<>();
        int graded = 0;
        for (Shape shape : SHAPES) {
            String capability = capabilityOf(shape.tool());
            if (capability == null) {
                continue;
            }
            graded++;
            for (String bad : List.of("verified", "confirmed", "OBSERVED_BY_EXECUTION", "")) {
                String document =
                        ManifestDocuments.document(
                                shape.record()
                                        .raw(
                                                "capabilities",
                                                ManifestDocuments.array(
                                                        ManifestDocuments.capability(
                                                                        capability, bad)
                                                                .render()))
                                        .render());
                assertions.add(
                        () ->
                                assertEquals(
                                        expected(
                                                "artefacts[0].capabilities[0].evidence",
                                                EVIDENCE_IDS,
                                                shape),
                                        rejectionOf(document),
                                        shape.label()
                                                + " with evidence \""
                                                + bad
                                                + "\": the words this project bans of a"
                                                + " binary nobody has run are not evidence"
                                                + " values either"));
            }
        }
        int gradedShapes = graded;
        assertions.add(
                () ->
                        assertTrue(
                                gradedShapes >= 4,
                                "the evidence axis must be graded over more than one tool and"
                                        + " platform, and only "
                                        + gradedShapes
                                        + " shape(s) were exercised"));
        assertAll(assertions);
    }

    // ------------------------------------------------------- capability on wrong tool --

    @Test
    @DisplayName("a capability attached to the wrong tool is rejected, over four tools")
    void aCapabilityOnTheWrongToolIsRejected() {
        record Mismatch(Shape shape, String capability, String owner) {}
        List<Mismatch> mismatches =
                List.of(
                        new Mismatch(
                                new Shape(
                                        "comet",
                                        "2026.02.2",
                                        "v2026.02.2",
                                        "linux",
                                        "x86-64",
                                        "BARE_EXECUTABLE",
                                        false),
                                "XML_OUTPUT",
                                "percolator"),
                        new Mismatch(
                                new Shape(
                                        "percolator",
                                        "3.07.1",
                                        "rel-3-07-01",
                                        "windows",
                                        "x86-64",
                                        "ZIP",
                                        true),
                                "THERMO_RAW_WINDOWS",
                                "comet"),
                        new Mismatch(
                                new Shape(
                                        "pdv", "2.7.0", "v2.7.0", "macos", "aarch64", "ZIP", false),
                                "PIN_OUTPUT",
                                "comet"),
                        new Mismatch(
                                new Shape(
                                        "limelight-converter",
                                        "2.8.1",
                                        "v2.8.1",
                                        "linux",
                                        "aarch64",
                                        "JAR",
                                        false),
                                "XML_DECOY_OUTPUT",
                                "percolator"));

        List<Executable> assertions = new ArrayList<>();
        for (Mismatch mismatch : mismatches) {
            String document =
                    ManifestDocuments.document(
                            mismatch.shape()
                                    .record()
                                    .raw(
                                            "capabilities",
                                            ManifestDocuments.array(
                                                    ManifestDocuments.capability(
                                                                    mismatch.capability(),
                                                                    "unverified")
                                                            .render()))
                                    .render());
            assertions.add(
                    () ->
                            assertEquals(
                                    expected(
                                            "artefacts[0].capabilities[0].capability",
                                            "is "
                                                    + mismatch.capability()
                                                    + ", which is a capability of "
                                                    + mismatch.owner()
                                                    + " and cannot be declared for "
                                                    + mismatch.shape().tool(),
                                            mismatch.shape()),
                                    rejectionOf(document),
                                    mismatch.capability() + " on " + mismatch.shape().tool()));
        }
        assertAll(assertions);
    }

    @Test
    @DisplayName("a companion gating a capability of another tool is rejected too")
    void aCompanionGatingAnotherToolsCapabilityIsRejected() {
        Shape shape = SHAPES.get(0);
        String document =
                ManifestDocuments.document(
                        shape.record()
                                .raw(
                                        "companions",
                                        ManifestDocuments.array(
                                                ManifestDocuments.payloadCompanion(
                                                                "x", "DEB_PAYLOAD")
                                                        .str(
                                                                "gatesCapability",
                                                                "THERMO_RAW_WINDOWS")
                                                        .render()))
                                .render());

        assertEquals(
                expected(
                        "artefacts[0].companions[0].gatesCapability",
                        "is THERMO_RAW_WINDOWS, which is a capability of comet and cannot be"
                                + " declared for percolator",
                        shape),
                rejectionOf(document));
    }

    // ------------------------------------------------------------- extraction modes --

    @Test
    @DisplayName("a record declaring both extraction modes is rejected, for every shape")
    void bothExtractionModesAreRejected() {
        List<Executable> assertions = new ArrayList<>();
        for (Shape shape : SHAPES) {
            Json both =
                    shape.namedMember()
                            ? shape.record().str("expectedExecutablePath", "bin/tool")
                            : shape.record()
                                    .str("member", "percolator")
                                    .num("memberSizeBytes", 2538632)
                                    .str("memberSha256", ManifestDocuments.MEMBER_SHA256)
                                    .str("memberMd5", ManifestDocuments.MEMBER_MD5)
                                    .str("installedPath", "bin/percolator");
            String document = ManifestDocuments.document(both.render());
            assertions.add(
                    () ->
                            assertTrue(
                                    rejectionOf(document)
                                            .startsWith(
                                                    "the tool artefact manifest is not valid:"
                                                            + " \"artefacts[0]\" declares both"
                                                            + " extraction modes:"),
                                    shape.label() + ": " + rejectionOf(document)));
            assertions.add(
                    () ->
                            assertTrue(
                                    rejectionOf(document)
                                            .endsWith("in the record for " + shape.label()),
                                    shape.label() + ": " + rejectionOf(document)));
        }
        assertAll(assertions);
    }

    @Test
    @DisplayName("a record declaring neither extraction mode is rejected, for every shape")
    void neitherExtractionModeIsRejected() {
        List<Executable> assertions = new ArrayList<>();
        for (Shape shape : SHAPES) {
            Json neither = shape.record();
            if (shape.namedMember()) {
                neither.without("member")
                        .without("memberSizeBytes")
                        .without("memberSha256")
                        .without("memberMd5")
                        .without("installedPath");
            } else {
                neither.without("expectedExecutablePath");
            }
            String document = ManifestDocuments.document(neither.render());
            assertions.add(
                    () ->
                            assertTrue(
                                    rejectionOf(document)
                                            .startsWith(
                                                    "the tool artefact manifest is not valid:"
                                                            + " \"artefacts[0]\" declares neither"
                                                            + " extraction mode:"),
                                    shape.label() + ": " + rejectionOf(document)));
        }
        assertAll(assertions);
    }

    @Test
    @DisplayName("whole-artefact mode without expectedExecutablePath declares no mode at all")
    void wholeArtefactModeWithoutItsPathDeclaresNoMode() {
        Shape shape = SHAPES.get(5);
        String document =
                ManifestDocuments.document(
                        shape.record().without("expectedExecutablePath").render());

        String message = rejectionOf(document);
        assertAll(
                () ->
                        assertTrue(
                                message.contains("declares neither extraction mode"),
                                "PDV is the one multi-entry archive the product installs, and"
                                        + " without expectedExecutablePath nothing says where its"
                                        + " JAR ends up: "
                                        + message),
                () -> assertTrue(message.contains("\"expectedExecutablePath\""), message));
    }

    @Test
    @DisplayName("a named-member record's install path may not escape the install directory")
    void anInstallPathThatEscapesIsRejected() {
        List<String> escapes =
                List.of(
                        "../percolator",
                        "/usr/bin/percolator",
                        "bin/../../percolator",
                        "bin\\percolator",
                        "C:/percolator",
                        "bin//percolator");
        List<Executable> assertions = new ArrayList<>();
        for (Shape shape : NAMED_MEMBER_SHAPES) {
            for (String escape : escapes) {
                String document =
                        ManifestDocuments.document(
                                shape.record().str("installedPath", escape).render());
                assertions.add(
                        () ->
                                assertTrue(
                                        rejectionOf(document).contains("\"artefacts[0].member\""),
                                        shape.label() + " with installedPath " + escape));
            }
        }
        assertAll(assertions);
    }

    @Test
    @DisplayName(
            "a member name that escapes its archive is accepted, because it never places a file")
    void anEscapingMemberNameIsAccepted() {
        /*
         * rel-3-06-05/percolator-noxml-osx-portable.zip really does name its single member
         * "../my_build/percolator-noxml/src/percolator". The manifest has to be able to name it,
         * because the extractor asks for it BY NAME and writes it to the declared destination --
         * the archive's own path never places a file. Taking the basename instead would be the
         * weakening this project forbids, and refusing the name would make a real upstream artefact
         * unnameable.
         */
        ArtefactManifest manifest =
                ArtefactManifestReader.parse(
                        ManifestDocuments.document(
                                ManifestDocuments.namedMember(
                                                "percolator",
                                                "3.06.5",
                                                "rel-3-06-05",
                                                "macos",
                                                "x86-64",
                                                "ZIP")
                                        .str(
                                                "member",
                                                "../my_build/percolator-noxml/src/percolator")
                                        .render()));

        ArtefactRecord record = manifest.artefacts().get(0);
        assertAll(
                () ->
                        assertEquals(
                                "../my_build/percolator-noxml/src/percolator",
                                record.member().orElseThrow().path()),
                () -> assertEquals("bin/percolator", record.executablePath()));
    }

    // ------------------------------------------------------------------- duplicates --

    @Test
    @DisplayName("two records for the same tool, version and platform are rejected, naming both")
    void aDuplicateRecordIsRejected() {
        List<Executable> assertions = new ArrayList<>();
        for (Shape shape : SHAPES) {
            String document =
                    ManifestDocuments.document(
                            shape.record().render(),
                            ManifestDocuments.wholeArtefact(
                                            shape.tool(),
                                            shape.version(),
                                            shape.releaseTag(),
                                            shape.os(),
                                            shape.arch(),
                                            "TAR_GZ")
                                    .render());
            assertions.add(
                    () ->
                            assertEquals(
                                    "the tool artefact manifest is not valid: \"artefacts\" must"
                                            + " hold artefacts this build's model accepts",
                                    rejectionOf(document),
                                    shape.label()));
        }
        assertAll(assertions);
    }

    @Test
    @DisplayName("two records differing only in platform, or only in version, are not duplicates")
    void differingRecordsAreNotDuplicates() {
        ArtefactManifest byPlatform =
                ArtefactManifestReader.parse(
                        ManifestDocuments.document(
                                ManifestDocuments.namedMember(
                                                "percolator",
                                                "3.07.1",
                                                "rel-3-07-01",
                                                "linux",
                                                "x86-64",
                                                "ZIP")
                                        .render(),
                                ManifestDocuments.namedMember(
                                                "percolator",
                                                "3.07.1",
                                                "rel-3-07-01",
                                                "windows",
                                                "x86-64",
                                                "ZIP")
                                        .render()));
        ArtefactManifest byVersion =
                ArtefactManifestReader.parse(
                        ManifestDocuments.document(
                                ManifestDocuments.namedMember(
                                                "percolator",
                                                "3.07.1",
                                                "rel-3-07-01",
                                                "linux",
                                                "x86-64",
                                                "ZIP")
                                        .render(),
                                ManifestDocuments.namedMember(
                                                "percolator",
                                                "3.06.5",
                                                "rel-3-06-05",
                                                "linux",
                                                "x86-64",
                                                "ZIP")
                                        .render()));

        assertAll(
                () -> assertEquals(2, byPlatform.artefacts().size()),
                () -> assertEquals(2, byVersion.artefacts().size()));
    }

    // ------------------------------------------------------------------------ URLs --

    @Test
    @DisplayName("a URL that is not https is rejected, for every tool and platform")
    void aNonHttpsUrlIsRejected() {
        List<String> bad =
                List.of(
                        "http://github.com/example/artefact",
                        "ftp://github.com/example/artefact",
                        "file:///tmp/artefact",
                        "github.com/example/artefact",
                        "https://user:secret@github.com/example/artefact");
        List<Executable> assertions = new ArrayList<>();
        for (Shape shape : SHAPES) {
            for (String url : bad) {
                String document =
                        ManifestDocuments.document(shape.record().str("url", url).render());
                assertions.add(
                        () ->
                                assertEquals(
                                        expected(
                                                "artefacts[0].url",
                                                "must be an absolute https URL with a host and no"
                                                        + " credentials",
                                                shape),
                                        rejectionOf(document),
                                        shape.label() + " with url " + url));
                assertions.add(
                        () ->
                                assertTrue(
                                        !rejectionOf(document).contains("secret"),
                                        "a rejected URL is never quoted back, because a user-info"
                                                + " component is exactly where a credential would"
                                                + " be"));
            }
        }
        assertAll(assertions);
    }

    // ------------------------------------------------------------- schema and shape --

    @Test
    @DisplayName("a schema version this build does not read is refused rather than half-read")
    void anUnreadableSchemaVersionIsRefused() {
        assertAll(
                () ->
                        assertTrue(
                                rejectionOf("{\"schemaVersion\": 2, \"artefacts\": []}")
                                        .startsWith(unreadableVersion(2))),
                () ->
                        assertTrue(
                                rejectionOf("{\"schemaVersion\": 0, \"artefacts\": []}")
                                        .startsWith(unreadableVersion(0))));
    }

    @Test
    @DisplayName("a member of the wrong JSON type is rejected, naming the type it should be")
    void aMemberOfTheWrongTypeIsRejected() {
        Shape shape = SHAPES.get(0);
        assertAll(
                () ->
                        assertEquals(
                                expected("artefacts[0].sizeBytes", "must be a whole number", shape),
                                rejectionOf(
                                        ManifestDocuments.document(
                                                shape.record()
                                                        .str("sizeBytes", "946303")
                                                        .render()))),
                () ->
                        assertEquals(
                                expected("artefacts[0].executable", "must be true or false", shape),
                                rejectionOf(
                                        ManifestDocuments.document(
                                                shape.record()
                                                        .str("executable", "true")
                                                        .render()))),
                () ->
                        assertEquals(
                                expected("artefacts[0].companions", "must be a JSON array", shape),
                                rejectionOf(
                                        ManifestDocuments.document(
                                                shape.record().raw("companions", "{}").render()))),
                () ->
                        assertEquals(
                                expected("artefacts[0].licence", "must be a JSON object", shape),
                                rejectionOf(
                                        ManifestDocuments.document(
                                                shape.record().raw("licence", "[]").render()))),
                () ->
                        assertEquals(
                                expected("artefacts[0].releaseTag", "must be a string", shape),
                                rejectionOf(
                                        ManifestDocuments.document(
                                                shape.record().num("releaseTag", 7).render()))),
                () ->
                        assertEquals(
                                "the tool artefact manifest is not valid: the document must be a"
                                        + " JSON object",
                                rejectionOf("[]")));
    }

    @Test
    @DisplayName("a manifest can be read from a file, and a malformed one there is refused too")
    void aManifestCanBeReadFromAFile(@TempDir Path directory) throws IOException {
        Path good = directory.resolve("tools.json");
        Files.writeString(
                good,
                ManifestDocuments.document(SHAPES.get(0).record().render()),
                StandardCharsets.UTF_8);
        Path bad = directory.resolve("broken.json");
        Files.writeString(
                bad,
                ManifestDocuments.document(SHAPES.get(0).record().str("md5", "nope").render()),
                StandardCharsets.UTF_8);

        ArtefactManifest manifest = ArtefactManifestReader.readFrom(good);

        assertAll(
                () ->
                        assertEquals(
                                "percolator 3.07.1 linux-x86-64",
                                manifest.artefacts().get(0).describe()),
                () ->
                        assertEquals(
                                expected(
                                        "artefacts[0].md5",
                                        "must be 32 hexadecimal characters",
                                        SHAPES.get(0)),
                                assertThrows(
                                                InvalidArtefactManifestException.class,
                                                () -> ArtefactManifestReader.readFrom(bad))
                                        .getMessage()),
                () ->
                        assertThrows(
                                IOException.class,
                                () ->
                                        ArtefactManifestReader.readFrom(
                                                directory.resolve("absent.json"))));
    }

    @Test
    @DisplayName("a version that is not two to four numeric components is rejected")
    void aMalformedVersionIsRejected() {
        List<Executable> assertions = new ArrayList<>();
        for (Shape shape : SHAPES) {
            for (String bad : List.of("3", "3.07.1.2.3", "v3.07.1", "3.07.x", "")) {
                String document =
                        ManifestDocuments.document(shape.record().str("version", bad).render());
                assertions.add(
                        () ->
                                assertEquals(
                                        expectedAt(
                                                "artefacts[0].version",
                                                "must be two to four numeric components, such as"
                                                        + " 3.09, 3.07.1 or 2026.02.2",
                                                "artefacts[0]"),
                                        rejectionOf(document),
                                        shape.label() + " with version \"" + bad + "\""));
            }
            String badMinimum =
                    ManifestDocuments.document(
                            shape.record().str("minimumCometGuiVersion", "one").render());
            assertions.add(
                    () ->
                            assertEquals(
                                    expected(
                                            "artefacts[0].minimumCometGuiVersion",
                                            "must be two to four numeric components, such as 3.09,"
                                                    + " 3.07.1 or 2026.02.2",
                                            shape),
                                    rejectionOf(badMinimum),
                                    shape.label()));
        }
        assertAll(assertions);
    }

    @Test
    @DisplayName("a URL that is not even a URI, and one carrying an empty user-info, are rejected")
    void aUrlThatIsNotAUriIsRejected() {
        Shape shape = SHAPES.get(0);

        assertAll(
                () ->
                        assertEquals(
                                expected("artefacts[0].url", "must be a URL", shape),
                                rejectionOf(
                                        ManifestDocuments.document(
                                                shape.record()
                                                        .str("url", "https://exa mple.org/a")
                                                        .render()))),
                () ->
                        assertEquals(
                                expected(
                                        "artefacts[0].url",
                                        "must be an absolute https URL with a host and no"
                                                + " credentials",
                                        shape),
                                rejectionOf(
                                        ManifestDocuments.document(
                                                shape.record()
                                                        .str("url", "https://@github.com/a")
                                                        .render())),
                                "java.net.URI reads \"@host\" as an empty user-info component,"
                                        + " which is still a credential slot"));
    }

    @Test
    @DisplayName("a manifest missing from the classpath is refused with a packaging diagnostic")
    void anAbsentClasspathResourceIsRefused() {
        assertEquals(
                "the tool artefact manifest is missing from the classpath at \"/tools.json\"; this"
                        + " build cannot offer any managed tool, and the cause is a packaging fault"
                        + " rather than anything the user did",
                assertThrows(
                                InvalidArtefactManifestException.class,
                                () -> ArtefactManifestReader.fromResource(null))
                        .getMessage());
    }

    @Test
    @DisplayName("the reader cannot be instantiated, even by reflection")
    void theReaderIsNotInstantiable() throws ReflectiveOperationException {
        Constructor<ArtefactManifestReader> constructor =
                ArtefactManifestReader.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertEquals(
                "ArtefactManifestReader is a utility class and is never instantiated",
                assertThrows(InvocationTargetException.class, constructor::newInstance)
                        .getCause()
                        .getMessage());
    }

    @Test
    @DisplayName("text that is not JSON is refused, and the parse failure is named")
    void textThatIsNotJsonIsRefused() {
        String message = rejectionOf("{\"schemaVersion\": 1,}");

        assertTrue(
                message.startsWith("the tool artefact manifest is not well-formed JSON: "),
                message);
    }
}
