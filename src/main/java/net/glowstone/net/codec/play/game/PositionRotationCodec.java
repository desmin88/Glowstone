package net.glowstone.net.codec.play.game;

import com.flowpowered.networking.Codec;
import io.netty.buffer.ByteBuf;
import net.glowstone.net.message.play.game.PositionRotationMessage;

import java.io.IOException;

public final class PositionRotationCodec implements Codec<PositionRotationMessage> {

    @Override
    public PositionRotationMessage decode(ByteBuf buffer) throws IOException {
        double x = buffer.readDouble();
        double y = buffer.readDouble();
        double z = buffer.readDouble();
        float rotation = buffer.readFloat();
        float pitch = buffer.readFloat();
        boolean onGround = buffer.readByte() != 0;

        return new PositionRotationMessage(x, y, z, rotation, pitch, onGround);
    }

    @Override
    public ByteBuf encode(ByteBuf buf, PositionRotationMessage message) throws IOException {
        buf.writeDouble(message.getX());
        buf.writeDouble(message.getY());
        buf.writeDouble(message.getZ());
        buf.writeFloat(message.getRotation());
        buf.writeFloat(message.getPitch());
        buf.writeByte(message.isOnGround() ? 1 : 0);

        return buf;
    }
}
