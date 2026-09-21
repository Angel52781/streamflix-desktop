package dev.streamflix.desktop;

record SportsBroadcaster(
        String channel,
        String country,
        String logo
) {
    SportsBroadcaster {
        channel = channel == null ? "" : channel.trim();
        country = country == null ? "" : country.trim();
        logo = logo == null || logo.isBlank() ? null : logo.trim();
    }
}
