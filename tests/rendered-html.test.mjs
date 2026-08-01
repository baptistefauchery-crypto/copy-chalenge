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

test("server-renders the Dicta setup experience", async () => {
  const response = await render();
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type") ?? "", /^text\/html\b/i);

  const html = await response.text();
  assert.match(html, /<html lang="fr">/);
  assert.match(html, /<title>Dicta/);
  assert.match(html, /Je regarde\./);
  assert.match(html, /Préparer la caméra/);
  assert.match(html, /Continuer sans caméra/);
  assert.match(html, /manifest\.webmanifest/);
  assert.doesNotMatch(html, /codex-preview|Your site is taking shape/);
});

test("ships the Android PWA and local vision assets", async () => {
  const manifest = JSON.parse(await readFile(new URL("../public/manifest.webmanifest", import.meta.url), "utf8"));
  assert.equal(manifest.short_name, "Dicta");
  assert.equal(manifest.id, "/");
  assert.equal(manifest.display, "standalone");
  assert.deepEqual(manifest.display_override, ["standalone"]);
  assert.equal(manifest.orientation, "portrait-primary");

  await Promise.all([
    access(new URL("../public/sw.js", import.meta.url)),
    access(new URL("../public/icons/icon-192.png", import.meta.url)),
    access(new URL("../public/icons/icon-512.png", import.meta.url)),
    access(new URL("../public/models/face_landmarker.task", import.meta.url)),
    access(new URL("../public/mediapipe/wasm/vision_wasm_internal.wasm", import.meta.url)),
  ]);

  const serviceWorker = await readFile(new URL("../public/sw.js", import.meta.url), "utf8");
  assert.match(serviceWorker, /CACHE_VERSION = "dicta-v4"/);
  assert.match(serviceWorker, /icon-maskable-512\.png/);

  const pwaProvider = await readFile(new URL("../app/components/pwa/PwaProvider.tsx", import.meta.url), "utf8");
  assert.match(pwaProvider, /display-mode: standalone/);
  assert.match(pwaProvider, /Installer Dicta sur ce téléphone/);
  assert.match(pwaProvider, /Installer\s+l’application/);
  assert.match(pwaProvider, /Google Chrome/);
  assert.match(pwaProvider, /display-mode: fullscreen/);
});
