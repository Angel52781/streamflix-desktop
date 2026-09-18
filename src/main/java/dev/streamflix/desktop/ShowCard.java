package dev.streamflix.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

final class ShowCard extends JPanel {
    private boolean hovered;
    private boolean focused;

    ShowCard(Models.ShowItem item, Consumer<Models.ShowItem> onOpen) {
        setLayout(new BorderLayout(0, 10));
        setOpaque(false);
        setBorder(new EmptyBorder(4, 4, 8, 4));
        setPreferredSize(new Dimension(182, 316));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        PosterLabel poster = new PosterLabel();
        poster.setText("Cargando…");
        poster.setHorizontalAlignment(SwingConstants.CENTER);
        poster.setPreferredSize(new Dimension(174, 258));
        poster.setForeground(Theme.MUTED);
        ImageLoader.load(item.poster(), poster, 174, 258);
        add(poster, BorderLayout.CENTER);

        JPanel meta = new JPanel();
        meta.setOpaque(false);
        meta.setLayout(new BoxLayout(meta, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("<html><body style='width:166px'>" + escapeHtml(item.title()) + "</body></html>");
        title.setForeground(Theme.TEXT);
        title.setFont(Theme.FONT_BOLD.deriveFont(13.5f));
        title.setToolTipText(item.title());
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        meta.add(title);
        meta.add(Box.createVerticalStrut(5));

        JPanel detailsRow = new JPanel(new BorderLayout());
        detailsRow.setOpaque(false);
        detailsRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        String type = item.type() == Models.ShowType.MOVIE ? "Película" : "Serie";
        String year = year(item.released());
        JLabel details = Theme.muted(year.isBlank() ? type : year + " · " + type);
        details.setFont(Theme.FONT.deriveFont(11.5f));
        detailsRow.add(details, BorderLayout.WEST);

        if (item.rating() != null) {
            JLabel rating = new JLabel(String.format("%.1f", item.rating()));
            rating.setForeground(new Color(239, 195, 76));
            rating.setFont(Theme.FONT_BOLD.deriveFont(11.5f));
            detailsRow.add(rating, BorderLayout.EAST);
        }

        meta.add(detailsRow);
        add(meta, BorderLayout.SOUTH);

        MouseAdapter click = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) onOpen.accept(item);
            }
            @Override public void mouseEntered(MouseEvent e) {
                hovered = true;
                repaint();
            }
            @Override public void mouseExited(MouseEvent e) {
                hovered = false;
                repaint();
            }
        };

        addMouseListener(click);
        poster.addMouseListener(click);
        meta.addMouseListener(click);
        title.addMouseListener(click);

        setFocusable(true);
        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                focused = true;
                repaint();
            }
            @Override public void focusLost(FocusEvent e) {
                focused = false;
                repaint();
            }
        });

        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) {
                    onOpen.accept(item);
                }
            }
        });
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (hovered || focused) {
                g2.setColor(Theme.HOVER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.setColor(focused ? Theme.ACCENT : Theme.BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            }
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }

    private static String year(String released) {
        if (released == null || released.isBlank()) return "";
        String value = released.strip();
        return value.length() >= 4 ? value.substring(0, 4) : value;
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final class PosterLabel extends JLabel {
        PosterLabel() {
            setOpaque(false);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.PANEL_ALT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.clip(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
                super.paintComponent(g2);
            } finally {
                g2.dispose();
            }
        }
    }
}
