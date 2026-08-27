package net.vibmc.world.structure;

import net.vibmc.world.WorldEnvironment;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

final class StructureCatalogLoader {
    static final class Catalog {
        final Map<String,StructureTemplate> templates=new LinkedHashMap<>();
        final Map<String,StructurePool> pools=new LinkedHashMap<>();
        final Map<String,CompositeStructure> composites=new LinkedHashMap<>();
    }
    private StructureCatalogLoader(){}

    static Catalog load(Path external) throws IOException {
        Catalog catalog=new Catalog();
        for(StructureTemplate template:StructureLoader.load(external))catalog.templates.put(template.name(),template);
        try(InputStream index=StructureCatalogLoader.class.getResourceAsStream("/structures/index.txt")){
            if(index!=null)try(BufferedReader reader=new BufferedReader(new InputStreamReader(index,StandardCharsets.UTF_8))){String file;while((file=reader.readLine())!=null){file=file.trim();if(file.isEmpty()||file.startsWith("#")||file.endsWith(".vstruct"))continue;try(InputStream input=StructureCatalogLoader.class.getResourceAsStream("/structures/"+file)){if(input!=null)parse(file,input,catalog);}}}
        }
        Files.createDirectories(external);
        try(DirectoryStream<Path> files=Files.newDirectoryStream(external)){for(Path file:files){String name=file.getFileName().toString();if(!name.endsWith(".vpool")&&!name.endsWith(".vstructure"))continue;try(InputStream input=Files.newInputStream(file)){parse(name,input,catalog);}}}
        validate(catalog);
        return catalog;
    }

    private static void parse(String file,InputStream input,Catalog catalog)throws IOException{
        Map<String,List<String>> values=new LinkedHashMap<>();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null){line=line.trim();if(line.isEmpty()||line.startsWith("#"))continue;int split=line.indexOf('=');if(split<1)continue;values.computeIfAbsent(line.substring(0,split).trim(),key->new ArrayList<>()).add(line.substring(split+1).trim());}}
        String name=one(values,"name","unnamed");
        if(file.endsWith(".vpool")){
            List<StructurePool.Entry> entries=new ArrayList<>();for(String value:values.getOrDefault("entry",Collections.emptyList())){String[] parts=value.split(",");if(parts.length!=2)throw new IOException("Invalid pool entry in "+name);entries.add(new StructurePool.Entry(Integer.parseInt(parts[0].trim()),parts[1].trim()));}if(entries.isEmpty())throw new IOException("Empty structure pool "+name);catalog.pools.put(name,new StructurePool(name,entries));
        }else{
            WorldEnvironment environment=WorldEnvironment.valueOf(one(values,"dimension","overworld").toUpperCase(Locale.ROOT));
            int search=Integer.parseInt(one(values,"search-radius","48")),footprint=Integer.parseInt(one(values,"footprint-radius","20")),salt=Integer.parseInt(one(values,"salt","1"));
            int[] spawn=ints(one(values,"spawn-offset","0,0"),2);List<CompositeStructure.Node> nodes=new ArrayList<>();
            for(String value:values.getOrDefault("node",Collections.emptyList())){String[] p=value.split(",");if(p.length!=6)throw new IOException("Invalid composite node in "+name+": "+value);nodes.add(new CompositeStructure.Node(p[0].trim(),Integer.parseInt(p[1].trim()),Integer.parseInt(p[2].trim()),Integer.parseInt(p[3].trim()),CompositeStructure.Anchor.valueOf(p[4].trim().toUpperCase(Locale.ROOT)),Integer.parseInt(p[5].trim())));}
            Set<String> excluded=new LinkedHashSet<>();String excludedValue=one(values,"exclude-structures","");if(!excludedValue.isEmpty())for(String reference:excludedValue.split(","))if(!reference.trim().isEmpty())excluded.add(reference.trim());
            if(nodes.isEmpty())throw new IOException("Composite structure has no nodes: "+name);catalog.composites.put(name,new CompositeStructure(name,environment,search,footprint,spawn[0],spawn[1],salt,nodes,excluded));
        }
    }

    private static void validate(Catalog catalog)throws IOException{
        for(StructurePool pool:catalog.pools.values())for(StructurePool.Entry entry:pool.entries())if(!catalog.templates.containsKey(entry.reference)&&!catalog.pools.containsKey(entry.reference))throw new IOException("Unknown structure reference "+entry.reference+" in pool "+pool.name());
        for(CompositeStructure composite:catalog.composites.values()){
            for(CompositeStructure.Node node:composite.nodes())if(!catalog.templates.containsKey(node.reference)&&!catalog.pools.containsKey(node.reference))throw new IOException("Unknown structure reference "+node.reference+" in "+composite.name());
            for(String excluded:composite.excludedStructures())if(!catalog.templates.containsKey(excluded)&&!catalog.composites.containsKey(excluded))throw new IOException("Unknown excluded structure "+excluded+" in "+composite.name());
        }
    }
    private static String one(Map<String,List<String>> values,String key,String fallback){List<String> found=values.get(key);return found==null||found.isEmpty()?fallback:found.get(found.size()-1);}
    private static int[] ints(String value,int count)throws IOException{String[] parts=value.split(",");if(parts.length!=count)throw new IOException("Expected "+count+" integers: "+value);int[] result=new int[count];for(int i=0;i<count;i++)result[i]=Integer.parseInt(parts[i].trim());return result;}
}
