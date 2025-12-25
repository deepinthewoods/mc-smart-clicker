package ninja.trek.smartclicker.recording;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import ninja.trek.smartclicker.command.CommandInstruction;
import ninja.trek.smartclicker.command.CommandType;
import ninja.trek.smartclicker.mixin.client.InventoryAccessor;
import ninja.trek.smartclicker.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class RecordingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecordingManager.class);
    private static final int HOLD_THRESHOLD_TICKS = 5; // 0.25 seconds

    private boolean recording = false;
    private Script targetScript = null;
    private List<CommandInstruction> recordedCommands = new ArrayList<>();
    private long startTick = 0;
    private long lastCommandTick = 0;

    // Track button states for hold detection
    private boolean leftButtonDown = false;
    private long leftButtonDownTick = 0;
    private boolean rightButtonDown = false;
    private long rightButtonDownTick = 0;

    // Track movement key states
    private boolean forwardKeyDown = false;
    private long forwardKeyDownTick = 0;
    private boolean backKeyDown = false;
    private long backKeyDownTick = 0;
    private boolean leftKeyDown = false;
    private long leftKeyDownTick = 0;
    private boolean rightKeyDown = false;
    private long rightKeyDownTick = 0;

    // Track crouch state
    private boolean crouchKeyDown = false;

    // Track hotbar slot
    private int lastHotbarSlot = -1;

    // Track trade state
    private final java.util.Map<Integer, Integer> lastTradeUses = new java.util.HashMap<>();

    // Track mouse movement recording
    private boolean recordMouseMovements = false;
    private float lastSavedYaw = 0.0f;
    private float lastSavedPitch = 0.0f;
    private boolean mousePositionChanged = false;
    private boolean wasStationary = true;

    public void startRecording(Script script, boolean recordMouseMovements) {
        if (script == null) {
            LOGGER.error("Cannot start recording with null script");
            return;
        }

        this.recording = true;
        this.targetScript = script;
        this.recordedCommands.clear();
        this.startTick = getCurrentTick();
        this.lastCommandTick = this.startTick;

        // Reset all state
        this.leftButtonDown = false;
        this.rightButtonDown = false;
        this.forwardKeyDown = false;
        this.backKeyDown = false;
        this.leftKeyDown = false;
        this.rightKeyDown = false;
        this.crouchKeyDown = false;
        this.lastHotbarSlot = -1;
        this.lastTradeUses.clear();

        // Initialize mouse movement recording
        this.recordMouseMovements = recordMouseMovements;
        this.mousePositionChanged = false;
        this.wasStationary = true;
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            this.lastSavedYaw = client.player.getYRot();
            this.lastSavedPitch = client.player.getXRot();
        }

        LOGGER.info("Started recording for script: {} (Record Mouse: {})", script.getName(), recordMouseMovements);
    }

    public void stopRecording() {
        if (!recording) return;

        // Release any held keys before stopping
        Minecraft client = Minecraft.getInstance();
        long currentTick = getCurrentTick();

        if (forwardKeyDown) {
            addMoveCommand("w", forwardKeyDownTick, currentTick);
            forwardKeyDown = false;
        }
        if (backKeyDown) {
            addMoveCommand("s", backKeyDownTick, currentTick);
            backKeyDown = false;
        }
        if (leftKeyDown) {
            addMoveCommand("a", leftKeyDownTick, currentTick);
            leftKeyDown = false;
        }
        if (rightKeyDown) {
            addMoveCommand("d", rightKeyDownTick, currentTick);
            rightKeyDown = false;
        }

        // Save any pending mouse position before stopping
        savePendingMousePosition();

        // Add all recorded commands to the script
        for (CommandInstruction instruction : recordedCommands) {
            targetScript.addInstruction(instruction);
        }

        LOGGER.info("Stopped recording. Added {} commands to script: {}", recordedCommands.size(), targetScript.getName());

        this.recording = false;
        this.targetScript = null;
        this.recordedCommands.clear();
    }

    public boolean isRecording() {
        return recording;
    }

    public Script getTargetScript() {
        return targetScript;
    }

    public void tick(Minecraft client) {
        if (!recording || client.player == null) return;

        // Stop recording if player opens a screen (ESC/menu)
        if (client.screen != null) {
            LOGGER.info("Screen detected, stopping recording");
            stopRecording();
            return;
        }

        long currentTick = getCurrentTick();

        // Handle mouse movement recording
        if (recordMouseMovements) {
            float currentYaw = client.player.getYRot();
            float currentPitch = client.player.getXRot();
            boolean isStationary = isPlayerStationary(client);

            if (isStationary) {
                // Player is stationary - allow mouse movement
                // Check if mouse position has changed
                float yawDiff = Math.abs(currentYaw - lastSavedYaw);
                float pitchDiff = Math.abs(currentPitch - lastSavedPitch);

                // Account for yaw wrapping (359.9 -> 0.1 should be small diff)
                if (yawDiff > 180.0f) {
                    yawDiff = 360.0f - yawDiff;
                }

                if (yawDiff > 0.01f || pitchDiff > 0.01f) {
                    mousePositionChanged = true;
                }

                wasStationary = true;
            } else {
                // Player is non-stationary - freeze camera
                client.player.setYRot(lastSavedYaw);
                client.player.setXRot(lastSavedPitch);

                wasStationary = false;
            }
        }

        // Record left mouse button
        recordLeftMouse(client, currentTick);

        // Record right mouse button
        recordRightMouse(client, currentTick);

        // Record jump
        recordJump(client, currentTick);

        // Record crouch toggle
        recordCrouch(client, currentTick);

        // Record movement keys
        recordMovement(client, currentTick);

        // Record hotbar changes
        recordHotbarChange(client, currentTick);

        // Record villager trades
        recordTrades(client, currentTick);
    }

    private void recordLeftMouse(Minecraft client, long currentTick) {
        boolean isDown = client.options.keyAttack.isDown();

        if (isDown && !leftButtonDown) {
            // Button just pressed
            leftButtonDown = true;
            leftButtonDownTick = currentTick;
        } else if (!isDown && leftButtonDown) {
            // Button just released
            long holdDuration = currentTick - leftButtonDownTick;
            if (holdDuration >= HOLD_THRESHOLD_TICKS) {
                // It was a hold
                addCommand(CommandType.LEFT_HOLD, "", (int) holdDuration);
            } else {
                // It was a click
                addCommand(CommandType.LEFT_CLICK, "", 1);
            }
            leftButtonDown = false;
        }
    }

    private void recordRightMouse(Minecraft client, long currentTick) {
        boolean isDown = client.options.keyUse.isDown();

        if (isDown && !rightButtonDown) {
            // Button just pressed
            rightButtonDown = true;
            rightButtonDownTick = currentTick;
        } else if (!isDown && rightButtonDown) {
            // Button just released
            long holdDuration = currentTick - rightButtonDownTick;
            if (holdDuration >= HOLD_THRESHOLD_TICKS) {
                // It was a hold
                addCommand(CommandType.RIGHT_HOLD, "", (int) holdDuration);
            } else {
                // It was a click
                addCommand(CommandType.RIGHT_CLICK, "", 1);
            }
            rightButtonDown = false;
        }
    }

    private void recordJump(Minecraft client, long currentTick) {
        // Only record jump when key is pressed (not held)
        if (client.options.keyJump.consumeClick()) {
            addCommand(CommandType.JUMP, "", 1);
        }
    }

    private void recordCrouch(Minecraft client, long currentTick) {
        boolean isDown = client.options.keyShift.isDown();

        if (isDown != crouchKeyDown) {
            // Crouch state changed
            crouchKeyDown = isDown;
            addCommand(CommandType.CROUCH, isDown ? "ON" : "OFF", 1);
        }
    }

    private void recordMovement(Minecraft client, long currentTick) {
        // Forward (W)
        boolean forwardDown = client.options.keyUp.isDown();
        if (forwardDown && !forwardKeyDown) {
            forwardKeyDown = true;
            forwardKeyDownTick = currentTick;
        } else if (!forwardDown && forwardKeyDown) {
            addMoveCommand("w", forwardKeyDownTick, currentTick);
            forwardKeyDown = false;
        }

        // Back (S)
        boolean backDown = client.options.keyDown.isDown();
        if (backDown && !backKeyDown) {
            backKeyDown = true;
            backKeyDownTick = currentTick;
        } else if (!backDown && backKeyDown) {
            addMoveCommand("s", backKeyDownTick, currentTick);
            backKeyDown = false;
        }

        // Left (A)
        boolean leftDown = client.options.keyLeft.isDown();
        if (leftDown && !leftKeyDown) {
            leftKeyDown = true;
            leftKeyDownTick = currentTick;
        } else if (!leftDown && leftKeyDown) {
            addMoveCommand("a", leftKeyDownTick, currentTick);
            leftKeyDown = false;
        }

        // Right (D)
        boolean rightDown = client.options.keyRight.isDown();
        if (rightDown && !rightKeyDown) {
            rightKeyDown = true;
            rightKeyDownTick = currentTick;
        } else if (!rightDown && rightKeyDown) {
            addMoveCommand("d", rightKeyDownTick, currentTick);
            rightKeyDown = false;
        }
    }

    private void addMoveCommand(String direction, long startTick, long endTick) {
        int duration = (int) (endTick - startTick);
        if (duration < 1) duration = 1;
        addCommand(CommandType.MOVE, direction, duration);
    }

    private void recordHotbarChange(Minecraft client, long currentTick) {
        if (client.player == null) return;

        int currentSlot = ((InventoryAccessor) client.player.getInventory()).getSelected();

        // Initialize on first tick
        if (lastHotbarSlot == -1) {
            lastHotbarSlot = currentSlot;
            return;
        }

        if (currentSlot != lastHotbarSlot) {
            addCommand(CommandType.BELT_SELECT, String.valueOf(currentSlot), 1);
            lastHotbarSlot = currentSlot;
        }
    }

    private void recordTrades(Minecraft client, long currentTick) {
        if (!(client.screen instanceof MerchantScreen)) {
            lastTradeUses.clear();
            return;
        }

        LocalPlayer player = client.player;
        if (player == null || !(player.containerMenu instanceof MerchantMenu menu)) {
            return;
        }

        MerchantOffers offers = menu.getOffers();
        if (offers.isEmpty()) return;

        // Check all offers for changes in use count
        for (int i = 0; i < offers.size(); i++) {
            MerchantOffer offer = offers.get(i);
            int currentUses = offer.getUses();
            int previousUses = lastTradeUses.getOrDefault(i, 0);

            // Detect if a trade was just completed
            if (currentUses > previousUses) {
                // A trade was completed!
                recordTradeCommand(offer, currentTick);
            }

            lastTradeUses.put(i, currentUses);
        }
    }

    private void recordTradeCommand(MerchantOffer offer, long currentTick) {
        // Determine if this is a BUY or SELL based on the trade
        // A BUY means we're buying items from the villager (result is what we get)
        // A SELL means we're selling items to the villager

        String resultItemId = offer.getResult().getItem().toString();

        // For simplicity, we'll default to BUY (purchasing from villager)
        // The user can manually change it to SELL if needed
        addCommand(CommandType.BUY, resultItemId, 1);

        LOGGER.info("Recorded trade: {}", resultItemId);
    }

    private void addCommand(CommandType type, String parameter, int delay) {
        // Save pending mouse position before adding any other command
        savePendingMousePosition();

        // Calculate the delay since the last command
        long currentTick = getCurrentTick();
        int postDelay = (int) (currentTick - lastCommandTick);
        if (postDelay < 1) postDelay = 1;

        // Set the delay on the previous command if it exists
        if (!recordedCommands.isEmpty()) {
            CommandInstruction lastCommand = recordedCommands.get(recordedCommands.size() - 1);
            lastCommand.setPostDelay(postDelay);
        }

        // Add the new command with the specified delay (for holds/moves)
        CommandInstruction instruction = new CommandInstruction(type, parameter, delay);
        recordedCommands.add(instruction);
        lastCommandTick = currentTick;

        LOGGER.debug("Recorded command: {} {} (delay: {})", type.getDisplayName(), parameter, delay);
    }

    /**
     * Saves the current mouse position as TILT_ABSOLUTE and PAN_ABSOLUTE commands
     * if the mouse position has changed since the last save.
     */
    private void savePendingMousePosition() {
        if (!recordMouseMovements || !mousePositionChanged) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        float currentYaw = client.player.getYRot();
        float currentPitch = client.player.getXRot();

        // Calculate the delay since the last command
        long currentTick = getCurrentTick();
        int postDelay = (int) (currentTick - lastCommandTick);
        if (postDelay < 1) postDelay = 1;

        // Set the delay on the previous command if it exists
        if (!recordedCommands.isEmpty()) {
            CommandInstruction lastCommand = recordedCommands.get(recordedCommands.size() - 1);
            lastCommand.setPostDelay(postDelay);
        }

        // Add TILT_ABSOLUTE command
        CommandInstruction tiltInstruction = new CommandInstruction(
            CommandType.TILT_ABSOLUTE,
            String.format("%.2f", currentPitch),
            1
        );
        recordedCommands.add(tiltInstruction);
        lastCommandTick = currentTick;

        // Add PAN_ABSOLUTE command (with minimal delay since they happen together)
        CommandInstruction panInstruction = new CommandInstruction(
            CommandType.PAN_ABSOLUTE,
            String.format("%.2f", currentYaw),
            1
        );
        recordedCommands.add(panInstruction);
        lastCommandTick = currentTick;

        // Update saved position and reset flag
        lastSavedYaw = currentYaw;
        lastSavedPitch = currentPitch;
        mousePositionChanged = false;

        LOGGER.debug("Saved mouse position: Yaw={}, Pitch={}", currentYaw, currentPitch);
    }

    private long getCurrentTick() {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) {
            return client.level.getGameTime();
        }
        return 0;
    }

    /**
     * Checks if the player is stationary (no movement keys pressed and velocity below threshold).
     */
    private boolean isPlayerStationary(Minecraft client) {
        if (client.player == null) return true;

        // Check if any movement keys are pressed
        boolean movementKeysPressed = client.options.keyUp.isDown()
            || client.options.keyDown.isDown()
            || client.options.keyLeft.isDown()
            || client.options.keyRight.isDown();

        if (movementKeysPressed) {
            return false;
        }

        // Check velocity magnitude
        net.minecraft.world.phys.Vec3 velocity = client.player.getDeltaMovement();
        double velocityMagnitude = Math.sqrt(
            velocity.x * velocity.x +
            velocity.y * velocity.y +
            velocity.z * velocity.z
        );

        return velocityMagnitude < 0.0001;
    }
}
