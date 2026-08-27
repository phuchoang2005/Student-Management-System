// bench/lib/manifest.js — loads the per-scale runtime sample data k6 scenarios need but can't
// cheaply discover live against the API: which students sit in the enrollment-count tail (needed
// so BM-ENR-002's size=100 page has >20 rows to actually page through), which courses are
// enrollment-heavy, and the full login cohort (BM-ME-* needs distinct STUDENT logins per VU).
//
// Two files, both written right after seeding:
//   bench/out/<scale>-manifest.json       written by bench/seed/manifest.js (new, this sprint)
//   bench/out/<scale>-search-terms.json   written by bench/seed/generate.js (PM-031, unchanged)
//
// k6's open() only works in the init context, so this module calls it at module-load time (not
// inside setup()/exec functions) -- every scenario file that needs this data must import from
// here at its own top level, not lazily inside an exec function.

import { SCALE } from './config.js';

function loadJson(path) {
  return JSON.parse(open(path));
}

let manifestCache = null;
let searchTermsCache = null;

export function loadManifest() {
  if (manifestCache === null) {
    manifestCache = loadJson(`../out/${SCALE}-manifest.json`);
  }
  return manifestCache;
}

export function loadSearchTerms() {
  if (searchTermsCache === null) {
    searchTermsCache = loadJson(`../out/${SCALE}-search-terms.json`);
  }
  return searchTermsCache;
}

/** Terms with at least one hit, for scenarios where an always-empty result would be a poor sample. */
export function termsWithHits(entityTable) {
  return Object.entries(entityTable)
    .filter(([, count]) => count > 0)
    .map(([term]) => term);
}

export function pickRandom(array) {
  if (array.length === 0) {
    throw new Error('pickRandom() called on an empty array -- check the manifest/search-terms data for this scale.');
  }
  return array[Math.floor(Math.random() * array.length)];
}
