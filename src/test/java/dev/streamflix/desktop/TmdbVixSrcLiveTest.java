package dev.streamflix.desktop;

/**
 * Opt-in live smoke for VixSrc. Not part of the deterministic build gate because
 * it depends on third-party availability and real mpv/network access.
 */
public final class TmdbVixSrcLiveTest {
    public static void main(String[] args) throws Exception {
        String target = args.length == 0 ? "movie-en" : args[0];
        switch (target) {
            case "movie-en" -> smoke(target, "https://vixsrc.to/api/movie/550?lang=en");
            case "episode-en" -> smoke(target, "https://vixsrc.to/api/tv/1399/1/1?lang=en");
            case "movie-es" -> smoke(target, "https://vixsrc.to/api/movie/550?lang=es");
            case "episode-es" -> smoke(target, "https://vixsrc.to/api/tv/1399/1/1?lang=es");
            default -> throw new IllegalArgumentException("Unknown target: " + target);
        }
        System.out.println("TmdbVixSrcLiveTest OK: " + target);
    }

    private static void smoke(String label, String url) throws Exception {
        VixSrcExtractor extractor = new VixSrcExtractor();
        Models.Video video = extractor.extract(url);
        if (video.source() == null || video.source().isBlank()) {
            throw new AssertionError(label + ": extractor returned no source");
        }
        if (!video.headers().containsKey("Referer") || !video.headers().containsKey("User-Agent")) {
            throw new AssertionError(label + ": playback headers missing");
        }
        System.out.println(label + ": extraction OK; starting mpv smoke");
        int exit = MpvPlayer.smoke(video);
        if (exit != 0) throw new AssertionError(label + ": mpv exit=" + exit);
        System.out.println(label + ": mpv smoke OK");
    }
}
