package ninja.trek.smartclicker;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    private static final Logger LOGGER = LoggerFactory.getLogger(Config.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configFile;
    private boolean useRealWorldTiming = false;

    public Config(Path configDir) {
        Path smartClickerDir = configDir.resolve("smart-clicker");
        try {
            Files.createDirectories(smartClickerDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create smart-clicker config directory", e);
        }
        this.configFile = smartClickerDir.resolve("config.json");
        load();
    }

    public boolean isUseRealWorldTiming() {
        return useRealWorldTiming;
    }

    public void setUseRealWorldTiming(boolean useRealWorldTiming) {
        this.useRealWorldTiming = useRealWorldTiming;
        save();
    }

    private void load() {
        if (!Files.exists(configFile)) {
            save(); // Create default config
            return;
        }

        try {
            String json = Files.readString(configFile);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            if (obj.has("useRealWorldTiming")) {
                useRealWorldTiming = obj.get("useRealWorldTiming").getAsBoolean();
            }

            LOGGER.info("Loaded config: useRealWorldTiming={}", useRealWorldTiming);
        } catch (Exception e) {
            LOGGER.error("Failed to load config, using defaults", e);
        }
    }

    private void save() {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("useRealWorldTiming", useRealWorldTiming);

            String json = GSON.toJson(obj);
            Files.writeString(configFile, json);
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }
}
