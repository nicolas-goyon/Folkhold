# Monde Vivant — Document de conception

Oct 11, 2026 · @Nicolas

## Vision et principes

Objectif : un Minecraft solo (NeoForge 1.21.1) où le monde évolue sans le joueur — des factions PNJ qui commercent, se battent et prennent des territoires, une nature qui pousse et vieillit, des événements naturels — avec moins de grind et moins de fermes automatiques abusives.

Le projet se compose d'un modpack de base (mods existants) et de trois mods à développer : **Monde** (factions, villages, économie, quêtes), **Équilibrage** (fermes, villageois, chargement) et **Événements naturels** (séismes, effondrements).

Principes de design :

- **Le monde vit sans le joueur.** Tout état important (factions, stocks, territoires) est simulé de façon abstraite, même quand les chunks sont déchargés.
- **Le joueur est un acteur parmi d'autres.** Il peut influencer les factions, pas les piloter ; aucun système ne dépend de son intervention.
- **Des états stables, pas des courbes infinies.** Prix, populations et budgets convergent vers un équilibre ; une perturbation crée une histoire, puis le système se rééquilibre.
- **Moins de grind, pas plus de fermes.** Le confort vient du commerce et des PNJ, pas de l'automatisation illimitée.
- **Tout est configurable.** Chaque règle a un réglage (config ou datapack), pour équilibrer sans recompiler.
- **Data-driven d'abord.** Factions, recettes PNJ, structures et quêtes sont définies en JSON (datapacks), ce qui sépare le code du contenu.

## Modpack de base

Le pack cible **NeoForge 1.21.1** et est maintenu avec **packwiz** : une liste versionnée dans git, mise à jour en une commande (`packwiz update --all`), exportée en `.mrpack` importable en un clic dans Prism Launcher ou l'app Modrinth.

| Catégorie | Mod | Slug Modrinth | Statut 1.21.1 |
| --- | --- | --- | --- |
| Optimisation | Sodium | `sodium` | Validé (de mémoire) |
| Optimisation | Lithium | `lithium` | À confirmer sur NeoForge |
| Optimisation | ModernFix | `modernfix` | Validé (de mémoire) |
| Optimisation | FerriteCore | `ferrite-core` | Validé (de mémoire) |
| Optimisation | ImmediatelyFast | `immediatelyfast` | Validé (de mémoire) |
| Optimisation | Entity Culling | `entityculling` | Validé (de mémoire) |
| Nature | [Dynamic Trees](https://www.boxtoplay.com/en-bh/minecraft-hosting/minecraft-server/modrinth-mods/dynamictrees-1229) | `dynamictrees` | Vérifié (1.7.x) |
| Nature | Serene Seasons | `serene-seasons` | Validé (de mémoire) |
| Nature | [Animal Feeding Trough](https://modrinth.com/project/4dEFIhvD) | `animal_feeding_trough` | Confirmé par toi |
| Météo | [Weather, Storms & Tornadoes](https://www.curseforge.com/minecraft/mc-mods/weather-storms-tornadoes) | `weather-storms-tornadoes` | Vérifié |
| Météo | [Expanded Weather2 Dynamics](https://www.modpackindex.com/mod/109167/expanded-weather2-dynamics) | `expanded-weather2-dynamics` | Vérifié, optionnel |
| Terrain | [The Roads More Travelled](https://www.mado-hosting.com/minecraft-mods/the-roads-more-travelled) | `74oZhrEe` (ID) | Vérifié, bêta |
| Vieillissement | [Immersive Weathering: Renewed](https://modrinth.com/mod/immersive-weather-renewed/versions) | `immersive-weather-renewed` | Vérifié, portage non officiel |
| Navigation | [Small Ships](https://www.boxtoplay.com/en-gi/minecraft-hosting/minecraft-server/modrinth-mods/small-ships-255) | `small-ships` | Vérifié, bêta (2.0.0-b2.1) |
| Confort | JEI, Jade | `jei`, `jade` | Recommandé pour le dev |

Les dépendances (CoroUtil pour Weather2, Moonlight Lib pour Immersive Weathering) sont résolues automatiquement par packwiz.

Règles de maintenance :

- Toute mise à jour passe par une branche git, un lancement de test et un monde de test avant d'être fusionnée.
- Les mods à statut bêta ou non officiel sont épinglés (`packwiz pin`) pour éviter une mise à jour cassante silencieuse.
- Nos propres mods sont ajoutés au pack comme des fichiers locaux pendant le dev, puis via Modrinth s'ils sont publiés.

## Architecture générale

Les trois mods reposent sur une bibliothèque commune, `core`, qui sépare une couche abstraite toujours simulée d'une couche concrète limitée aux chunks chargés.

&#91;embedded content: architecture · 3 mods, 1 bibliothèque partagée, 2 couches\]

La couche abstraite décide de ce qui se passe (guerres, stocks, trajets) ; la couche concrète ne fait que le montrer près du joueur, puis réécrit ce qui a changé (morts, pillages, ventes). Le mod Équilibrage dépend peu de `core` et reste utilisable seul.

## Mod Monde : factions, diplomatie, territoires

Au démarrage d'un monde, 5 à 7 factions sont générées : 1 ou 2 alliées du joueur, 1 neutre, le reste hostile, chacune avec 3 à 8 villages répartis par région.

### Modèle de données

| Entité | Champs principaux |
| --- | --- |
| `Faction` | id, nom, culture (style de villages, unités), couleur et bannière, trésor, puissance militaire, attitude de base envers le joueur, traits (expansionniste, marchande, isolationniste) |
| `Relation` | paire de factions, valeur de -100 à +100, état (alliance, paix, tension, guerre), date du dernier changement |
| `Territoire` | région (grille de 16×16 chunks), faction propriétaire, village rattaché, contestation en cours |
| `Settlement` | id, faction, position, taille (hameau, village, ville, forteresse), population, garnison, stocks, bâtiments |

Tout est stocké dans un `SavedData` du niveau Overworld, sérialisé en NBT via des `Codec`.

### Règles de simulation (tick abstrait toutes les 2 400 ticks, soit 2 min)

**Phase 1 : territoires vivants, sans guerres entre PNJ.** Les territoires changent surtout par les actions du joueur et par l'expansion lente des factions.

- **Revendication** : chaque village revendique les régions qui l'entourent, selon son palier (1 région pour un hameau, jusqu'à 9 pour une ville).
- **Destruction d'un village** : tuer le capitaine et détruire le cœur du village (bannière centrale) le transforme en ruines ; ses régions passent en **non conquis**.
- **Aider une faction à s'étendre** : quêtes de colonisation (escorter des colons, livrer des matériaux) qui fondent un hameau de cette faction dans une région non conquise.
- **Rallier un village** : avec une réputation suffisante et une quête, un village peut changer de bannière et rejoindre une autre faction.
- **Expansion naturelle** : une faction prospère fonde lentement un hameau dans une région non conquise adjacente (au plus un par semaine de jeu).
- **Diplomatie** : les relations dérivent selon le commerce et les incidents ; elles décident qui commerce avec qui.
- **Déclin** : une faction sans village disparaît ; ses ruines restent dans le monde.

**Phase 2 (plus tard)** : guerres entre factions, sièges, armées et conquêtes PNJ contre PNJ, sur la même simulation.

### Paramètres datapack

Chaque faction est un fichier `data/<namespace>/monde/factions/<id>.json` (culture, unités, structures, traits, relations initiales), pour que l'ajout d'une faction ne demande aucun code.

## Mod Monde : villages, PNJ, défense

Chaque village de faction est fortifié et gardé de sorte qu'un joueur en fer sans enchantements perde un assaut frontal ; il faut du diamant enchanté, des alliés ou de la ruse.

### Villages

- Générés en structures *jigsaw* par culture (pools de bâtiments : centre, habitations, ateliers, muraille, tours, portes).
- Taille liée à l'état abstrait : un hameau a une palissade, une ville une muraille de pierre et des tours d'archers.
- Matérialisation : à l'approche du joueur, la garnison et les habitants sont créés à partir de l'état du `Settlement` ; à l'éloignement, ils sont retirés et leur état réécrit (PV, morts, équipement).

### Croissance des villages

Un village prospère monte de palier, et ses défenses changent visiblement :

| Palier | Défenses | Échanges | Condition de montée |
| --- | --- | --- | --- |
| Hameau | Palissade en bois, 2 gardes | Offres de base | Population et stocks au-dessus du seuil pendant 5 jours de jeu |
| Village | Palissade et tours de guet, archers | Plus d'offres, prix meilleurs | Idem, seuils plus hauts |
| Bourg | Mur de pierre, portes, cavaliers | Artisans spécialisés | Idem, plus une route commerciale active |
| Ville | Muraille, donjon, golems | Offres rares, auberge, quêtes avancées | Idem, plus 2 villages de la même faction reliés |

Un village qui perd sa prospérité (pillé, isolé) peut redescendre d'un palier. Les travaux se font hors de vue du joueur : le bâtiment est remplacé par sa version supérieure quand le chunk n'est pas chargé, sans toucher aux blocs posés par le joueur.

### Réputation et accueil

- La réputation se suit **par village** et **par faction** : le comportement d'un village dépend surtout de sa propre réputation, tirée par celle de sa faction.
- En entrant dans un village : hostile, les gardes attaquent ; neutre, ils surveillent et avertissent ; ami, ils saluent et les portes restent ouvertes.
- Deux villages d'une même faction commercent entre eux en priorité (section économie).

### Refuge la nuit

- La nuit, les gardes allument torches et braseros, ferment les portes et patrouillent les remparts.
- Le cœur du village empêche l'apparition de monstres dans son périmètre.
- Un joueur neutre ou ami peut se réfugier dans le village ; à partir du palier Bourg, il peut louer un lit à l'auberge contre des émeraudes.

### Types de PNJ

| PNJ | Rôle | Comportement clé |
| --- | --- | --- |
| Habitant | Population, métier économique | Routine jour/nuit, fuit au combat, se réfugie dans les bâtiments |
| Garde | Défense de base | Patrouille fixe, épée et bouclier, poursuit dans un rayon limité |
| Archer | Défense à distance | Se poste sur les tours et remparts, cible prioritaire : joueurs hostiles |
| Cavalier | Intercepteur | Patrouille hors des murs, charge les menaces isolées |
| Golem de faction | Unité lourde | Garde les portes et le centre, très résistant, lent |
| Capitaine | Chef de garnison | Buff de moral, alerte générale, sa mort démoralise (fuite partielle) |

### IA

- Basée sur les `Goal` de `PathfinderMob` (plus simple que le système `Brain` pour commencer), avec un état partagé par village : *calme*, *alerte*, *assaut*.
- Une alerte se propage : un garde attaqué déclenche l'alerte, les archers montent aux postes, les portes se ferment.
- L'agression dépend de la réputation : en dessous du seuil hostile, les gardes attaquent à vue ; au neutre, ils avertissent puis attaquent si le joueur casse ou vole.

### Équilibrage

Calibrer avec des valeurs configurables (PV, dégâts, armure par type) et un scénario de test GameTest : un joueur en armure de fer contre une garnison de village moyen doit perdre dans plus de 80 % des essais.

## Mod Monde : économie simulée

Chaque village a des stocks réels, produit et consomme selon ses métiers, et fixe ses prix selon l'écart entre stock et besoin ; les villages commercent entre eux, et le joueur n'est qu'un client de plus.

### Producteurs et recettes

Chaque métier a une recette par cycle (1 jour de jeu = 24 000 ticks), définie en datapack :

| Métier | Consomme | Produit |
| --- | --- | --- |
| Mineur | 1 outil (usure) | 6 minerai de fer, 4 charbon, 1 diamant (5 %) |
| Fermier | — | 12 blé, 6 carottes |
| Forgeron | 3 lingots de fer, 1 charbon | 1 arme ou pièce d'armure |
| Fondeur | 6 minerai, 3 charbon | 6 lingots |
| Boucher | 2 bétail (stock élevage) | 8 viande |

Une recette sans intrants ne produit rien ; le métier passe en « besoin » et achète.

### Formule de prix

```latex
P = P_{base} \times \mathrm{clamp}\left( \left( \frac{S_{cible}}{\max(S, 1)} \right)^{\varepsilon},\ 0.5,\ 3 \right)
```

S = stock actuel, S cible = stock visé du village, ε = élasticité (0,5 par défaut). Un surplus fait baisser le prix jusqu'à ×0,5 ; une pénurie le fait monter jusqu'à ×3. Le prix de vente au joueur est P × 1,15, le prix de rachat P × 0,85.

### Commerce entre villages

- À chaque tick abstrait, chaque village liste ses surplus (S > 1,5 × S cible) et ses besoins (S < 0,5 × S cible).
- Un appariement glouton relie un vendeur et un acheteur de factions non hostiles, pondéré par la distance.
- L'échange crée une **caravane** (section suivante) ; les biens arrivent après un délai proportionnel à la distance, ou se perdent si la caravane est pillée.
- Résultat attendu : les prix convergent entre villages connectés, et une guerre qui coupe une route fait monter les prix d'un côté.

### Commerce avec le joueur

- Les PNJ vendent **uniquement ce qu'ils ont en stock** : plus de réapprovisionnement magique, donc pas de « fast-reset ».
- Le joueur peut vendre ses surplus (minerai, récoltes), ce qui fait baisser leur prix localement ; il faut aller vendre ailleurs pour optimiser.
- La monnaie reste l'émeraude ; le trésor de chaque faction est fini et alimenté par ses ventes.

### Stabilité

Pour éviter les spirales : production plafonnée par la population, stocks plafonnés par la taille des entrepôts, et une légère consommation (pourriture, usure) qui retire 2 % des stocks par cycle.

## Mod Monde : caravanes, escouades, marchands ambulants

Les groupes mobiles voyagent de façon abstraite sur une route et ne deviennent des entités que lorsque leur position passe près du joueur.

### Groupes mobiles

| Groupe | Origine | Contenu | Interaction joueur |
| --- | --- | --- | --- |
| Caravane marchande | Échange entre villages | Marchand, mules chargées, 2 à 4 gardes | Commerce en route, escorte (quête), pillage (réputation) |
| Patrouille | Faction, autour de son territoire | 3 à 6 soldats, parfois un cavalier | Hostile, neutre ou alliée selon la réputation |
| Armée | Faction en guerre | 10 à 30 unités | Peut être rejointe (alliée) ou harcelée |
| Marchand ambulant | Faction neutre | 1 marchand + animal de bât | Stock rare, se déplace de village en village |
| Bandits | Faction déchue ou événement | 3 à 8 hors-la-loi | Hostiles, pillent les caravanes |

### Déplacement abstrait

- Une route est une liste de points entre deux villages, générée une fois (ligne approchée sur la heightmap, en évitant l'eau profonde).
- Le groupe avance de N blocs par tick abstrait ; sa position est interpolée sur la route.
- Quand la position entre dans la distance de simulation d'un joueur, le groupe est matérialisé au point le plus proche et prend le pathfinding vanilla sur courte distance.
- Quand il sort de la zone, il redevient abstrait avec son état (pertes, cargaison).
- Rencontres : deux groupes hostiles sur des segments proches se battent, résolu par comparaison de puissance avec un facteur aléatoire, ou réellement si le joueur est là.

### Synergie avec les mods du pack

Avec The Roads More Travelled, faire marcher les caravanes matérialisées sur leur route creusera de vrais chemins au fil du temps, ce qui donne des routes commerciales visibles. À vérifier : TRMT réagit aux mobs, pas seulement aux joueurs (son portage Forge le mentionne).

## Mod Monde : réputation et quêtes

La réputation du joueur est une valeur par faction (-1000 à +1000) qui se propage aux alliés et ennemis de cette faction ; les quêtes sont générées à partir de l'état réel du monde.

### Paliers de réputation

| Palier | Plage | Effet |
| --- | --- | --- |
| Ennemi juré | -1000 à -600 | Attaqué à vue, primes sur sa tête, raids sur sa base |
| Hostile | -600 à -200 | Attaqué à vue sur le territoire |
| Neutre | -200 à +200 | Toléré, avertissement avant attaque, commerce de base |
| Ami | +200 à +600 | Meilleurs prix, quêtes avancées, logement |
| Allié | +600 à +1000 | Recrutement d'escortes, accès aux unités d'élite, appui en guerre |

Propagation : une action envers la faction A modifie la réputation auprès de ses alliés (50 %) et de ses ennemis (-30 %), tous deux configurables.

### Quêtes générées par l'état du monde

- **Livraison** : un village en pénurie demande un bien (lié à l'économie, récompense indexée sur le prix local).
- **Escorte** : une caravane réelle part, à protéger jusqu'à destination.
- **Défense** : un village allié est menacé par une armée en route.
- **Chasse** : bandits ou monstres qui harcèlent une route commerciale.
- **Diplomatie** : porter un message pour faire monter une relation entre deux factions.

Une quête a un donneur, une condition de réussite vérifiée sur l'état du monde, une échéance et une récompense (émeraudes, objets, réputation). Elles sont proposées sur un panneau de quêtes par village et par les capitaines.

### Lore

Chaque culture a une courte histoire (fichiers texte en datapack) découverte via des livres dans les villages et les ruines ; les guerres passées laissent des ruines réelles dans le monde, ce qui fait émerger un lore propre à chaque partie.

## Mod Équilibrage : fermes, villageois, chargement de chunks

Ce mod est indépendant du mod Monde : il bride les fermes infinies par des budgets régionaux plutôt que par la détection de designs, et propose un chargement de chunks coûteux ou un rattrapage hors-ligne.

### Saturation régionale des apparitions

- Le monde est découpé en régions de 4×4 chunks ; chaque région tient un compteur de morts de mobs hostiles récentes.
- Le taux d'apparition naturel est multiplié par `max(0.1, 1 - morts / seuil)` ; le compteur décroît de 5 % par minute de jeu.
- Effet : une petite ferme reste utile, une ferme industrielle plafonne vite, et aucun design n'est « interdit ».
- Option : morts dans de l'eau qui coule ou par chute comptées double (détection simple via la source des dégâts).

### Budget des villages vanilla

- **Golems** : chaque village a un budget de golems (par défaut 4) qui se recharge d'une unité par jour de jeu.
- **Reproduction** : nombre de villageois par village plafonné selon les lits et un délai minimal entre naissances.
- **Soin des villageois zombies** : la réduction de prix par soin ne se cumule plus et expire après 7 jours de jeu.

### Échanges plus justes

- Le réapprovisionnement vanilla est remplacé par un stock par offre qui se remplit lentement (ou par le stock du village si le mod Monde est présent).
- **Reconversion propre** d'un maître : un objet ou un service payant au village relance une nouvelle liste d'offres, avec un temps de recharge de 5 jours de jeu.
- Les offres trop rentables (livres enchantés rares, objets de valeur) ont un plafond d'achats par semaine.

### Chargement de chunks

| Option | Principe | Coût | Usage |
| --- | --- | --- | --- |
| Ancre de monde | Bloc qui garde 3×3 chunks chargés | Carburant (ex. 1 bloc de charbon par heure), 2 ancres max par joueur | Machines critiques |
| Rattrapage hors-ligne | À la recharge du chunk, les blocs compatibles calculent la production manquée | Rendement 50 %, plafonné à 3 jours de jeu | Cultures, fours, ruches, machines du mod |

Le rattrapage est fiable uniquement pour nos propres blocs et quelques blocs vanilla simples (cultures, fours) ; les contraptions Redstone ne sont pas couvertes.

Tous les seuils sont dans `balance-common.toml`, avec un préréglage « doux » et un préréglage « strict ».

## Mod Événements naturels

Les tornades et orages restent confiés à Weather2 ; ce mod ajoute les événements telluriques et des catastrophes rares, avec une protection possible des constructions.

| Événement | Déclencheur | Effet | Coût technique |
| --- | --- | --- | --- |
| Séisme | Aléatoire, plus fréquent près des montagnes | Secousse de caméra, blocs instables (sable, gravier, pierre fissurée) qui tombent, fissures en surface | Moyen |
| Effondrement | Cavité sous la surface + séisme ou pluie | Une zone de 5 à 15 blocs s'effondre en blocs qui tombent | Moyen |
| Glissement de terrain | Pente forte + pluie prolongée | Terre et gravier glissent vers le bas | Moyen |
| Feu de forêt | Été (Serene Seasons) + foudre | Propagation accélérée, cendres, régénération par Dynamic Trees | Faible |
| Inondation | Fortes pluies près des rivières | Montée temporaire de l'eau sur les berges | Difficile |

### Règles

- Un événement ne se produit que dans les chunks chargés (limite technique), mais il est annoncé : grondements, animaux qui fuient, alerte des PNJ.
- Un bloc **stabilisateur** (fondations renforcées) protège un rayon de 16 blocs, comme le déflecteur de Weather2.
- Mise à jour par lots : au plus N blocs modifiés par tick, pour ne pas faire chuter les TPS.
- Lien avec le mod Monde : un village touché voit sa production baisser et lance des quêtes de reconstruction.

Pour le premier jalon, seul le séisme avec effondrements locaux est prévu ; les autres viendront après.

## Assets et contenu pour un non-artiste

La stratégie est de réutiliser au maximum le vanilla, de générer les bâtiments par code plutôt que de les dessiner, et de n'importer des créations externes que sous licence compatible.

### Personnages et unités

- Partir des modèles vanilla existants (villageois, illageois, joueur) et ne changer que la texture : une recoloration ou un uniforme suffit à distinguer une faction.
- Bannières et couleurs de faction générées par code (motifs de bannière vanilla), donc zéro dessin.
- Blockbench (gratuit) pour les rares modèles nouveaux : il exporte directement au format Minecraft.

### Bâtiments et villages

- **Génération par code** : murailles, tours, portes et palissades sont des formes paramétriques (longueur, hauteur, matériau de la culture) ; un script produit les fichiers `.nbt` de structures. C'est la voie la plus rentable pour toi.
- **Construction en jeu** : pour les bâtiments uniques, construire en créatif, sauvegarder avec un bloc de structure, puis assembler en *jigsaw*.
- **Palettes par culture** : un même bâtiment décliné en plusieurs matériaux (chêne/pierre, bouleau/grès, sapin/ardoise) par substitution de blocs, ce qui multiplie le contenu sans dessin.

### Créations trouvées sur internet

- Sources : Planet Minecraft, GrabCraft, minecraft-schematics.com.
- Toujours vérifier la licence : beaucoup de créations sont « tous droits réservés ». Garder un registre `ASSETS_LICENSES.md` (source, auteur, licence, fichier) pour chaque import.
- Préférer les créations sous Creative Commons (CC0, CC-BY) ou obtenir l'accord de l'auteur, surtout si le mod est publié.
- Pendant le travail en commun, je peux rechercher des créations, vérifier leur licence et convertir un schéma en structure.

### Textures

Pour les blocs nouveaux (stabilisateur, ancre de monde, panneau de quêtes), partir d'une texture vanilla proche et la modifier légèrement : un cadre ou une couleur suffisent.

## Stack technique et outillage

Java 21, NeoForge 1.21.1, Gradle avec ModDevGradle, un dépôt mono-repo contenant les trois mods et le modpack.

| Besoin | Outil | Remarque |
| --- | --- | --- |
| Base du projet | Template MDK NeoForge (ModDevGradle) | Générateur officiel sur le site de NeoForge |
| Noms lisibles | Mappings Mojang + Parchment | Paramètres et Javadoc nommés |
| IDE | IntelliJ IDEA + plugin Minecraft Development | Run configs client/serveur générées |
| Données | Datagen NeoForge | Génère recettes, loot tables, tags, modèles, traductions |
| Tests | GameTest (intégré) + JUnit pour la simulation pure | La simulation abstraite est testable sans lancer le jeu |
| Profilage | Spark | Mesure du coût par tick de chaque système |
| Config | `ModConfigSpec` (TOML) | Un fichier par mod |
| Modpack | packwiz | Liste versionnée, export `.mrpack` |
| CI | GitHub Actions | Build, tests, artefacts `.jar` |

### Organisation du dépôt

```
monde-vivant/
  modpack/           packwiz (pack.toml, index.toml, mods/)
  core/              bibliothèque partagée : sim abstraite, régions, scheduler
  monde/             factions, villages, économie, caravanes, quêtes
  equilibrage/       fermes, villageois, chargement
  evenements/        séismes, effondrements
  tools/             scripts de génération de structures (.nbt)
  docs/              ce document, notes d'équilibrage, licences d'assets
```

### Conseils pour un développeur Java/C# qui débute en modding

- Le code tourne côté **serveur logique** même en solo ; tout état de simulation vit côté serveur, le client ne reçoit que l'affichage via des paquets réseau.
- Écrire la simulation abstraite en **Java pur sans classes Minecraft** (entrées et sorties sous forme de données) : elle se teste en JUnit et se débogue sans lancer le jeu.
- Éviter les mixins tant qu'un événement NeoForge suffit (apparitions, interactions, ticks) ; les mixins servent surtout au mod Équilibrage (offres des villageois, golems).

## Roadmap et jalons

Le projet avance par jalons qui produisent chacun un monde jouable, pour garder la motivation et tester l'équilibrage tôt.

&#91;embedded content: roadmap · 6 jalons, chacun jouable\]

J1 et J2 donnent déjà un mod intéressant à jouer ; J3 est le cœur et le jalon le plus risqué, à prototyper d'abord en Java pur avec tests JUnit. Les guerres entre factions viennent après J4, en phase 2. La première session de travail en commun portera sur J0 : création du dépôt, du projet NeoForge et import du modpack.

## Risques et questions ouvertes

| Risque | Impact | Parade |
| --- | --- | --- |
| Ampleur du projet (mod Monde) | Abandon avant d'avoir quelque chose de jouable | Jalons jouables dès J1, simulation abstraite seulement en J3 |
| Coût CPU de la simulation | Perte de TPS | Tick abstrait toutes les 2 min, budget en ms par tick, profilage Spark |
| Matérialisation incohérente | PNJ qui disparaissent ou se dupliquent | Identifiants stables par unité, tests GameTest d'entrée et sortie de zone |
| Mods du pack en bêta (TRMT, Immersive Weathering: Renewed) | Crash ou corruption de monde | Versions épinglées, sauvegardes avant mise à jour |
| Pathfinding longue distance | Caravanes bloquées | Déplacement abstrait, pathfinding vanilla seulement sur courte distance |
| Contenu visuel (bâtiments) | Temps de production | Génération par code, palettes, imports sous licence |

### Questions ouvertes

- [ ] Les factions hostiles sont-elles humaines uniquement, ou aussi des cultures non humaines (illageois, piglins) ?
- [ ] Le joueur peut-il fonder sa propre faction, ou rejoindre une faction existante ?
- [ ] Faut-il un mode multijoueur, ou le solo suffit pour toujours ?
- [ ] Publier les mods sur Modrinth à terme, ou usage personnel ? (impacte les licences d'assets)
- [ ] Lithium a-t-il une version NeoForge 1.21.1 fiable ? Sinon, le retirer du pack.
