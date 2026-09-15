package dev.streamflix.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

final class MainFrame extends JFrame {
    private enum Mode { MOVIES, SERIES, SEARCH }

    private final Provider provider;
    private final JPanel grid = new JPanel();
    private final JLabel status = Theme.muted("Listo");
    private final JTextField search = new JTextField(24);
    private final JButton moviesButton = Theme.button("Películas");
    private final JButton seriesButton = Theme.button("Series");
    private final JButton prevButton = Theme.button("‹");
    private final JButton nextButton = Theme.button("›");
    private final JLabel pageLabel = new JLabel("1");
    private Mode mode = Mode.MOVIES;
    private int page = 1;
    private String query = "";
    private SwingWorker<List<Models.ShowItem>, Void> activeWorker;

    MainFrame(Provider provider) {
        super("Streamflix Desktop");
        this.provider = provider;
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(980, 680));
        setSize(1280, 820);
        setLocationRelativeTo(null);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());

        add(buildHeader(), BorderLayout.NORTH);
        add(buildContent(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { rebuildGridColumns(); }
        });

        loadPage();
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setBackground(Theme.PANEL);
        header.setBorder(new EmptyBorder(12, 18, 12, 18));

        JLabel brand = new JLabel("STREAMFLIX");
        brand.setForeground(Theme.ACCENT);
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 22f));
        header.add(brand, BorderLayout.WEST);

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        nav.setOpaque(false);
        moviesButton.addActionListener(e -> switchMode(Mode.MOVIES));
        seriesButton.addActionListener(e -> switchMode(Mode.SERIES));
        nav.add(moviesButton);
        nav.add(seriesButton);
        header.add(nav, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        search.setToolTipText("Buscar películas y series");
        search.addActionListener(e -> runSearch());
        JButton searchButton = Theme.button("Buscar");
        searchButton.addActionListener(e -> runSearch());
        right.add(search);
        right.add(searchButton);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JComponent buildContent() {
        grid.setBackground(Theme.BG);
        grid.setBorder(new EmptyBorder(18, 18, 18, 18));
        rebuildGridColumns();

        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(22);
        return scroll;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(Theme.PANEL);
        footer.setBorder(new EmptyBorder(8, 14, 8, 14));
        footer.add(status, BorderLayout.WEST);

        JPanel pager = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        pager.setOpaque(false);
        prevButton.addActionListener(e -> { if (page > 1) { page--; loadPage(); } });
        nextButton.addActionListener(e -> { page++; loadPage(); });
        pager.add(prevButton);
        pageLabel.setPreferredSize(new Dimension(36, 30));
        pageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        pager.add(pageLabel);
        pager.add(nextButton);
        footer.add(pager, BorderLayout.EAST);
        return footer;
    }

    private void switchMode(Mode newMode) {
        mode = newMode;
        page = 1;
        query = "";
        search.setText("");
        loadPage();
    }

    private void runSearch() {
        String q = search.getText().trim();
        if (q.isBlank()) return;
        mode = Mode.SEARCH;
        query = q;
        page = 1;
        loadPage();
    }

    private void loadPage() {
        if (activeWorker != null && !activeWorker.isDone()) activeWorker.cancel(true);
        setBusy(true, mode == Mode.SEARCH ? "Buscando “" + query + "”…" : "Cargando " + labelForMode() + "…");
        pageLabel.setText(String.valueOf(page));
        prevButton.setEnabled(page > 1);
        grid.removeAll();
        JLabel loading = Theme.muted("Cargando desde " + provider.name() + "…");
        loading.setFont(loading.getFont().deriveFont(16f));
        grid.add(loading);
        grid.revalidate(); grid.repaint();

        activeWorker = new SwingWorker<>() {
            @Override protected List<Models.ShowItem> doInBackground() throws Exception {
                return switch (mode) {
                    case MOVIES -> provider.movies(page);
                    case SERIES -> provider.tvShows(page);
                    case SEARCH -> provider.search(query, page);
                };
            }

            @Override protected void done() {
                if (isCancelled()) return;
                try {
                    List<Models.ShowItem> items = get();
                    render(items);
                    setBusy(false, items.isEmpty() ? "Sin resultados" : items.size() + " resultados · " + provider.name());
                    nextButton.setEnabled(!items.isEmpty());
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    renderError(cause);
                    setBusy(false, "Error de conexión");
                    nextButton.setEnabled(false);
                }
            }
        };
        activeWorker.execute();
    }

    private void render(List<Models.ShowItem> items) {
        grid.removeAll();
        rebuildGridColumns();
        if (items.isEmpty()) {
            JLabel empty = Theme.muted("No se encontraron resultados.");
            empty.setFont(empty.getFont().deriveFont(16f));
            grid.add(empty);
        } else {
            for (Models.ShowItem item : items) {
                grid.add(new ShowCard(item, this::openDetails));
            }
        }
        grid.revalidate();
        grid.repaint();
    }

    private void renderError(Throwable error) {
        grid.removeAll();
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("No se pudo cargar el catálogo");
        title.setForeground(Theme.TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        JLabel message = Theme.muted(error.getMessage() == null ? error.toString() : error.getMessage());
        JButton retry = Theme.button("Reintentar");
        retry.addActionListener(e -> loadPage());
        panel.add(title); panel.add(Box.createVerticalStrut(8)); panel.add(message); panel.add(Box.createVerticalStrut(14)); panel.add(retry);
        grid.add(panel);
        grid.revalidate(); grid.repaint();
    }

    private void openDetails(Models.ShowItem item) {
        new DetailDialog(this, provider, item).setVisible(true);
    }

    private void rebuildGridColumns() {
        int usable = Math.max(900, getWidth() - 55);
        int columns = Math.max(4, usable / 195);
        grid.setLayout(new GridLayout(0, columns, 14, 14));
    }

    private String labelForMode() {
        return switch (mode) {
            case MOVIES -> "películas";
            case SERIES -> "series";
            case SEARCH -> "resultados";
        };
    }

    private void setBusy(boolean busy, String text) {
        status.setText(text);
        moviesButton.setEnabled(!busy);
        seriesButton.setEnabled(!busy);
        search.setEnabled(!busy);
    }
}
