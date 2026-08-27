#!/usr/bin/env node
// bench/monitor-soak.js — BM-XC-002 companion, H7 (03-benchmark-scenarios.md §7, PM-036).
//
// heap-bytes-per-active-session isn't something a single k6 --summary-export captures: it needs
// periodic sampling of actuator's jvm.memory.used correlated with the live session count, over the
// whole 30-minute soak. Run this alongside `k6 run bench/scenarios/mixed-soak.js` (bench-mixed-soak
// backgrounds this file around the k6 run -- see Makefile).
//
// Uses Node's native fetch (18+), not bench/lib/ -- this is a plain Node script, no k6 runtime
// here. fetch() has no cookie jar of its own (unlike a browser or k6's per-VU jar), so the
// Set-Cookie -> Cookie thread below is the one thing to get right: log in once, keep the resulting
// JSESSIONID, and send it back on every subsequent request by hand.
//
// Usage: node monitor-soak.js --scale=S2 [--base-url=http://localhost:8080] [--interval=30]
//   [--duration=1800] [--username=bench.system_administrator] [--password=Benchmark123!]
// Ctrl-C (SIGINT) stops early and still writes whatever was collected.

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUT_DIR = path.join(__dirname, 'out');

function parseArgs(argv) {
  const args = {
    baseUrl: 'http://localhost:8080',
    intervalSeconds: 30,
    durationSeconds: null,
    username: 'bench.system_administrator',
    password: 'Benchmark123!',
  };
  for (const arg of argv) {
    if (arg.startsWith('--scale=')) args.scale = arg.slice('--scale='.length);
    else if (arg.startsWith('--base-url=')) args.baseUrl = arg.slice('--base-url='.length);
    else if (arg.startsWith('--interval=')) args.intervalSeconds = Number(arg.slice('--interval='.length));
    else if (arg.startsWith('--duration=')) args.durationSeconds = Number(arg.slice('--duration='.length));
    else if (arg.startsWith('--username=')) args.username = arg.slice('--username='.length);
    else if (arg.startsWith('--password=')) args.password = arg.slice('--password='.length);
  }
  if (!args.scale) throw new Error('Missing --scale=S1|S2|S3|S4');
  return args;
}

async function login(baseUrl, username, password) {
  const res = await fetch(`${baseUrl}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  if (res.status !== 200) {
    throw new Error(`login as ${username} failed: HTTP ${res.status} ${await res.text()}`);
  }
  const setCookie = res.headers.get('set-cookie');
  if (!setCookie) throw new Error('login succeeded but no Set-Cookie header was returned');
  // Only the JSESSIONID pair matters for later requests -- strip attributes (Path, HttpOnly, ...).
  return setCookie.split(';')[0];
}

async function sample(baseUrl, cookie) {
  const headers = { Cookie: cookie };
  const [heapRes, sessionsRes] = await Promise.all([
    fetch(`${baseUrl}/actuator/metrics/jvm.memory.used?tag=area:heap`, { headers }),
    fetch(`${baseUrl}/api/v1/sessions`, { headers }),
  ]);
  if (heapRes.status !== 200 || sessionsRes.status !== 200) {
    return { at: new Date().toISOString(), error: `HTTP ${heapRes.status}/${sessionsRes.status}` };
  }
  const heapBody = await heapRes.json();
  const sessions = await sessionsRes.json();
  const heapUsedBytes = (heapBody.measurements || []).find((m) => m.statistic === 'VALUE')?.value ?? null;
  const sessionCount = Array.isArray(sessions) ? sessions.length : null;
  return {
    at: new Date().toISOString(),
    heapUsedBytes,
    sessionCount,
    heapBytesPerSession: heapUsedBytes !== null && sessionCount ? heapUsedBytes / sessionCount : null,
  };
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const cookie = await login(args.baseUrl, args.username, args.password);
  console.log(`Logged in as ${args.username}; sampling every ${args.intervalSeconds}s.`);

  const samples = [];
  let stopped = false;
  process.on('SIGINT', () => {
    console.log('\nStopping (SIGINT) -- writing what was collected so far.');
    stopped = true;
  });

  const startedAt = Date.now();
  while (!stopped) {
    const point = await sample(args.baseUrl, cookie);
    samples.push(point);
    console.log(
      point.error
        ? `[${point.at}] sample failed: ${point.error}`
        : `[${point.at}] heap=${point.heapUsedBytes ?? '--'} sessions=${point.sessionCount ?? '--'} ` +
            `bytes/session=${point.heapBytesPerSession !== null ? point.heapBytesPerSession.toFixed(0) : '--'}`,
    );
    if (args.durationSeconds !== null && (Date.now() - startedAt) / 1000 >= args.durationSeconds) break;
    await new Promise((resolve) => setTimeout(resolve, args.intervalSeconds * 1000));
  }

  const values = samples.map((s) => s.heapBytesPerSession).filter((v) => v !== null && v !== undefined);
  const summary = {
    scale: args.scale,
    startedAt: new Date(startedAt).toISOString(),
    endedAt: new Date().toISOString(),
    sampleCount: samples.length,
    heapBytesPerSession: {
      min: values.length ? Math.min(...values) : null,
      max: values.length ? Math.max(...values) : null,
      last: values.length ? values[values.length - 1] : null,
    },
    samples,
  };

  fs.mkdirSync(OUT_DIR, { recursive: true });
  const outFile = path.join(OUT_DIR, `monitor-soak-${args.scale}-${Date.now()}.json`);
  fs.writeFileSync(outFile, JSON.stringify(summary, null, 2));
  console.log(`Written to ${outFile}`);
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
