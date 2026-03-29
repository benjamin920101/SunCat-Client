/*
 * SunCat Client - ShiftKitSort Module
 * Shift 整理 Kit 布局模块
 *
 * 功能:
 * - 在背包界面按 Shift 键自动整理成 Kit 布局
 * - 按照 Kit 配置将物品放到对应槽位
 * - 使用 ;kit load [名字] 加载 Kit 后生效
 */
package dev.suncat.mod.modules.impl.player;

import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.ClientTickEvent;
import dev.suncat.core.impl.KitManager;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;

import java.util.*;

public class ShiftKitSort extends Module {
    public static ShiftKitSort INSTANCE;

    private final BooleanSetting sortInChests = this.add(new BooleanSetting("SortInChests", true));
    private final BooleanSetting debug = this.add(new BooleanSetting("Debug", false));

    private boolean triggered = false;
    private boolean isSorting = false;
    private int sortStep = 0;
    private int currentSlot = 0;
    private int waitTicks = 0;
    private KitManager.Kit currentKit = null;
    private Map<Integer, String> targetItems;  // 槽位 -> 物品 ID
    private Map<Integer, Integer> targetCounts;  // 槽位 -> 物品数量
    private boolean[] slotFilled;  // 记录槽位是否已填充
    public String currentKitName = "";  // 当前加载的 Kit 名称

    public ShiftKitSort() {
        super("ShiftKitSort", Category.Player);
        this.setChinese("Shift 整理 Kit");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        triggered = false;
        isSorting = false;
        currentKit = null;
    }

    @Override
    public void onDisable() {
        triggered = false;
        isSorting = false;
        currentKit = null;
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (Module.nullCheck() || event.isPost()) {
            return;
        }

        // 检查是否在背包界面
        boolean isInInventory = mc.currentScreen instanceof InventoryScreen;
        boolean isInChest = sortInChests.getValue() && mc.currentScreen instanceof GenericContainerScreen;

        if (!isInInventory && !isInChest) {
            isSorting = false;
            triggered = false;
            return;
        }

        // 检查是否按下 Shift 键
        if (mc.options.sprintKey.isPressed() && !triggered && !isSorting) {
            triggered = true;
            startSorting();
            return;
        }

        if (!mc.options.sprintKey.isPressed()) {
            triggered = false;
        }

        // 处理整理流程
        if (isSorting) {
            if (waitTicks > 0) {
                waitTicks--;
                return;
            }
            processSorting();
        }
    }

    private void startSorting() {
        // 使用当前加载的 Kit 名称
        if (currentKitName == null || currentKitName.isEmpty()) {
            if (debug.getValue()) {
                sendMessage("§c[ShiftSort] §7未加载 Kit，请先使用 §f;kit load [名字]");
            }
            isSorting = false;
            return;
        }

        currentKit = KitManager.getKit(currentKitName);

        if (currentKit == null) {
            if (debug.getValue()) {
                sendMessage("§c[ShiftSort] §7未找到 Kit: §f" + currentKitName + " §7请先使用 ;kit load [名字]");
            }
            isSorting = false;
            return;
        }

        if (debug.getValue()) {
            sendMessage("§a[ShiftSort] §7开始整理为 Kit: §f" + currentKitName);
        }

        // 构建目标物品映射
        targetItems = new HashMap<>();
        targetCounts = new HashMap<>();
        slotFilled = new boolean[36];

        for (int i = 0; i < 36; i++) {
            String itemId = currentKit.mainInventory[i];
            int count = currentKit.mainInventoryCounts[i] > 0 ? currentKit.mainInventoryCounts[i] : 1;

            if (itemId != null && !itemId.isEmpty()) {
                targetItems.put(i, itemId);
                targetCounts.put(i, count);
            }
        }

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

        ScreenHandler handler = mc.player.currentScreenHandler;
        int syncId = handler.syncId;

        // 获取当前物品栏状态（主背包 36 格）
        List<Integer> invSlots = getInventorySlots(handler);

        if (sortStep == 0) {
            // 步骤 0: 将目标物品移动到正确位置
            if (currentSlot < 36) {
                String targetItemId = targetItems.get(currentSlot);
                
                if (targetItemId != null) {
                    Item targetItem = Registries.ITEM.getOrEmpty(Identifier.of(targetItemId)).orElse(null);
                    
                    if (targetItem != null) {
                        // 检查当前槽位是否已经是目标物品
                        ItemStack currentStack = mc.player.getInventory().getStack(currentSlot);
                        boolean isCorrect = !currentStack.isEmpty() && 
                                          currentStack.getItem() == targetItem &&
                                          currentStack.getCount() >= targetCounts.getOrDefault(currentSlot, 1);

                        if (!isCorrect) {
                            // 在背包中查找目标物品
                            int foundSlot = findItemInInventory(targetItem, currentSlot);
                            
                            if (foundSlot != -1) {
                                // 移动物品到目标槽位
                                moveItemToSlot(handler, foundSlot, currentSlot);
                                slotFilled[currentSlot] = true;
                                
                                if (debug.getValue()) {
                                    sendMessage("§a[ShiftSort] §7移动物品到槽位 " + currentSlot);
                                }
                                
                                waitTicks = 3;
                                currentSlot++;
                                return;
                            }
                        } else {
                            slotFilled[currentSlot] = true;
                        }
                    }
                }
                
                currentSlot++;
                waitTicks = 1;
                
                if (currentSlot >= 36) {
                    sortStep = 1;
                    currentSlot = 0;
                    waitTicks = 2;
                    return;
                }
            }
        }

        // 步骤 1: 整理剩余物品到空位
        if (sortStep == 1) {
            if (currentSlot < 36) {
                ItemStack stack = mc.player.getInventory().getStack(currentSlot);
                
                if (!stack.isEmpty() && !slotFilled[currentSlot]) {
                    // 查找是否有空位
                    int emptySlot = findEmptySlot();
                    
                    if (emptySlot != -1 && emptySlot != currentSlot) {
                        // 移动物品到空位
                        moveItemToSlot(handler, currentSlot, emptySlot);
                        slotFilled[emptySlot] = true;
                        
                        if (debug.getValue()) {
                            sendMessage("§a[ShiftSort] §7移动剩余物品到空位 " + emptySlot);
                        }
                        
                        waitTicks = 3;
                    }
                }
                
                currentSlot++;
                waitTicks = 1;
                
                if (currentSlot >= 36) {
                    finishSorting();
                }
            }
        }
    }

    /**
     * 获取物品栏槽位列表
     */
    private List<Integer> getInventorySlots(ScreenHandler handler) {
        List<Integer> slots = new ArrayList<>();
        
        if (handler instanceof net.minecraft.screen.PlayerScreenHandler) {
            // 玩家背包界面：槽位 9-44 是主背包和装备栏
            for (int i = 9; i < 45; i++) {
                slots.add(i);
            }
        } else if (handler instanceof net.minecraft.screen.GenericContainerScreenHandler) {
            // 箱子界面：先获取箱子槽位，再获取玩家背包
            int containerSize = handler.slots.size() - 37;  // 减去玩家背包和装备栏
            for (int i = 0; i < containerSize; i++) {
                slots.add(i);
            }
            // 添加玩家背包槽位
            for (int i = containerSize; i < containerSize + 36; i++) {
                slots.add(i);
            }
        }
        
        return slots;
    }

    /**
     * 在背包中查找物品
     */
    private int findItemInInventory(Item item, int excludeSlot) {
        for (int i = 0; i < 36; i++) {
            if (i == excludeSlot) continue;
            if (slotFilled[i]) continue;
            
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 查找空位
     */
    private int findEmptySlot() {
        for (int i = 0; i < 36; i++) {
            if (slotFilled[i]) continue;
            
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 移动物品到指定槽位
     */
    private void moveItemToSlot(ScreenHandler handler, int fromSlot, int toSlot) {
        // 计算实际的屏幕处理器槽位 ID
        int actualFromSlot = getActualSlotId(fromSlot, handler);
        int actualToSlot = getActualSlotId(toSlot, handler);
        
        // 点击源槽位拿起物品
        mc.interactionManager.clickSlot(handler.syncId, actualFromSlot, 0, SlotActionType.PICKUP, mc.player);
        waitTicks = 1;

        // 点击目标槽位放下物品
        mc.interactionManager.clickSlot(handler.syncId, actualToSlot, 0, SlotActionType.PICKUP, mc.player);
        waitTicks = 1;

        // 如果还有剩余物品，点击源槽位放回
        mc.interactionManager.clickSlot(handler.syncId, actualFromSlot, 0, SlotActionType.PICKUP, mc.player);
    }

    /**
     * 获取实际的屏幕处理器槽位 ID
     */
    private int getActualSlotId(int inventorySlot, ScreenHandler handler) {
        if (handler instanceof net.minecraft.screen.PlayerScreenHandler) {
            // 玩家背包界面
            if (inventorySlot < 27) {
                // 主背包 0-26 -> 屏幕槽位 9-35
                return inventorySlot + 9;
            } else if (inventorySlot < 36) {
                // 热键栏 27-35 -> 屏幕槽位 36-44
                return inventorySlot + 9;
            }
        } else if (handler instanceof net.minecraft.screen.GenericContainerScreenHandler) {
            // 箱子界面
            int containerSize = handler.slots.size() - 37;
            if (inventorySlot < 27) {
                return containerSize + inventorySlot + 9;
            } else if (inventorySlot < 36) {
                return containerSize + inventorySlot + 9;
            }
        }
        
        return inventorySlot;
    }

    private void finishSorting() {
        isSorting = false;
        sortStep = 0;
        currentSlot = 0;
        waitTicks = 0;
        targetItems = null;
        targetCounts = null;
        slotFilled = null;

        if (debug.getValue()) {
            sendMessage("§a[ShiftSort] §7Kit 整理完成！");
        }
    }
}
