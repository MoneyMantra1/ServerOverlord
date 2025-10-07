# GrandEntrance v1.1.0 (Paper 1.21.6, Java 21)

**Now even grander.** In addition to trumpets, fireworks, particles, titles, and effects, this adds:

- **Bossbar flash** (Adventure BossBar) with customizable text/color/style + auto-hide.
- **Confetti rain** using REDSTONE dust with custom color palette (bursts).
- **Lightning (visual)** bolts around each player (no damage) + thunder.
- **Floating hologram** text above the joiner for a few seconds.
- Richer default **soundscape** (villagers celebrate, beacon activate, level up, toast, etc.).

Everything is toggleable. All tasks are short-lived and scheduled; no polling loops.

## Build
Java 21 → `mvn -B -ntp package` → drop jar in `plugins/`.

## Permissions
- `grandentrance.admin` — `/grandentrance reload` (OP default)
- `grandentrance.trigger` — who triggers on join (OP default, configurable)
- `grandentrance.silent` — opt-out of receiving the spectacle

## Notes
- Lightning uses `strikeLightningEffect` (purely visual).
- Hologram uses an invisible, marker ArmorStand removed after N seconds.
