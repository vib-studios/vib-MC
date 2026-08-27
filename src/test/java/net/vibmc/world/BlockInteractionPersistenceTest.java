package net.vibmc.world;

import net.vibmc.entity.ServerPlayer;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import net.vibmc.world.storage.WorldStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockInteractionPersistenceTest {
    @TempDir Path temporaryDirectory;

    @Test
    void playerPlacementAndDiggingAreStoredInChunkFiles() {
        WorldStorage storage = new WorldStorage(temporaryDirectory.resolve("world").toString());
        World world = new World(44L, "world", storage, WorldEnvironment.OVERWORLD);
        ServerPlayer player = new ServerPlayer(world, null, "Builder", UUID.randomUUID());
        player.getInventory().setSlot(0, ItemStack.builder().type(ItemTypes.OBSIDIAN).amount(2).build());

        BlockInteractionService.place(player, 0, 90, 0, 1);
        assertEquals(Blocks.OBSIDIAN, world.getBlockAt(0, 91, 0));
        world.chunkManager().saveAll();

        World restored = new World(44L, "world", storage, WorldEnvironment.OVERWORLD);
        assertEquals(Blocks.OBSIDIAN, restored.getBlockAt(0, 91, 0));
        BlockInteractionService.dig(player, 2, 0, 91, 0);
        assertEquals(Blocks.AIR, world.getBlockAt(0, 91, 0));
    }

    @Test
    void placeableItemUsesPacketEventsPlacedBlockType() {
        World world=new World(44L,"world",new WorldStorage(temporaryDirectory.resolve("placed-type").toString()),WorldEnvironment.OVERWORLD);
        ServerPlayer player=new ServerPlayer(world,null,"Builder",UUID.randomUUID());
        player.getInventory().setSlot(0,ItemStack.builder().type(ItemTypes.OAK_PLANKS).amount(1).build());
        BlockInteractionService.place(player,0,90,0,1);
        assertEquals(Blocks.OAK_PLANKS,world.getBlockAt(0,91,0));
    }

    @Test
    void legacyWoodVariantIsNormalizedBeforeImmediateBroadcast() {
        World world=new World(44L,"world",new WorldStorage(temporaryDirectory.resolve("wood-variant").toString()),WorldEnvironment.OVERWORLD);
        ServerPlayer player=new ServerPlayer(world,null,"Builder",UUID.randomUUID());
        player.getInventory().setSlot(0,ItemStack.builder().type(ItemTypes.DARK_OAK_PLANKS).amount(1).build());
        assertEquals(ItemTypes.DARK_OAK_PLANKS,player.getInventory().getSlot(0).getType());
        BlockInteractionService.place(player,0,90,0,1);
        assertEquals(ItemTypes.DARK_OAK_PLANKS.getPlacedType(),world.getBlockAt(0,91,0).getType());
    }

    @Test
    void legacyInventoryMetadataSelectsPlacedVariant() {
        World world=new World(44L,"world",new WorldStorage(temporaryDirectory.resolve("legacy-item-data").toString()),WorldEnvironment.OVERWORLD);
        ServerPlayer player=new ServerPlayer(world,null,"Builder",UUID.randomUUID());
        player.getInventory().setSlot(0,ItemStack.builder().type(ItemTypes.WHITE_CARPET).amount(1)
                .legacyData(3).build());
        BlockInteractionService.place(player,0,90,0,1);
        assertEquals(ItemTypes.LIGHT_BLUE_CARPET.getPlacedType(),world.getBlockAt(0,91,0).getType());
        assertEquals((171<<4)|3,world.getBlockAt(0,91,0).getGlobalId());
    }

    @Test
    void placementAppliesAxisAndHorizontalFacing() {
        World world=new World(44L,"world",new WorldStorage(temporaryDirectory.resolve("directions").toString()),WorldEnvironment.OVERWORLD);
        ServerPlayer player=new ServerPlayer(world,null,"Builder",UUID.randomUUID());
        player.setPositionAndRotation(20,90,20,0.0f,0.0f);

        player.getInventory().setSlot(0,ItemStack.builder().type(ItemTypes.OAK_LOG).amount(1).build());
        BlockInteractionService.place(player,0,90,0,5);
        assertEquals(com.github.retrooper.packetevents.protocol.world.states.enums.Axis.X,
                world.getBlockAt(1,90,0).getAxis());

        player.getInventory().setSlot(0,ItemStack.builder().type(ItemTypes.FURNACE).amount(1).build());
        BlockInteractionService.place(player,3,90,0,1);
        assertEquals(com.github.retrooper.packetevents.protocol.world.BlockFace.NORTH,
                world.getBlockAt(3,91,0).getFacing());
    }

    @Test
    void eyesFillACompletedEndPortalFrame() {
        World world = new World(44L,"world",new WorldStorage(temporaryDirectory.resolve("endportal").toString()),WorldEnvironment.OVERWORLD);
        for(int i=1;i<=3;i++) {
            world.setBlockAt(i,80,0,Blocks.END_PORTAL_FRAME_FILLED); world.setBlockAt(i,80,4,Blocks.END_PORTAL_FRAME_FILLED);
            world.setBlockAt(0,80,i,Blocks.END_PORTAL_FRAME_FILLED); world.setBlockAt(4,80,i,Blocks.END_PORTAL_FRAME_FILLED);
        }
        world.setBlockAt(2,80,0,Blocks.END_PORTAL_FRAME);
        ServerPlayer player=new ServerPlayer(world,null,"Builder",UUID.randomUUID());
        player.getInventory().setSlot(0,ItemStack.builder().type(ItemTypes.ENDER_EYE).amount(1).build());
        BlockInteractionService.place(player,2,80,0,1);
        assertEquals(Blocks.END_PORTAL,world.getBlockAt(2,80,2));
    }

    @Test
    void flintAndSteelActivatesAValidObsidianFrame() {
        World world = new World(44L, "world", new WorldStorage(temporaryDirectory.resolve("portal").toString()), WorldEnvironment.OVERWORLD);
        for (int x=0;x<4;x++) for(int y=0;y<5;y++) if(x==0||x==3||y==0||y==4) world.setBlockAt(x,80+y,0,Blocks.OBSIDIAN);
        ServerPlayer player = new ServerPlayer(world, null, "Builder", UUID.randomUUID());
        player.getInventory().setSlot(0, ItemStack.builder().type(ItemTypes.FLINT_AND_STEEL).amount(1).build());

        BlockInteractionService.place(player, 0, 80, 0, 1);

        assertEquals(Blocks.NETHER_PORTAL, world.getBlockAt(1,81,0));
        assertTrue(world.chunkManager().getUnsavedChunkCount() > 0);
    }
}
