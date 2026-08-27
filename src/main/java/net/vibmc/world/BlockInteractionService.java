package net.vibmc.world;

import net.vibmc.entity.ServerPlayer;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import net.vibmc.player.GameMode;
import net.vibmc.server.VibMC;

import java.util.List;

/** Applies client block actions to authoritative world/chunk state. */
public final class BlockInteractionService {
    private static final int[][] FACE_OFFSETS = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };

    private BlockInteractionService() {}

    public static void dig(ServerPlayer player, int status, int x, int y, int z) {
        World world = player.getWorld();
        WrappedBlockState existing = world.getBlockAt(x, y, z);
        boolean complete = status == 2 || (status == 0
                && (player.getGameModeEnum() == GameMode.CREATIVE || Blocks.same(existing,Blocks.FIRE)));
        if (!complete) return;
        if (Blocks.same(existing,Blocks.AIR) || Blocks.same(existing,Blocks.BEDROCK)) return;
        if (world.setBlockAt(x, y, z, Blocks.AIR)) {
            broadcast(world, x, y, z, Blocks.AIR);
        }
    }

    public static void place(ServerPlayer player, int clickedX, int clickedY, int clickedZ, int face) {
        ItemStack held = player.getInventory().getSlot(player.getHeldItemSlot());
        if (held.isEmpty()) return;
        World world = player.getWorld();
        if (held.getType() == ItemTypes.ENDER_EYE) {
            List<int[]> activated = PortalDetector.insertEyeAndActivate(world, clickedX, clickedY, clickedZ);
            broadcast(world, clickedX, clickedY, clickedZ, world.getBlockAt(clickedX, clickedY, clickedZ));
            for (int[] position : activated) broadcast(world, position[0], position[1], position[2], Blocks.END_PORTAL);
            if (player.getGameModeEnum() != GameMode.CREATIVE) { player.getInventory().removeItem(player.getHeldItemSlot(),1); player.sendInventory(); }
            return;
        }
        if (held.getType() == ItemTypes.FLINT_AND_STEEL) {
            List<int[]> activated = PortalDetector.activateNear(world, clickedX, clickedY, clickedZ);
            for (int[] position : activated) {
                broadcast(world, position[0], position[1], position[2], Blocks.NETHER_PORTAL);
            }
            if (!activated.isEmpty() && player.getGameModeEnum()!=GameMode.CREATIVE) { held.setDamageValue(held.getDamageValue()+1);if(held.getMaxDamage()>0&&held.getDamageValue()>=held.getMaxDamage())held.setAmount(0);player.getInventory().setSlot(player.getHeldItemSlot(),held);player.sendInventory(); }
            return;
        }
        if (face < 0 || face >= FACE_OFFSETS.length) return;
        WrappedBlockState block = blockFor(held);
        if (Blocks.same(block,Blocks.AIR)) return;
        block = orientForPlacement(block, player, face);
        int[] offset = FACE_OFFSETS[face];
        int x = clickedX + offset[0];
        int y = clickedY + offset[1];
        int z = clickedZ + offset[2];
        int playerX=(int)Math.floor(player.getX()),playerZ=(int)Math.floor(player.getZ());
        int feetY=(int)Math.floor(player.getY());
        if(x==playerX&&z==playerZ&&(y==feetY||y==feetY+1))return;
        WrappedBlockState replaced = world.getBlockAt(x, y, z);
        if (!Blocks.same(replaced,Blocks.AIR) && !Blocks.same(replaced,Blocks.WATER) && !Blocks.same(replaced,Blocks.LAVA)) return;
        if (world.setBlockAt(x, y, z, block)) {
            broadcast(world, x, y, z, block);
            if (player.getGameModeEnum() != GameMode.CREATIVE) {
                player.getInventory().removeItem(player.getHeldItemSlot(), 1);
                player.sendInventory();
            }
        }
    }

    private static void broadcast(World world, int x, int y, int z, WrappedBlockState block) {
        VibMC server = VibMC.getInstance();
        if (server != null) server.getPlayerManager().broadcastBlockChange(world, x, y, z, block);
    }

    private static WrappedBlockState blockFor(ItemStack held) {
        if(held==null||held.isEmpty())return Blocks.AIR;
        ItemType type=held.getType();
        com.github.retrooper.packetevents.protocol.world.states.type.StateType placed=type.getPlacedType();
        if(placed==null||placed.isAir())return Blocks.AIR;
        WrappedBlockState state=placed.createBlockState(
                com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_12_2);
        if(state==null)return Blocks.AIR;
        // Legacy inventory decoding may expose a generic item type plus its variant metadata.
        // Apply that metadata to the canonical combined block ID before placement.
        int legacyData=held.getLegacyData();
        if(legacyData>0&&legacyData<=15)state=WrappedBlockState.getByGlobalId(
                com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_12_2,
                (state.getGlobalId()&~0x0f)|(legacyData&0x0f),true);
        return state;
    }

    /** Applies the common vanilla orientation properties using PE's typed state API. */
    private static WrappedBlockState orientForPlacement(WrappedBlockState original,
                                                         ServerPlayer player, int faceId) {
        WrappedBlockState state=original.clone();
        com.github.retrooper.packetevents.protocol.world.BlockFace clicked=
                com.github.retrooper.packetevents.protocol.world.BlockFace.getLegacyBlockFaceByValue(faceId);
        com.github.retrooper.packetevents.protocol.world.BlockFace playerFacing=horizontalFacing(player.getYaw());

        if(state.hasProperty(com.github.retrooper.packetevents.protocol.world.states.type.StateValue.AXIS)){
            com.github.retrooper.packetevents.protocol.world.states.enums.Axis axis;
            switch(clicked){
                case EAST:case WEST:axis=com.github.retrooper.packetevents.protocol.world.states.enums.Axis.X;break;
                case NORTH:case SOUTH:axis=com.github.retrooper.packetevents.protocol.world.states.enums.Axis.Z;break;
                default:axis=com.github.retrooper.packetevents.protocol.world.states.enums.Axis.Y;
            }
            state.setAxis(axis);
        }
        if(state.hasProperty(com.github.retrooper.packetevents.protocol.world.states.type.StateValue.FACING)){
            state.setFacing(playerFacing);
        }
        if(state.hasProperty(com.github.retrooper.packetevents.protocol.world.states.type.StateValue.HALF)){
            com.github.retrooper.packetevents.protocol.world.states.enums.Half current=state.getHalf();
            if(current==com.github.retrooper.packetevents.protocol.world.states.enums.Half.TOP
                    ||current==com.github.retrooper.packetevents.protocol.world.states.enums.Half.BOTTOM){
                state.setHalf(clicked==com.github.retrooper.packetevents.protocol.world.BlockFace.DOWN
                        ?com.github.retrooper.packetevents.protocol.world.states.enums.Half.TOP
                        :com.github.retrooper.packetevents.protocol.world.states.enums.Half.BOTTOM);
            }
        }
        if(state.hasProperty(com.github.retrooper.packetevents.protocol.world.states.type.StateValue.ROTATION)){
            int rotation=Math.floorMod((int)Math.floor((player.getYaw()+180.0f)*16.0f/360.0f+0.5f),16);
            state.setRotation(rotation);
        }
        return state;
    }

    private static com.github.retrooper.packetevents.protocol.world.BlockFace horizontalFacing(float yaw){
        int direction=Math.floorMod((int)Math.floor(yaw/90.0f+0.5f),4);
        com.github.retrooper.packetevents.protocol.world.BlockFace looking;
        switch(direction){
            case 1:looking=com.github.retrooper.packetevents.protocol.world.BlockFace.WEST;break;
            case 2:looking=com.github.retrooper.packetevents.protocol.world.BlockFace.NORTH;break;
            case 3:looking=com.github.retrooper.packetevents.protocol.world.BlockFace.EAST;break;
            default:looking=com.github.retrooper.packetevents.protocol.world.BlockFace.SOUTH;
        }
        return looking.getOppositeFace();
    }

}
