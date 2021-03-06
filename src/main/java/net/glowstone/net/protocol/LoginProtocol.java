package net.glowstone.net.protocol;

import net.glowstone.GlowServer;
import net.glowstone.net.codec.JsonCodec;
import net.glowstone.net.codec.login.EncryptionKeyRequestCodec;
import net.glowstone.net.codec.login.EncryptionKeyResponseCodec;
import net.glowstone.net.codec.login.LoginStartCodec;
import net.glowstone.net.codec.login.LoginSuccessCodec;
import net.glowstone.net.handler.login.EncryptionKeyResponseHandler;
import net.glowstone.net.handler.login.LoginStartHandler;
import net.glowstone.net.message.KickMessage;
import net.glowstone.net.message.login.EncryptionKeyRequestMessage;
import net.glowstone.net.message.login.EncryptionKeyResponseMessage;
import net.glowstone.net.message.login.LoginStartMessage;
import net.glowstone.net.message.login.LoginSuccessMessage;

public final class LoginProtocol extends GlowProtocol {
    public LoginProtocol(GlowServer server) {
        super(server, "LOGIN", 5);

        inbound(0x00, LoginStartMessage.class, LoginStartCodec.class, LoginStartHandler.class);
        inbound(0x01, EncryptionKeyResponseMessage.class, EncryptionKeyResponseCodec.class, EncryptionKeyResponseHandler.class);

        outbound(0x00, KickMessage.class, JsonCodec.class);
        outbound(0x01, EncryptionKeyRequestMessage.class, EncryptionKeyRequestCodec.class);
        outbound(0x02, LoginSuccessMessage.class, LoginSuccessCodec.class);
    }
}
