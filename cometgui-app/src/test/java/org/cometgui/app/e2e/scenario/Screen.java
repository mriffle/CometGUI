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

package org.cometgui.app.e2e.scenario;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import org.cometgui.app.uidriver.Await;
import org.cometgui.app.uidriver.RemotableUiDriver;

/**
 * The few things every scenario step does to the interface, said once, over {@link
 * RemotableUiDriver} alone (P14-3): show a section by its navigation entry, type a value and commit
 * it, and wait -- through {@link Await}, the harness's one wait primitive (P14-8) -- for a
 * control's text to become what a step needs. Nothing here touches a node, a scene or the
 * application thread, so it runs unchanged against a packaged application through the bridge.
 *
 * <p>Every identifier a scenario passes is a hand-typed literal (P7-5: a GUI test never calls the
 * product's {@code UiIds}), so a renamed control fails the scenario rather than following the
 * rename silently.
 */
public final class Screen {

    private final RemotableUiDriver ui;

    /**
     * Over a driver.
     *
     * @param ui the driver
     */
    public Screen(RemotableUiDriver ui) {
        this.ui = Objects.requireNonNull(ui, "ui");
    }

    /**
     * The driver.
     *
     * @return what this screen drives
     */
    public RemotableUiDriver ui() {
        return ui;
    }

    /**
     * Shows a section with a click on its navigation entry, and checks that it is the one shown.
     *
     * @param section the entry's suffix, for example {@code tool-manager}
     */
    public void show(String section) {
        ui.clickOn("nav-" + section);
        if (!ui.isSelected("nav-" + section)) {
            throw new AssertionError("a click on #nav-" + section + " did not select it");
        }
    }

    /**
     * Types a value into a text control in place of what it held, and presses Enter, which commits
     * it -- as a scientist does.
     *
     * @param id the text control
     * @param text the value
     */
    public void enter(String id, String text) {
        ui.typeInto(id, text);
        ui.pressKey("ENTER");
        String shown = ui.textOf(id);
        if (!shown.equals(text)) {
            throw new AssertionError("#" + id + " shows \"" + shown + "\" after typing " + text);
        }
    }

    /**
     * Waits until a control's text satisfies a condition.
     *
     * @param id the control
     * @param what what is awaited, completing "waited ... for"
     * @param wanted the condition
     * @param bound how long to wait at most: a deadlock detector, never a timing assumption
     * @return the text that satisfied it
     */
    public String await(String id, String what, Predicate<String> wanted, Duration bound) {
        return Await.until(what + " (#" + id + ")", () -> ui.textOf(id), wanted, bound);
    }

    /**
     * The texts of the numbered controls {@code <prefix>0}, {@code <prefix>1}, ... that exist, in
     * order: how a scenario reads a list the interface draws as separate labels.
     *
     * @param prefix the identifiers' common part, for example {@code param-search-result-}
     * @return each existing control's text
     */
    public List<String> numbered(String prefix) {
        List<String> texts = new ArrayList<>();
        for (int index = 0; ui.exists(prefix + index); index++) {
            texts.add(ui.textOf(prefix + index));
        }
        return texts;
    }
}
