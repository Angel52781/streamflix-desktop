package dev.streamflix.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;

/** Small local settings dialog. Secrets are never logged or echoed into status text. */
final class SettingsDialog extends JDialog {
    private final JPasswordField tmdbKey = new JPasswordField(34);
    private final JLabel status = Theme.muted(" ");

    SettingsDialog(Window owner) {
        super(owner, "Configuración", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setResizable(false);
        setContentPane(build());
        pack();
        setLocationRelativeTo(owner);
        loadCurrent();
    }

    private JComponent build() {
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(18, 20, 18, 20));

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 8, 12);
        JLabel label = new JLabel("TMDb API key");
        label.setForeground(Theme.TEXT);
        form.add(label, c);

        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        tmdbKey.setToolTipText("Se guarda localmente en Streamflix/settings.json");
        form.add(tmdbKey, c);

        c.gridx = 1;
        c.gridy = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        JCheckBox show = new JCheckBox("Mostrar");
        show.setOpaque(false);
        show.setForeground(Theme.MUTED);
        char echo = tmdbKey.getEchoChar();
        show.addActionListener(e -> tmdbKey.setEchoChar(show.isSelected() ? (char) 0 : echo));
        form.add(show, c);

        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        c.insets = new Insets(10, 0, 0, 0);
        JLabel help = Theme.muted("La variable STREAMFLIX_TMDB_API_KEY tiene prioridad sobre esta configuración local.");
        form.add(help, c);

        root.add(form, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(12, 0));
        bottom.setOpaque(false);
        bottom.add(status, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton cancel = Theme.button("Cancelar");
        cancel.addActionListener(e -> dispose());
        JButton save = Theme.button("Guardar");
        save.addActionListener(e -> save());
        actions.add(cancel);
        actions.add(save);
        bottom.add(actions, BorderLayout.EAST);
        root.add(bottom, BorderLayout.SOUTH);
        return root;
    }

    private void loadCurrent() {
        try {
            String key = TmdbSettings.localApiKey();
            tmdbKey.setText(key);
            if (TmdbSettings.environmentOverrideActive()) {
                status.setText("Hay una key configurada por entorno; seguirá teniendo prioridad.");
            }
        } catch (TmdbException ex) {
            status.setText(ex.getMessage());
        }
    }

    private void save() {
        char[] chars = tmdbKey.getPassword();
        try {
            TmdbSettings.saveApiKey(new String(chars));
            status.setText(TmdbSettings.environmentOverrideActive()
                    ? "Guardada localmente. La key del entorno sigue teniendo prioridad."
                    : "Configuración guardada.");
            dispose();
        } catch (TmdbException ex) {
            status.setText(ex.getMessage());
        } finally {
            Arrays.fill(chars, '\0');
        }
    }
}
