# Search UX invariants

These rules protect the desktop search experience from regressions.

## 1. Background search must never steal keyboard focus

Typing in the top search field starts a debounced asynchronous request.
Starting, cancelling or completing that request must not disable the search field or move keyboard focus away from it.

A user must be able to type a long title continuously even when intermediate queries are already loading.

Current debounce: 450 ms.

## 2. Search preserves the section where it started

- Search from Movies -> movie results only; Movies stays selected.
- Search from Series -> TV-series results only; Series stays selected.
- Search from TV -> live-channel search; TV stays selected.
- Search from Home -> global movie + series search.

Subsequent debounced queries keep the original search scope until the user leaves search.

## 3. Clearing or escaping returns to the source section

Clearing the search field or pressing Escape while showing search results returns to the section where the search started instead of forcing Home or Movies.

## 4. TMDb search is bilingual but canonically deduplicated

TMDb searches merge preferred-language and alternate ES/EN responses by canonical TMDb ID.
The preferred catalog language wins when the same title appears in both responses.

Regression coverage:
- MainFrameSearchTest
- TmdbFixtureTest cross-language fallback
