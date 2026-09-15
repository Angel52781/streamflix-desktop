package dev.streamflix.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

final class DetailDialog extends JDialog {
    private final Provider provider;
    private final Models.ShowItem item;
    private final ExtractorRegistry extractors = new ExtractorRegistry();
    private final JLabel status = Theme.muted(" ");
    private final JPanel episodeArea = new JPanel(new BorderLayout(8, 8));
    private List<Models.Episode> episodes = List.of();

    DetailDialog(Window owner, Provider provider, Models.ShowItem item) {
        super(owner, item.title(), ModalityType.MODELESS);
        this.provider = provider;
        this.item = item;
        setSize(900, 640);
        setMinimumSize(new Dimension(760, 560));
        setLocationRelativeTo(owner);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(buildTop(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
        if (item.type() == Models.ShowType.TV_SHOW) loadEpisodes();
    }

    private JComponent buildTop() {
        JPanel top = new JPanel(new BorderLayout(14, 0));
        top.setBackground(Theme.PANEL);
        top.setBorder(new EmptyBorder(14, 18, 14, 18));
        JLabel title = new JLabel(item.title());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        top.add(title, BorderLayout.CENTER);
        JLabel badge = new JLabel(item.type() == Models.ShowType.MOVIE ? "PELÍCULA" : "SERIE");
        badge.setForeground(Theme.ACCENT);
        badge.setFont(badge.getFont().deriveFont(Font.BOLD, 12f));
        top.add(badge, BorderLayout.EAST);
        return top;
    }

    private JComponent buildBody() {
        JPanel body = new JPanel(new BorderLayout(18, 18));
        body.setBackground(Theme.BG);
        body.setBorder(new EmptyBorder(18, 18, 18, 18));

        JLabel poster = new JLabel("Sin poster", SwingConstants.CENTER);
        poster.setPreferredSize(new Dimension(240, 355));
        poster.setOpaque(true);
        poster.setBackground(Theme.PANEL_ALT);
        poster.setForeground(Theme.MUTED);
        ImageLoader.load(item.poster(), poster, 240, 355);
        body.add(poster, BorderLayout.WEST);

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.add(metadataLine());
        info.add(Box.createVerticalStrut(14));

        JTextArea overview = new JTextArea(item.overview() == null || item.overview().isBlank()
                ? "Sin descripción disponible." : item.overview());
        overview.setWrapStyleWord(true);
        overview.setLineWrap(true);
        overview.setEditable(false);
        overview.setOpaque(false);
        overview.setForeground(Theme.TEXT);
        overview.setFont(overview.getFont().deriveFont(14f));
        overview.setRows(6);
        info.add(overview);
        info.add(Box.createVerticalStrut(16));

        if (item.type() == Models.ShowType.MOVIE) {
            JButton play = Theme.button("▶ Reproducir");
            play.setAlignmentX(Component.LEFT_ALIGNMENT);
            play.addActionListener(e -> chooseServerAndPlay(item.providerId(), item.title()));
            info.add(play);
        } else {
            episodeArea.setOpaque(false);
            JLabel loading = Theme.muted("Cargando episodios…");
            episodeArea.add(loading, BorderLayout.CENTER);
            info.add(episodeArea);
        }

        body.add(info, BorderLayout.CENTER);
        return new JScrollPane(body) {{
            setBorder(null);
            getVerticalScrollBar().setUnitIncrement(18);
        }};
    }

    private JComponent metadataLine() {
        JPanel meta = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        meta.setOpaque(false);
        if (item.released() != null) meta.add(Theme.muted(item.released()));
        if (item.runtimeMinutes() != null) meta.add(Theme.muted(item.runtimeMinutes() + " min"));
        if (item.rating() != null) meta.add(Theme.muted("★ " + String.format("%.1f", item.rating())));
        return meta;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(Theme.PANEL);
        footer.setBorder(new EmptyBorder(9, 14, 9, 14));
        footer.add(status, BorderLayout.WEST);
        JLabel mpv = Theme.muted(MpvPlayer.isAvailable() ? "mpv detectado" : "mpv no detectado — se avisará al reproducir");
        footer.add(mpv, BorderLayout.EAST);
        return footer;
    }

    private void loadEpisodes() {
        status.setText("Cargando episodios…");
        new SwingWorker<List<Models.Episode>, Void>() {
            @Override protected List<Models.Episode> doInBackground() throws Exception { return provider.episodes(item); }
            @Override protected void done() {
                try {
                    episodes = get();
                    renderEpisodes();
                    status.setText(episodes.size() + " episodios");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    episodeArea.removeAll();
                    episodeArea.add(errorLabel(cause), BorderLayout.CENTER);
                    episodeArea.revalidate(); episodeArea.repaint();
                    status.setText("Error cargando episodios");
                }
            }
        }.execute();
    }

    private void renderEpisodes() {
        episodeArea.removeAll();
        Map<Integer, List<Models.Episode>> seasons = episodes.stream()
                .collect(Collectors.groupingBy(Models.Episode::seasonNumber, TreeMap::new, Collectors.toList()));
        if (seasons.isEmpty()) {
            episodeArea.add(Theme.muted("No hay episodios disponibles."), BorderLayout.CENTER);
            episodeArea.revalidate(); episodeArea.repaint();
            return;
        }

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        controls.add(new JLabel("Temporada:"));
        JComboBox<Integer> seasonBox = new JComboBox<>(seasons.keySet().toArray(Integer[]::new));
        controls.add(seasonBox);
        episodeArea.add(controls, BorderLayout.NORTH);

        DefaultListModel<Models.Episode> model = new DefaultListModel<>();
        JList<Models.Episode> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(l, value, index, selected, focus);
                Models.Episode ep = (Models.Episode) value;
                String title = ep.title() == null || ep.title().isBlank() ? "Episodio " + ep.episodeNumber() : ep.title();
                label.setText("E" + ep.episodeNumber() + "  ·  " + title);
                label.setBorder(new EmptyBorder(8,8,8,8));
                return label;
            }
        });

        Runnable populate = () -> {
            Integer selected = (Integer) seasonBox.getSelectedItem();
            model.clear();
            if (selected != null) seasons.getOrDefault(selected, List.of()).forEach(model::addElement);
            if (!model.isEmpty()) list.setSelectedIndex(0);
        };
        seasonBox.addActionListener(e -> populate.run());
        populate.run();

        episodeArea.add(new JScrollPane(list), BorderLayout.CENTER);
        JButton playEpisode = Theme.button("▶ Reproducir episodio seleccionado");
        playEpisode.addActionListener(e -> {
            Models.Episode ep = list.getSelectedValue();
            if (ep == null) return;
            chooseServerAndPlay(ep.id(), item.title() + " · T" + ep.seasonNumber() + "E" + ep.episodeNumber());
        });
        episodeArea.add(playEpisode, BorderLayout.SOUTH);
        episodeArea.revalidate(); episodeArea.repaint();
    }

    private void chooseServerAndPlay(int providerItemId, String mediaTitle) {
        status.setText("Buscando servidores…");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<List<Models.Server>, Void>() {
            @Override protected List<Models.Server> doInBackground() throws Exception { return provider.servers(providerItemId); }
            @Override protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    List<Models.Server> servers = get();
                    if (servers.isEmpty()) {
                        status.setText("Sin servidores");
                        JOptionPane.showMessageDialog(DetailDialog.this, "El provider no devolvió servidores para este contenido.", "Sin servidores", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    Models.Server selected = selectServer(servers);
                    if (selected == null) return;
                    if ("__auto__".equals(selected.id())) resolveAnyAndPlay(servers, mediaTitle);
                    else resolveAndPlay(selected, mediaTitle);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    status.setText("Error obteniendo servidores");
                    showError("No se pudieron obtener servidores", cause);
                }
            }
        }.execute();
    }

    private Models.Server selectServer(List<Models.Server> servers) {
        String automatic = "Automático (probar servidores compatibles)";
        String[] labels = new String[servers.size() + 1];
        labels[0] = automatic;
        for (int i = 0; i < servers.size(); i++) labels[i + 1] = servers.get(i).name();
        String selected = (String) JOptionPane.showInputDialog(
                this, "Selecciona un servidor:", "Servidor",
                JOptionPane.PLAIN_MESSAGE, null, labels, labels[0]
        );
        if (selected == null) return null;
        if (Objects.equals(selected, automatic)) return new Models.Server("__auto__", automatic, "");
        for (int i = 0; i < servers.size(); i++) if (Objects.equals(servers.get(i).name(), selected)) return servers.get(i);
        return null;
    }

    private record ResolvedMedia(Models.Server server, Models.Video video) {}

    private void resolveAnyAndPlay(List<Models.Server> servers, String mediaTitle) {
        status.setText("Buscando un servidor compatible…");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<ResolvedMedia, Void>() {
            @Override protected ResolvedMedia doInBackground() throws Exception {
                Exception last = null;
                for (Models.Server server : servers) {
                    try {
                        Models.Video video = extractors.resolve(server);
                        if (video.source() != null && !video.source().isBlank()) return new ResolvedMedia(server, video);
                    } catch (Exception ex) { last = ex; }
                }
                throw new IllegalStateException("Ningún servidor disponible pudo resolverse.", last);
            }
            @Override protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    ResolvedMedia resolved = get();
                    MpvPlayer.play(resolved.video(), mediaTitle);
                    status.setText("Reproduciendo ÷ " + resolved.server().name());
                } catch (Exception ex) {
                    Throwable cause = ex instanceof ExecutionException && ex.getCause() != null ? ex.getCause() : ex;
                    status.setText("No se pudo reproducir");
                    showError("No se encontró un servidor compatible", cause);
                }
            }
        }.execute();
    }

    private void resolveAndPlay(Models.Server server, String mediaTitle) {
        status.setText("Resolviendo " + server.name() + "…");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<Models.Video, Void>() {
            @Override protected Models.Video doInBackground() throws Exception { return extractors.resolve(server); }
            @Override protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    Models.Video video = get();
                    MpvPlayer.play(video, mediaTitle);
                    status.setText("Reproduciendo en mpv");
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    if (cause instanceof UnsupportedOperationException) {
                        fallbackPrompt(server, cause.getMessage());
                    } else {
                        fallbackPrompt(server, "El extractor falló: " + (cause == null ? ex.getMessage() : cause.getMessage()));
                    }
                } catch (Exception ex) {
                    if (ex instanceof IllegalStateException && ex.getMessage() != null && ex.getMessage().contains("mpv")) {
                        status.setText("mpv requerido");
                        showError("mpv no está disponible", ex);
                    } else {
                        fallbackPrompt(server, ex.getMessage());
                    }
                }
            }
        }.execute();
    }

    private void fallbackPrompt(Models.Server server, String reason) {
        status.setText("Servidor aún sin extractor desktop");
        int result = JOptionPane.showConfirmDialog(
                this,
                (reason == null ? "Este servidor aún no tiene extractor desktop." : reason)
                        + "\n\n¿Abrir el embed original en el navegador como fallback?",
                "Extractor no disponible",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (result == JOptionPane.YES_OPTION) {
            try { ExtractorRegistry.openFallback(server); status.setText("Embed abierto en navegador"); }
            catch (Exception ex) { showError("No se pudo abrir el navegador", ex); }
        }
    }

    private JLabel errorLabel(Throwable ex) {
        JLabel label = new JLabel("Error: " + (ex.getMessage() == null ? ex.toString() : ex.getMessage()));
        label.setForeground(new Color(255, 120, 120));
        return label;
    }

    private void showError(String title, Throwable ex) {
        JOptionPane.showMessageDialog(this,
                ex.getMessage() == null ? ex.toString() : ex.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }
}
