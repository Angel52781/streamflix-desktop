package dev.streamflix.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

final class MainFrame extends JFrame {
    private enum Mode { HOME, MOVIES, SERIES, LIVE, SEARCH, FAVORITES, HISTORY }

    private final List<Provider> providers;
    private final List<Provider> providerChoices = new ArrayList<>();
    private Provider provider;
    private boolean updatingProviderBox;

    private final JComboBox<String> providerBox = new JComboBox<>();
    private final JLabel sourceLabel = Theme.muted("Catálogo");
    private final JPanel sourceWrap = new JPanel(new BorderLayout(7, 0));
    private final JPanel contentStack = new JPanel(new CardLayout());
    private final JPanel grid = new JPanel();
    private final JPanel homeRoot = new JPanel();
    private final JPanel pagingBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));

    private final JLabel sectionTitle = Theme.heading("Inicio", 30f);
    private final JLabel sectionSubtitle = Theme.muted("Tu contenido, sin ruido");
    private final JLabel status = Theme.muted(" ");
    private final JTextField search = new JTextField(27);

    private final JButton homeButton = Theme.navButton("Inicio");
    private final JButton moviesButton = Theme.navButton("Películas");
    private final JButton seriesButton = Theme.navButton("Series");
    private final JButton liveButton = Theme.navButton("TV en vivo");
    private final JButton favoritesButton = Theme.navButton("Favoritos");
    private final JButton historyButton = Theme.navButton("Historial");

    private final JButton resetButton = Theme.button("Volver al inicio");
    private final JButton nextButton = Theme.button("Cargar más");
    private final JLabel pageLabel = Theme.muted("0 cargados");

    private final List<Models.ShowItem> loadedItems = new ArrayList<>();
    private Mode mode = Mode.HOME;
    private int page = 1;
    private String query = "";
    private SwingWorker<?, Void> activeWorker;

    MainFrame(List<Provider> providers) {
        super("Streamflix");
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("Sin providers configurados");
        }
        this.providers = List.copyOf(providers);
        this.provider = preferredVodProvider(true);

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        getRootPane().putClientProperty("JRootPane.titleBarBackground", Theme.SIDEBAR);
        getRootPane().putClientProperty("JRootPane.titleBarForeground", Theme.TEXT);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { MpvPlayer.shutdown(); }
        });
        setMinimumSize(new Dimension(1080, 700));
        setSize(1420, 880);
        setLocationRelativeTo(null);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());

        add(buildSidebar(), BorderLayout.WEST);
        add(buildWorkspace(), BorderLayout.CENTER);

        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                if (mode != Mode.HOME) rebuildGridColumns();
            }
        });

        installShortcuts();
        refreshProviderChoices();
        updateNavigationState();
        updateHeader();
        loadHome();
    }

    @Override public void dispose() {
        try { MpvPlayer.shutdown(); }
        finally { super.dispose(); }
    }

    private JComponent buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(Theme.SIDEBAR);
        sidebar.setPreferredSize(new Dimension(214, 0));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER));

        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        brand.setBorder(new EmptyBorder(26, 22, 20, 18));

        JLabel logo = new JLabel("STREAMFLIX");
        logo.setForeground(Theme.TEXT);
        logo.setFont(Theme.FONT_BOLD.deriveFont(21f));
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel edition = Theme.eyebrow("Desktop");
        edition.setForeground(Theme.ACCENT);
        edition.setAlignmentX(Component.LEFT_ALIGNMENT);

        brand.add(logo);
        brand.add(Box.createVerticalStrut(3));
        brand.add(edition);
        sidebar.add(brand, BorderLayout.NORTH);

        JPanel nav = new JPanel();
        nav.setOpaque(false);
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBorder(new EmptyBorder(10, 14, 14, 14));

        JLabel browse = Theme.eyebrow("Explorar");
        browse.setAlignmentX(Component.LEFT_ALIGNMENT);
        nav.add(browse);
        nav.add(Box.createVerticalStrut(8));

        for (JButton button : List.of(homeButton, moviesButton, seriesButton, liveButton)) {
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            nav.add(button);
            nav.add(Box.createVerticalStrut(4));
        }

        nav.add(Box.createVerticalStrut(18));
        JLabel library = Theme.eyebrow("Tu biblioteca");
        library.setAlignmentX(Component.LEFT_ALIGNMENT);
        nav.add(library);
        nav.add(Box.createVerticalStrut(8));

        for (JButton button : List.of(favoritesButton, historyButton)) {
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            nav.add(button);
            nav.add(Box.createVerticalStrut(4));
        }

        homeButton.addActionListener(e -> switchMode(Mode.HOME));
        moviesButton.addActionListener(e -> switchMode(Mode.MOVIES));
        seriesButton.addActionListener(e -> switchMode(Mode.SERIES));
        liveButton.addActionListener(e -> switchMode(Mode.LIVE));
        favoritesButton.addActionListener(e -> switchMode(Mode.FAVORITES));
        historyButton.addActionListener(e -> switchMode(Mode.HISTORY));

        sidebar.add(nav, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBorder(new EmptyBorder(12, 14, 20, 14));

        JButton settings = Theme.navButton("Configuración");
        settings.setAlignmentX(Component.LEFT_ALIGNMENT);
        settings.addActionListener(e -> openSettings());
        bottom.add(settings);
        bottom.add(Box.createVerticalStrut(12));

        JLabel shortcut = Theme.muted("Ctrl + F  Buscar");
        shortcut.setFont(Theme.FONT.deriveFont(11f));
        shortcut.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottom.add(shortcut);

        sidebar.add(bottom, BorderLayout.SOUTH);
        return sidebar;
    }

    private JComponent buildWorkspace() {
        JPanel workspace = new JPanel(new BorderLayout());
        workspace.setBackground(Theme.BG);
        workspace.add(buildTopbar(), BorderLayout.NORTH);
        workspace.add(buildContent(), BorderLayout.CENTER);
        return workspace;
    }

    private JComponent buildTopbar() {
        JPanel top = new JPanel(new BorderLayout(28, 0));
        top.setBackground(Theme.BG);
        top.setBorder(new EmptyBorder(26, 34, 16, 34));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        sectionTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        sectionSubtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        sectionSubtitle.setFont(Theme.FONT.deriveFont(13f));
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        status.setFont(Theme.FONT.deriveFont(11.5f));

        heading.add(sectionTitle);
        heading.add(Box.createVerticalStrut(4));
        heading.add(sectionSubtitle);
        heading.add(Box.createVerticalStrut(3));
        heading.add(status);
        top.add(heading, BorderLayout.WEST);

        JPanel tools = new JPanel();
        tools.setOpaque(false);
        tools.setLayout(new BoxLayout(tools, BoxLayout.Y_AXIS));

        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        searchRow.setOpaque(false);

        search.putClientProperty("JTextField.placeholderText", "Buscar películas, series o canales");
        search.putClientProperty("JTextField.showClearButton", true);
        search.setPreferredSize(new Dimension(330, 40));
        search.setToolTipText("Buscar en la fuente seleccionada");
        search.addActionListener(e -> runSearch());

        JButton searchButton = Theme.primaryButton("Buscar");
        searchButton.addActionListener(e -> runSearch());

        sourceWrap.setOpaque(false);
        sourceWrap.setBorder(new EmptyBorder(0, 0, 0, 4));
        sourceLabel.setFont(Theme.FONT.deriveFont(12f));
        sourceWrap.add(sourceLabel, BorderLayout.WEST);
        providerBox.setPreferredSize(new Dimension(205, 38));
        providerBox.setToolTipText("El catálogo elegido determina dónde se buscan los títulos.");
        providerBox.addActionListener(e -> {
            if (!updatingProviderBox) switchProvider(providerBox.getSelectedIndex());
        });
        sourceWrap.add(providerBox, BorderLayout.CENTER);

        searchRow.add(sourceWrap);
        searchRow.add(search);
        searchRow.add(searchButton);
        tools.add(searchRow);
        top.add(tools, BorderLayout.EAST);
        return top;
    }

    private JComponent buildContent() {
        contentStack.setBackground(Theme.BG);

        JPanel catalogBody = new JPanel(new BorderLayout());
        catalogBody.setBackground(Theme.BG);

        grid.setBackground(Theme.BG);
        grid.setBorder(new EmptyBorder(8, 34, 20, 34));
        rebuildGridColumns();
        catalogBody.add(grid, BorderLayout.NORTH);

        pagingBar.setOpaque(false);
        pagingBar.setBorder(new EmptyBorder(6, 0, 32, 0));
        resetButton.addActionListener(e -> {
            page = 1;
            loadedItems.clear();
            loadPage(false);
        });
        nextButton.addActionListener(e -> {
            page++;
            loadPage(true);
        });
        pagingBar.add(resetButton);
        pagingBar.add(pageLabel);
        pagingBar.add(nextButton);
        catalogBody.add(pagingBar, BorderLayout.SOUTH);

        JScrollPane catalogScroll = new JScrollPane(catalogBody);
        catalogScroll.setBorder(null);
        catalogScroll.getViewport().setBackground(Theme.BG);
        catalogScroll.getVerticalScrollBar().setUnitIncrement(30);
        catalogScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        homeRoot.setBackground(Theme.BG);
        homeRoot.setLayout(new BoxLayout(homeRoot, BoxLayout.Y_AXIS));
        homeRoot.setBorder(new EmptyBorder(2, 34, 36, 34));

        JPanel homeViewport = new JPanel(new BorderLayout());
        homeViewport.setBackground(Theme.BG);
        homeViewport.add(homeRoot, BorderLayout.NORTH);

        JScrollPane homeScroll = new JScrollPane(homeViewport);
        homeScroll.setBorder(null);
        homeScroll.getViewport().setBackground(Theme.BG);
        homeScroll.getVerticalScrollBar().setUnitIncrement(30);
        homeScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        contentStack.add(homeScroll, Mode.HOME.name());
        contentStack.add(catalogScroll, "CATALOG");
        return contentStack;
    }

    private void switchMode(Mode newMode) {
        if (newMode == mode && newMode != Mode.HOME) return;
        if (activeWorker != null && !activeWorker.isDone()) activeWorker.cancel(true);

        mode = newMode;
        page = 1;
        query = "";
        loadedItems.clear();
        search.setText("");

        if (newMode == Mode.MOVIES) provider = compatibleOrPreferred(true, false);
        else if (newMode == Mode.SERIES) provider = compatibleOrPreferred(false, false);
        else if (newMode == Mode.LIVE) provider = compatibleOrPreferred(false, true);

        refreshProviderChoices();
        updateNavigationState();
        updateHeader();

        if (newMode == Mode.HOME) loadHome();
        else loadPage(false);
    }

    private Provider compatibleOrPreferred(boolean movies, boolean live) {
        if (provider != null
                && (provider instanceof M3uLiveProvider) == live
                && (live || (movies ? provider.supportsMovies() : provider.supportsTvShows()))) {
            return provider;
        }
        return live ? preferredLiveProvider() : preferredVodProvider(movies);
    }

    private Provider preferredVodProvider(boolean movies) {
        boolean tmdbReady = tmdbReady();
        if (tmdbReady) {
            Provider spanishTmdb = ProviderRegistry.get("tmdb-es");
            if (spanishTmdb != null && (movies ? spanishTmdb.supportsMovies() : spanishTmdb.supportsTvShows())) {
                return spanishTmdb;
            }
            Provider englishTmdb = ProviderRegistry.get("tmdb-en");
            if (englishTmdb != null && (movies ? englishTmdb.supportsMovies() : englishTmdb.supportsTvShows())) {
                return englishTmdb;
            }
        }
        for (Provider p : providers) {
            if (p instanceof M3uLiveProvider || p instanceof TmdbProvider) continue;
            if (movies ? p.supportsMovies() : p.supportsTvShows()) return p;
        }
        for (Provider p : providers) {
            if (!(p instanceof M3uLiveProvider) && (movies ? p.supportsMovies() : p.supportsTvShows())) return p;
        }
        return providers.get(0);
    }

    private Provider preferredLiveProvider() {
        Provider plutoEs = ProviderRegistry.get("pluto-es");
        if (plutoEs != null) return plutoEs;
        return providers.stream().filter(M3uLiveProvider.class::isInstance).findFirst().orElse(providers.get(0));
    }

    private boolean tmdbReady() {
        try { return TmdbSettings.hasApiKey(); }
        catch (TmdbException ignored) { return false; }
    }

    private void refreshProviderChoices() {
        providerChoices.clear();

        if (mode == Mode.LIVE) {
            providerChoices.addAll(providers.stream().filter(M3uLiveProvider.class::isInstance).toList());
        } else if (mode == Mode.MOVIES) {
            providerChoices.addAll(providers.stream()
                    .filter(p -> !(p instanceof M3uLiveProvider) && p.supportsMovies()).toList());
        } else if (mode == Mode.SERIES) {
            providerChoices.addAll(providers.stream()
                    .filter(p -> !(p instanceof M3uLiveProvider) && p.supportsTvShows()).toList());
        } else if (mode == Mode.SEARCH) {
            boolean live = provider instanceof M3uLiveProvider;
            providerChoices.addAll(providers.stream()
                    .filter(p -> (p instanceof M3uLiveProvider) == live).toList());
        }

        updatingProviderBox = true;
        try {
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
            for (Provider p : providerChoices) model.addElement(p.name());
            providerBox.setModel(model);

            int selected = providerChoices.indexOf(provider);
            if (selected < 0 && !providerChoices.isEmpty()) {
                provider = providerChoices.get(0);
                selected = 0;
            }
            if (selected >= 0) providerBox.setSelectedIndex(selected);
        } finally {
            updatingProviderBox = false;
        }

        boolean liveCatalog = mode == Mode.LIVE || (mode == Mode.SEARCH && provider instanceof M3uLiveProvider);
        sourceLabel.setText(liveCatalog ? "Lista" : "Catálogo");
        sourceWrap.setVisible(mode == Mode.MOVIES || mode == Mode.SERIES || mode == Mode.LIVE || mode == Mode.SEARCH);
    }

    private void switchProvider(int index) {
        if (index < 0 || index >= providerChoices.size()) return;
        Provider selected = providerChoices.get(index);
        if (selected.id().equals(provider.id())) return;

        if (activeWorker != null && !activeWorker.isDone()) activeWorker.cancel(true);
        provider = selected;
        page = 1;
        loadedItems.clear();
        updateHeader();
        loadPage(false);
    }

    private void runSearch() {
        String q = search.getText().trim();
        if (q.isBlank()) return;

        if (mode == Mode.HOME || mode == Mode.FAVORITES || mode == Mode.HISTORY) {
            provider = preferredVodProvider(true);
        }
        mode = Mode.SEARCH;
        query = q;
        page = 1;
        loadedItems.clear();
        refreshProviderChoices();
        updateNavigationState();
        updateHeader();
        loadPage(false);
    }

    private void loadHome() {
        ((CardLayout) contentStack.getLayout()).show(contentStack, Mode.HOME.name());
        sourceWrap.setVisible(false);
        status.setText("Preparando tu inicio…");
        homeRoot.removeAll();
        homeRoot.add(buildHomeSkeleton());
        homeRoot.revalidate();
        homeRoot.repaint();

        Provider movieSource = preferredVodProvider(true);
        Provider seriesSource = preferredVodProvider(false);
        Provider liveSource = preferredLiveProvider();

        activeWorker = new SwingWorker<HomeData, Void>() {
            @Override protected HomeData doInBackground() {
                List<Models.ShowItem> history = UserData.getHistory();
                List<Models.ShowItem> movies = safeLoad(() -> movieSource.movies(1), 10);
                List<Models.ShowItem> series = safeLoad(() -> seriesSource.tvShows(1), 10);
                List<Models.ShowItem> live = safeLoad(() -> liveSource.tvShows(1), 10);
                return new HomeData(history.stream().limit(10).toList(), movies, series, live,
                        movieSource.name(), seriesSource.name(), liveSource.name());
            }

            @Override protected void done() {
                if (isCancelled()) return;
                try {
                    renderHome(get());
                    status.setText(" ");
                } catch (Exception ex) {
                    renderHomeError(ex.getCause() == null ? ex : ex.getCause());
                }
            }
        };
        activeWorker.execute();
    }

    private JComponent buildHomeSkeleton() {
        JPanel skeleton = Theme.surface();
        skeleton.setLayout(new BoxLayout(skeleton, BoxLayout.Y_AXIS));
        skeleton.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel title = Theme.heading("Cargando tu inicio…", 18f);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel detail = Theme.muted("Preparando películas, series y TV en vivo.");
        detail.setAlignmentX(Component.LEFT_ALIGNMENT);
        skeleton.add(title);
        skeleton.add(Box.createVerticalStrut(7));
        skeleton.add(detail);
        return skeleton;
    }

    private void renderHome(HomeData data) {
        homeRoot.removeAll();

        if (!data.history().isEmpty()) {
            homeRoot.add(homeSection("Vistos recientemente", "Retoma lo último que abriste", data.history()));
            homeRoot.add(Box.createVerticalStrut(30));
        }

        if (!data.movies().isEmpty()) {
            homeRoot.add(homeSection("Películas", data.movieSource(), data.movies()));
            homeRoot.add(Box.createVerticalStrut(30));
        }

        if (!data.series().isEmpty()) {
            homeRoot.add(homeSection("Series", data.seriesSource(), data.series()));
            homeRoot.add(Box.createVerticalStrut(30));
        }

        if (!data.live().isEmpty()) {
            homeRoot.add(homeSection("TV en vivo", data.liveSource(), data.live()));
        }

        if (homeRoot.getComponentCount() == 0) {
            homeRoot.add(stateCard("No pudimos cargar el inicio",
                    "Prueba Películas, Series o TV en vivo desde la barra lateral.", null, null));
        }

        homeRoot.revalidate();
        homeRoot.repaint();
    }

    private JComponent homeSection(String title, String subtitle, List<Models.ShowItem> items) {
        JPanel section = new JPanel(new BorderLayout(0, 12));
        section.setOpaque(false);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 395));

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel label = Theme.heading(title, 21f);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel sub = Theme.muted(subtitle);
        sub.setFont(Theme.FONT.deriveFont(12f));
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(label);
        text.add(Box.createVerticalStrut(3));
        text.add(sub);
        heading.add(text, BorderLayout.WEST);
        section.add(heading, BorderLayout.NORTH);

        JPanel rail = new JPanel();
        rail.setOpaque(false);
        rail.setLayout(new BoxLayout(rail, BoxLayout.X_AXIS));
        rail.setBorder(new EmptyBorder(1, 1, 8, 8));

        for (int i = 0; i < items.size(); i++) {
            rail.add(new ShowCard(items.get(i), this::openDetails));
            if (i < items.size() - 1) rail.add(Box.createHorizontalStrut(14));
        }

        JScrollPane scroll = new JScrollPane(rail);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.getHorizontalScrollBar().setUnitIncrement(28);
        scroll.setPreferredSize(new Dimension(800, 330));
        section.add(scroll, BorderLayout.CENTER);
        return section;
    }

    private void renderHomeError(Throwable error) {
        homeRoot.removeAll();
        homeRoot.add(stateCard("No pudimos preparar el inicio",
                error.getMessage() == null ? "Reintenta en unos segundos." : error.getMessage(),
                "Reintentar", this::loadHome));
        homeRoot.revalidate();
        homeRoot.repaint();
    }

    private void loadPage(boolean append) {
        ((CardLayout) contentStack.getLayout()).show(contentStack, "CATALOG");

        if (activeWorker != null && !activeWorker.isDone()) activeWorker.cancel(true);
        Provider requestProvider = provider;
        Mode requestMode = mode;
        int requestPage = page;
        String requestQuery = query;

        if (!append) loadedItems.clear();

        if (requestProvider instanceof TmdbProvider && !tmdbReady()) {
            renderTmdbSetup();
            status.setText("TMDb necesita una credencial gratuita");
            configurePaging(false, false);
            return;
        }

        setBusy(true, append ? "Cargando más…" : "Cargando…");
        if (!append) renderLoading(requestProvider.name());

        activeWorker = new SwingWorker<List<Models.ShowItem>, Void>() {
            @Override protected List<Models.ShowItem> doInBackground() throws Exception {
                return switch (requestMode) {
                    case MOVIES -> requestProvider.movies(requestPage);
                    case SERIES, LIVE -> requestProvider.tvShows(requestPage);
                    case SEARCH -> requestProvider.search(requestQuery, requestPage);
                    case FAVORITES -> UserData.getFavorites();
                    case HISTORY -> UserData.getHistory();
                    case HOME -> List.of();
                };
            }

            @Override protected void done() {
                if (isCancelled()) return;
                try {
                    List<Models.ShowItem> items = get();
                    mergeLoaded(items, append);
                    renderCatalog(loadedItems);

                    boolean paged = requestMode == Mode.MOVIES || requestMode == Mode.SERIES
                            || requestMode == Mode.LIVE || requestMode == Mode.SEARCH;
                    configurePaging(paged, !items.isEmpty());
                    pageLabel.setText(loadedItems.size() + " cargados");
                    status.setText(loadedItems.isEmpty()
                            ? "Sin resultados"
                            : loadedItems.size() + " elementos");
                    setBusy(false, status.getText());
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (append && !loadedItems.isEmpty()) {
                        page = Math.max(1, page - 1);
                        status.setText("No se pudo cargar más");
                        configurePaging(true, true);
                        setBusy(false, status.getText());
                    } else {
                        renderError(cause);
                        configurePaging(false, false);
                        setBusy(false, "No se pudo cargar");
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

    private void renderCatalog(List<Models.ShowItem> items) {
        grid.removeAll();
        rebuildGridColumns();

        if (items.isEmpty()) {
            String title = switch (mode) {
                case FAVORITES -> "Todavía no tienes favoritos";
                case HISTORY -> "Aún no hay historial";
                default -> "No encontramos resultados";
            };
            String message = switch (mode) {
                case FAVORITES -> "Guarda títulos desde su ficha y aparecerán aquí.";
                case HISTORY -> "Tu historial se irá creando a medida que reproduzcas contenido.";
                default -> "Prueba otra búsqueda o cambia la fuente.";
            };
            renderGridState(title, message, null, null);
            return;
        }

        for (Models.ShowItem item : items) grid.add(new ShowCard(item, this::openDetails));
        grid.revalidate();
        grid.repaint();
    }

    private void renderLoading(String source) {
        renderGridState("Cargando catálogo", "Consultando " + source + "…", null, null);
    }

    private void renderTmdbSetup() {
        renderGridState("Activa TMDb",
                "TMDb es el catálogo principal de películas y series. Su API de desarrollador es gratuita para uso no comercial. Configura tu API key o Read Access Token una sola vez.",
                "Configurar TMDb", this::openSettings);
    }

    private void renderError(Throwable error) {
        renderGridState("No se pudo cargar",
                error.getMessage() == null ? "Revisa tu conexión e inténtalo de nuevo." : error.getMessage(),
                "Reintentar", () -> loadPage(false));
    }

    private void renderGridState(String title, String message, String actionText, Runnable action) {
        grid.removeAll();
        grid.setLayout(new GridBagLayout());
        grid.add(stateCard(title, message, actionText, action));
        grid.revalidate();
        grid.repaint();
    }

    private JComponent stateCard(String title, String message, String actionText, Runnable action) {
        JPanel card = Theme.surface();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setPreferredSize(new Dimension(560, 180));

        JLabel heading = Theme.heading(title, 20f);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel body = Theme.muted("<html><body style='width:455px'>" + escapeHtml(message) + "</body></html>");
        body.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(heading);
        card.add(Box.createVerticalStrut(9));
        card.add(body);

        if (actionText != null && action != null) {
            card.add(Box.createVerticalStrut(18));
            JButton actionButton = Theme.primaryButton(actionText);
            actionButton.setAlignmentX(Component.LEFT_ALIGNMENT);
            actionButton.addActionListener(e -> action.run());
            card.add(actionButton);
        }
        return card;
    }

    private void configurePaging(boolean visible, boolean canLoadMore) {
        pagingBar.setVisible(visible);
        resetButton.setVisible(visible && page > 1);
        nextButton.setVisible(visible);
        nextButton.setEnabled(canLoadMore);
        pageLabel.setVisible(visible);
    }

    private void openSettings() {
        new SettingsDialog(this).setVisible(true);
        if (mode == Mode.HOME) {
            provider = preferredVodProvider(true);
            loadHome();
        } else if (provider instanceof TmdbProvider) {
            loadPage(false);
        }
    }

    private void openDetails(Models.ShowItem item) {
        Provider itemProvider = item.sourceProviderId() == null ? null : ProviderRegistry.get(item.sourceProviderId());
        if (itemProvider == null) itemProvider = provider;
        new DetailDialog(this, itemProvider, item).setVisible(true);
    }

    private void rebuildGridColumns() {
        int usable = Math.max(760, getWidth() - 214 - 86);
        int columns = Math.max(3, Math.min(7, usable / 205));
        grid.setLayout(new GridLayout(0, columns, 18, 22));
    }

    private void updateNavigationState() {
        Theme.setNavSelected(homeButton, mode == Mode.HOME);
        Theme.setNavSelected(moviesButton, mode == Mode.MOVIES || (mode == Mode.SEARCH && !(provider instanceof M3uLiveProvider)));
        Theme.setNavSelected(seriesButton, mode == Mode.SERIES);
        Theme.setNavSelected(liveButton, mode == Mode.LIVE || (mode == Mode.SEARCH && provider instanceof M3uLiveProvider));
        Theme.setNavSelected(favoritesButton, mode == Mode.FAVORITES);
        Theme.setNavSelected(historyButton, mode == Mode.HISTORY);
    }

    private void updateHeader() {
        String title = switch (mode) {
            case HOME -> "Inicio";
            case MOVIES -> "Películas";
            case SERIES -> "Series";
            case LIVE -> "TV en vivo";
            case SEARCH -> query.isBlank() ? "Resultados" : "Resultados para “" + query + "”";
            case FAVORITES -> "Favoritos";
            case HISTORY -> "Historial";
        };
        sectionTitle.setText(title);

        String subtitle = switch (mode) {
            case HOME -> "Películas, series y canales en un solo lugar";
            case FAVORITES -> "Tu biblioteca guardada";
            case HISTORY -> "Lo que abriste recientemente";
            case LIVE -> provider == null ? "Canales en directo" : provider.name();
            case SEARCH -> provider == null ? "Búsqueda" : "Buscando en " + provider.name();
            default -> provider == null ? "" : provider.name();
        };
        sectionSubtitle.setText(subtitle);
        status.setText(" ");
    }

    private void setBusy(boolean busy, String text) {
        status.setText(text);
        for (JButton button : List.of(homeButton, moviesButton, seriesButton, liveButton, favoritesButton, historyButton)) {
            button.setEnabled(!busy);
        }
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

        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escape-search");
        actions.put("escape-search", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (mode == Mode.SEARCH) switchMode(Mode.HOME);
                else search.setText("");
            }
        });
    }

    @FunctionalInterface
    private interface Loader {
        List<Models.ShowItem> load() throws Exception;
    }

    private static List<Models.ShowItem> safeLoad(Loader loader, int limit) {
        try {
            List<Models.ShowItem> items = loader.load();
            return items.stream().limit(limit).toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private record HomeData(
            List<Models.ShowItem> history,
            List<Models.ShowItem> movies,
            List<Models.ShowItem> series,
            List<Models.ShowItem> live,
            String movieSource,
            String seriesSource,
            String liveSource
    ) {}

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
