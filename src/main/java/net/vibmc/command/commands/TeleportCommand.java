package net.vibmc.command.commands;

import net.vibmc.command.Command;
import net.vibmc.command.CommandSender;
import net.vibmc.entity.ServerPlayer;
import net.vibmc.server.VibMC;

public class TeleportCommand extends Command {
    public TeleportCommand() {
        super("tp", "Teleport to a player or coordinates", "/tp <player> | /tp <x> <y> <z>", "vibmc.command.tp");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 1) {
            ServerPlayer target = VibMC.getInstance().getPlayerManager().getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage("{\"text\":\"§cPlayer not found.\"}");
                return false;
            }
            if (!sender.isPlayer()) {
                sender.sendMessage("{\"text\":\"§cOnly players can use this form.\"}");
                return false;
            }
            sender.getPlayer().teleport(target.getX(), target.getY(), target.getZ());
            sender.sendMessage("{\"text\":\"§aTeleported to " + target.getUsername() + ".\"}");
            return true;
        }
        if (args.length == 3) {
            if (!sender.isPlayer()) {
                sender.sendMessage("{\"text\":\"§cOnly players can use this form.\"}");
                return false;
            }
            try {
                double x = Double.parseDouble(args[0]);
                double y = Double.parseDouble(args[1]);
                double z = Double.parseDouble(args[2]);
                sender.getPlayer().teleport(x, y, z);
                sender.sendMessage("{\"text\":\"§aTeleported.\"}");
                return true;
            } catch (NumberFormatException e) {
                sender.sendMessage("{\"text\":\"§cInvalid coordinates.\"}");
                return false;
            }
        }
        sender.sendMessage("{\"text\":\"§cUsage: /tp <player> | /tp <x> <y> <z>\"}");
        return false;
    }
}
