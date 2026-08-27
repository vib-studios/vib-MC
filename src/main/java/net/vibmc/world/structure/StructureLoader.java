package net.vibmc.world.structure;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;

import net.vibmc.world.WorldEnvironment;
import java.util.LinkedHashSet;
import java.util.Set;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads palette-and-position templates inspired by vanilla structure templates. */
public final class StructureLoader {
    private StructureLoader() {}

    public static List<StructureTemplate> load(Path externalDirectory) throws IOException {
        Map<String, StructureTemplate> templates=new LinkedHashMap<>();
        try(InputStream index=StructureLoader.class.getResourceAsStream("/structures/index.txt")){
            if(index!=null) try(BufferedReader r=new BufferedReader(new InputStreamReader(index,StandardCharsets.UTF_8))){
                String file; while((file=r.readLine())!=null){file=file.trim();if(file.isEmpty()||file.startsWith("#"))continue;
                    if(!file.endsWith(".vstruct"))continue;try(InputStream in=StructureLoader.class.getResourceAsStream("/structures/"+file)){if(in!=null){StructureTemplate t=parse(in);templates.put(t.name(),t);}}
                }
            }
        }
        Files.createDirectories(externalDirectory);
        try(DirectoryStream<Path> files=Files.newDirectoryStream(externalDirectory,"*.vstruct")){
            for(Path file:files)try(InputStream in=Files.newInputStream(file)){StructureTemplate t=parse(in);templates.put(t.name(),t);}
        }
        return new ArrayList<>(templates.values());
    }

    static StructureTemplate parse(InputStream input) throws IOException {
        Map<Integer,WrappedBlockState> palette=new LinkedHashMap<>(); List<String> commands=new ArrayList<>();
        String name="unnamed"; int sx=1,sy=1,sz=1,spacing=8,salt=1,anchorX=0,anchorZ=0; double chance=1; boolean standalone=true; WorldEnvironment env=WorldEnvironment.OVERWORLD; Set<String> included=new LinkedHashSet<>(),excluded=new LinkedHashSet<>();
        try(BufferedReader r=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))){String line;
            while((line=r.readLine())!=null){line=line.trim();if(line.isEmpty()||line.startsWith("#"))continue;int eq=line.indexOf('=');if(eq<1)continue;
                String key=line.substring(0,eq).trim(),value=line.substring(eq+1).trim();
                if(key.equals("name"))name=value; else if(key.equals("size")){int[]v=ints(value,3);sx=v[0];sy=v[1];sz=v[2];}
                else if(key.equals("spacing"))spacing=Integer.parseInt(value);else if(key.equals("salt"))salt=Integer.parseInt(value);
                else if(key.equals("chance"))chance=Double.parseDouble(value);else if(key.equals("standalone"))standalone=Boolean.parseBoolean(value);else if(key.equals("dimension"))env=WorldEnvironment.valueOf(value.toUpperCase());
                else if(key.equals("anchor")){int[]v=ints(value,3);anchorX=v[0];anchorZ=v[2];}
                else if(key.equals("biomes"))parseBiomes(value,included); else if(key.equals("exclude-biomes"))parseBiomes(value,excluded);
                else if(key.startsWith("palette."))palette.put(Integer.parseInt(key.substring(8)),StructureTemplate.blockByName(value));
                else if(key.equals("block"))commands.add("B,"+value);
                else if(key.equals("fill"))commands.add("F,"+value);
            }
        }
        List<StructureTemplate.Entry> blocks=new ArrayList<>();for(String command:commands){if(command.startsWith("B,")){int[]v=ints(command.substring(2),4);WrappedBlockState b=palette.get(v[3]);if(b==null)throw new IOException("Unknown palette state "+v[3]);blocks.add(new StructureTemplate.Entry(v[0],v[1],v[2],b));}else{int[]v=ints(command.substring(2),7);WrappedBlockState b=palette.get(v[6]);if(b==null)throw new IOException("Unknown palette state "+v[6]);int minX=Math.min(v[0],v[3]),maxX=Math.max(v[0],v[3]),minY=Math.min(v[1],v[4]),maxY=Math.max(v[1],v[4]),minZ=Math.min(v[2],v[5]),maxZ=Math.max(v[2],v[5]);for(int x=minX;x<=maxX;x++)for(int y=minY;y<=maxY;y++)for(int z=minZ;z<=maxZ;z++)blocks.add(new StructureTemplate.Entry(x,y,z,b));}}
        if(spacing<1||chance<0||chance>1)throw new IOException("Invalid placement settings for "+name);
        return new StructureTemplate(name,sx,sy,sz,spacing,salt,anchorX,anchorZ,chance,standalone,env,included,excluded,blocks);
    }
    private static void parseBiomes(String value,Set<String> target)throws IOException{
        for(String part:value.split(",")){
            String name=part.trim().toLowerCase(java.util.Locale.ROOT);
            if(name.equals("hell"))name="nether_wastes";else if(name.equals("sky"))name="the_end";
            String biome=name.contains(":")?name:"minecraft:"+name;
            com.github.retrooper.packetevents.protocol.world.biome.Biome known=
                    com.github.retrooper.packetevents.protocol.world.biome.Biomes.getRegistry().getByName(biome);
            if(known==null)throw new IOException("Unknown biome: "+part);
            target.add(biome);
        }
    }
    private static int[] ints(String value,int count)throws IOException{String[]p=value.split(",");if(p.length!=count)throw new IOException("Expected "+count+" integers: "+value);int[]r=new int[count];for(int i=0;i<count;i++)r[i]=Integer.parseInt(p[i].trim());return r;}
}
