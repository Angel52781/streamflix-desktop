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
 * Streamflix-owned video player UI. mpv remains the playback engine, but it renders
 * into this window and is controlled through JSON IPC rather than showing its own UI.
 */
final class EmbeddedPlayerWindow extends JDialog {
    private static EmbeddedPlayerWindow activeWindow;

    private final Canvas videoSurface = new Canvas();
    private final JLabel mediaTitle = Theme.heading("Reproduciendo", 16f);
    private final JLabel serverLabel = Theme.muted("Preparando…");
    private final JLabel timeLabel = Theme.muted("00:00 / 00:00");
    private final JLabel status = Theme.muted("Preparando reproducción…");
    private final JProgressBar loading = new JProgressBar();

    private final JButton playPause = Theme.primaryButton("Pausar");
    private final JButton back10 = Theme.button("−10 s");
    private final JButton forward10 = Theme.button("+10 s");
    private final JButton maximize = Theme.button("Maximizar");

    private final JSlider timeline = new JSlider(0, 1000, 0);
    private final JSlider volume = new JSlider(0, 100, 80);
    private final JComboBox<TrackOption> subtitleBox = new JComboBox<>();
    private final JComboBox<TrackOption> audioBox = new JComboBox<>();

    private final Timer refreshTimer;
    private final AtomicBoolean refreshInFlight = new AtomicBoolean();
    private volatile MpvIpcClient ipc;
    private volatile boolean closing;
    private volatile boolean updatingTracks;
    private volatile double durationSeconds;
    private Rectangle normalBounds;

    private EmbeddedPlayerWindow(Window owner, String title) {
        super(owner, "Streamflix · " + title, ModalityType.MODELESS);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        getRootPane().putClientProperty("JRootPane.titleBarBackground", Theme.SIDEBAR);
        getRootPane().putClientProperty("JRootPane.titleBarForeground", Theme.TEXT);
        setMinimumSize(new Dimension(900, 600));
        setSize(1240, 780);
        setLocationRelativeTo(owner);
        setContentPane(buildUi());

        mediaTitle.setText(title);
        videoSurface.setBackground(Color.BLACK);
        videoSurface.setFocusable(true);

        installActions();
        refreshTimer = new Timer(750, e -> refreshStateAsync());
        refreshTimer.setCoalesce(true);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { stopPlayback(); }
        });

        getRootPane().registerKeyboardAction(
                e -> dispose(),
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

        videoSurface.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) toggleMaximize();
            }
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

    void setPreparing(String message) {
        SwingUtilities.invokeLater(() -> {
            status.setText(message);
            loading.setIndeterminate(true);
            loading.setVisible(true);
        });
    }

    void start(Models.Video video, String serverName, long requestId) throws Exception {
        if (closing || !isDisplayable()) throw new IllegalStateException("El reproductor fue cerrado.");

        long hwnd = windowHandle();
        String pipePath = "\\\\.\\pipe\\streamflix-mpv-" + UUID.randomUUID();
        setPreparing("Conectando con " + serverName + "…");

        MpvPlayer.playEmbedded(video, mediaTitle.getText(), requestId, hwnd, pipePath);
        MpvIpcClient client = MpvIpcClient.connect(pipePath, 5000);
        if (closing || !isDisplayable()) {
            client.close();
            throw new IllegalStateException("El reproductor fue cerrado.");
        }
        this.ipc = client;

        try {
            Object rawVolume = client.getProperty("volume");
            if (rawVolume instanceof Number n) {
                SwingUtilities.invokeLater(() -> volume.setValue((int) Math.round(n.doubleValue())));
            }
        } catch (Exception ignored) {}

        refreshTracks();
        SwingUtilities.invokeLater(() -> {
            serverLabel.setText("Servidor · " + serverName);
            status.setText("Reproduciendo");
            loading.setIndeterminate(false);
            loading.setVisible(false);
            playPause.setText("Pausar");
            refreshTimer.start();
        });
    }

    void showFailure(String message) {
        SwingUtilities.invokeLater(() -> {
            loading.setIndeterminate(false);
            loading.setVisible(false);
            status.setForeground(Theme.DANGER);
            status.setText(message);
        });
    }

    private JComponent buildUi() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);

        JPanel top = new JPanel(new BorderLayout(18, 0));
        top.setBackground(Theme.SIDEBAR);
        top.setBorder(new EmptyBorder(12, 18, 12, 18));

        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        mediaTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        serverLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        serverLabel.setFont(Theme.FONT.deriveFont(11.5f));
        titleBlock.add(mediaTitle);
        titleBlock.add(Box.createVerticalStrut(2));
        titleBlock.add(serverLabel);
        top.add(titleBlock, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(maximize);
        JButton close = Theme.button("Cerrar");
        close.addActionListener(e -> dispose());
        right.add(close);
        top.add(right, BorderLayout.EAST);

        root.add(top, BorderLayout.NORTH);

        JPanel videoWrap = new JPanel(new BorderLayout());
        videoWrap.setBackground(Color.BLACK);
        videoWrap.setBorder(new EmptyBorder(0, 0, 0, 0));
        videoWrap.add(videoSurface, BorderLayout.CENTER);
        root.add(videoWrap, BorderLayout.CENTER);

        JPanel controls = new JPanel();
        controls.setBackground(Theme.SIDEBAR);
        controls.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                new EmptyBorder(10, 16, 12, 16)));
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

        JPanel progressRow = new JPanel(new BorderLayout(10, 0));
        progressRow.setOpaque(false);
        timeline.setOpaque(false);
        timeline.putClientProperty("JSlider.trackWidth", 4);
        timeline.putClientProperty("JSlider.thumbSize", new Dimension(13, 13));
        progressRow.add(timeline, BorderLayout.CENTER);
        timeLabel.setPreferredSize(new Dimension(112, 28));
        timeLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        progressRow.add(timeLabel, BorderLayout.EAST);
        controls.add(progressRow);
        controls.add(Box.createVerticalStrut(8));

        JPanel actions = new JPanel(new BorderLayout(12, 0));
        actions.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        left.setOpaque(false);
        left.add(playPause);
        left.add(back10);
        left.add(forward10);

        JLabel volumeLabel = Theme.muted("Volumen");
        volumeLabel.setFont(Theme.FONT.deriveFont(11.5f));
        left.add(Box.createHorizontalStrut(6));
        left.add(volumeLabel);
        volume.setPreferredSize(new Dimension(110, 30));
        volume.setOpaque(false);
        left.add(volume);
        actions.add(left, BorderLayout.WEST);

        JPanel tracks = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        tracks.setOpaque(false);

        subtitleBox.setPreferredSize(new Dimension(210, 34));
        subtitleBox.setToolTipText("Seleccionar subtítulos");
        audioBox.setPreferredSize(new Dimension(180, 34));
        audioBox.setToolTipText("Seleccionar pista de audio");

        tracks.add(Theme.muted("Audio"));
        tracks.add(audioBox);
        tracks.add(Theme.muted("Subtítulos"));
        tracks.add(subtitleBox);
        actions.add(tracks, BorderLayout.EAST);

        controls.add(actions);
        controls.add(Box.createVerticalStrut(7));

        JPanel state = new JPanel(new BorderLayout());
        state.setOpaque(false);
        status.setFont(Theme.FONT.deriveFont(11.5f));
        state.add(status, BorderLayout.WEST);
        loading.setIndeterminate(true);
        loading.setPreferredSize(new Dimension(150, 4));
        loading.putClientProperty("JProgressBar.largeHeight", false);
        state.add(loading, BorderLayout.EAST);
        controls.add(state);

        root.add(controls, BorderLayout.SOUTH);
        return root;
    }

    private void installActions() {
        playPause.addActionListener(e -> togglePause());
        back10.addActionListener(e -> seek(-10));
        forward10.addActionListener(e -> seek(10));
        maximize.addActionListener(e -> toggleMaximize());

        timeline.addChangeListener(this::timelineChanged);
        volume.addChangeListener(e -> {
            if (!volume.getValueIsAdjusting()) {
                runCommand(() -> requireIpc().setProperty("volume", volume.getValue()));
            }
        });

        subtitleBox.addActionListener(e -> {
            if (updatingTracks) return;
            TrackOption selected = (TrackOption) subtitleBox.getSelectedItem();
            if (selected == null) return;
            runCommand(() -> requireIpc().setProperty("sid", selected.id() == 0 ? "no" : selected.id()));
        });

        audioBox.addActionListener(e -> {
            if (updatingTracks) return;
            TrackOption selected = (TrackOption) audioBox.getSelectedItem();
            if (selected == null || selected.id() == 0) return;
            runCommand(() -> requireIpc().setProperty("aid", selected.id()));
        });
    }

    private void timelineChanged(ChangeEvent e) {
        if (timeline.getValueIsAdjusting() || durationSeconds <= 0) return;
        double target = durationSeconds * timeline.getValue() / 1000.0;
        runCommand(() -> requireIpc().setProperty("time-pos", target));
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

    private void toggleMaximize() {
        if (normalBounds == null) {
            normalBounds = getBounds();
            Rectangle bounds = getGraphicsConfiguration().getBounds();
            Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(getGraphicsConfiguration());
            setBounds(bounds.x + screenInsets.left, bounds.y + screenInsets.top,
                    bounds.width - screenInsets.left - screenInsets.right,
                    bounds.height - screenInsets.top - screenInsets.bottom);
            maximize.setText("Restaurar");
        } else {
            setBounds(normalBounds);
            normalBounds = null;
            maximize.setText("Maximizar");
        }
    }

    private void refreshStateAsync() {
        if (ipc == null || closing || !refreshInFlight.compareAndSet(false, true)) return;
        new SwingWorker<PlayerState, Void>() {
            @Override protected PlayerState doInBackground() throws Exception {
                MpvIpcClient client = requireIpc();
                Object timeRaw = client.getProperty("time-pos");
                Object durationRaw = client.getProperty("duration");
                Object pauseRaw = client.getProperty("pause");
                double time = timeRaw instanceof Number n ? n.doubleValue() : 0.0;
                double duration = durationRaw instanceof Number n ? n.doubleValue() : 0.0;
                boolean paused = pauseRaw instanceof Boolean b && b;
                return new PlayerState(time, duration, paused);
            }

            @Override protected void done() {
                refreshInFlight.set(false);
                if (closing) return;
                try {
                    PlayerState state = get();
                    durationSeconds = state.duration();
                    if (!timeline.getValueIsAdjusting() && state.duration() > 0) {
                        timeline.setValue((int) Math.max(0, Math.min(1000,
                                Math.round(state.time() / state.duration() * 1000))));
                    }
                    timeLabel.setText(formatTime(state.time()) + " / " + formatTime(state.duration()));
                    playPause.setText(state.paused() ? "Reanudar" : "Pausar");
                } catch (Exception ignored) {}
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
                    updatingTracks = true;
                    try {
                        fillTracks(audioBox, tracks.audio(), false);
                        fillTracks(subtitleBox, tracks.subtitles(), true);
                    } finally {
                        updatingTracks = false;
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private static void fillTracks(JComboBox<TrackOption> box, List<TrackOption> tracks, boolean allowOff) {
        DefaultComboBoxModel<TrackOption> model = new DefaultComboBoxModel<>();
        TrackOption selected = null;
        for (TrackOption track : tracks) {
            model.addElement(track);
            if (track.selected()) selected = track;
        }
        box.setModel(model);
        if (selected != null) box.setSelectedItem(selected);
        else if (allowOff && model.getSize() > 0) box.setSelectedIndex(0);
        box.setEnabled(model.getSize() > (allowOff ? 1 : 0));
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
            try { command.run(); }
            catch (Exception ex) {
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
    private interface ThrowingRunnable { void run() throws Exception; }

    private record TrackOption(int id, String label, boolean selected) {
        @Override public String toString() { return label; }
    }

    private record PlayerState(double time, double duration, boolean paused) {}
    private record Tracks(List<TrackOption> audio, List<TrackOption> subtitles) {}
}
