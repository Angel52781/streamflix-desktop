package dev.streamflix.desktop;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Small vector icon system for Streamflix controls.
 *
 * Functional pictograms are intentionally generic and geometric; the brand
 * personality belongs to the Streamflix mark, while controls prioritize
 * consistency, DPI independence, and accessibility.
 */
final class StreamflixIcons {
    enum Glyph {
        PLAY,
        PAUSE,
        VOLUME,
        VOLUME_OFF,
        BACK,
        CHEVRON_LEFT,
        CHEVRON_RIGHT,
        SETTINGS,
        PIN,
        MINIMIZE,
        MAXIMIZE,
        RESTORE,
        FULLSCREEN,
        INFO,
        MORE,
        CHECK,
        PLUS
    }

    private StreamflixIcons() {}

    static Icon icon(Glyph glyph, int size) {
        return icon(glyph, size, Theme.TEXT);
    }

    static Icon icon(Glyph glyph, int size, Color color) {
        return new VectorIcon(glyph, Math.max(8, size), color == null ? Theme.TEXT : color);
    }

    private record VectorIcon(Glyph glyph, int size, Color color) implements Icon {
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }

        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.translate(x, y);
                g2.scale(size / 24.0, size / 24.0);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                paintGlyph(g2, glyph);
            } finally {
                g2.dispose();
            }
        }
    }

    private static void paintGlyph(Graphics2D g2, Glyph glyph) {
        switch (glyph) {
            case PLAY -> {
                Path2D p = new Path2D.Double();
                p.moveTo(8, 5);
                p.lineTo(19, 12);
                p.lineTo(8, 19);
                p.closePath();
                g2.fill(p);
            }
            case PAUSE -> {
                g2.fill(new RoundRectangle2D.Double(7, 5, 3.5, 14, 1.2, 1.2));
                g2.fill(new RoundRectangle2D.Double(13.5, 5, 3.5, 14, 1.2, 1.2));
            }
            case VOLUME, VOLUME_OFF -> {
                Path2D speaker = new Path2D.Double();
                speaker.moveTo(5, 10);
                speaker.lineTo(9, 10);
                speaker.lineTo(13, 6);
                speaker.lineTo(13, 18);
                speaker.lineTo(9, 14);
                speaker.lineTo(5, 14);
                speaker.closePath();
                g2.fill(speaker);
                if (glyph == Glyph.VOLUME) {
                    g2.drawArc(12, 8, 6, 8, -55, 110);
                    g2.drawArc(12, 5, 10, 14, -50, 100);
                } else {
                    g2.drawLine(16, 9, 21, 14);
                    g2.drawLine(21, 9, 16, 14);
                }
            }
            case BACK -> {
                g2.drawLine(19, 12, 6, 12);
                g2.drawLine(6, 12, 11, 7);
                g2.drawLine(6, 12, 11, 17);
            }
            case CHEVRON_LEFT -> {
                g2.drawLine(15, 6, 9, 12);
                g2.drawLine(9, 12, 15, 18);
            }
            case CHEVRON_RIGHT, MORE -> {
                g2.drawLine(9, 6, 15, 12);
                g2.drawLine(15, 12, 9, 18);
            }
            case SETTINGS -> {
                Path2D gear = new Path2D.Double();
                double[] offsets = {-0.20, -0.10, 0.10, 0.20};
                double[] radii = {6.7, 8.2, 8.2, 6.7};
                for (int tooth = 0; tooth < 8; tooth++) {
                    double center = -Math.PI / 2.0 + tooth * Math.PI / 4.0;
                    for (int point = 0; point < offsets.length; point++) {
                        double angle = center + offsets[point];
                        double x = 12 + Math.cos(angle) * radii[point];
                        double y = 12 + Math.sin(angle) * radii[point];
                        if (tooth == 0 && point == 0) gear.moveTo(x, y);
                        else gear.lineTo(x, y);
                    }
                }
                gear.closePath();
                g2.draw(gear);
                g2.drawOval(9, 9, 6, 6);
            }
            case PIN -> {
                Path2D p = new Path2D.Double();
                p.moveTo(9, 5);
                p.lineTo(16, 5);
                p.lineTo(14.5, 10);
                p.lineTo(18, 13);
                p.lineTo(13, 13);
                p.lineTo(12, 20);
                p.lineTo(11, 13);
                p.lineTo(6, 13);
                p.lineTo(9.5, 10);
                p.closePath();
                g2.draw(p);
            }
            case MINIMIZE -> g2.drawLine(6, 16, 18, 16);
            case MAXIMIZE -> g2.draw(new RoundRectangle2D.Double(6, 6, 12, 12, 1.5, 1.5));
            case RESTORE -> {
                g2.draw(new RoundRectangle2D.Double(8, 6, 10, 10, 1.5, 1.5));
                g2.draw(new RoundRectangle2D.Double(6, 8, 10, 10, 1.5, 1.5));
            }
            case FULLSCREEN -> {
                g2.drawLine(6, 10, 6, 6);
                g2.drawLine(6, 6, 10, 6);
                g2.drawLine(18, 10, 18, 6);
                g2.drawLine(18, 6, 14, 6);
                g2.drawLine(6, 14, 6, 18);
                g2.drawLine(6, 18, 10, 18);
                g2.drawLine(18, 14, 18, 18);
                g2.drawLine(18, 18, 14, 18);
            }
            case INFO -> {
                g2.drawOval(5, 5, 14, 14);
                g2.fillOval(11, 8, 2, 2);
                g2.drawLine(12, 12, 12, 16);
            }
            case CHECK -> {
                g2.drawLine(6, 12, 10, 16);
                g2.drawLine(10, 16, 18, 8);
            }
            case PLUS -> {
                g2.drawLine(6, 12, 18, 12);
                g2.drawLine(12, 6, 12, 18);
            }
        }
    }
}
