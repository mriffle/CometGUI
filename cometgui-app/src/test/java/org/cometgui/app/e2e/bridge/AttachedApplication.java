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

package org.cometgui.app.e2e.bridge;

import java.time.Duration;
import org.cometgui.app.uidriver.RemotableUiDriver;

/**
 * The application the bridge serves: found once its window is showing, and then driven by exactly
 * one driver. Inside the packaged application it is {@link FxApplication}; the bridge's own tests
 * give the server a recording stand-in, so that the protocol, the token and the refusals are proved
 * without a window.
 */
interface AttachedApplication {

    /**
     * Waits until the application is showing its window and a driver is attached to it; returns at
     * once if that has already happened.
     *
     * @param bound how long to wait at most: a deadlock detector, not a timing assumption
     * @return the window's title
     * @throws AssertionError if no window showed within the bound, naming what was last observed
     */
    String await(Duration bound);

    /**
     * The one driver every remote operation is executed by.
     *
     * @return the driver
     * @throws IllegalStateException if {@link #await(Duration)} has not yet succeeded
     */
    RemotableUiDriver driver();
}
