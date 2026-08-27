// Faithful replica of the vanilla 1.12.2 client's chunk parse path:
//   SPacketChunkData.readPacketData -> Chunk.readChunkData -> ChunkSection.read ->
//   BlockStateContainer.readFromPacket -> BitArray.readFromLongArray
// Goal: find where (if anywhere) the vanilla client diverges from our wire data.

const fs = require('fs');
const data = fs.readFileSync('capture-chunk.bin');

let off = 0;
function readByte() { return data[off++]; }
function readVarInt() {
  let v = 0, shift = 0, b;
  do {
    b = data[off++];
    v |= (b & 0x7f) << shift;
    shift += 7;
  } while (b & 0x80);
  return v >>> 0;
}
function readLong() {
  // vanilla PacketBuffer.readLong reads BIG-endian (ByteBuf default order)
  let v = 0n;
  for (let i = 0; i < 8; i++) v = (v << 8n) | BigInt(data[off++]);
  return v;
}
function readLongArray(existing) {
  // vanilla PacketBuffer.readLongArray: reads count, only limits when allocating
  const i = readVarInt();
  if (existing === null || existing.length !== i) {
    if (i > 43) throw new Error(`LongArray with size ${i} is bigger than allowed 43`);
    existing = new Array(i).fill(0n);
  }
  for (let k = 0; k < i; k++) existing[k] = readLong();
  return existing;
}

// vanilla BitArray: (bitsPerEntry * arraySize + 63) / 64 longs
function makeBitArray(bitsPerEntry, arraySize) {
  const longArraySize = Math.floor((arraySize * bitsPerEntry + 63) / 64);
  const longArray = new Array(longArraySize).fill(0n);
  return { bitsPerEntry, arraySize, longArraySize, longArray };
}

function getAt(bitsPerEntry, longArray, index) {
  const bitIndex = index * bitsPerEntry;
  const startLong = bitIndex >> 6;
  const startOffset = bitIndex & 63;
  if (!longArray || typeof longArray[0] !== 'bigint') {
    console.log('getAt bad array:', { bitsPerEntry, len: longArray && longArray.length, t0: longArray && typeof longArray[0], index });
    process.exit(3);
  }
  let value;
  if (startOffset + bitsPerEntry > 64) {
    value = (longArray[startLong] >> BigInt(startOffset)) | (longArray[startLong + 1] << BigInt(64 - startOffset));
  } else {
    value = longArray[startLong] >> BigInt(startOffset);
  }
  return Number(value & ((1n << BigInt(bitsPerEntry)) - 1n));
}

console.log('=== vanilla 1.12.2 client replica ===');
let sectionIndex = 0;
for (let j = 0; j < 16; j++) {
  const bitsPerBlock = readByte();
  const ba = makeBitArray(bitsPerBlock, 4096);

  // BlockStateContainer: palette = bits > 8 ? BlockStatePaletteRegistry : HashMap
  // palette.read(buf): global -> BlockStatePaletteRegistry.read reads the dummy varint; local -> paletteLength + entries
  const paletteLength = readVarInt(); // dummy 0 for global
  if (bitsPerBlock <= 8) {
    for (let i = 0; i < paletteLength; i++) readVarInt(); // local palette entries
  } else if (paletteLength !== 0) {
    throw new Error(`section ${j}: global palette with length ${paletteLength}`);
  }

  // readLongArray(stateArray.getBackingLongArray()) -- reads count varint itself
  const arr = readLongArray(ba.longArray);
  if (arr.length !== ba.longArraySize) {
    console.log(`section ${j}: WARNING dataLen ${arr.length} != expected ${ba.longArraySize} (bits ${bitsPerBlock})`);
  }

  const blockLight = data.slice(off, off + 2048); off += 2048;
  const skyLight = data.slice(off, off + 2048); off += 2048;

  const first = getAt(bitsPerBlock, arr, 0);
  const at0 = getAt(bitsPerBlock, arr, (0 << 8) | (0 << 4) | 0); // x0,y0,z0
  const atX8Z8 = getAt(bitsPerBlock, arr, (0 << 8) | (8 << 4) | 8); // x8,z8, section-local y0
  console.log(`section ${j}: bits=${bitsPerBlock} paletteLen=${paletteLength} dataLen=${arr.length} (x0,y0,z0)=${at0} (x8,z8)=${atX8Z8}`);
  sectionIndex = j;
}

const biomes = data.slice(off, off + 256); off += 256;
console.log(`biomes: ${biomes.length} bytes, first=${biomes[0]}`);
console.log(`bytes consumed: ${off} of ${data.length} -> ${off === data.length ? 'OK, exact' : 'MISMATCH (expected: +1 for block-entity count outside this buffer)'}`);
