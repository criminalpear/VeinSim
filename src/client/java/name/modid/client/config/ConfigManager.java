package name.modid.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import name.modid.VeinSim;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static VeinSimConfig config = new VeinSimConfig();

    public static VeinSimConfig getConfig() {
        return config;
    }

    public static void load() {
        File file = getPath().toFile();
        if (!file.exists()) {
            save();
            return;
        }
        try (FileReader reader = new FileReader(file)) {
            VeinSimConfig loaded = GSON.fromJson(reader, VeinSimConfig.class);
            if (loaded != null) config = loaded;
        } catch (IOException e) {
            VeinSim.LOGGER.error("[VeinSim] Failed to load config", e);
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(getPath().toFile())) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            VeinSim.LOGGER.error("[VeinSim] Failed to save config", e);
        }
    }

    private static Path getPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("vein-sim.json");
    }
}
