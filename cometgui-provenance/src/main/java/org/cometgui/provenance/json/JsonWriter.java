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

package org.cometgui.provenance.json;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.cometgui.domain.secrets.SecretRedactor;

/**
 * Builds one canonical JSON document, in the caller's field order, with every string value redacted
 * on the way through.
 *
 * <p>See the package documentation for why this project writes its own JSON and what "canonical"
 * means here. This class is the whole of it: a small state machine over a {@link StringBuilder}
 * that knows how to indent, when to write a comma, how to escape a string and how to render an
 * integer.
 *
 * <p><strong>The redactor is a constructor argument and there is no overload without one.</strong>
 * That is the point of applying redaction here rather than at the call sites. {@code R-SEC-03}
 * requires that no secret reaches an exported report, and phase 04's exit gate item 6 states it as
 * a property that can be greped for; a design in which each caller remembers to redact is a design
 * in which one caller eventually does not, and the caller that forgets is the field somebody adds
 * next year. Every string that goes into a document goes through {@link #value(String)}, and {@link
 * #value(String)} calls {@link SecretRedactor#redactText(String)}. There is no way to write a
 * string value that skips it, and a new field on the manifest therefore cannot open a new leak
 * path. Callers that know more than the text rules do -- that {@code --password} makes the
 * <em>next</em> argument a credential, that a variable called {@code GITHUB_TOKEN} is one whatever
 * its value looks like -- still redact positionally first, through {@link
 * SecretRedactor#redactArgv} and {@link SecretRedactor#redactEnvironment}; redaction is idempotent,
 * so passing an already-redacted string through again is free.
 *
 * <p><strong>Names are not redacted.</strong> A name is part of the schema, is chosen by this
 * repository rather than supplied by a run, and a reader that met {@code "[REDACTED]": 1} could not
 * do anything sensible with it. Names are escaped, like any JSON string, but never rewritten.
 *
 * <p><strong>Layout, pinned so that a hand-typed expected document is possible.</strong> Members
 * and elements go one per line, indented two spaces per level, separated by {@code ,} at the end of
 * the preceding line. A name is followed by {@code ": "} -- colon, one space. An empty object is
 * {@code {}} and an empty array is {@code []}, on one line, because a two-line empty container is
 * noise a diff has to step over. The line terminator is always {@code \n}: {@link
 * System#lineSeparator()} would make the document, and therefore its checksum, depend on the
 * machine that wrote it.
 *
 * <p><strong>Misuse throws rather than producing a document that is not JSON.</strong> A name
 * outside an object, a value with no name inside one, a mismatched {@code end}, a second root value
 * or a write after {@link #finish()} are all programming errors, and every one of them would
 * otherwise emit a plausible-looking file that no parser accepts. They fail loudly, at the call
 * that made the mistake.
 *
 * <p><strong>Not thread-safe, and single-use.</strong> One instance builds one document; {@link
 * #finish()} closes it. Two documents need two instances, which costs a {@link StringBuilder}.
 */
public final class JsonWriter {

    /** Lower-case hexadecimal digits for {@code \\u00XX} escapes, so no formatter is needed. */
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    /** One level of indentation: two spaces, fixed. */
    private static final String INDENT = "  ";

    /** The highest character that must be escaped as {@code \\u00XX}; {@code U+0020} is a space. */
    private static final char LAST_CONTROL_CHARACTER = 0x1f;

    /** The one rule set, applied to every string value. Never null; see the class documentation. */
    private final SecretRedactor redactor;

    /** The document so far. */
    private final StringBuilder out = new StringBuilder(4096);

    /** The containers currently open, innermost first. Its size is the current depth. */
    private final Deque<Frame> open = new ArrayDeque<>();

    /** True between a {@link #name(String)} and the value that belongs to it. */
    private boolean afterName;

    /** True once the document's single root value has been started. */
    private boolean rootStarted;

    /** True once {@link #finish()} has run; nothing may be written afterwards. */
    private boolean finished;

    /**
     * Use {@link #redactingWith(SecretRedactor)}: a writer without a redactor is the leak this
     * class exists to prevent, so there is no constructor that permits one.
     *
     * @param redactor the rule set to apply to every string value
     */
    private JsonWriter(SecretRedactor redactor) {
        this.redactor = redactor;
    }

    /**
     * Opens a document whose every string value passes through the given rule set.
     *
     * @param redactor the project's secret rule set; {@link SecretRedactor#patternsOnly()} is the
     *     weakest permitted argument, not an opt-out, because the pattern rules still run
     * @return a fresh writer with nothing written yet
     * @throws NullPointerException if {@code redactor} is {@code null}
     */
    public static JsonWriter redactingWith(SecretRedactor redactor) {
        return new JsonWriter(Objects.requireNonNull(redactor, "redactor"));
    }

    /**
     * Starts an object.
     *
     * @return this writer
     * @throws IllegalStateException if the document is finished, if a root value has already been
     *     written, or if this would be a value inside an object with no name before it
     */
    public JsonWriter beginObject() {
        beforeValue();
        out.append('{');
        open.push(new Frame(true));
        return this;
    }

    /**
     * Ends the innermost object, writing {@code {}} if nothing was put in it.
     *
     * @return this writer
     * @throws IllegalStateException if the document is finished, if a name is waiting for its
     *     value, if no container is open, or if the innermost open container is an array
     */
    public JsonWriter endObject() {
        return end(true, '}');
    }

    /**
     * Starts an array.
     *
     * @return this writer
     * @throws IllegalStateException if the document is finished, if a root value has already been
     *     written, or if this would be a value inside an object with no name before it
     */
    public JsonWriter beginArray() {
        beforeValue();
        out.append('[');
        open.push(new Frame(false));
        return this;
    }

    /**
     * Ends the innermost array, writing {@code []} if nothing was put in it.
     *
     * @return this writer
     * @throws IllegalStateException if the document is finished, if a name is waiting for its
     *     value, if no container is open, or if the innermost open container is an object
     */
    public JsonWriter endArray() {
        return end(false, ']');
    }

    /**
     * Writes a member name, which is escaped but never redacted.
     *
     * @param name the member name, exactly as it must appear in the document
     * @return this writer
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalStateException if the document is finished, if the innermost open container is
     *     not an object, or if the previous name has not been given a value
     */
    public JsonWriter name(String name) {
        Objects.requireNonNull(name, "name");
        requireWritable();
        requireNoDanglingName();
        Frame frame = open.peek();
        if (frame == null || !frame.isObject()) {
            throw new IllegalStateException(
                    "a name may only be written inside an object, and \"" + name + "\" was not");
        }
        separate(frame);
        out.append('"');
        escapeInto(name);
        out.append("\": ");
        afterName = true;
        return this;
    }

    /**
     * Writes a string value, <strong>after passing it through the secret rule set</strong> and then
     * escaping it.
     *
     * <p>Redaction runs first and escaping second, and the order is load-bearing: the rules match
     * credential URLs, {@code name=value} assignments and bearer headers as they appear in the real
     * text, and would not recognise them once every quote in them had become {@code \\"}.
     *
     * @param value the text to write
     * @return this writer
     * @throws NullPointerException if {@code value} is {@code null}; use {@link #nullValue()} for a
     *     JSON null, so that "absent" is always a deliberate choice at the call site
     * @throws IllegalStateException if the document is finished, if a root value has already been
     *     written, or if this would be a value inside an object with no name before it
     */
    public JsonWriter value(String value) {
        Objects.requireNonNull(value, "value");
        beforeValue();
        out.append('"');
        escapeInto(redactor.redactText(value));
        out.append('"');
        return this;
    }

    /**
     * Writes an integer value exactly, with no locale anywhere in the path.
     *
     * <p>{@link Long#toString(long)} and nothing else: it produces ASCII digits and an ASCII minus
     * sign whatever {@link java.util.Locale#getDefault()} says, where {@code String.format("%,d",
     * n)} produces grouped digits under {@code de-DE} and Thai digits under {@code
     * th-TH-u-nu-thai}. An {@code int} widens to {@code long} losslessly, so there is one method
     * rather than two and both types render exactly.
     *
     * @param value the number to write
     * @return this writer
     * @throws IllegalStateException if the document is finished, if a root value has already been
     *     written, or if this would be a value inside an object with no name before it
     */
    public JsonWriter value(long value) {
        beforeValue();
        out.append(Long.toString(value));
        return this;
    }

    /**
     * Writes {@code true} or {@code false}.
     *
     * @param value the flag to write
     * @return this writer
     * @throws IllegalStateException if the document is finished, if a root value has already been
     *     written, or if this would be a value inside an object with no name before it
     */
    public JsonWriter value(boolean value) {
        beforeValue();
        out.append(value ? "true" : "false");
        return this;
    }

    /**
     * Writes {@code null}.
     *
     * @return this writer
     * @throws IllegalStateException if the document is finished, if a root value has already been
     *     written, or if this would be a value inside an object with no name before it
     */
    public JsonWriter nullValue() {
        beforeValue();
        out.append("null");
        return this;
    }

    /**
     * Writes a map as an object whose members are in <strong>ascending key order</strong>.
     *
     * <p>Sorted here, in the writer, rather than trusted from the caller. A settings map or a
     * process environment arrives as whatever the JVM built, and {@link java.util.Map#copyOf}
     * promises nothing about iteration order; two identical runs must still produce byte-identical
     * documents. The ordering is {@link String}'s natural, code-point ordering -- not a {@link
     * java.text.Collator}, which would sort differently in Sweden than in Germany and would put a
     * locale back into the format {@code R-PROV-04} exists to keep it out of.
     *
     * @param entries the members to write; may be empty, which produces {@code {}}
     * @return this writer
     * @throws NullPointerException if {@code entries} is {@code null}, or if it contains a null key
     *     or a null value
     * @throws IllegalStateException if the document is finished, if a root value has already been
     *     written, or if this would be a value inside an object with no name before it
     */
    public JsonWriter sortedObject(Map<String, String> entries) {
        Objects.requireNonNull(entries, "entries");
        beginObject();
        for (Map.Entry<String, String> entry : new TreeMap<>(entries).entrySet()) {
            name(entry.getKey()).value(entry.getValue());
        }
        return endObject();
    }

    /**
     * Writes a collection of strings as an array, <strong>in the collection's own iteration
     * order</strong>.
     *
     * <p>Deliberately not sorted, where {@link #sortedObject(Map)} is. An array's order carries
     * meaning that must survive the round trip: an argument array reordered is a different command,
     * and a list of warnings reordered is a different story about the run. Making the order
     * deterministic is therefore the caller's job, done by holding the values in a type that has an
     * order -- which is why every collection on the manifest records is either a {@link
     * java.util.List} or a {@link java.util.SortedSet}.
     *
     * @param values the elements to write; may be empty, which produces {@code []}
     * @return this writer
     * @throws NullPointerException if {@code values} is {@code null} or contains a null element
     * @throws IllegalStateException if the document is finished, if a root value has already been
     *     written, or if this would be a value inside an object with no name before it
     */
    public JsonWriter arrayOfStrings(Collection<String> values) {
        Objects.requireNonNull(values, "values");
        beginArray();
        for (String element : values) {
            value(element);
        }
        return endArray();
    }

    /**
     * Closes the document and returns it, with exactly one trailing newline.
     *
     * <p>The trailing newline is part of the format, not decoration: it is what makes {@code
     * provenance.json} a well-formed POSIX text file, so that {@code diff}, {@code wc -l} and every
     * line-oriented tool a scientist reaches for behave, and so that a reader can pin the whole
     * document as a literal without an invisible difference at the end.
     *
     * @return the finished document
     * @throws IllegalStateException if the document is already finished, if a name is waiting for
     *     its value, if any container is still open, or if no value was ever written
     */
    public String finish() {
        requireWritable();
        requireNoDanglingName();
        if (!open.isEmpty()) {
            throw new IllegalStateException(
                    "the document still has " + open.size() + " unclosed container(s)");
        }
        if (!rootStarted) {
            throw new IllegalStateException("the document has no root value");
        }
        finished = true;
        return out.append('\n').toString();
    }

    /**
     * Describes the writer without disclosing a character of the document it is building.
     *
     * <p>The generated {@code toString} of a class holding a {@link StringBuilder} would be the
     * document itself, and this document is precisely the one that may contain a credential right
     * up until {@link #value(String)} has cleaned it. What is useful in a log line or an exception
     * message is how far the writer has got, which is what this prints.
     *
     * @return a description safe to put in a log line or an exception message
     */
    @Override
    public String toString() {
        return "JsonWriter[depth="
                + open.size()
                + ", characters="
                + out.length()
                + ", finished="
                + finished
                + "]";
    }

    /**
     * Ends the innermost container, checking that it is the kind the caller thinks it is.
     *
     * @param object {@code true} to end an object, {@code false} to end an array
     * @param bracket the closing bracket to write
     * @return this writer
     */
    private JsonWriter end(boolean object, char bracket) {
        requireWritable();
        requireNoDanglingName();
        Frame frame = open.peek();
        if (frame == null) {
            throw new IllegalStateException(
                    "there is no open container to close with '" + bracket + "'");
        }
        if (frame.isObject() != object) {
            throw new IllegalStateException(
                    "the innermost open container is "
                            + (frame.isObject() ? "an object" : "an array")
                            + ", which cannot be closed with '"
                            + bracket
                            + "'");
        }
        open.pop();
        if (!frame.isEmpty()) {
            out.append('\n');
            indent(open.size());
        }
        out.append(bracket);
        return this;
    }

    /**
     * Writes whatever has to come before a value: the comma, the newline and the indentation, or
     * nothing at all when the value belongs to a name that has just been written.
     */
    private void beforeValue() {
        requireWritable();
        if (afterName) {
            afterName = false;
            return;
        }
        Frame frame = open.peek();
        if (frame == null) {
            if (rootStarted) {
                throw new IllegalStateException("a JSON document has exactly one root value");
            }
            rootStarted = true;
            return;
        }
        if (frame.isObject()) {
            throw new IllegalStateException("a value inside an object must be preceded by a name");
        }
        separate(frame);
    }

    /**
     * Writes the separator before a member or an element of an already-open container, and records
     * that the container is no longer empty.
     *
     * @param frame the container the item is going into
     */
    private void separate(Frame frame) {
        if (!frame.isEmpty()) {
            out.append(',');
        }
        frame.filled();
        out.append('\n');
        indent(open.size());
    }

    /**
     * Writes one level of indentation per open container.
     *
     * @param depth how many levels to write
     */
    private void indent(int depth) {
        for (int level = 0; level < depth; level++) {
            out.append(INDENT);
        }
    }

    /**
     * Appends a string as the inside of a JSON string literal, escaping exactly what JSON requires
     * and nothing else.
     *
     * <p>The quote and the backslash have to be escaped or the literal ends early; the five control
     * characters with short forms get them because they are the ones that appear in real captured
     * output; everything else below {@code U+0020} becomes {@code \\u00XX}, because the JSON
     * grammar forbids a raw control character inside a string. <strong>Everything at or above
     * {@code U+0020} is written as itself</strong>, including every non-ASCII character and both
     * halves of a surrogate pair, and is encoded as UTF-8 when the document is written to a file. A
     * path containing {@code é} or an emoji therefore reads as that path, which is the whole reason
     * a scientist can check a provenance record against their own disk.
     *
     * @param text the string to escape into the document
     */
    private void escapeInto(String text) {
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> appendUnescapedOrHex(character);
            }
        }
    }

    /**
     * Appends one character that has no short escape: as itself, or as {@code \\u00XX} if it is a
     * control character.
     *
     * @param character the character to append
     */
    private void appendUnescapedOrHex(char character) {
        if (character > LAST_CONTROL_CHARACTER) {
            out.append(character);
            return;
        }
        out.append("\\u00")
                .append(HEX_DIGITS[(character >> 4) & 0xf])
                .append(HEX_DIGITS[character & 0xf]);
    }

    /** Rejects any write after the document has been finished. */
    private void requireWritable() {
        if (finished) {
            throw new IllegalStateException("the document is finished and cannot be added to");
        }
    }

    /** Rejects a name that was written and never given a value. */
    private void requireNoDanglingName() {
        if (afterName) {
            throw new IllegalStateException("the last name written has no value");
        }
    }

    /**
     * One open container: which kind it is, and whether anything has been put in it yet.
     *
     * <p>Both facts are needed at the moment the container closes -- the kind to check the caller's
     * {@code end} against and to choose the bracket, the emptiness to decide between {@code {}} and
     * a multi-line object -- and they are per level rather than global, because closing a nested
     * container must leave its parent still knowing it is no longer empty.
     */
    private static final class Frame {

        /** True for an object, false for an array. */
        private final boolean object;

        /** True until the first member or element is written. */
        private boolean empty = true;

        /**
         * @param object true for an object, false for an array
         */
        Frame(boolean object) {
            this.object = object;
        }

        /**
         * @return true if this is an object, false if it is an array
         */
        boolean isObject() {
            return object;
        }

        /**
         * @return true while nothing has been written into this container
         */
        boolean isEmpty() {
            return empty;
        }

        /** Records that something has been written into this container. */
        void filled() {
            empty = false;
        }
    }
}
