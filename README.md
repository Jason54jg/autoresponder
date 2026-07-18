# AutoResponder

Mod Fabric (client) qui répond **automatiquement** aux chat games du serveur (anciennement *autochatgames*).

## Installation

Dans `.minecraft/mods/`, mets 3 jars :

1. Le mod : `autoresponder-1.0.0-mc26.2.jar`
2. **Fabric API**
3. **Fabric Language Kotlin**

Client sur la **même version que le serveur** (26.2). JDK 25.

Le mod dépend aussi de **YACL** (YetAnotherConfigLib, requis — écran de config). **ModMenu** est optionnel : mets-le dans `mods/` si tu veux l'entrée "AutoResponder" dans son menu, sinon utilise `/ar config`.

## Stockage unifié

`/ar add` (unscramble), `/ar addquestion` (banque de questions) et `/ar addtrigger` (flux "Chat Games »") écrivent tous les trois dans **un seul fichier**, `autoresponder_questions.json`. Chaque entrée porte un `kind` (`UNSCRAMBLE`, `QUESTION` ou `CHATGAMES`) qui détermine quel mécanisme de détection l'utilise — le stockage est commun, la détection reste spécifique à chaque type (anagramme pour l'unscramble, texte de question normalisé pour la banque, sous-chaîne pour "Chat Games »").

Les anciens fichiers `learned.json` et `learned_questions.json` (s'ils existent d'une version précédente) sont importés automatiquement dans `autoresponder_questions.json` au premier chargement, puis renommés en `.migrated` pour ne pas être réimportés.

## Flux "Chat Games »" (validation par commande)

Quand un message reçu commence **exactement** par `Chat Games »` :

1. Le mod analyse le reste du message et cherche une entrée `kind: CHATGAMES` correspondante dans `autoresponder_questions.json`.
2. Si trouvée : affiche une notice **locale côté client** (jamais envoyée au serveur) :
   `[AutoResponder] Réponse trouvée ! Envoi automatique programmé.`
   Puis, après un délai aléatoire tiré entre `mindelay` et `maxdelay` (ms), envoie la commande de validation configurée au serveur, par ex. `/cg valid Rebirth | 1532`.
3. Si **aucune** entrée ne correspond : notice locale `[AutoResponder] aucune reponse connue pour ce Chat Games, reponds toi-meme !` — rien n'est envoyé au serveur, à toi de répondre à la main.

Aucune réponse publique instantanée n'est jamais envoyée pour ces messages, dans un cas comme dans l'autre.

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
      "maxdelay": 2000,
      "kind": "CHATGAMES"
    },
    {
      "trigger": "What is the rarest ore in the Overworld?",
      "response": "Emerald",
      "mindelay": 800,
      "maxdelay": 1400,
      "kind": "QUESTION"
    },
    {
      "trigger": "epCerre",
      "response": "Creeper",
      "mindelay": 800,
      "maxdelay": 1400,
      "kind": "UNSCRAMBLE"
    }
  ]
}
```

- `trigger` : selon `kind`, sous-chaîne cherchée après le préfixe `Chat Games »` (`CHATGAMES`), texte exact de la question (`QUESTION`), ou mot mélangé de référence — seules les lettres comptent, `UNSCRAMBLE` matche par anagramme.
- `response` : la réponse. Pour `CHATGAMES`, valeur injectée dans `{response}`.
- `mindelay` / `maxdelay` : bornes en millisecondes du délai aléatoire avant envoi.
- `kind` : `CHATGAMES`, `QUESTION` ou `UNSCRAMBLE`. Absent = `CHATGAMES` (rétrocompatible avec l'ancien format).
- `commandTemplate` : gabarit de la commande finale (flux `CHATGAMES` uniquement). Placeholders `{response}` et `{timer}` (délai réellement tiré, en ms). Change-le en `/chatgame valid {response} | {timer}` si besoin.

## Autres solveurs (messages sans préfixe "Chat Games »")

| Type de message | Réponse |
|---|---|
| `First one to say X` | renvoie `X` |
| `Unscramble abc` | trouve le mot (dictionnaire + entrées `kind: UNSCRAMBLE`) |
| `What is 9 x 6?` | calcule → `54` |
| `Answer the following question: ...` | banque de réponses (`questions.json` + entrées `kind: QUESTION`) |
| entrées de `responders.json` | réponse fixe configurée |

Réponse envoyée dans le **chat public**. Ce chemin ne s'applique qu'aux messages qui ne commencent **pas** par `Chat Games »`.

## Commandes (`/ar` ou `/autoresponder`)

**Important : ce ne sont PAS les mêmes commandes que `/cg valid ...` / `/chatgame valid ...`.** Celles-ci sont celles du **plugin du serveur** (voir `commandTemplate` plus haut), notre mod les envoie automatiquement en arrière-plan sans passer par le chat. Le mod utilise volontairement un préfixe différent (`/ar`) pour ne jamais entrer en collision avec elles — si notre mod enregistrait aussi `/cg` comme commande client, taper `/cg valid ...` à la main serait intercepté par notre propre arbre de commandes (qui n'a pas de sous-commande "valid") et rejeté avant même d'atteindre le serveur.

Une seule commande `add`, le premier mot choisit le `kind` (mécanisme de détection) ; même syntaxe et même fichier de stockage pour les trois :

| Commande | Effet |
|---|---|
| `/ar` ou `/ar toggle` | on / off |
| `/ar on` · `/ar off` | activer / désactiver |
| `/ar reload` | recharger la config |
| `/ar config` | ouvrir l'écran de config (YACL) |
| `/ar add unscramble <scrambled> \| <mot> \| [mindelay] [maxdelay]` | apprendre un unscramble (`kind: UNSCRAMBLE`) ; délais optionnels, défaut 800/1400 |
| `/ar add question <question> \| <réponse> \| [mindelay] [maxdelay]` | apprendre une question (`kind: QUESTION`) ; délais optionnels, défaut 800/1400 |
| `/ar add chatgames <trigger> \| <réponse> \| [mindelay] [maxdelay]` | ajouter/remplacer un trigger "Chat Games »" (`kind: CHATGAMES`) ; délais optionnels, défaut 1000/2000 |

Pour les trois : `mindelay`/`maxdelay` séparés par `|` ou juste un espace, tous deux optionnels. Ce qui est appris est gardé et prioritaire sur les valeurs par défaut (`questions.json`, dictionnaire `words.txt`). `/autoresponder` fonctionne comme alias identique à `/ar`.

## Écran de config (YACL / ModMenu)

Accessible via `/ar config`, ou via le menu ModMenu (Mods → AutoResponder → Config) si ModMenu est installé.

Deux catégories :

**General**
- **Actif** — équivalent à `/ar toggle`.
- **Cooldown de base (ms)** — délai additionnel appliqué à *toutes* les réponses programmées (flux "Chat Games »" inclus, `{timer}` reste cohérent avec le délai réel envoyé), en plus du `mindelay`/`maxdelay` propre à chaque entrée. Borné entre 2000 et 10000 ms.

**Chat Games »**
- **Template de commande** — équivalent au `commandTemplate` du JSON (`{response}`/`{timer}`).
- **Triggers » Chat Games** — liste éditable (+ / - / réordonner), une ligne par entrée au format `trigger|response|mindelay|maxdelay` (les deux derniers champs sont optionnels, défaut 1000/2000 si omis ou invalides). N'affiche/n'édite que les entrées `kind: CHATGAMES` — celles ajoutées via `/ar add`/`addquestion` restent inchangées au save.

Le bouton **Save** réécrit `autoresponder_questions.json` (entrées `CHATGAMES` remplacées par le contenu du menu, les autres `kind` préservés tels quels) — toute modification faite à la main dans le fichier pendant que l'écran est ouvert sera écrasée à la sauvegarde.

## Config

Éditable dans `.minecraft/config/autoresponder/` :

- `autoresponder_questions.json` — stockage unifié (trigger/response/mindelay/maxdelay/kind + commandTemplate), alimenté par `/ar add`, `/ar addquestion`, `/ar addtrigger` et par l'écran YACL
- `words.txt` — dictionnaire unscramble par défaut (1 mot/ligne)
- `questions.json` — questions → réponses par défaut (banque `Answer the following question:`, pas de délai — utilise 800/1400ms)
- `responders.json` — réponses fixes (fallback, hors flux "Chat Games »")
- `learned.json.migrated` / `learned_questions.json.migrated` — anciens fichiers, conservés tels quels après migration (non relus)

## Compiler

```
gradlew.bat build
```
Le jar sort dans `build/libs/`.

---
⚠️ Automatiser les chat games peut enfreindre le règlement du serveur.
