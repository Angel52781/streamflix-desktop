package dev.streamflix.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;

/** Wide artwork card for home rails, closer to contemporary streaming UIs. */
final class LandscapeCard extends JPanel {
    private boolean hovered;
    private boolean focused;

    LandscapeCard(Models.ShowItem item, Consumer<Models.ShowItem> onOpen) {
        setLayout(new BorderLayout());
        setOpaque(false);
        setPreferredSize(new Dimension(292, 186));
        setMaximumSize(new Dimension(292, 186));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFocusable(true);

        String image = item.banner() != null && !item.banner().isBlank() ? item.banner() : item.poster();
        ArtworkPanel artwork = new ArtworkPanel(image);
        artwork.setPreferredSize(new Dimension(292, 164));

        JPanel text = new JPanel(new BorderLayout());
        text.setOpaque(false);
        text.setBorder(new EmptyBorder(8, 4, 0, 4));

        JLabel title = new JLabel(item.title());
        title.setForeground(Theme.TEXT);
        title.setFont(Theme.FONT_BOLD.deriveFont(13f));
        title.setToolTipText(item.title());
        text.add(title, BorderLayout.WEST);

        if (item.rating() != null && item.rating() > 0) {
            JLabel rating = Theme.muted(String.format("%.1f", item.rating()));
            rating.setFont(Theme.FONT_BOLD.deriveFont(11.5f));
            text.add(rating, BorderLayout.EAST);
        }

        add(artwork, BorderLayout.CENTER);
        add(text, BorderLayout.SOUTH);

        MouseAdapter mouse = new MouseAdapter() {
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

        addMouseListener(mouse);
        artwork.addMouseListener(mouse);
        text.addMouseListener(mouse);
        title.addMouseListener(mouse);

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
                g2.setColor(new Color(28, 33, 44));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.setColor(focused ? Theme.ACCENT : Theme.BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            }
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
