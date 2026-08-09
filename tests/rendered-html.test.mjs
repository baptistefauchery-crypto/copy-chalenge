import assert from "node:assert/strict";
import { access, readFile } from "node:fs/promises";
import test from "node:test";

async function render() {
  const workerUrl = new URL("../dist/server/index.js", import.meta.url);
  workerUrl.searchParams.set("test", `${process.pid}-${Date.now()}`);
  const { default: worker } = await import(workerUrl.href);
  return worker.fetch(
    new Request("http://localhost/", { headers: { accept: "text/html" } }),
    { ASSETS: { fetch: async () => new Response("Not found", { status: 404 }) } },
    { waitUntil() {}, passThroughOnException() {} },
  );
}

test("server-renders the Copy Challenge setup experience", async () => {
  const response = await render();
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type") ?? "", /^text\/html\b/i);

  const html = await response.text();
  assert.match(html, /<html lang="fr">/);
  assert.match(html, /Niveau de classe/);
  assert.match(html, /level-picker/);
  assert.match(html, /Lancer un challenge\./);
  assert.doesNotMatch(html, /Vérifier|orthographe|OCR|Photographier/);
  assert.match(html, /Continuer sans cam/);
  assert.match(html, /manifest\.webmanifest/);
  assert.doesNotMatch(html, /codex-preview|Your site is taking shape/);
});

test("ships the Android PWA and local vision assets", async () => {
  const manifest = JSON.parse(await readFile(new URL("../public/manifest.webmanifest", import.meta.url), "utf8"));
  assert.equal(manifest.id, "/");
  assert.equal(manifest.display, "standalone");
  assert.deepEqual(manifest.display_override, ["standalone"]);
  assert.equal(manifest.orientation, "portrait-primary");

  await Promise.all([
    access(new URL("../public/sw.js", import.meta.url)),
    access(new URL("../public/icons/icon-192.png", import.meta.url)),
    access(new URL("../public/icons/icon-512.png", import.meta.url)),
    access(new URL("../public/icons/copy-challenge-option-a.png", import.meta.url)),
    access(new URL("../public/icons/copy-challenge-option-b.png", import.meta.url)),
    access(new URL("../public/models/face_landmarker.task", import.meta.url)),
    access(new URL("../public/mediapipe/wasm/vision_wasm_internal.wasm", import.meta.url)),
  ]);

  const serviceWorker = await readFile(new URL("../public/sw.js", import.meta.url), "utf8");
  assert.match(serviceWorker, /CACHE_VERSION = "copy-challenge-v7"/);
  assert.doesNotMatch(serviceWorker, /dictionaries|ocr/i);

  const pwaProvider = await readFile(new URL("../app/components/pwa/PwaProvider.tsx", import.meta.url), "utf8");
  const offlineStatus = await readFile(new URL("../app/components/pwa/OfflineStatus.tsx", import.meta.url), "utf8");
  const dictaApp = await readFile(new URL("../app/DictaApp.tsx", import.meta.url), "utf8");
  const scoring = await readFile(new URL("../app/lib/domain/scoring.ts", import.meta.url), "utf8");
  const globals = await readFile(new URL("../app/globals.css", import.meta.url), "utf8");
  assert.match(pwaProvider, /display-mode: standalone/);
  assert.match(pwaProvider, /deferredInstallEvent/);
  assert.match(pwaProvider, /installEvent !== null && !installDismissed/);
  assert.match(pwaProvider, /Masquer la proposition d’installation/);
  assert.match(offlineStatus, /OFFLINE_NOTICE_DURATION_MS = 4000/);
  assert.match(dictaApp, /calculateScore/);
  const activeDictaApp = dictaApp.replace(/\/\*[\s\S]*?\*\//g, "");
  assert.doesNotMatch(activeDictaApp, /OCR|orthographe|SpellingCheckModal|recognizeHandwrittenText/);
  assert.match(scoring, /export function calculateScore/);
  assert.match(scoring, /MAX_SCORE = 100/);
  assert.doesNotMatch(scoring, /OCR|spellingFaults|ocrConfidence/);
  assert.doesNotMatch(globals, /score-gate|spelling-check|spelling-result/);
});

test("the production shell is generated for complete offline use and sends security headers", async () => {
  const serviceWorker = await readFile(new URL("../public/sw.js", import.meta.url), "utf8");
  const sitesPlugin = await readFile(new URL("../build/sites-vite-plugin.ts", import.meta.url), "utf8");
  const worker = await readFile(new URL("../worker/index.ts", import.meta.url), "utf8");
  assert.match(serviceWorker, /copy-challenge-v7/);
  assert.match(serviceWorker, /GENERATED_ASSETS/);
  assert.match(sitesPlugin, /listFiles\(clientDirectory\)/);
  assert.match(sitesPlugin, /asset !== "\/_headers"/);
  assert.match(worker, /Content-Security-Policy/);
  assert.match(worker, /Permissions-Policy/);
  assert.match(worker, /Strict-Transport-Security/);
});
