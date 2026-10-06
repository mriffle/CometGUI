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

package org.cometgui.workflow.state;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.cometgui.domain.ports.FileHashes;

/**
 * The current value of one {@link InputKind}, reduced to the SHA-256 {@link #digest()} a step
 * fingerprint contains.
 *
 * <p>Five shapes, one per {@link ValueType}. Each digest is a SHA-256, written as 64 lower-case
 * hexadecimal characters, over an encoding documented on its record. The encodings are ASCII
 * framing around UTF-8 text, with no locale-dependent formatting anywhere, so a digest can be
 * recomputed by hand with {@code printf ... | sha256sum}, and the tests do exactly that.
 *
 * <p>Files are never read here. A file contributes the SHA-256 its caller obtained from the one
 * hasher, {@code HashService}, and the file's name -- not its path. Moving a FASTA does not change
 * what Comet computes from it; renaming a spectrum file changes the name Comet's outputs are given
 * ({@code -N}), so the name counts. The encoding is conservative in that direction: a rename may
 * cause a rerun that was not strictly needed, and nothing can cause a reuse that was wrong.
 */
public sealed interface InputValue
        permits InputValue.Files,
                InputValue.Bytes,
                InputValue.Tool,
                InputValue.Text,
                InputValue.Decimal {

    /**
     * The value type, which must equal its kind's {@link InputKind#valueType()}.
     *
     * @return the type
     */
    ValueType type();

    /**
     * The SHA-256 of this value's canonical encoding.
     *
     * @return 64 lower-case hexadecimal characters
     */
    String digest();

    /**
     * A list of files, in order.
     *
     * @param files the files
     * @return the value
     */
    static Files files(List<NamedFile> files) {
        return new Files(files);
    }

    /**
     * A single file, as a list of one.
     *
     * @param name the file's name, without any directory
     * @param hashes its hashes, from the one hasher
     * @return the value
     */
    static Files file(String name, FileHashes hashes) {
        return new Files(List.of(new NamedFile(name, hashes)));
    }

    /**
     * Bytes held in memory, such as the canonical {@code comet.params} before it is written.
     *
     * @param content the bytes; not retained
     * @return the value, carrying only their SHA-256
     */
    static Bytes bytes(byte[] content) {
        Objects.requireNonNull(content, "content");
        return new Bytes(Sha256.hex(content));
    }

    /**
     * A tool identity.
     *
     * @param version the version the tool reports
     * @param binarySha256 the SHA-256 of its binary or JAR
     * @return the value
     */
    static Tool tool(String version, String binarySha256) {
        return new Tool(version, binarySha256);
    }

    /**
     * Canonical settings text.
     *
     * @param value the text
     * @return the value
     */
    static Text text(String value) {
        return new Text(value);
    }

    /**
     * A decimal number.
     *
     * @param value the number
     * @return the value
     */
    static Decimal decimal(BigDecimal value) {
        return new Decimal(value);
    }

    /**
     * A file, by name and hashes.
     *
     * @param name the file's name without any directory: not empty, no {@code /} or {@code \}
     * @param hashes its MD5 and SHA-256; only the SHA-256 enters a fingerprint
     */
    record NamedFile(String name, FileHashes hashes) {

        /**
         * Validates the name and requires the hashes.
         *
         * @throws NullPointerException naming the component if one is {@code null}
         * @throws IllegalArgumentException if the name is empty or contains a directory separator,
         *     quoting it
         */
        public NamedFile {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(hashes, "hashes");
            if (name.isEmpty() || name.contains("/") || name.contains("\\")) {
                throw new IllegalArgumentException(
                        "a file input is named by its file name alone, but was: \"" + name + "\"");
            }
        }
    }

    /**
     * An ordered list of files.
     *
     * <p>Encoding: for each file in order, the line {@code <sha256> <n>:<name>\n}, where {@code n}
     * is the name's length in UTF-8 bytes, written in ASCII decimal. The length prefix makes the
     * framing unambiguous whatever the name contains. An empty list encodes as zero bytes.
     *
     * @param files the files, in order
     */
    record Files(List<NamedFile> files) implements InputValue {

        /**
         * Copies the list.
         *
         * @throws NullPointerException if the list or an element is {@code null}
         */
        public Files {
            files = List.copyOf(files);
        }

        @Override
        public ValueType type() {
            return ValueType.FILES;
        }

        @Override
        public String digest() {
            StringBuilder encoding = new StringBuilder();
            for (NamedFile file : files) {
                encoding.append(file.hashes().sha256())
                        .append(' ')
                        .append(Sha256.framed(file.name()))
                        .append('\n');
            }
            return Sha256.hex(encoding.toString());
        }
    }

    /**
     * A byte sequence, identified by its SHA-256.
     *
     * <p>Encoding: the bytes themselves, so the digest <em>is</em> their SHA-256 -- equal to {@code
     * sha256sum} of the file the bytes are written to, which is what lets provenance compare the
     * archived {@code comet.params} with the fingerprint that chose to run it.
     *
     * @param sha256 the bytes' SHA-256
     */
    record Bytes(String sha256) implements InputValue {

        /**
         * Validates the digest and puts it in lower case.
         *
         * @throws NullPointerException if {@code sha256} is {@code null}
         * @throws IllegalArgumentException if it is not 64 hexadecimal characters
         */
        public Bytes {
            sha256 = Sha256.requireDigest(sha256, "sha256");
        }

        @Override
        public ValueType type() {
            return ValueType.BYTES;
        }

        @Override
        public String digest() {
            return sha256;
        }
    }

    /**
     * A tool identity.
     *
     * <p>Encoding: the line {@code <binarySha256> <n>:<version>\n}, {@code n} the version's length
     * in UTF-8 bytes.
     *
     * @param version the version the tool reports; not empty
     * @param binarySha256 the SHA-256 of the binary or JAR
     */
    record Tool(String version, String binarySha256) implements InputValue {

        /**
         * Validates both components.
         *
         * @throws NullPointerException naming a component that is {@code null}
         * @throws IllegalArgumentException if the version is empty or the digest is not 64
         *     hexadecimal characters
         */
        public Tool {
            Objects.requireNonNull(version, "version");
            if (version.isEmpty()) {
                throw new IllegalArgumentException("a tool identity needs a version");
            }
            binarySha256 = Sha256.requireDigest(binarySha256, "binarySha256");
        }

        @Override
        public ValueType type() {
            return ValueType.TOOL;
        }

        @Override
        public String digest() {
            return Sha256.hex(binarySha256 + ' ' + Sha256.framed(version) + '\n');
        }
    }

    /**
     * Canonical settings text. Producing a canonical form -- sorted keys, fixed number formatting
     * -- is the caller's job; this records exactly the text it is given.
     *
     * <p>Encoding: the text's UTF-8 bytes, unframed.
     *
     * @param value the text
     */
    record Text(String value) implements InputValue {

        /**
         * Requires the text.
         *
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public Text {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public ValueType type() {
            return ValueType.TEXT;
        }

        @Override
        public String digest() {
            return Sha256.hex(value);
        }
    }

    /**
     * A decimal number, held without trailing zeros so that {@code 0.01} and {@code 0.010} are one
     * value.
     *
     * <p>Encoding: {@link BigDecimal#toPlainString()} of that value, in ASCII -- never scientific
     * notation and never locale digits.
     *
     * @param value the number
     */
    record Decimal(BigDecimal value) implements InputValue {

        /**
         * Strips trailing zeros.
         *
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public Decimal {
            value = Objects.requireNonNull(value, "value").stripTrailingZeros();
        }

        @Override
        public ValueType type() {
            return ValueType.DECIMAL;
        }

        @Override
        public String digest() {
            return Sha256.hex(value.toPlainString());
        }
    }
}
