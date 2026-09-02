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

package org.cometgui.install.probe;

import java.io.IOException;
import java.io.Serial;
import java.util.Objects;
import org.cometgui.domain.tools.ProbeFailureKind;
import org.cometgui.domain.tools.ProbeStage;

/**
 * A probe stage refused a build, with the kind of failure and the stage it belongs to.
 *
 * <p>Thrown rather than returned, because {@code org.cometgui.install.cache.ToolProbe}'s contract
 * is that refusing is throwing: {@code R-TOOL-06} says a tool that fails loadability is never
 * offered, so the install stops before the staged payload becomes a cache entry.
 *
 * <p><strong>The message is the whole {@code R-PLAT-03} diagnostic where there is one</strong>, so
 * that a failure surfacing through a plain {@code IOException} still tells the reader what was
 * required, what this host has and what to do instead -- rather than an opaque non-zero exit, which
 * {@code R-PLAT-03} forbids in as many words.
 *
 * <p>Only serializable parts are held, which is why the structured {@code
 * org.cometgui.domain.tools.LoaderDiagnostic} is not one of them: an {@code Optional} field would
 * make this exception unserializable. The record itself is what {@link ProbeGatedOffers} works in,
 * and that is the path the Tool Manager uses.
 */
public final class ProbeFailedException extends IOException {

    @Serial private static final long serialVersionUID = 1L;

    /** What went wrong. Enums are serializable, so this class stays so. */
    private final ProbeFailureKind kind;

    /**
     * Creates the failure.
     *
     * @param kind what went wrong
     * @param message the whole diagnostic, or one sentence naming what was refused and why
     * @throws NullPointerException if either argument is {@code null}
     */
    public ProbeFailedException(ProbeFailureKind kind, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    /**
     * What went wrong.
     *
     * @return the kind, never {@code null}
     */
    public ProbeFailureKind kind() {
        return kind;
    }

    /**
     * Which of {@code R-TOOL-06}'s three stages refused.
     *
     * @return the stage the kind belongs to
     */
    public ProbeStage stage() {
        return kind.stage();
    }
}
