package dev.streamflix.desktop;

interface SportsDataProvider {
    SportsSnapshot load() throws Exception;
}
