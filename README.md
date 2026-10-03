# VeinSim

VeinSim lets you see ores through walls. On servers without anti-xray it simply shows you the real ores around you. On servers that hide or fake them, it works out where they are from the world seed, the same way the game decided where to put them in the first place.

## VeinSim 3.0.0 is here!

I'm honestly so excited to finally put this one out. 3.0.0 is the biggest update VeinSim has ever had. It started as "what if I could predict ores from the seed" and turned into a whole toolkit for finding pretty much anything in your world. I spent a long time testing every feature against real worlds and real servers to make sure it actually gets things right, and I'm really proud of how it turned out. I hope you enjoy using it as much as I enjoyed making it.

---

## What it does

### Ores
- Press **X** and every ore you've switched on gets a coloured outline you can see through walls. Press **X** again to hide them (or switch to Hold mode).
- **Checks the server for you.** When you join, VeinSim figures out whether the server uses anti-xray and what kind:
  - **No anti-xray:** it shows the real ores right away. No seed needed.
  - **Hides ores:** it predicts them from the seed.
  - **Fake ores:** it ignores the fake ones the server sends you and predicts the real ones from the seed.
- **Every ore:** coal, iron, copper, gold, lapis, redstone, diamond and emerald (normal and deepslate), the big raw iron and raw copper veins, ancient debris, nether quartz, nether gold and glowstone.
- **Nearest vein only** shows just the closest vein of each ore, whole, so you can head straight for it.
- **Rare ores beyond your range:** if there's no emerald or ancient debris nearby, VeinSim looks further out, tells you where the nearest one is and outlines it. Mine it and it finds the next one.
- Deepslate variants get their own slightly darker colours so you can tell them apart.

### Finding the seed
You only need the seed on servers that hide ores (and for the Find menus). In singleplayer it's known instantly. On a server you can:
- Press **Ask server**, which works on servers that allow /seed.
- Use the **Seed Cracker**: walk around the Overworld and the Nether for a bit and a checklist pinned to your chat ticks itself off as it reads the bedrock. When it's done, it tells you the seed.
- It can also work the seed out from structures you walk past.

### Find menu
- **Structures:** the nearest village, temple, stronghold, trial chamber, bastion, end city and more, with a button to point your compass at it.
- **Biomes:** the nearest of any biome.
- **Blocks:**
  - **Highlighter:** outline any block in the game in any colour you like. The **Containers** button lights up every chest and shulker box around you.
  - **Scanner:** finds blocks that only structures place, like spawners, end portal frames, vaults and trial spawners. It gives you the exact block, not just "somewhere in that structure".
- **Items:** pick any item that can show up in structure loot and VeinSim tells you which chests near you actually have it, and how many. It works out each chest's loot from the seed, the same way the game rolls it.
  - Searches out to about 10,000 blocks, nearest first, and you can sort by nearest, furthest, most or least.
  - Switch between Overworld, Nether and End.
  - **Auto-scan:** once the seed is known, it quietly looks for valuable loot near you (enchanted golden apples, diamonds, netherite, upgrade templates and more), so the results are already waiting when you open the menu.
  - Click any result to see everything in that chest, or send it to chat or your compass.

### Seeing better
- **Fullbright** and **No Fog**, either always on or only while VeinSim is on. Great for caves and for spotting ores underwater.

### A cleaner menu
- Press **V** to open it. Everything's laid out on one screen, with a **Guide** button if you're new and a **Reset settings** button if you want to start fresh.

---

## How accurate is it?
I tested everything against real generated worlds, chest by chest and block by block:
- Diamonds, iron, copper, gold, lapis, redstone and emerald: **100%** of the real ores found in singleplayer tests. Coal is the trickiest one at around 90%.
- Glowstone: 54 out of 54.
- Chest loot: 116 out of 116 chests matched exactly across 15 different structures.
- On a server using fake-ore anti-xray, deepslate diamonds came out the same as on a server with no anti-xray at all.

A couple of honest limits:
- Predictions are only right for parts of the world that haven't been changed. If someone already mined it out or opened the chest, it won't be there.
- Vault rewards (like the heavy core) can't be predicted. The game rolls them at the moment you use the key.
- Strongholds aren't included in the loot finder, because they don't predict reliably enough.

---

## Controls
- **X**: show or hide ores (you can change this in the menu)
- **V**: open the menu
- **/VeinSim guide**: show the seed checklist
- **/VeinSim seed [number]**: set the seed yourself
- **/VeinSim forget**: forget the seed for this server

## Versions
- **3.0.0:** Minecraft 26.3, 26.2 and 26.1.x
- **2.0.0:** Minecraft 1.21.11. Ore prediction, structures, biomes and seed cracking. The newer features (Items, Blocks, anti-xray detection) are 26.1 and up.

## Requirements
- Fabric Loader and Fabric API
- Java 25 (Java 21 for 1.21.11)
- Client-side only. You don't install it on the server.

## Please play fair
A lot of servers don't allow x-ray tools. Check the rules before you use VeinSim somewhere, because getting banned is no fun. It's perfect for singleplayer, your own servers, or anywhere it's allowed.

---

If you find a bug or have an idea, leave a comment. I read them all, and a lot of what's in 3.0.0 came from testing and feedback. Thanks for checking out VeinSim, and happy mining!
