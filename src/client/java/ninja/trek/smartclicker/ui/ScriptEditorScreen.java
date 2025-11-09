package ninja.trek.smartclicker.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import ninja.trek.smartclicker.SmartClickerClient;
import ninja.trek.smartclicker.command.CommandInstruction;
import ninja.trek.smartclicker.command.CommandType;
import ninja.trek.smartclicker.script.Script;

import java.util.ArrayList;
import java.util.List;

public class ScriptEditorScreen extends Screen {
    private final Screen parent;
    private final Script script;
    private final List<CommandRow> commandRows = new ArrayList<>();
    private int scrollOffset = 0;

    private static final int ROW_HEIGHT = 25;
    private static final int LIST_TOP = 70;
    private static final int COMMAND_BUTTONS_X = 10;

    private EditBox nameField;

    public ScriptEditorScreen(Screen parent, Script script) {
        super(Component.literal("Edit Script"));
        this.parent = parent;
        this.script = script;
    }

    @Override
    protected void init() {
        super.init();

        // Name field at top
        nameField = new EditBox(this.font, this.width / 2 - 100, 30, 150, 20, Component.literal("Script Name"));
        nameField.setMaxLength(50);
        nameField.setValue(script.getName());
        this.addRenderableWidget(nameField);

        // Rename button
        this.addRenderableWidget(Button.builder(Component.literal("Rename"), button -> {
            script.setName(nameField.getValue());
            SmartClickerClient.getScriptManager().saveScript(script);
        }).bounds(this.width / 2 + 55, 30, 60, 20).build());

        // Delete button
        this.addRenderableWidget(Button.builder(Component.literal("Delete"), button -> {
            SmartClickerClient.getScriptManager().deleteScript(script);
            onClose();
        }).bounds(this.width / 2 + 120, 30, 60, 20).build());

        // Back button
        this.addRenderableWidget(Button.builder(Component.literal("Back"), button -> {
            onClose();
        }).bounds(10, 10, 60, 20).build());

        // Command palette on the right
        buildCommandPalette();

        // Build command list
        buildCommandList();
    }

    private void buildCommandPalette() {
        int x = this.width - 120;
        int y = LIST_TOP;

        for (CommandType type : CommandType.values()) {
            this.addRenderableWidget(Button.builder(Component.literal(type.getDisplayName()), button -> {
                CommandInstruction newInstruction = new CommandInstruction(type, type.getDefaultParameter(), 1);
                script.addInstruction(newInstruction);
                SmartClickerClient.getScriptManager().saveScript(script);
                rebuildCommandList();
            }).bounds(x, y, 110, 20).build());
            y += 22;
        }
    }

    private void buildCommandList() {
        commandRows.clear();
        int y = LIST_TOP;

        for (int i = 0; i < script.getInstructions().size(); i++) {
            CommandInstruction instruction = script.getInstructions().get(i);
            CommandRow row = new CommandRow(instruction, i, y);
            commandRows.add(row);
            y += ROW_HEIGHT;
        }
    }

    private void rebuildCommandList() {
        // Remove old command row widgets
        commandRows.clear();
        buildCommandList();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        // Draw title
        graphics.drawString(this.font, "Edit Script: " + script.getName(), this.width / 2 - 100, 15, 0xFFFFFF);

        // Draw section labels
        graphics.drawString(this.font, "Commands:", COMMAND_BUTTONS_X, 55, 0xFFFFFF);
        graphics.drawString(this.font, "Available:", this.width - 120, 55, 0xFFFFFF);

        // Draw command rows
        for (CommandRow row : commandRows) {
            row.render(graphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check if any command row was clicked
        for (CommandRow row : commandRows) {
            if (row.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        SmartClickerClient.getScriptManager().saveScript(script);
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private class CommandRow {
        private final CommandInstruction instruction;
        private final int index;
        private final int y;
        private final Button removeButton;
        private final Button upButton;
        private final Button downButton;
        private final Button addButton;
        private final EditBox paramField;
        private int delayValue;

        public CommandRow(CommandInstruction instruction, int index, int y) {
            this.instruction = instruction;
            this.index = index;
            this.y = y;
            this.delayValue = instruction.getPostDelay();

            int x = COMMAND_BUTTONS_X;

            // Remove button [-]
            this.removeButton = Button.builder(Component.literal("-"), button -> {
                script.removeInstruction(index);
                SmartClickerClient.getScriptManager().saveScript(script);
                rebuildCommandList();
            }).bounds(x, y, 20, 20).build();
            x += 22;

            // Up button [↑]
            this.upButton = Button.builder(Component.literal("↑"), button -> {
                script.moveInstructionUp(index);
                SmartClickerClient.getScriptManager().saveScript(script);
                rebuildCommandList();
            }).bounds(x, y, 20, 20).build();
            x += 22;

            // Down button [↓]
            this.downButton = Button.builder(Component.literal("↓"), button -> {
                script.moveInstructionDown(index);
                SmartClickerClient.getScriptManager().saveScript(script);
                rebuildCommandList();
            }).bounds(x, y, 20, 20).build();
            x += 22;

            // Command type display (not editable, just shows name)
            x += 2;
            // We'll draw this as text in render()

            // Parameter field
            x += 100; // Space for command name
            this.paramField = new EditBox(font, x, y + 2, 80, 16, Component.literal("Param"));
            this.paramField.setMaxLength(20);
            this.paramField.setValue(instruction.getParameter());
            this.paramField.setResponder(text -> {
                instruction.setParameter(text);
                SmartClickerClient.getScriptManager().saveScript(script);
            });
            if (!instruction.getType().hasParameter()) {
                this.paramField.setEditable(false);
                this.paramField.setValue("");
            }
            x += 82;

            // Delay slider representation (we'll use +/- buttons for simplicity)
            // Show current delay value and +/- buttons
            x += 5;

            // Add button [+]
            this.addButton = Button.builder(Component.literal("+"), button -> {
                CommandInstruction newInstruction = new CommandInstruction(CommandType.LEFT_CLICK, "", 1);
                script.getInstructions().add(index + 1, newInstruction);
                SmartClickerClient.getScriptManager().saveScript(script);
                rebuildCommandList();
            }).bounds(width - 250, y, 20, 20).build();
        }

        public void render(GuiGraphics graphics, int mouseX, int mouseY) {
            removeButton.render(graphics, mouseX, mouseY, 0);
            upButton.render(graphics, mouseX, mouseY, 0);
            downButton.render(graphics, mouseX, mouseY, 0);

            // Draw command type name
            graphics.drawString(font, instruction.getType().getDisplayName(),
                COMMAND_BUTTONS_X + 68, y + 6, 0xFFFFFF);

            // Draw parameter field
            paramField.render(graphics, mouseX, mouseY, 0);

            // Draw delay value and controls
            int delayX = COMMAND_BUTTONS_X + 252;
            graphics.drawString(font, "Delay: " + instruction.getPostDelay() + "t",
                delayX, y + 6, 0xFFFFFF);

            // Delay decrease button
            if (mouseX >= delayX + 60 && mouseX <= delayX + 75 &&
                mouseY >= y && mouseY <= y + 20) {
                graphics.fill(delayX + 60, y, delayX + 75, y + 20, 0x80FFFFFF);
            }
            graphics.drawString(font, "-", delayX + 65, y + 6, 0xFFFFFF);

            // Delay increase button
            if (mouseX >= delayX + 77 && mouseX <= delayX + 92 &&
                mouseY >= y && mouseY <= y + 20) {
                graphics.fill(delayX + 77, y, delayX + 92, y + 20, 0x80FFFFFF);
            }
            graphics.drawString(font, "+", delayX + 82, y + 6, 0xFFFFFF);

            addButton.render(graphics, mouseX, mouseY, 0);
        }

        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (removeButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (upButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (downButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (addButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (paramField.mouseClicked(mouseX, mouseY, button)) return true;

            // Check delay buttons
            int delayX = COMMAND_BUTTONS_X + 252;
            if (mouseX >= delayX + 60 && mouseX <= delayX + 75 &&
                mouseY >= y && mouseY <= y + 20) {
                // Decrease delay
                if (instruction.getPostDelay() > 1) {
                    instruction.setPostDelay(instruction.getPostDelay() - 1);
                    SmartClickerClient.getScriptManager().saveScript(script);
                }
                return true;
            }
            if (mouseX >= delayX + 77 && mouseX <= delayX + 92 &&
                mouseY >= y && mouseY <= y + 20) {
                // Increase delay
                if (instruction.getPostDelay() < 50) {
                    instruction.setPostDelay(instruction.getPostDelay() + 1);
                    SmartClickerClient.getScriptManager().saveScript(script);
                }
                return true;
            }

            return false;
        }
    }
}
