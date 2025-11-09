package ninja.trek.smartclicker.executor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import ninja.trek.smartclicker.command.CommandInstruction;
import ninja.trek.smartclicker.command.CommandType;
import ninja.trek.smartclicker.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ScriptExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptExecutor.class);

    private Script currentScript;
    private int currentInstructionIndex;
    private int delayTicks;
    private boolean running;
    private boolean holding;
    private boolean leftHolding;
    private boolean rightHolding;

    public ScriptExecutor() {
        this.running = false;
        this.holding = false;
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
        LOGGER.info("Started script: {}", script.getName());
    }

    public void stop() {
        if (!running) return;

        MinecraftClient client = MinecraftClient.getInstance();

        // Release any held buttons
        if (leftHolding && client.options.attackKey.isPressed()) {
            client.options.attackKey.setPressed(false);
        }
        if (rightHolding && client.options.useKey.isPressed()) {
            client.options.useKey.setPressed(false);
        }

        this.running = false;
        this.holding = false;
        this.leftHolding = false;
        this.rightHolding = false;
        LOGGER.info("Stopped script");
    }

    public boolean isRunning() {
        return running;
    }

    public void tick() {
        if (!running) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            stop();
            return;
        }

        // Handle delay
        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        // Release holds if delay is done
        if (holding) {
            if (leftHolding) {
                client.options.attackKey.setPressed(false);
                leftHolding = false;
            }
            if (rightHolding) {
                client.options.useKey.setPressed(false);
                rightHolding = false;
            }
            holding = false;
        }

        // Check if script is complete
        List<CommandInstruction> instructions = currentScript.getInstructions();
        if (currentInstructionIndex >= instructions.size()) {
            stop();
            return;
        }

        // Execute current instruction
        CommandInstruction instruction = instructions.get(currentInstructionIndex);
        executeInstruction(client, instruction);

        // Set delay and move to next instruction
        delayTicks = instruction.getPostDelay();
        currentInstructionIndex++;
    }

    private void executeInstruction(MinecraftClient client, CommandInstruction instruction) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        switch (instruction.getType()) {
            case LEFT_CLICK -> {
                client.options.attackKey.setPressed(true);
                client.options.attackKey.setPressed(false);
            }
            case RIGHT_CLICK -> {
                client.options.useKey.setPressed(true);
                client.options.useKey.setPressed(false);
            }
            case LEFT_HOLD -> {
                client.options.attackKey.setPressed(true);
                leftHolding = true;
                holding = true;
            }
            case RIGHT_HOLD -> {
                client.options.useKey.setPressed(true);
                rightHolding = true;
                holding = true;
            }
            case BELT_SELECT -> {
                try {
                    int slot = Integer.parseInt(instruction.getParameter());
                    if (slot >= 0 && slot <= 8) {
                        player.getInventory().selectedSlot = slot;
                    }
                } catch (NumberFormatException e) {
                    LOGGER.error("Invalid belt slot: {}", instruction.getParameter());
                }
            }
            case PAN_MOUSE -> {
                try {
                    float degrees = Float.parseFloat(instruction.getParameter());
                    float newYaw = player.getYaw() + degrees;
                    player.setYaw(newYaw);
                    if (client.getNetworkHandler() != null) {
                        player.networkHandler.sendChatCommand(""); // Trigger update
                    }
                } catch (NumberFormatException e) {
                    LOGGER.error("Invalid pan angle: {}", instruction.getParameter());
                }
            }
            case TILT_MOUSE -> {
                try {
                    float degrees = Float.parseFloat(instruction.getParameter());
                    float newPitch = player.getPitch() + degrees;
                    player.setPitch(newPitch);
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
                    default -> player.getYaw();
                };
                player.setYaw(targetYaw);
            }
            case JUMP -> {
                if (player.isOnGround()) {
                    player.jump();
                }
            }
            case CROUCH -> {
                String param = instruction.getParameter().toUpperCase();
                boolean shouldCrouch = param.equals("ON") || param.equals("TRUE");
                client.options.sneakKey.setPressed(shouldCrouch);
            }
        }
    }
}
