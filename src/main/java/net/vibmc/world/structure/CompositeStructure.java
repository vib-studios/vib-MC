package net.vibmc.world.structure;

import net.vibmc.world.WorldEnvironment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Data-driven arrangement of nested template/pool nodes with deterministic near-spawn placement. */
public final class CompositeStructure {
    public enum Anchor { TERRAIN, SURFACE }
    public static final class Node {
        final String reference;
        final int x,y,z,rotation;
        final Anchor anchor;
        Node(String reference,int x,int y,int z,Anchor anchor,int rotation){this.reference=reference;this.x=x;this.y=y;this.z=z;this.anchor=anchor;this.rotation=rotation;}
    }
    private final String name;
    private final WorldEnvironment environment;
    private final int searchRadius, footprintRadius, spawnX, spawnZ, salt;
    private final List<Node> nodes;
    private final Set<String> excludedStructures;
    CompositeStructure(String name,WorldEnvironment environment,int searchRadius,int footprintRadius,
                       int spawnX,int spawnZ,int salt,List<Node> nodes,Set<String> excludedStructures){this.name=name;this.environment=environment;this.searchRadius=searchRadius;this.footprintRadius=footprintRadius;this.spawnX=spawnX;this.spawnZ=spawnZ;this.salt=salt;this.nodes=Collections.unmodifiableList(new ArrayList<>(nodes));this.excludedStructures=Collections.unmodifiableSet(new LinkedHashSet<>(excludedStructures));}
    public String name(){return name;} public WorldEnvironment environment(){return environment;}
    int searchRadius(){return searchRadius;} int footprintRadius(){return footprintRadius;}
    int spawnX(){return spawnX;} int spawnZ(){return spawnZ;} int salt(){return salt;}
    List<Node> nodes(){return nodes;}
    boolean excludes(String structureName){return excludedStructures.contains(structureName);}
    Set<String> excludedStructures(){return excludedStructures;}
}
