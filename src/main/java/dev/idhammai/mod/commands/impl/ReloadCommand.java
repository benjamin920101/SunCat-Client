/*
 * Decompiled with CFR 0.152.
 */
package dev.idhammai.mod.commands.impl;

import dev.idhammai.suncat;
import dev.idhammai.core.impl.ConfigManager;
import dev.idhammai.mod.commands.Command;
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
