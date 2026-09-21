# PacketEvents / Netty migration

PacketEvents is the Minecraft wire-protocol implementation and Netty is the socket transport.
vib-MC implements exactly one wire protocol — **vanilla 1.12.2, protocol 340** — so PacketEvents
is used purely as transport and wrapper serialization, never as the game's data model.

## Completed

- Netty event loops replaced the Java selector transport.
- PacketEvents standalone runtime, users, profiles, channel injector, decoder, and encoder are active.
- AES/CFB8 encryption is implemented as Netty pipeline handlers.
- Handshake, status, login, and play input use PacketEvents wrappers/listeners. Only protocol 340 is
  accepted (`HandshakeHandler.isSupportedProtocol`), and only `ClientVersion.V_1_12_2` proceeds to login.
- Every outbound packet family uses PacketEvents wrappers, including lifecycle, entity, player list,
  inventory, join/respawn, and chunk packets; the handful of 1.12 payloads without a clean wrapper
  (sound 0x49, window items 0x14, cursor slot 0x16, entity equipment 0x3C) use raw `PacketWriter`
  writers flushed unframed through Netty via `PacketSender`.
- `User.sendPacket(wrapper)` is used for destination-aware sends; this fixed cross-channel entity
  visibility and ordering.
- Chunk output uses explicit PacketEvents `Chunk_v1_9` sections, legacy palettes, block light,
  skylight, byte biome data, `Column`, and `WrapperPlayServerChunkData`.
- Removed Java NIO selectors, custom frame parser, packet DTOs, `Packet`, `PacketBuffer`, raw
  packet-ID registries, `ProtocolBase`, `Protocol340`, `ProtocolImplementation`, AutoService
  discovery, and the legacy chunk serializer.
- A `ServerPlayer` is created immediately with PacketEvents `User`; handshake/login fields remain
  nullable until known.
- `ServerPlayer.isInWorld()` distinguishes authenticated/in-world players from pre-login sessions.
- `PendingConnection`, `ServerPackets`, and `PacketEventsSender` were removed. Wrappers are
  constructed at call sites and sent with `User.sendPacket(...)` directly.
- PacketEvents packet-type tables are prewarmed during startup because their lazy legacy
  initialization is not safe when the first two logins occur concurrently on different Netty event loops.
- Chat uses `User.sendMessage(Component)`, which selects the correct legacy chat representation for 1.12.2.
- Player profiles and texture properties use PacketEvents `UserProfile`/`TextureProperty` directly.

## "Full switch" away from PacketEvents data types

- `net.vibmc.mappings.Mappings` loads a vendored ViaVersion 1.12 mapping snapshot
  (`/vendored/via-mappings/mapping-1.12.json`): blocks keyed by combined id `blockId<<4|data`,
  item IDs, sounds, enchantments, and tags. `Protocol340`, `PacketEventsStateMappings`,
  `PacketEventsTags`, `WrapperConfigServerUpdateTags`, `ConfigurationHandler`, and the whole
  `net.vibmc.registry` package were deleted.
- World chunks store `net.vibmc.world.block.BlockState` (a semantic name + 1.12 data nibble)
  whose global id IS the 1.12 combined id, so chunk palette encoding (`PacketEventsChunkAdapter`),
  world persistence, and block interaction all share one value. `WrappedBlockState` is gone.
- Inventories, creative actions, `/give`, durability, placement, and portal items use
  `net.vibmc.inventory.ItemStack`/`ItemType`/`ItemTypes`. PacketEvents `ItemStack` remains only
  where a wrapper hands us a stack (creative inventory), and is converted via a small `toVibItem`
  helper (legacy metadata is kept as the damage value).
- The server is protocol-340-only end to end: no configuration phase, no login-payload
  negotiation, no multi-version adapter seams.
- The PrismarineJS minecraft-data submodule stays as a developer reference but is excluded from
  the runnable JAR via `sourceSets.main.resources.exclude 'vendored/minecraft-data/**'`.
- Chunk persistence format v2 stores combined block-state global ids. Older rapid-development
  worlds intentionally require reset.

## Chunk sending pipeline

1. `PlayerManager` computes the desired chunk ring and atomically reserves each coordinate in `ServerPlayer.sentChunks` before generation or sending.
2. `WorldChunk` provides `BlockState` sections and semantic biome data.
3. `PacketEventsChunkAdapter` builds 1.12-compatible legacy-palette `Chunk_v1_9` sections, block light, skylight, byte biome data, and a full `Column`.
4. `WrapperPlayServerChunkData` is sent through `User.sendPacket(...)`.
5. PacketEvents serializes for `ClientVersion.V_1_12_2` and runs outbound listeners.
6. Netty's packet formatter adds the VarInt frame length.
7. Online-mode AES/CFB8 encryption runs after framing.
8. Netty writes and flushes the final bytes.

Chunks stream in expanding rings. Reserving coordinates before sending prevents the login and tick threads from sending the same chunk concurrently. Crossing a chunk border keeps the fully streamed radius and sends the new edge before unloading the old distant edge, avoiding visible holes.

Packet sending calls `User.sendPacket(...)` directly; Netty performs the channel handoff. Dimension transfer and chunk-set mutation are synchronized on `ServerPlayer`, so the tick thread cannot generate old-world chunks during the transfer.

For compatibility with ViaFabricPlus translating 1.12.2 chunks into modern PalettedContainer sections, all 16 sections are emitted explicitly. Empty sections contain a one-entry legacy air palette instead of being omitted. This directly addresses the supplied Lithium crash details (`IndexBits: 4`, sixteen null entries, empty table) without adding an extra event-loop scheduling hop.

## Remaining cleanup

- Add namespaced-state persistence if long-term save compatibility becomes a goal.
- Add golden wrapper fixtures for protocol 340.
- Continue replacing broad static constants with injected registries where useful for plugins/tests.

## Compatibility rules

- Runnable distributions are Shadow JARs containing Netty, PacketEvents, and Adventure.
- Java 8 bytecode remains the target.
- Existing world/player formats must be migrated rather than silently discarded.
- The project is GPL-3.0-or-later because PacketEvents is GPL-3.0.