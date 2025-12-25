package ninja.trek.smartclicker;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.resources.ResourceLocation;
import ninja.trek.smartclicker.SmartClicker;
import ninja.trek.smartclicker.executor.ScriptExecutor;
import ninja.trek.smartclicker.script.Script;
import ninja.trek.smartclicker.script.ScriptManager;
import ninja.trek.smartclicker.ui.ScriptMenuScreen;
import ninja.trek.smartclicker.recording.RecordingManager;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmartClickerClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("SmartClicker");

	private static final KeyMapping.Category KEY_CATEGORY =
		KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath(SmartClicker.MOD_ID, "main"));

	private static ScriptManager scriptManager;
	private static ScriptExecutor executor;
	private static RecordingManager recordingManager;
	private static KeyMapping menuKeyBinding;

	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing Smart Clicker Client");

		// Initialize script manager
		scriptManager = new ScriptManager(FabricLoader.getInstance().getConfigDir());

		// Initialize executor
		executor = new ScriptExecutor();

		// Initialize recording manager
		recordingManager = new RecordingManager();

		// Register keybinding for opening menu (default: M key)
		menuKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.smart-clicker.menu",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_M,
			KEY_CATEGORY
		));

		// Register tick event for script execution and recording
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// Handle recording tick
			if (recordingManager.isRecording()) {
				recordingManager.tick(client);

				// Check for menu key press to stop recording
				while (menuKeyBinding.consumeClick()) {
					Script targetScript = recordingManager.getTargetScript();
					recordingManager.stopRecording();
					if (targetScript != null) {
						scriptManager.saveScript(targetScript);
					}
					// Open menu after stopping recording
					client.setScreen(new ScriptMenuScreen(client.screen));
				}
			} else {
				// Execute script tick
				executor.tick();

				// Check for menu key press
				while (menuKeyBinding.consumeClick()) {
					if (executor.isRunning()) {
						// Stop script if running
						executor.stop();
					} else {
						// Open menu if not running
						client.setScreen(new ScriptMenuScreen(client.screen));
					}
				}

				// Stop script if player clicks, opens inventory, or opens the pause menu
				if (executor.isRunning()) {
					// Check for mouse clicks
					boolean attackClick = client.options.keyAttack.consumeClick();
					boolean useClick = client.options.keyUse.consumeClick();
					if (attackClick || useClick) {
						executor.stop();
					}

					// Check if inventory or pause screen is open
					if (client.screen != null) {
						if (client.screen instanceof PauseScreen) {
							executor.stop();
						}
						String screenClass = client.screen.getClass().getSimpleName();
						if (screenClass.contains("Inventory") || screenClass.contains("Container")) {
							executor.stop();
						}
					}
				}
			}
		});

		// Register HUD render callback for recording indicator
		HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
			if (recordingManager.isRecording()) {
				Minecraft client = Minecraft.getInstance();
				if (client.font != null) {
					String text = "RECORDING - Press M to stop";
					int textWidth = client.font.width(text);
					int x = (client.getWindow().getGuiScaledWidth() - textWidth) / 2;
					int y = 10;

					// Draw background
					drawContext.fill(x - 5, y - 2, x + textWidth + 5, y + 12, 0xAA000000);

					// Draw text in red
					drawContext.drawString(client.font, text, x, y, 0xFF5555, true);
				}
			}
		});

		LOGGER.info("Smart Clicker Client initialized");
	}

	public static ScriptManager getScriptManager() {
		return scriptManager;
	}

	public static ScriptExecutor getExecutor() {
		return executor;
	}

	public static RecordingManager getRecordingManager() {
		return recordingManager;
	}
}
