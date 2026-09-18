package dev.streamflix.desktop;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/** Large home hero using responsive backdrop artwork with streaming-style hierarchy. */
final class HeroPanel extends JLayeredPane {
    private final ArtworkPanel backdrop;
    private final JPanel shade;
    private final JPanel copy;

    HeroPanel(Models.ShowItem item, Consumer<Models.ShowItem> onOpen) {
        setPreferredSize(new Dimension(1080, 420));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 420));
        setOpaque(true);
        setBackground(Color.BLACK);

        String image = item.banner() != null && !item.banner().isBlank() ? item.banner() : item.poster();
        backdrop = new ArtworkPanel(image);
        backdrop.setFallbackText("");
        add(backdrop, Integer.valueOf(0));

        shade = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    int w = getWidth();
                    int h = getHeight();

                    g2.setPaint(new GradientPaint(
                            0, 0, new Color(0, 0, 0, 220),
                            Math.max(1, (int) (w * 0.72)), 0, new Color(0, 0, 0, 18)));
                    g2.fillRect(0, 0, w, h);

                    g2.setPaint(new GradientPaint(
                            0, Math.max(1, (int) (h * 0.50)), new Color(0, 0, 0, 0),
                            0, h, Theme.BG));
                    g2.fillRect(0, 0, w, h);
                } finally {
                    g2.dispose();
                }
            }
        };
        shade.setOpaque(false);
        add(shade, Integer.valueOf(1));

        copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));

        JLabel eyebrow = Theme.eyebrow(
                item.type() == Models.ShowType.MOVIE ? "PELÍCULA DESTACADA" : "SERIE DESTACADA");
        eyebrow.setForeground(new Color(220, 220, 224));
        eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = Theme.heading(
                "<html><body style='width:540px'>" + escape(item.title()) + "</body></html>",
                36f);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        String synopsis = item.overview() == null || item.overview().isBlank()
                ? "Descubre este título en Streamflix."
                : item.overview();

        JLabel description = Theme.muted(
                "<html><body style='width:540px'>" + escape(shorten(synopsis, 250)) + "</body></html>");
        description.setFont(Theme.FONT.deriveFont(14f));
        description.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton open = Theme.primaryButton("Ver detalles");
        open.setAlignmentX(Component.LEFT_ALIGNMENT);
        open.addActionListener(e -> onOpen.accept(item));

        copy.add(eyebrow);
        copy.add(Box.createVerticalStrut(9));
        copy.add(title);
        copy.add(Box.createVerticalStrut(12));
        copy.add(description);
        copy.add(Box.createVerticalStrut(18));
        copy.add(open);

        add(copy, Integer.valueOf(2));
        setBorder(BorderFactory.createLineBorder(new Color(32, 36, 44)));
    }

    @Override public void doLayout() {
        int w = getWidth();
        int h = getHeight();

        backdrop.setBounds(0, 0, w, h);
        shade.setBounds(0, 0, w, h);

        int cw = Math.min(590, Math.max(440, w / 2));
        int ch = Math.min(305, h - 58);
        copy.setBounds(36, Math.max(26, h - ch - 34), cw, ch);
    }

    private static String shorten(String value, int max) {
        if (value == null || value.length() <= max) return value == null ? "" : value;
        int cut = value.lastIndexOf(' ', max);
        if (cut < max / 2) cut = max;
        return value.substring(0, cut).strip() + "…";
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
