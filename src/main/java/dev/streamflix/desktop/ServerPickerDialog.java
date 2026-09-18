package dev.streamflix.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/** Streamflix-styled server chooser used instead of Swing's generic input dialog. */
final class ServerPickerDialog extends JDialog {
    record Choice(boolean automatic, Models.Server server) {}

    private Choice choice;

    private ServerPickerDialog(Window owner, List<Models.Server> servers) {
        super(owner, "Elegir servidor", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setResizable(false);
        setContentPane(build(servers));
        pack();
        setLocationRelativeTo(owner);
    }

    static Choice choose(Window owner, List<Models.Server> servers) {
        ServerPickerDialog dialog = new ServerPickerDialog(owner, servers);
        dialog.setVisible(true);
        return dialog.choice;
    }

    private JComponent build(List<Models.Server> servers) {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(24, 26, 22, 26));
        root.setPreferredSize(new Dimension(470, Math.min(560, 190 + servers.size() * 54)));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel title = Theme.heading("¿Cómo quieres reproducir?", 23f);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = Theme.muted("Automático prueba las fuentes disponibles hasta encontrar una que inicie correctamente.");
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setFont(Theme.FONT.deriveFont(12.5f));

        header.add(title);
        header.add(Box.createVerticalStrut(5));
        header.add(subtitle);
        root.add(header, BorderLayout.NORTH);

        JPanel options = new JPanel();
        options.setOpaque(false);
        options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));
        options.setBorder(new EmptyBorder(20, 0, 10, 0));

        JButton automatic = Theme.primaryButton("Automático · recomendado");
        automatic.setAlignmentX(Component.LEFT_ALIGNMENT);
        automatic.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        automatic.addActionListener(e -> {
            choice = new Choice(true, null);
            dispose();
        });
        options.add(automatic);
        options.add(Box.createVerticalStrut(10));

        for (Models.Server server : servers) {
            JButton button = Theme.button(server.name());
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            button.addActionListener(e -> {
                choice = new Choice(false, server);
                dispose();
            });
            options.add(button);
            options.add(Box.createVerticalStrut(7));
        }

        JScrollPane scroll = new JScrollPane(options);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.BG);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        root.add(scroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(false);
        JButton cancel = Theme.button("Cancelar");
        cancel.addActionListener(e -> dispose());
        footer.add(cancel);
        root.add(footer, BorderLayout.SOUTH);
        return root;
    }
}
