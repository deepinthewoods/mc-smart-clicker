package ninja.trek.smartclicker.script;

import com.google.gson.*;
import ninja.trek.smartclicker.command.CommandInstruction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ScriptManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptManager.class);
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(CommandInstruction.class, new CommandInstruction.Serializer())
            .create();

    private final Path scriptsDir;
    private final List<Script> scripts = new ArrayList<>();

    public ScriptManager(Path configDir) {
        this.scriptsDir = configDir.resolve("smart-clicker").resolve("scripts");
        try {
            Files.createDirectories(scriptsDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create scripts directory", e);
        }
        loadScripts();
    }

    public void loadScripts() {
        scripts.clear();
        try {
            if (!Files.exists(scriptsDir)) {
                return;
            }

            Files.list(scriptsDir)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            String json = Files.readString(path);
                            Script script = deserializeScript(json);
                            if (script != null) {
                                scripts.add(script);
                            }
                        } catch (IOException e) {
                            LOGGER.error("Failed to load script: " + path, e);
                        }
                    });
            LOGGER.info("Loaded {} scripts", scripts.size());
        } catch (IOException e) {
            LOGGER.error("Failed to list scripts directory", e);
        }
    }

    public void saveScript(Script script) {
        try {
            String json = serializeScript(script);
            Path scriptPath = scriptsDir.resolve(script.getId() + ".json");
            Files.writeString(scriptPath, json);

            if (!scripts.contains(script)) {
                scripts.add(script);
            }
            LOGGER.info("Saved script: {}", script.getName());
        } catch (IOException e) {
            LOGGER.error("Failed to save script: " + script.getName(), e);
        }
    }

    public void deleteScript(Script script) {
        try {
            Path scriptPath = scriptsDir.resolve(script.getId() + ".json");
            Files.deleteIfExists(scriptPath);
            scripts.remove(script);
            LOGGER.info("Deleted script: {}", script.getName());
        } catch (IOException e) {
            LOGGER.error("Failed to delete script: " + script.getName(), e);
        }
    }

    public List<Script> getScripts() {
        return new ArrayList<>(scripts);
    }

    private String serializeScript(Script script) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", script.getId());
        obj.addProperty("name", script.getName());

        JsonArray instructions = new JsonArray();
        for (CommandInstruction instruction : script.getInstructions()) {
            instructions.add(GSON.toJsonTree(instruction));
        }
        obj.add("instructions", instructions);

        return GSON.toJson(obj);
    }

    private Script deserializeScript(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            String id = obj.get("id").getAsString();
            String name = obj.get("name").getAsString();

            List<CommandInstruction> instructions = new ArrayList<>();
            JsonArray instructionsArray = obj.getAsJsonArray("instructions");
            for (JsonElement element : instructionsArray) {
                instructions.add(GSON.fromJson(element, CommandInstruction.class));
            }

            return new Script(id, name, instructions);
        } catch (Exception e) {
            LOGGER.error("Failed to deserialize script", e);
            return null;
        }
    }
}
