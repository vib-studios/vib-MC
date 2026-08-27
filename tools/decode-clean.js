const fs = require('fs');

// Decode a 1.12.2 chunk data buffer with the VANILLA layout:
//  - per section: bits u8, paletteLen varint, dataLen varint, then dataLen longs
//  - longs are BIG-ENDIAN per long (server writes MSB-first, vanilla PacketBuffer reads BE)
//  - blocks are packed LSB-first inside each 64-bit long
function decodeSectionEntries(data, off, bits) {
  const dataLen = data[off]; // varint fits in 1 byte for 768
  const longs = [];
  off += 1;
  for (let l = 0; l < dataLen; l++) {
    let v = 0n;
    for (let i = 0; i < 8; i++) v = (v << 8n) | BigInt(data[off + l * 8 + i]);
    longs.push(v);
  }
  const get = (index) => {
    const bitIndex = index * bits;
    const startLong = bitIndex >> 6;
    const startOffset = bitIndex & 63;
    let v = longs[startLong] >> BigInt(startOffset);
    if (startOffset + bits > 64) v |= longs[startLong + 1] << BigInt(64 - startOffset);
    return Number(v & BigInt((1 << bits) - 1));
  };
  return { get, longs };
}

const data = fs.readFileSync('capture-chunk.bin');
console.log('file size:', data.length);
let off = 0;
for (let s = 0; s < 16; s++) {
  const bits = data[off];
  const paletteLen = data[off + 1];
  const { get, longs } = decodeSectionEntries(data, off + 2, bits);
  const e0 = get(0);
  const e136 = get(136);
  console.log(`section ${s}: bits=${bits} paletteLen=${paletteLen} entry0=${e0} entry136=${e136} long0=0x${longs[0].toString(16)}`);
  off += 2 + 1 + longs.length * 8 + 4096;
}
console.log('consumed:', off, '(of', data.length, ')');