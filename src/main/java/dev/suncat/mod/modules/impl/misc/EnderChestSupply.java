/*
 * SunCat Client - EnderChestSupply Module
 * 末影箱补给模块
 *
 * 功能:
 * - 自动在背包或快捷栏找末影箱
 * - 自动放置并打开末影箱
 * - 自动从末影箱拿取潜影箱到背包
 * - 拿完后关闭末影箱
 */
package dev.suncat.mod.modules.impl.misc;

import dev.suncat.suncat;
import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.UpdateEvent;
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
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class EnderChestSupply extends Module {
    public static EnderChestSupply INSTANCE;

    // 潜影箱设置
    private final EnumSetting<ShulkerColor> shulkerColor = this.add(new EnumSetting<>("ShulkerColor", ShulkerColor.PURPLE));
    
    // 操作设置
    private final BooleanSetting autoClose = this.add(new BooleanSetting("AutoClose", true));
    private final BooleanSetting rotate = this.add(new BooleanSetting("Rotate", true));
    private final BooleanSetting debug = this.add(new BooleanSetting("Debug", false));

    // 状态变量
    private boolean isProcessing = false;
    private int processStep = 0;
    private int waitTicks = 0;
    private BlockPos enderChestPos = null;
    private int originalSlot = -1;
    private int enderChestSlot = -1;
    private boolean needRestore = false;

    public EnderChestSupply() {
        super("EnderChestSupply", Category.Misc);
        this.setChinese("末影箱补给");
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
        needRestore = false;
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
            originalSlot = mc.player.getInventory().selectedSlot;
            startProcess();
        }
    }

    private void startProcess() {
        if (debug.getValue()) {
            sendMessage("§a[ECSupply] §7开始末影箱补给流程");
        }

        isProcessing = true;
        processStep = 0;
        waitTicks = 0;
        enderChestPos = null;
        originalSlot = mc.player.getInventory().selectedSlot;
        enderChestSlot = -1;
        needRestore = false;
    }

    private void processStep() {
        switch (processStep) {
            case 0: findEnderChestInInventory(); break;   // 在背包找末影箱
            case 1: findEnderChestBlock(); break;         // 寻找附近末影箱方块
            case 2: prepareEnderChest(); break;           // 准备末影箱（切换到手上）
            case 3: placeEnderChest(); break;             // 放置末影箱
            case 4: openEnderChest(); break;              // 打开末影箱
            case 5: takeShulkersFromEnderChest(); break;  // 从末影箱拿取潜影箱
            case 6: closeEnderChest(); break;             // 关闭末影箱
            case 7: restoreEnderChest(); break;           // 归还末影箱
            case 8: finishProcess(); break;               // 完成
        }
    }

    /**
     * 在背包中查找末影箱
     */
    private void findEnderChestInInventory() {
        // 先检查手上是否有末影箱
        if (mc.player.getMainHandStack().getItem() == Items.ENDER_CHEST) {
            if (debug.getValue()) {
                sendMessage("§a[ECSupply] §7手上已经有末影箱");
            }
            processStep = 3;
            waitTicks = 2;
            return;
        }

        // 在背包中找末影箱
        enderChestSlot = InventoryUtil.findBlock(Blocks.ENDER_CHEST);
        if (enderChestSlot == -1) {
            if (debug.getValue()) {
                sendMessage("§c[ECSupply] §7背包中没有末影箱");
            }
            processStep = 8;
            return;
        }

        if (debug.getValue()) {
            sendMessage("§a[ECSupply] §7在背包中找到末影箱 (槽位：" + enderChestSlot + ")");
        }

        processStep = 1;
        waitTicks = 1;
    }

    /**
     * 寻找附近的末影箱方块
     */
    private void findEnderChestBlock() {
        if (mc.world == null) {
            processStep = 2;
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
                            sendMessage("§a[ECSupply] §7发现附近末影箱 at " + enderChestPos.toShortString());
                        }
                        processStep = 4;  // 直接打开
                        waitTicks = 2;
                        return;
                    }
                }
            }
        }

        // 未找到末影箱，需要自己放置
        if (debug.getValue()) {
            sendMessage("§e[ECSupply] §7附近未找到末影箱，准备放置");
        }
        processStep = 2;
        waitTicks = 1;
    }

    /**
     * 准备末影箱（切换到手上）
     */
    private void prepareEnderChest() {
        // 检查手上是否已经有末影箱
        if (mc.player.getMainHandStack().getItem() == Items.ENDER_CHEST) {
            processStep = 3;
            waitTicks = 2;
            return;
        }

        // 切换末影箱到手上
        InventoryUtil.switchToSlot(enderChestSlot);
        needRestore = true;

        if (debug.getValue()) {
            sendMessage("§a[ECSupply] §7已切换末影箱到手上");
        }

        processStep = 3;
        waitTicks = 3;
    }

    /**
     * 放置末影箱
     */
    private void placeEnderChest() {
        if (mc.player == null || mc.world == null) {
            processStep = 8;
            return;
        }

        // 检查手上是否有末影箱
        ItemStack mainHandStack = mc.player.getMainHandStack();
        if (mainHandStack.getItem() != Items.ENDER_CHEST) {
            if (debug.getValue()) {
                sendMessage("§c[ECSupply] §7手上没有末影箱，无法放置");
            }
            processStep = 8;
            return;
        }

        // 在玩家前方寻找放置位置
        BlockPos playerPos = mc.player.getBlockPos();
        Direction facing = mc.player.getHorizontalFacing();

        BlockPos[] testPositions = {
            playerPos.offset(facing),
            playerPos.offset(facing.rotateYClockwise()),
            playerPos.offset(facing.rotateYCounterclockwise())
        };

        for (BlockPos pos : testPositions) {
            if (mc.world.getBlockState(pos).isAir() &&
                !mc.world.getBlockState(pos.down()).isAir() &&
                BlockUtil.canPlace(pos)) {
                enderChestPos = pos;
                break;
            }
        }

        if (enderChestPos == null) {
            if (debug.getValue()) {
                sendMessage("§c[ECSupply] §7未找到合适的放置位置");
            }
            processStep = 8;
            return;
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
            sendMessage("§a[ECSupply] §7已放置末影箱 at " + enderChestPos.toShortString());
        }

        processStep = 4;
        waitTicks = 3;
    }

    /**
     * 打开末影箱
     */
    private void openEnderChest() {
        if (enderChestPos == null || mc.world == null) {
            processStep = 8;
            return;
        }

        if (rotate.getValue()) {
            suncat.ROTATION.lookAt(enderChestPos, Direction.UP);
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
            sendMessage("§a[ECSupply] §7正在打开末影箱...");
        }

        processStep = 5;
        waitTicks = 5;
    }

    /**
     * 从末影箱拿取潜影箱
     */
    private void takeShulkersFromEnderChest() {
        if (!(mc.currentScreen instanceof GenericContainerScreen)) {
            if (debug.getValue()) {
                sendMessage("§c[ECSupply] §7未能打开末影箱界面");
            }
            processStep = 8;
            return;
        }

        GenericContainerScreenHandler handler = ((GenericContainerScreen) mc.currentScreen).getScreenHandler();
        Item targetShulker = getShulkerBoxItem(shulkerColor.getValue());

        // 计算背包中的空位数量
        int emptySlots = countEmptySlots();

        if (emptySlots == 0) {
            if (debug.getValue()) {
                sendMessage("§c[ECSupply] §7背包没有空位，无法拿取潜影箱");
            }
            processStep = 6;
            return;
        }

        int takenCount = 0;

        // 从末影箱中拿取潜影箱到背包空位
        for (int slot = 0; slot <= 26; slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (stack.getItem() == targetShulker) {
                if (takenCount >= emptySlots) {
                    break;
                }

                // 点击拿取
                mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
                waitTicks = 1;

                // 找到背包空位并放入
                int emptySlot = findEmptySlotInInventory();
                if (emptySlot != -1) {
                    mc.interactionManager.clickSlot(handler.syncId, emptySlot, 0, SlotActionType.PICKUP, mc.player);
                    waitTicks = 1;
                    takenCount++;

                    if (debug.getValue()) {
                        sendMessage("§a[ECSupply] §7已拿取 1 个 " + shulkerColor.getValue().getDisplayName() + " 潜影箱");
                    }
                }
            }
        }

        if (takenCount == 0) {
            if (debug.getValue()) {
                sendMessage("§c[ECSupply] §7末影箱中没有 " + shulkerColor.getValue().getDisplayName() + " 潜影箱");
            }
        } else {
            if (debug.getValue()) {
                sendMessage("§a[ECSupply] §7共拿取 " + takenCount + " 个 " + shulkerColor.getValue().getDisplayName() + " 潜影箱");
            }
        }

        processStep = 6;
        waitTicks = 3;
    }

    /**
     * 关闭末影箱
     */
    private void closeEnderChest() {
        if (autoClose.getValue() && mc.currentScreen instanceof GenericContainerScreen) {
            mc.player.closeHandledScreen();
            if (debug.getValue()) {
                sendMessage("§a[ECSupply] §7已关闭末影箱");
            }
        }
        processStep = 7;
        waitTicks = 2;
    }

    /**
     * 归还末影箱到背包
     */
    private void restoreEnderChest() {
        if (needRestore && enderChestSlot != -1) {
            // 检查手上是否拿着末影箱
            if (mc.player.getMainHandStack().getItem() == Items.ENDER_CHEST) {
                // 切换回原来的槽位
                InventoryUtil.switchToSlot(enderChestSlot);
                if (debug.getValue()) {
                    sendMessage("§a[ECSupply] §7已将末影箱放回原位置");
                }
            }
        }
        processStep = 8;
        waitTicks = 1;
    }

    /**
     * 完成流程
     */
    private void finishProcess() {
        isProcessing = false;
        processStep = 0;
        enderChestPos = null;
        waitTicks = 0;
        
        // 恢复原来的快捷栏槽位
        if (originalSlot != -1) {
            mc.player.getInventory().selectedSlot = originalSlot;
        }
        
        if (debug.getValue()) {
            sendMessage("§a[ECSupply] §7末影箱补给完成！");
        }
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
