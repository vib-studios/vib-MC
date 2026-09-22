package net.vibmc.registry;

import com.github.retrooper.packetevents.protocol.nbt.*;
import com.viaversion.nbt.tag.basic.*;
import com.viaversion.nbt.tag.collection.*;
import com.viaversion.nbt.tag.compound.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NbtMapperTest {
    @Test
    void roundTripCompoundTag() {
        CompoundTag viaComp = new CompoundTag();
        viaComp.put("str", new StringTag("hello"));
        viaComp.put("int", new IntTag(42));
        viaComp.put("byte", new ByteTag((byte) 1));
        viaComp.put("float", new FloatTag(3.14f));
        viaComp.put("double", new DoubleTag(2.718));
        viaComp.put("long", new LongTag(100L));
        viaComp.put("bytes", new ByteArrayTag(new byte[]{1, 2, 3}));
        viaComp.put("ints", new IntArrayTag(new int[]{10, 20, 30}));

        ListTag<StringTag> list = new ListTag<>(StringTag.class);
        list.add(new StringTag("a"));
        list.add(new StringTag("b"));
        viaComp.put("list", list);

        NBTCompound peComp = NbtMapper.toPacketEventsCompound(viaComp);
        assertNotNull(peComp);
        assertEquals("hello", peComp.getStringTagValueOrNull("str"));
        assertEquals(42, peComp.getNumberTagValueOrNull("int").intValue());
        assertEquals(1, peComp.getNumberTagValueOrNull("byte").byteValue());
        assertEquals(3.14f, peComp.getNumberTagValueOrNull("float").floatValue(), 0.001f);
        assertEquals(2.718, peComp.getNumberTagValueOrNull("double").doubleValue(), 0.001);
        assertEquals(100L, peComp.getNumberTagValueOrNull("long").longValue());

        CompoundTag viaBack = (CompoundTag) NbtMapper.fromPacketEvents(peComp);
        assertNotNull(viaBack);
        assertEquals("hello", viaBack.getString("str"));
        assertEquals(42, viaBack.getInt("int"));
    }
}
