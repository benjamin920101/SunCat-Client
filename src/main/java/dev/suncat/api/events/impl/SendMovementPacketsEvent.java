/*
 * Decompiled with CFR 0.152.
 */
package dev.suncat.api.events.impl;

import dev.suncat.api.events.Event;
import dev.suncat.api.utils.Wrapper;

public class SendMovementPacketsEvent extends Event {
    public static final SendMovementPacketsEvent instance = new SendMovementPacketsEvent();
    private float yaw;
    private float pitch;
    private boolean onGround;
    private boolean modified = false;

    private SendMovementPacketsEvent() {
    }

    public static SendMovementPacketsEvent get(float yaw, float pitch) {
        SendMovementPacketsEvent.instance.yaw = yaw;
        SendMovementPacketsEvent.instance.pitch = pitch;
        SendMovementPacketsEvent.instance.onGround = Wrapper.mc.player.isOnGround();
        SendMovementPacketsEvent.instance.modified = false;
        return instance;
    }

    public float getYaw() {
        return this.yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
        this.modified = true;
    }

    public float getPitch() {
        return this.pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
        this.modified = true;
    }

    public boolean isOnGround() {
        return this.onGround;
    }

    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
        this.modified = true;
    }

    public boolean isModified() {
        return this.modified;
    }
}

