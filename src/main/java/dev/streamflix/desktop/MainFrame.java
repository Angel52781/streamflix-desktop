package dev.streamflix.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

final class MainFrame extends JFrame {
    private enum Mode { MOVIES, SERIES, SEARCH, FAVORITES, HISTORY }

    private final List<Provider> providers;
    private Provider provider;
    private final JComboBox<String> providerBox = new JComboBox<>();
    private final JPanel grid = new JPanel();
    private final JLabel status = Theme.muted("Listo");
    private final JTextField search = new JTextField(22);
    private final JButton moviesButton = Theme.button("Películas");
    private final JButton seriesButton = Theme.button("Series");
    private final JButton favoritesButton = Theme.button("Favoritos");
    private final JButton historyButton = Theme.button("Historial");
    private final JButton prevButton = Theme.button("◄");
    private final JButton nextButton = Theme.button("►");
    private final JLabel pageLabel = new JLabel("1");
    private Mode mode = Mode.MOVIES;
    private int page = 1;
    private String query = "";
    private SwingWorker<List<Models.ShowItem>, Void> activeWorker;

    MainFrame(List<Provider> providers) {
        super("Streamflix Desktop");
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("Sin providers configurados");
        }
        this.providers = List.copyOf(providers);
        this.provider = this.providers.get(0);
        for (Provider item : this.providers) providerBox.addItem(item.name());

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
        normalizeModeForProvider();
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
        nav.add(Theme.muted("Fuente:"));
        providerBox.setToolTipText("Proveedor de contenido");
        providerBox.addActionListener(e -> switchProvider(providerBox.getSelectedIndex()));
        nav.add(providerBox);
        moviesButton.addActionListener(e -> switchMode(Mode.MOVIES));
        seriesButton.addActionListener(e -> switchMode(Mode.SERIES));
        favoritesButton.addActionListener(e -> switchMode(Mode.FAVORITES));
        historyButton.addActionListener(e -> switchMode(Mode.HISTORY));
        nav.add(moviesButton);
        nav.add(seriesButton);
        nav.add(favoritesButton);
        nav.add(historyButton);
        header.add(nav, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        search.setToolTipText("Buscar en el proveedor actual");
        search.addActionListener(e -> runSearch());
        JButton searchButton = Theme.button("Buscar");
        searchButton.addActionListener(e -> runSearch());
        right.add(search);
        right.add(searchButton);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JComponent buildContent() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Theme.BG);
        
        grid.setBackground(Theme.BG);
        grid.setBorder(new EmptyBorder(18, 18, 18, 18));
        rebuildGridColumns();
        wrapper.add(grid, BorderLayout.NORTH);
        
        JScrollPane scroll = new JScrollPane(wrapper);
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
        prevButton.addActionListener(e -> {
            if (page > 1) { page--; loadPage(); }
        });
        nextButton.addActionListener(e -> { page++; loadPage(); });
        pager.add(prevButton);
        pageLabel.setPreferredSize(new Dimension(36, 30));
        pageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        pager.add(pageLabel);
        pager.add(nextButton);
        footer.add(pager, BorderLayout.EAST);
        return footer;
    }

    void switchProvider(int index) {
        if (index < 0 || index >= providers.size()) return;
        Provider selected = providers.get(index);
        if (selected == provider) return;
        if (activeWorker != null && !activeWorker.isDone()) activeWorker.cancel(true);
        provider = selected;
        if (providerBox.getSelectedIndex() != index) {
            providerBox.setSelectedIndex(index);
        }
        page = 1;
        query = "";
        search.setText("");
        mode = provider.supportsMovies() ? Mode.MOVIES : Mode.SERIES;
        normalizeModeForProvider();
        loadPage();
    }

    private void normalizeModeForProvider() {
        if (mode == Mode.MOVIES && !provider.supportsMovies()) mode = Mode.SERIES;
        if (mode == Mode.SERIES && !provider.supportsTvShows()) mode = Mode.MOVIES;
        moviesButton.setVisible(provider.supportsMovies());
        seriesButton.setVisible(provider.supportsTvShows());
    }

    private void switchMode(Mode newMode) {
        if (newMode == Mode.MOVIES && !provider.supportsMovies()) return;
        if (newMode == Mode.SERIES && !provider.supportsTvShows()) return;
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
        Provider requestProvider = provider;
        Mode requestMode = mode;
        int requestPage = page;
        String requestQuery = query;

        String action = requestMode == Mode.SEARCH
                ? "Buscando “" + requestQuery + "”…"
                : "Cargando " + labelForMode(requestMode) + "…";
        setBusy(true, action);
        pageLabel.setText(String.valueOf(requestPage));
        prevButton.setEnabled(requestPage > 1);
        grid.removeAll();
        JLabel loading = Theme.muted("Cargando desde " + requestProvider.name() + "…");
        loading.setFont(loading.getFont().deriveFont(16f));
        grid.add(loading);
        grid.revalidate();
        grid.repaint();

        activeWorker = new SwingWorker<>() {
            @Override protected List<Models.ShowItem> doInBackground() throws Exception {
                return switch (requestMode) {
                    case MOVIES -> requestProvider.movies(requestPage);
                    case SERIES -> requestProvider.tvShows(requestPage);
                    case SEARCH -> requestProvider.search(requestQuery, requestPage);
                    case FAVORITES -> UserData.getFavorites();
                    case HISTORY -> UserData.getHistory();
                };
            }

            @Override protected void done() {
                if (isCancelled()) return;
                try {
                    List<Models.ShowItem> items = get();
                    render(items);
                    String text = items.isEmpty()
                            ? "Sin resultados · " + requestProvider.name()
                            : items.size() + " resultados · " + requestProvider.name();
                    setBusy(false, text);
                    nextButton.setEnabled(!items.isEmpty());
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    renderError(cause);
                    setBusy(false, "Error de conexión · " + requestProvider.name());
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
        panel.add(title);
        panel.add(Box.createVerticalStrut(8));
        panel.add(message);
        panel.add(Box.createVerticalStrut(14));
        panel.add(retry);
        grid.add(panel);
        grid.revalidate();
        grid.repaint();
    }

    private void openDetails(Models.ShowItem item) {
        Provider itemProvider = ProviderRegistry.get(item.providerId());
        if (itemProvider == null) itemProvider = provider;
        new DetailDialog(this, itemProvider, item).setVisible(true);
    }

    private void rebuildGridColumns() {
        int usable = Math.max(900, getWidth() - 55);
        int columns = Math.max(4, usable / 195);
        grid.setLayout(new GridLayout(0, columns, 14, 14));
    }

    private String labelForMode(Mode value) {
        return switch (value) {
            case MOVIES -> "pel\u00edculas";
            case SERIES -> "series";
            case SEARCH -> "resultados";
            case FAVORITES -> "favoritos";
            case HISTORY -> "historial";
        };
    }

    private void setBusy(boolean busy, String text) {
        status.setText(text);
        moviesButton.setEnabled(!busy && provider.supportsMovies());
        seriesButton.setEnabled(!busy && provider.supportsTvShows());
        search.setEnabled(!busy);
        providerBox.setEnabled(!busy);
        prevButton.setEnabled(!busy && page > 1);
        if (busy) nextButton.setEnabled(false);
    }
}
