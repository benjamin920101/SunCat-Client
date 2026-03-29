/*
 * SunCat Client - EnderChest Refill Module
 * 末影箱自动补充模块
 *
 * 功能:
 * - 只能在地面使用（检查是否在地面上）
 * - 自动检测附近末影箱，有则直接打开，没有则鬼手放置
 * - 鬼手拿末影箱放下然后打开
 * - 从末影箱中拿取潜影箱到背包空位
 * - 可选择潜影箱颜色
 * - 背包有空位就放，没空位就不放
 * - 拿完后关闭末影箱，不破坏
 * - 末影箱放回原来位置
 * - 支持 Grim v2 绕过
 */
package dev.suncat.mod.modules.impl.misc;

import dev.suncat.suncat;
import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.UpdateEvent;
import dev.suncat.api.utils.math.Timer;
import dev.suncat.api.utils.player.InventoryUtil;
import dev.suncat.api.utils.world.BlockUtil;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import dev.suncat.mod.modules.settings.impl.EnumSetting;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class EnderChestRefill extends Module {
    public static EnderChestRefill INSTANCE;

    // 潜影箱设置
    private final EnumSetting<ShulkerColor> shulkerColor = this.add(new EnumSetting<>("ShulkerColor", ShulkerColor.PURPLE));

    // 操作设置
    private final BooleanSetting autoClose = this.add(new BooleanSetting("AutoClose", true));
    private final BooleanSetting rotate = this.add(new BooleanSetting("Rotate", true));
    private final BooleanSetting debug = this.add(new BooleanSetting("Debug", false));

    // Grim 绕过设置
    private final BooleanSetting grimV2 = this.add(new BooleanSetting("GrimV2", true));
    private final BooleanSetting onGroundBypass = this.add(new BooleanSetting("OnGroundBypass", true, () -> this.grimV2.getValue()));

    // 状态变量
    private boolean isProcessing = false;
    private int processStep = 0;
    private int waitTicks = 0;
    private BlockPos enderChestPos = null;
    private Timer actionTimer = new Timer();
    private int originalSlot = -1;
    private int enderChestSlot = -1;  // 记录末影箱原来的槽位
    private boolean hadShulkerInHand = false;
    private ItemStack originalShulkerStack = ItemStack.EMPTY;
    private boolean needRestoreEnderChest = false;  // 是否需要归还末影箱

    public EnderChestRefill() {
        super("EnderChestRefill", Category.Misc);
        this.setChinese("末影箱补充");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (Module.nullCheck()) {
            this.disable();
            return;
        }
        isProcessing = false;
        processStep = 0;
        enderChestPos = null;
        originalSlot = -1;
        enderChestSlot = -1;
        hadShulkerInHand = false;
        originalShulkerStack = ItemStack.EMPTY;
        needRestoreEnderChest = false;
    }

    @Override
    public void onDisable() {
        isProcessing = false;
        enderChestPos = null;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (Module.nullCheck()) {
            return;
        }

        // 处理中
        if (isProcessing) {
            if (waitTicks > 0) {
                waitTicks--;
                return;
            }
            processStep();
            return;
        }

        // 检查是否按下激活键（使用键）
        if (mc.options.useKey.isPressed()) {
            // 检查手上是否拿着潜影箱或末影箱
            ItemStack mainHandStack = mc.player.getMainHandStack();
            Item item = mainHandStack.getItem();
            
            if (item instanceof BlockItem && ((BlockItem) item).getBlock() instanceof ShulkerBoxBlock) {
                // 手持潜影箱，需要找末影箱补充
                hadShulkerInHand = true;
                originalShulkerStack = mainHandStack.copy();
                originalSlot = mc.player.getInventory().selectedSlot;
                startProcess();
            } else if (item == Items.ENDER_CHEST) {
                // 手持末影箱，直接放置使用
                hadShulkerInHand = false;
                originalShulkerStack = ItemStack.EMPTY;
                originalSlot = mc.player.getInventory().selectedSlot;
                startProcess();
            }
        }
    }

    /**
     * 检查玩家是否在地面上
     */
    private boolean isOnGround() {
        if (mc.player == null || mc.world == null) {
            return false;
        }
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos feetPos = playerPos.down();
        // 检查脚下是否有方块支撑
        return !mc.world.getBlockState(feetPos).isAir();
    }

    private void startProcess() {
        // 检查是否在地面上
        if (!isOnGround()) {
            if (debug.getValue()) {
                sendMessage("§c[ECRefill] §7请站在地面上使用！");
            }
            return;
        }

        if (debug.getValue()) {
            sendMessage("§a[ECRefill] §7开始末影箱补充流程");
        }

        isProcessing = true;
        processStep = 0;
        waitTicks = 0;
        actionTimer.reset();
        enderChestPos = null;
        enderChestSlot = -1;
        needRestoreEnderChest = false;

        originalSlot = mc.player.getInventory().selectedSlot;
        ItemStack mainHandStack = mc.player.getMainHandStack();
        if (mainHandStack.getItem() instanceof BlockItem &&
            ((BlockItem) mainHandStack.getItem()).getBlock() instanceof ShulkerBoxBlock) {
            hadShulkerInHand = true;
            originalShulkerStack = mainHandStack.copy();
        } else {
            hadShulkerInHand = false;
            originalShulkerStack = ItemStack.EMPTY;
        }
    }

    private void processStep() {
        switch (processStep) {
            case 0: findEnderChest(); break;            // 寻找附近末影箱
            case 1: prepareEnderChest(); break;         // 准备末影箱（切换到手上）
            case 2: placeEnderChest(); break;           // 放置末影箱
            case 3: openEnderChest(); break;            // 打开末影箱
            case 4: takeShulkersFromEnderChest(); break; // 从末影箱拿取潜影箱
            case 5: closeEnderChest(); break;           // 关闭末影箱
            case 6: restoreEnderChest(); break;         // 归还末影箱
            case 7: restoreHand(); break;               // 恢复手持物品
            case 8: finishProcess(); break;             // 完成
        }
    }

    /**
     * 寻找附近的末影箱
     */
    private void findEnderChest() {
        if (mc.world == null) {
            processStep = 8;
            return;
        }

        // 搜索周围 5 格范围内的末影箱
        int range = 5;
        BlockPos playerPos = mc.player.getBlockPos();

        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (mc.world.getBlockState(pos).getBlock() == Blocks.ENDER_CHEST) {
                        enderChestPos = pos;
                        if (debug.getValue()) {
                            sendMessage("§a[ECRefill] §7发现附近末影箱 at " + enderChestPos.toShortString());
                        }
                        processStep = 2; // 直接放置（其实是打开）
                        waitTicks = 2;
                        return;
                    }
                }
            }
        }

        // 未找到末影箱，需要自己放置
        if (debug.getValue()) {
            sendMessage("§e[ECRefill] §7附近未找到末影箱，准备自己放置");
        }
        processStep = 1;
        waitTicks = 1;
    }

    private void prepareEnderChest() {
        ItemStack mainHandStack = mc.player.getMainHandStack();

        // 检查手上是否已经有末影箱
        if (mainHandStack.getItem() == Items.ENDER_CHEST) {
            if (debug.getValue()) {
                sendMessage("§a[ECRefill] §7手上已经有末影箱，直接放置");
            }
            processStep = 2;
            waitTicks = 2;
            return;
        }

        // 从背包中找末影箱
        enderChestSlot = InventoryUtil.findBlock(Blocks.ENDER_CHEST);
        if (enderChestSlot == -1) {
            if (debug.getValue()) {
                sendMessage("§c[ECRefill] §7背包中没有末影箱");
            }
            processStep = 8; // 直接完成
            return;
        }

        // 记录原来的槽位
        originalSlot = mc.player.getInventory().selectedSlot;

        // 如果手上拿着潜影箱，先尝试找空位存放或丢到地上
        if (hadShulkerInHand && !originalShulkerStack.isEmpty()) {
            // 找空位
            int emptySlot = findEmptySlotInInventory();
            if (emptySlot != -1) {
                // 有空位，切换到空位让潜影箱自动放入
                mc.player.getInventory().selectedSlot = emptySlot;
                if (debug.getValue()) {
                    sendMessage("§a[ECRefill] §7已切换到空位存放潜影箱");
                }
            } else {
                // 没有空位，丢到地上
                mc.player.dropSelectedItem(true);
                if (debug.getValue()) {
                    sendMessage("§e[ECRefill] §7背包没有空位，将潜影箱放到地上");
                }
            }
            waitTicks = 2;
            return;
        }

        // 切换物品到手上
        InventoryUtil.switchToSlot(enderChestSlot);

        needRestoreEnderChest = true;

        if (debug.getValue()) {
            sendMessage("§a[ECRefill] §7已从背包拿取末影箱 (槽位：" + enderChestSlot + ")");
        }

        processStep = 2;
        waitTicks = 3;
    }

    private void placeEnderChest() {
        if (mc.player == null || mc.world == null) {
            processStep = 8;
            return;
        }

        // 如果已经找到末影箱位置，直接打开（不需要手上拿着末影箱）
        if (enderChestPos != null && mc.world.getBlockState(enderChestPos).getBlock() == Blocks.ENDER_CHEST) {
            if (debug.getValue()) {
                sendMessage("§a[ECRefill] §7直接使用现有末影箱");
            }
            processStep = 3;
            waitTicks = 2;
            return;
        }

        // 需要自己放置末影箱，检查手上是否有末影箱
        ItemStack mainHandStack = mc.player.getMainHandStack();
        if (mainHandStack.getItem() != Items.ENDER_CHEST) {
            if (debug.getValue()) {
                sendMessage("§c[ECRefill] §7手上没有末影箱，无法放置");
            }
            processStep = 8;
            return;
        }

        // 只在玩家脚下前方寻找放置位置
        BlockPos playerPos = mc.player.getBlockPos();
        Direction facing = mc.player.getHorizontalFacing();

        // 优先在玩家正前方放置
        BlockPos[] testPositions = {
            playerPos.offset(facing),
            playerPos.offset(facing.rotateYClockwise()),
            playerPos.offset(facing.rotateYCounterclockwise())
        };

        for (BlockPos pos : testPositions) {
            // 检查是否为空气且可以放置（地面检查）
            if (mc.world.getBlockState(pos).isAir() &&
                !mc.world.getBlockState(pos.down()).isAir() &&
                BlockUtil.canPlace(pos)) {
                enderChestPos = pos;
                break;
            }
        }

        if (enderChestPos == null) {
            if (debug.getValue()) {
                sendMessage("§c[ECRefill] §7未找到合适的地面放置位置");
            }
            processStep = 8;
            return;
        }

        // Grim v2 绕过 - 发送 onGround 假包
        if (grimV2.getValue() && onGroundBypass.getValue()) {
            sendGrimBypassPacket();
        }

        if (rotate.getValue()) {
            suncat.ROTATION.lookAt(enderChestPos, Direction.UP);
        }

        Vec3d hitVec = new Vec3d(
            enderChestPos.getX() + 0.5,
            enderChestPos.getY() + 0.5,
            enderChestPos.getZ() + 0.5
        );

        mc.interactionManager.interactBlock(
            mc.player,
            Hand.MAIN_HAND,
            new BlockHitResult(hitVec, Direction.UP, enderChestPos, false)
        );

        if (debug.getValue()) {
            sendMessage("§a[ECRefill] §7已放置末影箱 at " + enderChestPos.toShortString());
        }

        processStep = 3;
        waitTicks = 3;
    }

    /**
     * 发送 Grim v2 绕过包
     */
    private void sendGrimBypassPacket() {
        // 发送一个 onGround=true 的假包，让 Grim 认为玩家在地面上
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
    }

    private void openEnderChest() {
        if (enderChestPos == null || mc.world == null) {
            processStep = 8;
            return;
        }

        // Grim v2 绕过 - 打开时 also 发送 onGround 包
        if (grimV2.getValue() && onGroundBypass.getValue()) {
            sendGrimBypassPacket();
        }

        mc.interactionManager.interactBlock(
            mc.player,
            Hand.MAIN_HAND,
            new BlockHitResult(
                new Vec3d(
                    enderChestPos.getX() + 0.5,
                    enderChestPos.getY() + 0.5,
                    enderChestPos.getZ() + 0.5
                ),
                Direction.UP,
                enderChestPos,
                false
            )
        );

        if (debug.getValue()) {
            sendMessage("§a[ECRefill] §7正在打开末影箱...");
        }

        processStep = 4;
        waitTicks = 5;
    }

    private void takeShulkersFromEnderChest() {
        if (!(mc.currentScreen instanceof GenericContainerScreen)) {
            if (debug.getValue()) {
                sendMessage("§c[ECRefill] §7未能打开末影箱界面");
            }
            processStep = 8;
            return;
        }

        GenericContainerScreenHandler handler = ((GenericContainerScreen) mc.currentScreen).getScreenHandler();

        // 获取目标颜色的潜影箱
        Item targetShulker = getShulkerBoxItem(shulkerColor.getValue());

        // 计算背包中的空位数量
        int emptySlots = countEmptySlots();

        if (emptySlots == 0) {
            if (debug.getValue()) {
                sendMessage("§c[ECRefill] §7背包没有空位，无法拿取潜影箱");
            }
            processStep = 5;
            return;
        }

        int takenCount = 0;

        // 从末影箱中拿取潜影箱到背包空位
        for (int slot = 0; slot <= 26; slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (stack.getItem() == targetShulker) {
                // 检查是否还有空位
                if (takenCount >= emptySlots) {
                    break;
                }

                int toTake = stack.getCount();
                for (int click = 0; click < toTake; click++) {
                    mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
                    waitTicks = 1;

                    // 找到背包空位并放入
                    int emptySlot = findEmptySlotInInventory();
                    if (emptySlot != -1) {
                        mc.interactionManager.clickSlot(handler.syncId, emptySlot, 0, SlotActionType.PICKUP, mc.player);
                        waitTicks = 1;
                        takenCount++;

                        if (debug.getValue()) {
                            sendMessage("§a[ECRefill] §7已拿取 1 个 " + shulkerColor.getValue().getDisplayName() + " 潜影箱 (剩余空位：" + (emptySlots - takenCount) + ")");
                        }
                    }
                }
            }
        }

        if (takenCount == 0) {
            if (debug.getValue()) {
                sendMessage("§c[ECRefill] §7末影箱中没有 " + shulkerColor.getValue().getDisplayName() + " 潜影箱");
            }
        } else {
            if (debug.getValue()) {
                sendMessage("§a[ECRefill] §7共拿取 " + takenCount + " 个 " + shulkerColor.getValue().getDisplayName() + " 潜影箱");
            }
        }

        processStep = 5;
        waitTicks = 3;
    }

    /**
     * 计算背包中的空位数量
     */
    private int countEmptySlots() {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 查找背包中的第一个空位
     */
    private int findEmptySlotInInventory() {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private void closeEnderChest() {
        if (autoClose.getValue() && mc.currentScreen instanceof GenericContainerScreen) {
            mc.player.closeHandledScreen();
            mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(
                ((GenericContainerScreenHandler) ((GenericContainerScreen) mc.currentScreen).getScreenHandler()).syncId
            ));
            if (debug.getValue()) {
                sendMessage("§a[ECRefill] §7已关闭末影箱");
            }
        }
        processStep = 6;
        waitTicks = 2;
    }

    private void restoreEnderChest() {
        // 如果需要归还末影箱且手上拿着末影箱
        if (needRestoreEnderChest && enderChestSlot != -1) {
            ItemStack mainHandStack = mc.player.getMainHandStack();

            // 检查手上是否拿着末影箱
            if (mainHandStack.getItem() == Items.ENDER_CHEST) {
                // 切换回原来的槽位
                InventoryUtil.switchToSlot(enderChestSlot);
                if (debug.getValue()) {
                    sendMessage("§a[ECRefill] §7已将末影箱放回原位置 (槽位：" + enderChestSlot + ")");
                }
            }
        }
        processStep = 7;
        waitTicks = 1;
    }

    private void restoreHand() {
        if (hadShulkerInHand && originalSlot != -1) {
            int shulkerSlot = -1;
            Item originalShulkerType = originalShulkerStack.getItem();
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).getItem() == originalShulkerType) {
                    shulkerSlot = i;
                    break;
                }
            }
            if (shulkerSlot != -1) {
                InventoryUtil.switchToSlot(shulkerSlot);
                if (debug.getValue()) {
                    sendMessage("§a[ECRefill] §7已恢复手持潜影箱");
                }
            }
        } else if (originalSlot != -1) {
            InventoryUtil.switchToSlot(originalSlot);
        }
        processStep = 8;
        waitTicks = 1;
    }

    private void finishProcess() {
        isProcessing = false;
        processStep = 0;
        enderChestPos = null;
        waitTicks = 0;
        if (debug.getValue()) {
            sendMessage("§a[ECRefill] §7末影箱补充完成！");
        }
    }

    private Item getShulkerBoxItem(ShulkerColor color) {
        switch (color) {
            case WHITE: return Items.WHITE_SHULKER_BOX;
            case ORANGE: return Items.ORANGE_SHULKER_BOX;
            case MAGENTA: return Items.MAGENTA_SHULKER_BOX;
            case LIGHT_BLUE: return Items.LIGHT_BLUE_SHULKER_BOX;
            case YELLOW: return Items.YELLOW_SHULKER_BOX;
            case LIME: return Items.LIME_SHULKER_BOX;
            case PINK: return Items.PINK_SHULKER_BOX;
            case GRAY: return Items.GRAY_SHULKER_BOX;
            case LIGHT_GRAY: return Items.LIGHT_GRAY_SHULKER_BOX;
            case CYAN: return Items.CYAN_SHULKER_BOX;
            case PURPLE: return Items.PURPLE_SHULKER_BOX;
            case BLUE: return Items.BLUE_SHULKER_BOX;
            case BROWN: return Items.BROWN_SHULKER_BOX;
            case GREEN: return Items.GREEN_SHULKER_BOX;
            case RED: return Items.RED_SHULKER_BOX;
            case BLACK: return Items.BLACK_SHULKER_BOX;
            default: return Items.PURPLE_SHULKER_BOX;
        }
    }

    public enum ShulkerColor {
        WHITE("白色"),
        ORANGE("橙色"),
        MAGENTA("品红色"),
        LIGHT_BLUE("淡蓝色"),
        YELLOW("黄色"),
        LIME("黄绿色"),
        PINK("粉红色"),
        GRAY("灰色"),
        LIGHT_GRAY("淡灰色"),
        CYAN("青色"),
        PURPLE("紫色"),
        BLUE("蓝色"),
        BROWN("棕色"),
        GREEN("绿色"),
        RED("红色"),
        BLACK("黑色");

        private final String displayName;

        ShulkerColor(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
