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
 * Thrown when a value offered to {@link SecretRegistry} is too short to be replaced safely.
 *
 * <p>The registry works by literal substring replacement over every string this application emits.
 * That is what makes it airtight, and it is also what makes a short value dangerous: registering
 * {@code 1.0} would blank out a version number, a tolerance and half of every digest in the
 * provenance record, and registering {@code test} would corrupt every path under a directory called
 * {@code test}. A provenance record silently mangled by its own redactor is a worse outcome than
 * the one the redactor was guarding against, so the registry refuses rather than accepting quietly.
 * See {@link SecretRegistry#MINIMUM_SECRET_LENGTH} for how the threshold was chosen.
 *
 * <p><strong>The message never contains the offending value.</strong> This exception is thrown by
 * the component whose entire purpose is that secret values do not escape into text, and an
 * exception message reaches a log, a stack trace and often a dialog. It carries the value's
 * <em>length</em> instead, which is enough to diagnose the call and useless to anyone reading the
 * log.
 *
 * <p>It extends {@link IllegalArgumentException} because that is what it is -- a rejected argument
 * -- so a caller that does not care to distinguish it still catches it in the usual place.
 */
public final class SecretTooShortException extends IllegalArgumentException {

    /** Fixed, because this type's serialised shape is a single {@code int} and will stay that. */
    private static final long serialVersionUID = 1L;

    /** How long the rejected value was. Never the value itself. */
    private final int offeredLength;

    /**
     * Builds the rejection message from the length alone.
     *
     * @param offeredLength the length of the rejected value, in characters
     */
    SecretTooShortException(int offeredLength) {
        super(
                "a registered secret must be at least "
                        + SecretRegistry.MINIMUM_SECRET_LENGTH
                        + " characters long, but the value offered was "
                        + offeredLength
                        + " characters long; the value itself is deliberately not named here");
        this.offeredLength = offeredLength;
    }

    /**
     * How long the rejected value was.
     *
     * <p>Exposed so that a caller -- or a test -- can assert on the reason without the value ever
     * being formatted into a string.
     *
     * @return the length in characters of the value that was refused
     */
    public int offeredLength() {
        return offeredLength;
    }
}
