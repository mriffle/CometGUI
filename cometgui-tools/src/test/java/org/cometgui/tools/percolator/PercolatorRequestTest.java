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

package org.cometgui.tools.percolator;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.tools.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The request's and the result's own rules: what may go into a command at all. */
class PercolatorRequestTest {

    private static final Path EXE = absolute("opt/percolator");
    private static final Path PIN = absolute("run/merged.pin");
    private static final Path OUT = absolute("run/outputs/percolator");
    private static final Set<ToolCapability> TABLES =
            EnumSet.of(ToolCapability.PSM_TSV_OUTPUT, ToolCapability.PEPTIDE_TSV_OUTPUT);

    /* No constant here is an absolute pathname; see TestPaths in tools.comet for why. */
    private static Path absolute(String withoutLeadingSlash) {
        return Path.of("/" + withoutLeadingSlash);
    }

    private static PercolatorRequest withValues(Map<PercolatorOption, String> values) {
        return new PercolatorRequest(EXE, PIN, OUT, TABLES, false, values);
    }

    @Test
    @DisplayName("the valued options are exactly the five settings' options")
    void valuedOptions() {
        assertEquals(
                EnumSet.of(
                        PercolatorOption.SEED,
                        PercolatorOption.NUM_THREADS,
                        PercolatorOption.TEST_FDR,
                        PercolatorOption.TRAIN_FDR,
                        PercolatorOption.MAX_ITERATIONS),
                PercolatorRequest.VALUED_OPTIONS);
    }

    @Test
    @DisplayName("a relative path is refused, naming which one")
    void relativePaths() {
        Path relative = Path.of("percolator");
        assertAll(
                () ->
                        assertEquals(
                                "the Percolator executable must be an absolute path, not"
                                        + " \"percolator\"",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new PercolatorRequest(
                                                                relative, PIN, OUT, TABLES, false,
                                                                Map.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "the merged PIN must be an absolute path, not \"percolator\"",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new PercolatorRequest(
                                                                EXE, relative, OUT, TABLES, false,
                                                                Map.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "the Percolator output directory must be an absolute path, not"
                                        + " \"percolator\"",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new PercolatorRequest(
                                                                EXE, PIN, relative, TABLES, false,
                                                                Map.of()))
                                        .getMessage()));
    }

    @Test
    @DisplayName("a Comet capability is not a Percolator one, and is refused")
    void cometCapability() {
        Set<ToolCapability> mixed = EnumSet.of(ToolCapability.PIN_OUTPUT);
        assertThrows(
                IllegalArgumentException.class,
                () -> new PercolatorRequest(EXE, PIN, OUT, mixed, false, Map.of()));
    }

    @Test
    @DisplayName("a file-naming option cannot carry a setting's value")
    void notAValuedOption() {
        assertEquals(
                "--weights does not take a setting's value; the valued options are [--seed,"
                        + " --num-threads, --testFDR, --trainFDR, --maxiter]",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> withValues(Map.of(PercolatorOption.WEIGHTS, "1")))
                        .getMessage());
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", "-1", "0,01", "1e-3", "1E3", " 1", "1 ", ".5", "5.", "+1", "x"})
    @DisplayName("a value that is not plain decimal is refused, naming it and the option")
    void badValue(String value) {
        assertEquals(
                "the value \""
                        + value
                        + "\" for --testFDR is not plain decimal digits with an optional fraction",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> withValues(Map.of(PercolatorOption.TEST_FDR, value)))
                        .getMessage());
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"0", "1", "0.01", "20000", "0.000001", "1.0"})
    @DisplayName("plain decimal values are accepted as they are")
    void goodValue(String value) {
        assertEquals(
                Map.of(PercolatorOption.TEST_FDR, value),
                withValues(Map.of(PercolatorOption.TEST_FDR, value)).values());
    }

    @Test
    @DisplayName("a null key, value or capability is refused")
    void nulls() {
        Map<PercolatorOption, String> nullValue = new HashMap<>();
        nullValue.put(PercolatorOption.SEED, null);
        Map<PercolatorOption, String> nullKey = new HashMap<>();
        nullKey.put(null, "1");
        Set<ToolCapability> nullCapability = new HashSet<>();
        nullCapability.add(null);
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> withValues(nullValue)),
                () -> assertThrows(NullPointerException.class, () -> withValues(nullKey)),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        new PercolatorRequest(
                                                EXE, PIN, OUT, nullCapability, false, Map.of())),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        new PercolatorRequest(
                                                EXE,
                                                PIN,
                                                OUT,
                                                Nulls.of(Set.class),
                                                false,
                                                Map.of())),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        new PercolatorRequest(
                                                EXE, PIN, OUT, TABLES, false, Nulls.of(Map.class))),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> PercolatorCommands.build(Nulls.of(PercolatorRequest.class))));
    }

    @Test
    @DisplayName("both collections are copied in, and handed out as immutable copies")
    void defensiveCopies() {
        Set<ToolCapability> capabilities = EnumSet.copyOf(TABLES);
        Map<PercolatorOption, String> values = new EnumMap<>(PercolatorOption.class);
        values.put(PercolatorOption.SEED, "1");
        PercolatorRequest request =
                new PercolatorRequest(EXE, PIN, OUT, capabilities, true, values);
        capabilities.add(ToolCapability.XML_OUTPUT);
        values.put(PercolatorOption.MAX_ITERATIONS, "3");

        assertAll(
                () -> assertEquals(TABLES, request.capabilities()),
                () -> assertEquals(Map.of(PercolatorOption.SEED, "1"), request.values()),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> request.capabilities().add(ToolCapability.XML_OUTPUT)),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> request.values().put(PercolatorOption.SEED, "2")));
    }

    @Test
    @DisplayName("the result's collections are copied too, and nulls refused")
    void commandRecord() {
        ToolCommand command = new ToolCommand(List.of("/opt/percolator"), OUT, Map.of());
        Map<PercolatorArtefact, Path> artefacts = new EnumMap<>(PercolatorArtefact.class);
        artefacts.put(PercolatorArtefact.TARGET_PSMS, OUT.resolve("psms.tsv"));
        NotEmitted left = new NotEmitted(PercolatorOption.SEED, "because");
        PercolatorCommand built =
                new PercolatorCommand(command, artefacts, new ArrayList<>(List.of(left)));
        artefacts.put(PercolatorArtefact.POUT_XML, OUT.resolve("pout.xml"));
        Map<PercolatorArtefact, Path> nullPath = new HashMap<>();
        nullPath.put(PercolatorArtefact.WEIGHTS, null);
        Map<PercolatorArtefact, Path> nullRole = new HashMap<>();
        nullRole.put(null, OUT);

        assertAll(
                () ->
                        assertEquals(
                                Map.of(PercolatorArtefact.TARGET_PSMS, OUT.resolve("psms.tsv")),
                                built.artefacts()),
                () -> assertEquals(List.of(left), built.notEmitted()),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> built.artefacts().clear()),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> built.notEmitted().clear()),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        new PercolatorCommand(
                                                Nulls.of(ToolCommand.class), Map.of(), List.of())),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> new PercolatorCommand(command, nullPath, List.of())),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> new PercolatorCommand(command, nullRole, List.of())),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        new PercolatorCommand(
                                                command, Nulls.of(Map.class), List.of())),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> built.omission(Nulls.of(PercolatorOption.class))));
    }

    @Test
    @DisplayName("an omission needs an option and a reason that says something")
    void notEmitted() {
        assertAll(
                () ->
                        assertEquals(
                                "a left-out option needs a reason",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> new NotEmitted(PercolatorOption.SEED, " "))
                                        .getMessage()),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> new NotEmitted(Nulls.of(PercolatorOption.class), "reason")),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        new NotEmitted(
                                                PercolatorOption.SEED, Nulls.of(String.class))),
                () ->
                        assertEquals(
                                ToolCapability.DECOY_OUTPUT,
                                new NotEmitted(PercolatorOption.DECOY_RESULTS_PEPTIDES, "r")
                                        .missing()));
    }
}
