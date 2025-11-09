package ninja.trek.smartclicker.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
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

    private TextFieldWidget nameField;

    public ScriptEditorScreen(Screen parent, Script script) {
        super(Text.literal("Edit Script"));
        this.parent = parent;
        this.script = script;
    }

    @Override
    protected void init() {
        super.init();

        // Name field at top
        nameField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, 30, 150, 20, Text.literal("Script Name"));
        nameField.setMaxLength(50);
        nameField.setText(script.getName());
        this.addDrawableChild(nameField);

        // Rename button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Rename"), button -> {
            script.setName(nameField.getText());
            SmartClickerClient.getScriptManager().saveScript(script);
        }).dimensions(this.width / 2 + 55, 30, 60, 20).build());

        // Delete button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Delete"), button -> {
            SmartClickerClient.getScriptManager().deleteScript(script);
            close();
        }).dimensions(this.width / 2 + 120, 30, 60, 20).build());

        // Back button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> {
            close();
        }).dimensions(10, 10, 60, 20).build());

        // Command palette on the right
        buildCommandPalette();

        // Build command list
        buildCommandList();
    }

    private void buildCommandPalette() {
        int x = this.width - 120;
        int y = LIST_TOP;

        for (CommandType type : CommandType.values()) {
            this.addDrawableChild(ButtonWidget.builder(Text.literal(type.getDisplayName()), button -> {
                CommandInstruction newInstruction = new CommandInstruction(type, type.getDefaultParameter(), 1);
                script.addInstruction(newInstruction);
                SmartClickerClient.getScriptManager().saveScript(script);
                rebuildCommandList();
            }).dimensions(x, y, 110, 20).build());
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Draw title
        context.drawTextWithShadow(this.textRenderer, "Edit Script: " + script.getName(), this.width / 2 - 100, 15, 0xFFFFFF);

        // Draw section labels
        context.drawTextWithShadow(this.textRenderer, "Commands:", COMMAND_BUTTONS_X, 55, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "Available:", this.width - 120, 55, 0xFFFFFF);

        // Draw command rows
        for (CommandRow row : commandRows) {
            row.render(context, mouseX, mouseY);
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
    public void close() {
        SmartClickerClient.getScriptManager().saveScript(script);
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private class CommandRow {
        private final CommandInstruction instruction;
        private final int index;
        private final int y;
        private final ButtonWidget removeButton;
        private final ButtonWidget upButton;
        private final ButtonWidget downButton;
        private final ButtonWidget addButton;
        private final TextFieldWidget paramField;
        private int delayValue;

        public CommandRow(CommandInstruction instruction, int index, int y) {
            this.instruction = instruction;
            this.index = index;
            this.y = y;
            this.delayValue = instruction.getPostDelay();

            int x = COMMAND_BUTTONS_X;

            // Remove button [-]
            this.removeButton = ButtonWidget.builder(Text.literal("-"), button -> {
                script.removeInstruction(index);
                SmartClickerClient.getScriptManager().saveScript(script);
                rebuildCommandList();
            }).dimensions(x, y, 20, 20).build();
            x += 22;

            // Up button [↑]
            this.upButton = ButtonWidget.builder(Text.literal("↑"), button -> {
                script.moveInstructionUp(index);
                SmartClickerClient.getScriptManager().saveScript(script);
                rebuildCommandList();
            }).dimensions(x, y, 20, 20).build();
            x += 22;

            // Down button [↓]
            this.downButton = ButtonWidget.builder(Text.literal("↓"), button -> {
                script.moveInstructionDown(index);
                SmartClickerClient.getScriptManager().saveScript(script);
                rebuildCommandList();
            }).dimensions(x, y, 20, 20).build();
            x += 22;

            // Command type display (not editable, just shows name)
            x += 2;
            // We'll draw this as text in render()

            // Parameter field
            x += 100; // Space for command name
            this.paramField = new TextFieldWidget(textRenderer, x, y + 2, 80, 16, Text.literal("Param"));
            this.paramField.setMaxLength(20);
            this.paramField.setText(instruction.getParameter());
            this.paramField.setChangedListener(text -> {
                instruction.setParameter(text);
                SmartClickerClient.getScriptManager().saveScript(script);
            });
            if (!instruction.getType().hasParameter()) {
                this.paramField.setEditable(false);
                this.paramField.setText("");
            }
            x += 82;

            // Delay slider representation (we'll use +/- buttons for simplicity)
            // Show current delay value and +/- buttons
            x += 5;

            // Add button [+]
            this.addButton = ButtonWidget.builder(Text.literal("+"), button -> {
                CommandInstruction newInstruction = new CommandInstruction(CommandType.LEFT_CLICK, "", 1);
                script.getInstructions().add(index + 1, newInstruction);
                SmartClickerClient.getScriptManager().saveScript(script);
                rebuildCommandList();
            }).dimensions(width - 250, y, 20, 20).build();
        }

        public void render(DrawContext context, int mouseX, int mouseY) {
            removeButton.render(context, mouseX, mouseY, 0);
            upButton.render(context, mouseX, mouseY, 0);
            downButton.render(context, mouseX, mouseY, 0);

            // Draw command type name
            context.drawTextWithShadow(textRenderer, instruction.getType().getDisplayName(),
                COMMAND_BUTTONS_X + 68, y + 6, 0xFFFFFF);

            // Draw parameter field
            paramField.render(context, mouseX, mouseY, 0);

            // Draw delay value and controls
            int delayX = COMMAND_BUTTONS_X + 252;
            context.drawTextWithShadow(textRenderer, "Delay: " + instruction.getPostDelay() + "t",
                delayX, y + 6, 0xFFFFFF);

            // Delay decrease button
            if (mouseX >= delayX + 60 && mouseX <= delayX + 75 &&
                mouseY >= y && mouseY <= y + 20) {
                context.fill(delayX + 60, y, delayX + 75, y + 20, 0x80FFFFFF);
            }
            context.drawTextWithShadow(textRenderer, "-", delayX + 65, y + 6, 0xFFFFFF);

            // Delay increase button
            if (mouseX >= delayX + 77 && mouseX <= delayX + 92 &&
                mouseY >= y && mouseY <= y + 20) {
                context.fill(delayX + 77, y, delayX + 92, y + 20, 0x80FFFFFF);
            }
            context.drawTextWithShadow(textRenderer, "+", delayX + 82, y + 6, 0xFFFFFF);

            addButton.render(context, mouseX, mouseY, 0);
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
