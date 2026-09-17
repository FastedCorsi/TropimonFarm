# Audit solo des habitats — 17 septembre 2026

By FastedCorsi

Note de suivi : cet audit décrit l'état initial 0.1.0. La version 0.2.0 ajoute ensuite les indicateurs synchronisés, les pools candidats, la navigation par phase et une estimation conditionnelle fondée sur les paquets d'âge du monde. Voir README.md pour le périmètre livré ; les mentions « non implémenté » et « aucune livraison » ci-dessous décrivent uniquement la séance d'audit initiale. Le mod livré reste uniquement client, sans lectures du serveur intégré.

Audit expérimental de TropimonFarm 0.1.0, sans évolution du mod public. Référence : `docs/HUNTERBOARD-1.4.0-INTEGRATION.md` du dossier parent. Les sources de Cobblemon citées ci-dessous sont une lecture du bytecode des JAR locaux, pas une recompilation du projet amont.

## Résultat principal

La phase active n'est pas synchronisée par le NBT ordinaire du bloc. Mais deux compléments sont disponibles : les propriétés de bloc `activated_style` et `cancels_regular_spawns`, et un paquet d'éditeur créatif contenant la phase et les réglages. L'éditeur est un instantané : une transition ultérieure ne le met pas à jour.

Les phases ne progressent pas avec les captures ou le nombre de Pokémon apparus. Dans les versions examinées, elles dépendent de l'âge total du monde, de la position du bloc, du nombre de phases et de l'ordre configuré. Le compte à rebours d'un prochain Pokémon n'est pas une propriété exposée par ces classes : les activations sont soumises à des conditions et à l'aléatoire.

## Informations détectables et provenance

| Information | Serveur intégré faisant autorité | Client ordinaire sur serveur distant | Catalogue / estimation | Fiabilité et portée |
| --- | --- | --- | --- | --- |
| Présence, position, dimension, distance | Bloc et monde serveur | Oui, dans les chunks reçus | Distance calculée depuis le joueur | Observation fiable à l'instant du scan ; hors chargement = inconnu |
| Apparence `MimicId` | Réglage du bloc | Oui, NBT de chunk et mise à jour | Peut aider à reconnaître visuellement un habitat | Ne fournit pas l'identité du pool |
| Espèces `DisplaySpecies` | Espèces distinctes du pool dans le code vanilla | Oui | Peut réduire une liste de pools candidats | Toutes phases confondues, sans conditions, formes, poids ou niveaux ; pas une garantie d'apparition actuelle |
| Style activé / naturel | Objet de style réel | Oui via `activated_style`, pas via le getter local du style | Sans objet | Le drapeau indique le style, pas « une activation vient de réussir » |
| Annulation des spawns ordinaires | Réglage de remplacement ou rayon d'annulation | Oui via `cancels_regular_spawns` | Sans objet | Le rayon exact et les catégories affectées restent inconnus |
| Phase active | `currentPhase`, recalculée côté serveur | Non dans la synchronisation ordinaire | Calculable seulement avec les hypothèses de pool, nombre et ordre | Un getter client à 1 est une valeur par défaut, pas une observation |
| Nombre et ordre des phases | Pool affecté et `phaseOrder` | Non via le bloc ordinaire | Les phases du catalogue décrivent une ressource, pas l'affectation réelle du bloc | `PoolId` et ordre peuvent différer sur le serveur |
| Progression jusqu'à la phase suivante | Âge du monde modulo 24 000 | L'horloge du monde est distincte de l'heure jour/nuit ; sa correspondance doit être contrôlée pour toute future estimation | Estimation conditionnelle, en ticks | Pas encore validée comme fonctionnalité client de TropimonFarm ; ne pas transformer une hypothèse en phase confirmée |
| Déclencheur TICK / REDSTONE | Réglage du style activé | Non dans le NBT ordinaire | Certains indices visuels redstone sont observables | Un circuit visible ne prouve ni le déclencheur ni l'état interne du bloc |
| Chance par activation, portée, plafonds | Réglages du style activé | Non dans le NBT ordinaire | Une chance d'activation ne donne pas la probabilité d'une espèce | Pas de délai exact déductible de ces paramètres seuls |
| Nombre de Pokémon suivis / capacité restante | Ensemble serveur des identifiants d'entités suivies | Non via le bloc ordinaire | Compter les Pokémon visibles serait incomplet et sans attribution fiable | Le paquet créatif fournit un nombre ponctuel ; ce n'est pas une synchronisation continue |
| Phases, heure, lumière, type de position, niveaux, poids et modificateurs des spawns | Pool et conditions réellement appliqués | Pas comme réglages du bloc ordinaire | Ressources installées ou profil Tropimon explicite | L'heure/lumière locale peuvent aider à évaluer une condition connue à une position ; elles ne prouvent pas le pool ni toutes les positions candidates |
| Configuration complète via éditeur | Paquet construit depuis le bloc serveur | Réception testée en créatif ; interaction survie retourne PASS sans ouvrir l'éditeur | Contient également les pools serveur | Voie normale mais contextuelle, pas un accès supposé pour un joueur Tropimon en survie |
| Données persistées du bloc | NBT complet : pool, style, ordre et paramètres | Non via les paquets ordinaires | Sans objet | `/data` et requêtes NBT d'administration ne sont pas une source passive accessible à tous |

Le catalogue installé ne prouve pas les datapacks du serveur. Le profil Tropimon de HunterBoard 1.4.0 reste un instantané explicite : 53 fichiers d'habitats et 22 de spawns. Aucune configuration courante de Tropimon n'a été interrogée pendant cet audit.

## Preuves de synchronisation

`HabitatBlockEntity.toInitialChunkDataNbt` construit uniquement `MimicId` et `DisplaySpecies`. `toUpdatePacket` utilise le paquet standard de mise à jour d'entité de bloc. Le test compare les deux NBT, puis observe le vrai bloc client après réception ; il ne copie pas le NBT serveur complet sur le client.

Sur le pool local `cobblemon:abandoned_fortress`, cinq phases, 24 lignes de spawn et 15 espèces distinctes ont été relevées. Le serveur est placé en phase 3, tandis que le client reste en phase 1 avec `cobblemon:custom__default_pool`. Un changement serveur vers la phase 2, une mise à jour explicite et un nouveau chargement de chunk ne transmettent toujours pas la phase.

`HabitatBlockEntity.onUse` refuse l'éditeur en survie et envoie `OpenHabitatBlockEditorPacket` en créatif. Le diagnostic appelle cette interaction officielle sur le serveur intégré et vérifie la réception par le gestionnaire client officiel : `HabitatEditGUI` reçoit phase 3, paramètres d'activation et pools serveur. Il conserve phase 3 après passage réel du serveur à phase 2. Aucun paquet de sauvegarde de l'éditeur n'est envoyé.

`HabitatPools.sync(player)` ne fait pas d'envoi dans le code examiné. Lire le singleton des pools en solo ne constitue pas une preuve de synchronisation réseau : client et serveur intégré partagent le processus. Le paquet de l'éditeur est ici une preuve distincte de réception.

Les 24 classes examinées des chemins `block/habitat`, `api/habitats` et `net/messages/client/habitat` ont des SHA-256 identiques entre les JAR 1.8.0 et 1.8.1. Cette comparaison est limitée à ces classes et ne prétend pas couvrir toutes les différences entre les versions.

## Phases et activation

- **SIMPLE** : `1 + (âge / 24000) % nombreDePhases`.
- **FIXED_RANDOM** : permutation déterministe des phases initialisée par la position ; le cycle se répète.
- **FULL_RANDOM** : tirage déterministe initialisé par `âge / 24000 + position.asLong()` ; une phase peut se répéter et un cycle complet n'est pas garanti.
- Le ticker réévalue la phase lorsque l'âge du monde est un multiple de 40. En simulation nominale à 20 ticks/s, une tranche de 24 000 ticks vaut 20 minutes de simulation, sans garantie de durée murale en cas de pause ou de ralentissement.
- Une modification de l'heure jour/nuit seule ne change pas cette phase. Les conditions horaires des spawns utilisent, elles, l'heure modulo 24 000. Ces deux horloges ne doivent pas être confondues.
- Le NBT de sauvegarde ne conserve pas `currentPhase`. Une nouvelle entité de bloc démarre à 1 et le ticker la corrige à la prochaine frontière de 40 ticks. Le test de recréation positionne explicitement cette frontière ; exiger immédiatement la phase calculée avant celle-ci serait une erreur de test.
- **REDSTONE** : front montant d'un signal fort. Maintenir le signal n'entraîne pas une activation par tick ; il faut une retombée puis une nouvelle montée. Le premier montage avec un bloc de redstone a échoué ; le levier alimentant directement le bloc fournit 15 de signal fort et valide les transitions.
- **TICK** : appel d'activation à chaque tick du bloc. Le test confirme cinq tentatives en cinq appels, sans délai fixe entre ces appels. Chaque tentative peut échouer en raison de la chance, de la capacité, des conditions ou du moteur de spawn.
- **RANDOM_TICK** : valeur présente dans l'énumération, mais aucune activation via le ticker de l'habitat n'a été observée. Le bloc examiné n'implémente pas de chemin de random tick correspondant. Ne pas proposer ce mode comme mécanisme fonctionnel vérifié ; aucune mesure longue de random ticks n'a été effectuée.
- Une chance nulle et une capacité maximale nulle empêchent l'émission de l'événement d'activation. Le code vérifie aussi la présence de détails de spawn et les limites de population. Le nombre autorisé par activation est borné par la capacité restante et le plafond propre à l'activation.
- Le style naturel agit comme influence sur les spawns et éventuellement la pêche ; il n'a pas un compte à rebours indépendant de l'habitat dans les classes examinées.

## Méthode et résultats

Le diagnostic est exclusivement dans `src/smoke`, derrière `tropimon.habitatAudit`, et réutilise `SmokeClient` et `tools/VerifyClient.ps1 -HabitatAudit`. Le script crée des mondes neufs sous `build/verify-habitat-audit`. Il réutilise le JAR public 0.1.0 existant et lui ajoute un JAR de test distinct. L'updater est désactivé par le mode smoke existant.

Les frontières temporelles sont accélérées en modifiant l'âge du monde de test et en appelant le ticker officiel sur le thread serveur. Les tentatives d'activation sont observées via l'événement officiel, puis annulées dans ce test pour isoler leur comptage du hasard des apparitions. Les tests de lumière et d'heure utilisent des positions synthétiques contrôlées dans le serveur intégré et les vraies conditions Cobblemon. **Ils ne mesurent pas un taux d'apparition ni le délai jusqu'à un Pokémon réel.**

Contrôles effectués : cycles SIMPLE/FIXED_RANDOM/FULL_RANDOM, frontières 23 960/24 000/48 001/48 040 ticks, dissociation âge/heure, bornes de lumière inclusives 5 et 10 et rejet de 4 et 11, phases discontinues 1 et 3–4, condition horaire et sa borne, fronts redstone, cinq activations TICK, refus chance/capacité zéro, NBT initial/mise à jour, paquet d'éditeur, péremption de l'éditeur, sortie réelle de portée et rechargement client, suppression et recréation depuis le NBT, séparation par région/dimension, déconnexion et nouveau monde.

Le passage initial complet en 1.8.1 a réussi. Les répétitions ont révélé des hypothèses trop strictes du banc d'essai : l'identité de l'objet client ne suffit pas à qualifier le rechargement, et la phase recalculée n'est pas garantie immédiatement après recréation avant la frontière de 40 ticks. Un passage 1.8.0 a atteint sa limite de temps au retour dans le chunk. L'attente est désormais bornée par étape avec relevé des positions et états serveur/client en cas d'échec. Ces essais préliminaires ne sont pas comptés comme des validations complètes.

| Matrice finale, banc corrigé | Compilation du diagnostic | Tests unitaires | Parcours Minecraft isolé |
| --- | --- | --- | --- |
| Cobblemon 1.8.0 + Minecraft 1.21.1 | Réussie contre le JAR 1.8.0 | 9 réussis, 0 échec | 95 assertions réussies, marqueurs `HABITAT_AUDIT COMPLETE` et `TROPIMON_SMOKE_OK`, processus terminé avec code 0 |
| Cobblemon 1.8.1 + Minecraft 1.21.1 | Réussie contre le JAR installé | 9 réussis, 0 échec | 95 assertions réussies, mêmes marqueurs, processus terminé avec code 0 |

Les 95 assertions comprennent les garde-fous d'isolation et les points d'un même cycle de phase ; ce ne sont pas 95 fonctionnalités distinctes. Les deux passages finaux ont reçu un nouvel objet client au retour dans le chunk et ont terminé la nouvelle session. Les relevés locaux sont `build/habitat-audit/evidence-1.8.0.txt` et `build/habitat-audit/evidence-1.8.1.txt`. Les journaux détaillés correspondants restent dans ce même dossier ignoré, sous `run-1.8.0-final.txt` et `run-1.8.1-validated.txt`.

Les neuf tests unitaires existants passent sur 1.8.0 et 1.8.1. Les tâches Gradle `testLocalDelivery` et `prepareReleaseDelivery` ne sont pas exécutées par cet audit.

Reproduction depuis le sous-dossier du mod, avec les dépendances locales déjà disponibles :

```powershell
.\gradlew.bat remapSmokeJar test --offline
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/VerifyClient.ps1 -HabitatAudit
# Pour le minimum, fournir le même JAR 1.8.0 aux deux commandes :
.\gradlew.bat remapSmokeJar test --offline '-PcobblemonJar=<chemin-du-jar-1.8.0>'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/VerifyClient.ps1 -HabitatAudit -CobblemonJar '<chemin-du-jar-1.8.0>'
```

L'essai exige que le JAR public 0.1.0 soit déjà présent dans `build/libs`. Il ne le reconstruit pas. Le garde-fou du diagnostic refuse un dossier de jeu autre que `build/verify-habitat-audit`. Attendre la fin d'un passage avant de préparer le suivant.

## Améliorations concrètes proposées, non implémentées

1. Afficher les deux indicateurs synchronisés : « style activé/naturel » et « remplace ou supprime les spawns ordinaires », avec une explication de leur portée.
2. Ajouter des étiquettes de provenance : observé dans le chunk, serveur intégré, instantané d'éditeur, catalogue installé, profil Tropimon et estimation.
3. Proposer en solo une vue de diagnostic faisant des lectures ponctuelles sur le thread serveur : pool réel, phase/ordre, ticks jusqu'à la prochaine frontière, déclencheur, limites et conditions. Aucun accès au serveur intégré ne doit subsister après déconnexion ou changement de monde.
4. Comparer les espèces d'affichage aux pools du profil choisi et présenter des **candidats**, jamais une identification certaine. Un pool personnalisé ou des données modifiées peuvent produire le même ensemble d'espèces.
5. Après identification explicite d'un pool et de son ordre, offrir éventuellement une estimation de phase avec hypothèses visibles. Vérifier séparément la réception et la continuité de l'horloge client ; invalider après changement de session, réglage ou absence prolongée.
6. Expliquer les conditions de farm à une position choisie : heure, lumière et type d'emplacement ; conserver « inconnu » pour les paramètres serveur absents. Aucun pourcentage de chance d'espèce calculé à partir du seul poids relatif.
7. Si l'utilisateur ouvre normalement l'éditeur créatif, une observation passive de son paquet pourrait enrichir un instantané horodaté. Ne pas ouvrir automatiquement l'éditeur ni solliciter ce paquet sur le serveur public.

## Limites et confidentialité

### Complément : générateur aléatoire du prochain Pokémon

Vérification statique supplémentaire demandée le 17 septembre 2026, sur le JAR Cobblemon 1.8.1, Kotlin 2.2.21 et le JDK 21 local. Aucun essai contre le serveur public ni nouvelle livraison.

La formulation « impossible à prédire » doit être précisée : le caractère pseudo-aléatoire ne constitue pas une impossibilité mathématique de reproduction. Il faut cependant disposer de l'état interne au bon instant et des entrées exactes. Aucun accès à cet état n'a été trouvé dans les paquets d'habitat examinés ; aucune récupération de cet état à partir d'observations partielles n'a été démontrée.

Chaîne suivie dans les classes :

1. `ActivatedHabitatSpawning.activate` utilise `world.random.nextFloat()` pour la chance d'activation lorsque celle-ci est inférieure à 1. Ce test utilise le générateur du monde serveur.
2. `FixedAreaSpawner.run` appelle `Spawner.runForArea`. `BasicSpawner` prend `SpawningSelector.DEFAULT`, défini comme `FlatSpawnablePositionWeightedSelector`.
3. `Spawner.chooseBucket`, le choix du type de position, celui du détail de spawn et celui de la position utilisent des sélections pondérées. Le sélecteur peut aussi consommer un tirage pour les entrées en pourcentage lorsqu'elles existent.
4. `CollectionUtilsKt.weightedSelection` tire `random.nextFloat() * sommeDesPoidsPositifs`, puis parcourt les poids cumulés. Ses appels sans générateur explicite passent par `weightedSelection$default`, qui fournit `kotlin.random.Random.Default`.
5. Sur ce runtime JVM, `Random.Default` utilise `PlatformThreadLocalRandom`, dont `getImpl()` retourne `java.util.concurrent.ThreadLocalRandom.current()`. Le JDK conserve l'état dans le thread ; les appels successifs le font avancer. L'initialisation du seeder Java utilise les horloges du processus, avec une variante d'initialisation sécurisée configurable, pas la graine Minecraft ni les coordonnées du bloc.

Le tirage de phase `Random(âge / 24000 + position.asLong())` est donc distinct du tirage des Pokémon. Connaître ce calcul de phase ou la graine du monde ne fournit pas directement l'état de `ThreadLocalRandom`. D'autres utilisations de ce générateur sur le même thread peuvent consommer des tirages entre deux apparitions observées. Les pools, poids effectifs, positions admissibles, ordre d'itération et influences serveur sont aussi nécessaires pour reproduire la sélection.

Conclusion pratique : prédiction conditionnelle des phases envisageable avec les réglages connus ; prédiction exacte du prochain Pokémon **non démontrée avec les seules informations accessibles au mod client**. Cela ne constitue ni une preuve d'impossibilité de toute technique d'inférence future, ni une validation du runtime ou des modifications propres au serveur Tropimon. Les décompilations et relevés de bytecode complémentaires restent dans `build/habitat-audit/random-decompiled`, `kotlin-random-bytecode.txt` et `jdk21-thread-random.txt`.

### Périmètre des essais et fichiers

Aucune partie personnelle, sauvegarde personnelle ou instance publique n'a été pilotée. Les fermetures et changements de monde concernent uniquement le processus de test créé pour cet audit. Ni le launcher ni un jeu personnel ne sont arrêtés. Aucun commit, tag, push, publication ou remplacement du JAR installé.

Les interactions créatives sont vérifiées dans le serveur intégré stock, pas sous les permissions ou plugins Tropimon. Le déchargement client est testé ; le délai d'éviction physique d'un chunk serveur sur disque n'est pas mesuré. La recréation NBT est testée séparément. Le changement de région utilise le signal interne contrôlé ; le changement de dimension et la nouvelle session sont réels dans le client de test.

Les journaux bruts, captures, mondes de test et décompilations restent sous `build`, ignoré par Git. Ce rapport et les sources de test emploient des chemins portables et des données synthétiques. Le contrôle de confidentialité ne prétend pas effacer des copies historiques déjà diffusées.

Contrôles finaux : sources et rapport examinés par le vérificateur de confidentialité existant ; archive du diagnostic contrôlée séparément avec le même moteur de détection et attribution vérifiée ; aucun diagnostic dans le JAR public. Le JAR public local et l'unique JAR Farm installé conservent le SHA-256 de la livraison 0.1.0 : `9474fcc027df52b8830b192220432f668ff8ff30b0898b1c03a940b403e9f6ce`.
