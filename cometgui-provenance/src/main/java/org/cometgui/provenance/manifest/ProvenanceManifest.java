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

package org.cometgui.provenance.manifest;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The whole provenance record for one run, as a single immutable value.
 *
 * <p>This is the model {@code provenance.json} and {@code provenance.rst} are both generated from.
 * The specification is explicit that the human-readable report "shall be generated from the same
 * machine-readable model, never maintained independently", and that is only enforceable if there is
 * one model to generate from -- which is this type. It holds no I/O and knows nothing about JSON:
 * serialisation is a separate concern, so that the shape of the record is decided by what a run has
 * to prove rather than by what a writer found convenient.
 *
 * <p><strong>The settings map is where the locale-sensitive science lives.</strong> {@code
 * AC-PRV-10} requires the effective Percolator seed and the JVM locale to be recorded; the locale
 * has its own field on {@link ApplicationRecord}, and the seed goes here, under {@link
 * ProvenanceSchema#PERCOLATOR_SEED_SETTING}. So do the other settings the application-provenance
 * list names but does not give a field to -- the result-view q filters used for a derived export,
 * the Limelight conversion parameters -- because they are open-ended, stage-specific and added by
 * later phases. An open namespace is the right shape for them and the wrong shape for anything a
 * reader must be able to find, which is why the keys that matter are pinned as constants rather
 * than written as literals at the call sites.
 *
 * <p><strong>Sorted, because a manifest is compared.</strong> The settings are iterated in
 * ascending key order, always. Two runs that differ only in the order a {@link java.util.HashMap}
 * happened to hash their keys must produce byte-identical documents, or a diff of two provenance
 * records is unreadable and the manifest's own checksum is not reproducible. {@link Map#copyOf}
 * makes no promise about iteration order, so this record does not rely on one.
 *
 * @param schemaVersion the format version this manifest was written against; see {@link
 *     ProvenanceSchema#VERSION} for what a difference obliges a reader to do
 * @param run the run's identity, state and timing
 * @param application the application, machine and JVM the run happened on
 * @param settings the scientific and export settings in effect, iterated in ascending key order
 * @param tools every tool invocation the run made, in the order they were made
 * @param files every input and output file the run read or wrote
 */
public record ProvenanceManifest(
        int schemaVersion,
        RunRecord run,
        ApplicationRecord application,
        Map<String, String> settings,
        List<ToolRecord> tools,
        List<FileRecord> files) {

    /**
     * Validates the manifest and takes defensive, immutable copies of all three collections.
     *
     * @throws NullPointerException if any reference component is {@code null}, or if any element of
     *     {@code tools} or {@code files} is
     * @throws IllegalArgumentException if {@code schemaVersion} is below 1, or if a settings key is
     *     blank or a settings value is null -- with a message naming the field and the rejected
     *     value
     */
    public ProvenanceManifest {
        if (schemaVersion < 1) {
            throw new IllegalArgumentException(
                    "schemaVersion must be at least 1, but was: " + schemaVersion);
        }
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(application, "application");
        settings = sortedSettings(settings);
        tools = ManifestChecks.copyOfNonNull(tools, "tools");
        files = ManifestChecks.copyOfNonNull(files, "files");
    }

    /**
     * Builds a manifest stamped with the schema version this build writes.
     *
     * <p>Use this rather than the canonical constructor for a manifest being created now. The
     * constructor takes an explicit version because a reader that loads an older document has to be
     * able to represent the version it found, but a writer that chose its own version number would
     * eventually choose one that disagreed with {@link ProvenanceSchema#VERSION}.
     *
     * @param run the run's identity, state and timing
     * @param application the application, machine and JVM the run happened on
     * @param settings the scientific and export settings in effect
     * @param tools every tool invocation the run made
     * @param files every input and output file the run read or wrote
     * @return a manifest carrying {@link ProvenanceSchema#VERSION}
     */
    public static ProvenanceManifest current(
            RunRecord run,
            ApplicationRecord application,
            Map<String, String> settings,
            List<ToolRecord> tools,
            List<FileRecord> files) {
        return new ProvenanceManifest(
                ProvenanceSchema.VERSION, run, application, settings, tools, files);
    }

    private static Map<String, String> sortedSettings(Map<String, String> settings) {
        Objects.requireNonNull(settings, "settings");
        Map<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> setting : settings.entrySet()) {
            String key = ManifestChecks.requireNonBlank(setting.getKey(), "a settings key");
            if (setting.getValue() == null) {
                throw new IllegalArgumentException(
                        "the setting \"" + key + "\" must not have a null value");
            }
            sorted.put(key, setting.getValue());
        }
        return Collections.unmodifiableMap(sorted);
    }

    /**
     * The scientific and export settings in effect for the run.
     *
     * <p>Immutable, and <strong>iterated in ascending key order</strong> for the reason given on
     * the class: a provenance document that is not byte-reproducible cannot be diffed or
     * checksummed. The copy is what makes the immutability visible at the call site -- and to
     * SpotBugs, which reports a record accessor handing out a collection field as {@code
     * EI_EXPOSE_REP}.
     *
     * @return the settings, immutable and sorted by key
     */
    public Map<String, String> settings() {
        return Collections.unmodifiableSortedMap(new TreeMap<>(settings));
    }

    /**
     * Every tool invocation the run made, in the order they were made.
     *
     * @return the tool records, immutable
     */
    public List<ToolRecord> tools() {
        return List.copyOf(tools);
    }

    /**
     * Every input and output file the run read or wrote.
     *
     * @return the file records, immutable
     */
    public List<FileRecord> files() {
        return List.copyOf(files);
    }

    /**
     * Describes the manifest without disclosing a single settings value.
     *
     * <p>The generated {@code toString} would print every setting's value, and a settings map is an
     * open namespace that a later phase fills with whatever a stage needs to record -- a server
     * address, an upload target, eventually something that should never have been put there. It
     * prints the <em>keys</em>, already sorted, and the tool names, which is what identifies a
     * manifest in a log line. The values are still available through {@link #settings()}, which is
     * the deliberate, redactable path the serialiser uses.
     *
     * @return a description safe to put in a log line or an exception message
     */
    @Override
    public String toString() {
        return "ProvenanceManifest[schemaVersion="
                + schemaVersion
                + ", runId="
                + run.runId()
                + ", status="
                + run.status().wireName()
                + ", settingsKeys="
                + settings.keySet()
                + ", tools="
                + tools.stream().map(ToolRecord::name).toList()
                + ", fileCount="
                + files.size()
                + "]";
    }
}
