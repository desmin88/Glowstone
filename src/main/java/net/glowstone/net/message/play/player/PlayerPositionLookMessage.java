package net.glowstone.net.message.play.player;

import org.bukkit.Location;

public final class PlayerPositionLookMessage extends PlayerUpdateMessage {

    private final double x, y, headY,  z;
    private final float yaw, pitch;

    public PlayerPositionLookMessage(boolean onGround, double x, double y,double headY,  double z, float yaw, float pitch) {
        super(onGround);
        this.x = x;
        this.y = y;
        this.headY = headY;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getHeadY() {
        return headY;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    @Override
    public void update(Location location) {
        location.setX(x);
        location.setY(y);
        location.setZ(z);
        location.setYaw(yaw);
        location.setPitch(pitch);
    }
}
