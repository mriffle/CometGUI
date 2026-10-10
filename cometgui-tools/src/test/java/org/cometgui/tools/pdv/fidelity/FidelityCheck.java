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

package org.cometgui.tools.pdv.fidelity;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * The fidelity suite's comparison of a generated mzTab, and the MGFs it references, against the
 * sources it was made from ({@code R-PDV-03}, Phase 11 exit gate items 6 and 7).
 *
 * <p>Everything is read here with the suite's own readers ({@link PercolatorTable}, {@link
 * PepXmlFile}, {@link MzMlFile}, {@link MzTabFile}, {@link MgfEntries}); nothing the exporter
 * reports about itself is consulted (design decision P11-9). The class depends on the JDK alone so
 * that the run-level test in {@code cometgui-workflow} can compile and run this very source.
 *
 * <p>Each check is a named {@link Finding}: how many values it compared and every failure, each
 * naming the PSM by its table and {@code PSMId}.
 */
public final class FidelityCheck {

    // The exporter's column names, typed again here rather than read from its constants: the
    // suite states independently what it expects the document to call them.

    static final String PROFORMA = "opt_global_cv_MS:1003169_proforma_peptidoform_sequence";
    static final String DECOY = "opt_global_cv_MS:1002217_decoy_peptide";
    static final String PSM_ID = "opt_global_percolator_PSMId";
    static final String SCORE = "opt_global_percolator_score";
    static final String Q_VALUE = "opt_global_percolator_q_value";
    static final String PEP = "opt_global_percolator_posterior_error_prob";

    /** The columns no source supplies, which must be {@code null} in every row. */
    static final List<String> NULL_COLUMNS =
            List.of("unique", "database", "database_version", "uri", "start", "end");

    /** The columns PDV, and this suite, need. */
    static final List<String> REQUIRED_COLUMNS =
            List.of(
                    "sequence",
                    "PSM_ID",
                    "accession",
                    "search_engine_score[1]",
                    "search_engine_score[2]",
                    "search_engine_score[3]",
                    "modifications",
                    "retention_time",
                    "charge",
                    "exp_mass_to_charge",
                    "spectra_ref",
                    "pre",
                    "post",
                    PROFORMA,
                    DECOY,
                    PSM_ID,
                    SCORE,
                    Q_VALUE,
                    PEP);

    /** The monoisotopic mass of hydrogen, the unmodified N terminus Comet adds to its delta. */
    static final BigDecimal HYDROGEN = new BigDecimal("1.007825035");

    /**
     * One source of a run: the spectrum file Comet searched and the pepXML it wrote.
     *
     * @param base the {@code -N} base every {@code PSMId} from it starts with
     * @param spectra the mzML
     * @param pepXml the pepXML
     * @param decoyPepXml the separate decoy pepXML ({@code decoy_search = 2}), or {@code null}
     */
    record Source(String base, Path spectra, Path pepXml, Path decoyPepXml) {}

    /**
     * One check's outcome.
     *
     * @param name what was checked
     * @param compared how many values it compared
     * @param failures every failure, each naming its PSM
     */
    record Finding(String name, long compared, List<String> failures) {

        /** A short report: the name, the count, and the first failures. */
        String report() {
            StringBuilder text =
                    new StringBuilder(name)
                            .append(": ")
                            .append(compared)
                            .append(" compared, ")
                            .append(failures.size())
                            .append(" failed");
            for (int index = 0; index < Math.min(20, failures.size()); index++) {
                text.append("\n  ").append(failures.get(index));
            }
            return text.toString();
        }
    }

    /** A Percolator row joined, by the suite, to its pepXML hit and producing spectrum. */
    record Joined(
            PercolatorTable.Row row,
            Source source,
            PepXmlFile pepXml,
            PepXmlFile.Query query,
            PepXmlFile.Hit hit,
            MzMlFile.Spectrum spectrum) {}

    private final List<PercolatorTable.Row> sourceRows = new ArrayList<>();
    private final Map<String, Joined> joined = new LinkedHashMap<>();
    private final List<String> joinFailures = new ArrayList<>();
    private final Map<Path, MzMlFile> spectra = new HashMap<>();
    private final MzTabFile mzTab;
    private final Path mzTabFile;
    private final Map<Path, List<MgfEntries.Entry>> mgfs = new HashMap<>();

    /**
     * Reads every source and the mzTab, and joins each Percolator row to its hit and spectrum.
     *
     * @param targets Percolator's target table
     * @param decoys its decoy table, or {@code null}
     * @param sources the run's sources
     * @param mzTabFile the generated mzTab
     * @throws IOException if a file cannot be read
     */
    FidelityCheck(Path targets, Path decoys, List<Source> sources, Path mzTabFile)
            throws IOException {
        this.mzTabFile = mzTabFile;
        sourceRows.addAll(PercolatorTable.read(targets, false));
        if (decoys != null) {
            sourceRows.addAll(PercolatorTable.read(decoys, true));
        }
        Map<String, Source> byBase = new HashMap<>();
        Map<Path, PepXmlFile> pepXmls = new HashMap<>();
        for (Source source : sources) {
            byBase.put(source.base(), source);
            pepXmls.put(source.pepXml(), PepXmlFile.read(source.pepXml()));
            if (source.decoyPepXml() != null) {
                pepXmls.put(source.decoyPepXml(), PepXmlFile.read(source.decoyPepXml()));
            }
            spectra.put(source.spectra(), MzMlFile.read(source.spectra()));
        }
        for (PercolatorTable.Row row : sourceRows) {
            join(row, byBase, pepXmls);
        }
        this.mzTab = MzTabFile.read(mzTabFile);
    }

    private void join(
            PercolatorTable.Row row, Map<String, Source> byBase, Map<Path, PepXmlFile> pepXmls) {
        String name = name(row.decoy(), row.psmId());
        String[] parts = row.psmId().split("_");
        if (parts.length < 4) {
            joinFailures.add(name + ": PSMId is not <base>_<scan>_<charge>_<rank>");
            return;
        }
        String rank = parts[parts.length - 1];
        String charge = parts[parts.length - 2];
        String scan = parts[parts.length - 3];
        String base =
                row.psmId()
                        .substring(
                                0,
                                row.psmId().length()
                                        - rank.length()
                                        - charge.length()
                                        - scan.length()
                                        - 3);
        Source source = byBase.get(base);
        if (source == null) {
            joinFailures.add(name + ": no source has the base " + base);
            return;
        }
        Path pepXmlPath =
                row.decoy() && source.decoyPepXml() != null
                        ? source.decoyPepXml()
                        : source.pepXml();
        PepXmlFile pepXml = pepXmls.get(pepXmlPath);
        List<PepXmlFile.Query> queries = pepXml.queries(scan, charge);
        if (queries.size() != 1) {
            joinFailures.add(
                    name
                            + ": "
                            + queries.size()
                            + " queries of "
                            + pepXmlPath
                            + " have start_scan "
                            + scan
                            + " and assumed_charge "
                            + charge);
            return;
        }
        PepXmlFile.Query query = queries.get(0);
        int position = Integer.parseInt(rank);
        if (position < 1 || position > query.hits().size()) {
            joinFailures.add(name + ": the query has no hit at position " + position);
            return;
        }
        PepXmlFile.Hit hit = query.hits().get(position - 1);
        String percolatorSequence = Peptidoforms.percolator(row.peptide()).sequence();
        if (!percolatorSequence.equals(hit.peptide())) {
            joinFailures.add(
                    name
                            + ": Percolator's peptide "
                            + row.peptide()
                            + " is not hit "
                            + position
                            + "'s "
                            + hit.peptide());
            return;
        }
        if (query.nativeId() == null) {
            joinFailures.add(name + ": the query has no spectrumNativeID");
            return;
        }
        List<MzMlFile.Spectrum> found = spectra.get(source.spectra()).withId(query.nativeId());
        if (found.size() != 1) {
            joinFailures.add(
                    name
                            + ": "
                            + found.size()
                            + " spectra of "
                            + source.spectra()
                            + " have id "
                            + query.nativeId());
            return;
        }
        if (joined.put(row.key(), new Joined(row, source, pepXml, query, hit, found.get(0)))
                != null) {
            joinFailures.add(name + ": the source tables hold this PSM twice");
        }
    }

    /**
     * The name a failure gives a PSM.
     *
     * @param decoy whether it is a decoy-table row
     * @param psmId its {@code PSMId}
     * @return the name
     */
    static String name(boolean decoy, String psmId) {
        return (decoy ? "decoy" : "target") + " PSM " + psmId;
    }

    MzTabFile mzTab() {
        return mzTab;
    }

    List<PercolatorTable.Row> sourceRows() {
        return List.copyOf(sourceRows);
    }

    Map<String, Joined> joined() {
        return Map.copyOf(joined);
    }

    MzMlFile spectra(Path file) {
        return spectra.get(file);
    }

    /** The rows of an mzTab, keyed on (decoy flag, PSMId); a key seen twice keeps the first. */
    private static Map<String, Map<String, String>> byKey(MzTabFile document) {
        Map<String, Map<String, String>> rows = new LinkedHashMap<>();
        for (Map<String, String> row : document.rows()) {
            rows.putIfAbsent(
                    PercolatorTable.Row.key("1".equals(row.get(DECOY)), row.get(PSM_ID)), row);
        }
        return rows;
    }

    private static String rowName(Map<String, String> row) {
        return name("1".equals(row.get(DECOY)), row.get(PSM_ID));
    }

    /**
     * The sources were joined: every Percolator row has exactly one pepXML hit, whose peptide is
     * Percolator's, and exactly one spectrum. A failure here is a fault of the sources or the
     * suite, not of the export, and makes every other check meaningless.
     *
     * @return the finding
     */
    Finding sourcesJoin() {
        return new Finding("sources join", sourceRows.size(), List.copyOf(joinFailures));
    }

    /**
     * The document has the shape PDV reads, and the metadata the rows rely on.
     *
     * @return the finding
     */
    Finding shape() {
        List<String> failures = new ArrayList<>(mzTab.shape());
        for (String column : REQUIRED_COLUMNS) {
            if (!mzTab.columns().contains(column)) {
                failures.add("no column " + column);
            }
        }
        if (mzTab.columns().contains("calc_mass_to_charge")) {
            failures.add("calc_mass_to_charge is present, and PDV dies on its null");
        }
        String unit = mzTab.metadata().get("colunit-psm");
        if (!"retention_time=[UO, UO:0000010, second, ]".equals(unit)) {
            failures.add("retention_time is not declared in seconds: " + unit);
        }
        Map<String, String> lastKeyOfRun = new HashMap<>();
        for (String key : mzTab.metadata().keySet()) {
            if (key.startsWith("ms_run[")) {
                lastKeyOfRun.put(key.substring(0, key.indexOf(']') + 1), key);
            }
        }
        for (Map.Entry<String, String> run : lastKeyOfRun.entrySet()) {
            if (!run.getValue().equals(run.getKey() + "-location")) {
                failures.add(
                        run.getKey()
                                + "'s last line is "
                                + run.getValue()
                                + ", which PDV takes as the file name");
            }
        }
        return new Finding("mzTab shape", mzTab.rows().size(), failures);
    }

    /**
     * Every source PSM appears exactly once and no mzTab PSM lacks a source row: counts on both
     * sides, and identity by (decoy flag, {@code PSMId}) both ways.
     *
     * @return the finding
     */
    Finding completeness() {
        List<String> failures = new ArrayList<>();
        Map<String, Integer> sourceCounts = new LinkedHashMap<>();
        for (PercolatorTable.Row row : sourceRows) {
            sourceCounts.merge(row.key(), 1, Integer::sum);
        }
        Map<String, Integer> documentCounts = new LinkedHashMap<>();
        for (Map<String, String> row : mzTab.rows()) {
            String decoy = row.get(DECOY);
            if (!"0".equals(decoy) && !"1".equals(decoy)) {
                failures.add(rowName(row) + ": decoy flag is " + decoy);
            }
            documentCounts.merge(
                    PercolatorTable.Row.key("1".equals(decoy), row.get(PSM_ID)), 1, Integer::sum);
        }
        long targets = sourceRows.stream().filter(row -> !row.decoy()).count();
        long decoys = sourceRows.size() - targets;
        long documentDecoys =
                mzTab.rows().stream().filter(row -> "1".equals(row.get(DECOY))).count();
        long documentTargets = mzTab.rows().size() - documentDecoys;
        if (targets != documentTargets || decoys != documentDecoys) {
            failures.add(
                    "counts differ: the sources hold "
                            + targets
                            + " target and "
                            + decoys
                            + " decoy PSMs, the mzTab "
                            + documentTargets
                            + " and "
                            + documentDecoys);
        }
        for (Map.Entry<String, Integer> source : sourceCounts.entrySet()) {
            int inDocument = documentCounts.getOrDefault(source.getKey(), 0);
            if (source.getValue() != 1) {
                failures.add(source.getKey() + " is in the source tables " + source.getValue());
            }
            if (inDocument != 1) {
                failures.add(
                        source.getKey().replace("/", " PSM ")
                                + " appears "
                                + inDocument
                                + " times in the mzTab");
            }
        }
        for (Map.Entry<String, Integer> document : documentCounts.entrySet()) {
            if (!sourceCounts.containsKey(document.getKey())) {
                failures.add(
                        document.getKey().replace("/", " PSM ")
                                + " is in the mzTab and in no source table");
            }
        }
        Set<String> psmIds = new LinkedHashSet<>();
        for (Map<String, String> row : mzTab.rows()) {
            if (!psmIds.add(row.get("PSM_ID"))) {
                failures.add(rowName(row) + ": mzTab PSM_ID " + row.get("PSM_ID") + " repeats");
            }
        }
        return new Finding(
                "every PSM exactly once", sourceRows.size() + mzTab.rows().size(), failures);
    }

    private interface RowCheck {
        void check(Joined source, Map<String, String> row, List<String> failures);
    }

    private Finding perRow(String name, RowCheck check) {
        List<String> failures = new ArrayList<>();
        long compared = 0;
        for (Map.Entry<String, Map<String, String>> entry : byKey(mzTab).entrySet()) {
            Joined source = joined.get(entry.getKey());
            if (source == null) {
                continue;
            }
            compared++;
            check.check(source, entry.getValue(), failures);
        }
        if (compared != joined.size()) {
            failures.add(
                    (joined.size() - compared)
                            + " joined source PSMs have no mzTab row to compare");
        }
        return new Finding(name, compared, failures);
    }

    private static void same(
            List<String> failures,
            Map<String, String> row,
            String column,
            String expected,
            String from) {
        String actual = row.get(column);
        if (!Objects.equals(expected, actual)) {
            failures.add(
                    rowName(row)
                            + ": "
                            + column
                            + " is \""
                            + actual
                            + "\", the source's "
                            + from
                            + " is \""
                            + expected
                            + "\"");
        }
    }

    /**
     * Percolator's score, q-value and PEP, compared as Percolator's text, in the {@code
     * search_engine_score} columns and the {@code opt_} columns.
     *
     * @return the finding
     */
    Finding scores() {
        return perRow(
                "score, q-value and PEP as Percolator's text",
                (source, row, failures) -> {
                    PercolatorTable.Row percolator = source.row();
                    same(failures, row, "search_engine_score[1]", percolator.score(), "score");
                    same(failures, row, SCORE, percolator.score(), "score");
                    same(failures, row, "search_engine_score[2]", percolator.qValue(), "q-value");
                    same(failures, row, Q_VALUE, percolator.qValue(), "q-value");
                    same(
                            failures,
                            row,
                            "search_engine_score[3]",
                            percolator.pep(),
                            "posterior_error_prob");
                    same(failures, row, PEP, percolator.pep(), "posterior_error_prob");
                });
    }

    /**
     * The charge, as the pepXML query's {@code assumed_charge} text.
     *
     * @return the finding
     */
    Finding charge() {
        return perRow(
                "charge as pepXML assumed_charge",
                (source, row, failures) ->
                        same(
                                failures,
                                row,
                                "charge",
                                source.query().assumedCharge(),
                                "assumed_charge"));
    }

    /**
     * The observed m/z, as the producing spectrum's selected-ion m/z text in the spectrum file.
     *
     * @return the finding
     */
    Finding observedMz() {
        return perRow(
                "exp_mass_to_charge as the spectrum's selected ion m/z",
                (source, row, failures) ->
                        same(
                                failures,
                                row,
                                "exp_mass_to_charge",
                                source.spectrum().selectedIonMz() == null
                                        ? "null"
                                        : source.spectrum().selectedIonMz(),
                                "selected ion m/z"));
    }

    /**
     * The retention time, as the pepXML query's {@code retention_time_sec} text, or {@code null}
     * where the query has none.
     *
     * @return the finding
     */
    Finding retentionTime() {
        return perRow(
                "retention_time as pepXML retention_time_sec",
                (source, row, failures) ->
                        same(
                                failures,
                                row,
                                "retention_time",
                                source.query().retentionTimeSec() == null
                                        ? "null"
                                        : source.query().retentionTimeSec(),
                                "retention_time_sec"));
    }

    /**
     * The accessions, as Percolator's {@code proteinIds}: every protein, in order.
     *
     * @return the finding
     */
    Finding accessions() {
        return perRow(
                "accession as Percolator's proteinIds",
                (source, row, failures) -> {
                    List<String> proteins = source.row().proteins();
                    for (String protein : proteins) {
                        if (protein.contains(";")) {
                            failures.add(
                                    rowName(row)
                                            + ": protein "
                                            + protein
                                            + " holds the accession separator; the mzTab"
                                            + " cannot state it apart from its neighbours");
                        }
                    }
                    List<String> written = List.of(row.get("accession").split(";", -1));
                    // A row whose one protein field is empty supplies no accession: null.
                    List<String> expected =
                            proteins.equals(List.of("")) ? List.of("null") : proteins;
                    if (!written.equals(expected)) {
                        failures.add(
                                rowName(row)
                                        + ": accession "
                                        + written
                                        + " is not Percolator's proteinIds "
                                        + proteins);
                    }
                });
    }

    /**
     * The sequence and flanking residues, as the pepXML hit's and Percolator's.
     *
     * @return the finding
     */
    Finding sequence() {
        return perRow(
                "sequence, pre and post as pepXML and Percolator",
                (source, row, failures) -> {
                    Peptidoforms.Parsed percolator =
                            Peptidoforms.percolator(source.row().peptide());
                    same(failures, row, "sequence", source.hit().peptide(), "pepXML peptide");
                    same(failures, row, "sequence", percolator.sequence(), "Percolator peptide");
                    same(failures, row, "pre", source.hit().previous(), "peptide_prev_aa");
                    same(failures, row, "pre", percolator.previous(), "Percolator flank");
                    same(failures, row, "post", source.hit().next(), "peptide_next_aa");
                    same(failures, row, "post", percolator.next(), "Percolator flank");
                });
    }

    /**
     * The modifications the pepXML hit states, as (position, delta) values: each residue's {@code
     * static} and {@code variable} deltas, and an N-terminal delta from the declaration whose
     * terminal-group mass the hit's {@code mod_nterm_mass} equals. A C-terminal modification has no
     * faithful form, so a row carrying one is itself a failure.
     *
     * @param joined the joined row
     * @param failures where a source the suite cannot read is reported
     * @return the deltas by position, {@linkplain Peptidoforms#normalised normalised}
     */
    static Map<Integer, List<BigDecimal>> expectedFromPepXml(Joined joined, List<String> failures) {
        String name = name(joined.row().decoy(), joined.row().psmId());
        Map<Integer, List<BigDecimal>> deltas = new TreeMap<>();
        PepXmlFile.Hit hit = joined.hit();
        for (PepXmlFile.ResidueMod mod : hit.residueMods()) {
            if (mod.staticDelta() == null && mod.variableDelta() == null) {
                failures.add(name + ": residue " + mod.position() + " states no delta");
            }
            for (String delta : new String[] {mod.staticDelta(), mod.variableDelta()}) {
                if (delta != null) {
                    deltas.computeIfAbsent(mod.position(), ignored -> new ArrayList<>())
                            .add(new BigDecimal(delta));
                }
            }
        }
        if (hit.ntermMass() != null) {
            BigDecimal delta = ntermDelta(joined.pepXml(), hit.ntermMass());
            if (delta == null) {
                failures.add(
                        name
                                + ": no N-terminal declaration has the mass "
                                + hit.ntermMass()
                                + ", so its delta is not stated");
            } else {
                deltas.computeIfAbsent(0, ignored -> new ArrayList<>()).add(delta);
            }
        }
        if (hit.ctermMass() != null) {
            failures.add(
                    name
                            + ": the hit carries a C-terminal modification ("
                            + hit.ctermMass()
                            + "), which no mzTab row can represent");
        }
        return Peptidoforms.normalised(deltas);
    }

    private static BigDecimal ntermDelta(PepXmlFile pepXml, String ntermMass) {
        BigDecimal found = null;
        int matches = 0;
        for (PepXmlFile.Declaration declaration : pepXml.declarations()) {
            if (declaration.element().equals("terminal")
                    && declaration.site().equalsIgnoreCase("n")
                    && declaration.mass().equals(ntermMass)) {
                matches++;
                found = new BigDecimal(declaration.massdiff());
            }
        }
        if (matches != 1) {
            return null;
        }
        // Cross-check: the group mass is the delta plus the hydrogen of a free N terminus.
        BigDecimal implied = new BigDecimal(ntermMass).subtract(HYDROGEN);
        if (implied.subtract(found).abs().compareTo(new BigDecimal("0.000002")) > 0) {
            return null;
        }
        return found;
    }

    /**
     * The modifications, as parsed values: the ProForma column and the {@code modifications} column
     * each against the pepXML hit, position and exact delta.
     *
     * @return the finding
     */
    Finding modificationsAgainstPepXml() {
        return perRow(
                "modifications as the pepXML hit's (position, delta)",
                (source, row, failures) -> {
                    Map<Integer, List<BigDecimal>> expected = expectedFromPepXml(source, failures);
                    String name = rowName(row);
                    try {
                        Peptidoforms.Parsed proForma = Peptidoforms.proForma(row.get(PROFORMA));
                        if (!proForma.sequence().equals(source.hit().peptide())) {
                            failures.add(
                                    name
                                            + ": ProForma's residues "
                                            + proForma.sequence()
                                            + " are not "
                                            + source.hit().peptide());
                        }
                        if (!proForma.sortedDeltas().equals(expected)) {
                            failures.add(
                                    name
                                            + ": ProForma "
                                            + row.get(PROFORMA)
                                            + " states "
                                            + proForma.sortedDeltas()
                                            + ", the pepXML hit "
                                            + expected);
                        }
                    } catch (IllegalArgumentException unreadable) {
                        failures.add(name + ": " + unreadable.getMessage());
                    }
                    try {
                        Peptidoforms.Parsed column =
                                new Peptidoforms.Parsed(
                                        "",
                                        Peptidoforms.mzTabModifications(row.get("modifications")),
                                        null,
                                        null);
                        if (!column.sortedDeltas().equals(expected)) {
                            failures.add(
                                    name
                                            + ": modifications "
                                            + row.get("modifications")
                                            + " states "
                                            + column.sortedDeltas()
                                            + ", the pepXML hit "
                                            + expected);
                        }
                    } catch (IllegalArgumentException unreadable) {
                        failures.add(name + ": " + unreadable.getMessage());
                    }
                });
    }

    /**
     * The modifications against Percolator's peptide: Percolator shows Comet's variable
     * modifications (a residue's {@code variable} delta, and a variable N-terminal one) rounded to
     * the decimals it prints, and no static one. Positions must be equal, and each mass within half
     * a unit of Percolator's last printed decimal of the ProForma column's.
     *
     * @return the finding
     */
    Finding modificationsAgainstPercolator() {
        return perRow(
                "modifications as Percolator's peptide",
                (source, row, failures) -> {
                    String name = rowName(row);
                    Map<Integer, List<BigDecimal>> variable = new TreeMap<>();
                    for (PepXmlFile.ResidueMod mod : source.hit().residueMods()) {
                        if (mod.variableDelta() != null) {
                            variable.computeIfAbsent(mod.position(), ignored -> new ArrayList<>())
                                    .add(new BigDecimal(mod.variableDelta()));
                        }
                    }
                    if (source.hit().ntermMass() != null) {
                        for (PepXmlFile.Declaration declaration : source.pepXml().declarations()) {
                            if (declaration.element().equals("terminal")
                                    && declaration.site().equalsIgnoreCase("n")
                                    && declaration.mass().equals(source.hit().ntermMass())
                                    && "Y".equals(declaration.variable())) {
                                variable.computeIfAbsent(0, ignored -> new ArrayList<>())
                                        .add(new BigDecimal(declaration.massdiff()));
                            }
                        }
                    }
                    Map<Integer, List<BigDecimal>> written;
                    try {
                        written = Peptidoforms.proForma(row.get(PROFORMA)).deltas();
                    } catch (IllegalArgumentException unreadable) {
                        failures.add(name + ": " + unreadable.getMessage());
                        return;
                    }
                    Map<Integer, List<BigDecimal>> percolator =
                            Peptidoforms.percolator(source.row().peptide()).deltas();
                    if (!percolator.keySet().equals(variable.keySet())) {
                        failures.add(
                                name
                                        + ": Percolator's "
                                        + source.row().peptide()
                                        + " modifies positions "
                                        + percolator.keySet()
                                        + ", the pepXML's variable modifications "
                                        + variable.keySet());
                        return;
                    }
                    for (Map.Entry<Integer, List<BigDecimal>> entry : percolator.entrySet()) {
                        for (BigDecimal shown : entry.getValue()) {
                            BigDecimal half =
                                    BigDecimal.ONE
                                            .movePointLeft(Math.max(0, shown.scale()))
                                            .divide(new BigDecimal(2));
                            boolean matched = false;
                            for (BigDecimal delta :
                                    written.getOrDefault(entry.getKey(), List.of())) {
                                if (delta.subtract(shown).abs().compareTo(half) <= 0) {
                                    matched = true;
                                }
                            }
                            if (!matched) {
                                failures.add(
                                        name
                                                + ": Percolator's "
                                                + shown
                                                + " at position "
                                                + entry.getKey()
                                                + " matches no delta the mzTab writes there: "
                                                + row.get(PROFORMA));
                            }
                        }
                    }
                });
    }

    /**
     * Fixed modifications are explicit: every residue a static declaration of the search names
     * carries that declaration's delta in the ProForma column (PDV applies none itself).
     *
     * @return the finding
     */
    Finding fixedModifications() {
        return perRow(
                "fixed modifications written on every residue they apply to",
                (source, row, failures) -> {
                    Peptidoforms.Parsed proForma;
                    try {
                        proForma = Peptidoforms.proForma(row.get(PROFORMA));
                    } catch (IllegalArgumentException unreadable) {
                        failures.add(rowName(row) + ": " + unreadable.getMessage());
                        return;
                    }
                    for (PepXmlFile.Declaration declaration : source.pepXml().declarations()) {
                        if (!declaration.element().equals("aminoacid")
                                || !"N".equals(declaration.variable())) {
                            continue;
                        }
                        BigDecimal delta = new BigDecimal(declaration.massdiff());
                        String sequence = proForma.sequence();
                        for (int index = 0; index < sequence.length(); index++) {
                            if (sequence.charAt(index) != declaration.site().charAt(0)) {
                                continue;
                            }
                            boolean carried = false;
                            for (BigDecimal written :
                                    proForma.deltas().getOrDefault(index + 1, List.of())) {
                                carried |= written.compareTo(delta) == 0;
                            }
                            if (!carried) {
                                failures.add(
                                        rowName(row)
                                                + ": residue "
                                                + (index + 1)
                                                + " ("
                                                + declaration.site()
                                                + ") lacks the fixed "
                                                + declaration.massdiff()
                                                + ": "
                                                + row.get(PROFORMA));
                            }
                        }
                    }
                });
    }

    /**
     * The columns no source supplies are the literal {@code null} in every row.
     *
     * @return the finding
     */
    Finding nulls() {
        List<String> failures = new ArrayList<>();
        long compared = 0;
        for (Map<String, String> row : mzTab.rows()) {
            for (String column : NULL_COLUMNS) {
                compared++;
                if (!"null".equals(row.get(column))) {
                    failures.add(
                            rowName(row)
                                    + ": "
                                    + column
                                    + " is \""
                                    + row.get(column)
                                    + "\", and no source supplies it");
                }
            }
        }
        return new Finding("unsupplied columns null: " + NULL_COLUMNS, compared, failures);
    }

    /**
     * The MGF a {@code spectra_ref}'s {@code ms_run} names, as PDV finds it: the base name of the
     * run's {@code -location}, among the files beside the mzTab.
     */
    private Path mgfOf(MzTabFile document, String run) {
        String location = document.metadata().get(run + "-location");
        if (location == null) {
            return null;
        }
        Path named = Path.of(URI.create(location));
        Path beside = mzTabFile.toAbsolutePath().resolveSibling(named.getFileName());
        if (!named.equals(beside) || !Files.isRegularFile(beside)) {
            return null;
        }
        return beside;
    }

    private List<MgfEntries.Entry> entries(Path mgf) throws IOException {
        List<MgfEntries.Entry> entries = mgfs.get(mgf);
        if (entries == null) {
            entries = MgfEntries.read(mgf);
            mgfs.put(mgf, entries);
        }
        return entries;
    }

    /**
     * Gate item 7, on the generated mzTab.
     *
     * @return the finding
     * @throws IOException if an MGF cannot be read
     */
    Finding spectraRef() throws IOException {
        return spectraRef(mzTab);
    }

    /**
     * Gate item 7 on a document: every row's {@code spectra_ref} resolves, exactly as PDV resolves
     * it ({@code ms_run[n]} to its {@code -location}'s MGF, {@code index=k} to the {@code k}-th
     * entry, 0-based), to the spectrum that produced the PSM -- found independently from the {@code
     * PSMId} through the pepXML to the mzML spectrum -- with every m/z and intensity equal to the
     * source's binary value, the precursor m/z equal to the selected ion's text, and the native id
     * as title. And every reference is the row's own: PDV keeps only the first row of a repeated
     * reference, so a repeated one makes a PSM unreachable.
     *
     * @param document the mzTab
     * @return the finding; {@code compared} counts peak values
     * @throws IOException if an MGF cannot be read
     */
    Finding spectraRef(MzTabFile document) throws IOException {
        List<String> failures = new ArrayList<>();
        long peaks = 0;
        Map<String, String> firstRowOfRef = new HashMap<>();
        Map<String, Integer> rowsPerRun = new TreeMap<>();
        for (Map<String, String> row : document.rows()) {
            String name = rowName(row);
            String ref = row.get("spectra_ref");
            String previous = firstRowOfRef.putIfAbsent(ref, name);
            if (previous != null) {
                failures.add(
                        name
                                + ": spectra_ref "
                                + ref
                                + " is also "
                                + previous
                                + "'s, and PDV selects only the first row of a reference");
            }
            int split = ref == null ? -1 : ref.indexOf(":index=");
            if (split < 0 || !ref.startsWith("ms_run[")) {
                failures.add(name + ": spectra_ref " + ref + " is not ms_run[n]:index=k");
                continue;
            }
            String run = ref.substring(0, split);
            rowsPerRun.merge(run, 1, Integer::sum);
            int index;
            try {
                index = Integer.parseInt(ref.substring(split + ":index=".length()));
            } catch (NumberFormatException notAnIndex) {
                failures.add(name + ": spectra_ref " + ref + " has no integer index");
                continue;
            }
            Path mgf = mgfOf(document, run);
            if (mgf == null) {
                failures.add(name + ": " + run + "'s location names no MGF beside the mzTab");
                continue;
            }
            List<MgfEntries.Entry> entries = entries(mgf);
            if (index < 0 || index >= entries.size()) {
                failures.add(
                        name
                                + ": spectra_ref "
                                + ref
                                + " is beyond "
                                + mgf.getFileName()
                                + "'s "
                                + entries.size()
                                + " entries");
                continue;
            }
            Joined source =
                    joined.get(
                            PercolatorTable.Row.key("1".equals(row.get(DECOY)), row.get(PSM_ID)));
            if (source == null) {
                failures.add(name + ": no source PSM to find the producing spectrum of");
                continue;
            }
            peaks +=
                    compare(
                            name,
                            ref,
                            entries.get(index),
                            source.spectrum(),
                            source.query().assumedCharge(),
                            failures);
        }
        for (Map.Entry<String, Integer> run : rowsPerRun.entrySet()) {
            Path mgf = mgfOf(document, run.getKey());
            if (mgf != null && entries(mgf).size() != run.getValue()) {
                failures.add(
                        run.getKey()
                                + "'s MGF holds "
                                + entries(mgf).size()
                                + " entries for "
                                + run.getValue()
                                + " rows");
            }
        }
        return new Finding("spectra_ref resolves to the producing spectrum", peaks, failures);
    }

    private static long compare(
            String name,
            String ref,
            MgfEntries.Entry entry,
            MzMlFile.Spectrum spectrum,
            String charge,
            List<String> failures) {
        String prefix = name + ": " + ref + " (entry " + entry.ordinal() + ") ";
        String entryCharge = entry.headers().get("CHARGE");
        if (entryCharge != null && !(charge + "+").equals(entryCharge)) {
            failures.add(
                    prefix
                            + "states CHARGE="
                            + entryCharge
                            + ", the PSM's assumed_charge is "
                            + charge);
        }
        if (!spectrum.id().equals(entry.headers().get("TITLE"))) {
            failures.add(
                    prefix
                            + "is titled "
                            + entry.headers().get("TITLE")
                            + ", the producing spectrum is "
                            + spectrum.id());
        }
        String pepmass = entry.headers().get("PEPMASS");
        String precursor = pepmass == null ? null : pepmass.split("[ \t]")[0];
        if (!Objects.equals(precursor, spectrum.selectedIonMz())) {
            failures.add(
                    prefix
                            + "has precursor "
                            + precursor
                            + ", the producing spectrum's selected ion is "
                            + spectrum.selectedIonMz());
        }
        if (entry.peaks().size() != spectrum.mz().length) {
            failures.add(
                    prefix
                            + "has "
                            + entry.peaks().size()
                            + " peaks, the producing spectrum "
                            + spectrum.id()
                            + " has "
                            + spectrum.mz().length);
            return 0;
        }
        long compared = 0;
        int mismatches = 0;
        for (int index = 0; index < spectrum.mz().length; index++) {
            String[] peak = entry.peaks().get(index);
            compared += 2;
            if (!sameValue(peak[0], spectrum.mz()[index])
                    || !sameValue(peak[1], spectrum.intensity()[index])) {
                if (mismatches++ == 0) {
                    failures.add(
                            prefix
                                    + "peak "
                                    + index
                                    + " is "
                                    + peak[0]
                                    + " "
                                    + peak[1]
                                    + ", the producing spectrum "
                                    + spectrum.id()
                                    + "'s is "
                                    + new BigDecimal(spectrum.mz()[index]).toPlainString()
                                    + " "
                                    + new BigDecimal(spectrum.intensity()[index]).toPlainString());
                }
            }
        }
        return compared;
    }

    /** Whether a peak's text parses, as PDV parses it (a double), to exactly the source's bits. */
    private static boolean sameValue(String text, double source) {
        try {
            return Double.doubleToRawLongBits(Double.parseDouble(text))
                    == Double.doubleToRawLongBits(source);
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    /**
     * Every gate-6 and gate-7 check, in order.
     *
     * @return the findings
     * @throws IOException if an MGF cannot be read
     */
    List<Finding> all() throws IOException {
        return List.of(
                sourcesJoin(),
                shape(),
                completeness(),
                scores(),
                charge(),
                observedMz(),
                retentionTime(),
                accessions(),
                sequence(),
                modificationsAgainstPepXml(),
                modificationsAgainstPercolator(),
                fixedModifications(),
                nulls(),
                spectraRef());
    }

    /**
     * Runs every check and reports them, for the run-level test in {@code cometgui-workflow}, which
     * compiles this source and calls it by reflection, so it takes and returns JDK types only.
     *
     * @param targets Percolator's target table
     * @param decoys its decoy table, or {@code null}
     * @param sources each source as {@code [base, mzML, pepXML, decoy pepXML or ""]}
     * @param mzTab the generated mzTab
     * @return one report line per check, then each failure; every check that failed or compared
     *     nothing is listed under a first line {@code FAILED}, else the first line is {@code
     *     PASSED}
     * @throws IOException if a file cannot be read
     */
    public static List<String> verify(
            Path targets, Path decoys, List<List<String>> sources, Path mzTab) throws IOException {
        List<Source> parsed = new ArrayList<>();
        for (List<String> source : sources) {
            parsed.add(
                    new Source(
                            source.get(0),
                            Path.of(source.get(1)),
                            Path.of(source.get(2)),
                            source.get(3).isEmpty() ? null : Path.of(source.get(3))));
        }
        FidelityCheck check = new FidelityCheck(targets, decoys, parsed, mzTab);
        List<String> report = new ArrayList<>();
        boolean failed = false;
        for (Finding finding : check.all()) {
            failed |= !finding.failures().isEmpty() || finding.compared() == 0;
            report.add(finding.report());
        }
        report.add(0, failed ? "FAILED" : "PASSED");
        return report;
    }
}
