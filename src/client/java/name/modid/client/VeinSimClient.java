package name.modid.client;

import com.mojang.blaze3d.platform.InputConstants;
import name.modid.VeinSim;
import name.modid.client.config.ConfigManager;
import name.modid.client.config.VeinSimConfig;
import name.modid.client.ore.OrePredictor;
import name.modid.client.ore.OrePredictor.PredictedOre;
import name.modid.client.ore.OreType;
import name.modid.client.render.OreOverlayRenderer;
import name.modid.client.screen.ConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
import static com.mojang.brigadier.arguments.LongArgumentType.getLong;
import static com.mojang.brigadier.arguments.LongArgumentType.longArg;

public class VeinSimClient implements ClientModInitializer {

    /** Opens the config screen (default V — changeable in MC Controls). */
    public static KeyMapping configKey;

    /** Whether the ore overlay is currently showing. */
    public static volatile boolean overlayActive = false;

    /** Edge-detection state for toggle mode. */
    private static boolean prevKeyDown = false;

    /** Custom key binding category for VeinSim. */
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("vein-sim", "vein_sim"));

    /**
     * Registers the /VeinSim debug and verify diagnostic commands. Keep FALSE for release builds
     * (only /VeinSim seed is exposed). Flip to TRUE when porting to another MC version to gather
     * indices/data and confirm predictions, then flip back.
     */
    private static final boolean DIAGNOSTIC_COMMANDS = false;

    @Override
    public void onInitializeClient() {
        ConfigManager.load();

        // Config screen key (V) — registered in MC controls
        configKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.vein-sim.open_config",
                GLFW.GLFW_KEY_V,
                CATEGORY
        ));

        registerCommand();
        registerTickHandler();
        LevelRenderEvents.BEFORE_GIZMOS.register(OreOverlayRenderer::render);

        VeinSim.LOGGER.info("[VeinSim] Client initialized.");
    }

    // ─── Command ─────────────────────────────────────────────────────────────

    private void registerCommand() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            var root = literal("VeinSim")
                .then(literal("seed")
                    .then(argument("seed", longArg())
                        .executes(ctx -> {
                            long seed = getLong(ctx, "seed");
                            ConfigManager.getConfig().worldSeed = seed;
                            ConfigManager.save();
                            OrePredictor.clearCache();
                            ctx.getSource().sendFeedback(
                                Component.literal("§a[VeinSim] World seed set to: §f" + seed));
                            return 1;
                        })));

            // Diagnostics (singleplayer) — only present when porting to another MC version.
            if (DIAGNOSTIC_COMMANDS) {
                root.then(literal("debug")
                        .executes(ctx -> { dumpFeatureData(ctx.getSource()); return 1; }))
                    .then(literal("verify")
                        .executes(ctx -> { verifyAgainstWorld(ctx.getSource()); return 1; }));
            }

            dispatcher.register(root);
        });
    }

    // ─── Diagnostics ────────────────────────────────────────────────────────────

    /**
     * Dumps the live worldgen data for the current dimension's ore step: each feature's index,
     * name, feature type (OreFeature vs ScatteredOreFeature), vein size, and air-discard chance.
     * Directly comparable to OrePredictor.FEATURES — the fastest way to confirm/port a version.
     */
    @SuppressWarnings("unchecked")
    private static void dumpFeatureData(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource src) {
        try {
            IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
            if (server == null) {
                src.sendFeedback(Component.literal("§c[VeinSim] /VeinSim debug only works in singleplayer."));
                return;
            }
            ServerLevel level = server.getLevel(Minecraft.getInstance().player.level().dimension());
            if (level == null) level = server.getLevel(Level.OVERWORLD);
            ChunkGenerator gen = level.getChunkSource().getGenerator();

            Field field = ChunkGenerator.class.getDeclaredField("featuresPerStep");
            field.setAccessible(true);
            Supplier<List<FeatureSorter.StepFeatureData>> supplier =
                    (Supplier<List<FeatureSorter.StepFeatureData>>) field.get(gen);
            List<FeatureSorter.StepFeatureData> steps = supplier.get();
            var registry = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);

            boolean nether = level.dimension() == Level.NETHER;
            int oreStep = nether ? 7 : 6;
            src.sendFeedback(Component.literal("§6[VeinSim] " + (nether ? "NETHER step 7" : "OVERWORLD step 6")
                    + " — index | name | type | size | discard:"));
            if (steps.size() <= oreStep) {
                src.sendFeedback(Component.literal("§c[VeinSim] Step " + oreStep + " absent (only " + steps.size() + " steps)."));
                return;
            }
            List<PlacedFeature> features = steps.get(oreStep).features();
            for (int i = 0; i < features.size(); i++) {
                PlacedFeature pf = features.get(i);
                String name = registry.getResourceKey(pf).map(k -> k.identifier().getPath()).orElse("?");
                if (!(name.contains("ore") || name.contains("debris"))) continue;
                ConfiguredFeature<?, ?> cf = pf.feature().value();
                String type = cf.feature().getClass().getSimpleName();
                FeatureConfiguration cfg = cf.config();
                String extra = "";
                if (cfg instanceof OreConfiguration oc) {
                    extra = " size=" + oc.size + " discard=" + oc.discardChanceOnAirExposure;
                }
                src.sendFeedback(Component.literal("§f [" + i + "] " + name + " §7(" + type + ")" + extra));
            }
        } catch (Exception e) {
            src.sendFeedback(Component.literal("§c[VeinSim] debug failed: " + e));
            VeinSim.LOGGER.error("[VeinSim] debug error", e);
        }
    }

    /**
     * Singleplayer diagnostic: scans the current chunk for real ores, runs our prediction with the
     * REAL seed, and reports per-type / per-feature match rates plus a copper offset histogram.
     */
    private static void verifyAgainstWorld(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource src) {
        try {
            IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
            if (server == null) {
                src.sendFeedback(Component.literal("§c[VeinSim] /VeinSim verify only works in singleplayer."));
                return;
            }
            ServerLevel level = server.getLevel(Minecraft.getInstance().player.level().dimension());
            if (level == null) level = server.getLevel(Level.OVERWORLD);

            long realSeed = level.getSeed();
            long cfgSeed = ConfigManager.getConfig().worldSeed;
            BlockPos pp = Minecraft.getInstance().player.blockPosition();
            int cx = pp.getX() >> 4, cz = pp.getZ() >> 4;

            src.sendFeedback(Component.literal("§6══ VeinSim verify ══ chunk (" + cx + ", " + cz + ")"));
            src.sendFeedback(Component.literal("§fReal seed: §a" + realSeed
                    + (cfgSeed == realSeed ? " §a(config matches)" : " §c(config=" + cfgSeed + " MISMATCH)")));

            ChunkAccess chunk = level.getChunk(cx, cz);
            int minY = level.getMinY(), maxY = level.getMaxY();
            java.util.Map<OreType, Set<Long>> realByType = new java.util.EnumMap<>(OreType.class);
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
                int wx = cx * 16 + lx, wz = cz * 16 + lz;
                for (int y = minY; y <= maxY; y++) {
                    m.set(wx, y, wz);
                    OreType base = baseOreOf(chunk.getBlockState(m).getBlock());
                    if (base != null) realByType.computeIfAbsent(base, k -> new HashSet<>()).add(pack(wx, y, wz));
                }
            }

            List<PredictedOre> predicted = OrePredictor.debugPredictChunk(level, cx, cz, realSeed);
            java.util.Map<OreType, Set<Long>> predByType = new java.util.EnumMap<>(OreType.class);
            java.util.Map<Integer, Set<Long>> predByFeature = new java.util.HashMap<>();
            java.util.Map<Integer, OreType> featBase = new java.util.HashMap<>();
            for (PredictedOre p : predicted) {
                predByType.computeIfAbsent(p.type(), k -> new HashSet<>()).add(pack(p.x(), p.y(), p.z()));
                predByFeature.computeIfAbsent(p.featureIndex(), k -> new HashSet<>()).add(pack(p.x(), p.y(), p.z()));
                featBase.put(p.featureIndex(), baseOf(p.type()));
            }

            OreType[] bases = { OreType.COAL, OreType.IRON, OreType.COPPER, OreType.GOLD,
                                OreType.REDSTONE, OreType.LAPIS, OreType.DIAMOND, OreType.EMERALD,
                                OreType.ANCIENT_DEBRIS, OreType.NETHER_QUARTZ, OreType.NETHER_GOLD };
            for (OreType base : bases) {
                Set<Long> real = realByType.getOrDefault(base, java.util.Collections.emptySet());
                Set<Long> pred = predByType.getOrDefault(base, java.util.Collections.emptySet());
                if (real.isEmpty() && pred.isEmpty()) continue;
                int match = 0;
                for (long k : pred) if (real.contains(k)) match++;
                String color = (match > 0 && match == pred.size() && match == real.size()) ? "§a"
                             : (match > 0) ? "§e" : "§c";
                src.sendFeedback(Component.literal(color + base.displayName
                        + ": real=" + real.size() + " predicted=" + pred.size() + " matched=" + match));
                for (var e : predByFeature.entrySet()) {
                    if (featBase.get(e.getKey()) != base) continue;
                    int fm = 0;
                    for (long k : e.getValue()) if (real.contains(k)) fm++;
                    src.sendFeedback(Component.literal("§7    feat[" + e.getKey() + "] predicted="
                            + e.getValue().size() + " matched=" + fm));
                }
            }
            offsetAnalysis(src, "COPPER",
                    predByType.getOrDefault(OreType.COPPER, java.util.Collections.emptySet()),
                    realByType.getOrDefault(OreType.COPPER, java.util.Collections.emptySet()));

            if (cfgSeed != realSeed) {
                ConfigManager.getConfig().worldSeed = realSeed;
                ConfigManager.save();
                OrePredictor.clearCache();
                src.sendFeedback(Component.literal("§a[VeinSim] Auto-set configured seed to the real seed."));
            }
        } catch (Exception e) {
            src.sendFeedback(Component.literal("§c[VeinSim] verify failed: " + e));
            VeinSim.LOGGER.error("[VeinSim] verify error", e);
        }
    }

    private static OreType baseOf(OreType t) {
        return switch (t) {
            case DEEPSLATE_COAL -> OreType.COAL;
            case DEEPSLATE_IRON -> OreType.IRON;
            case DEEPSLATE_COPPER -> OreType.COPPER;
            case DEEPSLATE_GOLD -> OreType.GOLD;
            case DEEPSLATE_LAPIS -> OreType.LAPIS;
            case DEEPSLATE_REDSTONE -> OreType.REDSTONE;
            case DEEPSLATE_DIAMOND -> OreType.DIAMOND;
            case DEEPSLATE_EMERALD -> OreType.EMERALD;
            default -> t;
        };
    }

    private static void offsetAnalysis(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource src,
                                       String label, Set<Long> pred, Set<Long> real) {
        if (pred.isEmpty() || real.isEmpty()) {
            src.sendFeedback(Component.literal("§7  " + label + " offsets: (no data)"));
            return;
        }
        int radius = 6;
        java.util.Map<String, Integer> hist = new java.util.HashMap<>();
        int noNeighbor = 0;
        for (long pk : pred) {
            int px = unX(pk), py = unY(pk), pz = unZ(pk);
            int bestDist = Integer.MAX_VALUE; String bestOff = null;
            for (int dx = -radius; dx <= radius; dx++)
                for (int dy = -radius; dy <= radius; dy++)
                    for (int dz = -radius; dz <= radius; dz++)
                        if (real.contains(pack(px + dx, py + dy, pz + dz))) {
                            int d = dx*dx + dy*dy + dz*dz;
                            if (d < bestDist) { bestDist = d; bestOff = dx + "," + dy + "," + dz; }
                        }
            if (bestOff == null) noNeighbor++; else hist.merge(bestOff, 1, Integer::sum);
        }
        src.sendFeedback(Component.literal("§7  " + label + " nearest-real offset (top 4):"));
        hist.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(4)
            .forEach(e -> src.sendFeedback(Component.literal("§7    offset(" + e.getKey() + ") x" + e.getValue())));
        src.sendFeedback(Component.literal("§7    no real within " + radius + ": " + noNeighbor));
    }

    private static OreType baseOreOf(Block b) {
        if (b == Blocks.COAL_ORE     || b == Blocks.DEEPSLATE_COAL_ORE)     return OreType.COAL;
        if (b == Blocks.IRON_ORE     || b == Blocks.DEEPSLATE_IRON_ORE)     return OreType.IRON;
        if (b == Blocks.COPPER_ORE   || b == Blocks.DEEPSLATE_COPPER_ORE)   return OreType.COPPER;
        if (b == Blocks.GOLD_ORE     || b == Blocks.DEEPSLATE_GOLD_ORE)     return OreType.GOLD;
        if (b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE) return OreType.REDSTONE;
        if (b == Blocks.LAPIS_ORE    || b == Blocks.DEEPSLATE_LAPIS_ORE)    return OreType.LAPIS;
        if (b == Blocks.DIAMOND_ORE  || b == Blocks.DEEPSLATE_DIAMOND_ORE)  return OreType.DIAMOND;
        if (b == Blocks.EMERALD_ORE  || b == Blocks.DEEPSLATE_EMERALD_ORE)  return OreType.EMERALD;
        if (b == Blocks.ANCIENT_DEBRIS)     return OreType.ANCIENT_DEBRIS;
        if (b == Blocks.NETHER_QUARTZ_ORE)  return OreType.NETHER_QUARTZ;
        if (b == Blocks.NETHER_GOLD_ORE)    return OreType.NETHER_GOLD;
        return null;
    }

    private static long pack(int x, int y, int z) {
        return ((long)(x & 0x3FFFFFF) << 38) | ((long)(y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }
    private static int unX(long k) { return signExtend((int)((k >> 38) & 0x3FFFFFF), 26); }
    private static int unY(long k) { return signExtend((int)((k >> 26) & 0xFFF), 12); }
    private static int unZ(long k) { return signExtend((int)(k & 0x3FFFFFF), 26); }
    private static int signExtend(int v, int bits) { int s = 32 - bits; return (v << s) >> s; }

    // ─── Tick ─────────────────────────────────────────────────────────────────

    private void registerTickHandler() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Open config screen
            while (configKey.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.setScreenAndShow(new ConfigScreen());
                }
            }

            // Don't handle activation key while a screen is open
            if (client.gui.screen() != null) return;

            VeinSimConfig cfg = ConfigManager.getConfig();
            boolean keyDown = InputConstants.isKeyDown(client.getWindow(), cfg.activationKeyCode);

            boolean wasActive = overlayActive;
            if (cfg.toggleMode) {
                if (keyDown && !prevKeyDown) overlayActive = !overlayActive;
            } else {
                overlayActive = keyDown; // hold mode: active only while held
            }
            prevKeyDown = keyDown;

            // On any enable/disable transition: clear the cache and (optionally) announce in chat.
            if (overlayActive != wasActive) {
                if (!overlayActive) OrePredictor.clearCache();
                if (cfg.statusMessages) sendStatus(client, overlayActive);
            }

            if (overlayActive) {
                OrePredictor.tick();
            }
        });
    }

    private static void sendStatus(Minecraft client, boolean enabled) {
        if (client.player == null) return;
        client.player.sendSystemMessage(
                Component.literal(enabled ? "§a[VeinSim] Enabled" : "§c[VeinSim] Disabled"));
    }
}
