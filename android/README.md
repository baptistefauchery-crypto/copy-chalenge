# Copy Challenge — application Android native

Ce module reproduit nativement le parcours et l’apparence du site web stable
Copy Challenge (version Sites v52, commit `005169bd24b63cdf99cdee9df092f483b907a0c4`).
La PWA stable reste dans les dossiers racine et n’est pas remplacée par ce
module.

## Périmètre du clone stable

- interface Compose fidèle à la palette, aux textes et aux écrans de la stable ;
- quinze challenges CP à CM2, progression locale et fragments de 1 à 100 lettres ;
- mode caméra frontale avec MediaPipe Face Landmarker et repli manuel ;
- lecture complète, calibration, mémorisation, écriture, choix et score ;
- formule, récompenses et classement local identiques au site stable ;
- aucune permission réseau et aucun envoi de photo, de vidéo ou de texte.

Les sources et modèles OCR de la bêta précédente restent présents pour ne pas
détruire ce travail, mais ils ne sont pas exposés dans le parcours du clone
stable.

## Build local

Le projet est autonome et utilise JDK 11, le wrapper Gradle 7.6.4 et Android
API 33. Depuis ce dossier :

```text
gradlew.bat testBetaDebugUnitTest
gradlew.bat assembleBetaDebug
```

Le fichier APK est produit dans :

```text
app\build\outputs\apk\beta\debug\app-beta-debug.apk
```

Le premier lancement du wrapper peut télécharger Gradle et les dépendances
Maven. `local.properties` indique le chemin du SDK Android de la machine et
reste ignoré par Git.

## Installation de test

```text
adb install -r app\build\outputs\apk\beta\debug\app-beta-debug.apk
```

L’APK est signé avec une clé de débogage locale générée dans
`beta-debug.keystore` (fichier ignoré par Git). Cette clé est destinée aux
tests de la bêta uniquement ; une clé de distribution et une configuration
Play devront être ajoutées avant toute publication publique.
