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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.secrets.Credential;
import org.cometgui.domain.secrets.SessionSecrets;
import org.cometgui.ui.viewmodel.limelight.LimelightUploadPort;
import org.cometgui.ui.viewmodel.limelight.LimelightUploadPreview;
import org.cometgui.ui.viewmodel.limelight.LimelightUploaded;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.workflow.limelight.LimelightUploadRefusedException;
import org.cometgui.workflow.limelight.LimelightUploadWatcher;

/**
 * A Limelight upload port a test scripts: the store's explanation, a run's converted files, the
 * server and project rules (a small stand-in for the workflow's: {@code https://} anything, {@code
 * http://127.0.0.1...} only, positive whole numbers), keys admitted through a real {@link
 * SessionSecrets}, kept keys in a map, and what a destination check and a send answer -- recording
 * every call, and the watcher of each send so that a test can drive its log and progress.
 */
public class ScriptedLimelightUpload implements LimelightUploadPort {

    /** What a check of a destination or a send answers. */
    @FunctionalInterface
    public interface Answer<T> {

        /**
         * The answer.
         *
         * @return it
         * @throws IOException to refuse
         */
        T answer() throws IOException;
    }

    /** One destination the port was asked to check. */
    public record Prepare(ResultsRun run, Path file, String server, String project) {}

    /** One send the port was asked for. */
    public record Send(
            LimelightUploadPreview confirmed,
            Optional<Credential> typed,
            boolean remember,
            LimelightUploadWatcher watcher) {}

    /** A destination as the port shows it. */
    public record Preview(
            RunId run,
            String server,
            int projectId,
            String projectPage,
            Path file,
            long size,
            String sha256)
            implements LimelightUploadPreview {}

    private final SessionSecrets secrets = new SessionSecrets();

    private String store = "kept for this session only, in this test";

    private String storeFailure;

    private final Map<String, List<Path>> files = new HashMap<>();

    private String filesFailure;

    private final Map<String, Credential> kept = new HashMap<>();

    private String keptFailure;

    private String forgetFailure;

    private Answer<LimelightUploadPreview> prepare;

    private Answer<LimelightUploaded> send;

    private final List<String> admitted = new ArrayList<>();

    private final List<Prepare> prepares = new ArrayList<>();

    private final List<Send> sends = new ArrayList<>();

    private final List<String> forgotten = new ArrayList<>();

    private int fileReads;

    /** A port with no files, no kept key, and no destination or send answer scripted yet. */
    public ScriptedLimelightUpload() {
        this.prepare =
                () -> {
                    throw new IOException("no destination is scripted");
                };
        this.send =
                () -> {
                    throw new IOException("no send is scripted");
                };
    }

    /**
     * Sets the store's explanation.
     *
     * @param explanation it
     */
    public void store(String explanation) {
        this.store = explanation;
    }

    /**
     * Makes reading the store's explanation fail.
     *
     * @param failure the message
     */
    public void storeFails(String failure) {
        this.storeFailure = failure;
    }

    /**
     * Sets a run's converted files, newest first.
     *
     * @param run the run's id
     * @param converted the files
     */
    public void files(String run, List<Path> converted) {
        files.put(run, List.copyOf(converted));
    }

    /**
     * Makes reading the converted files fail, or succeed again.
     *
     * @param failure the message; {@code null} to succeed
     */
    public void filesFail(String failure) {
        this.filesFailure = failure;
    }

    /**
     * Keeps a key for a server, as a store would.
     *
     * @param server the normalised server
     * @param key the key's characters
     */
    public void keep(String server, String key) {
        kept.put(server, secrets.admit(key));
    }

    /**
     * Makes reading whether a key is kept fail.
     *
     * @param failure the message
     */
    public void keptFails(String failure) {
        this.keptFailure = failure;
    }

    /**
     * Makes forgetting fail.
     *
     * @param failure the message
     */
    public void forgetFails(String failure) {
        this.forgetFailure = failure;
    }

    /**
     * Sets what a destination check answers.
     *
     * @param answer it
     */
    public void prepare(Answer<LimelightUploadPreview> answer) {
        this.prepare = Objects.requireNonNull(answer, "answer");
    }

    /**
     * Sets what a send answers.
     *
     * @param answer it
     */
    public void send(Answer<LimelightUploaded> answer) {
        this.send = Objects.requireNonNull(answer, "answer");
    }

    /**
     * The session's secrets the keys were admitted to.
     *
     * @return them
     */
    public SessionSecrets secrets() {
        return secrets;
    }

    /**
     * Every key admitted, as typed: a test's own record, which no published text may contain.
     *
     * @return a copy
     */
    public List<String> admitted() {
        return List.copyOf(admitted);
    }

    /**
     * Every destination check asked for.
     *
     * @return a copy
     */
    public List<Prepare> prepares() {
        return List.copyOf(prepares);
    }

    /**
     * Every send asked for.
     *
     * @return a copy
     */
    public List<Send> sends() {
        return List.copyOf(sends);
    }

    /**
     * Every server whose key was forgotten.
     *
     * @return a copy
     */
    public List<String> forgotten() {
        return List.copyOf(forgotten);
    }

    /**
     * How many times the converted files were read.
     *
     * @return the count
     */
    public int fileReads() {
        return fileReads;
    }

    @Override
    public String credentialStore() throws IOException {
        if (storeFailure != null) {
            throw new IOException(storeFailure);
        }
        return store;
    }

    @Override
    public List<Path> convertedFiles(ResultsRun run) throws IOException {
        fileReads++;
        if (filesFailure != null) {
            throw new IOException(filesFailure);
        }
        return files.getOrDefault(run.id().value(), List.of());
    }

    @Override
    public String server(String typed) throws LimelightUploadRefusedException {
        String trimmed = typed.strip();
        if (trimmed.startsWith("https://") || trimmed.startsWith("http://127.0.0.1")) {
            return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        }
        throw new LimelightUploadRefusedException(
                LimelightUploadRefusedException.Reason.SERVER,
                "plain http would send the submit key across the network unencrypted; use https");
    }

    @Override
    public int project(String typed) throws LimelightUploadRefusedException {
        String trimmed = typed.strip();
        if (trimmed.matches("[0-9]{1,9}") && Integer.parseInt(trimmed) > 0) {
            return Integer.parseInt(trimmed);
        }
        throw new LimelightUploadRefusedException(
                LimelightUploadRefusedException.Reason.PROJECT,
                "a Limelight project id is a positive whole number, not \"" + typed + "\"");
    }

    @Override
    public Credential admit(String typed) {
        Credential credential = secrets.admit(typed);
        admitted.add(typed);
        return credential;
    }

    @Override
    public boolean keyKept(String server) throws IOException {
        if (keptFailure != null) {
            throw new IOException(keptFailure);
        }
        return kept.containsKey(server);
    }

    @Override
    public void forgetKey(String server) throws IOException {
        if (forgetFailure != null) {
            throw new IOException(forgetFailure);
        }
        forgotten.add(server);
        kept.remove(server);
    }

    @Override
    public LimelightUploadPreview prepare(ResultsRun run, Path file, String server, String project)
            throws IOException {
        prepares.add(new Prepare(run, file, server, project));
        return prepare.answer();
    }

    @Override
    public LimelightUploaded send(
            LimelightUploadPreview confirmed,
            Optional<Credential> typed,
            boolean remember,
            LimelightUploadWatcher watcher)
            throws IOException {
        sends.add(new Send(confirmed, typed, remember, watcher));
        return send.answer();
    }
}
