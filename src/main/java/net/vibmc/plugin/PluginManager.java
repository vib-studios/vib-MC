package net.vibmc.plugin;

import net.vibmc.permission.PermissionManager;
import net.vibmc.plugin.event.*;
import net.vibmc.server.VibMC;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class PluginManager {
    private final List<VibMCPlugin> plugins;
    private final Map<Class<? extends Event>, List<RegisteredListener>> listeners;
    private final PermissionManager permissionManager;
    private final Map<VibMCPlugin, URLClassLoader> classLoaders;

    public PluginManager() {
        this.plugins = new CopyOnWriteArrayList<>();
        this.listeners = new ConcurrentHashMap<>();
        this.permissionManager = new PermissionManager();
        this.classLoaders = new ConcurrentHashMap<>();
    }

    public void loadPlugins(String directory) {
        File pluginDir = new File(directory);
        if (!pluginDir.exists() && !pluginDir.mkdirs()) {
            VibMC.getInstance().getLogger().warn("Could not create plugin directory: %s", pluginDir);
            return;
        }
        if (!pluginDir.isDirectory()) {
            VibMC.getInstance().getLogger().warn("Plugin path is not a directory: %s", pluginDir);
            return;
        }

        File[] files = pluginDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (files == null) {
            VibMC.getInstance().getLogger().warn("Could not list plugin directory: %s", pluginDir);
            return;
        }
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        for (File file : files) {
            try {
                loadPlugin(file);
            } catch (Exception e) {
                VibMC.getInstance().getLogger().severe("Failed to load plugin from %s: %s", file.getName(), e);
            }
        }
    }

    private void loadPlugin(File file) throws Exception {
        URLClassLoader loader = new URLClassLoader(
                new URL[]{file.toURI().toURL()}, getClass().getClassLoader());
        boolean retained = false;
        try {
            PluginDescription description;
            try (InputStream stream = loader.getResourceAsStream("plugin.yml")) {
                if (stream == null) {
                    VibMC.getInstance().getLogger().warn("Plugin %s has no plugin.yml", file.getName());
                    return;
                }
                description = loadDescription(stream);
            }

            if (description == null) {
                VibMC.getInstance().getLogger().warn("Invalid plugin.yml in %s", file.getName());
                return;
            }
            if (getPlugin(description.getName()) != null) {
                VibMC.getInstance().getLogger().warn(
                        "Duplicate plugin name %s in %s", description.getName(), file.getName());
                return;
            }

            Class<?> mainClass = loader.loadClass(description.getMain());
            if (!VibMCPlugin.class.isAssignableFrom(mainClass)) {
                VibMC.getInstance().getLogger().warn(
                        "Plugin %s main class does not extend VibMCPlugin", file.getName());
                return;
            }

            VibMCPlugin plugin = (VibMCPlugin) mainClass.getDeclaredConstructor().newInstance();
            plugin.setDescription(description);
            plugin.setDataFolder(new File(file.getParentFile(), description.getName()));
            plugin.setPluginFile(file);
            plugins.add(plugin);
            classLoaders.put(plugin, loader);
            retained = true;
            VibMC.getInstance().getLogger().info(
                    "Loaded plugin %s v%s", description.getName(), description.getVersion());
        } finally {
            if (!retained) {
                loader.close();
            }
        }
    }

    private PluginDescription loadDescription(InputStream stream) {
        try {
            Properties props = new Properties();
            props.load(stream);
            String name = trimToNull(props.getProperty("name"));
            String version = trimToNull(props.getProperty("version"));
            String main = trimToNull(props.getProperty("main"));
            String description = props.getProperty("description", "").trim();
            List<String> authors = parseList(props.getProperty("authors", props.getProperty("author", "")));
            List<String> depends = parseList(props.getProperty("depends", ""));
            if (name != null && version != null && main != null) {
                return new PluginDescription(name, version, main, authors, depends, description);
            }
        } catch (Exception e) {
            VibMC.getInstance().getLogger().warn("Error reading plugin.yml: %s", e.getMessage());
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2
                && ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'")))) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static List<String> parseList(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (normalized.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (String part : normalized.split(",")) {
            String item = trimToNull(part);
            if (item != null) {
                values.add(item);
            }
        }
        return values;
    }

    public void onLoad() {
        for (VibMCPlugin plugin : new ArrayList<>(plugins)) {
            try {
                plugin.onLoad();
            } catch (Exception e) {
                VibMC.getInstance().getLogger().severe("Error loading plugin %s: %s", plugin.getName(), e);
                unloadPlugin(plugin);
            }
        }
    }

    public void onEnable() {
        List<VibMCPlugin> remaining = new ArrayList<>(plugins);
        boolean progressed;
        do {
            progressed = false;
            Iterator<VibMCPlugin> iterator = remaining.iterator();
            while (iterator.hasNext()) {
                VibMCPlugin plugin = iterator.next();
                if (!dependenciesEnabled(plugin)) {
                    continue;
                }
                try {
                    File dataFolder = plugin.getDataFolder();
                    if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                        throw new IllegalStateException("Could not create data folder " + dataFolder);
                    }
                    plugin.onEnable();
                    plugin.setEnabled(true);
                    VibMC.getInstance().getLogger().info(
                            "Enabled plugin %s v%s", plugin.getName(), plugin.getVersion());
                } catch (Exception e) {
                    VibMC.getInstance().getLogger().severe("Error enabling plugin %s: %s", plugin.getName(), e);
                }
                iterator.remove();
                progressed = true;
            }
        } while (progressed && !remaining.isEmpty());

        for (VibMCPlugin plugin : remaining) {
            VibMC.getInstance().getLogger().warn(
                    "Did not enable plugin %s; dependency missing, disabled, or cyclic: %s",
                    plugin.getName(), plugin.getDescription().getDepends());
        }
    }

    private boolean dependenciesEnabled(VibMCPlugin plugin) {
        for (String dependencyName : plugin.getDescription().getDepends()) {
            VibMCPlugin dependency = getPlugin(dependencyName);
            if (dependency == null || !dependency.isEnabled()) {
                return false;
            }
        }
        return true;
    }

    public void onDisable() {
        for (int i = plugins.size() - 1; i >= 0; i--) {
            VibMCPlugin plugin = plugins.get(i);
            if (!plugin.isEnabled()) {
                continue;
            }
            try {
                plugin.onDisable();
            } catch (Exception e) {
                VibMC.getInstance().getLogger().severe("Error disabling plugin %s: %s", plugin.getName(), e);
            } finally {
                plugin.setEnabled(false);
            }
        }
        listeners.clear();
        for (VibMCPlugin plugin : new ArrayList<>(plugins)) {
            unloadPlugin(plugin);
        }
    }

    private void unloadPlugin(VibMCPlugin plugin) {
        plugins.remove(plugin);
        URLClassLoader loader = classLoaders.remove(plugin);
        if (loader != null) {
            try {
                loader.close();
            } catch (java.io.IOException e) {
                VibMC.getInstance().getLogger().warn("Could not close plugin %s: %s", plugin.getName(), e);
            }
        }
    }

    public void registerEvents(Listener listener, VibMCPlugin plugin) {
        for (java.lang.reflect.Method method : listener.getClass().getMethods()) {
            EventHandler annotation = method.getAnnotation(EventHandler.class);
            if (annotation == null) continue;

            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1) continue;

            if (Event.class.isAssignableFrom(params[0])) {
                @SuppressWarnings("unchecked")
                Class<? extends Event> eventClass = (Class<? extends Event>) params[0];
                listeners.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>())
                        .add(new RegisteredListener(listener, method, plugin, annotation.priority()));
                sortListeners(eventClass);
            }
        }
    }

    private void sortListeners(Class<? extends Event> eventClass) {
        List<RegisteredListener> list = listeners.get(eventClass);
        if (list == null) return;
        list.sort(Comparator.comparingInt(listener -> listener.getPriority().value()));
    }

    public void fireEvent(Event event) {
        List<RegisteredListener> list = listeners.get(event.getClass());
        if (list == null) return;
        for (RegisteredListener listener : list) {
            if (event instanceof Cancellable && ((Cancellable) event).isCancelled() && listener.isIgnoringCancelled()) {
                continue;
            }
            try {
                listener.getMethod().invoke(listener.getListener(), event);
            } catch (Exception e) {
                VibMC.getInstance().getLogger().severe("Error firing event %s: %s",
                    event.getClass().getSimpleName(), e);
            }
        }
    }

    public void fireTickStart() {
        fireEvent(new TickEvent.Start());
    }

    public void fireTickEnd() {
        fireEvent(new TickEvent.End());
    }

    public PermissionManager getPermissionManager() { return permissionManager; }

    public List<VibMCPlugin> getPlugins() {
        return Collections.unmodifiableList(plugins);
    }

    public VibMCPlugin getPlugin(String name) {
        for (VibMCPlugin plugin : plugins) {
            if (plugin.getName().equalsIgnoreCase(name)) return plugin;
        }
        return null;
    }
}
