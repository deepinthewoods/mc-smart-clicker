package ninja.trek.smartclicker.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import ninja.trek.smartclicker.SmartClickerClient;
import ninja.trek.smartclicker.command.CommandInstruction;
import ninja.trek.smartclicker.command.CommandType;
import ninja.trek.smartclicker.script.Script;
import ninja.trek.smartclicker.recording.RecordingManager;

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
    private boolean recordMouseMovements = false;
    private Button recordMouseToggle;
    private Button replaceToolsToggle;
    private EditBox replaceToolsThresholdField;
    private static List<String> ALL_ITEM_IDS;

    public ScriptEditorScreen(Screen parent, Script script) {
        super(Component.literal("Edit Script"));
        this.parent = parent;
        this.script = script;
    }

    @Override
    protected void init() {
        super.init();

        if (ALL_ITEM_IDS == null) {
            ALL_ITEM_IDS = BuiltInRegistries.ITEM.keySet().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .toList();
        }

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

        // Record Mouse Movements toggle button
        this.recordMouseToggle = Button.builder(
            Component.literal("Mouse: " + (recordMouseMovements ? "ON" : "OFF")),
            button -> {
                recordMouseMovements = !recordMouseMovements;
                button.setMessage(Component.literal("Mouse: " + (recordMouseMovements ? "ON" : "OFF")));
            }
        ).bounds(this.width / 2 + 185, 10, 80, 20).build();
        this.addRenderableWidget(recordMouseToggle);

        // Replace Tools toggle button
        this.replaceToolsToggle = Button.builder(
            Component.literal("Replace: " + (script.isReplaceTools() ? "ON" : "OFF")),
            button -> {
                script.setReplaceTools(!script.isReplaceTools());
                button.setMessage(Component.literal("Replace: " + (script.isReplaceTools() ? "ON" : "OFF")));
                SmartClickerClient.getScriptManager().saveScript(script);
            }
        ).bounds(this.width / 2 + 185, 33, 60, 20).build();
        this.addRenderableWidget(replaceToolsToggle);

        // Replace Tools threshold field
        this.replaceToolsThresholdField = new EditBox(this.font, this.width / 2 + 247, 35, 35, 16, Component.literal("Threshold"));
        this.replaceToolsThresholdField.setMaxLength(3);
        this.replaceToolsThresholdField.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        this.replaceToolsThresholdField.setValue(Integer.toString(script.getReplaceToolsThreshold()));
        this.replaceToolsThresholdField.setResponder(text -> {
            int threshold = text == null || text.isEmpty() ? 0 : Integer.parseInt(text);
            script.setReplaceToolsThreshold(threshold);
            SmartClickerClient.getScriptManager().saveScript(script);
        });
        this.addRenderableWidget(replaceToolsThresholdField);

        // Record button
        this.addRenderableWidget(Button.builder(Component.literal("Record"), button -> {
            RecordingManager recordingManager = SmartClickerClient.getRecordingManager();
            recordingManager.startRecording(script, recordMouseMovements);
            onClose(); // Close the UI so player can perform actions
        }).bounds(this.width / 2 + 270, 10, 60, 20).build());

        // Back button
        this.addRenderableWidget(Button.builder(Component.literal("Back"), button -> {
            onClose();
        }).bounds(10, 10, 60, 20).build());

        // Section labels (non-clickable)
        Button commandsLabel = Button.builder(Component.literal("Commands:"), button -> {})
            .bounds(COMMAND_BUTTONS_X, 55, 80, 12).build();
        commandsLabel.active = false;
        this.addRenderableWidget(commandsLabel);

        Button availableLabel = Button.builder(Component.literal("Available:"), button -> {})
            .bounds(this.width - 120, 55, 80, 12).build();
        availableLabel.active = false;
        this.addRenderableWidget(availableLabel);

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
            this.addRenderableWidget(row.commandTypeLabel);
            if (row.paramLabel != null) {
                this.addRenderableWidget(row.paramLabel);
            }
            this.addRenderableWidget(row.paramField);
            if (row.amountLabel != null) {
                this.addRenderableWidget(row.amountLabel);
            }
            if (row.amountField != null) {
                this.addRenderableWidget(row.amountField);
            }
            this.addRenderableWidget(row.delaySlider);
            this.addRenderableWidget(row.setDelayButton);
            y += ROW_HEIGHT;
        }
    }

    private void rebuildCommandList() {
        // Remove old command row widgets
        for (CommandRow row : commandRows) {
            this.removeWidget(row.removeButton);
            this.removeWidget(row.upButton);
            this.removeWidget(row.downButton);
            this.removeWidget(row.commandTypeLabel);
            if (row.paramLabel != null) {
                this.removeWidget(row.paramLabel);
            }
            this.removeWidget(row.paramField);
            if (row.amountLabel != null) {
                this.removeWidget(row.amountLabel);
            }
            if (row.amountField != null) {
                this.removeWidget(row.amountField);
            }
            this.removeWidget(row.delaySlider);
            this.removeWidget(row.setDelayButton);
        }
        commandRows.clear();
        buildCommandList();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Render a simple semi-transparent background without blur to avoid the "Can only blur once per frame" error
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (keyEvent.isCycleFocus() && !keyEvent.hasShiftDown()) {
            for (CommandRow row : commandRows) {
                if (row.canApplyAutocomplete()) {
                    row.applyAutocomplete();
                    return true;
                }
            }
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public void onClose() {
        SmartClickerClient.getScriptManager().saveScript(script);
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private class CustomSlider extends AbstractSliderButton {
        private final CommandInstruction instruction;

        public CustomSlider(int x, int y, int width, int height, Component message, double value, CommandInstruction instruction) {
            super(x, y, width, height, message, value);
            this.instruction = instruction;
        }

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

        public void setSliderValue(double newValue) {
            this.value = newValue;
            this.updateMessage();
        }
    }

    private class CommandRow {
        private final CommandInstruction instruction;
        private final int index;
        private final int y;
        public final Button removeButton;
        public final Button upButton;
        public final Button downButton;
        public final Button commandTypeLabel;
        public final Button paramLabel;
        public final Button amountLabel;
        public final CustomSlider delaySlider;
        public final Button setDelayButton;
        public final EditBox paramField;
        public final EditBox amountField;
        private String autocompleteTarget;

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

            // Command type display label (non-clickable)
            x += 2;
            this.commandTypeLabel = Button.builder(Component.literal(instruction.getType().getDisplayName()), button -> {})
                .bounds(x, y, 100, 20).build();
            this.commandTypeLabel.active = false;
            x += 102;

            // Parameter label (non-clickable)
            if (instruction.getType().hasParameter()) {
                this.paramLabel = Button.builder(Component.literal("Param:"), button -> {})
                    .bounds(x, y, 40, 20).build();
                this.paramLabel.active = false;
                x += 42;
            } else {
                this.paramLabel = null;
                x += 42;
            }

            // Parameter field
            this.paramField = new EditBox(ScriptEditorScreen.this.font, x, y + 2, 80, 16, Component.literal("Param"));
            this.paramField.setMaxLength(isItemIdParam(instruction.getType()) ? 128 : 20);
            this.paramField.setValue(instruction.getParameter());
            this.paramField.setResponder(text -> {
                instruction.setParameter(text);
                SmartClickerClient.getScriptManager().saveScript(script);
                updateAutocomplete();
            });
            if (!instruction.getType().hasParameter()) {
                this.paramField.setEditable(false);
                this.paramField.setValue("");
            }
            x += 82;

            updateAutocomplete();

            // Amount field (BUY/SELL only)
            if (instruction.getType() == CommandType.BUY || instruction.getType() == CommandType.SELL) {
                this.amountLabel = Button.builder(Component.literal("Amt:"), button -> {})
                    .bounds(x, y, 32, 20).build();
                this.amountLabel.active = false;
                x += 34;

                this.amountField = new EditBox(ScriptEditorScreen.this.font, x, y + 2, 40, 16, Component.literal("Amt"));
                this.amountField.setMaxLength(4);
                this.amountField.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
                this.amountField.setValue(Integer.toString(instruction.getAmount()));
                this.amountField.setResponder(text -> {
                    int amount = text == null || text.isEmpty() ? 0 : Integer.parseInt(text);
                    instruction.setAmount(amount);
                    SmartClickerClient.getScriptManager().saveScript(script);
                });
                x += 42;
            } else {
                this.amountLabel = null;
                this.amountField = null;
            }

            // Delay slider
            int paletteLeft = ScriptEditorScreen.this.width - 120;
            int maxDelayX = Math.max(COMMAND_BUTTONS_X, paletteLeft - 10 - 80 - 35);
            int delayX = Math.min(x + 2, maxDelayX);
            this.delaySlider = new CustomSlider(delayX, y, 80, 20,
                Component.literal("Delay: " + instruction.getPostDelay() + "t"),
                (instruction.getPostDelay() - 1) / 49.0,
                instruction);

            // Set delay button
            this.setDelayButton = Button.builder(Component.literal("Set"), button -> {
                minecraft.setScreen(new SetDelayDialog(ScriptEditorScreen.this, instruction.getPostDelay(), newDelay -> {
                    instruction.setPostDelay(newDelay);
                    SmartClickerClient.getScriptManager().saveScript(script);
                    // Update the slider to reflect the new value
                    double sliderValue = Math.min((newDelay - 1) / 49.0, 1.0);
                    delaySlider.setSliderValue(sliderValue);
                }));
            }).bounds(delayX + 82, y, 30, 20).build();
        }

        private void updateAutocomplete() {
            if (!isItemIdParam(instruction.getType())) {
                this.autocompleteTarget = null;
                this.paramField.setSuggestion(null);
                return;
            }

            String current = this.paramField.getValue();
            String normalized = current == null ? "" : current.trim().toLowerCase();
            if (normalized.isEmpty()) {
                this.autocompleteTarget = "minecraft:";
                this.paramField.setSuggestion("minecraft:");
                return;
            }

            String best = findBestItemIdMatch(normalized);
            this.autocompleteTarget = best;
            if (best != null && best.startsWith(normalized) && best.length() > normalized.length()) {
                this.paramField.setSuggestion(best.substring(normalized.length()));
            } else {
                this.paramField.setSuggestion(null);
            }
        }

        private boolean canApplyAutocomplete() {
            if (!isItemIdParam(instruction.getType())) return false;
            if (!this.paramField.isFocused()) return false;
            if (this.autocompleteTarget == null) return false;
            String current = this.paramField.getValue();
            if (current == null) return false;
            String normalized = current.trim().toLowerCase();
            return this.autocompleteTarget.startsWith(normalized) && !this.autocompleteTarget.equals(normalized);
        }

        private void applyAutocomplete() {
            if (!canApplyAutocomplete()) return;
            this.paramField.setValue(this.autocompleteTarget);
            this.paramField.moveCursorToEnd(false);
        }
    }

    private static boolean isItemIdParam(CommandType type) {
        return type == CommandType.BUY || type == CommandType.SELL;
    }

    private static String findBestItemIdMatch(String normalized) {
        if (ALL_ITEM_IDS == null || ALL_ITEM_IDS.isEmpty()) return null;

        // Prefer prefix matches, then substring matches.
        for (String id : ALL_ITEM_IDS) {
            if (id.startsWith(normalized)) return id;
        }
        for (String id : ALL_ITEM_IDS) {
            if (id.contains(normalized)) return id;
        }
        return null;
    }
}
