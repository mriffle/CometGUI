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

package org.cometgui.workflow.steps;

import static org.cometgui.workflow.testing.TestPaths.absolute;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.results.filtering.store.TableKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Where a run's raw Percolator results are: every path typed out by hand. */
class RunResultFilesTest {

    private static final RunLayout RUN =
            new RunLayout(absolute("p/runs/20261009T101500Z-run-0001"));

    @Test
    @DisplayName("the four tables and the weights are under outputs/percolator, by their own names")
    void theFiles() {
        assertAll(
                () ->
                        assertEquals(
                                absolute("p/runs/20261009T101500Z-run-0001/outputs/percolator"),
                                RunResultFiles.percolatorOutputDirectory(RUN)),
                () ->
                        assertEquals(
                                absolute(
                                        "p/runs/20261009T101500Z-run-0001/outputs/percolator/"
                                                + "psms.tsv"),
                                RunResultFiles.table(RUN, TableKind.TARGET_PSMS)),
                () ->
                        assertEquals(
                                absolute(
                                        "p/runs/20261009T101500Z-run-0001/outputs/percolator/"
                                                + "peptides.tsv"),
                                RunResultFiles.table(RUN, TableKind.TARGET_PEPTIDES)),
                () ->
                        assertEquals(
                                absolute(
                                        "p/runs/20261009T101500Z-run-0001/outputs/percolator/"
                                                + "decoy-psms.tsv"),
                                RunResultFiles.table(RUN, TableKind.DECOY_PSMS)),
                () ->
                        assertEquals(
                                absolute(
                                        "p/runs/20261009T101500Z-run-0001/outputs/percolator/"
                                                + "decoy-peptides.tsv"),
                                RunResultFiles.table(RUN, TableKind.DECOY_PEPTIDES)),
                () ->
                        assertEquals(
                                absolute(
                                        "p/runs/20261009T101500Z-run-0001/outputs/percolator/"
                                                + "weights.txt"),
                                RunResultFiles.weights(RUN)));
    }

    @Test
    @DisplayName(
            "phase 12: the pout XML is outputs/percolator/pout.xml, and the recorded roles are the"
                    + " ones run-comet and run-percolator declare")
    void thePoutXmlAndTheRoles() {
        assertAll(
                () ->
                        assertEquals(
                                absolute(
                                        "p/runs/20261009T101500Z-run-0001/outputs/percolator/"
                                                + "pout.xml"),
                                RunResultFiles.percolatorXml(RUN)),
                () -> assertEquals("pepxml", RunResultFiles.PEP_XML_ROLE),
                () -> assertEquals("percolator-pout-xml", RunResultFiles.POUT_XML_ROLE),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> RunResultFiles.percolatorXml(null)),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> RunResultFiles.makeReadOnly(null)));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    @DisplayName("makeReadOnly removes every write permission and keeps the rest")
    void makeReadOnly(@TempDir Path directory) throws IOException {
        Path file = Files.writeString(directory.resolve("limelight-1.xml"), "<x/>");
        Files.setPosixFilePermissions(
                file,
                EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.GROUP_READ,
                        PosixFilePermission.GROUP_WRITE,
                        PosixFilePermission.OTHERS_WRITE));

        RunResultFiles.makeReadOnly(file);

        Set<PosixFilePermission> after = Files.getPosixFilePermissions(file);
        assertEquals(
                EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.GROUP_READ), after);
    }

    @Test
    @DisplayName("a missing run or table is refused")
    void nulls() {
        assertAll(
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> RunResultFiles.percolatorOutputDirectory(null)),
                () ->
                        assertThrows(
                                NullPointerException.class, () -> RunResultFiles.table(RUN, null)),
                () -> assertThrows(NullPointerException.class, () -> RunResultFiles.weights(null)));
    }
}
