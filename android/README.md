# bêta copy chalenge — application Android

Ce module est l’application native Android de la bêta Dicta. La PWA et le
site stable restent dans les dossiers racine et ne sont pas remplacés par ce
module.

## Périmètre bêta

- parcours Compose : niveau, calibration, mémorisation, écriture, vérification et score ;
- mode caméra frontale avec MediaPipe Face Landmarker et repli manuel ;
- photo locale de la feuille avec OCR PP-OCRv6 Small ONNX Runtime ;
- dictionnaire français embarqué et vérification orthographique locale ;
- meilleur score conservé uniquement dans les préférences locales ;
- aucune permission réseau et aucun envoi de photo, de vidéo ou de texte.

## Build local

Le projet est autonome et utilise JDK 11, le wrapper Gradle 7.6.4 et Android
API 33 pour cette version bêta. Depuis ce dossier :

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
