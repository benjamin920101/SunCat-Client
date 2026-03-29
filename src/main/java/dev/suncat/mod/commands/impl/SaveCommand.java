/*
 * Decompiled with CFR 0.152.
 */
package dev.suncat.mod.commands.impl;

import dev.suncat.suncat;
import dev.suncat.core.impl.ConfigManager;
import dev.suncat.mod.commands.Command;
import java.util.List;

public class SaveCommand
extends Command {
    public SaveCommand() {
        super("save", "");
    }

    @Override
    public void runCommand(String[] parameters) {
        if (parameters.length == 1) {
            this.sendChatMessage("\u00a7fSaving config named " + parameters[0]);
            ConfigManager.saveCfg(parameters[0]);
        } else {
            this.sendChatMessage("\u00a7fSaving..");
        }
        suncat.save();
    }

    @Override
    public String[] getAutocorrect(int count, List<String> seperated) {
        return null;
    }
}
