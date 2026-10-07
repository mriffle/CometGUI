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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolRegistrationException;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.tools.testing.UpstreamArtefacts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code R-PERC-02}, executed: the real Percolator binaries, through the real process service.
 *
 * <p>This is where the capability probe stops being a rule about strings. The binaries come out of
 * the real upstream archives in the gitignored mirror, they are checked against the artefact
 * manifest's own {@code memberSha256} before anything is run, and every number asserted below was
 * read out of the file a real Percolator wrote on 2026-09-03.
 *
 * <h2>The negative control, and why 64 is the number</h2>
 *
 * <p>{@link #theFixtureSizeIsTheOnlyDifference} runs <em>this same probe against this same
 * binary</em> at 8 target rows and at 64, and watches the verdict change from nothing to every
 * capability. That is what makes 64 a measurement rather than a magic number, and it is why the
 * over-small fixture is called a false negative: the binary is fully capable in both runs.
 *
 * <p>The size is not the only thing that matters, and this is worth stating because the phase's own
 * records read as though 8 plus 8 always fails. It does not. Fifty seeds of {@link SyntheticPin}
 * were run through the 3.07.1 binary at both sizes on 2026-09-03: <strong>ten of the fifty aborted
 * at 8 plus 8, and none of the fifty aborted at 64 plus 64</strong>. So an under-sized fixture does
 * not make the probe wrong, it makes the probe <em>unreliable</em> -- about one draw in five would
 * report a capable binary as incapable, and the Limelight stage would appear or vanish depending on
 * the seed. {@link SyntheticPin#PROBE_SEED} is one of the ten.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "these are the real Linux ELF binaries from this phase's artefact mirror; no"
                        + " Windows or macOS Percolator binary has ever been executed anywhere in"
                        + " this project, which is recorded as residue rather than covered here")
class PercolatorRealBinaryTest {

    /** The namespace both real binaries write, hand-typed from the documents they produced. */
    private static final String OBSERVED_NAMESPACE = "http://per-colator.com/percolator_out/15";

    /** The whole line the 3.07.1 binary printed on standard error when 8 plus 8 aborted it. */
    private static final String ABORT_MESSAGE =
            "Exception caught: Error: median decoy score <= score at 1% FDR. Cannot rescale scores"
                    + " to merge cross validation bins, try lowering --trainFDR.";

    static final String ZIP_3071 = "rel-3-07-01__percolator-noxml-ubuntu-portable.zip";
    private static final String ZIP_3065 = "rel-3-06-05__percolator-noxml-linux-portable.zip";

    /** The manifest's {@code memberSha256} for the 3.07.1 Linux portable zip, hand-typed. */
    static final String SHA256_3071 =
            "1ba38acf09520cc89d5ed907ed0382c4d23876a7e20ec3e91cbbaa2ed431237c";

    /** The manifest's {@code memberSha256} for the 3.06.5 Linux portable zip, hand-typed. */
    private static final String SHA256_3065 =
            "1f1034b4f265e0b45efe93f55eb4fdc09c698f1b916dde910889f9057a9cb76e";

    /** Unit 0's digest for the 3.09 Debian payload, which does not load on this host. */
    private static final String SHA256_309_PAYLOAD =
            "1f067b5d438a3a88be8a88f636844baea824e239fd2c5c053462ae56fd0e7c15";

    static final HostPlatform HOST =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    /*
     * PERCOLATOR 3.09 ON LINUX, AS AN UNMANAGED BINARY.  There is no manifest row for it (D-003:
     * absent is honest) -- its .deb needs GLIBC_2.38 and its .rpm needs Boost libraries it does not
     * ship -- so this project runs it the way a user would have to: the upstream .rpm's executable
     * with Boost 1.66 beside it, launched through a wrapper that sets LD_LIBRARY_PATH, registered
     * as a local binary.  Every digest below was measured on 2026-10-07 and is hand-typed.
     */
    static final String DIR_309 = "scratch/percolator/3.09";
    static final String WRAPPER_309 = DIR_309 + "/run-percolator-3.09.sh";
    static final String SHA256_WRAPPER_309 =
            "fe1b018a3afb0f97d6ff79e18264e282924f274d1ae7c71ff64d3825538f3e90";
    static final String BINARY_309 = DIR_309 + "/linux-x86_64-from-rpm/usr/bin/percolator";
    static final String SHA256_BINARY_309 =
            "c31f613929f06ef0f519623ed7ffd39253ce23d3681cc4cd825791c15e49ba26";
    static final String BOOST_FILESYSTEM_309 =
            DIR_309 + "/deps/usr/lib64/libboost_filesystem.so.1.66.0";
    static final String SHA256_BOOST_FILESYSTEM_309 =
            "18f3934a0ecb5d465fb369914626254400799e9302813dfe630e763ee437a3bd";
    static final String BOOST_SYSTEM_309 = DIR_309 + "/deps/usr/lib64/libboost_system.so.1.66.0";
    static final String SHA256_BOOST_SYSTEM_309 =
            "ded43dd2101680377021387e8bf7c4c8db07912deac657c53b1e62337691b06c";

    /** Every Percolator capability, hand-typed: what a fully capable build probes to. */
    static final Set<ToolCapability> EVERY_CAPABILITY =
            Set.of(
                    ToolCapability.XML_OUTPUT,
                    ToolCapability.XML_DECOY_OUTPUT,
                    ToolCapability.PSM_TSV_OUTPUT,
                    ToolCapability.PEPTIDE_TSV_OUTPUT,
                    ToolCapability.DECOY_OUTPUT,
                    ToolCapability.WEIGHTS_OUTPUT,
                    ToolCapability.THREAD_OPTION,
                    ToolCapability.SEED_OPTION,
                    ToolCapability.TEST_FDR_OPTION,
                    ToolCapability.TRAIN_FDR_OPTION,
                    ToolCapability.MAX_ITERATIONS_OPTION);

    static final ToolVersion V3071 = ToolVersion.parse("3.07.1");
    private static final ToolVersion V3065 = ToolVersion.parse("3.06.5");
    static final ToolVersion V309 = ToolVersion.parse("3.09");

    static ToolRunner runner() {
        return new ToolRunner(new ProcessService(Clock.systemUTC()), Duration.ofSeconds(120));
    }

    static Path stage(Path directory, String archive, String expectedSha256) throws IOException {
        Path binary =
                UpstreamArtefacts.executableMember(
                        archive, "percolator", directory.resolve("bin").resolve("percolator"));
        assertEquals(
                expectedSha256,
                UpstreamArtefacts.sha256(binary),
                () -> "the member taken out of " + archive + " is not the bytes the manifest pins");
        return binary;
    }

    private static Path payload309() {
        Path binary =
                UpstreamArtefacts.repositoryRoot()
                        .resolve("scratch/phase05/extract/deb-3.09/usr/bin/percolator");
        if (!Files.isRegularFile(binary)) {
            throw new AssertionError(
                    "the Percolator 3.09 Debian payload is not at "
                            + binary
                            + ". It is a gitignored working file extracted from the real .deb in"
                            + " the mirror, and it is the binary that does NOT load on this host."
                            + " This test fails rather than skips, because a probe suite that stops"
                            + " running real binaries stops proving anything.");
        }
        return binary;
    }

    private static ToolRunOutcome run(Path executable, Path workspace, List<String> arguments)
            throws IOException {
        List<String> argv = new ArrayList<>();
        argv.add(executable.toString());
        argv.addAll(arguments);
        return runner().run(new ToolCommand(argv, workspace, Map.of()));
    }

    /* Probes once, and prints what was found and how long it took, for the record. */
    private static Set<ToolCapability> probeAndReport(
            String label, ToolVersion version, Path binary) throws IOException {
        long started = System.nanoTime();
        Set<ToolCapability> observed =
                new PercolatorCapabilityProbe(runner())
                        .probe(ToolName.PERCOLATOR, version, HOST, binary);
        long elapsed = System.nanoTime() - started;
        System.out.printf(
                Locale.ROOT,
                "PROBED %s in %.2f s: %s%n",
                label,
                elapsed / 1.0e9,
                observed.stream().map(ToolCapability::id).sorted().toList());
        return observed;
    }

    @Test
    @DisplayName(
            "the real 3.07.1 portable binary probes to EVERY capability, XML and tabular alike")
    void theRealBinaryIsFullyCapable(@TempDir Path directory) throws IOException {
        Path binary = stage(directory, ZIP_3071, SHA256_3071);

        Set<ToolCapability> observed = probeAndReport("3.07.1 linux portable", V3071, binary);

        assertEquals(
                EVERY_CAPABILITY,
                observed,
                "this is the exact binary scripts/feasibility/probe_xml_capability.py gets wrong:"
                        + " the noxml build, whose help text is byte-identical to the"
                        + " XML_SUPPORT=ON twin's and which writes pout XML anyway -- and it writes"
                        + " every tab-separated artefact and accepts every option too");
    }

    @Test
    @DisplayName("the real 3.06.5 portable binary probes to every capability too, measured")
    void theOldestManagedBinary(@TempDir Path directory) throws IOException {
        Path binary = stage(directory, ZIP_3065, SHA256_3065);

        assertEquals(
                EVERY_CAPABILITY,
                probeAndReport("3.06.5 linux portable", V3065, binary),
                "measured on 2026-10-07, then pinned: 3.06.5 accepted every option and wrote every"
                        + " artefact the probe asks for");
    }

    static Path fixture309(String relative, String expectedSha256) throws IOException {
        Path file = UpstreamArtefacts.repositoryRoot().resolve(relative);
        if (!Files.isRegularFile(file)) {
            throw new AssertionError(
                    "the Percolator 3.09 Linux fixture file "
                            + file
                            + " is missing. It is gitignored scratch, rebuilt by hand: extract"
                            + " usr/bin/percolator from upstream's"
                            + " percolator-v3-09-linux-x86_64.rpm (scratch/percolator/archives,"
                            + " sha256 45f08388...), and"
                            + " libboost_filesystem.so.1.66.0 and libboost_system.so.1.66.0 from"
                            + " CentOS 8.5's boost-filesystem and boost-system 1.66.0-10.el8"
                            + " packages, with scripts/feasibility/extract_rpm.py -- nothing is"
                            + " installed on the host; the wrapper's own header records the recipe."
                            + " This test fails rather than skips, because the 3.09 verdict is the"
                            + " one the whole phase turns on.");
        }
        assertEquals(
                expectedSha256,
                UpstreamArtefacts.sha256(file),
                () -> file + " is not the bytes this test was measured against");
        return file;
    }

    private static HashService hashes() {
        return file -> new FileHashes(digest(file, "MD5"), UpstreamArtefacts.sha256(file));
    }

    private static String digest(Path file, String algorithm) throws IOException {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance(algorithm).digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    @Test
    @DisplayName(
            "3.09, REGISTERED AS A LOCAL BINARY: every tabular capability, and NEITHER XML one")
    void percolator309HasNoXml() throws IOException, ToolRegistrationException {
        Path wrapper = fixture309(WRAPPER_309, SHA256_WRAPPER_309);
        fixture309(BINARY_309, SHA256_BINARY_309);
        fixture309(BOOST_FILESYSTEM_309, SHA256_BOOST_FILESYSTEM_309);
        fixture309(BOOST_SYSTEM_309, SHA256_BOOST_SYSTEM_309);
        ToolRunner runner = runner();

        long started = System.nanoTime();
        RegisteredLocalBinary registered =
                new LocalPercolatorRegistration(
                                runner, new PercolatorCapabilityProbe(runner), hashes(), HOST)
                        .register(wrapper);
        long elapsed = System.nanoTime() - started;
        ToolOffer offer = registered.offer();
        List<ToolCapability> observed =
                offer.capabilities().stream().map(DeclaredCapability::capability).toList();
        System.out.printf(
                Locale.ROOT,
                "PROBED 3.09 linux rpm (registered local) in %.2f s, registration included: %s%n",
                elapsed / 1.0e9,
                observed.stream().map(ToolCapability::id).sorted().toList());

        assertAll(
                () -> assertEquals("3.09.0", offer.version().text(), "read from its own banner"),
                () -> assertEquals(V309, offer.version()),
                () -> assertEquals(ToolOrigin.LOCAL, offer.origin()),
                () ->
                        assertEquals(
                                List.of(
                                        ToolCapability.PSM_TSV_OUTPUT,
                                        ToolCapability.PEPTIDE_TSV_OUTPUT,
                                        ToolCapability.DECOY_OUTPUT,
                                        ToolCapability.WEIGHTS_OUTPUT,
                                        ToolCapability.THREAD_OPTION,
                                        ToolCapability.SEED_OPTION,
                                        ToolCapability.TEST_FDR_OPTION,
                                        ToolCapability.TRAIN_FDR_OPTION,
                                        ToolCapability.MAX_ITERATIONS_OPTION),
                                observed),
                () ->
                        assertFalse(
                                observed.contains(ToolCapability.XML_OUTPUT),
                                "XML_OUTPUT absent for 3.09: it removed XML I/O"),
                () ->
                        assertFalse(
                                observed.contains(ToolCapability.XML_DECOY_OUTPUT),
                                "and XML_DECOY_OUTPUT with it"),
                () ->
                        assertEquals(
                                SHA256_WRAPPER_309,
                                registered.checksums().sha256(),
                                "the registered file is the wrapper, and its checksum is the"
                                        + " wrapper's: the ELF and the two libraries it loads are"
                                        + " pinned above, not by the registration"));
    }

    @Test
    @DisplayName("the document the probe judged: 64 psm, the full namespace, no decoy attribute")
    void theTargetOnlyDocument(@TempDir Path directory) throws IOException {
        Path binary = stage(directory, ZIP_3071, SHA256_3071);
        Path pin = SyntheticPin.writeForCapabilityProbe(directory);
        Path written = directory.resolve("targets.xml");

        ToolRunOutcome outcome =
                run(binary, directory, List.of("-X", written.toString(), pin.toString()));
        PoutDocument document = PoutDocument.read(written);

        assertAll(
                () -> assertEquals(OptionalInt.of(0), outcome.exitCode()),
                () -> assertEquals("percolator_output", document.rootElement()),
                () -> assertEquals(OBSERVED_NAMESPACE, document.namespace()),
                () -> assertTrue(document.isPercolatorOutput()),
                () -> assertEquals(64, document.psmCount()),
                () -> assertEquals(64, document.peptideCount()),
                () -> assertEquals(Set.of(), document.psmDecoyValues()),
                () -> assertTrue(Files.size(written) > 40000, "wrote " + Files.size(written)));
    }

    @Test
    @DisplayName("-X -Z writes 128 psm carrying both decoy values, which is the second capability")
    void theDecoyDocument(@TempDir Path directory) throws IOException {
        Path binary = stage(directory, ZIP_3071, SHA256_3071);
        Path pin = SyntheticPin.writeForCapabilityProbe(directory);
        Path written = directory.resolve("decoys.xml");

        ToolRunOutcome outcome =
                run(binary, directory, List.of("-X", written.toString(), "-Z", pin.toString()));
        PoutDocument document = PoutDocument.read(written);

        assertAll(
                () -> assertEquals(OptionalInt.of(0), outcome.exitCode()),
                () -> assertEquals(OBSERVED_NAMESPACE, document.namespace()),
                () -> assertEquals(128, document.psmCount()),
                () -> assertEquals(Set.of("false", "true"), document.psmDecoyValues()),
                () -> assertTrue(document.hasBothDecoyValues()));
    }

    @Test
    @DisplayName("3.06.5 writes the same namespace as 3.07.1, checked rather than assumed")
    void theOtherRealBinary(@TempDir Path directory) throws IOException {
        Path binary = stage(directory, ZIP_3065, SHA256_3065);
        Path pin = SyntheticPin.writeForCapabilityProbe(directory);
        Path written = directory.resolve("targets-3065.xml");

        run(binary, directory, List.of("-X", written.toString(), pin.toString()));
        PoutDocument document = PoutDocument.read(written);
        Set<ToolCapability> observed =
                new PercolatorCapabilityProbe(runner())
                        .probe(ToolName.PERCOLATOR, V3065, HOST, binary);

        assertAll(
                () -> assertEquals(OBSERVED_NAMESPACE, document.namespace()),
                () -> assertEquals(64, document.psmCount()),
                () -> assertEquals(EVERY_CAPABILITY, observed));
    }

    @Test
    @DisplayName("THE NEGATIVE CONTROL: 8 plus 8 aborts, leaves a ZERO-BYTE file, and reads absent")
    void theFixtureSizeIsTheOnlyDifference(@TempDir Path directory) throws IOException {
        Path binary = stage(directory, ZIP_3071, SHA256_3071);
        Path small = SyntheticPin.write(directory, 8, SyntheticPin.PROBE_SEED);
        Path written = directory.resolve("aborted.xml");

        ToolRunOutcome aborted =
                run(binary, directory, List.of("-X", written.toString(), small.toString()));
        Set<ToolCapability> atEight =
                new PercolatorCapabilityProbe(runner(), 8)
                        .probe(ToolName.PERCOLATOR, V3071, HOST, binary);
        Set<ToolCapability> atSixtyFour =
                new PercolatorCapabilityProbe(runner(), 64)
                        .probe(ToolName.PERCOLATOR, V3071, HOST, binary);

        assertAll(
                () -> assertEquals(OptionalInt.of(1), aborted.exitCode()),
                () ->
                        assertTrue(
                                aborted.standardError().contains(ABORT_MESSAGE),
                                "the documented false negative, hand-typed: "
                                        + aborted.joinedOutput()),
                () ->
                        assertTrue(
                                Files.isRegularFile(written),
                                "the trap is that the file EXISTS after the abort"),
                () ->
                        assertEquals(
                                0L,
                                Files.size(written),
                                "and it is zero bytes, which is why \"the file exists\" is not a"
                                        + " probe condition"),
                () ->
                        assertEquals(
                                "Percolator left a ZERO-BYTE file at "
                                        + written
                                        + ": the file exists and says nothing, which is what an"
                                        + " aborted run leaves behind, so its existence is not"
                                        + " evidence of XML output",
                                assertThrows(IOException.class, () -> PoutDocument.read(written))
                                        .getMessage()),
                () ->
                        assertEquals(
                                Set.of(),
                                atEight,
                                "a fully capable binary reported as having no capability at"
                                        + " all: the false negative R-PERC-02 warns about, which"
                                        + " reaches every capability because every run is over the"
                                        + " same fixture"),
                () ->
                        assertEquals(
                                EVERY_CAPABILITY,
                                atSixtyFour,
                                "same probe, same binary, 64 rows instead of 8: the fixture size is"
                                        + " the only difference between these two verdicts"),
                () -> assertNotEquals(atEight, atSixtyFour));
    }

    @Test
    @DisplayName("a binary that does not load is a refusal, never an empty capability set")
    void aLoaderFailureIsNotAnAbsence(@TempDir Path directory) throws IOException {
        Path payload = payload309();
        assertEquals(
                SHA256_309_PAYLOAD,
                UpstreamArtefacts.sha256(payload),
                "the 3.09 Debian payload is not the bytes unit 0 extracted");
        Path staged = directory.resolve("bin").resolve("percolator");
        Files.createDirectories(
                Objects.requireNonNull(staged.getParent(), "the staged path has a directory"));
        Files.copy(payload, staged);
        Files.setPosixFilePermissions(
                staged,
                Set.of(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE));

        IOException refused =
                assertThrows(
                        IOException.class,
                        () ->
                                new PercolatorCapabilityProbe(runner())
                                        .probe(ToolName.PERCOLATOR, V309, HOST, staged));

        assertAll(
                () ->
                        assertTrue(
                                refused.getMessage()
                                        .startsWith(
                                                "Percolator 3.09 at "
                                                        + staged
                                                        + " never printed its version banner, so"
                                                        + " it did not run far enough to be asked"
                                                        + " what it can do; this is a loadability"
                                                        + " failure and must not be reported as a"
                                                        + " missing capability."),
                                refused.getMessage()),
                () ->
                        assertTrue(
                                refused.getMessage().contains("libboost_filesystem.so.1.83.0"),
                                "the loader's own complaint has to reach the message, or nobody"
                                        + " can act on it: "
                                        + refused.getMessage()),
                () ->
                        assertTrue(
                                refused.getMessage().contains("It exited 127"),
                                refused.getMessage()));
    }

    @Test
    @DisplayName("the help text is on STDERR and lists both XML options, so it discriminates none")
    void helpTextIsNotAProbe(@TempDir Path directory) throws IOException {
        Path binary = stage(directory, ZIP_3071, SHA256_3071);

        ToolRunOutcome outcome = run(binary, directory, List.of("--help"));

        assertAll(
                () -> assertEquals(OptionalInt.of(0), outcome.exitCode()),
                () ->
                        assertTrue(
                                outcome.standardOutput().isEmpty(),
                                "a probe reading standard output alone sees an empty string; it"
                                        + " printed "
                                        + outcome.standardOutput().size()
                                        + " line(s) there"),
                () ->
                        assertTrue(
                                outcome.standardError().stream()
                                        .anyMatch(line -> line.contains("--xmloutput")),
                                "the noxml build advertises --xmloutput in its help text, which is"
                                        + " why a text probe cannot tell the two builds apart"),
                () ->
                        assertTrue(
                                outcome.standardError().stream()
                                        .anyMatch(line -> line.contains("--decoy-xml-output")),
                                "and --decoy-xml-output as well"),
                () ->
                        assertFalse(
                                outcome.standardError().isEmpty(),
                                "the whole help text is on standard error"));
    }
}
