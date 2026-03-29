/*
 * Decompiled with CFR 0.152.
 */
package dev.suncat.mod.commands.impl;

import dev.suncat.suncat;
import dev.suncat.core.impl.ConfigManager;
import dev.suncat.mod.commands.Command;
import java.util.List;

public class ReloadCommand
extends Command {
    public ReloadCommand() {
        super("reload", "");
    }

    @Override
    public void runCommand(String[] parameters) {
        this.sendChatMessage("\u00a7fReloading..");
        suncat.CONFIG = new ConfigManager();
        suncat.CONFIG.load();
        suncat.CLEANER.read();
        suncat.XRAY.read();
        suncat.TRADE.read();
        suncat.FRIEND.read();
    }

    @Override
    public String[] getAutocorrect(int count, List<String> seperated) {
        return null;
    }
}
