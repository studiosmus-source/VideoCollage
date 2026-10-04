# VideoCollage

Android app for creating a single collage from 2–4 videos.

## V1 requirements
- Landscape editor automatically.
- 2–4 videos shown simultaneously.
- Every video keeps its own audio.
- Shorter videos loop until the longest source ends.
- Each video can be dragged and pinch-zoomed inside its masked grid cell.
- Source aspect ratio is preserved.
- Final action is a single **SALVA VIDEO** button.
- Export targets the best useful resolution derived from the source media and layout rather than artificial upscaling.
- Final MP4 is intended to be written to the Android Gallery through MediaStore.

## Current implementation status
The interactive editor foundation is implemented: video picker, landscape UI, grid, simultaneous playback/audio, per-tile looping, drag and pinch-to-zoom.

The final Media3 multi-asset compositor/export pipeline is intentionally not marked complete yet. The Save button currently does not create a misleading partial file.

## Technical direction
Jetpack Media3 is used for playback and the export pipeline. Multi-input export will use Composition + Transformer and custom VideoCompositorSettings. The editor preview is kept independent from experimental multi-asset CompositionPlayer behavior.
