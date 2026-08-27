package net.vibmc.player;

import net.vibmc.command.CommandSender;
import net.vibmc.entity.ServerPlayer;
import com.github.retrooper.packetevents.protocol.player.User;
import net.vibmc.network.JsonText;
import net.vibmc.plugin.event.ChatEvent;
import net.vibmc.plugin.event.PlayerJoinEvent;
import net.vibmc.plugin.event.PlayerQuitEvent;
import net.vibmc.plugin.PluginManager;
import net.vibmc.server.VibMC;
import net.vibmc.world.WorldChunk;
import net.vibmc.world.World;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.vibmc.player.storage.PlayerData;
import net.vibmc.player.storage.PlayerDataStorage;

public class PlayerManager {
    private final Map<UUID, ServerPlayer> players;
    private final Map<String, ServerPlayer> byName;
    private final PlayerDataStorage playerDataStorage;

    public PlayerManager() {
        this(Paths.get("playerdata"));
    }

    public PlayerManager(Path playerDataDirectory) {
        this.players = new ConcurrentHashMap<>();
        this.byName = new ConcurrentHashMap<>();
        this.playerDataStorage = new PlayerDataStorage(playerDataDirectory);
    }

    public void addPlayer(ServerPlayer player) {
        players.put(player.getUuid(), player);
        byName.put(player.getUsername().toLowerCase(Locale.ROOT), player);

        VibMC server = VibMC.getInstance();
        PluginManager pluginManager = server.getPluginManager();

        PlayerJoinEvent event = new PlayerJoinEvent(player, player.getUsername() + " joined the game");
        pluginManager.fireEvent(event);

        server.getLogger().info("%s joined the game", player.getUsername());

        server.getLogger().info("Player spawn at %.1f, %.1f, %.1f (chunk %d, %d)", player.getX(), player.getY(), player.getZ(), (int) Math.floor(player.getX()) >> 4, (int) Math.floor(player.getZ()) >> 4);
        sendJoinPackets(player);
        if (event.getJoinMessage() != null && !event.getJoinMessage().isEmpty()) {
            broadcastMessage(JsonText.component("§e" + event.getJoinMessage()));
        }
    }

    public void removePlayer(ServerPlayer player) {
        for(ServerPlayer viewer:players.values()){
            if(player.getUuid().equals(viewer.getCameraTargetUuid()))viewer.resetSpectatorCamera(true);
        }
        boolean removed = players.remove(player.getUuid(), player);
        byName.remove(player.getUsername().toLowerCase(Locale.ROOT), player);
        if (!removed) {
            return;
        }

        VibMC server = VibMC.getInstance();
        if (server.isAutomaticSaving()) savePlayer(player);
        PluginManager pluginManager = server.getPluginManager();

        if (player.getUsername() != null && !player.getUsername().isEmpty()) {
            PlayerQuitEvent event = new PlayerQuitEvent(player, player.getUsername() + " left the game");
            pluginManager.fireEvent(event);

            server.getLogger().info("%s left the game", player.getUsername());
            removePlayerVisibility(player);
            if (event.getQuitMessage() != null && !event.getQuitMessage().isEmpty()) {
                broadcastMessage(JsonText.component("§e" + event.getQuitMessage()));
            }
        }
    }

    /** Restores by UUID; unknown worlds safely fall back to the main world. */
    public boolean restorePlayer(ServerPlayer player) {
        try {
            Optional<PlayerData> loaded = playerDataStorage.read(player.getUuid());
            if (!loaded.isPresent()) return false;
            PlayerData data = loaded.get();
            World restoredWorld = VibMC.getInstance().getWorldManager().getWorld(data.worldName);
            if (restoredWorld == null) restoredWorld = VibMC.getInstance().getWorldManager().getMainWorld();
            player.restorePersistentState(data, restoredWorld);
            return true;
        } catch (IOException | RuntimeException error) {
            VibMC.getInstance().getLogger().warn("Could not restore player data for %s: %s",
                    player.getUsername(), error.getMessage());
            return false;
        }
    }

    public boolean savePlayer(ServerPlayer player) {
        if (player == null || player.getUuid() == null || !player.isInWorld()) return false;
        try {
            playerDataStorage.write(player.getUuid(), player.snapshotPersistentState());
            return true;
        } catch (IOException | RuntimeException error) {
            VibMC server = VibMC.getInstance();
            if (server != null) server.getLogger().warn("Could not save player data for %s: %s",
                    player.getUsername(), error.getMessage());
            return false;
        }
    }

    public int saveAllPlayers() {
        int saved = 0;
        for (ServerPlayer player : players.values()) if (savePlayer(player)) saved++;
        return saved;
    }

    public ServerPlayer getPlayer(UUID uuid) {
        return players.get(uuid);
    }

    public ServerPlayer getPlayer(String name) {
        return name == null ? null : byName.get(name.toLowerCase(Locale.ROOT));
    }

    public ServerPlayer getPlayerByEntityId(int entityId){
        for(ServerPlayer player:players.values())if(player.getEntityId()==entityId)return player;
        return null;
    }

    public void handleSpectatorInteraction(ServerPlayer spectator,int entityId){
        if(spectator.getGameModeEnum()!=GameMode.SPECTATOR)return;
        ServerPlayer target=getPlayerByEntityId(entityId);
        if(target!=null&&target.getWorld()==spectator.getWorld())spectator.setSpectatorCamera(target);
    }

    public void handleSpectateTeleport(ServerPlayer spectator,UUID targetUuid){
        if(spectator.getGameModeEnum()!=GameMode.SPECTATOR)return;
        ServerPlayer target=getPlayer(targetUuid);
        if(target!=null&&target.getWorld()==spectator.getWorld())
            spectator.teleport(target.getX(),target.getY(),target.getZ());
    }

    public ServerPlayer getPlayer(User user) {
        for (ServerPlayer player : players.values()) {
            if (player.getUser() == user) {
                return player;
            }
        }
        return null;
    }

    public Collection<ServerPlayer> getOnlinePlayers() {
        return Collections.unmodifiableCollection(players.values());
    }

    public int getOnlineCount() {
        return players.size();
    }

    public void respawnPlayer(ServerPlayer player){
        if(player==null||player.isAlive()||!player.isInWorld())return;
        synchronized(player){
            player.resetSpectatorCamera(false);
            player.respawn();
            player.resetChunkStreaming();
            sendRespawn(player.getUser(),player);
            player.teleport(player.getX(),player.getY(),player.getZ());
            sendPlayerAbilities(player.getUser(),player);
            sendHeldItemChange(player.getUser(),player);
            sendUpdateHealth(player.getUser(),player);
            player.sendInventory();
            sendStartWaitingForChunks(player.getUser());
            synchronizePlayerVisibility(player);
            sendInitialChunks(player);
            sendWeather(player.getUser(),player.getWorld().weatherSystem().weather());
        }
    }

    public void transferPlayer(ServerPlayer player, World destination) {
        synchronized (player) {
            if (destination == null || destination == player.getWorld()) return;
            for(ServerPlayer viewer:players.values()){
                if(player.getUuid().equals(viewer.getCameraTargetUuid()))viewer.resetSpectatorCamera(true);
            }
            player.resetSpectatorCamera(false);
            World previous = player.getWorld();
            for (ServerPlayer online : players.values()) {
                if (online == player) continue;
                if (online.getWorld() == previous) {
                    sendDestroyEntity(online.getUser(), player.getEntityId());
                    sendDestroyEntity(player.getUser(), online.getEntityId());
                }
            }
            VibMC.getInstance().getLogger().info("Moving %s from %s (%d) to %s (%d)",
                    player.getUsername(), previous.name(), previous.environment().ordinal(),
                    destination.name(), destination.environment().ordinal());
            previous.removeEntity(player);
            player.setWorld(destination);
            player.getUser().setDimensionType(dimension(player));
            destination.addEntity(player);
            player.resetChunkStreaming();
    
            sendRespawn(player.getUser(), player);
            int spawnX = destination.environment() == net.vibmc.world.WorldEnvironment.NETHER ? 8 : 10;
            int spawnZ = 8;
            double spawnY = destination.environment() == net.vibmc.world.WorldEnvironment.NETHER
                    ? 65.0 : destination.findSafeSpawnY(spawnX, spawnZ);
            player.teleport(spawnX + 0.5, spawnY, spawnZ + 0.5);
            // Respawn clears client-side inventory/selected slot/abilities even though the
            // authoritative ServerPlayer data survives the dimension transfer.
            sendPlayerAbilities(player.getUser(),player);
            sendHeldItemChange(player.getUser(),player);
            sendUpdateHealth(player.getUser(),player);
            player.sendInventory();
            sendStartWaitingForChunks(player.getUser());
            synchronizePlayerVisibility(player);
            sendInitialChunks(player);
            sendWeather(player.getUser(), destination.weatherSystem().weather());
        }
        }

    private void sendRespawn(User user, ServerPlayer player) {
        send(user,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRespawn(dimension(player),player.getWorld().name(),difficulty(),0L,mode(player),mode(player),false,false,false,null,null,null));
    }

    private void synchronizePlayerVisibility(ServerPlayer joining) {
        // A player-list entry must arrive before Spawn Player or vanilla ignores the skin/entity.
        sendPlayerInfoAdd(joining.getUser(), joining);
        for (ServerPlayer online : players.values()) {
            if (online == joining) {
                continue;
            }
            sendPlayerInfoAdd(joining.getUser(), online);
            sendPlayerInfoAdd(online.getUser(), joining);
            if (online.getWorld() == joining.getWorld()) {
                if (online.getGameModeEnum() != GameMode.SPECTATOR || joining.getGameModeEnum() == GameMode.SPECTATOR)
                    sendSpawnPlayer(joining.getUser(), online);
                if (joining.getGameModeEnum() != GameMode.SPECTATOR || online.getGameModeEnum() == GameMode.SPECTATOR)
                    sendSpawnPlayer(online.getUser(), joining);
            }
        }
    }

    private void removePlayerVisibility(ServerPlayer leaving) {
        for (ServerPlayer online : players.values()) {
            if (online.getWorld() == leaving.getWorld()) {
                sendDestroyEntity(online.getUser(), leaving.getEntityId());
            }
            sendPlayerInfoRemove(online.getUser(), leaving.getUuid());
        }
    }

    public void updateGameModeVisibility(ServerPlayer changed) {
        for (ServerPlayer viewer : players.values()) {
            sendPlayerGameMode(viewer.getUser(), changed);
            if (viewer == changed || viewer.getWorld() != changed.getWorld()) continue;
            if (changed.getGameModeEnum() == GameMode.SPECTATOR && viewer.getGameModeEnum() != GameMode.SPECTATOR) {
                sendDestroyEntity(viewer.getUser(), changed.getEntityId());
            } else {
                sendSpawnPlayer(viewer.getUser(), changed);
                sendInvisibleMetadata(viewer.getUser(), changed,
                        changed.getGameModeEnum() == GameMode.SPECTATOR);
            }
        }
    }

    private void sendPlayerGameMode(User user, ServerPlayer player) {
        if(user.getClientVersion().isNewerThanOrEquals(
                com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_19_3)){
            com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.PlayerInfo info=
                    new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                            profile(player),true,0,mode(player),null,null);
            send(user,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate(
                    com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE,info));
        }else{
            send(user,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo(com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo.Action.UPDATE_GAME_MODE,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo.PlayerData(null,profile(player),mode(player),0)));
        }
    }

    private void sendInvisibleMetadata(User user, ServerPlayer player, boolean invisible) {
        send(user,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata(player.getEntityId(),java.util.Collections.singletonList(new com.github.retrooper.packetevents.protocol.entity.data.EntityData<Byte>(0,com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes.BYTE,(byte)(invisible?0x20:0)))));
    }

    public void broadcastPlayerPosition(ServerPlayer moving) {
        for (ServerPlayer online : players.values()) {
            if (online != moving && online.getWorld() == moving.getWorld()) {
                sendEntityTeleport(online.getUser(), moving);
                sendEntityHeadLook(online.getUser(), moving);
            }
        }
    }

    private void sendPlayerInfoAdd(User user, ServerPlayer player) {
        if(user.getClientVersion().isNewerThanOrEquals(
                com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_19_3)){
            com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.PlayerInfo info=
                    new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                            profile(player),true,0,mode(player),null,null);
            java.util.EnumSet<com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.Action> actions=
                    java.util.EnumSet.of(
                            com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                            com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE,
                            com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
                            com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY,
                            com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME);
            send(user,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate(actions,info));
        }else{
            send(user,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo(com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo.Action.ADD_PLAYER,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo.PlayerData(null,profile(player),mode(player),0)));
        }
    }

    private void sendPlayerInfoRemove(User user, UUID uuid) {
        if(user.getClientVersion().isNewerThanOrEquals(
                com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_19_3))
            send(user,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove(uuid));
        else
            send(user,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo(com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo.Action.REMOVE_PLAYER,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo.PlayerData(null,new com.github.retrooper.packetevents.protocol.player.UserProfile(uuid,""),null,0)));
    }

    private void sendSpawnPlayer(User user, ServerPlayer player) {
        send(user,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer(player.getEntityId(),player.getUuid(),location(player)));
    }

    private void sendEntityTeleport(User user, ServerPlayer player) {
        send(user,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport(player.getEntityId(),location(player),player.isOnGround()));
    }

    private void sendEntityHeadLook(User user, ServerPlayer player) {
        send(user,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook(player.getEntityId(),player.getYaw()));
    }

    private void sendDestroyEntity(User user, int entityId) {
        send(user,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities(entityId));
    }

    public void broadcastBlockChange(World world, int x, int y, int z, com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState state) {
        for (ServerPlayer player : players.values()) {
            if (player.getWorld() != world) continue;
            User user = player.getUser();
            int stateId=net.vibmc.network.packetevents.PacketEventsStateMappings.id(
                    state,user.getClientVersion());
            send(user,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange(
                    new com.github.retrooper.packetevents.util.Vector3i(x,y,z),stateId));
        }
    }

    public void broadcastMessage(String message) {
        for (ServerPlayer player : players.values()) {
            player.sendMessage(message);
        }
    }

    public void broadcastMessage(String message, ServerPlayer exclude) {
        for (ServerPlayer player : players.values()) {
            if (player != exclude) {
                player.sendMessage(message);
            }
        }
    }

    public void handleChat(ServerPlayer sender, String message) {
        if (message.startsWith("/")) {
            VibMC.getInstance().getCommandManager().execute(new CommandSender(sender), message);
            return;
        }

        ChatEvent event = new ChatEvent(sender, message);
        VibMC.getInstance().getPluginManager().fireEvent(event);
        if (event.isCancelled()) return;

        String eventMessage = event.getMessage();
        if (eventMessage == null || eventMessage.isEmpty()) {
            return;
        }
        broadcastMessage(JsonText.component("<" + sender.getUsername() + "> " + eventMessage));
    }

    public void tickAll() {
        for (ServerPlayer player : players.values()) {
            // World.tick() owns entity updates; this pass only manages per-player streaming.
            updateChunkStream(player);
        }
    }

    private void updateChunkStream(ServerPlayer player) {
        if (player.hasChunkStreamingFailed()) return;
        synchronized (player) {
            int cx = (int) Math.floor(player.getX()) >> 4;
            int cz = (int) Math.floor(player.getZ()) >> 4;
            int configuredViewDistance = VibMC.getInstance().getConfig().getViewDistance();
            boolean centerChanged=cx!=player.getLoadedChunkX()||cz!=player.getLoadedChunkZ();
            if (!centerChanged && player.getStreamedViewDistance() >= configuredViewDistance) return;
            if(centerChanged)sendViewPosition(player.getUser(),cx,cz);
            int viewDist = Math.min(configuredViewDistance, player.getStreamedViewDistance());
            Set<Long> wanted = new HashSet<>();
            for (int dx = -viewDist; dx <= viewDist; dx++) {
                for (int dz = -viewDist; dz <= viewDist; dz++) {
                    wanted.add(chunkKey(cx + dx, cz + dz));
                }
            }
            // Fill the newly visible edge before removing the old distant edge. This avoids
            // a one-tick hole/flicker when crossing a chunk boundary.
            for (Long key : wanted) {
                if (player.getSentChunks().add(key)) {
                    sendChunk(player, player.getWorld(), (int) (key >> 32), (int) (key & 0xFFFFFFFFL));
                }
            }
            for (Long key : new ArrayList<>(player.getSentChunks())) {
                if (!wanted.contains(key)) {
                    sendUnloadChunk(player.getUser(), (int) (key >> 32), (int) (key & 0xFFFFFFFFL));
                    player.getSentChunks().remove(key);
                }
            }
            player.setLoadedChunk(cx, cz);
            player.advanceStreamedViewDistance(configuredViewDistance);
        }
        }

    private static long chunkKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private void sendUnloadChunk(User user, int chunkX, int chunkZ) {
        send(user,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUnloadChunk(chunkX,chunkZ));
    }

    private void sendViewDistance(User user,int distance){
        if(user.getClientVersion().isNewerThanOrEquals(
                com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_14))
            send(user,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateViewDistance(distance));
    }

    private void sendViewPosition(User user,int chunkX,int chunkZ){
        if(user.getClientVersion().isNewerThanOrEquals(
                com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_14))
            send(user,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateViewPosition(chunkX,chunkZ));
    }

    private void sendJoinPackets(ServerPlayer player) {
        User user = player.getUser();
        World world = player.getWorld();
        // Resolve and cache this connection's immutable minecraft-data manifest. Individual
        // registry files are parsed lazily when a protocol adapter requests them.
        net.vibmc.registry.MinecraftDataRegistry.get().forClient(user.getClientVersion());

        sendLoginPlay(user, player);
        sendRegistryTags(user);
        sendServerBrand(user);
        sendDifficulty(user);
        sendPlayerAbilities(user, player);
        sendHeldItemChange(user, player);
        sendWorldInfo(user, player);
        sendSpawnPosition(user, player);
        sendUpdateHealth(user, player);
        player.sendInventory();
        sendPlayerPosition(user, player);
        sendStartWaitingForChunks(user);
        synchronizePlayerVisibility(player);

        sendInitialChunks(player);
        sendWeather(user, world.weatherSystem().weather());
    }

    private void sendInitialChunks(ServerPlayer player) {
        World world = player.getWorld();
        int centerX = (int) Math.floor(player.getX()) >> 4;
        int centerZ = (int) Math.floor(player.getZ()) >> 4;
        int configuredViewDistance=VibMC.getInstance().getConfig().getViewDistance();
        sendViewDistance(player.getUser(),configuredViewDistance);
        sendViewPosition(player.getUser(),centerX,centerZ);
        int viewDistance = Math.min(1, configuredViewDistance);
        for (int radius = 0; radius <= viewDistance; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int chunkX = centerX + dx;
                    int chunkZ = centerZ + dz;
                    long key=chunkKey(chunkX,chunkZ);
                    // PlayerManager publishes the player before login packets finish; the tick
                    // thread may stream concurrently, so reserve the key before encoding/sending.
                    if(player.getSentChunks().add(key))sendChunk(player, world, chunkX, chunkZ);
                }
            }
        }
        player.setLoadedChunk(centerX, centerZ);
    }

    private void sendRegistryTags(User user) {
        if (user.getClientVersion().isNewerThanOrEquals(
                com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_13)
                && user.getClientVersion().isOlderThan(
                com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_20_2)) {
            send(user,net.vibmc.network.packetevents.PacketEventsTags.create(user.getClientVersion()));
        }
    }

    private void sendStartWaitingForChunks(User user){
        if(user.getClientVersion().isNewerThanOrEquals(
                com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_20_3)){
            send(user,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChangeGameState(
                    com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChangeGameState.Reason.START_LOADING_CHUNKS,0.0f));
        }
    }

    private void sendServerBrand(User user) {
        String channel = user.getClientVersion().isNewerThanOrEquals(
                com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_13)
                ? "minecraft:brand" : "MC|Brand";
        send(user,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPluginMessage(channel,brandData()));
    }

    private void sendLoginPlay(User user, ServerPlayer player) {
        user.setDimensionType(dimension(player));
        sendJoin(user,player);
    }

    private void sendDifficulty(User user) {
        send(user,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDifficulty(difficulty(),false));
    }

    private void sendPlayerAbilities(User user, ServerPlayer player) {
        send(user,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerAbilities(player.isInvulnerable(),player.isFlying(),player.isAllowFlight(),player.getGameModeEnum()==GameMode.CREATIVE,.05f,.1f));
    }

    private void sendHeldItemChange(User user, ServerPlayer player) {
        send(user,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerHeldItemChange(player.getHeldItemSlot()));
    }

    private void sendPlayerPosition(User user, ServerPlayer player) {
        player.teleport(player.getX(),player.getY(),player.getZ());
    }

    public void broadcastWorldTime(World world) {
        for (ServerPlayer player : players.values()) {
            if (player.getWorld() == world) {
                sendWorldInfo(player.getUser(), player);
            }
        }
    }

    private void sendWorldInfo(User user, ServerPlayer player) {
        send(user,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTimeUpdate(player.getWorld().getWorldTime(),player.getWorld().getDayTime()));
    }

    private void sendSpawnPosition(User user, ServerPlayer player) {
        send(user,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPosition(new com.github.retrooper.packetevents.util.Vector3i((int)player.getX(),(int)player.getY(),(int)player.getZ())));
    }

    private void sendUpdateHealth(User user, ServerPlayer player) {
        send(user,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateHealth(player.getHealth(),player.getFoodLevel(),player.getFoodSaturation()));
    }

    private void sendChunk(ServerPlayer player, World world, int chunkX, int chunkZ) {
        if (player.hasChunkStreamingFailed()) return;
        com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData packet = null;
        try {
            WorldChunk chunk = world.getChunk(chunkX, chunkZ);
            if (chunk != null) {
                packet = net.vibmc.network.packetevents.PacketEventsChunkAdapter.wrap(
                        chunk, player.getUser().getClientVersion());
                send(player.getUser(), packet);
            }
        } catch (RuntimeException error) {
            if (packet != null && packet.buffer != null) {
                com.github.retrooper.packetevents.netty.buffer.ByteBufHelper.release(packet.buffer);
                packet.buffer = null;
            }
            if (player.markChunkStreamingFailed()) {
                VibMC.getInstance().getLogger().warn(
                        "Chunk streaming is unavailable for %s protocol %d: %s",
                        player.getUsername(), player.getUser().getClientVersion().getProtocolVersion(), error);
                player.disconnect("This client version's chunk format is not supported yet.");
            }
        }
    }

    public void broadcastWeather(String weather) {
        for (ServerPlayer player : players.values()) {
            sendWeather(player.getUser(), weather);
        }
    }

    private void sendWeather(User user, String weather) {
        boolean raining = !"clear".equals(weather);
        sendGameState(user, raining ? 2 : 1, 0.0f);
        sendGameState(user, 7, raining ? 1.0f : 0.0f);
        sendGameState(user, 8, "thunder".equals(weather) ? 1.0f : 0.0f);
    }

    private void sendGameState(User user, int reason, float value) {
        send(user,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChangeGameState(reason,value));
    }


    private static void send(User user,com.github.retrooper.packetevents.wrapper.PacketWrapper<?> packet){user.sendPacket(packet);}
    private static com.github.retrooper.packetevents.protocol.world.Location location(ServerPlayer p){return new com.github.retrooper.packetevents.protocol.world.Location(p.getX(),p.getY(),p.getZ(),p.getYaw(),p.getPitch());}
    private static com.github.retrooper.packetevents.protocol.player.GameMode mode(ServerPlayer p){return com.github.retrooper.packetevents.protocol.player.GameMode.getById(p.getGameMode());}
    private static com.github.retrooper.packetevents.protocol.player.UserProfile profile(ServerPlayer p){return p.getUser().getProfile();}
    private static com.github.retrooper.packetevents.protocol.world.Difficulty difficulty(){return com.github.retrooper.packetevents.protocol.world.Difficulty.valueOf(VibMC.getInstance().getConfig().difficulty().toUpperCase(java.util.Locale.ROOT));}
    private static byte[] brandData(){byte[] text="vib-MC".getBytes(java.nio.charset.StandardCharsets.UTF_8);byte[] data=new byte[text.length+1];data[0]=(byte)text.length;System.arraycopy(text,0,data,1,text.length);return data;}
    private static com.github.retrooper.packetevents.protocol.world.dimension.DimensionType dimension(ServerPlayer p){
        switch(p.getWorld().environment()){
            case NETHER:return com.github.retrooper.packetevents.protocol.world.dimension.DimensionTypes.THE_NETHER;
            case END:return com.github.retrooper.packetevents.protocol.world.dimension.DimensionTypes.THE_END_PRE_1_21_9;
            // vib-MC intentionally keeps the classic 0..255 build range on modern clients.
            default:return com.github.retrooper.packetevents.protocol.world.dimension.DimensionTypes.OVERWORLD_PRE_1_18;
        }
    }
    private static void sendJoin(User user,ServerPlayer p){com.github.retrooper.packetevents.protocol.nbt.NBTCompound codec=user.getClientVersion().isNewerThanOrEquals(com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_20_2)?new com.github.retrooper.packetevents.protocol.nbt.NBTCompound():net.vibmc.registry.MinecraftDataRegistryCodec.create(user.getClientVersion());java.util.List<String> worlds=java.util.Collections.singletonList(p.getWorld().name());send(user,new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerJoinGame(p.getEntityId(),false,mode(p),null,worlds,codec,dimension(p),difficulty(),p.getWorld().name(),0L,VibMC.getInstance().getConfig().getMaxPlayers(),8,8,false,true,false,false,null,null));}

}
