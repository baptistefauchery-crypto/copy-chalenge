# Dicta

Prototype Android de dictée visuelle : l’élève mémorise quelques mots, regarde son cahier pour les écrire, puis choisit de revoir le fragment ou de continuer.

## Fonctionnalités V1

- PWA installable sur Android depuis une URL ou un QR code
- fonctionnement hors ligne après le premier chargement
- découpage naturel d’un texte français en fragments
- détection locale `écran / cahier` avec MediaPipe
- réglage automatique sur une courte lecture à l'écran
- masquage sécurisé lorsque le visage disparaît
- mode manuel de secours
- bilan local des relectures
- aucune vidéo enregistrée ou envoyée

## Installer sur Android

Ouvrir l’adresse publiée dans Chrome sur le téléphone, puis utiliser le bouton
`Installer` proposé par Dicta ou le menu `⋮` de Chrome → `Installer l’application`.
L’application nécessite une connexion HTTPS pour l’installation et l’accès caméra.

## Mises à jour

Dicta vérifie les nouvelles versions à l’ouverture, lorsque l’application revient
au premier plan et périodiquement lorsqu’elle reste ouverte. Une notification
`Mettre à jour` permet de recharger la nouvelle version sans interrompre une
séance en cours.

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
