package name.modid.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import name.modid.client.config.ConfigManager;
import name.modid.client.config.VeinSimConfig;
import name.modid.client.ore.OrePredictor;
import name.modid.client.ore.OreType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.Map;

public class ConfigScreen extends Screen {

    // Left column: regular ores  |  Right column: deepslate variants
    private static final OreType[] LEFT_COL = {
        OreType.COAL, OreType.IRON, OreType.COPPER, OreType.GOLD,
        OreType.LAPIS, OreType.REDSTONE, OreType.DIAMOND, OreType.EMERALD
    };
    private static final OreType[] RIGHT_COL = {
        OreType.DEEPSLATE_COAL, OreType.DEEPSLATE_IRON, OreType.DEEPSLATE_COPPER,
        OreType.DEEPSLATE_GOLD, OreType.DEEPSLATE_LAPIS, OreType.DEEPSLATE_REDSTONE,
        OreType.DEEPSLATE_DIAMOND, OreType.DEEPSLATE_EMERALD
    };
    // Nether ores (own row below the columns).
    private static final OreType[] NETHER_ROW = {
        OreType.ANCIENT_DEBRIS, OreType.NETHER_QUARTZ, OreType.NETHER_GOLD
    };

    private final VeinSimConfig config = ConfigManager.getConfig();
    private final Map<OreType, Button> oreButtons = new EnumMap<>(OreType.class);

    private Button keyButton;
    private Button toggleBtn;
    private Button holdBtn;
    private Button rangeBtn;
    private Button maxBtn;
    private Button veinBtn;
    private Button statusBtn;
    private boolean capturingKey = false;

    public ConfigScreen() {
        super(Component.literal("VeinSim Configuration"));
    }

    // Layout fields computed in init() (also used by extractRenderState for headers/warnings).
    private int warnY, oresHeaderY;

    @Override
    protected void init() {
        oreButtons.clear();
        int w = this.width;
        int cx = w / 2;
        int margin = Math.max(14, w / 18);
        int usable = w - 2 * margin;
        int colGap = 10;
        int colW = (usable - colGap) / 2;
        int leftX = margin;
        int rightX = margin + colW + colGap;

        int y = 22;

        // ── Activation key (wide, centered) ──────────────────────────────────
        int kw = Math.min(usable, 360);
        keyButton = Button.builder(keyLabel(), btn -> startCapture())
                .bounds(cx - kw / 2, y, kw, 20).build();
        addRenderableWidget(keyButton);
        y += 23;

        // ── Mode (two halves of the key width) ───────────────────────────────
        int mw = (kw - 8) / 2;
        toggleBtn = Button.builder(Component.literal(config.toggleMode ? "§a[ TOGGLE ]" : "TOGGLE"),
                btn -> setMode(true)).bounds(cx - kw / 2, y, mw, 20).build();
        holdBtn = Button.builder(Component.literal(!config.toggleMode ? "§a[ HOLD ]" : "HOLD"),
                btn -> setMode(false)).bounds(cx - kw / 2 + mw + 8, y, mw, 20).build();
        addRenderableWidget(toggleBtn);
        addRenderableWidget(holdBtn);
        y += 23;

        // ── Search range (left col) + Max results (right col), as -/+ steppers ─
        rangeBtn = buildStepper(leftX, y, colW, rangeLabel(), () -> changeRange(-1), () -> changeRange(1));
        maxBtn   = buildStepper(rightX, y, colW, maxLabel(),  () -> changeMax(-1),   () -> changeMax(1));
        y += 23;

        // ── Nearest-vein toggle (left) + Chat-status toggle (right) ──────────
        veinBtn = Button.builder(veinLabel(), btn -> toggleVein())
                .bounds(leftX, y, colW, 20).build();
        statusBtn = Button.builder(statusLabel(), btn -> toggleStatus())
                .bounds(rightX, y, colW, 20).build();
        addRenderableWidget(veinBtn);
        addRenderableWidget(statusBtn);
        y += 23;

        // ── Warning line + ores header (drawn in extractRenderState) ─────────
        warnY = y;
        y += 12;
        oresHeaderY = y;
        y += 14;

        // ── Ore toggles: flow layout, boxes sized to text. Overworld (short) →
        //    deepslate (medium) → nether (largest), each group wraps + is centered. ─
        y = flowGroup(LEFT_COL,   margin, y, usable);
        y += 4;
        y = flowGroup(RIGHT_COL,  margin, y, usable);
        y += 4;
        y = flowGroup(NETHER_ROW, margin, y, usable);

        // ── Done (sits after the content, but never off the bottom of the screen) ─
        int doneY = Math.min(y + 8, this.height - 26);
        addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(cx - 60, doneY, 120, 20).build());
    }

    /**
     * Lays out a group of ore toggles as a wrapped, centered flow of buttons each sized to its
     * own label width. Returns the Y just below the group.
     */
    private int flowGroup(OreType[] types, int areaX, int y, int areaW) {
        int gap = 6, rowH = 21, pad = 16, minW = 56, btnH = 19;
        int i = 0;
        while (i < types.length) {
            // Greedily collect buttons that fit on this row.
            java.util.List<OreType> row = new java.util.ArrayList<>();
            java.util.List<Integer> ws = new java.util.ArrayList<>();
            int rowW = 0;
            while (i < types.length) {
                int bw = Math.max(minW, this.font.width(oreLabel(types[i], config.isOreEnabled(types[i]))) + pad);
                int add = (row.isEmpty() ? 0 : gap) + bw;
                if (!row.isEmpty() && rowW + add > areaW) break;
                rowW += add; row.add(types[i]); ws.add(bw); i++;
            }
            int x = areaX + (areaW - rowW) / 2; // center the row
            for (int k = 0; k < row.size(); k++) {
                Button btn = oreBtn(row.get(k), x, y, ws.get(k), btnH);
                addRenderableWidget(btn);
                oreButtons.put(row.get(k), btn);
                x += ws.get(k) + gap;
            }
            y += rowH;
        }
        return y;
    }

    /** Builds a "[-] label [+]" stepper spanning width w at (x,y); returns the (inactive) label button. */
    private Button buildStepper(int x, int y, int w, Component label, Runnable minus, Runnable plus) {
        int bw = 22;
        addRenderableWidget(Button.builder(Component.literal("−"), b -> minus.run())
                .bounds(x, y, bw, 20).build());
        Button mid = Button.builder(label, b -> {}).bounds(x + bw + 2, y, w - 2 * (bw + 2), 20).build();
        mid.active = false; // display only
        addRenderableWidget(mid);
        addRenderableWidget(Button.builder(Component.literal("+"), b -> plus.run())
                .bounds(x + w - bw, y, bw, 20).build());
        return mid;
    }

    private Component rangeLabel() {
        return Component.literal("Range: " + config.clampedRadius() + " chunk" + (config.clampedRadius() == 1 ? "" : "s"));
    }

    private void changeRange(int delta) {
        config.searchRadiusChunks = Math.max(1,
                Math.min(VeinSimConfig.MAX_SEARCH_RADIUS, config.clampedRadius() + delta));
        rangeBtn.setMessage(rangeLabel());
        OrePredictor.clearCache();
    }

    private Component maxLabel() {
        return Component.literal("Max boxes: " + config.maxResults);
    }

    private void changeMax(int delta) {
        int[] p = VeinSimConfig.MAX_RESULT_PRESETS;
        int idx = 0;
        for (int i = 0; i < p.length; i++) if (p[i] == config.maxResults) idx = i;
        // if current value isn't an exact preset, snap to nearest then step
        idx = Math.max(0, Math.min(p.length - 1, idx + delta));
        config.maxResults = p[idx];
        maxBtn.setMessage(maxLabel());
        OrePredictor.clearCache();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Component keyLabel() {
        String name = InputConstants.Type.KEYSYM
                .getOrCreate(config.activationKeyCode)
                .getDisplayName().getString();
        return Component.literal("Activation Key: [ " + name + " ]");
    }

    private void startCapture() {
        capturingKey = true;
        keyButton.setMessage(Component.literal("Activation Key: [ press any key... ]"));
    }

    private void setMode(boolean toggle) {
        config.toggleMode = toggle;
        toggleBtn.setMessage(Component.literal(toggle ? "§a[ TOGGLE ]" : "TOGGLE"));
        holdBtn  .setMessage(Component.literal(!toggle ? "§a[ HOLD ]"  : "HOLD"));
    }

    private Button oreBtn(OreType type, int x, int y, int w) {
        return oreBtn(type, x, y, w, 20);
    }

    private Button oreBtn(OreType type, int x, int y, int w, int h) {
        boolean on = config.isOreEnabled(type);
        return Button.builder(oreLabel(type, on), btn -> {
            boolean next = !config.isOreEnabled(type);
            config.setOreEnabled(type, next);
            btn.setMessage(oreLabel(type, next));
            OrePredictor.clearCache();
        }).bounds(x, y, w, h).build();
    }

    private static Component oreLabel(OreType type, boolean on) {
        // White name; a small green/gray dot shows on/off state.
        return Component.literal((on ? "§a● §f" : "§8● §f") + type.displayName);
    }

    private Component veinLabel() {
        return Component.literal("Nearest vein only: "
                + (config.nearestVeinOnly ? "§aON" : "§7OFF"));
    }

    private void toggleVein() {
        config.nearestVeinOnly = !config.nearestVeinOnly;
        veinBtn.setMessage(veinLabel());
        OrePredictor.clearCache();
    }

    private Component statusLabel() {
        return Component.literal("Chat status: "
                + (config.statusMessages ? "§aON" : "§7OFF"));
    }

    private void toggleStatus() {
        config.statusMessages = !config.statusMessages;
        statusBtn.setMessage(statusLabel());
    }

    // ─── Input handling ───────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (capturingKey) {
            capturingKey = false;
            if (event.key() != GLFW.GLFW_KEY_ESCAPE) {
                config.activationKeyCode = event.key();
            }
            keyButton.setMessage(keyLabel());
            return true;
        }
        return super.keyPressed(event);
    }

    // ─── Rendering ───────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        extractMenuBackground(g);

        g.centeredText(font, Component.literal("§lVeinSim Configuration"), this.width / 2, 12, 0xFFFFFF);

        // Dynamic warning about heavy settings.
        String warn = warning();
        if (warn != null) {
            g.centeredText(font, Component.literal(warn), this.width / 2, warnY, 0xFFD24A);
        }

        g.centeredText(font, Component.literal("§6§l── Ores to Find ──"), this.width / 2, oresHeaderY, 0xFFAA00);

        super.extractRenderState(g, mx, my, delta);
    }

    /** Returns a warning string if the current settings may load slowly or cause lag, else null. */
    private String warning() {
        boolean bigRange = config.clampedRadius() >= 10;
        boolean bigMax   = config.maxResults >= 5000;
        if (bigRange && bigMax) return "§e⚠ High range + high box cap: slow to load and may lag.";
        if (bigRange) return "§e⚠ High chunk range: predictions take longer to fill in.";
        if (bigMax)   return "§e⚠ High box cap: many boxes can cause lag.";
        return null;
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    public void onClose() {
        ConfigManager.save();
        OrePredictor.clearCache();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
