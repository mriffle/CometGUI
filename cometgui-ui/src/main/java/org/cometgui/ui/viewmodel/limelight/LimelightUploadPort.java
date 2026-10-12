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

package org.cometgui.ui.viewmodel.limelight;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.secrets.Credential;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.workflow.limelight.LimelightUploadRefusedException;
import org.cometgui.workflow.limelight.LimelightUploadWatcher;

/**
 * What the Limelight section's upload half asks of the application ({@code R-SEC-03}, {@code
 * R-SEC-04}; phase 12, work unit 7, design decisions P12-10, P12-12, P12-15, P12-19): a run's
 * converted files, the server and project checked by the workflow's rules, the submit key admitted
 * to the session and kept or forgotten in the credential store this computer gets, a destination
 * checked and shown -- sending nothing -- and the upload of a destination the scientist confirmed.
 *
 * <p>Every method but {@link #admit} may block and is called off the interface thread. Every answer
 * is the workflow's ({@code org.cometgui.workflow.limelight.LimelightUploads}) or the credential
 * store's: the port finds the run in the session's project and hands text to them.
 *
 * <p><strong>Two steps.</strong> {@link #prepare} checks where an upload would go and what it would
 * send, and sends nothing. {@link #send} uploads a destination {@link #prepare} returned -- the
 * scientist's explicit action, once per destination -- and is the only method that sends anything
 * anywhere.
 */
public interface LimelightUploadPort {

    /**
     * Where a kept key is held on this computer, and why there, in the scientist's words: for
     * example that it is kept for this session only, because no desktop keyring can be reached.
     *
     * @return the explanation
     * @throws IOException if the store cannot be chosen
     */
    String credentialStore() throws IOException;

    /**
     * The Limelight XML files a run's conversions produced, as its event log records them.
     *
     * @param run the run, as the Results section lists it
     * @return each file's absolute path, <strong>newest first</strong>; empty when nothing has been
     *     converted
     * @throws IOException if the run or its event log cannot be read
     */
    List<Path> convertedFiles(ResultsRun run) throws IOException;

    /**
     * Checks a server address by the workflow's rule -- an absolute https URL, or http to this
     * computer only (P12-19) -- and normalises it.
     *
     * @param typed the address as typed
     * @return the normalised address: what is shown and what a kept key is kept under
     * @throws LimelightUploadRefusedException naming what is wrong with it, without repeating it
     */
    String server(String typed) throws LimelightUploadRefusedException;

    /**
     * Checks a project id by the workflow's rule: a positive whole number.
     *
     * @param typed the id as typed
     * @return the id
     * @throws LimelightUploadRefusedException naming what is wrong with it
     */
    int project(String typed) throws LimelightUploadRefusedException;

    /**
     * Admits a typed submit key to the session: registered with the session's one redactor before
     * it is returned, so that no log line, event or export can carry it from this moment on. Does
     * not block; called on the interface thread the moment the key is taken from its field.
     *
     * @param typed the key's characters
     * @return the credential
     * @throws IllegalArgumentException if it is too short, blank or holds a control character; the
     *     message never names it
     */
    Credential admit(String typed);

    /**
     * Whether a key is kept for a server in this computer's credential store.
     *
     * @param server the normalised server address
     * @return {@code true} when one is kept
     * @throws IOException if the store cannot be read; the message never names a key
     */
    boolean keyKept(String server) throws IOException;

    /**
     * Forgets the key kept for a server, if any.
     *
     * @param server the normalised server address
     * @throws IOException if the store cannot forget it
     */
    void forgetKey(String server) throws IOException;

    /**
     * Checks where an upload would go and what it would send: the server and project by the
     * workflow's rules, that the run has ended, and that the file is one of the run's own
     * conversions whose SHA-256, hashed now, is still the one its conversion recorded. Sends
     * nothing and writes nothing.
     *
     * @param run the run
     * @param file one of {@link #convertedFiles}
     * @param server the server address as typed
     * @param project the project id as typed
     * @return the destination, for the section to show and the scientist to confirm or cancel
     * @throws LimelightUploadRefusedException naming why it cannot be used
     * @throws IOException if the run, its event log or the file cannot be read
     */
    LimelightUploadPreview prepare(ResultsRun run, Path file, String server, String project)
            throws IOException;

    /**
     * Uploads a destination the scientist confirmed by pressing Send: the one method that sends.
     * The key is the one typed, when there is one, and otherwise the one kept for the destination's
     * server; when {@code remember} is set and the server queued the import, the typed key is kept
     * for that server afterwards.
     *
     * @param confirmed a destination {@link #prepare} returned; each is sent at most once
     * @param typed the key typed in this session, admitted; empty to use the kept one
     * @param remember whether to keep the typed key for the server once the server queued the
     *     import
     * @param watcher hears the upload log's lines, already redacted, and the bytes sent, on the
     *     uploading threads
     * @return how the upload ended
     * @throws LimelightUploadRefusedException if the destination was sent before, the run is
     *     executing or the file changed; nothing was sent
     * @throws IOException if there is no key to send with (nothing was sent), or the upload's
     *     record could not be written
     */
    LimelightUploaded send(
            LimelightUploadPreview confirmed,
            Optional<Credential> typed,
            boolean remember,
            LimelightUploadWatcher watcher)
            throws IOException;
}
