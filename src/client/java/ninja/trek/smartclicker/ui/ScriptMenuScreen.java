package ninja.trek.smartclicker.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import ninja.trek.smartclicker.SmartClickerClient;
import ninja.trek.smartclicker.script.Script;

import java.util.ArrayList;
import java.util.List;

public class ScriptMenuScreen extends Screen {
    private final Screen parent;
    private final List<ScriptRow> scriptRows = new ArrayList<>();
    private int scrollOffset = 0;
    private static final int ROW_HEIGHT = 25;
    private static final int BUTTON_HEIGHT = 20;
    private static final int LIST_TOP = 40;

    public ScriptMenuScreen(Screen parent) {
        super(Component.literal("Smart Clicker Scripts"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        buildScriptList();

        // Add "+" button at the bottom
        this.addRenderableWidget(Button.builder(Component.literal("+"), button -> {
            Script newScript = new Script("New Script");
            SmartClickerClient.getScriptManager().saveScript(newScript);
            if (minecraft != null) {
                minecraft.setScreen(new ScriptEditorScreen(this, newScript));
            }
        }).bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }

    private void buildScriptList() {
        this.clearWidgets();
        scriptRows.clear();

        List<Script> scripts = SmartClickerClient.getScriptManager().getScripts();
        int y = LIST_TOP;

        for (Script script : scripts) {
            ScriptRow row = new ScriptRow(script, y);
            scriptRows.add(row);
            // Add buttons to the screen
            this.addRenderableWidget(row.runButton);
            this.addRenderableWidget(row.editButton);
            this.addRenderableWidget(row.deleteButton);
            y += ROW_HEIGHT;
        }

        // Re-add the + button
        this.addRenderableWidget(Button.builder(Component.literal("+"), button -> {
            Script newScript = new Script("New Script");
            SmartClickerClient.getScriptManager().saveScript(newScript);
            if (minecraft != null) {
                minecraft.setScreen(new ScriptEditorScreen(this, newScript));
            }
        }).bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        // Draw title
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private class ScriptRow {
        private final Script script;
        private final int y;
        public final Button runButton;
        public final Button editButton;
        public final Button deleteButton;

        public ScriptRow(Script script, int y) {
            this.script = script;
            this.y = y;

            int centerX = width / 2;

            // Run button (main button, wider)
            this.runButton = Button.builder(Component.literal(script.getName()), button -> {
                // Start script and close menu
                SmartClickerClient.getExecutor().startScript(script);
                onClose();
            }).bounds(centerX - 150, y, 250, BUTTON_HEIGHT).build();

            // Edit button
            this.editButton = Button.builder(Component.literal("Edit"), button -> {
                if (minecraft != null) {
                    minecraft.setScreen(new ScriptEditorScreen(ScriptMenuScreen.this, script));
                }
            }).bounds(centerX + 105, y, 45, BUTTON_HEIGHT).build();

            // Delete button
            this.deleteButton = Button.builder(Component.literal("Delete"), button -> {
                if (minecraft != null) {
                    minecraft.setScreen(new ConfirmationDialog(
                        ScriptMenuScreen.this,
                        "Delete script '" + script.getName() + "'?",
                        () -> {
                            SmartClickerClient.getScriptManager().deleteScript(script);
                            buildScriptList();
                        }
                    ));
                }
            }).bounds(centerX + 155, y, 50, BUTTON_HEIGHT).build();
        }
    }
}
