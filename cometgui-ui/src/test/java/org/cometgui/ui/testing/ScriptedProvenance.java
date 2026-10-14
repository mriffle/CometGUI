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

package org.cometgui.ui.testing;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.cometgui.ui.viewmodel.provenance.OpenedRecord;
import org.cometgui.ui.viewmodel.provenance.ProvenancePort;
import org.cometgui.workflow.provenance.DiskVerification;
import org.cometgui.workflow.provenance.ProvenanceView;
import org.cometgui.workflow.provenance.RunListing;

/**
 * A Provenance port that answers from a script and records every call: which method, with what, and
 * whether the test said the call was allowed where it was made -- so that a test can prove the
 * view-model reaches the port only from its background executor.
 */
public final class ScriptedProvenance implements ProvenancePort {

    /** What an action answers: a value, or a failure. */
    @FunctionalInterface
    public interface Answer<T> {

        /**
         * The answer.
         *
         * @return the value
         * @throws IOException to refuse
         */
        T answer() throws IOException;
    }

    /** One opened run, as a test builds it. */
    public record Record(
            RunListing listing,
            ProvenanceView view,
            Optional<String> recordProblem,
            boolean canExport)
            implements OpenedRecord {}

    /**
     * One call the port received.
     *
     * @param method the method's name
     * @param argument what it was called with, in words
     * @param allowed what the test's guard said when the call was made
     */
    public record Call(String method, String argument, boolean allowed) {}

    private final BooleanSupplier allowed;

    private final List<Call> calls = Collections.synchronizedList(new ArrayList<>());

    private volatile Answer<List<RunListing>> runs = List::of;

    private volatile OpenedRecord opened;

    private volatile IOException openFailure;

    private volatile Answer<Path> exportAnswer;

    private volatile Answer<DiskVerification> verifyAnswer =
            () -> new DiskVerification(List.of(), true);

    private volatile Answer<String> locationAnswer = () -> "the file manager was started";

    /** The stop switch the last verification was given. */
    private volatile BooleanSupplier lastCancel;

    /** A port whose every call is allowed: no runs, and nothing to open. */
    public ScriptedProvenance() {
        this(() -> true);
    }

    /**
     * A port that records, for every call, what a guard says.
     *
     * @param allowed asked at each call whether it is made where it may be
     */
    public ScriptedProvenance(BooleanSupplier allowed) {
        this.allowed = Objects.requireNonNull(allowed, "allowed");
    }

    /**
     * Sets what {@link #runs()} answers.
     *
     * @param answer the answer
     * @return this port
     */
    public ScriptedProvenance runs(Answer<List<RunListing>> answer) {
        this.runs = Objects.requireNonNull(answer, "answer");
        return this;
    }

    /**
     * Sets what {@link #open} answers, whatever run is asked for.
     *
     * @param record the record
     * @return this port
     */
    public ScriptedProvenance opens(OpenedRecord record) {
        this.opened = Objects.requireNonNull(record, "record");
        this.openFailure = null;
        return this;
    }

    /**
     * Makes {@link #open} refuse.
     *
     * @param failure what it throws
     * @return this port
     */
    public ScriptedProvenance openFails(IOException failure) {
        this.openFailure = Objects.requireNonNull(failure, "failure");
        return this;
    }

    /**
     * Sets what both exports answer.
     *
     * @param answer the answer
     * @return this port
     */
    public ScriptedProvenance exports(Answer<Path> answer) {
        this.exportAnswer = Objects.requireNonNull(answer, "answer");
        return this;
    }

    /**
     * Sets what a verification answers.
     *
     * @param answer the answer
     * @return this port
     */
    public ScriptedProvenance verifies(Answer<DiskVerification> answer) {
        this.verifyAnswer = Objects.requireNonNull(answer, "answer");
        return this;
    }

    /**
     * Sets what opening a location answers.
     *
     * @param answer the answer
     * @return this port
     */
    public ScriptedProvenance locates(Answer<String> answer) {
        this.locationAnswer = Objects.requireNonNull(answer, "answer");
        return this;
    }

    /**
     * Every call so far, oldest first.
     *
     * @return a copy
     */
    public List<Call> calls() {
        synchronized (calls) {
            return List.copyOf(calls);
        }
    }

    /**
     * The stop switch the last verification was given.
     *
     * @return it, or {@code null} before any verification
     */
    public BooleanSupplier lastCancel() {
        return lastCancel;
    }

    @Override
    public List<RunListing> runs() throws IOException {
        record("runs", "");
        return runs.answer();
    }

    @Override
    public OpenedRecord open(RunListing run) throws IOException {
        record("open", run.directoryName());
        if (openFailure != null) {
            throw openFailure;
        }
        if (opened == null) {
            throw new IOException("nothing is scripted to open");
        }
        return opened;
    }

    @Override
    public Path exportJson(OpenedRecord run, Path target, boolean replaceExisting)
            throws IOException {
        record("exportJson", target + (replaceExisting ? " replacing" : ""));
        return export();
    }

    @Override
    public Path exportRst(OpenedRecord run, Path target, boolean replaceExisting)
            throws IOException {
        record("exportRst", target + (replaceExisting ? " replacing" : ""));
        return export();
    }

    @Override
    public DiskVerification verify(OpenedRecord run, BooleanSupplier cancelled) throws IOException {
        record("verify", run.listing().directoryName());
        lastCancel = cancelled;
        return verifyAnswer.answer();
    }

    @Override
    public String openLocation(Path file) throws IOException {
        record("openLocation", file.toString());
        return locationAnswer.answer();
    }

    private Path export() throws IOException {
        if (exportAnswer == null) {
            throw new IOException("no export is scripted");
        }
        return exportAnswer.answer();
    }

    private void record(String method, String argument) {
        calls.add(new Call(method, argument, allowed.getAsBoolean()));
    }
}
