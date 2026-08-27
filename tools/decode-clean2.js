const fs = require('fs');

function readVarInt(data, off) {
  let value = 0, shift = 0, b;
  do {
    b = data[off++];
    value |= (b & 0x7F) << shift;
    shift += 7;
  } while (b & 0x80);
  return { value, off };
}

const data = fs.readFileSync('capture-chunk.bin');
console.log('file size:', data.length);
let off = 0;
const longsPerSec = [];
for (let s = 0; s < 16; s++) {
  const bits = data[off++];
  const { value: paletteLen, off: o2 } = readVarInt(data, off);
  const { value: dataLen, off: o3 } = readVarInt(data, o2);
  const longs = [];
  for (let l = 0; l < dataLen; l++) {
    let v = 0n;
    for (let i = 0; i < 8; i++) v = (v << 8n) | BigInt(data[o3 + l * 8 + i]);
    longs.push(v);
  }
  longsPerSec.push({ bits, paletteLen, dataLen, longs });
  off = o3 + dataLen * 8 + 2048 + 2048;
}

function getAt(sec, index) {
  const { bits, longs } = sec;
  const bitIndex = index * bits;
  const startLong = bitIndex >> 6;
  const startOffset = bitIndex & 63;
  let v = longs[startLong] >> BigInt(startOffset);
  if (startOffset + bits > 64) v |= longs[startLong + 1] << BigInt(64 - startOffset);
  return Number(v & BigInt((1 << bits) - 1));
}

const sec0 = longsPerSec[0];
console.log('section 0: bits=%d paletteLen=%d dataLen=%d', sec0.bits, sec0.paletteLen, sec0.dataLen);
for (const idx of [0, 1, 8, 16, 128, 136, 137, 255, 256, 272, 4095]) {
  console.log('  entry', idx, '=', getAt(sec0, idx));
}
console.log('section 4 (y64-79) entry 136:', getAt(longsPerSec[4], 136));
console.log('section 4 entry 0:', getAt(longsPerSec[4], 0));
const seen = new Set();
for (let i = 0; i < 4096; i++) seen.add(getAt(sec0, i));
console.log('section 0 distinct states:', [...seen].sort((a, b) => a - b));
const seen4 = new Set();
for (let i = 0; i < 4096; i++) seen4.add(getAt(longsPerSec[4], i));
console.log('section 4 distinct states:', [...seen4].sort((a, b) => a - b));
console.log('section 0 first 6 longs:');
for (let l = 0; l < 6; l++) console.log('  0x' + sec0.longs[l].toString(16).padStart(16, '0'));