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

package org.cometgui.provenance;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.secrets.SecretRegistry;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.events.ProvenanceEventLog;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.manifest.ApplicationRecord;
import org.cometgui.provenance.manifest.ExecutionRecord;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.LogRecord;
import org.cometgui.provenance.manifest.ManifestWriter;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.provenance.manifest.RunRecord;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.provenance.report.ProvenanceReportWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * One run directory, all three provenance artefacts, and a grep of the bytes actually on disk.
 *
 * <p>This is phase 04's exit gate item 6 taken whole: "a seeded corpus of secrets (tokens,
 * passwords, bearer headers, credential-bearing URLs) appears nowhere in JSON, RST or logs; the
 * test greps the generated artefacts". Three narrower tests already exist and none of them is this
 * one. {@code SeededSecretCorpusTest} proves the <em>redactor</em> clears every carrier shape, in
 * memory, before any artefact exists. {@code ProvenanceReportWriterTest.Secrets} sweeps {@code
 * provenance.rst} alone, and {@code EventLogSecrecyTest} sweeps the event log alone. Nothing proved
 * that a whole run directory is clean, which is the property a scientist handing a colleague a run
 * directory actually relies on.
 *
 * <p><strong>The files are read back off the disk, twice.</strong> Every artefact is read as a
 * UTF-8 {@link String} and searched, and the same bytes are searched for the secret's US-ASCII byte
 * sequence. A single decode is one assumption -- that the writer and this test agree about the
 * encoding -- and a byte search costs nothing and removes it. Every corpus value is ASCII, so the
 * two searches must agree; a disagreement is itself a finding worth the failure.
 *
 * <p><strong>The directory is enumerated, not named.</strong> The sweep walks the run directory and
 * inspects every regular file it finds, so a {@code .tmp-} file left behind by an interrupted
 * atomic write, or an artefact a later phase adds beside these three, is swept without anyone
 * having to remember to add it here. {@link #theWalkFindsEveryArtefactRatherThanNothing()} asserts
 * the walk found at least the three expected names, because a walk that found nothing would pass an
 * absence sweep perfectly.
 *
 * <p><strong>A sweep proves the absence of a string, not the presence of redaction</strong>, so
 * five guards sit beside it and each one exists because the sweep alone is satisfied by something
 * useless:
 *
 * <ol>
 *   <li>{@link #everyCorpusEntryIsCarriedBeforeAnyWriterSeesIt()} is the most important assertion
 *       in the file. It checks, against the <em>unredacted</em> model, that every corpus entry is
 *       carried by at least one field of it. A corpus entry that nothing ever carried is a sweep
 *       for a string that was never there, and it passes for free.
 *   <li>{@link #everyArtefactKeepsTheRunsOrdinaryContent()} pins ordinary values by exact string. A
 *       writer that emitted an empty file, or a rule set that destroyed every string, passes an
 *       absence sweep and is worthless.
 *   <li>{@link #everyArtefactCarriesTheRedactionMarker()} keeps "nothing was redacted" from looking
 *       like "nothing leaked".
 *   <li>{@link #noShortCarrierIsLongEnoughForASizeConditionedLeakToHide()} pins the carrier
 *       <em>lengths</em>. This is blind spot (2) recorded in {@code SeededSecretCorpusTest}: a
 *       redactor that short-circuited on small inputs shipped past eight green tests because every
 *       carrier in the corpus happened to be long. Do not tidy the short carriers into
 *       realistic-looking longer ones.
 *   <li>{@link #thePrivateKeyIsSweptLineByLineRatherThanAsOneBlob()} pins the PEM key's three
 *       <em>lines</em> in the corpus. This is blind spot (1): {@code contains} is defeated by one
 *       changed character, and a rule that ate only a PEM header would leave 99% of the key on disk
 *       while a whole-body {@code contains} returned false.
 * </ol>
 *
 * <p><strong>{@link #thePatternRulesAloneLeakExactlyTheRegistryOnlyCarriers()} is this gate's
 * demonstration of its own failure.</strong> It builds the same three artefacts with {@link
 * SecretRedactor#patternsOnly()} -- the weakest redactor the API permits, which still runs every
 * pattern rule -- and asserts the exact corpus indices that then survive in each file. It names the
 * specific entries rather than merely asserting that something leaked, so that it cannot quietly
 * stop testing anything the day a pattern rule improves: a rule that started covering one of them
 * fails this test and says which.
 *
 * <p><strong>No failure message ever names a secret.</strong> Only its corpus index, its length,
 * the file it survived into and the offset. The reason is in {@code JsonParseException}'s
 * documentation: a failure message ends up in a build log and a bug report.
 *
 * <p>The corpus values are hand-transcribed from {@code SeededSecretCorpusTest}, character for
 * character; that class is the authority on them. Nothing here is produced by calling the redactor.
 *
 * <p>The path-bearing assertions are disabled on Windows, exactly as {@code ManifestWriterTest} and
 * {@code ProvenanceReportWriterTest} disable theirs and for the same reason: a manifest records the
 * paths a run actually used, and no hand-typed POSIX path can match {@code C:\...}. The sweep
 * itself is not one of them -- it runs everywhere, because a secret is a secret on every platform.
 */
class SeededSecretArtefactSweepTest {

    // -----------------------------------------------------------------------------------------
    // The seeded corpus, hand-transcribed from SeededSecretCorpusTest.  The last three entries
    // are the LINES of the PEM body rather than the joined body; see blind spot (1).
    // -----------------------------------------------------------------------------------------

    /** An AWS secret access key. */
    private static final String AWS_SECRET = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";

    /** A GitHub personal access token. */
    private static final String GITHUB_TOKEN = "ghp_S3cr3tT0k3nExampleValue0123456789ab";

    /** A JSON web token. */
    private static final String JWT =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIn0"
                    + ".dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk";

    /** A password passed on a command line. */
    private static final String PASSWORD = "hunter2-not-a-real-password";

    /** The password inside a credential-bearing URL. */
    private static final String URL_PASSWORD = "Tr0ub4dor-26-3";

    /** A Limelight API key. */
    private static final String LIMELIGHT_KEY = "ll_live_9f8e7d6c5b4a39281706";

    /** A private-key passphrase. */
    private static final String PASSPHRASE = "correct-horse-battery-staple";

    /** The twelve-character secret every SHORT carrier is built from. */
    private static final String SWORDFISH = "swordfish-42";

    /** A vendor token with no published prefix worth pattern-matching. */
    private static final String LIVE_TOKEN = "tok_live_abcdef0123456789";

    /** An AWS access key id. */
    private static final String AWS_ACCESS_KEY_ID = "AKIAIOSFODNN7EXAMPLE";

    /** The base64 body of a synthetic PEM private key, as its three lines. */
    private static final String PEM_LINE_1 =
            "MIIBVAIBADANBgkqhkiG9w0BAQEFAASCAT4wggE6AgEAAkEAnotArealKeyExample";

    /** The second line of the synthetic PEM private key. */
    private static final String PEM_LINE_2 =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ+/";

    /** The third line of the synthetic PEM private key. */
    private static final String PEM_LINE_3 =
            "ThisIsNotARealPrivateKeyItIsAFixtureForCometGUIPhase04Tests012==";

    /** The thirteen strings the sweep looks for, in the order the phase brief lists them. */
    private static final List<String> CORPUS =
            List.of(
                    "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                    "ghp_S3cr3tT0k3nExampleValue0123456789ab",
                    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIn0"
                            + ".dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk",
                    "hunter2-not-a-real-password",
                    "Tr0ub4dor-26-3",
                    "ll_live_9f8e7d6c5b4a39281706",
                    "correct-horse-battery-staple",
                    "swordfish-42",
                    "tok_live_abcdef0123456789",
                    "AKIAIOSFODNN7EXAMPLE",
                    "MIIBVAIBADANBgkqhkiG9w0BAQEFAASCAT4wggE6AgEAAkEAnotArealKeyExample",
                    "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ+/",
                    "ThisIsNotARealPrivateKeyItIsAFixtureForCometGUIPhase04Tests012==");

    // -----------------------------------------------------------------------------------------
    // Digest vectors, hand-transcribed from RFC 1321 and NIST.  No expected value in this file
    // can therefore have come from CometGUI code.
    // -----------------------------------------------------------------------------------------

    /** MD5 and SHA-256 of {@code "abc"}, RFC 1321 and NIST. */
    private static final FileHashes ABC_HASHES =
            new FileHashes(
                    "900150983cd24fb0d6963f7d28e17f72",
                    "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");

    /** MD5 and SHA-256 of the empty file, RFC 1321 and NIST. */
    private static final FileHashes EMPTY_HASHES =
            new FileHashes(
                    "d41d8cd98f00b204e9800998ecf8427e",
                    "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

    // -----------------------------------------------------------------------------------------
    // The run directory.
    // -----------------------------------------------------------------------------------------

    /** What the event log is called inside a run directory. */
    private static final String EVENT_LOG_NAME = "events.log";

    /** The three artefacts a run of this phase's writers produces. */
    private static final List<String> EXPECTED_ARTEFACTS =
            List.of(EVENT_LOG_NAME, "provenance.json", "provenance.rst");

    /**
     * The corpus indices that only the registry can clear, so only they survive {@code
     * patternsOnly()}.
     *
     * <ul>
     *   <li><b>#0</b>, the AWS secret access key, sits after {@code -k} in the argument array and
     *       in a bare event payload value. A single-letter flag never makes the next argument a
     *       credential ({@code AC-PRV-03} requires the array recorded exactly), and 40 characters
     *       of base64 look exactly like a digest, so no pattern may match it.
     *   <li><b>#5</b>, the Limelight key, appears as a bare vendor token in a warning and in an
     *       event message. It has no published prefix worth pattern-matching.
     *   <li><b>#6</b>, the passphrase, appears in a warning with no assignment syntax around it.
     *   <li><b>#7</b>, the twelve-character secret, is a bare value, a tool capability, a file role
     *       and a {@code pw=} assignment -- and {@code pw} is not one of the redactor's secret-name
     *       keywords.
     *   <li><b>#8</b>, the vendor token, is a path segment of an output file and of a captured log.
     * </ul>
     */
    private static final List<Integer> REGISTRY_ONLY_CORPUS_INDICES = List.of(0, 5, 6, 7, 8);

    /** A temporary directory each test builds its own run directories under. */
    @TempDir private Path tempDir;

    // -----------------------------------------------------------------------------------------
    // The anti-vacuity guard that matters most: the artefacts really did carry the secrets.
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("every corpus entry is carried by the model before any writer sees it")
    void everyCorpusEntryIsCarriedBeforeAnyWriterSeesIt() {
        // Without this, a corpus entry that nothing ever carried is a sweep for a string that was
        // never there, and the whole file passes for free.  The carriers are read out of the
        // MODEL OBJECTS, not out of the fixture literals, so deleting a field from the manifest or
        // an event payload really does fail this.
        List<Carrier> carriers = unredactedCarriers();

        List<String> uncarried = new ArrayList<>();
        for (int secret = 0; secret < CORPUS.size(); secret++) {
            int carried = 0;
            for (Carrier carrier : carriers) {
                if (carrier.text().contains(CORPUS.get(secret))) {
                    carried++;
                }
            }
            if (carried == 0) {
                uncarried.add(
                        "corpus secret #"
                                + secret
                                + " (length "
                                + CORPUS.get(secret).length()
                                + ") is carried by nothing in the unredacted model, so sweeping"
                                + " the artefacts for it proves nothing");
            }
        }

        assertAll(
                () -> assertEquals(List.of(), uncarried),
                () ->
                        assertTrue(
                                carriers.size() >= CORPUS.size(),
                                "the model produced only "
                                        + carriers.size()
                                        + " carriers, which is fewer than the corpus has entries;"
                                        + " the carrier walk has stopped seeing the model"));
    }

    // -----------------------------------------------------------------------------------------
    // The sweep.
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("not one of the thirteen seeded secrets is in any file of the run directory")
    void notOneSecretReachesAnyFileInTheRunDirectory() throws IOException {
        Path runDirectory = writeRunDirectory("loaded", loaded());

        Sweep sweep = sweep(runDirectory);

        assertAll(
                () -> assertEquals(List.of(), sweep.leaks()),
                () ->
                        assertTrue(
                                sweep.fileNames().containsAll(EXPECTED_ARTEFACTS),
                                "the walk found " + sweep.fileNames() + ", which is not a run"));
    }

    @Test
    @DisplayName("the walk enumerates the directory and finds all three artefacts, not nothing")
    void theWalkFindsEveryArtefactRatherThanNothing() throws IOException {
        // An absence sweep over an empty list of files is perfectly green, so the floor is
        // asserted rather than assumed.  containsAll, not equals: a later phase adding a fourth
        // artefact to a run directory must be swept, not rejected.
        Path runDirectory = writeRunDirectory("walk", loaded());

        Sweep sweep = sweep(runDirectory);

        assertAll(
                () -> assertTrue(sweep.fileNames().containsAll(EXPECTED_ARTEFACTS)),
                () -> assertTrue(sweep.fileNames().size() >= EXPECTED_ARTEFACTS.size()),
                () -> assertEquals(List.of(), sweep.emptyFiles()));
    }

    @Test
    @DisplayName("every artefact still carries the run's ordinary content")
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "POSIX paths in the pinned values")
    void everyArtefactKeepsTheRunsOrdinaryContent() throws IOException {
        // The half of the gate an absence sweep cannot see.  A writer that emitted an empty file,
        // or a rule set that destroyed every string, sweeps clean and is useless.
        Path runDirectory = writeRunDirectory("ordinary", loaded());

        String json = Files.readString(runDirectory.resolve("provenance.json"), UTF_8);
        String rst = Files.readString(runDirectory.resolve("provenance.rst"), UTF_8);
        String log = Files.readString(runDirectory.resolve(EVENT_LOG_NAME), UTF_8);

        assertAll(
                () -> assertTrue(json.contains("\"name\": \"limelight-upload\""), "json tool name"),
                () -> assertTrue(json.contains("\"/data/HeLa_1ug_rep1.mzML\""), "json input path"),
                () ->
                        assertTrue(
                                json.contains("\"COMET_PARAMS\": \"/data/comet.params\""),
                                "json ordinary environment variable"),
                () -> assertTrue(rst.contains(":Name: ``limelight-upload``"), "rst tool name"),
                () -> assertTrue(rst.contains("``/data/HeLa_1ug_rep1.mzML``"), "rst input path"),
                () ->
                        assertTrue(
                                rst.contains("* ``COMET_PARAMS`` = ``/data/comet.params``"),
                                "rst ordinary environment variable"),
                () -> assertTrue(log.contains("\"type\":\"run.started\""), "log first event"),
                () -> assertTrue(log.contains("/data/HeLa_1ug_rep1.mzML"), "log input path"),
                () ->
                        assertTrue(
                                log.contains("limelight.example.org"),
                                "the upload host was destroyed, which is over-redaction"));
    }

    @Test
    @DisplayName(
            "every artefact carries the redaction marker, so silence is not mistaken for safety")
    void everyArtefactCarriesTheRedactionMarker() throws IOException {
        Path runDirectory = writeRunDirectory("marker", loaded());

        List<String> without = new ArrayList<>();
        for (Path file : regularFilesUnder(runDirectory)) {
            if (!Files.readString(file, UTF_8).contains(SecretRedactor.REDACTION_MARKER)) {
                without.add(fileName(file));
            }
        }

        assertEquals(
                List.of(),
                without,
                "these artefacts contain no redaction marker at all, so nothing was redacted in"
                        + " them and 'nothing leaked' means nothing");
    }

    // -----------------------------------------------------------------------------------------
    // The two blind spots this corpus inherits, asserted rather than trusted.
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("no short carrier is long enough for a size-conditioned leak to hide behind")
    void noShortCarrierIsLongEnoughForASizeConditionedLeakToHide() {
        // Blind spot (2).  A redactor that gave up on small inputs -- "if (text.length() < 32)
        // return text;" as a fast path -- leaks in clear and is invisible to a corpus whose
        // carriers all happen to be long.  Carrier LENGTH is coverage here, not an accident.
        int shortest = Integer.MAX_VALUE;
        List<String> grown = new ArrayList<>();
        for (Map.Entry<String, String> carrier : shortCarriers().entrySet()) {
            int length = carrier.getValue().length();
            shortest = Math.min(shortest, length);
            if (length >= 32) {
                grown.add(
                        "the \""
                                + carrier.getKey()
                                + "\" carrier has grown to "
                                + length
                                + " characters; see blind spot (2) in SeededSecretCorpusTest");
            }
        }
        for (String argument : shortArgv()) {
            if (argument.length() >= 32) {
                grown.add("a short argv element has grown to " + argument.length() + " characters");
            }
        }

        int measured = shortest;
        assertAll(
                () -> assertEquals(List.of(), grown),
                () -> assertEquals(12, measured, "the shortest carrier is no longer twelve"),
                () -> assertEquals(12, SWORDFISH.length()),
                () ->
                        assertEquals(
                                6, shortCarriers().size(), "a rule family lost its short carrier"));
    }

    @Test
    @DisplayName("the private key is swept as its three lines, never as one joined blob")
    void thePrivateKeyIsSweptLineByLineRatherThanAsOneBlob() {
        // Blind spot (1).  contains is defeated by one changed character: when the PEM rule was
        // deleted to prove it could fail, an earlier rule rewrote the "=" padding at the end of
        // the body, 99% of the key was still on disk, and contains(body) was false.  Line by line,
        // a partial mutation still leaves whole units of key material for the sweep to find.
        String joined = PEM_LINE_1 + "\n" + PEM_LINE_2 + "\n" + PEM_LINE_3;

        assertAll(
                () ->
                        assertEquals(
                                List.of(PEM_LINE_1, PEM_LINE_2, PEM_LINE_3),
                                CORPUS.subList(10, 13)),
                () -> assertTrue(!CORPUS.contains(joined), "the joined body is back in the corpus"),
                () ->
                        assertTrue(
                                pemBlock().contains(joined),
                                "the fixture no longer carries the key"),
                () -> assertTrue(PEM_LINE_1.length() >= 32),
                () -> assertTrue(PEM_LINE_2.length() >= 32),
                () -> assertTrue(PEM_LINE_3.length() >= 32));
    }

    // -----------------------------------------------------------------------------------------
    // The demonstration that this gate can fail.
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName(
            "the pattern rules alone leak exactly the five registry-only carriers, in every file")
    void thePatternRulesAloneLeakExactlyTheRegistryOnlyCarriers() throws IOException {
        // A gate that has never been seen to fail has not been shown to work.  patternsOnly() is
        // the weakest redactor the API permits -- it still runs every pattern rule -- so what
        // survives is exactly the set of carriers only a registered literal can clear.  The set is
        // pinned by index, not merely asserted non-empty: a pattern rule that started covering one
        // of these would otherwise make this test quietly stop testing anything.
        Path runDirectory = writeRunDirectory("patterns-only", SecretRedactor.patternsOnly());

        Map<String, List<Integer>> leaked = leakedIndicesByFile(runDirectory);

        Map<String, List<Integer>> expected = new TreeMap<>();
        expected.put(EVENT_LOG_NAME, REGISTRY_ONLY_CORPUS_INDICES);
        expected.put("provenance.json", REGISTRY_ONLY_CORPUS_INDICES);
        expected.put("provenance.rst", REGISTRY_ONLY_CORPUS_INDICES);

        assertEquals(expected, leaked);
    }

    // -----------------------------------------------------------------------------------------
    // The sweep itself.
    // -----------------------------------------------------------------------------------------

    /** The result of sweeping one run directory. */
    private record Sweep(List<String> fileNames, List<String> emptyFiles, List<String> leaks) {}

    /**
     * Reads every regular file under a directory and searches it for every corpus value, twice.
     *
     * @param runDirectory the directory to walk
     * @return the names found, the empty ones among them, and one entry per leak
     * @throws IOException if a file cannot be read
     */
    private static Sweep sweep(Path runDirectory) throws IOException {
        List<String> names = new ArrayList<>();
        List<String> empty = new ArrayList<>();
        List<String> leaks = new ArrayList<>();
        for (Path file : regularFilesUnder(runDirectory)) {
            String name = fileName(file);
            names.add(name);
            byte[] raw = Files.readAllBytes(file);
            if (raw.length == 0) {
                empty.add(name);
            }
            String text = new String(raw, UTF_8);
            for (int secret = 0; secret < CORPUS.size(); secret++) {
                int inText = text.indexOf(CORPUS.get(secret));
                if (inText >= 0) {
                    leaks.add(leak(secret, name, "as UTF-8 text", inText));
                }
                int inBytes = indexOf(raw, CORPUS.get(secret).getBytes(US_ASCII));
                if (inBytes >= 0) {
                    leaks.add(leak(secret, name, "as US-ASCII bytes", inBytes));
                }
            }
        }
        return new Sweep(names, empty, leaks);
    }

    /**
     * Which corpus entries survived into which file, by index.
     *
     * @param runDirectory the directory to walk
     * @return file name to the sorted, distinct corpus indices found in it; files with no leak are
     *     absent
     * @throws IOException if a file cannot be read
     */
    private static Map<String, List<Integer>> leakedIndicesByFile(Path runDirectory)
            throws IOException {
        Map<String, List<Integer>> leaked = new TreeMap<>();
        for (Path file : regularFilesUnder(runDirectory)) {
            String text = Files.readString(file, UTF_8);
            List<Integer> indices = new ArrayList<>();
            for (int secret = 0; secret < CORPUS.size(); secret++) {
                if (text.contains(CORPUS.get(secret))) {
                    indices.add(secret);
                }
            }
            if (!indices.isEmpty()) {
                leaked.put(fileName(file), List.copyOf(indices));
            }
        }
        return leaked;
    }

    /**
     * Every regular file under a directory, sorted, with the stream closed.
     *
     * @param directory the directory to walk
     * @return the files, in path order
     * @throws IOException if the walk fails
     */
    private static List<Path> regularFilesUnder(Path directory) throws IOException {
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile).sorted().toList();
        }
    }

    /**
     * A file's name, with the {@link Path#getFileName()} contract's {@code null} ruled out.
     *
     * <p>{@code getFileName} returns {@code null} for a root path, which a walk of a temporary
     * directory cannot produce; SpotBugs is right that the contract permits it, so it is checked
     * rather than suppressed.
     *
     * @param file the file
     * @return its last path element
     */
    private static String fileName(Path file) {
        return Objects.requireNonNull(file.getFileName(), "file name").toString();
    }

    /**
     * Describes one leak without ever naming the secret.
     *
     * @param secret the corpus index
     * @param fileName the artefact it survived into
     * @param how which of the two searches found it
     * @param at the offset it was found at
     * @return the failure text
     */
    private static String leak(int secret, String fileName, String how, int at) {
        return "corpus secret #"
                + secret
                + " (length "
                + CORPUS.get(secret).length()
                + ") survived into "
                + fileName
                + " "
                + how
                + ", at offset "
                + at;
    }

    /**
     * The first offset at which one byte sequence occurs in another.
     *
     * @param haystack the bytes to search
     * @param needle the bytes to look for, never empty
     * @return the offset, or {@code -1}
     */
    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int start = 0; start + needle.length <= haystack.length; start++) {
            for (int offset = 0; offset < needle.length; offset++) {
                if (haystack[start + offset] != needle[offset]) {
                    continue outer;
                }
            }
            return start;
        }
        return -1;
    }

    // -----------------------------------------------------------------------------------------
    // Writing one run directory.
    // -----------------------------------------------------------------------------------------

    /**
     * Writes all three artefacts of one run into a fresh directory.
     *
     * @param name the directory's name under the temporary root
     * @param redactor the rule set all three writers are built with
     * @return the run directory
     * @throws IOException if an artefact cannot be written
     */
    private Path writeRunDirectory(String name, SecretRedactor redactor) throws IOException {
        Path runDirectory = Files.createDirectories(tempDir.resolve(name));
        ProvenanceManifest manifest = manifestCarryingTheCorpus();
        ManifestWriter.redactingWith(redactor).writeInto(runDirectory, manifest);
        ProvenanceReportWriter.redactingWith(redactor).writeInto(runDirectory, manifest);
        try (ProvenanceEventLog log =
                ProvenanceEventLog.openAppend(
                        runDirectory.resolve(EVENT_LOG_NAME),
                        redactor,
                        Clock.fixed(Instant.parse("2026-08-31T09:15:00Z"), ZoneOffset.UTC))) {
            for (LoggedEvent event : eventsCarryingTheCorpus()) {
                log.append(event.type(), event.payload());
            }
        }
        return runDirectory;
    }

    /**
     * How a production run is configured: every credential it holds is registered.
     *
     * @return the loaded rule set
     */
    private static SecretRedactor loaded() {
        return SecretRedactor.with(SecretRegistry.copyOf(CORPUS));
    }

    // -----------------------------------------------------------------------------------------
    // The unredacted model.
    // -----------------------------------------------------------------------------------------

    /** One field of the unredacted model, with a name for the failure message. */
    private record Carrier(String name, String text) {}

    /** One event as it is handed to the log, before the log redacts it. */
    private record LoggedEvent(ProvenanceEventType type, Map<String, String> payload) {}

    /**
     * Every string the unredacted model carries, read out of the model objects themselves.
     *
     * <p>Read out of the records rather than out of the fixture literals on purpose: a field
     * removed from the manifest, or a payload dropped from an event, must fail {@link
     * #everyCorpusEntryIsCarriedBeforeAnyWriterSeesIt()} rather than silently leave the sweep
     * looking for a string that is no longer anywhere.
     *
     * @return every carrier, named
     */
    private static List<Carrier> unredactedCarriers() {
        ProvenanceManifest manifest = manifestCarryingTheCorpus();
        List<Carrier> carriers = new ArrayList<>();
        carriers.add(new Carrier("run.projectId", manifest.run().projectId()));
        carriers.add(new Carrier("run.runId", manifest.run().runId().value()));
        carriers.add(
                new Carrier(
                        "application.buildIdentifier", manifest.application().buildIdentifier()));
        carriers.add(
                new Carrier(
                        "application.cometGuiVersion", manifest.application().cometGuiVersion()));
        for (Map.Entry<String, String> setting : manifest.settings().entrySet()) {
            carriers.add(new Carrier("setting " + setting.getKey(), setting.getValue()));
        }
        for (ToolRecord tool : manifest.tools()) {
            carriers.add(new Carrier("tool.name", tool.name()));
            carriers.add(new Carrier("tool.version", tool.version()));
            tool.releaseTag().ifPresent(tag -> carriers.add(new Carrier("tool.releaseTag", tag)));
            carriers.add(new Carrier("tool.executablePath", tool.executablePath().toString()));
            tool.artefactIdentity()
                    .ifPresent(
                            identity ->
                                    carriers.add(new Carrier("tool.artefactIdentity", identity)));
            for (String capability : tool.capabilities()) {
                carriers.add(new Carrier("tool.capability", capability));
            }
            for (String warning : tool.warnings()) {
                carriers.add(new Carrier("tool.warning", warning));
            }
            ToolCommand command = tool.execution().command();
            for (int index = 0; index < command.argv().size(); index++) {
                carriers.add(new Carrier("argv[" + index + "]", command.argv().get(index)));
            }
            for (Map.Entry<String, String> variable : command.environment().entrySet()) {
                carriers.add(new Carrier("environment " + variable.getKey(), variable.getValue()));
            }
            carriers.add(new Carrier("workingDirectory", command.workingDirectory().toString()));
            tool.execution()
                    .stdout()
                    .ifPresent(
                            log -> carriers.add(new Carrier("stdout log", log.path().toString())));
            tool.execution()
                    .stderr()
                    .ifPresent(
                            log -> carriers.add(new Carrier("stderr log", log.path().toString())));
        }
        for (FileRecord file : manifest.files()) {
            carriers.add(new Carrier("file.role", file.role()));
            carriers.add(new Carrier("file.path", file.path().toString()));
        }
        for (LoggedEvent event : eventsCarryingTheCorpus()) {
            for (Map.Entry<String, String> entry : event.payload().entrySet()) {
                carriers.add(
                        new Carrier(
                                "event " + event.type().wireName() + " " + entry.getKey(),
                                entry.getValue()));
            }
        }
        return carriers;
    }

    // -----------------------------------------------------------------------------------------
    // Fixtures.
    // -----------------------------------------------------------------------------------------

    /**
     * An absolute path built from a POSIX path without a leading separator.
     *
     * <p>SpotBugs reports {@code DMI_HARDCODED_ABSOLUTE_FILENAME} for a string constant that looks
     * like an absolute path, so the separator is added here, exactly as the sibling writer tests
     * do.
     *
     * @param posixPath the path without its leading separator
     * @return the absolute path
     */
    private static Path absolute(String posixPath) {
        return Path.of("/" + posixPath);
    }

    /** The captured process environment, four credentials and one ordinary variable. */
    private static Map<String, String> environment() {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("AWS_SECRET_ACCESS_KEY", AWS_SECRET);
        environment.put("GITHUB_TOKEN", GITHUB_TOKEN);
        environment.put("LIMELIGHT_API_KEY", LIMELIGHT_KEY);
        environment.put("PERCOLATOR_PASSWORD", PASSPHRASE);
        environment.put("COMET_PARAMS", "/data/comet.params");
        return environment;
    }

    /**
     * The argument array, which carries credentials positionally as well as textually.
     *
     * @return the array, executable first
     */
    private static List<String> argv() {
        List<String> argv = new ArrayList<>();
        argv.add("/opt/limelight/bin/upload");
        argv.add("--password");
        argv.add(PASSWORD);
        argv.add("--password=" + PASSWORD);
        argv.add("--api-key");
        argv.add(AWS_ACCESS_KEY_ID);
        argv.addAll(shortArgv());
        argv.add("-k");
        argv.add(AWS_SECRET);
        argv.add("--input");
        argv.add("/data/HeLa_1ug_rep1.mzML");
        return argv;
    }

    /**
     * The smallest positional credential an argument array can carry: a flag and twelve characters.
     *
     * @return the two elements
     */
    private static List<String> shortArgv() {
        return List.of("--token", SWORDFISH);
    }

    /**
     * One deliberately small carrier per rule family, name to raw text.
     *
     * <p>The longest is 23 characters and the shortest is 12. {@code pw=} is here on purpose
     * alongside {@code auth=}: {@code pw} is not one of the redactor's secret-name keywords, so
     * only the registry clears it, and that boundary is better recorded as a carrier than left as a
     * surprise.
     *
     * @return each short carrier's name and its text
     */
    private static Map<String, String> shortCarriers() {
        Map<String, String> carriers = new LinkedHashMap<>();
        carriers.put("short assignment", "auth=" + SWORDFISH);
        carriers.put("short bearer", "Bearer " + SWORDFISH);
        carriers.put("short credential URL", "ftp://u:" + SWORDFISH + "@h/");
        carriers.put("short token shape", AWS_ACCESS_KEY_ID);
        carriers.put("short unnamed assignment", "pw=" + SWORDFISH);
        carriers.put("short bare value", SWORDFISH);
        return carriers;
    }

    /**
     * The upload endpoint, with its password still in it.
     *
     * @return the credential-bearing URL
     */
    private static String credentialUrl() {
        return "https://limelight-user:" + URL_PASSWORD + "@limelight.example.org/api/upload";
    }

    /**
     * A whole PEM private key, delimiters included, as a tool would have printed it.
     *
     * @return the block
     */
    private static String pemBlock() {
        return "-----BEGIN RSA PRIVATE KEY-----\n"
                + PEM_LINE_1
                + "\n"
                + PEM_LINE_2
                + "\n"
                + PEM_LINE_3
                + "\n-----END RSA PRIVATE KEY-----";
    }

    /**
     * A manifest whose settings, argument array, environment, capabilities, warnings and paths all
     * carry seeded secrets, long and short.
     *
     * @return the manifest
     */
    private static ProvenanceManifest manifestCarryingTheCorpus() {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("limelight.upload-url", credentialUrl());
        settings.put("limelight.auth-header", "Authorization: Bearer " + JWT);
        settings.put("limelight.response", "response body {\"token\":\"" + LIVE_TOKEN + "\"}");
        settings.put("limelight.key-material", pemBlock());
        settings.put("report.short-assignment", "auth=" + SWORDFISH);
        settings.put("report.short-bearer", "Bearer " + SWORDFISH);
        settings.put("report.short-url", "ftp://u:" + SWORDFISH + "@h/");
        settings.put("report.short-token", AWS_ACCESS_KEY_ID);
        settings.put("report.short-unnamed", "pw=" + SWORDFISH);
        settings.put("report.short-bare", SWORDFISH);

        ToolRecord upload =
                new ToolRecord(
                        "limelight-upload",
                        "1.0.0",
                        Optional.of(GITHUB_TOKEN),
                        absolute("opt/limelight/bin/upload"),
                        ABC_HASHES,
                        false,
                        Optional.of(AWS_ACCESS_KEY_ID),
                        Set.of(SWORDFISH),
                        Optional.of("upload"),
                        new ExecutionRecord(
                                new ToolCommand(argv(), absolute("data"), environment()),
                                Instant.parse("2026-08-31T09:15:00Z"),
                                Instant.parse("2026-08-31T09:15:01Z"),
                                0,
                                Optional.of(
                                        new LogRecord(
                                                absolute(
                                                        "data/"
                                                                + LIVE_TOKEN
                                                                + "/upload.stdout.log"),
                                                ABC_HASHES)),
                                Optional.empty(),
                                ProvenanceStatus.COMPLETED),
                        List.of(
                                "github token " + GITHUB_TOKEN + " accepted",
                                "connecting with password: " + SWORDFISH,
                                "limelight key " + LIMELIGHT_KEY + " accepted",
                                "percolator passphrase " + PASSPHRASE));

        return new ProvenanceManifest(
                1,
                new RunRecord(
                        new RunId("run-20260831-091500"),
                        "project-alpha",
                        ProvenanceStatus.COMPLETED,
                        Instant.parse("2026-08-31T09:14:00Z"),
                        Optional.of(Instant.parse("2026-08-31T09:48:00Z"))),
                new ApplicationRecord(
                        "0.1.0-SNAPSHOT",
                        AWS_ACCESS_KEY_ID,
                        "Linux",
                        "6.8.0-137-generic",
                        "amd64",
                        "25.0.4.1",
                        Locale.forLanguageTag("en-US"),
                        Locale.forLanguageTag("en-US"),
                        ZoneId.of("UTC")),
                settings,
                List.of(upload),
                List.of(
                        new FileRecord(
                                FileDirection.INPUT,
                                "spectra",
                                absolute("data/HeLa_1ug_rep1.mzML"),
                                1024L,
                                Instant.parse("2026-08-30T18:00:00Z"),
                                ABC_HASHES,
                                ProvenanceStatus.COMPLETED),
                        new FileRecord(
                                FileDirection.OUTPUT,
                                SWORDFISH,
                                absolute("data/" + LIVE_TOKEN + "/results.txt"),
                                2048L,
                                Instant.parse("2026-08-31T09:48:00Z"),
                                EMPTY_HASHES,
                                ProvenanceStatus.COMPLETED)));
    }

    /**
     * The run's events, whose payloads carry the corpus in every shape a payload can hold it.
     *
     * @return the events, in the order they are appended
     */
    private static List<LoggedEvent> eventsCarryingTheCorpus() {
        List<LoggedEvent> events = new ArrayList<>();
        events.add(
                new LoggedEvent(
                        ProvenanceEventType.RUN_STARTED, Map.of("run.id", "run-20260831-091500")));

        Map<String, String> invocation = new LinkedHashMap<>();
        invocation.put("argv.0", "/opt/limelight/bin/upload");
        invocation.put("argv.1", "--password=" + PASSWORD);
        invocation.put("argv.2", AWS_SECRET);
        invocation.put("argv.3", "--input");
        invocation.put("argv.4", "/data/HeLa_1ug_rep1.mzML");
        invocation.put("endpoint", credentialUrl());
        invocation.put("upload.api.key", LIMELIGHT_KEY);
        events.add(new LoggedEvent(ProvenanceEventType.TOOL_INVOKED, invocation));

        events.add(warning("GET /api/upload -> Authorization: Bearer " + JWT));
        events.add(warning("response body {\"token\":\"" + LIVE_TOKEN + "\"}"));
        events.add(warning("github token " + GITHUB_TOKEN + " accepted"));
        events.add(warning("percolator passphrase " + PASSPHRASE));
        events.add(warning("limelight key " + LIMELIGHT_KEY + " accepted"));
        events.add(warning("upload: key material follows\n" + pemBlock() + "\nupload: done"));
        for (String carrier : shortCarriers().values()) {
            events.add(warning(carrier));
        }

        Map<String, String> hashed = new LinkedHashMap<>();
        hashed.put("path", "/data/HeLa_1ug_rep1.mzML");
        hashed.put("output.path", "/data/" + LIVE_TOKEN + "/results.txt");
        hashed.put("aws.key.id", AWS_ACCESS_KEY_ID);
        events.add(new LoggedEvent(ProvenanceEventType.FILE_HASHED, hashed));

        events.add(new LoggedEvent(ProvenanceEventType.STAGE_FINISHED, Map.of("stage", "upload")));
        events.add(
                new LoggedEvent(
                        ProvenanceEventType.RUN_FINISHED,
                        Map.of(ProvenanceEvent.STATUS_KEY, "completed")));
        return events;
    }

    /**
     * One warning event carrying a message.
     *
     * @param message the message, unredacted
     * @return the event
     */
    private static LoggedEvent warning(String message) {
        return new LoggedEvent(ProvenanceEventType.WARNING_RAISED, Map.of("message", message));
    }
}
