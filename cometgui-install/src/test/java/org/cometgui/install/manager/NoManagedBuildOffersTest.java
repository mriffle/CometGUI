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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.NoManagedBuild;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.registry.ArtefactManifest;
import org.cometgui.install.registry.ArtefactRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code D-011}: {@link ManagedToolManager#noManagedBuild()} over the <strong>shipped</strong>
 * manifest, host by host. The fact is derived from the absence of rows, so these tests vary the
 * host and, once, the manifest -- never a platform-and-version pair in the code under test.
 */
class NoManagedBuildOffersTest {

    private static final HostPlatform INTEL_MAC =
            new HostPlatform(HostOperatingSystem.MACOS, HostArchitecture.X86_64);

    private static final HostPlatform APPLE_SILICON =
            new HostPlatform(HostOperatingSystem.MACOS, HostArchitecture.AARCH64);

    private static final HostPlatform WINDOWS =
            new HostPlatform(HostOperatingSystem.WINDOWS, HostArchitecture.X86_64);

    private static final HostPlatform LINUX_ARM =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.AARCH64);

    @TempDir private Path temporary;

    private ToolManagerHarness.Builder on(HostPlatform host) {
        return ToolManagerHarness.at(temporary.resolve("cache-" + host.id())).platform(host);
    }

    private static List<String> cometRows(List<ToolOffer> offers) {
        List<String> rows = new ArrayList<>();
        for (ToolOffer offer : offers) {
            if (offer.tool() == ToolName.COMET) {
                rows.add(offer.version().text() + " " + offer.origin() + " " + offer.state());
            }
        }
        return rows;
    }

    @Test
    @DisplayName("on an Intel Mac Comet has no managed build, and its registrar is reported")
    void anIntelMacHasNoManagedComet() throws IOException {
        ManagedToolManager withRegistrar =
                on(INTEL_MAC)
                        .registering(
                                ToolName.COMET,
                                executable -> {
                                    throw new AssertionError("nothing is registered here");
                                })
                        .build()
                        .manager();
        ManagedToolManager without = on(INTEL_MAC).build().manager();

        assertAll(
                () ->
                        assertEquals(
                                List.of(new NoManagedBuild(ToolName.COMET, INTEL_MAC, true)),
                                withRegistrar.noManagedBuild()),
                () ->
                        assertEquals(
                                List.of(new NoManagedBuild(ToolName.COMET, INTEL_MAC, false)),
                                without.noManagedBuild(),
                                "a registration the product cannot perform is not offered"),
                () ->
                        assertEquals(
                                List.of(
                                        "2026.03.0 MANAGED UNAVAILABLE_ON_THIS_PLATFORM",
                                        "2026.02.2 MANAGED UNAVAILABLE_ON_THIS_PLATFORM"),
                                cometRows(withRegistrar.offers()),
                                "every Comet release is shown, and none is installable"),
                () -> {
                    IllegalArgumentException refused =
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            withRegistrar.install(
                                                    ToolName.COMET,
                                                    ToolVersion.parse("2026.02.2"),
                                                    progress -> {}));
                    assertTrue(
                            refused.getMessage()
                                    .startsWith(
                                            "no offer names comet 2026.02.2 on macos-x86-64: the"
                                                    + " manifest publishes no artefact"),
                            refused::getMessage);
                });
    }

    @Test
    @DisplayName("Apple silicon, Linux x86-64 and Windows report nothing missing")
    void theTierOnePlatformsReportNothingMissing() throws IOException {
        List<String> seen = new ArrayList<>();
        for (HostPlatform host : List.of(ToolManagerFixtures.LINUX, APPLE_SILICON, WINDOWS)) {
            ManagedToolManager manager = on(host).build().manager();
            seen.add(
                    host.id() + " " + manager.noManagedBuild() + " " + cometRows(manager.offers()));
        }

        assertEquals(
                List.of(
                        "linux-x86-64 [] [2026.03.0 MANAGED NOT_INSTALLED,"
                                + " 2026.02.2 MANAGED NOT_INSTALLED]",
                        "macos-aarch64 [] [2026.03.0 MANAGED NOT_INSTALLED,"
                                + " 2026.02.2 MANAGED NOT_INSTALLED]",
                        "windows-x86-64 [] [2026.03.0 MANAGED NOT_INSTALLED,"
                                + " 2026.02.2 MANAGED NOT_INSTALLED]"),
                seen);
    }

    @Test
    @DisplayName("the same rule, unchanged, reports Percolator on Linux aarch64, which has none")
    void theRuleIsTheManifestsNotAPlatforms() throws IOException {
        assertEquals(
                List.of(new NoManagedBuild(ToolName.PERCOLATOR, LINUX_ARM, false)),
                on(LINUX_ARM).build().manager().noManagedBuild(),
                "the specification's tier-3 row for Linux aarch64 says Percolator is local-binary"
                        + " registration only, and no line of code says so: no row does");
    }

    @Test
    @DisplayName("a tool the manifest does not name at all is not reported as missing")
    void aToolWithNoReleasesIsNotMissing() throws IOException {
        ArtefactManifest shipped = ToolManagerFixtures.shippedManifest();
        List<ArtefactRecord> noPdv = new ArrayList<>();
        for (ArtefactRecord record : shipped.artefacts()) {
            if (record.tool() != ToolName.PDV) {
                noPdv.add(record);
            }
        }
        ArtefactManifest manifest = new ArtefactManifest(shipped.schemaVersion(), noPdv);

        assertAll(
                () ->
                        assertEquals(
                                List.of(),
                                on(ToolManagerFixtures.LINUX)
                                        .over(manifest)
                                        .build()
                                        .manager()
                                        .noManagedBuild(),
                                "no PDV release exists anywhere, so there is nothing to be missing"
                                        + " here"),
                () ->
                        assertEquals(
                                List.of(new NoManagedBuild(ToolName.COMET, INTEL_MAC, false)),
                                on(INTEL_MAC).over(manifest).build().manager().noManagedBuild()));
    }
}
