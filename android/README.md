# Copy Challenge — application Android native

Ce module contient la bêta Android native de Copy Challenge. La PWA reste dans
les dossiers racine et n’est pas remplacée par ce module.

## Fonctionnalités Android

- interface Compose fidèle à la palette, aux textes et aux écrans de la stable ;
- quinze challenges CP à CM2, progression locale et fragments de 1 à 100 lettres ;
- mode caméra frontale avec MediaPipe Face Landmarker et repli manuel ;
- lecture complète, calibration, mémorisation, écriture et choix ;
- scan final avec caméra arrière non miroir et PP-OCRv6 Small hors ligne ;
- détection du début de la dictée pour écarter les exercices précédents ;
- comparaison au texte attendu, confiance OCR et fautes intégrées au score ;
- fables de La Fontaine aux niveaux avancé et perfectionnement, avec découpage
  vers par vers et marqueur de fin de vers ;
- récompenses, confettis sonores et classement conservés localement ;
- aucune photo, vidéo ni texte envoyé hors du téléphone.

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

[Télécharger la bêta Android publiée](https://github.com/baptistefauchery-crypto/copy-chalenge/releases/download/v0.1.0-beta.4/copy-challenge-beta.apk)

L’application utilise le réseau uniquement pour vérifier les bêtas publiées sur GitHub. Elle contrôle les mises à jour au lancement et à chaque retour au premier plan, puis affiche un bouton de téléchargement lorsqu’une version plus récente est disponible. Les images, vidéos et textes restent sur le téléphone.

```text
adb install -r app\build\outputs\apk\beta\debug\app-beta-debug.apk
```

L’APK est signé avec une clé de débogage locale générée dans
`beta-debug.keystore` (fichier ignoré par Git). Cette clé est destinée aux
tests de la bêta uniquement ; une clé de distribution et une configuration
Play devront être ajoutées avant toute publication publique.
