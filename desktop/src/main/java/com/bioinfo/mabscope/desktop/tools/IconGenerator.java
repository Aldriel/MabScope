/*
 * Copyright 2026 Maxime Ethier - Consultant en Bioinformatique/Biocomputing Consultant
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bioinfo.mabscope.desktop.tools;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.StrokeLineCap;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Draws the application icon (an antibody: a white Y whose antigen-binding tips, the CDRs, are
 * amber) as PNG files for the window and as a Windows {@code .ico} for the executable. Run with
 * {@code mvnw -pl desktop javafx:run@icons}.
 */
public final class IconGenerator extends Application {

    private static final int[] SIZES = {16, 32, 48, 64, 256};

    /**
     * Starts the generator.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws IOException {
        System.setProperty("java.awt.headless", "true");
        Path module = Files.isDirectory(Path.of("desktop")) ? Path.of("desktop") : Path.of(".");
        Path view = module.resolve("src/main/resources/com/bioinfo/mabscope/desktop/view");
        Path packaging = module.resolve("packaging");
        Files.createDirectories(packaging);

        List<byte[]> pngs = new ArrayList<>();
        for (int size : SIZES) {
            byte[] png = png(draw(size));
            Files.write(view.resolve("icon-" + size + ".png"), png);
            pngs.add(png);
        }
        Files.write(packaging.resolve("MabScope.ico"), ico(pngs));
        System.out.println(
                "Icons written to " + view.toAbsolutePath().normalize() + " and " + packaging);
        Platform.exit();
    }

    private static WritableImage draw(int size) {
        Canvas canvas = new Canvas(size, size);
        GraphicsContext g = canvas.getGraphicsContext2D();
        double arc = size * 0.22;
        g.setFill(
                new LinearGradient(
                        0,
                        0,
                        0,
                        1,
                        true,
                        CycleMethod.NO_CYCLE,
                        new Stop(0, Color.web("#14a395")),
                        new Stop(1, Color.web("#0b4f4a"))));
        g.fillRoundRect(0, 0, size, size, arc, arc);

        double cx = size * 0.5;
        double hinge = size * 0.52;
        double stroke = Math.max(2, size * 0.12);
        g.setLineCap(StrokeLineCap.ROUND);
        g.setStroke(Color.WHITE);
        g.setLineWidth(stroke);
        // Fc stem, then the two Fab arms.
        g.strokeLine(cx, hinge, cx, size * 0.84);
        double armX = size * 0.25;
        double armY = size * 0.22;
        g.strokeLine(cx, hinge, armX, armY);
        g.strokeLine(cx, hinge, size - armX, armY);
        // Antigen-binding tips (CDRs).
        double tip = stroke * 1.15;
        g.setFill(Color.web("#fbbf24"));
        g.fillOval(armX - tip / 2, armY - tip / 2, tip, tip);
        g.fillOval(size - armX - tip / 2, armY - tip / 2, tip, tip);
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return canvas.snapshot(params, null);
    }

    private static byte[] png(WritableImage image) throws IOException {
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        int[] pixels = new int[w * h];
        image.getPixelReader()
                .getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        BufferedImage buffered = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, w, h, pixels, 0, w);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(buffered, "png", out);
        return out.toByteArray();
    }

    /**
     * Packs PNG images into an ICO file (PNG-compressed entries, supported since Windows Vista).
     */
    private static byte[] ico(List<byte[]> pngs) {
        int headerSize = 6 + 16 * pngs.size();
        int total = headerSize + pngs.stream().mapToInt(p -> p.length).sum();
        ByteBuffer buffer = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) 0).putShort((short) 1).putShort((short) pngs.size());
        int offset = headerSize;
        for (int i = 0; i < pngs.size(); i++) {
            int size = SIZES[i];
            buffer.put((byte) (size >= 256 ? 0 : size)).put((byte) (size >= 256 ? 0 : size));
            buffer.put((byte) 0).put((byte) 0);
            buffer.putShort((short) 1).putShort((short) 32);
            buffer.putInt(pngs.get(i).length).putInt(offset);
            offset += pngs.get(i).length;
        }
        pngs.forEach(buffer::put);
        return buffer.array();
    }
}
