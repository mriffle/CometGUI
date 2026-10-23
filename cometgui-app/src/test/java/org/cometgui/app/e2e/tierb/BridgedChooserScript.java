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

package org.cometgui.app.e2e.tierb;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.cometgui.app.e2e.scenario.ChooserScript;

/**
 * Tier B's answer to the file dialogs: the scenario's {@link ChooserScript} over the bridge, which
 * replaces only the native dialog inside the packaged application ({@code
 * e2e.bridge.BridgeDialogs}) -- the product's {@code FxFileChooser} still sets each dialog's title,
 * filters and suggested name and still turns the answer into its port's result.
 *
 * <h2>Which dialog an answer is for</h2>
 *
 * <p>A dialog is told apart by its kind and its title, as {@code FxFileChooser} sets them; the
 * titles are typed out here, as every identifier a scenario uses is (P7-5), so a renamed dialog
 * fails the scenario -- its answer is left unused and the dialog is cancelled -- rather than being
 * answered silently:
 *
 * <ul>
 *   <li>spectra: several files, "{@value #SPECTRA}";
 *   <li>database: one file, "{@value #DATABASE}";
 *   <li>a file parameter: one file, "{@value #FILE_PREFIX}" followed by what it is for;
 *   <li>the parameter file to import: one file, "{@value #PARAMETER_FILE}";
 *   <li>saving the parameter file: a save dialog, "{@value #SAVE}";
 *   <li>an export: a save dialog with any other title.
 * </ul>
 *
 * <p>{@link #asked()} names the dialogs exactly as Tier A's {@code ScriptedChooser} does, so a
 * scenario's assertion on them reads the same in both tiers.
 */
public final class BridgedChooserScript implements ChooserScript {

    /** {@code FxFileChooser.chooseSpectrumFiles}'s title. */
    static final String SPECTRA = "Choose spectrum files to search";

    /** {@code FxFileChooser.chooseDatabase}'s title. */
    static final String DATABASE = "Choose the sequence database";

    /** The start of {@code FxFileChooser.chooseFile}'s title. */
    static final String FILE_PREFIX = "Choose the file for ";

    /** {@code FxFileChooser.chooseParameterFile}'s title. */
    static final String PARAMETER_FILE = "Import a Comet parameter file";

    /** {@code FxFileChooser.chooseSaveTarget}'s title. */
    static final String SAVE = "Save the Comet parameter file";

    private final BridgeClient bridge;

    /**
     * Over a bridge connection.
     *
     * @param bridge the connection
     */
    public BridgedChooserScript(BridgeClient bridge) {
        this.bridge = Objects.requireNonNull(bridge, "bridge");
    }

    @Override
    public void nextSpectra(List<Path> files) {
        answer("openMultiple", Pattern.quote(SPECTRA), files);
    }

    @Override
    public void nextDatabase(Path file) {
        answer("open", Pattern.quote(DATABASE), List.of(file));
    }

    @Override
    public void nextFile(Path file) {
        answer("open", Pattern.quote(FILE_PREFIX) + ".*", List.of(file));
    }

    @Override
    public void nextExportTarget(Path file) {
        answer("save", "(?!" + Pattern.quote(SAVE) + "$).*", List.of(file));
    }

    @Override
    public List<String> asked() {
        List<String> named = new ArrayList<>();
        for (String dialog : raw()) {
            named.add(name(dialog));
        }
        return List.copyOf(named);
    }

    /**
     * Every dialog the packaged application opened, as the bridge recorded it: kind, title,
     * suggested name and filter descriptions, tab-separated.
     *
     * @return the records
     */
    public List<String> raw() {
        return bridge.lines("bridge.chooser.asked");
    }

    /**
     * What the bridge's transformer did to the product's dialogs object: one line per rewrite or
     * failure. A scenario requires the rewrite before it relies on it, because a native dialog
     * opened on the test display would hang the run instead of failing it.
     *
     * @return the lines
     */
    public List<String> transformed() {
        return bridge.lines("bridge.chooser.transformed");
    }

    private void answer(String kind, String titleRegex, List<Path> files) {
        List<String> paths = new ArrayList<>();
        for (Path file : files) {
            if (!file.isAbsolute()) {
                throw new IllegalArgumentException("a chosen file must be absolute: " + file);
            }
            paths.add(file.toString());
        }
        bridge.call("bridge.chooser.answer", kind, titleRegex, List.copyOf(paths));
    }

    /**
     * A recorded dialog, named as {@code ScriptedChooser} names its questions.
     *
     * @param dialog kind, title, suggested name and filters, tab-separated
     * @return the name
     */
    static String name(String dialog) {
        String[] parts = dialog.split("\t", -1);
        String kind = parts[0];
        String title = parts.length > 1 ? parts[1] : "";
        String suggested = parts.length > 2 ? parts[2] : "";
        if ("openMultiple".equals(kind) && SPECTRA.equals(title)) {
            return "spectra";
        }
        if ("open".equals(kind) && DATABASE.equals(title)) {
            return "database";
        }
        if ("open".equals(kind) && title.startsWith(FILE_PREFIX)) {
            return "file:" + title.substring(FILE_PREFIX.length());
        }
        if ("open".equals(kind) && PARAMETER_FILE.equals(title)) {
            return "parameter file";
        }
        if ("save".equals(kind) && SAVE.equals(title)) {
            return "save";
        }
        if ("save".equals(kind)) {
            return "export:" + title + ":" + suggested;
        }
        return "unrecognised " + kind + " dialog: " + title;
    }
}
