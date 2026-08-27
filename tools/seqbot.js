const mc = require('minecraft-protocol')
const client = mc.createClient({ host: '127.0.0.1', port: 25565, username: 'seqbot', version: '1.12.2' })
client.on('error', e => console.log('client error:', e.message))
client.on('login', () => console.log('LOGIN OK'))
let n = 0
client.on('raw', (buffer, meta) => {
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
  const id = readVarInt()
  console.log(`#${n++} id=0x${id.toString(16)} name=${meta.name} len=${buffer.length} first16=${buffer.slice(0, Math.min(16, buffer.length)).toString('hex')}`)
  if (n > 45) { console.log('--- done'); process.exit(0) }
})
setTimeout(() => { console.log('timeout'); process.exit(0) }, 10000)