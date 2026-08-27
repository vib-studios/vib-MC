package net.vibmc.command.commands;

import net.vibmc.command.Command;
import net.vibmc.command.CommandSender;
import net.vibmc.entity.ServerPlayer;
import net.vibmc.network.JsonText;
import net.vibmc.server.VibMC;

public final class KickCommand extends Command {
    public KickCommand(){super("kick","Disconnect a player","/kick <player> [reason]","vibmc.command.kick");}
    @Override public boolean execute(CommandSender sender,String[] args){
        if(args.length<1){sender.sendMessage(JsonText.component("§cUsage: /kick <player> [reason]"));return false;}
        ServerPlayer target=VibMC.getInstance().getPlayerManager().getPlayer(args[0]);
        if(target==null){sender.sendMessage(JsonText.component("§cNo player was found."));return false;}
        StringBuilder reason=new StringBuilder();for(int i=1;i<args.length;i++){if(reason.length()>0)reason.append(' ');reason.append(args[i]);}
        String message=reason.length()==0?"Kicked by an operator":reason.toString();
        target.disconnect(message);
        sender.sendMessage(JsonText.component("§aKicked "+target.getUsername()+": "+message));
        return true;
    }
}
