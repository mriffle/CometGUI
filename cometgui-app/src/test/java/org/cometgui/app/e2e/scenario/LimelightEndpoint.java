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

package org.cometgui.app.e2e.scenario;

import java.util.List;

/**
 * The controlled Limelight endpoint canonical scenario step 27 uploads to ({@code D-007}: a local
 * fake by default, never a production server), as a tier supplies it: where it is, which project
 * and test key it accepts, and what it received -- so that the scenario can type the destination
 * into the interface and then check, from the endpoint's side, what arrived.
 *
 * <p>Tier A wraps the loopback {@code FakeLimelightServer} in its own JVM; Tier B (unit 7) can wrap
 * the same fake, since the fake runs in the test's JVM and the packaged application reaches it over
 * loopback. Everything crossing this interface is text, numbers or a list of records of them.
 */
public interface LimelightEndpoint {

    /**
     * The server's address, as a scientist types it into the upload's server field.
     *
     * @return for example {@code http://127.0.0.1:40123/limelight}
     */
    String server();

    /**
     * The project the endpoint holds, as typed into the project field.
     *
     * @return the project's identifier, for example {@code 7}
     */
    String project();

    /**
     * The submit key the endpoint accepts: a test key made for this run, which step 28 then
     * searches every file for.
     *
     * @return the key; printable ASCII the driver can type, without blanks
     */
    String key();

    /**
     * How many requests of any kind the endpoint has received.
     *
     * @return the count
     */
    int requests();

    /**
     * Every file upload the endpoint received, oldest first.
     *
     * @return the uploads
     */
    List<Received> uploads();

    /**
     * Every request or answer that did not match the protocol's wire schema.
     *
     * @return the violations; empty when the protocol was followed
     */
    List<String> violations();

    /**
     * One file upload as the endpoint saw it.
     *
     * @param trackingId the identifier the endpoint gave the upload ({@code uploadKey})
     * @param bytes how many bytes of the file arrived
     * @param sha256 the endpoint's own SHA-256 of what arrived
     * @param verdict {@code VALID} when the file was valid against the converter's schema, else the
     *     first reason it was refused
     * @param submitted whether the final submit queued the import
     * @param importerSubDir the directory the final submit named, or empty text
     */
    record Received(
            String trackingId,
            long bytes,
            String sha256,
            String verdict,
            boolean submitted,
            String importerSubDir) {}
}
