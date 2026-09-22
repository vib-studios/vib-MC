const fs = require('fs');
const path = require('path');

// Minimal NBT Writer in Big Endian format (standard Java NBT)
class NBTWriter {
    constructor() {
        this.buffers = [];
    }

    writeByte(val) {
        const buf = Buffer.alloc(1);
        buf.writeInt8(val, 0);
        this.buffers.push(buf);
    }

    writeShort(val) {
        const buf = Buffer.alloc(2);
        buf.writeInt16BE(val, 0);
        this.buffers.push(buf);
    }

    writeInt(val) {
        const buf = Buffer.alloc(4);
        buf.writeInt32BE(val, 0);
        this.buffers.push(buf);
    }

    writeLong(val) {
        const buf = Buffer.alloc(8);
        if (typeof val === 'bigint') {
            buf.writeBigInt64BE(val, 0);
        } else if (Array.isArray(val) && val.length === 2) {
            buf.writeInt32BE(val[0], 0);
            buf.writeInt32BE(val[1], 4);
        } else {
            buf.writeBigInt64BE(BigInt(val), 0);
        }
        this.buffers.push(buf);
    }

    writeFloat(val) {
        const buf = Buffer.alloc(4);
        buf.writeFloatBE(val, 0);
        this.buffers.push(buf);
    }

    writeDouble(val) {
        const buf = Buffer.alloc(8);
        buf.writeDoubleBE(val, 0);
        this.buffers.push(buf);
    }

    writeString(str) {
        const strBuf = Buffer.from(str, 'utf8');
        this.writeShort(strBuf.length);
        this.buffers.push(strBuf);
    }

    writeByteArray(arr) {
        this.writeInt(arr.length);
        const buf = Buffer.alloc(arr.length);
        for (let i = 0; i < arr.length; i++) buf.writeInt8(arr[i], i);
        this.buffers.push(buf);
    }

    writeIntArray(arr) {
        this.writeInt(arr.length);
        const buf = Buffer.alloc(arr.length * 4);
        for (let i = 0; i < arr.length; i++) buf.writeInt32BE(arr[i], i * 4);
        this.buffers.push(buf);
    }

    writeLongArray(arr) {
        this.writeInt(arr.length);
        const buf = Buffer.alloc(arr.length * 8);
        for (let i = 0; i < arr.length; i++) {
            const v = arr[i];
            if (typeof v === 'bigint') buf.writeBigInt64BE(v, i * 8);
            else buf.writeBigInt64BE(BigInt(v), i * 8);
        }
        this.buffers.push(buf);
    }

    writeTag(type, name, value) {
        this.writeByte(type);
        if (name !== null) {
            this.writeString(name);
        }
        this.writeValue(type, value);
    }

    writeValue(type, value) {
        switch (type) {
            case 1: // BYTE
                this.writeByte(typeof value === 'boolean' ? (value ? 1 : 0) : value);
                break;
            case 2: // SHORT
                this.writeShort(value);
                break;
            case 3: // INT
                this.writeInt(value);
                break;
            case 4: // LONG
                this.writeLong(value);
                break;
            case 5: // FLOAT
                this.writeFloat(value);
                break;
            case 6: // DOUBLE
                this.writeDouble(value);
                break;
            case 7: // BYTE_ARRAY
                this.writeByteArray(value);
                break;
            case 8: // STRING
                this.writeString(value);
                break;
            case 9: // LIST
                const elemType = value.type || 0;
                const items = value.list || [];
                this.writeByte(elemType);
                this.writeInt(items.length);
                for (const item of items) {
                    this.writeValue(elemType, item);
                }
                break;
            case 10: // COMPOUND
                for (const [key, tagObj] of Object.entries(value)) {
                    this.writeTag(tagObj.type, key, tagObj.value);
                }
                this.writeByte(0); // TAG_End
                break;
            case 11: // INT_ARRAY
                this.writeIntArray(value);
                break;
            case 12: // LONG_ARRAY
                this.writeLongArray(value);
                break;
            default:
                throw new Error("Unknown NBT tag type: " + type);
        }
    }

    toBuffer() {
        return Buffer.concat(this.buffers);
    }
}

// Helper to convert json typed NBT (from minecraft-data loginPacket) into tag objects {type, value}
function jsonNbtToTagObj(json) {
    if (!json || typeof json !== 'object') throw new Error("Invalid json nbt");
    const typeStr = json.type;
    const val = json.value;
    switch (typeStr) {
        case 'byte': return { type: 1, value: val };
        case 'short': return { type: 2, value: val };
        case 'int': return { type: 3, value: val };
        case 'long': return { type: 4, value: val };
        case 'float': return { type: 5, value: val };
        case 'double': return { type: 6, value: val };
        case 'string': return { type: 8, value: val };
        case 'byteArray': return { type: 7, value: val };
        case 'intArray': return { type: 11, value: val };
        case 'longArray': return { type: 12, value: val };
        case 'compound': {
            const comp = {};
            for (const [k, v] of Object.entries(val)) {
                comp[k] = jsonNbtToTagObj(v);
            }
            return { type: 10, value: comp };
        }
        case 'list': {
            const elemTypeStr = val.type;
            const elemTypeId = getTypeNumber(elemTypeStr);
            const items = (val.value || []).map(itemVal => {
                const wrapped = { type: elemTypeStr, value: itemVal };
                return jsonNbtToTagObj(wrapped).value;
            });
            return { type: 9, value: { type: elemTypeId, list: items } };
        }
        default:
            throw new Error("Unknown typed json type: " + typeStr);
    }
}

function getTypeNumber(t) {
    switch (t) {
        case 'end': return 0;
        case 'byte': return 1;
        case 'short': return 2;
        case 'int': return 3;
        case 'long': return 4;
        case 'float': return 5;
        case 'double': return 6;
        case 'byteArray': return 7;
        case 'string': return 8;
        case 'list': return 9;
        case 'compound': return 10;
        case 'intArray': return 11;
        case 'longArray': return 12;
        default: return 0;
    }
}

const mcDataDir = path.join(__dirname, '../src/main/resources/vendored/minecraft-data/data/pc');
const outputDir = path.join(__dirname, '../src/main/resources/mappings');

if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
}

// Process all loginPacket releases
const releases = [
    '1.16', '1.16.2', '1.17', '1.18', '1.18.2', '1.19', '1.19.2', '1.19.4',
    '1.20', '1.20.2', '1.20.5', '1.21.1', '1.21.3', '1.21.9', '1.21.11', '26.1'
];

for (const rel of releases) {
    const loginPacketPath = path.join(mcDataDir, rel, 'loginPacket.json');
    if (!fs.existsSync(loginPacketPath)) {
        console.log(`Skipping missing loginPacket for ${rel}`);
        continue;
    }
    const loginData = JSON.parse(fs.readFileSync(loginPacketPath, 'utf8'));
    const codecJson = loginData.dimensionCodec;
    if (!codecJson) continue;

    const rootComp = {};
    rootComp['release'] = { type: 8, value: rel };

    if (codecJson.type) {
        // Compound codec (legacy <= 1.20.3)
        rootComp['codecType'] = { type: 8, value: 'compound' };
        rootComp['dimensionCodec'] = jsonNbtToTagObj(codecJson);
    } else {
        // Split registries (>= 1.20.5)
        rootComp['codecType'] = { type: 8, value: 'split' };
        const regsComp = {};
        for (const [key, regObj] of Object.entries(codecJson)) {
            const regId = regObj.id;
            const entriesList = [];
            for (const rawEntry of regObj.entries || []) {
                const entryComp = {};
                entryComp['key'] = { type: 8, value: rawEntry.key };
                if (rawEntry.value) {
                    entryComp['value'] = jsonNbtToTagObj(rawEntry.value);
                }
                entriesList.push(entryComp);
            }
            regsComp[regId] = {
                type: 9,
                value: { type: 10, list: entriesList }
            };
        }
        rootComp['registries'] = { type: 10, value: regsComp };
    }

    const writer = new NBTWriter();
    writer.writeTag(10, "", rootComp); // Root unnamed compound tag
    const buf = writer.toBuffer();

    const outFile = path.join(outputDir, `mapping-${rel}.nbt`);
    fs.writeFileSync(outFile, buf);
    console.log(`Generated ${outFile} (${buf.length} bytes)`);
}

console.log("ViaVersion Mappings NBT files generation complete!");
