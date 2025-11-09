package ninja.trek.smartclicker.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class ConfirmationDialog extends Screen {
    private final Screen parent;
    private final String message;
    private final Runnable onConfirm;

    public ConfirmationDialog(Screen parent, String message, Runnable onConfirm) {
        super(Component.literal("Confirm"));
        this.parent = parent;
        this.message = message;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Yes button
        this.addRenderableWidget(Button.builder(Component.literal("Yes"), button -> {
            onConfirm.run();
            onClose();
        }).bounds(centerX - 80, centerY + 20, 70, 20).build());

        // No button
        this.addRenderableWidget(Button.builder(Component.literal("No"), button -> {
            onClose();
        }).bounds(centerX + 10, centerY + 20, 70, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Render dark overlay background
        graphics.fill(0, 0, this.width, this.height, 0xC0000000);

        // Draw dialog box
        int boxWidth = 300;
        int boxHeight = 100;
        int boxX = this.width / 2 - boxWidth / 2;
        int boxY = this.height / 2 - boxHeight / 2;
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF202020);
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 1, 0xFFFFFFFF);
        graphics.fill(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, 0xFFFFFFFF);
        graphics.fill(boxX, boxY, boxX + 1, boxY + boxHeight, 0xFFFFFFFF);
        graphics.fill(boxX + boxWidth - 1, boxY, boxX + boxWidth, boxY + boxHeight, 0xFFFFFFFF);

        super.render(graphics, mouseX, mouseY, delta);

        // Draw message
        graphics.drawCenteredString(this.font, message, this.width / 2, this.height / 2 - 20, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
