package dev.suncat.mod.modules.impl.misc;

import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.settings.impl.ModuleListSetting;

import java.util.ArrayList;
import java.util.List;

/**
 * HiddenModule - 隐藏模块
 * 可以在模块列表中隐藏指定的模块（不显示在HUD/TabGui中）
 * 支持点击解锁和关闭功能
 */
public class HiddenModule extends Module {
    public static HiddenModule INSTANCE;

    // 选择要隐藏的模块列表
    public final ModuleListSetting hiddenModules = this.add(new ModuleListSetting("Modules", new ArrayList<>()));

    public HiddenModule() {
        super("HiddenModule", Category.Misc);
        this.setChinese("隐藏模块");
        INSTANCE = this;
    }

    /**
     * 检查指定模块是否应该被隐藏
     */
    public static boolean shouldHide(Module module) {
        if (INSTANCE == null || !INSTANCE.isOn()) {
            return false;
        }
        return INSTANCE.hiddenModules.contains(module);
    }

    /**
     * 获取所有被隐藏的模块列表
     */
    public static List<Module> getHiddenModules() {
        if (INSTANCE == null || !INSTANCE.isOn()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(INSTANCE.hiddenModules.getValue());
    }

    /**
     * 切换模块的隐藏状态（解锁/隐藏）
     * @param module 要切换的模块
     */
    public static void toggleHide(Module module) {
        if (INSTANCE == null) return;
        
        if (INSTANCE.hiddenModules.contains(module)) {
            // 解锁模块（从隐藏列表中移除）
            INSTANCE.hiddenModules.remove(module);
            INSTANCE.sendMessage("已解锁: " + module.getDisplayName());
        } else {
            // 隐藏模块（添加到隐藏列表）
            INSTANCE.hiddenModules.add(module);
            INSTANCE.sendMessage("已隐藏: " + module.getDisplayName());
        }
    }

    /**
     * 关闭指定的隐藏模块
     * @param module 要关闭的模块
     */
    public static void disableHiddenModule(Module module) {
        if (module == null) return;
        
        // 如果模块正在运行，关闭它
        if (module.isOn()) {
            module.disable();
            if (INSTANCE != null) {
                INSTANCE.sendMessage("已关闭: " + module.getDisplayName());
            }
        } else {
            if (INSTANCE != null) {
                INSTANCE.sendMessage(module.getDisplayName() + " 未开启");
            }
        }
    }

    /**
     * 解锁并开启指定的模块
     * @param module 要解锁并开启的模块
     */
    public static void unlockAndEnable(Module module) {
        if (module == null) return;
        
        // 从隐藏列表中移除
        if (INSTANCE != null && INSTANCE.hiddenModules.contains(module)) {
            INSTANCE.hiddenModules.remove(module);
        }
        
        // 如果模块未运行，开启它
        if (!module.isOn()) {
            module.enable();
            if (INSTANCE != null) {
                INSTANCE.sendMessage("已解锁并开启: " + module.getDisplayName());
            }
        } else {
            if (INSTANCE != null) {
                INSTANCE.sendMessage("已解锁: " + module.getDisplayName());
            }
        }
    }

    /**
     * 一键关闭所有隐藏的模块
     */
    public static void disableAllHiddenModules() {
        if (INSTANCE == null) return;
        
        List<Module> hiddenModules = new ArrayList<>(INSTANCE.hiddenModules.getValue());
        for (Module module : hiddenModules) {
            if (module.isOn()) {
                module.disable();
            }
        }
        INSTANCE.sendMessage("已关闭所有 " + hiddenModules.size() + " 个隐藏模块");
    }

    /**
     * 一键解锁所有隐藏的模块
     */
    public static void unlockAllHiddenModules() {
        if (INSTANCE == null) return;
        
        int count = INSTANCE.hiddenModules.getValue().size();
        INSTANCE.hiddenModules.getValue().clear();
        INSTANCE.sendMessage("已解锁所有 " + count + " 个隐藏模块");
    }
}
