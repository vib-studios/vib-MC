package net.vibmc.network.packetevents;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.protocol.world.states.type.StateValue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** One semantic block-state conversion boundary shared by chunks and incremental updates. */
public final class PacketEventsStateMappings {
    private static final Map<Long,Integer> IDS = new ConcurrentHashMap<>();
    private PacketEventsStateMappings() {}

    public static int id(WrappedBlockState source, ClientVersion version) {
        if (source == null) return WrappedBlockState.getDefaultState(version, StateTypes.AIR).getGlobalId();
        int sourceId=source.getGlobalId();
        // The legacy combined block-id/metadata registry is stable from 1.8 through 1.12.2.
        if(version.isNewerThanOrEquals(ClientVersion.V_1_8)
                &&version.isOlderThan(ClientVersion.V_1_13))return sourceId;
        long key=((long)version.getProtocolVersion()<<32)|(sourceId&0xffffffffL);
        Integer cached=IDS.get(key);if(cached!=null)return cached;
        int mapped=map(source,version);Integer previous=IDS.putIfAbsent(key,mapped);return previous==null?mapped:previous;
    }

    @SuppressWarnings("deprecation") // PE currently exposes no public semantic property iterator.
    private static int map(WrappedBlockState source,ClientVersion version){
        WrappedBlockState target=WrappedBlockState.getDefaultState(version,source.getType(),true);
        if(target!=null){
            for(Map.Entry<StateValue,Object> property:source.getInternalData().entrySet()){
                if(!target.hasProperty(property.getKey()))continue;
                try{target.setData(property.getKey(),property.getValue());}catch(IllegalArgumentException ignored){/* keep target default */}
            }
        }
        if(target==null||target.getType()!=source.getType())target=WrappedBlockState.getDefaultState(version,StateTypes.AIR,true);
        int result=target.getGlobalId();
        // PE 2.13 groups 1.13/1.13.1 with 1.13.2, whose extra TNT state shifts
        // the shared vanilla registry by one until the later coral-state additions.
        if((version==ClientVersion.V_1_13||version==ClientVersion.V_1_13_1)&&result>=1128&&result<8460)result--;
        return result;
    }
}
