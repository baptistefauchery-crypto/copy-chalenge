# Consignes pour les agents du projet Dicta

Ce fichier documente les erreurs rencontrées lors des publications de la
version bêta et la procédure à suivre pour ne pas les reproduire.

## Problème principal : stable et bêta ne sont pas le même projet

Le fichier local `.openai/hosting.json` pointe vers le projet stable. C’est
volontaire pour protéger la stable, mais cela devient une erreur si une
archive destinée à la bêta est construite directement à partir de ce fichier.
Sites vérifie que le `project_id` déclaré dans `dist/.openai/hosting.json`
correspond au projet ciblé.

Projets connus :

- Stable : `appgprj_6a6db9b98a94819199a47397cb6a8ec3`
- Bêta : `appgprj_6a705c94d6748191b45931c562523adf`

Pour publier la bêta :

1. conserver le fichier local `.openai/hosting.json` sur la stable ;
2. construire et valider le projet ;
3. créer une copie temporaire de `dist` destinée à l’archive ;
4. mettre uniquement dans cette copie un `dist/.openai/hosting.json` qui
   contient l’identifiant bêta ;
5. vérifier le contenu de l’archive avant de l’envoyer à Sites.

Ne jamais modifier la configuration locale stable puis oublier de la restaurer.
Ne jamais publier une archive bêta dont les métadonnées déclarent la stable.

## Identifiants Sites

Les identifiants Sites sont opaques et doivent être copiés exactement depuis la
réponse de l’outil :

- utiliser le `commit_sha` réellement poussé sur le dépôt source bêta ;
- utiliser le `version_id` renvoyé par `sites_save_site_version`, avec son
  préfixe complet `...~appgver_...` ;
- utiliser le `deployment_id` renvoyé par `sites_deploy_site_version` pour le
  suivi ;
- ne jamais retaper, tronquer ou réutiliser un ancien identifiant.

Une erreur précédente venait d’un `version_id` auquel il manquait le préfixe
`appgver_`. Une autre venait du suivi avec l’identifiant d’une ancienne version.

## Archive et moteur OCR

Le build Vite génère automatiquement un fichier WebGPU/JSEP volumineux dans
`dist/client/assets`, par exemple :

`ort-wasm-simd-threaded.jsep-*.wasm`

Ce fichier dépasse la limite Sites de 26 214 400 octets et n’est pas utilisé
par le backend OCR WASM standard. Avant de créer l’archive bêta :

- conserver les fichiers locaux `ocr/wasm/ort-wasm-simd-threaded.mjs` et
  `ocr/wasm/ort-wasm-simd-threaded.wasm` ;
- retirer uniquement de la copie temporaire de l’archive les fichiers JSEP
  générés automatiquement ;
- vérifier qu’aucune entrée `jsep` ne reste dans l’archive ;
- vérifier que les deux fichiers WASM standard y sont présents.

Dans `app/lib/ocr/paddle.ts`, conserver `wasmPaths` sous forme d’objet
explicite avec les chemins `mjs` et `wasm`. Ne pas revenir à un simple préfixe
`"/ocr/wasm/"`, qui peut provoquer le chargement dynamique du module JSEP
absent et l’erreur `no available backend found` sur téléphone.

## Caméras

- La vérification OCR utilise la caméra arrière et son aperçu ne doit pas être
  miroir.
- La caméra frontale utilisée pour l’attention et le regard peut rester
  miroir pour l’expérience selfie.
- La capture OCR doit rester non miroir dans le canvas envoyé au moteur OCR.

## Décision OCR et migration Android

La stratégie OCR validée est la suivante :

- pour la version web/PWA actuelle, utiliser `TrOCR Small ONNX` avec le
  runtime adapté au navigateur, après découpage de la photo en lignes de
  texte ;
- lors de la migration vers une véritable application Android native,
  remplacer ce moteur par `PP-OCRv6 Small` (détection + reconnaissance),
  exporté en ONNX et exécuté avec `ONNX Runtime Mobile` ;
- ne pas confondre cette future cible Android native avec la version PWA :
  `ONNX Runtime Mobile` est destiné à l’application native, tandis que la
  PWA doit utiliser un runtime web ;
- conserver la même interface de résultat OCR et la même comparaison avec le
  texte de référence afin que le changement de moteur ne modifie pas le
  calcul du score ni la vérification orthographique.

Cette migration Android est une décision d’architecture à garder en tête lors
des prochaines évolutions ; elle ne doit pas être effectuée dans la PWA sans
demande explicite.

## Procédure de publication bêta

1. Lire `.openai/hosting.json` et confirmer qu’il s’agit de la stable locale.
2. Exécuter `npm.cmd test`, puis `npm.cmd run lint`.
3. Exécuter `npm.cmd run build` si le build de test n’est plus récent.
4. Synchroniser le dépôt source bêta à partir de son dépôt distant, puis
   pousser le commit exact sur sa branche principale.
5. Construire l’archive à partir du `dist` validé et de la configuration bêta
   temporaire.
6. Contrôler l’archive : métadonnées bêta, fichiers obligatoires, absence de
   JSEP, absence de fichier individuel supérieur à la limite Sites.
7. Appeler `sites_save_site_version` une seule fois avec le commit et
   l’archive correspondants, puis conserver le `version_id` retourné.
8. Appeler `sites_deploy_site_version` avec ce `version_id` exact.
9. Suivre le déploiement avec le `deployment_id` et le `version_id` retournés
   par ce même déploiement jusqu’à `succeeded`.
10. Vérifier que l’URL bêta est en ligne et que l’URL stable n’a pas changé.

Si une sauvegarde ou un déploiement échoue, corriger la cause puis recommencer
avec une nouvelle révision source et une nouvelle archive. Ne pas réutiliser
aveuglément un ancien identifiant ou une ancienne archive.

## Nom de la bêta

Le nom demandé pour la bêta est exactement `bêta copy chalenge` (avec un seul
`l` dans `chalenge`). Il doit être cohérent dans le titre Sites, le manifeste
PWA, les métadonnées HTML et les textes d’installation. La stable conserve son
nom `Copy Challenge`.

## Autorisation permanente de publication

L’utilisateur autorise l’agent à publier automatiquement les corrections et
améliorations validées sur le site de la bêta, sans redemander une autorisation
à chaque publication.

Cette autorisation est limitée à la bêta :

- elle concerne uniquement le projet bêta et son URL bêta ;
- elle ne permet pas de publier, modifier ou redéployer la version stable ;
- la stable doit rester inchangée sauf demande explicite ultérieure de
  l’utilisateur ;
- avant chaque publication bêta, les tests et la cohérence des métadonnées du
  projet bêta doivent tout de même être vérifiés.
