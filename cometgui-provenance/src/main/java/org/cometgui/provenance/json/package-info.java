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
 * The canonical JSON this project writes, and the strict reader that accepts it and nothing else.
 *
 * <p>One byte sequence per model, chosen rather than inherited, and one grammar accepted on the way
 * back in.
 *
 * <p><strong>There is no JSON library here, and that is a decision rather than an
 * omission.</strong> The provenance record is the artefact the whole product exists to produce, so
 * its byte-level form has to be a property of this repository: a library's defaults -- its field
 * order, its escaping of non-ASCII, its number rendering, its line endings -- would become the
 * on-disk contract of {@code provenance.json} without anyone choosing them, and would move when the
 * library moved. A writer of a few hundred lines is also inside PIT's reach, which is where {@code
 * R-TEST-02} puts "checksum and provenance code".
 *
 * <p><strong>Everything here is deterministic, because a provenance record is compared.</strong>
 * Two runs that differ only in what a {@link java.util.HashMap} did with its keys must produce
 * byte-identical documents, or a diff of two records is unreadable and the document's own checksum
 * is not reproducible. Concretely: object members are written in the order the caller asks for and
 * that order is fixed by the caller's code, not by reflection; a map is written in ascending key
 * order under {@link String}'s natural ordering, never a {@link java.text.Collator}; indentation is
 * two spaces per level; the line terminator is {@code \n} on every platform, never {@code
 * System.lineSeparator()}; and the document ends with exactly one newline.
 *
 * <p><strong>The number path never sees a locale.</strong> Integers are rendered with {@link
 * Long#toString(long)} and nothing else. {@code String.format("%,d", 1234567890123L)} is {@code
 * 1,234,567,890,123} under {@code Locale.US}, {@code 1.234.567.890.123} under {@code
 * Locale.GERMANY} and a string of Thai digits under {@code th-TH-u-nu-thai}; {@code R-PROV-04}
 * exists because the JVM's default locale reaches serialisation, and this package is one of the
 * places it must not.
 *
 * <p><strong>Non-ASCII characters are emitted as themselves, in UTF-8.</strong> Proteomics paths
 * carry real Unicode -- accents, micro signs, the occasional emoji in a directory a scientist named
 * -- and a record whose reader has to decode {@code é} by hand is a worse record. Only the
 * characters JSON requires are escaped: the quote, the backslash and everything below {@code
 * U+0020}.
 *
 * <p><strong>Redaction happens inside the writer.</strong> {@link
 * org.cometgui.provenance.json.JsonWriter} takes a {@link
 * org.cometgui.domain.secrets.SecretRedactor} and has no constructor that omits one, so every
 * string <em>value</em> that reaches a document has passed through the project's single rule set.
 * That is the phase's design: adding a field to the manifest cannot open a leak path, because the
 * leak path was closed one level below the fields. Keys are not redacted -- a key is part of the
 * schema, is never user data, and redacting one would produce a document no reader could parse.
 *
 * <p><strong>Reading is the other half, and it is deliberately strict.</strong> {@link
 * org.cometgui.provenance.json.JsonReader} accepts the grammar {@link
 * org.cometgui.provenance.json.JsonWriter} emits and refuses every dialect around it -- trailing
 * commas, comments, single quotes, unquoted names, duplicate member names, {@code NaN}, an
 * exponent, an integer outside a {@code long}, a raw control character, an unpaired surrogate, a
 * byte-order mark, anything after the top-level value -- because a permissive parser accepts a
 * corrupted provenance record and hands back an object graph that looks like a run which never
 * happened. Nesting is bounded at {@link org.cometgui.provenance.json.JsonReader#MAX_DEPTH}, so a
 * hostile document is a parse error with a position rather than a {@link StackOverflowError}.
 * {@link org.cometgui.provenance.json.JsonValue} is the closed set of six shapes it returns, in
 * which JSON {@code null} is a value rather than an absence.
 *
 * <p><strong>A rejection names the rule and the position and quotes nothing from the
 * document.</strong> This is the reading half of the same rule redaction is the writing half of: a
 * parse failure is the one moment at which a document has certainly <em>not</em> been through
 * {@link org.cometgui.domain.secrets.SecretRedactor}, so a message echoing the offending text is
 * how a credential reaches a log, a bug report and an issue tracker. See {@link
 * org.cometgui.provenance.json.JsonParseException}.
 *
 * <p>Written by phase 04 (hashing and provenance core): the writer in work unit 7, the reader in
 * work unit 9.
 */
package org.cometgui.provenance.json;
