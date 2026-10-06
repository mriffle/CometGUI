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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.SpectrumInput;
import org.cometgui.workflow.state.InputKind;
import org.cometgui.workflow.state.InputValue;
import org.cometgui.workflow.state.StepInputs;

/**
 * The input values a Comet run's step fingerprints are computed from (design decision P8-10): every
 * input kind the Phase 08 plan declares, and no other.
 *
 * <p>Pure. A recorded run's values come from its {@code run.json} identity -- the hashes taken when
 * it was created -- so a retry of the run is fingerprinted from what it recorded, and a file that
 * changed since is caught by re-hashing, not silently folded into a new fingerprint. A preview of a
 * changed configuration is fingerprinted from {@link #of} over the files as they are now.
 */
final class RunInputs {

    private RunInputs() {}

    /**
     * One file as a fingerprint sees it: its name and its hashes.
     *
     * @param path the file
     * @param hashes its hashes, from the one hasher
     * @return the named file
     */
    static InputValue.NamedFile named(Path path, FileHashes hashes) {
        return new InputValue.NamedFile(String.valueOf(path.getFileName()), hashes);
    }

    /**
     * The input values of a configuration.
     *
     * @param spectra the spectrum files with their hashes, in input order
     * @param database the database with its hashes
     * @param parametersSha256 the SHA-256 of the canonical {@code comet.params} bytes
     * @param mode the index mode
     * @param release the Comet release
     * @param cometSha256 the Comet executable's SHA-256
     * @return the inputs
     */
    static StepInputs of(
            List<InputValue.NamedFile> spectra,
            InputValue.NamedFile database,
            String parametersSha256,
            IndexMode mode,
            String release,
            String cometSha256) {
        Map<InputKind, InputValue> values = new EnumMap<>(InputKind.class);
        values.put(InputKind.SPECTRUM_FILES, InputValue.files(spectra));
        values.put(InputKind.FASTA, InputValue.files(List.of(database)));
        values.put(InputKind.COMET_PARAMETERS, new InputValue.Bytes(parametersSha256));
        values.put(InputKind.COMET_INDEX_MODE, InputValue.text(mode.wireName()));
        values.put(InputKind.COMET_TOOL, InputValue.tool(release, cometSha256));
        return StepInputs.of(values);
    }

    /**
     * The input values a recorded run was created with.
     *
     * @param identity the run's identity
     * @param cometSha256 the SHA-256 of the Comet executable the run uses
     * @return the inputs
     */
    static StepInputs recorded(RunIdentity identity, String cometSha256) {
        Objects.requireNonNull(identity, "identity");
        List<InputValue.NamedFile> spectra = new ArrayList<>();
        for (SpectrumInput spectrum : identity.spectra()) {
            spectra.add(named(spectrum.file().path(), spectrum.file().hashes()));
        }
        return of(
                spectra,
                named(identity.fasta().path(), identity.fasta().hashes()),
                identity.parameters().hashes().sha256(),
                identity.indexMode(),
                identity.cometRelease(),
                cometSha256);
    }
}
