package dev.streamflix.desktop;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;

/** Image-first 16:9 catalog card with streaming-style overlay metadata. */
final class LandscapeCard extends JLayeredPane {
    private final ArtworkPanel artwork;
    private final JPanel overlay;
    private boolean hovered;
    private boolean focused;

    LandscapeCard(Models.ShowItem item, Consumer<Models.ShowItem> onOpen) {
        setOpaque(false);
        setPreferredSize(new Dimension(300, 172));
        setMinimumSize(new Dimension(220, 126));
        setMaximumSize(new Dimension(380, 214));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFocusable(true);

        String image = item.banner() != null && !item.banner().isBlank()
                ? item.banner() : item.poster();
        artwork = new ArtworkPanel(image);
        artwork.setFallbackText(item.title());
        add(artwork, Integer.valueOf(0));

        overlay = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    int w = getWidth();
                    int h = getHeight();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    GradientPaint gradient = new GradientPaint(
                            0, Math.max(0, h / 3), new Color(0, 0, 0, 0),
                            0, h, new Color(0, 0, 0, hovered || focused ? 235 : 188));
                    g2.setPaint(gradient);
                    g2.fillRoundRect(0, 0, w, h, 12, 12);

                    if (hovered || focused) {
                        g2.setColor(focused ? Theme.ACCENT : new Color(255, 255, 255, 55));
                        g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);
                    }
                } finally {
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        overlay.setOpaque(false);
        overlay.setLayout(new BorderLayout());
        add(overlay, Integer.valueOf(1));

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        copy.setBorder(BorderFactory.createEmptyBorder(0, 12, 10, 12));

        JLabel title = new JLabel(item.title());
        title.setForeground(Theme.TEXT);
        title.setFont(Theme.FONT_BOLD.deriveFont(13.5f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setToolTipText(item.title());
        copy.add(title);

        JPanel meta = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        meta.setOpaque(false);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (item.released() != null && !item.released().isBlank()) {
            String year = item.released().length() >= 4 ? item.released().substring(0, 4) : item.released();
            JLabel yearLabel = new JLabel(year);
            yearLabel.setForeground(new Color(205, 209, 216));
            yearLabel.setFont(Theme.FONT.deriveFont(11f));
            meta.add(yearLabel);
        }

        if (item.rating() != null && item.rating() > 0) {
            JLabel rating = new JLabel(String.format("%.1f", item.rating()));
            rating.setForeground(new Color(232, 211, 96));
            rating.setFont(Theme.FONT_BOLD.deriveFont(11f));
            meta.add(rating);
        }

        JLabel kind = new JLabel(item.type() == Models.ShowType.MOVIE ? "Película" : "Serie");
        kind.setForeground(new Color(178, 185, 198));
        kind.setFont(Theme.FONT.deriveFont(11f));
        meta.add(kind);

        copy.add(Box.createVerticalStrut(4));
        copy.add(meta);

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(copy, BorderLayout.SOUTH);
        overlay.add(south, BorderLayout.SOUTH);

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) onOpen.accept(item);
            }

            @Override public void mouseEntered(MouseEvent e) {
                hovered = true;
                repaintAll();
            }

            @Override public void mouseExited(MouseEvent e) {
                hovered = false;
                repaintAll();
            }
        };

        addMouseListener(mouse);
        artwork.addMouseListener(mouse);
        overlay.addMouseListener(mouse);
        copy.addMouseListener(mouse);
        title.addMouseListener(mouse);

        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                focused = true;
                repaintAll();
            }

            @Override public void focusLost(FocusEvent e) {
                focused = false;
                repaintAll();
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

    @Override public void doLayout() {
        int w = getWidth();
        int h = getHeight();
        artwork.setBounds(0, 0, w, h);
        overlay.setBounds(0, 0, w, h);
    }

    private void repaintAll() {
        repaint();
        overlay.repaint();
    }
}
