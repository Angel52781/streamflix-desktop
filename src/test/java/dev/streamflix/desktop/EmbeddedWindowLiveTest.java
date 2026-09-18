package dev.streamflix.desktop;

import javax.swing.*;

/** Opt-in live smoke of the actual Streamflix embedded-player window. */
public final class EmbeddedWindowLiveTest {
    public static void main(String[] args) throws Exception {
        Theme.install();
        Models.Video video = new VixSrcExtractor().extract("https://vixsrc.to/api/movie/550?lang=en");

        final EmbeddedPlayerWindow[] holder = new EmbeddedPlayerWindow[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = EmbeddedPlayerWindow.open(null, "Streamflix embedded window test");
            holder[0].setPreparing("Validando pantalla de carga…");
        });

        long request = MpvPlayer.beginRequest();
        try {
            holder[0].start(video, "VixSrc", request);
            Thread.sleep(1800);
            if (!holder[0].isDisplayable()) throw new AssertionError("Player window closed unexpectedly");
            System.out.println("EMBEDDED_WINDOW_OK");
        } finally {
            SwingUtilities.invokeAndWait(holder[0]::dispose);
        }
    }
}
