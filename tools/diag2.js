const mineflayer = require('mineflayer')
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'DiagBot', version: '1.12.2' })
bot.on('error', e => console.log('ERROR:', e.message))
let received = 0
let loaded = 0
bot._client.on('map_chunk', () => received++)
bot.on('chunkColumnLoad', () => { loaded++; console.log(`  [chunkColumnLoad #${loaded}]`) })
bot.once('spawn', () => {
  console.log('spawned, game.dimension =', bot.game.dimension)
  setTimeout(() => {
    console.log(`received=${received} loaded=${loaded} world columns=${Object.keys(bot.world.columns).length}`)
    const b = bot.blockAt(bot.entity.position.offset(0, -1, 0))
    console.log('block below:', b ? b.name : 'none')
    process.exit(0)
  }, 15000)
})