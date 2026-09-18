package dev.streamflix.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CancellationException;
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
        setSize(1040, 720);
        setMinimumSize(new Dimension(880, 620));
        setLocationRelativeTo(owner);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(buildBody(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
        if (item.type() == Models.ShowType.TV_SHOW) loadEpisodes();
    }

    private JComponent buildBody() {
        JPanel body = new JPanel(new BorderLayout(30, 0));
        body.setBackground(Theme.BG);
        body.setBorder(new EmptyBorder(30, 32, 28, 32));

        JLabel poster = new JLabel("Sin imagen", SwingConstants.CENTER);
        poster.setPreferredSize(new Dimension(260, 390));
        poster.setMinimumSize(new Dimension(220, 330));
        poster.setOpaque(true);
        poster.setBackground(Theme.PANEL_ALT);
        poster.setForeground(Theme.MUTED);
        poster.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        ImageLoader.load(item.poster(), poster, 260, 390);
        body.add(poster, BorderLayout.WEST);

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

        JLabel source = Theme.eyebrow(provider.name() + "  ·  "
                + (item.type() == Models.ShowType.MOVIE ? "PELÍCULA" : "SERIE"));
        source.setForeground(Theme.ACCENT);
        source.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(source);
        info.add(Box.createVerticalStrut(8));

        JLabel title = Theme.heading("<html><body style='width:590px'>" + escapeHtml(item.title()) + "</body></html>", 31f);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(title);
        info.add(Box.createVerticalStrut(10));

        JComponent metadata = metadataLine();
        metadata.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(metadata);
        info.add(Box.createVerticalStrut(18));

        JTextArea overview = new JTextArea(item.overview() == null || item.overview().isBlank()
                ? "Sin descripción disponible." : item.overview());
        overview.setWrapStyleWord(true);
        overview.setLineWrap(true);
        overview.setEditable(false);
        overview.setOpaque(false);
        overview.setForeground(Theme.MUTED);
        overview.setFont(Theme.FONT.deriveFont(14.5f));
        overview.setRows(6);
        overview.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        overview.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(overview);
        info.add(Box.createVerticalStrut(20));

        JButton favorite = Theme.button(UserData.isFavorite(provider.id(), item.id())
                ? "Quitar de favoritos" : "Añadir a favoritos");
        favorite.addActionListener(e -> {
            UserData.toggleFavorite(provider.id(), item);
            favorite.setText(UserData.isFavorite(provider.id(), item.id())
                    ? "Quitar de favoritos" : "Añadir a favoritos");
        });

        if (item.type() == Models.ShowType.MOVIE) {
            JButton play = Theme.primaryButton("Reproducir");
            play.addActionListener(e -> chooseServerAndPlay(item.providerId(), item.title(), true));

            JButton selectServer = Theme.button("Elegir servidor");
            selectServer.addActionListener(e -> chooseServerAndPlay(item.providerId(), item.title(), false));

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            actions.setOpaque(false);
            actions.setAlignmentX(Component.LEFT_ALIGNMENT);
            actions.add(play);
            actions.add(selectServer);
            actions.add(favorite);
            info.add(actions);
        } else {
            JPanel seriesActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            seriesActions.setOpaque(false);
            seriesActions.setAlignmentX(Component.LEFT_ALIGNMENT);
            seriesActions.add(favorite);
            info.add(seriesActions);
            info.add(Box.createVerticalStrut(24));

            episodeArea.setOpaque(false);
            episodeArea.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel loading = Theme.muted("Cargando episodios…");
            episodeArea.add(loading, BorderLayout.CENTER);
            info.add(episodeArea);
        }

        JPanel infoWrapper = new JPanel(new BorderLayout());
        infoWrapper.setOpaque(false);
        infoWrapper.add(info, BorderLayout.NORTH);
        body.add(infoWrapper, BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.BG);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    private JComponent metadataLine() {
        JPanel meta = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        meta.setOpaque(false);
        if (item.released() != null) meta.add(metaPill(item.released()));
        if (item.runtimeMinutes() != null) meta.add(metaPill(item.runtimeMinutes() + " min"));
        if (item.rating() != null) meta.add(metaPill("★ " + String.format("%.1f", item.rating())));
        return meta;
    }

    private JLabel metaPill(String text) {
        JLabel label = Theme.muted(text);
        label.setFont(Theme.FONT_BOLD.deriveFont(12f));
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                new EmptyBorder(5, 9, 5, 9)));
        return label;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(Theme.SIDEBAR);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                new EmptyBorder(9, 18, 9, 18)));
        status.setFont(Theme.FONT.deriveFont(12f));
        footer.add(status, BorderLayout.WEST);
        JLabel mpv = Theme.muted(MpvPlayer.isAvailable() ? "Reproductor listo" : "mpv no disponible");
        mpv.setFont(Theme.FONT.deriveFont(11.5f));
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

        JPanel controls = new JPanel(new BorderLayout(12, 0));
        controls.setOpaque(false);
        controls.setBorder(new EmptyBorder(0, 0, 10, 0));

        JLabel episodesTitle = Theme.heading("Episodios", 18f);
        controls.add(episodesTitle, BorderLayout.WEST);

        JPanel seasonControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        seasonControls.setOpaque(false);
        JLabel seasonLabel = Theme.muted("Temporada");
        seasonLabel.setFont(Theme.FONT.deriveFont(12f));
        JComboBox<Integer> seasonBox = new JComboBox<>(seasons.keySet().toArray(Integer[]::new));
        seasonBox.setPreferredSize(new Dimension(100, 34));
        seasonControls.add(seasonLabel);
        seasonControls.add(seasonBox);
        controls.add(seasonControls, BorderLayout.EAST);
        episodeArea.add(controls, BorderLayout.NORTH);

        DefaultListModel<Models.Episode> model = new DefaultListModel<>();
        JList<Models.Episode> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(l, value, index, selected, focus);
                Models.Episode ep = (Models.Episode) value;
                String title = ep.title() == null || ep.title().isBlank() ? "Episodio " + ep.episodeNumber() : ep.title();
                label.setText("<html><b>E" + ep.episodeNumber() + "</b>&nbsp;&nbsp; " + escapeHtml(title) + "</html>");
                label.setBorder(new EmptyBorder(10, 12, 10, 12));
                label.setFont(Theme.FONT.deriveFont(13f));
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

        list.setVisibleRowCount(7);
        list.setFixedCellHeight(42);
        JScrollPane episodeScroll = new JScrollPane(list);
        episodeScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        episodeScroll.setPreferredSize(new Dimension(560, 300));
        episodeArea.add(episodeScroll, BorderLayout.CENTER);
        
        JButton playEpisode = Theme.primaryButton("Reproducir episodio");
        playEpisode.addActionListener(e -> {
            Models.Episode ep = list.getSelectedValue();
            if (ep == null) return;
            chooseServerAndPlay(ep.id(), item.title() + " · T" + ep.seasonNumber() + "E" + ep.episodeNumber(), true);
        });
        
        JButton selectServer = Theme.button("Elegir servidor");
        selectServer.addActionListener(e -> {
            Models.Episode ep = list.getSelectedValue();
            if (ep == null) return;
            chooseServerAndPlay(ep.id(), item.title() + " · T" + ep.seasonNumber() + "E" + ep.episodeNumber(), false);
        });
        
        JPanel epButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        epButtons.setOpaque(false);
        epButtons.add(playEpisode);
        epButtons.add(Box.createHorizontalStrut(10));
        epButtons.add(selectServer);
        
        epButtons.setBorder(new EmptyBorder(12, 0, 0, 0));
        episodeArea.add(epButtons, BorderLayout.SOUTH);

        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && list.getSelectedValue() != null) {
                    Models.Episode ep = list.getSelectedValue();
                    chooseServerAndPlay(ep.id(),
                            item.title() + " · T" + ep.seasonNumber() + "E" + ep.episodeNumber(), true);
                }
            }
        });

        episodeArea.revalidate(); episodeArea.repaint();
    }

    private void chooseServerAndPlay(String providerItemId, String mediaTitle, boolean autoPlay) {
        long playbackRequest = MpvPlayer.beginRequest();
        status.setText("Buscando servidores…");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<List<Models.Server>, Void>() {
            @Override protected List<Models.Server> doInBackground() throws Exception { return provider.servers(providerItemId); }
            @Override protected void done() {
                if (MpvPlayer.isShutdown() || !isDisplayable()) return;
                setCursor(Cursor.getDefaultCursor());
                try {
                    List<Models.Server> servers = get();
                    if (servers.isEmpty()) {
                        status.setText("Sin servidores");
                        JOptionPane.showMessageDialog(DetailDialog.this, "El provider no devolvió servidores para este contenido.", "Sin servidores", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    Models.Server selected;
                    if (autoPlay) {
                        resolveAnyAndPlay(servers, mediaTitle, playbackRequest);
                        return;
                    } else {
                        selected = selectServer(servers);
                    }
                    
                    if (selected == null) return;
                    if ("__auto__".equals(selected.id())) resolveAnyAndPlay(servers, mediaTitle, playbackRequest);
                    else resolveAndPlay(selected, mediaTitle, playbackRequest);
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

    @FunctionalInterface
    interface VideoResolver { Models.Video resolve(Models.Server server) throws Exception; }

    @FunctionalInterface
    interface VideoStarter { void start(Models.Video video, String title) throws Exception; }

    // Shared by the Swing worker and headless fixtures. Success includes player startup.
    static Models.Server startFirstAvailable(List<Models.Server> servers, String title,
                                            VideoResolver resolver, VideoStarter starter) throws Exception {
        Exception last = null;
        for (Models.Server server : servers) {
            try {
                resolveAndStart(server, title, resolver, starter);
                return server;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw ex;
            } catch (CancellationException ex) {
                throw ex;
            } catch (Exception ex) { last = ex; }
        }
        throw new IllegalStateException("Ning\u00fan servidor disponible pudo iniciar la reproducci\u00f3n.", last);
    }

    static void resolveAndStart(Models.Server server, String title,
                                VideoResolver resolver, VideoStarter starter) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        Models.Video video = resolver.resolve(server);
        if (video == null || video.source() == null || video.source().isBlank()) {
            throw new IllegalStateException("El servidor no devolvi\u00f3 un video reproducible.");
        }
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        starter.start(video, title);
    }

    private void resolveAnyAndPlay(List<Models.Server> servers, String mediaTitle, long playbackRequest) {
        status.setText("Buscando un servidor compatible…");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<Models.Server, Void>() {
            @Override protected Models.Server doInBackground() throws Exception {
                return startFirstAvailable(servers, mediaTitle, extractors::resolve,
                        (video, title) -> MpvPlayer.play(video, title, playbackRequest));
            }
            @Override protected void done() {
                if (MpvPlayer.isShutdown() || !isDisplayable()) return;
                setCursor(Cursor.getDefaultCursor());
                try {
                    Models.Server server = get();
                    UserData.recordHistory(provider.id(), item);
                    status.setText("Reproduciendo \u2014 " + server.name());
                } catch (Exception ex) {
                    Throwable cause = ex instanceof ExecutionException && ex.getCause() != null ? ex.getCause() : ex;
                    status.setText("No se pudo reproducir");
                    showError("No se encontró un servidor compatible", cause);
                }
            }
        }.execute();
    }

    private void resolveAndPlay(Models.Server server, String mediaTitle, long playbackRequest) {
        status.setText("Resolviendo " + server.name() + "…");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                resolveAndStart(server, mediaTitle, extractors::resolve,
                        (video, title) -> MpvPlayer.play(video, title, playbackRequest));
                return null;
            }
            @Override protected void done() {
                if (MpvPlayer.isShutdown() || !isDisplayable()) return;
                setCursor(Cursor.getDefaultCursor());
                try {
                    get();
                    UserData.recordHistory(provider.id(), item);
                    status.setText("Reproduciendo \u2014 " + server.name());
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    if (cause instanceof IllegalStateException && cause.getMessage() != null && cause.getMessage().contains("mpv")) {
                        status.setText("mpv requerido");
                        showError("mpv no est\u00e1 disponible", cause);
                    } else if (cause instanceof UnsupportedOperationException) {
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

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
