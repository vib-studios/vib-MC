package net.vibmc.command.commands;

import net.vibmc.command.Command;
import net.vibmc.command.CommandSender;
import net.vibmc.server.VibMC;

public class SaveAllCommand extends Command {
    public SaveAllCommand() {
        super("save-all", "Save the world", "/save-all", "vibmc.command.save");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        int saved = VibMC.getInstance().getWorldManager().saveAll();
        int players = VibMC.getInstance().getPlayerManager().saveAllPlayers();
        if (saved == 0) {
            sender.sendMessage("{\"text\":\"§aSaved " + players + " player(s); no chunks had changed.\"}");
        } else {
            sender.sendMessage("{\"text\":\"§aSaved " + saved + " chunk(s) and " + players + " player(s).\"}");
        }
        return true;
    }
}
