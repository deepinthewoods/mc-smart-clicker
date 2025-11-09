package ninja.trek.smartclicker.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractSliderButton;
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
            // Add all buttons to the screen
            this.addRenderableWidget(row.removeButton);
            this.addRenderableWidget(row.upButton);
            this.addRenderableWidget(row.downButton);
            this.addRenderableWidget(row.addButton);
            this.addRenderableWidget(row.paramField);
            this.addRenderableWidget(row.delaySlider);
            y += ROW_HEIGHT;
        }
    }

    private void rebuildCommandList() {
        // Remove old command row widgets
        for (CommandRow row : commandRows) {
            this.removeWidget(row.removeButton);
            this.removeWidget(row.upButton);
            this.removeWidget(row.downButton);
            this.removeWidget(row.addButton);
            this.removeWidget(row.paramField);
            this.removeWidget(row.delaySlider);
        }
        commandRows.clear();
        buildCommandList();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);
        super.render(graphics, mouseX, mouseY, delta);

        // Draw title
        graphics.drawString(this.font, "Edit Script: " + script.getName(), this.width / 2 - 100, 15, 0xFFFFFF);

        // Draw section labels
        graphics.drawString(this.font, "Commands:", COMMAND_BUTTONS_X, 55, 0xFFFFFF);
        graphics.drawString(this.font, "Available:", this.width - 120, 55, 0xFFFFFF);

        // Draw command rows (just the command names and delay controls)
        for (CommandRow row : commandRows) {
            row.render(graphics, mouseX, mouseY);
        }
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
        public final Button removeButton;
        public final Button upButton;
        public final Button downButton;
        public final Button addButton;
        public final AbstractSliderButton delaySlider;
        public final EditBox paramField;

        public CommandRow(CommandInstruction instruction, int index, int y) {
            this.instruction = instruction;
            this.index = index;
            this.y = y;

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
            this.paramField = new EditBox(ScriptEditorScreen.this.font, x, y + 2, 80, 16, Component.literal("Param"));
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

            // Delay slider
            int delayX = COMMAND_BUTTONS_X + 280;
            this.delaySlider = new AbstractSliderButton(delayX, y, 80, 20,
                Component.literal("Delay: " + instruction.getPostDelay() + "t"),
                (instruction.getPostDelay() - 1) / 49.0) {

                @Override
                protected void updateMessage() {
                    int delay = (int) Math.round(this.value * 49) + 1;
                    this.setMessage(Component.literal("Delay: " + delay + "t"));
                }

                @Override
                protected void applyValue() {
                    int delay = (int) Math.round(this.value * 49) + 1;
                    instruction.setPostDelay(delay);
                    SmartClickerClient.getScriptManager().saveScript(script);
                }
            };

            // Add button [+]
            this.addButton = Button.builder(Component.literal("+"), button -> {
                CommandInstruction newInstruction = new CommandInstruction(CommandType.LEFT_CLICK, "", 1);
                script.getInstructions().add(index + 1, newInstruction);
                SmartClickerClient.getScriptManager().saveScript(script);
                rebuildCommandList();
            }).bounds(width - 250, y, 20, 20).build();
        }

        public void render(GuiGraphics graphics, int mouseX, int mouseY) {
            // Draw command type name
            graphics.drawString(ScriptEditorScreen.this.font, instruction.getType().getDisplayName(),
                COMMAND_BUTTONS_X + 68, y + 6, 0xFFFFFF);

            // Draw parameter label
            if (instruction.getType().hasParameter()) {
                graphics.drawString(ScriptEditorScreen.this.font, "Param:",
                    COMMAND_BUTTONS_X + 160, y + 6, 0xFFFFFF);
            }
        }
    }
}
