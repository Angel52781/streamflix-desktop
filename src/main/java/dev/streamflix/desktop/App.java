package dev.streamflix.desktop;

import javax.swing.*;
import java.util.Arrays;

public final class App {
    public static void main(String[] args) {
        if (Arrays.asList(args).contains("--self-test")) {
            System.exit(SelfTest.run());
        }

        System.setProperty("sun.awt.noerasebackground", "true");
        Theme.install();
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame(ProviderRegistry.all());
            frame.setVisible(true);
        });
    }
}
