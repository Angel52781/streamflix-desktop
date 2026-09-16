package dev.streamflix.desktop;

public final class OkruLiveTest {
    public static void main(String[] args) throws Exception {
        var extractor = new OkruExtractor();
        var video = extractor.extract("https://ok.ru/videoembed/1392690399817");
        System.out.println("SOURCE=" + video.source());
        System.out.println("HEADERS=" + video.headers());
        int code = MpvPlayer.smoke(video);
        System.out.println("MPV=" + code);
        if (code != 0) throw new IllegalStateException("Okru playback failed: " + code);
        System.out.println("OKRU_OK");
    }
}
