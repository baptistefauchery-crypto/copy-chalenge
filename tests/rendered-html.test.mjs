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
  assert.match(html, /<title>Copy Challenge/);
  assert.match(html, /src="\/icons\/icon-192\.png"/);
  assert.match(html, /Niveau de classe/);
  assert.match(html, /level-picker/);
  assert.match(html, /Lancer un challenge\./);
  assert.doesNotMatch(html, /Je regarde\./);
  assert.match(html, /dicta-banner-tilted-notebook\.png/);
  assert.match(html, /Continuer sans caméra/);
  assert.match(html, /manifest\.webmanifest/);
  assert.doesNotMatch(html, /codex-preview|Your site is taking shape/);
});

test("ships the Android PWA and local vision assets", async () => {
  const manifest = JSON.parse(await readFile(new URL("../public/manifest.webmanifest", import.meta.url), "utf8"));
  assert.equal(manifest.short_name, "Copy Challenge");
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
  assert.match(serviceWorker, /CACHE_VERSION = "copy-challenge-v1"/);
  assert.match(serviceWorker, /icon-maskable-512\.png/);

  const pwaProvider = await readFile(new URL("../app/components/pwa/PwaProvider.tsx", import.meta.url), "utf8");
  const offlineStatus = await readFile(new URL("../app/components/pwa/OfflineStatus.tsx", import.meta.url), "utf8");
  const dictaApp = await readFile(new URL("../app/DictaApp.tsx", import.meta.url), "utf8");
  const scoring = await readFile(new URL("../app/lib/domain/scoring.ts", import.meta.url), "utf8");
  const globals = await readFile(new URL("../app/globals.css", import.meta.url), "utf8");
  assert.match(pwaProvider, /display-mode: standalone/);
  assert.match(pwaProvider, /Installer Copy Challenge sur ce téléphone/);
  assert.match(pwaProvider, /Installer\s+l’application/);
  assert.match(pwaProvider, /Google Chrome/);
  assert.match(pwaProvider, /display-mode: fullscreen/);
  assert.match(offlineStatus, /OFFLINE_NOTICE_DURATION_MS = 4000/);
  assert.match(offlineStatus, /setShowOfflineNotice\(false\)/);
  assert.match(dictaApp, /AUTO_HIDE_GRACE_MS = 2000/);
  assert.match(dictaApp, /SCORE_REVEAL_DURATION_MS = 1800/);
  assert.match(dictaApp, /requestAnimationFrame/);
  assert.match(dictaApp, /isScoreRevealComplete/);
  assert.match(dictaApp, /score-meter/);
  assert.match(dictaApp, /progressColor/);
  assert.match(dictaApp, /Math\.pow\(progressRatio, 1\.65\)/);
  assert.match(dictaApp, /isScoreRevealComplete && revealedScore > 0/);
  assert.match(dictaApp, /calculateScore/);
  assert.match(dictaApp, /LEADERBOARD_STORAGE_KEY/);
  assert.match(dictaApp, /leaderboard\.slice\(0, 5\)/);
  assert.match(dictaApp, /DICTATION_PROGRESS_STORAGE_KEY/);
  assert.match(dictaApp, /useSyncExternalStore/);
  assert.match(dictaApp, /level-menu/);
  assert.doesNotMatch(dictaApp, /NEXT_DICTATION_OPTION/);
  assert.match(dictaApp, /cameraMode === "manual" \|\| calibrationPhase === "ready"/);
  assert.doesNotMatch(dictaApp, /Chaque relecture aide à mieux connaître sa mémoire/);
  assert.doesNotMatch(dictaApp, /Challenge terminé/);
  assert.doesNotMatch(dictaApp, /fragments revus/);
  assert.match(dictaApp, /<span>relecture<\/span>/);
  assert.match(dictaApp, /calibration-dictation/);
  assert.match(dictaApp, /J&apos;ai lu/);
  assert.doesNotMatch(dictaApp, /Regardez la caméra/);
  assert.doesNotMatch(dictaApp, /Arrondi au mot supérieur/);
  assert.match(scoring, /export function calculateScore/);
  assert.match(scoring, /MAX_SCORE = 100/);
  assert.match(scoring, /SCORE_POINTS_PER_LETTER = 80/);
  assert.match(scoring, /REVIEW_SCORE_MULTIPLIER = 0\.8/);
  assert.match(scoring, /export function getScoreReward/);
  assert.match(globals, /confetti-fall 6\.5s/);
  assert.match(globals, /confetti-from-left/);
  assert.match(globals, /confetti-from-right/);
  assert.match(globals, /summary-shell/);
  assert.match(globals, /summary-score-bounce/);
  assert.match(globals, /score-meter-fill/);
  assert.match(globals, /score-reward/);
});
