# RadiationZones — NeoForge 1.21.1

**Created by Neo (Neonekpro in Minecraft).**

Admin-defined radiation zones for Minecraft. Inspired by [CraftserveRadiation](https://github.com/Craftserve/CraftserveRadiation).

## Download

Grab the latest `.jar` from the [GitHub Releases page](https://github.com/Neony777/Radiation-Zone-Mod/releases/latest) and drop it into your server's `mods/` folder. Requires NeoForge 21.1.x for Minecraft 1.21.1.

## Features

- **Radiation Wand** — admin tool. Left-click block = pos1, right-click block = pos2.
- **Cuboid radiation zones** with level 1–5 (configurable up to 20).
- **Per-player boss bar** showing zone level and grace-period countdown; turns red and starts inflicting radiation damage when the timer runs out.
- **Hostile mobs are buffed** in zones — extra max health, movement speed, attack damage, scaled by zone level.
- **Lugol's Potion** — drinkable item that grants temporary immunity to radiation. Craftable in survival, or hand it out via `/lugols give`.
- **Gas Mask** — craftable head-slot helmet with three swappable filter tiers (Basic / Industrial / Hazmat). Right-click to install a filter, sneak + right-click to eject it. While worn in a zone, the filter takes the radiation hit instead of the player; the mask itself wears down very slowly. Lugol's still wins if both are active.
- All zones persist per-world to `<world>/data/radiationzones.json`.
- All values tunable in `config/radiationzones-common.toml`.

## Commands (require OP level 2)

| Command | Description |
| --- | --- |
| `/radiationwand give [player]` | Gives the Radiation Wand. |
| `/radiationzone create <name> <level>` | Creates a hazard zone (radiation INSIDE) using your wand selection. |
| `/radiationzone createsafe <name> <level> <square\|circle> <size> [x y z]` | Creates a **safe zone** (radiation OUTSIDE the bounds, world-border style). `size` = full side length for square, radius for circle. Defaults to player position when coords omitted. |
| `/radiationzone remove <name>` | Deletes a zone. |
| `/radiationzone list` | Lists all zones with bounds, level, and dynamic settings. |
| `/radiationzone setspread <name> <blocks/min>` | Zone bounds expand outward at this rate. |
| `/radiationzone setdecay <name> <blocks/min>` | Zone bounds shrink inward at this rate. Net effect = spread − decay. |
| `/radiationzone setleveldecay <name> <minutes>` | Reduce zone level by 1 every N minutes. Any zone (INSIDE or OUTSIDE) is removed when its level reaches 0. |
| `/radiationzone setmaxradius <name> <blocks>` | Cap how far the zone can spread from its original bounds (0 = unlimited). |
| `/radiationzone setdrift <name> <dx> <dy> <dz>` | Drift the bounds through the world at `dx,dy,dz` blocks/minute on each axis. |
| `/radiationzone setmode <name> <inside\|outside>` | Flip an existing zone between hazard (inside) and safe-bubble (outside) modes. |
| `/lugols give <player> [count]` | Gives Lugol's Potion(s). |

### Safe zones (world-border style)

A safe zone behaves like Minecraft's world border but for radiation: anyone **outside** the bounds in that dimension takes damage. Combine with `setdrift`, `setspread`, and `setdecay` to make the safe area slide, expand, or close in over time.

```
# 1000-block safe square centered on spawn, level 3
/radiationzone createsafe spawn 3 square 1000 0 64 0   # explicit center; omit coords to use the executor's position

# Closes in by 1 block/min toward the center
/radiationzone setdecay spawn 1

# Drifts ~100 blocks east per day (100 / (24*60) ≈ 0.0694 blocks/min on +X)
/radiationzone setdrift spawn 0.0694 0 0

# 200-block safe circle centered on (0, 64, 0), level 5
/radiationzone createsafe oasis 5 circle 200 0 64 0

# Flip a normal hazard zone into a safe bubble (and back)
/radiationzone setmode reactor outside
/radiationzone setmode reactor inside
```

Safe zones support both **square** (cuboid) and **circle** (vertical cylinder, full world height) shapes. Drift and shrink/expand survive server restarts — the system uses wall-clock time deltas, not tick counts. When a safe zone shrinks below 1×1 it's left in place at zero size, meaning the entire dimension is irradiated (admin "scorched earth" end state). To clean up a fully decayed safe zone, give it a level decay so its level eventually reaches 0 and it removes itself, or use `/radiationzone remove`.

Multiple safe zones are **independent**. Each one irradiates whenever the player is outside its own bounds. When several safe zones apply at once (the player is outside more than one), the **highest level** wins.

This makes nested concentric rings work cleanly:

```
/radzone createsafe ring1 square 1000 1
/radzone createsafe ring2 square 1500 2
/radzone createsafe ring3 square 2000 3
```

* Inside the 1000-block ring → completely safe.
* Between 1000 and 1500 → outside ring1, inside ring2/ring3 → level 1 damage.
* Between 1500 and 2000 → outside ring1+ring2, inside ring3 → level 2 damage.
* Outside the 2000-block ring → outside all three → level 3 damage.

### Dynamic zones (spread, decay, level decay)

Zones can be made dynamic so they grow, shrink, or weaken over time — useful for nuclear-accident scenarios and cleanup gameplay.

```
/radiationzone create reactor 5
/radiationzone setspread reactor 2          # grows by 2 blocks/min in every direction
/radiationzone setmaxradius reactor 64      # cap spread at +64 blocks from origin
/radiationzone setleveldecay reactor 30     # drop one level every 30 min (auto-cleans up at level 0)
/radiationzone setdecay reactor 1           # shrinks by 1 block/min (slower than spread => still net-growing)
```

A scheduled task ticks zones once per second on the server. Updates are time-based (uses wall clock since last update), so spread/decay survives server restarts and lag correctly.

## Building the JAR

You need **JDK 21** (Temurin / Adoptium recommended). NeoForge 1.21.1 will refuse to load on older JVMs.

### Option 1 — One-line local build

```
./build.sh
```

This downloads a portable Gradle into `.gradle-local/` (gitignored) and runs the build.
The output JAR appears in `build/libs/radiationzones-1.0.0.jar`.

### Option 2 — Use your own Gradle

```
gradle --no-daemon build
```

### Option 3 — GitHub Actions (no local toolchain needed)

Push this repo to GitHub. The workflow at `.github/workflows/build.yml` will build the
JAR automatically on every push and on every release. Download the JAR from the
**Actions tab → latest run → Artifacts → radiationzones-mod-jar**.

If you create a GitHub release, the JAR is also attached to the release page.

## Configuration

After running the mod once on a server (or single player), a config file appears at:

```
<world>/serverconfig/radiationzones-common.toml
```

It exposes everything tunable:

- **levels.\*** — per-level damage, grace period, mob health/speed/attack multipliers.
- **general.\*** — `maxLevel`, `creativeImmune`, `spectatorImmune`, `bossBarVisible`,
  `shieldingYBelow` (players below this Y take half damage — simulates being underground).
- **lugols.\*** — `defaultDurationSeconds`, `amplifier` (potion strength tier),
  `showIcon` (whether the effect shows in HUD).
- **mobs.\*** — `enabled` (master mob-buff toggle), `affectPassiveMobs`
  (also buff cows, villagers, ...), `checkIntervalTicks`.
- **particles.\*** — `enabled`, per-level `particlesPerTick`, `radius`, `previewRadius`
  (distance from which approaching players see hint particles).
- **geiger.\*** — `enabled`, per-level `intervalTicks`, `volume`, and `soundId` (any
  vanilla sound resource location, e.g. `minecraft:block.note_block.hat`).

## How to use (admin workflow)

1. `/radiationwand give` — get the wand.
2. **Left-click** a block to mark corner 1, **right-click** a block to mark corner 2. Action-bar feedback confirms each pick.
3. `/radiationzone create reactor 3` — creates a level-3 zone named `reactor` from your two corners.
4. Walk into the zone — a yellow boss bar appears with a countdown. After grace period it goes red and you start taking damage.
5. To protect a player: `/lugols give <player>` — they drink the potion and get protection for the configured duration.

## Protection: Gas Mask & Filters

Players have two ways to survive a radiation zone:

1. **Lugol's Potion** — temporary chemical immunity. Wins over the gas mask when both are active (no filter charge is consumed while Lugol's is up).
2. **Gas Mask + Filter** — equipment-based, durability-based protection. Craft the mask, slot in a filter, and wear it in the head slot.

### Filters

| Filter | Charges | Protection | Notes |
| --- | --- | --- | --- |
| Basic Filter | 200 | **60% block** (40% of radiation damage still leaks through) | Cheap, short life. Good for quick passes when you don't have anything better. Boss bar turns yellow ("Basic Filter — partial"). |
| Industrial Filter | 800 | **100% block** | Mid-tier, long life. The everyday option. Boss bar turns green ("Gas Mask filtering"). |
| Hazmat Filter | 2400 | **100% block + Resistance I overflow** | Best in slot. Each filtered tick also grants 2 seconds of Resistance I, which slightly reduces other damage you take while the filter is doing its job. |

Filter wear scales with zone level — a level 5 zone consumes filter charge 5× faster than a level 1 zone. The mask itself loses 1 durability per damage tick spent filtering, so it lasts a very long time across many filter swaps.

### Using the mask

- **Right-click** while holding the mask with a filter in your **other hand** → installs the filter into the mask.
- **Sneak + right-click** while holding the mask → ejects the installed filter back into your inventory (its remaining charge is preserved as item damage).
- The mask icon shows a green cartridge while a filter is installed; tooltip shows the filter type and remaining charge.
- Wear it in the helmet slot. While inside a radiation zone past the grace period, the boss bar turns green and reads "Gas Mask filtering" instead of dealing damage.

### Recipes

- **Gas Mask**: iron ingots + leather + glass panes (mask body, eye lenses, straps).
- **Basic Filter** *(early-game, all vanilla)*: 4 iron nuggets + 2 paper + 2 charcoal + 1 string in a layered `IPI / CSC / IPI` pattern. The nuggets form a rigid frame, paper + charcoal sandwich the absorbent core, and the string lashes it shut.
- **Industrial Filter** *(mid-game, builds on the basic)*: 4 copper ingots + 2 redstone + 2 blaze powder + **1 Basic Filter** as the centerpiece, in `CRC / BFB / CRC`. Effectively an upgrade kit: copper plating, redstone for active sensing, blaze powder for higher-temperature scrubbing.
- **Hazmat Filter**: netherite scrap + slime ball + diamond + charcoal (unchanged).
- **Lugol's Potion** *(shapeless)*: 1 glass bottle + 1 nether wart + 2 kelp + 1 sugar. The kelp supplies the iodine, the bottle and nether wart give it a potion base, and the sugar makes the brew palatable.

All four recipes appear in the recipe book once their ingredients are unlocked. Each recipe has an individual on/off switch in the config (see *Recipe toggles* below) — set any to `false` to remove it from the crafting table and recipe book; `/reload` picks up changes without a restart.

## Building

This is a standard NeoForge MDK project layout. From this directory:

```bash
gradle wrapper           # one-time, generates ./gradlew
./gradlew build          # produces build/libs/radiationzones-1.0.0.jar
```

Drop the resulting jar into your server's `mods/` folder. Requires NeoForge 21.1.x for Minecraft 1.21.1.

## Config (`config/radiationzones-common.toml`)

The config exposes per-level lists (index 0 = level 1) for:

- `gracePeriodSeconds` — seconds inside the zone before damage starts.
- `damageAmount` — damage in half-hearts per application.
- `damageIntervalTicks` — ticks between damage applications.
- `mobHealthMultiplier`, `mobSpeedMultiplier`, `mobAttackMultiplier` — buffs added to hostile mobs.
- `lugols.defaultDurationSeconds` — how long Lugol's Potion protects you.

Defaults (levels 1 → 5):

| Level | Grace | Damage / hit | Interval (s) |
| --- | --- | --- | --- |
| 1 | 60s | 0.5♥ | 3.0 |
| 2 | 45s | 1.0♥ | 2.0 |
| 3 | 30s | 1.5♥ | 1.5 |
| 4 | 15s | 2.0♥ | 1.0 |
| 5 |  5s | 3.0♥ | 0.5 |

## Credits

- **Gas mask 3D model** — based on *"QSMP Gas Mask 3D model"* by **skibidi** on Sketchfab ([source](https://sketchfab.com/3d-models/qsmp-gas-mask-3d-model-d33075f4f86a40dea4cc908b2d0ddbfa)), used under its Sketchfab CC license. The original glTF cube sizes and world-space positions are reprojected onto a Minecraft `HumanoidModel` head bone; the worn-armor atlas is re-painted in Minecraft's cube-unwrap convention using the QSMP palette sampled from the original GLB texture (the GLB's PNG cannot ship verbatim because Minecraft requires a fixed cube-unwrap layout that the GLB's per-face UVs do not respect).

## Notes

- Lugol's Potion ships with a shapeless crafting recipe (see *Recipes* above) gated behind `recipes.lugolsPotion`. Set it to `false` and `/reload` to remove it again if you want to keep Lugol's admin-only.

### Recipe toggles

In `config/radiationzones-common.toml` under `[recipes]`:

```toml
[recipes]
gasMask          = true
basicFilter      = true
industrialFilter = true
hazmatFilter     = true
lugolsPotion     = true
```

Set any value to `false` to disable that recipe. Pack authors can also override or replace the JSON files at `data/radiationzones/recipe/*.json` from a datapack — the recipes remain fully data-driven.
- Hazard zones can be static cuboids OR dynamic (spread / decay / drift). Safe zones (`createsafe`) flip the rule so anyone *outside* the bounds takes damage.
- Damage bypasses armor (it's radiation, after all).
