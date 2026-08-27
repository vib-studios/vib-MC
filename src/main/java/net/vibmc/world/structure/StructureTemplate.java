package net.vibmc.world.structure;

import net.vibmc.world.Blocks;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import net.vibmc.world.WorldEnvironment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Immutable palette-based structure plus deterministic placement settings. */
public final class StructureTemplate {
    public static final class Entry {
        public final int x, y, z;
        public final WrappedBlockState block;
        Entry(int x, int y, int z, WrappedBlockState block) { this.x=x; this.y=y; this.z=z; this.block=block; }
    }

    private final String name;
    private final int sizeX, sizeY, sizeZ, spacing, salt, anchorX, anchorZ;
    private final double chance;
    private final boolean standalone;
    private final WorldEnvironment environment;
    private final Set<String> includedBiomes;
    private final Set<String> excludedBiomes;
    private final List<Entry> blocks;

    StructureTemplate(String name, int sizeX, int sizeY, int sizeZ, int spacing, int salt,
                      int anchorX, int anchorZ, double chance, boolean standalone,
                      WorldEnvironment environment, Set<String> includedBiomes,
                      Set<String> excludedBiomes, List<Entry> blocks) {
        this.name=name; this.sizeX=sizeX; this.sizeY=sizeY; this.sizeZ=sizeZ;
        this.spacing=spacing; this.salt=salt; this.anchorX=anchorX; this.anchorZ=anchorZ;
        this.chance=chance; this.standalone=standalone; this.environment=environment;
        this.includedBiomes=includedBiomes.isEmpty()?Collections.emptySet():Collections.unmodifiableSet(new java.util.LinkedHashSet<>(includedBiomes));
        this.excludedBiomes=excludedBiomes.isEmpty()?Collections.emptySet():Collections.unmodifiableSet(new java.util.LinkedHashSet<>(excludedBiomes));
        this.blocks=Collections.unmodifiableList(new ArrayList<>(blocks));
    }
    public String name(){return name;} public int sizeX(){return sizeX;} public int sizeY(){return sizeY;}
    public int sizeZ(){return sizeZ;} public int spacing(){return spacing;} public int salt(){return salt;}
    public int anchorX(){return anchorX;} public int anchorZ(){return anchorZ;}
    public double chance(){return chance;} public boolean standalone(){return standalone;}
    public WorldEnvironment environment(){return environment;}
    public List<Entry> blocks(){return blocks;}
    public boolean allows(String biome){return (includedBiomes.isEmpty()||includedBiomes.contains(biome))&&!excludedBiomes.contains(biome);}

    static WrappedBlockState blockByName(String name) {
        String n=name.trim().toLowerCase().replace("minecraft:","");
        switch(n){
            case "oak_log": case "log": return Blocks.WOOD;
            case "oak_planks": case "planks": return Blocks.OAK_PLANKS;
            case "oak_leaves": case "leaves": return Blocks.LEAVES;
            case "glass": return Blocks.GLASS;
            case "crafting_table": return Blocks.CRAFTING_TABLE;
            case "furnace": return Blocks.FURNACE;
            case "water": return Blocks.WATER;
            case "stone": case "cobblestone": return Blocks.STONE;
            case "dirt": return Blocks.DIRT; case "grass": case "grass_block": return Blocks.GRASS;
            case "sand": return Blocks.SAND; case "gravel": return Blocks.GRAVEL;
            case "obsidian": return Blocks.OBSIDIAN; case "netherrack": return Blocks.NETHERRACK;
            case "soul_sand": return Blocks.SOUL_SAND; case "glowstone": return Blocks.GLOWSTONE;
            case "end_stone": return Blocks.END_STONE; case "air": return Blocks.AIR;
            default: throw new IllegalArgumentException("Unknown structure block: "+name);
        }
    }
}
