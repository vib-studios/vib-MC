package net.vibmc.network.packetevents;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import net.vibmc.entity.ServerPlayer;
import net.vibmc.player.GameMode;
import net.vibmc.server.VibMC;
import net.vibmc.world.BlockInteractionService;

/** Decodes packets on Netty and applies all gameplay state changes on the server tick thread. */
public final class VibGameplayPacketListener implements PacketListener {
    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer)) return;
        ServerPlayer player = (ServerPlayer) event.getPlayer();

        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
            Location location = wrapper.getLocation();
            boolean positionChanged = wrapper.hasPositionChanged();
            boolean rotationChanged = wrapper.hasRotationChanged();
            boolean onGround = wrapper.isOnGround();
            double x = location.getX(), y = location.getY(), z = location.getZ();
            float yaw = location.getYaw(), pitch = location.getPitch();
            submit(player, () -> {
                if(player.isAwaitingTeleportConfirmation())return;
                if (positionChanged && rotationChanged) player.setPositionAndRotation(x, y, z, yaw, pitch);
                else if (positionChanged) player.setPosition(x, y, z);
                else if (rotationChanged) player.setRotation(yaw, pitch);
                player.setOnGround(onGround);
                player.handleClientMovement(onGround);
                VibMC.getInstance().getPlayerManager().broadcastPlayerPosition(player);
            });
            event.setCancelled(true);
        } else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity wrapper=new WrapperPlayClientInteractEntity(event);
            int entityId=wrapper.getEntityId();String action=wrapper.getAction().name();
            if("ATTACK".equals(action))submit(player,()->{
                if(player.getGameModeEnum()==GameMode.SPECTATOR)
                    VibMC.getInstance().getPlayerManager().handleSpectatorInteraction(player,entityId);
                else VibMC.getInstance().getPlayerManager().handleAttack(player,entityId);
            });
            event.setCancelled(true);
        } else if (event.getPacketType() == PacketType.Play.Client.SPECTATE) {
            java.util.UUID target=new WrapperPlayClientSpectate(event).getTargetUUID();
            submit(player,()->VibMC.getInstance().getPlayerManager().handleSpectateTeleport(player,target));
            event.setCancelled(true);
        } else if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION) {
            String action=new WrapperPlayClientEntityAction(event).getAction().name();
            if("START_SNEAKING".equals(action))submit(player,()->{player.setSneaking(true);player.resetSpectatorCamera(true);});
            else if("STOP_SNEAKING".equals(action))submit(player,()->player.setSneaking(false));
            else if("START_SPRINTING".equals(action))submit(player,()->player.setSprinting(true));
            else if("STOP_SPRINTING".equals(action))submit(player,()->player.setSprinting(false));
            event.setCancelled(true);
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_ABILITIES) {
            boolean flying = new WrapperPlayClientPlayerAbilities(event).isFlying();
            submit(player, () -> player.setFlying(player.isAllowFlight() && flying));
            event.setCancelled(true);
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            WrapperPlayClientPlayerBlockPlacement wrapper = new WrapperPlayClientPlayerBlockPlacement(event);
            Vector3i position = wrapper.getBlockPosition();
            int x = position.getX(), y = position.getY(), z = position.getZ(), face = wrapper.getFaceId();
            int sequence=wrapper.getSequence();
            submit(player, () -> {
                BlockInteractionService.place(player, x, y, z, face);
                player.acknowledgeBlockChange(sequence);
            });
            event.setCancelled(true);
        } else if (event.getPacketType() == PacketType.Play.Client.CLIENT_STATUS) {
            WrapperPlayClientClientStatus.Action action = new WrapperPlayClientClientStatus(event).getAction();
            if (action == WrapperPlayClientClientStatus.Action.PERFORM_RESPAWN) {
                submit(player, () -> VibMC.getInstance().getPlayerManager().respawnPlayer(player));
            }
            event.setCancelled(true);
        } else if (event.getPacketType() == PacketType.Play.Client.PICK_ITEM) {
            WrapperPlayClientPickItem wrapper = new WrapperPlayClientPickItem(event);
            int slot = wrapper.getSlot();
            submit(player, () -> handlePickItem(player, slot));
            event.setCancelled(true);
        } else if (event.getPacketType() == PacketType.Play.Client.PICK_ITEM_FROM_BLOCK) {
            WrapperPlayClientPickItemFromBlock wrapper = new WrapperPlayClientPickItemFromBlock(event);
            Vector3i pos = wrapper.getBlockPos();
            boolean includeData = wrapper.isIncludeData();
            submit(player, () -> handlePickItemFromBlock(player, pos, includeData));
            event.setCancelled(true);
        } else if (event.getPacketType() == PacketType.Play.Client.PICK_ITEM_FROM_ENTITY) {
            WrapperPlayClientPickItemFromEntity wrapper = new WrapperPlayClientPickItemFromEntity(event);
            int entityId = wrapper.getEntityId();
            boolean includeData = wrapper.isIncludeData();
            submit(player, () -> handlePickItemFromEntity(player, entityId, includeData));
            event.setCancelled(true);
        } else if (event.getPacketType() == PacketType.Play.Client.CHANGE_GAME_MODE) {
            WrapperPlayClientChangeGameMode wrapper = new WrapperPlayClientChangeGameMode(event);
            com.github.retrooper.packetevents.protocol.player.GameMode peMode = wrapper.getGameMode();
            submit(player, () -> handleChangeGameMode(player, peMode));
            event.setCancelled(true);
        } else if (event.getPacketType() == PacketType.Play.Client.CREATIVE_INVENTORY_ACTION) {
            WrapperPlayClientCreativeInventoryAction wrapper = new WrapperPlayClientCreativeInventoryAction(event);
            int slot = wrapper.getSlot();
            ItemStack stack = wrapper.getItemStack();
            submit(player, () -> {
                if (player.getGameModeEnum() == GameMode.CREATIVE && slot >= 36 && slot <= 44) {
                    player.getInventory().setSlot(slot - 36, stack);
                    player.sendInventory();
                }
            });
            event.setCancelled(true);
        } else if (event.getPacketType() == PacketType.Play.Client.CHAT_COMMAND) {
            String command = new WrapperPlayClientChatCommand(event).getCommand();
            submit(player, () -> VibMC.getInstance().getPlayerManager().handleChat(player, "/" + command));
            event.setCancelled(true);
        } else if (event.getPacketType() == PacketType.Play.Client.CHAT_COMMAND_UNSIGNED) {
            String command = new WrapperPlayClientChatCommandUnsigned(event).getCommand();
            submit(player, () -> VibMC.getInstance().getPlayerManager().handleChat(player, "/" + command));
            event.setCancelled(true);
        } else if (event.getPacketType() == PacketType.Play.Client.CHAT_MESSAGE) {
            String message = new WrapperPlayClientChatMessage(event).getMessage();
            submit(player, () -> VibMC.getInstance().getPlayerManager().handleChat(player, message));
            event.setCancelled(true);
        } else if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            int slot = new WrapperPlayClientHeldItemChange(event).getSlot();
            submit(player, () -> {player.setHeldItemSlot(slot);player.broadcastEquipment();});
            event.setCancelled(true);
        } else if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            WrapperPlayClientClickWindow wrapper = new WrapperPlayClientClickWindow(event);
            int windowId = wrapper.getWindowId(), slot = wrapper.getSlot(), button = wrapper.getButton();
            WrapperPlayClientClickWindow.WindowClickType clickType = wrapper.getWindowClickType();
            int action = wrapper.getActionNumber().isPresent() ? wrapper.getActionNumber().get() : -1;
            submit(player, () -> net.vibmc.inventory.WindowService.click(player, windowId, slot, button, clickType, action));
            event.setCancelled(true);
        } else if (event.getPacketType() == PacketType.Play.Client.CLOSE_WINDOW) {
            submit(player, () -> net.vibmc.inventory.WindowService.close(player, false));
            event.setCancelled(true);
        } else if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
            submit(player, () -> net.vibmc.world.BlockInteractionService.useItem(player));
            event.setCancelled(true);
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging wrapper = new WrapperPlayClientPlayerDigging(event);
            Vector3i position = wrapper.getBlockPosition();
            String action = wrapper.getAction().name();
            int status = "FINISHED_DIGGING".equals(action) ? 2 : "START_DIGGING".equals(action) ? 0 : 1;
            int x = position.getX(), y = position.getY(), z = position.getZ(), sequence=wrapper.getSequence();
            submit(player, () -> {
                BlockInteractionService.dig(player, status, x, y, z);
                player.acknowledgeBlockChange(sequence);
            });
            event.setCancelled(true);
        } else if (event.getPacketType() == PacketType.Play.Client.TELEPORT_CONFIRM) {
            int teleportId=new WrapperPlayClientTeleportConfirm(event).getTeleportId();
            submit(player,()->player.confirmTeleport(teleportId));
            event.setCancelled(true);
        } else if (event.getPacketType() == PacketType.Play.Client.KEEP_ALIVE
                || event.getPacketType() == PacketType.Play.Client.CLIENT_SETTINGS
                || event.getPacketType() == PacketType.Play.Client.PLUGIN_MESSAGE) {
            event.setCancelled(true);
        }
    }

    private static void submit(ServerPlayer player, Runnable action) {
        VibMC server = VibMC.getInstance();
        boolean accepted = server.executeOnMainThread(() -> {
            if (!player.isOpen() || !player.isInWorld() || player.getUuid() == null
                    || server.getPlayerManager().getPlayer(player.getUuid()) != player) return;
            action.run();
        });
        if (!accepted) player.disconnect("Server overloaded: too many pending gameplay actions");
    }

    // --- Gamemode switcher (F3+F4) 1.21.6+ ---
    private static void handleChangeGameMode(ServerPlayer player, com.github.retrooper.packetevents.protocol.player.GameMode peMode) {
        if (peMode == null) return;
        // Map PacketEvents GameMode to vib-MC GameMode
        GameMode target;
        try {
            target = GameMode.byId(peMode.getId());
        } catch (Throwable t) {
            // Fallback by name
            try {
                target = GameMode.valueOf(peMode.name());
            } catch (Throwable t2) {
                return;
            }
        }
        // Allow all players to use F3+F4 because we send op level 4 to everyone.
        // The permission check for /gamemode command is separate; F3+F4 should work without op.
        player.setGameMode(target);
    }

    // --- Pick Block handling (vanilla PlayerInventory.addPickBlock) ---

    private static void handlePickItem(ServerPlayer player, int windowSlot) {
        int invSlot = windowSlotToInventorySlot(windowSlot);
        if (invSlot < 0 || invSlot >= player.getInventory().getSize()) return;
        ItemStack source = player.getInventory().getSlot(invSlot);
        if (source.isEmpty()) return;
        if (isValidHotbarIndex(invSlot)) {
            player.setHeldItemSlot(invSlot);
            sendHeldItemChange(player, invSlot);
            player.sendInventory();
            player.broadcastEquipment();
            return;
        }
        swapSlotWithHotbar(player, invSlot);
        player.sendInventory();
        player.broadcastEquipment();
    }

    private static void handlePickItemFromBlock(ServerPlayer player, Vector3i pos, boolean includeData) {
        if (pos == null) return;
        net.vibmc.world.World world = player.getWorld();
        if (world == null) return;
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        if (y < 0 || y >= 256) return;
        com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState block = world.getBlockAt(x, y, z);
        if (block == null || net.vibmc.world.Blocks.same(block, net.vibmc.world.Blocks.AIR)) return;
        ItemStack picked = blockToItem(block, player.getUser().getClientVersion());
        if (picked.isEmpty()) return;

        boolean creative = player.getGameModeEnum() == GameMode.CREATIVE;
        int found = getSlotWithStack(player, picked);

        if (isValidHotbarIndex(found)) {
            player.setHeldItemSlot(found);
            sendHeldItemChange(player, found);
            player.sendInventory();
            player.broadcastEquipment();
            return;
        }

        if (found == -1) {
            if (!creative) {
                return;
            }
            int swappable = getSwappableHotbarSlot(player);
            ItemStack current = player.getInventory().getSlot(swappable);
            if (!current.isEmpty()) {
                int empty = getEmptySlot(player);
                if (empty != -1 && empty != swappable) {
                    player.getInventory().setSlot(empty, current);
                }
            }
            player.getInventory().setSlot(swappable, picked);
            player.setHeldItemSlot(swappable);
            sendHeldItemChange(player, swappable);
            player.sendInventory();
            player.broadcastEquipment();
            return;
        } else {
            int swappable = getSwappableHotbarSlot(player);
            ItemStack swappableStack = player.getInventory().getSlot(swappable);
            ItemStack slotStack = player.getInventory().getSlot(found);
            player.getInventory().setSlot(swappable, slotStack);
            player.getInventory().setSlot(found, swappableStack);
            player.setHeldItemSlot(swappable);
            sendHeldItemChange(player, swappable);
            player.sendInventory();
            player.broadcastEquipment();
        }
    }

    private static void handlePickItemFromEntity(ServerPlayer player, int entityId, boolean includeData) {
        // Not implemented – could give spawn egg in creative
    }

    // --- Vanilla inventory helpers ---

    private static int windowSlotToInventorySlot(int windowSlot) {
        if (windowSlot >= 36 && windowSlot <= 44) return windowSlot - 36;
        if (windowSlot >= 9 && windowSlot <= 35) return windowSlot;
        if (windowSlot >= 0 && windowSlot <= 8) return windowSlot;
        return -1;
    }

    private static boolean isValidHotbarIndex(int slot) {
        return slot >= 0 && slot < 9;
    }

    private static int getEmptySlot(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (player.getInventory().getSlot(i).isEmpty()) return i;
        }
        return -1;
    }

    private static int getSlotWithStack(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack other = player.getInventory().getSlot(i);
            if (other.isEmpty()) continue;
            if (other.getType() == stack.getType()) {
                return i;
            }
        }
        return -1;
    }

    private static int getSwappableHotbarSlot(ServerPlayer player) {
        int selected = player.getHeldItemSlot();
        for (int offset = 0; offset < 9; offset++) {
            int slot = (selected + offset) % 9;
            if (player.getInventory().getSlot(slot).isEmpty()) return slot;
        }
        for (int offset = 0; offset < 9; offset++) {
            int slot = (selected + offset) % 9;
            ItemStack stack = player.getInventory().getSlot(slot);
            if (!stack.isEmpty() && !isEnchanted(stack)) return slot;
        }
        return selected;
    }

    private static void swapSlotWithHotbar(ServerPlayer player, int slot) {
        int swappable = getSwappableHotbarSlot(player);
        ItemStack swappableStack = player.getInventory().getSlot(swappable);
        ItemStack slotStack = player.getInventory().getSlot(slot);
        player.getInventory().setSlot(swappable, slotStack);
        player.getInventory().setSlot(slot, swappableStack);
        player.setHeldItemSlot(swappable);
        sendHeldItemChange(player, swappable);
    }

    private static void sendHeldItemChange(ServerPlayer player, int slot) {
        try {
            player.getUser().sendPacket(
                    new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerHeldItemChange(slot));
        } catch (Throwable ignored) {
        }
    }

    private static boolean isEnchanted(ItemStack stack) {
        try {
            if (stack.getNBT() != null) {
                return stack.getNBT().getTags().containsKey("Enchantments") || stack.getNBT().getTags().containsKey("ench");
            }
            return stack.getComponent(com.github.retrooper.packetevents.protocol.component.ComponentTypes.ENCHANTMENTS).isPresent()
                    && !stack.getComponent(com.github.retrooper.packetevents.protocol.component.ComponentTypes.ENCHANTMENTS).get().isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static ItemStack blockToItem(com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState block,
                                         com.github.retrooper.packetevents.protocol.player.ClientVersion version) {
        if (block == null) return ItemStack.EMPTY;
        com.github.retrooper.packetevents.protocol.world.states.type.StateType type = block.getType();
        if (type == null) return ItemStack.EMPTY;
        try {
            com.github.retrooper.packetevents.protocol.item.type.ItemType itemType =
                    com.github.retrooper.packetevents.protocol.item.type.ItemTypes.getTypePlacingState(type);
            if (itemType != null) {
                return ItemStack.builder().type(itemType).amount(1)
                        .version(com.github.retrooper.packetevents.protocol.player.ClientVersion.getLatest()).build();
            }
        } catch (Throwable ignored) {
        }
        try {
            String name = type.getName().toString();
            com.github.retrooper.packetevents.protocol.item.type.ItemType byName =
                    com.github.retrooper.packetevents.protocol.item.type.ItemTypes.getByName(name);
            if (byName != null) {
                return ItemStack.builder().type(byName).amount(1)
                        .version(com.github.retrooper.packetevents.protocol.player.ClientVersion.getLatest()).build();
            }
        } catch (Throwable ignored) {
        }
        try {
            if (type == com.github.retrooper.packetevents.protocol.world.states.type.StateTypes.WATER) {
                com.github.retrooper.packetevents.protocol.item.type.ItemType waterBucket =
                        com.github.retrooper.packetevents.protocol.item.type.ItemTypes.getByName("minecraft:water_bucket");
                if (waterBucket != null) return ItemStack.builder().type(waterBucket).amount(1)
                        .version(com.github.retrooper.packetevents.protocol.player.ClientVersion.getLatest()).build();
            }
            if (type == com.github.retrooper.packetevents.protocol.world.states.type.StateTypes.LAVA) {
                com.github.retrooper.packetevents.protocol.item.type.ItemType lavaBucket =
                        com.github.retrooper.packetevents.protocol.item.type.ItemTypes.getByName("minecraft:lava_bucket");
                if (lavaBucket != null) return ItemStack.builder().type(lavaBucket).amount(1)
                        .version(com.github.retrooper.packetevents.protocol.player.ClientVersion.getLatest()).build();
            }
        } catch (Throwable ignored) {
        }
        return ItemStack.EMPTY;
    }
}
