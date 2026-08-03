# Copy Challenge — application Android native

Ce module contient la bêta Android native de Copy Challenge. La PWA reste dans
les dossiers racine et n’est pas remplacée par ce module.

## Fonctionnalités Android

- interface Compose fidèle à la palette, aux textes et aux écrans de la stable ;
- quinze challenges CP à CM2, progression locale et fragments de 1 à 100 lettres ;
- mode caméra frontale avec MediaPipe Face Landmarker et repli manuel ;
- lecture complète, calibration, mémorisation, écriture et choix ;
- photo finale avec aperçu figé, confirmation ou reprise, puis reconnaissance PP-OCRv6 Small hors ligne ;
- détection du début de la dictée pour écarter les exercices précédents ;
- comparaison au texte attendu, confiance OCR et fautes intégrées au score ;
- fables de La Fontaine aux niveaux avancé et perfectionnement, avec découpage
  vers par vers et marqueur de fin de vers ;
- récompenses, confettis sonores et classement conservés localement ;
- mises à jour téléchargées, vérifiées et installées depuis l’application ;
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

[Télécharger la bêta Android publiée](https://github.com/baptistefauchery-crypto/copy-chalenge/releases/download/v0.1.0-beta.6/copy-challenge-beta.apk)

L’application utilise le réseau uniquement pour vérifier et télécharger les bêtas publiées sur GitHub. L’APK est enregistré dans son cache privé, vérifié puis transmis à l’installateur Android. Le navigateur n’est plus ouvert et aucun fichier en double n’est créé. Les images, vidéos et textes restent sur le téléphone.

```text
adb install -r app\build\outputs\apk\beta\debug\app-beta-debug.apk
```

## Signature durable des bêtas

Android n’accepte une nouvelle version par-dessus une bêta installée que si
l’identifiant d’application **et le certificat de signature** restent les
mêmes. Toutes les bêtas GitHub doivent donc utiliser le fichier local historique
`android/beta-debug.keystore`, qui reste ignoré par Git et ne doit jamais être
commité, envoyé dans une discussion ou ajouté à une archive publique.

L’empreinte publique attendue du certificat est conservée dans
`signing-cert-sha256.txt`. Avant toute publication locale, construire l’APK puis
exécuter depuis la racine du dépôt :

```powershell
android\gradlew.bat -p android testBetaDebugUnitTest assembleBetaDebug
powershell.exe -NoProfile -ExecutionPolicy Bypass -File android\scripts\verify-android-signing.ps1
```

Le second script lit uniquement le certificat public intégré à l’APK. Il échoue
si l’APK n’est pas signé par la clé historique. Ne publier aucun APK si cette
vérification échoue.

### Publication optionnelle par GitHub Actions

Le workflow `.github/workflows/android-beta-release.yml` est déclenché par les
tags `v*-beta.*`. Sans secret configuré, il se termine sans publier et affiche
seulement un avertissement. Avec le secret, il vérifie la version, reconstruit exactement
`android/beta-debug.keystore`, teste l’application, construit l’APK, vérifie son
certificat puis crée une prerelease GitHub. Il ne génère jamais une autre clé.

Le seul secret Actions requis est `DICTA_BETA_KEYSTORE_BASE64`. Sa création doit
être faite manuellement depuis une machine de confiance par un administrateur du
dépôt. Pour préparer sa valeur sous Windows sans l’afficher dans le terminal :

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("android\beta-debug.keystore")) | Set-Clipboard
```

Coller ensuite cette valeur dans **GitHub → Settings → Secrets and variables →
Actions**, puis vider le presse-papiers. Codex et le workflow ne doivent pas
tenter de créer ou modifier ce secret.

Pour publier la prochaine bêta :

1. incrémenter `versionCode` sans jamais le réinitialiser, puis modifier
   `versionName` dans `app/build.gradle.kts` ;
2. vérifier localement le build et le certificat avec les commandes ci-dessus ;
3. commiter les sources ;
4. créer un tag exactement égal à `v${versionName}` ; `versionCode` doit être
   strictement supérieur à celui de toutes les bêtas déjà taguées ;
5. pousser le tag seulement lorsque le secret a été configuré et vérifié par un
   administrateur.

La cohérence peut être contrôlée avant de créer le tag :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File android\scripts\verify-beta-version.ps1 -Tag v0.1.0-beta.6 -CheckGitHistory
```

Cette clé reste réservée au canal bêta distribué directement. Une publication
Play Store devra utiliser sa propre stratégie de signature et un autre canal.
