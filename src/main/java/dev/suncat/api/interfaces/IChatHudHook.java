/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.text.Text
 */
package dev.suncat.api.interfaces;

import net.minecraft.text.Text;

public interface IChatHudHook {
    public void suncatClient$addMessage(Text var1, int var2);

    public void suncatClient$addMessage(Text var1);

    public void suncatClient$addMessageOutSync(Text var1, int var2);
}
