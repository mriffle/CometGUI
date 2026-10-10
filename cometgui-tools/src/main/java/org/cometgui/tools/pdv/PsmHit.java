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

package org.cometgui.tools.pdv;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.cometgui.tools.comet.PepXmlModificationInfo;
import org.cometgui.tools.comet.PepXmlQuery;
import org.cometgui.tools.comet.PepXmlResidueModification;
import org.cometgui.tools.comet.PepXmlSearchHit;
import org.cometgui.tools.comet.PepXmlTerminalModification;
import org.cometgui.tools.comet.PepXmlTerminus;

/**
 * What the mzTab takes from one pepXML search hit, every value the file's own text, and what is
 * needed to check the Percolator row that named it.
 *
 * <h2>Modifications (design decisions P11-8 and P11-15)</h2>
 *
 * <p>Each residue modification is written as Comet states it on that residue: the {@code static}
 * delta's text, then the {@code variable} delta's text, each as a bracketed signed mass after the
 * residue ({@code C[+57.021464]}, {@code M[+15.994915]}, and {@code C[+57.021464][+15.994915]} for
 * a residue that carries both). A sign is added to a text that has none; nothing else is changed.
 * Fixed modifications are written explicitly because PDV applies none.
 *
 * <p>A terminal modification is harder: Comet writes {@code mod_nterm_mass} and {@code
 * mod_cterm_mass} as the <em>mass of the modified terminal group</em>, the sum of every terminal
 * modification that applied plus a hydrogen or hydroxyl, never as a delta. Recovering a delta from
 * it would be a computation, so a terminal modification is transcribed only when the search
 * declared <strong>exactly one</strong> N-terminal modification and the hit's {@code
 * mod_nterm_mass} text is that declaration's {@code mass} text: the delta is then the declaration's
 * {@code massdiff} text, written {@code [+delta]-} before the first residue. With two or more
 * N-terminal declarations a matching mass is not proof -- a variable declaration's mass includes a
 * static peptide-terminal one -- so the hit is refused. A C-terminal modification is always
 * refused: PDV's peptide parser has no C-terminal form.
 *
 * @param startScan the query's {@code start_scan}
 * @param charge the query's {@code assumed_charge}
 * @param position the hit's 1-based position in its query
 * @param nativeId the query's {@code spectrumNativeID}
 * @param retentionTime the query's {@code retention_time_sec} text, when present
 * @param peptide the hit's residues
 * @param previous {@code peptide_prev_aa}
 * @param next {@code peptide_next_aa}
 * @param proForma the peptide with every modification as a bracketed signed mass
 * @param modifications the mzTab {@code modifications} value: {@code <position>-CHEMMOD:<signed
 *     delta>} for each modification, comma-separated, position 0 for the N-terminus; {@code null}
 *     when there is none
 * @param variableByPosition each variable delta's text by its residue's position
 * @param nTerminal the N-terminal modification, when there is one
 */
record PsmHit(
        long startScan,
        int charge,
        int position,
        String nativeId,
        Optional<String> retentionTime,
        String peptide,
        String previous,
        String next,
        String proForma,
        String modifications,
        Map<Integer, String> variableByPosition,
        Optional<Terminal> nTerminal) {

    /** The mzTab null. */
    static final String NULL = "null";

    /**
     * A transcribed N-terminal modification.
     *
     * @param delta the declaration's {@code massdiff} text
     * @param variable whether the declaration is variable, which is when Percolator shows it
     */
    record Terminal(String delta, boolean variable) {}

    /** Copies the map. */
    PsmHit {
        variableByPosition = Collections.unmodifiableMap(new TreeMap<>(variableByPosition));
    }

    /**
     * Reads what the mzTab needs from a hit, or refuses it.
     *
     * @param query the hit's query
     * @param hit the hit
     * @param declared the terminal modifications the file's search summary declares
     * @return the hit's values
     * @throws MzTabRefusal if the hit cannot be represented faithfully
     */
    static PsmHit of(
            PepXmlQuery query, PepXmlSearchHit hit, List<PepXmlTerminalModification> declared)
            throws MzTabRefusal {
        String nativeId =
                query.spectrumNativeId()
                        .orElseThrow(
                                () ->
                                        new MzTabRefusal(
                                                PdvMzTabException.Reason.NO_NATIVE_ID,
                                                "has no spectrumNativeID on its spectrum_query \""
                                                        + query.spectrum()
                                                        + "\", so no spectrum of the spectrum"
                                                        + " file can be shown as its own"));
        Map<Integer, PepXmlResidueModification> residues = new TreeMap<>();
        Optional<Terminal> nTerminal = Optional.empty();
        if (hit.modificationInfo().isPresent()) {
            PepXmlModificationInfo info = hit.modificationInfo().get();
            if (!info.sequenceVariants().isEmpty()) {
                throw new MzTabRefusal(
                        PdvMzTabException.Reason.SEQUENCE_VARIANT,
                        "carries the PEFF sequence variant "
                                + info.sequenceVariants().get(0).element()
                                + " "
                                + info.sequenceVariants().get(0).attributes()
                                + ", which an mzTab row cannot state");
            }
            if (info.ctermMass().isPresent()) {
                throw new MzTabRefusal(
                        PdvMzTabException.Reason.UNREPRESENTABLE_MODIFICATION,
                        "carries a C-terminal modification (mod_cterm_mass=\""
                                + info.ctermMass().get().text()
                                + "\"), and PDV's peptide form has no C-terminal modification");
            }
            if (info.ntermMass().isPresent()) {
                nTerminal = Optional.of(nTerminal(info.ntermMass().get().text(), declared));
            }
            for (PepXmlResidueModification residue : info.residues()) {
                residues.put(residue.position(), residue);
            }
        }
        StringBuilder proForma = new StringBuilder();
        List<String> modifications = new ArrayList<>();
        if (nTerminal.isPresent()) {
            String delta = signed(nTerminal.get().delta());
            proForma.append('[').append(delta).append("]-");
            modifications.add("0-CHEMMOD:" + delta);
        }
        Map<Integer, String> variable = new TreeMap<>();
        String peptide = hit.peptide();
        for (int index = 0; index < peptide.length(); index++) {
            int position = index + 1;
            proForma.append(peptide.charAt(index));
            PepXmlResidueModification residue = residues.get(position);
            if (residue == null) {
                continue;
            }
            if (residue.staticDelta().isPresent()) {
                String delta = signed(residue.staticDelta().get().text());
                proForma.append('[').append(delta).append(']');
                modifications.add(position + "-CHEMMOD:" + delta);
            }
            if (residue.variableDelta().isPresent()) {
                String text = residue.variableDelta().get().text();
                String delta = signed(text);
                proForma.append('[').append(delta).append(']');
                modifications.add(position + "-CHEMMOD:" + delta);
                variable.put(position, text);
            }
        }
        return new PsmHit(
                query.startScan(),
                query.assumedCharge(),
                hit.position(),
                nativeId,
                query.retentionTimeSec().map(time -> time.text()),
                peptide,
                hit.peptidePrevAa(),
                hit.peptideNextAa(),
                proForma.toString(),
                modifications.isEmpty() ? NULL : String.join(",", modifications),
                variable,
                nTerminal);
    }

    private static Terminal nTerminal(String mass, List<PepXmlTerminalModification> declared)
            throws MzTabRefusal {
        List<PepXmlTerminalModification> nTerminal = new ArrayList<>();
        for (PepXmlTerminalModification declaration : declared) {
            if (declaration.terminus() == PepXmlTerminus.N) {
                nTerminal.add(declaration);
            }
        }
        if (nTerminal.size() != 1) {
            throw new MzTabRefusal(
                    PdvMzTabException.Reason.UNREPRESENTABLE_MODIFICATION,
                    "carries the N-terminal group mass mod_nterm_mass=\""
                            + mass
                            + "\", and the search declares "
                            + nTerminal.size()
                            + " N-terminal modifications, so which deltas that mass holds"
                            + " cannot be read without computing them");
        }
        PepXmlTerminalModification only = nTerminal.get(0);
        if (!only.mass().text().equals(mass)) {
            throw new MzTabRefusal(
                    PdvMzTabException.Reason.UNREPRESENTABLE_MODIFICATION,
                    "carries the N-terminal group mass mod_nterm_mass=\""
                            + mass
                            + "\", which is not the mass \""
                            + only.mass().text()
                            + "\" of the one N-terminal modification the search declares, so"
                            + " its delta cannot be read without computing it");
        }
        return new Terminal(only.massDiff().text(), only.variable());
    }

    /** A delta's text with a sign: {@code +} added when it has none. */
    static String signed(String delta) {
        return delta.startsWith("-") || delta.startsWith("+") ? delta : "+" + delta;
    }

    /**
     * Compares the Percolator row's peptide with this hit, as values.
     *
     * <p>The residues and flanking residues must be equal. Every variable modification must appear
     * in Percolator's peptide at the same position, and nothing else may: Percolator's mass,
     * printed to fewer places, must be the pepXML delta rounded to its own places -- within half a
     * unit of its last digit. A static modification is not shown by Percolator and is not compared.
     *
     * @param percolator the Percolator {@code peptide} field
     * @return why they differ, or empty if they agree
     */
    Optional<String> mismatch(String percolator) {
        Optional<PercolatorPeptide> parsed = PercolatorPeptide.parse(percolator);
        if (parsed.isEmpty()) {
            return Optional.of(
                    "has the Percolator peptide \""
                            + percolator
                            + "\", which is not <prev>.<peptide>.<next> with bracketed masses");
        }
        PercolatorPeptide other = parsed.get();
        String hitText = previous + "." + proForma + "." + next;
        if (!other.residues().equals(peptide)
                || !other.previous().equals(previous)
                || !other.next().equals(next)) {
            return Optional.of(differ(percolator, hitText, "residues"));
        }
        if (!other.byPosition().keySet().equals(variableByPosition.keySet())) {
            return Optional.of(differ(percolator, hitText, "modified positions"));
        }
        for (Map.Entry<Integer, String> mass : other.byPosition().entrySet()) {
            if (!agree(mass.getValue(), variableByPosition.get(mass.getKey()))) {
                return Optional.of(
                        differ(percolator, hitText, "mass at position " + mass.getKey()));
            }
        }
        boolean variableTerminal = nTerminal.isPresent() && nTerminal.get().variable();
        if (other.nTerminal().isPresent() != variableTerminal
                || variableTerminal && !agree(other.nTerminal().get(), nTerminal.get().delta())) {
            return Optional.of(differ(percolator, hitText, "N-terminal modification"));
        }
        if (other.cTerminal().isPresent()) {
            return Optional.of(differ(percolator, hitText, "C-terminal modification"));
        }
        return Optional.empty();
    }

    private static String differ(String percolator, String hit, String what) {
        return "has the Percolator peptide \""
                + percolator
                + "\", whose "
                + what
                + " differ from its pepXML hit "
                + hit;
    }

    /** Whether a rounded mass text is the exact one rounded to its own places. */
    static boolean agree(String rounded, String exact) {
        BigDecimal shown = new BigDecimal(rounded);
        BigDecimal halfUnit = new BigDecimal(BigInteger.valueOf(5), shown.scale() + 1);
        return shown.subtract(new BigDecimal(exact)).abs().compareTo(halfUnit) <= 0;
    }

    /**
     * Writes the hit to the exporter's scratch record.
     *
     * @param out where
     * @throws IOException if it cannot be written
     */
    void write(DataOutput out) throws IOException {
        out.writeLong(startScan);
        out.writeInt(charge);
        out.writeInt(position);
        out.writeUTF(nativeId);
        out.writeBoolean(retentionTime.isPresent());
        out.writeUTF(retentionTime.orElse(""));
        out.writeUTF(peptide);
        out.writeUTF(previous);
        out.writeUTF(next);
        out.writeUTF(proForma);
        out.writeUTF(modifications);
        out.writeInt(variableByPosition.size());
        for (Map.Entry<Integer, String> mass : variableByPosition.entrySet()) {
            out.writeInt(mass.getKey());
            out.writeUTF(mass.getValue());
        }
        out.writeBoolean(nTerminal.isPresent());
        out.writeUTF(nTerminal.map(Terminal::delta).orElse(""));
        out.writeBoolean(nTerminal.isPresent() && nTerminal.get().variable());
    }

    /**
     * Reads a hit back from the exporter's scratch record.
     *
     * @param in where
     * @return the hit
     * @throws IOException if it cannot be read
     */
    static PsmHit read(DataInput in) throws IOException {
        long startScan = in.readLong();
        int charge = in.readInt();
        int position = in.readInt();
        String nativeId = in.readUTF();
        boolean hasTime = in.readBoolean();
        String time = in.readUTF();
        String peptide = in.readUTF();
        String previous = in.readUTF();
        String next = in.readUTF();
        String proForma = in.readUTF();
        String modifications = in.readUTF();
        int count = in.readInt();
        Map<Integer, String> variable = new TreeMap<>();
        for (int index = 0; index < count; index++) {
            variable.put(in.readInt(), in.readUTF());
        }
        boolean hasTerminal = in.readBoolean();
        String delta = in.readUTF();
        boolean variableTerminal = in.readBoolean();
        return new PsmHit(
                startScan,
                charge,
                position,
                nativeId,
                hasTime ? Optional.of(time) : Optional.empty(),
                peptide,
                previous,
                next,
                proForma,
                modifications,
                variable,
                hasTerminal
                        ? Optional.of(new Terminal(delta, variableTerminal))
                        : Optional.empty());
    }
}
