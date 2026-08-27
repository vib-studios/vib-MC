const fs = require('fs')
const Chunk = require('prismarine-chunk')('1.12')
const data = fs.readFileSync('real-chunk.bin')
console.log('real chunk size:', data.length)
for (const sky of [true, false]) {
  const ch = new Chunk()
  try {
    ch.load(data, 65535, sky, true)
    console.log(`skyLightSent=${sky}: PARSE OK, block(0,64,0)=${ch.getBlock(0, 64, 0).name}, block(0,0,0)=${ch.getBlock(0, 0, 0).name}`)
  } catch (e) {
    console.log(`skyLightSent=${sky}: PARSE FAIL: ${e.message}`)
  }
}
