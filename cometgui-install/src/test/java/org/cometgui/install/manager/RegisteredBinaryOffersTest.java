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

package org.cometgui.install.manager;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolRegistrationException;
import org.cometgui.domain.tools.ToolVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A binary the user registered is one the Tool Manager shows.
 *
 * <h2>What was wrong</h2>
 *
 * <p>{@code registerLocalBinary} handed its offer to the caller and nothing kept it, so the one
 * path the specification calls the documented remedy wherever no managed XML-capable build exists
 * produced a tool that {@code offers()} never mentioned again. {@code
 * phases/PHASE-05-tool-registry.rst} puts local tools in this section's scope in as many words.
 *
 * <h2>The three decisions graded here</h2>
 *
 * <ul>
 *   <li><strong>Where the row sits</strong>: with its own tool, after the releases CometGUI can
 *       install, in registration order.
 *   <li><strong>The same path twice</strong>: one row, replaced where it was.
 *   <li><strong>A second, different path for the same tool</strong>: a second row.
 * </ul>
 *
 * <p>Every expected list below is written out by hand. The manifest's six rows are the same six
 * {@code ManagedToolManagerOffersTest} pins, restated here so that the local rows are asserted in
 * their place among them rather than on their own.
 */
class RegisteredBinaryOffersTest {

    private static final ToolVersion LOCAL_3_05 = ToolVersion.parse("3.05");

    private static final ToolVersion LOCAL_3_07_1 = ToolVersion.parse("3.07.1");

    @TempDir private Path temporary;

    /**
     * Tool, version, origin and state, so that a managed row and a local one cannot be confused.
     */
    private static String row(ToolOffer offer) {
        return offer.tool().id()
                + " "
                + offer.version().text()
                + " "
                + offer.origin()
                + " "
                + offer.state();
    }

    /**
     * The six rows the shipped manifest gives this host, hand-typed.
     *
     * @return the rows, in the order the port emits them
     */
    private static List<String> theManifestsSixRows() {
        return List.of(
                "comet 2026.03.0 MANAGED NOT_INSTALLED",
                "comet 2026.02.2 MANAGED NOT_INSTALLED",
                "percolator 3.09 MANAGED UNAVAILABLE_ON_THIS_PLATFORM",
                "percolator 3.07.1 MANAGED NOT_INSTALLED",
                "percolator 3.06.5 MANAGED NOT_INSTALLED",
                "pdv 2.7.0 MANAGED NOT_INSTALLED",
                "limelight-converter 2.8.1 MANAGED NOT_INSTALLED");
    }

    @Test
    @DisplayName("before anything is registered the offered rows are the manifest's alone")
    void beforeAnythingIsRegisteredTheRowsAreTheManifestsAlone() throws IOException {
        ToolManagerHarness harness =
                ToolManagerHarness.at(temporary.resolve("cache"))
                        .registering(
                                ToolName.PERCOLATOR, chosen -> localPercolator(chosen, LOCAL_3_05))
                        .build();

        assertEquals(theManifestsSixRows(), rows(harness.manager().offers()));
    }

    @Test
    @DisplayName("a registered binary appears, with its own tool and after that tool's releases")
    void aRegisteredBinaryAppearsWithItsOwnTool() throws IOException, ToolRegistrationException {
        Path binary = temporary.resolve("percolator-3.05");
        ToolManagerHarness harness =
                ToolManagerHarness.at(temporary.resolve("cache"))
                        .registering(
                                ToolName.PERCOLATOR, chosen -> localPercolator(chosen, LOCAL_3_05))
                        .build();

        harness.manager().registerLocalBinary(ToolName.PERCOLATOR, binary);

        assertEquals(
                List.of(
                        "comet 2026.03.0 MANAGED NOT_INSTALLED",
                        "comet 2026.02.2 MANAGED NOT_INSTALLED",
                        "percolator 3.09 MANAGED UNAVAILABLE_ON_THIS_PLATFORM",
                        "percolator 3.07.1 MANAGED NOT_INSTALLED",
                        "percolator 3.06.5 MANAGED NOT_INSTALLED",
                        "percolator 3.05 LOCAL INSTALLED",
                        "pdv 2.7.0 MANAGED NOT_INSTALLED",
                        "limelight-converter 2.8.1 MANAGED NOT_INSTALLED"),
                rows(harness.manager().offers()),
                "the local row belongs among the Percolators, after the releases CometGUI can"
                        + " install and before the next tool");
    }

    @Test
    @DisplayName("the same path registered twice is one row, kept where it was")
    void theSamePathTwiceIsOneRowKeptWhereItWas() throws IOException, ToolRegistrationException {
        Path first = temporary.resolve("percolator-a");
        Path second = temporary.resolve("percolator-b");
        ToolManagerHarness harness =
                ToolManagerHarness.at(temporary.resolve("cache"))
                        .registering(
                                ToolName.PERCOLATOR,
                                chosen ->
                                        localPercolator(
                                                chosen,
                                                chosen.equals(first) ? LOCAL_3_05 : LOCAL_3_07_1))
                        .build();

        harness.manager().registerLocalBinary(ToolName.PERCOLATOR, first);
        harness.manager().registerLocalBinary(ToolName.PERCOLATOR, second);
        List<String> afterTwoDifferentPaths = rows(harness.manager().offers());
        harness.manager().registerLocalBinary(ToolName.PERCOLATOR, first);

        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        "comet 2026.03.0 MANAGED NOT_INSTALLED",
                                        "comet 2026.02.2 MANAGED NOT_INSTALLED",
                                        "percolator 3.09 MANAGED UNAVAILABLE_ON_THIS_PLATFORM",
                                        "percolator 3.07.1 MANAGED NOT_INSTALLED",
                                        "percolator 3.06.5 MANAGED NOT_INSTALLED",
                                        "percolator 3.05 LOCAL INSTALLED",
                                        "percolator 3.07.1 LOCAL INSTALLED",
                                        "pdv 2.7.0 MANAGED NOT_INSTALLED",
                                        "limelight-converter 2.8.1 MANAGED NOT_INSTALLED"),
                                afterTwoDifferentPaths,
                                "two different files are two binaries the user holds, and one of"
                                        + " them reports a version the manifest also names -- which"
                                        + " is exactly why the key is the path"),
                () ->
                        assertEquals(
                                afterTwoDifferentPaths,
                                rows(harness.manager().offers()),
                                "registering the first path again re-probes it and leaves the row"
                                        + " where it was; it does not add one and does not move it"
                                        + " to the end while the user is looking at it"));
    }

    @Test
    @DisplayName("a registration is re-probed, so the row shows what the file says now")
    void aRegistrationIsReProbed() throws IOException, ToolRegistrationException {
        Path binary = temporary.resolve("percolator");
        ToolVersion[] answer = {LOCAL_3_05};
        ToolManagerHarness harness =
                ToolManagerHarness.at(temporary.resolve("cache"))
                        .registering(
                                ToolName.PERCOLATOR, chosen -> localPercolator(chosen, answer[0]))
                        .build();

        harness.manager().registerLocalBinary(ToolName.PERCOLATOR, binary);
        List<String> before = rows(harness.manager().offers());
        answer[0] = LOCAL_3_07_1;
        harness.manager().registerLocalBinary(ToolName.PERCOLATOR, binary);

        assertAll(
                () -> assertEquals("percolator 3.05 LOCAL INSTALLED", theOneLocalRow(before)),
                () ->
                        assertEquals(
                                "percolator 3.07.1 LOCAL INSTALLED",
                                theOneLocalRow(rows(harness.manager().offers())),
                                "the file at that path changed under the user and asking again is"
                                        + " how they say so"));
    }

    @Test
    @DisplayName("a local row sits with its own tool and with no other")
    void aLocalRowSitsWithItsOwnToolAndNoOther() throws IOException, ToolRegistrationException {
        Path percolator = temporary.resolve("percolator");
        Path comet = temporary.resolve("comet");
        ToolManagerHarness harness =
                ToolManagerHarness.at(temporary.resolve("cache"))
                        .registeringAll(
                                Map.of(
                                        ToolName.PERCOLATOR,
                                        chosen -> localPercolator(chosen, LOCAL_3_05),
                                        ToolName.COMET,
                                        chosen -> localComet(chosen)))
                        .build();

        harness.manager().registerLocalBinary(ToolName.PERCOLATOR, percolator);
        harness.manager().registerLocalBinary(ToolName.COMET, comet);

        assertEquals(
                List.of(
                        "comet 2026.03.0 MANAGED NOT_INSTALLED",
                        "comet 2026.02.2 MANAGED NOT_INSTALLED",
                        "comet 2026.01.1 LOCAL INSTALLED",
                        "percolator 3.09 MANAGED UNAVAILABLE_ON_THIS_PLATFORM",
                        "percolator 3.07.1 MANAGED NOT_INSTALLED",
                        "percolator 3.06.5 MANAGED NOT_INSTALLED",
                        "percolator 3.05 LOCAL INSTALLED",
                        "pdv 2.7.0 MANAGED NOT_INSTALLED",
                        "limelight-converter 2.8.1 MANAGED NOT_INSTALLED"),
                rows(harness.manager().offers()),
                "each local row goes with its own tool, in the enumeration's order, and the"
                        + " Percolator the user registered second is not listed under Comet");
    }

    @Test
    @DisplayName("the registered row is the registrar's own answer, capabilities and all")
    void theRegisteredRowIsTheRegistrarsOwnAnswer() throws IOException, ToolRegistrationException {
        Path binary = temporary.resolve("percolator");
        ToolManagerHarness harness =
                ToolManagerHarness.at(temporary.resolve("cache"))
                        .registering(
                                ToolName.PERCOLATOR, chosen -> localPercolator(chosen, LOCAL_3_05))
                        .build();

        ToolOffer returned = harness.manager().registerLocalBinary(ToolName.PERCOLATOR, binary);
        ToolOffer listed =
                harness.manager().offers().stream()
                        .filter(offer -> offer.origin() == ToolOrigin.LOCAL)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("no local row was listed"));

        assertAll(
                () ->
                        assertEquals(
                                returned,
                                listed,
                                "the row is what the registrar answered, unchanged: this class"
                                        + " keeps offers, it does not make them"),
                () ->
                        assertEquals(
                                List.of("PSM_TSV_OUTPUT"),
                                listed.capabilities().stream()
                                        .map(declared -> declared.capability().id())
                                        .toList()),
                () ->
                        assertEquals(
                                CapabilityEvidence.UNVERIFIED,
                                listed.capabilities().get(0).evidence(),
                                "R-TOOL-08: absent positive evidence, and the row says so"),
                () ->
                        assertEquals(
                                OptionalLong.empty(),
                                listed.downloadSizeBytes(),
                                "nothing was fetched for it"),
                () -> assertEquals(Optional.of(binary), listed.installedPath()));
    }

    private static List<String> rows(List<ToolOffer> offers) {
        return offers.stream().map(RegisteredBinaryOffersTest::row).toList();
    }

    private static ToolOffer localPercolator(Path executable, ToolVersion version) {
        return new ToolOffer(
                ToolName.PERCOLATOR,
                version,
                ToolOrigin.LOCAL,
                ToolInstallState.INSTALLED,
                List.of(
                        new DeclaredCapability(
                                ToolCapability.PSM_TSV_OUTPUT,
                                CapabilityEvidence.UNVERIFIED,
                                "not probed by this fixture")),
                List.of(),
                Optional.empty(),
                Optional.of(executable),
                OptionalLong.empty());
    }

    private static ToolOffer localComet(Path executable) {
        return new ToolOffer(
                ToolName.COMET,
                ToolVersion.parse("2026.01.1"),
                ToolOrigin.LOCAL,
                ToolInstallState.INSTALLED,
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.of(executable),
                OptionalLong.empty());
    }

    /*
     * The single row a registration produced, found by its origin rather than by its position: a
     * release added to the manifest moves every row after it, and an index would then read some
     * other row and fail for a reason unrelated to the registration under test.
     */
    private static String theOneLocalRow(List<String> rows) {
        List<String> local = rows.stream().filter(row -> row.contains(" LOCAL ")).toList();
        assertEquals(1, local.size(), () -> "one LOCAL row expected: " + rows);
        return local.get(0);
    }
}
