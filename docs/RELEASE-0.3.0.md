# Tropimon Better Farm 0.3.0

By FastedCorsi

Le mod devient **Tropimon Better Farm**. La détection est désormais active directement en jeu : aucun écran ni repère préalable requis. Les habitats chargés reçoivent un contour vert, le bloc visé est souligné en doré, et une fiche compacte affiche ses coordonnées, son apparence, son style et ses espèces d'affichage synchronisées. La touche configurable F8 sélectionne ce bloc dans les détails complémentaires.

Le scan reste borné aux entités de bloc déjà reçues, toutes les 40 ticks. Le rendu se limite aux 64 habitats proches dans un rayon de 96 blocs et respecte la profondeur. Un bloc de décor de même apparence n'est jamais identifié comme habitat. Le HUD conserve les repères suivis et signale les données absentes ; la phase non synchronisée n'est pas inventée.

Uniquement client. Le catalogue, les conditions de terrain et les calculs de cycle sous hypothèses restent accessibles. L'identifiant Fabric et le dépôt officiel restent stables pour conserver les réglages, les repères et l'auto-update. Les JAR de livraison portent le nouveau nom ; l'installation différée remplace aussi une ancienne copie nommée TropimonFarm après sauvegarde vérifiée.

Validation : builds et tests sur Cobblemon 1.8.0 et 1.8.1, client Minecraft isolé avec détection sans écran et sans épingle, visée réelle du bloc, contrôle négatif sur un bloc ordinaire de même apparence, capture du HUD et des contours. Contrôles de confidentialité des sources et JAR, tests de migration du nom de fichier et de remplacement différé. Aucun test sur une partie personnelle ou sur le serveur public.
