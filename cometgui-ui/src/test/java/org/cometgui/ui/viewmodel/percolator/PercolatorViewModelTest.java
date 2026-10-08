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

package org.cometgui.ui.viewmodel.percolator;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolRegistrationException;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.schema.PercolatorSetting;
import org.cometgui.params.percolator.validation.TestFdr;
import org.cometgui.params.percolator.validation.TrainFdr;
import org.cometgui.results.filtering.PeptideQValueFilter;
import org.cometgui.results.filtering.PsmQValueFilter;
import org.cometgui.ui.testing.Editors.ScriptedChooser;
import org.cometgui.ui.testing.Percolators;
import org.cometgui.ui.testing.ScriptedEngine;
import org.cometgui.ui.viewmodel.params.PercolatorRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The Percolator section's view-model over a scripted Tool Manager port and two executors the test
 * drains by hand: where each call happens, what resolution chooses and what the section says about
 * it, the notice when Limelight conversion is switched, Limelight unavailable with both remedies,
 * advisories, the user's choice, Advanced settings, the display filters, local registration and the
 * request Run is given. Every expected text is typed out here; none is computed by the model.
 */
class PercolatorViewModelTest {

    private static final Path P309 = Path.of("p309", "percolator").toAbsolutePath();

    private static final Path P3071 = Path.of("p3071", "percolator").toAbsolutePath();

    private static final ToolOffer LOCAL_309 = Percolators.local309(P309);

    private static final ToolOffer MANAGED_3071 = Percolators.managed3071(P3071);

    private static final String NAME_309 = "Percolator 3.09 (registered local binary)";

    private static final String WHY_309_SKIPPED =
            "Using Percolator 3.07.1 rather than 3.09 (registered local binary) because 3.09"
                    + " (registered local binary) lacks XML_OUTPUT, which Limelight conversion"
                    + " needs (the Limelight converter reads the Percolator XML that XML_OUTPUT"
                    + " writes).";

    private static final String NEWEST_309 =
            "Percolator 3.09 (registered local binary) is the newest Percolator that can be used on"
                    + " this computer.";

    private static final String ADVISORIES_3071 =
            "Advisories for Percolator 3.07.1, shown when it is selected:\n"
                    + "- Percolator 3.07.1 predates 3.08's change of the default PEP regressor to"
                    + " I-splines, so its posterior error probabilities are computed the older"
                    + " way.\n"
                    + "- Percolator 3.07.1 predates the fix for PEP values exceeding 1.0 (upstream"
                    + " issue #394, fixed in 3.08.1 and 3.09), so a PEP above 1.0 can appear in"
                    + " its output.";

    private static final String UNAVAILABLE_309_ONLY =
            "Limelight conversion is unavailable. Limelight conversion is unavailable: no"
                    + " Percolator that can be used on this computer has been observed to have"
                    + " XML_OUTPUT, and the Limelight converter reads the Percolator XML that"
                    + " XML_OUTPUT writes.\n"
                    + "What you can do:\n"
                    + "- Register a local Percolator binary that can write Percolator XML, from"
                    + " the Tool Manager. CometGUI probes it, and offers Limelight conversion if"
                    + " the probe observes XML_OUTPUT.\n"
                    + "- Run the Limelight conversion on a computer whose platform has an"
                    + " XML-capable Percolator, rerunning Percolator there from this run's merged"
                    + " PIN.";

    private final ScriptedEngine.Queue background = new ScriptedEngine.Queue("background");

    private final ScriptedEngine.Queue ui = new ScriptedEngine.Queue("ui");

    private final ScriptedChooser chooser = new ScriptedChooser();

    private final List<String> refreshedElsewhere = new ArrayList<>();

    private Percolators.Port port = new Percolators.Port(MANAGED_3071, LOCAL_309);

    private PercolatorViewModel section = build();

    private PercolatorViewModel build() {
        return new PercolatorViewModel(
                port, chooser, background, ui, () -> refreshedElsewhere.add("tool manager"));
    }

    private void settle() {
        while (background.pending() + ui.pending() > 0) {
            background.drain();
            ui.drain();
        }
    }

    private void readWith(ToolOffer... offers) {
        port = new Percolators.Port(offers);
        section = build();
        section.refresh();
        settle();
    }

    private static List<String> labels(List<VersionChoice> choices) {
        return choices.stream().map(VersionChoice::label).toList();
    }

    @Nested
    @DisplayName("reading the builds")
    class Reading {

        @Test
        @DisplayName("nothing is read on construction, and Run is told why")
        void nothingOnConstruction() {
            assertAll(
                    () -> assertEquals(0, port.reads()),
                    () -> assertEquals(PercolatorViewModel.NOT_READ, selection()),
                    () -> assertFalse(section.limelightEnabled()),
                    () -> assertFalse(section.limelightEnabledProperty().get()),
                    () -> assertEquals(section.request(), section.requestProperty().get()),
                    () -> assertEquals(PercolatorViewModel.NOT_READ, offersText()),
                    () ->
                            assertEquals(
                                    List.of(
                                            "The Percolator builds on this computer have not been"
                                                    + " read yet."),
                                    section.request().problems()),
                    () -> assertFalse(section.request().runnable()),
                    () -> assertTrue(section.request().pending(), "Run waits for it"),
                    () -> assertFalse(section.registerEnabledProperty().get()),
                    () -> assertEquals(List.of(), section.choicesProperty().get()),
                    () ->
                            assertEquals(
                                    "Whether Limelight conversion can run is known once the"
                                            + " Percolator builds have been read.",
                                    section.limelightStatusProperty().get()),
                    () -> assertEquals("", section.reasonProperty().get()),
                    () -> assertEquals("", section.skippedProperty().get()));
        }

        @Test
        @DisplayName(
                "the port is read on the background executor and applied on the interface one;"
                        + " an older answer is dropped")
        void backgroundThenInterface() {
            section.refresh();
            assertEquals(0, port.reads(), "not on the caller's thread");
            assertEquals(PercolatorViewModel.READING, offersText());
            Percolators.Port first = port;
            background.drain();
            assertEquals(1, first.reads());
            assertEquals(PercolatorViewModel.READING, offersText(), "not applied yet");
            port.answer(PercolatorOffers.of(List.of(LOCAL_309)));
            section.refresh();
            background.drain();
            ui.drain();
            assertFalse(section.request().pending(), "read: no longer pending");
            assertAll(
                    "the second answer, one build, is shown; the first was dropped",
                    () ->
                            assertEquals(
                                    "1 Percolator build is known on this computer, and 1 of"
                                            + " them is installed and can run.",
                                    offersText()),
                    () ->
                            assertEquals(
                                    List.of(NAME_309 + " -- the resolved default"),
                                    labels(section.choicesProperty().get())));
        }

        @Test
        @DisplayName("no Tool Manager: said in words, nothing to register, Run told why")
        void noToolManager() {
            port.answer(PercolatorOffers.unavailable("the artefact manifest cannot be read"));
            section.refresh();
            settle();
            String said =
                    "No Percolator can be selected, because this computer has no Tool Manager:"
                            + " the artefact manifest cannot be read";
            assertAll(
                    () -> assertEquals(said, offersText()),
                    () -> assertEquals(said, section.selectionProperty().get()),
                    () -> assertEquals(List.of(said), section.request().problems()),
                    () -> assertFalse(section.registerEnabledProperty().get()),
                    () -> assertFalse(section.register(), "nothing to register with"));
        }

        @Test
        @DisplayName("a port that throws is stated, never swallowed")
        void portThrows() {
            PercolatorPort broken =
                    new PercolatorPort() {
                        @Override
                        public PercolatorOffers offers() {
                            throw new IllegalStateException("boom");
                        }

                        @Override
                        public ToolOffer register(Path executable) {
                            throw new IllegalStateException("not used");
                        }
                    };
            PercolatorViewModel failing =
                    new PercolatorViewModel(broken, chooser, background, ui, () -> {});
            failing.refresh();
            settle();
            assertEquals(
                    "No Percolator can be selected, because this computer has no Tool Manager:"
                            + " the Percolator builds could not be read from the Tool Manager:"
                            + " java.lang.IllegalStateException: boom",
                    failing.offersStatusProperty().get());
        }

        @Test
        @DisplayName("no build at all: the resolution's own words, and Run is blocked by them")
        void noBuild() {
            readWith();
            String none =
                    "No Percolator can be used on this computer: none is installed, registered or"
                            + " installable here. Install a managed Percolator or register a local"
                            + " binary in the Tool Manager.";
            assertAll(
                    () ->
                            assertEquals(
                                    "0 Percolator builds are known on this computer, and 0 of"
                                            + " them are installed and can run.",
                                    offersText()),
                    () -> assertEquals("No Percolator can run. " + none, selection()),
                    () -> assertEquals(List.of(none), section.request().problems()),
                    () -> assertEquals("", section.advisoriesProperty().get()),
                    () -> assertEquals(Optional.empty(), section.selectedChoiceProperty().get()),
                    () -> assertTrue(section.registerEnabledProperty().get()));
        }

        private String offersText() {
            return section.offersStatusProperty().get();
        }
    }

    @Nested
    @DisplayName("gate 3 and 4: the default, the switch, the notice and the reasons")
    class Resolution {

        @Test
        @DisplayName(
                "3.09 local and 3.07.1 managed: off -> 3.09; on -> 3.07.1 with a notice naming"
                        + " both and XML_OUTPUT; off -> 3.09 again, noticed")
        void toggling() {
            readWith(MANAGED_3071, LOCAL_309);
            assertAll(
                    "Limelight off",
                    () ->
                            assertEquals(
                                    List.of(
                                            "Percolator 3.07.1",
                                            NAME_309 + " -- the resolved default"),
                                    labels(section.choicesProperty().get())),
                    () ->
                            assertEquals(
                                    NAME_309 + " -- the resolved default",
                                    section.selectedChoiceProperty().get().orElseThrow().label()),
                    () ->
                            assertEquals(
                                    NAME_309 + " runs in the next search: the resolved default.",
                                    selection()),
                    () -> assertEquals(NEWEST_309, section.reasonProperty().get()),
                    () ->
                            assertEquals(
                                    "No newer Percolator was passed over.",
                                    section.skippedProperty().get()),
                    () -> assertEquals("", section.noticeProperty().get()),
                    () ->
                            assertEquals(
                                    NAME_309 + " has no version advisories.",
                                    section.advisoriesProperty().get()),
                    () ->
                            assertEquals(
                                    "Limelight conversion can run: a Percolator on this computer"
                                            + " was observed to write the Percolator XML it reads"
                                            + " (XML_OUTPUT).",
                                    section.limelightStatusProperty().get()),
                    () ->
                            assertEquals(
                                    NAME_309
                                            + ": a binary registered from this computer, installed"
                                            + " at "
                                            + P309
                                            + ". Capabilities observed by running it:"
                                            + " PSM_TSV_OUTPUT, PEPTIDE_TSV_OUTPUT, DECOY_OUTPUT,"
                                            + " WEIGHTS_OUTPUT, THREAD_OPTION, SEED_OPTION,"
                                            + " TEST_FDR_OPTION, TRAIN_FDR_OPTION,"
                                            + " MAX_ITERATIONS_OPTION.",
                                    section.badgeProperty().get()),
                    () -> assertEquals(LOCAL_309, section.request().build().orElseThrow()),
                    () -> assertTrue(section.request().runnable()),
                    () -> assertTrue(section.request().resolvedDefault()));

            section.setLimelightEnabled(true);
            assertAll(
                    "Limelight on",
                    () -> assertTrue(section.limelightEnabled()),
                    () ->
                            assertEquals(
                                    "The default Percolator changed from 3.09 (registered local"
                                            + " binary) to 3.07.1 because Limelight conversion was"
                                            + " switched on and needs XML_OUTPUT.",
                                    section.noticeProperty().get()),
                    () ->
                            assertEquals(
                                    "Percolator 3.07.1 runs in the next search: the resolved"
                                            + " default.",
                                    selection()),
                    () -> assertEquals(WHY_309_SKIPPED, section.reasonProperty().get()),
                    () ->
                            assertEquals(
                                    "Newer Percolator builds passed over:\n- " + WHY_309_SKIPPED,
                                    section.skippedProperty().get()),
                    () -> assertEquals(ADVISORIES_3071, section.advisoriesProperty().get()),
                    () ->
                            assertEquals(
                                    List.of("Percolator 3.07.1 -- the resolved default", NAME_309),
                                    labels(section.choicesProperty().get())),
                    () -> assertEquals(MANAGED_3071, section.request().build().orElseThrow()),
                    () ->
                            assertEquals(
                                    WHY_309_SKIPPED,
                                    section.request()
                                            .resolution()
                                            .orElseThrow()
                                            .skipped()
                                            .get(0)
                                            .reason(),
                                    "the words on screen are the words provenance records"));

            section.setLimelightEnabled(true);
            assertEquals(MANAGED_3071, section.request().build().orElseThrow(), "no change");

            section.setLimelightEnabled(false);
            assertAll(
                    "Limelight off again",
                    () ->
                            assertEquals(
                                    "The default Percolator changed from 3.07.1 to 3.09"
                                            + " (registered local binary) because Limelight"
                                            + " conversion was switched off, so XML_OUTPUT is no"
                                            + " longer needed.",
                                    section.noticeProperty().get()),
                    () -> assertEquals(LOCAL_309, section.request().build().orElseThrow()),
                    () -> assertEquals(NEWEST_309, section.reasonProperty().get()));
        }

        @Test
        @DisplayName(
                "switched before the builds are read: no notice, and the first read resolves for"
                        + " the switch")
        void switchedBeforeReading() {
            section.setLimelightEnabled(true);
            assertEquals("", section.noticeProperty().get());
            section.refresh();
            settle();
            assertEquals(MANAGED_3071, section.request().build().orElseThrow());
            assertEquals("", section.noticeProperty().get(), "a first read changes no default");
        }

        @Test
        @DisplayName(
                "no observed XML-capable build: the non-XML default, Limelight unavailable with"
                        + " both remedies, and the switch said to change nothing")
        void noXmlCapableBuild() {
            readWith(LOCAL_309);
            section.setLimelightEnabled(true);
            assertAll(
                    () -> assertEquals(LOCAL_309, section.request().build().orElseThrow()),
                    () -> assertTrue(section.request().runnable()),
                    () ->
                            assertEquals(
                                    UNAVAILABLE_309_ONLY, section.limelightStatusProperty().get()),
                    () ->
                            assertEquals(
                                    "The default Percolator is still 3.09 (registered local binary)"
                                            + " (Limelight conversion was switched on and needs"
                                            + " XML_OUTPUT, but no Percolator here has been"
                                            + " observed to have it, so Limelight conversion is"
                                            + " unavailable).",
                                    section.noticeProperty().get()),
                    () ->
                            assertEquals(
                                    NEWEST_309
                                            + " Limelight conversion is switched on but"
                                            + " unavailable, because no Percolator here has been"
                                            + " observed to have XML_OUTPUT.",
                                    section.reasonProperty().get()),
                    () -> assertTrue(section.registerEnabledProperty().get(), "the remedy"));
        }

        @Test
        @DisplayName(
                "a claim inferred from bytes is not counted: the installed non-XML build is the"
                        + " default, and the explanation says the claim is unobserved")
        void inferredClaim() {
            ToolOffer macos =
                    Percolators.notInstalled(
                            "3.07.1",
                            Set.of(ToolCapability.XML_OUTPUT),
                            CapabilityEvidence.INFERRED_FROM_ARTEFACT_BYTES);
            readWith(macos, LOCAL_309);
            section.setLimelightEnabled(true);
            assertAll(
                    () -> assertEquals(LOCAL_309, section.request().build().orElseThrow()),
                    () ->
                            assertEquals(
                                    "Limelight conversion is unavailable. Limelight conversion is"
                                            + " unavailable: no Percolator that can be used on this"
                                            + " computer has been observed to have XML_OUTPUT, and"
                                            + " the Limelight converter reads the Percolator XML"
                                            + " that XML_OUTPUT writes. Percolator 3.07.1 claims"
                                            + " XML_OUTPUT from inferred-from-artefact-bytes"
                                            + " evidence, which has not been observed by running"
                                            + " it; installing 3.07.1 will probe it.\n"
                                            + "What you can do:\n"
                                            + "- Register a local Percolator binary that can write"
                                            + " Percolator XML, from the Tool Manager. CometGUI"
                                            + " probes it, and offers Limelight conversion if the"
                                            + " probe observes XML_OUTPUT.\n"
                                            + "- Run the Limelight conversion on a computer whose"
                                            + " platform has an XML-capable Percolator, rerunning"
                                            + " Percolator there from this run's merged PIN.",
                                    section.limelightStatusProperty().get()),
                    () ->
                            assertEquals(
                                    "Not installed, so not offered for running -- install from the"
                                            + " Tool Manager section: Percolator 3.07.1.",
                                    section.installableProperty().get()),
                    () ->
                            assertEquals(
                                    List.of(NAME_309 + " -- the resolved default"),
                                    labels(section.choicesProperty().get()),
                                    "a build that is not installed is never offered for running"));
        }

        @Test
        @DisplayName(
                "the default is installable but not installed: said, badge says so, Run blocked"
                        + " naming what to do")
        void defaultNotInstalled() {
            ToolOffer linux =
                    Percolators.notInstalled(
                            "3.07.1",
                            EnumSet.of(ToolCapability.XML_OUTPUT, ToolCapability.XML_DECOY_OUTPUT),
                            CapabilityEvidence.OBSERVED_BY_EXECUTION);
            readWith(linux);
            assertAll(
                    () ->
                            assertEquals(
                                    "The resolved default, Percolator 3.07.1, is not installed, so"
                                            + " no Percolator can run yet: install it in the Tool"
                                            + " Manager section, register a local Percolator binary"
                                            + " below, or choose an installed build above.",
                                    selection()),
                    () ->
                            assertEquals(
                                    "Percolator 3.07.1: a managed build, not installed -- install"
                                            + " it from the Tool Manager section. Capabilities"
                                            + " observed by running it: XML_OUTPUT,"
                                            + " XML_DECOY_OUTPUT.",
                                    section.badgeProperty().get()),
                    () ->
                            assertEquals(
                                    List.of(
                                            "Percolator 3.07.1, the default Percolator for the"
                                                    + " enabled downstream stages, is not"
                                                    + " installed: install it in the Tool Manager"
                                                    + " section,"
                                                    + " register a local Percolator binary in the"
                                                    + " Percolator section, or choose an installed"
                                                    + " build there."),
                                    section.request().problems()),
                    () -> assertEquals(Optional.empty(), section.selectedChoiceProperty().get()),
                    () -> assertEquals(List.of(), section.choicesProperty().get()));
        }

        @Test
        @DisplayName("a managed build being installed is named as installable, not offered")
        void installing() {
            ToolOffer installing =
                    new ToolOffer(
                            ToolName.PERCOLATOR,
                            ToolVersion.parse("3.06.5"),
                            ToolOrigin.MANAGED,
                            ToolInstallState.INSTALLING,
                            List.of(),
                            List.of(),
                            Optional.empty(),
                            Optional.empty(),
                            OptionalLong.of(1000L));
            readWith(installing, LOCAL_309);
            assertEquals(
                    "Not installed, so not offered for running -- install from the Tool Manager"
                            + " section: Percolator 3.06.5.",
                    section.installableProperty().get());
            assertEquals(
                    List.of(NAME_309 + " -- the resolved default"),
                    labels(section.choicesProperty().get()));
            assertEquals(
                    NAME_309 + " -- the resolved default",
                    section.choicesProperty().get().get(0).toString());
        }

        @Test
        @DisplayName("a claim that is not observed is shown as not counted on the badge")
        void unobservedOnTheBadge() {
            ToolOffer macos =
                    Percolators.notInstalled(
                            "3.07.1",
                            Set.of(ToolCapability.XML_OUTPUT),
                            CapabilityEvidence.INFERRED_FROM_ARTEFACT_BYTES);
            readWith(macos);
            assertEquals(
                    "Percolator 3.07.1: a managed build, not installed -- install it from the Tool"
                            + " Manager section. Capabilities observed by running it: none."
                            + " Claimed but not observed, so not counted: XML_OUTPUT"
                            + " (inferred-from-artefact-bytes).",
                    section.badgeProperty().get());
        }
    }

    @Nested
    @DisplayName("the scientist's choice")
    class Choice {

        @Test
        @DisplayName("another installed build runs when chosen; the default again follows it")
        void chooseAndReturn() {
            readWith(MANAGED_3071, LOCAL_309);
            VersionChoice other = section.choicesProperty().get().get(0);
            section.choose(other);
            assertAll(
                    () ->
                            assertEquals(
                                    "Percolator 3.07.1 runs in the next search: your choice. The"
                                            + " resolved default is Percolator 3.09 (registered"
                                            + " local binary); choose it, or Use the default, to"
                                            + " follow the default again.",
                                    selection()),
                    () -> assertEquals(MANAGED_3071, section.request().build().orElseThrow()),
                    () -> assertFalse(section.request().resolvedDefault()),
                    () -> assertEquals(other, section.selectedChoiceProperty().get().orElseThrow()),
                    () -> assertEquals(ADVISORIES_3071, section.advisoriesProperty().get()));

            section.setLimelightEnabled(true);
            section.setLimelightEnabled(false);
            assertEquals(
                    MANAGED_3071,
                    section.request().build().orElseThrow(),
                    "a choice is kept when the default moves");

            section.useDefault();
            assertEquals(LOCAL_309, section.request().build().orElseThrow());
            assertTrue(section.request().resolvedDefault());

            section.choose(other);
            section.choose(section.choicesProperty().get().get(1));
            assertTrue(section.request().resolvedDefault(), "choosing the default follows it");
            section.setLimelightEnabled(true);
            assertEquals(MANAGED_3071, section.request().build().orElseThrow(), "and it moved");
        }

        @Test
        @DisplayName("a choice the selector does not offer is refused")
        void unknownChoice() {
            readWith(LOCAL_309);
            VersionChoice stranger = new VersionChoice("x", MANAGED_3071, "Percolator x", false);
            assertThrows(IllegalArgumentException.class, () -> section.choose(stranger));
        }

        @Test
        @DisplayName("a choice that disappears from the builds falls back to the default, noticed")
        void choiceDisappears() {
            readWith(MANAGED_3071, LOCAL_309);
            section.choose(section.choicesProperty().get().get(0));
            port.answer(PercolatorOffers.of(List.of(LOCAL_309)));
            section.refresh();
            settle();
            assertAll(
                    () -> assertEquals(LOCAL_309, section.request().build().orElseThrow()),
                    () -> assertTrue(section.request().resolvedDefault()),
                    () ->
                            assertEquals(
                                    "Your choice of Percolator is no longer installed on this"
                                            + " computer, so the resolved default is used.",
                                    section.noticeProperty().get()));
        }

        @Test
        @DisplayName("builds re-read with a different default: the change is noticed")
        void buildsChanged() {
            readWith(LOCAL_309);
            section.setLimelightEnabled(true);
            port.answer(PercolatorOffers.of(List.of(MANAGED_3071, LOCAL_309)));
            section.refresh();
            settle();
            assertEquals(
                    "The default Percolator changed from 3.09 (registered local binary) to 3.07.1"
                            + " because the Percolator builds available on this computer changed.",
                    section.noticeProperty().get());
        }
    }

    @Nested
    @DisplayName("Advanced settings")
    class Advanced {

        private SettingState state(PercolatorSetting setting) {
            return section.settingStatesProperty().get().get(setting.ordinal());
        }

        @Test
        @DisplayName(
                "only what the selected build supports is editable; the rest is said not to be,"
                        + " with the capability it lacks")
        void onlySupported() {
            Set<ToolCapability> noSeed = EnumSet.copyOf(Percolators.ALL);
            noSeed.remove(ToolCapability.SEED_OPTION);
            ToolOffer seedless =
                    Percolators.installed("3.10", ToolOrigin.LOCAL, P309, noSeed, List.of());
            readWith(seedless);
            assertAll(
                    () -> assertTrue(state(PercolatorSetting.TEST_FDR).editable()),
                    () -> assertTrue(state(PercolatorSetting.TRAIN_FDR).editable()),
                    () -> assertTrue(state(PercolatorSetting.MAXIMUM_ITERATIONS).editable()),
                    () -> assertTrue(state(PercolatorSetting.THREAD_COUNT).editable()),
                    () -> assertFalse(state(PercolatorSetting.RANDOM_SEED).editable()),
                    () ->
                            assertEquals(
                                    "The seed of Percolator's random number generator, which"
                                            + " decides how results are split for"
                                            + " cross-validation. It is always recorded, so a"
                                            + " rerun of this run can reproduce it.\n"
                                            + "Not supported by this build: Percolator 3.10"
                                            + " (registered local binary) was not observed to"
                                            + " accept SEED_OPTION, so this setting is not passed"
                                            + " to it.",
                                    state(PercolatorSetting.RANDOM_SEED).state()),
                    () ->
                            assertEquals(
                                    "A learning threshold inside Percolator: the false discovery"
                                            + " rate at which Percolator selects the best"
                                            + " cross-validation result and reports its final"
                                            + " results. Changing it changes what Percolator"
                                            + " computes, so it takes effect only when Percolator"
                                            + " runs. It is not the PSM q-value result filter,"
                                            + " which only changes which results are displayed and"
                                            + " exported and never reruns Percolator.",
                                    state(PercolatorSetting.TEST_FDR).state(),
                                    "testFDR says it is not the display filter (R-PERC-04)"),
                    () -> assertEquals("1", state(PercolatorSetting.RANDOM_SEED).text()),
                    () -> assertEquals("0.01", state(PercolatorSetting.TEST_FDR).text()),
                    () -> assertEquals("10", state(PercolatorSetting.MAXIMUM_ITERATIONS).text()),
                    () -> assertEquals("3", state(PercolatorSetting.THREAD_COUNT).text()));
        }

        @Test
        @DisplayName("with no installed build selected, every setting is editable and says why")
        void unknownSupport() {
            readWith();
            assertTrue(state(PercolatorSetting.RANDOM_SEED).editable());
            assertTrue(
                    state(PercolatorSetting.RANDOM_SEED)
                            .state()
                            .endsWith(
                                    "\nWhich settings the build accepts is known once an"
                                            + " installed build is selected."));
        }

        @Test
        @DisplayName("valid values reach the request through the settings model, canonical")
        void validEdits() {
            readWith(LOCAL_309);
            section.editSetting(PercolatorSetting.TEST_FDR, " 0.050 ");
            section.editSetting(PercolatorSetting.TRAIN_FDR, "0.02");
            section.editSetting(PercolatorSetting.RANDOM_SEED, "42");
            section.editSetting(PercolatorSetting.MAXIMUM_ITERATIONS, "20");
            section.editSetting(PercolatorSetting.THREAD_COUNT, " 8 ");
            assertAll(
                    () ->
                            assertEquals(
                                    new PercolatorSettings(
                                            new TestFdr(new BigDecimal("0.05")),
                                            new TrainFdr(new BigDecimal("0.02")),
                                            42,
                                            20,
                                            8),
                                    section.request().settings().orElseThrow()),
                    () -> assertEquals("0.05", state(PercolatorSetting.TEST_FDR).text()),
                    () -> assertEquals("8", state(PercolatorSetting.THREAD_COUNT).text()),
                    () -> assertTrue(state(PercolatorSetting.TEST_FDR).valid()),
                    () -> assertTrue(section.request().runnable()));
        }

        @Test
        @DisplayName(
                "refused text stays in the field, the model's refusal is stated, Run is blocked;"
                        + " correcting it lifts the block")
        void refusedEdits() {
            readWith(LOCAL_309);
            section.editSetting(PercolatorSetting.TEST_FDR, "abc");
            section.editSetting(PercolatorSetting.RANDOM_SEED, "0");
            section.editSetting(PercolatorSetting.THREAD_COUNT, "x");
            String testFdr =
                    "testFDR must be a number greater than 0 and at most 1, written with a full"
                            + " stop as the decimal separator (for example 0.01), but was \"abc\"";
            String seed = "random seed must be a whole number from 1 to 20000, but was 0";
            String threads = "Thread count must be a whole number, but was \"x\"";
            assertAll(
                    () -> assertEquals("abc", state(PercolatorSetting.TEST_FDR).text()),
                    () -> assertFalse(state(PercolatorSetting.TEST_FDR).valid()),
                    () ->
                            assertTrue(
                                    state(PercolatorSetting.TEST_FDR)
                                            .state()
                                            .endsWith("\nNot valid: " + testFdr)),
                    () ->
                            assertEquals(
                                    List.of(
                                            "The Percolator setting testFDR is not valid: "
                                                    + testFdr,
                                            "The Percolator setting Random seed is not valid: "
                                                    + seed,
                                            "The Percolator setting Thread count is not valid: "
                                                    + threads),
                                    section.request().problems()),
                    () -> assertEquals(Optional.empty(), section.request().settings()));
            section.editSetting(PercolatorSetting.TEST_FDR, "0.01");
            section.editSetting(PercolatorSetting.RANDOM_SEED, "7");
            section.editSetting(PercolatorSetting.THREAD_COUNT, "2");
            assertTrue(section.request().runnable(), () -> section.request().problems().toString());
            assertEquals(7, section.request().settings().orElseThrow().randomSeed());
        }

        @Test
        @DisplayName("the maximum iterations go through the model's range")
        void maximumIterations() {
            readWith(LOCAL_309);
            section.editSetting(PercolatorSetting.MAXIMUM_ITERATIONS, "1001");
            assertEquals(
                    List.of(
                            "The Percolator setting Maximum iterations is not valid: maximum"
                                    + " iterations must be a whole number from 1 to 1000, but was"
                                    + " 1001"),
                    section.request().problems());
        }
    }

    @Nested
    @DisplayName("the display filters")
    class Filters {

        @Test
        @DisplayName(
                "0.01 and 0.01, set independently, refused text stated, and never a reason"
                        + " against Run")
        void filters() {
            readWith(LOCAL_309);
            assertAll(
                    () -> assertEquals("0.01", section.psmFilterTextProperty().get()),
                    () -> assertEquals("0.01", section.peptideFilterTextProperty().get()),
                    () ->
                            assertEquals(
                                    "The PSM and the peptide q-value filters (each 0.01 by"
                                            + " default, from 0 to 1, a q-value equal to the"
                                            + " cutoff passing) change only which PSMs and peptides"
                                            + " are displayed and exported. Changing them never"
                                            + " reruns Percolator or any other tool, and they are"
                                            + " not Percolator's testFDR or trainFDR, the learning"
                                            + " thresholds under Advanced settings.",
                                    section.filtersStatusProperty().get()));
            section.editPsmFilter(" 0.05 ");
            assertEquals(
                    new PsmQValueFilter(new BigDecimal("0.05")),
                    section.displayFiltersProperty().get().psm());
            assertEquals("0.05", section.psmFilterTextProperty().get());
            assertEquals(
                    PeptideQValueFilter.DEFAULT,
                    section.displayFiltersProperty().get().peptide(),
                    "the peptide filter is independent");
            section.editPeptideFilter("2");
            section.editPsmFilter("x");
            String refusals =
                    "\nThe PSM filter's text is not used: The PSM q-value filter must be a"
                            + " number between 0 and 1 inclusive, written with a '.' decimal"
                            + " point, but was 'x'\nThe peptide filter's text is not used: The"
                            + " peptide q-value filter must be between 0 and 1 inclusive, but was"
                            + " 2";
            assertAll(
                    () -> assertEquals("2", section.peptideFilterTextProperty().get()),
                    () ->
                            assertEquals(
                                    PeptideQValueFilter.DEFAULT,
                                    section.displayFiltersProperty().get().peptide(),
                                    "a refused value changes nothing"),
                    () ->
                            assertTrue(
                                    section.filtersStatusProperty().get().endsWith(refusals),
                                    section.filtersStatusProperty()::get),
                    () -> assertTrue(section.request().runnable(), "filters never block Run"));
            section.editPeptideFilter("1");
            assertEquals(
                    new PeptideQValueFilter(BigDecimal.ONE),
                    section.displayFiltersProperty().get().peptide());
            assertEquals("1", section.peptideFilterTextProperty().get());
            assertFalse(
                    section.filtersStatusProperty().get().contains("peptide filter's text"),
                    "an accepted value lifts the refusal");
        }
    }

    @Nested
    @DisplayName("local-binary registration")
    class Registration {

        @Test
        @DisplayName("not before the builds are read")
        void notBeforeReading() {
            assertFalse(section.register());
            assertEquals(List.of(), chooser.asked());
        }

        @Test
        @DisplayName("a cancelled chooser registers nothing, and says so")
        void cancelled() {
            readWith(LOCAL_309);
            assertFalse(section.register());
            assertEquals(List.of("file:a Percolator executable to register"), chooser.asked());
            assertEquals(
                    "No file was chosen, so nothing was registered.",
                    section.registrationStatusProperty().get());
            assertEquals(List.of(), port.registered());
        }

        @Test
        @DisplayName(
                "a chosen file is registered off the interface thread, the builds read again,"
                        + " the Tool Manager told, and the default re-resolved")
        void registered() {
            readWith();
            port.registers(MANAGED_3071);
            chooser.file(P3071);
            assertTrue(section.register());
            assertAll(
                    "under way",
                    () ->
                            assertEquals(
                                    "Registering "
                                            + P3071
                                            + ": CometGUI is running it to read its version and"
                                            + " probe what it can do.",
                                    section.registrationStatusProperty().get()),
                    () -> assertFalse(section.registerEnabledProperty().get()),
                    () -> assertFalse(section.register(), "one at a time"),
                    () -> assertEquals(List.of(), port.registered(), "not on the caller"));
            background.drain();
            assertEquals(List.of(P3071), port.registered());
            int reads = port.reads();
            settle();
            assertAll(
                    "registered",
                    () ->
                            assertEquals(
                                    "Registered Percolator 3.07.1 from "
                                            + P3071
                                            + ". Observed capabilities: XML_OUTPUT,"
                                            + " XML_DECOY_OUTPUT, PSM_TSV_OUTPUT,"
                                            + " PEPTIDE_TSV_OUTPUT, DECOY_OUTPUT, WEIGHTS_OUTPUT,"
                                            + " THREAD_OPTION, SEED_OPTION, TEST_FDR_OPTION,"
                                            + " TRAIN_FDR_OPTION, MAX_ITERATIONS_OPTION.",
                                    section.registrationStatusProperty().get()),
                    () -> assertEquals(List.of("tool manager"), refreshedElsewhere),
                    () -> assertEquals(reads + 1, port.reads(), "the builds read again"),
                    () -> assertEquals(MANAGED_3071, section.request().build().orElseThrow()),
                    () -> assertTrue(section.registerEnabledProperty().get()));
        }

        @Test
        @DisplayName("a refusal is stated in the Tool Manager's words; nothing is re-read")
        void refused() {
            readWith(LOCAL_309);
            port.refuses(
                    new ToolRegistrationException(
                            "The file at /tmp/x is Percolator 3.04, older than 3.05."));
            chooser.file(Path.of("x").toAbsolutePath());
            assertTrue(section.register());
            int reads = port.reads();
            settle();
            assertAll(
                    () ->
                            assertEquals(
                                    "Percolator was not registered: The file at /tmp/x is"
                                            + " Percolator 3.04, older than 3.05.",
                                    section.registrationStatusProperty().get()),
                    () -> assertEquals(reads, port.reads()),
                    () -> assertEquals(List.of(), refreshedElsewhere),
                    () -> assertTrue(section.registerEnabledProperty().get()));
        }

        @Test
        @DisplayName("an unexpected failure is stated, never swallowed")
        void failure() {
            readWith(LOCAL_309);
            port.fails(new IllegalStateException("no process service"));
            chooser.file(Path.of("y").toAbsolutePath());
            assertTrue(section.register());
            settle();
            assertEquals(
                    "Percolator could not be registered: java.lang.IllegalStateException: no"
                            + " process service",
                    section.registrationStatusProperty().get());
        }
    }

    @Test
    @DisplayName("a request with no problem must be whole")
    void requestInvariants() {
        PercolatorRequest ready = Percolators.ready();
        assertAll(
                () -> assertTrue(ready.runnable()),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new PercolatorRequest(
                                                Optional.of(MANAGED_3071),
                                                true,
                                                Optional.empty(),
                                                ready.settings(),
                                                List.of(),
                                                false)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new PercolatorRequest(
                                                Optional.empty(),
                                                false,
                                                Optional.empty(),
                                                Optional.empty(),
                                                List.of(),
                                                true)),
                () -> assertTrue(PercolatorRequest.pending("reading").pending()),
                () -> assertFalse(PercolatorRequest.blocked(List.of("no")).pending()),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> PercolatorRequest.blocked(List.of())),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> PercolatorRequest.blocked(List.of(" "))));
    }

    private String selection() {
        return section.selectionProperty().get();
    }
}
