'use strict';

const mc = require('minecraft-protocol');

const host = process.env.HOST || '127.0.0.1';
const port = Number.parseInt(process.env.PORT || '25565', 10);
const results = [];
let finished = false;

function check(name, condition, detail) {
  results.push(Boolean(condition));
  console.log(`[${condition ? 'PASS' : 'FAIL'}] ${name}: ${detail}`);
}

function wait(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

function createClient(username) {
  const state = {
    username,
    players: new Map(),
    spawned: new Map(),
    destroyed: new Set(),
    teleports: new Map(),
  };
  const client = mc.createClient({ host, port, username, version: '1.12.2' });
  state.client = client;

  client.on('packet', (data, metadata) => {
    if (metadata.name === 'player_info') {
      for (const entry of data.data) {
        if (data.action === 'add_player') state.players.set(entry.uuid, entry.name);
        if (data.action === 'remove_player') state.players.delete(entry.uuid);
      }
    } else if (metadata.name === 'named_entity_spawn') {
      state.spawned.set(data.playerUUID, data);
    } else if (metadata.name === 'entity_teleport') {
      state.teleports.set(data.entityId, data);
    } else if (metadata.name === 'entity_destroy') {
      for (const id of data.entityIds) state.destroyed.add(id);
    }
  });
  client.on('error', (error) => finish(2, `${username}: ${error.message}`));
  return state;
}

function waitForLogin(state) {
  return new Promise((resolve, reject) => {
    state.client.once('login', resolve);
    state.client.once('error', reject);
  });
}

function uuidFor(state, username) {
  for (const [uuid, name] of state.players) if (name === username) return uuid;
  return null;
}

async function run() {
  const alice = createClient('AliceBot');
  await waitForLogin(alice);
  // PacketEvents emits native entity packets immediately; let the test client's
  // login plugins finish initializing before introducing the second player.
  await wait(500);
  const bob = createClient('BobBot');
  await waitForLogin(bob);
  await wait(750);

  const bobUuid = uuidFor(alice, 'BobBot');
  const aliceUuid = uuidFor(bob, 'AliceBot');
  check('Alice has Bob in the player list', Boolean(bobUuid),
    Array.from(alice.players.values()).join(', '));
  check('Bob has Alice in the player list', Boolean(aliceUuid),
    Array.from(bob.players.values()).join(', '));
  check('Alice receives Bob spawn', Boolean(alice.spawned.get(bobUuid)),
    `entity=${alice.spawned.get(bobUuid)?.entityId ?? 'missing'}`);
  check('Bob receives Alice spawn', Boolean(bob.spawned.get(aliceUuid)),
    `entity=${bob.spawned.get(aliceUuid)?.entityId ?? 'missing'}`);

  const bobSpawn = alice.spawned.get(bobUuid);
  bob.client.write('position', { x: 30.5, y: 14, z: 8.5, onGround: true });
  await wait(400);
  const moved = bobSpawn && alice.teleports.get(bobSpawn.entityId);
  check('Alice receives Bob movement', moved && moved.x === 30.5,
    `x=${moved?.x ?? 'missing'}`);

  bob.client.end();
  await wait(600);
  check('Bob is removed from Alice player list', !uuidFor(alice, 'BobBot'),
    Array.from(alice.players.values()).join(', '));
  check('Bob entity is destroyed for Alice', bobSpawn && alice.destroyed.has(bobSpawn.entityId),
    `entity=${bobSpawn?.entityId ?? 'missing'}`);

  alice.client.end();
  finish();
}

function finish(forcedCode, message) {
  if (finished) return;
  finished = true;
  if (message) console.error(message);
  const failures = results.filter((value) => !value).length;
  console.log(`\n${results.length - failures}/${results.length} checks passed`);
  setTimeout(() => process.exit(forcedCode === undefined ? (failures ? 1 : 0) : forcedCode), 100);
}

run().catch((error) => finish(2, error.stack || error.message));
setTimeout(() => finish(2, 'TIMEOUT'), 15000);
