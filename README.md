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
| `Solstice/` | Folia-native seasons, calendar, and temperature plugin — biome recoloring, ambient visuals, seasonal events | `Solstice` |

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

### JDK version

`.github/workflows/release.yml` builds every plugin under JDK 21 by
default. A plugin that needs a newer JDK to actually *run* Gradle (e.g.
`Solstice/`, whose `dev.folia:folia-api` dependency ships class files
only a JDK 25+ javac can read off the classpath, even though Solstice's
own compiled output still targets release 21) can opt in by committing a
`.java-version` file in its own directory containing just the major
version, e.g. `25`. Leave it unset unless you hit the same kind of
classfile-version mismatch — most plugins should never need this.
