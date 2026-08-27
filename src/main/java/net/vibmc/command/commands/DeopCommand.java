package net.vibmc.command.commands;

import net.vibmc.command.Command;
import net.vibmc.command.CommandSender;
import net.vibmc.entity.ServerPlayer;
import net.vibmc.network.JsonText;
import net.vibmc.server.VibMC;

import java.io.IOException;

public final class DeopCommand extends Command {
    public DeopCommand() { super("deop", "Revoke operator status", "/deop <player>", "vibmc.command.op"); }
    @Override public boolean execute(CommandSender sender, String[] args) {
        if (args.length != 1) { sender.sendMessage(JsonText.component("§cUsage: /deop <player>")); return false; }
        ServerPlayer player=VibMC.getInstance().getPlayerManager().getPlayer(args[0]);
        if(player==null){sender.sendMessage(JsonText.component("§cPlayer must be online."));return false;}
        try { VibMC.getInstance().getOperatorManager().remove(player); }
        catch(IOException e){sender.sendMessage(JsonText.component("§cCould not save ops.json: "+e.getMessage()));return false;}
        sender.sendMessage(JsonText.component("§aRemoved operator status from "+player.getUsername()+"."));
        return true;
    }
}
