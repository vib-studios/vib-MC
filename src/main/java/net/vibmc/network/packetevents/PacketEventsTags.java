package net.vibmc.network.packetevents;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTags;
import net.vibmc.registry.ViaMappingsTags;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Vanilla and dynamic tags delegating to ViaMappingsTags. */
public final class PacketEventsTags {
    private PacketEventsTags() {}

    public static WrapperPlayServerTags create(ClientVersion version) {
        return ViaMappingsTags.create(version);
    }

    public static Map<ResourceLocation, List<WrapperPlayServerTags.Tag>> tagMap(
            ClientVersion version, Set<ResourceLocation> referencedTags) {
        return ViaMappingsTags.tagMap(version, referencedTags);
    }

    public static Map<ResourceLocation, List<WrapperPlayServerTags.Tag>> tagMap(
            ClientVersion version, Set<ResourceLocation> referencedTags,
            Map<ResourceLocation, Set<ResourceLocation>> tagsByRegistry) {
        return ViaMappingsTags.tagMap(version, referencedTags, tagsByRegistry);
    }
}
