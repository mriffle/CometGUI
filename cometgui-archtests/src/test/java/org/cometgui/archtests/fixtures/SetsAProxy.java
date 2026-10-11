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

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.http.HttpClient;

/**
 * Every way product code could route around the test JVM's loopback sinkhole (phase 12, P12-16): a
 * proxy property set, the default selector replaced, a selector of its own, no proxy at all, a
 * {@link Proxy} of its own. A committed fixture for {@code LimelightUploadRulesTest}; each method
 * is one shortcut the rules must name.
 */
public final class SetsAProxy {

    private SetsAProxy() {}

    static void property() {
        System.setProperty("https.proxyHost", "proxy.invalid");
    }

    static void defaultSelector() {
        ProxySelector.setDefault(ProxySelector.of(new InetSocketAddress("proxy.invalid", 3128)));
    }

    static HttpClient noProxy() {
        return HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build();
    }

    static Proxy direct() {
        return Proxy.NO_PROXY;
    }

    static Proxy ownProxy() {
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy.invalid", 3128));
    }
}
