# Moteur d'attention

Le module expose `MediaPipeAttentionDetector`, sans dépendance avec l'interface de la dictée.
Il fonctionne uniquement dans le navigateur et ne transmet ni ne conserve aucune image.

## Cycle de vie

```ts
const detector = new MediaPipeAttentionDetector({ analysisFps: 10 });
const unsubscribe = detector.subscribe((reading) => {
  // reading.state: "screen" | "notebook" | "unknown"
});

await detector.start(videoElement); // demande la caméra frontale

detector.beginCalibration("screen");
// attendre 2 à 3 secondes
detector.finishCalibration("screen");

detector.beginCalibration("notebook");
// attendre 2 à 3 secondes
detector.finishCalibration("notebook");

const calibration = detector.getCalibration();
// Une qualité faible indique que les deux poses sont trop semblables.

unsubscribe();
detector.stop();
```

`start()` accepte un élément vidéo fourni par l'interface, ou crée une vidéo interne.
`stop()` arrête les pistes de caméra, la boucle d'analyse et MediaPipe.

## Sémantique de sécurité

Une disparition du visage produit immédiatement `notebook` avec
`faceDetected: false`. Le texte doit donc rester caché. Après le retour du visage,
le moteur exige une détection stable de l'écran avant de revenir à `screen`.

Avant la fin des deux calibrations, un visage visible produit `unknown`. La
calibration nécessite au moins cinq images valides par pose. Les applications
peuvent utiliser `AttentionCalibration.quality` pour refuser une calibration
insuffisamment séparée (une valeur de `2` constitue un point de départ à tester).

## Configuration des ressources

Par défaut, le WASM et le modèle sont chargés depuis les CDN officiels. Pour une
PWA réellement hors ligne, copiez-les dans `public/` puis passez `wasmPath` et
`modelAssetPath` au constructeur.
