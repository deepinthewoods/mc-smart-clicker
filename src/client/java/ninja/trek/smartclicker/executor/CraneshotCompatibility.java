package ninja.trek.smartclicker.executor;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Compatibility layer for the Craneshot camera mod.
 * Handles freecam mode detection and camera rotation updates.
 */
public class CraneshotCompatibility {
    private static final Logger LOGGER = LoggerFactory.getLogger(CraneshotCompatibility.class);

    private static Boolean craneshotAvailable = null;
    private static Class<?> cameraEntityClass = null;
    private static Class<?> cameraSystemClass = null;
    private static Method getCameraMethod = null;
    private static Method getCameraInstanceMethod = null;
    private static Method updateRotationMethod = null;

    /**
     * Checks if the Craneshot mod is available.
     */
    public static boolean isCraneshotAvailable() {
        if (craneshotAvailable == null) {
            try {
                cameraEntityClass = Class.forName("ninja.trek.util.CameraEntity");
                cameraSystemClass = Class.forName("ninja.trek.camera.CameraSystem");

                getCameraMethod = cameraEntityClass.getMethod("getCamera");
                getCameraInstanceMethod = cameraSystemClass.getMethod("getInstance");
                updateRotationMethod = cameraSystemClass.getMethod("updateRotation", double.class, double.class, double.class);

                craneshotAvailable = true;
                LOGGER.info("Craneshot camera mod detected - compatibility enabled");
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                craneshotAvailable = false;
                LOGGER.debug("Craneshot camera mod not detected - compatibility disabled");
            }
        }
        return craneshotAvailable;
    }

    /**
     * Checks if freecam mode is currently active.
     */
    public static boolean isFreecamActive() {
        if (!isCraneshotAvailable()) {
            return false;
        }

        try {
            Object cameraEntity = getCameraMethod.invoke(null);
            return cameraEntity != null;
        } catch (Exception e) {
            LOGGER.error("Failed to check freecam status", e);
            return false;
        }
    }

    /**
     * Updates the camera rotation when in freecam mode.
     * This should be called in addition to updating the player rotation.
     *
     * @param yawDelta Change in yaw (horizontal rotation)
     * @param pitchDelta Change in pitch (vertical rotation)
     */
    public static void updateCameraRotation(double yawDelta, double pitchDelta) {
        if (!isCraneshotAvailable() || !isFreecamActive()) {
            return;
        }

        try {
            Object cameraSystem = getCameraInstanceMethod.invoke(null);
            if (cameraSystem != null) {
                // Convert degrees to the sensitivity multiplier expected by CameraSystem
                // The updateRotation method in CameraSystem already applies sensitivity calculations
                double sensitivity = 0.15; // Base sensitivity for script commands
                updateRotationMethod.invoke(cameraSystem, yawDelta * sensitivity, pitchDelta * sensitivity, 1.0);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to update camera rotation", e);
        }
    }

    /**
     * Ensures the player remains the target for interactions even when in freecam mode.
     * This is called before executing click actions.
     */
    public static void ensurePlayerInteraction(Minecraft client) {
        if (!isCraneshotAvailable() || !isFreecamActive()) {
            return;
        }

        // When in freecam mode, Minecraft's attack/use methods should still operate
        // on the player. The craneshot mod's CameraEntity is marked as a spectator,
        // so most interaction methods will fail on it anyway. By ensuring we're
        // using the player entity for interactions, clicks will work correctly.
        // The Minecraft client already tracks the player separately from the camera
        // entity, so clicks should naturally work on the player as long as we don't
        // try to override the target.

        // Note: We don't need to do anything special here because Minecraft's
        // startAttack() and startUseItem() methods use client.player, not
        // client.getCameraEntity(), for the entity performing the action.
        // The crosshair targeting uses the camera's look direction, which is correct.
    }
}
