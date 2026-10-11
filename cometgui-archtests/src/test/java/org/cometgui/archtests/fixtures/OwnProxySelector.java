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

package org.cometgui.archtests.fixtures;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;

/**
 * A proxy selector of the product's own, which would decide for itself where a connection goes. A
 * committed fixture for {@code LimelightUploadRulesTest}.
 */
public final class OwnProxySelector extends ProxySelector {

    @Override
    public List<Proxy> select(URI uri) {
        return List.of(Proxy.NO_PROXY);
    }

    @Override
    public void connectFailed(URI uri, SocketAddress address, IOException failure) {
        // nothing
    }
}
