package dev.suncat.mod.modules.impl.movement;

import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.JumpEvent;
import dev.suncat.api.events.impl.MoveEvent;
import dev.suncat.api.events.impl.TravelEvent;
import dev.suncat.api.events.impl.UpdateEvent;
import dev.suncat.api.utils.player.InventoryUtil;
import dev.suncat.api.utils.player.MovementUtil;
import dev.suncat.api.utils.player.PlayerUtils;
import dev.suncat.api.utils.math.Timer;
import dev.suncat.core.impl.RotationManager;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.impl.player.MiddleClick;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import dev.suncat.mod.modules.settings.impl.EnumSetting;
import dev.suncat.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;

/**
 * EFly - 从 Sn0w 客户端完整移植的 Flight 模块
 * 包含 Creative 和 Grim 两种飞行模式
 * 独立实现，不依赖 SunCat 的 mixin
 */
public class EFly extends Module {
    public static EFly INSTANCE;

    // 模式选择
    public final EnumSetting<Mode> mode = this.add(new EnumSetting<>("Mode", Mode.Creative));

    // Creative 模式设置
    private final SliderSetting horizontalSpeed = this.add(new SliderSetting("Horizontal", 3.0, 1.0, 5.0, 0.1, () -> this.mode.is(Mode.Creative)));
    private final SliderSetting verticalSpeed = this.add(new SliderSetting("Vertical", 3.0, 1.0, 5.0, 0.1, () -> this.mode.is(Mode.Creative)));
    private final BooleanSetting antiKick = this.add(new BooleanSetting("AntiKick", false, () -> this.mode.is(Mode.Creative)));

    // Grim 模式设置
    public final BooleanSetting pitch = this.add(new BooleanSetting("Pitch", false, () -> this.mode.is(Mode.Grim)));
    public final BooleanSetting firework = this.add(new BooleanSetting("Firework", false, () -> this.mode.is(Mode.Grim)));

    // Timers
    private final Timer antiKickTimer = new Timer();
    private final Timer fireworkDelayTimer = new Timer();

    // Grim air friction constant (from Sn0w - 精确调谐值)
    private static final float GRIM_AIR_FRICTION = 0.0264444413f;

    public EFly() {
        super("EFly", Category.Movement);
        this.setChinese("甲飞");
        INSTANCE = this;
        this.antiKickTimer.setMs(3800);
        this.fireworkDelayTimer.setMs(400);
    }

    @Override
    public String getInfo() {
        return this.mode.getValue().name();
    }

    @Override
    public void onEnable() {
        if (Module.nullCheck()) {
            return;
        }
        this.antiKickTimer.reset();
        this.fireworkDelayTimer.reset();
    }

    @Override
    public void onDisable() {
        // Grim 模式关闭时停止飞行
        if (this.mode.is(Mode.Grim) && !Module.nullCheck() && mc.player != null && mc.player.isFallFlying()) {
            mc.player.stopFallFlying();
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (Module.nullCheck()) {
            return;
        }

        // Grim 模式：每 tick 设置旋转方向（实现站着飞和按键转向）
        if (this.mode.is(Mode.Grim)) {
            float moveYaw = getMoveYaw();
            float controlPitch = getControlPitch();
            RotationManager.INSTANCE.snapAt(moveYaw, controlPitch);
        }
    }

    @EventListener(priority = -9999)
    public void onMove(MoveEvent event) {
        if (Module.nullCheck()) {
            return;
        }

        switch (this.mode.getValue()) {
            case Creative:
                handleCreativeMode(event);
                break;
            case Grim:
                handleGrimMove(event);
                break;
        }
    }

    @EventListener
    public void onTravel(TravelEvent event) {
        if (Module.nullCheck()) {
            return;
        }

        if (this.mode.is(Mode.Grim)) {
            handleGrimTravel(event);
        }
    }

    @EventListener
    public void onJump(JumpEvent event) {
        if (Module.nullCheck()) {
            return;
        }

        // Grim 模式取消原版跳跃
        if (this.mode.is(Mode.Grim)) {
            event.setCancelled(true);
        }
    }

    /**
     * Creative 模式移动处理
     */
    private void handleCreativeMode(MoveEvent event) {
        event.setY(0.0);

        // Anti-kick 逻辑
        if (this.antiKick.getValue() && this.antiKickTimer.passed(3800L)) {
            event.setY(-0.04);
            this.antiKickTimer.reset();
        } else {
            if (mc.options.jumpKey.isPressed()) {
                event.setY(this.verticalSpeed.getValue());
            } else if (mc.options.sneakKey.isPressed()) {
                event.setY(-this.verticalSpeed.getValue());
            }
        }

        // 水平移动
        float speed = this.horizontalSpeed.getValueFloat();
        float forward = mc.player.input.movementForward;
        float strafe = mc.player.input.movementSideways;
        float yaw = mc.player.getYaw();

        if (forward == 0.0f && strafe == 0.0f) {
            event.setX(0.0);
            event.setZ(0.0);
            return;
        }

        double rx = Math.cos(Math.toRadians(yaw + 90.0f));
        double rz = Math.sin(Math.toRadians(yaw + 90.0f));
        event.setX((forward * speed * rx) + (strafe * speed * rz));
        event.setZ((forward * speed * rz) - (strafe * speed * rx));
    }

    /**
     * Grim 模式移动处理
     * Sn0w 原版：无条件调用 doBoost，不检查 isFallFlying()
     */
    private void handleGrimMove(MoveEvent event) {
        // 使用 EFly 自己的 doBoost 逻辑（使用 getMoveYaw 保证旋转一致性）
        applyGrimBoost(event);
    }

    /**
     * Grim 模式加速应用（Sn0w 风格）
     * 使用 getMoveYaw() 而非 mc.player.getYaw()，确保与 RotationManager 一致
     */
    private void applyGrimBoost(MoveEvent event) {
        float yaw = getMoveYaw();
        final double x = GRIM_AIR_FRICTION * Math.cos(Math.toRadians(yaw + 90.0f));
        final double z = GRIM_AIR_FRICTION * Math.sin(Math.toRadians(yaw + 90.0f));
        event.setX(event.getX() + x);
        event.setZ(event.getZ() + z);
    }

    /**
     * Grim 模式移动事件处理
     */
    private void handleGrimTravel(TravelEvent event) {
        // 查找背包中的鞘翅
        int slot = InventoryUtil.findItemInventorySlot(Items.ELYTRA);

        // 如果没有鞘翅且没装备，直接返回
        if (!isElytraEquipped() && slot == -1) {
            return;
        }

        // 如果没在移动且没按跳跃/潜行，取消移动
        if (!MovementUtil.isMoving() &&
            !(mc.options.jumpKey.isPressed() && !mc.options.sneakKey.isPressed()) &&
            !(mc.options.sneakKey.isPressed() && !mc.options.jumpKey.isPressed())) {
            event.setCancelled(true);
            return;
        }

        // 如果不在飞行状态，自动启动
        if (!mc.player.isFallFlying()) {
            boolean swapBack = false;

            // 如果没装备鞘翅，先装备
            if (!isElytraEquipped()) {
                InventoryUtil.swapArmor(2, slot);
                swapBack = true;
            }

            // 发送飞行数据包
            mc.getNetworkHandler().sendPacket(
                new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            mc.player.startFallFlying();

            // 使用烟花加速
            if (this.firework.getValue()) {
                if (!PlayerUtils.isBoostedByFirework() && this.fireworkDelayTimer.passed(400L) && !mc.player.isOnGround()) {
                    PlayerUtils.doFirework();
                    this.fireworkDelayTimer.reset();
                }
            }

            // MiddleClick 烟花支持
            if (MiddleClick.INSTANCE.fireworkSchedule) {
                MiddleClick.INSTANCE.doFirework();
                MiddleClick.INSTANCE.fireworkSchedule = false;
            }

            // 如果需要，换回鞘翅
            if (swapBack) {
                InventoryUtil.swapArmor(2, slot);
            }
        }

        // 在地面上时自动跳跃
        if (mc.player.isOnGround()) {
            PlayerUtils.clientJump();
        }
    }

    /**
     * 获取移动朝向（Sn0w 风格 - 使用按键状态）
     * 按 A 头往左 90 度，按 D 头往右 90 度，实现站着飞
     */
    private float getMoveYaw() {
        float yaw = mc.player.getYaw();

        boolean forward = mc.options.forwardKey.isPressed();
        boolean back = mc.options.backKey.isPressed();
        boolean left = mc.options.leftKey.isPressed();
        boolean right = mc.options.rightKey.isPressed();

        if (forward && !back) {
            if (left && !right) {
                return net.minecraft.util.math.MathHelper.wrapDegrees(yaw - 45.0f);
            } else if (right && !left) {
                return net.minecraft.util.math.MathHelper.wrapDegrees(yaw + 45.0f);
            }
            return yaw;
        } else if (back && !forward) {
            yaw = net.minecraft.util.math.MathHelper.wrapDegrees(yaw + 180.0f);
            if (left && !right) {
                return net.minecraft.util.math.MathHelper.wrapDegrees(yaw - 45.0f);
            } else if (right && !left) {
                return net.minecraft.util.math.MathHelper.wrapDegrees(yaw + 45.0f);
            }
            return yaw;
        } else if (left && !right) {
            return net.minecraft.util.math.MathHelper.wrapDegrees(yaw - 90.0f);
        } else if (right && !left) {
            return net.minecraft.util.math.MathHelper.wrapDegrees(yaw + 90.0f);
        }

        // 没有按键时保持当前朝向（站着飞）
        return yaw;
    }

    /**
     * 获取控制俯仰角
     * 根据跳跃/潜行键调整 pitch，实现上下飞行控制
     */
    private float getControlPitch() {
        if (PlayerUtils.isBoostedByFirework()) {
            // 有烟花加速时
            if (mc.options.jumpKey.isPressed() && !mc.options.sneakKey.isPressed()) {
                // 按跳跃键 - 抬头向上飞
                if (MovementUtil.isMoving()) {
                    return -50f;  // 移动时抬头 -50 度
                } else {
                    return -90.0f;  // 站着时抬头 -90 度（垂直向上）
                }
            } else if (mc.options.sneakKey.isPressed() && !mc.options.jumpKey.isPressed()) {
                // 按潜行键 - 低头向下飞
                if (MovementUtil.isMoving()) {
                    return 50f;  // 移动时低头 50 度
                } else {
                    return 90.0f;  // 站着时低头 90 度（垂直向下）
                }
            } else {
                return 0.0f;  // 没有输入时保持水平
            }
        } else {
            // 没有烟花加速时
            if (mc.options.jumpKey.isPressed() && !mc.options.sneakKey.isPressed()) {
                return -50f;  // 按跳跃键抬头 -50 度
            } else if (mc.options.sneakKey.isPressed() && !mc.options.jumpKey.isPressed()) {
                return 50f;  // 按潜行键低头 50 度
            } else {
                return 0.1f;  // 没有输入时保持水平（微小角度防止检测）
            }
        }
    }

    /**
     * 检查是否装备鞘翅
     */
    private boolean isElytraEquipped() {
        ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        return chestStack.getItem() instanceof ElytraItem;
    }

    /**
     * 静态方法：检查是否正在使用 Grim 模式飞行
     */
    public static boolean isGrimFlying() {
        if (INSTANCE == null || !INSTANCE.isOn()) {
            return false;
        }
        if (!INSTANCE.mode.is(Mode.Grim)) {
            return false;
        }
        int slot = InventoryUtil.findItemInventorySlot(Items.ELYTRA);
        return slot != -1 || isElytraEquippedStatic();
    }

    private static boolean isElytraEquippedStatic() {
        if (mc.player == null) {
            return false;
        }
        ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        return chestStack.getItem() instanceof ElytraItem;
    }

    public enum Mode {
        Creative,
        Grim
    }
}
