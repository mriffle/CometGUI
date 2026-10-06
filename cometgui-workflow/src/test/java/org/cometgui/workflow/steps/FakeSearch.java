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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.comet.writer.CanonicalParamsWriter;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.hashing.StreamingHashService;

/**
 * A search over small, made-up files, for the tests that need no Comet process: an "executable"
 * that is never run, two tiny spectrum files and a two-record FASTA without decoys. Every path is
 * canonical, as the run records it.
 *
 * @param root the directory everything is in
 * @param executable the stand-in executable, mode 700
 * @param spectra the two spectrum files
 * @param fasta the FASTA
 * @param project the project directory, not created
 */
record FakeSearch(Path root, Path executable, List<Path> spectra, Path fasta, Path project) {

    /** The FASTA's text: two target records, no decoy. */
    static final String FASTA_TEXT =
            ">sp|P00001|ONE first\nPEPTIDEK\n>sp|P00002|TWO second\nKEDITPEP\n";

    static FakeSearch create(Path directory) throws IOException {
        Path root = directory.toRealPath();
        Path executable = Files.writeString(root.resolve("comet"), "not a real comet\n");
        Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwx------"));
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        Path first = Files.writeString(inputs.resolve("k562_3.mzML"), "spectra three\n");
        Path second = Files.writeString(inputs.resolve("k562_4.mzML"), "spectra four\n");
        Path fasta =
                Files.writeString(
                        inputs.resolve("db.fasta"), FASTA_TEXT, StandardCharsets.US_ASCII);
        return new FakeSearch(
                root, executable, List.of(first, second), fasta, root.resolve("project"));
    }

    /** The stand-in's SHA-256. */
    String executableSha256() throws IOException {
        return RealComet.sha256(executable);
    }

    CometSelection selection() throws IOException {
        return new CometSelection(
                executable,
                ToolVersion.parse(RealComet.NEWER),
                executableSha256(),
                false,
                Optional.empty());
    }

    CometParameters model(DecoySource decoys) {
        return RealComet.model(RealComet.NEWER, fasta, decoys, 4);
    }

    SearchRequest request(DecoySource decoys, IndexMode mode) throws IOException {
        return new SearchRequest(model(decoys), spectra, selection(), mode);
    }

    static CachingHashService hashes() {
        return new CachingHashService(new StreamingHashService());
    }

    static PreRunChecks checks(CachingHashService hashes, boolean windows) {
        return new PreRunChecks(hashes, new CanonicalParamsWriter(RealComet.BUILD), windows);
    }
}
