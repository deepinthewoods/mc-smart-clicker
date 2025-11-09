package ninja.trek.smartclicker;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import ninja.trek.smartclicker.executor.ScriptExecutor;
import ninja.trek.smartclicker.script.ScriptManager;
import ninja.trek.smartclicker.ui.ScriptMenuScreen;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmartClickerClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("SmartClicker");

	private static ScriptManager scriptManager;
	private static ScriptExecutor executor;
	private static KeyMapping menuKeyBinding;

	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing Smart Clicker Client");

		// Initialize script manager
		scriptManager = new ScriptManager(FabricLoader.getInstance().getConfigDir());

		// Initialize executor
		executor = new ScriptExecutor();

		// Register keybinding for opening menu (default: M key)
		menuKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.smart-clicker.menu",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_M,
			"category.smart-clicker"
		));

		// Register tick event for script execution
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
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

			// Stop script if player clicks or opens inventory
			if (executor.isRunning()) {
				// Check for mouse clicks
				if (client.options.keyAttack.consumeClick() || client.options.keyUse.consumeClick()) {
					executor.stop();
				}

				// Check if inventory screen is open
				if (client.screen != null) {
					String screenClass = client.screen.getClass().getSimpleName();
					if (screenClass.contains("Inventory") || screenClass.contains("Container")) {
						executor.stop();
					}
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
}