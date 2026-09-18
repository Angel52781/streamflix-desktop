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
        getRootPane().putClientProperty("JRootPane.titleBarBackground", Theme.SIDEBAR);
        getRootPane().putClientProperty("JRootPane.titleBarForeground", Theme.TEXT);
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
        if (item.rating() != null) meta.add(metaPill(String.format("%.1f / 10", item.rating())));
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
            episodeArea.revalidate();
            episodeArea.repaint();
            return;
        }

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(new EmptyBorder(0, 0, 12, 0));

        JLabel episodesTitle = Theme.heading("Episodios", 19f);
        episodesTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(episodesTitle);
        header.add(Box.createVerticalStrut(10));

        JPanel seasonTabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        seasonTabs.setOpaque(false);

        JScrollPane seasonScroll = new JScrollPane(seasonTabs);
        seasonScroll.setBorder(null);
        seasonScroll.setOpaque(false);
        seasonScroll.getViewport().setOpaque(false);
        seasonScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        seasonScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        seasonScroll.getHorizontalScrollBar().setUnitIncrement(24);
        seasonScroll.setPreferredSize(new Dimension(560, 48));
        seasonScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(seasonScroll);
        episodeArea.add(header, BorderLayout.NORTH);

        DefaultListModel<Models.Episode> model = new DefaultListModel<>();
        JList<Models.Episode> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(7);
        list.setFixedCellHeight(48);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(
                    JList<?> l, Object value, int index, boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(l, value, index, selected, focus);
                Models.Episode ep = (Models.Episode) value;
                String title = ep.title() == null || ep.title().isBlank()
                        ? "Episodio " + ep.episodeNumber() : ep.title();
                label.setText("<html><b>E" + ep.episodeNumber() + "</b>&nbsp;&nbsp; "
                        + escapeHtml(title) + "</html>");
                label.setBorder(new EmptyBorder(11, 13, 11, 13));
                label.setFont(Theme.FONT.deriveFont(13f));
                return label;
            }
        });

        Map<Integer, JButton> seasonButtons = new LinkedHashMap<>();
        java.util.function.IntConsumer selectSeason = selectedSeason -> {
            model.clear();
            seasons.getOrDefault(selectedSeason, List.of()).forEach(model::addElement);
            if (!model.isEmpty()) list.setSelectedIndex(0);
            for (var entry : seasonButtons.entrySet()) {
                Theme.setNavSelected(entry.getValue(), entry.getKey() == selectedSeason);
            }
        };

        for (int season : seasons.keySet()) {
            String label = season == 0 ? "Especiales" : "Temporada " + season;
            JButton button = Theme.button(label);
            seasonButtons.put(season, button);
            button.addActionListener(e -> selectSeason.accept(season));
            seasonTabs.add(button);
        }

        int initialSeason = seasons.containsKey(1) ? 1 : seasons.keySet().iterator().next();
        selectSeason.accept(initialSeason);

        JScrollPane episodeScroll = new JScrollPane(list);
        episodeScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        episodeScroll.setPreferredSize(new Dimension(560, 320));
        episodeArea.add(episodeScroll, BorderLayout.CENTER);

        JButton playEpisode = Theme.primaryButton("Reproducir episodio");
        playEpisode.addActionListener(e -> {
            Models.Episode ep = list.getSelectedValue();
            if (ep == null) return;
            chooseServerAndPlay(ep.id(),
                    item.title() + " · T" + ep.seasonNumber() + "E" + ep.episodeNumber(), true);
        });

        JButton selectServer = Theme.button("Elegir servidor");
        selectServer.addActionListener(e -> {
            Models.Episode ep = list.getSelectedValue();
            if (ep == null) return;
            chooseServerAndPlay(ep.id(),
                    item.title() + " · T" + ep.seasonNumber() + "E" + ep.episodeNumber(), false);
        });

        JPanel epButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        epButtons.setOpaque(false);
        epButtons.setBorder(new EmptyBorder(12, 0, 0, 0));
        epButtons.add(playEpisode);
        epButtons.add(selectServer);
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

        episodeArea.revalidate();
        episodeArea.repaint();
    }

    private void chooseServerAndPlay(String providerItemId, String mediaTitle, boolean autoPlay) {
        long playbackRequest = MpvPlayer.beginRequest();
        EmbeddedPlayerWindow preparingWindow = autoPlay ? EmbeddedPlayerWindow.open(this, mediaTitle) : null;
        if (preparingWindow != null) preparingWindow.setPreparing("Buscando servidores disponibles…");

        status.setText("Buscando servidores…");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<List<Models.Server>, Void>() {
            @Override protected List<Models.Server> doInBackground() throws Exception {
                return provider.servers(providerItemId);
            }

            @Override protected void done() {
                if (MpvPlayer.isShutdown() || !isDisplayable()) return;
                setCursor(Cursor.getDefaultCursor());
                try {
                    List<Models.Server> servers = get();
                    if (servers.isEmpty()) {
                        status.setText("Sin servidores disponibles");
                        if (preparingWindow != null) preparingWindow.showFailure("No hay servidores disponibles para este contenido.");
                        else showError("Sin servidores", new IllegalStateException(
                                "La fuente no devolvió servidores para este contenido."));
                        return;
                    }

                    if (autoPlay) {
                        resolveAnyAndPlay(servers, mediaTitle, playbackRequest, preparingWindow);
                        return;
                    }

                    ServerPickerDialog.Choice choice = ServerPickerDialog.choose(DetailDialog.this, servers);
                    if (choice == null) return;

                    EmbeddedPlayerWindow player = EmbeddedPlayerWindow.open(DetailDialog.this, mediaTitle);
                    if (choice.automatic()) {
                        resolveAnyAndPlay(servers, mediaTitle, playbackRequest, player);
                    } else {
                        resolveAndPlay(choice.server(), mediaTitle, playbackRequest, player);
                    }
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    status.setText("Error obteniendo servidores");
                    if (preparingWindow != null) preparingWindow.showFailure(
                            cause.getMessage() == null ? "No se pudieron obtener servidores." : cause.getMessage());
                    else showError("No se pudieron obtener servidores", cause);
                }
            }
        }.execute();
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

    private void resolveAnyAndPlay(List<Models.Server> servers, String mediaTitle,
                                   long playbackRequest, EmbeddedPlayerWindow player) {
        status.setText("Buscando un servidor compatible…");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        new SwingWorker<Models.Server, Void>() {
            @Override protected Models.Server doInBackground() throws Exception {
                Exception last = null;
                for (Models.Server server : servers) {
                    if (!player.isDisplayable()) {
                        throw new CancellationException("Reproductor cerrado.");
                    }
                    player.setPreparing("Probando " + server.name() + "…");
                    try {
                        Models.Video video = extractors.resolve(server);
                        if (video == null || video.source() == null || video.source().isBlank()) {
                            throw new IllegalStateException("El servidor no devolvió un video reproducible.");
                        }
                        player.start(video, server.name(), playbackRequest);
                        return server;
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw ex;
                    } catch (CancellationException ex) {
                        throw ex;
                    } catch (Exception ex) {
                        last = ex;
                    }
                }
                throw new IllegalStateException("Ningún servidor disponible pudo iniciar la reproducción.", last);
            }

            @Override protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    Models.Server server = get();
                    UserData.recordHistory(provider.id(), item);
                    status.setText("Reproduciendo · " + server.name());
                } catch (Exception ex) {
                    Throwable cause = ex instanceof ExecutionException && ex.getCause() != null
                            ? ex.getCause() : ex;
                    status.setText("No se pudo reproducir");
                    if (player.isDisplayable()) {
                        player.showFailure(cause.getMessage() == null
                                ? "No se encontró un servidor compatible." : cause.getMessage());
                    }
                }
            }
        }.execute();
    }

    private void resolveAndPlay(Models.Server server, String mediaTitle,
                                long playbackRequest, EmbeddedPlayerWindow player) {
        status.setText("Preparando " + server.name() + "…");
        player.setPreparing("Preparando " + server.name() + "…");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                Models.Video video = extractors.resolve(server);
                if (video == null || video.source() == null || video.source().isBlank()) {
                    throw new IllegalStateException("El servidor no devolvió un video reproducible.");
                }
                player.start(video, server.name(), playbackRequest);
                return null;
            }

            @Override protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    get();
                    UserData.recordHistory(provider.id(), item);
                    status.setText("Reproduciendo · " + server.name());
                } catch (Exception ex) {
                    Throwable cause = ex instanceof ExecutionException && ex.getCause() != null
                            ? ex.getCause() : ex;
                    status.setText("No se pudo reproducir");
                    if (player.isDisplayable()) {
                        player.showFailure(cause.getMessage() == null
                                ? "No se pudo iniciar la reproducción." : cause.getMessage());
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
