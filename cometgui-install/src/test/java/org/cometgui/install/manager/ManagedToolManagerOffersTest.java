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
import java.util.Optional;
import java.util.OptionalLong;
import org.cometgui.domain.tools.InstallProgressListener;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolRegistrationException;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.registry.ArtefactSelection;
import org.cometgui.install.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the Tool Manager shows, against the manifest the product actually ships.
 *
 * <p>Everything here is asserted against {@code manifests/tools.json} rather than a fixture, and
 * that is this phase's own lesson twice over: a fixture built to exercise a rule contains exactly
 * what the rule needs and nothing awkward, while the shipped manifest contains a 99 MB download
 * carried on five platforms, a version published for two platforms and not for this one, and a tool
 * with two builds of one release. Every one of those is a case the Tool Manager has to get right.
 */
class ManagedToolManagerOffersTest {

    @TempDir private Path cacheRoot;

    /**
     * Every row the Tool Manager shows on a clean Debian 12 machine with an empty cache, hand-typed
     * in order.
     *
     * <p>Comet publishes one Linux x86-64 build; Percolator publishes 3.07.1 and 3.06.5 for this
     * host and <strong>no Linux build of 3.09 at all</strong>; PDV and the Limelight converter are
     * one file each. The 3.09 row is the interesting one and it is why this list is written out
     * rather than computed: {@code R-PERC-12} says a version may legitimately be absent on a
     * platform and that "absent is honest", and the honest way to say it is to say it.
     */
    private static final List<String> OFFERS_ON_A_CLEAN_LINUX_HOST =
            List.of(
                    "comet 2026.02.2 NOT_INSTALLED",
                    "percolator 3.07.1 NOT_INSTALLED",
                    "percolator 3.06.5 NOT_INSTALLED",
                    "percolator 3.09 UNAVAILABLE_ON_THIS_PLATFORM",
                    "pdv 2.7.0 NOT_INSTALLED",
                    "limelight-converter 2.8.1 NOT_INSTALLED");

    @Nested
    @DisplayName("the offered set is the manifest's selection, and nothing else")
    class TheOfferedSet {

        @Test
        @DisplayName("every row on a clean host, in order, from the shipped manifest")
        void everyRowIsPinned() throws IOException {
            try (ToolManagerHarness harness = ToolManagerHarness.onDebian12(cacheRoot)) {
                assertEquals(OFFERS_ON_A_CLEAN_LINUX_HOST, harness.describeOffers());
            }
        }

        /*
         * GATE ITEM 8, stated as an equality rather than as a spot check.  The set of rows the Tool
         * Manager presents as installable is computed here from ArtefactManifest.select over the
         * shipped file -- the same query the product uses -- and compared with what offers()
         * produced.  An offer of a version and platform combination the manifest does not carry
         * would appear on one side and not the other, in either direction.
         */
        @Test
        @DisplayName("the installable rows are exactly what select() answers for this host")
        void theInstallableRowsAreExactlyTheSelection() throws IOException {
            try (ToolManagerHarness harness = ToolManagerHarness.onDebian12(cacheRoot)) {
                List<String> fromTheManifest = new ArrayList<>();
                for (ToolName tool : ToolName.values()) {
                    for (ArtefactSelection selection :
                            harness.manifest().select(ToolManagerHarness.LINUX, tool)) {
                        fromTheManifest.add(
                                selection.artefact().tool().id()
                                        + " "
                                        + selection.artefact().version().text());
                    }
                }
                List<String> installable =
                        harness.manager().offers().stream()
                                .filter(offer -> offer.origin() == ToolOrigin.MANAGED)
                                .filter(
                                        offer ->
                                                offer.state()
                                                        != ToolInstallState
                                                                .UNAVAILABLE_ON_THIS_PLATFORM)
                                .map(offer -> offer.tool().id() + " " + offer.version().text())
                                .toList();

                assertAll(
                        () -> assertEquals(fromTheManifest, installable),
                        () ->
                                assertEquals(
                                        List.of(
                                                "comet 2026.02.2",
                                                "percolator 3.07.1",
                                                "percolator 3.06.5",
                                                "pdv 2.7.0",
                                                "limelight-converter 2.8.1"),
                                        installable,
                                        "and the selection itself is what this host should see,"
                                                + " hand-typed so that a manifest change is a"
                                                + " decision rather than an accident"));
            }
        }

        @Test
        @DisplayName("a version the manifest does not publish here cannot be installed by asking")
        void anAbsentVersionCannotBeInstalledByAsking() throws IOException {
            try (ToolManagerHarness harness = ToolManagerHarness.onDebian12(cacheRoot)) {
                assertAll(
                        () ->
                                assertEquals(
                                        "no offer on this host names percolator 3.08; linux-x86-64"
                                                + " is offered [percolator 3.07.1 linux-x86-64,"
                                                + " percolator 3.06.5 linux-x86-64]",
                                        assertThrows(
                                                        IllegalArgumentException.class,
                                                        () ->
                                                                harness.manager()
                                                                        .install(
                                                                                ToolName.PERCOLATOR,
                                                                                ToolVersion.parse(
                                                                                        "3.08"),
                                                                                progress -> {}))
                                                .getMessage(),
                                        "3.08 exists upstream and is in no row of this manifest"),
                        () ->
                                assertEquals(
                                        "no offer on this host names percolator 3.09; linux-x86-64"
                                                + " is offered [percolator 3.07.1 linux-x86-64,"
                                                + " percolator 3.06.5 linux-x86-64]",
                                        assertThrows(
                                                        IllegalArgumentException.class,
                                                        () ->
                                                                harness.manager()
                                                                        .install(
                                                                                ToolName.PERCOLATOR,
                                                                                ToolVersion.parse(
                                                                                        "3.09"),
                                                                                progress -> {}))
                                                .getMessage(),
                                        "3.09 is in the manifest, for two other platforms, and"
                                                + " being shown it is not being offered it"));
            }
        }

        /*
         * A SECOND PLATFORM, and the only one where the ORDER of the unavailable rows can be seen
         * at all.  On linux-x86-64 exactly one Percolator version has no build here, so newest
         * first and manifest order are the same list and neither can be told from the other.  On
         * 64-bit ARM Linux upstream publishes a Comet build and no Percolator build of any version
         * -- every one of the three is unavailable -- and the manifest lists them 3.07.1, 3.06.5,
         * 3.09, so the ordering is observable and wrong the moment it is not applied.
         */
        @Test
        @DisplayName("on ARM Linux all three Percolator versions are unavailable, newest first")
        void theUnavailableRowsAreOrderedNewestFirst() throws IOException {
            try (ToolManagerHarness harness = ToolManagerHarness.onLinuxAarch64(cacheRoot)) {
                assertEquals(
                        List.of(
                                "comet 2026.02.2 NOT_INSTALLED",
                                "percolator 3.09 UNAVAILABLE_ON_THIS_PLATFORM",
                                "percolator 3.07.1 UNAVAILABLE_ON_THIS_PLATFORM",
                                "percolator 3.06.5 UNAVAILABLE_ON_THIS_PLATFORM",
                                "pdv 2.7.0 NOT_INSTALLED",
                                "limelight-converter 2.8.1 NOT_INSTALLED"),
                        harness.describeOffers(),
                        "R-PERC-12: Percolator publishes no aarch64 Linux build of any version, so"
                                + " local-binary registration is the whole remedy there, and"
                                + " saying which versions exist is how a user knows that");
            }
        }

        @Test
        @DisplayName("the unavailable row carries no capability, advisory, size or diagnostic")
        void theUnavailableRowClaimsNothing() throws IOException {
            try (ToolManagerHarness harness = ToolManagerHarness.onDebian12(cacheRoot)) {
                ToolOffer absent = harness.offerOf(ToolName.PERCOLATOR, "3.09");

                assertAll(
                        () ->
                                assertEquals(
                                        ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM,
                                        absent.state()),
                        () -> assertEquals(List.of(), absent.capabilities()),
                        () ->
                                assertEquals(
                                        List.of(),
                                        absent.advisories(),
                                        "every advisory in the manifest belongs to a row, and this"
                                                + " host has no row; 3.09's macOS row carries a"
                                                + " Rosetta 2 advisory and its Windows row carries"
                                                + " a Visual C++ one, and neither is true here"),
                        () -> assertEquals(OptionalLong.empty(), absent.downloadSizeBytes()),
                        () -> assertEquals(Optional.empty(), absent.loaderDiagnostic()),
                        () -> assertEquals(Optional.empty(), absent.installedPath()));
            }
        }
    }

    @Nested
    @DisplayName("the download size (gate: the Tool Manager can say what a build costs)")
    class DownloadSizes {

        /*
         * The number is hand-typed and the path to it is the product's own.  Reading it back out of
         * the record the offer was built from would be an expected value computed by the code under
         * test, and 103407417 is exactly the figure a scientist is owed before a transfer the phase
         * document singles out for cancellation testing.
         */
        @Test
        @DisplayName("PDV's 103407417 bytes reach the offer from manifests/tools.json")
        void pdvsSizeIsStated() throws IOException {
            try (ToolManagerHarness harness = ToolManagerHarness.onDebian12(cacheRoot)) {
                assertAll(
                        () ->
                                assertEquals(
                                        OptionalLong.of(103407417L),
                                        harness.offerOf(ToolName.PDV, "2.7.0").downloadSizeBytes()),
                        () ->
                                assertEquals(
                                        103407417L,
                                        harness.recordOf(ToolName.PDV, "2.7.0").sizeBytes(),
                                        "and it is the manifest's own number, not a literal this"
                                                + " test agreed with itself about"));
            }
        }

        @Test
        @DisplayName("every installable row states a size, and it is that row's own artefact")
        void everyInstallableRowStatesItsOwnSize() throws IOException {
            try (ToolManagerHarness harness = ToolManagerHarness.onDebian12(cacheRoot)) {
                List<String> sizes = new ArrayList<>();
                for (ToolOffer offer : harness.manager().offers()) {
                    if (offer.state() != ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM) {
                        sizes.add(
                                offer.tool().id()
                                        + " "
                                        + offer.version().text()
                                        + " "
                                        + offer.downloadSizeBytes().getAsLong());
                    }
                }

                assertEquals(
                        List.of(
                                "comet 2026.02.2 7014400",
                                "percolator 3.07.1 946303",
                                "percolator 3.06.5 917285",
                                "pdv 2.7.0 103407417",
                                "limelight-converter 2.8.1 2762075"),
                        sizes,
                        "each row's own artefact length, hand-typed; two rows sharing a number"
                                + " would mean one download had been attributed to the other");
            }
        }
    }

    @Nested
    @DisplayName("a build that will not run here (R-PLAT-03, gate item 5)")
    class NotRunnableHere {

        /*
         * Against the SHIPPED manifest's own floors, on a host that fails one of them.  The host is
         * a value rather than a lie about this machine: HostRuntimeVersions is what the product
         * reads, and "a machine with glibc 2.20" is a real machine -- a CentOS 6 or an Ubuntu 10.04
         * -- for which upstream really does publish 3.06.5 and really does not publish a 3.07.1
         * that will start.
         */
        @Test
        @DisplayName("on a glibc 2.20 host 3.07.1 is withheld and the message names the way out")
        void anUnmetFloorWithholdsTheBuildAndNamesTheAlternative() throws IOException {
            try (ToolManagerHarness harness =
                    ToolManagerHarness.onHostWithGlibc(cacheRoot, "2.20")) {
                ToolOffer refused = harness.offerOf(ToolName.PERCOLATOR, "3.07.1");

                assertAll(
                        () ->
                                assertEquals(
                                        ToolInstallState.HOST_REQUIREMENTS_NOT_MET,
                                        refused.state()),
                        () ->
                                assertEquals(
                                        "This build cannot run on this host: libc.so.6 on this host"
                                                + " does not provide a symbol version this build"
                                                + " needs. Required: GLIBC_2.34. Available on this"
                                                + " host: GLIBC_2.20. Alternatives: percolator"
                                                + " 3.06.5 linux-x86-64.",
                                        refused.loaderDiagnostic().orElseThrow().message()),
                        () ->
                                assertEquals(
                                        ToolInstallState.NOT_INSTALLED,
                                        harness.offerOf(ToolName.PERCOLATOR, "3.06.5").state(),
                                        "3.06.5 declares GLIBC_2.14 and is still installable, which"
                                                + " is what makes the alternative above true"),
                        () ->
                                assertEquals(
                                        "no offer on this host names percolator 3.07.1;"
                                                + " linux-x86-64 is offered [percolator 3.06.5"
                                                + " linux-x86-64]",
                                        assertThrows(
                                                        IllegalArgumentException.class,
                                                        () ->
                                                                harness.manager()
                                                                        .install(
                                                                                ToolName.PERCOLATOR,
                                                                                ToolVersion.parse(
                                                                                        "3.07.1"),
                                                                                progress -> {}))
                                                .getMessage(),
                                        "R-TOOL-06's rule has to hold for the button as well as"
                                                + " for the list"));
            }
        }

        @Test
        @DisplayName("a host that meets every floor exactly is offered everything")
        void aHostThatMeetsTheFloorsIsOfferedEverything() throws IOException {
            try (ToolManagerHarness harness =
                    ToolManagerHarness.onHostWithGlibc(cacheRoot, "2.34")) {
                assertAll(
                        () ->
                                assertEquals(
                                        ToolInstallState.NOT_INSTALLED,
                                        harness.offerOf(ToolName.PERCOLATOR, "3.07.1").state(),
                                        "GLIBC_2.34 exactly meets 3.07.1's declared GLIBC_2.34"
                                                + " floor: a symbol-version floor says this version"
                                                + " or newer"),
                        () ->
                                assertEquals(
                                        Optional.empty(),
                                        harness.offerOf(ToolName.PERCOLATOR, "3.07.1")
                                                .loaderDiagnostic()));
            }
        }
    }

    @Nested
    @DisplayName("registering a local binary")
    class LocalBinaries {

        private Path somewhere() {
            return cacheRoot.resolve("opt").resolve("percolator").toAbsolutePath();
        }

        @Test
        @DisplayName(
                "a registered binary joins the list under its own tool, after the managed rows")
        void aRegisteredBinaryJoinsTheList() throws IOException, ToolRegistrationException {
            try (ToolManagerHarness harness = ToolManagerHarness.onDebian12(cacheRoot)) {
                ToolOffer registered =
                        harness.manager().registerLocalBinary(ToolName.PERCOLATOR, somewhere());

                assertAll(
                        () -> assertEquals(ToolOrigin.LOCAL, registered.origin()),
                        () ->
                                assertEquals(
                                        List.of(
                                                "comet 2026.02.2 NOT_INSTALLED",
                                                "percolator 3.07.1 NOT_INSTALLED",
                                                "percolator 3.06.5 NOT_INSTALLED",
                                                "percolator 3.09" + " UNAVAILABLE_ON_THIS_PLATFORM",
                                                "percolator 3.07.1 INSTALLED",
                                                "pdv 2.7.0 NOT_INSTALLED",
                                                "limelight-converter 2.8.1 NOT_INSTALLED"),
                                        harness.describeOffers(),
                                        "the local row sits with its own tool and after the"
                                                + " managed ones, so a scientist reads the managed"
                                                + " builds first and their own file last"),
                        () ->
                                assertEquals(
                                        List.of("percolator " + somewhere()),
                                        harness.registrar().asked()));
            }
        }

        @Test
        @DisplayName("registering the same file again replaces its row rather than adding one")
        void reRegisteringReplaces() throws IOException, ToolRegistrationException {
            try (ToolManagerHarness harness = ToolManagerHarness.onDebian12(cacheRoot)) {
                harness.manager().registerLocalBinary(ToolName.PERCOLATOR, somewhere());
                harness.registrar().answeringWithVersion("3.06.5");
                harness.manager().registerLocalBinary(ToolName.PERCOLATOR, somewhere());

                List<String> local =
                        harness.manager().offers().stream()
                                .filter(offer -> offer.origin() == ToolOrigin.LOCAL)
                                .map(offer -> offer.version().text())
                                .toList();

                assertEquals(
                        List.of("3.06.5"),
                        local,
                        "R-TOOL-07 re-confirms the capability set when the file changes, so the"
                                + " point of registering again is that the answer is different;"
                                + " two rows naming one path would show a stale set beside a fresh"
                                + " one with nothing to tell them apart");
            }
        }

        /*
         * REPLACING IS KEYED ON THE PATH, and this is what says so.  A rule that removed every
         * local row whenever one was registered would pass reRegisteringReplaces above -- that
         * test registers one path twice, so "remove the matching row" and "remove them all" have
         * the same answer.  Two different files is the case where they differ, and a scientist
         * with a 3.06.5 in one directory and a 3.07.1 in another has exactly that.
         */
        @Test
        @DisplayName("registering a second, different file keeps the first row as well")
        void twoDifferentFilesAreTwoRows() throws IOException, ToolRegistrationException {
            try (ToolManagerHarness harness = ToolManagerHarness.onDebian12(cacheRoot)) {
                harness.manager().registerLocalBinary(ToolName.PERCOLATOR, somewhere());
                harness.registrar().answeringWithVersion("3.06.5");
                harness.manager()
                        .registerLocalBinary(
                                ToolName.PERCOLATOR,
                                cacheRoot
                                        .resolve("elsewhere")
                                        .resolve("percolator")
                                        .toAbsolutePath());

                assertEquals(
                        List.of("3.07.1", "3.06.5"),
                        harness.manager().offers().stream()
                                .filter(offer -> offer.origin() == ToolOrigin.LOCAL)
                                .map(offer -> offer.version().text())
                                .toList(),
                        "both files are still registered, in the order they were registered");
            }
        }

        @Test
        @DisplayName("a relative path is refused before anything is probed")
        void aRelativePathIsRefused() throws IOException {
            Path relative = Path.of("bin/percolator");
            try (ToolManagerHarness harness = ToolManagerHarness.onDebian12(cacheRoot)) {
                assertAll(
                        () ->
                                assertEquals(
                                        "executable must be an absolute path, because a relative"
                                                + " one means a different file depending on where"
                                                + " this process was started, but was:"
                                                + " bin/percolator",
                                        assertThrows(
                                                        IllegalArgumentException.class,
                                                        () ->
                                                                harness.manager()
                                                                        .registerLocalBinary(
                                                                                ToolName.PERCOLATOR,
                                                                                relative))
                                                .getMessage()),
                        () ->
                                assertEquals(
                                        List.of(),
                                        harness.registrar().asked(),
                                        "and the registrar was never asked, so nothing was run"));
            }
        }

        @Test
        @DisplayName("the registrar's own refusal reaches the caller unchanged")
        void theRegistrarsRefusalIsThePoint() throws IOException {
            try (ToolManagerHarness harness = ToolManagerHarness.onDebian12(cacheRoot)) {
                harness.registrar()
                        .refusing(
                                "The file at /opt/percolator is Percolator 3.04, and CometGUI"
                                        + " requires Percolator 3.05 or newer.");

                assertAll(
                        () ->
                                assertEquals(
                                        "The file at /opt/percolator is Percolator 3.04, and"
                                                + " CometGUI requires Percolator 3.05 or newer.",
                                        assertThrows(
                                                        ToolRegistrationException.class,
                                                        () ->
                                                                harness.manager()
                                                                        .registerLocalBinary(
                                                                                ToolName.PERCOLATOR,
                                                                                somewhere()))
                                                .getMessage()),
                        () ->
                                assertTrue(
                                        harness.manager().offers().stream()
                                                .noneMatch(
                                                        offer ->
                                                                offer.origin() == ToolOrigin.LOCAL),
                                        "a refused registration adds no row"));
            }
        }

        @Test
        @DisplayName("a registrar answering with another tool, another origin or null is refused")
        void aRegistrarThatAnswersWrongly() throws IOException {
            try (ToolManagerHarness wrongTool = ToolManagerHarness.onDebian12(cacheRoot);
                    ToolManagerHarness wrongOrigin =
                            ToolManagerHarness.onDebian12(cacheRoot.resolve("b"));
                    ToolManagerHarness nothing =
                            ToolManagerHarness.onDebian12(cacheRoot.resolve("c"))) {
                wrongTool.registrar().answeringWithTool(ToolName.COMET);
                wrongOrigin.registrar().answeringWithOrigin(ToolOrigin.MANAGED);
                nothing.registrar().answeringWithNull();
                Path binary = somewhere();

                assertAll(
                        () ->
                                assertEquals(
                                        "The file at "
                                                + binary
                                                + " was offered for registration as percolator and"
                                                + " came back registered as comet; CometGUI will"
                                                + " not file one tool's binary under another's"
                                                + " name.",
                                        assertThrows(
                                                        ToolRegistrationException.class,
                                                        () ->
                                                                wrongTool
                                                                        .manager()
                                                                        .registerLocalBinary(
                                                                                ToolName.PERCOLATOR,
                                                                                binary))
                                                .getMessage()),
                        () ->
                                assertEquals(
                                        "The file at "
                                                + binary
                                                + " came back recorded as MANAGED and a binary"
                                                + " CometGUI did not download is LOCAL; its"
                                                + " provenance is whatever you know about where it"
                                                + " came from, and recording it as managed would"
                                                + " claim a pinned checksum that does not exist.",
                                        assertThrows(
                                                        ToolRegistrationException.class,
                                                        () ->
                                                                wrongOrigin
                                                                        .manager()
                                                                        .registerLocalBinary(
                                                                                ToolName.PERCOLATOR,
                                                                                binary))
                                                .getMessage()),
                        () ->
                                assertEquals(
                                        "a registrar answers with an offer or throws; it must not"
                                                + " return null",
                                        assertThrows(
                                                        NullPointerException.class,
                                                        () ->
                                                                nothing.manager()
                                                                        .registerLocalBinary(
                                                                                ToolName.PERCOLATOR,
                                                                                binary))
                                                .getMessage()));
            }
        }
    }

    @Nested
    @DisplayName("what the manager refuses to be asked")
    class Rejections {

        @Test
        @DisplayName("every argument of every method is required, by name")
        void nullArgumentsAreRejected() throws IOException {
            try (ToolManagerHarness harness = ToolManagerHarness.onDebian12(cacheRoot)) {
                ToolVersion version = ToolVersion.parse("3.07.1");
                Path binary = cacheRoot.toAbsolutePath();
                ToolName noTool = Nulls.of(ToolName.class);
                ToolVersion noVersion = Nulls.of(ToolVersion.class);
                InstallProgressListener noListener = Nulls.of(InstallProgressListener.class);
                Path noPath = Nulls.of(Path.class);

                assertAll(
                        () ->
                                assertEquals(
                                        "tool",
                                        assertThrows(
                                                        NullPointerException.class,
                                                        () ->
                                                                harness.manager()
                                                                        .install(
                                                                                noTool,
                                                                                version,
                                                                                progress -> {}))
                                                .getMessage()),
                        () ->
                                assertEquals(
                                        "version",
                                        assertThrows(
                                                        NullPointerException.class,
                                                        () ->
                                                                harness.manager()
                                                                        .install(
                                                                                ToolName.PERCOLATOR,
                                                                                noVersion,
                                                                                progress -> {}))
                                                .getMessage()),
                        () ->
                                assertEquals(
                                        "listener",
                                        assertThrows(
                                                        NullPointerException.class,
                                                        () ->
                                                                harness.manager()
                                                                        .install(
                                                                                ToolName.PERCOLATOR,
                                                                                version,
                                                                                noListener))
                                                .getMessage()),
                        () ->
                                assertEquals(
                                        "tool",
                                        assertThrows(
                                                        NullPointerException.class,
                                                        () ->
                                                                harness.manager()
                                                                        .registerLocalBinary(
                                                                                noTool, binary))
                                                .getMessage()),
                        () ->
                                assertEquals(
                                        "executable",
                                        assertThrows(
                                                        NullPointerException.class,
                                                        () ->
                                                                harness.manager()
                                                                        .registerLocalBinary(
                                                                                ToolName.PERCOLATOR,
                                                                                noPath))
                                                .getMessage()));
            }
        }

        @Test
        @DisplayName("the offered list cannot be modified by whoever received it")
        void theListIsImmutable() throws IOException {
            try (ToolManagerHarness harness = ToolManagerHarness.onDebian12(cacheRoot)) {
                List<ToolOffer> offers = harness.manager().offers();

                assertThrows(UnsupportedOperationException.class, () -> offers.remove(0));
            }
        }

        @Test
        @DisplayName("the manager describes itself by the host it answers for")
        void describesItself() throws IOException {
            try (ToolManagerHarness harness = ToolManagerHarness.onDebian12(cacheRoot)) {
                assertEquals(
                        "ManagedToolManager[linux-x86-64, 23 artefact(s), 0 local binary/binaries,"
                                + " 0 install(s) running]",
                        harness.manager().toString());
            }
        }
    }
}
