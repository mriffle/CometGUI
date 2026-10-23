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

package org.cometgui.app.e2e.bridge;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The bridge's wire format, one implementation for both ends.
 *
 * <p>A connection begins with the four bytes {@value #MAGIC} from the client. Then, any number of
 * times, the client sends a request and the server sends one response:
 *
 * <pre>
 *   request  = string token, string operation, int count, value * count
 *   response = byte status, (value          if status is OK
 *                            | string message otherwise)
 *   value    = 'N'                        null
 *            | 'S' string                 a String
 *            | 'Z' byte (0 or 1)          a boolean
 *            | 'I' int                    an int
 *            | 'L' int count, string*     a List&lt;String&gt;
 *   string   = int length, length bytes of UTF-8
 *   int      = four bytes, big-endian
 * </pre>
 *
 * <p>Those are exactly the types {@link org.cometgui.app.uidriver.RemotableUiDriver} is allowed
 * (P14-3), so every remotable method crosses unchanged. Lengths are bounded ({@link
 * #MAXIMUM_STRING_BYTES}, {@link #MAXIMUM_LIST_SIZE}, {@link #MAXIMUM_ARGUMENTS}) so that a
 * malformed or hostile peer is refused instead of making the other end allocate what it says.
 */
public final class BridgeProtocol {

    /** What a client says first: "CometGUI bridge, version 1". */
    public static final String MAGIC = "CGB1";

    /** The longest string either end accepts, in UTF-8 bytes. */
    public static final int MAXIMUM_STRING_BYTES = 16 * 1024 * 1024;

    /** The most items a list may hold. */
    public static final int MAXIMUM_LIST_SIZE = 1024 * 1024;

    /**
     * The longest token or operation name accepted, in UTF-8 bytes: both are read before the token
     * is checked, so a peer without one cannot make the server allocate more than this.
     */
    public static final int MAXIMUM_NAME_BYTES = 256;

    /** The most arguments a request may carry. */
    public static final int MAXIMUM_ARGUMENTS = 16;

    /** How a request ended. */
    public enum Status {
        /** It ran; the response carries its value. */
        OK,
        /**
         * The driver failed an assertion -- a control missing, an item not offered: the same
         * failure an in-process driver would throw, with its message.
         */
        ASSERTION_FAILED,
        /** It threw something else; the message names the exception. */
        ERROR,
        /** It was not run: no token or the wrong one, or not an operation the bridge offers. */
        REFUSED
    }

    /**
     * One request.
     *
     * @param token the per-launch token the server wrote beside its port
     * @param operation what to do: a {@code RemotableUiDriver} method name, or a {@code bridge.}
     *     operation
     * @param arguments the arguments, each {@code null}, a {@code String}, a {@code Boolean}, an
     *     {@code Integer} or a {@code List<String>}
     */
    public record Request(String token, String operation, List<Object> arguments) {

        /**
         * Checks the values and copies the arguments (which may hold {@code null}).
         *
         * @throws IllegalArgumentException if an argument has a type the protocol does not carry
         */
        public Request {
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(arguments, "arguments");
            arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
            for (Object argument : arguments) {
                checkCarried(argument);
            }
        }
    }

    /**
     * One response.
     *
     * @param status how the request ended
     * @param value its value when {@code OK}, else {@code null}
     * @param message why it did not succeed, else {@code null}
     */
    public record Response(Status status, Object value, String message) {

        /**
         * A successful response.
         *
         * @param value the value
         * @return the response
         */
        public static Response ok(Object value) {
            checkCarried(value);
            return new Response(Status.OK, value, null);
        }

        /**
         * An unsuccessful response.
         *
         * @param status why
         * @param message what to tell the client
         * @return the response
         */
        public static Response failed(Status status, String message) {
            if (status == Status.OK) {
                throw new IllegalArgumentException("a failure cannot be OK");
            }
            return new Response(status, null, Objects.requireNonNullElse(message, "(no message)"));
        }
    }

    private BridgeProtocol() {}

    /**
     * Writes the connection's opening bytes.
     *
     * @param out the stream
     * @throws IOException if writing fails
     */
    public static void writeMagic(DataOutputStream out) throws IOException {
        out.write(MAGIC.getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    /**
     * Reads and checks the connection's opening bytes.
     *
     * @param in the stream
     * @throws IOException if they are not {@value #MAGIC}, or reading fails
     */
    public static void readMagic(DataInputStream in) throws IOException {
        byte[] got = new byte[MAGIC.length()];
        in.readFully(got);
        if (!Arrays.equals(got, MAGIC.getBytes(StandardCharsets.US_ASCII))) {
            throw new IOException("not a CometGUI bridge client: it did not begin with " + MAGIC);
        }
    }

    /**
     * Writes a request.
     *
     * @param out the stream
     * @param request the request
     * @throws IOException if writing fails
     */
    public static void writeRequest(DataOutputStream out, Request request) throws IOException {
        writeString(out, request.token());
        writeString(out, request.operation());
        out.writeInt(request.arguments().size());
        for (Object argument : request.arguments()) {
            writeValue(out, argument);
        }
        out.flush();
    }

    /**
     * Reads a request.
     *
     * @param in the stream
     * @return the request
     * @throws java.io.EOFException if the client closed the connection
     * @throws IOException if the request is malformed or exceeds a bound
     */
    public static Request readRequest(DataInputStream in) throws IOException {
        String token = readString(in, MAXIMUM_NAME_BYTES);
        String operation = readString(in, MAXIMUM_NAME_BYTES);
        int count = in.readInt();
        if (count < 0 || count > MAXIMUM_ARGUMENTS) {
            throw new IOException("a request with " + count + " arguments");
        }
        List<Object> arguments = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            arguments.add(readValue(in));
        }
        return new Request(token, operation, arguments);
    }

    /**
     * Writes a response.
     *
     * @param out the stream
     * @param response the response
     * @throws IOException if writing fails
     */
    public static void writeResponse(DataOutputStream out, Response response) throws IOException {
        out.writeByte(response.status().ordinal());
        if (response.status() == Status.OK) {
            writeValue(out, response.value());
        } else {
            writeString(out, response.message());
        }
        out.flush();
    }

    /**
     * Reads a response.
     *
     * @param in the stream
     * @return the response
     * @throws IOException if it is malformed or reading fails
     */
    public static Response readResponse(DataInputStream in) throws IOException {
        int status = in.readUnsignedByte();
        if (status >= Status.values().length) {
            throw new IOException("an unknown response status " + status);
        }
        Status known = Status.values()[status];
        if (known == Status.OK) {
            return new Response(Status.OK, readValue(in), null);
        }
        return new Response(known, null, readString(in));
    }

    private static void checkCarried(Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Boolean
                || value instanceof Integer) {
            return;
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof String)) {
                    throw new IllegalArgumentException(
                            "a list the bridge carries holds strings only, not " + item);
                }
            }
            return;
        }
        throw new IllegalArgumentException(
                "the bridge carries null, String, boolean, int and List<String> only, not a "
                        + value.getClass().getName());
    }

    private static void writeValue(DataOutputStream out, Object value) throws IOException {
        if (value == null) {
            out.writeByte('N');
        } else if (value instanceof String text) {
            out.writeByte('S');
            writeString(out, text);
        } else if (value instanceof Boolean flag) {
            out.writeByte('Z');
            out.writeByte(flag ? 1 : 0);
        } else if (value instanceof Integer number) {
            out.writeByte('I');
            out.writeInt(number);
        } else if (value instanceof List<?> list) {
            out.writeByte('L');
            out.writeInt(list.size());
            for (Object item : list) {
                writeString(out, (String) item);
            }
        } else {
            throw new IllegalArgumentException("not carried: " + value.getClass().getName());
        }
    }

    private static Object readValue(DataInputStream in) throws IOException {
        int tag = in.readUnsignedByte();
        return switch (tag) {
            case 'N' -> null;
            case 'S' -> readString(in);
            case 'Z' -> {
                int flag = in.readUnsignedByte();
                if (flag > 1) {
                    throw new IOException("a boolean of " + flag);
                }
                yield flag == 1;
            }
            case 'I' -> in.readInt();
            case 'L' -> {
                int size = in.readInt();
                if (size < 0 || size > MAXIMUM_LIST_SIZE) {
                    throw new IOException("a list of " + size + " items");
                }
                List<String> items = new ArrayList<>(Math.min(size, 1024));
                for (int index = 0; index < size; index++) {
                    items.add(readString(in));
                }
                yield List.copyOf(items);
            }
            default -> throw new IOException("an unknown value tag " + tag);
        };
    }

    private static void writeString(DataOutputStream out, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAXIMUM_STRING_BYTES) {
            throw new IOException("a string of " + bytes.length + " bytes is too long to send");
        }
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        return readString(in, MAXIMUM_STRING_BYTES);
    }

    private static String readString(DataInputStream in, int maximum) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > maximum) {
            throw new IOException("a string of " + length + " bytes");
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
