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

package org.cometgui.tools.process;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CharsetDecoder;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Drains one of a process's two output streams, delivering complete lines as they arrive.
 *
 * <p>One instance runs on one daemon thread. Nothing accumulates: a line is handed to the sink the
 * moment it is complete and is then forgotten, which is half of {@code R-PROC-03}. The other half
 * is {@link LineSplitter}'s cap, which stops a tool that never writes a newline from growing one
 * unbounded buffer.
 *
 * <p><strong>Decoding never fails.</strong> The decoder replaces malformed and unmappable input
 * rather than throwing, because a single stray byte in the middle of a two-hour search must not
 * silence the rest of the log. The replacement character is visible in the output, which is the
 * honest result.
 *
 * <p><strong>An {@link IOException} means two different things.</strong> After cancellation has
 * been requested the pipe is expected to die, and the pump ends quietly. Before that it is a real
 * fault, and swallowing it would leave a run whose log simply stops with no explanation -- so it is
 * delivered to the sink as a visible line beginning {@value #FAULT_PREFIX}.
 */
final class StreamPump implements Runnable {

    /** Marks a line the service wrote itself, rather than one the tool wrote. */
    static final String FAULT_PREFIX = "[cometgui] ";

    /** Read buffer, in characters. Large enough that a flood is not a syscall benchmark. */
    private static final int READ_BUFFER_CHARACTERS = 8192;

    private final InputStream source;
    private final CharsetDecoder decoder;
    private final String streamName;
    private final int maximumLineLength;
    private final Consumer<String> lineSink;
    private final BooleanSupplier cancellationRequested;

    /**
     * A pump for one stream.
     *
     * @param source the process stream to drain; closed when the pump ends
     * @param decoder the decoder to read it with, already configured to replace bad input
     * @param streamName how the stream is named in a fault line, such as {@code standard output}
     * @param maximumLineLength the cap handed to the {@link LineSplitter}
     * @param lineSink receives each complete line; must not throw, so pass a {@link
     *     GuardedListener} callback rather than a caller's listener directly
     * @param cancellationRequested tells the pump whether a dead pipe is expected
     * @throws NullPointerException if any argument is null
     */
    StreamPump(
            InputStream source,
            CharsetDecoder decoder,
            String streamName,
            int maximumLineLength,
            Consumer<String> lineSink,
            BooleanSupplier cancellationRequested) {
        this.source = Objects.requireNonNull(source, "source");
        this.decoder = Objects.requireNonNull(decoder, "decoder");
        this.streamName = Objects.requireNonNull(streamName, "streamName");
        this.maximumLineLength = maximumLineLength;
        this.lineSink = Objects.requireNonNull(lineSink, "lineSink");
        this.cancellationRequested =
                Objects.requireNonNull(cancellationRequested, "cancellationRequested");
    }

    /** Reads the stream to its end, or to the failure that ends it. */
    @Override
    public void run() {
        LineSplitter splitter = new LineSplitter(maximumLineLength, lineSink);
        char[] buffer = new char[READ_BUFFER_CHARACTERS];
        try (Reader reader = new InputStreamReader(source, decoder)) {
            int read = reader.read(buffer);
            while (read >= 0) {
                splitter.accept(buffer, 0, read);
                read = reader.read(buffer);
            }
            splitter.endOfStream();
        } catch (IOException streamFailed) {
            /* Whatever was already complete is still real output the tool wrote, so it is
             * delivered before the failure is reported or the pump ends. */
            splitter.endOfStream();
            if (!cancellationRequested.getAsBoolean()) {
                lineSink.accept(FAULT_PREFIX + streamName + " could not be read: " + streamFailed);
            }
        }
    }
}
