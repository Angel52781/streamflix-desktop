package dev.streamflix.desktop;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

final class ImageLoader {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r, "poster-loader");
        t.setDaemon(true);
        return t;
    });

    private static final Map<String, ImageIcon> CACHE = lru(256);
    private static final Map<String, BufferedImage> RAW_CACHE = lru(96);
    private static final Http HTTP = new Http();

    static {
        ImageIO.scanForPlugins();
    }

    private ImageLoader() {}

    private static <K, V> Map<K, V> lru(int maxEntries) {
        return Collections.synchronizedMap(new LinkedHashMap<>(maxEntries, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxEntries;
            }
        });
    }

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
                BufferedImage base = fit(source, width, height);
                BufferedImage retina = fit(source, width * 2, height * 2);
                return new ImageIcon(new BaseMultiResolutionImage(base, retina));
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

    static void loadRaw(String url, Consumer<BufferedImage> callback) {
        if (url == null || url.isBlank()) {
            SwingUtilities.invokeLater(() -> callback.accept(null));
            return;
        }

        BufferedImage cached = RAW_CACHE.get(url);
        if (cached != null) {
            SwingUtilities.invokeLater(() -> callback.accept(cached));
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            try {
                BufferedImage image = download(url);
                RAW_CACHE.put(url, image);
                return image;
            } catch (Throwable ex) {
                System.err.println("Image load failed: " + url + " :: " + ex.getMessage());
                return null;
            }
        }, POOL).whenComplete((image, error) -> SwingUtilities.invokeLater(() ->
                callback.accept(error == null ? image : null)));
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

    private static BufferedImage fit(BufferedImage image, int width, int height) {
        double scale = Math.max((double) width / image.getWidth(), (double) height / image.getHeight());
        int w = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(image.getHeight() * scale));

        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
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
        return url + "#" + w + "x" + h + "@1x2x";
    }
}
