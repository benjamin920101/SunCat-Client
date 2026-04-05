package dev.suncat.mod.modules.impl.combat;

import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.UpdateEvent;
import dev.suncat.api.utils.math.Timer;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import dev.suncat.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * TPBotPlus - 从 Zenith 完整移植的传送机器人模块
 * 自动传送到目标位置
 */
public class TPBotPlus extends Module {
    public static TPBotPlus INSTANCE;

    private final SliderSetting range = add(new SliderSetting("Range", 50, 0, 100));
    private final SliderSetting tpDelay = add(new SliderSetting("TPDelay", 500, 0, 2000));
    private final BooleanSetting autoTarget = add(new BooleanSetting("AutoTarget", true));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", true));

    private final Timer timer = new Timer();
    private PlayerEntity target = null;

    public TPBotPlus() {
        super("TPBotPlus", Category.Combat);
        this.setChinese("传送机器人Plus");
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
        if (!timer.passedMs(tpDelay.getValueInt())) return;

        if (autoTarget.getValue()) {
            target = mc.world.getPlayers().stream()
                    .filter(p -> p != mc.player && p.isAlive() && p.distanceTo(mc.player) <= range.getValue())
                    .min((a, b) -> Float.compare(a.distanceTo(mc.player), b.distanceTo(mc.player)))
                    .orElse(null);
        }

        if (target == null) return;

        // 传送到目标
        Vec3d targetPos = target.getPos().add(
                Math.sin(Math.toRadians(mc.player.getYaw())) * 3,
                0,
                -Math.cos(Math.toRadians(mc.player.getYaw())) * 3
        );

        // 分段传送
        Vec3d startPos = mc.player.getPos();
        double dist = startPos.distanceTo(targetPos);
        int steps = (int) Math.ceil(dist / 10.0);

        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            Vec3d stepPos = startPos.lerp(targetPos, t);
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    stepPos.x, stepPos.y, stepPos.z, mc.player.isOnGround()
            ));
        }

        mc.player.setPosition(targetPos.x, targetPos.y, targetPos.z);

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

        timer.reset();
    }

    @Override
    public String getInfo() {
        if (target != null) {
            return target.getName().getString();
        }
        return null;
    }
}
