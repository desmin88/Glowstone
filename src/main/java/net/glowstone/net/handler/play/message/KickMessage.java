package net.glowstone.net.handler.play.message;

import com.flowpowered.networking.Message;
import org.json.simple.JSONObject;

public final class KickMessage implements Message {

    private final String json;

    public KickMessage(String json) {
        this.json = json;
    }

    public KickMessage(JSONObject jsonObject) {
        json = jsonObject.toJSONString();
    }

    public String getJson() {
        return json;
    }

    @Override
    public boolean isAsync() {
        return false;
    }
}
