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

package org.cometgui.results.filtering.store;

/**
 * Why the disk-backed store did not trust an index or sort file it found, or refused a table.
 *
 * <p>An index file that fails any check is never used: it is rebuilt from the raw table, and the
 * reason is kept ({@code DiskResultStore#indexReport}). A store refuses to open, with a {@link
 * ResultIndexException} naming the reason, only when a freshly built index fails the same checks,
 * when the raw table changes while it is being indexed, or when the table is too large to index.
 */
public enum IndexProblem {

    /** There was no file. */
    ABSENT("there was no index file"),

    /** The file is shorter than its header, or its length disagrees with its row count. */
    TRUNCATED("the index file is truncated: its length does not match its header"),

    /** The file does not begin with the index's magic bytes. */
    NOT_AN_INDEX("the file is not a CometGUI result index"),

    /** The header's own checksum does not match: the header is damaged. */
    HEADER_DAMAGED("the index header's checksum does not match"),

    /** The file was written by another index format or for another record layout. */
    OTHER_FORMAT("the index file was written in another format"),

    /** The index describes another kind of table, or another sort. */
    OTHER_TABLE("the index file describes another table or sort"),

    /** The raw table's size differs from the size the index recorded. */
    RAW_SIZE_CHANGED("the raw table's size differs from the size recorded in the index"),

    /** The raw table's modification time differs from the one the index recorded. */
    RAW_TIME_CHANGED("the raw table's modification time differs from the one recorded"),

    /** The raw table's SHA-256 differs from the one the index recorded. */
    RAW_CONTENT_CHANGED("the raw table's SHA-256 differs from the one recorded in the index"),

    /** The body's checksum does not match: the rows recorded are damaged. */
    BODY_DAMAGED("the index body's checksum does not match"),

    /** A sort file is not a permutation of the table's rows: a row is missing or repeated. */
    NOT_A_PERMUTATION("the sort file does not hold every row exactly once"),

    /** The raw table changed while it was being indexed. */
    RAW_CHANGED_WHILE_INDEXING("the raw table changed while it was being indexed"),

    /**
     * The raw table changed after the store opened it, or no longer holds at a row's offset the row
     * its index recorded there: the store answers nothing more from that index.
     */
    RAW_CHANGED_SINCE_OPENED("the raw table changed after its index was opened"),

    /** The raw table holds more rows than one index can address. */
    TOO_MANY_ROWS("the raw table holds more rows than an index can address");

    private final String description;

    IndexProblem(String description) {
        this.description = description;
    }

    /**
     * The reason in words.
     *
     * @return for example {@code the raw table's SHA-256 differs from the one recorded in the
     *     index}
     */
    public String description() {
        return description;
    }
}
