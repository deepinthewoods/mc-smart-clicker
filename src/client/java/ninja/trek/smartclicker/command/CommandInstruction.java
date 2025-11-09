package ninja.trek.smartclicker.command;

import com.google.gson.*;
import java.lang.reflect.Type;

public class CommandInstruction {
    private CommandType type;
    private String parameter;
    private int postDelay; // in game ticks (1-50)

    public CommandInstruction(CommandType type, String parameter, int postDelay) {
        this.type = type;
        this.parameter = parameter;
        this.postDelay = Math.max(1, Math.min(50, postDelay));
    }

    public CommandType getType() {
        return type;
    }

    public void setType(CommandType type) {
        this.type = type;
    }

    public String getParameter() {
        return parameter;
    }

    public void setParameter(String parameter) {
        this.parameter = parameter;
    }

    public int getPostDelay() {
        return postDelay;
    }

    public void setPostDelay(int postDelay) {
        this.postDelay = Math.max(1, Math.min(50, postDelay));
    }

    // GSON serializer/deserializer
    public static class Serializer implements JsonSerializer<CommandInstruction>, JsonDeserializer<CommandInstruction> {
        @Override
        public JsonElement serialize(CommandInstruction src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("type", src.type.name());
            obj.addProperty("parameter", src.parameter);
            obj.addProperty("postDelay", src.postDelay);
            return obj;
        }

        @Override
        public CommandInstruction deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            CommandType type = CommandType.valueOf(obj.get("type").getAsString());
            String parameter = obj.get("parameter").getAsString();
            int postDelay = obj.get("postDelay").getAsInt();
            return new CommandInstruction(type, parameter, postDelay);
        }
    }
}
