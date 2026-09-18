package dev.streamflix.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PortableUpdater {
    private static final Pattern SHA_256 = Pattern.compile("(?i)\\b([0-9a-f]{64})\\b");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private PortableUpdater() {}

    static boolean canSelfUpdate() {
        try {
            Path installDir = installDir();
            if (!Files.isRegularFile(installDir.resolve("StreamflixDesktop.exe"))) return false;
            Path probe = Files.createTempFile(installDir.getParent(), ".streamflix-write-", ".tmp");
            Files.deleteIfExists(probe);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    static void downloadAndInstall(Component parent, UpdateService.ReleaseInfo release) {
        ProgressDialog dialog = new ProgressDialog(parent, release.version());

        SwingWorker<Path, Void> worker = new SwingWorker<>() {
            @Override protected Path doInBackground() throws Exception {
                Path work = Files.createTempDirectory("streamflix-update-" + release.version() + "-");
                Path archive = work.resolve(release.zip().name());

                String checksumText = downloadText(release.checksum());
                String expected = expectedSha(checksumText);
                downloadFile(release.zip(), archive, (read, total) -> {
                    int percent = total <= 0 ? 0
                            : (int) Math.min(100L, Math.round(read * 100.0 / total));
                    setProgress(percent);
                });

                String actual = sha256(archive);
                if (!actual.equalsIgnoreCase(expected)) {
                    throw new IllegalStateException(
                            "La verificación SHA-256 falló. La actualización no se instalará.");
                }

                setProgress(100);
                return writeUpdaterScript(work, archive);
            }

            @Override protected void done() {
                try {
                    Path script = get();
                    dialog.dispose();
                    launchUpdater(script);
                    System.exit(0);
                } catch (Exception ex) {
                    AppLog.error("updater", "No se pudo preparar la actualización.", ex);
                    dialog.dispose();
                    JOptionPane.showMessageDialog(parent,
                            "No se pudo preparar la actualización.\n" + rootMessage(ex),
                            "Actualización de Streamflix", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.addPropertyChangeListener(event -> {
            if ("progress".equals(event.getPropertyName())) {
                dialog.setProgress((Integer) event.getNewValue());
            }
        });

        worker.execute();
        dialog.setVisible(true);
    }

    static String expectedSha(String checksumText) {
        Matcher matcher = SHA_256.matcher(checksumText == null ? "" : checksumText);
        if (!matcher.find()) throw new IllegalArgumentException("El checksum publicado no es válido.");
        return matcher.group(1).toUpperCase();
    }

    private static String downloadText(UpdateService.Asset asset) throws Exception {
        HttpRequest request = request(asset).build();
        HttpResponse<String> response = HTTP.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response.statusCode(), asset.name());
        return response.body();
    }

    private static void downloadFile(UpdateService.Asset asset, Path target,
                                     Progress progress) throws Exception {
        HttpRequest request = request(asset).build();
        HttpResponse<InputStream> response = HTTP.send(
                request, HttpResponse.BodyHandlers.ofInputStream());
        ensureSuccess(response.statusCode(), asset.name());

        long total = response.headers().firstValueAsLong("Content-Length")
                .orElse(asset.size());
        Files.createDirectories(target.getParent());

        long read = 0L;
        try (InputStream input = response.body();
             var output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count == 0) continue;
                output.write(buffer, 0, count);
                read += count;
                progress.accept(read, total);
            }
        }
    }

    private static HttpRequest.Builder request(UpdateService.Asset asset) {
        return HttpRequest.newBuilder(asset.downloadUri())
                .timeout(Duration.ofMinutes(10))
                .header("Accept", "application/octet-stream")
                .header("User-Agent", "StreamflixDesktop/" + AppVersion.current())
                .GET();
    }

    private static void ensureSuccess(int status, String assetName) {
        if (status < 200 || status >= 300) {
            throw new IllegalStateException(
                    "GitHub respondió HTTP " + status + " al descargar " + assetName);
        }
    }
    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[128 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count > 0) digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest()).toUpperCase();
    }

    private static Path installDir() {
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath != null && !appPath.isBlank()) {
            Path path = Path.of(appPath).toAbsolutePath().normalize();
            if (Files.isDirectory(path)) return path;
            Path parent = path.getParent();
            if (parent != null) return parent;
        }

        String command = ProcessHandle.current().info().command().orElse("");
        if (!command.isBlank()) {
            Path path = Path.of(command).toAbsolutePath().normalize();
            if ("StreamflixDesktop.exe".equalsIgnoreCase(path.getFileName().toString())) {
                return path.getParent();
            }
        }
        throw new IllegalStateException("Esta ejecución no proviene del paquete portable.");
    }

    private static Path writeUpdaterScript(Path work, Path archive) throws Exception {
        Path installDir = installDir();
        long pid = ProcessHandle.current().pid();
        Path script = work.resolve("install-update.ps1");
        String install = psLiteral(installDir);
        String zip = psLiteral(archive);
        String content = """
                $ErrorActionPreference = 'Stop'
                Set-Location $env:TEMP
                $pidToWait = %d
                $installDir = %s
                $archive = %s
                $parent = Split-Path -Parent $installDir
                $stage = Join-Path $parent ('.streamflix-stage-' + [guid]::NewGuid().ToString('N'))
                $backup = $installDir + '.previous'
                $errorLog = Join-Path $env:TEMP 'Streamflix-update-error.log'
                try {
                    Wait-Process -Id $pidToWait -ErrorAction SilentlyContinue
                    Expand-Archive -LiteralPath $archive -DestinationPath $stage -Force
                    $payload = Join-Path $stage 'StreamflixDesktop'
                    if (-not (Test-Path (Join-Path $payload 'StreamflixDesktop.exe'))) {
                        $payload = $stage
                    }
                    if (Test-Path $backup) { Remove-Item -LiteralPath $backup -Recurse -Force }
                    Move-Item -LiteralPath $installDir -Destination $backup
                    Move-Item -LiteralPath $payload -Destination $installDir
                    $exe = Join-Path $installDir 'StreamflixDesktop.exe'
                    $newProcess = Start-Process -FilePath $exe -PassThru
                    Start-Sleep -Seconds 4
                    if ($newProcess.HasExited) {
                        throw 'La nueva versión terminó durante el arranque.'
                    }
                    Remove-Item -LiteralPath $backup -Recurse -Force
                    if (Test-Path $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
                } catch {
                    try {
                        if (Test-Path $installDir) {
                            Remove-Item -LiteralPath $installDir -Recurse -Force
                        }
                        if (Test-Path $backup) {
                            Move-Item -LiteralPath $backup -Destination $installDir
                            Start-Process -FilePath (Join-Path $installDir 'StreamflixDesktop.exe')
                        }
                    } catch {}
                    ($_ | Out-String) | Set-Content -LiteralPath $errorLog -Encoding UTF8
                }
                """.formatted(pid, install, zip);

        Files.writeString(script, content, StandardCharsets.UTF_8);
        return script;
    }

    private static String psLiteral(Path path) {
        return "'" + path.toAbsolutePath().normalize().toString().replace("'", "''") + "'";
    }

    private static void launchUpdater(Path script) throws Exception {
        new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-File", script.toString())
                .directory(script.getParent().toFile())
                .start();
    }
    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName() : message;
    }

    @FunctionalInterface
    private interface Progress {
        void accept(long read, long total);
    }

    private static final class ProgressDialog extends JDialog {
        private final JProgressBar progress = new JProgressBar(0, 100);

        ProgressDialog(Component parent, String version) {
            super(SwingUtilities.getWindowAncestor(parent),
                    "Actualizando Streamflix", ModalityType.APPLICATION_MODAL);
            setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

            JPanel body = new JPanel();
            body.setBorder(new EmptyBorder(20, 22, 20, 22));
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

            JLabel title = Theme.heading("Descargando Streamflix " + version, 18f);
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(title);
            body.add(Box.createVerticalStrut(8));

            JLabel detail = Theme.muted(
                    "La descarga se verificará antes de reemplazar la aplicación.");
            detail.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(detail);
            body.add(Box.createVerticalStrut(16));

            progress.setStringPainted(true);
            progress.setAlignmentX(Component.LEFT_ALIGNMENT);
            progress.setPreferredSize(new Dimension(480, 22));
            progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
            body.add(progress);

            setContentPane(body);
            pack();
            setMinimumSize(new Dimension(530, getHeight()));
            setLocationRelativeTo(parent);
        }

        void setProgress(int value) {
            progress.setValue(Math.max(0, Math.min(100, value)));
        }
    }
}
