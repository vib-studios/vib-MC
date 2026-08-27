const Chunk = require('prismarine-chunk')('1.12')
const ch = new Chunk()
const size = 16 * (1 + 1 + 2 + 6656 + 2048 + 2048) + 256
const b = Buffer.alloc(size)
let off = 0
for (let s = 0; s < 16; s++) {
  b[off++] = 13
  b[off++] = 0
  b[off++] = 0xC0; b[off++] = 0x06
  off += 6656
  off += 2048
  off += 2048
}
for (let i = 0; i < 256; i++) b[off++] = 1
console.log('buffer size:', size, 'filled:', off)
try {
  ch.load(b, 65535, true, true)
  console.log('PARSE OK, block at y=64:', ch.getBlock(0, 64, 0))
} catch (e) {
  console.log('PARSE FAIL:', e.message)
}
