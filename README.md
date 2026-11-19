# Tuff SMP — Season 2

Fabric mod for **Minecraft 1.21.11**. Season 2 drops legendary weapons in favour of a **boss + essence** loop:
fight bosses, earn essence, spend it on permanent upgrades.

| Boss tier | How it starts | Announced? | Difficulty | Drops |
|---|---|---|---|---|
| **Tier 1** | Right-click a **Sigil** on the ground | No (silent) | Solo-able | Level 1 essence → **potion effects** |
| **Tier 2** | Right-click a **Rune** on a **Boss Altar** | Yes, server-wide; boss rises after a **15 min** countdown so others can contest | Group content | Level 2 essence → **abilities** |
| **Tier 3** | Weekly timer (default Sat 18:00 UTC), at a fixed location | Yes, with 60/15/5/1 min warnings | Whole-server fight | **Multiple of every essence** + exactly **1 Magic Essence** |

Level 3 is **Magic Essence**: a player channels it, then DMs the owner what they want and the owner builds it.

---

## Essence

### Level 1 — potion perks (Tier 1 bosses)
Right-click to invest one essence (sneak + right-click invests the whole stack). The perk is applied permanently
as a quiet ambient potion effect (no particles).

| Essence | Boss | Perk | Invested for level I / II |
|---|---|---|---|
| Verdant | Mossback Brute | Regeneration | 5 / 20 |
| Ember | Cinderfang | Fire Resistance | 3 |
| Frost | Rimebound Hulk | Resistance | 6 / 24 |
| Storm | Stormcaller | Speed | 4 / 16 |
| Tide | Tidewrecked Marauder | Water Breathing | 3 |
| Gilded | Gilded Warlord | Haste | 5 / 18 |

### Level 2 — abilities (Tier 2 bosses)
Invest to unlock and level an ability (unlock at 3, level II at 10, level III at 25; each level shaves 12% off the cooldown
and strengthens it). Default keys: **R** use, **V** cycle (rebindable under *Tuff SMP*). Fallback commands: `/ability use|next|list`.

| Essence | Boss | Ability |
|---|---|---|
| Rift | Rift Stalker | **Blink** — short-range teleport where you look |
| Wraith | Wraith Knight | **Dash** — burst of speed in your facing direction |
| Titan | Ironclad Colossus | **Slam** — AoE damage + knockback to hostile mobs |
| Gale | Skybreaker | **Leap** — big jump with slow-falling |
| Fury | Bloodhide Ravager | **Overcharge** — temporary Strength/Speed/Haste |

### Level 3 — Magic Essence (Tier 3 bosses)
Sneak + right-click to channel it (sneak is a misclick safeguard). This logs a request for the owner (`/tuff requests list`) and
op's online get a notification. Set `ownerName` in the config so players are told who to DM.

Check progress any time with `/essence`.

## Bosses

Bosses are vanilla mobs scaled up with custom attributes, a boss bar, phases and a skill set, so **no custom entity
models are needed**. At 66% and 33% health they enrage (more damage, faster casts, extra minions).

* **Co-op scaling** — each extra nearby player makes a boss shrug off a share of every hit, so zerging with 10 people
  isn't trivial but a lone player can still beat Tier 1. Tune with `coopBonusTier1/2/3`.
* **Contesting** — damage is tracked per player. Anyone who dealt a meaningful share (8% T1 / 4% T2 / 2% T3 of total damage) is
  rewarded, so rival groups can fight over an altar boss. The top damage dealer gets a bonus.
* **Rewards go straight into inventories** (nothing on the ground to steal). Anyone offline when the boss dies gets their share on next login.
* **Tier 3**: one Magic Essence is **raffled by damage share** among eligible players and announced.
* Bosses can't be killed by suffocation, fall, drowning or fire, are pulled back to their spawn if they stray, and
  reset if everyone leaves.
* Weekly boss pool rotates: Sculk Sovereign → Ashen Overlord → Tempest Archmage → Primordial Titan.

### Boss list

| Tier | Boss (id) | Base mob |
|---|---|---|
| 1 | Mossback Brute (`verdant`), Cinderfang (`ember`), Rimebound Hulk (`frost`), Stormcaller (`storm`), Tidewrecked Marauder (`tide`), Gilded Warlord (`gilded`) | Zombie, Wither Skeleton, Stray, Vindicator, Drowned, Piglin Brute |
| 2 | Rift Stalker (`rift`), Wraith Knight (`wraith`), Ironclad Colossus (`titan`), Skybreaker (`gale`), Bloodhide Ravager (`fury`) | Enderman, Wither Skeleton, Iron Golem, Evoker, Ravager |
| 3 | The Sculk Sovereign (`sculk_sovereign`), Ashen Overlord (`ashen_overlord`), Tempest Archmage (`tempest_archmage`), Primordial Titan (`primordial_titan`) | Warden, Wither Skeleton, Evoker, Iron Golem |

Skills: Slam, Volley, Minions, Leap, Frost Nova, Lightning, Pull, Teleport, Darkness, Wither Blast. All are telegraphed
(particles + action-bar warning) before they land. Projectiles/lightning are scripted so **they never set fires or break blocks**.

## Arenas (Tier 2 and Tier 3)

When a Tier 2 altar boss rises, or the weekly boss spawns, a **coded arena** is built first and the boss appears inside it:
a flat circular stone-brick platform (polished-blackstone rim), a 2-block wall, terrain cleared above it, and gaps under it filled.
Radius is **24** for Tier 2 (centered on the altar) and **40** for Tier 3 (centered on the weekly location). Tier 1 fights have no arena.

* The boss **never leaves** the arena (it's pulled back inside, and its teleport skill won't land outside).
* **Digging and placing blocks is disabled** inside the arena while the boss is alive (creative mode is exempt).
* **Abandoned fight** (everyone gone or dead for 2 minutes): the boss heals to full and stays, and the arena is rebuilt to its original layout.
* **Won fight:** the arena stays exactly as it is.
* Chests and other block-entity blocks inside the circle are left alone, but everything else in the circle is replaced, so place altars and the weekly location away from builds.

All numbers (radii, height, wall, materials) are constants at the top of `arena/Arena.java`.
`/tuff boss spawn <id> arena` builds an arena at your feet and spawns a Tier 2/3 boss in it (destructive).

## Crafting (survival)

* **Sigil** (Tier 1): `M I M / I D I / M I M` — M = thematic block (moss, magma, packed ice, lightning rod, prismarine, gold), I = iron ingot, D = diamond.
* **Rune** (Tier 2): `N T N / T D T / N T N` — N = netherite scrap, T = thematic item (ender eye, crying obsidian, iron block, phantom membrane, blaze rod), D = diamond block.
* **Boss Altar**: `O C O / O E O / O O O` — obsidian, crying obsidian, enchanting table. Mined with a diamond pickaxe or better.

Breaking the altar mid-countdown cancels the summoning and drops the rune back.

## Admin commands (`/tuff …`, permission level 2)

| Command | What it does |
|---|---|
| `boss spawn <id>` | Spawn any boss where you stand (silent, no announcement) |
| `boss list` / `boss killall` | List active bosses with coordinates / remove them (no rewards) |
| `altar start <id> [seconds]` | Place an altar at your feet and start a (shorter) countdown |
| `altar list` / `altar cancelall` | Pending summonings |
| `weekly status` | Next weekly spawn, boss and location |
| `weekly setlocation` | Make your position the weekly boss arena |
| `weekly spawnnow [id]` | Trigger the weekly boss immediately |
| `weekly reschedule` | Recompute the next spawn time from the config |
| `requests list` / `requests clear <n>` | Magic Essence wishes players have channeled |
| `invest <player> <essence> <n>` | Grant investment directly (testing / support) |
| `reset <player>` | Wipe a player's upgrades |

Everything is also obtainable with vanilla `/give @s tuffsmp:<id>` (e.g. `verdant_essence`, `rift_rune`, `gilded_sigil`, `boss_altar`, `magic_essence`).

## Config (`config/tuffsmp.json`)

`ownerName`, `altarCountdownSeconds`, `weeklyEnabled`, `weeklyDay`, `weeklyHour`, `weeklyMinute`, `weeklyTimezone`,
`weeklyGraceHours`, `coopBonusTier1/2/3`, `bossHealthMultiplier`, `bossDamageMultiplier`.
Perk/ability thresholds are in `essence/Essence.java`; boss stats, skills and loot are in `boss/BossRegistry.java`.

## Data

All state (upgrades, pending rewards, magic requests, pending altars, weekly schedule, boss damage) lives in
`<world>/tuffsmp/state.json`, so a fresh world is a fresh season.

## Building

Requires **JDK 21**.

```bash
./gradlew build        # jar in build/libs/tuffsmp-<version>.jar
./gradlew runServer    # dev server (accept the EULA in run/eula.txt)
```

Install the jar (plus Fabric API) on the server **and** every client (clients need it for the items and keybinds).

## Adding content

1. Add the boss to `BossRegistry.init()`.
2. If it has a summon item, add it to `SIGILS`/`RUNES` in `tools/gen_assets.py`; new essences go in `LEVEL1`/`LEVEL2` there **and** in the `Essence` enum.
3. `python3 tools/gen_assets.py` regenerates textures, models, recipes and lang (requires Pillow).

## Handing off a Magic Essence wish

`/tuff requests list` shows who channeled one and when. Build their item, then `/tuff requests clear <n>`.
