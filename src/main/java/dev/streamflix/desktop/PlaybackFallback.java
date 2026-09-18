package dev.streamflix.desktop;

import java.util.List;
import java.util.concurrent.CancellationException;

/** Deterministic fallback primitive shared by playback flows and tests. */
final class PlaybackFallback {
    private PlaybackFallback() {}

    @FunctionalInterface
    interface VideoResolver {
        Models.Video resolve(Models.Server server) throws Exception;
    }

    @FunctionalInterface
    interface VideoStarter {
        void start(Models.Video video, String title) throws Exception;
    }

    static Models.Server startFirstAvailable(List<Models.Server> servers, String title,
                                             VideoResolver resolver, VideoStarter starter) throws Exception {
        Exception last = null;
        for (Models.Server server : servers) {
            try {
                resolveAndStart(server, title, resolver, starter);
                return server;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw ex;
            } catch (CancellationException ex) {
                throw ex;
            } catch (Exception ex) {
                last = ex;
            }
        }
        throw new IllegalStateException("Ningún servidor disponible pudo iniciar la reproducción.", last);
    }

    static void resolveAndStart(Models.Server server, String title,
                                VideoResolver resolver, VideoStarter starter) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

        Models.Video video = resolver.resolve(server);
        if (video == null || video.source() == null || video.source().isBlank()) {
            throw new IllegalStateException("El servidor no devolvió un video reproducible.");
        }

        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        starter.start(video, title);
    }
}
