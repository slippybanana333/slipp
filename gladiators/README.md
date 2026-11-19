# Gladiators

A **server-side only** Fabric mod for Minecraft **1.21.11**. An op runs a few commands and everyone online is
dropped into a procedurally built colosseum for a free-for-all. Players install nothing.

## Install

1. Use a Fabric server for Minecraft 1.21.11 (Fabric Loader 0.20.1 or newer, Java 21+).
2. Put these two jars into the server's `mods/` folder:
   - `gladiators-1.0.0.jar` (this mod)
   - [Fabric API](https://modrinth.com/mod/fabric-api) `0.141.6+1.21.11` (or newer for 1.21.11)
3. Start the server. On first start the mod writes `config/gladiators/config.json` and `config/gladiators/kits.json`.

## Quick start

```
/gladiators arena build      (stand where you want the middle of the arena)
/gladiators start            (everyone online is saved, cleared and teleported in)
/gladiators kit diamond_smp  (hand out a kit; fight!)
/gladiators end              (everyone gets their stuff and location back)
/gladiators arena remove     (optional: restore the terrain)
```

## Commands (op level 2 only)

| Command | What it does |
|---|---|
| `/gladiators arena build` | Builds the colosseum centered on you. The floor level is the block under your feet. Saves the arena center and spawn points to `config.json`. Spread over many ticks, progress is shown in chat. |
| `/gladiators arena remove` | Puts back every block the build replaced (including chest contents). Also the way to clean up after a crash during a build. |
| `/gladiators start` | Saves every online player's state **to disk first**, then clears them and teleports everyone to spread-out spawn points. |
| `/gladiators kit <name> [players]` | Clears the inventory and gives the kit. No players given = every fighter still alive. Tab-completes kit names. |
| `/gladiators end` | Restores everyone's saved state and location and cleans up the arena (placed rails, dropped loot, carts, arrows). |
| `/gladiators reload` | Re-reads `config.json` and `kits.json` and reports bad kit entries. |
| `/gladiators status` | Arena state, event state, number of saved states waiting to be restored. |

## What gets saved and restored

Position, dimension, gamemode, inventory, armor, offhand, health, hunger, XP and potion effects. The ender chest is never
touched. Each player's state is written to `world/gladiators/snapshots/<uuid>.dat` (atomically) before anything
about them changes, and deleted only after a clean restore.

**Crash safety.** An event does not survive a restart, but the saved states do. When a player joins and has a saved
state, it is restored immediately, whether the server crashed, they disconnected mid-event, or `/gladiators end` ran
while they were offline. (Nobody is online at startup, so a join is the earliest moment the restore can happen; the
server log says how many are waiting.) A snapshot that could only be partly restored is moved to
`world/gladiators/snapshots/failed/` instead of being deleted.

If a player's old location is now inside the arena (you built it on top of them), they are placed just outside the
arena wall instead of inside the stands.

## Free-for-all rules

- Dying does not kill: items drop where the player fell, there is no death screen and no respawn at world spawn. The
  player becomes a spectator inside the arena.
- When one fighter is left, the winner is announced in chat. (Needs at least 2 fighters at the start.)
- Players who join during an event are put in spectator mode in the arena. Their real state is saved too.
- A fighter who disconnects counts as out. When they rejoin they get their saved state back (and, while the event is
  still running, then spectate).
- If you are in spectator mode when you run `start`, you spectate instead of fighting (handy for the op).
- Fall damage is cancelled for a few seconds after every event teleport (`fallGraceSeconds`).
- Hostile mobs that wander or spawn into the arena are removed.

## Arena

Built from code: flat sand floor, a few cover pillars, tiered stone-brick seating, an outer wall, and an invisible
barrier column and ceiling so nobody leaves (elytra, pearls, spectators included; a check every half second teleports
strays back). Sea lanterns set into the floor keep it lit. The middle is left open for cart PvP.

While an event runs:

- Only blocks that appear as items in `kits.json` (by default rails, oak logs, cobwebs and shulker boxes) can be placed or broken inside the
  arena. Everything else is protected. Placed kit blocks are removed again by `/gladiators end`.
- Explosions (TNT, TNT minecarts, creepers...) still hurt players but do not break or ignite blocks. This uses one
  small server-side mixin.

The arena needs room: the floor level minus 3 up to floor level plus `ceilingHeight` (default 40) must fit in the world
height. Build on reasonably flat ground; there is only a 3-block foundation, so building over a ravine leaves it floating.
Farm animals standing inside the footprint will suffocate when it is built.

## Editing kits (`config/gladiators/kits.json`)

```json
{
  "kits": {
    "my_kit": {
      "description": "shown nowhere, just a note",
      "items": [
        { "id": "minecraft:diamond_helmet", "enchantments": { "minecraft:protection": 4 } },
        { "id": "minecraft:diamond_sword",  "enchantments": { "minecraft:sharpness": 3, "minecraft:unbreaking": 2 } },
        { "id": "minecraft:shield", "slot": "offhand" },
        { "id": "minecraft:golden_apple", "count": 8 },
        { "id": "minecraft:splash_potion", "components": { "minecraft:potion_contents": { "potion": "minecraft:strong_healing" } } }
      ]
    }
  }
}
```

- `id` (required), `count` (default 1), `enchantments` (id → level), `slot` (optional), `components` (optional, raw item
  components, same JSON as `/give`).
- `slot`: `head`, `chest`, `legs`, `feet`, `offhand`, or `0`-`35`. Armor with no slot is worn automatically; everything
  else fills the next free slot in the order written.
- Kit names must be lowercase letters, digits and `_`.
- Run `/gladiators reload`. Unknown item or enchantment ids are skipped and listed in chat and in the log.
- Block items in kits are the only blocks players may place and break during an event. Kits with buckets (water) are fine: leftover water and lava in the arena is swept away when the event ends.

Default kits (copied from loadouts supplied by the server owner): `sword`, `axe`, `diamond_smp`, `cart`, `spear_mace`. In 1.21.11 spears are `<material>_spear` (for example
`minecraft:diamond_spear`) and their enchantment is `minecraft:lunge`.

## Config (`config/gladiators/config.json`)

| Key | Default | Meaning |
|---|---|---|
| `arenaRadius` | 40 | Radius of the flat floor (12-120). Applies to the next build. |
| `seatingTiers` / `seatingTierWidth` | 6 / 2 | Number of seating steps and their width. |
| `wallThickness` / `wallExtraHeight` | 2 / 4 | Outer wall. |
| `ceilingHeight` | 40 | Height of the barrier ceiling above the floor. |
| `pillarCount` | 10 | Cover pillars (0 = none). |
| `lightSpacing` | 6 | Spacing of floor lights (0 = none). |
| `blocksPerTick` | 2500 | Blocks placed per tick while building/removing. Lower = gentler on the server. |
| `healOnKit` | true | Heal and feed players when a kit is given. |
| `removeHostileMobs` | true | Remove hostile mobs from the arena during events. |
| `fallGraceSeconds` | 6 | Fall-damage immunity after event teleports. |
| `arena` | null | Written by `arena build`: center, radius, ceiling and `spawnPoints` (you may hand-edit the spawn points). |

## Building from source

Fabric Loom 1.19 (the version the official 1.21.11 example mod uses) needs **JDK 25 to run Gradle**. The mod itself
compiles to Java 21 bytecode and runs on Java 21 servers.

```bash
./gradlew build        # jar: build/libs/gladiators-1.0.0.jar
./gradlew runServer    # dev server in ./run (accept the EULA in run/eula.txt first)
```

Versions: Minecraft 1.21.11, Fabric Loader 0.20.1, Fabric API 0.141.6+1.21.11, Loom 1.19.4, Gradle 9.8.2, Mojang mappings.

## Layout

```
com.gladiators
  GladiatorsMod        entrypoint, wiring
  command/             /gladiators command tree
  config/              config.json model + loader
  kit/                 kits.json model, loader, item factory
  arena/               geometry (ArenaDesign), build/remove jobs, chunk backups, manager
  event/               start/end/kits (EventManager), deaths + winner, patrol, protection, cleanup
  state/               player snapshots, on-disk store, teleports, fall grace
  mixin/               ServerExplosionMixin (arena survives explosions)
```
