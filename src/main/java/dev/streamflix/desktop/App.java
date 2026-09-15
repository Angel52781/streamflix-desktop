package dev.streamflix.desktop;

import javax.swing.*;

public final class App {
    public static void main(String[] args) {
        System.setProperty("sun.awt.noerasebackground", "true");
        Theme.install();
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame(new FanpelisProvider());
            frame.setVisible(true);
        });
    }
}
