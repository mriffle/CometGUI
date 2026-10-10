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

package org.cometgui.workflow.pdv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallProgressListener;
import org.cometgui.domain.tools.NoManagedBuild;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PDV's state read from the Tool Manager's offers, and its install started through the same port:
 * the one installer, never a second downloader.
 */
class PdvProvisioningTest {

    private static final Path CACHE = Path.of("cache").toAbsolutePath();

    private static final Path JAR = CACHE.resolve("tools/pdv/2.7/PDV-2.7.0/PDV-2.7.0.jar");

    private static final Path NEWER_JAR = CACHE.resolve("tools/pdv/2.8/PDV-2.8.0/PDV-2.8.0.jar");

    private static ToolOffer offer(
            ToolName tool, String version, ToolOrigin origin, ToolInstallState state) {
        boolean installed = state == ToolInstallState.INSTALLED;
        boolean fetchable =
                origin == ToolOrigin.MANAGED
                        && state != ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM;
        return new ToolOffer(
                tool,
                ToolVersion.parse(version),
                origin,
                state,
                List.of(),
                List.of(),
                Optional.empty(),
                installed
                        ? Optional.of("2.8.0".equals(version) ? NEWER_JAR : JAR)
                        : Optional.empty(),
                fetchable ? OptionalLong.of(103_407_417L) : OptionalLong.empty());
    }

    private static ToolOffer pdv(String version, ToolInstallState state) {
        return offer(ToolName.PDV, version, ToolOrigin.MANAGED, state);
    }

    /** A Tool Manager that offers what it is given and records installs. */
    private static final class Offering implements ToolManager {

        private final List<ToolOffer> offers;

        private final List<String> installs = new ArrayList<>();

        private final InstallHandle handle = () -> {};

        Offering(ToolOffer... offers) {
            this.offers = List.of(offers);
        }

        @Override
        public List<ToolOffer> offers() {
            return offers;
        }

        @Override
        public List<NoManagedBuild> noManagedBuild() {
            return List.of();
        }

        @Override
        public InstallHandle install(
                ToolName tool, ToolVersion version, InstallProgressListener listener) {
            installs.add(tool.id() + " " + version.text());
            return handle;
        }

        @Override
        public ToolOffer registerLocalBinary(ToolName tool, Path executable) {
            throw new UnsupportedOperationException();
        }
    }

    private static PdvStatus status(ToolOffer... offers) {
        return new PdvProvisioning(new Offering(offers)).status();
    }

    @Test
    @DisplayName("an installed PDV is used, the newest of them, and named with its JAR")
    void installed() {
        PdvStatus status =
                status(
                        offer(
                                ToolName.COMET,
                                "2026.3.0",
                                ToolOrigin.LOCAL,
                                ToolInstallState.INSTALLED),
                        pdv("2.7.0", ToolInstallState.INSTALLED),
                        pdv("2.8.0", ToolInstallState.INSTALLED),
                        pdv("2.9.0", ToolInstallState.NOT_INSTALLED));

        assertAll(
                () -> assertEquals(PdvStatus.State.INSTALLED, status.state()),
                () -> assertEquals(Optional.of(ToolVersion.parse("2.8.0")), status.version()),
                () ->
                        assertEquals(
                                Optional.of(
                                        new PdvInstallation(ToolVersion.parse("2.8.0"), NEWER_JAR)),
                                status.installation()),
                () ->
                        assertEquals(
                                "PDV 2.8.0 is installed: " + NEWER_JAR + ".",
                                status.explanation()));
    }

    @Test
    @DisplayName("an install in flight is said, from whichever section started it")
    void installing() {
        PdvStatus status =
                status(
                        pdv("2.7.0", ToolInstallState.INSTALLING),
                        pdv("2.8.0", ToolInstallState.NOT_INSTALLED));

        assertEquals(PdvStatus.State.INSTALLING, status.state());
        assertEquals(
                "PDV 2.7.0 is being installed. A spectrum can be drawn when the install has"
                        + " finished.",
                status.explanation());
        assertEquals(Optional.empty(), status.installation());
    }

    @Test
    @DisplayName("an installable PDV says what installing it costs, and that no search waits")
    void notInstalled() {
        PdvStatus status =
                status(
                        pdv("2.6.0", ToolInstallState.NOT_INSTALLED),
                        pdv("2.7.0", ToolInstallState.NOT_INSTALLED),
                        pdv("2.9.0", ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM));

        assertAll(
                () -> assertEquals(PdvStatus.State.NOT_INSTALLED, status.state()),
                () -> assertEquals(Optional.of(ToolVersion.parse("2.7.0")), status.version()),
                () -> assertEquals(OptionalLong.of(103_407_417L), status.downloadBytes()),
                () ->
                        assertEquals(
                                "PDV 2.7.0 is not installed. It is installed the first time a"
                                        + " spectrum is drawn: a download of 103,407,417 bytes,"
                                        + " which no search waits for.",
                                status.explanation()));
    }

    @Test
    @DisplayName("a failed attempt is installable again, and says so")
    void failedIsInstallable() {
        PdvStatus status = status(pdv("2.7.0", ToolInstallState.FAILED));

        assertEquals(PdvStatus.State.NOT_INSTALLED, status.state());
        assertEquals(
                "PDV 2.7.0 is not installed (the last install attempt did not succeed). It is"
                        + " installed the first time a spectrum is drawn: a download of"
                        + " 103,407,417 bytes, which no search waits for.",
                status.explanation());
    }

    @Test
    @DisplayName("no PDV offered at all is unavailable, in words")
    void noneOffered() {
        PdvStatus status =
                status(
                        offer(
                                ToolName.COMET,
                                "2026.3.0",
                                ToolOrigin.LOCAL,
                                ToolInstallState.INSTALLED));

        assertEquals(PdvStatus.State.UNAVAILABLE, status.state());
        assertEquals(
                "The Tool Manager offers no PDV build, so no spectrum can be drawn here.",
                status.explanation());
    }

    @Test
    @DisplayName("builds this computer cannot have are unavailable, each named")
    void noneUsable() {
        PdvStatus status =
                status(
                        pdv("2.7.0", ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM),
                        pdv("2.8.0", ToolInstallState.HOST_REQUIREMENTS_NOT_MET));

        assertEquals(PdvStatus.State.UNAVAILABLE, status.state());
        assertEquals(
                "No PDV build can be installed or used on this computer: PDV 2.7.0 is not"
                        + " published for this platform; PDV 2.8.0 is not able to run on this"
                        + " computer.",
                status.explanation());
    }

    @Test
    @DisplayName("an install goes to the one Tool Manager, for PDV and the version asked")
    void installThroughTheToolManager() {
        Offering tools = new Offering(pdv("2.7.0", ToolInstallState.NOT_INSTALLED));

        InstallHandle handle =
                new PdvProvisioning(tools).install(ToolVersion.parse("2.7.0"), progress -> {});

        assertSame(tools.handle, handle);
        assertEquals(List.of("pdv 2.7.0"), tools.installs);
    }

    @Test
    @DisplayName("a status's components must agree with its state")
    void statusesAreConsistent() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PdvStatus(
                                PdvStatus.State.INSTALLED,
                                Optional.of(ToolVersion.parse("2.7.0")),
                                Optional.empty(),
                                OptionalLong.empty(),
                                "x"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PdvStatus(
                                PdvStatus.State.UNAVAILABLE,
                                Optional.of(ToolVersion.parse("2.7.0")),
                                Optional.empty(),
                                OptionalLong.empty(),
                                "x"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PdvStatus(
                                PdvStatus.State.INSTALLING,
                                Optional.of(ToolVersion.parse("2.7.0")),
                                Optional.empty(),
                                OptionalLong.of(1),
                                "x"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PdvStatus(
                                PdvStatus.State.NOT_INSTALLED,
                                Optional.of(ToolVersion.parse("2.7.0")),
                                Optional.of(JAR),
                                OptionalLong.empty(),
                                "x"));
        assertThrows(IllegalArgumentException.class, () -> PdvStatus.unavailable(" "));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PdvInstallation(ToolVersion.parse("2.7.0"), Path.of("relative.jar")));
        assertEquals(
                "PDV 2.7.0 is not installed. It is installed the first time a spectrum is drawn,"
                        + " which no search waits for.",
                PdvStatus.notInstalled(ToolVersion.parse("2.7.0"), OptionalLong.empty(), false)
                        .explanation());
    }
}
