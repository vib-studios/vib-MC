package net.vibmc.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PluginDescription {
    private final String name;
    private final String version;
    private final String main;
    private final List<String> authors;
    private final List<String> depends;
    private final String description;

    public PluginDescription(String name, String version, String main,
                             List<String> authors, List<String> depends, String description) {
        this.name = name;
        this.version = version;
        this.main = main;
        this.authors = Collections.unmodifiableList(new ArrayList<>(authors));
        this.depends = Collections.unmodifiableList(new ArrayList<>(depends));
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getMain() {
        return main;
    }

    public List<String> getAuthors() {
        return authors;
    }

    public List<String> getDepends() {
        return depends;
    }

    public String getDescription() {
        return description;
    }
}
