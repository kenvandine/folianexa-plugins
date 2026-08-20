# folianexa-plugins

In-house Paper/Folia plugins for the [FoliaNexa](https://github.com/kenvandine/FoliaNexa)
cluster, each in its own top-level directory with its own Gradle build.
This repo exists purely as a home for plugin source — FoliaNexa's plugin
catalog (`mgmt/src/folia_mgmt/catalog.yaml` in the `FoliaNexa` repo) only
ever needs a `download_url` pointing at a built jar (typically a GitHub
Releases asset from here), never the source itself.

See `docs/plugin-dev/` in the `FoliaNexa` repo for the full plugin
development guide (environment setup, Folia-safe architecture, and how
to submit a plugin here for catalog review).

## Plugins

| Directory | What | catalog.yaml id |
| --- | --- | --- |
| `campus-lobby/` | Procedurally builds an NC State Wolfpack-themed lobby scene (Belltower, Brickyard plaza, wolf statue, more) | `CampusLobby` |
| `folianexa-stats/` | Reports per-player kills/deaths/blocks-mined/playtime to mgmt's public player hub (PLAN.md §7A); softdepends on AuraSkills and Vault | `FoliaNexaStats` |
| `hungergames/` | Configurable battle-royale minigame — queue-based arenas, shrinking world border, config-only maps and randomized "twists" | `HungerGames` |

## Design docs

| Directory | What |
| --- | --- |
| [`docs/phqen1x-rpg-suite/`](docs/phqen1x-rpg-suite/) | Project plan for **Phqen1xWorldEditCraft** (natural-language schematic generation via a local Lemonade Server) and **Phqen1xRPG** (LLM-authored, replayable RPG campaigns built on top of it). Design only — to be implemented by [Phqen1x](https://github.com/Phqen1x), likely in his own repos. |

## Adding a new plugin

Each plugin is a standalone Gradle project in its own top-level
directory (see `campus-lobby/` for the shape: `build.gradle.kts`,
`settings.gradle.kts`, its own `gradlew`, `src/main`, `src/test`). Cut a
release (tag + build + attach the jar to a GitHub Release) once it's
ready, then open a PR against `FoliaNexa` adding/updating its
`catalog.yaml` entry with the real `download_url` and `sha256`.

### Versioning convention

Every plugin's `build.gradle.kts` should read its version like this
(see `campus-lobby/build.gradle.kts` or `folianexa-stats/build.gradle.kts`
for the working copy):

```kotlin
version = (findProperty("releaseVersion") as String?) ?: "0.1.0"

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version.toString())
    }
}
```

with `plugin.yml`'s own `version:` field set to the literal placeholder
`'${version}'` rather than a hardcoded string. `.github/workflows/release.yml`
passes `-PreleaseVersion=<the tag's version>` to every release build, so
the resulting jar's filename and its `plugin.yml` both self-report the
exact version they were released under — no hand-editing a version
number before tagging, and no risk of it drifting out of sync (which is
exactly what happened before this convention existed: campus-lobby
shipped three releases, `v0.0.1`–`v0.0.3`, all internally reporting
`0.1.0`). Plain local `./gradlew build` with no property set still falls
back to the hardcoded default, so day-to-day local builds are unaffected.
