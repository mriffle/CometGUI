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

package org.cometgui.app.e2e.oracle;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * A figure file read by the test with the JDK's own {@link ImageIO} (canonical scenario step 25):
 * whether it decodes at all, its size in pixels and in bytes, and how much of it is ink -- every
 * pixel that is not white -- and how many pixels are in each of PDV's two ion-series colours
 * ({@code PDVCLIMainClass.displayResults}: orange {@code #FF6600} and green {@code #009900}), so
 * that a blank or unannotated image is told from a drawn spectrum.
 *
 * @param bytes the file's size
 * @param width the image's width in pixels
 * @param height its height
 * @param ink how many pixels are not white
 * @param orange how many are PDV's orange
 * @param green how many are PDV's green
 */
public record FigureImage(long bytes, int width, int height, long ink, long orange, long green) {

    /** PDV's orange ion series. */
    public static final int ORANGE = 0xFF6600;

    /** PDV's green ion series. */
    public static final int GREEN = 0x009900;

    /**
     * Reads a figure.
     *
     * @param file the image file
     * @return what it shows
     * @throws IOException if it cannot be read
     * @throws AssertionError if it is not an image {@link ImageIO} can decode
     */
    public static FigureImage read(Path file) throws IOException {
        long bytes = Files.size(file);
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) {
            throw new AssertionError(
                    file + " (" + bytes + " bytes) is not an image ImageIO decodes");
        }
        long ink = 0;
        long orange = 0;
        long green = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y) & 0xFFFFFF;
                ink += rgb == 0xFFFFFF ? 0 : 1;
                orange += rgb == ORANGE ? 1 : 0;
                green += rgb == GREEN ? 1 : 0;
            }
        }
        return new FigureImage(bytes, image.getWidth(), image.getHeight(), ink, orange, green);
    }
}
