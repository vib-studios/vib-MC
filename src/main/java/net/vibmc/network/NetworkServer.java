package net.vibmc.network;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import net.vibmc.network.packetevents.codec.*;
import net.vibmc.entity.ServerPlayer;
import net.vibmc.server.VibMC;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class NetworkServer {
    private final Map<ChannelId,ServerPlayer> connections=new ConcurrentHashMap<>();
    private EventLoopGroup bossGroup,workerGroup;
    private Channel serverChannel;
    private volatile boolean running;

    public void start(String address,int port) throws java.io.IOException {
        if(running)throw new IllegalStateException("Network server already running");
        bossGroup=new NioEventLoopGroup(1);workerGroup=new NioEventLoopGroup();
        try{
            ServerBootstrap bootstrap=new ServerBootstrap();
            bootstrap.group(bossGroup,workerGroup).channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG,128).childOption(ChannelOption.TCP_NODELAY,true)
                    .childOption(ChannelOption.SO_KEEPALIVE,true)
                    .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,new WriteBufferWaterMark(8<<20,32<<20))
                    .childHandler(new ChannelInitializer<SocketChannel>(){protected void initChannel(SocketChannel channel){initializeChannel(channel);}});
            serverChannel=bootstrap.bind(new InetSocketAddress(address,port)).syncUninterruptibly().channel();running=true;
        }catch(RuntimeException error){shutdownGroups();throw error;}
    }

    private void initializeChannel(SocketChannel channel) {
        // PacketEvents' internal listener learns the client version and next state from handshake.
        User user=new User(channel,ConnectionState.HANDSHAKING,null,new UserProfile(null,null));
        // Register through PacketEvents' public lifecycle API so its internal listener can own
        // client-version detection, protocol state, profiles, and channel mappings.
        PacketEvents.getAPI().getProtocolManager().setUser(channel,user);
        ServerPlayer connection=new ServerPlayer(user);
        connection.setHandler(new net.vibmc.network.handler.HandshakeHandler());
        connections.put(channel.id(),connection);
        channel.closeFuture().addListener(future->remove(connection,"Connection closed"));

            channel.pipeline().addLast("packet_splitter",new PacketSplitter())
                    .addLast(PacketEvents.DECODER_NAME,new PacketEventsDecoder(user,connection))
                    .addLast("packet_formatter",new PacketFormatter())
                    .addLast(PacketEvents.ENCODER_NAME,new PacketEventsEncoder(user,connection));
    }

    private void remove(ServerPlayer connection,String reason){if(connections.remove(connection.channel().id(),connection)){if(connection.getHandler()!=null)try{connection.getHandler().onDisconnect(connection,reason);}catch(RuntimeException error){VibMC.getInstance().getLogger().warn("Disconnect handler failed: %s",error);}if(!(connection.getHandler() instanceof net.vibmc.network.handler.PlayHandler))PacketEvents.getAPI().getProtocolManager().removeUser(connection.channel());}connection.forceClose();}
    public void stop(){if(!running&&serverChannel==null)return;running=false;String message=VibMC.getInstance().getConfig().shutdownMessage();for(ServerPlayer c:new ArrayList<>(connections.values())){c.disconnect(message);remove(c,message);}connections.clear();if(serverChannel!=null){serverChannel.close().syncUninterruptibly();serverChannel=null;}shutdownGroups();}
    private void shutdownGroups(){if(workerGroup!=null){workerGroup.shutdownGracefully().syncUninterruptibly();workerGroup=null;}if(bossGroup!=null){bossGroup.shutdownGracefully().syncUninterruptibly();bossGroup=null;}}
    public void tick(){for(ServerPlayer c:connections.values())if(!c.isOpen())remove(c,"Connection closed");}
    public int getOnlineCount(){return connections.size();}
    public Collection<ServerPlayer> getConnections(){return Collections.unmodifiableCollection(new ArrayList<>(connections.values()));}
}
