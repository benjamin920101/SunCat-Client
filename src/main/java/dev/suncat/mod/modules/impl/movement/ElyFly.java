package dev.suncat.mod.modules.impl.movement;

import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.*;
import dev.suncat.api.utils.player.InventoryUtil;
import dev.suncat.api.utils.player.MovementUtil;
import dev.suncat.api.utils.math.Timer;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import dev.suncat.mod.modules.settings.impl.EnumSetting;
import dev.suncat.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

public class ElyFly extends Module {
    public static ElyFly INSTANCE;

    // Settings
    private final EnumSetting<Mode> mode = this.add(new EnumSetting<>("Mode", Mode.Control));
    private final SliderSetting horizontalSpeed = this.add(new SliderSetting("Horizontal", 3.0, 1.0, 5.0, 0.1, () -> this.mode.is(Mode.Control)));
    private final SliderSetting verticalSpeed = this.add(new SliderSetting("Vertical", 3.0, 1.0, 5.0, 0.1, () -> this.mode.is(Mode.Control)));
    private final BooleanSetting autoStart = this.add(new BooleanSetting("AutoStart", true, () -> this.mode.is(Mode.Control)));
    private final BooleanSetting antiKick = this.add(new BooleanSetting("AntiKick", false, () -> this.mode.is(Mode.Control)));
    private final BooleanSetting pitch = this.add(new BooleanSetting("Pitch", false, () -> this.mode.is(Mode.Grim)));
    private final BooleanSetting firework = this.add(new BooleanSetting("Firework", false, () -> this.mode.is(Mode.Grim)));

    // Timers
    private final Timer antiKickTimer = new Timer();
    private final Timer fireworkDelayTimer = new Timer();

    // State
    private boolean wasFlying = false;

    public ElyFly() {
        super("ElyFly", Category.Movement);
        this.setChinese("鞘翅飞行");
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
        this.wasFlying = false;
    }

    @Override
    public void onDisable() {
        if (this.mode.is(Mode.Grim) && !Module.nullCheck() && mc.player != null && mc.player.isFallFlying()) {
            mc.player.stopFallFlying();
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (Module.nullCheck()) {
            return;
        }

        if (this.mode.is(Mode.Grim)) {
            // Only set pitch for Grim mode, yaw is handled by movement input
            if (this.pitch.getValue()) {
                mc.player.setPitch(getControlPitch());
            }

            // Override onGround status for Grim anti-cheat
            if (mc.player.isFallFlying() && !mc.player.isOnGround()) {
                mc.player.setOnGround(false);
            }
        }
    }

    @EventListener(priority = -9999)
    public void onMove(MoveEvent event) {
        if (Module.nullCheck()) {
            return;
        }

        switch (this.mode.getValue()) {
            case Control:
                handleControlMode(event);
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

        if (this.mode.is(Mode.Grim)) {
            event.setCancelled(true);
        }
    }

    private void handleControlMode(MoveEvent event) {
        if (!mc.player.isFallFlying()) {
            // Auto start elytra flight
            if (this.autoStart.getValue() && !mc.player.isOnGround() && !this.wasFlying) {
                if (hasElytra()) {
                    mc.getNetworkHandler().sendPacket(
                        new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                    mc.player.startFallFlying();
                    this.wasFlying = true;
                }
            }
            return;
        }
        this.wasFlying = true;

        event.setY(0.0);

        // Anti-kick logic
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

        // Horizontal movement
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

    private void handleGrimMove(MoveEvent event) {
        if (!mc.player.isFallFlying()) {
            return;
        }

        // Grim mode acceleration
        float yaw = mc.player.getYaw();
        final double GRIM_AIR_FRICTION = 0.0264444413;
        final double x = GRIM_AIR_FRICTION * Math.cos(Math.toRadians(yaw + 90.0f));
        final double z = GRIM_AIR_FRICTION * Math.sin(Math.toRadians(yaw + 90.0f));
        event.setX(event.getX() + x);
        event.setZ(event.getZ() + z);
    }

    private void handleGrimTravel(TravelEvent event) {
        // Find elytra in inventory
        int slot = InventoryUtil.findItemInventorySlot(Items.ELYTRA);

        if (!isElytraEquipped() && slot == -1) {
            return;
        }

        // Cancel movement if not moving
        if (!MovementUtil.isMoving() &&
            !(mc.options.jumpKey.isPressed() && !mc.options.sneakKey.isPressed()) &&
            !(mc.options.sneakKey.isPressed() && !mc.options.jumpKey.isPressed())) {
            event.setCancelled(true);
            return;
        }

        // Start elytra flight
        if (!mc.player.isFallFlying()) {
            boolean swapBack = false;

            if (!isElytraEquipped()) {
                swapArmor(2, slot);
                swapBack = true;
            }

            // Start flying
            mc.getNetworkHandler().sendPacket(
                new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            mc.player.startFallFlying();

            // Use firework for boosting
            if (this.firework.getValue()) {
                if (!isBoostedByFirework() && this.fireworkDelayTimer.passed(400L) && !mc.player.isOnGround()) {
                    doFirework(slot);
                    this.fireworkDelayTimer.reset();
                }
            }

            if (swapBack) {
                swapArmor(2, slot);
            }
        }

        // Jump on ground
        if (mc.player.isOnGround()) {
            mc.player.jump();
        }
    }

    private boolean hasElytra() {
        if (isElytraEquipped()) {
            return true;
        }
        return InventoryUtil.findItemInventorySlot(Items.ELYTRA) != -1;
    }

    private boolean isElytraEquipped() {
        ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        return chestStack.getItem() instanceof ElytraItem;
    }

    private boolean isBoostedByFirework() {
        return !mc.player.getAbilities().flying && !mc.player.isOnGround();
    }

    private void doFirework(int elytraSlot) {
        int fireworkSlot = InventoryUtil.findItemInventorySlot(Items.FIREWORK_ROCKET);
        if (fireworkSlot == -1) {
            return;
        }

        int oldSlot = mc.player.getInventory().selectedSlot;

        // Swap to elytra if needed
        boolean swappedElytra = false;
        if (!isElytraEquipped()) {
            swapArmor(2, elytraSlot);
            swappedElytra = true;
        }

        // Use firework
        InventoryUtil.switchToSlot(fireworkSlot);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);

        // Swap back
        InventoryUtil.switchToSlot(oldSlot);

        if (swappedElytra) {
            swapArmor(2, elytraSlot);
        }
    }

    private void swapArmor(int armorSlot, int inSlot) {
        int slot = inSlot;
        if (slot < 9) {
            slot += 36;
        }

        int syncId = mc.player.currentScreenHandler.syncId;
        int armorSlotId = 8 - armorSlot;

        mc.interactionManager.clickSlot(syncId, slot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, armorSlotId, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }

    private float getControlPitch() {
        if (isBoostedByFirework()) {
            if (mc.options.jumpKey.isPressed() && !mc.options.sneakKey.isPressed()) {
                if (MovementUtil.isMoving()) {
                    return -50f;
                } else {
                    return -90.0f;
                }
            } else if (mc.options.sneakKey.isPressed() && !mc.options.jumpKey.isPressed()) {
                if (MovementUtil.isMoving()) {
                    return 50f;
                } else {
                    return 90.0f;
                }
            } else {
                return 0.0f;
            }
        } else {
            if (mc.options.jumpKey.isPressed() && !mc.options.sneakKey.isPressed()) {
                return -50;
            } else if (mc.options.sneakKey.isPressed() && !mc.options.jumpKey.isPressed()) {
                return 50f;
            } else {
                return 0.1f;
            }
        }
    }

    public static boolean isGrimFlying() {
        if (ElyFly.INSTANCE == null || !ElyFly.INSTANCE.isOn()) {
            return false;
        }
        if (!ElyFly.INSTANCE.mode.is(Mode.Grim)) {
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
        Control,
        Grim
    }
}
