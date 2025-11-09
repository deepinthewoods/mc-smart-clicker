package ninja.trek.smartclicker.executor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import ninja.trek.smartclicker.command.CommandInstruction;
import ninja.trek.smartclicker.command.CommandType;
import ninja.trek.smartclicker.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.List;

public class ScriptExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptExecutor.class);
    private static Field inventorySelectedField = null;

    private Script currentScript;
    private int currentInstructionIndex;
    private int delayTicks;
    private boolean running;
    private boolean holding;
    private boolean leftHolding;
    private boolean rightHolding;
    private boolean leftClicking;
    private boolean rightClicking;
    private boolean movingForward;
    private boolean movingBack;
    private boolean movingLeft;
    private boolean movingRight;

    public ScriptExecutor() {
        this.running = false;
        this.holding = false;

        // Initialize reflection field for inventory selection
        if (inventorySelectedField == null) {
            try {
                inventorySelectedField = Class.forName("net.minecraft.world.entity.player.Inventory").getDeclaredField("selected");
                inventorySelectedField.setAccessible(true);
            } catch (Exception e) {
                LOGGER.error("Failed to initialize inventory selected field reflection", e);
            }
        }
    }

    public void startScript(Script script) {
        if (script == null || script.getInstructions().isEmpty()) {
            LOGGER.warn("Cannot start empty script");
            return;
        }

        this.currentScript = script;
        this.currentInstructionIndex = 0;
        this.delayTicks = 0;
        this.running = true;
        this.holding = false;
        this.leftHolding = false;
        this.rightHolding = false;
        this.leftClicking = false;
        this.rightClicking = false;
        this.movingForward = false;
        this.movingBack = false;
        this.movingLeft = false;
        this.movingRight = false;
        LOGGER.info("Started script: {}", script.getName());
    }

    public void stop() {
        if (!running) return;

        Minecraft client = Minecraft.getInstance();

        // Release any held or clicked buttons
        if ((leftHolding || leftClicking) && client.options.keyAttack.isDown()) {
            client.options.keyAttack.setDown(false);
        }
        if ((rightHolding || rightClicking) && client.options.keyUse.isDown()) {
            client.options.keyUse.setDown(false);
        }

        // Release any movement keys
        if (movingForward && client.options.keyUp.isDown()) {
            client.options.keyUp.setDown(false);
        }
        if (movingBack && client.options.keyDown.isDown()) {
            client.options.keyDown.setDown(false);
        }
        if (movingLeft && client.options.keyLeft.isDown()) {
            client.options.keyLeft.setDown(false);
        }
        if (movingRight && client.options.keyRight.isDown()) {
            client.options.keyRight.setDown(false);
        }

        this.running = false;
        this.holding = false;
        this.leftHolding = false;
        this.rightHolding = false;
        this.leftClicking = false;
        this.rightClicking = false;
        this.movingForward = false;
        this.movingBack = false;
        this.movingLeft = false;
        this.movingRight = false;
        LOGGER.info("Stopped script");
    }

    public boolean isRunning() {
        return running;
    }

    public void tick() {
        if (!running) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            stop();
            return;
        }

        // Release any clicks from previous tick (clicks are always 1 tick duration)
        if (leftClicking) {
            client.options.keyAttack.setDown(false);
            leftClicking = false;
        }
        if (rightClicking) {
            client.options.keyUse.setDown(false);
            rightClicking = false;
        }

        // Release any movement from previous tick (movement is always 1 tick duration)
        if (movingForward) {
            client.options.keyUp.setDown(false);
            movingForward = false;
        }
        if (movingBack) {
            client.options.keyDown.setDown(false);
            movingBack = false;
        }
        if (movingLeft) {
            client.options.keyLeft.setDown(false);
            movingLeft = false;
        }
        if (movingRight) {
            client.options.keyRight.setDown(false);
            movingRight = false;
        }

        // Handle delay
        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        // Release holds if delay is done
        if (holding) {
            if (leftHolding) {
                client.options.keyAttack.setDown(false);
                leftHolding = false;
            }
            if (rightHolding) {
                client.options.keyUse.setDown(false);
                rightHolding = false;
            }
            holding = false;
        }

        // Loop back to beginning when reaching the end
        List<CommandInstruction> instructions = currentScript.getInstructions();
        if (currentInstructionIndex >= instructions.size()) {
            currentInstructionIndex = 0;
        }

        // Execute current instruction
        CommandInstruction instruction = instructions.get(currentInstructionIndex);
        executeInstruction(client, instruction);

        // Set delay and move to next instruction
        delayTicks = instruction.getPostDelay();
        currentInstructionIndex++;
    }

    private void executeInstruction(Minecraft client, CommandInstruction instruction) {
        LocalPlayer player = client.player;
        if (player == null) return;

        switch (instruction.getType()) {
            case LEFT_CLICK -> {
                client.options.keyAttack.setDown(true);
                leftClicking = true;
            }
            case RIGHT_CLICK -> {
                client.options.keyUse.setDown(true);
                rightClicking = true;
            }
            case LEFT_HOLD -> {
                client.options.keyAttack.setDown(true);
                leftHolding = true;
                holding = true;
            }
            case RIGHT_HOLD -> {
                client.options.keyUse.setDown(true);
                rightHolding = true;
                holding = true;
            }
            case BELT_SELECT -> {
                try {
                    int slot = Integer.parseInt(instruction.getParameter());
                    if (slot >= 0 && slot <= 8 && inventorySelectedField != null) {
                        inventorySelectedField.set(player.getInventory(), slot);
                    }
                } catch (NumberFormatException e) {
                    LOGGER.error("Invalid belt slot: {}", instruction.getParameter());
                } catch (Exception e) {
                    LOGGER.error("Failed to set inventory slot", e);
                }
            }
            case PAN_MOUSE -> {
                try {
                    float degrees = Float.parseFloat(instruction.getParameter());
                    float newYaw = player.getYRot() + degrees;
                    player.setYRot(newYaw);
                } catch (NumberFormatException e) {
                    LOGGER.error("Invalid pan angle: {}", instruction.getParameter());
                }
            }
            case TILT_MOUSE -> {
                try {
                    float degrees = Float.parseFloat(instruction.getParameter());
                    float newPitch = player.getXRot() + degrees;
                    player.setXRot(newPitch);
                } catch (NumberFormatException e) {
                    LOGGER.error("Invalid tilt angle: {}", instruction.getParameter());
                }
            }
            case FACE -> {
                String direction = instruction.getParameter().toUpperCase();
                float targetYaw = switch (direction) {
                    case "N" -> 180.0f;
                    case "S" -> 0.0f;
                    case "E" -> -90.0f;
                    case "W" -> 90.0f;
                    default -> player.getYRot();
                };
                player.setYRot(targetYaw);
            }
            case JUMP -> {
                if (player.onGround()) {
                    player.jumpFromGround();
                }
            }
            case CROUCH -> {
                String param = instruction.getParameter().toUpperCase();
                boolean shouldCrouch = param.equals("ON") || param.equals("TRUE");
                client.options.keyShift.setDown(shouldCrouch);
            }
            case FORWARD -> {
                client.options.keyUp.setDown(true);
                movingForward = true;
            }
            case BACK -> {
                client.options.keyDown.setDown(true);
                movingBack = true;
            }
            case LEFT -> {
                client.options.keyLeft.setDown(true);
                movingLeft = true;
            }
            case RIGHT -> {
                client.options.keyRight.setDown(true);
                movingRight = true;
            }
        }
    }
}
