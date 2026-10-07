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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.DownstreamStage;
import org.cometgui.params.percolator.schema.PercolatorSetting;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.io.AtomicDocumentWriter;
import org.cometgui.provenance.json.JsonWriter;
import org.cometgui.workflow.engine.StepFailedException;
import org.cometgui.workflow.state.InputValue;

/**
 * A run's {@code parameters/percolator-settings.json}: the Percolator scientific settings it
 * executes with, serialised once when the run is prepared and never again ({@code R-RUN-06}, design
 * decision P9-9).
 *
 * <p>The document is canonical: the one JSON writer's layout, the settings in {@link
 * PercolatorSetting} order as {@link PercolatorSettings#valueText} renders them -- exactly the text
 * that goes on the command line -- and the enabled downstream stages in {@link DownstreamStage}
 * order, because whether an enabled stage needs pout XML changes the command. Its bytes are
 * therefore a function of the settings alone, and their SHA-256 is the run's {@code
 * percolator-settings} fingerprint input. The format is documented in {@code
 * docs/reference/project_format.rst}.
 */
final class PercolatorSettingsFile {

    /** The format's version, the document's first member. */
    static final int SCHEMA_VERSION = 1;

    private PercolatorSettingsFile() {}

    /**
     * The archived file as it was written.
     *
     * @param text the document
     * @param size its length in bytes
     * @param hashes its digests, from the one hasher, read back from the file
     */
    record Archived(String text, long size, FileHashes hashes) {

        Archived {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(hashes, "hashes");
        }
    }

    /**
     * The canonical document.
     *
     * @param settings the settings
     * @param stages the enabled downstream stages
     * @return the JSON text, ending in one newline
     */
    static String render(PercolatorSettings settings, Set<DownstreamStage> stages) {
        JsonWriter json =
                JsonWriter.redactingWith(SecretRedactor.patternsOnly())
                        .beginObject()
                        .name("schemaVersion")
                        .value(SCHEMA_VERSION)
                        .name("settings")
                        .beginObject();
        for (PercolatorSetting setting : PercolatorSetting.values()) {
            json.name(setting.id()).value(settings.valueText(setting));
        }
        json.endObject().name("downstreamStages").beginArray();
        for (DownstreamStage stage : DownstreamStage.values()) {
            if (stages.contains(stage)) {
                json.value(stage.id());
            }
        }
        return json.endArray().endObject().finish();
    }

    /**
     * Writes the document once, atomically, and hashes what was written.
     *
     * @param file the run's settings file
     * @param text the document
     * @param hashes the one hasher
     * @return the archived file
     * @throws FileAlreadyExistsException if the file exists; it is not touched
     * @throws IOException if it cannot be written or read back, or what was read back is not what
     *     was written
     */
    static Archived writeOnce(Path file, String text, CachingHashService hashes)
            throws IOException {
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(
                    file.toString(),
                    null,
                    "a run's Percolator settings are written once, when the run is prepared"
                            + " (R-RUN-06)");
        }
        AtomicDocumentWriter.write(file, text);
        FileHashes written = hashes.rehash(file);
        String expected = InputValue.text(text).digest();
        if (!written.sha256().equals(expected)) {
            throw new IOException(
                    "the Percolator settings file "
                            + file
                            + " reads back with SHA-256 "
                            + written.sha256()
                            + ", not the "
                            + expected
                            + " of the document written");
        }
        return new Archived(text, Files.size(file), written);
    }

    /**
     * Re-hashes the archived file -- never from the cache -- and requires the digest and size it
     * had when it was written.
     *
     * @param file the run's settings file
     * @param archived what was written
     * @param hashes the one hasher
     * @throws StepFailedException if the file is gone or has changed, naming it and both digests
     * @throws IOException if it cannot be read
     */
    static void verify(Path file, Archived archived, CachingHashService hashes)
            throws StepFailedException, IOException {
        if (!Files.isRegularFile(file)) {
            throw new StepFailedException(
                    "the run's archived Percolator settings file " + file + " no longer exists");
        }
        FileHashes now = hashes.rehash(file);
        long size = Files.size(file);
        if (!now.sha256().equals(archived.hashes().sha256()) || size != archived.size()) {
            throw new StepFailedException(
                    "the run's archived Percolator settings file "
                            + file
                            + " has SHA-256 "
                            + now.sha256()
                            + " ("
                            + size
                            + " bytes), but the run recorded "
                            + archived.hashes().sha256()
                            + " ("
                            + archived.size()
                            + " bytes) when it was written; a run's settings are written once,"
                            + " so Percolator is not run");
        }
    }
}
