# PacketEvents issue draft: legacy chunk writer silently emits dimension-incompatible skylight

## Environment

- PacketEvents Netty Common 2.9.5
- Server version and client version: Minecraft 1.12.2 / protocol 340
- Standalone/custom-server PacketEvents integration
- Affected dimensions: Nether and End

## Summary

`WrapperPlayServerChunkData` will serialize `Chunk_v1_9#getSkyLight()` whenever it is non-null, including when `user.getDimensionType().hasSkyLight()` is false.

For legacy chunk packets, the presence of the 2048-byte skylight array is implicit from the dimension and is not represented by a packet field. Emitting a non-null skylight array in the Nether or End therefore shifts the remainder of the section stream and creates a malformed packet. A ViaFabricPlus 4.6.1 client targeting 1.12.2 translated the malformed column into 16 missing/null modern sections and Lithium later crashed with `MissingPaletteEntryException`.

The caller mistake is attaching skylight to a dimension that does not support it. The requested PacketEvents improvement is to reject or normalize that invalid combination instead of silently writing malformed wire data.

## Relevant PacketEvents behavior

`WrapperPlayServerChunkData#read()` determines legacy skylight presence from the user dimension:

```java
boolean hasSkyLight = this.serverVersion.isNewerThanOrEquals(ServerVersion.V_1_16)
        || this.serverVersion.isOlderThanOrEquals(ServerVersion.V_1_8_8)
        || this.user != null && this.user.getDimensionType().hasSkyLight()
        && this.serverVersion.isOlderThan(ServerVersion.V_1_14);
```

However, the legacy write path calls `Chunk_v1_9.write(...)`, which writes skylight solely according to nullability:

```java
if (chunk.skyLight != null) {
    out.writeBytes(chunk.skyLight.getData());
}
```

Thus read and write do not enforce the same dimension-dependent layout.

## Minimal reproduction outline

1. Configure PacketEvents server/client protocol as 1.12.2.
2. Set the user's dimension type to `DimensionTypes.THE_NETHER` or `DimensionTypes.THE_END`.
3. Create a `Column` containing `Chunk_v1_9` sections.
4. Attach both block light and a non-null `NibbleArray3d` skylight array to each included section.
5. Send `new WrapperPlayServerChunkData(column)`.
6. Decode according to the 1.12.2 Nether/End format. The decoder does not consume skylight, so subsequent section boundaries are shifted.

## Expected behavior

One of:

1. Throw an informative exception when a legacy no-skylight dimension is combined with non-null section skylight; or
2. Suppress the incompatible skylight arrays while writing; or
3. Clearly document that callers must make section lighting match `User#getDimensionType()`.

Throwing is likely safest because silently suppressing data can hide a caller error.

## Actual behavior

PacketEvents emits a malformed legacy chunk packet without warning.

## Application-side workaround

```java
boolean hasSkyLight = user.getDimensionType().hasSkyLight();
section.setSkyLight(hasSkyLight ? skyLight : null);
```

In our server the equivalent decision is made from the world environment before constructing the wrapper.
