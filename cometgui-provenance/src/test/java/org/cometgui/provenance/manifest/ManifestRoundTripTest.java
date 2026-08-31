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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.secrets.SecretRedactor;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Writes a manifest, reads it back, and asserts the two are the same value.
 *
 * <p><strong>This suite proves symmetry. It does not prove correctness, and nothing in it should
 * ever be treated as if it did.</strong> A writer that emitted {@code "runid"} and a reader that
 * looked for {@code "runid"} would pass every test in this file, for every manifest it can
 * generate, while every {@code provenance.json} on disk was wrong and unreadable by anything else.
 * What proves the format right is the pair of hand-typed documents -- {@code ManifestWriterTest}
 * pins the bytes the writer must produce, {@code ManifestReaderTest} pins the values the reader
 * must produce from those bytes -- and both were typed from the format rather than captured from
 * the code. This file is the supplement that catches the other class of defect: a field the writer
 * emits and the reader forgets, a collection that loses its order, a value that survives one shape
 * of manifest and not another.
 *
 * <p>Two things it does prove that a single document cannot. The <strong>property</strong> suite
 * generates two hundred manifests from a fixed seed and round-trips every one, so a field that only
 * fails when an optional is absent, a collection is empty, or a number is at its extreme is caught
 * without anyone having thought to type that case out. And the round trip is checked to be
 * <strong>byte-stable</strong>: the document produced from the manifest read back must be identical
 * to the document that was read, so a reader that quietly normalised a value would fail here rather
 * than in a scientist's diff of two runs a year later.
 *
 * <p><strong>Where the round trip is deliberately lossy, that is asserted too.</strong> A
 * provenance document is milliseconds and a redacted document is redacted; both losses are
 * decisions, and {@link Lossy} pins them as such so that neither can be mistaken for a defect in
 * this reader, or quietly undone.
 */
class ManifestRoundTripTest {

    /** MD5 of {@code "abc"}, RFC 1321, hand-transcribed. */
    private static final String ABC_MD5 = "900150983cd24fb0d6963f7d28e17f72";

    /** SHA-256 of {@code "abc"}, NIST, hand-transcribed. */
    private static final String ABC_SHA256 =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    /** MD5 of the empty file, RFC 1321, hand-transcribed. */
    private static final String EMPTY_MD5 = "d41d8cd98f00b204e9800998ecf8427e";

    /** SHA-256 of the empty file, NIST, hand-transcribed. */
    private static final String EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    /** The digest pair every generated record carries. */
    private static final FileHashes ABC_HASHES = new FileHashes(ABC_MD5, ABC_SHA256);

    /** The second digest pair, so that two records in one manifest are distinguishable. */
    private static final FileHashes EMPTY_HASHES = new FileHashes(EMPTY_MD5, EMPTY_SHA256);

    /**
     * The oldest instant {@link org.cometgui.provenance.json.CanonicalTimestamp} can render.
     *
     * <p>Not {@link Instant#MIN}: that is year -1 000 000 000, and the pattern's {@code uuuu} field
     * is backed by a {@code LocalDate}, whose range stops at year -999 999 999. The writer throws
     * beyond this, so this is the extreme the format actually supports and therefore the extreme
     * worth round-tripping.
     */
    private static final Instant OLDEST = Instant.parse("-999999999-01-01T00:00:00Z");

    /** The newest instant the format can render, for the same reason as {@link #OLDEST}. */
    private static final Instant NEWEST = Instant.parse("+999999999-12-31T23:59:59.999Z");

    /** The writer under test, with the weakest redactor the class permits. */
    private static ManifestWriter writer() {
        return ManifestWriter.redactingWith(SecretRedactor.patternsOnly());
    }

    /**
     * Writes a manifest, reads it back, and checks the document is stable across a second write.
     *
     * <p>The document is also checked to contain no redaction marker. Redaction is one-way, so a
     * generated value that happened to trip a rule would make an equality assertion fail for a
     * reason that has nothing to do with the reader; this turns that into a message that says so.
     *
     * @param manifest the manifest to send round
     * @return the manifest that came back
     */
    private static ProvenanceManifest roundTrip(ProvenanceManifest manifest) {
        String document = writer().render(manifest);
        assertFalse(
                document.contains(SecretRedactor.REDACTION_MARKER),
                "a fixture tripped a redaction rule, so this round trip is not measuring the"
                        + " reader: "
                        + document);
        ProvenanceManifest readBack = ManifestReader.parse(document);
        assertEquals(
                document,
                writer().render(readBack),
                "the document did not survive being read and written again");
        return readBack;
    }

    /**
     * Builds an absolute POSIX path, adding the leading separator here rather than in a literal.
     *
     * @param posixPath a path in POSIX form, without its leading separator
     * @return the absolute path
     */
    private static Path absolute(String posixPath) {
        return Path.of("/" + posixPath);
    }

    /**
     * An absolute path, or an aborted test where this JVM's charset cannot hold one.
     *
     * @param posixPath a path in POSIX form, without its leading separator
     * @return the absolute path
     */
    private static Path representableOrAbort(String posixPath) {
        try {
            return absolute(posixPath);
        } catch (InvalidPathException notRepresentable) {
            return Assumptions.abort(
                    "this JVM's sun.jnu.encoding is "
                            + System.getProperty("sun.jnu.encoding")
                            + ", which cannot represent a non-ASCII path; re-run under a UTF-8"
                            + " locale (LANG=C.UTF-8) to exercise this test");
        }
    }

    /**
     * A run record with the given status and interval.
     *
     * @param status the run's state
     * @param start when it began
     * @param end when it finished, if it did
     * @return the run record
     */
    private static RunRecord run(ProvenanceStatus status, Instant start, Optional<Instant> end) {
        return new RunRecord(new RunId("run-20260831-091500"), "project-alpha", status, start, end);
    }

    /**
     * An application record with values chosen to exercise the locale and zone conversions.
     *
     * @return the application record
     */
    private static ApplicationRecord application() {
        return new ApplicationRecord(
                "0.1.0-SNAPSHOT",
                "9f8c1d2e4b7a",
                "Linux",
                "6.8.0-137-generic",
                "amd64",
                "25.0.4.1",
                Locale.forLanguageTag("en-US"),
                Locale.forLanguageTag("de-DE"),
                ZoneId.of("Europe/Berlin"));
    }

    /**
     * An execution with the given interval, exit code and command.
     *
     * @param command what was launched
     * @param start when it started
     * @param end when it finished
     * @param exitCode what it exited with
     * @return the execution record
     */
    private static ExecutionRecord execution(
            ToolCommand command, Instant start, Instant end, int exitCode) {
        return new ExecutionRecord(
                command,
                start,
                end,
                exitCode,
                Optional.of(new LogRecord(absolute("var/runs/r1/out.log"), ABC_HASHES)),
                Optional.empty(),
                ProvenanceStatus.COMPLETED);
    }

    /**
     * A plain command with no argument any redaction rule reacts to.
     *
     * @return the command
     */
    private static ToolCommand command() {
        return new ToolCommand(
                List.of("/opt/comet/comet", "-P", "comet.params"),
                absolute("var/runs/r1"),
                Map.of("COMET_PARAMS", "comet.params"));
    }

    @Nested
    @DisplayName("A manifest")
    class Manifests {

        @Test
        @DisplayName("with every field set comes back equal, and its document is byte-stable")
        void aFullManifestComesBackEqual() {
            ProvenanceManifest manifest =
                    ProvenanceManifest.current(
                            run(
                                    ProvenanceStatus.COMPLETED,
                                    Instant.parse("2026-08-31T09:14:00.250Z"),
                                    Optional.of(Instant.parse("2026-08-31T09:48:00Z"))),
                            application(),
                            Map.of("percolator.seed", "9001", "comet.num-threads", "8"),
                            List.of(
                                    new ToolRecord(
                                            "comet",
                                            "2026.02.2",
                                            Optional.of("v2026.02.2"),
                                            absolute("opt/comet/comet"),
                                            ABC_HASHES,
                                            true,
                                            Optional.of("comet-2026.02.2.tar.gz"),
                                            Set.of("mzml", "mzxml"),
                                            Optional.of("search"),
                                            execution(
                                                    command(),
                                                    Instant.parse("2026-08-31T09:15:00Z"),
                                                    Instant.parse("2026-08-31T09:47:30.500Z"),
                                                    0),
                                            List.of("a warning", "another warning"))),
                            List.of(
                                    new FileRecord(
                                            FileDirection.INPUT,
                                            "spectra",
                                            absolute("data/spectra.mzML"),
                                            1234567890123L,
                                            Instant.parse("2026-08-30T18:00:00.001Z"),
                                            ABC_HASHES,
                                            ProvenanceStatus.COMPLETED)));

            assertEquals(manifest, roundTrip(manifest));
        }

        @Test
        @DisplayName("with every optional absent and every collection empty comes back equal")
        void anEmptyManifestComesBackEqual() {
            ProvenanceManifest manifest =
                    ProvenanceManifest.current(
                            run(
                                    ProvenanceStatus.RUNNING,
                                    Instant.parse("2026-08-31T10:15:00Z"),
                                    Optional.empty()),
                            new ApplicationRecord(
                                    "0.1.0-SNAPSHOT",
                                    "9f8c1d2e4b7a",
                                    "Windows 11",
                                    "10.0",
                                    "aarch64",
                                    "25.0.4.1",
                                    Locale.ROOT,
                                    Locale.forLanguageTag("tr-TR"),
                                    ZoneId.of("UTC")),
                            Map.of(),
                            List.of(),
                            List.of());

            ProvenanceManifest readBack = roundTrip(manifest);

            assertAll(
                    () -> assertEquals(manifest, readBack),
                    () -> assertEquals(Optional.empty(), readBack.run().end()),
                    () -> assertEquals(Locale.ROOT, readBack.application().locale()),
                    () -> assertEquals(Map.of(), readBack.settings()),
                    () -> assertEquals(List.of(), readBack.tools()),
                    () -> assertEquals(List.of(), readBack.files()));
        }

        @Test
        @DisplayName("carrying Unicode and emoji in its text comes back unchanged, on any JVM")
        void unicodeAndEmojiInTextComeBackUnchanged() {
            ProvenanceManifest manifest =
                    ProvenanceManifest.current(
                            run(
                                    ProvenanceStatus.COMPLETED,
                                    Instant.parse("2026-08-31T09:14:00Z"),
                                    Optional.of(Instant.parse("2026-08-31T09:48:00Z"))),
                            application(),
                            Map.of("limelight.repertoire", "données µg 🧬"),
                            List.of(
                                    new ToolRecord(
                                            "comet",
                                            "2026.02.2",
                                            Optional.of("v2026.02.2-é"),
                                            absolute("opt/comet/comet"),
                                            ABC_HASHES,
                                            true,
                                            Optional.empty(),
                                            Set.of("mzml"),
                                            Optional.empty(),
                                            execution(
                                                    command(),
                                                    Instant.parse("2026-08-31T09:15:00Z"),
                                                    Instant.parse("2026-08-31T09:16:00Z"),
                                                    0),
                                            List.of(
                                                    "skipped 2 spectra in"
                                                            + " /data/protéomique/HeLa_1µg.mzML",
                                                    "wrote /data/🧬-run/résultats.txt"))),
                            List.of(
                                    new FileRecord(
                                            FileDirection.OUTPUT,
                                            "résultats-🧬",
                                            absolute("data/out.txt"),
                                            2048L,
                                            Instant.parse("2026-08-31T09:47:00Z"),
                                            EMPTY_HASHES,
                                            ProvenanceStatus.PARTIAL)));

            ProvenanceManifest readBack = roundTrip(manifest);

            assertAll(
                    () -> assertEquals(manifest, readBack),
                    () ->
                            assertEquals(
                                    "données µg 🧬",
                                    readBack.settings().get("limelight.repertoire")),
                    () -> assertEquals("résultats-🧬", readBack.files().get(0).role()),
                    () ->
                            assertEquals(
                                    "wrote /data/🧬-run/résultats.txt",
                                    readBack.tools().get(0).warnings().get(1)),
                    () ->
                            assertEquals(
                                    Optional.of("v2026.02.2-é"),
                                    readBack.tools().get(0).releaseTag()));
        }

        @Test
        @DisplayName(
                "carrying Unicode and emoji in its paths comes back unchanged, where the JVM"
                        + " can hold such a path")
        void unicodeAndEmojiInPathsComeBackUnchanged() {
            Path spectra = representableOrAbort("data/protéomique/HeLa_1µg_rep1.mzML");
            Path export = representableOrAbort("data/🧬-run/résultats.txt");
            ProvenanceManifest manifest =
                    ProvenanceManifest.current(
                            run(
                                    ProvenanceStatus.COMPLETED,
                                    Instant.parse("2026-08-31T09:14:00Z"),
                                    Optional.of(Instant.parse("2026-08-31T09:48:00Z"))),
                            application(),
                            Map.of(),
                            List.of(),
                            List.of(
                                    new FileRecord(
                                            FileDirection.INPUT,
                                            "spectres",
                                            spectra,
                                            1024L,
                                            Instant.parse("2026-08-30T18:00:00Z"),
                                            ABC_HASHES,
                                            ProvenanceStatus.COMPLETED),
                                    new FileRecord(
                                            FileDirection.OUTPUT,
                                            "résultats",
                                            export,
                                            2048L,
                                            Instant.parse("2026-08-31T09:47:00Z"),
                                            EMPTY_HASHES,
                                            ProvenanceStatus.PARTIAL)));

            ProvenanceManifest readBack = roundTrip(manifest);

            assertAll(
                    () -> assertEquals(manifest, readBack),
                    () -> assertEquals(spectra, readBack.files().get(0).path()),
                    () -> assertEquals(export, readBack.files().get(1).path()));
        }

        @Test
        @DisplayName("carrying a quote and a backslash in a path comes back unchanged")
        void aQuotedAndEscapedPathComesBackUnchanged() {
            Path awkward = absolute("data/exports/\"quoted\"\\name.txt");
            ProvenanceManifest manifest = manifestWithOneFile(awkward, 0L);

            assertEquals(awkward, roundTrip(manifest).files().get(0).path());
        }

        @Test
        @DisplayName("at the extremes of the timestamp format comes back with the same instants")
        void theExtremesOfTheTimestampFormatComeBack() {
            // Two manifests rather than one, because a single run spanning both extremes cannot be
            // written at all: the document's durationMillis is a long of milliseconds, and the two
            // extremes are two billion years apart.  That is a property of the format, named here
            // rather than discovered by whoever next writes a fixture with Instant.MIN in it.
            ProvenanceManifest oldest = manifestSpanning(OLDEST, OLDEST.plusMillis(1), OLDEST);
            ProvenanceManifest newest = manifestSpanning(NEWEST.minusMillis(1), NEWEST, NEWEST);

            ProvenanceManifest oldestBack = roundTrip(oldest);
            ProvenanceManifest newestBack = roundTrip(newest);

            assertAll(
                    () -> assertEquals(oldest, oldestBack),
                    () -> assertEquals(newest, newestBack),
                    () -> assertEquals(OLDEST, oldestBack.run().start()),
                    () -> assertEquals(OLDEST, oldestBack.files().get(0).modifiedAt()),
                    () -> assertEquals(Optional.of(NEWEST), newestBack.run().end()),
                    () -> assertEquals(NEWEST, newestBack.files().get(0).modifiedAt()));
        }

        @Test
        @DisplayName("at the extremes of its numbers comes back with the same numbers")
        void theExtremesOfTheNumbersComeBack() {
            ProvenanceManifest biggest =
                    manifestWithOneToolAndExitCode(Integer.MAX_VALUE, Long.MAX_VALUE);
            ProvenanceManifest smallest = manifestWithOneToolAndExitCode(Integer.MIN_VALUE, 0L);

            ProvenanceManifest biggestBack = roundTrip(biggest);
            ProvenanceManifest smallestBack = roundTrip(smallest);

            assertAll(
                    () -> assertEquals(biggest, biggestBack),
                    () -> assertEquals(smallest, smallestBack),
                    () ->
                            assertEquals(
                                    Integer.MAX_VALUE,
                                    biggestBack.tools().get(0).execution().exitCode()),
                    () -> assertEquals(Long.MAX_VALUE, biggestBack.files().get(0).sizeBytes()),
                    () ->
                            assertEquals(
                                    Integer.MIN_VALUE,
                                    smallestBack.tools().get(0).execution().exitCode()),
                    () -> assertEquals(0L, smallestBack.files().get(0).sizeBytes()));
        }

        @Test
        @DisplayName("with one element in every collection comes back with one in each")
        void oneElementCollectionsComeBack() {
            ProvenanceManifest manifest =
                    ProvenanceManifest.current(
                            run(
                                    ProvenanceStatus.COMPLETED,
                                    Instant.parse("2026-08-31T09:14:00Z"),
                                    Optional.of(Instant.parse("2026-08-31T09:48:00Z"))),
                            application(),
                            Map.of("comet.num-threads", "1"),
                            List.of(
                                    new ToolRecord(
                                            "comet",
                                            "2026.02.2",
                                            Optional.empty(),
                                            absolute("opt/comet/comet"),
                                            ABC_HASHES,
                                            true,
                                            Optional.empty(),
                                            Set.of("mzml"),
                                            Optional.empty(),
                                            execution(
                                                    new ToolCommand(
                                                            List.of("/opt/comet/comet"),
                                                            absolute("var/runs/r1"),
                                                            Map.of()),
                                                    Instant.parse("2026-08-31T09:15:00Z"),
                                                    Instant.parse("2026-08-31T09:16:00Z"),
                                                    0),
                                            List.of("only warning"))),
                            List.of(
                                    new FileRecord(
                                            FileDirection.OUTPUT,
                                            "pepxml",
                                            absolute("data/out.pep.xml"),
                                            17L,
                                            Instant.parse("2026-08-31T09:16:00Z"),
                                            EMPTY_HASHES,
                                            ProvenanceStatus.COMPLETED)));

            ProvenanceManifest readBack = roundTrip(manifest);

            assertAll(
                    () -> assertEquals(manifest, readBack),
                    () -> assertEquals(1, readBack.settings().size()),
                    () -> assertEquals(1, readBack.tools().size()),
                    () -> assertEquals(1, readBack.files().size()),
                    () -> assertEquals(Set.of("mzml"), readBack.tools().get(0).capabilities()),
                    () ->
                            assertEquals(
                                    List.of("only warning"), readBack.tools().get(0).warnings()));
        }

        @Test
        @DisplayName("with many elements in every collection comes back with all of them, in order")
        void manyElementCollectionsComeBack() {
            Map<String, String> settings = new LinkedHashMap<>();
            List<String> warnings = new ArrayList<>();
            Set<String> capabilities = new LinkedHashSet<>();
            List<String> argv = new ArrayList<>();
            Map<String, String> environment = new LinkedHashMap<>();
            List<FileRecord> files = new ArrayList<>();
            argv.add("/opt/comet/comet");
            for (int index = 0; index < 40; index++) {
                settings.put("comet.option-" + index, "value-" + index);
                warnings.add("warning number " + index);
                capabilities.add("capability-" + index);
                argv.add("--option-" + index);
                environment.put("COMET_OPTION_" + index, "value-" + index);
                files.add(
                        new FileRecord(
                                index % 2 == 0 ? FileDirection.INPUT : FileDirection.OUTPUT,
                                "role-" + index,
                                absolute("data/file-" + index + ".mzML"),
                                index,
                                Instant.parse("2026-08-30T18:00:00Z").plusMillis(index),
                                index % 2 == 0 ? ABC_HASHES : EMPTY_HASHES,
                                ProvenanceStatus.COMPLETED));
            }
            ProvenanceManifest manifest =
                    ProvenanceManifest.current(
                            run(
                                    ProvenanceStatus.COMPLETED,
                                    Instant.parse("2026-08-31T09:14:00Z"),
                                    Optional.of(Instant.parse("2026-08-31T09:48:00Z"))),
                            application(),
                            settings,
                            List.of(
                                    new ToolRecord(
                                            "comet",
                                            "2026.02.2",
                                            Optional.of("v1"),
                                            absolute("opt/comet/comet"),
                                            ABC_HASHES,
                                            true,
                                            Optional.of("comet.tar.gz"),
                                            capabilities,
                                            Optional.of("search"),
                                            execution(
                                                    new ToolCommand(
                                                            argv,
                                                            absolute("var/runs/r1"),
                                                            environment),
                                                    Instant.parse("2026-08-31T09:15:00Z"),
                                                    Instant.parse("2026-08-31T09:16:00Z"),
                                                    0),
                                            warnings)),
                            files);

            ProvenanceManifest readBack = roundTrip(manifest);

            assertAll(
                    () -> assertEquals(manifest, readBack),
                    () -> assertEquals(40, readBack.settings().size()),
                    () -> assertEquals(40, readBack.files().size()),
                    () -> assertEquals(warnings, readBack.tools().get(0).warnings()),
                    () -> assertEquals(argv, readBack.tools().get(0).execution().command().argv()),
                    () -> assertEquals("role-39", readBack.files().get(39).role()));
        }

        @Test
        @DisplayName(
                "comes back with its settings and capabilities sorted, whatever order it went"
                        + " in with")
        void comesBackSorted() {
            Map<String, String> unsorted = new LinkedHashMap<>();
            unsorted.put("percolator.seed", "9001");
            unsorted.put("comet.num-threads", "8");
            unsorted.put("limelight.project", "17");
            ProvenanceManifest manifest =
                    ProvenanceManifest.current(
                            run(
                                    ProvenanceStatus.COMPLETED,
                                    Instant.parse("2026-08-31T09:14:00Z"),
                                    Optional.of(Instant.parse("2026-08-31T09:48:00Z"))),
                            application(),
                            unsorted,
                            List.of(
                                    new ToolRecord(
                                            "comet",
                                            "2026.02.2",
                                            Optional.empty(),
                                            absolute("opt/comet/comet"),
                                            ABC_HASHES,
                                            true,
                                            Optional.empty(),
                                            new LinkedHashSet<>(List.of("mzxml", "mzml", "fasta")),
                                            Optional.empty(),
                                            execution(
                                                    command(),
                                                    Instant.parse("2026-08-31T09:15:00Z"),
                                                    Instant.parse("2026-08-31T09:16:00Z"),
                                                    0),
                                            List.of())),
                            List.of());

            ProvenanceManifest readBack = roundTrip(manifest);

            assertAll(
                    () -> assertEquals(manifest, readBack),
                    () ->
                            assertEquals(
                                    List.of(
                                            "comet.num-threads",
                                            "limelight.project",
                                            "percolator.seed"),
                                    List.copyOf(readBack.settings().keySet())),
                    () ->
                            assertEquals(
                                    List.of("fasta", "mzml", "mzxml"),
                                    List.copyOf(readBack.tools().get(0).capabilities())));
        }

        /**
         * A manifest whose run spans the given interval and whose one file was modified when named.
         *
         * @param start when the run began
         * @param end when it finished
         * @param modifiedAt the file's modification time
         * @return the manifest
         */
        private ProvenanceManifest manifestSpanning(
                Instant start, Instant end, Instant modifiedAt) {
            return ProvenanceManifest.current(
                    run(ProvenanceStatus.COMPLETED, start, Optional.of(end)),
                    application(),
                    Map.of(),
                    List.of(),
                    List.of(
                            new FileRecord(
                                    FileDirection.INPUT,
                                    "spectra",
                                    absolute("data/spectra.mzML"),
                                    0L,
                                    modifiedAt,
                                    ABC_HASHES,
                                    ProvenanceStatus.COMPLETED)));
        }

        /**
         * A manifest with one file, for the tests that vary only the file.
         *
         * @param path the file's path
         * @param sizeBytes the file's size
         * @return the manifest
         */
        private ProvenanceManifest manifestWithOneFile(Path path, long sizeBytes) {
            return ProvenanceManifest.current(
                    run(
                            ProvenanceStatus.COMPLETED,
                            Instant.parse("2026-08-31T09:14:00Z"),
                            Optional.of(Instant.parse("2026-08-31T09:48:00Z"))),
                    application(),
                    Map.of(),
                    List.of(),
                    List.of(
                            new FileRecord(
                                    FileDirection.OUTPUT,
                                    "spectrum-export",
                                    path,
                                    sizeBytes,
                                    Instant.parse("2026-08-31T09:47:00Z"),
                                    EMPTY_HASHES,
                                    ProvenanceStatus.PARTIAL)));
        }

        /**
         * A manifest with one tool and one file, for the tests that vary only the numbers.
         *
         * @param exitCode the process's exit status
         * @param sizeBytes the file's size
         * @return the manifest
         */
        private ProvenanceManifest manifestWithOneToolAndExitCode(int exitCode, long sizeBytes) {
            return ProvenanceManifest.current(
                    run(
                            ProvenanceStatus.FAILED,
                            Instant.parse("2026-08-31T09:14:00Z"),
                            Optional.of(Instant.parse("2026-08-31T09:48:00Z"))),
                    application(),
                    Map.of(),
                    List.of(
                            new ToolRecord(
                                    "comet",
                                    "2026.02.2",
                                    Optional.empty(),
                                    absolute("opt/comet/comet"),
                                    ABC_HASHES,
                                    false,
                                    Optional.empty(),
                                    Set.of(),
                                    Optional.empty(),
                                    execution(
                                            command(),
                                            Instant.parse("2026-08-31T09:15:00Z"),
                                            Instant.parse("2026-08-31T09:16:00Z"),
                                            exitCode),
                                    List.of())),
                    List.of(
                            new FileRecord(
                                    FileDirection.INPUT,
                                    "spectra",
                                    absolute("data/spectra.mzML"),
                                    sizeBytes,
                                    Instant.parse("2026-08-30T18:00:00Z"),
                                    ABC_HASHES,
                                    ProvenanceStatus.COMPLETED)));
        }
    }

    @Nested
    @DisplayName("Two hundred generated manifests")
    class Property {

        /** The seed, fixed so that a failure can be reproduced exactly from this file alone. */
        private static final long SEED = 20260831L;

        /** How many manifests to generate. */
        private static final int MANIFESTS = 200;

        @Test
        @DisplayName("all come back equal, and all their documents are byte-stable")
        void allComeBackEqual() {
            Random random = new Random(SEED);
            List<ProvenanceManifest> generated = new ArrayList<>();
            for (int index = 0; index < MANIFESTS; index++) {
                generated.add(generateManifest(random, index));
            }

            List<ProvenanceManifest> returned =
                    generated.stream().map(ManifestRoundTripTest::roundTrip).toList();

            assertAll(
                    () -> assertEquals(MANIFESTS, returned.size()),
                    () -> assertEquals(generated, returned));
        }

        @Test
        @DisplayName("really do vary, so the suite is not two hundred copies of one manifest")
        void reallyDoVary() {
            Random random = new Random(SEED);
            List<String> documents = new ArrayList<>();
            for (int index = 0; index < MANIFESTS; index++) {
                documents.add(writer().render(generateManifest(random, index)));
            }

            long distinct = documents.stream().distinct().count();
            long withoutTools = documents.stream().filter(d -> d.contains("\"tools\": []")).count();
            long withTools = documents.stream().filter(d -> !d.contains("\"tools\": []")).count();
            long stillRunning = documents.stream().filter(d -> d.contains("\"end\": null")).count();

            assertAll(
                    () -> assertEquals(MANIFESTS, distinct),
                    () ->
                            assertTrue(
                                    withoutTools > 0,
                                    "no generated manifest had an empty tool" + " list"),
                    () -> assertTrue(withTools > 0, "no generated manifest had a tool"),
                    () -> assertTrue(stillRunning > 0, "no generated manifest was still running"),
                    () ->
                            assertTrue(
                                    stillRunning < MANIFESTS,
                                    "every generated manifest was still running"));
        }

        /**
         * Builds one manifest from the generator's next values.
         *
         * @param random the source of variation
         * @param index the manifest's number, which keeps the identifiers distinct
         * @return the manifest
         */
        private static ProvenanceManifest generateManifest(Random random, int index) {
            ProvenanceStatus status = pick(random, ProvenanceStatus.values());
            Instant start = instant(random);
            Optional<Instant> end =
                    status == ProvenanceStatus.RUNNING
                            ? Optional.empty()
                            : Optional.of(start.plusMillis(random.nextInt(100_000_000)));
            Map<String, String> settings = new LinkedHashMap<>();
            for (int setting = 0; setting < random.nextInt(6); setting++) {
                settings.put("comet.setting-" + setting, "value-" + random.nextInt(1000));
            }
            List<ToolRecord> tools = new ArrayList<>();
            for (int tool = 0; tool < random.nextInt(4); tool++) {
                tools.add(generateTool(random, tool));
            }
            List<FileRecord> files = new ArrayList<>();
            for (int file = 0; file < random.nextInt(5); file++) {
                files.add(generateFile(random, file));
            }
            return ProvenanceManifest.current(
                    new RunRecord(
                            new RunId("run-" + index + "-" + random.nextInt(1_000_000)),
                            "project-" + random.nextInt(100),
                            status,
                            start,
                            end),
                    new ApplicationRecord(
                            "0.1.0-SNAPSHOT",
                            "build-" + random.nextInt(100_000),
                            pick(random, new String[] {"Linux", "Mac OS X", "Windows 11"}),
                            "version-" + random.nextInt(100),
                            pick(random, new String[] {"amd64", "aarch64", "sparc64"}),
                            "25.0.4.1",
                            pick(random, LOCALES),
                            pick(random, LOCALES),
                            pick(random, ZONES)),
                    settings,
                    tools,
                    files);
        }

        /**
         * Builds one tool record.
         *
         * @param random the source of variation
         * @param index the tool's position, which keeps the names distinct
         * @return the tool record
         */
        private static ToolRecord generateTool(Random random, int index) {
            Set<String> capabilities = new LinkedHashSet<>();
            for (int capability = 0; capability < random.nextInt(5); capability++) {
                capabilities.add("capability-" + random.nextInt(50));
            }
            List<String> warnings = new ArrayList<>();
            for (int warning = 0; warning < random.nextInt(4); warning++) {
                warnings.add("warning " + random.nextInt(1000));
            }
            List<String> argv = new ArrayList<>();
            argv.add("/opt/tools/tool-" + index);
            for (int argument = 0; argument < random.nextInt(5); argument++) {
                argv.add("--flag-" + random.nextInt(100));
            }
            Map<String, String> environment = new LinkedHashMap<>();
            for (int variable = 0; variable < random.nextInt(4); variable++) {
                environment.put("COMET_VAR_" + variable, "value-" + random.nextInt(1000));
            }
            Instant start = instant(random);
            return new ToolRecord(
                    "tool-" + index,
                    "version-" + random.nextInt(100),
                    optionalText(random, "release-" + random.nextInt(100)),
                    absolute("opt/tools/tool-" + index),
                    random.nextBoolean() ? ABC_HASHES : EMPTY_HASHES,
                    random.nextBoolean(),
                    optionalText(random, "artefact-" + random.nextInt(100) + ".tar.gz"),
                    capabilities,
                    optionalText(random, "stage-" + random.nextInt(10)),
                    new ExecutionRecord(
                            new ToolCommand(argv, absolute("var/runs/r" + index), environment),
                            start,
                            start.plusMillis(random.nextInt(10_000_000)),
                            random.nextInt(),
                            optionalLog(random, index, "stdout"),
                            optionalLog(random, index, "stderr"),
                            pick(random, ProvenanceStatus.values())),
                    warnings);
        }

        /**
         * Builds one file record.
         *
         * @param random the source of variation
         * @param index the file's position, which keeps the paths distinct
         * @return the file record
         */
        private static FileRecord generateFile(Random random, int index) {
            return new FileRecord(
                    pick(random, FileDirection.values()),
                    "role-" + random.nextInt(20),
                    absolute("data/file-" + index + "-" + random.nextInt(1000) + ".mzML"),
                    random.nextBoolean() ? Long.MAX_VALUE : Math.abs(random.nextLong() % 1_000_000),
                    instant(random),
                    random.nextBoolean() ? ABC_HASHES : EMPTY_HASHES,
                    pick(random, ProvenanceStatus.values()));
        }

        /**
         * An instant on a millisecond boundary, which is the precision the format keeps.
         *
         * @param random the source of variation
         * @return the instant
         */
        private static Instant instant(Random random) {
            return Instant.ofEpochMilli(random.nextLong(0L, 4_000_000_000_000L));
        }

        /**
         * The given text, present or absent.
         *
         * @param random the source of variation
         * @param text the text to hold when it is present
         * @return the optional
         */
        private static Optional<String> optionalText(Random random, String text) {
            return random.nextBoolean() ? Optional.of(text) : Optional.empty();
        }

        /**
         * A captured log, present or absent.
         *
         * @param random the source of variation
         * @param index the tool's position
         * @param stream the stream's name
         * @return the optional
         */
        private static Optional<LogRecord> optionalLog(Random random, int index, String stream) {
            if (!random.nextBoolean()) {
                return Optional.empty();
            }
            return Optional.of(
                    new LogRecord(
                            absolute("var/runs/r" + index + "/" + stream + ".log"),
                            random.nextBoolean() ? ABC_HASHES : EMPTY_HASHES));
        }

        /**
         * One of the given values.
         *
         * @param <T> the value type
         * @param random the source of variation
         * @param values the values to choose between
         * @return one of them
         */
        private static <T> T pick(Random random, T[] values) {
            return values[random.nextInt(values.length)];
        }

        /** Locales whose language tag is their own canonical form, so that they round-trip. */
        private static final Locale[] LOCALES = {
            Locale.ROOT,
            Locale.forLanguageTag("en-US"),
            Locale.forLanguageTag("de-DE"),
            Locale.forLanguageTag("tr-TR"),
            Locale.forLanguageTag("th-TH-u-nu-thai"),
            Locale.forLanguageTag("zh-Hant-TW")
        };

        /** Zones of both kinds the format can carry: a region and a fixed offset. */
        private static final ZoneId[] ZONES = {
            ZoneId.of("UTC"),
            ZoneId.of("Europe/Berlin"),
            ZoneId.of("Pacific/Chatham"),
            ZoneId.of("America/Sao_Paulo"),
            ZoneId.of("+05:30")
        };
    }

    @Nested
    @DisplayName("What the round trip deliberately loses")
    class Lossy {

        @Test
        @DisplayName("sub-millisecond precision, because the format records three digits")
        void losesSubMillisecondPrecision() {
            Instant precise = Instant.parse("2026-08-31T09:14:00.250999999Z");
            ProvenanceManifest manifest =
                    ProvenanceManifest.current(
                            run(
                                    ProvenanceStatus.COMPLETED,
                                    precise,
                                    Optional.of(Instant.parse("2026-08-31T09:48:00Z"))),
                            application(),
                            Map.of(),
                            List.of(),
                            List.of());

            ProvenanceManifest readBack = roundTrip(manifest);

            assertAll(
                    () -> assertNotEquals(manifest, readBack),
                    () ->
                            assertEquals(
                                    Instant.parse("2026-08-31T09:14:00.250Z"),
                                    readBack.run().start()),
                    () ->
                            assertEquals(
                                    precise.truncatedTo(ChronoUnit.MILLIS),
                                    readBack.run().start()));
        }

        @Test
        @DisplayName("a credential, because the writer redacts and redaction is one way")
        void losesACredential() {
            ToolCommand credentialBearing =
                    new ToolCommand(
                            List.of("/opt/comet/comet", "--password", "swordfish-42"),
                            absolute("var/runs/r1"),
                            Map.of("LIMELIGHT_API_KEY", "ll_live_9f8e7d6c5b4a39281706"));
            ProvenanceManifest manifest =
                    ProvenanceManifest.current(
                            run(
                                    ProvenanceStatus.COMPLETED,
                                    Instant.parse("2026-08-31T09:14:00Z"),
                                    Optional.of(Instant.parse("2026-08-31T09:48:00Z"))),
                            application(),
                            Map.of(),
                            List.of(
                                    new ToolRecord(
                                            "comet",
                                            "2026.02.2",
                                            Optional.empty(),
                                            absolute("opt/comet/comet"),
                                            ABC_HASHES,
                                            true,
                                            Optional.empty(),
                                            Set.of(),
                                            Optional.empty(),
                                            execution(
                                                    credentialBearing,
                                                    Instant.parse("2026-08-31T09:15:00Z"),
                                                    Instant.parse("2026-08-31T09:16:00Z"),
                                                    0),
                                            List.of())),
                            List.of());

            String document = writer().render(manifest);
            ProvenanceManifest readBack = ManifestReader.parse(document);
            ToolCommand command = readBack.tools().get(0).execution().command();

            assertAll(
                    () -> assertNotEquals(manifest, readBack),
                    () -> assertFalse(document.contains("swordfish-42")),
                    () -> assertFalse(document.contains("ll_live_9f8e7d6c5b4a39281706")),
                    () ->
                            assertEquals(
                                    List.of("/opt/comet/comet", "--password", "[REDACTED]"),
                                    command.argv()),
                    () ->
                            assertEquals(
                                    Map.of("LIMELIGHT_API_KEY", "[REDACTED]"),
                                    command.environment()));
        }
    }
}
