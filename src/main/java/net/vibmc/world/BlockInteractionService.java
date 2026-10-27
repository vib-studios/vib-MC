package net.vibmc.world;

import net.vibmc.entity.ServerPlayer;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import net.vibmc.player.GameMode;
import net.vibmc.server.VibMC;

import java.util.List;
import java.util.Random;

/** Applies client block actions to authoritative world/chunk state. */
public final class BlockInteractionService {
    private static final int[][] FACE_OFFSETS = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };

    /** Shared source for variable drops; determinism is not needed for loot. */
    private static final Random RANDOM = new Random();

    private BlockInteractionService() {}

    /**
     * Right-click with no block targeted. Only eating is implemented: the client plays its own
     * use animation, and the server applies the whole food value at once.
     */
    public static void useItem(ServerPlayer player) {
        if (player.getGameModeEnum() == GameMode.SPECTATOR) return;
        ItemStack held = player.getInventory().getSlot(player.getHeldItemSlot());
        if (!net.vibmc.inventory.Foods.isFood(held)) return;
        if (player.getFoodLevel() >= 20 && player.getGameModeEnum() != GameMode.CREATIVE) return;
        player.setFoodLevel(player.getFoodLevel() + net.vibmc.inventory.Foods.nutrition(held));
        player.setFoodSaturation(player.getFoodSaturation() + net.vibmc.inventory.Foods.saturation(held));
        player.sendHealth();
        if (player.getGameModeEnum() != GameMode.CREATIVE) {
            player.getInventory().removeItem(player.getHeldItemSlot(), 1);
            player.sendInventory();
        }
        Effects.sound(player.getWorld(), player.getX(), player.getY(), player.getZ(),
                com.github.retrooper.packetevents.protocol.sound.Sounds.ENTITY_PLAYER_BURP,
                com.github.retrooper.packetevents.protocol.sound.SoundCategory.PLAYER, 0.5f, 1.0f);
    }

    /**
     * Block packets carry whatever coordinates the client likes. Without this a modified client
     * could break, place or open containers anywhere in the world, and every probe of an
     * unvisited position would generate and keep a chunk. Measured from the eyes to the block
     * centre, with slack over the vanilla reach so lag at the edge is not rejected.
     */
    static boolean withinReach(ServerPlayer player, int x, int y, int z) {
        if (y < 0 || y >= 256) return false;
        double limit = player.getGameModeEnum() == GameMode.CREATIVE ? 7.0 : 6.0;
        double dx = x + 0.5 - player.getX();
        double dy = y + 0.5 - (player.getY() + 1.62);
        double dz = z + 0.5 - player.getZ();
        return dx * dx + dy * dy + dz * dz <= limit * limit;
    }

    /** Spectators and adventure players cannot change blocks, as in vanilla. */
    private static boolean canModifyBlocks(ServerPlayer player) {
        GameMode mode = player.getGameModeEnum();
        return mode != GameMode.SPECTATOR && mode != GameMode.ADVENTURE;
    }

    public static void dig(ServerPlayer player, int status, int x, int y, int z) {
        if (!canModifyBlocks(player) || !withinReach(player, x, y, z)) return;
        World world = player.getWorld();
        WrappedBlockState existing = world.getBlockAt(x, y, z);
        boolean complete = status == 2 || (status == 0
                && (player.getGameModeEnum() == GameMode.CREATIVE || Blocks.same(existing,Blocks.FIRE)));
        if (!complete) return;
        if (Blocks.same(existing,Blocks.AIR) || Blocks.same(existing,Blocks.BEDROCK)) return;
        if (!world.setBlockAndUpdate(x, y, z, Blocks.AIR)) return;
        Effects.blockBreak(world, x, y, z, existing);
        // Anyone still looking into this container would otherwise keep a live handle to its
        // inventory and could take the contents out again after the breaker received them.
        closeViewers(world, x, y, z);
        // Breaking a container spills nothing (there are no item entities), so hand the
        // contents to the breaker rather than destroying them.
        net.vibmc.inventory.Inventory container = world.blockEntities().remove(x, y, z);
        if (container != null) {
            for (ItemStack stored : container.getSlots()) {
                if (!stored.isEmpty()) player.getInventory().addItem(stored);
            }
            container.clear();
        }
        if (player.getGameModeEnum() == GameMode.CREATIVE) { player.sendInventory(); return; }
        ItemStack held = player.getInventory().getSlot(player.getHeldItemSlot());
        ItemStack drop = BlockDrops.drop(existing, held, RANDOM);
        if (!drop.isEmpty()) player.getInventory().addItem(drop);
        damageTool(player, held);
        player.addExhaustion(0.005f);
        player.sendInventory();
    }

    /** Tools lose a point of durability per block and break when they run out. */
    private static void damageTool(ServerPlayer player, ItemStack held) {
        if (held == null || held.isEmpty() || held.getMaxDamage() <= 0) return;
        if (BlockDrops.heldTier(held) == BlockDrops.Tier.NONE
                && net.vibmc.inventory.Weapons.attackDamage(held) <= 1.0f) return;
        held.setDamageValue(held.getDamageValue() + 1);
        if (held.getDamageValue() >= held.getMaxDamage()) {
            player.getInventory().setSlot(player.getHeldItemSlot(), ItemStack.EMPTY);
            player.broadcastEquipment();
            return;
        }
        player.getInventory().setSlot(player.getHeldItemSlot(), held);
    }

    /** Closes the window of every player who has the container at this position open. */
    private static void closeViewers(World world, int x, int y, int z) {
        VibMC server = VibMC.getInstance();
        if (server == null) return;
        long position = net.vibmc.world.BlockEntities.pack(x, y, z);
        for (ServerPlayer viewer : server.getPlayerManager().getOnlinePlayers()) {
            net.vibmc.inventory.WindowSession window = viewer.getOpenWindow();
            if (window == null || viewer.getWorld() != world || window.blockPosition() != position) continue;
            net.vibmc.inventory.WindowService.close(viewer, true);
        }
    }

    public static void place(ServerPlayer player, int clickedX, int clickedY, int clickedZ, int face) {
        World world = player.getWorld();
        // 1.8-1.12.2 have no Use Item packet: right-clicking the air arrives here with an
        // out-of-range face, which is how eating reaches the server on those versions.
        boolean airClick = face < 0 || face >= FACE_OFFSETS.length;
        if (!airClick && !withinReach(player, clickedX, clickedY, clickedZ)) return;
        if (!airClick && openContainer(player, world, clickedX, clickedY, clickedZ)) return;
        ItemStack held = player.getInventory().getSlot(player.getHeldItemSlot());
        if (held.isEmpty()) return;
        if (airClick) { useItem(player); return; }
        if (!canModifyBlocks(player)) return;
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
        // 1.8-1.12.2 have no Use Item packet: right-clicking the air arrives here with an
        // out-of-range face, which is how eating reaches the server on those versions.
        if (face < 0 || face >= FACE_OFFSETS.length) { useItem(player); return; }
        WrappedBlockState bucket = bucketFluid(held);
        if (bucket != null) {
            placeBucket(player, world, bucket, clickedX, clickedY, clickedZ, face);
            return;
        }
        WrappedBlockState block = blockFor(held);
        if (Blocks.same(block,Blocks.AIR)) return;
        block = orientForPlacement(block, player, face);
        int[] offset = FACE_OFFSETS[face];
        int x = clickedX + offset[0];
        int y = clickedY + offset[1];
        int z = clickedZ + offset[2];
        if (y < 0 || y >= 256) return;
        int playerX=(int)Math.floor(player.getX()),playerZ=(int)Math.floor(player.getZ());
        int feetY=(int)Math.floor(player.getY());
        if(x==playerX&&z==playerZ&&(y==feetY||y==feetY+1))return;
        WrappedBlockState replaced = world.getBlockAt(x, y, z);
        if (!Blocks.same(replaced,Blocks.AIR) && !Blocks.same(replaced,Blocks.WATER) && !Blocks.same(replaced,Blocks.LAVA)) return;
        if (world.setBlockAndUpdate(x, y, z, block)) {
            Effects.blockPlace(world, x, y, z, block);
            if (player.getGameModeEnum() != GameMode.CREATIVE) {
                player.getInventory().removeItem(player.getHeldItemSlot(), 1);
                player.sendInventory();
            }
        }
    }

    /**
     * Buckets are not block items, so PacketEvents reports no placed type for them. Map the
     * two fluid buckets to the source block they create.
     */
    private static WrappedBlockState bucketFluid(ItemStack held) {
        if (held.getType() == ItemTypes.WATER_BUCKET) return Blocks.WATER;
        if (held.getType() == ItemTypes.LAVA_BUCKET) return Blocks.LAVA;
        return null;
    }

    private static void placeBucket(ServerPlayer player, World world, WrappedBlockState fluid,
                                    int clickedX, int clickedY, int clickedZ, int face) {
        int[] offset = FACE_OFFSETS[face];
        int x = clickedX + offset[0];
        int y = clickedY + offset[1];
        int z = clickedZ + offset[2];
        if (y < 0 || y >= 256) return;
        WrappedBlockState replaced = world.getBlockAt(x, y, z);
        if (!Blocks.isReplaceable(replaced) || Blocks.isFluid(replaced)) return;
        if (!world.setBlockAndUpdate(x, y, z, fluid)) return;
        Effects.blockPlace(world, x, y, z, fluid);
        if (player.getGameModeEnum() == GameMode.CREATIVE) return;
        player.getInventory().setSlot(player.getHeldItemSlot(),
                ItemStack.builder().type(ItemTypes.BUCKET).amount(1).build());
        player.sendInventory();
    }

    /** Right-clicking a chest, crafting table, or furnace opens its window. */
    private static boolean openContainer(ServerPlayer player, World world, int x, int y, int z) {
        if (player.getGameModeEnum() == GameMode.SPECTATOR) return false;
        WrappedBlockState clicked = world.getBlockAt(x, y, z);
        if (player.isSneaking() && !player.getInventory().getSlot(player.getHeldItemSlot()).isEmpty()) return false;
        net.vibmc.inventory.WindowSession session;
        if (Blocks.isType(clicked, com.github.retrooper.packetevents.protocol.world.states.type.StateTypes.CHEST)) {
            net.vibmc.inventory.Inventory contents = world.blockEntities()
                    .container(x, y, z, "Chest", net.vibmc.world.BlockEntities.CHEST_SIZE);
            session = new net.vibmc.inventory.WindowSession(player.nextWindowId(),
                    net.vibmc.inventory.WindowSession.Type.CHEST, contents, null,
                    net.vibmc.world.BlockEntities.pack(x, y, z));
            Effects.sound(world, x + 0.5, y + 0.5, z + 0.5,
                    com.github.retrooper.packetevents.protocol.sound.Sounds.BLOCK_CHEST_OPEN,
                    com.github.retrooper.packetevents.protocol.sound.SoundCategory.BLOCK, 0.5f, 1.0f);
        } else if (Blocks.isType(clicked, com.github.retrooper.packetevents.protocol.world.states.type.StateTypes.FURNACE)) {
            session = new net.vibmc.inventory.WindowSession(player.nextWindowId(),
                    net.vibmc.inventory.WindowSession.Type.FURNACE,
                    world.blockEntities().furnace(x, y, z).slots(), null,
                    net.vibmc.world.BlockEntities.pack(x, y, z));
        } else if (Blocks.isType(clicked, com.github.retrooper.packetevents.protocol.world.states.type.StateTypes.CRAFTING_TABLE)) {
            session = new net.vibmc.inventory.WindowSession(player.nextWindowId(),
                    net.vibmc.inventory.WindowSession.Type.CRAFTING_TABLE, null,
                    new net.vibmc.crafting.CraftingGrid(3), net.vibmc.world.BlockEntities.pack(x, y, z));
        } else {
            return false;
        }
        net.vibmc.inventory.WindowService.open(player, session);
        return true;
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
