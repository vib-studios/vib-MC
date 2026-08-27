# PacketEvents / Netty migration

PacketEvents is the Minecraft wire-protocol implementation and Netty is the socket transport.

## Completed

- Netty event loops replaced the Java selector transport.
- PacketEvents standalone runtime, users, profiles, channel injector, decoder, and encoder are active.
- AES/CFB8 encryption is implemented as Netty pipeline handlers.
- Handshake, status, login, and play input use PacketEvents wrappers/listeners.
- Every outbound packet family uses PacketEvents wrappers, including lifecycle, entity, player list, inventory, join/respawn, and chunk packets.
- `User.sendPacket(wrapper)` is used for destination-aware sends; this fixed cross-channel entity visibility and ordering.
- Chunk output uses explicit PacketEvents `Chunk_v1_9` sections, palettes, block light, skylight, byte biome data, `Column`, and `WrapperPlayServerChunkData`.
- Block-state conversion uses PacketEvents `StateTypes` and `WrappedBlockState` mappings.
- Initial chunks are streamed in expanding rings so entity/player-list packets are not starved behind large chunk queues.
- Removed Java NIO selectors, custom frame parser, packet DTOs, `Packet`, `PacketBuffer`, raw packet-ID registries, `ProtocolBase`, `Protocol340`, `ProtocolImplementation`, AutoService discovery, and the legacy chunk serializer.
- A `ServerPlayer` is created immediately with PacketEvents `User`; handshake/login fields remain nullable until known.
- `ServerPlayer.isInWorld()` distinguishes authenticated/in-world players from pre-login sessions.
- `PendingConnection`, `ServerPackets`, and `PacketEventsSender` were removed. Wrappers are constructed at call sites and sent with `User.sendPacket(...)` directly.
- PacketEvents packet-type tables are prewarmed during startup because their lazy legacy initialization is not safe when the first two logins occur concurrently on different Netty event loops.
- Chat uses `User.sendMessage(Component)`, which selects the correct legacy chat representation for 1.12.2.
- Player profiles and texture properties use PacketEvents `UserProfile`/`TextureProperty` directly.

## Domain migration status

- World chunks store PacketEvents `WrappedBlockState` directly.
- Inventories, creative actions, `/give`, durability, placement, and portal items use PacketEvents `ItemStack`/`ItemType` directly.
- Chunk persistence format v2 stores PacketEvents wrapped-state global IDs. Older rapid-development worlds intentionally require reset.
- Semantic generator biomes map to PacketEvents `Biome` identities. Protocol 1.12.2 still requires legacy byte biome IDs, which PacketEvents does not expose as a stable public numeric registry, so the adapter contains the seven required legacy numbers.
- The server retains `Chunk`, `World`, and `Inventory` as gameplay/persistence owners; their protocol-represented contents are PacketEvents objects.

## Chunk sending pipeline

1. `PlayerManager` computes the desired chunk ring and atomically reserves each coordinate in `ServerPlayer.sentChunks` before generation or sending.
2. `WorldChunk` provides PacketEvents `WrappedBlockState` sections and semantic biome data.
3. `PacketEventsChunkAdapter` creates 1.12-compatible `Chunk_v1_9` sections, palettes, block light, skylight, byte biome data, and a full `Column`.
4. `WrapperPlayServerChunkData` is sent through `User.sendPacket(...)`.
5. PacketEvents serializes for `ClientVersion.V_1_12_2` and runs outbound listeners.
6. Netty's packet formatter adds the VarInt frame length.
7. Online-mode AES/CFB8 encryption runs after framing.
8. Netty writes and flushes the final bytes.

Chunks stream in expanding rings. Reserving coordinates before sending prevents the login and tick threads from sending the same chunk concurrently. Crossing a chunk border keeps the fully streamed radius and sends the new edge before unloading the old distant edge, avoiding visible holes.

Packet sending calls `User.sendPacket(...)` directly; Netty performs the channel handoff. Dimension transfer and chunk-set mutation are synchronized on `ServerPlayer`, so the tick thread cannot generate old-world chunks during the transfer.

For compatibility with ViaFabricPlus translating 1.12.2 chunks into modern PalettedContainer sections, all 16 sections are emitted explicitly. Empty sections contain a one-entry air palette instead of being omitted. This directly addresses the supplied Lithium crash details (`IndexBits: 4`, sixteen null entries, empty table) without adding an extra event-loop scheduling hop.

## Remaining cleanup

- Add namespaced-state persistence if long-term save compatibility becomes a goal.
- Add golden wrapper fixtures for protocol 340.
- Continue replacing broad static constants with injected registries where useful for plugins/tests.

## Compatibility rules

- Runnable distributions are Shadow JARs containing Netty, PacketEvents, and Adventure.
- Java 8 bytecode remains the target.
- Existing world/player formats must be migrated rather than silently discarded.
- The project is GPL-3.0-or-later because PacketEvents is GPL-3.0.
