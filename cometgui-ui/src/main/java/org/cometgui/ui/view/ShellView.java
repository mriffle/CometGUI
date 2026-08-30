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

package org.cometgui.ui.view;

import static org.cometgui.ui.controls.AccessibleControls.named;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.cometgui.ui.controls.StageStepper;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.controls.derived.ConsolePane;
import org.cometgui.ui.viewmodel.ConsoleViewModel;
import org.cometgui.ui.viewmodel.HostBaselineViewModel;
import org.cometgui.ui.viewmodel.NavigationViewModel;
import org.cometgui.ui.viewmodel.SectionId;
import org.cometgui.ui.viewmodel.StageStepperViewModel;

/**
 * The application shell: a header, a left navigation over every section, and a content area holding
 * exactly the selected section's pane.
 *
 * <h2>All ten panes are built; exactly one is shown</h2>
 *
 * <p>Every section's pane is a child of the content area from the moment the shell is built, and
 * the selection decides which one is visible and managed -- not which one exists. Two things follow
 * that attaching and detaching one pane at a time would not give. A test can look a section up by
 * its stable identifier through {@code Scene.lookup} without first navigating to it, which is what
 * this phase's second exit-gate item asks for; and the enumeration behind the fourth gate item --
 * "every control that exists has an accessible name" -- sees every control in the whole shell in
 * one pass, rather than only the controls of whichever section happened to be selected. Unmanaged
 * children are excluded from layout, so the nine hidden panes cost a construction and nothing per
 * frame.
 *
 * <h2>The view-models are injected, never made here</h2>
 *
 * <p>Every piece of state this shell shows arrives through the constructor. There is no {@code new
 * NavigationViewModel()} and no {@code new BoundedMessageLog(...)} anywhere in this package, and
 * that is a testability requirement rather than a preference: a shell that built its own message
 * log would be showing a second, empty log while the process service filled the real one, and a
 * test could not put a message on the screen at all.
 *
 * <h2>The navigation binds in two steps, never bidirectionally</h2>
 *
 * <p>{@link NavigationViewModel#selectedSectionProperty()} is read-only by design, and its Javadoc
 * says why: a JavaFX selection model reports {@code null} when it is cleared, and a bidirectional
 * binding would push that {@code null} into a view-model that has no such state. So the wiring is
 * two explicit halves -- a listener here that moves the interface when the selection changes, and a
 * button action that calls {@link NavigationViewModel#select(SectionId)} -- and the {@code null}
 * never has anywhere to go.
 *
 * <h2>Keyboard reachability</h2>
 *
 * <p>The phase's first exit-gate item is that every primary section is reachable by mouse and by
 * keyboard alone. Three decisions make that true and, more usefully, make it provable:
 *
 * <ul>
 *   <li><b>The navigation is the first thing Tab reaches.</b> The header holds only labels, which
 *       are not focus traversable, so the first traversable control in the scene is a navigation
 *       entry.
 *   <li><b>Exactly one navigation entry is focus traversable: the selected one.</b> This is the
 *       roving-tab-stop pattern. Tab moves <em>into</em> and <em>out of</em> the navigation in one
 *       press each rather than ten, and arrow keys move within it -- which is how every real
 *       navigation list behaves and what a keyboard user expects.
 *   <li><b>Arrow keys move the selection, through the secondary sections as well.</b> Up and Left
 *       go back, Down and Right go forward, and the handler is an event <em>filter</em> on the
 *       navigation container so that it runs before the focused button's own behaviour and cannot
 *       be beaten to the key by JavaFX's directional focus traversal. The keys are consumed whether
 *       or not the selection moved, so that arrows only ever mean "move the selection" and Tab only
 *       ever means "leave the navigation"; the ends do not wrap, which is {@link
 *       NavigationViewModel}'s documented decision, not this view's.
 * </ul>
 *
 * <p>Mnemonics are deliberately not the mechanism. They are a shortcut for a user who already knows
 * the interface, not a way to reach a control, and a gate item that rested on them would be proved
 * by a test that never moved focus.
 *
 * <h2>Accessible names</h2>
 *
 * <p>Every control here is created through {@link
 * org.cometgui.ui.controls.AccessibleControls#named(javafx.scene.control.Control, String)}, which
 * refuses a blank name. A navigation entry is announced as a radio button rather than a toggle
 * button, because that is what it is: one of a set of which exactly one is chosen.
 */
public final class ShellView extends BorderPane {

    private final NavigationViewModel navigation;

    private final Map<SectionId, SectionPane> panes = new EnumMap<>(SectionId.class);

    private final Map<SectionId, ToggleButton> entries = new EnumMap<>(SectionId.class);

    private final StackPane content = new StackPane();

    private final Label sectionTitle = new Label();

    /**
     * The shell, over the four view-models it presents.
     *
     * @param navigation which sections there are and which one is selected
     * @param hostBaseline the startup host-baseline banner
     * @param stepper the Run screen's stage states
     * @param console the console's filters and the messages they admit
     * @throws NullPointerException if any argument is {@code null}
     */
    public ShellView(
            NavigationViewModel navigation,
            HostBaselineViewModel hostBaseline,
            StageStepperViewModel stepper,
            ConsoleViewModel console) {
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        Objects.requireNonNull(hostBaseline, "hostBaseline");
        Objects.requireNonNull(stepper, "stepper");
        Objects.requireNonNull(console, "console");

        setId(UiIds.SHELL_ROOT);
        content.setId(UiIds.CONTENT);
        content.setPadding(new Insets(4));

        for (SectionId section : navigation.sections()) {
            SectionPane pane = new SectionPane(section);
            panes.put(section, pane);
            content.getChildren().add(pane);
        }

        panes.get(SectionId.RUN).addContent(new StageStepper(stepper));

        /*
         * The console's stage filter is offered the stepper's own stages, in the stepper's own
         * draw order, so that the two cannot come to disagree about which stages exist.
         */
        ConsolePane consolePane = new ConsolePane(console, stepper.stagesInDrawOrder());
        panes.get(SectionId.CONSOLE).addContent(consolePane);
        VBox.setVgrow(consolePane, Priority.ALWAYS);

        setTop(buildHeader(hostBaseline));
        setLeft(buildNavigation());
        setCenter(content);

        navigation.selectedSectionProperty().addListener((property, was, now) -> showSelection());
        showSelection();
    }

    /**
     * The application title, the selected section's title, and the host-baseline banner slot.
     *
     * @param hostBaseline the banner's view-model
     * @return the header
     */
    private VBox buildHeader(HostBaselineViewModel hostBaseline) {
        Label title = new Label("CometGUI");
        title.setId(UiIds.SHELL_TITLE);
        named(title, "CometGUI");

        sectionTitle.setId(UiIds.SHELL_SECTION_TITLE);
        named(sectionTitle, "selected section");

        Label banner = new Label(hostBaseline.bannerText());
        banner.setId(UiIds.HOST_BASELINE_BANNER);
        banner.setWrapText(true);
        named(banner, hostBaseline.bannerText());
        /*
         * The banner stays in the scene graph when the host is fine, hidden and unmanaged: a slot
         * that exists always is one a test can look up and assert is empty, where a slot that is
         * added and removed can only be asserted absent -- which is also what a broken shell looks
         * like.
         */
        banner.setVisible(hostBaseline.bannerVisible());
        banner.setManaged(hostBaseline.bannerVisible());

        VBox header = new VBox(2, title, sectionTitle, banner);
        header.setId(UiIds.SHELL_HEADER);
        header.setPadding(new Insets(8, 12, 8, 12));
        return header;
    }

    /**
     * The left navigation: every section in display order, with the two secondary ones below a
     * rule.
     *
     * @return the navigation container
     */
    private VBox buildNavigation() {
        VBox bar = new VBox(2);
        bar.setId(UiIds.NAVIGATION);
        bar.setPadding(new Insets(8));
        ToggleGroup group = new ToggleGroup();
        boolean separatorPlaced = false;
        for (SectionId section : navigation.sections()) {
            if (section.isSecondary() && !separatorPlaced) {
                Separator separator = new Separator();
                separator.setId(UiIds.NAVIGATION_SEPARATOR);
                named(separator, "secondary sections follow");
                bar.getChildren().add(separator);
                separatorPlaced = true;
            }
            ToggleButton entry = new ToggleButton(section.title());
            entry.setId(UiIds.navigationEntry(section));
            entry.setToggleGroup(group);
            entry.setMaxWidth(Double.MAX_VALUE);
            named(entry, AccessibleRole.RADIO_BUTTON, section.title() + " section");
            entry.setAccessibleHelp(section.description());
            entry.setOnAction(event -> select(section));
            entries.put(section, entry);
            bar.getChildren().add(entry);
        }
        bar.addEventFilter(KeyEvent.KEY_PRESSED, this::moveSelectionOnArrowKey);
        return bar;
    }

    /**
     * Moves the selection with the arrow keys, and keeps the focus on the entry that is selected.
     *
     * @param event a key press that reached the navigation container
     */
    private void moveSelectionOnArrowKey(KeyEvent event) {
        KeyCode code = event.getCode();
        if (code == KeyCode.UP || code == KeyCode.LEFT) {
            navigation.selectPrevious();
        } else if (code == KeyCode.DOWN || code == KeyCode.RIGHT) {
            navigation.selectNext();
        } else {
            return;
        }
        entries.get(navigation.selectedSection()).requestFocus();
        event.consume();
    }

    /**
     * Selects a section from a navigation entry's action.
     *
     * <p>{@link #showSelection()} is called even when the selection did not change, because
     * clicking the entry that is already selected toggles the button off and nothing else would put
     * it back: a property that does not change fires no event.
     *
     * @param section the section the user asked for
     */
    private void select(SectionId section) {
        navigation.select(section);
        showSelection();
    }

    /**
     * Puts the interface in step with the view-model: the selected entry, the single focus stop,
     * the header's section title, and the one pane the content area holds.
     */
    private void showSelection() {
        SectionId selected = navigation.selectedSection();
        for (Map.Entry<SectionId, ToggleButton> entry : entries.entrySet()) {
            boolean isSelected = entry.getKey() == selected;
            entry.getValue().setSelected(isSelected);
            entry.getValue().setFocusTraversable(isSelected);
        }
        sectionTitle.setText(selected.title());
        sectionTitle.setAccessibleText("selected section: " + selected.title());
        for (Map.Entry<SectionId, SectionPane> pane : panes.entrySet()) {
            boolean isSelected = pane.getKey() == selected;
            pane.getValue().setVisible(isSelected);
            pane.getValue().setManaged(isSelected);
        }
    }

    /**
     * One section's pane.
     *
     * @param section the section
     * @return its pane, which exists whether or not the section is selected
     * @throws NullPointerException if {@code section} is {@code null}
     */
    public SectionPane paneFor(SectionId section) {
        return panes.get(Objects.requireNonNull(section, "section"));
    }

    /**
     * One section's navigation entry.
     *
     * @param section the section
     * @return the entry that selects it
     * @throws NullPointerException if {@code section} is {@code null}
     */
    public ToggleButton navigationEntryFor(SectionId section) {
        return entries.get(Objects.requireNonNull(section, "section"));
    }
}
