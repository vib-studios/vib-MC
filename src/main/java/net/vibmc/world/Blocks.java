package net.vibmc.world;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;

/** PacketEvents-backed semantic block constants used by gameplay and world storage. */
public final class Blocks {
    private Blocks() {}
    private static WrappedBlockState state(StateType type){return WrappedBlockState.getDefaultState(ClientVersion.V_1_12_2,type);}
    public static boolean same(WrappedBlockState a,WrappedBlockState b){return a!=null&&b!=null&&a.getGlobalId()==b.getGlobalId();}
    public static final WrappedBlockState AIR=state(StateTypes.AIR),STONE=state(StateTypes.STONE),GRASS=state(StateTypes.GRASS_BLOCK),DIRT=state(StateTypes.DIRT),WOOD=state(StateTypes.OAK_LOG),OAK_PLANKS=state(StateTypes.OAK_PLANKS),LEAVES=state(StateTypes.OAK_LEAVES),GLASS=state(StateTypes.GLASS),WATER=state(StateTypes.WATER),LAVA=state(StateTypes.LAVA),CHEST=state(StateTypes.CHEST),FURNACE=state(StateTypes.FURNACE),CRAFTING_TABLE=state(StateTypes.CRAFTING_TABLE),DOOR=state(StateTypes.OAK_DOOR),TRAPDOOR=state(StateTypes.OAK_TRAPDOOR),SAND=state(StateTypes.SAND),GRAVEL=state(StateTypes.GRAVEL),BEDROCK=state(StateTypes.BEDROCK),ANDESITE=state(StateTypes.ANDESITE),DIORITE=state(StateTypes.DIORITE),COAL_ORE=state(StateTypes.COAL_ORE),IRON_ORE=state(StateTypes.IRON_ORE),OBSIDIAN=state(StateTypes.OBSIDIAN),NETHERRACK=state(StateTypes.NETHERRACK),SOUL_SAND=state(StateTypes.SOUL_SAND),GLOWSTONE=state(StateTypes.GLOWSTONE),NETHER_PORTAL=state(StateTypes.NETHER_PORTAL),END_STONE=state(StateTypes.END_STONE),END_PORTAL=state(StateTypes.END_PORTAL),FIRE=state(StateTypes.FIRE),END_PORTAL_FRAME=state(StateTypes.END_PORTAL_FRAME);
    public static final WrappedBlockState END_PORTAL_FRAME_FILLED=filledFrame();
    private static WrappedBlockState filledFrame(){
        WrappedBlockState value=END_PORTAL_FRAME.clone();
        value.setEye(true);
        value.setFacing(com.github.retrooper.packetevents.protocol.world.BlockFace.NORTH);
        return value;
    }
}
