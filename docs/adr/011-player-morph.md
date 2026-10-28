# 011 — Progress-driven mini/full player morph

Date: 2026-10-27

## Status

Accepted

## Context

The mini player and full player need to behave like SoundCloud / YouTube Music:
tapping or dragging the mini player expands it into the full player, and dragging the
full player down collapses it back. The transition has to follow the finger.

The full player previously used `ModalBottomSheet`, which renders in a separate dialog
window. Compose shared element transitions cannot animate across windows, and the
standard `SharedTransitionLayout` / `sharedBounds` API is time-based, not scrubbable by
a gesture. Keeping the dialog would therefore make a follow-finger morph impossible.

## Decision

The player is an in-window overlay owned by `PlayerHost`. The bottom bar reserves the
mini player's space and measures its card bounds; the host draws both the mini card and
the full player inside one morphing container.

A single `progress` value drives the morph:

- `0` = exactly the measured mini card
- `1` = full-screen player

The card's top edge follows the finger: upward drag increases progress by
`dragY / miniCardTop`, and downward drag reverses it. The container bounds, corner
radius, scrim, and content crossfade are derived from progress. The full player is laid
out once at full size and revealed by translation plus a rounded clip, so dragging does
not remeasure the player UI.

Expand gestures live on the mini layer; collapse uses a nested-scroll connection on the
full player so it composes with the queue pager. X, system back, artist navigation, and
an empty queue all settle progress back to `0`.

## Consequences

- Follow-finger expansion and collapse are possible without cross-window hacks.
- The player is no longer a dialog; insets, back handling, and layering are owned by
  the app instead of Material 3.
- Hidden player layers are kept out of the composition while collapsed, and full
  player interaction is gated until the player is open.
- Progress is intentionally not persisted across process death; the logical `open`
  state is. A restored open player starts fully expanded rather than mid-morph.
- Adding another morph target (for example a tablet rail player) would use the same
  progress-driven container rather than a second sheet.
