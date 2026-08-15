# CampusLobby

Procedurally decorates a FoliaNexa lobby world into an NC State
Wolfpack-themed scene: a central brick "Brickyard" plaza, a Memorial
Belltower centerpiece, a blocky wolf-mascot statue, a
Free-Expression-Tunnel-style mural walkway, and stylized student-union /
library building facades — all in Wolfpack red, white, and black.

This is an original, from-scratch plugin — not a port or reimplementation
of any existing mod/plugin. It's a stylized, iconic representation of a
few recognizable NC State campus landmarks (silhouette and color scheme,
not literal architecture): every shape is generated from plain arithmetic
in `CampusScene`, with no real building schematics, LIDAR/GIS data, or
other external geometry assets involved. It is not affiliated with,
endorsed by, or an accurate reproduction of North Carolina State
University's actual campus.

The lobby is a jumping-off point to other worlds in the cluster — see
[`docs/plugin-dev`](https://github.com/kenvandine/FoliaNexa/tree/main/docs/plugin-dev)
and PLAN.md §14B in the [`FoliaNexa`](https://github.com/kenvandine/FoliaNexa)
repo. CampusLobby only builds the physical scene; it doesn't handle
world-switching itself. Pair the signs it places near each landmark with
the catalog's `ServerSelector` plugin (its own config controls the actual
click-to-teleport NPCs/portals) if you want them to sit side by side.

This plugin lives in `folianexa-plugins` (this repo) rather than in the
`FoliaNexa` monorepo itself — the plugin catalog
(`mgmt/src/folia_mgmt/catalog.yaml` in `FoliaNexa`) only ever needs a
`download_url`, wherever the jar is actually hosted; see this repo's
top-level README.

## Building

Requires Java 21.

```bash
./gradlew build
# build/libs/campus-lobby-0.1.0.jar
```

## Installing (for local testing)

Drop the built jar into a Folia/Paper 1.21.4 server's `plugins/`
directory and (re)start the server. See
[the `FoliaNexa` plugin-dev environment setup guide](https://github.com/kenvandine/FoliaNexa/blob/main/docs/plugin-dev/01-environment-setup.md)
for running a local test server.

## Usage

- `/campuslobby build` — generates the scene centered on the sender's
  current location (or the world's spawn, from console). Placement is
  chunk-by-chunk via Paper's region scheduler, so it can take a few
  seconds to finish on a big plaza.
- `/campuslobby reload` — reloads `config.yml` without a restart. Run
  `build` again afterward to apply changed scene settings; reload alone
  does not retroactively edit already-placed blocks.

## Configuration

`config.yml` is generated on first run. It controls the plaza size,
tower height, which landmarks are included, the block palette (default:
concrete/brick in Wolfpack red/white/black), and the text shown on each
landmark's sign.

## License

MIT
