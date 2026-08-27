package net.vibmc.command.commands;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import net.vibmc.command.Command;
import net.vibmc.command.CommandSender;
import net.vibmc.entity.ServerPlayer;
import net.vibmc.network.JsonText;
import net.vibmc.server.VibMC;

import java.util.Locale;

public final class GiveCommand extends Command {
    public GiveCommand(){super("give","Give an item to a player","/give <player> <item> [amount]","vibmc.command.give");}
    @Override public boolean execute(CommandSender sender,String[] args){
        if(args.length<2){sender.sendMessage(JsonText.component("§cUsage: /give <player> <item> [amount]"));return false;}
        ServerPlayer target=VibMC.getInstance().getPlayerManager().getPlayer(args[0]);if(target==null){sender.sendMessage(JsonText.component("§cPlayer not found."));return false;}
        String name=args[1].toLowerCase(Locale.ROOT);if(!name.contains(":"))name="minecraft:"+name;
        ItemType type=ItemTypes.getByName(name);
        if(type==null||type==ItemTypes.AIR){sender.sendMessage(JsonText.component("§cUnknown item: "+args[1]));return false;}
        int amount=1;if(args.length>=3)try{amount=Integer.parseInt(args[2]);}catch(NumberFormatException e){sender.sendMessage(JsonText.component("§cInvalid amount."));return false;}
        if (amount < 1 || amount > type.getMaxAmount()) { sender.sendMessage(JsonText.component("§cAmount must be between 1 and " + type.getMaxAmount() + ".")); return false; }
        target.addItem(ItemStack.builder().type(type).amount(amount).build());
        sender.sendMessage(JsonText.component("§aGave "+amount+" "+type.getName()+" to "+target.getUsername()+"."));return true;
    }
}
