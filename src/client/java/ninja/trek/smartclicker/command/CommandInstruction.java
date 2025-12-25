package ninja.trek.smartclicker.command;

import com.google.gson.*;
import java.lang.reflect.Type;

public class CommandInstruction {
    private CommandType type;
    private String parameter;
    private int postDelay; // in game ticks (minimum 1, no maximum)
    private int amount; // trade count for BUY/SELL; 0 = as many as possible

    public CommandInstruction(CommandType type, String parameter, int postDelay) {
        this(type, parameter, postDelay, getDefaultAmount(type));
    }

    public CommandInstruction(CommandType type, String parameter, int postDelay, int amount) {
        this.type = type;
        this.parameter = parameter;
        this.postDelay = Math.max(1, postDelay);
        setAmount(amount);
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
        this.postDelay = Math.max(1, postDelay);
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = Math.max(0, Math.min(9999, amount));
    }

    public boolean hasAmountSetting() {
        return type == CommandType.BUY || type == CommandType.SELL;
    }

    private static int getDefaultAmount(CommandType type) {
        return (type == CommandType.BUY || type == CommandType.SELL) ? 0 : 1;
    }

    // GSON serializer/deserializer
    public static class Serializer implements JsonSerializer<CommandInstruction>, JsonDeserializer<CommandInstruction> {
        @Override
        public JsonElement serialize(CommandInstruction src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("type", src.type.name());
            obj.addProperty("parameter", src.parameter);
            obj.addProperty("postDelay", src.postDelay);
            obj.addProperty("amount", src.amount);
            return obj;
        }

        @Override
        public CommandInstruction deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            CommandType type = CommandType.valueOf(obj.get("type").getAsString());
            String parameter = obj.get("parameter").getAsString();
            int postDelay = obj.get("postDelay").getAsInt();
            int amount = obj.has("amount") ? obj.get("amount").getAsInt() : getDefaultAmount(type);
            return new CommandInstruction(type, parameter, postDelay, amount);
        }
    }
}
