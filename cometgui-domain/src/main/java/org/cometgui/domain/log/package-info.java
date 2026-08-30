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

/**
 * The bounded console message model: what one console line is, how severe it is, and the capped
 * buffer the console pane binds to.
 *
 * <p>{@code R-PROC-03} has two halves. Process output must reach the run's log files on disk as it
 * arrives -- that half belongs to the process service, in phase 03 -- and the in-memory console
 * buffer must be capped with a documented retention policy, so that a tool emitting hundreds of
 * megabytes cannot exhaust the heap. This package is the second half, and {@link
 * org.cometgui.domain.log.BoundedMessageLog} is where the policy is written down and enforced.
 *
 * <p>Three things are deliberately absent. There is no JavaFX here, because the console model is
 * domain state that a headless test must be able to flood; there is no process, file or stream
 * handling, because a message arrives already read; and there is no call to {@code Instant.now()},
 * because the instant comes with the message from a caller holding a {@link java.time.Clock}.
 *
 * <p>The link to the workflow points the other way from what one might expect: a message carries an
 * {@link org.cometgui.domain.run.StageTag} so the console can filter by workflow stage, and the
 * workflow module's stage enumeration implements that interface. The domain does not depend on the
 * workflow.
 */
package org.cometgui.domain.log;
