package net.glowstone.net;

import com.flowpowered.networking.NetworkServer;
import com.flowpowered.networking.session.Session;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import net.glowstone.GlowServer;

public class GlowNetworkServer extends NetworkServer {

    private final GlowServer server;

    public GlowNetworkServer(GlowServer server) {
        this.server = server;
    }

    @Override
    public Session newSession(Channel c) {
        System.out.println("newSession");
        GlowSession session = new GlowSession(server, c);
        session.setOption(ChannelOption.AUTO_READ, false);
        server.getSessionRegistry().add(session);

        return session;
    }

    @Override
    public void sessionInactivated(Session session) {
        server.getSessionRegistry().remove((GlowSession) session);
    }

}

