package dev.streamflix.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;

final class ShowCard extends JPanel {
    ShowCard(Models.ShowItem item, Consumer<Models.ShowItem> onOpen) {
        setLayout(new BorderLayout(0, 8));
        setBackground(Theme.PANEL);
        setBorder(new EmptyBorder(8,8,10,8));
        setPreferredSize(new Dimension(180, 305));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel poster = new JLabel("Cargando...", SwingConstants.CENTER);
        poster.setPreferredSize(new Dimension(164, 235));
        poster.setOpaque(true);
        poster.setBackground(Theme.PANEL_ALT);
        poster.setForeground(Theme.MUTED);
        add(poster, BorderLayout.CENTER);
        ImageLoader.load(item.poster(), poster, 164, 235);

        JPanel meta = new JPanel(new BorderLayout(0,3));
        meta.setOpaque(false);
        JLabel title = new JLabel(item.title() == null ? "" : item.title());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        title.setToolTipText(item.title());
        meta.add(title, BorderLayout.NORTH);
        String sub = item.released() == null ? (item.type() == Models.ShowType.MOVIE ? "Película" : "Serie")
                : item.released().substring(0, Math.min(4, item.released().length()));
        JLabel details = Theme.muted(sub + (item.rating() == null ? "" : "  ★ " + String.format("%.1f", item.rating())));
        details.setFont(details.getFont().deriveFont(11f));
        meta.add(details, BorderLayout.SOUTH);
        add(meta, BorderLayout.SOUTH);

        MouseAdapter click = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { onOpen.accept(item); }
            @Override public void mouseEntered(MouseEvent e) { setBackground(Theme.PANEL_ALT); repaint(); }
            @Override public void mouseExited(MouseEvent e) { setBackground(Theme.PANEL); repaint(); }
        };
        addMouseListener(click); poster.addMouseListener(click); meta.addMouseListener(click); title.addMouseListener(click);

        setFocusable(true);
        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { setBackground(Theme.PANEL_ALT); repaint(); }
            @Override public void focusLost(FocusEvent e) { setBackground(Theme.PANEL); repaint(); }
        });
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) {
                    onOpen.accept(item);
                }
            }
        });
    }

}
