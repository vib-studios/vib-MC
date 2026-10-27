package net.vibmc.network.handler;

import net.vibmc.entity.ServerPlayer;
import net.vibmc.network.LoginPacketData;
import net.vibmc.network.ProtocolState;
import net.vibmc.server.VibMC;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class LoginHandler implements PacketHandler {
    /**
     * Session lookups block for up to the HTTP timeouts. Anyone can produce a valid
     * encryption response with the public key, so a thread per login was unbounded.
     */
    private static final java.util.concurrent.ExecutorService AUTHENTICATION = createAuthenticationPool();

    private final SecureRandom random = new SecureRandom();
    private String username;
    private byte[] verifyToken;
    private boolean authenticationStarted;

    private static java.util.concurrent.ExecutorService createAuthenticationPool() {
        java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.ThreadPoolExecutor pool = new java.util.concurrent.ThreadPoolExecutor(
                4, 4, 30L, java.util.concurrent.TimeUnit.SECONDS,
                new java.util.concurrent.ArrayBlockingQueue<Runnable>(256), task -> {
                    Thread thread = new Thread(task, "Session Auth #" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    public void beginLogin(ServerPlayer connection, String requestedUsername) {
        if (!requestedUsername.matches("[A-Za-z0-9_]{3,16}")) {
            connection.disconnect("Invalid username. Use 3-16 letters, numbers, or underscores.");
            return;
        }
        VibMC server = VibMC.getInstance();
        if (server.getPlayerManager().getPlayer(requestedUsername) != null) {
            connection.disconnect("A player with that username is already online.");
            return;
        }
        if (server.getPlayerManager().getOnlineCount() >= server.getConfig().maxPlayers()) {
            connection.disconnect("The server is full.");
            return;
        }

        username = requestedUsername;
        connection.setUsername(username);
        if (server.getConfig().useLegacyProxyForwarding()) {
            if (connection.getProfileUuid() == null) {
                connection.disconnect("The proxy did not forward a valid player identity.");
                return;
            }
            finishLogin(connection, connection.getProfileUuid());
        } else if (server.getConfig().onlineMode()) {
            sendEncryptionRequest(connection);
        } else {
            connection.setProfileProperties(Collections.emptyList());
            finishLogin(connection, UUID.nameUUIDFromBytes(
                    ("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)));
        }
    }

    private void sendEncryptionRequest(ServerPlayer connection) {
        verifyToken = new byte[4];
        random.nextBytes(verifyToken);
        byte[] publicKey = SessionAuthentication.keyPair().getPublic().getEncoded();
        connection.getUser().sendPacket(new com.github.retrooper.packetevents.wrapper.login.server.WrapperLoginServerEncryptionRequest("",publicKey,verifyToken));
    }

    public synchronized void receiveEncryptionResponse(ServerPlayer connection, byte[] secret, byte[] token) {
        if (verifyToken == null || authenticationStarted) return;
        authenticationStarted = true;
        handleEncryptionResponse(connection, new LoginPacketData(LoginPacketData.Type.ENCRYPTION_RESPONSE, null, secret, token));
    }

    private void handleEncryptionResponse(ServerPlayer connection, LoginPacketData data) {
        try {
            byte[] encryptedSecret = data.encryptedSecret();
            byte[] encryptedToken = data.encryptedToken();
            byte[] sharedSecret = SessionAuthentication.rsaDecrypt(
                    encryptedSecret, SessionAuthentication.keyPair().getPrivate());
            byte[] returnedToken = SessionAuthentication.rsaDecrypt(
                    encryptedToken, SessionAuthentication.keyPair().getPrivate());
            if (sharedSecret.length != 16) {
                connection.forceClose();
                return;
            }
            connection.enableEncryption(sharedSecret);
            if (!Arrays.equals(verifyToken, returnedToken)) {
                connection.disconnect("Invalid encryption response.");
                return;
            }
            try {
                AUTHENTICATION.execute(() -> authenticate(connection, sharedSecret));
            } catch (java.util.concurrent.RejectedExecutionException busy) {
                connection.disconnect("The server is busy authenticating other players. Try again shortly.");
            } catch (Exception e) {
                connection.disconnect("Authentication queue is saturated.");
            }
        } catch (Exception e) {
            connection.disconnect("Failed to enable encryption.");
        }
    }

    private void authenticate(ServerPlayer connection, byte[] sharedSecret) {
        // Queued behind other logins long enough for the client to give up.
        if (!connection.isOpen()) return;
        try {
            SessionAuthentication.AuthenticatedProfile profile =
                    SessionAuthentication.authenticate(username, sharedSecret);
            if (!connection.isOpen()) {
                return;
            }
            connection.setProfileUuid(profile.uuid);
            connection.setProfileProperties(profile.properties);
            finishLogin(connection, profile.uuid);
        } catch (Exception e) {
            VibMC.getInstance().getLogger().warn("Authentication failed for %s: %s", username, e.getMessage());
            connection.disconnect("Failed to verify username with the session server.");
        }
    }

    private synchronized void finishLogin(ServerPlayer connection, UUID uuid) {
        if (!connection.isOpen() || connection.protocolState() == ProtocolState.PLAY) {
            return;
        }
        VibMC server = VibMC.getInstance();
        if (server.getPlayerManager().getPlayer(uuid) != null) {
            connection.disconnect("That authenticated profile is already online.");
            return;
        }
        connection.setProfileUuid(uuid);
        connection.getUser().sendPacket(new com.github.retrooper.packetevents.wrapper.login.server.WrapperLoginServerLoginSuccess(
                connection.getUser().getProfile()));
        if(connection.getUser().getClientVersion().isNewerThanOrEquals(
                com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_20_2)){
            connection.setProtocolState(ProtocolState.CONFIGURATION);
            connection.setHandler(new ConfigurationHandler(username,uuid));
            return;
        }
        connection.setProtocolState(ProtocolState.PLAY);
        connection.setHandler(new PlayHandler(connection));
        com.github.retrooper.packetevents.PacketEvents.getAPI().getInjector().setPlayer(connection.channel(),connection);
        publishPlayer(connection,username,uuid);
    }

    static void publishPlayer(ServerPlayer player,String username,UUID uuid){
        VibMC server=VibMC.getInstance();
        // PacketEvents finishes inbound state transitions after listener callbacks. Publish on
        // the following event-loop turn, then perform gameplay mutation on the server thread.
        player.channel().eventLoop().execute(()->server.executeOnMainThread(()->{
            if(!player.isOpen())return;
            if(server.getPlayerManager().getPlayer(uuid)!=null
                    ||server.getPlayerManager().getPlayer(username)!=null){
                player.disconnect("That player is already online.");return;
            }
            player.setWorldAndIdentity(server.getWorldManager().getMainWorld(),username,uuid);
            player.setAllowFlight(server.getConfig().allowFlight());
            // Persisted positions are authoritative. Do not relocate on reconnect: doing so
            // would let relogging escape water, lava, falling blocks, or combat situations.
            if(!server.getPlayerManager().restorePlayer(player))player.spawnAtSpawn();
            server.getPlayerManager().addPlayer(player);
            player.getWorld().addEntity(player);
        }));
    }
}
