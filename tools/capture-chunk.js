const mc = require('minecraft-protocol');
const fs = require('fs');

const client = mc.createClient({
  host: '127.0.0.1',
  port: 25565,
  username: 'CaptureBot',
  version: '1.12.2',
});

let saved = 0;
client.on('packet', (data, meta) => {
  if (meta.name === 'map_chunk' && saved === 0) {
    const buf = Buffer.isBuffer(data.chunkData) ? data.chunkData : (data.chunkData && data.chunkData.data);
    fs.writeFileSync('capture-chunk.bin', buf);
    saved++;
    console.log('saved chunkData:', buf.length, 'bytes, bitmask', data.bitMask, 'full', data.groundUp, 'keys:', Object.keys(data.chunkData || {}).join(','));
    client.end();
    setTimeout(() => process.exit(0), 500);
  }
});

client.on('error', (e) => { console.log('ERR', e.message); process.exit(1); });
setTimeout(() => { console.log('timeout'); process.exit(1); }, 10000);