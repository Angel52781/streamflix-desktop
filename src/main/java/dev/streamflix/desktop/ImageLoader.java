package dev.streamflix.desktop;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.*;

final class ImageLoader {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(6, r -> {
        Thread t = new Thread(r, "poster-loader");
        t.setDaemon(true);
        return t;
    });
    private static final Map<String, ImageIcon> CACHE = new ConcurrentHashMap<>();
    private static final Http HTTP = new Http();

    private ImageLoader() {}

    static void load(String url, JLabel label, int width, int height) {
        if (url == null || url.isBlank()) return;
        ImageIcon cached = CACHE.get(key(url, width, height));
        if (cached != null) { label.setIcon(cached); label.setText(""); return; }
        CompletableFuture.supplyAsync(() -> {
            try {
                byte[] bytes = HTTP.getBytes(url);
                BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
                if (source == null) return null;
                Image scaled = fit(source, width, height);
                return new ImageIcon(scaled);
            } catch (Exception ignored) { return null; }
        }, POOL).thenAccept(icon -> {
            if (icon == null) return;
            CACHE.put(key(url, width, height), icon);
            SwingUtilities.invokeLater(() -> { label.setIcon(icon); label.setText(""); });
        });
    }

    private static Image fit(BufferedImage image, int width, int height) {
        double scale = Math.min((double) width / image.getWidth(), (double) height / image.getHeight());
        int w = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(Theme.PANEL_ALT);
        g.fillRect(0, 0, width, height);
        g.drawImage(image, (width - w) / 2, (height - h) / 2, w, h, null);
        g.dispose();
        return canvas;
    }

    private static String key(String url, int w, int h) { return url + "#" + w + "x" + h; }
}
