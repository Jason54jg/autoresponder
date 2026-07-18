# AutoResponder

Mod Fabric (client) qui répond **automatiquement** aux chat games du serveur (anciennement *autochatgames*).

## Installation

Dans `.minecraft/mods/`, mets 3 jars :

1. Le mod : `autoresponder-1.0.0-mc26.1.2.jar` (ou `-mc26.2.jar` selon ta version)
2. **Fabric API**
3. **Fabric Language Kotlin**

Client sur la **même version que le serveur** (26.1.2). JDK 25.

## Flux "Chat Games »" (validation par commande)

Quand un message reçu commence **exactement** par `Chat Games »` :

1. Le mod analyse le reste du message et cherche une entrée correspondante dans `autoresponder_questions.json`.
2. Si trouvée : affiche une notice **éphémère côté client** (actionbar, jamais envoyée au serveur) :
   `[AutoResponder] Réponse trouvée ! Envoi automatique programmé.`
3. Après un délai aléatoire tiré entre `mindelay` et `maxdelay` (ms), envoie la commande de validation configurée, par ex. `/cg valid Rebirth | 1532`.
4. Aucune réponse publique instantanée n'est envoyée pour ces messages — si aucune entrée ne correspond, le mod ne fait rien (à toi de répondre).

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
- `mindelay` / `maxdelay` : bornes en millisecondes du délai aléatoire avant envoi.
- `commandTemplate` : gabarit de la commande finale. Placeholders `{response}` et `{timer}` (délai réellement tiré, en ms). Change-le en `/chatgame valid {response} | {timer}` si besoin.

## Autres solveurs (messages sans préfixe "Chat Games »")

| Type de message | Réponse |
|---|---|
| `First one to say X` | renvoie `X` |
| `Unscramble abc` | trouve le mot (dictionnaire) |
| `What is 9 x 6?` | calcule → `54` |
| `Answer the following question: ...` | banque de réponses |
| entrées de `responders.json` | réponse fixe configurée |

Réponse envoyée dans le **chat public**, après un délai aléatoire de **0.8 à 1.4 s**. Ce chemin ne s'applique qu'aux messages qui ne commencent **pas** par `Chat Games »`.

## Commandes (`/cg` ou `/chatgame`)

| Commande | Effet |
|---|---|
| `/cg` ou `/cg toggle` | on / off |
| `/cg on` · `/cg off` | activer / désactiver |
| `/cg reload` | recharger la config |
| `/cg add <scrambled> <mot>` | apprendre un unscramble |
| `/cg addquestion <question> \| <réponse>` | apprendre une question |

Ce qui est appris est gardé et prioritaire. `/chatgame` fonctionne comme alias identique à `/cg`.

## Config

Éditable dans `.minecraft/config/autoresponder/` :

- `autoresponder_questions.json` — flux de validation "Chat Games »" (trigger/response/mindelay/maxdelay + commandTemplate)
- `words.txt` — dictionnaire unscramble (1 mot/ligne)
- `questions.json` — questions → réponses (banque `Answer the following question:`)
- `responders.json` — réponses fixes (fallback, hors flux "Chat Games »")
- `learned.json` / `learned_questions.json` — appris via commandes

## Compiler

```
gradlew.bat build
```
Le jar sort dans `build/libs/`.

---
⚠️ Automatiser les chat games peut enfreindre le règlement du serveur.
