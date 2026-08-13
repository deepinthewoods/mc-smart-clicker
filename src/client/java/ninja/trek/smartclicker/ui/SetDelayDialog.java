package ninja.trek.smartclicker.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public class SetDelayDialog extends Screen {
    private final Screen parent;
    private final int currentDelay;
    private final Consumer<Integer> onConfirm;
    private EditBox delayInput;
    private Button confirmButton;
    private String errorMessage = null;

    public SetDelayDialog(Screen parent, int currentDelay, Consumer<Integer> onConfirm) {
        super(Component.literal("Set Delay"));
        this.parent = parent;
        this.currentDelay = currentDelay;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int dialogWidth = 300;
        int dialogHeight = 140;
        int dialogX = (this.width - dialogWidth) / 2;
        int dialogY = (this.height - dialogHeight) / 2;

        // Delay input field
        this.delayInput = new EditBox(this.font, dialogX + 50, dialogY + 50, 200, 20, Component.literal("Delay"));
        this.delayInput.setValue(String.valueOf(currentDelay));
        this.delayInput.setMaxLength(10);
        this.delayInput.setResponder(s -> {
            errorMessage = null;
            validateInput(s);
        });
        this.addRenderableWidget(this.delayInput);
        this.setInitialFocus(this.delayInput);

        // Confirm button
        this.confirmButton = Button.builder(Component.literal("Confirm"), button -> {
            if (validateAndApply()) {
                minecraft.gui.setScreen(parent);
            }
        }).bounds(dialogX + 50, dialogY + 90, 90, 20).build();
        this.addRenderableWidget(confirmButton);

        // Cancel button
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> {
            minecraft.gui.setScreen(parent);
        }).bounds(dialogX + 160, dialogY + 90, 90, 20).build());
    }

    private boolean validateInput(String input) {
        if (input.isEmpty()) {
            errorMessage = "Delay cannot be empty";
            return false;
        }
        try {
            int value = Integer.parseInt(input);
            if (value < 1) {
                errorMessage = "Delay must be at least 1";
                return false;
            }
            return true;
        } catch (NumberFormatException e) {
            errorMessage = "Invalid number";
            return false;
        }
    }

    private boolean validateAndApply() {
        String input = this.delayInput.getValue();
        if (!validateInput(input)) {
            return false;
        }
        try {
            int delay = Integer.parseInt(input);
            onConfirm.accept(delay);
            return true;
        } catch (NumberFormatException e) {
            errorMessage = "Invalid number";
            return false;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int dialogWidth = 300;
        int dialogHeight = 140;
        int dialogX = (this.width - dialogWidth) / 2;
        int dialogY = (this.height - dialogHeight) / 2;

        // Dark overlay
        graphics.fill(0, 0, this.width, this.height, 0xC0000000);

        // Dialog background
        graphics.fill(dialogX, dialogY, dialogX + dialogWidth, dialogY + dialogHeight, 0xFF2B2B2B);

        // Border
        graphics.fill(dialogX, dialogY, dialogX + dialogWidth, dialogY + 1, 0xFFFFFFFF);
        graphics.fill(dialogX, dialogY + dialogHeight - 1, dialogX + dialogWidth, dialogY + dialogHeight, 0xFFFFFFFF);
        graphics.fill(dialogX, dialogY, dialogX + 1, dialogY + dialogHeight, 0xFFFFFFFF);
        graphics.fill(dialogX + dialogWidth - 1, dialogY, dialogX + dialogWidth, dialogY + dialogHeight, 0xFFFFFFFF);

        // Title
        graphics.centeredText(this.font, "Set Delay (ticks)", this.width / 2, dialogY + 15, 0xFFFFFFFF);

        // Label
        graphics.text(this.font, "Delay:", dialogX + 50, dialogY + 38, 0xFFFFFFFF);

        // Error message
        if (errorMessage != null) {
            graphics.centeredText(this.font, errorMessage, this.width / 2, dialogY + 75, 0xFFFF5555);
        }

        // Render input field

        // Render buttons
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        // Enter key confirms
        if (keyEvent.key() == GLFW.GLFW_KEY_ENTER) {
            if (validateAndApply()) {
                minecraft.gui.setScreen(parent);
                return true;
            }
        }
        // Escape key cancels
        if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE) {
            minecraft.gui.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
