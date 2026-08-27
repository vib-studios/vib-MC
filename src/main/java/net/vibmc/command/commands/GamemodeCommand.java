package net.vibmc.command.commands;

import net.vibmc.command.Command;
import net.vibmc.command.CommandSender;
import net.vibmc.entity.ServerPlayer;
import net.vibmc.player.GameMode;
import net.vibmc.server.VibMC;

import java.util.Locale;

public class GamemodeCommand extends Command {
    public GamemodeCommand() {
        super("gamemode", "Change your game mode", "/gamemode <mode> [player]", "vibmc.command.gamemode");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("{\"text\":\"§cUsage: /gamemode <mode> [player]\"}");
            return false;
        }
        GameMode mode = GameMode.fromName(args[0]);
        if (mode == null) {
            sender.sendMessage("{\"text\":\"§cInvalid game mode. Use survival, creative, adventure or spectator.\"}");
            return false;
        }
        ServerPlayer target;
        if (args.length >= 2) {
            target = VibMC.getInstance().getPlayerManager().getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("{\"text\":\"§cPlayer not found.\"}");
                return false;
            }
        } else if (sender.isPlayer()) {
            target = sender.getPlayer();
        } else {
            sender.sendMessage("{\"text\":\"§cUsage: /gamemode <mode> [player]\"}");
            return false;
        }
        target.setGameMode(mode);
        target.sendMessage("{\"text\":\"§aYour game mode was set to " + mode.name().toLowerCase(Locale.ROOT) + ".\"}");
        sender.sendMessage("{\"text\":\"§aSet " + target.getUsername() + "'s game mode to "
                + mode.name().toLowerCase(Locale.ROOT) + ".\"}");
        return true;
    }
}
