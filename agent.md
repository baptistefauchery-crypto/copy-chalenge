# Notes de maintenance Copy Challenge

Le projet contient deux livrables distincts :

- la PWA racine, publiée via le projet Sites déclaré dans `.openai/hosting.json` ;
- l’application Android native sous `android/`, distribuée par GitHub Releases.

## Validation web

Depuis la racine, utiliser `npm.cmd test` puis `npm.cmd run lint`. Le test web
compile l’application, vérifie le parcours de session, la vision locale et le
rendu HTML serveur. Le service worker doit recevoir une nouvelle version quand
le shell hors ligne change.

## Validation Android

Depuis la racine, utiliser `android\gradlew.bat testBetaDebugUnitTest` puis
`android\gradlew.bat assembleBetaDebug`. Une nouvelle bêta doit augmenter
`versionCode` et `versionName`, conserver le keystore local historique et être
vérifiée avec `android\scripts\verify-android-signing.ps1` avant publication.

La caméra Android et la caméra PWA servent au placement, à la calibration et au
suivi local de l’attention. Le parcours terminé affiche directement le score et
le classement ; aucune capture ou reconnaissance de la copie n’est incluse.

## Publication

Ne pas utiliser Sites pour distribuer l’APK Android. Ne pas modifier ou publier
la stable sans demande explicite. Après une publication PWA, contrôler la version
du service worker et le comportement réel dans le navigateur cible ; après une
publication Android, contrôler l’APK signé et l’installation sur l’appareil.
