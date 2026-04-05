package dev.suncat.mod.modules.impl.combat;

import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.UpdateEvent;
import dev.suncat.api.utils.math.Timer;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import dev.suncat.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * TPAuraPlus - 从 Zenith 完整移植的传送光环模块
 * 传送到目标附近并攻击
 */
public class TPAuraPlus extends Module {
    public static TPAuraPlus INSTANCE;

    private final SliderSetting range = add(new SliderSetting("Range", 10, 0, 50));
    private final SliderSetting tpRange = add(new SliderSetting("TPRange", 5, 0, 20));
    private final SliderSetting cps = add(new SliderSetting("CPS", 10, 1, 20));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", true));
    private final BooleanSetting packet = add(new BooleanSetting("Packet", true));
    private final BooleanSetting onlyVisible = add(new BooleanSetting("OnlyVisible", false));

    private final Timer attackTimer = new Timer();
    private PlayerEntity target = null;

    public TPAuraPlus() {
        super("TPAuraPlus", Category.Combat);
        this.setChinese("传送光环Plus");
        INSTANCE = this;
    }

    @Override
    public void onDisable() {
        target = null;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (Module.nullCheck()) {
            return;
        }
        target = getTarget();
        if (target == null) return;

        double dist = target.distanceTo(mc.player);
        if (dist > tpRange.getValue() && dist <= range.getValue()) {
            // 传送到目标附近
            Vec3d targetPos = target.getPos().add(
                    Math.sin(Math.toRadians(mc.player.getYaw())) * 2,
                    0,
                    -Math.cos(Math.toRadians(mc.player.getYaw())) * 2
            );

            if (packet.getValue()) {
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        targetPos.x, targetPos.y, targetPos.z, mc.player.isOnGround()
                ));
            } else {
                mc.player.setPosition(targetPos.x, targetPos.y, targetPos.z);
            }

            if (rotate.getValue()) {
                mc.player.setYaw((float) Math.toDegrees(Math.atan2(
                        target.getZ() - mc.player.getZ(),
                        target.getX() - mc.player.getX()
                )));
                mc.player.setPitch((float) Math.toDegrees(-Math.atan2(
                        target.getY() - mc.player.getEyeY(),
                        Math.sqrt(Math.pow(target.getX() - mc.player.getX(), 2) + Math.pow(target.getZ() - mc.player.getZ(), 2))
                )));
            }
        }

        // 攻击
        if (dist <= 5 && attackTimer.passedMs(1000L / (long) cps.getValue())) {
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
            attackTimer.reset();
        }
    }

    private PlayerEntity getTarget() {
        return mc.world.getPlayers().stream()
                .filter(p -> p != mc.player && p.isAlive() && p.distanceTo(mc.player) <= range.getValue())
                .filter(p -> !onlyVisible.getValue() || mc.player.canSee(p))
                .min((a, b) -> Float.compare(a.distanceTo(mc.player), b.distanceTo(mc.player)))
                .orElse(null);
    }

    @Override
    public String getInfo() {
        if (target != null) {
            return target.getName().getString();
        }
        return null;
    }
}
