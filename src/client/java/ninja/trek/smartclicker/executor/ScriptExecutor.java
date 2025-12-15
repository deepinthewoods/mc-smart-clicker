package ninja.trek.smartclicker.executor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import ninja.trek.smartclicker.command.CommandInstruction;
import ninja.trek.smartclicker.command.CommandType;
import ninja.trek.smartclicker.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
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

    private TradeTask tradeTask;
    private int tradeTaskPostDelay;

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
        this.tradeTask = null;
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

        // Continue any in-progress trade task.
        if (tradeTask != null) {
            if (tradeTask.tick(client)) {
                tradeTask = null;
                delayTicks = tradeTaskPostDelay;
                currentInstructionIndex++;
            }
            return;
        }

        // Execute current instruction
        CommandInstruction instruction = instructions.get(currentInstructionIndex);
        if (instruction.getType() == CommandType.BUY || instruction.getType() == CommandType.SELL) {
            tradeTaskPostDelay = instruction.getPostDelay();
            tradeTask = new TradeTask(instruction.getType(), instruction.getParameter(), instruction.getAmount());
            if (tradeTask.tick(client)) {
                tradeTask = null;
                delayTicks = tradeTaskPostDelay;
                currentInstructionIndex++;
            }
            return;
        }

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
                    default -> LOGGER.error("Invalid move direction: {}", direction);
                }
            }
            case TILT_ABSOLUTE -> {
                try {
                    float yaw = Float.parseFloat(instruction.getParameter());
                    player.setYRot(yaw);
                } catch (NumberFormatException e) {
                    LOGGER.error("Invalid yaw value: {}", instruction.getParameter());
                }
            }
            case SWAP_TOOL -> {
                try {
                    int durabilityThreshold = Integer.parseInt(instruction.getParameter());
                    Inventory inventory = player.getInventory();

                    // Get current hotbar slot using reflection
                    if (inventorySelectedField == null) {
                        LOGGER.error("Cannot swap tool: inventory reflection not initialized");
                        break;
                    }

                    int currentSlot = (int) inventorySelectedField.get(inventory);
                    ItemStack currentItem = inventory.getItem(currentSlot);

                    // Only proceed if there's an item and it's damageable
                    if (!currentItem.isEmpty() && currentItem.isDamageableItem()) {
                        int remainingDurability = currentItem.getMaxDamage() - currentItem.getDamageValue();

                        // Only swap if durability is below threshold
                        if (remainingDurability < durabilityThreshold) {
                            boolean swapped = false;

                            // Search inventory (slots 9-35) for replacement
                            for (int i = 9; i < 36; i++) {
                                ItemStack candidateItem = inventory.getItem(i);

                                // Check if it's the same item type
                                if (!candidateItem.isEmpty() && ItemStack.isSameItem(currentItem, candidateItem)) {
                                    int candidateDurability = candidateItem.getMaxDamage() - candidateItem.getDamageValue();

                                    // Swap if candidate has more durability than threshold
                                    if (candidateDurability > durabilityThreshold) {
                                        // Perform swap
                                        inventory.setItem(currentSlot, candidateItem);
                                        inventory.setItem(i, currentItem);
                                        swapped = true;
                                        LOGGER.info("Swapped tool in slot {} with inventory slot {}", currentSlot, i);
                                        break;
                                    }
                                }
                            }

                            // If no replacement found, move current tool to empty slot
                            if (!swapped) {
                                for (int i = 9; i < 36; i++) {
                                    ItemStack slotItem = inventory.getItem(i);
                                    if (slotItem.isEmpty()) {
                                        // Move current tool to empty slot
                                        inventory.setItem(i, currentItem);
                                        inventory.setItem(currentSlot, ItemStack.EMPTY);
                                        LOGGER.info("Moved tool from slot {} to empty inventory slot {}", currentSlot, i);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } catch (NumberFormatException e) {
                    LOGGER.error("Invalid durability threshold: {}", instruction.getParameter());
                } catch (Exception e) {
                    LOGGER.error("Failed to swap tool", e);
                }
            }
        }
    }

    private static final class TradeTask {
        private static final int MAX_WAIT_TICKS_FOR_SCREEN = 60;
        private static final int ACTION_COOLDOWN_TICKS = 2;

        private final CommandType mode;
        private final String itemIdText;
        private final ResourceLocation itemId;
        private final int targetTrades;

        private int stage = 0;
        private int waitTicks = 0;
        private int actionCooldown = 0;

        private int selectedOfferIndex = -1;
        private int tradesCompleted = 0;

        private TradeTask(CommandType mode, String itemIdText, int targetTrades) {
            this.mode = mode;
            this.itemIdText = itemIdText == null ? "" : itemIdText.trim();
            this.itemId = ResourceLocation.tryParse(this.itemIdText);
            this.targetTrades = Math.max(0, targetTrades);
        }

        public boolean tick(Minecraft client) {
            LocalPlayer player = client.player;
            if (player == null) return true;

            if (this.itemId == null) {
                LOGGER.error("{} requires a full item id like minecraft:emerald; got '{}'", mode, itemIdText);
                return true;
            }

            if (actionCooldown > 0) {
                actionCooldown--;
                return false;
            }

            return switch (stage) {
                case 0 -> {
                    if (!(client.screen instanceof MerchantScreen)) {
                        AbstractVillager villager = getPointedVillager(client);
                        if (villager == null) {
                            LOGGER.error("{} requires pointing at a villager", mode);
                            yield true;
                        }

                        if (client.gameMode == null) {
                            LOGGER.error("Cannot {}: gameMode is null", mode);
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

                    if (++waitTicks > MAX_WAIT_TICKS_FOR_SCREEN) {
                        LOGGER.error("Timed out waiting for villager trade screen for {}", mode);
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
                        if (++waitTicks > MAX_WAIT_TICKS_FOR_SCREEN) {
                            LOGGER.error("No villager offers available for {}", mode);
                            closeScreen(client, player);
                            yield true;
                        }
                        yield false;
                    }

                    selectedOfferIndex = pickBestOfferIndex(player.getInventory(), offers, mode, itemId);
                    if (selectedOfferIndex < 0) {
                        LOGGER.error("No matching offers found for {} '{}'", mode, itemId);
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

                    if (!clearPaymentSlots(menu)) {
                        LOGGER.error("Not enough inventory space to clear payment slots for {}", mode);
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
                    if (targetTrades > 0 && tradesCompleted >= targetTrades) {
                        closeScreen(client, player);
                        yield true;
                    }
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

        private static boolean matchesOffer(CommandType mode, ResourceLocation itemId, MerchantOffer offer) {
            if (mode == CommandType.BUY) {
                return BuiltInRegistries.ITEM.getKey(offer.getResult().getItem()).equals(itemId);
            }
            // SELL
            return BuiltInRegistries.ITEM.getKey(offer.getCostA().getItem()).equals(itemId)
                || (!offer.getCostB().isEmpty() && BuiltInRegistries.ITEM.getKey(offer.getCostB().getItem()).equals(itemId));
        }

        private static int pickBestOfferIndex(Inventory inventory, MerchantOffers offers, CommandType mode, ResourceLocation itemId) {
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
