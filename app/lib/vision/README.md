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
// lire naturellement à l'écran pendant environ 1,5 seconde
detector.finishCalibration("screen");

const calibration = detector.getCalibration();
// Le moteur reconnaît l'état "écran" si la tête reste orientée vers sa
// référence OU si les deux iris restent proches de leur position de référence.

unsubscribe();
detector.stop();
```

`start()` accepte un élément vidéo fourni par l'interface, ou crée une vidéo interne.
`stop()` arrête les pistes de caméra, la boucle d'analyse et MediaPipe.

## Sémantique de sécurité

Une disparition du visage produit immédiatement `notebook` avec
`faceDetected: false`. Le texte doit donc rester caché. Après le retour du visage,
le moteur exige une détection stable de l'écran avant de revenir à `screen`.

Le calcul reprend l'approche courante des projets MediaPipe de suivi du regard :
position de chaque iris relative aux coins de l'œil, orientation de tête relative
à une référence, puis lissage temporel. Références :
[MediaPipe Iris](https://github.com/google-ai-edge/mediapipe/blob/master/docs/solutions/iris.md)
et [Python-Gaze-Face-Tracker](https://github.com/alireza787b/Python-Gaze-Face-Tracker).

Avant la fin de la calibration écran, un visage visible produit `unknown`. La
calibration nécessite au moins cinq images valides. Une seconde référence
`notebook` reste acceptée par l'API pour compatibilité, mais elle n'est plus
nécessaire au parcours normal.

## Configuration des ressources

Par défaut, le WASM et le modèle sont chargés depuis les CDN officiels. Pour une
PWA réellement hors ligne, copiez-les dans `public/` puis passez `wasmPath` et
`modelAssetPath` au constructeur.
