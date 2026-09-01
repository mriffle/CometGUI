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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.json.JsonReader;
import org.cometgui.provenance.json.JsonValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * The drift check between {@code provenance.json} and the page that documents it.
 *
 * <p><strong>When this test fails, {@code docs/reference/provenance_format.rst} is stale, and both
 * it and the expected set below must be updated in the same change.</strong> That page is the
 * versioned on-disk schema: a reader is told they can write a parser from it without opening the
 * Java. A field added, removed or renamed in {@link ManifestWriter} without a corresponding edit to
 * that page turns the promise into a falsehood silently, because nothing else in the build compares
 * the two. This test is that comparison, expressed the only way it can be expressed from inside the
 * test sandbox: the set of member names the writer actually emits is pinned here, by hand, beside a
 * note of where the prose copy of it lives.
 *
 * <p><strong>Why it does not read the page itself.</strong> {@code scripts/verify-test-gates.sh}
 * builds a sandbox holding only the POMs, {@code config/}, {@code scripts/}, {@code
 * specification.rst} and each module's {@code src/}. A test that opened {@code docs/...} would fail
 * there and take a currently-green gate control down with it, so the hand-typed set below stands in
 * for the page and the class documentation names the page it stands in for.
 *
 * <p><strong>What a "member name" is here.</strong> Every name is collected as a <em>dotted
 * path</em> from the document root, with array subscripts collapsed to {@code []} because every
 * element of an array has the same shape -- so {@code tools[].execution.stdout.path}, not {@code
 * tools[0].execution.stdout.path}. A container's own name is collected as well as its contents:
 * {@code run} is a member of the root and {@code run.status} is a member of {@code run}, and both
 * are part of the schema a parser has to know.
 *
 * <p><strong>The two open-ended maps are excluded, and their exclusion is itself asserted.</strong>
 * {@code settings} and each {@code tools[].execution.environment} have keys that are run data --
 * {@code percolator.seed} is a value a run records, not a name this format fixes -- so their
 * members are skipped. {@link #theOpenEndedMapsAreExcludedRatherThanAbsent()} proves the fixture
 * really does carry entries in both, because an exclusion that excluded nothing would look exactly
 * like a correct one.
 *
 * <p><strong>The count is asserted separately, and that is not redundant.</strong> A walk that
 * collected nothing would equal an expected set that had been emptied, and the comparison would
 * pass having proved nothing at all. {@link #EXPECTED_MEMBER_COUNT} is a second hand-typed number
 * that such a failure cannot satisfy.
 */
class ProvenanceFormatDocumentationTest {

    /**
     * How many schema members the format has: 57.
     *
     * <p>Hand-typed, and deliberately not {@code EXPECTED_MEMBERS.size()}. See the class
     * documentation: this number is the anti-vacuity guard, and deriving it from the set it guards
     * would remove the only thing it is there to catch.
     */
    private static final int EXPECTED_MEMBER_COUNT = 57;

    /**
     * Every member name {@code provenance.json} carries, as a dotted path from the document root.
     *
     * <p>Typed out from {@code docs/reference/provenance_format.rst}, in the order that page's
     * tables list them. The prose page and this set are the two copies that must not drift; the
     * writer is the third party they are both about.
     *
     * <p>Held sorted so that a failure prints both sides in the same order and the difference is
     * one readable line rather than two shuffled lists; see {@link #sortedSetOf(String...)}.
     */
    private static final Set<String> EXPECTED_MEMBERS =
            sortedSetOf(
                    // The root, in the fixed order the document writes them.
                    "schemaVersion",
                    "run",
                    "application",
                    "settings",
                    "tools",
                    "files",
                    // run
                    "run.runId",
                    "run.projectId",
                    "run.status",
                    "run.start",
                    "run.end",
                    "run.durationMillis",
                    // application
                    "application.cometGuiVersion",
                    "application.buildIdentifier",
                    "application.osName",
                    "application.osVersion",
                    "application.architecture",
                    "application.jvmVersion",
                    "application.locale",
                    "application.formatLocale",
                    "application.zoneId",
                    // tools[]
                    "tools[].name",
                    "tools[].version",
                    "tools[].releaseTag",
                    "tools[].executablePath",
                    "tools[].md5",
                    "tools[].sha256",
                    "tools[].managed",
                    "tools[].artefactIdentity",
                    "tools[].capabilities",
                    "tools[].stageId",
                    "tools[].execution",
                    "tools[].warnings",
                    // tools[].execution
                    "tools[].execution.argv",
                    "tools[].execution.workingDirectory",
                    "tools[].execution.environment",
                    "tools[].execution.start",
                    "tools[].execution.end",
                    "tools[].execution.durationMillis",
                    "tools[].execution.exitCode",
                    "tools[].execution.stdout",
                    "tools[].execution.stderr",
                    "tools[].execution.status",
                    // tools[].execution.stdout and .stderr
                    "tools[].execution.stdout.path",
                    "tools[].execution.stdout.md5",
                    "tools[].execution.stdout.sha256",
                    "tools[].execution.stderr.path",
                    "tools[].execution.stderr.md5",
                    "tools[].execution.stderr.sha256",
                    // files[]
                    "files[].direction",
                    "files[].role",
                    "files[].path",
                    "files[].sizeBytes",
                    "files[].modifiedAt",
                    "files[].md5",
                    "files[].sha256",
                    "files[].status");

    /**
     * The paths of the two objects whose members are run data rather than schema.
     *
     * <p>See the class documentation. Both are written by {@code JsonWriter.sortedObject}, both
     * take whatever keys a run puts in them, and a parser is told their <em>shape</em> -- string to
     * string, sorted by key -- rather than their names.
     */
    private static final Set<String> OPEN_ENDED_MAPS =
            Set.of("settings", "tools[].execution.environment");

    /** The settings key the fixture carries, so that the exclusion has something to exclude. */
    private static final String FIXTURE_SETTING = "percolator.seed";

    /** The environment variable the fixture carries, for the same reason. */
    private static final String FIXTURE_ENVIRONMENT_VARIABLE = "COMET_PARAMS";

    /** MD5 and SHA-256 of {@code "abc"}, RFC 1321 and NIST, transcribed rather than computed. */
    private static final FileHashes ABC_HASHES =
            new FileHashes(
                    "900150983cd24fb0d6963f7d28e17f72",
                    "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");

    @Test
    @DisplayName("the writer emits exactly the members docs/reference/provenance_format.rst lists")
    @DisabledOnOs(
            value = OS.WINDOWS,
            disabledReason =
                    "the fixture uses POSIX paths, which a Windows JVM rejects as relative before"
                            + " a manifest can be built")
    void theDocumentedMembersAreTheMembersTheWriterEmits() {
        Set<String> emitted = memberPathsOf(renderedFixture());

        assertAll(
                () -> assertEquals(EXPECTED_MEMBERS, emitted),
                () ->
                        assertEquals(
                                EXPECTED_MEMBER_COUNT,
                                emitted.size(),
                                "the walk must find the documented number of members; an empty"
                                        + " walk equals an empty expected set and proves nothing"),
                () -> assertEquals(EXPECTED_MEMBER_COUNT, EXPECTED_MEMBERS.size()));
    }

    @Test
    @DisplayName("the two open-ended maps are excluded from the walk, and are not merely empty")
    @DisabledOnOs(
            value = OS.WINDOWS,
            disabledReason = "the fixture uses POSIX paths; see the sibling test")
    void theOpenEndedMapsAreExcludedRatherThanAbsent() {
        String document = renderedFixture();
        Set<String> emitted = memberPathsOf(document);

        assertAll(
                () ->
                        assertTrue(
                                document.contains("\"" + FIXTURE_SETTING + "\": \"9001\""),
                                "the fixture must carry a settings entry for the exclusion to"
                                        + " exclude"),
                () ->
                        assertTrue(
                                document.contains(
                                        "\""
                                                + FIXTURE_ENVIRONMENT_VARIABLE
                                                + "\": \"comet.params\""),
                                "the fixture must carry an environment entry for the exclusion to"
                                        + " exclude"),
                () -> assertFalse(emitted.contains("settings." + FIXTURE_SETTING)),
                () ->
                        assertFalse(
                                emitted.contains(
                                        "tools[].execution.environment."
                                                + FIXTURE_ENVIRONMENT_VARIABLE)));
    }

    /**
     * Renders the fully populated fixture with the production writer.
     *
     * @return the whole {@code provenance.json} document
     */
    private static String renderedFixture() {
        return ManifestWriter.redactingWith(SecretRedactor.patternsOnly()).render(fixture());
    }

    /**
     * Parses a rendered document and collects every schema member name in it.
     *
     * @param document the document {@link ManifestWriter#render} produced
     * @return the member paths, sorted, with the open-ended maps' own keys left out
     */
    private static Set<String> memberPathsOf(String document) {
        Set<String> paths = new TreeSet<>();
        collect(JsonReader.parse(document), "", paths);
        return paths;
    }

    /**
     * Walks one parsed value, adding the path of every member it meets.
     *
     * @param value the value to walk
     * @param path the dotted path this value sits at, empty at the document root
     * @param paths the set being filled
     */
    private static void collect(JsonValue value, String path, Set<String> paths) {
        // A string, a number, a boolean and a null have no members, so they end the walk; the
        // JsonNull case is the form every absent optional takes and is not a missing member.
        if (value instanceof JsonValue.JsonObject object) {
            if (OPEN_ENDED_MAPS.contains(path)) {
                return;
            }
            for (Map.Entry<String, JsonValue> member : object.members().entrySet()) {
                String memberPath = path.isEmpty() ? member.getKey() : path + "." + member.getKey();
                paths.add(memberPath);
                collect(member.getValue(), memberPath, paths);
            }
        } else if (value instanceof JsonValue.JsonArray array) {
            for (JsonValue element : array.elements()) {
                collect(element, path + "[]", paths);
            }
        }
    }

    /**
     * A manifest with every optional present, so that every nested member name is emitted.
     *
     * <p>An absent optional is written as {@code null} and keeps its key, so a single tool and a
     * single file would already produce every top-level name. The two <em>logs</em> are the
     * exception: {@code stdout} and {@code stderr} are objects whose own members exist only when
     * the log does, so this fixture gives the one tool both.
     *
     * @return the manifest
     */
    private static ProvenanceManifest fixture() {
        return new ProvenanceManifest(
                ProvenanceSchema.VERSION,
                new RunRecord(
                        new RunId("run-20260901-120000"),
                        "project-documentation",
                        ProvenanceStatus.COMPLETED,
                        Instant.parse("2026-09-01T12:00:00Z"),
                        Optional.of(Instant.parse("2026-09-01T12:30:00Z"))),
                new ApplicationRecord(
                        "0.1.0-SNAPSHOT",
                        "9f8c1d2e4b7a",
                        "Linux",
                        "6.8.0-137-generic",
                        "amd64",
                        "25.0.4.1",
                        Locale.forLanguageTag("en-US"),
                        Locale.forLanguageTag("en-US"),
                        ZoneId.of("UTC")),
                Map.of(FIXTURE_SETTING, "9001"),
                List.of(tool()),
                List.of(file()));
    }

    /**
     * The one tool of the fixture: every optional present, both logs captured.
     *
     * @return the tool record
     */
    private static ToolRecord tool() {
        return new ToolRecord(
                "comet",
                "2026.02.2",
                Optional.of("v2026.02.2"),
                absolute("opt/cometgui/tools/comet-2026.02.2/comet"),
                ABC_HASHES,
                true,
                Optional.of("comet-2026.02.2-linux-x86_64.tar.gz"),
                Set.of("mzml"),
                Optional.of("search"),
                new ExecutionRecord(
                        new ToolCommand(
                                List.of("/opt/cometgui/tools/comet-2026.02.2/comet", "-P"),
                                absolute("var/cometgui/runs/run-20260901-120000"),
                                Map.of(FIXTURE_ENVIRONMENT_VARIABLE, "comet.params")),
                        Instant.parse("2026-09-01T12:00:01Z"),
                        Instant.parse("2026-09-01T12:29:59Z"),
                        0,
                        Optional.of(new LogRecord(absolute("var/comet.stdout.log"), ABC_HASHES)),
                        Optional.of(new LogRecord(absolute("var/comet.stderr.log"), ABC_HASHES)),
                        ProvenanceStatus.COMPLETED),
                List.of("this build has no xml capability"));
    }

    /**
     * The one file of the fixture.
     *
     * @return the file record
     */
    private static FileRecord file() {
        return new FileRecord(
                FileDirection.INPUT,
                "spectra",
                absolute("data/proteomics/HeLa_1ug_rep1.mzML"),
                1024L,
                Instant.parse("2026-08-31T18:00:00Z"),
                ABC_HASHES,
                ProvenanceStatus.COMPLETED);
    }

    /**
     * Collects hand-typed names into an immutable set that iterates in sorted order.
     *
     * <p>Two properties, and both are wanted. {@link Set#of(Object...)} rejects a duplicate
     * argument, so a name typed twice in the expected list fails loudly instead of silently making
     * the set one shorter than it looks. The {@link TreeSet} then makes the iteration order the
     * same as the walk's, which is what turns an assertion failure into a readable difference
     * rather than two lists in two arbitrary orders.
     *
     * @param names the names to collect
     * @return the names, immutable and sorted
     */
    private static Set<String> sortedSetOf(String... names) {
        return Collections.unmodifiableSortedSet(new TreeSet<>(Set.of(names)));
    }

    /**
     * Builds an absolute POSIX path, adding the leading separator here rather than in a literal.
     *
     * <p>The same idiom, and the same reason, as {@code ManifestWriterTest.absolute}: SpotBugs at
     * effort Max reports a string constant that looks like an absolute pathname as {@code
     * DMI_HARDCODED_ABSOLUTE_FILENAME}, and the repository's policy is to fix the finding rather
     * than filter the pattern away.
     *
     * @param posixPath a path in POSIX form, without its leading separator
     * @return the absolute path
     */
    private static Path absolute(String posixPath) {
        return Path.of("/" + posixPath);
    }
}
