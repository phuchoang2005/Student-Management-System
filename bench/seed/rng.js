// bench/seed/rng.js — deterministic PRNG. Every dataset is generated from a recorded RNG seed
// (04-workload-data-preparation.md §3); a hand-written PRNG here (rather than an npm random-seed
// package) means the seed is the only source of truth -- no dependency-version drift can change
// what a given seed produces.

// xmur3: hashes an arbitrary string into a 32-bit int, used to seed mulberry32.
function xmur3(str) {
  let h = 1779033703 ^ str.length;
  for (let i = 0; i < str.length; i++) {
    h = Math.imul(h ^ str.charCodeAt(i), 3432918353);
    h = (h << 13) | (h >>> 19);
  }
  return function () {
    h = Math.imul(h ^ (h >>> 16), 2246822507);
    h = Math.imul(h ^ (h >>> 13), 3266489909);
    h ^= h >>> 16;
    return h >>> 0;
  };
}

// mulberry32: fast, small-state PRNG. Returns a function producing floats in [0, 1).
function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/**
 * Derive an independent named substream from one top-level seed, e.g.
 *   createRng('my-seed', 'enrollments')
 * so a future change to one concern's random usage (e.g. book ownership) can't silently perturb
 * another's output (e.g. the student_code shuffle) just because they'd otherwise share one shared
 * call sequence.
 */
export function createRng(topLevelSeed, purpose) {
  const hash = xmur3(`${topLevelSeed}:${purpose}`);
  return mulberry32(hash());
}
