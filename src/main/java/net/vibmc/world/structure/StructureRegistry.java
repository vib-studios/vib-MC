package net.vibmc.world.structure;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import net.vibmc.world.*;
import net.vibmc.world.gen.TerrainGenerator;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Registry and placement engine for standalone, pooled, nested, and composite structures. */
public final class StructureRegistry {
    private static volatile Map<String,StructureTemplate> templates=Collections.emptyMap();
    private static volatile Map<String,StructurePool> pools=Collections.emptyMap();
    private static volatile Map<String,CompositeStructure> composites=Collections.emptyMap();
    private static final Map<String,int[]> placementCache=new ConcurrentHashMap<>();
    private StructureRegistry(){}

    public static void reload() throws IOException {
        StructureCatalogLoader.Catalog catalog=StructureCatalogLoader.load(Paths.get("structures"));
        templates=Collections.unmodifiableMap(new LinkedHashMap<>(catalog.templates));
        pools=Collections.unmodifiableMap(new LinkedHashMap<>(catalog.pools));
        composites=Collections.unmodifiableMap(new LinkedHashMap<>(catalog.composites));
        placementCache.clear();
    }
    public static void clear(){templates=Collections.emptyMap();pools=Collections.emptyMap();composites=Collections.emptyMap();placementCache.clear();}
    public static List<StructureTemplate> templates(){return Collections.unmodifiableList(new ArrayList<>(templates.values()));}
    public static Collection<CompositeStructure> composites(){return composites.values();}

    public static void decorate(WorldChunk chunk,World world){
        if(templates.isEmpty()&&composites.isEmpty())return;
        TerrainGenerator terrain=new TerrainGenerator(world.seed());
        decorateStandalone(chunk,world,terrain);
        for(CompositeStructure composite:composites.values())if(composite.environment()==world.environment())decorateComposite(chunk,world,terrain,composite);
    }

    public static int[] spawnPoint(World world){
        for(CompositeStructure composite:composites.values())if(composite.environment()==world.environment()){
            int[] center=placement(world,composite);
            return new int[]{center[0]+composite.spawnX(),center[1]+composite.spawnZ()};
        }
        return null;
    }

    private static void decorateStandalone(WorldChunk chunk,World world,TerrainGenerator terrain){
        int minX=chunk.chunkX()*16,minZ=chunk.chunkZ()*16,maxX=minX+15,maxZ=minZ+15;
        for(StructureTemplate template:templates.values()){
            if(!template.standalone()||template.environment()!=world.environment())continue;int spacing=template.spacing();
            int firstCellX=Math.floorDiv(minX-template.sizeX(),spacing),lastCellX=Math.floorDiv(maxX,spacing),firstCellZ=Math.floorDiv(minZ-template.sizeZ(),spacing),lastCellZ=Math.floorDiv(maxZ,spacing);
            for(int cellX=firstCellX;cellX<=lastCellX;cellX++)for(int cellZ=firstCellZ;cellZ<=lastCellZ;cellZ++){
                int hash=terrain.hash(cellX+template.salt(),cellZ-template.salt());if((hash&0xffff)/65535.0>template.chance())continue;
                int originX=cellX*spacing+Math.floorMod(hash>>>8,spacing),originZ=cellZ*spacing+Math.floorMod(hash>>>16,spacing);
                int anchorX=originX+template.anchorX(),anchorZ=originZ+template.anchorZ();String biome=world.biomeAt(anchorX,anchorZ);if(!template.allows(biome))continue;
                int ground=terrain.getHeight(anchorX,anchorZ);if(world.environment()==WorldEnvironment.OVERWORLD&&ground<=63)continue;
                if(intersectsExclusion(world,template.name(),originX,originZ,template.sizeX(),template.sizeZ()))continue;
                place(chunk,terrain,template,originX,ground+1,originZ,0,false);
            }
        }
    }

    private static void decorateComposite(WorldChunk chunk,World world,TerrainGenerator terrain,CompositeStructure composite){
        if(isCompositeExcluded(world,composite))return;
        int[] center=placement(world,composite);int nodeIndex=0;
        for(CompositeStructure.Node node:composite.nodes()){
            StructureTemplate template=resolve(node.reference,terrain.hash(composite.salt()+nodeIndex,(int)world.seed()),0);
            int[] rotatedAnchor=rotate(template.anchorX(),template.anchorZ(),template.sizeX(),template.sizeZ(),node.rotation);
            int targetX=center[0]+node.x,targetZ=center[1]+node.z;
            int originX=targetX-rotatedAnchor[0],originZ=targetZ-rotatedAnchor[1];
            int baseY=node.anchor==CompositeStructure.Anchor.TERRAIN?terrain.getHeight(targetX,targetZ)+node.y:node.y;
            place(chunk,terrain,template,originX,baseY,originZ,node.rotation,node.anchor==CompositeStructure.Anchor.SURFACE);nodeIndex++;
        }
    }

    private static void place(WorldChunk chunk,TerrainGenerator terrain,StructureTemplate template,int originX,int baseY,int originZ,int rotation,boolean surface){
        int normalized=Math.floorMod(rotation,360);if(normalized%90!=0)throw new IllegalArgumentException("rotation must be a multiple of 90");
        for(StructureTemplate.Entry entry:template.blocks()){
            int rx,rz;switch(normalized){case 90:rx=entry.z;rz=template.sizeX()-1-entry.x;break;case 180:rx=template.sizeX()-1-entry.x;rz=template.sizeZ()-1-entry.z;break;case 270:rx=template.sizeZ()-1-entry.z;rz=entry.x;break;default:rx=entry.x;rz=entry.z;}
            int worldX=originX+rx,worldZ=originZ+rz,worldY=(surface?terrain.getHeight(worldX,worldZ)+baseY:baseY)+entry.y;
            if(worldY<0||worldY>=256||Math.floorDiv(worldX,16)!=chunk.chunkX()||Math.floorDiv(worldZ,16)!=chunk.chunkZ())continue;
            WrappedBlockState existing=chunk.getBlock(Math.floorMod(worldX,16),worldY,Math.floorMod(worldZ,16));
            if(Blocks.same(entry.block,Blocks.LEAVES)&&!Blocks.same(existing,Blocks.AIR))continue;
            chunk.setBlock(Math.floorMod(worldX,16),worldY,Math.floorMod(worldZ,16),entry.block);
        }
    }

    private static int[] rotate(int x,int z,int sizeX,int sizeZ,int rotation){
        switch(Math.floorMod(rotation,360)){case 90:return new int[]{z,sizeX-1-x};case 180:return new int[]{sizeX-1-x,sizeZ-1-z};case 270:return new int[]{sizeZ-1-z,x};default:return new int[]{x,z};}
    }

    private static boolean intersectsExclusion(World world,String structureName,int minX,int minZ,int sizeX,int sizeZ){
        int maxX=minX+sizeX-1,maxZ=minZ+sizeZ-1;
        for(CompositeStructure owner:composites.values()){
            if(owner.environment()!=world.environment()||!owner.excludes(structureName))continue;
            int[] center=placement(world,owner);int radius=owner.footprintRadius();
            if(minX<=center[0]+radius&&maxX>=center[0]-radius&&minZ<=center[1]+radius&&maxZ>=center[1]-radius)return true;
        }
        return false;
    }

    private static boolean isCompositeExcluded(World world,CompositeStructure subject){
        int[] subjectCenter=placement(world,subject);int subjectRadius=subject.footprintRadius();
        for(CompositeStructure owner:composites.values()){
            if(owner==subject||owner.environment()!=world.environment()||!owner.excludes(subject.name()))continue;
            int[] ownerCenter=placement(world,owner);int ownerRadius=owner.footprintRadius();
            if(Math.abs(ownerCenter[0]-subjectCenter[0])<=ownerRadius+subjectRadius&&Math.abs(ownerCenter[1]-subjectCenter[1])<=ownerRadius+subjectRadius)return true;
        }
        return false;
    }

    private static StructureTemplate resolve(String reference,int selection,int depth){
        if(depth>8)throw new IllegalStateException("structure pool nesting exceeds 8 levels at "+reference);
        StructureTemplate direct=templates.get(reference);if(direct!=null)return direct;
        StructurePool pool=pools.get(reference);if(pool==null)throw new IllegalStateException("unknown structure reference "+reference);
        int total=0;for(StructurePool.Entry entry:pool.entries())total+=entry.weight;if(total<=0)throw new IllegalStateException("pool has no positive weight: "+reference);
        int choice=Math.floorMod(selection,total);for(StructurePool.Entry entry:pool.entries()){choice-=entry.weight;if(choice<0)return resolve(entry.reference,Integer.rotateLeft(selection*31+entry.reference.hashCode(),5),depth+1);}throw new AssertionError();
    }

    private static int[] placement(World world,CompositeStructure composite){
        String key=world.name()+":"+world.seed()+":"+composite.name();int[] cached=placementCache.get(key);if(cached!=null)return cached.clone();
        TerrainGenerator terrain=new TerrainGenerator(world.seed());int bestX=8,bestZ=8,bestScore=Integer.MAX_VALUE,radius=composite.searchRadius(),footprint=composite.footprintRadius();
        for(int z=8-radius;z<=8+radius;z+=4)for(int x=8-radius;x<=8+radius;x+=4){
            int center=terrain.getHeight(x,z),minimum=center,maximum=center,wetSamples=0;
            for(int sampleX=-footprint;sampleX<=footprint;sampleX+=8)for(int sampleZ=-footprint;sampleZ<=footprint;sampleZ+=8){int height=terrain.getHeight(x+sampleX,z+sampleZ);minimum=Math.min(minimum,height);maximum=Math.max(maximum,height);if(height<=63)wetSamples++;}
            int spawnHeight=terrain.getHeight(x+composite.spawnX(),z+composite.spawnZ());if(spawnHeight<=63)wetSamples+=20;
            int distance=(x-8)*(x-8)+(z-8)*(z-8),score=distance+(maximum-minimum)*32+wetSamples*5000;
            if(center>63&&score<bestScore){bestScore=score;bestX=x;bestZ=z;}
        }
        int[] result={bestX,bestZ};placementCache.put(key,result.clone());return result;
    }
}
