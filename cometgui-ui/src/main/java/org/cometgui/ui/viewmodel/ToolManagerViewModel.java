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

package org.cometgui.ui.viewmodel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallProgress;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolOffer;

/**
 * The Tool Manager: every tool build this machine may be shown, and the two actions a user has.
 *
 * <h2>The port's answer is the whole answer</h2>
 *
 * <p>{@link ToolManager#offers()} decides which builds exist, which are offered here and in what
 * order. This class maps that list to rows one for one -- it does not filter it, re-order it or
 * re-derive it. That is not fastidiousness: the ordering and the selection rules live in one place
 * on purpose, and the one time this project duplicated them it showed a 99 MB download twice and
 * labelled the second copy as running under Rosetta 2, which is a false statement about a Java
 * program.
 *
 * <h2>Why a row's key carries an ordinal</h2>
 *
 * <p><strong>Two offers can legitimately share a tool and a version.</strong> On Apple silicon
 * Comet 2026.02.2 is published as a native build and as an x86-64 build and both are offered, so a
 * key built from the tool and the version alone would name two rows with one string -- and {@code
 * Scene.lookup} returns whichever node it reaches first, which is how a test comes to assert
 * against the wrong control. The key is therefore the tool, the version with its dots written as
 * underscores (a dot in an identifier is read as a style class), and a 1-based ordinal within the
 * offers that share both. No platform this project can execute today produces a second ordinal,
 * which is precisely why it is written down rather than discovered on the first Apple silicon Mac.
 *
 * <h2>Nothing is read until someone asks</h2>
 *
 * <p>A new Tool Manager holds no rows: {@link #refresh()} is what reads the port, and it is called
 * by the composition root and by the view rather than by the constructor. {@code offers()} verifies
 * every installed entry against its recorded checksums, so "what is on screen" stays a thing
 * someone decided rather than a thing that happened while a window was being built.
 *
 * <h2>The two threads, and the seam between them</h2>
 *
 * <p>An install runs on its own thread and reports progress from it. This class takes the executor
 * that puts work back on the interface thread as a constructor argument -- an {@link Executor}, not
 * a toolkit call -- so the marshalling is the view's decision, this package stays testable with no
 * toolkit started, and a test can pass {@code Runnable::run} and get a deterministic sequence.
 *
 * <p>Everything else is called on the interface thread: {@link #refresh()}, {@link #install} and
 * {@link #cancel} all touch the observable row list, which is the toolkit's to read while it
 * paints.
 */
public final class ToolManagerViewModel {

    /** The summary shown before {@link #refresh()} has read the port. */
    public static final String NOT_READ_YET = "The tool list has not been read yet.";

    /**
     * The manager this view-model shows, or {@code null} where this host has none.
     *
     * <p>Null rather than a do-nothing implementation. A {@link ToolManager} that answered with an
     * empty list would be a fiction the rest of the application could not tell from a machine on
     * which upstream publishes nothing, and this project has already decided once that a no-op
     * implementation of a seam is worse than an absent one.
     */
    private final ToolManager tools;

    /** Why there is no manager, present exactly when {@link #tools} is {@code null}. */
    private final String unavailableReason;

    /** How work gets back onto the interface thread from an install thread. */
    private final Executor uiThread;

    private final ObservableList<ToolRowViewModel> rows = FXCollections.observableArrayList();

    private final ReadOnlyStringWrapper summary =
            new ReadOnlyStringWrapper(this, "summary", NOT_READ_YET);

    /**
     * A Tool Manager over the port.
     *
     * @param tools the manager the application composed for this host
     * @param uiThread how a progress report gets back onto the interface thread; the view passes
     *     the toolkit's own "run this later on the interface thread" call and a test passes {@code
     *     Runnable::run}
     * @throws NullPointerException if either argument is {@code null}
     */
    public ToolManagerViewModel(ToolManager tools, Executor uiThread) {
        this.tools = Objects.requireNonNull(tools, "tools");
        this.unavailableReason = null;
        this.uiThread = Objects.requireNonNull(uiThread, "uiThread");
    }

    private ToolManagerViewModel(String unavailableReason, Executor uiThread) {
        this.tools = null;
        this.unavailableReason = Objects.requireNonNull(unavailableReason, "unavailableReason");
        if (unavailableReason.isBlank()) {
            throw new IllegalArgumentException(
                    "a Tool Manager that is not available on this host has to say why: a blank"
                            + " reason leaves the section empty with no explanation");
        }
        this.uiThread = Objects.requireNonNull(uiThread, "uiThread");
    }

    /**
     * A Tool Manager for a host the application could not compose one for.
     *
     * <p>The reason comes from the composition root, which is the only part of the application that
     * knows whether the platform is one the product supports, whether the artefact manifest could
     * be read, and whether this runtime can start the second virtual machine a JAR's identity probe
     * needs. The section then says that sentence instead of listing nothing and explaining nothing.
     *
     * @param reason what to tell the user, in a sentence
     * @param uiThread how work gets back onto the interface thread
     * @return the view-model, which holds no rows and offers no action
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if {@code reason} is blank
     */
    public static ToolManagerViewModel unavailable(String reason, Executor uiThread) {
        return new ToolManagerViewModel(reason, uiThread);
    }

    /**
     * Whether this host has a Tool Manager at all.
     *
     * @return {@code true} when the application composed one
     */
    public boolean isAvailable() {
        return tools != null;
    }

    /**
     * Why this host has no Tool Manager.
     *
     * @return the reason, or empty when there is one
     */
    public Optional<String> unavailableReason() {
        return Optional.ofNullable(unavailableReason);
    }

    /**
     * The rows, in the order the port offered them.
     *
     * <p><strong>A caller that listens to this list must keep the returned wrapper.</strong> A new
     * unmodifiable view is built on each call and it observes the list behind it <em>weakly</em>,
     * so a listener registered on a wrapper nobody kept stops firing at the next garbage collection
     * -- intermittently, and only under memory pressure, which is the worst way to find a bug.
     * {@code StageStepper} learnt this from {@code stageStates()} and holds its wrapper in a field;
     * {@code ToolManagerPane} does the same. The wrapper is built per call rather than held here
     * because a field handed straight out is a composition root publishing its own mutable state,
     * which is what SpotBugs reports as {@code EI_EXPOSE_REP}.
     *
     * @return an unmodifiable observable view of the rows, empty until {@link #refresh()} has run
     */
    public ObservableList<ToolRowViewModel> rows() {
        return FXCollections.unmodifiableObservableList(rows);
    }

    /**
     * What the list below the heading holds, in one line.
     *
     * @return the read-only property, never holding {@code null}
     */
    public ReadOnlyStringProperty summaryProperty() {
        return summary.getReadOnlyProperty();
    }

    /**
     * The summary's current value.
     *
     * @return the summary line
     */
    public String summary() {
        return summary.get();
    }

    /**
     * Reads the port and puts the rows in step with it.
     *
     * <p>A row whose key is still offered keeps its identity, its running install and its last
     * progress report; only the offer it shows is replaced. That is what lets a view watch one row
     * rather than be rebuilt every time a state moves, and it is why an install started from a row
     * can still be cancelled from that row after the state has changed to installing.
     */
    public void refresh() {
        if (tools == null) {
            rows.clear();
            summary.set(unavailableReason);
            return;
        }
        Map<String, ToolRowViewModel> existing = new LinkedHashMap<>();
        for (ToolRowViewModel row : rows) {
            existing.put(row.key(), row);
        }
        Map<String, Integer> seen = new LinkedHashMap<>();
        List<ToolRowViewModel> next = new ArrayList<>();
        for (ToolOffer offer : tools.offers()) {
            String key = keyFor(offer, seen);
            ToolRowViewModel row = existing.get(key);
            if (row == null) {
                row = new ToolRowViewModel(key, offer);
            } else {
                row.update(offer);
            }
            next.add(row);
        }
        rows.setAll(next);
        summary.set(summaryFor(next.size()));
    }

    /**
     * Starts installing the build a row is showing.
     *
     * <p>Returns as soon as the install is under way, which is the port's own promise. The rows are
     * read again immediately, because the manager reports the build as installing from the moment
     * this call returns, and again when the install reaches a terminal phase -- and at no point in
     * between, because reading the rows verifies every installed entry against its recorded
     * checksums and doing that once per transferred chunk would freeze the interface it is supposed
     * to be updating.
     *
     * @param row the row to install
     * @throws NullPointerException if {@code row} is {@code null}
     * @throws IllegalStateException if this host has no Tool Manager
     * @throws IllegalArgumentException if {@code row} is not one of this view-model's rows, or if
     *     the port names no installable offer for it
     */
    public void install(ToolRowViewModel row) {
        Objects.requireNonNull(row, "row");
        requireAvailable();
        requireOwnRow(row);
        ToolOffer offer = row.offer();
        InstallHandle handle =
                tools.install(
                        offer.tool(),
                        offer.version(),
                        progress -> uiThread.execute(() -> onProgress(row, progress)));
        row.installStarted(handle);
        refresh();
    }

    /**
     * Asks the install a row started to stop.
     *
     * @param row the row whose install should stop
     * @throws NullPointerException if {@code row} is {@code null}
     * @throws IllegalArgumentException if {@code row} is not one of this view-model's rows
     */
    public void cancel(ToolRowViewModel row) {
        Objects.requireNonNull(row, "row");
        requireOwnRow(row);
        row.cancelInstall();
    }

    /**
     * What a list of this many builds is described as.
     *
     * @param builds how many rows there are, zero or more
     * @return the sentence
     * @throws IllegalArgumentException if {@code builds} is negative, naming the value
     */
    public static String summaryFor(int builds) {
        if (builds < 0) {
            throw new IllegalArgumentException(
                    "a tool build count cannot be negative, but was: " + builds);
        }
        if (builds == 0) {
            return "No tool builds on this host.";
        }
        if (builds == 1) {
            return "1 tool build on this host.";
        }
        return builds + " tool builds on this host.";
    }

    /**
     * The key for one offer, given how many offers of the same release have already been seen.
     *
     * @param offer the offer to key
     * @param seen how many rows each tool-and-version has produced so far, updated here
     * @return the key
     */
    private static String keyFor(ToolOffer offer, Map<String, Integer> seen) {
        String release = offer.tool().id() + "-" + offer.version().text().replace('.', '_');
        int ordinal = seen.merge(release, 1, Integer::sum);
        return release + "-" + ordinal;
    }

    private void requireAvailable() {
        if (tools == null) {
            throw new IllegalStateException(
                    "this host has no Tool Manager, so nothing can be installed from it: "
                            + unavailableReason);
        }
    }

    private void requireOwnRow(ToolRowViewModel row) {
        for (ToolRowViewModel own : rows) {
            if (own == row) {
                return;
            }
        }
        throw new IllegalArgumentException(
                "this Tool Manager is not showing the row " + row + ", so it cannot act on it");
    }

    /**
     * Shows one progress report, already on the interface thread.
     *
     * @param row the row the install belongs to
     * @param report where the install has got to
     */
    private void onProgress(ToolRowViewModel row, InstallProgress report) {
        row.reportProgress(report);
        if (report.phase().isTerminal()) {
            row.installFinished();
            refresh();
        }
    }
}
