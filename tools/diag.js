const mineflayer = require('mineflayer')
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'DiagBot', version: '1.12.2' })
bot._client.on('send_packet', (data, meta) => {
  console.log('SENT', meta.name, 'id=0x' + meta.id.toString(16))
})
bot._client.on('packet', (data, meta) => {
  if (meta.name === 'map_chunk' || meta.name === 'position' || meta.name === 'login' || meta.name === 'keep_alive') {
    console.log('GOT', meta.name)
  }
})
bot.on('login', () => console.log('LOGIN OK'))
bot.on('spawn', () => { console.log('SPAWNED at', bot.entity.position); setTimeout(() => { console.log('DONE'); process.exit(0) }, 2500) })
bot.on('error', e => { console.log('ERR', e.message); process.exit(1) })
setTimeout(() => { console.log('TIMEOUT - no spawn'); process.exit(2) }, 20000)
