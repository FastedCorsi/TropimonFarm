# Tropimon Farm 0.2.0

By FastedCorsi

Le radar ouvre maintenant une vue **Cycles / Pokémon possibles** pour un habitat sélectionné. Elle rapproche les espèces d'affichage reçues des pools du catalogue choisi et présente des candidats, puis les Pokémon, niveaux, poids relatifs et conditions de chaque phase.

Une estimation de phase est disponible après choix explicite du candidat et de l'ordre. Les formules SIMPLE, FIXED_RANDOM et FULL_RANDOM utilisent l'âge total reçu dans les paquets ordinaires, par tranches de 24 000 ticks, et la position du bloc. L'ordre et le pool ne sont pas confirmés par le serveur. Les données locales et le profil Tropimon restent clairement identifiés.

Le mod reste uniquement client. Les valeurs par défaut de phase côté client ne sont jamais des observations. Le bloc absent, l'horloge périmée et le changement de contexte invalident l'estimation. Les propriétés synchronisées de style et d'annulation des apparitions ordinaires sont visibles dans le radar.

Le choix effectif du Pokémon utilise un autre état aléatoire côté serveur : cette version affiche les possibilités du catalogue et leurs conditions, sans annoncer le prochain Pokémon comme certain.

Vérification : compilation et tests unitaires sur Cobblemon 1.8.0 et 1.8.1, client Minecraft isolé, comparaison des trois formules à Cobblemon, changements de phase, chargement/déchargement, suppression de bloc, dimension et reconnexion. Les diagnostics sont exclus des JAR livrés. Les tests d'installation différée et les contrôles de confidentialité des sources et archives accompagnent le build. Aucun test sur le serveur public Tropimon ; les paramètres actuels de ses datapacks restent non vérifiés.

La Release contient un JAR partageable et son SHA-256. La copie locale de même version est préparée séparément avec l'installation différée ; elle n'est pas publiée.
