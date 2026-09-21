package dev.streamflix.desktop;

record SportsFavorite(String kind, String name, String sport) {
    SportsFavorite {
        kind = kind == null ? "team" : kind.trim();
        name = name == null ? "" : name.trim();
        sport = sport == null ? "" : sport.trim();
    }
}
