package dev.suncat.mod.modules.impl.combat;

import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.ClientTickEvent;
import dev.suncat.api.events.impl.Render3DEvent;
import dev.suncat.api.utils.combat.CombatUtil;
import dev.suncat.api.utils.math.Timer;
import dev.suncat.api.utils.path.TPUtils;
import dev.suncat.api.utils.world.BlockUtil;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.impl.exploit.Blink;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import dev.suncat.mod.modules.settings.impl.SliderSetting;
import dev.suncat.mod.modules.settings.impl.ColorSetting;
import dev.suncat.mod.modules.settings.impl.BindSetting;
import dev.suncat.mod.modules.settings.impl.EnumSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import dev.suncat.api.utils.render.Render3DUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MaceMissLite extends Module {
    public static MaceMissLite INSTANCE;
    
    // General Settings
    private final EnumSetting<TargetPriority> priority = this.add(new EnumSetting<TargetPriority>("Priority", TargetPriority.Distance));
    private final SliderSetting range = this.add(new SliderSetting("Range", 20.0, 1.0, 128.0));
    private final SliderSetting moveDistance = this.add(new SliderSetting("MoveDistance", 20.0, 1.0, 128.0));
    private final BooleanSetting swingHand = this.add(new BooleanSetting("SwingHand", false));
    private final BooleanSetting ignoreBots = this.add(new BooleanSetting("IgnoreBots", false));
    private final BooleanSetting ignoreFriends = this.add(new BooleanSetting("IgnoreFriends", true));
    
    // Cooldown Settings
    private final BooleanSetting useCooldown = this.add(new BooleanSetting("UseCooldown", true).setParent());
    private final SliderSetting cooldownBaseTime = this.add(new SliderSetting("CooldownBaseTime", 0.75, 0.1, 1.0, () -> this.useCooldown.getValue()));
    private final SliderSetting attackDelay = this.add(new SliderSetting("AttackDelay", 50, 1, 2000, 1, () -> !this.useCooldown.getValue()));
    
    // Predict Settings
    private final BooleanSetting predict = this.add(new BooleanSetting("Predict", true).setParent());
    private final SliderSetting predictTicks = this.add(new SliderSetting("PredictTicks", 5, 1, 20, 1, () -> this.predict.getValue()));
    
    // TP Settings
    private final SliderSetting vClip1 = this.add(new SliderSetting("VClip1", 10.0, 0.0, 1000.0));
    private final BindSetting forceIgnore = this.add(new BindSetting("ForceIgnore", -1));
    
    // Destroy Armor Settings
    private final SliderSetting ignoreArmorValue = this.add(new SliderSetting("IgnoreArmorValue", 4, 0, 4, 1));
    private final SliderSetting destroyVClip1 = this.add(new SliderSetting("DestroyVClip1", 80.0, 1.0, 200.0));
    private final SliderSetting destroyVClip2 = this.add(new SliderSetting("DestroyVClip2", 120.0, 1.0, 200.0));
    
    // Render Settings
    private final BooleanSetting render = this.add(new BooleanSetting("Render", false).setParent());
    private final SliderSetting renderHeight = this.add(new SliderSetting("RenderHeight", 1.8, 0.5, 3.0, () -> this.render.getValue()));
    private final ColorSetting sideColor = this.add(new ColorSetting("SideColor", new Color(255, 0, 0, 50), () -> this.render.getValue()));
    private final ColorSetting lineColor = this.add(new ColorSetting("LineColor", new Color(255, 0, 0, 255), () -> this.render.getValue()));
    
    private Entity target;
    private final Timer attackTimer = new Timer();
    private Vec3d predictPos;
    private boolean isNaked;
    
    public MaceMissLite() {
        super("MaceMissLite", Module.Category.Combat);
        this.setChinese("锤子 misses 轻量版");
        INSTANCE = this;
    }
    
    @Override
    public void onEnable() {
        this.target = null;
        this.isNaked = false;
    }
    
    @Override
    public void onDisable() {
        this.target = null;
        this.predictPos = null;
    }
    
    @Override
    public String getInfo() {
        if (this.target != null) {
            return this.target.getName().getString();
        }
        return null;
    }
    
    private boolean isReadyToAttack() {
        // 使用攻击计时器来检查
        return this.attackTimer.passed(this.attackDelay.getValueInt());
    }
    
    @EventListener
    public void onTick(ClientTickEvent event) {
        if (MaceMissLite.nullCheck()) return;
        if (event.isPre()) return;
        
        this.updateTarget();
        this.doAura();
    }
    
    private void updateTarget() {
        Entity bestTarget = null;
        double bestValue = Double.MAX_VALUE;
        
        for (Entity entity : mc.world.getEntities()) {
            if (!this.isValidTarget(entity)) continue;
            
            double value = this.getTargetValue(entity);
            if (value < bestValue) {
                bestValue = value;
                bestTarget = entity;
            }
        }
        
        if (bestTarget != null) {
            this.target = bestTarget;
        }
    }
    
    private boolean isValidTarget(Entity entity) {
        if (entity == null || !entity.isAlive()) return false;
        if (entity == mc.player) return false;
        if (entity.isInvulnerable()) return false;
        if (!(entity instanceof PlayerEntity)) return false;
        
        PlayerEntity player = (PlayerEntity) entity;
        if (player.isCreative()) return false;
        
        // Check range
        if (mc.player.distanceTo(entity) > this.range.getValue()) return false;
        
        // Check ignore friends
        if (this.ignoreFriends.getValue() && dev.suncat.suncat.FRIEND.isFriend(player)) return false;
        
        return true;
    }
    
    private double getTargetValue(Entity entity) {
        if (this.priority.is(TargetPriority.Distance)) {
            return mc.player.squaredDistanceTo(entity);
        } else if (this.priority.is(TargetPriority.Health)) {
            return ((LivingEntity) entity).getHealth();
        }
        return mc.player.squaredDistanceTo(entity);
    }
    
    private void doAura() {
        if (!this.isReadyToAttack() || this.target == null) return;
        if (mc.player.distanceTo(this.target) > this.range.getValue()) return;
        if (!this.target.isAlive()) {
            this.isNaked = false;
            return;
        }
        
        // Check if target is about to be ignored (low armor)
        if (this.target instanceof PlayerEntity) {
            PlayerEntity playerTarget = (PlayerEntity) this.target;
            int armorCount = this.getRemainingArmorCount(playerTarget);
            int ignoreValue = this.ignoreArmorValue.getValueInt();

            if (armorCount <= ignoreValue || this.forceIgnore.isPressed()) {
                this.doTpAura(this.target);
            } else {
                this.doDestroyArmor(this.target);
            }
        }
    }
    
    private int getRemainingArmorCount(PlayerEntity player) {
        int count = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack armorStack = player.getInventory().armor.get(i);
            if (!armorStack.isEmpty()) {
                count++;
            }
        }
        return count;
    }
    
    private void doDestroyArmor(Entity target) {
        if (!(target instanceof PlayerEntity)) return;
        
        // Update predict position
        if (this.predict.getValue()) {
            this.predictPos = this.predictPosition(target, this.predictTicks.getValueInt());
        } else {
            this.predictPos = target.getPos();
        }
        
        Vec3d targetVec = this.predictPos;
        Vec3d playerPos = mc.player.getPos();
        
        // Find VClip hole
        Vec3d newMiss = this.findVclipHole(playerPos, this.destroyVClip1.getValue());
        if (newMiss == null) {
            newMiss = this.findVclipHole(playerPos, this.destroyVClip2.getValue());
        }
        
        if (newMiss == null) return;
        
        // Check if mace is in inventory
        int maceSlot = this.findItemInInventory(Items.MACE);
        if (maceSlot == -1) return;
        
        // Switch to mace
        this.switchToSlot(maceSlot);
        
        // Calculate packets needed
        double distToTarget = playerPos.distanceTo(targetVec);
        int maxPackets = (int) Math.ceil(distToTarget / this.moveDistance.getValue());
        
        if (maxPackets > 20) {
            return; // Packet limit
        }
        
        // Send movement packets
        for (int i = 1; i < maxPackets; i++) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                playerPos.x, playerPos.y, playerPos.z, false
            ));
        }
        
        // Teleport to attack position
        this.doTeleport(playerPos, newMiss, this.moveDistance.getValue());
        
        // Send target position packet
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            targetVec.x, targetVec.y, targetVec.z, false
        ));
        
        // Swing hand
        if (this.swingHand.getValue()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
        
        // Attack
        mc.interactionManager.attackEntity(mc.player, target);
        
        // Return to original position
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            playerPos.x, playerPos.y, playerPos.z, false
        ));
        
        // Switch back
        this.switchBack(maceSlot);
        
        // Reset timer
        this.attackTimer.reset();
    }
    
    private void doTpAura(Entity target) {
        // Update predict position
        if (this.predict.getValue()) {
            this.predictPos = this.predictPosition(target, this.predictTicks.getValueInt());
        } else {
            this.predictPos = target.getPos();
        }
        
        Vec3d targetVec = this.predictPos;
        Vec3d playerPos = mc.player.getPos();
        
        // Find VClip hole
        Vec3d newMiss = this.findVclipHole(playerPos, this.vClip1.getValue());
        if (newMiss == null) return;
        
        // Check if mace is in inventory
        int maceSlot = this.findItemInInventory(Items.MACE);
        if (maceSlot == -1) return;
        
        // Switch to mace
        this.switchToSlot(maceSlot);
        
        // Calculate packets needed
        double distToTarget = playerPos.distanceTo(targetVec);
        int maxPackets = (int) Math.ceil(distToTarget / this.moveDistance.getValue());
        
        if (maxPackets > 20) {
            return; // Packet limit
        }
        
        // Send movement packets
        for (int i = 1; i < maxPackets; i++) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                playerPos.x, playerPos.y, playerPos.z, false
            ));
        }
        
        // Teleport to attack position
        this.doTeleport(playerPos, newMiss, this.moveDistance.getValue());
        
        // Send target position packet
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            targetVec.x, targetVec.y, targetVec.z, false
        ));
        
        // Swing hand
        if (this.swingHand.getValue()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
        
        // Attack
        mc.interactionManager.attackEntity(mc.player, target);
        
        // Return to original position
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            playerPos.x, playerPos.y, playerPos.z, false
        ));
        
        // Switch back
        this.switchBack(maceSlot);
        
        // Reset timer
        this.attackTimer.reset();
        
        // Mark as naked if armor is low
        if (target instanceof PlayerEntity) {
            int armorCount = this.getRemainingArmorCount((PlayerEntity) target);
            if (armorCount <= this.ignoreArmorValue.getValueInt()) {
                this.isNaked = true;
                this.sendMessage("§7[Miss] §a目标护甲已成功摧毁");
            }
        }
    }
    
    private Vec3d predictPosition(Entity entity, int ticks) {
        Vec3d velocity = entity.getVelocity();
        Vec3d pos = entity.getPos();
        
        return new Vec3d(
            pos.x + velocity.x * ticks,
            pos.y + velocity.y * ticks,
            pos.z + velocity.z * ticks
        );
    }
    
    private Vec3d findVclipHole(Vec3d fromPos, double vClip) {
        // Simple implementation: try to find a valid position with VClip
        BlockPos playerBlockPos = new BlockPos((int)fromPos.x, (int)fromPos.y, (int)fromPos.z);
        
        // Search in a small radius for a valid hole
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos checkPos = playerBlockPos.add(x, 0, z);
                Vec3d vClipPos = new Vec3d(checkPos.getX() + 0.5, fromPos.y + vClip, checkPos.getZ() + 0.5);
                
                // Check if position is valid (not in block)
                if (mc.world.getBlockState(checkPos).isAir() && 
                    mc.world.getBlockState(checkPos.up()).isAir()) {
                    return vClipPos;
                }
            }
        }
        
        return null;
    }
    
    private void doTeleport(Vec3d from, Vec3d to, double maxDist) {
        double dist = from.distanceTo(to);
        int packets = (int) Math.ceil(dist / maxDist);
        
        for (int i = 0; i < packets; i++) {
            double progress = (double) (i + 1) / packets;
            Vec3d intermediate = from.lerp(to, progress);
            
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                intermediate.x, intermediate.y, intermediate.z, false
            ));
        }
    }
    
    private int findItemInInventory(net.minecraft.item.Item item) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(item)) {
                return i;
            }
        }
        return -1;
    }
    
    private void switchToSlot(int slot) {
        if (slot < 0 || slot >= 36) return;
        if (slot < 9) {
            mc.player.getInventory().selectedSlot = slot;
        } else {
            // Need to swap from inventory to hotbar
            int hotbarSlot = 36 + (slot - 9);
            // Simple switch - just select it
            mc.player.getInventory().selectedSlot = slot < 9 ? slot : (slot - 27);
        }
    }
    
    private void switchBack(int slot) {
        // Restore original slot (simplified)
    }
    
    @EventListener
    public void onRender3D(Render3DEvent event) {
        if (!this.render.getValue()) return;
        if (this.predictPos == null || this.target == null) return;
        
        MatrixStack matrices = event.matrixStack;
        matrices.push();
        
        double x = this.predictPos.x - MathHelper.lerp(event.tickDelta, 
            this.target.prevX, this.target.getX());
        double y = this.predictPos.y - MathHelper.lerp(event.tickDelta, 
            this.target.prevY, this.target.getY());
        double z = this.predictPos.z - MathHelper.lerp(event.tickDelta, 
            this.target.prevZ, this.target.getZ());
        
        Box box = new Box(x - 0.3, y, z - 0.3, x + 0.3, y + this.renderHeight.getValue(), z + 0.3);
        
        // 使用 draw3DBox 方法来同时渲染填充和线条
        Render3DUtil.draw3DBox(matrices, box, 
            this.sideColor.getValue(), this.lineColor.getValue());
        
        matrices.pop();
    }
    
    public enum TargetPriority {
        Distance,
        Health
    }
}
