package net.vibmc.registry;

import com.github.retrooper.packetevents.protocol.nbt.NBT;
import com.github.retrooper.packetevents.protocol.nbt.NBTByteArray;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.protocol.nbt.NBTLimiter;
import com.github.retrooper.packetevents.protocol.nbt.serializer.DefaultNBTSerializer;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loader for ViaVersion mapping NBT assets using PacketEvents' NBT reader.
 * <p>
 * The assets live under {@code /vendored/viaversion-mappings/} and are binary NBT.
 * We use {@link DefaultNBTSerializer} to read them, satisfying the requirement
 * to use PacketEvents NBT reader + ViaVersion Mappings data (not ViaVersion runtime).
 * <p>
 * Implements the v2 mapping format from ViaVersion's MappingDataLoader (MIT):
 * - DIRECT_ID=0, SHIFTS_ID=1, CHANGES_ID=2, IDENTITY_ID=3
 * - version must be 2
 * - val is packed varints (plain varint for at, zigzag varint for values)
 */
public final class ViaMappings {
    private static final String ROOT = "/vendored/viaversion-mappings/";
    private static final byte DIRECT_ID = 0;
    private static final byte SHIFTS_ID = 1;
    private static final byte CHANGES_ID = 2;
    private static final byte IDENTITY_ID = 3;

    private static volatile ViaMappings instance;
    private final Map<String, NBTCompound> cache = new HashMap<>();

    private ViaMappings() {}

    public static synchronized ViaMappings get() {
        if (instance == null) {
            instance = new ViaMappings();
        }
        return instance;
    }

    /** Load a binary NBT file from the vendored folder via PacketEvents reader. */
    public synchronized NBTCompound loadNbt(String fileName) throws IOException {
        NBTCompound cached = cache.get(fileName);
        if (cached != null) {
            return cached.copy();
        }
        String resource = ROOT + fileName;
        InputStream stream = ViaMappings.class.getResourceAsStream(resource);
        if (stream == null) {
            throw new IOException("Missing ViaVersion mapping resource " + resource);
        }
        try (DataInputStream in = new DataInputStream(stream)) {
            NBT tag = DefaultNBTSerializer.INSTANCE.deserializeTag(NBTLimiter.noop(), in, true);
            if (!(tag instanceof NBTCompound)) {
                throw new IOException("Root of " + fileName + " is not a compound: " + tag.getClass());
            }
            NBTCompound compound = (NBTCompound) tag;
            cache.put(fileName, compound.copy());
            return compound;
        }
    }

    /** Decode a mapping section (e.g. blockstates, blocks, items) into oldId -> newId array. */
    public int[] decodeMapping(NBTCompound root, String key) throws IOException {
        NBTCompound section = root.getCompoundTagOrNull(key);
        if (section == null) {
            throw new IOException("Missing mapping section " + key);
        }
        return decodeMappingSection(section);
    }

    private int[] decodeMappingSection(NBTCompound section) throws IOException {
        NBTInt versionTag = section.getTagOfTypeOrNull("version", NBTInt.class);
        // Some files (like identifiers) store version at root, not in section; allow missing version in section
        // but check if present and must be 2
        if (versionTag != null && versionTag.getAsInt() != 2) {
            throw new IOException("Unsupported mapping version " + versionTag.getAsInt());
        }
        // For root version check, caller should ensure root version==2 when needed
        NBTInt sizeTag = section.getTagOfTypeOrNull("size", NBTInt.class);
        NBTInt mappedSizeTag = section.getTagOfTypeOrNull("mappedSize", NBTInt.class);
        if (sizeTag == null) {
            // IDENTITY case may have only size
            // Try to get size directly
            throw new IOException("Missing size in mapping section");
        }
        int size = sizeTag.getAsInt();
        int mappedSize = mappedSizeTag != null ? mappedSizeTag.getAsInt() : size;

        // id strategy
        byte strategy = 0;
        NBT idTag = section.getTagOrNull("id");
        if (idTag instanceof com.github.retrooper.packetevents.protocol.nbt.NBTByte) {
            strategy = ((com.github.retrooper.packetevents.protocol.nbt.NBTByte) idTag).getAsByte();
        } else if (idTag instanceof NBTInt) {
            strategy = (byte) ((NBTInt) idTag).getAsInt();
        } else if (idTag != null) {
            throw new IOException("Unexpected id tag type " + idTag.getClass());
        } else {
            // If no id, assume IDENTITY? But spec says always present except maybe
            strategy = IDENTITY_ID;
        }

        byte[] valBytes;
        NBTByteArray valTag = section.getTagOfTypeOrNull("val", NBTByteArray.class);
        if (valTag != null) {
            valBytes = valTag.getValue();
        } else {
            valBytes = new byte[0];
        }

        switch (strategy) {
            case DIRECT_ID: {
                int[] values = new int[size];
                VarIntReader reader = new VarIntReader(valBytes);
                int prev = 0;
                for (int i = 0; i < size; i++) {
                    int zz = reader.readZigZagVarInt();
                    prev += zz;
                    values[i] = prev;
                }
                return values;
            }
            case SHIFTS_ID: {
                ValuePairs pairs = readAtValuePairs(valBytes);
                int[] at = pairs.at;
                int[] to = pairs.values;
                int[] out = new int[size];
                // Fill identity before first shift if needed
                int idx = 0;
                if (at.length > 0 && at[0] != 0) {
                    int toFill = at[0];
                    for (int id = 0; id < toFill; id++) {
                        out[id] = id;
                    }
                    idx = toFill;
                }
                for (int i = 0; i < at.length; i++) {
                    int from = at[i];
                    int toEnd = (i == at.length - 1) ? size : at[i + 1];
                    int mappedId = to[i];
                    for (int id = from; id < toEnd; id++) {
                        out[id] = mappedId++;
                    }
                }
                return out;
            }
            case CHANGES_ID: {
                ValuePairs pairs = readAtValuePairs(valBytes);
                int[] changesAt = pairs.at;
                int[] values = pairs.values;
                boolean fillBetween = section.getTagOrNull("nofill") == null;
                int[] out = new int[size];
                // Initialize with -1 to detect missing
                for (int i = 0; i < size; i++) out[i] = -1;
                int nextUnhandled = 0;
                for (int i = 0; i < changesAt.length; i++) {
                    int changedId = changesAt[i];
                    if (fillBetween) {
                        for (int id = nextUnhandled; id < changedId; id++) {
                            out[id] = id;
                        }
                        nextUnhandled = changedId + 1;
                    }
                    out[changedId] = values[i];
                }
                if (fillBetween && nextUnhandled != size) {
                    for (int id = nextUnhandled; id < size; id++) {
                        out[id] = id;
                    }
                }
                // For nofill case, leave -1 where not mapped (should be filled by caller if needed)
                if (!fillBetween) {
                    // Fill remaining with -1 already, but for identity fallback, set to id where still -1? No, per spec nofill means don't fill
                    // We'll keep -1 for unmapped
                }
                return out;
            }
            case IDENTITY_ID: {
                int[] out = new int[size];
                for (int i = 0; i < size; i++) out[i] = i;
                return out;
            }
            default:
                throw new IOException("Unknown mapping strategy " + strategy);
        }
    }

    /** Decode blockstate mapping 1.13 -> 1.13.2 and invert to 1.13.2 -> 1.13. */
    public int[] blockStateInverse13To132() throws IOException {
        NBTCompound root = loadNbt("mappings-1.13to1.13.2.nbt");
        NBTInt ver = root.getTagOfTypeOrNull("version", NBTInt.class);
        if (ver == null || ver.getAsInt() != 2) {
            throw new IOException("Invalid version in mappings-1.13to1.13.2.nbt");
        }
        int[] forward = decodeMapping(root, "blockstates");
        // forward[oldId] = newId (1.13 -> 1.13.2)
        // Build inverse: newId -> oldId, size = mappedSize
        NBTCompound section = root.getCompoundTagOrNull("blockstates");
        int mappedSize = section.getTagOfTypeOrNull("mappedSize", NBTInt.class).getAsInt();
        int[] inverse = new int[mappedSize];
        for (int i = 0; i < inverse.length; i++) inverse[i] = -1;
        for (int oldId = 0; oldId < forward.length; oldId++) {
            int newId = forward[oldId];
            if (newId >= 0 && newId < inverse.length) {
                inverse[newId] = oldId;
            }
        }
        return inverse;
    }

    /** Decode IdRanges byte array into list of ids (inclusive ranges). */
    public static List<Integer> decodeIdRanges(byte[] data) {
        List<Integer> out = new ArrayList<>();
        VarIntReader reader = new VarIntReader(data);
        int prevEnd = 0;
        while (reader.hasRemaining()) {
            int startOffset = reader.readVarInt();
            int length = reader.readVarInt();
            int start = prevEnd + startOffset;
            int end = start + length;
            for (int id = start; id <= end; id++) {
                out.add(id);
            }
            prevEnd = end + 1;
        }
        return out;
    }

    /** Load timeline registry from ViaVersion file. Returns map timelineName -> element NBTCompound */
    public Map<String, NBTCompound> loadTimelineRegistry() throws IOException {
        NBTCompound root = loadNbt("timeline-registry-1.21.11.nbt");
        Map<String, NBTCompound> result = new LinkedHashMap<>();
        for (Map.Entry<String, NBT> entry : root.getTags().entrySet()) {
            if (entry.getValue() instanceof NBTCompound) {
                result.put(entry.getKey(), (NBTCompound) entry.getValue());
            }
        }
        return result;
    }

    /** Load sound variant registries from ViaVersion file. Returns registryKey -> (variantName -> element) */
    public Map<String, Map<String, NBTCompound>> loadSoundVariantRegistries() throws IOException {
        NBTCompound root = loadNbt("sound-variant-registries-26.1.nbt");
        Map<String, Map<String, NBTCompound>> result = new LinkedHashMap<>();
        for (Map.Entry<String, NBT> outer : root.getTags().entrySet()) {
            if (!(outer.getValue() instanceof NBTCompound)) continue;
            NBTCompound innerMap = (NBTCompound) outer.getValue();
            Map<String, NBTCompound> variants = new LinkedHashMap<>();
            for (Map.Entry<String, NBT> inner : innerMap.getTags().entrySet()) {
                if (inner.getValue() instanceof NBTCompound) {
                    variants.put(inner.getKey(), (NBTCompound) inner.getValue());
                }
            }
            result.put(outer.getKey(), Collections.unmodifiableMap(variants));
        }
        return Collections.unmodifiableMap(result);
    }

    /** Load dimension registry 1.16.2 (for reference, not strictly needed as PE provides it). */
    public NBTCompound loadDimensionRegistry1162() throws IOException {
        return loadNbt("dimension-registry-1.16.2.nbt");
    }

    // --- helpers ---

    private static final class ValuePairs {
        final int[] at;
        final int[] values;
        ValuePairs(int[] at, int[] values) {
            this.at = at;
            this.values = values;
        }
    }

    private static ValuePairs readAtValuePairs(byte[] data) throws IOException {
        VarIntReader reader = new VarIntReader(data);
        List<Integer> atList = new ArrayList<>();
        List<Integer> valList = new ArrayList<>();
        int prevAt = -1;
        int prevValue = 0;
        while (reader.hasRemaining()) {
            int atDelta = reader.readVarInt();
            int zz = reader.readZigZagVarInt();
            prevAt = prevAt + 1 + atDelta;
            prevValue += zz;
            atList.add(prevAt);
            valList.add(prevValue);
        }
        int[] at = new int[atList.size()];
        int[] vals = new int[valList.size()];
        for (int i = 0; i < at.length; i++) {
            at[i] = atList.get(i);
            vals[i] = valList.get(i);
        }
        return new ValuePairs(at, vals);
    }

    /** Simple varint reader for ViaVersion's packed format. */
    private static final class VarIntReader {
        private final byte[] data;
        private int pos = 0;
        VarIntReader(byte[] data) {
            this.data = data;
        }
        boolean hasRemaining() {
            return pos < data.length;
        }
        int readVarInt() {
            int numRead = 0;
            int result = 0;
            byte read;
            do {
                if (pos >= data.length) {
                    throw new IllegalStateException("VarInt does not have enough bytes");
                }
                read = data[pos++];
                int value = (read & 0b01111111);
                result |= (value << (7 * numRead));
                numRead++;
                if (numRead > 5) {
                    throw new IllegalStateException("VarInt too big");
                }
            } while ((read & 0b10000000) != 0);
            return result;
        }
        int readZigZagVarInt() {
            int v = readVarInt();
            return (v >>> 1) ^ -(v & 1);
        }
    }
}
