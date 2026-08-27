package net.vibmc.network.packetevents;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import io.github.retrooper.packetevents.impl.netty.BuildData;
import io.github.retrooper.packetevents.impl.netty.factory.NettyPacketEventsBuilder;
import io.github.retrooper.packetevents.impl.netty.manager.player.PlayerManagerAbstract;
import io.github.retrooper.packetevents.impl.netty.manager.server.ServerManagerAbstract;
import io.netty.channel.Channel;
import net.vibmc.entity.ServerPlayer;

public final class PacketEventsRuntime {
    private static volatile boolean initialized;
    private PacketEventsRuntime(){}

    public static synchronized void initialize(){
        if(initialized)return;
        ServerManagerAbstract server=new ServerManagerAbstract(){public ServerVersion getVersion(){return ServerVersion.V_1_12_2;}};
        PlayerManagerAbstract players=new PlayerManagerAbstract(){public int getPing(Object player){return 0;}public Object getChannel(Object player){if(player instanceof ServerPlayer)return ((ServerPlayer)player).getUser().getChannel();return player instanceof Channel?player:null;}};
        io.github.retrooper.packetevents.impl.netty.manager.protocol.ProtocolManagerAbstract protocols=
                new io.github.retrooper.packetevents.impl.netty.manager.protocol.ProtocolManagerAbstract(){
                    public com.github.retrooper.packetevents.protocol.ProtocolVersion getPlatformVersion(){
                        return com.github.retrooper.packetevents.protocol.ProtocolVersion.UNKNOWN;
                    }
                };
        PacketEvents.setAPI(NettyPacketEventsBuilder.build(new BuildData("vib-mc"),new VibChannelInjector(),protocols,server,players));
        PacketEvents.getAPI().getSettings().checkForUpdates(false);
        PacketEvents.getAPI().load();
        PacketEvents.getAPI().getEventManager().registerListener(new VibLifecyclePacketListener(), com.github.retrooper.packetevents.event.PacketListenerPriority.LOWEST);
        PacketEvents.getAPI().getEventManager().registerListener(new VibGameplayPacketListener(), com.github.retrooper.packetevents.event.PacketListenerPriority.NORMAL);
        PacketEvents.getAPI().init();
        // PacketType's lazy legacy tables are not safe to initialize concurrently
        // from two first-time logins on separate Netty event loops.
        com.github.retrooper.packetevents.protocol.packettype.PacketType.prepare();
        initialized=true;
    }

    public static synchronized void terminate(){if(!initialized)return;PacketEvents.getAPI().terminate();initialized=false;}
    public static boolean isInitialized(){return initialized;}
}
