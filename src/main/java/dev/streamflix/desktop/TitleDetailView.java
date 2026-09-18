package dev.streamflix.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/** In-app title detail route for the streaming navigation flow. */
final class TitleDetailView extends JPanel {
    private final Window owner;
    private final Provider provider;
    private final Models.ShowItem item;
    private final Runnable onBack;
    private final Runnable onHistoryChanged;
    private final boolean autoPlayRequested;
    private final ExtractorRegistry extractors = new ExtractorRegistry();

    private final JPanel episodeArea = new JPanel(new BorderLayout());
    private final JLabel status = Theme.muted(" ");
    private volatile List<Models.Episode> episodes = List.of();

    TitleDetailView(Window owner, Provider provider, Models.ShowItem item, Runnable onBack) {
        this(owner, provider, item, onBack, false, () -> {});
    }

    TitleDetailView(Window owner, Provider provider, Models.ShowItem item, Runnable onBack, boolean autoPlay) {
        this(owner, provider, item, onBack, autoPlay, () -> {});
    }

    TitleDetailView(Window owner, Provider provider, Models.ShowItem item, Runnable onBack,
                    boolean autoPlay, Runnable onHistoryChanged) {
        this.owner = owner;
        this.provider = provider;
        this.item = item;
        this.onBack = onBack;
        this.onHistoryChanged = onHistoryChanged == null ? () -> {} : onHistoryChanged;
        this.autoPlayRequested = autoPlay;

        setLayout(new BorderLayout());
        setBackground(Theme.BG);
        add(buildBody(), BorderLayout.CENTER);

        if (item.type() == Models.ShowType.TV_SHOW) {
            loadEpisodes();
        } else if (autoPlay) {
            SwingUtilities.invokeLater(() -> {
                UserData.HistoryEntry historyEntry = UserData.getHistoryEntry(provider.id(), item);
                double resumeAt = historyEntry != null
                        && historyEntry.progressSeconds() > 15
                        && historyEntry.durationSeconds() > 0
                        && historyEntry.progressSeconds() < historyEntry.durationSeconds() * 0.95
                        ? historyEntry.progressSeconds()
                        : 0.0;
                chooseServerAndPlay(item.providerId(), item.title(), true, null, resumeAt, null);
            });
        }
    }

    private JComponent buildBody() {
        JPanel content = new JPanel();
        content.setBackground(Theme.BG);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JComponent hero = buildHero();
        hero.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(hero);

        if (item.type() == Models.ShowType.TV_SHOW) {
            JPanel episodesWrap = new JPanel(new BorderLayout());
            episodesWrap.setOpaque(false);
            episodesWrap.setBorder(new EmptyBorder(18, 40, 46, 40));
            episodeArea.setOpaque(false);

            JLabel loading = Theme.muted("Cargando episodios…");
            loading.setBorder(new EmptyBorder(24, 0, 24, 0));
            episodeArea.add(loading, BorderLayout.CENTER);

            episodesWrap.add(episodeArea, BorderLayout.CENTER);
            episodesWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(episodesWrap);
        }

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.BG);
        scroll.getVerticalScrollBar().setUnitIncrement(30);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    private JComponent buildHero() {
        JLayeredPane hero = new JLayeredPane();
        hero.setPreferredSize(new Dimension(1200, 520));
        hero.setMaximumSize(new Dimension(Integer.MAX_VALUE, 520));
        hero.setOpaque(true);
        hero.setBackground(Color.BLACK);

        String image = item.banner() != null && !item.banner().isBlank()
                ? item.banner() : item.poster();
        ArtworkPanel background = new ArtworkPanel(image);
        background.setFallbackText("");
        hero.add(background, Integer.valueOf(0));

        JPanel shade = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    int w = getWidth();
                    int h = getHeight();
                    g2.setPaint(new GradientPaint(
                            0, 0, new Color(0, 0, 0, 220),
                            Math.max(1, (int) (w * 0.72)), 0, new Color(0, 0, 0, 24)));
                    g2.fillRect(0, 0, w, h);
                    g2.setPaint(new GradientPaint(
                            0, Math.max(1, (int) (h * 0.48)), new Color(0, 0, 0, 0),
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

        JLabel eyebrow = Theme.eyebrow(item.type() == Models.ShowType.MOVIE ? "PELÍCULA" : "SERIE");
        eyebrow.setForeground(new Color(224, 226, 230));
        eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = Theme.heading(
                "<html><body style='width:720px'>" + escapeHtml(item.title()) + "</body></html>", 38f);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel meta = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        meta.setOpaque(false);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (item.released() != null && !item.released().isBlank()) {
            String released = item.released().length() >= 4 ? item.released().substring(0, 4) : item.released();
            JLabel value = Theme.muted(released);
            value.setFont(Theme.FONT_BOLD.deriveFont(12.5f));
            meta.add(value);
        }
        if (item.rating() != null && item.rating() > 0) {
            JLabel rating = new JLabel(String.format("%.1f / 10", item.rating()));
            rating.setForeground(new Color(232, 211, 96));
            rating.setFont(Theme.FONT_BOLD.deriveFont(12.5f));
            meta.add(rating);
        }
        if (item.runtimeMinutes() != null && item.runtimeMinutes() > 0) {
            JLabel runtime = Theme.muted(item.runtimeMinutes() + " min");
            runtime.setFont(Theme.FONT.deriveFont(12.5f));
            meta.add(runtime);
        }

        String overviewText = item.overview() == null || item.overview().isBlank()
                ? "Sin descripción disponible."
                : item.overview();
        JLabel overview = Theme.muted(
                "<html><body style='width:710px'>" + escapeHtml(shorten(overviewText, 380)) + "</body></html>");
        overview.setForeground(new Color(220, 223, 228));
        overview.setFont(Theme.FONT.deriveFont(14.5f));
        overview.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);

        UserData.HistoryEntry historyEntry = UserData.getHistoryEntry(provider.id(), item);
        boolean resumable = historyEntry != null
                && historyEntry.progressSeconds() > 15
                && historyEntry.durationSeconds() > 0
                && historyEntry.progressSeconds() < historyEntry.durationSeconds() * 0.95;

        if (item.type() == Models.ShowType.MOVIE) {
            double resumeAt = resumable ? historyEntry.progressSeconds() : 0.0;
            JButton play = Theme.primaryButton(resumable
                    ? "▶  Continuar · " + formatTime(resumeAt)
                    : "▶  Reproducir");
            play.addActionListener(e -> chooseServerAndPlay(
                    item.providerId(), item.title(), true, null, resumeAt, null));
            actions.add(play);
        } else if (resumable
                && historyEntry.mediaId() != null
                && historyEntry.seasonNumber() != null
                && historyEntry.episodeNumber() != null) {
            Models.Episode resumeEpisode = new Models.Episode(
                    historyEntry.mediaId(),
                    historyEntry.seasonNumber(),
                    historyEntry.episodeNumber(),
                    historyEntry.mediaTitle(),
                    null,
                    null
            );
            String label = "▶  Continuar T" + historyEntry.seasonNumber()
                    + ":E" + historyEntry.episodeNumber();
            JButton continueButton = Theme.primaryButton(label);
            continueButton.addActionListener(e -> chooseServerAndPlay(
                    historyEntry.mediaId(),
                    historyEntry.mediaTitle() == null ? item.title() : historyEntry.mediaTitle(),
                    true,
                    resumeEpisode,
                    historyEntry.progressSeconds(),
                    null
            ));
            actions.add(continueButton);
        }

        JButton favorite = Theme.button(UserData.isFavorite(provider.id(), item.id())
                ? "✓  En mi lista" : "+  Mi lista");
        favorite.addActionListener(e -> {
            UserData.toggleFavorite(provider.id(), item);
            favorite.setText(UserData.isFavorite(provider.id(), item.id())
                    ? "✓  En mi lista" : "+  Mi lista");
        });
        actions.add(favorite);

        if (item.type() == Models.ShowType.MOVIE) {
            JButton servers = Theme.button("Opciones de reproducción");
            servers.addActionListener(e -> chooseServerAndPlay(
                    item.providerId(), item.title(), false, null, 0.0, servers));
            actions.add(servers);
        }

        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        status.setFont(Theme.FONT.deriveFont(11.5f));

        copy.add(eyebrow);
        copy.add(Box.createVerticalStrut(9));
        copy.add(title);
        copy.add(Box.createVerticalStrut(10));
        copy.add(meta);
        copy.add(Box.createVerticalStrut(15));
        copy.add(overview);
        copy.add(Box.createVerticalStrut(20));
        copy.add(actions);
        copy.add(Box.createVerticalStrut(10));
        copy.add(status);

        hero.add(copy, Integer.valueOf(2));

        JButton back = Theme.button("←  Volver");
        back.addActionListener(e -> onBack.run());
        hero.add(back, Integer.valueOf(3));

        hero.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                int w = hero.getWidth();
                int h = hero.getHeight();
                background.setBounds(0, 0, w, h);
                shade.setBounds(0, 0, w, h);
                copy.setBounds(40, Math.max(76, h - 360), Math.min(780, Math.max(600, w - 140)), 330);
                back.setBounds(30, 24, 105, 38);
            }
        });

        SwingUtilities.invokeLater(() -> {
            int w = hero.getWidth();
            int h = hero.getHeight();
            background.setBounds(0, 0, w, h);
            shade.setBounds(0, 0, w, h);
            copy.setBounds(40, Math.max(76, h - 360), Math.min(780, Math.max(600, w - 140)), 330);
            back.setBounds(30, 24, 105, 38);
        });

        return hero;
    }

    private void loadEpisodes() {
        new SwingWorker<List<Models.Episode>, Void>() {
            @Override protected List<Models.Episode> doInBackground() throws Exception {
                return provider.episodes(item);
            }

            @Override protected void done() {
                try {
                    episodes = get();
                    renderEpisodes();
                    if (autoPlayRequested) autoPlaySeries();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    episodeArea.removeAll();
                    episodeArea.add(errorLabel(cause), BorderLayout.CENTER);
                    episodeArea.revalidate();
                    episodeArea.repaint();
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
            return;
        }

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(new EmptyBorder(0, 0, 15, 0));

        JLabel episodesTitle = Theme.heading("Episodios", 23f);
        episodesTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(episodesTitle);
        header.add(Box.createVerticalStrut(12));

        JPanel seasonTabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        seasonTabs.setOpaque(false);

        JScrollPane seasonScroll = new JScrollPane(seasonTabs);
        seasonScroll.setBorder(null);
        seasonScroll.setOpaque(false);
        seasonScroll.getViewport().setOpaque(false);
        seasonScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        seasonScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        seasonScroll.getHorizontalScrollBar().setUnitIncrement(24);
        seasonScroll.setPreferredSize(new Dimension(800, 48));
        seasonScroll.addMouseWheelListener(e -> {
            JScrollBar bar = seasonScroll.getHorizontalScrollBar();
            bar.setValue(bar.getValue() + e.getWheelRotation() * 80);
            e.consume();
        });
        seasonScroll.setAlignmentX(Component.LEFT_ALIGNMENT);

        header.add(seasonScroll);
        episodeArea.add(header, BorderLayout.NORTH);

        JPanel rows = new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));

        episodeArea.add(rows, BorderLayout.CENTER);

        Map<Integer, JButton> seasonButtons = new LinkedHashMap<>();

        java.util.function.IntConsumer selectSeason = selectedSeason -> {
            rows.removeAll();
            UserData.HistoryEntry historyEntry = UserData.getHistoryEntry(provider.id(), item);

            for (Models.Episode episode : seasons.getOrDefault(selectedSeason, List.of())) {
                boolean currentEpisode = historyEntry != null
                        && episode.id().equals(historyEntry.mediaId())
                        && historyEntry.durationSeconds() > 0;
                double episodeProgress = currentEpisode
                        ? Math.max(0.0, Math.min(1.0,
                                historyEntry.progressSeconds() / historyEntry.durationSeconds()))
                        : 0.0;
                double resumeAt = currentEpisode && episodeProgress < 0.95
                        ? historyEntry.progressSeconds()
                        : 0.0;

                EpisodeRow row = new EpisodeRow(
                        episode,
                        ep -> chooseServerAndPlay(
                                ep.id(),
                                item.title() + " · T" + ep.seasonNumber() + "E" + ep.episodeNumber(),
                                true,
                                ep,
                                resumeAt,
                                null),
                        (ep, invoker) -> chooseServerAndPlay(
                                ep.id(),
                                item.title() + " · T" + ep.seasonNumber() + "E" + ep.episodeNumber(),
                                false,
                                ep,
                                resumeAt,
                                invoker),
                        episodeProgress
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

        int initialSeason = seasons.containsKey(1) ? 1 : seasons.keySet().iterator().next();
        selectSeason.accept(initialSeason);

        episodeArea.revalidate();
        episodeArea.repaint();
    }

    private void chooseServerAndPlay(String providerItemId, String mediaTitle, boolean autoPlay,
                                     Models.Episode episode, double resumeAtSeconds, Component manualInvoker) {
        long playbackRequest = MpvPlayer.beginRequest();
        status.setText(autoPlay ? "Preparando reproducción…" : "Buscando fuentes…");

        EmbeddedPlayerWindow preparingWindow = autoPlay
                ? configuredPlayer(mediaTitle, episode)
                : null;
        if (preparingWindow != null) preparingWindow.setPreparing("Buscando la mejor fuente…");

        new SwingWorker<List<Models.Server>, Void>() {
            @Override protected List<Models.Server> doInBackground() throws Exception {
                return provider.servers(providerItemId);
            }

            @Override protected void done() {
                try {
                    List<Models.Server> servers = get();
                    if (servers.isEmpty()) {
                        if (preparingWindow != null) preparingWindow.showFailure("No hay fuentes disponibles.");
                        status.setText("No hay fuentes disponibles");
                        return;
                    }

                    if (autoPlay) {
                        resolveAnyAndPlay(
                                servers, mediaTitle, playbackRequest, preparingWindow, episode, resumeAtSeconds);
                        return;
                    }

                    Component invoker = manualInvoker != null ? manualInvoker : TitleDetailView.this;
                    ServerPickerMenu.show(invoker, servers, choice -> {
                        EmbeddedPlayerWindow player = configuredPlayer(mediaTitle, episode);
                        if (choice.automatic()) {
                            resolveAnyAndPlay(
                                    servers, mediaTitle, playbackRequest, player, episode, resumeAtSeconds);
                        } else {
                            resolveAndPlay(
                                    choice.server(), mediaTitle, playbackRequest, player, episode, resumeAtSeconds);
                        }
                    });
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (preparingWindow != null) {
                        preparingWindow.showFailure(cause.getMessage() == null
                                ? "No se pudo iniciar la reproducción." : cause.getMessage());
                    }
                    status.setText("No se pudo reproducir");
                }
            }
        }.execute();
    }

    private EmbeddedPlayerWindow configuredPlayer(String mediaTitle, Models.Episode episode) {
        EmbeddedPlayerWindow player = EmbeddedPlayerWindow.open(owner, mediaTitle);
        if (episode == null) {
            player.setProgressListener((progress, duration, finalUpdate) -> {
                if (finalUpdate) UserData.recordHistoryFinal(provider.id(), item, progress, duration);
                else UserData.recordHistoryAsync(provider.id(), item, progress, duration);
                onHistoryChanged.run();
            });
        } else {
            player.setProgressListener((progress, duration, finalUpdate) -> {
                if (finalUpdate) {
                    UserData.recordEpisodeHistoryFinal(provider.id(), item, episode, progress, duration);
                } else {
                    UserData.recordEpisodeHistoryAsync(provider.id(), item, episode, progress, duration);
                }
                onHistoryChanged.run();
            });
        }
        return player;
    }

    private void resolveAnyAndPlay(List<Models.Server> servers, String mediaTitle,
                                   long playbackRequest, EmbeddedPlayerWindow player,
                                   Models.Episode episode, double resumeAtSeconds) {
        status.setText("Buscando la mejor fuente…");

        new SwingWorker<Models.Server, Void>() {
            @Override protected Models.Server doInBackground() throws Exception {
                Exception last = null;
                for (Models.Server server : servers) {
                    if (!player.isDisplayable()) throw new CancellationException("Reproductor cerrado.");
                    player.setPreparing("Probando " + server.name() + "…");
                    try {
                        Models.Video video = extractors.resolve(server);
                        if (video == null || video.source() == null || video.source().isBlank()) {
                            throw new IllegalStateException("La fuente no devolvió un video reproducible.");
                        }
                        player.start(video, server.name(), playbackRequest, resumeAtSeconds);
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
                throw new IllegalStateException("Ninguna fuente pudo iniciar la reproducción.", last);
            }

            @Override protected void done() {
                try {
                    Models.Server server = get();
                    recordOpened(episode);
                    status.setText("Reproduciendo");
                } catch (Exception ex) {
                    Throwable cause = ex instanceof ExecutionException && ex.getCause() != null
                            ? ex.getCause() : ex;
                    if (player.isDisplayable()) {
                        player.showFailure(cause.getMessage() == null
                                ? "No se encontró una fuente compatible." : cause.getMessage());
                    }
                    status.setText("No se pudo reproducir");
                }
            }
        }.execute();
    }

    private void resolveAndPlay(Models.Server server, String mediaTitle,
                                long playbackRequest, EmbeddedPlayerWindow player,
                                Models.Episode episode, double resumeAtSeconds) {
        status.setText("Preparando reproducción…");
        player.setPreparing("Preparando " + server.name() + "…");

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                Models.Video video = extractors.resolve(server);
                if (video == null || video.source() == null || video.source().isBlank()) {
                    throw new IllegalStateException("La fuente no devolvió un video reproducible.");
                }
                player.start(video, server.name(), playbackRequest, resumeAtSeconds);
                return null;
            }

            @Override protected void done() {
                try {
                    get();
                    recordOpened(episode);
                    status.setText("Reproduciendo");
                } catch (Exception ex) {
                    Throwable cause = ex instanceof ExecutionException && ex.getCause() != null
                            ? ex.getCause() : ex;
                    if (player.isDisplayable()) {
                        player.showFailure(cause.getMessage() == null
                                ? "No se pudo iniciar la reproducción." : cause.getMessage());
                    }
                    status.setText("No se pudo reproducir");
                }
            }
        }.execute();
    }

    private void autoPlaySeries() {
        if (episodes.isEmpty()) return;

        UserData.HistoryEntry historyEntry = UserData.getHistoryEntry(provider.id(), item);
        Models.Episode target = null;
        double resumeAt = 0.0;

        if (historyEntry != null
                && historyEntry.mediaId() != null
                && historyEntry.durationSeconds() > 0
                && historyEntry.progressSeconds() > 15
                && historyEntry.progressSeconds() < historyEntry.durationSeconds() * 0.95) {
            target = episodes.stream()
                    .filter(ep -> historyEntry.mediaId().equals(ep.id()))
                    .findFirst()
                    .orElse(null);
            if (target != null) resumeAt = historyEntry.progressSeconds();
        }

        if (target == null) {
            target = episodes.stream()
                    .filter(ep -> ep.seasonNumber() == 1)
                    .findFirst()
                    .orElse(episodes.get(0));
        }

        Models.Episode episode = target;
        double position = resumeAt;
        SwingUtilities.invokeLater(() -> chooseServerAndPlay(
                episode.id(),
                item.title() + " · T" + episode.seasonNumber() + "E" + episode.episodeNumber(),
                true,
                episode,
                position,
                null
        ));
    }

    private void recordOpened(Models.Episode episode) {
        if (episode == null) UserData.recordHistory(provider.id(), item);
        else UserData.recordEpisodeHistory(provider.id(), item, episode, 0.0, 0.0);
        onHistoryChanged.run();
    }

    private JLabel errorLabel(Throwable ex) {
        JLabel label = new JLabel("No se pudieron cargar los episodios: "
                + (ex.getMessage() == null ? ex.toString() : ex.getMessage()));
        label.setForeground(Theme.DANGER);
        return label;
    }

    private static String formatTime(double rawSeconds) {
        if (!Double.isFinite(rawSeconds) || rawSeconds < 0) rawSeconds = 0;
        long total = Math.round(rawSeconds);
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long seconds = total % 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                : String.format("%02d:%02d", minutes, seconds);
    }

    private static String shorten(String value, int max) {
        if (value == null || value.length() <= max) return value == null ? "" : value;
        int cut = value.lastIndexOf(' ', max);
        if (cut < max / 2) cut = max;
        return value.substring(0, cut).strip() + "…";
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
