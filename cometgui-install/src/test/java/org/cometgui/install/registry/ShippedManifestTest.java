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

package org.cometgui.install.registry;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.tools.ArtefactExecutability;
import org.cometgui.domain.tools.ArtefactKind;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.MinimumHostRequirements;
import org.cometgui.domain.tools.ToolAdvisory;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Tests over the manifest this build actually ships, {@code manifests/tools.json}, read from the
 * classpath.
 *
 * <p><strong>These guard the real file, not a fixture.</strong> A fixture proves the reader works;
 * only the real file proves that what the product will offer a scientist is true. Two of the
 * assertions below exist because the manifest is where this phase is most likely to lie:
 *
 * <ul>
 *   <li>no row for a non-Linux platform may claim {@code observed-by-execution}, because no Windows
 *       or macOS binary has ever been run anywhere in this project;
 *   <li>the words <em>verified</em>, <em>confirmed</em>, <em>proven</em> and <em>tested</em> do not
 *       appear in the file at all. Banning them outright rather than only beside an unverified row
 *       is deliberate: a rule that had to decide which row a word "applied to" would be a rule with
 *       an argument in it, and the honest word for what this project did on Linux is
 *       <em>observed</em>.
 * </ul>
 *
 * <p>And one guards an absence: Percolator 3.09 has no Linux row, and a test says so in words a
 * later reader will understand, so that nobody closes the gap by inventing a row.
 */
class ShippedManifestTest {

    private static final HostPlatform LINUX_X86_64 =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);
    private static final HostPlatform MACOS_X86_64 =
            new HostPlatform(HostOperatingSystem.MACOS, HostArchitecture.X86_64);
    private static final HostPlatform MACOS_AARCH64 =
            new HostPlatform(HostOperatingSystem.MACOS, HostArchitecture.AARCH64);
    private static final HostPlatform WINDOWS_X86_64 =
            new HostPlatform(HostOperatingSystem.WINDOWS, HostArchitecture.X86_64);

    /** Re-derived from the bytes with sha256sum, md5sum and python3 zipfile. */
    private static final String ZIP_SHA256 =
            "4d0e94af851884ff8ab6a2223e73cf28ba3ced28f6af863d4a76d541009b9dd1";

    /** Re-derived from the bytes with sha256sum, md5sum and python3 zipfile. */
    private static final String ZIP_MD5 = "9c86de1c45d2d93dae1ab43216b5864c";

    /** Re-derived from the bytes with sha256sum, md5sum and python3 zipfile. */
    private static final String BINARY_SHA256 =
            "1ba38acf09520cc89d5ed907ed0382c4d23876a7e20ec3e91cbbaa2ed431237c";

    /** Re-derived from the bytes with sha256sum, md5sum and python3 zipfile. */
    private static final String BINARY_MD5 = "0b77b68fd859639d7421f1c5e006ade5";

    /** Re-derived from the bytes with sha256sum, md5sum and python3 zipfile. */
    private static final String DEB_SHA256 =
            "ea630bbcf8db380169e2d691ea5c3f15ee1b5d81a3f54281fde2f3aa23612f9e";

    /** Re-derived from the bytes with sha256sum, md5sum and python3 zipfile. */
    private static final String DEB_MD5 = "9cc2fc3c44fc43509d402cbf69a000e1";

    /** Re-derived from the bytes with sha256sum, md5sum and python3 zipfile. */
    private static final String POUT_SHA256 =
            "21204c89234b3b255fc05009ac6b956195573fce79020863f472ab64fd986865";

    /** Re-derived from the bytes with sha256sum, md5sum and python3 zipfile. */
    private static final String POUT_MD5 = "593bb444f3685dbb9bfefdabdeaf4773";

    /** Re-derived from the bytes with sha256sum, md5sum and python3 zipfile. */
    private static final String PIN_SHA256 =
            "fa50a550ea01c9109197ad2c8c9efdcdad448fddd81c5ddcf54f13f8af280f4f";

    /** Re-derived from the bytes with sha256sum, md5sum and python3 zipfile. */
    private static final String PIN_MD5 = "373a90cd51eb055cc89bd807334cd267";

    /** A path inside the shipped manifest. */
    private static final String DEB_POUT_PATH =
            "usr/share/xml/percolator/xml-pout-1-5/percolator_out.xsd";

    /** A path inside the shipped manifest. */
    private static final String DEB_PIN_PATH =
            "usr/share/xml/percolator/xml-pin-1-3/percolator_in.xsd";

    /** A path inside the shipped manifest. */
    private static final String PKG_POUT_PATH =
            "usr/local/share/xml/percolator/xml-pout-1-5/percolator_out.xsd";

    /** A path inside the shipped manifest. */
    private static final String PKG_PIN_PATH =
            "usr/local/share/xml/percolator/xml-pin-1-3/percolator_in.xsd";

    /** A path inside the shipped manifest. */
    private static final String INSTALLED_POUT =
            "share/xml/percolator/xml-pout-1-5/percolator_out.xsd";

    /** A path inside the shipped manifest. */
    private static final String INSTALLED_PIN =
            "share/xml/percolator/xml-pin-1-3/percolator_in.xsd";

    /** The note the XSD companion carries, so a later reader knows why the schemas are there. */
    private static final String XSD_COMPANION_NOTE =
            "No portable Percolator archive ships an XSD -- every one upstream"
                    + " publishes holds exactly one member, the bare executable --"
                    + " so R-TOOL-02's two schemas are a second, small download from"
                    + " the matching noxml package. They are a provenance and"
                    + " validation asset and NOT a runtime prerequisite: phase 00 ran"
                    + " the binary with no XSD present and it wrote pout XML. The"
                    + " shipped percolator_out.xsd also declares majorVersion as"
                    + " use=\"required\" fixed=\"2\" while the 3.07.1 binary writes 3,"
                    + " so it cannot serve unmodified as a validation gate.";

    /** Why there is no Linux row for Percolator 3.09, addressed to whoever wants to add one. */
    private static final String NO_LINUX_309 =
            "DO NOT ADD A LINUX ROW FOR PERCOLATOR 3.09 TO MAKE THIS PASS. rel-3-09"
                    + " publishes no Linux portable archive at all; its .deb needs"
                    + " GLIBC_2.38 AND libboost_filesystem.so.1.83.0, which it does"
                    + " not ship, and both failures were reproduced on this project's"
                    + " Debian 12 host. R-PERC-12: absent is honest, a fabricated"
                    + " entry is not. The .deb and .rpm are kept as evidence and are"
                    + " not manifest rows.";

    /** The licence statement the Percolator records carry, from upstream's own file. */
    private static final String LICENCE_NOTE =
            "upstream license.txt at this release tag states that percolator,"
                    + " qvality, sqt2pin and mzidentml2pin are distributed under the"
                    + " Apache License 2.0";

    /** Where the XML_OUTPUT claim on the Linux 3.07.1 row comes from. */
    private static final String XML_OUTPUT_NOTE =
            "run on linux-x86-64 by phase 00 and again by phase 05 unit 0: percolator"
                    + " -X over a 64 target plus 64 decoy synthetic PIN exited 0 and"
                    + " wrote a percolator_out document carrying the"
                    + " http://per-colator.com/percolator_out/ namespace and exactly"
                    + " 64 psm elements";

    /** Where the XML_DECOY_OUTPUT claim on the same row comes from. */
    private static final String XML_DECOY_NOTE =
            "run on linux-x86-64 by phase 05 unit 0: percolator -X -Z over the same"
                    + " fixture exited 0 and wrote 128 psm elements with both"
                    + " p:decoy=true and p:decoy=false";

    /** The first advisory R-PERC-11 requires of 3.07.1. */
    private static final String PEP_REGRESSOR_TEXT =
            "Percolator 3.07.1 predates 3.08's change of the default PEP regressor to"
                    + " I-splines, so its posterior error probabilities are computed"
                    + " the older way.";

    /** The second advisory R-PERC-11 requires of 3.07.1. */
    private static final String PEP_ABOVE_ONE_TEXT =
            "Percolator 3.07.1 predates the fix for PEP values exceeding 1.0"
                    + " (upstream issue #394, fixed in 3.08.1 and 3.09), so a PEP"
                    + " above 1.0 can appear in its output.";

    /** {@code D-004}'s sentence about Comet, which the shipped manifest has to keep true. */
    private static final String COMET_RUNS_NATIVELY =
            "D-004: \"Comet still runs natively (it publishes an aarch64 macOS"
                    + " build), so only the Percolator stage is translated.\" Comet is"
                    + " the one tool that really has both builds on this host, so it is"
                    + " the only place the native-before-translated ordering key can be"
                    + " observed against the shipped data";

    private static final Pattern BANNED =
            Pattern.compile("\\b(verified|confirmed|proven|tested)\\b", Pattern.CASE_INSENSITIVE);

    private static ArtefactManifest shipped() throws IOException {
        return ArtefactManifestReader.readFromClasspath();
    }

    private static String shippedText() throws IOException {
        try (InputStream stream =
                ArtefactManifestReader.class.getResourceAsStream(
                        ArtefactManifestReader.RESOURCE_NAME)) {
            assertNotNull(stream, "the manifest must be on the classpath");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ArtefactRecord recordFor(
            ArtefactManifest manifest, ToolName tool, String version, HostPlatform platform) {
        String wanted = tool.id() + " " + version + " " + platform.id();
        for (ArtefactRecord record : manifest.artefacts()) {
            if (record.describe().equals(wanted)) {
                return record;
            }
        }
        throw new AssertionError("the shipped manifest has no record for " + wanted);
    }

    // ------------------------------------------------------------ where it comes from --

    @Test
    @DisplayName("the manifest is read from the classpath, not from a relative path")
    void theManifestComesFromTheClasspath() throws IOException {
        URL resource =
                ArtefactManifestReader.class.getResource(ArtefactManifestReader.RESOURCE_NAME);

        assertAll(
                () -> assertNotNull(resource, "tools.json must be packaged with the code"),
                () ->
                        assertTrue(
                                resource.toExternalForm().contains("/target/classes/")
                                        || resource.toExternalForm().contains(".jar!"),
                                "the manifest must come from the module's build output or its jar,"
                                        + " and not from the source tree, which only exists when"
                                        + " the working directory happens to be the repository"
                                        + " root: "
                                        + resource),
                () -> assertEquals(1, shipped().schemaVersion()),
                () -> assertEquals(23, shipped().artefacts().size()));
    }

    // ------------------------------------------------------------------- round trip --

    @Test
    @DisplayName("the Percolator 3.07.1 Linux record equals a hand-typed literal, field by field")
    void theLinuxPercolatorRecordRoundTrips() throws IOException {
        ArtefactRecord actual = recordFor(shipped(), ToolName.PERCOLATOR, "3.07.1", LINUX_X86_64);

        /*
         * Every number and digest below was typed from the measurement, not copied from the file
         * the reader just parsed: the zip is 946303 bytes with sha256 4d0e94af..., its one member
         * `percolator` is 2538632 bytes with sha256 1ba38acf..., and the .deb the two schemas come
         * from is 1852660 bytes with sha256 ea630bbc.... All of them were re-derived from the bytes
         * in scratch/phase05/artefacts with sha256sum, md5sum and python3 zipfile before this test
         * was written.
         */
        ArtefactRecord expected =
                new ArtefactRecord(
                        ToolName.PERCOLATOR,
                        ToolVersion.parse("3.07.1"),
                        "rel-3-07-01",
                        LINUX_X86_64,
                        ArtefactKind.ZIP,
                        URI.create(
                                "https://github.com/percolator/percolator/releases/download/rel-3-07-01/percolator-noxml-ubuntu-portable.zip"),
                        946303,
                        new FileHashes(ZIP_MD5, ZIP_SHA256),
                        Optional.of(
                                new ArchiveMember(
                                        "percolator",
                                        2538632,
                                        new FileHashes(BINARY_MD5, BINARY_SHA256),
                                        "bin/percolator")),
                        Optional.empty(),
                        true,
                        new ArtefactLicence(
                                "Apache-2.0",
                                URI.create(
                                        "https://raw.githubusercontent.com/percolator/percolator/rel-3-07-01/license.txt"),
                                LICENCE_NOTE),
                        List.of(expectedXsdCompanion()),
                        List.of(
                                new DeclaredCapability(
                                        ToolCapability.XML_OUTPUT,
                                        CapabilityEvidence.OBSERVED_BY_EXECUTION,
                                        XML_OUTPUT_NOTE),
                                new DeclaredCapability(
                                        ToolCapability.XML_DECOY_OUTPUT,
                                        CapabilityEvidence.OBSERVED_BY_EXECUTION,
                                        XML_DECOY_NOTE)),
                        List.of(
                                new ToolAdvisory(
                                        "percolator.3-07-1-predates-i-spline-pep-regressor",
                                        PEP_REGRESSOR_TEXT),
                                new ToolAdvisory(
                                        "percolator.3-07-1-predates-pep-above-one-fix",
                                        PEP_ABOVE_ONE_TEXT)),
                        new MinimumHostRequirements(
                                Optional.of(GlibcVersion.parse("2.34")),
                                Optional.empty(),
                                List.of()),
                        ToolVersion.parse("0.1.0"));

        ArtefactCompanion expectedCompanion = expected.companions().get(0);
        ArtefactCompanion actualCompanion = actual.companions().get(0);
        assertAll(
                () -> assertEquals(expected.tool(), actual.tool()),
                () -> assertEquals(expected.version(), actual.version()),
                () -> assertEquals("3.07.1", actual.version().text()),
                () -> assertEquals(expected.releaseTag(), actual.releaseTag()),
                () -> assertEquals(expected.platform(), actual.platform()),
                () -> assertEquals(expected.kind(), actual.kind()),
                () -> assertEquals(expected.url(), actual.url()),
                () -> assertEquals(expected.sizeBytes(), actual.sizeBytes()),
                () -> assertEquals(expected.hashes(), actual.hashes()),
                () -> assertEquals(expected.member(), actual.member()),
                () ->
                        assertEquals(
                                expected.expectedExecutablePath(), actual.expectedExecutablePath()),
                () -> assertEquals(expected.executable(), actual.executable()),
                () -> assertEquals(expected.licence(), actual.licence()),
                () -> assertEquals(expectedCompanion.id(), actualCompanion.id()),
                () -> assertEquals(expectedCompanion.kind(), actualCompanion.kind()),
                () -> assertEquals(expectedCompanion.url(), actualCompanion.url()),
                () -> assertEquals(expectedCompanion.sizeBytes(), actualCompanion.sizeBytes()),
                () -> assertEquals(expectedCompanion.hashes(), actualCompanion.hashes()),
                () ->
                        assertEquals(
                                expectedCompanion.runtimePrerequisite(),
                                actualCompanion.runtimePrerequisite()),
                () ->
                        assertEquals(
                                expectedCompanion.gatesCapability(),
                                actualCompanion.gatesCapability()),
                () -> assertEquals(expectedCompanion.note(), actualCompanion.note()),
                () -> assertEquals(expectedCompanion.members(), actualCompanion.members()),
                () -> assertEquals(expected.capabilities(), actual.capabilities()),
                () -> assertEquals(expected.advisories(), actual.advisories()),
                () ->
                        assertEquals(
                                expected.minimumHostRequirements(),
                                actual.minimumHostRequirements()),
                () ->
                        assertEquals(
                                expected.minimumCometGuiVersion(), actual.minimumCometGuiVersion()),
                () -> assertEquals(expected, actual, "and the whole record, not only its parts"));
    }

    private static ArtefactCompanion expectedXsdCompanion() {
        return new ArtefactCompanion(
                "percolator-3-07-1-xsd-schemas-from-deb",
                ArtefactKind.DEB_PAYLOAD,
                URI.create(
                        "https://github.com/percolator/percolator/releases/download/rel-3-07-01/percolator-noxml-v3-07-linux-amd64.deb"),
                1852660,
                new FileHashes(DEB_MD5, DEB_SHA256),
                false,
                Optional.empty(),
                XSD_COMPANION_NOTE,
                List.of(
                        new ArchiveMember(
                                DEB_POUT_PATH,
                                10388,
                                new FileHashes(POUT_MD5, POUT_SHA256),
                                INSTALLED_POUT),
                        new ArchiveMember(
                                DEB_PIN_PATH,
                                15457,
                                new FileHashes(PIN_MD5, PIN_SHA256),
                                INSTALLED_PIN)));
    }

    // ------------------------------------------------------------------ honesty --

    /*
     * EVERY CLAIM OF OBSERVATION, TYPED OUT BY HAND.  Until 2026-09-02 this test read "no
     * non-Linux row may claim observation", which was true because no Windows or macOS binary had
     * ever been executed anywhere in this project.  A GitHub windows-latest runner then executed
     * Percolator 3.07.1's portable noxml binary, so the sentence is false for Windows and the rule
     * has to become one about evidence rather than one about platforms.
     *
     * It is an allowlist and not a "must carry a note" rule, because a fabricated claim can carry
     * a fabricated note: that rule would grade the presence of prose rather than the truth of the
     * claim.  This list is falsifiable in both directions.  A fabricated claim anywhere fails it.
     * And a GENUINE new observation cannot be added without editing this list, which is the
     * friction that matters, because the edit is where somebody has to write down what was run,
     * where, and when.
     *
     * XML_DECOY_OUTPUT is deliberately NOT here for Windows.  The runner exercised -X and
     * --xml-in; it did not exercise -X -Z, and 200 psm elements from 200 targets is the
     * target-only shape.  Adding it would be exactly the over-reading the evidence does not
     * support.
     */
    private static final List<String> OBSERVED_BY_EXECUTION =
            List.of(
                    "comet 2026.02.2 linux-x86-64 COMPLETE_PARAMS_QUERY",
                    "comet 2026.02.2 linux-x86-64 PEPXML_OUTPUT",
                    "comet 2026.02.2 linux-x86-64 PIN_OUTPUT",
                    "percolator 3.06.5 linux-x86-64 XML_DECOY_OUTPUT",
                    "percolator 3.06.5 linux-x86-64 XML_OUTPUT",
                    "percolator 3.07.1 linux-x86-64 XML_DECOY_OUTPUT",
                    "percolator 3.07.1 linux-x86-64 XML_OUTPUT",
                    "percolator 3.07.1 windows-x86-64 XML_OUTPUT");

    @Test
    @DisplayName("exactly the capabilities somebody has watched run claim to have been observed")
    void onlyWatchedCapabilitiesClaimObservation() throws IOException {
        List<String> claiming = new ArrayList<>();
        for (ArtefactRecord record : shipped().artefacts()) {
            for (DeclaredCapability declared : record.capabilities()) {
                if (declared.isObserved()) {
                    claiming.add(record.describe() + " " + declared.capability().id());
                }
            }
        }
        claiming.sort(String::compareTo);

        assertEquals(
                OBSERVED_BY_EXECUTION,
                claiming,
                "a row claiming observed-by-execution that is not in this list is a fabricated"
                        + " claim, and a real observation missing from it means somebody upgraded"
                        + " a row without recording what was run -- the whole product's promise is"
                        + " that it says what it knows");
    }

    @Test
    @DisplayName("no macOS row claims observation, because no macOS binary has been run anywhere")
    void noMacOsRowClaimsObservation() throws IOException {
        List<String> claiming = new ArrayList<>();
        for (ArtefactRecord record : shipped().artefacts()) {
            if (record.platform().operatingSystem() != HostOperatingSystem.MACOS) {
                continue;
            }
            for (DeclaredCapability declared : record.capabilities()) {
                if (declared.isObserved()) {
                    claiming.add(record.describe() + " " + declared.capability().id());
                }
            }
        }

        assertEquals(
                List.of(),
                claiming,
                "no macOS binary has been executed anywhere in this project -- not on hardware,"
                        + " not on a runner, not under emulation -- so every macOS capability is"
                        + " an inference and must say so");
    }

    @Test
    @DisplayName("the words verified, confirmed, proven and tested appear nowhere in the manifest")
    void theBannedWordsAreAbsent() throws IOException {
        String text = shippedText();
        Matcher matcher = BANNED.matcher(text);
        List<String> found = new ArrayList<>();
        while (matcher.find()) {
            found.add(matcher.group());
        }

        assertAll(
                () ->
                        assertEquals(
                                List.of(),
                                found,
                                "these four words are what a fabricated capability claim reads"
                                        + " like; the honest word for what was done on Linux is"
                                        + " \"observed\", and \"unverified\" is an evidence"
                                        + " identifier rather than one of these words"),
                () ->
                        assertTrue(
                                text.contains("unverified"),
                                "the guard must not be passing because the file is empty or"
                                        + " because the evidence vocabulary stopped being used:"
                                        + " unverified rows are expected to exist"),
                () ->
                        assertTrue(
                                BANNED.matcher("a verified claim").find()
                                        && BANNED.matcher("CONFIRMED").find()
                                        && !BANNED.matcher("unverified").find(),
                                "the pattern must actually match the words it bans, and must not"
                                        + " match \"unverified\", which contains one of them"));
    }

    @Test
    @DisplayName("every capability the manifest claims carries a note saying where it came from")
    void everyCapabilityCarriesItsProvenance() throws IOException {
        List<Executable> assertions = new ArrayList<>();
        for (ArtefactRecord record : shipped().artefacts()) {
            for (DeclaredCapability declared : record.capabilities()) {
                assertions.add(
                        () ->
                                assertTrue(
                                        declared.note().length() > 20,
                                        record.describe()
                                                + " "
                                                + declared.capability().id()
                                                + " has a note too short to say where the evidence"
                                                + " came from: \""
                                                + declared.note()
                                                + "\""));
            }
        }
        assertAll(assertions);
    }

    // --------------------------------------------------- what is deliberately absent --

    @Test
    @DisplayName("Percolator 3.09 has no Linux artefact, and that absence is deliberate")
    void percolator309HasNoLinuxRow() throws IOException {
        ArtefactManifest manifest = shipped();

        assertAll(
                () ->
                        assertEquals(
                                List.of(),
                                manifest.select(
                                        LINUX_X86_64,
                                        ToolName.PERCOLATOR,
                                        ToolVersion.parse("3.09")),
                                NO_LINUX_309),
                () ->
                        assertEquals(
                                List.of(),
                                manifest.select(
                                        new HostPlatform(
                                                HostOperatingSystem.LINUX,
                                                HostArchitecture.AARCH64),
                                        ToolName.PERCOLATOR),
                                "upstream publishes no aarch64 Linux Percolator of any version"),
                () ->
                        assertEquals(
                                2,
                                countRecordsOf(manifest, ToolName.PERCOLATOR, "3.09"),
                                "3.09 is carried on macOS and Windows only"));
    }

    private static int countRecordsOf(ArtefactManifest manifest, ToolName tool, String version) {
        int count = 0;
        for (ArtefactRecord record : manifest.artefacts()) {
            if (record.tool() == tool && record.version().equals(ToolVersion.parse(version))) {
                count++;
            }
        }
        return count;
    }

    @Test
    @DisplayName("the three managed Percolator versions D-003 names are all carried")
    void theManagedVersionSetIsCarried() throws IOException {
        ArtefactManifest manifest = shipped();

        assertAll(
                () -> assertEquals(3, countRecordsOf(manifest, ToolName.PERCOLATOR, "3.07.1")),
                () -> assertEquals(3, countRecordsOf(manifest, ToolName.PERCOLATOR, "3.06.5")),
                () -> assertEquals(2, countRecordsOf(manifest, ToolName.PERCOLATOR, "3.09")),
                () -> assertEquals(0, countRecordsOf(manifest, ToolName.PERCOLATOR, "3.08")));
    }

    // ------------------------------------------------------------------- selection --

    @Test
    @DisplayName("on Apple silicon the x86-64 Percolator 3.07.1 is offered, marked as translated")
    void rosettaSelectionWorks() throws IOException {
        List<ArtefactSelection> offered = shipped().select(MACOS_AARCH64, ToolName.PERCOLATOR);
        List<String> described = new ArrayList<>();
        for (ArtefactSelection selection : offered) {
            described.add(
                    selection.artefact().describe()
                            + (selection.isTranslated() ? " (translated)" : " (native)"));
        }

        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        "percolator 3.09 macos-aarch64 (native)",
                                        "percolator 3.07.1 macos-x86-64 (translated)",
                                        "percolator 3.06.5 macos-x86-64 (translated)"),
                                described),
                () ->
                        assertEquals(
                                ArtefactExecutability.TRANSLATED_ROSETTA_2,
                                offered.get(1).executability(),
                                "D-004: the only XML-capable macOS Percolator upstream publishes is"
                                        + " x86-64, so on Apple silicon that stage runs under"
                                        + " Rosetta 2 and the user has to be told so in advance"));
    }

    @Test
    @DisplayName("on Apple silicon Comet is offered natively first, and its x86-64 build second")
    void cometIsOfferedNativelyOnAppleSilicon() throws IOException {
        List<ArtefactSelection> offered = shipped().select(MACOS_AARCH64, ToolName.COMET);
        List<String> described = new ArrayList<>();
        for (ArtefactSelection selection : offered) {
            described.add(
                    selection.artefact().describe()
                            + (selection.isTranslated() ? " (translated)" : " (native)"));
        }

        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        "comet 2026.02.2 macos-aarch64 (native)",
                                        "comet 2026.02.2 macos-x86-64 (translated)"),
                                described,
                                COMET_RUNS_NATIVELY),
                () ->
                        assertEquals(
                                ArtefactExecutability.NATIVE,
                                offered.get(0).executability(),
                                "the native build must be first, not merely present"),
                () ->
                        assertTrue(
                                !offered.get(0)
                                        .artefact()
                                        .url()
                                        .equals(offered.get(1).artefact().url()),
                                "two different downloads with two different digests, which"
                                        + " is why both are offered rather than collapsed"));
    }

    @Test
    @DisplayName("a JAR carried on every platform is offered once, and never as translated")
    void aPlatformIndependentDownloadIsOfferedOnce() throws IOException {
        ArtefactManifest manifest = shipped();
        List<Executable> assertions = new ArrayList<>();
        for (ToolName tool : List.of(ToolName.PDV, ToolName.LIMELIGHT_CONVERTER)) {
            List<ArtefactSelection> offered = manifest.select(MACOS_AARCH64, tool);
            assertions.add(
                    () ->
                            assertEquals(
                                    1,
                                    offered.size(),
                                    tool.id()
                                            + " is one download carried on all five platforms"
                                            + " because the specification requires an"
                                            + " operating system and an architecture in"
                                            + " every record; on Apple silicon both its own"
                                            + " row and the macos-x86-64 row are runnable,"
                                            + " and offering both would show one file"
                                            + " twice: "
                                            + describedBy(offered)));
            assertions.add(
                    () ->
                            assertEquals(
                                    ArtefactExecutability.NATIVE,
                                    offered.get(0).executability(),
                                    tool.id()
                                            + " is a JAR, and a row marked TRANSLATED_ROSETTA_2"
                                            + " would tell a scientist that a Java program runs"
                                            + " under Rosetta 2"));
        }
        assertAll(assertions);
    }

    @Test
    @DisplayName("a Linux host is never offered a macOS or a Windows artefact of any tool")
    void noForeignPlatformLeaksIntoALinuxSelection() throws IOException {
        ArtefactManifest manifest = shipped();
        List<Executable> assertions = new ArrayList<>();
        for (ToolName tool : ToolName.values()) {
            for (ArtefactSelection selection : manifest.select(LINUX_X86_64, tool)) {
                assertions.add(
                        () ->
                                assertEquals(
                                        HostOperatingSystem.LINUX,
                                        selection.artefact().platform().operatingSystem(),
                                        "offered on linux-x86-64: "
                                                + selection.artefact().describe()));
                assertions.add(
                        () ->
                                assertEquals(
                                        ArtefactExecutability.NATIVE,
                                        selection.executability(),
                                        "only Rosetta 2 translates, and it is macOS"));
            }
        }
        assertions.add(
                () ->
                        assertEquals(
                                List.of(
                                        "percolator 3.07.1 linux-x86-64",
                                        "percolator 3.06.5 linux-x86-64"),
                                describedBy(manifest.select(LINUX_X86_64, ToolName.PERCOLATOR))));
        assertAll(assertions);
    }

    private static List<String> describedBy(List<ArtefactSelection> selections) {
        List<String> described = new ArrayList<>(selections.size());
        for (ArtefactSelection selection : selections) {
            described.add(selection.artefact().describe());
        }
        return described;
    }

    // ------------------------------------------------------------------ companions --

    @Test
    @DisplayName(
            "every Windows Percolator row declares the Visual C++ runtime as a host requirement")
    void windowsPercolatorDeclaresTheVisualCppRuntime() throws IOException {
        List<Executable> assertions = new ArrayList<>();
        int rows = 0;
        for (ArtefactRecord record : shipped().artefacts()) {
            if (record.tool() != ToolName.PERCOLATOR
                    || record.platform().operatingSystem() != HostOperatingSystem.WINDOWS) {
                continue;
            }
            rows++;
            assertions.add(
                    () ->
                            assertEquals(
                                    List.of(
                                            "MSVCP140.dll",
                                            "VCRUNTIME140.dll",
                                            "VCRUNTIME140_1.dll",
                                            "VCOMP140.DLL"),
                                    record.minimumHostRequirements().requiredHostLibraries(),
                                    record.describe()
                                            + ": the portable zip ships no Visual C++ runtime, and"
                                            + " R-PLAT-03 requires its absence reported as a loader"
                                            + " failure naming the DLL rather than as \"not"
                                            + " XML-capable\""));
        }
        int windowsRows = rows;
        assertions.add(
                () ->
                        assertEquals(
                                3,
                                windowsRows,
                                "3.07.1, 3.06.5 and 3.09 each publish a Windows artefact"));
        assertAll(assertions);
    }

    @Test
    @DisplayName("Windows takes the XSD pair from the Linux .deb, and the record says why")
    void windowsTakesTheSchemasFromTheLinuxDeb() throws IOException {
        ArtefactManifest manifest = shipped();
        ArtefactCompanion windows =
                recordFor(manifest, ToolName.PERCOLATOR, "3.07.1", WINDOWS_X86_64)
                        .companions()
                        .get(0);
        ArtefactCompanion linux =
                recordFor(manifest, ToolName.PERCOLATOR, "3.07.1", LINUX_X86_64)
                        .companions()
                        .get(0);
        ArtefactCompanion macos =
                recordFor(manifest, ToolName.PERCOLATOR, "3.07.1", MACOS_X86_64)
                        .companions()
                        .get(0);

        assertAll(
                () -> assertEquals(ArtefactKind.DEB_PAYLOAD, windows.kind()),
                () -> assertEquals(linux.url(), windows.url()),
                () -> assertEquals(linux.hashes(), windows.hashes()),
                () ->
                        assertTrue(
                                windows.note()
                                        .contains(
                                                "WINDOWS TAKES THESE FROM THE LINUX .deb, which"
                                                        + " looks like a mistake and is not"),
                                "a Debian payload on a Windows machine becomes somebody's cleanup"
                                        + " unless the record itself says why: "
                                        + windows.note()),
                () -> assertEquals(ArtefactKind.PKG_PAYLOAD, macos.kind()),
                () ->
                        assertEquals(
                                List.of(PKG_POUT_PATH, PKG_PIN_PATH),
                                List.of(
                                        macos.members().get(0).path(),
                                        macos.members().get(1).path()),
                                "the .pkg prefixes the same paths with usr/local/share"),
                () ->
                        assertEquals(
                                linux.members().get(0).hashes(),
                                macos.members().get(0).hashes(),
                                "the two schemas are byte-identical across the .deb and the .pkg,"
                                        + " which is what makes taking them from either one safe"),
                () ->
                        assertTrue(
                                !windows.runtimePrerequisite()
                                        && !linux.runtimePrerequisite()
                                        && !macos.runtimePrerequisite(),
                                "R-TOOL-02 requires the registry to record that the schemas are a"
                                        + " provenance and validation asset rather than something"
                                        + " the binary needs to run"));
    }

    @Test
    @DisplayName("Comet on Windows gates THERMO_RAW_WINDOWS on its three companion libraries")
    void cometOnWindowsGatesThermoOnItsCompanions() throws IOException {
        ArtefactRecord comet = recordFor(shipped(), ToolName.COMET, "2026.02.2", WINDOWS_X86_64);
        List<String> gating = new ArrayList<>();
        for (ArtefactCompanion companion : comet.companions()) {
            if (companion
                    .gatesCapability()
                    .equals(Optional.of(ToolCapability.THERMO_RAW_WINDOWS))) {
                gating.add(companion.installedPaths().get(0));
            }
        }

        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        "bin/CometWrapper.dll",
                                        "bin/ThermoFisher.CommonCore.Data.dll",
                                        "bin/ThermoFisher.CommonCore.RawFileReader.dll"),
                                gating,
                                "R-TOOL-02: a Comet install missing these shall not advertise"
                                        + " THERMO_RAW_WINDOWS, and gatesCapability is what makes"
                                        + " that data rather than a conditional in code"),
                () ->
                        assertTrue(
                                comet.capabilities().stream()
                                        .anyMatch(
                                                declared ->
                                                        declared.capability()
                                                                == ToolCapability
                                                                        .THERMO_RAW_WINDOWS),
                                "the capability the companions gate must be one the row declares"),
                () ->
                        assertEquals(
                                List.of(),
                                otherPlatformsGating(),
                                "no platform but Windows may gate THERMO_RAW_WINDOWS"));
    }

    private static List<String> otherPlatformsGating() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (ArtefactRecord record : shipped().artefacts()) {
            if (record.platform().operatingSystem() == HostOperatingSystem.WINDOWS) {
                continue;
            }
            for (ArtefactCompanion companion : record.companions()) {
                if (companion.gatesCapability().isPresent()) {
                    offenders.add(record.describe() + " " + companion.id());
                }
            }
        }
        return offenders;
    }

    // --------------------------------------------------------------- other invariants --

    @Test
    @DisplayName("every URL in the manifest is an https GitHub release or raw URL")
    void everyUrlIsHttps() throws IOException {
        List<Executable> assertions = new ArrayList<>();
        for (ArtefactRecord record : shipped().artefacts()) {
            assertions.add(
                    () ->
                            assertEquals(
                                    "https",
                                    record.url().getScheme(),
                                    record.describe() + " " + record.url()));
            assertions.add(
                    () ->
                            assertEquals(
                                    "https",
                                    record.licence().url().getScheme(),
                                    record.describe() + " licence " + record.licence().url()));
            for (ArtefactCompanion companion : record.companions()) {
                assertions.add(
                        () ->
                                assertEquals(
                                        "https",
                                        companion.url().getScheme(),
                                        record.describe() + " " + companion.id()));
            }
        }
        assertAll(assertions);
    }

    @Test
    @DisplayName("a corrupted copy of the shipped manifest is rejected, so this guard can fail")
    void aCorruptedCopyIsRejected() throws IOException {
        String text = shippedText();
        String corrupted =
                text.replace(
                        ZIP_SHA256,
                        "4d0e94af851884ff8ab6a2223e73cf28ba3ced28f6af863d4a76d541009b9dd");

        assertAll(
                () ->
                        assertTrue(
                                !corrupted.equals(text),
                                "the injection must actually land, or this control is measuring"
                                        + " nothing"),
                () ->
                        assertTrue(
                                assertThrows(
                                                ArtefactManifestReader
                                                        .InvalidArtefactManifestException.class,
                                                () -> ArtefactManifestReader.parse(corrupted))
                                        .getMessage()
                                        .contains("must be 64 hexadecimal characters"),
                                "a digest one character short must be refused, not rounded off"));
    }
}
