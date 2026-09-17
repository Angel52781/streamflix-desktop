package dev.streamflix.desktop;

import java.io.IOException;

/** Safe, provider-specific errors. Never attach credential-bearing transport causes. */
final class TmdbException extends IOException {
    TmdbException(String message) { super("TMDb: " + message); }
}
