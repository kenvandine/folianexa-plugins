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

## Adding a new plugin

Each plugin is a standalone Gradle project in its own top-level
directory (see `campus-lobby/` for the shape: `build.gradle.kts`,
`settings.gradle.kts`, its own `gradlew`, `src/main`, `src/test`). Cut a
release (tag + build + attach the jar to a GitHub Release) once it's
ready, then open a PR against `FoliaNexa` adding/updating its
`catalog.yaml` entry with the real `download_url` and `sha256`.
