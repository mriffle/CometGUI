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

package org.cometgui.tools.process;

import org.cometgui.domain.log.LogMessage;

/**
 * Somewhere a stage's output lines can be appended to. Nothing more.
 *
 * <p>In the assembled application this is {@code boundedMessageLog::append}, and the whole point of
 * this type is that it is a <em>method reference</em> rather than the log.
 *
 * <h2>The decision this interface implements</h2>
 *
 * <p>Phase 02 left {@code org.cometgui.domain.log.BoundedMessageLog} injected into {@code
 * CometGuiApplication} rather than held by the composition root, because a composition root that
 * hands out a mutable shared object is publishing mutable state -- SpotBugs calls that {@code
 * EI_EXPOSE_REP} and it is right. What it left open was what happens when the process service has
 * to append to the same log the console reads. The answer taken by phase 03 is to <strong>narrow
 * the reference rather than move the object</strong>, and it has three consequences that are the
 * reason for the choice:
 *
 * <ul>
 *   <li><strong>Nothing new is published.</strong> The composition root is unchanged, so the {@code
 *       EI_EXPOSE_REP} finding is never created. What crosses the boundary is one method.
 *   <li><strong>The capability is one-directional.</strong> The process service can append. It
 *       cannot read the console back, cannot {@code clear()} it, and cannot learn its capacity or
 *       its discard count. A tool adapter able to empty the user's console is a capability nobody
 *       asked for, and an interface with one method is the cheapest way not to grant it.
 *   <li><strong>The dependency stays legal.</strong> This type names {@link LogMessage} and nothing
 *       else, so the process service keeps its single {@code cometgui-domain} edge and gains no
 *       dependency on the user interface or on the composition root.
 * </ul>
 *
 * <p>The rejected alternative was an accessor on the composition root's services object. It is one
 * line, and it makes every holder of that object a potential writer to -- and clearer of -- the
 * console.
 *
 * <h2>Thread safety is the implementer's problem, and is already paid for</h2>
 *
 * <p>{@link #append} is called from <strong>both</strong> of a stage's pump threads, while the
 * JavaFX application thread reads the console. {@code BoundedMessageLog} synchronises every method
 * body on a private monitor, which is exactly what makes {@code log::append} safe here; this phase
 * does not re-implement that and must not weaken it. An implementation that is not thread safe is a
 * defect in the implementation.
 *
 * <p>An implementation should also not throw. One that does cannot break the run -- the process
 * service's guarded listener catches it and the pump survives -- but the line will still be on disk
 * and missing from the console, which is a confusing thing for a user to look at.
 */
@FunctionalInterface
public interface RunMessageSink {

    /**
     * Appends one message to whatever is showing the run.
     *
     * @param message the message, already redacted and already timestamped from the run's clock
     */
    void append(LogMessage message);
}
