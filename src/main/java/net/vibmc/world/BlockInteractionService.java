package net.vibmc.world;

import net.vibmc.entity.ServerPlayer;
import net.vibmc.inventory.ItemStack;
import net.vibmc.inventory.ItemTypes;
import net.vibmc.mappings.Mappings;
import net.vibmc.player.GameMode;
import net.vibmc.server.VibMC;
import net.vibmc.world.block.BlockState;
import net.vibmc.world.block.BlockType;
import net.vibmc.world.block.StateValue;

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
                "entity.player.burp", Effects.Category.PLAYERS, 0.5f, 1.0f);
    }

    public static void dig(ServerPlayer player, int status, int x, int y, int z) {
        World world = player.getWorld();
        BlockState existing = world.getBlockAt(x, y, z);
        boolean complete = status == 2 || (status == 0
                && (player.getGameModeEnum() == GameMode.CREATIVE || Blocks.same(existing, Blocks.FIRE)));
        if (!complete) return;
        if (Blocks.same(existing, Blocks.AIR) || Blocks.same(existing, Blocks.BEDROCK)) return;
        if (!world.setBlockAndUpdate(x, y, z, Blocks.AIR)) return;
        Effects.blockBreak(world, x, y, z, existing);
        // Breaking a container spills nothing (there are no item entities), so hand the
        // contents to the breaker rather than destroying them.
        net.vibmc.inventory.Inventory container = world.blockEntities().remove(x, y, z);
        if (container != null) for (ItemStack stored : container.getSlots()) {
            if (!stored.isEmpty()) player.getInventory().addItem(stored);
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

    public static void place(ServerPlayer player, int clickedX, int clickedY, int clickedZ, int face) {
        World world = player.getWorld();
        if (openContainer(player, world, clickedX, clickedY, clickedZ)) return;
        ItemStack held = player.getInventory().getSlot(player.getHeldItemSlot());
        if (held.isEmpty()) return;
        if (held.getType() == ItemTypes.ENDER_EYE) {
            List<int[]> activated = PortalDetector.insertEyeAndActivate(world, clickedX, clickedY, clickedZ);
            broadcast(world, clickedX, clickedY, clickedZ, world.getBlockAt(clickedX, clickedY, clickedZ));
            for (int[] position : activated) broadcast(world, position[0], position[1], position[2], Blocks.END_PORTAL);
            if (player.getGameModeEnum() != GameMode.CREATIVE) { player.getInventory().removeItem(player.getHeldItemSlot(), 1); player.sendInventory(); }
            return;
        }
        if (held.getType() == ItemTypes.FLINT_AND_STEEL) {
            List<int[]> activated = PortalDetector.activateNear(world, clickedX, clickedY, clickedZ);
            for (int[] position : activated) {
                broadcast(world, position[0], position[1], position[2], Blocks.NETHER_PORTAL);
            }
            if (!activated.isEmpty() && player.getGameModeEnum() != GameMode.CREATIVE) {
                held.setDamageValue(held.getDamageValue() + 1);
                if (held.getMaxDamage() > 0 && held.getDamageValue() >= held.getMaxDamage()) held.setAmount(0);
                player.getInventory().setSlot(player.getHeldItemSlot(), held);
                player.sendInventory();
            }
            return;
        }
        // 1.8-1.12.2 have no Use Item packet: right-clicking the air arrives here with an
        // out-of-range face, which is how eating reaches the server on those versions.
        if (face < 0 || face >= FACE_OFFSETS.length) { useItem(player); return; }
        BlockState block = blockFor(held);
        if (Blocks.same(block, Blocks.AIR)) return;
        block = orientForPlacement(block, player, face);
        int[] offset = FACE_OFFSETS[face];
        int x = clickedX + offset[0];
        int y = clickedY + offset[1];
        int z = clickedZ + offset[2];
        int playerX = (int) Math.floor(player.getX()), playerZ = (int) Math.floor(player.getZ());
        int feetY = (int) Math.floor(player.getY());
        if (x == playerX && z == playerZ && (y == feetY || y == feetY + 1)) return;
        BlockState replaced = world.getBlockAt(x, y, z);
        if (!Blocks.same(replaced, Blocks.AIR) && !Blocks.same(replaced, Blocks.WATER)
                && !Blocks.same(replaced, Blocks.LAVA)) return;
        if (world.setBlockAndUpdate(x, y, z, block)) {
            Effects.blockPlace(world, x, y, z, block);
            if (player.getGameModeEnum() != GameMode.CREATIVE) {
                player.getInventory().removeItem(player.getHeldItemSlot(), 1);
                player.sendInventory();
            }
        }
    }

    /** Right-clicking a chest, crafting table, or furnace opens its window. */
    private static boolean openContainer(ServerPlayer player, World world, int x, int y, int z) {
        if (player.getGameModeEnum() == GameMode.SPECTATOR) return false;
        BlockState clicked = world.getBlockAt(x, y, z);
        if (player.isSneaking() && !player.getInventory().getSlot(player.getHeldItemSlot()).isEmpty()) return false;
        net.vibmc.inventory.WindowSession session;
        if (Blocks.isType(clicked, BlockType.of("chest"))) {
            net.vibmc.inventory.Inventory contents = world.blockEntities()
                    .container(x, y, z, "Chest", net.vibmc.world.BlockEntities.CHEST_SIZE);
            session = new net.vibmc.inventory.WindowSession(player.nextWindowId(),
                    net.vibmc.inventory.WindowSession.Type.CHEST, contents, null,
                    net.vibmc.world.BlockEntities.pack(x, y, z));
            Effects.sound(world, x + 0.5, y + 0.5, z + 0.5,
                    "block.chest.open", Effects.Category.BLOCKS, 0.5f, 1.0f);
        } else if (Blocks.isType(clicked, BlockType.of("furnace"))) {
            session = new net.vibmc.inventory.WindowSession(player.nextWindowId(),
                    net.vibmc.inventory.WindowSession.Type.FURNACE,
                    world.blockEntities().furnace(x, y, z).slots(), null,
                    net.vibmc.world.BlockEntities.pack(x, y, z));
        } else if (Blocks.isType(clicked, BlockType.of("crafting_table"))) {
            session = new net.vibmc.inventory.WindowSession(player.nextWindowId(),
                    net.vibmc.inventory.WindowSession.Type.CRAFTING_TABLE, null,
                    new net.vibmc.crafting.CraftingGrid(3), net.vibmc.world.BlockEntities.pack(x, y, z));
        } else {
            return false;
        }
        net.vibmc.inventory.WindowService.open(player, session);
        return true;
    }

    private static void broadcast(World world, int x, int y, int z, BlockState block) {
        VibMC server = VibMC.getInstance();
        if (server != null) server.getPlayerManager().broadcastBlockChange(world, x, y, z, block);
    }

    /** The block an item places, defaulting to its variant metadata when meaningful. */
    private static BlockState blockFor(ItemStack held) {
        if (held == null || held.isEmpty()) return Blocks.AIR;
        String name = held.getType().name();
        if (!Mappings.isBlock(name)) return Blocks.AIR;
        // The inventory may hold a variant like granite (stone damage 1) or spruce planks
        // (oak_planks damage 1); that metadata selects the placed block state. The stored
        // combined id resolves to the true block name for carpets, planks and logs alike.
        int variant = held.getDamageValue();
        if (variant > 0 && variant <= 15) {
            String resolved = Mappings.blockName((Mappings.blockId(name) & ~0xF) | variant);
            return BlockState.of(resolved);
        }
        return BlockState.of(name);
    }

    /** Applies the common vanilla orientation data bits a placed block carries. */
    private static BlockState orientForPlacement(BlockState original, ServerPlayer player, int faceId) {
        BlockState block = original.clone();
        if (block.hasProperty(StateValue.AXIS)) {
            int axisBits = faceId == 4 || faceId == 5 ? 0x4      // x
                    : faceId == 2 || faceId == 3 ? 0x8          // z
                    : 0x0;                                      // y
            block.setData((block.getData() & ~0xC) | axisBits);
        }
        if (block.hasProperty(StateValue.FACING)) {
            block.setFacing(blockFacing(player.getYaw()));
        }
        if (block.hasProperty(StateValue.HALF)) {
            int half = faceId == 0 ? 0x8 : 0x0;
            block.setData((block.getData() & ~0x8) | half);
        }
        if (block.hasProperty(StateValue.ROTATION)) {
            int rotation = Math.floorMod((int) Math.floor((player.getYaw() + 180.0f) * 16.0f / 360.0f + 0.5f), 16);
            block.setData(rotation & 0xF);
        }
        return block;
    }

    /** Which way the placed block faces, mirroring vanilla: toward the player. */
    private static net.vibmc.world.block.Facing blockFacing(float yaw) {
        int direction = Math.floorMod((int) Math.floor(yaw / 90.0f + 0.5f), 4);
        switch (direction) {
            case 1: return net.vibmc.world.block.Facing.EAST;    // looking west
            case 2: return net.vibmc.world.block.Facing.SOUTH;   // looking north
            case 3: return net.vibmc.world.block.Facing.WEST;    // looking east
            default: return net.vibmc.world.block.Facing.NORTH;  // looking south
        }
    }
}