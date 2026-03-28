package dev.idhammai.mod.modules.impl.player;

import dev.idhammai.api.events.eventbus.EventListener;
import dev.idhammai.api.events.impl.ClientTickEvent;
import dev.idhammai.core.impl.KitManager;
import dev.idhammai.mod.modules.Module;
import dev.idhammai.mod.modules.settings.impl.BooleanSetting;
import dev.idhammai.mod.modules.settings.impl.SliderSetting;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;

import java.util.*;

public class InventorySorter extends Module {
    public static InventorySorter INSTANCE;

    private final SliderSetting kitSlot = this.add(new SliderSetting("KitSlot", 0, 0, 10, 1));
    private final BooleanSetting sortInChests = this.add(new BooleanSetting("SortInChests", false));
    private final BooleanSetting debug = this.add(new BooleanSetting("Debug", false));
    private final BooleanSetting autoSaveKit = this.add(new BooleanSetting("AutoSaveKit", true));

    private boolean triggered = false;
    private KitManager.Kit currentKit = null;
    private boolean isSorting = false;
    private int sortStep = 0;
    private int currentSlot = 0;
    private int waitTicks = 0;
    private ItemStack[] targetInventory;
    private Map<Integer, ItemStack> pendingMoves;
    private Map<Item, Integer> availableItems;

    public InventorySorter() {
        super("InventorySorter", Category.Player);
        this.setChinese("背包整理");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (Module.nullCheck()) {
            return;
        }
        triggered = false;
        currentKit = null;
        isSorting = false;
    }

    @Override
    public void onDisable() {
        triggered = false;
        currentKit = null;
        isSorting = false;
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (Module.nullCheck()) {
            return;
        }

        // Only sort in inventory screen
        if (!(mc.currentScreen instanceof InventoryScreen) &&
            !(sortInChests.getValue() && mc.currentScreen instanceof GenericContainerScreen)) {
            return;
        }

        // Check if shift key is pressed (single press trigger)
        if (mc.options.sprintKey.isPressed() && !triggered) {
            triggered = true;
            startSorting();
        } else if (!mc.options.sprintKey.isPressed()) {
            triggered = false;
        }

        // Process sorting steps with delay
        if (isSorting) {
            if (waitTicks > 0) {
                waitTicks--;
                return;
            }
            processSorting();
        }
    }

    private void startSorting() {
        // Use same kit naming as AutoRegear: "kit0", "kit1", etc.
        String kitName = "kit" + kitSlot.getValueInt();

        // Auto save kit if enabled
        if (autoSaveKit.getValue()) {
            KitManager.saveKit(kitName);
        }

        currentKit = KitManager.getKit(kitName);

        if (currentKit == null) {
            sendMessage("§c[Sorter] §7未找到 Kit: §f" + kitName + " §7请先使用 /kit save " + kitName);
            isSorting = false;
            return;
        }

        if (debug.getValue()) {
            sendMessage("§a[Sorter] §7开始整理背包为：§f" + kitName);
        }

        // Build target inventory state from kit
        targetInventory = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            String itemId = currentKit.mainInventory[i];
            int count = currentKit.mainInventoryCounts[i] > 0 ? currentKit.mainInventoryCounts[i] : 1;

            if (itemId != null && !itemId.isEmpty()) {
                var itemOpt = Registries.ITEM.getOrEmpty(Identifier.of(itemId));
                if (itemOpt.isPresent()) {
                    targetInventory[i] = new ItemStack(itemOpt.get(), count);
                } else {
                    targetInventory[i] = ItemStack.EMPTY;
                }
            } else {
                targetInventory[i] = ItemStack.EMPTY;
            }
        }

        // Collect all items from current inventory
        availableItems = new HashMap<>();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                availableItems.put(stack.getItem(), availableItems.getOrDefault(stack.getItem(), 0) + stack.getCount());
            }
        }

        // Calculate what items need to go to which slots
        pendingMoves = new LinkedHashMap<>();
        Map<Item, Integer> remainingItems = new HashMap<>(availableItems);
        
        // First pass: assign items to their target slots
        for (int i = 0; i < 36; i++) {
            if (!targetInventory[i].isEmpty()) {
                Item targetItem = targetInventory[i].getItem();
                int targetCount = targetInventory[i].getCount();
                
                if (remainingItems.containsKey(targetItem) && remainingItems.get(targetItem) >= targetCount) {
                    pendingMoves.put(i, targetInventory[i].copy());
                    remainingItems.put(targetItem, remainingItems.get(targetItem) - targetCount);
                }
            }
        }
        
        // Second pass: assign remaining items to empty slots
        for (Map.Entry<Item, Integer> entry : remainingItems.entrySet()) {
            if (entry.getValue() <= 0) continue;
            
            Item item = entry.getKey();
            int count = entry.getValue();
            int maxStack = item.getMaxCount();
            
            for (int i = 0; i < 36; i++) {
                if (pendingMoves.containsKey(i)) continue; // Skip target slots
                
                if (mc.player.getInventory().getStack(i).isEmpty() || 
                    mc.player.getInventory().getStack(i).getItem() == item) {
                    
                    int currentCount = mc.player.getInventory().getStack(i).getCount();
                    int toAdd = Math.min(maxStack - currentCount, count);
                    
                    if (pendingMoves.containsKey(i)) {
                        ItemStack existing = pendingMoves.get(i);
                        if (existing.getItem() == item) {
                            pendingMoves.put(i, new ItemStack(item, existing.getCount() + toAdd));
                        }
                    } else {
                        pendingMoves.put(i, new ItemStack(item, toAdd));
                    }
                    
                    count -= toAdd;
                    if (count <= 0) break;
                }
            }
        }

        // Start sorting process
        isSorting = true;
        sortStep = 0;
        currentSlot = 0;
        waitTicks = 0;
    }

    private void processSorting() {
        if (mc.player == null || mc.player.currentScreenHandler == null) {
            isSorting = false;
            return;
        }

        int syncId = mc.player.currentScreenHandler.syncId;

        // Step 0: Clear inventory by dropping items to pending
        if (sortStep == 0) {
            if (currentSlot < 36) {
                ItemStack stack = mc.player.getInventory().getStack(currentSlot);
                if (!stack.isEmpty()) {
                    // Store the item, will be placed back in step 1
                    if (!pendingMoves.containsKey(currentSlot)) {
                        pendingMoves.put(currentSlot, stack.copy());
                    }
                    // Clear the slot (client-side only, will sync via clicks)
                    mc.player.getInventory().setStack(currentSlot, ItemStack.EMPTY);
                }
                currentSlot++;
                waitTicks = 1; // Small delay between slots
                
                if (currentSlot >= 36) {
                    currentSlot = 0;
                    sortStep = 1;
                }
            }
        }
        // Step 1: Place items according to kit layout
        else if (sortStep == 1) {
            if (currentSlot < 36) {
                if (pendingMoves.containsKey(currentSlot)) {
                    ItemStack targetStack = pendingMoves.get(currentSlot);
                    mc.player.getInventory().setStack(currentSlot, targetStack);
                    pendingMoves.remove(currentSlot);
                }
                currentSlot++;
                waitTicks = 1;
                
                if (currentSlot >= 36) {
                    sortStep = 2;
                    currentSlot = 0;
                }
            }
        }
        // Step 2: Put remaining items in any empty slots
        else if (sortStep == 2) {
            if (!pendingMoves.isEmpty()) {
                Map.Entry<Integer, ItemStack> firstEntry = pendingMoves.entrySet().iterator().next();
                int targetSlot = -1;
                
                // Find an empty slot
                for (int i = 0; i < 36; i++) {
                    if (mc.player.getInventory().getStack(i).isEmpty()) {
                        targetSlot = i;
                        break;
                    }
                }
                
                if (targetSlot != -1) {
                    mc.player.getInventory().setStack(targetSlot, firstEntry.getValue().copy());
                    pendingMoves.remove(firstEntry.getKey());
                    waitTicks = 1;
                } else {
                    // No empty slots, try to stack
                    sortStep = 3;
                }
            } else {
                finishSorting();
            }
        }
        // Step 3: Stack remaining items
        else if (sortStep == 3) {
            boolean stacked = false;
            for (Map.Entry<Integer, ItemStack> entry : pendingMoves.entrySet()) {
                ItemStack toStack = entry.getValue();
                
                for (int i = 0; i < 36; i++) {
                    ItemStack existing = mc.player.getInventory().getStack(i);
                    if (existing.getItem() == toStack.getItem() && existing.getCount() < existing.getMaxCount()) {
                        int toAdd = Math.min(toStack.getCount(), existing.getMaxCount() - existing.getCount());
                        existing.increment(toAdd);
                        toStack.decrement(toAdd);
                        stacked = true;
                        waitTicks = 1;
                        break;
                    }
                }
                
                if (toStack.isEmpty()) {
                    pendingMoves.remove(entry.getKey());
                    break;
                }
            }
            
            if (!stacked && !pendingMoves.isEmpty()) {
                // Can't stack anymore, items will be lost (shouldn't happen normally)
                if (debug.getValue()) {
                    sendMessage("§c[Sorter] §7警告：物品过多，无法全部放入背包！");
                }
                pendingMoves.clear();
            }
            
            if (pendingMoves.isEmpty()) {
                finishSorting();
            }
        }
    }

    private void finishSorting() {
        isSorting = false;
        sortStep = 0;
        currentSlot = 0;
        pendingMoves = null;
        targetInventory = null;
        availableItems = null;
        
        if (debug.getValue()) {
            sendMessage("§a[Sorter] §7背包整理完成！");
        }
    }
}
