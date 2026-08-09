# Copy Challenge — application Android native

Ce module contient la bêta Android native de Copy Challenge. La PWA reste dans
les dossiers racine et n’est pas remplacée par ce module.

## Fonctionnalités Android

- interface Compose fidèle à la palette et aux écrans de la stable ;
- quinze challenges CP à CM2, progression locale et fragments de 1 à 100 lettres ;
- mode caméra frontale avec MediaPipe Face Landmarker et repli manuel ;
- lecture complète, calibration, mémorisation, écriture et choix ;
- fables de La Fontaine aux niveaux avancé et perfectionnement, avec découpage
  vers par vers et marqueur de fin de vers ;
- récompenses, confettis sonores et classement conservés localement ;
- mises à jour téléchargées, vérifiées et installées depuis l’application ;
- aucune photo, vidéo ni texte envoyé hors du téléphone.

## Build local

Le projet est autonome et utilise JDK 17, le wrapper Gradle 8.9 et Android
API 35. Depuis ce dossier :

```text
gradlew.bat testBetaReleaseUnitTest
gradlew.bat lintBetaRelease
gradlew.bat assembleBetaRelease
```

Le fichier APK est produit dans :

```text
app\build\outputs\apk\beta\release\app-beta-release.apk
```

Le premier lancement du wrapper peut télécharger Gradle et les dépendances
Maven. `local.properties` indique le chemin du SDK Android de la machine et
reste ignoré par Git.

## Installation de test

[Télécharger la bêta Android publiée](https://github.com/baptistefauchery-crypto/copy-chalenge/releases/download/v0.1.0-beta.8/copy-challenge-beta.apk)

L’application utilise le réseau uniquement pour vérifier et télécharger les
bêtas publiées sur GitHub. L’APK est enregistré dans son cache privé, vérifié
puis transmis à l’installateur Android.

```text
adb install -r app\build\outputs\apk\beta\release\app-beta-release.apk
```

## Signature durable des bêtas

Android n’accepte une nouvelle version par-dessus une bêta installée que si
l’identifiant d’application et le certificat de signature restent les mêmes.
Toutes les bêtas GitHub doivent donc utiliser le fichier local historique
`android/beta-debug.keystore`, qui reste ignoré par Git.

L’empreinte publique attendue du certificat est conservée dans
`signing-cert-sha256.txt`. Avant toute publication locale, construire l’APK puis
exécuter depuis la racine du dépôt :

```powershell
android\gradlew.bat -p android testBetaReleaseUnitTest lintBetaRelease assembleBetaRelease
powershell.exe -NoProfile -ExecutionPolicy Bypass -File android\scripts\verify-android-signing.ps1
```

Le second script lit uniquement le certificat public intégré à l’APK et échoue
si l’APK n’est pas signé par la clé historique.

### Publication optionnelle par GitHub Actions

Le workflow `.github/workflows/android-beta-release.yml` est déclenché par les
tags `v*-beta.*`. Sans secret configuré, il se termine sans publier. Avec le
secret, il vérifie la version, teste l’application, construit l’APK, vérifie son
certificat puis crée une prerelease GitHub.

Le seul secret Actions requis est `DICTA_BETA_KEYSTORE_BASE64`. Sa création doit
être faite manuellement depuis une machine de confiance par un administrateur du
dépôt.

Pour publier la prochaine bêta :

1. incrémenter `versionCode` et modifier `versionName` dans `app/build.gradle.kts` ;
2. vérifier localement le build et le certificat ;
3. commiter les sources ;
4. créer un tag exactement égal à `v${versionName}` ;
5. pousser le tag uniquement lorsque le secret a été configuré et vérifié.
