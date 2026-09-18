package dev.streamflix.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

final class MainFrame extends JFrame {
    private enum Mode { MOVIES, SERIES, SEARCH, FAVORITES, HISTORY }

    private final List<Provider> providers;
    private Provider provider;

    private final JComboBox<String> providerBox = new JComboBox<>();
    private final JPanel grid = new JPanel();
    private final JLabel status = Theme.muted("Listo");
    private final JLabel sectionTitle = Theme.heading("Películas", 27f);
    private final JLabel sectionSubtitle = Theme.muted(" ");
    private final JTextField search = new JTextField(28);

    private final JButton moviesButton = Theme.navButton("Películas");
    private final JButton seriesButton = Theme.navButton("Series");
    private final JButton favoritesButton = Theme.navButton("Favoritos");
    private final JButton historyButton = Theme.navButton("Historial");
    private final JButton resetButton = Theme.button("Volver al inicio");
    private final JButton nextButton = Theme.button("Cargar más");
    private final JLabel pageLabel = Theme.muted("0 cargados");

    private final List<Models.ShowItem> loadedItems = new ArrayList<>();
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
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { MpvPlayer.shutdown(); }
        });
        setMinimumSize(new Dimension(1040, 700));
        setSize(1360, 850);
        setLocationRelativeTo(null);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());

        add(buildSidebar(), BorderLayout.WEST);
        add(buildWorkspace(), BorderLayout.CENTER);

        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { rebuildGridColumns(); }
        });

        installShortcuts();
        normalizeModeForProvider();
        updateNavigationState();
        updateHeader();
        loadPage();
    }

    @Override public void dispose() {
        try { MpvPlayer.shutdown(); }
        finally { super.dispose(); }
    }

    private JComponent buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(Theme.SIDEBAR);
        sidebar.setPreferredSize(new Dimension(224, 0));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER));

        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        brand.setBorder(new EmptyBorder(24, 20, 20, 20));

        JLabel logo = new JLabel("STREAMFLIX");
        logo.setForeground(Theme.TEXT);
        logo.setFont(Theme.FONT_BOLD.deriveFont(20f));
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel desktop = Theme.eyebrow("Windows");
        desktop.setForeground(Theme.ACCENT);
        desktop.setAlignmentX(Component.LEFT_ALIGNMENT);

        brand.add(logo);
        brand.add(Box.createVerticalStrut(3));
        brand.add(desktop);
        sidebar.add(brand, BorderLayout.NORTH);

        JPanel nav = new JPanel();
        nav.setOpaque(false);
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBorder(new EmptyBorder(10, 16, 16, 16));

        JLabel sourceLabel = Theme.eyebrow("Fuente");
        sourceLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        nav.add(sourceLabel);
        nav.add(Box.createVerticalStrut(8));

        providerBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        providerBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        providerBox.setPreferredSize(new Dimension(190, 38));
        providerBox.setToolTipText("Selecciona el catálogo o servicio");
        providerBox.addActionListener(e -> switchProvider(providerBox.getSelectedIndex()));
        nav.add(providerBox);

        nav.add(Box.createVerticalStrut(26));
        JLabel browseLabel = Theme.eyebrow("Explorar");
        browseLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        nav.add(browseLabel);
        nav.add(Box.createVerticalStrut(8));

        moviesButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        seriesButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        favoritesButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        historyButton.setAlignmentX(Component.LEFT_ALIGNMENT);

        moviesButton.addActionListener(e -> switchMode(Mode.MOVIES));
        seriesButton.addActionListener(e -> switchMode(Mode.SERIES));
        favoritesButton.addActionListener(e -> switchMode(Mode.FAVORITES));
        historyButton.addActionListener(e -> switchMode(Mode.HISTORY));

        nav.add(moviesButton);
        nav.add(Box.createVerticalStrut(4));
        nav.add(seriesButton);
        nav.add(Box.createVerticalStrut(16));
        nav.add(favoritesButton);
        nav.add(Box.createVerticalStrut(4));
        nav.add(historyButton);

        sidebar.add(nav, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBorder(new EmptyBorder(12, 16, 20, 16));

        JButton settings = Theme.navButton("Configuración");
        settings.setAlignmentX(Component.LEFT_ALIGNMENT);
        settings.setToolTipText("Configurar TMDb y opciones locales");
        settings.addActionListener(e -> openSettings());
        bottom.add(settings);
        bottom.add(Box.createVerticalStrut(12));

        JLabel hint = Theme.muted("Ctrl + F · Buscar");
        hint.setFont(Theme.FONT.deriveFont(11f));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottom.add(hint);
        sidebar.add(bottom, BorderLayout.SOUTH);
        return sidebar;
    }

    private JComponent buildWorkspace() {
        JPanel workspace = new JPanel(new BorderLayout());
        workspace.setBackground(Theme.BG);
        workspace.add(buildTopbar(), BorderLayout.NORTH);
        workspace.add(buildContent(), BorderLayout.CENTER);
        workspace.add(buildFooter(), BorderLayout.SOUTH);
        return workspace;
    }

    private JComponent buildTopbar() {
        JPanel top = new JPanel(new BorderLayout(24, 0));
        top.setBackground(Theme.BG);
        top.setBorder(new EmptyBorder(24, 34, 14, 34));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        sectionTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        sectionSubtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        sectionSubtitle.setFont(Theme.FONT.deriveFont(12.5f));
        heading.add(sectionTitle);
        heading.add(Box.createVerticalStrut(4));
        heading.add(sectionSubtitle);
        top.add(heading, BorderLayout.WEST);

        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchPanel.setOpaque(false);
        search.setToolTipText("Buscar dentro de la fuente seleccionada");
        search.setPreferredSize(new Dimension(310, 38));
        search.addActionListener(e -> runSearch());

        JButton searchButton = Theme.primaryButton("Buscar");
        searchButton.addActionListener(e -> runSearch());
        searchPanel.add(search, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);

        JPanel searchWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 2));
        searchWrap.setOpaque(false);
        searchWrap.add(searchPanel);
        top.add(searchWrap, BorderLayout.EAST);
        return top;
    }

    private JComponent buildContent() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Theme.BG);

        grid.setBackground(Theme.BG);
        grid.setBorder(new EmptyBorder(8, 34, 34, 34));
        rebuildGridColumns();
        wrapper.add(grid, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(wrapper);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.BG);
        scroll.getVerticalScrollBar().setUnitIncrement(28);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(16, 0));
        footer.setBackground(Theme.SIDEBAR);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                new EmptyBorder(10, 22, 10, 22)));
        status.setFont(Theme.FONT.deriveFont(12f));
        footer.add(status, BorderLayout.WEST);

        JPanel pager = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        pager.setOpaque(false);

        resetButton.addActionListener(e -> {
            if (page > 1) {
                page = 1;
                loadedItems.clear();
                loadPage(false);
            }
        });
        nextButton.addActionListener(e -> {
            page++;
            loadPage(true);
        });

        pageLabel.setPreferredSize(new Dimension(108, 34));
        pageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        pager.add(resetButton);
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
        if (providerBox.getSelectedIndex() != index) providerBox.setSelectedIndex(index);

        page = 1;
        loadedItems.clear();
        query = "";
        search.setText("");
        mode = provider.supportsMovies() ? Mode.MOVIES : Mode.SERIES;
        normalizeModeForProvider();
        updateNavigationState();
        updateHeader();
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
        loadedItems.clear();
        query = "";
        search.setText("");
        updateNavigationState();
        updateHeader();
        loadPage(false);
    }

    private void runSearch() {
        String q = search.getText().trim();
        if (q.isBlank()) return;
        mode = Mode.SEARCH;
        query = q;
        page = 1;
        loadedItems.clear();
        updateNavigationState();
        updateHeader();
        loadPage(false);
    }

    private void loadPage() {
        loadPage(false);
    }

    private void loadPage(boolean append) {
        if (activeWorker != null && !activeWorker.isDone()) activeWorker.cancel(true);
        Provider requestProvider = provider;
        Mode requestMode = mode;
        int requestPage = page;
        String requestQuery = query;

        if (!append) loadedItems.clear();

        if (requestProvider instanceof TmdbProvider) {
            try {
                if (!TmdbSettings.hasApiKey()) {
                    renderTmdbSetup();
                    setBusy(false, "TMDb requiere configuración");
                    pageLabel.setText("0 cargados");
                    nextButton.setEnabled(false);
                    resetButton.setVisible(false);
                    return;
                }
            } catch (TmdbException ex) {
                renderError(ex);
                setBusy(false, "Configuración de TMDb inválida");
                nextButton.setEnabled(false);
                return;
            }
        }

        String action = append
                ? "Cargando más contenido…"
                : requestMode == Mode.SEARCH
                    ? "Buscando “" + requestQuery + "”…"
                    : "Cargando " + labelForMode(requestMode) + "…";
        setBusy(true, action);
        pageLabel.setText(loadedItems.size() + " cargados");
        resetButton.setVisible(requestPage > 1);

        if (!append) renderLoading(requestProvider.name());

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
                    mergeLoaded(items, append);
                    render(loadedItems);
                    String text = loadedItems.isEmpty()
                            ? "Sin resultados · " + requestProvider.name()
                            : loadedItems.size() + " elementos cargados";
                    setBusy(false, text);
                    pageLabel.setText(loadedItems.size() + " cargados");

                    boolean paged = requestMode == Mode.MOVIES
                            || requestMode == Mode.SERIES
                            || requestMode == Mode.SEARCH;
                    nextButton.setVisible(paged);
                    nextButton.setEnabled(paged && !items.isEmpty());
                    resetButton.setVisible(paged && requestPage > 1);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (append && !loadedItems.isEmpty()) {
                        page = Math.max(1, page - 1);
                        setBusy(false, "No se pudo cargar más contenido");
                        pageLabel.setText(loadedItems.size() + " cargados");
                        nextButton.setEnabled(true);
                    } else {
                        renderError(cause);
                        setBusy(false, "No se pudo cargar " + requestProvider.name());
                        nextButton.setEnabled(false);
                    }
                }
            }
        };
        activeWorker.execute();
    }

    private void mergeLoaded(List<Models.ShowItem> items, boolean append) {
        if (!append) loadedItems.clear();
        LinkedHashMap<String, Models.ShowItem> unique = new LinkedHashMap<>();
        for (Models.ShowItem existing : loadedItems) unique.put(itemKey(existing), existing);
        for (Models.ShowItem item : items) unique.putIfAbsent(itemKey(item), item);
        loadedItems.clear();
        loadedItems.addAll(unique.values());
    }

    private static String itemKey(Models.ShowItem item) {
        return (item.sourceProviderId() == null ? "" : item.sourceProviderId()) + "|" + item.id();
    }

    private void render(List<Models.ShowItem> items) {
        grid.removeAll();
        rebuildGridColumns();

        if (items.isEmpty()) {
            renderState(
                    mode == Mode.FAVORITES ? "Todavía no tienes favoritos"
                            : mode == Mode.HISTORY ? "Aún no hay historial"
                            : "No encontramos resultados",
                    mode == Mode.FAVORITES ? "Marca títulos como favoritos para encontrarlos aquí."
                            : mode == Mode.HISTORY ? "Cuando reproduzcas contenido aparecerá en esta sección."
                            : "Prueba con otra búsqueda o cambia de fuente.",
                    null,
                    null);
            return;
        }

        for (Models.ShowItem item : items) grid.add(new ShowCard(item, this::openDetails));
        grid.revalidate();
        grid.repaint();
    }

    private void renderLoading(String source) {
        renderState("Cargando catálogo", "Consultando " + source + "…", null, null);
    }

    private void renderTmdbSetup() {
        renderState(
                "Configura TMDb",
                "TMDb ofrece el catálogo principal de películas y series. Añade una API key v3 o Read Access Token para activarlo.",
                "Configurar TMDb",
                this::openSettings);
    }

    private void renderError(Throwable error) {
        String message = error.getMessage() == null ? error.toString() : error.getMessage();
        renderState("No se pudo cargar el catálogo", message, "Reintentar", this::loadPage);
    }

    private void renderState(String title, String message, String actionText, Runnable action) {
        grid.removeAll();
        grid.setLayout(new GridBagLayout());

        JPanel card = Theme.surface();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setPreferredSize(new Dimension(520, 180));

        JLabel heading = Theme.heading(title, 19f);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel body = Theme.muted("<html><body style='width:430px'>" + escapeHtml(message) + "</body></html>");
        body.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(heading);
        card.add(Box.createVerticalStrut(9));
        card.add(body);

        if (actionText != null && action != null) {
            card.add(Box.createVerticalStrut(18));
            JButton button = Theme.primaryButton(actionText);
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.addActionListener(e -> action.run());
            card.add(button);
        }

        grid.add(card);
        grid.revalidate();
        grid.repaint();
    }

    private void openSettings() {
        new SettingsDialog(this).setVisible(true);
        if (provider instanceof TmdbProvider) loadPage(false);
    }

    private void openDetails(Models.ShowItem item) {
        Provider itemProvider = item.sourceProviderId() == null ? null : ProviderRegistry.get(item.sourceProviderId());
        if (itemProvider == null) itemProvider = provider;
        new DetailDialog(this, itemProvider, item).setVisible(true);
    }

    private void rebuildGridColumns() {
        int usable = Math.max(760, getWidth() - 224 - 82);
        int columns = Math.max(3, Math.min(7, usable / 205));
        grid.setLayout(new GridLayout(0, columns, 18, 22));
    }

    private void updateNavigationState() {
        Theme.setNavSelected(moviesButton, mode == Mode.MOVIES);
        Theme.setNavSelected(seriesButton, mode == Mode.SERIES);
        Theme.setNavSelected(favoritesButton, mode == Mode.FAVORITES);
        Theme.setNavSelected(historyButton, mode == Mode.HISTORY);
    }

    private void updateHeader() {
        String title = switch (mode) {
            case MOVIES -> "Películas";
            case SERIES -> provider instanceof M3uLiveProvider ? "TV en vivo" : "Series";
            case SEARCH -> query.isBlank() ? "Resultados" : "Resultados para “" + query + "”";
            case FAVORITES -> "Favoritos";
            case HISTORY -> "Historial";
        };
        sectionTitle.setText(title);

        String subtitle = switch (mode) {
            case FAVORITES -> "Tu biblioteca guardada";
            case HISTORY -> "Contenido reproducido recientemente";
            case SEARCH -> "Buscando en " + provider.name();
            default -> provider.name();
        };
        sectionSubtitle.setText(subtitle);
    }

    private String labelForMode(Mode value) {
        return switch (value) {
            case MOVIES -> "películas";
            case SERIES -> provider instanceof M3uLiveProvider ? "canales" : "series";
            case SEARCH -> "resultados";
            case FAVORITES -> "favoritos";
            case HISTORY -> "historial";
        };
    }

    private void setBusy(boolean busy, String text) {
        status.setText(text);
        moviesButton.setEnabled(!busy && provider.supportsMovies());
        seriesButton.setEnabled(!busy && provider.supportsTvShows());
        favoritesButton.setEnabled(!busy);
        historyButton.setEnabled(!busy);
        search.setEnabled(!busy);
        providerBox.setEnabled(!busy);
        resetButton.setEnabled(!busy && page > 1);
        if (busy) nextButton.setEnabled(false);
    }

    private void installShortcuts() {
        JRootPane root = getRootPane();
        InputMap input = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actions = root.getActionMap();

        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "focus-search");
        actions.put("focus-search", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                search.requestFocusInWindow();
                search.selectAll();
            }
        });

        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clear-search");
        actions.put("clear-search", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (mode == Mode.SEARCH) {
                    search.setText("");
                    switchMode(provider.supportsMovies() ? Mode.MOVIES : Mode.SERIES);
                }
            }
        });
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
