/**
 * setup-libav.js
 * Downloads and extracts only the needed libav.js WASM files for the build.
 * Run before `vite build` or `npm run dev` on a fresh clone.
 * Skips download if files already exist.
 */

import { existsSync, mkdirSync, createWriteStream } from 'fs';
import { pipeline } from 'stream/promises';
import { get } from 'https';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const OUTPUT_DIR = join(__dirname, 'public', 'libav');
const JASSUB_OUTPUT_DIR = join(__dirname, 'public', 'jassub');
const VERSION = '6.8.8.0';           // file names use 4-part version
const NPM_VERSION = '6.8.8';         // npm/unpkg package uses 3-part version
const BASE_URL = `https://unpkg.com/libav.js@${NPM_VERSION}/dist/`;
const JASSUB_BASE_URL = 'https://cdn.jsdelivr.net/npm/jassub@1.8.8/dist/';

// Download both 'default' and 'webcodecs-avf' variants to support hardware decoding of AAC/AC3
const FILES = [
  `libav-${VERSION}-default.wasm.js`,
  `libav-${VERSION}-default.wasm.wasm`,
  `libav-default.js`,
  `libav-${VERSION}-webcodecs-avf.wasm.js`,
  `libav-${VERSION}-webcodecs-avf.wasm.wasm`,
  `libav-webcodecs-avf.js`,
];

const JASSUB_FILES = [
  'jassub-worker.js',
  'jassub-worker.wasm',
  'default.woff2',
];
// Files that need to be renamed: { remote: local }
const JASSUB_REMAP = {
  'jassub.umd.js': 'jassub.min.js',
};

async function download(url, dest) {
  return new Promise((resolve, reject) => {
    const file = createWriteStream(dest);
    get(url, (res) => {
      if (res.statusCode === 302 || res.statusCode === 301) {
        // Follow redirect
        file.close();
        download(res.headers.location, dest).then(resolve).catch(reject);
        return;
      }
      if (res.statusCode !== 200) {
        file.close();
        reject(new Error(`HTTP ${res.statusCode} for ${url}`));
        return;
      }
      pipeline(res, file).then(resolve).catch(reject);
    }).on('error', reject);
  });
}

async function main() {
  if (!existsSync(OUTPUT_DIR)) {
    mkdirSync(OUTPUT_DIR, { recursive: true });
  }
  if (!existsSync(JASSUB_OUTPUT_DIR)) {
    mkdirSync(JASSUB_OUTPUT_DIR, { recursive: true });
  }

  console.log('--- Setting up libav.js ---');
  for (const file of FILES) {
    const dest = join(OUTPUT_DIR, file);
    if (existsSync(dest)) {
      console.log(`✓ ${file} (already exists)`);
      continue;
    }
    const url = BASE_URL + file;
    console.log(`↓ Downloading ${file}...`);
    try {
      await download(url, dest);
      console.log(`✓ ${file}`);
    } catch (err) {
      console.error(`✗ Failed: ${file} — ${err.message}`);
      process.exit(1);
    }
  }

  console.log('\n--- Setting up JASSUB ---');
  for (const file of JASSUB_FILES) {
    const dest = join(JASSUB_OUTPUT_DIR, file);
    if (existsSync(dest)) {
      console.log(`✓ ${file} (already exists)`);
      continue;
    }
    const url = JASSUB_BASE_URL + file;
    console.log(`↓ Downloading ${file}...`);
    try {
      await download(url, dest);
      console.log(`✓ ${file}`);
    } catch (err) {
      console.error(`✗ Failed: ${file} — ${err.message}`);
      process.exit(1);
    }
  }

  // Download remapped JASSUB files (e.g., jassub.umd.js → jassub.min.js)
  for (const [remoteName, localName] of Object.entries(JASSUB_REMAP)) {
    const dest = join(JASSUB_OUTPUT_DIR, localName);
    if (existsSync(dest)) {
      console.log(`✓ ${localName} (already exists)`);
      continue;
    }
    const url = JASSUB_BASE_URL + remoteName;
    console.log(`↓ Downloading ${remoteName} → ${localName}...`);
    try {
      await download(url, dest);
      console.log(`✓ ${localName}`);
    } catch (err) {
      console.error(`✗ Failed: ${localName} — ${err.message}`);
      process.exit(1);
    }
  }

  console.log('\n✅ Setup completed successfully!');
}

main();
