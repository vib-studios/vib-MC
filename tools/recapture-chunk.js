const mc = require('minecraft-protocol');
const fs = require('fs');
const Chunk = require('prismarine-chunk')('1.12');

const client = mc.createClient({
  host: '127.0.0.1',
  port: 25565,
  username: 'CaptureBot3',
  version: '1.12.2',
});

let saved = 0;
client.on('packet', (data, meta) => {
  if (meta.name === 'map_chunk' && saved === 0) {
    saved++;
    const buf = Buffer.from(Buffer.isBuffer(data.chunkData) ? data.chunkData : (data.chunkData && data.chunkData.data));
    fs.writeFileSync('capture-chunk.bin', buf);
    const bitMap = data.bitMap !== undefined ? data.bitMap : 0xffff;
    console.log('chunk', data.x, data.z, 'bitMap', bitMap, 'groundUp', data.groundUp, 'bytes', buf.length);
    const c = new Chunk();
    c.load(buf, bitMap, true, data.groundUp);
    const cols = [[8, 0, 8], [8, 1, 8], [8, 49, 8], [8, 50, 8], [8, 62, 8], [8, 63, 8], [8, 64, 8], [8, 65, 8]];
    for (const [x, y, z] of cols) {
      const b = c.getBlock({ x, y, z });
      console.log(`  (${x},${y},${z}) state=${b.stateId} name=${b.name}`);
    }
    client.end();
    setTimeout(() => process.exit(0), 500);
  }
});

client.on('error', (e) => { console.log('ERR', e.message); process.exit(1); });
setTimeout(() => { console.log('timeout'); process.exit(1); }, 10000);