/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.hud.ChatHudLine
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 */
package dev.idhammai.asm.mixins;

import dev.idhammai.api.interfaces.IChatHudLineHook;
import dev.idhammai.api.utils.math.FadeUtils;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value={ChatHudLine.class})
public class MixinChatHudLine
implements IChatHudLineHook {
    @Unique
    private int id = 0;
    @Unique
    private boolean sync = false;
    @Unique
    private FadeUtils fade;

    @Override
    public int suncatClient$getMessageId() {
        return this.id;
    }

    @Override
    public void suncatClient$setMessageId(int id) {
        this.id = id;
    }

    @Override
    public boolean suncatClient$getSync() {
        return this.sync;
    }

    @Override
    public void suncatClient$setSync(boolean sync) {
        this.sync = sync;
    }

    @Override
    public FadeUtils suncatClient$getFade() {
        return this.fade;
    }

    @Override
    public void suncatClient$setFade(FadeUtils fade) {
        this.fade = fade;
    }
}

