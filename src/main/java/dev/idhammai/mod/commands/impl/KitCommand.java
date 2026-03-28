package dev.idhammai.mod.commands.impl;

import dev.idhammai.core.impl.KitManager;
import dev.idhammai.mod.modules.impl.combat.AutoRegear;
import dev.idhammai.mod.commands.Command;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class KitCommand extends Command {
    public KitCommand() {
        super("kit", "save [name] | load [name] | list | delete [name]");
    }

    @Override
    public void runCommand(String[] parameters) {
        if (parameters.length == 0) {
            this.sendUsage();
            return;
        }

        switch (parameters[0].toLowerCase()) {
            case "save" -> {
                if (parameters.length == 2) {
                    if (mc.player == null) return;
                    KitManager.saveKit(parameters[1]);
                } else {
                    this.sendUsage();
                }
            }
            case "load" -> {
                if (parameters.length == 2) {
                    if (mc.player == null) return;
                    KitManager.loadKit(parameters[1]);
                    // Set the loaded kit name to AutoRegear
                    if (AutoRegear.INSTANCE != null) {
                        AutoRegear.INSTANCE.currentKitName = parameters[1];
                        this.sendChatMessage("§a[Kit] §7已加载 Kit: §f" + parameters[1] + " §7并设置到 AutoRegear");
                    }
                } else {
                    this.sendUsage();
                }
            }
            case "list" -> {
                File kitsDir = new File("suncat/kits");
                if (!kitsDir.exists()) {
                    this.sendChatMessage("§c[Kit] §7没有找到任何 Kit");
                    return;
                }
                boolean found = false;
                for (File file : kitsDir.listFiles()) {
                    if (!file.getName().endsWith(".json")) continue;
                    String name = file.getName().replace(".json", "");
                    this.sendChatMessage("§a[Kit] §7找到 Kit: §f" + name);
                    found = true;
                }
                if (!found) {
                    this.sendChatMessage("§c[Kit] §7没有找到任何 Kit");
                }
            }
            case "delete" -> {
                if (parameters.length == 2) {
                    File kitsDir = new File("suncat/kits");
                    File kitFile = new File(kitsDir, parameters[1] + ".json");
                    if (kitFile.exists()) {
                        kitFile.delete();
                        this.sendChatMessage("§a[Kit] §7已删除 Kit: §f" + parameters[1]);
                    } else {
                        this.sendChatMessage("§c[Kit] §7未找到 Kit: §f" + parameters[1]);
                    }
                } else {
                    this.sendUsage();
                }
            }
            default -> this.sendUsage();
        }
    }

    @Override
    public String[] getAutocorrect(int count, List<String> seperated) {
        if (count == 1) {
            String input = seperated.getLast().toLowerCase();
            ArrayList<String> correct = new ArrayList<>();
            List<String> list = List.of("save", "load", "list", "delete");
            for (String x : list) {
                if (!x.toLowerCase().startsWith(input)) continue;
                correct.add(x);
            }
            return correct.toArray(new String[0]);
        }
        return null;
    }
}
