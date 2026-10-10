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

package org.cometgui.tools.testing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The real K562 {@code _3} run the PDV tests read: the spectrum file Comet searched and the pepXML
 * Comet wrote from it, each held to its SHA-256 before use.
 *
 * <p>{@code D-006}: these are local, gitignored inputs and nothing derived from them -- an MGF, a
 * figure -- is ever written outside a build directory or committed. A missing or changed input
 * fails the test with how to refill it; nothing skips.
 */
public final class K562Run {

    /** The as-fetched fixture mzML (CRLF line endings), relative to the repository root. */
    public static final String MZML = "scratch/fixture/20100614_Velos1_TaGe_SA_K562_3.mzML";

    /** Its SHA-256, as {@link RealCometSearch} and the Phase 11 spike record it. */
    public static final String MZML_SHA256 =
            "cbd0c1b37fb990e6f44528278956306754145ff7318ec7f89fed3b4d3c9b0bc7";

    /** Comet 2026.02.2's pepXML for that file, from Phase 00's scientific path. */
    public static final String PEPXML =
            "scratch/scientific-path/comet/20100614_Velos1_TaGe_SA_K562_3.pep.xml";

    /** Its SHA-256. */
    public static final String PEPXML_SHA256 =
            "34bf414524e6dedf12a777b44fdf49956c88addcc44c76565a0d9fed316f7dd7";

    /** Comet 2026.02.2's pepXML for {@code _4}, from the same search. */
    public static final String PEPXML_4 =
            "scratch/scientific-path/comet/20100614_Velos1_TaGe_SA_K562_4.pep.xml";

    /** Its SHA-256. */
    public static final String PEPXML_4_SHA256 =
            "28f8a6da4ead892322b449c9dba37869cfc75d47433ba2ac457986336889590c";

    /** Percolator 3.07.1's target PSMs over both pepXML files' PINs, from the same path. */
    public static final String PERCOLATOR_TARGET_PSMS =
            "scratch/scientific-path/percolator-3.07.1/psms.target.txt";

    /** Its SHA-256. */
    public static final String PERCOLATOR_TARGET_PSMS_SHA256 =
            "458b79b5a423f57406421d31ddd57b946eb8096ab6935447efd1e98e48f7fe9e";

    /** Percolator 3.07.1's decoy PSMs, from the same run. */
    public static final String PERCOLATOR_DECOY_PSMS =
            "scratch/scientific-path/percolator-3.07.1/psms.decoy.txt";

    /** Its SHA-256. */
    public static final String PERCOLATOR_DECOY_PSMS_SHA256 =
            "e1245d4f3fd128a5c7efcf89bc0b0ab0991d3f31bff437caa23bec3f437fc4c1";

    private static final String REGENERATE =
            "regenerate it with bash scripts/feasibility/run_scientific_path.sh";

    private K562Run() {}

    /**
     * The fixture mzML, verified.
     *
     * @return the file
     * @throws IOException if it cannot be read
     */
    public static Path mzml() throws IOException {
        return verified(
                MZML,
                MZML_SHA256,
                "refill it with python3 scripts/feasibility/fetch_ephemeral_input.py, which fetches"
                        + " by checksum");
    }

    /**
     * The pepXML, verified.
     *
     * @return the file
     * @throws IOException if it cannot be read
     */
    public static Path pepXml() throws IOException {
        return verified(PEPXML, PEPXML_SHA256, REGENERATE);
    }

    /**
     * The {@code _4} pepXML, verified.
     *
     * @return the file
     * @throws IOException if it cannot be read
     */
    public static Path pepXml4() throws IOException {
        return verified(PEPXML_4, PEPXML_4_SHA256, REGENERATE);
    }

    /**
     * Percolator's target PSMs, verified.
     *
     * @return the file
     * @throws IOException if it cannot be read
     */
    public static Path percolatorTargets() throws IOException {
        return verified(PERCOLATOR_TARGET_PSMS, PERCOLATOR_TARGET_PSMS_SHA256, REGENERATE);
    }

    /**
     * Percolator's decoy PSMs, verified.
     *
     * @return the file
     * @throws IOException if it cannot be read
     */
    public static Path percolatorDecoys() throws IOException {
        return verified(PERCOLATOR_DECOY_PSMS, PERCOLATOR_DECOY_PSMS_SHA256, REGENERATE);
    }

    private static Path verified(String relative, String sha256, String refill) throws IOException {
        Path file = UpstreamArtefacts.repositoryRoot().resolve(relative);
        if (!Files.isRegularFile(file)) {
            throw new AssertionError(
                    file
                            + " does not exist. It is a local, gitignored input (D-006); "
                            + refill
                            + ". This test fails rather than skips.");
        }
        String actual = UpstreamArtefacts.sha256(file);
        if (!sha256.equals(actual)) {
            throw new AssertionError(
                    file
                            + " has SHA-256 "
                            + actual
                            + ", not the "
                            + sha256
                            + " this test pins; "
                            + refill);
        }
        return file;
    }
}
