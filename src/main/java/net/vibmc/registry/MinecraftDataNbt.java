package net.vibmc.registry;

import com.github.retrooper.packetevents.protocol.nbt.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;

/** Converts minecraft-protocol's typed JSON NBT representation into PacketEvents NBT. */
public final class MinecraftDataNbt {
    private MinecraftDataNbt(){}

    public static NBT decode(JsonElement typed){
        if(typed==null||!typed.isJsonObject())throw new IllegalArgumentException("Typed NBT must be an object");
        JsonObject object=typed.getAsJsonObject();
        String type=object.get("type").getAsString();
        JsonElement value=object.get("value");
        switch(type){
            case "byte":return new NBTByte(value.getAsByte());
            case "short":return new NBTShort(value.getAsShort());
            case "int":return new NBTInt(value.getAsInt());
            case "long":return new NBTLong(longValue(value));
            case "float":return new NBTFloat(value.getAsFloat());
            case "double":return new NBTDouble(value.getAsDouble());
            case "string":return new NBTString(value.getAsString());
            case "byteArray":return new NBTByteArray(byteArray(value));
            case "intArray":return new NBTIntArray(intArray(value));
            case "longArray":return new NBTLongArray(longArray(value));
            case "compound":return compound(value.getAsJsonObject());
            case "list":return list(value.getAsJsonObject());
            default:throw new IllegalArgumentException("Unsupported minecraft-data NBT type "+type);
        }
    }

    private static long longValue(JsonElement value){
        if(!value.isJsonArray())return value.getAsLong();
        if(value.getAsJsonArray().size()!=2)throw new IllegalArgumentException("NBT long tuple must contain two integers");
        long high=value.getAsJsonArray().get(0).getAsInt();
        long low=value.getAsJsonArray().get(1).getAsInt()&0xffffffffL;
        return high<<32|low;
    }

    private static byte[] byteArray(JsonElement value){
        byte[] result=new byte[value.getAsJsonArray().size()];
        for(int i=0;i<result.length;i++)result[i]=value.getAsJsonArray().get(i).getAsByte();
        return result;
    }

    private static int[] intArray(JsonElement value){
        int[] result=new int[value.getAsJsonArray().size()];
        for(int i=0;i<result.length;i++)result[i]=value.getAsJsonArray().get(i).getAsInt();
        return result;
    }

    private static long[] longArray(JsonElement value){
        long[] result=new long[value.getAsJsonArray().size()];
        for(int i=0;i<result.length;i++)result[i]=longValue(value.getAsJsonArray().get(i));
        return result;
    }

    private static NBTCompound compound(JsonObject values){
        NBTCompound result=new NBTCompound();
        for(Map.Entry<String,JsonElement> entry:values.entrySet())result.setTag(entry.getKey(),decode(entry.getValue()));
        return result;
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static NBTList<?> list(JsonObject list){
        String elementType=list.get("type").getAsString();
        NBTType type=type(elementType);
        NBTList result=new NBTList(type);
        if("end".equals(elementType))return result;
        for(JsonElement value:list.getAsJsonArray("value")){
            JsonObject wrapped=new JsonObject();wrapped.addProperty("type",elementType);wrapped.add("value",value);
            result.addTag(decode(wrapped));
        }
        return result;
    }

    private static NBTType<?> type(String type){
        switch(type){
            case "end":return NBTType.END;
            case "byte":return NBTType.BYTE;
            case "short":return NBTType.SHORT;
            case "int":return NBTType.INT;
            case "long":return NBTType.LONG;
            case "float":return NBTType.FLOAT;
            case "double":return NBTType.DOUBLE;
            case "string":return NBTType.STRING;
            case "byteArray":return NBTType.BYTE_ARRAY;
            case "intArray":return NBTType.INT_ARRAY;
            case "longArray":return NBTType.LONG_ARRAY;
            case "compound":return NBTType.COMPOUND;
            case "list":return NBTType.LIST;
            default:throw new IllegalArgumentException("Unsupported minecraft-data list type "+type);
        }
    }
}
