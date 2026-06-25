package name.modid.client.config;

import name.modid.client.ore.OreType;
import java.util.HashMap;
import java.util.Map;

public class VeinSimConfig {
    public long worldSeed = 0;
    public boolean toggleMode = true;
    public int activationKeyCode = 88; // GLFW_KEY_X

    /** Search radius in chunks (1..MAX_SEARCH_RADIUS). Larger = wider scan, fills in slower. */
    public int searchRadiusChunks = 4;

    public static final int MAX_SEARCH_RADIUS = 16;

    public int clampedRadius() {
        return Math.max(1, Math.min(MAX_SEARCH_RADIUS, searchRadiusChunks));
    }

    /** Max number of ore boxes shown at once (nearest first). Caps render load. */
    public int maxResults = 2000;

    /** Preset values the UI cycles through. */
    public static final int[] MAX_RESULT_PRESETS = { 250, 500, 1000, 2000, 5000, 10000, 25000 };

    /** When true, show only the single closest vein of each selected ore type. */
    public boolean nearestVeinOnly = false;

    /** When true, print "[VeinSim] Enabled/Disabled" in chat as the overlay turns on/off. */
    public boolean statusMessages = true;

    // keyed by OreType.configKey()
    public Map<String, Boolean> oreStates = new HashMap<>();

    public boolean isOreEnabled(OreType type) {
        return oreStates.getOrDefault(type.configKey(), true);
    }

    public void setOreEnabled(OreType type, boolean enabled) {
        oreStates.put(type.configKey(), enabled);
    }
}
