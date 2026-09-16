package dev.streamflix.desktop;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.*;

final class ImageLoader {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r, "poster-loader");
        t.setDaemon(true);
        return t;
    });
    private static final Map<String, ImageIcon> CACHE = new ConcurrentHashMap<>();
    private static final Http HTTP = new Http();

    static {
        ImageIO.scanForPlugins();
    }

    private ImageLoader() {}

    static void load(String url, JLabel label, int width, int height) {
        if (url == null || url.isBlank()) {
            showFallback(label);
            return;
        }
        String cacheKey = key(url, width, height);
        ImageIcon cached = CACHE.get(cacheKey);
        if (cached != null) {
            label.setIcon(cached);
            label.setText("");
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            try {
                BufferedImage source = download(url);
                Image scaled = fit(source, width, height);
                return new ImageIcon(scaled);
            } catch (Throwable ex) {
                System.err.println("Image load failed: " + url + " :: " + ex.getMessage());
                return null;
            }
        }, POOL).whenComplete((icon, error) -> SwingUtilities.invokeLater(() -> {
            if (icon == null || error != null) {
                showFallback(label);
                return;
            }
            CACHE.put(cacheKey, icon);
            label.setIcon(icon);
            label.setText("");
        }));
    }

    static BufferedImage download(String url) throws Exception {
        byte[] bytes;
        try {
            bytes = HTTP.getBytes(url);
        } catch (java.io.IOException first) {
            java.net.URI uri = java.net.URI.create(url);
            String origin = uri.getScheme() + "://" + uri.getHost() + "/";
            bytes = HTTP.getBytesLegacy(url, Map.of("Referer", origin, "Platform", "android"));
        }
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
        if (source == null) {
            throw new IllegalStateException("No ImageIO decoder available for " + url);
        }
        return source;
    }

    private static Image fit(BufferedImage image, int width, int height) {
        double scale = Math.max((double) width / image.getWidth(), (double) height / image.getHeight());
        int w = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Theme.PANEL_ALT);
        g.fillRect(0, 0, width, height);
        g.drawImage(image, (width - w) / 2, (height - h) / 2, w, h, null);
        g.dispose();
        return canvas;
    }

    private static void showFallback(JLabel label) {
        label.setIcon(null);
        label.setText("Sin imagen");
        label.setForeground(Theme.MUTED);
    }

    private static String key(String url, int w, int h) {
        return url + "#" + w + "x" + h;
    }
}
