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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.cometgui.app.e2e.bridge.BridgeProtocol.Request;
import org.cometgui.app.e2e.bridge.BridgeProtocol.Response;
import org.cometgui.app.e2e.bridge.BridgeProtocol.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The wire format: every carried type survives the trip, and what it does not carry is refused. */
class BridgeProtocolTest {

    @Test
    @DisplayName(
            "a request with every argument type, including null and non-ASCII text, round-trips")
    void requestRoundTrip() throws IOException {
        Request sent =
                new Request(
                        "tok",
                        "typeInto",
                        Arrays.asList(
                                "protéomique run", null, true, false, 42, List.of("a", "é\tb")));
        Request received =
                BridgeProtocol.readRequest(
                        in(write(out -> BridgeProtocol.writeRequest(out, sent))));
        assertEquals(sent, received);
    }

    @Test
    @DisplayName("each response kind round-trips: a value, void, and the three failures")
    void responseRoundTrip() throws IOException {
        for (Response sent :
                List.of(
                        Response.ok(List.of("x", "y")),
                        Response.ok(null),
                        Response.ok(7),
                        Response.failed(Status.ASSERTION_FAILED, "#x offers no item"),
                        Response.failed(Status.ERROR, "java.lang.IllegalStateException: boom"),
                        Response.failed(Status.REFUSED, "refused: no token"))) {
            assertEquals(
                    sent,
                    BridgeProtocol.readResponse(
                            in(write(out -> BridgeProtocol.writeResponse(out, sent)))));
        }
    }

    @Test
    @DisplayName("the wire bytes of a small request are exactly the documented format")
    void exactBytes() throws IOException {
        byte[] bytes =
                write(out -> BridgeProtocol.writeRequest(out, new Request("t", "tab", List.of(1))));
        assertArrayEquals(
                new byte[] {
                    0, 0, 0, 1, 't', 0, 0, 0, 3, 't', 'a', 'b', 0, 0, 0, 1, 'I', 0, 0, 0, 1
                },
                bytes);
    }

    @Test
    @DisplayName("an argument the protocol does not carry is refused before anything is sent")
    void uncarriedTypes() {
        assertThrows(IllegalArgumentException.class, () -> new Request("t", "x", List.of(1.5d)));
        assertThrows(
                IllegalArgumentException.class, () -> new Request("t", "x", List.of(List.of(1))));
        assertThrows(IllegalArgumentException.class, () -> Response.ok(new Object()));
        assertThrows(IllegalArgumentException.class, () -> Response.failed(Status.OK, "no"));
    }

    @Test
    @DisplayName(
            "a token or operation name longer than its bound is refused before it is read, so a"
                    + " peer without the token cannot make the server allocate")
    void boundedNames() throws IOException {
        byte[] huge =
                write(
                        out -> {
                            out.writeInt(BridgeProtocol.MAXIMUM_NAME_BYTES + 1);
                            out.write(new byte[BridgeProtocol.MAXIMUM_NAME_BYTES + 1]);
                        });
        IOException refused =
                assertThrows(IOException.class, () -> BridgeProtocol.readRequest(in(huge)));
        assertEquals("a string of 257 bytes", refused.getMessage());
        byte[] negative = write(out -> out.writeInt(-1));
        assertThrows(IOException.class, () -> BridgeProtocol.readRequest(in(negative)));
        byte[] arguments =
                write(
                        out -> {
                            out.writeInt(0);
                            out.writeInt(0);
                            out.writeInt(BridgeProtocol.MAXIMUM_ARGUMENTS + 1);
                        });
        assertEquals(
                "a request with 17 arguments",
                assertThrows(IOException.class, () -> BridgeProtocol.readRequest(in(arguments)))
                        .getMessage());
    }

    @Test
    @DisplayName("a connection that does not open with CGB1 is refused")
    void magic() throws IOException {
        BridgeProtocol.readMagic(in(write(BridgeProtocol::writeMagic)));
        IOException refused =
                assertThrows(
                        IOException.class,
                        () -> BridgeProtocol.readMagic(in("GET ".getBytes("US-ASCII"))));
        assertTrue(refused.getMessage().contains("did not begin with CGB1"));
    }

    @Test
    @DisplayName("an unknown value tag or response status is refused")
    void unknownTags() throws IOException {
        byte[] badValue =
                write(
                        out -> {
                            out.writeByte(0);
                            out.writeByte('X');
                        });
        assertEquals(
                "an unknown value tag 88",
                assertThrows(IOException.class, () -> BridgeProtocol.readResponse(in(badValue)))
                        .getMessage());
        byte[] badStatus = write(out -> out.writeByte(9));
        assertThrows(IOException.class, () -> BridgeProtocol.readResponse(in(badStatus)));
    }

    private interface Writing {
        void to(DataOutputStream out) throws IOException;
    }

    private static byte[] write(Writing writing) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            writing.to(out);
        }
        return bytes.toByteArray();
    }

    private static DataInputStream in(byte[] bytes) {
        return new DataInputStream(new ByteArrayInputStream(bytes));
    }
}
