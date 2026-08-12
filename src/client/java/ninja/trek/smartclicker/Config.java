package ninja.trek.smartclicker;

import com.google.gson.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configFile;
    private boolean useRealWorldTiming = false;

    public Config(Path configDir) {
        Path smartClickerDir = configDir.resolve("smart-clicker");
        try {
            Files.createDirectories(smartClickerDir);
        } catch (IOException e) {
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

        } catch (Exception e) {
        }
    }

    private void save() {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("useRealWorldTiming", useRealWorldTiming);

            String json = GSON.toJson(obj);
            Files.writeString(configFile, json);
        } catch (IOException e) {
        }
    }
}
