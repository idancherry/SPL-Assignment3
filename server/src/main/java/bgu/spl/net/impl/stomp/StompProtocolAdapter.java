package bgu.spl.net.impl.stomp;

import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.srv.Connections;

public class StompProtocolAdapter implements MessagingProtocol<String> {
    private final StompMessagingProtocolImpl impl;

    public StompProtocolAdapter() {
        this.impl = new StompMessagingProtocolImpl();
    }

    @Override
    public String process(String msg) {
        impl.process(msg);
        return null;
    }

    @Override
    public boolean shouldTerminate() {
        return impl.shouldTerminate();
    }

    public void start(int connectionId, Connections<String> connections) {
        impl.start(connectionId, connections);
    }
}
