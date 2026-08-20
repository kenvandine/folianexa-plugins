# Phqen1x RPG Suite — project plan

Design documentation for two new Folia plugins that put a local LLM at the
centre of world-building and gameplay on a [FoliaNexa](https://github.com/kenvandine/FoliaNexa)
server:

- **Phqen1xWorldEditCraft** — an operator types natural language, a
  [Lemonade Server](https://lemonade-server.ai/) on the LAN turns it into a
  structure, and the plugin writes a real WorldEdit-compatible `.schem` file,
  files it in a permanent library, and pastes it into the world. Useful on its
  own to anyone with operator status.
- **Phqen1xRPG** — generates a complete, unique role-playing campaign (setting,
  factions, quest chain, NPCs, classes, bestiary, loot, bosses) from the same
  Lemonade server, and calls WorldEditCraft in-process to build every village,
  dungeon, shrine and boss lair that campaign needs. Every generated campaign
  is saved as a portable pack that replays with no LLM in the loop.

**Nothing here is implemented yet.** This directory is the plan. The plugins
will be built by [Phqen1x](https://github.com/Phqen1x), most likely in his own
repositories under the `io.github.phqen1x.*` namespace; these documents exist so
that work can start from a settled architecture instead of a blank page.

## Read in this order

| Doc | What it covers |
| --- | --- |
| [`00-project-plan.md`](00-project-plan.md) | The vision, the end-goal gameplay loop, how the two plugins interlock, and the top-level risks. **Start here.** |
| [`01-worldeditcraft-design.md`](01-worldeditcraft-design.md) | Full technical design of Phqen1xWorldEditCraft — pipeline, classes, `.schem` writer, paste engine, commands, config. |
| [`02-rpg-design.md`](02-rpg-design.md) | Full technical design of Phqen1xRPG — the nine-stage campaign generator, the fixed mechanical vocabulary, runtime systems. |
| [`03-buildscript-dsl.md`](03-buildscript-dsl.md) | **Normative spec.** The JSON build-script language the LLM emits and the plugin rasterizes. The contract everything else hangs off. |
| [`04-lemonade-integration.md`](04-lemonade-integration.md) | The inference client: endpoints, prompting, JSON coercion and repair, the shared request queue, operational tuning. |
| [`05-shared-api.md`](05-shared-api.md) | The `phqen1x-worldeditcraft-api` artifact both plugins compile against, and the `ServicesManager` handshake. |
| [`06-campaign-pack-format.md`](06-campaign-pack-format.md) | Saving, loading, replaying and sharing a generated campaign — and how offline play works. |
| [`07-folia-safety.md`](07-folia-safety.md) | The threading rules both plugins must obey, with the precedents in this repo they are drawn from. |
| [`08-roadmap.md`](08-roadmap.md) | Milestones, exit criteria, and the test/QA plan. |

## Decisions already settled

These were chosen deliberately and the rest of the design depends on them. If
you want to revisit one, read the section that argues for it first.

| Decision | Choice | Argued in |
| --- | --- | --- |
| WorldEdit dependency | Self-contained. Read/write standard Sponge v3 `.schem`, paste with our own Folia-safe engine. No runtime dependency on WorldEdit or FAWE. | [`01`](01-worldeditcraft-design.md#why-not-just-depend-on-worldedit) |
| How the LLM expresses geometry | A compact JSON build-script DSL, rasterized deterministically. Never per-block lists. | [`03`](03-buildscript-dsl.md#why-a-dsl) |
| What the LLM is allowed to author | Names, prose, relationships, composition. Never behaviour — every mechanic is chosen from a fixed vocabulary the plugin already implements. | [`02`](02-rpg-design.md#the-central-principle) |
| Replay | Every generated campaign is saved as a portable pack. Lemonade is an authoring-time dependency, not a runtime one. | [`06`](06-campaign-pack-format.md) |
| Inter-plugin coupling | Bukkit `ServicesManager` plus a tiny dependency-free `-api` artifact. No reflection. | [`05`](05-shared-api.md) |
| Namespace | `io.github.phqen1x.worldeditcraft` / `io.github.phqen1x.rpg` | [`00`](00-project-plan.md#repository-and-namespace) |

## What's real vs. unverified

Nothing in this directory has been built or run. It is a design, written
against a real reading of this repository's three existing plugins and against
the published Lemonade Server and Sponge Schematic specifications.

What is grounded in something checked:

- Every claim about this repo's conventions (Java 21, `paper-api
  1.21.4-R0.1-SNAPSHOT`, `com.gradleup.shadow` 8.3.5, the scheduler idioms, the
  no-third-party-dependencies stance) was read out of `campus-lobby/`,
  `folianexa-stats/` and `hungergames/` and is cited by file and line where it
  matters.
- Lemonade Server's endpoints, its default port of 13305, and its parameter
  support were taken from its
  [OpenAI-compatible API documentation](https://lemonade-server.ai/docs/api/openai/).
- The `.schem` structure was taken from the
  [Sponge Schematic v3 specification](https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-3.md).

What is extrapolated and must be measured before it is believed: paste
throughput on Folia, how reliably a locally-served model produces valid build
scripts, generation latency, and whether the `.schem` files this design
produces actually load in WorldEdit. No Lemonade server, WorldEdit instance, or
Folia test server was available while writing this. Each document repeats this
warning in its own terms at the bottom.

## License

MIT
