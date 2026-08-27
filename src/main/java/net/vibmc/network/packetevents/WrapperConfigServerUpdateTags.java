package net.vibmc.network.packetevents;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTags;

import java.util.List;
import java.util.Map;

/** PacketEvents 2.13 exposes the 1.20.2 configuration Update Tags type without a wrapper. */
public final class WrapperConfigServerUpdateTags extends PacketWrapper<WrapperConfigServerUpdateTags> {
    private Map<ResourceLocation,List<WrapperPlayServerTags.Tag>> tags;

    public WrapperConfigServerUpdateTags(Map<ResourceLocation,List<WrapperPlayServerTags.Tag>> tags){
        super(PacketType.Configuration.Server.UPDATE_TAGS);
        this.tags=tags;
    }

    @Override public void write(){
        writeMap(tags,PacketWrapper::writeIdentifier,
                (wrapper,values)->wrapper.writeList(values,WrapperPlayServerTags.Tag::write));
    }

    @Override public void copy(WrapperConfigServerUpdateTags other){tags=other.tags;}
    public Map<ResourceLocation,List<WrapperPlayServerTags.Tag>> getTags(){return tags;}
}
