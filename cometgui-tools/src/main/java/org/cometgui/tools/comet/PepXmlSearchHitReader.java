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

package org.cometgui.tools.comet;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Reads the search hits of one pepXML file Comet wrote, streaming, in file order.
 *
 * <p>The file is read with the same hardened StAX factory as {@link CometPepXmlValidator} (no DTD,
 * no external entities, a {@code DOCTYPE} refused) and is never held whole: one spectrum query and
 * its hits at a time are built and handed to a {@link PepXmlVisitor}, so a file far larger than
 * memory is read in the memory of its largest query. {@link PepXmlHitIndex} is the keyed lookup
 * built on it.
 *
 * <h2>Faithful or refused (design decision P11-8)</h2>
 *
 * <p>Every value is carried as the file's text -- decimals as {@link PepXmlDecimal}, integers only
 * in their canonical form so that the number <em>is</em> the text -- and nothing is recomputed.
 * Whatever this reader cannot represent exactly is refused with a {@link CometOutputException}
 * naming the file, the line, and the {@code spectrum_query} and {@code search_hit} it is in:
 *
 * <ul>
 *   <li>a mass, mass difference or retention time that is not a plain decimal; an integer that is
 *       not canonical; a peptide that is not a run of upper-case residue letters;
 *   <li>a {@code mod_aminoacid_mass} whose position is outside its peptide, a second one at the
 *       same position, one with neither a {@code static} nor a {@code variable} delta, or a {@code
 *       source} other than {@code param} or {@code peff} (and {@code id} exactly with {@code
 *       peff});
 *   <li>a second {@code modification_info} in a hit, a second {@code search_result} in a query, a
 *       second {@code search_score} of one name, a missing required attribute;
 *   <li>a {@code search_engine} other than {@code Comet}, a second {@code msms_run_summary} or
 *       {@code search_summary}, or a {@code spectrum_query} before the {@code search_summary}.
 * </ul>
 *
 * <h2>What is ignored, and why that is safe</h2>
 *
 * <p>The element vocabulary is <strong>closed</strong>: the nineteen elements Comet 2026.02.2 and
 * 2026.03.0 write ({@code CometWritePepXML.cpp} at both tags), each only under the parent Comet
 * writes it under. Any other element, in any namespace, anywhere, is refused -- an element this
 * reader does not know could qualify a hit in a way it would silently drop.
 *
 * <p>Attributes are closed on the elements that define a mass -- {@code modification_info}, {@code
 * mod_aminoacid_mass}, {@code aminoacid_modification}, {@code terminal_modification} -- because an
 * unknown attribute there could change what a mass means. Elsewhere an attribute this reader does
 * not read is ignored, because no value it yields derives from one: Comet's own counters ({@code
 * num_matched_ions}, {@code tot_num_ions}, {@code num_tol_term}, {@code num_missed_cleavages},
 * {@code num_matched_peptides}), the header's descriptive attributes, and the {@code parameter},
 * {@code sample_enzyme}, {@code specificity}, {@code search_database} and {@code
 * enzymatic_search_constraint} elements, whose content describes the search rather than any hit.
 * Character data is ignored: no element Comet writes carries any.
 */
public final class PepXmlSearchHitReader {

    static final String SEARCH_RESULT = "search_result";

    static final String SEARCH_HIT = "search_hit";

    static final String MODIFICATION_INFO = "modification_info";

    static final String MOD_AMINOACID_MASS = "mod_aminoacid_mass";

    /** Every element Comet writes, mapped to the one parent it writes it under. */
    static final Map<String, String> PARENT =
            Map.ofEntries(
                    Map.entry(CometPepXmlValidator.RUN_SUMMARY, CometPepXmlValidator.ROOT),
                    Map.entry("sample_enzyme", CometPepXmlValidator.RUN_SUMMARY),
                    Map.entry("specificity", "sample_enzyme"),
                    Map.entry(
                            CometPepXmlValidator.SEARCH_SUMMARY, CometPepXmlValidator.RUN_SUMMARY),
                    Map.entry("search_database", CometPepXmlValidator.SEARCH_SUMMARY),
                    Map.entry("enzymatic_search_constraint", CometPepXmlValidator.SEARCH_SUMMARY),
                    Map.entry("aminoacid_modification", CometPepXmlValidator.SEARCH_SUMMARY),
                    Map.entry("terminal_modification", CometPepXmlValidator.SEARCH_SUMMARY),
                    Map.entry("parameter", CometPepXmlValidator.SEARCH_SUMMARY),
                    Map.entry(
                            CometPepXmlValidator.SPECTRUM_QUERY, CometPepXmlValidator.RUN_SUMMARY),
                    Map.entry(SEARCH_RESULT, CometPepXmlValidator.SPECTRUM_QUERY),
                    Map.entry(SEARCH_HIT, SEARCH_RESULT),
                    Map.entry("alternative_protein", SEARCH_HIT),
                    Map.entry(MODIFICATION_INFO, SEARCH_HIT),
                    Map.entry("search_score", SEARCH_HIT),
                    Map.entry(MOD_AMINOACID_MASS, MODIFICATION_INFO),
                    Map.entry("aminoacid_substitution", MODIFICATION_INFO),
                    Map.entry("sequence_substitution", MODIFICATION_INFO));

    private static final Set<String> MODIFICATION_INFO_ATTRIBUTES =
            Set.of("modified_peptide", "mod_nterm_mass", "mod_cterm_mass");

    private static final Set<String> MOD_AMINOACID_MASS_ATTRIBUTES =
            Set.of("position", "mass", "static", "variable", "source", "id");

    private static final Set<String> AMINOACID_MODIFICATION_ATTRIBUTES =
            Set.of("aminoacid", "massdiff", "mass", "variable", "binary");

    private static final Set<String> TERMINAL_MODIFICATION_ATTRIBUTES =
            Set.of("terminus", "massdiff", "mass", "variable", "protein_terminus");

    /** A canonical non-negative integer: no sign, no leading zero, at most 18 digits. */
    private static final Pattern CANONICAL = Pattern.compile("0|[1-9][0-9]{0,17}");

    /** A peptide: one or more upper-case residue letters. */
    private static final Pattern PEPTIDE = Pattern.compile("[A-Z]+");

    private PepXmlSearchHitReader() {}

    /**
     * Reads a pepXML file, handing its search summary and then each spectrum query with its hits to
     * the visitor, in file order.
     *
     * @param pepXml the file
     * @param visitor what receives the content
     * @param <X> what the visitor may throw
     * @return the search summary, as the visitor received it
     * @throws CometOutputException if the file is missing, unreadable, malformed, or holds anything
     *     this reader cannot represent exactly, naming the file and where in it
     * @throws X if the visitor throws it; reading stops there
     */
    public static <X extends Exception> PepXmlSearchSummary read(
            Path pepXml, PepXmlVisitor<X> visitor) throws CometOutputException, X {
        Objects.requireNonNull(pepXml, "pepXml");
        Objects.requireNonNull(visitor, "visitor");
        if (Files.isDirectory(pepXml)) {
            throw new CometOutputException(
                    pepXml, "the pepXML file " + pepXml + " is a directory, not a file", null);
        }
        try (Source source = Source.open(pepXml)) {
            return new Walk<>(pepXml, visitor, source.reader).through();
        }
    }

    /**
     * Reads a canonical non-negative integer.
     *
     * @param text the text, or {@code null}
     * @param max the largest value accepted
     * @return the value, or -1 if the text is {@code null}, not canonical, or larger than {@code
     *     max}
     */
    static long canonical(String text, long max) {
        if (text == null || !CANONICAL.matcher(text).matches()) {
            return -1;
        }
        long value = Long.parseLong(text);
        return value <= max ? value : -1;
    }

    private static CometOutputException malformed(Path file, XMLStreamException malformed) {
        return new CometOutputException(
                file,
                "the pepXML file " + file + " is not well-formed XML: " + malformed.getMessage(),
                malformed);
    }

    private static CometOutputException unreadable(Path file, IOException unreadable) {
        return new CometOutputException(
                file,
                "the pepXML file " + file + " cannot be read: " + unreadable.getMessage(),
                unreadable);
    }

    /**
     * The open file and its parser. Its {@code close} throws only a {@link CometOutputException},
     * so a visitor's own exception -- even an {@code IOException} -- leaves {@link #read} without
     * passing through any clause that could mistake it for a problem with the file.
     */
    private static final class Source implements AutoCloseable {

        private final Path file;

        private final InputStream bytes;

        private final XMLStreamReader reader;

        private Source(Path file, InputStream bytes, XMLStreamReader reader) {
            this.file = file;
            this.bytes = bytes;
            this.reader = reader;
        }

        static Source open(Path file) throws CometOutputException {
            InputStream bytes;
            try {
                bytes = Files.newInputStream(file);
            } catch (NoSuchFileException missing) {
                throw new CometOutputException(
                        file, "the pepXML file " + file + " does not exist", missing);
            } catch (IOException unreadable) {
                throw unreadable(file, unreadable);
            }
            try {
                return new Source(
                        file,
                        bytes,
                        CometPepXmlValidator.harden(XMLInputFactory.newDefaultFactory())
                                .createXMLStreamReader(bytes));
            } catch (XMLStreamException notXml) {
                CometOutputException refused = malformed(file, notXml);
                try {
                    bytes.close();
                } catch (IOException alsoUnclosable) {
                    refused.addSuppressed(alsoUnclosable);
                }
                throw refused;
            }
        }

        /*
         * The parser is not closed: XMLStreamReader.close frees only the parser's own state and
         * does not close the input stream, the reasoning CometPepXmlValidator records.
         */
        @Override
        public void close() throws CometOutputException {
            try {
                bytes.close();
            } catch (IOException unclosable) {
                throw unreadable(file, unclosable);
            }
        }
    }

    /** One pass over one document. */
    private static final class Walk<X extends Exception> {

        private final Path file;

        private final PepXmlVisitor<X> visitor;

        private final XMLStreamReader reader;

        private final Deque<String> open = new ArrayDeque<>();

        private int runSummaries;

        private String input = "";

        private PepXmlSearchSummary summary;

        private String outputBase;

        private String version;

        private final List<PepXmlAminoAcidModification> aminoAcidModifications = new ArrayList<>();

        private final List<PepXmlTerminalModification> terminalModifications = new ArrayList<>();

        private String queryContext;

        private PepXmlQuery query;

        private int searchResults;

        private final List<PepXmlSearchHit> hits = new ArrayList<>();

        private HitInProgress hit;

        Walk(Path file, PepXmlVisitor<X> visitor, XMLStreamReader reader) {
            this.file = file;
            this.visitor = visitor;
            this.reader = reader;
        }

        PepXmlSearchSummary through() throws CometOutputException, X {
            while (hasNext()) {
                int event = next();
                if (event == XMLStreamConstants.DTD) {
                    throw refused(
                            "declares a DOCTYPE, which Comet never writes and which is refused"
                                    + " unread");
                }
                if (event == XMLStreamConstants.START_ELEMENT) {
                    start();
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    end();
                }
            }
            if (runSummaries == 0) {
                throw refused("holds no msms_run_summary");
            }
            if (summary == null) {
                throw refused("holds no search_summary");
            }
            return summary;
        }

        /** The parser's {@code hasNext}, a parse failure refusing the file. */
        private boolean hasNext() throws CometOutputException {
            try {
                return reader.hasNext();
            } catch (XMLStreamException broken) {
                throw malformed(file, broken);
            }
        }

        /** The parser's {@code next}, a parse failure refusing the file. */
        private int next() throws CometOutputException {
            try {
                return reader.next();
            } catch (XMLStreamException broken) {
                throw malformed(file, broken);
            }
        }

        private void start() throws CometOutputException {
            String local = reader.getLocalName();
            String namespace = Objects.toString(reader.getNamespaceURI(), "");
            if (open.isEmpty()) {
                if (!CometPepXmlValidator.ROOT.equals(local)
                        || !CometPepXmlValidator.NAMESPACE.equals(namespace)) {
                    throw refused(
                            "is not a pepXML file: its document element is {"
                                    + namespace
                                    + "}"
                                    + local
                                    + ", not {"
                                    + CometPepXmlValidator.NAMESPACE
                                    + "}"
                                    + CometPepXmlValidator.ROOT);
                }
                open.push(local);
                return;
            }
            String parent = open.peek();
            if (!CometPepXmlValidator.NAMESPACE.equals(namespace)
                    || !parent.equals(PARENT.get(local))) {
                throw refused(
                        "holds an element {"
                                + namespace
                                + "}"
                                + local
                                + " inside "
                                + parent
                                + ", which Comet does not write there; this reader cannot know"
                                + " what it would change");
            }
            open.push(local);
            switch (local) {
                case CometPepXmlValidator.RUN_SUMMARY -> runSummary();
                case CometPepXmlValidator.SEARCH_SUMMARY -> searchSummary();
                case "aminoacid_modification" -> aminoAcidModification();
                case "terminal_modification" -> terminalModification();
                case CometPepXmlValidator.SPECTRUM_QUERY -> spectrumQuery();
                case SEARCH_RESULT -> searchResult();
                case SEARCH_HIT -> searchHit();
                case "alternative_protein" -> hit.alternatives.add(required("protein"));
                case "search_score" -> searchScore();
                case MODIFICATION_INFO -> modificationInfo();
                case MOD_AMINOACID_MASS -> residueModification();
                case "aminoacid_substitution", "sequence_substitution" -> sequenceVariant(local);
                default -> {
                    // A header element whose content describes the search, not a hit.
                }
            }
        }

        private void end() throws X {
            String local = open.pop();
            switch (local) {
                case CometPepXmlValidator.SEARCH_SUMMARY -> {
                    summary =
                            new PepXmlSearchSummary(
                                    file,
                                    input,
                                    outputBase,
                                    version,
                                    aminoAcidModifications,
                                    terminalModifications);
                    visitor.searchSummary(summary);
                }
                case SEARCH_HIT -> {
                    hits.add(hit.finish());
                    hit = null;
                }
                case CometPepXmlValidator.SPECTRUM_QUERY -> {
                    visitor.spectrumQuery(query, List.copyOf(hits));
                    query = null;
                    queryContext = null;
                }
                default -> {
                    // Nothing to finish.
                }
            }
        }

        private void runSummary() throws CometOutputException {
            if (++runSummaries > 1) {
                throw refused(
                        "holds more than one msms_run_summary; a Comet pepXML names one spectrum"
                                + " file");
            }
            input = attribute("base_name", "") + attribute("raw_data", "");
        }

        private void searchSummary() throws CometOutputException {
            if (outputBase != null) {
                throw refused("holds more than one search_summary");
            }
            String engine = required("search_engine");
            if (!"Comet".equals(engine)) {
                throw refused(
                        "was written by search_engine=\""
                                + engine
                                + "\", not Comet; this reader knows only what Comet writes");
            }
            version = required("search_engine_version");
            outputBase = required("base_name");
        }

        private void aminoAcidModification() throws CometOutputException {
            closedAttributes(AMINOACID_MODIFICATION_ATTRIBUTES);
            String aminoAcid = required("aminoacid");
            if (aminoAcid.length() != 1) {
                throw refused(
                        "declares an aminoacid_modification on \""
                                + aminoAcid
                                + "\", which is not one residue");
            }
            aminoAcidModifications.add(
                    new PepXmlAminoAcidModification(
                            aminoAcid,
                            decimal("massdiff"),
                            decimal("mass"),
                            yesNo("variable"),
                            reader.getAttributeValue(null, "binary") != null && yesNo("binary")));
        }

        private void terminalModification() throws CometOutputException {
            closedAttributes(TERMINAL_MODIFICATION_ATTRIBUTES);
            String terminus = required("terminus");
            PepXmlTerminus which;
            if ("N".equals(terminus)) {
                which = PepXmlTerminus.N;
            } else if ("C".equals(terminus)) {
                which = PepXmlTerminus.C;
            } else {
                throw refused(
                        "declares a terminal_modification on terminus=\""
                                + terminus
                                + "\", which is neither N nor C");
            }
            terminalModifications.add(
                    new PepXmlTerminalModification(
                            which,
                            decimal("massdiff"),
                            decimal("mass"),
                            yesNo("variable"),
                            yesNo("protein_terminus")));
        }

        private void spectrumQuery() throws CometOutputException {
            if (summary == null) {
                throw refused(
                        "holds a spectrum_query before its search_summary, which Comet never"
                                + " writes");
            }
            queryContext =
                    ", spectrum_query \""
                            + attribute("spectrum", "")
                            + "\" (index \""
                            + attribute("index", "")
                            + "\")";
            searchResults = 0;
            hits.clear();
            query =
                    new PepXmlQuery(
                            required("spectrum"),
                            Optional.ofNullable(reader.getAttributeValue(null, "spectrumNativeID")),
                            integer("start_scan", Long.MAX_VALUE),
                            integer("end_scan", Long.MAX_VALUE),
                            decimal("precursor_neutral_mass"),
                            (int) integer("assumed_charge", Integer.MAX_VALUE),
                            integer("index", Long.MAX_VALUE),
                            optionalDecimal("retention_time_sec"));
        }

        private void searchResult() throws CometOutputException {
            if (++searchResults > 1) {
                throw refused(
                        "holds more than one search_result, so a hit's position would be"
                                + " ambiguous");
            }
        }

        private void searchHit() throws CometOutputException {
            int position = hits.size() + 1;
            hit =
                    new HitInProgress(
                            position,
                            ", search_hit "
                                    + position
                                    + " (hit_rank \""
                                    + attribute("hit_rank", "")
                                    + "\", peptide \""
                                    + attribute("peptide", "")
                                    + "\")");
            hit.hitRank = (int) integer("hit_rank", Integer.MAX_VALUE);
            if (hit.hitRank < 1) {
                throw refused("has hit_rank 0; Comet ranks from 1");
            }
            hit.peptide = required("peptide");
            if (!PEPTIDE.matcher(hit.peptide).matches()) {
                throw refused(
                        "has peptide \""
                                + hit.peptide
                                + "\", which is not a run of upper-case residue letters");
            }
            hit.prev = flankingResidue("peptide_prev_aa");
            hit.next = flankingResidue("peptide_next_aa");
            hit.protein = required("protein");
            hit.numTotProteins = integer("num_tot_proteins", Long.MAX_VALUE);
            hit.calcNeutralPepMass = decimal("calc_neutral_pep_mass");
            hit.massDiff = decimal("massdiff");
        }

        private String flankingResidue(String name) throws CometOutputException {
            String text = required(name);
            if (text.length() != 1) {
                throw refused("has " + name + "=\"" + text + "\", which is not one character");
            }
            return text;
        }

        private void searchScore() throws CometOutputException {
            String name = required("name");
            if (!hit.scoreNames.add(name)) {
                throw refused("has a second search_score named \"" + name + "\"");
            }
            hit.scores.add(new PepXmlNameValue(name, required("value")));
        }

        private void modificationInfo() throws CometOutputException {
            if (hit.modifiedPeptide != null) {
                throw refused("has more than one modification_info");
            }
            closedAttributes(MODIFICATION_INFO_ATTRIBUTES);
            hit.modifiedPeptide = required("modified_peptide");
            hit.ntermMass = optionalDecimal("mod_nterm_mass");
            hit.ctermMass = optionalDecimal("mod_cterm_mass");
        }

        private void residueModification() throws CometOutputException {
            closedAttributes(MOD_AMINOACID_MASS_ATTRIBUTES);
            String positionText = required("position");
            long position = canonical(positionText, Integer.MAX_VALUE);
            if (position < 1 || position > hit.peptide.length()) {
                throw refused(
                        "has a mod_aminoacid_mass at position \""
                                + positionText
                                + "\", outside the peptide "
                                + hit.peptide
                                + " (residues 1 to "
                                + hit.peptide.length()
                                + ")");
            }
            if (!hit.modifiedPositions.add((int) position)) {
                throw refused("has a second mod_aminoacid_mass at position " + position);
            }
            PepXmlDecimal mass = decimal("mass");
            Optional<PepXmlDecimal> staticDelta = optionalDecimal("static");
            Optional<PepXmlDecimal> variableDelta = optionalDecimal("variable");
            if (staticDelta.isEmpty() && variableDelta.isEmpty()) {
                throw refused(
                        "has a mod_aminoacid_mass at position "
                                + position
                                + " with neither a static nor a variable delta");
            }
            Optional<String> source = Optional.ofNullable(reader.getAttributeValue(null, "source"));
            Optional<String> id = Optional.ofNullable(reader.getAttributeValue(null, "id"));
            boolean peff = source.isPresent() && "peff".equals(source.get());
            if (source.isPresent() && !peff && !"param".equals(source.get())) {
                throw refused(
                        "has a mod_aminoacid_mass at position "
                                + position
                                + " with source=\""
                                + source.get()
                                + "\"; Comet writes only param and peff");
            }
            if (peff != id.isPresent()) {
                throw refused(
                        "has a mod_aminoacid_mass at position "
                                + position
                                + (peff
                                        ? " from a PEFF source with no id"
                                        : " with an id but no" + " PEFF source"));
            }
            hit.residues.add(
                    new PepXmlResidueModification(
                            (int) position,
                            hit.peptide.charAt((int) position - 1),
                            mass,
                            staticDelta,
                            variableDelta,
                            source,
                            id));
        }

        private void sequenceVariant(String element) {
            List<PepXmlNameValue> attributes = new ArrayList<>();
            for (int index = 0; index < reader.getAttributeCount(); index++) {
                attributes.add(
                        new PepXmlNameValue(
                                reader.getAttributeLocalName(index),
                                reader.getAttributeValue(index)));
            }
            hit.variants.add(new PepXmlSequenceVariant(element, attributes));
        }

        /** Refuses an attribute outside {@code allowed}, or any attribute in a namespace. */
        private void closedAttributes(Set<String> allowed) throws CometOutputException {
            for (int index = 0; index < reader.getAttributeCount(); index++) {
                String name = reader.getAttributeLocalName(index);
                String namespace = Objects.toString(reader.getAttributeNamespace(index), "");
                if (!namespace.isEmpty() || !allowed.contains(name)) {
                    throw refused(
                            "has "
                                    + article(reader.getLocalName())
                                    + " with an attribute {"
                                    + namespace
                                    + "}"
                                    + name
                                    + " that Comet does not write; it could change what the mass"
                                    + " means");
                }
            }
        }

        /** An element's name with its indefinite article: "an alternative_protein". */
        private static String article(String element) {
            return ("aeiou".indexOf(element.charAt(0)) >= 0 ? "an " : "a ") + element;
        }

        private String attribute(String name, String absent) {
            return Objects.toString(reader.getAttributeValue(null, name), absent);
        }

        private String required(String name) throws CometOutputException {
            String value = reader.getAttributeValue(null, name);
            if (value == null) {
                throw refused(
                        "has "
                                + article(reader.getLocalName())
                                + " with no "
                                + name
                                + " attribute");
            }
            return value;
        }

        private long integer(String name, long max) throws CometOutputException {
            String text = required(name);
            long value = canonical(text, max);
            if (value < 0) {
                throw refused(
                        "has "
                                + name
                                + "=\""
                                + text
                                + "\", which is not a canonical integer from 0 to "
                                + max);
            }
            return value;
        }

        private PepXmlDecimal decimal(String name) throws CometOutputException {
            return checkedDecimal(name, required(name));
        }

        private Optional<PepXmlDecimal> optionalDecimal(String name) throws CometOutputException {
            String text = reader.getAttributeValue(null, name);
            return text == null ? Optional.empty() : Optional.of(checkedDecimal(name, text));
        }

        private PepXmlDecimal checkedDecimal(String name, String text) throws CometOutputException {
            if (!PepXmlDecimal.isPlain(text)) {
                throw refused(
                        "has " + name + "=\"" + text + "\", which is not a plain decimal number");
            }
            return new PepXmlDecimal(text);
        }

        private boolean yesNo(String name) throws CometOutputException {
            String text = required(name);
            if ("Y".equals(text)) {
                return true;
            }
            if ("N".equals(text)) {
                return false;
            }
            throw refused("has " + name + "=\"" + text + "\", which is neither Y nor N");
        }

        /** A message naming the file, where in it, and the query and hit being read. */
        private CometOutputException refused(String problem) {
            StringBuilder message = new StringBuilder("the pepXML file ").append(file);
            int line = reader.getLocation().getLineNumber();
            if (line > 0) {
                // At the end of the document the parser has no line to report.
                message.append(" line ").append(line);
            }
            if (queryContext != null) {
                message.append(queryContext);
            }
            if (hit != null) {
                message.append(hit.context);
            }
            return new CometOutputException(
                    file, message.append(' ').append(problem).toString(), null);
        }
    }

    /** A search hit while its children are read. */
    private static final class HitInProgress {

        private final int position;

        private final String context;

        private int hitRank;

        private String peptide;

        private String prev;

        private String next;

        private String protein;

        private long numTotProteins;

        private PepXmlDecimal calcNeutralPepMass;

        private PepXmlDecimal massDiff;

        private final List<String> alternatives = new ArrayList<>();

        private final List<PepXmlNameValue> scores = new ArrayList<>();

        private final Set<String> scoreNames = new HashSet<>();

        private String modifiedPeptide;

        private Optional<PepXmlDecimal> ntermMass = Optional.empty();

        private Optional<PepXmlDecimal> ctermMass = Optional.empty();

        private final List<PepXmlResidueModification> residues = new ArrayList<>();

        private final Set<Integer> modifiedPositions = new HashSet<>();

        private final List<PepXmlSequenceVariant> variants = new ArrayList<>();

        HitInProgress(int position, String context) {
            this.position = position;
            this.context = context;
        }

        PepXmlSearchHit finish() {
            Optional<PepXmlModificationInfo> info =
                    modifiedPeptide == null
                            ? Optional.empty()
                            : Optional.of(
                                    new PepXmlModificationInfo(
                                            modifiedPeptide,
                                            ntermMass,
                                            ctermMass,
                                            residues,
                                            variants));
            return new PepXmlSearchHit(
                    position,
                    hitRank,
                    peptide,
                    prev,
                    next,
                    protein,
                    alternatives,
                    numTotProteins,
                    calcNeutralPepMass,
                    massDiff,
                    scores,
                    info);
        }
    }
}
