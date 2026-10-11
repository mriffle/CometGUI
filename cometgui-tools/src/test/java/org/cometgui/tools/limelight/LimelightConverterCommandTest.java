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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.cometgui.tools.limelight.LimelightFixtures.Psm;
import org.cometgui.tools.limelight.LimelightFixtures.SmallRun;
import org.cometgui.tools.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The converter's argument array, exactly. */
class LimelightConverterCommandTest {

    @Test
    @DisplayName(
            "the minimal command: -c -p -d -f -o -q, each value its own argument, q always given")
    void theMinimalCommand(@TempDir Path directory) throws IOException {
        SmallRun run = SmallRun.in(directory);
        LimelightConversionRequest request =
                run.request(directory.resolve("out.xml"), LimelightQValueCutoff.DEFAULT);
        Path staging = directory.resolve("out.xml.partial");

        assertEquals(
                List.of(
                        "-c",
                        request.cometParams().toString(),
                        "-p",
                        run.pout().toString(),
                        "-d",
                        run.pepXmlDirectory().toString(),
                        "-f",
                        request.fasta().toString(),
                        "-o",
                        staging.toString(),
                        "-q",
                        "0.01"),
                LimelightConverterCommand.arguments(request, staging));
    }

    @Test
    @DisplayName(
            "every switch on: decoys, the prefix attached with =, open-mod, verbose, in that order")
    void everySwitch(@TempDir Path directory) throws IOException {
        SmallRun run =
                SmallRun.in(directory, List.of(Psm.target("r_1_2_1"), new Psm("r_2_2_1", "true")));
        PercolatorXmlPsms read = run.read();
        LimelightConversionRequest request =
                new LimelightConversionRequest(
                        directory.resolve("comet.params"),
                        read,
                        run.verified(),
                        directory.resolve("db.fasta"),
                        directory.resolve("out.xml"),
                        new LimelightQValueCutoff(0.05),
                        true,
                        Optional.of("-rev_"),
                        true,
                        true);

        List<String> arguments =
                LimelightConverterCommand.arguments(request, directory.resolve("staged.xml"));

        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        "-q",
                                        "0.05",
                                        "--import-decoys",
                                        "--independent-decoy-prefix=-rev_",
                                        "--open-mod",
                                        "-v"),
                                arguments.subList(10, arguments.size())),
                () -> assertEquals(16, arguments.size()),
                () -> assertEquals(directory.resolve("staged.xml").toString(), arguments.get(9)));
    }

    @Test
    @DisplayName("each switch is independent of the others")
    void switchesAreIndependent(@TempDir Path directory) throws IOException {
        SmallRun run = SmallRun.in(directory);
        PercolatorXmlPsms read = run.read();
        PepXmlSet set = run.verified();
        Path out = directory.resolve("o.xml");

        assertAll(
                () ->
                        assertEquals(
                                List.of("-q", "1", "--independent-decoy-prefix=XYZ_"),
                                tail(
                                        new LimelightConversionRequest(
                                                out,
                                                read,
                                                set,
                                                out,
                                                out,
                                                new LimelightQValueCutoff(1.0),
                                                false,
                                                Optional.of("XYZ_"),
                                                false,
                                                false),
                                        out)),
                () ->
                        assertEquals(
                                List.of("-q", "0", "--open-mod"),
                                tail(
                                        new LimelightConversionRequest(
                                                out,
                                                read,
                                                set,
                                                out,
                                                out,
                                                new LimelightQValueCutoff(0.0),
                                                false,
                                                Optional.empty(),
                                                true,
                                                false),
                                        out)),
                () ->
                        assertEquals(
                                List.of("-q", "0.01", "-v"),
                                tail(
                                        new LimelightConversionRequest(
                                                out,
                                                read,
                                                set,
                                                out,
                                                out,
                                                LimelightQValueCutoff.DEFAULT,
                                                false,
                                                Optional.empty(),
                                                false,
                                                true),
                                        out)));
    }

    private static List<String> tail(LimelightConversionRequest request, Path out) {
        List<String> arguments = LimelightConverterCommand.arguments(request, out);
        return arguments.subList(10, arguments.size());
    }

    @Test
    @DisplayName("the option list is what the builder emits, and nothing else")
    void theOptionList() {
        assertEquals(
                List.of(
                        "-c",
                        "-p",
                        "-d",
                        "-f",
                        "-o",
                        "-q",
                        "--import-decoys",
                        "--independent-decoy-prefix",
                        "--open-mod",
                        "-v"),
                LimelightConverterCommand.OPTIONS);
    }

    @Test
    @DisplayName("a relative output and missing arguments are refused")
    void refusals(@TempDir Path directory) throws IOException {
        LimelightConversionRequest request =
                SmallRun.in(directory)
                        .request(directory.resolve("o.xml"), LimelightQValueCutoff.DEFAULT);

        assertAll(
                () ->
                        assertEquals(
                                "outFile must be an absolute path, but was: o.xml",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        LimelightConverterCommand.arguments(
                                                                request, Path.of("o.xml")))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "request",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        LimelightConverterCommand.arguments(
                                                                Nulls.of(
                                                                        LimelightConversionRequest
                                                                                .class),
                                                                directory))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "outFile",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        LimelightConverterCommand.arguments(
                                                                request, Nulls.of(Path.class)))
                                        .getMessage()));
    }
}
