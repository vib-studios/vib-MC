'use strict';

const mineflayer = require('mineflayer');

const host = process.env.HOST || '127.0.0.1';
const port = Number.parseInt(process.env.PORT || '25565', 10);
const results = [];
const chunks = new Map();
let initialPosition;
let serverBrand;
let chunkPackets = 0;
let finished = false;

function check(name, ok, detail) {
  results.push({ name, ok });
  console.log(`[${ok ? 'PASS' : 'FAIL'}] ${name}: ${detail}`);
}

function readVarInt(buffer, cursor) {
  let value = 0;
  for (let index = 0; index < 5; index++) {
    if (cursor.offset >= buffer.length) throw new Error('truncated VarInt');
    const byte = buffer[cursor.offset++];
    value |= (byte & 0x7f) << (index * 7);
    if ((byte & 0x80) === 0) return value >>> 0;
  }
  throw new Error('VarInt exceeds 5 bytes');
}

function readLong(buffer, cursor) {
  let value = 0n;
  for (let index = 0; index < 8; index++) {
    value = (value << 8n) | BigInt(buffer[cursor.offset++]);
  }
  return value;
}

function decodeChunk(data) {
  const buffer = Buffer.from(data.chunkData);
  const bitMap = data.bitMap;
  const cursor = { offset: 0 };
  const sections = new Map();

  for (let section = 0; section < 16; section++) {
    if ((bitMap & (1 << section)) === 0) continue;
    const bits = buffer[cursor.offset++];
    const paletteLength = readVarInt(buffer, cursor);
    const palette = [];
    for (let index = 0; index < paletteLength; index++) {
      palette.push(readVarInt(buffer, cursor));
    }
    const longCount = readVarInt(buffer, cursor);
    const values = [];
    for (let index = 0; index < longCount; index++) values.push(readLong(buffer, cursor));
    cursor.offset += 4096; // block light + sky light
    sections.set(section, { bits, palette, values });
  }

  function stateAt(x, y, z) {
    const section = sections.get(y >> 4);
    if (!section) return 0;
    const index = ((y & 15) << 8) | (z << 4) | x;
    const bitIndex = index * section.bits;
    const longIndex = bitIndex >> 6;
    const offset = bitIndex & 63;
    let value = section.values[longIndex] >> BigInt(offset);
    if (offset + section.bits > 64) {
      value |= section.values[longIndex + 1] << BigInt(64 - offset);
    }
    const paletteIndex = Number(value & ((1n << BigInt(section.bits)) - 1n));
    return section.palette.length === 0 ? paletteIndex : section.palette[paletteIndex];
  }

  function highestBlock(x, z, ignoreTrees = false) {
    for (let y = 255; y >= 0; y--) {
      const state = stateAt(x, y, z);
      const id = state >> 4;
      if (state !== 0 && (!ignoreTrees || id !== 17 && id !== 18 && id !== 9 && id !== 11)) return y;
    }
    return -1;
  }

  const biomeIds = new Set(buffer.slice(cursor.offset, cursor.offset + 256));
  const palettesValid = Array.from(sections.values()).every((section) =>
    section.bits >= 4 && (section.bits > 8 || section.palette.length > 0));
  return { byteLength: buffer.length, bitMap, sectionCount: sections.size,
    palettesValid, stateAt, highestBlock, biomeIds };
}

const bot = mineflayer.createBot({ host, port, username: 'ChunkTester', version: '1.12.2' });

bot._client.on('packet', (data, meta) => {
  if (meta.name === 'custom_payload' && data.channel === 'MC|Brand') {
    const payload = Buffer.from(data.data);
    const cursor = { offset: 0 };
    const length = readVarInt(payload, cursor);
    serverBrand = payload.slice(cursor.offset, cursor.offset + length).toString('utf8');
  }
  if (meta.name === 'position' && !initialPosition) initialPosition = data;
  if (meta.name === 'map_chunk') {
    chunkPackets++;
    try {
      chunks.set(`${data.x},${data.z}`, decodeChunk(data));
    } catch (error) {
      console.error(`Could not decode chunk ${data.x},${data.z}:`, error.message);
    }
  }
});

bot.on('error', (error) => {
  console.error('ERROR:', error.message);
  finish(2);
});

bot.on('end', (reason) => {
  if (!finished) {
    console.error('DISCONNECTED:', reason);
    finish(2);
  }
});

bot.once('spawn', () => {
  bot.physicsEnabled = false;
  setTimeout(runChecks, 2000);
});

async function runChecks() {
  if (!initialPosition) {
    check('received initial position', false, 'missing position packet');
    return finish();
  }

  check('spawn position sane', initialPosition.y > 0,
    `(${initialPosition.x}, ${initialPosition.y}, ${initialPosition.z})`);
  check('initial chunks received', chunks.size >= 25, `${chunks.size} chunks`);

  const chunkX = Math.floor(initialPosition.x) >> 4;
  const chunkZ = Math.floor(initialPosition.z) >> 4;
  const center = chunks.get(`${chunkX},${chunkZ}`);
  const localX = Math.floor(initialPosition.x) & 15;
  const localZ = Math.floor(initialPosition.z) & 15;
  check('spawn chunk decoded', Boolean(center), `chunk ${chunkX},${chunkZ}`);
  if (center) {
    check('bedrock at y=0', center.stateAt(localX, 0, localZ) === (7 << 4),
      `state=${center.stateAt(localX, 0, localZ)}`);
    const height = center.highestBlock(localX, localZ, true);
    check('solid ground below spawn', height === Math.floor(initialPosition.y) - 1,
      `terrain=${height}, spawn=${initialPosition.y}`);
    const heights = [0, 3, 6, 9, 12, 15].map((x) => center.highestBlock(x, localZ, true));
    check('terrain heights decode', heights.every((height) => height >= 0 && height < 256), `heights=${heights.join(',')}`);
  }

  const malformedSections = Array.from(chunks.entries()).filter(([, chunk]) =>
    (chunk.bitMap & 1) === 0 || chunk.sectionCount === 0 || !chunk.palettesValid);
  check('all chunk sections are present and paletted', malformedSections.length === 0,
    malformedSections.length === 0 ? `${chunks.size} chunks valid` :
      malformedSections.map(([coordinate]) => coordinate).join(','));

  const largest = Math.max(...Array.from(chunks.values(), (chunk) => chunk.byteLength));
  check('chunk payloads are compact', largest < 100000, `largest=${largest} bytes`);
  check('server brand received', serverBrand === 'vib-MC', `brand=${serverBrand || 'missing'}`);
  const biomeIds = new Set();
  for (const chunk of chunks.values()) for (const biome of chunk.biomeIds) biomeIds.add(biome);
  check('biome data received', biomeIds.size > 0 && !biomeIds.has(undefined),
    `biomes=${Array.from(biomeIds).join(',')}`);

  const before = chunkPackets;
  bot._client.write('position', {
    x: initialPosition.x + 48,
    y: initialPosition.y,
    z: initialPosition.z,
    onGround: true,
  });
  await new Promise((resolve) => setTimeout(resolve, 2500));
  check('chunks stream after movement', chunkPackets > before,
    `packets before=${before}, after=${chunkPackets}`);
  finish();
}

function finish(forcedCode) {
  if (finished) return;
  finished = true;
  const failures = results.filter((result) => !result.ok).length;
  console.log(`\n${results.length - failures}/${results.length} checks passed`);
  bot.quit();
  setTimeout(() => process.exit(forcedCode === undefined ? (failures ? 1 : 0) : forcedCode), 100);
}

setTimeout(() => {
  console.error('TIMEOUT');
  finish(2);
}, 15000);
