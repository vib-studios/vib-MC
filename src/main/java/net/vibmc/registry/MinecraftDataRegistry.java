package net.vibmc.registry;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Loads only vendored PrismarineJS Java registry/configuration snapshots from the classpath. */
public final class MinecraftDataRegistry {
    private static final String ROOT="/vendored/minecraft-data/data/";
    private static volatile MinecraftDataRegistry instance;

    private final Map<String,JsonObject> pathsByVersion;
    private final Map<Integer,String> versionByProtocol;
    private final Map<String,VersionData> versions=new ConcurrentHashMap<>();

    private MinecraftDataRegistry(Map<String,JsonObject> pathsByVersion,
                                  Map<Integer,String> versionByProtocol){
        this.pathsByVersion=pathsByVersion;
        this.versionByProtocol=versionByProtocol;
    }

    /** Reads the small indexes once. Individual registry JSON files remain lazy and cached. */
    public static synchronized void initialize() throws IOException {
        if(instance!=null)return;
        JsonObject roots=object(readJson(ROOT+"dataPaths.json"),"dataPaths.json");
        JsonObject pc=object(roots.get("pc"),"dataPaths.json.pc");
        Map<String,JsonObject> paths=new LinkedHashMap<>();
        for(Map.Entry<String,JsonElement> entry:pc.entrySet()){
            if(!entry.getValue().isJsonObject())continue;
            JsonObject source=entry.getValue().getAsJsonObject(),configuration=new JsonObject();
            if(source.has("version"))configuration.add("version",source.get("version"));
            if(source.has("loginPacket"))configuration.add("loginPacket",source.get("loginPacket"));
            paths.put(entry.getKey(),configuration);
        }

        JsonElement protocolRoot=readJson(ROOT+"pc/common/protocolVersions.json");
        if(!protocolRoot.isJsonArray())throw new IOException("minecraft-data protocolVersions.json is not an array");
        Map<Integer,String> protocols=new LinkedHashMap<>();
        protocolRoot.getAsJsonArray().forEach(element->{
            if(!element.isJsonObject())return;
            JsonObject value=element.getAsJsonObject();
            if(!value.has("version")||!value.has("minecraftVersion"))return;
            int protocol=value.get("version").getAsInt();
            String exact=value.get("minecraftVersion").getAsString();
            String major=value.has("majorVersion")?value.get("majorVersion").getAsString():exact;
            String selected=paths.containsKey(exact)?exact:paths.containsKey(major)?major:null;
            if(selected!=null)protocols.putIfAbsent(protocol,selected);
        });
        instance=new MinecraftDataRegistry(Collections.unmodifiableMap(paths),
                Collections.unmodifiableMap(protocols));
    }

    public static MinecraftDataRegistry get(){
        MinecraftDataRegistry loaded=instance;
        if(loaded==null)throw new IllegalStateException("MinecraftDataRegistry has not been initialized");
        return loaded;
    }

    public VersionData forClient(ClientVersion clientVersion){
        if(clientVersion==null)throw new IllegalArgumentException("clientVersion");
        String selected=selectRelease(clientVersion);
        return versions.computeIfAbsent(selected,key->new VersionData(key,pathsByVersion.get(key)));
    }

    public Set<String> releases(){return pathsByVersion.keySet();}

    private String selectRelease(ClientVersion clientVersion){
        String release=clientVersion.getReleaseName();
        if(release!=null){
            for(String candidate:release.split("/")){
                String trimmed=candidate.trim();
                if(pathsByVersion.containsKey(trimmed))return trimmed;
            }
        }
        String protocolRelease=versionByProtocol.get(clientVersion.getProtocolVersion());
        if(protocolRelease!=null)return protocolRelease;
        throw new IllegalArgumentException("No minecraft-data Java snapshot for "+release+
                " (protocol "+clientVersion.getProtocolVersion()+")");
    }

    private static JsonElement readJson(String resource) throws IOException {
        InputStream stream=MinecraftDataRegistry.class.getResourceAsStream(resource);
        if(stream==null)throw new IOException("Missing minecraft-data resource "+resource);
        try(Reader reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){
            return JsonParser.parseReader(reader);
        }
    }

    private static JsonObject object(JsonElement value,String description)throws IOException{
        if(value==null||!value.isJsonObject())throw new IOException(description+" is not a JSON object");
        return value.getAsJsonObject();
    }

    public static final class VersionData {
        private final String release;
        private final Map<String,String> categoryPaths;
        private final Map<String,JsonElement> loaded=new ConcurrentHashMap<>();

        private VersionData(String release,JsonObject mappings){
            this.release=release;
            Map<String,String> paths=new LinkedHashMap<>();
            for(Map.Entry<String,JsonElement> entry:mappings.entrySet()){
                if(entry.getValue().isJsonPrimitive())paths.put(entry.getKey(),entry.getValue().getAsString());
            }
            this.categoryPaths=Collections.unmodifiableMap(paths);
        }

        public String release(){return release;}
        public Set<String> categories(){return categoryPaths.keySet();}
        public boolean has(String category){return categoryPaths.containsKey(category);}
        void discard(String category){loaded.remove(category);}

        /** Returns a cached parsed JSON tree. Callers must treat it as immutable. */
        public JsonElement data(String category){
            String directory=categoryPaths.get(category);
            if(directory==null)throw new IllegalArgumentException(
                    "minecraft-data "+release+" has no category "+category);
            return loaded.computeIfAbsent(category,key->{
                String safeDirectory=safe(directory),safeCategory=safe(category);
                try{return readJson(ROOT+safeDirectory+"/"+safeCategory+".json");}
                catch(IOException error){throw new RegistryLoadException(error.getMessage(),error);}
            });
        }

        private static String safe(String value){
            if(value.isEmpty()||value.startsWith("/")||value.contains("..")||value.contains("\\"))
                throw new IllegalArgumentException("Unsafe minecraft-data path: "+value);
            return value;
        }
    }

    public static final class RegistryLoadException extends RuntimeException {
        private static final long serialVersionUID=1L;
        public RegistryLoadException(String message,Throwable cause){super(message,cause);}
    }
}
