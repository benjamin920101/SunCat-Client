/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.minecraft.client.input.KeyboardInput
 *  net.minecraft.client.option.KeyBinding
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.Redirect
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package dev.suncat.asm.mixins;

import dev.suncat.suncat;
import dev.suncat.api.events.impl.InputEvent;
import dev.suncat.api.events.impl.KeyboardInputEvent;
import dev.suncat.api.events.impl.SneakEvent;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={KeyboardInput.class})
public abstract class MixinKeyboardInput {
    @Inject(method={"tick"}, at={@At(value="HEAD")}, cancellable=true)
    private void hookTickPre(boolean slowDown, float slowDownFactor, CallbackInfo info) {
        InputEvent event = InputEvent.get((net.minecraft.client.input.Input)(Object)this);
        suncat.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            info.cancel();
        }
    }

    @Redirect(method={"tick"}, at=@At(value="INVOKE", target="Lnet/minecraft/client/option/KeyBinding;isPressed()Z", ordinal=5), require=0)
    private boolean sneakHook(KeyBinding instance) {
        SneakEvent event = SneakEvent.get();
        suncat.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            return false;
        }
        return instance.isPressed();
    }

    @Inject(method={"tick"}, at={@At(value="FIELD", target="Lnet/minecraft/client/input/KeyboardInput;sneaking:Z", shift=At.Shift.BEFORE)}, cancellable=true)
    private void hookTickPost(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        InputEvent event = InputEvent.get((net.minecraft.client.input.Input)(Object)this);
        suncat.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method={"tick"}, at={@At(value="HEAD")}, cancellable=true)
    private void onKeyboardInput(boolean slowDown, float slowDownFactor, CallbackInfo info) {
        KeyboardInputEvent event = KeyboardInputEvent.get();
        suncat.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            info.cancel();
        }
    }
}

