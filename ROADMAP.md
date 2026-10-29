# Roadmap

One box per scheduled update. Tick a box only when that change is built, verified and released.
When the list runs out, add new items above the line and keep going.

## Queued

- [ ] Object list: show a one-line summary at the top ("3 objects found")
- [ ] Tap the photo to open a full-screen viewer with pinch-zoom
- [ ] Copy the result to the clipboard with one tap
- [ ] Remember the last 10 scans in a local history list (no permissions, app storage only)
- [ ] "Share text only" alongside "Share photo + text"
- [ ] Settings sheet: confidence threshold slider for the detector
- [ ] Settings sheet: choose which model runs (classifier only / detector only / both)
- [ ] Blur/quality warning when the photo is too soft to analyse well
- [ ] Person count chip ("2 people") derived from the detector
- [ ] Per-object dominant colours in the "look closer" view
- [ ] Sort the object list by box size instead of confidence (toggle)
- [ ] Map near-duplicate labels to one friendly name (e.g. "hot dog" → "hot dog / sausage")
- [ ] Multi-crop inference: centre + 4 corner crops, merged, for small subjects
- [ ] Cache the loaded interpreter so the second scan starts instantly
- [ ] Guard against OOM on very large photos (retry with a bigger sample size)
- [ ] Decode HEIC/HEIF where the platform can
- [ ] Keep the screen awake while analysing
- [ ] Animate the boxes in (fade + slight scale) when results appear
- [ ] Haptic tick when a scan finishes
- [ ] Skeleton shimmer instead of the plain loading row
- [ ] Launcher icon refresh with the glass palette
- [ ] Dark/light theme switch that persists
- [ ] Reduce APK size: drop the x86 native library
- [ ] "What is this?" quick action from the share sheet with no app open
- [ ] Empty state: show three example photos to try
- [ ] Long-press a detection row to see the raw label and score
- [ ] Accessibility: content descriptions on every icon and row
- [ ] Localise the UI strings (start with Hindi)
- [ ] README: FAQ section answering the common "why does it say X" questions
- [ ] Instrument a tiny in-app self-test (runs both models on a bundled sample image)

## Done

