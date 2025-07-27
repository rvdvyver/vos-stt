package com.example.vostts;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class IconGeneratorTest {
    @Test
    void generateIconIfMissing() throws Exception {
        Path icon = Path.of("target/app-icon.png");
        if (Files.notExists(icon)) {
            int size = 256;
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Transparent background
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, size, size);
            g.setComposite(AlphaComposite.SrcOver);

            // Fox head
            Polygon head = new Polygon();
            head.addPoint(size / 2, size / 3);
            head.addPoint(size * 3 / 4, size * 3 / 4);
            head.addPoint(size / 4, size * 3 / 4);
            g.setColor(new Color(255, 140, 0));
            g.fillPolygon(head);

            // Left ear
            Polygon leftEar = new Polygon();
            leftEar.addPoint(size / 3, size / 3);
            leftEar.addPoint(size / 4, size / 8);
            leftEar.addPoint(size / 5, size / 3);
            g.fillPolygon(leftEar);

            // Right ear
            Polygon rightEar = new Polygon();
            rightEar.addPoint(size * 2 / 3, size / 3);
            rightEar.addPoint(size * 3 / 4, size / 8);
            rightEar.addPoint(size * 4 / 5, size / 3);
            g.fillPolygon(rightEar);

            // Eyes
            g.setColor(Color.WHITE);
            int eyeSize = size / 12;
            g.fillOval(size / 2 - eyeSize - eyeSize / 2, size / 2, eyeSize, eyeSize);
            g.fillOval(size / 2 + eyeSize / 2, size / 2, eyeSize, eyeSize);
            g.setColor(Color.BLACK);
            int pupilSize = eyeSize / 2;
            g.fillOval(size / 2 - eyeSize - pupilSize / 2, size / 2 + pupilSize / 2, pupilSize, pupilSize);
            g.fillOval(size / 2 + eyeSize / 2 + pupilSize / 2 - pupilSize, size / 2 + pupilSize / 2, pupilSize, pupilSize);

            // Nose
            int noseSize = eyeSize;
            g.fillOval(size / 2 - noseSize / 2, size * 3 / 4 - noseSize / 2, noseSize, noseSize);

            g.dispose();
            Files.createDirectories(icon.getParent());
            ImageIO.write(img, "png", icon.toFile());
        }
        assertTrue(Files.exists(icon));
    }
}
