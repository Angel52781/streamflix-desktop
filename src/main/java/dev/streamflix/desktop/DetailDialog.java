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
        setUndecorated(true);
        GraphicsConfiguration gc = owner != null
                ? owner.getGraphicsConfiguration()
                : GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration();
        Rectangle screen = gc.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        int usableWidth = screen.width - insets.left - insets.right;
        int usableHeight = screen.height - insets.top - insets.bottom;
        setSize(Math.min(1450, Math.max(980, usableWidth - 56)),
                Math.min(820, Math.max(660, usableHeight - 36)));
        setMinimumSize(new Dimension(980, 660));
        setLocationRelativeTo(owner);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(buildBody(), BorderLayout.CENTER);
        installDismissBehavior(owner);
        if (item.type() == Models.ShowType.TV_SHOW) loadEpisodes();
    }

    private JComponent buildBody() {
        JLayeredPane shell = new JLayeredPane();
        shell.setOpaque(true);
        shell.setBackground(Theme.BG);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);

        JPanel content = new JPanel();
        content.setBackground(Theme.BG);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JComponent hero = buildHero();
        hero.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(hero);

        if (item.type() == Models.ShowType.TV_SHOW) {
            JPanel episodesWrap = new JPanel(new BorderLayout());
            episodesWrap.setOpaque(false);
            episodesWrap.setBorder(new EmptyBorder(22, 28, 26, 28));
            episodeArea.setOpaque(false);
            JLabel loading = Theme.muted("Cargando episodios…");
            loading.setBorder(new EmptyBorder(18, 0, 18, 0));
            episodeArea.add(loading, BorderLayout.CENTER);
            episodesWrap.add(episodeArea, BorderLayout.CENTER);
            episodesWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(episodesWrap);
        }

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.BG);
        scroll.getVerticalScrollBar().setUnitIncrement(26);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        root.add(scroll, BorderLayout.CENTER);

        JButton close = createFixedCloseButton();
        shell.add(root, Integer.valueOf(0));
        shell.add(close, Integer.valueOf(100));

        shell.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                layoutShell(shell, root, close);
            }
        });
        SwingUtilities.invokeLater(() -> layoutShell(shell, root, close));

        return shell;
    }

    private JButton createFixedCloseButton() {
        JButton close = new JButton("×") {
            private boolean hovered;

            {
                setFont(Theme.FONT.deriveFont(Font.PLAIN, 30f));
                setForeground(Color.WHITE);
                setBorderPainted(false);
                setContentAreaFilled(false);
                setFocusPainted(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setToolTipText("Cerrar (Esc)");
                addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                        hovered = true;
                        repaint();
                    }

                    @Override public void mouseExited(java.awt.event.MouseEvent e) {
                        hovered = false;
                        repaint();
                    }
                });
            }

            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(hovered ? new Color(255, 255, 255, 46) : new Color(0, 0, 0, 150));
                    g2.fillOval(2, 2, getWidth() - 4, getHeight() - 4);
                } finally {
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        close.addActionListener(e -> dispose());
        return close;
    }

    private static void layoutShell(JLayeredPane shell, Component content, Component close) {
        int w = shell.getWidth();
        int h = shell.getHeight();
        content.setBounds(0, 0, w, h);
        int size = 46;
        close.setBounds(Math.max(12, w - size - 18), 16, size, size);
    }

    private void installDismissBehavior(Window owner) {
        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_LEFT,
                        java.awt.event.InputEvent.ALT_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        if (owner != null) {
            addWindowFocusListener(new java.awt.event.WindowAdapter() {
                @Override public void windowLostFocus(java.awt.event.WindowEvent e) {
                    if (e.getOppositeWindow() == owner && isDisplayable()) {
                        dispose();
                    }
                }
            });
        }
    }

    private JComponent buildHero() {
        JLayeredPane hero = new JLayeredPane();
        hero.setPreferredSize(new Dimension(1160, 430));
        hero.setMaximumSize(new Dimension(Integer.MAX_VALUE, 430));
        hero.setOpaque(true);
        hero.setBackground(Color.BLACK);

        String heroImage = item.banner() != null && !item.banner().isBlank() ? item.banner() : item.poster();
        ArtworkPanel background = new ArtworkPanel(heroImage);
        background.setFallbackText("");
        hero.add(background, Integer.valueOf(0));

        JPanel shade = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    int w = getWidth();
                    int h = getHeight();
                    g2.setPaint(new GradientPaint(
                            0, 0, new Color(0, 0, 0, 210),
                            Math.max(1, (int) (w * 0.72)), 0, new Color(0, 0, 0, 30)));
                    g2.fillRect(0, 0, w, h);
                    g2.setPaint(new GradientPaint(
                            0, Math.max(1, (int) (h * 0.55)), new Color(0, 0, 0, 0),
                            0, h, Theme.BG));
                    g2.fillRect(0, 0, w, h);
                } finally {
                    g2.dispose();
                }
            }
        };
        shade.setOpaque(false);
        hero.add(shade, Integer.valueOf(1));

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));

        JLabel source = Theme.eyebrow(provider.name() + "  ·  "
                + (item.type() == Models.ShowType.MOVIE ? "PELÍCULA" : "SERIE"));
        source.setForeground(new Color(225, 225, 228));
        source.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = Theme.heading("<html><body style='width:690px'>"
                + escapeHtml(item.title()) + "</body></html>", 34f);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JComponent metadata = metadataLine();
        metadata.setAlignmentX(Component.LEFT_ALIGNMENT);

        String overviewText = item.overview() == null || item.overview().isBlank()
                ? "Sin descripción disponible." : item.overview();
        JLabel overview = Theme.muted("<html><body style='width:680px'>"
                + escapeHtml(shorten(overviewText, 330)) + "</body></html>");
        overview.setFont(Theme.FONT.deriveFont(14f));
        overview.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton favorite = Theme.button(UserData.isFavorite(provider.id(), item.id())
                ? "Quitar de favoritos" : "Añadir a favoritos");
        favorite.addActionListener(e -> {
            UserData.toggleFavorite(provider.id(), item);
            favorite.setText(UserData.isFavorite(provider.id(), item.id())
                    ? "Quitar de favoritos" : "Añadir a favoritos");
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (item.type() == Models.ShowType.MOVIE) {
            JButton play = Theme.primaryButton("Reproducir");
            play.addActionListener(e -> chooseServerAndPlay(item.providerId(), item.title(), true));
            JButton servers = Theme.button("Elegir servidor");
            servers.addActionListener(e -> chooseServerAndPlay(item.providerId(), item.title(), false));
            actions.add(play);
            actions.add(servers);
        }
        actions.add(favorite);

        copy.add(source);
        copy.add(Box.createVerticalStrut(8));
        copy.add(title);
        copy.add(Box.createVerticalStrut(10));
        copy.add(metadata);
        copy.add(Box.createVerticalStrut(14));
        copy.add(overview);
        copy.add(Box.createVerticalStrut(18));
        copy.add(actions);

        hero.add(copy, Integer.valueOf(2));

        hero.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                layoutHero(hero, background, shade, copy);
            }
        });
        SwingUtilities.invokeLater(() -> layoutHero(hero, background, shade, copy));
        return hero;
    }

    private static void layoutHero(JLayeredPane hero, Component background, Component shade,
                                   Component copy) {
        int w = hero.getWidth();
        int h = hero.getHeight();
        background.setBounds(0, 0, w, h);
        shade.setBounds(0, 0, w, h);
        copy.setBounds(28, Math.max(42, h - 300), Math.min(760, Math.max(620, w - 120)), 275);
    }

    private static String shorten(String value, int max) {
        if (value == null || value.length() <= max) return value == null ? "" : value;
        int cut = value.lastIndexOf(' ', max);
        if (cut < max / 2) cut = max;
        return value.substring(0, cut).strip() + "…";
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
                .collect(Collectors.groupingBy(
                        Models.Episode::seasonNumber,
                        TreeMap::new,
                        Collectors.toList()));

        if (seasons.isEmpty()) {
            episodeArea.add(Theme.muted("No hay episodios disponibles."), BorderLayout.CENTER);
            episodeArea.revalidate();
            episodeArea.repaint();
            return;
        }

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(new EmptyBorder(0, 0, 14, 0));

        JLabel episodesTitle = Theme.heading("Episodios", 21f);
        episodesTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(episodesTitle);
        header.add(Box.createVerticalStrut(12));

        JPanel seasonTabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        seasonTabs.setOpaque(false);

        JScrollPane seasonScroll = new JScrollPane(seasonTabs);
        seasonScroll.setBorder(null);
        seasonScroll.setOpaque(false);
        seasonScroll.getViewport().setOpaque(false);
        seasonScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        seasonScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        seasonScroll.getHorizontalScrollBar().setUnitIncrement(24);
        seasonScroll.setPreferredSize(new Dimension(760, 48));
        seasonScroll.setAlignmentX(Component.LEFT_ALIGNMENT);

        header.add(seasonScroll);
        episodeArea.add(header, BorderLayout.NORTH);

        JPanel rows = new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setBorder(new EmptyBorder(0, 0, 4, 0));

        episodeArea.add(rows, BorderLayout.CENTER);

        Map<Integer, JButton> seasonButtons = new LinkedHashMap<>();

        java.util.function.IntConsumer selectSeason = selectedSeason -> {
            rows.removeAll();

            for (Models.Episode episode : seasons.getOrDefault(selectedSeason, List.of())) {
                EpisodeRow row = new EpisodeRow(
                        episode,
                        ep -> chooseServerAndPlay(
                                ep.id(),
                                item.title() + " · T" + ep.seasonNumber() + "E" + ep.episodeNumber(),
                                true),
                        ep -> chooseServerAndPlay(
                                ep.id(),
                                item.title() + " · T" + ep.seasonNumber() + "E" + ep.episodeNumber(),
                                false)
                );
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                rows.add(row);
                rows.add(Box.createVerticalStrut(9));
            }

            for (var entry : seasonButtons.entrySet()) {
                Theme.setNavSelected(entry.getValue(), entry.getKey() == selectedSeason);
            }

            rows.revalidate();
            rows.repaint();
        };

        for (int season : seasons.keySet()) {
            String label = season == 0 ? "Especiales" : "Temporada " + season;
            JButton button = Theme.button(label);
            seasonButtons.put(season, button);
            button.addActionListener(e -> selectSeason.accept(season));
            seasonTabs.add(button);
        }

        int initialSeason = seasons.containsKey(1)
                ? 1
                : seasons.keySet().iterator().next();
        selectSeason.accept(initialSeason);

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
