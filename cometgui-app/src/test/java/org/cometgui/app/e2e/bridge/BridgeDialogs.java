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

package org.cometgui.app.e2e.bridge;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * What the packaged application's native file dialogs answer while the bridge is attached: the
 * answers the test queued, one per dialog, in order -- and a record of every dialog the application
 * opened.
 *
 * <h2>Only the dialog is replaced</h2>
 *
 * <p>{@link DialogsTransformer} rewrites the three methods of {@code FxFileChooser}'s JavaFX
 * dialogs object -- the product's one seam that calls {@code FileChooser.showOpenDialog}, {@code
 * showOpenMultipleDialog} and {@code showSaveDialog} -- to call {@link #open}, {@link
 * #openMultiple} and {@link #save} here instead, with the same arguments. Everything the product
 * decides before and after the dialog still runs as shipped: {@code FxFileChooser} sets the title,
 * the filters and the suggested name and turns the answer, or {@code null} for a cancelled dialog,
 * into its port's result; the view-model then validates and records the file exactly as for a user.
 *
 * <h2>Matching an answer to a dialog</h2>
 *
 * <p>An answer names the dialog kind ({@code open}, {@code openMultiple} or {@code save}) and a
 * regular expression the dialog's title must match in full. A dialog takes the first queued answer
 * that fits it and leaves the others queued; a dialog no answer fits is cancelled -- what a user
 * closing it would do. Every dialog is recorded, answered or not, as {@code
 * kind<TAB>title<TAB>suggested name<TAB>filter descriptions joined by |}.
 *
 * <p>The dialogs are called on the application thread and the answers queued from a bridge
 * connection's thread; one lock of this class's own orders the two.
 */
public final class BridgeDialogs {

    /** The dialog kinds, as the rewritten methods are named. */
    static final Set<String> KINDS = Set.of("open", "openMultiple", "save");

    private static final Object LOCK = new Object();

    private static final List<Answer> ANSWERS = new ArrayList<>();

    private static final List<String> ASKED = new ArrayList<>();

    private BridgeDialogs() {}

    /**
     * One queued answer.
     *
     * @param kind the dialog kind it answers
     * @param title what the dialog's title must match, in full
     * @param paths the files it returns
     */
    private record Answer(String kind, Pattern title, List<String> paths) {}

    /**
     * Queues an answer.
     *
     * @param kind {@code open}, {@code openMultiple} or {@code save}
     * @param titleRegex a regular expression the dialog's title must match in full
     * @param paths the files to answer: exactly one for {@code open} and {@code save}, at least one
     *     for {@code openMultiple}; absolute
     * @throws IllegalArgumentException naming what is wrong with the answer
     */
    static void queue(String kind, String titleRegex, List<String> paths) {
        Objects.requireNonNull(paths, "paths");
        if (!KINDS.contains(kind)) {
            throw new IllegalArgumentException(
                    "a dialog kind is one of " + KINDS + ", not \"" + kind + "\"");
        }
        int wanted = "openMultiple".equals(kind) ? Math.max(1, paths.size()) : 1;
        if (paths.size() != wanted) {
            throw new IllegalArgumentException(
                    "a " + kind + " dialog answers " + wanted + " file(s), not " + paths);
        }
        for (String path : paths) {
            if (!new File(path).isAbsolute()) {
                throw new IllegalArgumentException("an answer's file must be absolute: " + path);
            }
        }
        Pattern title;
        try {
            title = Pattern.compile(Objects.requireNonNull(titleRegex, "titleRegex"));
        } catch (PatternSyntaxException bad) {
            throw new IllegalArgumentException(
                    "not a regular expression: " + titleRegex + ": " + bad.getMessage(), bad);
        }
        synchronized (LOCK) {
            ANSWERS.add(new Answer(kind, title, List.copyOf(paths)));
        }
    }

    /**
     * Every dialog opened so far, in order.
     *
     * @return one entry per dialog: kind, title, suggested name, filters, tab-separated
     */
    static List<String> asked() {
        synchronized (LOCK) {
            return List.copyOf(ASKED);
        }
    }

    /**
     * How many queued answers no dialog has taken.
     *
     * @return the count
     */
    static int unused() {
        synchronized (LOCK) {
            return ANSWERS.size();
        }
    }

    /** Forgets every answer and every recorded dialog; for the bridge's own tests. */
    static void reset() {
        synchronized (LOCK) {
            ANSWERS.clear();
            ASKED.clear();
        }
    }

    /**
     * In place of {@code chooser.showOpenMultipleDialog(owner)}.
     *
     * @param chooser the chooser the product configured
     * @param owner the window the dialog would be modal over
     * @return the queued files, or {@code null} -- a cancelled dialog -- if no answer fits
     */
    public static List<File> openMultiple(FileChooser chooser, Window owner) {
        Answer answer = take("openMultiple", chooser);
        return answer == null ? null : answer.paths().stream().map(File::new).toList();
    }

    /**
     * In place of {@code chooser.showOpenDialog(owner)}.
     *
     * @param chooser the chooser the product configured
     * @param owner the window the dialog would be modal over
     * @return the queued file, or {@code null} if no answer fits
     */
    public static File open(FileChooser chooser, Window owner) {
        Answer answer = take("open", chooser);
        return answer == null ? null : new File(answer.paths().get(0));
    }

    /**
     * In place of {@code chooser.showSaveDialog(owner)}.
     *
     * @param chooser the chooser the product configured
     * @param owner the window the dialog would be modal over
     * @return the queued file, or {@code null} if no answer fits
     */
    public static File save(FileChooser chooser, Window owner) {
        Answer answer = take("save", chooser);
        return answer == null ? null : new File(answer.paths().get(0));
    }

    private static Answer take(String kind, FileChooser chooser) {
        String title = Objects.toString(chooser.getTitle(), "");
        String suggested = Objects.toString(chooser.getInitialFileName(), "");
        List<String> filters = new ArrayList<>();
        for (FileChooser.ExtensionFilter filter : chooser.getExtensionFilters()) {
            filters.add(filter.getDescription());
        }
        synchronized (LOCK) {
            ASKED.add(kind + "\t" + title + "\t" + suggested + "\t" + String.join("|", filters));
            for (Iterator<Answer> queued = ANSWERS.iterator(); queued.hasNext(); ) {
                Answer answer = queued.next();
                if (answer.kind().equals(kind) && answer.title().matcher(title).matches()) {
                    queued.remove();
                    return answer;
                }
            }
            return null;
        }
    }
}
