# AutoResponder

🌐 [English](README.md) · **Français**

Mod Fabric (client) qui répond **automatiquement** aux chat games du serveur (anciennement *autochatgames*).

## Installation

Dans `.minecraft/mods/`, mets 3 jars :

1. Le mod : `autoresponder-<version>+<mc>.jar` (ex. `autoresponder-1.1.4+26.3.jar`)
2. **Fabric API**
3. **Fabric Language Kotlin**

Versions Minecraft supportées : **26.1.2, 26.2, 26.3** (JDK 25). Prends le jar qui correspond à ta version. Historique des versions : [CHANGELOG.fr.md](CHANGELOG.fr.md).

Langues : le mod suit la langue de Minecraft (anglais, français, espagnol, allemand ; autre langue = anglais). Pour en ajouter une, copie `src/main/resources/assets/autoresponder/lang/en_us.json` en `<code_langue>.json` et traduis.

Écran de config maison (pas de dépendance externe). **ModMenu** est optionnel : mets-le dans `mods/` si tu veux l'entrée "AutoResponder" dans son menu, sinon utilise `/ar`.

## Flux "Chat Games »" (validation par commande)

Quand un message reçu commence **exactement** par `Chat Games »` (espaces/retours à la ligne autour ignorés — certains serveurs paddent le message) :

1. Le mod analyse le reste du message et cherche une entrée correspondante dans `autoresponder_questions.json`.
2. Si trouvée : affiche une notice **locale côté client** (jamais envoyée au serveur, style configurable — voir plus bas) :
   `[AutoResponder] Réponse trouvée ! Envoi automatique programmé.`
   Puis, après un délai aléatoire tiré entre `mindelay` et `maxdelay` (ms), envoie la commande de validation configurée au serveur, par ex. `/cg valid Rebirth | 1532`.
3. Si **aucune** entrée ne correspond : notice locale `[AutoResponder] Aucune réponse connue pour ce Chat Games, réponds toi-même !` — rien n'est envoyé au serveur, à toi de répondre à la main.

Le texte des notices suit la langue de Minecraft. Aucune réponse publique instantanée n'est jamais envoyée pour ces messages, dans un cas comme dans l'autre.

`/cg valid ...` (ou `/chatgame valid ...`) est la commande du **plugin du serveur**, envoyée automatiquement en arrière-plan (`connection.sendCommand`, ne passe pas par le chat local). Elle n'apparaît pas dans le tab-complete de nos commandes `/ar` — normal, ce n'est pas une commande à nous, et si le serveur ne l'a pas déclarée dans son propre arbre Brigadier, elle n'apparaîtra pas non plus au tab-complete général du jeu. Rien à faire côté mod.

### Config `autoresponder_questions.json`

```json
{
  "commandTemplate": "/cg valid {response} | {timer}",
  "questions": [
    {
      "trigger": "First one to say Rebirth",
      "response": "Rebirth",
      "mindelay": 1000,
      "maxdelay": 2000
    }
  ]
}
```

- `trigger` : sous-chaîne cherchée dans le message (après le préfixe `Chat Games »`).
- `response` : valeur injectée dans `{response}`.
- `mindelay` / `maxdelay` : bornes en millisecondes du délai aléatoire avant envoi (s'ajoute au **cooldown de base**, voir écran de config).
- `commandTemplate` : gabarit de la commande finale. Placeholders `{response}` et `{timer}` (délai réellement tiré, en ms — cooldown de base inclus). Change-le en `/chatgame valid {response} | {timer}` si besoin.

## Autres solveurs (messages sans préfixe "Chat Games »")

| Type de message | Réponse |
|---|---|
| `First one to say X` | renvoie `X` |
| `Unscramble abc` | trouve le mot (dictionnaire `words.txt`) |
| `What is 9 x 6?` | calcule → `54` |
| `Answer the following question: ...` | banque de réponses (`questions.json`) |
| entrées de `responders.json` | réponse fixe configurée |

Réponse envoyée dans le **chat public**, délai fixe 800-1400ms + cooldown de base. Ce chemin ne s'applique qu'aux messages qui ne commencent **pas** par `Chat Games »`. Purement statique (fichiers de ressources) — pas de commande pour y ajouter des entrées, seul `/ar add` (flux Chat Games) est disponible.

## Commandes (`/ar` ou `/autoresponder`)

**Important : ce ne sont PAS les mêmes commandes que `/cg valid ...` / `/chatgame valid ...`.** Celles-ci sont celles du **plugin du serveur** (voir `commandTemplate` plus haut), notre mod les envoie automatiquement en arrière-plan sans passer par le chat. Le mod utilise volontairement un préfixe différent (`/ar`) pour ne jamais entrer en collision avec elles — si notre mod enregistrait aussi `/cg` comme commande client, taper `/cg valid ...` à la main serait intercepté par notre propre arbre de commandes (qui n'a pas de sous-commande "valid") et rejeté avant même d'atteindre le serveur.

| Commande | Effet |
|---|---|
| `/ar` | ouvre l'écran de config (voir plus bas) |
| `/ar on` · `/ar off` | activer / désactiver sans ouvrir l'écran |
| `/ar reload` | recharger la config |
| `/ar add <trigger> \| <réponse> \| [mindelay] [maxdelay]` | ajouter/remplacer un trigger "Chat Games »" ; délais optionnels (défaut 1000/2000), séparés par `\|` ou juste un espace |

`/autoresponder` fonctionne comme alias identique à `/ar`.

## Écran de config

Écran maison (pas de dépendance externe), accessible via `/ar` ou via le menu ModMenu (Mods → AutoResponder → Config) si ModMenu est installé. Deux onglets :

**Général**
- **Actif** — bouton on/off.
- **Cooldown de base (ms)** — délai additionnel appliqué à *toutes* les réponses programmées (flux "Chat Games »" et autres solveurs), en plus du `mindelay`/`maxdelay` propre à chaque entrée. Borné entre 2000 et 10000 ms — c'est souvent la plus grosse part du délai perçu (ex. solveur math 800-1400ms + cooldown de base ≈ 2800-3400ms).
- **Style de notice** — comment afficher "réponse trouvée" / "aucune réponse connue" : `Chat` (message dans le chat), `Overlay` (texte dessiné directement à l'écran, position réglable, disparaît après 3s), `Toast` (popup coin haut-droit, style notification vanilla), `Aucun` (rien). Les flèches `‹ ›` changent la valeur : clic à gauche = précédente, à droite = suivante.
- **Position de l'overlay** — où dessiner le texte quand le style est `Overlay` : coins/centre haut/bas. Sans effet pour les autres styles.

**Chat Games »**
- **Modèle de commande** — équivalent au `commandTemplate` du JSON (`{response}`/`{timer}`).
- **Triggers Chat Games »** — zone de texte, une entrée par ligne au format `trigger|response|mindelay|maxdelay` (les deux derniers champs sont optionnels, défaut 1000/2000 si omis ou invalides).

Le bouton **Sauvegarder** réécrit entièrement `autoresponder_questions.json` avec le contenu du menu — toute modification faite à la main dans le fichier pendant que l'écran est ouvert sera écrasée à la sauvegarde. **Annuler** ferme sans rien changer.

## Config

Éditable dans `.minecraft/config/autoresponder/` :

- `autoresponder_questions.json` — triggers "Chat Games »" (trigger/response/mindelay/maxdelay + commandTemplate), alimenté par `/ar add` et par l'écran de config
- `words.txt` — dictionnaire unscramble (1 mot/ligne)
- `questions.json` — questions → réponses (banque `Answer the following question:`)
- `responders.json` — réponses fixes (fallback, hors flux "Chat Games »")
- `learned.json.migrated` / `learned_questions.json.migrated` — anciens fichiers d'une version antérieure (avant stockage unifié), conservés tels quels, non relus

## Compiler

```
gradlew.bat :26.3:build :26.2:build :26.1.2:build
```
Un jar par version dans `versions/<mc>/build/libs/`. La version du mod se change dans
`stonecutter.properties.toml` (`mod.version`).

---
⚠️ Automatiser les chat games peut enfreindre le règlement du serveur.
