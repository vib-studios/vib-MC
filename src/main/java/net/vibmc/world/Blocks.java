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
    public static final WrappedBlockState COBBLESTONE=state(StateTypes.COBBLESTONE),CACTUS=state(StateTypes.CACTUS),DEAD_BUSH=state(StateTypes.DEAD_BUSH),GOLD_ORE=state(StateTypes.GOLD_ORE),DIAMOND_ORE=state(StateTypes.DIAMOND_ORE),REDSTONE_ORE=state(StateTypes.REDSTONE_ORE),LAPIS_ORE=state(StateTypes.LAPIS_ORE),EMERALD_ORE=state(StateTypes.EMERALD_ORE);
    public static final WrappedBlockState END_PORTAL_FRAME_FILLED=filledFrame();
    private static WrappedBlockState filledFrame(){
        WrappedBlockState value=END_PORTAL_FRAME.clone();
        value.setEye(true);
        value.setFacing(com.github.retrooper.packetevents.protocol.world.BlockFace.NORTH);
        return value;
    }

    /**
     * Compares a block by its kind, ignoring state properties. A placed chest or furnace
     * carries a facing property, so comparing it against the default state with
     * {@link #same} silently fails.
     */
    public static boolean isType(WrappedBlockState state,StateType type){
        return state!=null&&state.getType()==type;
    }

    /** Water or lava, at any flow level. */
    public static boolean isFluid(WrappedBlockState state){
        if(state==null)return false;
        StateType type=state.getType();
        return type==StateTypes.WATER||type==StateTypes.LAVA;
    }

    public static boolean isWater(WrappedBlockState state){return state!=null&&state.getType()==StateTypes.WATER;}
    public static boolean isLava(WrappedBlockState state){return state!=null&&state.getType()==StateTypes.LAVA;}

    /** A fluid or air: something a placed or flowing block may overwrite. */
    public static boolean isReplaceable(WrappedBlockState state){
        return state==null||same(state,AIR)||isFluid(state)||same(state,FIRE)||same(state,DEAD_BUSH);
    }

    /**
     * Whether the block stops movement and supports what is placed on it. Deliberately
     * coarse: vib-MC has no per-block collision shapes, so anything that is not air, a
     * fluid, or a decoration counts as solid.
     */
    public static boolean isSolid(WrappedBlockState state){
        if(state==null)return false;
        if(same(state,AIR)||isFluid(state)||same(state,FIRE)||same(state,DEAD_BUSH))return false;
        StateType type=state.getType();
        return type!=StateTypes.NETHER_PORTAL&&type!=StateTypes.END_PORTAL&&type!=StateTypes.TORCH
                &&type!=StateTypes.OAK_SAPLING;
    }

    /** Blocks that fall when unsupported. */
    public static boolean isGravityAffected(WrappedBlockState state){
        return same(state,SAND)||same(state,GRAVEL);
    }

    /** A fluid's flow distance from its source; 0 for a source block. */
    public static int fluidLevel(WrappedBlockState state){
        return state!=null&&state.hasProperty(com.github.retrooper.packetevents.protocol.world.states.type.StateValue.LEVEL)
                ?state.getLevel():0;
    }

    /** A copy of a fluid state at the given flow level. */
    public static WrappedBlockState fluidAt(WrappedBlockState fluid,int level){
        WrappedBlockState copy=fluid.clone();
        copy.setLevel(Math.max(0,Math.min(15,level)));
        return copy;
    }
}
