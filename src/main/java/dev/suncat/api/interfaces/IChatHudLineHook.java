/*
 * Decompiled with CFR 0.152.
 */
package dev.suncat.api.interfaces;

import dev.suncat.api.utils.math.FadeUtils;

public interface IChatHudLineHook {
    public int suncatClient$getMessageId();

    public void suncatClient$setMessageId(int var1);

    public boolean suncatClient$getSync();

    public void suncatClient$setSync(boolean var1);

    public FadeUtils suncatClient$getFade();

    public void suncatClient$setFade(FadeUtils var1);
}

