# VeinSim

**VeinSim** predicts the **exact locations of ores from your world's seed** and draws them as colored, see-through wireframe boxes — so you can find real veins even on servers running anti-xray.

Instead of reading hidden block data (like an X-ray mod), VeinSim **re-simulates Minecraft's ore generation from the seed**.

---

##  Features

-  **Seed-based prediction** — calculates ore positions from the world seed by replicating vanilla world generation. Not block-reading; anti-xray proof.
-  **See-through wireframe boxes** — every predicted ore is outlined with a bright, color-coded box visible through terrain.
-  **All ores supported** — overworld, **deepslate variants (toggle separately)**, and **Nether** ores.
-  **In-game config menu** — toggle ores, set your activation key, and tune everything without leaving the game.
-  **Hold or Toggle activation** — hold the key while you look, or press once to toggle on/off.
-  **Adjustable search range** — scan from 1 up to 16 chunks around you.
-  **Result limit** — cap how many boxes show at once (nearest first) to keep performance smooth.
-  **Nearest-vein-only mode** — show just the single closest vein of each selected ore for clean beelining.
-  **Chat status messages** — get an Enabled/Disabled notice when you activate it (can be turned off).
-  **Persistent settings** — everything saves to `config/vein-sim.json`.

---

##  Quick Start

1. Install Fabric Loader + Fabric API, then drop VeinSim in your `mods` folder.
2. Join your world/server and **set the seed**:
   /VeinSim seed 123456789
3. Press **V** to open the config menu and choose which ores to find.
4. Hold (or toggle) **X** to display the ore boxes. Go mine.

> You need to know the world's seed for predictions to work. On singleplayer it's your world seed; on servers, whatever seed that world was generated with. Found through a Seed Cracker.

---

##  Hotkeys & Commands

| Input | Action |
|-------|--------|
| **V** | Open the VeinSim config menu *(rebindable in Options → Controls)* |
| **X** | Activate the ore overlay *(default; change it in the config menu)* |
| `/VeinSim seed <number>` | Set the world seed used for predictions |

Activation can be set to **Hold** (active only while held) or **Toggle** (press to switch on/off) in the config menu.

---

##  Config Menu

Open with **V**. Options:

- **Activation Key** — rebind the overlay key to anything you like.
- **Mode** — `TOGGLE` or `HOLD`.
- **Search Range** — 1–16 chunks. Larger ranges fill in over a second or two.
- **Max Boxes** — cap the number of boxes rendered (nearest to you first).
- **Nearest vein only** — show only the closest vein of each enabled ore.
- **Chat status** — turn the Enabled/Disabled chat messages on or off.
- **Ore toggles** — enable/disable each ore individually:
  - **Overworld:** Coal, Iron, Copper, Gold, Lapis, Redstone, Diamond, Emerald
  - **Deepslate:** every deepslate variant, toggled separately
  - **Nether:** Ancient Debris, Nether Quartz, Nether Gold

Heavy settings (large range or high box cap) show an in-menu warning so you know what may cause lag.

---

##  Accuracy

VeinSim reproduces Minecraft's generation algorithm block-for-block. Ores fall into two groups:

- **Exact** — iron, copper, redstone, lapis, gold, emerald, coal (upper), nether quartz, nether gold, ancient debris, and buried diamond/lapis. These are fully deterministic and predicted precisely.
- **Approximate** — coal (lower) and regular diamond. Their generation depends on nearby cave air, which **cannot be derived from the seed alone** by any tool. The closest veins still show, but expect some extras.

> Tip: enable **Nearest vein only** and a small **Search Range** for the cleanest results while mining.

---

##  Requirements

- **Fabric Loader**
- **Fabric API**
- Client-side only (does not need to be on the server).

**Supported versions:** download the file that matches your Minecraft version:

| Minecraft | File |
|-----------|------|
| 26.2 | VeinSim for 26.2 |
| 26.1 / 26.1.1 / 26.1.2 | VeinSim for 26.1.x |
| 1.21.11 | VeinSim for 1.21.11 |

---

If you run into any issues or want to make a suggestion: [Fill Out This Form](https://docs.google.com/forms/d/e/1FAIpQLSejXIOFw99BLZ-qlh2arSHzjZpFGK1kiVnIAiajQ_Yj2S5BwA/viewform?usp=publish-editor)
