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

package org.cometgui.install.download;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Decides, before any socket is opened, whether this product is allowed to fetch a URL.
 *
 * <h2>The rule</h2>
 *
 * <p><strong>A managed download uses {@code https}.</strong> {@code R-SEC-02} makes the SHA-256 the
 * sole integrity authority and the specification's supply-chain section says "use HTTPS for managed
 * downloads" in as many words. Plain HTTP hands any intermediary on the path the chance to choose
 * the bytes; the checksum would still catch it, but a product that lets an attacker choose what it
 * writes to disk before checking is one bug away from executing it.
 *
 * <p><strong>The one exception is a loopback address, and it is an address, never a name.</strong>
 * {@code http://127.0.0.1/...} does not leave the machine, so there is no intermediary for the rule
 * to protect against; the packets never reach a network interface. That exception is what lets the
 * installer be tested against a real HTTP server serving real artefact bytes -- which is the phase
 * design recorded in the work log, "artefact bytes for routine tests come from a gitignored mirror,
 * served over loopback".
 *
 * <p><strong>It cannot be reached from the manifest.</strong> {@code ArtefactValues.downloadUrl}
 * already refuses anything but {@code https} for every record in {@code manifests/tools.json}, so
 * no artefact this product can be asked to install can carry an {@code http} URL at all. The
 * loopback exception is therefore unreachable from product data, and both halves are tested: this
 * class rejects {@code http} to a routable host, and the registry rejects {@code http} outright.
 *
 * <h2>What else is refused</h2>
 *
 * <p>A relative URL, a URL with no host, any scheme that is not {@code http} or {@code https}, and
 * <em>any</em> user-info component. Credentials are refused for the reason {@code D-008} gives:
 * these are public release artefacts, so a user-info component is either a mistake or a secret
 * about to be written into a provenance record. The refusal also matters after a redirect -- {@link
 * HttpDownloader} re-checks the URL the response actually came from, and a redirect to a
 * credential-bearing URL is refused there.
 */
final class DownloadUrls {

    /** The scheme a download off this machine must use. */
    static final String SECURE_SCHEME = "https";

    /** The scheme permitted only to a loopback address. */
    static final String PLAIN_SCHEME = "http";

    /**
     * IPv4 literals in {@code 127.0.0.0/8} and the IPv6 loopback, as {@link URI} spells them.
     *
     * <p>Literals only. A host <em>name</em> -- {@code localhost} included -- is refused, because
     * accepting one would make the rule depend on what a resolver says at the moment the check
     * runs, and the check would then be answering a different question from the connection made
     * afterwards. There is no name this product needs.
     */
    private static final Pattern LOOPBACK_LITERAL =
            Pattern.compile("127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|\\[::1\\]|\\[0:0:0:0:0:0:0:1\\]");

    private DownloadUrls() {
        throw new AssertionError("DownloadUrls is a utility class and is never instantiated");
    }

    /**
     * Requires a URL this product may fetch.
     *
     * @param url the URL to check
     * @param what what the URL is, for the message -- for example {@code "source"} or {@code "the
     *     URL the response came from"}
     * @return the URL
     * @throws NullPointerException if {@code url} is {@code null}
     * @throws IllegalArgumentException if the URL is not fetchable, with a message naming the URL
     *     and saying why the rule exists
     */
    static URI requireFetchable(URI url, String what) {
        Objects.requireNonNull(url, what);
        if (!isFetchable(url)) {
            throw new IllegalArgumentException(
                    what
                            + " must be an https URL with a host and no credentials, because"
                            + " SHA-256 verification is the only integrity authority and a"
                            + " plain-HTTP transfer lets an intermediary choose the bytes"
                            + " (R-SEC-02); plain http is accepted only to a loopback address"
                            + " literal, which no intermediary can reach. Refused: \""
                            + url
                            + "\"");
        }
        return url;
    }

    /**
     * Whether a URL passes the rule.
     *
     * @param url the URL to test
     * @return {@code true} if {@link #requireFetchable} would accept it
     */
    static boolean isFetchable(URI url) {
        /*
         * There is no isAbsolute() clause, and its absence is deliberate: java.net.URI defines an
         * absolute URI as one that has a scheme, so `!isAbsolute()` and `getScheme() == null` are
         * the same condition and writing both would be a clause that can never be the one that
         * decides.
         */
        String scheme = url.getScheme();
        if (scheme == null || url.getUserInfo() != null) {
            return false;
        }
        String host = url.getHost();
        if (host == null) {
            /*
             * java.net.URI yields a null host for every authority it cannot parse as a server --
             * "https:///p", "https://:8080/p" -- so this covers the empty-host case too; there is
             * no separate isEmpty() clause because no input can produce an empty non-null host.
             * An IPv6 literal comes back with its brackets, which is the form LOOPBACK_LITERAL
             * matches.
             */
            return false;
        }
        String lowerCase = scheme.toLowerCase(Locale.ROOT);
        if (SECURE_SCHEME.equals(lowerCase)) {
            return true;
        }
        return PLAIN_SCHEME.equals(lowerCase) && isLoopbackLiteral(host);
    }

    /**
     * Whether a host component is a loopback address literal.
     *
     * @param host the host component, as {@link URI#getHost()} returns it
     * @return {@code true} for {@code 127.x.y.z} and the IPv6 loopback, {@code false} for every
     *     name including {@code localhost}
     */
    private static boolean isLoopbackLiteral(String host) {
        return LOOPBACK_LITERAL.matcher(host.toLowerCase(Locale.ROOT)).matches();
    }
}
