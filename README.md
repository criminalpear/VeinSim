# VeinSim

**VeinSim** predicts **ores, structures and biomes from your world's seed** and draws them as colored, see-through wireframe boxes — so you can find real veins and real structures even on servers running anti-xray.

Instead of reading hidden block data (like an X-ray mod), VeinSim **re-simulates Minecraft's world generation from the seed**.

And if you don't know the seed, **it can work it out for you** — just by walking past structures.

***

## Features

### Seed cracking

*   **Automatic** — recognises structures as they come into view and cracks the world seed on its own. No commands, no setup; just explore.
*   **Works from landmarks** — shipwrecks, desert pyramids, igloos, jungle temples, swamp huts, ocean ruins, and pillager outposts.
*   **Per-server memory** — a cracked seed is saved against that server and reused next time you join.

### Ore prediction

*   **Seed-based** — calculates ore positions by replicating vanilla world generation. Not block-reading; anti-xray proof.
*   **See-through wireframe boxes** — every predicted ore outlined in a bright, color-coded box visible through terrain.
*   **All ores** — overworld, **deepslate variants (toggled separately)**, and **Nether** ores.
*   **Adjustable search range** — 1 to 16 chunks around you.
*   **Result limit** — cap how many boxes show at once (nearest first) to keep performance smooth.
*   **Nearest-vein-only mode** — show just the closest vein of each selected ore for quick runs.

### Structures

*   **22 structure types** located from the seed — villages, outposts, pyramids, temples, igloos, huts, mansions, trail ruins, monuments, ocean ruins, shipwrecks, buried treasure, ruined portals, strongholds, mineshafts, ancient cities, trial chambers, fortresses, bastions, nether fossils and end cities.
*   **See-through boxes** drawn around located structures.
*   **Search radius** up to 256 chunks.

### Biomes

*   **Find any biome** from the seed, out to 25,600 blocks.

### Compass binding

*   **Bind your compass** to a structure or biome and it points there — including a distinct model when you arrive.

### Quality of life

*   **In-game config menu** — toggle everything without leaving the game.
*   **Hold or Toggle activation** for the ore overlay.
*   **Chat status messages** — Enabled/Disabled notices (can be turned off).
*   **Persistent settings** — everything saves to `config/vein-sim.json`.

***

## Quick Start

1.  Install Fabric Loader + Fabric API, then drop VeinSim in your `mods` folder.
2.  Join your world or server.
3.  **If you know the seed:** `/VeinSim seed 123456789` **If you don't:** just play. Walk past a few structures and VeinSim cracks it for you, then tells you in chat.
4.  Press **V** to open the config menu and choose what to find.
5.  Hold (or toggle) **X** to display the ore boxes. Go mine.

***

## Hotkeys & Commands

| Input                  |Action                                                    |
| ---------------------- |--------------------------------------------------------- |
| <strong>V</strong>     |Open the VeinSim menu <em>(rebindable in Options → Controls)</em> |
| <strong>X</strong>     |Activate the ore overlay <em>(default; change it in the menu)</em> |
| <code>/VeinSim seed &lt;number&gt;</code> |Set the world seed manually                               |
| <code>/VeinSim inspect</code> |Explain what the detector sees where you're standing      |

Activation can be set to **Hold** (active only while held) or **Toggle** in the config menu.

***

## Config Menu

Open with **V**.

*   **Activation Key** — rebind the overlay key.
*   **Mode** — `TOGGLE` or `HOLD`.
*   **Search Range** — 1–16 chunks. Larger ranges fill in over a second or two.
*   **Max Boxes** — cap the boxes rendered (nearest first).
*   **Nearest vein only** — only the closest vein of each enabled ore.
*   **Chat status** — Enabled/Disabled messages on or off.
*   **Auto-crack** — let VeinSim find the seed on its own (on by default).
*   **Structure boxes** — draw boxes around located structures.
*   **Structure / biome search radius** — up to 256 chunks and 25,600 blocks.
*   **Ore toggles** — each ore individually:
    *   **Overworld:** Coal, Iron, Copper, Gold, Lapis, Redstone, Diamond, Emerald
    *   **Deepslate:** every deepslate variant, toggled separately
    *   **Nether:** Ancient Debris, Nether Quartz, Nether Gold

Heavy settings show an in-menu warning so you know what may cause lag.

***

## Accuracy

### Ores

VeinSim reproduces Minecraft's generation algorithm block-for-block. Ores fall into two groups:

*   **Exact** — iron, copper, redstone, lapis, gold, emerald, coal (upper), nether quartz, nether gold, ancient debris, and buried diamond/lapis. Fully deterministic and predicted precisely.
*   **Approximate** — coal (lower) and regular diamond. Their generation depends on nearby cave air, which **cannot be derived from the seed alone** by any tool. The closest veins still show, but expect some extras.

> Tip: enable **Nearest vein only** with a small **Search Range** for the cleanest results while mining.

### Structure recognition

Measured over 50 real structures: **46 found**. Desert pyramids, igloos, jungle temples, shipwrecks, swamp huts and pillager outposts were recognised every single time.

**Ocean ruins are the exception**, and it's worth knowing why. Minecraft deliberately erodes them as it builds — often deleting around half the blocks — so a badly rotted ruin no longer resembles its own template closely enough to identify with confidence. Intact ruins are found; heavily eroded ones are not. This is the game's own randomness, not a bug, and no seed-based tool can recover what was never placed.

Missing one structure costs you nothing: cracking simply uses the next one you walk past.

***

## Requirements

*   **Fabric Loader**
*   **Fabric API**
*   **Java 25**
*   Client-side only — does not need to be on the server.

**Supported version:**

| Minecraft |File                   |
| --------- |---------------------- |
| 26.2      |VeinSim 1.0.0 for 26.2 |

Builds for 26.1.x and 1.21.11 are not yet available for this release.

***

## Notes

*   Seed cracking needs structures, so it works best while exploring new terrain.
*   Everything is client-side: nothing is sent to the server, and no server-side permissions are required.
