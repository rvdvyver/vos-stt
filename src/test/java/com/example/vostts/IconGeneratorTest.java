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
            g.setPaint(new GradientPaint(0,0, Color.ORANGE, size, size, Color.BLUE));
            g.fillRect(0,0,size,size);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, size/2));
            FontMetrics fm = g.getFontMetrics();
            String txt = "V";
            int x = (size - fm.stringWidth(txt)) / 2;
            int y = (size - fm.getHeight()) / 2 + fm.getAscent();
            g.drawString(txt, x, y);
            g.dispose();
            Files.createDirectories(icon.getParent());
            ImageIO.write(img, "png", icon.toFile());
        }
        assertTrue(Files.exists(icon));
    }
}
