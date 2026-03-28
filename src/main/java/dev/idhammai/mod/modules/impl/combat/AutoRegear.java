/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.block.BedBlock
 *  net.minecraft.block.Blocks
 *  net.minecraft.block.PistonBlock
 *  net.minecraft.block.ShulkerBoxBlock
 *  net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen
 *  net.minecraft.component.DataComponentTypes
 *  net.minecraft.component.type.PotionContentsComponent
 *  net.minecraft.entity.effect.StatusEffect
 *  net.minecraft.entity.effect.StatusEffectInstance
 *  net.minecraft.entity.effect.StatusEffects
 *  net.minecraft.entity.player.PlayerEntity
 *  net.minecraft.item.BlockItem
 *  net.minecraft.item.Item
 *  net.minecraft.item.ItemStack
 *  net.minecraft.item.Items
 *  net.minecraft.screen.ScreenHandler
 *  net.minecraft.screen.ShulkerBoxScreenHandler
 *  net.minecraft.screen.slot.Slot
 *  net.minecraft.screen.slot.SlotActionType
 *  net.minecraft.util.math.BlockPos
 *  net.minecraft.util.math.Direction
 *  net.minecraft.util.math.MathHelper
 */
package dev.idhammai.mod.modules.impl.combat;

import dev.idhammai.api.events.eventbus.EventListener;
import dev.idhammai.api.events.impl.UpdateEvent;
import dev.idhammai.api.utils.math.Timer;
import dev.idhammai.api.utils.player.InventoryUtil;
import dev.idhammai.api.utils.world.BlockUtil;
import dev.idhammai.core.impl.KitManager;
import dev.idhammai.mod.modules.Module;
import dev.idhammai.mod.modules.impl.player.PacketMine;
import dev.idhammai.mod.modules.settings.impl.BindSetting;
import dev.idhammai.mod.modules.settings.impl.BooleanSetting;
import dev.idhammai.mod.modules.settings.impl.SliderSetting;
import net.minecraft.registry.Registries;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

public class AutoRegear
extends Module {
    public static AutoRegear INSTANCE;
    public final BooleanSetting rotate = this.add(new BooleanSetting("Rotate", true));
    public final Timer timeoutTimer = new Timer();
    final int[] stealCountList = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    private final BooleanSetting autoDisable = this.add(new BooleanSetting("AutoDisable", true));
    private final SliderSetting disableTime = this.add(new SliderSetting("DisableTime", 500, 0, 1000));
    private final BooleanSetting place = this.add(new BooleanSetting("Place", true));
    private final BooleanSetting inventory = this.add(new BooleanSetting("InventorySwap", true));
    private final BooleanSetting preferOpen = this.add(new BooleanSetting("PerferOpen", true));
    private final BooleanSetting open = this.add(new BooleanSetting("Open", true));
    private final SliderSetting range = this.add(new SliderSetting("MaxRange", 4.0, 0.0, 6.0, 0.1));
    private final SliderSetting minRange = this.add(new SliderSetting("MinRange", 1.0, 0.0, 3.0, 0.1));
    private final BooleanSetting mine = this.add(new BooleanSetting("Mine", true));
    private final BooleanSetting take = this.add(new BooleanSetting("Take", true));
    private final BooleanSetting smart = this.add(new BooleanSetting("Smart", true, this.take::getValue).setParent());
    private final BooleanSetting forceMove = this.add(new BooleanSetting("ForceQuickMove", true, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting takeSpeed = this.add(new SliderSetting("TakeSpeed", 1, 1, 10, 1, this.take::getValue));
    private final SliderSetting clickDelay = this.add(new SliderSetting("ClickDelay", 50, 0, 500, 5, this.take::getValue));
    private final BindSetting placeKey = this.add(new BindSetting("PlaceKey", -1));

    // Kit settings
    public String currentKitName = null;

    // Take speed control
    private int takeProgress = 0;
    private final Timer clickTimer = new Timer();
    
    private final Timer timer = new Timer();
    private final List<BlockPos> openList = new ArrayList<BlockPos>();
    public BlockPos placePos = null;
    private BlockPos openPos;
    private boolean opend = false;
    private boolean on = false;
    private boolean placeKeyPressed = false; // Track key state to prevent double-click

    private boolean hasShownNoShulkerMessage = false;

    public AutoRegear() {
        super("AutoRegear", Module.Category.Combat);
        this.setChinese("\u81ea\u52a8\u8865\u7ed9");
        INSTANCE = this;
    }

    public int findShulker() {
        if (this.inventory.getValue()) {
            for (int i = 0; i < 36; ++i) {
                BlockItem blockItem;
                Item item;
                ItemStack stack = AutoRegear.mc.player.getInventory().getStack(i);
                if (stack.isEmpty() || !((item = stack.getItem()) instanceof BlockItem) || !((blockItem = (BlockItem)item).getBlock() instanceof ShulkerBoxBlock)) continue;
                return i < 9 ? i + 36 : i;
            }
            return -1;
        }
        return InventoryUtil.findClass(ShulkerBoxBlock.class);
    }

    @Override
    public void onEnable() {
        this.opend = false;
        this.openPos = null;
        this.timeoutTimer.reset();
        this.placePos = null;
        this.takeProgress = 0;
        this.clickTimer.reset();
        this.placeKeyPressed = false;
        this.hasShownNoShulkerMessage = false;

        if (AutoRegear.nullCheck()) {
            return;
        }
        
        // Auto-find existing shulker boxes on ground first
        if (this.open.getValue()) {
            for (BlockPos pos : BlockUtil.getSphere((float)this.range.getValue())) {
                if (AutoRegear.mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock &&
                    AutoRegear.mc.world.isAir(pos.up())) {
                    this.openPos = pos;
                    this.sendMessage("\u00a72Found shulker box at: " + pos.toShortString());
                    break;
                }
            }
        }
        
        // If no shulker found and place is enabled, place one
        if (this.openPos == null && this.place.getValue()) {
            this.doPlace();
        } else if (this.openPos != null) {
            // Open the found shulker
            this.timer.reset();
        }
    }

    private void doPlace() {
        if (AutoRegear.nullCheck()) {
            return;
        }
        
        int oldSlot = AutoRegear.mc.player.getInventory().selectedSlot;
        double getDistance = 100.0;
        BlockPos bestPos = null;
        
        for (BlockPos pos : BlockUtil.getSphere((float)this.range.getValue())) {
            // Check if position is valid for placing shulker
            BlockPos belowPos = pos.offset(Direction.DOWN);
            
            // Skip if not air above
            if (!AutoRegear.mc.world.isAir(pos)) continue;
            
            // Skip if no solid block below to place on
            if (AutoRegear.mc.world.isAir(belowPos)) continue;
            if (!AutoRegear.mc.world.getBlockState(belowPos).isSolid()) continue;
            
            // Check distance
            double dist = AutoRegear.mc.player.squaredDistanceTo(pos.toCenterPos());
            if (dist < this.minRange.getValue() * this.minRange.getValue()) continue;
            if (dist > this.range.getValue() * this.range.getValue()) continue;
            
            // Check if can place
            if (!BlockUtil.clientCanPlace(pos, false)) continue;
            
            // Check if position is safe (not in lava/water)
            if (AutoRegear.mc.world.getFluidState(pos).isEmpty() == false) continue;
            
            if (bestPos == null || dist < getDistance) {
                getDistance = dist;
                bestPos = pos;
            }
        }
        
        if (bestPos != null) {
            if (this.findShulker() == -1) {
                if (!hasShownNoShulkerMessage) {
                    this.sendMessage("\u00a74No shulkerbox found.");
                    hasShownNoShulkerMessage = true;
                }
                return;
            }
            
            // Don't place if already a shulker there
            if (AutoRegear.mc.world.getBlockState(bestPos).getBlock() instanceof ShulkerBoxBlock) {
                this.openPos = bestPos;
                this.placePos = bestPos;
                this.sendMessage("\u00a72Found existing shulker at: " + bestPos.toShortString());
                return;
            }
            
            if (this.inventory.getValue()) {
                int slot = this.findShulker();
                InventoryUtil.inventorySwap(slot, oldSlot);
                this.placeBlock(bestPos);
                this.placePos = bestPos;
                this.openPos = bestPos;
                InventoryUtil.inventorySwap(slot, oldSlot);
            } else {
                InventoryUtil.switchToSlot(this.findShulker());
                this.placeBlock(bestPos);
                this.placePos = bestPos;
                this.openPos = bestPos;
                InventoryUtil.switchToSlot(oldSlot);
            }
            this.timer.reset();
            this.sendMessage("\u00a72Placed shulker box at: " + bestPos.toShortString());
        } else {
            this.sendMessage("\u00a74No valid place position found. Check range and surroundings.");
        }
    }

    @Override
    public void onDisable() {
        this.opend = false;
        this.openPos = null;
        this.placePos = null;
        this.placeKeyPressed = false;
        
        if (this.mine.getValue() && this.placePos != null && AutoRegear.mc.world != null) {
            if (AutoRegear.mc.world.getBlockState(this.placePos).getBlock() instanceof ShulkerBoxBlock) {
                PacketMine.INSTANCE.mine(this.placePos);
            }
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (AutoRegear.nullCheck()) {
            return;
        }
        
        // Handle place key press with debounce
        boolean currentKeyState = this.placeKey.isPressed();
        
        if (currentKeyState && !this.placeKeyPressed && AutoRegear.mc.currentScreen == null) {
            // Key just pressed
            this.placeKeyPressed = true;
            this.opend = false;
            this.openPos = null;
            this.timeoutTimer.reset();
            this.placePos = null;
            this.doPlace();
            this.on = true;
        } else if (!currentKeyState) {
            // Key released
            this.placeKeyPressed = false;
            this.on = false;
        }
        
        this.openList.removeIf(pos -> !(AutoRegear.mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock));
        
        // Auto-find shulker boxes on ground if not already found
        if (this.openPos == null && this.open.getValue()) {
            for (BlockPos pos : BlockUtil.getSphere((float)this.range.getValue())) {
                if (AutoRegear.mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock && 
                    AutoRegear.mc.world.isAir(pos.up())) {
                    this.openPos = pos;
                    break;
                }
            }
        }
        
        if (!(AutoRegear.mc.currentScreen instanceof ShulkerBoxScreen)) {
            if (this.opend) {
                this.opend = false;
                if (this.autoDisable.getValue()) {
                    this.timeoutToDisable();
                }
                if (this.mine.getValue() && this.openPos != null) {
                    if (AutoRegear.mc.world.getBlockState(this.openPos).getBlock() instanceof ShulkerBoxBlock) {
                        PacketMine.INSTANCE.mine(this.openPos);
                    } else {
                        this.openPos = null;
                    }
                }
                return;
            }
            if (this.open.getValue()) {
                if (this.placePos != null && (double)MathHelper.sqrt((float)((float)AutoRegear.mc.player.squaredDistanceTo(this.placePos.toCenterPos()))) <= this.range.getValue() && AutoRegear.mc.world.isAir(this.placePos.up()) && (!this.timer.passed(500L) || AutoRegear.mc.world.getBlockState(this.placePos).getBlock() instanceof ShulkerBoxBlock)) {
                    if (AutoRegear.mc.world.getBlockState(this.placePos).getBlock() instanceof ShulkerBoxBlock) {
                        this.openPos = this.placePos;
                        BlockUtil.clickBlock(this.placePos, BlockUtil.getClickSide(this.placePos), this.rotate.getValue());
                    }
                } else if (this.openPos != null && AutoRegear.mc.world.getBlockState(this.openPos).getBlock() instanceof ShulkerBoxBlock) {
                    // Found shulker on ground, open it
                    BlockUtil.clickBlock(this.openPos, BlockUtil.getClickSide(this.openPos), this.rotate.getValue());
                } else {
                    boolean found = false;
                    for (BlockPos pos2 : BlockUtil.getSphere((float)this.range.getValue())) {
                        if (this.openList.contains(pos2) || !AutoRegear.mc.world.isAir(pos2.up()) && !BlockUtil.canReplace(pos2.up()) || !(AutoRegear.mc.world.getBlockState(pos2).getBlock() instanceof ShulkerBoxBlock)) continue;
                        this.openPos = pos2;
                        BlockUtil.clickBlock(pos2, BlockUtil.getClickSide(pos2), this.rotate.getValue());
                        found = true;
                        break;
                    }
                    if (!found && this.autoDisable.getValue()) {
                        // No shulker found, try to place one
                        this.doPlace();
                    }
                }
            } else if (!this.take.getValue() && this.autoDisable.getValue()) {
                this.timeoutToDisable();
            }
            return;
        }
        this.opend = true;
        if (this.openPos != null) {
            this.openList.add(this.openPos);
        }
        if (!this.take.getValue()) {
            if (this.autoDisable.getValue()) {
                this.timeoutToDisable();
            }
            return;
        }

        // Check click delay
        if (!this.clickTimer.passed(this.clickDelay.getValueInt())) {
            return;
        }

        boolean take = false;
        ScreenHandler screenHandler = AutoRegear.mc.player.currentScreenHandler;
        if (screenHandler instanceof ShulkerBoxScreenHandler) {
            ShulkerBoxScreenHandler shulker = (ShulkerBoxScreenHandler)screenHandler;

            // Kit mode - take items based on saved kit in order
            // Use currentKitName if set, otherwise use default kit name
            String kitName = this.currentKitName != null ? this.currentKitName : "kit0";
            KitManager.Kit kit = KitManager.getKit(kitName);

            if (kit != null) {
                int takesThisTick = 0;
                int maxTakes = this.takeSpeed.getValueInt();

                // Continue from last progress position
                for (int kitSlot = this.takeProgress; kitSlot < 36 && takesThisTick < maxTakes; kitSlot++) {
                    if (kit.mainInventory[kitSlot] == null || kit.mainInventory[kitSlot].isEmpty()) {
                        this.takeProgress = kitSlot + 1;
                        continue;
                    }

                    // Check if player already has this item in the correct slot
                    ItemStack playerStack = AutoRegear.mc.player.getInventory().getStack(kitSlot);
                    String playerItemId = playerStack.isEmpty() ? "" : Registries.ITEM.getId(playerStack.getItem()).toString();
                    if (playerItemId.equals(kit.mainInventory[kitSlot])) {
                        this.takeProgress = kitSlot + 1;
                        continue; // Already has correct item
                    }

                    // Skip if player has a different item in this slot (don't replace it)
                    if (!playerStack.isEmpty()) {
                        this.takeProgress = kitSlot + 1;
                        continue; // Slot occupied by different item, skip
                    }

                    // Find this item in shulker
                    boolean found = false;
                    for (Slot slot : shulker.slots) {
                        if (slot.id >= 27 || slot.getStack().isEmpty()) continue;

                        String shulkerItemId = Registries.ITEM.getId(slot.getStack().getItem()).toString();
                        if (!shulkerItemId.equals(kit.mainInventory[kitSlot])) continue;

                        // Click to move item to player inventory
                        AutoRegear.mc.interactionManager.clickSlot(shulker.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, (PlayerEntity)AutoRegear.mc.player);
                        take = true;
                        takesThisTick++;
                        this.clickTimer.reset();

                        // Now move the item from its current position to the correct kit slot
                        // After QUICK_MOVE, item goes to first available slot, we need to swap it to correct position
                        int itemSlot = findItemInInventory(kit.mainInventory[kitSlot]);
                        if (itemSlot != -1 && itemSlot != kitSlot) {
                            // Swap item to correct slot
                            swapInventorySlots(itemSlot, kitSlot);
                        }

                        this.takeProgress = kitSlot + 1;
                        found = true;
                        break; // Move to next kit item
                    }

                    if (!found) {
                        this.takeProgress = kitSlot + 1;
                    }
                }

                // Reset progress when done
                if (this.takeProgress >= 36) {
                    this.takeProgress = 0;
                }
            }
        }
        if (this.autoDisable.getValue() && !take) {
            this.timeoutToDisable();
        }
    }

    private void timeoutToDisable() {
        if (this.timeoutTimer.passed(this.disableTime.getValueInt())) {
            this.disable();
        }
    }

    private Type needSteal(ItemStack i) {
        if (i.getItem().equals(Items.END_CRYSTAL) && this.stealCountList[0] > 0) {
            this.stealCountList[0] = this.stealCountList[0] - i.getCount();
            if (this.stealCountList[0] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Items.EXPERIENCE_BOTTLE) && this.stealCountList[1] > 0) {
            this.stealCountList[1] = this.stealCountList[1] - i.getCount();
            if (this.stealCountList[1] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Items.TOTEM_OF_UNDYING) && this.stealCountList[2] > 0) {
            this.stealCountList[2] = this.stealCountList[2] - i.getCount();
            if (this.stealCountList[2] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Items.ENCHANTED_GOLDEN_APPLE) && this.stealCountList[3] > 0) {
            this.stealCountList[3] = this.stealCountList[3] - i.getCount();
            if (this.stealCountList[3] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Blocks.OBSIDIAN.asItem()) && this.stealCountList[4] > 0) {
            this.stealCountList[4] = this.stealCountList[4] - i.getCount();
            if (this.stealCountList[4] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Blocks.COBWEB.asItem()) && this.stealCountList[5] > 0) {
            this.stealCountList[5] = this.stealCountList[5] - i.getCount();
            if (this.stealCountList[5] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Blocks.GLOWSTONE.asItem()) && this.stealCountList[6] > 0) {
            this.stealCountList[6] = this.stealCountList[6] - i.getCount();
            if (this.stealCountList[6] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Blocks.RESPAWN_ANCHOR.asItem()) && this.stealCountList[7] > 0) {
            this.stealCountList[7] = this.stealCountList[7] - i.getCount();
            if (this.stealCountList[7] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Items.ENDER_PEARL) && this.stealCountList[8] > 0) {
            this.stealCountList[8] = this.stealCountList[8] - i.getCount();
            if (this.stealCountList[8] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem() instanceof BlockItem && ((BlockItem)i.getItem()).getBlock() instanceof PistonBlock && this.stealCountList[9] > 0) {
            this.stealCountList[9] = this.stealCountList[9] - i.getCount();
            if (this.stealCountList[9] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Blocks.REDSTONE_BLOCK.asItem()) && this.stealCountList[10] > 0) {
            this.stealCountList[10] = this.stealCountList[10] - i.getCount();
            if (this.stealCountList[10] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem() instanceof BlockItem && ((BlockItem)i.getItem()).getBlock() instanceof BedBlock && this.stealCountList[11] > 0) {
            this.stealCountList[11] = this.stealCountList[11] - i.getCount();
            if (this.stealCountList[11] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (Item.getRawId((Item)i.getItem()) == Item.getRawId((Item)Items.SPLASH_POTION)) {
            PotionContentsComponent potionContentsComponent = (PotionContentsComponent)i.getOrDefault(DataComponentTypes.POTION_CONTENTS, (Object)PotionContentsComponent.DEFAULT);
            for (StatusEffectInstance effect : potionContentsComponent.getEffects()) {
                if (effect.getEffectType().value() == StatusEffects.SPEED.value()) {
                    if (this.stealCountList[12] <= 0) continue;
                    this.stealCountList[12] = this.stealCountList[12] - i.getCount();
                    if (this.stealCountList[12] < 0) {
                        return Type.Stack;
                    }
                    return Type.QuickMove;
                }
                if (effect.getEffectType().value() == StatusEffects.RESISTANCE.value()) {
                    if (this.stealCountList[13] <= 0) continue;
                    this.stealCountList[13] = this.stealCountList[13] - i.getCount();
                    if (this.stealCountList[13] < 0) {
                        return Type.Stack;
                    }
                    return Type.QuickMove;
                }
                if (effect.getEffectType().value() != StatusEffects.STRENGTH.value() || this.stealCountList[14] <= 0) continue;
                this.stealCountList[14] = this.stealCountList[14] - i.getCount();
                if (this.stealCountList[14] < 0) {
                    return Type.Stack;
                }
                return Type.QuickMove;
            }
        }
        return Type.None;
    }

    private void placeBlock(BlockPos pos) {
        if (pos == null || AutoRegear.mc.world == null || AutoRegear.mc.player == null) {
            return;
        }
        AntiRegear.INSTANCE.safe.add(pos);
        BlockUtil.clickBlock(pos.offset(Direction.DOWN), Direction.UP, this.rotate.getValue());
    }

    // Find item in player inventory and return slot number
    private int findItemInInventory(String itemId) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = AutoRegear.mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            String stackId = Registries.ITEM.getId(stack.getItem()).toString();
            if (stackId.equals(itemId)) {
                return i;
            }
        }
        return -1;
    }

    // Swap two slots in player inventory using QUICK_MOVE
    private void swapInventorySlots(int slot1, int slot2) {
        ScreenHandler screenHandler = AutoRegear.mc.player.currentScreenHandler;
        if (screenHandler == null) return;

        // Quick move from slot1 to temp location
        AutoRegear.mc.interactionManager.clickSlot(screenHandler.syncId, slot1, 0, SlotActionType.QUICK_MOVE, (PlayerEntity)AutoRegear.mc.player);
        // Quick move from slot2 to slot1
        AutoRegear.mc.interactionManager.clickSlot(screenHandler.syncId, slot2, 0, SlotActionType.QUICK_MOVE, (PlayerEntity)AutoRegear.mc.player);
        // Quick move from temp (now slot1) to slot2
        AutoRegear.mc.interactionManager.clickSlot(screenHandler.syncId, slot1, 0, SlotActionType.QUICK_MOVE, (PlayerEntity)AutoRegear.mc.player);
    }

    private static enum Type {
        None,
        Stack,
        QuickMove;

    }
}

