package dev.streamflix.desktop;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/** Responsive high-quality cover artwork painted from the cached source image. */
final class ArtworkPanel extends JPanel {
    private volatile BufferedImage image;
    private String fallbackText = "Sin imagen";

    ArtworkPanel(String url) {
        setOpaque(true);
        setBackground(Theme.PANEL_ALT);
        ImageLoader.loadRaw(url, loaded -> {
            image = loaded;
            repaint();
        });
    }

    void setFallbackText(String text) {
        fallbackText = text == null ? "" : text;
        repaint();
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        BufferedImage current = image;
        if (current == null) {
            paintFallback(g);
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            double scale = Math.max(
                    getWidth() / (double) current.getWidth(),
                    getHeight() / (double) current.getHeight()
            );
            int w = Math.max(1, (int) Math.round(current.getWidth() * scale));
            int h = Math.max(1, (int) Math.round(current.getHeight() * scale));
            int x = (getWidth() - w) / 2;
            int y = (getHeight() - h) / 2;
            g2.drawImage(current, x, y, w, h, null);
        } finally {
            g2.dispose();
        }
    }

    private void paintFallback(Graphics g) {
        if (fallbackText == null || fallbackText.isBlank()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setColor(Theme.MUTED);
            g2.setFont(Theme.FONT.deriveFont(12f));
            FontMetrics fm = g2.getFontMetrics();
            int x = Math.max(8, (getWidth() - fm.stringWidth(fallbackText)) / 2);
            int y = Math.max(fm.getAscent() + 8, (getHeight() + fm.getAscent()) / 2);
            g2.drawString(fallbackText, x, y);
        } finally {
            g2.dispose();
        }
    }
}
