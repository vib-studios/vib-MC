package net.vibmc.network.packetevents;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTIntArray;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.protocol.world.states.type.StateValue;
import net.vibmc.registry.Registry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Semantic block-state conversion boundary backed by PacketEvents and ViaVersion Mappings. */
public final class PacketEventsStateMappings {
    private static final Map<Long, Integer> IDS = new ConcurrentHashMap<>();
    private PacketEventsStateMappings() {}

    public static int id(WrappedBlockState source, ClientVersion version) {
        if (source == null) return WrappedBlockState.getDefaultState(version, StateTypes.AIR).getGlobalId();
        int sourceId = source.getGlobalId();
        if (version.isNewerThanOrEquals(ClientVersion.V_1_8) && version.isOlderThan(ClientVersion.V_1_13)) {
            return sourceId;
        }
        long key = ((long) version.getProtocolVersion() << 32) | (sourceId & 0xffffffffL);
        Integer cached = IDS.get(key);
        if (cached != null) return cached;
        int mapped = map(source, version);
        Integer previous = IDS.putIfAbsent(key, mapped);
        return previous == null ? mapped : previous;
    }

    @SuppressWarnings("deprecation")
    private static int map(WrappedBlockState source, ClientVersion version) {
        try {
            NBTCompound mappingsTag = Registry.get().forClient(version);
            NBTIntArray stateMap = (NBTIntArray) mappingsTag.getTagOrNull("blockstates");
            if (stateMap != null && stateMap.getValue() != null) {
                int[] arr = stateMap.getValue();
                int sourceId = source.getGlobalId();
                if (sourceId >= 0 && sourceId < arr.length) {
                    int mappedId = arr[sourceId];
                    if (mappedId >= 0) return mappedId;
                }
            }
        } catch (Exception ignored) {
            /* Fallback to PacketEvents */
        }

        WrappedBlockState target = WrappedBlockState.getDefaultState(version, source.getType(), true);
        if (target != null) {
            for (Map.Entry<StateValue, Object> property : source.getInternalData().entrySet()) {
                if (!target.hasProperty(property.getKey())) continue;
                try {
                    target.setData(property.getKey(), property.getValue());
                } catch (IllegalArgumentException ignored) {
                    /* keep target default */
                }
            }
        }
        if (target == null || target.getType() != source.getType()) {
            target = WrappedBlockState.getDefaultState(version, StateTypes.AIR, true);
        }
        int result = target.getGlobalId();
        if ((version == ClientVersion.V_1_13 || version == ClientVersion.V_1_13_1) && result >= 1128 && result < 8460) {
            result--;
        }
        return result;
    }
}
