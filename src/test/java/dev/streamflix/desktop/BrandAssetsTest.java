package dev.streamflix.desktop;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BrandAssetsTest {
    public static void main(String[] args) throws Exception {
        testBrandMarkRendersCoreColors();
        testFunctionalIconsHaveStableDimensions();
        testIcoExportHasWindowsHeader();
        System.out.println("BrandAssetsTest OK");
    }

    private static void testBrandMarkRendersCoreColors() {
        BufferedImage image = BrandMark.appIconImage(128);
        boolean hasRed = false;
        boolean hasWhite = false;
        boolean hasDark = false;
        for (int y = 0; y < image.getHeight(); y += 2) {
            for (int x = 0; x < image.getWidth(); x += 2) {
                Color c = new Color(image.getRGB(x, y), true);
                if (c.getAlpha() == 0) continue;
                if (c.getRed() > 150 && c.getGreen() < 90 && c.getBlue() < 100) hasRed = true;
                if (c.getRed() > 220 && c.getGreen() > 220 && c.getBlue() > 220) hasWhite = true;
                if (c.getRed() < 35 && c.getGreen() < 35 && c.getBlue() < 45) hasDark = true;
            }
        }
        require(hasRed && hasWhite && hasDark, "Brand mark lost one of its core visual colors");
    }

    private static void testFunctionalIconsHaveStableDimensions() {
        for (StreamflixIcons.Glyph glyph : StreamflixIcons.Glyph.values()) {
            Icon icon = StreamflixIcons.icon(glyph, 18);
            require(icon.getIconWidth() == 18 && icon.getIconHeight() == 18,
                    "Unexpected icon dimensions for " + glyph);
        }
    }

    private static void testIcoExportHasWindowsHeader() throws Exception {
        Path temp = Files.createTempFile("streamflix-brand-", ".ico");
        try {
            IconExporter.writeIco(temp);
            byte[] bytes = Files.readAllBytes(temp);
            require(bytes.length > 1000, "ICO export is unexpectedly small");
            require((bytes[0] & 0xff) == 0 && (bytes[1] & 0xff) == 0, "ICO reserved header invalid");
            require((bytes[2] & 0xff) == 1 && (bytes[3] & 0xff) == 0, "ICO type header invalid");
            require((bytes[4] & 0xff) == 8 && (bytes[5] & 0xff) == 0, "ICO image count invalid");
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
