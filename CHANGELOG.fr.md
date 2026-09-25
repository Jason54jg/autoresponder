# Changelog

🌐 [English](CHANGELOG.md) · **Français**

Une entrée par demande : la version du mod augmente à chaque demande (1.1.1, 1.1.2, 1.1.3, ...).
La version est définie à un seul endroit : `mod.version` dans `stonecutter.properties.toml`.

## 1.1.4

### Documentation
- **README et changelog en deux langues** : l'anglais est la version de base (`README.md`, `CHANGELOG.md`),
  le français est dans `README.fr.md` et `CHANGELOG.fr.md`. Chaque fichier a un lien vers l'autre langue en haut.
- README : correction de la section Config (elle parlait encore de « l'écran YACL », retiré depuis 1.1.1),
  exemple de jar mis à jour, description des flèches `‹ ›` et des libellés traduits de l'écran de config.
- Aucun changement de code du mod.

## 1.1.3

### Langues
- Tout le texte du mod suit maintenant la **langue choisie dans Minecraft** : écran de config (onglets,
  libellés, boutons, valeurs des styles de notice et positions d'overlay), notices « réponse trouvée » /
  « aucune réponse connue », retours des commandes `/ar` (`on`, `off`, `reload`, `add`) et message du
  raccourci « toggle ». Avant, ces textes étaient écrits en dur (français).
- Langues fournies : **anglais (en_us, langue par défaut), français (fr_fr), espagnol (es_es),
  allemand (de_de)**. Toute autre langue Minecraft retombe sur l'anglais.
- Ajouter une langue = copier `assets/autoresponder/lang/en_us.json` en `<code>.json` et traduire.
- Les noms propres (« AutoResponder », « Chat Games » ») et le contenu de `autoresponder_questions.json`
  ne sont pas traduits (ils dépendent du serveur).

## 1.1.2

- **Abandon de 1.20.1 et 1.21.1** (demande utilisateur) : retirées du build (`settings.gradle.kts`,
  `stonecutter.properties.toml`). Versions supportées : **26.1.2, 26.2, 26.3**. Aucun changement de code du mod.
- `gradlew build` sans préfixe de version ne cible donc plus que ces trois versions.

## 1.1.1

### Nouvelles versions de Minecraft
- **Ajout de Minecraft 26.3** (Fabric API 0.161.0+26.3, Fabric Loader 0.19.5, ModMenu 21.0.0).
- Build multi-version via Stonecutter : un jar par version (`autoresponder-1.1.1+<mc>.jar`).
  Versions qui compilent et chargent : **26.1.2, 26.2, 26.3**.
- 1.20.1 et 1.21.1 étaient déclarées dans le build mais pas supportées (abandonnées en 1.1.2).

### Compatibilité 26.3
- 26.3 passe de GLFW à SDL : la touche « toggle » n'utilise plus `GLFW.*` ni `InputConstants.Type.KEYSYM`
  (retirés). Constructeur `KeyMapping` à 3 arguments + `InputConstants.UNKNOWN`, valable de 26.1 à 26.3.
- Écran de config : aucun code de bouton de souris codé en dur, EditBox vanilla (déjà compatible avec le
  nouveau `TextInputManager` SDL).
- Vérifié : compilation propre 26.1.2 / 26.2 / 26.3, et démarrage du client 26.3 avec le mod chargé
  (ressources rechargées, atlas GUI créé sans erreur de sprite).

### Écran de configuration (`/ar`)
- Refonte complète, sans YACL : palette sombre, coins arrondis via textures 9-slice
  (`textures/gui/sprites/rounded_rect.png`, `pill.png`) teintées à la volée, survol animé.
- Corrigé : les cadres de l'onglet « Chat Games » se dessinaient par-dessus l'onglet « General ».
- Corrigé : texte des champs (cooldown, template) collé en haut/à gauche — `EditBox` non bordée ne
  centre pas son texte, décalage compensé.
- Corrigé : la texture du switch on/off s'affichait en « texture manquante » (bordure 9-slice = moitié
  exacte de l'image, rejetée par l'atlas).
- Corrigé : chevrons `‹ ›` des champs à choix — ils sont maintenant aux deux bords, clic gauche =
  valeur précédente, clic droit = suivante (avant : toujours « suivante »).
- Le panneau s'adapte à la résolution / GUI scale.
- Titre « AutoResponder » centré au-dessus du panneau, en gras avec ombre.

### Détection / notifications
- Détection « Chat Games » robuste aux espaces et sauts de ligne autour du préfixe.
- Ignore les messages de résultat de manche (« X answered Y in 2.1s! »).
- Style de notice configurable : chat / overlay (position réglable) / toast / aucun.

### Commandes
- `/ar` ouvre l'écran de config ; `/ar on|off`, `/ar reload`, `/ar add <trigger> | <réponse> | [min] [max]`.
- Un seul fichier de données : `autoresponder_questions.json`.

## 1.1.0
- Version de départ de ce changelog.
