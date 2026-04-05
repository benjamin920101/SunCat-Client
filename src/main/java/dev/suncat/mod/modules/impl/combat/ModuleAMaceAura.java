package dev.suncat.mod.modules.impl.combat;

import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.UpdateEvent;
import dev.suncat.api.utils.player.InventoryUtil;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import dev.suncat.mod.modules.settings.impl.EnumSetting;
import dev.suncat.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolItem;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * ModuleAMaceAura - 重锤光环 (反混淆并适配 SunCat)
 * 原逻辑来自 Error404 Addon，支持瞬移蓄力重锤下落攻击
 */
public class ModuleAMaceAura extends Module {
    public static ModuleAMaceAura INSTANCE;

    // 设置项
    private final SliderSetting distanceLimit = this.add(new SliderSetting("DistanceLimit", 10, 1, 128));
    private final SliderSetting range = this.add(new SliderSetting("Range", 6, 1, 10));
    private final SliderSetting maxTarget = this.add(new SliderSetting("MaxTarget", 1, 1, 10));
    private final EnumSetting<Priority> priority = this.add(new EnumSetting<>("Priority", Priority.LowestDistance));
    private final BooleanSetting swingHand = this.add(new BooleanSetting("SwingHand", true));
    private final BooleanSetting tpMace = this.add(new BooleanSetting("TPMace", true));

    private List<Entity> targets = new ArrayList<>();

    public ModuleAMaceAura() {
        super("ModuleAMaceAura", Category.Combat);
        this.setChinese("A 重锤光环");
        INSTANCE = this;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (Module.nullCheck()) return;
        if (mc.interactionManager == null) return;

        updateTargets();
        doAura();
    }

    private void updateTargets() {
        targets = StreamSupport.stream(mc.world.getEntities().spliterator(), false)
                .filter(this::isValidTarget)
                .filter(e -> e.distanceTo(mc.player) <= range.getValue())
                .sorted(getComparator())
                .limit((int) maxTarget.getValue())
                .collect(Collectors.toList());
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == mc.player) return false;
        if (!entity.isAlive()) return false;
        if (entity instanceof PlayerEntity player) {
            return !player.isSpectator();
        }
        return true;
    }

    private Comparator<Entity> getComparator() {
        switch (priority.getValue()) {
            case LowestDistance:
                return Comparator.comparingDouble(e -> e.distanceTo(mc.player));
            case HighestDamage:
                return (e1, e2) -> Float.compare(getWeaponDamage(), getWeaponDamage());
            default:
                return Comparator.comparingDouble(e -> e.distanceTo(mc.player));
        }
    }

    private void doAura() {
        if (targets.isEmpty()) return;

        Vec3d playerPos = mc.player.getPos();
        WeaponInfo weapon = findBestWeapon();

        // 如果是重锤且开启了 TP，执行瞬移蓄力逻辑
        if (weapon.item == Items.MACE && tpMace.getValue()) {
            // 瞬移到空中 30 格
            warp(playerPos.x, playerPos.y, playerPos.z, playerPos.x, playerPos.y + 30.0, playerPos.z);
            // 瞬移回地面（触发下落状态）
            warp(playerPos.x, playerPos.y + 30.0, playerPos.z, playerPos.x, playerPos.y, playerPos.z);
        }

        // 切换武器
        if (weapon.slot != -1) {
            InventoryUtil.switchToSlot(weapon.slot);
        }

        int count = Math.min((int) maxTarget.getValue(), targets.size());
        for (int i = 0; i < count; i++) {
            Entity target = targets.get(i);
            attack(target);
            
            if (swingHand.getValue()) {
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private WeaponInfo findBestWeapon() {
        // 优先找重锤
        int maceSlot = InventoryUtil.findItem(Items.MACE);
        if (maceSlot != -1) {
            return new WeaponInfo(maceSlot, Items.MACE);
        }

        // 找伤害最高的武器
        int bestSlot = -1;
        float bestDamage = 0f;
        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            Item item = stack.getItem();
            float damage = 0f;
            
            if (item instanceof SwordItem sword) {
                damage = (float) sword.getMaterial().getAttackDamage();
            } else if (item instanceof ToolItem tool) {
                damage = (float) tool.getMaterial().getAttackDamage();
            }
            
            if (damage > bestDamage) {
                bestDamage = damage;
                bestSlot = i;
            }
        }
        
        return bestSlot != -1 ? new WeaponInfo(bestSlot, mc.player.getInventory().getStack(bestSlot).getItem()) 
                              : new WeaponInfo(-1, Items.AIR);
    }

    private void attack(Entity entity) {
        mc.interactionManager.attackEntity(mc.player, entity);
    }

    /**
     * 瞬移发包工具方法
     * 注意：实际使用需要配合反作弊绕过逻辑（如分包发送）
     */
    private void warp(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        double dist = Math.sqrt(Math.pow(toX - fromX, 2) + Math.pow(toY - fromY, 2) + Math.pow(toZ - fromZ, 2));
        int limit = (int) distanceLimit.getValue();
        
        if (dist <= limit) {
            mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround(
                toX, toY, toZ, mc.player.isOnGround()));
        } else {
            // 分包瞬移（简化版）
            double steps = Math.ceil(dist / limit);
            for (int i = 1; i <= steps; i++) {
                double t = (double) i / steps;
                double curX = fromX + (toX - fromX) * t;
                double curY = fromY + (toY - fromY) * t;
                double curZ = fromZ + (toZ - fromZ) * t;
                mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround(
                    curX, curY, curZ, mc.player.isOnGround()));
            }
        }
    }

    private float getWeaponDamage() {
        var stack = mc.player.getMainHandStack();
        if (stack.getItem() instanceof SwordItem sword) {
            return (float) sword.getMaterial().getAttackDamage();
        }
        return 0f;
    }

    private static class WeaponInfo {
        int slot;
        Item item;
        WeaponInfo(int slot, Item item) {
            this.slot = slot;
            this.item = item;
        }
    }

    public enum Priority {
        LowestDistance,
        HighestDamage
    }
}
