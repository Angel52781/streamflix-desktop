package dev.streamflix.desktop;

interface Extractor {
    String name();
    boolean supports(String url);
    Models.Video extract(String url) throws Exception;
}
