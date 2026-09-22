package net.vibmc.network.packetevents;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import net.vibmc.registry.ViaMappingsStateMapper;

/** Block-state mapping boundary delegating to ViaMappingsStateMapper. */
public final class PacketEventsStateMappings {
    private PacketEventsStateMappings() {}

    public static int id(WrappedBlockState source, ClientVersion version) {
        return ViaMappingsStateMapper.id(source, version);
    }
}
