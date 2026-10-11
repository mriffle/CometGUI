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

package org.cometgui.tools.limelight;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;

/**
 * A Limelight web application's address, checked and normalised before anything is sent to it
 * (phase 12, design decisions P12-14 and P12-15; {@code R-SEC-04}: "shall show the exact
 * destination server ... before sending").
 *
 * <p>The address is the web application's root, context included, as the scientist's browser shows
 * it: {@code https://limelight.example.org/limelight}. Every protocol call is a path under it, and
 * the project page is {@code <address>/d/pg/project/<id>}.
 *
 * <p><strong>What is refused, and why each.</strong>
 *
 * <ul>
 *   <li>Anything that is not an absolute {@code http} or {@code https} URL with a host.
 *   <li>A user name or password in the URL ({@code https://user:pw@host/...}): a credential has one
 *       way into the application ({@code SessionSecrets.admit}), and a URL is shown, logged and
 *       recorded.
 *   <li>A query or a fragment: the protocol appends paths, and a URL that already carries {@code
 *       ?...} would send its calls somewhere other than the address shown.
 *   <li><strong>Plain {@code http} to anything but this computer.</strong> The submit key travels
 *       in the body of every call (the protocol has no other place for it), so over plain {@code
 *       http} it would cross the network readable by anyone on the path. Loopback ({@code
 *       127.0.0.0/8}, {@code ::1}, {@code localhost}) is allowed because nothing leaves the machine
 *       -- which is also what the test fake needs.
 * </ul>
 *
 * <p><strong>What is normalised.</strong> The scheme and host are lower-cased (both are
 * case-insensitive), surrounding blanks are trimmed and trailing slashes are removed, so {@code
 * HTTPS://Limelight.Example.org/limelight/} and {@code https://limelight.example.org/limelight} are
 * the same server -- and the same key in the credential store. The path's own case is kept: a
 * servlet context is case-sensitive.
 */
public final class LimelightServer {

    /** The path, under the web application, of a project's page. */
    public static final String PROJECT_PAGE_PATH = "/d/pg/project/";

    private final URI webApp;

    private LimelightServer(URI webApp) {
        this.webApp = webApp;
    }

    /**
     * Checks and normalises a server address as the scientist typed it.
     *
     * @param text the address
     * @return the server
     * @throws IllegalArgumentException naming what is wrong with it
     * @throws NullPointerException if {@code text} is {@code null}
     */
    public static LimelightServer parse(String text) {
        Objects.requireNonNull(text, "text");
        String trimmed = text.strip();
        if (trimmed.isEmpty()) {
            throw refused(text, "it is empty");
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException notAUri) {
            throw refused(text, "it is not a URL: " + notAUri.getReason());
        }
        String scheme = uri.getScheme();
        if (scheme == null || !uri.isAbsolute()) {
            throw refused(text, "it is not an absolute URL; it must start with https://");
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            throw refused(text, "only https (or http to this computer) can be used");
        }
        if (uri.getRawUserInfo() != null) {
            throw refused(
                    text,
                    "it carries a user name or password; Limelight takes its submit key"
                            + " separately, never in the address");
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw refused(text, "it names no host");
        }
        if (uri.getRawQuery() != null) {
            throw refused(text, "it has a query (?...); give the web application's address only");
        }
        if (uri.getRawFragment() != null) {
            throw refused(
                    text, "it has a fragment (#...); give the web application's address only");
        }
        host = host.toLowerCase(Locale.ROOT);
        if ("http".equals(scheme) && !isLoopback(host)) {
            throw refused(
                    text,
                    "plain http would send the submit key across the network unencrypted; use"
                            + " https");
        }
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        int end = path.length();
        while (end > 0 && path.charAt(end - 1) == '/') {
            end--;
        }
        path = path.substring(0, end);
        StringBuilder normal = new StringBuilder(scheme).append("://").append(host);
        if (uri.getPort() != -1) {
            normal.append(':').append(uri.getPort());
        }
        normal.append(path);
        return new LimelightServer(URI.create(normal.toString()));
    }

    /**
     * Whether a host name, as written in a URL, is this computer: a {@code 127.} IPv4 literal, the
     * IPv6 loopback literal, or {@code localhost}. Decided from the text alone; nothing is looked
     * up.
     *
     * @param host the host, lower case
     * @return {@code true} for loopback
     */
    static boolean isLoopback(String host) {
        return "localhost".equals(host)
                || "[::1]".equals(host)
                || "::1".equals(host)
                || host.matches("127\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}");
    }

    /**
     * The web application's normalised address.
     *
     * @return it, with no trailing slash
     */
    public URI webApp() {
        return webApp;
    }

    /**
     * A protocol call's full address.
     *
     * @param path the call's path, starting with {@code /}
     * @return the address
     */
    URI endpoint(String path) {
        return URI.create(webApp + path);
    }

    /**
     * The page where the scientist sees a project's imports (P12-15).
     *
     * @param projectId the project's id
     * @return {@code <address>/d/pg/project/<id>}
     * @throws IllegalArgumentException if the id is not positive
     */
    public URI projectPage(int projectId) {
        return URI.create(webApp + PROJECT_PAGE_PATH + checkedProjectId(projectId));
    }

    /**
     * Checks a project id: Limelight's are positive integers.
     *
     * @param projectId the id
     * @return the id
     * @throws IllegalArgumentException if it is zero or negative
     */
    public static int checkedProjectId(int projectId) {
        if (projectId < 1) {
            throw new IllegalArgumentException(
                    "a Limelight project id is a positive whole number, not " + projectId);
        }
        return projectId;
    }

    /**
     * Reads a project id as the scientist typed it.
     *
     * @param text the id
     * @return the id
     * @throws IllegalArgumentException if it is not a positive whole number that fits an {@code
     *     int}
     */
    public static int parseProjectId(String text) {
        Objects.requireNonNull(text, "text");
        String trimmed = text.strip();
        if (!trimmed.matches("[0-9]{1,10}")) {
            throw new IllegalArgumentException(
                    "a Limelight project id is a positive whole number, not \"" + text + "\"");
        }
        long value = Long.parseLong(trimmed);
        if (value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "a Limelight project id is at most " + Integer.MAX_VALUE + ", not " + trimmed);
        }
        return checkedProjectId((int) value);
    }

    /**
     * The normalised address, as shown to the scientist and used as the credential store's account.
     *
     * @return it
     */
    @Override
    public String toString() {
        return webApp.toString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LimelightServer server && webApp.equals(server.webApp);
    }

    @Override
    public int hashCode() {
        return webApp.hashCode();
    }

    /**
     * The refusal. It does not repeat the address: one with a password in it must not be echoed
     * into a message that may be shown or logged.
     */
    private static IllegalArgumentException refused(String text, String why) {
        Objects.requireNonNull(text, "text");
        return new IllegalArgumentException(
                "This address cannot be used as a Limelight server: " + why + ".");
    }
}
