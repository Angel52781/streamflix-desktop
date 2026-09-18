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
        getRootPane().putClientProperty("JRootPane.titleBarBackground", Theme.SIDEBAR);
        getRootPane().putClientProperty("JRootPane.titleBarForeground", Theme.TEXT);
        setResizable(true);
        setContentPane(build());
        pack();
        setMinimumSize(new Dimension(720, 560));
        setLocationRelativeTo(owner);
        loadCurrent();
    }

    private JComponent build() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(22, 24, 20, 24));
        root.setPreferredSize(new Dimension(780, 600));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel title = Theme.heading("Configuración", 26f);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = Theme.muted("Catálogos, servicios externos y reproducción.");
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setFont(Theme.FONT.deriveFont(13f));
        header.add(title);
        header.add(Box.createVerticalStrut(5));
        header.add(subtitle);
        root.add(header, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBorder(new EmptyBorder(18, 0, 12, 0));
        tabs.addTab("TMDb", buildTmdbPanel());
        tabs.addTab("Fuentes", buildSourcesPanel());
        tabs.addTab("Reproductor", buildPlayerPanel());
        root.add(tabs, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setOpaque(false);
        JButton close = Theme.button("Cerrar");
        close.addActionListener(e -> dispose());
        saveButton.addActionListener(e -> save());
        footer.add(close);
        footer.add(saveButton);
        root.add(footer, BorderLayout.SOUTH);
        return root;
    }

    private JComponent buildTmdbPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Theme.BG);
        wrapper.setBorder(new EmptyBorder(12, 4, 8, 4));

        JPanel tmdb = Theme.surface();
        tmdb.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;

        c.gridy = 0;
        JLabel title = Theme.heading("The Movie Database (TMDb)", 18f);
        tmdb.add(title, c);

        c.gridy++;
        c.insets = new Insets(4, 0, 0, 0);
        JLabel note = new JLabel("Una credencial sirve para TMDb ES y TMDb EN");
        note.setForeground(new Color(104, 211, 145));
        note.setFont(Theme.FONT_BOLD.deriveFont(12f));
        tmdb.add(note, c);

        c.gridy++;
        c.insets = new Insets(12, 0, 0, 0);
        JLabel explanation = Theme.muted(
                "<html><body style='width:640px'>"
                + "TMDb es el catálogo principal de metadata: títulos, pósters, temporadas y episodios. "
                + "No es el servidor de video. La misma API key v3 o Read Access Token funciona para español e inglés."
                + "</body></html>");
        tmdb.add(explanation, c);

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

        wrapper.add(tmdb, BorderLayout.NORTH);
        return wrapper;
    }

    private JComponent buildSourcesPanel() {
        JPanel root = new JPanel();
        root.setBackground(Theme.BG);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(new EmptyBorder(12, 4, 16, 4));

        JLabel explanation = Theme.muted(
                "<html><body style='width:650px'>"
                + "<b>Modelo actual:</b> Streamflix navega un catálogo a la vez. "
                + "El selector <b>Catálogo</b> o <b>Lista</b> de la pantalla principal cambia la fuente. "
                + "TMDb aporta metadata; su reproducción usa servidores compatibles detrás de escena. "
                + "Los catálogos alternativos incluyen su propia metadata y servidores."
                + "</body></html>");
        explanation.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(explanation);
        root.add(Box.createVerticalStrut(18));

        root.add(sourceGroup("Catálogos principales", ProviderRegistry.all().stream()
                .filter(TmdbProvider.class::isInstance).toList()));
        root.add(Box.createVerticalStrut(14));
        root.add(sourceGroup("Catálogos alternativos", ProviderRegistry.all().stream()
                .filter(p -> !(p instanceof TmdbProvider) && !(p instanceof M3uLiveProvider)).toList()));
        root.add(Box.createVerticalStrut(14));
        root.add(sourceGroup("TV en vivo", ProviderRegistry.all().stream()
                .filter(M3uLiveProvider.class::isInstance).toList()));

        JPanel viewport = new JPanel(new BorderLayout());
        viewport.setBackground(Theme.BG);
        viewport.add(root, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(viewport);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.BG);
        scroll.getVerticalScrollBar().setUnitIncrement(22);
        return scroll;
    }

    private JComponent sourceGroup(String title, java.util.List<Provider> providers) {
        JPanel group = Theme.surface();
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel heading = Theme.heading(title, 16f);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.add(heading);
        group.add(Box.createVerticalStrut(8));

        for (Provider provider : providers) {
            JPanel row = new JPanel(new BorderLayout(12, 0));
            row.setOpaque(false);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

            JLabel name = new JLabel(provider.name());
            name.setForeground(Theme.TEXT);
            name.setFont(Theme.FONT_BOLD.deriveFont(13f));
            row.add(name, BorderLayout.WEST);

            String capability = provider instanceof M3uLiveProvider
                    ? "Canales en vivo"
                    : provider.supportsMovies() && provider.supportsTvShows()
                        ? "Películas · Series"
                        : provider.supportsMovies() ? "Películas" : "Series";
            JLabel state = Theme.muted(capability + "   ·   Activo");
            state.setFont(Theme.FONT.deriveFont(11.5f));
            row.add(state, BorderLayout.EAST);

            group.add(row);
            group.add(Box.createVerticalStrut(5));
        }
        return group;
    }

    private JComponent buildPlayerPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Theme.BG);
        wrapper.setBorder(new EmptyBorder(12, 4, 8, 4));

        JPanel player = Theme.surface();
        player.setLayout(new BoxLayout(player, BoxLayout.Y_AXIS));

        JLabel heading = Theme.heading("Reproductor integrado", 18f);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        player.add(heading);
        player.add(Box.createVerticalStrut(8));

        JLabel engine = Theme.muted("Motor: mpv portable, renderizado dentro de Streamflix");
        engine.setAlignmentX(Component.LEFT_ALIGNMENT);
        player.add(engine);
        player.add(Box.createVerticalStrut(6));

        JLabel available = new JLabel(MpvPlayer.isAvailable()
                ? "Reproductor detectado y disponible"
                : "mpv no detectado");
        available.setForeground(MpvPlayer.isAvailable() ? new Color(104, 211, 145) : Theme.DANGER);
        available.setFont(Theme.FONT_BOLD.deriveFont(12f));
        available.setAlignmentX(Component.LEFT_ALIGNMENT);
        player.add(available);
        player.add(Box.createVerticalStrut(14));

        JLabel behavior = Theme.muted(
                "<html><body style='width:640px'>"
                + "Automático prueba servidores hasta que uno inicia realmente. "
                + "La interfaz de Streamflix controla pausa, salto temporal, volumen, pista de audio y subtítulos. "
                + "Preferencia de subtítulos: español → inglés."
                + "</body></html>");
        behavior.setAlignmentX(Component.LEFT_ALIGNMENT);
        player.add(behavior);

        wrapper.add(player, BorderLayout.NORTH);
        return wrapper;
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
                    status.setText("Conexión correcta. Esta credencial puede usar TMDb ES y EN.");
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
