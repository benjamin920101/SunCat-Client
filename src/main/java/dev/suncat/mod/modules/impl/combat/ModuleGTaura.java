package dev.suncat.mod.modules.impl.combat;

import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.UpdateEvent;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import dev.suncat.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;

/**
 * ModuleGTaura - 瞬移杀戮光环
 * 攻击时瞬间传送到目标身边，攻击后瞬间返回
 */
public class ModuleGTaura extends Module {
    public static ModuleGTaura INSTANCE;
    
    private final SliderSetting range = add(new SliderSetting("Range", 6, 1, 10));
    private final BooleanSetting tp = add(new BooleanSetting("TP", true));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", true));
    private final BooleanSetting onlyVisible = add(new BooleanSetting("OnlyVisible", false));
    private final BooleanSetting multiTask = add(new BooleanSetting("MultiTask", true));

    private Vec3d originalPos = null;

    public ModuleGTaura() {
        super("ModuleGTaura", Category.Combat);
        this.setChinese("GT 杀戮光环");
        INSTANCE = this;
    }

    @Override
    public void onDisable() {
        // 关闭时确保位置同步，防止卡虚空中
        if (originalPos != null && mc.player != null) {
             mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                originalPos.x, originalPos.y, originalPos.z, mc.player.isOnGround()));
        }
        originalPos = null;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (Module.nullCheck()) return;

        PlayerEntity target = getTarget();
        
        // 如果没有目标且之前在 TP 状态，尝试返回原位
        if (target == null) {
            if (originalPos != null) {
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    originalPos.x, originalPos.y, originalPos.z, mc.player.isOnGround()));
                originalPos = null;
            }
            return;
        }

        // 记录原始位置
        if (originalPos == null) {
            originalPos = mc.player.getPos();
        }

        // 如果开启了 TP，瞬移到目标身边
        if (tp.getValue()) {
            Vec3d targetPos = target.getPos();
            // 计算攻击位置：目标身后或侧面，避免重叠
            Vec3d attackPos = targetPos.add(target.getRotationVec(1.0f).multiply(-1.2));
            
            // 发包瞬移
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                attackPos.x, attackPos.y, attackPos.z, mc.player.isOnGround()));
        }

        // 旋转
        if (rotate.getValue()) {
            float[] angle = getRotationToEntity(target);
            mc.player.setYaw(angle[0]);
            mc.player.setPitch(angle[1]);
        }

        // 攻击逻辑
        if (mc.player.getAttackCooldownProgress(0.5f) >= 0.9f) {
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
            
            // 攻击后如果开启了 TP，瞬移回原位
            if (tp.getValue() && originalPos != null) {
                 mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    originalPos.x, originalPos.y, originalPos.z, mc.player.isOnGround()));
                 // 重置 originalPos，下一次 onUpdate 重新记录
                 originalPos = null;
            }
        }
    }
    
    private PlayerEntity getTarget() {
         return mc.world.getPlayers().stream()
                .filter(p -> p != mc.player && p.isAlive() && p.distanceTo(mc.player) <= range.getValue())
                .filter(p -> !onlyVisible.getValue() || mc.player.canSee(p))
                .min(Comparator.comparingDouble(e -> e.distanceTo(mc.player)))
                .orElse(null);
    }

    private float[] getRotationToEntity(net.minecraft.entity.Entity entity) {
        double diffX = entity.getX() - mc.player.getX();
        double diffY = entity.getEyeY() - mc.player.getEyeY();
        double diffZ = entity.getZ() - mc.player.getZ();
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(diffY, diffXZ)));
        return new float[]{MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -90, 90)};
    }
}
