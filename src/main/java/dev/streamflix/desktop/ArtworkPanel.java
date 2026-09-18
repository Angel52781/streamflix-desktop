package dev.streamflix.desktop;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/** Responsive cover artwork that caches the expensive cover-scale operation. */
final class ArtworkPanel extends JPanel {
    private volatile BufferedImage image;
    private volatile BufferedImage scaled;
    private volatile int scaledWidth = -1;
    private volatile int scaledHeight = -1;
    private String fallbackText = "Sin imagen";

    ArtworkPanel(String url) {
        setOpaque(true);
        setBackground(Theme.PANEL_ALT);
        ImageLoader.loadRaw(url, loaded -> {
            image = loaded;
            scaled = null;
            scaledWidth = -1;
            scaledHeight = -1;
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

        int targetWidth = Math.max(1, getWidth());
        int targetHeight = Math.max(1, getHeight());
        BufferedImage ready = scaled;

        if (ready == null || scaledWidth != targetWidth || scaledHeight != targetHeight) {
            ready = scaleCover(current, targetWidth, targetHeight);
            scaled = ready;
            scaledWidth = targetWidth;
            scaledHeight = targetHeight;
        }

        g.drawImage(ready, 0, 0, null);
    }

    private static BufferedImage scaleCover(BufferedImage source, int width, int height) {
        double scale = Math.max(
                width / (double) source.getWidth(),
                height / (double) source.getHeight()
        );
        int w = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(source.getHeight() * scale));

        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = canvas.createGraphics();
        try {
            // Bilinear is visually sufficient for card/downscale work and materially
            // cheaper than re-running bicubic scaling on every scroll repaint.
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            g2.drawImage(source, (width - w) / 2, (height - h) / 2, w, h, null);
        } finally {
            g2.dispose();
        }
        return canvas;
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
