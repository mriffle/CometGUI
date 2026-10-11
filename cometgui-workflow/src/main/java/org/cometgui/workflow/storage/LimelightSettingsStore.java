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

package org.cometgui.workflow.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.provenance.io.AtomicDocumentWriter;

/**
 * Reads and writes a run's Limelight conversion settings, {@code results/limelight-settings.json}
 * ({@link LimelightSettingsJson}).
 *
 * <p>Like the view state ({@link ViewStateStore}) and separate from it: not part of the run's
 * record, never in {@code run.json}, never under {@code outputs/}. Writing touches {@code
 * results/limelight-settings.json} and a temporary file beside it, and nothing else -- not the view
 * state, not an output, not the event log. The settings a conversion actually used are what
 * provenance records, in that conversion's own event.
 *
 * <p>A file this build cannot read is <strong>refused</strong>, not replaced by the defaults: a
 * conversion made with silently different settings would be a conversion the scientist did not ask
 * for. {@link #write} refuses to replace such a file too, so a newer CometGUI's settings are never
 * overwritten by an older one ({@code R-RUN-04}); removing the file restores the defaults.
 */
public final class LimelightSettingsStore {

    private LimelightSettingsStore() {
        throw new AssertionError("LimelightSettingsStore is never instantiated");
    }

    /**
     * Reads a run's settings, or the defaults when none were saved.
     *
     * @param run the run
     * @return the settings
     * @throws InvalidDocumentException if the file is not version-1 Limelight settings
     * @throws org.cometgui.domain.project.UnsupportedSchemaVersionException if it is of another
     *     schema version
     * @throws IOException if the file exists but cannot be read
     * @throws NullPointerException if {@code run} is {@code null}
     */
    public static LimelightSettings read(RunLayout run) throws IOException {
        Objects.requireNonNull(run, "run");
        Path file = run.limelightSettingsFile();
        if (!Files.exists(file)) {
            return LimelightSettings.DEFAULTS;
        }
        return parse(file);
    }

    /**
     * Saves a run's settings, atomically ({@code results/} is made if absent).
     *
     * @param run the run
     * @param settings the settings
     * @throws InvalidDocumentException if the run already has settings this build cannot read; they
     *     are left untouched
     * @throws org.cometgui.domain.project.UnsupportedSchemaVersionException if they are of another
     *     schema version; they are left untouched
     * @throws IOException if the file cannot be read or written
     * @throws NullPointerException if an argument is {@code null}
     */
    public static void write(RunLayout run, LimelightSettings settings) throws IOException {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(settings, "settings");
        Path file = run.limelightSettingsFile();
        if (Files.exists(file)) {
            parse(file);
        }
        Files.createDirectories(run.resultsDirectory());
        AtomicDocumentWriter.write(file, LimelightSettingsJson.render(settings));
    }

    private static LimelightSettings parse(Path file) throws IOException {
        return LimelightSettingsJson.parse(DocumentFields.readText(file), file.toString());
    }
}
