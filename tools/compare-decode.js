const fs = require('fs');
const Chunk = require('prismarine-chunk')('1.12');
const data = fs.readFileSync('capture-chunk.bin');

const chunk = new Chunk();
chunk.load(data, 0xffff, true, true);
const state = (x, y, z) => chunk.getBlockStateId ? chunk.getBlockStateId(x, y, z) : chunk.getBlock(x, y, z);
for (const [x, y, z] of [[0, 0, 0], [8, 0, 8], [8, 1, 8], [8, 4, 8], [8, 7, 8], [8, 48, 8], [8, 49, 8], [8, 50, 8], [8, 55, 8], [8, 62, 8], [8, 63, 8], [8, 64, 8], [8, 65, 8]]) {
  const bb = chunk.getBlock(x, y, z);
  console.log(`(${x},${y},${z}) stateId=${state(x, y, z)} name=${bb && bb.name}`);
}
const b = chunk.getBlock(8, 0, 8);
console.log('block name at (8,0,8):', b.name);