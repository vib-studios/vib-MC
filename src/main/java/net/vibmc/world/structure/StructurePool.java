package net.vibmc.world.structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Weighted references to templates or other pools; recursive resolution enables nested pieces. */
public final class StructurePool {
    public static final class Entry {
        final int weight;
        final String reference;
        Entry(int weight, String reference) { this.weight=weight; this.reference=reference; }
    }
    private final String name;
    private final List<Entry> entries;
    StructurePool(String name,List<Entry> entries){this.name=name;this.entries=Collections.unmodifiableList(new ArrayList<>(entries));}
    public String name(){return name;}
    List<Entry> entries(){return entries;}
}
