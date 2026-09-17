# Tropimon Farm

By FastedCorsi — 0.1.0

**F8** : radar des nouveaux blocs d'habitat (touche reconfigurable).

## Surveillance des habitats

- Repère les vrais blocs `cobblemon:habitat_block` dans les chunks déjà reçus, jusqu'à 96 blocs.
- Position, distance, apparence et espèces d'affichage transmises par Cobblemon.
- Jusqu'à 64 repères persistants, stockés localement et séparés par serveur, sous-serveur Tropimon et dimension.
- Distingue bloc présent, absent et chunk non chargé. Un repère non chargé n'est jamais présenté comme encore actif.
- Petit HUD des trois repères épinglés les plus proches.
- Lecture bornée des entités de bloc toutes les deux secondes ; aucun scan volumique, chargement de chunk forcé ou requête de spawn.

Dans Cobblemon 1.8.0/1.8.1, les paquets ordinaires de ces blocs ne transmettent **ni phase active ni délai de spawn**. Les getters locaux correspondants contiennent des valeurs par défaut : ils ne sont pas utilisés. Les espèces d'affichage ne sont pas présentées comme la liste complète des apparitions.

## Catalogue et conditions

Le bouton Catalogue ouvre les habitats et apparitions : recherche, rareté, niveaux, poids relatif, phases et conditions disponibles. Le poids n'est jamais transformé en pourcentage de chance.

Deux profils explicites :

- Ressources des mods installés, qui ne prouvent pas le contenu des datapacks privés du serveur.
- Instantané Tropimon de HunterBoard 1.4.0, publié le 16 septembre 2026 : 53 fichiers d'habitat et 22 de spawns, appliqués par ressource et non par espèce. Ce profil ne s'active pas automatiquement hors serveur.

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

Deux exemplaires identiques sont produits dans `build/release/0.1.0/local` et `build/release/0.1.0/shareable`, avec SHA-256. Ne jamais charger les deux exemplaires. Le script du dossier local attend l'arrêt de Minecraft, vérifie les empreintes, conserve l'ancien JAR hors des mods et refuse une cible modifiée depuis la préparation. Le launcher peut rester ouvert.

L'auto-update est autonome : uniquement la Release du dépôt de ce mod, SHA-256, identifiant et version exacts, préparation hors des mods, remplacement différé après arrêt du jeu sous Windows. Vérification asynchrone au démarrage, espacée d'au moins six heures entre les sessions. Désactivation locale possible dans le fichier `config/<mod_id>-updater.json`.

## Périmètre de la première version

Cette version n'est pas une copie complète de HunterBoard. Pas d'interface de combat, pas de dépendance à un autre mod développé par By FastedCorsi. Les crédits tiers figurent dans THIRD_PARTY.md. Les préférences persistantes sont conservées dans AGENTS.md.

Les tests réseau sont réalisés avec des paquets synthétiques dans un vrai client Minecraft isolé. Ils ne remplacent pas une validation connectée à un événement Tropimon en cours. Aucune partie réelle n'est pilotée automatiquement.
