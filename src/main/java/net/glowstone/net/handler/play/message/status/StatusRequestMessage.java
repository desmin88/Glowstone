package net.glowstone.net.handler.play.message.status;

import com.flowpowered.networking.Message;

public final class StatusRequestMessage implements Message {

    public StatusRequestMessage(){
    }

    @Override
    public boolean isAsync() {
        return false;  ///TODO
    }
}
