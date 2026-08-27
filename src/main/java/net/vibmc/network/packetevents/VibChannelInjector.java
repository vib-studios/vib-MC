package net.vibmc.network.packetevents;
import com.github.retrooper.packetevents.PacketEvents;import com.github.retrooper.packetevents.injector.ChannelInjector;import com.github.retrooper.packetevents.protocol.player.User;import io.netty.channel.Channel;import net.vibmc.network.packetevents.codec.*;
public final class VibChannelInjector implements ChannelInjector {
 // PacketEvents currently uses this flag for two coupled facts: proxy topology and
 // per-connection wire versions. vib-MC is not a network proxy, but it does require the latter
 // so PacketWrapper resolves IDs/layout from User#getClientVersion instead of one global version.
 public boolean isProxy(){return true;}public boolean isServerBound(){return true;}public void inject(){}public void uninject(){}
 public void updateUser(Object raw,User user){Channel ch=(Channel)raw;PacketEventsDecoder d=(PacketEventsDecoder)ch.pipeline().get(PacketEvents.DECODER_NAME);PacketEventsEncoder e=(PacketEventsEncoder)ch.pipeline().get(PacketEvents.ENCODER_NAME);if(d!=null)d.setUser(user);if(e!=null)e.setUser(user);}
 public void setPlayer(Object raw,Object player){Channel ch=(Channel)raw;PacketEventsDecoder d=(PacketEventsDecoder)ch.pipeline().get(PacketEvents.DECODER_NAME);PacketEventsEncoder e=(PacketEventsEncoder)ch.pipeline().get(PacketEvents.ENCODER_NAME);if(d!=null)d.setPlayer(player);if(e!=null)e.setPlayer(player);ch.attr(io.netty.util.AttributeKey.valueOf("vibmc-player-set")).set(Boolean.TRUE);}
 public boolean isPlayerSet(Object raw){Object value=((Channel)raw).attr(io.netty.util.AttributeKey.valueOf("vibmc-player-set")).get();return Boolean.TRUE.equals(value);}
}
