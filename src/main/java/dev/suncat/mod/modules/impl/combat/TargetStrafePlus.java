package dev.suncat.mod.modules.impl.combat;

import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.MoveEvent;
import dev.suncat.api.events.impl.UpdateEvent;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import dev.suncat.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * TargetStrafePlus - 从 Zenith 完整移植的目标绕圈模块
 * 围绕目标进行绕圈移动
 */
public class TargetStrafePlus extends Module {
    public static TargetStrafePlus INSTANCE;

    private final SliderSetting range = add(new SliderSetting("Range", 4, 1, 10));
    private final SliderSetting speed = add(new SliderSetting("Speed", 0.5, 0.1, 2));
    private final BooleanSetting autoCircle = add(new BooleanSetting("AutoCircle", true));
    private final BooleanSetting reverse = add(new BooleanSetting("Reverse", false));
    private final BooleanSetting onlyOnGround = add(new BooleanSetting("OnlyGround", false));

    private float angle = 0;

    public TargetStrafePlus() {
        super("TargetStrafePlus", Category.Combat);
        this.setChinese("目标绕圈Plus");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        angle = 0;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (Module.nullCheck()) {
            return;
        }
        if (onlyOnGround.getValue() && !mc.player.isOnGround()) return;

        // 查找目标
        PlayerEntity target = mc.world.getPlayers().stream()
                .filter(p -> p != mc.player && p.isAlive() && p.distanceTo(mc.player) <= range.getValue())
                .min((a, b) -> Float.compare(a.distanceTo(mc.player), b.distanceTo(mc.player)))
                .orElse(null);

        if (target == null) return;

        if (autoCircle.getValue()) {
            angle += (reverse.getValue() ? -1 : 1) * speed.getValueFloat() * 10;
        }
    }

    @EventListener(priority = -9999)
    public void onMove(MoveEvent event) {
        if (Module.nullCheck()) {
            return;
        }
        PlayerEntity target = mc.world.getPlayers().stream()
                .filter(p -> p != mc.player && p.isAlive())
                .min((a, b) -> Float.compare(a.distanceTo(mc.player), b.distanceTo(mc.player)))
                .orElse(null);

        if (target == null) return;

        double targetX = target.getX();
        double targetZ = target.getZ();

        double rad = Math.toRadians(angle);
        double strafeX = -MathHelper.sin((float) rad) * speed.getValue();
        double strafeZ = MathHelper.cos((float) rad) * speed.getValue();

        double newX = targetX + strafeX * range.getValue();
        double newZ = targetZ + strafeZ * range.getValue();

        event.setX(newX - mc.player.getX());
        event.setZ(newZ - mc.player.getZ());
    }
}
