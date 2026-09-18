package dev.streamflix.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/** Lightweight, anchored server chooser for advanced/manual playback selection. */
final class ServerPickerMenu {
    record Choice(boolean automatic, Models.Server server) {}

    private ServerPickerMenu() {}

    static void show(Component invoker, List<Models.Server> servers, Consumer<Choice> onChoose) {
        if (invoker == null || servers == null || servers.isEmpty() || onChoose == null) return;

        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(Theme.PANEL);
        menu.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                new EmptyBorder(7, 7, 7, 7)));

        JPanel content = new JPanel();
        content.setOpaque(true);
        content.setBackground(Theme.PANEL);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(4, 4, 4, 4));

        JLabel heading = Theme.heading("Fuente de reproducción", 14f);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(heading);
        content.add(Box.createVerticalStrut(3));

        JLabel note = Theme.muted("Automático es la opción recomendada.");
        note.setFont(Theme.FONT.deriveFont(11.5f));
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(note);
        content.add(Box.createVerticalStrut(10));

        JButton automatic = Theme.primaryButton("Automático");
        automatic.setHorizontalAlignment(SwingConstants.LEFT);
        automatic.setAlignmentX(Component.LEFT_ALIGNMENT);
        automatic.setMaximumSize(new Dimension(330, 38));
        automatic.addActionListener(e -> {
            menu.setVisible(false);
            onChoose.accept(new Choice(true, null));
        });
        content.add(automatic);

        for (Models.Server server : servers) {
            content.add(Box.createVerticalStrut(6));
            JButton option = Theme.button(server.name());
            option.setHorizontalAlignment(SwingConstants.LEFT);
            option.setAlignmentX(Component.LEFT_ALIGNMENT);
            option.setMaximumSize(new Dimension(330, 38));
            option.addActionListener(e -> {
                menu.setVisible(false);
                onChoose.accept(new Choice(false, server));
            });
            content.add(option);
        }

        menu.add(content);
        Dimension preferred = menu.getPreferredSize();
        int x = Math.max(0, invoker.getWidth() - preferred.width);
        int y = invoker.getHeight() + 5;
        menu.show(invoker, x, y);
    }
}
