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

package org.cometgui.ui.viewmodel.params;

import static org.cometgui.ui.viewmodel.params.Sessions.C02;
import static org.cometgui.ui.viewmodel.params.Sessions.C03;
import static org.cometgui.ui.viewmodel.params.Sessions.METADATA;
import static org.cometgui.ui.viewmodel.params.Sessions.startingIn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.migration.MigrationEntry;
import org.cometgui.params.comet.migration.MigrationReview;
import org.cometgui.params.comet.migration.SchemaMigration;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.parser.ReleaseDefaults;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.cometgui.params.comet.validation.Finding;
import org.cometgui.params.comet.validation.Rule;
import org.cometgui.params.comet.validation.Severity;
import org.cometgui.params.comet.value.ValueSyntaxException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The editor session: one configuration, its release, its review and its fields. */
class ParameterSessionTest {

    private static final String PIN_REASON =
            "Required by CometGUI workflow: Percolator rescoring reads the .pin file";

    private static final String PEPXML_REASON =
            "Required by CometGUI workflow: PDV, which shows the spectra, and the Limelight"
                    + " export both read the pepXML file";

    @Nested
    @DisplayName("a new session")
    class NewSession {

        @Test
        @DisplayName("starts with a new configuration of the first offered release, outputs on")
        void startsInTheFirstOfferedRelease() {
            ParameterSession session = startingIn(C03);
            assertEquals(List.of(C03, C02), session.offeredReleases());
            assertEquals(C03, session.release());
            assertEquals(C03, session.model().version());
            assertEquals("-1", session.model().text("index_search_type"));
            assertEquals("1", session.model().text("output_percolatorfile"));
            assertEquals(
                    ValueOrigin.WORKFLOW_ENFORCED, session.model().origin("output_percolatorfile"));
            assertEquals(
                    ValueOrigin.WORKFLOW_ENFORCED, session.model().origin("output_pepxmlfile"));
            assertEquals(ValueOrigin.COMET_DEFAULT, session.model().origin("database_name"));
            assertEquals(12, session.model().enzymeTable().rows().size());
            assertEquals(List.of(), session.report().findings());
            assertEquals(Optional.empty(), session.review());
            assertEquals(List.of(), session.unresolved());
            assertEquals(118, session.fields().size());
            assertEquals(session.fields(), session.fieldsProperty().get());
            assertEquals(C03, session.releaseProperty().get());
            assertEquals(Optional.empty(), session.reviewProperty().get());
            assertSame(session.model(), session.modelProperty().get());
            assertSame(session.report(), session.reportProperty().get());
            assertEquals("database_name", session.fields().get(0).name());
            assertSame(METADATA, session.metadata());
        }

        @Test
        @DisplayName("the offered order is the caller's: 2026.02.2 first starts in 2026.02.2")
        void theOfferedOrderIsTheCallers() {
            ParameterSession session = startingIn(C02);
            assertEquals(List.of(C02, C03), session.offeredReleases());
            assertEquals(C02, session.release());
            assertEquals("1", session.model().text("index_search_type"));
            assertEquals(List.of(), session.report().findings());
        }

        @Test
        @DisplayName("refuses no release, a release twice, and one with no metadata or no set")
        void refusals() {
            assertEquals(
                    "the parameter editor needs at least one Comet release to offer",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            new ParameterSession(
                                                    METADATA, List.of(), StageSwitches.ALL_ENABLED))
                            .getMessage());
            assertEquals(
                    "Comet 2026.03.0 is offered twice",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            new ParameterSession(
                                                    METADATA,
                                                    List.of(C03, C02, C03),
                                                    StageSwitches.ALL_ENABLED))
                            .getMessage());
            assertEquals(
                    "Comet 2024.01.0 cannot be offered: it has no bundled starting set",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            new ParameterSession(
                                                    METADATA,
                                                    List.of(C03, ToolVersion.parse("2024.01.0")),
                                                    StageSwitches.ALL_ENABLED))
                            .getMessage());
            assertEquals(
                    "Comet 2025.01.0 cannot be offered: the parameter metadata was not curated"
                            + " against it",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            new ParameterSession(
                                                    METADATA,
                                                    List.of(ToolVersion.parse("2025.01.0")),
                                                    StageSwitches.ALL_ENABLED))
                            .getMessage());
        }

        @Test
        @DisplayName("a parameter the release does not model has no field")
        void noSuchField() {
            ParameterSession session = startingIn(C03);
            assertEquals(
                    "Comet 2026.03.0 has no parameter named ms1_mass_range",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> session.field("ms1_mass_range"))
                            .getMessage());
            assertEquals(Optional.empty(), session.displayNameOf("ms1_mass_range"));
            assertEquals(
                    Optional.of("Precursor tolerance units"),
                    session.displayNameOf("peptide_mass_units"));
        }
    }

    @Nested
    @DisplayName("editing")
    class Editing {

        @Test
        @DisplayName("an accepted edit replaces the model through withText, origin USER")
        void acceptedEdit() {
            ParameterSession session = startingIn(C03);
            CometParameters before = session.model();
            List<CometParameters> seen = new ArrayList<>();
            session.modelProperty().addListener((o, was, now) -> seen.add(now));

            EditOutcome outcome = session.field("allowed_missed_cleavage").setText("1");

            assertEquals(EditOutcome.applied(), outcome);
            assertEquals("1", session.model().text("allowed_missed_cleavage"));
            assertEquals(ValueOrigin.USER, session.model().origin("allowed_missed_cleavage"));
            assertEquals("2", before.text("allowed_missed_cleavage"));
            assertEquals(1, seen.size());
            assertSame(session.model(), seen.get(0));
            FieldViewModel field = session.field("allowed_missed_cleavage");
            assertEquals("1", field.text());
            assertEquals(ValueOrigin.USER, field.origin());
            assertEquals("Set by you", field.originText());
        }

        @Test
        @DisplayName("text the model refuses leaves the model unchanged; the field shows why")
        void refusedEdit() {
            ParameterSession session = startingIn(C03);
            CometParameters before = session.model();
            String modelsOwnMessage =
                    assertThrows(
                                    ValueSyntaxException.class,
                                    () ->
                                            assertNotNull(
                                                    before.withText(
                                                            "allowed_missed_cleavage",
                                                            "many",
                                                            ValueOrigin.USER)))
                            .getMessage();

            EditOutcome outcome = session.edit("allowed_missed_cleavage", "many");

            assertEquals(EditOutcome.refused(modelsOwnMessage), outcome);
            assertSame(before, session.model());
            FieldViewModel field = session.field("allowed_missed_cleavage");
            assertEquals("many", field.text());
            assertEquals(Optional.of(modelsOwnMessage), field.refusal());
            assertEquals(FieldState.ERROR, field.state());
            assertEquals(
                    "Error: not applied, the configuration still holds \"2\". " + modelsOwnMessage,
                    field.stateText());
            assertEquals(ValueOrigin.COMET_DEFAULT, field.origin());
            // the refusal is the field's, not a finding: the report is unchanged
            assertEquals(List.of(), session.report().findings());

            // the value it already holds: no change to the model, but the refusal is gone
            assertEquals(EditOutcome.applied(), field.setText("2"));
            assertSame(before, session.model());
            assertEquals("2", field.text());
            assertEquals(Optional.empty(), field.refusal());
            assertEquals(FieldState.VALID, field.state());

            session.edit("allowed_missed_cleavage", "many");
            assertEquals(EditOutcome.applied(), field.setText("3"));
            assertEquals("3", field.text());
            assertEquals(Optional.empty(), field.refusal());
            assertEquals(FieldState.VALID, field.state());
            assertEquals("No problems.", field.stateText());
        }

        @Test
        @DisplayName("a pending refusal survives an edit of another field")
        void refusalSurvivesOtherEdits() {
            ParameterSession session = startingIn(C03);
            session.edit("allowed_missed_cleavage", "many");
            session.edit("num_threads", "4");
            assertEquals("many", session.field("allowed_missed_cleavage").text());
            assertEquals(FieldState.ERROR, session.field("allowed_missed_cleavage").state());
            assertEquals("4", session.model().text("num_threads"));
        }

        @Test
        @DisplayName("text that reads as the value already held changes nothing, origin included")
        void sameValueIsNoChange() {
            ParameterSession session = startingIn(C03);
            CometParameters before = session.model();
            List<CometParameters> seen = new ArrayList<>();
            session.modelProperty().addListener((o, was, now) -> seen.add(now));

            assertEquals(EditOutcome.applied(), session.edit("allowed_missed_cleavage", "2"));

            assertSame(before, session.model());
            assertEquals(
                    ValueOrigin.COMET_DEFAULT, session.model().origin("allowed_missed_cleavage"));
            assertEquals(List.of(), seen);
        }

        @Test
        @DisplayName("an enumerated value is chosen by its token")
        void chooseByToken() {
            ParameterSession session = startingIn(C03);
            FieldViewModel units = session.field("peptide_mass_units");
            assertEquals(EditOutcome.applied(), units.choose(units.choices().get(0)));
            assertEquals("0", session.model().text("peptide_mass_units"));
        }
    }

    @Nested
    @DisplayName("workflow-enforced outputs (gate item 5)")
    class Locks {

        @Test
        @DisplayName("both outputs are locked on with the stage in words")
        void lockedWithReason() {
            ParameterSession session = startingIn(C03);
            FieldViewModel pin = session.field("output_percolatorfile");
            FieldViewModel pepxml = session.field("output_pepxmlfile");
            assertTrue(pin.isLocked());
            assertTrue(pin.lockedProperty().get());
            assertEquals(Optional.of(PIN_REASON), pin.lockReason());
            assertEquals(Optional.of(PEPXML_REASON), pepxml.lockReason());
            assertEquals("Required by CometGUI workflow", pin.originText());
            assertFalse(session.field("output_txtfile").isLocked());
            assertEquals(Optional.empty(), session.field("output_txtfile").lockReason());
        }

        @Test
        @DisplayName("switching a required output off is refused with the reason; nothing changes")
        void switchingOffIsRefused() {
            ParameterSession session = startingIn(C03);
            CometParameters before = session.model();
            String expected =
                    "Write Percolator input (PIN) cannot be changed while the stage that needs it"
                            + " is enabled. "
                            + PIN_REASON
                            + ".";

            assertEquals(
                    EditOutcome.refused(expected),
                    session.field("output_percolatorfile").setText("0"));
            assertEquals(
                    EditOutcome.refused(expected), session.field("output_percolatorfile").reset());
            assertSame(before, session.model());
            assertEquals("1", session.field("output_percolatorfile").text());
            assertEquals(
                    ValueOrigin.WORKFLOW_ENFORCED, session.model().origin("output_percolatorfile"));
        }

        @Test
        @DisplayName(
                "with its stage disabled an output may be switched off; enabled again, it is on")
        void stageSwitches() {
            ParameterSession session = startingIn(C03);
            session.setStageSwitches(output -> !output.equals("output_percolatorfile"));
            FieldViewModel pin = session.field("output_percolatorfile");
            assertFalse(pin.isLocked());
            assertEquals(Optional.empty(), pin.lockReason());
            assertTrue(session.field("output_pepxmlfile").isLocked());

            assertEquals(EditOutcome.applied(), pin.setText("0"));
            assertEquals("0", session.model().text("output_percolatorfile"));
            assertEquals(ValueOrigin.USER, session.model().origin("output_percolatorfile"));

            pin.setText("off");
            assertTrue(pin.refusal().isPresent());

            session.setStageSwitches(StageSwitches.ALL_ENABLED);
            assertTrue(pin.isLocked());
            assertEquals(Optional.empty(), pin.refusal());
            assertEquals("1", pin.text());
            assertEquals("1", session.model().text("output_percolatorfile"));
            assertEquals(
                    ValueOrigin.WORKFLOW_ENFORCED, session.model().origin("output_percolatorfile"));
        }

        @Test
        @DisplayName("an output whose stage stays disabled keeps the value it has")
        void unlockedOutputKeepsItsValue() {
            ParameterSession session = startingIn(C03);
            session.setStageSwitches(output -> false);
            session.field("output_pepxmlfile").setText("0");
            session.setStageSwitches(output -> !output.equals("output_pepxmlfile"));
            assertEquals("0", session.model().text("output_pepxmlfile"));
            assertEquals(ValueOrigin.USER, session.model().origin("output_pepxmlfile"));
            assertEquals("1", session.model().text("output_percolatorfile"));
        }
    }

    @Nested
    @DisplayName("adopting a whole set (P7-7)")
    class Adopting {

        @Test
        @DisplayName("an imported set has its outputs enforced and keeps its own origins")
        void imported() {
            ParameterSession session = startingIn(C03);
            CometParameters file =
                    ReleaseDefaults.load(METADATA, C03)
                            .withText("num_threads", "8", ValueOrigin.IMPORTED);
            assertEquals("0", file.text("output_percolatorfile"));

            session.adopt(file, Adoption.IMPORTED);

            assertEquals("1", session.model().text("output_percolatorfile"));
            assertEquals(
                    ValueOrigin.WORKFLOW_ENFORCED, session.model().origin("output_percolatorfile"));
            assertEquals("8", session.field("num_threads").text());
            assertEquals(
                    "Imported from a parameter file", session.field("num_threads").originText());
            assertEquals(List.of(), session.report().findings());
        }

        @Test
        @DisplayName("a preset or a raw apply must be of the selected release")
        void editsKeepTheRelease() {
            ParameterSession session = startingIn(C03);
            CometParameters other = ReleaseDefaults.load(METADATA, C02);
            for (Adoption edit : List.of(Adoption.RAW_APPLIED, Adoption.PRESET_APPLIED)) {
                assertEquals(
                        "a set of Comet 2026.02.2 cannot be applied to a configuration of Comet"
                                + " 2026.03.0",
                        assertThrows(
                                        IllegalArgumentException.class,
                                        () -> session.adopt(other, edit))
                                .getMessage());
            }
            CometParameters preset =
                    session.model()
                            .withText("fragment_bin_tol", "0.02", ValueOrigin.PRESET)
                            .withText("output_percolatorfile", "0", ValueOrigin.PRESET);
            session.edit("num_threads", "lots");
            assertTrue(session.field("num_threads").refusal().isPresent());
            session.adopt(preset, Adoption.PRESET_APPLIED);
            assertEquals(Optional.empty(), session.field("num_threads").refusal());
            assertEquals("0", session.field("num_threads").text());
            assertEquals("0.02", session.model().text("fragment_bin_tol"));
            assertEquals("Set by a preset", session.field("fragment_bin_tol").originText());
            assertEquals("1", session.model().text("output_percolatorfile"));
        }

        @Test
        @DisplayName("a new configuration may be of another offered release, never of another")
        void newConfiguration() {
            ParameterSession session = startingIn(C03);
            List<FieldViewModel> before = session.fields();
            session.newConfiguration(C02);
            assertEquals(C02, session.release());
            assertEquals("1", session.model().text("index_search_type"));
            assertNotSame(before, session.fields());
            assertEquals(C02, session.field("index_search_type").release());

            ParameterSession only =
                    new ParameterSession(METADATA, List.of(C03), StageSwitches.ALL_ENABLED);
            String expected =
                    "Comet 2026.02.2 is not offered; the offered releases are [2026.03.0]";
            assertEquals(
                    expected,
                    assertThrows(IllegalArgumentException.class, () -> only.newConfiguration(C02))
                            .getMessage());
            assertEquals(
                    expected,
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            only.adopt(
                                                    ReleaseDefaults.load(METADATA, C02),
                                                    Adoption.IMPORTED))
                            .getMessage());
            assertEquals(
                    expected,
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            only.adoptMigration(
                                                    SchemaMigration.migrate(only.model(), C02)))
                            .getMessage());
            assertEquals(
                    expected,
                    assertThrows(IllegalArgumentException.class, () -> only.selectRelease(C02))
                            .getMessage());
            // an unbundled release is refused as not offered, before anything is loaded
            assertEquals(
                    "Comet 2024.01.0 is not offered; the offered releases are [2026.03.0]",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> only.newConfiguration(ToolVersion.parse("2024.01.0")))
                            .getMessage());
        }
    }

    @Nested
    @DisplayName("switching release (P7-2)")
    class Switching {

        @Test
        @DisplayName("migrates the configuration and keeps the migration for review")
        void migrates() {
            ParameterSession session = startingIn(C03);
            session.edit("allowed_missed_cleavage", "1");

            assertEquals(EditOutcome.applied(), session.selectRelease(C02));

            assertEquals(C02, session.release());
            assertEquals(C02, session.model().version());
            MigrationReview review = session.review().orElseThrow();
            assertEquals(C03, review.report().from());
            assertEquals(C02, review.report().to());
            assertEquals(
                    List.of("index_search_type"),
                    review.report().names(MigrationEntry.Outcome.CONVERTED));
            assertEquals("1", session.model().text("index_search_type"));
            assertEquals("1", session.model().text("allowed_missed_cleavage"));
            assertEquals(ValueOrigin.USER, session.model().origin("allowed_missed_cleavage"));
            assertEquals(
                    ValueOrigin.WORKFLOW_ENFORCED, session.model().origin("output_percolatorfile"));
            assertEquals(List.of(), session.unresolved());
            assertEquals(List.of(), session.report().findings());
            assertEquals(C02, session.field("index_search_type").release());
            assertEquals("1", session.field("index_search_type").defaultText());
        }

        @Test
        @DisplayName("selecting the selected release changes nothing")
        void sameRelease() {
            ParameterSession session = startingIn(C03);
            CometParameters before = session.model();
            assertEquals(EditOutcome.applied(), session.selectRelease(C03));
            assertSame(before, session.model());
            assertEquals(Optional.empty(), session.review());
        }

        @Test
        @DisplayName("an entry needing attention is an error at its field until resolved")
        void needsAttentionBlocksUntilResolved() {
            ParameterSession session = startingIn(C02);
            // CONSTRUCTED edit: a terminus outside 0-3 at a distance, which 2026.03.0 cannot hold
            assertEquals(
                    EditOutcome.applied(),
                    session.edit("variable_mod01", "15.9949 M 0 3 2 4 0 0.0"));

            assertEquals(EditOutcome.applied(), session.selectRelease(C03));

            assertEquals(
                    List.of("variable_mod01"),
                    session.unresolved().stream().map(MigrationEntry::parameter).toList());
            assertEquals("15.9949 M 0 3 -1 0 0 0.0", session.model().text("variable_mod01"));
            assertTrue(session.report().hasErrors());
            Finding finding = session.report().errors().get(0);
            assertEquals(Rule.MIGRATION_NEEDS_ATTENTION, finding.rule());
            assertEquals(Severity.ERROR, finding.severity());
            assertEquals(List.of("variable_mod01"), finding.parameters());
            assertEquals(Optional.of(ParameterCategory.VARIABLE_MODS), finding.category());
            FieldViewModel slot = session.field("variable_mod01");
            assertEquals(FieldState.ERROR, slot.state());
            assertTrue(
                    slot.stateText().startsWith("Error: variable_mod01 needs your decision:"),
                    slot.stateText());

            // another release cannot be selected while the review has an open entry
            assertEquals(
                    EditOutcome.refused(
                            "Resolve the migration from Comet 2026.02.2 to Comet 2026.03.0 before"
                                    + " selecting another release; 1 still need your decision:"
                                    + " [variable_mod01]"),
                    session.selectRelease(C02));
            assertEquals(C03, session.release());
            // a release that is not offered is refused as such, open entries or not
            assertEquals(
                    "Comet 2024.01.0 is not offered; the offered releases are [2026.02.2,"
                            + " 2026.03.0]",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> session.selectRelease(ToolVersion.parse("2024.01.0")))
                            .getMessage());

            // re-committing the value it holds is not the scientist's decision
            session.edit("variable_mod01", "15.9949 M 0 3 -1 0 0 0.0");
            assertEquals(1, session.unresolved().size());

            session.resolve("variable_mod01");
            assertEquals(List.of(), session.unresolved());
            assertFalse(session.report().hasErrors());
            assertEquals(FieldState.VALID, slot.state());
            assertEquals(EditOutcome.applied(), session.selectRelease(C02));
        }

        @Test
        @DisplayName("a value the scientist sets on the entry resolves it too")
        void userValueResolves() {
            ParameterSession session = startingIn(C02);
            session.edit("variable_mod01", "15.9949 M 0 3 2 4 0 0.0");
            session.selectRelease(C03);
            assertEquals(1, session.unresolved().size());

            session.edit("variable_mod01", "15.9949 M 0 2 -1 0 0 0.0");

            assertEquals(List.of(), session.unresolved());
            assertFalse(session.report().hasErrors());
        }

        @Test
        @DisplayName("a new configuration drops the review; nothing to resolve without one")
        void newConfigurationDropsTheReview() {
            ParameterSession session = startingIn(C02);
            session.edit("variable_mod01", "15.9949 M 0 3 2 4 0 0.0");
            session.selectRelease(C03);
            session.resetAll();
            assertEquals(Optional.empty(), session.review());
            assertFalse(session.report().hasErrors());
            assertEquals(
                    "no migration is under review, so there is nothing to resolve",
                    assertThrows(
                                    IllegalStateException.class,
                                    () -> session.resolve("variable_mod01"))
                            .getMessage());
        }

        @Test
        @DisplayName("a raw apply or a preset keeps the review; the entry stays unresolved")
        void editsKeepTheReview() {
            ParameterSession session = startingIn(C02);
            session.edit("variable_mod01", "15.9949 M 0 3 2 4 0 0.0");
            session.selectRelease(C03);
            session.adopt(
                    session.model().withText("fragment_bin_tol", "0.02", ValueOrigin.PRESET),
                    Adoption.PRESET_APPLIED);
            assertTrue(session.review().isPresent());
            assertEquals(1, session.unresolved().size());
        }
    }

    @Nested
    @DisplayName("the decoy source (R-DEC-01)")
    class Decoys {

        @Test
        @DisplayName("one control over three sources, in the release's words")
        void decoyOptions() {
            ParameterSession session = startingIn(C03);
            assertEquals(
                    List.of(
                            new DecoyOption(
                                    DecoySource.FASTA_CONTAINS_DECOYS, "0", "No internal decoys"),
                            new DecoyOption(
                                    DecoySource.COMET_INTERNAL_CONCATENATED,
                                    "1",
                                    "Concatenated: targets and decoys compete, one result per"
                                            + " spectrum"),
                            new DecoyOption(
                                    DecoySource.COMET_INTERNAL_SEPARATE,
                                    "2",
                                    "Separate: targets and decoys searched and reported"
                                            + " separately")),
                    session.decoyOptions());
            assertEquals(Optional.of(DecoySource.FASTA_CONTAINS_DECOYS), session.decoySource());
        }

        @Test
        @DisplayName("choosing a source writes decoy_search through the model, origin USER")
        void setDecoySource() {
            ParameterSession session = startingIn(C03);
            session.edit("decoy_search", "concatenated");
            assertTrue(session.field("decoy_search").refusal().isPresent());
            assertEquals(
                    EditOutcome.applied(),
                    session.setDecoySource(DecoySource.COMET_INTERNAL_CONCATENATED));
            assertEquals(Optional.empty(), session.field("decoy_search").refusal());
            assertEquals("1", session.model().text("decoy_search"));
            assertEquals(ValueOrigin.USER, session.model().origin("decoy_search"));
            assertEquals(
                    Optional.of(DecoySource.COMET_INTERNAL_CONCATENATED), session.decoySource());

            CometParameters before = session.model();
            session.setDecoySource(DecoySource.COMET_INTERNAL_CONCATENATED);
            assertSame(before, session.model());

            session.setDecoySource(DecoySource.COMET_INTERNAL_SEPARATE);
            assertEquals("2", session.field("decoy_search").text());
        }
    }

    /**
     * No listener of any of the session's properties can observe a model, a report, a release, a
     * review and fields that do not belong together -- whichever property it listens to, and
     * whichever change: an edit, a release switch whose report changes (a slot holding {@code ^},
     * which 2026.02.2 cannot write), resolving a migration entry, a switch back, starting again.
     *
     * <p>Before, the session published the report before the model, so a report listener read the
     * new release's review against the old release's model: {@code MigrationReview} refused, and
     * JavaFX handed the exception to the thread's handler, where no test saw it. This test records
     * both kinds of failure: an inconsistency a listener reads, and an exception a listener throws.
     */
    @Test
    @DisplayName("every listener of every property reads one consistent configuration")
    void listenersReadOneConsistentConfiguration() {
        ParameterSession session = startingIn(C03);
        List<String> seen = new ArrayList<>();
        Runnable check =
                () -> {
                    CometParameters model = session.model();
                    if (!model.version().equals(session.release())) {
                        seen.add(
                                "model of "
                                        + model.version().text()
                                        + " while the release is "
                                        + session.release().text());
                    }
                    session.review()
                            .filter(r -> !r.report().to().equals(model.version()))
                            .ifPresent(
                                    r ->
                                            seen.add(
                                                    "a review of a migration to "
                                                            + r.report().to().text()));
                    if (!session.field("variable_mod01").release().equals(model.version())) {
                        seen.add("fields of another release");
                    }
                    session.unresolved();
                };
        session.modelProperty()
                .addListener(
                        (o, before, after) -> {
                            if (after != session.model()) {
                                seen.add("the model property is not the model");
                            }
                            check.run();
                        });
        session.reportProperty()
                .addListener(
                        (o, before, after) -> {
                            if (after != session.report()) {
                                seen.add("the report property is not the report");
                            }
                            check.run();
                        });
        session.releaseProperty().addListener((o, before, after) -> check.run());
        session.reviewProperty().addListener((o, before, after) -> check.run());
        session.fieldsProperty().addListener((o, before, after) -> check.run());
        session.pendingRefusalsProperty().addListener((o, before, after) -> check.run());

        List<Throwable> thrown = new ArrayList<>();
        Thread.UncaughtExceptionHandler previous =
                Thread.currentThread().getUncaughtExceptionHandler();
        Thread.currentThread()
                .setUncaughtExceptionHandler((thread, failure) -> thrown.add(failure));
        try {
            session.edit("variable_mod03", "42.010565 ^ 0 1 -1 0 0 0.0");
            assertEquals(EditOutcome.applied(), session.selectRelease(C02));
            for (MigrationEntry open : session.unresolved()) {
                session.resolve(open.parameter());
            }
            session.edit("allowed_missed_cleavage", "many");
            assertEquals(EditOutcome.applied(), session.selectRelease(C03));
            session.resetAll();
        } finally {
            Thread.currentThread().setUncaughtExceptionHandler(previous);
        }
        assertEquals(List.of(), thrown, "no listener may fail");
        assertEquals(List.of(), seen, "no listener may read a half-published change");
        assertEquals(C03, session.release());
        assertEquals(C03, session.releaseProperty().get(), "the property holds the release too");
        assertEquals(Optional.empty(), session.reviewProperty().get());
        assertSame(session.model(), session.modelProperty().get());
        assertSame(session.report(), session.reportProperty().get());
        assertEquals(EditOutcome.applied(), session.selectRelease(C02));
        assertEquals(C02, session.releaseProperty().get());
        assertSame(session.fields(), session.fieldsProperty().get(), "the new release's fields");
        assertEquals(C02, session.fieldsProperty().get().get(0).release());
    }
}
