#!/usr/bin/env node
// Post-processes @redocly/cli `build-docs` output: it embeds the spec data inline but
// still hardcodes a <script src="https://cdn.redocly.com/redoc/v<version>/..."> tag for
// the Redoc rendering engine itself. This inlines that script's actual JS so the file
// works fully offline, then verifies no external <script>/<link> reference remains.

import { readFile, writeFile } from "node:fs/promises";

const filePath = process.argv[2];
if (!filePath) {
  console.error("Usage: node inline-redoc.mjs <path-to-html>");
  process.exit(1);
}

const html = await readFile(filePath, "utf8");

const scriptTagPattern = /<script src="(https:\/\/cdn\.redocly\.com\/redoc\/v[^"]+\/bundles\/redoc\.standalone\.js)"[^>]*><\/script>/;
const match = html.match(scriptTagPattern);
if (!match) {
  console.error("Could not find the expected cdn.redocly.com <script> tag — build-docs output format may have changed. Aborting.");
  process.exit(1);
}
const [tag, cdnUrl] = match;

console.log(`Fetching ${cdnUrl} (one-time, build-time only)...`);
const res = await fetch(cdnUrl);
if (!res.ok) {
  console.error(`Failed to fetch ${cdnUrl}: ${res.status} ${res.statusText}`);
  process.exit(1);
}
const redocJs = (await res.text()).replaceAll("</script", "<\\/script");

// Use a replacer *function*, not a replacement string: String.replace interprets "$&",
// "$'", etc. specially in a replacement string, and minified JS is full of literal "$"
// characters — a plain-string replacement would corrupt the output by re-inserting
// fragments of the match wherever the vendor bundle happens to contain one of those
// sequences. A function's return value is always inserted literally.
let result = html.replace(tag, () => `<script>${redocJs}</script>`);

// build-docs' --disableGoogleFont flag only strips the <link> Google Fonts import, not
// any other remote reference; strip a Google Fonts <link> defensively if still present.
result = result.replace(/<link[^>]+fonts\.googleapis\.com[^>]*>\s*/g, "");

await writeFile(filePath, result, "utf8");

function countOccurrences(haystack, needle) {
  let count = 0;
  let pos = 0;
  while ((pos = haystack.indexOf(needle, pos)) !== -1) {
    count++;
    pos += needle.length;
  }
  return count;
}

const remainingCdn = countOccurrences(result, "cdn.redocly.com");
const remainingFonts = countOccurrences(result, "fonts.googleapis.com");
if (remainingCdn > 0 || remainingFonts > 0) {
  console.error(
    `Inline failed verification: ${remainingCdn} cdn.redocly.com reference(s), ${remainingFonts} fonts.googleapis.com reference(s) remain.`
  );
  process.exit(1);
}

console.log(`Inlined redoc.standalone.js (${(redocJs.length / 1024).toFixed(0)} KiB) into ${filePath}. Zero remaining CDN references.`);
