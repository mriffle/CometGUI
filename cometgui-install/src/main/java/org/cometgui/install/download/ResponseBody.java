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

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A response body read chunk by chunk, with a deadline on every chunk and no thread of its own.
 *
 * <h2>Why not an {@code InputStream}</h2>
 *
 * <p>{@code HttpRequest.Builder.timeout} bounds how long the <em>response</em> takes to arrive,
 * which for a streaming body handler means the headers. Once they have arrived, a server that stops
 * sending leaves {@code InputStream.read} blocked for as long as the connection survives, and a
 * hung server becomes a hung application rather than a failure. That is the specific defect this
 * class exists to prevent: a timeout on connect and on a stalled read, so a hung server is a
 * failure.
 *
 * <p>The obvious repair -- a watchdog thread that closes the stream -- costs a thread per transfer
 * and a shutdown path that has to be right in every failure case. Subscribing to {@code
 * BodyHandlers.ofPublisher()} instead gives the same guarantee for free: chunks are delivered by
 * the client's own thread into a queue, and {@link #next(Duration)} waits on that queue with a
 * timeout. There is nothing to shut down but the subscription.
 *
 * <p>One item is requested at a time, so the queue holds at most one chunk beyond the one being
 * written and memory does not grow with the size of the artefact. A 99 MB download costs the same
 * as a 1 kB one.
 */
final class ResponseBody implements Flow.Subscriber<List<ByteBuffer>>, AutoCloseable {

    /**
     * Queued when the body ends normally; a sentinel rather than {@code null}, which a queue bans.
     */
    private static final Object COMPLETE = new Object();

    /** Chunks, the completion sentinel, or the {@link Throwable} that ended the body. */
    private final BlockingQueue<Object> items = new LinkedBlockingQueue<>();

    /** Set once, by {@link #onSubscribe}, before any chunk can arrive. */
    private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();

    private ResponseBody() {}

    /**
     * Subscribes to a response body.
     *
     * @param publisher the body, from {@code HttpResponse.BodyHandlers.ofPublisher()}
     * @return the subscribed body, which the caller closes
     */
    static ResponseBody subscribeTo(Flow.Publisher<List<ByteBuffer>> publisher) {
        ResponseBody body = new ResponseBody();
        publisher.subscribe(body);
        return body;
    }

    @Override
    public void onSubscribe(Flow.Subscription newSubscription) {
        subscription.set(newSubscription);
        newSubscription.request(1);
    }

    @Override
    public void onNext(List<ByteBuffer> item) {
        items.add(item);
    }

    @Override
    public void onError(Throwable throwable) {
        items.add(throwable);
    }

    @Override
    public void onComplete() {
        items.add(COMPLETE);
    }

    /**
     * The next chunk of the body, or {@code null} at its end.
     *
     * @param stallTimeout how long to wait for bytes before calling the transfer stalled
     * @return the next chunk's buffers, or {@code null} when the body has ended
     * @throws HttpTimeoutException if nothing arrives within {@code stallTimeout}
     * @throws IOException if the body failed
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    List<ByteBuffer> next(Duration stallTimeout) throws IOException, InterruptedException {
        Object item = items.poll(stallTimeout.toNanos(), TimeUnit.NANOSECONDS);
        if (item == null) {
            throw new HttpTimeoutException(
                    "no bytes arrived for " + stallTimeout.toMillis() + " ms");
        }
        if (item == COMPLETE) {
            return null;
        }
        if (item instanceof Throwable failure) {
            throw failure instanceof IOException io ? io : new IOException(failure);
        }
        subscription.get().request(1);
        @SuppressWarnings("unchecked")
        List<ByteBuffer> chunk = (List<ByteBuffer>) item;
        return chunk;
    }

    /**
     * Cancels the subscription, which releases the connection.
     *
     * <p>Safe to call on every path, including one where the body has already completed: an
     * already-finished subscription ignores a cancel.
     */
    @Override
    public void close() {
        Flow.Subscription current = subscription.get();
        if (current != null) {
            current.cancel();
        }
    }
}
