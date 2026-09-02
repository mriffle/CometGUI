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

package org.cometgui.install.archive;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Reads the file tree out of a macOS flat installer package: {@code xar!}, then gzip, then cpio.
 *
 * <p>Three nested containers. The outer one is a {@code xar} archive whose table of contents is a
 * zlib-compressed XML document listing every blob in the heap; one of those blobs is called {@code
 * Payload} and is a gzip stream wrapping a cpio archive of the installed file tree. Nothing here
 * runs {@code installer(8)}, {@code pkgutil} or a package script, and nothing needs administrative
 * rights: the payload is a plain relative file tree.
 *
 * <p><strong>The table of contents is XML from an untrusted file</strong>, so it is parsed with
 * doctype declarations refused outright and secure processing on. An installer package that could
 * make this reader fetch an external entity would be a way to reach the network from an extraction,
 * which is precisely what {@code R-SEC-05} exists to prevent elsewhere.
 *
 * <p>Like {@link DebPayloadReader} this kind survives {@code D-002} option C for one purpose only:
 * the two Percolator XSD companion files, which on macOS live at {@code usr/local/share/...} rather
 * than the {@code usr/share/...} the Debian payload uses.
 */
final class PkgPayloadReader implements ArchiveReader {

    /** The four bytes every flat package starts with. */
    private static final String XAR_MAGIC = "xar!";

    /** The fixed part of a {@code xar} header. */
    private static final int HEADER_BYTES = 28;

    /** The most table-of-contents bytes this reader will inflate. */
    private static final int MAX_TOC_BYTES = 8 * 1024 * 1024;

    /** The blob every flat package carries its file tree in. */
    private static final String PAYLOAD = "Payload";

    /** The artefact's name, for messages. */
    private final String artefactName;

    /** The package on disk, held open while the payload is read out of it. */
    private final SeekableByteChannel channel;

    /** The cpio reader over the decompressed payload. */
    private final CpioArchiveReader payload;

    /**
     * Opens a flat installer package and positions it at the start of its payload.
     *
     * @param source the package on disk
     * @throws IOException if it cannot be read
     * @throws ExtractionRejectedException if it is not a {@code xar} archive, if it carries no
     *     {@code Payload} blob, or if that blob uses a codec the runtime cannot decode
     */
    PkgPayloadReader(Path source) throws IOException {
        this.artefactName = String.valueOf(source.getFileName());
        this.channel = Files.newByteChannel(source, StandardOpenOption.READ);
        try {
            this.payload = new CpioArchiveReader(openPayload(), artefactName);
        } catch (IOException | RuntimeException failure) {
            channel.close();
            throw failure;
        }
    }

    @Override
    public ArchiveEntry next() throws IOException {
        return payload.next();
    }

    @Override
    public InputStream content() {
        return payload.content();
    }

    /**
     * Closes the payload reader and the package underneath it.
     *
     * <p>Both, for the reason {@link DebPayloadReader#close()} gives: the cpio reader owns a {@code
     * GZIPInputStream} with a native inflater of its own.
     *
     * @throws IOException if either cannot be closed
     */
    @Override
    public void close() throws IOException {
        try (SeekableByteChannel open = channel) {
            payload.close();
        }
    }

    private InputStream openPayload() throws IOException {
        ByteBuffer header = read(0, HEADER_BYTES);
        String magic = new String(header.array(), 0, 4, StandardCharsets.US_ASCII);
        if (!XAR_MAGIC.equals(magic)) {
            throw malformed(
                    "it begins \""
                            + magic
                            + "\" where \"xar!\" belongs, so it is not a macOS flat"
                            + " package");
        }
        int headerSize = header.getShort(4) & 0xFFFF;
        long tocCompressed = header.getLong(8);
        long tocUncompressed = header.getLong(16);
        if (tocUncompressed > MAX_TOC_BYTES || tocCompressed > MAX_TOC_BYTES) {
            throw malformed(
                    "its table of contents declares "
                            + tocUncompressed
                            + " bytes uncompressed from "
                            + tocCompressed
                            + ", and this reader inflates at most "
                            + MAX_TOC_BYTES);
        }
        byte[] toc = inflateToc(headerSize, tocCompressed, tocUncompressed);
        long heapOffset = headerSize + tocCompressed;
        Element data = findPayloadData(toc);
        long offset = childLong(data, "offset");
        long length = childLong(data, "length");
        String encoding = encodingStyle(data);
        channel.position(heapOffset + offset);
        InputStream blob =
                new BoundedEntryStream(
                        new BufferedInputStream(Channels.newInputStream(channel)), length);
        return decompress(decodeXar(blob, encoding));
    }

    private byte[] inflateToc(int headerSize, long tocCompressed, long tocUncompressed)
            throws IOException {
        channel.position(headerSize);
        try (InputStream compressed =
                        new BoundedEntryStream(
                                new BufferedInputStream(Channels.newInputStream(channel)),
                                tocCompressed);
                InflaterInputStream inflater = new InflaterInputStream(compressed)) {
            byte[] toc = inflater.readNBytes(MAX_TOC_BYTES);
            if (toc.length != tocUncompressed) {
                throw malformed(
                        "its table of contents declares "
                                + tocUncompressed
                                + " bytes and inflates to "
                                + toc.length);
            }
            return toc;
        }
    }

    private Element findPayloadData(byte[] toc) throws IOException {
        Element root = parse(toc);
        Element payloadFile = findPayloadFile(root);
        if (payloadFile == null) {
            throw malformed(
                    "its table of contents lists no \""
                            + PAYLOAD
                            + "\" blob, so the package"
                            + " carries no installed file tree");
        }
        Element data = child(payloadFile, "data");
        if (data == null) {
            throw malformed(
                    "its \""
                            + PAYLOAD
                            + "\" entry has no <data> element, so there is nothing in"
                            + " the heap to read");
        }
        return data;
    }

    private static Element findPayloadFile(Node node) {
        for (Element element : elementsOf(node)) {
            if ("file".equals(element.getTagName())) {
                Element name = child(element, "name");
                if (name != null && PAYLOAD.equals(name.getTextContent())) {
                    return element;
                }
            }
            Element found = findPayloadFile(element);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Element child(Element parent, String tag) {
        for (Element element : elementsOf(parent)) {
            if (tag.equals(element.getTagName())) {
                return element;
            }
        }
        return null;
    }

    /*
     * The element children of a node, as a list.  Walking a NodeList by index puts an off-by-one
     * boundary in two places for no gain: item(getLength()) returns null rather than throwing, so
     * the mutant that walks one past the end behaves identically and cannot be killed.  Reading
     * the children once removes the arithmetic instead of arguing about it.
     */
    private static List<Element> elementsOf(Node node) {
        /*
         * One index walk rather than two.  Its own boundary is not killable and is not a hole:
         * NodeList.item returns null past the end rather than throwing, so a walk that went one too
         * far would skip a null and stop on the next test.  The loop is here once so that the
         * argument is made once.
         */
        NodeList children = node.getChildNodes();
        List<Element> elements = new ArrayList<>(children.getLength());
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element element) {
                elements.add(element);
            }
        }
        return elements;
    }

    private long childLong(Element data, String tag) throws IOException {
        Element element = child(data, tag);
        String text = element == null ? "" : element.getTextContent().trim();
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException cause) {
            throw malformed(
                    "its \""
                            + PAYLOAD
                            + "\" entry gives <"
                            + tag
                            + "> as \""
                            + text
                            + "\", which is not a number");
        }
    }

    private static String encodingStyle(Element data) {
        Element encoding = child(data, "encoding");
        return encoding == null ? "" : encoding.getAttribute("style");
    }

    private InputStream decodeXar(InputStream blob, String style) throws IOException {
        if (style.isEmpty() || "application/octet-stream".equals(style)) {
            return blob;
        }
        if ("application/x-gzip".equals(style)) {
            return new InflaterInputStream(blob);
        }
        throw ExtractionRejectedException.artefact(
                RejectionReason.UNSUPPORTED_COMPRESSION,
                artefactName,
                " -- its payload is encoded as \""
                        + style
                        + "\", and this extractor decodes the raw and zlib forms only, because the"
                        + " Java runtime ships no xz or bzip2 decoder and this project adds no"
                        + " dependency to gain one");
    }

    private InputStream decompress(InputStream payloadBlob) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(payloadBlob);
        buffered.mark(6);
        byte[] sniff = buffered.readNBytes(6);
        buffered.reset();
        if (sniff.length >= 2 && (sniff[0] & 0xFF) == 0x1F && (sniff[1] & 0xFF) == 0x8B) {
            try {
                return new GZIPInputStream(buffered);
            } catch (IOException cause) {
                /*
                 * The payload announced itself as gzip and then was not one -- a header too short
                 * to finish, or a checksum that does not belong.  Refused with the artefact's name
                 * on it: an EOFException carrying no subject tells a reader that something ended,
                 * and not which downloaded file to go and look at.
                 */
                throw malformed(
                        "its payload begins with the gzip marker and is not a readable gzip"
                                + " stream: "
                                + cause);
            }
        }
        String text = new String(sniff, StandardCharsets.US_ASCII);
        if (text.startsWith("0707")) {
            return buffered;
        }
        throw ExtractionRejectedException.artefact(
                RejectionReason.UNSUPPORTED_COMPRESSION,
                artefactName,
                " -- its payload begins with bytes "
                        + hex(sniff)
                        + ", which is neither a gzip stream nor a cpio archive, and this extractor"
                        + " decodes no other payload compression");
    }

    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder();
        for (byte value : bytes) {
            text.append(String.format("%02x", value));
        }
        return text.toString();
    }

    /** The feature name that refuses a {@code DOCTYPE} declaration outright. */
    static final String DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";

    /**
     * Forces a parser factory into the only configuration this reader will read a package with.
     *
     * <p>The table of contents is XML that arrived over the network inside a downloaded {@code
     * .pkg}: attacker-controlled by construction. A parser left at its defaults will resolve an
     * external entity -- reaching the network or the local file system from inside an extraction --
     * and will expand a nested entity until the heap is gone.
     *
     * <p><strong>It forces the state; it does not merely decline to disturb it.</strong> Two of
     * these five settings happen to match the JDK's own defaults today, so a version of this method
     * that omitted them would behave identically on this runtime and no test could tell -- which is
     * exactly a protection that is present, believed and unproven. Written as a forcing operation
     * over a factory the caller supplies, each of the five is load-bearing and {@code
     * XarTableOfContentsHardeningTest} proves it by handing in a factory set to every unsafe value
     * first.
     *
     * @param factory the factory to harden, in whatever state it arrives
     * @return the same factory, hardened
     * @throws ParserConfigurationException if the factory refuses a setting this reader requires,
     *     which is a reason not to parse rather than a reason to carry on
     */
    static DocumentBuilderFactory harden(DocumentBuilderFactory factory)
            throws ParserConfigurationException {
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature(DISALLOW_DOCTYPE, true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(false);
        return factory;
    }

    private Element parse(byte[] toc) throws IOException {
        try {
            DocumentBuilder builder =
                    harden(DocumentBuilderFactory.newInstance()).newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(toc)).getDocumentElement();
        } catch (ParserConfigurationException | SAXException cause) {
            throw malformed("its table of contents is not readable as XML: " + cause.getMessage());
        }
    }

    private ExtractionRejectedException malformed(String detail) {
        return ExtractionRejectedException.artefact(
                RejectionReason.MALFORMED_ARCHIVE, artefactName, " -- " + detail);
    }

    private ByteBuffer read(long offset, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length).order(ByteOrder.BIG_ENDIAN);
        channel.position(offset);
        while (buffer.hasRemaining()) {
            /*
             * The test is for the end of the file, which a channel reports as -1.  A file channel
             * positioned inside a file never answers a non-empty buffer with zero, so the boundary
             * between "< 0" and "<= 0" is a value this loop cannot be handed.
             */
            if (channel.read(buffer) < 0) {
                throw malformed(
                        "it ends before offset "
                                + (offset + length)
                                + ", where its header belongs");
            }
        }
        return buffer.flip().order(ByteOrder.BIG_ENDIAN);
    }
}
