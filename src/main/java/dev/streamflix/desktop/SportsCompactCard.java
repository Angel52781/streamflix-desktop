package dev.streamflix.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;

final class SportsCompactCard extends JPanel {
    SportsCompactCard(SportsEvent event, Consumer<SportsEvent> onPlay) {
        setLayout(new BorderLayout(10, 8));
        setBackground(Theme.PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                new EmptyBorder(14, 14, 14, 14)));
        setPreferredSize(new Dimension(300, 146));
        setMaximumSize(new Dimension(300, 146));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel eyebrow = Theme.eyebrow(event.sport() + " · " + event.league());
        JLabel title = Theme.heading(event.hasTeams()
                ? event.homeTeam() + " vs " + event.awayTeam() : event.name(), 15f);
        JLabel status = Theme.muted(status(event));
        eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(eyebrow);
        text.add(Box.createVerticalStrut(6));
        text.add(title);
        text.add(Box.createVerticalStrut(5));
        text.add(status);
        add(text, BorderLayout.CENTER);

        JButton play = Theme.primaryButton("▶ Ver");
        play.setEnabled(!event.broadcasters().isEmpty());
        play.setToolTipText(play.isEnabled()
                ? "Buscar la mejor señal disponible"
                : "Todavía no hay broadcaster asociado a este evento");
        play.addActionListener(e -> onPlay.accept(event));
        add(play, BorderLayout.SOUTH);
    }

    private static String status(SportsEvent event) {
        if (event.homeScore() != null && event.awayScore() != null) {
            String progress = event.progress() == null || event.progress().isBlank() ? "" : " · " + event.progress();
            return event.homeScore() + " — " + event.awayScore() + progress;
        }
        if (event.startsAt() != null) {
            return DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", new Locale("es", "PE"))
                    .format(event.startsAt().atZone(ZoneId.systemDefault()));
        }
        return event.status() == null || event.status().isBlank() ? "En vivo" : event.status();
    }
}
