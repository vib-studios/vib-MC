'use strict';

// Integration checks for the 0.0.6-hotfix.1 spawn/flight fixes.
//
//   1. A joining player never lands in water.
//   2. A survival player who hovers is still kicked by the floating check.
//   3. A creative player who hovers is never kicked.
//
// The creative bot needs the gamemode permission, so op it first - either
// `op CreativeFlyBot` in the server console once it has joined, or add its offline UUID
// to ops.json before starting the server.
//
//   node tools/spawn-flight-test.js

const mineflayer = require('mineflayer');
const mc = require('minecraft-protocol');

const host = process.env.HOST || '127.0.0.1';
const port = Number.parseInt(process.env.PORT || '25565', 10);
const version = process.env.MC_VERSION || '1.12.2';
const results = [];

function check(name, ok, detail) {
  results.push({ name, ok });
  console.log(`[${ok ? 'PASS' : 'FAIL'}] ${name}: ${detail}`);
}

function spawnIsDry() {
  return new Promise((resolve) => {
    const bot = mineflayer.createBot({ host, port, username: 'DrySpawnBot', version });
    const failed = (reason) => { check('spawn position is dry', false, reason); resolve(); };
    bot.on('error', (error) => failed(`client error: ${error.message}`));
    bot.on('kicked', (reason) => failed(`kicked: ${reason}`));
    bot.once('spawn', () => {
      setTimeout(() => {
        const position = bot.entity.position;
        const feet = bot.blockAt(position);
        const head = bot.blockAt(position.offset(0, 1, 0));
        const floor = bot.blockAt(position.offset(0, -1, 0));
        const at = `${position.x.toFixed(1)}, ${position.y.toFixed(1)}, ${position.z.toFixed(1)}`;
        if (!feet || !head || !floor) {
          check('spawn position is dry', false, `chunk around ${at} never arrived`);
        } else {
          check('spawn body is not in water', feet.name !== 'water' && head.name !== 'water',
            `${at} feet=${feet.name} head=${head.name}`);
          check('spawn floor is dry ground', floor.name !== 'water' && floor.name !== 'air',
            `${at} floor=${floor.name}`);
        }
        bot.quit();
        resolve();
      }, 4000);
    });
  });
}

// Hovers in place with onGround=false, which is exactly what the floating check watches for.
function hover(username, creative) {
  return new Promise((resolve) => {
    const client = mc.createClient({ host, port, username, version });
    let hovering = null;
    let kickReason = null;
    let started = false;
    const finish = () => {
      if (hovering) clearInterval(hovering);
      try { client.end(); } catch (ignored) { /* already closed */ }
      resolve(kickReason);
    };
    client.on('error', () => { /* the kick closes the socket */ });
    client.on('end', () => { if (started) finish(); });
    client.on('packet', (data, meta) => {
      if (meta.name === 'kick_disconnect') {
        kickReason = String(data.reason);
        return;
      }
      if (meta.name !== 'position' || started) return;
      client.write('teleport_confirm', { teleportId: data.teleportId });
      started = true;
      const target = { x: data.x, y: data.y + 3, z: data.z };
      if (creative) client.write('chat', { message: '/gamemode creative' });
      setTimeout(() => {
        hovering = setInterval(() => {
          client.write('position', { x: target.x, y: target.y, z: target.z, onGround: false });
        }, 50);
      }, 1000);
      setTimeout(finish, 10000);
    });
  });
}

async function main() {
  await spawnIsDry();

  const survival = await hover('SurvivalFlyBot', false);
  check('a survival player hovering is still kicked',
    survival !== null && survival.includes('Flying is not enabled'),
    survival === null ? 'no kick packet arrived' : survival);

  const creative = await hover('CreativeFlyBot', true);
  check('a creative player hovering is never kicked', creative === null,
    creative === null ? 'stayed connected while flying' : creative);

  const failures = results.filter((result) => !result.ok).length;
  console.log(`${results.length - failures}/${results.length} checks passed`);
  process.exit(failures === 0 ? 0 : 1);
}

main().catch((error) => { console.error(error); process.exit(2); });
