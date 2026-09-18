package dev.streamflix.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;

/** Local application settings. Secrets are never logged or echoed into status text. */
final class SettingsDialog extends JDialog {
    private static final String TMDB_API_URL = "https://www.themoviedb.org/settings/api";

    private final JPasswordField tmdbKey = new JPasswordField(40);
    private final JLabel status = Theme.muted(" ");
    private final JButton testButton = Theme.button("Probar credencial");
    private final JButton saveButton = Theme.primaryButton("Guardar");

    SettingsDialog(Window owner) {
        super(owner, "Configuración · Streamflix", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setResizable(false);
        setContentPane(build());
        pack();
        setLocationRelativeTo(owner);
        loadCurrent();
    }

    private JComponent build() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(26, 28, 24, 28));
        root.setPreferredSize(new Dimension(720, 480));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel title = Theme.heading("Configuración", 26f);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = Theme.muted("Conecta servicios externos sin guardar secretos dentro del proyecto.");
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setFont(Theme.FONT.deriveFont(13f));

        header.add(title);
        header.add(Box.createVerticalStrut(5));
        header.add(subtitle);
        root.add(header, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(22, 0, 18, 0));

        JPanel tmdb = Theme.surface();
        tmdb.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;

        c.gridy = 0;
        JLabel tmdbTitle = Theme.heading("The Movie Database (TMDb)", 18f);
        tmdb.add(tmdbTitle, c);

        c.gridy++;
        c.insets = new Insets(4, 0, 0, 0);
        JLabel free = new JLabel("API de desarrollador gratuita para uso no comercial");
        free.setForeground(new Color(104, 211, 145));
        free.setFont(Theme.FONT_BOLD.deriveFont(12f));
        tmdb.add(free, c);

        c.gridy++;
        c.insets = new Insets(12, 0, 0, 0);
        JLabel instructions = Theme.muted(
                "<html><body style='width:610px'>"
                + "1. Crea o inicia sesión en TMDb.<br>"
                + "2. Abre <b>Settings → API</b> y solicita una API key de desarrollador.<br>"
                + "3. Copia la <b>API Key (v3 auth)</b> o el <b>API Read Access Token</b>.<br>"
                + "4. Pégalo aquí y usa <b>Probar credencial</b> antes de guardar."
                + "</body></html>");
        tmdb.add(instructions, c);

        c.gridy++;
        c.insets = new Insets(17, 0, 0, 0);
        JLabel keyLabel = new JLabel("API key v3 o Read Access Token");
        keyLabel.setForeground(Theme.TEXT);
        keyLabel.setFont(Theme.FONT_BOLD.deriveFont(13f));
        tmdb.add(keyLabel, c);

        c.gridy++;
        c.insets = new Insets(7, 0, 0, 0);
        tmdbKey.putClientProperty("JTextField.placeholderText", "Pega aquí tu credencial de TMDb");
        tmdbKey.setToolTipText("Se guarda únicamente en %APPDATA%\\Streamflix\\settings.json");
        tmdb.add(tmdbKey, c);

        c.gridy++;
        c.insets = new Insets(7, 0, 0, 0);
        JPanel underField = new JPanel(new BorderLayout());
        underField.setOpaque(false);

        JCheckBox show = new JCheckBox("Mostrar credencial");
        show.setOpaque(false);
        show.setForeground(Theme.MUTED);
        show.setFont(Theme.FONT.deriveFont(12f));
        char echo = tmdbKey.getEchoChar();
        show.addActionListener(e -> tmdbKey.setEchoChar(show.isSelected() ? (char) 0 : echo));
        underField.add(show, BorderLayout.WEST);

        JLabel storage = Theme.muted("Guardado local · nunca se commitea al repositorio");
        storage.setFont(Theme.FONT.deriveFont(11.5f));
        underField.add(storage, BorderLayout.EAST);
        tmdb.add(underField, c);

        c.gridy++;
        c.insets = new Insets(18, 0, 0, 0);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);

        JButton openTmdb = Theme.button("Abrir página de TMDb");
        openTmdb.addActionListener(e -> openTmdbSite());
        testButton.addActionListener(e -> testCredential());

        actions.add(openTmdb);
        actions.add(testButton);
        tmdb.add(actions, c);

        c.gridy++;
        c.insets = new Insets(14, 0, 0, 0);
        status.setFont(Theme.FONT.deriveFont(12f));
        tmdb.add(status, c);

        c.gridy++;
        c.insets = new Insets(12, 0, 0, 0);
        JLabel attribution = Theme.muted(
                "<html><body style='width:610px'>"
                + "TMDb exige atribución en aplicaciones que usan sus datos. Streamflix incluirá el aviso correspondiente "
                + "en Créditos/Acerca de antes del release."
                + "</body></html>");
        attribution.setFont(Theme.FONT.deriveFont(11.5f));
        tmdb.add(attribution, c);

        body.add(tmdb, BorderLayout.CENTER);
        root.add(body, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);

        JButton close = Theme.button("Cerrar");
        close.addActionListener(e -> dispose());

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(close);
        saveButton.addActionListener(e -> save());
        right.add(saveButton);
        footer.add(right, BorderLayout.EAST);

        root.add(footer, BorderLayout.SOUTH);
        return root;
    }

    private void loadCurrent() {
        try {
            String key = TmdbSettings.localApiKey();
            tmdbKey.setText(key);
            if (TmdbSettings.environmentOverrideActive()) {
                status.setText("STREAMFLIX_TMDB_API_KEY está configurada y tiene prioridad sobre este campo.");
            } else if (!key.isBlank()) {
                status.setText("Hay una credencial local guardada. Puedes probarla o reemplazarla.");
            } else {
                status.setText("Aún no hay una credencial de TMDb configurada.");
            }
        } catch (TmdbException ex) {
            status.setForeground(Theme.DANGER);
            status.setText(ex.getMessage());
        }
    }

    private void testCredential() {
        char[] chars = tmdbKey.getPassword();
        String credential = new String(chars).strip();
        Arrays.fill(chars, '\0');

        if (credential.isBlank()) {
            status.setForeground(Theme.DANGER);
            status.setText("Pega una API key o Read Access Token antes de probar.");
            return;
        }

        setTesting(true);
        status.setForeground(Theme.MUTED);
        status.setText("Comprobando conexión con TMDb…");

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                TmdbClient client = new TmdbClient("en", () -> credential, new Http()::get);
                client.get("discover/movie", Map.of("page", "1"));
                return null;
            }

            @Override protected void done() {
                setTesting(false);
                try {
                    get();
                    status.setForeground(new Color(104, 211, 145));
                    status.setText("Conexión correcta. Esta credencial puede usar TMDb.");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    status.setForeground(Theme.DANGER);
                    status.setText(cause.getMessage() == null ? "TMDb rechazó la credencial." : cause.getMessage());
                }
            }
        }.execute();
    }

    private void setTesting(boolean testing) {
        testButton.setEnabled(!testing);
        saveButton.setEnabled(!testing);
        tmdbKey.setEnabled(!testing);
    }

    private void save() {
        char[] chars = tmdbKey.getPassword();
        try {
            TmdbSettings.saveApiKey(new String(chars));
            status.setForeground(new Color(104, 211, 145));
            status.setText(TmdbSettings.environmentOverrideActive()
                    ? "Guardada. La variable de entorno seguirá teniendo prioridad."
                    : "Configuración guardada.");
            dispose();
        } catch (TmdbException ex) {
            status.setForeground(Theme.DANGER);
            status.setText(ex.getMessage());
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    private void openTmdbSite() {
        try {
            if (!Desktop.isDesktopSupported()) throw new UnsupportedOperationException();
            Desktop.getDesktop().browse(URI.create(TMDB_API_URL));
            status.setForeground(Theme.MUTED);
            status.setText("Abrí la página de API de TMDb en tu navegador.");
        } catch (Exception ex) {
            status.setForeground(Theme.DANGER);
            status.setText("No pude abrir el navegador. Ve a themoviedb.org → Settings → API.");
        }
    }
}
