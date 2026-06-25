package name.modid.client.ore;

import name.modid.client.VeinSimClient;
import name.modid.client.config.ConfigManager;
import name.modid.client.config.VeinSimConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;


import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class OrePredictor {

    public record PredictedOre(int x, int y, int z, OreType type, BiomeReq biomeReq,
                               int ox, int oy, int oz, int featureIndex) {}

    // ─── Ore sub-feature definitions (exact MC 26.2 vanilla data) ────────────

    enum HeightDist { UNIFORM, TRAPEZOID }

    /** Which biomes a feature actually generates in (filters out-of-biome false positives). */
    public enum BiomeReq { ANY, MOUNTAIN, DRIPSTONE, BADLANDS, NETHER_NON_DELTAS, BASALT_DELTAS }

    /** Dimension the feature belongs to (we only simulate features matching the current level). */
    public enum Dim { OVERWORLD, NETHER }

    /** OreFeature (blob) vs ScatteredOreFeature (ancient debris — scattered points). */
    enum FeatureKind { ORE, SCATTERED }

    /** Which blocks a feature can replace (drives the block-aware target.test). */
    enum TargetSet { OVERWORLD, NETHERRACK, NETHER_BASE }

    /**
     * One placed ore feature, using exact MC 26.2 vanilla data extracted from the game jar.
     * {@code decorationIndex} is the index into the GLOBAL sorted feature list for {@code step}
     * (overworld ores = step 6, nether ores = step 7). Verify in-game with {@code /VeinSim debug}.
     */
    record OreSubFeature(
            OreType type,
            int decorationIndex,
            int countMin, int countMax,   // min==max → constant count (no random call)
            int rarityChance,             // >0 → rarity_filter(1/chance), count ignored
            HeightDist heightDist,
            int minY, int maxY,           // resolved world Y coords
            int size,                     // vein size (from configured_feature)
            float discardChance,          // discardChanceOnAirExposure
            BiomeReq biomeReq,            // which biomes this feature actually generates in
            int step,                     // GenerationStep.Decoration ordinal (6 ow, 7 nether)
            Dim dim,                      // dimension this feature belongs to
            FeatureKind kind,            // blob ore vs scattered ore
            TargetSet target             // which blocks it replaces
    ) {}

    // ── factories to keep the table readable ──
    private static OreSubFeature ow(OreType t, int idx, int cMin, int cMax, int rar,
            HeightDist hd, int minY, int maxY, int size, float disc, BiomeReq br) {
        return new OreSubFeature(t, idx, cMin, cMax, rar, hd, minY, maxY, size, disc, br,
                6, Dim.OVERWORLD, FeatureKind.ORE, TargetSet.OVERWORLD);
    }
    private static OreSubFeature nOre(OreType t, int idx, int cMin, int cMax,
            HeightDist hd, int minY, int maxY, int size, float disc, BiomeReq br) {
        return new OreSubFeature(t, idx, cMin, cMax, 0, hd, minY, maxY, size, disc, br,
                7, Dim.NETHER, FeatureKind.ORE, TargetSet.NETHERRACK);
    }
    private static OreSubFeature nScatter(OreType t, int idx,
            HeightDist hd, int minY, int maxY, int size, float disc) {
        return new OreSubFeature(t, idx, 1, 1, 0, hd, minY, maxY, size, disc, BiomeReq.ANY,
                7, Dim.NETHER, FeatureKind.SCATTERED, TargetSet.NETHER_BASE);
    }

    // OVERWORLD: step 6. above_bottom(n)=-64+n, below_top(n)=320-n, absolute(n)=n.
    // NETHER:    step 7. above_bottom(n)=n,     below_top(n)=127-n.
    private static final List<OreSubFeature> FEATURES = List.of(
        // ── Overworld (step 6) ──
        ow(OreType.COAL,      9, 30, 30, 0, HeightDist.UNIFORM,    136, 319, 17, 0.0f, BiomeReq.ANY),
        ow(OreType.COAL,     10, 20, 20, 0, HeightDist.TRAPEZOID,    0, 192, 17, 0.5f, BiomeReq.ANY),
        ow(OreType.IRON,     11, 90, 90, 0, HeightDist.TRAPEZOID,   80, 384,  9, 0.0f, BiomeReq.ANY),
        ow(OreType.IRON,     12, 10, 10, 0, HeightDist.TRAPEZOID,  -24,  56,  9, 0.0f, BiomeReq.ANY),
        ow(OreType.IRON,     13, 10, 10, 0, HeightDist.UNIFORM,    -64,  72,  4, 0.0f, BiomeReq.ANY),
        ow(OreType.GOLD,     14,  4,  4, 0, HeightDist.TRAPEZOID,  -64,  32,  9, 0.5f, BiomeReq.ANY),
        ow(OreType.GOLD,     15,  0,  1, 0, HeightDist.UNIFORM,    -64, -48,  9, 0.5f, BiomeReq.ANY),
        ow(OreType.GOLD,     28, 50, 50, 0, HeightDist.UNIFORM,     32, 256,  9, 0.0f, BiomeReq.BADLANDS),
        ow(OreType.REDSTONE, 16,  4,  4, 0, HeightDist.UNIFORM,    -64,  15,  8, 0.0f, BiomeReq.ANY),
        ow(OreType.REDSTONE, 17,  8,  8, 0, HeightDist.TRAPEZOID,  -96, -32,  8, 0.0f, BiomeReq.ANY),
        ow(OreType.DIAMOND,  18,  7,  7, 0, HeightDist.TRAPEZOID, -144,  16,  4, 0.5f, BiomeReq.ANY),
        ow(OreType.DIAMOND,  19,  2,  2, 0, HeightDist.UNIFORM,   -64,  -4,  8, 0.5f, BiomeReq.ANY),
        ow(OreType.DIAMOND,  20,  1,  1, 9, HeightDist.TRAPEZOID, -144,  16, 12, 0.7f, BiomeReq.ANY),
        ow(OreType.DIAMOND,  21,  4,  4, 0, HeightDist.TRAPEZOID, -144,  16,  8, 1.0f, BiomeReq.ANY),
        ow(OreType.LAPIS,    22,  2,  2, 0, HeightDist.TRAPEZOID,  -32,  32,  7, 0.0f, BiomeReq.ANY),
        ow(OreType.LAPIS,    23,  4,  4, 0, HeightDist.UNIFORM,    -64,  64,  7, 1.0f, BiomeReq.ANY),
        ow(OreType.COPPER,   24, 16, 16, 0, HeightDist.TRAPEZOID,  -16, 112, 20, 0.0f, BiomeReq.DRIPSTONE),
        ow(OreType.COPPER,   25, 16, 16, 0, HeightDist.TRAPEZOID,  -16, 112, 10, 0.0f, BiomeReq.ANY),
        ow(OreType.EMERALD,  33, 100, 100, 0, HeightDist.TRAPEZOID, -16, 480, 3, 0.0f, BiomeReq.MOUNTAIN),

        // ── Nether (step 7). Heights resolved for nether (minY=0, top=127). ──
        // nether_gold (idx19) & nether_quartz (idx20): all nether EXCEPT basalt_deltas.
        nOre(OreType.NETHER_GOLD,    19, 10, 10, HeightDist.UNIFORM, 10, 117, 10, 0.0f, BiomeReq.NETHER_NON_DELTAS),
        nOre(OreType.NETHER_QUARTZ,  20, 16, 16, HeightDist.UNIFORM, 10, 117, 14, 0.0f, BiomeReq.NETHER_NON_DELTAS),
        // basalt_deltas variants: gold_deltas (idx13, count20), quartz_deltas (idx14, count32).
        nOre(OreType.NETHER_GOLD,    13, 20, 20, HeightDist.UNIFORM, 10, 117, 10, 0.0f, BiomeReq.BASALT_DELTAS),
        nOre(OreType.NETHER_QUARTZ,  14, 32, 32, HeightDist.UNIFORM, 10, 117, 14, 0.0f, BiomeReq.BASALT_DELTAS),
        // ancient debris (scattered_ore, discard 1.0 = must be fully buried) — all nether biomes.
        // large: idx21 trapezoid(8,24) size3 ;  small: idx22 uniform(8,119) size2
        nScatter(OreType.ANCIENT_DEBRIS, 21, HeightDist.TRAPEZOID, 8,  24, 3, 1.0f),
        nScatter(OreType.ANCIENT_DEBRIS, 22, HeightDist.UNIFORM,   8, 119, 2, 1.0f)
    );

    // ─── Compute state (all on the game thread, throttled) ────────────────────

    /** Chunks simulated per client tick — keeps the block-aware sim from causing a hitch. */
    private static final int CHUNKS_PER_TICK = 4;

    /** Predictions read by the renderer (swapped in when a full pass finishes). */
    private static final AtomicReference<List<PredictedOre>> cache =
            new AtomicReference<>(Collections.emptyList());

    /** Work queue + accumulator for the in-progress pass. */
    private static final ArrayDeque<int[]> pendingChunks = new ArrayDeque<>();
    private static List<PredictedOre> accum = new ArrayList<>();
    private static HeightGrid activeGrid = null;
    private static long activeSeed = 0L;
    private static int activeRadius = -1;

    private static int lastCX = Integer.MIN_VALUE;
    private static int lastCZ = Integer.MIN_VALUE;

    public static List<PredictedOre> getPredictions() { return cache.get(); }

    public static void clearCache() {
        cache.set(Collections.emptyList());
        pendingChunks.clear();
        accum = new ArrayList<>();
        activeGrid = null;
        activeRadius = -1;
        lastCX = Integer.MIN_VALUE;
        lastCZ = Integer.MIN_VALUE;
    }

    // ─── Debug: simulate ONE chunk on demand (game thread) ───────────────────

    /**
     * Runs the full ore simulation for a single chunk with the given seed, ignoring the
     * per-ore config (returns ALL features). Samples terrain heights directly from the level.
     * Used by /VeinSim verify to compare predictions against real generated ores.
     */
    public static List<PredictedOre> debugPredictChunk(Level level, int cx, int cz, long worldSeed) {
        // Sample a height grid covering this chunk plus a 1-chunk margin (vein boxes overhang).
        int marginChunks = 1;
        int originX = (cx - marginChunks) * 16;
        int originZ = (cz - marginChunks) * 16;
        int gridDim = (2 * marginChunks + 1) * 16;
        int[] heights = new int[gridDim * gridDim];
        Arrays.fill(heights, Integer.MIN_VALUE);
        BlockPos.MutableBlockPos hm = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < gridDim; lx++) {
            for (int lz = 0; lz < gridDim; lz++) {
                int wx = originX + lx, wz = originZ + lz;
                // Bare-terrain height (matches MC's OCEAN_FLOOR_WG), same as the live path.
                heights[lx * gridDim + lz] = genSurfaceY(level, wx, wz, hm);
            }
        }
        HeightGrid grid = new HeightGrid(originX, originZ, gridDim, heights);

        List<PredictedOre> raw = new ArrayList<>();
        WorldgenRandom seeder = new WorldgenRandom(new XoroshiroRandomSource(worldSeed));
        long decorationSeed = seeder.setDecorationSeed(worldSeed, cx * 16, cz * 16);
        Dim dim = dimOf(level);

        for (OreSubFeature feat : FEATURES) {
            if (feat.dim() != dim) continue; // only features of the current dimension
            simulateFeature(level, cx, cz, decorationSeed, feat, grid, raw);
        }
        // Biome-gate at the vein ORIGIN (as MC does), THEN dedup — so biome-removed features
        // (e.g. copper_large outside dripstone) can't steal positions from kept ones.
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
        List<PredictedOre> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (PredictedOre o : raw) {
            if (o.biomeReq() != BiomeReq.ANY) {
                bp.set(o.ox(), o.oy(), o.oz());
                if (!biomeMatches(level, bp, o.biomeReq())) continue;
            }
            if (seen.add(packXYZ(o.x(), o.y(), o.z()))) out.add(o);
        }
        return out;
    }

    // ─── Tick (game thread) ───────────────────────────────────────────────────

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null || !VeinSimClient.overlayActive) return;

        int cx = player.blockPosition().getX() >> 4;
        int cz = player.blockPosition().getZ() >> 4;
        int radius = ConfigManager.getConfig().clampedRadius();

        // On chunk change / radius change / first run, sample the terrain-height grid and
        // (re)build the per-chunk work queue. The simulation is block-aware so it must run on the
        // game thread; we spread it over several ticks (CHUNKS_PER_TICK) to avoid a hitch.
        if (cx != lastCX || cz != lastCZ || activeGrid == null || radius != activeRadius) {
            lastCX = cx;
            lastCZ = cz;
            activeRadius = radius;
            activeSeed = ConfigManager.getConfig().worldSeed;
            activeGrid = sampleGrid(level, cx, cz, radius);
            accum = new ArrayList<>();
            pendingChunks.clear();
            for (int dx = -radius; dx <= radius; dx++)
                for (int dz = -radius; dz <= radius; dz++)
                    pendingChunks.add(new int[]{cx + dx, cz + dz});
        }

        // Process chunks this tick. Budget scales with radius so big scans still finish quickly
        // (far chunks are mostly unloaded → cheap), while small scans stay cheap per tick.
        if (pendingChunks.isEmpty()) return;
        VeinSimConfig cfg = ConfigManager.getConfig();
        int budget = Math.min(40, Math.max(CHUNKS_PER_TICK, radius * 3));
        while (budget-- > 0 && !pendingChunks.isEmpty()) {
            int[] c = pendingChunks.poll();
            simulateChunk(level, c[0], c[1], activeSeed, cfg, activeGrid, accum);
        }
        // When the full pass finishes, biome/terrain-filter it, cap to maxResults (nearest first),
        // and publish to the renderer.
        if (pendingChunks.isEmpty()) {
            List<PredictedOre> result = filterByTerrain(level, accum);
            final double px = player.getX(), py = player.getY(), pz = player.getZ();

            // "Nearest vein only": keep just the single closest vein of each ore type.
            if (cfg.nearestVeinOnly) {
                result = keepNearestVeinPerType(result, px, py, pz);
            }

            int max = Math.max(1, cfg.maxResults);
            if (result.size() > max) {
                result.sort(Comparator.comparingDouble(o -> dist2(o, px, py, pz)));
                result = new ArrayList<>(result.subList(0, max));
            }
            cache.set(result);
        }
    }

    private static double dist2(PredictedOre o, double px, double py, double pz) {
        double dx = o.x() + 0.5 - px, dy = o.y() + 0.5 - py, dz = o.z() + 0.5 - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Keeps, for each ore type, only the blocks of the single vein nearest the player. */
    private static List<PredictedOre> keepNearestVeinPerType(List<PredictedOre> in,
                                                             double px, double py, double pz) {
        // type -> (veinOriginKey -> blocks of that vein)
        Map<OreType, Map<Long, List<PredictedOre>>> groups = new EnumMap<>(OreType.class);
        for (PredictedOre o : in) {
            long originKey = packXYZ(o.ox(), o.oy(), o.oz());
            groups.computeIfAbsent(o.type(), k -> new HashMap<>())
                  .computeIfAbsent(originKey, k -> new ArrayList<>())
                  .add(o);
        }
        List<PredictedOre> out = new ArrayList<>();
        for (Map<Long, List<PredictedOre>> veins : groups.values()) {
            List<PredictedOre> best = null;
            double bestD = Double.MAX_VALUE;
            for (List<PredictedOre> vein : veins.values()) {
                double d = Double.MAX_VALUE;
                for (PredictedOre o : vein) d = Math.min(d, dist2(o, px, py, pz));
                if (d < bestD) { bestD = d; best = vein; }
            }
            if (best != null) out.addAll(best);
        }
        return out;
    }

    /** Samples the bare-terrain height grid for the render area (genSurfaceY per column). */
    private static HeightGrid sampleGrid(Level level, int cx, int cz, int r) {
        int originX = (cx - r) * 16;
        int originZ = (cz - r) * 16;
        int gridDim = (2 * r + 1) * 16;
        int[] heights = new int[gridDim * gridDim];
        Arrays.fill(heights, Integer.MIN_VALUE);
        BlockPos.MutableBlockPos hp = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < gridDim; lx++) {
            int wx = originX + lx;
            for (int lz = 0; lz < gridDim; lz++) {
                int wz = originZ + lz;
                hp.set(wx, 0, wz);
                if (level.isLoaded(hp)) {
                    heights[lx * gridDim + lz] = genSurfaceY(level, wx, wz, hp);
                }
            }
        }
        return new HeightGrid(originX, originZ, gridDim, heights);
    }

    /** Per-column bare-terrain heights (≈ OCEAN_FLOOR_WG), used by the doPlace() gate. */
    private record HeightGrid(int originX, int originZ, int dim, int[] data) {
        /** OCEAN_FLOOR height at a world column, or Integer.MIN_VALUE if outside/unloaded. */
        int heightAt(int wx, int wz) {
            int lx = wx - originX, lz = wz - originZ;
            if (lx < 0 || lz < 0 || lx >= dim || lz >= dim) return Integer.MIN_VALUE;
            return data[lx * dim + lz];
        }
        /** Max terrain height over an inclusive world-coordinate box (MIN_VALUE if none loaded). */
        int maxInBox(int x0, int z0, int x1, int z1) {
            int max = Integer.MIN_VALUE;
            for (int wx = x0; wx <= x1; wx++) {
                for (int wz = z0; wz <= z1; wz++) {
                    int h = heightAt(wx, wz);
                    if (h > max) max = h;
                }
            }
            return max;
        }
    }

    /**
     * Simulates OreFeature placement for one chunk using the exact MC random call sequence:
     *   setFeatureSeed → CountPlacement → N×(InSquarePlacement + HeightRange + place() + doPlace())
     */
    private static void simulateChunk(Level level, int cx, int cz, long worldSeed,
                                       VeinSimConfig cfg, HeightGrid grid,
                                       List<PredictedOre> out) {
        WorldgenRandom seeder = new WorldgenRandom(new XoroshiroRandomSource(worldSeed));
        long decorationSeed = seeder.setDecorationSeed(worldSeed, cx * 16, cz * 16);
        Dim dim = dimOf(level);

        for (OreSubFeature feat : FEATURES) {
            if (feat.dim() != dim) continue; // only features of the current dimension
            // Simulate if the base ore OR its deepslate variant is enabled — the per-block
            // type (and thus the relevant toggle) is resolved later in filterByTerrain().
            OreType deep = feat.type().deepslateVariant();
            boolean wanted = cfg.isOreEnabled(feat.type()) || (deep != null && cfg.isOreEnabled(deep));
            if (!wanted) continue;
            simulateFeature(level, cx, cz, decorationSeed, feat, grid, out);
        }
    }

    /** Runs one feature's full vein/scatter placement for a chunk (block-aware). */
    private static void simulateFeature(Level level, int cx, int cz, long decorationSeed,
                                        OreSubFeature feat, HeightGrid grid, List<PredictedOre> out) {
        WorldgenRandom wgr = new WorldgenRandom(new XoroshiroRandomSource(0));
        wgr.setFeatureSeed(decorationSeed, feat.decorationIndex(), feat.step());

        int count;
        if (feat.rarityChance() > 0) {
            count = (wgr.nextFloat() < 1.0f / feat.rarityChance()) ? 1 : 0;
        } else if (feat.countMin() == feat.countMax()) {
            count = feat.countMin(); // ConstantInt — 0 random calls
        } else {
            count = wgr.nextInt(feat.countMax() - feat.countMin() + 1) + feat.countMin();
        }

        for (int v = 0; v < count; v++) {
            if (feat.kind() == FeatureKind.SCATTERED) {
                placeScattered(level, cx, cz, wgr, feat, out);
            } else {
                placeVein(level, cx, cz, wgr, feat, grid, out);
            }
        }
    }

    private static Dim dimOf(Level level) {
        return level.dimension() == Level.NETHER ? Dim.NETHER : Dim.OVERWORLD;
    }

    /** True if the block was replaceable at gen time (ore now present → was the base block). */
    private static boolean isReplaceable(BlockState bs, TargetSet t) {
        Block b = bs.getBlock();
        return switch (t) {
            case OVERWORLD  -> isOreContainer(bs) || isAnyOreBlock(bs);
            case NETHERRACK -> b == Blocks.NETHERRACK
                            || b == Blocks.NETHER_GOLD_ORE || b == Blocks.NETHER_QUARTZ_ORE;
            case NETHER_BASE -> b == Blocks.NETHERRACK || b == Blocks.BLACKSTONE
                            || b == Blocks.BASALT || b == Blocks.ANCIENT_DEBRIS;
        };
    }

    /**
     * ScatteredOreFeature.place() — ancient debris. Scatters up to nextInt(size+1) points around
     * the origin; each axis offset = round((nextFloat()-nextFloat()) * min(i,7)). discard is 1.0
     * so canPlaceOre consumes no RNG and only places where NOT adjacent to air (fully buried).
     */
    private static void placeScattered(Level level, int cx, int cz, WorldgenRandom wgr,
                                       OreSubFeature feat, List<PredictedOre> out) {
        int bx = cx * 16 + wgr.nextInt(16);
        int bz = cz * 16 + wgr.nextInt(16);
        int by = sampleHeight(wgr, feat.heightDist(), feat.minY(), feat.maxY());

        int n = wgr.nextInt(feat.size() + 1);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < n; i++) {
            int clamp = Math.min(i, 7);
            int dx = Math.round((wgr.nextFloat() - wgr.nextFloat()) * clamp);
            int dy = Math.round((wgr.nextFloat() - wgr.nextFloat()) * clamp);
            int dz = Math.round((wgr.nextFloat() - wgr.nextFloat()) * clamp);
            int px = bx + dx, py = by + dy, pz = bz + dz;
            m.set(px, py, pz);
            BlockState bs = level.getBlockState(m);
            if (!isReplaceable(bs, feat.target())) continue;     // target.test fails
            if (isAdjacentToAir(level, px, py, pz)) continue;    // discard 1.0 → must be buried
            out.add(new PredictedOre(px, py, pz, feat.type(), feat.biomeReq(),
                                     bx, by, bz, feat.decorationIndex()));
        }
    }

    /**
     * Simulates one vein placement exactly as MC's PlacedFeature + OreFeature does.
     * Emits per-vein-deduplicated blocks; cross-vein/feature dedup is done later, AFTER
     * biome gating, so biome-removed features can't "steal" positions from kept ones.
     */
    private static void placeVein(Level level, int cx, int cz, WorldgenRandom wgr, OreSubFeature feat,
                                   HeightGrid grid, List<PredictedOre> out) {
        // InSquarePlacement: 2 nextInt(16) calls
        int xOff = wgr.nextInt(16);
        int zOff = wgr.nextInt(16);
        int bx = cx * 16 + xOff;
        int bz = cz * 16 + zOff;

        // HeightRangePlacement
        int by = sampleHeight(wgr, feat.heightDist(), feat.minY(), feat.maxY());

        // OreFeature.place(): 3 random calls
        float angle = wgr.nextFloat() * (float)Math.PI;
        int y1off = wgr.nextInt(3) - 2;   // -2, -1, or 0  (bytecode: nextInt(3) then subtract 2)
        int y2off = wgr.nextInt(3) - 2;

        // Blob endpoints
        float g = feat.size() / 8.0f;
        double sinA = Math.sin(angle), cosA = Math.cos(angle);
        double x1 = bx + sinA * g,  x2 = bx - sinA * g;
        double y1 = by + y1off,      y2 = by + y2off;
        double z1 = bz + cosA * g,  z2 = bz - cosA * g;

        // doPlace() gate (from place() bytecode): it scans a small horizontal box and calls
        // doPlace only if minBoundsY <= OCEAN_FLOOR_WG height at SOME column in that box.
        //   local8     = ceil((size/8 + 1) / 2)
        //   minBoundsY = by - 2 - local8
        //   box        = [bx-ceil(g)-local8 .. +range] × [bz-ceil(g)-local8 .. +range]
        //   range      = 2 * (ceil(g) + local8)
        int local8 = Mth.ceil((feat.size() / 8.0f + 1.0f) / 2.0f);
        int minBoundsY = by - 2 - local8;
        int ceilG = Mth.ceil(g);
        int boxMinX = bx - ceilG - local8;
        int boxMinZ = bz - ceilG - local8;
        int range = 2 * (ceilG + local8);
        int boxMaxX = boxMinX + range;
        int boxMaxZ = boxMinZ + range;

        int terrainMax = grid.maxInBox(boxMinX, boxMinZ, boxMaxX, boxMaxZ);
        // If no column in the box is loaded (terrainMax == MIN_VALUE), we can't predict this
        // chunk reliably — treat doPlace as NOT called (skip; avoids RNG desync from guessing).
        boolean doPlaceCalled = (terrainMax != Integer.MIN_VALUE) && (minBoundsY <= terrainMax);

        if (!doPlaceCalled) {
            // doPlace skipped — no nextDouble calls, no ore blocks
            return;
        }

        // OreFeature.doPlace(): compute sphere positions and radii.
        // Each sphere consumes exactly 1 nextDouble() call, all consumed up front.
        int size = feat.size();
        double[] sx = new double[size], sy = new double[size];
        double[] sz = new double[size], sr = new double[size];

        for (int i = 0; i < size; i++) {
            float t = (float)i / (float)size;
            sx[i] = Mth.lerp(t, x1, x2);
            sy[i] = Mth.lerp(t, y1, y2);
            sz[i] = Mth.lerp(t, z1, z2);
            double randR = wgr.nextDouble() * size / 16.0;
            // MC uses Mth.sin (a float lookup table) with float arithmetic (Mth.PI * t),
            // NOT Math.sin in double — replicate exactly to match the table index.
            sr[i] = ((Mth.sin(Mth.PI * t) + 1.0) * randR + 1.0) / 2.0;
        }

        // Remove redundant spheres (MC optimization: if one sphere fully contains another,
        // mark the smaller one r=-1 to skip it).
        for (int i = 0; i < size - 1; i++) {
            if (sr[i] <= 0) continue;
            for (int j = i + 1; j < size; j++) {
                if (sr[j] <= 0) continue;
                double ddx = sx[i] - sx[j];
                double ddy = sy[i] - sy[j];
                double ddz = sz[i] - sz[j];
                double dr  = sr[i] - sr[j];
                // If |dr| > distance(centers), one sphere is inside the other
                if (dr * dr > ddx*ddx + ddy*ddy + ddz*ddz) {
                    if (dr > 0) sr[j] = -1;  // i contains j
                    else        sr[i] = -1;  // j contains i
                }
            }
        }

        // Generate ore block positions from remaining spheres.
        // MC's doPlace() tests the block CENTER (x+0.5, y+0.5, z+0.5) against the sphere,
        // and uses floor() — not ceil() — for the upper loop bound. Each unique block is
        // visited once per vein (MC uses a BitSet); collect coords into a per-vein list.
        Set<Long> veinSeen = new HashSet<>();
        List<int[]> veinBlocks = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            double r = sr[i];
            if (r <= 0) continue;
            int x0 = Mth.floor(sx[i] - r), x1b = Mth.floor(sx[i] + r);
            int y0 = Mth.floor(sy[i] - r), y1b = Mth.floor(sy[i] + r);
            int z0 = Mth.floor(sz[i] - r), z1b = Mth.floor(sz[i] + r);
            for (int bx2 = x0; bx2 <= x1b; bx2++) {
                double dx  = (bx2 + 0.5 - sx[i]) / r;
                double dx2 = dx * dx;
                if (dx2 >= 1.0) continue;
                for (int by2 = y0; by2 <= y1b; by2++) {
                    double dy  = (by2 + 0.5 - sy[i]) / r;
                    double dy2 = dy * dy;
                    if (dx2 + dy2 >= 1.0) continue;
                    for (int bz2 = z0; bz2 <= z1b; bz2++) {
                        double dz = (bz2 + 0.5 - sz[i]) / r;
                        if (dx2 + dy2 + dz * dz >= 1.0) continue;
                        // Out-of-build-height positions read as air below → skipped by the
                        // block check; no need for a hardcoded dimension range here.
                        if (veinSeen.add(packXYZ(bx2, by2, bz2))) {
                            veinBlocks.add(new int[]{bx2, by2, bz2});
                        }
                    }
                }
            }
        }

        // Block-aware placement — replicates OreFeature.canPlaceOre() EXACTLY using the real
        // (loaded) world, in MC's visitation order. This nails the air-exposure ores (coal_lower,
        // gold, diamond) and removes air/cave false positives, instead of approximating.
        //
        //   target.test : passes if the spot was ore-replaceable at gen time. On a generated
        //                 world the spot now holds the ore (or stone on anti-xray servers), so
        //                 "replaceable" = stone-family/deepslate/tuff OR any ore block.
        //   shouldSkipAirCheck : only when target.test passed; for 0<discard<1 consumes ONE
        //                 nextFloat(); >=1 and <=0 consume none.
        //   placed unless the air check applies and the block is adjacent to air.
        float discard = feat.discardChance();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int[] b : veinBlocks) {
            m.set(b[0], b[1], b[2]);
            BlockState bs = level.getBlockState(m);
            if (!isReplaceable(bs, feat.target())) continue; // target.test fails → no RNG

            boolean place;
            if (discard <= 0.0f) {
                place = true;                                   // skip air check; always place
            } else if (discard >= 1.0f) {
                place = !isAdjacentToAir(level, b[0], b[1], b[2]); // air check, no RNG
            } else {
                // nextFloat is ALWAYS consumed here (left operand) — matches shouldSkipAirCheck.
                place = (wgr.nextFloat() >= discard) || !isAdjacentToAir(level, b[0], b[1], b[2]);
            }
            if (place) {
                out.add(new PredictedOre(b[0], b[1], b[2], feat.type(), feat.biomeReq(),
                                         bx, by, bz, feat.decorationIndex()));
            }
        }
    }

    /** Replicates OreFeature.isAdjacentToAir: true if any of the 6 face-neighbors is air. */
    private static boolean isAdjacentToAir(Level level, int x, int y, int z) {
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        if (level.getBlockState(n.set(x + 1, y, z)).isAir()) return true;
        if (level.getBlockState(n.set(x - 1, y, z)).isAir()) return true;
        if (level.getBlockState(n.set(x, y + 1, z)).isAir()) return true;
        if (level.getBlockState(n.set(x, y - 1, z)).isAir()) return true;
        if (level.getBlockState(n.set(x, y, z + 1)).isAir()) return true;
        if (level.getBlockState(n.set(x, y, z - 1)).isAir()) return true;
        return false;
    }

    /** Samples Y coordinate using the same random calls as MC's HeightRangePlacement. */
    private static int sampleHeight(WorldgenRandom wgr, HeightDist dist, int minY, int maxY) {
        if (dist == HeightDist.UNIFORM) {
            // UniformHeight: Mth.randomBetweenInclusive(rand, minY, maxY) = nextInt(max-min+1)+min
            int range = maxY - minY;
            if (range < 0) return minY;  // degenerate
            return wgr.nextInt(range + 1) + minY;
        } else {
            // TrapezoidHeight (plateau=0), exact MC order:
            //   side = (range - plateau)/2 = range/2  ;  big = range - side
            //   return min + randomBetweenInclusive(0, big) + randomBetweenInclusive(0, side)
            //   randomBetweenInclusive(0, n) = nextInt(n+1)
            int range = maxY - minY;
            if (range <= 0) return minY;
            int side = range / 2;
            int big = range - side;
            int a = wgr.nextInt(big + 1);    // first call uses (range - range/2)
            int b = wgr.nextInt(side + 1);   // second call uses range/2
            return minY + a + b;
        }
    }

    private static long packXYZ(int x, int y, int z) {
        return ((long)(x & 0x3FFFFFF) << 38) | ((long)(y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }

    /**
     * Approximates MC's OCEAN_FLOOR_WG heightmap (used by OreFeature.place()'s doPlace gate)
     * from client-available block data. WORLD_SURFACE/MOTION_BLOCKING include trees and so sit
     * ABOVE the bare terrain — using them flips the gate for near-surface veins and cascades RNG
     * desync through a whole feature. Here we scan down from WORLD_SURFACE past vegetation, snow,
     * fluids, logs and leaves to the first real terrain block, returning its top (solidY+1) to
     * match the heightmap convention.
     */
    private static int genSurfaceY(Level level, int x, int z, BlockPos.MutableBlockPos m) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z); // first air above everything
        int minY = level.getMinY();
        for (int y = top - 1; y >= minY; y--) {
            m.set(x, y, z);
            BlockState s = level.getBlockState(m);
            // Skip decoration cover: air/fluids/plants/snow-layer (all replaceable), plus trees.
            if (s.canBeReplaced()) continue;
            if (s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES)) continue;
            return y + 1; // top of bare terrain (heightmap convention)
        }
        return top;
    }

    // ─── Game-thread terrain filter ───────────────────────────────────────────

    private static List<PredictedOre> filterByTerrain(Level level, List<PredictedOre> candidates) {
        VeinSimConfig cfg = ConfigManager.getConfig();
        List<PredictedOre> out = new ArrayList<>();
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos opos = new BlockPos.MutableBlockPos();
        Set<Long> emitted = new HashSet<>();
        for (PredictedOre ore : candidates) {
            // Biome gating at the vein ORIGIN (copper_large→dripstone, emerald→mountain, etc.).
            // Done FIRST so biome-removed features don't block (dedup) kept ones at the same spot.
            if (ore.biomeReq() != BiomeReq.ANY) {
                opos.set(ore.ox(), ore.oy(), ore.oz());
                if (!biomeMatches(level, opos, ore.biomeReq())) continue;
            }

            mpos.set(ore.x(), ore.y(), ore.z());
            if (!level.isLoaded(mpos)) continue;
            BlockState bs = level.getBlockState(mpos);

            // The block-aware sim already validated this is real terrain/ore. Resolve the actual
            // ore variant: ore in deepslate (or a deepslate ore block) is the deepslate variant.
            OreType type = ore.type();
            if (isDeepslate(bs)) {
                OreType deep = type.deepslateVariant();
                if (deep != null) type = deep;
            }
            // Only show if the (resolved) variant is the one the user enabled.
            if (!cfg.isOreEnabled(type)) continue;

            if (!emitted.add(packXYZ(ore.x(), ore.y(), ore.z()))) continue;
            out.add(new PredictedOre(ore.x(), ore.y(), ore.z(), type, ore.biomeReq(),
                                     ore.ox(), ore.oy(), ore.oz(), ore.featureIndex()));
        }
        return out;
    }

    private static boolean biomeMatches(Level level, BlockPos pos, BiomeReq req) {
        if (req == BiomeReq.ANY) return true;
        var biome = level.getBiome(pos);
        return switch (req) {
            case DRIPSTONE -> biome.is(net.minecraft.world.level.biome.Biomes.DRIPSTONE_CAVES);
            case MOUNTAIN  -> biome.is(net.minecraft.tags.BiomeTags.IS_MOUNTAIN);
            case BADLANDS  -> biome.is(net.minecraft.tags.BiomeTags.IS_BADLANDS);
            case BASALT_DELTAS     -> biome.is(net.minecraft.world.level.biome.Biomes.BASALT_DELTAS);
            case NETHER_NON_DELTAS -> !biome.is(net.minecraft.world.level.biome.Biomes.BASALT_DELTAS);
            default        -> true;
        };
    }

    /** True if the block is deepslate-region (stone container or a deepslate ore variant). */
    private static boolean isDeepslate(BlockState bs) {
        Block b = bs.getBlock();
        return b == Blocks.DEEPSLATE || b == Blocks.TUFF
            || b == Blocks.DEEPSLATE_COAL_ORE     || b == Blocks.DEEPSLATE_IRON_ORE
            || b == Blocks.DEEPSLATE_COPPER_ORE   || b == Blocks.DEEPSLATE_GOLD_ORE
            || b == Blocks.DEEPSLATE_REDSTONE_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE
            || b == Blocks.DEEPSLATE_DIAMOND_ORE  || b == Blocks.DEEPSLATE_EMERALD_ORE;
    }

    private static boolean isOreContainer(BlockState bs) {
        if (bs.isAir()) return false;
        Block b = bs.getBlock();
        // Exactly the stone_ore_replaceables + deepslate_ore_replaceables tags.
        return b == Blocks.STONE   || b == Blocks.GRANITE || b == Blocks.DIORITE
            || b == Blocks.ANDESITE || b == Blocks.DEEPSLATE || b == Blocks.TUFF;
    }

    private static boolean isAnyOreBlock(BlockState bs) {
        Block b = bs.getBlock();
        return b == Blocks.COAL_ORE     || b == Blocks.DEEPSLATE_COAL_ORE
            || b == Blocks.IRON_ORE     || b == Blocks.DEEPSLATE_IRON_ORE
            || b == Blocks.COPPER_ORE   || b == Blocks.DEEPSLATE_COPPER_ORE
            || b == Blocks.GOLD_ORE     || b == Blocks.DEEPSLATE_GOLD_ORE
            || b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE
            || b == Blocks.LAPIS_ORE    || b == Blocks.DEEPSLATE_LAPIS_ORE
            || b == Blocks.DIAMOND_ORE  || b == Blocks.DEEPSLATE_DIAMOND_ORE
            || b == Blocks.EMERALD_ORE  || b == Blocks.DEEPSLATE_EMERALD_ORE;
    }
}
