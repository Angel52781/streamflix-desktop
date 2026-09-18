package dev.streamflix.desktop;

import com.sun.jna.Native;
import com.sun.jna.Pointer;

import javax.swing.*;
import java.awt.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Opt-in live validation of embedded mpv + Windows named-pipe IPC. */
public final class EmbeddedPlayerLiveTest {
    public static void main(String[] args) throws Exception {
        Theme.install();
        Models.Video video = new VixSrcExtractor().extract("https://vixsrc.to/api/movie/550?lang=en");

        JFrame frame = new JFrame("Embedded mpv live test");
        Canvas canvas = new Canvas();
        canvas.setBackground(Color.BLACK);
        SwingUtilities.invokeAndWait(() -> {
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.setSize(960, 600);
            frame.add(canvas);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });

        try {
            AtomicLong handle = new AtomicLong();
            SwingUtilities.invokeAndWait(() -> {
                Pointer pointer = Native.getComponentPointer(canvas);
                if (pointer != null) handle.set(Pointer.nativeValue(pointer));
            });
            if (handle.get() == 0L) throw new AssertionError("No native Canvas handle");

            String pipe = "\\\\.\\pipe\\streamflix-embed-test-" + UUID.randomUUID();
            long request = MpvPlayer.beginRequest();
            MpvPlayer.playEmbedded(video, "Embedded test", request, handle.get(), pipe);

            try (MpvIpcClient ipc = MpvIpcClient.connect(pipe, 5000)) {
                ipc.setProperty("volume", 0);
                Object duration = waitProperty(ipc, "duration", 10000);
                Object tracks = waitProperty(ipc, "track-list", 10000);
                if (!(duration instanceof Number n) || n.doubleValue() <= 0) {
                    throw new AssertionError("Invalid duration: " + duration);
                }
                if (!(tracks instanceof java.util.List<?>)) {
                    throw new AssertionError("Invalid track-list");
                }
                ipc.setProperty("pause", true);
                Thread.sleep(300);
                Object paused = ipc.getProperty("pause");
                if (!Boolean.TRUE.equals(paused)) throw new AssertionError("Pause IPC failed");
                System.out.println("EMBEDDED_MPV_OK duration=" + Math.round(n.doubleValue())
                        + " tracks=" + ((java.util.List<?>) tracks).size());
            }
        } finally {
            try { MpvPlayer.stopCurrent(); } catch (Exception ignored) {}
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }

    private static Object waitProperty(MpvIpcClient ipc, String name, long timeoutMillis) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        Exception last = null;
        while (System.nanoTime() < deadline) {
            try { return ipc.getProperty(name); }
            catch (Exception ex) { last = ex; Thread.sleep(150); }
        }
        throw last == null ? new IllegalStateException("Property unavailable: " + name) : last;
    }
}
