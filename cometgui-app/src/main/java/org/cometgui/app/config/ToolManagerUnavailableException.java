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

package org.cometgui.app.config;

import java.io.Serial;

/**
 * This machine has no Tool Manager, and this is the sentence to tell the user.
 *
 * <p>Thrown by {@link ToolManagerWiring#forThisApplication} for the two things that can stop the
 * composition root building one: a platform the product publishes nothing for, and an artefact
 * manifest or a probe seam that cannot be read. Both are facts about the machine rather than
 * programming errors, and both have to reach the Tool Manager section as an explanation, so they
 * are a checked exception carrying a message written for a scientist rather than an {@code
 * IllegalStateException} a caller would have to guess at.
 *
 * <p>The application does not fail to start because of one. It shows the section with the reason in
 * it, which is the same honesty the rest of this phase applies to a build upstream does not
 * publish.
 */
public final class ToolManagerUnavailableException extends Exception {

    @Serial private static final long serialVersionUID = 1L;

    /**
     * The exception with a message the Tool Manager section shows as it stands.
     *
     * @param message what to tell the user, in a sentence
     */
    public ToolManagerUnavailableException(String message) {
        super(message);
    }

    /**
     * The exception over an underlying failure.
     *
     * @param message what to tell the user, in a sentence
     * @param cause what actually failed
     */
    public ToolManagerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
