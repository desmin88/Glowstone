package net.glowstone.net.handler.play.message.login;

import com.flowpowered.networking.Message;

public final class LoginStartMessage implements Message {

    private final String username;

    public LoginStartMessage(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAsync() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
