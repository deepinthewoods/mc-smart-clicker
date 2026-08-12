package ninja.trek.smartclicker.executor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import ninja.trek.smartclicker.SmartClickerClient;
import ninja.trek.smartclicker.command.CommandInstruction;
import ninja.trek.smartclicker.command.CommandType;
import ninja.trek.smartclicker.mixin.client.InventoryAccessor;
import ninja.trek.smartclicker.mixin.client.MinecraftAccessor;
import ninja.trek.smartclicker.script.Script;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScriptExecutor {

    private Script currentScript;
    private int currentInstructionIndex;
    private int delayTicks;
    private boolean running;
    private boolean leftHolding;
    private boolean rightHolding;
    private boolean leftClicking;
    private boolean rightClicking;
    private boolean rightHoldOnlyIfHungry;
    private int leftHoldRemainingTicks;
    private int rightHoldRemainingTicks;
    private long leftHoldRemainingMillis;
    private long rightHoldRemainingMillis;
    private boolean movingForward;
    private boolean movingBack;
    private boolean movingLeft;
    private boolean movingRight;
    private boolean ignoreAttackClickThisTick;
    private boolean ignoreUseClickThisTick;
    private boolean skipPostDelayThisInstruction;

    private TradeTask tradeTask;
    private int tradeTaskPostDelay;

    // Track game time to sync with tick rate changes
    private long lastGameTime;

    // Real-world timing fields
    private long delayMillis;
    private long lastRealWorldTime;

    // Track what tool type should be in each hotbar slot (for SWAP_TOOL)
    private final Map<Integer, net.minecraft.world.item.Item> expectedToolTypePerSlot = new HashMap<>();

    // Track durability threshold per slot (for SWAP_TOOL)
    private final Map<Integer, Integer> durabilityThresholdPerSlot = new HashMap<>();

    // Default durability threshold for automatic weapon swapping during attacks
    private static final int DEFAULT_WEAPON_SWAP_THRESHOLD = 10;

    public ScriptExecutor() {
        this.running = false;
    }

    public void startScript(Script script) {
        if (script == null || script.getInstructions().isEmpty()) {
            return;
        }

        this.currentScript = script;
        this.currentInstructionIndex = 0;
        this.delayTicks = 0;
        this.delayMillis = 0;
        this.running = true;
        this.leftHolding = false;
        this.rightHolding = false;
        this.leftClicking = false;
        this.rightClicking = false;
        this.rightHoldOnlyIfHungry = false;
        this.leftHoldRemainingTicks = 0;
        this.rightHoldRemainingTicks = 0;
        this.leftHoldRemainingMillis = 0;
        this.rightHoldRemainingMillis = 0;
        this.movingForward = false;
        this.movingBack = false;
        this.movingLeft = false;
        this.movingRight = false;
        this.ignoreAttackClickThisTick = false;
        this.ignoreUseClickThisTick = false;
        this.skipPostDelayThisInstruction = false;
        this.lastGameTime = 0; // Will be initialized on first tick
        this.lastRealWorldTime = 0; // Will be initialized on first tick
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
        this.leftHolding = false;
        this.rightHolding = false;
        this.leftClicking = false;
        this.rightClicking = false;
        this.rightHoldOnlyIfHungry = false;
        this.leftHoldRemainingTicks = 0;
        this.rightHoldRemainingTicks = 0;
        this.leftHoldRemainingMillis = 0;
        this.rightHoldRemainingMillis = 0;
        this.movingForward = false;
        this.movingBack = false;
        this.movingLeft = false;
        this.movingRight = false;
        this.tradeTask = null;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean shouldIgnoreAttackClick() {
        return ignoreAttackClickThisTick;
    }

    public boolean shouldIgnoreUseClick() {
        return ignoreUseClickThisTick;
    }

    public void tick() {
        if (!running) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            stop();
            return;
        }

        // Stop script if player dies
        if (client.player.isDeadOrDying()) {
            stop();
            return;
        }

        // Determine timing mode
        boolean useRealWorldTiming = SmartClickerClient.getConfig().isUseRealWorldTiming();

        // Track game time to sync with tick rate changes (for game-tick timing)
        long currentGameTime = client.level.getGameTime();
        long gameTicksPassed = 1; // Default to 1 tick
        if (lastGameTime != 0) {
            gameTicksPassed = currentGameTime - lastGameTime;
        }
        lastGameTime = currentGameTime;

        // Track real-world time (for real-world timing)
        long currentRealWorldTime = System.currentTimeMillis();
        long realWorldTimePassed = 0;
        if (lastRealWorldTime != 0) {
            realWorldTimePassed = currentRealWorldTime - lastRealWorldTime;
        }
        lastRealWorldTime = currentRealWorldTime;

        ignoreAttackClickThisTick = false;
        ignoreUseClickThisTick = false;
        skipPostDelayThisInstruction = false;

        updateHoldTimers(client, useRealWorldTiming, gameTicksPassed, realWorldTimePassed);

        if (leftHolding) {
            // Continue attacking every tick - this bypasses target checks
            ((MinecraftAccessor) client).invokeContinueAttack(true);
            ignoreAttackClickThisTick = true;
            // Check and swap weapon if damaged/broken during continuous attacks
            if (currentScript.isReplaceTools()) {
                checkAndSwapWeapon(client, client.player);
            }
        }
        if (rightHolding) {
            // Keep the key down so items that check for continuous use work properly
            client.options.keyUse.setDown(true);
            ignoreUseClickThisTick = true;
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

        // Auto-replace tools if enabled
        if (currentScript.isReplaceTools()) {
            checkAndReplaceTool(client, client.player);
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

        // Handle delay based on timing mode
        if (useRealWorldTiming) {
            // Real-world timing mode: use milliseconds
            if (delayMillis > 0) {
                delayMillis -= realWorldTimePassed;
                if (delayMillis > 0) {
                    return;
                }
            }
        } else {
            // Game-tick timing mode: use game ticks
            if (delayTicks > 0) {
                delayTicks -= (int) gameTicksPassed;
                if (delayTicks > 0) {
                    return;
                }
            }
        }

        // Loop back to beginning when reaching the end
        List<CommandInstruction> instructions = currentScript.getInstructions();
        if (currentInstructionIndex >= instructions.size()) {
            currentInstructionIndex = 0;
        }

        // Continue any in-progress trade task.
        if (tradeTask != null) {
            if (tradeTask.tick(client, (int) gameTicksPassed)) {
                tradeTask = null;
                if (useRealWorldTiming) {
                    delayMillis = tradeTaskPostDelay * 50L; // Convert ticks to milliseconds (20 TPS = 50ms per tick)
                } else {
                    delayTicks = tradeTaskPostDelay;
                }
                currentInstructionIndex++;
            }
            return;
        }

        // Execute current instruction
        CommandInstruction instruction = instructions.get(currentInstructionIndex);
        if (instruction.getType() == CommandType.BUY || instruction.getType() == CommandType.SELL) {
            tradeTaskPostDelay = instruction.getPostDelay();
            tradeTask = new TradeTask(instruction.getType(), instruction.getParameter(), instruction.getAmount());
            if (tradeTask.tick(client, (int) gameTicksPassed)) {
                tradeTask = null;
                if (useRealWorldTiming) {
                    delayMillis = tradeTaskPostDelay * 50L; // Convert ticks to milliseconds
                } else {
                    delayTicks = tradeTaskPostDelay;
                }
                currentInstructionIndex++;
            }
            return;
        }

        executeInstruction(client, instruction);

        // Set delay and move to next instruction
        if (skipPostDelayThisInstruction) {
            delayMillis = 0;
            delayTicks = 0;
        } else if (useRealWorldTiming) {
            delayMillis = instruction.getPostDelay() * 50L; // Convert ticks to milliseconds (20 TPS = 50ms per tick)
        } else {
            delayTicks = instruction.getPostDelay();
        }
        currentInstructionIndex++;
    }

    private void executeInstruction(Minecraft client, CommandInstruction instruction) {
        LocalPlayer player = client.player;
        if (player == null) return;

        switch (instruction.getType()) {
            case LEFT_CLICK -> {
                // Directly invoke Minecraft's attack method - works even without a target
                ((MinecraftAccessor) client).invokeStartAttack();
                leftClicking = true;
                ignoreAttackClickThisTick = true;
                // Check and swap weapon if damaged/broken after attack
                if (currentScript.isReplaceTools()) {
                    checkAndSwapWeapon(client, player);
                }
            }
            case RIGHT_CLICK -> {
                // Directly invoke Minecraft's use item method - works even without a target
                ((MinecraftAccessor) client).invokeStartUseItem();
                rightClicking = true;
                ignoreUseClickThisTick = true;
            }
            case LEFT_HOLD -> {
                if (!leftHolding) {
                    // Start the attack on first hold
                    ((MinecraftAccessor) client).invokeStartAttack();
                    ignoreAttackClickThisTick = true;
                    // Check and swap weapon if damaged/broken after initial attack
                    if (currentScript.isReplaceTools()) {
                        checkAndSwapWeapon(client, player);
                    }
                }
                leftHolding = true;
                setHoldDuration(true, instruction.getPostDelay());
            }
            case RIGHT_HOLD -> {
                if (!rightHolding) {
                    // Start using the item on first hold
                    ((MinecraftAccessor) client).invokeStartUseItem();
                    client.options.keyUse.setDown(true);
                    ignoreUseClickThisTick = true;
                }
                rightHolding = true;
                rightHoldOnlyIfHungry = false;
                setHoldDuration(false, instruction.getPostDelay());
            }
            case RIGHT_IF_HUNGRY -> {
                if (player.getFoodData().getFoodLevel() < 20) {
                    if (!rightHolding) {
                        // Start using the item on first hold
                        ((MinecraftAccessor) client).invokeStartUseItem();
                        client.options.keyUse.setDown(true);
                        ignoreUseClickThisTick = true;
                    }
                    rightHolding = true;
                    rightHoldOnlyIfHungry = true;
                    setHoldDuration(false, instruction.getPostDelay());
                } else {
                    // Player is not hungry - stop holding if we were, and skip post-delay
                    if (rightHolding && rightHoldOnlyIfHungry) {
                        rightHolding = false;
                        rightHoldOnlyIfHungry = false;
                        rightHoldRemainingTicks = 0;
                        rightHoldRemainingMillis = 0;
                        if (client.options.keyUse.isDown()) {
                            client.options.keyUse.setDown(false);
                        }
                    }
                    skipPostDelayThisInstruction = true;
                }
            }
            case BELT_SELECT -> {
                try {
                    int slot = Integer.parseInt(instruction.getParameter());
                    if (slot >= 0 && slot <= 8) {
                        ((InventoryAccessor) player.getInventory()).setSelected(slot);
                    }
                } catch (NumberFormatException e) {
                }
            }
            case PAN_MOUSE -> {
                try {
                    float degrees = Float.parseFloat(instruction.getParameter());
                    float newYaw = player.getYRot() + degrees;
                    player.setYRot(newYaw);
                    // Also update camera rotation if in freecam mode
                    CraneshotCompatibility.updateCameraRotation(degrees, 0);
                } catch (NumberFormatException e) {
                }
            }
            case TILT_MOUSE -> {
                try {
                    float degrees = Float.parseFloat(instruction.getParameter());
                    float newPitch = player.getXRot() + degrees;
                    player.setXRot(newPitch);
                    // Also update camera rotation if in freecam mode
                    CraneshotCompatibility.updateCameraRotation(0, -degrees);
                } catch (NumberFormatException e) {
                }
            }
            case FACE -> {
                String direction = instruction.getParameter().toUpperCase();
                float currentYaw = player.getYRot();
                float targetYaw = switch (direction) {
                    case "N" -> 180.0f;
                    case "S" -> 0.0f;
                    case "E" -> -90.0f;
                    case "W" -> 90.0f;
                    default -> currentYaw;
                };
                float delta = targetYaw - currentYaw;
                player.setYRot(targetYaw);
                // Also update camera rotation if in freecam mode
                CraneshotCompatibility.updateCameraRotation(delta, 0);
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
            case MOVE -> {
                String direction = instruction.getParameter().toLowerCase();
                switch (direction) {
                    case "w" -> {
                        client.options.keyUp.setDown(true);
                        movingForward = true;
                    }
                    case "s" -> {
                        client.options.keyDown.setDown(true);
                        movingBack = true;
                    }
                    case "a" -> {
                        client.options.keyLeft.setDown(true);
                        movingLeft = true;
                    }
                    case "d" -> {
                        client.options.keyRight.setDown(true);
                        movingRight = true;
                    }
                    default -> {}
                }
            }
            case PAN_ABSOLUTE -> {
                try {
                    float targetYaw = Float.parseFloat(instruction.getParameter());
                    float currentYaw = player.getYRot();
                    float delta = targetYaw - currentYaw;
                    player.setYRot(targetYaw);
                    // Also update camera rotation if in freecam mode
                    CraneshotCompatibility.updateCameraRotation(delta, 0);
                } catch (NumberFormatException e) {
                }
            }
            case TILT_ABSOLUTE -> {
                try {
                    float targetPitch = Float.parseFloat(instruction.getParameter());
                    float currentPitch = player.getXRot();
                    float delta = targetPitch - currentPitch;
                    player.setXRot(targetPitch);
                    // Also update camera rotation if in freecam mode
                    CraneshotCompatibility.updateCameraRotation(0, -delta);
                } catch (NumberFormatException e) {
                }
            }
            case SWAP_TOOL -> {
                try {
                    int durabilityThreshold = Integer.parseInt(instruction.getParameter());
                    Inventory inventory = player.getInventory();

                    // Get current hotbar slot
                    int currentSlot = ((InventoryAccessor) inventory).getSelected();
                    ItemStack currentItem = inventory.getItem(currentSlot);

                    // Step 1: Remember what tool type should be in this slot and its threshold
                    if (!currentItem.isEmpty() && currentItem.isDamageableItem()) {
                        expectedToolTypePerSlot.put(currentSlot, currentItem.getItem());
                        durabilityThresholdPerSlot.put(currentSlot, durabilityThreshold);
                    }

                    // Step 2: Check if current item is the expected tool type, restore if not
                    net.minecraft.world.item.Item expectedTool = expectedToolTypePerSlot.get(currentSlot);
                    if (expectedTool != null && (currentItem.isEmpty() || currentItem.getItem() != expectedTool)) {
                        // Current item is not the expected tool, try to restore it from hotbar or inventory
                        // Search hotbar slots first (0-8), then main inventory (9-35)
                        for (int i = 0; i < 36; i++) {
                            if (i == currentSlot) continue; // Skip the current slot
                            ItemStack candidateItem = inventory.getItem(i);
                            if (!candidateItem.isEmpty() && candidateItem.getItem() == expectedTool) {
                                // Found expected tool, swap it in (server-synced)
                                swapInventorySlots(client, player, currentSlot, i);
                                currentItem = inventory.getItem(currentSlot); // Update reference
                                break;
                            }
                        }
                    }

                    // Step 3: Check if current tool has low durability and needs swapping
                    if (!currentItem.isEmpty() && currentItem.isDamageableItem()) {
                        int remainingDurability = currentItem.getMaxDamage() - currentItem.getDamageValue();

                        // Only swap if durability is below threshold
                        if (remainingDurability <= durabilityThreshold) {
                            boolean swapped = false;
                            int bestSlot = -1;
                            int bestDurability = remainingDurability;

                            // Search hotbar (slots 0-8) and main inventory (slots 9-35) for replacement
                            for (int i = 0; i < 36; i++) {
                                if (i == currentSlot) continue; // Skip the current slot
                                ItemStack candidateItem = inventory.getItem(i);

                                // Check if it's the same item type
                                if (!candidateItem.isEmpty() && ItemStack.isSameItem(currentItem, candidateItem)) {
                                    int candidateDurability = candidateItem.getMaxDamage() - candidateItem.getDamageValue();

                                    // Pick the best candidate above the threshold and current durability.
                                    if (candidateDurability > durabilityThreshold && candidateDurability > bestDurability) {
                                        bestDurability = candidateDurability;
                                        bestSlot = i;
                                    }
                                }
                            }

                            if (bestSlot >= 0) {
                                swapped = swapInventorySlots(client, player, currentSlot, bestSlot);
                                if (swapped) {
                                }
                            }

                            // If no replacement found, move current tool to empty slot
                            if (!swapped) {
                                for (int i = 9; i < 36; i++) {
                                    ItemStack slotItem = inventory.getItem(i);
                                    if (slotItem.isEmpty()) {
                                        // Move current tool to empty slot (server-synced)
                                        swapInventorySlots(client, player, currentSlot, i);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } catch (NumberFormatException e) {
                } catch (Exception e) {
                }
            }
            case REFILL_SLOT -> refillSelectedSlot(client, player);
            case DROP_ITEM -> {
                try {
                    int count = Integer.parseInt(instruction.getParameter());
                    if (count >= 64) {
                        player.drop(true);
                    } else {
                        for (int i = 0; i < count; i++) {
                            player.drop(false);
                        }
                    }
                } catch (NumberFormatException e) {
                }
            }
        }
    }

    private void setHoldDuration(boolean isLeftHold, int holdTicks) {
        boolean useRealWorldTiming = SmartClickerClient.getConfig().isUseRealWorldTiming();
        if (useRealWorldTiming) {
            long holdMillis = holdTicks * 50L;
            if (isLeftHold) {
                leftHoldRemainingMillis = holdMillis;
                leftHoldRemainingTicks = 0;
            } else {
                rightHoldRemainingMillis = holdMillis;
                rightHoldRemainingTicks = 0;
            }
        } else {
            if (isLeftHold) {
                leftHoldRemainingTicks = holdTicks;
                leftHoldRemainingMillis = 0;
            } else {
                rightHoldRemainingTicks = holdTicks;
                rightHoldRemainingMillis = 0;
            }
        }
    }

    private void updateHoldTimers(Minecraft client, boolean useRealWorldTiming, long gameTicksPassed, long realWorldTimePassed) {
        if (rightHolding && rightHoldOnlyIfHungry) {
            LocalPlayer player = client.player;
            if (player == null || player.getFoodData().getFoodLevel() >= 20) {
                rightHoldRemainingTicks = 0;
                rightHoldRemainingMillis = 0;
                rightHolding = false;
                rightHoldOnlyIfHungry = false;
                if (client.options.keyUse.isDown()) {
                    client.options.keyUse.setDown(false);
                }
            }
        }

        if (leftHolding) {
            if (useRealWorldTiming) {
                if (leftHoldRemainingMillis > 0) {
                    leftHoldRemainingMillis -= realWorldTimePassed;
                    if (leftHoldRemainingMillis <= 0) {
                        leftHoldRemainingMillis = 0;
                        leftHolding = false;
                        if (client.options.keyAttack.isDown()) {
                            client.options.keyAttack.setDown(false);
                        }
                    }
                }
            } else if (leftHoldRemainingTicks > 0) {
                leftHoldRemainingTicks -= (int) gameTicksPassed;
                if (leftHoldRemainingTicks <= 0) {
                    leftHoldRemainingTicks = 0;
                    leftHolding = false;
                    if (client.options.keyAttack.isDown()) {
                        client.options.keyAttack.setDown(false);
                    }
                }
            }
        }

        if (rightHolding) {
            if (useRealWorldTiming) {
                if (rightHoldRemainingMillis > 0) {
                    rightHoldRemainingMillis -= realWorldTimePassed;
                    if (rightHoldRemainingMillis <= 0) {
                        rightHoldRemainingMillis = 0;
                        rightHolding = false;
                        rightHoldOnlyIfHungry = false;
                        if (client.options.keyUse.isDown()) {
                            client.options.keyUse.setDown(false);
                        }
                    }
                }
            } else if (rightHoldRemainingTicks > 0) {
                rightHoldRemainingTicks -= (int) gameTicksPassed;
                if (rightHoldRemainingTicks <= 0) {
                    rightHoldRemainingTicks = 0;
                    rightHolding = false;
                    rightHoldOnlyIfHungry = false;
                    if (client.options.keyUse.isDown()) {
                        client.options.keyUse.setDown(false);
                    }
                }
            }
        }
    }

    private static boolean swapInventorySlots(Minecraft client, LocalPlayer player, int inventorySlotA, int inventorySlotB) {
        if (client.gameMode == null) {
            return false;
        }

        if (!player.inventoryMenu.getCarried().isEmpty()) {
            return false;
        }

        int slotA = toMenuSlotIndex(inventorySlotA);
        int slotB = toMenuSlotIndex(inventorySlotB);
        if (slotA < 0 || slotB < 0) {
            return false;
        }

        int containerId = player.inventoryMenu.containerId;
        client.gameMode.handleInventoryMouseClick(containerId, slotA, 0, ClickType.PICKUP, player);
        client.gameMode.handleInventoryMouseClick(containerId, slotB, 0, ClickType.PICKUP, player);
        client.gameMode.handleInventoryMouseClick(containerId, slotA, 0, ClickType.PICKUP, player);
        return true;
    }

    private static int toMenuSlotIndex(int inventoryIndex) {
        if (inventoryIndex >= 0 && inventoryIndex <= 8) {
            return inventoryIndex + 36;
        }
        if (inventoryIndex >= 9 && inventoryIndex <= 35) {
            return inventoryIndex;
        }
        return -1;
    }

    private static void refillSelectedSlot(Minecraft client, LocalPlayer player) {
        if (client.gameMode == null) {
            return;
        }

        if (!player.inventoryMenu.getCarried().isEmpty()) {
            return;
        }

        Inventory inventory = player.getInventory();
        int selectedSlot = ((InventoryAccessor) inventory).getSelected();
        ItemStack target = inventory.getItem(selectedSlot);
        if (target.isEmpty() || !target.isStackable()) {
            return;
        }

        int targetMax = target.getMaxStackSize();
        if (target.getCount() >= targetMax) {
            return;
        }

        int targetMenuSlot = toMenuSlotIndex(selectedSlot);
        if (targetMenuSlot < 0) {
            return;
        }

        int containerId = player.inventoryMenu.containerId;
        int remaining = targetMax - target.getCount();

        for (int i = 9; i <= 35 && remaining > 0; i++) {
            ItemStack candidate = inventory.getItem(i);
            if (candidate.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(candidate, target)) continue;

            int sourceMenuSlot = toMenuSlotIndex(i);
            if (sourceMenuSlot < 0) continue;

            client.gameMode.handleInventoryMouseClick(containerId, sourceMenuSlot, 0, ClickType.PICKUP, player);
            client.gameMode.handleInventoryMouseClick(containerId, targetMenuSlot, 0, ClickType.PICKUP, player);
            if (!player.inventoryMenu.getCarried().isEmpty()) {
                client.gameMode.handleInventoryMouseClick(containerId, sourceMenuSlot, 0, ClickType.PICKUP, player);
            }

            ItemStack updatedTarget = inventory.getItem(selectedSlot);
            remaining = updatedTarget.getMaxStackSize() - updatedTarget.getCount();
        }
    }

    /**
     * Automatically checks and replaces any tool/weapon when enabled via the Replace Tools checkbox.
     * Uses similar logic to SWAP_TOOL with configurable threshold.
     */
    private void checkAndReplaceTool(Minecraft client, LocalPlayer player) {
        if (player == null || currentScript == null) return;

        try {
            Inventory inventory = player.getInventory();
            int currentSlot = ((InventoryAccessor) inventory).getSelected();
            ItemStack currentItem = inventory.getItem(currentSlot);

            // Get threshold from script settings
            int threshold = currentScript.getReplaceToolsThreshold();

            // Only track damageable items (tools, weapons, armor)
            if (!currentItem.isEmpty() && currentItem.isDamageableItem()) {
                // Remember what tool type should be in this slot
                if (!expectedToolTypePerSlot.containsKey(currentSlot)) {
                    expectedToolTypePerSlot.put(currentSlot, currentItem.getItem());
                    // Also update the threshold for this slot
                    durabilityThresholdPerSlot.put(currentSlot, threshold);
                }
            }

            // Check if current item is the expected tool type, restore if not
            net.minecraft.world.item.Item expectedTool = expectedToolTypePerSlot.get(currentSlot);
            if (expectedTool != null && (currentItem.isEmpty() || currentItem.getItem() != expectedTool)) {
                // Current item is not the expected tool, try to restore it from hotbar or inventory
                for (int i = 0; i < 36; i++) {
                    if (i == currentSlot) continue;
                    ItemStack candidateItem = inventory.getItem(i);
                    if (!candidateItem.isEmpty() && candidateItem.getItem() == expectedTool) {
                        // Found expected tool, swap it in
                        swapInventorySlots(client, player, currentSlot, i);
                        currentItem = inventory.getItem(currentSlot);
                        break;
                    }
                }
            }

            // Check if current tool has low durability and needs swapping
            if (!currentItem.isEmpty() && currentItem.isDamageableItem()) {
                int remainingDurability = currentItem.getMaxDamage() - currentItem.getDamageValue();

                // Only swap if durability is at or below threshold
                if (remainingDurability <= threshold) {
                    boolean swapped = false;
                    int bestSlot = -1;
                    int bestDurability = remainingDurability;

                    // Search for replacement with better durability
                    for (int i = 0; i < 36; i++) {
                        if (i == currentSlot) continue;
                        ItemStack candidateItem = inventory.getItem(i);

                        if (!candidateItem.isEmpty() && ItemStack.isSameItem(currentItem, candidateItem)) {
                            int candidateDurability = candidateItem.getMaxDamage() - candidateItem.getDamageValue();

                            // Accept any replacement with higher durability than current
                            if (candidateDurability > remainingDurability && candidateDurability > bestDurability) {
                                bestDurability = candidateDurability;
                                bestSlot = i;
                            }
                        }
                    }

                    if (bestSlot >= 0) {
                        swapped = swapInventorySlots(client, player, currentSlot, bestSlot);
                    }

                    // If no replacement found and tool is broken (0 durability), move it to empty slot
                    if (!swapped && remainingDurability <= 0) {
                        for (int i = 9; i < 36; i++) {
                            ItemStack slotItem = inventory.getItem(i);
                            if (slotItem.isEmpty()) {
                                swapInventorySlots(client, player, currentSlot, i);
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    /**
     * Automatically checks and swaps weapons when they're damaged/broken during attacks.
     * Uses similar logic to SWAP_TOOL.
     */
    private void checkAndSwapWeapon(Minecraft client, LocalPlayer player) {
        if (player == null) return;

        try {
            Inventory inventory = player.getInventory();
            int currentSlot = ((InventoryAccessor) inventory).getSelected();
            ItemStack currentItem = inventory.getItem(currentSlot);

            // Step 1: Remember what weapon type should be in this slot
            if (!currentItem.isEmpty() && currentItem.isDamageableItem()) {
                expectedToolTypePerSlot.put(currentSlot, currentItem.getItem());
            }

            // Step 2: Check if current item is the expected weapon type, restore if not
            net.minecraft.world.item.Item expectedWeapon = expectedToolTypePerSlot.get(currentSlot);
            if (expectedWeapon != null && (currentItem.isEmpty() || currentItem.getItem() != expectedWeapon)) {
                // Current item is not the expected weapon, try to restore it from hotbar or inventory
                // Search hotbar slots first (0-8), then main inventory (9-35)
                for (int i = 0; i < 36; i++) {
                    if (i == currentSlot) continue; // Skip the current slot
                    ItemStack candidateItem = inventory.getItem(i);
                    if (!candidateItem.isEmpty() && candidateItem.getItem() == expectedWeapon) {
                        // Found expected weapon, swap it in (server-synced)
                        swapInventorySlots(client, player, currentSlot, i);
                        currentItem = inventory.getItem(currentSlot); // Update reference
                        break;
                    }
                }
            }

            // Step 3: Check if current weapon has low durability and needs swapping
            if (!currentItem.isEmpty() && currentItem.isDamageableItem()) {
                int remainingDurability = currentItem.getMaxDamage() - currentItem.getDamageValue();

                // Get the threshold for this slot, default to 10 if not set via SWAP_TOOL
                int threshold = durabilityThresholdPerSlot.getOrDefault(currentSlot, DEFAULT_WEAPON_SWAP_THRESHOLD);

                // Only swap if durability is below threshold or broken
                if (remainingDurability <= threshold) {
                    boolean swapped = false;
                    int bestSlot = -1;
                    int bestDurability = remainingDurability;

                    // Search hotbar (slots 0-8) and main inventory (slots 9-35) for replacement
                    for (int i = 0; i < 36; i++) {
                        if (i == currentSlot) continue; // Skip the current slot
                        ItemStack candidateItem = inventory.getItem(i);

                        // Check if it's the same item type
                        if (!candidateItem.isEmpty() && ItemStack.isSameItem(currentItem, candidateItem)) {
                            int candidateDurability = candidateItem.getMaxDamage() - candidateItem.getDamageValue();

                            // Pick the best candidate above the threshold and current durability.
                            if (candidateDurability > threshold && candidateDurability > bestDurability) {
                                bestDurability = candidateDurability;
                                bestSlot = i;
                            }
                        }
                    }

                    if (bestSlot >= 0) {
                        swapped = swapInventorySlots(client, player, currentSlot, bestSlot);
                    }

                    // If no replacement found, move current weapon to empty slot
                    if (!swapped && remainingDurability <= 0) {
                        for (int i = 9; i < 36; i++) {
                            ItemStack slotItem = inventory.getItem(i);
                            if (slotItem.isEmpty()) {
                                // Move broken weapon to empty slot (server-synced)
                                swapInventorySlots(client, player, currentSlot, i);
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    private static final class TradeTask {
        private static final int MAX_WAIT_TICKS_FOR_SCREEN = 60;
        private static final int ACTION_COOLDOWN_TICKS = 2;

        private final CommandType mode;
        private final String itemIdText;
        private final Identifier itemId;
        private final int targetTrades;

        private int stage = 0;
        private int waitTicks = 0;
        private int actionCooldown = 0;

        private int selectedOfferIndex = -1;
        private int tradesCompleted = 0;

        private TradeTask(CommandType mode, String itemIdText, int targetTrades) {
            this.mode = mode;
            this.itemIdText = itemIdText == null ? "" : itemIdText.trim();
            this.itemId = Identifier.tryParse(this.itemIdText);
            this.targetTrades = Math.max(0, targetTrades);
        }

        public boolean tick(Minecraft client, int gameTicksPassed) {
            LocalPlayer player = client.player;
            if (player == null) return true;

            if (this.itemId == null) {
                return true;
            }

            if (actionCooldown > 0) {
                actionCooldown -= gameTicksPassed;
                if (actionCooldown > 0) {
                    return false;
                }
            }

            return switch (stage) {
                case 0 -> {
                    if (!(client.screen instanceof MerchantScreen)) {
                        AbstractVillager villager = getPointedVillager(client);
                        if (villager == null) {
                            yield true;
                        }

                        if (client.gameMode == null) {
                            yield true;
                        }

                        InteractionResult result = client.gameMode.interact(player, villager, InteractionHand.MAIN_HAND);
                        player.swing(InteractionHand.MAIN_HAND);
                        if (result.consumesAction()) {
                            stage = 1;
                            waitTicks = 0;
                            actionCooldown = ACTION_COOLDOWN_TICKS;
                            yield false;
                        }

                        stage = 1;
                        waitTicks = 0;
                        actionCooldown = ACTION_COOLDOWN_TICKS;
                        yield false;
                    }

                    stage = 2;
                    yield false;
                }
                case 1 -> {
                    if (client.screen instanceof MerchantScreen && player.containerMenu instanceof MerchantMenu) {
                        stage = 2;
                        yield false;
                    }

                    waitTicks += gameTicksPassed;
                    if (waitTicks > MAX_WAIT_TICKS_FOR_SCREEN) {
                        yield true;
                    }

                    yield false;
                }
                case 2 -> {
                    MerchantMenu menu = getMerchantMenu(player);
                    if (menu == null) {
                        stage = 1;
                        waitTicks = 0;
                        yield false;
                    }

                    MerchantOffers offers = menu.getOffers();
                    if (offers.isEmpty()) {
                        waitTicks += gameTicksPassed;
                        if (waitTicks > MAX_WAIT_TICKS_FOR_SCREEN) {
                            closeScreen(client, player);
                            yield true;
                        }
                        yield false;
                    }

                    selectedOfferIndex = pickBestOfferIndex(player.getInventory(), offers, mode, itemId);
                    if (selectedOfferIndex < 0) {
                        closeScreen(client, player);
                        yield true;
                    }

                    stage = 3;
                    yield false;
                }
                case 3 -> {
                    MerchantMenu menu = getMerchantMenu(player);
                    if (menu == null) {
                        closeScreen(client, player);
                        yield true;
                    }

                    MerchantOffers offers = menu.getOffers();
                    if (offers.isEmpty()) {
                        closeScreen(client, player);
                        yield true;
                    }

                    if (selectedOfferIndex < 0 || selectedOfferIndex >= offers.size()) {
                        selectedOfferIndex = pickBestOfferIndex(player.getInventory(), offers, mode, itemId);
                        if (selectedOfferIndex < 0) {
                            closeScreen(client, player);
                            yield true;
                        }
                    }

                    MerchantOffer offer = offers.get(selectedOfferIndex);

                    if (!matchesOffer(mode, itemId, offer) || offer.isOutOfStock() || !canPerformOneTradeWithoutOverflow(player.getInventory(), offer)) {
                        int newOfferIndex = pickBestOfferIndex(player.getInventory(), offers, mode, itemId);
                        if (newOfferIndex >= 0 && newOfferIndex != selectedOfferIndex) {
                            selectedOfferIndex = newOfferIndex;
                            offer = offers.get(selectedOfferIndex);
                        } else {
                            closeScreen(client, player);
                            yield true;
                        }
                    }

                    // For targetTrades == -1, stop if this trade would leave only 1 use remaining
                    if (targetTrades == -1) {
                        int remainingAfterThisTrade = offer.getMaxUses() - offer.getUses() - 1;
                        if (remainingAfterThisTrade <= 1) {
                            // Perform this final trade, then stop
                            if (!clearPaymentSlots(menu)) {
                                closeScreen(client, player);
                                yield true;
                            }
                            selectOffer(client, menu, selectedOfferIndex);
                            if (fillPaymentSlotsExact(menu, offer) && offer.satisfiedBy(menu.getSlot(0).getItem(), menu.getSlot(1).getItem())) {
                                if (client.gameMode != null) {
                                    client.gameMode.handleInventoryMouseClick(menu.containerId, 2, 0, ClickType.QUICK_MOVE, player);
                                }
                            }
                            closeScreen(client, player);
                            yield true;
                        }
                    }

                    if (!clearPaymentSlots(menu)) {
                        closeScreen(client, player);
                        yield true;
                    }

                    selectOffer(client, menu, selectedOfferIndex);

                    if (!fillPaymentSlotsExact(menu, offer) || !offer.satisfiedBy(menu.getSlot(0).getItem(), menu.getSlot(1).getItem())) {
                        int newOfferIndex = pickBestOfferIndex(player.getInventory(), offers, mode, itemId);
                        if (newOfferIndex >= 0 && newOfferIndex != selectedOfferIndex) {
                            selectedOfferIndex = newOfferIndex;
                            actionCooldown = ACTION_COOLDOWN_TICKS;
                            yield false;
                        }
                        closeScreen(client, player);
                        yield true;
                    }

                    if (client.gameMode != null) {
                        client.gameMode.handleInventoryMouseClick(menu.containerId, 2, 0, ClickType.QUICK_MOVE, player);
                    }
                    tradesCompleted++;

                    // Check if we should stop trading
                    if (targetTrades > 0 && tradesCompleted >= targetTrades) {
                        // Stop after specific number of trades
                        closeScreen(client, player);
                        yield true;
                    }
                    // targetTrades == 0 means continue until out of stock (handled by isOutOfStock check)
                    // targetTrades == -1 means continue until 1 trade left (handled above before the trade)

                    actionCooldown = ACTION_COOLDOWN_TICKS;
                    yield false;
                }
                default -> true;
            };
        }

        private static MerchantMenu getMerchantMenu(LocalPlayer player) {
            if (player.containerMenu instanceof MerchantMenu menu) {
                return menu;
            }
            return null;
        }

        private static void closeScreen(Minecraft client, LocalPlayer player) {
            player.closeContainer();
            if (client.screen != null) {
                client.setScreen(null);
            }
        }

        private static AbstractVillager getPointedVillager(Minecraft client) {
            if (client.hitResult == null) return null;
            if (!(client.hitResult instanceof net.minecraft.world.phys.EntityHitResult entityHitResult)) return null;
            Entity entity = entityHitResult.getEntity();
            if (entity instanceof AbstractVillager villager) {
                return villager;
            }
            return null;
        }

        private static void selectOffer(Minecraft client, MerchantMenu menu, int offerIndex) {
            menu.setSelectionHint(offerIndex);
            if (client.getConnection() != null) {
                client.getConnection().send(new ServerboundSelectTradePacket(offerIndex));
            }
        }

        private static boolean matchesOffer(CommandType mode, Identifier itemId, MerchantOffer offer) {
            if (mode == CommandType.BUY) {
                return BuiltInRegistries.ITEM.getKey(offer.getResult().getItem()).equals(itemId);
            }
            // SELL
            return BuiltInRegistries.ITEM.getKey(offer.getCostA().getItem()).equals(itemId)
                || (!offer.getCostB().isEmpty() && BuiltInRegistries.ITEM.getKey(offer.getCostB().getItem()).equals(itemId));
        }

        private static int pickBestOfferIndex(Inventory inventory, MerchantOffers offers, CommandType mode, Identifier itemId) {
            int bestIndex = -1;
            long bestResultCount = 0;
            long bestTotalCost = 1;
            int bestTradesPossible = 0;

            List<ItemStack> baseInventory = copyInventoryStacks(inventory);

            for (int i = 0; i < offers.size(); i++) {
                MerchantOffer offer = offers.get(i);
                if (offer.isOutOfStock()) continue;
                if (!matchesOffer(mode, itemId, offer)) continue;

                int tradesPossible = maxTradesWithoutOverflow(baseInventory, offer);
                if (tradesPossible <= 0) continue;

                ItemStack costA = offer.getCostA();
                ItemStack costB = offer.getCostB();
                long totalCost = (long)costA.getCount() + (long)(costB.isEmpty() ? 0 : costB.getCount());
                long resultCount = (long)offer.getResult().getCount();

                // Maximize resultCount / totalCost, then prefer more trades possible.
                boolean better =
                    bestIndex < 0
                        || resultCount * bestTotalCost > bestResultCount * totalCost
                        || (resultCount * bestTotalCost == bestResultCount * totalCost && tradesPossible > bestTradesPossible);

                if (better) {
                    bestIndex = i;
                    bestResultCount = resultCount;
                    bestTotalCost = Math.max(1L, totalCost);
                    bestTradesPossible = tradesPossible;
                }
            }

            return bestIndex;
        }

        private static int maxTradesWithoutOverflow(List<ItemStack> startingInventory, MerchantOffer offer) {
            int remainingUses = Math.max(0, offer.getMaxUses() - offer.getUses());
            if (remainingUses == 0) return 0;

            List<ItemStack> inv = deepCopyStacks(startingInventory);
            int trades = 0;
            while (trades < remainingUses) {
                if (!simulateRemove(inv, offer.getCostA())) break;
                if (!offer.getCostB().isEmpty() && !simulateRemove(inv, offer.getCostB())) break;
                if (!simulateAdd(inv, offer.getResult())) break;
                trades++;
            }
            return trades;
        }

        private static boolean canPerformOneTradeWithoutOverflow(Inventory inventory, MerchantOffer offer) {
            List<ItemStack> inv = copyInventoryStacks(inventory);
            if (!simulateRemove(inv, offer.getCostA())) return false;
            if (!offer.getCostB().isEmpty() && !simulateRemove(inv, offer.getCostB())) return false;
            return simulateAdd(inv, offer.getResult());
        }

        private static List<ItemStack> copyInventoryStacks(Inventory inventory) {
            List<ItemStack> list = new ArrayList<>(Inventory.INVENTORY_SIZE);
            for (ItemStack stack : inventory.getNonEquipmentItems()) {
                list.add(stack.copy());
            }
            return list;
        }

        private static List<ItemStack> deepCopyStacks(List<ItemStack> stacks) {
            List<ItemStack> copy = new ArrayList<>(stacks.size());
            for (ItemStack stack : stacks) {
                copy.add(stack.copy());
            }
            return copy;
        }

        private static boolean simulateRemove(List<ItemStack> stacks, ItemStack cost) {
            if (cost.isEmpty()) return true;
            int remaining = cost.getCount();
            for (int i = 0; i < stacks.size() && remaining > 0; i++) {
                ItemStack stack = stacks.get(i);
                if (stack.isEmpty()) continue;
                if (!ItemStack.isSameItemSameComponents(stack, cost)) continue;
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
                if (stack.isEmpty()) {
                    stacks.set(i, ItemStack.EMPTY);
                }
            }
            return remaining == 0;
        }

        private static boolean simulateAdd(List<ItemStack> stacks, ItemStack toAdd) {
            if (toAdd.isEmpty()) return true;

            int remaining = toAdd.getCount();
            int maxStack = toAdd.getMaxStackSize();

            if (toAdd.isStackable()) {
                for (ItemStack stack : stacks) {
                    if (remaining <= 0) break;
                    if (stack.isEmpty()) continue;
                    if (!ItemStack.isSameItemSameComponents(stack, toAdd)) continue;
                    int space = Math.min(maxStack, stack.getMaxStackSize()) - stack.getCount();
                    if (space <= 0) continue;
                    int add = Math.min(space, remaining);
                    stack.grow(add);
                    remaining -= add;
                }
            }

            for (int i = 0; i < stacks.size() && remaining > 0; i++) {
                if (!stacks.get(i).isEmpty()) continue;
                int add = Math.min(maxStack, remaining);
                stacks.set(i, toAdd.copyWithCount(add));
                remaining -= add;
            }

            return remaining == 0;
        }

        private static boolean clearPaymentSlots(MerchantMenu menu) {
            // Return any payment items (slots 0 and 1) to the player's inventory slots.
            for (int paymentSlotIndex = 0; paymentSlotIndex <= 1; paymentSlotIndex++) {
                Slot paymentSlot = menu.getSlot(paymentSlotIndex);
                ItemStack payment = paymentSlot.getItem();
                if (payment.isEmpty()) continue;

                ItemStack remaining = payment.copy();
                remaining = moveIntoPlayerInventory(menu, remaining);
                if (!remaining.isEmpty()) {
                    return false;
                }
                paymentSlot.set(ItemStack.EMPTY);
            }
            return true;
        }

        private static ItemStack moveIntoPlayerInventory(MerchantMenu menu, ItemStack stack) {
            if (stack.isEmpty()) return ItemStack.EMPTY;

            // First pass: merge into existing stacks.
            if (stack.isStackable()) {
                for (int i = 3; i < menu.slots.size() && !stack.isEmpty(); i++) {
                    Slot invSlot = menu.getSlot(i);
                    ItemStack inv = invSlot.getItem();
                    if (inv.isEmpty()) continue;
                    if (!ItemStack.isSameItemSameComponents(inv, stack)) continue;
                    int max = invSlot.getMaxStackSize(inv);
                    int space = max - inv.getCount();
                    if (space <= 0) continue;
                    int move = Math.min(space, stack.getCount());
                    inv.grow(move);
                    stack.shrink(move);
                    invSlot.setChanged();
                }
            }

            // Second pass: empty slots.
            for (int i = 3; i < menu.slots.size() && !stack.isEmpty(); i++) {
                Slot invSlot = menu.getSlot(i);
                if (!invSlot.getItem().isEmpty()) continue;
                int move = Math.min(invSlot.getMaxStackSize(stack), stack.getCount());
                invSlot.set(stack.split(move));
            }

            return stack;
        }

        private static boolean fillPaymentSlotsExact(MerchantMenu menu, MerchantOffer offer) {
            ItemStack costA = offer.getCostA();
            ItemStack costB = offer.getCostB();

            if (!fillPaymentSlotExact(menu, 0, costA)) return false;
            if (!costB.isEmpty() && !fillPaymentSlotExact(menu, 1, costB)) return false;
            if (costB.isEmpty()) {
                menu.getSlot(1).set(ItemStack.EMPTY);
            }
            return true;
        }

        private static boolean fillPaymentSlotExact(MerchantMenu menu, int paymentSlotIndex, ItemStack required) {
            if (required.isEmpty()) {
                menu.getSlot(paymentSlotIndex).set(ItemStack.EMPTY);
                return true;
            }

            Slot paymentSlot = menu.getSlot(paymentSlotIndex);
            ItemStack payment = paymentSlot.getItem();
            if (!payment.isEmpty()) {
                // If the slot has anything unexpected, abort (we clear beforehand, so this is a safety check).
                if (!ItemStack.isSameItemSameComponents(payment, required)) return false;
                if (payment.getCount() > required.getCount()) return false;
            }

            int needed = required.getCount() - (payment.isEmpty() ? 0 : payment.getCount());
            if (needed <= 0) return true;

            for (int i = 3; i < menu.slots.size() && needed > 0; i++) {
                Slot invSlot = menu.getSlot(i);
                ItemStack inv = invSlot.getItem();
                if (inv.isEmpty()) continue;
                if (!ItemStack.isSameItemSameComponents(inv, required)) continue;

                int move = Math.min(needed, inv.getCount());
                if (payment.isEmpty()) {
                    ItemStack newPayment = inv.copyWithCount(move);
                    inv.shrink(move);
                    invSlot.setChanged();
                    paymentSlot.set(newPayment);
                    payment = newPayment;
                } else {
                    inv.shrink(move);
                    invSlot.setChanged();
                    payment.grow(move);
                    paymentSlot.setChanged();
                }
                needed -= move;
            }

            return needed == 0;
        }
    }
}
