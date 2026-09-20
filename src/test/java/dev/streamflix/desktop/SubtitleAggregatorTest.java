package dev.streamflix.desktop;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class SubtitleAggregatorTest {
    public static void main(String[] args) {
        testStrictMovieIdentity();
        testStrictEpisodeIdentity();
        testAdditionalSubtitlesDeduplicatePrimary();
        System.out.println("SubtitleAggregatorTest OK");
    }

    private static void testStrictMovieIdentity() {
        Models.ShowItem movie = new Models.ShowItem(
                "tmdb:movie:123", "movie/123", "Movie", "", "2026",
                100, 8.0, "", "", Models.ShowType.MOVIE, "tmdb-es");
        require(SubtitleAggregator.hasStrictTmdbIdentity(movie, null, "movie/123"),
                "movie canonical identity accepted");
        Models.Episode fakeEpisode = new Models.Episode(
                "tv/123/season/1/episode/1", 1, 1, "Episode", "", "");
        require(!SubtitleAggregator.hasStrictTmdbIdentity(movie, fakeEpisode, "movie/123"),
                "movie with episode rejected");
        require(!SubtitleAggregator.hasStrictTmdbIdentity(movie, null, "movie/124"),
                "wrong movie id rejected");
    }

    private static void testStrictEpisodeIdentity() {
        Models.ShowItem show = new Models.ShowItem(
                "tmdb:tv:5920", "tv/5920", "The Mentalist", "", "2008",
                45, 8.4, "", "", Models.ShowType.TV_SHOW, "tmdb-es");
        Models.Episode episode = new Models.Episode(
                "tv/5920/season/1/episode/3", 1, 3, "Red Tide", "", "");
        require(SubtitleAggregator.hasStrictTmdbIdentity(
                        show, episode, "tv/5920/season/1/episode/3"),
                "episode canonical identity accepted");
        require(!SubtitleAggregator.hasStrictTmdbIdentity(
                        show, episode, "tv/5920/season/1/episode/4"),
                "wrong episode rejected");
    }

    private static void testAdditionalSubtitlesDeduplicatePrimary() {
        Models.Subtitle existing = new Models.Subtitle(
                "Español", "https://subs.example/es.vtt", true);
        Models.Subtitle duplicate = new Models.Subtitle(
                "Español", "https://subs.example/es.vtt", false);
        Models.Subtitle english = new Models.Subtitle(
                "English", "https://subs.example/en.vtt", false);

        Models.Video primary = new Models.Video(
                "https://video.example/main.m3u8", Map.of(), List.of(existing));
        Models.Video auxiliary = new Models.Video(
                "https://video.example/other.m3u8", Map.of(), List.of(duplicate, english));

        List<Models.Subtitle> additions =
                SubtitleAggregator.additionalSubtitles(primary, List.of(auxiliary));
        Set<String> files = additions.stream().map(Models.Subtitle::file).collect(Collectors.toSet());

        require(additions.size() == 1, "duplicate primary subtitle removed");
        require(files.contains("https://subs.example/en.vtt"), "new subtitle retained");

        Models.Video merged = SubtitleAggregator.merge(primary, List.of(auxiliary));
        Set<String> mergedFiles = merged.subtitles().stream()
                .map(Models.Subtitle::file).collect(Collectors.toSet());
        require(mergedFiles.size() == 2, "merged subtitles deduplicated");
    }

    private static void require(boolean value, String name) {
        if (!value) throw new AssertionError("Failed: " + name);
    }
}