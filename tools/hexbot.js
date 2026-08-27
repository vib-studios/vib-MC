const fs = require('fs')
const mc = require('minecraft-protocol')
const client = mc.createClient({ host: '127.0.0.1', port: 25565, username: 'hexbot', version: '1.12.2' })
client.on('error', e => console.log('client error:', e.message))
client.on('login', () => console.log('LOGIN OK'))
let saved = false
client.on('raw.map_chunk', (buffer, meta) => {
  if (saved) return
  saved = true
  let off = 0
  const readVarInt = () => {
    let v = 0, s = 0
    while (true) {
      const b = buffer[off++]
      v |= (b & 0x7f) << s
      if (!(b & 0x80)) return v
      s += 7
    }
  }
  readVarInt()
  off += 8
  const groundUp = buffer[off++]
  readVarInt()
  const size = readVarInt()
  fs.writeFileSync('real-chunk.bin', buffer.slice(off, off + size))
  console.log(`saved ${size} bytes (groundUp=${groundUp})`)
  process.exit(0)
})