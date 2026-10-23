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

package org.cometgui.app.e2e.tiera;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.cometgui.app.e2e.scenario.LimelightEndpoint;
import org.cometgui.tools.limelight.fake.FakeLimelightServer;

/**
 * Tier A's controlled Limelight endpoint ({@code D-007}): the one loopback {@link
 * FakeLimelightServer}, which validates every request against the protocol's wire schema and every
 * file against the converter's own XSD, seen through the scenario's {@link LimelightEndpoint}.
 */
final class FakeLimelightEndpoint implements LimelightEndpoint {

    private final FakeLimelightServer fake;

    private final String project;

    private final String key;

    /**
     * Over a running fake.
     *
     * @param fake the fake, listening on loopback
     * @param project the project it holds
     * @param key the test key it accepts
     */
    FakeLimelightEndpoint(FakeLimelightServer fake, int project, String key) {
        this.fake = Objects.requireNonNull(fake, "fake");
        this.project = Integer.toString(project);
        this.key = Objects.requireNonNull(key, "key");
    }

    @Override
    public String server() {
        return fake.baseUrl();
    }

    @Override
    public String project() {
        return project;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public int requests() {
        return fake.requests().size();
    }

    @Override
    public List<Received> uploads() {
        List<Received> received = new ArrayList<>();
        for (FakeLimelightServer.Upload upload : fake.uploads()) {
            received.add(
                    new Received(
                            upload.trackingId(),
                            upload.receivedBytes(),
                            upload.receivedSha256(),
                            upload.verdict(),
                            upload.submitted(),
                            upload.importerSubDir()));
        }
        return List.copyOf(received);
    }

    @Override
    public List<String> violations() {
        return fake.wireViolations();
    }
}
