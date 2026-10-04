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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.registry.ArtefactCompanion;
import org.cometgui.install.registry.ArtefactManifest;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.registry.ArtefactSelection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the Tool Manager shows, over the manifest the product ships and a real cache.
 *
 * <p>The expectation is derived from {@code manifests/tools.json} rather than written out, so that
 * a manifest change moves the test with it -- and then one test writes the answer out by hand
 * anyway, because a derivation that walks the manifest the same way the production code does could
 * agree with it about something they are both wrong about.
 */
class ManagedToolManagerOffersTest {

    @TempDir private Path temporary;

    private ToolManagerHarness harness() throws IOException {
        return ToolManagerHarness.at(temporary.resolve("cache")).build();
    }

    private static String row(ToolOffer offer) {
        return offer.tool().id()
                + " "
                + offer.version().text()
                + " "
                + offer.state()
                + " "
                + (offer.downloadSizeBytes().isPresent()
                        ? Long.toString(offer.downloadSizeBytes().getAsLong())
                        : "no-download");
    }

    /*
     * The bytes an install of this row would move, added up here from the manifest's own fields.
     * Deliberately written out a second time rather than calling the production summing method: an
     * expected value computed by the code under test cannot fail, and the hand-written table below
     * is the third statement of the same fact.
     */
    private static long everythingFetchedFor(ArtefactRecord record) {
        long bytes = record.sizeBytes();
        for (ArtefactCompanion companion : record.companions()) {
            bytes += companion.sizeBytes();
        }
        return bytes;
    }

    /*
     * The expectation, walked out of the manifest: every release of every tool, newest first, with
     * the rows this host can run.  An empty cache and a host that meets every declared floor means
     * every runnable row is NOT_INSTALLED and every release with no row here is unavailable.
     */
    private static List<String> expectedRowsFrom(ArtefactManifest manifest) {
        List<String> expected = new ArrayList<>();
        for (ToolName tool : ToolName.values()) {
            Map<ToolVersion, ArtefactRecord> releases = new LinkedHashMap<>();
            for (ArtefactRecord record : manifest.artefacts()) {
                if (record.tool() == tool) {
                    releases.putIfAbsent(record.version(), record);
                }
            }
            List<ToolVersion> newestFirst = new ArrayList<>(releases.keySet());
            newestFirst.sort(Comparator.reverseOrder());
            for (ToolVersion version : newestFirst) {
                List<ArtefactSelection> rows =
                        manifest.select(ToolManagerFixtures.LINUX, tool, version);
                if (rows.isEmpty()) {
                    expected.add(
                            tool.id()
                                    + " "
                                    + releases.get(version).version().text()
                                    + " "
                                    + ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM
                                    + " no-download");
                    continue;
                }
                for (ArtefactSelection selection : rows) {
                    expected.add(
                            tool.id()
                                    + " "
                                    + selection.artefact().version().text()
                                    + " "
                                    + ToolInstallState.NOT_INSTALLED
                                    + " "
                                    + everythingFetchedFor(selection.artefact()));
                }
            }
        }
        return expected;
    }

    @Test
    @DisplayName("offers() is the shipped manifest's selection for this host, release by release")
    void offersAreTheManifestsSelectionForThisHost() throws IOException {
        ArtefactManifest manifest = ToolManagerFixtures.shippedManifest();

        List<ToolOffer> offers = harness().manager().offers();

        assertEquals(
                expectedRowsFrom(manifest),
                offers.stream().map(ManagedToolManagerOffersTest::row).toList());
    }

    @Test
    @DisplayName("and on linux-x86-64 those rows are, written out by hand, exactly these seven")
    void theRowsAScientistSeesOnLinux() throws IOException {
        /*
         * Hand-typed from manifests/tools.json, not derived: the test above walks the manifest the
         * same way the production code does, so it would agree with a shared misreading.  Seven
         * rows -- Comet's two releases, one Linux build each and the newer first (D-010 makes
         * 2026.03.0 the default and keeps 2026.02.2 offered), Percolator's three releases of which
         * 3.09 publishes nothing for Linux at all, PDV and the converter.
         *
         * THE TWO PERCOLATOR FIGURES ARE SUMS AND THE OTHERS ARE NOT, which is why they are written
         * out.  3.07.1 is 946 303 + 1 852 660 = 2 798 963 and 3.06.5 is 917 285 + 1 882 696 =
         * 2 799 981, because each fetches the .deb its two XSDs are taken out of.  Comet, PDV and
         * the converter name no companion, so their rows are one file and their numbers are the
         * artefact lengths unchanged -- PDV in particular is still 103 407 417.
         */
        List<String> expected =
                List.of(
                        "comet 2026.03.0 NOT_INSTALLED 7077008",
                        "comet 2026.02.2 NOT_INSTALLED 7014400",
                        "percolator 3.09 UNAVAILABLE_ON_THIS_PLATFORM no-download",
                        "percolator 3.07.1 NOT_INSTALLED 2798963",
                        "percolator 3.06.5 NOT_INSTALLED 2799981",
                        "pdv 2.7.0 NOT_INSTALLED 103407417",
                        "limelight-converter 2.8.1 NOT_INSTALLED 2762075");

        List<ToolOffer> offers = harness().manager().offers();

        assertEquals(expected, offers.stream().map(ManagedToolManagerOffersTest::row).toList());
    }

    @Test
    @DisplayName(
            "the first Comet the Tool Manager offers is the newest release the manifest names,"
                    + " and every older release is still offered after it")
    void theDefaultCometIsTheNewestReleaseTheManifestNames() throws IOException {
        /*
         * D-010 (2026-10-04): 2026.03.0 is the default verified Comet and 2026.02.2 stays offered.
         * There is no "default" field anywhere: the default is the first row the port offers for
         * the tool, and that order is the manifest's releases newest first.  So the expectation is
         * derived from the manifest -- the newest Comet version among its rows, by ToolVersion's
         * numeric order -- and then held to the decision's own words, so that a manifest that
         * stopped naming 2026.03.0, or an order that stopped being newest-first, both go red.
         */
        ArtefactManifest manifest = ToolManagerFixtures.shippedManifest();
        List<ToolVersion> cometReleases =
                manifest.artefacts().stream()
                        .filter(record -> record.tool() == ToolName.COMET)
                        .map(ArtefactRecord::version)
                        .distinct()
                        .sorted(Comparator.reverseOrder())
                        .toList();

        List<ToolVersion> offered =
                harness().manager().offers().stream()
                        .filter(offer -> offer.tool() == ToolName.COMET)
                        .map(ToolOffer::version)
                        .toList();

        assertAll(
                () ->
                        assertEquals(
                                cometReleases,
                                offered,
                                "every Comet release the manifest names is offered on"
                                        + " linux-x86-64, newest first"),
                () ->
                        assertEquals(
                                "2026.03.0",
                                offered.get(0).text(),
                                "D-010: the default -- the first Comet offered -- is 2026.03.0"),
                () ->
                        assertEquals(
                                cometReleases.get(0),
                                offered.get(0),
                                "and it is the default because it is the newest, not because"
                                        + " anything names it"),
                () ->
                        assertTrue(
                                offered.contains(ToolVersion.parse("2026.02.2")),
                                "D-010: 2026.02.2 stays in the release matrix: " + offered));
    }

    @Test
    @DisplayName("PDV's 103 407 417 bytes reach the port from the shipped manifest")
    void pdvCarriesItsDownloadSize() throws IOException {
        ArtefactRecord pdv =
                ToolManagerFixtures.record(ToolName.PDV, "2.7.0", ToolManagerFixtures.LINUX);

        ToolOffer offer = only(harness().manager().offers(), ToolName.PDV);

        assertAll(
                () ->
                        assertEquals(
                                103_407_417L,
                                offer.downloadSizeBytes().getAsLong(),
                                "the one transfer the phase document singles out for cancellation"
                                        + " testing, and the Tool Manager has to be able to say how"
                                        + " large it is"),
                () ->
                        assertEquals(
                                103_407_417L,
                                pdv.sizeBytes(),
                                "and that is the length manifests/tools.json pins for it"),
                () ->
                        assertEquals(
                                List.of(),
                                pdv.companions(),
                                "PDV names no companion, which is why the whole transfer and the"
                                        + " artefact are the same number here and are not the same"
                                        + " number for Percolator"),
                () -> assertEquals(ToolOrigin.MANAGED, offer.origin()),
                () -> assertEquals(Optional.empty(), offer.installedPath()));
    }

    @Test
    @DisplayName("a release with no artefact for this platform is shown, not hidden")
    void aReleaseWithNoArtefactHereIsShownAsUnavailable() throws IOException {
        ArtefactManifest manifest = ToolManagerFixtures.shippedManifest();
        ToolVersion percolator309 = ToolVersion.parse("3.09");

        ToolOffer offer = one(harness().manager().offers(), ToolName.PERCOLATOR, percolator309);

        assertAll(
                () ->
                        assertEquals(
                                List.of(),
                                manifest.select(
                                        ToolManagerFixtures.LINUX,
                                        ToolName.PERCOLATOR,
                                        percolator309),
                                "the manifest publishes no Linux row for 3.09, which is the fact"
                                        + " this row exists to report"),
                () ->
                        assertEquals(
                                ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM,
                                offer.state(),
                                "R-PERC-01 forbids promising a build that cannot run, not the"
                                        + " admission that it exists"),
                () -> assertEquals(OptionalLong.empty(), offer.downloadSizeBytes()),
                () -> assertEquals(List.of(), offer.capabilities()),
                () -> assertEquals(Optional.empty(), offer.loaderDiagnostic()));
    }

    @Test
    @DisplayName("a build beyond this host's floors is shown with the R-PLAT-03 diagnostic")
    void aBuildBeyondThisHostsFloorsIsShownWithTheDiagnostic() throws IOException {
        ArtefactRecord percolator3071 =
                ToolManagerFixtures.record(
                        ToolName.PERCOLATOR, "3.07.1", ToolManagerFixtures.LINUX);
        ToolManagerHarness harness =
                ToolManagerHarness.at(temporary.resolve("cache"))
                        .on(ToolManagerFixtures.CENTOS_7)
                        .build();

        List<ToolOffer> offers = harness.manager().offers();
        ToolOffer refused = one(offers, ToolName.PERCOLATOR, ToolVersion.parse("3.07.1"));
        ToolOffer alternative = one(offers, ToolName.PERCOLATOR, ToolVersion.parse("3.06.5"));

        assertAll(
                () ->
                        assertEquals(
                                "2.34",
                                percolator3071
                                        .minimumHostRequirements()
                                        .minimumGlibc()
                                        .orElseThrow()
                                        .text(),
                                "the floor the manifest declares, which the diagnostic quotes"),
                () ->
                        assertEquals(
                                ToolInstallState.HOST_REQUIREMENTS_NOT_MET,
                                refused.state(),
                                "the artefact is real; this host cannot run it (R-TOOL-03)"),
                () ->
                        assertEquals(
                                "This build cannot run on this host: libc.so.6 on this host"
                                        + " does not provide a symbol version this build needs."
                                        + " Required: GLIBC_2.34. Available on this host:"
                                        + " GLIBC_2.17. Alternatives: percolator 3.06.5"
                                        + " linux-x86-64.",
                                refused.loaderDiagnostic().orElseThrow().message()),
                () ->
                        assertEquals(
                                OptionalLong.of(2_798_963L),
                                refused.downloadSizeBytes(),
                                "the artefact and its companion exist and their lengths are known,"
                                        + " even though this host may not fetch them"),
                () ->
                        assertEquals(
                                ToolInstallState.NOT_INSTALLED,
                                alternative.state(),
                                "and the build the diagnostic names instead is offered"),
                () ->
                        assertEquals(
                                List.of(),
                                harness.loadability().asked(),
                                "and nothing was executed to establish any of it: the advance"
                                        + " check answered from the manifest's declared floor and"
                                        + " this host's measured version, which is what R-TOOL-03"
                                        + " is for"));
    }

    @Test
    @DisplayName("a build nobody has installed is never asked whether its binary starts")
    void aBuildThatIsNotInstalledIsNeverAskedWhetherItStarts() throws IOException {
        ToolManagerHarness.RecordedLoadability refusingEverything =
                new ToolManagerHarness.RecordedLoadability().mustNotBeAsked();
        ToolManagerHarness harness =
                ToolManagerHarness.at(temporary.resolve("cache"))
                        .loadability(refusingEverything)
                        .build();

        List<ToolOffer> offers = harness.manager().offers();

        assertAll(
                () -> assertEquals(7, offers.size()),
                () -> assertEquals(List.of(), refusingEverything.asked()));
    }

    @Test
    @DisplayName("capabilities come from the manifest, with the evidence the manifest recorded")
    void capabilitiesComeFromTheManifestUntilSomethingIsInstalled() throws IOException {
        ArtefactRecord percolator3071 =
                ToolManagerFixtures.record(
                        ToolName.PERCOLATOR, "3.07.1", ToolManagerFixtures.LINUX);

        ToolOffer offer =
                one(harness().manager().offers(), ToolName.PERCOLATOR, ToolVersion.parse("3.07.1"));

        assertAll(
                () ->
                        assertEquals(
                                List.of("XML_OUTPUT", "XML_DECOY_OUTPUT"),
                                offer.capabilities().stream()
                                        .map(declared -> declared.capability().id())
                                        .toList()),
                () ->
                        assertEquals(
                                percolator3071.capabilities(),
                                offer.capabilities(),
                                "the manifest's own claims, evidence and note included, until a"
                                        + " probe has said otherwise (R-TOOL-07)"),
                () ->
                        assertEquals(
                                percolator3071.advisories(),
                                offer.advisories(),
                                "and the version advisories R-PERC-11 requires at selection time"),
                () ->
                        assertTrue(
                                offer.capabilities().stream()
                                        .allMatch(DeclaredCapability::isObserved),
                                "this project executed the Linux build, so its manifest rows say"
                                        + " observed-by-execution"));
    }

    @Test
    @DisplayName("installing a release this platform does not publish is refused, saying so")
    void installingAReleaseWithNoArtefactHereIsRefused() throws IOException {
        ToolManagerHarness harness = harness();

        IllegalArgumentException refused =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                harness.manager()
                                        .install(
                                                ToolName.PERCOLATOR,
                                                ToolVersion.parse("3.09"),
                                                progress -> {}));

        assertEquals(
                "no offer names percolator 3.09 on linux-x86-64: the manifest publishes no"
                        + " artefact of that release for this platform, which is what"
                        + " UNAVAILABLE_ON_THIS_PLATFORM means on the row",
                refused.getMessage());
    }

    @Test
    @DisplayName("installing a build beyond this host's floors is refused with the diagnostic")
    void installingABuildBeyondThisHostsFloorsIsRefused() throws IOException {
        ToolManagerHarness harness =
                ToolManagerHarness.at(temporary.resolve("cache"))
                        .on(ToolManagerFixtures.CENTOS_7)
                        .build();

        IllegalArgumentException refused =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                harness.manager()
                                        .install(
                                                ToolName.PERCOLATOR,
                                                ToolVersion.parse("3.07.1"),
                                                progress -> {}));

        assertEquals(
                "no offer names percolator 3.07.1 on linux-x86-64 as something this host can"
                        + " install: This build cannot run on this host: libc.so.6 on this host"
                        + " does not provide a symbol version this build needs. Required:"
                        + " GLIBC_2.34. Available on this host: GLIBC_2.17. Alternatives:"
                        + " percolator 3.06.5 linux-x86-64.",
                refused.getMessage());
    }

    private static ToolOffer only(List<ToolOffer> offers, ToolName tool) {
        List<ToolOffer> matching = offers.stream().filter(offer -> offer.tool() == tool).toList();
        assertEquals(
                1, matching.size(), () -> "one row expected for " + tool.id() + ": " + matching);
        return matching.get(0);
    }

    private static ToolOffer one(List<ToolOffer> offers, ToolName tool, ToolVersion version) {
        List<ToolOffer> matching =
                offers.stream()
                        .filter(offer -> offer.tool() == tool)
                        .filter(offer -> offer.version().equals(version))
                        .toList();
        assertEquals(
                1,
                matching.size(),
                () -> "one row expected for " + tool.id() + " " + version.text() + ": " + matching);
        return matching.get(0);
    }
}
