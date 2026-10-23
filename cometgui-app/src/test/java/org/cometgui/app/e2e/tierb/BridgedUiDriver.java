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

package org.cometgui.app.e2e.tierb;

import java.util.List;
import java.util.Objects;
import org.cometgui.app.uidriver.RemotableUiDriver;

/**
 * The {@link RemotableUiDriver} of a packaged application in another process: every method is one
 * request over the bridge, executed there by the {@code RobotFxUiDriver} the bridge attached -- the
 * same driver class Tier A's robot runs (P14-3). Nothing is decided here: no lookup, no retry, no
 * wait. A failure the remote driver reports comes back as the {@code AssertionError} with its
 * message that the in-process driver would have thrown.
 */
public final class BridgedUiDriver implements RemotableUiDriver {

    private final BridgeClient bridge;

    /**
     * Over a bridge connection.
     *
     * @param bridge the connection
     */
    public BridgedUiDriver(BridgeClient bridge) {
        this.bridge = Objects.requireNonNull(bridge, "bridge");
    }

    @Override
    public boolean exists(String id) {
        return bridge.flag("exists", id);
    }

    @Override
    public boolean isVisible(String id) {
        return bridge.flag("isVisible", id);
    }

    @Override
    public boolean isDisabled(String id) {
        return bridge.flag("isDisabled", id);
    }

    @Override
    public void clickOn(String id) {
        bridge.call("clickOn", id);
    }

    @Override
    public void type(String text) {
        bridge.call("type", text);
    }

    @Override
    public void typeInto(String id, String text) {
        bridge.call("typeInto", id, text);
    }

    @Override
    public void pressKey(String key) {
        bridge.call("pressKey", key);
    }

    @Override
    public void pressKeyWith(String modifier, String key) {
        bridge.call("pressKeyWith", modifier, key);
    }

    @Override
    public void tab() {
        bridge.call("tab");
    }

    @Override
    public void shiftTab() {
        bridge.call("shiftTab");
    }

    @Override
    public String focusedNodeId() {
        return bridge.text("focusedNodeId");
    }

    @Override
    public String textOf(String id) {
        return bridge.text("textOf", id);
    }

    @Override
    public String accessibleTextOf(String id) {
        return bridge.text("accessibleTextOf", id);
    }

    @Override
    public void select(String id, String item) {
        bridge.call("select", id, item);
    }

    @Override
    public String selectedTextOf(String id) {
        return bridge.text("selectedTextOf", id);
    }

    @Override
    public List<String> itemsOf(String id) {
        return bridge.lines("itemsOf", id);
    }

    @Override
    public List<String> columnsOf(String id) {
        return bridge.lines("columnsOf", id);
    }

    @Override
    public List<String> rowsOf(String id) {
        return bridge.lines("rowsOf", id);
    }

    @Override
    public boolean isSelected(String id) {
        return bridge.flag("isSelected", id);
    }

    @Override
    public String toString() {
        return "the packaged application's RobotFxUiDriver, over the bridge at "
                + bridge.endpoint();
    }
}
