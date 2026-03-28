package dev.idhammai.mod.modules.impl.combat;

import dev.idhammai.suncat;
import dev.idhammai.api.events.eventbus.EventListener;
import dev.idhammai.api.events.impl.UpdateEvent;
import dev.idhammai.api.utils.combat.CombatUtil;
import dev.idhammai.api.utils.math.ExplosionUtil;
import dev.idhammai.api.utils.math.Timer;
import dev.idhammai.api.utils.player.EntityUtil;
import dev.idhammai.api.utils.world.BlockPosX;
import dev.idhammai.api.utils.world.BlockUtil;
import dev.idhammai.core.impl.BreakManager;
import dev.idhammai.mod.modules.Module;
import dev.idhammai.mod.modules.impl.exploit.Blink;
import dev.idhammai.mod.modules.settings.impl.BooleanSetting;
import dev.idhammai.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;

public class BurrowAssist extends Module {
    public static BurrowAssist INSTANCE;

    public static Timer delay = new Timer();
    private final SliderSetting Delay = this.add(new SliderSetting("Delay", 100, 0, 1000));
    public final BooleanSetting pause = this.add(new BooleanSetting("UsingPause", true));
    public final SliderSetting speed = this.add(new SliderSetting("MaxSpeed", 8, 0, 20));
    public final BooleanSetting cCheck = this.add(new BooleanSetting("CheckCrystal", true).setParent());
    private final SliderSetting cRange = this.add(new SliderSetting("Range", 5.0, 0.0, 6.0, cCheck::isOpen));
    private final SliderSetting breakMinSelf = this.add(new SliderSetting("BreakSelf", 12.0, 0.0, 36.0, cCheck::isOpen));
    public final BooleanSetting mCheck = this.add(new BooleanSetting("CheckMine", true).setParent());
    public final BooleanSetting mSelf = this.add(new BooleanSetting("Self", true, mCheck::isOpen));
    private final SliderSetting predictTicks = this.add(new SliderSetting("PredictTicks", 4, 0, 10));
    private final BooleanSetting terrainIgnore = this.add(new BooleanSetting("TerrainIgnore", true));

    public final HashMap<PlayerEntity, Double> playerSpeeds = new HashMap<>();

    public BurrowAssist() {
        super("BurrowAssist", Category.Combat);
        this.setChinese("埋身助手");
        INSTANCE = this;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck()) return;
        if (!delay.passed((long) Delay.getValue())) return;
        if (pause.getValue() && mc.player.isUsingItem()) {
            return;
        }
        if (mc.options.jumpKey.isPressed()) {
            return;
        }
        if (!Burrow.INSTANCE.hasBurrowItem) return;
        if (!canBurrow()) {
            return;
        }
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) return;
        if (mc.player.isOnGround() &&
                getPlayerSpeed(mc.player) < speed.getValueInt() &&
                (cCheck.getValue() && mCheck.getValue() ? (findCrystal() || checkMine(mSelf.getValue())) : ((!cCheck.getValue() || findCrystal()) && (!mCheck.getValue() || checkMine(mSelf.getValue()))))) {

            if (Burrow.INSTANCE.isOn()) return;
            Burrow.INSTANCE.enable();
            delay.reset();
        }
    }

    public boolean findCrystal() {
        PlayerAndPredict self = new PlayerAndPredict(mc.player);
        for (Entity crystal : mc.world.getEntities()) {
            if (!(crystal instanceof EndCrystalEntity)) continue;
            if (mc.player.getEyePos().distanceTo(crystal.getPos()) > cRange.getValue()) continue;
            float selfDamage = calculateDamage(crystal.getPos(), self.player, self.predict);
            if (selfDamage < breakMinSelf.getValue()) continue;
            return true;
        }
        return false;
    }

    public double getPlayerSpeed(PlayerEntity player) {
        if (playerSpeeds.get(player) == null) {
            return 0.0;
        }
        return turnIntoKpH(playerSpeeds.get(player));
    }

    public double turnIntoKpH(double input) {
        return (double) MathHelper.sqrt((float) input) * 71.2729367892;
    }

    public float calculateDamage(Vec3d pos, PlayerEntity player, PlayerEntity predict) {
        if (terrainIgnore.getValue()) {
            CombatUtil.terrainIgnore = true;
        }
        float damage = ExplosionUtil.calculateDamage(pos, (LivingEntity) player, (LivingEntity) predict, 6f);
        CombatUtil.terrainIgnore = false;
        return damage;
    }

    public boolean checkMine(boolean self) {
        ArrayList<BlockPos> pos = new ArrayList<>();
        pos.add(EntityUtil.getPlayerPos(true));
        pos.add(new BlockPosX(mc.player.getX() + 0.4, mc.player.getY() + 0.5, mc.player.getZ() + 0.4));
        pos.add(new BlockPosX(mc.player.getX() - 0.4, mc.player.getY() + 0.5, mc.player.getZ() + 0.4));
        pos.add(new BlockPosX(mc.player.getX() + 0.4, mc.player.getY() + 0.5, mc.player.getZ() - 0.4));
        pos.add(new BlockPosX(mc.player.getX() - 0.4, mc.player.getY() + 0.5, mc.player.getZ() - 0.4));
        for (BreakManager.BreakData breakData : new HashMap<>(suncat.BREAK.breakMap).values()) {
            if (breakData == null || breakData.getEntity() == null) continue;
            for (BlockPos pos1 : pos) {
                if (pos1.equals(breakData.pos) && breakData.getEntity() != mc.player) {
                    return true;
                }
            }
        }
        // Simplified - just check breakMap
        return self && !suncat.BREAK.breakMap.isEmpty();
    }

    public class PlayerAndPredict {
        PlayerEntity player;
        PlayerEntity predict;
        public PlayerAndPredict(PlayerEntity player) {
            this.player = player;
            // Simplified prediction - just use the player itself
            predict = player;
        }
    }

    private static boolean canBurrow() {
        BlockPos pos1 = new BlockPosX(mc.player.getX() + 0.3, mc.player.getY() + 0.5, mc.player.getZ() + 0.3);
        BlockPos pos2 = new BlockPosX(mc.player.getX() - 0.3, mc.player.getY() + 0.5, mc.player.getZ() + 0.3);
        BlockPos pos3 = new BlockPosX(mc.player.getX() + 0.3, mc.player.getY() + 0.5, mc.player.getZ() - 0.3);
        BlockPos pos4 = new BlockPosX(mc.player.getX() - 0.3, mc.player.getY() + 0.5, mc.player.getZ() - 0.3);
        return Burrow.INSTANCE.canPlacePublic(pos1) || Burrow.INSTANCE.canPlacePublic(pos2) || Burrow.INSTANCE.canPlacePublic(pos3) || Burrow.INSTANCE.canPlacePublic(pos4);
    }
}
