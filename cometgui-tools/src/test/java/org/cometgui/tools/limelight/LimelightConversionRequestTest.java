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

package org.cometgui.tools.limelight;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.cometgui.tools.api.JavaRuntime;
import org.cometgui.tools.limelight.LimelightFixtures.Psm;
import org.cometgui.tools.limelight.LimelightFixtures.SmallRun;
import org.cometgui.tools.testing.Nulls;
import org.cometgui.tools.testing.ScriptedRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

/**
 * The typed request: the decoy rule decided before any process exists (gate item 8, model half),
 * and every path absolute.
 */
class LimelightConversionRequestTest {

    private static LimelightConversionRequest request(
            SmallRun run, boolean importDecoys, Optional<String> prefix) throws IOException {
        PercolatorXmlPsms read = run.read();
        return new LimelightConversionRequest(
                run.pepXmlDirectory().resolveSibling("comet.params"),
                read,
                PepXmlSet.verify(
                        read, run.pepXmlDirectory(), run.recorded(), LimelightFixtures.hashes()),
                run.pepXmlDirectory().resolveSibling("db.fasta"),
                run.pepXmlDirectory().resolveSibling("out.limelight.xml"),
                LimelightQValueCutoff.DEFAULT,
                importDecoys,
                prefix,
                true,
                true);
    }

    @Test
    @DisplayName("a request keeps what it was given")
    void aRequest(@TempDir Path directory) throws IOException {
        SmallRun run = SmallRun.in(directory);

        LimelightConversionRequest request = request(run, false, Optional.of("DECOY_"));

        assertAll(
                () -> assertEquals(run.pout(), request.percolatorXml().file()),
                () -> assertEquals(run.pepXmlDirectory(), request.pepXmls().directory()),
                () -> assertEquals(Optional.of("DECOY_"), request.independentDecoyPrefix()),
                () -> assertEquals(LimelightQValueCutoff.DEFAULT, request.qCutoff()));
    }

    @Test
    @DisplayName(
            "GATE 8 (model): decoy import against a run with no decoy PSMs cannot be requested, and"
                    + " no process starts")
    void decoyImportWithoutDecoysIsRefusedBeforeAnyProcess(@TempDir Path directory)
            throws IOException {
        SmallRun run = SmallRun.in(directory);
        ScriptedRunner nothingScripted = new ScriptedRunner();
        LimelightConverter converter =
                new LimelightConverter(
                        nothingScripted,
                        Duration.ofSeconds(5),
                        JavaRuntime.ofThisApplication(),
                        directory.resolve("converter.jar"));

        IllegalArgumentException refused =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> converter.convert(request(run, true, Optional.empty())));

        assertAll(
                () ->
                        assertEquals(
                                "Limelight decoy import is not available for this run: its"
                                        + " Percolator output "
                                        + run.pout()
                                        + " holds no decoy PSMs, because Percolator was not run"
                                        + " with -Z (decoy XML output). Asking the converter to"
                                        + " import decoys from it would silently import none."
                                        + " Comet's internal decoys cannot be imported either: the"
                                        + " converter looks every protein up in the FASTA, and"
                                        + " Comet's generated decoys are not FASTA entries.",
                                refused.getMessage()),
                () -> assertEquals(0, nothingScripted.played()),
                () -> assertEquals(List.of(), nothingScripted.commands()));
    }

    @Test
    @DisplayName("the same rule answers a control before anything is requested")
    void theRuleForAControl(@TempDir Path directory) throws IOException {
        SmallRun targets = SmallRun.in(directory.resolve("t"));
        SmallRun withDecoys =
                SmallRun.in(
                        directory.resolve("d"),
                        List.of(Psm.target("r_1_2_1"), new Psm("r_2_2_1", "true")));

        assertAll(
                () ->
                        assertTrue(
                                LimelightConversionRequest.decoyImportUnavailable(targets.read())
                                        .orElseThrow()
                                        .startsWith(
                                                "Limelight decoy import is not available for this"
                                                        + " run")),
                () ->
                        assertEquals(
                                Optional.empty(),
                                LimelightConversionRequest.decoyImportUnavailable(
                                        withDecoys.read())),
                () ->
                        assertEquals(
                                true, request(withDecoys, true, Optional.empty()).importDecoys()),
                () ->
                        assertEquals(
                                "percolatorXml",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        LimelightConversionRequest
                                                                .decoyImportUnavailable(
                                                                        Nulls.of(
                                                                                PercolatorXmlPsms
                                                                                        .class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName(
            "a run WITH decoy PSMs cannot be converted without decoy import: the converter fails"
                    + " on it")
    void decoysWithoutImportAreRefused(@TempDir Path directory) throws IOException {
        SmallRun withDecoys =
                SmallRun.in(
                        directory,
                        List.of(
                                Psm.target("r_1_2_1"),
                                new Psm("r_2_2_1", "true"),
                                new Psm("r_3_2_1", "true")));

        assertEquals(
                "the Percolator XML "
                        + withDecoys.pout()
                        + " holds 2 decoy PSMs (Percolator ran with -Z), and the converter fails on"
                        + " such a file unless decoy import is selected",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> request(withDecoys, false, Optional.empty()))
                        .getMessage());
    }

    @Test
    @DisplayName("the pepXML set must come from this request's own Percolator XML")
    void thePepXmlSetMustMatch(@TempDir Path directory) throws IOException {
        SmallRun one = SmallRun.in(directory.resolve("one"));
        SmallRun two = SmallRun.in(directory.resolve("two"));

        assertEquals(
                "the pepXML set was derived from "
                        + two.pout()
                        + ", not from this conversion's Percolator XML "
                        + one.pout(),
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new LimelightConversionRequest(
                                                directory.resolve("comet.params"),
                                                one.read(),
                                                two.verified(),
                                                directory.resolve("db.fasta"),
                                                directory.resolve("out.xml"),
                                                LimelightQValueCutoff.DEFAULT,
                                                false,
                                                Optional.empty(),
                                                false,
                                                false))
                        .getMessage());
    }

    @Test
    @DisplayName("a blank prefix, or one holding whitespace, is refused")
    void prefixes(@TempDir Path directory) throws IOException {
        SmallRun run = SmallRun.in(directory);
        for (String prefix : List.of("", " ", "DE COY", "DECOY\t", " X")) {
            assertEquals(
                    "an independent decoy prefix is the start of a protein name and cannot be"
                            + " blank or hold whitespace, but was \""
                            + prefix
                            + "\"",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> request(run, false, Optional.of(prefix)))
                            .getMessage(),
                    prefix);
        }
        assertEquals(
                Optional.of("-rev_"),
                request(run, false, Optional.of("-rev_")).independentDecoyPrefix());
    }

    @Test
    @DisplayName("every path must be absolute and every component given")
    void pathsAndNulls(@TempDir Path directory) throws IOException {
        SmallRun run = SmallRun.in(directory);
        PercolatorXmlPsms read = run.read();
        PepXmlSet set = run.verified();
        Path abs = directory.resolve("x");
        Path rel = Path.of("relative.xml");
        LimelightQValueCutoff q = LimelightQValueCutoff.DEFAULT;
        Optional<String> none = Optional.empty();

        assertAll(
                () ->
                        relative(
                                "cometParams",
                                () ->
                                        new LimelightConversionRequest(
                                                rel, read, set, abs, abs, q, false, none, false,
                                                false)),
                () ->
                        relative(
                                "fasta",
                                () ->
                                        new LimelightConversionRequest(
                                                abs, read, set, rel, abs, q, false, none, false,
                                                false)),
                () ->
                        relative(
                                "output",
                                () ->
                                        new LimelightConversionRequest(
                                                abs, read, set, abs, rel, q, false, none, false,
                                                false)),
                () ->
                        relative(
                                "percolatorXml",
                                () ->
                                        new LimelightConversionRequest(
                                                abs,
                                                PercolatorXmlPsms.read(relativeCopy(run)),
                                                set,
                                                abs,
                                                abs,
                                                q,
                                                false,
                                                none,
                                                false,
                                                false)),
                () ->
                        relative(
                                "pepXmls",
                                () ->
                                        new LimelightConversionRequest(
                                                abs,
                                                read,
                                                new PepXmlSet(run.pout(), rel, set.files()),
                                                abs,
                                                abs,
                                                q,
                                                false,
                                                none,
                                                false,
                                                false)),
                () ->
                        npe(
                                "cometParams",
                                () ->
                                        new LimelightConversionRequest(
                                                Nulls.of(Path.class),
                                                read,
                                                set,
                                                abs,
                                                abs,
                                                q,
                                                false,
                                                none,
                                                false,
                                                false)),
                () ->
                        npe(
                                "percolatorXml",
                                () ->
                                        new LimelightConversionRequest(
                                                abs,
                                                Nulls.of(PercolatorXmlPsms.class),
                                                set,
                                                abs,
                                                abs,
                                                q,
                                                false,
                                                none,
                                                false,
                                                false)),
                () ->
                        npe(
                                "pepXmls",
                                () ->
                                        new LimelightConversionRequest(
                                                abs,
                                                read,
                                                Nulls.of(PepXmlSet.class),
                                                abs,
                                                abs,
                                                q,
                                                false,
                                                none,
                                                false,
                                                false)),
                () ->
                        npe(
                                "fasta",
                                () ->
                                        new LimelightConversionRequest(
                                                abs,
                                                read,
                                                set,
                                                Nulls.of(Path.class),
                                                abs,
                                                q,
                                                false,
                                                none,
                                                false,
                                                false)),
                () ->
                        npe(
                                "output",
                                () ->
                                        new LimelightConversionRequest(
                                                abs,
                                                read,
                                                set,
                                                abs,
                                                Nulls.of(Path.class),
                                                q,
                                                false,
                                                none,
                                                false,
                                                false)),
                () ->
                        npe(
                                "qCutoff",
                                () ->
                                        new LimelightConversionRequest(
                                                abs,
                                                read,
                                                set,
                                                abs,
                                                abs,
                                                Nulls.of(LimelightQValueCutoff.class),
                                                false,
                                                none,
                                                false,
                                                false)),
                () ->
                        npe(
                                "independentDecoyPrefix",
                                () ->
                                        new LimelightConversionRequest(
                                                abs,
                                                read,
                                                set,
                                                abs,
                                                abs,
                                                q,
                                                false,
                                                Nulls.of(Optional.class),
                                                false,
                                                false)));
    }

    /** The run's Percolator XML, read through a relative path from the working directory. */
    private static Path relativeCopy(SmallRun run) {
        return Path.of("").toAbsolutePath().relativize(run.pout());
    }

    private static void relative(String field, Executable call) {
        String message = assertThrows(IllegalArgumentException.class, call).getMessage();
        assertTrue(
                message.startsWith(
                        field
                                + " must be an absolute path, because the converter runs in its"
                                + " own directory and would resolve a relative one there, but"
                                + " was: "),
                message);
    }

    private static void npe(String field, Executable call) {
        assertEquals(field, assertThrows(NullPointerException.class, call).getMessage());
    }
}
