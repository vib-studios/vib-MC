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
        } else if (event.getPacketType() == PacketType.Play.Client.CREATIVE_INVENTORY_ACTION) {
            WrapperPlayClientCreativeInventoryAction wrapper = new WrapperPlayClientCreativeInventoryAction(event);
            int slot = wrapper.getSlot();
            ItemStack stack = wrapper.getItemStack();
            submit(player, () -> {
                if (player.getGameModeEnum() == GameMode.CREATIVE && slot >= 36 && slot <= 44) {
                    player.getInventory().setSlot(slot - 36, stack);
                }
            });
            event.setCancelled(true);
        } else if (event.getPacketType() == PacketType.Play.Client.CHAT_COMMAND) {
            // 1.19 split slash commands out of the ordinary chat packet. The command field
            // excludes the leading slash, while vib-MC's command manager expects it.
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

}
