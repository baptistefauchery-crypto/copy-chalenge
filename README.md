# Copy Challenge

Copy Challenge est une application web progressive (PWA) de challenge visuel.
L’élève mémorise un fragment, regarde son cahier pour l’écrire, puis choisit de
revoir les mots ou de continuer.

## Démo publiée

[Ouvrir Copy Challenge](https://dicta-memoire.baptiste-fauchery.chatgpt.site)

## Fonctionnement

1. Choisir un niveau scolaire et le nombre de lettres par étape.
2. Lire et mémoriser le fragment affiché.
3. Regarder le cahier pour écrire le fragment.
4. Choisir **Revoir** ou **Continuer**.
5. Photographier la feuille avec le bouton **Vérifier l'orthographe**.
6. Comparer le texte reconnu à la référence et révéler le score.

La caméra et MediaPipe distinguent localement le regard vers l’écran du regard
vers le cahier. Un mode manuel reste disponible si la caméra n’est pas utilisée.

## Fonctionnalités

- PWA installable sur Android depuis une URL ou un QR code
- fonctionnement hors ligne après le premier chargement
- niveaux CP, CE1, CE2, CM1 et CM2
- découpage naturel d’un texte français en fragments
- calibration courte de la lecture à l’écran
- masquage du fragment lorsque l’élève regarde son cahier
- mode manuel de secours
- scores et relectures conservés localement
- OCR manuscrit local PP-OCRv6 small et dictionnaire français hors ligne
- aucune vidéo enregistrée ou envoyée

## Installation sur Android

Ouvrir l’URL publiée dans Chrome sur le téléphone, puis utiliser le bouton
`Installer` proposé par Copy Challenge ou le menu `⋮` de Chrome →
`Installer l’application`.

L’installation et l’accès à la caméra nécessitent une connexion HTTPS.

## Développement

Prérequis : Node.js `>=22.13.0`.

```bash
npm install
npm run dev
```

Ouvrir ensuite `http://localhost:3000`. L’accès caméra depuis un autre appareil
nécessite une origine HTTPS.

## Vérification

```bash
npm test
npm run lint
```

La commande `npm test` compile l’application, exécute les tests métier et vision,
puis vérifie le rendu HTML serveur.

## Application Android native bêta

[Télécharger directement la dernière bêta Android (APK)](https://github.com/baptistefauchery-crypto/copy-chalenge/releases/download/v0.1.0-beta.3/copy-challenge-beta.apk)

Le module `android/` contient la version native installable de la bêta :
parcours Compose, caméra CameraX/MediaPipe, OCR PP-OCRv6 ONNX hors ligne et
repli manuel. La procédure de build et d’installation est documentée dans
[`android/README.md`](android/README.md).

## Structure principale

- `app/DictaApp.tsx` : interface et parcours d’une séance
- `app/globals.css` : styles et responsive design
- `app/lib/domain/` : découpage des textes, session, score et relectures
- `app/lib/vision/` : calibration et détection locale du regard
- `app/components/pwa/` : installation et état hors ligne
- `public/` : icônes, modèle Face Landmarker et fichiers WebAssembly
- `tests/` : tests métier, vision et rendu serveur
- `.openai/hosting.json` : identifiant de publication Sites

## Mises à jour

Copy Challenge vérifie les nouvelles versions à l’ouverture, lorsque
l’application revient au premier plan et périodiquement lorsqu’elle reste
ouverte. Une notification `Mettre à jour` permet de recharger la nouvelle
version sans interrompre une séance en cours.
