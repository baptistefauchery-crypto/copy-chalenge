# Dicta

Prototype Android de dictée visuelle : l’élève mémorise quelques mots, regarde son cahier pour les écrire, puis choisit de revoir le fragment ou de continuer.

## Fonctionnalités V1

- PWA installable sur Android depuis une URL ou un QR code
- fonctionnement hors ligne après le premier chargement
- découpage naturel d’un texte français en fragments
- détection locale `écran / cahier` avec MediaPipe
- calibration personnalisée en deux étapes
- masquage sécurisé lorsque le visage disparaît
- mode manuel de secours
- bilan local des relectures
- aucune vidéo enregistrée ou envoyée

## Développement

```bash
npm install
npm run dev
```

Ouvrir ensuite `http://localhost:3000`. L’accès caméra sur un autre appareil nécessite une origine HTTPS.

## Vérification

```bash
npm test
npm run lint
```

Le modèle Face Landmarker et les fichiers WebAssembly MediaPipe sont servis depuis `public/` afin de rester disponibles hors connexion.
