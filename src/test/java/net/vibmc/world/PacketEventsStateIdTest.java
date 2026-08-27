package net.vibmc.world;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import net.vibmc.network.packetevents.PacketEventsChunkAdapter;
import net.vibmc.network.packetevents.PacketEventsStateMappings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PacketEventsStateIdTest {
    @Test
    void legacyStateIdsAreSelected() {
        assertEquals(0, Blocks.AIR.getGlobalId());
        assertEquals(16, Blocks.STONE.getGlobalId());
        assertEquals(121 << 4, Blocks.END_STONE.getGlobalId());
        assertEquals(2 << 4, Blocks.GRASS.getGlobalId());
        assertEquals(58 << 4, Blocks.CRAFTING_TABLE.getGlobalId());
        assertEquals(61 << 4, Blocks.FURNACE.getGlobalId());
    }

    @Test
    void flattenedBlockStateIdsMatchTheTargetRegistry() {
        assertEquals(3051, com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState
                .getDefaultState(ClientVersion.V_1_13,
                        com.github.retrooper.packetevents.protocol.world.states.type.StateTypes.CRAFTING_TABLE).getGlobalId());
        assertEquals(9, com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState
                .getDefaultState(ClientVersion.V_1_13,
                        com.github.retrooper.packetevents.protocol.world.states.type.StateTypes.GRASS_BLOCK).getGlobalId());
        assertEquals(9, com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState
                .getDefaultState(ClientVersion.V_1_14_4,
                        com.github.retrooper.packetevents.protocol.world.states.type.StateTypes.GRASS_BLOCK).getGlobalId());
        assertEquals(3354, com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState
                .getDefaultState(ClientVersion.V_1_14_4,
                        com.github.retrooper.packetevents.protocol.world.states.type.StateTypes.CRAFTING_TABLE).getGlobalId());
        assertEquals(3372, com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState
                .getDefaultState(ClientVersion.V_1_14_4,
                        com.github.retrooper.packetevents.protocol.world.states.type.StateTypes.FURNACE).getGlobalId());
        assertEquals(9,PacketEventsStateMappings.id(Blocks.GRASS,ClientVersion.V_1_13));
        assertEquals(3050,PacketEventsStateMappings.id(Blocks.CRAFTING_TABLE,ClientVersion.V_1_13));
        assertEquals(Blocks.CRAFTING_TABLE.getGlobalId(),
                PacketEventsStateMappings.id(Blocks.CRAFTING_TABLE,ClientVersion.V_1_12_2));
    }

    @Test
    void placedItemTypesPreserveLegacyVariants() {
        com.github.retrooper.packetevents.protocol.world.states.type.StateType carpet=
                com.github.retrooper.packetevents.protocol.item.type.ItemTypes.LIGHT_BLUE_CARPET.getPlacedType();
        assertEquals("light_blue_carpet",carpet.getName());
        com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState lightBlue=
                carpet.createBlockState(ClientVersion.V_1_12_2);
        assertEquals((171<<4)|3,lightBlue.getGlobalId());
        assertEquals((171<<4)|3,PacketEventsStateMappings.id(lightBlue,ClientVersion.V_1_9_3));
        assertEquals((171<<4)|3,PacketEventsStateMappings.id(lightBlue,ClientVersion.V_1_12_1));
        assertEquals(6826,PacketEventsStateMappings.id(lightBlue,ClientVersion.V_1_13));
        assertEquals(7333,PacketEventsStateMappings.id(lightBlue,ClientVersion.V_1_14_4));
        assertEquals(34,PacketEventsStateMappings.id(Blocks.WATER,ClientVersion.V_1_13));
        assertEquals(34,PacketEventsStateMappings.id(Blocks.WATER,ClientVersion.V_1_16_2));
        com.github.retrooper.packetevents.protocol.world.states.type.StateType planks=
                com.github.retrooper.packetevents.protocol.item.type.ItemTypes.DARK_OAK_PLANKS.getPlacedType();
        assertEquals((5<<4)|5,planks.createBlockState(ClientVersion.V_1_12_2).getGlobalId());
    }

    @Test
    void packetEventsProvidesVersionedBiomeIds() {
        assertEquals(0, PacketEventsChunkAdapter.biomeId("minecraft:ocean", ClientVersion.V_1_12_2));
        assertEquals(1, PacketEventsChunkAdapter.biomeId("minecraft:plains", ClientVersion.V_1_12_2));
        assertEquals(2, PacketEventsChunkAdapter.biomeId("minecraft:desert", ClientVersion.V_1_12_2));
        assertEquals(4, PacketEventsChunkAdapter.biomeId("minecraft:forest", ClientVersion.V_1_12_2));
        assertEquals(5, PacketEventsChunkAdapter.biomeId("minecraft:taiga", ClientVersion.V_1_12_2));
        assertEquals(8, PacketEventsChunkAdapter.biomeId("minecraft:nether_wastes", ClientVersion.V_1_12_2));
        assertEquals(9, PacketEventsChunkAdapter.biomeId("minecraft:the_end", ClientVersion.V_1_12_2));
        assertEquals(1,PacketEventsChunkAdapter.biomeId("minecraft:plains", ClientVersion.V_1_13));
        assertEquals(38,PacketEventsChunkAdapter.biomeId("minecraft:plains", ClientVersion.V_1_19_4));
    }
}
