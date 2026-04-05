package dev.suncat.mod.modules.impl.combat;

import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.UpdateEvent;
import dev.suncat.api.utils.math.Timer;
import dev.suncat.api.utils.player.InventoryUtil;
import dev.suncat.api.utils.world.BlockUtil;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.impl.player.PacketMine;
import dev.suncat.mod.modules.settings.impl.BindSetting;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import dev.suncat.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class AutoKit extends Module {
    private final BooleanSetting rotate = this.add(new BooleanSetting("Rotate", true));
    private final SliderSetting range = this.add(new SliderSetting("Range", 4.0, 0.0, 6.0));
    private final SliderSetting minRange = this.add(new SliderSetting("MinRange", 1.0, 0.0, 3.0));
    private final BooleanSetting inventory = this.add(new BooleanSetting("InventorySwap", true));
    private final BooleanSetting place = this.add(new BooleanSetting("Place", true));
    private final BooleanSetting mine = this.add(new BooleanSetting("Mine", true));
    private final SliderSetting speed = this.add(new SliderSetting("Delay", 50, 1, 1000));
    private final SliderSetting clicks = this.add(new SliderSetting("Clicks", 1, 1, 36));
    private final BooleanSetting autoOpen = this.add(new BooleanSetting("AutoOpen", true));
    private final SliderSetting maxWaitTime = this.add(new SliderSetting("MaxWaitTime", 3000, 0, 10000));
    private final BooleanSetting autoDisable = this.add(new BooleanSetting("AutoDisable", true));
    private final SliderSetting disableTime = this.add(new SliderSetting("DisableTime", 500, 0, 1000));
    private final BooleanSetting preferOpen = this.add(new BooleanSetting("PreferOpen", true));
    private final BindSetting placeKey = this.add(new BindSetting("PlaceKey", -1));
    
    private HashMap<Integer, String> expectedInv = new HashMap<>();
    private final Timer timer = new Timer();
    private final Timer regearTimer = new Timer();
    public final Timer timeoutTimer = new Timer();
    public BlockPos placePos = null;
    private BlockPos openPos;
    private boolean setupDone = false;
    private boolean regearActive = false;
    private long regearDelay = 0L;
    private final Timer regearWaitTimer = new Timer();
    private boolean waitingForShulker = false;
    private BlockPos targetShulkerPos = null;
    private boolean needPlaceForRegear = false;
    private boolean regearCompleted = false;
    private final Set<Integer> processedSlots = new HashSet<>();
    private boolean wasInShulkerScreen = false;
    private boolean shouldDisableOnScreenClose = false;
    private boolean placeKeyOn = false;
    private final List<BlockPos> openList = new ArrayList<>();
    private final Map<Integer, String[]> presetKits = new HashMap<>() {{
        put(1, new String[]{"minecraft:netherite_sword", "minecraft:totem_of_undying", "minecraft:totem_of_undying", "minecraft:end_crystal", "minecraft:enchanted_golden_apple", "minecraft:enchanted_golden_apple", "minecraft:ender_pearl", "minecraft:ender_pearl", "minecraft:ender_chest", "minecraft:obsidian", "minecraft:obsidian", "minecraft:obsidian", "minecraft:redstone_block", "minecraft:glowstone", "minecraft:cobweb", "minecraft:cobweb", "minecraft:experience_bottle", "minecraft:experience_bottle", "minecraft:experience_bottle", "minecraft:piston", "minecraft:piston", "minecraft:chorus_fruit", "minecraft:chorus_fruit", "minecraft:chorus_fruit", "minecraft:air", "minecraft:air", "minecraft:air", "minecraft:air", "minecraft:air", "minecraft:air", "minecraft:air", "minecraft:air", "minecraft:air", "minecraft:air", "minecraft:air", "minecraft:air"});
        put(2, new String[]{"minecraft:netherite_helmet", "minecraft:netherite_chestplate", "minecraft:netherite_leggings", "minecraft:netherite_boots", "minecraft:elytra", "minecraft:elytra", "minecraft:netherite_pickaxe", "minecraft:netherite_sword", "minecraft:totem_of_undying", "minecraft:totem_of_undying", "minecraft:totem_of_undying", "minecraft:totem_of_undying", "minecraft:end_crystal", "minecraft:end_crystal", "minecraft:end_crystal", "minecraft:end_crystal", "minecraft:enchanted_golden_apple", "minecraft:enchanted_golden_apple", "minecraft:ender_pearl", "minecraft:ender_pearl", "minecraft:ender_pearl", "minecraft:ender_pearl", "minecraft:ender_chest", "minecraft:obsidian", "minecraft:obsidian", "minecraft:obsidian", "minecraft:obsidian", "minecraft:respawn_anchor", "minecraft:experience_bottle", "minecraft:experience_bottle", "minecraft:chorus_fruit", "minecraft:chorus_fruit", "minecraft:air", "minecraft:air", "minecraft:air", "minecraft:air"});
    }};
    
    private static final File KITS_FILE = new File(System.getProperty("user.dir") + File.separator + "suncatclient" + File.separator + "kits.cfg");
    public static AutoKit INSTANCE;

    public AutoKit() {
        super("AutoKit", Category.Combat);
        this.setChinese("补给+");
        INSTANCE = this;
    }

    public int findShulker() {
        AtomicInteger atomicInteger = new AtomicInteger(-1);
        if (this.findClass(ShulkerBoxBlock.class) != -1) {
            atomicInteger.set(this.findClass(ShulkerBoxBlock.class));
        }
        return atomicInteger.get();
    }

    public int findClass(Class clazz) {
        return this.inventory.getValue() ? InventoryUtil.findClassInventorySlot(clazz) : InventoryUtil.findClass(clazz);
    }

    @Override
    public void onEnable() {
        this.openPos = null;
        this.placePos = null;
        this.setupDone = false;
        this.regearActive = false;
        this.regearDelay = (long) this.speed.getValue();
        this.waitingForShulker = false;
        this.targetShulkerPos = null;
        this.regearWaitTimer.reset();
        this.needPlaceForRegear = false;
        this.regearCompleted = false;
        this.processedSlots.clear();
        this.wasInShulkerScreen = false;
        this.shouldDisableOnScreenClose = false;
        this.placeKeyOn = false;
        this.timeoutTimer.reset();
        this.openList.clear();
        if (nullCheck()) {
            return;
        }
        if (this.place.getValue()) {
            this.handlePlaceLogic();
        } else if (this.autoOpen.getValue() && !this.regearCompleted) {
            this.findAndOpenShulkerForRegear();
        }
    }

    private void handlePlaceLogic() {
        int oldSlot = mc.player.getInventory().selectedSlot;
        double distance = 100.0;
        BlockPos bestPos = null;
        if (this.preferOpen.getValue()) {
            for (BlockPos pos : BlockUtil.getSphere((float) this.range.getValue())) {
                if (!(mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock)) continue;
                this.targetShulkerPos = pos;
                if (this.rotate.getValue()) {
                    BlockUtil.clickBlock(pos, BlockUtil.getClickSide(pos), true);
                } else {
                    BlockUtil.clickBlock(pos, BlockUtil.getClickSide(pos), false);
                }
                this.waitingForShulker = true;
                this.regearWaitTimer.reset();
                return;
            }
        }
        for (BlockPos pos : BlockUtil.getSphere((float) this.range.getValue())) {
            if (!mc.world.isAir(pos.up()) || MathHelper.sqrt((float) mc.player.squaredDistanceTo(pos.toCenterPos())) < this.minRange.getValue() || !BlockUtil.clientCanPlace(pos, false) || !BlockUtil.isStrictDirection(pos.offset(Direction.DOWN), Direction.UP) || !BlockUtil.canClick(pos.offset(Direction.DOWN)))
                continue;
            double currentDistance = MathHelper.sqrt((float) mc.player.squaredDistanceTo(pos.toCenterPos()));
            if (bestPos != null && !(currentDistance < distance)) continue;
            distance = currentDistance;
            bestPos = pos;
        }
        if (bestPos != null) {
            int slot = this.findShulker();
            if (slot == -1) {
                this.sendMessage("§4No shulkerbox found.");
                return;
            }
            this.doSwap(slot);
            this.placeBlock(bestPos);
            this.placePos = bestPos;
            if (this.inventory.getValue()) {
                this.doSwap(slot);
            } else {
                this.doSwap(oldSlot);
            }
            this.timer.reset();
            this.needPlaceForRegear = true;
        } else {
            this.sendMessage("§4No place position found.");
            if (this.autoOpen.getValue() && !this.regearCompleted) {
                this.findAndOpenShulkerForRegear();
            }
        }
    }

    private void findAndOpenShulkerForRegear() {
        BlockPos nearestShulker = null;
        double nearestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockUtil.getSphere((float) this.range.getValue())) {
            double distance;
            if (!(mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock) || (distance = mc.player.squaredDistanceTo(pos.toCenterPos())) < this.minRange.getValue() * this.minRange.getValue() || !(distance < nearestDistance))
                continue;
            nearestDistance = distance;
            nearestShulker = pos;
        }
        if (nearestShulker != null) {
            this.targetShulkerPos = nearestShulker;
            if (this.rotate.getValue()) {
                BlockUtil.clickBlock(nearestShulker, BlockUtil.getClickSide(nearestShulker), true);
            } else {
                BlockUtil.clickBlock(nearestShulker, BlockUtil.getClickSide(nearestShulker), false);
            }
            this.waitingForShulker = true;
            this.regearWaitTimer.reset();
        }
    }

    private void doSwap(int slot) {
        if (this.inventory.getValue()) {
            InventoryUtil.inventorySwap(slot, mc.player.getInventory().selectedSlot);
        } else {
            InventoryUtil.switchToSlot(slot);
        }
    }

    @Override
    public void onDisable() {
        if (this.mine.getValue() && this.placePos != null) {
            PacketMine.INSTANCE.mine(this.placePos);
        }
        this.regearActive = false;
        this.waitingForShulker = false;
        this.targetShulkerPos = null;
        this.needPlaceForRegear = false;
        this.processedSlots.clear();
        this.wasInShulkerScreen = false;
        this.shouldDisableOnScreenClose = false;
        this.placeKeyOn = false;
        this.openList.clear();
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (this.placeKey.isPressed() && mc.currentScreen == null) {
            if (!this.placeKeyOn) {
                this.openPos = null;
                this.timeoutTimer.reset();
                this.placePos = null;
                this.handlePlaceLogic();
                this.placeKeyOn = true;
            }
        } else {
            this.placeKeyOn = false;
        }
        this.openList.removeIf(pos -> !(mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock));
        if (!(mc.currentScreen instanceof ShulkerBoxScreen)) {
            if (this.wasInShulkerScreen) {
                this.wasInShulkerScreen = false;
                if (this.autoDisable.getValue()) {
                    this.timeoutToDisable();
                }
                if (this.mine.getValue() && this.openPos != null) {
                    if (mc.world.getBlockState(this.openPos).getBlock() instanceof ShulkerBoxBlock) {
                        PacketMine.INSTANCE.mine(this.openPos);
                    } else {
                        this.openPos = null;
                    }
                }
            } else if (this.autoOpen.getValue()) {
                this.handleAutoOpenLogic();
            } else if (this.autoDisable.getValue()) {
                this.timeoutToDisable();
            }
        } else {
            this.wasInShulkerScreen = true;
            if (this.openPos != null) {
                this.openList.add(this.openPos);
            }
        }
        this.handleRegearMode();
        if (this.autoDisable.getValue()) {
            this.timeoutToDisable();
        }
    }

    private void handleAutoOpenLogic() {
        if (this.placePos != null) {
            double distance = MathHelper.sqrt((float) mc.player.squaredDistanceTo(this.placePos.toCenterPos()));
            boolean isAirOrReplaceable = mc.world.isAir(this.placePos.up()) || BlockUtil.canReplace(this.placePos.up());
            if (distance <= this.range.getValue() && isAirOrReplaceable && mc.world.getBlockState(this.placePos).getBlock() instanceof ShulkerBoxBlock) {
                this.openPos = this.placePos;
                BlockUtil.clickBlock(this.placePos, BlockUtil.getClickSide(this.placePos), this.rotate.getValue());
                return;
            }
        }
        for (BlockPos pos : BlockUtil.getSphere((float) this.range.getValue())) {
            if (this.openList.contains(pos) || !mc.world.isAir(pos.up()) && !BlockUtil.canReplace(pos.up()) || !(mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock))
                continue;
            this.openPos = pos;
            BlockUtil.clickBlock(pos, BlockUtil.getClickSide(pos), this.rotate.getValue());
            return;
        }
        if (this.autoDisable.getValue()) {
            this.timeoutToDisable();
        }
    }

    private void timeoutToDisable() {
        if (this.timeoutTimer.passedMs((long) this.disableTime.getValue())) {
            this.disable();
        }
    }

    private void handleRegearMode() {
        if (this.needPlaceForRegear && this.placePos != null && mc.world.getBlockState(this.placePos).getBlock() instanceof ShulkerBoxBlock && !(mc.currentScreen instanceof ShulkerBoxScreen)) {
            this.openPos = this.placePos;
            BlockUtil.clickBlock(this.placePos, BlockUtil.getClickSide(this.placePos), this.rotate.getValue());
            this.waitingForShulker = true;
            this.regearWaitTimer.reset();
            this.needPlaceForRegear = false;
        }
        if (this.waitingForShulker && !(mc.currentScreen instanceof ShulkerBoxScreen)) {
            if (this.regearWaitTimer.passedMs((long) this.maxWaitTime.getValue())) {
                this.waitingForShulker = false;
            }
            return;
        }
        if (mc.currentScreen instanceof ShulkerBoxScreen) {
            this.waitingForShulker = false;
            this.needPlaceForRegear = false;
            this.shouldDisableOnScreenClose = true;
            if (!this.regearActive) {
                this.setupRegear();
                this.regearActive = true;
                this.regearTimer.reset();
                this.regearDelay = (long) this.speed.getValue();
                this.processedSlots.clear();
            }
            ShulkerBoxScreenHandler handler = (ShulkerBoxScreenHandler) mc.player.currentScreenHandler;
            if (handler.slots.size() != 63 && handler.slots.size() != 90) {
                return;
            }
            if (this.expectedInv.isEmpty()) {
                return;
            }
            if (this.regearTimer.passedMs(this.regearDelay)) {
                boolean allItemsCorrect = true;
                int actionsTaken = 0;
                ArrayList<Map.Entry<Integer, String>> itemsToProcess = new ArrayList<>(this.expectedInv.entrySet());
                itemsToProcess.sort((a, b) -> {
                    int slotA = a.getKey();
                    int slotB = b.getKey();
                    ItemStack stackA = this.getItemInTargetSlot(slotA, handler);
                    ItemStack stackB = this.getItemInTargetSlot(slotB, handler);
                    boolean isEmptyA = stackA.isEmpty();
                    boolean isEmptyB = stackB.isEmpty();
                    boolean isCorrectA = this.isItemCorrect(stackA, a.getValue());
                    boolean isCorrectB = this.isItemCorrect(stackB, b.getValue());
                    if (isEmptyA && !isEmptyB) {
                        return -1;
                    }
                    if (!isEmptyA && isEmptyB) {
                        return 1;
                    }
                    if (!isCorrectA && isCorrectB) {
                        return -1;
                    }
                    if (isCorrectA && !isCorrectB) {
                        return 1;
                    }
                    return 0;
                });
                for (Map.Entry<Integer, String> entry : itemsToProcess) {
                    int mergeSlot;
                    if ((double) actionsTaken >= this.clicks.getValue()) break;
                    int slotIndex = entry.getKey();
                    String expectedItem = entry.getValue();
                    int targetSlot = this.getTargetSlot(slotIndex, handler);
                    if (targetSlot < 0 || targetSlot >= handler.slots.size()) continue;
                    ItemStack targetStack = handler.slots.get(targetSlot).getStack();
                    if (this.needToTakeItem(targetStack, expectedItem)) {
                        int emptySlot;
                        int sourceSlot = this.findItemInShulker(expectedItem, handler, targetSlot);
                        if (sourceSlot != -1) {
                            this.performPickupAction(handler, sourceSlot, targetSlot);
                            ++actionsTaken;
                            allItemsCorrect = false;
                            continue;
                        }
                        if (targetStack.isEmpty() || this.isItemCorrect(targetStack, expectedItem) || (emptySlot = this.findEmptySlotInShulker(handler)) == -1)
                            continue;
                        mc.interactionManager.clickSlot(handler.syncId, targetSlot, 0, SlotActionType.PICKUP, mc.player);
                        mc.interactionManager.clickSlot(handler.syncId, emptySlot, 0, SlotActionType.PICKUP, mc.player);
                        ++actionsTaken;
                        continue;
                    }
                    if (targetStack.getCount() >= targetStack.getMaxCount() || (mergeSlot = this.findMergeableStack(expectedItem, handler, targetSlot)) == -1)
                        continue;
                    this.performMergeAction(handler, mergeSlot, targetSlot);
                    ++actionsTaken;
                }
                this.regearTimer.reset();
                if (allItemsCorrect || this.checkAllItemsCorrect(handler)) {
                    this.regearCompleted = true;
                    this.processedSlots.clear();
                    if (this.mine.getValue() && this.placePos != null) {
                        PacketMine.INSTANCE.mine(this.placePos);
                    }
                    this.resetRegearState();
                }
            }
        } else {
            this.regearActive = false;
            this.processedSlots.clear();
        }
    }

    private int getTargetSlot(int playerSlot, ShulkerBoxScreenHandler handler) {
        if (handler.slots.size() == 63) {
            return playerSlot < 9 ? playerSlot + 54 : playerSlot + 18;
        }
        if (handler.slots.size() == 90) {
            return playerSlot < 9 ? playerSlot + 81 : playerSlot + 45;
        }
        return -1;
    }

    private ItemStack getItemInTargetSlot(int playerSlot, ShulkerBoxScreenHandler handler) {
        int targetSlot = this.getTargetSlot(playerSlot, handler);
        if (targetSlot >= 0 && targetSlot < handler.slots.size()) {
            return handler.slots.get(targetSlot).getStack();
        }
        return ItemStack.EMPTY;
    }

    private boolean isItemCorrect(ItemStack stack, String expectedItem) {
        if (stack.isEmpty()) {
            return false;
        }
        String itemKey = stack.getItem().toString();
        return itemKey.equals(expectedItem);
    }

    private boolean needToTakeItem(ItemStack currentStack, String expectedItem) {
        if (currentStack.isEmpty()) {
            return true;
        }
        return !this.isItemCorrect(currentStack, expectedItem);
    }

    private int findItemInShulker(String itemName, ShulkerBoxScreenHandler handler, int excludeSlot) {
        int maxSearch = handler.slots.size() == 63 ? 27 : 54;
        ArrayList<Integer> candidateSlots = new ArrayList<>();
        int bestSlot = -1;
        int bestCount = 0;
        for (int i = 0; i < maxSearch; ++i) {
            ItemStack stack;
            if (i == excludeSlot || (stack = handler.slots.get(i).getStack()).isEmpty()) continue;
            String stackKey = stack.getItem().toString();
            if (!stackKey.equals(itemName)) continue;
            candidateSlots.add(i);
            if (stack.getCount() == stack.getMaxCount()) {
                return i;
            }
            if (stack.getCount() <= bestCount) continue;
            bestCount = stack.getCount();
            bestSlot = i;
        }
        return bestSlot;
    }

    private int findMergeableStack(String itemName, ShulkerBoxScreenHandler handler, int targetSlot) {
        int maxSearch = handler.slots.size() == 63 ? 27 : 54;
        ItemStack targetStack = handler.slots.get(targetSlot).getStack();
        if (targetStack.isEmpty() || targetStack.getCount() >= targetStack.getMaxCount()) {
            return -1;
        }
        for (int i = 0; i < maxSearch; ++i) {
            ItemStack stack;
            if (i == targetSlot || (stack = handler.slots.get(i).getStack()).isEmpty() || stack.getItem() != targetStack.getItem())
                continue;
            if (!stack.getItem().toString().equals(targetStack.getItem().toString())) continue;
            return i;
        }
        return -1;
    }

    private int findEmptySlotInShulker(ShulkerBoxScreenHandler handler) {
        int maxSearch = handler.slots.size() == 63 ? 27 : 54;
        for (int i = 0; i < maxSearch; ++i) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (!stack.isEmpty()) continue;
            return i;
        }
        return -1;
    }

    private void performPickupAction(ShulkerBoxScreenHandler handler, int sourceSlot, int targetSlot) {
        mc.interactionManager.clickSlot(handler.syncId, sourceSlot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(handler.syncId, targetSlot, 0, SlotActionType.PICKUP, mc.player);
        ItemStack cursorStack = handler.getCursorStack();
        if (!cursorStack.isEmpty()) {
            mc.interactionManager.clickSlot(handler.syncId, sourceSlot, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    private void performMergeAction(ShulkerBoxScreenHandler handler, int sourceSlot, int targetSlot) {
        ItemStack sourceStack = handler.slots.get(sourceSlot).getStack();
        ItemStack targetStack = handler.slots.get(targetSlot).getStack();
        if (sourceStack.isEmpty() || targetStack.isEmpty()) {
            return;
        }
        int spaceLeft = targetStack.getMaxCount() - targetStack.getCount();
        int available = Math.min(spaceLeft, sourceStack.getCount());
        if (available > 0) {
            if (sourceStack.getCount() == available) {
                mc.interactionManager.clickSlot(handler.syncId, sourceSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
            } else {
                mc.interactionManager.clickSlot(handler.syncId, sourceSlot, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(handler.syncId, targetSlot, 0, SlotActionType.PICKUP, mc.player);
                ItemStack cursorStack = handler.getCursorStack();
                if (!cursorStack.isEmpty()) {
                    mc.interactionManager.clickSlot(handler.syncId, sourceSlot, 0, SlotActionType.PICKUP, mc.player);
                }
            }
        }
    }

    private boolean checkAllItemsCorrect(ShulkerBoxScreenHandler handler) {
        for (Map.Entry<Integer, String> entry : this.expectedInv.entrySet()) {
            int slotIndex = entry.getKey();
            String expectedItem = entry.getValue();
            ItemStack stack = this.getItemInTargetSlot(slotIndex, handler);
            if (!this.isItemCorrect(stack, expectedItem)) {
                return false;
            }
        }
        return true;
    }

    private void setupRegear() {
        this.expectedInv.clear();
        this.loadKit("default");
    }

    private void resetRegearState() {
        if (this.autoDisable.getValue()) {
            this.timeoutToDisable();
        }
    }

    public void loadKit(String kitName) {
        File file = new File(System.getProperty("user.dir") + File.separator + "suncatclient" + File.separator + "kits" + File.separator + kitName + ".kit");
        if (!file.exists()) {
            if (this.presetKits.containsKey(Integer.valueOf(kitName))) {
                String[] preset = this.presetKits.get(Integer.valueOf(kitName));
                for (int i = 0; i < preset.length; ++i) {
                    if (preset[i].equals("minecraft:air")) continue;
                    this.expectedInv.put(i, preset[i]);
                }
            }
            return;
        }
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    int slot = Integer.parseInt(parts[0]);
                    String item = parts[1];
                    this.expectedInv.put(slot, item);
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void placeBlock(BlockPos pos) {
        if (mc.world.isAir(pos)) {
            Direction side = BlockUtil.getClickSide(pos).getOpposite();
            Vec3d hitVec = Vec3d.ofCenter(pos).add(Vec3d.of(side.getVector()).multiply(0.5));
            BlockHitResult hitResult = new BlockHitResult(hitVec, side, pos, false);
            mc.interactionManager.interactBlock(mc.player, mc.player.getActiveHand(), hitResult);
        }
    }
}