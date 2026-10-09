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

package org.cometgui.ui.viewmodel.results;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.Executor;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import org.cometgui.results.filtering.DisplayFilters;
import org.cometgui.results.filtering.FilterCounts;
import org.cometgui.results.filtering.QValueFilter;
import org.cometgui.results.filtering.store.Category;
import org.cometgui.results.filtering.store.ResultPage;
import org.cometgui.results.filtering.store.ResultQuery;
import org.cometgui.results.filtering.store.ResultSort;
import org.cometgui.results.filtering.store.ResultStore;
import org.cometgui.results.filtering.store.RowKey;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.results.parser.ResultRow;
import org.cometgui.ui.viewmodel.NonNullProperty;

/**
 * One results table -- the PSM or the peptide table, target or decoy -- as a <strong>page</strong>
 * of rows over an open {@link ResultStore} ({@code R-RES-03}, design decision P10-9).
 *
 * <h2>Never every row</h2>
 *
 * <p>The table holds one {@link TablePage}: at most {@link #PAGE_SIZE} rows, fetched from the store
 * by a {@link ResultQuery} whose limit is {@link #PAGE_SIZE}. Nothing here holds, lists or counts
 * the table's rows itself: the counts, the number of matching rows and every row come from the
 * store, which decides where a row falls by the one q-value predicate (design decision P10-1).
 *
 * <h2>Two threads</h2>
 *
 * <p>The store reads files, so it is asked only on the {@code background} executor; every answer is
 * applied on the {@code ui} executor. Each request carries a generation number and an answer to an
 * older request -- a slow page of the previous filter, the previous table -- is dropped, so what is
 * shown always answers the newest question.
 *
 * <h2>What re-queries the store, and what does not</h2>
 *
 * <p>A change of the table's q-value filter (the shared {@link DisplayFiltersViewModel}'s PSM
 * filter for a PSM table, its peptide filter for a peptide table -- the other filter is ignored,
 * {@code AC-RES-03}), of the category, of the sort and an applied text filter each ask the store
 * for the counts and one page; nothing else happens -- no process, no rerun, no file written
 * (design decision P10-10). The text filter is <strong>applied on request</strong> ({@link
 * #applyText()}, the view's Enter key), never on each keystroke: a new text costs the disk store a
 * pass over the table, about 1.5 s at a million rows. Paging asks for one page. Column visibility
 * and selection ask for nothing.
 *
 * <h2>Selection, by key, stable across every change</h2>
 *
 * <p>The selection is a set of {@link RowKey}s -- a row's line in the raw file -- never an index,
 * so it means the same rows whatever the filter, category, text, sort or page. Rows are selected
 * from the page shown ({@link #select}, {@link #toggle}); the most recently selected is the
 * <em>anchor</em>. The rule, after a change of the filter, category, text or sort:
 *
 * <ul>
 *   <li>the selection is kept, every key of it, whatever the change;
 *   <li>if the new query matches the anchor, the view moves to the page holding it ({@link
 *       ResultStore#positionOf}) and it is shown selected there;
 *   <li>if it does not, the first page is shown, the anchor stays selected but hidden, and {@link
 *       #selectionStatusProperty()} says so; a later change under which it matches again moves to
 *       its page once more;
 *   <li>with no selection, the first page is shown.
 * </ul>
 *
 * <p>Paging moves nothing but the page; the selection is kept and shown wherever its rows are on
 * the page. Only {@link #select}, {@link #toggle}, {@link #clearSelection()} and showing another
 * table change it.
 *
 * <h2>Copy</h2>
 *
 * <p>{@link #copy()} gives the selected rows <em>shown on the current page</em>, in the page's
 * order, as tab-separated text: a header line of the shown columns' headings, then each row's shown
 * cells exactly as displayed. A selected row that is not on the page -- on another page, or hidden
 * by the filter -- is not copied, and the outcome says how many.
 *
 * <h2>Columns</h2>
 *
 * <p>Any column can be hidden, except the source-file column of a run with more than one spectrum
 * file: there it is mandatory ({@code R-RES}, PSM table), and hiding it is refused. Hidden columns
 * are a preference that outlives the table shown.
 */
public final class ResultTableViewModel {

    /** The most rows the table holds at once: one page. */
    public static final int PAGE_SIZE = ResultQuery.DEFAULT_PAGE_SIZE;

    /** The status while the store is being asked. */
    public static final String READING = "Reading the table.";

    /** The status when the table is shown and nothing is pending. */
    public static final String READY = "";

    /** The selection status with nothing selected. */
    public static final String NOTHING_SELECTED = "No row is selected.";

    private static final long HIDDEN = -1;

    private static final long UNKNOWN = -2;

    private final DisplayFiltersViewModel filters;

    private final Executor background;

    private final Executor ui;

    // ---- state, on the interface thread ----

    private ResultStore store;

    private TableKind kind;

    private Map<String, String> sourceFiles = Map.of();

    private long generation;

    private ResultQuery query;

    private final Set<RowKey> selection = new LinkedHashSet<>();

    private RowKey anchor;

    /**
     * The anchor's position among the current query's matching rows; {@code HIDDEN} when the query
     * does not match it; {@code UNKNOWN} when it is not known -- the anchor became the anchor by
     * another row's deselection while it was not on the page.
     */
    private long anchorPosition = HIDDEN;

    private final Set<ResultsColumn> hidden = EnumSet.noneOf(ResultsColumn.class);

    // ---- published ----

    private final NonNullProperty<TablePage> page;

    private final NonNullProperty<TableCounts> counts;

    private final NonNullProperty<Category> category;

    private final NonNullProperty<ResultSort> sort;

    private final NonNullProperty<String> textDraft;

    private final NonNullProperty<String> appliedText;

    private final ReadOnlyBooleanWrapper textPending;

    private final NonNullProperty<List<ColumnState>> columns;

    private final NonNullProperty<String> columnStatus;

    private final NonNullProperty<List<RowKey>> selected;

    private final NonNullProperty<String> selectionStatus;

    private final NonNullProperty<String> status;

    private final ReadOnlyBooleanWrapper busy;

    /**
     * A table with nothing open.
     *
     * @param filters the interface's one display-filter state; a change of this table's filter
     *     re-queries it
     * @param background where the store is asked: never the interface thread
     * @param ui where every answer is applied: the interface thread
     */
    public ResultTableViewModel(DisplayFiltersViewModel filters, Executor background, Executor ui) {
        this.filters = Objects.requireNonNull(filters, "filters");
        this.background = Objects.requireNonNull(background, "background");
        this.ui = Objects.requireNonNull(ui, "ui");
        page = new NonNullProperty<>(this, "page", TablePage.NONE);
        counts = new NonNullProperty<>(this, "counts", TableCounts.NONE);
        category = new NonNullProperty<>(this, "category", Category.PASSING);
        sort = new NonNullProperty<>(this, "sort", ResultSort.FILE_ORDER);
        textDraft = new NonNullProperty<>(this, "textDraft", "");
        appliedText = new NonNullProperty<>(this, "appliedText", "");
        textPending = new ReadOnlyBooleanWrapper(this, "textPending", false);
        columns = new NonNullProperty<>(this, "columns", List.of());
        columnStatus = new NonNullProperty<>(this, "columnStatus", "");
        selected = new NonNullProperty<>(this, "selected", List.of());
        selectionStatus = new NonNullProperty<>(this, "selectionStatus", NOTHING_SELECTED);
        status = new NonNullProperty<>(this, "status", TablePage.NONE.text());
        busy = new ReadOnlyBooleanWrapper(this, "busy", false);
        filters.displayFiltersProperty()
                .addListener((observable, before, after) -> filtersChanged(after));
    }

    // =========================================================================== actions ====

    /**
     * Shows a table: its first page of passing rows in file order, no text filter, nothing
     * selected. The store stays the caller's to close; this table stops asking it when another is
     * shown or {@link #clear()} is called.
     *
     * @param table the open store
     * @param spectrumFiles the run's spectrum files: each Comet {@code -N} base to its display name
     */
    public void show(ResultStore table, Map<String, String> spectrumFiles) {
        store = Objects.requireNonNull(table, "table");
        kind = table.kind();
        sourceFiles = Map.copyOf(spectrumFiles);
        query =
                new ResultQuery(
                        filterOf(filters.filters()),
                        Category.PASSING,
                        "",
                        ResultSort.FILE_ORDER,
                        0,
                        PAGE_SIZE);
        selection.clear();
        anchor = null;
        anchorPosition = HIDDEN;
        textDraft.set("");
        columnStatus.set("");
        publishQuery();
        publishColumns();
        page.set(TablePage.NONE);
        counts.set(TableCounts.NONE);
        publishSelection();
        fetch(true, 0);
    }

    /** Shows no table, and stops asking the last one. */
    public void clear() {
        generation++;
        store = null;
        kind = null;
        query = null;
        selection.clear();
        anchor = null;
        anchorPosition = HIDDEN;
        textDraft.set("");
        appliedText.set("");
        textPending.set(false);
        category.set(Category.PASSING);
        sort.set(ResultSort.FILE_ORDER);
        columns.set(List.of());
        columnStatus.set("");
        page.set(TablePage.NONE);
        counts.set(TableCounts.NONE);
        status.set(TablePage.NONE.text());
        busy.set(false);
        publishSelection();
    }

    /**
     * Shows the rows of a category: passing (the default), unknown q-value, failing, or all.
     *
     * @param newCategory the category
     */
    public void setCategory(Category newCategory) {
        Objects.requireNonNull(newCategory, "newCategory");
        if (query == null || query.category() == newCategory) {
            return;
        }
        query = query.withCategory(newCategory);
        publishQuery();
        fetch(true, 0);
    }

    /**
     * Sorts by a column, as a column heading's click does: ascending first, then descending, then
     * back to file order.
     *
     * @param column the column
     * @throws IllegalArgumentException if the table shown has no such column
     */
    public void sortBy(ResultsColumn column) {
        Objects.requireNonNull(column, "column");
        if (query == null) {
            return;
        }
        if (!column.isIn(kind)) {
            throw new IllegalArgumentException(
                    "the " + TableCounts.rowsName(kind) + " table has no " + column.label());
        }
        ResultSort now = query.sort();
        ResultSort next;
        if (now.column() != column.sortColumn()) {
            next = ResultSort.ascending(column.sortColumn());
        } else if (now.direction() == ResultSort.Direction.ASCENDING) {
            next = ResultSort.descending(column.sortColumn());
        } else {
            next = ResultSort.FILE_ORDER;
        }
        query = query.withSort(next);
        publishQuery();
        fetch(true, 0);
    }

    /**
     * Holds text in the text filter's field without applying it: typing re-queries nothing.
     *
     * @param text the field's text
     */
    public void editText(String text) {
        textDraft.set(Objects.requireNonNull(text, "text"));
        textPending.set(query != null && !text.strip().equals(query.text()));
    }

    /**
     * Applies the text filter's field: rows whose {@code PSMId}, peptide or a protein contains it,
     * ignoring case.
     *
     * @return {@code true} if the table was asked again; {@code false} if the text is the one
     *     applied, or no table is shown
     */
    public boolean applyText() {
        if (query == null || textDraft.get().strip().equals(query.text())) {
            return false;
        }
        query = query.withText(textDraft.get());
        publishQuery();
        fetch(true, 0);
        return true;
    }

    /**
     * Empties the text filter's field and applies it.
     *
     * @return as {@link #applyText()}
     */
    public boolean clearText() {
        textDraft.set("");
        return applyText();
    }

    /** Shows the next page, if there is one. */
    public void nextPage() {
        goToPage(page.get().pageNumber() + 1);
    }

    /** Shows the previous page, if there is one. */
    public void previousPage() {
        goToPage(page.get().pageNumber() - 1);
    }

    /** Shows the first page. */
    public void firstPage() {
        goToPage(1);
    }

    /** Shows the last page. */
    public void lastPage() {
        goToPage(page.get().pageCount());
    }

    /**
     * Shows a page; a number outside the pages there are is brought within them.
     *
     * @param number the page's number, from 1
     */
    public void goToPage(long number) {
        if (query == null) {
            return;
        }
        long last = Math.max(1, page.get().pageCount());
        long wanted = Math.max(1, Math.min(number, last));
        long offset = (wanted - 1) * PAGE_SIZE;
        if (offset == query.offset() && !busy.get()) {
            return;
        }
        fetch(false, offset);
    }

    /**
     * Selects one row of the page shown, and nothing else.
     *
     * @param key the row's key
     * @throws IllegalArgumentException if no row of the page shown has that key
     */
    public void select(RowKey key) {
        long position = positionOnPage(key);
        selection.clear();
        selection.add(key);
        anchor = key;
        anchorPosition = position;
        publishSelection();
    }

    /**
     * Adds a row of the page shown to the selection, or removes it if it is selected -- as a
     * control-click does.
     *
     * @param key the row's key
     * @throws IllegalArgumentException if no row of the page shown has that key
     */
    public void toggle(RowKey key) {
        long position = positionOnPage(key);
        if (selection.remove(key)) {
            if (key.equals(anchor)) {
                anchor = null;
                anchorPosition = HIDDEN;
                for (RowKey left : selection) {
                    anchor = left;
                }
                if (anchor != null) {
                    anchorPosition = positionOnPageOrUnknown(anchor);
                }
            }
        } else {
            selection.add(key);
            anchor = key;
            anchorPosition = position;
        }
        publishSelection();
    }

    /** Selects nothing. */
    public void clearSelection() {
        selection.clear();
        anchor = null;
        anchorPosition = HIDDEN;
        publishSelection();
    }

    /**
     * Shows or hides a column.
     *
     * @param column the column
     * @param visible whether to show it
     * @return {@code false} if hiding was refused: the source-file column of a run with more than
     *     one spectrum file cannot be hidden
     */
    public boolean setColumnVisible(ResultsColumn column, boolean visible) {
        Objects.requireNonNull(column, "column");
        if (!visible && mandatory(column)) {
            columnStatus.set(
                    "The source file column cannot be hidden: this run has "
                            + sourceFiles.size()
                            + " spectrum files, and without it a PSM's spectrum file is not"
                            + " shown.");
            return false;
        }
        if (visible) {
            hidden.remove(column);
        } else {
            hidden.add(column);
        }
        columnStatus.set("");
        publishColumns();
        return true;
    }

    /**
     * The selected rows shown on the current page, as tab-separated text for the clipboard.
     *
     * @return the text and what it holds
     */
    public CopyOutcome copy() {
        if (selection.isEmpty()) {
            return new CopyOutcome("", 0, "Nothing was copied: no row is selected.");
        }
        List<ResultsColumn> shown = new ArrayList<>();
        for (ColumnState state : columns.get()) {
            if (state.visible()) {
                shown.add(state.column());
            }
        }
        StringBuilder text = new StringBuilder();
        List<String> headings = new ArrayList<>();
        for (ResultsColumn column : shown) {
            headings.add(column.label());
        }
        text.append(String.join("\t", headings)).append('\n');
        int copied = 0;
        for (ResultRowView row : page.get().rows()) {
            if (selection.contains(row.key())) {
                List<String> cells = new ArrayList<>();
                for (ResultsColumn column : shown) {
                    cells.add(row.cell(column));
                }
                text.append(String.join("\t", cells)).append('\n');
                copied++;
            }
        }
        int left = selection.size() - copied;
        if (copied == 0) {
            return new CopyOutcome(
                    "",
                    0,
                    "Nothing was copied: no selected row is shown on this page ("
                            + left
                            + (left == 1 ? " selected row is" : " selected rows are")
                            + " elsewhere).");
        }
        String message =
                "Copied "
                        + copied
                        + (copied == 1 ? " row" : " rows")
                        + " with "
                        + shown.size()
                        + " columns as tab-separated text."
                        + (left == 0
                                ? ""
                                : " "
                                        + left
                                        + (left == 1
                                                ? " selected row is not on this page and was"
                                                : " selected rows are not on this page and"
                                                        + " were")
                                        + " not copied.");
        return new CopyOutcome(text.toString(), copied, message);
    }

    // ========================================================================= published ====

    /**
     * The page shown: at most {@link #PAGE_SIZE} rows and where they stand.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<TablePage> pageProperty() {
        return page.getReadOnlyProperty();
    }

    /**
     * The page shown.
     *
     * @return the page
     */
    public TablePage page() {
        return page.get();
    }

    /**
     * The counts under the table's filter: total, passing, failing and unknown q-value.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<TableCounts> countsProperty() {
        return counts.getReadOnlyProperty();
    }

    /**
     * The counts under the table's filter.
     *
     * @return the counts
     */
    public TableCounts counts() {
        return counts.get();
    }

    /**
     * The category shown.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<Category> categoryProperty() {
        return category.getReadOnlyProperty();
    }

    /**
     * The order.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<ResultSort> sortProperty() {
        return sort.getReadOnlyProperty();
    }

    /**
     * The text filter's field.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> textDraftProperty() {
        return textDraft.getReadOnlyProperty();
    }

    /**
     * The text filter applied.
     *
     * @return the read-only property; empty for none
     */
    public ReadOnlyObjectProperty<String> appliedTextProperty() {
        return appliedText.getReadOnlyProperty();
    }

    /**
     * Whether the text filter's field holds text not yet applied.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty textPendingProperty() {
        return textPending.getReadOnlyProperty();
    }

    /**
     * The open table's columns, in display order, each shown or hidden.
     *
     * @return the read-only property; empty when no table is shown
     */
    public ReadOnlyObjectProperty<List<ColumnState>> columnsProperty() {
        return columns.getReadOnlyProperty();
    }

    /**
     * Why the last change of a column's visibility was refused.
     *
     * @return the read-only property; empty text when none was
     */
    public ReadOnlyObjectProperty<String> columnStatusProperty() {
        return columnStatus.getReadOnlyProperty();
    }

    /**
     * The selected rows' keys, in the order they were selected.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<List<RowKey>> selectedProperty() {
        return selected.getReadOnlyProperty();
    }

    /**
     * What is selected and where it is -- including a selected row the filter now hides.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> selectionStatusProperty() {
        return selectionStatus.getReadOnlyProperty();
    }

    /**
     * What the table is doing, or why it could not be read.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> statusProperty() {
        return status.getReadOnlyProperty();
    }

    /**
     * Whether the store is being asked.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty busyProperty() {
        return busy.getReadOnlyProperty();
    }

    // ========================================================================= internals ====

    private void filtersChanged(DisplayFilters now) {
        if (query == null) {
            return;
        }
        QValueFilter filter = filterOf(now);
        if (filter.equals(query.filter())) {
            return;
        }
        query = query.withFilter(filter);
        fetch(true, 0);
    }

    private QValueFilter filterOf(DisplayFilters now) {
        return kind.isPsms() ? now.psm() : now.peptide();
    }

    /** One answer from the store, made on the background executor. */
    private record Answer(
            List<ResultRowView> rows,
            long offset,
            long matching,
            FilterCounts counts,
            boolean followed,
            long anchorPosition,
            String failure) {}

    /**
     * Asks the store for a page on the background executor.
     *
     * @param follow whether the query changed: the anchor is looked up and its page shown
     * @param offset the page's offset when not following
     */
    private void fetch(boolean follow, long offset) {
        generation++;
        long mine = generation;
        ResultStore asked = store;
        ResultQuery question = query;
        RowKey following = follow ? anchor : null;
        Map<String, String> names = sourceFiles;
        busy.set(true);
        status.set(READING);
        background.execute(
                () -> {
                    Answer answer;
                    try {
                        long start = offset;
                        long found = HIDDEN;
                        if (following != null) {
                            OptionalLong position = asked.positionOf(following, question);
                            if (position.isPresent()) {
                                found = position.getAsLong();
                                start = found - found % PAGE_SIZE;
                            } else {
                                start = 0;
                            }
                        }
                        ResultPage answered = asked.query(question.withPage(start, PAGE_SIZE));
                        List<ResultRowView> rows = new ArrayList<>(answered.rows().size());
                        for (ResultRow row : answered.rows()) {
                            rows.add(ResultRowView.of(row, names));
                        }
                        answer =
                                new Answer(
                                        rows,
                                        answered.offset(),
                                        answered.matching(),
                                        answered.counts(),
                                        follow,
                                        found,
                                        null);
                    } catch (IOException | RuntimeException failed) {
                        answer =
                                new Answer(List.of(), 0, 0, null, follow, HIDDEN, describe(failed));
                    }
                    Answer answered = answer;
                    ui.execute(() -> answered(mine, question, answered));
                });
    }

    private void answered(long answeredGeneration, ResultQuery question, Answer answer) {
        if (answeredGeneration != generation) {
            return;
        }
        busy.set(false);
        if (answer.failure() != null) {
            // where the selected row stands is not known until the store answers again
            anchorPosition = UNKNOWN;
            page.set(TablePage.NONE);
            counts.set(TableCounts.unavailable(answer.failure()));
            status.set("The table could not be read: " + answer.failure());
            publishSelection();
            return;
        }
        query = question.withPage(answer.offset(), PAGE_SIZE);
        if (answer.followed()) {
            anchorPosition = answer.anchorPosition();
        }
        page.set(TablePage.of(answer.rows(), answer.offset(), answer.matching()));
        counts.set(TableCounts.of(kind, question.filter(), answer.counts()));
        status.set(READY);
        publishSelection();
    }

    private static String describe(Exception failed) {
        String message = failed.getMessage();
        return message == null || message.isBlank() ? failed.toString() : message;
    }

    private boolean mandatory(ResultsColumn column) {
        return column == ResultsColumn.SOURCE_FILE && sourceFiles.size() > 1;
    }

    private long positionOnPage(RowKey key) {
        Objects.requireNonNull(key, "key");
        long position = positionOnPageOrUnknown(key);
        if (position == UNKNOWN) {
            throw new IllegalArgumentException(
                    "no row of the page shown is on line " + key.line() + " of the table file");
        }
        return position;
    }

    private long positionOnPageOrUnknown(RowKey key) {
        TablePage shown = page.get();
        List<ResultRowView> rows = shown.rows();
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).key().equals(key)) {
                return shown.offset() + index;
            }
        }
        return UNKNOWN;
    }

    private void publishQuery() {
        category.set(query.category());
        sort.set(query.sort());
        appliedText.set(query.text());
        textPending.set(!textDraft.get().strip().equals(query.text()));
    }

    private void publishColumns() {
        List<ColumnState> states = new ArrayList<>();
        for (ResultsColumn column : ResultsColumn.of(kind)) {
            boolean required = mandatory(column);
            states.add(new ColumnState(column, required || !hidden.contains(column), !required));
        }
        columns.set(List.copyOf(states));
    }

    private void publishSelection() {
        selected.set(List.copyOf(selection));
        selectionStatus.set(selectionText());
    }

    private String selectionText() {
        if (selection.isEmpty()) {
            return NOTHING_SELECTED;
        }
        String how =
                selection.size() == 1
                        ? "1 row is selected"
                        : selection.size() + " rows are selected";
        if (anchorPosition == UNKNOWN) {
            return how + ".";
        }
        if (anchorPosition == HIDDEN) {
            return how
                    + ". The most recently selected, line "
                    + anchor.line()
                    + " of the table file, does not match the current q-value filter, category"
                    + " or text filter, so it is not shown. It stays selected, and is shown"
                    + " again when it matches.";
        }
        long matching = page.get().matching();
        long onPage = anchorPosition / PAGE_SIZE + 1;
        return how
                + (selection.size() == 1 ? ": row " : "; the most recently selected is row ")
                + (anchorPosition + 1)
                + " of "
                + matching
                + ", on page "
                + onPage
                + ".";
    }
}
