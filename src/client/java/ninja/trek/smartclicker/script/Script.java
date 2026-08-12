package ninja.trek.smartclicker.script;

import ninja.trek.smartclicker.command.CommandInstruction;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Script {
    private String id;
    private String name;
    private List<CommandInstruction> instructions;
    private boolean replaceTools;
    private int replaceToolsThreshold;

    public Script(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.instructions = new ArrayList<>();
        this.replaceTools = false;
        this.replaceToolsThreshold = 10;
    }

    // Constructor for deserialization
    public Script(String id, String name, List<CommandInstruction> instructions) {
        this.id = id;
        this.name = name;
        this.instructions = instructions;
        this.replaceTools = false;
        this.replaceToolsThreshold = 10;
    }

    // Constructor for deserialization with replaceTools
    public Script(String id, String name, List<CommandInstruction> instructions, boolean replaceTools) {
        this.id = id;
        this.name = name;
        this.instructions = instructions;
        this.replaceTools = replaceTools;
        this.replaceToolsThreshold = 10;
    }

    // Constructor for deserialization with all fields
    public Script(String id, String name, List<CommandInstruction> instructions, boolean replaceTools, int replaceToolsThreshold) {
        this.id = id;
        this.name = name;
        this.instructions = instructions;
        this.replaceTools = replaceTools;
        this.replaceToolsThreshold = replaceToolsThreshold;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<CommandInstruction> getInstructions() {
        return instructions;
    }

    public void setInstructions(List<CommandInstruction> instructions) {
        this.instructions = instructions;
    }

    public void addInstruction(CommandInstruction instruction) {
        instructions.add(instruction);
    }

    public void removeInstruction(int index) {
        if (index >= 0 && index < instructions.size()) {
            instructions.remove(index);
        }
    }

    public void moveInstructionUp(int index) {
        if (index > 0 && index < instructions.size()) {
            CommandInstruction temp = instructions.get(index);
            instructions.set(index, instructions.get(index - 1));
            instructions.set(index - 1, temp);
        }
    }

    public void moveInstructionDown(int index) {
        if (index >= 0 && index < instructions.size() - 1) {
            CommandInstruction temp = instructions.get(index);
            instructions.set(index, instructions.get(index + 1));
            instructions.set(index + 1, temp);
        }
    }

    public boolean isReplaceTools() {
        return replaceTools;
    }

    public void setReplaceTools(boolean replaceTools) {
        this.replaceTools = replaceTools;
    }

    public int getReplaceToolsThreshold() {
        return replaceToolsThreshold;
    }

    public void setReplaceToolsThreshold(int replaceToolsThreshold) {
        this.replaceToolsThreshold = replaceToolsThreshold;
    }
}
