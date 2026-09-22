package net.vibmc.registry;

import com.github.retrooper.packetevents.protocol.nbt.*;
import com.viaversion.nbt.tag.Tag;
import com.viaversion.nbt.tag.basic.*;
import com.viaversion.nbt.tag.collection.*;
import com.viaversion.nbt.tag.compound.CompoundTag;

import java.util.Map;

/** Converts ViaNBT tags (com.viaversion.nbt) into PacketEvents NBT objects and vice-versa. */
public final class NbtMapper {
    private NbtMapper() {}

    public static NBT toPacketEvents(Tag tag) {
        if (tag == null) return null;
        if (tag instanceof CompoundTag) return toPacketEventsCompound((CompoundTag) tag);
        if (tag instanceof ListTag) return toPacketEventsList((ListTag<?>) tag);
        if (tag instanceof ByteTag) return new NBTByte(((ByteTag) tag).getValue());
        if (tag instanceof ShortTag) return new NBTShort(((ShortTag) tag).getValue());
        if (tag instanceof IntTag) return new NBTInt(((IntTag) tag).getValue());
        if (tag instanceof LongTag) return new NBTLong(((LongTag) tag).getValue());
        if (tag instanceof FloatTag) return new NBTFloat(((FloatTag) tag).getValue());
        if (tag instanceof DoubleTag) return new NBTDouble(((DoubleTag) tag).getValue());
        if (tag instanceof StringTag) return new NBTString(((StringTag) tag).getValue());
        if (tag instanceof ByteArrayTag) return new NBTByteArray(((ByteArrayTag) tag).getValue());
        if (tag instanceof IntArrayTag) return new NBTIntArray(((IntArrayTag) tag).getValue());
        if (tag instanceof LongArrayTag) return new NBTLongArray(((LongArrayTag) tag).getValue());
        throw new IllegalArgumentException("Unsupported ViaNBT tag: " + tag.getClass().getName());
    }

    public static NBTCompound toPacketEventsCompound(CompoundTag compoundTag) {
        if (compoundTag == null) return null;
        NBTCompound compound = new NBTCompound();
        for (Map.Entry<String, Tag> entry : compoundTag.entrySet()) {
            compound.setTag(entry.getKey(), toPacketEvents(entry.getValue()));
        }
        return compound;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static NBTList<?> toPacketEventsList(ListTag<?> listTag) {
        if (listTag == null) return null;
        NBTType<?> peType = toPacketEventsType(listTag.getElementType());
        NBTList peList = new NBTList(peType);
        for (Tag elem : listTag) {
            peList.addTag(toPacketEvents(elem));
        }
        return peList;
    }

    public static Tag fromPacketEvents(NBT nbt) {
        if (nbt == null) return null;
        if (nbt instanceof NBTCompound) {
            NBTCompound peCompound = (NBTCompound) nbt;
            CompoundTag compoundTag = new CompoundTag();
            for (Map.Entry<String, NBT> entry : peCompound.getTags().entrySet()) {
                compoundTag.put(entry.getKey(), fromPacketEvents(entry.getValue()));
            }
            return compoundTag;
        }
        if (nbt instanceof NBTList) {
            NBTList<?> peList = (NBTList<?>) nbt;
            Class<? extends Tag> elemType = fromPacketEventsType(peList.getType());
            @SuppressWarnings({"rawtypes", "unchecked"})
            ListTag listTag = new ListTag(elemType);
            for (Object elem : peList.getTags()) {
                listTag.add(fromPacketEvents((NBT) elem));
            }
            return listTag;
        }
        if (nbt instanceof NBTByte) return new ByteTag(((NBTByte) nbt).getValue());
        if (nbt instanceof NBTShort) return new ShortTag(((NBTShort) nbt).getValue());
        if (nbt instanceof NBTInt) return new IntTag(((NBTInt) nbt).getValue());
        if (nbt instanceof NBTLong) return new LongTag(((NBTLong) nbt).getValue());
        if (nbt instanceof NBTFloat) return new FloatTag(((NBTFloat) nbt).getValue());
        if (nbt instanceof NBTDouble) return new DoubleTag(((NBTDouble) nbt).getValue());
        if (nbt instanceof NBTString) return new StringTag(((NBTString) nbt).getValue());
        if (nbt instanceof NBTByteArray) return new ByteArrayTag(((NBTByteArray) nbt).getValue());
        if (nbt instanceof NBTIntArray) return new IntArrayTag(((NBTIntArray) nbt).getValue());
        if (nbt instanceof NBTLongArray) return new LongArrayTag(((NBTLongArray) nbt).getValue());
        throw new IllegalArgumentException("Unsupported PacketEvents NBT: " + nbt.getClass().getName());
    }

    private static NBTType<?> toPacketEventsType(Class<? extends Tag> viaType) {
        if (viaType == null) return NBTType.END;
        if (CompoundTag.class.isAssignableFrom(viaType)) return NBTType.COMPOUND;
        if (ListTag.class.isAssignableFrom(viaType)) return NBTType.LIST;
        if (ByteTag.class.isAssignableFrom(viaType)) return NBTType.BYTE;
        if (ShortTag.class.isAssignableFrom(viaType)) return NBTType.SHORT;
        if (IntTag.class.isAssignableFrom(viaType)) return NBTType.INT;
        if (LongTag.class.isAssignableFrom(viaType)) return NBTType.LONG;
        if (FloatTag.class.isAssignableFrom(viaType)) return NBTType.FLOAT;
        if (DoubleTag.class.isAssignableFrom(viaType)) return NBTType.DOUBLE;
        if (StringTag.class.isAssignableFrom(viaType)) return NBTType.STRING;
        if (ByteArrayTag.class.isAssignableFrom(viaType)) return NBTType.BYTE_ARRAY;
        if (IntArrayTag.class.isAssignableFrom(viaType)) return NBTType.INT_ARRAY;
        if (LongArrayTag.class.isAssignableFrom(viaType)) return NBTType.LONG_ARRAY;
        return NBTType.END;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Tag> fromPacketEventsType(NBTType<?> peType) {
        if (peType == NBTType.COMPOUND) return CompoundTag.class;
        if (peType == NBTType.LIST) return ListTag.class;
        if (peType == NBTType.BYTE) return ByteTag.class;
        if (peType == NBTType.SHORT) return ShortTag.class;
        if (peType == NBTType.INT) return IntTag.class;
        if (peType == NBTType.LONG) return LongTag.class;
        if (peType == NBTType.FLOAT) return FloatTag.class;
        if (peType == NBTType.DOUBLE) return DoubleTag.class;
        if (peType == NBTType.STRING) return StringTag.class;
        if (peType == NBTType.BYTE_ARRAY) return ByteArrayTag.class;
        if (peType == NBTType.INT_ARRAY) return IntArrayTag.class;
        if (peType == NBTType.LONG_ARRAY) return LongArrayTag.class;
        return Tag.class;
    }
}
