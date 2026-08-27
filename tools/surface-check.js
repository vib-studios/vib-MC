const DEFAULT_SEED = 0x1B2C3D4E5F6A7B8Cn;
const MASK = 0xFFFFFFFFFFFFFFFFn;

function hash(x, z) {
  let h = DEFAULT_SEED;
  h = (h * 0x9E3779B97F4A7C15n + BigInt(x)) & MASK;
  h = (h * 0x9E3779B97F4A7C15n + BigInt(z)) & MASK;
  h = ((h ^ (h >> 30n)) * 0xBF58476D1CE4E5B9n) & MASK;
  h = ((h ^ (h >> 27n)) * 0x94D049BB133111EBn) & MASK;
  h = h ^ (h >> 31n);
  let v = Number(h & 0x7FFFFFFFn);
  return v;
}

function floor(v) { const i = Math.floor(v); return v < i ? i - 1 : i; }
function smoothstep(t) { return t * t * (3 - 2 * t); }
function lerp(a, b, t) { return a + (b - a) * t; }

function valueAt(x, z) {
  return (hash(x, z) / 2147483647) * 2.0 - 1.0;
}

function noise(x, z) {
  const ix = floor(x), iz = floor(z);
  const fx = x - ix, fz = z - iz;
  const sx = smoothstep(fx), sz = smoothstep(fz);
  const a = valueAt(ix, iz), b = valueAt(ix + 1, iz);
  const c = valueAt(ix, iz + 1), d = valueAt(ix + 1, iz + 1);
  return lerp(lerp(a, b, sx), lerp(c, d, sx), sz);
}

function fbm(x, z, octaves) {
  let total = 0, amp = 1, freq = 1, max = 0;
  for (let i = 0; i < octaves; i++) {
    total += noise(x * freq, z * freq) * amp;
    max += amp;
    amp *= 0.5;
    freq *= 2;
  }
  return total / max;
}

const BASE_SURFACE = 9, SEA_LEVEL = 13;
function surface(x, z) {
  const n = fbm(x * 0.05, z * 0.05, 2);
  let s = BASE_SURFACE + Math.max(0, Math.min(6, Math.floor((n + 1.0) * 5.0)));
  return s;
}

let dry = 0, wet = 0, min = 99, maxS = -1;
for (let dz = -16; dz <= 16; dz++) {
  for (let dx = -16; dx <= 16; dx++) {
    const s = surface(8 + dx, 8 + dz);
    if (s >= SEA_LEVEL) dry++; else wet++;
    if (s < min) min = s;
    if (s > maxS) maxS = s;
  }
}
console.log('dry columns near spawn:', dry, 'wet:', wet, 'surface range', min, '-', maxS);
console.log('spawn column (8,8) surface:', surface(8, 8), '-> water depth:', Math.max(0, SEA_LEVEL - surface(8, 8)));
for (let dz = -16; dz <= 16; dz += 4) {
  const row = [];
  for (let dx = -16; dx <= 16; dx += 4) {
    const s = surface(8 + dx, 8 + dz);
    row.push(s >= SEA_LEVEL ? 'D' : String(s));
  }
  console.log('z=' + (8 + dz).toString().padStart(3) + ' ' + row.join(' '));
}
