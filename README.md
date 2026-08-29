# vib-MC

<div align="center">

[![Work in Progress](https://img.shields.io/badge/status-WIP-red?style=for-the-badge)]()
[![AI Generated](https://img.shields.io/badge/AI-Generated-9cf?style=for-the-badge)]()
[![Vibecoded](https://img.shields.io/badge/Vibecoded-ff69b4?style=for-the-badge)]()
[![Minecraft](https://img.shields.io/badge/Minecraft-1.8--26.1.2-blue?style=for-the-badge&logo=minecraft)]()
[![License: GPL--3.0](https://img.shields.io/badge/License-GPL--3.0-yellow?style=for-the-badge)](LICENSE)
[![Release](https://img.shields.io/badge/Release-v0.0.7-blue?style=for-the-badge)](https://github.com/vib-studios/vib-MC/releases/tag/v0.0.7)

**vibed into existence** — a Minecraft server made entirely by AI, one prompt at a time.

**Minecraft 1.8 → 26.1.2** — excluding 1.16, 1.16.1 and 1.19.3

</div>

---

`vib-MC` is a Minecraft Java Edition server implementation built from scratch through AI-assisted/vibecoded development — no vanilla code, no Bukkit fork, just prompts, one at a time. It started as an experiment in "can this even connect", and it is now a real, playable server: clients from **Minecraft 1.8 through 26.1.2** (excluding 1.16, 1.16.1 and 1.19.3) can join the same server, walk around persistent procedurally generated worlds, and — as of v0.0.7 — actually play survival.

The project focuses on experimenting with Minecraft server internals, custom world generation, persistence, multiplayer, plugins, and making the server easy to extend.

vib-MC is a hobby/experimental project and is not intended to replace mature server software such as Paper or Vanilla. Development is rapid and early: world and player-data formats may, while unlikly, change without migration, and resets could be required at any time.

## Latest Release — v0.0.7 — the survival release

**v0.0.7 is where vib-MC stops being a world you look at and becomes one you can play.** Blocks drop what you break, tools wear out, sand and gravel fall, water and lava flow, leaves decay, and ore generates in veins. You can starve, drown, suffocate, burn and fall — and heal again when you are fed. Crafting works in your inventory and at a crafting table, furnaces smelt, chests keep 27 slots across restarts, armour reduces damage, and PvP hits back with knockback. Blocks, damage and deaths finally make sounds.

No mobs yet, and no item entities — drops go straight into your inventory. vib-MC is not a Vanilla or Paper replacement, and not every Minecraft mechanic is implemented, but the survival loop is real.

### Multi-version support

vib-MC accepts Minecraft Java Edition clients from **1.8 through 26.1.2**, over PacketEvents-based multi-version networking on top of Netty.

Explicitly **not** supported: **1.16**, **1.16.1** and **1.19.3** — those three are rejected at handshake (see [Protocol version model](#protocol-version-model) for why).

## Previous Release — v0.0.6

v0.0.6 - Add PacketEvents-based protocol handling, player storage, world persistence and generation, structure resources, updated tests, and supporting development tools.

### World persistence

- Persistent `world/` directory
- Persistent `level.dat`
- Persistent world seed
- Persistent chunk storage under `world/region/`
- UUID-keyed player state under `playerdata/` (dimension, position, health, food, game mode, flight, selected slot, and complete inventory item NBT)
- Gzipped chunk data
- Chunks load from disk when already saved
- New chunks are generated only when they do not already exist
- Incremental saving — unchanged chunks are not rewritten
- `/save-all` actually saves changed chunks and reports how many were written
- Automatic saving through `autosave-interval-ticks`
- Runtime `/save-on` and `/save-off` control; `/save-all` always forces a save
- Corrupt/truncated chunk files are detected and regenerated
- Chunk writes use temporary files before replacement to reduce partial-write corruption

### Configuration

```properties
autosave-interval-ticks=6000
```

Set `autosave-interval-ticks=0` to disable automatic saving.

### New-world seeds

The default is now `seed=`. A blank seed chooses a random 64-bit seed once, writes it to the new world's `level.dat`, and restores that same seed on every restart. Existing `server.properties` files are never rewritten, and an existing saved world's seed always wins.

### Authentication and proxies

- `online-mode=true` uses the Minecraft 1.12.2 RSA/AES login handshake and Mojang session-server verification.
- Authenticated UUIDs and signed skin properties are forwarded to clients.
- `proxy-mode=legacy` accepts trusted BungeeCord forwarding and Velocity's 1.12.2-compatible **legacy** forwarding mode.
- Set `proxy-trusted-address` to the proxy's backend-facing address. Direct connections are rejected in proxy mode.
- Velocity modern forwarding cannot exist on protocol 340 because Login Plugin Request was added after Minecraft 1.12.2; configure Velocity with `player-info-forwarding-mode = "legacy"` for this server.

```properties
online-mode=true
proxy-mode=none
proxy-trusted-address=127.0.0.1
shutdown-message=Server closed
```

### World layout

```text
world/
├── level.dat            seed, time, weather, and world metadata
└── region/
    └── r.<x>.<z>.chunk  gzipped chunk data
```

For an existing world, the seed stored in `level.dat` takes priority over the configured seed so an existing world cannot silently change its terrain generation seed.

## Features

| Status | Feature |
|:------:|---------|
| ✅ | Server startup |
| ✅ | Minecraft 1.8 – 26.1.2 client connections (except 1.16, 1.16.1, 1.19.3) |
| ✅ | Custom world generation |
| ✅ | Chunk generation and streaming |
| ✅ | Players appear in each other's tab list and world |
| ✅ | Player movement and disconnects synchronize between clients |
| ✅ | Direct online-mode authentication and legacy proxy forwarding |
| ✅ | Persistent Overworld, Nether, and End dimensions |
| ✅ | Detection-based Nether portal activation and dimension travel |
| ✅ | Lenient vanilla-style flight detection when `allow-flight=false` |
| ✅ | Server-stop and unsupported-version kick messages |
| ✅ | World persistence |
| ✅ | Chunk persistence |
| ✅ | Incremental world saving |
| ✅ | Plugin API |
| ✅ | Commands |
| ✅ | Multiplayer-oriented server architecture |
| 🚧 | More advanced terrain generation |
| ✅ | Per-column biome data sent to clients |
| ✅ | Data-driven structure templates with default oak trees |
| ✅ | Nether |
| ✅ | Block breaking, placement and block drops |
| ✅ | Block physics — falling sand/gravel, flowing water and lava, leaf decay |
| ✅ | Hunger, food and damage (fall, void, drowning, suffocation, fire, lava, cactus, starvation) |
| ✅ | Crafting, smelting and chest storage |
| ✅ | Armour and PvP combat |
| ❌ | Mobs |

## World Generation

vib-MC has its own procedural world generator rather than relying on Vanilla's terrain generator.

The generator is being expanded with features such as:

- Hills and varied terrain
- Sand and water
- Trees
- Ores
- Caves and cave entrances
- Different environments/biomes
- Generated structures

World generation uses a seed and remains deterministic for a given world seed. New chunks now use vanilla-scale sea level and layered grass/dirt/stone terrain, beaches, oceans, hills, caves, coal and iron ore. The Nether has a bedrock roof, netherrack caverns, soul sand, and lava; the End has deterministic central islands. Oak trees are placed through data-driven structure templates with biome inclusion/exclusion and terrain anchors, so they do not generate in deserts or oceans and trunks remain grounded. Every newly generated structured Overworld has a deterministic multi-piece village near spawn with four houses, roads, a well, and a farm. Existing saved chunks are preserved; only newly generated chunks use the newer generator.

## Data-driven structures

Generated objects are owned by the structure system rather than hardcoded into the terrain generator. The registry supports reusable `.vstruct` pieces, weighted and recursively nested `.vpool` pools, and multi-piece `.vstructure` composites. Templates support palettes, individual blocks, compact cuboid `fill` operations, biome rules, rotation, terrain anchoring, per-block surface conformance, spacing, salts, and deterministic selection. Built-ins are indexed under `src/main/resources/structures/`, while server-specific files under `structures/` can add or override registry entries.

The spawn village is a real composite resource at `structures/village/spawn_village.vstructure`. Its roads, house, well, and farm are separate templates, and house nodes resolve through nested weighted pools. Composite node coordinates identify the child template's declared anchor—not its minimum corner—so rotation keeps a piece centered on its requested position. `terrain` anchoring places template Y=0 at the terrain surface, while `surface` projects every block column onto its own terrain surface. Composites can declare `exclude-structures`; the village excludes `minecraft:oak_tree` across its configured footprint, preventing trunks or canopies from intersecting the village. Oak trees remain standalone structure templates. Both are disabled when `generate-structures=false`.

```properties
name=example:small_rock
size=2,1,2
dimension=overworld
spacing=12
salt=12345
chance=0.25
palette.0=minecraft:stone
block=0,0,0,0
block=1,0,0,0
```

## Blocks, portals, and persistence

Player digging and placement now update authoritative chunks, broadcast block-change packets, invalidate the network cache, and persist through autosave or `/save-all`. Creative hotbar updates and basic survival stack consumption are supported. Nether portals are no longer generated: build a standard 4x5 obsidian frame and use flint and steel on it; the server detects the completed frame and fills its interior. End Portal Frames can be placed, filled one-by-one with Eyes of Ender, and activate a completed 12-frame ring.

## Operators

`/op <online-player>` and `/deop <online-player>` write UUID-based, vanilla-shaped `ops.json` entries. Operators bypass command permission checks. The server console can bootstrap the first operator.

## Protocol organization

Protocol 340 packet IDs, version metadata, brand channel, and wire constants are centralized in `Protocol340`, reducing version-specific literals in handlers and providing a clear seam for future protocol adapters. The server sends `MC|Brand` with `vib-MC` after login and includes per-column biome bytes in full chunk packets.

## Protocol version model

vib-MC uses Minecraft 1.12.2 semantics and registries while PacketEvents owns handshake version detection and connection-state transitions. Because this custom endpoint accepts multiple wire versions directly, PacketEvents prepares each wrapper against the target `User#getClientVersion()` so packet IDs and version-dependent wrapper layouts match that connection. PacketEvents currently exposes this behavior behind its `ChannelInjector#isProxy()` capability even though vib-MC is not a forwarding proxy; no ViaVersion translation layer is installed. This does not invent semantic translations for concepts vib-MC has not implemented, but supported wrappers are encoded with the target protocol's IDs and layouts. Minecraft 1.16 and 1.16.1 are explicitly rejected because PacketEvents 2.13 reports incorrect clientbound packet IDs for those two releases. Minecraft 1.19.3 is also rejected because PE reads a profile-key field that the release no longer sends. Verified compatibility currently stops at exactly Minecraft 26.1.2.

## PacketEvents-backed world data

World chunks store PacketEvents `WrappedBlockState` objects directly, and inventories use PacketEvents `ItemStack`/`ItemType` directly. Structure palettes, terrain generation, block interaction, creative inventory updates, durability, `/give`, persistence, and chunk packets all share those semantic objects instead of parallel vib-MC block/item IDs.

A `ServerPlayer` is created immediately for every PacketEvents `User`. Its world, UUID, username, and other not-yet-known login state remain nullable until authentication completes; `isInWorld()` distinguishes active gameplay players. Packet wrappers are created at call sites and sent directly with `User.sendPacket(...)`; there is no pending-connection class, packet facade, or parallel protocol implementation.

Generator biomes use stable Minecraft resource keys. PacketEvents remains responsible for gameplay mappings and packet wrappers. The vendored PrismarineJS minecraft-data snapshots are used only for Java-edition registry/configuration payloads such as the modern Join Game dimension codec. `tools/update-minecraft-data.sh` creates a sparse checkout containing only the required PC `version.json` and `loginPacket.json` snapshots plus their indexes, and Gradle verifies that no unrelated datasets enter the runnable JAR.

## Movement policy

The server intentionally has almost no gameplay anti-cheat. It rejects malformed/non-finite coordinates and performs the same broad kind of lenient floating check used by vanilla 1.12.2: sustained unsupported airtime is allowed for a grace period, with exemptions for flight-enabled, creative, spectator, teleporting, and grounded players. It does not add speed, reach, combat, inventory, or heuristic cheat detection.

## Plugins

vib-MC includes plugin support so servers can be extended without modifying the core server.

Plugins can be used for things such as:

- Commands
- Gameplay mechanics
- Events
- Server utilities
- Custom features

Plugin files are loaded from:

```text
plugins/
```

The project also aims to make plugin creation accessible through a Scratch-style/no-code creator on the website, allowing users to construct simple plugins without having to write code.

## Commands

Current commands include:

```text
/help
/tp
/gamemode
/time
/weather
/give
/kill
/say
/seed
/save-all
/save-on
/save-off
/stop
/list
/op
/deop
/kick
/dimension
```

## Architecture

```text
net.vibmc.server        — server core
net.vibmc.network       — Minecraft networking
net.vibmc.world         — world and block systems
net.vibmc.world.gen     — world generation
net.vibmc.world.storage — world persistence
net.vibmc.entity        — entities
net.vibmc.player        — players
net.vibmc.plugin        — plugin support
net.vibmc.command       — commands
net.vibmc.permission    — permissions
```

## Requirements

- Java 8 or newer to run the current server/release line
- JDK 17 or newer to run the Gradle 9.7 build (output bytecode still targets Java 8)
- Minecraft Java Edition for client testing

For building from source, use the repository's Gradle wrapper when available:

```bash
./gradlew clean build
```

The resulting server JAR is produced under `build/libs/`.

With a local server running, the protocol tests can verify terrain streaming and two-player visibility:

```bash
cd tools
npm ci
npm test
npm run test:players
npm run test:dimensions
```

## Version History

| Status | Version |
|:------:|---------|
| ✅ | v0.0.1 — alpha |
| ✅ | v0.0.2 — stable |
| ✅ | v0.0.3 — stable |
| ✅ | v0.0.4 — stable |
| ✅ | v0.0.4-hotfix.1 |
| ✅ | v0.0.4-hotfix.2 |
| ✅ | v0.0.4-hotfix.3 |
| ✅ | v0.0.5 - stable |
| ✅ | v0.0.5-hotfix.1 |
| ✅ | v0.0.6 - PacketEvents & Netty based networking w/ multi-version |
| ✅ | v0.0.6-hotfix.1 |
| 🚀 | **v0.0.7 - survival: drops, physics, hunger, crafting, smelting, storage, combat** |

## License

GPL-3.0-or-later. This project is being relicensed for its PacketEvents-based networking stack. See `LICENSE`.


