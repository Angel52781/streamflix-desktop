package dev.streamflix.desktop;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Streamflix brand mark derived from the approved monogram-S direction.
 *
 * The geometry is intentionally vector-only so the same mark stays crisp in
 * Swing, Windows taskbar/title surfaces, and exported application icons.
 */
final class BrandMark {
    private static final double VIEWBOX = 257.0;
    private static final Color BRAND_RED = new Color(201, 42, 54);
    private static final Color BRAND_WHITE = new Color(246, 247, 249);
    private static final Color BRAND_BG = new Color(10, 12, 17);

    private BrandMark() {}

    static Icon markIcon(int size) {
        return new BrandIcon(size, false);
    }

    static Icon appIcon(int size) {
        return new BrandIcon(size, true);
    }

    static List<Image> windowIcons() {
        int[] sizes = {16, 20, 24, 32, 48, 64, 128, 256};
        List<Image> images = new ArrayList<>(sizes.length);
        for (int size : sizes) images.add(appIconImage(size));
        return List.copyOf(images);
    }

    static BufferedImage appIconImage(int size) {
        int actual = Math.max(1, size);
        BufferedImage image = new BufferedImage(actual, actual, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        try {
            paint(g2, actual, actual, true);
        } finally {
            g2.dispose();
        }
        return image;
    }

    static BufferedImage markImage(int size) {
        int actual = Math.max(1, size);
        BufferedImage image = new BufferedImage(actual, actual, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        try {
            paint(g2, actual, actual, false);
        } finally {
            g2.dispose();
        }
        return image;
    }

    private static void paint(Graphics2D g2, int width, int height, boolean background) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        double side = Math.min(width, height);
        double ox = (width - side) / 2.0;
        double oy = (height - side) / 2.0;

        if (background) {
            double radius = Math.max(3.0, side * 0.19);
            g2.setColor(BRAND_BG);
            g2.fill(new RoundRectangle2D.Double(ox, oy, side, side, radius, radius));
        }

        // The approved source mark sits inside a 257x257 charcoal field with
        // generous breathing room. Preserve that exact visual proportion.
        double inset = background ? side * 0.10 : 0.0;
        double drawSide = side - inset * 2.0;
        double scale = drawSide / VIEWBOX;
        Graphics2D mark = (Graphics2D) g2.create();
        try {
            mark.translate(ox + inset, oy + inset);
            mark.scale(scale, scale);

            mark.setColor(BRAND_RED);
            mark.fill(redTop());
            mark.fill(redLeft());
            mark.fill(redRight());
            mark.fill(redBottom());

            mark.setColor(BRAND_WHITE);
            mark.fill(whiteTop());
            mark.fill(whiteBottom());
        } finally {
            mark.dispose();
        }
    }

    private static Path2D redTop() {
        Path2D p = new Path2D.Double();
        p.moveTo(126, 36);
        p.curveTo(109, 34, 82, 38, 63, 55);
        p.lineTo(95, 85);
        p.curveTo(93, 74, 100, 60, 126, 36);
        p.closePath();
        return p;
    }

    private static Path2D redLeft() {
        Path2D p = new Path2D.Double();
        p.moveTo(55, 64);
        p.curveTo(48, 74, 47, 92, 52, 105);
        p.curveTo(58, 120, 73, 130, 95, 139);
        p.lineTo(149, 152);
        p.closePath();
        return p;
    }

    private static Path2D redRight() {
        Path2D p = new Path2D.Double();
        p.moveTo(107, 97);
        p.lineTo(166, 115);
        p.curveTo(185, 121, 198, 136, 202, 151);
        p.curveTo(206, 164, 203, 176, 200, 183);
        p.closePath();
        return p;
    }

    private static Path2D redBottom() {
        Path2D p = new Path2D.Double();
        p.moveTo(158, 161);
        p.lineTo(192, 193);
        p.curveTo(186, 202, 174, 209, 159, 212);
        p.curveTo(147, 215, 136, 216, 127, 215);
        p.lineTo(156, 185);
        p.curveTo(162, 179, 161, 170, 158, 161);
        p.closePath();
        return p;
    }

    private static Path2D whiteTop() {
        Path2D p = new Path2D.Double();
        p.moveTo(111, 67);
        p.lineTo(156, 67);
        p.lineTo(157, 95);
        p.lineTo(198, 95);
        p.lineTo(198, 36);
        p.lineTo(142, 36);
        p.closePath();
        return p;
    }

    private static Path2D whiteBottom() {
        Path2D p = new Path2D.Double();
        p.moveTo(49, 153);
        p.lineTo(49, 215);
        p.lineTo(111, 215);
        p.lineTo(141, 185);
        p.lineTo(97, 185);
        p.lineTo(96, 153);
        p.closePath();
        return p;
    }

    private record BrandIcon(int size, boolean background) implements Icon {
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }

        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.translate(x, y);
                paint(g2, size, size, background);
            } finally {
                g2.dispose();
            }
        }
    }
}
