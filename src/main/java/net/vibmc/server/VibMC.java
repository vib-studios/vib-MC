package net.vibmc.server;

import net.vibmc.command.CommandManager;
import net.vibmc.entity.ServerPlayer;
import net.vibmc.network.NetworkServer;
import net.vibmc.player.PlayerManager;
import net.vibmc.permission.OperatorManager;
import net.vibmc.plugin.PluginManager;
import net.vibmc.server.util.Logger;
import net.vibmc.world.WorldManager;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class VibMC {
    private static VibMC instance;

    private final ServerConfig config;
    private final Logger logger;
    private final WorldManager worldManager;
    private final PluginManager pluginManager;
    private final PlayerManager playerManager;
    private final NetworkServer networkServer;
    private final CommandManager commandManager;
    private final OperatorManager operatorManager;

    private volatile boolean running;
    private volatile boolean pluginsEnabled;
    private static final int MAX_QUEUED_MAIN_THREAD_TASKS = 65536;
    private static final int MAX_MAIN_THREAD_TASKS_PER_TICK = 10000;

    private volatile boolean automaticSaving = true;
    private final Queue<Runnable> mainThreadTasks = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queuedMainThreadTaskCount = new AtomicInteger();
    private volatile Thread tickThread;
    private long tickCounter;

    private VibMC(ServerConfig config) throws IOException {
        instance = this;
        this.config = config;
        this.logger = new Logger("vib-MC", config.debug());
        if (config.generateStructures()) {
            try {
                net.vibmc.world.structure.StructureRegistry.reload();
            } catch (IOException e) {
                logger.warn("Could not load structure templates: %s", e);
                net.vibmc.world.structure.StructureRegistry.clear();
            }
        } else {
            net.vibmc.world.structure.StructureRegistry.clear();
        }
        this.worldManager = new WorldManager(config);
        this.pluginManager = new PluginManager();
        this.playerManager = new PlayerManager();
        this.networkServer = new NetworkServer();
        this.operatorManager = new OperatorManager(java.nio.file.Paths.get("ops.json"));
        this.commandManager = new CommandManager();
    }

    public static void main(String[] args) {
        // New JDKs warn that Netty's sun.misc.Unsafe allocation path is terminally deprecated.
        // Prefer the supported allocator path; this must be set before PacketEvents loads Netty.
        if (System.getProperty("io.netty.noUnsafe") == null) {
            System.setProperty("io.netty.noUnsafe", "true");
        }
        try {
            ServerConfig config = ServerConfig.load("server.properties");
            if (config.useLegacyProxyForwarding() && !config.onlineMode()) {
                throw new IOException("proxy-mode=legacy requires online-mode=true");
            }
            net.vibmc.registry.MinecraftDataRegistry.initialize();
            net.vibmc.network.packetevents.PacketEventsRuntime.initialize();
            new VibMC(config).start();
        } catch (IOException e) {
            System.err.println("Unable to start vib-MC: " + e.getMessage());
            System.exit(1);
        }
    }

    public static VibMC getInstance() {
        return instance;
    }

    public void start() {
        running = true;
        try {
            networkServer.start(config.address(), config.port());
        } catch (IOException e) {
            logger.severe("Failed to start network server: %s", e);
            running = false;
            return;
        }

        if (!config.onlineMode()) {
            logger.warn("Running in offline mode: player identities are not authenticated. Do not expose this server publicly.");
        } else if (config.useLegacyProxyForwarding()) {
            logger.info("Online mode is delegated to a trusted legacy-compatible BungeeCord/Velocity proxy at %s",
                    config.proxyTrustedAddress());
        } else {
            logger.info("Online mode enabled with encrypted Mojang session authentication");
        }
        pluginManager.loadPlugins("plugins");
        pluginManager.onLoad();
        pluginManager.onEnable();
        pluginsEnabled = true;
        commandManager.startConsole();

        tickThread = new Thread(() -> {
            try {
                tickLoop();
            } finally {
                if (running) logger.severe("Tick thread stopped while the server was still running");
            }
        }, "Server Tick");
        tickThread.setDaemon(true);
        tickThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "Shutdown Hook"));
        logger.info("vib-MC started on %s:%d (seed %d)", config.address(), config.port(),
                worldManager.getMainWorld().seed());

        try {
            while (running) {
                Thread.sleep(200);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            stop();
        }
    }

    private void tickLoop() {
        int autosaveInterval = config.autosaveIntervalTicks();
        while (running) {
            long start = System.currentTimeMillis();
            tickCounter++;
            try {
                drainMainThreadTasks(MAX_MAIN_THREAD_TASKS_PER_TICK);
                if (!running) break;
                pluginManager.fireTickStart();
                for (net.vibmc.world.World world : worldManager.getWorlds()) {
                    world.tick(tickCounter);
                }
                playerManager.tickAll();
                networkServer.tick();
                pluginManager.fireTickEnd();

                if (automaticSaving && autosaveInterval > 0 && tickCounter % autosaveInterval == 0) {
                    int saved = worldManager.saveAll();
                    int playersSaved = playerManager.saveAllPlayers();
                    if (saved > 0 || playersSaved > 0) {
                        logger.info("Autosaved %d chunk(s) and %d player(s)", saved, playersSaved);
                    }
                }
                if (tickCounter % 100 == 0) {
                    long keepAlive = System.currentTimeMillis();
                    for (ServerPlayer player : playerManager.getOnlinePlayers()) {
                        player.sendKeepAlive(keepAlive);
                    }
                }
            } catch (Throwable e) {
                // Catching Throwable rather than RuntimeException is deliberate: an Error
                // escaping here used to kill the tick thread outright, leaving the server
                // accepting connections while nothing in the world ever advanced again.
                logger.severe("Unhandled error during tick %d: %s", tickCounter, e);
            }

            long elapsed = System.currentTimeMillis() - start;
            try {
                Thread.sleep(Math.max(1, 50 - elapsed));
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    /**
     * Runs an action on the authoritative server tick thread. Packet listeners and the console
     * use this boundary before touching worlds, entities, inventories, or player collections.
     */
    public boolean executeOnMainThread(Runnable task) {
        if (task == null) throw new IllegalArgumentException("task cannot be null");
        Thread authoritativeThread = tickThread;
        if (Thread.currentThread() == authoritativeThread || (authoritativeThread == null && !running)) {
            task.run();
            return true;
        }
        int queued = queuedMainThreadTaskCount.incrementAndGet();
        if (queued > MAX_QUEUED_MAIN_THREAD_TASKS) {
            queuedMainThreadTaskCount.decrementAndGet();
            logger.warn("Rejected a main-thread task because the queue reached %d entries",
                    MAX_QUEUED_MAIN_THREAD_TASKS);
            return false;
        }
        mainThreadTasks.add(task);
        return true;
    }

    public boolean isMainThread() {
        return Thread.currentThread() == tickThread;
    }

    private void drainMainThreadTasks(int maximum) {
        for (int completed = 0; completed < maximum; completed++) {
            Runnable task = mainThreadTasks.poll();
            if (task == null) return;
            queuedMainThreadTaskCount.decrementAndGet();
            try {
                task.run();
            } catch (Throwable error) {
                logger.severe("Unhandled error in main-thread task: %s", error);
            }
        }
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        logger.info("Shutting down...");

        Thread thread = tickThread;
        if (thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
            try {
                thread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // The tick thread is stopped; complete work accepted before shutdown on this single
        // shutdown thread before network close callbacks perform their final cleanup.
        drainMainThreadTasks(Integer.MAX_VALUE);
        tickThread = null;

        networkServer.stop();
        net.vibmc.network.packetevents.PacketEventsRuntime.terminate();
        if (pluginsEnabled) {
            pluginManager.onDisable();
            pluginsEnabled = false;
        }
        if (automaticSaving) {
            int saved = worldManager.saveAll();
            logger.info("Saved %d chunk(s) to %s", saved,
                    worldManager.getMainWorld().storage().worldDir());
        } else {
            logger.warn("Automatic saving is disabled; shutdown did not save pending changes");
        }
        logger.info("vib-MC stopped");
    }

    public boolean isRunning() { return running; }
    public ServerConfig getConfig() { return config; }
    public Logger getLogger() { return logger; }
    public WorldManager getWorldManager() { return worldManager; }
    public PluginManager getPluginManager() { return pluginManager; }
    public PlayerManager getPlayerManager() { return playerManager; }
    public NetworkServer getNetworkServer() { return networkServer; }
    public CommandManager getCommandManager() { return commandManager; }
    public OperatorManager getOperatorManager() { return operatorManager; }
    public boolean isAutomaticSaving() { return automaticSaving; }
    public void setAutomaticSaving(boolean enabled) { automaticSaving = enabled; }
}
