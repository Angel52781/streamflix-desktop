package dev.streamflix.desktop;

import javax.swing.*;
import java.awt.*;

/** Opt-in live gate for Windows exclusive fullscreen acquisition/release. */
public final class FullscreenLiveTest {
    public static void main(String[] args) throws Exception {
        Theme.install();
        final JFrame[] frame = new JFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame("Streamflix fullscreen test");
            f.setUndecorated(true);
            f.setSize(800, 450);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            frame[0] = f;
        });

        GraphicsDevice device = frame[0].getGraphicsConfiguration().getDevice();
        try {
            SwingUtilities.invokeAndWait(() -> device.setFullScreenWindow(frame[0]));
            if (device.getFullScreenWindow() != frame[0]) {
                throw new AssertionError("Fullscreen window was not acquired");
            }
            Thread.sleep(350);
            System.out.println("FULLSCREEN_OK bounds=" + frame[0].getBounds().width + "x" + frame[0].getBounds().height);
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                device.setFullScreenWindow(null);
                frame[0].dispose();
            });
        }
    }
}
