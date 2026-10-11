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

package org.cometgui.tools.limelight;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.cometgui.tools.testing.UpstreamArtefacts;

/**
 * Small Percolator XML documents written by hand, the real inputs from the gitignored mirror
 * checked by SHA-256, and a hashing service of the test's own.
 *
 * <p>{@code D-006}: every real input is read in place under {@code scratch/}; anything a test
 * derives from one goes under a {@code @TempDir}. A missing or changed input fails the test, naming
 * the file and how to refill it; nothing skips.
 */
final class LimelightFixtures {

    /** The mirror's file name for the pinned converter. */
    static final String CONVERTER_FILE = "v2.8.1__cometPercolator2LimelightXML.jar";

    /** Its SHA-256, hand-typed from the manifest. */
    static final String CONVERTER_SHA256 =
            "843573396ce0654a0ac81582b378c496923e49dde71f40d750d890947774ece1";

    /** The schema entry's SHA-256, hand-typed from Phase 00's record. */
    static final String SCHEMA_SHA256 =
            "e8c1b92098db3b4364f06101b1def43675cd956ddf8758de25b692e3f698d242";

    static final String COMET_PARAMS = "scratch/scientific-path/comet/comet.params";
    static final String COMET_PARAMS_SHA256 =
            "da0da57e336343c559e6c6b930f77fe29fa3547738bc422c5b2ec7f7c61bb470";

    static final String COMET_DIRECTORY = "scratch/scientific-path/comet";

    static final String POUT = "scratch/scientific-path/percolator-3.07.1/percolator.pout.xml";
    static final String POUT_SHA256 =
            "50fb0d44f92b3bd66db11baa802c8cec9298fc7d497210d408651b2d7ca33b52";

    static final String FASTA = "scratch/fixture/UP000005640_9606.fasta";
    static final String FASTA_SHA256 =
            "2329a517bec9bd7269f9ce3b9252d8b959ae98bc41405a945c2d6134b284d5a0";

    static final String PEPXML_3 = "20100614_Velos1_TaGe_SA_K562_3.pep.xml";
    static final String PEPXML_3_SHA256 =
            "34bf414524e6dedf12a777b44fdf49956c88addcc44c76565a0d9fed316f7dd7";
    static final String PEPXML_4 = "20100614_Velos1_TaGe_SA_K562_4.pep.xml";
    static final String PEPXML_4_SHA256 =
            "28f8a6da4ead892322b449c9dba37869cfc75d47433ba2ac457986336889590c";

    /** Phase 00's real conversion of the same inputs. */
    static final String LIMELIGHT_XML = "scratch/scientific-path/limelight/limelight.xml";

    static final String LIMELIGHT_XML_SHA256 =
            "25c218a8f5658ea19273703517040c823530b1c5890681b0e231f001408c0a11";

    static final String CORRUPT_BOGUS = "scratch/scientific-path/corrupt/bogus-element.xml";
    static final String CORRUPT_BOGUS_SHA256 =
            "b2cf792f20fc5f2de1c143aa083065d45f3ab2a6db9e2032615561420a112196";
    static final String CORRUPT_ATTRIBUTE = "scratch/scientific-path/corrupt/missing-attribute.xml";
    static final String CORRUPT_ATTRIBUTE_SHA256 =
            "56c2f1a1c10dbfc1d75069c459343befccde149a9095755a8b34a690330d746f";
    static final String CORRUPT_TRUNCATED = "scratch/scientific-path/corrupt/truncated.xml";
    static final String CORRUPT_TRUNCATED_SHA256 =
            "e1eed9441b780b3a951cf233d7b6664634ece890aa5fc49e590e88ea9afc2b26";

    /** Percolator 3.07.1 with -X -Z over Comet's internal decoys. */
    static final String WITH_Z = "scratch/scientific-path/decoy-chain/withZ.pout.xml";

    static final String WITH_Z_SHA256 =
            "2e078342f90f986aac1d7fa8ff802efc5e6bf7d53b36c5b00feaad748cb00bb1";

    /** Percolator 3.07.1 with -X -Z over a concatenated target+decoy FASTA search. */
    static final String CONCAT = "scratch/scientific-path/decoy-chain/concat.pout.xml";

    static final String CONCAT_SHA256 =
            "9986fceecc649908697f22d0878a0ae3c58f128195cb1ae6e04d445015214247";

    static final String CONCAT_DIRECTORY = "scratch/scientific-path/decoy-chain/comet";
    static final String CONCAT_COMET_PARAMS =
            "scratch/scientific-path/decoy-chain/comet/comet.params";
    static final String CONCAT_COMET_PARAMS_SHA256 =
            "6c21335a5bb613aa1ee603cf4acc315a4560d1169bbf0c6a881a77ef0f6d30d9";
    static final String CONCAT_PEPXML_3_SHA256 =
            "0d0140151a272e9cd8fe40ae9bb9920a008230cb06004975868cc286301f60e8";
    static final String CONCAT_PEPXML_4_SHA256 =
            "4e72f681f95ce8dbb200db19680b1345a9c423c79db5bc6378fbc70d2a953190";
    static final String CONCAT_FASTA =
            "scratch/scientific-path/decoy-chain/concat_target_decoy.fasta";
    static final String CONCAT_FASTA_SHA256 =
            "87bee129ef8d5ecd2ab9e2d806faec5f3898afcf53620b9a66b815d0c30e787a";

    private static final String REGENERATE =
            "regenerate it with bash scripts/feasibility/run_scientific_path.sh (Phase 00)";

    private LimelightFixtures() {}

    /**
     * One real input, held to its SHA-256.
     *
     * @param relative the path under the repository root
     * @param sha256 its pinned SHA-256
     * @return the absolute path
     * @throws IOException if it cannot be hashed
     */
    static Path real(String relative, String sha256) throws IOException {
        Path file = UpstreamArtefacts.repositoryRoot().resolve(relative);
        if (!Files.isRegularFile(file)) {
            throw new AssertionError(
                    file
                            + " does not exist. It is a local, gitignored input (D-006); "
                            + REGENERATE
                            + ". This test fails rather than skips.");
        }
        String actual = UpstreamArtefacts.sha256(file);
        if (!sha256.equals(actual)) {
            throw new AssertionError(
                    file
                            + " has SHA-256 "
                            + actual
                            + ", not the pinned "
                            + sha256
                            + "; "
                            + REGENERATE);
        }
        return file;
    }

    /**
     * A real directory under the repository root.
     *
     * @param relative the path
     * @return the absolute path
     */
    static Path realDirectory(String relative) {
        Path directory = UpstreamArtefacts.repositoryRoot().resolve(relative);
        if (!Files.isDirectory(directory)) {
            throw new AssertionError(directory + " does not exist; " + REGENERATE);
        }
        return directory;
    }

    /**
     * The pinned converter JAR, copied into a directory and checked.
     *
     * @param directory where to put it
     * @return the copy
     * @throws IOException if it cannot be copied
     */
    static Path converterJar(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path jar = directory.resolve("cometPercolator2LimelightXML.jar");
        Files.copy(
                UpstreamArtefacts.artefact(CONVERTER_FILE),
                jar,
                StandardCopyOption.REPLACE_EXISTING);
        String actual = UpstreamArtefacts.sha256(jar);
        if (!CONVERTER_SHA256.equals(actual)) {
            throw new AssertionError(
                    "the staged converter has SHA-256 "
                            + actual
                            + ", not the pinned "
                            + CONVERTER_SHA256);
        }
        return jar;
    }

    /** One PSM of a hand-written Percolator XML document. */
    record Psm(String id, String decoy) {
        static Psm target(String id) {
            return new Psm(id, null);
        }
    }

    /**
     * Writes a small Percolator XML document in the shape 3.07.1 writes: every PSM in {@code psms},
     * and one peptide referencing all of them through {@code psm_ids}.
     *
     * @param file where to write it
     * @param psms the PSMs
     * @return the file
     * @throws IOException if it cannot be written
     */
    static Path pout(Path file, List<Psm> psms) throws IOException {
        StringBuilder xml =
                new StringBuilder(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                                + "<percolator_output\n"
                                + "xmlns=\"http://per-colator.com/percolator_out/15\"\n"
                                + "xmlns:p=\"http://per-colator.com/percolator_out/15\"\n"
                                + "p:majorVersion=\"3\" p:minorVersion=\"07\">\n"
                                + "  <process_info><command_line>percolator</command_line>"
                                + "</process_info>\n  <psms>\n");
        for (Psm psm : psms) {
            xml.append("    <psm p:psm_id=\"").append(psm.id()).append('"');
            if (psm.decoy() != null) {
                xml.append(" p:decoy=\"").append(psm.decoy()).append('"');
            }
            xml.append(">\n      <svm_score>1.0</svm_score>\n      <q_value>0.001</q_value>\n")
                    .append("      <peptide_seq n=\"K\" c=\"R\" seq=\"PEPTIDE\"/>\n")
                    .append("    </psm>\n");
        }
        xml.append("  </psms>\n  <peptides>\n    <peptide p:peptide_id=\"K.PEPTIDE.R\">\n")
                .append("      <psm_ids>\n");
        for (Psm psm : psms) {
            xml.append("        <psm_id>").append(psm.id()).append("</psm_id>\n");
        }
        xml.append("      </psm_ids>\n    </peptide>\n  </peptides>\n</percolator_output>\n");
        return Files.writeString(file, xml, StandardCharsets.UTF_8);
    }

    /**
     * A hashing service of the test's own, sharing no code with the product's.
     *
     * @return the service
     */
    static HashService hashes() {
        return file -> new FileHashes(digest(file, "MD5"), digest(file, "SHA-256"));
    }

    static String digest(Path file, String algorithm) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] buffer = new byte[65536];
            try (InputStream in = Files.newInputStream(file)) {
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    /**
     * A small, self-consistent run in a directory: a Percolator XML with two target PSMs from one
     * pepXML whose name has underscores and digits in it, that pepXML, and the recorded checksums.
     */
    record SmallRun(Path pout, Path pepXmlDirectory, Map<String, String> recorded) {

        static SmallRun in(Path directory) throws IOException {
            return in(
                    directory, List.of(Psm.target("run_10_7_1_2_1"), Psm.target("run_10_7_2_3_1")));
        }

        static SmallRun in(Path directory, List<Psm> psms) throws IOException {
            Path comet = Files.createDirectories(directory.resolve("comet"));
            Path pout =
                    LimelightFixtures.pout(
                            Files.createDirectories(directory.resolve("percolator"))
                                    .resolve("percolator.pout.xml"),
                            psms);
            Map<String, String> recorded = new LinkedHashMap<>();
            for (Psm psm : psms) {
                String name = PepXmlSet.expectedFileName(psm.id());
                Path pepXml = comet.resolve(name);
                if (!Files.exists(pepXml)) {
                    Files.writeString(
                            pepXml,
                            "<msms_pipeline_analysis>" + name + "</msms_pipeline_analysis>\n");
                }
                recorded.put(name, digest(pepXml, "SHA-256"));
            }
            return new SmallRun(pout, comet, recorded);
        }

        PercolatorXmlPsms read() throws IOException {
            return PercolatorXmlPsms.read(pout);
        }

        PepXmlSet verified() throws IOException {
            return PepXmlSet.verify(read(), pepXmlDirectory, recorded, hashes());
        }

        LimelightConversionRequest request(Path output, LimelightQValueCutoff cutoff)
                throws IOException {
            PercolatorXmlPsms read = read();
            return new LimelightConversionRequest(
                    pepXmlDirectory.resolveSibling("comet.params"),
                    read,
                    PepXmlSet.verify(read, pepXmlDirectory, recorded, hashes()),
                    pepXmlDirectory.resolveSibling("db.fasta"),
                    output,
                    cutoff,
                    false,
                    Optional.empty(),
                    false,
                    false);
        }
    }
}
