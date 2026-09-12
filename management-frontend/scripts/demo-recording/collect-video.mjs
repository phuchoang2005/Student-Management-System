#!/usr/bin/env node
// Copies the single video Playwright wrote under test-results/ to assests/demo-videos/full-demo.webm
// at the repo root. Run right after `npm run demo:record` — see scripts/demo-recording/README.md.
import { existsSync, mkdirSync, copyFileSync, readdirSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const frontendDir = dirname(dirname(dirname(fileURLToPath(import.meta.url))));
const resultsDir = join(frontendDir, 'test-results');

function findVideo(dir) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      const found = findVideo(full);
      if (found) return found;
    } else if (entry.name.endsWith('.webm')) {
      return full;
    }
  }
  return null;
}

if (!existsSync(resultsDir)) {
  console.error(`No test-results/ directory found at ${resultsDir} — run npm run demo:record first.`);
  process.exit(1);
}

const video = findVideo(resultsDir);
if (!video) {
  console.error('No .webm video found under test-results/.');
  process.exit(1);
}

const outDir = join(frontendDir, '..', 'assests', 'demo-videos');
mkdirSync(outDir, { recursive: true });
const dest = join(outDir, 'full-demo.webm');
copyFileSync(video, dest);

console.log(`Copied ${video} (${(statSync(video).size / 1024 / 1024).toFixed(1)} MB) -> ${dest}`);
