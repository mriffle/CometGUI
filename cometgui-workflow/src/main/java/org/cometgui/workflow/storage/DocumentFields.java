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

package org.cometgui.workflow.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.project.SchemaVersionPolicy;
import org.cometgui.provenance.json.CanonicalTimestamp;
import org.cometgui.provenance.json.JsonParseException;
import org.cometgui.provenance.json.JsonReader;
import org.cometgui.provenance.json.JsonValue;

/**
 * Reads the members of one storage document, refusing every malformation with an {@link
 * InvalidDocumentException} that names the document and the member and quotes no value.
 *
 * <p>Shared by {@link ProjectJson}, {@link RunJson} and {@link LockJson}, so that the three formats
 * apply one set of rules: the schema version first, every known member required, no member this
 * build does not know (an update would otherwise drop it), whole numbers only where numbers are
 * expected, timestamps exactly as {@link CanonicalTimestamp} writes them.
 */
final class DocumentFields {

    /**
     * The largest document this reader opens: 64 MiB.
     *
     * <p>A {@code run.json} for ten thousand spectrum files is a few megabytes; anything near this
     * bound is not a CometGUI document, and reading it whole would be the denial of service a
     * hostile file is designed for.
     */
    static final long MAX_DOCUMENT_BYTES = 64L << 20;

    /** The member path of the document root. */
    static final String ROOT = "";

    private final String document;

    /**
     * Creates the reader for one document.
     *
     * @param document the document's path or label, for messages
     */
    DocumentFields(String document) {
        this.document = Objects.requireNonNull(document, "document");
    }

    /**
     * Reads a document file as strict UTF-8 text, refusing one that is too large or not UTF-8.
     *
     * @param file the file
     * @return its text
     * @throws IOException if it cannot be read
     * @throws InvalidDocumentException if it is larger than {@link #MAX_DOCUMENT_BYTES} or is not
     *     UTF-8
     */
    static String readText(Path file) throws IOException {
        long size = Files.size(file);
        if (size > MAX_DOCUMENT_BYTES) {
            throw new InvalidDocumentException(
                    file.toString(),
                    ROOT,
                    file
                            + " is not valid: the document is "
                            + size
                            + " bytes, larger than the "
                            + MAX_DOCUMENT_BYTES
                            + " this reader opens");
        }
        return decode(file.toString(), Files.readAllBytes(file));
    }

    /**
     * Decodes bytes as strict UTF-8.
     *
     * @param document the document, for the message
     * @param bytes the bytes
     * @return the text
     * @throws InvalidDocumentException if the bytes are not UTF-8
     */
    static String decode(String document, byte[] bytes) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException notUtf8) {
            throw new InvalidDocumentException(
                    document, ROOT, document + " is not valid: the document is not UTF-8 text");
        }
    }

    /**
     * Parses the text and requires an object at the root.
     *
     * @param text the document's text
     * @return the root object
     */
    JsonValue.JsonObject root(String text) {
        JsonValue root;
        try {
            root = JsonReader.parse(text);
        } catch (JsonParseException malformed) {
            throw new InvalidDocumentException(
                    document,
                    document + " is not well-formed JSON: " + malformed.getMessage(),
                    malformed);
        }
        return object(root, ROOT);
    }

    /**
     * Applies {@code R-RUN-04}'s policy to the root's {@code schemaVersion}, before any other
     * member is looked at.
     *
     * @param root the root object
     * @param current the version this build reads
     * @throws org.cometgui.domain.project.UnsupportedSchemaVersionException for any other version
     */
    void requireVersion(JsonValue.JsonObject root, int current) {
        long found = integer(member(root, ROOT, "schemaVersion"), "schemaVersion");
        SchemaVersionPolicy.requireReadable(document, found, current);
    }

    /**
     * Applies {@code R-RUN-04}'s policy to the root's {@code schemaVersion} for a format this build
     * reads at every version from {@code oldest} to {@code current}, before any other member is
     * looked at. A version above {@code current} is refused as newer; one below {@code oldest} as
     * older, with no migration.
     *
     * @param root the root object
     * @param oldest the oldest version this build reads
     * @param current the newest version this build reads and writes
     * @return the declared version, between {@code oldest} and {@code current}
     * @throws org.cometgui.domain.project.UnsupportedSchemaVersionException for any other version
     */
    long requireVersionBetween(JsonValue.JsonObject root, int oldest, int current) {
        long found = integer(member(root, ROOT, "schemaVersion"), "schemaVersion");
        if (found < oldest || found > current) {
            SchemaVersionPolicy.requireReadable(document, found, current);
        }
        return found;
    }

    /**
     * Requires an object to have no member outside the given names. Missing members are caught by
     * {@link #member}; this catches extra ones, which an update would otherwise silently drop.
     *
     * @param owner the object
     * @param ownerPath its member path
     * @param names every member the format defines for it
     */
    void requireOnly(JsonValue.JsonObject owner, String ownerPath, List<String> names) {
        long unknown =
                owner.members().keySet().stream().filter(name -> !names.contains(name)).count();
        if (unknown > 0) {
            throw invalid(
                    ownerPath,
                    "has "
                            + unknown
                            + " member(s) this build does not know; its schema version defines"
                            + " exactly "
                            + names);
        }
    }

    /**
     * Looks up a member every document of this version must carry.
     *
     * @param owner the object
     * @param ownerPath its member path
     * @param name the member's name
     * @return its value
     */
    JsonValue member(JsonValue.JsonObject owner, String ownerPath, String name) {
        return owner.member(name)
                .orElseThrow(() -> invalid(ownerPath, "has no member \"" + name + "\""));
    }

    /**
     * Requires an object.
     *
     * @param value the value
     * @param path its member path
     * @return the object
     */
    JsonValue.JsonObject object(JsonValue value, String path) {
        if (value instanceof JsonValue.JsonObject asObject) {
            return asObject;
        }
        throw invalid(path, "must be a JSON object");
    }

    /**
     * Requires an array.
     *
     * @param value the value
     * @param path its member path
     * @return its elements
     */
    List<JsonValue> array(JsonValue value, String path) {
        if (value instanceof JsonValue.JsonArray asArray) {
            return asArray.elements();
        }
        throw invalid(path, "must be a JSON array");
    }

    /**
     * Requires a string.
     *
     * @param value the value
     * @param path its member path
     * @return the text
     */
    String string(JsonValue value, String path) {
        if (value instanceof JsonValue.JsonString asString) {
            return asString.value();
        }
        throw invalid(path, "must be a string");
    }

    /**
     * Requires a whole number.
     *
     * @param value the value
     * @param path its member path
     * @return the number
     */
    long integer(JsonValue value, String path) {
        if (value instanceof JsonValue.JsonNumber asNumber) {
            return asNumber.value();
        }
        throw invalid(path, "must be a whole number");
    }

    /**
     * Requires a whole number that fits in an {@code int}.
     *
     * @param value the value
     * @param path its member path
     * @return the number
     */
    int smallInteger(JsonValue value, String path) {
        long number = integer(value, path);
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw invalid(path, "must fit in a signed 32-bit integer");
        }
        return (int) number;
    }

    /**
     * Requires a timestamp in the canonical form.
     *
     * @param value the value
     * @param path its member path
     * @return the instant
     */
    Instant timestamp(JsonValue value, String path) {
        String text = string(value, path);
        try {
            return CanonicalTimestamp.parse(text);
        } catch (DateTimeParseException notATimestamp) {
            throw invalid(
                    path, "must be a UTC timestamp of the form " + CanonicalTimestamp.PATTERN);
        }
    }

    /**
     * Requires a timestamp or {@code null}.
     *
     * @param value the value
     * @param path its member path
     * @return the instant, or empty for {@code null}
     */
    Optional<Instant> optionalTimestamp(JsonValue value, String path) {
        if (value instanceof JsonValue.JsonNull) {
            return Optional.empty();
        }
        return Optional.of(timestamp(value, path));
    }

    /**
     * Reads the {@code md5} and {@code sha256} members of an object.
     *
     * @param owner the object
     * @param ownerPath its member path
     * @return the hashes
     */
    FileHashes hashes(JsonValue.JsonObject owner, String ownerPath) {
        String md5 = string(member(owner, ownerPath, "md5"), child(ownerPath, "md5"));
        String sha256 = string(member(owner, ownerPath, "sha256"), child(ownerPath, "sha256"));
        FileHashes hashes = rebuilt(ownerPath, () -> new FileHashes(md5, sha256));
        if (!hashes.md5().equals(md5) || !hashes.sha256().equals(sha256)) {
            throw invalid(ownerPath, "must record its digests in lower-case hexadecimal");
        }
        return hashes;
    }

    /**
     * Builds part of the model, turning any refusal into one that quotes no value.
     *
     * <p>The supplier must hold nothing but a constructor call over values already read, so that a
     * refusal of a member read inside it is never re-attributed to the record around it.
     *
     * @param <T> the type built
     * @param path the member path the values came from
     * @param construction the constructor call
     * @return what it built
     */
    <T> T rebuilt(String path, Supplier<T> construction) {
        try {
            return construction.get();
        } catch (RuntimeException rejected) {
            throw invalid(
                    path,
                    "was refused by the model ("
                            + rejected.getClass().getSimpleName()
                            + "); the model's own message is not repeated, because it quotes the"
                            + " value it refused");
        }
    }

    /**
     * A refusal naming the member and the rule.
     *
     * @param path the member path, empty for the document as a whole
     * @param problem what is wrong
     * @return the exception to throw
     */
    InvalidDocumentException invalid(String path, String problem) {
        String subject = path.isEmpty() ? "the document" : "\"" + path + "\"";
        return new InvalidDocumentException(
                document, path, document + " is not valid: " + subject + " " + problem);
    }

    /**
     * The path of a member of a member.
     *
     * @param ownerPath the owner's path, empty for the root
     * @param name the member's name
     * @return the child's path
     */
    static String child(String ownerPath, String name) {
        return ownerPath.isEmpty() ? name : ownerPath + "." + name;
    }

    /**
     * The path of an element of an array.
     *
     * @param arrayPath the array's path
     * @param index the element's index
     * @return the element's path
     */
    static String element(String arrayPath, int index) {
        return arrayPath + "[" + index + "]";
    }
}
