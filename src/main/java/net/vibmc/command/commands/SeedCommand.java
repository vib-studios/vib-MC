package net.vibmc.command.commands;

import net.vibmc.command.Command;
import net.vibmc.command.CommandSender;
import net.vibmc.server.VibMC;

public class SeedCommand extends Command {
    public SeedCommand() {
        super("seed", "Show the world seed", "/seed", "vibmc.command.seed");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        long seed = VibMC.getInstance().getWorldManager().getMainWorld().seed();
        sender.sendMessage("{\"text\":\"§aSeed: " + seed + "\"}");
        return true;
    }
}
