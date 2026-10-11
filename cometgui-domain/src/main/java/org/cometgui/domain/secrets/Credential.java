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

package org.cometgui.domain.secrets;

/**
 * One credential the application is holding -- a Limelight submit key, say -- that has already been
 * registered with the session's {@link SecretRegistry}.
 *
 * <p><strong>There is no public way to make one.</strong> The only constructor is package-private
 * and its only caller is {@link SessionSecrets#admit(String)}, which registers the value before it
 * returns the credential. So a credential cannot exist in this application without the redactor
 * that every log, event and export writer uses already knowing its exact characters (phase 12,
 * design decision P12-12: "registered with {@code SecretRegistry} at the moment they enter the
 * application").
 *
 * <p><strong>It does not print what it holds.</strong> {@link #toString()} says only that it is a
 * credential, so a credential that ends up in a log line, an exception message or a debugger's
 * variable view by accident discloses nothing. {@link #reveal()} is the one way to the value, and
 * its callers are the places where the value must leave the heap: the request bodies of the
 * Limelight protocol and the standard input of a keychain tool.
 *
 * <p>Equality is identity, deliberately: a value-based {@code equals} would invite comparing a
 * credential to a string, and a {@code hashCode} derived from the value is a small leak of its own.
 */
public final class Credential {

    private final String value;

    /**
     * A credential whose value has been checked and registered by {@link SessionSecrets}.
     *
     * @param value the value, already registered
     */
    Credential(String value) {
        this.value = value;
    }

    /**
     * The credential's characters, for the one place that must send them.
     *
     * @return the value, never {@code null}
     */
    public String reveal() {
        return value;
    }

    /**
     * Describes the credential without disclosing a single character of it.
     *
     * @return a description safe to put in a log line or an exception message
     */
    @Override
    public String toString() {
        return "Credential[redacted]";
    }
}
