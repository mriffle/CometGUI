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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.CompatibilityAction;
import org.cometgui.params.percolator.resolution.DownstreamStage;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.workflow.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The entry point phase 12 added to the one compatible rerun: {@link
 * PercolatorRerun#compatibleChoice} turns the action offered for a run without Percolator XML into
 * the Percolator choice {@link PercolatorRerun#prepare} takes -- the build resolution returned,
 * hashed now, Limelight enabled -- or refuses, saying why, when no rerun is offered or the build is
 * not installed yet. The rerun itself is {@code PercolatorRerunTest}'s and the real {@code
 * RealLimelightCompatibleRerunTest}'s.
 */
class PercolatorRerunCompatibleChoiceTest {

    private static final DownstreamStage LIMELIGHT = DownstreamStage.LIMELIGHT_CONVERSION;

    @TempDir private Path root;

    private final PercolatorRerun rerun =
            new PercolatorRerun(new CachingHashService(new StreamingHashService()));

    private static ToolOffer offer(
            String version,
            ToolOrigin origin,
            ToolInstallState state,
            Optional<Path> path,
            boolean xml) {
        List<DeclaredCapability> declared = new ArrayList<>();
        List<ToolCapability> capabilities =
                new ArrayList<>(
                        List.of(ToolCapability.PSM_TSV_OUTPUT, ToolCapability.PEPTIDE_TSV_OUTPUT));
        if (xml) {
            capabilities.add(ToolCapability.XML_OUTPUT);
        }
        for (ToolCapability capability : capabilities) {
            declared.add(
                    new DeclaredCapability(
                            capability, CapabilityEvidence.OBSERVED_BY_EXECUTION, "test fixture"));
        }
        return new ToolOffer(
                ToolName.PERCOLATOR,
                ToolVersion.parse(version),
                origin,
                state,
                declared,
                List.of(),
                Optional.empty(),
                path,
                origin == ToolOrigin.MANAGED ? OptionalLong.of(1L) : OptionalLong.empty());
    }

    private Path executable(String name, String content) throws IOException {
        return Files.writeString(root.resolve(name), content, StandardCharsets.UTF_8);
    }

    private static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }

    @Test
    @DisplayName(
            "the offered rerun becomes the choice of the build resolution returned, Limelight"
                    + " enabled, its executable hashed now")
    void theResolvedBuild() throws IOException, RerunRefusedException {
        Path nine = executable("percolator-3.09", "#!/bin/sh\necho 3.09\n");
        Path seven = executable("percolator-3.07.1", "#!/bin/sh\necho 3.07.1\n");
        ToolOffer local309 =
                offer(
                        "3.09",
                        ToolOrigin.LOCAL,
                        ToolInstallState.INSTALLED,
                        Optional.of(nine),
                        false);
        ToolOffer managed3071 =
                offer(
                        "3.07.1",
                        ToolOrigin.MANAGED,
                        ToolInstallState.INSTALLED,
                        Optional.of(seven),
                        true);
        CompatibilityAction action =
                CompatibilityAction.forStage(List.of(local309, managed3071), LIMELIGHT);
        PercolatorSettings settings = PercolatorSettings.defaults();

        PercolatorChoice choice = rerun.compatibleChoice(action, settings);

        assertEquals(managed3071, choice.selection().offer());
        assertEquals("3.07.1", choice.selection().version().text());
        assertEquals(seven, choice.selection().executable());
        assertEquals(sha256(seven), choice.selection().sha256());
        assertEquals(EnumSet.of(LIMELIGHT), choice.enabledStages());
        assertSame(action.resolution(), choice.resolution());
        assertSame(settings, choice.settings());
        assertTrue(choice.xmlNeeded());
        assertTrue(choice.isResolvedDefault());
    }

    @Test
    @DisplayName("no rerun offered (R-LL-02): refused with the action's own explanation")
    void noRerunOffered() throws IOException {
        Path nine = executable("percolator-3.09", "#!/bin/sh\n");
        CompatibilityAction action =
                CompatibilityAction.forStage(
                        List.of(
                                offer(
                                        "3.09",
                                        ToolOrigin.LOCAL,
                                        ToolInstallState.INSTALLED,
                                        Optional.of(nine),
                                        false)),
                        LIMELIGHT);

        RerunRefusedException refused =
                assertThrows(
                        RerunRefusedException.class,
                        () -> rerun.compatibleChoice(action, PercolatorSettings.defaults()));

        assertEquals(
                "No Percolator rerun is offered for Limelight conversion: " + action.explanation(),
                refused.getMessage());
    }

    @Test
    @DisplayName("the resolved build is not installed yet: refused, naming it, before any hashing")
    void notInstalledYet() {
        CompatibilityAction action =
                CompatibilityAction.forStage(
                        List.of(
                                offer(
                                        "3.08.2",
                                        ToolOrigin.MANAGED,
                                        ToolInstallState.NOT_INSTALLED,
                                        Optional.empty(),
                                        true)),
                        LIMELIGHT);

        RerunRefusedException refused =
                assertThrows(
                        RerunRefusedException.class,
                        () -> rerun.compatibleChoice(action, PercolatorSettings.defaults()));

        assertEquals(
                "Percolator 3.08.2 is not installed, and the rerun runs only an installed build:"
                        + " install it, then ask for the rerun again.",
                refused.getMessage());
    }

    @Test
    @DisplayName("nulls are refused")
    void nulls() {
        CompatibilityAction action = CompatibilityAction.forStage(List.of(), LIMELIGHT);
        assertThrows(
                NullPointerException.class,
                () ->
                        rerun.compatibleChoice(
                                Nulls.of(CompatibilityAction.class),
                                PercolatorSettings.defaults()));
        assertThrows(
                NullPointerException.class,
                () -> rerun.compatibleChoice(action, Nulls.of(PercolatorSettings.class)));
    }
}
