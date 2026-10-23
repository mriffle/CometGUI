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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The figure reader against images drawn here pixel by pixel: 40 x 20, white, with 7 orange pixels,
 * 5 green and 3 black -- 15 of ink.
 */
class FigureImageTest {

    @TempDir private Path directory;

    @Test
    @DisplayName("a 40 x 20 PNG: 15 pixels of ink, 7 orange, 5 green")
    void counts() throws IOException {
        BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 40; x++) {
                image.setRGB(x, y, 0xFFFFFF);
            }
        }
        for (int x = 0; x < 7; x++) {
            image.setRGB(x, 0, FigureImage.ORANGE);
        }
        for (int x = 0; x < 5; x++) {
            image.setRGB(x, 1, FigureImage.GREEN);
        }
        for (int x = 0; x < 3; x++) {
            image.setRGB(x, 2, 0x000000);
        }
        Path file = directory.resolve("figure.png");
        ImageIO.write(image, "png", file.toFile());
        FigureImage read = FigureImage.read(file);
        assertAll(
                () -> assertEquals(Files.size(file), read.bytes()),
                () -> assertEquals(40, read.width()),
                () -> assertEquals(20, read.height()),
                () -> assertEquals(15, read.ink()),
                () -> assertEquals(7, read.orange()),
                () -> assertEquals(5, read.green()));
    }

    @Test
    @DisplayName("a file that is not an image is refused, with its size")
    void notAnImage() throws IOException {
        Path file = directory.resolve("figure.png");
        Files.writeString(file, "not a picture", StandardCharsets.US_ASCII);
        AssertionError refused = assertThrows(AssertionError.class, () -> FigureImage.read(file));
        assertEquals(file + " (13 bytes) is not an image ImageIO decodes", refused.getMessage());
    }
}
