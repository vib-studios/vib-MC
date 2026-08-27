'use strict';
const mineflayer = require('mineflayer');
const host = process.env.HOST || '127.0.0.1';
const port = Number.parseInt(process.env.PORT || '25565', 10);
const username = 'PersistBot';
let expectedX;

function connect(first) {
  const bot = mineflayer.createBot({ host, port, username, version: '1.12.2' });
  bot.on('error', error => { console.error(error.stack || error); process.exit(2); });
  bot.once('spawn', () => setTimeout(() => {
    if (first) {
      expectedX = bot.entity.position.x + 12;
      bot.physicsEnabled = false;
      bot.entity.position.x = expectedX;
      bot._client.write('position', {
        x: expectedX,
        y: bot.entity.position.y,
        z: bot.entity.position.z,
        onGround: true
      });
      setTimeout(() => bot.quit(), 500);
      bot.once('end', () => setTimeout(() => connect(false), 250));
    } else {
      const difference = Math.abs(bot.entity.position.x - expectedX);
      if (difference < 0.01) {
        console.log(`[PASS] player position restored: x=${bot.entity.position.x}`);
        bot.quit();
        setTimeout(() => process.exit(0), 100);
      } else {
        console.error(`[FAIL] expected restored x=${expectedX}, got ${bot.entity.position.x}`);
        process.exit(1);
      }
    }
  }, 500));
}
connect(true);
setTimeout(() => { console.error('[FAIL] persistence test timed out'); process.exit(1); }, 15000);
