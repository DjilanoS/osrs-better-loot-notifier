# Attribution

## Alert sounds

The three highlight alerts shipped in `src/main/resources/com/betterlootnotifier/sound/` were sourced
as MP3s and converted to 16-bit PCM WAV (mono, 44.1 kHz, trimmed and loudness-normalised), because
`javax.sound.sampled` — which RuneLite's `AudioPlayer` is built on — has no MP3 decoder.

| Shipped as | Original file | Credited to |
|---|---|---|
| `sound/scale-e6.wav` | `freesound_community-scale-e6-14577.mp3` | freesound_community |
| `sound/item-pickup.wav` | `freesound_community-item-pick-up-38258.mp3` | freesound_community |
| `sound/arcade.wav` | `floraphonic-arcade-ui-1-229498.mp3` | floraphonic |

The originals are kept under `sound-sources/` for provenance.

> **Note:** the filenames indicate these came from Pixabay/Freesound contributors, but the exact
> licence for each was not captured at download time. Before publishing to the RuneLite Plugin Hub,
> confirm each sound's licence permits redistribution and record the specific licence and source URL
> in the table above.
