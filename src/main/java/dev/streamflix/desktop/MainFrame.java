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
    private final JScrollPane catalogScroll = new JScrollPane();

    private final JLabel sectionTitle = Theme.heading("Inicio", 30f);
    private final JLabel sectionSubtitle = Theme.muted("Tu contenido, sin ruido");
    private final JLabel status = Theme.muted(" ");
    private final JPanel sectionHeaderPanel = new JPanel(new BorderLayout());
    private final JTextField search = new JTextField(27);

    private final JButton homeButton = Theme.topNavButton("Inicio");
    private final JButton moviesButton = Theme.topNavButton("Películas");
    private final JButton seriesButton = Theme.topNavButton("Series");
    private final JButton liveButton = Theme.topNavButton("TV");
    private final JButton favoritesButton = Theme.topNavButton("Favoritos");
    private final JButton historyButton = Theme.topNavButton("Historial");

    private final JButton resetButton = Theme.button("Volver al inicio");
    private final JButton nextButton = Theme.button("Cargar más");
    private final JLabel pageLabel = Theme.muted("0 cargados");

    private final List<Models.ShowItem> loadedItems = new ArrayList<>();
    private Mode mode = Mode.HOME;
    private int page = 1;
    private String query = "";
    private boolean hasMore = true;
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
        setMinimumSize(new Dimension(1180, 720));
        setSize(1480, 900);
        setLocationRelativeTo(null);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());

        wireNavigation();
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

    private void wireNavigation() {
        homeButton.addActionListener(e -> switchMode(Mode.HOME));
        moviesButton.addActionListener(e -> switchMode(Mode.MOVIES));
        seriesButton.addActionListener(e -> switchMode(Mode.SERIES));
        liveButton.addActionListener(e -> switchMode(Mode.LIVE));
        favoritesButton.addActionListener(e -> switchMode(Mode.FAVORITES));
        historyButton.addActionListener(e -> switchMode(Mode.HISTORY));
    }

    private JComponent buildWorkspace() {
        JPanel workspace = new JPanel(new BorderLayout());
        workspace.setBackground(Theme.BG);

        JPanel north = new JPanel();
        north.setBackground(Theme.BG);
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.add(buildTopbar());
        north.add(buildSectionHeader());

        workspace.add(north, BorderLayout.NORTH);
        workspace.add(buildContent(), BorderLayout.CENTER);
        return workspace;
    }

    private JComponent buildTopbar() {
        JPanel top = new JPanel(new BorderLayout(18, 0));
        top.setBackground(new Color(8, 10, 15));
        top.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                new EmptyBorder(12, 24, 12, 24)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);

        JLabel logo = new JLabel("STREAMFLIX");
        logo.setForeground(Theme.ACCENT);
        logo.setFont(Theme.FONT_BOLD.deriveFont(20f));
        logo.setBorder(new EmptyBorder(0, 0, 0, 12));
        left.add(logo);

        for (JButton button : List.of(homeButton, moviesButton, seriesButton, liveButton, favoritesButton, historyButton)) {
            left.add(button);
        }
        top.add(left, BorderLayout.WEST);

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        tools.setOpaque(false);

        sourceWrap.setOpaque(false);
        sourceWrap.setBorder(new EmptyBorder(0, 0, 0, 2));
        sourceLabel.setFont(Theme.FONT.deriveFont(11.5f));
        sourceWrap.add(sourceLabel, BorderLayout.WEST);

        providerBox.setPreferredSize(new Dimension(175, 36));
        providerBox.setToolTipText("El catálogo elegido determina dónde se buscan los títulos.");
        providerBox.addActionListener(e -> {
            if (!updatingProviderBox) switchProvider(providerBox.getSelectedIndex());
        });
        sourceWrap.add(providerBox, BorderLayout.CENTER);
        tools.add(sourceWrap);

        search.putClientProperty("JTextField.placeholderText", "Buscar títulos");
        search.putClientProperty("JTextField.showClearButton", true);
        search.setPreferredSize(new Dimension(245, 38));
        search.setToolTipText("Buscar en el catálogo seleccionado");
        search.addActionListener(e -> runSearch());
        tools.add(search);

        JButton searchButton = Theme.primaryButton("Buscar");
        searchButton.addActionListener(e -> runSearch());
        tools.add(searchButton);

        JButton settings = Theme.button("Ajustes");
        settings.addActionListener(e -> openSettings());
        tools.add(settings);

        top.add(tools, BorderLayout.EAST);
        return top;
    }

    private JComponent buildSectionHeader() {
        sectionHeaderPanel.setBackground(Theme.BG);
        sectionHeaderPanel.setBorder(new EmptyBorder(18, 34, 10, 34));

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

        sectionHeaderPanel.add(heading, BorderLayout.WEST);
        return sectionHeaderPanel;
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

        catalogScroll.setViewportView(catalogBody);
        catalogScroll.setBorder(null);
        catalogScroll.getViewport().setBackground(Theme.BG);
        catalogScroll.getVerticalScrollBar().setUnitIncrement(30);
        catalogScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        catalogScroll.getVerticalScrollBar().addAdjustmentListener(e -> maybeLoadMore());

        pagingBar.setVisible(false);

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
        hasMore = true;
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
        hasMore = true;
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
        hasMore = true;
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

        Models.ShowItem hero = !data.series().isEmpty()
                ? data.series().get(0)
                : !data.movies().isEmpty() ? data.movies().get(0) : null;
        if (hero != null) {
            homeRoot.add(new HeroPanel(hero, this::openDetails));
            homeRoot.add(Box.createVerticalStrut(28));
        }

        if (!data.history().isEmpty()) {
            homeRoot.add(homeSection("Continuar explorando", "Lo último que abriste", data.history()));
            homeRoot.add(Box.createVerticalStrut(30));
        }

        if (!data.movies().isEmpty()) {
            homeRoot.add(homeSection("Películas populares", data.movieSource(), data.movies()));
            homeRoot.add(Box.createVerticalStrut(30));
        }

        if (!data.series().isEmpty()) {
            homeRoot.add(homeSection("Series populares", data.seriesSource(), data.series()));
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
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 270));

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
            rail.add(new LandscapeCard(items.get(i), this::openDetails));
            if (i < items.size() - 1) rail.add(Box.createHorizontalStrut(12));
        }

        JScrollPane scroll = new JScrollPane(rail);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.getHorizontalScrollBar().setUnitIncrement(28);
        scroll.setPreferredSize(new Dimension(800, 210));
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

        if (append && !hasMore) return;
        if (activeWorker != null && !activeWorker.isDone()) {
            if (append) return;
            activeWorker.cancel(true);
        }
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

        if (append) status.setText("Cargando más…");
        else setBusy(true, "Cargando…");
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
                    hasMore = paged && !items.isEmpty();
                    configurePaging(false, false);
                    pageLabel.setText(loadedItems.size() + " cargados");
                    status.setText(loadedItems.isEmpty() ? "Sin resultados" : " ");
                    if (!append) setBusy(false, status.getText());
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (append && !loadedItems.isEmpty()) {
                        page = Math.max(1, page - 1);
                        status.setText("No se pudo cargar más");
                        configurePaging(false, false);
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

        for (Models.ShowItem item : items) grid.add(new LandscapeCard(item, this::openDetails));
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

    private void maybeLoadMore() {
        if (!hasMore || loadedItems.isEmpty()) return;
        if (activeWorker != null && !activeWorker.isDone()) return;

        boolean paged = mode == Mode.MOVIES || mode == Mode.SERIES
                || mode == Mode.LIVE || mode == Mode.SEARCH;
        if (!paged) return;

        JScrollBar bar = catalogScroll.getVerticalScrollBar();
        int remaining = bar.getMaximum() - (bar.getValue() + bar.getVisibleAmount());
        if (remaining > 260) return;

        page++;
        loadPage(true);
    }

    private void configurePaging(boolean visible, boolean canLoadMore) {
        pagingBar.setVisible(false);
        resetButton.setVisible(false);
        nextButton.setVisible(false);
        nextButton.setEnabled(false);
        pageLabel.setVisible(false);
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
        int usable = Math.max(900, getWidth() - 86);
        int columns = Math.max(2, Math.min(5, usable / 310));
        grid.setLayout(new GridLayout(0, columns, 18, 24));
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
        sectionHeaderPanel.setVisible(mode != Mode.HOME);

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
