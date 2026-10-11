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

package org.cometgui.workflow.limelight;

import static org.cometgui.workflow.limelight.LimelightFixtures.converter;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.workflow.limelight.LimelightFixtures.Converters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The converter's state read from the Tool Manager's offers, and its install started through the
 * same port: the one installer, never a second downloader.
 */
class LimelightProvisioningTest {

    private static final Path JAR =
            Path.of("cache").toAbsolutePath().resolve("tools/limelight/converter.jar");

    private static LimelightConverterStatus status(ToolOffer... offers) {
        return new LimelightProvisioning(new Converters(offers)).status();
    }

    @Test
    @DisplayName("an installed converter is used, the newest of them, and named with its JAR")
    void installed() {
        LimelightConverterStatus status =
                status(
                        converter("2.8.0", ToolInstallState.NOT_INSTALLED),
                        LimelightFixtures.installed("2.8.1", JAR),
                        LimelightFixtures.installed("2.7.0", JAR.resolveSibling("old.jar")));
        assertAll(
                () -> assertEquals(LimelightConverterStatus.State.INSTALLED, status.state()),
                () ->
                        assertEquals(
                                Optional.of(
                                        new LimelightConverterInstallation(
                                                ToolVersion.parse("2.8.1"), JAR)),
                                status.installation()),
                () ->
                        assertEquals(
                                "The Limelight converter 2.8.1 is installed: " + JAR + ".",
                                status.explanation()));
    }

    @Test
    @DisplayName(
            "not installed: installable, with the download's size, and after a failed attempt says"
                    + " so; being installed is its own state")
    void notInstalled() {
        LimelightConverterStatus fresh = status(converter("2.8.1", ToolInstallState.NOT_INSTALLED));
        LimelightConverterStatus failed = status(converter("2.8.1", ToolInstallState.FAILED));
        LimelightConverterStatus installing =
                status(
                        converter("2.8.1", ToolInstallState.INSTALLING),
                        converter("2.8.0", ToolInstallState.NOT_INSTALLED));
        assertAll(
                () -> assertEquals(LimelightConverterStatus.State.NOT_INSTALLED, fresh.state()),
                () -> assertEquals(OptionalLong.of(2_762_075L), fresh.downloadBytes()),
                () ->
                        assertEquals(
                                "The Limelight converter 2.8.1 is not installed. It is installed"
                                        + " the first time a run is converted (a download of"
                                        + " 2,762,075 bytes), which no search waits for.",
                                fresh.explanation()),
                () -> assertEquals(Optional.empty(), fresh.installation()),
                () -> assertEquals(LimelightConverterStatus.State.NOT_INSTALLED, failed.state()),
                () ->
                        assertEquals(
                                "The Limelight converter 2.8.1 is not installed (the last install"
                                        + " attempt did not succeed, or the installed files no"
                                        + " longer matched their recorded checksums). It is"
                                        + " installed the first time a run is converted (a"
                                        + " download of 2,762,075 bytes), which no search waits"
                                        + " for.",
                                failed.explanation()),
                () -> assertEquals(LimelightConverterStatus.State.INSTALLING, installing.state()),
                () -> assertEquals(Optional.of(ToolVersion.parse("2.8.1")), installing.version()));
    }

    @Test
    @DisplayName("nothing offered, or nothing runnable here, is unavailable with the reason")
    void unavailable() {
        ToolOffer pdv =
                new ToolOffer(
                        ToolName.PDV,
                        ToolVersion.parse("2.7.0"),
                        ToolOrigin.MANAGED,
                        ToolInstallState.INSTALLED,
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        Optional.of(JAR),
                        OptionalLong.of(1L));
        LimelightConverterStatus none = status(pdv);
        LimelightConverterStatus elsewhere =
                status(converter("2.8.1", ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM));
        assertAll(
                () -> assertEquals(LimelightConverterStatus.State.UNAVAILABLE, none.state()),
                () ->
                        assertEquals(
                                "The Tool Manager offers no Limelight converter build, so no run"
                                        + " can be converted here.",
                                none.explanation()),
                () ->
                        assertEquals(
                                "No Limelight converter build can be installed or used on this"
                                        + " computer: the Limelight converter 2.8.1 is not"
                                        + " published for this platform.",
                                elsewhere.explanation()));
    }

    @Test
    @DisplayName("install goes to the Tool Manager, for the converter and the version asked")
    void installThroughThePort() {
        Converters tools = new Converters(converter("2.8.1", ToolInstallState.NOT_INSTALLED));
        org.cometgui.domain.tools.InstallHandle handle =
                new LimelightProvisioning(tools)
                        .install(ToolVersion.parse("2.8.1"), progress -> {});
        assertEquals(List.of("limelight-converter 2.8.1"), tools.installs());
        assertEquals(tools.handle(), handle, "the Tool Manager's own handle, which cancels it");
    }

    @Test
    @DisplayName("statuses that contradict themselves are refused")
    void contradictions() {
        assertAll(
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new LimelightConverterStatus(
                                                LimelightConverterStatus.State.INSTALLED,
                                                Optional.of(ToolVersion.parse("2.8.1")),
                                                Optional.empty(),
                                                OptionalLong.empty(),
                                                "x")),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new LimelightConverterStatus(
                                                LimelightConverterStatus.State.UNAVAILABLE,
                                                Optional.of(ToolVersion.parse("2.8.1")),
                                                Optional.empty(),
                                                OptionalLong.empty(),
                                                "x")),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new LimelightConverterStatus(
                                                LimelightConverterStatus.State.INSTALLING,
                                                Optional.of(ToolVersion.parse("2.8.1")),
                                                Optional.empty(),
                                                OptionalLong.of(3L),
                                                "x")),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> LimelightConverterStatus.unavailable(" ")),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new LimelightConverterInstallation(
                                                ToolVersion.parse("2.8.1"), Path.of("a.jar"))));
    }
}
