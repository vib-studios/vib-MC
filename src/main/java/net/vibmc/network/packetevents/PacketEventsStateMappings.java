package net.vibmc.network.packetevents;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.protocol.world.states.type.StateValue;
import net.vibmc.registry.ViaMappings;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** One semantic block-state conversion boundary shared by chunks and incremental updates. */
public final class PacketEventsStateMappings {
    private static final Map<Long, Integer> IDS = new ConcurrentHashMap<>();
    private static volatile int[] inverse13To132 = null;

    private PacketEventsStateMappings() {}

    public static int id(WrappedBlockState source, ClientVersion version) {
        if (source == null) {
            return WrappedBlockState.getDefaultState(version, StateTypes.AIR).getGlobalId();
        }
        int sourceId = source.getGlobalId();
        if (version.isNewerThanOrEquals(ClientVersion.V_1_8)
                && version.isOlderThan(ClientVersion.V_1_13)) {
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
        WrappedBlockState target = WrappedBlockState.getDefaultState(version, source.getType(), true);
        if (target != null) {
            for (Map.Entry<StateValue, Object> property : source.getInternalData().entrySet()) {
                if (!target.hasProperty(property.getKey())) continue;
                try {
                    target.setData(property.getKey(), property.getValue());
                } catch (IllegalArgumentException ignored) {
                    // keep target default
                }
            }
        }
        if (target == null || target.getType() != source.getType()) {
            target = WrappedBlockState.getDefaultState(version, StateTypes.AIR, true);
        }
        int result = target.getGlobalId();

        // PacketEvents 2.13 groups 1.13/1.13.1 with 1.13.2. Use ViaVersion blockstate mapping
        // (mappings-1.13to1.13.2.nbt) to invert 1.13.2 -> 1.13 correctly, replacing the previous
        // magic -1 shift. This is data-driven via ViaVersion Mappings + PE NBT reader.
        if (version == ClientVersion.V_1_13 || version == ClientVersion.V_1_13_1) {
            int[] inverse = getInverse13();
            if (inverse != null && result >= 0 && result < inverse.length) {
                int mapped = inverse[result];
                if (mapped >= 0) {
                    return mapped;
                }
            }
        }
        return result;
    }

    private static int[] getInverse13() {
        if (inverse13To132 != null) return inverse13To132;
        synchronized (PacketEventsStateMappings.class) {
            if (inverse13To132 != null) return inverse13To132;
            try {
                inverse13To132 = ViaMappings.get().blockStateInverse13To132();
            } catch (Exception ex) {
                // If ViaVersion data unavailable, leave as null and use identity (no shift)
                inverse13To132 = new int[0];
            }
            return inverse13To132;
        }
    }
}
