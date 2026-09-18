# Player UX invariants

These rules are regression guards for the Streamflix desktop player.

## 1. Fullscreen chrome must never resize video

The native mpv video surface always occupies the full player bounds.
Top and bottom controls are overlays on that surface.
Showing or hiding controls must not change the bounds of the media stage or native Canvas.

Why: the previous BorderLayout implementation removed the top/bottom bars from layout
when the 2.8 s fullscreen auto-hide timer fired. The media stage then expanded for a frame
and shrank again as soon as mouse movement revealed the controls. In screen recordings this
looked like a flash or scene change. It was reproduced repeatedly at ~1.73 s, 4.57 s and 7.40 s.

Regression test: MpvPlayerTest.testPlayerChromeOverlayKeepsMediaStable.

## 2. Continue Watching is not a horizontal rail

Continue Watching uses a bounded grid with no horizontal wheel handling and no rail arrows.
The normal Home vertical scroll remains active around and through this section.

## 3. Track preferences are explicit

Preferred audio/subtitle languages are applied after mpv exposes track-list.
A requested Spanish subtitle may match es/spa/Spanish/Español/Castellano/Latino aliases.
If no requested subtitle language exists, subtitles are disabled instead of choosing
an unrelated forced/default track.
