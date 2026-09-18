package dev.streamflix.desktop;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

final class UpdateService {
    static final String OWNER = "Angel52781";
    static final String REPO = "streamflix-desktop";
    static final String STABLE_ZIP = "StreamflixDesktop-windows.zip";
    static final String STABLE_SHA = STABLE_ZIP + ".sha256";
    private static final URI LATEST_API = URI.create(
            "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest");

    private UpdateService() {}

    record Asset(String name, URI downloadUri, long size) {}

    record ReleaseInfo(String version, String tag, String name, String notes,
                       URI pageUri, Asset zip, Asset checksum) {}
    static CompletableFuture<Optional<ReleaseInfo>> checkAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(12))
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .build();
                HttpRequest request = HttpRequest.newBuilder(LATEST_API)
                        .timeout(Duration.ofSeconds(20))
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "StreamflixDesktop/" + AppVersion.current())
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(
                        request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("GitHub respondió HTTP " + response.statusCode());
                }
                return findUpdate(response.body(), AppVersion.current());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    static Optional<ReleaseInfo> findUpdate(String json, String currentVersion) {
        Map<String, Object> root = Json.object(Json.parse(json));
        if (Boolean.TRUE.equals(root.get("draft")) || Boolean.TRUE.equals(root.get("prerelease"))) {
            return Optional.empty();
        }
        String tag = Json.string(root.get("tag_name")).strip();
        String version = normalizeVersion(tag);
        if (version.isBlank() || compareVersions(version, currentVersion) <= 0) {
            return Optional.empty();
        }

        Asset stableZip = null;
        Asset fallbackZip = null;
        Asset stableSha = null;
        Asset fallbackSha = null;

        for (Object raw : Json.array(root.get("assets"))) {
            Map<String, Object> asset = Json.object(raw);
            String name = Json.string(asset.get("name")).strip();
            String url = Json.string(asset.get("browser_download_url")).strip();
            if (name.isBlank() || url.isBlank()) continue;

            long size = 0L;
            Object rawSize = asset.get("size");
            if (rawSize instanceof Number n) size = Math.max(0L, n.longValue());
            Asset candidate = new Asset(name, URI.create(url), size);

            if (STABLE_ZIP.equalsIgnoreCase(name)) stableZip = candidate;
            else if (name.toLowerCase().endsWith("-windows.zip")) fallbackZip = candidate;

            if (STABLE_SHA.equalsIgnoreCase(name)) stableSha = candidate;
            else if (name.toLowerCase().endsWith(".zip.sha256")) fallbackSha = candidate;
        }

        Asset zip = stableZip != null ? stableZip : fallbackZip;
        Asset checksum = stableSha != null ? stableSha : fallbackSha;
        if (zip == null || checksum == null) return Optional.empty();
        String page = Json.string(root.get("html_url")).strip();
        if (page.isBlank()) {
            page = "https://github.com/" + OWNER + "/" + REPO + "/releases/tag/" + tag;
        }
        String releaseName = Json.string(root.get("name")).strip();
        if (releaseName.isBlank()) releaseName = "Streamflix Desktop " + tag;

        return Optional.of(new ReleaseInfo(
                version,
                tag,
                releaseName,
                Json.string(root.get("body")),
                URI.create(page),
                zip,
                checksum
        ));
    }

    static int compareVersions(String left, String right) {
        int[] a = parseVersion(left);
        int[] b = parseVersion(right);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private static int[] parseVersion(String value) {
        String normalized = normalizeVersion(value);
        if (normalized.isBlank()) return new int[] {0};
        String[] parts = normalized.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException ex) {
                out[i] = 0;
            }
        }
        return out;
    }

    private static String normalizeVersion(String value) {
        if (value == null) return "";
        String text = value.strip();
        if (text.startsWith("v") || text.startsWith("V")) text = text.substring(1);
        int dash = text.indexOf('-');
        if (dash >= 0) text = text.substring(0, dash);
        return text.matches("\\d+(?:\\.\\d+)*") ? text : "";
    }

    static void checkAndPrompt(Component parent, boolean reportNoUpdate) {
        CompletableFuture<Optional<ReleaseInfo>> future = checkAsync();
        future.whenComplete((result, error) -> SwingUtilities.invokeLater(() -> {
            if (error != null) {
                AppLog.warn("updates", "No se pudo comprobar GitHub Releases.", error);
                if (reportNoUpdate) {
                    JOptionPane.showMessageDialog(parent,
                            "No se pudo comprobar GitHub Releases.\n" + rootMessage(error),
                            "Actualizaciones", JOptionPane.WARNING_MESSAGE);
                }
                return;
            }
            if (result.isEmpty()) {
                if (reportNoUpdate) {
                    JOptionPane.showMessageDialog(parent,
                            "Estás usando la versión más reciente disponible.",
                            "Actualizaciones", JOptionPane.INFORMATION_MESSAGE);
                }
                return;
            }
            promptForUpdate(parent, result.get());
        }));
    }

    private static void promptForUpdate(Component parent, ReleaseInfo release) {
        JTextArea notes = new JTextArea(updateText(release));
        notes.setEditable(false);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setOpaque(false);
        notes.setFont(Theme.FONT);
        notes.setForeground(Theme.TEXT);
        notes.setRows(9);
        notes.setColumns(58);

        JScrollPane noteScroll = new JScrollPane(notes);
        noteScroll.setBorder(null);
        noteScroll.setOpaque(false);
        noteScroll.getViewport().setOpaque(false);

        boolean selfUpdate = PortableUpdater.canSelfUpdate();
        Object[] options = selfUpdate
                ? new Object[] {"Actualizar ahora", "Ver release", "Más tarde"}
                : new Object[] {"Ver release", "Más tarde"};

        int selected = JOptionPane.showOptionDialog(
                parent, noteScroll, "Actualización disponible · " + release.tag(),
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, options, options[0]);

        if (selfUpdate && selected == 0) {
            PortableUpdater.downloadAndInstall(parent, release);
        } else if ((selfUpdate && selected == 1) || (!selfUpdate && selected == 0)) {
            openRelease(parent, release.pageUri());
        }
    }
    private static String updateText(ReleaseInfo release) {
        StringBuilder text = new StringBuilder();
        text.append("Streamflix ").append(release.version())
                .append(" está disponible.\n");
        text.append("Versión instalada: ").append(AppVersion.current()).append("\n\n");
        if (release.notes() == null || release.notes().isBlank()) {
            text.append("Consulta GitHub para ver los cambios de esta versión.");
        } else {
            String notes = release.notes().strip();
            if (notes.length() > 1600) notes = notes.substring(0, 1600) + "\n…";
            text.append(notes);
        }
        if (!PortableUpdater.canSelfUpdate()) {
            text.append("\n\nEsta ejecución no es un paquete portable actualizable; ")
                    .append("se abrirá GitHub Releases.");
        }
        return text.toString();
    }

    private static void openRelease(Component parent, URI uri) {
        try {
            if (!Desktop.isDesktopSupported()) throw new IllegalStateException("Desktop no disponible");
            Desktop.getDesktop().browse(uri);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent,
                    "No se pudo abrir GitHub Releases.\n" + uri,
                    "Actualizaciones", JOptionPane.WARNING_MESSAGE);
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName() : message;
    }
}
