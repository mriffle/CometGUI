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

package org.cometgui.install.download;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ResponseBody} driven by a hand-written publisher.
 *
 * <p>Some of what this class has to survive cannot be produced by a real server: a body that fails
 * with something that is not an {@link IOException}, and a publisher that never calls {@code
 * onSubscribe} at all. Both are reachable through the {@link Flow.Publisher} contract, which is why
 * they are driven directly here rather than through {@link HttpDownloader}.
 */
class ResponseBodyTest {

    private static final Duration PATIENT = Duration.ofSeconds(5);

    /** A publisher the test drives by hand. */
    private static final class ManualPublisher implements Flow.Publisher<List<ByteBuffer>> {

        private final AtomicReference<Flow.Subscriber<? super List<ByteBuffer>>> subscriber =
                new AtomicReference<>();
        private final AtomicLong requested = new AtomicLong();
        private final boolean subscribeAtAll;

        ManualPublisher(boolean subscribeAtAll) {
            this.subscribeAtAll = subscribeAtAll;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super List<ByteBuffer>> newSubscriber) {
            if (!subscribeAtAll) {
                return;
            }
            this.subscriber.set(newSubscriber);
            newSubscriber.onSubscribe(
                    new Flow.Subscription() {
                        @Override
                        public void request(long n) {
                            requested.addAndGet(n);
                        }

                        @Override
                        public void cancel() {
                            requested.set(-1);
                        }
                    });
        }

        long requested() {
            return requested.get();
        }

        Flow.Subscriber<? super List<ByteBuffer>> subscriber() {
            return subscriber.get();
        }
    }

    private static List<ByteBuffer> chunk(String text) {
        return List.of(ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("chunks arrive in order and the end of the body is null, not an exception")
    void chunksArriveInOrder() throws IOException, InterruptedException {
        ManualPublisher publisher = new ManualPublisher(true);
        try (ResponseBody body = ResponseBody.subscribeTo(publisher)) {
            publisher.subscriber().onNext(chunk("one"));
            publisher.subscriber().onNext(chunk("two"));
            publisher.subscriber().onComplete();

            assertAll(
                    () -> assertEquals(3, body.next(PATIENT).get(0).remaining()),
                    () -> assertEquals(3, body.next(PATIENT).get(0).remaining()),
                    () -> assertNull(body.next(PATIENT), "the end of the body"),
                    () ->
                            assertTrue(
                                    publisher.requested() >= 3,
                                    "one item is requested at a time, so the queue never grows with"
                                            + " the size of the artefact: "
                                            + publisher.requested()));
        }
    }

    @Test
    @DisplayName("a body that fails with an IOException raises that exception unchanged")
    void anIoFailureIsRaisedUnchanged() {
        ManualPublisher publisher = new ManualPublisher(true);
        IOException failure = new IOException("fixed content-length: 200, bytes received: 40");
        try (ResponseBody body = ResponseBody.subscribeTo(publisher)) {
            publisher.subscriber().onError(failure);
            IOException thrown = assertThrows(IOException.class, () -> body.next(PATIENT));
            assertEquals(failure, thrown);
        }
    }

    @Test
    @DisplayName("a body that fails with something else is wrapped rather than swallowed")
    void aNonIoFailureIsWrapped() {
        ManualPublisher publisher = new ManualPublisher(true);
        RuntimeException failure = new IllegalStateException("the client gave up");
        try (ResponseBody body = ResponseBody.subscribeTo(publisher)) {
            publisher.subscriber().onError(failure);
            IOException thrown = assertThrows(IOException.class, () -> body.next(PATIENT));
            assertEquals(failure, thrown.getCause());
        }
    }

    @Test
    @DisplayName("a body that delivers nothing times out rather than blocking for ever")
    void aSilentBodyTimesOut() {
        ManualPublisher publisher = new ManualPublisher(true);
        try (ResponseBody body = ResponseBody.subscribeTo(publisher)) {
            HttpTimeoutException thrown =
                    assertThrows(
                            HttpTimeoutException.class, () -> body.next(Duration.ofMillis(50)));
            assertTrue(thrown.getMessage().contains("50 ms"), thrown.getMessage());
        }
    }

    @Test
    @DisplayName("closing before a subscription arrived is safe")
    void closingBeforeSubscribingIsSafe() {
        ManualPublisher publisher = new ManualPublisher(false);
        ResponseBody body = ResponseBody.subscribeTo(publisher);
        body.close();
        assertEquals(0L, publisher.requested());
    }

    @Test
    @DisplayName("closing cancels the subscription, which is what releases the connection")
    void closingCancelsTheSubscription() {
        ManualPublisher publisher = new ManualPublisher(true);
        ResponseBody body = ResponseBody.subscribeTo(publisher);
        body.close();
        assertEquals(-1L, publisher.requested(), "cancel() was called");
    }
}
