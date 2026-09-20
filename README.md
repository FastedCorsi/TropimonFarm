# Tropimon Better Farm

By FastedCorsi — 0.3.3

La détection fonctionne **directement en jeu**, sans écran ouvert ni repère préalable. Les habitats reçus apparaissent avec un contour vert ; le bloc visé est souligné en doré et sa fiche apparaît en bas à droite. **F8** ouvre les détails du bloc visé et le catalogue (touche reconfigurable).

## Surveillance des habitats

- Repère les vrais blocs `cobblemon:habitat_block` dans les chunks déjà reçus, jusqu'à 96 blocs.
- Position, distance, apparence et espèces d'affichage transmises par Cobblemon.
- Jusqu'à 64 repères persistants, stockés localement et séparés par serveur, sous-serveur Tropimon et dimension.
- Distingue bloc présent, absent et chunk non chargé. Un repère non chargé n'est jamais présenté comme encore actif.
- HUD automatique : informations propres au bloc visé, trois habitats proches et trois repères suivis. Jusqu'à six espèces d'affichage sont visibles dans la fiche compacte ; le nombre restant et l'accès aux détails sont indiqués.
- Contours dans le monde pour les 64 habitats chargés les plus proches dans le rayon surveillé. Le rendu respecte la profondeur ; un bloc de décor ordinaire n'est pas un habitat même s'il a la même apparence.
- Lecture bornée des entités de bloc toutes les deux secondes ; aucun scan volumique, chargement de chunk forcé ou requête de spawn.

Dans Cobblemon 1.8.0/1.8.1, les paquets ordinaires de ces blocs ne transmettent **ni phase active ni délai de spawn**. Les getters locaux correspondants contiennent des valeurs par défaut : ils ne sont pas utilisés. Les espèces d'affichage ne sont pas présentées comme la liste complète des apparitions.

## Catalogue et conditions

Le catalogue regroupe désormais les entrées **par habitat** ; la recherche accepte un habitat ou un Pokémon. Les onglets **Structure**, **Pokémon**, **Blocs** et **Infos** séparent l'aperçu, les apparitions, le terrain et les détails techniques. Le radar de proximité reste accessible en haut à droite. Le profil Tropimon est sélectionné par défaut dans ce catalogue et sa date figure dans Infos.

**Structure** affiche les modèles NBT locaux avec les textures du jeu, leurs dimensions, les principaux blocs de décor et les variantes disponibles. Glisser pour tourner, utiliser la molette pour zoomer et les flèches pour changer de variante. L'association utilise l'identifiant `PoolId` contenu dans les vrais blocs d'habitat des modèles, sans déduire une structure de son nom de fichier. Certains modèles sont des pièces d'une structure générée ; l'aperçu ne reconstitue pas l'assemblage aléatoire du serveur. Les entités, animations et surfaces de fluides ne sont pas reproduites. Un modèle absent ou trop volumineux est signalé sans inventer une image. Les fichiers sont lus hors du rendu, avec limites de taille ; seul le modèle sélectionné est conservé pour l'affichage.

Tropimon a annoncé la suppression des cycles d'habitat : le catalogue Tropimon ne filtre ni ne prédit les apparitions par phase. La simulation de cycles conservée dans l'écran avancé concerne les règles de Cobblemon standard et ne prouve pas les règles du serveur.

L'interface compacte utilise des panneaux gris, des accents verts, des boutons en relief et un repère Poké Ball. **Reconnaître le bloc / zone** montre le bloc d'habitat réellement reçu, son apparence avec icône et ses coordonnées. L'apparence seule d'un bloc ordinaire ne prouve pas un habitat.

**Blocs / zone**, depuis le catalogue ou la phase choisie, détaille les blocs au sol ou à proximité, les biomes, structures et autres critères déclarés. Les préréglages des ressources locales sont inclus et identifiés séparément. Les groupes d'exclusion restent groupés : leurs critères ne sont pas présentés comme des interdictions indépendantes. Les tags peuvent être développés pour afficher les blocs connus du client (256 premiers membres par tag, avec indication du total). Ces conditions ne sont pas évaluées sur place ; poser les blocs indiqués ne crée pas un habitat.

Depuis un bloc sélectionné dans le radar, **Cycles / Pokémon possibles** affiche les habitats candidats d'après les espèces reçues, puis les Pokémon et conditions de chaque phase. Le style activé/naturel et l'annulation des apparitions ordinaires proviennent de l'état de bloc synchronisé.

Le mod reste **uniquement client**. Choisir explicitement un candidat et un ordre (`SIMPLE`, `FIXED_RANDOM`, `FULL_RANDOM`) permet une estimation utilisant l'âge du monde reçu dans les paquets ordinaires et la position du bloc. Les tranches durent 24 000 ticks de simulation : le délai affiché n'est pas une durée garantie en secondes. L'heure solaire, le sommeil et l'horloge de la machine ne servent pas au calcul. Une horloge absente depuis dix secondes, un bloc non observé ou un changement de session/région invalide l'estimation.

Les réglages du serveur ne sont pas confirmés par ce choix. Les ressources installées et l'instantané Tropimon peuvent différer de ses datapacks. Le générateur des phases est reproductible avec ces entrées ; la sélection effective du Pokémon emploie un autre état aléatoire côté serveur. L'écran affiche donc les Pokémon **possibles selon le catalogue choisi**, pas une prochaine apparition certaine. L'analyse détaillée est dans [l'audit](docs/HABITAT-PHASE-AUDIT.md).

Le bouton Catalogue ouvre les habitats et apparitions : recherche, rareté, niveaux, poids relatif, phases et conditions disponibles. Le poids n'est jamais transformé en pourcentage de chance.

Deux profils explicites :

- Ressources des mods installés, qui ne prouvent pas le contenu des datapacks privés du serveur.
- Instantané Tropimon de HunterBoard 1.4.0, publié le 16 septembre 2026 : 53 fichiers d'habitat et 22 de spawns, appliqués par ressource et non par espèce. Ce profil est choisi par défaut dans le catalogue ; il n'est pas une synchronisation du serveur.

Les phases discontinues sont conservées. Les conditions avancées restent lisibles sous leur forme technique. Pas d'auto-capture, de scan de Pokémon cachés, de validation automatique d'un tableau de chasse, ni d'interaction automatique avec les blocs.

## Compilation et vérification

Java 21, Minecraft 1.21.1, Fabric et Cobblemon >= 1.8.0. Sans borne supérieure mineure artificielle.

```text
gradlew build remapSmokeJar
gradlew build -PcobblemonJar=<jar-de-la-version-minimale>
gradlew build -PofficialDependenciesOnly
gradlew prepareReleaseDelivery
```

Le build local exige un unique JAR Cobblemon actif. `TROPIMON_HOME` permet de choisir une instance ; la matrice utilise `-PcobblemonJar`. Un exemple de CI utilisant le minimum officiel est fourni sous tools ; aucun workflow distant n'est activé dans cette livraison. Tests unitaires, contrôle de confidentialité des sources et des JAR (archives imbriquées comprises), tests d'installation sous Windows, puis test hors ligne isolé avec `tools/VerifyClient.ps1`. Aucun journal, sauvegarde ou profil réel n'est publié.

## Distribution

Le nom public et les JAR de livraison deviennent **Tropimon Better Farm** / `TropimonBetterFarm`. L'identifiant Fabric `tropimon_farm`, les fichiers de configuration et le dépôt officiel `FastedCorsi/TropimonFarm` sont conservés pour les repères existants et l'auto-update. L'installateur reconnaît l'ancien JAR par son identifiant et le sauvegarde hors du dossier des mods avant remplacement.

Deux exemplaires identiques sont produits dans `build/release/0.3.3/local` et `build/release/0.3.3/shareable`, avec SHA-256. Ne jamais charger les deux exemplaires. Le script du dossier local attend l'arrêt de Minecraft, vérifie les empreintes, conserve l'ancien JAR hors des mods et refuse une cible modifiée depuis la préparation. Le launcher peut rester ouvert.

L'auto-update est autonome : uniquement la Release du dépôt de ce mod, SHA-256, identifiant et version exacts, préparation hors des mods, remplacement différé après arrêt du jeu sous Windows. Vérification asynchrone au démarrage, espacée d'au moins six heures entre les sessions. Désactivation locale possible dans le fichier `config/<mod_id>-updater.json`.

## Périmètre de la première version

Cette version n'est pas une copie complète de HunterBoard. Pas d'interface de combat, pas de dépendance à un autre mod développé par By FastedCorsi. Les crédits tiers figurent dans THIRD_PARTY.md. Les préférences persistantes sont conservées dans AGENTS.md.

Les tests réseau sont réalisés avec des paquets synthétiques dans un vrai client Minecraft isolé. Ils ne remplacent pas une validation connectée à un événement Tropimon en cours. Aucune partie réelle n'est pilotée automatiquement.


## Mises à jour avec consentement

Aucun téléchargement de mise à jour sans accord. Le premier écran propose uniquement d'autoriser la consultation des métadonnées GitHub (au démarrage, au plus toutes les six heures). Une seconde confirmation montre la version et demande explicitement le téléchargement du JAR et de son SHA-256. L'ancien réglage `enabled: true` ne donne aucune autorisation.

Après accord et vérification, un installateur local utilise le Java de Minecraft, attend la fermeture du jeu, sauvegarde l'ancien JAR hors des mods chargés et remplace uniquement ce mod. Aucun autre mod Tropimon ni changement de launcher n'est requis. Le dossier `mods` classique et le stockage géré Tropimon reconnu sont pris en charge ; une disposition inconnue, un fichier modifié/verrouillé ou une incompatibilité bloque l'installation sans forcer. Le nom du JAR installé est conservé pour rester enregistré par le launcher ; la version réelle se lit dans les métadonnées Fabric.

Pour modifier le choix en jeu : `/tropimonupdates tropimon_farm`. Refuser laisse le mod utilisable. Les anciennes versions dont l'updater est défectueux nécessitent un premier remplacement manuel, jeu fermé. L'accord donné pour ce mod ne s'applique pas aux autres mods. Les tests automatisés sont exécutés sous Windows ; les autres systèmes doivent encore être validés en situation réelle.

Les versions à consentement utilisent un canal de releases distinct du lien GitHub « latest » historique : sélectionner la version par son tag. Cela évite de déclencher les anciens updaters sans accord.
