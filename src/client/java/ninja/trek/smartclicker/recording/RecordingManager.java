package ninja.trek.smartclicker.recording;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import ninja.trek.smartclicker.command.CommandInstruction;
import ninja.trek.smartclicker.command.CommandType;
import ninja.trek.smartclicker.mixin.client.InventoryAccessor;
import ninja.trek.smartclicker.script.Script;

import java.util.ArrayList;
import java.util.List;

public class RecordingManager {
    private static final int HOLD_THRESHOLD_TICKS = 5; // 0.25 seconds

    private boolean recording = false;
    private Script targetScript = null;
    private List<CommandInstruction> recordedCommands = new ArrayList<>();
    private long startTick = 0;
    private long lastCommandTick = 0;

    // Active action system — at most one duration-based action at a time
    private enum ActiveAction {
        NONE, MOVE_W, MOVE_S, MOVE_A, MOVE_D, LEFT_MOUSE, RIGHT_MOUSE
    }
    private ActiveAction activeAction = ActiveAction.NONE;
    private long activeActionStartTick = 0;

    // Track crouch state
    private boolean crouchKeyDown = false;

    // Track drop key state
    private boolean dropKeyWasDown = false;

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
            return;
        }

        this.recording = true;
        this.targetScript = script;
        this.recordedCommands.clear();
        this.startTick = getCurrentTick();
        this.lastCommandTick = this.startTick;

        // Reset all state
        this.activeAction = ActiveAction.NONE;
        this.activeActionStartTick = 0;
        this.crouchKeyDown = false;
        this.dropKeyWasDown = false;
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

    }

    public void stopRecording() {
        if (!recording) return;

        long currentTick = getCurrentTick();

        // Finalize active action if any
        if (activeAction != ActiveAction.NONE) {
            finalizeActiveAction(currentTick);
        }

        // Save any pending mouse position before stopping
        savePendingMousePosition();

        // Add all recorded commands to the script
        for (CommandInstruction instruction : recordedCommands) {
            targetScript.addInstruction(instruction);
        }

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

        // Stop recording if player opens the pause screen (ESC)
        if (client.screen instanceof PauseScreen) {
            stopRecording();
            return;
        }

        // Skip action recording while any other screen is open (inventory, etc.)
        // Recording stays active so the mixin can detect inventory throws
        if (client.screen != null) {
            return;
        }

        long currentTick = getCurrentTick();

        // 1. Block concurrent actions — force non-active keys to false
        blockConcurrentActions(client);

        // 2. Handle mouse movement recording (isPlayerStationary sees correct state)
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

        // 3. Record left mouse button (respects active action)
        recordLeftMouse(client, currentTick);

        // 4. Record right mouse button (respects active action)
        recordRightMouse(client, currentTick);

        // 5. Record jump (passive — always allowed)
        recordJump(client, currentTick);

        // 6. Record crouch toggle (passive — always allowed)
        recordCrouch(client, currentTick);

        // 7. Record movement keys (respects active action)
        recordMovement(client, currentTick);

        // 8. Record hotbar changes (passive — always allowed)
        recordHotbarChange(client, currentTick);

        // 9. Record drop item (passive — Q key edge detection)
        recordDropItem(client, currentTick);

        // 10. Record villager trades (passive — always allowed)
        recordTrades(client, currentTick);
    }

    /**
     * Forces all non-active action keys to setDown(false) each tick,
     * preventing both the game action and recording for blocked keys.
     */
    private void blockConcurrentActions(Minecraft client) {
        if (activeAction == ActiveAction.NONE) return;

        // Block all active-action keys except the current active one
        if (activeAction != ActiveAction.MOVE_W) {
            client.options.keyUp.setDown(false);
        }
        if (activeAction != ActiveAction.MOVE_S) {
            client.options.keyDown.setDown(false);
        }
        if (activeAction != ActiveAction.MOVE_A) {
            client.options.keyLeft.setDown(false);
        }
        if (activeAction != ActiveAction.MOVE_D) {
            client.options.keyRight.setDown(false);
        }
        if (activeAction != ActiveAction.LEFT_MOUSE) {
            client.options.keyAttack.setDown(false);
        }
        if (activeAction != ActiveAction.RIGHT_MOUSE) {
            client.options.keyUse.setDown(false);
        }
    }

    private void recordLeftMouse(Minecraft client, long currentTick) {
        boolean isDown = client.options.keyAttack.isDown();

        if (activeAction == ActiveAction.LEFT_MOUSE) {
            // We own the active action — check for release
            if (!isDown) {
                finalizeActiveAction(currentTick);
                checkNextActiveAction(client, currentTick);
            }
        } else if (activeAction == ActiveAction.NONE && isDown) {
            // No active action and button just pressed — claim it
            activeAction = ActiveAction.LEFT_MOUSE;
            activeActionStartTick = currentTick;
        }
        // Otherwise: another action is active, button is blocked by blockConcurrentActions
    }

    private void recordRightMouse(Minecraft client, long currentTick) {
        boolean isDown = client.options.keyUse.isDown();

        if (activeAction == ActiveAction.RIGHT_MOUSE) {
            // We own the active action — check for release
            if (!isDown) {
                finalizeActiveAction(currentTick);
                checkNextActiveAction(client, currentTick);
            }
        } else if (activeAction == ActiveAction.NONE && isDown) {
            // No active action and button just pressed — claim it
            activeAction = ActiveAction.RIGHT_MOUSE;
            activeActionStartTick = currentTick;
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
        if (activeAction == ActiveAction.MOVE_W || activeAction == ActiveAction.MOVE_S
                || activeAction == ActiveAction.MOVE_A || activeAction == ActiveAction.MOVE_D) {
            // We own the active action — check if the active key was released
            boolean activeKeyStillDown = switch (activeAction) {
                case MOVE_W -> client.options.keyUp.isDown();
                case MOVE_S -> client.options.keyDown.isDown();
                case MOVE_A -> client.options.keyLeft.isDown();
                case MOVE_D -> client.options.keyRight.isDown();
                default -> false;
            };

            if (!activeKeyStillDown) {
                finalizeActiveAction(currentTick);
                checkNextActiveAction(client, currentTick);
            }
        } else if (activeAction == ActiveAction.NONE) {
            // No active action — check if any movement key is pressed (priority: W > S > A > D)
            if (client.options.keyUp.isDown()) {
                activeAction = ActiveAction.MOVE_W;
                activeActionStartTick = currentTick;
            } else if (client.options.keyDown.isDown()) {
                activeAction = ActiveAction.MOVE_S;
                activeActionStartTick = currentTick;
            } else if (client.options.keyLeft.isDown()) {
                activeAction = ActiveAction.MOVE_A;
                activeActionStartTick = currentTick;
            } else if (client.options.keyRight.isDown()) {
                activeAction = ActiveAction.MOVE_D;
                activeActionStartTick = currentTick;
            }
        }
        // Otherwise: another action type is active, keys are blocked by blockConcurrentActions
    }

    /**
     * Records the command for the current active action and resets to NONE.
     */
    private void finalizeActiveAction(long currentTick) {
        switch (activeAction) {
            case MOVE_W -> addMoveCommand("w", activeActionStartTick, currentTick);
            case MOVE_S -> addMoveCommand("s", activeActionStartTick, currentTick);
            case MOVE_A -> addMoveCommand("a", activeActionStartTick, currentTick);
            case MOVE_D -> addMoveCommand("d", activeActionStartTick, currentTick);
            case LEFT_MOUSE -> {
                long holdDuration = currentTick - activeActionStartTick;
                if (holdDuration >= HOLD_THRESHOLD_TICKS) {
                    addCommand(CommandType.LEFT_HOLD, "", (int) holdDuration);
                } else {
                    addCommand(CommandType.LEFT_CLICK, "", 1);
                }
            }
            case RIGHT_MOUSE -> {
                long holdDuration = currentTick - activeActionStartTick;
                if (holdDuration >= HOLD_THRESHOLD_TICKS) {
                    addCommand(CommandType.RIGHT_HOLD, "", (int) holdDuration);
                } else {
                    addCommand(CommandType.RIGHT_CLICK, "", 1);
                }
            }
            case NONE -> {}
        }
        activeAction = ActiveAction.NONE;
    }

    /**
     * After the active action is released, check if another active-action key is still
     * physically held and auto-activate it. We must read the raw key state here since
     * blockConcurrentActions already ran this tick and forced them to false — but the
     * active action is now NONE, so next tick they won't be blocked.
     *
     * Priority: W > S > A > D > Left Mouse > Right Mouse
     */
    private void checkNextActiveAction(Minecraft client, long currentTick) {
        // We need to peek at the real underlying key state. Since blockConcurrentActions
        // already set these to false for this tick, we check KeyMapping directly.
        // However, the physical key is still held, so on the next tick (before blocking)
        // isDown() will return true again. We can set the active action now so that
        // blockConcurrentActions on the next tick knows what to allow.
        //
        // For movement keys, the game re-evaluates isDown from the physical key state
        // each tick, so even though we set them to false this tick, they'll be true
        // next tick if still physically held. We need to check the physical state.
        //
        // We use a simple approach: since we just finalized, set activeAction = NONE.
        // On the next tick, blockConcurrentActions sees NONE and doesn't block anything,
        // so recordMovement/recordLeftMouse/recordRightMouse will pick up the next key
        // naturally via their "activeAction == NONE && isDown" checks.
        //
        // This is correct because:
        // - This tick: the just-released action is finalized
        // - Next tick: no blocking happens (NONE), the first recorder to see a held key claims it
        // The tick order (left mouse -> right mouse -> movement) determines implicit priority.
        // That's fine — the one-tick gap where no action is active is imperceptible.
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

    private void recordDropItem(Minecraft client, long currentTick) {
        boolean isDown = client.options.keyDrop.isDown();
        if (isDown && !dropKeyWasDown) {
            // Rising edge — Q key just pressed
            if (client.hasControlDown()) {
                addCommand(CommandType.DROP_ITEM, "64", 1);
            } else {
                addCommand(CommandType.DROP_ITEM, "1", 1);
            }
        }
        dropKeyWasDown = isDown;
    }

    /**
     * Called by DropItemMixin when items are thrown from an inventory screen.
     */
    public void onInventoryThrow(boolean entireStack) {
        if (!recording) return;
        addCommand(CommandType.DROP_ITEM, entireStack ? "64" : "1", 1);
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
