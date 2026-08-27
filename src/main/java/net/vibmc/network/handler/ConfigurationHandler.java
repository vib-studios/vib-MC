package net.vibmc.network.handler;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerConfigurationEnd;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerPluginMessage;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerUpdateEnabledFeatures;
import net.vibmc.entity.ServerPlayer;
import net.vibmc.network.ProtocolState;
import net.vibmc.network.packetevents.PacketEventsTags;
import net.vibmc.network.packetevents.WrapperConfigServerUpdateTags;
import net.vibmc.registry.MinecraftDataRegistryCodec;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;

/** Configuration-stage handshake with registry data selected from the detected client version. */
public final class ConfigurationHandler implements PacketHandler {
    private final String username;
    private final UUID uuid;
    private boolean sent;
    private boolean waitingForKnownPacks;
    private boolean registriesSent;
    private boolean completed;

    public ConfigurationHandler(String username,UUID uuid){this.username=username;this.uuid=uuid;}

    public synchronized void begin(ServerPlayer connection){
        if(sent||completed||connection.protocolState()!=ProtocolState.CONFIGURATION)return;
        ClientVersion version=connection.getUser().getClientVersion();
        sent=true;
        connection.getUser().sendPacket(new WrapperConfigServerPluginMessage("minecraft:brand",brandData()));
        connection.getUser().sendPacket(new WrapperConfigServerUpdateEnabledFeatures(
                Collections.singleton(ResourceLocation.minecraft("vanilla"))));
        if(MinecraftDataRegistryCodec.usesSplitRegistries(version)){
            waitingForKnownPacks=true;
            // We do not omit any registry entries, so there is no need to negotiate a built-in
            // pack. The response is still a required synchronization point in this format.
            connection.getUser().sendPacket(new com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerSelectKnownPacks(
                    Collections.emptyList()));
        }else sendRegistriesAndFinish(connection,version);
    }

    public synchronized void knownPacksSelected(ServerPlayer connection){
        if(completed||!waitingForKnownPacks||registriesSent)return;
        waitingForKnownPacks=false;
        sendRegistriesAndFinish(connection,connection.getUser().getClientVersion());
    }

    private void sendRegistriesAndFinish(ServerPlayer connection,ClientVersion version){
        if(registriesSent)return;registriesSent=true;
        if(MinecraftDataRegistryCodec.usesSplitRegistries(version)){
            for(java.util.Map.Entry<ResourceLocation,java.util.List<WrapperConfigServerRegistryData.RegistryElement>> registry:
                    MinecraftDataRegistryCodec.splitRegistries(version).entrySet()){
                connection.getUser().sendPacket(new WrapperConfigServerRegistryData(
                        registry.getKey(),registry.getValue()));
            }
        }else{
            connection.getUser().sendPacket(new WrapperConfigServerRegistryData(
                    MinecraftDataRegistryCodec.create(version)));
        }
        connection.getUser().sendPacket(new WrapperConfigServerUpdateTags(PacketEventsTags.tagMap(
                version,MinecraftDataRegistryCodec.referencedTags(version),
                MinecraftDataRegistryCodec.referencedTagsByRegistry(version))));
        // PacketEvents changes the outbound state to PLAY while serializing this packet.
        connection.getUser().sendPacket(new WrapperConfigServerConfigurationEnd());
    }

    public synchronized void complete(ServerPlayer connection){
        if(completed||!sent||connection.protocolState()!=ProtocolState.CONFIGURATION)return;
        completed=true;
        connection.setProtocolState(ProtocolState.PLAY);
        connection.setHandler(new PlayHandler(connection));
        com.github.retrooper.packetevents.PacketEvents.getAPI().getInjector().setPlayer(
                connection.channel(),connection);
        LoginHandler.publishPlayer(connection,username,uuid);
    }

    private static byte[] brandData(){
        byte[] text="vib-MC".getBytes(StandardCharsets.UTF_8);
        byte[] data=new byte[text.length+1];data[0]=(byte)text.length;
        System.arraycopy(text,0,data,1,text.length);return data;
    }
}
