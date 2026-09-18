package dev.streamflix.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;

/** Streaming-style episode row with still image, synopsis and explicit actions. */
final class EpisodeRow extends JPanel {
    EpisodeRow(Models.Episode episode,
               Consumer<Models.Episode> onPlay,
               Consumer<Models.Episode> onServer) {
        setLayout(new BorderLayout(16, 0));
        setBackground(new Color(16, 19, 26));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                new EmptyBorder(10, 10, 10, 12)));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 126));
        setPreferredSize(new Dimension(760, 126));

        ArtworkPanel still = new ArtworkPanel(episode.poster());
        still.setPreferredSize(new Dimension(176, 99));
        add(still, BorderLayout.WEST);

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));

        String titleText = episode.title() == null || episode.title().isBlank()
                ? "Episodio " + episode.episodeNumber()
                : episode.title();

        JLabel title = Theme.heading("E" + episode.episodeNumber() + " · " + titleText, 14f);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        String overviewText = episode.overview() == null || episode.overview().isBlank()
                ? "Sin descripción disponible."
                : episode.overview();
        JLabel overview = Theme.muted("<html><body style='width:430px'>"
                + escape(shorten(overviewText, 180)) + "</body></html>");
        overview.setFont(Theme.FONT.deriveFont(12f));
        overview.setAlignmentX(Component.LEFT_ALIGNMENT);

        copy.add(title);
        copy.add(Box.createVerticalStrut(7));
        copy.add(overview);
        add(copy, BorderLayout.CENTER);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));

        JButton play = Theme.primaryButton("Reproducir");
        play.setAlignmentX(Component.CENTER_ALIGNMENT);
        play.addActionListener(e -> onPlay.accept(episode));

        JButton server = Theme.button("Servidor");
        server.setAlignmentX(Component.CENTER_ALIGNMENT);
        server.addActionListener(e -> onServer.accept(episode));

        actions.add(Box.createVerticalGlue());
        actions.add(play);
        actions.add(Box.createVerticalStrut(7));
        actions.add(server);
        actions.add(Box.createVerticalGlue());

        add(actions, BorderLayout.EAST);

        MouseAdapter open = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) onPlay.accept(episode);
            }
        };
        addMouseListener(open);
        still.addMouseListener(open);
        copy.addMouseListener(open);
    }

    private static String shorten(String value, int max) {
        if (value == null || value.length() <= max) return value == null ? "" : value;
        int cut = value.lastIndexOf(' ', max);
        if (cut < max / 2) cut = max;
        return value.substring(0, cut).strip() + "…";
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
