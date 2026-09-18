package dev.streamflix.desktop;

import com.sun.jna.Native;
import com.sun.jna.Pointer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Streamflix-owned video player. mpv remains the engine, but all visible chrome
 * and controls belong to Streamflix.
 */
final class EmbeddedPlayerWindow extends JFrame {
    private static final String CARD_LOADING = "loading";
    private static final String CARD_VIDEO = "video";
    private static EmbeddedPlayerWindow activeWindow;

    private final Canvas videoSurface = new Canvas();
    private final JPanel mediaStage = new JPanel(new CardLayout());
    private final JPanel chromeTop = new JPanel(new BorderLayout(18, 0));
    private final JPanel chromeBottom = new JPanel();
    private final JLabel loadingTitle = Theme.heading("STREAMFLIX", 24f);
    private final JLabel loadingDetail = Theme.muted("Buscando un servidor compatible…");
    private final JProgressBar loadingProgress = new JProgressBar();

    private final JLabel mediaTitle = Theme.heading("Reproduciendo", 16f);
    private final JLabel serverLabel = Theme.muted("Preparando…");
    private final JLabel timeLabel = Theme.muted("00:00 / 00:00");
    private final JLabel status = Theme.muted("Preparando reproducción…");

    private final JButton playPause = Theme.primaryButton("Pausar");
    private final JButton back10 = Theme.button("−10 s");
    private final JButton forward10 = Theme.button("+10 s");
    private final JButton fullscreen = Theme.button("Pantalla completa");

    private final JSlider timeline = new JSlider(0, 1000, 0);
    private final JSlider volume = new JSlider(0, 100, 80);
    private final JButton audioButton = Theme.button("Audio");
    private final JButton subtitleButton = Theme.button("Subtítulos");
    private final JPopupMenu audioMenu = new JPopupMenu();
    private final JPopupMenu subtitleMenu = new JPopupMenu();

    private final Timer refreshTimer;
    private final Timer chromeHideTimer;
    private final AtomicBoolean refreshInFlight = new AtomicBoolean();
    private volatile MpvIpcClient ipc;
    private volatile boolean closing;
    private volatile double durationSeconds;
    private boolean updatingTimeline;
    private boolean updatingVolume;
    private volatile Models.Video currentVideo;
    private volatile String currentServerName;
    private volatile long currentRequestId;
    private volatile double lastKnownTime;
    private int consecutiveRefreshFailures;
    private int stalledTicks;
    private int recoveryAttempts;
    private boolean recovering;
    private final Window appOwner;
    private boolean fullScreen;
    private boolean minimizedByApplication;
    private boolean restoreFullscreenAfterApplicationRestore;

    private EmbeddedPlayerWindow(Window owner, String title) {
        super("Streamflix · " + title);
        this.appOwner = owner;
        setUndecorated(true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(960, 620));
        setSize(1320, 820);
        setLocationRelativeTo(owner);
        setContentPane(buildUi());

        mediaTitle.setText(title);
        videoSurface.setBackground(Color.BLACK);
        videoSurface.setFocusable(true);

        installActions();
        refreshTimer = new Timer(1000, e -> refreshStateAsync());
        refreshTimer.setCoalesce(true);
        chromeHideTimer = new Timer(2400, e -> {
            if (fullScreen) chromeBottom.setVisible(false);
        });
        chromeHideTimer.setRepeats(false);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { stopPlayback(); }
        });

        getRootPane().registerKeyboardAction(
                e -> {
                    if (fullScreen) toggleFullscreen();
                    else dispose();
                },
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        getRootPane().registerKeyboardAction(
                e -> togglePause(),
                KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        getRootPane().registerKeyboardAction(
                e -> seek(-10),
                KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        getRootPane().registerKeyboardAction(
                e -> seek(10),
                KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        getRootPane().registerKeyboardAction(
                e -> toggleFullscreen(),
                KeyStroke.getKeyStroke(KeyEvent.VK_F, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        videoSurface.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                revealChrome();
                if (e.getClickCount() == 2) toggleFullscreen();
            }
        });
        videoSurface.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) { revealChrome(); }
            @Override public void mouseDragged(MouseEvent e) { revealChrome(); }
        });
    }

    static EmbeddedPlayerWindow open(Window owner, String title) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("El reproductor debe abrirse desde la UI.");
        }
        if (activeWindow != null && activeWindow.isDisplayable()) activeWindow.dispose();

        EmbeddedPlayerWindow window = new EmbeddedPlayerWindow(owner, title);
        activeWindow = window;
        window.setVisible(true);
        window.toFront();
        window.videoSurface.requestFocusInWindow();
        return window;
    }

    static void onApplicationStateChanged(int state) {
        Runnable action = () -> {
            EmbeddedPlayerWindow window = activeWindow;
            if (window == null || !window.isDisplayable() || window.closing) return;

            boolean iconified = (state & Frame.ICONIFIED) != 0;
            if (iconified) window.minimizeWithApplication();
            else window.restoreWithApplication();
        };
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }

    private void minimizeWithApplication() {
        if (minimizedByApplication || !isDisplayable()) return;
        minimizedByApplication = true;

        if (fullScreen) {
            GraphicsDevice device = getGraphicsConfiguration().getDevice();
            if (device.getFullScreenWindow() == this) device.setFullScreenWindow(null);
            fullScreen = false;
            restoreFullscreenAfterApplicationRestore = true;
            chromeHideTimer.stop();
            chromeTop.setVisible(true);
            chromeBottom.setVisible(true);
            fullscreen.setText("Pantalla completa");
        }

        setState(Frame.ICONIFIED);
    }

    private void restoreWithApplication() {
        if (!minimizedByApplication || !isDisplayable()) return;
        minimizedByApplication = false;
        setState(Frame.NORMAL);
        setVisible(true);
        toFront();

        if (restoreFullscreenAfterApplicationRestore) {
            restoreFullscreenAfterApplicationRestore = false;
            SwingUtilities.invokeLater(this::toggleFullscreen);
        } else {
            videoSurface.requestFocusInWindow();
        }
    }

    void setPreparing(String message) {
        SwingUtilities.invokeLater(() -> {
            loadingDetail.setText(message);
            status.setText(message);
            loadingProgress.setIndeterminate(true);
            showStage(CARD_LOADING);
        });
    }

    void start(Models.Video video, String serverName, long requestId) throws Exception {
        if (closing || !isDisplayable()) throw new IllegalStateException("El reproductor fue cerrado.");

        currentVideo = video;
        currentServerName = serverName;
        currentRequestId = requestId;
        lastKnownTime = 0;
        consecutiveRefreshFailures = 0;
        stalledTicks = 0;
        recoveryAttempts = 0;
        recovering = false;

        MpvIpcClient previous = ipc;
        ipc = null;
        if (previous != null) previous.close();

        long hwnd = windowHandle();
        String pipePath = "\\\\.\\pipe\\streamflix-mpv-" + UUID.randomUUID();
        setPreparing("Conectando con " + serverName + "…");

        MpvPlayer.playEmbedded(video, mediaTitle.getText(), requestId, hwnd, pipePath);
        MpvIpcClient client = MpvIpcClient.connect(pipePath, 5000);

        try {
            if (closing || !isDisplayable()) {
                throw new IllegalStateException("El reproductor fue cerrado.");
            }

            waitUntilMediaReady(client, 30000);

            try {
                Object rawVolume = client.getProperty("volume");
                if (rawVolume instanceof Number n) {
                    SwingUtilities.invokeLater(() -> {
                        updatingVolume = true;
                        try {
                            volume.setValue((int) Math.round(n.doubleValue()));
                        } finally {
                            updatingVolume = false;
                        }
                    });
                }
            } catch (Exception ignored) {}

            this.ipc = client;
            refreshTracks();

            SwingUtilities.invokeLater(() -> {
                serverLabel.setText("Servidor · " + serverName);
                status.setForeground(Theme.MUTED);
                status.setText("Reproduciendo");
                loadingProgress.setIndeterminate(false);
                playPause.setText("Pausar");
                showStage(CARD_VIDEO);
                refreshTimer.start();
            });
        } catch (Exception ex) {
            client.close();
            if (this.ipc == client) this.ipc = null;
            try { MpvPlayer.stopCurrent(); } catch (RuntimeException ignored) {}
            throw ex;
        }
    }

    void showFailure(String message) {
        SwingUtilities.invokeLater(() -> {
            refreshTimer.stop();
            loadingProgress.setIndeterminate(false);
            loadingTitle.setText("No se pudo reproducir");
            loadingDetail.setText(message);
            status.setForeground(Theme.DANGER);
            status.setText(message);
            showStage(CARD_LOADING);
        });
    }

    private JComponent buildUi() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.BLACK);
        root.setBorder(BorderFactory.createLineBorder(Theme.BORDER));

        chromeTop.setBackground(new Color(8, 10, 15));
        chromeTop.setBorder(new EmptyBorder(12, 18, 12, 18));

        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        mediaTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        serverLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        serverLabel.setFont(Theme.FONT.deriveFont(11.5f));
        titleBlock.add(mediaTitle);
        titleBlock.add(Box.createVerticalStrut(2));
        titleBlock.add(serverLabel);
        chromeTop.add(titleBlock, BorderLayout.WEST);

        JPanel topActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        topActions.setOpaque(false);
        JButton close = Theme.button("Cerrar");
        close.addActionListener(e -> dispose());
        topActions.add(close);
        chromeTop.add(topActions, BorderLayout.EAST);

        root.add(chromeTop, BorderLayout.NORTH);

        mediaStage.setBackground(Color.BLACK);
        mediaStage.add(buildLoadingStage(), CARD_LOADING);

        JPanel videoWrap = new JPanel(new BorderLayout());
        videoWrap.setBackground(Color.BLACK);
        videoWrap.add(videoSurface, BorderLayout.CENTER);
        mediaStage.add(videoWrap, CARD_VIDEO);

        root.add(mediaStage, BorderLayout.CENTER);

        chromeBottom.setBackground(new Color(8, 10, 15));
        chromeBottom.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                new EmptyBorder(10, 18, 12, 18)));
        chromeBottom.setLayout(new BoxLayout(chromeBottom, BoxLayout.Y_AXIS));

        JPanel progressRow = new JPanel(new BorderLayout(10, 0));
        progressRow.setOpaque(false);
        timeline.setOpaque(false);
        timeline.putClientProperty("JSlider.trackWidth", 4);
        timeline.putClientProperty("JSlider.thumbSize", new Dimension(13, 13));
        progressRow.add(timeline, BorderLayout.CENTER);
        timeLabel.setPreferredSize(new Dimension(112, 28));
        timeLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        progressRow.add(timeLabel, BorderLayout.EAST);
        chromeBottom.add(progressRow);
        chromeBottom.add(Box.createVerticalStrut(8));

        JPanel actions = new JPanel(new BorderLayout(12, 0));
        actions.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        left.setOpaque(false);
        left.add(playPause);
        left.add(back10);
        left.add(forward10);
        left.add(Box.createHorizontalStrut(8));

        JLabel volumeLabel = Theme.muted("Volumen");
        volumeLabel.setFont(Theme.FONT.deriveFont(11.5f));
        left.add(volumeLabel);
        volume.setPreferredSize(new Dimension(110, 30));
        volume.setOpaque(false);
        left.add(volume);
        actions.add(left, BorderLayout.WEST);

        JPanel tracks = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        tracks.setOpaque(false);

        audioButton.setToolTipText("Seleccionar pista de audio");
        subtitleButton.setToolTipText("Seleccionar subtítulos");
        audioButton.setEnabled(false);
        subtitleButton.setEnabled(false);

        tracks.add(audioButton);
        tracks.add(subtitleButton);
        tracks.add(fullscreen);
        actions.add(tracks, BorderLayout.EAST);

        chromeBottom.add(actions);

        root.add(chromeBottom, BorderLayout.SOUTH);
        return root;
    }

    private JComponent buildLoadingStage() {
        JPanel stage = new JPanel(new GridBagLayout());
        stage.setBackground(Color.BLACK);

        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));

        loadingTitle.setForeground(Theme.ACCENT);
        loadingTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        loadingDetail.setAlignmentX(Component.CENTER_ALIGNMENT);
        loadingDetail.setFont(Theme.FONT.deriveFont(13f));

        loadingProgress.setIndeterminate(true);
        loadingProgress.setPreferredSize(new Dimension(260, 5));
        loadingProgress.setMaximumSize(new Dimension(260, 5));
        loadingProgress.setAlignmentX(Component.CENTER_ALIGNMENT);

        box.add(loadingTitle);
        box.add(Box.createVerticalStrut(10));
        box.add(loadingDetail);
        box.add(Box.createVerticalStrut(18));
        box.add(loadingProgress);

        stage.add(box);
        return stage;
    }

    private void installActions() {
        playPause.addActionListener(e -> togglePause());
        back10.addActionListener(e -> seek(-10));
        forward10.addActionListener(e -> seek(10));
        fullscreen.addActionListener(e -> toggleFullscreen());

        timeline.addChangeListener(this::timelineChanged);
        volume.addChangeListener(e -> {
            if (!updatingVolume && !volume.getValueIsAdjusting()) {
                runCommand(() -> requireIpc().setProperty("volume", volume.getValue()));
            }
        });

        audioButton.addActionListener(e -> showTrackMenu(audioButton, audioMenu));
        subtitleButton.addActionListener(e -> showTrackMenu(subtitleButton, subtitleMenu));
    }

    private void timelineChanged(ChangeEvent e) {
        if (!shouldSeekTimeline(updatingTimeline, timeline.getValueIsAdjusting(), durationSeconds)) return;
        double target = durationSeconds * timeline.getValue() / 1000.0;
        runCommand(() -> requireIpc().setProperty("time-pos", target));
    }

    static boolean shouldSeekTimeline(boolean programmaticUpdate, boolean adjusting, double durationSeconds) {
        return !programmaticUpdate && !adjusting && durationSeconds > 0;
    }

    private void togglePause() {
        runCommand(() -> {
            Object raw = requireIpc().getProperty("pause");
            boolean paused = raw instanceof Boolean b && b;
            requireIpc().setProperty("pause", !paused);
            SwingUtilities.invokeLater(() -> playPause.setText(paused ? "Pausar" : "Reanudar"));
        });
    }

    private void seek(int seconds) {
        runCommand(() -> requireIpc().command(List.of("seek", seconds, "relative+exact")));
    }

    private void toggleFullscreen() {
        GraphicsDevice device = getGraphicsConfiguration().getDevice();
        if (!fullScreen) {
            device.setFullScreenWindow(this);
            fullScreen = true;
            chromeTop.setVisible(false);
            chromeBottom.setVisible(true);
            chromeHideTimer.restart();
            fullscreen.setText("Salir de pantalla completa");
        } else {
            device.setFullScreenWindow(null);
            fullScreen = false;
            chromeHideTimer.stop();
            chromeTop.setVisible(true);
            chromeBottom.setVisible(true);
            setSize(1320, 820);
            setLocationRelativeTo(appOwner);
            fullscreen.setText("Pantalla completa");
        }
        videoSurface.requestFocusInWindow();
    }

    private void revealChrome() {
        if (!fullScreen) return;
        chromeBottom.setVisible(true);
        chromeHideTimer.restart();
    }

    private void showStage(String card) {
        ((CardLayout) mediaStage.getLayout()).show(mediaStage, card);
        mediaStage.revalidate();
        mediaStage.repaint();
    }

    private static void waitUntilMediaReady(MpvIpcClient client, long timeoutMillis) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        Exception last = null;
        while (System.nanoTime() < deadline) {
            try {
                Object duration = client.getProperty("duration");
                if (duration instanceof Number n && n.doubleValue() > 0) return;
            } catch (Exception ex) {
                last = ex;
            }
            Thread.sleep(250);
        }
        if (last != null) throw last;
        throw new IllegalStateException("El video no terminó de cargar.");
    }

    private void refreshStateAsync() {
        if (ipc == null || closing || recovering || !refreshInFlight.compareAndSet(false, true)) return;

        new SwingWorker<PlayerState, Void>() {
            @Override protected PlayerState doInBackground() throws Exception {
                MpvIpcClient client = requireIpc();
                Object timeRaw = client.getProperty("time-pos");
                Object durationRaw = client.getProperty("duration");
                Object pauseRaw = client.getProperty("pause");
                Object cacheRaw = client.getProperty("paused-for-cache");
                Object eofRaw = client.getProperty("eof-reached");

                double time = timeRaw instanceof Number n ? n.doubleValue() : 0.0;
                double duration = durationRaw instanceof Number n ? n.doubleValue() : 0.0;
                boolean paused = pauseRaw instanceof Boolean b && b;
                boolean pausedForCache = cacheRaw instanceof Boolean b && b;
                boolean eofReached = eofRaw instanceof Boolean b && b;
                return new PlayerState(time, duration, paused, pausedForCache, eofReached);
            }

            @Override protected void done() {
                refreshInFlight.set(false);
                if (closing || recovering) return;

                try {
                    PlayerState state = get();
                    consecutiveRefreshFailures = 0;
                    durationSeconds = state.duration();

                    if (!timeline.getValueIsAdjusting() && state.duration() > 0) {
                        updatingTimeline = true;
                        try {
                            timeline.setValue((int) Math.max(0, Math.min(1000,
                                    Math.round(state.time() / state.duration() * 1000))));
                        } finally {
                            updatingTimeline = false;
                        }
                    }

                    timeLabel.setText(formatTime(state.time()) + " / " + formatTime(state.duration()));
                    playPause.setText(state.paused() ? "Reanudar" : "Pausar");

                    if (state.eofReached()) {
                        stalledTicks = 0;
                        lastKnownTime = state.time();
                        status.setText("Finalizado");
                        return;
                    }

                    boolean moved = Math.abs(state.time() - lastKnownTime) >= 0.20;
                    if (moved) {
                        lastKnownTime = state.time();
                        stalledTicks = 0;
                        if (!state.paused()) status.setText("Reproduciendo");
                    } else if (state.paused()) {
                        stalledTicks = 0;
                    } else {
                        stalledTicks++;
                        if (state.pausedForCache()) status.setText("Cargando…");
                    }

                    // A short CDN hiccup should be absorbed by mpv's cache. Only
                    // recover after a genuinely prolonged stall.
                    if (!state.paused() && stalledTicks >= 20) {
                        requestRecovery(state.pausedForCache()
                                ? "El servidor dejó de entregar datos."
                                : "La reproducción dejó de avanzar.");
                    }
                } catch (Exception ex) {
                    consecutiveRefreshFailures++;
                    boolean processGone = !MpvPlayer.isCurrentAlive();
                    if (processGone || consecutiveRefreshFailures >= 3) {
                        requestRecovery(processGone
                                ? "El motor de reproducción se cerró inesperadamente."
                                : "Se perdió la comunicación con el reproductor.");
                    }
                }
            }
        }.execute();
    }

    private void requestRecovery(String reason) {
        if (closing || recovering || currentVideo == null) return;

        if (recoveryAttempts >= 1) {
            refreshTimer.stop();
            showFailure("La reproducción se detuvo y no pudo recuperarse automáticamente.");
            return;
        }

        recoveryAttempts++;
        recovering = true;
        refreshTimer.stop();

        double resumeAt = Math.max(0.0, lastKnownTime - 0.5);
        Models.Video video = currentVideo;
        String serverName = currentServerName == null ? "servidor" : currentServerName;
        long requestId = currentRequestId;

        setPreparing("Recuperando reproducción…");

        MpvIpcClient previous = ipc;
        ipc = null;
        if (previous != null) previous.close();

        new SwingWorker<MpvIpcClient, Void>() {
            @Override protected MpvIpcClient doInBackground() throws Exception {
                long hwnd = windowHandle();
                String pipePath = "\\\\.\\pipe\\streamflix-mpv-" + UUID.randomUUID();

                MpvPlayer.playEmbedded(video, mediaTitle.getText(), requestId, hwnd, pipePath);
                MpvIpcClient client = MpvIpcClient.connect(pipePath, 5000);
                try {
                    waitUntilMediaReady(client, 30000);
                    if (resumeAt > 1.0) {
                        client.command(List.of("seek", resumeAt, "absolute+exact"));
                    }
                    return client;
                } catch (Exception ex) {
                    client.close();
                    try { MpvPlayer.stopCurrent(); } catch (RuntimeException ignored) {}
                    throw ex;
                }
            }

            @Override protected void done() {
                if (closing) {
                    recovering = false;
                    return;
                }

                try {
                    ipc = get();
                    consecutiveRefreshFailures = 0;
                    stalledTicks = 0;
                    lastKnownTime = resumeAt;
                    recovering = false;

                    serverLabel.setText("Servidor · " + serverName);
                    status.setForeground(Theme.MUTED);
                    status.setText("Reproduciendo");
                    loadingProgress.setIndeterminate(false);
                    playPause.setText("Pausar");
                    showStage(CARD_VIDEO);
                    refreshTracks();
                    refreshTimer.start();
                } catch (Exception ex) {
                    recovering = false;
                    refreshTimer.stop();
                    showFailure("No se pudo recuperar la reproducción. " + reason);
                }
            }
        }.execute();
    }

    private void refreshTracks() {
        new SwingWorker<Tracks, Void>() {
            @Override protected Tracks doInBackground() throws Exception {
                Object raw = requireIpc().getProperty("track-list");
                if (!(raw instanceof List<?> list)) return new Tracks(List.of(), List.of());

                ArrayList<TrackOption> audio = new ArrayList<>();
                ArrayList<TrackOption> subtitles = new ArrayList<>();
                subtitles.add(new TrackOption(0, "Desactivados", false));

                for (Object item : list) {
                    if (!(item instanceof Map<?, ?>)) continue;
                    Map<String, Object> track = Json.object(item);
                    Integer id = Json.integer(track.get("id"));
                    if (id == null) continue;

                    String type = Json.string(track.get("type"));
                    String lang = Json.string(track.get("lang"));
                    String title = Json.string(track.get("title"));
                    boolean selected = Boolean.TRUE.equals(track.get("selected"));
                    String label = trackLabel(lang, title, id);

                    if ("audio".equals(type)) audio.add(new TrackOption(id, label, selected));
                    else if ("sub".equals(type)) subtitles.add(new TrackOption(id, label, selected));
                }
                return new Tracks(List.copyOf(audio), List.copyOf(subtitles));
            }

            @Override protected void done() {
                try {
                    Tracks tracks = get();
                    populateTrackMenu(audioButton, audioMenu, tracks.audio(), false, "aid");
                    populateTrackMenu(subtitleButton, subtitleMenu, tracks.subtitles(), true, "sid");
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void populateTrackMenu(JButton button, JPopupMenu menu, List<TrackOption> tracks,
                                   boolean allowOff, String property) {
        menu.removeAll();
        ButtonGroup group = new ButtonGroup();
        TrackOption selected = null;

        for (TrackOption track : tracks) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(track.label());
            item.setFont(Theme.FONT.deriveFont(12.5f));
            item.setForeground(Theme.TEXT);
            item.setBackground(Theme.PANEL);
            item.setSelected(track.selected());
            item.addActionListener(e -> {
                button.setText(track.id() == 0
                        ? (allowOff ? "Subtítulos" : "Audio")
                        : (allowOff ? "Subtítulos · " : "Audio · ") + shortTrackLabel(track.label()));
                runCommand(() -> requireIpc().setProperty(property,
                        allowOff && track.id() == 0 ? "no" : track.id()));
            });
            group.add(item);
            menu.add(item);
            if (track.selected()) selected = track;
        }

        if (allowOff && selected == null && !tracks.isEmpty()) {
            ((JRadioButtonMenuItem) menu.getComponent(0)).setSelected(true);
        }

        menu.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        menu.setBackground(Theme.PANEL);

        int meaningful = tracks.size() - (allowOff ? 1 : 0);
        button.setEnabled(meaningful > 0);
        if (selected != null && selected.id() != 0) {
            button.setText((allowOff ? "Subtítulos · " : "Audio · ") + shortTrackLabel(selected.label()));
        } else {
            button.setText(allowOff ? "Subtítulos" : "Audio");
        }
    }

    private static String shortTrackLabel(String label) {
        if (label == null || label.isBlank()) return "";
        String value = label.strip();
        return value.length() <= 14 ? value : value.substring(0, 13).strip() + "…";
    }

    private static void showTrackMenu(JButton button, JPopupMenu menu) {
        if (!button.isEnabled() || menu.getComponentCount() == 0) return;
        Dimension preferred = menu.getPreferredSize();
        int x = Math.max(0, button.getWidth() - preferred.width);
        int y = -preferred.height - 6;
        menu.show(button, x, y);
    }

    private long windowHandle() throws Exception {
        AtomicLong value = new AtomicLong();

        Runnable read = () -> {
            if (!videoSurface.isDisplayable()) return;
            Pointer pointer = Native.getComponentPointer(videoSurface);
            if (pointer != null) value.set(Pointer.nativeValue(pointer));
        };

        if (SwingUtilities.isEventDispatchThread()) read.run();
        else SwingUtilities.invokeAndWait(read);

        if (value.get() == 0L) throw new IllegalStateException("No se pudo preparar la superficie de video.");
        return value.get();
    }

    private void runCommand(ThrowingRunnable command) {
        if (ipc == null || closing) return;
        new Thread(() -> {
            try {
                command.run();
            } catch (Exception ex) {
                if (!closing) SwingUtilities.invokeLater(() -> status.setText("Control no disponible"));
            }
        }, "streamflix-player-control").start();
    }

    private MpvIpcClient requireIpc() {
        MpvIpcClient client = ipc;
        if (client == null) throw new IllegalStateException("Reproductor aún no conectado.");
        return client;
    }

    private void stopPlayback() {
        if (closing) return;
        closing = true;
        refreshTimer.stop();
        chromeHideTimer.stop();

        GraphicsDevice device = getGraphicsConfiguration().getDevice();
        if (fullScreen && device.getFullScreenWindow() == this) {
            device.setFullScreenWindow(null);
        }
        fullScreen = false;

        MpvIpcClient client = ipc;
        ipc = null;
        if (client != null) client.close();

        if (!MpvPlayer.isShutdown()) {
            try { MpvPlayer.beginRequest(); } catch (RuntimeException ignored) {}
            try { MpvPlayer.stopCurrent(); } catch (RuntimeException ignored) {}
        }
        if (activeWindow == this) activeWindow = null;
    }

    private static String trackLabel(String lang, String title, int id) {
        String language = lang == null || lang.isBlank() ? "" : lang.toUpperCase();
        String name = title == null || title.isBlank() ? "" : title.strip();

        if (!language.isBlank() && !name.isBlank()) return language + " · " + name;
        if (!language.isBlank()) return language;
        if (!name.isBlank()) return name;
        return "Pista " + id;
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

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private record TrackOption(int id, String label, boolean selected) {
        @Override public String toString() { return label; }
    }

    private record PlayerState(double time, double duration, boolean paused,
                               boolean pausedForCache, boolean eofReached) {}
    private record Tracks(List<TrackOption> audio, List<TrackOption> subtitles) {}
}
