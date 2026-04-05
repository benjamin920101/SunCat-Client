package dev.suncat.mod.modules.impl.combat;

import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.UpdateEvent;
import dev.suncat.api.utils.player.EntityUtil;
import dev.suncat.api.utils.math.Timer;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import dev.suncat.mod.modules.settings.impl.SliderSetting;
import dev.suncat.suncat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EnemyList extends Module {
    public static EnemyList INSTANCE;
    
    private static final String ENEMIES_FILE = "suncat/enemies.txt";
    private final List<EnemyEntry> enemies = new ArrayList<>();
    private final Timer attackTimer = new Timer();
    
    private final SliderSetting range = this.add(new SliderSetting("Range", 6.0, 1.0, 15.0, 0.1));
    private final SliderSetting delay = this.add(new SliderSetting("Delay", 500, 0, 2000, 50));
    private final BooleanSetting rotate = this.add(new BooleanSetting("Rotate", false));
    
    public EnemyList() {
        super("EnemyList", Category.Combat);
        this.setChinese("敌人列表");
        INSTANCE = this;
        
        // 默认启用
        this.toggle();
        
        // 从文件加载敌人列表
        this.loadEnemies();
    }
    
    /**
     * 通过名字添加敌人（会在玩家进入世界后自动匹配）
     */
    public void addEnemyByName(String name) {
        // 检查是否已存在
        for (EnemyEntry enemy : enemies) {
            if (enemy.name.equalsIgnoreCase(name)) {
                return;
            }
        }
        // 尝试在当前在线玩家中查找
        if (mc.world != null) {
            for (var player : mc.world.getPlayers()) {
                if (player.getName().getString().equalsIgnoreCase(name)) {
                    enemies.add(new EnemyEntry(player.getName().getString(), player.getUuid()));
                    saveEnemies();
                    return;
                }
            }
        }
        // 如果玩家不在线，先保存名字，等上线后匹配
        enemies.add(new EnemyEntry(name, null));
        saveEnemies();
    }
    
    /**
     * 从文件加载敌人列表
     */
    private void loadEnemies() {
        try {
            Path path = Paths.get(ENEMIES_FILE);
            if (!Files.exists(path)) {
                // 文件不存在，创建默认文件
                Files.createDirectories(path.getParent());
                StringBuilder defaultEnemies = new StringBuilder();
                defaultEnemies.append("Router\n");
                defaultEnemies.append("Filter\n");
                defaultEnemies.append("ak1ra5ura\n");
                defaultEnemies.append("玩玩 alex\n");
                Files.write(path, defaultEnemies.toString().getBytes());
                
                enemies.add(new EnemyEntry("Router", null));
                enemies.add(new EnemyEntry("Filter", null));
                enemies.add(new EnemyEntry("ak1ra5ura", null));
                enemies.add(new EnemyEntry("玩玩 alex", null));
                return;
            }
            
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                String name = line.trim();
                if (!name.isEmpty()) {
                    // 尝试在当前在线玩家中查找
                    if (mc.world != null) {
                        for (var player : mc.world.getPlayers()) {
                            if (player.getName().getString().equalsIgnoreCase(name)) {
                                enemies.add(new EnemyEntry(player.getName().getString(), player.getUuid()));
                                continue;
                            }
                        }
                    }
                    // 玩家不在线，保存名字
                    enemies.add(new EnemyEntry(name, null));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 保存敌人列表到文件
     */
    private void saveEnemies() {
        try {
            Path path = Paths.get(ENEMIES_FILE);
            Files.createDirectories(path.getParent());
            
            StringBuilder sb = new StringBuilder();
            for (EnemyEntry enemy : enemies) {
                sb.append(enemy.name).append("\n");
            }
            Files.write(path, sb.toString().getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void onDisable() {
        // 关闭时保存
        saveEnemies();
    }
    
    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck()) return;
        
        // 定期更新在线敌人的 UUID
        for (EnemyEntry enemy : enemies) {
            if (enemy.uuid == null && mc.world != null) {
                for (var player : mc.world.getPlayers()) {
                    if (player.getName().getString().equalsIgnoreCase(enemy.name)) {
                        // UUID 是 final 的，不能重新赋值，跳过
                        break;
                    }
                }
            }
        }
        
        if (!this.attackTimer.passedMs(this.delay.getValueInt())) {
            return;
        }
        
        PlayerEntity target = getTarget();
        if (target == null) return;
        
        if (this.rotate.getValue()) {
            suncat.ROTATION.lookAt(target.getPos());
        }

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        this.attackTimer.reset();
    }
    
    private PlayerEntity getTarget() {
        PlayerEntity best = null;
        double bestDist = this.range.getValue();
        boolean foundEnemy = false;

        // 优先攻击敌人列表中的玩家
        for (EnemyEntry enemy : enemies) {
            if (enemy.uuid == null) {
                // UUID 为空，尝试在线玩家中匹配名字
                for (var player : mc.world.getPlayers()) {
                    if (player.getName().getString().equalsIgnoreCase(enemy.name)) {
                        // UUID 是 final 的，不能重新赋值
                        double dist = mc.player.distanceTo(player);
                        if (dist < bestDist) {
                            best = player;
                            bestDist = dist;
                            foundEnemy = true;
                        }
                        break;
                    }
                }
            } else {
                // 已经有 UUID，直接查找
                for (var player : mc.world.getPlayers()) {
                    if (player.getUuid().equals(enemy.uuid)) {
                        double dist = mc.player.distanceTo(player);
                        if (dist < bestDist) {
                            best = player;
                            bestDist = dist;
                            foundEnemy = true;
                        }
                        break;
                    }
                }
            }
        }

        // 如果找到了敌人列表中的玩家，直接返回
        if (foundEnemy && best != null) {
            return best;
        }

        // 如果没有敌人列表中的玩家，攻击最近的玩家
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity player)) continue;
            if (player == mc.player) continue;
            if (!player.isAlive()) continue;

            double dist = mc.player.distanceTo(player);
            if (dist > bestDist) continue;

            best = player;
            bestDist = dist;
        }

        return best;
    }
    
    public boolean isEnemy(PlayerEntity player) {
        UUID playerUuid = player.getUuid();
        
        // 先通过 UUID 匹配
        for (EnemyEntry enemy : enemies) {
            if (enemy.uuid != null && enemy.uuid.equals(playerUuid)) {
                return true;
            }
        }
        
        // UUID 匹配失败时，通过名字回退匹配（处理不在线时添加的敌人）
        String playerName = player.getName().getString();
        for (EnemyEntry enemy : enemies) {
            if (enemy.uuid == null && enemy.name.equalsIgnoreCase(playerName)) {
                return true;
            }
        }
        
        return false;
    }

    public boolean isEnemy(UUID uuid) {
        if (uuid == null) return false;
        
        for (EnemyEntry enemy : enemies) {
            if (enemy.uuid != null && enemy.uuid.equals(uuid)) {
                return true;
            }
        }
        return false;
    }
    
    public void addEnemy(PlayerEntity player) {
        if (!isEnemy(player)) {
            enemies.add(new EnemyEntry(player.getName().getString(), player.getUuid()));
        }
    }
    
    public void removeEnemy(UUID uuid) {
        enemies.removeIf(e -> e.uuid != null && e.uuid.equals(uuid));
        saveEnemies();
    }
    
    public void clearEnemies() {
        enemies.clear();
        saveEnemies();
    }
    
    public List<EnemyEntry> getEnemies() {
        return new ArrayList<>(enemies);
    }
    
    public static class EnemyEntry {
        public final String name;
        public final UUID uuid;
        
        public EnemyEntry(String name, UUID uuid) {
            this.name = name;
            this.uuid = uuid;
        }
    }
}
