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

package org.cometgui.app.testing;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Searches every file under a directory, found by walking the tree from its root -- never from a
 * list of what the application is expected to write -- for a secret in every form it could have
 * been written in (phase 12 gate item 7, {@code AC-LL-06}).
 *
 * <p>The forms: its UTF-8 bytes; UTF-16 little- and big-endian; standard and URL-safe Base64, each
 * padded and unpadded on its own and, because a secret inside a longer Base64 text is encoded at
 * one of three alignments, the run of characters every such encoding shares at each alignment; URL
 * encoding as a form posts it, and every byte percent-encoded. A file is read in chunks that
 * overlap by the longest form, so a form split across two chunks is found.
 *
 * <p>Nothing here prints a secret: a hit names the file and the form.
 */
public final class SecretSweep {

    /** How much of a file is read at a time. */
    private static final int CHUNK = 4 * 1024 * 1024;

    private SecretSweep() {}

    /**
     * One form of the secret.
     *
     * @param name what it is, for a message
     * @param bytes the bytes to search for
     */
    public record Form(String name, byte[] bytes) {

        /**
         * Copies the bytes.
         *
         * @throws IllegalArgumentException if they are empty
         */
        public Form {
            Objects.requireNonNull(name, "name");
            bytes = bytes.clone();
            if (bytes.length == 0) {
                throw new IllegalArgumentException("an empty form matches everything: " + name);
            }
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Form form
                    && name.equals(form.name)
                    && Arrays.equals(bytes, form.bytes);
        }

        @Override
        public int hashCode() {
            return name.hashCode() * 31 + Arrays.hashCode(bytes);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Every form of a secret the search looks for.
     *
     * @param secret the secret
     * @return its forms, by name; forms with equal bytes are kept once
     */
    public static List<Form> forms(String secret) {
        byte[] utf8 = secret.getBytes(StandardCharsets.UTF_8);
        List<Form> forms = new ArrayList<>();
        forms.add(new Form("UTF-8", utf8));
        forms.add(new Form("UTF-16LE", secret.getBytes(StandardCharsets.UTF_16LE)));
        forms.add(new Form("UTF-16BE", secret.getBytes(StandardCharsets.UTF_16BE)));
        for (boolean urlSafe : new boolean[] {false, true}) {
            Base64.Encoder encoder = urlSafe ? Base64.getUrlEncoder() : Base64.getEncoder();
            String alphabet = urlSafe ? "URL-safe Base64" : "Base64";
            forms.add(ascii(alphabet + ", padded", encoder.encodeToString(utf8)));
            forms.add(
                    ascii(alphabet + ", unpadded", encoder.withoutPadding().encodeToString(utf8)));
            for (int offset = 0; offset < 3; offset++) {
                byte[] shifted = new byte[offset + utf8.length];
                System.arraycopy(utf8, 0, shifted, offset, utf8.length);
                String encoded = encoder.withoutPadding().encodeToString(shifted);
                int first = (8 * offset + 5) / 6;
                int end = 8 * (offset + utf8.length) / 6;
                forms.add(
                        ascii(
                                alphabet + ", inside a longer text at byte offset " + offset,
                                encoded.substring(first, end)));
            }
        }
        forms.add(ascii("URL-encoded (form)", URLEncoder.encode(secret, StandardCharsets.UTF_8)));
        StringBuilder percent = new StringBuilder();
        for (byte value : utf8) {
            percent.append('%').append(HexFormat.of().withUpperCase().toHexDigits(value));
        }
        forms.add(ascii("percent-encoded, every byte", percent.toString()));
        return List.copyOf(new LinkedHashSet<>(forms));
    }

    private static Form ascii(String name, String text) {
        return new Form(name, text.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * The forms found in a stream.
     *
     * @param in the bytes
     * @param forms what to look for
     * @return the names of the forms found
     * @throws IOException if the stream cannot be read
     */
    public static List<String> search(InputStream in, List<Form> forms) throws IOException {
        int overlap = 0;
        for (Form form : forms) {
            overlap = Math.max(overlap, form.bytes.length - 1);
        }
        Set<String> found = new LinkedHashSet<>();
        byte[] carry = new byte[0];
        byte[] chunk = new byte[CHUNK];
        int read;
        while ((read = in.readNBytes(chunk, 0, CHUNK)) > 0) {
            byte[] window = new byte[carry.length + read];
            System.arraycopy(carry, 0, window, 0, carry.length);
            System.arraycopy(chunk, 0, window, carry.length, read);
            String text = new String(window, StandardCharsets.ISO_8859_1);
            for (Form form : forms) {
                if (text.contains(new String(form.bytes, StandardCharsets.ISO_8859_1))) {
                    found.add(form.name);
                }
            }
            carry = Arrays.copyOfRange(window, Math.max(0, window.length - overlap), window.length);
        }
        return List.copyOf(found);
    }

    /**
     * What a walk of one tree saw.
     *
     * @param root where it started
     * @param directories how many directories it entered
     * @param files how many regular files it found
     * @param searched how many it read and searched
     * @param older how many it left unread because they were not written since the walk's cut-off
     * @param unreadable what it could not read, with why
     * @param otherEntries how many entries were neither directories nor regular files (links,
     *     sockets), which are not followed
     * @param searchedFiles every file it searched
     * @param hits each file holding the secret, with the forms found; never the secret
     */
    public record Walk(
            Path root,
            int directories,
            int files,
            int searched,
            int older,
            List<String> unreadable,
            int otherEntries,
            Set<Path> searchedFiles,
            List<String> hits) {

        /** Copies the collections. */
        public Walk {
            unreadable = List.copyOf(unreadable);
            searchedFiles = Set.copyOf(searchedFiles);
            hits = List.copyOf(hits);
        }

        /**
         * The walk in one line, for the test's report.
         *
         * @return the counts
         */
        public String summary() {
            return root
                    + ": "
                    + directories
                    + " directories, "
                    + files
                    + " files, "
                    + searched
                    + " searched, "
                    + older
                    + " older than the cut-off, "
                    + unreadable.size()
                    + " unreadable, "
                    + otherEntries
                    + " other entries";
        }
    }

    /**
     * Walks a tree from its root, without following links, and searches every regular file in it.
     *
     * @param root the tree
     * @param forms what to look for
     * @param writtenSince when present, only files modified at or after this are read; the others
     *     are counted
     * @param skipped directories not entered (counted where they are walked on their own)
     * @return what the walk saw
     * @throws IOException if the root cannot be walked at all
     */
    public static Walk walk(
            Path root, List<Form> forms, Optional<FileTime> writtenSince, Set<Path> skipped)
            throws IOException {
        int[] counts = new int[5];
        List<String> unreadable = new ArrayList<>();
        Set<Path> searched = new LinkedHashSet<>();
        List<String> hits = new ArrayList<>();
        Files.walkFileTree(
                root,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(
                            Path directory, BasicFileAttributes attributes) {
                        if (!directory.equals(root) && skipped.contains(directory)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        counts[0]++;
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                        if (!attributes.isRegularFile()) {
                            counts[4]++;
                            return FileVisitResult.CONTINUE;
                        }
                        counts[1]++;
                        if (writtenSince.isPresent()
                                && attributes.lastModifiedTime().compareTo(writtenSince.get())
                                        < 0) {
                            counts[3]++;
                            return FileVisitResult.CONTINUE;
                        }
                        try (InputStream in = Files.newInputStream(file)) {
                            List<String> found = search(in, forms);
                            counts[2]++;
                            searched.add(file);
                            if (!found.isEmpty()) {
                                hits.add(file + " holds the secret as " + found);
                            }
                        } catch (IOException failed) {
                            unreadable.add(file + ": " + failed.getClass().getSimpleName());
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException failed) {
                        unreadable.add(
                                file
                                        + ": "
                                        + (failed instanceof AccessDeniedException
                                                ? "access denied"
                                                : failed.getClass().getSimpleName()));
                        return FileVisitResult.CONTINUE;
                    }
                });
        return new Walk(
                root,
                counts[0],
                counts[1],
                counts[2],
                counts[3],
                unreadable,
                counts[4],
                searched,
                hits);
    }
}
