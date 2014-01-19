package net.glowstone.net.handler.play.message.status;

import com.flowpowered.networking.Message;
import org.json.simple.JSONObject;

public final class StatusResponseMessage implements Message {

    private String json;

    public StatusResponseMessage(String json) {
        this.json = json;
    }

    public StatusResponseMessage(JSONObject jsonObject) {
     this.json = jsonObject.toJSONString();
    }

    public String getJson() {
        return json;
    }

    @Override
    public boolean isAsync() {
        return false;
    }
}
